/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#define ATRACE_TAG (ATRACE_TAG_THERMAL | ATRACE_TAG_HAL)

#include "thermal-helper.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <utils/Trace.h>

#include <iterator>
#include <set>
#include <sstream>
#include <thread>
#include <vector>

namespace android {
namespace hardware {
namespace thermal {
namespace V2_0 {
namespace implementation {

constexpr std::string_view kCpuOnlineRoot("/sys/devices/system/cpu");
constexpr std::string_view kThermalSensorsRoot("/sys/devices/virtual/thermal");
constexpr std::string_view kCpuUsageFile("/proc/stat");
constexpr std::string_view kCpuOnlineFileSuffix("online");
constexpr std::string_view kCpuPresentFile("/sys/devices/system/cpu/present");
constexpr std::string_view kSensorPrefix("thermal_zone");
constexpr std::string_view kCoolingDevicePrefix("cooling_device");
constexpr std::string_view kThermalNameFile("type");
constexpr std::string_view kSensorPolicyFile("policy");
constexpr std::string_view kSensorTempSuffix("temp");
constexpr std::string_view kSensorTripPointTempZeroFile("trip_point_0_temp");
constexpr std::string_view kSensorTripPointHystZeroFile("trip_point_0_hyst");
constexpr std::string_view kUserSpaceSuffix("user_space");
constexpr std::string_view kCoolingDeviceCurStateSuffix("cur_state");
constexpr std::string_view kCoolingDeviceMaxStateSuffix("max_state");
constexpr std::string_view kCoolingDeviceState2powerSuffix("state2power_table");
constexpr std::string_view kConfigProperty("vendor.thermal.config");
constexpr std::string_view kConfigDefaultFileName("thermal_info_config.json");
constexpr std::string_view kThermalGenlProperty("persist.vendor.enable.thermal.genl");
constexpr std::string_view kThermalDisabledProperty("vendor.disable.thermal.control");

namespace {
using android::base::StringPrintf;

/*
 * Pixel don't offline CPU, so std::thread::hardware_concurrency(); should work.
 * However /sys/devices/system/cpu/present is preferred.
 * The file is expected to contain single text line with two numbers %d-%d,
 * which is a range of available cpu numbers, e.g. 0-7 would mean there
 * are 8 cores number from 0 to 7.
 * For Android systems this approach is safer than using cpufeatures, see bug
 * b/36941727.
 */
static int getNumberOfCores() {
    std::string file;
    if (!android::base::ReadFileToString(kCpuPresentFile.data(), &file)) {
        LOG(ERROR) << "Error reading CPU present file: " << kCpuPresentFile;
        return 0;
    }
    std::vector<std::string> pieces = android::base::Split(file, "-");
    if (pieces.size() != 2) {
        LOG(ERROR) << "Error parsing CPU present file content: " << file;
        return 0;
    }
    auto min_core = std::stoul(pieces[0]);
    auto max_core = std::stoul(pieces[1]);
    if (max_core < min_core) {
        LOG(ERROR) << "Error parsing CPU present min and max: " << min_core << " - " << max_core;
        return 0;
    }
    return static_cast<std::size_t>(max_core - min_core + 1);
}
const int kMaxCpus = getNumberOfCores();

void parseCpuUsagesFileAndAssignUsages(hidl_vec<CpuUsage> *cpu_usages) {
    std::string data;
    if (!android::base::ReadFileToString(kCpuUsageFile.data(), &data)) {
        LOG(ERROR) << "Error reading CPU usage file: " << kCpuUsageFile;
        return;
    }

    std::istringstream stat_data(data);
    std::string line;
    while (std::getline(stat_data, line)) {
        if (!line.find("cpu") && isdigit(line[3])) {
            // Split the string using spaces.
            std::vector<std::string> words = android::base::Split(line, " ");
            std::string cpu_name = words[0];
            int cpu_num = std::stoi(cpu_name.substr(3));

            if (cpu_num < kMaxCpus) {
                uint64_t user = std::stoull(words[1]);
                uint64_t nice = std::stoull(words[2]);
                uint64_t system = std::stoull(words[3]);
                uint64_t idle = std::stoull(words[4]);

                // Check if the CPU is online by reading the online file.
                std::string cpu_online_path =
                        StringPrintf("%s/%s/%s", kCpuOnlineRoot.data(), cpu_name.c_str(),
                                     kCpuOnlineFileSuffix.data());
                std::string is_online;
                if (!android::base::ReadFileToString(cpu_online_path, &is_online)) {
                    LOG(ERROR) << "Could not open CPU online file: " << cpu_online_path;
                    if (cpu_num != 0) {
                        return;
                    }
                    // Some architecture cannot offline cpu0, so assuming it is online
                    is_online = "1";
                }
                is_online = android::base::Trim(is_online);

                (*cpu_usages)[cpu_num].active = user + nice + system;
                (*cpu_usages)[cpu_num].total = user + nice + system + idle;
                (*cpu_usages)[cpu_num].isOnline = (is_online == "1") ? true : false;
            } else {
                LOG(ERROR) << "Unexpected CPU number: " << words[0];
                return;
            }
        }
    }
}

std::unordered_map<std::string, std::string> parseThermalPathMap(std::string_view prefix) {
    std::unordered_map<std::string, std::string> path_map;
    std::unique_ptr<DIR, int (*)(DIR *)> dir(opendir(kThermalSensorsRoot.data()), closedir);
    if (!dir) {
        return path_map;
    }

    // std::filesystem is not available for vendor yet
    // see discussion: aosp/894015
    while (struct dirent *dp = readdir(dir.get())) {
        if (dp->d_type != DT_DIR) {
            continue;
        }

        if (!android::base::StartsWith(dp->d_name, prefix.data())) {
            continue;
        }

        std::string path = android::base::StringPrintf("%s/%s/%s", kThermalSensorsRoot.data(),
                                                       dp->d_name, kThermalNameFile.data());
        std::string name;
        if (!android::base::ReadFileToString(path, &name)) {
            PLOG(ERROR) << "Failed to read from " << path;
            continue;
        }

        path_map.emplace(
                android::base::Trim(name),
                android::base::StringPrintf("%s/%s", kThermalSensorsRoot.data(), dp->d_name));
    }

    return path_map;
}

}  // namespace

/*
 * Populate the sensor_name_to_file_map_ map by walking through the file tree,
 * reading the type file and assigning the temp file path to the map.  If we do
 * not succeed, abort.
 */
ThermalHelper::ThermalHelper(const NotificationCallback &cb)
    : thermal_watcher_(new ThermalWatcher(
              std::bind(&ThermalHelper::thermalWatcherCallbackFunc, this, std::placeholders::_1))),
      cb_(cb) {
    const std::string config_path =
            "/vendor/etc/" +
            android::base::GetProperty(kConfigProperty.data(), kConfigDefaultFileName.data());
    bool thermal_throttling_disabled =
            android::base::GetBoolProperty(kThermalDisabledProperty.data(), false);

    is_initialized_ = ParseCoolingDevice(config_path, &cooling_device_info_map_) &&
                      ParseSensorInfo(config_path, &sensor_info_map_);

    if (thermal_throttling_disabled) {
        return;
    }

    if (!is_initialized_) {
        LOG(FATAL) << "Failed to parse thermal configs";
    }

    auto tz_map = parseThermalPathMap(kSensorPrefix.data());
    auto cdev_map = parseThermalPathMap(kCoolingDevicePrefix.data());

    is_initialized_ = initializeSensorMap(tz_map) && initializeCoolingDevices(cdev_map);

    if (!is_initialized_) {
        LOG(FATAL) << "ThermalHAL could not be initialized properly.";
    }

    if (!power_files_.registerPowerRailsToWatch(config_path)) {
        LOG(FATAL) << "Failed to register power rails";
    }

    for (auto const &name_status_pair : sensor_info_map_) {
        sensor_status_map_[name_status_pair.first] = {
                .severity = ThrottlingSeverity::NONE,
                .prev_hot_severity = ThrottlingSeverity::NONE,
                .prev_cold_severity = ThrottlingSeverity::NONE,
                .prev_hint_severity = ThrottlingSeverity::NONE,
                .last_update_time = boot_clock::time_point::min(),
                .thermal_cached = {NAN, boot_clock::time_point::min()},
        };

        if (name_status_pair.second.throttling_info != nullptr) {
            if (!thermal_throttling_.registerThermalThrottling(
                        name_status_pair.first, name_status_pair.second.throttling_info,
                        cooling_device_info_map_)) {
                LOG(FATAL) << name_status_pair.first << " failed to register thermal throttling";
            }
        }

        // Update cooling device max state
        for (auto &binded_cdev_info_pair :
             name_status_pair.second.throttling_info->binded_cdev_info_map) {
            const auto &cdev_info = cooling_device_info_map_.at(binded_cdev_info_pair.first);

            for (auto &cdev_ceiling : binded_cdev_info_pair.second.cdev_ceiling) {
                if (cdev_ceiling > cdev_info.max_state) {
                    if (cdev_ceiling != std::numeric_limits<int>::max()) {
                        LOG(ERROR)
                                << "Sensor " << name_status_pair.first << "'s "
                                << binded_cdev_info_pair.first << " cdev_ceiling:" << cdev_ceiling
                                << " is higher than max state:" << cdev_info.max_state;
                    }
                    cdev_ceiling = cdev_info.max_state;
                }
            }
        }

        if (name_status_pair.second.virtual_sensor_info != nullptr &&
            !name_status_pair.second.virtual_sensor_info->trigger_sensors.empty() &&
            name_status_pair.second.is_watch) {
            for (size_t i = 0;
                 i < name_status_pair.second.virtual_sensor_info->trigger_sensors.size(); i++) {
                if (sensor_info_map_.find(
                            name_status_pair.second.virtual_sensor_info->trigger_sensors[i]) !=
                    sensor_info_map_.end()) {
                    sensor_info_map_[name_status_pair.second.virtual_sensor_info
                                             ->trigger_sensors[i]]
                            .is_watch = true;
                } else {
                    LOG(FATAL) << name_status_pair.first << "'s trigger sensor: "
                               << name_status_pair.second.virtual_sensor_info->trigger_sensors[i]
                               << " is invalid";
                }
            }
        }
    }

    const bool thermal_genl_enabled =
            android::base::GetBoolProperty(kThermalGenlProperty.data(), false);

    std::set<std::string> monitored_sensors;
    initializeTrip(tz_map, &monitored_sensors, thermal_genl_enabled);

    if (thermal_genl_enabled) {
        thermal_watcher_->registerFilesToWatchNl(monitored_sensors);
    } else {
        thermal_watcher_->registerFilesToWatch(monitored_sensors);
    }

    // Need start watching after status map initialized
    is_initialized_ = thermal_watcher_->startWatchingDeviceFiles();
    if (!is_initialized_) {
        LOG(FATAL) << "ThermalHAL could not start watching thread properly.";
    }

    if (!connectToPowerHal()) {
        LOG(ERROR) << "Fail to connect to Power Hal";
    } else {
        updateSupportedPowerHints();
    }
}

bool getThermalZoneTypeById(int tz_id, std::string *type) {
    std::string tz_type;
    std::string path =
            android::base::StringPrintf("%s/%s%d/%s", kThermalSensorsRoot.data(),
                                        kSensorPrefix.data(), tz_id, kThermalNameFile.data());
    LOG(INFO) << "TZ Path: " << path;
    if (!::android::base::ReadFileToString(path, &tz_type)) {
        LOG(ERROR) << "Failed to read sensor: " << tz_type;
        return false;
    }

    // Strip the newline.
    *type = ::android::base::Trim(tz_type);
    LOG(INFO) << "TZ type: " << *type;
    return true;
}

bool ThermalHelper::readCoolingDevice(std::string_view cooling_device,
                                      CoolingDevice_2_0 *out) const {
    // Read the file.  If the file can't be read temp will be empty string.
    std::string data;

    if (!cooling_devices_.readThermalFile(cooling_device, &data)) {
        LOG(ERROR) << "readCoolingDevice: failed to read cooling_device: " << cooling_device;
        return false;
    }

    const CdevInfo &cdev_info = cooling_device_info_map_.at(cooling_device.data());
    const CoolingType &type = cdev_info.type;

    out->type = type;
    out->name = cooling_device.data();
    out->value = std::stoi(data);

    return true;
}

bool ThermalHelper::readTemperature(std::string_view sensor_name, Temperature_1_0 *out) {
    // Return fail if the thermal sensor cannot be read.
    float temp;
    std::map<std::string, float> sensor_log_map;
    if (!readThermalSensor(sensor_name, &temp, false, &sensor_log_map)) {
        LOG(ERROR) << "readTemperature: failed to read sensor: " << sensor_name;
        return false;
    }

    const SensorInfo &sensor_info = sensor_info_map_.at(sensor_name.data());
    TemperatureType_1_0 type =
            (static_cast<int>(sensor_info.type) > static_cast<int>(TemperatureType_1_0::SKIN))
                    ? TemperatureType_1_0::UNKNOWN
                    : static_cast<TemperatureType_1_0>(sensor_info.type);
    out->type = type;
    out->name = sensor_name.data();
    out->currentValue = temp * sensor_info.multiplier;
    out->throttlingThreshold =
            sensor_info.hot_thresholds[static_cast<size_t>(ThrottlingSeverity::SEVERE)];
    out->shutdownThreshold =
            sensor_info.hot_thresholds[static_cast<size_t>(ThrottlingSeverity::SHUTDOWN)];
    out->vrThrottlingThreshold = sensor_info.vr_threshold;

    if (sensor_info.is_watch) {
        std::ostringstream sensor_log;
        for (const auto &sensor_log_pair : sensor_log_map) {
            sensor_log << sensor_log_pair.first << ":" << sensor_log_pair.second << " ";
        }
        LOG(INFO) << sensor_name.data() << ":" << out->currentValue
                  << " raw data: " << sensor_log.str();
    }
    return true;
}

bool ThermalHelper::readTemperature(
        std::string_view sensor_name, Temperature_2_0 *out,
        std::pair<ThrottlingSeverity, ThrottlingSeverity> *throtting_status,
        const bool force_no_cache) {
    // Return fail if the thermal sensor cannot be read.
    float temp;
    std::map<std::string, float> sensor_log_map;

    if (!readThermalSensor(sensor_name, &temp, force_no_cache, &sensor_log_map)) {
        LOG(ERROR) << "readTemperature: failed to read sensor: " << sensor_name;
        return false;
    }

    const auto &sensor_info = sensor_info_map_.at(sensor_name.data());
    out->type = sensor_info.type;
    out->name = sensor_name.data();
    out->value = temp * sensor_info.multiplier;

    std::pair<ThrottlingSeverity, ThrottlingSeverity> status =
            std::make_pair(ThrottlingSeverity::NONE, ThrottlingSeverity::NONE);
    // Only update status if the thermal sensor is being monitored
    if (sensor_info.is_watch) {
        ThrottlingSeverity prev_hot_severity, prev_cold_severity;
        {
            // reader lock, readTemperature will be called in Binder call and the watcher thread.
            std::shared_lock<std::shared_mutex> _lock(sensor_status_map_mutex_);
            prev_hot_severity = sensor_status_map_.at(sensor_name.data()).prev_hot_severity;
            prev_cold_severity = sensor_status_map_.at(sensor_name.data()).prev_cold_severity;
        }
        status = getSeverityFromThresholds(sensor_info.hot_thresholds, sensor_info.cold_thresholds,
                                           sensor_info.hot_hysteresis, sensor_info.cold_hysteresis,
                                           prev_hot_severity, prev_cold_severity, out->value);
    }
    if (throtting_status) {
        *throtting_status = status;
    }

    out->throttlingStatus = static_cast<size_t>(status.first) > static_cast<size_t>(status.second)
                                    ? status.first
                                    : status.second;
    if (sensor_info.is_watch) {
        std::ostringstream sensor_log;
        for (const auto &sensor_log_pair : sensor_log_map) {
            sensor_log << sensor_log_pair.first << ":" << sensor_log_pair.second << " ";
        }
        LOG(INFO) << sensor_name.data() << ":" << out->value << " raw data: " << sensor_log.str();
    }

    return true;
}

bool ThermalHelper::readTemperatureThreshold(std::string_view sensor_name,
                                             TemperatureThreshold *out) const {
    // Read the file.  If the file can't be read temp will be empty string.
    std::string temp;
    std::string path;

    if (!sensor_info_map_.count(sensor_name.data())) {
        LOG(ERROR) << __func__ << ": sensor not found: " << sensor_name;
        return false;
    }

    const auto &sensor_info = sensor_info_map_.at(sensor_name.data());

    out->type = sensor_info.type;
    out->name = sensor_name.data();
    out->hotThrottlingThresholds = sensor_info.hot_thresholds;
    out->coldThrottlingThresholds = sensor_info.cold_thresholds;
    out->vrThrottlingThreshold = sensor_info.vr_threshold;
    return true;
}

void ThermalHelper::updateCoolingDevices(const std::vector<std::string> &updated_cdev) {
    int max_state;

    const auto &thermal_throttling_status_map = thermal_throttling_.GetThermalThrottlingStatusMap();

    for (const auto &target_cdev : updated_cdev) {
        max_state = 0;
        for (const auto &thermal_throttling_status_pair : thermal_throttling_status_map) {
            if (!thermal_throttling_status_pair.second.cdev_status_map.count(target_cdev)) {
                continue;
            }
            const auto state =
                    thermal_throttling_status_pair.second.cdev_status_map.at(target_cdev);
            if (state > max_state) {
                max_state = state;
            }
        }
        if (cooling_devices_.writeCdevFile(target_cdev, std::to_string(max_state))) {
            ATRACE_INT(target_cdev.c_str(), max_state);
            LOG(INFO) << "Successfully update cdev " << target_cdev << " sysfs to " << max_state;
        } else {
            LOG(ERROR) << "Failed to update cdev " << target_cdev << " sysfs to " << max_state;
        }
    }
}

std::pair<ThrottlingSeverity, ThrottlingSeverity> ThermalHelper::getSeverityFromThresholds(
        const ThrottlingArray &hot_thresholds, const ThrottlingArray &cold_thresholds,
        const ThrottlingArray &hot_hysteresis, const ThrottlingArray &cold_hysteresis,
        ThrottlingSeverity prev_hot_severity, ThrottlingSeverity prev_cold_severity,
        float value) const {
    ThrottlingSeverity ret_hot = ThrottlingSeverity::NONE;
    ThrottlingSeverity ret_hot_hysteresis = ThrottlingSeverity::NONE;
    ThrottlingSeverity ret_cold = ThrottlingSeverity::NONE;
    ThrottlingSeverity ret_cold_hysteresis = ThrottlingSeverity::NONE;

    // Here we want to control the iteration from high to low, and hidl_enum_range doesn't support
    // a reverse iterator yet.
    for (size_t i = static_cast<size_t>(ThrottlingSeverity::SHUTDOWN);
         i > static_cast<size_t>(ThrottlingSeverity::NONE); --i) {
        if (!std::isnan(hot_thresholds[i]) && hot_thresholds[i] <= value &&
            ret_hot == ThrottlingSeverity::NONE) {
            ret_hot = static_cast<ThrottlingSeverity>(i);
        }
        if (!std::isnan(hot_thresholds[i]) && (hot_thresholds[i] - hot_hysteresis[i]) < value &&
            ret_hot_hysteresis == ThrottlingSeverity::NONE) {
            ret_hot_hysteresis = static_cast<ThrottlingSeverity>(i);
        }
        if (!std::isnan(cold_thresholds[i]) && cold_thresholds[i] >= value &&
            ret_cold == ThrottlingSeverity::NONE) {
            ret_cold = static_cast<ThrottlingSeverity>(i);
        }
        if (!std::isnan(cold_thresholds[i]) && (cold_thresholds[i] + cold_hysteresis[i]) > value &&
            ret_cold_hysteresis == ThrottlingSeverity::NONE) {
            ret_cold_hysteresis = static_cast<ThrottlingSeverity>(i);
        }
    }
    if (static_cast<size_t>(ret_hot) < static_cast<size_t>(prev_hot_severity)) {
        ret_hot = ret_hot_hysteresis;
    }
    if (static_cast<size_t>(ret_cold) < static_cast<size_t>(prev_cold_severity)) {
        ret_cold = ret_cold_hysteresis;
    }

    return std::make_pair(ret_hot, ret_cold);
}

bool ThermalHelper::initializeSensorMap(
        const std::unordered_map<std::string, std::string> &path_map) {
    for (const auto &sensor_info_pair : sensor_info_map_) {
        std::string_view sensor_name = sensor_info_pair.first;
        if (sensor_info_pair.second.virtual_sensor_info != nullptr) {
            continue;
        }
        if (!path_map.count(sensor_name.data())) {
            LOG(ERROR) << "Could not find " << sensor_name << " in sysfs";
            return false;
        }

        std::string path;
        if (sensor_info_pair.second.temp_path.empty()) {
            path = android::base::StringPrintf("%s/%s", path_map.at(sensor_name.data()).c_str(),
                                               kSensorTempSuffix.data());
        } else {
            path = sensor_info_pair.second.temp_path;
        }

        if (!thermal_sensors_.addThermalFile(sensor_name, path)) {
            LOG(ERROR) << "Could not add " << sensor_name << "to sensors map";
            return false;
        }
    }
    return true;
}

bool ThermalHelper::initializeCoolingDevices(
        const std::unordered_map<std::string, std::string> &path_map) {
    for (auto &cooling_device_info_pair : cooling_device_info_map_) {
        std::string cooling_device_name = cooling_device_info_pair.first;
        if (!path_map.count(cooling_device_name)) {
            LOG(ERROR) << "Could not find " << cooling_device_name << " in sysfs";
            return false;
        }
        // Add cooling device path for thermalHAL to get current state
        std::string_view path = path_map.at(cooling_device_name);
        std::string read_path;
        if (!cooling_device_info_pair.second.read_path.empty()) {
            read_path = cooling_device_info_pair.second.read_path.data();
        } else {
            read_path = android::base::StringPrintf("%s/%s", path.data(),
                                                    kCoolingDeviceCurStateSuffix.data());
        }
        if (!cooling_devices_.addThermalFile(cooling_device_name, read_path)) {
            LOG(ERROR) << "Could not add " << cooling_device_name
                       << " read path to cooling device map";
            return false;
        }

        std::string state2power_path = android::base::StringPrintf(
                "%s/%s", path.data(), kCoolingDeviceState2powerSuffix.data());
        std::string state2power_str;
        if (android::base::ReadFileToString(state2power_path, &state2power_str)) {
            LOG(INFO) << "Cooling device " << cooling_device_info_pair.first
                      << " use state2power read from sysfs";
            cooling_device_info_pair.second.state2power.clear();

            std::stringstream power(state2power_str);
            unsigned int power_number;
            int i = 0;
            while (power >> power_number) {
                cooling_device_info_pair.second.state2power.push_back(
                        static_cast<float>(power_number));
                LOG(INFO) << "Cooling device " << cooling_device_info_pair.first << " state:" << i
                          << " power: " << power_number;
                i++;
            }
        }

        // Get max cooling device request state
        std::string max_state;
        std::string max_state_path = android::base::StringPrintf(
                "%s/%s", path.data(), kCoolingDeviceMaxStateSuffix.data());
        if (!android::base::ReadFileToString(max_state_path, &max_state)) {
            LOG(ERROR) << cooling_device_info_pair.first
                       << " could not open max state file:" << max_state_path;
            cooling_device_info_pair.second.max_state = std::numeric_limits<int>::max();
        } else {
            cooling_device_info_pair.second.max_state = std::stoi(android::base::Trim(max_state));
            LOG(INFO) << "Cooling device " << cooling_device_info_pair.first
                      << " max state: " << cooling_device_info_pair.second.max_state
                      << " state2power number: "
                      << cooling_device_info_pair.second.state2power.size();
            if (cooling_device_info_pair.second.state2power.size() > 0 &&
                static_cast<int>(cooling_device_info_pair.second.state2power.size()) !=
                        (cooling_device_info_pair.second.max_state + 1)) {
                LOG(ERROR) << "Invalid state2power number: "
                           << cooling_device_info_pair.second.state2power.size()
                           << ", number should be " << cooling_device_info_pair.second.max_state + 1
                           << " (max_state + 1)";
                return false;
            }
        }

        // Add cooling device path for thermalHAL to request state
        cooling_device_name =
                android::base::StringPrintf("%s_%s", cooling_device_name.c_str(), "w");
        std::string write_path;
        if (!cooling_device_info_pair.second.write_path.empty()) {
            write_path = cooling_device_info_pair.second.write_path.data();
        } else {
            write_path = android::base::StringPrintf("%s/%s", path.data(),
                                                     kCoolingDeviceCurStateSuffix.data());
        }

        if (!cooling_devices_.addThermalFile(cooling_device_name, write_path)) {
            LOG(ERROR) << "Could not add " << cooling_device_name
                       << " write path to cooling device map";
            return false;
        }
    }
    return true;
}

void ThermalHelper::setMinTimeout(SensorInfo *sensor_info) {
    sensor_info->polling_delay = kMinPollIntervalMs;
    sensor_info->passive_delay = kMinPollIntervalMs;
}

void ThermalHelper::initializeTrip(const std::unordered_map<std::string, std::string> &path_map,
                                   std::set<std::string> *monitored_sensors,
                                   bool thermal_genl_enabled) {
    for (auto &sensor_info : sensor_info_map_) {
        if (!sensor_info.second.is_watch || (sensor_info.second.virtual_sensor_info != nullptr)) {
            continue;
        }

        bool trip_update = false;
        std::string_view sensor_name = sensor_info.first;
        std::string_view tz_path = path_map.at(sensor_name.data());
        std::string tz_policy;
        std::string path =
                android::base::StringPrintf("%s/%s", (tz_path.data()), kSensorPolicyFile.data());

        if (thermal_genl_enabled) {
            trip_update = true;
        } else {
            // Check if thermal zone support uevent notify
            if (!android::base::ReadFileToString(path, &tz_policy)) {
                LOG(ERROR) << sensor_name << " could not open tz policy file:" << path;
            } else {
                tz_policy = android::base::Trim(tz_policy);
                if (tz_policy != kUserSpaceSuffix) {
                    LOG(ERROR) << sensor_name << " does not support uevent notify";
                } else {
                    trip_update = true;
                }
            }
        }
        if (trip_update) {
            // Update thermal zone trip point
            for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                if (!std::isnan(sensor_info.second.hot_thresholds[i]) &&
                    !std::isnan(sensor_info.second.hot_hysteresis[i])) {
                    // Update trip_point_0_temp threshold
                    std::string threshold = std::to_string(static_cast<int>(
                            sensor_info.second.hot_thresholds[i] / sensor_info.second.multiplier));
                    path = android::base::StringPrintf("%s/%s", (tz_path.data()),
                                                       kSensorTripPointTempZeroFile.data());
                    if (!android::base::WriteStringToFile(threshold, path)) {
                        LOG(ERROR) << "fail to update " << sensor_name << " trip point: " << path
                                   << " to " << threshold;
                        trip_update = false;
                        break;
                    }
                    // Update trip_point_0_hyst threshold
                    threshold = std::to_string(static_cast<int>(
                            sensor_info.second.hot_hysteresis[i] / sensor_info.second.multiplier));
                    path = android::base::StringPrintf("%s/%s", (tz_path.data()),
                                                       kSensorTripPointHystZeroFile.data());
                    if (!android::base::WriteStringToFile(threshold, path)) {
                        LOG(ERROR) << "fail to update " << sensor_name << "trip hyst" << threshold
                                   << path;
                        trip_update = false;
                        break;
                    }
                    break;
                } else if (i == kThrottlingSeverityCount - 1) {
                    LOG(ERROR) << sensor_name << ":all thresholds are NAN";
                    trip_update = false;
                    break;
                }
            }
            monitored_sensors->insert(sensor_info.first);
        }

        if (!trip_update) {
            LOG(INFO) << "config Sensor: " << sensor_info.first
                      << " to default polling interval: " << kMinPollIntervalMs.count();
            setMinTimeout(&sensor_info.second);
        }
    }
}

bool ThermalHelper::fillTemperatures(hidl_vec<Temperature_1_0> *temperatures) {
    std::vector<Temperature_1_0> ret;
    for (const auto &name_info_pair : sensor_info_map_) {
        Temperature_1_0 temp;

        if (name_info_pair.second.is_hidden) {
            continue;
        }

        if (readTemperature(name_info_pair.first, &temp)) {
            ret.emplace_back(std::move(temp));
        } else {
            LOG(ERROR) << __func__
                       << ": error reading temperature for sensor: " << name_info_pair.first;
            return false;
        }
    }
    *temperatures = ret;
    return ret.size() > 0;
}

bool ThermalHelper::fillCurrentTemperatures(bool filterType, bool filterCallback,
                                            TemperatureType_2_0 type,
                                            hidl_vec<Temperature_2_0> *temperatures) {
    std::vector<Temperature_2_0> ret;
    for (const auto &name_info_pair : sensor_info_map_) {
        Temperature_2_0 temp;
        if (name_info_pair.second.is_hidden) {
            continue;
        }
        if (filterType && name_info_pair.second.type != type) {
            continue;
        }
        if (filterCallback && !name_info_pair.second.send_cb) {
            continue;
        }
        if (readTemperature(name_info_pair.first, &temp, nullptr, false)) {
            ret.emplace_back(std::move(temp));
        } else {
            LOG(ERROR) << __func__
                       << ": error reading temperature for sensor: " << name_info_pair.first;
        }
    }
    *temperatures = ret;
    return ret.size() > 0;
}

bool ThermalHelper::fillTemperatureThresholds(bool filterType, TemperatureType_2_0 type,
                                              hidl_vec<TemperatureThreshold> *thresholds) const {
    std::vector<TemperatureThreshold> ret;
    for (const auto &name_info_pair : sensor_info_map_) {
        TemperatureThreshold temp;
        if (name_info_pair.second.is_hidden) {
            continue;
        }
        if (filterType && name_info_pair.second.type != type) {
            continue;
        }
        if (readTemperatureThreshold(name_info_pair.first, &temp)) {
            ret.emplace_back(std::move(temp));
        } else {
            LOG(ERROR) << __func__ << ": error reading temperature threshold for sensor: "
                       << name_info_pair.first;
            return false;
        }
    }
    *thresholds = ret;
    return ret.size() > 0;
}

bool ThermalHelper::fillCurrentCoolingDevices(bool filterType, CoolingType type,
                                              hidl_vec<CoolingDevice_2_0> *cooling_devices) const {
    std::vector<CoolingDevice_2_0> ret;
    for (const auto &name_info_pair : cooling_device_info_map_) {
        CoolingDevice_2_0 value;
        if (filterType && name_info_pair.second.type != type) {
            continue;
        }
        if (readCoolingDevice(name_info_pair.first, &value)) {
            ret.emplace_back(std::move(value));
        } else {
            LOG(ERROR) << __func__ << ": error reading cooling device: " << name_info_pair.first;
            return false;
        }
    }
    *cooling_devices = ret;
    return ret.size() > 0;
}

bool ThermalHelper::fillCpuUsages(hidl_vec<CpuUsage> *cpu_usages) const {
    cpu_usages->resize(kMaxCpus);
    for (int i = 0; i < kMaxCpus; i++) {
        (*cpu_usages)[i].name = StringPrintf("cpu%d", i);
        (*cpu_usages)[i].active = 0;
        (*cpu_usages)[i].total = 0;
        (*cpu_usages)[i].isOnline = false;
    }
    parseCpuUsagesFileAndAssignUsages(cpu_usages);
    return true;
}

bool ThermalHelper::readThermalSensor(std::string_view sensor_name, float *temp,
                                      const bool force_no_cache,
                                      std::map<std::string, float> *sensor_log_map) {
    float temp_val = 0.0;
    std::string file_reading;
    boot_clock::time_point now = boot_clock::now();

    ATRACE_NAME(StringPrintf("ThermalHelper::readThermalSensor - %s", sensor_name.data()).c_str());
    if (!(sensor_info_map_.count(sensor_name.data()) &&
          sensor_status_map_.count(sensor_name.data()))) {
        return false;
    }

    const auto &sensor_info = sensor_info_map_.at(sensor_name.data());
    auto &sensor_status = sensor_status_map_.at(sensor_name.data());

    // Check if thermal data need to be read from cache
    if (!force_no_cache &&
        (sensor_status.thermal_cached.timestamp != boot_clock::time_point::min()) &&
        (std::chrono::duration_cast<std::chrono::milliseconds>(
                 now - sensor_status.thermal_cached.timestamp) < sensor_info.time_resolution) &&
        !isnan(sensor_status.thermal_cached.temp)) {
        *temp = sensor_status.thermal_cached.temp;
        (*sensor_log_map)[sensor_name.data()] = *temp;
        ATRACE_INT((sensor_name.data() + std::string("-cached")).c_str(), static_cast<int>(*temp));
        return true;
    }

    // Reading thermal sensor according to it's composition
    if (sensor_info.virtual_sensor_info == nullptr) {
        if (!thermal_sensors_.readThermalFile(sensor_name.data(), &file_reading)) {
            return false;
        }

        if (file_reading.empty()) {
            LOG(ERROR) << "failed to read sensor: " << sensor_name;
            return false;
        }
        *temp = std::stof(::android::base::Trim(file_reading));
    } else {
        for (size_t i = 0; i < sensor_info.virtual_sensor_info->linked_sensors.size(); i++) {
            float sensor_reading = 0.0;
            if (!readThermalSensor(sensor_info.virtual_sensor_info->linked_sensors[i],
                                   &sensor_reading, force_no_cache, sensor_log_map)) {
                return false;
            }
            if (std::isnan(sensor_info.virtual_sensor_info->coefficients[i])) {
                return false;
            }

            float coefficient = sensor_info.virtual_sensor_info->coefficients[i];
            switch (sensor_info.virtual_sensor_info->formula) {
                case FormulaOption::COUNT_THRESHOLD:
                    if ((coefficient < 0 && sensor_reading < -coefficient) ||
                        (coefficient >= 0 && sensor_reading >= coefficient))
                        temp_val += 1;
                    break;
                case FormulaOption::WEIGHTED_AVG:
                    temp_val += sensor_reading * coefficient;
                    break;
                case FormulaOption::MAXIMUM:
                    if (i == 0)
                        temp_val = std::numeric_limits<float>::lowest();
                    if (sensor_reading * coefficient > temp_val)
                        temp_val = sensor_reading * coefficient;
                    break;
                case FormulaOption::MINIMUM:
                    if (i == 0)
                        temp_val = std::numeric_limits<float>::max();
                    if (sensor_reading * coefficient < temp_val)
                        temp_val = sensor_reading * coefficient;
                    break;
                default:
                    break;
            }
        }
        *temp = (temp_val + sensor_info.virtual_sensor_info->offset);
    }
    (*sensor_log_map)[sensor_name.data()] = *temp;
    ATRACE_INT(sensor_name.data(), static_cast<int>(*temp));

    {
        std::unique_lock<std::shared_mutex> _lock(sensor_status_map_mutex_);
        sensor_status.thermal_cached.temp = *temp;
        sensor_status.thermal_cached.timestamp = now;
    }

    return true;
}

// This is called in the different thread context and will update sensor_status
// uevent_sensors is the set of sensors which trigger uevent from thermal core driver.
std::chrono::milliseconds ThermalHelper::thermalWatcherCallbackFunc(
        const std::set<std::string> &uevent_sensors) {
    std::vector<Temperature_2_0> temps;
    std::vector<std::string> cooling_devices_to_update;
    boot_clock::time_point now = boot_clock::now();
    auto min_sleep_ms = std::chrono::milliseconds::max();
    bool power_data_is_updated = false;

    ATRACE_CALL();
    for (auto &name_status_pair : sensor_status_map_) {
        bool force_update = false;
        bool force_no_cache = false;
        Temperature_2_0 temp;
        TemperatureThreshold threshold;
        SensorStatus &sensor_status = name_status_pair.second;
        const SensorInfo &sensor_info = sensor_info_map_.at(name_status_pair.first);

        // Only handle the sensors in allow list
        if (!sensor_info.is_watch) {
            continue;
        }

        ATRACE_NAME(StringPrintf("ThermalHelper::thermalWatcherCallbackFunc - %s",
                                 name_status_pair.first.data())
                            .c_str());

        std::chrono::milliseconds time_elapsed_ms = std::chrono::milliseconds::zero();
        auto sleep_ms = (sensor_status.severity != ThrottlingSeverity::NONE)
                                ? sensor_info.passive_delay
                                : sensor_info.polling_delay;

        if (sensor_info.virtual_sensor_info != nullptr &&
            !sensor_info.virtual_sensor_info->trigger_sensors.empty()) {
            for (size_t i = 0; i < sensor_info.virtual_sensor_info->trigger_sensors.size(); i++) {
                const auto &trigger_sensor_status =
                        sensor_status_map_.at(sensor_info.virtual_sensor_info->trigger_sensors[i]);
                if (trigger_sensor_status.severity != ThrottlingSeverity::NONE) {
                    sleep_ms = sensor_info.passive_delay;
                }
            }
        }
        // Check if the sensor need to be updated
        if (sensor_status.last_update_time == boot_clock::time_point::min()) {
            force_update = true;
            force_no_cache = true;
        } else {
            time_elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                    now - sensor_status.last_update_time);
            if (uevent_sensors.size()) {
                if (sensor_info.virtual_sensor_info != nullptr) {
                    for (size_t i = 0; i < sensor_info.virtual_sensor_info->trigger_sensors.size();
                         i++) {
                        if (uevent_sensors.find(
                                    sensor_info.virtual_sensor_info->trigger_sensors[i]) !=
                            uevent_sensors.end()) {
                            force_update = true;
                            force_no_cache = true;
                        }
                    }
                } else if (uevent_sensors.find(name_status_pair.first) != uevent_sensors.end()) {
                    force_update = true;
                    force_no_cache = true;
                }
            } else if (time_elapsed_ms > sleep_ms) {
                force_update = true;
            }
        }
        LOG(VERBOSE) << "sensor " << name_status_pair.first
                     << ": time_elpased=" << time_elapsed_ms.count()
                     << ", sleep_ms=" << sleep_ms.count() << ", force_update = " << force_update
                     << ", force_no_cache = " << force_no_cache;

        if (!force_update) {
            auto timeout_remaining = sleep_ms - time_elapsed_ms;
            if (min_sleep_ms > timeout_remaining) {
                min_sleep_ms = timeout_remaining;
            }
            LOG(VERBOSE) << "sensor " << name_status_pair.first
                         << ": timeout_remaining=" << timeout_remaining.count();
            continue;
        }

        std::pair<ThrottlingSeverity, ThrottlingSeverity> throtting_status;
        if (!readTemperature(name_status_pair.first, &temp, &throtting_status, force_no_cache)) {
            LOG(ERROR) << __func__
                       << ": error reading temperature for sensor: " << name_status_pair.first;
            continue;
        }
        if (!readTemperatureThreshold(name_status_pair.first, &threshold)) {
            LOG(ERROR) << __func__ << ": error reading temperature threshold for sensor: "
                       << name_status_pair.first;
            continue;
        }

        {
            // writer lock
            std::unique_lock<std::shared_mutex> _lock(sensor_status_map_mutex_);
            if (throtting_status.first != sensor_status.prev_hot_severity) {
                sensor_status.prev_hot_severity = throtting_status.first;
            }
            if (throtting_status.second != sensor_status.prev_cold_severity) {
                sensor_status.prev_cold_severity = throtting_status.second;
            }
            if (temp.throttlingStatus != sensor_status.severity) {
                temps.push_back(temp);
                sensor_status.severity = temp.throttlingStatus;
                sleep_ms = (sensor_status.severity != ThrottlingSeverity::NONE)
                                   ? sensor_info.passive_delay
                                   : sensor_info.polling_delay;
            }
        }

        if (!power_data_is_updated) {
            power_files_.refreshPowerStatus();
            power_data_is_updated = true;
        }

        if (sensor_status.severity == ThrottlingSeverity::NONE) {
            thermal_throttling_.clearThrottlingData(name_status_pair.first, sensor_info);
        } else {
            // update thermal throttling request
            thermal_throttling_.thermalThrottlingUpdate(
                    temp, sensor_info, sensor_status.severity, time_elapsed_ms,
                    power_files_.GetPowerStatusMap(), cooling_device_info_map_);
        }

        thermal_throttling_.computeCoolingDevicesRequest(name_status_pair.first, sensor_info,
                                                         sensor_status.severity,
                                                         &cooling_devices_to_update);
        if (min_sleep_ms > sleep_ms) {
            min_sleep_ms = sleep_ms;
        }

        LOG(VERBOSE) << "Sensor " << name_status_pair.first << ": sleep_ms=" << sleep_ms.count()
                     << ", min_sleep_ms voting result=" << min_sleep_ms.count();
        sensor_status.last_update_time = now;
    }

    if (!cooling_devices_to_update.empty()) {
        updateCoolingDevices(cooling_devices_to_update);
    }

    if (!temps.empty()) {
        for (const auto &t : temps) {
            if (sensor_info_map_.at(t.name).send_cb && cb_) {
                cb_(t);
            }

            if (sensor_info_map_.at(t.name).send_powerhint && isAidlPowerHalExist()) {
                sendPowerExtHint(t);
            }
        }
    }

    return min_sleep_ms;
}

bool ThermalHelper::connectToPowerHal() {
    return power_hal_service_.connect();
}

void ThermalHelper::updateSupportedPowerHints() {
    for (auto const &name_status_pair : sensor_info_map_) {
        if (!(name_status_pair.second.send_powerhint)) {
            continue;
        }
        ThrottlingSeverity current_severity = ThrottlingSeverity::NONE;
        for (const auto &severity : hidl_enum_range<ThrottlingSeverity>()) {
            if (severity == ThrottlingSeverity::NONE) {
                supported_powerhint_map_[name_status_pair.first][ThrottlingSeverity::NONE] =
                        ThrottlingSeverity::NONE;
                continue;
            }

            bool isSupported = false;
            ndk::ScopedAStatus isSupportedResult;

            if (power_hal_service_.isPowerHalExtConnected()) {
                isSupported = power_hal_service_.isModeSupported(name_status_pair.first, severity);
            }
            if (isSupported)
                current_severity = severity;
            supported_powerhint_map_[name_status_pair.first][severity] = current_severity;
        }
    }
}

void ThermalHelper::sendPowerExtHint(const Temperature_2_0 &t) {
    ATRACE_CALL();
    std::lock_guard<std::shared_mutex> lock(sensor_status_map_mutex_);
    ThrottlingSeverity prev_hint_severity;
    prev_hint_severity = sensor_status_map_.at(t.name).prev_hint_severity;
    ThrottlingSeverity current_hint_severity = supported_powerhint_map_[t.name][t.throttlingStatus];

    if (prev_hint_severity == current_hint_severity)
        return;

    if (prev_hint_severity != ThrottlingSeverity::NONE) {
        power_hal_service_.setMode(t.name, prev_hint_severity, false);
    }

    if (current_hint_severity != ThrottlingSeverity::NONE) {
        power_hal_service_.setMode(t.name, current_hint_severity, true);
    }

    sensor_status_map_[t.name].prev_hint_severity = current_hint_severity;
}
}  // namespace implementation
}  // namespace V2_0
}  // namespace thermal
}  // namespace hardware
}  // namespace android

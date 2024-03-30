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

#pragma once

#include <array>
#include <chrono>
#include <mutex>
#include <shared_mutex>
#include <string>
#include <string_view>
#include <thread>
#include <unordered_map>
#include <vector>

#include "utils/power_files.h"
#include "utils/powerhal_helper.h"
#include "utils/thermal_files.h"
#include "utils/thermal_info.h"
#include "utils/thermal_throttling.h"
#include "utils/thermal_watcher.h"

namespace android {
namespace hardware {
namespace thermal {
namespace V2_0 {
namespace implementation {

using ::android::hardware::hidl_vec;
using ::android::hardware::thermal::V1_0::CpuUsage;
using ::android::hardware::thermal::V2_0::CoolingType;
using ::android::hardware::thermal::V2_0::IThermal;
using CoolingDevice_1_0 = ::android::hardware::thermal::V1_0::CoolingDevice;
using CoolingDevice_2_0 = ::android::hardware::thermal::V2_0::CoolingDevice;
using Temperature_1_0 = ::android::hardware::thermal::V1_0::Temperature;
using Temperature_2_0 = ::android::hardware::thermal::V2_0::Temperature;
using TemperatureType_1_0 = ::android::hardware::thermal::V1_0::TemperatureType;
using TemperatureType_2_0 = ::android::hardware::thermal::V2_0::TemperatureType;
using ::android::hardware::thermal::V2_0::TemperatureThreshold;
using ::android::hardware::thermal::V2_0::ThrottlingSeverity;

using NotificationCallback = std::function<void(const Temperature_2_0 &t)>;
using NotificationTime = std::chrono::time_point<std::chrono::steady_clock>;

// Get thermal_zone type
bool getThermalZoneTypeById(int tz_id, std::string *);

struct ThermalSample {
    float temp;
    boot_clock::time_point timestamp;
};

struct SensorStatus {
    ThrottlingSeverity severity;
    ThrottlingSeverity prev_hot_severity;
    ThrottlingSeverity prev_cold_severity;
    ThrottlingSeverity prev_hint_severity;
    boot_clock::time_point last_update_time;
    ThermalSample thermal_cached;
};

class ThermalHelper {
  public:
    explicit ThermalHelper(const NotificationCallback &cb);
    ~ThermalHelper() = default;

    bool fillTemperatures(hidl_vec<Temperature_1_0> *temperatures);
    bool fillCurrentTemperatures(bool filterType, bool filterCallback, TemperatureType_2_0 type,
                                 hidl_vec<Temperature_2_0> *temperatures);
    bool fillTemperatureThresholds(bool filterType, TemperatureType_2_0 type,
                                   hidl_vec<TemperatureThreshold> *thresholds) const;
    bool fillCurrentCoolingDevices(bool filterType, CoolingType type,
                                   hidl_vec<CoolingDevice_2_0> *coolingdevices) const;
    bool fillCpuUsages(hidl_vec<CpuUsage> *cpu_usages) const;

    // Dissallow copy and assign.
    ThermalHelper(const ThermalHelper &) = delete;
    void operator=(const ThermalHelper &) = delete;

    bool isInitializedOk() const { return is_initialized_; }

    // Read the temperature of a single sensor.
    bool readTemperature(std::string_view sensor_name, Temperature_1_0 *out);
    bool readTemperature(
            std::string_view sensor_name, Temperature_2_0 *out,
            std::pair<ThrottlingSeverity, ThrottlingSeverity> *throtting_status = nullptr,
            const bool force_sysfs = false);
    bool readTemperatureThreshold(std::string_view sensor_name, TemperatureThreshold *out) const;
    // Read the value of a single cooling device.
    bool readCoolingDevice(std::string_view cooling_device, CoolingDevice_2_0 *out) const;
    // Get SensorInfo Map
    const std::unordered_map<std::string, SensorInfo> &GetSensorInfoMap() const {
        return sensor_info_map_;
    }
    // Get CdevInfo Map
    const std::unordered_map<std::string, CdevInfo> &GetCdevInfoMap() const {
        return cooling_device_info_map_;
    }
    // Get SensorStatus Map
    const std::unordered_map<std::string, SensorStatus> &GetSensorStatusMap() const {
        std::shared_lock<std::shared_mutex> _lock(sensor_status_map_mutex_);
        return sensor_status_map_;
    }
    // Get ThermalThrottling Map
    const std::unordered_map<std::string, ThermalThrottlingStatus> &GetThermalThrottlingStatusMap()
            const {
        return thermal_throttling_.GetThermalThrottlingStatusMap();
    }
    // Get PowerRailInfo Map
    const std::unordered_map<std::string, PowerRailInfo> &GetPowerRailInfoMap() const {
        return power_files_.GetPowerRailInfoMap();
    }

    // Get PowerStatus Map
    const std::unordered_map<std::string, PowerStatus> &GetPowerStatusMap() const {
        return power_files_.GetPowerStatusMap();
    }
    void sendPowerExtHint(const Temperature_2_0 &t);
    bool isAidlPowerHalExist() { return power_hal_service_.isAidlPowerHalExist(); }
    bool isPowerHalConnected() { return power_hal_service_.isPowerHalConnected(); }
    bool isPowerHalExtConnected() { return power_hal_service_.isPowerHalExtConnected(); }

  private:
    bool initializeSensorMap(const std::unordered_map<std::string, std::string> &path_map);
    bool initializeCoolingDevices(const std::unordered_map<std::string, std::string> &path_map);
    void setMinTimeout(SensorInfo *sensor_info);
    void initializeTrip(const std::unordered_map<std::string, std::string> &path_map,
                        std::set<std::string> *monitored_sensors, bool thermal_genl_enabled);

    // For thermal_watcher_'s polling thread, return the sleep interval
    std::chrono::milliseconds thermalWatcherCallbackFunc(
            const std::set<std::string> &uevent_sensors);
    // Return hot and cold severity status as std::pair
    std::pair<ThrottlingSeverity, ThrottlingSeverity> getSeverityFromThresholds(
            const ThrottlingArray &hot_thresholds, const ThrottlingArray &cold_thresholds,
            const ThrottlingArray &hot_hysteresis, const ThrottlingArray &cold_hysteresis,
            ThrottlingSeverity prev_hot_severity, ThrottlingSeverity prev_cold_severity,
            float value) const;
    // Read temperature data according to thermal sensor's info
    bool readThermalSensor(std::string_view sensor_name, float *temp, const bool force_sysfs);
    bool connectToPowerHal();
    void updateSupportedPowerHints();
    void updateCoolingDevices(const std::vector<std::string> &cooling_devices_to_update);
    sp<ThermalWatcher> thermal_watcher_;
    PowerFiles power_files_;
    ThermalFiles thermal_sensors_;
    ThermalFiles cooling_devices_;
    ThermalThrottling thermal_throttling_;
    bool is_initialized_;
    const NotificationCallback cb_;
    std::unordered_map<std::string, CdevInfo> cooling_device_info_map_;
    std::unordered_map<std::string, SensorInfo> sensor_info_map_;
    std::unordered_map<std::string, std::map<ThrottlingSeverity, ThrottlingSeverity>>
            supported_powerhint_map_;
    PowerHalService power_hal_service_;

    mutable std::shared_mutex sensor_status_map_mutex_;
    std::unordered_map<std::string, SensorStatus> sensor_status_map_;
};

}  // namespace implementation
}  // namespace V2_0
}  // namespace thermal
}  // namespace hardware
}  // namespace android

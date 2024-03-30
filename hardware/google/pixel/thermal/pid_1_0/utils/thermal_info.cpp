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
#include "thermal_info.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <json/reader.h>
#include <json/value.h>

#include <cmath>
#include <unordered_set>

namespace android {
namespace hardware {
namespace thermal {
namespace V2_0 {
namespace implementation {

constexpr std::string_view kPowerLinkDisabledProperty("vendor.disable.thermal.powerlink");

using ::android::hardware::hidl_enum_range;
using ::android::hardware::thermal::V2_0::toString;
using TemperatureType_2_0 = ::android::hardware::thermal::V2_0::TemperatureType;

namespace {

template <typename T>
// Return false when failed parsing
bool getTypeFromString(std::string_view str, T *out) {
    auto types = hidl_enum_range<T>();
    for (const auto &type : types) {
        if (toString(type) == str) {
            *out = type;
            return true;
        }
    }
    return false;
}

float getFloatFromValue(const Json::Value &value) {
    if (value.isString()) {
        return std::stof(value.asString());
    } else {
        return value.asFloat();
    }
}

int getIntFromValue(const Json::Value &value) {
    if (value.isString()) {
        return (value.asString() == "max") ? std::numeric_limits<int>::max()
                                           : std::stoul(value.asString());
    } else {
        return value.asInt();
    }
}

bool getIntFromJsonValues(const Json::Value &values, CdevArray *out, bool inc_check,
                          bool dec_check) {
    CdevArray ret;

    if (inc_check && dec_check) {
        LOG(ERROR) << "Cannot enable inc_check and dec_check at the same time";
        return false;
    }

    if (values.size() != kThrottlingSeverityCount) {
        LOG(ERROR) << "Values size is invalid";
        return false;
    } else {
        int last;
        for (Json::Value::ArrayIndex i = 0; i < kThrottlingSeverityCount; ++i) {
            ret[i] = getIntFromValue(values[i]);
            if (inc_check && ret[i] < last) {
                LOG(FATAL) << "Invalid array[" << i << "]" << ret[i] << " min=" << last;
                return false;
            }
            if (dec_check && ret[i] > last) {
                LOG(FATAL) << "Invalid array[" << i << "]" << ret[i] << " max=" << last;
                return false;
            }
            last = ret[i];
            LOG(INFO) << "[" << i << "]: " << ret[i];
        }
    }

    *out = ret;
    return true;
}

bool getFloatFromJsonValues(const Json::Value &values, ThrottlingArray *out, bool inc_check,
                            bool dec_check) {
    ThrottlingArray ret;

    if (inc_check && dec_check) {
        LOG(ERROR) << "Cannot enable inc_check and dec_check at the same time";
        return false;
    }

    if (values.size() != kThrottlingSeverityCount) {
        LOG(ERROR) << "Values size is invalid";
        return false;
    } else {
        float last = std::nanf("");
        for (Json::Value::ArrayIndex i = 0; i < kThrottlingSeverityCount; ++i) {
            ret[i] = getFloatFromValue(values[i]);
            if (inc_check && !std::isnan(last) && !std::isnan(ret[i]) && ret[i] < last) {
                LOG(FATAL) << "Invalid array[" << i << "]" << ret[i] << " min=" << last;
                return false;
            }
            if (dec_check && !std::isnan(last) && !std::isnan(ret[i]) && ret[i] > last) {
                LOG(FATAL) << "Invalid array[" << i << "]" << ret[i] << " max=" << last;
                return false;
            }
            last = std::isnan(ret[i]) ? last : ret[i];
            LOG(INFO) << "[" << i << "]: " << ret[i];
        }
    }

    *out = ret;
    return true;
}
}  // namespace

bool ParseSensorInfo(std::string_view config_path,
                     std::unordered_map<std::string, SensorInfo> *sensors_parsed) {
    std::string json_doc;
    if (!android::base::ReadFileToString(config_path.data(), &json_doc)) {
        LOG(ERROR) << "Failed to read JSON config from " << config_path;
        return false;
    }
    Json::Value root;
    Json::CharReaderBuilder builder;
    std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
    std::string errorMessage;

    if (!reader->parse(&*json_doc.begin(), &*json_doc.end(), &root, &errorMessage)) {
        LOG(ERROR) << "Failed to parse JSON config: " << errorMessage;
        return false;
    }

    Json::Value sensors = root["Sensors"];
    std::size_t total_parsed = 0;
    std::unordered_set<std::string> sensors_name_parsed;

    for (Json::Value::ArrayIndex i = 0; i < sensors.size(); ++i) {
        const std::string &name = sensors[i]["Name"].asString();
        LOG(INFO) << "Sensor[" << i << "]'s Name: " << name;
        if (name.empty()) {
            LOG(ERROR) << "Failed to read "
                       << "Sensor[" << i << "]'s Name";
            sensors_parsed->clear();
            return false;
        }

        auto result = sensors_name_parsed.insert(name);
        if (!result.second) {
            LOG(ERROR) << "Duplicate Sensor[" << i << "]'s Name";
            sensors_parsed->clear();
            return false;
        }

        std::string sensor_type_str = sensors[i]["Type"].asString();
        LOG(INFO) << "Sensor[" << name << "]'s Type: " << sensor_type_str;
        TemperatureType_2_0 sensor_type;

        if (!getTypeFromString(sensor_type_str, &sensor_type)) {
            LOG(ERROR) << "Invalid "
                       << "Sensor[" << name << "]'s Type: " << sensor_type_str;
            sensors_parsed->clear();
            return false;
        }

        bool send_cb = false;
        if (sensors[i]["Monitor"].empty() || !sensors[i]["Monitor"].isBool()) {
            LOG(INFO) << "Failed to read Sensor[" << name << "]'s Monitor, set to 'false'";
        } else if (sensors[i]["Monitor"].asBool()) {
            send_cb = true;
        }
        LOG(INFO) << "Sensor[" << name << "]'s SendCallback: " << std::boolalpha << send_cb
                  << std::noboolalpha;

        bool send_powerhint = false;
        if (sensors[i]["SendPowerHint"].empty() || !sensors[i]["SendPowerHint"].isBool()) {
            LOG(INFO) << "Failed to read Sensor[" << name << "]'s SendPowerHint, set to 'false'";
        } else if (sensors[i]["SendPowerHint"].asBool()) {
            send_powerhint = true;
        }
        LOG(INFO) << "Sensor[" << name << "]'s SendPowerHint: " << std::boolalpha << send_powerhint
                  << std::noboolalpha;

        bool is_hidden = false;
        if (sensors[i]["Hidden"].empty() || !sensors[i]["Hidden"].isBool()) {
            LOG(INFO) << "Failed to read Sensor[" << name << "]'s Hidden, set to 'false'";
        } else if (sensors[i]["Hidden"].asBool()) {
            is_hidden = true;
        }
        LOG(INFO) << "Sensor[" << name << "]'s Hidden: " << std::boolalpha << is_hidden
                  << std::noboolalpha;

        std::array<float, kThrottlingSeverityCount> hot_thresholds;
        hot_thresholds.fill(NAN);
        std::array<float, kThrottlingSeverityCount> cold_thresholds;
        cold_thresholds.fill(NAN);
        std::array<float, kThrottlingSeverityCount> hot_hysteresis;
        hot_hysteresis.fill(0.0);
        std::array<float, kThrottlingSeverityCount> cold_hysteresis;
        cold_hysteresis.fill(0.0);
        std::vector<std::string> linked_sensors;
        std::vector<float> coefficients;
        float offset = 0;
        std::string trigger_sensor;

        FormulaOption formula = FormulaOption::COUNT_THRESHOLD;
        bool is_virtual_sensor = false;
        if (sensors[i]["VirtualSensor"].empty() || !sensors[i]["VirtualSensor"].isBool()) {
            LOG(INFO) << "Failed to read Sensor[" << name << "]'s VirtualSensor, set to 'false'";
        } else {
            is_virtual_sensor = sensors[i]["VirtualSensor"].asBool();
        }
        Json::Value values = sensors[i]["HotThreshold"];
        if (!values.size()) {
            LOG(INFO) << "Sensor[" << name << "]'s HotThreshold, default all to NAN";
        } else if (values.size() != kThrottlingSeverityCount) {
            LOG(ERROR) << "Invalid "
                       << "Sensor[" << name << "]'s HotThreshold count:" << values.size();
            sensors_parsed->clear();
            return false;
        } else {
            float min = std::numeric_limits<float>::min();
            for (Json::Value::ArrayIndex j = 0; j < kThrottlingSeverityCount; ++j) {
                hot_thresholds[j] = getFloatFromValue(values[j]);
                if (!std::isnan(hot_thresholds[j])) {
                    if (hot_thresholds[j] < min) {
                        LOG(ERROR) << "Invalid "
                                   << "Sensor[" << name << "]'s HotThreshold[j" << j
                                   << "]: " << hot_thresholds[j] << " < " << min;
                        sensors_parsed->clear();
                        return false;
                    }
                    min = hot_thresholds[j];
                }
                LOG(INFO) << "Sensor[" << name << "]'s HotThreshold[" << j
                          << "]: " << hot_thresholds[j];
            }
        }

        values = sensors[i]["HotHysteresis"];
        if (!values.size()) {
            LOG(INFO) << "Sensor[" << name << "]'s HotHysteresis, default all to 0.0";
        } else if (values.size() != kThrottlingSeverityCount) {
            LOG(ERROR) << "Invalid "
                       << "Sensor[" << name << "]'s HotHysteresis, count:" << values.size();
            sensors_parsed->clear();
            return false;
        } else {
            for (Json::Value::ArrayIndex j = 0; j < kThrottlingSeverityCount; ++j) {
                hot_hysteresis[j] = getFloatFromValue(values[j]);
                if (std::isnan(hot_hysteresis[j])) {
                    LOG(ERROR) << "Invalid "
                               << "Sensor[" << name << "]'s HotHysteresis: " << hot_hysteresis[j];
                    sensors_parsed->clear();
                    return false;
                }
                LOG(INFO) << "Sensor[" << name << "]'s HotHysteresis[" << j
                          << "]: " << hot_hysteresis[j];
            }
        }

        for (Json::Value::ArrayIndex j = 0; j < (kThrottlingSeverityCount - 1); ++j) {
            if (std::isnan(hot_thresholds[j])) {
                continue;
            }
            for (auto k = j + 1; k < kThrottlingSeverityCount; ++k) {
                if (std::isnan(hot_thresholds[k])) {
                    continue;
                } else if (hot_thresholds[j] > (hot_thresholds[k] - hot_hysteresis[k])) {
                    LOG(ERROR) << "Sensor[" << name << "]'s hot threshold " << j
                               << " is overlapped";
                    sensors_parsed->clear();
                    return false;
                } else {
                    break;
                }
            }
        }

        values = sensors[i]["ColdThreshold"];
        if (!values.size()) {
            LOG(INFO) << "Sensor[" << name << "]'s ColdThreshold, default all to NAN";
        } else if (values.size() != kThrottlingSeverityCount) {
            LOG(ERROR) << "Invalid "
                       << "Sensor[" << name << "]'s ColdThreshold count:" << values.size();
            sensors_parsed->clear();
            return false;
        } else {
            float max = std::numeric_limits<float>::max();
            for (Json::Value::ArrayIndex j = 0; j < kThrottlingSeverityCount; ++j) {
                cold_thresholds[j] = getFloatFromValue(values[j]);
                if (!std::isnan(cold_thresholds[j])) {
                    if (cold_thresholds[j] > max) {
                        LOG(ERROR) << "Invalid "
                                   << "Sensor[" << name << "]'s ColdThreshold[j" << j
                                   << "]: " << cold_thresholds[j] << " > " << max;
                        sensors_parsed->clear();
                        return false;
                    }
                    max = cold_thresholds[j];
                }
                LOG(INFO) << "Sensor[" << name << "]'s ColdThreshold[" << j
                          << "]: " << cold_thresholds[j];
            }
        }

        values = sensors[i]["ColdHysteresis"];
        if (!values.size()) {
            LOG(INFO) << "Sensor[" << name << "]'s ColdHysteresis, default all to 0.0";
        } else if (values.size() != kThrottlingSeverityCount) {
            LOG(ERROR) << "Invalid "
                       << "Sensor[" << name << "]'s ColdHysteresis count:" << values.size();
            sensors_parsed->clear();
            return false;
        } else {
            for (Json::Value::ArrayIndex j = 0; j < kThrottlingSeverityCount; ++j) {
                cold_hysteresis[j] = getFloatFromValue(values[j]);
                if (std::isnan(cold_hysteresis[j])) {
                    LOG(ERROR) << "Invalid "
                               << "Sensor[" << name << "]'s ColdHysteresis: " << cold_hysteresis[j];
                    sensors_parsed->clear();
                    return false;
                }
                LOG(INFO) << "Sensor[" << name << "]'s ColdHysteresis[" << j
                          << "]: " << cold_hysteresis[j];
            }
        }

        for (Json::Value::ArrayIndex j = 0; j < (kThrottlingSeverityCount - 1); ++j) {
            if (std::isnan(cold_thresholds[j])) {
                continue;
            }
            for (auto k = j + 1; k < kThrottlingSeverityCount; ++k) {
                if (std::isnan(cold_thresholds[k])) {
                    continue;
                } else if (cold_thresholds[j] < (cold_thresholds[k] + cold_hysteresis[k])) {
                    LOG(ERROR) << "Sensor[" << name << "]'s cold threshold " << j
                               << " is overlapped";
                    sensors_parsed->clear();
                    return false;
                } else {
                    break;
                }
            }
        }

        if (is_virtual_sensor) {
            values = sensors[i]["Combination"];
            if (values.size()) {
                linked_sensors.reserve(values.size());
                for (Json::Value::ArrayIndex j = 0; j < values.size(); ++j) {
                    linked_sensors.emplace_back(values[j].asString());
                    LOG(INFO) << "Sensor[" << name << "]'s combination[" << j
                              << "]: " << linked_sensors[j];
                }
            } else {
                LOG(ERROR) << "Sensor[" << name << "] has no combination setting";
                sensors_parsed->clear();
                return false;
            }

            values = sensors[i]["Coefficient"];
            if (values.size()) {
                coefficients.reserve(values.size());
                for (Json::Value::ArrayIndex j = 0; j < values.size(); ++j) {
                    coefficients.emplace_back(getFloatFromValue(values[j]));
                    LOG(INFO) << "Sensor[" << name << "]'s coefficient[" << j
                              << "]: " << coefficients[j];
                }
            } else {
                LOG(ERROR) << "Sensor[" << name << "] has no coefficient setting";
                sensors_parsed->clear();
                return false;
            }

            if (linked_sensors.size() != coefficients.size()) {
                LOG(ERROR) << "Sensor[" << name
                           << "]'s combination size is not matched with coefficient size";
                sensors_parsed->clear();
                return false;
            }

            if (!sensors[i]["Offset"].empty()) {
                offset = sensors[i]["Offset"].asFloat();
            }

            trigger_sensor = sensors[i]["TriggerSensor"].asString();
            if (sensors[i]["Formula"].asString().compare("COUNT_THRESHOLD") == 0) {
                formula = FormulaOption::COUNT_THRESHOLD;
            } else if (sensors[i]["Formula"].asString().compare("WEIGHTED_AVG") == 0) {
                formula = FormulaOption::WEIGHTED_AVG;
            } else if (sensors[i]["Formula"].asString().compare("MAXIMUM") == 0) {
                formula = FormulaOption::MAXIMUM;
            } else if (sensors[i]["Formula"].asString().compare("MINIMUM") == 0) {
                formula = FormulaOption::MINIMUM;
            } else {
                LOG(ERROR) << "Sensor[" << name << "]'s Formula is invalid";
                sensors_parsed->clear();
                return false;
            }
        }

        std::string temp_path;
        if (!sensors[i]["TempPath"].empty()) {
            temp_path = sensors[i]["TempPath"].asString();
            LOG(INFO) << "Sensor[" << name << "]'s TempPath: " << temp_path;
        }

        float vr_threshold = NAN;
        if (!sensors[i]["VrThreshold"].empty()) {
            vr_threshold = getFloatFromValue(sensors[i]["VrThreshold"]);
            LOG(INFO) << "Sensor[" << name << "]'s VrThreshold: " << vr_threshold;
        }
        float multiplier = sensors[i]["Multiplier"].asFloat();
        LOG(INFO) << "Sensor[" << name << "]'s Multiplier: " << multiplier;

        std::chrono::milliseconds polling_delay = kUeventPollTimeoutMs;
        if (!sensors[i]["PollingDelay"].empty()) {
            const auto value = getIntFromValue(sensors[i]["PollingDelay"]);
            polling_delay = (value > 0) ? std::chrono::milliseconds(value)
                                        : std::chrono::milliseconds::max();
        }
        LOG(INFO) << "Sensor[" << name << "]'s Polling delay: " << polling_delay.count();

        std::chrono::milliseconds passive_delay = kMinPollIntervalMs;
        if (!sensors[i]["PassiveDelay"].empty()) {
            const auto value = getIntFromValue(sensors[i]["PassiveDelay"]);
            passive_delay = (value > 0) ? std::chrono::milliseconds(value)
                                        : std::chrono::milliseconds::max();
        }
        LOG(INFO) << "Sensor[" << name << "]'s Passive delay: " << passive_delay.count();

        std::chrono::milliseconds time_resolution;
        if (sensors[i]["TimeResolution"].empty()) {
            time_resolution = kMinPollIntervalMs;
        } else {
            time_resolution =
                    std::chrono::milliseconds(getIntFromValue(sensors[i]["TimeResolution"]));
        }
        LOG(INFO) << "Sensor[" << name << "]'s Time resolution: " << time_resolution.count();

        bool support_pid = false;
        std::array<float, kThrottlingSeverityCount> k_po;
        k_po.fill(0.0);
        std::array<float, kThrottlingSeverityCount> k_pu;
        k_pu.fill(0.0);
        std::array<float, kThrottlingSeverityCount> k_i;
        k_i.fill(0.0);
        std::array<float, kThrottlingSeverityCount> k_d;
        k_d.fill(0.0);
        std::array<float, kThrottlingSeverityCount> i_max;
        i_max.fill(NAN);
        std::array<float, kThrottlingSeverityCount> max_alloc_power;
        max_alloc_power.fill(NAN);
        std::array<float, kThrottlingSeverityCount> min_alloc_power;
        min_alloc_power.fill(NAN);
        std::array<float, kThrottlingSeverityCount> s_power;
        s_power.fill(NAN);
        std::array<float, kThrottlingSeverityCount> i_cutoff;
        i_cutoff.fill(NAN);
        float err_integral_default = 0.0;

        // Parse PID parameters
        if (!sensors[i]["PIDInfo"].empty()) {
            LOG(INFO) << "Start to parse"
                      << " Sensor[" << name << "]'s K_Po";
            if (sensors[i]["PIDInfo"]["K_Po"].empty() ||
                !getFloatFromJsonValues(sensors[i]["PIDInfo"]["K_Po"], &k_po, false, false)) {
                LOG(ERROR) << "Sensor[" << name << "]: Failed to parse K_Po";
                sensors_parsed->clear();
                return false;
            }
            LOG(INFO) << "Start to parse"
                      << " Sensor[" << name << "]'s  K_Pu";
            if (sensors[i]["PIDInfo"]["K_Pu"].empty() ||
                !getFloatFromJsonValues(sensors[i]["PIDInfo"]["K_Pu"], &k_pu, false, false)) {
                LOG(ERROR) << "Sensor[" << name << "]: Failed to parse K_Pu";
                sensors_parsed->clear();
                return false;
            }
            LOG(INFO) << "Start to parse"
                      << " Sensor[" << name << "]'s K_I";
            if (sensors[i]["PIDInfo"]["K_I"].empty() ||
                !getFloatFromJsonValues(sensors[i]["PIDInfo"]["K_I"], &k_i, false, false)) {
                LOG(ERROR) << "Sensor[" << name << "]: Failed to parse K_I";
                sensors_parsed->clear();
                return false;
            }
            LOG(INFO) << "Start to parse"
                      << " Sensor[" << name << "]'s K_D";
            if (sensors[i]["PIDInfo"]["K_D"].empty() ||
                !getFloatFromJsonValues(sensors[i]["PIDInfo"]["K_D"], &k_d, false, false)) {
                LOG(ERROR) << "Sensor[" << name << "]: Failed to parse K_D";
                sensors_parsed->clear();
                return false;
            }
            LOG(INFO) << "Start to parse"
                      << " Sensor[" << name << "]'s I_Max";
            if (sensors[i]["PIDInfo"]["I_Max"].empty() ||
                !getFloatFromJsonValues(sensors[i]["PIDInfo"]["I_Max"], &i_max, false, false)) {
                LOG(ERROR) << "Sensor[" << name << "]: Failed to parse I_Max";
                sensors_parsed->clear();
                return false;
            }
            LOG(INFO) << "Start to parse"
                      << " Sensor[" << name << "]'s MaxAllocPower";
            if (sensors[i]["PIDInfo"]["MaxAllocPower"].empty() ||
                !getFloatFromJsonValues(sensors[i]["PIDInfo"]["MaxAllocPower"], &max_alloc_power,
                                        false, true)) {
                LOG(ERROR) << "Sensor[" << name << "]: Failed to parse MaxAllocPower";
                sensors_parsed->clear();
                return false;
            }
            LOG(INFO) << "Start to parse"
                      << " Sensor[" << name << "]'s MinAllocPower";
            if (sensors[i]["PIDInfo"]["MinAllocPower"].empty() ||
                !getFloatFromJsonValues(sensors[i]["PIDInfo"]["MinAllocPower"], &min_alloc_power,
                                        false, true)) {
                LOG(ERROR) << "Sensor[" << name << "]: Failed to parse MinAllocPower";
                sensors_parsed->clear();
                return false;
            }
            LOG(INFO) << "Start to parse"
                      << " Sensor[" << name << "]'s S_Power";
            if (sensors[i]["PIDInfo"]["S_Power"].empty() ||
                !getFloatFromJsonValues(sensors[i]["PIDInfo"]["S_Power"], &s_power, false, true)) {
                LOG(ERROR) << "Sensor[" << name << "]: Failed to parse S_Power";
                sensors_parsed->clear();
                return false;
            }
            LOG(INFO) << "Start to parse"
                      << " Sensor[" << name << "]'s I_Cutoff";
            if (sensors[i]["PIDInfo"]["I_Cutoff"].empty() ||
                !getFloatFromJsonValues(sensors[i]["PIDInfo"]["I_Cutoff"], &i_cutoff, false,
                                        false)) {
                LOG(ERROR) << "Sensor[" << name << "]: Failed to parse I_Cutoff";
                sensors_parsed->clear();
                return false;
            }
            LOG(INFO) << "Start to parse"
                      << " Sensor[" << name << "]'s E_Integral_Default";
            err_integral_default = getFloatFromValue(sensors[i]["PIDInfo"]["E_Integral_Default"]);
            LOG(INFO) << "Sensor[" << name << "]'s E_Integral_Default: " << err_integral_default;
            // Confirm we have at least one valid PID combination
            bool valid_pid_combination = false;
            for (Json::Value::ArrayIndex j = 0; j < kThrottlingSeverityCount; ++j) {
                if (!std::isnan(s_power[j])) {
                    if (std::isnan(k_po[j]) || std::isnan(k_pu[j]) || std::isnan(k_i[j]) ||
                        std::isnan(k_d[j]) || std::isnan(i_max[j]) ||
                        std::isnan(max_alloc_power[j]) || std::isnan(min_alloc_power[j]) ||
                        std::isnan(i_cutoff[j])) {
                        valid_pid_combination = false;
                        break;
                    } else {
                        valid_pid_combination = true;
                    }
                }
            }
            if (!valid_pid_combination) {
                LOG(ERROR) << "Sensor[" << name << "]: Invalid PID parameters combinations";
                sensors_parsed->clear();
                return false;
            } else {
                support_pid = true;
            }
        }

        // Parse binded cooling device
        bool support_hard_limit = false;
        std::unordered_map<std::string, BindedCdevInfo> binded_cdev_info_map;
        values = sensors[i]["BindedCdevInfo"];
        for (Json::Value::ArrayIndex j = 0; j < values.size(); ++j) {
            Json::Value sub_values;
            const std::string &cdev_name = values[j]["CdevRequest"].asString();
            ThrottlingArray cdev_weight_for_pid;
            cdev_weight_for_pid.fill(NAN);
            CdevArray cdev_ceiling;
            cdev_ceiling.fill(std::numeric_limits<int>::max());
            int max_release_step = std::numeric_limits<int>::max();
            int max_throttle_step = std::numeric_limits<int>::max();
            if (support_pid) {
                if (!values[j]["CdevWeightForPID"].empty()) {
                    LOG(INFO) << "Sensor[" << name << "]: Star to parse " << cdev_name
                              << "'s CdevWeightForPID";
                    if (!getFloatFromJsonValues(values[j]["CdevWeightForPID"], &cdev_weight_for_pid,
                                                false, false)) {
                        LOG(ERROR) << "Failed to parse CdevWeightForPID";
                        sensors_parsed->clear();
                        return false;
                    }
                }
                if (!values[j]["CdevCeiling"].empty()) {
                    LOG(INFO) << "Sensor[" << name
                              << "]: Start to parse CdevCeiling: " << cdev_name;
                    if (!getIntFromJsonValues(values[j]["CdevCeiling"], &cdev_ceiling, false,
                                              false)) {
                        LOG(ERROR) << "Failed to parse CdevCeiling";
                        sensors_parsed->clear();
                        return false;
                    }
                }
                if (!values[j]["MaxReleaseStep"].empty()) {
                    max_release_step = getIntFromValue(values[j]["MaxReleaseStep"]);
                    if (max_release_step < 0) {
                        LOG(ERROR) << "Sensor[" << name << "]'s " << cdev_name
                                   << " MaxReleaseStep: " << max_release_step;
                        sensors_parsed->clear();
                        return false;
                    } else {
                        LOG(INFO) << "Sensor[" << name << "]'s " << cdev_name
                                  << " MaxReleaseStep: " << max_release_step;
                    }
                }
                if (!values[j]["MaxThrottleStep"].empty()) {
                    max_throttle_step = getIntFromValue(values[j]["MaxThrottleStep"]);
                    if (max_throttle_step < 0) {
                        LOG(ERROR) << "Sensor[" << name << "]'s " << cdev_name
                                   << " MaxThrottleStep: " << max_throttle_step;
                        sensors_parsed->clear();
                        return false;
                    } else {
                        LOG(INFO) << "Sensor[" << name << "]'s " << cdev_name
                                  << " MaxThrottleStep: " << max_throttle_step;
                    }
                }
            }
            CdevArray limit_info;
            limit_info.fill(0);
            ThrottlingArray power_thresholds;
            power_thresholds.fill(NAN);

            ReleaseLogic release_logic = ReleaseLogic::NONE;

            sub_values = values[j]["LimitInfo"];
            if (sub_values.size()) {
                LOG(INFO) << "Sensor[" << name << "]: Start to parse LimitInfo: " << cdev_name;
                if (!getIntFromJsonValues(sub_values, &limit_info, false, false)) {
                    LOG(ERROR) << "Failed to parse LimitInfo";
                    sensors_parsed->clear();
                    return false;
                }
                support_hard_limit = true;
            }

            // Parse linked power info
            std::string power_rail;
            bool high_power_check = false;
            bool throttling_with_power_link = false;
            CdevArray cdev_floor_with_power_link;
            cdev_floor_with_power_link.fill(0);

            const bool power_link_disabled =
                    android::base::GetBoolProperty(kPowerLinkDisabledProperty.data(), false);
            if (!power_link_disabled) {
                power_rail = values[j]["BindedPowerRail"].asString();

                if (values[j]["HighPowerCheck"].asBool()) {
                    high_power_check = true;
                }
                LOG(INFO) << "Highpowercheck: " << std::boolalpha << high_power_check;

                if (values[j]["ThrottlingWithPowerLink"].asBool()) {
                    throttling_with_power_link = true;
                }
                LOG(INFO) << "ThrottlingwithPowerLink: " << std::boolalpha
                          << throttling_with_power_link;

                sub_values = values[j]["CdevFloorWithPowerLink"];
                if (sub_values.size()) {
                    LOG(INFO) << "Sensor[" << name << "]: Start to parse " << cdev_name
                              << "'s CdevFloorWithPowerLink";
                    if (!getIntFromJsonValues(sub_values, &cdev_floor_with_power_link, false,
                                              false)) {
                        LOG(ERROR) << "Failed to parse CdevFloor";
                        sensors_parsed->clear();
                        return false;
                    }
                }
                sub_values = values[j]["PowerThreshold"];
                if (sub_values.size()) {
                    LOG(INFO) << "Sensor[" << name << "]: Start to parse " << cdev_name
                              << "'s PowerThreshold";
                    if (!getFloatFromJsonValues(sub_values, &power_thresholds, false, false)) {
                        LOG(ERROR) << "Failed to parse power thresholds";
                        sensors_parsed->clear();
                        return false;
                    }
                    if (values[j]["ReleaseLogic"].asString() == "INCREASE") {
                        release_logic = ReleaseLogic::INCREASE;
                        LOG(INFO) << "Release logic: INCREASE";
                    } else if (values[j]["ReleaseLogic"].asString() == "DECREASE") {
                        release_logic = ReleaseLogic::DECREASE;
                        LOG(INFO) << "Release logic: DECREASE";
                    } else if (values[j]["ReleaseLogic"].asString() == "STEPWISE") {
                        release_logic = ReleaseLogic::STEPWISE;
                        LOG(INFO) << "Release logic: STEPWISE";
                    } else if (values[j]["ReleaseLogic"].asString() == "RELEASE_TO_FLOOR") {
                        release_logic = ReleaseLogic::RELEASE_TO_FLOOR;
                        LOG(INFO) << "Release logic: RELEASE_TO_FLOOR";
                    } else {
                        LOG(ERROR) << "Release logic is invalid";
                        sensors_parsed->clear();
                        return false;
                    }
                }
            }

            binded_cdev_info_map[cdev_name] = {
                    .limit_info = limit_info,
                    .power_thresholds = power_thresholds,
                    .release_logic = release_logic,
                    .high_power_check = high_power_check,
                    .throttling_with_power_link = throttling_with_power_link,
                    .cdev_weight_for_pid = cdev_weight_for_pid,
                    .cdev_ceiling = cdev_ceiling,
                    .max_release_step = max_release_step,
                    .max_throttle_step = max_throttle_step,
                    .cdev_floor_with_power_link = cdev_floor_with_power_link,
                    .power_rail = power_rail,
            };
        }

        if (is_hidden && send_cb) {
            LOG(ERROR) << "is_hidden and send_cb cannot be enabled together";
            sensors_parsed->clear();
            return false;
        }

        bool is_watch = (send_cb | send_powerhint | support_pid | support_hard_limit);
        LOG(INFO) << "Sensor[" << name << "]'s is_watch: " << std::boolalpha << is_watch;

        std::unique_ptr<VirtualSensorInfo> virtual_sensor_info;
        if (is_virtual_sensor) {
            virtual_sensor_info.reset(new VirtualSensorInfo{linked_sensors, coefficients, offset,
                                                            trigger_sensor, formula});
        }

        std::shared_ptr<ThrottlingInfo> throttling_info(
                new ThrottlingInfo{k_po, k_pu, k_i, k_d, i_max, max_alloc_power, min_alloc_power,
                                   s_power, i_cutoff, err_integral_default, binded_cdev_info_map});

        (*sensors_parsed)[name] = {
                .type = sensor_type,
                .hot_thresholds = hot_thresholds,
                .cold_thresholds = cold_thresholds,
                .hot_hysteresis = hot_hysteresis,
                .cold_hysteresis = cold_hysteresis,
                .temp_path = temp_path,
                .vr_threshold = vr_threshold,
                .multiplier = multiplier,
                .polling_delay = polling_delay,
                .passive_delay = passive_delay,
                .time_resolution = time_resolution,
                .send_cb = send_cb,
                .send_powerhint = send_powerhint,
                .is_watch = is_watch,
                .is_hidden = is_hidden,
                .virtual_sensor_info = std::move(virtual_sensor_info),
                .throttling_info = std::move(throttling_info),
        };

        ++total_parsed;
    }
    LOG(INFO) << total_parsed << " Sensors parsed successfully";
    return true;
}

bool ParseCoolingDevice(std::string_view config_path,
                        std::unordered_map<std::string, CdevInfo> *cooling_devices_parsed) {
    std::string json_doc;
    if (!android::base::ReadFileToString(config_path.data(), &json_doc)) {
        LOG(ERROR) << "Failed to read JSON config from " << config_path;
        return false;
    }
    Json::Value root;
    Json::CharReaderBuilder builder;
    std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
    std::string errorMessage;

    if (!reader->parse(&*json_doc.begin(), &*json_doc.end(), &root, &errorMessage)) {
        LOG(ERROR) << "Failed to parse JSON config: " << errorMessage;
        return false;
    }

    Json::Value cooling_devices = root["CoolingDevices"];
    std::size_t total_parsed = 0;
    std::unordered_set<std::string> cooling_devices_name_parsed;

    for (Json::Value::ArrayIndex i = 0; i < cooling_devices.size(); ++i) {
        const std::string &name = cooling_devices[i]["Name"].asString();
        LOG(INFO) << "CoolingDevice[" << i << "]'s Name: " << name;
        if (name.empty()) {
            LOG(ERROR) << "Failed to read "
                       << "CoolingDevice[" << i << "]'s Name";
            cooling_devices_parsed->clear();
            return false;
        }

        auto result = cooling_devices_name_parsed.insert(name.data());
        if (!result.second) {
            LOG(ERROR) << "Duplicate CoolingDevice[" << i << "]'s Name";
            cooling_devices_parsed->clear();
            return false;
        }

        std::string cooling_device_type_str = cooling_devices[i]["Type"].asString();
        LOG(INFO) << "CoolingDevice[" << name << "]'s Type: " << cooling_device_type_str;
        CoolingType cooling_device_type;

        if (!getTypeFromString(cooling_device_type_str, &cooling_device_type)) {
            LOG(ERROR) << "Invalid "
                       << "CoolingDevice[" << name << "]'s Type: " << cooling_device_type_str;
            cooling_devices_parsed->clear();
            return false;
        }

        const std::string &read_path = cooling_devices[i]["ReadPath"].asString();
        LOG(INFO) << "Cdev Read Path: " << (read_path.empty() ? "default" : read_path);

        const std::string &write_path = cooling_devices[i]["WritePath"].asString();
        LOG(INFO) << "Cdev Write Path: " << (write_path.empty() ? "default" : write_path);

        std::vector<float> state2power;
        Json::Value values = cooling_devices[i]["State2Power"];
        if (values.size()) {
            state2power.reserve(values.size());
            for (Json::Value::ArrayIndex j = 0; j < values.size(); ++j) {
                state2power.emplace_back(getFloatFromValue(values[j]));
                LOG(INFO) << "Cooling device[" << name << "]'s Power2State[" << j
                          << "]: " << state2power[j];
            }
        } else {
            LOG(INFO) << "CoolingDevice[" << i << "]'s Name: " << name
                      << " does not support State2Power";
        }

        const std::string &power_rail = cooling_devices[i]["PowerRail"].asString();
        LOG(INFO) << "Cooling device power rail : " << power_rail;

        (*cooling_devices_parsed)[name] = {
                .type = cooling_device_type,
                .read_path = read_path,
                .write_path = write_path,
                .state2power = state2power,
        };
        ++total_parsed;
    }
    LOG(INFO) << total_parsed << " CoolingDevices parsed successfully";
    return true;
}

bool ParsePowerRailInfo(std::string_view config_path,
                        std::unordered_map<std::string, PowerRailInfo> *power_rails_parsed) {
    std::string json_doc;
    if (!android::base::ReadFileToString(config_path.data(), &json_doc)) {
        LOG(ERROR) << "Failed to read JSON config from " << config_path;
        return false;
    }
    Json::Value root;
    Json::CharReaderBuilder builder;
    std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
    std::string errorMessage;

    if (!reader->parse(&*json_doc.begin(), &*json_doc.end(), &root, &errorMessage)) {
        LOG(ERROR) << "Failed to parse JSON config: " << errorMessage;
        return false;
    }

    Json::Value power_rails = root["PowerRails"];
    std::size_t total_parsed = 0;
    std::unordered_set<std::string> power_rails_name_parsed;

    for (Json::Value::ArrayIndex i = 0; i < power_rails.size(); ++i) {
        const std::string &name = power_rails[i]["Name"].asString();
        LOG(INFO) << "PowerRail[" << i << "]'s Name: " << name;
        if (name.empty()) {
            LOG(ERROR) << "Failed to read "
                       << "PowerRail[" << i << "]'s Name";
            power_rails_parsed->clear();
            return false;
        }

        std::string rail;
        if (power_rails[i]["Rail"].empty()) {
            rail = name;
        } else {
            rail = power_rails[i]["Rail"].asString();
        }
        LOG(INFO) << "PowerRail[" << i << "]'s Rail: " << rail;

        std::vector<std::string> linked_power_rails;
        std::vector<float> coefficients;
        float offset = 0;
        FormulaOption formula = FormulaOption::COUNT_THRESHOLD;
        bool is_virtual_power_rail = false;
        Json::Value values;
        int power_sample_count = 0;
        std::chrono::milliseconds power_sample_delay;

        if (!power_rails[i]["VirtualRails"].empty() && power_rails[i]["VirtualRails"].isBool()) {
            is_virtual_power_rail = power_rails[i]["VirtualRails"].asBool();
            LOG(INFO) << "PowerRails[" << name << "]'s VirtualRail, set to 'true'";
        }

        if (is_virtual_power_rail) {
            values = power_rails[i]["Combination"];
            if (values.size()) {
                linked_power_rails.reserve(values.size());
                for (Json::Value::ArrayIndex j = 0; j < values.size(); ++j) {
                    linked_power_rails.emplace_back(values[j].asString());
                    LOG(INFO) << "PowerRail[" << name << "]'s combination[" << j
                              << "]: " << linked_power_rails[j];
                }
            } else {
                LOG(ERROR) << "PowerRails[" << name << "] has no combination for VirtualRail";
                power_rails_parsed->clear();
                return false;
            }

            values = power_rails[i]["Coefficient"];
            if (values.size()) {
                coefficients.reserve(values.size());
                for (Json::Value::ArrayIndex j = 0; j < values.size(); ++j) {
                    coefficients.emplace_back(getFloatFromValue(values[j]));
                    LOG(INFO) << "PowerRail[" << name << "]'s coefficient[" << j
                              << "]: " << coefficients[j];
                }
            } else {
                LOG(ERROR) << "PowerRails[" << name << "] has no coefficient for VirtualRail";
                power_rails_parsed->clear();
                return false;
            }

            if (linked_power_rails.size() != coefficients.size()) {
                LOG(ERROR) << "PowerRails[" << name
                           << "]'s combination size is not matched with coefficient size";
                power_rails_parsed->clear();
                return false;
            }

            if (!power_rails[i]["Offset"].empty()) {
                offset = power_rails[i]["Offset"].asFloat();
            }

            if (linked_power_rails.size() != coefficients.size()) {
                LOG(ERROR) << "PowerRails[" << name
                           << "]'s combination size is not matched with coefficient size";
                power_rails_parsed->clear();
                return false;
            }

            if (power_rails[i]["Formula"].asString().compare("COUNT_THRESHOLD") == 0) {
                formula = FormulaOption::COUNT_THRESHOLD;
            } else if (power_rails[i]["Formula"].asString().compare("WEIGHTED_AVG") == 0) {
                formula = FormulaOption::WEIGHTED_AVG;
            } else if (power_rails[i]["Formula"].asString().compare("MAXIMUM") == 0) {
                formula = FormulaOption::MAXIMUM;
            } else if (power_rails[i]["Formula"].asString().compare("MINIMUM") == 0) {
                formula = FormulaOption::MINIMUM;
            } else {
                LOG(ERROR) << "PowerRails[" << name << "]'s Formula is invalid";
                power_rails_parsed->clear();
                return false;
            }
        }

        std::unique_ptr<VirtualPowerRailInfo> virtual_power_rail_info;
        if (is_virtual_power_rail) {
            virtual_power_rail_info.reset(
                    new VirtualPowerRailInfo{linked_power_rails, coefficients, offset, formula});
        }

        power_sample_count = power_rails[i]["PowerSampleCount"].asInt();
        LOG(INFO) << "Power sample Count: " << power_sample_count;

        if (!power_rails[i]["PowerSampleDelay"]) {
            power_sample_delay = std::chrono::milliseconds::max();
        } else {
            power_sample_delay =
                    std::chrono::milliseconds(getIntFromValue(power_rails[i]["PowerSampleDelay"]));
        }

        (*power_rails_parsed)[name] = {
                .rail = rail,
                .power_sample_count = power_sample_count,
                .power_sample_delay = power_sample_delay,
                .virtual_power_rail_info = std::move(virtual_power_rail_info),
        };
        ++total_parsed;
    }
    LOG(INFO) << total_parsed << " PowerRails parsed successfully";
    return true;
}

}  // namespace implementation
}  // namespace V2_0
}  // namespace thermal
}  // namespace hardware
}  // namespace android

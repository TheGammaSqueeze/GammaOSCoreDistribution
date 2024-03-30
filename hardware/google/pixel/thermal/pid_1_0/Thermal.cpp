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

#include "Thermal.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <hidl/HidlTransportSupport.h>
#include <utils/Trace.h>

#include <cerrno>
#include <mutex>
#include <string>

#include "thermal-helper.h"

namespace android {
namespace hardware {
namespace thermal {
namespace V2_0 {
namespace implementation {

namespace {

using ::android::hardware::interfacesEqual;
using ::android::hardware::thermal::V1_0::ThermalStatus;
using ::android::hardware::thermal::V1_0::ThermalStatusCode;

template <typename T, typename U>
Return<void> setFailureAndCallback(T _hidl_cb, hidl_vec<U> data, std::string_view debug_msg) {
    ThermalStatus status;
    status.code = ThermalStatusCode::FAILURE;
    status.debugMessage = debug_msg.data();
    _hidl_cb(status, data);
    return Void();
}

template <typename T, typename U>
Return<void> setInitFailureAndCallback(T _hidl_cb, hidl_vec<U> data) {
    return setFailureAndCallback(_hidl_cb, data, "Failure initializing thermal HAL");
}

}  // namespace

// On init we will spawn a thread which will continually watch for
// throttling.  When throttling is seen, if we have a callback registered
// the thread will call notifyThrottling() else it will log the dropped
// throttling event and do nothing.  The thread is only killed when
// Thermal() is killed.
Thermal::Thermal()
    : thermal_helper_(
              std::bind(&Thermal::sendThermalChangedCallback, this, std::placeholders::_1)) {}

// Methods from ::android::hardware::thermal::V1_0::IThermal.
Return<void> Thermal::getTemperatures(getTemperatures_cb _hidl_cb) {
    ThermalStatus status;
    status.code = ThermalStatusCode::SUCCESS;
    hidl_vec<Temperature_1_0> temperatures;

    if (!thermal_helper_.isInitializedOk()) {
        LOG(ERROR) << "ThermalHAL not initialized properly.";
        return setInitFailureAndCallback(_hidl_cb, temperatures);
    }

    if (!thermal_helper_.fillTemperatures(&temperatures)) {
        return setFailureAndCallback(_hidl_cb, temperatures, "Failed to read thermal sensors.");
    }

    _hidl_cb(status, temperatures);
    return Void();
}

Return<void> Thermal::getCpuUsages(getCpuUsages_cb _hidl_cb) {
    ThermalStatus status;
    status.code = ThermalStatusCode::SUCCESS;
    hidl_vec<CpuUsage> cpu_usages;

    if (!thermal_helper_.isInitializedOk()) {
        return setInitFailureAndCallback(_hidl_cb, cpu_usages);
    }

    if (!thermal_helper_.fillCpuUsages(&cpu_usages)) {
        return setFailureAndCallback(_hidl_cb, cpu_usages, "Failed to get CPU usages.");
    }

    _hidl_cb(status, cpu_usages);
    return Void();
}

Return<void> Thermal::getCoolingDevices(getCoolingDevices_cb _hidl_cb) {
    ThermalStatus status;
    status.code = ThermalStatusCode::SUCCESS;
    hidl_vec<CoolingDevice_1_0> cooling_devices;

    if (!thermal_helper_.isInitializedOk()) {
        return setInitFailureAndCallback(_hidl_cb, cooling_devices);
    }
    _hidl_cb(status, cooling_devices);
    return Void();
}

Return<void> Thermal::getCurrentTemperatures(bool filterType, TemperatureType_2_0 type,
                                             getCurrentTemperatures_cb _hidl_cb) {
    ThermalStatus status;
    status.code = ThermalStatusCode::SUCCESS;
    hidl_vec<Temperature_2_0> temperatures;

    if (!thermal_helper_.isInitializedOk()) {
        LOG(ERROR) << "ThermalHAL not initialized properly.";
        return setInitFailureAndCallback(_hidl_cb, temperatures);
    }

    if (!thermal_helper_.fillCurrentTemperatures(filterType, false, type, &temperatures)) {
        return setFailureAndCallback(_hidl_cb, temperatures, "Failed to read thermal sensors.");
    }

    _hidl_cb(status, temperatures);
    return Void();
}

Return<void> Thermal::getTemperatureThresholds(bool filterType, TemperatureType_2_0 type,
                                               getTemperatureThresholds_cb _hidl_cb) {
    ThermalStatus status;
    status.code = ThermalStatusCode::SUCCESS;
    hidl_vec<TemperatureThreshold> temperatures;

    if (!thermal_helper_.isInitializedOk()) {
        LOG(ERROR) << "ThermalHAL not initialized properly.";
        return setInitFailureAndCallback(_hidl_cb, temperatures);
    }

    if (!thermal_helper_.fillTemperatureThresholds(filterType, type, &temperatures)) {
        return setFailureAndCallback(_hidl_cb, temperatures, "Failed to read thermal sensors.");
    }

    _hidl_cb(status, temperatures);
    return Void();
}

Return<void> Thermal::getCurrentCoolingDevices(bool filterType, CoolingType type,
                                               getCurrentCoolingDevices_cb _hidl_cb) {
    ThermalStatus status;
    status.code = ThermalStatusCode::SUCCESS;
    hidl_vec<CoolingDevice_2_0> cooling_devices;

    if (!thermal_helper_.isInitializedOk()) {
        LOG(ERROR) << "ThermalHAL not initialized properly.";
        return setInitFailureAndCallback(_hidl_cb, cooling_devices);
    }

    if (!thermal_helper_.fillCurrentCoolingDevices(filterType, type, &cooling_devices)) {
        return setFailureAndCallback(_hidl_cb, cooling_devices, "Failed to read cooling devices.");
    }

    _hidl_cb(status, cooling_devices);
    return Void();
}

Return<void> Thermal::registerThermalChangedCallback(const sp<IThermalChangedCallback> &callback,
                                                     bool filterType, TemperatureType_2_0 type,
                                                     registerThermalChangedCallback_cb _hidl_cb) {
    ThermalStatus status;
    hidl_vec<Temperature_2_0> temperatures;

    ATRACE_CALL();
    if (callback == nullptr) {
        status.code = ThermalStatusCode::FAILURE;
        status.debugMessage = "Invalid nullptr callback";
        LOG(ERROR) << status.debugMessage;
        _hidl_cb(status);
        return Void();
    } else {
        status.code = ThermalStatusCode::SUCCESS;
    }
    std::lock_guard<std::mutex> _lock(thermal_callback_mutex_);
    if (std::any_of(callbacks_.begin(), callbacks_.end(), [&](const CallbackSetting &c) {
            return interfacesEqual(c.callback, callback);
        })) {
        status.code = ThermalStatusCode::FAILURE;
        status.debugMessage = "Same callback registered already";
        LOG(ERROR) << status.debugMessage;
    } else {
        callbacks_.emplace_back(callback, filterType, type);
        LOG(INFO) << "a callback has been registered to ThermalHAL, isFilter: " << filterType
                  << " Type: " << android::hardware::thermal::V2_0::toString(type);
    }
    _hidl_cb(status);

    // Send notification right away after thermal callback registration
    if (thermal_helper_.fillCurrentTemperatures(filterType, true, type, &temperatures)) {
        for (const auto &t : temperatures) {
            if (!filterType || t.type == type) {
                LOG(INFO) << "Sending notification: "
                          << " Type: " << android::hardware::thermal::V2_0::toString(t.type)
                          << " Name: " << t.name << " CurrentValue: " << t.value
                          << " ThrottlingStatus: "
                          << android::hardware::thermal::V2_0::toString(t.throttlingStatus);
                callback->notifyThrottling(t);
            }
        }
    }

    return Void();
}

Return<void> Thermal::unregisterThermalChangedCallback(
        const sp<IThermalChangedCallback> &callback, unregisterThermalChangedCallback_cb _hidl_cb) {
    ThermalStatus status;
    if (callback == nullptr) {
        status.code = ThermalStatusCode::FAILURE;
        status.debugMessage = "Invalid nullptr callback";
        LOG(ERROR) << status.debugMessage;
        _hidl_cb(status);
        return Void();
    } else {
        status.code = ThermalStatusCode::SUCCESS;
    }
    bool removed = false;
    std::lock_guard<std::mutex> _lock(thermal_callback_mutex_);
    callbacks_.erase(
            std::remove_if(
                    callbacks_.begin(), callbacks_.end(),
                    [&](const CallbackSetting &c) {
                        if (interfacesEqual(c.callback, callback)) {
                            LOG(INFO)
                                    << "a callback has been unregistered to ThermalHAL, isFilter: "
                                    << c.is_filter_type << " Type: "
                                    << android::hardware::thermal::V2_0::toString(c.type);
                            removed = true;
                            return true;
                        }
                        return false;
                    }),
            callbacks_.end());
    if (!removed) {
        status.code = ThermalStatusCode::FAILURE;
        status.debugMessage = "The callback was not registered before";
        LOG(ERROR) << status.debugMessage;
    }
    _hidl_cb(status);
    return Void();
}

void Thermal::sendThermalChangedCallback(const Temperature_2_0 &t) {
    ATRACE_CALL();
    std::lock_guard<std::mutex> _lock(thermal_callback_mutex_);
    LOG(VERBOSE) << "Sending notification: "
                 << " Type: " << android::hardware::thermal::V2_0::toString(t.type)
                 << " Name: " << t.name << " CurrentValue: " << t.value << " ThrottlingStatus: "
                 << android::hardware::thermal::V2_0::toString(t.throttlingStatus);

    callbacks_.erase(
            std::remove_if(callbacks_.begin(), callbacks_.end(),
                           [&](const CallbackSetting &c) {
                               if (!c.is_filter_type || t.type == c.type) {
                                   Return<void> ret = c.callback->notifyThrottling(t);
                                   if (!ret.isOk()) {
                                       LOG(ERROR) << "a Thermal callback is dead, removed from "
                                                     "callback list.";
                                       return true;
                                   }
                                   return false;
                               }
                               return false;
                           }),
            callbacks_.end());
}

void Thermal::dumpVirtualSensorInfo(std::ostringstream *dump_buf) {
    *dump_buf << "getVirtualSensorInfo:" << std::endl;
    const auto &map = thermal_helper_.GetSensorInfoMap();
    for (const auto &sensor_info_pair : map) {
        if (sensor_info_pair.second.virtual_sensor_info != nullptr) {
            *dump_buf << " Name: " << sensor_info_pair.first << std::endl;
            *dump_buf << "  LinkedSensorName: [";
            for (size_t i = 0;
                 i < sensor_info_pair.second.virtual_sensor_info->linked_sensors.size(); i++) {
                *dump_buf << sensor_info_pair.second.virtual_sensor_info->linked_sensors[i] << " ";
            }
            *dump_buf << "]" << std::endl;
            *dump_buf << "  LinkedSensorCoefficient: [";
            for (size_t i = 0; i < sensor_info_pair.second.virtual_sensor_info->coefficients.size();
                 i++) {
                *dump_buf << sensor_info_pair.second.virtual_sensor_info->coefficients[i] << " ";
            }
            *dump_buf << "]" << std::endl;
            *dump_buf << "  Offset: " << sensor_info_pair.second.virtual_sensor_info->offset
                      << std::endl;
            *dump_buf << "  Trigger Sensor: "
                      << (sensor_info_pair.second.virtual_sensor_info->trigger_sensor.empty()
                                  ? "N/A"
                                  : sensor_info_pair.second.virtual_sensor_info->trigger_sensor)
                      << std::endl;
            *dump_buf << "  Formula: ";
            switch (sensor_info_pair.second.virtual_sensor_info->formula) {
                case FormulaOption::COUNT_THRESHOLD:
                    *dump_buf << "COUNT_THRESHOLD";
                    break;
                case FormulaOption::WEIGHTED_AVG:
                    *dump_buf << "WEIGHTED_AVG";
                    break;
                case FormulaOption::MAXIMUM:
                    *dump_buf << "MAXIMUM";
                    break;
                case FormulaOption::MINIMUM:
                    *dump_buf << "MINIMUM";
                    break;
                default:
                    *dump_buf << "NONE";
                    break;
            }

            *dump_buf << std::endl;
        }
    }
}

void Thermal::dumpThrottlingInfo(std::ostringstream *dump_buf) {
    *dump_buf << "getThrottlingInfo:" << std::endl;
    const auto &map = thermal_helper_.GetSensorInfoMap();
    const auto &thermal_throttling_status_map = thermal_helper_.GetThermalThrottlingStatusMap();
    for (const auto &name_info_pair : map) {
        if (name_info_pair.second.throttling_info->binded_cdev_info_map.size()) {
            *dump_buf << " Name: " << name_info_pair.first << std::endl;
            if (thermal_throttling_status_map.at(name_info_pair.first)
                        .pid_power_budget_map.size()) {
                *dump_buf << "  PID Info:" << std::endl;
                *dump_buf << "   K_po: [";
                for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                    *dump_buf << name_info_pair.second.throttling_info->k_po[i] << " ";
                }
                *dump_buf << "]" << std::endl;
                *dump_buf << "   K_pu: [";
                for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                    *dump_buf << name_info_pair.second.throttling_info->k_pu[i] << " ";
                }
                *dump_buf << "]" << std::endl;
                *dump_buf << "   K_i: [";
                for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                    *dump_buf << name_info_pair.second.throttling_info->k_i[i] << " ";
                }
                *dump_buf << "]" << std::endl;
                *dump_buf << "   K_d: [";
                for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                    *dump_buf << name_info_pair.second.throttling_info->k_d[i] << " ";
                }
                *dump_buf << "]" << std::endl;
                *dump_buf << "   i_max: [";
                for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                    *dump_buf << name_info_pair.second.throttling_info->i_max[i] << " ";
                }
                *dump_buf << "]" << std::endl;
                *dump_buf << "   max_alloc_power: [";
                for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                    *dump_buf << name_info_pair.second.throttling_info->max_alloc_power[i] << " ";
                }
                *dump_buf << "]" << std::endl;
                *dump_buf << "   min_alloc_power: [";
                for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                    *dump_buf << name_info_pair.second.throttling_info->min_alloc_power[i] << " ";
                }
                *dump_buf << "]" << std::endl;
                *dump_buf << "   s_power: [";
                for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                    *dump_buf << name_info_pair.second.throttling_info->s_power[i] << " ";
                }
                *dump_buf << "]" << std::endl;
                *dump_buf << "   i_cutoff: [";
                for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                    *dump_buf << name_info_pair.second.throttling_info->i_cutoff[i] << " ";
                }
                *dump_buf << "]" << std::endl;
            }
            *dump_buf << "  Binded CDEV Info:" << std::endl;
            for (const auto &binded_cdev_info_pair :
                 name_info_pair.second.throttling_info->binded_cdev_info_map) {
                *dump_buf << "   Cooling device name: " << binded_cdev_info_pair.first << std::endl;
                if (thermal_throttling_status_map.at(name_info_pair.first)
                            .pid_power_budget_map.size()) {
                    *dump_buf << "    WeightForPID: [";
                    for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                        *dump_buf << binded_cdev_info_pair.second.cdev_weight_for_pid[i] << " ";
                    }
                    *dump_buf << "]" << std::endl;
                }
                *dump_buf << "    Ceiling: [";
                for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                    *dump_buf << binded_cdev_info_pair.second.cdev_ceiling[i] << " ";
                }
                *dump_buf << "]" << std::endl;
                *dump_buf << "    Hard limit: [";
                for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                    *dump_buf << binded_cdev_info_pair.second.limit_info[i] << " ";
                }
                *dump_buf << "]" << std::endl;

                if (!binded_cdev_info_pair.second.power_rail.empty()) {
                    *dump_buf << "    Binded power rail: "
                              << binded_cdev_info_pair.second.power_rail << std::endl;
                    *dump_buf << "    Power threshold: [";
                    for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                        *dump_buf << binded_cdev_info_pair.second.power_thresholds[i] << " ";
                    }
                    *dump_buf << "]" << std::endl;
                    *dump_buf << "    Floor with PowerLink: [";
                    for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                        *dump_buf << binded_cdev_info_pair.second.cdev_floor_with_power_link[i]
                                  << " ";
                    }
                    *dump_buf << "]" << std::endl;
                    *dump_buf << "    Release logic: ";
                    switch (binded_cdev_info_pair.second.release_logic) {
                        case ReleaseLogic::INCREASE:
                            *dump_buf << "INCREASE";
                            break;
                        case ReleaseLogic::DECREASE:
                            *dump_buf << "DECREASE";
                            break;
                        case ReleaseLogic::STEPWISE:
                            *dump_buf << "STEPWISE";
                            break;
                        case ReleaseLogic::RELEASE_TO_FLOOR:
                            *dump_buf << "RELEASE_TO_FLOOR";
                            break;
                        default:
                            *dump_buf << "NONE";
                            break;
                    }
                    *dump_buf << std::endl;
                    *dump_buf << "    high_power_check: " << std::boolalpha
                              << binded_cdev_info_pair.second.high_power_check << std::endl;
                    *dump_buf << "    throttling_with_power_link: " << std::boolalpha
                              << binded_cdev_info_pair.second.throttling_with_power_link
                              << std::endl;
                }
            }
        }
    }
}

void Thermal::dumpThrottlingRequestStatus(std::ostringstream *dump_buf) {
    const auto &thermal_throttling_status_map = thermal_helper_.GetThermalThrottlingStatusMap();
    if (!thermal_throttling_status_map.size()) {
        return;
    }
    *dump_buf << "getThrottlingRequestStatus:" << std::endl;
    for (const auto &thermal_throttling_status_pair : thermal_throttling_status_map) {
        *dump_buf << " Name: " << thermal_throttling_status_pair.first << std::endl;
        if (thermal_throttling_status_pair.second.pid_power_budget_map.size()) {
            *dump_buf << "  power budget request state" << std::endl;
            for (const auto &request_pair :
                 thermal_throttling_status_pair.second.pid_power_budget_map) {
                *dump_buf << "   " << request_pair.first << ": " << request_pair.second
                          << std::endl;
            }
        }
        if (thermal_throttling_status_pair.second.pid_cdev_request_map.size()) {
            *dump_buf << "  pid cdev request state" << std::endl;
            for (const auto &request_pair :
                 thermal_throttling_status_pair.second.pid_cdev_request_map) {
                *dump_buf << "   " << request_pair.first << ": " << request_pair.second
                          << std::endl;
            }
        }
        if (thermal_throttling_status_pair.second.hardlimit_cdev_request_map.size()) {
            *dump_buf << "  hard limit cdev request state" << std::endl;
            for (const auto &request_pair :
                 thermal_throttling_status_pair.second.hardlimit_cdev_request_map) {
                *dump_buf << "   " << request_pair.first << ": " << request_pair.second
                          << std::endl;
            }
        }
        if (thermal_throttling_status_pair.second.throttling_release_map.size()) {
            *dump_buf << "  cdev release state" << std::endl;
            for (const auto &request_pair :
                 thermal_throttling_status_pair.second.throttling_release_map) {
                *dump_buf << "   " << request_pair.first << ": " << request_pair.second
                          << std::endl;
            }
        }
        if (thermal_throttling_status_pair.second.cdev_status_map.size()) {
            *dump_buf << "  cdev request state" << std::endl;
            for (const auto &request_pair : thermal_throttling_status_pair.second.cdev_status_map) {
                *dump_buf << "   " << request_pair.first << ": " << request_pair.second
                          << std::endl;
            }
        }
    }
}

void Thermal::dumpPowerRailInfo(std::ostringstream *dump_buf) {
    const auto &power_rail_info_map = thermal_helper_.GetPowerRailInfoMap();
    const auto &power_status_map = thermal_helper_.GetPowerStatusMap();

    *dump_buf << "getPowerRailInfo:" << std::endl;
    for (const auto &power_rail_pair : power_rail_info_map) {
        *dump_buf << " Power Rail: " << power_rail_pair.first << std::endl;
        *dump_buf << "  Power Sample Count: " << power_rail_pair.second.power_sample_count
                  << std::endl;
        *dump_buf << "  Power Sample Delay: " << power_rail_pair.second.power_sample_delay.count()
                  << std::endl;
        if (power_status_map.count(power_rail_pair.first)) {
            auto power_history = power_status_map.at(power_rail_pair.first).power_history;
            *dump_buf << "  Last Updated AVG Power: "
                      << power_status_map.at(power_rail_pair.first).last_updated_avg_power << " mW"
                      << std::endl;
            if (power_rail_pair.second.virtual_power_rail_info != nullptr) {
                *dump_buf << "  Formula=";
                switch (power_rail_pair.second.virtual_power_rail_info->formula) {
                    case FormulaOption::COUNT_THRESHOLD:
                        *dump_buf << "COUNT_THRESHOLD";
                        break;
                    case FormulaOption::WEIGHTED_AVG:
                        *dump_buf << "WEIGHTED_AVG";
                        break;
                    case FormulaOption::MAXIMUM:
                        *dump_buf << "MAXIMUM";
                        break;
                    case FormulaOption::MINIMUM:
                        *dump_buf << "MINIMUM";
                        break;
                    default:
                        *dump_buf << "NONE";
                        break;
                }
                *dump_buf << std::endl;
            }
            for (size_t i = 0; i < power_history.size(); ++i) {
                if (power_rail_pair.second.virtual_power_rail_info != nullptr) {
                    *dump_buf
                            << "  Linked power rail "
                            << power_rail_pair.second.virtual_power_rail_info->linked_power_rails[i]
                            << std::endl;
                    *dump_buf << "   Coefficient="
                              << power_rail_pair.second.virtual_power_rail_info->coefficients[i]
                              << std::endl;
                    *dump_buf << "   Power Samples: ";
                } else {
                    *dump_buf << "  Power Samples: ";
                }
                while (power_history[i].size() > 0) {
                    const auto power_sample = power_history[i].front();
                    power_history[i].pop();
                    *dump_buf << "(T=" << power_sample.duration
                              << ", uWs=" << power_sample.energy_counter << ") ";
                }
                *dump_buf << std::endl;
            }
        }
    }
}

Return<void> Thermal::debug(const hidl_handle &handle, const hidl_vec<hidl_string> &) {
    if (handle != nullptr && handle->numFds >= 1) {
        int fd = handle->data[0];
        std::ostringstream dump_buf;

        if (!thermal_helper_.isInitializedOk()) {
            dump_buf << "ThermalHAL not initialized properly." << std::endl;
        } else {
            {
                hidl_vec<CpuUsage> cpu_usages;
                dump_buf << "getCpuUsages:" << std::endl;
                if (!thermal_helper_.fillCpuUsages(&cpu_usages)) {
                    dump_buf << " Failed to get CPU usages." << std::endl;
                }

                for (const auto &usage : cpu_usages) {
                    dump_buf << " Name: " << usage.name << " Active: " << usage.active
                             << " Total: " << usage.total << " IsOnline: " << usage.isOnline
                             << std::endl;
                }
            }
            {
                dump_buf << "getCachedTemperatures:" << std::endl;
                boot_clock::time_point now = boot_clock::now();
                const auto &sensor_status_map = thermal_helper_.GetSensorStatusMap();
                for (const auto &sensor_status_pair : sensor_status_map) {
                    if ((sensor_status_pair.second.thermal_cached.timestamp) ==
                        boot_clock::time_point::min()) {
                        continue;
                    }
                    dump_buf << " Name: " << sensor_status_pair.first
                             << " CachedValue: " << sensor_status_pair.second.thermal_cached.temp
                             << " TimeToCache: "
                             << std::chrono::duration_cast<std::chrono::milliseconds>(
                                        now - sensor_status_pair.second.thermal_cached.timestamp)
                                        .count()
                             << "ms" << std::endl;
                }
            }
            {
                const auto &map = thermal_helper_.GetSensorInfoMap();
                dump_buf << "getTemperatures:" << std::endl;
                Temperature_1_0 temp_1_0;
                for (const auto &name_info_pair : map) {
                    thermal_helper_.readTemperature(name_info_pair.first, &temp_1_0);
                    dump_buf << " Type: "
                             << android::hardware::thermal::V1_0::toString(temp_1_0.type)
                             << " Name: " << name_info_pair.first
                             << " CurrentValue: " << temp_1_0.currentValue
                             << " ThrottlingThreshold: " << temp_1_0.throttlingThreshold
                             << " ShutdownThreshold: " << temp_1_0.shutdownThreshold
                             << " VrThrottlingThreshold: " << temp_1_0.vrThrottlingThreshold
                             << std::endl;
                }
                dump_buf << "getCurrentTemperatures:" << std::endl;
                Temperature_2_0 temp_2_0;
                for (const auto &name_info_pair : map) {
                    thermal_helper_.readTemperature(name_info_pair.first, &temp_2_0, nullptr, true);
                    dump_buf << " Type: "
                             << android::hardware::thermal::V2_0::toString(temp_2_0.type)
                             << " Name: " << name_info_pair.first
                             << " CurrentValue: " << temp_2_0.value << " ThrottlingStatus: "
                             << android::hardware::thermal::V2_0::toString(
                                        temp_2_0.throttlingStatus)
                             << std::endl;
                }
                dump_buf << "getTemperatureThresholds:" << std::endl;
                for (const auto &name_info_pair : map) {
                    if (!name_info_pair.second.is_watch) {
                        continue;
                    }
                    dump_buf << " Type: "
                             << android::hardware::thermal::V2_0::toString(
                                        name_info_pair.second.type)
                             << " Name: " << name_info_pair.first;
                    dump_buf << " hotThrottlingThreshold: [";
                    for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                        dump_buf << name_info_pair.second.hot_thresholds[i] << " ";
                    }
                    dump_buf << "] coldThrottlingThreshold: [";
                    for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                        dump_buf << name_info_pair.second.cold_thresholds[i] << " ";
                    }
                    dump_buf << "] vrThrottlingThreshold: " << name_info_pair.second.vr_threshold;
                    dump_buf << std::endl;
                }
                dump_buf << "getHysteresis:" << std::endl;
                for (const auto &name_info_pair : map) {
                    if (!name_info_pair.second.is_watch) {
                        continue;
                    }
                    dump_buf << " Name: " << name_info_pair.first;
                    dump_buf << " hotHysteresis: [";
                    for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                        dump_buf << name_info_pair.second.hot_hysteresis[i] << " ";
                    }
                    dump_buf << "] coldHysteresis: [";
                    for (size_t i = 0; i < kThrottlingSeverityCount; ++i) {
                        dump_buf << name_info_pair.second.cold_hysteresis[i] << " ";
                    }
                    dump_buf << "]" << std::endl;
                }
            }
            {
                dump_buf << "getCurrentCoolingDevices:" << std::endl;
                hidl_vec<CoolingDevice_2_0> cooling_devices;
                if (!thermal_helper_.fillCurrentCoolingDevices(false, CoolingType::CPU,
                                                               &cooling_devices)) {
                    dump_buf << " Failed to getCurrentCoolingDevices." << std::endl;
                }

                for (const auto &c : cooling_devices) {
                    dump_buf << " Type: " << android::hardware::thermal::V2_0::toString(c.type)
                             << " Name: " << c.name << " CurrentValue: " << c.value << std::endl;
                }
            }
            {
                dump_buf << "getCallbacks:" << std::endl;
                dump_buf << " Total: " << callbacks_.size() << std::endl;
                for (const auto &c : callbacks_) {
                    dump_buf << " IsFilter: " << c.is_filter_type
                             << " Type: " << android::hardware::thermal::V2_0::toString(c.type)
                             << std::endl;
                }
            }
            {
                dump_buf << "sendCallback:" << std::endl;
                dump_buf << "  Enabled List: ";
                const auto &map = thermal_helper_.GetSensorInfoMap();
                for (const auto &name_info_pair : map) {
                    if (name_info_pair.second.send_cb) {
                        dump_buf << name_info_pair.first << " ";
                    }
                }
                dump_buf << std::endl;
            }
            {
                dump_buf << "sendPowerHint:" << std::endl;
                dump_buf << "  Enabled List: ";
                const auto &map = thermal_helper_.GetSensorInfoMap();
                for (const auto &name_info_pair : map) {
                    if (name_info_pair.second.send_powerhint) {
                        dump_buf << name_info_pair.first << " ";
                    }
                }
                dump_buf << std::endl;
            }
            dumpVirtualSensorInfo(&dump_buf);
            dumpThrottlingInfo(&dump_buf);
            dumpThrottlingRequestStatus(&dump_buf);
            dumpPowerRailInfo(&dump_buf);
            {
                dump_buf << "getAIDLPowerHalInfo:" << std::endl;
                dump_buf << " Exist: " << std::boolalpha << thermal_helper_.isAidlPowerHalExist()
                         << std::endl;
                dump_buf << " Connected: " << std::boolalpha
                         << thermal_helper_.isPowerHalConnected() << std::endl;
                dump_buf << " Ext connected: " << std::boolalpha
                         << thermal_helper_.isPowerHalExtConnected() << std::endl;
            }
        }
        std::string buf = dump_buf.str();
        if (!android::base::WriteStringToFd(buf, fd)) {
            PLOG(ERROR) << "Failed to dump state to fd";
        }
        fsync(fd);
    }
    return Void();
}

}  // namespace implementation
}  // namespace V2_0
}  // namespace thermal
}  // namespace hardware
}  // namespace android

/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <android/hardware/thermal/2.0/IThermal.h>

#include <queue>
#include <shared_mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>

#include "power_files.h"
#include "thermal_info.h"

namespace android {
namespace hardware {
namespace thermal {
namespace V2_0 {
namespace implementation {

using ::android::hardware::hidl_vec;
using ::android::hardware::thermal::V2_0::IThermal;
using Temperature_2_0 = ::android::hardware::thermal::V2_0::Temperature;
using ::android::hardware::thermal::V2_0::TemperatureThreshold;
using ::android::hardware::thermal::V2_0::ThrottlingSeverity;

struct ThermalThrottlingStatus {
    std::unordered_map<std::string, int> pid_power_budget_map;
    std::unordered_map<std::string, int> pid_cdev_request_map;
    std::unordered_map<std::string, int> hardlimit_cdev_request_map;
    std::unordered_map<std::string, int> throttling_release_map;
    std::unordered_map<std::string, int> cdev_status_map;
    float err_integral;
    float prev_err;
};

// Return the target state of PID algorithm
size_t getTargetStateOfPID(const SensorInfo &sensor_info, const ThrottlingSeverity curr_severity);

// A helper class for conducting thermal throttling
class ThermalThrottling {
  public:
    ThermalThrottling() = default;
    ~ThermalThrottling() = default;
    // Disallow copy and assign.
    ThermalThrottling(const ThermalThrottling &) = delete;
    void operator=(const ThermalThrottling &) = delete;

    // Clear throttling data
    void clearThrottlingData(std::string_view sensor_name, const SensorInfo &sensor_info);
    // Register map for throttling algo
    bool registerThermalThrottling(
            std::string_view sensor_name, const std::shared_ptr<ThrottlingInfo> &throttling_info,
            const std::unordered_map<std::string, CdevInfo> &cooling_device_info_map);
    // Register map for throttling release algo
    bool registerThrottlingReleaseToWatch(std::string_view sensor_name, std::string_view cdev_name,
                                          const BindedCdevInfo &binded_cdev_info);
    // Get throttling status map
    const std::unordered_map<std::string, ThermalThrottlingStatus> &GetThermalThrottlingStatusMap()
            const {
        std::shared_lock<std::shared_mutex> _lock(thermal_throttling_status_map_mutex_);
        return thermal_throttling_status_map_;
    }
    // update thermal throttling request for the specific sensor
    void thermalThrottlingUpdate(
            const Temperature_2_0 &temp, const SensorInfo &sensor_info,
            const ThrottlingSeverity curr_severity, const std::chrono::milliseconds time_elapsed_ms,
            const std::unordered_map<std::string, PowerStatus> &power_status_map,
            const std::unordered_map<std::string, CdevInfo> &cooling_device_info_map);

    // Compute the throttling target from all the sensors' request
    void computeCoolingDevicesRequest(std::string_view sensor_name, const SensorInfo &sensor_info,
                                      const ThrottlingSeverity curr_severity,
                                      std::vector<std::string> *cooling_devices_to_update);

  private:
    // PID algo - get the total power budget
    float updatePowerBudget(const Temperature_2_0 &temp, const SensorInfo &sensor_info,
                            std::chrono::milliseconds time_elapsed_ms,
                            ThrottlingSeverity curr_severity);
    // PID algo - map the target throttling state according to the power budget
    bool updateCdevRequestByPower(
            const Temperature_2_0 &temp, const SensorInfo &sensor_info,
            const ThrottlingSeverity curr_severity, const std::chrono::milliseconds time_elapsed_ms,
            const std::unordered_map<std::string, CdevInfo> &cooling_device_info_map);
    // Hard limit algo - assign the throttling state according to the severity
    void updateCdevRequestBySeverity(std::string_view sensor_name, const SensorInfo &sensor_info,
                                     ThrottlingSeverity curr_severity);
    // Throttling release algo according to predefined power threshold
    bool throttlingReleaseUpdate(
            std::string_view sensor_name,
            const std::unordered_map<std::string, CdevInfo> &cooling_device_info_map,
            const std::unordered_map<std::string, PowerStatus> &power_status_map,
            const ThrottlingSeverity severity, const SensorInfo &sensor_info);

    mutable std::shared_mutex thermal_throttling_status_map_mutex_;
    // Thermal throttling status from each sensor
    std::unordered_map<std::string, ThermalThrottlingStatus> thermal_throttling_status_map_;
    std::vector<std::string> cooling_devices_to_update;
};

}  // namespace implementation
}  // namespace V2_0
}  // namespace thermal
}  // namespace hardware
}  // namespace android

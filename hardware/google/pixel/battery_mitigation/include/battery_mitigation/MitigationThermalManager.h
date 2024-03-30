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

#ifndef MITIGATION_THERMAL_MANAGER_H_
#define MITIGATION_THERMAL_MANAGER_H_

#include <android-base/chrono_utils.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <android/hardware/thermal/2.0/IThermal.h>
#include <android/hardware/thermal/2.0/IThermalChangedCallback.h>
#include <android/hardware/thermal/2.0/types.h>
#include <unistd.h>
#include <utils/Mutex.h>

#include <fstream>
#include <iostream>
#include <regex>

#include "MitigationConfig.h"

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using android::hardware::google::pixel::MitigationConfig;
using android::hardware::hidl_death_recipient;
using android::hardware::hidl_string;
using android::hardware::Return;
using android::hardware::thermal::V2_0::IThermal;
using android::hardware::thermal::V2_0::IThermalChangedCallback;
using android::hardware::thermal::V2_0::Temperature;
using android::hardware::thermal::V2_0::TemperatureType;
using android::hardware::thermal::V2_0::ThrottlingSeverity;
using android::hardware::Void;

class MitigationThermalManager {
  public:
    static MitigationThermalManager &getInstance();

    // delete copy and move constructors and assign operators
    MitigationThermalManager(MitigationThermalManager const &) = delete;
    MitigationThermalManager(MitigationThermalManager &&) = delete;
    MitigationThermalManager &operator=(MitigationThermalManager const &) = delete;
    MitigationThermalManager &operator=(MitigationThermalManager &&) = delete;

  private:
    // ThermalCallback implements the HIDL thermal changed callback
    // interface, IThermalChangedCallback.
    void thermalCb(const Temperature &temperature);
    android::base::boot_clock::time_point lastCapturedTime;

    class ThermalCallback : public IThermalChangedCallback {
      public:
        ThermalCallback(std::function<void(const Temperature &)> notify_function)
            : notifyFunction(notify_function) {}

        // Callback function. thermal service will call this.
        Return<void> notifyThrottling(const Temperature &temperature) override {
            if ((temperature.type == TemperatureType::BCL_VOLTAGE) ||
                (temperature.type == TemperatureType::BCL_CURRENT)) {
                notifyFunction(temperature);
            }
            return ::android::hardware::Void();
        }

      private:
        std::function<void(const Temperature &)> notifyFunction;
    };

    class ThermalDeathRecipient : virtual public hidl_death_recipient {
      public:
        void serviceDied(uint64_t /*cookie*/,
                         const android::wp<::android::hidl::base::V1_0::IBase> & /*who*/) override {
            MitigationThermalManager::getInstance().connectThermalHal();
        };
    };

  public:
    MitigationThermalManager();
    ~MitigationThermalManager();
    bool connectThermalHal();
    bool isMitigationTemperature(const Temperature &temperature);
    void registerCallback();
    void remove();
    void updateConfig(const struct MitigationConfig::Config &cfg);


  private:
    std::mutex mutex_;
    // Thermal hal interface.
    android::sp<IThermal> thermal;
    // Thermal hal callback object.
    android::sp<ThermalCallback> callback;
    // Receiver when thermal hal restart.
    android::sp<ThermalDeathRecipient> deathRecipient;
    std::vector<std::string> kSystemPath;
    std::vector<std::string> kFilteredZones;
    std::vector<std::string> kSystemName;
    std::string kLogFilePath;
    std::string kTimestampFormat;
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
#endif

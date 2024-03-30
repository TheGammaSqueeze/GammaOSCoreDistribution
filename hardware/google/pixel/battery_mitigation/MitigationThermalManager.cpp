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
#define LOG_TAG "mitigation-logger"

#include <android-base/chrono_utils.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <battery_mitigation/MitigationThermalManager.h>
#include <errno.h>
#include <sys/time.h>

#include <chrono>
#include <cmath>
#include <ctime>
#include <iomanip>
#include <iostream>
#include <string>

#define NUM_OF_SAMPLES     20
#define CAPTURE_INTERVAL_S 2     /* 2 seconds between new capture */

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using android::hardware::thermal::V1_0::ThermalStatus;
using android::hardware::thermal::V1_0::ThermalStatusCode;

MitigationThermalManager &MitigationThermalManager::getInstance() {
    static MitigationThermalManager mitigationThermalManager;
    return mitigationThermalManager;
}

void MitigationThermalManager::remove() {
    if (!thermal) {
        return;
    }
    if (callback) {
        ThermalStatus returnStatus;
        auto ret = thermal->unregisterThermalChangedCallback(
                callback, [&returnStatus](ThermalStatus status) { returnStatus = status; });
        if (!ret.isOk() || returnStatus.code != ThermalStatusCode::SUCCESS) {
            LOG(ERROR) << "Failed to release thermal callback!";
        }
    }
    if (deathRecipient) {
        auto ret = thermal->unlinkToDeath(deathRecipient);
        if (!ret.isOk()) {
            LOG(ERROR) << "Failed to release thermal death notification!";
        }
    }
}

MitigationThermalManager::MitigationThermalManager() {
    if (!connectThermalHal()) {
        remove();
    }
}

MitigationThermalManager::~MitigationThermalManager() {
    remove();
}

void MitigationThermalManager::updateConfig(const struct MitigationConfig::Config &cfg) {
    kLogFilePath = std::string(cfg.LogFilePath);
    kSystemPath = cfg.SystemPath;
    kSystemName = cfg.SystemName;
    kFilteredZones = cfg.FilteredZones;
    kTimestampFormat = cfg.TimestampFormat;
}

bool MitigationThermalManager::connectThermalHal() {
    thermal = IThermal::getService();
    if (thermal) {
        lastCapturedTime = ::android::base::boot_clock::now();
        registerCallback();
        return true;
    } else {
        LOG(ERROR) << "Cannot get IThermal service!";
    }
    return false;
}

bool MitigationThermalManager::isMitigationTemperature(const Temperature &temperature) {
    if (std::find(kFilteredZones.begin(), kFilteredZones.end(), temperature.name) !=
            kFilteredZones.end()) {
        return true;
    }
    return false;
}

void MitigationThermalManager::thermalCb(const Temperature &temperature) {
    if ((temperature.throttlingStatus == ThrottlingSeverity::NONE) ||
          (!isMitigationTemperature(temperature))) {
        return;
    }
    auto currentTime = ::android::base::boot_clock::now();
    auto delta =
            std::chrono::duration_cast<std::chrono::seconds>(currentTime - lastCapturedTime);
    if (delta.count() < CAPTURE_INTERVAL_S) {
        /* Do not log if delta is within 2 seconds */
        return;
    }
    int flag = O_WRONLY | O_CREAT | O_NOFOLLOW | O_CLOEXEC | O_APPEND | O_TRUNC;
    android::base::unique_fd fd(TEMP_FAILURE_RETRY(open(kLogFilePath.c_str(), flag, 0644)));
    lastCapturedTime = currentTime;
    std::stringstream oss;
    oss << temperature.name << " triggered at " << temperature.value << std::endl << std::flush;
    android::base::WriteStringToFd(oss.str(), fd);
    fsync(fd);

    for (int i = 0; i < NUM_OF_SAMPLES; i++) {
        auto now = std::chrono::system_clock::now();
        auto time_sec = std::chrono::system_clock::to_time_t(now);
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()) -
                std::chrono::duration_cast<std::chrono::seconds>(now.time_since_epoch());
        struct tm now_tm;
        localtime_r(&time_sec, &now_tm);
        oss << std::put_time(&now_tm, kTimestampFormat.c_str()) << "." << std::setw(3)
            << std::setfill('0') << ms.count() << std::endl
            << std::flush;
        android::base::WriteStringToFd(oss.str(), fd);
        fsync(fd);
        oss.str("");
        /* log System info */
        for (int j = 0; j < kSystemName.size(); j++) {
            std::string value;
            bool result = android::base::ReadFileToString(kSystemPath[j], &value);
            if (!result) {
                LOG(ERROR) << "Could not read: " << kSystemName[j];
            }
            android::base::WriteStringToFd(kSystemName[j] + ":" + value, fd);
        }
    }
    fsync(fd);
}

void MitigationThermalManager::registerCallback() {
    if (!thermal) {
        LOG(ERROR) << "Cannot register thermal callback!";
        return;
    }
    ThermalStatus returnStatus;
    // Create thermal death recipient object.
    if (deathRecipient == nullptr) {
        deathRecipient = new MitigationThermalManager::ThermalDeathRecipient();
    }
    // Create thermal callback recipient object.
    if (callback == nullptr) {
        callback = new MitigationThermalManager::ThermalCallback(
                [this](const Temperature &temperature) {
                    std::lock_guard api_lock(mutex_);
                    thermalCb(temperature);
                });
    }
    // Register thermal callback SKIN to thermal hal to cover all.  Cannot register twice.
    auto ret_callback = thermal->registerThermalChangedCallback(
            callback, false, TemperatureType::SKIN,
            [&returnStatus](ThermalStatus status) { return returnStatus = status; });
    if (!ret_callback.isOk() || returnStatus.code != ThermalStatusCode::SUCCESS) {
        LOG(ERROR) << "Failed to register thermal callback!";
    }
    // Register thermal death notification to thermal hal.
    auto retLink = thermal->linkToDeath(deathRecipient, 0);
    if (!retLink.isOk()) {
        LOG(ERROR) << "Failed to register thermal death notification!";
    }
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

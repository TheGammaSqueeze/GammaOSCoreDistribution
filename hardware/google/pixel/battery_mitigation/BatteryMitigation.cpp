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

#include <battery_mitigation/BatteryMitigation.h>

#include <sstream>

#define MAX_BROWNOUT_DATA_AGE_SECONDS 300

namespace android {
namespace hardware {
namespace google {
namespace pixel {

BatteryMitigation::BatteryMitigation(const struct MitigationConfig::Config &cfg) {
        mThermalMgr = &MitigationThermalManager::getInstance();
        mThermalMgr->updateConfig(cfg);
}

bool BatteryMitigation::isMitigationLogTimeValid(std::chrono::system_clock::time_point startTime,
                                                 const char *const logFilePath,
                                                 const char *const timestampFormat,
                                                 const std::regex pattern) {
    std::string logFile;
    if (!android::base::ReadFileToString(logFilePath, &logFile)) {
        return false;
    }
    std::istringstream content(logFile);
    std::string line;
    int counter = 0;
    std::smatch pattern_match;
    while (std::getline(content, line)) {
        if (std::regex_match(line, pattern_match, pattern)) {
            std::tm triggeredTimestamp = {};
            std::istringstream ss(pattern_match.str());
            ss >> std::get_time(&triggeredTimestamp, timestampFormat);
            auto logFileTime = std::chrono::system_clock::from_time_t(mktime(&triggeredTimestamp));
            auto delta = std::chrono::duration_cast<std::chrono::seconds>(startTime - logFileTime);
            if ((delta.count() < MAX_BROWNOUT_DATA_AGE_SECONDS) && (delta.count() > 0)) {
                return true;
            }
        }
        counter += 1;
        if (counter > 5) {
            break;
        }
    }
    return false;
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

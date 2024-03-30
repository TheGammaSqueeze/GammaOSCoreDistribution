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

#ifndef HARDWARE_GOOGLE_PIXEL_BATTERY_MITIGATION_CONFIG_H
#define HARDWARE_GOOGLE_PIXEL_BATTERY_MITIGATION_CONFIG_H

namespace android {
namespace hardware {
namespace google {
namespace pixel {

class MitigationConfig {
  public:
    struct Config {
        const std::vector<std::string> SystemPath;
        const std::vector<std::string> FilteredZones;
        const std::vector<std::string> SystemName;
        const char *const LogFilePath;
        const char *const TimestampFormat;
    };

    MitigationConfig(const struct Config &cfg);

  private:
    const std::vector<std::string> kSystemPath;
    const std::vector<std::string> kFilteredZones;
    const std::vector<std::string> kSystemName;
    const char *const kLogFilePath;
    const char *const kTimestampFormat;
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_BATTERY_MITIGATION_CONFIG_H

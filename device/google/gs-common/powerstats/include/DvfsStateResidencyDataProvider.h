/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <PowerStatsAidl.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {

class DvfsStateResidencyDataProvider : public PowerStats::IStateResidencyDataProvider {
  public:
    class Config {
      public:
        // Power entity name to parse.
        std::string powerEntityName;

        // List of state pairs (name to display, name to parse).
        std::vector<std::pair<std::string, std::string>> states;
    };
    /*
     * path - path to dvfs sysfs node.
     * clockRate - clock rate in KHz.
     */
    DvfsStateResidencyDataProvider(std::string path, uint64_t clockRate, std::vector<Config> cfgs);
    ~DvfsStateResidencyDataProvider() = default;

    /*
     * See IStateResidencyDataProvider::getStateResidencies
     */
    bool getStateResidencies(
        std::unordered_map<std::string, std::vector<StateResidency>> *residencies) override;

    /*
     * See IStateResidencyDataProvider::getInfo
     */
    std::unordered_map<std::string, std::vector<State>> getInfo() override;

  private:
    int32_t matchEntity(char const *line);
    int32_t matchState(char const *line, const Config& powerEntity);
    bool parseState(char const *line, uint64_t *duration, uint64_t *count);

    const std::string mPath;
    const uint64_t mClockRate;
    std::vector<Config> mPowerEntities;
};

}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl

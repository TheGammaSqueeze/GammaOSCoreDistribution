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

#include <dataproviders/GenericStateResidencyDataProvider.h>
#include <PowerStatsAidl.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {

class AocStateResidencyDataProvider : public PowerStats::IStateResidencyDataProvider {
  public:
    AocStateResidencyDataProvider(std::vector<std::pair<std::string, std::string>> ids,
                                  std::vector<std::pair<std::string, std::string>> states,
                                  const uint64_t aocClock);
    ~AocStateResidencyDataProvider() = default;
    bool getStateResidencies(
        std::unordered_map<std::string, std::vector<StateResidency>> *residencies) override;
    std::unordered_map<std::string, std::vector<State>> getInfo() override;

  protected:
    std::unordered_map<std::string /* entity name */,
        std::vector<std::unique_ptr<GenericStateResidencyDataProvider>> /* providers */> mProviders;
};

}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl

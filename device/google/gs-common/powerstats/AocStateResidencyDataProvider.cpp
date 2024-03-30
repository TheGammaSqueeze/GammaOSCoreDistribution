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

#include "AocStateResidencyDataProvider.h"

#include <android-base/logging.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {

AocStateResidencyDataProvider::AocStateResidencyDataProvider(
        std::vector<std::pair<std::string, std::string>> ids,
        std::vector<std::pair<std::string, std::string>> states,
        const uint64_t aocClock) {
    // AoC stats are reported in ticks.
    static const uint64_t AOC_CLK = aocClock;
    std::function<uint64_t(uint64_t)> aocTickToMs = [](uint64_t a) { return a / AOC_CLK; };
    GenericStateResidencyDataProvider::StateResidencyConfig config = {
            .entryCountSupported = true,
            .entryCountPrefix = "Counter:",
            .totalTimeSupported = true,
            .totalTimePrefix = "Cumulative time:",
            .totalTimeTransform = aocTickToMs,
            .lastEntrySupported = true,
            .lastEntryPrefix = "Time last entered:",
            .lastEntryTransform = aocTickToMs,
    };
    for (const auto &id : ids) {
        for (const auto &state : states) {
            std::vector<std::pair<std::string, std::string>> aocStateHeaders = {
                std::make_pair(state.first, ""),
            };
            std::vector<GenericStateResidencyDataProvider::PowerEntityConfig> cfgs;
            cfgs.emplace_back(generateGenericStateResidencyConfigs(config, aocStateHeaders),
                    id.first, "");
            std::unique_ptr<GenericStateResidencyDataProvider> sdp(
                    new GenericStateResidencyDataProvider(id.second + state.second, cfgs));
            mProviders[id.first].push_back(std::move(sdp));
        }
    }
}

bool AocStateResidencyDataProvider::getStateResidencies(
        std::unordered_map<std::string, std::vector<StateResidency>> *residencies) {
    // States from the same power entity are merged.
    bool ret = true;
    for (const auto &providerList : mProviders) {
        int32_t stateId = 0;
        std::string curEntity = providerList.first;
        std::vector<StateResidency> stateResidencies;

        // Iterate over each provider in the providerList, appending each of the states
        for (const auto &provider : providerList.second) {
            std::unordered_map<std::string, std::vector<StateResidency>> residency;
            ret &= provider->getStateResidencies(&residency);

            // Each provider should only return data for curEntity but checking anyway
            if (residency.find(curEntity) != residency.end()) {
                for (auto &r : residency.at(curEntity)) {
                    /*
                     * Modifying stateId here because we are stitching together infos from
                     * multiple GenericStateResidencyDataProviders. stateId must be modified
                     * to maintain uniqueness for a given entity
                     */
                    r.id = stateId++;
                    stateResidencies.push_back(r);
                }
            }
        }

        residencies->emplace(curEntity, stateResidencies);
    }
    return ret;
}

std::unordered_map<std::string, std::vector<State>> AocStateResidencyDataProvider::getInfo() {
    // States from the same power entity are merged
    std::unordered_map<std::string, std::vector<State>> infos;
    for (const auto &providerList : mProviders) {
        int32_t stateId = 0;
        std::string curEntity = providerList.first;
        std::vector<State> stateInfos;

        // Iterate over each provider in the providerList, appending each of the states
        for (const auto &provider : providerList.second) {
            std::unordered_map<std::string, std::vector<State>> info = provider->getInfo();

            // Each provider should only return data for curEntity but checking anyway
            if (info.find(curEntity) != info.end()) {
                for (auto &i : info.at(curEntity)) {
                    /*
                     * Modifying stateId because we are stitching together infos from
                     * multiple GenericStateResidencyDataProviders. stateId must be modified
                     * to maintain uniqueness for a given entity
                     */
                    i.id = stateId++;
                    stateInfos.push_back(i);
                }
            }
        }

        infos.emplace(curEntity, stateInfos);
    }

    return infos;
}

}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl

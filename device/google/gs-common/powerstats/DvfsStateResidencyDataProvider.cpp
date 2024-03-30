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
#include "DvfsStateResidencyDataProvider.h"

#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>

#include <string>
#include <utility>

using android::base::ParseUint;
using android::base::Split;
using android::base::StartsWith;
using android::base::Trim;

static const std::string nameSuffix = "-DVFS";

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {

DvfsStateResidencyDataProvider::DvfsStateResidencyDataProvider(std::string path, uint64_t clockRate,
        std::vector<Config> cfgs)
    : mPath(std::move(path)), mClockRate(clockRate), mPowerEntities(std::move(cfgs)) {}

int32_t DvfsStateResidencyDataProvider::matchEntity(char const *line) {
    for (int32_t i = 0; i < mPowerEntities.size(); i++) {
        if (mPowerEntities[i].powerEntityName == Trim(std::string(line))) {
            return i;
        }
    }
    return -1;
}

int32_t DvfsStateResidencyDataProvider::matchState(char const *line, const Config& powerEntity) {
    for (int32_t i = 0; i < powerEntity.states.size(); i++) {
        if (StartsWith(Trim(std::string(line)), powerEntity.states[i].second)) {
            return i;
        }
    }
    return -1;
}

bool DvfsStateResidencyDataProvider::parseState(char const *line, uint64_t *duration,
        uint64_t *count) {
    std::vector<std::string> parts = Split(line, " ");
    if (parts.size() != 7) {
        return false;
    }
    if (!ParseUint(Trim(parts[3]), count)) {
        return false;
    }
    if (!ParseUint(Trim(parts[6]), duration)) {
        return false;
    }
    return true;
}

bool DvfsStateResidencyDataProvider::getStateResidencies(
        std::unordered_map<std::string, std::vector<StateResidency>> *residencies) {
    std::unique_ptr<FILE, decltype(&fclose)> fp(fopen(mPath.c_str(), "r"), fclose);
    if (!fp) {
        PLOG(ERROR) << __func__ << ":Failed to open file " << mPath;
        return false;
    }

    for (const Config &powerEntity : mPowerEntities) {
        std::vector<StateResidency> stateResidency(powerEntity.states.size());
        for (int32_t i = 0; i < stateResidency.size(); i++) {
            stateResidency[i].id = i;
        }
        residencies->emplace(powerEntity.powerEntityName + nameSuffix, stateResidency);
    }

    size_t len = 0;
    char *line = nullptr;

    int32_t temp, powerEntityIndex, stateId = -1;
    uint64_t duration, count;
    auto it = residencies->end();

    while (getline(&line, &len, fp.get()) != -1) {
        temp = matchEntity(line);
        // Assign new index only when a new valid entity is encountered.
        if (temp >= 0) {
            powerEntityIndex = temp;
            it = residencies->find(mPowerEntities[powerEntityIndex].powerEntityName + nameSuffix);
        }

        if (it != residencies->end()) {
            stateId = matchState(line, mPowerEntities[powerEntityIndex]);

            if (stateId >= 0) {
                if (parseState(line, &duration, &count)) {
                    it->second[stateId].totalTimeInStateMs =
                            duration / mClockRate;
                    it->second[stateId].totalStateEntryCount = count;
                } else {
                    LOG(ERROR) << "Failed to parse duration and count from [" << std::string(line)
                               << "]";
                    return false;
                }
            }
        }
    }

    free(line);

    return true;
}

std::unordered_map<std::string, std::vector<State>> DvfsStateResidencyDataProvider::getInfo() {
    std::unordered_map<std::string, std::vector<State>> info;
    for (auto const &entity : mPowerEntities) {
        std::vector<State> stateInfo(entity.states.size());
        int32_t stateId = 0;
        for (auto const &state : entity.states) {
            stateInfo[stateId] = State{
                .id = stateId,
                .name = state.first
            };
            stateId++;
        }
        info.emplace(entity.powerEntityName + nameSuffix, stateInfo);
    }
    return info;
}

}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl

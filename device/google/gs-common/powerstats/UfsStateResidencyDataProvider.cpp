/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include "UfsStateResidencyDataProvider.h"

#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>

#include <string>
#include <utility>

using android::base::ParseInt;
using android::base::Split;
using android::base::StartsWith;
using android::base::Trim;

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {

const int32_t HIBERNATE_STATE_ID = 0;
const std::string UFS_NAME = "UFS";

UfsStateResidencyDataProvider::UfsStateResidencyDataProvider(std::string prefix) : kPrefix(prefix) {}

bool UfsStateResidencyDataProvider::getStateResidencies(
        std::unordered_map<std::string, std::vector<StateResidency>> *residencies) {
    StateResidency residency;
    residency.id = HIBERNATE_STATE_ID;

        // The transform function converts microseconds to milliseconds.
    std::function<uint64_t(uint64_t)> usecToMs = [](uint64_t a) { return a / 1000; };

    residency.totalTimeInStateMs = usecToMs(readStat(kPrefix + "hibern8_total_us"));
    residency.totalStateEntryCount = readStat(kPrefix + "hibern8_exit_cnt");
    residency.lastEntryTimestampMs = usecToMs(readStat(kPrefix + "last_hibern8_enter_time"));

    residencies->emplace(UFS_NAME, std::vector<StateResidency>{residency});
    return true;
}

std::unordered_map<std::string, std::vector<State>> UfsStateResidencyDataProvider::getInfo() {
    return {{UFS_NAME, std::vector<State>{{HIBERNATE_STATE_ID, "HIBERN8"}} }};
}

int64_t UfsStateResidencyDataProvider::readStat(std::string path) {
    std::unique_ptr<FILE, decltype(&fclose)> fp(fopen(path.c_str(), "r"), fclose);
    if (!fp) {
        PLOG(ERROR) << __func__ << ":Failed to open file " << path
                    << " Error = " << strerror(errno);
        return 0;
    }
    const size_t size = 20;
    char buf[size];
    (void)fread(&buf, sizeof(char), size, fp.get());
    int64_t ret;
    if (!ParseInt(Trim(std::string(buf)), &ret)) {
        LOG(ERROR) << "Failed to parse int64 from [" << std::string(buf) << "]";
    }
    return ret;
}

}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl

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

#include <aidl/android/frameworks/stats/IStats.h>
#include <android-base/chrono_utils.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>

#include <string>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtomValue;

/**
 * A class to upload Pixel TempResidency Stats metrics
 */
class TempResidencyReporter {
  public:
    void logTempResidencyStats(const std::shared_ptr<IStats> &stats_client,
                               const char *const temperature_residency_path);

  private:
    std::map<std::string, std::vector<int64_t>> prev_stats;
    ::android::base::boot_clock::time_point prevTime =
            ::android::base::boot_clock::time_point::min();
    const int kMaxBucketLen = 20;
    const int kMaxResidencyDiffMs = 3000;
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

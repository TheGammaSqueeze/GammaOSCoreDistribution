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

#define LOG_TAG "pixelstats: TempResidencyStats"

#include <aidl/android/frameworks/stats/IStats.h>
#include <android-base/chrono_utils.h>
#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <pixelstats/TempResidencyReporter.h>
#include <utils/Log.h>

#include <cinttypes>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;
using android::base::ReadFileToString;
using android::hardware::google::pixel::PixelAtoms::ThermalDfsStats;

/**
 * Parse file_contents and read residency stats into cur_stats.
 */
bool parse_file_contents(std::string file_contents,
                         std::map<std::string, std::vector<int64_t>> *cur_stats) {
    const char *data = file_contents.c_str();
    int data_len = file_contents.length();
    char sensor_name[32];
    int offset = 0;
    int bytes_read;

    while (sscanf(data + offset, "THERMAL ZONE: %31s\n%n", sensor_name, &bytes_read) == 1) {
        int64_t temp_res_value;
        int num_stats_buckets;
        int index = 0;
        offset += bytes_read;
        if (offset >= data_len)
            return false;

        std::string sensor_name_str = sensor_name;

        if (!sscanf(data + offset, "NUM_TEMP_RESIDENCY_BUCKETS: %d\n%n", &num_stats_buckets,
                    &bytes_read))
            return false;
        offset += bytes_read;
        if (offset >= data_len)
            return false;
        while (index < num_stats_buckets) {
            if (sscanf(data + offset, "-inf - %*d ====> %ldms\n%n", &temp_res_value, &bytes_read) !=
                        1 &&
                sscanf(data + offset, "%*d - %*d ====> %ldms\n%n", &temp_res_value, &bytes_read) !=
                        1 &&
                sscanf(data + offset, "%*d - inf ====> %ldms\n\n%n", &temp_res_value,
                       &bytes_read) != 1)
                return false;

            (*cur_stats)[sensor_name_str].push_back(temp_res_value);
            index++;

            offset += bytes_read;
            if ((offset >= data_len) && (index < num_stats_buckets))
                return false;
        }
    }
    return true;
}

/**
 * Logs the Temperature residency stats for every thermal zone.
 */
void TempResidencyReporter::logTempResidencyStats(const std::shared_ptr<IStats> &stats_client,
                                                  const char *const temperature_residency_path) {
    if (!temperature_residency_path) {
        ALOGV("TempResidencyStatsPath path not specified");
        return;
    }
    std::string file_contents;
    if (!ReadFileToString(temperature_residency_path, &file_contents)) {
        ALOGE("Unable to read TempResidencyStatsPath %s - %s", temperature_residency_path,
              strerror(errno));
        return;
    }
    std::map<std::string, std::vector<int64_t>> cur_stats_map;
    if (!parse_file_contents(file_contents, &cur_stats_map)) {
        ALOGE("Fail to parse TempResidencyStatsPath");
        return;
    }
    if (!cur_stats_map.size())
        return;
    ::android::base::boot_clock::time_point curTime = ::android::base::boot_clock::now();
    int64_t since_last_update_ms =
            std::chrono::duration_cast<std::chrono::milliseconds>(curTime - prevTime).count();

    auto cur_stats_map_iterator = cur_stats_map.begin();
    VendorAtomValue tmp_atom_value;

    // Iterate through cur_stats_map by sensor_name
    while (cur_stats_map_iterator != cur_stats_map.end()) {
        std::vector<VendorAtomValue> values;
        std::string sensor_name_str = cur_stats_map_iterator->first;
        std::vector<int64_t> residency_stats = cur_stats_map_iterator->second;
        tmp_atom_value.set<VendorAtomValue::stringValue>(sensor_name_str);
        values.push_back(tmp_atom_value);
        tmp_atom_value.set<VendorAtomValue::longValue>(since_last_update_ms);
        values.push_back(tmp_atom_value);

        bool key_in_map = (prev_stats.find(sensor_name_str)) != prev_stats.end();
        bool stat_len_match = (residency_stats.size() == prev_stats[sensor_name_str].size());
        if (key_in_map && !stat_len_match)
            prev_stats[sensor_name_str].clear();

        int64_t sum_residency = 0;
        if (residency_stats.size() > kMaxBucketLen) {
            cur_stats_map_iterator++;
            continue;
        }
        // Iterate over every temperature residency buckets
        for (int index = 0; index < residency_stats.size(); index++) {
            //  Get diff if stats arr length match previous stats
            //  Otherwise use raw stats as temperature residency stats per day
            if (key_in_map && stat_len_match) {
                int64_t diff_residency =
                        residency_stats[index] - prev_stats[sensor_name_str][index];
                tmp_atom_value.set<VendorAtomValue::longValue>(diff_residency);
                sum_residency += diff_residency;
                prev_stats[sensor_name_str][index] = residency_stats[index];
            } else {
                tmp_atom_value.set<VendorAtomValue::longValue>(residency_stats[index]);
                sum_residency += residency_stats[index];
                prev_stats[sensor_name_str].push_back(residency_stats[index]);
            }
            values.push_back(tmp_atom_value);
        }
        if (abs(since_last_update_ms - sum_residency) > kMaxResidencyDiffMs)
            ALOGI("Thermal zone: %s Temperature residency stats not good!\ndevice sum_residency: "
                  "%ldms, since_last_update_ms %ldms\n",
                  sensor_name_str.c_str(), sum_residency, since_last_update_ms);

        //  Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kVendorTempResidencyStats,
                            .values = std::move(values)};
        ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk())
            ALOGE("Unable to report VendorTempResidencyStats to Stats service");

        cur_stats_map_iterator++;
    }
    prevTime = curTime;
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

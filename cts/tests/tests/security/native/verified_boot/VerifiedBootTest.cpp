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

#include <string>

#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <fs_mgr.h>
#include <fstab/fstab.h>
#include <gtest/gtest.h>

#include "utils.h"

// The relevant Android API levels
constexpr auto S_API_LEVEL = 31;

// As required by CDD, verified boot MUST use verification algorithms as strong
// as current recommendations from NIST for hashing algorithms (SHA-256).
// https://source.android.com/compatibility/11/android-11-cdd#9_10_device_integrity
TEST(VerifiedBootTest, avbHashtreeNotUsingSha1) {
  int first_api_level = getFirstApiLevel();
  int vendor_api_level = getVendorApiLevel();
  GTEST_LOG_(INFO) << "First API level is " << first_api_level;
  GTEST_LOG_(INFO) << "Vendor API level is " << vendor_api_level;
  if (first_api_level < S_API_LEVEL) {
    GTEST_LOG_(INFO)
        << "Exempt from avb hash tree test due to old starting API level";
    return;
  }

  // This feature name check only applies to devices that first shipped with
  // SC or later.
  int min_api_level = (first_api_level < vendor_api_level) ? first_api_level
                                                           : vendor_api_level;
  if (min_api_level >= S_API_LEVEL &&
      !deviceSupportsFeature("android.hardware.security.model.compatible")) {
      GTEST_SKIP()
          << "Skipping test: FEATURE_SECURITY_MODEL_COMPATIBLE missing.";
      return;
  }

  android::fs_mgr::Fstab mounted_fstab;
  ASSERT_TRUE(ReadFstabFromFile("/proc/mounts", &mounted_fstab))
      << "Failed to read the mounted fstab";
  // Build a list of mount points that are either mounted or known to have
  // importance.
  std::set<std::string> mount_points = {"/", "/system"};
  for (const auto& entry : mounted_fstab) {
    mount_points.insert(entry.mount_point);
  }
  android::fs_mgr::Fstab fstab;
  ASSERT_TRUE(ReadDefaultFstab(&fstab)) << "Failed to read default fstab";

  for (const auto& entry : fstab) {
    if (!entry.fs_mgr_flags.avb) {
      continue;
    }

    if (mount_points.find(entry.mount_point) == mount_points.end()) {
      GTEST_LOG_(INFO) << entry.mount_point << " isn't mounted, skipping"
          << " hashtree algorithm verification";
      continue;
    }

    if (mount_points.find(entry.mount_point) == mount_points.end()) {
      GTEST_LOG_(INFO) << entry.mount_point << " isn't mounted, skipping"
          << " hashtree algorithm verification";
      continue;
    }

    if (android::base::EqualsIgnoreCase(entry.fs_type, "emmc")) {
      GTEST_LOG_(INFO) << entry.mount_point << " has emmc fs_type, skipping"
          << " hashtree algorithm verification";
      continue;
    }

    GTEST_LOG_(INFO) << "partition enabled verity " << entry.mount_point;

    // The verity sysprop use "system" as the partition name in the system as
    // root case.
    std::string partition = entry.mount_point == "/"
                                ? "system"
                                : android::base::Basename(entry.mount_point);

    std::string alg_prop_name = "partition." + partition + ".verified.hash_alg";
    std::string hash_alg = android::base::GetProperty(alg_prop_name, "");
    ASSERT_FALSE(hash_alg.empty());
    ASSERT_FALSE(android::base::StartsWithIgnoreCase(hash_alg, "sha1"));
  }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "DeviceManifestTest.h"

#include <android-base/properties.h>
#include <android-base/result.h>
#include <libvts_vintf_test_common/common.h>
#include <vintf/VintfObject.h>

#include "SingleManifestTest.h"

namespace android {
namespace vintf {
namespace testing {

void DeviceManifestTest::SetUp() {
  VtsTrebleVintfTestBase::SetUp();

  vendor_manifest_ = VintfObject::GetDeviceHalManifest();
  ASSERT_NE(vendor_manifest_, nullptr)
      << "Failed to get vendor HAL manifest." << endl;
}

// Tests that Shipping FCM Version in the device manifest is at least the
// minimum Shipping FCM Version as required by Board API level.
TEST_F(DeviceManifestTest, ShippingFcmVersion) {
  uint64_t board_api_level = GetBoardApiLevel();
  Level shipping_fcm_version = VintfObject::GetDeviceHalManifest()->level();
  auto res = TestTargetFcmVersion(shipping_fcm_version, board_api_level);
  ASSERT_RESULT_OK(res);
}

TEST_F(DeviceManifestTest, KernelFcmVersion) {
  const char* kHeader =
      "Kernel FCM version (specified in VINTF manifests with <kernel "
      "target-level=\"[0-9]+\"/> if not by /proc/version) ";
  Level shipping_fcm_version = VintfObject::GetDeviceHalManifest()->level();

  if (shipping_fcm_version == Level::UNSPECIFIED ||
      shipping_fcm_version < Level::R) {
    GTEST_SKIP() << kHeader << " not enforced on target FCM version "
                 << shipping_fcm_version;
  }
  std::string error;
  Level kernel_fcm_version = VintfObject::GetInstance()->getKernelLevel(&error);
  ASSERT_NE(Level::UNSPECIFIED, kernel_fcm_version)
      << kHeader << " must be specified for target FCM version '"
      << shipping_fcm_version << "': " << error;
  ASSERT_GE(kernel_fcm_version, shipping_fcm_version)
      << kHeader << " is " << kernel_fcm_version
      << ", but it must be greater or equal to target FCM version "
      << shipping_fcm_version;
}

// Tests that deprecated HALs are not in the manifest, unless a higher,
// non-deprecated minor version is in the manifest.
TEST_F(DeviceManifestTest, NoDeprecatedHalsOnManifest) {
  string error;
  EXPECT_EQ(android::vintf::NO_DEPRECATED_HALS,
            VintfObject::GetInstance()->checkDeprecation(
                HidlInterfaceMetadata::all(), &error))
      << error;
}

// Tests that devices launching R support mapper@4.0.  Go devices are exempt
// from this requirement, so we use this test to enforce instead of the
// compatibility matrix.
TEST_F(DeviceManifestTest, GraphicsMapperHalVersionCompatibility) {
  Level shipping_fcm_version = VintfObject::GetDeviceHalManifest()->level();
  bool is_go_device =
      android::base::GetBoolProperty("ro.config.low_ram", false);
  if (shipping_fcm_version == Level::UNSPECIFIED ||
      shipping_fcm_version < Level::R || is_go_device) {
    GTEST_SKIP() << "Graphics mapper 4 is only required on launching R devices";
  }

  ASSERT_TRUE(vendor_manifest_->hasHidlInstance(
      "android.hardware.graphics.mapper", {4, 0}, "IMapper", "default"));
  ASSERT_FALSE(vendor_manifest_->hasHidlInstance(
      "android.hardware.graphics.mapper", {2, 0}, "IMapper", "default"));
  ASSERT_FALSE(vendor_manifest_->hasHidlInstance(
      "android.hardware.graphics.mapper", {2, 1}, "IMapper", "default"));
}

// Devices with Shipping FCM version 3~6 must have either the HIDL or the
// AIDL health HAL. Because compatibility matrices cannot express OR condition
// between <hal>'s, add a test here.
//
// There's no need to enforce minimum HAL versions because
// NoDeprecatedHalsOnManifest already checks it.
TEST_F(DeviceManifestTest, HealthHal) {
  bool has_hidl = vendor_manifest_->hasHidlInstance(
      "android.hardware.health", {2, 0}, "IHealth", "default");
  bool has_aidl = vendor_manifest_->hasAidlInstance("android.hardware.health",
                                                    1, "IHealth", "default");
  ASSERT_TRUE(has_hidl || has_aidl)
      << "Device must have either health HIDL HAL or AIDL HAL";
}

// Devices with Shipping FCM version 7 must have either the HIDL or the
// AIDL composer HAL. Because compatibility matrices cannot express OR condition
// between <hal>'s, add a test here.
//
// There's no need to enforce minimum HAL versions because
// NoDeprecatedHalsOnManifest already checks it.
TEST_F(DeviceManifestTest, ComposerHal) {
  bool has_hidl = vendor_manifest_->hasHidlInstance(
      "android.hardware.graphics.composer", {2, 1}, "IComposer", "default");
  bool has_aidl = vendor_manifest_->hasAidlInstance(
      "android.hardware.graphics.composer3", 1, "IComposer", "default");
  ASSERT_TRUE(has_hidl || has_aidl)
      << "Device must have either composer HIDL HAL or AIDL HAL";
}

// Devices with Shipping FCM version 7 must have either the HIDL or the
// AIDL gralloc HAL. Because compatibility matrices cannot express OR condition
// between <hal>'s, add a test here.
//
// There's no need to enforce minimum HAL versions because
// NoDeprecatedHalsOnManifest already checks it.
TEST_F(DeviceManifestTest, GrallocHal) {
  bool has_hidl = false;
  for (size_t hidl_major = 2; hidl_major <= 4; hidl_major++)
    has_hidl = has_hidl || vendor_manifest_->hasHidlInstance(
                               "android.hardware.graphics.allocator",
                               {hidl_major, 0}, "IAllocator", "default");

  bool has_aidl = vendor_manifest_->hasAidlInstance(
      "android.hardware.graphics.allocator", 1, "IAllocator", "default");

  ASSERT_TRUE(has_hidl || has_aidl)
      << "Device must have either graphics allocator HIDL HAL or AIDL HAL";
}

// Tests that devices launching T support allocator@4.0 or AIDL.
// Go devices are exempt
// from this requirement, so we use this test to enforce instead of the
// compatibility matrix.
TEST_F(DeviceManifestTest, GrallocHalVersionCompatibility) {
  Level shipping_fcm_version = VintfObject::GetDeviceHalManifest()->level();
  bool is_go_device =
      android::base::GetBoolProperty("ro.config.low_ram", false);
  if (shipping_fcm_version == Level::UNSPECIFIED ||
      shipping_fcm_version < Level::T || is_go_device) {
    GTEST_SKIP() << "Gralloc 4.0/AIDL is only required on launching T devices";
  }

  bool has_aidl = vendor_manifest_->hasAidlInstance(
      "android.hardware.graphics.allocator", 1, "IAllocator", "default");
  bool has_hidl_4_0 = vendor_manifest_->hasHidlInstance(
      "android.hardware.graphics.allocator", {4, 0}, "IAllocator", "default");
  ASSERT_TRUE(has_aidl || has_hidl_4_0);

  ASSERT_FALSE(vendor_manifest_->hasHidlInstance(
      "android.hardware.graphics.allocator", {2, 0}, "IAllocator", "default"));
  ASSERT_FALSE(vendor_manifest_->hasHidlInstance(
      "android.hardware.graphics.allocator", {3, 0}, "IAllocator", "default"));
}

static std::vector<HalManifestPtr> GetTestManifests() {
  return {
      VintfObject::GetDeviceHalManifest(),
  };
}

INSTANTIATE_TEST_CASE_P(DeviceManifest, SingleManifestTest,
                        ::testing::ValuesIn(GetTestManifests()));

}  // namespace testing
}  // namespace vintf
}  // namespace android

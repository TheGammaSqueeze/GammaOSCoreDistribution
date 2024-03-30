/*
 * Copyright 2021 The Android Open Source Project
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

#include <android/hardware/bluetooth/audio/2.1/IBluetoothAudioProvidersFactory.h>
#include <android/hardware/bluetooth/audio/2.1/types.h>

namespace bluetooth {
namespace audio {

using ::android::hardware::hidl_vec;

using IBluetoothAudioProvidersFactory_2_0 = ::android::hardware::bluetooth::
    audio::V2_0::IBluetoothAudioProvidersFactory;
using IBluetoothAudioProvidersFactory_2_1 = ::android::hardware::bluetooth::
    audio::V2_1::IBluetoothAudioProvidersFactory;

constexpr char kFullyQualifiedInterfaceName_2_0[] =
    "android.hardware.bluetooth.audio@2.0::IBluetoothAudioProvidersFactory";
constexpr char kFullyQualifiedInterfaceName_2_1[] =
    "android.hardware.bluetooth.audio@2.1::IBluetoothAudioProvidersFactory";

enum class BluetoothAudioHalVersion : uint8_t {
  VERSION_UNAVAILABLE = 0,
  VERSION_2_0,
  VERSION_2_1,
  VERSION_AIDL_V1,
};

enum class BluetoothAudioHalTransport : uint8_t {
  // Uninit, default value
  UNKNOWN,
  // No HAL available after init or force disabled
  DISABLED,
  AIDL,
  HIDL,
};

class HalVersionManager {
 public:
  static BluetoothAudioHalVersion GetHalVersion();

  static BluetoothAudioHalTransport GetHalTransport();

  static android::sp<IBluetoothAudioProvidersFactory_2_1>
  GetProvidersFactory_2_1();

  static android::sp<IBluetoothAudioProvidersFactory_2_0>
  GetProvidersFactory_2_0();

  HalVersionManager();

 private:
  static std::unique_ptr<HalVersionManager> instance_ptr;
  std::mutex mutex_;

  BluetoothAudioHalVersion hal_version_;
};

}  // namespace audio
}  // namespace bluetooth

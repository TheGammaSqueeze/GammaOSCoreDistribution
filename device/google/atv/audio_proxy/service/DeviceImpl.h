// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <set>

// clang-format off
#include PATH(android/hardware/audio/FILE_VERSION/IDevice.h)
// clang-format on
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace audio_proxy {
namespace service {

using ::android::sp;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_bitfield;
using ::android::hardware::hidl_death_recipient;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::audio::common::CPP_VERSION::AudioConfig;
using ::android::hardware::audio::common::CPP_VERSION::AudioPatchHandle;
using ::android::hardware::audio::common::CPP_VERSION::AudioPort;
using ::android::hardware::audio::common::CPP_VERSION::AudioPortConfig;
using ::android::hardware::audio::common::CPP_VERSION::AudioPortHandle;
using ::android::hardware::audio::common::CPP_VERSION::DeviceAddress;
using ::android::hardware::audio::common::CPP_VERSION::SinkMetadata;
using ::android::hardware::audio::common::CPP_VERSION::SourceMetadata;
using ::android::hardware::audio::CPP_VERSION::IDevice;
using ::android::hardware::audio::CPP_VERSION::ParameterValue;
using ::android::hardware::audio::CPP_VERSION::Result;

#if MAJOR_VERSION >= 7
using ::android::hardware::audio::CPP_VERSION::AudioInOutFlag;
#else
using ::android::hardware::audio::common::CPP_VERSION::AudioInputFlag;
using ::android::hardware::audio::common::CPP_VERSION::AudioOutputFlag;
#endif

class BusStreamProvider;
struct ServiceConfig;

#if MAJOR_VERSION == 7 && MINOR_VERSION == 1
class DeviceImpl : public android::hardware::audio::V7_1::IDevice {
#else
class DeviceImpl : public IDevice {
#endif
 public:
  DeviceImpl(BusStreamProvider& busStreamProvider, const ServiceConfig& config);

  // Methods from ::android::hardware::audio::V5_0::IDevice follow.
  Return<Result> initCheck() override;
  Return<Result> setMasterVolume(float volume) override;
  Return<void> getMasterVolume(getMasterVolume_cb _hidl_cb) override;
  Return<Result> setMicMute(bool mute) override;
  Return<void> getMicMute(getMicMute_cb _hidl_cb) override;
  Return<Result> setMasterMute(bool mute) override;
  Return<void> getMasterMute(getMasterMute_cb _hidl_cb) override;
  Return<void> getInputBufferSize(const AudioConfig& config,
                                  getInputBufferSize_cb _hidl_cb) override;

#if MAJOR_VERSION >= 7
  Return<void> openOutputStream(int32_t ioHandle, const DeviceAddress& device,
                                const AudioConfig& config,
                                const hidl_vec<AudioInOutFlag>& flags,
                                const SourceMetadata& sourceMetadata,
                                openOutputStream_cb _hidl_cb) override;
  Return<void> openInputStream(int32_t ioHandle, const DeviceAddress& device,
                               const AudioConfig& config,
                               const hidl_vec<AudioInOutFlag>& flags,
                               const SinkMetadata& sinkMetadata,
                               openInputStream_cb _hidl_cb) override;
#else
  Return<void> openOutputStream(int32_t ioHandle, const DeviceAddress& device,
                                const AudioConfig& config,
                                hidl_bitfield<AudioOutputFlag> flags,
                                const SourceMetadata& sourceMetadata,
                                openOutputStream_cb _hidl_cb) override;
  Return<void> openInputStream(int32_t ioHandle, const DeviceAddress& device,
                               const AudioConfig& config,
                               hidl_bitfield<AudioInputFlag> flags,
                               const SinkMetadata& sinkMetadata,
                               openInputStream_cb _hidl_cb) override;
#endif

  Return<bool> supportsAudioPatches() override;
  Return<void> createAudioPatch(const hidl_vec<AudioPortConfig>& sources,
                                const hidl_vec<AudioPortConfig>& sinks,
                                createAudioPatch_cb _hidl_cb) override;
  Return<Result> releaseAudioPatch(AudioPatchHandle patch) override;
  Return<void> getAudioPort(const AudioPort& port,
                            getAudioPort_cb _hidl_cb) override;
  Return<Result> setAudioPortConfig(const AudioPortConfig& config) override;
  Return<void> getHwAvSync(getHwAvSync_cb _hidl_cb) override;
  Return<Result> setScreenState(bool turnedOn) override;
  Return<void> getParameters(const hidl_vec<ParameterValue>& context,
                             const hidl_vec<hidl_string>& keys,
                             getParameters_cb _hidl_cb) override;
  Return<Result> setParameters(
      const hidl_vec<ParameterValue>& context,
      const hidl_vec<ParameterValue>& parameters) override;
  Return<void> getMicrophones(getMicrophones_cb _hidl_cb) override;
  Return<Result> setConnectedState(const DeviceAddress& address,
                                   bool connected) override;

#if MAJOR_VERSION >= 6
  Return<void> updateAudioPatch(AudioPatchHandle previousPatch,
                                const hidl_vec<AudioPortConfig>& sources,
                                const hidl_vec<AudioPortConfig>& sinks,
                                updateAudioPatch_cb _hidl_cb) override;
  Return<Result> close() override;
  Return<Result> addDeviceEffect(AudioPortHandle device,
                                 uint64_t effectId) override;
  Return<Result> removeDeviceEffect(AudioPortHandle device,
                                    uint64_t effectId) override;
#endif

#if MAJOR_VERSION == 7 && MINOR_VERSION == 1
  Return<void> openOutputStream_7_1(int32_t ioHandle,
                                    const DeviceAddress& device,
                                    const AudioConfig& config,
                                    const hidl_vec<AudioInOutFlag>& flags,
                                    const SourceMetadata& sourceMetadata,
                                    openOutputStream_7_1_cb _hidl_cb) override;
  Return<Result> setConnectedState_7_1(const AudioPort& devicePort,
                                       bool connected) override;
#endif

 private:
#if MAJOR_VERSION >= 7
  template<typename CallbackType>
  Return<void> openOutputStreamImpl(int32_t ioHandle,
                                    const DeviceAddress& device,
                                    const AudioConfig& config,
                                    const hidl_vec<AudioInOutFlag>& flags,
                                    const SourceMetadata& sourceMetadata,
                                    CallbackType _hidl_cb);
#endif

  BusStreamProvider& mBusStreamProvider;
  const ServiceConfig& mServiceConfig;
  std::set<AudioPatchHandle> mAudioPatchHandles;
};

}  // namespace service
}  // namespace audio_proxy

/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "BTAudioProviderLeAudioSW"

#include "LeAudioSoftwareAudioProvider.h"

#include <BluetoothAudioCodecs.h>
#include <BluetoothAudioSessionReport.h>
#include <android-base/logging.h>

#include <cstdint>

namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {

static constexpr uint32_t kBufferOutCount = 2;  // two frame buffer
static constexpr uint32_t kBufferInCount = 2;   // two frame buffer

inline uint32_t channel_mode_to_channel_count(ChannelMode channel_mode) {
  switch (channel_mode) {
    case ChannelMode::MONO:
      return 1;
    case ChannelMode::STEREO:
      return 2;
    default:
      return 0;
  }
  return 0;
}

LeAudioSoftwareOutputAudioProvider::LeAudioSoftwareOutputAudioProvider()
    : LeAudioSoftwareAudioProvider() {
  session_type_ = SessionType::LE_AUDIO_SOFTWARE_ENCODING_DATAPATH;
}

LeAudioSoftwareInputAudioProvider::LeAudioSoftwareInputAudioProvider()
    : LeAudioSoftwareAudioProvider() {
  session_type_ = SessionType::LE_AUDIO_SOFTWARE_DECODING_DATAPATH;
}

LeAudioSoftwareBroadcastAudioProvider::LeAudioSoftwareBroadcastAudioProvider()
    : LeAudioSoftwareAudioProvider() {
  session_type_ = SessionType::LE_AUDIO_BROADCAST_SOFTWARE_ENCODING_DATAPATH;
}

LeAudioSoftwareAudioProvider::LeAudioSoftwareAudioProvider()
    : BluetoothAudioProvider(), data_mq_(nullptr) {}

bool LeAudioSoftwareAudioProvider::isValid(const SessionType& sessionType) {
  return (sessionType == session_type_);
}

ndk::ScopedAStatus LeAudioSoftwareAudioProvider::startSession(
    const std::shared_ptr<IBluetoothAudioPort>& host_if,
    const AudioConfiguration& audio_config,
    const std::vector<LatencyMode>& latency_modes, DataMQDesc* _aidl_return) {
  if (audio_config.getTag() != AudioConfiguration::pcmConfig) {
    LOG(WARNING) << __func__ << " - Invalid Audio Configuration="
                 << audio_config.toString();
    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  const auto& pcm_config = audio_config.get<AudioConfiguration::pcmConfig>();
  if (!BluetoothAudioCodecs::IsSoftwarePcmConfigurationValid(pcm_config)) {
    LOG(WARNING) << __func__ << " - Unsupported PCM Configuration="
                 << pcm_config.toString();
    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }

  uint32_t buffer_modifier = 0;
  if (session_type_ == SessionType::LE_AUDIO_SOFTWARE_ENCODING_DATAPATH ||
      session_type_ ==
          SessionType::LE_AUDIO_BROADCAST_SOFTWARE_ENCODING_DATAPATH)
    buffer_modifier = kBufferOutCount;
  else if (session_type_ == SessionType::LE_AUDIO_SOFTWARE_DECODING_DATAPATH)
    buffer_modifier = kBufferInCount;

  // 24 bit audio stream is sent as unpacked
  int bytes_per_sample =
      (pcm_config.bitsPerSample == 24) ? 4 : (pcm_config.bitsPerSample / 8);

  uint32_t data_mq_size =
      (ceil(pcm_config.sampleRateHz) / 1000) *
      channel_mode_to_channel_count(pcm_config.channelMode) * bytes_per_sample *
      (pcm_config.dataIntervalUs / 1000) * buffer_modifier;
  if (data_mq_size <= 0) {
    LOG(ERROR) << __func__ << "Unexpected audio buffer size: " << data_mq_size
               << ", SampleRateHz: " << pcm_config.sampleRateHz
               << ", ChannelMode: " << toString(pcm_config.channelMode)
               << ", BitsPerSample: "
               << static_cast<int>(pcm_config.bitsPerSample)
               << ", BytesPerSample: " << bytes_per_sample
               << ", DataIntervalUs: " << pcm_config.dataIntervalUs
               << ", SessionType: " << toString(session_type_);
    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }

  LOG(INFO) << __func__ << " - size of audio buffer " << data_mq_size
            << " byte(s)";

  std::unique_ptr<DataMQ> temp_data_mq(
      new DataMQ(data_mq_size, /* EventFlag */ true));
  if (temp_data_mq == nullptr || !temp_data_mq->isValid()) {
    ALOGE_IF(!temp_data_mq, "failed to allocate data MQ");
    ALOGE_IF(temp_data_mq && !temp_data_mq->isValid(), "data MQ is invalid");
    *_aidl_return = DataMQDesc();
    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  data_mq_ = std::move(temp_data_mq);

  return BluetoothAudioProvider::startSession(
      host_if, audio_config, latency_modes, _aidl_return);
}

ndk::ScopedAStatus LeAudioSoftwareAudioProvider::onSessionReady(
    DataMQDesc* _aidl_return) {
  if (data_mq_ == nullptr || !data_mq_->isValid()) {
    *_aidl_return = DataMQDesc();
    return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
  }
  *_aidl_return = data_mq_->dupeDesc();
  auto desc = data_mq_->dupeDesc();
  BluetoothAudioSessionReport::OnSessionStarted(
      session_type_, stack_iface_, &desc, *audio_config_, latency_modes_);
  return ndk::ScopedAStatus::ok();
}

}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl

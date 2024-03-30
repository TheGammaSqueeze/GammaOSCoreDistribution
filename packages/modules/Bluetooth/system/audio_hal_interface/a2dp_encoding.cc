/*
 * Copyright 2022 The Android Open Source Project
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

#include "a2dp_encoding.h"

#include "aidl/a2dp_encoding_aidl.h"
#include "hal_version_manager.h"
#include "hidl/a2dp_encoding_hidl.h"

namespace bluetooth {
namespace audio {
namespace a2dp {

bool update_codec_offloading_capabilities(
    const std::vector<btav_a2dp_codec_config_t>& framework_preference) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::a2dp::update_codec_offloading_capabilities(
        framework_preference);
  }
  return aidl::a2dp::update_codec_offloading_capabilities(framework_preference);
}

// Check if new bluetooth_audio is enabled
bool is_hal_enabled() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::a2dp::is_hal_2_0_enabled();
  }
  return aidl::a2dp::is_hal_enabled();
}

// Check if new bluetooth_audio is running with offloading encoders
bool is_hal_offloading() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::a2dp::is_hal_2_0_offloading();
  }
  return aidl::a2dp::is_hal_offloading();
}

// Initialize BluetoothAudio HAL: openProvider
bool init(bluetooth::common::MessageLoopThread* message_loop) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::a2dp::init(message_loop);
  }
  return aidl::a2dp::init(message_loop);
}

// Clean up BluetoothAudio HAL
void cleanup() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::a2dp::cleanup();
    return;
  }
  aidl::a2dp::cleanup();
}

// Set up the codec into BluetoothAudio HAL
bool setup_codec() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::a2dp::setup_codec();
  }
  return aidl::a2dp::setup_codec();
}

// Send command to the BluetoothAudio HAL: StartSession, EndSession,
// StreamStarted, StreamSuspended
void start_session() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::a2dp::start_session();
    return;
  }
  aidl::a2dp::start_session();
}
void end_session() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::AIDL) {
    return aidl::a2dp::end_session();
  }
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::a2dp::end_session();
    return;
  }
}
void ack_stream_started(const tA2DP_CTRL_ACK& status) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::a2dp::ack_stream_started(status);
    return;
  }
  return aidl::a2dp::ack_stream_started(status);
}
void ack_stream_suspended(const tA2DP_CTRL_ACK& status) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::a2dp::ack_stream_suspended(status);
    return;
  }
  aidl::a2dp::ack_stream_suspended(status);
}

// Read from the FMQ of BluetoothAudio HAL
size_t read(uint8_t* p_buf, uint32_t len) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::a2dp::read(p_buf, len);
  }
  return aidl::a2dp::read(p_buf, len);
}

// Update A2DP delay report to BluetoothAudio HAL
void set_remote_delay(uint16_t delay_report) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::a2dp::set_remote_delay(delay_report);
    return;
  }
  aidl::a2dp::set_remote_delay(delay_report);
}

// Set low latency buffer mode allowed or disallowed
void set_audio_low_latency_mode_allowed(bool allowed) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::AIDL) {
    aidl::a2dp::set_low_latency_mode_allowed(allowed);
  }
}

// Check if OPUS codec is supported
bool is_opus_supported() {
  // OPUS codec was added after HIDL HAL was frozen
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::AIDL) {
    return true;
  }
  return false;
}

}  // namespace a2dp
}  // namespace audio
}  // namespace bluetooth

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

#include "bluetooth_audio_port_impl.h"

#include "btif/include/btif_common.h"
#include "common/stop_watch_legacy.h"

namespace bluetooth {
namespace audio {
namespace aidl {

using ::bluetooth::common::StopWatchLegacy;

BluetoothAudioPortImpl::BluetoothAudioPortImpl(
    IBluetoothTransportInstance* transport_instance,
    const std::shared_ptr<IBluetoothAudioProvider>& provider)
    : transport_instance_(transport_instance), provider_(provider) {}

BluetoothAudioPortImpl::~BluetoothAudioPortImpl() {}

ndk::ScopedAStatus BluetoothAudioPortImpl::startStream(bool is_low_latency) {
  StopWatchLegacy stop_watch(__func__);
  BluetoothAudioCtrlAck ack = transport_instance_->StartRequest(is_low_latency);
  if (ack != BluetoothAudioCtrlAck::PENDING) {
    auto aidl_retval =
        provider_->streamStarted(BluetoothAudioCtrlAckToHalStatus(ack));
    if (!aidl_retval.isOk()) {
      LOG(ERROR) << __func__ << ": BluetoothAudioHal failure: "
                 << aidl_retval.getDescription();
    }
  }
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus BluetoothAudioPortImpl::suspendStream() {
  StopWatchLegacy stop_watch(__func__);
  BluetoothAudioCtrlAck ack = transport_instance_->SuspendRequest();
  if (ack != BluetoothAudioCtrlAck::PENDING) {
    auto aidl_retval =
        provider_->streamSuspended(BluetoothAudioCtrlAckToHalStatus(ack));
    if (!aidl_retval.isOk()) {
      LOG(ERROR) << __func__ << ": BluetoothAudioHal failure: "
                 << aidl_retval.getDescription();
    }
  }
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus BluetoothAudioPortImpl::stopStream() {
  StopWatchLegacy stop_watch(__func__);
  transport_instance_->StopRequest();
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus BluetoothAudioPortImpl::getPresentationPosition(
    PresentationPosition* _aidl_return) {
  StopWatchLegacy stop_watch(__func__);
  uint64_t remote_delay_report_ns;
  uint64_t total_bytes_read;
  timespec data_position;
  bool retval = transport_instance_->GetPresentationPosition(
      &remote_delay_report_ns, &total_bytes_read, &data_position);

  PresentationPosition::TimeSpec transmittedOctetsTimeStamp;
  if (retval) {
    transmittedOctetsTimeStamp = timespec_convert_to_hal(data_position);
  } else {
    remote_delay_report_ns = 0;
    total_bytes_read = 0;
    transmittedOctetsTimeStamp = {};
  }
  VLOG(2) << __func__ << ": result=" << retval
          << ", delay=" << remote_delay_report_ns
          << ", data=" << total_bytes_read
          << " byte(s), timestamp=" << transmittedOctetsTimeStamp.toString();
  _aidl_return->remoteDeviceAudioDelayNanos =
      static_cast<int64_t>(remote_delay_report_ns);
  _aidl_return->transmittedOctets = static_cast<int64_t>(total_bytes_read);
  _aidl_return->transmittedOctetsTimestamp = transmittedOctetsTimeStamp;
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus BluetoothAudioPortImpl::updateSourceMetadata(
    const SourceMetadata& source_metadata) {
  StopWatchLegacy stop_watch(__func__);
  LOG(INFO) << __func__ << ": " << source_metadata.tracks.size() << "track(s)";

  std::vector<playback_track_metadata> metadata_vec;
  metadata_vec.reserve(source_metadata.tracks.size());
  for (const auto& metadata : source_metadata.tracks) {
    metadata_vec.push_back({
        .usage = static_cast<audio_usage_t>(metadata.usage),
        .content_type = static_cast<audio_content_type_t>(metadata.contentType),
        .gain = metadata.gain,
    });
  }
  const source_metadata_t legacy_source_metadata = {
      .track_count = metadata_vec.size(), .tracks = metadata_vec.data()};
  transport_instance_->SourceMetadataChanged(legacy_source_metadata);
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus BluetoothAudioPortImpl::updateSinkMetadata(
    const SinkMetadata& sink_metadata) {
  StopWatchLegacy stop_watch(__func__);
  LOG(INFO) << __func__ << ": " << sink_metadata.tracks.size() << " track(s)";

  std::vector<record_track_metadata> metadata_vec;
  metadata_vec.reserve(sink_metadata.tracks.size());
  for (const auto& metadata : sink_metadata.tracks) {
    metadata_vec.push_back({
        .source = static_cast<audio_source_t>(metadata.source),
        .gain = metadata.gain,
    });
  }
  const sink_metadata_t legacy_sink_metadata = {
      .track_count = metadata_vec.size(), .tracks = metadata_vec.data()};
  transport_instance_->SinkMetadataChanged(legacy_sink_metadata);
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus BluetoothAudioPortImpl::setLatencyMode(
    LatencyMode latency_mode) {
  bool is_low_latency = latency_mode == LatencyMode::LOW_LATENCY ? true : false;
  invoke_switch_buffer_size_cb(is_low_latency);
  transport_instance_->SetLowLatency(is_low_latency);
  return ndk::ScopedAStatus::ok();
}

PresentationPosition::TimeSpec BluetoothAudioPortImpl::timespec_convert_to_hal(
    const timespec& ts) {
  return {.tvSec = static_cast<int64_t>(ts.tv_sec),
          .tvNSec = static_cast<int64_t>(ts.tv_nsec)};
}

}  // namespace aidl
}  // namespace audio
}  // namespace bluetooth

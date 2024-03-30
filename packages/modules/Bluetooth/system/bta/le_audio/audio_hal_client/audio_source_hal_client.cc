/******************************************************************************
 *
 * Copyright 2019 HIMSA II K/S - www.himsa.com.Represented by EHIMA -
 * www.ehima.com
 * Copyright (c) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

#include "audio_hal_client.h"
#include "audio_hal_interface/le_audio_software.h"
#include "bta/le_audio/codec_manager.h"
#include "btu.h"
#include "common/time_util.h"
#include "osi/include/log.h"
#include "osi/include/wakelock.h"

using bluetooth::audio::le_audio::LeAudioClientInterface;

namespace le_audio {
namespace {
// TODO: HAL state should be in the HAL implementation
enum {
  HAL_UNINITIALIZED,
  HAL_STOPPED,
  HAL_STARTED,
} le_audio_sink_hal_state;

struct AudioHalStats {
  size_t media_read_total_underflow_bytes;
  size_t media_read_total_underflow_count;
  uint64_t media_read_last_underflow_us;

  AudioHalStats() { Reset(); }

  void Reset() {
    media_read_total_underflow_bytes = 0;
    media_read_total_underflow_count = 0;
    media_read_last_underflow_us = 0;
  }
} sStats;

class SourceImpl : public LeAudioSourceAudioHalClient {
 public:
  // Interface implementation
  bool Start(const LeAudioCodecConfiguration& codec_configuration,
             LeAudioSourceAudioHalClient::Callbacks* audioReceiver) override;
  void Stop() override;
  void ConfirmStreamingRequest() override;
  void CancelStreamingRequest() override;
  void UpdateRemoteDelay(uint16_t remote_delay_ms) override;
  void UpdateAudioConfigToHal(
      const ::le_audio::offload_config& config) override;
  void UpdateBroadcastAudioConfigToHal(
      const ::le_audio::broadcast_offload_config& config) override;
  void SuspendedForReconfiguration() override;
  void ReconfigurationComplete() override;

  // Internal functionality
  SourceImpl(bool is_broadcaster) : is_broadcaster_(is_broadcaster){};
  ~SourceImpl() override {
    if (le_audio_sink_hal_state != HAL_UNINITIALIZED) Release();
  }

  bool OnResumeReq(bool start_media_task);
  bool OnSuspendReq();
  bool OnMetadataUpdateReq(const source_metadata_t& source_metadata);
  bool Acquire();
  void Release();
  bool InitAudioSinkThread();

  bluetooth::common::MessageLoopThread* worker_thread_;
  bluetooth::common::RepeatingTimer audio_timer_;
  LeAudioCodecConfiguration source_codec_config_;
  void StartAudioTicks();
  void StopAudioTicks();
  void SendAudioData();

  bool is_broadcaster_;

  bluetooth::audio::le_audio::LeAudioClientInterface::Sink* halSinkInterface_ =
      nullptr;
  LeAudioSourceAudioHalClient::Callbacks* audioSourceCallbacks_ = nullptr;
  std::mutex audioSourceCallbacksMutex_;
};

bool SourceImpl::Acquire() {
  auto sink_stream_cb = bluetooth::audio::le_audio::StreamCallbacks{
      .on_resume_ =
          std::bind(&SourceImpl::OnResumeReq, this, std::placeholders::_1),
      .on_suspend_ = std::bind(&SourceImpl::OnSuspendReq, this),
      .on_metadata_update_ = std::bind(&SourceImpl::OnMetadataUpdateReq, this,
                                       std::placeholders::_1),
      .on_sink_metadata_update_ =
          [](const sink_metadata_t& sink_metadata) {
            // TODO: update microphone configuration based on sink metadata
            return true;
          },
  };

  /* Get pointer to singleton LE audio client interface */
  auto halInterface = LeAudioClientInterface::Get();
  if (halInterface == nullptr) {
    LOG_ERROR("Can't get LE Audio HAL interface");
    return false;
  }

  halSinkInterface_ =
      halInterface->GetSink(sink_stream_cb, get_main_thread(), is_broadcaster_);

  if (halSinkInterface_ == nullptr) {
    LOG_ERROR("Can't get Audio HAL Audio sink interface");
    return false;
  }

  LOG_INFO();
  le_audio_sink_hal_state = HAL_STOPPED;
  return this->InitAudioSinkThread();
}

void SourceImpl::Release() {
  if (le_audio_sink_hal_state == HAL_UNINITIALIZED) {
    LOG_WARN("Audio HAL Audio sink is not running");
    return;
  }

  LOG_INFO();
  worker_thread_->ShutDown();

  if (halSinkInterface_) {
    halSinkInterface_->Cleanup();

    auto halInterface = LeAudioClientInterface::Get();
    if (halInterface != nullptr) {
      halInterface->ReleaseSink(halSinkInterface_);
    } else {
      LOG_ERROR("Can't get LE Audio HAL interface");
    }

    le_audio_sink_hal_state = HAL_UNINITIALIZED;
    halSinkInterface_ = nullptr;
  }
}

bool SourceImpl::OnResumeReq(bool start_media_task) {
  std::lock_guard<std::mutex> guard(audioSourceCallbacksMutex_);
  if (audioSourceCallbacks_ == nullptr) {
    LOG_ERROR("audioSourceCallbacks_ not set");
    return false;
  }
  bt_status_t status = do_in_main_thread(
      FROM_HERE,
      base::BindOnce(&LeAudioSourceAudioHalClient::Callbacks::OnAudioResume,
                     base::Unretained(audioSourceCallbacks_)));
  if (status == BT_STATUS_SUCCESS) {
    return true;
  }

  LOG_ERROR("do_in_main_thread err=%d", status);
  return false;
}

void SourceImpl::SendAudioData() {
  if (halSinkInterface_ == nullptr) {
    LOG_ERROR("Audio HAL Audio sink interface not acquired - aborting");
    return;
  }

  // 24 bit audio is aligned to 32bit
  int bytes_per_sample = (source_codec_config_.bits_per_sample == 24)
                             ? 4
                             : (source_codec_config_.bits_per_sample / 8);
  uint32_t bytes_per_tick =
      (source_codec_config_.num_channels * source_codec_config_.sample_rate *
       source_codec_config_.data_interval_us / 1000 * bytes_per_sample) /
      1000;
  std::vector<uint8_t> data(bytes_per_tick);

  uint32_t bytes_read = halSinkInterface_->Read(data.data(), bytes_per_tick);
  if (bytes_read < bytes_per_tick) {
    sStats.media_read_total_underflow_bytes += bytes_per_tick - bytes_read;
    sStats.media_read_total_underflow_count++;
    sStats.media_read_last_underflow_us =
        bluetooth::common::time_get_os_boottime_us();
  }

  std::lock_guard<std::mutex> guard(audioSourceCallbacksMutex_);
  if (audioSourceCallbacks_ != nullptr) {
    audioSourceCallbacks_->OnAudioDataReady(data);
  }
}

bool SourceImpl::InitAudioSinkThread() {
  const std::string thread_name =
      is_broadcaster_ ? "bt_le_audio_broadcast_sink_worker_thread"
                      : "bt_le_audio_unicast_sink_worker_thread";
  worker_thread_ = new bluetooth::common::MessageLoopThread(thread_name);

  worker_thread_->StartUp();
  if (!worker_thread_->IsRunning()) {
    LOG_ERROR("Unable to start up the BLE audio sink worker thread");
    return false;
  }

  /* Schedule the rest of the operations */
  if (!worker_thread_->EnableRealTimeScheduling()) {
#if defined(OS_ANDROID)
    LOG(FATAL) << __func__ << ", Failed to increase media thread priority";
#endif
  }

  return true;
}

void SourceImpl::StartAudioTicks() {
  wakelock_acquire();
  audio_timer_.SchedulePeriodic(
      worker_thread_->GetWeakPtr(), FROM_HERE,
      base::Bind(&SourceImpl::SendAudioData, base::Unretained(this)),
#if BASE_VER < 931007
      base::TimeDelta::FromMicroseconds(source_codec_config_.data_interval_us));
#else
      base::Microseconds(source_codec_config_.data_interval_us));
#endif
}

void SourceImpl::StopAudioTicks() {
  audio_timer_.CancelAndWait();
  wakelock_release();
}

bool SourceImpl::OnSuspendReq() {
  std::lock_guard<std::mutex> guard(audioSourceCallbacksMutex_);
  if (CodecManager::GetInstance()->GetCodecLocation() ==
      types::CodecLocation::HOST) {
    StopAudioTicks();
  }

  if (audioSourceCallbacks_ == nullptr) {
    LOG_ERROR("audioSourceCallbacks_ not set");
    return false;
  }

  // Call OnAudioSuspend and block till it returns.
  std::promise<void> do_suspend_promise;
  std::future<void> do_suspend_future = do_suspend_promise.get_future();
  bt_status_t status = do_in_main_thread(
      FROM_HERE,
      base::BindOnce(&LeAudioSourceAudioHalClient::Callbacks::OnAudioSuspend,
                     base::Unretained(audioSourceCallbacks_),
                     std::move(do_suspend_promise)));
  if (status == BT_STATUS_SUCCESS) {
    do_suspend_future.wait();
    return true;
  }

  LOG_ERROR("do_in_main_thread err=%d", status);
  return false;
}

bool SourceImpl::OnMetadataUpdateReq(const source_metadata_t& source_metadata) {
  std::lock_guard<std::mutex> guard(audioSourceCallbacksMutex_);
  if (audioSourceCallbacks_ == nullptr) {
    LOG(ERROR) << __func__ << ", audio receiver not started";
    return false;
  }

  std::vector<struct playback_track_metadata> metadata;
  for (size_t i = 0; i < source_metadata.track_count; i++) {
    metadata.push_back(source_metadata.tracks[i]);
  }

  bt_status_t status = do_in_main_thread(
      FROM_HERE,
      base::BindOnce(
          &LeAudioSourceAudioHalClient::Callbacks::OnAudioMetadataUpdate,
          base::Unretained(audioSourceCallbacks_), metadata));
  if (status == BT_STATUS_SUCCESS) {
    return true;
  }

  LOG_ERROR("do_in_main_thread err=%d", status);
  return false;
}

bool SourceImpl::Start(const LeAudioCodecConfiguration& codec_configuration,
                       LeAudioSourceAudioHalClient::Callbacks* audioReceiver) {
  if (!halSinkInterface_) {
    LOG_ERROR("Audio HAL Audio sink interface not acquired");
    return false;
  }

  if (le_audio_sink_hal_state == HAL_STARTED) {
    LOG_ERROR("Audio HAL Audio sink is already in use");
    return false;
  }

  LOG_INFO("bit rate: %d, num channels: %d, sample rate: %d, data interval: %d",
           codec_configuration.bits_per_sample,
           codec_configuration.num_channels, codec_configuration.sample_rate,
           codec_configuration.data_interval_us);

  sStats.Reset();

  /* Global config for periodic audio data */
  source_codec_config_ = codec_configuration;
  LeAudioClientInterface::PcmParameters pcmParameters = {
      .data_interval_us = codec_configuration.data_interval_us,
      .sample_rate = codec_configuration.sample_rate,
      .bits_per_sample = codec_configuration.bits_per_sample,
      .channels_count = codec_configuration.num_channels};

  halSinkInterface_->SetPcmParameters(pcmParameters);
  halSinkInterface_->StartSession();

  std::lock_guard<std::mutex> guard(audioSourceCallbacksMutex_);
  audioSourceCallbacks_ = audioReceiver;
  le_audio_sink_hal_state = HAL_STARTED;
  return true;
}

void SourceImpl::Stop() {
  if (!halSinkInterface_) {
    LOG_ERROR("Audio HAL Audio sink interface already stopped");
    return;
  }

  if (le_audio_sink_hal_state != HAL_STARTED) {
    LOG_ERROR("Audio HAL Audio sink was not started!");
    return;
  }

  LOG_INFO();

  halSinkInterface_->StopSession();
  le_audio_sink_hal_state = HAL_STOPPED;

  if (CodecManager::GetInstance()->GetCodecLocation() ==
      types::CodecLocation::HOST) {
    StopAudioTicks();
  }

  std::lock_guard<std::mutex> guard(audioSourceCallbacksMutex_);
  audioSourceCallbacks_ = nullptr;
}

void SourceImpl::ConfirmStreamingRequest() {
  if ((halSinkInterface_ == nullptr) ||
      (le_audio_sink_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio sink was not started!");
    return;
  }

  LOG_INFO();
  halSinkInterface_->ConfirmStreamingRequest();
  if (CodecManager::GetInstance()->GetCodecLocation() !=
      types::CodecLocation::HOST)
    return;

  StartAudioTicks();
}

void SourceImpl::SuspendedForReconfiguration() {
  if ((halSinkInterface_ == nullptr) ||
      (le_audio_sink_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio sink was not started!");
    return;
  }

  LOG_INFO();
  halSinkInterface_->SuspendedForReconfiguration();
}

void SourceImpl::ReconfigurationComplete() {
  if ((halSinkInterface_ == nullptr) ||
      (le_audio_sink_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio sink was not started!");
    return;
  }

  LOG_INFO();
  halSinkInterface_->ReconfigurationComplete();
}

void SourceImpl::CancelStreamingRequest() {
  if ((halSinkInterface_ == nullptr) ||
      (le_audio_sink_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio sink was not started!");
    return;
  }

  LOG_INFO();
  halSinkInterface_->CancelStreamingRequest();
}

void SourceImpl::UpdateRemoteDelay(uint16_t remote_delay_ms) {
  if ((halSinkInterface_ == nullptr) ||
      (le_audio_sink_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio sink was not started!");
    return;
  }

  LOG_INFO();
  halSinkInterface_->SetRemoteDelay(remote_delay_ms);
}

void SourceImpl::UpdateAudioConfigToHal(
    const ::le_audio::offload_config& config) {
  if ((halSinkInterface_ == nullptr) ||
      (le_audio_sink_hal_state != HAL_STARTED)) {
    LOG_ERROR("Audio HAL Audio sink was not started!");
    return;
  }

  LOG_INFO();
  halSinkInterface_->UpdateAudioConfigToHal(config);
}

void SourceImpl::UpdateBroadcastAudioConfigToHal(
    const ::le_audio::broadcast_offload_config& config) {
  if (halSinkInterface_ == nullptr) {
    LOG_ERROR("Audio HAL Audio sink interface not acquired");
    return;
  }

  LOG_INFO();
  halSinkInterface_->UpdateBroadcastAudioConfigToHal(config);
}
}  // namespace

std::unique_ptr<LeAudioSourceAudioHalClient>
LeAudioSourceAudioHalClient::AcquireUnicast() {
  std::unique_ptr<SourceImpl> impl(new SourceImpl(false));
  if (!impl->Acquire()) {
    LOG_ERROR("Could not acquire Unicast Source on LE Audio HAL enpoint");
    impl.reset();
    return nullptr;
  }

  LOG_INFO();
  return std::move(impl);
}

std::unique_ptr<LeAudioSourceAudioHalClient>
LeAudioSourceAudioHalClient::AcquireBroadcast() {
  std::unique_ptr<SourceImpl> impl(new SourceImpl(true));
  if (!impl->Acquire()) {
    LOG_ERROR("Could not acquire Broadcast Source on LE Audio HAL enpoint");
    impl.reset();
    return nullptr;
  }

  LOG_INFO();
  return std::move(impl);
}

void LeAudioSourceAudioHalClient::DebugDump(int fd) {
  uint64_t now_us = bluetooth::common::time_get_os_boottime_us();
  std::stringstream stream;
  stream << "  LE AudioHalClient:"
         << "\n    Counts (underflow)                                      : "
         << sStats.media_read_total_underflow_count
         << "\n    Bytes (underflow)                                       : "
         << sStats.media_read_total_underflow_bytes
         << "\n    Last update time ago in ms (underflow)                  : "
         << (sStats.media_read_last_underflow_us > 0
                 ? (unsigned long long)(now_us -
                                        sStats.media_read_last_underflow_us) /
                       1000
                 : 0)
         << std::endl;
  dprintf(fd, "%s", stream.str().c_str());
}
}  // namespace le_audio

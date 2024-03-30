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

#define LOG_TAG "BTAudioClientIf"

#include "client_interface_aidl.h"

#include <android/binder_manager.h>

namespace bluetooth {
namespace audio {
namespace aidl {

std::ostream& operator<<(std::ostream& os, const BluetoothAudioCtrlAck& ack) {
  switch (ack) {
    case BluetoothAudioCtrlAck::SUCCESS_FINISHED:
      return os << "SUCCESS_FINISHED";
    case BluetoothAudioCtrlAck::PENDING:
      return os << "PENDING";
    case BluetoothAudioCtrlAck::FAILURE_UNSUPPORTED:
      return os << "FAILURE_UNSUPPORTED";
    case BluetoothAudioCtrlAck::FAILURE_BUSY:
      return os << "FAILURE_BUSY";
    case BluetoothAudioCtrlAck::FAILURE_DISCONNECTING:
      return os << "FAILURE_DISCONNECTING";
    case BluetoothAudioCtrlAck::FAILURE:
      return os << "FAILURE";
    default:
      return os << "UNDEFINED " << static_cast<int8_t>(ack);
  }
}

BluetoothAudioClientInterface::BluetoothAudioClientInterface(
    IBluetoothTransportInstance* instance)
    : provider_(nullptr),
      provider_factory_(nullptr),
      session_started_(false),
      data_mq_(nullptr),
      transport_(instance) {
  death_recipient_ = ::ndk::ScopedAIBinder_DeathRecipient(
      AIBinder_DeathRecipient_new(binderDiedCallbackAidl));
}

bool BluetoothAudioClientInterface::is_aidl_available() {
  return AServiceManager_isDeclared(
      kDefaultAudioProviderFactoryInterface.c_str());
}

std::vector<AudioCapabilities>
BluetoothAudioClientInterface::GetAudioCapabilities() const {
  return capabilities_;
}

std::vector<AudioCapabilities>
BluetoothAudioClientInterface::GetAudioCapabilities(SessionType session_type) {
  std::vector<AudioCapabilities> capabilities(0);
  if (!is_aidl_available()) {
    return capabilities;
  }
  auto provider_factory = IBluetoothAudioProviderFactory::fromBinder(
      ::ndk::SpAIBinder(AServiceManager_waitForService(
          kDefaultAudioProviderFactoryInterface.c_str())));

  if (provider_factory == nullptr) {
    LOG(ERROR) << __func__ << ", can't get capability from unknown factory";
    return capabilities;
  }

  auto aidl_retval =
      provider_factory->getProviderCapabilities(session_type, &capabilities);
  if (!aidl_retval.isOk()) {
    LOG(FATAL) << __func__
               << ": BluetoothAudioHal::getProviderCapabilities failure: "
               << aidl_retval.getDescription();
  }
  return capabilities;
}

void BluetoothAudioClientInterface::FetchAudioProvider() {
  if (!is_aidl_available()) {
    LOG(ERROR) << __func__ << ": aidl is not supported on this platform.";
    return;
  }
  if (provider_ != nullptr) {
    LOG(WARNING) << __func__ << ": refetch";
  }
  auto provider_factory = IBluetoothAudioProviderFactory::fromBinder(
      ::ndk::SpAIBinder(AServiceManager_waitForService(
          kDefaultAudioProviderFactoryInterface.c_str())));

  if (provider_factory == nullptr) {
    LOG(ERROR) << __func__ << ", can't get capability from unknown factory";
    return;
  }

  capabilities_.clear();
  auto aidl_retval = provider_factory->getProviderCapabilities(
      transport_->GetSessionType(), &capabilities_);
  if (!aidl_retval.isOk()) {
    LOG(FATAL) << __func__
               << ": BluetoothAudioHal::getProviderCapabilities failure: "
               << aidl_retval.getDescription();
    return;
  }
  if (capabilities_.empty()) {
    LOG(WARNING) << __func__
                 << ": SessionType=" << toString(transport_->GetSessionType())
                 << " Not supported by BluetoothAudioHal";
    return;
  }
  LOG(INFO) << __func__ << ": BluetoothAudioHal SessionType="
            << toString(transport_->GetSessionType()) << " has "
            << capabilities_.size() << " AudioCapabilities";

  aidl_retval =
      provider_factory->openProvider(transport_->GetSessionType(), &provider_);
  if (!aidl_retval.isOk()) {
    LOG(FATAL) << __func__ << ": BluetoothAudioHal::openProvider failure: "
               << aidl_retval.getDescription();
  }
  CHECK(provider_ != nullptr);

  binder_status_t binder_status = AIBinder_linkToDeath(
      provider_factory->asBinder().get(), death_recipient_.get(), this);
  if (binder_status != STATUS_OK) {
    LOG(ERROR) << "Failed to linkToDeath " << static_cast<int>(binder_status);
  }
  provider_factory_ = std::move(provider_factory);

  LOG(INFO) << "IBluetoothAudioProvidersFactory::openProvider() returned "
            << provider_.get()
            << (provider_->isRemote() ? " (remote)" : " (local)");
}

BluetoothAudioSinkClientInterface::BluetoothAudioSinkClientInterface(
    IBluetoothSinkTransportInstance* sink,
    bluetooth::common::MessageLoopThread* message_loop)
    : BluetoothAudioClientInterface{sink}, sink_(sink) {
  FetchAudioProvider();
}

BluetoothAudioSinkClientInterface::~BluetoothAudioSinkClientInterface() {
  if (provider_factory_ != nullptr) {
    AIBinder_unlinkToDeath(provider_factory_->asBinder().get(), death_recipient_.get(),
                           nullptr);
  }
}

BluetoothAudioSourceClientInterface::BluetoothAudioSourceClientInterface(
    IBluetoothSourceTransportInstance* source,
    bluetooth::common::MessageLoopThread* message_loop)
    : BluetoothAudioClientInterface{source}, source_(source) {
  FetchAudioProvider();
}

BluetoothAudioSourceClientInterface::~BluetoothAudioSourceClientInterface() {
  if (provider_factory_ != nullptr) {
    AIBinder_unlinkToDeath(provider_factory_->asBinder().get(), death_recipient_.get(),
                           nullptr);
  }
}

void BluetoothAudioClientInterface::binderDiedCallbackAidl(void* ptr) {
  LOG(WARNING) << __func__ << ": restarting connection with new Audio Hal";
  auto client = static_cast<BluetoothAudioClientInterface*>(ptr);
  if (client == nullptr) {
    LOG(ERROR) << __func__ << ": null audio HAL died!";
    return;
  }
  client->RenewAudioProviderAndSession();
}

bool BluetoothAudioClientInterface::UpdateAudioConfig(
    const AudioConfiguration& audio_config) {
  bool is_software_session =
      (transport_->GetSessionType() ==
           SessionType::A2DP_SOFTWARE_ENCODING_DATAPATH ||
       transport_->GetSessionType() ==
           SessionType::HEARING_AID_SOFTWARE_ENCODING_DATAPATH ||
       transport_->GetSessionType() ==
           SessionType::LE_AUDIO_SOFTWARE_ENCODING_DATAPATH ||
       transport_->GetSessionType() ==
           SessionType::LE_AUDIO_SOFTWARE_DECODING_DATAPATH ||
       transport_->GetSessionType() ==
           SessionType::LE_AUDIO_BROADCAST_SOFTWARE_ENCODING_DATAPATH);
  bool is_a2dp_offload_session =
      (transport_->GetSessionType() ==
       SessionType::A2DP_HARDWARE_OFFLOAD_ENCODING_DATAPATH);
  bool is_leaudio_unicast_offload_session =
      (transport_->GetSessionType() ==
           SessionType::LE_AUDIO_HARDWARE_OFFLOAD_ENCODING_DATAPATH ||
       transport_->GetSessionType() ==
           SessionType::LE_AUDIO_HARDWARE_OFFLOAD_DECODING_DATAPATH);
  bool is_leaudio_broadcast_offload_session =
      (transport_->GetSessionType() ==
       SessionType::LE_AUDIO_BROADCAST_HARDWARE_OFFLOAD_ENCODING_DATAPATH);
  auto audio_config_tag = audio_config.getTag();
  bool is_software_audio_config =
      (is_software_session &&
       audio_config_tag == AudioConfiguration::pcmConfig);
  bool is_a2dp_offload_audio_config =
      (is_a2dp_offload_session &&
       audio_config_tag == AudioConfiguration::a2dpConfig);
  bool is_leaudio_unicast_offload_audio_config =
      (is_leaudio_unicast_offload_session &&
       audio_config_tag == AudioConfiguration::leAudioConfig);
  bool is_leaudio_broadcast_offload_audio_config =
      (is_leaudio_broadcast_offload_session &&
       audio_config_tag == AudioConfiguration::leAudioBroadcastConfig);
  if (!is_software_audio_config && !is_a2dp_offload_audio_config &&
      !is_leaudio_unicast_offload_audio_config &&
      !is_leaudio_broadcast_offload_audio_config) {
    return false;
  }
  transport_->UpdateAudioConfiguration(audio_config);

  if (provider_ == nullptr) {
    LOG(INFO) << __func__
              << ": BluetoothAudioHal nullptr, update it as session started";
    return true;
  }

  auto aidl_retval = provider_->updateAudioConfiguration(audio_config);
  if (!aidl_retval.isOk()) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal failure: "
               << aidl_retval.getDescription();
  }
  return true;
}

bool BluetoothAudioClientInterface::SetLowLatencyModeAllowed(bool allowed) {
  is_low_latency_allowed_ = allowed;
  if (provider_ == nullptr) {
    LOG(INFO) << __func__ << ": BluetoothAudioHal nullptr";
    return false;
  }

  auto aidl_retval = provider_->setLowLatencyModeAllowed(allowed);
  if (!aidl_retval.isOk()) {
    LOG(WARNING) << __func__ << ": BluetoothAudioHal is not ready: "
               << aidl_retval.getDescription()
               << ". is_low_latency_allowed_ is saved "
               <<"and it will be sent to BluetoothAudioHal at StartSession.";
  }
  return true;
}

int BluetoothAudioClientInterface::StartSession() {
  std::lock_guard<std::mutex> guard(internal_mutex_);
  if (provider_ == nullptr) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal nullptr";
    session_started_ = false;
    return -EINVAL;
  }
  if (session_started_) {
    LOG(ERROR) << __func__ << ": session started already";
    return -EBUSY;
  }

  std::shared_ptr<IBluetoothAudioPort> stack_if =
      ndk::SharedRefBase::make<BluetoothAudioPortImpl>(transport_, provider_);

  std::unique_ptr<DataMQ> data_mq;
  DataMQDesc mq_desc;
  std::vector<LatencyMode> latency_modes = {LatencyMode::FREE};
  if (is_low_latency_allowed_) {
    latency_modes.push_back(LatencyMode::LOW_LATENCY);
  }
  auto aidl_retval = provider_->startSession(
      stack_if, transport_->GetAudioConfiguration(), latency_modes, &mq_desc);
  if (!aidl_retval.isOk()) {
    if (aidl_retval.getExceptionCode() == EX_ILLEGAL_ARGUMENT) {
      LOG(ERROR) << __func__ << ": BluetoothAudioHal Error: "
                 << aidl_retval.getDescription() << ", audioConfig="
                 << transport_->GetAudioConfiguration().toString();
    } else {
      LOG(FATAL) << __func__ << ": BluetoothAudioHal failure: "
                 << aidl_retval.getDescription();
    }
    return -EPROTO;
  }
  data_mq.reset(new DataMQ(mq_desc));

  if (data_mq && data_mq->isValid()) {
    data_mq_ = std::move(data_mq);
  } else if (transport_->GetSessionType() ==
                 SessionType::A2DP_HARDWARE_OFFLOAD_ENCODING_DATAPATH ||
             transport_->GetSessionType() ==
                 SessionType::LE_AUDIO_HARDWARE_OFFLOAD_DECODING_DATAPATH ||
             transport_->GetSessionType() ==
                 SessionType::LE_AUDIO_HARDWARE_OFFLOAD_ENCODING_DATAPATH ||
             transport_->GetSessionType() ==
                 SessionType::
                     LE_AUDIO_BROADCAST_HARDWARE_OFFLOAD_ENCODING_DATAPATH) {
    transport_->ResetPresentationPosition();
    session_started_ = true;
    return 0;
  }
  if (data_mq_ && data_mq_->isValid()) {
    transport_->ResetPresentationPosition();
    session_started_ = true;
    return 0;
  } else {
    if (!data_mq_) {
      LOG(ERROR) << __func__ << "Failed to obtain audio data path";
    }
    if (data_mq_ && !data_mq_->isValid()) {
      LOG(ERROR) << __func__ << "Audio data path is invalid";
    }
    session_started_ = false;
    return -EIO;
  }
}

void BluetoothAudioClientInterface::StreamStarted(
    const BluetoothAudioCtrlAck& ack) {
  if (provider_ == nullptr) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal nullptr";
    return;
  }
  if (ack == BluetoothAudioCtrlAck::PENDING) {
    LOG(INFO) << __func__ << ": " << ack << " ignored";
    return;
  }
  BluetoothAudioStatus status = BluetoothAudioCtrlAckToHalStatus(ack);

  auto aidl_retval = provider_->streamStarted(status);

  if (!aidl_retval.isOk()) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal failure: "
               << aidl_retval.getDescription();
  }
}

void BluetoothAudioClientInterface::StreamSuspended(
    const BluetoothAudioCtrlAck& ack) {
  if (provider_ == nullptr) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal nullptr";
    return;
  }
  if (ack == BluetoothAudioCtrlAck::PENDING) {
    LOG(INFO) << __func__ << ": " << ack << " ignored";
    return;
  }
  BluetoothAudioStatus status = BluetoothAudioCtrlAckToHalStatus(ack);

  auto aidl_retval = provider_->streamSuspended(status);

  if (!aidl_retval.isOk()) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal failure: "
               << aidl_retval.getDescription();
  }
}

int BluetoothAudioClientInterface::EndSession() {
  std::lock_guard<std::mutex> guard(internal_mutex_);
  if (!session_started_) {
    LOG(INFO) << __func__ << ": session ended already";
    return 0;
  }

  session_started_ = false;
  if (provider_ == nullptr) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal nullptr";
    return -EINVAL;
  }
  data_mq_ = nullptr;

  auto aidl_retval = provider_->endSession();

  if (!aidl_retval.isOk()) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal failure: "
               << aidl_retval.getDescription();
    return -EPROTO;
  }
  return 0;
}

void BluetoothAudioClientInterface::FlushAudioData() {
  if (transport_->GetSessionType() ==
          SessionType::LE_AUDIO_HARDWARE_OFFLOAD_ENCODING_DATAPATH ||
      transport_->GetSessionType() ==
          SessionType::LE_AUDIO_HARDWARE_OFFLOAD_DECODING_DATAPATH ||
      transport_->GetSessionType() ==
          SessionType::LE_AUDIO_BROADCAST_HARDWARE_OFFLOAD_ENCODING_DATAPATH) {
    return;
  }

  if (data_mq_ == nullptr || !data_mq_->isValid()) {
    LOG(WARNING) << __func__ << ", data_mq_ invalid";
    return;
  }
  size_t size = data_mq_->availableToRead();
  std::vector<MqDataType> buffer(size);

  if (data_mq_->read(buffer.data(), size) != size) {
    LOG(WARNING) << __func__ << ", failed to flush data queue!";
  }
}

size_t BluetoothAudioSinkClientInterface::ReadAudioData(uint8_t* p_buf,
                                                        uint32_t len) {
  if (!IsValid()) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal is not valid";
    return 0;
  }
  if (p_buf == nullptr || len == 0) return 0;

  std::lock_guard<std::mutex> guard(internal_mutex_);

  size_t total_read = 0;
  int timeout_ms = kDefaultDataReadTimeoutMs;
  do {
    if (data_mq_ == nullptr || !data_mq_->isValid()) break;

    size_t avail_to_read = data_mq_->availableToRead();
    if (avail_to_read) {
      if (avail_to_read > len - total_read) {
        avail_to_read = len - total_read;
      }
      if (data_mq_->read((MqDataType*)p_buf + total_read, avail_to_read) == 0) {
        LOG(WARNING) << __func__ << ": len=" << len
                     << " total_read=" << total_read << " failed";
        break;
      }
      total_read += avail_to_read;
    } else if (timeout_ms >= kDefaultDataReadPollIntervalMs) {
      std::this_thread::sleep_for(
          std::chrono::milliseconds(kDefaultDataReadPollIntervalMs));
      timeout_ms -= kDefaultDataReadPollIntervalMs;
      continue;
    } else {
      LOG(WARNING) << __func__ << ": " << (len - total_read) << "/" << len
                   << " no data " << (kDefaultDataReadTimeoutMs - timeout_ms)
                   << " ms";
      break;
    }
  } while (total_read < len);

  if (timeout_ms <
          (kDefaultDataReadTimeoutMs - kDefaultDataReadPollIntervalMs) &&
      timeout_ms >= kDefaultDataReadPollIntervalMs) {
    VLOG(1) << __func__ << ": underflow " << len << " -> " << total_read
            << " read " << (kDefaultDataReadTimeoutMs - timeout_ms) << " ms";
  } else {
    VLOG(2) << __func__ << ": " << len << " -> " << total_read << " read";
  }

  sink_->LogBytesRead(total_read);
  return total_read;
}

void BluetoothAudioClientInterface::RenewAudioProviderAndSession() {
  // NOTE: must be invoked on the same thread where this
  // BluetoothAudioClientInterface is running
  FetchAudioProvider();

  if (session_started_) {
    LOG(INFO) << __func__
              << ": Restart the session while audio HAL recovering ";
    session_started_ = false;

    StartSession();
  }
}

size_t BluetoothAudioSourceClientInterface::WriteAudioData(const uint8_t* p_buf,
                                                           uint32_t len) {
  if (!IsValid()) {
    LOG(ERROR) << __func__ << ": BluetoothAudioHal is not valid";
    return 0;
  }
  if (p_buf == nullptr || len == 0) return 0;

  std::lock_guard<std::mutex> guard(internal_mutex_);

  size_t total_written = 0;
  int timeout_ms = kDefaultDataWriteTimeoutMs;
  do {
    if (data_mq_ == nullptr || !data_mq_->isValid()) break;

    size_t avail_to_write = data_mq_->availableToWrite();
    if (avail_to_write) {
      if (avail_to_write > len - total_written) {
        avail_to_write = len - total_written;
      }
      if (data_mq_->write((const MqDataType*)p_buf + total_written,
                          avail_to_write) == 0) {
        LOG(WARNING) << __func__ << ": len=" << len
                     << " total_written=" << total_written << " failed";
        break;
      }
      total_written += avail_to_write;
    } else if (timeout_ms >= kDefaultDataWritePollIntervalMs) {
      std::this_thread::sleep_for(
          std::chrono::milliseconds(kDefaultDataWritePollIntervalMs));
      timeout_ms -= kDefaultDataWritePollIntervalMs;
      continue;
    } else {
      LOG(WARNING) << __func__ << ": " << (len - total_written) << "/" << len
                   << " no data " << (kDefaultDataWriteTimeoutMs - timeout_ms)
                   << " ms";
      break;
    }
  } while (total_written < len);

  if (timeout_ms <
          (kDefaultDataWriteTimeoutMs - kDefaultDataWritePollIntervalMs) &&
      timeout_ms >= kDefaultDataWritePollIntervalMs) {
    VLOG(1) << __func__ << ": underflow " << len << " -> " << total_written
            << " read " << (kDefaultDataWriteTimeoutMs - timeout_ms) << " ms ";
  } else {
    VLOG(2) << __func__ << ": " << len << " -> " << total_written
            << " written ";
  }

  source_->LogBytesWritten(total_written);
  return total_written;
}

}  // namespace aidl
}  // namespace audio
}  // namespace bluetooth

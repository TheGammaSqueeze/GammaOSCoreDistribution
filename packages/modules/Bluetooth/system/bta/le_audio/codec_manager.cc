/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "codec_manager.h"

#include "audio_hal_client/audio_hal_client.h"
#include "device/include/controller.h"
#include "le_audio_set_configuration_provider.h"
#include "osi/include/log.h"
#include "osi/include/properties.h"
#include "stack/acl/acl.h"
#include "stack/include/acl_api.h"

namespace {

using bluetooth::hci::iso_manager::kIsoDataPathHci;
using bluetooth::hci::iso_manager::kIsoDataPathPlatformDefault;
using le_audio::CodecManager;
using le_audio::types::CodecLocation;

using bluetooth::le_audio::btle_audio_codec_config_t;
using bluetooth::le_audio::btle_audio_codec_index_t;
using le_audio::AudioSetConfigurationProvider;
using le_audio::set_configurations::AudioSetConfiguration;
using le_audio::set_configurations::AudioSetConfigurations;
using le_audio::set_configurations::SetConfiguration;

}  // namespace

namespace le_audio {

struct codec_manager_impl {
 public:
  codec_manager_impl(
      const std::vector<btle_audio_codec_config_t>& offloading_preference) {
    offload_enable_ = osi_property_get_bool(
                          "ro.bluetooth.leaudio_offload.supported", false) &&
                      !osi_property_get_bool(
                          "persist.bluetooth.leaudio_offload.disabled", true);
    if (offload_enable_ == false) {
      LOG_INFO("offload disabled");
      return;
    }

    if (!LeAudioHalVerifier::SupportsLeAudioHardwareOffload()) {
      LOG_WARN("HAL not support hardware offload");
      return;
    }

    if (!controller_get_interface()->supports_configure_data_path()) {
      LOG_WARN("Controller does not support config data path command");
      return;
    }

    LOG_INFO("LeAudioCodecManagerImpl: configure_data_path for encode");
    btm_configure_data_path(btm_data_direction::HOST_TO_CONTROLLER,
                            kIsoDataPathPlatformDefault, {});
    btm_configure_data_path(btm_data_direction::CONTROLLER_TO_HOST,
                            kIsoDataPathPlatformDefault, {});
    UpdateOffloadCapability(offloading_preference);
    SetCodecLocation(CodecLocation::ADSP);
  }
  ~codec_manager_impl() {
    if (GetCodecLocation() != CodecLocation::HOST) {
      btm_configure_data_path(btm_data_direction::HOST_TO_CONTROLLER,
                              kIsoDataPathHci, {});
      btm_configure_data_path(btm_data_direction::CONTROLLER_TO_HOST,
                              kIsoDataPathHci, {});
    }
  }
  CodecLocation GetCodecLocation(void) const { return codec_location_; }

  void UpdateActiveSourceAudioConfig(
      const le_audio::stream_configuration& stream_conf, uint16_t delay_ms,
      std::function<void(const ::le_audio::offload_config& config)>
          update_receiver) {
    if (stream_conf.sink_streams.empty()) return;

    if (stream_conf.sink_is_initial) {
      sink_config.stream_map =
          stream_conf.sink_offloader_streams_target_allocation;
    } else {
      sink_config.stream_map =
          stream_conf.sink_offloader_streams_current_allocation;
    }
    // TODO: set the default value 16 for now, would change it if we support
    // mode bits_per_sample
    sink_config.bits_per_sample = 16;
    sink_config.sampling_rate = stream_conf.sink_sample_frequency_hz;
    sink_config.frame_duration = stream_conf.sink_frame_duration_us;
    sink_config.octets_per_frame = stream_conf.sink_octets_per_codec_frame;
    sink_config.blocks_per_sdu = stream_conf.sink_codec_frames_blocks_per_sdu;
    sink_config.peer_delay_ms = delay_ms;
    update_receiver(sink_config);
  }

  void UpdateActiveSinkAudioConfig(
      const le_audio::stream_configuration& stream_conf, uint16_t delay_ms,
      std::function<void(const ::le_audio::offload_config& config)>
          update_receiver) {
    if (stream_conf.source_streams.empty()) return;

    if (stream_conf.source_is_initial) {
      source_config.stream_map =
          stream_conf.source_offloader_streams_target_allocation;
    } else {
      source_config.stream_map =
          stream_conf.source_offloader_streams_current_allocation;
    }
    // TODO: set the default value 16 for now, would change it if we support
    // mode bits_per_sample
    source_config.bits_per_sample = 16;
    source_config.sampling_rate = stream_conf.source_sample_frequency_hz;
    source_config.frame_duration = stream_conf.source_frame_duration_us;
    source_config.octets_per_frame = stream_conf.source_octets_per_codec_frame;
    source_config.blocks_per_sdu =
        stream_conf.source_codec_frames_blocks_per_sdu;
    source_config.peer_delay_ms = delay_ms;
    update_receiver(source_config);
  }

  const AudioSetConfigurations* GetOffloadCodecConfig(
      types::LeAudioContextType ctx_type) {
    return &context_type_offload_config_map_[ctx_type];
  }

  const broadcast_offload_config* GetBroadcastOffloadConfig() {
    // TODO: Need to check the offload capabilities and audio policy further
    // Use 48_1_2 for the media quality as default by now.
    broadcast_config.stream_map.resize(
        LeAudioCodecConfiguration::kChannelNumberStereo);
    broadcast_config.bits_per_sample =
        LeAudioCodecConfiguration::kBitsPerSample16;
    broadcast_config.sampling_rate =
        LeAudioCodecConfiguration::kSampleRate48000;
    broadcast_config.frame_duration =
        LeAudioCodecConfiguration::kInterval7500Us;
    broadcast_config.octets_per_frame = 75;
    broadcast_config.blocks_per_sdu = 1;
    broadcast_config.codec_bitrate = 80000;
    broadcast_config.retransmission_number = 4;
    broadcast_config.max_transport_latency = 60;
    return &broadcast_config;
  }

  void UpdateBroadcastConnHandle(
      const std::vector<uint16_t>& conn_handle,
      std::function<void(const ::le_audio::broadcast_offload_config& config)>
          update_receiver) {
    LOG_ASSERT(conn_handle.size() == broadcast_config.stream_map.size());

    if (broadcast_config.stream_map.size() ==
        LeAudioCodecConfiguration::kChannelNumberStereo) {
      broadcast_config.stream_map[0] = std::pair<uint16_t, uint32_t>{
          conn_handle[0], codec_spec_conf::kLeAudioLocationFrontLeft};
      broadcast_config.stream_map[1] = std::pair<uint16_t, uint32_t>{
          conn_handle[1], codec_spec_conf::kLeAudioLocationFrontRight};
    } else if (broadcast_config.stream_map.size() ==
               LeAudioCodecConfiguration::kChannelNumberMono) {
      broadcast_config.stream_map[0] = std::pair<uint16_t, uint32_t>{
          conn_handle[0], codec_spec_conf::kLeAudioLocationFrontCenter};
    }

    update_receiver(broadcast_config);
  }

 private:
  void SetCodecLocation(CodecLocation location) {
    if (offload_enable_ == false) return;
    codec_location_ = location;
  }

  bool IsLc3ConfigMatched(
      const set_configurations::CodecCapabilitySetting& adsp_config,
      const set_configurations::CodecCapabilitySetting& target_config) {
    if (adsp_config.id.coding_format != types::kLeAudioCodingFormatLC3 ||
        target_config.id.coding_format != types::kLeAudioCodingFormatLC3) {
      return false;
    }

    const types::LeAudioLc3Config adsp_lc3_config =
        std::get<types::LeAudioLc3Config>(adsp_config.config);
    const types::LeAudioLc3Config target_lc3_config =
        std::get<types::LeAudioLc3Config>(target_config.config);

    if (adsp_lc3_config.sampling_frequency !=
            target_lc3_config.sampling_frequency ||
        adsp_lc3_config.frame_duration != target_lc3_config.frame_duration ||
        adsp_lc3_config.channel_count != target_lc3_config.channel_count ||
        adsp_lc3_config.octets_per_codec_frame !=
            target_lc3_config.octets_per_codec_frame) {
      return false;
    }

    return true;
  }

  bool IsSetConfigurationMatched(const SetConfiguration& software_set_config,
                                 const SetConfiguration& adsp_set_config) {
    // Skip the check of stategry and ase_cnt due to ADSP doesn't have the info
    return (
        software_set_config.direction == adsp_set_config.direction &&
        software_set_config.device_cnt == adsp_set_config.device_cnt &&
        IsLc3ConfigMatched(software_set_config.codec, adsp_set_config.codec));
  }

  bool IsAudioSetConfigurationMatched(
      const AudioSetConfiguration* software_audio_set_conf,
      std::unordered_set<uint8_t>& offload_preference_set,
      const std::vector<AudioSetConfiguration>& adsp_capabilities) {
    if (software_audio_set_conf->confs.empty()) {
      return false;
    }

    std::unordered_map<uint8_t, const SetConfiguration&>
        software_set_conf_direction_map;

    for (auto& software_set_conf : software_audio_set_conf->confs) {
      // Checks offload preference supports the codec
      if (offload_preference_set.find(
              software_set_conf.codec.id.coding_format) ==
          offload_preference_set.end()) {
        return false;
      }
      software_set_conf_direction_map.emplace(software_set_conf.direction,
                                              software_set_conf);
    }

    // Checks any of offload config matches the input audio set config
    for (const auto& adsp_audio_set_conf : adsp_capabilities) {
      if (adsp_audio_set_conf.confs.size() !=
          software_audio_set_conf->confs.size()) {
        continue;
      }

      size_t match_cnt = 0;

      for (auto& adsp_set_conf : adsp_audio_set_conf.confs) {
        auto it = software_set_conf_direction_map.find(adsp_set_conf.direction);

        if (it == software_set_conf_direction_map.end()) {
          continue;
        }

        if (IsSetConfigurationMatched(it->second, adsp_set_conf)) {
          match_cnt++;
        }
      }

      if (match_cnt == software_set_conf_direction_map.size()) {
        return true;
      }
    }

    return false;
  }

  void UpdateOffloadCapability(
      const std::vector<btle_audio_codec_config_t>& offloading_preference) {
    LOG(INFO) << __func__;
    std::unordered_set<uint8_t> offload_preference_set;

    if (AudioSetConfigurationProvider::Get() == nullptr) {
      LOG(ERROR) << __func__ << " Audio set configuration provider is not available.";
      return;
    }

    std::vector<::le_audio::set_configurations::AudioSetConfiguration>
        adsp_capabilities =
            ::bluetooth::audio::le_audio::get_offload_capabilities();

    for (auto codec : offloading_preference) {
      auto it = btle_audio_codec_type_map_.find(codec.codec_type);

      if (it != btle_audio_codec_type_map_.end()) {
        offload_preference_set.insert(it->second);
      }
    }

    for (types::LeAudioContextType ctx_type :
         types::kLeAudioContextAllTypesArray) {
      // Gets the software supported context type and the corresponding config
      // priority
      const AudioSetConfigurations* software_audio_set_confs =
          AudioSetConfigurationProvider::Get()->GetConfigurations(ctx_type);

      for (const auto& software_audio_set_conf : *software_audio_set_confs) {
        if (IsAudioSetConfigurationMatched(software_audio_set_conf,
                                           offload_preference_set,
                                           adsp_capabilities)) {
          LOG(INFO) << "Offload supported conf, context type: " << (int)ctx_type
                    << ", settings -> " << software_audio_set_conf->name;
          context_type_offload_config_map_[ctx_type].push_back(
              software_audio_set_conf);
        }
      }
    }
  }

  CodecLocation codec_location_ = CodecLocation::HOST;
  bool offload_enable_ = false;
  le_audio::offload_config sink_config;
  le_audio::offload_config source_config;
  le_audio::broadcast_offload_config broadcast_config;
  std::unordered_map<types::LeAudioContextType, AudioSetConfigurations>
      context_type_offload_config_map_;
  std::unordered_map<btle_audio_codec_index_t, uint8_t>
      btle_audio_codec_type_map_ = {
          {::bluetooth::le_audio::LE_AUDIO_CODEC_INDEX_SOURCE_LC3,
           types::kLeAudioCodingFormatLC3}};
};

struct CodecManager::impl {
  impl(const CodecManager& codec_manager) : codec_manager_(codec_manager) {}

  void Start(
      const std::vector<btle_audio_codec_config_t>& offloading_preference) {
    LOG_ASSERT(!codec_manager_impl_);
    codec_manager_impl_ =
        std::make_unique<codec_manager_impl>(offloading_preference);
  }

  void Stop() {
    LOG_ASSERT(codec_manager_impl_);
    codec_manager_impl_.reset();
  }

  bool IsRunning() { return codec_manager_impl_ ? true : false; }

  const CodecManager& codec_manager_;
  std::unique_ptr<codec_manager_impl> codec_manager_impl_;
};

CodecManager::CodecManager() : pimpl_(std::make_unique<impl>(*this)) {}

void CodecManager::Start(
    const std::vector<btle_audio_codec_config_t>& offloading_preference) {
  if (!pimpl_->IsRunning()) pimpl_->Start(offloading_preference);
}

void CodecManager::Stop() {
  if (pimpl_->IsRunning()) pimpl_->Stop();
}

types::CodecLocation CodecManager::GetCodecLocation(void) const {
  if (!pimpl_->IsRunning()) {
    return CodecLocation::HOST;
  }

  return pimpl_->codec_manager_impl_->GetCodecLocation();
}

void CodecManager::UpdateActiveSourceAudioConfig(
    const stream_configuration& stream_conf, uint16_t delay_ms,
    std::function<void(const ::le_audio::offload_config& config)>
        update_receiver) {
  if (pimpl_->IsRunning())
    pimpl_->codec_manager_impl_->UpdateActiveSourceAudioConfig(
        stream_conf, delay_ms, update_receiver);
}

void CodecManager::UpdateActiveSinkAudioConfig(
    const stream_configuration& stream_conf, uint16_t delay_ms,
    std::function<void(const ::le_audio::offload_config& config)>
        update_receiver) {
  if (pimpl_->IsRunning())
    pimpl_->codec_manager_impl_->UpdateActiveSinkAudioConfig(
        stream_conf, delay_ms, update_receiver);
}

const AudioSetConfigurations* CodecManager::GetOffloadCodecConfig(
    types::LeAudioContextType ctx_type) {
  if (pimpl_->IsRunning()) {
    return pimpl_->codec_manager_impl_->GetOffloadCodecConfig(ctx_type);
  }

  return nullptr;
}

const ::le_audio::broadcast_offload_config*
CodecManager::GetBroadcastOffloadConfig() {
  if (pimpl_->IsRunning()) {
    return pimpl_->codec_manager_impl_->GetBroadcastOffloadConfig();
  }

  return nullptr;
}

void CodecManager::UpdateBroadcastConnHandle(
    const std::vector<uint16_t>& conn_handle,
    std::function<void(const ::le_audio::broadcast_offload_config& config)>
        update_receiver) {
  if (pimpl_->IsRunning()) {
    return pimpl_->codec_manager_impl_->UpdateBroadcastConnHandle(
        conn_handle, update_receiver);
  }
}

}  // namespace le_audio

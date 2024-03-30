/*
 *  Copyright (c) 2022 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

#include <string>
#include <string_view>

#include "audio_set_configurations_generated.h"
#include "audio_set_scenarios_generated.h"
#include "codec_manager.h"
#include "flatbuffers/idl.h"
#include "flatbuffers/util.h"
#include "le_audio_set_configuration_provider.h"
#include "osi/include/log.h"
#include "osi/include/osi.h"

using le_audio::set_configurations::AudioSetConfiguration;
using le_audio::set_configurations::AudioSetConfigurations;
using le_audio::set_configurations::CodecCapabilitySetting;
using le_audio::set_configurations::LeAudioCodecIdLc3;
using le_audio::set_configurations::QosConfigSetting;
using le_audio::set_configurations::SetConfiguration;
using le_audio::types::LeAudioContextType;

namespace le_audio {
using ::le_audio::CodecManager;

#ifdef OS_ANDROID
static const std::vector<
    std::pair<const char* /*schema*/, const char* /*content*/>>
    kLeAudioSetConfigs = {
        {"/apex/com.android.btservices/etc/bluetooth/le_audio/"
         "audio_set_configurations.bfbs",
         "/apex/com.android.btservices/etc/bluetooth/le_audio/"
         "audio_set_configurations.json"}};
static const std::vector<
    std::pair<const char* /*schema*/, const char* /*content*/>>
    kLeAudioSetScenarios = {{"/apex/com.android.btservices/etc/bluetooth/"
                             "le_audio/audio_set_scenarios.bfbs",
                             "/apex/com.android.btservices/etc/bluetooth/"
                             "le_audio/audio_set_scenarios.json"}};
#else
static const std::vector<
    std::pair<const char* /*schema*/, const char* /*content*/>>
    kLeAudioSetConfigs = {
        {"audio_set_configurations.bfbs", "audio_set_configurations.json"}};
static const std::vector<
    std::pair<const char* /*schema*/, const char* /*content*/>>
    kLeAudioSetScenarios = {
        {"audio_set_scenarios.bfbs", "audio_set_scenarios.json"}};
#endif

/** Provides a set configurations for the given context type */
struct AudioSetConfigurationProviderJson {
  static constexpr auto kDefaultScenario = "Media";

  AudioSetConfigurationProviderJson() {
    ASSERT_LOG(LoadContent(kLeAudioSetConfigs, kLeAudioSetScenarios),
               ": Unable to load le audio set configuration files.");
  }

  /* Use the same scenario configurations for different contexts to avoid
   * internal reconfiguration and handover that produces time gap. When using
   * the same scenario for different contexts, quality and configuration remains
   * the same while changing to same scenario based context type.
   */
  static auto ScenarioToContextTypes(const std::string& scenario) {
    static const std::multimap<std::string,
                               ::le_audio::types::LeAudioContextType>
        scenarios = {
            {"Media", types::LeAudioContextType::ALERTS},
            {"Media", types::LeAudioContextType::INSTRUCTIONAL},
            {"Media", types::LeAudioContextType::NOTIFICATIONS},
            {"Media", types::LeAudioContextType::EMERGENCYALARM},
            {"Media", types::LeAudioContextType::UNSPECIFIED},
            {"Media", types::LeAudioContextType::MEDIA},
            {"Conversational", types::LeAudioContextType::RINGTONE},
            {"Conversational", types::LeAudioContextType::CONVERSATIONAL},
            {"Live", types::LeAudioContextType::LIVE},
            {"Game", types::LeAudioContextType::GAME},
            {"VoiceAssistants", types::LeAudioContextType::VOICEASSISTANTS},
        };
    return scenarios.equal_range(scenario);
  }

  static std::string ContextTypeToScenario(
      ::le_audio::types::LeAudioContextType context_type) {
    switch (context_type) {
      case types::LeAudioContextType::ALERTS:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::INSTRUCTIONAL:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::NOTIFICATIONS:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::EMERGENCYALARM:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::UNSPECIFIED:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::SOUNDEFFECTS:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::MEDIA:
        return "Media";
      case types::LeAudioContextType::RINGTONE:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::CONVERSATIONAL:
        return "Conversational";
      case types::LeAudioContextType::LIVE:
        return "Live";
      case types::LeAudioContextType::GAME:
        return "Game";
      case types::LeAudioContextType::VOICEASSISTANTS:
        return "VoiceAssistants";
      default:
        return kDefaultScenario;
    }
  }

  const AudioSetConfigurations* GetConfigurationsByContextType(
      LeAudioContextType context_type) const {
    if (context_configurations_.count(context_type))
      return &context_configurations_.at(context_type);

    LOG_WARN(": No predefined scenario for the context %d was found.",
             (int)context_type);

    auto [it_begin, it_end] = ScenarioToContextTypes(kDefaultScenario);
    if (it_begin != it_end) {
      LOG_WARN(": Using '%s' scenario by default.", kDefaultScenario);
      return &context_configurations_.at(it_begin->second);
    }

    LOG_ERROR(
        ": No valid configuration for the default '%s' scenario, or no audio "
        "set configurations loaded at all.",
        kDefaultScenario);
    return nullptr;
  };

 private:
  /* Codec configurations */
  std::map<std::string, const AudioSetConfiguration> configurations_;

  /* Maps of context types to a set of configuration structs */
  std::map<::le_audio::types::LeAudioContextType, AudioSetConfigurations>
      context_configurations_;

  static const bluetooth::le_audio::CodecSpecificConfiguration*
  LookupCodecSpecificParam(
      const flatbuffers::Vector<
          flatbuffers::Offset<bluetooth::le_audio::CodecSpecificConfiguration>>*
          flat_codec_specific_params,
      bluetooth::le_audio::CodecSpecificLtvGenericTypes type) {
    auto it = std::find_if(
        flat_codec_specific_params->cbegin(),
        flat_codec_specific_params->cend(),
        [&type](const auto& csc) { return (csc->type() == type); });
    return (it != flat_codec_specific_params->cend()) ? *it : nullptr;
  }

  static CodecCapabilitySetting CodecCapabilitySettingFromFlat(
      const bluetooth::le_audio::CodecId* flat_codec_id,
      const flatbuffers::Vector<
          flatbuffers::Offset<bluetooth::le_audio::CodecSpecificConfiguration>>*
          flat_codec_specific_params) {
    CodecCapabilitySetting codec;

    /* Cache the le_audio::types::CodecId type value */
    codec.id = types::LeAudioCodecId({
        .coding_format = flat_codec_id->coding_format(),
        .vendor_company_id = flat_codec_id->vendor_company_id(),
        .vendor_codec_id = flat_codec_id->vendor_codec_id(),
    });

    /* Cache the types::LeAudioLc3Config type value */
    uint8_t sampling_frequency = 0;
    uint8_t frame_duration = 0;
    uint32_t audio_channel_allocation = 0;
    uint16_t octets_per_codec_frame = 0;
    uint8_t codec_frames_blocks_per_sdu = 0;

    auto param = LookupCodecSpecificParam(
        flat_codec_specific_params,
        bluetooth::le_audio::
            CodecSpecificLtvGenericTypes_SUPPORTED_SAMPLING_FREQUENCY);
    if (param) {
      ASSERT_LOG((param->compound_value()->value()->size() == 1),
                 " Invalid compound value length: %d",
                 param->compound_value()->value()->size());
      auto ptr = param->compound_value()->value()->data();
      STREAM_TO_UINT8(sampling_frequency, ptr);
    }

    param = LookupCodecSpecificParam(
        flat_codec_specific_params,
        bluetooth::le_audio::
            CodecSpecificLtvGenericTypes_SUPPORTED_FRAME_DURATION);
    if (param) {
      LOG_ASSERT(param->compound_value()->value()->size() == 1)
          << " Invalid compound value length: "
          << param->compound_value()->value()->size();
      auto ptr = param->compound_value()->value()->data();
      STREAM_TO_UINT8(frame_duration, ptr);
    }

    param = LookupCodecSpecificParam(
        flat_codec_specific_params,
        bluetooth::le_audio::
            CodecSpecificLtvGenericTypes_SUPPORTED_AUDIO_CHANNEL_ALLOCATION);
    if (param) {
      ASSERT_LOG((param->compound_value()->value()->size() == 4),
                 " Invalid compound value length %d",
                 param->compound_value()->value()->size());
      auto ptr = param->compound_value()->value()->data();
      STREAM_TO_UINT32(audio_channel_allocation, ptr);
    }

    param = LookupCodecSpecificParam(
        flat_codec_specific_params,
        bluetooth::le_audio::
            CodecSpecificLtvGenericTypes_SUPPORTED_OCTETS_PER_CODEC_FRAME);
    if (param) {
      ASSERT_LOG((param->compound_value()->value()->size() == 2),
                 " Invalid compound value length %d",
                 param->compound_value()->value()->size());
      auto ptr = param->compound_value()->value()->data();
      STREAM_TO_UINT16(octets_per_codec_frame, ptr);
    }

    param = LookupCodecSpecificParam(
        flat_codec_specific_params,
        bluetooth::le_audio::
            CodecSpecificLtvGenericTypes_SUPPORTED_CODEC_FRAME_BLOCKS_PER_SDU);
    if (param) {
      ASSERT_LOG((param->compound_value()->value()->size() == 1),
                 " Invalid compound value length %d",
                 param->compound_value()->value()->size());
      auto ptr = param->compound_value()->value()->data();
      STREAM_TO_UINT8(codec_frames_blocks_per_sdu, ptr);
    }

    codec.config = types::LeAudioLc3Config({
        .sampling_frequency = sampling_frequency,
        .frame_duration = frame_duration,
        .octets_per_codec_frame = octets_per_codec_frame,
        .codec_frames_blocks_per_sdu = codec_frames_blocks_per_sdu,
        .channel_count =
            (uint8_t)std::bitset<32>(audio_channel_allocation).count(),
        .audio_channel_allocation = audio_channel_allocation,
    });
    return codec;
  }

  SetConfiguration SetConfigurationFromFlatSubconfig(
      const bluetooth::le_audio::AudioSetSubConfiguration* flat_subconfig,
      QosConfigSetting qos) {
    auto strategy_int =
        static_cast<int>(flat_subconfig->configuration_strategy());

    bool valid_strategy =
        (strategy_int >=
         (int)types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE) &&
        strategy_int < (int)types::LeAudioConfigurationStrategy::RFU;

    types::LeAudioConfigurationStrategy strategy =
        valid_strategy
            ? static_cast<types::LeAudioConfigurationStrategy>(strategy_int)
            : types::LeAudioConfigurationStrategy::RFU;

    auto target_latency_int =
        static_cast<int>(flat_subconfig->target_latency());

    bool valid_target_latency =
        (target_latency_int >= (int)types::kTargetLatencyLower &&
         target_latency_int <= (int)types::kTargetLatencyHigherReliability);

    uint8_t target_latency =
        valid_target_latency ? static_cast<uint8_t>(target_latency_int)
                             : types::kTargetLatencyBalancedLatencyReliability;
    return SetConfiguration(
        flat_subconfig->direction(), flat_subconfig->device_cnt(),
        flat_subconfig->ase_cnt(), target_latency,
        CodecCapabilitySettingFromFlat(flat_subconfig->codec_id(),
                                       flat_subconfig->codec_configuration()),
        qos, strategy);
  }

  AudioSetConfiguration AudioSetConfigurationFromFlat(
      const bluetooth::le_audio::AudioSetConfiguration* flat_cfg,
      std::vector<const bluetooth::le_audio::CodecConfiguration*>* codec_cfgs,
      std::vector<const bluetooth::le_audio::QosConfiguration*>* qos_cfgs) {
    ASSERT_LOG(flat_cfg != nullptr, "flat_cfg cannot be null");
    std::string codec_config_key = flat_cfg->codec_config_name()->str();
    auto* qos_config_key_array = flat_cfg->qos_config_name();

    constexpr std::string_view default_qos = "QoS_Config_Server_Preferred";

    std::string qos_sink_key(default_qos);
    std::string qos_source_key(default_qos);

    /* We expect maximum two QoS settings. First for Sink and second for Source
     */
    if (qos_config_key_array->size() > 0) {
      qos_sink_key = qos_config_key_array->Get(0)->str();
      if (qos_config_key_array->size() > 1) {
        qos_source_key = qos_config_key_array->Get(1)->str();
      } else {
        qos_source_key = qos_sink_key;
      }
    }

    LOG_INFO("Config name %s, qos_sink %s, qos_source %s",
             codec_config_key.c_str(), qos_sink_key.c_str(),
             qos_source_key.c_str());

    const bluetooth::le_audio::QosConfiguration* qos_sink_cfg = nullptr;
    for (auto i = qos_cfgs->begin(); i != qos_cfgs->end(); ++i) {
      if ((*i)->name()->str() == qos_sink_key) {
        qos_sink_cfg = *i;
        break;
      }
    }

    const bluetooth::le_audio::QosConfiguration* qos_source_cfg = nullptr;
    for (auto i = qos_cfgs->begin(); i != qos_cfgs->end(); ++i) {
      if ((*i)->name()->str() == qos_source_key) {
        qos_source_cfg = *i;
        break;
      }
    }

    QosConfigSetting qos_sink;
    if (qos_sink_cfg != nullptr) {
      qos_sink.retransmission_number = qos_sink_cfg->retransmission_number();
      qos_sink.max_transport_latency = qos_sink_cfg->max_transport_latency();
    } else {
      LOG_ERROR("No qos config matching key %s found", qos_sink_key.c_str());
    }

    QosConfigSetting qos_source;
    if (qos_source_cfg != nullptr) {
      qos_source.retransmission_number =
          qos_source_cfg->retransmission_number();
      qos_source.max_transport_latency =
          qos_source_cfg->max_transport_latency();
    } else {
      LOG_ERROR("No qos config matching key %s found", qos_source_key.c_str());
    }

    const bluetooth::le_audio::CodecConfiguration* codec_cfg = nullptr;
    for (auto i = codec_cfgs->begin(); i != codec_cfgs->end(); ++i) {
      if ((*i)->name()->str() == codec_config_key) {
        codec_cfg = *i;
        break;
      }
    }

    std::vector<SetConfiguration> subconfigs;
    if (codec_cfg != nullptr && codec_cfg->subconfigurations()) {
      /* Load subconfigurations */
      for (auto subconfig : *codec_cfg->subconfigurations()) {
        if (subconfig->direction() == le_audio::types::kLeAudioDirectionSink) {
          subconfigs.push_back(
              SetConfigurationFromFlatSubconfig(subconfig, qos_sink));
        } else {
          subconfigs.push_back(
              SetConfigurationFromFlatSubconfig(subconfig, qos_source));
        }
      }
    } else {
      if (codec_cfg == nullptr) {
        LOG_ERROR("No codec config matching key %s found",
                  codec_config_key.c_str());
      } else {
        LOG_ERROR("Configuration '%s' has no valid subconfigurations.",
                  flat_cfg->name()->c_str());
      }
    }

    return AudioSetConfiguration({flat_cfg->name()->c_str(), subconfigs});
  }

  bool LoadConfigurationsFromFiles(const char* schema_file,
                                   const char* content_file) {
    flatbuffers::Parser configurations_parser_;
    std::string configurations_schema_binary_content;
    bool ok = flatbuffers::LoadFile(schema_file, true,
                                    &configurations_schema_binary_content);
    if (!ok) return ok;

    /* Load the binary schema */
    ok = configurations_parser_.Deserialize(
        (uint8_t*)configurations_schema_binary_content.c_str(),
        configurations_schema_binary_content.length());
    if (!ok) return ok;

    /* Load the content from JSON */
    std::string configurations_json_content;
    ok = flatbuffers::LoadFile(content_file, false,
                               &configurations_json_content);
    if (!ok) return ok;

    /* Parse */
    ok = configurations_parser_.Parse(configurations_json_content.c_str());
    if (!ok) return ok;

    /* Import from flatbuffers */
    auto configurations_root = bluetooth::le_audio::GetAudioSetConfigurations(
        configurations_parser_.builder_.GetBufferPointer());
    if (!configurations_root) return false;

    auto flat_qos_configs = configurations_root->qos_configurations();
    if ((flat_qos_configs == nullptr) || (flat_qos_configs->size() == 0))
      return false;

    LOG_DEBUG(": Updating %d qos config entries.", flat_qos_configs->size());
    std::vector<const bluetooth::le_audio::QosConfiguration*> qos_cfgs;
    for (auto const& flat_qos_cfg : *flat_qos_configs) {
      qos_cfgs.push_back(flat_qos_cfg);
    }

    auto flat_codec_configs = configurations_root->codec_configurations();
    if ((flat_codec_configs == nullptr) || (flat_codec_configs->size() == 0))
      return false;

    LOG_DEBUG(": Updating %d codec config entries.",
              flat_codec_configs->size());
    std::vector<const bluetooth::le_audio::CodecConfiguration*> codec_cfgs;
    for (auto const& flat_codec_cfg : *flat_codec_configs) {
      codec_cfgs.push_back(flat_codec_cfg);
    }

    auto flat_configs = configurations_root->configurations();
    if ((flat_configs == nullptr) || (flat_configs->size() == 0)) return false;

    LOG_DEBUG(": Updating %d config entries.", flat_configs->size());
    for (auto const& flat_cfg : *flat_configs) {
      configurations_.insert(
          {flat_cfg->name()->str(),
           AudioSetConfigurationFromFlat(flat_cfg, &codec_cfgs, &qos_cfgs)});
    }

    return true;
  }

  AudioSetConfigurations AudioSetConfigurationsFromFlatScenario(
      const bluetooth::le_audio::AudioSetScenario* const flat_scenario) {
    AudioSetConfigurations items;
    if (!flat_scenario->configurations()) return items;

    for (auto config_name : *flat_scenario->configurations()) {
      if (configurations_.count(config_name->str()) == 0) continue;

      auto& cfg = configurations_.at(config_name->str());
      items.push_back(&cfg);
    }

    return items;
  }

  bool LoadScenariosFromFiles(const char* schema_file,
                              const char* content_file) {
    flatbuffers::Parser scenarios_parser_;
    std::string scenarios_schema_binary_content;
    bool ok = flatbuffers::LoadFile(schema_file, true,
                                    &scenarios_schema_binary_content);
    if (!ok) return ok;

    /* Load the binary schema */
    ok = scenarios_parser_.Deserialize(
        (uint8_t*)scenarios_schema_binary_content.c_str(),
        scenarios_schema_binary_content.length());
    if (!ok) return ok;

    /* Load the content from JSON */
    std::string scenarios_json_content;
    ok = flatbuffers::LoadFile(content_file, false, &scenarios_json_content);
    if (!ok) return ok;

    /* Parse */
    ok = scenarios_parser_.Parse(scenarios_json_content.c_str());
    if (!ok) return ok;

    /* Import from flatbuffers */
    auto scenarios_root = bluetooth::le_audio::GetAudioSetScenarios(
        scenarios_parser_.builder_.GetBufferPointer());
    if (!scenarios_root) return false;

    auto flat_scenarios = scenarios_root->scenarios();
    if ((flat_scenarios == nullptr) || (flat_scenarios->size() == 0))
      return false;

    LOG_DEBUG(": Updating %d scenarios.", flat_scenarios->size());
    for (auto const& scenario : *flat_scenarios) {
      auto [it_begin, it_end] =
          ScenarioToContextTypes(scenario->name()->c_str());
      for (auto it = it_begin; it != it_end; ++it) {
        context_configurations_.insert_or_assign(
            it->second, AudioSetConfigurationsFromFlatScenario(scenario));
      }
    }

    return true;
  }

  bool LoadContent(
      std::vector<std::pair<const char* /*schema*/, const char* /*content*/>>
          config_files,
      std::vector<std::pair<const char* /*schema*/, const char* /*content*/>>
          scenario_files) {
    for (auto [schema, content] : config_files) {
      if (!LoadConfigurationsFromFiles(schema, content)) return false;
    }

    for (auto [schema, content] : scenario_files) {
      if (!LoadScenariosFromFiles(schema, content)) return false;
    }
    return true;
  }
};

struct AudioSetConfigurationProvider::impl {
  impl(const AudioSetConfigurationProvider& config_provider)
      : config_provider_(config_provider) {}

  void Initialize() {
    ASSERT_LOG(!config_provider_impl_, " Config provider not available.");
    config_provider_impl_ =
        std::make_unique<AudioSetConfigurationProviderJson>();
  }

  void Cleanup() {
    ASSERT_LOG(config_provider_impl_, " Config provider not available.");
    config_provider_impl_.reset();
  }

  bool IsRunning() { return config_provider_impl_ ? true : false; }

  void Dump(int fd) {
    std::stringstream stream;

    for (LeAudioContextType context : types::kLeAudioContextAllTypesArray) {
      auto confs = Get()->GetConfigurations(context);
      stream << "\n  === Configurations for context type: " << (int)context
             << ", num: " << (confs == nullptr ? 0 : confs->size()) << " \n";
      if (confs && confs->size() > 0) {
        for (const auto& conf : *confs) {
          stream << "  name: " << conf->name << " \n";
          for (const auto& ent : conf->confs) {
            stream << "    direction: "
                   << (ent.direction == types::kLeAudioDirectionSink
                           ? "Sink (speaker)\n"
                           : "Source (mic)\n")
                   << "     number of devices: " << +ent.device_cnt << " \n"
                   << "     number of ASEs: " << +ent.ase_cnt << " \n"
                   << "     target latency: " << +ent.target_latency << " \n"
                   << "     strategy: " << (int)(ent.strategy) << " \n"
                   << "     qos->retransmission_number: "
                   << +ent.qos.retransmission_number << " \n"
                   << "     qos->max_transport_latency: "
                   << +ent.qos.max_transport_latency << " \n"
                   << "     channel count: "
                   << +ent.codec.GetConfigChannelCount() << "\n";
          }
        }
      }
    }
    dprintf(fd, "%s", stream.str().c_str());
  }

  const AudioSetConfigurationProvider& config_provider_;
  std::unique_ptr<AudioSetConfigurationProviderJson> config_provider_impl_;
};

static std::unique_ptr<AudioSetConfigurationProvider> config_provider;

AudioSetConfigurationProvider::AudioSetConfigurationProvider()
    : pimpl_(std::make_unique<AudioSetConfigurationProvider::impl>(*this)) {}

void AudioSetConfigurationProvider::Initialize() {
  if (!config_provider)
    config_provider = std::make_unique<AudioSetConfigurationProvider>();

  if (!config_provider->pimpl_->IsRunning())
    config_provider->pimpl_->Initialize();
}

void AudioSetConfigurationProvider::DebugDump(int fd) {
  if (!config_provider || !config_provider->pimpl_->IsRunning()) {
    dprintf(
        fd,
        "\n AudioSetConfigurationProvider not initialized: config provider: "
        "%d, pimpl: %d \n",
        config_provider != nullptr,
        (config_provider == nullptr ? 0
                                    : config_provider->pimpl_->IsRunning()));
    return;
  }
  dprintf(fd, "\n AudioSetConfigurationProvider: \n");
  config_provider->pimpl_->Dump(fd);
}

void AudioSetConfigurationProvider::Cleanup() {
  if (!config_provider) return;
  if (config_provider->pimpl_->IsRunning()) config_provider->pimpl_->Cleanup();
  config_provider.reset();
}

AudioSetConfigurationProvider* AudioSetConfigurationProvider::Get() {
  return config_provider.get();
}

const set_configurations::AudioSetConfigurations*
AudioSetConfigurationProvider::GetConfigurations(
    ::le_audio::types::LeAudioContextType content_type) const {
  if (CodecManager::GetInstance()->GetCodecLocation() ==
      types::CodecLocation::ADSP) {
    LOG_DEBUG("Get offload config for the context type: %d", (int)content_type);
    const AudioSetConfigurations* offload_confs =
        CodecManager::GetInstance()->GetOffloadCodecConfig(content_type);

    if (offload_confs != nullptr && !(*offload_confs).empty()) {
      return offload_confs;
    }

    // TODO: Need to have a mechanism to switch to software session if offload
    // doesn't support.
  }

  LOG_DEBUG("Get software config for the context type: %d", (int)content_type);

  if (pimpl_->IsRunning())
    return pimpl_->config_provider_impl_->GetConfigurationsByContextType(
        content_type);

  return nullptr;
}

}  // namespace le_audio

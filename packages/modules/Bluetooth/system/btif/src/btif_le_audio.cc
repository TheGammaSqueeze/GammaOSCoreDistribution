/*
 * Copyright 2019 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
 * www.ehima.com
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

#include <base/logging.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_le_audio.h>

#include <vector>

#include "bta_le_audio_api.h"
#include "btif_common.h"
#include "btif_storage.h"
#include "stack/include/btu.h"

using base::Bind;
using base::Unretained;
using bluetooth::le_audio::btle_audio_codec_config_t;
using bluetooth::le_audio::ConnectionState;
using bluetooth::le_audio::GroupNodeStatus;
using bluetooth::le_audio::GroupStatus;
using bluetooth::le_audio::LeAudioClientCallbacks;
using bluetooth::le_audio::LeAudioClientInterface;

namespace {
class LeAudioClientInterfaceImpl;
std::unique_ptr<LeAudioClientInterface> leAudioInstance;

class LeAudioClientInterfaceImpl : public LeAudioClientInterface,
                                   public LeAudioClientCallbacks {
  ~LeAudioClientInterfaceImpl() = default;

  void OnInitialized(void) {
    do_in_jni_thread(FROM_HERE, Bind(&LeAudioClientCallbacks::OnInitialized,
                                     Unretained(callbacks)));
  }

  void OnConnectionState(ConnectionState state,
                         const RawAddress& address) override {
    do_in_jni_thread(FROM_HERE, Bind(&LeAudioClientCallbacks::OnConnectionState,
                                     Unretained(callbacks), state, address));
  }

  void OnGroupStatus(int group_id, GroupStatus group_status) override {
    do_in_jni_thread(FROM_HERE,
                     Bind(&LeAudioClientCallbacks::OnGroupStatus,
                          Unretained(callbacks), group_id, group_status));
  }

  void OnGroupNodeStatus(const RawAddress& addr, int group_id,
                         GroupNodeStatus node_status) override {
    do_in_jni_thread(FROM_HERE,
                     Bind(&LeAudioClientCallbacks::OnGroupNodeStatus,
                          Unretained(callbacks), addr, group_id, node_status));
  }

  void OnAudioConf(uint8_t direction, int group_id, uint32_t snk_audio_location,
                   uint32_t src_audio_location, uint16_t avail_cont) override {
    do_in_jni_thread(FROM_HERE,
                     Bind(&LeAudioClientCallbacks::OnAudioConf,
                          Unretained(callbacks), direction, group_id,
                          snk_audio_location, src_audio_location, avail_cont));
  }

  void OnSinkAudioLocationAvailable(const RawAddress& address,
                                    uint32_t snk_audio_location) override {
    do_in_jni_thread(FROM_HERE,
                     Bind(&LeAudioClientCallbacks::OnSinkAudioLocationAvailable,
                          Unretained(callbacks), address, snk_audio_location));
  }

  void OnAudioLocalCodecCapabilities(
      std::vector<btle_audio_codec_config_t> local_input_capa_codec_conf,
      std::vector<btle_audio_codec_config_t> local_output_capa_codec_conf)
      override {
    do_in_jni_thread(
        FROM_HERE, Bind(&LeAudioClientCallbacks::OnAudioLocalCodecCapabilities,
                        Unretained(callbacks), local_input_capa_codec_conf,
                        local_output_capa_codec_conf));
  }

  void OnAudioGroupCodecConf(
      int group_id, btle_audio_codec_config_t input_codec_conf,
      btle_audio_codec_config_t output_codec_conf,
      std::vector<btle_audio_codec_config_t> input_selectable_codec_conf,
      std::vector<btle_audio_codec_config_t> output_selectable_codec_conf)
      override {
    do_in_jni_thread(FROM_HERE,
                     Bind(&LeAudioClientCallbacks::OnAudioGroupCodecConf,
                          Unretained(callbacks), group_id, input_codec_conf,
                          output_codec_conf, input_selectable_codec_conf,
                          output_selectable_codec_conf));
  }

  void Initialize(LeAudioClientCallbacks* callbacks,
                  const std::vector<btle_audio_codec_config_t>&
                      offloading_preference) override {
    this->callbacks = callbacks;

    for (auto codec : offloading_preference) {
      LOG_INFO("supported codec: %s", codec.ToString().c_str());
    }

    LeAudioClient::InitializeAudioSetConfigurationProvider();
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioClient::Initialize, this,
                        jni_thread_wrapper(
                            FROM_HERE, Bind(&btif_storage_load_bonded_leaudio)),
                        base::Bind([]() -> bool {
                          return LeAudioHalVerifier::SupportsLeAudio();
                        }),
                        offloading_preference));
  }

  void Cleanup(void) override {
    DVLOG(2) << __func__;
    do_in_main_thread(
        FROM_HERE,
        Bind(&LeAudioClient::Cleanup,
             jni_thread_wrapper(
                 FROM_HERE,
                 Bind(&LeAudioClient::CleanupAudioSetConfigurationProvider))));
  }

  void RemoveDevice(const RawAddress& address) override {
    DVLOG(2) << __func__ << " address: " << address;
    do_in_main_thread(FROM_HERE,
                      Bind(&LeAudioClient::RemoveDevice,
                           Unretained(LeAudioClient::Get()), address));

    do_in_jni_thread(FROM_HERE, Bind(&btif_storage_remove_leaudio, address));
  }

  void Connect(const RawAddress& address) override {
    DVLOG(2) << __func__ << " address: " << address;
    do_in_main_thread(FROM_HERE,
                      Bind(&LeAudioClient::Connect,
                           Unretained(LeAudioClient::Get()), address));
  }

  void Disconnect(const RawAddress& address) override {
    DVLOG(2) << __func__ << " address: " << address;
    do_in_main_thread(FROM_HERE,
                      Bind(&LeAudioClient::Disconnect,
                           Unretained(LeAudioClient::Get()), address));
  }

  void GroupAddNode(const int group_id, const RawAddress& address) override {
    DVLOG(2) << __func__ << " group_id: " << group_id
             << " address: " << address;
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioClient::GroupAddNode,
                        Unretained(LeAudioClient::Get()), group_id, address));
  }

  void GroupRemoveNode(const int group_id, const RawAddress& address) override {
    DVLOG(2) << __func__ << " group_id: " << group_id
             << " address: " << address;
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioClient::GroupRemoveNode,
                        Unretained(LeAudioClient::Get()), group_id, address));
  }

  void GroupSetActive(const int group_id) override {
    DVLOG(2) << __func__ << " group_id: " << group_id;
    do_in_main_thread(FROM_HERE,
                      Bind(&LeAudioClient::GroupSetActive,
                           Unretained(LeAudioClient::Get()), group_id));
  }

  void SetCodecConfigPreference(int group_id,
                                btle_audio_codec_config_t input_codec_config,
                                btle_audio_codec_config_t output_codec_config) {
    DVLOG(2) << __func__ << " group_id: " << group_id;
    do_in_main_thread(FROM_HERE,
                      Bind(&LeAudioClient::SetCodecConfigPreference,
                           Unretained(LeAudioClient::Get()), group_id,
                           input_codec_config, output_codec_config));
  }

  void SetCcidInformation(int ccid, int context_type) {
    DVLOG(2) << __func__ << " ccid: " << ccid << " context_type"
             << context_type;
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioClient::SetCcidInformation,
                        Unretained(LeAudioClient::Get()), ccid, context_type));
  }

  void SetInCall(bool in_call) {
    DVLOG(2) << __func__ << " in_call: " << in_call;
    do_in_main_thread(FROM_HERE,
                      Bind(&LeAudioClient::SetInCall,
                           Unretained(LeAudioClient::Get()), in_call));
  }

 private:
  LeAudioClientCallbacks* callbacks;
};

} /* namespace */

LeAudioClientInterface* btif_le_audio_get_interface() {
  if (!leAudioInstance) {
    leAudioInstance.reset(new LeAudioClientInterfaceImpl());
  }

  return leAudioInstance.get();
}

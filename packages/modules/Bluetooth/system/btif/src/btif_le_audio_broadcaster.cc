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

#include <base/bind.h>
#include <base/logging.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_le_audio.h>

#include "audio_hal_interface/hal_version_manager.h"
#include "bta_le_audio_api.h"
#include "bta_le_audio_broadcaster_api.h"
#include "btif_common.h"
#include "stack/include/btu.h"

using base::Bind;
using base::Unretained;
using bluetooth::le_audio::BroadcastId;
using bluetooth::le_audio::BroadcastState;
using bluetooth::le_audio::LeAudioBroadcasterCallbacks;
using bluetooth::le_audio::LeAudioBroadcasterInterface;

namespace {
class LeAudioBroadcasterInterfaceImpl;
std::unique_ptr<LeAudioBroadcasterInterface> leAudioBroadcasterInstance;

class LeAudioBroadcasterInterfaceImpl : public LeAudioBroadcasterInterface,
                                        public LeAudioBroadcasterCallbacks {
  ~LeAudioBroadcasterInterfaceImpl() override = default;

  void Initialize(LeAudioBroadcasterCallbacks* callbacks) override {
    this->callbacks_ = callbacks;
    do_in_main_thread(
        FROM_HERE,
        Bind(&LeAudioBroadcaster::Initialize, this, base::Bind([]() -> bool {
          return LeAudioHalVerifier::SupportsLeAudioBroadcast();
        })));
  }

  void CreateBroadcast(
      std::vector<uint8_t> metadata,
      std::optional<std::array<uint8_t, 16>> broadcast_code) override {
    DVLOG(2) << __func__;
    do_in_main_thread(FROM_HERE, Bind(&LeAudioBroadcaster::CreateAudioBroadcast,
                                      Unretained(LeAudioBroadcaster::Get()),
                                      std::move(metadata), broadcast_code));
  }

  void UpdateMetadata(uint32_t broadcast_id,
                      std::vector<uint8_t> metadata) override {
    DVLOG(2) << __func__;
    do_in_main_thread(FROM_HERE, Bind(&LeAudioBroadcaster::UpdateMetadata,
                                      Unretained(LeAudioBroadcaster::Get()),
                                      broadcast_id, std::move(metadata)));
  }

  void StartBroadcast(uint32_t broadcast_id) override {
    DVLOG(2) << __func__;
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioBroadcaster::StartAudioBroadcast,
                        Unretained(LeAudioBroadcaster::Get()), broadcast_id));
  }

  void StopBroadcast(uint32_t broadcast_id) override {
    DVLOG(2) << __func__;
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioBroadcaster::StopAudioBroadcast,
                        Unretained(LeAudioBroadcaster::Get()), broadcast_id));
  }

  void PauseBroadcast(uint32_t broadcast_id) override {
    DVLOG(2) << __func__;
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioBroadcaster::SuspendAudioBroadcast,
                        Unretained(LeAudioBroadcaster::Get()), broadcast_id));
  }

  void DestroyBroadcast(uint32_t broadcast_id) override {
    DVLOG(2) << __func__;
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioBroadcaster::DestroyAudioBroadcast,
                        Unretained(LeAudioBroadcaster::Get()), broadcast_id));
  }

  void GetBroadcastMetadata(uint32_t broadcast_id) override {
    DVLOG(2) << __func__;
    do_in_main_thread(
        FROM_HERE, Bind(&LeAudioBroadcaster::GetBroadcastMetadata,
                        Unretained(LeAudioBroadcaster::Get()), broadcast_id));
  }

  void OnBroadcastCreated(uint32_t broadcast_id, bool success) override {
    DVLOG(2) << __func__;
    do_in_jni_thread(FROM_HERE,
                     Bind(&LeAudioBroadcasterCallbacks::OnBroadcastCreated,
                          Unretained(callbacks_), broadcast_id, success));
  }

  void OnBroadcastDestroyed(uint32_t broadcast_id) override {
    DVLOG(2) << __func__;
    do_in_jni_thread(FROM_HERE,
                     Bind(&LeAudioBroadcasterCallbacks::OnBroadcastDestroyed,
                          Unretained(callbacks_), broadcast_id));
  }

  void OnBroadcastStateChanged(uint32_t broadcast_id,
                               BroadcastState state) override {
    DVLOG(2) << __func__;
    do_in_jni_thread(FROM_HERE,
                     Bind(&LeAudioBroadcasterCallbacks::OnBroadcastStateChanged,
                          Unretained(callbacks_), broadcast_id, state));
  }

  void OnBroadcastMetadataChanged(uint32_t broadcast_id,
                                  const bluetooth::le_audio::BroadcastMetadata&
                                      broadcast_metadata) override {
    DVLOG(2) << __func__;
    do_in_jni_thread(
        FROM_HERE,
        Bind(&LeAudioBroadcasterCallbacks::OnBroadcastMetadataChanged,
             Unretained(callbacks_), broadcast_id, broadcast_metadata));
  }

  void Stop(void) override {
    do_in_main_thread(FROM_HERE, Bind(&LeAudioBroadcaster::Stop));
  }

  void Cleanup(void) override {
    do_in_main_thread(FROM_HERE, Bind(&LeAudioBroadcaster::Cleanup));
  }

 private:
  LeAudioBroadcasterCallbacks* callbacks_;
};

} /* namespace */

LeAudioBroadcasterInterface* btif_le_audio_broadcaster_get_interface() {
  if (!leAudioBroadcasterInstance) {
    leAudioBroadcasterInstance.reset(new LeAudioBroadcasterInterfaceImpl());
  }

  return leAudioBroadcasterInstance.get();
}

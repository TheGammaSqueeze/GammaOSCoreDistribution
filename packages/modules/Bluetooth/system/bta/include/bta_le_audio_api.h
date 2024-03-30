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

#pragma once

#include <base/bind.h>
#include <base/callback.h>
#include <base/callback_forward.h>
#include <hardware/bt_le_audio.h>

#include <vector>

class LeAudioHalVerifier {
 public:
  static bool SupportsLeAudio();
  static bool SupportsLeAudioHardwareOffload();
  static bool SupportsLeAudioBroadcast();
};

/* Interface class */
class LeAudioClient {
 public:
  virtual ~LeAudioClient(void) = default;

  static void Initialize(
      bluetooth::le_audio::LeAudioClientCallbacks* callbacks,
      base::Closure initCb, base::Callback<bool()> hal_2_1_verifier,
      const std::vector<bluetooth::le_audio::btle_audio_codec_config_t>&
          offloading_preference);
  static void Cleanup(base::Callback<void()> cleanupCb);
  static LeAudioClient* Get(void);
  static void DebugDump(int fd);

  virtual void RemoveDevice(const RawAddress& address) = 0;
  virtual void Connect(const RawAddress& address) = 0;
  virtual void Disconnect(const RawAddress& address) = 0;
  virtual void GroupAddNode(const int group_id, const RawAddress& addr) = 0;
  virtual void GroupRemoveNode(const int group_id, const RawAddress& addr) = 0;
  virtual void GroupStream(const int group_id, const uint16_t content_type) = 0;
  virtual void GroupSuspend(const int group_id) = 0;
  virtual void GroupStop(const int group_id) = 0;
  virtual void GroupDestroy(const int group_id) = 0;
  virtual void GroupSetActive(const int group_id) = 0;
  virtual void SetCodecConfigPreference(
      int group_id,
      bluetooth::le_audio::btle_audio_codec_config_t input_codec_config,
      bluetooth::le_audio::btle_audio_codec_config_t output_codec_config) = 0;
  virtual void SetCcidInformation(int ccid, int context_type) = 0;
  virtual void SetInCall(bool in_call) = 0;

  virtual std::vector<RawAddress> GetGroupDevices(const int group_id) = 0;
  static void AddFromStorage(const RawAddress& addr, bool autoconnect,
                             int sink_audio_location, int source_audio_location,
                             int sink_supported_context_types,
                             int source_supported_context_types,
                             const std::vector<uint8_t>& handles,
                             const std::vector<uint8_t>& sink_pacs,
                             const std::vector<uint8_t>& source_pacs,
                             const std::vector<uint8_t>& ases);
  static bool GetHandlesForStorage(const RawAddress& addr,
                                   std::vector<uint8_t>& out);
  static bool GetSinkPacsForStorage(const RawAddress& addr,
                                    std::vector<uint8_t>& out);
  static bool GetSourcePacsForStorage(const RawAddress& addr,
                                      std::vector<uint8_t>& out);
  static bool GetAsesForStorage(const RawAddress& addr,
                                std::vector<uint8_t>& out);
  static bool IsLeAudioClientRunning();

  static void InitializeAudioSetConfigurationProvider(void);
  static void CleanupAudioSetConfigurationProvider(void);
};

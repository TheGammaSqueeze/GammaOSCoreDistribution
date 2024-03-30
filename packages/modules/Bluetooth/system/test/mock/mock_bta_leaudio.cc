/*
 * Copyright 2021 The Android Open Source Project
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

/*
 * Generated mock file from original source file
 *   Functions generated:7
 */

#include <map>
#include <memory>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

#include <base/bind.h>
#include <base/bind_helpers.h>
#include <hardware/bt_le_audio.h>

#include "bta/include/bta_le_audio_api.h"
#include "types/raw_address.h"

#ifndef UNUSED_ATTR
#define UNUSED_ATTR
#endif

/* Empty class to satisfy compiler */
namespace bluetooth {
namespace audio {

class HalVersionManager {
  static inline std::unique_ptr<HalVersionManager> instance_ptr =
      std::make_unique<HalVersionManager>();
};

}  // namespace audio
}  // namespace bluetooth

void LeAudioClient::AddFromStorage(
    const RawAddress& addr, bool autoconnect, int sink_audio_location,
    int source_audio_location, int sink_supported_context_types,
    int source_supported_context_types, const std::vector<uint8_t>& handles,
    const std::vector<uint8_t>& sink_pacs,
    const std::vector<uint8_t>& source_pacs, const std::vector<uint8_t>& ases) {
  mock_function_count_map[__func__]++;
}

bool LeAudioClient::GetHandlesForStorage(const RawAddress& addr,
                                         std::vector<uint8_t>& out) {
  mock_function_count_map[__func__]++;
  return false;
}

bool LeAudioClient::GetSinkPacsForStorage(const RawAddress& addr,
                                          std::vector<uint8_t>& out) {
  mock_function_count_map[__func__]++;
  return false;
}

bool LeAudioClient::GetSourcePacsForStorage(const RawAddress& addr,
                                            std::vector<uint8_t>& out) {
  mock_function_count_map[__func__]++;
  return false;
}

bool LeAudioClient::GetAsesForStorage(const RawAddress& addr,
                                      std::vector<uint8_t>& out) {
  mock_function_count_map[__func__]++;
  return false;
}

void LeAudioClient::Cleanup(base::Callback<void()> cleanupCb) {
  std::move(cleanupCb).Run();
  mock_function_count_map[__func__]++;
}

LeAudioClient* LeAudioClient::Get(void) {
  mock_function_count_map[__func__]++;
  return nullptr;
}
bool LeAudioClient::IsLeAudioClientRunning(void) {
  mock_function_count_map[__func__]++;
  return false;
}
void LeAudioClient::Initialize(
    bluetooth::le_audio::LeAudioClientCallbacks* callbacks_,
    base::Closure initCb, base::Callback<bool()> hal_2_1_verifier,
    const std::vector<bluetooth::le_audio::btle_audio_codec_config_t>&
        offloading_preference) {
  mock_function_count_map[__func__]++;
}
void LeAudioClient::DebugDump(int fd) { mock_function_count_map[__func__]++; }
void LeAudioClient::InitializeAudioSetConfigurationProvider() {
  mock_function_count_map[__func__]++;
}
void LeAudioClient::CleanupAudioSetConfigurationProvider() {
  mock_function_count_map[__func__]++;
}

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

#pragma once

#include <gmock/gmock.h>

#include "codec_manager.h"

class MockCodecManager {
 public:
  static MockCodecManager* GetInstance();

  MockCodecManager() = default;
  MockCodecManager(const MockCodecManager&) = delete;
  MockCodecManager& operator=(const MockCodecManager&) = delete;

  virtual ~MockCodecManager() = default;

  MOCK_METHOD((le_audio::types::CodecLocation), GetCodecLocation, (), (const));
  MOCK_METHOD((void), UpdateActiveSourceAudioConfig,
              (const le_audio::stream_configuration& stream_conf,
               uint16_t delay,
               std::function<void(const ::le_audio::offload_config& config)>
                   update_receiver));
  MOCK_METHOD((void), UpdateActiveSinkAudioConfig,
              (const le_audio::stream_configuration& stream_conf,
               uint16_t delay,
               std::function<void(const ::le_audio::offload_config& config)>
                   update_receiver));
  MOCK_METHOD((le_audio::set_configurations::AudioSetConfigurations*),
              GetOffloadCodecConfig,
              (le_audio::types::LeAudioContextType ctx_type), (const));
  MOCK_METHOD((le_audio::broadcast_offload_config*), GetBroadcastOffloadConfig,
              (), (const));
  MOCK_METHOD(
      (void), UpdateBroadcastConnHandle,
      (const std::vector<uint16_t>& conn_handle,
       std::function<void(const ::le_audio::broadcast_offload_config& config)>
           update_receiver));

  MOCK_METHOD((void), Start, ());
  MOCK_METHOD((void), Stop, ());
};

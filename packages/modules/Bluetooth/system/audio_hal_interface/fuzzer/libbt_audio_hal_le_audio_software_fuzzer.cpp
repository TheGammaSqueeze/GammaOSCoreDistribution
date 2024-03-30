/*
 * Copyright (C) 2021 The Android Open Source Project
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
 */

#include <fuzzer/FuzzedDataProvider.h>

#include "audio_hal_interface/le_audio_software.h"
#include "osi/include/properties.h"

using ::bluetooth::audio::le_audio::LeAudioClientInterface;

constexpr int32_t kRandomStringLength = 256;

constexpr uint8_t kBitsPerSample[] = {0, 16, 24, 32};

constexpr uint8_t kChannelCount[] = {0, 1, 2};

constexpr uint32_t kSampleRates[] = {0,     8000,  16000, 24000,  32000, 44100,
                                     48000, 88200, 96000, 176400, 192000};

extern "C" {
struct android_namespace_t* android_get_exported_namespace(const char*) {
  return nullptr;
}
}

bool onResume(bool) { return true; }

bool onSuspend(void) { return true; }

bool onMetadataUpdate(const source_metadata_t&) { return true; }

bool onSinkMetadataUpdate(const sink_metadata_t&) { return true; }

static void source_init_delayed(void) {}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fdp(data, size);
  osi_property_set("persist.bluetooth.a2dp_offload.disabled",
                   fdp.PickValueInArray({"true", "false"}));
  std::string name = fdp.ConsumeRandomLengthString(kRandomStringLength);
  bluetooth::common::MessageLoopThread messageLoopThread(name);
  messageLoopThread.StartUp();
  messageLoopThread.DoInThread(FROM_HERE, base::Bind(&source_init_delayed));

  LeAudioClientInterface* interface = LeAudioClientInterface::Get();

  bluetooth::audio::le_audio::StreamCallbacks streamCb = {
      onResume, onSuspend, onMetadataUpdate, onSinkMetadataUpdate};

  if (!interface->IsSourceAcquired()) {
    LeAudioClientInterface::Source* source =
        interface->GetSource(streamCb, &messageLoopThread);
    if (source != nullptr) {
      source->StartSession();
      uint16_t delay = fdp.ConsumeIntegral<uint16_t>();
      source->SetRemoteDelay(delay);
      LeAudioClientInterface::PcmParameters params;
      params.data_interval_us = fdp.ConsumeIntegral<uint32_t>();
      params.sample_rate = fdp.PickValueInArray(kSampleRates);
      params.bits_per_sample = fdp.PickValueInArray(kBitsPerSample);
      params.channels_count = fdp.PickValueInArray(kChannelCount);
      source->SetPcmParameters(params);
      source->StopSession();
      source->Cleanup();
    }
    interface->ReleaseSource(source);
  }

  if (!interface->IsUnicastSinkAcquired()) {
    LeAudioClientInterface::Sink* sink =
        interface->GetSink(streamCb, &messageLoopThread, false);
    if (sink != nullptr) {
      sink->StartSession();
      uint16_t delay = fdp.ConsumeIntegral<uint16_t>();
      sink->SetRemoteDelay(delay);
      LeAudioClientInterface::PcmParameters params;
      params.data_interval_us = fdp.ConsumeIntegral<uint32_t>();
      params.sample_rate = fdp.PickValueInArray(kSampleRates);
      params.bits_per_sample = fdp.PickValueInArray(kBitsPerSample);
      params.channels_count = fdp.PickValueInArray(kChannelCount);
      sink->SetPcmParameters(params);
      sink->StopSession();
      sink->Cleanup();
    }
    interface->ReleaseSink(sink);
  }

  messageLoopThread.ShutDown();
  return 0;
}

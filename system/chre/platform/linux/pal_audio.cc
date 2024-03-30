/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "chre/pal/audio.h"

#include "chre/platform/memory.h"
#include "chre/util/macros.h"
#include "chre/util/memory.h"
#include "chre/util/unique_ptr.h"

#include <chrono>
#include <cinttypes>
#include <cstdint>
#include <future>
#include <thread>

/**
 * A simulated implementation of the audio PAL for the linux platform.
 */
namespace {
const struct chrePalSystemApi *gSystemApi = nullptr;
const struct chrePalAudioCallbacks *gCallbacks = nullptr;

//! Thread to deliver asynchronous audio data after a CHRE request.
std::thread gHandle0Thread;
std::promise<void> gStopHandle0Thread;
constexpr uint32_t kHandle0SampleRate = 16000;

//! Whether the handle 0 is currently enabled.
bool gIsHandle0Enabled = false;

void stopHandle0Thread() {
  if (gHandle0Thread.joinable()) {
    gStopHandle0Thread.set_value();
    gHandle0Thread.join();
  }
}

void chrePalAudioApiClose(void) {
  stopHandle0Thread();
}

bool chrePalAudioApiOpen(const struct chrePalSystemApi *systemApi,
                         const struct chrePalAudioCallbacks *callbacks) {
  chrePalAudioApiClose();

  if (systemApi != nullptr && callbacks != nullptr) {
    gSystemApi = systemApi;
    gCallbacks = callbacks;
    callbacks->audioAvailabilityCallback(0 /*handle*/, true /*available*/);
    return true;
  }

  return false;
}

void sendHandle0Events(uint64_t delayNs, uint32_t numSamples) {
  std::future<void> signal = gStopHandle0Thread.get_future();
  if (signal.wait_for(std::chrono::nanoseconds(delayNs)) ==
      std::future_status::timeout) {
    auto data = chre::MakeUniqueZeroFill<struct chreAudioDataEvent>();

    data->version = CHRE_AUDIO_DATA_EVENT_VERSION;
    data->handle = 0;
    data->timestamp = gSystemApi->getCurrentTime();
    data->sampleRate = kHandle0SampleRate;
    data->sampleCount = numSamples;
    data->format = CHRE_AUDIO_DATA_FORMAT_8_BIT_U_LAW;
    data->samplesULaw8 =
        static_cast<const uint8_t *>(chre::memoryAlloc(numSamples));

    gCallbacks->audioDataEventCallback(data.release());
  }
}

bool chrePalAudioApiRequestAudioDataEvent(uint32_t handle, uint32_t numSamples,
                                          uint64_t eventDelayNs) {
  if (handle != 0) {
    return false;
  }

  stopHandle0Thread();
  if (numSamples > 0) {
    gIsHandle0Enabled = true;
    gStopHandle0Thread = std::promise<void>();
    gHandle0Thread = std::thread(sendHandle0Events, eventDelayNs, numSamples);
  }

  return true;
}

void chrePalAudioApiCancelAudioDataEvent(uint32_t handle) {
  if (handle == 0) {
    gIsHandle0Enabled = false;
    stopHandle0Thread();
  }
}

void chrePalAudioApiReleaseAudioDataEvent(struct chreAudioDataEvent *event) {
  if (event->format == CHRE_AUDIO_DATA_FORMAT_8_BIT_U_LAW) {
    chre::memoryFree((void *)event->samplesULaw8);
  } else if (event->format == CHRE_AUDIO_DATA_FORMAT_16_BIT_SIGNED_PCM) {
    chre::memoryFree((void *)event->samplesS16);
  }
  chre::memoryFree(event);
}

uint32_t chrePalAudioApiGetSourceCount(void) {
  return 1;
}

bool chrePalAudioApiGetAudioSource(uint32_t handle,
                                   struct chreAudioSource *audioSource) {
  if (handle != 0) {
    return false;
  }

  *audioSource = {
      .name = "Test Source",
      .sampleRate = kHandle0SampleRate,
      .minBufferDuration = 1,
      .maxBufferDuration = 1000000000,
      .format = CHRE_AUDIO_DATA_FORMAT_8_BIT_U_LAW,
  };

  return true;
}

}  // namespace

bool chrePalAudioIsHandle0Enabled() {
  return gIsHandle0Enabled;
}

const chrePalAudioApi *chrePalAudioGetApi(uint32_t requestedApiVersion) {
  static const struct chrePalAudioApi kApi = {
      .moduleVersion = CHRE_PAL_AUDIO_API_CURRENT_VERSION,
      .open = chrePalAudioApiOpen,
      .close = chrePalAudioApiClose,
      .requestAudioDataEvent = chrePalAudioApiRequestAudioDataEvent,
      .cancelAudioDataEvent = chrePalAudioApiCancelAudioDataEvent,
      .releaseAudioDataEvent = chrePalAudioApiReleaseAudioDataEvent,
      .getSourceCount = chrePalAudioApiGetSourceCount,
      .getAudioSource = chrePalAudioApiGetAudioSource,
  };

  if (!CHRE_PAL_VERSIONS_ARE_COMPATIBLE(kApi.moduleVersion,
                                        requestedApiVersion)) {
    return nullptr;
  } else {
    return &kApi;
  }
}
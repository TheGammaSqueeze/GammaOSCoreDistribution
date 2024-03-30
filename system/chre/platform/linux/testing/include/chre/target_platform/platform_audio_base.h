/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_LINUX_PLATFORM_AUDIO_BASE_H_
#define CHRE_PLATFORM_LINUX_PLATFORM_AUDIO_BASE_H_

#include "chre/pal/audio.h"
#include "chre/platform/shared/platform_pal.h"

namespace chre {

/**
 * Provides an instance of the PlatformAudioBase class that uses the CHRE PAL to
 * access the audio subsystem.
 */
class PlatformAudioBase : public PlatformPal {
 protected:
  //! The instance of callbacks that are provided to the CHRE PAL.
  static const chrePalAudioCallbacks sCallbacks;

  //! The instance of the CHRE PAL API. This will be set to nullptr if the
  //! platform does not supply an implementation.
  const chrePalAudioApi *mApi;

  static void audioDataEventCallback(struct chreAudioDataEvent *event);
  static void audioAvailabilityCallback(uint32_t handle, bool available);
};

}  // namespace chre

#endif  // CHRE_PLATFORM_LINUX_PLATFORM_AUDIO_BASE_H_

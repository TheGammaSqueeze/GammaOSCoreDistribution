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

#include "chre_api/chre/audio.h"

#include <cstdint>

#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/platform/linux/pal_audio.h"
#include "chre/platform/log.h"
#include "chre/util/system/napp_permissions.h"
#include "chre_api/chre/event.h"
#include "chre_api/chre/user_settings.h"

#include "gtest/gtest.h"
#include "inc/test_util.h"
#include "test_base.h"
#include "test_event.h"
#include "test_event_queue.h"
#include "test_util.h"

namespace chre {
namespace {

struct AudioNanoapp : public TestNanoapp {
  uint32_t perms = NanoappPermissions::CHRE_PERMS_AUDIO;

  bool (*start)() = []() {
    chreUserSettingConfigureEvents(CHRE_USER_SETTING_MICROPHONE,
                                   true /* enable */);
    return true;
  };
};

TEST_F(TestBase, AudioCanSubscribeAndUnsubscribeToDataEvents) {
  CREATE_CHRE_TEST_EVENT(CONFIGURE, 0);

  struct App : public AudioNanoapp {
    void (*handleEvent)(uint32_t, uint16_t,
                        const void *) = [](uint32_t, uint16_t eventType,
                                           const void *eventData) {
      static int count = 0;

      switch (eventType) {
        case CHRE_EVENT_AUDIO_DATA: {
          auto event =
              static_cast<const struct chreAudioDataEvent *>(eventData);
          if (event->handle == 0) {
            count++;
            if (count == 3) {
              TestEventQueueSingleton::get()->pushEvent(CHRE_EVENT_AUDIO_DATA);
            }
          }
          break;
        }

        case CHRE_EVENT_AUDIO_SAMPLING_CHANGE: {
          auto event =
              static_cast<const struct chreAudioSourceStatusEvent *>(eventData);
          if (event->handle == 0) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_AUDIO_SAMPLING_CHANGE);
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case CONFIGURE: {
              auto enable = static_cast<const bool *>(event->data);
              const bool success = chreAudioConfigureSource(
                  0 /*handle*/, *enable, 1000000 /*bufferDuration*/,
                  1000000 /*deliveryInterval*/);
              TestEventQueueSingleton::get()->pushEvent(CONFIGURE, success);
              break;
            }
          }
        }
      }
    };
  };

  auto app = loadNanoapp<App>();
  EXPECT_FALSE(chrePalAudioIsHandle0Enabled());

  bool enable = true;
  bool success;
  sendEventToNanoapp(app, CONFIGURE, enable);
  waitForEvent(CONFIGURE, &success);
  EXPECT_TRUE(success);
  waitForEvent(CHRE_EVENT_AUDIO_SAMPLING_CHANGE);
  EXPECT_TRUE(chrePalAudioIsHandle0Enabled());

  waitForEvent(CHRE_EVENT_AUDIO_DATA);

  enable = false;
  sendEventToNanoapp(app, CONFIGURE, enable);
  waitForEvent(CONFIGURE, &success);
  EXPECT_TRUE(success);
  EXPECT_FALSE(chrePalAudioIsHandle0Enabled());
}

TEST_F(TestBase, AudioUnsubscribeToDataEventsOnUnload) {
  CREATE_CHRE_TEST_EVENT(CONFIGURE, 0);

  struct App : public AudioNanoapp {
    void (*handleEvent)(uint32_t, uint16_t,
                        const void *) = [](uint32_t, uint16_t eventType,
                                           const void *eventData) {
      switch (eventType) {
        case CHRE_EVENT_AUDIO_SAMPLING_CHANGE: {
          auto event =
              static_cast<const struct chreAudioSourceStatusEvent *>(eventData);
          if (event->handle == 0) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_AUDIO_SAMPLING_CHANGE);
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case CONFIGURE: {
              auto enable = static_cast<const bool *>(event->data);
              const bool success = chreAudioConfigureSource(
                  0 /*handle*/, *enable, 1000000 /*bufferDuration*/,
                  1000000 /*deliveryInterval*/);
              TestEventQueueSingleton::get()->pushEvent(CONFIGURE, success);
              break;
            }
          }
        }
      }
    };
  };

  auto app = loadNanoapp<App>();
  EXPECT_FALSE(chrePalAudioIsHandle0Enabled());

  bool enable = true;
  bool success;
  sendEventToNanoapp(app, CONFIGURE, enable);
  waitForEvent(CONFIGURE, &success);
  EXPECT_TRUE(success);
  waitForEvent(CHRE_EVENT_AUDIO_SAMPLING_CHANGE);
  EXPECT_TRUE(chrePalAudioIsHandle0Enabled());

  unloadNanoapp(app);
  EXPECT_FALSE(chrePalAudioIsHandle0Enabled());
}

}  // namespace
}  // namespace chre
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

#include "chre_api/chre/re.h"

#include <cstdint>

#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/platform/log.h"
#include "chre/util/time.h"
#include "chre_api/chre/event.h"

#include "gtest/gtest.h"
#include "inc/test_util.h"
#include "test_base.h"
#include "test_event.h"
#include "test_event_queue.h"
#include "test_util.h"

namespace chre {

// TestTimer is required to access private members of the TimerPool.
class TestTimer : public TestBase {
 protected:
  bool hasNanoappTimers(TimerPool &pool, uint16_t instanceId) {
    return pool.hasNanoappTimers(instanceId);
  }
};

namespace {
TEST_F(TestTimer, SetupAndCancelPeriodicTimer) {
  CREATE_CHRE_TEST_EVENT(START_TIMER, 0);
  CREATE_CHRE_TEST_EVENT(STOP_TIMER, 1);

  struct App : public TestNanoapp {
    void (*handleEvent)(uint32_t, uint16_t,
                        const void *) = [](uint32_t, uint16_t eventType,
                                           const void *eventData) {
      const uint32_t cookie = 123;
      switch (eventType) {
        static int count = 0;

        case CHRE_EVENT_TIMER: {
          auto data = static_cast<const uint32_t *>(eventData);
          if (*data == cookie) {
            count++;
            if (count == 3) {
              TestEventQueueSingleton::get()->pushEvent(CHRE_EVENT_TIMER);
            }
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case START_TIMER: {
              uint32_t handle = chreTimerSet(kOneMillisecondInNanoseconds,
                                             &cookie, false /*oneShot*/);
              TestEventQueueSingleton::get()->pushEvent(START_TIMER, handle);
              break;
            }
            case STOP_TIMER: {
              auto handle = static_cast<const uint32_t *>(event->data);
              bool success = chreTimerCancel(*handle);
              TestEventQueueSingleton::get()->pushEvent(STOP_TIMER, success);
              break;
            }
          }
        }
      }
    };
  };

  auto app = loadNanoapp<App>();

  TimerPool &timerPool =
      EventLoopManagerSingleton::get()->getEventLoop().getTimerPool();

  uint16_t instanceId;
  EXPECT_TRUE(EventLoopManagerSingleton::get()
                  ->getEventLoop()
                  .findNanoappInstanceIdByAppId(app.id, &instanceId));

  uint32_t handle;
  sendEventToNanoapp(app, START_TIMER);
  waitForEvent(START_TIMER, &handle);
  EXPECT_NE(handle, CHRE_TIMER_INVALID);
  EXPECT_TRUE(hasNanoappTimers(timerPool, instanceId));

  waitForEvent(CHRE_EVENT_TIMER);

  bool success;

  // Cancelling an active timer should be successful.
  sendEventToNanoapp(app, STOP_TIMER, handle);
  waitForEvent(STOP_TIMER, &success);
  EXPECT_TRUE(success);
  EXPECT_FALSE(hasNanoappTimers(timerPool, instanceId));

  // Cancelling an inactive time should return false.
  sendEventToNanoapp(app, STOP_TIMER, handle);
  waitForEvent(STOP_TIMER, &success);
  EXPECT_FALSE(success);
}

TEST_F(TestTimer, CancelPeriodicTimerOnUnload) {
  CREATE_CHRE_TEST_EVENT(START_TIMER, 0);

  struct App : public TestNanoapp {
    void (*handleEvent)(uint32_t, uint16_t,
                        const void *) = [](uint32_t, uint16_t eventType,
                                           const void *eventData) {
      const uint32_t cookie = 123;
      switch (eventType) {
        static int count = 0;

        case CHRE_EVENT_TIMER: {
          auto data = static_cast<const uint32_t *>(eventData);
          if (*data == cookie) {
            count++;
            if (count == 3) {
              TestEventQueueSingleton::get()->pushEvent(CHRE_EVENT_TIMER);
            }
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case START_TIMER: {
              uint32_t handle = chreTimerSet(kOneMillisecondInNanoseconds,
                                             &cookie, false /*oneShot*/);
              TestEventQueueSingleton::get()->pushEvent(START_TIMER, handle);
              break;
            }
          }
        }
      }
    };
  };

  auto app = loadNanoapp<App>();

  TimerPool &timerPool =
      EventLoopManagerSingleton::get()->getEventLoop().getTimerPool();

  uint16_t instanceId;
  EXPECT_TRUE(EventLoopManagerSingleton::get()
                  ->getEventLoop()
                  .findNanoappInstanceIdByAppId(app.id, &instanceId));

  uint32_t handle;
  sendEventToNanoapp(app, START_TIMER);
  waitForEvent(START_TIMER, &handle);
  EXPECT_NE(handle, CHRE_TIMER_INVALID);
  EXPECT_TRUE(hasNanoappTimers(timerPool, instanceId));

  waitForEvent(CHRE_EVENT_TIMER);

  unloadNanoapp(app);
  EXPECT_FALSE(hasNanoappTimers(timerPool, instanceId));
}

}  // namespace
}  // namespace chre

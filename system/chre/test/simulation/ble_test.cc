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

#include "chre/common.h"
#include "inc/test_util.h"
#include "test_base.h"

#include <gtest/gtest.h>
#include <cstdint>

#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/linux/pal_ble.h"
#include "chre/util/dynamic_vector.h"
#include "chre_api/chre/ble.h"
#include "chre_api/chre/user_settings.h"
#include "test_util.h"

namespace chre {

namespace {

/**
 * This test verifies that a nanoapp can query for BLE capabilities and filter
 * capabilities. Note that a nanoapp does not require BLE permissions to use
 * these APIs.
 */
TEST_F(TestBase, BleCapabilitiesTest) {
  CREATE_CHRE_TEST_EVENT(GET_CAPABILITIES, 0);
  CREATE_CHRE_TEST_EVENT(GET_FILTER_CAPABILITIES, 1);

  struct App : public TestNanoapp {
    uint32_t perms = NanoappPermissions::CHRE_PERMS_WIFI;

    void (*handleEvent)(uint32_t, uint16_t, const void *) =
        [](uint32_t, uint16_t eventType, const void *eventData) {
          switch (eventType) {
            case CHRE_EVENT_TEST_EVENT: {
              auto event = static_cast<const TestEvent *>(eventData);
              switch (event->type) {
                case GET_CAPABILITIES: {
                  TestEventQueueSingleton::get()->pushEvent(
                      GET_CAPABILITIES, chreBleGetCapabilities());
                  break;
                }

                case GET_FILTER_CAPABILITIES: {
                  TestEventQueueSingleton::get()->pushEvent(
                      GET_FILTER_CAPABILITIES, chreBleGetFilterCapabilities());
                  break;
                }
              }
            }
          }
        };
  };

  auto app = loadNanoapp<App>();

  uint32_t capabilities;
  sendEventToNanoapp(app, GET_CAPABILITIES);
  waitForEvent(GET_CAPABILITIES, &capabilities);
  ASSERT_EQ(capabilities, CHRE_BLE_CAPABILITIES_SCAN |
                              CHRE_BLE_CAPABILITIES_SCAN_RESULT_BATCHING |
                              CHRE_BLE_CAPABILITIES_SCAN_FILTER_BEST_EFFORT);

  sendEventToNanoapp(app, GET_FILTER_CAPABILITIES);
  waitForEvent(GET_FILTER_CAPABILITIES, &capabilities);
  ASSERT_EQ(capabilities, CHRE_BLE_FILTER_CAPABILITIES_RSSI |
                              CHRE_BLE_FILTER_CAPABILITIES_SERVICE_DATA);
}

struct BleTestNanoapp : public TestNanoapp {
  uint32_t perms = NanoappPermissions::CHRE_PERMS_BLE;

  bool (*start)() = []() {
    chreUserSettingConfigureEvents(CHRE_USER_SETTING_BLE_AVAILABLE,
                                   true /* enable */);
    return true;
  };

  void (*end)() = []() {
    chreUserSettingConfigureEvents(CHRE_USER_SETTING_BLE_AVAILABLE,
                                   false /* enable */);
  };
};

/**
 * This test validates the case in which a nanoapp starts a scan, receives
 * at least one advertisement event, and stops a scan.
 */
TEST_F(TestBase, BleSimpleScanTest) {
  CREATE_CHRE_TEST_EVENT(START_SCAN, 0);
  CREATE_CHRE_TEST_EVENT(SCAN_STARTED, 1);
  CREATE_CHRE_TEST_EVENT(STOP_SCAN, 2);
  CREATE_CHRE_TEST_EVENT(SCAN_STOPPED, 3);

  struct App : public BleTestNanoapp {
    void (*handleEvent)(uint32_t, uint16_t,
                        const void *) = [](uint32_t, uint16_t eventType,
                                           const void *eventData) {
      switch (eventType) {
        case CHRE_EVENT_BLE_ASYNC_RESULT: {
          auto *event = static_cast<const struct chreAsyncResult *>(eventData);
          if (event->errorCode == CHRE_ERROR_NONE) {
            uint16_t type =
                (event->requestType == CHRE_BLE_REQUEST_TYPE_START_SCAN)
                    ? SCAN_STARTED
                    : SCAN_STOPPED;
            TestEventQueueSingleton::get()->pushEvent(type);
          }
          break;
        }

        case CHRE_EVENT_BLE_ADVERTISEMENT: {
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_BLE_ADVERTISEMENT);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case START_SCAN: {
              const bool success = chreBleStartScanAsync(
                  CHRE_BLE_SCAN_MODE_BACKGROUND, 0, nullptr);
              TestEventQueueSingleton::get()->pushEvent(START_SCAN, success);
              break;
            }

            case STOP_SCAN: {
              const bool success = chreBleStopScanAsync();
              TestEventQueueSingleton::get()->pushEvent(STOP_SCAN, success);
              break;
            }
          }
          break;
        }
      }
    };
  };

  auto app = loadNanoapp<App>();

  bool success;
  sendEventToNanoapp(app, START_SCAN);
  waitForEvent(START_SCAN, &success);
  EXPECT_TRUE(success);
  waitForEvent(SCAN_STARTED);
  ASSERT_TRUE(chrePalIsBleEnabled());
  waitForEvent(CHRE_EVENT_BLE_ADVERTISEMENT);

  sendEventToNanoapp(app, STOP_SCAN);
  waitForEvent(STOP_SCAN, &success);
  EXPECT_TRUE(success);
  waitForEvent(SCAN_STOPPED);
  ASSERT_FALSE(chrePalIsBleEnabled());
}

TEST_F(TestBase, BleStopScanOnUnload) {
  CREATE_CHRE_TEST_EVENT(START_SCAN, 0);
  CREATE_CHRE_TEST_EVENT(SCAN_STARTED, 1);

  struct App : public BleTestNanoapp {
    void (*handleEvent)(uint32_t, uint16_t,
                        const void *) = [](uint32_t, uint16_t eventType,
                                           const void *eventData) {
      switch (eventType) {
        case CHRE_EVENT_BLE_ASYNC_RESULT: {
          auto *event = static_cast<const struct chreAsyncResult *>(eventData);
          if (event->requestType == CHRE_BLE_REQUEST_TYPE_START_SCAN &&
              event->errorCode == CHRE_ERROR_NONE) {
            TestEventQueueSingleton::get()->pushEvent(SCAN_STARTED);
          }
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case START_SCAN: {
              const bool success = chreBleStartScanAsync(
                  CHRE_BLE_SCAN_MODE_BACKGROUND, 0, nullptr);
              TestEventQueueSingleton::get()->pushEvent(START_SCAN, success);
              break;
            }
          }
          break;
        }
      }
    };
  };

  auto app = loadNanoapp<App>();
  bool success;

  sendEventToNanoapp(app, START_SCAN);
  waitForEvent(START_SCAN, &success);
  EXPECT_TRUE(success);
  waitForEvent(SCAN_STARTED);
  ASSERT_TRUE(chrePalIsBleEnabled());

  unloadNanoapp(app);
  ASSERT_FALSE(chrePalIsBleEnabled());
}

/**
 * This test validates that a nanoapp can start a scan twice and the platform
 * will be enabled.
 */
TEST_F(TestBase, BleStartTwiceScanTest) {
  CREATE_CHRE_TEST_EVENT(START_SCAN, 0);
  CREATE_CHRE_TEST_EVENT(SCAN_STARTED, 1);
  CREATE_CHRE_TEST_EVENT(STOP_SCAN, 2);
  CREATE_CHRE_TEST_EVENT(SCAN_STOPPED, 3);

  struct App : public BleTestNanoapp {
    void (*handleEvent)(uint32_t, uint16_t,
                        const void *) = [](uint32_t, uint16_t eventType,
                                           const void *eventData) {
      switch (eventType) {
        case CHRE_EVENT_BLE_ASYNC_RESULT: {
          auto *event = static_cast<const struct chreAsyncResult *>(eventData);
          if (event->errorCode == CHRE_ERROR_NONE) {
            uint16_t type =
                (event->requestType == CHRE_BLE_REQUEST_TYPE_START_SCAN)
                    ? SCAN_STARTED
                    : SCAN_STOPPED;
            TestEventQueueSingleton::get()->pushEvent(type);
          }
          break;
        }

        case CHRE_EVENT_BLE_ADVERTISEMENT: {
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_BLE_ADVERTISEMENT);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case START_SCAN: {
              const bool success = chreBleStartScanAsync(
                  CHRE_BLE_SCAN_MODE_BACKGROUND, 0, nullptr);
              TestEventQueueSingleton::get()->pushEvent(START_SCAN, success);
              break;
            }

            case STOP_SCAN: {
              const bool success = chreBleStopScanAsync();
              TestEventQueueSingleton::get()->pushEvent(STOP_SCAN, success);
              break;
            }
          }
        }
      }
    };
  };

  auto app = loadNanoapp<App>();
  bool success;

  sendEventToNanoapp(app, START_SCAN);
  waitForEvent(START_SCAN, &success);
  EXPECT_TRUE(success);
  waitForEvent(SCAN_STARTED);

  sendEventToNanoapp(app, START_SCAN);
  waitForEvent(START_SCAN, &success);
  EXPECT_TRUE(success);
  waitForEvent(SCAN_STARTED);
  waitForEvent(CHRE_EVENT_BLE_ADVERTISEMENT);

  sendEventToNanoapp(app, STOP_SCAN);
  waitForEvent(STOP_SCAN, &success);
  EXPECT_TRUE(success);
  waitForEvent(SCAN_STOPPED);
}

/**
 * This test validates that a nanoapp can request to stop a scan twice without
 * any ongoing scan existing. It asserts that the nanoapp did not receive any
 * advertisment events because a scan was never started.
 */
TEST_F(TestBase, BleStopTwiceScanTest) {
  CREATE_CHRE_TEST_EVENT(SCAN_STARTED, 1);
  CREATE_CHRE_TEST_EVENT(STOP_SCAN, 2);
  CREATE_CHRE_TEST_EVENT(SCAN_STOPPED, 3);

  struct App : public BleTestNanoapp {
    void (*handleEvent)(uint32_t, uint16_t, const void *) =
        [](uint32_t, uint16_t eventType, const void *eventData) {
          switch (eventType) {
            case CHRE_EVENT_BLE_ASYNC_RESULT: {
              auto *event =
                  static_cast<const struct chreAsyncResult *>(eventData);
              if (event->errorCode == CHRE_ERROR_NONE) {
                uint16_t type =
                    (event->requestType == CHRE_BLE_REQUEST_TYPE_START_SCAN)
                        ? SCAN_STARTED
                        : SCAN_STOPPED;
                TestEventQueueSingleton::get()->pushEvent(type);
              }
              break;
            }

            case CHRE_EVENT_BLE_ADVERTISEMENT: {
              FATAL_ERROR("No advertisement expected");
              break;
            }

            case CHRE_EVENT_TEST_EVENT: {
              auto event = static_cast<const TestEvent *>(eventData);
              switch (event->type) {
                case STOP_SCAN: {
                  const bool success = chreBleStopScanAsync();
                  TestEventQueueSingleton::get()->pushEvent(STOP_SCAN, success);
                  break;
                }
              }
            }
          }
        };
  };

  auto app = loadNanoapp<App>();
  bool success;

  sendEventToNanoapp(app, STOP_SCAN);
  waitForEvent(STOP_SCAN, &success);
  EXPECT_TRUE(success);
  waitForEvent(SCAN_STOPPED);

  sendEventToNanoapp(app, STOP_SCAN);
  waitForEvent(STOP_SCAN, &success);
  EXPECT_TRUE(success);

  waitForEvent(SCAN_STOPPED);
  unloadNanoapp(app);
}

/**
 * This test verifies the following BLE settings behavior:
 * 1) Nanoapp makes BLE scan request
 * 2) Toggle BLE setting -> disabled
 * 3) Toggle BLE setting -> enabled.
 * 4) Verify things resume.
 */
TEST_F(TestBase, BleSettingChangeTest) {
  CREATE_CHRE_TEST_EVENT(START_SCAN, 0);
  CREATE_CHRE_TEST_EVENT(SCAN_STARTED, 1);
  CREATE_CHRE_TEST_EVENT(SCAN_STOPPED, 3);

  struct App : public BleTestNanoapp {
    void (*handleEvent)(uint32_t, uint16_t,
                        const void *) = [](uint32_t, uint16_t eventType,
                                           const void *eventData) {
      switch (eventType) {
        case CHRE_EVENT_BLE_ASYNC_RESULT: {
          auto *event = static_cast<const struct chreAsyncResult *>(eventData);
          if (event->errorCode == CHRE_ERROR_NONE) {
            uint16_t type =
                (event->requestType == CHRE_BLE_REQUEST_TYPE_START_SCAN)
                    ? SCAN_STARTED
                    : SCAN_STOPPED;
            TestEventQueueSingleton::get()->pushEvent(type);
          }
          break;
        }

        case CHRE_EVENT_BLE_ADVERTISEMENT: {
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_BLE_ADVERTISEMENT);
          break;
        }

        case CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE: {
          auto *event =
              static_cast<const chreUserSettingChangedEvent *>(eventData);
          bool enabled =
              (event->settingState == CHRE_USER_SETTING_STATE_ENABLED);
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE, enabled);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case START_SCAN: {
              const bool success = chreBleStartScanAsync(
                  CHRE_BLE_SCAN_MODE_BACKGROUND, 0, nullptr);
              TestEventQueueSingleton::get()->pushEvent(START_SCAN, success);
              break;
            }
          }
        }
      }
    };
  };

  auto app = loadNanoapp<App>();
  bool success;

  sendEventToNanoapp(app, START_SCAN);
  waitForEvent(START_SCAN, &success);
  EXPECT_TRUE(success);

  waitForEvent(SCAN_STARTED);
  waitForEvent(CHRE_EVENT_BLE_ADVERTISEMENT);

  EventLoopManagerSingleton::get()->getSettingManager().postSettingChange(
      Setting::BLE_AVAILABLE, false /* enabled */);
  bool enabled;
  waitForEvent(CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE, &enabled);
  EXPECT_FALSE(enabled);
  EXPECT_FALSE(
      EventLoopManagerSingleton::get()->getSettingManager().getSettingEnabled(
          Setting::BLE_AVAILABLE));
  std::this_thread::sleep_for(std::chrono::milliseconds(100));
  EXPECT_FALSE(chrePalIsBleEnabled());

  EventLoopManagerSingleton::get()->getSettingManager().postSettingChange(
      Setting::BLE_AVAILABLE, true /* enabled */);
  waitForEvent(CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE, &enabled);
  EXPECT_TRUE(enabled);
  EXPECT_TRUE(
      EventLoopManagerSingleton::get()->getSettingManager().getSettingEnabled(
          Setting::BLE_AVAILABLE));
  waitForEvent(CHRE_EVENT_BLE_ADVERTISEMENT);
  EXPECT_TRUE(chrePalIsBleEnabled());
}

/**
 * Test that a nanoapp receives a function disabled error if it attempts to
 * start a scan when the BLE setting is disabled.
 */
TEST_F(TestBase, BleSettingDisabledStartScanTest) {
  CREATE_CHRE_TEST_EVENT(START_SCAN, 0);

  struct App : public BleTestNanoapp {
    void (*handleEvent)(uint32_t, uint16_t,
                        const void *) = [](uint32_t, uint16_t eventType,
                                           const void *eventData) {
      switch (eventType) {
        case CHRE_EVENT_BLE_ASYNC_RESULT: {
          auto *event = static_cast<const struct chreAsyncResult *>(eventData);
          if (event->errorCode == CHRE_ERROR_FUNCTION_DISABLED) {
            TestEventQueueSingleton::get()->pushEvent(
                CHRE_EVENT_BLE_ASYNC_RESULT);
          }
          break;
        }

        case CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE: {
          auto *event =
              static_cast<const chreUserSettingChangedEvent *>(eventData);
          bool enabled =
              (event->settingState == CHRE_USER_SETTING_STATE_ENABLED);
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE, enabled);
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case START_SCAN: {
              const bool success = chreBleStartScanAsync(
                  CHRE_BLE_SCAN_MODE_BACKGROUND, 0, nullptr);
              TestEventQueueSingleton::get()->pushEvent(START_SCAN, success);
              break;
            }
          }
        }
      }
    };
  };

  auto app = loadNanoapp<App>();

  EventLoopManagerSingleton::get()->getSettingManager().postSettingChange(
      Setting::BLE_AVAILABLE, false /* enable */);

  bool enabled;
  waitForEvent(CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE, &enabled);
  EXPECT_FALSE(enabled);

  bool success;
  sendEventToNanoapp(app, START_SCAN);
  waitForEvent(START_SCAN, &success);
  EXPECT_TRUE(success);
  waitForEvent(CHRE_EVENT_BLE_ASYNC_RESULT);
}

/**
 * Test that a nanoapp receives a success response when it attempts to stop a
 * BLE scan while the BLE setting is disabled.
 */
TEST_F(TestBase, BleSettingDisabledStopScanTest) {
  CREATE_CHRE_TEST_EVENT(SCAN_STARTED, 1);
  CREATE_CHRE_TEST_EVENT(STOP_SCAN, 2);
  CREATE_CHRE_TEST_EVENT(SCAN_STOPPED, 3);

  struct App : public BleTestNanoapp {
    void (*handleEvent)(uint32_t, uint16_t, const void *) =
        [](uint32_t, uint16_t eventType, const void *eventData) {
          switch (eventType) {
            case CHRE_EVENT_BLE_ASYNC_RESULT: {
              auto *event =
                  static_cast<const struct chreAsyncResult *>(eventData);
              if (event->errorCode == CHRE_ERROR_NONE) {
                uint16_t type =
                    (event->requestType == CHRE_BLE_REQUEST_TYPE_START_SCAN)
                        ? SCAN_STARTED
                        : SCAN_STOPPED;
                TestEventQueueSingleton::get()->pushEvent(type);
              }
              break;
            }

            case CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE: {
              auto *event =
                  static_cast<const chreUserSettingChangedEvent *>(eventData);
              bool enabled =
                  (event->settingState == CHRE_USER_SETTING_STATE_ENABLED);
              TestEventQueueSingleton::get()->pushEvent(
                  CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE, enabled);
              break;
            }

            case CHRE_EVENT_TEST_EVENT: {
              auto event = static_cast<const TestEvent *>(eventData);
              switch (event->type) {
                case STOP_SCAN: {
                  const bool success = chreBleStopScanAsync();
                  TestEventQueueSingleton::get()->pushEvent(STOP_SCAN, success);
                  break;
                }
              }
            }
          }
        };
  };

  auto app = loadNanoapp<App>();

  EventLoopManagerSingleton::get()->getSettingManager().postSettingChange(
      Setting::BLE_AVAILABLE, false /* enable */);

  bool enabled;
  waitForEvent(CHRE_EVENT_SETTING_CHANGED_BLE_AVAILABLE, &enabled);
  EXPECT_FALSE(enabled);

  bool success;
  sendEventToNanoapp(app, STOP_SCAN);
  waitForEvent(STOP_SCAN, &success);
  EXPECT_TRUE(success);
  waitForEvent(SCAN_STOPPED);
}

}  // namespace
}  // namespace chre

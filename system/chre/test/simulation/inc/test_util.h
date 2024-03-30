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

#ifndef CHRE_SIMULATION_TEST_UTIL_H_
#define CHRE_SIMULATION_TEST_UTIL_H_

#include <chre/nanoapp.h>
#include <cstdint>

#include "chre/core/event_loop_manager.h"
#include "chre/core/nanoapp.h"
#include "chre/util/unique_ptr.h"
#include "test_event.h"
#include "test_event_queue.h"

namespace chre {

struct TestNanoapp;

/**
 * @return the statically loaded nanoapp based on the arguments.
 *
 * @see chreNslNanoappInfo for param descriptions.
 */
UniquePtr<Nanoapp> createStaticNanoapp(
    const char *name, uint64_t appId, uint32_t appVersion, uint32_t appPerms,
    decltype(nanoappStart) *startFunc,
    decltype(nanoappHandleEvent) *handleEventFunc,
    decltype(nanoappEnd) *endFunc);

/**
 * Deletes memory allocated by createStaticNanoapp.
 *
 * This function must be called when the nanoapp is no more used.
 */
void deleteNanoappInfos();

/**
 * Default CHRE nanoapp entry points that don't do anything.
 */
bool defaultNanoappStart();
void defaultNanoappHandleEvent(uint32_t senderInstanceId, uint16_t eventType,
                               const void *eventData);
void defaultNanoappEnd();

/**
 * Create static nanoapp and load it in CHRE.
 *
 * This function returns after the nanoapp start has been executed.
 *
 * @see createStatic Nanoapp.
 */
void loadNanoapp(const char *name, uint64_t appId, uint32_t appVersion,
                 uint32_t appPerms, decltype(nanoappStart) *startFunc,
                 decltype(nanoappHandleEvent) *handleEventFunc,
                 decltype(nanoappEnd) *endFunc);

/**
 * Create a static nanoapp and load it in CHRE.
 *
 * This function returns after the nanoapp start has been executed.
 *
 * @return An instance of the TestNanoapp.
 */
template <class Nanoapp>
Nanoapp loadNanoapp() {
  static_assert(std::is_base_of<TestNanoapp, Nanoapp>::value);
  Nanoapp app;
  loadNanoapp(app.name, app.id, app.version, app.perms, app.start,
              app.handleEvent, app.end);

  return app;
}

/**
 * Unload a test nanoapp.
 *
 * This function returns after the nanoapp end has been executed.
 *
 * @param app An instance of TestNanoapp.
 */
template <class Nanoapp>
void unloadNanoapp(Nanoapp app) {
  static_assert(std::is_base_of<TestNanoapp, Nanoapp>::value);
  unloadNanoapp(app.id);
}

/**
 * Unload nanoapp corresponding to appId.
 *
 * This function returns after the nanoapp end has been executed.
 *
 * @param appId App Id of nanoapp to be unloaded.
 */
template <>
void unloadNanoapp<uint64_t>(uint64_t appId);

/**
 * A convenience deferred callback function that can be used to start an already
 * loaded nanoapp.
 *
 * @param type The callback type.
 * @param nanoapp A pointer to the nanoapp that is already loaded.
 */
void testFinishLoadingNanoappCallback(SystemCallbackType type,
                                      UniquePtr<Nanoapp> &&nanoapp);

/**
 * A convenience deferred callback function to unload a nanoapp.
 *
 * @param type The callback type.
 * @param data The data containing the appId.
 * @param extraData Extra data.
 */
void testFinishUnloadingNanoappCallback(uint16_t type, void *data,
                                        void *extraData);

/**
 * Test nanoapp.
 *
 * Tests typically inherit this struct to test the nanoapp behavior.
 * The bulk of the code should be in the handleEvent closure to respond to
 * events sent to the nanoapp by the platform and by the sendEventToNanoapp
 * function. start and end can be use to setup and cleanup the test environment
 * around each test.
 *
 * Note: end is only executed when the nanoapp is explicitly unloaded.
 */
struct TestNanoapp {
  const char *name = "Test";
  uint64_t id = 0x0123456789abcdef;
  uint32_t version = 0;
  uint32_t perms = NanoappPermissions::CHRE_PERMS_NONE;

  bool (*start)() = []() { return true; };

  void (*handleEvent)(uint32_t senderInstanceId, uint16_t eventType,
                      const void *eventData) = [](uint32_t, uint16_t,
                                                  const void *) {};

  void (*end)() = []() {};
};

/**
 * Deallocate the memory allocated for a TestEvent.
 */
void freeTestEventDataCallback(uint16_t /*eventType*/, void *eventData);

/**
 * Sends a message to a nanoapp.
 *
 * This function is typically used to execute code in the context of the
 * nanoapp in its handleEvent method.
 *
 * @param app An instance of TestNanoapp.
 * @param eventType The event to send.
 */
template <class Nanoapp>
void sendEventToNanoapp(const Nanoapp &app, uint16_t eventType) {
  static_assert(std::is_base_of<TestNanoapp, Nanoapp>::value);
  uint16_t instanceId;
  if (EventLoopManagerSingleton::get()
          ->getEventLoop()
          .findNanoappInstanceIdByAppId(app.id, &instanceId)) {
    auto event = memoryAlloc<TestEvent>();
    ASSERT_NE(event, nullptr);
    event->type = eventType;
    EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
        CHRE_EVENT_TEST_EVENT, static_cast<void *>(event),
        freeTestEventDataCallback, instanceId);

  } else {
    LOGE("No instance found for nanoapp id = 0x%016" PRIx64, app.id);
  }
}

/**
 * Sends a message to a nanoapp with data.
 *
 * This function is typically used to execute code in the context of the
 * nanoapp in its handleEvent method.
 *
 * The nanoapp handleEvent function will receive a a TestEvent instance
 * populated with the eventType and a pointer to as copy of the evenData as
 * a CHRE_EVENT_TEST_EVENT event.
 *
 * @param app An instance of TestNanoapp.
 * @param eventType The event to send.
 * @param eventData The data to send.
 */
template <class Nanoapp, class T>
void sendEventToNanoapp(const Nanoapp &app, uint16_t eventType,
                        const T &eventData) {
  static_assert(std::is_base_of<TestNanoapp, Nanoapp>::value);
  static_assert(std::is_trivial<T>::value);
  uint16_t instanceId;
  if (EventLoopManagerSingleton::get()
          ->getEventLoop()
          .findNanoappInstanceIdByAppId(app.id, &instanceId)) {
    auto event = memoryAlloc<TestEvent>();
    ASSERT_NE(event, nullptr);
    event->type = eventType;
    auto ptr = memoryAlloc<T>();
    ASSERT_NE(ptr, nullptr);
    *ptr = eventData;
    event->data = ptr;
    EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
        CHRE_EVENT_TEST_EVENT, static_cast<void *>(event),
        freeTestEventDataCallback, instanceId);
  } else {
    LOGE("No instance found for nanoapp id = 0x%016" PRIx64, app.id);
  }
}

}  // namespace chre

#endif  // CHRE_SIMULATION_TEST_UTIL_H_

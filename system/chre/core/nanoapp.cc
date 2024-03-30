/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "chre/core/nanoapp.h"

#include "chre/core/event_loop_manager.h"
#include "chre/platform/assert.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/log.h"
#include "chre/util/system/debug_dump.h"
#include "chre_api/chre/gnss.h"
#include "chre_api/chre/version.h"

#include <algorithm>

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5
#define CHRE_GNSS_MEASUREMENT_BACK_COMPAT_ENABLED
#endif

namespace chre {

constexpr size_t Nanoapp::kMaxSizeWakeupBuckets;

Nanoapp::Nanoapp() {
  // Push first bucket onto wakeup bucket queue
  cycleWakeupBuckets(1);
}

bool Nanoapp::isRegisteredForBroadcastEvent(const Event *event) const {
  bool registered = false;
  uint16_t eventType = event->eventType;
  uint16_t targetGroupIdMask = event->targetAppGroupMask;

  // The host endpoint notification is a special case, because it requires
  // explicit registration using host endpoint IDs rather than masks.
  if (eventType == CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION) {
    const auto *data =
        static_cast<const chreHostEndpointNotification *>(event->eventData);
    registered = isRegisteredForHostEndpointNotifications(data->hostEndpointId);
  } else {
    size_t foundIndex = registrationIndex(eventType);
    if (foundIndex < mRegisteredEvents.size()) {
      const EventRegistration &reg = mRegisteredEvents[foundIndex];
      if (targetGroupIdMask & reg.groupIdMask) {
        registered = true;
      }
    }
  }
  return registered;
}

void Nanoapp::registerForBroadcastEvent(uint16_t eventType,
                                        uint16_t groupIdMask) {
  size_t foundIndex = registrationIndex(eventType);
  if (foundIndex < mRegisteredEvents.size()) {
    mRegisteredEvents[foundIndex].groupIdMask |= groupIdMask;
  } else if (!mRegisteredEvents.push_back(
                 EventRegistration(eventType, groupIdMask))) {
    FATAL_ERROR_OOM();
  }
}

void Nanoapp::unregisterForBroadcastEvent(uint16_t eventType,
                                          uint16_t groupIdMask) {
  size_t foundIndex = registrationIndex(eventType);
  if (foundIndex < mRegisteredEvents.size()) {
    EventRegistration &reg = mRegisteredEvents[foundIndex];
    reg.groupIdMask &= ~groupIdMask;
    if (reg.groupIdMask == 0) {
      mRegisteredEvents.erase(foundIndex);
    }
  }
}

void Nanoapp::configureNanoappInfoEvents(bool enable) {
  if (enable) {
    registerForBroadcastEvent(CHRE_EVENT_NANOAPP_STARTED);
    registerForBroadcastEvent(CHRE_EVENT_NANOAPP_STOPPED);
  } else {
    unregisterForBroadcastEvent(CHRE_EVENT_NANOAPP_STARTED);
    unregisterForBroadcastEvent(CHRE_EVENT_NANOAPP_STOPPED);
  }
}

void Nanoapp::configureHostSleepEvents(bool enable) {
  if (enable) {
    registerForBroadcastEvent(CHRE_EVENT_HOST_AWAKE);
    registerForBroadcastEvent(CHRE_EVENT_HOST_ASLEEP);
  } else {
    unregisterForBroadcastEvent(CHRE_EVENT_HOST_AWAKE);
    unregisterForBroadcastEvent(CHRE_EVENT_HOST_ASLEEP);
  }
}

void Nanoapp::configureDebugDumpEvent(bool enable) {
  if (enable) {
    registerForBroadcastEvent(CHRE_EVENT_DEBUG_DUMP);
  } else {
    unregisterForBroadcastEvent(CHRE_EVENT_DEBUG_DUMP);
  }
}

void Nanoapp::configureUserSettingEvent(uint8_t setting, bool enable) {
  if (enable) {
    registerForBroadcastEvent(CHRE_EVENT_SETTING_CHANGED_FIRST_EVENT + setting);
  } else {
    unregisterForBroadcastEvent(CHRE_EVENT_SETTING_CHANGED_FIRST_EVENT +
                                setting);
  }
}

void Nanoapp::processEvent(Event *event) {
  if (event->eventType == CHRE_EVENT_GNSS_DATA) {
    handleGnssMeasurementDataEvent(event);
  } else {
    handleEvent(event->senderInstanceId, event->eventType, event->eventData);
  }
}

void Nanoapp::blameHostWakeup() {
  if (mWakeupBuckets.back() < UINT16_MAX) ++mWakeupBuckets.back();
  if (mNumWakeupsSinceBoot < UINT32_MAX) ++mNumWakeupsSinceBoot;
}

void Nanoapp::cycleWakeupBuckets(size_t numBuckets) {
  numBuckets = std::min(numBuckets, kMaxSizeWakeupBuckets);
  for (size_t i = 0; i < numBuckets; ++i) {
    if (mWakeupBuckets.full()) {
      mWakeupBuckets.erase(0);
    }
    mWakeupBuckets.push_back(0);
  }
}

void Nanoapp::logStateToBuffer(DebugDumpWrapper &debugDump) const {
  debugDump.print(" Id=%" PRIu16 " 0x%016" PRIx64 " ", getInstanceId(),
                  getAppId());
  PlatformNanoapp::logStateToBuffer(debugDump);
  debugDump.print(" v%" PRIu32 ".%" PRIu32 ".%" PRIu32 " tgtAPI=%" PRIu32
                  ".%" PRIu32 " curAlloc=%zu peakAlloc=%zu",
                  CHRE_EXTRACT_MAJOR_VERSION(getAppVersion()),
                  CHRE_EXTRACT_MINOR_VERSION(getAppVersion()),
                  CHRE_EXTRACT_PATCH_VERSION(getAppVersion()),
                  CHRE_EXTRACT_MAJOR_VERSION(getTargetApiVersion()),
                  CHRE_EXTRACT_MINOR_VERSION(getTargetApiVersion()),
                  getTotalAllocatedBytes(), getPeakAllocatedBytes());
  debugDump.print(" hostWakeups=[ cur->");
  // Get buckets latest -> earliest except last one
  for (size_t i = mWakeupBuckets.size() - 1; i > 0; --i) {
    debugDump.print("%" PRIu16 ", ", mWakeupBuckets[i]);
  }
  // Earliest bucket gets no comma
  debugDump.print("%" PRIu16 " ]", mWakeupBuckets.front());

  // Print total wakeups since boot
  debugDump.print(" totWakeups=%" PRIu32 " ]\n", mNumWakeupsSinceBoot);
}

bool Nanoapp::permitPermissionUse(uint32_t permission) const {
  return !supportsAppPermissions() ||
         ((getAppPermissions() & permission) == permission);
}

size_t Nanoapp::registrationIndex(uint16_t eventType) const {
  size_t foundIndex = 0;
  for (; foundIndex < mRegisteredEvents.size(); ++foundIndex) {
    const EventRegistration &reg = mRegisteredEvents[foundIndex];
    if (reg.eventType == eventType) {
      break;
    }
  }
  return foundIndex;
}

void Nanoapp::handleGnssMeasurementDataEvent(const Event *event) {
#ifdef CHRE_GNSS_MEASUREMENT_BACK_COMPAT_ENABLED
  const struct chreGnssDataEvent *data =
      static_cast<const struct chreGnssDataEvent *>(event->eventData);
  if (getTargetApiVersion() < CHRE_API_VERSION_1_5 &&
      data->measurement_count > CHRE_GNSS_MAX_MEASUREMENT_PRE_1_5) {
    chreGnssDataEvent localEvent;
    memcpy(&localEvent, data, sizeof(struct chreGnssDataEvent));
    localEvent.measurement_count = CHRE_GNSS_MAX_MEASUREMENT_PRE_1_5;
    handleEvent(event->senderInstanceId, event->eventType, &localEvent);
  } else
#endif  // CHRE_GNSS_MEASUREMENT_BACK_COMPAT_ENABLED
  {
    handleEvent(event->senderInstanceId, event->eventType, event->eventData);
  }
}

bool Nanoapp::configureHostEndpointNotifications(uint16_t hostEndpointId,
                                                 bool enable) {
  bool success = true;
  bool registered = isRegisteredForHostEndpointNotifications(hostEndpointId);
  if (enable && !registered) {
    success = mRegisteredHostEndpoints.push_back(hostEndpointId);
    if (!success) {
      LOG_OOM();
    }
  } else if (!enable && registered) {
    size_t index = mRegisteredHostEndpoints.find(hostEndpointId);
    mRegisteredHostEndpoints.erase(index);
  }

  return success;
}

bool Nanoapp::publishRpcServices(struct chreNanoappRpcService *services,
                                 size_t numServices) {
  // TODO(b/204426460): Validate this code is only called from nanoappStart().
  bool success = true;
  for (size_t i = 0; i < numServices; i++) {
    if (!mRpcServices.push_back(services[i])) {
      LOG_OOM();
      success = false;
    }
  }

  return success;
}

void Nanoapp::linkHeapBlock(HeapBlockHeader *header) {
  header->data.next = mFirstHeader;
  mFirstHeader = header;
}

void Nanoapp::unlinkHeapBlock(HeapBlockHeader *header) {
  if (mFirstHeader == nullptr) {
    // The list is empty.
    return;
  }

  if (header == mFirstHeader) {
    mFirstHeader = header->data.next;
    return;
  }

  HeapBlockHeader *previous = mFirstHeader;
  HeapBlockHeader *current = mFirstHeader->data.next;

  while (current != nullptr) {
    if (current == header) {
      previous->data.next = current->data.next;
      break;
    }
    previous = current;
    current = current->data.next;
  }
}

}  // namespace chre

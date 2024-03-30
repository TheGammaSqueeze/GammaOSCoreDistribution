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

#include <cinttypes>

#include "chre.h"
#include "chre/util/macros.h"
#include "chre/util/memory.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/time.h"
#include "chre/util/unique_ptr.h"

#ifdef CHRE_NANOAPP_INTERNAL
namespace chre {
namespace {
#endif  // CHRE_NANOAPP_INTERNAL

bool gAsyncResultReceived = false;
uint32_t gTimerHandle = 0;

//! A fake/unused cookie to pass into the session async and timer request.
const uint32_t kBleCookie = 0x1337;
//! The interval in seconds between updates.
const chreBleScanMode kScanModes[] = {CHRE_BLE_SCAN_MODE_BACKGROUND,
                                      CHRE_BLE_SCAN_MODE_FOREGROUND,
                                      CHRE_BLE_SCAN_MODE_AGGRESSIVE};

enum ScanRequestType {
  NO_FILTER = 0,
  SERVICE_DATA_16 = 1,
  STOP_SCAN = 2,
};

chreBleScanFilter *getBleScanFilter(ScanRequestType &scanRequestType) {
  chre::UniquePtr<chreBleScanFilter> filter =
      chre::MakeUniqueZeroFill<chreBleScanFilter>();
  filter->rssiThreshold = CHRE_BLE_RSSI_THRESHOLD_NONE;
  filter->scanFilterCount = 1;
  chre::UniquePtr<chreBleGenericFilter> scanFilter =
      chre::MakeUniqueZeroFill<chreBleGenericFilter>();
  switch (scanRequestType) {
    case NO_FILTER:
      filter = nullptr;
      scanRequestType = SERVICE_DATA_16;
      break;
    case SERVICE_DATA_16:
      scanFilter->type = CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16;
      scanFilter->len = 2;
      filter->scanFilters = scanFilter.release();
      scanRequestType = STOP_SCAN;
      break;
    case STOP_SCAN:
      break;
  }
  return filter.release();
}

void makeBleScanRequest() {
  static uint8_t scanModeIndex = 0;
  static ScanRequestType scanRequestType = NO_FILTER;
  if (scanRequestType != STOP_SCAN) {
    chreBleScanMode mode = kScanModes[scanModeIndex];
    uint32_t reportDelayMs = 0;
    chreBleScanFilter *filter = getBleScanFilter(scanRequestType);
    LOGI("Sending BLE start scan request to PAL with parameters:");
    LOGI("  mode=%" PRIu8, kScanModes[scanModeIndex]);
    LOGI("  reportDelayMs=%" PRIu32, reportDelayMs);
    if (filter != nullptr) {
      LOGI("  rssiThreshold=%" PRIu32, filter->rssiThreshold);
      LOGI("  scanFilterType=%" PRIx8, filter->scanFilters[0].type);
      LOGI("  scanFilterLen=%" PRIu8, filter->scanFilters[0].len);
      LOGI("  scanFilterData=%s", filter->scanFilters[0].data);
      LOGI("  scanFilterDataMask=%s", filter->scanFilters[0].dataMask);
    }
    if (chreBleStartScanAsync(mode, 0, nullptr)) {
      LOGI("BLE start scan request sent to PAL");
    } else {
      LOGE("Error sending BLE start scan request sent to PAL");
    }
    if (filter != nullptr) {
      if (filter->scanFilters != nullptr) {
        chre::memoryFree(
            const_cast<chreBleGenericFilter *>(filter->scanFilters));
      }
      chre::memoryFree(filter);
    }
  } else {
    if (chreBleStopScanAsync()) {
      LOGI("BLE stop scan request sent to PAL");
    } else {
      LOGE("Error sending BLE stop scan request sent to PAL");
    }
    scanRequestType = NO_FILTER;
    scanModeIndex = (scanModeIndex + 1) % ARRAY_SIZE(kScanModes);
  }
  gTimerHandle = chreTimerSet(CHRE_ASYNC_RESULT_TIMEOUT_NS, /* 5 sec */
                              &kBleCookie, true /* oneShot */);
}

void handleAdvertismentEvent(const chreBleAdvertisementEvent *event) {
  for (uint8_t i = 0; i < event->numReports; i++) {
    LOGI("BLE Report %" PRIu8, i + 1);
    LOGI("Scan data:");
    const uint8_t *data = event->reports[i].data;
    for (uint8_t j = 0; j < event->reports[i].dataLength; j++) {
      LOGI("  %" PRIx8, data[j]);
    }
  }
}

void handleAsyncResultEvent(const chreAsyncResult *result) {
  gAsyncResultReceived = true;
  const char *requestType =
      result->requestType == CHRE_BLE_REQUEST_TYPE_START_SCAN ? "start"
                                                              : "stop";
  if (result->success) {
    LOGI("BLE %s scan success", requestType);
  } else {
    LOGE("BLE %s scan failure: %" PRIu8, requestType, result->errorCode);
  }
}

void handleTimerEvent(const void *eventData) {
  static uint32_t timerCount = 1;
  if (eventData == &kBleCookie) {
    LOGI("BLE timer event received, count %" PRIu32, timerCount++);
    if (!gAsyncResultReceived) {
      LOGE("BLE async result not received");
    }
    gAsyncResultReceived = false;
    makeBleScanRequest();
  } else {
    LOGE("Invalid timer cookie");
  }
}

bool nanoappStart(void) {
  LOGI("nanoapp started");
  makeBleScanRequest();
  return true;
}

void nanoappEnd(void) {
  if (!chreBleStopScanAsync()) {
    LOGE("Error sending BLE stop scan request sent to PAL");
  }
  if (!chreTimerCancel(gTimerHandle)) {
    LOGE("Error canceling timer");
  }
  LOGI("nanoapp stopped");
}

void nanoappHandleEvent(uint32_t /* sender_instance_id */, uint16_t event_type,
                        const void *event_data) {
  if (event_type == CHRE_EVENT_BLE_ADVERTISEMENT) {
    handleAdvertismentEvent(
        static_cast<const chreBleAdvertisementEvent *>(event_data));
  } else if (event_type == CHRE_EVENT_BLE_ASYNC_RESULT) {
    handleAsyncResultEvent(static_cast<const chreAsyncResult *>(event_data));
  } else if (event_type == CHRE_EVENT_TIMER) {
    handleTimerEvent(event_data);
  }
}

#ifdef CHRE_NANOAPP_INTERNAL
}  // anonymous namespace
}  // namespace chre

#include "chre/platform/static_nanoapp_init.h"
#include "chre/util/nanoapp/app_id.h"
#include "chre/util/system/napp_permissions.h"

CHRE_STATIC_NANOAPP_INIT(BleWorld, kBleWorldAppId, 0,
                         NanoappPermissions::CHRE_PERMS_BLE);
#endif  // CHRE_NANOAPP_INTERNAL

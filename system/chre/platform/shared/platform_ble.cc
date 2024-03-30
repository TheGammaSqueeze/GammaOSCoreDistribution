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

#include "chre/platform/platform_ble.h"

#include <cinttypes>

#include "chre/core/event_loop_manager.h"
#include "chre/platform/log.h"
#include "chre/platform/shared/pal_system_api.h"
#include "chre_api/chre/ble.h"

namespace chre {

const chrePalBleCallbacks PlatformBleBase::sBleCallbacks = {
    PlatformBleBase::requestStateResync,
    PlatformBleBase::scanStatusChangeCallback,
    PlatformBleBase::advertisingEventCallback,
};

PlatformBle::~PlatformBle() {
  if (mBleApi != nullptr) {
    LOGD("Platform BLE closing");
    prePalApiCall(PalType::BLE);
    mBleApi->close();
    LOGD("Platform BLE closed");
  }
}

void PlatformBle::init() {
  prePalApiCall(PalType::BLE);
  mBleApi = chrePalBleGetApi(CHRE_PAL_BLE_API_CURRENT_VERSION);
  if (mBleApi != nullptr) {
    if (!mBleApi->open(&gChrePalSystemApi, &sBleCallbacks)) {
      LOGE("BLE PAL open returned false");
      mBleApi = nullptr;
    } else {
      LOGD("Opened BLE PAL version 0x%08" PRIx32, mBleApi->moduleVersion);
    }
  } else {
    LOGW("Requested BLE PAL (version 0x%08" PRIx32 ") not found",
         CHRE_PAL_BLE_API_CURRENT_VERSION);
  }
}

uint32_t PlatformBle::getCapabilities() {
  if (mBleApi != nullptr) {
    prePalApiCall(PalType::BLE);
    return mBleApi->getCapabilities();
  } else {
    return CHRE_BLE_CAPABILITIES_NONE;
  }
}

uint32_t PlatformBle::getFilterCapabilities() {
  if (mBleApi != nullptr) {
    prePalApiCall(PalType::BLE);
    return mBleApi->getFilterCapabilities();
  } else {
    return CHRE_BLE_FILTER_CAPABILITIES_NONE;
  }
}

bool PlatformBle::startScanAsync(chreBleScanMode mode, uint32_t reportDelayMs,
                                 const struct chreBleScanFilter *filter) {
  if (mBleApi != nullptr) {
    prePalApiCall(PalType::BLE);
    return mBleApi->startScan(mode, reportDelayMs, filter);
  } else {
    return false;
  }
}

bool PlatformBle::stopScanAsync() {
  if (mBleApi != nullptr) {
    prePalApiCall(PalType::BLE);
    return mBleApi->stopScan();
  } else {
    return false;
  }
}

void PlatformBle::releaseAdvertisingEvent(
    struct chreBleAdvertisementEvent *event) {
  prePalApiCall(PalType::BLE);
  mBleApi->releaseAdvertisingEvent(event);
}

void PlatformBleBase::requestStateResync() {
  EventLoopManagerSingleton::get()
      ->getBleRequestManager()
      .handleRequestStateResyncCallback();
}

void PlatformBleBase::scanStatusChangeCallback(bool enabled,
                                               uint8_t errorCode) {
  EventLoopManagerSingleton::get()->getBleRequestManager().handlePlatformChange(
      enabled, errorCode);
}

void PlatformBleBase::advertisingEventCallback(
    struct chreBleAdvertisementEvent *event) {
  EventLoopManagerSingleton::get()
      ->getBleRequestManager()
      .handleAdvertisementEvent(event);
}

}  // namespace chre

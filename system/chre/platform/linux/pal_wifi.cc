/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "chre/pal/wifi.h"

#include "chre/util/memory.h"
#include "chre/util/unique_ptr.h"

#include "chre/platform/linux/pal_nan.h"

#include <chrono>
#include <cinttypes>
#include <thread>

/**
 * A simulated implementation of the WiFi PAL for the linux platform.
 */
namespace {
const struct chrePalSystemApi *gSystemApi = nullptr;
const struct chrePalWifiCallbacks *gCallbacks = nullptr;

//! Thread to deliver asynchronous WiFi scan results after a CHRE request.
std::thread gScanEventsThread;

//! Thread to use when delivering a scan monitor status update.
std::thread gScanMonitorStatusThread;

//! Whether scan monitoring is active.
bool gScanMonitoringActive = false;

void sendScanResponse() {
  gCallbacks->scanResponseCallback(true, CHRE_ERROR_NONE);

  auto event = chre::MakeUniqueZeroFill<struct chreWifiScanEvent>();
  auto result = chre::MakeUniqueZeroFill<struct chreWifiScanResult>();
  event->resultCount = 1;
  event->resultTotal = 1;
  event->referenceTime = gSystemApi->getCurrentTime();
  event->results = result.release();

  gCallbacks->scanEventCallback(event.release());
}

void sendScanMonitorResponse(bool enable) {
  gCallbacks->scanMonitorStatusChangeCallback(enable, CHRE_ERROR_NONE);
}

void stopScanEventThreads() {
  if (gScanEventsThread.joinable()) {
    gScanEventsThread.join();
  }
}

void stopScanMonitorThreads() {
  if (gScanMonitorStatusThread.joinable()) {
    gScanMonitorStatusThread.join();
  }
}

uint32_t chrePalWifiGetCapabilities() {
  return CHRE_WIFI_CAPABILITIES_SCAN_MONITORING |
         CHRE_WIFI_CAPABILITIES_ON_DEMAND_SCAN | CHRE_WIFI_CAPABILITIES_NAN_SUB;
}

bool chrePalWifiConfigureScanMonitor(bool enable) {
  stopScanMonitorThreads();

  gScanMonitorStatusThread = std::thread(sendScanMonitorResponse, enable);
  gScanMonitoringActive = enable;

  return true;
}

bool chrePalWifiApiRequestScan(const struct chreWifiScanParams * /* params */) {
  stopScanEventThreads();

  gScanEventsThread = std::thread(sendScanResponse);

  return true;
}

bool chrePalWifiApiRequestRanging(
    const struct chreWifiRangingParams * /* params */) {
  // unimplemented
  return false;
}

void chrePalWifiApiReleaseScanEvent(struct chreWifiScanEvent *event) {
  chre::memoryFree(const_cast<uint32_t *>(event->scannedFreqList));
  chre::memoryFree(const_cast<struct chreWifiScanResult *>(event->results));
  chre::memoryFree(event);
}

void chrePalWifiApiReleaseRangingEvent(struct chreWifiRangingEvent *event) {
  chre::memoryFree(const_cast<struct chreWifiRangingResult *>(event->results));
  chre::memoryFree(event);
}

bool chrePalWifiApiNanSubscribe(
    const struct chreWifiNanSubscribeConfig *config) {
  uint32_t subscriptionId = 0;
  uint8_t errorCode =
      chre::PalNanEngineSingleton::get()->subscribe(config, &subscriptionId);

  gCallbacks->nanServiceIdentifierCallback(errorCode, subscriptionId);

  return true;
}

bool chrePalWifiApiNanSubscribeCancel(const uint32_t subscriptionId) {
  gCallbacks->nanSubscriptionCanceledCallback(CHRE_ERROR_NONE, subscriptionId);
  return chre::PalNanEngineSingleton::get()->subscribeCancel(subscriptionId);
}

void chrePalWifiApiNanReleaseDiscoveryEvent(
    struct chreWifiNanDiscoveryEvent *event) {
  chre::PalNanEngineSingleton::get()->destroyDiscoveryEvent(event);
}

bool chrePalWifiApiRequestNanRanging(
    const struct chreWifiNanRangingParams *params) {
  constexpr uint32_t kFakeRangeMeasurementMm = 1000;

  auto *event = chre::memoryAlloc<struct chreWifiRangingEvent>();
  CHRE_ASSERT_NOT_NULL(event);

  auto *result = chre::memoryAlloc<struct chreWifiRangingResult>();
  CHRE_ASSERT_NOT_NULL(result);

  std::memcpy(result->macAddress, params->macAddress, CHRE_WIFI_BSSID_LEN);
  result->status = CHRE_WIFI_RANGING_STATUS_SUCCESS;
  result->distance = kFakeRangeMeasurementMm;
  event->resultCount = 1;
  event->results = result;

  gCallbacks->rangingEventCallback(CHRE_ERROR_NONE, event);

  return true;
}

void chrePalWifiApiClose() {
  stopScanEventThreads();
  stopScanMonitorThreads();
}

bool chrePalWifiApiOpen(const struct chrePalSystemApi *systemApi,
                        const struct chrePalWifiCallbacks *callbacks) {
  chrePalWifiApiClose();

  bool success = false;
  if (systemApi != nullptr && callbacks != nullptr) {
    gSystemApi = systemApi;
    gCallbacks = callbacks;

    chre::PalNanEngineSingleton::get()->setPlatformWifiCallbacks(callbacks);

    success = true;
  }

  return success;
}

}  // anonymous namespace

bool chrePalWifiIsScanMonitoringActive() {
  return gScanMonitoringActive;
}

const struct chrePalWifiApi *chrePalWifiGetApi(uint32_t requestedApiVersion) {
  static const struct chrePalWifiApi kApi = {
      .moduleVersion = CHRE_PAL_WIFI_API_CURRENT_VERSION,
      .open = chrePalWifiApiOpen,
      .close = chrePalWifiApiClose,
      .getCapabilities = chrePalWifiGetCapabilities,
      .configureScanMonitor = chrePalWifiConfigureScanMonitor,
      .requestScan = chrePalWifiApiRequestScan,
      .releaseScanEvent = chrePalWifiApiReleaseScanEvent,
      .requestRanging = chrePalWifiApiRequestRanging,
      .releaseRangingEvent = chrePalWifiApiReleaseRangingEvent,
      .nanSubscribe = chrePalWifiApiNanSubscribe,
      .nanSubscribeCancel = chrePalWifiApiNanSubscribeCancel,
      .releaseNanDiscoveryEvent = chrePalWifiApiNanReleaseDiscoveryEvent,
      .requestNanRanging = chrePalWifiApiRequestNanRanging,
  };

  if (!CHRE_PAL_VERSIONS_ARE_COMPATIBLE(kApi.moduleVersion,
                                        requestedApiVersion)) {
    return nullptr;
  } else {
    chre::PalNanEngineSingleton::init();
    return &kApi;
  }
}

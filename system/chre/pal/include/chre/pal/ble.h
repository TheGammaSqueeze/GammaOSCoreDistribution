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

#ifndef CHRE_PAL_BLE_H_
#define CHRE_PAL_BLE_H_

/**
 * @file
 * Defines the interface between the common CHRE core system and the
 * platform-specific BLE (Bluetooth LE, Bluetooth Low Energy) module.
 */

#include <stdbool.h>
#include <stdint.h>

#include "chre/pal/system.h"
#include "chre/pal/version.h"
#include "chre_api/chre/ble.h"
#include "chre_api/chre/common.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initial version of the CHRE BLE PAL, introduced alongside CHRE API v1.6.
 */
#define CHRE_PAL_BLE_API_V1_6 CHRE_PAL_CREATE_API_VERSION(1, 6)

/**
 * The version of the CHRE BLE PAL defined in this header file.
 */
#define CHRE_PAL_BLE_API_CURRENT_VERSION CHRE_PAL_BLE_API_V1_6

struct chrePalBleCallbacks {
  /**
   * This function can be used by the BLE PAL subsystem to request that CHRE
   * re-send requests for any ongoing scans. This can be useful, for example, if
   * the BLE subsystem has recovered from a crash.
   */
  void (*requestStateResync)(void);

  /**
   * Callback invoked to inform the CHRE of the result of startScan() or
   * stopScan().
   *
   * Unsolicited calls to this function must not be made. In other words,
   * this callback should only be invoked as the direct result of an earlier
   * call to startScan() or stopScan().
   *
   * @param enabled true if the BLE scan is currently active and
   *        scanResultEventCallback() will receive scan results. False
   *        otherwise.
   * @param errorCode An error code from enum chreError
   *
   * @see chrePalBleApi.startScan
   * @see chrePalBleApi.stopScan
   * @see #chreError
   */
  void (*scanStatusChangeCallback)(bool enabled, uint8_t errorCode);

  /**
   * Callback used to pass BLE scan results from the to CHRE, which distributes
   * it to clients (nanoapps).
   *
   * This function call passes ownership of the event memory to the core CHRE
   * system, i.e. the PAL module must not modify the referenced data until
   * releaseAdvertisingEvent() is called to release the memory.
   *
   * If the results of a BLE scan are be split across multiple events, multiple
   * calls may be made to this callback.
   *
   * The PAL module must not deliver the same advertising event twice.
   *
   * @param event Event data to distribute to clients. The BLE module
   *        must ensure that this memory remains accessible until it is passed
   *        to the releaseAdvertisingEvent() function in struct chrePalBleApi.
   *
   * @see chrePalBleApi.startScan
   * @see chreBleAdvertisementEvent
   * @see releaseAdvertisingEvent
   */
  void (*advertisingEventCallback)(struct chreBleAdvertisementEvent *event);
};

struct chrePalBleApi {
  /**
   * Version of the module providing this API. This value should be
   * constructed from CHRE_PAL_CREATE_MODULE_VERSION using the supported
   * API version constant (CHRE_PAL_BLE_API_*) and the module-specific patch
   * version.
   */
  uint32_t moduleVersion;

  /**
   * Initializes the BLE module. Initialization must complete synchronously.
   *
   * @param systemApi Structure containing CHRE system function pointers which
   *        the PAL implementation should prefer to use over equivalent
   *        functionality exposed by the underlying platform. The module does
   *        not need to deep-copy this structure; its memory remains
   *        accessible at least until after close() is called.
   * @param callbacks Structure containing entry points to the core CHRE
   *        system. The module does not need to deep-copy this structure; its
   *        memory remains accessible at least until after close() is called.
   *
   * @return true if initialization was successful, false otherwise
   */
  bool (*open)(const struct chrePalSystemApi *systemApi,
               const struct chrePalBleCallbacks *callbacks);

  /**
   * Performs clean shutdown of the BLE module, usually done in preparation
   * for stopping the CHRE. The BLE module must ensure that it will not
   * invoke any callbacks past this point, and complete any relevant teardown
   * activities before returning from this function.
   */
  void (*close)(void);

  //! @see chreBleGetCapabilities()
  uint32_t (*getCapabilities)(void);

  //! @see chreBleGetFilterCapabilities()
  uint32_t (*getFilterCapabilities)(void);

  /**
   * Starts Bluetooth LE (BLE) scanning. The resulting BLE scan results will
   * be provided via subsequent calls to advertisingEventCallback().
   *
   * If startScan() is called while a previous scan has been started, the
   * previous scan will be stopped and replaced with the new scan.
   *
   * CHRE will combine Nanoapp BLE scan requests such that the PAL receives a
   * single scan mode, report delay, RSSI filtering threshold, and a list of all
   * requested filters. It is up to the BLE subsystem to optimize these filter
   * requests as best it can based on the hardware it has available.
   *
   * @param mode Scanning mode selected among enum chreBleScanMode
   * @param reportDelayMs Maximum requested batching delay in ms. 0 indicates no
   *                      batching. Note that the system may deliver results
   *                      before the maximum specified delay is reached.
   * @param filter List of filters that, if possible, should be used as hardware
   *               filters by the BT peripheral. Note that if any of these
   *               filters are invalid, they can be discarded by the PAL rather
   *               than causing a synchronous failure.
   *
   * @return true if the request was accepted for processing, in which case a
   *         subsequent call to scanStatusChangeCallback() will be used to
   *         communicate the result of the operation.
   *
   * @see chreBleStartScanAsync()
   */
  bool (*startScan)(enum chreBleScanMode mode, uint32_t reportDelayMs,
                    const struct chreBleScanFilter *filter);
  /**
   * Stops Bluetooth LE (BLE) scanning.
   *
   * If stopScan() is called without a previous scan being started, stopScan()
   * will be ignored.
   *
   * @return true if the request was accepted for processing, in which case a
   *         subsequent call to scanStatusChangeCallback() will be used to
   *         communicate the result of the operation.
   *
   * @see chreBleStopScanAsync()
   */
  bool (*stopScan)();

  /**
   * Invoked when the core CHRE system no longer needs a BLE advertising event
   * structure that was provided to it via advertisingEventCallback().
   *
   * @param event Event data to release
   */
  void (*releaseAdvertisingEvent)(struct chreBleAdvertisementEvent *event);
};

/**
 * Retrieve a handle for the CHRE BLE PAL.
 *
 * @param requestedApiVersion The implementation of this function must return a
 *        pointer to a structure with the same major version as requested.
 *
 * @return Pointer to API handle, or NULL if a compatible API version is not
 *         supported by the module, or the API as a whole is not implemented. If
 *         non-NULL, the returned API handle must be valid as long as this
 *         module is loaded.
 */
const struct chrePalBleApi *chrePalBleGetApi(uint32_t requestedApiVersion);

#ifdef __cplusplus
}
#endif

#endif  // CHRE_PAL_BLE_H_

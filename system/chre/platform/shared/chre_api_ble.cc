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

#include "chre_api/chre/ble.h"

#include "chre/core/event_loop_manager.h"
#include "chre/util/macros.h"
#include "chre/util/system/napp_permissions.h"

using chre::EventLoopManager;
using chre::EventLoopManagerSingleton;
using chre::NanoappPermissions;

DLL_EXPORT uint32_t chreBleGetCapabilities() {
#ifdef CHRE_BLE_SUPPORT_ENABLED
  return EventLoopManagerSingleton::get()
      ->getBleRequestManager()
      .getCapabilities();
#else
  return CHRE_BLE_CAPABILITIES_NONE;
#endif  // CHRE_BLE_SUPPORT_ENABLED
}

DLL_EXPORT uint32_t chreBleGetFilterCapabilities() {
#ifdef CHRE_BLE_SUPPORT_ENABLED
  return EventLoopManagerSingleton::get()
      ->getBleRequestManager()
      .getFilterCapabilities();
#else
  return CHRE_BLE_FILTER_CAPABILITIES_NONE;
#endif  // CHRE_BLE_SUPPORT_ENABLED
}

DLL_EXPORT bool chreBleStartScanAsync(chreBleScanMode mode,
                                      uint32_t reportDelayMs,
                                      const struct chreBleScanFilter *filter) {
#ifdef CHRE_BLE_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_BLE) &&
         EventLoopManagerSingleton::get()
             ->getBleRequestManager()
             .startScanAsync(nanoapp, mode, reportDelayMs, filter);
#else
  UNUSED_VAR(mode);
  UNUSED_VAR(reportDelayMs);
  UNUSED_VAR(filter);
  return false;
#endif  // CHRE_BLE_SUPPORT_ENABLED
}

DLL_EXPORT bool chreBleStopScanAsync() {
#ifdef CHRE_BLE_SUPPORT_ENABLED
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return nanoapp->permitPermissionUse(NanoappPermissions::CHRE_PERMS_BLE) &&
         EventLoopManagerSingleton::get()->getBleRequestManager().stopScanAsync(
             nanoapp);
#else
  return false;
#endif  // CHRE_BLE_SUPPORT_ENABLED
}

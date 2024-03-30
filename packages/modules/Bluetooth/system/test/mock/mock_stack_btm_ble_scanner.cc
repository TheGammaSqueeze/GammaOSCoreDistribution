/*
 * Copyright 2021 The Android Open Source Project
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

#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

#include <base/bind.h>
#include <base/location.h>
#include <base/logging.h>
#include <base/memory/weak_ptr.h>
#include <base/strings/string_number_conversions.h>
#include <base/time/time.h>
#include <string.h>

#include <queue>
#include <vector>

#include "bind_helpers.h"
#include "ble_scanner.h"
#include "bt_target.h"
#include "device/include/controller.h"
#include "osi/include/alarm.h"
#include "stack/btm/ble_scanner_hci_interface.h"
#include "stack/btm/btm_ble_int.h"
#include "stack/btm/btm_int_types.h"

#ifndef UNUSED_ATTR
#define UNUSED_ATTR
#endif

void BleScanningManager::CleanUp() { mock_function_count_map[__func__]++; }
void btm_ble_scanner_init() { mock_function_count_map[__func__]++; }
base::WeakPtr<BleScanningManager> BleScanningManager::Get() {
  mock_function_count_map[__func__]++;
  return nullptr;
}
bool BleScanningManager::IsInitialized() {
  mock_function_count_map[__func__]++;
  return false;
}
void BleScanningManager::Initialize(BleScannerHciInterface* interface) {
  mock_function_count_map[__func__]++;
}
void btm_ble_scanner_cleanup(void) { mock_function_count_map[__func__]++; }
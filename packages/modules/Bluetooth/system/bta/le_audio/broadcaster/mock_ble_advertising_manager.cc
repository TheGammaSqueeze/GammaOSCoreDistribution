/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include "mock_ble_advertising_manager.h"

#include <base/memory/weak_ptr.h>

#include "ble_advertiser_hci_interface.h"

namespace {
BleAdvertisingManager* instance;
base::WeakPtr<MockBleAdvertisingManager> instance_weakptr;
}  // namespace

void BleAdvertisingManager::Initialize(BleAdvertiserHciInterface* interface) {
  MockBleAdvertisingManager* manager = new MockBleAdvertisingManager();
  manager->SetBleAdvertiserHciInterfaceForTesting(interface);

  instance = manager;
  instance_weakptr = ((MockBleAdvertisingManager*)instance)->GetWeakPtr();
}

void BleAdvertisingManager::CleanUp() {
  delete instance;
  instance = nullptr;
}

bool BleAdvertisingManager::IsInitialized() { return instance; }

base::WeakPtr<BleAdvertisingManager> BleAdvertisingManager::Get() {
  return instance_weakptr;
}

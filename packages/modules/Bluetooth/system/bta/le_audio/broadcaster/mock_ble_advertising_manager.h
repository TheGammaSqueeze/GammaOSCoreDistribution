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

#pragma once

#include <base/bind.h>
#include <base/callback.h>
#include <base/memory/weak_ptr.h>
#include <gmock/gmock.h>

#include "ble_advertiser.h"

class MockBleAdvertisingManager : public BleAdvertisingManager {
 public:
  MockBleAdvertisingManager() = default;
  MockBleAdvertisingManager(const MockBleAdvertisingManager&) = delete;
  MockBleAdvertisingManager& operator=(const MockBleAdvertisingManager&) =
      delete;

  ~MockBleAdvertisingManager() override = default;

  /* Allows getting and setting BleAdvertiserHciInterface dependency */
  BleAdvertiserHciInterface* GetBleAdvertiserHciInterface() {
    return ble_adv_hci_interface_;
  }
  void SetBleAdvertiserHciInterfaceForTesting(
      BleAdvertiserHciInterface* interface) {
    ble_adv_hci_interface_ = interface;
  }

  base::WeakPtr<MockBleAdvertisingManager> GetWeakPtr() {
    return weak_factory_.GetWeakPtr();
  }

  MOCK_METHOD((void), StartAdvertising,
              (uint8_t advertiser_id, MultiAdvCb cb,
               tBTM_BLE_ADV_PARAMS* params, std::vector<uint8_t> advertise_data,
               std::vector<uint8_t> scan_response_data, int duration,
               MultiAdvCb timeout_cb),
              (override));
  MOCK_METHOD((void), StartAdvertisingSet,
              (base::Callback<void(uint8_t /* inst_id */, int8_t /* tx_power */,
                                   uint8_t /* status */)>
                   cb,
               tBTM_BLE_ADV_PARAMS* params, std::vector<uint8_t> advertise_data,
               std::vector<uint8_t> scan_response_data,
               tBLE_PERIODIC_ADV_PARAMS* periodic_params,
               std::vector<uint8_t> periodic_data, uint16_t duration,
               uint8_t maxExtAdvEvents,
               base::Callback<void(uint8_t /* inst_id */, uint8_t /* status */)>
                   timeout_cb),
              (override));
  MOCK_METHOD((void), RegisterAdvertiser,
              (base::Callback<void(uint8_t inst_id, uint8_t status)>),
              (override));
  MOCK_METHOD((void), Enable,
              (uint8_t inst_id, bool enable, MultiAdvCb cb, uint16_t duration,
               uint8_t maxExtAdvEvents, MultiAdvCb timeout_cb),
              (override));
  MOCK_METHOD((void), SetParameters,
              (uint8_t inst_id, tBTM_BLE_ADV_PARAMS* p_params, ParametersCb cb),
              (override));
  MOCK_METHOD((void), SetData,
              (uint8_t inst_id, bool is_scan_rsp, std::vector<uint8_t> data,
               MultiAdvCb cb),
              (override));
  MOCK_METHOD((void), SetPeriodicAdvertisingParameters,
              (uint8_t inst_id, tBLE_PERIODIC_ADV_PARAMS* params,
               MultiAdvCb cb),
              (override));
  MOCK_METHOD((void), SetPeriodicAdvertisingData,
              (uint8_t inst_id, std::vector<uint8_t> data, MultiAdvCb cb),
              (override));
  MOCK_METHOD((void), SetPeriodicAdvertisingEnable,
              (uint8_t inst_id, uint8_t enable, MultiAdvCb cb), (override));
  MOCK_METHOD((void), Unregister, (uint8_t inst_id), (override));
  MOCK_METHOD((void), Suspend, (), (override));
  MOCK_METHOD((void), Resume, (), (override));
  MOCK_METHOD((void), OnAdvertisingSetTerminated,
              (uint8_t status, uint8_t advertising_handle,
               uint16_t connection_handle,
               uint8_t num_completed_extended_adv_events),
              (override));
  MOCK_METHOD(
      (void), GetOwnAddress,
      (uint8_t inst_id,
       base::Callback<void(uint8_t /* address_type*/, RawAddress /*address*/)>
           cb),
      (override));

 private:
  base::WeakPtrFactory<MockBleAdvertisingManager> weak_factory_{this};
  BleAdvertiserHciInterface* ble_adv_hci_interface_;
};

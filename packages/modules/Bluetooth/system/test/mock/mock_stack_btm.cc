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

/*
 * Generated mock file from original source file
 */

#include "stack/include/btm_api.h"
#include "stack/include/btm_ble_api_types.h"
#include "stack/include/btm_client_interface.h"
#include "types/raw_address.h"

tBTM_STATUS BTM_BleGetEnergyInfo(tBTM_BLE_ENERGY_INFO_CBACK* p_ener_cback) {
  return BTM_SUCCESS;
}
void BTM_BleBackgroundObserve(bool enable, tBTM_INQ_RESULTS_CB* p_results_cb) {}
void BTM_BleReadControllerFeatures(tBTM_BLE_CTRL_FEATURES_CBACK* p_vsc_cback) {}
uint8_t BTM_GetAcceptlistSize() { return 0; }

tBTM_STATUS BTM_WriteEIR(BT_HDR* p_buff) { return BTM_SUCCESS; }

struct btm_client_interface_t btm_client_interface = {
    .eir =
        {
            .BTM_GetEirSupportedServices = BTM_GetEirSupportedServices,
            .BTM_WriteEIR = BTM_WriteEIR,
        },
};

struct btm_client_interface_t& get_btm_client_interface() {
  return btm_client_interface;
}

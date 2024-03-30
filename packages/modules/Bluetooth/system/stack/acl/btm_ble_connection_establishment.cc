/******************************************************************************
 *
 *  Copyright 2019 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include <frameworks/proto_logging/stats/enums/bluetooth/enums.pb.h>
#include <frameworks/proto_logging/stats/enums/bluetooth/hci/enums.pb.h>

#include <bitset>

#include "common/metrics.h"
#include "device/include/controller.h"
#include "main/shim/shim.h"
#include "osi/include/log.h"
#include "stack/btm/btm_ble_int.h"
#include "stack/gatt/connection_manager.h"
#include "stack/include/acl_api.h"
#include "stack/include/ble_acl_interface.h"
#include "stack/include/ble_hci_link_interface.h"
#include "stack/include/l2cap_hci_link_interface.h"
#include "stack/include/stack_metrics_logging.h"
#include "types/raw_address.h"

#include <base/logging.h>

extern tBTM_CB btm_cb;

extern void btm_ble_advertiser_notify_terminated_legacy(
    uint8_t status, uint16_t connection_handle);

extern bool btm_ble_init_pseudo_addr(tBTM_SEC_DEV_REC* p_dev_rec,
                                     const RawAddress& new_pseudo_addr);
/** LE connection complete. */
void btm_ble_create_ll_conn_complete(tHCI_STATUS status) {
  if (status == HCI_SUCCESS) return;

  LOG(WARNING) << "LE Create Connection attempt failed, status="
               << hci_error_code_text(status);

  if (status == HCI_ERR_COMMAND_DISALLOWED) {
    btm_cb.ble_ctr_cb.set_connection_state_connecting();
    btm_ble_set_topology_mask(BTM_BLE_STATE_INIT_BIT);
    LOG(ERROR) << "LE Create Connection - command disallowed";
  } else {
    btm_cb.ble_ctr_cb.set_connection_state_idle();
    btm_ble_clear_topology_mask(BTM_BLE_STATE_INIT_BIT);
    btm_ble_update_mode_operation(HCI_ROLE_UNKNOWN, NULL, status);
  }
}

bool maybe_resolve_address(RawAddress* bda, tBLE_ADDR_TYPE* bda_type) {
  bool is_in_security_db = false;
  tBLE_ADDR_TYPE peer_addr_type = *bda_type;
  bool addr_is_rpa =
      (peer_addr_type == BLE_ADDR_RANDOM && BTM_BLE_IS_RESOLVE_BDA(*bda));

  /* We must translate whatever address we received into the "pseudo" address.
   * i.e. if we bonded with device that was using RPA for first connection,
   * "pseudo" address is equal to this RPA. If it later decides to use Public
   * address, or Random Static Address, we convert it into the "pseudo"
   * address here. */
  if (!addr_is_rpa || peer_addr_type & BLE_ADDR_TYPE_ID_BIT) {
    is_in_security_db = btm_identity_addr_to_random_pseudo(bda, bda_type, true);
  }

  /* possiblly receive connection complete with resolvable random while
     the device has been paired */
  if (!is_in_security_db && addr_is_rpa) {
    tBTM_SEC_DEV_REC* match_rec = btm_ble_resolve_random_addr(*bda);
    if (match_rec) {
      LOG(INFO) << __func__ << ": matched and resolved random address";
      is_in_security_db = true;
      match_rec->ble.active_addr_type = tBTM_SEC_BLE::BTM_BLE_ADDR_RRA;
      match_rec->ble.cur_rand_addr = *bda;
      if (!btm_ble_init_pseudo_addr(match_rec, *bda)) {
        /* assign the original address to be the current report address */
        *bda = match_rec->ble.pseudo_addr;
        *bda_type = match_rec->ble.AddressType();
      } else {
        *bda = match_rec->bd_addr;
      }
    } else {
      LOG(INFO) << __func__ << ": unable to match and resolve random address";
    }
  }
  return is_in_security_db;
}

void btm_ble_create_conn_cancel() {
  ASSERT_LOG(false,
             "When gd_acl enabled this code path should not be exercised");

  btsnd_hcic_ble_create_conn_cancel();
  btm_cb.ble_ctr_cb.set_connection_state_cancelled();
  btm_ble_clear_topology_mask(BTM_BLE_STATE_INIT_BIT);
}

void btm_ble_create_conn_cancel_complete(uint8_t* p) {
  uint8_t status;
  STREAM_TO_UINT8(status, p);
  if (status != HCI_SUCCESS) {
    // Only log errors to prevent log spam due to acceptlist connections
    log_link_layer_connection_event(
        nullptr, bluetooth::common::kUnknownConnectionHandle,
        android::bluetooth::DIRECTION_OUTGOING,
        android::bluetooth::LINK_TYPE_ACL,
        android::bluetooth::hci::CMD_BLE_CREATE_CONN_CANCEL,
        android::bluetooth::hci::EVT_COMMAND_COMPLETE,
        android::bluetooth::hci::BLE_EVT_UNKNOWN, status,
        android::bluetooth::hci::STATUS_UNKNOWN);
  }

  if (status == HCI_ERR_COMMAND_DISALLOWED) {
    /* This is a sign that logic around keeping connection state is broken */
    LOG(ERROR)
        << "Attempt to cancel LE connection, when no connection is pending.";
    if (btm_cb.ble_ctr_cb.is_connection_state_cancelled()) {
      btm_cb.ble_ctr_cb.set_connection_state_idle();
      btm_ble_clear_topology_mask(BTM_BLE_STATE_INIT_BIT);
      btm_ble_update_mode_operation(HCI_ROLE_UNKNOWN, nullptr,
                                    static_cast<tHCI_STATUS>(status));
    }
  }
}

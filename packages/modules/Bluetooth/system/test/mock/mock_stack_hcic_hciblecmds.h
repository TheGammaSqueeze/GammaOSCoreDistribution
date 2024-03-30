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
#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:69
 *
 *  mockcify.pl ver 0.3.2
 */

#include <cstdint>
#include <functional>
#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

// Original included files, if any
// NOTE: Since this is a mock file with mock definitions some number of
//       include files may not be required.  The include-what-you-use
//       still applies, but crafting proper inclusion is out of scope
//       for this effort.  This compilation unit may compile as-is, or
//       may need attention to prune from (or add to ) the inclusion set.
#include <base/bind.h>
#include <stddef.h>
#include <string.h>

#include <bitset>

#include "bt_target.h"
#include "btu.h"
#include "hcidefs.h"
#include "hcimsgs.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_octets.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace stack_hcic_hciblecmds {

// Shared state between mocked functions and tests
// Name: btsnd_hci_ble_add_device_to_periodic_advertiser_list
// Params: uint8_t adv_addr_type, const RawAddress& adv_addr, uint8_t adv_sid,
// base::OnceCallback<void(uint8_t*, uint16_t Return: void
struct btsnd_hci_ble_add_device_to_periodic_advertiser_list {
  std::function<void(uint8_t, const RawAddress&, uint8_t,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint8_t adv_addr_type, const RawAddress& adv_addr,
              uint8_t adv_sid,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint8_t adv_addr_type, const RawAddress& adv_addr,
                  uint8_t adv_sid,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(adv_addr_type, adv_addr, adv_sid, std::move(cb));
  };
};
extern struct btsnd_hci_ble_add_device_to_periodic_advertiser_list
    btsnd_hci_ble_add_device_to_periodic_advertiser_list;

// Name: btsnd_hci_ble_clear_periodic_advertiser_list
// Params: base::OnceCallback<void(uint8_t*, uint16_t
// Return: void
struct btsnd_hci_ble_clear_periodic_advertiser_list {
  std::function<void(base::OnceCallback<void(uint8_t*, uint16_t)>)> body{
      [](base::OnceCallback<void(uint8_t*, uint16_t)>) {}};
  void operator()(base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(std::move(cb));
  };
};
extern struct btsnd_hci_ble_clear_periodic_advertiser_list
    btsnd_hci_ble_clear_periodic_advertiser_list;

// Name: btsnd_hci_ble_read_periodic_advertiser_list_size
// Params: base::OnceCallback<void(uint8_t*, uint16_t
// Return: void
struct btsnd_hci_ble_read_periodic_advertiser_list_size {
  std::function<void(base::OnceCallback<void(uint8_t*, uint16_t)>)> body{
      [](base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(std::move(cb));
  };
};
extern struct btsnd_hci_ble_read_periodic_advertiser_list_size
    btsnd_hci_ble_read_periodic_advertiser_list_size;

// Name: btsnd_hci_ble_remove_device_from_periodic_advertiser_list
// Params: uint8_t adv_addr_type, const RawAddress& adv_addr, uint8_t adv_sid,
// base::OnceCallback<void(uint8_t*, uint16_t Return: void
struct btsnd_hci_ble_remove_device_from_periodic_advertiser_list {
  std::function<void(uint8_t, const RawAddress&, uint8_t,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint8_t adv_addr_type, const RawAddress& adv_addr,
              uint8_t adv_sid,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint8_t adv_addr_type, const RawAddress& adv_addr,
                  uint8_t adv_sid,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(adv_addr_type, adv_addr, adv_sid, std::move(cb));
  };
};
extern struct btsnd_hci_ble_remove_device_from_periodic_advertiser_list
    btsnd_hci_ble_remove_device_from_periodic_advertiser_list;

// Name: btsnd_hcic_accept_cis_req
// Params: uint16_t conn_handle
// Return: void
struct btsnd_hcic_accept_cis_req {
  std::function<void(uint16_t conn_handle)> body{[](uint16_t conn_handle) {}};
  void operator()(uint16_t conn_handle) { body(conn_handle); };
};
extern struct btsnd_hcic_accept_cis_req btsnd_hcic_accept_cis_req;

// Name: btsnd_hcic_big_create_sync
// Params: uint8_t big_handle, uint16_t sync_handle, uint8_t enc,
// std::array<uint8_t, 16> bcst_code, uint8_t mse, uint16_t big_sync_timeout,
// std::vector<uint8_t> bis Return: void
struct btsnd_hcic_big_create_sync {
  std::function<void(uint8_t, uint16_t, uint8_t, std::array<uint8_t, 16>,
                     uint8_t, uint16_t, std::vector<uint8_t>)>
      body{[](uint8_t big_handle, uint16_t sync_handle, uint8_t enc,
              std::array<uint8_t, 16> bcst_code, uint8_t mse,
              uint16_t big_sync_timeout, std::vector<uint8_t> bis) {}};
  void operator()(uint8_t big_handle, uint16_t sync_handle, uint8_t enc,
                  std::array<uint8_t, 16> bcst_code, uint8_t mse,
                  uint16_t big_sync_timeout, std::vector<uint8_t> bis) {
    body(big_handle, sync_handle, enc, bcst_code, mse, big_sync_timeout, bis);
  };
};
extern struct btsnd_hcic_big_create_sync btsnd_hcic_big_create_sync;

// Name: btsnd_hcic_big_term_sync
// Params: uint8_t big_handle, base::OnceCallback<void(uint8_t*, uint16_t
// Return: void
struct btsnd_hcic_big_term_sync {
  std::function<void(uint8_t, base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint8_t big_handle,
              base::OnceCallback<void(uint8_t*, uint16_t)>) {}};
  void operator()(uint8_t big_handle,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(big_handle, std::move(cb));
  };
};
extern struct btsnd_hcic_big_term_sync btsnd_hcic_big_term_sync;

// Name: btsnd_hcic_ble_add_acceptlist
// Params: uint8_t addr_type, const RawAddress& bda,
// base::OnceCallback<void(uint8_t*, uint16_t Return: void
struct btsnd_hcic_ble_add_acceptlist {
  std::function<void(uint8_t, const RawAddress&,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint8_t addr_type, const RawAddress& bda,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint8_t addr_type, const RawAddress& bda,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(addr_type, bda, std::move(cb));
  };
};
extern struct btsnd_hcic_ble_add_acceptlist btsnd_hcic_ble_add_acceptlist;

// Name: btsnd_hcic_ble_add_device_resolving_list
// Params: uint8_t addr_type_peer, const RawAddress& bda_peer, const Octet16&
// irk_peer, const Octet16& irk_local Return: void
struct btsnd_hcic_ble_add_device_resolving_list {
  std::function<void(uint8_t addr_type_peer, const RawAddress& bda_peer,
                     const Octet16& irk_peer, const Octet16& irk_local)>
      body{[](uint8_t addr_type_peer, const RawAddress& bda_peer,
              const Octet16& irk_peer, const Octet16& irk_local) {}};
  void operator()(uint8_t addr_type_peer, const RawAddress& bda_peer,
                  const Octet16& irk_peer, const Octet16& irk_local) {
    body(addr_type_peer, bda_peer, irk_peer, irk_local);
  };
};
extern struct btsnd_hcic_ble_add_device_resolving_list
    btsnd_hcic_ble_add_device_resolving_list;

// Name: btsnd_hcic_ble_clear_acceptlist
// Params: base::OnceCallback<void(uint8_t*, uint16_t
// Return: void
struct btsnd_hcic_ble_clear_acceptlist {
  std::function<void(base::OnceCallback<void(uint8_t*, uint16_t)>)> body{
      [](base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(std::move(cb));
  };
};
extern struct btsnd_hcic_ble_clear_acceptlist btsnd_hcic_ble_clear_acceptlist;

// Name: btsnd_hcic_ble_clear_resolving_list
// Params: void
// Return: void
struct btsnd_hcic_ble_clear_resolving_list {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btsnd_hcic_ble_clear_resolving_list
    btsnd_hcic_ble_clear_resolving_list;

// Name: btsnd_hcic_ble_create_conn_cancel
// Params: void
// Return: void
struct btsnd_hcic_ble_create_conn_cancel {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btsnd_hcic_ble_create_conn_cancel
    btsnd_hcic_ble_create_conn_cancel;

// Name: btsnd_hcic_ble_create_ll_conn
// Params: uint16_t scan_int, uint16_t scan_win, uint8_t init_filter_policy,
// tBLE_ADDR_TYPE addr_type_peer, const RawAddress& bda_peer, tBLE_ADDR_TYPE
// addr_type_own, uint16_t conn_int_min, uint16_t conn_int_max, uint16_t
// conn_latency, uint16_t conn_timeout, uint16_t min_ce_len, uint16_t max_ce_len
// Return: void
struct btsnd_hcic_ble_create_ll_conn {
  std::function<void(uint16_t scan_int, uint16_t scan_win,
                     uint8_t init_filter_policy, tBLE_ADDR_TYPE addr_type_peer,
                     const RawAddress& bda_peer, tBLE_ADDR_TYPE addr_type_own,
                     uint16_t conn_int_min, uint16_t conn_int_max,
                     uint16_t conn_latency, uint16_t conn_timeout,
                     uint16_t min_ce_len, uint16_t max_ce_len)>
      body{[](uint16_t scan_int, uint16_t scan_win, uint8_t init_filter_policy,
              tBLE_ADDR_TYPE addr_type_peer, const RawAddress& bda_peer,
              tBLE_ADDR_TYPE addr_type_own, uint16_t conn_int_min,
              uint16_t conn_int_max, uint16_t conn_latency,
              uint16_t conn_timeout, uint16_t min_ce_len,
              uint16_t max_ce_len) {}};
  void operator()(uint16_t scan_int, uint16_t scan_win,
                  uint8_t init_filter_policy, tBLE_ADDR_TYPE addr_type_peer,
                  const RawAddress& bda_peer, tBLE_ADDR_TYPE addr_type_own,
                  uint16_t conn_int_min, uint16_t conn_int_max,
                  uint16_t conn_latency, uint16_t conn_timeout,
                  uint16_t min_ce_len, uint16_t max_ce_len) {
    body(scan_int, scan_win, init_filter_policy, addr_type_peer, bda_peer,
         addr_type_own, conn_int_min, conn_int_max, conn_latency, conn_timeout,
         min_ce_len, max_ce_len);
  };
};
extern struct btsnd_hcic_ble_create_ll_conn btsnd_hcic_ble_create_ll_conn;

// Name: btsnd_hcic_ble_enh_rx_test
// Params: uint8_t rx_chan, uint8_t phy, uint8_t mod_index
// Return: void
struct btsnd_hcic_ble_enh_rx_test {
  std::function<void(uint8_t rx_chan, uint8_t phy, uint8_t mod_index)> body{
      [](uint8_t rx_chan, uint8_t phy, uint8_t mod_index) {}};
  void operator()(uint8_t rx_chan, uint8_t phy, uint8_t mod_index) {
    body(rx_chan, phy, mod_index);
  };
};
extern struct btsnd_hcic_ble_enh_rx_test btsnd_hcic_ble_enh_rx_test;

// Name: btsnd_hcic_ble_enh_tx_test
// Params: uint8_t tx_chan, uint8_t data_len, uint8_t payload, uint8_t phy
// Return: void
struct btsnd_hcic_ble_enh_tx_test {
  std::function<void(uint8_t tx_chan, uint8_t data_len, uint8_t payload,
                     uint8_t phy)>
      body{[](uint8_t tx_chan, uint8_t data_len, uint8_t payload, uint8_t phy) {
      }};
  void operator()(uint8_t tx_chan, uint8_t data_len, uint8_t payload,
                  uint8_t phy) {
    body(tx_chan, data_len, payload, phy);
  };
};
extern struct btsnd_hcic_ble_enh_tx_test btsnd_hcic_ble_enh_tx_test;

// Name: btsnd_hcic_ble_ext_create_conn
// Params: uint8_t init_filter_policy, tBLE_ADDR_TYPE addr_type_own,
// tBLE_ADDR_TYPE addr_type_peer, const RawAddress& bda_peer, uint8_t
// initiating_phys, EXT_CONN_PHY_CFG* phy_cfg Return: void
struct btsnd_hcic_ble_ext_create_conn {
  std::function<void(uint8_t init_filter_policy, tBLE_ADDR_TYPE addr_type_own,
                     tBLE_ADDR_TYPE addr_type_peer, const RawAddress& bda_peer,
                     uint8_t initiating_phys, EXT_CONN_PHY_CFG* phy_cfg)>
      body{[](uint8_t init_filter_policy, tBLE_ADDR_TYPE addr_type_own,
              tBLE_ADDR_TYPE addr_type_peer, const RawAddress& bda_peer,
              uint8_t initiating_phys, EXT_CONN_PHY_CFG* phy_cfg) {}};
  void operator()(uint8_t init_filter_policy, tBLE_ADDR_TYPE addr_type_own,
                  tBLE_ADDR_TYPE addr_type_peer, const RawAddress& bda_peer,
                  uint8_t initiating_phys, EXT_CONN_PHY_CFG* phy_cfg) {
    body(init_filter_policy, addr_type_own, addr_type_peer, bda_peer,
         initiating_phys, phy_cfg);
  };
};
extern struct btsnd_hcic_ble_ext_create_conn btsnd_hcic_ble_ext_create_conn;

// Name: btsnd_hcic_ble_ltk_req_neg_reply
// Params: uint16_t handle
// Return: void
struct btsnd_hcic_ble_ltk_req_neg_reply {
  std::function<void(uint16_t handle)> body{[](uint16_t handle) {}};
  void operator()(uint16_t handle) { body(handle); };
};
extern struct btsnd_hcic_ble_ltk_req_neg_reply btsnd_hcic_ble_ltk_req_neg_reply;

// Name: btsnd_hcic_ble_ltk_req_reply
// Params: uint16_t handle, const Octet16& ltk
// Return: void
struct btsnd_hcic_ble_ltk_req_reply {
  std::function<void(uint16_t handle, const Octet16& ltk)> body{
      [](uint16_t handle, const Octet16& ltk) {}};
  void operator()(uint16_t handle, const Octet16& ltk) { body(handle, ltk); };
};
extern struct btsnd_hcic_ble_ltk_req_reply btsnd_hcic_ble_ltk_req_reply;

// Name: btsnd_hcic_ble_periodic_advertising_create_sync
// Params: uint8_t options, uint8_t adv_sid, uint8_t adv_addr_type, const
// RawAddress& adv_addr, uint16_t skip_num, uint16_t sync_timeout, uint8_t
// sync_cte_type Return: void
struct btsnd_hcic_ble_periodic_advertising_create_sync {
  std::function<void(uint8_t options, uint8_t adv_sid, uint8_t adv_addr_type,
                     const RawAddress& adv_addr, uint16_t skip_num,
                     uint16_t sync_timeout, uint8_t sync_cte_type)>
      body{[](uint8_t options, uint8_t adv_sid, uint8_t adv_addr_type,
              const RawAddress& adv_addr, uint16_t skip_num,
              uint16_t sync_timeout, uint8_t sync_cte_type) {}};
  void operator()(uint8_t options, uint8_t adv_sid, uint8_t adv_addr_type,
                  const RawAddress& adv_addr, uint16_t skip_num,
                  uint16_t sync_timeout, uint8_t sync_cte_type) {
    body(options, adv_sid, adv_addr_type, adv_addr, skip_num, sync_timeout,
         sync_cte_type);
  };
};
extern struct btsnd_hcic_ble_periodic_advertising_create_sync
    btsnd_hcic_ble_periodic_advertising_create_sync;

// Name: btsnd_hcic_ble_periodic_advertising_create_sync_cancel
// Params: base::OnceCallback<void(uint8_t*, uint16_t
// Return: void
struct btsnd_hcic_ble_periodic_advertising_create_sync_cancel {
  std::function<void(base::OnceCallback<void(uint8_t*, uint16_t)>)> body{
      [](base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(std::move(cb));
  };
};
extern struct btsnd_hcic_ble_periodic_advertising_create_sync_cancel
    btsnd_hcic_ble_periodic_advertising_create_sync_cancel;

// Name: btsnd_hcic_ble_periodic_advertising_set_info_transfer
// Params: uint16_t conn_handle, uint16_t service_data, uint8_t adv_handle,
// base::OnceCallback<void(uint8_t*, uint16_t Return: void
struct btsnd_hcic_ble_periodic_advertising_set_info_transfer {
  std::function<void(uint16_t, uint16_t, uint8_t,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint16_t conn_handle, uint16_t service_data, uint8_t adv_handle,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint16_t conn_handle, uint16_t service_data,
                  uint8_t adv_handle,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(conn_handle, service_data, adv_handle, std::move(cb));
  };
};
extern struct btsnd_hcic_ble_periodic_advertising_set_info_transfer
    btsnd_hcic_ble_periodic_advertising_set_info_transfer;

// Name: btsnd_hcic_ble_periodic_advertising_sync_transfer
// Params: uint16_t conn_handle, uint16_t service_data, uint16_t sync_handle,
// base::OnceCallback<void(uint8_t*, uint16_t Return: void
struct btsnd_hcic_ble_periodic_advertising_sync_transfer {
  std::function<void(uint16_t, uint16_t, uint16_t,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint16_t conn_handle, uint16_t service_data, uint16_t sync_handle,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint16_t conn_handle, uint16_t service_data,
                  uint16_t sync_handle,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(conn_handle, service_data, sync_handle, std::move(cb));
  };
};
extern struct btsnd_hcic_ble_periodic_advertising_sync_transfer
    btsnd_hcic_ble_periodic_advertising_sync_transfer;

// Name: btsnd_hcic_ble_periodic_advertising_terminate_sync
// Params: uint16_t sync_handle, base::OnceCallback<void(uint8_t*, uint16_t
// Return: void
struct btsnd_hcic_ble_periodic_advertising_terminate_sync {
  std::function<void(uint16_t, base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint16_t sync_handle,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint16_t sync_handle,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(sync_handle, std::move(cb));
  };
};
extern struct btsnd_hcic_ble_periodic_advertising_terminate_sync
    btsnd_hcic_ble_periodic_advertising_terminate_sync;

// Name: btsnd_hcic_ble_rand
// Params: base::Callback<void(BT_OCTET8
// Return: void
struct btsnd_hcic_ble_rand {
  std::function<void(base::Callback<void(BT_OCTET8)>)> body{
      [](base::Callback<void(BT_OCTET8)> cb) {}};
  void operator()(base::Callback<void(BT_OCTET8)> cb) { body(std::move(cb)); };
};
extern struct btsnd_hcic_ble_rand btsnd_hcic_ble_rand;

// Name: btsnd_hcic_ble_rc_param_req_neg_reply
// Params: uint16_t handle, uint8_t reason
// Return: void
struct btsnd_hcic_ble_rc_param_req_neg_reply {
  std::function<void(uint16_t handle, uint8_t reason)> body{
      [](uint16_t handle, uint8_t reason) {}};
  void operator()(uint16_t handle, uint8_t reason) { body(handle, reason); };
};
extern struct btsnd_hcic_ble_rc_param_req_neg_reply
    btsnd_hcic_ble_rc_param_req_neg_reply;

// Name: btsnd_hcic_ble_rc_param_req_reply
// Params: uint16_t handle, uint16_t conn_int_min, uint16_t conn_int_max,
// uint16_t conn_latency, uint16_t conn_timeout, uint16_t min_ce_len, uint16_t
// max_ce_len Return: void
struct btsnd_hcic_ble_rc_param_req_reply {
  std::function<void(uint16_t handle, uint16_t conn_int_min,
                     uint16_t conn_int_max, uint16_t conn_latency,
                     uint16_t conn_timeout, uint16_t min_ce_len,
                     uint16_t max_ce_len)>
      body{[](uint16_t handle, uint16_t conn_int_min, uint16_t conn_int_max,
              uint16_t conn_latency, uint16_t conn_timeout, uint16_t min_ce_len,
              uint16_t max_ce_len) {}};
  void operator()(uint16_t handle, uint16_t conn_int_min, uint16_t conn_int_max,
                  uint16_t conn_latency, uint16_t conn_timeout,
                  uint16_t min_ce_len, uint16_t max_ce_len) {
    body(handle, conn_int_min, conn_int_max, conn_latency, conn_timeout,
         min_ce_len, max_ce_len);
  };
};
extern struct btsnd_hcic_ble_rc_param_req_reply
    btsnd_hcic_ble_rc_param_req_reply;

// Name: btsnd_hcic_ble_read_adv_chnl_tx_power
// Params: void
// Return: void
struct btsnd_hcic_ble_read_adv_chnl_tx_power {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btsnd_hcic_ble_read_adv_chnl_tx_power
    btsnd_hcic_ble_read_adv_chnl_tx_power;

// Name: btsnd_hcic_ble_read_chnl_map
// Params: uint16_t handle
// Return: void
struct btsnd_hcic_ble_read_chnl_map {
  std::function<void(uint16_t handle)> body{[](uint16_t handle) {}};
  void operator()(uint16_t handle) { body(handle); };
};
extern struct btsnd_hcic_ble_read_chnl_map btsnd_hcic_ble_read_chnl_map;

// Name: btsnd_hcic_ble_read_host_supported
// Params: void
// Return: void
struct btsnd_hcic_ble_read_host_supported {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btsnd_hcic_ble_read_host_supported
    btsnd_hcic_ble_read_host_supported;

// Name: btsnd_hcic_ble_read_remote_feat
// Params: uint16_t handle
// Return: void
struct btsnd_hcic_ble_read_remote_feat {
  std::function<void(uint16_t handle)> body{[](uint16_t handle) {}};
  void operator()(uint16_t handle) { body(handle); };
};
extern struct btsnd_hcic_ble_read_remote_feat btsnd_hcic_ble_read_remote_feat;

// Name: btsnd_hcic_ble_read_resolvable_addr_local
// Params: uint8_t addr_type_peer, const RawAddress& bda_peer
// Return: void
struct btsnd_hcic_ble_read_resolvable_addr_local {
  std::function<void(uint8_t addr_type_peer, const RawAddress& bda_peer)> body{
      [](uint8_t addr_type_peer, const RawAddress& bda_peer) {}};
  void operator()(uint8_t addr_type_peer, const RawAddress& bda_peer) {
    body(addr_type_peer, bda_peer);
  };
};
extern struct btsnd_hcic_ble_read_resolvable_addr_local
    btsnd_hcic_ble_read_resolvable_addr_local;

// Name: btsnd_hcic_ble_read_resolvable_addr_peer
// Params: uint8_t addr_type_peer, const RawAddress& bda_peer
// Return: void
struct btsnd_hcic_ble_read_resolvable_addr_peer {
  std::function<void(uint8_t addr_type_peer, const RawAddress& bda_peer)> body{
      [](uint8_t addr_type_peer, const RawAddress& bda_peer) {}};
  void operator()(uint8_t addr_type_peer, const RawAddress& bda_peer) {
    body(addr_type_peer, bda_peer);
  };
};
extern struct btsnd_hcic_ble_read_resolvable_addr_peer
    btsnd_hcic_ble_read_resolvable_addr_peer;

// Name: btsnd_hcic_ble_receiver_test
// Params: uint8_t rx_freq
// Return: void
struct btsnd_hcic_ble_receiver_test {
  std::function<void(uint8_t rx_freq)> body{[](uint8_t rx_freq) {}};
  void operator()(uint8_t rx_freq) { body(rx_freq); };
};
extern struct btsnd_hcic_ble_receiver_test btsnd_hcic_ble_receiver_test;

// Name: btsnd_hcic_ble_remove_from_acceptlist
// Params: tBLE_ADDR_TYPE addr_type, const RawAddress& bda,
// base::OnceCallback<void(uint8_t*, uint16_t Return: void
struct btsnd_hcic_ble_remove_from_acceptlist {
  std::function<void(tBLE_ADDR_TYPE, const RawAddress&,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](tBLE_ADDR_TYPE addr_type, const RawAddress& bda,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(tBLE_ADDR_TYPE addr_type, const RawAddress& bda,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(addr_type, bda, std::move(cb));
  };
};
extern struct btsnd_hcic_ble_remove_from_acceptlist
    btsnd_hcic_ble_remove_from_acceptlist;

// Name: btsnd_hcic_ble_rm_device_resolving_list
// Params: uint8_t addr_type_peer, const RawAddress& bda_peer
// Return: void
struct btsnd_hcic_ble_rm_device_resolving_list {
  std::function<void(uint8_t addr_type_peer, const RawAddress& bda_peer)> body{
      [](uint8_t addr_type_peer, const RawAddress& bda_peer) {}};
  void operator()(uint8_t addr_type_peer, const RawAddress& bda_peer) {
    body(addr_type_peer, bda_peer);
  };
};
extern struct btsnd_hcic_ble_rm_device_resolving_list
    btsnd_hcic_ble_rm_device_resolving_list;

// Name: btsnd_hcic_ble_set_addr_resolution_enable
// Params: uint8_t addr_resolution_enable
// Return: void
struct btsnd_hcic_ble_set_addr_resolution_enable {
  std::function<void(uint8_t addr_resolution_enable)> body{
      [](uint8_t addr_resolution_enable) {}};
  void operator()(uint8_t addr_resolution_enable) {
    body(addr_resolution_enable);
  };
};
extern struct btsnd_hcic_ble_set_addr_resolution_enable
    btsnd_hcic_ble_set_addr_resolution_enable;

// Name: btsnd_hcic_ble_set_adv_data
// Params: uint8_t data_len, uint8_t* p_data
// Return: void
struct btsnd_hcic_ble_set_adv_data {
  std::function<void(uint8_t data_len, uint8_t* p_data)> body{
      [](uint8_t data_len, uint8_t* p_data) {}};
  void operator()(uint8_t data_len, uint8_t* p_data) {
    body(data_len, p_data);
  };
};
extern struct btsnd_hcic_ble_set_adv_data btsnd_hcic_ble_set_adv_data;

// Name: btsnd_hcic_ble_set_adv_enable
// Params: uint8_t adv_enable
// Return: void
struct btsnd_hcic_ble_set_adv_enable {
  std::function<void(uint8_t adv_enable)> body{[](uint8_t adv_enable) {}};
  void operator()(uint8_t adv_enable) { body(adv_enable); };
};
extern struct btsnd_hcic_ble_set_adv_enable btsnd_hcic_ble_set_adv_enable;

// Name: btsnd_hcic_ble_set_data_length
// Params: uint16_t conn_handle, uint16_t tx_octets, uint16_t tx_time
// Return: void
struct btsnd_hcic_ble_set_data_length {
  std::function<void(uint16_t conn_handle, uint16_t tx_octets,
                     uint16_t tx_time)>
      body{[](uint16_t conn_handle, uint16_t tx_octets, uint16_t tx_time) {}};
  void operator()(uint16_t conn_handle, uint16_t tx_octets, uint16_t tx_time) {
    body(conn_handle, tx_octets, tx_time);
  };
};
extern struct btsnd_hcic_ble_set_data_length btsnd_hcic_ble_set_data_length;

// Name: btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params
// Params: uint16_t conn_handle, uint8_t mode, uint16_t skip, uint16_t
// sync_timeout, uint8_t cte_type, base::OnceCallback<void(uint8_t*, uint16_t
// Return: void
struct btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params {
  std::function<void(uint16_t, uint8_t, uint16_t, uint16_t, uint8_t,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint16_t conn_handle, uint8_t mode, uint16_t skip,
              uint16_t sync_timeout, uint8_t cte_type,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint16_t conn_handle, uint8_t mode, uint16_t skip,
                  uint16_t sync_timeout, uint8_t cte_type,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(conn_handle, mode, skip, sync_timeout, cte_type, std::move(cb));
  };
};
extern struct
    btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params
        btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params;

// Name: btsnd_hcic_ble_set_extended_scan_enable
// Params: uint8_t enable, uint8_t filter_duplicates, uint16_t duration,
// uint16_t period Return: void
struct btsnd_hcic_ble_set_extended_scan_enable {
  std::function<void(uint8_t enable, uint8_t filter_duplicates,
                     uint16_t duration, uint16_t period)>
      body{[](uint8_t enable, uint8_t filter_duplicates, uint16_t duration,
              uint16_t period) {}};
  void operator()(uint8_t enable, uint8_t filter_duplicates, uint16_t duration,
                  uint16_t period) {
    body(enable, filter_duplicates, duration, period);
  };
};
extern struct btsnd_hcic_ble_set_extended_scan_enable
    btsnd_hcic_ble_set_extended_scan_enable;

// Name: btsnd_hcic_ble_set_extended_scan_params
// Params: uint8_t own_address_type, uint8_t scanning_filter_policy, uint8_t
// scanning_phys, scanning_phy_cfg* phy_cfg Return: void
struct btsnd_hcic_ble_set_extended_scan_params {
  std::function<void(uint8_t own_address_type, uint8_t scanning_filter_policy,
                     uint8_t scanning_phys, scanning_phy_cfg* phy_cfg)>
      body{[](uint8_t own_address_type, uint8_t scanning_filter_policy,
              uint8_t scanning_phys, scanning_phy_cfg* phy_cfg) {}};
  void operator()(uint8_t own_address_type, uint8_t scanning_filter_policy,
                  uint8_t scanning_phys, scanning_phy_cfg* phy_cfg) {
    body(own_address_type, scanning_filter_policy, scanning_phys, phy_cfg);
  };
};
extern struct btsnd_hcic_ble_set_extended_scan_params
    btsnd_hcic_ble_set_extended_scan_params;

// Name: btsnd_hcic_ble_set_host_chnl_class
// Params: uint8_t chnl_map[HCIC_BLE_CHNL_MAP_SIZE]
// Return: void
struct btsnd_hcic_ble_set_host_chnl_class {
  std::function<void(uint8_t chnl_map[HCIC_BLE_CHNL_MAP_SIZE])> body{
      [](uint8_t chnl_map[HCIC_BLE_CHNL_MAP_SIZE]) {}};
  void operator()(uint8_t chnl_map[HCIC_BLE_CHNL_MAP_SIZE]) { body(chnl_map); };
};
extern struct btsnd_hcic_ble_set_host_chnl_class
    btsnd_hcic_ble_set_host_chnl_class;

// Name: btsnd_hcic_ble_set_local_used_feat
// Params: uint8_t feat_set[8]
// Return: void
struct btsnd_hcic_ble_set_local_used_feat {
  std::function<void(uint8_t feat_set[8])> body{[](uint8_t feat_set[8]) {}};
  void operator()(uint8_t feat_set[]) { body(feat_set); };
};
extern struct btsnd_hcic_ble_set_local_used_feat
    btsnd_hcic_ble_set_local_used_feat;

// Name: btsnd_hcic_ble_set_periodic_advertising_receive_enable
// Params: uint16_t sync_handle, bool enable, base::OnceCallback<void(uint8_t*,
// uint16_t Return: void
struct btsnd_hcic_ble_set_periodic_advertising_receive_enable {
  std::function<void(uint16_t, bool,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint16_t sync_handle, bool enable,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint16_t sync_handle, bool enable,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(sync_handle, enable, std::move(cb));
  };
};
extern struct btsnd_hcic_ble_set_periodic_advertising_receive_enable
    btsnd_hcic_ble_set_periodic_advertising_receive_enable;

// Name: btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params
// Params: uint16_t conn_handle, uint8_t mode, uint16_t skip, uint16_t
// sync_timeout, uint8_t cte_type, base::OnceCallback<void(uint8_t*, uint16_t
// Return: void
struct btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params {
  std::function<void(uint16_t, uint8_t, uint16_t, uint16_t, uint8_t,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint16_t conn_handle, uint8_t mode, uint16_t skip,
              uint16_t sync_timeout, uint8_t cte_type,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint16_t conn_handle, uint8_t mode, uint16_t skip,
                  uint16_t sync_timeout, uint8_t cte_type,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(conn_handle, mode, skip, sync_timeout, cte_type, std::move(cb));
  };
};
extern struct btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params
    btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params;

// Name: btsnd_hcic_ble_set_privacy_mode
// Params: uint8_t addr_type_peer, const RawAddress& bda_peer, uint8_t
// privacy_type Return: void
struct btsnd_hcic_ble_set_privacy_mode {
  std::function<void(uint8_t addr_type_peer, const RawAddress& bda_peer,
                     uint8_t privacy_type)>
      body{[](uint8_t addr_type_peer, const RawAddress& bda_peer,
              uint8_t privacy_type) {}};
  void operator()(uint8_t addr_type_peer, const RawAddress& bda_peer,
                  uint8_t privacy_type) {
    body(addr_type_peer, bda_peer, privacy_type);
  };
};
extern struct btsnd_hcic_ble_set_privacy_mode btsnd_hcic_ble_set_privacy_mode;

// Name: btsnd_hcic_ble_set_rand_priv_addr_timeout
// Params: uint16_t rpa_timout
// Return: void
struct btsnd_hcic_ble_set_rand_priv_addr_timeout {
  std::function<void(uint16_t rpa_timout)> body{[](uint16_t rpa_timout) {}};
  void operator()(uint16_t rpa_timout) { body(rpa_timout); };
};
extern struct btsnd_hcic_ble_set_rand_priv_addr_timeout
    btsnd_hcic_ble_set_rand_priv_addr_timeout;

// Name: btsnd_hcic_ble_set_random_addr
// Params: const RawAddress& random_bda
// Return: void
struct btsnd_hcic_ble_set_random_addr {
  std::function<void(const RawAddress& random_bda)> body{
      [](const RawAddress& random_bda) {}};
  void operator()(const RawAddress& random_bda) { body(random_bda); };
};
extern struct btsnd_hcic_ble_set_random_addr btsnd_hcic_ble_set_random_addr;

// Name: btsnd_hcic_ble_set_scan_enable
// Params: uint8_t scan_enable, uint8_t duplicate
// Return: void
struct btsnd_hcic_ble_set_scan_enable {
  std::function<void(uint8_t scan_enable, uint8_t duplicate)> body{
      [](uint8_t scan_enable, uint8_t duplicate) {}};
  void operator()(uint8_t scan_enable, uint8_t duplicate) {
    body(scan_enable, duplicate);
  };
};
extern struct btsnd_hcic_ble_set_scan_enable btsnd_hcic_ble_set_scan_enable;

// Name: btsnd_hcic_ble_set_scan_params
// Params: uint8_t scan_type, uint16_t scan_int, uint16_t scan_win, uint8_t
// addr_type_own, uint8_t scan_filter_policy Return: void
struct btsnd_hcic_ble_set_scan_params {
  std::function<void(uint8_t scan_type, uint16_t scan_int, uint16_t scan_win,
                     uint8_t addr_type_own, uint8_t scan_filter_policy)>
      body{[](uint8_t scan_type, uint16_t scan_int, uint16_t scan_win,
              uint8_t addr_type_own, uint8_t scan_filter_policy) {}};
  void operator()(uint8_t scan_type, uint16_t scan_int, uint16_t scan_win,
                  uint8_t addr_type_own, uint8_t scan_filter_policy) {
    body(scan_type, scan_int, scan_win, addr_type_own, scan_filter_policy);
  };
};
extern struct btsnd_hcic_ble_set_scan_params btsnd_hcic_ble_set_scan_params;

// Name: btsnd_hcic_ble_set_scan_rsp_data
// Params: uint8_t data_len, uint8_t* p_scan_rsp
// Return: void
struct btsnd_hcic_ble_set_scan_rsp_data {
  std::function<void(uint8_t data_len, uint8_t* p_scan_rsp)> body{
      [](uint8_t data_len, uint8_t* p_scan_rsp) {}};
  void operator()(uint8_t data_len, uint8_t* p_scan_rsp) {
    body(data_len, p_scan_rsp);
  };
};
extern struct btsnd_hcic_ble_set_scan_rsp_data btsnd_hcic_ble_set_scan_rsp_data;

// Name: btsnd_hcic_ble_start_enc
// Params: uint16_t handle, uint8_t rand[HCIC_BLE_RAND_DI_SIZE], uint16_t ediv,
// const Octet16& ltk Return: void
struct btsnd_hcic_ble_start_enc {
  std::function<void(uint16_t handle, uint8_t rand[HCIC_BLE_RAND_DI_SIZE],
                     uint16_t ediv, const Octet16& ltk)>
      body{[](uint16_t handle, uint8_t rand[HCIC_BLE_RAND_DI_SIZE],
              uint16_t ediv, const Octet16& ltk) {}};
  void operator()(uint16_t handle, uint8_t rand[HCIC_BLE_RAND_DI_SIZE],
                  uint16_t ediv, const Octet16& ltk) {
    body(handle, rand, ediv, ltk);
  };
};
extern struct btsnd_hcic_ble_start_enc btsnd_hcic_ble_start_enc;

// Name: btsnd_hcic_ble_test_end
// Params: void
// Return: void
struct btsnd_hcic_ble_test_end {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btsnd_hcic_ble_test_end btsnd_hcic_ble_test_end;

// Name: btsnd_hcic_ble_transmitter_test
// Params: uint8_t tx_freq, uint8_t test_data_len, uint8_t payload
// Return: void
struct btsnd_hcic_ble_transmitter_test {
  std::function<void(uint8_t tx_freq, uint8_t test_data_len, uint8_t payload)>
      body{[](uint8_t tx_freq, uint8_t test_data_len, uint8_t payload) {}};
  void operator()(uint8_t tx_freq, uint8_t test_data_len, uint8_t payload) {
    body(tx_freq, test_data_len, payload);
  };
};
extern struct btsnd_hcic_ble_transmitter_test btsnd_hcic_ble_transmitter_test;

// Name: btsnd_hcic_ble_upd_ll_conn_params
// Params: uint16_t handle, uint16_t conn_int_min, uint16_t conn_int_max,
// uint16_t conn_latency, uint16_t conn_timeout, uint16_t min_ce_len, uint16_t
// max_ce_len Return: void
struct btsnd_hcic_ble_upd_ll_conn_params {
  std::function<void(uint16_t handle, uint16_t conn_int_min,
                     uint16_t conn_int_max, uint16_t conn_latency,
                     uint16_t conn_timeout, uint16_t min_ce_len,
                     uint16_t max_ce_len)>
      body{[](uint16_t handle, uint16_t conn_int_min, uint16_t conn_int_max,
              uint16_t conn_latency, uint16_t conn_timeout, uint16_t min_ce_len,
              uint16_t max_ce_len) {}};
  void operator()(uint16_t handle, uint16_t conn_int_min, uint16_t conn_int_max,
                  uint16_t conn_latency, uint16_t conn_timeout,
                  uint16_t min_ce_len, uint16_t max_ce_len) {
    body(handle, conn_int_min, conn_int_max, conn_latency, conn_timeout,
         min_ce_len, max_ce_len);
  };
};
extern struct btsnd_hcic_ble_upd_ll_conn_params
    btsnd_hcic_ble_upd_ll_conn_params;

// Name: btsnd_hcic_ble_write_adv_params
// Params: uint16_t adv_int_min, uint16_t adv_int_max, uint8_t adv_type,
// tBLE_ADDR_TYPE addr_type_own, tBLE_ADDR_TYPE addr_type_dir, const RawAddress&
// direct_bda, uint8_t channel_map, uint8_t adv_filter_policy Return: void
struct btsnd_hcic_ble_write_adv_params {
  std::function<void(uint16_t adv_int_min, uint16_t adv_int_max,
                     uint8_t adv_type, tBLE_ADDR_TYPE addr_type_own,
                     tBLE_ADDR_TYPE addr_type_dir, const RawAddress& direct_bda,
                     uint8_t channel_map, uint8_t adv_filter_policy)>
      body{[](uint16_t adv_int_min, uint16_t adv_int_max, uint8_t adv_type,
              tBLE_ADDR_TYPE addr_type_own, tBLE_ADDR_TYPE addr_type_dir,
              const RawAddress& direct_bda, uint8_t channel_map,
              uint8_t adv_filter_policy) {}};
  void operator()(uint16_t adv_int_min, uint16_t adv_int_max, uint8_t adv_type,
                  tBLE_ADDR_TYPE addr_type_own, tBLE_ADDR_TYPE addr_type_dir,
                  const RawAddress& direct_bda, uint8_t channel_map,
                  uint8_t adv_filter_policy) {
    body(adv_int_min, adv_int_max, adv_type, addr_type_own, addr_type_dir,
         direct_bda, channel_map, adv_filter_policy);
  };
};
extern struct btsnd_hcic_ble_write_adv_params btsnd_hcic_ble_write_adv_params;

// Name: btsnd_hcic_create_big
// Params: uint8_t big_handle, uint8_t adv_handle, uint8_t num_bis, uint32_t
// sdu_itv, uint16_t max_sdu_size, uint16_t transport_latency, uint8_t rtn,
// uint8_t phy, uint8_t packing, uint8_t framing, uint8_t enc,
// std::array<uint8_t, 16> bcst_code Return: void
struct btsnd_hcic_create_big {
  std::function<void(uint8_t big_handle, uint8_t adv_handle, uint8_t num_bis,
                     uint32_t sdu_itv, uint16_t max_sdu_size,
                     uint16_t transport_latency, uint8_t rtn, uint8_t phy,
                     uint8_t packing, uint8_t framing, uint8_t enc,
                     std::array<uint8_t, 16> bcst_code)>
      body{[](uint8_t big_handle, uint8_t adv_handle, uint8_t num_bis,
              uint32_t sdu_itv, uint16_t max_sdu_size,
              uint16_t transport_latency, uint8_t rtn, uint8_t phy,
              uint8_t packing, uint8_t framing, uint8_t enc,
              std::array<uint8_t, 16> bcst_code) {}};
  void operator()(uint8_t big_handle, uint8_t adv_handle, uint8_t num_bis,
                  uint32_t sdu_itv, uint16_t max_sdu_size,
                  uint16_t transport_latency, uint8_t rtn, uint8_t phy,
                  uint8_t packing, uint8_t framing, uint8_t enc,
                  std::array<uint8_t, 16> bcst_code) {
    body(big_handle, adv_handle, num_bis, sdu_itv, max_sdu_size,
         transport_latency, rtn, phy, packing, framing, enc, bcst_code);
  };
};
extern struct btsnd_hcic_create_big btsnd_hcic_create_big;

// Name: btsnd_hcic_create_cis
// Params: uint8_t num_cis, const EXT_CIS_CREATE_CFG* cis_cfg,
// base::OnceCallback<void(uint8_t*, uint16_t Return: void
struct btsnd_hcic_create_cis {
  std::function<void(uint8_t, const EXT_CIS_CREATE_CFG*,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint8_t num_cis, const EXT_CIS_CREATE_CFG* cis_cfg,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint8_t num_cis, const EXT_CIS_CREATE_CFG* cis_cfg,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(num_cis, cis_cfg, std::move(cb));
  };
};
extern struct btsnd_hcic_create_cis btsnd_hcic_create_cis;

// Name: btsnd_hcic_read_iso_link_quality
// Params: uint16_t iso_handle, base::OnceCallback<void(uint8_t*, uint16_t
// Return: void
struct btsnd_hcic_read_iso_link_quality {
  std::function<void(uint16_t, base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint16_t iso_handle,
              base::OnceCallback<void(uint8_t*, uint16_t)>) {}};
  void operator()(uint16_t iso_handle,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(iso_handle, std::move(cb));
  };
};
extern struct btsnd_hcic_read_iso_link_quality btsnd_hcic_read_iso_link_quality;

// Name: btsnd_hcic_read_iso_tx_sync
// Params: uint16_t iso_handle, base::OnceCallback<void(uint8_t*, uint16_t
// Return: void
struct btsnd_hcic_read_iso_tx_sync {
  std::function<void(uint16_t, base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint16_t iso_handle,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint16_t iso_handle,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(iso_handle, std::move(cb));
  };
};
extern struct btsnd_hcic_read_iso_tx_sync btsnd_hcic_read_iso_tx_sync;

// Name: btsnd_hcic_rej_cis_req
// Params: uint16_t conn_handle, uint8_t reason,
// base::OnceCallback<void(uint8_t*, uint16_t Return: void
struct btsnd_hcic_rej_cis_req {
  std::function<void(uint16_t, uint8_t,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint16_t conn_handle, uint8_t reason,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint16_t conn_handle, uint8_t reason,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(conn_handle, reason, std::move(cb));
  };
};
extern struct btsnd_hcic_rej_cis_req btsnd_hcic_rej_cis_req;

// Name: btsnd_hcic_remove_cig
// Params: uint8_t cig_id, base::OnceCallback<void(uint8_t*, uint16_t
// Return: void
struct btsnd_hcic_remove_cig {
  std::function<void(uint8_t, base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint8_t cig_id, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
      }};
  void operator()(uint8_t cig_id,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(cig_id, std::move(cb));
  };
};
extern struct btsnd_hcic_remove_cig btsnd_hcic_remove_cig;

// Name: btsnd_hcic_remove_iso_data_path
// Params: uint16_t iso_handle, uint8_t data_path_dir,
// base::OnceCallback<void(uint8_t*, uint16_t Return: void
struct btsnd_hcic_remove_iso_data_path {
  std::function<void(uint16_t, uint8_t,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint16_t iso_handle, uint8_t data_path_dir,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint16_t iso_handle, uint8_t data_path_dir,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(iso_handle, data_path_dir, std::move(cb));
  };
};
extern struct btsnd_hcic_remove_iso_data_path btsnd_hcic_remove_iso_data_path;

// Name: btsnd_hcic_req_peer_sca
// Params: uint16_t conn_handle
// Return: void
struct btsnd_hcic_req_peer_sca {
  std::function<void(uint16_t)> body{[](uint16_t conn_handle) {}};
  void operator()(uint16_t conn_handle) { body(conn_handle); };
};
extern struct btsnd_hcic_req_peer_sca btsnd_hcic_req_peer_sca;

// Name: btsnd_hcic_set_cig_params
// Params: uint8_t cig_id, uint32_t sdu_itv_mtos, uint32_t sdu_itv_stom, uint8_t
// sca, uint8_t packing, uint8_t framing, uint16_t max_trans_lat_stom, uint16_t
// max_trans_lat_mtos, uint8_t cis_cnt, const EXT_CIS_CFG* cis_cfg,
// base::OnceCallback<void(uint8_t*, uint16_t Return: void
struct btsnd_hcic_set_cig_params {
  std::function<void(uint8_t, uint32_t, uint32_t, uint8_t, uint8_t, uint8_t,
                     uint16_t, uint16_t, uint8_t, const EXT_CIS_CFG*,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint8_t cig_id, uint32_t sdu_itv_mtos, uint32_t sdu_itv_stom,
              uint8_t sca, uint8_t packing, uint8_t framing,
              uint16_t max_trans_lat_stom, uint16_t max_trans_lat_mtos,
              uint8_t cis_cnt, const EXT_CIS_CFG* cis_cfg,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint8_t cig_id, uint32_t sdu_itv_mtos, uint32_t sdu_itv_stom,
                  uint8_t sca, uint8_t packing, uint8_t framing,
                  uint16_t max_trans_lat_stom, uint16_t max_trans_lat_mtos,
                  uint8_t cis_cnt, const EXT_CIS_CFG* cis_cfg,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(cig_id, sdu_itv_mtos, sdu_itv_stom, sca, packing, framing,
         max_trans_lat_stom, max_trans_lat_mtos, cis_cnt, cis_cfg,
         std::move(cb));
  };
};
extern struct btsnd_hcic_set_cig_params btsnd_hcic_set_cig_params;

// Name: btsnd_hcic_setup_iso_data_path
// Params: uint16_t iso_handle, uint8_t data_path_dir, uint8_t data_path_id,
// uint8_t codec_id_format, uint16_t codec_id_company, uint16_t codec_id_vendor,
// uint32_t controller_delay, std::vector<uint8_t> codec_conf,
// base::OnceCallback<void(uint8_t*, uint16_t Return: void
struct btsnd_hcic_setup_iso_data_path {
  std::function<void(uint16_t, uint8_t, uint8_t, uint8_t, uint16_t, uint16_t,
                     uint32_t, std::vector<uint8_t>,
                     base::OnceCallback<void(uint8_t*, uint16_t)>)>
      body{[](uint16_t iso_handle, uint8_t data_path_dir, uint8_t data_path_id,
              uint8_t codec_id_format, uint16_t codec_id_company,
              uint16_t codec_id_vendor, uint32_t controller_delay,
              std::vector<uint8_t> codec_conf,
              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {}};
  void operator()(uint16_t iso_handle, uint8_t data_path_dir,
                  uint8_t data_path_id, uint8_t codec_id_format,
                  uint16_t codec_id_company, uint16_t codec_id_vendor,
                  uint32_t controller_delay, std::vector<uint8_t> codec_conf,
                  base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
    body(iso_handle, data_path_dir, data_path_id, codec_id_format,
         codec_id_company, codec_id_vendor, controller_delay, codec_conf,
         std::move(cb));
  };
};
extern struct btsnd_hcic_setup_iso_data_path btsnd_hcic_setup_iso_data_path;

// Name: btsnd_hcic_term_big
// Params: uint8_t big_handle, uint8_t reason
// Return: void
struct btsnd_hcic_term_big {
  std::function<void(uint8_t, uint8_t)> body{
      [](uint8_t big_handle, uint8_t reason) {}};
  void operator()(uint8_t big_handle, uint8_t reason) {
    body(big_handle, reason);
  };
};
extern struct btsnd_hcic_term_big btsnd_hcic_term_big;

}  // namespace stack_hcic_hciblecmds
}  // namespace mock
}  // namespace test

// END mockcify generation

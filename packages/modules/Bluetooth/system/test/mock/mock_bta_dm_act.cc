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
 *   Functions generated:62
 *
 *  mockcify.pl ver 0.3.0
 */

#include <cstdint>
#include <functional>
#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

// Mock include file to share data between tests and mock
#include "test/mock/mock_bta_dm_act.h"
#include "types/raw_address.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace bta_dm_act {

// Function state capture and return values, if needed
struct BTA_DmSetVisibility BTA_DmSetVisibility;
struct BTA_dm_acl_down BTA_dm_acl_down;
struct BTA_dm_acl_up BTA_dm_acl_up;
struct BTA_dm_notify_remote_features_complete
    BTA_dm_notify_remote_features_complete;
struct BTA_dm_on_hw_off BTA_dm_on_hw_off;
struct BTA_dm_on_hw_on BTA_dm_on_hw_on;
struct BTA_dm_report_role_change BTA_dm_report_role_change;
struct bta_dm_acl_up bta_dm_acl_up;
struct bta_dm_add_ble_device bta_dm_add_ble_device;
struct bta_dm_add_blekey bta_dm_add_blekey;
struct bta_dm_add_device bta_dm_add_device;
struct bta_dm_ble_config_local_privacy bta_dm_ble_config_local_privacy;
struct bta_dm_ble_confirm_reply bta_dm_ble_confirm_reply;
struct bta_dm_ble_csis_observe bta_dm_ble_csis_observe;
struct bta_dm_ble_get_energy_info bta_dm_ble_get_energy_info;
struct bta_dm_ble_observe bta_dm_ble_observe;
struct bta_dm_ble_passkey_reply bta_dm_ble_passkey_reply;
struct bta_dm_ble_scan bta_dm_ble_scan;
struct bta_dm_ble_set_conn_params bta_dm_ble_set_conn_params;
struct bta_dm_ble_set_data_length bta_dm_ble_set_data_length;
struct bta_dm_ble_update_conn_params bta_dm_ble_update_conn_params;
struct bta_dm_bond bta_dm_bond;
struct bta_dm_bond_cancel bta_dm_bond_cancel;
struct bta_dm_check_if_only_hd_connected bta_dm_check_if_only_hd_connected;
struct bta_dm_ci_rmt_oob_act bta_dm_ci_rmt_oob_act;
struct bta_dm_close_acl bta_dm_close_acl;
struct bta_dm_close_gatt_conn bta_dm_close_gatt_conn;
struct bta_dm_confirm bta_dm_confirm;
struct bta_dm_deinit_cb bta_dm_deinit_cb;
struct bta_dm_disable bta_dm_disable;
struct bta_dm_disc_result bta_dm_disc_result;
struct bta_dm_disc_rmt_name bta_dm_disc_rmt_name;
struct bta_dm_discover bta_dm_discover;
struct bta_dm_eir_update_cust_uuid bta_dm_eir_update_cust_uuid;
struct bta_dm_eir_update_uuid bta_dm_eir_update_uuid;
struct bta_dm_enable bta_dm_enable;
struct bta_dm_encrypt_cback bta_dm_encrypt_cback;
struct bta_dm_execute_queued_request bta_dm_execute_queued_request;
struct bta_dm_free_sdp_db bta_dm_free_sdp_db;
struct bta_dm_init_cb bta_dm_init_cb;
struct bta_dm_inq_cmpl bta_dm_inq_cmpl;
struct bta_dm_is_search_request_queued bta_dm_is_search_request_queued;
struct bta_dm_pin_reply bta_dm_pin_reply;
struct bta_dm_proc_open_evt bta_dm_proc_open_evt;
struct bta_dm_process_remove_device bta_dm_process_remove_device;
struct bta_dm_queue_disc bta_dm_queue_disc;
struct bta_dm_queue_search bta_dm_queue_search;
struct bta_dm_remove_device bta_dm_remove_device;
struct bta_dm_rm_cback bta_dm_rm_cback;
struct bta_dm_rmt_name bta_dm_rmt_name;
struct bta_dm_sdp_result bta_dm_sdp_result;
struct bta_dm_search_cancel bta_dm_search_cancel;
struct bta_dm_search_cancel_notify bta_dm_search_cancel_notify;
struct bta_dm_search_clear_queue bta_dm_search_clear_queue;
struct bta_dm_search_cmpl bta_dm_search_cmpl;
struct bta_dm_search_result bta_dm_search_result;
struct bta_dm_search_start bta_dm_search_start;
struct bta_dm_set_dev_name bta_dm_set_dev_name;
struct bta_dm_set_encryption bta_dm_set_encryption;
struct btm_dm_start_gatt_discovery btm_dm_start_gatt_discovery;
struct handle_remote_features_complete handle_remote_features_complete;

}  // namespace bta_dm_act
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool BTA_DmSetVisibility(bt_scan_mode_t mode) {
  mock_function_count_map[__func__]++;
  return test::mock::bta_dm_act::BTA_DmSetVisibility(mode);
}
void BTA_dm_acl_down(const RawAddress bd_addr, tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::BTA_dm_acl_down(bd_addr, transport);
}
void BTA_dm_acl_up(const RawAddress bd_addr, tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::BTA_dm_acl_up(bd_addr, transport);
}
void BTA_dm_notify_remote_features_complete(const RawAddress bd_addr) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::BTA_dm_notify_remote_features_complete(bd_addr);
}
void BTA_dm_on_hw_off() {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::BTA_dm_on_hw_off();
}
void BTA_dm_on_hw_on() {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::BTA_dm_on_hw_on();
}
void BTA_dm_report_role_change(const RawAddress bd_addr, tHCI_ROLE new_role,
                               tHCI_STATUS hci_status) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::BTA_dm_report_role_change(bd_addr, new_role,
                                                    hci_status);
}
void bta_dm_acl_up(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_acl_up(bd_addr, transport);
}
void bta_dm_add_ble_device(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                           tBT_DEVICE_TYPE dev_type) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_add_ble_device(bd_addr, addr_type, dev_type);
}
void bta_dm_add_blekey(const RawAddress& bd_addr, tBTA_LE_KEY_VALUE blekey,
                       tBTM_LE_KEY_TYPE key_type) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_add_blekey(bd_addr, blekey, key_type);
}
void bta_dm_add_device(std::unique_ptr<tBTA_DM_API_ADD_DEVICE> msg) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_add_device(std::move(msg));
}
void bta_dm_ble_config_local_privacy(bool privacy_enable) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_ble_config_local_privacy(privacy_enable);
}
void bta_dm_ble_confirm_reply(const RawAddress& bd_addr, bool accept) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_ble_confirm_reply(bd_addr, accept);
}
void bta_dm_ble_csis_observe(bool observe, tBTA_DM_SEARCH_CBACK* p_cback) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_ble_csis_observe(observe, p_cback);
}
void bta_dm_ble_get_energy_info(
    tBTA_BLE_ENERGY_INFO_CBACK* p_energy_info_cback) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_ble_get_energy_info(p_energy_info_cback);
}
void bta_dm_ble_observe(bool start, uint8_t duration,
                        tBTA_DM_SEARCH_CBACK* p_cback) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_ble_observe(start, duration, p_cback);
}
void bta_dm_ble_passkey_reply(const RawAddress& bd_addr, bool accept,
                              uint32_t passkey) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_ble_passkey_reply(bd_addr, accept, passkey);
}
void bta_dm_ble_scan(bool start, uint8_t duration_sec) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_ble_scan(start, duration_sec);
}
void bta_dm_ble_set_conn_params(const RawAddress& bd_addr,
                                uint16_t conn_int_min, uint16_t conn_int_max,
                                uint16_t peripheral_latency,
                                uint16_t supervision_tout) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_ble_set_conn_params(
      bd_addr, conn_int_min, conn_int_max, peripheral_latency,
      supervision_tout);
}
void bta_dm_ble_set_data_length(const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_ble_set_data_length(bd_addr);
}
void bta_dm_ble_update_conn_params(const RawAddress& bd_addr, uint16_t min_int,
                                   uint16_t max_int, uint16_t latency,
                                   uint16_t timeout, uint16_t min_ce_len,
                                   uint16_t max_ce_len) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_ble_update_conn_params(
      bd_addr, min_int, max_int, latency, timeout, min_ce_len, max_ce_len);
}
void bta_dm_bond(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                 tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_bond(bd_addr, addr_type, transport,
                                      device_type);
}
void bta_dm_bond_cancel(const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_bond_cancel(bd_addr);
}
bool bta_dm_check_if_only_hd_connected(const RawAddress& peer_addr) {
  mock_function_count_map[__func__]++;
  return test::mock::bta_dm_act::bta_dm_check_if_only_hd_connected(peer_addr);
}
void bta_dm_ci_rmt_oob_act(std::unique_ptr<tBTA_DM_CI_RMT_OOB> msg) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_ci_rmt_oob_act(std::move(msg));
}
void bta_dm_close_acl(const RawAddress& bd_addr, bool remove_dev,
                      tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_close_acl(bd_addr, remove_dev, transport);
}
void bta_dm_close_gatt_conn(tBTA_DM_MSG* p_data) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_close_gatt_conn(p_data);
}
void bta_dm_confirm(const RawAddress& bd_addr, bool accept) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_confirm(bd_addr, accept);
}
void bta_dm_deinit_cb(void) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_deinit_cb();
}
void bta_dm_disable() {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_disable();
}
void bta_dm_disc_result(tBTA_DM_MSG* p_data) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_disc_result(p_data);
}
void bta_dm_disc_rmt_name(tBTA_DM_MSG* p_data) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_disc_rmt_name(p_data);
}
void bta_dm_discover(tBTA_DM_MSG* p_data) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_discover(p_data);
}
void bta_dm_eir_update_cust_uuid(const tBTA_CUSTOM_UUID& curr, bool adding) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_eir_update_cust_uuid(curr, adding);
}
void bta_dm_eir_update_uuid(uint16_t uuid16, bool adding) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_eir_update_uuid(uuid16, adding);
}
void bta_dm_enable(tBTA_DM_SEC_CBACK* p_sec_cback) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_enable(p_sec_cback);
}
void bta_dm_encrypt_cback(const RawAddress* bd_addr, tBT_TRANSPORT transport,
                          void* p_ref_data, tBTM_STATUS result) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_encrypt_cback(bd_addr, transport, p_ref_data,
                                               result);
}
void bta_dm_execute_queued_request() {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_execute_queued_request();
}
void bta_dm_free_sdp_db() {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_free_sdp_db();
}
void bta_dm_init_cb(void) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_init_cb();
}
void bta_dm_inq_cmpl(uint8_t num) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_inq_cmpl(num);
}
bool bta_dm_is_search_request_queued() {
  mock_function_count_map[__func__]++;
  return test::mock::bta_dm_act::bta_dm_is_search_request_queued();
}
void bta_dm_pin_reply(std::unique_ptr<tBTA_DM_API_PIN_REPLY> msg) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_pin_reply(std::move(msg));
}
void bta_dm_proc_open_evt(tBTA_GATTC_OPEN* p_data) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_proc_open_evt(p_data);
}
void bta_dm_process_remove_device(const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_process_remove_device(bd_addr);
}
void bta_dm_queue_disc(tBTA_DM_MSG* p_data) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_queue_disc(p_data);
}
void bta_dm_queue_search(tBTA_DM_MSG* p_data) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_queue_search(p_data);
}
void bta_dm_remove_device(const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_remove_device(bd_addr);
}
void bta_dm_rm_cback(tBTA_SYS_CONN_STATUS status, uint8_t id, uint8_t app_id,
                     const RawAddress& peer_addr) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_rm_cback(status, id, app_id, peer_addr);
}
void bta_dm_rmt_name(tBTA_DM_MSG* p_data) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_rmt_name(p_data);
}
void bta_dm_sdp_result(tBTA_DM_MSG* p_data) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_sdp_result(p_data);
}
void bta_dm_search_cancel() {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_search_cancel();
}
void bta_dm_search_cancel_notify() {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_search_cancel_notify();
}
void bta_dm_search_clear_queue() {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_search_clear_queue();
}
void bta_dm_search_cmpl() {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_search_cmpl();
}
void bta_dm_search_result(tBTA_DM_MSG* p_data) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_search_result(p_data);
}
void bta_dm_search_start(tBTA_DM_MSG* p_data) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_search_start(p_data);
}
void bta_dm_set_dev_name(const std::vector<uint8_t>& name) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_set_dev_name(name);
}
void bta_dm_set_encryption(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                           tBTA_DM_ENCRYPT_CBACK* p_callback,
                           tBTM_BLE_SEC_ACT sec_act) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::bta_dm_set_encryption(bd_addr, transport, p_callback,
                                                sec_act);
}
void btm_dm_start_gatt_discovery(const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::btm_dm_start_gatt_discovery(bd_addr);
}
void handle_remote_features_complete(const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_act::handle_remote_features_complete(bd_addr);
}

// END mockcify generation

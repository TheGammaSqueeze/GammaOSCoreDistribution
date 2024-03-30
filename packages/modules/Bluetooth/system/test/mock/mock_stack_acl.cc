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
 *   Functions generated:125
 *
 *  mockcify.pl ver 0.2.1
 */

#include <cstdint>
#include <functional>
#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

// Mock include file to share data between tests and mock
#include "stack/include/bt_hdr.h"
#include "test/mock/mock_stack_acl.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
#ifndef UNUSED_ATTR
#define UNUSED_ATTR
#endif

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_acl {

// Function state capture and return values, if needed
struct ACL_SupportTransparentSynchronousData
    ACL_SupportTransparentSynchronousData;
struct BTM_BLE_IS_RESOLVE_BDA BTM_BLE_IS_RESOLVE_BDA;
struct BTM_IsAclConnectionUp BTM_IsAclConnectionUp;
struct BTM_IsAclConnectionUpAndHandleValid BTM_IsAclConnectionUpAndHandleValid;
struct BTM_IsAclConnectionUpFromHandle BTM_IsAclConnectionUpFromHandle;
struct BTM_IsBleConnection BTM_IsBleConnection;
struct BTM_IsPhy2mSupported BTM_IsPhy2mSupported;
struct BTM_ReadRemoteConnectionAddr BTM_ReadRemoteConnectionAddr;
struct BTM_ReadRemoteVersion BTM_ReadRemoteVersion;
struct BTM_is_sniff_allowed_for BTM_is_sniff_allowed_for;
struct acl_create_le_connection acl_create_le_connection;
struct acl_create_le_connection_with_id acl_create_le_connection_with_id;
struct acl_is_role_switch_allowed acl_is_role_switch_allowed;
struct acl_is_switch_role_idle acl_is_switch_role_idle;
struct acl_peer_supports_ble_2m_phy acl_peer_supports_ble_2m_phy;
struct acl_peer_supports_ble_coded_phy acl_peer_supports_ble_coded_phy;
struct acl_send_data_packet_br_edr acl_send_data_packet_br_edr;
struct acl_peer_supports_ble_connection_parameters_request
    acl_peer_supports_ble_connection_parameters_request;
struct acl_peer_supports_ble_packet_extension
    acl_peer_supports_ble_packet_extension;
struct acl_peer_supports_sniff_subrating acl_peer_supports_sniff_subrating;
struct acl_refresh_remote_address acl_refresh_remote_address;
struct acl_set_peer_le_features_from_handle
    acl_set_peer_le_features_from_handle;
struct sco_peer_supports_esco_2m_phy sco_peer_supports_esco_2m_phy;
struct sco_peer_supports_esco_3m_phy sco_peer_supports_esco_3m_phy;
struct acl_create_classic_connection acl_create_classic_connection;
struct IsEprAvailable IsEprAvailable;
struct acl_get_connection_from_address acl_get_connection_from_address;
struct btm_acl_for_bda btm_acl_for_bda;
struct acl_get_connection_from_handle acl_get_connection_from_handle;
struct BTM_GetLinkSuperTout BTM_GetLinkSuperTout;
struct BTM_GetRole BTM_GetRole;
struct BTM_ReadFailedContactCounter BTM_ReadFailedContactCounter;
struct BTM_ReadRSSI BTM_ReadRSSI;
struct BTM_ReadTxPower BTM_ReadTxPower;
struct BTM_SetLinkSuperTout BTM_SetLinkSuperTout;
struct BTM_SwitchRoleToCentral BTM_SwitchRoleToCentral;
struct btm_remove_acl btm_remove_acl;
struct btm_get_acl_disc_reason_code btm_get_acl_disc_reason_code;
struct BTM_GetHCIConnHandle BTM_GetHCIConnHandle;
struct BTM_GetMaxPacketSize BTM_GetMaxPacketSize;
struct BTM_GetNumAclLinks BTM_GetNumAclLinks;
struct acl_get_supported_packet_types acl_get_supported_packet_types;
struct BTM_GetPeerSCA BTM_GetPeerSCA;
struct BTM_SetTraceLevel BTM_SetTraceLevel;
struct acl_link_role_from_handle acl_link_role_from_handle;
struct btm_handle_to_acl_index btm_handle_to_acl_index;
struct BTM_ReadRemoteFeatures BTM_ReadRemoteFeatures;
struct ACL_RegisterClient ACL_RegisterClient;
struct ACL_UnregisterClient ACL_UnregisterClient;
struct BTM_ReadConnectionAddr BTM_ReadConnectionAddr;
struct BTM_RequestPeerSCA BTM_RequestPeerSCA;
struct BTM_acl_after_controller_started BTM_acl_after_controller_started;
struct BTM_block_role_switch_for BTM_block_role_switch_for;
struct BTM_block_sniff_mode_for BTM_block_sniff_mode_for;
struct BTM_default_block_role_switch BTM_default_block_role_switch;
struct BTM_default_unblock_role_switch BTM_default_unblock_role_switch;
struct BTM_unblock_role_switch_for BTM_unblock_role_switch_for;
struct BTM_unblock_sniff_mode_for BTM_unblock_sniff_mode_for;
struct HACK_acl_check_sm4 HACK_acl_check_sm4;
struct acl_accept_connection_request acl_accept_connection_request;
struct acl_disconnect_after_role_switch acl_disconnect_after_role_switch;
struct acl_disconnect_from_handle acl_disconnect_from_handle;
struct acl_link_segments_xmitted acl_link_segments_xmitted;
struct acl_packets_completed acl_packets_completed;
struct acl_process_extended_features acl_process_extended_features;
struct acl_process_supported_features acl_process_supported_features;
struct acl_rcv_acl_data acl_rcv_acl_data;
struct acl_reject_connection_request acl_reject_connection_request;
struct acl_send_data_packet_ble acl_send_data_packet_ble;
struct acl_set_disconnect_reason acl_set_disconnect_reason;
struct acl_write_automatic_flush_timeout acl_write_automatic_flush_timeout;
struct btm_acl_connected btm_acl_connected;
struct btm_acl_connection_request btm_acl_connection_request;
struct btm_acl_created btm_acl_created;
struct btm_acl_device_down btm_acl_device_down;
struct btm_acl_disconnected btm_acl_disconnected;
struct btm_acl_iso_disconnected btm_acl_iso_disconnected;
struct btm_acl_encrypt_change btm_acl_encrypt_change;
struct btm_acl_notif_conn_collision btm_acl_notif_conn_collision;
struct btm_acl_paging btm_acl_paging;
struct btm_acl_process_sca_cmpl_pkt btm_acl_process_sca_cmpl_pkt;
struct btm_acl_removed btm_acl_removed;
struct btm_acl_reset_paging btm_acl_reset_paging;
struct btm_acl_resubmit_page btm_acl_resubmit_page;
struct btm_acl_role_changed btm_acl_role_changed;
struct btm_acl_set_paging btm_acl_set_paging;
struct btm_acl_update_conn_addr btm_acl_update_conn_addr;
struct btm_configure_data_path btm_configure_data_path;
struct btm_acl_update_inquiry_status btm_acl_update_inquiry_status;
struct btm_ble_refresh_local_resolvable_private_addr
    btm_ble_refresh_local_resolvable_private_addr;
struct btm_cont_rswitch_from_handle btm_cont_rswitch_from_handle;
struct btm_establish_continue_from_address btm_establish_continue_from_address;
struct btm_process_remote_ext_features btm_process_remote_ext_features;
struct btm_process_remote_version_complete btm_process_remote_version_complete;
struct btm_read_automatic_flush_timeout_complete
    btm_read_automatic_flush_timeout_complete;
struct btm_read_failed_contact_counter_complete
    btm_read_failed_contact_counter_complete;
struct btm_read_failed_contact_counter_timeout
    btm_read_failed_contact_counter_timeout;
struct btm_read_link_quality_complete btm_read_link_quality_complete;
struct btm_read_link_quality_timeout btm_read_link_quality_timeout;
struct btm_read_remote_ext_features btm_read_remote_ext_features;
struct btm_read_remote_ext_features_complete
    btm_read_remote_ext_features_complete;
struct btm_read_remote_ext_features_complete_raw
    btm_read_remote_ext_features_complete_raw;
struct btm_read_remote_ext_features_failed btm_read_remote_ext_features_failed;
struct btm_read_remote_features_complete btm_read_remote_features_complete;
struct btm_read_remote_version_complete btm_read_remote_version_complete;
struct btm_read_rssi_complete btm_read_rssi_complete;
struct btm_read_rssi_timeout btm_read_rssi_timeout;
struct btm_read_tx_power_complete btm_read_tx_power_complete;
struct btm_read_tx_power_timeout btm_read_tx_power_timeout;
struct btm_rejectlist_role_change_device btm_rejectlist_role_change_device;
struct btm_set_link_policy btm_set_link_policy;
struct btm_set_packet_types_from_address btm_set_packet_types_from_address;
struct hci_btm_set_link_supervision_timeout
    hci_btm_set_link_supervision_timeout;
struct on_acl_br_edr_connected on_acl_br_edr_connected;
struct on_acl_br_edr_failed on_acl_br_edr_failed;

}  // namespace stack_acl
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool ACL_SupportTransparentSynchronousData(const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::ACL_SupportTransparentSynchronousData(bd_addr);
}
bool BTM_BLE_IS_RESOLVE_BDA(const RawAddress& x) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_BLE_IS_RESOLVE_BDA(x);
}
bool BTM_IsAclConnectionUp(const RawAddress& remote_bda,
                           tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_IsAclConnectionUp(remote_bda, transport);
}
bool BTM_IsAclConnectionUpAndHandleValid(const RawAddress& remote_bda,
                                         tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_IsAclConnectionUpAndHandleValid(remote_bda,
                                                                    transport);
}
bool BTM_IsAclConnectionUpFromHandle(uint16_t hci_handle) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_IsAclConnectionUpFromHandle(hci_handle);
}
bool BTM_IsBleConnection(uint16_t hci_handle) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_IsBleConnection(hci_handle);
}
bool BTM_IsPhy2mSupported(const RawAddress& remote_bda,
                          tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_IsPhy2mSupported(remote_bda, transport);
}
bool BTM_ReadRemoteConnectionAddr(const RawAddress& pseudo_addr,
                                  RawAddress& conn_addr,
                                  tBLE_ADDR_TYPE* p_addr_type) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_ReadRemoteConnectionAddr(
      pseudo_addr, conn_addr, p_addr_type);
}
bool BTM_ReadRemoteVersion(const RawAddress& addr, uint8_t* lmp_version,
                           uint16_t* manufacturer, uint16_t* lmp_sub_version) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_ReadRemoteVersion(
      addr, lmp_version, manufacturer, lmp_sub_version);
}
bool BTM_is_sniff_allowed_for(const RawAddress& peer_addr) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_is_sniff_allowed_for(peer_addr);
}
bool acl_create_le_connection(const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_create_le_connection(bd_addr);
}
bool acl_create_le_connection_with_id(uint8_t id, const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_create_le_connection_with_id(id, bd_addr);
}
bool acl_is_role_switch_allowed() {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_is_role_switch_allowed();
}
bool acl_is_switch_role_idle(const RawAddress& bd_addr,
                             tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_is_switch_role_idle(bd_addr, transport);
}
bool acl_peer_supports_ble_2m_phy(uint16_t hci_handle) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_peer_supports_ble_2m_phy(hci_handle);
}
bool acl_peer_supports_ble_coded_phy(uint16_t hci_handle) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_peer_supports_ble_coded_phy(hci_handle);
}
bool acl_peer_supports_ble_connection_parameters_request(
    const RawAddress& remote_bda) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::
      acl_peer_supports_ble_connection_parameters_request(remote_bda);
}
bool acl_peer_supports_ble_packet_extension(uint16_t hci_handle) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_peer_supports_ble_packet_extension(
      hci_handle);
}
bool acl_peer_supports_sniff_subrating(const RawAddress& remote_bda) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_peer_supports_sniff_subrating(remote_bda);
}
bool acl_refresh_remote_address(const RawAddress& identity_address,
                                tBLE_ADDR_TYPE identity_address_type,
                                const RawAddress& bda,
                                tBTM_SEC_BLE::tADDRESS_TYPE rra_type,
                                const RawAddress& rpa) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_refresh_remote_address(
      identity_address, identity_address_type, bda, rra_type, rpa);
}
bool acl_set_peer_le_features_from_handle(uint16_t hci_handle,
                                          const uint8_t* p) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_set_peer_le_features_from_handle(hci_handle,
                                                                     p);
}
bool sco_peer_supports_esco_2m_phy(const RawAddress& remote_bda) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::sco_peer_supports_esco_2m_phy(remote_bda);
}
bool sco_peer_supports_esco_3m_phy(const RawAddress& remote_bda) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::sco_peer_supports_esco_3m_phy(remote_bda);
}
void acl_send_data_packet_br_edr(const RawAddress& bd_addr, BT_HDR* p_buf) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_send_data_packet_br_edr(bd_addr, p_buf);
}
void acl_create_classic_connection(const RawAddress& bd_addr,
                                   bool there_are_high_priority_channels,
                                   bool is_bonding) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_create_classic_connection(
      bd_addr, there_are_high_priority_channels, is_bonding);
}
bool IsEprAvailable(const tACL_CONN& p_acl) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::IsEprAvailable(p_acl);
}
tACL_CONN* acl_get_connection_from_address(const RawAddress& bd_addr,
                                           tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_get_connection_from_address(bd_addr,
                                                                transport);
}
tACL_CONN* btm_acl_for_bda(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::btm_acl_for_bda(bd_addr, transport);
}
tACL_CONN* acl_get_connection_from_handle(uint16_t handle) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_get_connection_from_handle(handle);
}
tBTM_STATUS BTM_GetLinkSuperTout(const RawAddress& remote_bda,
                                 uint16_t* p_timeout) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_GetLinkSuperTout(remote_bda, p_timeout);
}
tBTM_STATUS BTM_GetRole(const RawAddress& remote_bd_addr, tHCI_ROLE* p_role) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_GetRole(remote_bd_addr, p_role);
}
tBTM_STATUS BTM_ReadFailedContactCounter(const RawAddress& remote_bda,
                                         tBTM_CMPL_CB* p_cb) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_ReadFailedContactCounter(remote_bda, p_cb);
}
tBTM_STATUS BTM_ReadRSSI(const RawAddress& remote_bda, tBTM_CMPL_CB* p_cb) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_ReadRSSI(remote_bda, p_cb);
}
tBTM_STATUS BTM_ReadTxPower(const RawAddress& remote_bda,
                            tBT_TRANSPORT transport, tBTM_CMPL_CB* p_cb) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_ReadTxPower(remote_bda, transport, p_cb);
}
tBTM_STATUS BTM_SetLinkSuperTout(const RawAddress& remote_bda,
                                 uint16_t timeout) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_SetLinkSuperTout(remote_bda, timeout);
}
tBTM_STATUS BTM_SwitchRoleToCentral(const RawAddress& remote_bd_addr) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_SwitchRoleToCentral(remote_bd_addr);
}
tBTM_STATUS btm_remove_acl(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::btm_remove_acl(bd_addr, transport);
}
tHCI_REASON btm_get_acl_disc_reason_code(void) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::btm_get_acl_disc_reason_code();
}
uint16_t BTM_GetHCIConnHandle(const RawAddress& remote_bda,
                              tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_GetHCIConnHandle(remote_bda, transport);
}
uint16_t BTM_GetMaxPacketSize(const RawAddress& addr) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_GetMaxPacketSize(addr);
}
uint16_t BTM_GetNumAclLinks(void) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_GetNumAclLinks();
}
uint16_t acl_get_supported_packet_types() {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_get_supported_packet_types();
}
uint8_t BTM_GetPeerSCA(const RawAddress& remote_bda, tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_GetPeerSCA(remote_bda, transport);
}
uint8_t BTM_SetTraceLevel(uint8_t new_level) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_SetTraceLevel(new_level);
}
uint8_t acl_link_role_from_handle(uint16_t handle) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::acl_link_role_from_handle(handle);
}
uint8_t btm_handle_to_acl_index(uint16_t hci_handle) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::btm_handle_to_acl_index(hci_handle);
}
uint8_t* BTM_ReadRemoteFeatures(const RawAddress& addr) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl::BTM_ReadRemoteFeatures(addr);
}
void ACL_RegisterClient(struct acl_client_callback_s* callbacks) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::ACL_RegisterClient(callbacks);
}
void ACL_UnregisterClient(struct acl_client_callback_s* callbacks) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::ACL_UnregisterClient(callbacks);
}
void BTM_ReadConnectionAddr(const RawAddress& remote_bda,
                            RawAddress& local_conn_addr,
                            tBLE_ADDR_TYPE* p_addr_type) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::BTM_ReadConnectionAddr(remote_bda, local_conn_addr,
                                                p_addr_type);
}
void BTM_RequestPeerSCA(const RawAddress& remote_bda, tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::BTM_RequestPeerSCA(remote_bda, transport);
}
void BTM_acl_after_controller_started(const controller_t* controller) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::BTM_acl_after_controller_started(controller);
}
void BTM_block_role_switch_for(const RawAddress& peer_addr) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::BTM_block_role_switch_for(peer_addr);
}
void BTM_block_sniff_mode_for(const RawAddress& peer_addr) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::BTM_block_sniff_mode_for(peer_addr);
}
void BTM_default_block_role_switch() {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::BTM_default_block_role_switch();
}
void BTM_default_unblock_role_switch() {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::BTM_default_unblock_role_switch();
}
void BTM_unblock_role_switch_for(const RawAddress& peer_addr) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::BTM_unblock_role_switch_for(peer_addr);
}
void BTM_unblock_sniff_mode_for(const RawAddress& peer_addr) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::BTM_unblock_sniff_mode_for(peer_addr);
}
void HACK_acl_check_sm4(tBTM_SEC_DEV_REC& record) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::HACK_acl_check_sm4(record);
}
void acl_accept_connection_request(const RawAddress& bd_addr, uint8_t role) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_accept_connection_request(bd_addr, role);
}
void acl_disconnect_after_role_switch(uint16_t conn_handle, tHCI_STATUS reason,
                                      std::string comment) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_disconnect_after_role_switch(conn_handle, reason,
                                                          comment);
}
void acl_disconnect_from_handle(uint16_t handle, tHCI_STATUS reason,
                                std::string comment) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_disconnect_from_handle(handle, reason, comment);
}
void acl_link_segments_xmitted(BT_HDR* p_msg) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_link_segments_xmitted(p_msg);
}
void acl_packets_completed(uint16_t handle, uint16_t credits) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_packets_completed(handle, credits);
}
void acl_process_extended_features(uint16_t handle, uint8_t current_page_number,
                                   uint8_t max_page_number, uint64_t features) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_process_extended_features(
      handle, current_page_number, max_page_number, features);
}
void acl_process_supported_features(uint16_t handle, uint64_t features) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_process_supported_features(handle, features);
}
void acl_rcv_acl_data(BT_HDR* p_msg) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_rcv_acl_data(p_msg);
}
void acl_reject_connection_request(const RawAddress& bd_addr, uint8_t reason) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_reject_connection_request(bd_addr, reason);
}
void acl_send_data_packet_ble(const RawAddress& bd_addr, BT_HDR* p_buf) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_send_data_packet_ble(bd_addr, p_buf);
}
void acl_set_disconnect_reason(tHCI_STATUS acl_disc_reason) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_set_disconnect_reason(acl_disc_reason);
}
void acl_write_automatic_flush_timeout(const RawAddress& bd_addr,
                                       uint16_t flush_timeout_in_ticks) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::acl_write_automatic_flush_timeout(
      bd_addr, flush_timeout_in_ticks);
}
void btm_acl_connected(const RawAddress& bda, uint16_t handle,
                       tHCI_STATUS status, uint8_t enc_mode) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_connected(bda, handle, status, enc_mode);
}
void btm_acl_connection_request(const RawAddress& bda, uint8_t* dc) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_connection_request(bda, dc);
}
void btm_acl_created(const RawAddress& bda, uint16_t hci_handle,
                     tHCI_ROLE link_role, tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_created(bda, hci_handle, link_role, transport);
}
void btm_acl_device_down(void) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_device_down();
}
void btm_acl_disconnected(tHCI_STATUS status, uint16_t handle,
                          tHCI_REASON reason) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_disconnected(status, handle, reason);
}
void btm_acl_iso_disconnected(uint16_t handle, tHCI_REASON reason) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_iso_disconnected(handle, reason);
}
void btm_acl_encrypt_change(uint16_t handle, uint8_t status,
                            uint8_t encr_enable) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_encrypt_change(handle, status, encr_enable);
}
void btm_acl_notif_conn_collision(const RawAddress& bda) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_notif_conn_collision(bda);
}
void btm_acl_paging(BT_HDR* p, const RawAddress& bda) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_paging(p, bda);
}
void btm_acl_process_sca_cmpl_pkt(uint8_t len, uint8_t* data) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_process_sca_cmpl_pkt(len, data);
}
void btm_acl_removed(uint16_t handle) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_removed(handle);
}
void btm_acl_reset_paging(void) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_reset_paging();
}
void btm_acl_resubmit_page(void) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_resubmit_page();
}
void btm_acl_role_changed(tHCI_STATUS hci_status, const RawAddress& bd_addr,
                          tHCI_ROLE new_role) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_role_changed(hci_status, bd_addr, new_role);
}
void btm_acl_set_paging(bool value) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_set_paging(value);
}
void btm_acl_update_conn_addr(uint16_t handle, const RawAddress& address) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_update_conn_addr(handle, address);
}
void btm_configure_data_path(uint8_t direction, uint8_t path_id,
                             std::vector<uint8_t> vendor_config) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_configure_data_path(direction, path_id,
                                                 vendor_config);
}
void btm_acl_update_inquiry_status(uint8_t status) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_acl_update_inquiry_status(status);
}
void btm_ble_refresh_local_resolvable_private_addr(
    const RawAddress& pseudo_addr, const RawAddress& local_rpa) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_ble_refresh_local_resolvable_private_addr(
      pseudo_addr, local_rpa);
}
void btm_cont_rswitch_from_handle(uint16_t hci_handle) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_cont_rswitch_from_handle(hci_handle);
}
void btm_establish_continue_from_address(const RawAddress& bda,
                                         tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_establish_continue_from_address(bda, transport);
}
void btm_process_remote_ext_features(tACL_CONN* p_acl_cb,
                                     uint8_t max_page_number) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_process_remote_ext_features(p_acl_cb,
                                                         max_page_number);
}
void btm_process_remote_version_complete(uint8_t status, uint16_t handle,
                                         uint8_t lmp_version,
                                         uint16_t manufacturer,
                                         uint16_t lmp_subversion) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_process_remote_version_complete(
      status, handle, lmp_version, manufacturer, lmp_subversion);
}
void btm_read_automatic_flush_timeout_complete(uint8_t* p) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_automatic_flush_timeout_complete(p);
}
void btm_read_failed_contact_counter_complete(uint8_t* p) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_failed_contact_counter_complete(p);
}
void btm_read_failed_contact_counter_timeout(UNUSED_ATTR void* data) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_failed_contact_counter_timeout(data);
}
void btm_read_link_quality_complete(uint8_t* p, uint16_t evt_len) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_link_quality_complete(p, evt_len);
}
void btm_read_link_quality_timeout(UNUSED_ATTR void* data) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_link_quality_timeout(data);
}
void btm_read_remote_ext_features(uint16_t handle, uint8_t page_number) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_remote_ext_features(handle, page_number);
}
void btm_read_remote_ext_features_complete(uint16_t handle, uint8_t page_num,
                                           uint8_t max_page,
                                           uint8_t* features) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_remote_ext_features_complete(
      handle, page_num, max_page, features);
}
void btm_read_remote_ext_features_complete_raw(uint8_t* p, uint8_t evt_len) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_remote_ext_features_complete_raw(p, evt_len);
}
void btm_read_remote_ext_features_failed(uint8_t status, uint16_t handle) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_remote_ext_features_failed(status, handle);
}
void btm_read_remote_features_complete(uint16_t handle, uint8_t* features) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_remote_features_complete(handle, features);
}
void btm_read_remote_version_complete(tHCI_STATUS status, uint16_t handle,
                                      uint8_t lmp_version,
                                      uint16_t manufacturer,
                                      uint16_t lmp_subversion) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_remote_version_complete(
      status, handle, lmp_version, manufacturer, lmp_subversion);
}
void btm_read_rssi_complete(uint8_t* p, uint16_t evt_len) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_rssi_complete(p, evt_len);
}
void btm_read_rssi_timeout(UNUSED_ATTR void* data) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_rssi_timeout(data);
}
void btm_read_tx_power_complete(uint8_t* p, uint16_t evt_len, bool is_ble) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_tx_power_complete(p, evt_len, is_ble);
}
void btm_read_tx_power_timeout(UNUSED_ATTR void* data) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_read_tx_power_timeout(data);
}
void btm_rejectlist_role_change_device(const RawAddress& bd_addr,
                                       uint8_t hci_status) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_rejectlist_role_change_device(bd_addr, hci_status);
}
void btm_set_link_policy(tACL_CONN* conn, tLINK_POLICY policy) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_set_link_policy(conn, policy);
}
void btm_set_packet_types_from_address(const RawAddress& bd_addr,
                                       uint16_t pkt_types) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::btm_set_packet_types_from_address(bd_addr, pkt_types);
}
void hci_btm_set_link_supervision_timeout(tACL_CONN& link, uint16_t timeout) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::hci_btm_set_link_supervision_timeout(link, timeout);
}
void on_acl_br_edr_connected(const RawAddress& bda, uint16_t handle,
                             uint8_t enc_mode) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::on_acl_br_edr_connected(bda, handle, enc_mode);
}
void on_acl_br_edr_failed(const RawAddress& bda, tHCI_STATUS status) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl::on_acl_br_edr_failed(bda, status);
}

// END mockcify generation

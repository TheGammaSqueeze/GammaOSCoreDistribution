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
 *   Functions generated:34
 *
 *  mockcify.pl ver 0.3.0
 */

#include <cstdint>
#include <functional>
#include <map>
#include <string>

#include "stack/include/bt_octets.h"

extern std::map<std::string, int> mock_function_count_map;

// Mock include file to share data between tests and mock
#include "test/mock/mock_bta_dm_api.h"
#include "types/raw_address.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace bta_dm_api {

// Function state capture and return values, if needed
struct BTA_DmAddBleDevice BTA_DmAddBleDevice;
struct BTA_DmAddBleKey BTA_DmAddBleKey;
struct BTA_DmAddDevice BTA_DmAddDevice;
struct BTA_DmBleConfigLocalPrivacy BTA_DmBleConfigLocalPrivacy;
struct BTA_DmBleConfirmReply BTA_DmBleConfirmReply;
struct BTA_DmBleCsisObserve BTA_DmBleCsisObserve;
struct BTA_DmBleGetEnergyInfo BTA_DmBleGetEnergyInfo;
struct BTA_DmBleObserve BTA_DmBleObserve;
struct BTA_DmBlePasskeyReply BTA_DmBlePasskeyReply;
struct BTA_DmBleRequestMaxTxDataLength BTA_DmBleRequestMaxTxDataLength;
struct BTA_DmBleScan BTA_DmBleScan;
struct BTA_DmBleSecurityGrant BTA_DmBleSecurityGrant;
struct BTA_DmBleUpdateConnectionParams BTA_DmBleUpdateConnectionParams;
struct BTA_DmBond BTA_DmBond;
struct BTA_DmBondCancel BTA_DmBondCancel;
struct BTA_DmCloseACL BTA_DmCloseACL;
struct BTA_DmConfirm BTA_DmConfirm;
struct BTA_DmDiscover BTA_DmDiscover;
struct BTA_DmGetConnectionState BTA_DmGetConnectionState;
struct BTA_DmLocalOob BTA_DmLocalOob;
struct BTA_DmPinReply BTA_DmPinReply;
struct BTA_DmRemoveDevice BTA_DmRemoveDevice;
struct BTA_DmSearch BTA_DmSearch;
struct BTA_DmSearchCancel BTA_DmSearchCancel;
struct BTA_DmSetBlePrefConnParams BTA_DmSetBlePrefConnParams;
struct BTA_DmSetDeviceName BTA_DmSetDeviceName;
struct BTA_DmSetEncryption BTA_DmSetEncryption;
struct BTA_DmSetLocalDiRecord BTA_DmSetLocalDiRecord;
struct BTA_EnableTestMode BTA_EnableTestMode;
struct BTA_GetEirService BTA_GetEirService;
struct BTA_VendorInit BTA_VendorInit;
struct BTA_dm_init BTA_dm_init;

}  // namespace bta_dm_api
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void BTA_DmAddBleDevice(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                        tBT_DEVICE_TYPE dev_type) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmAddBleDevice(bd_addr, addr_type, dev_type);
}
void BTA_DmAddBleKey(const RawAddress& bd_addr, tBTA_LE_KEY_VALUE* p_le_key,
                     tBTM_LE_KEY_TYPE key_type) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmAddBleKey(bd_addr, p_le_key, key_type);
}
void BTA_DmAddDevice(const RawAddress& bd_addr, DEV_CLASS dev_class,
                     const LinkKey& link_key, uint8_t key_type,
                     uint8_t pin_length) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmAddDevice(bd_addr, dev_class, link_key,
                                          key_type, pin_length);
}
void BTA_DmBleConfigLocalPrivacy(bool privacy_enable) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBleConfigLocalPrivacy(privacy_enable);
}
void BTA_DmBleConfirmReply(const RawAddress& bd_addr, bool accept) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBleConfirmReply(bd_addr, accept);
}
void BTA_DmBleCsisObserve(bool observe, tBTA_DM_SEARCH_CBACK* p_results_cb) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBleCsisObserve(observe, p_results_cb);
}
void BTA_DmBleGetEnergyInfo(tBTA_BLE_ENERGY_INFO_CBACK* p_cmpl_cback) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBleGetEnergyInfo(p_cmpl_cback);
}
void BTA_DmBleObserve(bool start, uint8_t duration,
                      tBTA_DM_SEARCH_CBACK* p_results_cb) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBleObserve(start, duration, p_results_cb);
}
void BTA_DmBlePasskeyReply(const RawAddress& bd_addr, bool accept,
                           uint32_t passkey) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBlePasskeyReply(bd_addr, accept, passkey);
}
void BTA_DmBleRequestMaxTxDataLength(const RawAddress& remote_device) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBleRequestMaxTxDataLength(remote_device);
}
void BTA_DmBleScan(bool start, uint8_t duration) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBleScan(start, duration);
}
void BTA_DmBleSecurityGrant(const RawAddress& bd_addr,
                            tBTA_DM_BLE_SEC_GRANT res) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBleSecurityGrant(bd_addr, res);
}
void BTA_DmBleUpdateConnectionParams(const RawAddress& bd_addr,
                                     uint16_t min_int, uint16_t max_int,
                                     uint16_t latency, uint16_t timeout,
                                     uint16_t min_ce_len, uint16_t max_ce_len) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBleUpdateConnectionParams(
      bd_addr, min_int, max_int, latency, timeout, min_ce_len, max_ce_len);
}
void BTA_DmBond(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBond(bd_addr, addr_type, transport,
                                     device_type);
}
void BTA_DmBondCancel(const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmBondCancel(bd_addr);
}
void BTA_DmCloseACL(const RawAddress& bd_addr, bool remove_dev,
                    tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmCloseACL(bd_addr, remove_dev, transport);
}
void BTA_DmConfirm(const RawAddress& bd_addr, bool accept) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmConfirm(bd_addr, accept);
}
void BTA_DmDiscover(const RawAddress& bd_addr, tBTA_DM_SEARCH_CBACK* p_cback,
                    tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmDiscover(bd_addr, p_cback, transport);
}
bool BTA_DmGetConnectionState(const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  return test::mock::bta_dm_api::BTA_DmGetConnectionState(bd_addr);
}
void BTA_DmLocalOob(void) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmLocalOob();
}
void BTA_DmPinReply(const RawAddress& bd_addr, bool accept, uint8_t pin_len,
                    uint8_t* p_pin) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmPinReply(bd_addr, accept, pin_len, p_pin);
}
tBTA_STATUS BTA_DmRemoveDevice(const RawAddress& bd_addr) {
  mock_function_count_map[__func__]++;
  return test::mock::bta_dm_api::BTA_DmRemoveDevice(bd_addr);
}
void BTA_DmSearch(tBTA_DM_SEARCH_CBACK* p_cback) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmSearch(p_cback);
}
void BTA_DmSearchCancel(void) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmSearchCancel();
}
void BTA_DmSetBlePrefConnParams(const RawAddress& bd_addr,
                                uint16_t min_conn_int, uint16_t max_conn_int,
                                uint16_t peripheral_latency,
                                uint16_t supervision_tout) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmSetBlePrefConnParams(
      bd_addr, min_conn_int, max_conn_int, peripheral_latency,
      supervision_tout);
}
void BTA_DmSetDeviceName(const char* p_name) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmSetDeviceName(p_name);
}
void BTA_DmSetEncryption(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                         tBTA_DM_ENCRYPT_CBACK* p_callback,
                         tBTM_BLE_SEC_ACT sec_act) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_DmSetEncryption(bd_addr, transport, p_callback,
                                              sec_act);
}
tBTA_STATUS BTA_DmSetLocalDiRecord(tSDP_DI_RECORD* p_device_info,
                                   uint32_t* p_handle) {
  mock_function_count_map[__func__]++;
  return test::mock::bta_dm_api::BTA_DmSetLocalDiRecord(p_device_info,
                                                        p_handle);
}
void BTA_EnableTestMode(void) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_EnableTestMode();
}
void BTA_GetEirService(const uint8_t* p_eir, size_t eir_len,
                       tBTA_SERVICE_MASK* p_services) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_GetEirService(p_eir, eir_len, p_services);
}
void BTA_VendorInit(void) {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_VendorInit();
}
void BTA_dm_init() {
  mock_function_count_map[__func__]++;
  test::mock::bta_dm_api::BTA_dm_init();
}

// END mockcify generation

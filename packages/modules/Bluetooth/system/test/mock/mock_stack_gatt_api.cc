/*
 * Copyright 2022 The Android Open Source Project
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
 *   Functions generated:26
 *
 *  mockcify.pl ver 0.5.0
 */

#include <cstdint>
#include <functional>
#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_gatt_api.h"

// Original usings
using bluetooth::Uuid;

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_gatt_api {

// Function state capture and return values, if needed
struct GATTC_ConfigureMTU GATTC_ConfigureMTU;
struct GATTC_Discover GATTC_Discover;
struct GATTC_ExecuteWrite GATTC_ExecuteWrite;
struct GATTC_Read GATTC_Read;
struct GATTC_SendHandleValueConfirm GATTC_SendHandleValueConfirm;
struct GATTC_Write GATTC_Write;
struct GATTS_AddService GATTS_AddService;
struct GATTS_DeleteService GATTS_DeleteService;
struct GATTS_HandleValueIndication GATTS_HandleValueIndication;
struct GATTS_HandleValueNotification GATTS_HandleValueNotification;
struct GATTS_NVRegister GATTS_NVRegister;
struct GATTS_SendRsp GATTS_SendRsp;
struct GATTS_StopService GATTS_StopService;
struct GATT_CancelConnect GATT_CancelConnect;
struct GATT_Connect GATT_Connect;
struct GATT_Deregister GATT_Deregister;
struct GATT_Disconnect GATT_Disconnect;
struct GATT_GetConnIdIfConnected GATT_GetConnIdIfConnected;
struct GATT_GetConnectionInfor GATT_GetConnectionInfor;
struct GATT_Register GATT_Register;
struct GATT_SetIdleTimeout GATT_SetIdleTimeout;
struct GATT_StartIf GATT_StartIf;
// struct gatt_add_an_item_to_list gatt_add_an_item_to_list;
struct is_active_service is_active_service;

}  // namespace stack_gatt_api
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace stack_gatt_api {

tGATT_STATUS GATTC_ConfigureMTU::return_value = GATT_SUCCESS;
tGATT_STATUS GATTC_Discover::return_value = GATT_SUCCESS;
tGATT_STATUS GATTC_ExecuteWrite::return_value = GATT_SUCCESS;
tGATT_STATUS GATTC_Read::return_value = GATT_SUCCESS;
tGATT_STATUS GATTC_SendHandleValueConfirm::return_value = GATT_SUCCESS;
tGATT_STATUS GATTC_Write::return_value = GATT_SUCCESS;
tGATT_STATUS GATTS_AddService::return_value = GATT_SUCCESS;
bool GATTS_DeleteService::return_value = false;
tGATT_STATUS GATTS_HandleValueIndication::return_value = GATT_SUCCESS;
tGATT_STATUS GATTS_HandleValueNotification::return_value = GATT_SUCCESS;
bool GATTS_NVRegister::return_value = false;
tGATT_STATUS GATTS_SendRsp::return_value = GATT_SUCCESS;
bool GATT_CancelConnect::return_value = false;
bool GATT_Connect::return_value = false;
tGATT_STATUS GATT_Disconnect::return_value = GATT_SUCCESS;
bool GATT_GetConnIdIfConnected::return_value = false;
bool GATT_GetConnectionInfor::return_value = false;
tGATT_IF GATT_Register::return_value = 0;
// tGATT_HDL_LIST_ELEM gatt_add_an_item_to_list::return_value = { .svc_db = {},
// .asgn_range = {}};
bool is_active_service::return_value = false;

}  // namespace stack_gatt_api
}  // namespace mock
}  // namespace test

// Mocked functions, if any
tGATT_STATUS GATTC_ConfigureMTU(uint16_t conn_id, uint16_t mtu) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTC_ConfigureMTU(conn_id, mtu);
}
tGATT_STATUS GATTC_Discover(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                            uint16_t start_handle, uint16_t end_handle) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTC_Discover(conn_id, disc_type,
                                                    start_handle, end_handle);
}
tGATT_STATUS GATTC_ExecuteWrite(uint16_t conn_id, bool is_execute) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTC_ExecuteWrite(conn_id, is_execute);
}
tGATT_STATUS GATTC_Read(uint16_t conn_id, tGATT_READ_TYPE type,
                        tGATT_READ_PARAM* p_read) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTC_Read(conn_id, type, p_read);
}
tGATT_STATUS GATTC_SendHandleValueConfirm(uint16_t conn_id, uint16_t cid) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTC_SendHandleValueConfirm(conn_id, cid);
}
tGATT_STATUS GATTC_Write(uint16_t conn_id, tGATT_WRITE_TYPE type,
                         tGATT_VALUE* p_write) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTC_Write(conn_id, type, p_write);
}
tGATT_STATUS GATTS_AddService(tGATT_IF gatt_if, btgatt_db_element_t* service,
                              int count) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTS_AddService(gatt_if, service, count);
}
bool GATTS_DeleteService(tGATT_IF gatt_if, Uuid* p_svc_uuid,
                         uint16_t svc_inst) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTS_DeleteService(gatt_if, p_svc_uuid,
                                                         svc_inst);
}
tGATT_STATUS GATTS_HandleValueIndication(uint16_t conn_id, uint16_t attr_handle,
                                         uint16_t val_len, uint8_t* p_val) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTS_HandleValueIndication(
      conn_id, attr_handle, val_len, p_val);
}
tGATT_STATUS GATTS_HandleValueNotification(uint16_t conn_id,
                                           uint16_t attr_handle,
                                           uint16_t val_len, uint8_t* p_val) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTS_HandleValueNotification(
      conn_id, attr_handle, val_len, p_val);
}
bool GATTS_NVRegister(tGATT_APPL_INFO* p_cb_info) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTS_NVRegister(p_cb_info);
}
tGATT_STATUS GATTS_SendRsp(uint16_t conn_id, uint32_t trans_id,
                           tGATT_STATUS status, tGATTS_RSP* p_msg) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATTS_SendRsp(conn_id, trans_id, status,
                                                   p_msg);
}
void GATTS_StopService(uint16_t service_handle) {
  mock_function_count_map[__func__]++;
  test::mock::stack_gatt_api::GATTS_StopService(service_handle);
}
bool GATT_CancelConnect(tGATT_IF gatt_if, const RawAddress& bd_addr,
                        bool is_direct) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATT_CancelConnect(gatt_if, bd_addr,
                                                        is_direct);
}
bool GATT_Connect(tGATT_IF gatt_if, const RawAddress& bd_addr,
                  tBTM_BLE_CONN_TYPE connection_type, tBT_TRANSPORT transport,
                  bool opportunistic, uint8_t initiating_phys) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATT_Connect(
      gatt_if, bd_addr, connection_type, transport, opportunistic,
      initiating_phys);
}
void GATT_Deregister(tGATT_IF gatt_if) {
  mock_function_count_map[__func__]++;
  test::mock::stack_gatt_api::GATT_Deregister(gatt_if);
}
tGATT_STATUS GATT_Disconnect(uint16_t conn_id) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATT_Disconnect(conn_id);
}
bool GATT_GetConnIdIfConnected(tGATT_IF gatt_if, const RawAddress& bd_addr,
                               uint16_t* p_conn_id, tBT_TRANSPORT transport) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATT_GetConnIdIfConnected(
      gatt_if, bd_addr, p_conn_id, transport);
}
bool GATT_GetConnectionInfor(uint16_t conn_id, tGATT_IF* p_gatt_if,
                             RawAddress& bd_addr, tBT_TRANSPORT* p_transport) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATT_GetConnectionInfor(
      conn_id, p_gatt_if, bd_addr, p_transport);
}
tGATT_IF GATT_Register(const Uuid& app_uuid128, std::string name,
                       tGATT_CBACK* p_cb_info, bool eatt_support) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATT_Register(app_uuid128, name, p_cb_info,
                                                   eatt_support);
}
void GATT_SetIdleTimeout(const RawAddress& bd_addr, uint16_t idle_tout,
                         tBT_TRANSPORT transport, bool is_active) {
  mock_function_count_map[__func__]++;
  test::mock::stack_gatt_api::GATT_SetIdleTimeout(bd_addr, idle_tout, transport,
                                                  is_active);
}
void GATT_StartIf(tGATT_IF gatt_if) {
  mock_function_count_map[__func__]++;
  test::mock::stack_gatt_api::GATT_StartIf(gatt_if);
}
// tGATT_HDL_LIST_ELEM& gatt_add_an_item_to_list(uint16_t s_handle) {
//   mock_function_count_map[__func__]++;
//   return test::mock::stack_gatt_api::gatt_add_an_item_to_list(s_handle);
// }
bool is_active_service(const Uuid& app_uuid128, Uuid* p_svc_uuid,
                       uint16_t start_handle) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::is_active_service(app_uuid128, p_svc_uuid,
                                                       start_handle);
}
// Mocked functions complete
//
bool GATT_Connect(tGATT_IF gatt_if, const RawAddress& bd_addr,
                  tBTM_BLE_CONN_TYPE connection_type, tBT_TRANSPORT transport,
                  bool opportunistic) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_gatt_api::GATT_Connect(
      gatt_if, bd_addr, connection_type, transport, opportunistic, 0);
}

// END mockcify generation

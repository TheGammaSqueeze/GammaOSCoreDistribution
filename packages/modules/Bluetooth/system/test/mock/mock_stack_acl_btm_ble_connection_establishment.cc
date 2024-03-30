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
 *   Functions generated:6
 *
 *  mockcify.pl ver 0.2
 */

#include <cstdint>
#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

// Original included files, if any
// NOTE: Since this is a mock file with mock definitions some number of
//       include files may not be required.  The include-what-you-use
//       still applies, but crafting proper inclusion is out of scope
//       for this effort.  This compilation unit may compile as-is, or
//       may need attention to prune the inclusion set.

// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_acl_btm_ble_connection_establishment.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
#ifndef UNUSED_ATTR
#define UNUSED_ATTR
#endif

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_acl_btm_ble_connection_establishment {

// Function state capture and return values, if needed
struct btm_ble_create_ll_conn_complete btm_ble_create_ll_conn_complete;
struct maybe_resolve_address maybe_resolve_address;
struct btm_ble_conn_complete btm_ble_conn_complete;
struct btm_ble_create_conn_cancel btm_ble_create_conn_cancel;
struct btm_ble_create_conn_cancel_complete btm_ble_create_conn_cancel_complete;

}  // namespace stack_acl_btm_ble_connection_establishment
}  // namespace mock
}  // namespace test

void btm_ble_create_ll_conn_complete(tHCI_STATUS status) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl_btm_ble_connection_establishment::
      btm_ble_create_ll_conn_complete(status);
}
bool maybe_resolve_address(RawAddress* bda, tBLE_ADDR_TYPE* bda_type) {
  mock_function_count_map[__func__]++;
  return test::mock::stack_acl_btm_ble_connection_establishment::
      maybe_resolve_address(bda, bda_type);
}
void btm_ble_conn_complete(uint8_t* p, uint16_t evt_len, bool enhanced) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl_btm_ble_connection_establishment::btm_ble_conn_complete(
      p, evt_len, enhanced);
}
void btm_ble_create_conn_cancel() {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl_btm_ble_connection_establishment::
      btm_ble_create_conn_cancel();
}
void btm_ble_create_conn_cancel_complete(uint8_t* p) {
  mock_function_count_map[__func__]++;
  test::mock::stack_acl_btm_ble_connection_establishment::
      btm_ble_create_conn_cancel_complete(p);
}

// END mockcify generation

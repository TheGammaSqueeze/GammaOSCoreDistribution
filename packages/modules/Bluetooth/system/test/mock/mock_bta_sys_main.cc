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
 *   Functions generated:10
 *
 *  mockcify.pl ver 0.3.0
 */

#include <cstdint>
#include <functional>
#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

// Mock include file to share data between tests and mock
#include "test/mock/mock_bta_sys_main.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace bta_sys_main {

// Function state capture and return values, if needed
struct BTA_sys_signal_hw_error BTA_sys_signal_hw_error;
struct bta_set_forward_hw_failures bta_set_forward_hw_failures;
struct bta_sys_deregister bta_sys_deregister;
struct bta_sys_disable bta_sys_disable;
struct bta_sys_init bta_sys_init;
struct bta_sys_is_register bta_sys_is_register;
struct bta_sys_register bta_sys_register;
struct bta_sys_sendmsg bta_sys_sendmsg;
struct bta_sys_sendmsg_delayed bta_sys_sendmsg_delayed;
struct bta_sys_start_timer bta_sys_start_timer;

}  // namespace bta_sys_main
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void BTA_sys_signal_hw_error() {
  mock_function_count_map[__func__]++;
  test::mock::bta_sys_main::BTA_sys_signal_hw_error();
}
void bta_set_forward_hw_failures(bool value) {
  mock_function_count_map[__func__]++;
  test::mock::bta_sys_main::bta_set_forward_hw_failures(value);
}
void bta_sys_deregister(uint8_t id) {
  mock_function_count_map[__func__]++;
  test::mock::bta_sys_main::bta_sys_deregister(id);
}
void bta_sys_disable() {
  mock_function_count_map[__func__]++;
  test::mock::bta_sys_main::bta_sys_disable();
}
void bta_sys_init(void) {
  mock_function_count_map[__func__]++;
  test::mock::bta_sys_main::bta_sys_init();
}
bool bta_sys_is_register(uint8_t id) {
  mock_function_count_map[__func__]++;
  return test::mock::bta_sys_main::bta_sys_is_register(id);
}
void bta_sys_register(uint8_t id, const tBTA_SYS_REG* p_reg) {
  mock_function_count_map[__func__]++;
  test::mock::bta_sys_main::bta_sys_register(id, p_reg);
}
void bta_sys_sendmsg(void* p_msg) {
  mock_function_count_map[__func__]++;
  test::mock::bta_sys_main::bta_sys_sendmsg(p_msg);
}
void bta_sys_sendmsg_delayed(void* p_msg, const base::TimeDelta& delay) {
  mock_function_count_map[__func__]++;
  test::mock::bta_sys_main::bta_sys_sendmsg_delayed(p_msg, delay);
}
void bta_sys_start_timer(alarm_t* alarm, uint64_t interval_ms, uint16_t event,
                         uint16_t layer_specific) {
  mock_function_count_map[__func__]++;
  test::mock::bta_sys_main::bta_sys_start_timer(alarm, interval_ms, event,
                                                layer_specific);
}
// Mocked functions complete
// END mockcify generation

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
 *   Functions generated:17
 *
 *  mockcify.pl ver 0.2.1
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
//       may need attention to prune the inclusion set.
#include <base/logging.h>
#include <hardware/bluetooth.h>
#include <hardware/bluetooth_headset_interface.h>
#include <hardware/bt_av.h>
#include <hardware/bt_gatt.h>
#include <hardware/bt_hd.h>
#include <hardware/bt_hearing_aid.h>
#include <hardware/bt_hf_client.h>
#include <hardware/bt_hh.h>
#include <hardware/bt_le_audio.h>
#include <hardware/bt_pan.h>
#include <hardware/bt_rc.h>
#include <hardware/bt_sdp.h>
#include <hardware/bt_sock.h>
#include <hardware/bt_vc.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "bta/include/bta_hearing_aid_api.h"
#include "bta/include/bta_hf_client_api.h"
#include "btif/avrcp/avrcp_service.h"
#include "btif/include/btif_a2dp.h"
#include "btif/include/btif_activity_attribution.h"
#include "btif/include/btif_api.h"
#include "btif/include/btif_av.h"
#include "btif/include/btif_bqr.h"
#include "btif/include/btif_config.h"
#include "btif/include/btif_debug_conn.h"
#include "btif/include/btif_hf.h"
#include "btif/include/btif_keystore.h"
#include "btif/include/btif_metrics_logging.h"
#include "btif/include/btif_storage.h"
#include "btif/include/stack_manager.h"
#include "common/address_obfuscator.h"
#include "common/metric_id_allocator.h"
#include "common/metrics.h"
#include "common/os_utils.h"
#include "gd/common/init_flags.h"
#include "main/shim/dumpsys.h"
#include "main/shim/shim.h"
#include "osi/include/alarm.h"
#include "osi/include/allocation_tracker.h"
#include "osi/include/log.h"
#include "osi/include/osi.h"
#include "osi/include/wakelock.h"
#include "stack/gatt/connection_manager.h"
#include "stack/include/avdt_api.h"
#include "types/raw_address.h"
#include "utils/include/bt_utils.h"

// Mocked compile conditionals, if any
#ifndef UNUSED_ATTR
#define UNUSED_ATTR
#endif

namespace test {
namespace mock {
namespace btif_bluetooth {

// Shared state between mocked functions and tests
// Name: is_atv_device
// Params:
// Returns: bool
struct is_atv_device {
  std::function<bool()> body{[]() { return false; }};
  bool operator()() { return body(); };
};
extern struct is_atv_device is_atv_device;
// Name: is_common_criteria_mode
// Params:
// Returns: bool
struct is_common_criteria_mode {
  std::function<bool()> body{[]() { return false; }};
  bool operator()() { return body(); };
};
extern struct is_common_criteria_mode is_common_criteria_mode;
// Name: is_restricted_mode
// Params:
// Returns: bool
struct is_restricted_mode {
  std::function<bool()> body{[]() { return false; }};
  bool operator()() { return body(); };
};
extern struct is_restricted_mode is_restricted_mode;
// Name: dut_mode_configure
// Params: uint8_t enable
// Returns: int
struct dut_mode_configure {
  std::function<int(uint8_t enable)> body{[](uint8_t enable) { return 0; }};
  int operator()(uint8_t enable) { return body(enable); };
};
extern struct dut_mode_configure dut_mode_configure;
// Name: dut_mode_send
// Params: uint16_t opcode, uint8_t* buf, uint8_t len
// Returns: int
struct dut_mode_send {
  std::function<int(uint16_t opcode, uint8_t* buf, uint8_t len)> body{
      [](uint16_t opcode, uint8_t* buf, uint8_t len) { return 0; }};
  int operator()(uint16_t opcode, uint8_t* buf, uint8_t len) {
    return body(opcode, buf, len);
  };
};
extern struct dut_mode_send dut_mode_send;
// Name: get_common_criteria_config_compare_result
// Params:
// Returns: int
struct get_common_criteria_config_compare_result {
  std::function<int()> body{[]() { return 0; }};
  int operator()() { return body(); };
};
extern struct get_common_criteria_config_compare_result
    get_common_criteria_config_compare_result;
// Name: get_remote_device_properties
// Params: RawAddress* remote_addr
// Returns: int
struct get_remote_device_properties {
  std::function<int(RawAddress* remote_addr)> body{
      [](RawAddress* remote_addr) { return 0; }};
  int operator()(RawAddress* remote_addr) { return body(remote_addr); };
};
extern struct get_remote_device_properties get_remote_device_properties;
// Name: get_remote_device_property
// Params: RawAddress* remote_addr, bt_property_type_t type
// Returns: int
struct get_remote_device_property {
  std::function<int(RawAddress* remote_addr, bt_property_type_t type)> body{
      [](RawAddress* remote_addr, bt_property_type_t type) { return 0; }};
  int operator()(RawAddress* remote_addr, bt_property_type_t type) {
    return body(remote_addr, type);
  };
};
extern struct get_remote_device_property get_remote_device_property;
// Name: get_remote_services
// Params: RawAddress* remote_addr
// Returns: int
struct get_remote_services {
  std::function<int(RawAddress* remote_addr)> body{
      [](RawAddress* remote_addr) { return 0; }};
  int operator()(RawAddress* remote_addr) { return body(remote_addr); };
};
extern struct get_remote_services get_remote_services;
// Name: le_test_mode
// Params: uint16_t opcode, uint8_t* buf, uint8_t len
// Returns: int
struct le_test_mode {
  std::function<int(uint16_t opcode, uint8_t* buf, uint8_t len)> body{
      [](uint16_t opcode, uint8_t* buf, uint8_t len) { return 0; }};
  int operator()(uint16_t opcode, uint8_t* buf, uint8_t len) {
    return body(opcode, buf, len);
  };
};
extern struct le_test_mode le_test_mode;
// Name: set_remote_device_property
// Params: RawAddress* remote_addr, const bt_property_t* property
// Returns: int
struct set_remote_device_property {
  std::function<int(RawAddress* remote_addr, const bt_property_t* property)>
      body{[](RawAddress* remote_addr, const bt_property_t* property) {
        return 0;
      }};
  int operator()(RawAddress* remote_addr, const bt_property_t* property) {
    return body(remote_addr, property);
  };
};
extern struct set_remote_device_property set_remote_device_property;
// Name: set_hal_cbacks
// Params: bt_callbacks_t* callbacks
// Returns: void
struct set_hal_cbacks {
  std::function<void(bt_callbacks_t* callbacks)> body{
      [](bt_callbacks_t* callbacks) { ; }};
  void operator()(bt_callbacks_t* callbacks) { body(callbacks); };
};
extern struct set_hal_cbacks set_hal_cbacks;

}  // namespace btif_bluetooth
}  // namespace mock
}  // namespace test

// END mockcify generation

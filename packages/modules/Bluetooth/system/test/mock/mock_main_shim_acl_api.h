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
#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:14
 *
 *  mockcify.pl ver 0.5.0
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
#include <cstddef>
#include <cstdint>
#include <future>

#include "gd/hci/acl_manager.h"
#include "main/shim/acl_api.h"
#include "main/shim/dumpsys.h"
#include "main/shim/helpers.h"
#include "main/shim/stack.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace main_shim_acl_api {

// Shared state between mocked functions and tests
// Name: ACL_AcceptLeConnectionFrom
// Params: const tBLE_BD_ADDR& legacy_address_with_type, bool is_direct
// Return: bool
struct ACL_AcceptLeConnectionFrom {
  static bool return_value;
  std::function<bool(const tBLE_BD_ADDR& legacy_address_with_type,
                     bool is_direct)>
      body{[](const tBLE_BD_ADDR& legacy_address_with_type, bool is_direct) {
        return return_value;
      }};
  bool operator()(const tBLE_BD_ADDR& legacy_address_with_type,
                  bool is_direct) {
    return body(legacy_address_with_type, is_direct);
  };
};
extern struct ACL_AcceptLeConnectionFrom ACL_AcceptLeConnectionFrom;

// Name: ACL_AddToAddressResolution
// Params: const tBLE_BD_ADDR& legacy_address_with_type, const Octet16&
// peer_irk, const Octet16& local_irk Return: void
struct ACL_AddToAddressResolution {
  std::function<void(const tBLE_BD_ADDR& legacy_address_with_type,
                     const Octet16& peer_irk, const Octet16& local_irk)>
      body{[](const tBLE_BD_ADDR& legacy_address_with_type,
              const Octet16& peer_irk, const Octet16& local_irk) {}};
  void operator()(const tBLE_BD_ADDR& legacy_address_with_type,
                  const Octet16& peer_irk, const Octet16& local_irk) {
    body(legacy_address_with_type, peer_irk, local_irk);
  };
};
extern struct ACL_AddToAddressResolution ACL_AddToAddressResolution;

// Name: ACL_CancelClassicConnection
// Params: const RawAddress& raw_address
// Return: void
struct ACL_CancelClassicConnection {
  std::function<void(const RawAddress& raw_address)> body{
      [](const RawAddress& raw_address) {}};
  void operator()(const RawAddress& raw_address) { body(raw_address); };
};
extern struct ACL_CancelClassicConnection ACL_CancelClassicConnection;

// Name: ACL_ClearAddressResolution
// Params:
// Return: void
struct ACL_ClearAddressResolution {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct ACL_ClearAddressResolution ACL_ClearAddressResolution;

// Name: ACL_ClearFilterAcceptList
// Params:
// Return: void
struct ACL_ClearFilterAcceptList {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct ACL_ClearFilterAcceptList ACL_ClearFilterAcceptList;

// Name: ACL_ConfigureLePrivacy
// Params: bool is_le_privacy_enabled
// Return: void
struct ACL_ConfigureLePrivacy {
  std::function<void(bool is_le_privacy_enabled)> body{
      [](bool is_le_privacy_enabled) {}};
  void operator()(bool is_le_privacy_enabled) { body(is_le_privacy_enabled); };
};
extern struct ACL_ConfigureLePrivacy ACL_ConfigureLePrivacy;

// Name: ACL_CreateClassicConnection
// Params: const RawAddress& raw_address
// Return: void
struct ACL_CreateClassicConnection {
  std::function<void(const RawAddress& raw_address)> body{
      [](const RawAddress& raw_address) {}};
  void operator()(const RawAddress& raw_address) { body(raw_address); };
};
extern struct ACL_CreateClassicConnection ACL_CreateClassicConnection;

// Name: ACL_Disconnect
// Params: uint16_t handle, bool is_classic, tHCI_STATUS reason, std::string
// comment Return: void
struct ACL_Disconnect {
  std::function<void(uint16_t handle, bool is_classic, tHCI_STATUS reason,
                     std::string comment)>
      body{[](uint16_t handle, bool is_classic, tHCI_STATUS reason,
              std::string comment) {}};
  void operator()(uint16_t handle, bool is_classic, tHCI_STATUS reason,
                  std::string comment) {
    body(handle, is_classic, reason, comment);
  };
};
extern struct ACL_Disconnect ACL_Disconnect;

// Name: ACL_IgnoreAllLeConnections
// Params:
// Return: void
struct ACL_IgnoreAllLeConnections {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct ACL_IgnoreAllLeConnections ACL_IgnoreAllLeConnections;

// Name: ACL_IgnoreLeConnectionFrom
// Params: const tBLE_BD_ADDR& legacy_address_with_type
// Return: void
struct ACL_IgnoreLeConnectionFrom {
  std::function<void(const tBLE_BD_ADDR& legacy_address_with_type)> body{
      [](const tBLE_BD_ADDR& legacy_address_with_type) {}};
  void operator()(const tBLE_BD_ADDR& legacy_address_with_type) {
    body(legacy_address_with_type);
  };
};
extern struct ACL_IgnoreLeConnectionFrom ACL_IgnoreLeConnectionFrom;

// Name: ACL_ReadConnectionAddress
// Params: const RawAddress& pseudo_addr, RawAddress& conn_addr, tBLE_ADDR_TYPE*
// p_addr_type Return: void
struct ACL_ReadConnectionAddress {
  std::function<void(const RawAddress& pseudo_addr, RawAddress& conn_addr,
                     tBLE_ADDR_TYPE* p_addr_type)>
      body{[](const RawAddress& pseudo_addr, RawAddress& conn_addr,
              tBLE_ADDR_TYPE* p_addr_type) {}};
  void operator()(const RawAddress& pseudo_addr, RawAddress& conn_addr,
                  tBLE_ADDR_TYPE* p_addr_type) {
    body(pseudo_addr, conn_addr, p_addr_type);
  };
};
extern struct ACL_ReadConnectionAddress ACL_ReadConnectionAddress;

// Name: ACL_RemoveFromAddressResolution
// Params: const tBLE_BD_ADDR& legacy_address_with_type
// Return: void
struct ACL_RemoveFromAddressResolution {
  std::function<void(const tBLE_BD_ADDR& legacy_address_with_type)> body{
      [](const tBLE_BD_ADDR& legacy_address_with_type) {}};
  void operator()(const tBLE_BD_ADDR& legacy_address_with_type) {
    body(legacy_address_with_type);
  };
};
extern struct ACL_RemoveFromAddressResolution ACL_RemoveFromAddressResolution;

// Name: ACL_Shutdown
// Params:
// Return: void
struct ACL_Shutdown {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct ACL_Shutdown ACL_Shutdown;

// Name: ACL_WriteData
// Params: uint16_t handle, BT_HDR* p_buf
// Return: void
struct ACL_WriteData {
  std::function<void(uint16_t handle, BT_HDR* p_buf)> body{
      [](uint16_t handle, BT_HDR* p_buf) {}};
  void operator()(uint16_t handle, BT_HDR* p_buf) { body(handle, p_buf); };
};
extern struct ACL_WriteData ACL_WriteData;

}  // namespace main_shim_acl_api
}  // namespace mock
}  // namespace test

// END mockcify generation

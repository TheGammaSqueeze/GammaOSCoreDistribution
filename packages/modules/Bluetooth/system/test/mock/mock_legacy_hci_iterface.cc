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

#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

#include <base/callback.h>
#include <stddef.h>

#include "bt_target.h"
#include "btu.h"
#include "hcimsgs.h"
#include "stack/include/acl_hci_link_interface.h"
#include "stack/include/bt_octets.h"
#include "types/raw_address.h"

namespace test {
namespace mock {
namespace hcic_hcicmds {

struct btsnd_hcic_change_conn_type {
  uint16_t handle{0};
  uint16_t packet_types{0};
} btsnd_hcic_change_conn_type;

}  // namespace hcic_hcicmds
}  // namespace mock
}  // namespace test

namespace mock = test::mock::hcic_hcicmds;

namespace {
void btsnd_hcic_disconnect(uint16_t handle, uint8_t reason) {
  mock_function_count_map[__func__]++;
}
void btsnd_hcic_switch_role(const RawAddress& bd_addr, uint8_t role) {
  mock_function_count_map[__func__]++;
}
}  // namespace

bluetooth::legacy::hci::Interface interface_ = {
    .Disconnect = btsnd_hcic_disconnect,
    .StartRoleSwitch = btsnd_hcic_switch_role,
    .ChangeConnectionPacketType = btsnd_hcic_change_conn_type,
};

const bluetooth::legacy::hci::Interface&
bluetooth::legacy::hci::GetInterface() {
  return interface_;
}

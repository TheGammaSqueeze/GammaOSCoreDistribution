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

#include <base/bind.h>
#include <base/location.h>
#include <gtest/gtest.h>

#include <chrono>

#include "bta/dm/bta_dm_int.h"
#include "bta/hf_client/bta_hf_client_int.h"
#include "bta/include/bta_api.h"
#include "bta/include/bta_dm_api.h"
#include "bta/include/bta_hf_client_api.h"
#include "btif/include/stack_manager.h"
#include "common/message_loop_thread.h"
#include "osi/include/compat.h"
#include "stack/include/btm_status.h"
#include "test/common/main_handler.h"
#include "test/mock/mock_osi_alarm.h"
#include "test/mock/mock_osi_allocator.h"
#include "test/mock/mock_stack_acl.h"
#include "test/mock/mock_stack_btm_sec.h"

using namespace std::chrono_literals;

std::map<std::string, int> mock_function_count_map;

extern struct btm_client_interface_t btm_client_interface;

void LogMsg(uint32_t trace_set_mask, const char* fmt_str, ...) {}

namespace base {
class MessageLoop;
}  // namespace base

namespace {
constexpr uint8_t kUnusedTimer = BTA_ID_MAX;
const RawAddress kRawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
const RawAddress kRawAddress2({0x12, 0x34, 0x56, 0x78, 0x9a, 0xbc});
constexpr char kRemoteName[] = "TheRemoteName";

const char* test_flags[] = {
    "INIT_logging_debug_enabled_for_all=true",
    nullptr,
};

bool bta_dm_search_sm_execute(BT_HDR_RIGID* p_msg) { return true; }
void bta_dm_search_sm_disable() { bta_sys_deregister(BTA_ID_DM_SEARCH); }

const tBTA_SYS_REG bta_dm_search_reg = {bta_dm_search_sm_execute,
                                        bta_dm_search_sm_disable};

}  // namespace

struct alarm_t {
  alarm_t(const char* name){};
  int any_value;
};

class BtaDmTest : public testing::Test {
 protected:
  void SetUp() override {
    mock_function_count_map.clear();
    bluetooth::common::InitFlags::Load(test_flags);
    test::mock::osi_alarm::alarm_new.body = [](const char* name) -> alarm_t* {
      return new alarm_t(name);
    };
    test::mock::osi_alarm::alarm_free.body = [](alarm_t* alarm) {
      delete alarm;
    };
    test::mock::osi_allocator::osi_malloc.body = [](size_t size) {
      return malloc(size);
    };
    test::mock::osi_allocator::osi_calloc.body = [](size_t size) {
      return calloc(1UL, size);
    };
    test::mock::osi_allocator::osi_free.body = [](void* ptr) { free(ptr); };
    test::mock::osi_allocator::osi_free_and_reset.body = [](void** ptr) {
      free(*ptr);
      *ptr = nullptr;
    };

    main_thread_start_up();
    post_on_bt_main([]() { LOG_INFO("Main thread started up"); });

    bta_sys_register(BTA_ID_DM_SEARCH, &bta_dm_search_reg);
    bta_dm_init_cb();

    for (int i = 0; i < BTA_DM_NUM_PM_TIMER; i++) {
      for (int j = 0; j < BTA_DM_PM_MODE_TIMER_MAX; j++) {
        bta_dm_cb.pm_timer[i].srvc_id[j] = kUnusedTimer;
      }
    }
  }
  void TearDown() override {
    bta_sys_deregister(BTA_ID_DM_SEARCH);
    bta_dm_deinit_cb();
    post_on_bt_main([]() { LOG_INFO("Main thread shutting down"); });
    main_thread_shut_down();

    test::mock::osi_alarm::alarm_new = {};
    test::mock::osi_alarm::alarm_free = {};
    test::mock::osi_allocator::osi_malloc = {};
    test::mock::osi_allocator::osi_calloc = {};
    test::mock::osi_allocator::osi_free = {};
    test::mock::osi_allocator::osi_free_and_reset = {};
  }
};

TEST_F(BtaDmTest, nop) {
  bool status = true;
  ASSERT_EQ(true, status);
}

TEST_F(BtaDmTest, disable_no_acl_links) {
  bta_dm_cb.disabling = true;

  alarm_callback_t alarm_callback;
  void* alarm_data{nullptr};
  test::mock::osi_alarm::alarm_set_on_mloop.body =
      [&alarm_callback, &alarm_data](alarm_t* alarm, uint64_t interval_ms,
                                     alarm_callback_t cb, void* data) {
        ASSERT_TRUE(alarm != nullptr);
        alarm_callback = cb;
        alarm_data = data;
      };

  bta_dm_disable();  // Waiting for all ACL connections to drain
  ASSERT_EQ(0, mock_function_count_map["btm_remove_acl"]);
  ASSERT_EQ(1, mock_function_count_map["alarm_set_on_mloop"]);

  // Execute timer callback
  alarm_callback(alarm_data);
  ASSERT_EQ(1, mock_function_count_map["alarm_set_on_mloop"]);
  ASSERT_EQ(0, mock_function_count_map["BTIF_dm_disable"]);
  ASSERT_EQ(1, mock_function_count_map["future_ready"]);
  ASSERT_TRUE(!bta_dm_cb.disabling);

  test::mock::osi_alarm::alarm_set_on_mloop = {};
}

TEST_F(BtaDmTest, disable_first_pass_with_acl_links) {
  uint16_t links_up = 1;
  test::mock::stack_acl::BTM_GetNumAclLinks.body = [&links_up]() {
    return links_up;
  };
  bta_dm_cb.disabling = true;
  // ACL link is open
  bta_dm_cb.device_list.count = 1;

  alarm_callback_t alarm_callback;
  void* alarm_data{nullptr};
  test::mock::osi_alarm::alarm_set_on_mloop.body =
      [&alarm_callback, &alarm_data](alarm_t* alarm, uint64_t interval_ms,
                                     alarm_callback_t cb, void* data) {
        ASSERT_TRUE(alarm != nullptr);
        alarm_callback = cb;
        alarm_data = data;
      };

  bta_dm_disable();              // Waiting for all ACL connections to drain
  ASSERT_EQ(1, mock_function_count_map["alarm_set_on_mloop"]);
  ASSERT_EQ(0, mock_function_count_map["BTIF_dm_disable"]);

  links_up = 0;
  // First disable pass
  alarm_callback(alarm_data);
  ASSERT_EQ(1, mock_function_count_map["alarm_set_on_mloop"]);
  ASSERT_EQ(1, mock_function_count_map["BTIF_dm_disable"]);
  ASSERT_TRUE(!bta_dm_cb.disabling);

  test::mock::stack_acl::BTM_GetNumAclLinks = {};
  test::mock::osi_alarm::alarm_set_on_mloop = {};
}

TEST_F(BtaDmTest, disable_second_pass_with_acl_links) {
  uint16_t links_up = 1;
  test::mock::stack_acl::BTM_GetNumAclLinks.body = [&links_up]() {
    return links_up;
  };
  bta_dm_cb.disabling = true;
  // ACL link is open
  bta_dm_cb.device_list.count = 1;

  alarm_callback_t alarm_callback;
  void* alarm_data{nullptr};
  test::mock::osi_alarm::alarm_set_on_mloop.body =
      [&alarm_callback, &alarm_data](alarm_t* alarm, uint64_t interval_ms,
                                     alarm_callback_t cb, void* data) {
        ASSERT_TRUE(alarm != nullptr);
        alarm_callback = cb;
        alarm_data = data;
      };

  bta_dm_disable();  // Waiting for all ACL connections to drain
  ASSERT_EQ(1, mock_function_count_map["alarm_set_on_mloop"]);
  ASSERT_EQ(0, mock_function_count_map["BTIF_dm_disable"]);

  // First disable pass
  alarm_callback(alarm_data);
  ASSERT_EQ(2, mock_function_count_map["alarm_set_on_mloop"]);
  ASSERT_EQ(0, mock_function_count_map["BTIF_dm_disable"]);
  ASSERT_EQ(1, mock_function_count_map["btm_remove_acl"]);

  // Second disable pass
  alarm_callback(alarm_data);
  ASSERT_EQ(1, mock_function_count_map["BTIF_dm_disable"]);
  ASSERT_TRUE(!bta_dm_cb.disabling);

  test::mock::stack_acl::BTM_GetNumAclLinks = {};
  test::mock::osi_alarm::alarm_set_on_mloop = {};
}

namespace {

struct BTA_DM_ENCRYPT_CBACK_parms {
  const RawAddress bd_addr;
  tBT_TRANSPORT transport;
  tBTA_STATUS result;
};

std::queue<BTA_DM_ENCRYPT_CBACK_parms> BTA_DM_ENCRYPT_CBACK_queue;

void BTA_DM_ENCRYPT_CBACK(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                          tBTA_STATUS result) {
  BTA_DM_ENCRYPT_CBACK_queue.push({bd_addr, transport, result});
}

}  // namespace

namespace bluetooth {
namespace legacy {
namespace testing {
tBTA_DM_PEER_DEVICE* allocate_device_for(const RawAddress& bd_addr,
                                         tBT_TRANSPORT transport);

void bta_dm_remname_cback(void* p);

}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth

TEST_F(BtaDmTest, bta_dm_set_encryption) {
  const RawAddress bd_addr{{0x11, 0x22, 0x33, 0x44, 0x55, 0x66}};
  const tBT_TRANSPORT transport{BT_TRANSPORT_LE};
  const tBTM_BLE_SEC_ACT sec_act{BTM_BLE_SEC_NONE};

  // Callback not provided
  bta_dm_set_encryption(bd_addr, transport, nullptr, sec_act);

  // Device connection does not exist
  bta_dm_set_encryption(bd_addr, transport, BTA_DM_ENCRYPT_CBACK, sec_act);

  // Setup a connected device
  tBTA_DM_PEER_DEVICE* device =
      bluetooth::legacy::testing::allocate_device_for(bd_addr, transport);
  ASSERT_TRUE(device != nullptr);
  device->conn_state = BTA_DM_CONNECTED;
  device->p_encrypt_cback = nullptr;

  // Setup a device that is busy with another encryption
  // Fake indication that the encryption is in progress with non-null callback
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_set_encryption(bd_addr, transport, BTA_DM_ENCRYPT_CBACK, sec_act);
  ASSERT_EQ(0, mock_function_count_map["BTM_SetEncryption"]);
  ASSERT_EQ(1UL, BTA_DM_ENCRYPT_CBACK_queue.size());
  auto params = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_BUSY, params.result);
  device->p_encrypt_cback = nullptr;

  // Setup a device that fails encryption
  test::mock::stack_btm_sec::BTM_SetEncryption.body =
      [](const RawAddress& bd_addr, tBT_TRANSPORT transport,
         tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
         tBTM_BLE_SEC_ACT sec_act) -> tBTM_STATUS {
    return BTM_MODE_UNSUPPORTED;
  };

  bta_dm_set_encryption(bd_addr, transport, BTA_DM_ENCRYPT_CBACK, sec_act);
  ASSERT_EQ(1, mock_function_count_map["BTM_SetEncryption"]);
  ASSERT_EQ(0UL, BTA_DM_ENCRYPT_CBACK_queue.size());
  device->p_encrypt_cback = nullptr;

  // Setup a device that successfully starts encryption
  test::mock::stack_btm_sec::BTM_SetEncryption.body =
      [](const RawAddress& bd_addr, tBT_TRANSPORT transport,
         tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
         tBTM_BLE_SEC_ACT sec_act) -> tBTM_STATUS { return BTM_CMD_STARTED; };

  bta_dm_set_encryption(bd_addr, transport, BTA_DM_ENCRYPT_CBACK, sec_act);
  ASSERT_EQ(2, mock_function_count_map["BTM_SetEncryption"]);
  ASSERT_EQ(0UL, BTA_DM_ENCRYPT_CBACK_queue.size());
  ASSERT_NE(nullptr, device->p_encrypt_cback);

  test::mock::stack_btm_sec::BTM_SetEncryption = {};
  BTA_DM_ENCRYPT_CBACK_queue = {};
}

extern void bta_dm_encrypt_cback(const RawAddress* bd_addr,
                                 tBT_TRANSPORT transport,
                                 UNUSED_ATTR void* p_ref_data,
                                 tBTM_STATUS result);

TEST_F(BtaDmTest, bta_dm_encrypt_cback) {
  const RawAddress bd_addr{{0x11, 0x22, 0x33, 0x44, 0x55, 0x66}};
  const tBT_TRANSPORT transport{BT_TRANSPORT_LE};

  // Setup a connected device
  tBTA_DM_PEER_DEVICE* device =
      bluetooth::legacy::testing::allocate_device_for(bd_addr, transport);
  ASSERT_TRUE(device != nullptr);
  device->conn_state = BTA_DM_CONNECTED;

  // Encryption with no callback set
  device->p_encrypt_cback = nullptr;
  bta_dm_encrypt_cback(&bd_addr, transport, nullptr, BTM_SUCCESS);
  ASSERT_EQ(0UL, BTA_DM_ENCRYPT_CBACK_queue.size());

  // Encryption with callback
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_encrypt_cback(&bd_addr, transport, nullptr, BTM_SUCCESS);
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_encrypt_cback(&bd_addr, transport, nullptr, BTM_WRONG_MODE);
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_encrypt_cback(&bd_addr, transport, nullptr, BTM_NO_RESOURCES);
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_encrypt_cback(&bd_addr, transport, nullptr, BTM_BUSY);
  device->p_encrypt_cback = BTA_DM_ENCRYPT_CBACK;
  bta_dm_encrypt_cback(&bd_addr, transport, nullptr, BTM_ILLEGAL_VALUE);

  ASSERT_EQ(5UL, BTA_DM_ENCRYPT_CBACK_queue.size());

  auto params_BTM_SUCCESS = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_SUCCESS, params_BTM_SUCCESS.result);
  auto params_BTM_WRONG_MODE = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_WRONG_MODE, params_BTM_WRONG_MODE.result);
  auto params_BTM_NO_RESOURCES = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_NO_RESOURCES, params_BTM_NO_RESOURCES.result);
  auto params_BTM_BUSY = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_BUSY, params_BTM_BUSY.result);
  auto params_BTM_ILLEGAL_VALUE = BTA_DM_ENCRYPT_CBACK_queue.front();
  BTA_DM_ENCRYPT_CBACK_queue.pop();
  ASSERT_EQ(BTA_FAILURE, params_BTM_ILLEGAL_VALUE.result);
}

TEST_F(BtaDmTest, bta_dm_event_text) {
  std::vector<std::pair<tBTA_DM_EVT, std::string>> events = {
      std::make_pair(BTA_DM_API_SEARCH_EVT, "BTA_DM_API_SEARCH_EVT"),
      std::make_pair(BTA_DM_API_DISCOVER_EVT, "BTA_DM_API_DISCOVER_EVT"),
      std::make_pair(BTA_DM_INQUIRY_CMPL_EVT, "BTA_DM_INQUIRY_CMPL_EVT"),
      std::make_pair(BTA_DM_REMT_NAME_EVT, "BTA_DM_REMT_NAME_EVT"),
      std::make_pair(BTA_DM_SDP_RESULT_EVT, "BTA_DM_SDP_RESULT_EVT"),
      std::make_pair(BTA_DM_SEARCH_CMPL_EVT, "BTA_DM_SEARCH_CMPL_EVT"),
      std::make_pair(BTA_DM_DISCOVERY_RESULT_EVT,
                     "BTA_DM_DISCOVERY_RESULT_EVT"),
      std::make_pair(BTA_DM_DISC_CLOSE_TOUT_EVT, "BTA_DM_DISC_CLOSE_TOUT_EVT"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), bta_dm_event_text(event.first).c_str());
  }
  ASSERT_STREQ(base::StringPrintf("UNKNOWN[0x%04x]",
                                  std::numeric_limits<uint16_t>::max())
                   .c_str(),
               bta_dm_event_text(static_cast<tBTA_DM_EVT>(
                                     std::numeric_limits<uint16_t>::max()))
                   .c_str());
}

TEST_F(BtaDmTest, bta_dm_state_text) {
  std::vector<std::pair<tBTA_DM_STATE, std::string>> states = {
      std::make_pair(BTA_DM_SEARCH_IDLE, "BTA_DM_SEARCH_IDLE"),
      std::make_pair(BTA_DM_SEARCH_ACTIVE, "BTA_DM_SEARCH_ACTIVE"),
      std::make_pair(BTA_DM_SEARCH_CANCELLING, "BTA_DM_SEARCH_CANCELLING"),
      std::make_pair(BTA_DM_DISCOVER_ACTIVE, "BTA_DM_DISCOVER_ACTIVE"),
  };
  for (const auto& state : states) {
    ASSERT_STREQ(state.second.c_str(), bta_dm_state_text(state.first).c_str());
  }
  auto unknown =
      base::StringPrintf("UNKNOWN[%d]", std::numeric_limits<int>::max());
  ASSERT_STREQ(unknown.c_str(),
               bta_dm_state_text(
                   static_cast<tBTA_DM_STATE>(std::numeric_limits<int>::max()))
                   .c_str());
}

TEST_F(BtaDmTest, bta_dm_remname_cback__typical) {
  bta_dm_search_cb = {
      .name_discover_done = false,
      .peer_bdaddr = kRawAddress,
  };

  tBTM_REMOTE_DEV_NAME name = {
      .status = BTM_SUCCESS,
      .bd_addr = kRawAddress,
      .length = static_cast<uint16_t>(strlen(kRemoteName)),
      .remote_bd_name = {},
      .hci_status = HCI_SUCCESS,
  };
  strlcpy(reinterpret_cast<char*>(&name.remote_bd_name), kRemoteName,
          strlen(kRemoteName));

  bluetooth::legacy::testing::bta_dm_remname_cback(static_cast<void*>(&name));

  sync_main_handler();

  ASSERT_EQ(1, mock_function_count_map["BTM_SecDeleteRmtNameNotifyCallback"]);
  ASSERT_TRUE(bta_dm_search_cb.name_discover_done);
}

TEST_F(BtaDmTest, bta_dm_remname_cback__wrong_address) {
  bta_dm_search_cb = {
      .name_discover_done = false,
      .peer_bdaddr = kRawAddress,
  };

  tBTM_REMOTE_DEV_NAME name = {
      .status = BTM_SUCCESS,
      .bd_addr = kRawAddress2,
      .length = static_cast<uint16_t>(strlen(kRemoteName)),
      .remote_bd_name = {},
      .hci_status = HCI_SUCCESS,
  };
  strlcpy(reinterpret_cast<char*>(&name.remote_bd_name), kRemoteName,
          strlen(kRemoteName));

  bluetooth::legacy::testing::bta_dm_remname_cback(static_cast<void*>(&name));

  sync_main_handler();

  ASSERT_EQ(0, mock_function_count_map["BTM_SecDeleteRmtNameNotifyCallback"]);
  ASSERT_FALSE(bta_dm_search_cb.name_discover_done);
}

TEST_F(BtaDmTest, bta_dm_remname_cback__HCI_ERR_CONNECTION_EXISTS) {
  bta_dm_search_cb = {
      .name_discover_done = false,
      .peer_bdaddr = kRawAddress,
  };

  tBTM_REMOTE_DEV_NAME name = {
      .status = BTM_SUCCESS,
      .bd_addr = RawAddress::kEmpty,
      .length = static_cast<uint16_t>(strlen(kRemoteName)),
      .remote_bd_name = {},
      .hci_status = HCI_ERR_CONNECTION_EXISTS,
  };
  strlcpy(reinterpret_cast<char*>(&name.remote_bd_name), kRemoteName,
          strlen(kRemoteName));

  bluetooth::legacy::testing::bta_dm_remname_cback(static_cast<void*>(&name));

  sync_main_handler();

  ASSERT_EQ(1, mock_function_count_map["BTM_SecDeleteRmtNameNotifyCallback"]);
  ASSERT_TRUE(bta_dm_search_cb.name_discover_done);
}

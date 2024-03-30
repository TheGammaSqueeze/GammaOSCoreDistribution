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

#include "btif/include/btif_hh.h"

#include <gtest/gtest.h>

#include <algorithm>
#include <array>
#include <future>
#include <vector>

#include "bta/hh/bta_hh_int.h"
#include "bta/include/bta_ag_api.h"
#include "bta/include/bta_hh_api.h"
#include "btcore/include/module.h"
#include "btif/include/btif_api.h"
#include "btif/include/stack_manager.h"
#include "include/hardware/bt_hh.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_osi_allocator.h"

using namespace std::chrono_literals;

void set_hal_cbacks(bt_callbacks_t* callbacks);

uint8_t appl_trace_level = BT_TRACE_LEVEL_DEBUG;
uint8_t btif_trace_level = BT_TRACE_LEVEL_DEBUG;
uint8_t btu_trace_level = BT_TRACE_LEVEL_DEBUG;

module_t bt_utils_module;
module_t gd_controller_module;
module_t gd_idle_module;
module_t gd_shim_module;
module_t osi_module;

const tBTA_AG_RES_DATA tBTA_AG_RES_DATA::kEmpty = {};

extern void bte_hh_evt(tBTA_HH_EVT event, tBTA_HH* p_data);
extern const bthh_interface_t* btif_hh_get_interface();

namespace test {
namespace mock {
extern bool bluetooth_shim_is_gd_stack_started_up;
}
}  // namespace test

#if __GLIBC__
size_t strlcpy(char* dst, const char* src, size_t siz) {
  char* d = dst;
  const char* s = src;
  size_t n = siz;

  /* Copy as many bytes as will fit */
  if (n != 0) {
    while (--n != 0) {
      if ((*d++ = *s++) == '\0') break;
    }
  }

  /* Not enough room in dst, add NUL and traverse rest of src */
  if (n == 0) {
    if (siz != 0) *d = '\0'; /* NUL-terminate dst */
    while (*s++)
      ;
  }

  return (s - src - 1); /* count does not include NUL */
}

pid_t gettid(void) throw() { return syscall(SYS_gettid); }
#endif

namespace {
std::array<uint8_t, 32> data32 = {
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b,
    0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,
    0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20,
};

const RawAddress kDeviceAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
const uint16_t kHhHandle = 123;

// Callback parameters grouped into a structure
struct get_report_cb_t {
  RawAddress raw_address;
  bthh_status_t status;
  std::vector<uint8_t> data;
} get_report_cb_;

// Globals allow usage within function pointers
std::promise<bt_cb_thread_evt> g_thread_evt_promise;
std::promise<bt_status_t> g_status_promise;
std::promise<get_report_cb_t> g_bthh_callbacks_get_report_promise;

}  // namespace

bt_callbacks_t bt_callbacks = {
    .size = sizeof(bt_callbacks_t),
    .adapter_state_changed_cb = nullptr,  // adapter_state_changed_callback
    .adapter_properties_cb = nullptr,     // adapter_properties_callback
    .remote_device_properties_cb =
        nullptr,                            // remote_device_properties_callback
    .device_found_cb = nullptr,             // device_found_callback
    .discovery_state_changed_cb = nullptr,  // discovery_state_changed_callback
    .pin_request_cb = nullptr,              // pin_request_callback
    .ssp_request_cb = nullptr,              // ssp_request_callback
    .bond_state_changed_cb = nullptr,       // bond_state_changed_callback
    .address_consolidate_cb = nullptr,      // address_consolidate_callback
    .le_address_associate_cb = nullptr,     // le_address_associate_callback
    .acl_state_changed_cb = nullptr,        // acl_state_changed_callback
    .thread_evt_cb = nullptr,               // callback_thread_event
    .dut_mode_recv_cb = nullptr,            // dut_mode_recv_callback
    .le_test_mode_cb = nullptr,             // le_test_mode_callback
    .energy_info_cb = nullptr,              // energy_info_callback
    .link_quality_report_cb = nullptr,      // link_quality_report_callback
    .generate_local_oob_data_cb = nullptr,  // generate_local_oob_data_callback
    .switch_buffer_size_cb = nullptr,       // switch_buffer_size_callback
    .switch_codec_cb = nullptr,             // switch_codec_callback
};

bthh_callbacks_t bthh_callbacks = {
    .size = sizeof(bthh_callbacks_t),
    .connection_state_cb = nullptr,  // bthh_connection_state_callback
    .hid_info_cb = nullptr,          // bthh_hid_info_callback
    .protocol_mode_cb = nullptr,     // bthh_protocol_mode_callback
    .idle_time_cb = nullptr,         // bthh_idle_time_callback
    .get_report_cb = nullptr,        // bthh_get_report_callback
    .virtual_unplug_cb = nullptr,    // bthh_virtual_unplug_callback
    .handshake_cb = nullptr,         // bthh_handshake_callback
};

class BtifHhWithMockTest : public ::testing::Test {
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
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
  }

  void TearDown() override {
    test::mock::osi_allocator::osi_malloc = {};
    test::mock::osi_allocator::osi_calloc = {};
    test::mock::osi_allocator::osi_free = {};
    test::mock::osi_allocator::osi_free_and_reset = {};
  }
};

class BtifHhWithHalCallbacksTest : public BtifHhWithMockTest {
 protected:
  void SetUp() override {
    bluetooth::common::InitFlags::SetAllForTesting();
    BtifHhWithMockTest::SetUp();
    g_thread_evt_promise = std::promise<bt_cb_thread_evt>();
    auto future = g_thread_evt_promise.get_future();
    bt_callbacks.thread_evt_cb = [](bt_cb_thread_evt evt) {
      g_thread_evt_promise.set_value(evt);
    };
    set_hal_cbacks(&bt_callbacks);
    // Start the jni callback thread
    ASSERT_EQ(BT_STATUS_SUCCESS, btif_init_bluetooth());
    ASSERT_EQ(std::future_status::ready, future.wait_for(2s));
    ASSERT_EQ(ASSOCIATE_JVM, future.get());

    bt_callbacks.thread_evt_cb = [](bt_cb_thread_evt evt) {};
  }

  void TearDown() override {
    g_thread_evt_promise = std::promise<bt_cb_thread_evt>();
    auto future = g_thread_evt_promise.get_future();
    bt_callbacks.thread_evt_cb = [](bt_cb_thread_evt evt) {
      g_thread_evt_promise.set_value(evt);
    };
    // Shutdown the jni callback thread
    ASSERT_EQ(BT_STATUS_SUCCESS, btif_cleanup_bluetooth());
    ASSERT_EQ(std::future_status::ready, future.wait_for(2s));
    ASSERT_EQ(DISASSOCIATE_JVM, future.get());

    bt_callbacks.thread_evt_cb = [](bt_cb_thread_evt evt) {};
    BtifHhWithMockTest::TearDown();
  }
};

class BtifHhAdapterReady : public BtifHhWithHalCallbacksTest {
 protected:
  void SetUp() override {
    BtifHhWithHalCallbacksTest::SetUp();
    test::mock::bluetooth_shim_is_gd_stack_started_up = true;
    ASSERT_EQ(BT_STATUS_SUCCESS,
              btif_hh_get_interface()->init(&bthh_callbacks));
  }

  void TearDown() override {
    test::mock::bluetooth_shim_is_gd_stack_started_up = false;
    BtifHhWithHalCallbacksTest::TearDown();
  }
};

class BtifHhWithDevice : public BtifHhAdapterReady {
 protected:
  void SetUp() override {
    BtifHhAdapterReady::SetUp();

    // Short circuit a connected device
    btif_hh_cb.devices[0].bd_addr = kDeviceAddress;
    btif_hh_cb.devices[0].dev_status = BTHH_CONN_STATE_CONNECTED;
    btif_hh_cb.devices[0].dev_handle = kHhHandle;
  }

  void TearDown() override { BtifHhAdapterReady::TearDown(); }
};

TEST_F(BtifHhAdapterReady, lifecycle) {}

TEST_F(BtifHhWithDevice, BTA_HH_GET_RPT_EVT) {
  tBTA_HH data = {
      .hs_data =
          {
              .status = BTA_HH_OK,
              .handle = kHhHandle,
              .rsp_data =
                  {
                      .p_rpt_data = static_cast<BT_HDR*>(
                          osi_calloc(data32.size() + sizeof(BT_HDR))),
                  },
          },
  };

  // Fill out the deep copy data
  data.hs_data.rsp_data.p_rpt_data->len = static_cast<uint16_t>(data32.size());
  std::copy(data32.begin(), data32.begin() + data32.size(),
            reinterpret_cast<uint8_t*>((data.hs_data.rsp_data.p_rpt_data + 1)));

  g_bthh_callbacks_get_report_promise = std::promise<get_report_cb_t>();
  auto future = g_bthh_callbacks_get_report_promise.get_future();
  bthh_callbacks.get_report_cb = [](RawAddress* bd_addr,
                                    bthh_status_t hh_status, uint8_t* rpt_data,
                                    int rpt_size) {
    get_report_cb_t report = {
        .raw_address = *bd_addr,
        .status = hh_status,
        .data = std::vector<uint8_t>(),
    };
    report.data.assign(rpt_data, rpt_data + rpt_size),
        g_bthh_callbacks_get_report_promise.set_value(report);
  };

  bte_hh_evt(BTA_HH_GET_RPT_EVT, &data);
  osi_free(data.hs_data.rsp_data.p_rpt_data);

  ASSERT_EQ(std::future_status::ready, future.wait_for(2s));
  auto report = future.get();

  // Verify data was delivered
  ASSERT_STREQ(kDeviceAddress.ToString().c_str(),
               report.raw_address.ToString().c_str());
  ASSERT_EQ(BTHH_OK, report.status);
  int i = 0;
  for (const auto& data : data32) {
    ASSERT_EQ(data, report.data[i++]);
  }
}

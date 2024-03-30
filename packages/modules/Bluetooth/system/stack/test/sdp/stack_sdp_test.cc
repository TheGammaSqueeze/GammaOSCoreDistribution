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
#include <frameworks/proto_logging/stats/enums/bluetooth/enums.pb.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <stdlib.h>

#include <cstddef>

#include "stack/include/sdp_api.h"
#include "stack/sdp/sdpint.h"
#include "test/mock/mock_osi_allocator.h"
#include "test/mock/mock_stack_l2cap_api.h"

#ifndef BT_DEFAULT_BUFFER_SIZE
#define BT_DEFAULT_BUFFER_SIZE (4096 + 16)
#endif

static int L2CA_ConnectReq2_cid = 0x42;
static RawAddress addr = RawAddress({0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6});
static tSDP_DISCOVERY_DB* sdp_db = nullptr;

class StackSdpMainTest : public ::testing::Test {
 protected:
  void SetUp() override {
    sdp_init();
    test::mock::stack_l2cap_api::L2CA_ConnectReq2.body =
        [](uint16_t psm, const RawAddress& p_bd_addr, uint16_t sec_level) {
          return ++L2CA_ConnectReq2_cid;
        };
    test::mock::stack_l2cap_api::L2CA_DataWrite.body = [](uint16_t cid,
                                                          BT_HDR* p_data) {
      osi_free_and_reset((void**)&p_data);
      return 0;
    };
    test::mock::stack_l2cap_api::L2CA_DisconnectReq.body = [](uint16_t cid) {
      return true;
    };
    test::mock::stack_l2cap_api::L2CA_Register2.body =
        [](uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info, bool enable_snoop,
           tL2CAP_ERTM_INFO* p_ertm_info, uint16_t my_mtu,
           uint16_t required_remote_mtu, uint16_t sec_level) {
          return 42;  // return non zero
        };
    test::mock::osi_allocator::osi_malloc.body = [](size_t size) {
      return malloc(size);
    };
    test::mock::osi_allocator::osi_free.body = [](void* ptr) { free(ptr); };
    test::mock::osi_allocator::osi_free_and_reset.body = [](void** ptr) {
      free(*ptr);
      *ptr = nullptr;
    };
    sdp_db = (tSDP_DISCOVERY_DB*)osi_malloc(BT_DEFAULT_BUFFER_SIZE);
  }

  void TearDown() override {
    osi_free(sdp_db);
    test::mock::stack_l2cap_api::L2CA_ConnectReq2 = {};
    test::mock::stack_l2cap_api::L2CA_Register2 = {};
    test::mock::stack_l2cap_api::L2CA_DataWrite = {};
    test::mock::stack_l2cap_api::L2CA_DisconnectReq = {};
    test::mock::osi_allocator::osi_malloc = {};
    test::mock::osi_allocator::osi_free = {};
    test::mock::osi_allocator::osi_free_and_reset = {};
  }
};

TEST_F(StackSdpMainTest, sdp_service_search_request) {
  ASSERT_TRUE(SDP_ServiceSearchRequest(addr, sdp_db, nullptr));
  int cid = L2CA_ConnectReq2_cid;
  tCONN_CB* p_ccb = sdpu_find_ccb_by_cid(cid);
  ASSERT_NE(p_ccb, nullptr);
  ASSERT_EQ(p_ccb->con_state, SDP_STATE_CONN_SETUP);

  tL2CAP_CFG_INFO cfg;
  sdp_cb.reg_info.pL2CA_ConfigCfm_Cb(p_ccb->connection_id, 0, &cfg);

  ASSERT_EQ(p_ccb->con_state, SDP_STATE_CONNECTED);

  sdp_disconnect(p_ccb, SDP_SUCCESS);
  sdp_cb.reg_info.pL2CA_DisconnectCfm_Cb(p_ccb->connection_id, 0);

  ASSERT_EQ(p_ccb->con_state, SDP_STATE_IDLE);
}

tCONN_CB* find_ccb(uint16_t cid, uint8_t state) {
  uint16_t xx;
  tCONN_CB* p_ccb;

  // Look through each connection control block
  for (xx = 0, p_ccb = sdp_cb.ccb; xx < SDP_MAX_CONNECTIONS; xx++, p_ccb++) {
    if ((p_ccb->con_state == state) && (p_ccb->connection_id == cid)) {
      return p_ccb;
    }
  }
  return nullptr;  // not found
}

TEST_F(StackSdpMainTest, sdp_service_search_request_queuing) {
  ASSERT_TRUE(SDP_ServiceSearchRequest(addr, sdp_db, nullptr));
  const int cid = L2CA_ConnectReq2_cid;
  tCONN_CB* p_ccb1 = find_ccb(cid, SDP_STATE_CONN_SETUP);
  ASSERT_NE(p_ccb1, nullptr);
  ASSERT_EQ(p_ccb1->con_state, SDP_STATE_CONN_SETUP);

  ASSERT_TRUE(SDP_ServiceSearchRequest(addr, sdp_db, nullptr));
  tCONN_CB* p_ccb2 = find_ccb(cid, SDP_STATE_CONN_PEND);
  ASSERT_NE(p_ccb2, nullptr);
  ASSERT_NE(p_ccb2, p_ccb1);
  ASSERT_EQ(p_ccb2->con_state, SDP_STATE_CONN_PEND);

  tL2CAP_CFG_INFO cfg;
  sdp_cb.reg_info.pL2CA_ConfigCfm_Cb(p_ccb1->connection_id, 0, &cfg);

  ASSERT_EQ(p_ccb1->con_state, SDP_STATE_CONNECTED);
  ASSERT_EQ(p_ccb2->con_state, SDP_STATE_CONN_PEND);

  p_ccb1->disconnect_reason = SDP_SUCCESS;
  sdp_disconnect(p_ccb1, SDP_SUCCESS);

  ASSERT_EQ(p_ccb1->con_state, SDP_STATE_IDLE);
  ASSERT_EQ(p_ccb2->con_state, SDP_STATE_CONNECTED);

  sdp_disconnect(p_ccb2, SDP_SUCCESS);
  sdp_cb.reg_info.pL2CA_DisconnectCfm_Cb(p_ccb2->connection_id, 0);

  ASSERT_EQ(p_ccb1->con_state, SDP_STATE_IDLE);
  ASSERT_EQ(p_ccb2->con_state, SDP_STATE_IDLE);
}

void sdp_callback(tSDP_RESULT result) {
  if (result == SDP_SUCCESS) {
    ASSERT_TRUE(SDP_ServiceSearchRequest(addr, sdp_db, nullptr));
  }
}

TEST_F(StackSdpMainTest, sdp_service_search_request_queuing_race_condition) {
  // start first request
  ASSERT_TRUE(SDP_ServiceSearchRequest(addr, sdp_db, sdp_callback));
  const int cid1 = L2CA_ConnectReq2_cid;
  tCONN_CB* p_ccb1 = find_ccb(cid1, SDP_STATE_CONN_SETUP);
  ASSERT_NE(p_ccb1, nullptr);
  ASSERT_EQ(p_ccb1->con_state, SDP_STATE_CONN_SETUP);

  tL2CAP_CFG_INFO cfg;
  sdp_cb.reg_info.pL2CA_ConfigCfm_Cb(p_ccb1->connection_id, 0, &cfg);

  ASSERT_EQ(p_ccb1->con_state, SDP_STATE_CONNECTED);

  sdp_disconnect(p_ccb1, SDP_SUCCESS);
  sdp_cb.reg_info.pL2CA_DisconnectCfm_Cb(p_ccb1->connection_id, 0);

  const int cid2 = L2CA_ConnectReq2_cid;
  ASSERT_NE(cid1, cid2);  // The callback a queued a new request
  tCONN_CB* p_ccb2 = find_ccb(cid2, SDP_STATE_CONN_SETUP);
  ASSERT_NE(p_ccb2, nullptr);
  // If race condition, this will be stuck in PEND
  ASSERT_EQ(p_ccb2->con_state, SDP_STATE_CONN_SETUP);

  sdp_disconnect(p_ccb2, SDP_SUCCESS);
}

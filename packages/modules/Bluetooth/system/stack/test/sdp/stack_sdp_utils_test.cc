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
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstddef>

#include "bt_types.h"
#include "device/include/interop.h"
#include "mock_btif_config.h"
#include "stack/include/avrc_defs.h"
#include "stack/include/sdp_api.h"
#include "stack/sdp/sdpint.h"
#include "test/mock/mock_btif_config.h"
#include "test/mock/mock_osi_properties.h"

using testing::_;
using testing::DoAll;
using testing::Return;
using testing::SetArrayArgument;

// Global trace level referred in the code under test
uint8_t appl_trace_level = BT_TRACE_LEVEL_VERBOSE;

extern "C" void LogMsg(uint32_t trace_set_mask, const char* fmt_str, ...) {}

namespace {
// convenience mock
class IopMock {
 public:
  MOCK_METHOD2(InteropMatchAddr,
               bool(const interop_feature_t, const RawAddress*));
};

std::unique_ptr<IopMock> localIopMock;
}  // namespace

bool interop_match_addr(const interop_feature_t feature,
                        const RawAddress* addr) {
  return localIopMock->InteropMatchAddr(feature, addr);
}

uint8_t avrc_value[8] = {
    ((DATA_ELE_SEQ_DESC_TYPE << 3) | SIZE_IN_NEXT_BYTE),  // data_element
    6,                                                    // data_len
    ((UUID_DESC_TYPE << 3) | SIZE_TWO_BYTES),             // uuid_element
    0,                                                    // uuid
    0,                                                    // uuid
    ((UINT_DESC_TYPE << 3) | SIZE_TWO_BYTES),             // version_element
    0,                                                    // version
    0                                                     // version
};
tSDP_ATTRIBUTE avrcp_attr = {
    .len = 0,
    .value_ptr = (uint8_t*)(&avrc_value),
    .id = 0,
    .type = 0,
};

void set_avrcp_attr(uint32_t len, uint16_t id, uint16_t uuid,
                    uint16_t version) {
  UINT16_TO_BE_FIELD(avrc_value + 3, uuid);
  UINT16_TO_BE_FIELD(avrc_value + 6, version);
  avrcp_attr.len = len;
  avrcp_attr.id = id;
}

uint16_t get_avrc_target_version(tSDP_ATTRIBUTE* p_attr) {
  uint8_t* p_version = p_attr->value_ptr + 6;
  uint16_t version =
      (((uint16_t)(*(p_version))) << 8) + ((uint16_t)(*((p_version) + 1)));
  return version;
}

class StackSdpUtilsTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test::mock::btif_config::btif_config_get_bin.body =
        [this](const std::string& section, const std::string& key,
               uint8_t* value, size_t* length) {
          return btif_config_interface_.GetBin(section, key, value, length);
        };
    test::mock::btif_config::btif_config_get_bin_length.body =
        [this](const std::string& section, const std::string& key) {
          return btif_config_interface_.GetBinLength(section, key);
        };
    test::mock::osi_properties::osi_property_get_bool.body =
        [](const char* key, bool default_value) { return true; };

    localIopMock = std::make_unique<IopMock>();
    set_avrcp_attr(8, ATTR_ID_BT_PROFILE_DESC_LIST,
                   UUID_SERVCLASS_AV_REMOTE_CONTROL, AVRC_REV_1_5);
  }

  void TearDown() override {
    test::mock::btif_config::btif_config_get_bin_length = {};
    test::mock::btif_config::btif_config_get_bin = {};
    test::mock::osi_properties::osi_property_get_bool = {};

    localIopMock.reset();
  }
  bluetooth::manager::MockBtifConfigInterface btif_config_interface_;
};

TEST_F(StackSdpUtilsTest,
       sdpu_set_avrc_target_version_device_in_iop_table_versoin_1_4) {
  RawAddress bdaddr;
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(true));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_4);
}

TEST_F(StackSdpUtilsTest,
       sdpu_set_avrc_target_version_device_in_iop_table_versoin_1_3) {
  RawAddress bdaddr;
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(true));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_3);
}

TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_wrong_len) {
  RawAddress bdaddr;
  set_avrcp_attr(5, ATTR_ID_BT_PROFILE_DESC_LIST,
                 UUID_SERVCLASS_AV_REMOTE_CONTROL, AVRC_REV_1_5);
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_wrong_attribute_id) {
  RawAddress bdaddr;
  set_avrcp_attr(8, ATTR_ID_SERVICE_CLASS_ID_LIST,
                 UUID_SERVCLASS_AV_REMOTE_CONTROL, AVRC_REV_1_5);
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_wrong_uuid) {
  RawAddress bdaddr;
  set_avrcp_attr(8, ATTR_ID_BT_PROFILE_DESC_LIST, UUID_SERVCLASS_AUDIO_SOURCE,
                 AVRC_REV_1_5);
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// device's controller version older than our target version
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_device_older_version) {
  RawAddress bdaddr;
  uint8_t config_0104[2] = {0x04, 0x01};
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(DoAll(SetArrayArgument<2>(config_0104, config_0104 + 2),
                      Return(true)));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_4);
}

// device's controller version same as our target version
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_device_same_version) {
  RawAddress bdaddr;
  uint8_t config_0105[2] = {0x05, 0x01};
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(DoAll(SetArrayArgument<2>(config_0105, config_0105 + 2),
                      Return(true)));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// device's controller version higher than our target version
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_device_newer_version) {
  RawAddress bdaddr;
  uint8_t config_0106[2] = {0x06, 0x01};
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(DoAll(SetArrayArgument<2>(config_0106, config_0106 + 2),
                      Return(true)));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// cannot read device's controller version from bt_config
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_no_config_value) {
  RawAddress bdaddr;
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(0));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// read device's controller version from bt_config return only 1 byte
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_config_value_1_byte) {
  RawAddress bdaddr;
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(1));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// read device's controller version from bt_config return 3 bytes
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_config_value_3_bytes) {
  RawAddress bdaddr;
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(3));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// cached controller version is not valid
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_config_value_not_valid) {
  RawAddress bdaddr;
  uint8_t config_not_valid[2] = {0x12, 0x34};
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(
          DoAll(SetArrayArgument<2>(config_not_valid, config_not_valid + 2),
                Return(true)));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

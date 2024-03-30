/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "bind_helpers.h"
#include "bta_csis_api.h"
#include "bta_dm_api_mock.h"
#include "bta_gatt_api_mock.h"
#include "bta_gatt_queue_mock.h"
#include "btif_storage.h"
#include "btm_api_mock.h"
#include "csis_types.h"
#include "gatt/database_builder.h"
#include "hardware/bt_gatt_types.h"

std::map<std::string, int> mock_function_count_map;

namespace bluetooth {
namespace csis {
namespace internal {
namespace {

using base::Bind;
using base::Closure;
using base::Unretained;

using bluetooth::csis::ConnectionState;
using bluetooth::csis::CsisClient;
using bluetooth::csis::CsisClientCallbacks;
using bluetooth::csis::CsisClientInterface;
using bluetooth::csis::CsisGroupLockStatus;
using bluetooth::groups::DeviceGroups;

using testing::_;
using testing::DoAll;
using testing::DoDefault;
using testing::Invoke;
using testing::Mock;
using testing::NotNull;
using testing::Return;
using testing::SaveArg;
using testing::SetArgPointee;
using testing::WithArg;

// Disables most likely false-positives from base::SplitString()
extern "C" const char* __asan_default_options() {
  return "detect_container_overflow=0";
}

RawAddress GetTestAddress(int index) {
  CHECK_LT(index, UINT8_MAX);
  RawAddress result = {
      {0xC0, 0xDE, 0xC0, 0xDE, 0x00, static_cast<uint8_t>(index)}};
  return result;
}

/* Csis lock callback */
class MockCsisLockCallback {
 public:
  MockCsisLockCallback() = default;
  MockCsisLockCallback(const MockCsisLockCallback&) = delete;
  MockCsisLockCallback& operator=(const MockCsisLockCallback&) = delete;

  ~MockCsisLockCallback() = default;
  MOCK_METHOD((void), CsisGroupLockCb,
              (int group_id, bool locked, CsisGroupLockStatus status));
};

static MockCsisLockCallback* csis_lock_callback_mock;

void SetMockCsisLockCallback(MockCsisLockCallback* mock) {
  csis_lock_callback_mock = mock;
}

/* Csis callbacks to JNI */
class MockCsisCallbacks : public CsisClientCallbacks {
 public:
  MockCsisCallbacks() = default;
  MockCsisCallbacks(const MockCsisCallbacks&) = delete;
  MockCsisCallbacks& operator=(const MockCsisCallbacks&) = delete;

  ~MockCsisCallbacks() override = default;

  MOCK_METHOD((void), OnConnectionState,
              (const RawAddress& address, ConnectionState state), (override));
  MOCK_METHOD((void), OnDeviceAvailable,
              (const RawAddress& address, int group_id, int group_size,
               int rank, const bluetooth::Uuid& uuid),
              (override));
  MOCK_METHOD((void), OnSetMemberAvailable,
              (const RawAddress& address, int group_id), (override));
  MOCK_METHOD((void), OnGroupLockChanged,
              (int group_id, bool locked,
               bluetooth::csis::CsisGroupLockStatus status),
              (override));
  MOCK_METHOD((void), OnGattCsisWriteLockRsp,
              (uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
               void* data));
};

class CsisClientTest : public ::testing::Test {
 private:
  void set_sample_database(uint16_t conn_id, bool csis, bool csis_broken,
                           uint8_t rank, uint8_t sirk_msb = 1) {
    gatt::DatabaseBuilder builder;
    builder.AddService(0x0001, 0x0003, Uuid::From16Bit(0x1800), true);
    builder.AddCharacteristic(0x0002, 0x0003, Uuid::From16Bit(0x2a00),
                              GATT_CHAR_PROP_BIT_READ);
    if (csis) {
      builder.AddService(0x0010, 0x0030, kCsisServiceUuid, true);
      builder.AddCharacteristic(
          0x0020, 0x0021, kCsisSirkUuid,
          GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

      builder.AddDescriptor(0x0022,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      builder.AddCharacteristic(
          0x0023, 0x0024, kCsisSizeUuid,
          GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
      builder.AddDescriptor(0x0025,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      builder.AddCharacteristic(0x0026, 0x0027, kCsisLockUuid,
                                GATT_CHAR_PROP_BIT_READ |
                                    GATT_CHAR_PROP_BIT_NOTIFY |
                                    GATT_CHAR_PROP_BIT_WRITE);
      builder.AddDescriptor(0x0028,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      builder.AddCharacteristic(0x0029, 0x0030, kCsisRankUuid,
                                GATT_CHAR_PROP_BIT_READ);
    }
    if (csis_broken) {
      builder.AddCharacteristic(
          0x0020, 0x0021, kCsisSirkUuid,
          GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

      builder.AddDescriptor(0x0022,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    }
    builder.AddService(0x0090, 0x0093,
                       Uuid::From16Bit(UUID_SERVCLASS_GATT_SERVER), true);
    builder.AddCharacteristic(0x0091, 0x0092,
                              Uuid::From16Bit(GATT_UUID_GATT_SRV_CHGD),
                              GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0093,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    services_map[conn_id] = builder.Build().Services();

    ON_CALL(gatt_queue, ReadCharacteristic(conn_id, _, _, _))
        .WillByDefault(
            Invoke([rank, sirk_msb](uint16_t conn_id, uint16_t handle,
                                    GATT_READ_OP_CB cb, void* cb_data) -> void {
              std::vector<uint8_t> value;

              switch (handle) {
                case 0x0003:
                  /* device name */
                  value.resize(20);
                  break;
                case 0x0021:
                  value.assign(17, 1);
                  value[16] = sirk_msb;
                  break;
                case 0x0024:
                  value.resize(1);
                  break;
                case 0x0027:
                  value.resize(1);
                  break;
                case 0x0030:
                  value.resize(1);
                  value.assign(1, rank);
                  break;
                default:
                  ASSERT_TRUE(false);
                  return;
              }

              cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                 cb_data);
            }));
  }

  void set_sample_database_double_csis(uint16_t conn_id, uint8_t rank_1,
                                       uint8_t rank_2, bool broken,
                                       uint8_t sirk1_infill = 1,
                                       uint8_t sirk2_infill = 2) {
    gatt::DatabaseBuilder builder;

    builder.AddService(0x0001, 0x0003, Uuid::From16Bit(0x1800), true);
    builder.AddCharacteristic(0x0002, 0x0003, Uuid::From16Bit(0x2a00),
                              GATT_CHAR_PROP_BIT_READ);
    builder.AddService(0x0010, 0x0026, bluetooth::Uuid::From16Bit(0x1850),
                       true);
    builder.AddIncludedService(0x0011, kCsisServiceUuid, 0x0031, 0x0041);
    builder.AddCharacteristic(
        0x0031, 0x0032, kCsisSirkUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

    builder.AddDescriptor(0x0033,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(
        0x0034, 0x0035, kCsisSizeUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0036,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0037, 0x0038, kCsisLockUuid,
                              GATT_CHAR_PROP_BIT_READ |
                                  GATT_CHAR_PROP_BIT_NOTIFY |
                                  GATT_CHAR_PROP_BIT_WRITE);
    builder.AddDescriptor(0x0039,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0040, 0x0041, kCsisRankUuid,
                              GATT_CHAR_PROP_BIT_READ);

    if (broken) {
      builder.AddCharacteristic(
          0x0020, 0x0021, kCsisSirkUuid,
          GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

      builder.AddDescriptor(0x0022,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    }

    builder.AddService(0x0042, 0x0044, bluetooth::Uuid::From16Bit(0x1860),
                       true);
    builder.AddIncludedService(0x0043, kCsisServiceUuid, 0x0045, 0x0055);

    builder.AddCharacteristic(
        0x0045, 0x0046, kCsisSirkUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);

    builder.AddDescriptor(0x0047,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(
        0x0048, 0x0049, kCsisSizeUuid,
        GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0050,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0051, 0x0052, kCsisLockUuid,
                              GATT_CHAR_PROP_BIT_READ |
                                  GATT_CHAR_PROP_BIT_NOTIFY |
                                  GATT_CHAR_PROP_BIT_WRITE);
    builder.AddDescriptor(0x0053,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    builder.AddCharacteristic(0x0054, 0x0055, kCsisRankUuid,
                              GATT_CHAR_PROP_BIT_READ);

    builder.AddService(0x0090, 0x0093,
                       Uuid::From16Bit(UUID_SERVCLASS_GATT_SERVER), true);
    builder.AddCharacteristic(0x0091, 0x0092,
                              Uuid::From16Bit(GATT_UUID_GATT_SRV_CHGD),
                              GATT_CHAR_PROP_BIT_NOTIFY);
    builder.AddDescriptor(0x0093,
                          Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
    services_map[conn_id] = builder.Build().Services();

    ON_CALL(gatt_queue, ReadCharacteristic(conn_id, _, _, _))
        .WillByDefault(Invoke([sirk1_infill, sirk2_infill, rank_1, rank_2](
                                  uint16_t conn_id, uint16_t handle,
                                  GATT_READ_OP_CB cb, void* cb_data) -> void {
          std::vector<uint8_t> value;

          switch (handle) {
            case 0x0003:
              /* device name */
              value.resize(20);
              break;
            case 0x0032:
              value.resize(17);
              value.assign(17, sirk1_infill);
              value[0] = 1;  // Plain text SIRK
              break;
            case 0x0035:
              value.resize(1);
              value.assign(1, 2);
              break;
            case 0x0038:
              value.resize(1);
              break;
            case 0x0041:
              value.resize(1);
              value.assign(1, rank_1);
              break;
            case 0x0046:
              value.resize(17);
              value.assign(17, sirk2_infill);
              value[0] = 1;  // Plain text SIRK
              break;
            case 0x0049:
              value.resize(1);
              value.assign(1, 2);
              break;
            case 0x0052:
              value.resize(1);
              break;
            case 0x0055:
              value.resize(1);
              value.assign(1, rank_2);
              break;
            default:
              LOG(ERROR) << " Unknown handle? " << +handle;
              ASSERT_TRUE(false);
              return;
          }

          cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
             cb_data);
        }));
  }

 protected:
  void SetUp(void) override {
    mock_function_count_map.clear();
    bluetooth::manager::SetMockBtmInterface(&btm_interface);
    dm::SetMockBtaDmInterface(&dm_interface);
    gatt::SetMockBtaGattInterface(&gatt_interface);
    gatt::SetMockBtaGattQueue(&gatt_queue);
    SetMockCsisLockCallback(&csis_lock_cb);
    callbacks.reset(new MockCsisCallbacks());

    ON_CALL(gatt_interface, GetCharacteristic(_, _))
        .WillByDefault(
            Invoke([&](uint16_t conn_id,
                       uint16_t handle) -> const gatt::Characteristic* {
              std::list<gatt::Service>& services = services_map[conn_id];
              for (auto const& service : services) {
                for (auto const& characteristic : service.characteristics) {
                  if (characteristic.value_handle == handle) {
                    return &characteristic;
                  }
                }
              }

              return nullptr;
            }));

    // default action for GetOwningService function call
    ON_CALL(gatt_interface, GetOwningService(_, _))
        .WillByDefault(Invoke(
            [&](uint16_t conn_id, uint16_t handle) -> const gatt::Service* {
              std::list<gatt::Service>& services = services_map[conn_id];
              for (auto const& service : services) {
                if (service.handle <= handle && service.end_handle >= handle) {
                  return &service;
                }
              }

              return nullptr;
            }));

    // default action for GetServices function call
    ON_CALL(gatt_interface, GetServices(_))
        .WillByDefault(WithArg<0>(
            Invoke([&](uint16_t conn_id) -> std::list<gatt::Service>* {
              return &services_map[conn_id];
            })));

    // default action for RegisterForNotifications function call
    ON_CALL(gatt_interface, RegisterForNotifications(gatt_if, _, _))
        .WillByDefault(Return(GATT_SUCCESS));

    // default action for DeregisterForNotifications function call
    ON_CALL(gatt_interface, DeregisterForNotifications(gatt_if, _, _))
        .WillByDefault(Return(GATT_SUCCESS));

    // default action for WriteDescriptor function call
    ON_CALL(gatt_queue, WriteDescriptor(_, _, _, _, _, _))
        .WillByDefault(
            Invoke([](uint16_t conn_id, uint16_t handle,
                      std::vector<uint8_t> value, tGATT_WRITE_TYPE write_type,
                      GATT_WRITE_OP_CB cb, void* cb_data) -> void {
              if (cb)
                cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                   cb_data);
            }));
  }

  void TearDown(void) override {
    services_map.clear();
    callbacks.reset();
    CsisClient::CleanUp();
    gatt::SetMockBtaGattInterface(nullptr);
    bluetooth::manager::SetMockBtmInterface(nullptr);
  }

  void TestAppRegister(void) {
    BtaAppRegisterCallback app_register_callback;
    EXPECT_CALL(gatt_interface, AppRegister(_, _, _))
        .WillOnce(DoAll(SaveArg<0>(&gatt_callback),
                        SaveArg<1>(&app_register_callback)));
    CsisClient::Initialize(callbacks.get(),
                           Bind(&btif_storage_load_bonded_csis_devices));
    ASSERT_TRUE(gatt_callback);
    ASSERT_TRUE(app_register_callback);
    app_register_callback.Run(gatt_if, GATT_SUCCESS);
    ASSERT_TRUE(CsisClient::IsCsisClientRunning());
  }

  void TestAppUnregister(void) {
    EXPECT_CALL(gatt_interface, AppDeregister(gatt_if));
    CsisClient::CleanUp();
    ASSERT_FALSE(CsisClient::IsCsisClientRunning());
    gatt_callback = nullptr;
  }

  void TestConnect(const RawAddress& address) {
    // by default indicate link as encrypted
    ON_CALL(btm_interface, GetSecurityFlagsByTransport(address, NotNull(), _))
        .WillByDefault(
            DoAll(SetArgPointee<1>(BTM_SEC_FLAG_ENCRYPTED), Return(true)));

    EXPECT_CALL(gatt_interface,
                Open(gatt_if, address, BTM_BLE_DIRECT_CONNECTION, _));
    CsisClient::Get()->Connect(address);
    Mock::VerifyAndClearExpectations(&gatt_interface);
    Mock::VerifyAndClearExpectations(&btm_interface);
  }

  void TestDisconnect(const RawAddress& address, uint16_t conn_id) {
    if (conn_id != GATT_INVALID_CONN_ID) {
      EXPECT_CALL(gatt_interface, Close(conn_id));
      EXPECT_CALL(*callbacks, OnConnectionState(test_address,
                                                ConnectionState::DISCONNECTED));
    } else {
      EXPECT_CALL(gatt_interface, CancelOpen(_, address, _));
    }
    CsisClient::Get()->Disconnect(address);
  }

  void TestAddFromStorage(const RawAddress& address, uint16_t conn_id,
                          std::vector<uint8_t>& storage_buf) {
    EXPECT_CALL(*callbacks,
                OnConnectionState(address, ConnectionState::CONNECTED))
        .Times(1);
    EXPECT_CALL(*callbacks, OnDeviceAvailable(address, _, _, _, _)).Times(1);
    EXPECT_CALL(gatt_interface,
                Open(gatt_if, address, BTM_BLE_BKG_CONNECT_ALLOW_LIST, _))
        .WillOnce(Invoke([this, conn_id](tGATT_IF client_if,
                                         const RawAddress& remote_bda,
                                         bool is_direct, bool opportunistic) {
          InjectConnectedEvent(remote_bda, conn_id);
          GetSearchCompleteEvent(conn_id);
        }));
    CsisClient::AddFromStorage(address, storage_buf, true);
  }

  void InjectConnectedEvent(const RawAddress& address, uint16_t conn_id) {
    tBTA_GATTC_OPEN event_data = {
        .status = GATT_SUCCESS,
        .conn_id = conn_id,
        .client_if = gatt_if,
        .remote_bda = address,
        .transport = GATT_TRANSPORT_LE,
        .mtu = 240,
    };

    gatt_callback(BTA_GATTC_OPEN_EVT, (tBTA_GATTC*)&event_data);
  }

  void InjectDisconnectedEvent(
      const RawAddress& address, uint16_t conn_id,
      tGATT_DISCONN_REASON reason = GATT_CONN_TERMINATE_PEER_USER) {
    tBTA_GATTC_CLOSE event_data = {
        .status = GATT_SUCCESS,
        .conn_id = conn_id,
        .client_if = gatt_if,
        .remote_bda = address,
        .reason = reason,
    };

    gatt_callback(BTA_GATTC_CLOSE_EVT, (tBTA_GATTC*)&event_data);
  }

  void GetSearchCompleteEvent(uint16_t conn_id) {
    tBTA_GATTC_SEARCH_CMPL event_data = {
        .status = GATT_SUCCESS,
        .conn_id = conn_id,
    };

    gatt_callback(BTA_GATTC_SEARCH_CMPL_EVT, (tBTA_GATTC*)&event_data);
  }

  void TestReadCharacteristic(const RawAddress& address, uint16_t conn_id,
                              std::vector<uint16_t> handles) {
    SetSampleDatabaseCsis(conn_id, 1);
    TestAppRegister();
    TestConnect(address);
    InjectConnectedEvent(address, conn_id);

    EXPECT_CALL(gatt_queue, ReadCharacteristic(conn_id, _, _, _))
        .WillRepeatedly(DoDefault());
    for (auto const& handle : handles) {
      EXPECT_CALL(gatt_queue, ReadCharacteristic(conn_id, handle, _, _))
          .WillOnce(DoDefault());
    }

    GetSearchCompleteEvent(conn_id);
    TestAppUnregister();
  }

  void GetDisconnectedEvent(const RawAddress& address, uint16_t conn_id) {
    tBTA_GATTC_CLOSE event_data = {
        .status = GATT_SUCCESS,
        .conn_id = conn_id,
        .client_if = gatt_if,
        .remote_bda = address,
        .reason = GATT_CONN_TERMINATE_PEER_USER,
    };

    gatt_callback(BTA_GATTC_CLOSE_EVT, (tBTA_GATTC*)&event_data);
  }

  void SetEncryptionResult(const RawAddress& address, bool success) {
    ON_CALL(btm_interface, GetSecurityFlagsByTransport(address, NotNull(), _))
        .WillByDefault(DoAll(SetArgPointee<1>(0), Return(true)));
    EXPECT_CALL(btm_interface,
                SetEncryption(address, _, NotNull(), _, BTM_BLE_SEC_ENCRYPT))
        .WillOnce(Invoke(
            [&success](const RawAddress& bd_addr, tBT_TRANSPORT transport,
                       tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
                       tBTM_BLE_SEC_ACT sec_act) -> tBTM_STATUS {
              p_callback(&bd_addr, transport, p_ref_data,
                         success ? BTM_SUCCESS : BTM_FAILED_ON_SECURITY);
              return BTM_SUCCESS;
            }));
  }

  void SetSampleDatabaseCsis(uint16_t conn_id, uint8_t rank,
                             uint8_t sirk_msb = 1) {
    set_sample_database(conn_id, true, false, rank, sirk_msb);
  }
  void SetSampleDatabaseNoCsis(uint16_t conn_id, uint8_t rank) {
    set_sample_database(conn_id, false, false, rank);
  }
  void SetSampleDatabaseCsisBroken(uint16_t conn_id, uint rank) {
    set_sample_database(conn_id, false, true, rank);
  }
  void SetSampleDatabaseDoubleCsis(uint16_t conn_id, uint8_t rank_1,
                                   uint8_t rank_2) {
    set_sample_database_double_csis(conn_id, rank_1, rank_2, false);
  }
  void SetSampleDatabaseDoubleCsisBroken(uint16_t conn_id, uint8_t rank_1,
                                         uint8_t rank_2) {
    set_sample_database_double_csis(conn_id, rank_1, rank_2, true);
  }

  std::unique_ptr<MockCsisCallbacks> callbacks;
  std::unique_ptr<MockCsisCallbacks> lock_callback;
  bluetooth::manager::MockBtmInterface btm_interface;
  dm::MockBtaDmInterface dm_interface;
  gatt::MockBtaGattInterface gatt_interface;
  gatt::MockBtaGattQueue gatt_queue;
  MockCsisLockCallback csis_lock_cb;
  tBTA_GATTC_CBACK* gatt_callback;
  const uint8_t gatt_if = 0xff;
  std::map<uint16_t, std::list<gatt::Service>> services_map;

  const RawAddress test_address = GetTestAddress(0);
  const RawAddress test_address2 = GetTestAddress(1);
};

TEST_F(CsisClientTest, test_get_uninitialized) {
  ASSERT_DEATH(CsisClient::Get(), "");
}

TEST_F(CsisClientTest, test_initialize) {
  CsisClient::Initialize(callbacks.get(), base::DoNothing());
  ASSERT_TRUE(CsisClient::IsCsisClientRunning());
  CsisClient::CleanUp();
}

TEST_F(CsisClientTest, test_initialize_twice) {
  CsisClient::Initialize(callbacks.get(), base::DoNothing());
  CsisClient* csis_p = CsisClient::Get();
  CsisClient::Initialize(callbacks.get(), base::DoNothing());
  ASSERT_EQ(csis_p, CsisClient::Get());
  CsisClient::CleanUp();
}

TEST_F(CsisClientTest, test_cleanup_initialized) {
  CsisClient::Initialize(callbacks.get(), base::DoNothing());
  CsisClient::CleanUp();
  ASSERT_FALSE(CsisClient::IsCsisClientRunning());
}

TEST_F(CsisClientTest, test_cleanup_uninitialized) {
  CsisClient::CleanUp();
  ASSERT_FALSE(CsisClient::IsCsisClientRunning());
}

TEST_F(CsisClientTest, test_app_registration) {
  TestAppRegister();
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_connect) {
  TestAppRegister();
  TestConnect(GetTestAddress(0));
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_disconnect_non_connected) {
  TestAppRegister();
  TestConnect(test_address);
  TestDisconnect(test_address, GATT_INVALID_CONN_ID);
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_disconnect_connected) {
  TestAppRegister();
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  TestDisconnect(test_address, 1);
  InjectDisconnectedEvent(test_address, 1);
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_disconnected) {
  TestAppRegister();
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::DISCONNECTED));
  InjectDisconnectedEvent(test_address, 1);

  TestAppUnregister();
}

TEST_F(CsisClientTest, test_discovery_csis_found) {
  SetSampleDatabaseCsis(1, 1);
  TestAppRegister();
  TestConnect(test_address);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::CONNECTED));
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _, _, _, _));
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  Mock::VerifyAndClearExpectations(callbacks.get());
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_discovery_csis_not_found) {
  SetSampleDatabaseNoCsis(1, 1);
  TestAppRegister();
  TestConnect(test_address);
  EXPECT_CALL(gatt_interface, Close(1));
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  Mock::VerifyAndClearExpectations(callbacks.get());
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_discovery_csis_broken) {
  SetSampleDatabaseCsisBroken(1, 1);
  TestAppRegister();
  TestConnect(test_address);
  EXPECT_CALL(gatt_interface, Close(1));
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  Mock::VerifyAndClearExpectations(callbacks.get());
  TestAppUnregister();
}

class CsisClientCallbackTest : public CsisClientTest {
 protected:
  const RawAddress test_address = GetTestAddress(0);
  uint16_t conn_id = 22;

  void SetUp(void) override {
    CsisClientTest::SetUp();
    SetSampleDatabaseCsis(conn_id, 1);
    TestAppRegister();
    TestConnect(test_address);
    InjectConnectedEvent(test_address, conn_id);
    GetSearchCompleteEvent(conn_id);
  }

  void TearDown(void) override {
    TestAppUnregister();
    CsisClientTest::TearDown();
  }

  void GetNotificationEvent(uint16_t handle, std::vector<uint8_t>& value) {
    tBTA_GATTC_NOTIFY event_data = {
        .conn_id = conn_id,
        .bda = test_address,
        .handle = handle,
        .len = (uint8_t)value.size(),
        .is_notify = true,
    };

    std::copy(value.begin(), value.end(), event_data.value);
    gatt_callback(BTA_GATTC_NOTIF_EVT, (tBTA_GATTC*)&event_data);
  }
};

TEST_F(CsisClientCallbackTest, test_on_group_lock_changed_group_not_found) {
  bool callback_called = false;
  EXPECT_CALL(
      *callbacks,
      OnGroupLockChanged(2, false, CsisGroupLockStatus::FAILED_INVALID_GROUP));
  CsisClient::Get()->LockGroup(
      2, true,
      base::BindOnce(
          [](bool* callback_called, int group_id, bool locked,
             CsisGroupLockStatus status) {
            if ((group_id == 2) &&
                (status == CsisGroupLockStatus::FAILED_INVALID_GROUP))
              *callback_called = true;
          },
          &callback_called));
  ASSERT_TRUE(callback_called);
}

TEST_F(CsisClientTest, test_get_group_id) {
  SetSampleDatabaseCsis(1, 1);
  TestAppRegister();
  TestConnect(test_address);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::CONNECTED));
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _, _, _, _));
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  int group_id = CsisClient::Get()->GetGroupId(test_address);
  ASSERT_TRUE(group_id == 1);
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_is_group_empty) {
  std::list<std::shared_ptr<CsisGroup>> csis_groups_;
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  csis_groups_.push_back(g_1);

  ASSERT_TRUE(g_1->IsEmpty());
}

TEST_F(CsisClientTest, test_add_device_to_group) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  auto d_1 = std::make_shared<CsisDevice>();

  ASSERT_TRUE(g_1->IsEmpty());
  g_1->AddDevice(d_1);
  ASSERT_FALSE(g_1->IsEmpty());
}

TEST_F(CsisClientTest, test_get_set_desired_size) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetDesiredSize(10);
  ASSERT_EQ(g_1->GetDesiredSize(), 10);
}

TEST_F(CsisClientTest, test_is_device_in_the_group) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  auto d_1 = std::make_shared<CsisDevice>();
  g_1->AddDevice(d_1);
  g_1->IsDeviceInTheGroup(d_1);
}

TEST_F(CsisClientTest, test_get_current_size) {
  const RawAddress test_address_1 = GetTestAddress(0);
  const RawAddress test_address_2 = GetTestAddress(1);
  const RawAddress test_address_3 = GetTestAddress(2);
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  auto d_1 = std::make_shared<CsisDevice>(test_address_1, true);
  auto d_2 = std::make_shared<CsisDevice>(test_address_2, true);
  auto d_3 = std::make_shared<CsisDevice>(test_address_3, true);
  g_1->AddDevice(d_1);
  g_1->AddDevice(d_2);
  g_1->AddDevice(d_3);
  ASSERT_EQ(3, g_1->GetCurrentSize());
}

TEST_F(CsisClientTest, test_set_current_lock_state_unset) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_UNSET);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_UNSET);
}

TEST_F(CsisClientTest, test_set_current_lock_state_locked) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_LOCKED);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_LOCKED);
}

TEST_F(CsisClientTest, test_set_current_lock_state_unlocked) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_UNLOCKED);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_UNLOCKED);
}

TEST_F(CsisClientTest, test_set_various_lock_states) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_UNLOCKED);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_UNLOCKED);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_LOCKED);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_LOCKED);
  g_1->SetCurrentLockState(CsisLockState::CSIS_STATE_UNSET);
  ASSERT_EQ(g_1->GetCurrentLockState(), CsisLockState::CSIS_STATE_UNSET);
}

TEST_F(CsisClientTest, test_set_discovery_state_completed) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_COMPLETED);
  ASSERT_EQ(g_1->GetDiscoveryState(),
            CsisDiscoveryState::CSIS_DISCOVERY_COMPLETED);
}

TEST_F(CsisClientTest, test_set_discovery_state_idle) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_IDLE);
  ASSERT_EQ(g_1->GetDiscoveryState(), CsisDiscoveryState::CSIS_DISCOVERY_IDLE);
}

TEST_F(CsisClientTest, test_set_discovery_state_ongoing) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_ONGOING);
  ASSERT_EQ(g_1->GetDiscoveryState(),
            CsisDiscoveryState::CSIS_DISCOVERY_ONGOING);
}

TEST_F(CsisClientTest, test_set_various_discovery_states) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_COMPLETED);
  ASSERT_EQ(g_1->GetDiscoveryState(),
            CsisDiscoveryState::CSIS_DISCOVERY_COMPLETED);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_IDLE);
  ASSERT_EQ(g_1->GetDiscoveryState(), CsisDiscoveryState::CSIS_DISCOVERY_IDLE);
  g_1->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_ONGOING);
  ASSERT_EQ(g_1->GetDiscoveryState(),
            CsisDiscoveryState::CSIS_DISCOVERY_ONGOING);
}

TEST_F(CsisClientTest, test_get_first_last_device) {
  const RawAddress test_address_3 = GetTestAddress(3);
  const RawAddress test_address_4 = GetTestAddress(4);
  const RawAddress test_address_5 = GetTestAddress(5);
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  auto d_1 = std::make_shared<CsisDevice>(test_address_3, true);
  auto d_2 = std::make_shared<CsisDevice>(test_address_4, true);
  auto d_3 = std::make_shared<CsisDevice>(test_address_5, true);
  g_1->AddDevice(d_1);
  g_1->AddDevice(d_2);
  g_1->AddDevice(d_3);
  ASSERT_EQ(g_1->GetLastDevice(), d_3);
  ASSERT_EQ(g_1->GetFirstDevice(), d_1);
}

TEST_F(CsisClientTest, test_get_set_sirk) {
  auto g_1 = std::make_shared<CsisGroup>(666, bluetooth::Uuid::kEmpty);
  Octet16 sirk = {1};
  g_1->SetSirk(sirk);
  ASSERT_EQ(g_1->GetSirk(), sirk);
}

class CsisMultiClientTest : public CsisClientTest {
 protected:
  const RawAddress test_address_1 = GetTestAddress(1);
  const RawAddress test_address_2 = GetTestAddress(2);

  void SetUp(void) override {
    CsisClientTest::SetUp();
    TestAppRegister();
    SetSampleDatabaseDoubleCsis(0x001, 1, 2);
  }
};

class CsisMultiClientTestBroken : public CsisClientTest {
 protected:
  const RawAddress test_address_1 = GetTestAddress(1);
  const RawAddress test_address_2 = GetTestAddress(2);

  void SetUp(void) override {
    CsisClientTest::SetUp();
    TestAppRegister();
    SetSampleDatabaseDoubleCsisBroken(0x001, 1, 2);
  }
};

TEST_F(CsisMultiClientTest, test_add_multiple_instances) {
  TestAppUnregister();
  CsisClientTest::TearDown();
}

TEST_F(CsisMultiClientTest, test_cleanup_multiple_instances) {
  CsisClient::CleanUp();
  CsisClient::IsCsisClientRunning();
}

TEST_F(CsisMultiClientTest, test_connect_multiple_instances) {
  TestConnect(GetTestAddress(0));
  TestAppUnregister();
}

TEST_F(CsisMultiClientTest, test_disconnect_multiple_instances) {
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::DISCONNECTED));
  InjectDisconnectedEvent(test_address, 1);

  TestAppUnregister();
  CsisClientTest::TearDown();
}

TEST_F(CsisMultiClientTest, test_lock_multiple_instances) {
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);

  EXPECT_CALL(*callbacks,
              OnGroupLockChanged(1, true, CsisGroupLockStatus::SUCCESS));
  EXPECT_CALL(*csis_lock_callback_mock,
              CsisGroupLockCb(1, true, CsisGroupLockStatus::SUCCESS));
  ON_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _))
      .WillByDefault(
          Invoke([](uint16_t conn_id, uint16_t handle,
                    std::vector<uint8_t> value, tGATT_WRITE_TYPE write_type,
                    GATT_WRITE_OP_CB cb, void* cb_data) -> void {
            if (cb)
              cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                 cb_data);
          }));
  CsisClient::Get()->LockGroup(
      1, true,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));

  EXPECT_CALL(*callbacks,
              OnGroupLockChanged(2, true, CsisGroupLockStatus::SUCCESS));
  EXPECT_CALL(*csis_lock_callback_mock,
              CsisGroupLockCb(2, true, CsisGroupLockStatus::SUCCESS));
  CsisClient::Get()->LockGroup(
      2, true,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));
}

TEST_F(CsisMultiClientTest, test_unlock_multiple_instances) {
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);

  ON_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _))
      .WillByDefault(
          Invoke([](uint16_t conn_id, uint16_t handle,
                    std::vector<uint8_t> value, tGATT_WRITE_TYPE write_type,
                    GATT_WRITE_OP_CB cb, void* cb_data) -> void {
            if (cb)
              cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                 cb_data);
          }));
  CsisClient::Get()->LockGroup(
      1, true,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));

  EXPECT_CALL(*callbacks,
              OnGroupLockChanged(1, false, CsisGroupLockStatus::SUCCESS));
  EXPECT_CALL(*csis_lock_callback_mock,
              CsisGroupLockCb(1, false, CsisGroupLockStatus::SUCCESS));
  CsisClient::Get()->LockGroup(
      1, false,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));
}

TEST_F(CsisMultiClientTest, test_disconnect_locked_multiple_instances) {
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);

  TestConnect(test_address2);
  InjectConnectedEvent(test_address2, 2);
  GetSearchCompleteEvent(2);

  EXPECT_CALL(*callbacks,
              OnGroupLockChanged(1, true, CsisGroupLockStatus::SUCCESS));
  EXPECT_CALL(*csis_lock_callback_mock,
              CsisGroupLockCb(1, true, CsisGroupLockStatus::SUCCESS));
  ON_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _))
      .WillByDefault(
          Invoke([](uint16_t conn_id, uint16_t handle,
                    std::vector<uint8_t> value, tGATT_WRITE_TYPE write_type,
                    GATT_WRITE_OP_CB cb, void* cb_data) -> void {
            if (cb)
              cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                 cb_data);
          }));
  CsisClient::Get()->LockGroup(
      1, true,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));

  EXPECT_CALL(*callbacks,
              OnGroupLockChanged(
                  1, false, CsisGroupLockStatus::LOCKED_GROUP_MEMBER_LOST));
  InjectDisconnectedEvent(test_address, 2, GATT_CONN_TIMEOUT);
}

TEST_F(CsisMultiClientTest, test_discover_multiple_instances) {
  TestConnect(test_address);
  EXPECT_CALL(*callbacks,
              OnConnectionState(test_address, ConnectionState::CONNECTED))
      .Times(1);
  EXPECT_CALL(*callbacks, OnDeviceAvailable(test_address, _, _, _, _)).Times(2);
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  Mock::VerifyAndClearExpectations(callbacks.get());
  TestAppUnregister();
}

TEST_F(CsisClientTest, test_storage_calls) {
  SetSampleDatabaseCsis(1, 1);

  ASSERT_EQ(0,
            mock_function_count_map["btif_storage_load_bonded_csis_devices"]);
  TestAppRegister();
  ASSERT_EQ(1,
            mock_function_count_map["btif_storage_load_bonded_csis_devices"]);

  ASSERT_EQ(0, mock_function_count_map["btif_storage_update_csis_info"]);
  ASSERT_EQ(0, mock_function_count_map["btif_storage_set_csis_autoconnect"]);
  TestConnect(test_address);
  InjectConnectedEvent(test_address, 1);
  GetSearchCompleteEvent(1);
  ASSERT_EQ(1, mock_function_count_map["btif_storage_set_csis_autoconnect"]);
  ASSERT_EQ(1, mock_function_count_map["btif_storage_update_csis_info"]);

  ASSERT_EQ(0, mock_function_count_map["btif_storage_remove_csis_device"]);
  CsisClient::Get()->RemoveDevice(test_address);
  ASSERT_EQ(1, mock_function_count_map["btif_storage_remove_csis_device"]);

  TestAppUnregister();
}

TEST_F(CsisClientTest, test_storage_content) {
  // Two devices in one set
  SetSampleDatabaseCsis(1, 1);
  SetSampleDatabaseCsis(2, 2);
  // Devices in the other set
  SetSampleDatabaseCsis(3, 1, 2);
  SetSampleDatabaseCsis(4, 1, 2);

  TestAppRegister();
  TestConnect(GetTestAddress(1));
  InjectConnectedEvent(GetTestAddress(1), 1);
  GetSearchCompleteEvent(1);
  ASSERT_EQ(1, CsisClient::Get()->GetGroupId(
                   GetTestAddress(1), bluetooth::Uuid::From16Bit(0x0000)));

  TestConnect(GetTestAddress(2));
  InjectConnectedEvent(GetTestAddress(2), 2);
  GetSearchCompleteEvent(2);
  ASSERT_EQ(1, CsisClient::Get()->GetGroupId(
                   GetTestAddress(2), bluetooth::Uuid::From16Bit(0x0000)));

  TestConnect(GetTestAddress(3));
  InjectConnectedEvent(GetTestAddress(3), 3);
  GetSearchCompleteEvent(3);
  ASSERT_EQ(2, CsisClient::Get()->GetGroupId(
                   GetTestAddress(3), bluetooth::Uuid::From16Bit(0x0000)));

  std::vector<uint8_t> dev1_storage;
  std::vector<uint8_t> dev2_storage;
  std::vector<uint8_t> dev3_storage;

  // Store to byte buffer
  CsisClient::GetForStorage(GetTestAddress(1), dev1_storage);
  CsisClient::GetForStorage(GetTestAddress(2), dev2_storage);
  CsisClient::GetForStorage(GetTestAddress(3), dev3_storage);
  ASSERT_NE(0u, dev1_storage.size());
  ASSERT_NE(0u, dev2_storage.size());
  ASSERT_NE(0u, dev3_storage.size());

  // Clean it up
  TestAppUnregister();

  // Reinitialize service
  TestAppRegister();

  // Restore dev1 from the byte buffer
  TestAddFromStorage(GetTestAddress(1), 1, dev1_storage);
  ASSERT_EQ(1, CsisClient::Get()->GetGroupId(
                   GetTestAddress(1), bluetooth::Uuid::From16Bit(0x0000)));

  // Restore dev2 from the byte buffer
  TestAddFromStorage(GetTestAddress(2), 2, dev2_storage);
  ASSERT_EQ(1, CsisClient::Get()->GetGroupId(
                   GetTestAddress(2), bluetooth::Uuid::From16Bit(0x0000)));

  // Restore dev3 from the byte buffer
  TestAddFromStorage(GetTestAddress(3), 3, dev3_storage);
  ASSERT_EQ(2, CsisClient::Get()->GetGroupId(
                   GetTestAddress(3), bluetooth::Uuid::From16Bit(0x0000)));

  // Restore not inerrogated dev4 - empty buffer but valid sirk for group 2
  std::vector<uint8_t> no_set_info;
  TestAddFromStorage(GetTestAddress(4), 4, no_set_info);
  ASSERT_EQ(2, CsisClient::Get()->GetGroupId(
                   GetTestAddress(4), bluetooth::Uuid::From16Bit(0x0000)));

  TestAppUnregister();
}

TEST_F(CsisClientTest, test_database_out_of_sync) {
  auto test_address = GetTestAddress(0);
  auto conn_id = 1;

  TestAppRegister();
  SetSampleDatabaseCsis(conn_id, 1);
  TestConnect(test_address);
  InjectConnectedEvent(test_address, conn_id);
  GetSearchCompleteEvent(conn_id);
  ASSERT_EQ(1, CsisClient::Get()->GetGroupId(
                   test_address, bluetooth::Uuid::From16Bit(0x0000)));

  // Simulated database changed on the remote side.
  ON_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _))
      .WillByDefault(
          Invoke([this](uint16_t conn_id, uint16_t handle,
                        std::vector<uint8_t> value, tGATT_WRITE_TYPE write_type,
                        GATT_WRITE_OP_CB cb, void* cb_data) {
            auto* svc = gatt::FindService(services_map[conn_id], handle);
            if (svc == nullptr) return;

            tGATT_STATUS status = GATT_DATABASE_OUT_OF_SYNC;
            if (cb)
              cb(conn_id, status, handle, value.size(), value.data(), cb_data);
          }));

  ON_CALL(gatt_interface, ServiceSearchRequest(_, _)).WillByDefault(Return());
  EXPECT_CALL(gatt_interface, ServiceSearchRequest(_, _));
  CsisClient::Get()->LockGroup(
      1, true,
      base::BindOnce([](int group_id, bool locked, CsisGroupLockStatus status) {
        csis_lock_callback_mock->CsisGroupLockCb(group_id, locked, status);
      }));
  TestAppUnregister();
}

}  // namespace
}  // namespace internal
}  // namespace csis
}  // namespace bluetooth

/*
 * Copyright 2019 The Android Open Source Project
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

#include "hci/le_advertising_manager.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <algorithm>
#include <chrono>
#include <future>
#include <map>

#include "common/bind.h"
#include "hci/acl_manager.h"
#include "hci/address.h"
#include "hci/controller.h"
#include "hci/hci_layer_fake.h"
#include "os/thread.h"
#include "packet/raw_builder.h"

namespace bluetooth {
namespace hci {
namespace {

using namespace std::literals;

using packet::RawBuilder;

using testing::_;
using testing::InSequence;

class TestController : public Controller {
 public:
  bool IsSupported(OpCode op_code) const override {
    return supported_opcodes_.count(op_code) == 1;
  }

  void AddSupported(OpCode op_code) {
    supported_opcodes_.insert(op_code);
  }

  uint8_t GetLeNumberOfSupportedAdverisingSets() const override {
    return num_advertisers_;
  }

  uint16_t GetLeMaximumAdvertisingDataLength() const override {
    return 0x0672;
  }

  bool SupportsBleExtendedAdvertising() const override {
    return support_ble_extended_advertising_;
  }

  void SetBleExtendedAdvertisingSupport(bool support) {
    support_ble_extended_advertising_ = support;
  }

  VendorCapabilities GetVendorCapabilities() const override {
    return vendor_capabilities_;
  }

  uint8_t num_advertisers_{0};
  VendorCapabilities vendor_capabilities_;

 protected:
  void Start() override {}
  void Stop() override {}
  void ListDependencies(ModuleList* list) const {}

 private:
  std::set<OpCode> supported_opcodes_{};
  bool support_ble_extended_advertising_ = false;
};

class TestLeAddressManager : public LeAddressManager {
 public:
  TestLeAddressManager(
      common::Callback<void(std::unique_ptr<CommandBuilder>)> enqueue_command,
      os::Handler* handler,
      Address public_address,
      uint8_t connect_list_size,
      uint8_t resolving_list_size)
      : LeAddressManager(enqueue_command, handler, public_address, connect_list_size, resolving_list_size) {}

  AddressPolicy Register(LeAddressManagerCallback* callback) override {
    client_ = callback;
    test_client_state_ = RESUMED;
    return AddressPolicy::USE_STATIC_ADDRESS;
  }

  void Unregister(LeAddressManagerCallback* callback) override {
    if (!ignore_unregister_for_testing) {
      client_ = nullptr;
    }
    test_client_state_ = UNREGISTERED;
  }

  void AckPause(LeAddressManagerCallback* callback) override {
    test_client_state_ = PAUSED;
  }

  void AckResume(LeAddressManagerCallback* callback) override {
    test_client_state_ = RESUMED;
  }

  AddressPolicy GetAddressPolicy() override {
    return address_policy_;
  }

  void SetAddressPolicy(AddressPolicy address_policy) {
    address_policy_ = address_policy;
  }

  AddressWithType GetAnotherAddress() override {
    hci::Address address;
    Address::FromString("05:04:03:02:01:00", address);
    auto random_address = AddressWithType(address, AddressType::RANDOM_DEVICE_ADDRESS);
    return random_address;
  }

  AddressWithType GetCurrentAddress() override {
    hci::Address address;
    Address::FromString("05:04:03:02:01:00", address);
    auto random_address = AddressWithType(address, AddressType::RANDOM_DEVICE_ADDRESS);
    return random_address;
  }

  AddressPolicy address_policy_ = AddressPolicy::USE_STATIC_ADDRESS;
  LeAddressManagerCallback* client_;
  bool ignore_unregister_for_testing = false;
  enum TestClientState {
    UNREGISTERED,
    PAUSED,
    RESUMED,
  };
  TestClientState test_client_state_ = UNREGISTERED;
};

class TestAclManager : public AclManager {
 public:
  LeAddressManager* GetLeAddressManager() override {
    return test_le_address_manager_;
  }

  void SetAddressPolicy(LeAddressManager::AddressPolicy address_policy) {
    test_le_address_manager_->SetAddressPolicy(address_policy);
  }

 protected:
  void Start() override {
    thread_ = new os::Thread("thread", os::Thread::Priority::NORMAL);
    handler_ = new os::Handler(thread_);
    Address address({0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
    test_le_address_manager_ = new TestLeAddressManager(
        common::Bind(&TestAclManager::enqueue_command, common::Unretained(this)), handler_, address, 0x3F, 0x3F);
  }

  void Stop() override {
    delete test_le_address_manager_;
    handler_->Clear();
    delete handler_;
    delete thread_;
  }

  void ListDependencies(ModuleList* list) const {}

  void SetRandomAddress(Address address) {}

  void enqueue_command(std::unique_ptr<CommandBuilder> command_packet){};

  os::Thread* thread_;
  os::Handler* handler_;
  TestLeAddressManager* test_le_address_manager_;
};

class LeAdvertisingManagerTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_hci_layer_ = new TestHciLayer;  // Ownership is transferred to registry
    test_controller_ = new TestController;
    test_acl_manager_ = new TestAclManager;
    test_controller_->AddSupported(param_opcode_);
    fake_registry_.InjectTestModule(&HciLayer::Factory, test_hci_layer_);
    fake_registry_.InjectTestModule(&Controller::Factory, test_controller_);
    fake_registry_.InjectTestModule(&AclManager::Factory, test_acl_manager_);
    client_handler_ = fake_registry_.GetTestModuleHandler(&HciLayer::Factory);
    ASSERT_NE(client_handler_, nullptr);
    test_controller_->num_advertisers_ = num_instances_;
    test_controller_->vendor_capabilities_.max_advt_instances_ = num_instances_;
    test_controller_->SetBleExtendedAdvertisingSupport(support_ble_extended_advertising_);
    le_advertising_manager_ = fake_registry_.Start<LeAdvertisingManager>(&thread_);
    le_advertising_manager_->RegisterAdvertisingCallback(&mock_advertising_callback_);
  }

  void TearDown() override {
    sync_client_handler();
    fake_registry_.SynchronizeModuleHandler(&LeAdvertisingManager::Factory, std::chrono::milliseconds(20));
    fake_registry_.StopAll();
  }

  TestModuleRegistry fake_registry_;
  TestHciLayer* test_hci_layer_ = nullptr;
  TestController* test_controller_ = nullptr;
  TestAclManager* test_acl_manager_ = nullptr;
  os::Thread& thread_ = fake_registry_.GetTestThread();
  LeAdvertisingManager* le_advertising_manager_ = nullptr;
  os::Handler* client_handler_ = nullptr;
  OpCode param_opcode_{OpCode::LE_SET_ADVERTISING_PARAMETERS};
  uint8_t num_instances_ = 8;
  bool support_ble_extended_advertising_ = false;

  const common::Callback<void(Address, AddressType)> scan_callback =
      common::Bind(&LeAdvertisingManagerTest::on_scan, common::Unretained(this));
  const common::Callback<void(ErrorCode, uint8_t, uint8_t)> set_terminated_callback =
      common::Bind(&LeAdvertisingManagerTest::on_set_terminated, common::Unretained(this));

  void on_scan(Address address, AddressType address_type) {}

  void on_set_terminated(ErrorCode error_code, uint8_t, uint8_t) {}

  void sync_client_handler() {
    ASSERT(thread_.GetReactor()->WaitForIdle(2s));
  }

  class MockAdvertisingCallback : public AdvertisingCallback {
   public:
    MOCK_METHOD4(
        OnAdvertisingSetStarted, void(int reg_id, uint8_t advertiser_id, int8_t tx_power, AdvertisingStatus status));
    MOCK_METHOD3(OnAdvertisingEnabled, void(uint8_t advertiser_id, bool enable, uint8_t status));
    MOCK_METHOD2(OnAdvertisingDataSet, void(uint8_t advertiser_id, uint8_t status));
    MOCK_METHOD2(OnScanResponseDataSet, void(uint8_t advertiser_id, uint8_t status));
    MOCK_METHOD3(OnAdvertisingParametersUpdated, void(uint8_t advertiser_id, int8_t tx_power, uint8_t status));
    MOCK_METHOD2(OnPeriodicAdvertisingParametersUpdated, void(uint8_t advertiser_id, uint8_t status));
    MOCK_METHOD2(OnPeriodicAdvertisingDataSet, void(uint8_t advertiser_id, uint8_t status));
    MOCK_METHOD3(OnPeriodicAdvertisingEnabled, void(uint8_t advertiser_id, bool enable, uint8_t status));
    MOCK_METHOD3(OnOwnAddressRead, void(uint8_t advertiser_id, uint8_t address_type, Address address));
  } mock_advertising_callback_;
};

class LeAdvertisingAPITest : public LeAdvertisingManagerTest {
 protected:
  void SetUp() override {
    LeAdvertisingManagerTest::SetUp();

    // start advertising set
    ExtendedAdvertisingConfig advertising_config{};
    advertising_config.advertising_type = AdvertisingType::ADV_IND;
    advertising_config.own_address_type = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
    std::vector<GapData> gap_data{};
    GapData data_item{};
    data_item.data_type_ = GapDataType::FLAGS;
    data_item.data_ = {0x34};
    gap_data.push_back(data_item);
    data_item.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
    data_item.data_ = {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
    gap_data.push_back(data_item);
    advertising_config.advertisement = gap_data;
    advertising_config.scan_response = gap_data;
    advertising_config.channel_map = 1;

    advertiser_id_ = le_advertising_manager_->ExtendedCreateAdvertiser(
        0x00, advertising_config, scan_callback, set_terminated_callback, 0, 0, client_handler_);
    ASSERT_NE(LeAdvertisingManager::kInvalidId, advertiser_id_);
    std::vector<OpCode> adv_opcodes = {
        OpCode::LE_READ_ADVERTISING_PHYSICAL_CHANNEL_TX_POWER,
        OpCode::LE_SET_ADVERTISING_PARAMETERS,
        OpCode::LE_SET_SCAN_RESPONSE_DATA,
        OpCode::LE_SET_ADVERTISING_DATA,
        OpCode::LE_SET_ADVERTISING_ENABLE,
    };
    EXPECT_CALL(
        mock_advertising_callback_,
        OnAdvertisingSetStarted(0x00, advertiser_id_, 0x00, AdvertisingCallback::AdvertisingStatus::SUCCESS));
    std::vector<uint8_t> success_vector{static_cast<uint8_t>(ErrorCode::SUCCESS)};
    for (size_t i = 0; i < adv_opcodes.size(); i++) {
      ASSERT_EQ(adv_opcodes[i], test_hci_layer_->GetCommand().GetOpCode());
      if (adv_opcodes[i] == OpCode::LE_READ_ADVERTISING_PHYSICAL_CHANNEL_TX_POWER) {
        test_hci_layer_->IncomingEvent(
            LeReadAdvertisingPhysicalChannelTxPowerCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, 0x00));
      } else {
        test_hci_layer_->IncomingEvent(
            CommandCompleteBuilder::Create(uint8_t{1}, adv_opcodes[i], std::make_unique<RawBuilder>(success_vector)));
      }
    }
  }

  AdvertiserId advertiser_id_;
};

class LeAndroidHciAdvertisingManagerTest : public LeAdvertisingManagerTest {
 protected:
  void SetUp() override {
    param_opcode_ = OpCode::LE_MULTI_ADVT;
    LeAdvertisingManagerTest::SetUp();
  }
};

class LeAndroidHciAdvertisingAPITest : public LeAndroidHciAdvertisingManagerTest {
 protected:
  void SetUp() override {
    LeAndroidHciAdvertisingManagerTest::SetUp();

    ExtendedAdvertisingConfig advertising_config{};
    advertising_config.advertising_type = AdvertisingType::ADV_IND;
    advertising_config.own_address_type = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
    std::vector<GapData> gap_data{};
    GapData data_item{};
    data_item.data_type_ = GapDataType::FLAGS;
    data_item.data_ = {0x34};
    gap_data.push_back(data_item);
    data_item.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
    data_item.data_ = {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
    gap_data.push_back(data_item);
    advertising_config.advertisement = gap_data;
    advertising_config.scan_response = gap_data;
    advertising_config.channel_map = 1;

    advertiser_id_ = le_advertising_manager_->ExtendedCreateAdvertiser(
        0x00, advertising_config, scan_callback, set_terminated_callback, 0, 0, client_handler_);
    ASSERT_NE(LeAdvertisingManager::kInvalidId, advertiser_id_);
    std::vector<SubOcf> sub_ocf = {
        SubOcf::SET_PARAM,
        SubOcf::SET_SCAN_RESP,
        SubOcf::SET_DATA,
        SubOcf::SET_RANDOM_ADDR,
        SubOcf::SET_ENABLE,
    };
    EXPECT_CALL(
        mock_advertising_callback_,
        OnAdvertisingSetStarted(0, advertiser_id_, 0, AdvertisingCallback::AdvertisingStatus::SUCCESS));
    for (size_t i = 0; i < sub_ocf.size(); i++) {
      auto packet = test_hci_layer_->GetCommand();
      auto sub_packet = LeMultiAdvtView::Create(LeAdvertisingCommandView::Create(packet));
      ASSERT_TRUE(sub_packet.IsValid());
      ASSERT_EQ(sub_packet.GetSubCmd(), sub_ocf[i]);
      test_hci_layer_->IncomingEvent(LeMultiAdvtCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, sub_ocf[i]));
    }
  }

  AdvertiserId advertiser_id_;
};

class LeAndroidHciAdvertisingAPIPublicAddressTest : public LeAndroidHciAdvertisingManagerTest {
 protected:
  void SetUp() override {
    LeAndroidHciAdvertisingManagerTest::SetUp();

    ExtendedAdvertisingConfig advertising_config{};
    advertising_config.advertising_type = AdvertisingType::ADV_IND;
    advertising_config.own_address_type = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
    std::vector<GapData> gap_data{};
    GapData data_item{};
    data_item.data_type_ = GapDataType::FLAGS;
    data_item.data_ = {0x34};
    gap_data.push_back(data_item);
    data_item.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
    data_item.data_ = {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
    gap_data.push_back(data_item);
    advertising_config.advertisement = gap_data;
    advertising_config.scan_response = gap_data;
    advertising_config.channel_map = 1;

    test_acl_manager_->SetAddressPolicy(LeAddressManager::AddressPolicy::USE_PUBLIC_ADDRESS);
    advertiser_id_ = le_advertising_manager_->ExtendedCreateAdvertiser(
        0x00, advertising_config, scan_callback, set_terminated_callback, 0, 0, client_handler_);
    ASSERT_NE(LeAdvertisingManager::kInvalidId, advertiser_id_);
    std::vector<SubOcf> sub_ocf = {
        SubOcf::SET_PARAM,
        SubOcf::SET_SCAN_RESP,
        SubOcf::SET_DATA,
        SubOcf::SET_ENABLE,
    };
    EXPECT_CALL(
        mock_advertising_callback_,
        OnAdvertisingSetStarted(0, advertiser_id_, 0, AdvertisingCallback::AdvertisingStatus::SUCCESS));
    for (size_t i = 0; i < sub_ocf.size(); i++) {
      auto packet = test_hci_layer_->GetCommand();
      auto sub_packet = LeMultiAdvtView::Create(LeAdvertisingCommandView::Create(packet));
      ASSERT_TRUE(sub_packet.IsValid());
      ASSERT_EQ(sub_packet.GetSubCmd(), sub_ocf[i]);
      test_hci_layer_->IncomingEvent(LeMultiAdvtCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, sub_ocf[i]));
    }
  }

  AdvertiserId advertiser_id_;
};

class LeExtendedAdvertisingManagerTest : public LeAdvertisingManagerTest {
 protected:
  void SetUp() override {
    support_ble_extended_advertising_ = true;
    param_opcode_ = OpCode::LE_SET_EXTENDED_ADVERTISING_PARAMETERS;
    LeAdvertisingManagerTest::SetUp();
  }
};

class LeExtendedAdvertisingAPITest : public LeExtendedAdvertisingManagerTest {
 protected:
  void SetUp() override {
    LeExtendedAdvertisingManagerTest::SetUp();

    // start advertising set
    ExtendedAdvertisingConfig advertising_config{};
    advertising_config.advertising_type = AdvertisingType::ADV_IND;
    advertising_config.own_address_type = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
    std::vector<GapData> gap_data{};
    GapData data_item{};
    data_item.data_type_ = GapDataType::FLAGS;
    data_item.data_ = {0x34};
    gap_data.push_back(data_item);
    data_item.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
    data_item.data_ = {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
    gap_data.push_back(data_item);
    advertising_config.advertisement = gap_data;
    advertising_config.scan_response = gap_data;
    advertising_config.channel_map = 1;
    advertising_config.sid = 0x01;

    advertiser_id_ = le_advertising_manager_->ExtendedCreateAdvertiser(
        0x00, advertising_config, scan_callback, set_terminated_callback, 0, 0, client_handler_);
    ASSERT_NE(LeAdvertisingManager::kInvalidId, advertiser_id_);
    EXPECT_CALL(
        mock_advertising_callback_,
        OnAdvertisingSetStarted(0x00, advertiser_id_, -23, AdvertisingCallback::AdvertisingStatus::SUCCESS));
    std::vector<OpCode> adv_opcodes = {
        OpCode::LE_SET_EXTENDED_ADVERTISING_PARAMETERS,
        OpCode::LE_SET_EXTENDED_SCAN_RESPONSE_DATA,
        OpCode::LE_SET_EXTENDED_ADVERTISING_DATA,
        OpCode::LE_SET_EXTENDED_ADVERTISING_ENABLE,
    };
    std::vector<uint8_t> success_vector{static_cast<uint8_t>(ErrorCode::SUCCESS)};
    for (size_t i = 0; i < adv_opcodes.size(); i++) {
      ASSERT_EQ(adv_opcodes[i], test_hci_layer_->GetCommand().GetOpCode());
      if (adv_opcodes[i] == OpCode::LE_SET_EXTENDED_ADVERTISING_PARAMETERS) {
        test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingParametersCompleteBuilder::Create(
            uint8_t{1}, ErrorCode::SUCCESS, static_cast<uint8_t>(-23)));
      } else {
        test_hci_layer_->IncomingEvent(
            CommandCompleteBuilder::Create(uint8_t{1}, adv_opcodes[i], std::make_unique<RawBuilder>(success_vector)));
      }
    }
    sync_client_handler();
  }

  AdvertiserId advertiser_id_;
};

TEST_F(LeAdvertisingManagerTest, startup_teardown) {}

TEST_F(LeAndroidHciAdvertisingManagerTest, startup_teardown) {}

TEST_F(LeExtendedAdvertisingManagerTest, startup_teardown) {}

TEST_F(LeAdvertisingManagerTest, create_advertiser_test) {
  ExtendedAdvertisingConfig advertising_config{};
  advertising_config.advertising_type = AdvertisingType::ADV_IND;
  advertising_config.own_address_type = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
  std::vector<GapData> gap_data{};
  GapData data_item{};
  data_item.data_type_ = GapDataType::FLAGS;
  data_item.data_ = {0x34};
  gap_data.push_back(data_item);
  data_item.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  data_item.data_ = {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
  gap_data.push_back(data_item);
  advertising_config.advertisement = gap_data;
  advertising_config.scan_response = gap_data;
  advertising_config.channel_map = 1;

  auto id = le_advertising_manager_->ExtendedCreateAdvertiser(
      0x00, advertising_config, scan_callback, set_terminated_callback, 0, 0, client_handler_);
  ASSERT_NE(LeAdvertisingManager::kInvalidId, id);
  std::vector<OpCode> adv_opcodes = {
      OpCode::LE_READ_ADVERTISING_PHYSICAL_CHANNEL_TX_POWER,
      OpCode::LE_SET_ADVERTISING_PARAMETERS,
      OpCode::LE_SET_SCAN_RESPONSE_DATA,
      OpCode::LE_SET_ADVERTISING_DATA,
      OpCode::LE_SET_ADVERTISING_ENABLE,
  };
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingSetStarted(0x00, id, 0x00, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  std::vector<uint8_t> success_vector{static_cast<uint8_t>(ErrorCode::SUCCESS)};
  for (size_t i = 0; i < adv_opcodes.size(); i++) {
    ASSERT_EQ(adv_opcodes[i], test_hci_layer_->GetCommand().GetOpCode());
    if (adv_opcodes[i] == OpCode::LE_READ_ADVERTISING_PHYSICAL_CHANNEL_TX_POWER) {
      test_hci_layer_->IncomingEvent(
          LeReadAdvertisingPhysicalChannelTxPowerCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, 0x00));
    } else {
      test_hci_layer_->IncomingEvent(
          CommandCompleteBuilder::Create(uint8_t{1}, adv_opcodes[i], std::make_unique<RawBuilder>(success_vector)));
    }
  }

  // Disable the advertiser
  le_advertising_manager_->RemoveAdvertiser(id);
  ASSERT_EQ(OpCode::LE_SET_ADVERTISING_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
}

TEST_F(LeAndroidHciAdvertisingManagerTest, create_advertiser_test) {
  ExtendedAdvertisingConfig advertising_config{};
  advertising_config.advertising_type = AdvertisingType::ADV_IND;
  advertising_config.own_address_type = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
  std::vector<GapData> gap_data{};
  GapData data_item{};
  data_item.data_type_ = GapDataType::FLAGS;
  data_item.data_ = {0x34};
  gap_data.push_back(data_item);
  data_item.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  data_item.data_ = {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
  gap_data.push_back(data_item);
  advertising_config.advertisement = gap_data;
  advertising_config.scan_response = gap_data;
  advertising_config.channel_map = 1;

  auto id = le_advertising_manager_->ExtendedCreateAdvertiser(
      0x00, advertising_config, scan_callback, set_terminated_callback, 0, 0, client_handler_);
  ASSERT_NE(LeAdvertisingManager::kInvalidId, id);
  std::vector<SubOcf> sub_ocf = {
      SubOcf::SET_PARAM,
      SubOcf::SET_SCAN_RESP,
      SubOcf::SET_DATA,
      SubOcf::SET_RANDOM_ADDR,
      SubOcf::SET_ENABLE,
  };
  EXPECT_CALL(
      mock_advertising_callback_, OnAdvertisingSetStarted(0, id, 0, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  for (size_t i = 0; i < sub_ocf.size(); i++) {
    auto packet = test_hci_layer_->GetCommand();
    auto sub_packet = LeMultiAdvtView::Create(LeAdvertisingCommandView::Create(packet));
    ASSERT_TRUE(sub_packet.IsValid());
    ASSERT_EQ(sub_packet.GetSubCmd(), sub_ocf[i]);
    test_hci_layer_->IncomingEvent(LeMultiAdvtCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, sub_ocf[i]));
  }

  // Disable the advertiser
  le_advertising_manager_->RemoveAdvertiser(id);
  ASSERT_EQ(OpCode::LE_MULTI_ADVT, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(LeMultiAdvtSetEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
}

TEST_F(LeExtendedAdvertisingManagerTest, create_advertiser_test) {
  ExtendedAdvertisingConfig advertising_config{};
  advertising_config.advertising_type = AdvertisingType::ADV_IND;
  advertising_config.own_address_type = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
  std::vector<GapData> gap_data{};
  GapData data_item{};
  data_item.data_type_ = GapDataType::FLAGS;
  data_item.data_ = {0x34};
  gap_data.push_back(data_item);
  data_item.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  data_item.data_ = {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
  gap_data.push_back(data_item);
  advertising_config.advertisement = gap_data;
  advertising_config.scan_response = gap_data;
  advertising_config.channel_map = 1;
  advertising_config.sid = 0x01;

  auto id = le_advertising_manager_->ExtendedCreateAdvertiser(
      0x00, advertising_config, scan_callback, set_terminated_callback, 0, 0, client_handler_);
  ASSERT_NE(LeAdvertisingManager::kInvalidId, id);
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingSetStarted(0x00, id, -23, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  std::vector<OpCode> adv_opcodes = {
      OpCode::LE_SET_EXTENDED_ADVERTISING_PARAMETERS,
      OpCode::LE_SET_EXTENDED_SCAN_RESPONSE_DATA,
      OpCode::LE_SET_EXTENDED_ADVERTISING_DATA,
      OpCode::LE_SET_EXTENDED_ADVERTISING_ENABLE,
  };
  std::vector<uint8_t> success_vector{static_cast<uint8_t>(ErrorCode::SUCCESS)};
  for (size_t i = 0; i < adv_opcodes.size(); i++) {
    ASSERT_EQ(adv_opcodes[i], test_hci_layer_->GetCommand().GetOpCode());
    if (adv_opcodes[i] == OpCode::LE_SET_EXTENDED_ADVERTISING_PARAMETERS) {
      test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingParametersCompleteBuilder::Create(
          uint8_t{1}, ErrorCode::SUCCESS, static_cast<uint8_t>(-23)));
    } else {
      test_hci_layer_->IncomingEvent(
          CommandCompleteBuilder::Create(uint8_t{1}, adv_opcodes[i], std::make_unique<RawBuilder>(success_vector)));
    }
  }
  sync_client_handler();

  // Remove the advertiser
  le_advertising_manager_->RemoveAdvertiser(id);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_ADVERTISING_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  ASSERT_EQ(OpCode::LE_SET_PERIODIC_ADVERTISING_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  ASSERT_EQ(OpCode::LE_REMOVE_ADVERTISING_SET, test_hci_layer_->GetCommand().GetOpCode());
}

TEST_F(LeExtendedAdvertisingManagerTest, ignore_on_pause_on_resume_after_unregistered) {
  TestLeAddressManager* test_le_address_manager = (TestLeAddressManager*)test_acl_manager_->GetLeAddressManager();
  test_le_address_manager->ignore_unregister_for_testing = true;

  // Register LeAddressManager vai ExtendedCreateAdvertiser
  ExtendedAdvertisingConfig advertising_config{};
  advertising_config.advertising_type = AdvertisingType::ADV_IND;
  advertising_config.own_address_type = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
  std::vector<GapData> gap_data{};
  GapData data_item{};
  data_item.data_type_ = GapDataType::FLAGS;
  data_item.data_ = {0x34};
  gap_data.push_back(data_item);
  data_item.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  data_item.data_ = {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
  gap_data.push_back(data_item);
  advertising_config.advertisement = gap_data;
  advertising_config.scan_response = gap_data;
  advertising_config.channel_map = 1;
  advertising_config.sid = 0x01;

  auto id = le_advertising_manager_->ExtendedCreateAdvertiser(
      0x00, advertising_config, scan_callback, set_terminated_callback, 0, 0, client_handler_);
  ASSERT_NE(LeAdvertisingManager::kInvalidId, id);
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingSetStarted(0x00, id, -23, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  std::vector<OpCode> adv_opcodes = {
      OpCode::LE_SET_EXTENDED_ADVERTISING_PARAMETERS,
      OpCode::LE_SET_EXTENDED_SCAN_RESPONSE_DATA,
      OpCode::LE_SET_EXTENDED_ADVERTISING_DATA,
      OpCode::LE_SET_EXTENDED_ADVERTISING_ENABLE,
  };
  std::vector<uint8_t> success_vector{static_cast<uint8_t>(ErrorCode::SUCCESS)};
  for (size_t i = 0; i < adv_opcodes.size(); i++) {
    ASSERT_EQ(adv_opcodes[i], test_hci_layer_->GetCommand().GetOpCode());
    if (adv_opcodes[i] == OpCode::LE_SET_EXTENDED_ADVERTISING_PARAMETERS) {
      test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingParametersCompleteBuilder::Create(
          uint8_t{1}, ErrorCode::SUCCESS, static_cast<uint8_t>(-23)));
    } else {
      test_hci_layer_->IncomingEvent(
          CommandCompleteBuilder::Create(uint8_t{1}, adv_opcodes[i], std::make_unique<RawBuilder>(success_vector)));
    }
  }
  sync_client_handler();

  // Unregister LeAddressManager vai RemoveAdvertiser
  le_advertising_manager_->RemoveAdvertiser(id);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_ADVERTISING_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  ASSERT_EQ(OpCode::LE_SET_PERIODIC_ADVERTISING_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  ASSERT_EQ(OpCode::LE_REMOVE_ADVERTISING_SET, test_hci_layer_->GetCommand().GetOpCode());
  sync_client_handler();

  // Unregistered client should ignore OnPause/OnResume
  ASSERT_NE(test_le_address_manager->client_, nullptr);
  ASSERT_EQ(test_le_address_manager->test_client_state_, TestLeAddressManager::TestClientState::UNREGISTERED);
  test_le_address_manager->client_->OnPause();
  ASSERT_EQ(test_le_address_manager->test_client_state_, TestLeAddressManager::TestClientState::UNREGISTERED);
  test_le_address_manager->client_->OnResume();
  ASSERT_EQ(test_le_address_manager->test_client_state_, TestLeAddressManager::TestClientState::UNREGISTERED);
}

TEST_F(LeAdvertisingAPITest, startup_teardown) {}

TEST_F(LeAndroidHciAdvertisingAPITest, startup_teardown) {}

TEST_F(LeAndroidHciAdvertisingAPIPublicAddressTest, startup_teardown) {}

TEST_F(LeExtendedAdvertisingAPITest, startup_teardown) {}

TEST_F(LeAdvertisingAPITest, set_parameter) {
  ExtendedAdvertisingConfig advertising_config{};
  advertising_config.advertising_type = AdvertisingType::ADV_IND;
  advertising_config.own_address_type = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
  std::vector<GapData> gap_data{};
  GapData data_item{};
  data_item.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  data_item.data_ = {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
  gap_data.push_back(data_item);
  advertising_config.advertisement = gap_data;
  advertising_config.channel_map = 1;
  le_advertising_manager_->SetParameters(advertiser_id_, advertising_config);
  ASSERT_EQ(OpCode::LE_SET_ADVERTISING_PARAMETERS, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingParametersUpdated(advertiser_id_, 0x00, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetAdvertisingParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
}

TEST_F(LeAndroidHciAdvertisingAPITest, set_parameter) {
  ExtendedAdvertisingConfig advertising_config{};
  advertising_config.advertising_type = AdvertisingType::ADV_IND;
  advertising_config.own_address_type = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
  std::vector<GapData> gap_data{};
  GapData data_item{};
  data_item.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  data_item.data_ = {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
  gap_data.push_back(data_item);
  advertising_config.advertisement = gap_data;
  advertising_config.channel_map = 1;
  le_advertising_manager_->SetParameters(advertiser_id_, advertising_config);
  auto packet = test_hci_layer_->GetCommand();
  auto sub_packet = LeMultiAdvtView::Create(LeAdvertisingCommandView::Create(packet));
  ASSERT_TRUE(sub_packet.IsValid());
  ASSERT_EQ(sub_packet.GetSubCmd(), SubOcf::SET_PARAM);
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingParametersUpdated(advertiser_id_, 0x00, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeMultiAdvtCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, SubOcf::SET_PARAM));
}

TEST_F(LeExtendedAdvertisingAPITest, set_parameter) {
  ExtendedAdvertisingConfig advertising_config{};
  advertising_config.advertising_type = AdvertisingType::ADV_IND;
  advertising_config.own_address_type = OwnAddressType::PUBLIC_DEVICE_ADDRESS;
  std::vector<GapData> gap_data{};
  GapData data_item{};
  data_item.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  data_item.data_ = {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
  gap_data.push_back(data_item);
  advertising_config.advertisement = gap_data;
  advertising_config.channel_map = 1;
  advertising_config.sid = 0x01;
  advertising_config.tx_power = 0x08;
  le_advertising_manager_->SetParameters(advertiser_id_, advertising_config);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_ADVERTISING_PARAMETERS, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingParametersUpdated(advertiser_id_, 0x08, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(
      LeSetExtendedAdvertisingParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, 0x08));
}

TEST_F(LeAdvertisingAPITest, set_data_test) {
  // Set advertising data
  std::vector<GapData> advertising_data{};
  GapData data_item{};
  data_item.data_type_ = GapDataType::TX_POWER_LEVEL;
  data_item.data_ = {0x00};
  advertising_data.push_back(data_item);
  le_advertising_manager_->SetData(advertiser_id_, false, advertising_data);
  ASSERT_EQ(OpCode::LE_SET_ADVERTISING_DATA, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetAdvertisingDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  // Set scan response data
  std::vector<GapData> response_data{};
  GapData data_item2{};
  data_item2.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  data_item2.data_ = {'t', 'e', 's', 't', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
  response_data.push_back(data_item2);
  le_advertising_manager_->SetData(advertiser_id_, true, response_data);
  ASSERT_EQ(OpCode::LE_SET_SCAN_RESPONSE_DATA, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnScanResponseDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetScanResponseDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
}

TEST_F(LeExtendedAdvertisingAPITest, set_data_test) {
  // Set advertising data
  std::vector<GapData> advertising_data{};
  GapData data_item{};
  data_item.data_type_ = GapDataType::TX_POWER_LEVEL;
  data_item.data_ = {0x00};
  advertising_data.push_back(data_item);
  le_advertising_manager_->SetData(advertiser_id_, false, advertising_data);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_ADVERTISING_DATA, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  // Set scan response data
  std::vector<GapData> response_data{};
  GapData data_item2{};
  data_item2.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  data_item2.data_ = {'t', 'e', 's', 't', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
  response_data.push_back(data_item2);
  le_advertising_manager_->SetData(advertiser_id_, true, response_data);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_RESPONSE_DATA, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnScanResponseDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetExtendedScanResponseDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
}

TEST_F(LeAndroidHciAdvertisingAPITest, set_data_test) {
  // Set advertising data
  std::vector<GapData> advertising_data{};
  GapData data_item{};
  data_item.data_type_ = GapDataType::TX_POWER_LEVEL;
  data_item.data_ = {0x00};
  advertising_data.push_back(data_item);
  le_advertising_manager_->SetData(advertiser_id_, false, advertising_data);
  auto packet = test_hci_layer_->GetCommand();
  auto sub_packet = LeMultiAdvtView::Create(LeAdvertisingCommandView::Create(packet));
  ASSERT_TRUE(sub_packet.IsValid());
  ASSERT_EQ(sub_packet.GetSubCmd(), SubOcf::SET_DATA);
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeMultiAdvtCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, SubOcf::SET_DATA));

  // Set scan response data
  std::vector<GapData> response_data{};
  GapData data_item2{};
  data_item2.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  data_item2.data_ = {'t', 'e', 's', 't', ' ', 'd', 'e', 'v', 'i', 'c', 'e'};
  response_data.push_back(data_item2);
  le_advertising_manager_->SetData(advertiser_id_, true, response_data);
  packet = test_hci_layer_->GetCommand();
  sub_packet = LeMultiAdvtView::Create(LeAdvertisingCommandView::Create(packet));
  ASSERT_TRUE(sub_packet.IsValid());
  ASSERT_EQ(sub_packet.GetSubCmd(), SubOcf::SET_SCAN_RESP);
  EXPECT_CALL(
      mock_advertising_callback_,
      OnScanResponseDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(
      LeMultiAdvtCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, SubOcf::SET_SCAN_RESP));
}

TEST_F(LeExtendedAdvertisingAPITest, set_data_fragments_test) {
  // Set advertising data
  std::vector<GapData> advertising_data{};
  for (uint8_t i = 0; i < 3; i++) {
    GapData data_item{};
    data_item.data_.push_back(0xda);
    data_item.data_type_ = GapDataType::SERVICE_DATA_128_BIT_UUIDS;
    uint8_t uuid[16] = {0xf0, 0x34, 0x9b, 0x5f, 0x80, 0x00, 0x00, 0x80, 0x00, 0x10, 0x00, 0x00, 0x00, 0x10, 0x00, i};
    std::copy_n(uuid, 16, std::back_inserter(data_item.data_));
    uint8_t service_data[200];
    std::copy_n(service_data, 200, std::back_inserter(data_item.data_));
    advertising_data.push_back(data_item);
  }
  le_advertising_manager_->SetData(advertiser_id_, false, advertising_data);

  // First fragment
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_ADVERTISING_DATA, test_hci_layer_->GetCommand().GetOpCode());
  // Intermediate fragment
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_ADVERTISING_DATA, test_hci_layer_->GetCommand().GetOpCode());
  // Last fragment
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_ADVERTISING_DATA, test_hci_layer_->GetCommand().GetOpCode());

  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
}

TEST_F(LeExtendedAdvertisingAPITest, set_scan_response_fragments_test) {
  // Set advertising data
  std::vector<GapData> advertising_data{};
  for (uint8_t i = 0; i < 3; i++) {
    GapData data_item{};
    data_item.data_.push_back(0xfa);
    data_item.data_type_ = GapDataType::SERVICE_DATA_128_BIT_UUIDS;
    uint8_t uuid[16] = {0xf0, 0x34, 0x9b, 0x5f, 0x80, 0x00, 0x00, 0x80, 0x00, 0x10, 0x00, 0x00, 0x00, 0x10, 0x00, i};
    std::copy_n(uuid, 16, std::back_inserter(data_item.data_));
    uint8_t service_data[232];
    std::copy_n(service_data, 232, std::back_inserter(data_item.data_));
    advertising_data.push_back(data_item);
  }
  le_advertising_manager_->SetData(advertiser_id_, true, advertising_data);

  // First fragment
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_RESPONSE_DATA, test_hci_layer_->GetCommand().GetOpCode());
  // Intermediate fragment
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_RESPONSE_DATA, test_hci_layer_->GetCommand().GetOpCode());
  // Last fragment
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_RESPONSE_DATA, test_hci_layer_->GetCommand().GetOpCode());

  EXPECT_CALL(
      mock_advertising_callback_,
      OnScanResponseDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetExtendedScanResponseDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetExtendedScanResponseDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetExtendedScanResponseDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
}

TEST_F(LeExtendedAdvertisingAPITest, set_data_with_invalid_ad_structure) {
  // Set advertising data with AD structure that length greater than 251
  std::vector<GapData> advertising_data{};
  GapData data_item{};
  data_item.data_.push_back(0xfb);
  data_item.data_type_ = GapDataType::SERVICE_DATA_128_BIT_UUIDS;
  uint8_t uuid[16] = {0xf0, 0x34, 0x9b, 0x5f, 0x80, 0x00, 0x00, 0x80, 0x00, 0x10, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00};
  std::copy_n(uuid, 16, std::back_inserter(data_item.data_));
  uint8_t service_data[233];
  std::copy_n(service_data, 233, std::back_inserter(data_item.data_));
  advertising_data.push_back(data_item);

  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::INTERNAL_ERROR));

  le_advertising_manager_->SetData(advertiser_id_, false, advertising_data);

  EXPECT_CALL(
      mock_advertising_callback_,
      OnScanResponseDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::INTERNAL_ERROR));
  le_advertising_manager_->SetData(advertiser_id_, true, advertising_data);

  sync_client_handler();
}

TEST_F(LeExtendedAdvertisingAPITest, set_data_with_invalid_length) {
  // Set advertising data with data that greater than le_maximum_advertising_data_length_
  std::vector<GapData> advertising_data{};
  for (uint8_t i = 0; i < 10; i++) {
    GapData data_item{};
    data_item.data_.push_back(0xfb);
    data_item.data_type_ = GapDataType::SERVICE_DATA_128_BIT_UUIDS;
    uint8_t uuid[16] = {0xf0, 0x34, 0x9b, 0x5f, 0x80, 0x00, 0x00, 0x80, 0x00, 0x10, 0x00, 0x00, 0x00, 0x10, 0x00, i};
    std::copy_n(uuid, 16, std::back_inserter(data_item.data_));
    uint8_t service_data[200];
    std::copy_n(service_data, 200, std::back_inserter(data_item.data_));
    advertising_data.push_back(data_item);
  }

  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::DATA_TOO_LARGE));
  le_advertising_manager_->SetData(advertiser_id_, false, advertising_data);

  EXPECT_CALL(
      mock_advertising_callback_,
      OnScanResponseDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::DATA_TOO_LARGE));
  le_advertising_manager_->SetData(advertiser_id_, true, advertising_data);

  sync_client_handler();
}

TEST_F(LeAdvertisingAPITest, disable_enable_advertiser_test) {
  // disable advertiser
  le_advertising_manager_->EnableAdvertiser(advertiser_id_, false, 0x00, 0x00);
  ASSERT_EQ(OpCode::LE_SET_ADVERTISING_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingEnabled(advertiser_id_, false, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetAdvertisingEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  sync_client_handler();

  // enable advertiser
  le_advertising_manager_->EnableAdvertiser(advertiser_id_, true, 0x00, 0x00);
  ASSERT_EQ(OpCode::LE_SET_ADVERTISING_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingEnabled(advertiser_id_, true, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetAdvertisingEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
}

TEST_F(LeAndroidHciAdvertisingAPITest, disable_enable_advertiser_test) {
  // disable advertiser
  le_advertising_manager_->EnableAdvertiser(advertiser_id_, false, 0x00, 0x00);
  auto packet = test_hci_layer_->GetCommand();
  auto sub_packet = LeMultiAdvtView::Create(LeAdvertisingCommandView::Create(packet));
  ASSERT_TRUE(sub_packet.IsValid());
  ASSERT_EQ(sub_packet.GetSubCmd(), SubOcf::SET_ENABLE);
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingEnabled(advertiser_id_, false, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(
      LeMultiAdvtCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, SubOcf::SET_ENABLE));
  sync_client_handler();

  // enable advertiser
  le_advertising_manager_->EnableAdvertiser(advertiser_id_, true, 0x00, 0x00);
  packet = test_hci_layer_->GetCommand();
  sub_packet = LeMultiAdvtView::Create(LeAdvertisingCommandView::Create(packet));
  ASSERT_TRUE(sub_packet.IsValid());
  ASSERT_EQ(sub_packet.GetSubCmd(), SubOcf::SET_ENABLE);
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingEnabled(advertiser_id_, true, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(
      LeMultiAdvtCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, SubOcf::SET_ENABLE));
}

TEST_F(LeExtendedAdvertisingAPITest, disable_enable_advertiser_test) {
  // disable advertiser
  le_advertising_manager_->EnableAdvertiser(advertiser_id_, false, 0x00, 0x00);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_ADVERTISING_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingEnabled(advertiser_id_, false, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  sync_client_handler();

  // enable advertiser
  le_advertising_manager_->EnableAdvertiser(advertiser_id_, true, 0x00, 0x00);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_ADVERTISING_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnAdvertisingEnabled(advertiser_id_, true, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
}

TEST_F(LeExtendedAdvertisingAPITest, disable_after_enable) {
  // we expect Started -> Enable(false) -> Enable(true) -> Enable(false)

  // setup already arranges everything and starts the advertiser

  // expect
  InSequence s;
  EXPECT_CALL(mock_advertising_callback_, OnAdvertisingEnabled(_, false, _));
  EXPECT_CALL(mock_advertising_callback_, OnAdvertisingEnabled(_, true, _));
  EXPECT_CALL(mock_advertising_callback_, OnAdvertisingEnabled(_, false, _));
  EXPECT_CALL(mock_advertising_callback_, OnAdvertisingEnabled(_, true, _));

  // act

  // disable
  le_advertising_manager_->EnableAdvertiser(advertiser_id_, false, 0x00, 0x00);
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      LeSetExtendedAdvertisingEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  // enable
  le_advertising_manager_->EnableAdvertiser(advertiser_id_, true, 0x00, 0x00);
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      LeSetExtendedAdvertisingEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  // disable
  le_advertising_manager_->EnableAdvertiser(advertiser_id_, false, 0x00, 0x00);
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      LeSetExtendedAdvertisingEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  // enable
  le_advertising_manager_->EnableAdvertiser(advertiser_id_, true, 0x00, 0x00);
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      LeSetExtendedAdvertisingEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  sync_client_handler();
}

TEST_F(LeExtendedAdvertisingAPITest, set_periodic_parameter) {
  PeriodicAdvertisingParameters advertising_config{};
  advertising_config.max_interval = 0x1000;
  advertising_config.min_interval = 0x0006;
  le_advertising_manager_->SetPeriodicParameters(advertiser_id_, advertising_config);
  ASSERT_EQ(OpCode::LE_SET_PERIODIC_ADVERTISING_PARAM, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnPeriodicAdvertisingParametersUpdated(advertiser_id_, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetPeriodicAdvertisingParamCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  sync_client_handler();
}

TEST_F(LeExtendedAdvertisingAPITest, set_periodic_data_test) {
  // Set advertising data
  std::vector<GapData> advertising_data{};
  GapData data_item{};
  data_item.data_type_ = GapDataType::TX_POWER_LEVEL;
  data_item.data_ = {0x00};
  advertising_data.push_back(data_item);
  le_advertising_manager_->SetPeriodicData(advertiser_id_, advertising_data);
  ASSERT_EQ(OpCode::LE_SET_PERIODIC_ADVERTISING_DATA, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnPeriodicAdvertisingDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetPeriodicAdvertisingDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  sync_client_handler();
}

TEST_F(LeExtendedAdvertisingAPITest, set_periodic_data_fragments_test) {
  // Set advertising data
  std::vector<GapData> advertising_data{};
  for (uint8_t i = 0; i < 3; i++) {
    GapData data_item{};
    data_item.data_.push_back(0xfa);
    data_item.data_type_ = GapDataType::SERVICE_DATA_128_BIT_UUIDS;
    uint8_t uuid[16] = {0xf0, 0x34, 0x9b, 0x5f, 0x80, 0x00, 0x00, 0x80, 0x00, 0x10, 0x00, 0x00, 0x00, 0x10, 0x00, i};
    std::copy_n(uuid, 16, std::back_inserter(data_item.data_));
    uint8_t service_data[232];
    std::copy_n(service_data, 232, std::back_inserter(data_item.data_));
    advertising_data.push_back(data_item);
  }
  le_advertising_manager_->SetPeriodicData(advertiser_id_, advertising_data);

  // First fragment
  ASSERT_EQ(OpCode::LE_SET_PERIODIC_ADVERTISING_DATA, test_hci_layer_->GetCommand().GetOpCode());
  // Intermediate fragment
  ASSERT_EQ(OpCode::LE_SET_PERIODIC_ADVERTISING_DATA, test_hci_layer_->GetCommand().GetOpCode());
  // Last fragment
  ASSERT_EQ(OpCode::LE_SET_PERIODIC_ADVERTISING_DATA, test_hci_layer_->GetCommand().GetOpCode());

  EXPECT_CALL(
      mock_advertising_callback_,
      OnPeriodicAdvertisingDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetPeriodicAdvertisingDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetPeriodicAdvertisingDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetPeriodicAdvertisingDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
}

TEST_F(LeExtendedAdvertisingAPITest, set_perodic_data_with_invalid_ad_structure) {
  // Set advertising data with AD structure that length greater than 251
  std::vector<GapData> advertising_data{};
  GapData data_item{};
  data_item.data_.push_back(0xfb);
  data_item.data_type_ = GapDataType::SERVICE_DATA_128_BIT_UUIDS;
  uint8_t uuid[16] = {0xf0, 0x34, 0x9b, 0x5f, 0x80, 0x00, 0x00, 0x80, 0x00, 0x10, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00};
  std::copy_n(uuid, 16, std::back_inserter(data_item.data_));
  uint8_t service_data[233];
  std::copy_n(service_data, 233, std::back_inserter(data_item.data_));
  advertising_data.push_back(data_item);

  EXPECT_CALL(
      mock_advertising_callback_,
      OnPeriodicAdvertisingDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::INTERNAL_ERROR));

  le_advertising_manager_->SetPeriodicData(advertiser_id_, advertising_data);
}

TEST_F(LeExtendedAdvertisingAPITest, set_perodic_data_with_invalid_length) {
  // Set advertising data with data that greater than le_maximum_advertising_data_length_
  std::vector<GapData> advertising_data{};
  for (uint8_t i = 0; i < 10; i++) {
    GapData data_item{};
    data_item.data_.push_back(0xfb);
    data_item.data_type_ = GapDataType::SERVICE_DATA_128_BIT_UUIDS;
    uint8_t uuid[16] = {0xf0, 0x34, 0x9b, 0x5f, 0x80, 0x00, 0x00, 0x80, 0x00, 0x10, 0x00, 0x00, 0x00, 0x10, 0x00, i};
    std::copy_n(uuid, 16, std::back_inserter(data_item.data_));
    uint8_t service_data[200];
    std::copy_n(service_data, 200, std::back_inserter(data_item.data_));
    advertising_data.push_back(data_item);
  }

  EXPECT_CALL(
      mock_advertising_callback_,
      OnPeriodicAdvertisingDataSet(advertiser_id_, AdvertisingCallback::AdvertisingStatus::DATA_TOO_LARGE));
  le_advertising_manager_->SetPeriodicData(advertiser_id_, advertising_data);

  sync_client_handler();
}

TEST_F(LeExtendedAdvertisingAPITest, disable_enable_periodic_advertiser_test) {
  // disable advertiser
  le_advertising_manager_->EnablePeriodicAdvertising(advertiser_id_, false);
  ASSERT_EQ(OpCode::LE_SET_PERIODIC_ADVERTISING_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnPeriodicAdvertisingEnabled(advertiser_id_, false, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetPeriodicAdvertisingEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  sync_client_handler();

  // enable advertiser
  le_advertising_manager_->EnablePeriodicAdvertising(advertiser_id_, true);
  ASSERT_EQ(OpCode::LE_SET_PERIODIC_ADVERTISING_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  EXPECT_CALL(
      mock_advertising_callback_,
      OnPeriodicAdvertisingEnabled(advertiser_id_, true, AdvertisingCallback::AdvertisingStatus::SUCCESS));
  test_hci_layer_->IncomingEvent(LeSetPeriodicAdvertisingEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  sync_client_handler();
}

TEST_F(LeExtendedAdvertisingAPITest, trigger_advertiser_callbacks_if_started_while_paused) {
  // arrange
  auto test_le_address_manager = (TestLeAddressManager*)test_acl_manager_->GetLeAddressManager();
  auto id_promise = std::promise<uint8_t>{};
  auto id_future = id_promise.get_future();
  le_advertising_manager_->RegisterAdvertiser(base::BindOnce(
      [](std::promise<uint8_t> promise, uint8_t id, uint8_t _status) { promise.set_value(id); },
      std::move(id_promise)));
  sync_client_handler();
  auto set_id = id_future.get();

  auto status_promise = std::promise<ErrorCode>{};
  auto status_future = status_promise.get_future();

  test_le_address_manager->client_->OnPause();

  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingEnableCompleteBuilder::Create(1, ErrorCode::SUCCESS));
  sync_client_handler();

  // act
  le_advertising_manager_->StartAdvertising(
      set_id,
      {},
      0,
      base::BindOnce(
          [](std::promise<ErrorCode> promise, uint8_t status) { promise.set_value((ErrorCode)status); },
          std::move(status_promise)),
      base::Bind([](uint8_t _status) {}),
      base::Bind([](Address _address, AddressType _address_type) {}),
      base::Bind([](ErrorCode _status, uint8_t _unused_1, uint8_t _unused_2) {}),
      client_handler_);

  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingParametersCompleteBuilder::Create(1, ErrorCode::SUCCESS, 0));

  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(LeSetExtendedScanResponseDataCompleteBuilder::Create(1, ErrorCode::SUCCESS));

  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingDataCompleteBuilder::Create(1, ErrorCode::SUCCESS));

  EXPECT_EQ(status_future.wait_for(std::chrono::milliseconds(100)), std::future_status::timeout);

  test_le_address_manager->client_->OnResume();

  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(LeSetExtendedAdvertisingEnableCompleteBuilder::Create(1, ErrorCode::SUCCESS));

  // assert
  EXPECT_EQ(status_future.get(), ErrorCode::SUCCESS);

  sync_client_handler();
}

TEST_F(LeExtendedAdvertisingAPITest, no_callbacks_on_pause) {
  // arrange
  auto test_le_address_manager = (TestLeAddressManager*)test_acl_manager_->GetLeAddressManager();

  // expect
  EXPECT_CALL(mock_advertising_callback_, OnAdvertisingEnabled(_, _, _)).Times(0);

  // act
  LOG_INFO("pause");
  test_le_address_manager->client_->OnPause();
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      LeSetExtendedAdvertisingEnableCompleteBuilder::Create(1, ErrorCode::SUCCESS));

  sync_client_handler();
}

TEST_F(LeExtendedAdvertisingAPITest, no_callbacks_on_resume) {
  // arrange
  auto test_le_address_manager = (TestLeAddressManager*)test_acl_manager_->GetLeAddressManager();
  test_le_address_manager->client_->OnPause();
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      LeSetExtendedAdvertisingEnableCompleteBuilder::Create(1, ErrorCode::SUCCESS));
  sync_client_handler();

  // expect
  EXPECT_CALL(mock_advertising_callback_, OnAdvertisingEnabled(_, _, _)).Times(0);

  // act
  test_le_address_manager->client_->OnResume();
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      LeSetExtendedAdvertisingEnableCompleteBuilder::Create(1, ErrorCode::SUCCESS));

  sync_client_handler();
}

}  // namespace
}  // namespace hci
}  // namespace bluetooth

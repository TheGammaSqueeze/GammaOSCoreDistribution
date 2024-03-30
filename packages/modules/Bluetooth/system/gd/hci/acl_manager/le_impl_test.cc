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

#include "hci/acl_manager/le_impl.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <chrono>
#include <mutex>

#include "common/bidi_queue.h"
#include "common/callback.h"
#include "common/testing/log_capture.h"
#include "hci/acl_manager.h"
#include "hci/acl_manager/le_connection_callbacks.h"
#include "hci/acl_manager/le_connection_management_callbacks.h"
#include "hci/address_with_type.h"
#include "hci/controller.h"
#include "hci/hci_packets.h"
#include "os/handler.h"
#include "os/log.h"
#include "packet/bit_inserter.h"
#include "packet/raw_builder.h"

using namespace bluetooth;
using namespace std::chrono_literals;

using ::bluetooth::common::BidiQueue;
using ::bluetooth::common::Callback;
using ::bluetooth::os::Handler;
using ::bluetooth::os::Thread;
using ::bluetooth::packet::BitInserter;
using ::bluetooth::packet::RawBuilder;
using ::bluetooth::testing::LogCapture;

using ::testing::_;
using ::testing::DoAll;
using ::testing::SaveArg;

namespace {
constexpr char kFixedAddress[] = "c0:aa:bb:cc:dd:ee";
constexpr char kRemoteAddress[] = "00:11:22:33:44:55";
constexpr bool kCrashOnUnknownHandle = true;
constexpr char kLocalRandomAddress[] = "04:c0:aa:bb:cc:dd:ee";
constexpr char kRemoteRandomAddress[] = "04:11:22:33:44:55";
constexpr uint16_t kHciHandle = 123;
[[maybe_unused]] constexpr bool kAddToFilterAcceptList = true;
[[maybe_unused]] constexpr bool kSkipFilterAcceptList = !kAddToFilterAcceptList;
[[maybe_unused]] constexpr bool kIsDirectConnection = true;
[[maybe_unused]] constexpr bool kIsBackgroundConnection = !kIsDirectConnection;
constexpr crypto_toolbox::Octet16 kRotationIrk = {};
constexpr std::chrono::milliseconds kMinimumRotationTime(14 * 1000);
constexpr std::chrono::milliseconds kMaximumRotationTime(16 * 1000);
constexpr uint16_t kIntervalMax = 0x40;
constexpr uint16_t kIntervalMin = 0x20;
constexpr uint16_t kLatency = 0x60;
constexpr uint16_t kLength = 0x5678;
constexpr uint16_t kTime = 0x1234;
constexpr uint16_t kTimeout = 0x80;
constexpr std::array<uint8_t, 16> kPeerIdentityResolvingKey({
    0x00,
    0x01,
    0x02,
    0x03,
    0x04,
    0x05,
    0x06,
    0x07,
    0x08,
    0x09,
    0x0a,
    0x0b,
    0x0c,
    0x0d,
    0x0e,
    0x0f,
});
constexpr std::array<uint8_t, 16> kLocalIdentityResolvingKey({
    0x80,
    0x81,
    0x82,
    0x83,
    0x84,
    0x85,
    0x86,
    0x87,
    0x88,
    0x89,
    0x8a,
    0x8b,
    0x8c,
    0x8d,
    0x8e,
    0x8f,
});

template <typename B>
std::shared_ptr<std::vector<uint8_t>> Serialize(std::unique_ptr<B> build) {
  auto bytes = std::make_shared<std::vector<uint8_t>>();
  BitInserter bi(*bytes);
  build->Serialize(bi);
  return bytes;
}

template <typename T>
T CreateCommandView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(hci::CommandView::Create(hci::PacketView<hci::kLittleEndian>(bytes)));
}

template <typename T>
T CreateAclCommandView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(CreateCommandView<hci::AclCommandView>(bytes));
}

template <typename T>
T CreateLeConnectionManagementCommandView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(CreateAclCommandView<hci::LeConnectionManagementCommandView>(bytes));
}

template <typename T>
T CreateLeSecurityCommandView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(CreateCommandView<hci::LeSecurityCommandView>(bytes));
}

template <typename T>
T CreateLeEventView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(hci::LeMetaEventView::Create(hci::EventView::Create(hci::PacketView<hci::kLittleEndian>(bytes))));
}

[[maybe_unused]] hci::CommandCompleteView ReturnCommandComplete(hci::OpCode op_code, hci::ErrorCode error_code) {
  std::vector<uint8_t> success_vector{static_cast<uint8_t>(error_code)};
  auto builder = hci::CommandCompleteBuilder::Create(uint8_t{1}, op_code, std::make_unique<RawBuilder>(success_vector));
  auto bytes = Serialize<hci::CommandCompleteBuilder>(std::move(builder));
  return hci::CommandCompleteView::Create(hci::EventView::Create(hci::PacketView<hci::kLittleEndian>(bytes)));
}

[[maybe_unused]] hci::CommandStatusView ReturnCommandStatus(hci::OpCode op_code, hci::ErrorCode error_code) {
  std::vector<uint8_t> success_vector{static_cast<uint8_t>(error_code)};
  auto builder = hci::CommandStatusBuilder::Create(
      hci::ErrorCode::SUCCESS, uint8_t{1}, op_code, std::make_unique<RawBuilder>(success_vector));
  auto bytes = Serialize<hci::CommandStatusBuilder>(std::move(builder));
  return hci::CommandStatusView::Create(hci::EventView::Create(hci::PacketView<hci::kLittleEndian>(bytes)));
}

}  // namespace

namespace bluetooth {
namespace hci {
namespace acl_manager {

namespace {

PacketView<kLittleEndian> GetPacketView(std::unique_ptr<packet::BasePacketBuilder> packet) {
  auto bytes = std::make_shared<std::vector<uint8_t>>();
  BitInserter i(*bytes);
  bytes->reserve(packet->size());
  packet->Serialize(i);
  return packet::PacketView<packet::kLittleEndian>(bytes);
}

class TestController : public Controller {
 public:
  bool IsSupported(OpCode op_code) const override {
    LOG_INFO("IsSupported");
    return supported_opcodes_.count(op_code) == 1;
  }

  void AddSupported(OpCode op_code) {
    LOG_INFO("AddSupported");
    supported_opcodes_.insert(op_code);
  }

  uint16_t GetNumAclPacketBuffers() const {
    return max_acl_packet_credits_;
  }

  uint16_t GetAclPacketLength() const {
    return hci_mtu_;
  }

  LeBufferSize GetLeBufferSize() const {
    LeBufferSize le_buffer_size;
    le_buffer_size.le_data_packet_length_ = le_hci_mtu_;
    le_buffer_size.total_num_le_packets_ = le_max_acl_packet_credits_;
    return le_buffer_size;
  }

  void RegisterCompletedAclPacketsCallback(CompletedAclPacketsCallback cb) {
    acl_credits_callback_ = cb;
  }

  void SendCompletedAclPacketsCallback(uint16_t handle, uint16_t credits) {
    acl_credits_callback_.Invoke(handle, credits);
  }

  void UnregisterCompletedAclPacketsCallback() {
    acl_credits_callback_ = {};
  }

  bool SupportsBlePrivacy() const override {
    return supports_ble_privacy_;
  }
  bool supports_ble_privacy_{false};

 public:
  const uint16_t max_acl_packet_credits_ = 10;
  const uint16_t hci_mtu_ = 1024;
  const uint16_t le_max_acl_packet_credits_ = 15;
  const uint16_t le_hci_mtu_ = 27;

 private:
  CompletedAclPacketsCallback acl_credits_callback_;
  std::set<OpCode> supported_opcodes_{};
};

class TestHciLayer : public HciLayer {
  // This is a springboard class that converts from `AclCommandBuilder`
  // to `ComandBuilder` for use in the hci layer.
  template <typename T>
  class CommandInterfaceImpl : public CommandInterface<T> {
   public:
    explicit CommandInterfaceImpl(HciLayer& hci) : hci_(hci) {}
    ~CommandInterfaceImpl() = default;

    void EnqueueCommand(
        std::unique_ptr<T> command, common::ContextualOnceCallback<void(CommandCompleteView)> on_complete) override {
      hci_.EnqueueCommand(move(command), std::move(on_complete));
    }

    void EnqueueCommand(
        std::unique_ptr<T> command, common::ContextualOnceCallback<void(CommandStatusView)> on_status) override {
      hci_.EnqueueCommand(move(command), std::move(on_status));
    }
    HciLayer& hci_;
  };

  void EnqueueCommand(
      std::unique_ptr<CommandBuilder> command,
      common::ContextualOnceCallback<void(CommandStatusView)> on_status) override {
    const std::lock_guard<std::mutex> lock(command_queue_mutex_);
    command_queue_.push(std::move(command));
    command_status_callbacks.push_back(std::move(on_status));
    if (command_promise_ != nullptr) {
      std::promise<void>* prom = command_promise_.release();
      prom->set_value();
      delete prom;
    }
  }

  void EnqueueCommand(
      std::unique_ptr<CommandBuilder> command,
      common::ContextualOnceCallback<void(CommandCompleteView)> on_complete) override {
    const std::lock_guard<std::mutex> lock(command_queue_mutex_);
    command_queue_.push(std::move(command));
    command_complete_callbacks.push_back(std::move(on_complete));
    if (command_promise_ != nullptr) {
      std::promise<void>* prom = command_promise_.release();
      prom->set_value();
      delete prom;
    }
  }

 public:
  std::unique_ptr<CommandBuilder> DequeueCommand() {
    const std::lock_guard<std::mutex> lock(command_queue_mutex_);
    auto packet = std::move(command_queue_.front());
    command_queue_.pop();
    return std::move(packet);
  }

  std::shared_ptr<std::vector<uint8_t>> DequeueCommandBytes() {
    auto command = DequeueCommand();
    auto bytes = std::make_shared<std::vector<uint8_t>>();
    packet::BitInserter bi(*bytes);
    command->Serialize(bi);
    return bytes;
  }

  bool IsPacketQueueEmpty() const {
    const std::lock_guard<std::mutex> lock(command_queue_mutex_);
    return command_queue_.empty();
  }

  size_t NumberOfQueuedCommands() const {
    const std::lock_guard<std::mutex> lock(command_queue_mutex_);
    return command_queue_.size();
  }

  void SetCommandFuture() {
    ASSERT_EQ(command_promise_, nullptr) << "Promises, Promises, ... Only one at a time.";
    command_promise_ = std::make_unique<std::promise<void>>();
    command_future_ = std::make_unique<std::future<void>>(command_promise_->get_future());
  }

  CommandView GetLastCommand() {
    if (command_queue_.empty()) {
      return CommandView::Create(PacketView<kLittleEndian>(std::make_shared<std::vector<uint8_t>>()));
    }
    auto last = std::move(command_queue_.front());
    command_queue_.pop();
    return CommandView::Create(GetPacketView(std::move(last)));
  }

  CommandView GetCommand(OpCode op_code) {
    if (!command_queue_.empty()) {
      std::lock_guard<std::mutex> lock(command_queue_mutex_);
      if (command_future_ != nullptr) {
        command_future_.reset();
        command_promise_.reset();
      }
    } else if (command_future_ != nullptr) {
      auto result = command_future_->wait_for(std::chrono::milliseconds(1000));
      EXPECT_NE(std::future_status::timeout, result);
    }
    std::lock_guard<std::mutex> lock(command_queue_mutex_);
    ASSERT_LOG(
        !command_queue_.empty(), "Expecting command %s but command queue was empty", OpCodeText(op_code).c_str());
    CommandView command_packet_view = GetLastCommand();
    EXPECT_TRUE(command_packet_view.IsValid());
    EXPECT_EQ(command_packet_view.GetOpCode(), op_code);
    return command_packet_view;
  }

  void CommandCompleteCallback(std::unique_ptr<EventBuilder> event_builder) {
    auto event = EventView::Create(GetPacketView(std::move(event_builder)));
    CommandCompleteView complete_view = CommandCompleteView::Create(event);
    ASSERT_TRUE(complete_view.IsValid());
    ASSERT_NE((uint16_t)command_complete_callbacks.size(), 0);
    std::move(command_complete_callbacks.front()).Invoke(complete_view);
    command_complete_callbacks.pop_front();
  }

  void CommandStatusCallback(std::unique_ptr<EventBuilder> event_builder) {
    auto event = EventView::Create(GetPacketView(std::move(event_builder)));
    CommandStatusView status_view = CommandStatusView::Create(event);
    ASSERT_TRUE(status_view.IsValid());
    ASSERT_NE((uint16_t)command_status_callbacks.size(), 0);
    std::move(command_status_callbacks.front()).Invoke(status_view);
    command_status_callbacks.pop_front();
  }

  void IncomingLeMetaEvent(std::unique_ptr<LeMetaEventBuilder> event_builder) {
    auto packet = GetPacketView(std::move(event_builder));
    EventView event = EventView::Create(packet);
    LeMetaEventView meta_event_view = LeMetaEventView::Create(event);
    EXPECT_TRUE(meta_event_view.IsValid());
    le_event_handler_.Invoke(meta_event_view);
  }

  LeAclConnectionInterface* GetLeAclConnectionInterface(
      common::ContextualCallback<void(LeMetaEventView)> event_handler,
      common::ContextualCallback<void(uint16_t, ErrorCode)> on_disconnect,
      common::ContextualCallback<
          void(hci::ErrorCode hci_status, uint16_t, uint8_t version, uint16_t manufacturer_name, uint16_t sub_version)>
          on_read_remote_version) override {
    disconnect_handlers_.push_back(on_disconnect);
    read_remote_version_handlers_.push_back(on_read_remote_version);
    le_event_handler_ = event_handler;
    return &le_acl_connection_manager_interface_;
  }

  void PutLeAclConnectionInterface() override {}

 private:
  std::list<common::ContextualOnceCallback<void(CommandCompleteView)>> command_complete_callbacks;
  std::list<common::ContextualOnceCallback<void(CommandStatusView)>> command_status_callbacks;
  common::ContextualCallback<void(LeMetaEventView)> le_event_handler_;
  std::queue<std::unique_ptr<CommandBuilder>> command_queue_;
  mutable std::mutex command_queue_mutex_;
  std::unique_ptr<std::promise<void>> command_promise_;
  std::unique_ptr<std::future<void>> command_future_;
  CommandInterfaceImpl<AclCommandBuilder> le_acl_connection_manager_interface_{*this};
};
}  // namespace

class MockLeConnectionCallbacks : public LeConnectionCallbacks {
 public:
  MOCK_METHOD(
      void,
      OnLeConnectSuccess,
      (AddressWithType address_with_type, std::unique_ptr<LeAclConnection> connection),
      (override));
  MOCK_METHOD(void, OnLeConnectFail, (AddressWithType address_with_type, ErrorCode reason), (override));
};

class MockLeConnectionManagementCallbacks : public LeConnectionManagementCallbacks {
 public:
  MOCK_METHOD(
      void,
      OnConnectionUpdate,
      (hci::ErrorCode hci_status,
       uint16_t connection_interval,
       uint16_t connection_latency,
       uint16_t supervision_timeout),
      (override));
  MOCK_METHOD(
      void,
      OnDataLengthChange,
      (uint16_t tx_octets, uint16_t tx_time, uint16_t rx_octets, uint16_t rx_time),
      (override));
  MOCK_METHOD(void, OnDisconnection, (ErrorCode reason), (override));
  MOCK_METHOD(
      void,
      OnReadRemoteVersionInformationComplete,
      (hci::ErrorCode hci_status, uint8_t lmp_version, uint16_t manufacturer_name, uint16_t sub_version),
      (override));
  MOCK_METHOD(void, OnLeReadRemoteFeaturesComplete, (hci::ErrorCode hci_status, uint64_t features), (override));
  MOCK_METHOD(void, OnPhyUpdate, (hci::ErrorCode hci_status, uint8_t tx_phy, uint8_t rx_phy), (override));
  MOCK_METHOD(void, OnLocalAddressUpdate, (AddressWithType address_with_type), (override));
};

class LeImplTest : public ::testing::Test {
 protected:
  void SetUp() override {
    bluetooth::common::InitFlags::SetAllForTesting();
    thread_ = new Thread("thread", Thread::Priority::NORMAL);
    handler_ = new Handler(thread_);
    controller_ = new TestController();
    hci_layer_ = new TestHciLayer();

    round_robin_scheduler_ = new RoundRobinScheduler(handler_, controller_, hci_queue_.GetUpEnd());
    hci_queue_.GetDownEnd()->RegisterDequeue(
        handler_, common::Bind(&LeImplTest::HciDownEndDequeue, common::Unretained(this)));
    le_impl_ = new le_impl(hci_layer_, controller_, handler_, round_robin_scheduler_, kCrashOnUnknownHandle);
    le_impl_->handle_register_le_callbacks(&mock_le_connection_callbacks_, handler_);

    Address address;
    Address::FromString(kFixedAddress, address);
    fixed_address_ = AddressWithType(address, AddressType::PUBLIC_DEVICE_ADDRESS);

    Address::FromString(kRemoteAddress, remote_address_);
    remote_public_address_with_type_ = AddressWithType(remote_address_, AddressType::PUBLIC_DEVICE_ADDRESS);

    Address::FromString(kLocalRandomAddress, local_rpa_);
    Address::FromString(kRemoteRandomAddress, remote_rpa_);
  }

  void set_random_device_address_policy() {
    // Set address policy
    ASSERT_NO_FATAL_FAILURE(hci_layer_->SetCommandFuture());
    hci::Address address;
    Address::FromString("D0:05:04:03:02:01", address);
    hci::AddressWithType address_with_type(address, hci::AddressType::RANDOM_DEVICE_ADDRESS);
    crypto_toolbox::Octet16 rotation_irk{};
    auto minimum_rotation_time = std::chrono::milliseconds(7 * 60 * 1000);
    auto maximum_rotation_time = std::chrono::milliseconds(15 * 60 * 1000);
    le_impl_->set_privacy_policy_for_initiator_address(
        LeAddressManager::AddressPolicy::USE_STATIC_ADDRESS,
        address_with_type,
        rotation_irk,
        minimum_rotation_time,
        maximum_rotation_time);
    hci_layer_->GetCommand(OpCode::LE_SET_RANDOM_ADDRESS);
    hci_layer_->CommandCompleteCallback(LeSetRandomAddressCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }

  void TearDown() override {
    // We cannot teardown our structure without unregistering
    // from our own structure we created.
    if (le_impl_->address_manager_registered) {
      le_impl_->ready_to_unregister = true;
      le_impl_->check_for_unregister();
      sync_handler();
    }

    sync_handler();
    delete le_impl_;

    hci_queue_.GetDownEnd()->UnregisterDequeue();

    delete hci_layer_;
    delete round_robin_scheduler_;
    delete controller_;

    handler_->Clear();
    delete handler_;
    delete thread_;
  }

  void sync_handler() {
    std::promise<void> promise;
    auto future = promise.get_future();
    handler_->BindOnceOn(&promise, &std::promise<void>::set_value).Invoke();
    auto status = future.wait_for(2s);
    ASSERT_EQ(status, std::future_status::ready);
  }

  void HciDownEndDequeue() {
    auto packet = hci_queue_.GetDownEnd()->TryDequeue();
    // Convert from a Builder to a View
    auto bytes = std::make_shared<std::vector<uint8_t>>();
    bluetooth::packet::BitInserter i(*bytes);
    bytes->reserve(packet->size());
    packet->Serialize(i);
    auto packet_view = bluetooth::packet::PacketView<bluetooth::packet::kLittleEndian>(bytes);
    AclView acl_packet_view = AclView::Create(packet_view);
    ASSERT_TRUE(acl_packet_view.IsValid());
    PacketView<true> count_view = acl_packet_view.GetPayload();
    sent_acl_packets_.push(acl_packet_view);

    packet_count_--;
    if (packet_count_ == 0) {
      packet_promise_->set_value();
      packet_promise_ = nullptr;
    }
  }

 protected:
  void set_privacy_policy_for_initiator_address(
      const AddressWithType& address, const LeAddressManager::AddressPolicy& policy) {
    le_impl_->set_privacy_policy_for_initiator_address(
        policy, address, kRotationIrk, kMinimumRotationTime, kMaximumRotationTime);
  }

  Address local_rpa_;
  Address remote_address_;
  Address remote_rpa_;
  AddressWithType fixed_address_;
  AddressWithType remote_public_address_;
  AddressWithType remote_public_address_with_type_;

  uint16_t packet_count_;
  std::unique_ptr<std::promise<void>> packet_promise_;
  std::unique_ptr<std::future<void>> packet_future_;
  std::queue<AclView> sent_acl_packets_;

  BidiQueue<AclView, AclBuilder> hci_queue_{3};

  Thread* thread_;
  Handler* handler_;
  TestHciLayer* hci_layer_{nullptr};
  TestController* controller_;
  RoundRobinScheduler* round_robin_scheduler_{nullptr};

  MockLeConnectionCallbacks mock_le_connection_callbacks_;
  MockLeConnectionManagementCallbacks connection_management_callbacks_;

  struct le_impl* le_impl_;
};

class LeImplRegisteredWithAddressManagerTest : public LeImplTest {
 protected:
  void SetUp() override {
    LeImplTest::SetUp();
    set_privacy_policy_for_initiator_address(fixed_address_, LeAddressManager::AddressPolicy::USE_PUBLIC_ADDRESS);

    le_impl_->register_with_address_manager();
    sync_handler();  // Let |LeAddressManager::register_client| execute on handler
    ASSERT_TRUE(le_impl_->address_manager_registered);
    ASSERT_TRUE(le_impl_->pause_connection);
  }

  void TearDown() override {
    LeImplTest::TearDown();
  }
};

class LeImplWithConnectionTest : public LeImplTest {
 protected:
  void SetUp() override {
    LeImplTest::SetUp();
    set_random_device_address_policy();

    EXPECT_CALL(mock_le_connection_callbacks_, OnLeConnectSuccess(_, _))
        .WillOnce([&](AddressWithType addr, std::unique_ptr<LeAclConnection> conn) {
          remote_address_with_type_ = addr;
          connection_ = std::move(conn);
          connection_->RegisterCallbacks(&connection_management_callbacks_, handler_);
        });

    auto command = LeEnhancedConnectionCompleteBuilder::Create(
        ErrorCode::SUCCESS,
        kHciHandle,
        Role::PERIPHERAL,
        AddressType::PUBLIC_DEVICE_ADDRESS,
        remote_address_,
        local_rpa_,
        remote_rpa_,
        0x0024,
        0x0000,
        0x0011,
        ClockAccuracy::PPM_30);
    auto bytes = Serialize<LeEnhancedConnectionCompleteBuilder>(std::move(command));
    auto view = CreateLeEventView<hci::LeEnhancedConnectionCompleteView>(bytes);
    ASSERT_TRUE(view.IsValid());
    le_impl_->on_le_event(view);

    sync_handler();
    ASSERT_EQ(remote_public_address_with_type_, remote_address_with_type_);
  }

  void TearDown() override {
    connection_.reset();
    LeImplTest::TearDown();
  }

  AddressWithType remote_address_with_type_;
  std::unique_ptr<LeAclConnection> connection_;
};

TEST_F(LeImplTest, add_device_to_connect_list) {
  le_impl_->add_device_to_connect_list({{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}, AddressType::PUBLIC_DEVICE_ADDRESS});
  ASSERT_EQ(1UL, le_impl_->connect_list.size());

  le_impl_->add_device_to_connect_list({{0x11, 0x12, 0x13, 0x14, 0x15, 0x16}, AddressType::PUBLIC_DEVICE_ADDRESS});
  ASSERT_EQ(2UL, le_impl_->connect_list.size());

  le_impl_->add_device_to_connect_list({{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}, AddressType::PUBLIC_DEVICE_ADDRESS});
  ASSERT_EQ(2UL, le_impl_->connect_list.size());

  le_impl_->add_device_to_connect_list({{0x11, 0x12, 0x13, 0x14, 0x15, 0x16}, AddressType::PUBLIC_DEVICE_ADDRESS});
  ASSERT_EQ(2UL, le_impl_->connect_list.size());
}

TEST_F(LeImplTest, remove_device_from_connect_list) {
  le_impl_->add_device_to_connect_list({{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}, AddressType::PUBLIC_DEVICE_ADDRESS});
  le_impl_->add_device_to_connect_list({{0x11, 0x12, 0x13, 0x14, 0x15, 0x16}, AddressType::PUBLIC_DEVICE_ADDRESS});
  le_impl_->add_device_to_connect_list({{0x21, 0x22, 0x23, 0x24, 0x25, 0x26}, AddressType::PUBLIC_DEVICE_ADDRESS});
  le_impl_->add_device_to_connect_list({{0x31, 0x32, 0x33, 0x34, 0x35, 0x36}, AddressType::PUBLIC_DEVICE_ADDRESS});
  ASSERT_EQ(4UL, le_impl_->connect_list.size());

  le_impl_->remove_device_from_connect_list({{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}, AddressType::PUBLIC_DEVICE_ADDRESS});
  ASSERT_EQ(3UL, le_impl_->connect_list.size());

  le_impl_->remove_device_from_connect_list({{0x11, 0x12, 0x13, 0x14, 0x15, 0x16}, AddressType::PUBLIC_DEVICE_ADDRESS});
  ASSERT_EQ(2UL, le_impl_->connect_list.size());

  le_impl_->remove_device_from_connect_list({{0x11, 0x12, 0x13, 0x14, 0x15, 0x16}, AddressType::PUBLIC_DEVICE_ADDRESS});
  ASSERT_EQ(2UL, le_impl_->connect_list.size());

  le_impl_->remove_device_from_connect_list({Address::kEmpty, AddressType::PUBLIC_DEVICE_ADDRESS});
  ASSERT_EQ(2UL, le_impl_->connect_list.size());

  le_impl_->remove_device_from_connect_list({{0x21, 0x22, 0x23, 0x24, 0x25, 0x26}, AddressType::PUBLIC_DEVICE_ADDRESS});
  le_impl_->remove_device_from_connect_list({{0x31, 0x32, 0x33, 0x34, 0x35, 0x36}, AddressType::PUBLIC_DEVICE_ADDRESS});
  ASSERT_EQ(0UL, le_impl_->connect_list.size());
}

TEST_F(LeImplTest, connection_complete_with_periperal_role) {
  set_random_device_address_policy();

  // Create connection
  ASSERT_NO_FATAL_FAILURE(hci_layer_->SetCommandFuture());
  le_impl_->create_le_connection(
      {{0x21, 0x22, 0x23, 0x24, 0x25, 0x26}, AddressType::PUBLIC_DEVICE_ADDRESS}, true, false);
  hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST);
  ASSERT_NO_FATAL_FAILURE(hci_layer_->SetCommandFuture());
  hci_layer_->CommandCompleteCallback(LeAddDeviceToFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  hci_layer_->GetCommand(OpCode::LE_CREATE_CONNECTION);
  hci_layer_->CommandStatusCallback(LeCreateConnectionStatusBuilder::Create(ErrorCode::SUCCESS, 0x01));
  sync_handler();

  // Check state is ARMED
  ASSERT_EQ(ConnectabilityState::ARMED, le_impl_->connectability_state_);

  // Receive connection complete of incoming connection (Role::PERIPHERAL)
  hci::Address remote_address;
  Address::FromString("D0:05:04:03:02:01", remote_address);
  hci::AddressWithType address_with_type(remote_address, hci::AddressType::PUBLIC_DEVICE_ADDRESS);
  EXPECT_CALL(mock_le_connection_callbacks_, OnLeConnectSuccess(address_with_type, _));
  hci_layer_->IncomingLeMetaEvent(LeConnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS,
      0x0041,
      Role::PERIPHERAL,
      AddressType::PUBLIC_DEVICE_ADDRESS,
      remote_address,
      0x0024,
      0x0000,
      0x0011,
      ClockAccuracy::PPM_30));
  sync_handler();

  // Check state is still ARMED
  ASSERT_EQ(ConnectabilityState::ARMED, le_impl_->connectability_state_);
}

TEST_F(LeImplTest, enhanced_connection_complete_with_periperal_role) {
  set_random_device_address_policy();

  controller_->AddSupported(OpCode::LE_EXTENDED_CREATE_CONNECTION);
  // Create connection
  ASSERT_NO_FATAL_FAILURE(hci_layer_->SetCommandFuture());
  le_impl_->create_le_connection(
      {{0x21, 0x22, 0x23, 0x24, 0x25, 0x26}, AddressType::PUBLIC_DEVICE_ADDRESS}, true, false);
  hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST);
  ASSERT_NO_FATAL_FAILURE(hci_layer_->SetCommandFuture());
  hci_layer_->CommandCompleteCallback(LeAddDeviceToFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  hci_layer_->GetCommand(OpCode::LE_EXTENDED_CREATE_CONNECTION);
  hci_layer_->CommandStatusCallback(LeExtendedCreateConnectionStatusBuilder::Create(ErrorCode::SUCCESS, 0x01));
  sync_handler();

  // Check state is ARMED
  ASSERT_EQ(ConnectabilityState::ARMED, le_impl_->connectability_state_);

  // Receive connection complete of incoming connection (Role::PERIPHERAL)
  hci::Address remote_address;
  Address::FromString("D0:05:04:03:02:01", remote_address);
  hci::AddressWithType address_with_type(remote_address, hci::AddressType::PUBLIC_DEVICE_ADDRESS);
  EXPECT_CALL(mock_le_connection_callbacks_, OnLeConnectSuccess(address_with_type, _));
  hci_layer_->IncomingLeMetaEvent(LeEnhancedConnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS,
      0x0041,
      Role::PERIPHERAL,
      AddressType::PUBLIC_DEVICE_ADDRESS,
      remote_address,
      Address::kEmpty,
      Address::kEmpty,
      0x0024,
      0x0000,
      0x0011,
      ClockAccuracy::PPM_30));
  sync_handler();

  // Check state is still ARMED
  ASSERT_EQ(ConnectabilityState::ARMED, le_impl_->connectability_state_);
}

TEST_F(LeImplTest, connection_complete_with_central_role) {
  set_random_device_address_policy();

  hci::Address remote_address;
  Address::FromString("D0:05:04:03:02:01", remote_address);
  hci::AddressWithType address_with_type(remote_address, hci::AddressType::PUBLIC_DEVICE_ADDRESS);
  // Create connection
  ASSERT_NO_FATAL_FAILURE(hci_layer_->SetCommandFuture());
  le_impl_->create_le_connection(address_with_type, true, false);
  hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST);
  ASSERT_NO_FATAL_FAILURE(hci_layer_->SetCommandFuture());
  hci_layer_->CommandCompleteCallback(LeAddDeviceToFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  hci_layer_->GetCommand(OpCode::LE_CREATE_CONNECTION);
  hci_layer_->CommandStatusCallback(LeCreateConnectionStatusBuilder::Create(ErrorCode::SUCCESS, 0x01));
  sync_handler();

  // Check state is ARMED
  ASSERT_EQ(ConnectabilityState::ARMED, le_impl_->connectability_state_);

  // Receive connection complete of outgoing connection (Role::CENTRAL)
  EXPECT_CALL(mock_le_connection_callbacks_, OnLeConnectSuccess(address_with_type, _));
  hci_layer_->IncomingLeMetaEvent(LeConnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS,
      0x0041,
      Role::CENTRAL,
      AddressType::PUBLIC_DEVICE_ADDRESS,
      remote_address,
      0x0024,
      0x0000,
      0x0011,
      ClockAccuracy::PPM_30));
  sync_handler();

  // Check state is DISARMED
  ASSERT_EQ(ConnectabilityState::DISARMED, le_impl_->connectability_state_);
}

TEST_F(LeImplTest, enhanced_connection_complete_with_central_role) {
  set_random_device_address_policy();

  controller_->AddSupported(OpCode::LE_EXTENDED_CREATE_CONNECTION);
  hci::Address remote_address;
  Address::FromString("D0:05:04:03:02:01", remote_address);
  hci::AddressWithType address_with_type(remote_address, hci::AddressType::PUBLIC_DEVICE_ADDRESS);
  // Create connection
  ASSERT_NO_FATAL_FAILURE(hci_layer_->SetCommandFuture());
  le_impl_->create_le_connection(address_with_type, true, false);
  hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST);
  ASSERT_NO_FATAL_FAILURE(hci_layer_->SetCommandFuture());
  hci_layer_->CommandCompleteCallback(LeAddDeviceToFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  hci_layer_->GetCommand(OpCode::LE_EXTENDED_CREATE_CONNECTION);
  hci_layer_->CommandStatusCallback(LeExtendedCreateConnectionStatusBuilder::Create(ErrorCode::SUCCESS, 0x01));
  sync_handler();

  // Check state is ARMED
  ASSERT_EQ(ConnectabilityState::ARMED, le_impl_->connectability_state_);

  // Receive connection complete of outgoing connection (Role::CENTRAL)
  EXPECT_CALL(mock_le_connection_callbacks_, OnLeConnectSuccess(address_with_type, _));
  hci_layer_->IncomingLeMetaEvent(LeEnhancedConnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS,
      0x0041,
      Role::CENTRAL,
      AddressType::PUBLIC_DEVICE_ADDRESS,
      remote_address,
      Address::kEmpty,
      Address::kEmpty,
      0x0024,
      0x0000,
      0x0011,
      ClockAccuracy::PPM_30));
  sync_handler();

  // Check state is DISARMED
  ASSERT_EQ(ConnectabilityState::DISARMED, le_impl_->connectability_state_);
}

// b/260917913
TEST_F(LeImplTest, DISABLED_register_with_address_manager__AddressPolicyNotSet) {
  auto log_capture = std::make_unique<LogCapture>();

  std::promise<void> promise;
  auto future = promise.get_future();
  handler_->Post(common::BindOnce(
      [](struct le_impl* le_impl, os::Handler* handler, std::promise<void> promise) {
        le_impl->register_with_address_manager();
        handler->Post(common::BindOnce([](std::promise<void> promise) { promise.set_value(); }, std::move(promise)));
      },
      le_impl_,
      handler_,
      std::move(promise)));

  // Let |LeAddressManager::register_client| execute on handler
  auto status = future.wait_for(2s);
  ASSERT_EQ(status, std::future_status::ready);

  handler_->Post(common::BindOnce(
      [](struct le_impl* le_impl) {
        ASSERT_TRUE(le_impl->address_manager_registered);
        ASSERT_TRUE(le_impl->pause_connection);
      },
      le_impl_));

  std::promise<void> promise2;
  auto future2 = promise2.get_future();
  handler_->Post(common::BindOnce(
      [](struct le_impl* le_impl, os::Handler* handler, std::promise<void> promise) {
        le_impl->ready_to_unregister = true;
        le_impl->check_for_unregister();
        ASSERT_FALSE(le_impl->address_manager_registered);
        ASSERT_FALSE(le_impl->pause_connection);
        handler->Post(common::BindOnce([](std::promise<void> promise) { promise.set_value(); }, std::move(promise)));
      },
      le_impl_,
      handler_,
      std::move(promise2)));

  // Let |LeAddressManager::unregister_client| execute on handler
  auto status2 = future2.wait_for(2s);
  ASSERT_EQ(status2, std::future_status::ready);

  handler_->Post(common::BindOnce(
      [](std::unique_ptr<LogCapture> log_capture) {
        log_capture->Sync();
        ASSERT_TRUE(log_capture->Rewind()->Find("address policy isn't set yet"));
        ASSERT_TRUE(log_capture->Rewind()->Find("Client unregistered"));
      },
      std::move(log_capture)));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_disarm_connectability_DISARMED) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  le_impl_->connectability_state_ = ConnectabilityState::DISARMED;
  le_impl_->disarm_connectability();
  ASSERT_FALSE(le_impl_->disarmed_while_arming_);

  le_impl_->on_create_connection(ReturnCommandStatus(OpCode::LE_CREATE_CONNECTION, ErrorCode::SUCCESS));

  ASSERT_TRUE(log_capture->Rewind()->Find("Attempting to disarm le connection"));
  ASSERT_TRUE(log_capture->Rewind()->Find("in unexpected state:ConnectabilityState::DISARMED"));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_disarm_connectability_DISARMED_extended) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  le_impl_->connectability_state_ = ConnectabilityState::DISARMED;
  le_impl_->disarm_connectability();
  ASSERT_FALSE(le_impl_->disarmed_while_arming_);

  le_impl_->on_extended_create_connection(
      ReturnCommandStatus(OpCode::LE_EXTENDED_CREATE_CONNECTION, ErrorCode::SUCCESS));

  ASSERT_TRUE(log_capture->Rewind()->Find("Attempting to disarm le connection"));
  ASSERT_TRUE(log_capture->Rewind()->Find("in unexpected state:ConnectabilityState::DISARMED"));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_disarm_connectability_ARMING) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  le_impl_->connectability_state_ = ConnectabilityState::ARMING;
  le_impl_->disarm_connectability();
  ASSERT_TRUE(le_impl_->disarmed_while_arming_);
  le_impl_->on_create_connection(ReturnCommandStatus(OpCode::LE_CREATE_CONNECTION, ErrorCode::SUCCESS));

  ASSERT_TRUE(log_capture->Rewind()->Find("Queueing cancel connect until"));
  ASSERT_TRUE(log_capture->Rewind()->Find("Le connection state machine armed state"));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_disarm_connectability_ARMING_extended) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  le_impl_->connectability_state_ = ConnectabilityState::ARMING;
  le_impl_->disarm_connectability();
  ASSERT_TRUE(le_impl_->disarmed_while_arming_);

  le_impl_->on_extended_create_connection(
      ReturnCommandStatus(OpCode::LE_EXTENDED_CREATE_CONNECTION, ErrorCode::SUCCESS));

  ASSERT_TRUE(log_capture->Rewind()->Find("Queueing cancel connect until"));
  ASSERT_TRUE(log_capture->Rewind()->Find("Le connection state machine armed state"));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_disarm_connectability_ARMED) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  le_impl_->connectability_state_ = ConnectabilityState::ARMED;
  le_impl_->disarm_connectability();
  ASSERT_FALSE(le_impl_->disarmed_while_arming_);

  le_impl_->on_create_connection(ReturnCommandStatus(OpCode::LE_CREATE_CONNECTION, ErrorCode::SUCCESS));

  ASSERT_TRUE(log_capture->Rewind()->Find("Disarming LE connection state machine"));
  ASSERT_TRUE(log_capture->Rewind()->Find("Disarming LE connection state machine with create connection"));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_disarm_connectability_ARMED_extended) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  le_impl_->connectability_state_ = ConnectabilityState::ARMED;
  le_impl_->disarm_connectability();
  ASSERT_FALSE(le_impl_->disarmed_while_arming_);

  le_impl_->on_extended_create_connection(
      ReturnCommandStatus(OpCode::LE_EXTENDED_CREATE_CONNECTION, ErrorCode::SUCCESS));

  ASSERT_TRUE(log_capture->Rewind()->Find("Disarming LE connection state machine"));
  ASSERT_TRUE(log_capture->Rewind()->Find("Disarming LE connection state machine with create connection"));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_disarm_connectability_DISARMING) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  le_impl_->connectability_state_ = ConnectabilityState::DISARMING;
  le_impl_->disarm_connectability();
  ASSERT_FALSE(le_impl_->disarmed_while_arming_);

  le_impl_->on_create_connection(ReturnCommandStatus(OpCode::LE_CREATE_CONNECTION, ErrorCode::SUCCESS));

  ASSERT_TRUE(log_capture->Rewind()->Find("Attempting to disarm le connection"));
  ASSERT_TRUE(log_capture->Rewind()->Find("in unexpected state:ConnectabilityState::DISARMING"));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_disarm_connectability_DISARMING_extended) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  le_impl_->connectability_state_ = ConnectabilityState::DISARMING;
  le_impl_->disarm_connectability();
  ASSERT_FALSE(le_impl_->disarmed_while_arming_);

  le_impl_->on_extended_create_connection(
      ReturnCommandStatus(OpCode::LE_EXTENDED_CREATE_CONNECTION, ErrorCode::SUCCESS));

  ASSERT_TRUE(log_capture->Rewind()->Find("Attempting to disarm le connection"));
  ASSERT_TRUE(log_capture->Rewind()->Find("in unexpected state:ConnectabilityState::DISARMING"));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_register_with_address_manager__AddressPolicyPublicAddress) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  set_privacy_policy_for_initiator_address(fixed_address_, LeAddressManager::AddressPolicy::USE_PUBLIC_ADDRESS);

  le_impl_->register_with_address_manager();
  sync_handler();  // Let |eAddressManager::register_client| execute on handler
  ASSERT_TRUE(le_impl_->address_manager_registered);
  ASSERT_TRUE(le_impl_->pause_connection);

  le_impl_->ready_to_unregister = true;

  le_impl_->check_for_unregister();
  sync_handler();  // Let |LeAddressManager::unregister_client| execute on handler
  ASSERT_FALSE(le_impl_->address_manager_registered);
  ASSERT_FALSE(le_impl_->pause_connection);

  ASSERT_TRUE(log_capture->Rewind()->Find("SetPrivacyPolicyForInitiatorAddress with policy 1"));
  ASSERT_TRUE(log_capture->Rewind()->Find("Client unregistered"));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_register_with_address_manager__AddressPolicyStaticAddress) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  set_privacy_policy_for_initiator_address(fixed_address_, LeAddressManager::AddressPolicy::USE_STATIC_ADDRESS);

  le_impl_->register_with_address_manager();
  sync_handler();  // Let |LeAddressManager::register_client| execute on handler
  ASSERT_TRUE(le_impl_->address_manager_registered);
  ASSERT_TRUE(le_impl_->pause_connection);

  le_impl_->ready_to_unregister = true;

  le_impl_->check_for_unregister();
  sync_handler();  // Let |LeAddressManager::unregister_client| execute on handler
  ASSERT_FALSE(le_impl_->address_manager_registered);
  ASSERT_FALSE(le_impl_->pause_connection);

  ASSERT_TRUE(log_capture->Rewind()->Find("SetPrivacyPolicyForInitiatorAddress with policy 2"));
  ASSERT_TRUE(log_capture->Rewind()->Find("Client unregistered"));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_register_with_address_manager__AddressPolicyNonResolvableAddress) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  set_privacy_policy_for_initiator_address(fixed_address_, LeAddressManager::AddressPolicy::USE_NON_RESOLVABLE_ADDRESS);

  le_impl_->register_with_address_manager();
  sync_handler();  // Let |LeAddressManager::register_client| execute on handler
  ASSERT_TRUE(le_impl_->address_manager_registered);
  ASSERT_TRUE(le_impl_->pause_connection);

  le_impl_->ready_to_unregister = true;

  le_impl_->check_for_unregister();
  sync_handler();  // Let |LeAddressManager::unregister_client| execute on handler
  ASSERT_FALSE(le_impl_->address_manager_registered);
  ASSERT_FALSE(le_impl_->pause_connection);

  ASSERT_TRUE(log_capture->Rewind()->Find("SetPrivacyPolicyForInitiatorAddress with policy 3"));
  ASSERT_TRUE(log_capture->Rewind()->Find("Client unregistered"));
}

// b/260917913
TEST_F(LeImplTest, DISABLED_register_with_address_manager__AddressPolicyResolvableAddress) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  set_privacy_policy_for_initiator_address(fixed_address_, LeAddressManager::AddressPolicy::USE_RESOLVABLE_ADDRESS);

  le_impl_->register_with_address_manager();
  sync_handler();  // Let |LeAddressManager::register_client| execute on handler
  ASSERT_TRUE(le_impl_->address_manager_registered);
  ASSERT_TRUE(le_impl_->pause_connection);

  le_impl_->ready_to_unregister = true;

  le_impl_->check_for_unregister();
  sync_handler();  // Let |LeAddressManager::unregister_client| execute on handler
  ASSERT_FALSE(le_impl_->address_manager_registered);
  ASSERT_FALSE(le_impl_->pause_connection);

  ASSERT_TRUE(log_capture->Rewind()->Find("SetPrivacyPolicyForInitiatorAddress with policy 4"));
  ASSERT_TRUE(log_capture->Rewind()->Find("Client unregistered"));
}

// b/260920739
TEST_F(LeImplTest, DISABLED_add_device_to_resolving_list) {
  // Some kind of privacy policy must be set for LeAddressManager to operate properly
  set_privacy_policy_for_initiator_address(fixed_address_, LeAddressManager::AddressPolicy::USE_PUBLIC_ADDRESS);
  // Let LeAddressManager::resume_registered_clients execute
  sync_handler();

  ASSERT_EQ(0UL, hci_layer_->NumberOfQueuedCommands());

  // le_impl should not be registered with address manager
  ASSERT_FALSE(le_impl_->address_manager_registered);
  ASSERT_FALSE(le_impl_->pause_connection);

  ASSERT_EQ(0UL, le_impl_->le_address_manager_->NumberCachedCommands());
  // Acknowledge that the le_impl has quiesced all relevant controller state
  le_impl_->add_device_to_resolving_list(
      remote_public_address_with_type_, kPeerIdentityResolvingKey, kLocalIdentityResolvingKey);
  ASSERT_EQ(3UL, le_impl_->le_address_manager_->NumberCachedCommands());

  sync_handler();  // Let |LeAddressManager::register_client| execute on handler
  ASSERT_TRUE(le_impl_->address_manager_registered);
  ASSERT_TRUE(le_impl_->pause_connection);

  le_impl_->le_address_manager_->AckPause(le_impl_);
  sync_handler();  // Allow |LeAddressManager::ack_pause| to complete

  ASSERT_FALSE(hci_layer_->IsPacketQueueEmpty());
  {
    // Inform controller to disable address resolution
    auto command = CreateLeSecurityCommandView<LeSetAddressResolutionEnableView>(hci_layer_->DequeueCommandBytes());
    ASSERT_TRUE(command.IsValid());
    ASSERT_EQ(Enable::DISABLED, command.GetAddressResolutionEnable());
    le_impl_->le_address_manager_->OnCommandComplete(
        ReturnCommandComplete(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE, ErrorCode::SUCCESS));
  }
  sync_handler();  // |LeAddressManager::check_cached_commands|

  ASSERT_FALSE(hci_layer_->IsPacketQueueEmpty());
  {
    auto command = CreateLeSecurityCommandView<LeAddDeviceToResolvingListView>(hci_layer_->DequeueCommandBytes());
    ASSERT_TRUE(command.IsValid());
    ASSERT_EQ(PeerAddressType::PUBLIC_DEVICE_OR_IDENTITY_ADDRESS, command.GetPeerIdentityAddressType());
    ASSERT_EQ(remote_public_address_with_type_.GetAddress(), command.GetPeerIdentityAddress());
    ASSERT_EQ(kPeerIdentityResolvingKey, command.GetPeerIrk());
    ASSERT_EQ(kLocalIdentityResolvingKey, command.GetLocalIrk());
    le_impl_->le_address_manager_->OnCommandComplete(
        ReturnCommandComplete(OpCode::LE_ADD_DEVICE_TO_RESOLVING_LIST, ErrorCode::SUCCESS));
  }
  sync_handler();  // |LeAddressManager::check_cached_commands|

  ASSERT_FALSE(hci_layer_->IsPacketQueueEmpty());
  {
    auto command = CreateLeSecurityCommandView<LeSetAddressResolutionEnableView>(hci_layer_->DequeueCommandBytes());
    ASSERT_TRUE(command.IsValid());
    ASSERT_EQ(Enable::ENABLED, command.GetAddressResolutionEnable());
    le_impl_->le_address_manager_->OnCommandComplete(
        ReturnCommandComplete(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE, ErrorCode::SUCCESS));
  }
  sync_handler();  // |LeAddressManager::check_cached_commands|

  ASSERT_TRUE(hci_layer_->IsPacketQueueEmpty());
  ASSERT_TRUE(le_impl_->address_manager_registered);

  le_impl_->ready_to_unregister = true;

  le_impl_->check_for_unregister();
  sync_handler();
  ASSERT_FALSE(le_impl_->address_manager_registered);
  ASSERT_FALSE(le_impl_->pause_connection);
}

TEST_F(LeImplTest, add_device_to_resolving_list__SupportsBlePrivacy) {
  controller_->supports_ble_privacy_ = true;

  // Some kind of privacy policy must be set for LeAddressManager to operate properly
  set_privacy_policy_for_initiator_address(fixed_address_, LeAddressManager::AddressPolicy::USE_PUBLIC_ADDRESS);
  // Let LeAddressManager::resume_registered_clients execute
  sync_handler();

  ASSERT_EQ(0UL, hci_layer_->NumberOfQueuedCommands());

  // le_impl should not be registered with address manager
  ASSERT_FALSE(le_impl_->address_manager_registered);
  ASSERT_FALSE(le_impl_->pause_connection);

  ASSERT_EQ(0UL, le_impl_->le_address_manager_->NumberCachedCommands());
  // Acknowledge that the le_impl has quiesced all relevant controller state
  le_impl_->add_device_to_resolving_list(
      remote_public_address_with_type_, kPeerIdentityResolvingKey, kLocalIdentityResolvingKey);
  ASSERT_EQ(4UL, le_impl_->le_address_manager_->NumberCachedCommands());

  sync_handler();  // Let |LeAddressManager::register_client| execute on handler
  ASSERT_TRUE(le_impl_->address_manager_registered);
  ASSERT_TRUE(le_impl_->pause_connection);

  le_impl_->le_address_manager_->AckPause(le_impl_);
  sync_handler();  // Allow |LeAddressManager::ack_pause| to complete

  ASSERT_FALSE(hci_layer_->IsPacketQueueEmpty());
  {
    // Inform controller to disable address resolution
    auto command = CreateLeSecurityCommandView<LeSetAddressResolutionEnableView>(hci_layer_->DequeueCommandBytes());
    ASSERT_TRUE(command.IsValid());
    ASSERT_EQ(Enable::DISABLED, command.GetAddressResolutionEnable());
    le_impl_->le_address_manager_->OnCommandComplete(
        ReturnCommandComplete(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE, ErrorCode::SUCCESS));
  }
  sync_handler();  // |LeAddressManager::check_cached_commands|

  ASSERT_FALSE(hci_layer_->IsPacketQueueEmpty());
  {
    auto command = CreateLeSecurityCommandView<LeAddDeviceToResolvingListView>(hci_layer_->DequeueCommandBytes());
    ASSERT_TRUE(command.IsValid());
    ASSERT_EQ(PeerAddressType::PUBLIC_DEVICE_OR_IDENTITY_ADDRESS, command.GetPeerIdentityAddressType());
    ASSERT_EQ(remote_public_address_with_type_.GetAddress(), command.GetPeerIdentityAddress());
    ASSERT_EQ(kPeerIdentityResolvingKey, command.GetPeerIrk());
    ASSERT_EQ(kLocalIdentityResolvingKey, command.GetLocalIrk());
    le_impl_->le_address_manager_->OnCommandComplete(
        ReturnCommandComplete(OpCode::LE_ADD_DEVICE_TO_RESOLVING_LIST, ErrorCode::SUCCESS));
  }
  sync_handler();  // |LeAddressManager::check_cached_commands|

  ASSERT_FALSE(hci_layer_->IsPacketQueueEmpty());
  {
    auto command = CreateLeSecurityCommandView<LeSetPrivacyModeView>(hci_layer_->DequeueCommandBytes());
    ASSERT_TRUE(command.IsValid());
    ASSERT_EQ(PrivacyMode::DEVICE, command.GetPrivacyMode());
    ASSERT_EQ(remote_public_address_with_type_.GetAddress(), command.GetPeerIdentityAddress());
    ASSERT_EQ(PeerAddressType::PUBLIC_DEVICE_OR_IDENTITY_ADDRESS, command.GetPeerIdentityAddressType());
    le_impl_->le_address_manager_->OnCommandComplete(
        ReturnCommandComplete(OpCode::LE_SET_PRIVACY_MODE, ErrorCode::SUCCESS));
  }
  sync_handler();  // |LeAddressManager::check_cached_commands|

  ASSERT_FALSE(hci_layer_->IsPacketQueueEmpty());
  {
    auto command = CreateLeSecurityCommandView<LeSetAddressResolutionEnableView>(hci_layer_->DequeueCommandBytes());
    ASSERT_TRUE(command.IsValid());
    ASSERT_EQ(Enable::ENABLED, command.GetAddressResolutionEnable());
    le_impl_->le_address_manager_->OnCommandComplete(
        ReturnCommandComplete(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE, ErrorCode::SUCCESS));
  }
  sync_handler();  // |LeAddressManager::check_cached_commands|

  ASSERT_TRUE(hci_layer_->IsPacketQueueEmpty());
  ASSERT_TRUE(le_impl_->address_manager_registered);

  le_impl_->ready_to_unregister = true;

  le_impl_->check_for_unregister();
  sync_handler();
  ASSERT_FALSE(le_impl_->address_manager_registered);
  ASSERT_FALSE(le_impl_->pause_connection);
}

TEST_F(LeImplTest, connectability_state_machine_text) {
  ASSERT_STREQ(
      "ConnectabilityState::DISARMED", connectability_state_machine_text(ConnectabilityState::DISARMED).c_str());
  ASSERT_STREQ("ConnectabilityState::ARMING", connectability_state_machine_text(ConnectabilityState::ARMING).c_str());
  ASSERT_STREQ("ConnectabilityState::ARMED", connectability_state_machine_text(ConnectabilityState::ARMED).c_str());
  ASSERT_STREQ(
      "ConnectabilityState::DISARMING", connectability_state_machine_text(ConnectabilityState::DISARMING).c_str());
}

TEST_F(LeImplTest, on_le_event__CONNECTION_COMPLETE_CENTRAL) {
  EXPECT_CALL(mock_le_connection_callbacks_, OnLeConnectSuccess(_, _)).Times(1);
  set_random_device_address_policy();
  auto command = LeConnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS,
      kHciHandle,
      Role::CENTRAL,
      AddressType::PUBLIC_DEVICE_ADDRESS,
      remote_address_,
      0x0024,
      0x0000,
      0x0011,
      ClockAccuracy::PPM_30);
  auto bytes = Serialize<LeConnectionCompleteBuilder>(std::move(command));
  auto view = CreateLeEventView<hci::LeConnectionCompleteView>(bytes);
  ASSERT_TRUE(view.IsValid());
  le_impl_->on_le_event(view);
}

TEST_F(LeImplTest, on_le_event__CONNECTION_COMPLETE_PERIPHERAL) {
  EXPECT_CALL(mock_le_connection_callbacks_, OnLeConnectSuccess(_, _)).Times(1);
  set_random_device_address_policy();
  auto command = LeConnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS,
      kHciHandle,
      Role::PERIPHERAL,
      AddressType::PUBLIC_DEVICE_ADDRESS,
      remote_address_,
      0x0024,
      0x0000,
      0x0011,
      ClockAccuracy::PPM_30);
  auto bytes = Serialize<LeConnectionCompleteBuilder>(std::move(command));
  auto view = CreateLeEventView<hci::LeConnectionCompleteView>(bytes);
  ASSERT_TRUE(view.IsValid());
  le_impl_->on_le_event(view);
}

TEST_F(LeImplTest, on_le_event__ENHANCED_CONNECTION_COMPLETE_CENTRAL) {
  EXPECT_CALL(mock_le_connection_callbacks_, OnLeConnectSuccess(_, _)).Times(1);
  set_random_device_address_policy();
  auto command = LeEnhancedConnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS,
      kHciHandle,
      Role::CENTRAL,
      AddressType::PUBLIC_DEVICE_ADDRESS,
      remote_address_,
      local_rpa_,
      remote_rpa_,
      0x0024,
      0x0000,
      0x0011,
      ClockAccuracy::PPM_30);
  auto bytes = Serialize<LeEnhancedConnectionCompleteBuilder>(std::move(command));
  auto view = CreateLeEventView<hci::LeEnhancedConnectionCompleteView>(bytes);
  ASSERT_TRUE(view.IsValid());
  le_impl_->on_le_event(view);
}

TEST_F(LeImplTest, on_le_event__ENHANCED_CONNECTION_COMPLETE_PERIPHERAL) {
  EXPECT_CALL(mock_le_connection_callbacks_, OnLeConnectSuccess(_, _)).Times(1);
  set_random_device_address_policy();
  auto command = LeEnhancedConnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS,
      kHciHandle,
      Role::PERIPHERAL,
      AddressType::PUBLIC_DEVICE_ADDRESS,
      remote_address_,
      local_rpa_,
      remote_rpa_,
      0x0024,
      0x0000,
      0x0011,
      ClockAccuracy::PPM_30);
  auto bytes = Serialize<LeEnhancedConnectionCompleteBuilder>(std::move(command));
  auto view = CreateLeEventView<hci::LeEnhancedConnectionCompleteView>(bytes);
  ASSERT_TRUE(view.IsValid());
  le_impl_->on_le_event(view);
}

TEST_F(LeImplRegisteredWithAddressManagerTest, ignore_on_pause_on_resume_after_unregistered) {
  le_impl_->ready_to_unregister = true;
  le_impl_->check_for_unregister();
  // OnPause should be ignored
  le_impl_->OnPause();
  ASSERT_FALSE(le_impl_->pause_connection);
  // OnResume should be ignored
  le_impl_->pause_connection = true;
  le_impl_->OnResume();
  ASSERT_TRUE(le_impl_->pause_connection);
}

TEST_F(LeImplWithConnectionTest, on_le_event__PHY_UPDATE_COMPLETE) {
  hci::ErrorCode hci_status{ErrorCode::STATUS_UNKNOWN};
  hci::PhyType tx_phy{0};
  hci::PhyType rx_phy{0};

  // Send a phy update
  {
    EXPECT_CALL(connection_management_callbacks_, OnPhyUpdate(_, _, _))
        .WillOnce([&](hci::ErrorCode _hci_status, uint8_t _tx_phy, uint8_t _rx_phy) {
          hci_status = _hci_status;
          tx_phy = static_cast<PhyType>(_tx_phy);
          rx_phy = static_cast<PhyType>(_rx_phy);
        });
    auto command = LePhyUpdateCompleteBuilder::Create(ErrorCode::SUCCESS, kHciHandle, 0x01, 0x02);
    auto bytes = Serialize<LePhyUpdateCompleteBuilder>(std::move(command));
    auto view = CreateLeEventView<hci::LePhyUpdateCompleteView>(bytes);
    ASSERT_TRUE(view.IsValid());
    le_impl_->on_le_event(view);
  }

  sync_handler();
  ASSERT_EQ(ErrorCode::SUCCESS, hci_status);
  ASSERT_EQ(PhyType::LE_1M, tx_phy);
  ASSERT_EQ(PhyType::LE_2M, rx_phy);
}

TEST_F(LeImplWithConnectionTest, on_le_event__DATA_LENGTH_CHANGE) {
  uint16_t tx_octets{0};
  uint16_t tx_time{0};
  uint16_t rx_octets{0};
  uint16_t rx_time{0};

  // Send a data length event
  {
    EXPECT_CALL(connection_management_callbacks_, OnDataLengthChange(_, _, _, _))
        .WillOnce([&](uint16_t _tx_octets, uint16_t _tx_time, uint16_t _rx_octets, uint16_t _rx_time) {
          tx_octets = _tx_octets;
          tx_time = _tx_time;
          rx_octets = _rx_octets;
          rx_time = _rx_time;
        });
    auto command = LeDataLengthChangeBuilder::Create(kHciHandle, 0x1234, 0x5678, 0x9abc, 0xdef0);
    auto bytes = Serialize<LeDataLengthChangeBuilder>(std::move(command));
    auto view = CreateLeEventView<hci::LeDataLengthChangeView>(bytes);
    ASSERT_TRUE(view.IsValid());
    le_impl_->on_le_event(view);
  }

  sync_handler();
  ASSERT_EQ(0x1234, tx_octets);
  ASSERT_EQ(0x5678, tx_time);
  ASSERT_EQ(0x9abc, rx_octets);
  ASSERT_EQ(0xdef0, rx_time);
}

TEST_F(LeImplWithConnectionTest, on_le_event__REMOTE_CONNECTION_PARAMETER_REQUEST) {
  // Send a remote connection parameter request
  auto command = hci::LeRemoteConnectionParameterRequestBuilder::Create(
      kHciHandle, kIntervalMin, kIntervalMax, kLatency, kTimeout);
  auto bytes = Serialize<LeRemoteConnectionParameterRequestBuilder>(std::move(command));
  {
    auto view = CreateLeEventView<hci::LeRemoteConnectionParameterRequestView>(bytes);
    ASSERT_TRUE(view.IsValid());
    le_impl_->on_le_event(view);
  }

  sync_handler();

  ASSERT_FALSE(hci_layer_->IsPacketQueueEmpty());

  auto view = CreateLeConnectionManagementCommandView<LeRemoteConnectionParameterRequestReplyView>(
      hci_layer_->DequeueCommandBytes());
  ASSERT_TRUE(view.IsValid());

  ASSERT_EQ(kIntervalMin, view.GetIntervalMin());
  ASSERT_EQ(kIntervalMax, view.GetIntervalMax());
  ASSERT_EQ(kLatency, view.GetLatency());
  ASSERT_EQ(kTimeout, view.GetTimeout());
}

// b/260920739
TEST_F(LeImplRegisteredWithAddressManagerTest, DISABLED_clear_resolving_list) {
  le_impl_->clear_resolving_list();
  ASSERT_EQ(3UL, le_impl_->le_address_manager_->NumberCachedCommands());

  sync_handler();  // Allow |LeAddressManager::pause_registered_clients| to complete
  sync_handler();  // Allow |LeAddressManager::handle_next_command| to complete

  ASSERT_EQ(1UL, hci_layer_->NumberOfQueuedCommands());
  {
    auto view = CreateLeSecurityCommandView<LeSetAddressResolutionEnableView>(hci_layer_->DequeueCommandBytes());
    ASSERT_TRUE(view.IsValid());
    ASSERT_EQ(Enable::DISABLED, view.GetAddressResolutionEnable());
    le_impl_->le_address_manager_->OnCommandComplete(
        ReturnCommandComplete(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE, ErrorCode::SUCCESS));
  }

  sync_handler();  // Allow |LeAddressManager::check_cached_commands| to complete
  ASSERT_EQ(1UL, hci_layer_->NumberOfQueuedCommands());
  {
    auto view = CreateLeSecurityCommandView<LeClearResolvingListView>(hci_layer_->DequeueCommandBytes());
    ASSERT_TRUE(view.IsValid());
    le_impl_->le_address_manager_->OnCommandComplete(
        ReturnCommandComplete(OpCode::LE_CLEAR_RESOLVING_LIST, ErrorCode::SUCCESS));
  }

  sync_handler();  // Allow |LeAddressManager::handle_next_command| to complete
  ASSERT_EQ(1UL, hci_layer_->NumberOfQueuedCommands());
  {
    auto view = CreateLeSecurityCommandView<LeSetAddressResolutionEnableView>(hci_layer_->DequeueCommandBytes());
    ASSERT_TRUE(view.IsValid());
    ASSERT_EQ(Enable::ENABLED, view.GetAddressResolutionEnable());
    le_impl_->le_address_manager_->OnCommandComplete(
        ReturnCommandComplete(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE, ErrorCode::SUCCESS));
  }
  ASSERT_TRUE(hci_layer_->IsPacketQueueEmpty());
}

TEST_F(LeImplWithConnectionTest, HACK_get_handle) {
  sync_handler();

  ASSERT_EQ(kHciHandle, le_impl_->HACK_get_handle(remote_address_));
}

TEST_F(LeImplTest, on_le_connection_canceled_on_pause) {
  set_random_device_address_policy();
  le_impl_->pause_connection = true;
  le_impl_->on_le_connection_canceled_on_pause();
  ASSERT_TRUE(le_impl_->arm_on_resume_);
  ASSERT_EQ(ConnectabilityState::DISARMED, le_impl_->connectability_state_);
}

TEST_F(LeImplTest, on_create_connection_timeout) {
  EXPECT_CALL(mock_le_connection_callbacks_, OnLeConnectFail(_, ErrorCode::CONNECTION_ACCEPT_TIMEOUT)).Times(1);
  le_impl_->create_connection_timeout_alarms_.emplace(
      std::piecewise_construct,
      std::forward_as_tuple(
          remote_public_address_with_type_.GetAddress(), remote_public_address_with_type_.GetAddressType()),
      std::forward_as_tuple(handler_));
  le_impl_->on_create_connection_timeout(remote_public_address_with_type_);
  sync_handler();
  ASSERT_TRUE(le_impl_->create_connection_timeout_alarms_.empty());
}

// b/260917913
TEST_F(LeImplTest, DISABLED_on_common_le_connection_complete__NoPriorConnection) {
  auto log_capture = std::make_unique<LogCapture>();
  le_impl_->on_common_le_connection_complete(remote_public_address_with_type_);
  ASSERT_TRUE(le_impl_->connecting_le_.empty());
  ASSERT_TRUE(log_capture->Rewind()->Find("No prior connection request for"));
}

TEST_F(LeImplTest, cancel_connect) {
  le_impl_->create_connection_timeout_alarms_.emplace(
      std::piecewise_construct,
      std::forward_as_tuple(
          remote_public_address_with_type_.GetAddress(), remote_public_address_with_type_.GetAddressType()),
      std::forward_as_tuple(handler_));
  le_impl_->cancel_connect(remote_public_address_with_type_);
  sync_handler();
  ASSERT_TRUE(le_impl_->create_connection_timeout_alarms_.empty());
}

TEST_F(LeImplTest, set_le_suggested_default_data_parameters) {
  le_impl_->set_le_suggested_default_data_parameters(kLength, kTime);
  sync_handler();
  auto view =
      CreateLeConnectionManagementCommandView<LeWriteSuggestedDefaultDataLengthView>(hci_layer_->DequeueCommandBytes());
  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(kLength, view.GetTxOctets());
  ASSERT_EQ(kTime, view.GetTxTime());
}

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth

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

#include "hci/hci_layer.h"

#include <gtest/gtest.h>

#include <chrono>
#include <future>

#include "common/bind.h"
#include "common/init_flags.h"
#include "common/testing/log_capture.h"
#include "hal/hci_hal.h"
#include "hci/address.h"
#include "hci/address_with_type.h"
#include "hci/class_of_device.h"
#include "hci/controller.h"
#include "module.h"
#include "os/fake_timer/fake_timerfd.h"
#include "os/handler.h"
#include "os/thread.h"
#include "packet/raw_builder.h"

using namespace std::chrono_literals;

namespace bluetooth {
namespace hci {

using common::BidiQueue;
using common::BidiQueueEnd;
using common::InitFlags;
using os::fake_timer::fake_timerfd_advance;
using packet::kLittleEndian;
using packet::PacketView;
using packet::RawBuilder;
using testing::LogCapture;

std::vector<uint8_t> GetPacketBytes(std::unique_ptr<packet::BasePacketBuilder> packet) {
  std::vector<uint8_t> bytes;
  BitInserter i(bytes);
  bytes.reserve(packet->size());
  packet->Serialize(i);
  return bytes;
}

std::unique_ptr<packet::BasePacketBuilder> CreatePayload(std::vector<uint8_t> payload) {
  auto raw_builder = std::make_unique<packet::RawBuilder>();
  raw_builder->AddOctets(payload);
  return raw_builder;
}

class TestHciHal : public hal::HciHal {
 public:
  TestHciHal() : hal::HciHal() {}

  ~TestHciHal() {
    ASSERT_LOG(callbacks == nullptr, "unregisterIncomingPacketCallback() must be called");
  }

  void registerIncomingPacketCallback(hal::HciHalCallbacks* callback) override {
    callbacks = callback;
  }

  void unregisterIncomingPacketCallback() override {
    callbacks = nullptr;
  }

  void sendHciCommand(hal::HciPacket command) override {
    outgoing_commands_.push_back(std::move(command));
    LOG_DEBUG("Enqueued HCI command in HAL.");
  }

  void sendScoData(hal::HciPacket data) override {}
  void sendIsoData(hal::HciPacket data) override {}
  void sendAclData(hal::HciPacket data) override {}

  hal::HciHalCallbacks* callbacks = nullptr;

  PacketView<kLittleEndian> GetPacketView(hal::HciPacket data) {
    auto shared = std::make_shared<std::vector<uint8_t>>(data);
    return PacketView<kLittleEndian>(shared);
  }

  CommandView GetSentCommand() {
    auto packetview = GetPacketView(std::move(outgoing_commands_.front()));
    outgoing_commands_.pop_front();
    return CommandView::Create(packetview);
  }

  void Start() override {}

  void Stop() override {}

  void ListDependencies(ModuleList*) const override {}

  int GetPendingCommands() {
    return outgoing_commands_.size();
  }

  void InjectEvent(std::unique_ptr<packet::BasePacketBuilder> packet) {
    callbacks->hciEventReceived(GetPacketBytes(std::move(packet)));
  }

  std::string ToString() const override {
    return std::string("TestHciHal");
  }

  static const ModuleFactory Factory;

 private:
  std::list<hal::HciPacket> outgoing_commands_;
  std::unique_ptr<std::promise<void>> sent_command_promise_;
};

const ModuleFactory TestHciHal::Factory = ModuleFactory([]() { return new TestHciHal(); });

class HciLayerTest : public ::testing::Test {
 protected:
  void SetUp() override {
    log_capture_ = std::make_unique<LogCapture>();
    hal_ = new TestHciHal();
    fake_registry_.InjectTestModule(&hal::HciHal::Factory, hal_);
    fake_registry_.Start<HciLayer>(&fake_registry_.GetTestThread());
    hci_ = static_cast<HciLayer*>(fake_registry_.GetModuleUnderTest(&HciLayer::Factory));
    hci_handler_ = fake_registry_.GetTestModuleHandler(&HciLayer::Factory);
    ASSERT_TRUE(fake_registry_.IsStarted<HciLayer>());
    ::testing::FLAGS_gtest_death_test_style = "threadsafe";
    InitFlags::SetAllForTesting();
  }

  void TearDown() override {
    fake_registry_.SynchronizeModuleHandler(&HciLayer::Factory, std::chrono::milliseconds(20));
    fake_registry_.StopAll();
  }

  void FakeTimerAdvance(uint64_t ms) {
    hci_handler_->Post(common::BindOnce(fake_timerfd_advance, ms));
  }

  void FailIfResetNotSent() {
    std::promise<void> promise;
    log_capture_->WaitUntilLogContains(&promise, "Enqueued HCI command in HAL.");
    auto sent_command = hal_->GetSentCommand();
    auto reset_view = ResetView::Create(CommandView::Create(sent_command));
    ASSERT_TRUE(reset_view.IsValid());
  }

  TestHciHal* hal_ = nullptr;
  HciLayer* hci_ = nullptr;
  os::Handler* hci_handler_ = nullptr;
  TestModuleRegistry fake_registry_;
  std::unique_ptr<LogCapture> log_capture_;
};

TEST_F(HciLayerTest, setup_teardown) {}

// b/260915548
TEST_F(HciLayerTest, DISABLED_reset_command_sent_on_start) {
  FailIfResetNotSent();
}

// b/260915548
TEST_F(HciLayerTest, DISABLED_controller_debug_info_requested_on_hci_timeout) {
  FailIfResetNotSent();
  FakeTimerAdvance(HciLayer::kHciTimeoutMs.count());

  std::promise<void> promise;
  log_capture_->WaitUntilLogContains(&promise, "Enqueued HCI command in HAL.");
  auto sent_command = hal_->GetSentCommand();
  auto debug_info_view = ControllerDebugInfoView::Create(VendorCommandView::Create(sent_command));
  ASSERT_TRUE(debug_info_view.IsValid());
}

// b/260915548
TEST_F(HciLayerTest, DISABLED_abort_after_hci_restart_timeout) {
  FailIfResetNotSent();
  FakeTimerAdvance(HciLayer::kHciTimeoutMs.count());

  std::promise<void> promise;
  log_capture_->WaitUntilLogContains(&promise, "Enqueued HCI command in HAL.");
  auto sent_command = hal_->GetSentCommand();
  auto debug_info_view = ControllerDebugInfoView::Create(VendorCommandView::Create(sent_command));
  ASSERT_TRUE(debug_info_view.IsValid());

  ASSERT_DEATH(
      {
        FakeTimerAdvance(HciLayer::kHciTimeoutRestartMs.count());
        std::promise<void> promise;
        log_capture_->WaitUntilLogContains(&promise, "Done waiting for debug information after HCI timeout");
      },
      "");
}

// b/260915548
TEST_F(HciLayerTest, DISABLED_abort_on_root_inflammation_event) {
  FailIfResetNotSent();

  auto payload = CreatePayload({'0'});
  auto root_inflammation_event = BqrRootInflammationEventBuilder::Create(0x01, 0x01, std::move(payload));
  hal_->InjectEvent(std::move(root_inflammation_event));
  std::promise<void> promise;
  log_capture_->WaitUntilLogContains(&promise, "Received a Root Inflammation Event");
  ASSERT_DEATH(
      {
        FakeTimerAdvance(HciLayer::kHciTimeoutRestartMs.count());
        std::promise<void> promise;
        log_capture_->WaitUntilLogContains(&promise, "Root inflammation with reason");
      },
      "");
}

}  // namespace hci
}  // namespace bluetooth

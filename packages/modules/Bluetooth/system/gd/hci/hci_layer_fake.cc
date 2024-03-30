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

#include "hci/hci_layer_fake.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <algorithm>
#include <chrono>

namespace bluetooth {
namespace hci {

using common::BidiQueue;
using common::BidiQueueEnd;
using packet::kLittleEndian;
using packet::PacketView;
using packet::RawBuilder;

PacketView<packet::kLittleEndian> GetPacketView(std::unique_ptr<packet::BasePacketBuilder> packet) {
  auto bytes = std::make_shared<std::vector<uint8_t>>();
  BitInserter i(*bytes);
  bytes->reserve(packet->size());
  packet->Serialize(i);
  return packet::PacketView<packet::kLittleEndian>(bytes);
}

std::unique_ptr<BasePacketBuilder> NextPayload(uint16_t handle) {
  static uint32_t packet_number = 1;
  auto payload = std::make_unique<RawBuilder>();
  payload->AddOctets2(6);  // L2CAP PDU size
  payload->AddOctets2(2);  // L2CAP CID
  payload->AddOctets2(handle);
  payload->AddOctets4(packet_number++);
  return std::move(payload);
}

static std::unique_ptr<AclBuilder> NextAclPacket(uint16_t handle) {
  PacketBoundaryFlag packet_boundary_flag = PacketBoundaryFlag::FIRST_AUTOMATICALLY_FLUSHABLE;
  BroadcastFlag broadcast_flag = BroadcastFlag::POINT_TO_POINT;
  return AclBuilder::Create(handle, packet_boundary_flag, broadcast_flag, NextPayload(handle));
}

void TestHciLayer::EnqueueCommand(
    std::unique_ptr<CommandBuilder> command, common::ContextualOnceCallback<void(CommandStatusView)> on_status) {
  std::lock_guard<std::mutex> lock(mutex_);

  command_queue_.push(std::move(command));
  command_status_callbacks.push_back(std::move(on_status));

  if (command_queue_.size() == 1) {
    // since GetCommand may replace this promise, we have to do this inside the lock
    command_promise_.set_value();
  }
}

void TestHciLayer::EnqueueCommand(
    std::unique_ptr<CommandBuilder> command, common::ContextualOnceCallback<void(CommandCompleteView)> on_complete) {
  std::lock_guard<std::mutex> lock(mutex_);

  command_queue_.push(std::move(command));
  command_complete_callbacks.push_back(std::move(on_complete));

  if (command_queue_.size() == 1) {
    // since GetCommand may replace this promise, we have to do this inside the lock
    command_promise_.set_value();
  }
}

CommandView TestHciLayer::GetCommand() {
  EXPECT_EQ(command_future_.wait_for(std::chrono::milliseconds(1000)), std::future_status::ready);

  std::lock_guard<std::mutex> lock(mutex_);

  if (command_queue_.empty()) {
    LOG_ERROR("Command queue is empty");
    return empty_command_view_;
  }

  auto last = std::move(command_queue_.front());
  command_queue_.pop();

  if (command_queue_.empty()) {
    command_promise_ = {};
    command_future_ = command_promise_.get_future();
  }

  CommandView command_packet_view = CommandView::Create(GetPacketView(std::move(last)));
  ASSERT_LOG(command_packet_view.IsValid(), "Got invalid command");
  return command_packet_view;
}

void TestHciLayer::RegisterEventHandler(
    EventCode event_code, common::ContextualCallback<void(EventView)> event_handler) {
  registered_events_[event_code] = event_handler;
}

void TestHciLayer::UnregisterEventHandler(EventCode event_code) {
  registered_events_.erase(event_code);
}

void TestHciLayer::RegisterLeEventHandler(
    SubeventCode subevent_code, common::ContextualCallback<void(LeMetaEventView)> event_handler) {
  registered_le_events_[subevent_code] = event_handler;
}

void TestHciLayer::UnregisterLeEventHandler(SubeventCode subevent_code) {
  registered_le_events_.erase(subevent_code);
}

void TestHciLayer::IncomingEvent(std::unique_ptr<EventBuilder> event_builder) {
  auto packet = GetPacketView(std::move(event_builder));
  EventView event = EventView::Create(packet);
  ASSERT_TRUE(event.IsValid());
  EventCode event_code = event.GetEventCode();
  if (event_code == EventCode::COMMAND_COMPLETE) {
    CommandCompleteCallback(event);
  } else if (event_code == EventCode::COMMAND_STATUS) {
    CommandStatusCallback(event);
  } else {
    ASSERT_NE(registered_events_.find(event_code), registered_events_.end()) << EventCodeText(event_code);
    registered_events_[event_code].Invoke(event);
  }
}

void TestHciLayer::IncomingLeMetaEvent(std::unique_ptr<LeMetaEventBuilder> event_builder) {
  auto packet = GetPacketView(std::move(event_builder));
  EventView event = EventView::Create(packet);
  LeMetaEventView meta_event_view = LeMetaEventView::Create(event);
  ASSERT_TRUE(meta_event_view.IsValid());
  SubeventCode subevent_code = meta_event_view.GetSubeventCode();
  ASSERT_TRUE(registered_le_events_.find(subevent_code) != registered_le_events_.end());
  registered_le_events_[subevent_code].Invoke(meta_event_view);
}

void TestHciLayer::CommandCompleteCallback(EventView event) {
  CommandCompleteView complete_view = CommandCompleteView::Create(event);
  ASSERT_TRUE(complete_view.IsValid());
  std::move(command_complete_callbacks.front()).Invoke(complete_view);
  command_complete_callbacks.pop_front();
}

void TestHciLayer::CommandStatusCallback(EventView event) {
  CommandStatusView status_view = CommandStatusView::Create(event);
  ASSERT_TRUE(status_view.IsValid());
  std::move(command_status_callbacks.front()).Invoke(status_view);
  command_status_callbacks.pop_front();
}

void TestHciLayer::InitEmptyCommand() {
  auto payload = std::make_unique<bluetooth::packet::RawBuilder>();
  auto command_builder = CommandBuilder::Create(OpCode::NONE, std::move(payload));
  empty_command_view_ = CommandView::Create(GetPacketView(std::move(command_builder)));
  ASSERT_TRUE(empty_command_view_.IsValid());
}

void TestHciLayer::IncomingAclData(uint16_t handle) {
  os::Handler* hci_handler = GetHandler();
  auto* queue_end = acl_queue_.GetDownEnd();
  std::promise<void> promise;
  auto future = promise.get_future();
  queue_end->RegisterEnqueue(
      hci_handler,
      common::Bind(
          [](decltype(queue_end) queue_end, uint16_t handle, std::promise<void> promise) {
            auto packet = GetPacketView(NextAclPacket(handle));
            AclView acl2 = AclView::Create(packet);
            queue_end->UnregisterEnqueue();
            promise.set_value();
            return std::make_unique<AclView>(acl2);
          },
          queue_end,
          handle,
          common::Passed(std::move(promise))));
  auto status = future.wait_for(std::chrono::milliseconds(1000));
  ASSERT_EQ(status, std::future_status::ready);
}

void TestHciLayer::AssertNoOutgoingAclData() {
  auto queue_end = acl_queue_.GetDownEnd();
  EXPECT_EQ(queue_end->TryDequeue(), nullptr);
}

PacketView<kLittleEndian> TestHciLayer::OutgoingAclData() {
  auto queue_end = acl_queue_.GetDownEnd();
  std::unique_ptr<AclBuilder> received;
  do {
    received = queue_end->TryDequeue();
  } while (received == nullptr);

  return GetPacketView(std::move(received));
}

BidiQueueEnd<AclBuilder, AclView>* TestHciLayer::GetAclQueueEnd() {
  return acl_queue_.GetUpEnd();
}

void TestHciLayer::Disconnect(uint16_t handle, ErrorCode reason) {
  GetHandler()->Post(
      common::BindOnce(&TestHciLayer::do_disconnect, common::Unretained(this), handle, reason));
}

void TestHciLayer::do_disconnect(uint16_t handle, ErrorCode reason) {
  HciLayer::Disconnect(handle, reason);
}

void TestHciLayer::ListDependencies(ModuleList* list) const {}
void TestHciLayer::Start() {
  std::lock_guard<std::mutex> lock(mutex_);
  InitEmptyCommand();
}
void TestHciLayer::Stop() {}

}  // namespace hci
}  // namespace bluetooth
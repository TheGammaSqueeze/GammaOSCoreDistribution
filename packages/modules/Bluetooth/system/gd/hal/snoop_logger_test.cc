/*
 * Copyright 2020 The Android Open Source Project
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

#include "hal/snoop_logger.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "os/fake_timer/fake_timerfd.h"

namespace testing {

using bluetooth::os::fake_timer::fake_timerfd_advance;
using bluetooth::os::fake_timer::fake_timerfd_reset;

namespace {
std::vector<uint8_t> kInformationRequest = {
    0xfe,
    0x2e,
    0x0a,
    0x00,
    0x06,
    0x00,
    0x01,
    0x00,
    0x0a,
    0x02,
    0x02,
    0x00,
    0x02,
    0x00,
};

std::vector<uint8_t> kSdpConnectionRequest = {
    0x08, 0x20, 0x0c, 0x00, 0x08, 0x00, 0x01, 0x00, 0x02, 0x0c, 0x04, 0x00, 0x01, 0x00, 0x44, 0x00};

std::vector<uint8_t> kAvdtpSuspend = {0x02, 0x02, 0x00, 0x07, 0x00, 0x03, 0x00, 0x8d, 0x00, 0x90, 0x09, 0x04};

std::vector<uint8_t> kHfpAtNrec0 = {0x02, 0x02, 0x20, 0x13, 0x00, 0x0f, 0x00, 0x41, 0x00, 0x09, 0xff, 0x15,
                                    0x01, 0x41, 0x54, 0x2b, 0x4e, 0x52, 0x45, 0x43, 0x3d, 0x30, 0x0d, 0x5c};

std::vector<uint8_t> kQualcommConnectionRequest = {0xdc, 0x2e, 0x54, 0x00, 0x50, 0x00, 0xff, 0x00, 0x00, 0x0a,
                                                   0x0f, 0x09, 0x01, 0x00, 0x5c, 0x93, 0x01, 0x00, 0x42, 0x00};

}  // namespace

using bluetooth::TestModuleRegistry;
using bluetooth::hal::SnoopLogger;
using namespace std::chrono_literals;

// Expose protected constructor for test
class TestSnoopLoggerModule : public SnoopLogger {
 public:
  TestSnoopLoggerModule(
      std::string snoop_log_path,
      std::string snooz_log_path,
      size_t max_packets_per_file,
      const std::string& btsnoop_mode,
      bool qualcomm_debug_log_enabled)
      : SnoopLogger(
            std::move(snoop_log_path),
            std::move(snooz_log_path),
            max_packets_per_file,
            SnoopLogger::GetMaxPacketsPerBuffer(),
            btsnoop_mode,
            qualcomm_debug_log_enabled,
            20ms,
            5ms) {}

  std::string ToString() const override {
    return std::string("TestSnoopLoggerModule");
  }

  void CallGetDumpsysData(flatbuffers::FlatBufferBuilder* builder) {
    GetDumpsysData(builder);
  }
};

class SnoopLoggerModuleTest : public Test {
 public:
  flatbuffers::FlatBufferBuilder* builder_;

 protected:
  void SetUp() override {
    temp_dir_ = std::filesystem::temp_directory_path();
    temp_snoop_log_ = temp_dir_ / "btsnoop_hci.log";
    temp_snoop_log_last_ = temp_dir_ / "btsnoop_hci.log.last";
    temp_snooz_log_ = temp_dir_ / "btsnooz_hci.log";
    temp_snooz_log_last_ = temp_dir_ / "btsnooz_hci.log.last";
    builder_ = new flatbuffers::FlatBufferBuilder();

    DeleteSnoopLogFiles();
    ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
    ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
    ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
    ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_last_));
  }

  void TearDown() override {
    DeleteSnoopLogFiles();
    delete builder_;
    fake_timerfd_reset();
  }

  void DeleteSnoopLogFiles() {
    if (std::filesystem::exists(temp_snoop_log_)) {
      ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_));
    }
    if (std::filesystem::exists(temp_snoop_log_last_)) {
      ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_last_));
    }
    if (std::filesystem::exists(temp_snooz_log_)) {
      ASSERT_TRUE(std::filesystem::remove(temp_snooz_log_));
    }
    if (std::filesystem::exists(temp_snooz_log_last_)) {
      ASSERT_TRUE(std::filesystem::remove(temp_snooz_log_last_));
    }
  }

  std::filesystem::path temp_dir_;
  std::filesystem::path temp_snoop_log_;
  std::filesystem::path temp_snoop_log_last_;
  std::filesystem::path temp_snooz_log_;
  std::filesystem::path temp_snooz_log_last_;
};

TEST_F(SnoopLoggerModuleTest, empty_snoop_log_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeFull, false);
  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);
  test_registry.StopAll();

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_EQ(std::filesystem::file_size(temp_snoop_log_), sizeof(SnoopLogger::FileHeaderType));
}

TEST_F(SnoopLoggerModuleTest, disable_snoop_log_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeDisabled, false);
  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);
  test_registry.StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, capture_one_packet_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeFull, false);
  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->Capture(kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);

  test_registry.StopAll();

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_),
      sizeof(SnoopLogger::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size());
}

TEST_F(SnoopLoggerModuleTest, capture_hci_cmd_btsnooz_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeDisabled, false);
  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->Capture(kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
  snoop_logger->CallGetDumpsysData(builder_);

  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snooz_log_),
      sizeof(SnoopLogger::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size());

  test_registry.StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, capture_l2cap_signal_packet_btsnooz_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeDisabled, false);
  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->Capture(kSdpConnectionRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
  snoop_logger->CallGetDumpsysData(builder_);

  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snooz_log_),
      sizeof(SnoopLogger::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kSdpConnectionRequest.size());

  test_registry.StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, capture_l2cap_short_data_packet_btsnooz_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeDisabled, false);
  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->Capture(kAvdtpSuspend, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
  snoop_logger->CallGetDumpsysData(builder_);

  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snooz_log_),
      sizeof(SnoopLogger::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kAvdtpSuspend.size());

  test_registry.StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, capture_l2cap_long_data_packet_btsnooz_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeDisabled, false);
  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->Capture(kHfpAtNrec0, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
  snoop_logger->CallGetDumpsysData(builder_);

  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snooz_log_),
      sizeof(SnoopLogger::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + 14);

  test_registry.StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, delete_old_snooz_log_files) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeDisabled, false);
  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  std::filesystem::create_directories(temp_snooz_log_);

  auto* handler = test_registry.GetTestModuleHandler(&SnoopLogger::Factory);
  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  handler->Post(bluetooth::common::BindOnce(fake_timerfd_advance, 10));
  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  handler->Post(bluetooth::common::BindOnce(fake_timerfd_advance, 15));
  handler->Post(bluetooth::common::BindOnce(
      [](std::filesystem::path path) { ASSERT_FALSE(std::filesystem::exists(path)); }, temp_snooz_log_));
  test_registry.StopAll();
}

TEST_F(SnoopLoggerModuleTest, rotate_file_at_new_session_test) {
  // Start once
  {
    auto* snoop_logger = new TestSnoopLoggerModule(
        temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeFull, false);
    TestModuleRegistry test_registry;
    test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);
    snoop_logger->Capture(kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
    test_registry.StopAll();
  }

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_),
      sizeof(SnoopLogger::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size());

  // Start again
  {
    auto* snoop_logger = new TestSnoopLoggerModule(
        temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeFull, false);
    TestModuleRegistry test_registry;
    test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);
    snoop_logger->Capture(kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
    snoop_logger->Capture(kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
    test_registry.StopAll();
  }

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_),
      sizeof(SnoopLogger::FileHeaderType) + (sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size()) * 2);
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_last_),
      sizeof(SnoopLogger::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size());
}

TEST_F(SnoopLoggerModuleTest, rotate_file_after_full_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeFull, false);
  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  for (int i = 0; i < 11; i++) {
    snoop_logger->Capture(kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
  }

  test_registry.StopAll();

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_),
      sizeof(SnoopLogger::FileHeaderType) + (sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size()) * 1);
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_last_),
      sizeof(SnoopLogger::FileHeaderType) + (sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size()) * 10);
}

TEST_F(SnoopLoggerModuleTest, qualcomm_debug_log_test) {
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeDisabled, true);
  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);
  snoop_logger->Capture(kQualcommConnectionRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
  snoop_logger->CallGetDumpsysData(builder_);

  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snooz_log_),
      sizeof(SnoopLogger::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kQualcommConnectionRequest.size());

  test_registry.StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, qualcomm_debug_log_regression_test) {
  {
    auto* snoop_logger = new TestSnoopLoggerModule(
        temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeDisabled, true);
    TestModuleRegistry test_registry;
    test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);
    snoop_logger->Capture(kHfpAtNrec0, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
    snoop_logger->CallGetDumpsysData(builder_);

    ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
    ASSERT_EQ(
        std::filesystem::file_size(temp_snooz_log_),
        sizeof(SnoopLogger::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + 14);
    test_registry.StopAll();
  }

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));

  {
    auto* snoop_logger = new TestSnoopLoggerModule(
        temp_snoop_log_.string(), temp_snooz_log_.string(), 10, SnoopLogger::kBtSnoopLogModeDisabled, false);
    TestModuleRegistry test_registry;
    test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);
    snoop_logger->Capture(kQualcommConnectionRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
    snoop_logger->CallGetDumpsysData(builder_);

    ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
    ASSERT_EQ(
        std::filesystem::file_size(temp_snooz_log_),
        sizeof(SnoopLogger::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + 14);
    test_registry.StopAll();
  }

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

}  // namespace testing

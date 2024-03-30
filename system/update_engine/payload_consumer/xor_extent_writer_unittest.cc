//
// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include <memory>

#include <unistd.h>

#include <android-base/file.h>
#include <gtest/gtest.h>
#include <libsnapshot/mock_snapshot_writer.h>

#include "common/utils.h"
#include "update_engine/payload_consumer/extent_map.h"
#include "update_engine/payload_consumer/file_descriptor.h"
#include "update_engine/payload_consumer/xor_extent_writer.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/merge_sequence_generator.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {

using testing::_;
using testing::Args;
using testing::Return;

class XorExtentWriterTest : public ::testing::Test {
 public:
  static constexpr size_t NUM_BLOCKS = 50;
  void SetUp() override {
    ASSERT_EQ(ftruncate64(source_part_.fd, kBlockSize * NUM_BLOCKS), 0);
    ASSERT_EQ(ftruncate64(target_part_.fd, kBlockSize * NUM_BLOCKS), 0);

    // Fill source part with 1s, as we are computing XOR between source and
    // target data later.
    ASSERT_EQ(lseek(source_part_.fd, 0, SEEK_SET), 0);
    brillo::Blob buffer(kBlockSize);
    std::fill(buffer.begin(), buffer.end(), 1);
    for (size_t i = 0; i < NUM_BLOCKS; i++) {
      ASSERT_EQ(write(source_part_.fd, buffer.data(), buffer.size()),
                static_cast<ssize_t>(buffer.size()));
    }
    ASSERT_EQ(fsync(source_part_.fd), 0);
    ASSERT_EQ(fsync(target_part_.fd), 0);
    ASSERT_TRUE(source_fd_->Open(source_part_.path, O_RDONLY | O_CREAT, 0644));
  }
  InstallOperation op_;
  FileDescriptorPtr source_fd_ = std::make_shared<EintrSafeFileDescriptor>();
  ExtentMap<const CowMergeOperation*> xor_map_;
  android::snapshot::MockSnapshotWriter cow_writer_;
  TemporaryFile source_part_;
  TemporaryFile target_part_;
};

MATCHER_P2(BytesEqual,
           bytes,
           size,
           "Check if args match expected value byte for byte") {
  return std::get<1>(arg) == size && std::get<0>(arg) != nullptr &&
         memcmp(std::get<0>(arg), bytes, size) == 0;
}

TEST_F(XorExtentWriterTest, StreamTest) {
  constexpr auto COW_XOR = CowMergeOperation::COW_XOR;
  ON_CALL(cow_writer_, EmitXorBlocks(_, _, _, _, _))
      .WillByDefault(Return(true));
  const auto op1 = CreateCowMergeOperation(
      ExtentForRange(5, 2), ExtentForRange(5, 2), COW_XOR);
  ASSERT_TRUE(xor_map_.AddExtent(op1.dst_extent(), &op1));
  *op_.add_src_extents() = op1.src_extent();
  *op_.add_dst_extents() = op1.dst_extent();

  const auto op2 = CreateCowMergeOperation(
      ExtentForRange(45, 2), ExtentForRange(456, 2), COW_XOR);
  ASSERT_TRUE(xor_map_.AddExtent(op2.dst_extent(), &op2));
  *op_.add_src_extents() = ExtentForRange(45, 3);
  *op_.add_dst_extents() = ExtentForRange(455, 3);

  const auto op3 = CreateCowMergeOperation(
      ExtentForRange(12, 2), ExtentForRange(321, 2), COW_XOR, 777);
  ASSERT_TRUE(xor_map_.AddExtent(op3.dst_extent(), &op3));
  *op_.add_src_extents() = ExtentForRange(12, 4);
  *op_.add_dst_extents() = ExtentForRange(320, 4);
  XORExtentWriter writer_{op_, source_fd_, &cow_writer_, xor_map_};

  // OTA op:
  // [5-6] => [5-6], [45-47] => [455-457], [12-15] => [320-323]

  // merge op:
  // [5-6] => [5-6], [45-46] => [456-457], [12-13] => [321-322]

  // Expected result:
  // [5-7], [45-47], [12-14] should be XOR blocks
  // [320], [323], [455] should be regular replace blocks

  auto zeros = utils::GetReadonlyZeroBlock(kBlockSize * 10);
  EXPECT_CALL(cow_writer_,
              EmitRawBlocks(455, zeros->data() + 2 * kBlockSize, kBlockSize))
      .With(Args<1, 2>(BytesEqual(zeros->data(), kBlockSize)))
      .WillOnce(Return(true));
  EXPECT_CALL(cow_writer_,
              EmitRawBlocks(320, zeros->data() + 5 * kBlockSize, kBlockSize))
      .With(Args<1, 2>(BytesEqual(zeros->data(), kBlockSize)))
      .WillOnce(Return(true));
  EXPECT_CALL(cow_writer_,
              EmitRawBlocks(323, zeros->data() + 8 * kBlockSize, kBlockSize))
      .With(Args<1, 2>(BytesEqual(zeros->data(), kBlockSize)))
      .WillOnce(Return(true));

  EXPECT_CALL(cow_writer_, EmitXorBlocks(5, _, kBlockSize * 2, 5, 0))
      .WillOnce(Return(true));
  EXPECT_CALL(cow_writer_, EmitXorBlocks(456, _, kBlockSize * 2, 45, 0))
      .WillOnce(Return(true));
  EXPECT_CALL(cow_writer_, EmitXorBlocks(321, _, kBlockSize * 2, 12, 777))
      .WillOnce(Return(true));

  ASSERT_TRUE(writer_.Init(op_.dst_extents(), kBlockSize));
  ASSERT_TRUE(writer_.Write(zeros->data(), 9 * kBlockSize));
}

}  // namespace chromeos_update_engine

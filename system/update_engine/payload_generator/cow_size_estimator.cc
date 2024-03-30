//
// Copyright (C) 2020 The Android Open Source Project
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

#include "update_engine/payload_generator/cow_size_estimator.h"

#include <algorithm>
#include <functional>
#include <string>
#include <utility>
#include <vector>

#include <android-base/unique_fd.h>
#include <libsnapshot/cow_writer.h>

#include "update_engine/common/cow_operation_convert.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_consumer/vabc_partition_writer.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {
using android::snapshot::CowWriter;

bool CowDryRun(
    FileDescriptorPtr source_fd,
    FileDescriptorPtr target_fd,
    const google::protobuf::RepeatedPtrField<InstallOperation>& operations,
    const google::protobuf::RepeatedPtrField<CowMergeOperation>&
        merge_operations,
    const size_t block_size,
    android::snapshot::CowWriter* cow_writer,
    const size_t partition_size,
    const bool xor_enabled) {
  CHECK_NE(target_fd, nullptr);
  CHECK(target_fd->IsOpen());
  VABCPartitionWriter::WriteMergeSequence(merge_operations, cow_writer);
  ExtentRanges visited;
  for (const auto& op : merge_operations) {
    if (op.type() == CowMergeOperation::COW_COPY) {
      visited.AddExtent(op.dst_extent());
      for (size_t i = 0; i < op.dst_extent().num_blocks(); i++) {
        cow_writer->AddCopy(op.dst_extent().start_block() + i,
                            op.src_extent().start_block() + i);
      }
    } else if (op.type() == CowMergeOperation::COW_XOR && xor_enabled) {
      CHECK_NE(source_fd, nullptr) << "Source fd is required to enable XOR ops";
      CHECK(source_fd->IsOpen());
      visited.AddExtent(op.dst_extent());
      // dst block count is used, because
      // src block count is probably(if src_offset > 0) 1 block
      // larger than dst extent. Using it might lead to intreseting out of bound
      // disk reads.
      std::vector<unsigned char> old_data(op.dst_extent().num_blocks() *
                                          block_size);
      ssize_t bytes_read = 0;
      if (!utils::PReadAll(
              source_fd,
              old_data.data(),
              old_data.size(),
              op.src_extent().start_block() * block_size + op.src_offset(),
              &bytes_read)) {
        PLOG(ERROR) << "Failed to read source data at " << op.src_extent();
        return false;
      }
      std::vector<unsigned char> new_data(op.dst_extent().num_blocks() *
                                          block_size);
      if (!utils::PReadAll(target_fd,
                           new_data.data(),
                           new_data.size(),
                           op.dst_extent().start_block() * block_size,
                           &bytes_read)) {
        PLOG(ERROR) << "Failed to read target data at " << op.dst_extent();
        return false;
      }
      CHECK_GT(old_data.size(), 0UL);
      CHECK_GT(new_data.size(), 0UL);
      std::transform(new_data.begin(),
                     new_data.end(),
                     old_data.begin(),
                     new_data.begin(),
                     std::bit_xor<unsigned char>{});
      CHECK(cow_writer->AddXorBlocks(op.dst_extent().start_block(),
                                     new_data.data(),
                                     new_data.size(),
                                     op.src_extent().start_block(),
                                     op.src_offset()));
    }
    // The value of label doesn't really matter, we just want to write some
    // labels to simulate bahvior of update_engine. As update_engine writes
    // labels every once a while when installing OTA, it's important that we do
    // the same to get accurate size estimation.
    cow_writer->AddLabel(0);
  }
  for (const auto& op : operations) {
    if (op.type() == InstallOperation::ZERO) {
      for (const auto& ext : op.dst_extents()) {
        visited.AddExtent(ext);
        cow_writer->AddZeroBlocks(ext.start_block(), ext.num_blocks());
      }
      cow_writer->AddLabel(0);
    }
  }
  const size_t last_block = partition_size / block_size;
  const auto unvisited_extents =
      FilterExtentRanges({ExtentForRange(0, last_block)}, visited);
  for (const auto& ext : unvisited_extents) {
    std::vector<unsigned char> data(ext.num_blocks() * block_size);
    ssize_t bytes_read = 0;
    if (!utils::PReadAll(target_fd,
                         data.data(),
                         data.size(),
                         ext.start_block() * block_size,
                         &bytes_read)) {
      PLOG(ERROR) << "Failed to read new block data at " << ext;
      return false;
    }
    cow_writer->AddRawBlocks(ext.start_block(), data.data(), data.size());
    cow_writer->AddLabel(0);
  }

  return cow_writer->Finalize();
}

size_t EstimateCowSize(
    FileDescriptorPtr source_fd,
    FileDescriptorPtr target_fd,
    const google::protobuf::RepeatedPtrField<InstallOperation>& operations,
    const google::protobuf::RepeatedPtrField<CowMergeOperation>&
        merge_operations,
    const size_t block_size,
    std::string compression,
    const size_t partition_size,
    const bool xor_enabled) {
  android::snapshot::CowWriter cow_writer{
      {.block_size = static_cast<uint32_t>(block_size),
       .compression = std::move(compression)}};
  // CowWriter treats -1 as special value, will discard all the data but still
  // reports Cow size. Good for estimation purposes
  cow_writer.Initialize(android::base::borrowed_fd{-1});
  CHECK(CowDryRun(source_fd,
                  target_fd,
                  operations,
                  merge_operations,
                  block_size,
                  &cow_writer,
                  partition_size,
                  xor_enabled));
  return cow_writer.GetCowSize();
}

}  // namespace chromeos_update_engine

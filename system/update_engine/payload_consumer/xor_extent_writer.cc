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

#include <algorithm>
#include <optional>
#include <vector>

#include "update_engine/common/utils.h"
#include "update_engine/payload_consumer/xor_extent_writer.h"
#include "update_engine/payload_generator/extent_utils.h"

namespace chromeos_update_engine {

// Returns true on success.
bool XORExtentWriter::WriteExtent(const void* bytes,
                                  const Extent& extent,
                                  const size_t size) {
  brillo::Blob xor_block_data;
  const auto xor_extents = xor_map_.GetIntersectingExtents(extent);
  for (const auto& xor_ext : xor_extents) {
    const auto merge_op_opt = xor_map_.Get(xor_ext);
    if (!merge_op_opt.has_value()) {
      // If a file in the target build contains duplicate blocks, e.g.
      // [120503-120514], [120503-120503], we can end up here. If that's the
      // case then there's no bug, just some annoying edge cases.
      LOG(ERROR)
          << xor_ext
          << " isn't in XOR map but it's returned by GetIntersectingExtents(), "
             "this is either a bug inside GetIntersectingExtents, or some "
             "duplicate blocks are present in target build. OTA extent: "
          << extent;
      return false;
    }

    const auto merge_op = merge_op_opt.value();
    TEST_AND_RETURN_FALSE(merge_op->has_src_extent());
    TEST_AND_RETURN_FALSE(merge_op->has_dst_extent());
    if (merge_op->dst_extent() != xor_ext) {
      LOG(ERROR) << "Each xor extent is expected to correspond to a complete "
                    "MergeOp, extent in value: "
                 << merge_op->dst_extent() << " extent in key: " << xor_ext;
      return false;
    }
    if (xor_ext.start_block() + xor_ext.num_blocks() >
        extent.start_block() + extent.num_blocks()) {
      LOG(ERROR) << "CowXor merge op extent should be completely inside "
                    "InstallOp's extent. merge op extent: "
                 << xor_ext << " InstallOp extent: " << extent;
      return false;
    }
    const auto src_offset = merge_op->src_offset();
    const auto src_block = merge_op->src_extent().start_block();
    xor_block_data.resize(BlockSize() * xor_ext.num_blocks());
    ssize_t bytes_read = 0;
    TEST_AND_RETURN_FALSE_ERRNO(
        utils::PReadAll(source_fd_,
                        xor_block_data.data(),
                        xor_block_data.size(),
                        src_offset + src_block * BlockSize(),
                        &bytes_read));
    if (bytes_read != static_cast<ssize_t>(xor_block_data.size())) {
      LOG(ERROR) << "bytes_read: " << bytes_read;
      return false;
    }

    const auto i = xor_ext.start_block() - extent.start_block();

    const auto dst_block_data =
        static_cast<const unsigned char*>(bytes) + i * BlockSize();
    std::transform(xor_block_data.cbegin(),
                   xor_block_data.cbegin() + xor_block_data.size(),
                   dst_block_data,
                   xor_block_data.begin(),
                   std::bit_xor<unsigned char>{});
    TEST_AND_RETURN_FALSE(cow_writer_->AddXorBlocks(xor_ext.start_block(),
                                                    xor_block_data.data(),
                                                    xor_block_data.size(),
                                                    src_block,
                                                    src_offset));
  }
  const auto replace_extents = xor_map_.GetNonIntersectingExtents(extent);
  return WriteReplaceExtents(replace_extents, extent, bytes, size);
}

bool XORExtentWriter::WriteReplaceExtents(
    const std::vector<Extent>& replace_extents,
    const Extent& extent,
    const void* bytes,
    size_t size) {
  const uint64_t new_block_start = extent.start_block();
  for (const auto& ext : replace_extents) {
    if (ext.start_block() + ext.num_blocks() >
        extent.start_block() + extent.num_blocks()) {
      LOG(ERROR) << "CowReplace merge op extent should be completely inside "
                    "InstallOp's extent. merge op extent: "
                 << ext << " InstallOp extent: " << extent;
      return false;
    }
    const auto i = ext.start_block() - new_block_start;
    const auto dst_block_data =
        static_cast<const unsigned char*>(bytes) + i * BlockSize();
    TEST_AND_RETURN_FALSE(cow_writer_->AddRawBlocks(
        ext.start_block(), dst_block_data, ext.num_blocks() * BlockSize()));
  }
  return true;
}

}  // namespace chromeos_update_engine

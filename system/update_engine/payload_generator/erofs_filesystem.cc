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

#include "update_engine/payload_generator/erofs_filesystem.h"

#include <time.h>

#include <string>
#include <mutex>

#include <erofs/internal.h>
#include <erofs/dir.h>
#include <erofs/io.h>

#include "erofs_iterate.h"
#include "lz4diff/lz4diff.pb.h"
#include "lz4diff/lz4patch.h"
#include "lz4diff/lz4diff.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/payload_generator/filesystem_interface.h"

namespace chromeos_update_engine {

namespace {

static constexpr int GetOccupiedSize(const struct erofs_inode* inode,
                                     erofs_off_t* size) {
  *size = 0;
  switch (inode->datalayout) {
    case EROFS_INODE_FLAT_INLINE:
    case EROFS_INODE_FLAT_PLAIN:
    case EROFS_INODE_CHUNK_BASED:
      *size = inode->i_size;
      break;
    case EROFS_INODE_FLAT_COMPRESSION_LEGACY:
    case EROFS_INODE_FLAT_COMPRESSION:
      *size = inode->u.i_blocks * EROFS_BLKSIZ;
      break;
    default:
      LOG(ERROR) << "unknown datalayout " << inode->datalayout;
      return -1;
  }
  return 0;
}

static int ErofsMapBlocks(struct erofs_inode* inode,
                          struct erofs_map_blocks* map,
                          int flags) {
  if (erofs_inode_is_data_compressed(inode->datalayout)) {
    return z_erofs_map_blocks_iter(inode, map, flags);
  }
  return erofs_map_blocks(inode, map, flags);
}

static constexpr bool IsBlockCompressed(const struct erofs_map_blocks& block) {
  // Z_EROFS_COMPRESSION_SHIFTED means data inside this block are merely
  // memmove()'ed in place, instead of going through some compression function
  // like LZ4 or LZMA
  return block.m_flags & EROFS_MAP_ENCODED &&
         block.m_algorithmformat != Z_EROFS_COMPRESSION_SHIFTED;
}

static void FillCompressedBlockInfo(FilesystemInterface::File* p_file,
                                    std::string_view image_filename,
                                    struct erofs_inode* inode) {
  auto& file = *p_file;
  if (!file.is_compressed) {
    return;
  }

  struct erofs_map_blocks block {};
  block.m_la = 0;
  block.index = UINT_MAX;

  const erofs_off_t uncompressed_size = file.file_stat.st_size;
  auto& compressed_blocks = file.compressed_file_info.blocks;
  auto last_pa = block.m_pa;
  auto last_plen = 0;
  while (block.m_la < uncompressed_size) {
    auto error = ErofsMapBlocks(inode, &block, EROFS_GET_BLOCKS_FIEMAP);
    if (error) {
      LOG(FATAL) << "Failed to map blocks for " << file.name << " in "
                 << image_filename;
    }
    // Certain uncompressed blocks have physical size > logical size. Usually
    // the physical block contains bunch of trailing zeros. Include thees
    // bytes in the logical size as well.
    if (!IsBlockCompressed(block)) {
      CHECK_LE(block.m_llen, block.m_plen);
      block.m_llen = block.m_plen;
    }

    if (last_pa + last_plen != block.m_pa) {
      if (last_plen != 0) {
        file.extents.push_back(ExtentForRange(
            last_pa / kBlockSize, utils::DivRoundUp(last_plen, kBlockSize)));
      }
      last_pa = block.m_pa;
      last_plen = block.m_plen;
    } else {
      last_plen += block.m_plen;
    }
    // If logical size and physical size are the same, this block is
    // uncompressed. Join consecutive uncompressed blocks to save a bit memory
    // storing metadata.
    if (block.m_llen == block.m_plen && !compressed_blocks.empty() &&
        !compressed_blocks.back().IsCompressed()) {
      compressed_blocks.back().compressed_length += block.m_llen;
      compressed_blocks.back().uncompressed_length += block.m_llen;
    } else {
      compressed_blocks.push_back(
          CompressedBlock(block.m_la, block.m_plen, block.m_llen));
    }

    block.m_la += block.m_llen;
  }
  file.extents.push_back(ExtentForRange(
      last_pa / kBlockSize, utils::DivRoundUp(last_plen, kBlockSize)));
  return;
}

}  // namespace

static_assert(kBlockSize == EROFS_BLKSIZ);

std::unique_ptr<ErofsFilesystem> ErofsFilesystem::CreateFromFile(
    const std::string& filename, const CompressionAlgorithm& algo) {
  // erofs-utils makes heavy use of global variables. Hence its functions aren't
  // thread safe. For example, it stores a global int holding file descriptors
  // to the opened EROFS image. It doesn't even support opening more than 1
  // imaeg at a time.
  // TODO(b/202784930) Replace erofs-utils with a cleaner and more C++ friendly
  // library. (Or turn erofs-utils into one)
  static std::mutex m;
  std::lock_guard g{m};

  if (const auto err = dev_open_ro(filename.c_str()); err) {
    PLOG(INFO) << "Failed to open " << filename;
    return nullptr;
  }
  DEFER { dev_close(); };

  if (const auto err = erofs_read_superblock(); err) {
    PLOG(INFO) << "Failed to parse " << filename << " as EROFS image";
    return nullptr;
  }
  struct stat st;
  if (const auto err = fstat(erofs_devfd, &st); err) {
    PLOG(ERROR) << "Failed to stat() " << filename;
    return nullptr;
  }
  const time_t time = sbi.build_time;
  LOG(INFO) << "Parsed EROFS image of size " << st.st_size << " built in "
            << ctime(&time) << " " << filename;
  std::vector<File> files;
  if (!ErofsFilesystem::GetFiles(filename, &files, algo)) {
    return nullptr;
  }
  LOG(INFO) << "Using compression algo " << algo << " for " << filename;
  // private ctor doesn't work with make_unique
  return std::unique_ptr<ErofsFilesystem>(
      new ErofsFilesystem(filename, st.st_size, std::move(files)));
}

bool ErofsFilesystem::GetFiles(std::vector<File>* files) const {
  *files = files_;
  return true;
}

bool ErofsFilesystem::GetFiles(const std::string& filename,
                               std::vector<File>* files,
                               const CompressionAlgorithm& algo) {
  erofs_iterate_root_dir(&sbi, [&](struct erofs_iterate_dir_context* p_info) {
    const auto& info = *p_info;
    if (info.ctx.de_ftype != EROFS_FT_REG_FILE) {
      return 0;
    }
    struct erofs_inode inode;
    inode.nid = info.ctx.de_nid;
    int err = erofs_read_inode_from_disk(&inode);
    if (err) {
      LOG(ERROR) << "Failed to read inode " << inode.nid;
      return err;
    }
    const auto uncompressed_size = inode.i_size;
    erofs_off_t compressed_size = 0;
    if (uncompressed_size == 0) {
      return 0;
    }
    err = GetOccupiedSize(&inode, &compressed_size);
    if (err) {
      LOG(FATAL) << "Failed to get occupied size for " << filename;
      return err;
    }
    // If data is packed inline, likely this node is stored on block unalighed
    // addresses. OTA doesn't work for non-block aligned files. All blocks not
    // reported by |GetFiles| will be updated in 1 operation. Ignore inline
    // files for now.
    // TODO(b/206729162) Support un-aligned files.
    if (inode.datalayout == EROFS_INODE_FLAT_INLINE) {
      return 0;
    }

    File file;
    file.name = info.path;
    file.compressed_file_info.zero_padding_enabled =
        erofs_sb_has_lz4_0padding();
    file.is_compressed = compressed_size != uncompressed_size;

    file.file_stat.st_size = uncompressed_size;
    file.file_stat.st_ino = inode.nid;
    FillCompressedBlockInfo(&file, filename, &inode);
    file.compressed_file_info.algo = algo;

    files->emplace_back(std::move(file));
    return 0;
  });

  for (auto& file : *files) {
    NormalizeExtents(&file.extents);
  }
  return true;
}

}  // namespace chromeos_update_engine
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

#include <string>

#include <erofs/dir.h>

#include "update_engine/common/utils.h"

//  The only way to pass extra information to callback function is to use a
//  wrapper type for erofs_dir_context. So here we go
struct erofs_iterate_dir_context {
  struct erofs_dir_context ctx;
  std::string path;
  void* arg;
};

// Dear compiler, please don't reoder fields inside erofs_iterate_dir_context.
// Because EROFS expects us to pass a wrapper type. So |ctx| member of
// erofs_iterate_dir_context must be put at 0 offset.
static_assert(offsetof(erofs_iterate_dir_context, ctx) == 0);

// Callable shold be a functor like
// std::function<int(struct erofs_inode_info *)>
template <typename Callable>
int erofs_iterate_root_dir(const struct erofs_sb_info* sbi, Callable cb) {
  struct erofs_inode dir {
    .nid = sbi->root_nid
  };
  int err = erofs_read_inode_from_disk(&dir);
  if (err) {
    LOG(ERROR) << "Failed to read inode " << sbi->root_nid << " from disk";
    return err;
  }
  struct erofs_iterate_dir_context param {
    .ctx.dir = &dir, .ctx.pnid = sbi->root_nid,
    .ctx.cb = [](struct erofs_dir_context* arg) -> int {
      auto ctx = reinterpret_cast<erofs_iterate_dir_context*>(arg);
      auto& path = ctx->path;
      const auto len = path.size();
      path.push_back('/');
      path.insert(
          path.end(), ctx->ctx.dname, ctx->ctx.dname + ctx->ctx.de_namelen);
      auto cb = static_cast<Callable*>(ctx->arg);
      const auto err = (*cb)(ctx);
      if (!err && !ctx->ctx.dot_dotdot && ctx->ctx.de_ftype == EROFS_FT_DIR) {
        // recursively walk into subdirectories
        erofs_inode dir{.nid = ctx->ctx.de_nid};
        if (const int err = erofs_read_inode_from_disk(&dir); err) {
          return err;
        }
        ctx->ctx.dir = &dir;
        if (const auto err = erofs_iterate_dir(&ctx->ctx, false); err) {
          return err;
        }
      }
      path.resize(len);
      return err;
    },
    .arg = &cb,
  };
  return erofs_iterate_dir(&param.ctx, false);
}

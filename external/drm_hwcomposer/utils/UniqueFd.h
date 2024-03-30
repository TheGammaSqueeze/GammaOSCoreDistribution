/*
 * Copyright (C) 2015, 2021 The Android Open Source Project
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

#ifndef UNIQUEFD_H_
#define UNIQUEFD_H_

#include <unistd.h>

#include <memory>

namespace android {

/*
 * Using UniqueFd:
 * 1. Create UniqueFd object:
 *    auto fd_obj = UniqueFd(open("SomeFile", xxx));
 *
 * 2. Check whether the fd_obj is empty:
 *    if (!fd_obj) { return -errno; }
 *
 * 3. Accessing the file descriptor:
 *    int ret = read(fd_obj.Get(), buf, buf_size);
 *
 * 4. Closing the file:
 *    FD will be closed once execution leaves fd_obj scope (on any return,
 *    exception, destruction of class/struct where object is member, etc.).
 *    User can also force closing the fd_obj by calling:
 *      fd_obj = UniqueFd();
 *      // fd is closed and fd_obj is empty now.
 *
 * 5. File descriptor may be transferred to the code, which will close it after
 *    using. This can be done in 2 ways:
 *    a. Duplicate the fd, in this case both fds should be closed separately:
 *      int out_fd = dup(fd_obj.Get();
 *      ...
 *      close(out_fd);
 *    b. Transfer ownership, use this method if you do not need the fd anymore.
 *      int out_fd = fd_obj.Release();
 *      // fd_obj is empty now.
 *      ...
 *      close(out_fd);
 *
 * 6. Transferring fd into another UniqueFD object:
 *    UniqueFd fd_obj_2 = std::move(fd_obj);
 *    // fd_obj empty now
 */

constexpr int kEmptyFd = -1;

class UniqueFd {
 public:
  UniqueFd() = default;
  explicit UniqueFd(int fd) : fd_(fd){};

  auto Release [[nodiscard]] () -> int {
    return std::exchange(fd_, kEmptyFd);
  }

  auto Get [[nodiscard]] () const -> int {
    return fd_;
  }

  explicit operator bool() const {
    return fd_ != kEmptyFd;
  }

  ~UniqueFd() {
    Set(kEmptyFd);
  }

  /* Allow move semantics */
  UniqueFd(UniqueFd &&rhs) noexcept {
    Set(rhs.Release());
  }

  auto operator=(UniqueFd &&rhs) noexcept -> UniqueFd & {
    Set(rhs.Release());
    return *this;
  }

  /* Disable copy semantics */
  UniqueFd(const UniqueFd &) = delete;
  auto operator=(const UniqueFd &) = delete;

 private:
  void Set(int new_fd) {
    if (fd_ != kEmptyFd) {
      close(fd_);
    }
    fd_ = new_fd;
  }

  int fd_ = kEmptyFd;
};

}  // namespace android

#endif

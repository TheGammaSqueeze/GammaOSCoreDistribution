/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <android-base/logging.h>
#include <android-base/strings.h>
#include <bootloader_message/bootloader_message.h>

#include <iostream>

int main(int argc, char** argv) {
  if (argc != 2) {
    std::cerr
        << "Usage: " << argv[0]
        << " [none|memtag|memtag_once|memtag_kernel|memtag_kernel_once]\n";
    return 1;
  }
  std::string value = argv[1];
  misc_memtag_message m = {.version = MISC_MEMTAG_MESSAGE_VERSION,
                           .magic = MISC_MEMTAG_MAGIC_HEADER};
  for (const auto& field : android::base::Split(value, ",")) {
    if (field == "memtag") {
      m.memtag_mode |= MISC_MEMTAG_MODE_MEMTAG;
    } else if (field == "memtag-once") {
      m.memtag_mode |= MISC_MEMTAG_MODE_MEMTAG_ONCE;
    } else if (field == "memtag-kernel") {
      m.memtag_mode |= MISC_MEMTAG_MODE_MEMTAG_KERNEL;
    } else if (field == "memtag-kernel-once") {
      m.memtag_mode |= MISC_MEMTAG_MODE_MEMTAG_KERNEL_ONCE;
    } else if (field != "none") {
      LOG(ERROR) << "Unknown value for arm64.memtag.bootctl: " << field;
      return 1;
    }
  }
  std::string err;
  if (!WriteMiscMemtagMessage(m, &err)) {
    LOG(ERROR) << "Failed to apply arm64.memtag.bootctl: " << value << ". "
               << err;
    return 1;
  } else {
    LOG(INFO) << "Applied arm64.memtag.bootctl: " << value;
    return 0;
  }
}

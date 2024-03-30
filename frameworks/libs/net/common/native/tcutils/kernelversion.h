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

// -----------------------------------------------------------------------------
// TODO - This should be replaced with BpfUtils in bpf_headers.
// Currently, bpf_headers contains a bunch requirements it doesn't actually provide, such as a
// non-ndk liblog version, and some version of libbase. libtcutils does not have access to either of
// these, so I think this will have to wait until we figure out a way around this.
//
// In the mean time copying verbatim from:
//   frameworks/libs/net/common/native/bpf_headers

#pragma once

#include <stdio.h>
#include <sys/utsname.h>

#define KVER(a, b, c) (((a) << 24) + ((b) << 16) + (c))

namespace android {

static inline unsigned uncachedKernelVersion() {
  struct utsname buf;
  int ret = uname(&buf);
  if (ret)
    return 0;

  unsigned kver_major;
  unsigned kver_minor;
  unsigned kver_sub;
  char discard;
  ret = sscanf(buf.release, "%u.%u.%u%c", &kver_major, &kver_minor, &kver_sub,
               &discard);
  // Check the device kernel version
  if (ret < 3)
    return 0;

  return KVER(kver_major, kver_minor, kver_sub);
}

static unsigned kernelVersion() {
  static unsigned kver = uncachedKernelVersion();
  return kver;
}

static inline bool isAtLeastKernelVersion(unsigned major, unsigned minor,
                                          unsigned sub) {
  return kernelVersion() >= KVER(major, minor, sub);
}

} // namespace android

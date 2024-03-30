/*
 * Copyright (C) 2017 The Android Open Source Project
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

#pragma once

#include <linux/if_ether.h>
#include <linux/pfkeyv2.h>
#include <net/if.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/socket.h>
#include <sys/utsname.h>

#include <string>

#include <android-base/unique_fd.h>
#include <log/log.h>

#include "BpfSyscallWrappers.h"

// The buffer size for the buffer that records program loading logs, needs to be large enough for
// the largest kernel program.

namespace android {
namespace bpf {

constexpr const int OVERFLOW_COUNTERSET = 2;

constexpr const uint64_t NONEXISTENT_COOKIE = 0;

static inline uint64_t getSocketCookie(int sockFd) {
    uint64_t sock_cookie;
    socklen_t cookie_len = sizeof(sock_cookie);
    int res = getsockopt(sockFd, SOL_SOCKET, SO_COOKIE, &sock_cookie, &cookie_len);
    if (res < 0) {
        res = -errno;
        ALOGE("Failed to get socket cookie: %s\n", strerror(errno));
        errno = -res;
        // 0 is an invalid cookie. See sock_gen_cookie.
        return NONEXISTENT_COOKIE;
    }
    return sock_cookie;
}

static inline int synchronizeKernelRCU() {
    // This is a temporary hack for network stats map swap on devices running
    // 4.9 kernels. The kernel code of socket release on pf_key socket will
    // explicitly call synchronize_rcu() which is exactly what we need.
    int pfSocket = socket(AF_KEY, SOCK_RAW | SOCK_CLOEXEC, PF_KEY_V2);

    if (pfSocket < 0) {
        int ret = -errno;
        ALOGE("create PF_KEY socket failed: %s", strerror(errno));
        return ret;
    }

    // When closing socket, synchronize_rcu() gets called in sock_release().
    if (close(pfSocket)) {
        int ret = -errno;
        ALOGE("failed to close the PF_KEY socket: %s", strerror(errno));
        return ret;
    }
    return 0;
}

static inline int setrlimitForTest() {
    // Set the memory rlimit for the test process if the default MEMLOCK rlimit is not enough.
    struct rlimit limit = {
            .rlim_cur = 1073741824,  // 1 GiB
            .rlim_max = 1073741824,  // 1 GiB
    };
    int res = setrlimit(RLIMIT_MEMLOCK, &limit);
    if (res) {
        ALOGE("Failed to set the default MEMLOCK rlimit: %s", strerror(errno));
    }
    return res;
}

#define KVER(a, b, c) (((a) << 24) + ((b) << 16) + (c))

static inline unsigned uncachedKernelVersion() {
    struct utsname buf;
    int ret = uname(&buf);
    if (ret) return 0;

    unsigned kver_major;
    unsigned kver_minor;
    unsigned kver_sub;
    char unused;
    ret = sscanf(buf.release, "%u.%u.%u%c", &kver_major, &kver_minor, &kver_sub, &unused);
    // Check the device kernel version
    if (ret < 3) return 0;

    return KVER(kver_major, kver_minor, kver_sub);
}

static inline unsigned kernelVersion() {
    static unsigned kver = uncachedKernelVersion();
    return kver;
}

static inline bool isAtLeastKernelVersion(unsigned major, unsigned minor, unsigned sub) {
    return kernelVersion() >= KVER(major, minor, sub);
}

#define SKIP_IF_BPF_SUPPORTED                              \
    do {                                                   \
        if (android::bpf::isAtLeastKernelVersion(4, 9, 0)) \
            GTEST_SKIP() << "Skip: bpf is supported.";     \
    } while (0)

#define SKIP_IF_BPF_NOT_SUPPORTED                           \
    do {                                                    \
        if (!android::bpf::isAtLeastKernelVersion(4, 9, 0)) \
            GTEST_SKIP() << "Skip: bpf is not supported.";  \
    } while (0)

#define SKIP_IF_EXTENDED_BPF_NOT_SUPPORTED                               \
    do {                                                                 \
        if (!android::bpf::isAtLeastKernelVersion(4, 14, 0))             \
            GTEST_SKIP() << "Skip: extended bpf feature not supported."; \
    } while (0)

#define SKIP_IF_XDP_NOT_SUPPORTED                           \
    do {                                                    \
        if (!android::bpf::isAtLeastKernelVersion(5, 9, 0)) \
            GTEST_SKIP() << "Skip: xdp not supported.";     \
    } while (0)

}  // namespace bpf
}  // namespace android

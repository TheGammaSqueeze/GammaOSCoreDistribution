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

#ifndef LOG_TAG
#define LOG_TAG "bpfloader"
#endif

#include <arpa/inet.h>
#include <dirent.h>
#include <elf.h>
#include <error.h>
#include <fcntl.h>
#include <inttypes.h>
#include <linux/bpf.h>
#include <linux/unistd.h>
#include <net/if.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <android-base/logging.h>
#include <android-base/macros.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <libbpf_android.h>
#include <log/log.h>
#include <netdutils/Misc.h>
#include <netdutils/Slice.h>
#include "bpf/BpfUtils.h"

using android::base::EndsWith;
using android::bpf::domain;
using std::string;

constexpr unsigned long long kTetheringApexDomainBitmask =
        domainToBitmask(domain::tethering) |
        domainToBitmask(domain::net_private) |
        domainToBitmask(domain::net_shared) |
        domainToBitmask(domain::netd_readonly) |
        domainToBitmask(domain::netd_shared);

// see b/162057235. For arbitrary program types, the concern is that due to the lack of
// SELinux access controls over BPF program attachpoints, we have no way to control the
// attachment of programs to shared resources (or to detect when a shared resource
// has one BPF program replace another that is attached there)
constexpr bpf_prog_type kVendorAllowedProgTypes[] = {
        BPF_PROG_TYPE_SOCKET_FILTER,
};

struct Location {
    const char* const dir;
    const char* const prefix;
    unsigned long long allowedDomainBitmask;
    const bpf_prog_type* allowedProgTypes = nullptr;
    size_t allowedProgTypesLength = 0;
};

const Location locations[] = {
        // S+ Tethering mainline module (network_stack): tether offload
        {
                .dir = "/apex/com.android.tethering/etc/bpf/",
                .prefix = "tethering/",
                .allowedDomainBitmask = kTetheringApexDomainBitmask,
        },
        // T+ Tethering mainline module (shared with netd & system server)
        // netutils_wrapper (for iptables xt_bpf) has access to programs
        {
                .dir = "/apex/com.android.tethering/etc/bpf/netd_shared/",
                .prefix = "netd_shared/",
                .allowedDomainBitmask = kTetheringApexDomainBitmask,
        },
        // T+ Tethering mainline module (shared with netd & system server)
        // netutils_wrapper has no access, netd has read only access
        {
                .dir = "/apex/com.android.tethering/etc/bpf/netd_readonly/",
                .prefix = "netd_readonly/",
                .allowedDomainBitmask = kTetheringApexDomainBitmask,
        },
        // T+ Tethering mainline module (shared with system server)
        {
                .dir = "/apex/com.android.tethering/etc/bpf/net_shared/",
                .prefix = "net_shared/",
                .allowedDomainBitmask = kTetheringApexDomainBitmask,
        },
        // T+ Tethering mainline module (not shared, just network_stack)
        {
                .dir = "/apex/com.android.tethering/etc/bpf/net_private/",
                .prefix = "net_private/",
                .allowedDomainBitmask = kTetheringApexDomainBitmask,
        },
        // Core operating system
        {
                .dir = "/system/etc/bpf/",
                .prefix = "",
                .allowedDomainBitmask = domainToBitmask(domain::platform),
        },
        // Vendor operating system
        {
                .dir = "/vendor/etc/bpf/",
                .prefix = "vendor/",
                .allowedDomainBitmask = domainToBitmask(domain::vendor),
                .allowedProgTypes = kVendorAllowedProgTypes,
                .allowedProgTypesLength = arraysize(kVendorAllowedProgTypes),
        },
};

int loadAllElfObjects(const Location& location) {
    int retVal = 0;
    DIR* dir;
    struct dirent* ent;

    if ((dir = opendir(location.dir)) != NULL) {
        while ((ent = readdir(dir)) != NULL) {
            string s = ent->d_name;
            if (!EndsWith(s, ".o")) continue;

            string progPath(location.dir);
            progPath += s;

            bool critical;
            int ret = android::bpf::loadProg(progPath.c_str(), &critical,
                                             location.prefix,
                                             location.allowedDomainBitmask,
                                             location.allowedProgTypes,
                                             location.allowedProgTypesLength);
            if (ret) {
                if (critical) retVal = ret;
                ALOGE("Failed to load object: %s, ret: %s", progPath.c_str(), std::strerror(-ret));
            } else {
                ALOGI("Loaded object: %s", progPath.c_str());
            }
        }
        closedir(dir);
    }
    return retVal;
}

void createSysFsBpfSubDir(const char* const prefix) {
    if (*prefix) {
        mode_t prevUmask = umask(0);

        string s = "/sys/fs/bpf/";
        s += prefix;

        errno = 0;
        int ret = mkdir(s.c_str(), S_ISVTX | S_IRWXU | S_IRWXG | S_IRWXO);
        if (ret && errno != EEXIST) {
            ALOGW("Failed to create directory: %s, ret: %s", s.c_str(), std::strerror(errno));
        }

        umask(prevUmask);
    }
}

int main(int argc, char** argv) {
    (void)argc;
    android::base::InitLogging(argv, &android::base::KernelLogger);

    // Create all the pin subdirectories
    // (this must be done first to allow selinux_context and pin_subdir functionality,
    //  which could otherwise fail with ENOENT during object pinning or renaming,
    //  due to ordering issues)
    for (const auto& location : locations) {
        createSysFsBpfSubDir(location.prefix);
    }

    // Load all ELF objects, create programs and maps, and pin them
    for (const auto& location : locations) {
        if (loadAllElfObjects(location) != 0) {
            ALOGE("=== CRITICAL FAILURE LOADING BPF PROGRAMS FROM %s ===", location.dir);
            ALOGE("If this triggers reliably, you're probably missing kernel options or patches.");
            ALOGE("If this triggers randomly, you might be hitting some memory allocation "
                  "problems or startup script race.");
            ALOGE("--- DO NOT EXPECT SYSTEM TO BOOT SUCCESSFULLY ---");
            sleep(20);
            return 2;
        }
    }

    int key = 1;
    int value = 123;
    android::base::unique_fd map(
            android::bpf::createMap(BPF_MAP_TYPE_ARRAY, sizeof(key), sizeof(value), 2, 0));
    if (android::bpf::writeToMapEntry(map, &key, &value, BPF_ANY)) {
        ALOGE("Critical kernel bug - failure to write into index 1 of 2 element bpf map array.");
        return 1;
    }

    if (android::base::SetProperty("bpf.progs_loaded", "1") == false) {
        ALOGE("Failed to set bpf.progs_loaded property");
        return 1;
    }

    return 0;
}

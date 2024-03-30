/**
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "BpfHandler"

#include "BpfHandler.h"

#include <linux/bpf.h>

#include <android-base/unique_fd.h>
#include <bpf/WaitForProgsLoaded.h>
#include <log/log.h>
#include <netdutils/UidConstants.h>
#include <private/android_filesystem_config.h>

#include "BpfSyscallWrappers.h"

namespace android {
namespace net {

using base::unique_fd;
using bpf::NONEXISTENT_COOKIE;
using bpf::getSocketCookie;
using bpf::retrieveProgram;
using netdutils::Status;
using netdutils::statusFromErrno;

constexpr int PER_UID_STATS_ENTRIES_LIMIT = 500;
// At most 90% of the stats map may be used by tagged traffic entries. This ensures
// that 10% of the map is always available to count untagged traffic, one entry per UID.
// Otherwise, apps would be able to avoid data usage accounting entirely by filling up the
// map with tagged traffic entries.
constexpr int TOTAL_UID_STATS_ENTRIES_LIMIT = STATS_MAP_SIZE * 0.9;

static_assert(STATS_MAP_SIZE - TOTAL_UID_STATS_ENTRIES_LIMIT > 100,
              "The limit for stats map is to high, stats data may be lost due to overflow");

static Status attachProgramToCgroup(const char* programPath, const unique_fd& cgroupFd,
                                    bpf_attach_type type) {
    unique_fd cgroupProg(retrieveProgram(programPath));
    if (cgroupProg == -1) {
        int ret = errno;
        ALOGE("Failed to get program from %s: %s", programPath, strerror(ret));
        return statusFromErrno(ret, "cgroup program get failed");
    }
    if (android::bpf::attachProgram(type, cgroupProg, cgroupFd)) {
        int ret = errno;
        ALOGE("Program from %s attach failed: %s", programPath, strerror(ret));
        return statusFromErrno(ret, "program attach failed");
    }
    return netdutils::status::ok;
}

static Status initPrograms(const char* cg2_path) {
    unique_fd cg_fd(open(cg2_path, O_DIRECTORY | O_RDONLY | O_CLOEXEC));
    if (cg_fd == -1) {
        int ret = errno;
        ALOGE("Failed to open the cgroup directory: %s", strerror(ret));
        return statusFromErrno(ret, "Open the cgroup directory failed");
    }
    RETURN_IF_NOT_OK(attachProgramToCgroup(BPF_EGRESS_PROG_PATH, cg_fd, BPF_CGROUP_INET_EGRESS));
    RETURN_IF_NOT_OK(attachProgramToCgroup(BPF_INGRESS_PROG_PATH, cg_fd, BPF_CGROUP_INET_INGRESS));

    // For the devices that support cgroup socket filter, the socket filter
    // should be loaded successfully by bpfloader. So we attach the filter to
    // cgroup if the program is pinned properly.
    // TODO: delete the if statement once all devices should support cgroup
    // socket filter (ie. the minimum kernel version required is 4.14).
    if (!access(CGROUP_SOCKET_PROG_PATH, F_OK)) {
        RETURN_IF_NOT_OK(
                attachProgramToCgroup(CGROUP_SOCKET_PROG_PATH, cg_fd, BPF_CGROUP_INET_SOCK_CREATE));
    }
    return netdutils::status::ok;
}

BpfHandler::BpfHandler()
    : mPerUidStatsEntriesLimit(PER_UID_STATS_ENTRIES_LIMIT),
      mTotalUidStatsEntriesLimit(TOTAL_UID_STATS_ENTRIES_LIMIT) {}

BpfHandler::BpfHandler(uint32_t perUidLimit, uint32_t totalLimit)
    : mPerUidStatsEntriesLimit(perUidLimit), mTotalUidStatsEntriesLimit(totalLimit) {}

Status BpfHandler::init(const char* cg2_path) {
    // Make sure BPF programs are loaded before doing anything
    android::bpf::waitForProgsLoaded();
    ALOGI("BPF programs are loaded");

    RETURN_IF_NOT_OK(initPrograms(cg2_path));
    RETURN_IF_NOT_OK(initMaps());

    return netdutils::status::ok;
}

Status BpfHandler::initMaps() {
    std::lock_guard guard(mMutex);
    RETURN_IF_NOT_OK(mCookieTagMap.init(COOKIE_TAG_MAP_PATH));
    RETURN_IF_NOT_OK(mStatsMapA.init(STATS_MAP_A_PATH));
    RETURN_IF_NOT_OK(mStatsMapB.init(STATS_MAP_B_PATH));
    RETURN_IF_NOT_OK(mConfigurationMap.init(CONFIGURATION_MAP_PATH));
    RETURN_IF_NOT_OK(mUidPermissionMap.init(UID_PERMISSION_MAP_PATH));

    return netdutils::status::ok;
}

bool BpfHandler::hasUpdateDeviceStatsPermission(uid_t uid) {
    // This implementation is the same logic as method ActivityManager#checkComponentPermission.
    // It implies that the real uid can never be the same as PER_USER_RANGE.
    uint32_t appId = uid % PER_USER_RANGE;
    auto permission = mUidPermissionMap.readValue(appId);
    if (permission.ok() && (permission.value() & BPF_PERMISSION_UPDATE_DEVICE_STATS)) {
        return true;
    }
    return ((appId == AID_ROOT) || (appId == AID_SYSTEM) || (appId == AID_DNS));
}

int BpfHandler::tagSocket(int sockFd, uint32_t tag, uid_t chargeUid, uid_t realUid) {
    std::lock_guard guard(mMutex);
    if (chargeUid != realUid && !hasUpdateDeviceStatsPermission(realUid)) {
        return -EPERM;
    }

    // Note that tagging the socket to AID_CLAT is only implemented in JNI ClatCoordinator.
    // The process is not allowed to tag socket to AID_CLAT via tagSocket() which would cause
    // process data usage accounting to be bypassed. Tagging AID_CLAT is used for avoiding counting
    // CLAT traffic data usage twice. See packages/modules/Connectivity/service/jni/
    // com_android_server_connectivity_ClatCoordinator.cpp
    if (chargeUid == AID_CLAT) {
        return -EPERM;
    }

    // The socket destroy listener only monitors on the group {INET_TCP, INET_UDP, INET6_TCP,
    // INET6_UDP}. Tagging listener unsupported socket causes that the tag can't be removed from
    // tag map automatically. Eventually, the tag map may run out of space because of dead tag
    // entries. Note that although tagSocket() of net client has already denied the family which
    // is neither AF_INET nor AF_INET6, the family validation is still added here just in case.
    // See tagSocket in system/netd/client/NetdClient.cpp and
    // TrafficController::makeSkDestroyListener in
    // packages/modules/Connectivity/service/native/TrafficController.cpp
    // TODO: remove this once the socket destroy listener can detect more types of socket destroy.
    int socketFamily;
    socklen_t familyLen = sizeof(socketFamily);
    if (getsockopt(sockFd, SOL_SOCKET, SO_DOMAIN, &socketFamily, &familyLen)) {
        ALOGE("Failed to getsockopt SO_DOMAIN: %s, fd: %d", strerror(errno), sockFd);
        return -errno;
    }
    if (socketFamily != AF_INET && socketFamily != AF_INET6) {
        ALOGE("Unsupported family: %d", socketFamily);
        return -EAFNOSUPPORT;
    }

    int socketProto;
    socklen_t protoLen = sizeof(socketProto);
    if (getsockopt(sockFd, SOL_SOCKET, SO_PROTOCOL, &socketProto, &protoLen)) {
        ALOGE("Failed to getsockopt SO_PROTOCOL: %s, fd: %d", strerror(errno), sockFd);
        return -errno;
    }
    if (socketProto != IPPROTO_UDP && socketProto != IPPROTO_TCP) {
        ALOGE("Unsupported protocol: %d", socketProto);
        return -EPROTONOSUPPORT;
    }

    uint64_t sock_cookie = getSocketCookie(sockFd);
    if (sock_cookie == NONEXISTENT_COOKIE) return -errno;
    UidTagValue newKey = {.uid = (uint32_t)chargeUid, .tag = tag};

    uint32_t totalEntryCount = 0;
    uint32_t perUidEntryCount = 0;
    // Now we go through the stats map and count how many entries are associated
    // with chargeUid. If the uid entry hit the limit for each chargeUid, we block
    // the request to prevent the map from overflow. It is safe here to iterate
    // over the map since when mMutex is hold, system server cannot toggle
    // the live stats map and clean it. So nobody can delete entries from the map.
    const auto countUidStatsEntries = [chargeUid, &totalEntryCount, &perUidEntryCount](
                                              const StatsKey& key,
                                              const BpfMap<StatsKey, StatsValue>&) {
        if (key.uid == chargeUid) {
            perUidEntryCount++;
        }
        totalEntryCount++;
        return base::Result<void>();
    };
    auto configuration = mConfigurationMap.readValue(CURRENT_STATS_MAP_CONFIGURATION_KEY);
    if (!configuration.ok()) {
        ALOGE("Failed to get current configuration: %s, fd: %d",
              strerror(configuration.error().code()), mConfigurationMap.getMap().get());
        return -configuration.error().code();
    }
    if (configuration.value() != SELECT_MAP_A && configuration.value() != SELECT_MAP_B) {
        ALOGE("unknown configuration value: %d", configuration.value());
        return -EINVAL;
    }

    BpfMap<StatsKey, StatsValue>& currentMap =
            (configuration.value() == SELECT_MAP_A) ? mStatsMapA : mStatsMapB;
    // HACK: mStatsMapB becomes RW BpfMap here, but countUidStatsEntries doesn't modify so it works
    base::Result<void> res = currentMap.iterate(countUidStatsEntries);
    if (!res.ok()) {
        ALOGE("Failed to count the stats entry in map %d: %s", currentMap.getMap().get(),
              strerror(res.error().code()));
        return -res.error().code();
    }

    if (totalEntryCount > mTotalUidStatsEntriesLimit ||
        perUidEntryCount > mPerUidStatsEntriesLimit) {
        ALOGE("Too many stats entries in the map, total count: %u, chargeUid(%u) count: %u,"
              " blocking tag request to prevent map overflow",
              totalEntryCount, chargeUid, perUidEntryCount);
        return -EMFILE;
    }
    // Update the tag information of a socket to the cookieUidMap. Use BPF_ANY
    // flag so it will insert a new entry to the map if that value doesn't exist
    // yet. And update the tag if there is already a tag stored. Since the eBPF
    // program in kernel only read this map, and is protected by rcu read lock. It
    // should be fine to cocurrently update the map while eBPF program is running.
    res = mCookieTagMap.writeValue(sock_cookie, newKey, BPF_ANY);
    if (!res.ok()) {
        ALOGE("Failed to tag the socket: %s, fd: %d", strerror(res.error().code()),
              mCookieTagMap.getMap().get());
        return -res.error().code();
    }
    return 0;
}

int BpfHandler::untagSocket(int sockFd) {
    std::lock_guard guard(mMutex);
    uint64_t sock_cookie = getSocketCookie(sockFd);

    if (sock_cookie == NONEXISTENT_COOKIE) return -errno;
    base::Result<void> res = mCookieTagMap.deleteValue(sock_cookie);
    if (!res.ok()) {
        ALOGE("Failed to untag socket: %s\n", strerror(res.error().code()));
        return -res.error().code();
    }
    return 0;
}

}  // namespace net
}  // namespace android

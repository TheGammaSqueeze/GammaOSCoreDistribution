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

#pragma once

#include <set>
#include <Common.h>

#include "android-base/thread_annotations.h"
#include "bpf/BpfMap.h"
#include "bpf_shared.h"
#include "netdutils/DumpWriter.h"
#include "netdutils/NetlinkListener.h"
#include "netdutils/StatusOr.h"

namespace android {
namespace net {

using netdutils::StatusOr;

class TrafficController {
  public:
    static constexpr char DUMP_KEYWORD[] = "trafficcontroller";

    /*
     * Initialize the whole controller
     */
    netdutils::Status start();

    /*
     * Swap the stats map config from current active stats map to the idle one.
     */
    netdutils::Status swapActiveStatsMap() EXCLUDES(mMutex);

    /*
     * Add the interface name and index pair into the eBPF map.
     */
    int addInterface(const char* name, uint32_t ifaceIndex);

    int changeUidOwnerRule(ChildChain chain, const uid_t uid, FirewallRule rule, FirewallType type);

    int removeUidOwnerRule(const uid_t uid);

    int replaceUidOwnerMap(const std::string& name, bool isAllowlist,
                           const std::vector<int32_t>& uids);

    enum IptOp { IptOpInsert, IptOpDelete };

    netdutils::Status updateOwnerMapEntry(UidOwnerMatchType match, uid_t uid, FirewallRule rule,
                                          FirewallType type) EXCLUDES(mMutex);

    void dump(int fd, bool verbose) EXCLUDES(mMutex);

    netdutils::Status replaceRulesInMap(UidOwnerMatchType match, const std::vector<int32_t>& uids)
            EXCLUDES(mMutex);

    netdutils::Status addUidInterfaceRules(const int ifIndex, const std::vector<int32_t>& uids)
            EXCLUDES(mMutex);
    netdutils::Status removeUidInterfaceRules(const std::vector<int32_t>& uids) EXCLUDES(mMutex);

    netdutils::Status updateUidOwnerMap(const uint32_t uid,
                                        UidOwnerMatchType matchType, IptOp op) EXCLUDES(mMutex);

    int toggleUidOwnerMap(ChildChain chain, bool enable) EXCLUDES(mMutex);

    static netdutils::StatusOr<std::unique_ptr<netdutils::NetlinkListenerInterface>>
    makeSkDestroyListener();

    void setPermissionForUids(int permission, const std::vector<uid_t>& uids) EXCLUDES(mMutex);

    FirewallType getFirewallType(ChildChain);

    static const char* LOCAL_DOZABLE;
    static const char* LOCAL_STANDBY;
    static const char* LOCAL_POWERSAVE;
    static const char* LOCAL_RESTRICTED;
    static const char* LOCAL_LOW_POWER_STANDBY;
    static const char* LOCAL_OEM_DENY_1;
    static const char* LOCAL_OEM_DENY_2;
    static const char* LOCAL_OEM_DENY_3;

  private:
    /*
     * mCookieTagMap: Store the corresponding tag and uid for a specific socket.
     * DO NOT hold any locks when modifying this map, otherwise when the untag
     * operation is waiting for a lock hold by other process and there are more
     * sockets being closed than can fit in the socket buffer of the netlink socket
     * that receives them, then the kernel will drop some of these sockets and we
     * won't delete their tags.
     * Map Key: uint64_t socket cookie
     * Map Value: UidTagValue, contains a uint32 uid and a uint32 tag.
     */
    bpf::BpfMap<uint64_t, UidTagValue> mCookieTagMap GUARDED_BY(mMutex);

    /*
     * mUidCounterSetMap: Store the counterSet of a specific uid.
     * Map Key: uint32 uid.
     * Map Value: uint32 counterSet specifies if the traffic is a background
     * or foreground traffic.
     */
    bpf::BpfMap<uint32_t, uint8_t> mUidCounterSetMap GUARDED_BY(mMutex);

    /*
     * mAppUidStatsMap: Store the total traffic stats for a uid regardless of
     * tag, counterSet and iface. The stats is used by TrafficStats.getUidStats
     * API to return persistent stats for a specific uid since device boot.
     */
    bpf::BpfMap<uint32_t, StatsValue> mAppUidStatsMap;

    /*
     * mStatsMapA/mStatsMapB: Store the traffic statistics for a specific
     * combination of uid, tag, iface and counterSet. These two maps contain
     * both tagged and untagged traffic.
     * Map Key: StatsKey contains the uid, tag, counterSet and ifaceIndex
     * information.
     * Map Value: Stats, contains packet count and byte count of each
     * transport protocol on egress and ingress direction.
     */
    bpf::BpfMap<StatsKey, StatsValue> mStatsMapA GUARDED_BY(mMutex);

    bpf::BpfMap<StatsKey, StatsValue> mStatsMapB GUARDED_BY(mMutex);

    /*
     * mIfaceIndexNameMap: Store the index name pair of each interface show up
     * on the device since boot. The interface index is used by the eBPF program
     * to correctly match the iface name when receiving a packet.
     */
    bpf::BpfMap<uint32_t, IfaceValue> mIfaceIndexNameMap;

    /*
     * mIfaceStataMap: Store per iface traffic stats gathered from xt_bpf
     * filter.
     */
    bpf::BpfMap<uint32_t, StatsValue> mIfaceStatsMap;

    /*
     * mConfigurationMap: Store the current network policy about uid filtering
     * and the current stats map in use. There are two configuration entries in
     * the map right now:
     * - Entry with UID_RULES_CONFIGURATION_KEY:
     *    Store the configuration for the current uid rules. It indicates the device
     *    is in doze/powersave/standby/restricted/low power standby/oem deny mode.
     * - Entry with CURRENT_STATS_MAP_CONFIGURATION_KEY:
     *    Stores the current live stats map that kernel program is writing to.
     *    Userspace can do scraping and cleaning job on the other one depending on the
     *    current configs.
     */
    bpf::BpfMap<uint32_t, uint32_t> mConfigurationMap GUARDED_BY(mMutex);

    /*
     * mUidOwnerMap: Store uids that are used for bandwidth control uid match.
     */
    bpf::BpfMap<uint32_t, UidOwnerValue> mUidOwnerMap GUARDED_BY(mMutex);

    /*
     * mUidOwnerMap: Store uids that are used for INTERNET permission check.
     */
    bpf::BpfMap<uint32_t, uint8_t> mUidPermissionMap GUARDED_BY(mMutex);

    std::unique_ptr<netdutils::NetlinkListenerInterface> mSkDestroyListener;

    netdutils::Status removeRule(uint32_t uid, UidOwnerMatchType match) REQUIRES(mMutex);

    netdutils::Status addRule(uint32_t uid, UidOwnerMatchType match, uint32_t iif = 0)
            REQUIRES(mMutex);

    std::mutex mMutex;

    netdutils::Status initMaps() EXCLUDES(mMutex);

    // Keep track of uids that have permission UPDATE_DEVICE_STATS so we don't
    // need to call back to system server for permission check.
    std::set<uid_t> mPrivilegedUser GUARDED_BY(mMutex);

    bool hasUpdateDeviceStatsPermission(uid_t uid) REQUIRES(mMutex);

    // For testing
    friend class TrafficControllerTest;
};

}  // namespace net
}  // namespace android

/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * bpf_existence_test.cpp - checks that the device has expected BPF programs and maps
 */

#include <cstdint>
#include <set>
#include <string>

#include <android/api-level.h>
#include <android-base/properties.h>
#include <android-modules-utils/sdk_level.h>
#include <bpf/BpfUtils.h>

#include <gtest/gtest.h>

using std::find;
using std::set;
using std::string;

using android::modules::sdklevel::IsAtLeastR;
using android::modules::sdklevel::IsAtLeastS;
using android::modules::sdklevel::IsAtLeastT;

// Mainline development branches lack the constant for the current development OS.
#ifndef __ANDROID_API_T__
#define __ANDROID_API_T__ 33
#endif

#define PLATFORM "/sys/fs/bpf/"
#define TETHERING "/sys/fs/bpf/tethering/"
#define PRIVATE "/sys/fs/bpf/net_private/"
#define SHARED "/sys/fs/bpf/net_shared/"
#define NETD "/sys/fs/bpf/netd_shared/"

class BpfExistenceTest : public ::testing::Test {
};

static const set<string> INTRODUCED_R = {
    PLATFORM "map_offload_tether_ingress_map",
    PLATFORM "map_offload_tether_limit_map",
    PLATFORM "map_offload_tether_stats_map",
    PLATFORM "prog_offload_schedcls_ingress_tether_ether",
    PLATFORM "prog_offload_schedcls_ingress_tether_rawip",
};

static const set<string> INTRODUCED_S = {
    TETHERING "map_offload_tether_dev_map",
    TETHERING "map_offload_tether_downstream4_map",
    TETHERING "map_offload_tether_downstream64_map",
    TETHERING "map_offload_tether_downstream6_map",
    TETHERING "map_offload_tether_error_map",
    TETHERING "map_offload_tether_limit_map",
    TETHERING "map_offload_tether_stats_map",
    TETHERING "map_offload_tether_upstream4_map",
    TETHERING "map_offload_tether_upstream6_map",
    TETHERING "map_test_tether_downstream6_map",
    TETHERING "prog_offload_schedcls_tether_downstream4_ether",
    TETHERING "prog_offload_schedcls_tether_downstream4_rawip",
    TETHERING "prog_offload_schedcls_tether_downstream6_ether",
    TETHERING "prog_offload_schedcls_tether_downstream6_rawip",
    TETHERING "prog_offload_schedcls_tether_upstream4_ether",
    TETHERING "prog_offload_schedcls_tether_upstream4_rawip",
    TETHERING "prog_offload_schedcls_tether_upstream6_ether",
    TETHERING "prog_offload_schedcls_tether_upstream6_rawip",
};

static const set<string> REMOVED_S = {
    PLATFORM "map_offload_tether_ingress_map",
    PLATFORM "map_offload_tether_limit_map",
    PLATFORM "map_offload_tether_stats_map",
    PLATFORM "prog_offload_schedcls_ingress_tether_ether",
    PLATFORM "prog_offload_schedcls_ingress_tether_rawip",
};

static const set<string> INTRODUCED_T = {
    SHARED "map_block_blocked_ports_map",
    SHARED "map_clatd_clat_egress4_map",
    SHARED "map_clatd_clat_ingress6_map",
    SHARED "map_dscp_policy_ipv4_dscp_policies_map",
    SHARED "map_dscp_policy_ipv4_socket_to_policies_map_A",
    SHARED "map_dscp_policy_ipv4_socket_to_policies_map_B",
    SHARED "map_dscp_policy_ipv6_dscp_policies_map",
    SHARED "map_dscp_policy_ipv6_socket_to_policies_map_A",
    SHARED "map_dscp_policy_ipv6_socket_to_policies_map_B",
    SHARED "map_dscp_policy_switch_comp_map",
    NETD "map_netd_app_uid_stats_map",
    NETD "map_netd_configuration_map",
    NETD "map_netd_cookie_tag_map",
    NETD "map_netd_iface_index_name_map",
    NETD "map_netd_iface_stats_map",
    NETD "map_netd_stats_map_A",
    NETD "map_netd_stats_map_B",
    NETD "map_netd_uid_counterset_map",
    NETD "map_netd_uid_owner_map",
    NETD "map_netd_uid_permission_map",
    SHARED "prog_clatd_schedcls_egress4_clat_ether",
    SHARED "prog_clatd_schedcls_egress4_clat_rawip",
    SHARED "prog_clatd_schedcls_ingress6_clat_ether",
    SHARED "prog_clatd_schedcls_ingress6_clat_rawip",
    NETD "prog_netd_cgroupskb_egress_stats",
    NETD "prog_netd_cgroupskb_ingress_stats",
    NETD "prog_netd_cgroupsock_inet_create",
    NETD "prog_netd_schedact_ingress_account",
    NETD "prog_netd_skfilter_allowlist_xtbpf",
    NETD "prog_netd_skfilter_denylist_xtbpf",
    NETD "prog_netd_skfilter_egress_xtbpf",
    NETD "prog_netd_skfilter_ingress_xtbpf",
};

static const set<string> INTRODUCED_T_5_4 = {
    SHARED "prog_block_bind4_block_port",
    SHARED "prog_block_bind6_block_port",
};

static const set<string> INTRODUCED_T_5_15 = {
    SHARED "prog_dscp_policy_schedcls_set_dscp_ether",
    SHARED "prog_dscp_policy_schedcls_set_dscp_raw_ip",
};

static const set<string> REMOVED_T = {
};

void addAll(set<string>* a, const set<string>& b) {
    a->insert(b.begin(), b.end());
}

void removeAll(set<string>* a, const set<string>& b) {
    for (const auto& toRemove : b) {
        a->erase(toRemove);
    }
}

void getFileLists(set<string>* expected, set<string>* unexpected) {
    unexpected->clear();
    expected->clear();

    addAll(unexpected, INTRODUCED_R);
    addAll(unexpected, INTRODUCED_S);
    addAll(unexpected, INTRODUCED_T);

    if (IsAtLeastR()) {
        addAll(expected, INTRODUCED_R);
        removeAll(unexpected, INTRODUCED_R);
        // Nothing removed in R.
    }

    if (IsAtLeastS()) {
        addAll(expected, INTRODUCED_S);
        removeAll(expected, REMOVED_S);

        addAll(unexpected, REMOVED_S);
        removeAll(unexpected, INTRODUCED_S);
    }

    // Nothing added or removed in SCv2.

    if (IsAtLeastT()) {
        addAll(expected, INTRODUCED_T);
        if (android::bpf::isAtLeastKernelVersion(5, 4, 0)) addAll(expected, INTRODUCED_T_5_4);
        if (android::bpf::isAtLeastKernelVersion(5, 15, 0)) addAll(expected, INTRODUCED_T_5_15);
        removeAll(expected, REMOVED_T);

        addAll(unexpected, REMOVED_T);
        removeAll(unexpected, INTRODUCED_T);
    }
}

void checkFiles() {
    set<string> mustExist;
    set<string> mustNotExist;

    getFileLists(&mustExist, &mustNotExist);

    for (const auto& file : mustExist) {
        EXPECT_EQ(0, access(file.c_str(), R_OK)) << file << " does not exist";
    }
    for (const auto& file : mustNotExist) {
        int ret = access(file.c_str(), R_OK);
        int err = errno;
        EXPECT_EQ(-1, ret) << file << " unexpectedly exists";
        if (ret == -1) {
            EXPECT_EQ(ENOENT, err) << " accessing " << file << " failed with errno " << err;
        }
    }
}

TEST_F(BpfExistenceTest, TestPrograms) {
    SKIP_IF_BPF_NOT_SUPPORTED;

    // Pre-flight check to ensure test has been updated.
    uint64_t buildVersionSdk = android_get_device_api_level();
    ASSERT_NE(0, buildVersionSdk) << "Unable to determine device SDK version";
    if (buildVersionSdk > __ANDROID_API_T__ && buildVersionSdk != __ANDROID_API_FUTURE__) {
            FAIL() << "Unknown OS version " << buildVersionSdk << ", please update this test";
    }

    // Only unconfined root is guaranteed to be able to access everything in /sys/fs/bpf.
    ASSERT_EQ(0, getuid()) << "This test must run as root.";

    checkFiles();
}

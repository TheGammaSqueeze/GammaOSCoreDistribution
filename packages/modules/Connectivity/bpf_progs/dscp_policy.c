/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <linux/types.h>
#include <linux/bpf.h>
#include <linux/if_packet.h>
#include <linux/ip.h>
#include <linux/ipv6.h>
#include <linux/if_ether.h>
#include <linux/pkt_cls.h>
#include <linux/tcp.h>
#include <stdint.h>
#include <netinet/in.h>
#include <netinet/udp.h>
#include <string.h>

// The resulting .o needs to load on the Android T beta 3 bpfloader
#define BPFLOADER_MIN_VER BPFLOADER_T_BETA3_VERSION

#include "bpf_helpers.h"
#include "dscp_policy.h"

DEFINE_BPF_MAP_GRW(switch_comp_map, ARRAY, int, uint64_t, 1, AID_SYSTEM)

DEFINE_BPF_MAP_GRW(ipv4_socket_to_policies_map_A, HASH, uint64_t, RuleEntry, MAX_POLICIES,
        AID_SYSTEM)
DEFINE_BPF_MAP_GRW(ipv4_socket_to_policies_map_B, HASH, uint64_t, RuleEntry, MAX_POLICIES,
        AID_SYSTEM)
DEFINE_BPF_MAP_GRW(ipv6_socket_to_policies_map_A, HASH, uint64_t, RuleEntry, MAX_POLICIES,
        AID_SYSTEM)
DEFINE_BPF_MAP_GRW(ipv6_socket_to_policies_map_B, HASH, uint64_t, RuleEntry, MAX_POLICIES,
        AID_SYSTEM)

DEFINE_BPF_MAP_GRW(ipv4_dscp_policies_map, ARRAY, uint32_t, DscpPolicy, MAX_POLICIES,
        AID_SYSTEM)
DEFINE_BPF_MAP_GRW(ipv6_dscp_policies_map, ARRAY, uint32_t, DscpPolicy, MAX_POLICIES,
        AID_SYSTEM)

static inline __always_inline void match_policy(struct __sk_buff* skb, bool ipv4, bool is_eth) {
    void* data = (void*)(long)skb->data;
    const void* data_end = (void*)(long)skb->data_end;

    const int l2_header_size = is_eth ? sizeof(struct ethhdr) : 0;
    struct ethhdr* eth = is_eth ? data : NULL;

    if (data + l2_header_size > data_end) return;

    int zero = 0;
    int hdr_size = 0;
    uint64_t* selectedMap = bpf_switch_comp_map_lookup_elem(&zero);

    // use this with HASH map so map lookup only happens once policies have been added?
    if (!selectedMap) {
        return;
    }

    // used for map lookup
    uint64_t cookie = bpf_get_socket_cookie(skb);
    if (!cookie)
        return;

    uint16_t sport = 0;
    uint16_t dport = 0;
    uint8_t protocol = 0; // TODO: Use are reserved value? Or int (-1) and cast to uint below?
    struct in6_addr srcIp = {};
    struct in6_addr dstIp = {};
    uint8_t tos = 0; // Only used for IPv4
    uint8_t priority = 0; // Only used for IPv6
    uint8_t flow_lbl = 0; // Only used for IPv6
    if (ipv4) {
        const struct iphdr* const iph = is_eth ? (void*)(eth + 1) : data;
        // Must have ipv4 header
        if (data + l2_header_size + sizeof(*iph) > data_end) return;

        // IP version must be 4
        if (iph->version != 4) return;

        // We cannot handle IP options, just standard 20 byte == 5 dword minimal IPv4 header
        if (iph->ihl != 5) return;

        // V4 mapped address in in6_addr sets 10/11 position to 0xff.
        srcIp.s6_addr32[2] = htonl(0x0000ffff);
        dstIp.s6_addr32[2] = htonl(0x0000ffff);

        // Copy IPv4 address into in6_addr for easy comparison below.
        srcIp.s6_addr32[3] = iph->saddr;
        dstIp.s6_addr32[3] = iph->daddr;
        protocol = iph->protocol;
        tos = iph->tos;
        hdr_size = sizeof(struct iphdr);
    } else {
        struct ipv6hdr* ip6h = is_eth ? (void*)(eth + 1) : data;
        // Must have ipv6 header
        if (data + l2_header_size + sizeof(*ip6h) > data_end) return;

        if (ip6h->version != 6) return;

        srcIp = ip6h->saddr;
        dstIp = ip6h->daddr;
        protocol = ip6h->nexthdr;
        priority = ip6h->priority;
        flow_lbl = ip6h->flow_lbl[0];
        hdr_size = sizeof(struct ipv6hdr);
    }

    switch (protocol) {
        case IPPROTO_UDP:
        case IPPROTO_UDPLITE:
        {
            struct udphdr *udp;
            udp = data + hdr_size;
            if ((void*)(udp + 1) > data_end) return;
            sport = udp->source;
            dport = udp->dest;
        }
        break;
        case IPPROTO_TCP:
        {
            struct tcphdr *tcp;
            tcp = data + hdr_size;
            if ((void*)(tcp + 1) > data_end) return;
            sport = tcp->source;
            dport = tcp->dest;
        }
        break;
        default:
            return;
    }

    RuleEntry* existingRule;
    if (ipv4) {
        if (*selectedMap == MAP_A) {
            existingRule = bpf_ipv4_socket_to_policies_map_A_lookup_elem(&cookie);
        } else {
            existingRule = bpf_ipv4_socket_to_policies_map_B_lookup_elem(&cookie);
        }
    } else {
        if (*selectedMap == MAP_A) {
            existingRule = bpf_ipv6_socket_to_policies_map_A_lookup_elem(&cookie);
        } else {
            existingRule = bpf_ipv6_socket_to_policies_map_B_lookup_elem(&cookie);
        }
    }

    if (existingRule && v6_equal(srcIp, existingRule->srcIp) &&
                v6_equal(dstIp, existingRule->dstIp) &&
                skb->ifindex == existingRule->ifindex &&
                ntohs(sport) == htons(existingRule->srcPort) &&
                ntohs(dport) == htons(existingRule->dstPort) &&
                protocol == existingRule->proto) {
        if (ipv4) {
            int ecn = tos & 3;
            uint8_t newDscpVal = (existingRule->dscpVal << 2) + ecn;
            int oldDscpVal = tos >> 2;
            bpf_l3_csum_replace(skb, 1, oldDscpVal, newDscpVal, sizeof(uint8_t));
            bpf_skb_store_bytes(skb, 1, &newDscpVal, sizeof(uint8_t), 0);
        } else {
            uint8_t new_priority = (existingRule->dscpVal >> 2) + 0x60;
            uint8_t new_flow_label = ((existingRule->dscpVal & 0xf) << 6) + (priority >> 6);
            bpf_skb_store_bytes(skb, 0, &new_priority, sizeof(uint8_t), 0);
            bpf_skb_store_bytes(skb, 1, &new_flow_label, sizeof(uint8_t), 0);
        }
        return;
    }

    // Linear scan ipv4_dscp_policies_map since no stored params match skb.
    int bestScore = -1;
    uint32_t bestMatch = 0;

    for (register uint64_t i = 0; i < MAX_POLICIES; i++) {
        int score = 0;
        uint8_t tempMask = 0;
        // Using a uint64 in for loop prevents infinite loop during BPF load,
        // but the key is uint32, so convert back.
        uint32_t key = i;

        DscpPolicy* policy;
        if (ipv4) {
            policy = bpf_ipv4_dscp_policies_map_lookup_elem(&key);
        } else {
            policy = bpf_ipv6_dscp_policies_map_lookup_elem(&key);
        }

        // If the policy lookup failed, presentFields is 0, or iface index does not match
        // index on skb buff, then we can continue to next policy.
        if (!policy || policy->presentFields == 0 || policy->ifindex != skb->ifindex)
            continue;

        if ((policy->presentFields & SRC_IP_MASK_FLAG) == SRC_IP_MASK_FLAG &&
                v6_equal(srcIp, policy->srcIp)) {
            score++;
            tempMask |= SRC_IP_MASK_FLAG;
        }
        if ((policy->presentFields & DST_IP_MASK_FLAG) == DST_IP_MASK_FLAG &&
                v6_equal(dstIp, policy->dstIp)) {
            score++;
            tempMask |= DST_IP_MASK_FLAG;
        }
        if ((policy->presentFields & SRC_PORT_MASK_FLAG) == SRC_PORT_MASK_FLAG &&
                ntohs(sport) == htons(policy->srcPort)) {
            score++;
            tempMask |= SRC_PORT_MASK_FLAG;
        }
        if ((policy->presentFields & DST_PORT_MASK_FLAG) == DST_PORT_MASK_FLAG &&
                ntohs(dport) >= htons(policy->dstPortStart) &&
                ntohs(dport) <= htons(policy->dstPortEnd)) {
            score++;
            tempMask |= DST_PORT_MASK_FLAG;
        }
        if ((policy->presentFields & PROTO_MASK_FLAG) == PROTO_MASK_FLAG &&
                protocol == policy->proto) {
            score++;
            tempMask |= PROTO_MASK_FLAG;
        }

        if (score > bestScore && tempMask == policy->presentFields) {
            bestMatch = i;
            bestScore = score;
        }
    }

    uint8_t new_tos= 0; // Can 0 be used as default forwarding value?
    uint8_t new_priority = 0;
    uint8_t new_flow_lbl = 0;
    if (bestScore > 0) {
        DscpPolicy* policy;
        if (ipv4) {
            policy = bpf_ipv4_dscp_policies_map_lookup_elem(&bestMatch);
        } else {
            policy = bpf_ipv6_dscp_policies_map_lookup_elem(&bestMatch);
        }

        if (policy) {
            // TODO: if DSCP value is already set ignore?
            if (ipv4) {
                int ecn = tos & 3;
                new_tos = (policy->dscpVal << 2) + ecn;
            } else {
                new_priority = (policy->dscpVal >> 2) + 0x60;
                new_flow_lbl = ((policy->dscpVal & 0xf) << 6) + (flow_lbl >> 6);

                // Set IPv6 curDscp value to stored value and recalulate priority
                // and flow label during next use.
                new_tos = policy->dscpVal;
            }
        }
    } else return;

    RuleEntry value = {
        .srcIp = srcIp,
        .dstIp = dstIp,
        .ifindex = skb->ifindex,
        .srcPort = sport,
        .dstPort = dport,
        .proto = protocol,
        .dscpVal = new_tos,
    };

    //Update map with new policy.
    if (ipv4) {
        if (*selectedMap == MAP_A) {
            bpf_ipv4_socket_to_policies_map_A_update_elem(&cookie, &value, BPF_ANY);
        } else {
            bpf_ipv4_socket_to_policies_map_B_update_elem(&cookie, &value, BPF_ANY);
        }
    } else {
        if (*selectedMap == MAP_A) {
            bpf_ipv6_socket_to_policies_map_A_update_elem(&cookie, &value, BPF_ANY);
        } else {
            bpf_ipv6_socket_to_policies_map_B_update_elem(&cookie, &value, BPF_ANY);
        }
    }

    // Need to store bytes after updating map or program will not load.
    if (ipv4 && new_tos != (tos & 252)) {
        int oldDscpVal = tos >> 2;
        bpf_l3_csum_replace(skb, 1, oldDscpVal, new_tos, sizeof(uint8_t));
        bpf_skb_store_bytes(skb, 1, &new_tos, sizeof(uint8_t), 0);
    } else if (!ipv4 && (new_priority != priority || new_flow_lbl != flow_lbl)) {
        bpf_skb_store_bytes(skb, 0, &new_priority, sizeof(uint8_t), 0);
        bpf_skb_store_bytes(skb, 1, &new_flow_lbl, sizeof(uint8_t), 0);
    }
    return;
}

DEFINE_BPF_PROG_KVER("schedcls/set_dscp_ether", AID_ROOT, AID_SYSTEM,
                     schedcls_set_dscp_ether, KVER(5, 15, 0))
(struct __sk_buff* skb) {

    if (skb->pkt_type != PACKET_HOST) return TC_ACT_PIPE;

    if (skb->protocol == htons(ETH_P_IP)) {
        match_policy(skb, true, true);
    } else if (skb->protocol == htons(ETH_P_IPV6)) {
        match_policy(skb, false, true);
    }

    // Always return TC_ACT_PIPE
    return TC_ACT_PIPE;
}

DEFINE_BPF_PROG_KVER("schedcls/set_dscp_raw_ip", AID_ROOT, AID_SYSTEM,
                     schedcls_set_dscp_raw_ip, KVER(5, 15, 0))
(struct __sk_buff* skb) {
    if (skb->protocol == htons(ETH_P_IP)) {
        match_policy(skb, true, false);
    } else if (skb->protocol == htons(ETH_P_IPV6)) {
        match_policy(skb, false, false);
    }

    // Always return TC_ACT_PIPE
    return TC_ACT_PIPE;
}

LICENSE("Apache 2.0");
CRITICAL("Connectivity");

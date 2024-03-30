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

#define MAX_POLICIES 16
#define MAP_A 1
#define MAP_B 2

#define SRC_IP_MASK_FLAG     1
#define DST_IP_MASK_FLAG     2
#define SRC_PORT_MASK_FLAG   4
#define DST_PORT_MASK_FLAG   8
#define PROTO_MASK_FLAG      16

#define STRUCT_SIZE(name, size) _Static_assert(sizeof(name) == (size), "Incorrect struct size.")

#define v6_equal(a, b) \
    (((a.s6_addr32[0] ^ b.s6_addr32[0]) | \
      (a.s6_addr32[1] ^ b.s6_addr32[1]) | \
      (a.s6_addr32[2] ^ b.s6_addr32[2]) | \
      (a.s6_addr32[3] ^ b.s6_addr32[3])) == 0)

// TODO: these are already defined in packages/modules/Connectivity/bpf_progs/bpf_net_helpers.h.
// smove to common location in future.
static uint64_t (*bpf_get_socket_cookie)(struct __sk_buff* skb) =
        (void*)BPF_FUNC_get_socket_cookie;
static int (*bpf_skb_store_bytes)(struct __sk_buff* skb, __u32 offset, const void* from, __u32 len,
                                  __u64 flags) = (void*)BPF_FUNC_skb_store_bytes;
static int (*bpf_l3_csum_replace)(struct __sk_buff* skb, __u32 offset, __u64 from, __u64 to,
                                  __u64 flags) = (void*)BPF_FUNC_l3_csum_replace;
static long (*bpf_skb_ecn_set_ce)(struct __sk_buff* skb) =
        (void*)BPF_FUNC_skb_ecn_set_ce;

typedef struct {
    struct in6_addr srcIp;
    struct in6_addr dstIp;
    uint32_t ifindex;
    __be16 srcPort;
    __be16 dstPortStart;
    __be16 dstPortEnd;
    uint8_t proto;
    uint8_t dscpVal;
    uint8_t presentFields;
    uint8_t pad[3];
} DscpPolicy;
STRUCT_SIZE(DscpPolicy, 2 * 16 + 4 + 3 * 2 + 3 * 1 + 3);  // 48

typedef struct {
    struct in6_addr srcIp;
    struct in6_addr dstIp;
    __u32 ifindex;
    __be16 srcPort;
    __be16 dstPort;
    __u8 proto;
    __u8 dscpVal;
    __u8 pad[2];
} RuleEntry;
STRUCT_SIZE(RuleEntry, 2 * 16 + 1 * 4 + 2 * 2 + 2 * 1 + 2);  // 44
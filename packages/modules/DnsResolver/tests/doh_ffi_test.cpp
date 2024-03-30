/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "doh.h"

#include <chrono>
#include <condition_variable>
#include <mutex>

#include <resolv.h>

#include <NetdClient.h>
#include <gmock/gmock-matchers.h>
#include <gtest/gtest.h>

static const char* GOOGLE_SERVER_IP = "8.8.8.8";
static const int TIMEOUT_MS = 3000;
constexpr int MAXPACKET = (8 * 1024);
constexpr unsigned int MINIMAL_NET_ID = 100;

std::mutex m;
std::condition_variable cv;
unsigned int dnsNetId;

TEST(DoHFFITest, SmokeTest) {
    getNetworkForDns(&dnsNetId);
    // To ensure that we have a real network.
    ASSERT_GE(dnsNetId, MINIMAL_NET_ID) << "No available networks";

    auto validation_cb = [](uint32_t netId, bool success, const char* ip_addr, const char* host) {
        EXPECT_EQ(netId, dnsNetId);
        EXPECT_TRUE(success);
        EXPECT_STREQ(ip_addr, GOOGLE_SERVER_IP);
        EXPECT_STREQ(host, "");
        cv.notify_one();
    };

    auto tag_socket_cb = [](int32_t sock) { EXPECT_GE(sock, 0); };

    DohDispatcher* doh = doh_dispatcher_new(validation_cb, tag_socket_cb);
    EXPECT_TRUE(doh != nullptr);

    const FeatureFlags flags = {
            .probe_timeout_ms = TIMEOUT_MS,
            .idle_timeout_ms = TIMEOUT_MS,
            .use_session_resumption = true,
    };

    // TODO: Use a local server instead of dns.google.
    // sk_mark doesn't matter here because this test doesn't have permission to set sk_mark.
    // The DNS packet would be sent via default network.
    EXPECT_EQ(doh_net_new(doh, dnsNetId, "https://dns.google/dns-query", /* domain */ "",
                          GOOGLE_SERVER_IP, /* sk_mark */ 0, /* cert_path */ "", &flags),
              0);
    {
        std::unique_lock<std::mutex> lk(m);
        EXPECT_EQ(cv.wait_for(lk, std::chrono::milliseconds(TIMEOUT_MS)),
                  std::cv_status::no_timeout);
    }

    std::vector<uint8_t> buf(MAXPACKET, 0);
    ssize_t len = res_mkquery(ns_o_query, "www.example.com", ns_c_in, ns_t_aaaa, nullptr, 0,
                              nullptr, buf.data(), MAXPACKET);
    uint8_t answer[8192];

    len = doh_query(doh, dnsNetId, buf.data(), len, answer, sizeof answer, TIMEOUT_MS);
    EXPECT_GT(len, 0);
    doh_net_delete(doh, dnsNetId);
    doh_dispatcher_delete(doh);
}

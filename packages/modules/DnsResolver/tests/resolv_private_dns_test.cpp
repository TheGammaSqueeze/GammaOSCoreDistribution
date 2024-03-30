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
 *
 */

#define LOG_TAG "resolv_private_dns_test"

#include <regex>

#include <aidl/android/net/IDnsResolver.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <netdutils/InternetAddresses.h>
#include <netdutils/Stopwatch.h>

#include "PrivateDnsConfiguration.h"
#include "doh_frontend.h"
#include "tests/dns_responder/dns_responder.h"
#include "tests/dns_responder/dns_responder_client_ndk.h"
#include "tests/dns_responder/dns_tls_frontend.h"
#include "tests/resolv_test_base.h"
#include "tests/resolv_test_utils.h"
#include "tests/unsolicited_listener/unsolicited_event_listener.h"

#include <android/multinetwork.h>  // ResNsendFlags
#include <arpa/inet.h>
#include <poll.h>
#include "NetdClient.h"

using aidl::android::net::resolv::aidl::IDnsResolverUnsolicitedEventListener;
using android::base::GetProperty;
using android::base::ReadFdToString;
using android::base::unique_fd;
using android::net::resolv::aidl::UnsolicitedEventListener;
using android::netdutils::IPSockAddr;
using android::netdutils::ScopedAddrinfo;
using android::netdutils::Stopwatch;
using std::chrono::milliseconds;
using std::this_thread::sleep_for;

const std::string kDohFlag("persist.device_config.netd_native.doh");
const std::string kDohQueryTimeoutFlag("persist.device_config.netd_native.doh_query_timeout_ms");
const std::string kDohProbeTimeoutFlag("persist.device_config.netd_native.doh_probe_timeout_ms");
const std::string kDohIdleTimeoutFlag("persist.device_config.netd_native.doh_idle_timeout_ms");
const std::string kDohSessionResumptionFlag(
        "persist.device_config.netd_native.doh_session_resumption");

constexpr int MAXPACKET = (8 * 1024);

namespace {

std::vector<std::string> dumpService(ndk::SpAIBinder binder) {
    unique_fd localFd, remoteFd;
    bool success = Pipe(&localFd, &remoteFd);
    EXPECT_TRUE(success) << "Failed to open pipe for dumping: " << strerror(errno);
    if (!success) return {};

    // dump() blocks until another thread has consumed all its output.
    std::thread dumpThread = std::thread([binder, remoteFd{std::move(remoteFd)}]() {
        EXPECT_EQ(STATUS_OK, AIBinder_dump(binder.get(), remoteFd, nullptr, 0));
    });

    std::string dumpContent;

    EXPECT_TRUE(ReadFdToString(localFd.get(), &dumpContent))
            << "Error during dump: " << strerror(errno);
    dumpThread.join();

    std::stringstream dumpStream(std::move(dumpContent));
    std::vector<std::string> lines;
    std::string line;
    while (std::getline(dumpStream, line)) {
        lines.push_back(std::move(line));
    }

    return lines;
}

int getAsyncResponse(int fd, int* rcode, uint8_t* buf, int bufLen) {
    struct pollfd wait_fd[1];
    wait_fd[0].fd = fd;
    wait_fd[0].events = POLLIN;
    short revents;

    if (int ret = poll(wait_fd, 1, -1); ret <= 0) {
        return -1;
    }

    revents = wait_fd[0].revents;
    if (revents & POLLIN) {
        return resNetworkResult(fd, rcode, buf, bufLen);
    }
    return -1;
}

std::string toString(uint8_t* buf, int bufLen, int ipType) {
    ns_msg handle;
    ns_rr rr;

    if (ns_initparse((const uint8_t*)buf, bufLen, &handle) >= 0) {
        if (ns_parserr(&handle, ns_s_an, 0, &rr) == 0) {
            const uint8_t* rdata = ns_rr_rdata(rr);
            char buffer[INET6_ADDRSTRLEN];
            if (inet_ntop(ipType, (const char*)rdata, buffer, sizeof(buffer))) {
                return buffer;
            }
        }
    }
    return "";
}

void expectAnswersValid(int fd, int ipType, const std::string& expectedAnswer) {
    int rcode = -1;
    uint8_t buf[MAXPACKET] = {};

    int res = getAsyncResponse(fd, &rcode, buf, MAXPACKET);
    EXPECT_GT(res, 0);
    EXPECT_EQ(expectedAnswer, toString(buf, res, ipType));
}

// A helper which can propagate the failure to outside of the stmt to know which line
// of stmt fails. The expectation fails only for the first failed stmt.
#define EXPECT_NO_FAILURE(stmt)                                         \
    do {                                                                \
        bool alreadyFailed = HasFailure();                              \
        stmt;                                                           \
        if (!alreadyFailed && HasFailure()) EXPECT_FALSE(HasFailure()); \
    } while (0)

}  // namespace

// Base class to deal with netd binder service and resolver binder service.
// TODO: derive ResolverTest from this base class.
class BaseTest : public ResolvTestBase {
  public:
    static void SetUpTestSuite() {
        // Get binder service.
        // Note that |mDnsClient| is not used for getting binder service in this static function.
        // The reason is that wants to keep |mDnsClient| as a non-static data member. |mDnsClient|
        // which sets up device network configuration could be independent from every test.
        // TODO: Perhaps add a static function in resolv_test_binder_utils.{cpp,h} to get binder
        // service.
        AIBinder* binder = AServiceManager_getService("dnsresolver");
        sResolvBinder = ndk::SpAIBinder(binder);
        auto resolvService = aidl::android::net::IDnsResolver::fromBinder(sResolvBinder);
        ASSERT_NE(nullptr, resolvService.get());

        // Subscribe the death recipient to the service IDnsResolver for detecting Netd death.
        // GTEST assertion macros are not invoked for generating a test failure in the death
        // recipient because the macros can't indicate failed test if Netd died between tests.
        // Moreover, continuing testing may have no meaningful after Netd death. Therefore, the
        // death recipient aborts process by GTEST_LOG_(FATAL) once Netd died.
        sResolvDeathRecipient = AIBinder_DeathRecipient_new([](void*) {
            constexpr char errorMessage[] = "Netd died";
            LOG(ERROR) << errorMessage;
            GTEST_LOG_(FATAL) << errorMessage;
        });
        ASSERT_EQ(STATUS_OK, AIBinder_linkToDeath(binder, sResolvDeathRecipient, nullptr));

        // Subscribe the unsolicited event listener for verifying unsolicited event contents.
        sUnsolicitedEventListener = ndk::SharedRefBase::make<UnsolicitedEventListener>(TEST_NETID);
        ASSERT_TRUE(
                resolvService->registerUnsolicitedEventListener(sUnsolicitedEventListener).isOk());

        // Start the binder thread pool for listening DNS metrics events and receiving death
        // recipient.
        ABinderProcess_startThreadPool();
    }
    static void TearDownTestSuite() { AIBinder_DeathRecipient_delete(sResolvDeathRecipient); }

  protected:
    void SetUp() {
        mDnsClient.SetUp();
        sUnsolicitedEventListener->reset();
    }

    void TearDown() {
        // Ensure the dump works at the end of each test.
        mDnsClient.TearDown();
    }

    void resetNetwork() {
        EXPECT_EQ(mDnsClient.TearDownOemNetwork(TEST_NETID), 0);
        EXPECT_EQ(mDnsClient.SetupOemNetwork(TEST_NETID), 0);
    }

    void flushCache() { mDnsClient.resolvService()->flushNetworkCache(TEST_NETID); }

    bool WaitForDotValidation(std::string serverAddr, bool validated) {
        return WaitForPrivateDnsValidation(serverAddr, validated,
                                           IDnsResolverUnsolicitedEventListener::PROTOCOL_DOT);
    }

    bool WaitForDohValidation(std::string serverAddr, bool validated) {
        return WaitForPrivateDnsValidation(serverAddr, validated,
                                           IDnsResolverUnsolicitedEventListener::PROTOCOL_DOH);
    }

    bool WaitForPrivateDnsValidation(std::string serverAddr, bool validated, int protocol) {
        return sUnsolicitedEventListener->waitForPrivateDnsValidation(
                serverAddr,
                validated ? IDnsResolverUnsolicitedEventListener::VALIDATION_RESULT_SUCCESS
                          : IDnsResolverUnsolicitedEventListener::VALIDATION_RESULT_FAILURE,
                protocol);
    }

    bool hasUncaughtPrivateDnsValidation(const std::string& serverAddr) {
        sleep_for(milliseconds(200));
        return sUnsolicitedEventListener->findValidationRecord(
                       serverAddr, IDnsResolverUnsolicitedEventListener::PROTOCOL_DOT) ||
               sUnsolicitedEventListener->findValidationRecord(
                       serverAddr, IDnsResolverUnsolicitedEventListener::PROTOCOL_DOH);
    }

    bool expectLog(const std::string& ipAddrOrNoData, const std::string& port) {
        ndk::SpAIBinder resolvBinder = ndk::SpAIBinder(AServiceManager_getService("dnsresolver"));
        assert(nullptr != resolvBinder.get());
        std::vector<std::string> lines = dumpService(resolvBinder);

        const std::string expectedLog =
                port.empty() ? ipAddrOrNoData
                             : IPSockAddr::toIPSockAddr(ipAddrOrNoData, std::stoi(port)).toString();
        const std::regex pattern(R"(^\s{4,}([0-9a-fA-F:\.\]\[]*)[ ]?([<(].*[>)])[ ]?(\S*)$)");

        for (const auto& line : lines) {
            if (line.empty()) continue;

            std::smatch match;
            if (std::regex_match(line, match, pattern)) {
                if (match[1] == expectedLog || match[2] == expectedLog) return true;
            }
        }
        return false;
    }

    DnsResponderClient mDnsClient;

    // Use a shared static DNS listener for all tests to avoid registering lots of listeners
    // which may be released late until process terminated. Currently, registered DNS listener
    // is removed by binder death notification which is fired when the process hosting an
    // IBinder has gone away. If every test registers its DNS listener, Netd
    // may temporarily hold lots of dead listeners until the unit test process terminates.
    // TODO: Perhaps add an unregistering listener binder call or fork a listener process which
    // could be terminated earlier.
    inline static std::shared_ptr<UnsolicitedEventListener> sUnsolicitedEventListener;

    // Use a shared static death recipient to monitor the service death. The static death
    // recipient could monitor the death not only during the test but also between tests.
    inline static AIBinder_DeathRecipient* sResolvDeathRecipient;

    // The linked AIBinder_DeathRecipient will be automatically unlinked if the binder is deleted.
    // The binder needs to be retained throughout tests.
    inline static ndk::SpAIBinder sResolvBinder;
};

class BasePrivateDnsTest : public BaseTest {
  public:
    static constexpr char kDnsPort[] = "53";
    static constexpr char kDohPort[] = "443";
    static constexpr char kDotPort[] = "853";

    static void SetUpTestSuite() {
        BaseTest::SetUpTestSuite();
        test::DohFrontend::initRustAndroidLogger();
    }

  protected:
    void SetUp() override {
        mDohScopedProp = make_unique<ScopedSystemProperties>(kDohFlag, "1");
        mDohQueryTimeoutScopedProp =
                make_unique<ScopedSystemProperties>(kDohQueryTimeoutFlag, "1000");
        unsigned int expectedProbeTimeout = kExpectedDohValidationTimeWhenTimeout.count();
        mDohProbeTimeoutScopedProp = make_unique<ScopedSystemProperties>(
                kDohProbeTimeoutFlag, std::to_string(expectedProbeTimeout));
        BaseTest::SetUp();

        static const std::vector<DnsRecord> records = {
                {kQueryHostname, ns_type::ns_t_a, kQueryAnswerA},
                {kQueryHostname, ns_type::ns_t_aaaa, kQueryAnswerAAAA},
        };

        for (const auto& r : records) {
            dns.addMapping(r.host_name, r.type, r.addr);
            dot_backend.addMapping(r.host_name, r.type, r.addr);
            doh_backend.addMapping(r.host_name, r.type, r.addr);
        }
    }

    void TearDown() override {
        DumpResolverService();
        mDohScopedProp.reset();
        BaseTest::TearDown();
    }

    void sendQueryAndCheckResult(const char* host_name = kQueryHostname) {
        const addrinfo hints = {.ai_socktype = SOCK_DGRAM};
        ScopedAddrinfo result = safe_getaddrinfo(host_name, nullptr, &hints);
        EXPECT_THAT(ToStrings(result),
                    testing::ElementsAreArray({kQueryAnswerAAAA, kQueryAnswerA}));
    };

    void expectQueries(int dnsQueries, int dotQueries, int dohQueries) {
        EXPECT_EQ(dns.queries().size(), static_cast<size_t>(dnsQueries));
        EXPECT_EQ(dot.queries(), dotQueries);
        EXPECT_EQ(doh.queries(), dohQueries);
    }

    // Used when a DoH probe is sent while the DoH server doesn't respond.
    void waitForDohValidationTimeout() {
        std::this_thread::sleep_for(kExpectedDohValidationTimeWhenTimeout);
    }

    // Used when a DoH probe is sent while the DoH server is not listening on the port.
    void waitForDohValidationFailed() {
        std::this_thread::sleep_for(kExpectedDohValidationTimeWhenServerUnreachable);
    }

    void DumpResolverService() {
        unique_fd fd(open("/dev/null", O_WRONLY));
        EXPECT_EQ(mDnsClient.resolvService()->dump(fd, nullptr, 0), 0);

        const char* querylogCmd[] = {"querylog"};  // Keep it sync with DnsQueryLog::DUMP_KEYWORD.
        EXPECT_EQ(mDnsClient.resolvService()->dump(fd, querylogCmd, std::size(querylogCmd)), 0);
    }

    static constexpr milliseconds kExpectedDohValidationTimeWhenTimeout{1000};
    static constexpr milliseconds kExpectedDohValidationTimeWhenServerUnreachable{1000};
    static constexpr char kQueryHostname[] = "TransportParameterizedTest.example.com.";
    static constexpr char kQueryAnswerA[] = "1.2.3.4";
    static constexpr char kQueryAnswerAAAA[] = "2001:db8::100";

    test::DNSResponder dns{test::kDefaultListenAddr, kDnsPort};
    test::DohFrontend doh{test::kDefaultListenAddr, kDohPort, "127.0.1.3", kDnsPort};
    test::DnsTlsFrontend dot{test::kDefaultListenAddr, kDotPort, "127.0.2.3", kDnsPort};
    test::DNSResponder doh_backend{"127.0.1.3", kDnsPort};
    test::DNSResponder dot_backend{"127.0.2.3", kDnsPort};

    // Used to enable DoH during the tests and set up a shorter timeout.
    std::unique_ptr<ScopedSystemProperties> mDohScopedProp;
    std::unique_ptr<ScopedSystemProperties> mDohQueryTimeoutScopedProp;
    std::unique_ptr<ScopedSystemProperties> mDohProbeTimeoutScopedProp;
};

// Parameterized test for the combination of DoH and DoT.
//  - DoT: the assigned private DNS servers support DoT only.
//  - DoH: the assigned private DNS servers support DoH only.
//  - DOT + DoH: the assigned private DNS servers support both DoT and DoH.
class TransportParameterizedTest : public BasePrivateDnsTest,
                                   public testing::WithParamInterface<uint8_t> {
  public:
    static constexpr uint8_t kDotBit = 0x01;
    static constexpr uint8_t kDohBit = 0x02;
    static constexpr std::array<uint8_t, 3> sParams = {kDotBit, kDohBit, kDotBit | kDohBit};

  protected:
    void SetUp() override {
        BasePrivateDnsTest::SetUp();

        ASSERT_TRUE(dns.startServer());
        if (testParamHasDot()) {
            ASSERT_TRUE(dot_backend.startServer());
            ASSERT_TRUE(dot.startServer());
        }
        if (testParamHasDoh()) {
            ASSERT_TRUE(doh_backend.startServer());
            ASSERT_TRUE(doh.startServer());
        }
        SetMdnsRoute();
    }

    void TearDown() override {
        RemoveMdnsRoute();
        BasePrivateDnsTest::TearDown();
    }

    bool testParamHasDot() { return GetParam() & kDotBit; }
    bool testParamHasDoh() { return GetParam() & kDohBit; }
};

INSTANTIATE_TEST_SUITE_P(PrivateDns, TransportParameterizedTest,
                         testing::ValuesIn(TransportParameterizedTest::sParams),
                         [](const testing::TestParamInfo<uint8_t>& info) {
                             std::string name;
                             if (info.param & TransportParameterizedTest::kDotBit) name += "DoT";
                             if (info.param & TransportParameterizedTest::kDohBit) name += "DoH";
                             return name;
                         });

TEST_P(TransportParameterizedTest, GetAddrInfo) {
    const auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

    if (testParamHasDoh()) EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    if (testParamHasDot()) EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));

    // This waiting time is expected to avoid that the DoH validation event interferes other tests.
    if (!testParamHasDoh()) waitForDohValidationFailed();

    // Have the test independent of the number of sent queries in private DNS validation, because
    // the DnsResolver can send either 1 or 2 queries in DoT validation.
    if (testParamHasDoh()) {
        doh.clearQueries();
    }
    if (testParamHasDot()) {
        EXPECT_TRUE(dot.waitForQueries(1));
        dot.clearQueries();
    }
    dns.clearQueries();

    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    if (testParamHasDoh()) {
        EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 2 /* doh */));
    } else {
        EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 2 /* dot */, 0 /* doh */));
    }

    // Stop the private DNS servers. Since we are in opportunistic mode, queries will
    // fall back to the cleartext nameserver.
    flushCache();
    dot.stopServer();
    doh.stopServer();

    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    if (testParamHasDoh()) {
        EXPECT_NO_FAILURE(expectQueries(2 /* dns */, 0 /* dot */, 2 /* doh */));
    } else {
        EXPECT_NO_FAILURE(expectQueries(2 /* dns */, 2 /* dot */, 0 /* doh */));
    }
}

TEST_P(TransportParameterizedTest, MdnsGetAddrInfo_fallback) {
    constexpr char host_name[] = "hello.local.";
    test::DNSResponder mdnsv4("127.0.0.3", test::kDefaultMdnsListenService,
                              static_cast<ns_rcode>(-1));
    test::DNSResponder mdnsv6("::1", test::kDefaultMdnsListenService, static_cast<ns_rcode>(-1));
    // Set unresponsive on multicast.
    mdnsv4.setResponseProbability(0.0);
    mdnsv6.setResponseProbability(0.0);
    ASSERT_TRUE(mdnsv4.startServer());
    ASSERT_TRUE(mdnsv6.startServer());

    const std::vector<DnsRecord> records = {
            {host_name, ns_type::ns_t_a, kQueryAnswerA},
            {host_name, ns_type::ns_t_aaaa, kQueryAnswerAAAA},
    };

    for (const auto& r : records) {
        dns.addMapping(r.host_name, r.type, r.addr);
        dot_backend.addMapping(r.host_name, r.type, r.addr);
        doh_backend.addMapping(r.host_name, r.type, r.addr);
    }

    auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

    if (testParamHasDoh()) EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    if (testParamHasDot()) EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));

    // This waiting time is expected to avoid that the DoH validation event interferes other tests.
    if (!testParamHasDoh()) waitForDohValidationFailed();

    // Have the test independent of the number of sent queries in private DNS validation, because
    // the DnsResolver can send either 1 or 2 queries in DoT validation.
    if (testParamHasDoh()) {
        doh.clearQueries();
    }
    if (testParamHasDot()) {
        EXPECT_TRUE(dot.waitForQueries(1));
        dot.clearQueries();
    }
    dns.clearQueries();

    EXPECT_NO_FAILURE(sendQueryAndCheckResult("hello.local"));
    EXPECT_EQ(1U, GetNumQueries(mdnsv4, host_name));
    EXPECT_EQ(1U, GetNumQueries(mdnsv6, host_name));
    if (testParamHasDoh()) {
        EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 2 /* doh */));
    } else {
        EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 2 /* dot */, 0 /* doh */));
    }

    // Stop the private DNS servers. Since we are in opportunistic mode, queries will
    // fall back to the cleartext nameserver.
    flushCache();
    dot.stopServer();
    doh.stopServer();
    mdnsv4.clearQueries();
    mdnsv6.clearQueries();

    EXPECT_NO_FAILURE(sendQueryAndCheckResult("hello.local"));
    EXPECT_EQ(1U, GetNumQueries(mdnsv4, host_name));
    EXPECT_EQ(1U, GetNumQueries(mdnsv6, host_name));
    if (testParamHasDoh()) {
        EXPECT_NO_FAILURE(expectQueries(2 /* dns */, 0 /* dot */, 2 /* doh */));
    } else {
        EXPECT_NO_FAILURE(expectQueries(2 /* dns */, 2 /* dot */, 0 /* doh */));
    }
}

class PrivateDnsDohTest : public BasePrivateDnsTest {
  protected:
    void SetUp() override {
        BasePrivateDnsTest::SetUp();

        ASSERT_TRUE(dns.startServer());
        ASSERT_TRUE(dot_backend.startServer());
        ASSERT_TRUE(dot.startServer());
        ASSERT_TRUE(doh_backend.startServer());
        ASSERT_TRUE(doh.startServer());
    }
};

// Tests that DoH validation doesn't take much time in the following scenario:
//   - DoH server is unreachable.
//   - DoH server does not respond.
TEST_F(PrivateDnsDohTest, ValidationFail) {
    using std::chrono::microseconds;

    constexpr milliseconds TIMING_TOLERANCE{1000};

    // Make the DoT server broken so that the test can receive the validation event of both
    // DoT and DoH, so we can calculate the time taken on DoH validation.
    dot.stopServer();

    // Set the DoH server unreachable.
    doh.stopServer();

    Stopwatch s;
    const auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, false));
    EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, false));
    EXPECT_LT(s.getTimeAndResetUs(),
              microseconds(kExpectedDohValidationTimeWhenServerUnreachable + TIMING_TOLERANCE)
                      .count());

    // Set the DoH server unresponsive.
    ASSERT_TRUE(doh.startServer());
    doh_backend.setResponseProbability(0.0);
    doh_backend.setErrorRcode(static_cast<ns_rcode>(-1));

    s.getTimeAndResetUs();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, false));
    EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, false));
    EXPECT_LT(s.getTimeAndResetUs(),
              microseconds(kExpectedDohValidationTimeWhenTimeout + TIMING_TOLERANCE).count());

    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    EXPECT_FALSE(hasUncaughtPrivateDnsValidation(test::kDefaultListenAddr));
}

// Tests that DoH query fails and fallback happens.
//   - Fallback to UDP if DoH query times out
//   - Fallback to DoT if DoH validation is in progress or has failed.
TEST_F(PrivateDnsDohTest, QueryFailover) {
    const auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(dot.waitForQueries(1));
    dot.clearQueries();
    dns.clearQueries();

    doh_backend.setResponseProbability(0.0);
    doh_backend.setErrorRcode(static_cast<ns_rcode>(-1));

    // Expect that the query fall back to UDP.
    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    EXPECT_EQ(dot.queries(), 0);
    EXPECT_EQ(dns.queries().size(), 2U);
    flushCache();

    resetNetwork();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

    EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(dot.waitForQueries(1));
    dot.clearQueries();
    dns.clearQueries();

    // Expect that the query fall back to DoT as DoH validation is in progress.
    EXPECT_NO_FAILURE(sendQueryAndCheckResult());

    EXPECT_EQ(dot.queries(), 2);
    EXPECT_EQ(dns.queries().size(), 0U);
    waitForDohValidationTimeout();
    flushCache();

    // Expect that this query fall back to DoT as DoH validation has failed.
    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    EXPECT_EQ(dot.queries(), 4);
    EXPECT_EQ(dns.queries().size(), 0U);
}

// Tests that the DnsResolver prioritizes IPv6 DoH servers over IPv4 DoH servers.
TEST_F(PrivateDnsDohTest, PreferIpv6) {
    constexpr char listen_ipv6_addr[] = "::1";
    const std::vector<std::vector<std::string>> testConfig = {
            {test::kDefaultListenAddr, listen_ipv6_addr},
            {listen_ipv6_addr, test::kDefaultListenAddr},
    };

    // To simplify the test, set the DoT server broken.
    dot.stopServer();

    test::DNSResponder dns_ipv6{listen_ipv6_addr, kDnsPort};
    test::DohFrontend doh_ipv6{listen_ipv6_addr, kDohPort, listen_ipv6_addr, kDnsPort};
    dns_ipv6.addMapping(kQueryHostname, ns_type::ns_t_a, kQueryAnswerA);
    dns_ipv6.addMapping(kQueryHostname, ns_type::ns_t_aaaa, kQueryAnswerAAAA);
    ASSERT_TRUE(dns_ipv6.startServer());
    ASSERT_TRUE(doh_ipv6.startServer());

    for (const auto& serverList : testConfig) {
        SCOPED_TRACE(fmt::format("serverList: [{}]", fmt::join(serverList, ", ")));

        auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
        parcel.servers = serverList;
        parcel.tlsServers = serverList;
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

        // Currently, DnsResolver sorts the server list and did DoH validation only
        // for the first server.
        EXPECT_TRUE(WaitForDohValidation(listen_ipv6_addr, true));

        doh.clearQueries();
        doh_ipv6.clearQueries();

        EXPECT_NO_FAILURE(sendQueryAndCheckResult());
        EXPECT_EQ(doh_ipv6.queries(), 2);
        EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 0 /* doh */));

        resetNetwork();
    }
}

// Tests that DoH server setting can be replaced/removed correctly.
TEST_F(PrivateDnsDohTest, ChangeAndClearPrivateDnsServer) {
    constexpr char listen_ipv6_addr[] = "::1";

    // To simplify the test, set the DoT server broken.
    dot.stopServer();

    test::DNSResponder dns_ipv6{listen_ipv6_addr, kDnsPort};
    test::DohFrontend doh_ipv6{listen_ipv6_addr, kDohPort, listen_ipv6_addr, kDnsPort};
    dns_ipv6.addMapping(kQueryHostname, ns_type::ns_t_a, kQueryAnswerA);
    dns_ipv6.addMapping(kQueryHostname, ns_type::ns_t_aaaa, kQueryAnswerAAAA);
    ASSERT_TRUE(dns_ipv6.startServer());
    ASSERT_TRUE(doh_ipv6.startServer());

    auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

    // Use v4 DoH server first.
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    doh.clearQueries();
    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 2 /* doh */));

    // Change to the v6 DoH server.
    parcel.servers = {listen_ipv6_addr};
    parcel.tlsServers = {listen_ipv6_addr};
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(listen_ipv6_addr, true));
    doh.clearQueries();
    doh_ipv6.clearQueries();
    flushCache();
    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    EXPECT_EQ(doh_ipv6.queries(), 2);
    EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 0 /* doh */));

    // Change to an invalid DoH server.
    parcel.tlsServers = {kHelloExampleComAddrV4};
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    doh_ipv6.clearQueries();
    dns_ipv6.clearQueries();
    flushCache();
    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    EXPECT_EQ(doh_ipv6.queries(), 0);
    EXPECT_EQ(dns_ipv6.queries().size(), 2U);

    // Remove private DNS servers.
    parcel.tlsServers = {};
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    doh_ipv6.clearQueries();
    dns_ipv6.clearQueries();
    flushCache();
    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    EXPECT_EQ(doh_ipv6.queries(), 0);
    EXPECT_EQ(dns_ipv6.queries().size(), 2U);
}

TEST_F(PrivateDnsDohTest, ChangePrivateDnsServerAndVerifyOutput) {
    // To simplify the test, set the DoT server broken.
    dot.stopServer();

    static const std::string ipv4DohServerAddr = "127.0.0.3";
    static const std::string ipv6DohServerAddr = "::1";

    test::DNSResponder dns_ipv6{ipv6DohServerAddr, kDnsPort};
    test::DohFrontend doh_ipv6{ipv6DohServerAddr, kDohPort, ipv6DohServerAddr, kDnsPort};
    dns.addMapping(kQueryHostname, ns_type::ns_t_a, kQueryAnswerA);
    dns.addMapping(kQueryHostname, ns_type::ns_t_aaaa, kQueryAnswerAAAA);
    dns_ipv6.addMapping(kQueryHostname, ns_type::ns_t_a, kQueryAnswerA);
    dns_ipv6.addMapping(kQueryHostname, ns_type::ns_t_aaaa, kQueryAnswerAAAA);
    ASSERT_TRUE(dns_ipv6.startServer());
    ASSERT_TRUE(doh_ipv6.startServer());

    // Start the v4 DoH server.
    auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(expectLog(ipv4DohServerAddr, kDohPort));

    // Change to an invalid DoH server.
    parcel.tlsServers = {kHelloExampleComAddrV4};
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_FALSE(expectLog(kHelloExampleComAddrV4, kDohPort));
    EXPECT_TRUE(expectLog("<no data>", ""));

    // Change to the v6 DoH server.
    parcel.servers = {ipv6DohServerAddr};
    parcel.tlsServers = {ipv6DohServerAddr};
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(ipv6DohServerAddr, true));
    EXPECT_TRUE(expectLog(ipv6DohServerAddr, kDohPort));
    EXPECT_FALSE(expectLog(ipv4DohServerAddr, kDohPort));

    // Remove the private DNS server.
    parcel.tlsServers = {};
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_FALSE(expectLog(ipv4DohServerAddr, kDohPort));
    EXPECT_FALSE(expectLog(ipv6DohServerAddr, kDohPort));
    EXPECT_TRUE(expectLog("<no data>", ""));
}

// Tests that a DoH query is sent while the network is stalled temporarily.
TEST_F(PrivateDnsDohTest, TemporaryConnectionStalled) {
    const int connectionStalledTimeMs = 3000;
    ScopedSystemProperties sp(kDohQueryTimeoutFlag, "10000");
    resetNetwork();

    const auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(dot.waitForQueries(1));
    dot.clearQueries();
    doh.clearQueries();
    dns.clearQueries();

    EXPECT_TRUE(doh.block_sending(true));
    Stopwatch s;
    int fd = resNetworkQuery(TEST_NETID, kQueryHostname, ns_c_in, ns_t_a,
                             ANDROID_RESOLV_NO_CACHE_LOOKUP);
    sleep_for(milliseconds(connectionStalledTimeMs));
    EXPECT_TRUE(doh.block_sending(false));

    expectAnswersValid(fd, AF_INET, kQueryAnswerA);
    EXPECT_GT(s.timeTakenUs() / 1000, connectionStalledTimeMs);
    EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 1 /* doh */));
}

// (b/207301204): Tests that the DnsResolver will try DoT rather than DoH if there are excess
// DNS requests. In addition, tests that sending DNS requests to other networks succeeds.
// Note: This test is subject to MAX_BUFFERED_COMMANDS. If the value is changed, this test might
// need to be modified as well.
TEST_F(PrivateDnsDohTest, ExcessDnsRequests) {
    const int total_queries = 70;

    // The number is from MAX_BUFFERED_COMMANDS + 2 (one that will be queued in
    // connection mpsc channel; the other one that will get blocked at dispatcher sending channel).
    const int timeout_queries = 52;

    const int initial_max_idle_timeout_ms = 2000;
    ASSERT_TRUE(doh.stopServer());
    EXPECT_TRUE(doh.setMaxIdleTimeout(initial_max_idle_timeout_ms));
    ASSERT_TRUE(doh.startServer());

    auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(dot.waitForQueries(1));
    dot.clearQueries();
    doh.clearQueries();
    dns.clearQueries();

    // Set the DoT server not to close the connection until it receives enough queries or timeout.
    dot.setDelayQueries(total_queries - timeout_queries);
    dot.setDelayQueriesTimeout(200);

    // Set the server blocking, wait for the connection closed, and send some DNS requests.
    EXPECT_TRUE(doh.block_sending(true));
    EXPECT_TRUE(doh.waitForAllClientsDisconnected());
    std::array<int, total_queries> fds;
    for (auto& fd : fds) {
        fd = resNetworkQuery(TEST_NETID, kQueryHostname, ns_c_in, ns_t_aaaa,
                             ANDROID_RESOLV_NO_CACHE_LOOKUP);
    }
    for (const auto& fd : fds) {
        expectAnswersValid(fd, AF_INET6, kQueryAnswerAAAA);
    }
    EXPECT_TRUE(doh.block_sending(false));

    // There are some queries that fall back to DoT rather than UDP since the DoH client rejects
    // any new DNS requests when the capacity is full.
    EXPECT_NO_FAILURE(expectQueries(timeout_queries /* dns */,
                                    total_queries - timeout_queries /* dot */, 0 /* doh */));

    // Set up another network and send a DNS query. Expect that this network is unaffected.
    constexpr int TEST_NETID_2 = 31;
    constexpr char listen_ipv6_addr[] = "::1";
    test::DNSResponder dns_ipv6{listen_ipv6_addr, kDnsPort};
    test::DnsTlsFrontend dot_ipv6{listen_ipv6_addr, kDotPort, listen_ipv6_addr, kDnsPort};
    test::DohFrontend doh_ipv6{listen_ipv6_addr, kDohPort, listen_ipv6_addr, kDnsPort};

    dns_ipv6.addMapping(kQueryHostname, ns_type::ns_t_aaaa, kQueryAnswerAAAA);
    ASSERT_TRUE(dns_ipv6.startServer());
    ASSERT_TRUE(dot_ipv6.startServer());
    ASSERT_TRUE(doh_ipv6.startServer());
    mDnsClient.SetupOemNetwork(TEST_NETID_2);

    parcel.netId = TEST_NETID_2;
    parcel.servers = {listen_ipv6_addr};
    parcel.tlsServers = {listen_ipv6_addr};
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));

    // Sleep a while to wait for DoH and DoT validation.
    // TODO: Extend WaitForDohValidation() to support passing a netId.
    sleep_for(milliseconds(200));
    EXPECT_TRUE(dot_ipv6.waitForQueries(1));

    int fd = resNetworkQuery(TEST_NETID_2, kQueryHostname, ns_c_in, ns_t_aaaa,
                             ANDROID_RESOLV_NO_CACHE_LOOKUP);
    expectAnswersValid(fd, AF_INET6, kQueryAnswerAAAA);

    // Expect two queries: one for DoH probe and the other one for kQueryHostname.
    EXPECT_EQ(doh_ipv6.queries(), 2);
    mDnsClient.TearDownOemNetwork(TEST_NETID_2);

    // The DnsResolver will reconnect to the DoH server for the query that gets blocked at
    // dispatcher sending channel. However, there's no way to know when the reconnection will start.
    // We have to periodically send a DNS request to check it. After the reconnection starts, the
    // DNS query will be sent to the Doh server instead of the cleartext DNS server. Then, we
    // are safe to end the test. Otherwise, the reconnection will interfere other tests.
    EXPECT_EQ(doh.queries(), 0);
    for (int i = 0; i < 50; i++) {
        sleep_for(milliseconds(100));
        int fd = resNetworkQuery(TEST_NETID, kQueryHostname, ns_c_in, ns_t_aaaa,
                                 ANDROID_RESOLV_NO_CACHE_LOOKUP);
        expectAnswersValid(fd, AF_INET6, kQueryAnswerAAAA);
        if (doh.queries() > 0) break;
    }
    EXPECT_GT(doh.queries(), 0);
}

// Tests the scenario where the DnsResolver runs out of QUIC connection data limit.
TEST_F(PrivateDnsDohTest, RunOutOfDataLimit) {
    // Each DoH query consumes about 100 bytes of QUIC connection send capacity.
    // Set initial_max_data to 450 so the fifth DoH query will get blocked.
    const int queries = 4;
    const int initial_max_data = 450;

    ScopedSystemProperties sp(kDohQueryTimeoutFlag, "3000");
    resetNetwork();

    ASSERT_TRUE(doh.stopServer());
    EXPECT_TRUE(doh.setMaxBufferSize(initial_max_data));
    ASSERT_TRUE(doh.startServer());

    const auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(dot.waitForQueries(1));
    dot.clearQueries();
    doh.clearQueries();
    dns.clearQueries();

    // Block the DoH server from sending data for a while.
    EXPECT_TRUE(doh.block_sending(true));
    std::vector<std::thread> threads(queries);
    for (std::thread& thread : threads) {
        thread = std::thread([&]() {
            int fd = resNetworkQuery(TEST_NETID, kQueryHostname, ns_c_in, ns_t_a,
                                     ANDROID_RESOLV_NO_CACHE_LOOKUP);
            expectAnswersValid(fd, AF_INET, kQueryAnswerA);
        });
    }
    sleep_for(milliseconds(500));
    EXPECT_TRUE(doh.block_sending(false));

    // In current implementation, the fifth DoH query will get blocked and result in timeout.
    int fd = resNetworkQuery(TEST_NETID, kQueryHostname, ns_c_in, ns_t_a,
                             ANDROID_RESOLV_NO_CACHE_LOOKUP);
    expectAnswersValid(fd, AF_INET, kQueryAnswerA);

    for (std::thread& thread : threads) {
        thread.join();
    }

    // TODO: see how we can improve the DnsResolver to make all of the DNS queries resolved by DoH.
    // EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 5 /* doh */));
}

// Tests the scenario where the DnsResolver runs out of QUIC streams limit.
TEST_F(PrivateDnsDohTest, RunOutOfStreams) {
    const int queries = 6;
    const int initial_max_streams_bidi = 5;

    // Since the last query won't be issued until there are streams available, lengthen the
    // timeout to 3 seconds.
    ScopedSystemProperties sp(kDohQueryTimeoutFlag, "3000");
    resetNetwork();

    ASSERT_TRUE(doh.stopServer());
    EXPECT_TRUE(doh.setMaxStreamsBidi(initial_max_streams_bidi));
    ASSERT_TRUE(doh.startServer());

    const auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(dot.waitForQueries(1));
    dot.clearQueries();
    doh.clearQueries();
    dns.clearQueries();

    // Block the DoH server from sending data for a while.
    EXPECT_TRUE(doh.block_sending(true));
    std::vector<std::thread> threads(queries);
    for (std::thread& thread : threads) {
        thread = std::thread([&]() {
            int fd = resNetworkQuery(TEST_NETID, kQueryHostname, ns_c_in, ns_t_a,
                                     ANDROID_RESOLV_NO_CACHE_LOOKUP);
            expectAnswersValid(fd, AF_INET, kQueryAnswerA);
        });
    }
    sleep_for(milliseconds(500));
    EXPECT_TRUE(doh.block_sending(false));

    for (std::thread& thread : threads) {
        thread.join();
    }

    EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 6 /* doh */));
}

// Tests that the DnsResolver automatically reconnects to the DoH server when needed.
// Session resumption should be used in each reconnection.
TEST_F(PrivateDnsDohTest, ReconnectAfterIdleTimeout) {
    const int initial_max_idle_timeout_ms = 1000;

    ASSERT_TRUE(doh.stopServer());
    EXPECT_TRUE(doh.setMaxIdleTimeout(initial_max_idle_timeout_ms));
    ASSERT_TRUE(doh.startServer());

    const auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(dot.waitForQueries(1));
    dot.clearQueries();
    doh.clearQueries();
    dns.clearQueries();

    for (int i = 0; i < 5; i++) {
        SCOPED_TRACE(fmt::format("Round: {}", i));
        sleep_for(milliseconds(initial_max_idle_timeout_ms + 500));

        // As the connection is closed, the DnsResolver will reconnect to the DoH server
        // for this DNS request.
        int fd = resNetworkQuery(TEST_NETID, kQueryHostname, ns_c_in, ns_t_a,
                                 ANDROID_RESOLV_NO_CACHE_LOOKUP);
        expectAnswersValid(fd, AF_INET, kQueryAnswerA);
    }

    EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 5 /* doh */));
    EXPECT_EQ(doh.connections(), 6);
}

// Tests that the experiment flag doh_idle_timeout_ms is effective.
TEST_F(PrivateDnsDohTest, ConnectionIdleTimer) {
    const int connection_idle_timeout = 1500;
    const int tolerance_ms = 200;

    // Check if the default value or the timeout the device is using is too short for the test.
    const int device_connection_idle_timeout =
            std::min(std::stoi(GetProperty(kDohIdleTimeoutFlag, "9999")),
                     android::net::PrivateDnsConfiguration::kDohIdleDefaultTimeoutMs);
    if (device_connection_idle_timeout <= connection_idle_timeout + tolerance_ms) {
        GTEST_LOG_(INFO) << "The test can't guarantee that the flag takes effect because "
                         << "device_connection_idle_timeout is too short: "
                         << device_connection_idle_timeout << " ms.";
    }

    ScopedSystemProperties sp(kDohIdleTimeoutFlag, std::to_string(connection_idle_timeout));
    resetNetwork();

    const auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(dot.waitForQueries(1));
    dot.clearQueries();
    doh.clearQueries();
    dns.clearQueries();

    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 2 /* doh */));
    flushCache();
    EXPECT_EQ(doh.connections(), 1);

    // Expect that the DoH connection gets disconnected while sleeping.
    sleep_for(milliseconds(connection_idle_timeout + tolerance_ms));

    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 4 /* doh */));
    EXPECT_EQ(doh.connections(), 2);
}

// Tests that the flag "doh_session_resumption" works as expected.
TEST_F(PrivateDnsDohTest, SessionResumption) {
    const int initial_max_idle_timeout_ms = 1000;
    for (const auto& flag : {"0", "1"}) {
        SCOPED_TRACE(fmt::format("flag: {}", flag));
        auto sp = make_unique<ScopedSystemProperties>(kDohSessionResumptionFlag, flag);

        // Each loop takes around 3 seconds, if the system property "doh" is reset in the middle
        // of the first loop, this test will fail when running the second loop because DnsResolver
        // updates its "doh" flag when resetNetwork() is called. Therefore, add another
        // ScopedSystemProperties for "doh" to make the test more robust.
        auto sp2 = make_unique<ScopedSystemProperties>(kDohFlag, "1");
        resetNetwork();

        ASSERT_TRUE(doh.stopServer());
        EXPECT_TRUE(doh.setMaxIdleTimeout(initial_max_idle_timeout_ms));
        ASSERT_TRUE(doh.startServer());

        const auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
        ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
        EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
        EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));
        EXPECT_TRUE(dot.waitForQueries(1));
        dot.clearQueries();
        doh.clearQueries();
        dns.clearQueries();

        for (int i = 0; i < 2; i++) {
            SCOPED_TRACE(fmt::format("Round: {}", i));
            sleep_for(milliseconds(initial_max_idle_timeout_ms + 500));

            // As the connection is closed, the DnsResolver will reconnect to the DoH server
            // for this DNS request.
            int fd = resNetworkQuery(TEST_NETID, kQueryHostname, ns_c_in, ns_t_a,
                                     ANDROID_RESOLV_NO_CACHE_LOOKUP);
            expectAnswersValid(fd, AF_INET, kQueryAnswerA);
        }

        EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 2 /* doh */));
        EXPECT_EQ(doh.connections(), 3);
        EXPECT_EQ(doh.resumedConnections(), (flag == "1" ? 2 : 0));
    }
}

// Tests that after the connection is closed by the server (known by sending CONNECTION_CLOSE
// frame), the DnsResolver can initiate another new connection for DNS requests.
TEST_F(PrivateDnsDohTest, RemoteConnectionClosed) {
    const auto parcel = DnsResponderClient::GetDefaultResolverParamsParcel();
    ASSERT_TRUE(mDnsClient.SetResolversFromParcel(parcel));
    EXPECT_TRUE(WaitForDohValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(WaitForDotValidation(test::kDefaultListenAddr, true));
    EXPECT_TRUE(dot.waitForQueries(1));
    dot.clearQueries();
    doh.clearQueries();
    dns.clearQueries();

    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 2 /* doh */));
    flushCache();
    EXPECT_EQ(doh.connections(), 1);

    // Make the server close the connection. This will also reset the stats, so the doh query
    // count below is still 2 rather than 4.
    ASSERT_TRUE(doh.stopServer());
    ASSERT_TRUE(doh.startServer());

    EXPECT_NO_FAILURE(sendQueryAndCheckResult());
    EXPECT_NO_FAILURE(expectQueries(0 /* dns */, 0 /* dot */, 2 /* doh */));
    EXPECT_EQ(doh.connections(), 1);
}

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

#include <aidl/android/net/connectivity/aidl/ConnectivityNative.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android-modules-utils/sdk_level.h>
#include <cutils/misc.h>  // FIRST_APPLICATION_UID
#include <gtest/gtest.h>
#include <netinet/in.h>

#include "bpf/BpfUtils.h"

using aidl::android::net::connectivity::aidl::IConnectivityNative;

class ConnectivityNativeBinderTest : public ::testing::Test {
  public:
    std::vector<int32_t> mActualBlockedPorts;

    ConnectivityNativeBinderTest() {
        AIBinder* binder = AServiceManager_getService("connectivity_native");
        ndk::SpAIBinder sBinder = ndk::SpAIBinder(binder);
        mService = aidl::android::net::connectivity::aidl::IConnectivityNative::fromBinder(sBinder);
    }

    void SetUp() override {
        // Skip test case if not on T.
        if (!android::modules::sdklevel::IsAtLeastT()) GTEST_SKIP() <<
                "Should be at least T device.";

        // Skip test case if not on 5.4 kernel which is required by bpf prog.
        if (!android::bpf::isAtLeastKernelVersion(5, 4, 0)) GTEST_SKIP() <<
                "Kernel should be at least 5.4.";

        ASSERT_NE(nullptr, mService.get());

        // If there are already ports being blocked on device unblockAllPortsForBind() store
        // the currently blocked ports and add them back at the end of the test. Do this for
        // every test case so additional test cases do not forget to add ports back.
        ndk::ScopedAStatus status = mService->getPortsBlockedForBind(&mActualBlockedPorts);
        EXPECT_TRUE(status.isOk()) << status.getDescription ();

    }

    void TearDown() override {
        ndk::ScopedAStatus status;
        if (mActualBlockedPorts.size() > 0) {
            for (int i : mActualBlockedPorts) {
                mService->blockPortForBind(i);
                EXPECT_TRUE(status.isOk()) << status.getDescription ();
            }
        }
    }

  protected:
    std::shared_ptr<IConnectivityNative> mService;

    void runSocketTest (sa_family_t family, const int type, bool blockPort) {
        ndk::ScopedAStatus status;
        in_port_t port = 0;
        int sock, sock2;
        // Open two sockets with SO_REUSEADDR and expect they can both bind to port.
        sock = openSocket(&port, family, type, false /* expectBindFail */);
        sock2 = openSocket(&port, family, type, false /* expectBindFail */);

        int blockedPort = 0;
        if (blockPort) {
            blockedPort = ntohs(port);
            status = mService->blockPortForBind(blockedPort);
            EXPECT_TRUE(status.isOk()) << status.getDescription ();
        }

        int sock3 = openSocket(&port, family, type, blockPort /* expectBindFail */);

        if (blockPort) {
            EXPECT_EQ(-1, sock3);
            status = mService->unblockPortForBind(blockedPort);
            EXPECT_TRUE(status.isOk()) << status.getDescription ();
        } else {
            EXPECT_NE(-1, sock3);
        }

        close(sock);
        close(sock2);
        close(sock3);
    }

    /*
    * Open the socket and update the port.
    */
    int openSocket(in_port_t* port, sa_family_t family, const int type, bool expectBindFail) {
        int ret = 0;
        int enable = 1;
        const int sock = socket(family, type, 0);
        ret = setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &enable, sizeof(enable));
        EXPECT_EQ(0, ret);

        if (family == AF_INET) {
            struct sockaddr_in addr4 = { .sin_family = family, .sin_port = htons(*port) };
            ret = bind(sock, (struct sockaddr*) &addr4, sizeof(addr4));
        } else {
            struct sockaddr_in6 addr6 = { .sin6_family = family, .sin6_port = htons(*port) };
            ret = bind(sock, (struct sockaddr*) &addr6, sizeof(addr6));
        }

        if (expectBindFail) {
            EXPECT_NE(0, ret);
            // If port is blocked, return here since the port is not needed
            // for subsequent sockets.
            close(sock);
            return -1;
        }
        EXPECT_EQ(0, ret) << "bind unexpectedly failed, errno: " << errno;

        if (family == AF_INET) {
            struct sockaddr_in sin;
            socklen_t len = sizeof(sin);
            EXPECT_NE(-1, getsockname(sock, (struct sockaddr *)&sin, &len));
            EXPECT_NE(0, ntohs(sin.sin_port));
            if (*port != 0) EXPECT_EQ(*port, ntohs(sin.sin_port));
            *port = ntohs(sin.sin_port);
        } else {
            struct sockaddr_in6 sin;
            socklen_t len = sizeof(sin);
            EXPECT_NE(-1, getsockname(sock, (struct sockaddr *)&sin, &len));
            EXPECT_NE(0, ntohs(sin.sin6_port));
            if (*port != 0) EXPECT_EQ(*port, ntohs(sin.sin6_port));
            *port = ntohs(sin.sin6_port);
        }
        return sock;
    }
};

TEST_F(ConnectivityNativeBinderTest, PortUnblockedV4Udp) {
    runSocketTest(AF_INET, SOCK_DGRAM, false);
}

TEST_F(ConnectivityNativeBinderTest, PortUnblockedV4Tcp) {
    runSocketTest(AF_INET, SOCK_STREAM, false);
}

TEST_F(ConnectivityNativeBinderTest, PortUnblockedV6Udp) {
    runSocketTest(AF_INET6, SOCK_DGRAM, false);
}

TEST_F(ConnectivityNativeBinderTest, PortUnblockedV6Tcp) {
    runSocketTest(AF_INET6, SOCK_STREAM, false);
}

TEST_F(ConnectivityNativeBinderTest, BlockPort4Udp) {
    runSocketTest(AF_INET, SOCK_DGRAM, true);
}

TEST_F(ConnectivityNativeBinderTest, BlockPort4Tcp) {
    runSocketTest(AF_INET, SOCK_STREAM, true);
}

TEST_F(ConnectivityNativeBinderTest, BlockPort6Udp) {
    runSocketTest(AF_INET6, SOCK_DGRAM, true);
}

TEST_F(ConnectivityNativeBinderTest, BlockPort6Tcp) {
    runSocketTest(AF_INET6, SOCK_STREAM, true);
}

TEST_F(ConnectivityNativeBinderTest, BlockPortTwice) {
    ndk::ScopedAStatus status = mService->blockPortForBind(5555);
    EXPECT_TRUE(status.isOk()) << status.getDescription ();
    status = mService->blockPortForBind(5555);
    EXPECT_TRUE(status.isOk()) << status.getDescription ();
    status = mService->unblockPortForBind(5555);
    EXPECT_TRUE(status.isOk()) << status.getDescription ();
}

TEST_F(ConnectivityNativeBinderTest, GetBlockedPorts) {
    ndk::ScopedAStatus status;
    std::vector<int> blockedPorts{1, 100, 1220, 1333, 2700, 5555, 5600, 65000};
    for (int i : blockedPorts) {
        status = mService->blockPortForBind(i);
        EXPECT_TRUE(status.isOk()) << status.getDescription ();
    }
    std::vector<int32_t> actualBlockedPorts;
    status = mService->getPortsBlockedForBind(&actualBlockedPorts);
    EXPECT_TRUE(status.isOk()) << status.getDescription ();
    EXPECT_FALSE(actualBlockedPorts.empty());
    EXPECT_EQ(blockedPorts, actualBlockedPorts);

    // Remove the ports we added.
    status = mService->unblockAllPortsForBind();
    EXPECT_TRUE(status.isOk()) << status.getDescription ();
    status = mService->getPortsBlockedForBind(&actualBlockedPorts);
    EXPECT_TRUE(status.isOk()) << status.getDescription ();
    EXPECT_TRUE(actualBlockedPorts.empty());
}

TEST_F(ConnectivityNativeBinderTest, UnblockAllPorts) {
    ndk::ScopedAStatus status;
    std::vector<int> blockedPorts{1, 100, 1220, 1333, 2700, 5555, 5600, 65000};

    if (mActualBlockedPorts.size() > 0) {
        status = mService->unblockAllPortsForBind();
    }

    for (int i : blockedPorts) {
        status = mService->blockPortForBind(i);
        EXPECT_TRUE(status.isOk()) << status.getDescription ();
    }

    std::vector<int32_t> actualBlockedPorts;
    status = mService->getPortsBlockedForBind(&actualBlockedPorts);
    EXPECT_TRUE(status.isOk()) << status.getDescription ();
    EXPECT_FALSE(actualBlockedPorts.empty());

    status = mService->unblockAllPortsForBind();
    EXPECT_TRUE(status.isOk()) << status.getDescription ();
    status = mService->getPortsBlockedForBind(&actualBlockedPorts);
    EXPECT_TRUE(status.isOk()) << status.getDescription ();
    EXPECT_TRUE(actualBlockedPorts.empty());
    // If mActualBlockedPorts is not empty, ports will be added back in teardown.
}

TEST_F(ConnectivityNativeBinderTest, BlockNegativePort) {
    int retry = 0;
    ndk::ScopedAStatus status;
    do {
        status = mService->blockPortForBind(-1);
        // TODO: find out why transaction failed is being thrown on the first attempt.
    } while (status.getExceptionCode() == EX_TRANSACTION_FAILED && retry++ < 5);
    EXPECT_EQ(EX_ILLEGAL_ARGUMENT, status.getExceptionCode());
}

TEST_F(ConnectivityNativeBinderTest, UnblockNegativePort) {
    int retry = 0;
    ndk::ScopedAStatus status;
    do {
        status = mService->unblockPortForBind(-1);
        // TODO: find out why transaction failed is being thrown on the first attempt.
    } while (status.getExceptionCode() == EX_TRANSACTION_FAILED && retry++ < 5);
    EXPECT_EQ(EX_ILLEGAL_ARGUMENT, status.getExceptionCode());
}

TEST_F(ConnectivityNativeBinderTest, BlockMaxPort) {
    int retry = 0;
    ndk::ScopedAStatus status;
    do {
        status = mService->blockPortForBind(65536);
        // TODO: find out why transaction failed is being thrown on the first attempt.
    } while (status.getExceptionCode() == EX_TRANSACTION_FAILED && retry++ < 5);
    EXPECT_EQ(EX_ILLEGAL_ARGUMENT, status.getExceptionCode());
}

TEST_F(ConnectivityNativeBinderTest, UnblockMaxPort) {
    int retry = 0;
    ndk::ScopedAStatus status;
    do {
        status = mService->unblockPortForBind(65536);
        // TODO: find out why transaction failed is being thrown on the first attempt.
    } while (status.getExceptionCode() == EX_TRANSACTION_FAILED && retry++ < 5);
    EXPECT_EQ(EX_ILLEGAL_ARGUMENT, status.getExceptionCode());
}

TEST_F(ConnectivityNativeBinderTest, CheckPermission) {
    int retry = 0;
    int curUid = getuid();
    EXPECT_EQ(0, seteuid(FIRST_APPLICATION_UID + 2000)) << "seteuid failed: " << strerror(errno);
    ndk::ScopedAStatus status;
    do {
        status = mService->blockPortForBind(5555);
        // TODO: find out why transaction failed is being thrown on the first attempt.
    } while (status.getExceptionCode() == EX_TRANSACTION_FAILED && retry++ < 5);
    EXPECT_EQ(EX_SECURITY, status.getExceptionCode());
    EXPECT_EQ(0, seteuid(curUid)) << "seteuid failed: " << strerror(errno);
}

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
#define LOG_TAG "jniClatCoordinator"

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <linux/if_tun.h>
#include <linux/ioctl.h>
#include <log/log.h>
#include <nativehelper/JNIHelp.h>
#include <net/if.h>
#include <spawn.h>
#include <sys/wait.h>
#include <string>

#include <bpf/BpfMap.h>
#include <bpf/BpfUtils.h>
#include <bpf_shared.h>
#include <netjniutils/netjniutils.h>
#include <private/android_filesystem_config.h>

#include "libclat/clatutils.h"
#include "nativehelper/scoped_utf_chars.h"

// Sync from system/netd/include/netid_client.h
#define MARK_UNSET 0u

// Sync from system/netd/server/NetdConstants.h
#define __INT_STRLEN(i) sizeof(#i)
#define _INT_STRLEN(i) __INT_STRLEN(i)
#define INT32_STRLEN _INT_STRLEN(INT32_MIN)

#define DEVICEPREFIX "v4-"

namespace android {
static const char* kClatdPath = "/apex/com.android.tethering/bin/for-system/clatd";

static void throwIOException(JNIEnv* env, const char* msg, int error) {
    jniThrowExceptionFmt(env, "java/io/IOException", "%s: %s", msg, strerror(error));
}

jstring com_android_server_connectivity_ClatCoordinator_selectIpv4Address(JNIEnv* env,
                                                                          jobject clazz,
                                                                          jstring v4addr,
                                                                          jint prefixlen) {
    ScopedUtfChars address(env, v4addr);
    in_addr ip;
    if (inet_pton(AF_INET, address.c_str(), &ip) != 1) {
        throwIOException(env, "invalid address", EINVAL);
        return nullptr;
    }

    // Pick an IPv4 address.
    // TODO: this picks the address based on other addresses that are assigned to interfaces, but
    // the address is only actually assigned to an interface once clatd starts up. So we could end
    // up with two clatd instances with the same IPv4 address.
    // Stop doing this and instead pick a free one from the kV4Addr pool.
    in_addr v4 = {net::clat::selectIpv4Address(ip, prefixlen)};
    if (v4.s_addr == INADDR_NONE) {
        jniThrowExceptionFmt(env, "java/io/IOException", "No free IPv4 address in %s/%d",
                             address.c_str(), prefixlen);
        return nullptr;
    }

    char addrstr[INET_ADDRSTRLEN];
    if (!inet_ntop(AF_INET, (void*)&v4, addrstr, sizeof(addrstr))) {
        throwIOException(env, "invalid address", EADDRNOTAVAIL);
        return nullptr;
    }
    return env->NewStringUTF(addrstr);
}

// Picks a random interface ID that is checksum neutral with the IPv4 address and the NAT64 prefix.
jstring com_android_server_connectivity_ClatCoordinator_generateIpv6Address(
        JNIEnv* env, jobject clazz, jstring ifaceStr, jstring v4Str, jstring prefix64Str,
        jint mark) {
    ScopedUtfChars iface(env, ifaceStr);
    ScopedUtfChars addr4(env, v4Str);
    ScopedUtfChars prefix64(env, prefix64Str);

    if (iface.c_str() == nullptr) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid null interface name");
        return nullptr;
    }

    in_addr v4;
    if (inet_pton(AF_INET, addr4.c_str(), &v4) != 1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid clat v4 address %s",
                             addr4.c_str());
        return nullptr;
    }

    in6_addr nat64Prefix;
    if (inet_pton(AF_INET6, prefix64.c_str(), &nat64Prefix) != 1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid prefix %s", prefix64.c_str());
        return nullptr;
    }

    in6_addr v6;
    if (net::clat::generateIpv6Address(iface.c_str(), v4, nat64Prefix, &v6, mark)) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                             "Unable to find global source address on %s for %s", iface.c_str(),
                             prefix64.c_str());
        return nullptr;
    }

    char addrstr[INET6_ADDRSTRLEN];
    if (!inet_ntop(AF_INET6, (void*)&v6, addrstr, sizeof(addrstr))) {
        throwIOException(env, "invalid address", EADDRNOTAVAIL);
        return nullptr;
    }
    return env->NewStringUTF(addrstr);
}

static jint com_android_server_connectivity_ClatCoordinator_createTunInterface(JNIEnv* env,
                                                                               jobject clazz,
                                                                               jstring tuniface) {
    ScopedUtfChars v4interface(env, tuniface);

    // open the tun device in non blocking mode as required by clatd
    jint fd = open("/dev/net/tun", O_RDWR | O_NONBLOCK | O_CLOEXEC);
    if (fd == -1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "open tun device failed (%s)",
                             strerror(errno));
        return -1;
    }

    struct ifreq ifr = {
            .ifr_flags = IFF_TUN,
    };
    strlcpy(ifr.ifr_name, v4interface.c_str(), sizeof(ifr.ifr_name));

    if (ioctl(fd, TUNSETIFF, &ifr, sizeof(ifr))) {
        close(fd);
        jniThrowExceptionFmt(env, "java/io/IOException", "ioctl(TUNSETIFF) failed (%s)",
                             strerror(errno));
        return -1;
    }

    return fd;
}

static jint com_android_server_connectivity_ClatCoordinator_detectMtu(JNIEnv* env, jobject clazz,
                                                                      jstring platSubnet,
                                                                      jint plat_suffix, jint mark) {
    ScopedUtfChars platSubnetStr(env, platSubnet);

    in6_addr plat_subnet;
    if (inet_pton(AF_INET6, platSubnetStr.c_str(), &plat_subnet) != 1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid plat prefix address %s",
                             platSubnetStr.c_str());
        return -1;
    }

    int ret = net::clat::detect_mtu(&plat_subnet, plat_suffix, mark);
    if (ret < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "detect mtu failed: %s", strerror(-ret));
        return -1;
    }

    return ret;
}

static jint com_android_server_connectivity_ClatCoordinator_openPacketSocket(JNIEnv* env,
                                                                              jobject clazz) {
    // Will eventually be bound to htons(ETH_P_IPV6) protocol,
    // but only after appropriate bpf filter is attached.
    int sock = socket(AF_PACKET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (sock < 0) {
        throwIOException(env, "packet socket failed", errno);
        return -1;
    }
    return sock;
}

static jint com_android_server_connectivity_ClatCoordinator_openRawSocket6(JNIEnv* env,
                                                                           jobject clazz,
                                                                           jint mark) {
    int sock = socket(AF_INET6, SOCK_RAW | SOCK_NONBLOCK | SOCK_CLOEXEC, IPPROTO_RAW);
    if (sock < 0) {
        throwIOException(env, "raw socket failed", errno);
        return -1;
    }

    // TODO: check the mark validation
    if (mark != MARK_UNSET && setsockopt(sock, SOL_SOCKET, SO_MARK, &mark, sizeof(mark)) < 0) {
        throwIOException(env, "could not set mark on raw socket", errno);
        close(sock);
        return -1;
    }

    return sock;
}

static void com_android_server_connectivity_ClatCoordinator_addAnycastSetsockopt(
        JNIEnv* env, jobject clazz, jobject javaFd, jstring addr6, jint ifindex) {
    int sock = netjniutils::GetNativeFileDescriptor(env, javaFd);
    if (sock < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid file descriptor");
        return;
    }

    ScopedUtfChars addrStr(env, addr6);

    in6_addr addr;
    if (inet_pton(AF_INET6, addrStr.c_str(), &addr) != 1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid IPv6 address %s",
                             addrStr.c_str());
        return;
    }

    struct ipv6_mreq mreq = {addr, ifindex};
    int ret = setsockopt(sock, SOL_IPV6, IPV6_JOIN_ANYCAST, &mreq, sizeof(mreq));
    if (ret) {
        jniThrowExceptionFmt(env, "java/io/IOException", "setsockopt IPV6_JOIN_ANYCAST failed: %s",
                             strerror(errno));
        return;
    }
}

static void com_android_server_connectivity_ClatCoordinator_configurePacketSocket(
        JNIEnv* env, jobject clazz, jobject javaFd, jstring addr6, jint ifindex) {
    ScopedUtfChars addrStr(env, addr6);

    int sock = netjniutils::GetNativeFileDescriptor(env, javaFd);
    if (sock < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid file descriptor");
        return;
    }

    in6_addr addr;
    if (inet_pton(AF_INET6, addrStr.c_str(), &addr) != 1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid IPv6 address %s",
                             addrStr.c_str());
        return;
    }

    int ret = net::clat::configure_packet_socket(sock, &addr, ifindex);
    if (ret < 0) {
        throwIOException(env, "configure packet socket failed", -ret);
        return;
    }
}

static jint com_android_server_connectivity_ClatCoordinator_startClatd(
        JNIEnv* env, jobject clazz, jobject tunJavaFd, jobject readSockJavaFd,
        jobject writeSockJavaFd, jstring iface, jstring pfx96, jstring v4, jstring v6) {
    ScopedUtfChars ifaceStr(env, iface);
    ScopedUtfChars pfx96Str(env, pfx96);
    ScopedUtfChars v4Str(env, v4);
    ScopedUtfChars v6Str(env, v6);

    int tunFd = netjniutils::GetNativeFileDescriptor(env, tunJavaFd);
    if (tunFd < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid tun file descriptor");
        return -1;
    }

    int readSock = netjniutils::GetNativeFileDescriptor(env, readSockJavaFd);
    if (readSock < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid read socket");
        return -1;
    }

    int writeSock = netjniutils::GetNativeFileDescriptor(env, writeSockJavaFd);
    if (writeSock < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid write socket");
        return -1;
    }

    // 1. these are the FD we'll pass to clatd on the cli, so need it as a string
    char tunFdStr[INT32_STRLEN];
    char sockReadStr[INT32_STRLEN];
    char sockWriteStr[INT32_STRLEN];
    snprintf(tunFdStr, sizeof(tunFdStr), "%d", tunFd);
    snprintf(sockReadStr, sizeof(sockReadStr), "%d", readSock);
    snprintf(sockWriteStr, sizeof(sockWriteStr), "%d", writeSock);

    // 2. we're going to use this as argv[0] to clatd to make ps output more useful
    std::string progname("clatd-");
    progname += ifaceStr.c_str();

    // clang-format off
    const char* args[] = {progname.c_str(),
                          "-i", ifaceStr.c_str(),
                          "-p", pfx96Str.c_str(),
                          "-4", v4Str.c_str(),
                          "-6", v6Str.c_str(),
                          "-t", tunFdStr,
                          "-r", sockReadStr,
                          "-w", sockWriteStr,
                          nullptr};
    // clang-format on

    // 3. register vfork requirement
    posix_spawnattr_t attr;
    if (int ret = posix_spawnattr_init(&attr)) {
        throwIOException(env, "posix_spawnattr_init failed", ret);
        return -1;
    }

    // TODO: use android::base::ScopeGuard.
    if (int ret = posix_spawnattr_setflags(&attr, POSIX_SPAWN_USEVFORK
#ifdef POSIX_SPAWN_CLOEXEC_DEFAULT
                                           | POSIX_SPAWN_CLOEXEC_DEFAULT
#endif
                                           )) {
        posix_spawnattr_destroy(&attr);
        throwIOException(env, "posix_spawnattr_setflags failed", ret);
        return -1;
    }

    // 4. register dup2() action: this is what 'clears' the CLOEXEC flag
    // on the tun fd that we want the child clatd process to inherit
    // (this will happen after the vfork, and before the execve).
    // Note that even though dup2(2) is a no-op if fd == new_fd but O_CLOEXEC flag will be removed.
    // See implementation of bionic's posix_spawn_file_actions_adddup2().
    posix_spawn_file_actions_t fa;
    if (int ret = posix_spawn_file_actions_init(&fa)) {
        posix_spawnattr_destroy(&attr);
        throwIOException(env, "posix_spawn_file_actions_init failed", ret);
        return -1;
    }

    if (int ret = posix_spawn_file_actions_adddup2(&fa, tunFd, tunFd)) {
        posix_spawnattr_destroy(&attr);
        posix_spawn_file_actions_destroy(&fa);
        throwIOException(env, "posix_spawn_file_actions_adddup2 for tun fd failed", ret);
        return -1;
    }
    if (int ret = posix_spawn_file_actions_adddup2(&fa, readSock, readSock)) {
        posix_spawnattr_destroy(&attr);
        posix_spawn_file_actions_destroy(&fa);
        throwIOException(env, "posix_spawn_file_actions_adddup2 for read socket failed", ret);
        return -1;
    }
    if (int ret = posix_spawn_file_actions_adddup2(&fa, writeSock, writeSock)) {
        posix_spawnattr_destroy(&attr);
        posix_spawn_file_actions_destroy(&fa);
        throwIOException(env, "posix_spawn_file_actions_adddup2 for write socket failed", ret);
        return -1;
    }

    // 5. actually perform vfork/dup2/execve
    pid_t pid;
    if (int ret = posix_spawn(&pid, kClatdPath, &fa, &attr, (char* const*)args, nullptr)) {
        posix_spawnattr_destroy(&attr);
        posix_spawn_file_actions_destroy(&fa);
        throwIOException(env, "posix_spawn failed", ret);
        return -1;
    }

    posix_spawnattr_destroy(&attr);
    posix_spawn_file_actions_destroy(&fa);

    return pid;
}

// Stop clatd process. SIGTERM with timeout first, if fail, SIGKILL.
// See stopProcess() in system/netd/server/NetdConstants.cpp.
// TODO: have a function stopProcess(int pid, const char *name) in common location and call it.
static constexpr int WAITPID_ATTEMPTS = 50;
static constexpr int WAITPID_RETRY_INTERVAL_US = 100000;

static void stopClatdProcess(int pid) {
    int err = kill(pid, SIGTERM);
    if (err) {
        err = errno;
    }
    if (err == ESRCH) {
        ALOGE("clatd child process %d unexpectedly disappeared", pid);
        return;
    }
    if (err) {
        ALOGE("Error killing clatd child process %d: %s", pid, strerror(err));
    }
    int status = 0;
    int ret = 0;
    for (int count = 0; ret == 0 && count < WAITPID_ATTEMPTS; count++) {
        usleep(WAITPID_RETRY_INTERVAL_US);
        ret = waitpid(pid, &status, WNOHANG);
    }
    if (ret == 0) {
        ALOGE("Failed to SIGTERM clatd pid=%d, try SIGKILL", pid);
        // TODO: fix that kill failed or waitpid doesn't return.
        kill(pid, SIGKILL);
        ret = waitpid(pid, &status, 0);
    }
    if (ret == -1) {
        ALOGE("Error waiting for clatd child process %d: %s", pid, strerror(errno));
    } else {
        ALOGD("clatd process %d terminated status=%d", pid, status);
    }
}

static void com_android_server_connectivity_ClatCoordinator_stopClatd(JNIEnv* env, jobject clazz,
                                                                      jstring iface, jstring pfx96,
                                                                      jstring v4, jstring v6,
                                                                      jint pid) {
    ScopedUtfChars ifaceStr(env, iface);
    ScopedUtfChars pfx96Str(env, pfx96);
    ScopedUtfChars v4Str(env, v4);
    ScopedUtfChars v6Str(env, v6);

    if (pid <= 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid pid");
        return;
    }

    stopClatdProcess(pid);
}

static jlong com_android_server_connectivity_ClatCoordinator_tagSocketAsClat(
        JNIEnv* env, jobject clazz, jobject sockJavaFd) {
    int sockFd = netjniutils::GetNativeFileDescriptor(env, sockJavaFd);
    if (sockFd < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid socket file descriptor");
        return -1;
    }

    uint64_t sock_cookie = bpf::getSocketCookie(sockFd);
    if (sock_cookie == bpf::NONEXISTENT_COOKIE) {
        throwIOException(env, "get socket cookie failed", errno);
        return -1;
    }

    bpf::BpfMap<uint64_t, UidTagValue> cookieTagMap;
    auto res = cookieTagMap.init(COOKIE_TAG_MAP_PATH);
    if (!res.ok()) {
        throwIOException(env, "failed to init the cookieTagMap", res.error().code());
        return -1;
    }

    // Tag raw socket with uid AID_CLAT and set tag as zero because tag is unused in bpf
    // program for counting data usage in netd.c. Tagging socket is used to avoid counting
    // duplicated clat traffic in bpf stat.
    UidTagValue newKey = {.uid = (uint32_t)AID_CLAT, .tag = 0 /* unused */};
    res = cookieTagMap.writeValue(sock_cookie, newKey, BPF_ANY);
    if (!res.ok()) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Failed to tag the socket: %s, fd: %d",
                             strerror(res.error().code()), cookieTagMap.getMap().get());
        return -1;
    }

    ALOGI("tag uid AID_CLAT to socket fd %d, cookie %" PRIu64 "", sockFd, sock_cookie);
    return static_cast<jlong>(sock_cookie);
}

static void com_android_server_connectivity_ClatCoordinator_untagSocket(JNIEnv* env, jobject clazz,
                                                                        jlong cookie) {
    uint64_t sock_cookie = static_cast<uint64_t>(cookie);
    if (sock_cookie == bpf::NONEXISTENT_COOKIE) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid socket cookie");
        return;
    }

    // The reason that deleting entry from cookie tag map directly is that the tag socket destroy
    // listener only monitors on group INET_TCP, INET_UDP, INET6_TCP, INET6_UDP. The other socket
    // types, ex: raw, are not able to be removed automatically by the listener.
    // See TrafficController::makeSkDestroyListener.
    bpf::BpfMap<uint64_t, UidTagValue> cookieTagMap;
    auto res = cookieTagMap.init(COOKIE_TAG_MAP_PATH);
    if (!res.ok()) {
        throwIOException(env, "failed to init the cookieTagMap", res.error().code());
        return;
    }

    res = cookieTagMap.deleteValue(sock_cookie);
    if (!res.ok()) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Failed to untag the socket: %s",
                             strerror(res.error().code()));
        return;
    }

    ALOGI("untag socket cookie %" PRIu64 "", sock_cookie);
    return;
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
        /* name, signature, funcPtr */
        {"native_selectIpv4Address", "(Ljava/lang/String;I)Ljava/lang/String;",
         (void*)com_android_server_connectivity_ClatCoordinator_selectIpv4Address},
        {"native_generateIpv6Address",
         "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;",
         (void*)com_android_server_connectivity_ClatCoordinator_generateIpv6Address},
        {"native_createTunInterface", "(Ljava/lang/String;)I",
         (void*)com_android_server_connectivity_ClatCoordinator_createTunInterface},
        {"native_detectMtu", "(Ljava/lang/String;II)I",
         (void*)com_android_server_connectivity_ClatCoordinator_detectMtu},
        {"native_openPacketSocket", "()I",
         (void*)com_android_server_connectivity_ClatCoordinator_openPacketSocket},
        {"native_openRawSocket6", "(I)I",
         (void*)com_android_server_connectivity_ClatCoordinator_openRawSocket6},
        {"native_addAnycastSetsockopt", "(Ljava/io/FileDescriptor;Ljava/lang/String;I)V",
         (void*)com_android_server_connectivity_ClatCoordinator_addAnycastSetsockopt},
        {"native_configurePacketSocket", "(Ljava/io/FileDescriptor;Ljava/lang/String;I)V",
         (void*)com_android_server_connectivity_ClatCoordinator_configurePacketSocket},
        {"native_startClatd",
         "(Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;Ljava/lang/"
         "String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I",
         (void*)com_android_server_connectivity_ClatCoordinator_startClatd},
        {"native_stopClatd",
         "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V",
         (void*)com_android_server_connectivity_ClatCoordinator_stopClatd},
        {"native_tagSocketAsClat", "(Ljava/io/FileDescriptor;)J",
         (void*)com_android_server_connectivity_ClatCoordinator_tagSocketAsClat},
        {"native_untagSocket", "(J)V",
         (void*)com_android_server_connectivity_ClatCoordinator_untagSocket},
};

int register_com_android_server_connectivity_ClatCoordinator(JNIEnv* env) {
    return jniRegisterNativeMethods(env, "com/android/server/connectivity/ClatCoordinator",
                                    gMethods, NELEM(gMethods));
}

};  // namespace android

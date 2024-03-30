/*
 * Copyright (C) 2010 The Android Open Source Project
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

#include "MDnsSdListener.h"

#include <arpa/inet.h>
#include <dirent.h>
#include <errno.h>
#include <inttypes.h>
#include <linux/if.h>
#include <netdb.h>
#include <netinet/in.h>
#include <pthread.h>
#include <resolv.h>
#include <stdlib.h>
#include <string.h>
#include <sys/poll.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <thread>

#define LOG_TAG "MDnsDS"
#define DBG 1
#define VDBG 1

#include <cutils/properties.h>
#include <log/log.h>
#include <netdutils/ThreadUtil.h>
#include <sysutils/SocketClient.h>

#include "Controllers.h"
#include "MDnsEventReporter.h"
#include "netid_client.h"

using android::net::gCtls;

#define MDNS_SERVICE_NAME "mdnsd"
#define MDNS_SERVICE_STATUS "init.svc.mdnsd"

#define CEIL(x, y) (((x) + (y) - 1) / (y))

constexpr char RESCAN[] = "1";

using android::net::mdns::aidl::DiscoveryInfo;
using android::net::mdns::aidl::GetAddressInfo;
using android::net::mdns::aidl::IMDnsEventListener;
using android::net::mdns::aidl::RegistrationInfo;
using android::net::mdns::aidl::ResolutionInfo;

static unsigned ifaceIndexToNetId(uint32_t interfaceIndex) {
    char interfaceName[IFNAMSIZ] = {};
    unsigned netId;
    if (if_indextoname(interfaceIndex, interfaceName) == nullptr) {
        ALOGE("Interface %d was not found", interfaceIndex);
        return NETID_UNSET;
    } else if ((netId = gCtls->netCtrl.getNetworkForInterface(interfaceName)) == NETID_UNSET) {
        ALOGE("Network was not found for interface %s", interfaceName);
        return NETID_UNSET;
    }
    return netId;
}

int MDnsSdListener::discover(uint32_t ifIndex, const char* regType, const char* domain,
                             const int requestId, const int requestFlags) {
    if (VDBG) {
        ALOGD("discover(%d, %s, %s, %d, %d)", ifIndex, regType, domain ? domain : "null", requestId,
              requestFlags);
    }
    Context* context = new Context(requestId);
    DNSServiceRef* ref = mMonitor.allocateServiceRef(requestId, context);
    if (ref == nullptr) {
        ALOGE("requestId %d already in use during discover call", requestId);
        return -EBUSY;
    }
    if (VDBG) ALOGD("using ref %p", ref);

    DNSServiceErrorType result = DNSServiceBrowse(ref, requestFlags, ifIndex, regType, domain,
                                                  &MDnsSdListenerDiscoverCallback, context);
    if (result != kDNSServiceErr_NoError) {
        ALOGE("Discover request %d got an error from DNSServiceBrowse %d", requestId, result);
        mMonitor.freeServiceRef(requestId);
        // Return kDNSServiceErr_* directly instead of transferring to an UNIX error.
        // This can help caller to know what going wrong from mdnsresponder side.
        return -result;
    }
    mMonitor.startMonitoring(requestId);
    if (VDBG) ALOGD("discover successful");
    return 0;
}

void MDnsSdListenerDiscoverCallback(DNSServiceRef /* sdRef */, DNSServiceFlags flags,
                                    uint32_t ifIndex, DNSServiceErrorType errorCode,
                                    const char* serviceName, const char* regType,
                                    const char* replyDomain, void* inContext) {
    MDnsSdListener::Context *context = reinterpret_cast<MDnsSdListener::Context *>(inContext);
    int refNumber = context->mRefNumber;
    const auto& listeners = MDnsEventReporter::getInstance().getEventListeners();
    if (listeners.empty()) {
        ALOGI("Discover callback not sent since no IMDnsEventListener receiver is available.");
        return;
    }

    DiscoveryInfo info;
    info.id = refNumber;
    info.serviceName = serviceName;
    info.registrationType = regType;
    info.interfaceIdx = ifIndex;
    // If the network is not found, still send the event and let
    // the service decide what to do with a callback with an empty network
    info.netId = ifaceIndexToNetId(ifIndex);
    if (errorCode == kDNSServiceErr_NoError) {
        if (flags & kDNSServiceFlagsAdd) {
            if (VDBG) {
                ALOGD("Discover found new serviceName %s, regType %s and domain %s for %d",
                      serviceName, regType, replyDomain, refNumber);
            }
            info.result = IMDnsEventListener::SERVICE_FOUND;
        } else {
            if (VDBG) {
                ALOGD("Discover lost serviceName %s, regType %s and domain %s for %d", serviceName,
                      regType, replyDomain, refNumber);
            }
            info.result = IMDnsEventListener::SERVICE_LOST;
        }
    } else {
        if (DBG) ALOGE("discover failure for %d, error= %d", refNumber, errorCode);
        info.result = IMDnsEventListener::SERVICE_DISCOVERY_FAILED;
    }

    for (const auto& it : listeners) {
        it->getListener()->onServiceDiscoveryStatus(info);
    }
}

int MDnsSdListener::stop(int requestId) {
    DNSServiceRef* ref = mMonitor.lookupServiceRef(requestId);
    if (ref == nullptr) {
        if (DBG) ALOGE("Stop used unknown requestId %d", requestId);
        return -ESRCH;
    }
    if (VDBG) ALOGD("Stopping operation with ref %p", ref);
    mMonitor.deallocateServiceRef(ref);
    mMonitor.freeServiceRef(requestId);
    return 0;
}

int MDnsSdListener::serviceRegister(int requestId, const char* serviceName, const char* serviceType,
                                    const char* domain, const char* host, int port,
                                    const std::vector<unsigned char>& txtRecord, uint32_t ifIndex) {
    if (VDBG) {
        ALOGD("serviceRegister(%d, %d, %s, %s, %s, %s, %d, <binary>)", requestId, ifIndex,
              serviceName, serviceType, domain ? domain : "null", host ? host : "null", port);
    }
    Context* context = new Context(requestId);
    DNSServiceRef* ref = mMonitor.allocateServiceRef(requestId, context);
    if (ref == nullptr) {
        ALOGE("requestId %d already in use during register call", requestId);
        return -EBUSY;
    }
    port = htons(port);
    DNSServiceFlags nativeFlags = 0;
    DNSServiceErrorType result = DNSServiceRegister(
            ref, nativeFlags, ifIndex, serviceName, serviceType, domain, host, port,
            txtRecord.size(), &txtRecord.front(), &MDnsSdListenerRegisterCallback, context);
    if (result != kDNSServiceErr_NoError) {
        ALOGE("service register request %d got an error from DNSServiceRegister %d", requestId,
                result);
        mMonitor.freeServiceRef(requestId);
        // Return kDNSServiceErr_* directly instead of transferring to an UNIX error.
        // This can help caller to know what going wrong from mdnsresponder side.
        return -result;
    }
    mMonitor.startMonitoring(requestId);
    if (VDBG) ALOGD("serviceRegister successful");
    return 0;
}

void MDnsSdListenerRegisterCallback(DNSServiceRef /* sdRef */, DNSServiceFlags /* flags */,
                                    DNSServiceErrorType errorCode, const char* serviceName,
                                    const char* regType, const char* /* domain */,
                                    void* inContext) {
    MDnsSdListener::Context* context = reinterpret_cast<MDnsSdListener::Context*>(inContext);
    int refNumber = context->mRefNumber;
    const auto& listeners = MDnsEventReporter::getInstance().getEventListeners();
    if (listeners.empty()) {
        ALOGI("Register callback not sent since no IMDnsEventListener receiver is available.");
        return;
    }

    RegistrationInfo info;
    info.id = refNumber;
    info.serviceName = serviceName;
    info.registrationType = regType;
    if (errorCode == kDNSServiceErr_NoError) {
        if (VDBG) ALOGD("register succeeded for %d as %s", refNumber, serviceName);
        info.result = IMDnsEventListener::SERVICE_REGISTERED;
    } else {
        if (DBG) ALOGE("register failure for %d, error= %d", refNumber, errorCode);
        info.result = IMDnsEventListener::SERVICE_REGISTRATION_FAILED;
    }

    for (const auto& it : listeners) {
        it->getListener()->onServiceRegistrationStatus(info);
    }
}

int MDnsSdListener::resolveService(int requestId, uint32_t ifIndex, const char* serviceName,
                                   const char* regType, const char* domain) {
    if (VDBG) {
        ALOGD("resolveService(%d, %d, %s, %s, %s)", requestId, ifIndex, serviceName, regType,
              domain);
    }
    Context* context = new Context(requestId);
    DNSServiceRef* ref = mMonitor.allocateServiceRef(requestId, context);
    if (ref == nullptr) {
        ALOGE("request Id %d already in use during resolve call", requestId);
        return -EBUSY;
    }
    DNSServiceFlags nativeFlags = 0;
    DNSServiceErrorType result = DNSServiceResolve(ref, nativeFlags, ifIndex, serviceName, regType,
                                                   domain, &MDnsSdListenerResolveCallback, context);
    if (result != kDNSServiceErr_NoError) {
        ALOGE("service resolve request %d on iface %d: got an error from DNSServiceResolve %d",
              requestId, ifIndex, result);
        mMonitor.freeServiceRef(requestId);
        // Return kDNSServiceErr_* directly instead of transferring to an UNIX error.
        // This can help caller to know what going wrong from mdnsresponder side.
        return -result;
    }
    mMonitor.startMonitoring(requestId);
    if (VDBG) ALOGD("resolveService successful");
    return 0;
}

void MDnsSdListenerResolveCallback(DNSServiceRef /* sdRef */, DNSServiceFlags /* flags */,
                                   uint32_t ifIndex, DNSServiceErrorType errorCode,
                                   const char* fullname, const char* hosttarget, uint16_t port,
                                   uint16_t txtLen, const unsigned char* txtRecord,
                                   void* inContext) {
    MDnsSdListener::Context* context = reinterpret_cast<MDnsSdListener::Context*>(inContext);
    int refNumber = context->mRefNumber;
    const auto& listeners = MDnsEventReporter::getInstance().getEventListeners();
    if (listeners.empty()) {
        ALOGI("Resolve callback not sent since no IMDnsEventListener receiver is available.");
        return;
    }
    port = ntohs(port);

    ResolutionInfo info;
    info.id = refNumber;
    info.port = port;
    info.serviceFullName = fullname;
    info.hostname = hosttarget;
    info.txtRecord = std::vector<unsigned char>(txtRecord, txtRecord + txtLen);
    info.interfaceIdx = ifIndex;
    if (errorCode == kDNSServiceErr_NoError) {
        if (VDBG) {
            ALOGD("resolve succeeded for %d finding %s at %s:%d with txtLen %d", refNumber,
                  fullname, hosttarget, port, txtLen);
        }
        info.result = IMDnsEventListener::SERVICE_RESOLVED;
    } else {
        if (DBG) ALOGE("resolve failure for %d, error= %d", refNumber, errorCode);
        info.result = IMDnsEventListener::SERVICE_RESOLUTION_FAILED;
    }

    for (const auto& it : listeners) {
        it->getListener()->onServiceResolutionStatus(info);
    }
}

int MDnsSdListener::getAddrInfo(int requestId, uint32_t ifIndex, uint32_t protocol,
                                const char* hostname) {
    if (VDBG) ALOGD("getAddrInfo(%d, %u %d, %s)", requestId, ifIndex, protocol, hostname);
    Context* context = new Context(requestId);
    DNSServiceRef* ref = mMonitor.allocateServiceRef(requestId, context);
    if (ref == nullptr) {
        ALOGE("request ID %d already in use during getAddrInfo call", requestId);
        return -EBUSY;
    }
    DNSServiceFlags nativeFlags = 0;
    DNSServiceErrorType result =
            DNSServiceGetAddrInfo(ref, nativeFlags, ifIndex, protocol, hostname,
                                  &MDnsSdListenerGetAddrInfoCallback, context);
    if (result != kDNSServiceErr_NoError) {
        ALOGE("getAddrInfo request %d got an error from DNSServiceGetAddrInfo %d", requestId,
                result);
        mMonitor.freeServiceRef(requestId);
        // Return kDNSServiceErr_* directly instead of transferring to an UNIX error.
        // This can help caller to know what going wrong from mdnsresponder side.
        return -result;
    }
    mMonitor.startMonitoring(requestId);
    if (VDBG) ALOGD("getAddrInfo successful");
    return 0;
}

void MDnsSdListenerGetAddrInfoCallback(DNSServiceRef /* sdRef */, DNSServiceFlags /* flags */,
                                       uint32_t ifIndex, DNSServiceErrorType errorCode,
                                       const char* hostname, const struct sockaddr* const sa,
                                       uint32_t /* ttl */, void* inContext) {
    MDnsSdListener::Context *context = reinterpret_cast<MDnsSdListener::Context *>(inContext);
    int refNumber = context->mRefNumber;
    const auto& listeners = MDnsEventReporter::getInstance().getEventListeners();
    if (listeners.empty()) {
        ALOGI("Get address callback not sent since no IMDnsEventListener receiver is available.");
        return;
    }

    GetAddressInfo info;
    info.id = refNumber;
    info.hostname = hostname;
    info.interfaceIdx = ifIndex;
    // If the network is not found, still send the event with an empty network
    // and let the service decide what to do with it
    info.netId = ifaceIndexToNetId(ifIndex);
    if (errorCode == kDNSServiceErr_NoError) {
        char addr[INET6_ADDRSTRLEN];
        if (sa->sa_family == AF_INET) {
            inet_ntop(sa->sa_family, &(((struct sockaddr_in *)sa)->sin_addr), addr, sizeof(addr));
        } else {
            inet_ntop(sa->sa_family, &(((struct sockaddr_in6 *)sa)->sin6_addr), addr, sizeof(addr));
        }
        info.address = addr;
        if (VDBG) {
            ALOGD("getAddrInfo succeeded for %d:", refNumber);
        }
        info.result = IMDnsEventListener::SERVICE_GET_ADDR_SUCCESS;
    } else {
        if (DBG) ALOGE("getAddrInfo failure for %d, error= %d", refNumber, errorCode);
        info.result = IMDnsEventListener::SERVICE_GET_ADDR_FAILED;
    }
    for (const auto& it : listeners) {
        it->getListener()->onGettingServiceAddressStatus(info);
    }
}

int MDnsSdListener::startDaemon() {
    if (!mMonitor.startService()) {
        ALOGE("Failed to start: Service already running");
        return -EBUSY;
    }
    return 0;
}

int MDnsSdListener::stopDaemon() {
    if (!mMonitor.stopService()) {
        ALOGE("Failed to stop: Service still in use");
        return -EBUSY;
    }
    return 0;
}

MDnsSdListener::Monitor::Monitor() {
    mHead = nullptr;
    mLiveCount = 0;
    mPollFds = nullptr;
    mPollRefs = nullptr;
    mPollSize = 10;
    socketpair(AF_LOCAL, SOCK_STREAM | SOCK_CLOEXEC, 0, mCtrlSocketPair);

    mRescanThread = new std::thread(&Monitor::run, this);
    if (!mRescanThread->joinable()) ALOGE("Unable to launch thread.");
}

MDnsSdListener::Monitor::~Monitor() {
    if (VDBG) ALOGD("Monitor recycling");
    close(mCtrlSocketPair[1]);  // interrupt poll in MDnsSdListener::Monitor::run() and revent will
                                // be 17 = POLLIN | POLLHUP
    mRescanThread->join();
    delete mRescanThread;
    if (VDBG) ALOGD("Monitor recycled");
}
#define NAP_TIME 200  // 200 ms between polls
static int wait_for_property(const char *name, const char *desired_value, int maxwait)
{
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    int maxnaps = (maxwait * 1000) / NAP_TIME;

    if (maxnaps < 1) {
        maxnaps = 1;
    }

    while (maxnaps-- > 0) {
        usleep(NAP_TIME * 1000);
        if (property_get(name, value, nullptr)) {
            if (desired_value == nullptr || strcmp(value, desired_value) == 0) {
                return 0;
            }
        }
    }
    return -1; /* failure */
}

int MDnsSdListener::Monitor::startService() {
    char property_value[PROPERTY_VALUE_MAX];
    std::lock_guard guard(mMutex);
    property_get(MDNS_SERVICE_STATUS, property_value, "");
    if (strcmp("running", property_value) != 0) {
        ALOGD("Starting MDNSD");
        property_set("ctl.start", MDNS_SERVICE_NAME);
        wait_for_property(MDNS_SERVICE_STATUS, "running", 5);
        return -1;
    }
    return 0;
}

int MDnsSdListener::Monitor::stopService() {
    std::lock_guard guard(mMutex);
    if (mHead == nullptr) {
        ALOGD("Stopping MDNSD");
        property_set("ctl.stop", MDNS_SERVICE_NAME);
        wait_for_property(MDNS_SERVICE_STATUS, "stopped", 5);
        return -1;
    }
    return 0;
}

void MDnsSdListener::Monitor::run() {
    int pollCount = 1;

    mPollFds = (struct pollfd *)calloc(sizeof(struct pollfd), mPollSize);
    mPollRefs = (DNSServiceRef **)calloc(sizeof(DNSServiceRef *), mPollSize);
    LOG_ALWAYS_FATAL_IF((mPollFds == nullptr), "initial calloc failed on mPollFds with a size of %d",
            ((int)sizeof(struct pollfd)) * mPollSize);
    LOG_ALWAYS_FATAL_IF((mPollRefs == nullptr), "initial calloc failed on mPollRefs with a size of %d",
            ((int)sizeof(DNSServiceRef *)) * mPollSize);

    mPollFds[0].fd = mCtrlSocketPair[0];
    mPollFds[0].events = POLLIN;

    if (VDBG) ALOGD("MDnsSdListener starting to monitor");
    while (1) {
        if (VDBG) ALOGD("Going to poll with pollCount %d", pollCount);
        int pollResults = poll(mPollFds, pollCount, 10000000);
        if (VDBG) ALOGD("pollResults=%d", pollResults);
        if (pollResults < 0) {
            ALOGE("Error in poll - got %d", errno);
        } else if (pollResults > 0) {
            if (VDBG) ALOGD("Monitor poll got data pollCount = %d, %d", pollCount, pollResults);
            for(int i = 1; i < pollCount; i++) {
                if (mPollFds[i].revents != 0) {
                    if (VDBG) {
                        ALOGD("Monitor found [%d].revents = %d - calling ProcessResults",
                                i, mPollFds[i].revents);
                    }
                    std::lock_guard guard(mMutex);
                    DNSServiceProcessResult(*(mPollRefs[i]));
                    mPollFds[i].revents = 0;
                }
            }
            if (VDBG) ALOGD("controlSocket shows revent= %d", mPollFds[0].revents);
            if (mPollFds[0].revents & POLLHUP) {
                free(mPollFds);
                free(mPollRefs);
                if (VDBG) ALOGD("Monitor thread leaving.");
                return;
            }
            if (mPollFds[0].revents == POLLIN) {
                char readBuf[2];
                read(mCtrlSocketPair[0], &readBuf, 1);
                if (DBG) ALOGD("MDnsSdListener::Monitor got %c", readBuf[0]);
                if (memcmp(RESCAN, readBuf, 1) == 0) {
                    pollCount = rescan();
                }
            }
            mPollFds[0].revents = 0;
        } else {
            if (VDBG) ALOGD("MDnsSdListener::Monitor poll timed out");
        }
    }
    free(mPollFds);
    free(mPollRefs);
}

#define DBG_RESCAN 0

int MDnsSdListener::Monitor::rescan() {
// rescan the list from mHead and make new pollfds and serviceRefs
    if (VDBG) {
        ALOGD("MDnsSdListener::Monitor poll rescanning - size=%d, live=%d", mPollSize, mLiveCount);
    }
    std::lock_guard guard(mMutex);
    Element **prevPtr = &mHead;
    int i = 1;
    if (mPollSize <= mLiveCount) {
        mPollSize = mLiveCount + 5;
        free(mPollFds);
        free(mPollRefs);
        mPollFds = (struct pollfd *)calloc(sizeof(struct pollfd), mPollSize);
        mPollRefs = (DNSServiceRef **)calloc(sizeof(DNSServiceRef *), mPollSize);
        LOG_ALWAYS_FATAL_IF((mPollFds == nullptr), "calloc failed on mPollFds with a size of %d",
                ((int)sizeof(struct pollfd)) * mPollSize);
        LOG_ALWAYS_FATAL_IF((mPollRefs == nullptr), "calloc failed on mPollRefs with a size of %d",
                ((int)sizeof(DNSServiceRef *)) * mPollSize);
    } else {
        memset(mPollFds, 0, sizeof(struct pollfd) * mPollSize);
        memset(mPollRefs, 0, sizeof(DNSServiceRef *) * mPollSize);
    }
    mPollFds[0].fd = mCtrlSocketPair[0];
    mPollFds[0].events = POLLIN;
    if (DBG_RESCAN) ALOGD("mHead = %p", mHead);
    while (*prevPtr != nullptr) {
        if (DBG_RESCAN) ALOGD("checking %p, mReady = %d", *prevPtr, (*prevPtr)->mReady);
        if ((*prevPtr)->mReady == 1) {
            int fd = DNSServiceRefSockFD((*prevPtr)->mRef);
            if (fd != -1) {
                if (DBG_RESCAN) ALOGD("  adding FD %d", fd);
                mPollFds[i].fd = fd;
                mPollFds[i].events = POLLIN;
                mPollRefs[i] = &((*prevPtr)->mRef);
                i++;
            } else {
                ALOGE("Error retreving socket FD for live ServiceRef");
            }
            prevPtr = &((*prevPtr)->mNext); // advance to the next element
        } else if ((*prevPtr)->mReady == -1) {
            if (DBG_RESCAN) ALOGD("  removing %p from  play", *prevPtr);
            Element *cur = *prevPtr;
            *prevPtr = (cur)->mNext; // change our notion of this element and don't advance
            delete cur;
        } else if ((*prevPtr)->mReady == 0) {
            // Not ready so just skip this node and continue on
            if (DBG_RESCAN) ALOGD("%p not ready.  Continuing.", *prevPtr);
            prevPtr = &((*prevPtr)->mNext);
        }
    }

    return i;
}

DNSServiceRef *MDnsSdListener::Monitor::allocateServiceRef(int id, Context *context) {
    if (lookupServiceRef(id) != nullptr) {
        delete(context);
        return nullptr;
    }
    Element *e = new Element(id, context);
    std::lock_guard guard(mMutex);
    e->mNext = mHead;
    mHead = e;
    return &(e->mRef);
}

DNSServiceRef *MDnsSdListener::Monitor::lookupServiceRef(int id) {
    std::lock_guard guard(mMutex);
    Element *cur = mHead;
    while (cur != nullptr) {
        if (cur->mId == id) {
            DNSServiceRef *result = &(cur->mRef);
            return result;
        }
        cur = cur->mNext;
    }
    return nullptr;
}

void MDnsSdListener::Monitor::startMonitoring(int id) {
    if (VDBG) ALOGD("startMonitoring %d", id);
    std::lock_guard guard(mMutex);
    for (Element* cur = mHead; cur != nullptr; cur = cur->mNext) {
        if (cur->mId == id) {
            if (DBG_RESCAN) ALOGD("marking %p as ready to be added", cur);
            mLiveCount++;
            cur->mReady = 1;
            write(mCtrlSocketPair[1], RESCAN, 1);  // trigger a rescan for a fresh poll
            if (VDBG) ALOGD("triggering rescan");
            return;
        }
    }
}

void MDnsSdListener::Monitor::freeServiceRef(int id) {
    if (VDBG) ALOGD("freeServiceRef %d", id);
    std::lock_guard guard(mMutex);
    Element* cur;
    for (Element** prevPtr = &mHead; *prevPtr != nullptr; prevPtr = &(cur->mNext)) {
        cur = *prevPtr;
        if (cur->mId == id) {
            if (DBG_RESCAN) ALOGD("marking %p as ready to be removed", cur);
            mLiveCount--;
            if (cur->mReady == 1) {
                cur->mReady = -1; // tell poll thread to delete
                cur->mRef = nullptr; // do not process further results
                write(mCtrlSocketPair[1], RESCAN, 1); // trigger a rescan for a fresh poll
                if (VDBG) ALOGD("triggering rescan");
            } else {
                *prevPtr = cur->mNext;
                delete cur;
            }
            return;
        }
    }
}

void MDnsSdListener::Monitor::deallocateServiceRef(DNSServiceRef* ref) {
    std::lock_guard guard(mMutex);
    DNSServiceRefDeallocate(*ref);
    *ref = nullptr;
}

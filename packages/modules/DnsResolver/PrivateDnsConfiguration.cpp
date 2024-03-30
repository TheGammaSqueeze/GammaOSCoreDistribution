/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "resolv"

#include "PrivateDnsConfiguration.h"

#include <algorithm>

#include <android-base/format.h>
#include <android-base/logging.h>
#include <android/binder_ibinder.h>
#include <netdutils/Slice.h>
#include <netdutils/ThreadUtil.h>
#include <sys/socket.h>

#include "DnsTlsTransport.h"
#include "ResolverEventReporter.h"
#include "doh.h"
#include "netd_resolv/resolv.h"
#include "resolv_cache.h"
#include "resolv_private.h"
#include "util.h"

using aidl::android::net::resolv::aidl::IDnsResolverUnsolicitedEventListener;
using aidl::android::net::resolv::aidl::PrivateDnsValidationEventParcel;
using android::netdutils::IPAddress;
using android::netdutils::IPSockAddr;
using android::netdutils::setThreadName;
using android::netdutils::Slice;
using std::chrono::milliseconds;

namespace android {
namespace net {

int PrivateDnsConfiguration::set(int32_t netId, uint32_t mark,
                                 const std::vector<std::string>& servers, const std::string& name,
                                 const std::string& caCert) {
    LOG(DEBUG) << "PrivateDnsConfiguration::set(" << netId << ", 0x" << std::hex << mark << std::dec
               << ", " << servers.size() << ", " << name << ")";

    // Parse the list of servers that has been passed in
    PrivateDnsTracker tmp;
    for (const auto& s : servers) {
        IPAddress ip;
        if (!IPAddress::forString(s, &ip)) {
            LOG(WARNING) << "Failed to parse server address (" << s << ")";
            return -EINVAL;
        }

        auto server = std::make_unique<DnsTlsServer>(ip);
        server->name = name;
        server->certificate = caCert;
        server->mark = mark;
        tmp[ServerIdentity(*server)] = std::move(server);
    }

    std::lock_guard guard(mPrivateDnsLock);
    if (!name.empty()) {
        mPrivateDnsModes[netId] = PrivateDnsMode::STRICT;
    } else if (!tmp.empty()) {
        mPrivateDnsModes[netId] = PrivateDnsMode::OPPORTUNISTIC;
    } else {
        mPrivateDnsModes[netId] = PrivateDnsMode::OFF;
        mPrivateDnsTransports.erase(netId);
        // TODO: signal validation threads to stop.
        return 0;
    }

    // Create the tracker if it was not present
    auto& tracker = mPrivateDnsTransports[netId];

    // Add the servers if not contained in tracker.
    for (auto& [identity, server] : tmp) {
        if (tracker.find(identity) == tracker.end()) {
            tracker[identity] = std::move(server);
        }
    }

    for (auto& [identity, server] : tracker) {
        const bool active = tmp.find(identity) != tmp.end();
        server->setActive(active);

        // For simplicity, deem the validation result of inactive servers as unreliable.
        if (!server->active() && server->validationState() == Validation::success) {
            updateServerState(identity, Validation::success_but_expired, netId);
        }

        if (needsValidation(*server)) {
            updateServerState(identity, Validation::in_process, netId);
            startValidation(identity, netId, false);
        }
    }

    return 0;
}

PrivateDnsStatus PrivateDnsConfiguration::getStatus(unsigned netId) const {
    PrivateDnsStatus status{
            .mode = PrivateDnsMode::OFF,
            .dotServersMap = {},
            .dohServersMap = {},
    };
    std::lock_guard guard(mPrivateDnsLock);

    const auto mode = mPrivateDnsModes.find(netId);
    if (mode == mPrivateDnsModes.end()) return status;
    status.mode = mode->second;

    const auto netPair = mPrivateDnsTransports.find(netId);
    if (netPair != mPrivateDnsTransports.end()) {
        for (const auto& [_, server] : netPair->second) {
            if (server->isDot() && server->active()) {
                DnsTlsServer& dotServer = *static_cast<DnsTlsServer*>(server.get());
                status.dotServersMap.emplace(dotServer, server->validationState());
            }
        }
    }

    auto it = mDohTracker.find(netId);
    if (it != mDohTracker.end()) {
        status.dohServersMap.emplace(
                netdutils::IPSockAddr::toIPSockAddr(it->second.ipAddr, kDohPort),
                it->second.status);
    }

    return status;
}

void PrivateDnsConfiguration::clear(unsigned netId) {
    LOG(DEBUG) << "PrivateDnsConfiguration::clear(" << netId << ")";
    std::lock_guard guard(mPrivateDnsLock);
    mPrivateDnsModes.erase(netId);
    mPrivateDnsTransports.erase(netId);
}

base::Result<void> PrivateDnsConfiguration::requestValidation(unsigned netId,
                                                              const ServerIdentity& identity,
                                                              uint32_t mark) {
    std::lock_guard guard(mPrivateDnsLock);

    // Running revalidation requires to mark the server as in_process, which means the server
    // won't be used until the validation passes. It's necessary and safe to run revalidation
    // when in private DNS opportunistic mode, because there's a fallback mechanics even if
    // all of the private DNS servers are in in_process state.
    if (auto it = mPrivateDnsModes.find(netId); it == mPrivateDnsModes.end()) {
        return Errorf("NetId not found in mPrivateDnsModes");
    } else if (it->second != PrivateDnsMode::OPPORTUNISTIC) {
        return Errorf("Private DNS setting is not opportunistic mode");
    }

    auto result = getPrivateDnsLocked(identity, netId);
    if (!result.ok()) {
        return result.error();
    }

    const IPrivateDnsServer* server = result.value();

    if (!server->active()) return Errorf("Server is not active");

    if (server->validationState() != Validation::success) {
        return Errorf("Server validation state mismatched");
    }

    // Don't run the validation if |mark| (from android_net_context.dns_mark) is different.
    // This is to protect validation from running on unexpected marks.
    // Validation should be associated with a mark gotten by system permission.
    if (server->validationMark() != mark) return Errorf("Socket mark mismatched");

    updateServerState(identity, Validation::in_process, netId);
    startValidation(identity, netId, true);
    return {};
}

void PrivateDnsConfiguration::startValidation(const ServerIdentity& identity, unsigned netId,
                                              bool isRevalidation) {
    // This ensures that the thread sends probe at least once in case
    // the server is removed before the thread starts running.
    // TODO: consider moving these code to the thread.
    const auto result = getPrivateDnsLocked(identity, netId);
    if (!result.ok()) return;
    DnsTlsServer server = *static_cast<const DnsTlsServer*>(result.value());

    std::thread validate_thread([this, identity, server, netId, isRevalidation] {
        setThreadName(fmt::format("TlsVerify_{}", netId));

        // cat /proc/sys/net/ipv4/tcp_syn_retries yields "6".
        //
        // Start with a 1 minute delay and backoff to once per hour.
        //
        // Assumptions:
        //     [1] Each TLS validation is ~10KB of certs+handshake+payload.
        //     [2] Network typically provision clients with <=4 nameservers.
        //     [3] Average month has 30 days.
        //
        // Each validation pass in a given hour is ~1.2MB of data. And 24
        // such validation passes per day is about ~30MB per month, in the
        // worst case. Otherwise, this will cost ~600 SYNs per month
        // (6 SYNs per ip, 4 ips per validation pass, 24 passes per day).
        auto backoff = mBackoffBuilder.build();

        while (true) {
            // ::validate() is a blocking call that performs network operations.
            // It can take milliseconds to minutes, up to the SYN retry limit.
            LOG(WARNING) << "Validating DnsTlsServer " << server.toIpString() << " with mark 0x"
                         << std::hex << server.validationMark();
            const bool success = DnsTlsTransport::validate(server, server.validationMark());
            LOG(WARNING) << "validateDnsTlsServer returned " << success << " for "
                         << server.toIpString();

            const bool needs_reeval =
                    this->recordPrivateDnsValidation(identity, netId, success, isRevalidation);

            if (!needs_reeval) {
                break;
            }

            if (backoff.hasNextTimeout()) {
                // TODO: make the thread able to receive signals to shutdown early.
                std::this_thread::sleep_for(backoff.getNextTimeout());
            } else {
                break;
            }
        }
    });
    validate_thread.detach();
}

void PrivateDnsConfiguration::sendPrivateDnsValidationEvent(const ServerIdentity& identity,
                                                            unsigned netId, bool success) const {
    LOG(DEBUG) << "Sending validation " << (success ? "success" : "failure") << " event on netId "
               << netId << " for " << identity.sockaddr.toString() << " with hostname {"
               << identity.provider << "}";
    // Send a validation event to NetdEventListenerService.
    const auto& listeners = ResolverEventReporter::getInstance().getListeners();
    if (listeners.empty()) {
        LOG(ERROR)
                << "Validation event not sent since no INetdEventListener receiver is available.";
    }
    for (const auto& it : listeners) {
        it->onPrivateDnsValidationEvent(netId, identity.sockaddr.ip().toString(), identity.provider,
                                        success);
    }

    // Send a validation event to unsolicited event listeners.
    const auto& unsolEventListeners = ResolverEventReporter::getInstance().getUnsolEventListeners();
    const PrivateDnsValidationEventParcel validationEvent = {
            .netId = static_cast<int32_t>(netId),
            .ipAddress = identity.sockaddr.ip().toString(),
            .hostname = identity.provider,
            .validation = success ? IDnsResolverUnsolicitedEventListener::VALIDATION_RESULT_SUCCESS
                                  : IDnsResolverUnsolicitedEventListener::VALIDATION_RESULT_FAILURE,
            .protocol = (identity.sockaddr.port() == kDotPort)
                                ? IDnsResolverUnsolicitedEventListener::PROTOCOL_DOT
                                : IDnsResolverUnsolicitedEventListener::PROTOCOL_DOH,
    };
    for (const auto& it : unsolEventListeners) {
        it->onPrivateDnsValidationEvent(validationEvent);
    }
}

bool PrivateDnsConfiguration::recordPrivateDnsValidation(const ServerIdentity& identity,
                                                         unsigned netId, bool success,
                                                         bool isRevalidation) {
    constexpr bool NEEDS_REEVALUATION = true;
    constexpr bool DONT_REEVALUATE = false;

    std::lock_guard guard(mPrivateDnsLock);

    auto netPair = mPrivateDnsTransports.find(netId);
    if (netPair == mPrivateDnsTransports.end()) {
        LOG(WARNING) << "netId " << netId << " was erased during private DNS validation";
        notifyValidationStateUpdate(identity.sockaddr, Validation::fail, netId);
        return DONT_REEVALUATE;
    }

    const auto mode = mPrivateDnsModes.find(netId);
    if (mode == mPrivateDnsModes.end()) {
        LOG(WARNING) << "netId " << netId << " has no private DNS validation mode";
        notifyValidationStateUpdate(identity.sockaddr, Validation::fail, netId);
        return DONT_REEVALUATE;
    }

    bool reevaluationStatus = NEEDS_REEVALUATION;
    if (success) {
        reevaluationStatus = DONT_REEVALUATE;
    } else if (mode->second == PrivateDnsMode::OFF) {
        reevaluationStatus = DONT_REEVALUATE;
    } else if (mode->second == PrivateDnsMode::OPPORTUNISTIC && !isRevalidation) {
        reevaluationStatus = DONT_REEVALUATE;
    }

    auto& tracker = netPair->second;
    auto serverPair = tracker.find(identity);
    if (serverPair == tracker.end()) {
        LOG(WARNING) << "Server " << identity.sockaddr.ip().toString()
                     << " was removed during private DNS validation";
        success = false;
        reevaluationStatus = DONT_REEVALUATE;
    } else if (!serverPair->second->active()) {
        LOG(WARNING) << "Server " << identity.sockaddr.ip().toString()
                     << " was removed from the configuration";
        success = false;
        reevaluationStatus = DONT_REEVALUATE;
    }

    // Send private dns validation result to listeners.
    if (needReportEvent(netId, identity, success)) {
        sendPrivateDnsValidationEvent(identity, netId, success);
    }

    if (success) {
        updateServerState(identity, Validation::success, netId);
    } else {
        // Validation failure is expected if a user is on a captive portal.
        // TODO: Trigger a second validation attempt after captive portal login
        // succeeds.
        const auto result = (reevaluationStatus == NEEDS_REEVALUATION) ? Validation::in_process
                                                                       : Validation::fail;
        updateServerState(identity, result, netId);
    }
    LOG(WARNING) << "Validation " << (success ? "success" : "failed");

    return reevaluationStatus;
}

void PrivateDnsConfiguration::updateServerState(const ServerIdentity& identity, Validation state,
                                                uint32_t netId) {
    const auto result = getPrivateDnsLocked(identity, netId);
    if (!result.ok()) {
        notifyValidationStateUpdate(identity.sockaddr, Validation::fail, netId);
        return;
    }

    auto* server = result.value();

    server->setValidationState(state);
    notifyValidationStateUpdate(identity.sockaddr, state, netId);

    RecordEntry record(netId, identity, state);
    mPrivateDnsLog.push(std::move(record));
}

bool PrivateDnsConfiguration::needsValidation(const IPrivateDnsServer& server) const {
    // The server is not expected to be used on the network.
    if (!server.active()) return false;

    // The server is newly added.
    if (server.validationState() == Validation::unknown_server) return true;

    // The server has failed at least one validation attempt. Give it another try.
    if (server.validationState() == Validation::fail) return true;

    // The previous validation result might be unreliable.
    if (server.validationState() == Validation::success_but_expired) return true;

    return false;
}

base::Result<IPrivateDnsServer*> PrivateDnsConfiguration::getPrivateDns(
        const ServerIdentity& identity, unsigned netId) {
    std::lock_guard guard(mPrivateDnsLock);
    return getPrivateDnsLocked(identity, netId);
}

base::Result<IPrivateDnsServer*> PrivateDnsConfiguration::getPrivateDnsLocked(
        const ServerIdentity& identity, unsigned netId) {
    auto netPair = mPrivateDnsTransports.find(netId);
    if (netPair == mPrivateDnsTransports.end()) {
        return Errorf("Failed to get private DNS: netId {} not found", netId);
    }

    auto iter = netPair->second.find(identity);
    if (iter == netPair->second.end()) {
        return Errorf("Failed to get private DNS: server {{{}/{}}} not found", identity.sockaddr,
                      identity.provider);
    }

    return iter->second.get();
}

void PrivateDnsConfiguration::setObserver(PrivateDnsValidationObserver* observer) {
    std::lock_guard guard(mPrivateDnsLock);
    mObserver = observer;
}

base::Result<netdutils::IPSockAddr> PrivateDnsConfiguration::getDohServer(unsigned netId) const {
    std::lock_guard guard(mPrivateDnsLock);
    auto it = mDohTracker.find(netId);
    if (it != mDohTracker.end()) {
        return netdutils::IPSockAddr::toIPSockAddr(it->second.ipAddr, kDohPort);
    }

    return Errorf("Failed to get DoH Server: netId {} not found", netId);
}

void PrivateDnsConfiguration::notifyValidationStateUpdate(const netdutils::IPSockAddr& sockaddr,
                                                          Validation validation,
                                                          uint32_t netId) const {
    if (mObserver) {
        mObserver->onValidationStateUpdate(sockaddr.ip().toString(), validation, netId);
    }
}

void PrivateDnsConfiguration::dump(netdutils::DumpWriter& dw) const {
    dw.println("PrivateDnsLog:");
    netdutils::ScopedIndent indentStats(dw);

    for (const auto& record : mPrivateDnsLog.copy()) {
        dw.println(fmt::format(
                "{} - netId={} PrivateDns={{{}/{}}} state={}", timestampToString(record.timestamp),
                record.netId, record.serverIdentity.sockaddr.toString(),
                record.serverIdentity.provider, validationStatusToString(record.state)));
    }
    dw.blankline();
}

void PrivateDnsConfiguration::initDoh() {
    std::lock_guard guard(mPrivateDnsLock);
    initDohLocked();
}

void PrivateDnsConfiguration::initDohLocked() {
    if (mDohDispatcher != nullptr) return;
    mDohDispatcher = doh_dispatcher_new(
            [](uint32_t net_id, bool success, const char* ip_addr, const char* host) {
                android::net::PrivateDnsConfiguration::getInstance().onDohStatusUpdate(
                        net_id, success, ip_addr, host);
            },
            [](int32_t sock) { resolv_tag_socket(sock, AID_DNS, NET_CONTEXT_INVALID_PID); });
}

int PrivateDnsConfiguration::setDoh(int32_t netId, uint32_t mark,
                                    const std::vector<std::string>& servers,
                                    const std::string& name, const std::string& caCert) {
    LOG(DEBUG) << "PrivateDnsConfiguration::setDoh(" << netId << ", 0x" << std::hex << mark
               << std::dec << ", " << servers.size() << ", " << name << ")";
    std::lock_guard guard(mPrivateDnsLock);
    if (servers.empty()) {
        clearDohLocked(netId);
        return 0;
    }

    const auto getTimeoutFromFlag = [&](const std::string_view key, int defaultValue) -> uint64_t {
        static constexpr int kMinTimeoutMs = 1000;
        uint64_t timeout = Experiments::getInstance()->getFlag(key, defaultValue);
        if (timeout < kMinTimeoutMs) {
            timeout = kMinTimeoutMs;
        }
        return timeout;
    };

    // Sort the input servers to ensure that we could get the server vector at the same order.
    std::vector<std::string> sortedServers = servers;
    // Prefer ipv6.
    std::sort(sortedServers.begin(), sortedServers.end(), [](std::string a, std::string b) {
        IPAddress ipa = IPAddress::forString(a);
        IPAddress ipb = IPAddress::forString(b);
        return ipa > ipb;
    });

    initDohLocked();

    // TODO: 1. Improve how to choose the server
    // TODO: 2. Support multiple servers
    for (const auto& entry : mAvailableDoHProviders) {
        const auto& doh = entry.getDohIdentity(sortedServers, name);
        if (!doh.ok()) continue;

        // Since the DnsResolver is expected to be configured by the system server, add the
        // restriction to prevent ResolverTestProvider from being used other than testing.
        if (entry.requireRootPermission && AIBinder_getCallingUid() != AID_ROOT) continue;

        auto it = mDohTracker.find(netId);
        // Skip if the same server already exists and its status == success.
        if (it != mDohTracker.end() && it->second == doh.value() &&
            it->second.status == Validation::success) {
            return 0;
        }
        const auto& [dohIt, _] = mDohTracker.insert_or_assign(netId, doh.value());
        const auto& dohId = dohIt->second;

        RecordEntry record(netId,
                           {netdutils::IPSockAddr::toIPSockAddr(dohId.ipAddr, kDohPort), name},
                           dohId.status);
        mPrivateDnsLog.push(std::move(record));
        LOG(INFO) << __func__ << ": Upgrading server to DoH: " << name;
        resolv_stats_set_addrs(netId, PROTO_DOH, {dohId.ipAddr}, kDohPort);

        const FeatureFlags flags = {
                .probe_timeout_ms =
                        getTimeoutFromFlag("doh_probe_timeout_ms", kDohProbeDefaultTimeoutMs),
                .idle_timeout_ms =
                        getTimeoutFromFlag("doh_idle_timeout_ms", kDohIdleDefaultTimeoutMs),
                .use_session_resumption =
                        Experiments::getInstance()->getFlag("doh_session_resumption", 0) == 1,
        };
        LOG(DEBUG) << __func__ << ": probe_timeout_ms=" << flags.probe_timeout_ms
                   << ", idle_timeout_ms=" << flags.idle_timeout_ms
                   << ", use_session_resumption=" << flags.use_session_resumption;

        return doh_net_new(mDohDispatcher, netId, dohId.httpsTemplate.c_str(), dohId.host.c_str(),
                           dohId.ipAddr.c_str(), mark, caCert.c_str(), &flags);
    }

    LOG(INFO) << __func__ << ": No suitable DoH server found";
    clearDohLocked(netId);
    return 0;
}

void PrivateDnsConfiguration::clearDohLocked(unsigned netId) {
    LOG(DEBUG) << "PrivateDnsConfiguration::clearDohLocked (" << netId << ")";
    if (mDohDispatcher != nullptr) doh_net_delete(mDohDispatcher, netId);
    mDohTracker.erase(netId);
    resolv_stats_set_addrs(netId, PROTO_DOH, {}, kDohPort);
}

void PrivateDnsConfiguration::clearDoh(unsigned netId) {
    std::lock_guard guard(mPrivateDnsLock);
    clearDohLocked(netId);
}

ssize_t PrivateDnsConfiguration::dohQuery(unsigned netId, const Slice query, const Slice answer,
                                          uint64_t timeoutMs) {
    {
        std::lock_guard guard(mPrivateDnsLock);
        // It's safe because mDohDispatcher won't be deleted after initializing.
        if (mDohDispatcher == nullptr) return DOH_RESULT_CAN_NOT_SEND;
    }
    return doh_query(mDohDispatcher, netId, query.base(), query.size(), answer.base(),
                     answer.size(), timeoutMs);
}

void PrivateDnsConfiguration::onDohStatusUpdate(uint32_t netId, bool success, const char* ipAddr,
                                                const char* host) {
    LOG(INFO) << __func__ << ": " << netId << ", " << success << ", " << ipAddr << ", " << host;
    std::lock_guard guard(mPrivateDnsLock);
    // Update the server status.
    auto it = mDohTracker.find(netId);
    if (it == mDohTracker.end() || (it->second.ipAddr != ipAddr && it->second.host != host)) {
        LOG(WARNING) << __func__ << ": Obsolete event";
        return;
    }
    Validation status = success ? Validation::success : Validation::fail;
    it->second.status = status;
    // Send the events to registered listeners.
    ServerIdentity identity = {netdutils::IPSockAddr::toIPSockAddr(ipAddr, kDohPort), host};
    if (needReportEvent(netId, identity, success)) {
        sendPrivateDnsValidationEvent(identity, netId, success);
    }
    // Add log.
    RecordEntry record(netId, identity, status);
    mPrivateDnsLog.push(std::move(record));
}

bool PrivateDnsConfiguration::needReportEvent(uint32_t netId, ServerIdentity identity,
                                              bool success) const {
    // If the result is success or DoH is not enable, no concern to report the events.
    if (success || !isDoHEnabled()) return true;
    // If the result is failure, check another transport's status to determine if we should report
    // the event.
    switch (identity.sockaddr.port()) {
        // DoH
        case kDohPort: {
            auto netPair = mPrivateDnsTransports.find(netId);
            if (netPair == mPrivateDnsTransports.end()) return true;
            for (const auto& [id, server] : netPair->second) {
                if ((identity.sockaddr.ip() == id.sockaddr.ip()) &&
                    (identity.sockaddr.port() != id.sockaddr.port()) &&
                    (server->validationState() == Validation::success)) {
                    LOG(DEBUG) << __func__
                               << ": Skip reporting DoH validation failure event, server addr: "
                               << identity.sockaddr.ip().toString();
                    return false;
                }
            }
            break;
        }
        // DoT
        case kDotPort: {
            auto it = mDohTracker.find(netId);
            if (it == mDohTracker.end()) return true;
            if (it->second == identity && it->second.status == Validation::success) {
                LOG(DEBUG) << __func__
                           << ": Skip reporting DoT validation failure event, server addr: "
                           << identity.sockaddr.ip().toString();
                return false;
            }
            break;
        }
    }
    return true;
}

}  // namespace net
}  // namespace android

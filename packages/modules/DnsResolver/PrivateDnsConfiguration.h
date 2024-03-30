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

#pragma once

#include <array>
#include <list>
#include <map>
#include <mutex>
#include <vector>

#include <android-base/format.h>
#include <android-base/logging.h>
#include <android-base/result.h>
#include <android-base/thread_annotations.h>
#include <netdutils/BackoffSequence.h>
#include <netdutils/DumpWriter.h>
#include <netdutils/InternetAddresses.h>
#include <netdutils/Slice.h>

#include "DnsTlsServer.h"
#include "LockedQueue.h"
#include "PrivateDnsValidationObserver.h"
#include "doh.h"

namespace android {
namespace net {

// TODO: decouple the dependency of DnsTlsServer.
struct PrivateDnsStatus {
    PrivateDnsMode mode;

    // TODO: change the type to std::vector<DnsTlsServer>.
    std::map<DnsTlsServer, Validation, AddressComparator> dotServersMap;

    std::map<netdutils::IPSockAddr, Validation> dohServersMap;

    std::list<DnsTlsServer> validatedServers() const {
        std::list<DnsTlsServer> servers;

        for (const auto& pair : dotServersMap) {
            if (pair.second == Validation::success) {
                servers.push_back(pair.first);
            }
        }
        return servers;
    }

    bool hasValidatedDohServers() const {
        for (const auto& [_, status] : dohServersMap) {
            if (status == Validation::success) {
                return true;
            }
        }
        return false;
    }
};

class PrivateDnsConfiguration {
  public:
    static constexpr int kDohQueryDefaultTimeoutMs = 30000;
    static constexpr int kDohProbeDefaultTimeoutMs = 60000;

    // The default value for QUIC max_idle_timeout.
    static constexpr int kDohIdleDefaultTimeoutMs = 55000;

    struct ServerIdentity {
        const netdutils::IPSockAddr sockaddr;
        const std::string provider;

        explicit ServerIdentity(const IPrivateDnsServer& server)
            : sockaddr(server.addr()), provider(server.provider()) {}
        ServerIdentity(const netdutils::IPSockAddr& addr, const std::string& host)
            : sockaddr(addr), provider(host) {}

        bool operator<(const ServerIdentity& other) const {
            return std::tie(sockaddr, provider) < std::tie(other.sockaddr, other.provider);
        }
        bool operator==(const ServerIdentity& other) const {
            return std::tie(sockaddr, provider) == std::tie(other.sockaddr, other.provider);
        }
    };

    // The only instance of PrivateDnsConfiguration.
    static PrivateDnsConfiguration& getInstance() {
        static PrivateDnsConfiguration instance;
        return instance;
    }

    int set(int32_t netId, uint32_t mark, const std::vector<std::string>& servers,
            const std::string& name, const std::string& caCert) EXCLUDES(mPrivateDnsLock);

    void initDoh() EXCLUDES(mPrivateDnsLock);

    int setDoh(int32_t netId, uint32_t mark, const std::vector<std::string>& servers,
               const std::string& name, const std::string& caCert) EXCLUDES(mPrivateDnsLock);

    PrivateDnsStatus getStatus(unsigned netId) const EXCLUDES(mPrivateDnsLock);

    void clear(unsigned netId) EXCLUDES(mPrivateDnsLock);

    void clearDoh(unsigned netId) EXCLUDES(mPrivateDnsLock);

    ssize_t dohQuery(unsigned netId, const netdutils::Slice query, const netdutils::Slice answer,
                     uint64_t timeoutMs) EXCLUDES(mPrivateDnsLock);

    // Request the server to be revalidated on a connection tagged with |mark|.
    // Returns a Result to indicate if the request is accepted.
    base::Result<void> requestValidation(unsigned netId, const ServerIdentity& identity,
                                         uint32_t mark) EXCLUDES(mPrivateDnsLock);

    void setObserver(PrivateDnsValidationObserver* observer);

    void dump(netdutils::DumpWriter& dw) const;

    void onDohStatusUpdate(uint32_t netId, bool success, const char* ipAddr, const char* host)
            EXCLUDES(mPrivateDnsLock);

    base::Result<netdutils::IPSockAddr> getDohServer(unsigned netId) const
            EXCLUDES(mPrivateDnsLock);

  private:
    typedef std::map<ServerIdentity, std::unique_ptr<IPrivateDnsServer>> PrivateDnsTracker;

    PrivateDnsConfiguration() = default;

    // Launchs a thread to run the validation for |server| on the network |netId|.
    // |isRevalidation| is true if this call is due to a revalidation request.
    void startValidation(const ServerIdentity& identity, unsigned netId, bool isRevalidation)
            REQUIRES(mPrivateDnsLock);

    bool recordPrivateDnsValidation(const ServerIdentity& identity, unsigned netId, bool success,
                                    bool isRevalidation) EXCLUDES(mPrivateDnsLock);

    void sendPrivateDnsValidationEvent(const ServerIdentity& identity, unsigned netId,
                                       bool success) const REQUIRES(mPrivateDnsLock);

    // Decide if a validation for |server| is needed. Note that servers that have failed
    // multiple validation attempts but for which there is still a validating
    // thread running are marked as being Validation::in_process.
    bool needsValidation(const IPrivateDnsServer& server) const REQUIRES(mPrivateDnsLock);

    void updateServerState(const ServerIdentity& identity, Validation state, uint32_t netId)
            REQUIRES(mPrivateDnsLock);

    // For testing.
    base::Result<IPrivateDnsServer*> getPrivateDns(const ServerIdentity& identity, unsigned netId)
            EXCLUDES(mPrivateDnsLock);

    base::Result<IPrivateDnsServer*> getPrivateDnsLocked(const ServerIdentity& identity,
                                                         unsigned netId) REQUIRES(mPrivateDnsLock);

    void initDohLocked() REQUIRES(mPrivateDnsLock);
    void clearDohLocked(unsigned netId) REQUIRES(mPrivateDnsLock);

    mutable std::mutex mPrivateDnsLock;
    std::map<unsigned, PrivateDnsMode> mPrivateDnsModes GUARDED_BY(mPrivateDnsLock);

    // Contains all servers for a network, along with their current validation status.
    // In case a server is removed due to a configuration change, it remains in this map,
    // but is marked inactive.
    // Any pending validation threads will continue running because we have no way to cancel them.
    std::map<unsigned, PrivateDnsTracker> mPrivateDnsTransports GUARDED_BY(mPrivateDnsLock);

    void notifyValidationStateUpdate(const netdutils::IPSockAddr& sockaddr, Validation validation,
                                     uint32_t netId) const REQUIRES(mPrivateDnsLock);

    bool needReportEvent(uint32_t netId, ServerIdentity identity, bool success) const
            REQUIRES(mPrivateDnsLock);

    // TODO: fix the reentrancy problem.
    PrivateDnsValidationObserver* mObserver GUARDED_BY(mPrivateDnsLock);

    DohDispatcher* mDohDispatcher;

    friend class PrivateDnsConfigurationTest;

    // It's not const because PrivateDnsConfigurationTest needs to override it.
    // TODO: make it const by dependency injection.
    netdutils::BackoffSequence<>::Builder mBackoffBuilder =
            netdutils::BackoffSequence<>::Builder()
                    .withInitialRetransmissionTime(std::chrono::seconds(60))
                    .withMaximumRetransmissionTime(std::chrono::seconds(3600));

    struct DohIdentity {
        std::string httpsTemplate;
        std::string ipAddr;
        std::string host;
        Validation status;
        bool operator<(const DohIdentity& other) const {
            return std::tie(ipAddr, host) < std::tie(other.ipAddr, other.host);
        }
        bool operator==(const DohIdentity& other) const {
            return std::tie(ipAddr, host) == std::tie(other.ipAddr, other.host);
        }
        bool operator<(const ServerIdentity& other) const {
            std::string otherIp = other.sockaddr.ip().toString();
            return std::tie(ipAddr, host) < std::tie(otherIp, other.provider);
        }
        bool operator==(const ServerIdentity& other) const {
            std::string otherIp = other.sockaddr.ip().toString();
            return std::tie(ipAddr, host) == std::tie(otherIp, other.provider);
        }
    };

    struct DohProviderEntry {
        std::string provider;
        std::set<std::string> ips;
        std::string host;
        std::string httpsTemplate;
        bool requireRootPermission;
        base::Result<DohIdentity> getDohIdentity(const std::vector<std::string>& ips,
                                                 const std::string& host) const {
            if (!host.empty() && this->host != host) return Errorf("host {} not matched", host);
            for (const auto& ip : ips) {
                if (this->ips.find(ip) == this->ips.end()) continue;
                LOG(INFO) << fmt::format("getDohIdentity: {} {}", ip, host);
                // Only pick the first one for now.
                return DohIdentity{httpsTemplate, ip, host, Validation::in_process};
            }
            return Errorf("server not matched");
        };
    };

    // TODO: Move below DoH relevant stuff into Rust implementation.
    std::map<unsigned, DohIdentity> mDohTracker GUARDED_BY(mPrivateDnsLock);
    std::array<DohProviderEntry, 4> mAvailableDoHProviders = {{
            {"Google",
             {"2001:4860:4860::8888", "2001:4860:4860::8844", "8.8.8.8", "8.8.4.4"},
             "dns.google",
             "https://dns.google/dns-query",
             false},
            {"Cloudflare",
             {"2606:4700::6810:f8f9", "2606:4700::6810:f9f9", "104.16.248.249", "104.16.249.249"},
             "cloudflare-dns.com",
             "https://cloudflare-dns.com/dns-query",
             false},

            // The DoH providers for testing only.
            // Using ResolverTestProvider requires that the DnsResolver is configured by someone
            // who has root permission, which should be run by tests only.
            {"ResolverTestProvider",
             {"127.0.0.3", "::1"},
             "example.com",
             "https://example.com/dns-query",
             true},
            {"AndroidTesting",
             {"192.0.2.100"},
             "dns.androidtesting.org",
             "https://dns.androidtesting.org/dns-query",
             false},
    }};

    struct RecordEntry {
        RecordEntry(uint32_t netId, const ServerIdentity& identity, Validation state)
            : netId(netId), serverIdentity(identity), state(state) {}

        const uint32_t netId;
        const ServerIdentity serverIdentity;
        const Validation state;
        const std::chrono::system_clock::time_point timestamp = std::chrono::system_clock::now();
    };

    LockedRingBuffer<RecordEntry> mPrivateDnsLog{100};
};

}  // namespace net
}  // namespace android

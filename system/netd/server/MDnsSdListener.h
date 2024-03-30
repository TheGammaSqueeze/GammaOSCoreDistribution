/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef _MDNSSDLISTENER_H__
#define _MDNSSDLISTENER_H__

#include <android-base/thread_annotations.h>
#include <dns_sd.h>
#include <sysutils/FrameworkListener.h>
#include <mutex>
#include <string>
#include <thread>

#include "NetdCommand.h"

// callbacks
void MDnsSdListenerDiscoverCallback(DNSServiceRef sdRef, DNSServiceFlags flags, uint32_t ifIndex,
                                    DNSServiceErrorType errorCode, const char* serviceName,
                                    const char* regType, const char* replyDomain, void* inContext);

void MDnsSdListenerRegisterCallback(DNSServiceRef sdRef, DNSServiceFlags flags,
        DNSServiceErrorType errorCode, const char *serviceName, const char *regType,
        const char *domain, void *inContext);

void MDnsSdListenerResolveCallback(DNSServiceRef sdRef, DNSServiceFlags flags, uint32_t ifIndex,
                                   DNSServiceErrorType errorCode, const char* fullname,
                                   const char* hosttarget, uint16_t port, uint16_t txtLen,
                                   const unsigned char* txtRecord, void* inContext);

void MDnsSdListenerSetHostnameCallback(DNSServiceRef, DNSServiceFlags flags,
        DNSServiceErrorType errorCode, const char *hostname, void *inContext);

void MDnsSdListenerGetAddrInfoCallback(DNSServiceRef sdRef, DNSServiceFlags flags, uint32_t ifIndex,
                                       DNSServiceErrorType errorCode, const char* hostname,
                                       const struct sockaddr* const sa, uint32_t ttl,
                                       void* inContext);

class MDnsSdListener {
  public:
    static constexpr const char* SOCKET_NAME = "mdns";

    class Context {
      public:
        int mRefNumber;

        Context(int refNumber) { mRefNumber = refNumber; }

        ~Context() {
        }
    };

    int stop(int requestId);

    int discover(uint32_t ifIndex, const char* regType, const char* domain, const int requestId,
                 const int requestFlags);

    int serviceRegister(int requestId, const char* serviceName, const char* serviceType,
                        const char* domain, const char* host, int port,
                        const std::vector<unsigned char>& txtRecord, uint32_t ifIndex);

    int resolveService(int requestId, uint32_t ifIndex, const char* serviceName,
                       const char* regType, const char* domain);

    int getAddrInfo(int requestId, uint32_t ifIndex, uint32_t protocol, const char* hostname);

    int startDaemon();

    int stopDaemon();

  private:
    class Monitor {
    public:
        Monitor();
        ~Monitor();
        DNSServiceRef *allocateServiceRef(int id, Context *c);
        void startMonitoring(int id);
        DNSServiceRef *lookupServiceRef(int id);
        void freeServiceRef(int id);
        int startService();
        int stopService();
        void run();
        void deallocateServiceRef(DNSServiceRef* ref);
        std::string threadName() { return std::string("MDnsSdMonitor"); }

      private:
        int rescan(); // returns the number of elements in the poll

        struct Element {
            Element(int id, Context* context) : mId(id), mContext(context) {}
            ~Element() { delete mContext; }

            int mId;
            Element* mNext = nullptr;
            DNSServiceRef mRef = nullptr;
            Context *mContext;
            int mReady = 0;
        };
        Element* mHead GUARDED_BY(mMutex);
        int mLiveCount;
        struct pollfd *mPollFds;
        DNSServiceRef **mPollRefs;
        int mPollSize;
        int mCtrlSocketPair[2];
        std::mutex mMutex;
        std::thread* mRescanThread;
    };
    Monitor mMonitor;
};

#endif

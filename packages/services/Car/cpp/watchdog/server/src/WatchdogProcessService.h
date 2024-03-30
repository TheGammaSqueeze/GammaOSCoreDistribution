/**
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_WATCHDOG_SERVER_SRC_WATCHDOGPROCESSSERVICE_H_
#define CPP_WATCHDOG_SERVER_SRC_WATCHDOGPROCESSSERVICE_H_

#include <android-base/chrono_utils.h>
#include <android-base/result.h>
#include <android/automotive/watchdog/ICarWatchdogClient.h>
#include <android/automotive/watchdog/internal/ICarWatchdogMonitor.h>
#include <android/automotive/watchdog/internal/ICarWatchdogServiceForSystem.h>
#include <android/automotive/watchdog/internal/PowerCycle.h>
#include <android/automotive/watchdog/internal/ProcessIdentifier.h>
#include <android/automotive/watchdog/internal/UserState.h>
#include <binder/IBinder.h>
#include <binder/Status.h>
#include <cutils/multiuser.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <utils/Vector.h>

#include <IVhalClient.h>
#include <VehicleHalTypes.h>

#include <optional>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

// Forward declaration for testing use only.
namespace internal {

class WatchdogProcessServicePeer;

}  // namespace internal

class WatchdogServiceHelperInterface;

class WatchdogProcessServiceInterface : public android::RefBase {
public:
    virtual android::base::Result<void> start() = 0;
    virtual void terminate() = 0;
    virtual android::base::Result<void> dump(int fd,
                                             const android::Vector<android::String16>& args) = 0;
    virtual void doHealthCheck(int what) = 0;

    virtual android::base::Result<void> registerWatchdogServiceHelper(
            const android::sp<WatchdogServiceHelperInterface>& helper) = 0;

    virtual android::binder::Status registerClient(const android::sp<ICarWatchdogClient>& client,
                                                   TimeoutLength timeout) = 0;
    virtual android::binder::Status unregisterClient(
            const android::sp<ICarWatchdogClient>& client) = 0;
    virtual android::binder::Status registerCarWatchdogService(
            const android::sp<IBinder>& binder) = 0;
    virtual void unregisterCarWatchdogService(const android::sp<IBinder>& binder) = 0;
    virtual android::binder::Status registerMonitor(
            const android::sp<android::automotive::watchdog::internal::ICarWatchdogMonitor>&
                    monitor) = 0;
    virtual android::binder::Status unregisterMonitor(
            const android::sp<android::automotive::watchdog::internal::ICarWatchdogMonitor>&
                    monitor) = 0;
    virtual android::binder::Status tellClientAlive(const android::sp<ICarWatchdogClient>& client,
                                                    int32_t sessionId) = 0;
    virtual android::binder::Status tellCarWatchdogServiceAlive(
            const android::sp<
                    android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>& service,
            const std::vector<android::automotive::watchdog::internal::ProcessIdentifier>&
                    clientsNotResponding,
            int32_t sessionId) = 0;
    virtual android::binder::Status tellDumpFinished(
            const android::sp<android::automotive::watchdog::internal::ICarWatchdogMonitor>&
                    monitor,
            const android::automotive::watchdog::internal::ProcessIdentifier&
                    processIdentifier) = 0;
    virtual void setEnabled(bool isEnabled) = 0;
    virtual void onUserStateChange(userid_t userId, bool isStarted) = 0;
};

class WatchdogProcessService final : public WatchdogProcessServiceInterface {
public:
    explicit WatchdogProcessService(const android::sp<Looper>& handlerLooper);
    ~WatchdogProcessService() { terminate(); }

    android::base::Result<void> start();
    void terminate();
    virtual android::base::Result<void> dump(int fd,
                                             const android::Vector<android::String16>& args);
    void doHealthCheck(int what);

    virtual android::base::Result<void> registerWatchdogServiceHelper(
            const android::sp<WatchdogServiceHelperInterface>& helper);

    virtual android::binder::Status registerClient(const android::sp<ICarWatchdogClient>& client,
                                                   TimeoutLength timeout);
    virtual android::binder::Status unregisterClient(const android::sp<ICarWatchdogClient>& client);
    virtual android::binder::Status registerCarWatchdogService(const android::sp<IBinder>& binder);
    virtual void unregisterCarWatchdogService(const android::sp<IBinder>& binder);
    virtual android::binder::Status registerMonitor(
            const android::sp<android::automotive::watchdog::internal::ICarWatchdogMonitor>&
                    monitor);
    virtual android::binder::Status unregisterMonitor(
            const android::sp<android::automotive::watchdog::internal::ICarWatchdogMonitor>&
                    monitor);
    virtual android::binder::Status tellClientAlive(const android::sp<ICarWatchdogClient>& client,
                                                    int32_t sessionId);
    virtual android::binder::Status tellCarWatchdogServiceAlive(
            const android::sp<
                    android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>& service,
            const std::vector<android::automotive::watchdog::internal::ProcessIdentifier>&
                    clientsNotResponding,
            int32_t sessionId);
    virtual android::binder::Status tellDumpFinished(
            const android::sp<android::automotive::watchdog::internal::ICarWatchdogMonitor>&
                    monitor,
            const android::automotive::watchdog::internal::ProcessIdentifier& processIdentifier);
    virtual void setEnabled(bool isEnabled);
    virtual void onUserStateChange(userid_t userId, bool isStarted);

private:
    enum ClientType {
        Regular,
        Service,
    };

    class ClientInfo {
    public:
        ClientInfo(const android::sp<ICarWatchdogClient>& client, pid_t pid, userid_t userId,
                   uint64_t startTimeMillis) :
              pid(pid),
              userId(userId),
              startTimeMillis(startTimeMillis),
              type(ClientType::Regular),
              client(client) {}
        ClientInfo(const android::sp<WatchdogServiceHelperInterface>& helper,
                   const android::sp<android::IBinder>& binder, pid_t pid, userid_t userId,
                   uint64_t startTimeMillis) :
              pid(pid),
              userId(userId),
              startTimeMillis(startTimeMillis),
              type(ClientType::Service),
              watchdogServiceHelper(helper),
              watchdogServiceBinder(binder) {}

        std::string toString() const;
        status_t linkToDeath(const android::sp<android::IBinder::DeathRecipient>& recipient) const;
        status_t unlinkToDeath(
                const android::wp<android::IBinder::DeathRecipient>& recipient) const;
        android::binder::Status checkIfAlive(TimeoutLength timeout) const;
        android::binder::Status prepareProcessTermination() const;
        bool operator!=(const ClientInfo& clientInfo) const {
            return getBinder() != clientInfo.getBinder() || type != clientInfo.type;
        }
        bool matchesBinder(const android::sp<android::IBinder>& binder) const {
            return binder == getBinder();
        }

        pid_t pid;
        userid_t userId;
        int64_t startTimeMillis;
        int sessionId;

    private:
        android::sp<android::IBinder> getBinder() const;

        ClientType type;
        android::sp<ICarWatchdogClient> client = nullptr;
        android::sp<WatchdogServiceHelperInterface> watchdogServiceHelper = nullptr;
        android::sp<IBinder> watchdogServiceBinder = nullptr;
    };

    struct HeartBeat {
        int64_t eventTime;
        int64_t value;
    };

    typedef std::unordered_map<int, ClientInfo> PingedClientMap;

    class BinderDeathRecipient final : public android::IBinder::DeathRecipient {
    public:
        explicit BinderDeathRecipient(const android::sp<WatchdogProcessService>& service);

        void binderDied(const android::wp<android::IBinder>& who) override;

    private:
        android::sp<WatchdogProcessService> mService;
    };

    class PropertyChangeListener final :
          public android::frameworks::automotive::vhal::ISubscriptionCallback {
    public:
        explicit PropertyChangeListener(const android::sp<WatchdogProcessService>& service);

        void onPropertyEvent(const std::vector<
                             std::unique_ptr<android::frameworks::automotive::vhal::IHalPropValue>>&
                                     values) override;

        void onPropertySetError(
                const std::vector<android::frameworks::automotive::vhal::HalPropError>& errors)
                override;

    private:
        android::sp<WatchdogProcessService> mService;
    };

    class MessageHandlerImpl final : public MessageHandler {
    public:
        explicit MessageHandlerImpl(const android::sp<WatchdogProcessService>& service);

        void handleMessage(const Message& message) override;

    private:
        android::sp<WatchdogProcessService> mService;
    };

private:
    android::binder::Status registerClient(const ClientInfo& clientInfo, TimeoutLength timeout);
    android::binder::Status unregisterClientLocked(const std::vector<TimeoutLength>& timeouts,
                                                   android::sp<IBinder> binder,
                                                   ClientType clientType);
    android::binder::Status tellClientAliveLocked(const android::sp<android::IBinder>& binder,
                                                  int32_t sessionId);
    android::base::Result<void> startHealthCheckingLocked(TimeoutLength timeout);
    android::base::Result<void> dumpAndKillClientsIfNotResponding(TimeoutLength timeout);
    android::base::Result<void> dumpAndKillAllProcesses(
            const std::vector<android::automotive::watchdog::internal::ProcessIdentifier>&
                    processesNotResponding,
            bool reportToVhal);
    int32_t getNewSessionId();
    android::base::Result<void> updateVhal(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValue& value);
    android::base::Result<void> connectToVhalLocked();
    void subscribeToVhalHeartBeatLocked();
    bool cacheVhalProcessIdentifier();
    void reportWatchdogAliveToVhal();
    void reportTerminatedProcessToVhal(
            const std::vector<android::automotive::watchdog::internal::ProcessIdentifier>&
                    processesNotResponding);
    android::base::Result<std::string> readProcCmdLine(int32_t pid);
    void handleBinderDeath(const android::wp<android::IBinder>& who);
    void handleVhalDeath();
    void queryVhalPropertiesLocked();
    bool isVhalPropertySupportedLocked(
            aidl::android::hardware::automotive::vehicle::VehicleProperty propId);
    void updateVhalHeartBeat(int64_t value);
    void checkVhalHealth();
    void terminateVhal();

    using Processor =
            std::function<void(std::vector<ClientInfo>&, std::vector<ClientInfo>::const_iterator)>;
    bool findClientAndProcessLocked(const std::vector<TimeoutLength> timeouts,
                                    const ClientInfo& clientInfo, const Processor& processor);
    bool findClientAndProcessLocked(const std::vector<TimeoutLength> timeouts,
                                    const android::sp<android::IBinder> binder,
                                    const Processor& processor);
    std::chrono::nanoseconds getTimeoutDurationNs(const TimeoutLength& timeout);

private:
    android::sp<Looper> mHandlerLooper;
    android::sp<MessageHandlerImpl> mMessageHandler;
    std::unordered_set<aidl::android::hardware::automotive::vehicle::VehicleProperty>
            mNotSupportedVhalProperties;
    std::shared_ptr<PropertyChangeListener> mPropertyChangeListener;
    // mLastSessionId is accessed only within main thread. No need for mutual-exclusion.
    int32_t mLastSessionId;
    bool mServiceStarted;
    std::chrono::milliseconds mVhalHealthCheckWindowMs;
    std::optional<std::chrono::nanoseconds> mOverriddenClientHealthCheckWindowNs;
    std::shared_ptr<android::frameworks::automotive::vhal::IVhalClient::OnBinderDiedCallbackFunc>
            mOnBinderDiedCallback;
    std::function<int64_t(pid_t)> mGetStartTimeForPidFunc;

    android::Mutex mMutex;
    std::unordered_map<TimeoutLength, std::vector<ClientInfo>> mClients GUARDED_BY(mMutex);
    std::unordered_map<TimeoutLength, PingedClientMap> mPingedClients GUARDED_BY(mMutex);
    std::unordered_set<userid_t> mStoppedUserIds GUARDED_BY(mMutex);
    android::sp<android::automotive::watchdog::internal::ICarWatchdogMonitor> mMonitor
            GUARDED_BY(mMutex);
    bool mIsEnabled GUARDED_BY(mMutex);
    std::shared_ptr<android::frameworks::automotive::vhal::IVhalClient> mVhalService
            GUARDED_BY(mMutex);
    std::optional<android::automotive::watchdog::internal::ProcessIdentifier> mVhalProcessIdentifier
            GUARDED_BY(mMutex);
    HeartBeat mVhalHeartBeat GUARDED_BY(mMutex);
    android::sp<WatchdogServiceHelperInterface> mWatchdogServiceHelper GUARDED_BY(mMutex);
    android::sp<BinderDeathRecipient> mBinderDeathRecipient GUARDED_BY(mMutex);

    // For unit tests.
    friend class internal::WatchdogProcessServicePeer;
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_SRC_WATCHDOGPROCESSSERVICE_H_

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

#define LOG_TAG "carwatchdogd"
#define DEBUG false  // STOPSHIP if true.

#include "WatchdogProcessService.h"

#include "UidProcStatsCollector.h"
#include "WatchdogServiceHelper.h"

#include <aidl/android/hardware/automotive/vehicle/BnVehicle.h>
#include <aidl/android/hardware/automotive/vehicle/ProcessTerminationReason.h>
#include <android-base/file.h>
#include <android-base/macros.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/automotive/watchdog/BnCarWatchdogClient.h>
#include <android/automotive/watchdog/internal/BnCarWatchdogMonitor.h>
#include <android/automotive/watchdog/internal/BnCarWatchdogServiceForSystem.h>
#include <android/hidl/manager/1.0/IServiceManager.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <hidl/HidlTransportSupport.h>
#include <utils/SystemClock.h>

#include <IVhalClient.h>
#include <VehicleHalTypes.h>

#include <utility>

namespace android {
namespace automotive {
namespace watchdog {

namespace aawi = ::android::automotive::watchdog::internal;

using aawi::BnCarWatchdogServiceForSystem;
using aawi::ICarWatchdogServiceForSystem;
using aawi::ProcessIdentifier;
using ::aidl::android::hardware::automotive::vehicle::BnVehicle;
using ::aidl::android::hardware::automotive::vehicle::ProcessTerminationReason;
using ::aidl::android::hardware::automotive::vehicle::StatusCode;
using ::aidl::android::hardware::automotive::vehicle::SubscribeOptions;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfig;
using ::aidl::android::hardware::automotive::vehicle::VehicleProperty;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyStatus;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropValue;
using ::android::IBinder;
using ::android::sp;
using ::android::String16;
using ::android::base::Error;
using ::android::base::GetIntProperty;
using ::android::base::GetProperty;
using ::android::base::ReadFileToString;
using ::android::base::Result;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::android::base::Trim;
using ::android::base::WriteStringToFd;
using ::android::binder::Status;
using ::android::frameworks::automotive::vhal::HalPropError;
using ::android::frameworks::automotive::vhal::IHalPropValue;
using ::android::frameworks::automotive::vhal::IVhalClient;
using ::android::hardware::hidl_vec;
using ::android::hardware::interfacesEqual;
using ::android::hardware::Return;
using ::android::hidl::base::V1_0::IBase;

namespace {

const std::vector<TimeoutLength> kTimeouts = {TimeoutLength::TIMEOUT_CRITICAL,
                                              TimeoutLength::TIMEOUT_MODERATE,
                                              TimeoutLength::TIMEOUT_NORMAL};

// TimeoutLength is also used as a message ID. Other message IDs should start next to
// TimeoutLength::TIMEOUT_NORMAL.
const int32_t MSG_VHAL_WATCHDOG_ALIVE = static_cast<int>(TimeoutLength::TIMEOUT_NORMAL) + 1;
const int32_t MSG_VHAL_HEALTH_CHECK = MSG_VHAL_WATCHDOG_ALIVE + 1;
const int32_t MSG_CACHE_VHAL_PROCESS_IDENTIFIER = MSG_VHAL_HEALTH_CHECK + 1;

// VHAL is supposed to send heart beat every 3s. Car watchdog checks if there is the latest heart
// beat from VHAL within 3s, allowing 1s marginal time.
// If {@code ro.carwatchdog.vhal_healthcheck.interval} is set, car watchdog checks VHAL health at
// the given interval. The lower bound of the interval is 3s.
constexpr int32_t kDefaultVhalCheckIntervalSec = 3;
constexpr std::chrono::milliseconds kHealthCheckDelayMs = 1s;

constexpr int32_t kMissingIntPropertyValue = -1;

constexpr const char kPropertyVhalCheckInterval[] = "ro.carwatchdog.vhal_healthcheck.interval";
constexpr const char kPropertyClientCheckInterval[] = "ro.carwatchdog.client_healthcheck.interval";
constexpr const char kServiceName[] = "WatchdogProcessService";
constexpr const char kHidlVhalInterfaceName[] = "android.hardware.automotive.vehicle@2.0::IVehicle";
constexpr const char kAidlVhalInterfaceName[] =
        "android.hardware.automotive.vehicle.IVehicle/default";

std::string toPidString(const std::vector<ProcessIdentifier>& processIdentifiers) {
    size_t size = processIdentifiers.size();
    if (size == 0) {
        return "";
    }
    std::string buffer;
    StringAppendF(&buffer, "%d", processIdentifiers[0].pid);
    for (size_t i = 1; i < size; i++) {
        StringAppendF(&buffer, ", %d", processIdentifiers[i].pid);
    }
    return buffer;
}

bool isSystemShuttingDown() {
    std::string sysPowerCtl;
    std::istringstream tokenStream(GetProperty("sys.powerctl", ""));
    std::getline(tokenStream, sysPowerCtl, ',');
    return sysPowerCtl == "reboot" || sysPowerCtl == "shutdown";
}

int64_t getStartTimeForPid(pid_t pid) {
    auto pidStat = UidProcStatsCollector::readStatFileForPid(pid);
    if (!pidStat.ok()) {
        return elapsedRealtime();
    }
    return pidStat->startTimeMillis;
}

Result<pid_t> queryHidlServiceManagerForVhalPid() {
    using android::hidl::manager::V1_0::IServiceManager;
    pid_t pid = -1;
    Return<void> ret = IServiceManager::getService()->debugDump([&](auto& hals) {
        for (const auto& info : hals) {
            if (info.pid == static_cast<int>(IServiceManager::PidConstant::NO_PID)) {
                continue;
            }
            if (info.interfaceName == kHidlVhalInterfaceName) {
                pid = info.pid;
                return;
            }
        }
    });

    if (!ret.isOk()) {
        return Error() << "Failed to get VHAL process id from HIDL service manager";
    }
    if (pid == -1) {
        return Error() << "No VHAL service registered to HIDL service manager";
    }
    return pid;
}

Result<pid_t> queryAidlServiceManagerForVhalPid() {
    using ServiceDebugInfo = android::IServiceManager::ServiceDebugInfo;
    std::vector<ServiceDebugInfo> serviceDebugInfos =
            defaultServiceManager()->getServiceDebugInfo();
    for (const auto& serviceDebugInfo : serviceDebugInfos) {
        if (serviceDebugInfo.name == kAidlVhalInterfaceName) {
            return serviceDebugInfo.pid;
        }
    }
    return Error() << "No VHAL service registered to AIDL service manager";
}

}  // namespace

WatchdogProcessService::WatchdogProcessService(const sp<Looper>& handlerLooper) :
      mHandlerLooper(handlerLooper),
      mLastSessionId(0),
      mServiceStarted(false),
      mIsEnabled(true),
      mVhalService(nullptr) {
    mOnBinderDiedCallback =
            std::make_shared<IVhalClient::OnBinderDiedCallbackFunc>([this] { handleVhalDeath(); });
    for (const auto& timeout : kTimeouts) {
        mClients.insert(std::make_pair(timeout, std::vector<ClientInfo>()));
        mPingedClients.insert(std::make_pair(timeout, PingedClientMap()));
    }

    int32_t vhalHealthCheckIntervalSec =
            GetIntProperty(kPropertyVhalCheckInterval, kDefaultVhalCheckIntervalSec);
    vhalHealthCheckIntervalSec = std::max(vhalHealthCheckIntervalSec, kDefaultVhalCheckIntervalSec);
    mVhalHealthCheckWindowMs = std::chrono::seconds(vhalHealthCheckIntervalSec);

    int32_t clientHealthCheckIntervalSec =
            GetIntProperty(kPropertyClientCheckInterval, kMissingIntPropertyValue);
    // Overridden timeout value must be greater than or equal to the maximum possible timeout value.
    // Otherwise, clients will be pinged more frequently than the guaranteed timeout duration.
    if (clientHealthCheckIntervalSec != kMissingIntPropertyValue) {
        int32_t normalSec = std::chrono::duration_cast<std::chrono::seconds>(
                                    getTimeoutDurationNs(TimeoutLength::TIMEOUT_NORMAL))
                                    .count();
        mOverriddenClientHealthCheckWindowNs = std::optional<std::chrono::seconds>{
                std::max(clientHealthCheckIntervalSec, normalSec)};
    }

    mGetStartTimeForPidFunc = &getStartTimeForPid;
}

Result<void> WatchdogProcessService::registerWatchdogServiceHelper(
        const sp<WatchdogServiceHelperInterface>& helper) {
    if (helper == nullptr) {
        return Error() << "Must provide a non-null watchdog service helper instance";
    }
    Mutex::Autolock lock(mMutex);
    mWatchdogServiceHelper = helper;
    return {};
}

Status WatchdogProcessService::registerClient(const sp<ICarWatchdogClient>& client,
                                              TimeoutLength timeout) {
    if (client == nullptr) {
        return Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                         "Must provide non-null client");
    }
    pid_t callingPid = IPCThreadState::self()->getCallingPid();
    uid_t callingUid = IPCThreadState::self()->getCallingUid();

    ClientInfo clientInfo(client, callingPid, callingUid, mGetStartTimeForPidFunc(callingPid));
    return registerClient(clientInfo, timeout);
}

Status WatchdogProcessService::unregisterClient(const sp<ICarWatchdogClient>& client) {
    if (client == nullptr) {
        return Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                         "Must provide non-null client");
    }
    Mutex::Autolock lock(mMutex);
    sp<IBinder> binder = BnCarWatchdogClient::asBinder(client);
    // kTimeouts is declared as global static constant to cover all kinds of timeout (CRITICAL,
    // MODERATE, NORMAL).
    return unregisterClientLocked(kTimeouts, binder, ClientType::Regular);
}

Status WatchdogProcessService::registerCarWatchdogService(const sp<IBinder>& binder) {
    pid_t callingPid = IPCThreadState::self()->getCallingPid();
    uid_t callingUid = IPCThreadState::self()->getCallingUid();

    sp<WatchdogServiceHelperInterface> helper;
    {
        Mutex::Autolock lock(mMutex);
        if (mWatchdogServiceHelper == nullptr) {
            return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE,
                                             "Watchdog service helper instance is null");
        }
        helper = mWatchdogServiceHelper;
    }

    ClientInfo clientInfo(helper, binder, callingPid, callingUid,
                          mGetStartTimeForPidFunc(callingPid));
    return registerClient(clientInfo, TimeoutLength::TIMEOUT_CRITICAL);
}

void WatchdogProcessService::unregisterCarWatchdogService(const sp<IBinder>& binder) {
    Mutex::Autolock lock(mMutex);

    std::vector<TimeoutLength> timeouts = {TimeoutLength::TIMEOUT_CRITICAL};
    unregisterClientLocked(timeouts, binder, ClientType::Service);
}

Status WatchdogProcessService::registerMonitor(const sp<aawi::ICarWatchdogMonitor>& monitor) {
    if (monitor == nullptr) {
        return Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                         "Must provide non-null monitor");
    }
    sp<BinderDeathRecipient> binderDeathRecipient;
    sp<IBinder> binder = aawi::BnCarWatchdogMonitor::asBinder(monitor);
    {
        Mutex::Autolock lock(mMutex);
        if (mBinderDeathRecipient == nullptr) {
            return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE,
                                             "Service is not initialized");
        }
        if (mMonitor != nullptr) {
            if (binder == aawi::BnCarWatchdogMonitor::asBinder(mMonitor)) {
                return Status::ok();
            }
            aawi::BnCarWatchdogMonitor::asBinder(mMonitor)->unlinkToDeath(mBinderDeathRecipient);
        }
        mMonitor = monitor;
        binderDeathRecipient = mBinderDeathRecipient;
    }
    if (status_t ret = binder->linkToDeath(binderDeathRecipient); ret != OK) {
        {
            Mutex::Autolock lock(mMutex);
            if (mMonitor != nullptr && binder == aawi::BnCarWatchdogMonitor::asBinder(mMonitor)) {
                mMonitor.clear();
            }
        }
        ALOGW("Failed to register the monitor as it is dead.");
        return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE, "The monitor is dead.");
    }
    if (DEBUG) {
        ALOGD("Car watchdog monitor is registered");
    }
    return Status::ok();
}

Status WatchdogProcessService::unregisterMonitor(const sp<aawi::ICarWatchdogMonitor>& monitor) {
    if (monitor == nullptr) {
        return Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                         "Must provide non-null monitor");
    }
    Mutex::Autolock lock(mMutex);
    if (mBinderDeathRecipient == nullptr) {
        return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE, "Service is not initialized");
    }
    sp<IBinder> curBinder = aawi::BnCarWatchdogMonitor::asBinder(mMonitor);
    sp<IBinder> newBinder = aawi::BnCarWatchdogMonitor::asBinder(monitor);
    if (curBinder != newBinder) {
        ALOGW("Failed to unregister the monitor as it has not been registered.");
        return Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                         "The monitor has not been registered.");
    }
    curBinder->unlinkToDeath(mBinderDeathRecipient);
    mMonitor.clear();
    if (DEBUG) {
        ALOGD("Car watchdog monitor is unregistered");
    }
    return Status::ok();
}

Status WatchdogProcessService::tellClientAlive(const sp<ICarWatchdogClient>& client,
                                               int32_t sessionId) {
    Mutex::Autolock lock(mMutex);
    return tellClientAliveLocked(BnCarWatchdogClient::asBinder(client), sessionId);
}

Status WatchdogProcessService::tellCarWatchdogServiceAlive(
        const sp<ICarWatchdogServiceForSystem>& service,
        const std::vector<ProcessIdentifier>& clientsNotResponding, int32_t sessionId) {
    Status status;
    {
        Mutex::Autolock lock(mMutex);
        if (DEBUG) {
            if (clientsNotResponding.size() > 0) {
                ALOGD("CarWatchdogService(session: %d) responded with non-responding clients: %s",
                      sessionId, toPidString(clientsNotResponding).c_str());
            }
        }
        status = tellClientAliveLocked(BnCarWatchdogServiceForSystem::asBinder(service), sessionId);
    }
    if (status.isOk()) {
        dumpAndKillAllProcesses(clientsNotResponding, /*reportToVhal=*/true);
    }
    return status;
}

Status WatchdogProcessService::tellDumpFinished(const sp<aawi::ICarWatchdogMonitor>& monitor,
                                                const ProcessIdentifier& processIdentifier) {
    Mutex::Autolock lock(mMutex);
    if (mMonitor == nullptr || monitor == nullptr ||
        aawi::BnCarWatchdogMonitor::asBinder(monitor) !=
                aawi::BnCarWatchdogMonitor::asBinder(mMonitor)) {
        return Status::
                fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                  "The monitor is not registered or an invalid monitor is given");
    }
    ALOGI("Process(pid: %d) has been dumped and killed", processIdentifier.pid);
    return Status::ok();
}

void WatchdogProcessService::setEnabled(bool isEnabled) {
    Mutex::Autolock lock(mMutex);
    if (mIsEnabled == isEnabled) {
        return;
    }
    ALOGI("%s is %s", kServiceName, isEnabled ? "enabled" : "disabled");
    mIsEnabled = isEnabled;
    mHandlerLooper->removeMessages(mMessageHandler, MSG_VHAL_HEALTH_CHECK);
    if (!mIsEnabled) {
        return;
    }
    if (mNotSupportedVhalProperties.count(VehicleProperty::VHAL_HEARTBEAT) == 0) {
        mVhalHeartBeat.eventTime = uptimeMillis();
        std::chrono::nanoseconds intervalNs = mVhalHealthCheckWindowMs + kHealthCheckDelayMs;
        mHandlerLooper->sendMessageDelayed(intervalNs.count(), mMessageHandler,
                                           Message(MSG_VHAL_HEALTH_CHECK));
    }
    for (const auto& timeout : kTimeouts) {
        mHandlerLooper->removeMessages(mMessageHandler, static_cast<int>(timeout));
        startHealthCheckingLocked(timeout);
    }
}

void WatchdogProcessService::onUserStateChange(userid_t userId, bool isStarted) {
    std::string buffer;
    Mutex::Autolock lock(mMutex);
    if (isStarted) {
        mStoppedUserIds.erase(userId);
    } else {
        mStoppedUserIds.insert(userId);
    }
}

Result<void> WatchdogProcessService::dump(int fd, const Vector<String16>& /*args*/) {
    Mutex::Autolock lock(mMutex);
    const char* indent = "  ";
    const char* doubleIndent = "    ";
    std::string buffer;
    WriteStringToFd("CAR WATCHDOG PROCESS SERVICE\n", fd);
    WriteStringToFd(StringPrintf("%s%s enabled: %s\n", indent, kServiceName,
                                 mIsEnabled ? "true" : "false"),
                    fd);
    WriteStringToFd(StringPrintf("%sRegistered clients\n", indent), fd);
    int count = 1;
    for (const auto& timeout : kTimeouts) {
        std::vector<ClientInfo>& clients = mClients[timeout];
        for (auto it = clients.begin(); it != clients.end(); it++, count++) {
            WriteStringToFd(StringPrintf("%sClient #%d: %s\n", doubleIndent, count,
                                         it->toString().c_str()),
                            fd);
        }
    }
    WriteStringToFd(StringPrintf("%sMonitor registered: %s\n", indent,
                                 mMonitor == nullptr ? "false" : "true"),
                    fd);
    WriteStringToFd(StringPrintf("%sisSystemShuttingDown: %s\n", indent,
                                 isSystemShuttingDown() ? "true" : "false"),
                    fd);
    buffer = "none";
    bool first = true;
    for (const auto& userId : mStoppedUserIds) {
        if (first) {
            buffer = StringPrintf("%d", userId);
            first = false;
        } else {
            StringAppendF(&buffer, ", %d", userId);
        }
    }
    WriteStringToFd(StringPrintf("%sStopped users: %s\n", indent, buffer.c_str()), fd);
    WriteStringToFd(StringPrintf("%sVHAL health check interval: %lldms\n", indent,
                                 mVhalHealthCheckWindowMs.count()),
                    fd);
    if (mVhalProcessIdentifier.has_value()) {
        WriteStringToFd(StringPrintf("%sVHAL process identifier (PID = %d, Start time millis = "
                                     "%" PRIi64 ")",
                                     indent, mVhalProcessIdentifier->pid,
                                     mVhalProcessIdentifier->startTimeMillis),
                        fd);
    }
    return {};
}

void WatchdogProcessService::doHealthCheck(int what) {
    mHandlerLooper->removeMessages(mMessageHandler, what);
    if (Mutex::Autolock lock(mMutex); !mIsEnabled) {
        return;
    }
    const TimeoutLength timeout = static_cast<TimeoutLength>(what);
    dumpAndKillClientsIfNotResponding(timeout);

    /* Generates a temporary/local vector containing clients.
     * Using a local copy may send unnecessary ping messages to clients after they are unregistered.
     * Clients should be able to handle them.
     */
    std::vector<ClientInfo> clientsToCheck;
    PingedClientMap& pingedClients = mPingedClients[timeout];
    {
        Mutex::Autolock lock(mMutex);
        pingedClients.clear();
        clientsToCheck = mClients[timeout];
        for (auto& clientInfo : clientsToCheck) {
            if (mStoppedUserIds.count(clientInfo.userId) > 0) {
                continue;
            }
            int sessionId = getNewSessionId();
            clientInfo.sessionId = sessionId;
            pingedClients.insert(std::make_pair(sessionId, clientInfo));
        }
    }

    for (const auto& clientInfo : clientsToCheck) {
        Status status = clientInfo.checkIfAlive(timeout);
        if (!status.isOk()) {
            ALOGW("Sending a ping message to client(pid: %d) failed: %s", clientInfo.pid,
                  status.exceptionMessage().c_str());
            {
                Mutex::Autolock lock(mMutex);
                pingedClients.erase(clientInfo.sessionId);
            }
        }
    }
    // Though the size of pingedClients is a more specific measure, clientsToCheck is used as a
    // conservative approach.
    if (clientsToCheck.size() > 0) {
        auto durationNs = getTimeoutDurationNs(timeout);
        mHandlerLooper->sendMessageDelayed(durationNs.count(), mMessageHandler, Message(what));
    }
}

Result<void> WatchdogProcessService::start() {
    {
        Mutex::Autolock lock(mMutex);
        if (mServiceStarted) {
            return Error(INVALID_OPERATION) << "Cannot start process monitoring more than once";
        }
        auto thiz = sp<WatchdogProcessService>::fromExisting(this);
        mMessageHandler = sp<MessageHandlerImpl>::make(thiz);
        mBinderDeathRecipient = sp<BinderDeathRecipient>::make(thiz);
        mPropertyChangeListener = std::make_shared<PropertyChangeListener>(thiz);
        mServiceStarted = true;
    }
    reportWatchdogAliveToVhal();
    return {};
}

void WatchdogProcessService::terminate() {
    Mutex::Autolock lock(mMutex);
    if (!mServiceStarted) {
        return;
    }
    for (const auto& timeout : kTimeouts) {
        std::vector<ClientInfo>& clients = mClients[timeout];
        for (auto it = clients.begin(); it != clients.end();) {
            it->unlinkToDeath(mBinderDeathRecipient);
            it = clients.erase(it);
        }
    }
    mWatchdogServiceHelper.clear();
    if (mMonitor != nullptr) {
        sp<IBinder> binder = aawi::BnCarWatchdogMonitor::asBinder(mMonitor);
        binder->unlinkToDeath(mBinderDeathRecipient);
    }
    mBinderDeathRecipient.clear();
    mHandlerLooper->removeMessages(mMessageHandler, MSG_VHAL_HEALTH_CHECK);
    mServiceStarted = false;
    if (mVhalService == nullptr) {
        return;
    }
    if (mNotSupportedVhalProperties.count(VehicleProperty::VHAL_HEARTBEAT) == 0) {
        std::vector<int32_t> propIds = {static_cast<int32_t>(VehicleProperty::VHAL_HEARTBEAT)};
        auto result =
                mVhalService->getSubscriptionClient(mPropertyChangeListener)->unsubscribe(propIds);
        if (!result.ok()) {
            ALOGW("Failed to unsubscribe from VHAL_HEARTBEAT.");
        }
    }
    mVhalService->removeOnBinderDiedCallback(mOnBinderDiedCallback);
    mVhalService.reset();
}

Status WatchdogProcessService::registerClient(const ClientInfo& clientInfo, TimeoutLength timeout) {
    sp<BinderDeathRecipient> binderDeathRecipient;
    {
        Mutex::Autolock lock(mMutex);
        if (mBinderDeathRecipient == nullptr) {
            return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE,
                                             "Service is not initialized");
        }
        if (findClientAndProcessLocked(kTimeouts, clientInfo, nullptr)) {
            ALOGW("Failed to register (%s) as it is already registered.",
                  clientInfo.toString().c_str());
            return Status::ok();
        }
        std::vector<ClientInfo>& clients = mClients[timeout];
        clients.emplace_back(clientInfo);
        binderDeathRecipient = mBinderDeathRecipient;
    }
    if (status_t status = clientInfo.linkToDeath(binderDeathRecipient); status != OK) {
        Mutex::Autolock lock(mMutex);
        std::vector<TimeoutLength> timeouts = {timeout};
        findClientAndProcessLocked(timeouts, clientInfo,
                                   [&](std::vector<ClientInfo>& clients,
                                       std::vector<ClientInfo>::const_iterator it) {
                                       clients.erase(it);
                                   });
        ALOGW("Failed to register (%s) as it is dead", clientInfo.toString().c_str());
        std::string errorStr = StringPrintf("(%s) is dead", clientInfo.toString().c_str());
        return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE, errorStr.c_str());
    }
    if (DEBUG) {
        ALOGD("Car watchdog client (%s, timeout = %d) is registered", clientInfo.toString().c_str(),
              timeout);
    }
    Mutex::Autolock lock(mMutex);
    // If the client array becomes non-empty, start health checking.
    if (mClients[timeout].size() == 1) {
        startHealthCheckingLocked(timeout);
        ALOGI("Starting health checking for timeout = %d", timeout);
    }
    return Status::ok();
}

Status WatchdogProcessService::unregisterClientLocked(const std::vector<TimeoutLength>& timeouts,
                                                      sp<IBinder> binder, ClientType clientType) {
    const char* clientName = clientType == ClientType::Regular ? "client" : "watchdog service";
    bool result = findClientAndProcessLocked(timeouts, binder,
                                             [&](std::vector<ClientInfo>& clients,
                                                 std::vector<ClientInfo>::const_iterator it) {
                                                 it->unlinkToDeath(mBinderDeathRecipient);
                                                 clients.erase(it);
                                             });
    if (!result) {
        std::string errorStr = StringPrintf("The %s has not been registered", clientName);
        const char* errorCause = errorStr.c_str();
        ALOGW("Failed to unregister the %s: %s", clientName, errorCause);
        return Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT, errorCause);
    }
    if (DEBUG) {
        ALOGD("Car watchdog %s is unregistered", clientName);
    }
    return Status::ok();
}

Status WatchdogProcessService::tellClientAliveLocked(const sp<IBinder>& binder, int32_t sessionId) {
    for (const auto& timeout : kTimeouts) {
        PingedClientMap& clients = mPingedClients[timeout];
        PingedClientMap::const_iterator it = clients.find(sessionId);
        if (it == clients.cend() || !it->second.matchesBinder(binder)) {
            continue;
        }
        clients.erase(it);
        return Status::ok();
    }
    return Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                     "The client is not registered or the session ID is not found");
}

bool WatchdogProcessService::findClientAndProcessLocked(const std::vector<TimeoutLength> timeouts,
                                                        const ClientInfo& clientInfo,
                                                        const Processor& processor) {
    for (const auto& timeout : timeouts) {
        std::vector<ClientInfo>& clients = mClients[timeout];
        for (auto it = clients.begin(); it != clients.end(); it++) {
            if (std::as_const(*it) != clientInfo) {
                continue;
            }
            if (processor != nullptr) {
                processor(clients, it);
            }
            return true;
        }
    }
    return false;
}

bool WatchdogProcessService::findClientAndProcessLocked(const std::vector<TimeoutLength> timeouts,
                                                        const sp<IBinder> binder,
                                                        const Processor& processor) {
    for (const auto& timeout : timeouts) {
        std::vector<ClientInfo>& clients = mClients[timeout];
        for (auto it = clients.begin(); it != clients.end(); it++) {
            if (!it->matchesBinder(binder)) {
                continue;
            }
            if (processor != nullptr) {
                processor(clients, it);
            }
            return true;
        }
    }
    return false;
}

Result<void> WatchdogProcessService::startHealthCheckingLocked(TimeoutLength timeout) {
    PingedClientMap& clients = mPingedClients[timeout];
    clients.clear();
    int what = static_cast<int>(timeout);
    auto durationNs = getTimeoutDurationNs(timeout);
    mHandlerLooper->sendMessageDelayed(durationNs.count(), mMessageHandler, Message(what));
    return {};
}

Result<void> WatchdogProcessService::dumpAndKillClientsIfNotResponding(TimeoutLength timeout) {
    std::vector<ProcessIdentifier> processIdentifiers;
    std::vector<const ClientInfo*> clientsToNotify;
    {
        Mutex::Autolock lock(mMutex);
        PingedClientMap& clients = mPingedClients[timeout];
        for (PingedClientMap::const_iterator it = clients.cbegin(); it != clients.cend(); it++) {
            pid_t pid = -1;
            userid_t userId = -1;
            uint64_t startTimeMillis = 0;
            std::vector<TimeoutLength> timeouts = {timeout};
            findClientAndProcessLocked(timeouts, it->second,
                                       [&](std::vector<ClientInfo>& cachedClients,
                                           std::vector<ClientInfo>::const_iterator
                                                   cachedClientsIt) {
                                           pid = cachedClientsIt->pid;
                                           startTimeMillis = cachedClientsIt->startTimeMillis;
                                           userId = cachedClientsIt->userId;
                                           cachedClients.erase(cachedClientsIt);
                                       });
            if (pid != -1 && mStoppedUserIds.count(userId) == 0) {
                clientsToNotify.emplace_back(&it->second);
                ProcessIdentifier processIdentifier;
                processIdentifier.pid = pid;
                processIdentifier.startTimeMillis = startTimeMillis;
                processIdentifiers.push_back(processIdentifier);
            }
        }
    }
    for (const ClientInfo*& clientInfo : clientsToNotify) {
        clientInfo->prepareProcessTermination();
    }
    return dumpAndKillAllProcesses(processIdentifiers, /*reportToVhal=*/true);
}

Result<void> WatchdogProcessService::dumpAndKillAllProcesses(
        const std::vector<ProcessIdentifier>& processesNotResponding, bool reportToVhal) {
    size_t size = processesNotResponding.size();
    if (size == 0) {
        return {};
    }
    std::string pidString = toPidString(processesNotResponding);
    sp<aawi::ICarWatchdogMonitor> monitor;
    {
        Mutex::Autolock lock(mMutex);
        if (mMonitor == nullptr) {
            std::string errorMsg =
                    StringPrintf("Failed to dump and kill processes(pid = %s): Monitor is not set",
                                 pidString.c_str());
            ALOGW("%s", errorMsg.c_str());
            return Error() << errorMsg;
        }
        monitor = mMonitor;
    }
    if (isSystemShuttingDown()) {
        ALOGI("Skip dumping and killing processes(%s): The system is shutting down",
              pidString.c_str());
        return {};
    }
    if (reportToVhal) {
        reportTerminatedProcessToVhal(processesNotResponding);
    }
    monitor->onClientsNotResponding(processesNotResponding);
    if (DEBUG) {
        ALOGD("Dumping and killing processes is requested: %s", pidString.c_str());
    }
    return {};
}

// Handle when car watchdog clients die.
void WatchdogProcessService::handleBinderDeath(const wp<IBinder>& who) {
    Mutex::Autolock lock(mMutex);
    IBinder* binder = who.unsafe_get();
    // Check if dead binder is monitor.
    sp<IBinder> monitor = aawi::BnCarWatchdogMonitor::asBinder(mMonitor);
    if (monitor == binder) {
        mMonitor.clear();
        ALOGW("The monitor has died.");
        return;
    }
    findClientAndProcessLocked(kTimeouts, who.promote(),
                               [&](std::vector<ClientInfo>& clients,
                                   std::vector<ClientInfo>::const_iterator it) {
                                   ALOGW("Client(pid: %d) died", it->pid);
                                   clients.erase(it);
                               });
}

// Handle when VHAL dies.
void WatchdogProcessService::handleVhalDeath() {
    Mutex::Autolock lock(mMutex);
    ALOGW("VHAL has died.");
    mHandlerLooper->removeMessages(mMessageHandler, MSG_VHAL_HEALTH_CHECK);
    // Destroying mVHalService would remove all onBinderDied callbacks.
    mVhalService.reset();
}

void WatchdogProcessService::reportWatchdogAliveToVhal() {
    if (mNotSupportedVhalProperties.count(VehicleProperty::WATCHDOG_ALIVE) > 0) {
        ALOGW("VHAL doesn't support WATCHDOG_ALIVE. Car watchdog will not update WATCHDOG_ALIVE.");
        return;
    }
    int64_t systemUptime = uptimeMillis();
    VehiclePropValue propValue{
            .prop = static_cast<int32_t>(VehicleProperty::WATCHDOG_ALIVE),
            .value.int64Values = {systemUptime},
    };
    const auto& ret = updateVhal(propValue);
    if (!ret.ok()) {
        ALOGW("Failed to update WATCHDOG_ALIVE VHAL property. Will try again in 3s, error: %s",
              ret.error().message().c_str());
    }
    // Update VHAL with the interval of TIMEOUT_CRITICAL(3s).
    auto durationNs = getTimeoutDurationNs(TimeoutLength::TIMEOUT_CRITICAL);
    mHandlerLooper->removeMessages(mMessageHandler, MSG_VHAL_WATCHDOG_ALIVE);
    mHandlerLooper->sendMessageDelayed(durationNs.count(), mMessageHandler,
                                       Message(MSG_VHAL_WATCHDOG_ALIVE));
}

void WatchdogProcessService::reportTerminatedProcessToVhal(
        const std::vector<ProcessIdentifier>& processesNotResponding) {
    if (mNotSupportedVhalProperties.count(VehicleProperty::WATCHDOG_TERMINATED_PROCESS) > 0) {
        ALOGW("VHAL doesn't support WATCHDOG_TERMINATED_PROCESS. Terminated process is not "
              "reported to VHAL.");
        return;
    }
    for (auto&& processIdentifier : processesNotResponding) {
        const auto& retCmdLine = readProcCmdLine(processIdentifier.pid);
        if (!retCmdLine.ok()) {
            ALOGW("Failed to get process command line for pid(%d): %s", processIdentifier.pid,
                  retCmdLine.error().message().c_str());
            continue;
        }
        std::string procCmdLine = retCmdLine.value();
        VehiclePropValue propValue{
                .prop = static_cast<int32_t>(VehicleProperty::WATCHDOG_TERMINATED_PROCESS),
                .value.int32Values = {static_cast<int32_t>(
                        ProcessTerminationReason::NOT_RESPONDING)},
                .value.stringValue = procCmdLine,
        };
        const auto& retUpdate = updateVhal(propValue);
        if (!retUpdate.ok()) {
            ALOGW("Failed to update WATCHDOG_TERMINATED_PROCESS VHAL property(command line: %s)",
                  procCmdLine.c_str());
        }
    }
}

Result<void> WatchdogProcessService::updateVhal(const VehiclePropValue& value) {
    Mutex::Autolock lock(mMutex);
    const auto& connectRet = connectToVhalLocked();
    if (!connectRet.ok()) {
        std::string errorMsg = "VHAL is not connected: " + connectRet.error().message();
        ALOGW("%s", errorMsg.c_str());
        return Error() << errorMsg;
    }
    int32_t propId = value.prop;
    if (mNotSupportedVhalProperties.count(static_cast<VehicleProperty>(propId)) > 0) {
        std::string errorMsg = StringPrintf("VHAL doesn't support property(id: %d)", propId);
        ALOGW("%s", errorMsg.c_str());
        return Error() << errorMsg;
    }

    auto halPropValue = mVhalService->createHalPropValue(propId);
    halPropValue->setInt32Values(value.value.int32Values);
    halPropValue->setInt64Values(value.value.int64Values);
    halPropValue->setStringValue(value.value.stringValue);
    if (auto result = mVhalService->setValueSync(*halPropValue); !result.ok()) {
        return Error() << "Failed to set propValue(" << propId
                       << ") to VHAL, error: " << result.error().message();
    }

    return {};
}

Result<std::string> WatchdogProcessService::readProcCmdLine(int32_t pid) {
    std::string cmdLinePath = StringPrintf("/proc/%d/cmdline", pid);
    std::string procCmdLine;
    if (ReadFileToString(cmdLinePath, &procCmdLine)) {
        std::replace(procCmdLine.begin(), procCmdLine.end(), '\0', ' ');
        procCmdLine = Trim(procCmdLine);
        return procCmdLine;
    }
    return Error() << "Failed to read " << cmdLinePath;
}

Result<void> WatchdogProcessService::connectToVhalLocked() {
    if (mVhalService != nullptr) {
        return {};
    }
    mVhalService = IVhalClient::tryCreate();
    if (mVhalService == nullptr) {
        return Error() << "Failed to connect to VHAL.";
    }
    mVhalService->addOnBinderDiedCallback(mOnBinderDiedCallback);
    queryVhalPropertiesLocked();
    subscribeToVhalHeartBeatLocked();
    ALOGI("Successfully connected to VHAL.");
    return {};
}

void WatchdogProcessService::queryVhalPropertiesLocked() {
    mNotSupportedVhalProperties.clear();
    std::vector<VehicleProperty> propIds = {VehicleProperty::WATCHDOG_ALIVE,
                                            VehicleProperty::WATCHDOG_TERMINATED_PROCESS,
                                            VehicleProperty::VHAL_HEARTBEAT};
    for (const auto& propId : propIds) {
        if (!isVhalPropertySupportedLocked(propId)) {
            mNotSupportedVhalProperties.insert(propId);
        }
    }
}

bool WatchdogProcessService::isVhalPropertySupportedLocked(VehicleProperty propId) {
    auto result = mVhalService->getPropConfigs({static_cast<int32_t>(propId)});
    return result.ok();
}

void WatchdogProcessService::subscribeToVhalHeartBeatLocked() {
    if (mNotSupportedVhalProperties.count(VehicleProperty::VHAL_HEARTBEAT) > 0) {
        ALOGW("VHAL doesn't support VHAL_HEARTBEAT. Checking VHAL health is disabled.");
        return;
    }

    mVhalHeartBeat = {
            .eventTime = 0,
            .value = 0,
    };

    std::vector<SubscribeOptions> options = {
            {.propId = static_cast<int32_t>(VehicleProperty::VHAL_HEARTBEAT), .areaIds = {}},
    };
    if (auto result =
                mVhalService->getSubscriptionClient(mPropertyChangeListener)->subscribe(options);
        !result.ok()) {
        ALOGW("Failed to subscribe to VHAL_HEARTBEAT. Checking VHAL health is disabled. '%s'",
              result.error().message().c_str());
        return;
    }
    std::chrono::nanoseconds intervalNs = mVhalHealthCheckWindowMs + kHealthCheckDelayMs;
    mHandlerLooper->sendMessageDelayed(intervalNs.count(), mMessageHandler,
                                       Message(MSG_VHAL_HEALTH_CHECK));
    // VHAL process identifier is required only when termiating the VHAL process. VHAL process is
    // terminated only when the VHAL is unhealthy. However, caching the process identifier as soon
    // as connecting to VHAL guarantees the correct PID is cached. Because the VHAL pid is queried
    // from the service manager, the caching should be performed outside the class level lock. So,
    // handle the caching in the handler thread after successfully subscribing to the VHAL_HEARTBEAT
    // property.
    mHandlerLooper->sendMessage(mMessageHandler, Message(MSG_CACHE_VHAL_PROCESS_IDENTIFIER));
    return;
}

bool WatchdogProcessService::cacheVhalProcessIdentifier() {
    pid_t pid = -1;
    if (Result<pid_t> hidlResult = queryHidlServiceManagerForVhalPid(); hidlResult.ok()) {
        pid = *hidlResult;
        ALOGI("Fetched HIDL VHAL PID %d", pid);
    } else if (Result<pid_t> aidlResult = queryAidlServiceManagerForVhalPid(); aidlResult.ok()) {
        pid = *aidlResult;
        ALOGI("Fetched AIDL VHAL PID %d", pid);
    } else {
        ALOGE("Failed to fetch VHAL pid:\n\t%s\n\t%s", hidlResult.error().message().c_str(),
              aidlResult.error().message().c_str());
        return false;
    }
    ProcessIdentifier processIdentifier;
    processIdentifier.pid = pid;
    processIdentifier.startTimeMillis = mGetStartTimeForPidFunc(pid);

    Mutex::Autolock lock(mMutex);
    mVhalProcessIdentifier = processIdentifier;
    return true;
}

int32_t WatchdogProcessService::getNewSessionId() {
    // Make sure that session id is always positive number.
    if (++mLastSessionId <= 0) {
        mLastSessionId = 1;
    }
    return mLastSessionId;
}

void WatchdogProcessService::updateVhalHeartBeat(int64_t value) {
    bool wrongHeartBeat;
    {
        Mutex::Autolock lock(mMutex);
        if (!mIsEnabled) {
            return;
        }
        wrongHeartBeat = value <= mVhalHeartBeat.value;
        mVhalHeartBeat.eventTime = uptimeMillis();
        mVhalHeartBeat.value = value;
    }
    if (wrongHeartBeat) {
        ALOGW("VHAL updated heart beat with a wrong value. Terminating VHAL...");
        terminateVhal();
        return;
    }
    std::chrono::nanoseconds intervalNs = mVhalHealthCheckWindowMs + kHealthCheckDelayMs;
    mHandlerLooper->sendMessageDelayed(intervalNs.count(), mMessageHandler,
                                       Message(MSG_VHAL_HEALTH_CHECK));
}

void WatchdogProcessService::checkVhalHealth() {
    int64_t lastEventTime;
    int64_t currentUptime = uptimeMillis();
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService == nullptr || !mIsEnabled) {
            return;
        }
        lastEventTime = mVhalHeartBeat.eventTime;
    }
    if (currentUptime > lastEventTime + mVhalHealthCheckWindowMs.count()) {
        ALOGW("VHAL failed to update heart beat within timeout. Terminating VHAL...");
        terminateVhal();
    }
}

void WatchdogProcessService::terminateVhal() {
    auto maybeDumpAndKillVhalProcess = [&]() -> bool {
        std::optional<ProcessIdentifier> processIdentifier;
        {
            Mutex::Autolock lock(mMutex);
            processIdentifier = mVhalProcessIdentifier;
        }
        if (!processIdentifier.has_value()) {
            return false;
        }
        dumpAndKillAllProcesses(std::vector<ProcessIdentifier>(1, *processIdentifier),
                                /*reportToVhal=*/false);
        return true;
    };
    if (maybeDumpAndKillVhalProcess()) {
        return;
    }
    if (!cacheVhalProcessIdentifier() || !maybeDumpAndKillVhalProcess()) {
        ALOGE("Failed to termitate VHAL: failed to fetch VHAL PID");
    }
}

std::chrono::nanoseconds WatchdogProcessService::getTimeoutDurationNs(
        const TimeoutLength& timeout) {
    // When a default timeout has been overridden by the |kPropertyClientCheckInterval| read-only
    // property override the timeout value for all timeout lengths.
    if (mOverriddenClientHealthCheckWindowNs.has_value()) {
        return mOverriddenClientHealthCheckWindowNs.value();
    }
    switch (timeout) {
        case TimeoutLength::TIMEOUT_CRITICAL:
            return 3s;  // 3s and no buffer time.
        case TimeoutLength::TIMEOUT_MODERATE:
            return 6s;  // 5s + 1s as buffer time.
        case TimeoutLength::TIMEOUT_NORMAL:
            return 12s;  // 10s + 2s as buffer time.
    }
}

std::string WatchdogProcessService::ClientInfo::toString() const {
    std::string buffer;
    StringAppendF(&buffer, "pid = %d, userId = %d, type = %s", pid, userId,
                  type == ClientType::Regular ? "regular" : "watchdog service");
    return buffer;
}

sp<IBinder> WatchdogProcessService::ClientInfo::getBinder() const {
    if (type == ClientType::Regular) {
        return BnCarWatchdogClient::asBinder(client);
    }
    return watchdogServiceBinder;
}

status_t WatchdogProcessService::ClientInfo::linkToDeath(
        const sp<IBinder::DeathRecipient>& recipient) const {
    if (type == ClientType::Regular) {
        return BnCarWatchdogClient::asBinder(client)->linkToDeath(recipient);
    }
    // WatchdogServiceHelper is the binder death recipient for watchdog service, ergo
    // skip this step.
    return OK;
}

status_t WatchdogProcessService::ClientInfo::unlinkToDeath(
        const wp<IBinder::DeathRecipient>& recipient) const {
    if (type == ClientType::Regular) {
        return BnCarWatchdogClient::asBinder(client)->unlinkToDeath(recipient);
    }
    // WatchdogServiceHelper is the binder death recipient for watchdog service, ergo
    // skip this step.
    return OK;
}

Status WatchdogProcessService::ClientInfo::checkIfAlive(TimeoutLength timeout) const {
    if (type == ClientType::Regular) {
        return client->checkIfAlive(sessionId, timeout);
    }
    return watchdogServiceHelper->checkIfAlive(watchdogServiceBinder, sessionId, timeout);
}

Status WatchdogProcessService::ClientInfo::prepareProcessTermination() const {
    if (type == ClientType::Regular) {
        return client->prepareProcessTermination();
    }
    return watchdogServiceHelper->prepareProcessTermination(watchdogServiceBinder);
}

WatchdogProcessService::BinderDeathRecipient::BinderDeathRecipient(
        const sp<WatchdogProcessService>& service) :
      mService(service) {}

void WatchdogProcessService::BinderDeathRecipient::binderDied(const wp<IBinder>& who) {
    mService->handleBinderDeath(who);
}

WatchdogProcessService::PropertyChangeListener::PropertyChangeListener(
        const sp<WatchdogProcessService>& service) :
      mService(service) {}

void WatchdogProcessService::PropertyChangeListener::onPropertyEvent(
        const std::vector<std::unique_ptr<IHalPropValue>>& propValues) {
    for (const auto& value : propValues) {
        if (value->getPropId() == static_cast<int32_t>(VehicleProperty::VHAL_HEARTBEAT)) {
            if (value->getInt64Values().size() < 1) {
                ALOGE("Invalid VHAL_HEARTBEAT value, empty value");
            } else {
                mService->updateVhalHeartBeat(value->getInt64Values()[0]);
            }
            break;
        }
    }
}

void WatchdogProcessService::PropertyChangeListener::onPropertySetError(
        const std::vector<HalPropError>& errors) {
    for (const auto& error : errors) {
        if (error.propId != static_cast<int32_t>(VehicleProperty::WATCHDOG_ALIVE) &&
            error.propId != static_cast<int32_t>(VehicleProperty::WATCHDOG_TERMINATED_PROCESS)) {
            continue;
        }
        ALOGE("failed to set VHAL property, prop ID: %d, status: %d", error.propId,
              static_cast<int32_t>(error.status));
    }
}

WatchdogProcessService::MessageHandlerImpl::MessageHandlerImpl(
        const sp<WatchdogProcessService>& service) :
      mService(service) {}

void WatchdogProcessService::MessageHandlerImpl::handleMessage(const Message& message) {
    switch (message.what) {
        case static_cast<int>(TimeoutLength::TIMEOUT_CRITICAL):
        case static_cast<int>(TimeoutLength::TIMEOUT_MODERATE):
        case static_cast<int>(TimeoutLength::TIMEOUT_NORMAL):
            mService->doHealthCheck(message.what);
            break;
        case MSG_VHAL_WATCHDOG_ALIVE:
            mService->reportWatchdogAliveToVhal();
            break;
        case MSG_VHAL_HEALTH_CHECK:
            mService->checkVhalHealth();
            break;
        case MSG_CACHE_VHAL_PROCESS_IDENTIFIER:
            mService->cacheVhalProcessIdentifier();
            break;
        default:
            ALOGW("Unknown message: %d", message.what);
    }
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

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

#define LOG_TAG "carpowerpolicyd"
#define DEBUG false  // STOPSHIP if true.

#include "CarPowerPolicyServer.h"

#include <aidl/android/hardware/automotive/vehicle/StatusCode.h>
#include <aidl/android/hardware/automotive/vehicle/SubscribeOptions.h>
#include <aidl/android/hardware/automotive/vehicle/VehicleApPowerStateReport.h>
#include <aidl/android/hardware/automotive/vehicle/VehicleProperty.h>
#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <android/binder_status.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <hidl/HidlTransportSupport.h>
#include <private/android_filesystem_config.h>
#include <utils/String8.h>
#include <utils/SystemClock.h>
#include <utils/Timers.h>

#include <inttypes.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicy;
using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicyFilter;
using ::aidl::android::frameworks::automotive::powerpolicy::ICarPowerPolicyChangeCallback;
using ::aidl::android::frameworks::automotive::powerpolicy::PowerComponent;
using ::aidl::android::frameworks::automotive::powerpolicy::internal::PolicyState;
using ::aidl::android::hardware::automotive::vehicle::StatusCode;
using ::aidl::android::hardware::automotive::vehicle::SubscribeOptions;
using ::aidl::android::hardware::automotive::vehicle::VehicleApPowerStateReport;
using ::aidl::android::hardware::automotive::vehicle::VehicleProperty;

using ::android::defaultServiceManager;
using ::android::IBinder;
using ::android::Looper;
using ::android::Mutex;
using ::android::status_t;
using ::android::String16;
using ::android::uptimeMillis;
using ::android::Vector;
using ::android::wp;
using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::android::base::WriteStringToFd;
using ::android::frameworks::automotive::vhal::HalPropError;
using ::android::frameworks::automotive::vhal::IHalPropValue;
using ::android::frameworks::automotive::vhal::ISubscriptionClient;
using ::android::frameworks::automotive::vhal::IVhalClient;
using ::android::hardware::hidl_vec;
using ::android::hardware::interfacesEqual;
using ::android::hardware::Return;
using ::android::hardware::automotive::vehicle::VhalResult;

using ::android::hidl::base::V1_0::IBase;
using ::ndk::ScopedAIBinder_DeathRecipient;
using ::ndk::ScopedAStatus;
using ::ndk::SharedRefBase;
using ::ndk::SpAIBinder;

namespace {

const int32_t MSG_CONNECT_TO_VHAL = 1;  // Message to request of connecting to VHAL.

const nsecs_t kConnectionRetryIntervalNs = 200000000;  // 200 milliseconds.
const int32_t kMaxConnectionRetry = 25;                // Retry up to 5 seconds.

constexpr const char kCarServiceInterface[] = "car_service";
constexpr const char kCarPowerPolicyServerInterface[] =
        "android.frameworks.automotive.powerpolicy.ICarPowerPolicyServer/default";
constexpr const char kCarPowerPolicySystemNotificationInterface[] =
        "android.frameworks.automotive.powerpolicy.internal.ICarPowerPolicySystemNotification/"
        "default";

std::vector<CallbackInfo>::const_iterator lookupPowerPolicyChangeCallback(
        const std::vector<CallbackInfo>& callbacks, const AIBinder* binder) {
    for (auto it = callbacks.begin(); it != callbacks.end(); it++) {
        if (it->binder.get() == binder) {
            return it;
        }
    }
    return callbacks.end();
}

ScopedAStatus checkSystemPermission() {
    if (IPCThreadState::self()->getCallingUid() != AID_SYSTEM) {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_SECURITY,
                                                                  "Calling process does not have "
                                                                  "proper privilege");
    }
    return ScopedAStatus::ok();
}

}  // namespace

std::shared_ptr<CarPowerPolicyServer> CarPowerPolicyServer::sCarPowerPolicyServer = nullptr;

PropertyChangeListener::PropertyChangeListener(CarPowerPolicyServer* service) : mService(service) {}

void PropertyChangeListener::onPropertyEvent(
        const std::vector<std::unique_ptr<IHalPropValue>>& values) {
    for (const auto& value : values) {
        const std::string stringValue = value->getStringValue();
        int32_t propId = value->getPropId();
        if (propId == static_cast<int32_t>(VehicleProperty::POWER_POLICY_GROUP_REQ)) {
            const auto& ret = mService->setPowerPolicyGroup(stringValue);
            if (!ret.ok()) {
                ALOGW("Failed to set power policy group(%s): %s", stringValue.c_str(),
                      ret.error().message().c_str());
            }
        } else if (propId == static_cast<int32_t>(VehicleProperty::POWER_POLICY_REQ)) {
            const auto& ret = mService->applyPowerPolicy(stringValue,
                                                         /*carServiceExpected=*/false,
                                                         /*force=*/false);
            if (!ret.ok()) {
                ALOGW("Failed to apply power policy(%s): %s", stringValue.c_str(),
                      ret.error().message().c_str());
            }
        }
    }
}

void PropertyChangeListener::onPropertySetError(
        [[maybe_unused]] const std::vector<HalPropError>& errors) {
    return;
}

MessageHandlerImpl::MessageHandlerImpl(CarPowerPolicyServer* service) : mService(service) {}

void MessageHandlerImpl::handleMessage(const Message& message) {
    switch (message.what) {
        case MSG_CONNECT_TO_VHAL:
            mService->connectToVhalHelper();
            break;
        default:
            ALOGW("Unknown message: %d", message.what);
    }
}

CarServiceNotificationHandler::CarServiceNotificationHandler(CarPowerPolicyServer* service) :
      mService(service) {}

void CarServiceNotificationHandler::terminate() {
    Mutex::Autolock lock(mMutex);
    mService = nullptr;
}

binder_status_t CarServiceNotificationHandler::dump(int fd, const char** args, uint32_t numArgs) {
    Mutex::Autolock lock(mMutex);
    if (mService == nullptr) {
        ALOGD("Skip dumping, CarPowerPolicyServer is ending");
        return STATUS_OK;
    }
    return mService->dump(fd, args, numArgs);
}

ScopedAStatus CarServiceNotificationHandler::notifyCarServiceReady(PolicyState* policyState) {
    Mutex::Autolock lock(mMutex);
    if (mService == nullptr) {
        ALOGD("Skip notifying CarServiceReady, CarPowerPolicyServer is ending");
        return ScopedAStatus::ok();
    }
    return mService->notifyCarServiceReady(policyState);
}

ScopedAStatus CarServiceNotificationHandler::notifyPowerPolicyChange(const std::string& policyId,
                                                                     bool force) {
    Mutex::Autolock lock(mMutex);
    if (mService == nullptr) {
        ALOGD("Skip notifying PowerPolicyChange, CarPowerPolicyServer is ending");
        return ScopedAStatus::ok();
    }
    return mService->notifyPowerPolicyChange(policyId, force);
}

ScopedAStatus CarServiceNotificationHandler::notifyPowerPolicyDefinition(
        const std::string& policyId, const std::vector<std::string>& enabledComponents,
        const std::vector<std::string>& disabledComponents) {
    Mutex::Autolock lock(mMutex);
    if (mService == nullptr) {
        ALOGD("Skip notifying PowerPolicyDefinition, CarPowerPolicyServer is ending");
        return ScopedAStatus::ok();
    }
    return mService->notifyPowerPolicyDefinition(policyId, enabledComponents, disabledComponents);
}

ISilentModeChangeHandler::~ISilentModeChangeHandler() {}

Result<std::shared_ptr<CarPowerPolicyServer>> CarPowerPolicyServer::startService(
        const sp<Looper>& looper) {
    if (sCarPowerPolicyServer != nullptr) {
        return Error(INVALID_OPERATION) << "Cannot start service more than once";
    }
    std::shared_ptr<CarPowerPolicyServer> server = SharedRefBase::make<CarPowerPolicyServer>();
    const auto& ret = server->init(looper);
    if (!ret.ok()) {
        return Error(ret.error().code())
                << "Failed to start car power policy server: " << ret.error();
    }
    sCarPowerPolicyServer = server;

    return sCarPowerPolicyServer;
}

void CarPowerPolicyServer::terminateService() {
    if (sCarPowerPolicyServer != nullptr) {
        sCarPowerPolicyServer->terminate();
        sCarPowerPolicyServer = nullptr;
    }
}

CarPowerPolicyServer::CarPowerPolicyServer() :
      mSilentModeHandler(this),
      mIsPowerPolicyLocked(false),
      mIsCarServiceInOperation(false),
      mIsFirstConnectionToVhal(true) {
    mMessageHandler = new MessageHandlerImpl(this);
    mDeathRecipient = ScopedAIBinder_DeathRecipient(
            AIBinder_DeathRecipient_new(&CarPowerPolicyServer::onBinderDied));
    mPropertyChangeListener = std::make_unique<PropertyChangeListener>(this);
    mLinkUnlinkImpl = std::make_unique<AIBinderLinkUnlinkImpl>();
}

// For test-only.
void CarPowerPolicyServer::setLinkUnlinkImpl(
        std::unique_ptr<CarPowerPolicyServer::LinkUnlinkImpl> impl) {
    mLinkUnlinkImpl = std::move(impl);
}

ScopedAStatus CarPowerPolicyServer::getCurrentPowerPolicy(CarPowerPolicy* aidlReturn) {
    Mutex::Autolock lock(mMutex);
    if (!isPowerPolicyAppliedLocked()) {
        return ScopedAStatus::
                fromServiceSpecificErrorWithMessage(EX_ILLEGAL_STATE,
                                                    "The current power policy is not set");
    }
    *aidlReturn = *mCurrentPowerPolicyMeta.powerPolicy;
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::getPowerComponentState(PowerComponent componentId,
                                                           bool* aidlReturn) {
    const auto& ret = mComponentHandler.getPowerComponentState(componentId);
    if (!ret.ok()) {
        std::string errorMsg = ret.error().message();
        ALOGW("getPowerComponentState(%s) failed: %s", toString(componentId).c_str(),
              errorMsg.c_str());
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT,
                                                                  errorMsg.c_str());
    }
    *aidlReturn = *ret;
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::registerPowerPolicyChangeCallback(
        const std::shared_ptr<ICarPowerPolicyChangeCallback>& callback,
        const CarPowerPolicyFilter& filter) {
    Mutex::Autolock lock(mMutex);
    pid_t callingPid = IPCThreadState::self()->getCallingPid();
    uid_t callingUid = IPCThreadState::self()->getCallingUid();
    SpAIBinder binder = callback->asBinder();
    AIBinder* clientId = binder.get();
    if (isRegisteredLocked(clientId)) {
        std::string errorStr = StringPrintf("The callback(pid: %d, uid: %d) is already registered.",
                                            callingPid, callingUid);
        const char* errorCause = errorStr.c_str();
        ALOGW("Cannot register a callback: %s", errorCause);
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT, errorCause);
    }

    std::unique_ptr<OnBinderDiedContext> context = std::make_unique<OnBinderDiedContext>(
            OnBinderDiedContext{.server = this, .clientId = clientId});
    binder_status_t status = mLinkUnlinkImpl->linkToDeath(clientId, mDeathRecipient.get(),
                                                          static_cast<void*>(context.get()));
    if (status != STATUS_OK) {
        std::string errorStr = StringPrintf("The given callback(pid: %d, uid: %d) is dead",
                                            callingPid, callingUid);
        const char* errorCause = errorStr.c_str();
        ALOGW("Cannot register a callback: %s", errorCause);
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_STATE, errorCause);
    }
    // Insert into a map to keep the context object alive.
    mOnBinderDiedContexts[clientId] = std::move(context);
    mPolicyChangeCallbacks.emplace_back(binder, filter, callingPid);

    if (DEBUG) {
        ALOGD("Power policy change callback(pid: %d, filter: %s) is registered", callingPid,
              toString(filter.components).c_str());
    }
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::unregisterPowerPolicyChangeCallback(
        const std::shared_ptr<ICarPowerPolicyChangeCallback>& callback) {
    Mutex::Autolock lock(mMutex);
    pid_t callingPid = IPCThreadState::self()->getCallingPid();
    uid_t callingUid = IPCThreadState::self()->getCallingUid();
    AIBinder* clientId = callback->asBinder().get();
    auto it = lookupPowerPolicyChangeCallback(mPolicyChangeCallbacks, clientId);
    if (it == mPolicyChangeCallbacks.end()) {
        std::string errorStr =
                StringPrintf("The callback(pid: %d, uid: %d) has not been registered", callingPid,
                             callingUid);
        const char* errorCause = errorStr.c_str();
        ALOGW("Cannot unregister a callback: %s", errorCause);
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT, errorCause);
    }
    if (mOnBinderDiedContexts.find(clientId) != mOnBinderDiedContexts.end()) {
        // We don't set a callback for unlinkToDeath but need to call unlinkToDeath to clean up the
        // registered death recipient.
        mLinkUnlinkImpl->unlinkToDeath(clientId, mDeathRecipient.get(),
                                       static_cast<void*>(mOnBinderDiedContexts[clientId].get()));
        mOnBinderDiedContexts.erase(clientId);
    }
    mPolicyChangeCallbacks.erase(it);
    if (DEBUG) {
        ALOGD("Power policy change callback(pid: %d, uid: %d) is unregistered", callingPid,
              callingUid);
    }
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::notifyCarServiceReady(PolicyState* policyState) {
    ScopedAStatus status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    mSilentModeHandler.stopMonitoringSilentModeHwState(/*shouldWaitThread=*/false);
    Mutex::Autolock lock(mMutex);
    policyState->policyId =
            isPowerPolicyAppliedLocked() ? mCurrentPowerPolicyMeta.powerPolicy->policyId : "";
    policyState->policyGroupId = mCurrentPolicyGroupId;
    mIsCarServiceInOperation = true;
    ALOGI("CarService is now responsible for power policy management");
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::notifyPowerPolicyChange(const std::string& policyId,
                                                            bool force) {
    ScopedAStatus status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    const auto& ret = applyPowerPolicy(policyId, /*carServiceExpected=*/true, force);
    if (!ret.ok()) {
        return ScopedAStatus::
                fromServiceSpecificErrorWithMessage(EX_ILLEGAL_STATE,
                                                    StringPrintf("Failed to notify power policy "
                                                                 "change: %s",
                                                                 ret.error().message().c_str())
                                                            .c_str());
    }
    ALOGD("Policy change(%s) is notified by CarService", policyId.c_str());
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::notifyPowerPolicyDefinition(
        const std::string& policyId, const std::vector<std::string>& enabledComponents,
        const std::vector<std::string>& disabledComponents) {
    ScopedAStatus status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    const auto& ret =
            mPolicyManager.definePowerPolicy(policyId, enabledComponents, disabledComponents);
    if (!ret.ok()) {
        return ScopedAStatus::
                fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT,
                                                    StringPrintf("Failed to notify power policy "
                                                                 "definition: %s",
                                                                 ret.error().message().c_str())
                                                            .c_str());
    }
    return ScopedAStatus::ok();
}

status_t CarPowerPolicyServer::dump(int fd, const char** args, uint32_t numArgs) {
    Vector<String16> argsV;
    for (size_t i = 0; i < numArgs; i++) {
        argsV.push(String16(args[i]));
    }

    {
        Mutex::Autolock lock(mMutex);
        const char* indent = "  ";
        const char* doubleIndent = "    ";
        WriteStringToFd("CAR POWER POLICY DAEMON\n", fd);
        WriteStringToFd(StringPrintf("%sCarService is in operation: %s\n", indent,
                                     mIsCarServiceInOperation ? "true" : "false"),
                        fd);
        WriteStringToFd(StringPrintf("%sConnection to VHAL: %s\n", indent,
                                     mVhalService.get() ? "connected" : "disconnected"),
                        fd);
        WriteStringToFd(StringPrintf("%sCurrent power policy: %s\n", indent,
                                     isPowerPolicyAppliedLocked()
                                             ? mCurrentPowerPolicyMeta.powerPolicy->policyId.c_str()
                                             : "not set"),
                        fd);
        WriteStringToFd(StringPrintf("%sLast uptime of applying power policy: %" PRId64 "ms\n",
                                     indent, mLastApplyPowerPolicyUptimeMs.value_or(-1)),
                        fd);
        WriteStringToFd(StringPrintf("%sPending power policy ID: %s\n", indent,
                                     mPendingPowerPolicyId.c_str()),
                        fd);
        WriteStringToFd(StringPrintf("%sCurrent power policy group ID: %s\n", indent,
                                     mCurrentPolicyGroupId.empty() ? "not set"
                                                                   : mCurrentPolicyGroupId.c_str()),
                        fd);
        WriteStringToFd(StringPrintf("%sLast uptime of setting default power policy group: "
                                     "%" PRId64 "ms\n",
                                     indent, mLastSetDefaultPowerPolicyGroupUptimeMs.value_or(-1)),
                        fd);
        WriteStringToFd(StringPrintf("%sPolicy change callbacks:%s\n", indent,
                                     mPolicyChangeCallbacks.size() ? "" : " none"),
                        fd);
        for (auto& callback : mPolicyChangeCallbacks) {
            WriteStringToFd(StringPrintf("%s- %s\n", doubleIndent,
                                         callbackToString(callback).c_str()),
                            fd);
        }
    }
    if (const auto& ret = mPolicyManager.dump(fd, argsV); !ret.ok()) {
        ALOGW("Failed to dump power policy handler: %s", ret.error().message().c_str());
        return ret.error().code();
    }
    if (const auto& ret = mComponentHandler.dump(fd); !ret.ok()) {
        ALOGW("Failed to dump power component handler: %s", ret.error().message().c_str());
        return ret.error().code();
    }
    if (const auto& ret = mSilentModeHandler.dump(fd, argsV); !ret.ok()) {
        ALOGW("Failed to dump Silent Mode handler: %s", ret.error().message().c_str());
        return ret.error().code();
    }
    return OK;
}

Result<void> CarPowerPolicyServer::init(const sp<Looper>& looper) {
    AIBinder* binderCarService = AServiceManager_checkService(kCarServiceInterface);

    Mutex::Autolock lock(mMutex);
    // Before initializing power policy daemon, we need to update mIsCarServiceInOperation
    // according to whether CPMS is running.
    mIsCarServiceInOperation = binderCarService != nullptr;

    mHandlerLooper = looper;
    mPolicyManager.init();
    mComponentHandler.init();
    mSilentModeHandler.init();
    mCarServiceNotificationHandler = SharedRefBase::make<CarServiceNotificationHandler>(this);

    binder_exception_t err =
            AServiceManager_addService(this->asBinder().get(), kCarPowerPolicyServerInterface);
    if (err != EX_NONE) {
        return Error(err) << "Failed to add carpowerpolicyd to ServiceManager";
    }
    err = AServiceManager_addService(mCarServiceNotificationHandler->asBinder().get(),
                                     kCarPowerPolicySystemNotificationInterface);
    if (err != EX_NONE) {
        return Error(err) << "Failed to add car power policy system notification to ServiceManager";
    }

    connectToVhal();
    return {};
}

void CarPowerPolicyServer::terminate() {
    Mutex::Autolock lock(mMutex);
    mPolicyChangeCallbacks.clear();
    if (mVhalService != nullptr) {
        mSubscriptionClient->unsubscribe(
                {static_cast<int32_t>(VehicleProperty::POWER_POLICY_REQ),
                 static_cast<int32_t>(VehicleProperty::POWER_POLICY_GROUP_REQ)});
    }

    if (mCarServiceNotificationHandler != nullptr) {
        mCarServiceNotificationHandler->terminate();
        mCarServiceNotificationHandler = nullptr;
    }

    // Delete the deathRecipient so that all binders would be unlinked.
    mDeathRecipient = ScopedAIBinder_DeathRecipient();
    mSilentModeHandler.release();
    // Remove the messages so that mMessageHandler would no longer be used.
    mHandlerLooper->removeMessages(mMessageHandler);
}

void CarPowerPolicyServer::onBinderDied(void* cookie) {
    OnBinderDiedContext* context = reinterpret_cast<OnBinderDiedContext*>(cookie);
    context->server->handleBinderDeath(context->clientId);
}

void CarPowerPolicyServer::handleBinderDeath(const AIBinder* clientId) {
    Mutex::Autolock lock(mMutex);
    auto it = lookupPowerPolicyChangeCallback(mPolicyChangeCallbacks, clientId);
    if (it != mPolicyChangeCallbacks.end()) {
        ALOGW("Power policy callback(pid: %d) died", it->pid);
        mPolicyChangeCallbacks.erase(it);
    }
    mOnBinderDiedContexts.erase(clientId);
}

void CarPowerPolicyServer::handleVhalDeath() {
    {
        Mutex::Autolock lock(mMutex);
        ALOGW("VHAL has died.");
        mVhalService = nullptr;
    }
    connectToVhal();
}

Result<void> CarPowerPolicyServer::applyPowerPolicy(const std::string& policyId,
                                                    const bool carServiceInOperation,
                                                    const bool force) {
    auto policyMeta = mPolicyManager.getPowerPolicy(policyId);
    if (!policyMeta.ok()) {
        return Error() << "Failed to apply power policy: " << policyMeta.error().message();
    }

    std::vector<CallbackInfo> clients;
    if (Mutex::Autolock lock(mMutex); mIsCarServiceInOperation != carServiceInOperation) {
        return Error() << (mIsCarServiceInOperation
                                   ? "After CarService starts serving, power policy cannot be "
                                     "managed in car power policy daemon"
                                   : "Before CarService starts serving, power policy cannot be "
                                     "applied from CarService");
    } else {
        if (mVhalService == nullptr) {
            ALOGI("%s is queued and will be applied after VHAL gets ready", policyId.c_str());
            mPendingPowerPolicyId = policyId;
            return {};
        }
        bool isPolicyApplied = isPowerPolicyAppliedLocked();
        if (isPolicyApplied && mCurrentPowerPolicyMeta.powerPolicy->policyId == policyId) {
            ALOGI("Applying policy skipped: the given policy(ID: %s) is the current policy",
                  policyId.c_str());
            return {};
        }
        if (policyMeta->isPreemptive) {
            if (isPolicyApplied && !mCurrentPowerPolicyMeta.isPreemptive) {
                mPendingPowerPolicyId = mCurrentPowerPolicyMeta.powerPolicy->policyId;
            }
            mIsPowerPolicyLocked = true;
        } else {
            if (force) {
                mPendingPowerPolicyId.clear();
                mIsPowerPolicyLocked = false;
            } else if (mIsPowerPolicyLocked) {
                ALOGI("%s is queued and will be applied after power policy get unlocked",
                      policyId.c_str());
                mPendingPowerPolicyId = policyId;
                return {};
            }
        }
        mCurrentPowerPolicyMeta = *policyMeta;
        clients = mPolicyChangeCallbacks;
        mLastApplyPowerPolicyUptimeMs = uptimeMillis();
    }
    CarPowerPolicyPtr policy = policyMeta->powerPolicy;
    mComponentHandler.applyPowerPolicy(policy);
    if (const auto& ret = notifyVhalNewPowerPolicy(policyId); !ret.ok()) {
        ALOGW("Failed to tell VHAL the new power policy(%s): %s", policyId.c_str(),
              ret.error().message().c_str());
    }
    for (auto client : clients) {
        ICarPowerPolicyChangeCallback::fromBinder(client.binder)->onPolicyChanged(*policy);
    }
    ALOGI("The current power policy is %s", policyId.c_str());
    return {};
}

Result<void> CarPowerPolicyServer::setPowerPolicyGroup(const std::string& groupId) {
    if (!mPolicyManager.isPowerPolicyGroupAvailable(groupId)) {
        return Error() << StringPrintf("Power policy group(%s) is not available", groupId.c_str());
    }
    Mutex::Autolock lock(mMutex);
    if (mIsCarServiceInOperation) {
        return Error() << "After CarService starts serving, power policy group cannot be set in "
                          "car power policy daemon";
    }
    mCurrentPolicyGroupId = groupId;
    ALOGI("The current power policy group is |%s|", groupId.c_str());
    return {};
}

void CarPowerPolicyServer::notifySilentModeChange(const bool isSilent) {
    std::string pendingPowerPolicyId;
    if (Mutex::Autolock lock(mMutex); mIsCarServiceInOperation) {
        return;
    } else {
        pendingPowerPolicyId = mPendingPowerPolicyId;
    }
    ALOGI("Silent Mode is set to %s", isSilent ? "silent" : "non-silent");
    Result<void> ret;
    if (isSilent) {
        ret = applyPowerPolicy(kSystemPolicyIdNoUserInteraction,
                               /*carServiceExpected=*/false, /*force=*/false);
    } else {
        ret = applyPowerPolicy(pendingPowerPolicyId,
                               /*carServiceExpected=*/false, /*force=*/true);
    }
    if (!ret.ok()) {
        ALOGW("Failed to apply power policy: %s", ret.error().message().c_str());
    }
}

bool CarPowerPolicyServer::isRegisteredLocked(const AIBinder* binder) {
    return lookupPowerPolicyChangeCallback(mPolicyChangeCallbacks, binder) !=
            mPolicyChangeCallbacks.end();
}

// This method ensures that the attempt to connect to VHAL occurs in the main thread.
void CarPowerPolicyServer::connectToVhal() {
    mRemainingConnectionRetryCount = kMaxConnectionRetry;
    mHandlerLooper->sendMessage(mMessageHandler, MSG_CONNECT_TO_VHAL);
}

// connectToVhalHelper is always executed in the main thread.
void CarPowerPolicyServer::connectToVhalHelper() {
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService != nullptr) {
            return;
        }
    }
    std::shared_ptr<IVhalClient> vhalService = IVhalClient::tryCreate();
    if (vhalService == nullptr) {
        ALOGW("Failed to connect to VHAL. Retrying in %" PRId64 " ms.",
              nanoseconds_to_milliseconds(kConnectionRetryIntervalNs));
        mRemainingConnectionRetryCount--;
        if (mRemainingConnectionRetryCount <= 0) {
            ALOGE("Failed to connect to VHAL after %d attempt%s. Gave up.", kMaxConnectionRetry,
                  kMaxConnectionRetry > 1 ? "s" : "");
            return;
        }
        mHandlerLooper->sendMessageDelayed(kConnectionRetryIntervalNs, mMessageHandler,
                                           MSG_CONNECT_TO_VHAL);
        return;
    }
    vhalService->addOnBinderDiedCallback(
            std::make_shared<IVhalClient::OnBinderDiedCallbackFunc>([this] { handleVhalDeath(); }));
    std::string currentPolicyId;
    {
        Mutex::Autolock lock(mMutex);
        mVhalService = vhalService;
        mSubscriptionClient = mVhalService->getSubscriptionClient(mPropertyChangeListener);
        if (isPowerPolicyAppliedLocked()) {
            currentPolicyId = mCurrentPowerPolicyMeta.powerPolicy->policyId;
        }
    }
    /*
     * When VHAL is first executed, a normal power management goes on. When VHAL is restarted due to
     * some reasons, the current policy is notified to VHAL.
     */
    if (mIsFirstConnectionToVhal) {
        applyInitialPowerPolicy();
        mIsFirstConnectionToVhal = false;
    } else if (!currentPolicyId.empty()) {
        notifyVhalNewPowerPolicy(currentPolicyId);
    }
    subscribeToVhal();
    ALOGI("Connected to VHAL");
    return;
}

void CarPowerPolicyServer::applyInitialPowerPolicy() {
    std::string policyId;
    std::string currentPolicyGroupId;
    CarPowerPolicyPtr powerPolicy;
    {
        Mutex::Autolock lock(mMutex);
        if (mIsCarServiceInOperation) {
            ALOGI("Skipping initial power policy application because CarService is running");
            return;
        }
        policyId = mPendingPowerPolicyId;
        currentPolicyGroupId = mCurrentPolicyGroupId;
    }
    if (policyId.empty()) {
        if (auto policy = mPolicyManager.getDefaultPowerPolicyForState(currentPolicyGroupId,
                                                                       VehicleApPowerStateReport::
                                                                               WAIT_FOR_VHAL);
            policy.ok()) {
            policyId = (*policy)->policyId;
        } else {
            policyId = kSystemPolicyIdInitialOn;
        }
    }
    if (const auto& ret = applyPowerPolicy(policyId, /*carServiceExpected=*/false, /*force=*/false);
        !ret.ok()) {
        ALOGW("Cannot apply the initial power policy(%s): %s", policyId.c_str(),
              ret.error().message().c_str());
        return;
    }
    ALOGD("Policy(%s) is applied as the initial one", policyId.c_str());
}

void CarPowerPolicyServer::subscribeToVhal() {
    subscribeToProperty(static_cast<int32_t>(VehicleProperty::POWER_POLICY_REQ),
                        [this](const IHalPropValue& value) {
                            std::string stringValue = value.getStringValue();
                            if (stringValue.size() > 0) {
                                const auto& ret = applyPowerPolicy(stringValue,
                                                                   /*carServiceExpected=*/false,
                                                                   /*force=*/false);
                                if (!ret.ok()) {
                                    ALOGW("Failed to apply power policy(%s): %s",
                                          stringValue.c_str(), ret.error().message().c_str());
                                }
                            }
                        });
    subscribeToProperty(static_cast<int32_t>(VehicleProperty::POWER_POLICY_GROUP_REQ),
                        [this](const IHalPropValue& value) {
                            std::string stringValue = value.getStringValue();
                            if (stringValue.size() > 0) {
                                const auto& ret = setPowerPolicyGroup(stringValue);
                                if (ret.ok()) {
                                    Mutex::Autolock lock(mMutex);
                                    mLastSetDefaultPowerPolicyGroupUptimeMs = value.getTimestamp();
                                } else {
                                    ALOGW("Failed to set power policy group(%s): %s",
                                          stringValue.c_str(), ret.error().message().c_str());
                                }
                            }
                        });
}

void CarPowerPolicyServer::subscribeToProperty(
        int32_t prop, std::function<void(const IHalPropValue&)> processor) {
    if (!isPropertySupported(prop)) {
        ALOGW("Vehicle property(%d) is not supported by VHAL.", prop);
        return;
    }
    std::shared_ptr<IVhalClient> vhalService;
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService == nullptr) {
            ALOGW("Failed to subscribe to property(%d): VHAL is not ready", prop);
            return;
        }
        vhalService = mVhalService;
    }

    VhalResult<std::unique_ptr<IHalPropValue>> result =
            vhalService->getValueSync(*vhalService->createHalPropValue(prop));

    if (!result.ok()) {
        ALOGW("Failed to get vehicle property(%d) value, error: %s.", prop,
              result.error().message().c_str());
        return;
    }
    processor(*result.value());
    std::vector<SubscribeOptions> options = {
            {.propId = prop, .areaIds = {}},
    };

    if (auto result = mSubscriptionClient->subscribe(options); !result.ok()) {
        ALOGW("Failed to subscribe to vehicle property(%d), error: %s", prop,
              result.error().message().c_str());
    }
}

Result<void> CarPowerPolicyServer::notifyVhalNewPowerPolicy(const std::string& policyId) {
    int32_t prop = static_cast<int32_t>(VehicleProperty::CURRENT_POWER_POLICY);
    if (!isPropertySupported(prop)) {
        return Error() << StringPrintf("Vehicle property(%d) is not supported by VHAL.", prop);
    }
    std::shared_ptr<IVhalClient> vhalService;
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService == nullptr) {
            return Error() << "VHAL is not ready";
        }
        vhalService = mVhalService;
    }
    std::unique_ptr<IHalPropValue> propValue = vhalService->createHalPropValue(prop);
    propValue->setStringValue(policyId);

    VhalResult<void> result = vhalService->setValueSync(*propValue);
    if (!result.ok()) {
        return Error() << "Failed to set CURRENT_POWER_POLICY property";
    }
    ALOGD("Policy(%s) is notified to VHAL", policyId.c_str());
    return {};
}

bool CarPowerPolicyServer::isPropertySupported(const int32_t prop) {
    if (mSupportedProperties.count(prop) > 0) {
        return mSupportedProperties[prop];
    }
    StatusCode status;
    hidl_vec<int32_t> props = {prop};
    std::shared_ptr<IVhalClient> vhalService;
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService == nullptr) {
            ALOGW("Failed to check if property(%d) is supported: VHAL is not ready", prop);
            return false;
        }
        vhalService = mVhalService;
    }
    auto result = vhalService->getPropConfigs(props);
    mSupportedProperties[prop] = result.ok();
    return mSupportedProperties[prop];
}

bool CarPowerPolicyServer::isPowerPolicyAppliedLocked() const {
    return mCurrentPowerPolicyMeta.powerPolicy != nullptr;
}

std::string CarPowerPolicyServer::callbackToString(const CallbackInfo& callback) {
    const std::vector<PowerComponent>& components = callback.filter.components;
    return StringPrintf("callback(pid %d, filter: %s)", callback.pid, toString(components).c_str());
}

std::vector<CallbackInfo> CarPowerPolicyServer::getPolicyChangeCallbacks() {
    Mutex::Autolock lock(mMutex);
    return mPolicyChangeCallbacks;
}

size_t CarPowerPolicyServer::countOnBinderDiedContexts() {
    Mutex::Autolock lock(mMutex);
    return mOnBinderDiedContexts.size();
}

binder_status_t CarPowerPolicyServer::AIBinderLinkUnlinkImpl::linkToDeath(
        AIBinder* binder, AIBinder_DeathRecipient* recipient, void* cookie) {
    return AIBinder_linkToDeath(binder, recipient, cookie);
}

binder_status_t CarPowerPolicyServer::AIBinderLinkUnlinkImpl::unlinkToDeath(
        AIBinder* binder, AIBinder_DeathRecipient* recipient, void* cookie) {
    return AIBinder_unlinkToDeath(binder, recipient, cookie);
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

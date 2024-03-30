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

#include "CarPowerPolicyServer.h"

#include <aidl/android/frameworks/automotive/powerpolicy/BnCarPowerPolicyChangeCallback.h>
#include <aidl/android/frameworks/automotive/powerpolicy/CarPowerPolicy.h>
#include <aidl/android/frameworks/automotive/powerpolicy/CarPowerPolicyFilter.h>
#include <aidl/android/frameworks/automotive/powerpolicy/ICarPowerPolicyChangeCallback.h>
#include <aidl/android/frameworks/automotive/powerpolicy/ICarPowerPolicyServer.h>
#include <aidl/android/frameworks/automotive/powerpolicy/PowerComponent.h>
#include <android-base/thread_annotations.h>
#include <gmock/gmock.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>
#include <utils/StrongPointer.h>

#include <unordered_set>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using android::IBinder;

using ::aidl::android::frameworks::automotive::powerpolicy::BnCarPowerPolicyChangeCallback;
using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicy;
using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicyFilter;
using ::aidl::android::frameworks::automotive::powerpolicy::ICarPowerPolicyChangeCallback;
using ::aidl::android::frameworks::automotive::powerpolicy::ICarPowerPolicyServer;
using ::aidl::android::frameworks::automotive::powerpolicy::PowerComponent;

using ::ndk::ScopedAStatus;
using ::ndk::SpAIBinder;

using ::testing::_;
using ::testing::Invoke;
using ::testing::Return;

namespace {

class MockPowerPolicyChangeCallback : public BnCarPowerPolicyChangeCallback {
public:
    ScopedAStatus onPolicyChanged(const CarPowerPolicy& /*policy*/) override {
        return ScopedAStatus::ok();
    }
};

}  // namespace

namespace internal {

class CarPowerPolicyServerPeer : public RefBase {
public:
    CarPowerPolicyServerPeer() {
        std::unique_ptr<MockLinkUnlinkImpl> impl = std::make_unique<MockLinkUnlinkImpl>();
        // We know this would be alive as long as server is alive.
        mLinkUnlinkImpl = impl.get();
        mServer = ::ndk::SharedRefBase::make<CarPowerPolicyServer>();
        mServer->setLinkUnlinkImpl(std::move(impl));
        mBinder = mServer->asBinder();
        mServerProxy = ICarPowerPolicyServer::fromBinder(mBinder);
    }

    ScopedAStatus getCurrentPowerPolicy(CarPowerPolicy* aidlReturn) {
        return mServerProxy->getCurrentPowerPolicy(aidlReturn);
    }

    ScopedAStatus registerPowerPolicyChangeCallback(
            const std::shared_ptr<ICarPowerPolicyChangeCallback>& callback,
            const CarPowerPolicyFilter& filter) {
        return mServerProxy->registerPowerPolicyChangeCallback(callback, filter);
    }

    ScopedAStatus unregisterPowerPolicyChangeCallback(
            const std::shared_ptr<ICarPowerPolicyChangeCallback>& callback) {
        return mServerProxy->unregisterPowerPolicyChangeCallback(callback);
    }

    void onBinderDied(void* cookie) { mServer->onBinderDied(cookie); }

    std::vector<CallbackInfo> getPolicyChangeCallbacks() {
        return mServer->getPolicyChangeCallbacks();
    }

    size_t countOnBinderDiedContexts() { return mServer->countOnBinderDiedContexts(); }

    std::unordered_set<void*> getCookies() { return mLinkUnlinkImpl->getCookies(); }

    void expectLinkToDeathStatus(AIBinder* binder, status_t linkToDeathResult) {
        mLinkUnlinkImpl->expectLinkToDeathStatus(binder, linkToDeathResult);
    }

private:
    class MockLinkUnlinkImpl : public CarPowerPolicyServer::LinkUnlinkImpl {
    public:
        MOCK_METHOD(binder_status_t, linkToDeath, (AIBinder*, AIBinder_DeathRecipient*, void*),
                    (override));
        MOCK_METHOD(binder_status_t, unlinkToDeath, (AIBinder*, AIBinder_DeathRecipient*, void*),
                    (override));

        void expectLinkToDeathStatus(AIBinder* binder, binder_status_t linkToDeathResult) {
            EXPECT_CALL(*this, linkToDeath(binder, _, _))
                    .WillRepeatedly(
                            Invoke([this, linkToDeathResult](AIBinder*, AIBinder_DeathRecipient*,
                                                             void* cookie) {
                                Mutex::Autolock lock(mMutex);
                                mCookies.insert(cookie);
                                return linkToDeathResult;
                            }));
            EXPECT_CALL(*this, unlinkToDeath(binder, _, _))
                    .WillRepeatedly(
                            Invoke([this](AIBinder*, AIBinder_DeathRecipient*, void* cookie) {
                                Mutex::Autolock lock(mMutex);
                                mCookies.erase(cookie);
                                return STATUS_OK;
                            }));
        }

        std::unordered_set<void*> getCookies() {
            Mutex::Autolock lock(mMutex);
            return mCookies;
        }

    private:
        android::Mutex mMutex;
        std::unordered_set<void*> mCookies GUARDED_BY(mMutex);
    };

    MockLinkUnlinkImpl* mLinkUnlinkImpl;
    std::shared_ptr<CarPowerPolicyServer> mServer;
    std::shared_ptr<ICarPowerPolicyServer> mServerProxy;
    SpAIBinder mBinder;
};

}  // namespace internal

class CarPowerPolicyServerTest : public ::testing::Test {
public:
    std::shared_ptr<ICarPowerPolicyChangeCallback> getPowerPolicyChangeCallback() {
        std::shared_ptr<MockPowerPolicyChangeCallback> callback =
                ndk::SharedRefBase::make<MockPowerPolicyChangeCallback>();
        return ICarPowerPolicyChangeCallback::fromBinder(callback->asBinder());
    }
};

TEST_F(CarPowerPolicyServerTest, TestRegisterCallback) {
    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<ICarPowerPolicyChangeCallback> callbackOne = getPowerPolicyChangeCallback();
    server->expectLinkToDeathStatus(callbackOne->asBinder().get(), STATUS_OK);

    CarPowerPolicyFilter filter;
    ScopedAStatus status = server->registerPowerPolicyChangeCallback(callbackOne, filter);
    ASSERT_TRUE(status.isOk()) << status.getMessage();
    status = server->registerPowerPolicyChangeCallback(callbackOne, filter);
    ASSERT_FALSE(status.isOk()) << "Duplicated registration is not allowed";
    filter.components = {PowerComponent::BLUETOOTH, PowerComponent::AUDIO};
    status = server->registerPowerPolicyChangeCallback(callbackOne, filter);
    ASSERT_FALSE(status.isOk()) << "Duplicated registration is not allowed";

    std::shared_ptr<ICarPowerPolicyChangeCallback> callbackTwo = getPowerPolicyChangeCallback();
    server->expectLinkToDeathStatus(callbackTwo->asBinder().get(), STATUS_OK);

    status = server->registerPowerPolicyChangeCallback(callbackTwo, filter);
    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(CarPowerPolicyServerTest, TestRegisterCallback_BinderDied) {
    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<ICarPowerPolicyChangeCallback> callback = getPowerPolicyChangeCallback();
    server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_DEAD_OBJECT);
    CarPowerPolicyFilter filter;

    ASSERT_FALSE(server->registerPowerPolicyChangeCallback(callback, filter).isOk())
            << "When linkToDeath fails, registerPowerPolicyChangeCallback should return an error";
}

TEST_F(CarPowerPolicyServerTest, TestOnBinderDied) {
    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<ICarPowerPolicyChangeCallback> callbackOne = getPowerPolicyChangeCallback();
    server->expectLinkToDeathStatus(callbackOne->asBinder().get(), STATUS_OK);

    CarPowerPolicyFilter filter;
    ScopedAStatus status = server->registerPowerPolicyChangeCallback(callbackOne, filter);
    ASSERT_TRUE(status.isOk()) << status.getMessage();
    ASSERT_EQ(server->getPolicyChangeCallbacks().size(), static_cast<size_t>(1));
    ASSERT_EQ(server->countOnBinderDiedContexts(), static_cast<size_t>(1));
    ASSERT_EQ(server->getCookies().size(), static_cast<size_t>(1));

    void* cookie = *(server->getCookies().begin());
    server->onBinderDied(cookie);

    ASSERT_TRUE(server->getPolicyChangeCallbacks().empty());

    ASSERT_EQ(server->countOnBinderDiedContexts(), static_cast<size_t>(0));
}

TEST_F(CarPowerPolicyServerTest, TestUnregisterCallback) {
    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<ICarPowerPolicyChangeCallback> callback = getPowerPolicyChangeCallback();
    server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_OK);
    CarPowerPolicyFilter filter;

    server->registerPowerPolicyChangeCallback(callback, filter);
    ScopedAStatus status = server->unregisterPowerPolicyChangeCallback(callback);
    ASSERT_TRUE(status.isOk()) << status.getMessage();
    ASSERT_FALSE(server->unregisterPowerPolicyChangeCallback(callback).isOk())
            << "Unregistering an unregistered powerpolicy change callback should return an error";
}

TEST_F(CarPowerPolicyServerTest, TestGetCurrentPowerPolicy) {
    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    CarPowerPolicy currentPolicy;

    ScopedAStatus status = server->getCurrentPowerPolicy(&currentPolicy);
    ASSERT_FALSE(status.isOk()) << "The current policy at creation should be null";
    // TODO(b/168545262): Add more test cases after VHAL integration is complete.
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

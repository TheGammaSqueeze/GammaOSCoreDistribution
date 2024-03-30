/*
 * Copyright 2020 The Android Open Source Project
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

#include "MockCarWatchdogServiceForSystem.h"
#include "MockVhalClient.h"
#include "MockWatchdogServiceHelper.h"
#include "WatchdogProcessService.h"
#include "WatchdogServiceHelper.h"

#include <android/automotive/watchdog/internal/BnCarWatchdogServiceForSystem.h>
#include <android/binder_interface_utils.h>
#include <gmock/gmock.h>

namespace android {
namespace automotive {
namespace watchdog {

namespace aawi = ::android::automotive::watchdog::internal;
namespace afav = ::android::frameworks::automotive::vhal;
namespace aahav = ::aidl::android::hardware::automotive::vehicle;

using aahav::VehicleProperty;
using aawi::ProcessIdentifier;
using ::android::IBinder;
using ::android::sp;
using ::android::binder::Status;
using ::ndk::SharedRefBase;
using ::testing::_;
using ::testing::ByMove;
using ::testing::Return;

namespace {

class MockCarWatchdogClient : public ICarWatchdogClientDefault {
public:
    MockCarWatchdogClient() { mBinder = sp<MockBinder>::make(); }
    sp<MockBinder> getBinder() const { return mBinder; }

    MOCK_METHOD(IBinder*, onAsBinder, (), (override));

private:
    sp<MockBinder> mBinder;
};

class MockCarWatchdogMonitor : public aawi::ICarWatchdogMonitorDefault {
public:
    MockCarWatchdogMonitor() { mBinder = sp<MockBinder>::make(); }
    sp<MockBinder> getBinder() const { return mBinder; }

    MOCK_METHOD(IBinder*, onAsBinder, (), (override));

private:
    sp<MockBinder> mBinder;
};

ProcessIdentifier constructProcessIdentifier(int32_t pid, int64_t startTimeMillis) {
    ProcessIdentifier processIdentifier;
    processIdentifier.pid = pid;
    processIdentifier.startTimeMillis = startTimeMillis;
    return processIdentifier;
}

}  // namespace

namespace internal {

class WatchdogProcessServicePeer final {
public:
    explicit WatchdogProcessServicePeer(const sp<WatchdogProcessService>& watchdogProcessService) :
          mWatchdogProcessService(watchdogProcessService) {
        mWatchdogProcessService->mGetStartTimeForPidFunc = [](pid_t) -> uint64_t { return 12356; };
    }

    void setVhalService(std::shared_ptr<afav::IVhalClient> service) {
        mWatchdogProcessService->mVhalService = service;
    }

    void setNotSupportedVhalProperties(const std::unordered_set<VehicleProperty>& properties) {
        mWatchdogProcessService->mNotSupportedVhalProperties = properties;
    }

private:
    sp<WatchdogProcessService> mWatchdogProcessService;
};

}  // namespace internal

class WatchdogProcessServiceTest : public ::testing::Test {
protected:
    void SetUp() override {
        sp<Looper> looper(Looper::prepare(/*opts=*/0));
        mWatchdogProcessService = sp<WatchdogProcessService>::make(looper);
        mMockVehicle = SharedRefBase::make<MockVehicle>();
        mMockVhalClient = std::make_shared<MockVhalClient>(mMockVehicle);
        internal::WatchdogProcessServicePeer peer(mWatchdogProcessService);
        peer.setVhalService(mMockVhalClient);
        peer.setNotSupportedVhalProperties(
                {VehicleProperty::WATCHDOG_ALIVE, VehicleProperty::WATCHDOG_TERMINATED_PROCESS});
        mWatchdogProcessService->start();
    }

    void TearDown() override {
        mWatchdogProcessService->terminate();
        mWatchdogProcessService.clear();
        mMockVhalClient.reset();
        mMockVehicle.reset();
    }

    sp<WatchdogProcessService> mWatchdogProcessService;
    std::shared_ptr<MockVhalClient> mMockVhalClient;
    std::shared_ptr<MockVehicle> mMockVehicle;
};

sp<MockCarWatchdogClient> createMockCarWatchdogClient(status_t linkToDeathResult) {
    sp<MockCarWatchdogClient> client = sp<MockCarWatchdogClient>::make();
    sp<MockBinder> binder = client->getBinder();
    EXPECT_CALL(*binder, linkToDeath(_, nullptr, 0)).WillRepeatedly(Return(linkToDeathResult));
    EXPECT_CALL(*binder, unlinkToDeath(_, nullptr, 0, nullptr)).WillRepeatedly(Return(OK));
    EXPECT_CALL(*client, onAsBinder()).WillRepeatedly(Return(binder.get()));
    return client;
}

sp<MockCarWatchdogMonitor> createMockCarWatchdogMonitor(status_t linkToDeathResult) {
    sp<MockCarWatchdogMonitor> monitor = sp<MockCarWatchdogMonitor>::make();
    sp<MockBinder> binder = monitor->getBinder();
    EXPECT_CALL(*binder, linkToDeath(_, nullptr, 0)).WillRepeatedly(Return(linkToDeathResult));
    EXPECT_CALL(*binder, unlinkToDeath(_, nullptr, 0, nullptr)).WillRepeatedly(Return(OK));
    EXPECT_CALL(*monitor, onAsBinder()).WillRepeatedly(Return(binder.get()));
    return monitor;
}

sp<MockCarWatchdogClient> expectNormalCarWatchdogClient() {
    return createMockCarWatchdogClient(OK);
}

sp<MockCarWatchdogClient> expectCarWatchdogClientBinderDied() {
    return createMockCarWatchdogClient(DEAD_OBJECT);
}

sp<MockCarWatchdogMonitor> expectNormalCarWatchdogMonitor() {
    return createMockCarWatchdogMonitor(OK);
}

sp<MockCarWatchdogMonitor> expectCarWatchdogMonitorBinderDied() {
    return createMockCarWatchdogMonitor(DEAD_OBJECT);
}

TEST_F(WatchdogProcessServiceTest, TestTerminate) {
    std::vector<int32_t> propIds = {static_cast<int32_t>(aahav::VehicleProperty::VHAL_HEARTBEAT)};
    EXPECT_CALL(*mMockVhalClient, removeOnBinderDiedCallback(_)).Times(1);
    EXPECT_CALL(*mMockVehicle, unsubscribe(_, propIds))
            .WillOnce(Return(ByMove(std::move(ndk::ScopedAStatus::ok()))));
    mWatchdogProcessService->terminate();
    // TODO(b/217405065): Verify looper removes all MSG_VHAL_HEALTH_CHECK messages.
}

// TODO(b/217405065): Add test to verify the handleVhalDeath method.

TEST_F(WatchdogProcessServiceTest, TestRegisterClient) {
    sp<MockCarWatchdogClient> client = expectNormalCarWatchdogClient();
    Status status =
            mWatchdogProcessService->registerClient(client, TimeoutLength::TIMEOUT_CRITICAL);
    ASSERT_TRUE(status.isOk()) << status;
    status = mWatchdogProcessService->registerClient(client, TimeoutLength::TIMEOUT_CRITICAL);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogProcessServiceTest, TestUnregisterClient) {
    sp<MockCarWatchdogClient> client = expectNormalCarWatchdogClient();
    mWatchdogProcessService->registerClient(client, TimeoutLength::TIMEOUT_CRITICAL);
    Status status = mWatchdogProcessService->unregisterClient(client);
    ASSERT_TRUE(status.isOk()) << status;
    ASSERT_FALSE(mWatchdogProcessService->unregisterClient(client).isOk())
            << "Unregistering an unregistered client should return an error";
}

TEST_F(WatchdogProcessServiceTest, TestErrorOnRegisterClientWithDeadBinder) {
    sp<MockCarWatchdogClient> client = expectCarWatchdogClientBinderDied();
    ASSERT_FALSE(
            mWatchdogProcessService->registerClient(client, TimeoutLength::TIMEOUT_CRITICAL).isOk())
            << "When linkToDeath fails, registerClient should return an error";
}

TEST_F(WatchdogProcessServiceTest, TestRegisterCarWatchdogService) {
    sp<MockWatchdogServiceHelper> mockServiceHelper = sp<MockWatchdogServiceHelper>::make();
    ASSERT_RESULT_OK(mWatchdogProcessService->registerWatchdogServiceHelper(mockServiceHelper));

    sp<MockCarWatchdogServiceForSystem> mockService = sp<MockCarWatchdogServiceForSystem>::make();
    sp<IBinder> binder = mockService->getBinder();

    Status status = mWatchdogProcessService->registerCarWatchdogService(binder);
    ASSERT_TRUE(status.isOk()) << status;

    status = mWatchdogProcessService->registerCarWatchdogService(binder);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogProcessServiceTest,
       TestErrorOnRegisterCarWatchdogServiceWithUninitializedWatchdogServiceHelper) {
    sp<MockCarWatchdogServiceForSystem> mockService = sp<MockCarWatchdogServiceForSystem>::make();
    sp<IBinder> binder = mockService->getBinder();

    ASSERT_FALSE(mWatchdogProcessService->registerCarWatchdogService(binder).isOk())
            << "Registering car watchdog service should fail when watchdog service helper is "
               "uninitialized";
}

TEST_F(WatchdogProcessServiceTest, TestRegisterMonitor) {
    sp<aawi::ICarWatchdogMonitor> monitorOne = expectNormalCarWatchdogMonitor();
    sp<aawi::ICarWatchdogMonitor> monitorTwo = expectNormalCarWatchdogMonitor();
    Status status = mWatchdogProcessService->registerMonitor(monitorOne);
    ASSERT_TRUE(status.isOk()) << status;
    status = mWatchdogProcessService->registerMonitor(monitorOne);
    ASSERT_TRUE(status.isOk()) << status;
    status = mWatchdogProcessService->registerMonitor(monitorTwo);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogProcessServiceTest, TestErrorOnRegisterMonitorWithDeadBinder) {
    sp<MockCarWatchdogMonitor> monitor = expectCarWatchdogMonitorBinderDied();
    ASSERT_FALSE(mWatchdogProcessService->registerMonitor(monitor).isOk())
            << "When linkToDeath fails, registerMonitor should return an error";
}

TEST_F(WatchdogProcessServiceTest, TestUnregisterMonitor) {
    sp<aawi::ICarWatchdogMonitor> monitor = expectNormalCarWatchdogMonitor();
    mWatchdogProcessService->registerMonitor(monitor);
    Status status = mWatchdogProcessService->unregisterMonitor(monitor);
    ASSERT_TRUE(status.isOk()) << status;
    ASSERT_FALSE(mWatchdogProcessService->unregisterMonitor(monitor).isOk())
            << "Unregistering an unregistered monitor should return an error";
}

TEST_F(WatchdogProcessServiceTest, TestTellClientAlive) {
    sp<ICarWatchdogClient> client = expectNormalCarWatchdogClient();
    mWatchdogProcessService->registerClient(client, TimeoutLength::TIMEOUT_CRITICAL);
    ASSERT_FALSE(mWatchdogProcessService->tellClientAlive(client, 1234).isOk())
            << "tellClientAlive not synced with checkIfAlive should return an error";
}

TEST_F(WatchdogProcessServiceTest, TestTellCarWatchdogServiceAlive) {
    sp<MockWatchdogServiceHelper> mockServiceHelper = sp<MockWatchdogServiceHelper>::make();
    ASSERT_RESULT_OK(mWatchdogProcessService->registerWatchdogServiceHelper(mockServiceHelper));

    sp<MockCarWatchdogServiceForSystem> mockService = sp<MockCarWatchdogServiceForSystem>::make();

    std::vector<ProcessIdentifier> processIdentifiers;
    processIdentifiers.push_back(
            constructProcessIdentifier(/* pid= */ 111, /* startTimeMillis= */ 0));
    processIdentifiers.push_back(
            constructProcessIdentifier(/* pid= */ 222, /* startTimeMillis= */ 0));
    ASSERT_FALSE(mWatchdogProcessService
                         ->tellCarWatchdogServiceAlive(mockService, processIdentifiers, 1234)
                         .isOk())
            << "tellCarWatchdogServiceAlive not synced with checkIfAlive should return an error";
}

TEST_F(WatchdogProcessServiceTest, TestTellDumpFinished) {
    sp<aawi::ICarWatchdogMonitor> monitor = expectNormalCarWatchdogMonitor();
    ASSERT_FALSE(mWatchdogProcessService
                         ->tellDumpFinished(monitor,
                                            constructProcessIdentifier(/* pid= */ 1234,
                                                                       /* startTimeMillis= */ 0))
                         .isOk())
            << "Unregistered monitor cannot call tellDumpFinished";
    mWatchdogProcessService->registerMonitor(monitor);
    Status status =
            mWatchdogProcessService
                    ->tellDumpFinished(monitor,
                                       constructProcessIdentifier(/* pid= */ 1234,
                                                                  /* startTimeMillis= */ 0));
    ASSERT_TRUE(status.isOk()) << status;
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

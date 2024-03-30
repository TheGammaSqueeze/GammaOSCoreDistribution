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

#include "MockIoOveruseMonitor.h"
#include "MockWatchdogPerfService.h"
#include "MockWatchdogProcessService.h"
#include "MockWatchdogServiceHelper.h"
#include "ThreadPriorityController.h"
#include "WatchdogBinderMediator.h"
#include "WatchdogInternalHandler.h"
#include "WatchdogServiceHelper.h"

#include <android-base/result.h>
#include <android/automotive/watchdog/internal/BootPhase.h>
#include <android/automotive/watchdog/internal/GarageMode.h>
#include <android/automotive/watchdog/internal/PowerCycle.h>
#include <android/automotive/watchdog/internal/UserState.h>
#include <binder/IBinder.h>
#include <binder/IPCThreadState.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <private/android_filesystem_config.h>
#include <utils/RefBase.h>

#include <errno.h>
#include <sched.h>
#include <unistd.h>

namespace android {
namespace automotive {
namespace watchdog {

namespace aawi = ::android::automotive::watchdog::internal;

using aawi::GarageMode;
using aawi::ICarWatchdogServiceForSystem;
using aawi::ICarWatchdogServiceForSystemDefault;
using aawi::PowerCycle;
using aawi::ProcessIdentifier;
using aawi::ResourceOveruseConfiguration;
using aawi::ThreadPolicyWithPriority;
using aawi::UserState;
using ::android::sp;
using ::android::String16;
using ::android::base::Result;
using ::android::binder::Status;
using ::testing::_;
using ::testing::Pointer;
using ::testing::Return;

class WatchdogInternalHandlerTestPeer final {
public:
    explicit WatchdogInternalHandlerTestPeer(WatchdogInternalHandler* handler) :
          mHandler(handler) {}

    void setThreadPriorityController(std::unique_ptr<ThreadPriorityController> controller) {
        mHandler->setThreadPriorityController(std::move(controller));
    }

private:
    WatchdogInternalHandler* mHandler;
};

namespace {

class MockWatchdogBinderMediator :
      public WatchdogBinderMediatorInterface,
      public ICarWatchdogDefault {
public:
    MOCK_METHOD(Result<void>, init, (), (override));
    MOCK_METHOD(void, terminate, (), (override));
    MOCK_METHOD(status_t, dump, (int fd, const Vector<android::String16>&), (override));
    MOCK_METHOD(Status, registerClient, (const android::sp<ICarWatchdogClient>&, TimeoutLength),
                (override));
    MOCK_METHOD(Status, unregisterClient, (const android::sp<ICarWatchdogClient>&), (override));
    MOCK_METHOD(Status, tellClientAlive, (const android::sp<ICarWatchdogClient>&, int32_t),
                (override));
    MOCK_METHOD(Status, addResourceOveruseListener,
                (const std::vector<ResourceType>&, const sp<IResourceOveruseListener>&),
                (override));
    MOCK_METHOD(Status, removeResourceOveruseListener, (const sp<IResourceOveruseListener>&),
                (override));
    MOCK_METHOD(Status, getResourceOveruseStats,
                (const std::vector<ResourceType>&, std::vector<ResourceOveruseStats>*), (override));
    MOCK_METHOD(Status, registerMediator, (const android::sp<ICarWatchdogClient>&), (override));
    MOCK_METHOD(Status, unregisterMediator, (const android::sp<ICarWatchdogClient>&), (override));
    MOCK_METHOD(Status, registerMonitor, (const android::sp<ICarWatchdogMonitor>&), (override));
    MOCK_METHOD(Status, unregisterMonitor, (const android::sp<ICarWatchdogMonitor>&), (override));
    MOCK_METHOD(Status, tellMediatorAlive,
                (const android::sp<ICarWatchdogClient>&, const std::vector<int32_t>&, int32_t),
                (override));
    MOCK_METHOD(Status, tellDumpFinished, (const android::sp<ICarWatchdogMonitor>&, int32_t),
                (override));
    MOCK_METHOD(Status, notifySystemStateChange, (StateType, int32_t, int32_t), (override));
};

class MockSystemCalls : public ThreadPriorityController::SystemCallsInterface {
public:
    MockSystemCalls(int tid, int uid, int pid) {
        ON_CALL(*this, readPidStatusFileForPid(tid))
                .WillByDefault(Return(std::make_tuple(uid, pid)));
    }

    MOCK_METHOD(int, setScheduler, (pid_t tid, int policy, const sched_param* param), (override));
    MOCK_METHOD(int, getScheduler, (pid_t tid), (override));
    MOCK_METHOD(int, getParam, (pid_t tid, sched_param* param), (override));
    MOCK_METHOD((Result<std::tuple<uid_t, pid_t>>), readPidStatusFileForPid, (pid_t pid),
                (override));
};

class ScopedChangeCallingUid final : public RefBase {
public:
    explicit ScopedChangeCallingUid(uid_t uid) {
        mCallingUid = IPCThreadState::self()->getCallingUid();
        mCallingPid = IPCThreadState::self()->getCallingPid();
        if (mCallingUid == uid) {
            return;
        }
        mChangedUid = uid;
        int64_t token = ((int64_t)mChangedUid << 32) | mCallingPid;
        IPCThreadState::self()->restoreCallingIdentity(token);
    }
    ~ScopedChangeCallingUid() {
        if (mCallingUid == mChangedUid) {
            return;
        }
        int64_t token = ((int64_t)mCallingUid << 32) | mCallingPid;
        IPCThreadState::self()->restoreCallingIdentity(token);
    }

private:
    uid_t mCallingUid;
    uid_t mChangedUid;
    pid_t mCallingPid;
};

MATCHER_P(PriorityEq, priority, "") {
    return (arg->sched_priority) == priority;
}

}  // namespace

class WatchdogInternalHandlerTest : public ::testing::Test {
protected:
    static constexpr pid_t TEST_PID = 1;
    static constexpr pid_t TEST_TID = 2;
    static constexpr uid_t TEST_UID = 3;

    virtual void SetUp() {
        mMockWatchdogProcessService = sp<MockWatchdogProcessService>::make();
        mMockWatchdogPerfService = sp<MockWatchdogPerfService>::make();
        mMockWatchdogServiceHelper = sp<MockWatchdogServiceHelper>::make();
        mMockIoOveruseMonitor = sp<MockIoOveruseMonitor>::make();
        mMockWatchdogBinderMediator = sp<MockWatchdogBinderMediator>::make();
        mWatchdogInternalHandler =
                sp<WatchdogInternalHandler>::make(mMockWatchdogBinderMediator,
                                                  mMockWatchdogServiceHelper,
                                                  mMockWatchdogProcessService,
                                                  mMockWatchdogPerfService, mMockIoOveruseMonitor);
        WatchdogInternalHandlerTestPeer peer(mWatchdogInternalHandler.get());
        std::unique_ptr<MockSystemCalls> mockSystemCalls =
                std::make_unique<MockSystemCalls>(TEST_TID, TEST_UID, TEST_PID);
        mMockSystemCalls = mockSystemCalls.get();
        peer.setThreadPriorityController(
                std::make_unique<ThreadPriorityController>(std::move(mockSystemCalls)));
    }
    virtual void TearDown() {
        mMockWatchdogBinderMediator.clear();
        mMockWatchdogServiceHelper.clear();
        mMockWatchdogProcessService.clear();
        mMockWatchdogPerfService.clear();
        mMockIoOveruseMonitor.clear();
        mWatchdogInternalHandler.clear();
        mScopedChangeCallingUid.clear();
    }

    // Sets calling UID to imitate System's process.
    void setSystemCallingUid() {
        mScopedChangeCallingUid = sp<ScopedChangeCallingUid>::make(AID_SYSTEM);
    }

    sp<MockWatchdogBinderMediator> mMockWatchdogBinderMediator;
    sp<MockWatchdogServiceHelper> mMockWatchdogServiceHelper;
    sp<MockWatchdogProcessService> mMockWatchdogProcessService;
    sp<MockWatchdogPerfService> mMockWatchdogPerfService;
    sp<MockIoOveruseMonitor> mMockIoOveruseMonitor;
    sp<WatchdogInternalHandler> mWatchdogInternalHandler;
    sp<ScopedChangeCallingUid> mScopedChangeCallingUid;
    MockSystemCalls* mMockSystemCalls;
};

TEST_F(WatchdogInternalHandlerTest, TestTerminate) {
    ASSERT_NE(mWatchdogInternalHandler->mWatchdogBinderMediator, nullptr);
    ASSERT_NE(mWatchdogInternalHandler->mWatchdogServiceHelper, nullptr);
    ASSERT_NE(mWatchdogInternalHandler->mWatchdogProcessService, nullptr);
    ASSERT_NE(mWatchdogInternalHandler->mWatchdogPerfService, nullptr);
    ASSERT_NE(mWatchdogInternalHandler->mIoOveruseMonitor, nullptr);

    mWatchdogInternalHandler->terminate();

    ASSERT_EQ(mWatchdogInternalHandler->mWatchdogBinderMediator, nullptr);
    ASSERT_EQ(mWatchdogInternalHandler->mWatchdogServiceHelper, nullptr);
    ASSERT_EQ(mWatchdogInternalHandler->mWatchdogProcessService, nullptr);
    ASSERT_EQ(mWatchdogInternalHandler->mWatchdogPerfService, nullptr);
    ASSERT_EQ(mWatchdogInternalHandler->mIoOveruseMonitor, nullptr);
}

TEST_F(WatchdogInternalHandlerTest, TestDump) {
    EXPECT_CALL(*mMockWatchdogBinderMediator, dump(-1, _)).WillOnce(Return(OK));
    ASSERT_EQ(mWatchdogInternalHandler->dump(-1, Vector<String16>()), OK);
}

TEST_F(WatchdogInternalHandlerTest, TestRegisterCarWatchdogService) {
    setSystemCallingUid();
    sp<ICarWatchdogServiceForSystem> service = sp<ICarWatchdogServiceForSystemDefault>::make();
    EXPECT_CALL(*mMockWatchdogServiceHelper, registerService(service))
            .WillOnce(Return(Status::ok()));

    Status status = mWatchdogInternalHandler->registerCarWatchdogService(service);

    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnRegisterCarWatchdogServiceWithNonSystemCallingUid) {
    sp<ICarWatchdogServiceForSystem> service = sp<ICarWatchdogServiceForSystemDefault>::make();
    EXPECT_CALL(*mMockWatchdogServiceHelper, registerService(_)).Times(0);

    Status status = mWatchdogInternalHandler->registerCarWatchdogService(service);

    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest,
       TestErrorOnRegisterCarWatchdogServiceWithWatchdogServiceHelperError) {
    setSystemCallingUid();
    sp<ICarWatchdogServiceForSystem> service = sp<ICarWatchdogServiceForSystemDefault>::make();
    EXPECT_CALL(*mMockWatchdogServiceHelper, registerService(service))
            .WillOnce(Return(Status::fromExceptionCode(Status::EX_ILLEGAL_STATE, "Illegal state")));

    Status status = mWatchdogInternalHandler->registerCarWatchdogService(service);

    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestUnregisterCarWatchdogService) {
    setSystemCallingUid();
    sp<ICarWatchdogServiceForSystem> service = sp<ICarWatchdogServiceForSystemDefault>::make();
    EXPECT_CALL(*mMockWatchdogServiceHelper, unregisterService(service))
            .WillOnce(Return(Status::ok()));
    Status status = mWatchdogInternalHandler->unregisterCarWatchdogService(service);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest,
       TestErrorOnUnregisterCarWatchdogServiceWithNonSystemCallingUid) {
    sp<ICarWatchdogServiceForSystem> service = sp<ICarWatchdogServiceForSystemDefault>::make();
    EXPECT_CALL(*mMockWatchdogServiceHelper, unregisterService(service)).Times(0);
    Status status = mWatchdogInternalHandler->unregisterCarWatchdogService(service);
    ASSERT_FALSE(status.isOk()) << status;
}
TEST_F(WatchdogInternalHandlerTest,
       TestErrorOnUnregisterCarWatchdogServiceWithWatchdogServiceHelperError) {
    setSystemCallingUid();
    sp<ICarWatchdogServiceForSystem> service = sp<ICarWatchdogServiceForSystemDefault>::make();
    EXPECT_CALL(*mMockWatchdogServiceHelper, unregisterService(service))
            .WillOnce(Return(
                    Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT, "Illegal argument")));
    Status status = mWatchdogInternalHandler->unregisterCarWatchdogService(service);
    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestRegisterMonitor) {
    setSystemCallingUid();
    sp<aawi::ICarWatchdogMonitor> monitor = sp<aawi::ICarWatchdogMonitorDefault>::make();
    EXPECT_CALL(*mMockWatchdogProcessService, registerMonitor(monitor))
            .WillOnce(Return(Status::ok()));
    Status status = mWatchdogInternalHandler->registerMonitor(monitor);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnRegisterMonitorWithNonSystemCallingUid) {
    sp<aawi::ICarWatchdogMonitor> monitor = sp<aawi::ICarWatchdogMonitorDefault>::make();
    EXPECT_CALL(*mMockWatchdogProcessService, registerMonitor(monitor)).Times(0);
    Status status = mWatchdogInternalHandler->registerMonitor(monitor);
    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestUnregisterMonitor) {
    setSystemCallingUid();
    sp<aawi::ICarWatchdogMonitor> monitor = sp<aawi::ICarWatchdogMonitorDefault>::make();
    EXPECT_CALL(*mMockWatchdogProcessService, unregisterMonitor(monitor))
            .WillOnce(Return(Status::ok()));
    Status status = mWatchdogInternalHandler->unregisterMonitor(monitor);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnUnregisterMonitorWithNonSystemCallingUid) {
    sp<aawi::ICarWatchdogMonitor> monitor = sp<aawi::ICarWatchdogMonitorDefault>::make();
    EXPECT_CALL(*mMockWatchdogProcessService, unregisterMonitor(monitor)).Times(0);
    Status status = mWatchdogInternalHandler->unregisterMonitor(monitor);
    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestCarWatchdogServiceAlive) {
    setSystemCallingUid();
    sp<ICarWatchdogServiceForSystem> service = sp<ICarWatchdogServiceForSystemDefault>::make();
    std::vector<ProcessIdentifier> clientsNotResponding;
    ProcessIdentifier processIdentifier;
    processIdentifier.pid = 123;
    clientsNotResponding.push_back(processIdentifier);
    EXPECT_CALL(*mMockWatchdogProcessService,
                tellCarWatchdogServiceAlive(service, clientsNotResponding, 456))
            .WillOnce(Return(Status::ok()));
    Status status =
            mWatchdogInternalHandler->tellCarWatchdogServiceAlive(service, clientsNotResponding,
                                                                  456);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnCarWatchdogServiceWithNonSystemCallingUid) {
    sp<ICarWatchdogServiceForSystem> service = sp<ICarWatchdogServiceForSystemDefault>::make();
    std::vector<ProcessIdentifier> clientsNotResponding;
    ProcessIdentifier processIdentifier;
    processIdentifier.pid = 123;
    clientsNotResponding.push_back(processIdentifier);
    EXPECT_CALL(*mMockWatchdogProcessService, tellCarWatchdogServiceAlive(_, _, _)).Times(0);
    Status status =
            mWatchdogInternalHandler->tellCarWatchdogServiceAlive(service, clientsNotResponding,
                                                                  456);
    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestTellDumpFinished) {
    setSystemCallingUid();
    sp<aawi::ICarWatchdogMonitor> monitor = sp<aawi::ICarWatchdogMonitorDefault>::make();
    ProcessIdentifier processIdentifier;
    processIdentifier.pid = 456;
    EXPECT_CALL(*mMockWatchdogProcessService, tellDumpFinished(monitor, processIdentifier))
            .WillOnce(Return(Status::ok()));
    Status status = mWatchdogInternalHandler->tellDumpFinished(monitor, processIdentifier);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnTellDumpFinishedWithNonSystemCallingUid) {
    sp<aawi::ICarWatchdogMonitor> monitor = sp<aawi::ICarWatchdogMonitorDefault>::make();
    EXPECT_CALL(*mMockWatchdogProcessService, tellDumpFinished(_, _)).Times(0);
    ProcessIdentifier processIdentifier;
    processIdentifier.pid = 456;
    Status status = mWatchdogInternalHandler->tellDumpFinished(monitor, processIdentifier);
    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyPowerCycleChangeToShutdownPrepare) {
    setSystemCallingUid();
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(/*isEnabled=*/false)).Times(1);
    Status status =
            mWatchdogInternalHandler
                    ->notifySystemStateChange(aawi::StateType::POWER_CYCLE,
                                              static_cast<int32_t>(
                                                      PowerCycle::POWER_CYCLE_SHUTDOWN_PREPARE),
                                              -1);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyPowerCycleChangeToShutdownEnter) {
    setSystemCallingUid();
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(/*isEnabled=*/false)).Times(1);
    EXPECT_CALL(*mMockWatchdogPerfService, onShutdownEnter()).Times(1);

    Status status =
            mWatchdogInternalHandler
                    ->notifySystemStateChange(aawi::StateType::POWER_CYCLE,
                                              static_cast<int32_t>(
                                                      PowerCycle::POWER_CYCLE_SHUTDOWN_ENTER),
                                              -1);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyPowerCycleChangeToResume) {
    setSystemCallingUid();
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(/*isEnabled=*/true)).Times(1);
    Status status =
            mWatchdogInternalHandler
                    ->notifySystemStateChange(aawi::StateType::POWER_CYCLE,
                                              static_cast<int32_t>(PowerCycle::POWER_CYCLE_RESUME),
                                              -1);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyPowerCycleChangeToSuspendExit) {
    setSystemCallingUid();

    EXPECT_CALL(*mMockWatchdogPerfService, onSuspendExit()).Times(1);

    auto status = mWatchdogInternalHandler
                          ->notifySystemStateChange(aawi::StateType::POWER_CYCLE,
                                                    static_cast<int32_t>(
                                                            PowerCycle::POWER_CYCLE_SUSPEND_EXIT),
                                                    -1);

    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnNotifyPowerCycleChangeWithInvalidArgs) {
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(_)).Times(0);
    EXPECT_CALL(*mMockWatchdogPerfService, setSystemState(_)).Times(0);
    aawi::StateType type = aawi::StateType::POWER_CYCLE;

    Status status = mWatchdogInternalHandler->notifySystemStateChange(type, -1, -1);
    ASSERT_FALSE(status.isOk()) << status;

    status = mWatchdogInternalHandler->notifySystemStateChange(type, 3000, -1);
    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyGarageModeOn) {
    setSystemCallingUid();
    EXPECT_CALL(*mMockWatchdogPerfService, setSystemState(SystemState::GARAGE_MODE)).Times(1);
    Status status =
            mWatchdogInternalHandler->notifySystemStateChange(aawi::StateType::GARAGE_MODE,
                                                              static_cast<int32_t>(
                                                                      GarageMode::GARAGE_MODE_ON),
                                                              -1);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyGarageModeOff) {
    setSystemCallingUid();
    EXPECT_CALL(*mMockWatchdogPerfService, setSystemState(SystemState::NORMAL_MODE)).Times(1);
    Status status =
            mWatchdogInternalHandler->notifySystemStateChange(aawi::StateType::GARAGE_MODE,
                                                              static_cast<int32_t>(
                                                                      GarageMode::GARAGE_MODE_OFF),
                                                              -1);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithStartedUser) {
    setSystemCallingUid();
    aawi::StateType type = aawi::StateType::USER_STATE;
    EXPECT_CALL(*mMockWatchdogProcessService, onUserStateChange(234567, /*isStarted=*/true));
    Status status = mWatchdogInternalHandler
                            ->notifySystemStateChange(type, 234567,
                                                      static_cast<int32_t>(
                                                              aawi::UserState::USER_STATE_STARTED));
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithSwitchingUser) {
    setSystemCallingUid();
    aawi::StateType type = aawi::StateType::USER_STATE;

    EXPECT_CALL(*mMockWatchdogPerfService,
                onUserStateChange(234567, UserState::USER_STATE_SWITCHING));

    auto status = mWatchdogInternalHandler
                          ->notifySystemStateChange(type, 234567,
                                                    static_cast<int32_t>(
                                                            UserState::USER_STATE_SWITCHING));

    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithUnlockingUser) {
    setSystemCallingUid();
    aawi::StateType type = aawi::StateType::USER_STATE;

    EXPECT_CALL(*mMockWatchdogPerfService,
                onUserStateChange(234567, UserState::USER_STATE_UNLOCKING));

    auto status = mWatchdogInternalHandler
                          ->notifySystemStateChange(type, 234567,
                                                    static_cast<int32_t>(
                                                            UserState::USER_STATE_UNLOCKING));

    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithPostUnlockedUser) {
    setSystemCallingUid();
    aawi::StateType type = aawi::StateType::USER_STATE;

    EXPECT_CALL(*mMockWatchdogPerfService,
                onUserStateChange(234567, UserState::USER_STATE_POST_UNLOCKED));

    auto status = mWatchdogInternalHandler
                          ->notifySystemStateChange(type, 234567,
                                                    static_cast<int32_t>(
                                                            UserState::USER_STATE_POST_UNLOCKED));

    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithStoppedUser) {
    setSystemCallingUid();
    aawi::StateType type = aawi::StateType::USER_STATE;
    EXPECT_CALL(*mMockWatchdogProcessService, onUserStateChange(234567, /*isStarted=*/false));
    Status status = mWatchdogInternalHandler
                            ->notifySystemStateChange(type, 234567,
                                                      static_cast<int32_t>(
                                                              aawi::UserState::USER_STATE_STOPPED));
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestOnUserStateChangeWithRemovedUser) {
    setSystemCallingUid();
    aawi::StateType type = aawi::StateType::USER_STATE;
    EXPECT_CALL(*mMockIoOveruseMonitor, removeStatsForUser(/*userId=*/234567));
    Status status = mWatchdogInternalHandler
                            ->notifySystemStateChange(type, 234567,
                                                      static_cast<int32_t>(
                                                              aawi::UserState::USER_STATE_REMOVED));
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnOnUserStateChangeWithInvalidArgs) {
    EXPECT_CALL(*mMockWatchdogProcessService, onUserStateChange(_, _)).Times(0);
    aawi::StateType type = aawi::StateType::USER_STATE;

    Status status = mWatchdogInternalHandler->notifySystemStateChange(type, 234567, -1);
    ASSERT_FALSE(status.isOk()) << status;

    status = mWatchdogInternalHandler->notifySystemStateChange(type, 234567, 3000);
    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyBootPhaseChange) {
    setSystemCallingUid();
    aawi::StateType type = aawi::StateType::BOOT_PHASE;
    EXPECT_CALL(*mMockWatchdogPerfService, onBootFinished()).WillOnce(Return(Result<void>()));
    Status status =
            mWatchdogInternalHandler
                    ->notifySystemStateChange(type,
                                              static_cast<int32_t>(aawi::BootPhase::BOOT_COMPLETED),
                                              -1);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestNotifyBootPhaseChangeWithNonBootCompletedPhase) {
    setSystemCallingUid();
    aawi::StateType type = aawi::StateType::BOOT_PHASE;
    EXPECT_CALL(*mMockWatchdogPerfService, onBootFinished()).Times(0);
    Status status = mWatchdogInternalHandler->notifySystemStateChange(type, 0, -1);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnNotifySystemStateChangeWithNonSystemCallingUid) {
    aawi::StateType type = aawi::StateType::POWER_CYCLE;
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(_)).Times(0);
    EXPECT_CALL(*mMockWatchdogPerfService, setSystemState(_)).Times(0);
    Status status =
            mWatchdogInternalHandler
                    ->notifySystemStateChange(type,
                                              static_cast<int32_t>(
                                                      PowerCycle::POWER_CYCLE_SHUTDOWN_PREPARE),
                                              -1);
    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestUpdateResourceOveruseConfigurations) {
    setSystemCallingUid();
    EXPECT_CALL(*mMockIoOveruseMonitor, updateResourceOveruseConfigurations(_))
            .WillOnce(Return(Result<void>()));
    Status status = mWatchdogInternalHandler->updateResourceOveruseConfigurations(
            std::vector<ResourceOveruseConfiguration>{});
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest,
       TestErrorOnUpdateResourceOveruseConfigurationsWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockIoOveruseMonitor, updateResourceOveruseConfigurations(_)).Times(0);
    Status status = mWatchdogInternalHandler->updateResourceOveruseConfigurations(
            std::vector<ResourceOveruseConfiguration>{});
    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestGetResourceOveruseConfigurations) {
    setSystemCallingUid();
    std::vector<ResourceOveruseConfiguration> configs;
    EXPECT_CALL(*mMockIoOveruseMonitor, getResourceOveruseConfigurations(Pointer(&configs)))
            .WillOnce(Return(Result<void>()));
    Status status = mWatchdogInternalHandler->getResourceOveruseConfigurations(&configs);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest,
       TestErrorOnGetResourceOveruseConfigurationsWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockIoOveruseMonitor, getResourceOveruseConfigurations(_)).Times(0);
    std::vector<ResourceOveruseConfiguration> configs;
    Status status = mWatchdogInternalHandler->getResourceOveruseConfigurations(&configs);
    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestControlProcessHealthCheck) {
    setSystemCallingUid();
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(/*isEnabled=*/true)).Times(1);
    Status status = mWatchdogInternalHandler->controlProcessHealthCheck(true);
    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestErrorOnControlProcessHealthCheckWithNonSystemCallingUid) {
    EXPECT_CALL(*mMockWatchdogProcessService, setEnabled(_)).Times(0);
    Status status = mWatchdogInternalHandler->controlProcessHealthCheck(false);
    ASSERT_FALSE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestSetThreadPriority) {
    setSystemCallingUid();
    int policy = SCHED_FIFO;
    int priority = 1;
    EXPECT_CALL(*mMockSystemCalls, setScheduler(TEST_TID, policy, PriorityEq(priority)))
            .WillOnce(Return(0));

    Status status = mWatchdogInternalHandler->setThreadPriority(TEST_PID, TEST_TID, TEST_UID,
                                                                policy, priority);

    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestSetThreadPriorityDefaultPolicy) {
    setSystemCallingUid();
    int policy = SCHED_OTHER;
    int setPriority = 1;
    // Default policy should ignore the provided priority.
    int expectedPriority = 0;
    EXPECT_CALL(*mMockSystemCalls, setScheduler(TEST_TID, policy, PriorityEq(expectedPriority)))
            .WillOnce(Return(0));

    Status status = mWatchdogInternalHandler->setThreadPriority(TEST_PID, TEST_TID, TEST_UID,
                                                                policy, setPriority);

    ASSERT_TRUE(status.isOk()) << status;
}

TEST_F(WatchdogInternalHandlerTest, TestSetThreadPriorityInvalidPid) {
    setSystemCallingUid();

    Status status = mWatchdogInternalHandler->setThreadPriority(TEST_PID + 1, TEST_TID, TEST_UID,
                                                                SCHED_FIFO, 1);

    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.exceptionCode(), EX_ILLEGAL_STATE);
}

TEST_F(WatchdogInternalHandlerTest, TestSetThreadPriorityInvalidTid) {
    setSystemCallingUid();

    Status status = mWatchdogInternalHandler->setThreadPriority(TEST_PID, TEST_TID + 1, TEST_UID,
                                                                SCHED_FIFO, 1);

    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.exceptionCode(), EX_ILLEGAL_STATE);
}

TEST_F(WatchdogInternalHandlerTest, TestSetThreadPriorityInvalidUid) {
    setSystemCallingUid();

    Status status = mWatchdogInternalHandler->setThreadPriority(TEST_PID, TEST_TID, TEST_UID + 1,
                                                                SCHED_FIFO, 1);

    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.exceptionCode(), EX_ILLEGAL_STATE);
}

TEST_F(WatchdogInternalHandlerTest, TestSetThreadPriorityInvalidPolicy) {
    setSystemCallingUid();

    Status status =
            mWatchdogInternalHandler->setThreadPriority(TEST_PID, TEST_TID, TEST_UID, -1, 1);

    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.exceptionCode(), EX_ILLEGAL_ARGUMENT);
}

TEST_F(WatchdogInternalHandlerTest, TestSetThreadPriorityInvalidPriority) {
    setSystemCallingUid();

    Status status = mWatchdogInternalHandler->setThreadPriority(TEST_PID, TEST_TID, TEST_UID,
                                                                SCHED_FIFO, 0);

    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.exceptionCode(), EX_ILLEGAL_ARGUMENT);
}

TEST_F(WatchdogInternalHandlerTest, TestSetThreadPriorityFailed) {
    setSystemCallingUid();
    int expectedPolicy = SCHED_FIFO;
    int expectedPriority = 1;
    EXPECT_CALL(*mMockSystemCalls,
                setScheduler(TEST_TID, expectedPolicy, PriorityEq(expectedPriority)))
            .WillOnce(Return(-1));

    Status status = mWatchdogInternalHandler->setThreadPriority(TEST_PID, TEST_TID, TEST_UID,
                                                                expectedPolicy, expectedPriority);

    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.exceptionCode(), EX_SERVICE_SPECIFIC);
}

TEST_F(WatchdogInternalHandlerTest, TestGetThreadPriority) {
    setSystemCallingUid();
    int expectedPolicy = SCHED_FIFO;
    int expectedPriority = 1;
    EXPECT_CALL(*mMockSystemCalls, getScheduler(TEST_TID)).WillOnce(Return(expectedPolicy));
    EXPECT_CALL(*mMockSystemCalls, getParam(TEST_TID, _))
            .WillOnce([expectedPriority](pid_t, sched_param* param) {
                param->sched_priority = expectedPriority;
                return 0;
            });

    ThreadPolicyWithPriority actual;
    Status status =
            mWatchdogInternalHandler->getThreadPriority(TEST_PID, TEST_TID, TEST_UID, &actual);

    ASSERT_TRUE(status.isOk()) << status;
    EXPECT_EQ(actual.policy, expectedPolicy);
    EXPECT_EQ(actual.priority, expectedPriority);
}

TEST_F(WatchdogInternalHandlerTest, TestGetThreadPriorityInvalidPid) {
    setSystemCallingUid();

    ThreadPolicyWithPriority actual;
    Status status =
            mWatchdogInternalHandler->getThreadPriority(TEST_PID + 1, TEST_TID, TEST_UID, &actual);

    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.exceptionCode(), EX_ILLEGAL_STATE);
}

TEST_F(WatchdogInternalHandlerTest, TestGetThreadPriorityGetSchedulerFailed) {
    setSystemCallingUid();
    EXPECT_CALL(*mMockSystemCalls, getScheduler(TEST_TID)).WillOnce(Return(-1));

    ThreadPolicyWithPriority actual;
    Status status =
            mWatchdogInternalHandler->getThreadPriority(TEST_PID, TEST_TID, TEST_UID, &actual);

    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.exceptionCode(), EX_SERVICE_SPECIFIC);
}

TEST_F(WatchdogInternalHandlerTest, TestGetThreadPriorityGetParamFailed) {
    setSystemCallingUid();
    EXPECT_CALL(*mMockSystemCalls, getScheduler(TEST_TID)).WillOnce(Return(0));
    EXPECT_CALL(*mMockSystemCalls, getParam(TEST_TID, _)).WillOnce(Return(-1));

    ThreadPolicyWithPriority actual;
    Status status =
            mWatchdogInternalHandler->getThreadPriority(TEST_PID, TEST_TID, TEST_UID, &actual);

    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.exceptionCode(), EX_SERVICE_SPECIFIC);
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

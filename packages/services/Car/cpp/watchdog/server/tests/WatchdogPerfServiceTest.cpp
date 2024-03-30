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

#include "LooperStub.h"
#include "MockDataProcessor.h"
#include "MockProcDiskStatsCollector.h"
#include "MockProcStatCollector.h"
#include "MockUidStatsCollector.h"
#include "ProcStatCollector.h"
#include "UidStatsCollector.h"
#include "WatchdogPerfService.h"

#include <WatchdogProperties.sysprop.h>
#include <android-base/file.h>
#include <android/automotive/watchdog/internal/UserState.h>
#include <gmock/gmock.h>
#include <utils/RefBase.h>

#include <future>  // NOLINT(build/c++11)
#include <queue>
#include <string>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::RefBase;
using ::android::sp;
using ::android::String16;
using ::android::wp;
using ::android::automotive::watchdog::internal::UserState;
using ::android::automotive::watchdog::testing::LooperStub;
using ::android::base::Error;
using ::android::base::Result;
using ::testing::_;
using ::testing::Eq;
using ::testing::InSequence;
using ::testing::Mock;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::StrictMock;
using ::testing::UnorderedElementsAreArray;

constexpr std::chrono::seconds kTestPostSystemEventDuration = 10s;
constexpr std::chrono::seconds kTestSystemEventCollectionInterval = 1s;
constexpr std::chrono::seconds kTestPeriodicCollectionInterval = 5s;
constexpr std::chrono::seconds kTestCustomCollectionInterval = 3s;
constexpr std::chrono::seconds kTestCustomCollectionDuration = 11s;
constexpr std::chrono::seconds kTestPeriodicMonitorInterval = 2s;
constexpr std::chrono::seconds kTestUserSwitchTimeout = 15s;
constexpr std::chrono::seconds kTestWakeUpDuration = 20s;

namespace internal {

class WatchdogPerfServicePeer final : public RefBase {
public:
    explicit WatchdogPerfServicePeer(const sp<WatchdogPerfService>& service) : mService(service) {}
    WatchdogPerfServicePeer() = delete;

    void init(const sp<LooperWrapper>& looper,
              const sp<UidStatsCollectorInterface>& uidStatsCollector,
              const sp<ProcStatCollectorInterface>& procStatCollector,
              const sp<ProcDiskStatsCollectorInterface>& procDiskStatsCollector) {
        Mutex::Autolock lock(mService->mMutex);
        mService->mHandlerLooper = looper;
        mService->mUidStatsCollector = uidStatsCollector;
        mService->mProcStatCollector = procStatCollector;
        mService->mProcDiskStatsCollector = procDiskStatsCollector;
    }

    void updateIntervals() {
        Mutex::Autolock lock(mService->mMutex);
        mService->mPostSystemEventDurationNs = kTestPostSystemEventDuration;
        mService->mBoottimeCollection.interval = kTestSystemEventCollectionInterval;
        mService->mPeriodicCollection.interval = kTestPeriodicCollectionInterval;
        mService->mUserSwitchCollection.interval = kTestSystemEventCollectionInterval;
        mService->mPeriodicMonitor.interval = kTestPeriodicMonitorInterval;
        mService->mUserSwitchTimeoutNs = kTestUserSwitchTimeout;
        mService->mWakeUpDurationNs = kTestWakeUpDuration;
    }

    void clearPostSystemEventDuration() {
        Mutex::Autolock lock(mService->mMutex);
        mService->mPostSystemEventDurationNs = 0ns;
    }

    EventType getCurrCollectionEvent() {
        Mutex::Autolock lock(mService->mMutex);
        return mService->mCurrCollectionEvent;
    }

    std::future<void> joinCollectionThread() {
        return std::async([&]() {
            if (mService->mCollectionThread.joinable()) {
                mService->mCollectionThread.join();
            }
        });
    }

protected:
    sp<WatchdogPerfService> mService;
};

}  // namespace internal

namespace {

class WatchdogPerfServiceTest : public ::testing::Test {
protected:
    virtual void SetUp() {
        mService = sp<WatchdogPerfService>::make();
        mServicePeer = sp<internal::WatchdogPerfServicePeer>::make(mService);
        mLooperStub = sp<LooperStub>::make();
        mMockUidStatsCollector = sp<MockUidStatsCollector>::make();
        mMockDataProcessor = sp<StrictMock<MockDataProcessor>>::make();
        mMockProcDiskStatsCollector = sp<NiceMock<MockProcDiskStatsCollector>>::make();
        mMockProcStatCollector = sp<NiceMock<MockProcStatCollector>>::make();
    }

    virtual void TearDown() {
        if (auto event = mServicePeer->getCurrCollectionEvent();
            event != EventType::INIT && event != EventType::TERMINATED) {
            EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);
            mService->terminate();
        }
        mService.clear();
        mServicePeer.clear();
        mLooperStub.clear();
        mMockUidStatsCollector.clear();
        mMockDataProcessor.clear();
        mMockProcDiskStatsCollector.clear();
        mMockProcStatCollector.clear();
    }

    void startService() {
        mServicePeer->init(mLooperStub, mMockUidStatsCollector, mMockProcStatCollector,
                           mMockProcDiskStatsCollector);

        EXPECT_CALL(*mMockDataProcessor, init()).Times(1);
        EXPECT_CALL(*mMockDataProcessor, onSystemStartup()).Times(1);

        ASSERT_RESULT_OK(mService->registerDataProcessor(mMockDataProcessor));

        EXPECT_CALL(*mMockUidStatsCollector, init()).Times(1);
        EXPECT_CALL(*mMockProcStatCollector, init()).Times(1);
        EXPECT_CALL(*mMockProcDiskStatsCollector, init()).Times(1);

        ASSERT_RESULT_OK(mService->start());

        mServicePeer->updateIntervals();
    }

    void startPeriodicCollection() {
        int bootIterations = static_cast<int>(kTestPostSystemEventDuration.count() /
                                              kTestSystemEventCollectionInterval.count());

        // Add the boot collection event done during startService()
        bootIterations += 1;

        EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(bootIterations);
        EXPECT_CALL(*mMockProcStatCollector, collect()).Times(bootIterations);
        EXPECT_CALL(*mMockDataProcessor,
                    onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
                .Times(bootIterations);

        // Make sure the collection event changes from EventType::INIT to
        // EventType::BOOT_TIME_COLLECTION.
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        // Mark boot complete.
        ASSERT_RESULT_OK(mService->onBootFinished());

        // Poll all post boot-time collections
        for (int i = 1; i < bootIterations; i++) {
            ASSERT_RESULT_OK(mLooperStub->pollCache());
        }

        // Process |SwitchMessage::END_BOOTTIME_COLLECTION| and switch to periodic collection.
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
                << "Invalid collection event";

        ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
    }

    void skipPeriodicMonitorEvents() {
        EXPECT_CALL(*mMockDataProcessor, onPeriodicMonitor(_, _, _)).Times(2);
        ASSERT_RESULT_OK(mLooperStub->pollCache());
        ASSERT_RESULT_OK(mLooperStub->pollCache());
    }

    void verifyAndClearExpectations() {
        Mock::VerifyAndClearExpectations(mMockUidStatsCollector.get());
        Mock::VerifyAndClearExpectations(mMockProcStatCollector.get());
        Mock::VerifyAndClearExpectations(mMockProcDiskStatsCollector.get());
        Mock::VerifyAndClearExpectations(mMockDataProcessor.get());
    }

    sp<WatchdogPerfService> mService;
    sp<internal::WatchdogPerfServicePeer> mServicePeer;
    sp<LooperStub> mLooperStub;
    sp<MockUidStatsCollector> mMockUidStatsCollector;
    sp<MockProcStatCollector> mMockProcStatCollector;
    sp<MockProcDiskStatsCollector> mMockProcDiskStatsCollector;
    sp<MockDataProcessor> mMockDataProcessor;
};

}  // namespace

TEST_F(WatchdogPerfServiceTest, TestServiceStartAndTerminate) {
    mServicePeer->init(mLooperStub, mMockUidStatsCollector, mMockProcStatCollector,
                       mMockProcDiskStatsCollector);

    EXPECT_CALL(*mMockDataProcessor, init()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onSystemStartup()).Times(1);

    ASSERT_RESULT_OK(mService->registerDataProcessor(mMockDataProcessor));

    EXPECT_CALL(*mMockUidStatsCollector, init()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, init()).Times(1);
    EXPECT_CALL(*mMockProcDiskStatsCollector, init()).Times(1);

    ASSERT_RESULT_OK(mService->start());

    ASSERT_TRUE(mService->mCollectionThread.joinable()) << "Collection thread not created";

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Boot-time collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";

    ASSERT_FALSE(mService->start().ok())
            << "No error returned when WatchdogPerfService was started more than once";

    ASSERT_TRUE(sysprop::systemEventCollectionInterval().has_value());
    ASSERT_EQ(std::chrono::duration_cast<std::chrono::seconds>(
                      mService->mBoottimeCollection.interval)
                      .count(),
              sysprop::systemEventCollectionInterval().value());
    ASSERT_TRUE(sysprop::periodicCollectionInterval().has_value());
    ASSERT_EQ(std::chrono::duration_cast<std::chrono::seconds>(
                      mService->mPeriodicCollection.interval)
                      .count(),
              sysprop::periodicCollectionInterval().value());

    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);

    mService->terminate();

    ASSERT_FALSE(mService->mCollectionThread.joinable()) << "Collection thread did not terminate";
}

TEST_F(WatchdogPerfServiceTest, TestValidCollectionSequence) {
    ASSERT_NO_FATAL_FAILURE(startService());

    // #1 Boot-time collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Boot-time collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 Boot-time collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Subsequent boot-time collection didn't happen at "
            << kTestSystemEventCollectionInterval.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Post system event collection - boot-time
    int maxIterations = static_cast<int>(kTestPostSystemEventDuration.count() /
                                         kTestSystemEventCollectionInterval.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    ASSERT_RESULT_OK(mService->onBootFinished());

    // Poll all post system event collections - boot-time except last
    for (int i = 0; i < maxIterations - 1; i++) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
                << "Subsequent post boot-time collection didn't happen at "
                << kTestSystemEventCollectionInterval.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last post system event collection - boot-time. The last boot-time collection should
    // switch to periodic collection.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Last boot-time collection didn't happen immediately after sending "
            << "END_BOOTTIME_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #4 Periodic monitor
    EXPECT_CALL(*mMockProcDiskStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onPeriodicMonitor(_, Eq(mMockProcDiskStatsCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestPeriodicMonitorInterval.count())
            << "First periodic monitor didn't happen at " << kTestPeriodicMonitorInterval.count()
            << " seconds interval";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #5 Periodic monitor
    EXPECT_CALL(*mMockProcDiskStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onPeriodicMonitor(_, Eq(mMockProcDiskStatsCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestPeriodicMonitorInterval.count())
            << "Second periodic monitor didn't happen at " << kTestPeriodicMonitorInterval.count()
            << " seconds interval";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #6 Periodic collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 1)
            << "First periodic collection didn't happen at 1 second interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #7 Custom collection
    Vector<String16> args;
    args.push_back(String16(kStartCustomCollectionFlag));
    args.push_back(String16(kIntervalFlag));
    args.push_back(String16(std::to_string(kTestCustomCollectionInterval.count()).c_str()));
    args.push_back(String16(kMaxDurationFlag));
    args.push_back(String16(std::to_string(kTestCustomCollectionDuration.count()).c_str()));
    ASSERT_RESULT_OK(mService->onCustomCollection(-1, args));

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onCustomCollection(_, SystemState::NORMAL_MODE, _, Eq(mMockUidStatsCollector),
                                   Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0) << "Custom collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #8 Custom collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onCustomCollection(_, SystemState::NORMAL_MODE, _, Eq(mMockUidStatsCollector),
                                   Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestCustomCollectionInterval.count())
            << "Subsequent custom collection didn't happen at "
            << kTestCustomCollectionInterval.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #9 End custom collection
    TemporaryFile customDump;
    {
        InSequence s;
        EXPECT_CALL(*mMockDataProcessor, onCustomCollectionDump(customDump.fd)).Times(1);
        EXPECT_CALL(*mMockDataProcessor, onCustomCollectionDump(-1)).Times(1);
    }

    args.clear();
    args.push_back(String16(kEndCustomCollectionFlag));
    ASSERT_RESULT_OK(mService->onCustomCollection(customDump.fd, args));
    ASSERT_RESULT_OK(mLooperStub->pollCache());
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";

    // #10 Switch to periodic collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Periodic collection didn't start immediately after ending custom collection";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #11 Periodic monitor.
    EXPECT_CALL(*mMockProcDiskStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onPeriodicMonitor(_, Eq(mMockProcDiskStatsCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestPeriodicMonitorInterval.count());
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);
}

TEST_F(WatchdogPerfServiceTest, TestCollectionTerminatesOnZeroEnabledCollectors) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ON_CALL(*mMockUidStatsCollector, enabled()).WillByDefault(Return(false));
    ON_CALL(*mMockProcStatCollector, enabled()).WillByDefault(Return(false));

    // Collection should terminate and call data processor's terminate method on error.
    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mServicePeer->joinCollectionThread().wait_for(1s), std::future_status::ready)
            << "Collection thread didn't terminate within 1 second.";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::TERMINATED);
}

TEST_F(WatchdogPerfServiceTest, TestCollectionTerminatesOnDataCollectorError) {
    ASSERT_NO_FATAL_FAILURE(startService());

    // Inject data collector error.
    Result<void> errorRes = Error() << "Failed to collect data";
    EXPECT_CALL(*mMockUidStatsCollector, collect()).WillOnce(Return(errorRes));

    // Collection should terminate and call data processor's terminate method on error.
    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mServicePeer->joinCollectionThread().wait_for(1s), std::future_status::ready)
            << "Collection thread didn't terminate within 1 second.";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::TERMINATED);
}

TEST_F(WatchdogPerfServiceTest, TestCollectionTerminatesOnDataProcessorError) {
    ASSERT_NO_FATAL_FAILURE(startService());

    // Inject data processor error.
    Result<void> errorRes = Error() << "Failed to process data";
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .WillOnce(Return(errorRes));

    // Collection should terminate and call data processor's terminate method on error.
    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mServicePeer->joinCollectionThread().wait_for(1s), std::future_status::ready)
            << "Collection thread didn't terminate within 1 second.";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::TERMINATED);
}

TEST_F(WatchdogPerfServiceTest, TestBoottimeCollectionWithNoPostSystemEventDuration) {
    ASSERT_NO_FATAL_FAILURE(startService());

    mServicePeer->clearPostSystemEventDuration();

    // #1 Boot-time collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Boot-time collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 Boot-time collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Subsequent boot-time collection didn't happen at "
            << kTestSystemEventCollectionInterval.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Last boot-time collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onBootFinished());

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Last boot-time collection didn't happen immediately after receiving boot complete "
            << "notification";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestCustomCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    // Start custom collection with filter packages option.
    Vector<String16> args;
    args.push_back(String16(kStartCustomCollectionFlag));
    args.push_back(String16(kIntervalFlag));
    args.push_back(String16(std::to_string(kTestCustomCollectionInterval.count()).c_str()));
    args.push_back(String16(kMaxDurationFlag));
    args.push_back(String16(std::to_string(kTestCustomCollectionDuration.count()).c_str()));
    args.push_back(String16(kFilterPackagesFlag));
    args.push_back(String16("android.car.cts,system_server"));
    ASSERT_RESULT_OK(mService->onCustomCollection(-1, args));

    // Poll until custom collection auto terminates.
    int maxIterations = static_cast<int>(kTestCustomCollectionDuration.count() /
                                         kTestCustomCollectionInterval.count());
    for (int i = 0; i <= maxIterations; ++i) {
        EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
        EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
        EXPECT_CALL(*mMockDataProcessor,
                    onCustomCollection(_, SystemState::NORMAL_MODE,
                                       UnorderedElementsAreArray(
                                               {"android.car.cts", "system_server"}),
                                       Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
                .Times(1);

        ASSERT_RESULT_OK(mLooperStub->pollCache());

        int secondsElapsed = (i == 0 ? 0 : kTestCustomCollectionInterval.count());
        ASSERT_EQ(mLooperStub->numSecondsElapsed(), secondsElapsed)
                << "Custom collection didn't happen at " << secondsElapsed
                << " seconds interval in iteration " << i;
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
                << "Invalid collection event";
        ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
    }

    EXPECT_CALL(*mMockDataProcessor, onCustomCollectionDump(-1)).Times(1);

    // Next looper message was injected during startCustomCollection to end the custom collection
    // after |kTestCustomCollectionDuration|. On processing this message, the custom collection
    // should auto terminate.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(),
              kTestCustomCollectionDuration.count() % kTestCustomCollectionInterval.count())
            << "Custom collection did't end after " << kTestCustomCollectionDuration.count()
            << " seconds";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);
}

TEST_F(WatchdogPerfServiceTest, TestUserSwitchCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    userid_t fromUserId = 0;
    userid_t toUserId = 100;

    // #1 Start user switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_SWITCHING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 User switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Subsequent user switch collection didn't happen at "
            << kTestSystemEventCollectionInterval.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Post system event collection - user switch
    int maxIterations = static_cast<int>(kTestPostSystemEventDuration.count() /
                                         kTestSystemEventCollectionInterval.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_POST_UNLOCKED));

    // Poll all post user switch collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
                << "Subsequent post system event collection - user switch didn't happen at "
                << kTestSystemEventCollectionInterval.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last post system event collection - user switch. The last user switch collection
    // event should switch to periodic collection.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Last user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestUserSwitchCollectionWithDelayedUnlocking) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    userid_t fromUserId = 0;
    userid_t toUserId = 100;

    // #1 Start user switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_SWITCHING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 User switch collections before timeout
    int maxIterations = static_cast<int>(kTestUserSwitchTimeout.count() /
                                         kTestSystemEventCollectionInterval.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    // Poll all user switch collections except last
    for (int i = 0; i < maxIterations - 1; i++) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
                << "Subsequent user switch collection didn't happen at "
                << kTestSystemEventCollectionInterval.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last user switch collection. The last user switch collection event should start
    // periodic collection.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Last user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Start user switch collection with unlocking signal
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_UNLOCKING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #4 User switch collections after unlocking
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Subsequent user switch collection didn't happen at "
            << kTestSystemEventCollectionInterval.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #5 Post system event collection - user switch
    maxIterations = static_cast<int>(kTestPostSystemEventDuration.count() /
                                     kTestSystemEventCollectionInterval.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_POST_UNLOCKED));

    // Poll all post user switch collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
                << "Subsequent post user switch collection didn't happen at "
                << kTestSystemEventCollectionInterval.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last post user switch collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Last user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestUserSwitchEventDuringUserSwitchCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    userid_t fromUserId = 0;
    userid_t toUserId = 100;

    // #1 Start user switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(2);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(2);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(2);

    ASSERT_RESULT_OK(mService->onUserStateChange(toUserId, UserState::USER_STATE_SWITCHING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";

    // #2 User switch collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Subsequent user switch collection didn't happen at "
            << kTestSystemEventCollectionInterval.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Start new user switch collection during prev user switch event
    userid_t newFromUserId = 100;
    userid_t newToUserId = 101;

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(newFromUserId), Eq(newToUserId),
                                       Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(newToUserId, UserState::USER_STATE_SWITCHING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "New user switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #4 New user switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(newFromUserId), Eq(newToUserId),
                                       Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Subsequent new user switch collection didn't happen at "
            << kTestSystemEventCollectionInterval.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #5 Post system event collection - new user switch
    int maxIterations = static_cast<int>(kTestPostSystemEventDuration.count() /
                                         kTestSystemEventCollectionInterval.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(newFromUserId), Eq(newToUserId),
                                       Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    ASSERT_RESULT_OK(mService->onUserStateChange(newToUserId, UserState::USER_STATE_POST_UNLOCKED));

    // Poll all post user switch collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
                << "Subsequent post system event collection -  new user switch didn't happen at "
                << kTestSystemEventCollectionInterval.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last post system event collection - user switch. The last user switch collection
    // event should switch to periodic collection.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Last new user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestUserSwitchCollectionWithTwoTimeouts) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    userid_t fromUserId = 0;
    userid_t toUserId = 100;

    // #1 Start user switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_SWITCHING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 User switch collections before timeout
    int maxIterations = static_cast<int>(kTestUserSwitchTimeout.count() /
                                         kTestSystemEventCollectionInterval.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    // Poll all user switch collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
                << "Subsequent post user switch collection didn't happen at "
                << kTestSystemEventCollectionInterval.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last user switch collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Last user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Start user switch collection with unlocking signal
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_UNLOCKING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #4 User switch collections after unlocking
    maxIterations = static_cast<int>(kTestUserSwitchTimeout.count() /
                                     kTestSystemEventCollectionInterval.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    // Poll all post user switch collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
                << "Subsequent post user switch collection didn't happen at "
                << kTestSystemEventCollectionInterval.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last post user switch collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Last user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestUserSwitchCollectionUserUnlockingWithNoPrevTimeout) {
    ASSERT_NO_FATAL_FAILURE(startService());
    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());
    ASSERT_NO_FATAL_FAILURE(skipPeriodicMonitorEvents());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector)))
            .Times(1);
    EXPECT_CALL(*mMockDataProcessor, onUserSwitchCollection(_, _, _, _, _)).Times(0);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_UNLOCKING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 1)
            << "First periodic collection didn't happen at 1 second interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestWakeUpCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    // #1 Wake up collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onSystemStartup()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onWakeUpCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onSuspendExit());

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0) << "Wake up collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::WAKE_UP_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 Wake up collections before duration expires
    int maxIterations = static_cast<int>(kTestWakeUpDuration.count() /
                                         kTestSystemEventCollectionInterval.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onWakeUpCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    // Poll all remaining wake up collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
                << "Subsequent wake up collection didn't happen at "
                << kTestSystemEventCollectionInterval.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::WAKE_UP_COLLECTION)
                << "Invalid collection event";
    }

    // Suspend exit signal should be ignored since already running wake up collection.
    ASSERT_RESULT_OK(mService->onSuspendExit());

    // Poll the last wake up collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionInterval.count())
            << "Last wake up collection didn't happen immediately after sending "
            << "END_WAKE_UP_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestWakeUpCollectionDuringCustomCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    // Start custom collection
    Vector<String16> args;
    args.push_back(String16(kStartCustomCollectionFlag));
    args.push_back(String16(kIntervalFlag));
    args.push_back(String16(std::to_string(kTestCustomCollectionInterval.count()).c_str()));
    args.push_back(String16(kMaxDurationFlag));
    args.push_back(String16(std::to_string(kTestCustomCollectionDuration.count()).c_str()));

    ASSERT_RESULT_OK(mService->onCustomCollection(-1, args));

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(2);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(2);
    EXPECT_CALL(*mMockDataProcessor,
                onCustomCollection(_, SystemState::NORMAL_MODE, _, Eq(mMockUidStatsCollector),
                                   Eq(mMockProcStatCollector)))
            .Times(2);
    EXPECT_CALL(*mMockDataProcessor,
                onWakeUpCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(0);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0) << "Custom collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";

    // Custom collection while suspend exit signal is received
    ASSERT_RESULT_OK(mService->onSuspendExit());

    // Continued custom collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestCustomCollectionInterval.count())
            << "Subsequent custom collection didn't happen at "
            << kTestCustomCollectionInterval.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestPeriodicMonitorRequestsCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    // Periodic monitor issuing an alert to start new collection.
    EXPECT_CALL(*mMockProcDiskStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onPeriodicMonitor(_, Eq(mMockProcDiskStatsCollector), _))
            .WillOnce([&](auto, auto, const auto& alertHandler) -> Result<void> {
                alertHandler();
                return {};
            });

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestPeriodicMonitorInterval.count())
            << "First periodic monitor didn't happen at " << kTestPeriodicMonitorInterval.count()
            << " seconds interval";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "First periodic collection didn't happen immediately after the alert";

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);
}

TEST_F(WatchdogPerfServiceTest, TestShutdownEnter) {
    ASSERT_NO_FATAL_FAILURE(startService());

    // Start boot-time collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Boot-time collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    ASSERT_RESULT_OK(mService->onShutdownEnter());

    // Switch to periodic collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Periodic collection didn't start immediately after receiving shutdown enter signal";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestShutdownEnterWithCustomCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    // Start custom collection
    Vector<String16> args;
    args.push_back(String16(kStartCustomCollectionFlag));
    args.push_back(String16(kIntervalFlag));
    args.push_back(String16(std::to_string(kTestCustomCollectionInterval.count()).c_str()));
    args.push_back(String16(kMaxDurationFlag));
    args.push_back(String16(std::to_string(kTestCustomCollectionDuration.count()).c_str()));

    ASSERT_RESULT_OK(mService->onCustomCollection(-1, args));

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onCustomCollection(_, SystemState::NORMAL_MODE, _, Eq(mMockUidStatsCollector),
                                   Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0) << "Custom collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // Suspend in middle of custom collection
    ASSERT_RESULT_OK(mService->onShutdownEnter());

    // Custom collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onCustomCollection(_, SystemState::NORMAL_MODE, _, Eq(mMockUidStatsCollector),
                                   Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestCustomCollectionInterval.count())
            << "Subsequent custom collection didn't happen at "
            << kTestCustomCollectionInterval.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestSystemStateSwitch) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());
    ASSERT_NO_FATAL_FAILURE(skipPeriodicMonitorEvents());

    EXPECT_CALL(*mMockDataProcessor, onPeriodicCollection(_, SystemState::NORMAL_MODE, _, _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    ASSERT_NO_FATAL_FAILURE(skipPeriodicMonitorEvents());

    mService->setSystemState(SystemState::GARAGE_MODE);

    EXPECT_CALL(*mMockDataProcessor, onPeriodicCollection(_, SystemState::GARAGE_MODE, _, _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    ASSERT_NO_FATAL_FAILURE(skipPeriodicMonitorEvents());

    mService->setSystemState(SystemState::NORMAL_MODE);

    EXPECT_CALL(*mMockDataProcessor, onPeriodicCollection(_, SystemState::NORMAL_MODE, _, _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);
}

TEST_F(WatchdogPerfServiceTest, TestHandlesInvalidDumpArguments) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    Vector<String16> args;
    args.push_back(String16(kStartCustomCollectionFlag));
    args.push_back(String16("Invalid flag"));
    args.push_back(String16("Invalid value"));
    ASSERT_FALSE(mService->onCustomCollection(-1, args).ok());

    args.clear();
    args.push_back(String16(kStartCustomCollectionFlag));
    args.push_back(String16(kIntervalFlag));
    args.push_back(String16("Invalid interval"));
    ASSERT_FALSE(mService->onCustomCollection(-1, args).ok());

    args.clear();
    args.push_back(String16(kStartCustomCollectionFlag));
    args.push_back(String16(kMaxDurationFlag));
    args.push_back(String16("Invalid duration"));
    ASSERT_FALSE(mService->onCustomCollection(-1, args).ok());

    args.clear();
    args.push_back(String16(kEndCustomCollectionFlag));
    args.push_back(String16(kMaxDurationFlag));
    args.push_back(String16(std::to_string(kTestCustomCollectionDuration.count()).c_str()));
    ASSERT_FALSE(mService->onCustomCollection(-1, args).ok());

    args.clear();
    args.push_back(String16("Invalid flag"));
    ASSERT_FALSE(mService->onCustomCollection(-1, args).ok());
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

/*
 * Copyright 2018 The Android Open Source Project
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
#undef LOG_TAG
#define LOG_TAG "SchedulerUnittests"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <utils/Log.h>
#include <utils/Timers.h>

#include "AsyncCallRecorder.h"
#include "Scheduler/OneShotTimer.h"
#include "fake/FakeClock.h"

using namespace std::chrono_literals;

namespace android {
namespace scheduler {

class OneShotTimerTest : public testing::Test {
protected:
    OneShotTimerTest() = default;
    ~OneShotTimerTest() override = default;

    AsyncCallRecorder<void (*)()> mResetTimerCallback;
    AsyncCallRecorder<void (*)()> mExpiredTimerCallback;

    std::unique_ptr<OneShotTimer> mIdleTimer;

    void clearPendingCallbacks() {
        while (mExpiredTimerCallback.waitForCall(0us).has_value()) {
        }
    }
};

namespace {
TEST_F(OneShotTimerTest, createAndDestroyTest) {
    fake::FakeClock* clock = new fake::FakeClock();
    mIdleTimer = std::make_unique<scheduler::OneShotTimer>(
            "TestTimer", 3ms, [] {}, [] {}, std::unique_ptr<fake::FakeClock>(clock));
}

TEST_F(OneShotTimerTest, startStopTest) {
    fake::FakeClock* clock = new fake::FakeClock();
    mIdleTimer = std::make_unique<scheduler::OneShotTimer>("TestTimer", 1ms,
                                                           mResetTimerCallback.getInvocable(),
                                                           mExpiredTimerCallback.getInvocable(),
                                                           std::unique_ptr<fake::FakeClock>(clock));
    mIdleTimer->start();
    EXPECT_TRUE(mResetTimerCallback.waitForCall().has_value());
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());

    clock->advanceTime(2ms);
    EXPECT_TRUE(mExpiredTimerCallback.waitForCall().has_value());

    clock->advanceTime(2ms);
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
    mIdleTimer->stop();
}

// TODO(b/186417847) This test is flaky. Reenable once fixed.
TEST_F(OneShotTimerTest, DISABLED_resetTest) {
    fake::FakeClock* clock = new fake::FakeClock();
    mIdleTimer = std::make_unique<scheduler::OneShotTimer>("TestTimer", 1ms,
                                                           mResetTimerCallback.getInvocable(),
                                                           mExpiredTimerCallback.getInvocable(),
                                                           std::unique_ptr<fake::FakeClock>(clock));

    mIdleTimer->start();
    EXPECT_TRUE(mResetTimerCallback.waitForCall().has_value());
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    clock->advanceTime(2ms);
    EXPECT_TRUE(mExpiredTimerCallback.waitForCall().has_value());
    mIdleTimer->reset();
    EXPECT_TRUE(mResetTimerCallback.waitForCall().has_value());
    clock->advanceTime(2ms);
    EXPECT_TRUE(mExpiredTimerCallback.waitForCall().has_value());

    clock->advanceTime(2ms);
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
}

// TODO(b/186417847) This test is flaky. Reenable once fixed.
TEST_F(OneShotTimerTest, DISABLED_resetBackToBackTest) {
    fake::FakeClock* clock = new fake::FakeClock();
    mIdleTimer = std::make_unique<scheduler::OneShotTimer>("TestTimer", 1ms,
                                                           mResetTimerCallback.getInvocable(),
                                                           mExpiredTimerCallback.getInvocable(),
                                                           std::unique_ptr<fake::FakeClock>(clock));
    mIdleTimer->start();
    EXPECT_TRUE(mResetTimerCallback.waitForCall().has_value());

    mIdleTimer->reset();
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());

    mIdleTimer->reset();
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());

    mIdleTimer->reset();
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());

    mIdleTimer->reset();
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());

    clock->advanceTime(2ms);
    EXPECT_TRUE(mExpiredTimerCallback.waitForCall().has_value());

    mIdleTimer->stop();
    clock->advanceTime(2ms);
    // Final quick check that no more callback were observed.
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
}

// TODO(b/186417847) This test is new and passes locally, but may be flaky
TEST_F(OneShotTimerTest, DISABLED_resetBackToBackSlowAdvanceTest) {
    fake::FakeClock* clock = new fake::FakeClock();
    mIdleTimer = std::make_unique<scheduler::OneShotTimer>("TestTimer", 1ms,
                                                           mResetTimerCallback.getInvocable(),
                                                           mExpiredTimerCallback.getInvocable(),
                                                           std::unique_ptr<fake::FakeClock>(clock));
    mIdleTimer->start();
    EXPECT_TRUE(mResetTimerCallback.waitForCall().has_value());

    mIdleTimer->reset();
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());

    clock->advanceTime(200us);
    mIdleTimer->reset();

    // Normally we would check that the timer callbacks weren't invoked here
    // after resetting the timer, but we need to precisely control the timing of
    // this test, and checking that callbacks weren't invoked requires non-zero
    // time.

    clock->advanceTime(1500us);
    EXPECT_TRUE(mExpiredTimerCallback.waitForCall(1100us).has_value());
    mIdleTimer->reset();
    EXPECT_TRUE(mResetTimerCallback.waitForCall().has_value());

    mIdleTimer->stop();
    clock->advanceTime(2ms);
    // Final quick check that no more callback were observed.
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
}

TEST_F(OneShotTimerTest, startNotCalledTest) {
    fake::FakeClock* clock = new fake::FakeClock();
    mIdleTimer = std::make_unique<scheduler::OneShotTimer>("TestTimer", 1ms,
                                                           mResetTimerCallback.getInvocable(),
                                                           mExpiredTimerCallback.getInvocable(),
                                                           std::unique_ptr<fake::FakeClock>(clock));
    // The start hasn't happened, so the callback does not happen.
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
    mIdleTimer->stop();
    clock->advanceTime(2ms);
    // Final quick check that no more callback were observed.
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
}

// TODO(b/186417847) This test is flaky. Reenable once fixed.
TEST_F(OneShotTimerTest, DISABLED_idleTimerIdlesTest) {
    fake::FakeClock* clock = new fake::FakeClock();
    mIdleTimer = std::make_unique<scheduler::OneShotTimer>("TestTimer", 1ms,
                                                           mResetTimerCallback.getInvocable(),
                                                           mExpiredTimerCallback.getInvocable(),
                                                           std::unique_ptr<fake::FakeClock>(clock));
    mIdleTimer->start();
    EXPECT_TRUE(mResetTimerCallback.waitForCall().has_value());
    clock->advanceTime(2ms);
    EXPECT_TRUE(mExpiredTimerCallback.waitForCall().has_value());

    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());

    mIdleTimer->reset();
    EXPECT_TRUE(mResetTimerCallback.waitForCall().has_value());
    clock->advanceTime(2ms);
    EXPECT_TRUE(mExpiredTimerCallback.waitForCall().has_value());
    mIdleTimer->stop();
    clock->advanceTime(2ms);
    // Final quick check that no more callback were observed.
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
}

// TODO(b/186417847) This test is flaky. Reenable once fixed.
TEST_F(OneShotTimerTest, DISABLED_timeoutCallbackExecutionTest) {
    fake::FakeClock* clock = new fake::FakeClock();
    mIdleTimer = std::make_unique<scheduler::OneShotTimer>("TestTimer", 1ms,
                                                           mResetTimerCallback.getInvocable(),
                                                           mExpiredTimerCallback.getInvocable(),
                                                           std::unique_ptr<fake::FakeClock>(clock));
    mIdleTimer->start();
    EXPECT_TRUE(mResetTimerCallback.waitForCall().has_value());

    clock->advanceTime(2ms);
    EXPECT_TRUE(mExpiredTimerCallback.waitForCall().has_value());
    mIdleTimer->stop();
    clock->advanceTime(2ms);
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
}

TEST_F(OneShotTimerTest, noCallbacksAfterStopAndResetTest) {
    fake::FakeClock* clock = new fake::FakeClock();
    mIdleTimer = std::make_unique<scheduler::OneShotTimer>("TestTimer", 1ms,
                                                           mResetTimerCallback.getInvocable(),
                                                           mExpiredTimerCallback.getInvocable(),
                                                           std::unique_ptr<fake::FakeClock>(clock));
    mIdleTimer->start();
    EXPECT_TRUE(mResetTimerCallback.waitForCall().has_value());
    clock->advanceTime(2ms);
    EXPECT_TRUE(mExpiredTimerCallback.waitForCall().has_value());

    mIdleTimer->stop();
    mIdleTimer->reset();
    clock->advanceTime(2ms);
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
}

TEST_F(OneShotTimerTest, noCallbacksAfterStopTest) {
    fake::FakeClock* clock = new fake::FakeClock();
    mIdleTimer = std::make_unique<scheduler::OneShotTimer>("TestTimer", 1ms,
                                                           mResetTimerCallback.getInvocable(),
                                                           mExpiredTimerCallback.getInvocable(),
                                                           std::unique_ptr<fake::FakeClock>(clock));
    mIdleTimer->start();
    EXPECT_TRUE(mResetTimerCallback.waitForCall().has_value());
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());

    mIdleTimer->stop();
    mIdleTimer->reset();

    clock->advanceTime(2ms);
    // No more idle events should be observed
    EXPECT_FALSE(mExpiredTimerCallback.waitForUnexpectedCall().has_value());
    EXPECT_FALSE(mResetTimerCallback.waitForUnexpectedCall().has_value());
}

} // namespace
} // namespace scheduler
} // namespace android

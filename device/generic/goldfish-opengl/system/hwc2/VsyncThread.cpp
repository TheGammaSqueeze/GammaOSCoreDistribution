/*
 * Copyright 2021 The Android Open Source Project
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

#include "VsyncThread.h"

#include <thread>

namespace android {
namespace {

std::chrono::time_point<std::chrono::steady_clock> asTimePoint(int64_t nanos) {
  return std::chrono::time_point<std::chrono::steady_clock>(
      std::chrono::nanoseconds(nanos));
}

hwc2_vsync_period_t asNanos(std::chrono::nanoseconds duration) {
  return duration.count();
}

int64_t asNanos(std::chrono::time_point<std::chrono::steady_clock> time) {
  std::chrono::time_point<std::chrono::steady_clock> zero(
      std::chrono::nanoseconds(0));
  return std::chrono::duration_cast<std::chrono::nanoseconds>(time - zero)
      .count();
}

// Returns the timepoint of the next vsync after the 'now' timepoint that is
// a multiple of 'vsyncPeriod' in-phase/offset-from 'previousSync'.
//
// Some examples:
//  * vsyncPeriod=50ns previousVsync=500ns now=510ns => 550ns
//  * vsyncPeriod=50ns previousVsync=300ns now=510ns => 550ns
//  * vsyncPeriod=50ns previousVsync=500ns now=550ns => 550ns
std::chrono::time_point<std::chrono::steady_clock> GetNextVsyncInPhase(
    std::chrono::nanoseconds vsyncPeriod,
    std::chrono::time_point<std::chrono::steady_clock> previousVsync,
    std::chrono::time_point<std::chrono::steady_clock> now) {
  const auto elapsed = std::chrono::nanoseconds(now - previousVsync);
  const auto nextMultiple = (elapsed / vsyncPeriod) + 1;
  return previousVsync + (nextMultiple * vsyncPeriod);
}

}  // namespace

VsyncThread::VsyncThread(hwc2_display_t id) : mDisplayId(id) {
  mPreviousVsync = std::chrono::steady_clock::now() - mVsyncPeriod;
}

HWC2::Error VsyncThread::start(hwc2_vsync_period_t vsyncPeriod) {
  DEBUG_LOG("%s for display:%" PRIu64, __FUNCTION__, mDisplayId);

  mVsyncPeriod = std::chrono::nanoseconds(vsyncPeriod);

  const std::string threadName =
      "display_" + std::to_string(mDisplayId) + "_vsync_thread";
  this->run(threadName.c_str(), ANDROID_PRIORITY_URGENT_DISPLAY);

  return HWC2::Error::None;
}

HWC2::Error VsyncThread::setVsyncCallback(HWC2_PFN_VSYNC callback,
                                          hwc2_callback_data_t callbackData) {
  DEBUG_LOG("%s for display:%" PRIu64, __FUNCTION__, mDisplayId);

  std::unique_lock<std::mutex> lock(mStateMutex);

  mVsyncCallback = callback;
  mVsyncCallbackData = callbackData;

  return HWC2::Error::None;
}

HWC2::Error VsyncThread::setVsync24Callback(HWC2_PFN_VSYNC_2_4 callback,
                                            hwc2_callback_data_t callbackData) {
  DEBUG_LOG("%s for display:%" PRIu64, __FUNCTION__, mDisplayId);

  std::unique_lock<std::mutex> lock(mStateMutex);

  mVsync24Callback = callback;
  mVsync24CallbackData = callbackData;

  return HWC2::Error::None;
}

HWC2::Error VsyncThread::setVsyncEnabled(bool enabled) {
  DEBUG_LOG("%s for display:%" PRIu64 " enabled:%d", __FUNCTION__, mDisplayId,
            enabled);

  std::unique_lock<std::mutex> lock(mStateMutex);

  mVsyncEnabled = enabled;

  return HWC2::Error::None;
}

HWC2::Error VsyncThread::scheduleVsyncUpdate(
    hwc2_vsync_period_t newVsyncPeriod,
    hwc_vsync_period_change_constraints_t* newVsyncPeriodConstraints,
    hwc_vsync_period_change_timeline_t* outTimeline) {
  DEBUG_LOG("%s for display:%" PRIu64, __FUNCTION__, mDisplayId);

  PendingUpdate update;
  update.period = std::chrono::nanoseconds(newVsyncPeriod);
  update.updateAfter = asTimePoint(newVsyncPeriodConstraints->desiredTimeNanos);

  std::unique_lock<std::mutex> lock(mStateMutex);
  mPendingUpdate.emplace(std::move(update));

  auto nextVsync =
      GetNextVsyncInPhase(mVsyncPeriod, mPreviousVsync, update.updateAfter);

  outTimeline->newVsyncAppliedTimeNanos = asNanos(nextVsync);
  outTimeline->refreshRequired = false;
  outTimeline->refreshTimeNanos = 0;

  return HWC2::Error::None;
}

std::chrono::nanoseconds VsyncThread::updateVsyncPeriodLocked(
    std::chrono::time_point<std::chrono::steady_clock> now) {
  if (mPendingUpdate && now > mPendingUpdate->updateAfter) {
    mVsyncPeriod = mPendingUpdate->period;
    mPendingUpdate.reset();
  }

  return mVsyncPeriod;
}

bool VsyncThread::threadLoop() {
  DEBUG_LOG("%s: for display:%" PRIu64 " started", __FUNCTION__, mDisplayId);

  std::chrono::nanoseconds vsyncPeriod = mVsyncPeriod;

  int vsyncs = 0;
  auto previousLog = std::chrono::steady_clock::now();
  while (true) {
    auto now = std::chrono::steady_clock::now();

    auto nextVsync = GetNextVsyncInPhase(vsyncPeriod, mPreviousVsync, now);
    std::this_thread::sleep_until(nextVsync);

    {
      std::unique_lock<std::mutex> lock(mStateMutex);

      mPreviousVsync = nextVsync;

      // Display has finished refreshing at previous vsync period. Update the
      // vsync period if there was a pending update.
      vsyncPeriod = updateVsyncPeriodLocked(mPreviousVsync);
    }

    if (mVsyncEnabled) {
      if (mVsync24Callback) {
        DEBUG_LOG("%s: for display:%" PRIu64 " calling vsync_2_4", __FUNCTION__,
                  mDisplayId);
        mVsync24Callback(mVsync24CallbackData, mDisplayId, asNanos(nextVsync),
                         asNanos(vsyncPeriod));
      } else if (mVsyncCallback) {
        DEBUG_LOG("%s: for display:%" PRIu64 " calling vsync", __FUNCTION__,
                  mDisplayId);
        mVsyncCallback(mVsyncCallbackData, mDisplayId, asNanos(nextVsync));
      }
    }

    static constexpr const int kLogIntervalSeconds = 60;
    if (now > (previousLog + std::chrono::seconds(kLogIntervalSeconds))) {
      DEBUG_LOG("%s: for display:%" PRIu64 " send %" PRIu32
                " in last %d seconds",
                __FUNCTION__, mDisplayId, vsyncs, kLogIntervalSeconds);
      previousLog = now;
      vsyncs = 0;
    }
    ++vsyncs;
  }

  DEBUG_LOG("%s: for display:%" PRIu64 " started", __FUNCTION__, mDisplayId);
  return false;
}

}  // namespace android

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

#ifndef ANDROID_HWC_VSYNCTHREAD_H
#define ANDROID_HWC_VSYNCTHREAD_H

#include <android/hardware/graphics/common/1.0/types.h>
#include <utils/Thread.h>

#include <chrono>
#include <mutex>
#include <optional>

#include "Common.h"

namespace android {

// Generates Vsync signals in software.
class VsyncThread : public Thread {
 public:
  VsyncThread(hwc2_display_t id);
  virtual ~VsyncThread() {}

  VsyncThread(const VsyncThread&) = default;
  VsyncThread& operator=(const VsyncThread&) = default;

  VsyncThread(VsyncThread&&) = default;
  VsyncThread& operator=(VsyncThread&&) = default;

  HWC2::Error start(hwc2_vsync_period_t period);

  HWC2::Error setVsyncCallback(HWC2_PFN_VSYNC callback,
                               hwc2_callback_data_t callbackData);
  HWC2::Error setVsync24Callback(HWC2_PFN_VSYNC_2_4 callback,
                                 hwc2_callback_data_t callbackData);

  HWC2::Error setVsyncEnabled(bool enabled);

  HWC2::Error scheduleVsyncUpdate(
      hwc2_vsync_period_t newVsyncPeriod,
      hwc_vsync_period_change_constraints_t* newVsyncPeriodChangeConstraints,
      hwc_vsync_period_change_timeline_t* timeline);

 private:
  bool threadLoop() final;

  std::chrono::nanoseconds updateVsyncPeriodLocked(
      std::chrono::time_point<std::chrono::steady_clock> now);

  const hwc2_display_t mDisplayId;

  std::mutex mStateMutex;

  HWC2_PFN_VSYNC mVsyncCallback;
  hwc2_callback_data_t mVsyncCallbackData = nullptr;

  HWC2_PFN_VSYNC_2_4 mVsync24Callback;
  hwc2_callback_data_t mVsync24CallbackData = nullptr;

  bool mVsyncEnabled = false;
  std::chrono::nanoseconds mVsyncPeriod;
  std::chrono::time_point<std::chrono::steady_clock> mPreviousVsync;

  struct PendingUpdate {
    std::chrono::nanoseconds period;
    std::chrono::time_point<std::chrono::steady_clock> updateAfter;
  };
  std::optional<PendingUpdate> mPendingUpdate;
};

}  // namespace android

#endif
/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)

#define LOG_TAG "hwc-vsync-worker"

#include "vsyncworker.h"

#include <hardware/hardware.h>
#include <log/log.h>
#include <stdlib.h>
#include <time.h>
#include <utils/Trace.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <map>

#include "drmdevice.h"
#include "worker.h"

using namespace std::chrono_literals;

constexpr auto nsecsPerSec = std::chrono::nanoseconds(1s).count();
constexpr auto hwVsyncPeriodTag = "HWVsyncPeriod";

namespace android {

VSyncWorker::VSyncWorker()
    : Worker("vsync", 2, true),
      drm_(NULL),
      display_(-1),
      enabled_(false),
      last_timestamp_(-1) {
}

VSyncWorker::~VSyncWorker() {
    Exit();
}

int VSyncWorker::Init(DrmDevice *drm, int display) {
    drm_ = drm;
    display_ = display;

    return InitWorker();
}

void VSyncWorker::RegisterCallback(std::shared_ptr<VsyncCallback> callback) {
    Lock();
    callback_ = callback;
    Unlock();
}

void VSyncWorker::VSyncControl(bool enabled) {
    Lock();
    enabled_ = enabled;
    last_timestamp_ = -1;
    Unlock();

    ATRACE_INT("HWCVsync", static_cast<int32_t>(enabled));
    ATRACE_INT64(hwVsyncPeriodTag, 0);
    Signal();
}

/*
 * Returns the timestamp of the next vsync in phase with last_timestamp_.
 * For example:
 *  last_timestamp_ = 137
 *  frame_ns = 50
 *  current = 683
 *
 *  expect = (50 * ((683 - 137)/50 + 1)) + 137
 *  expect = 687
 *
 *  Thus, we must sleep until timestamp 687 to maintain phase with the last
 *  timestamp. But if we don't know last vblank timestamp, sleep one vblank
 *  then try to get vblank from driver again.
 */
int VSyncWorker::GetPhasedVSync(int64_t frame_ns, int64_t &expect) {
    struct timespec now;
    if (clock_gettime(CLOCK_MONOTONIC, &now)) {
        ALOGE("clock_gettime failed %d", errno);
        return -EPERM;
    }

    int64_t current = now.tv_sec * nsecsPerSec + now.tv_nsec;
    if (last_timestamp_ < 0) {
        expect = current + frame_ns;
        return -EAGAIN;
    }

    expect = frame_ns * ((current - last_timestamp_) / frame_ns + 1) + last_timestamp_;

    return 0;
}

int VSyncWorker::SyntheticWaitVBlank(int64_t &timestamp) {
    float refresh = 60.0f; // Default to 60Hz refresh rate

    DrmConnector *conn = drm_->GetConnectorForDisplay(display_);
    if (conn && conn->active_mode().v_refresh() != 0.0f) {
        refresh = conn->active_mode().v_refresh();
    } else {
        ALOGW("Vsync worker active with conn=%p refresh=%f\n", conn,
              conn ? conn->active_mode().v_refresh() : 0.0f);
    }

    int64_t phased_timestamp;
    int ret = GetPhasedVSync(nsecsPerSec / refresh, phased_timestamp);
    if (ret && ret != -EAGAIN) return -1;

    struct timespec vsync;
    vsync.tv_sec = phased_timestamp / nsecsPerSec;
    vsync.tv_nsec = phased_timestamp % nsecsPerSec;

    int err;
    do {
        err = clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &vsync, nullptr);
    } while (err == EINTR);
    if (err || ret) return -1;

    timestamp = (int64_t)vsync.tv_sec * nsecsPerSec + (int64_t)vsync.tv_nsec;

    return 0;
}

void VSyncWorker::Routine() {
    int ret;

    Lock();
    if (!enabled_) {
        ret = WaitForSignalOrExitLocked();
        if (ret == -EINTR) {
            Unlock();
            return;
        }
    }

    int display = display_;
    std::shared_ptr<VsyncCallback> callback(callback_);
    Unlock();

    DrmCrtc *crtc = drm_->GetCrtcForDisplay(display);
    if (!crtc) {
        ALOGE("Failed to get crtc for display");
        return;
    }
    uint32_t high_crtc = (crtc->pipe() << DRM_VBLANK_HIGH_CRTC_SHIFT);

    drmVBlank vblank;
    memset(&vblank, 0, sizeof(vblank));
    vblank.request.type =
            (drmVBlankSeqType)(DRM_VBLANK_RELATIVE | (high_crtc & DRM_VBLANK_HIGH_CRTC_MASK));
    vblank.request.sequence = 1;

    int64_t timestamp;
    ret = drmWaitVBlank(drm_->fd(), &vblank);
    if (ret) {
        if (SyntheticWaitVBlank(timestamp)) {
            // postpone the callback until we get a real value from the hardware
            return;
        }
    } else {
        timestamp = (int64_t)vblank.reply.tval_sec * nsecsPerSec +
                (int64_t)vblank.reply.tval_usec * 1000;
    }

    /*
     * VSync could be disabled during routine execution so it could potentially
     * lead to crash since callback's inner hook could be invalid anymore. We have
     * no control over lifetime of this hook, therefore we can't rely that it'll
     * be valid after vsync disabling.
     *
     * Blocking VSyncControl to wait until routine
     * will finish execution is logically correct way to fix this issue, but it
     * creates visible lags and stutters, so we have to resort to other ways of
     * mitigating this issue.
     *
     * Doing check before attempt to invoke callback drastically shortens the
     * window when such situation could happen and that allows us to practically
     * avoid this issue.
     *
     * Please note that issue described below is different one and it is related
     * to RegisterCallback, not to disabling vsync via VSyncControl.
     */
    if (!enabled_) return;
    /*
     * There's a race here where a change in callback_ will not take effect until
     * the next subsequent requested vsync. This is unavoidable since we can't
     * call the vsync hook while holding the thread lock.
     *
     * We could shorten the race window by caching callback_ right before calling
     * the hook. However, in practice, callback_ is only updated once, so it's not
     * worth the overhead.
     */
    if (callback) callback->Callback(display, timestamp);

    if (last_timestamp_ >= 0) {
        int64_t period = timestamp - last_timestamp_;
        ATRACE_INT64(hwVsyncPeriodTag, period);
        ALOGV("HW vsync period %" PRId64 "ns", period);
    }

    last_timestamp_ = timestamp;
}
}  // namespace android

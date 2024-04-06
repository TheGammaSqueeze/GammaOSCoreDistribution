/*
 * Copyright 2021 Rockchip Electronics Co. LTD
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
 *
 * author: zj@rock-chips.com
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "OutFrameThread"

#include "OutFrameThread.h"
#include <pthread.h>
#include <sys/resource.h>
#include <utils/Log.h>

std::atomic<uint32_t> OutFrameThread::mNextThreadIndex{1};

OutFrameThread::OutFrameThread(const char *prefix) {
    setup(prefix);
}

OutFrameThread::OutFrameThread() {
    setup("OutFrame");
}

OutFrameThread::~OutFrameThread() {
    ALOGE_IF(pthread_equal(pthread_self(), mThread),
             "%s() destructor running in thread", __func__);
    ALOGE_IF(mHasThread, "%s() thread never joined", __func__);
}

void OutFrameThread::setup(const char *prefix) {
    // Name the thread with an increasing index, "prefix_#", for debugging.
    uint32_t index = mNextThreadIndex++;
    // Wrap the index so that we do not hit the 16 char limit
    // and to avoid hard-to-read large numbers.
    index = index % 100000; // arbitrary
    snprintf(mName, sizeof(mName), "%s_%u", prefix, index);
}

void OutFrameThread::dispatch() {
    if (mRunnable != nullptr) {
        // androidSetThreadPriority(gettid(), ANDROID_PRIORITY_HIGHEST);
        int ret = setpriority(PRIO_PROCESS, 0, -20);
        if (ret < 0) {
            ALOGE("failed to setpriority - %s err = %s", mName, strerror(errno));
        }
        mRunnable->run();
    } else {
        run();
    }
}

// This is the entry point for the new thread created by createThread_l().
// It converts the 'C' function call to a C++ method call.
static void *ThreadInternal(void *arg) {
    OutFrameThread *thread = (OutFrameThread *)arg;
    thread->dispatch();
    return nullptr;
}

bool OutFrameThread::start(Runnable *runnable) {
    if (mHasThread) {
        ALOGE("start() - mHasThread already true");
        return false;
    }
    // mRunnable will be read by the new thread when it starts.
    // pthread_create() forces a memory synchronization so mRunnable does not need
    // to be atomic.
    mRunnable = runnable;
    int err = pthread_create(&mThread, nullptr, ThreadInternal, this);
    if (err != 0) {
        ALOGE("start() - pthread_create() returned %d %s", err, strerror(err));
        return false;
    } else {
        int err = pthread_setname_np(mThread, mName);
        ALOGW_IF((err != 0), "Could not set name of OutFrameThread. err = %d", err);
        mHasThread = true;
        return true;
    }
}

bool OutFrameThread::stop() {
    if (!mHasThread) {
        ALOGE("stop() but no thread running");
        return false;
    }
    // Check to see if the thread is trying to stop itself.
    if (pthread_equal(pthread_self(), mThread)) {
        ALOGE("%s() attempt to pthread_join() from launched thread!", __func__);
        return false;
    }

    int err = pthread_join(mThread, nullptr);
    if (err != 0) {
        ALOGE("stop() - pthread_join() returned %d %s", err, strerror(err));
        return false;
    } else {
        mHasThread = false;
        return true;
    }
}

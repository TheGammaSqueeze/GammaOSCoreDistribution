/*
* Copyright (C) 2016 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#pragma once

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <functional>
#include <future>
#include <string>
#include <type_traits>

#include "FenceSync.h"
#include "base/ConditionVariable.h"
#include "base/Lock.h"
#include "base/MessageChannel.h"
#include "base/Optional.h"
#include "base/Thread.h"
#include "base/ThreadPool.h"
#include "virtio_gpu_ops.h"
#include "vulkan/VkDecoderGlobalState.h"

// SyncThread///////////////////////////////////////////////////////////////////
// The purpose of SyncThread is to track sync device timelines and give out +
// signal FD's that correspond to the completion of host-side GL fence commands.


struct RenderThreadInfo;
class SyncThread : public android::base::Thread {
public:
    // - constructor: start up the sync worker threads for a given context.
    // The initialization of the sync threads is nonblocking.
    // - Triggers a |SyncThreadCmd| with op code |SYNC_THREAD_EGL_INIT|
    SyncThread(bool noGL);
    ~SyncThread();

    // |triggerWait|: async wait with a given FenceSync object.
    // We use the wait() method to do a eglClientWaitSyncKHR.
    // After wait is over, the timeline will be incremented,
    // which should signal the guest-side fence FD.
    // This method is how the goldfish sync virtual device
    // knows when to increment timelines / signal native fence FD's.
    void triggerWait(FenceSync* fenceSync,
                     uint64_t timeline);

    // |triggerWaitVk|: async wait with a given VkFence object.
    // The |vkFence| argument is a *boxed* host Vulkan handle of the fence.
    //
    // We call vkWaitForFences() on host Vulkan device to wait for the fence.
    // After wait is over, the timeline will be incremented,
    // which should signal the guest-side fence FD / Zircon eventpair.
    // This method is how the goldfish sync virtual device
    // knows when to increment timelines / signal native fence FD's.
    void triggerWaitVk(VkFence vkFence, uint64_t timeline);

    // for use with the virtio-gpu path; is meant to have a current context
    // while waiting.
    void triggerBlockedWaitNoTimeline(FenceSync* fenceSync);

    // For use with virtio-gpu and async fence completion callback. This is async like triggerWait, but takes a fence completion callback instead of incrementing some timeline directly.
    void triggerWaitWithCompletionCallback(FenceSync* fenceSync, FenceCompletionCallback);
    void triggerWaitVkWithCompletionCallback(VkFence fenceHandle, FenceCompletionCallback);
    void triggerWaitVkQsriWithCompletionCallback(VkImage image, FenceCompletionCallback);
    void triggerGeneral(FenceCompletionCallback, std::string description);

    // |cleanup|: for use with destructors and other cleanup functions.
    // it destroys the sync context and exits the sync thread.
    // This is blocking; after this function returns, we're sure
    // the sync thread is gone.
    // - Triggers a |SyncThreadCmd| with op code |SYNC_THREAD_EXIT|
    void cleanup();

    // Initialize the global sync thread.
    static void initialize(bool noGL);

    // Obtains the global sync thread.
    static SyncThread* get();

    // Destroys and cleanup the global sync thread.
    static void destroy();

   private:
    using WorkerId = android::base::ThreadPoolWorkerId;
    struct Command {
        std::packaged_task<int(WorkerId)> mTask;
        std::string mDescription;
    };
    using ThreadPool = android::base::ThreadPool<Command>;

    // |initSyncContext| creates an EGL context expressly for calling
    // eglClientWaitSyncKHR in the processing caused by |triggerWait|.
    // This is used by the constructor only. It is non-blocking.
    // - Triggers a |SyncThreadCmd| with op code |SYNC_THREAD_EGL_INIT|
    void initSyncEGLContext();

    // Thread function.
    // It keeps the workers runner until |mExiting| is set.
    virtual intptr_t main() override final;

    // These two functions are used to communicate with the sync thread from another thread:
    // - |sendAndWaitForResult| issues |job| to the sync thread, and blocks until it receives the
    // result of the job.
    // - |sendAsync| issues |job| to the sync thread and does not wait for the result, returning
    // immediately after.
    int sendAndWaitForResult(std::function<int(WorkerId)> job, std::string description);
    void sendAsync(std::function<void(WorkerId)> job, std::string description);

    // |doSyncThreadCmd| execute the actual task. These run on the sync thread.
    static void doSyncThreadCmd(Command&& command, ThreadPool::WorkerId);

    void doSyncWait(FenceSync* fenceSync, std::function<void()> onComplete);
    static int doSyncWaitVk(VkFence, std::function<void()> onComplete);

    // EGL objects / object handles specific to
    // a sync thread.
    static const uint32_t kNumWorkerThreads = 4u;

    EGLDisplay mDisplay = EGL_NO_DISPLAY;
    EGLSurface mSurface[kNumWorkerThreads];
    EGLContext mContext[kNumWorkerThreads];

    bool mExiting = false;
    android::base::Lock mLock;
    android::base::ConditionVariable mCv;
    ThreadPool mWorkerThreadPool;
    bool mNoGL;
};


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
#ifndef __MPPENCODESERVER_H__
#define __MPPENCODESERVER_H__

#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AHandler.h>
#include <media/stagefright/foundation/ALooper.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/foundation/Mutexed.h>

#include <thread>

#include "OutFrameThread.h"
#include "RKMppEncApi.h"
#include "rk_mpi.h"
using namespace android;

/**
 * Called when an input buffer becomes available.
 * The specified index is the index of the available input buffer.
 */
typedef void (*OnInputAvailable)(int32_t fd);

void OnInputAvailableCB(int32_t fd);

typedef struct NotifyCallback {
    OnInputAvailable onInputAvailable;
} NotifyCallback;

class MppEncodeServer : public std::enable_shared_from_this<MppEncodeServer>,
                        public Runnable {
   public:
    MppEncodeServer();
    ~MppEncodeServer();

    typedef struct {
        char dev_name[64];     // v4l2 device name
        int width;             // v4l2 vfmt width
        int height;            // v4l2 vfmt height
        int fps;
        char stream_name[64];  // rtsp url stream name
        int port_num;          // rtsp port number
    } MetaInfo;

    bool init(MetaInfo* meta);
    bool setNotifyCallback(NotifyCallback callback, void* userdata);
    bool start();
    bool stop();
    bool reset();
    bool release();

    // for handler
    bool processQueue();
    // to implement Runnable
    void run();

    RKMppEncApi* mEncoder;
    NotifyCallback mNotifyCallback;
    FILE* mInputFile = nullptr;
    FILE* mOutputFile = nullptr;
    // This is used by one thread to tell another thread to exit. So it must be
    // atomic.
    std::atomic<bool> mThreadEnabled{false};
    std::atomic<bool> mThreadExited{false};
    // encode getoutPacket thread
    OutFrameThread mOutFrameThread;

   private:
    class WorkHandler : public AHandler {
       public:
        enum {
            kWhatProcess,
            kWhatInit,
            kWhatStart,
            kWhatStop,
            kWhatReset,
            kWhatRelease,
        };

        WorkHandler();
        ~WorkHandler() override = default;

        void setComponent(MppEncodeServer* thiz);

       protected:
        void onMessageReceived(const sp<AMessage>& msg) override;

       private:
        MppEncodeServer* mThiz;
    };

    enum {
        UNINITIALIZED,
        STOPPED,
        RUNNING,
    };

    struct ExecState {
        ExecState() : mState(UNINITIALIZED) {}
        int mState;
    };
    Mutexed<ExecState> mExecState;
    sp<ALooper> mLooper;
    sp<WorkHandler> mHandler;

    bool initOther(MetaInfo* meta);
};

#endif  // __MPPENCODESERVER_H__

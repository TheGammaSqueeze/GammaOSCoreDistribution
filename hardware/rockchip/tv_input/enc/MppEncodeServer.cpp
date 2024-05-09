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

//#define OPEN_DEBUG 1
#define LOG_TAG "MppEncodeServer"
#include "Log.h"
#include "MppEncodeServer.h"

#include <android-base/properties.h>
#include <cutils/properties.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>


#include "MpiDebug.h"
#include "RKMppEncApi.h"
#include "Tools.h"

using namespace android;

#define _ALIGN(x, a) (((x) + (a)-1) & ~((a)-1))

uint32_t enc_debug = 0;
RKMppEncApi::EncCfgInfo_t encInfo;
uint32_t mFrameCount = 0;

char videoPath[30] = "/data/video/";

MppEncodeServer::MppEncodeServer()
    : mEncoder(NULL),
      mOutFrameThread("OutFrameThread"),
      mLooper(new ALooper),
      mHandler(new WorkHandler) {
    Trace();
    mLooper->setName("MppEncodeServer");
    (void)mLooper->registerHandler(mHandler);
    mLooper->start(false, false, ANDROID_PRIORITY_VIDEO);

    ALOGD("MppEncodeServer enter");
}

bool MppEncodeServer::init(MetaInfo *meta) {
    Trace();
    Mutexed<ExecState>::Locked state(mExecState);
    state->mState = UNINITIALIZED;
    // bool needsInit = (state->mState == UNINITIALIZED);
    state.unlock();
    mEncoder = new RKMppEncApi();
    mHandler->setComponent(this);
    if (meta == NULL) {
        ALOGE("Failed to get metaData");
        return false;
    }

    /*if (nullptr == mOutputFile) {
        if (access(videoPath, 0)) {
            ALOGI("videoPath %s not found,build it", videoPath);
            mkdir(videoPath, 0777);
        }
        char h264FilePath[256];
        struct tm *ptime;
        time_t the_time;
        //get current time
        time(&the_time);
        ptime = localtime(&the_time);
        sprintf(h264FilePath, "%s/%02d-%02d-%02d-%02d-%02d-%02d.h264",
                videoPath, ptime->tm_year % 100, ptime->tm_mon + 1,
                ptime->tm_mday, ptime->tm_hour, ptime->tm_min, ptime->tm_sec);
        ALOGI("h264FilePath is %s", h264FilePath);
        mOutputFile = fopen(h264FilePath, "w+b");
    }*/

    get_env_u32("enc_debug", &enc_debug, 0);

    // if (needsInit) {
    //     sp<AMessage> reply;
    //      (new AMessage(WorkHandler::kWhatInit,
    //      mHandler))->postAndAwaitResponse(&reply);
    //     int32_t err;
    //     CHECK(reply->findInt32("err", &err));
    //     if (err != 0) {
    //         return (bool)err;
    //     }
    // }else{
    //     //(new AMessage(WorkHandler::kWhatStart, mHandler))->post();
    // }

    if (initOther(meta)) {
        state.lock();
        state->mState = STOPPED;
        return true;
    } else {
        return false;
    }
}

bool MppEncodeServer::setNotifyCallback(NotifyCallback callback,
                                        void *userdata) {
    mNotifyCallback = callback;
    (void)userdata;
    return true;
}

// TODO: Expand the parameters
bool MppEncodeServer::initOther(MetaInfo *meta) {
    encInfo.width = meta->width;
    encInfo.height = meta->height;
    encInfo.scaleWidth = _ALIGN((meta->width) / 2, 2);
    encInfo.scaleHeight = _ALIGN((meta->height) / 2, 2);
    encInfo.format = MPP_FMT_YUV420SP;
    encInfo.framerate = meta->fps;      // 60fps
    encInfo.bitRate = 20000000;  // 200M default
    encInfo.IDRInterval = 1;
    encInfo.bitrateMode =
        BITRATE_CONST; /* 0 - VBR mode; 1 - CBR mode; 2 - FIXQP mode */
    encInfo.qp = 30;   // 1~51
    encInfo.profile = H264_PROFILE_BASELINE;
    encInfo.level = AVC_LEVEL4_1;
    encInfo.rotation = MPP_ENC_ROT_0;
    if (!mEncoder->init(&encInfo)) {
        ALOGE("Failed to init mEncoder");
        return false;
    }

    return true;
}

void MppEncodeServer::run() {
    //usleep(32000);
    mThreadExited.exchange(false);
    while (mThreadEnabled.load()) {
        usleep(100);
        processQueue();
        ALOGD("run()");
    }
    mThreadExited.exchange(true);
    ALOGD("exit");
}

// TODO: reserved
bool MppEncodeServer::start() {
    Trace();
    int32_t err;
    (void)err;
    Mutexed<ExecState>::Locked state(mExecState);
    if (state->mState == UNINITIALIZED) {
        ALOGE("MppEncodeServer has not been initialized");
        return false;
    }
    // bool needsInit = (state->mState == UNINITIALIZED);
    // state.unlock();
    // if (needsInit) {
    //     sp<AMessage> reply;
    //     (new AMessage(WorkHandler::kWhatInit,
    //     mHandler))->postAndAwaitResponse(&reply); int32_t err;
    //     CHECK(reply->findInt32("err", &err));
    //     if (err != 0) {
    //         return (bool)err;
    //     }
    // } else {
    (new AMessage(WorkHandler::kWhatStart, mHandler))->post();
    // }
    state.lock();
    state->mState = RUNNING;
    (new AMessage(WorkHandler::kWhatProcess, mHandler))->post();
    return true;
}

// TODO: reserved
bool MppEncodeServer::stop() {
    Trace();
    {
        bool result = true;
        mThreadEnabled.exchange(false);
        // clear flag that tells thread to loop
        while (true) {
            if(mThreadExited.load()){
                ALOGD("zj add file: %s func %s line %d \n",__FILE__,__FUNCTION__,__LINE__);
                result = mOutFrameThread.stop();
                break;
            }else{
                ALOGD("zj add file: %s func %s line %d \n",__FILE__,__FUNCTION__,__LINE__);
                usleep(5000);
                continue;
            }
        }

        Mutexed<ExecState>::Locked state(mExecState);
        if (state->mState != RUNNING) {
            return false;
        }
        state->mState = STOPPED;
    }
    {
        // Mutexed<WorkQueue>::Locked queue(mWorkQueue);
        // queue->clear();
        // queue->pending().clear();
    }

    sp<AMessage> reply;
    (new AMessage(WorkHandler::kWhatStop, mHandler))
        ->postAndAwaitResponse(&reply);
    int32_t err;
    CHECK(reply->findInt32("err", &err));
    if (err != 0) {
        return (bool)err;
    }

    return true;
}

bool MppEncodeServer::reset() {
    Trace();
    {
        Mutexed<ExecState>::Locked state(mExecState);
        state->mState = UNINITIALIZED;
    }
    {
        // Mutexed<WorkQueue>::Locked queue(mWorkQueue);
        // queue->clear();
        // queue->pending().clear();
    }
    sp<AMessage> reply;
    (new AMessage(WorkHandler::kWhatReset, mHandler))
        ->postAndAwaitResponse(&reply);
    return true;
}

bool MppEncodeServer::release() {
    Trace();
    sp<AMessage> reply;
    if (mEncoder != NULL) {
        ALOGD("Exit mEncoder");
        delete mEncoder;
        mEncoder = NULL;
    }
    (new AMessage(WorkHandler::kWhatRelease, mHandler))
        ->postAndAwaitResponse(&reply);
    return true;
}

MppEncodeServer::~MppEncodeServer() {
    Trace();
    release();
    if (nullptr != mOutputFile) {
        fclose(mOutputFile);
        mOutputFile = nullptr;
    }

    mLooper->unregisterHandler(mHandler->id());
    (void)mLooper->stop();
    ALOGD("~MppEncodeServer out");
}

////////////////////////////////////////////////////////////////////////////////

MppEncodeServer::WorkHandler::WorkHandler() {}

void MppEncodeServer::WorkHandler::setComponent(MppEncodeServer *thiz) {
    Trace();
    mThiz = thiz;
}

static void Reply(const sp<AMessage> &msg, int32_t *err = nullptr) {
    sp<AReplyToken> replyId;
    CHECK(msg->senderAwaitsResponse(&replyId));
    sp<AMessage> reply = new AMessage;
    if (err) {
        reply->setInt32("err", *err);
    }
    reply->postReply(replyId);
}

void MppEncodeServer::WorkHandler::onMessageReceived(const sp<AMessage> &msg) {
    if (!mThiz) {
        ALOGD("component not yet set; msg = %s", msg->debugString().c_str());
        sp<AReplyToken> replyId;
        if (msg->senderAwaitsResponse(&replyId)) {
            sp<AMessage> reply = new AMessage;
            reply->setInt32("err", EFAULT);
            reply->postReply(replyId);
        }
        return;
    }

    switch (msg->what()) {
        case kWhatProcess: {
            mThiz->mThreadEnabled.store(true);
            break;
        }
        case kWhatInit: {
            int32_t err = mThiz->mEncoder->onInit();
            Reply(msg, &err);
            break;
        }
        case kWhatStart: {
            mThiz->mThreadExited.store(false);
            mThiz->mThreadEnabled.store(true);
            bool err = mThiz->mOutFrameThread.start(mThiz);
            if (err != true) {
                ALOGE("mOutFrameThread err: %d", err);
                break;
            }
            break;
        }
        case kWhatStop: {
            int32_t err = mThiz->mEncoder->onStop();
            // mThiz->mOutputBlockPool.reset();
            Reply(msg, &err);
            break;
        }
        case kWhatReset: {
            mThiz->mEncoder->onReset();
            // mThiz->mOutputBlockPool.reset();
            mThiz->mThreadEnabled.exchange(false);
            Reply(msg);
            break;
        }
        case kWhatRelease: {
            // mThiz->mOutputBlockPool.reset();
            mThiz->mThreadEnabled.exchange(false);
            Reply(msg);
            break;
        }
        default: {
            ALOGE("Unrecognized msg: %d", msg->what());
            break;
        }
    }
}

extern nsecs_t now;
extern nsecs_t mLastTime;
extern nsecs_t diff;
bool MppEncodeServer::processQueue() {
    Trace();
    bool ret = false;
    RKMppEncApi::OutWorkEntry entry;

    memset(&entry, 0, sizeof(RKMppEncApi::OutWorkEntry));

    // mLastTime = systemTime();
    ALOGD("zj add file: %s func %s line %d \n",__FILE__,__FUNCTION__,__LINE__);
    ret = mEncoder->getoutpacket(&entry);
    ALOGD("zj add file: %s func %s line %d \n",__FILE__,__FUNCTION__,__LINE__);
    now = systemTime();
    diff = now - mLastTime;
    // ALOGD("getoutpacket diff %" PRIu64, diff);
    if (ret == true && NULL != entry.outPacket) {
        void *data = mpp_packet_get_data(entry.outPacket);
        size_t len = mpp_packet_get_length(entry.outPacket);
        if (len != 0 && (nullptr != mOutputFile)) {
            fwrite(data, 1, len, mOutputFile);
            fflush(mOutputFile);
        }
        ALOGD("getoutput pts %d", entry.frameIndex);
    } else {
        ALOGD("no new packet this call,continue");
        return false;
    }

    mNotifyCallback.onInputAvailable(entry.index);

    if (NULL != entry.outPacket) {
        mpp_packet_deinit(&entry.outPacket);
        entry.outPacket = NULL;
    }
    return true;
}
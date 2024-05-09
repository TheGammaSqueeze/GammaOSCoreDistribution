/*
 * Copyright 2019 Rockchip Electronics Co. LTD
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
 * author: rimon.xu@rock-chips.com
 *   date: 2019/12/31
 * module: sideband window
 */

//#define LOG_NDEBUG 0
//#define LOG_TAG "RTSidebandWindow"

#include "RTSidebandWindow.h"
#include "log/log.h"
#include <sys/stat.h>
#include <sys/time.h>
#include <utils/Timers.h>
#include <string.h>
#include <ui/GraphicBufferAllocator.h>

#include "DrmVopRender.h"

namespace android {

#define MIN_BUFFER_COUNT_UNDEQUEUE      0

static uint64_t g_session_id;

RTSidebandWindow::RTSidebandWindow()
        : mBuffMgr(nullptr),
          mVopRender(NULL),
          mVTDevFd(-1),
          mVTID(-1),
          mRenderingCnt(0),
          mThreadRunning(false),
          mMessageQueue("RenderThread", static_cast<int>(MESSAGE_ID_MAX)),
          mMessageThread(nullptr),
          mDebugLevel(0) {
    memset(&mSidebandInfo, 0, sizeof(mSidebandInfo));
}

RTSidebandWindow::~RTSidebandWindow() {
    DEBUG_PRINT(mDebugLevel, "%s %d in", __FUNCTION__, __LINE__);
}

status_t RTSidebandWindow::init(const vt_win_attr_t *attr, int sidebandType) {
    ALOGD("%s %d in", __FUNCTION__, __LINE__);
    status_t    err = 0;
    bool        ready = false;
    mSidebandType = sidebandType;

    mBuffMgr = common::TvInputBufferManager::GetInstance();

    if (attr->struct_size != sizeof(vt_win_attr_t)) {
        DEBUG_PRINT(3, "sideband info struct size is invailed!");
        goto __FAILED;
    }

    memcpy(&mSidebandInfo, attr, sizeof(vt_win_attr_t));
    ALOGD("RTSidebandWindow::init width=%d, height=%d, format=%x, usage=%lld, type=%d",
        mSidebandInfo.width, mSidebandInfo.height, mSidebandInfo.format, (long long)mSidebandInfo.usage, sidebandType);

    if (mSidebandType & TYPE_SIDEBAND_WINDOW) {
        mVopRender = android::DrmVopRender::GetInstance();
        if (!mVopRender->mInitialized) {
            ready = mVopRender->initialize();
            if (ready) {
                mVopRender->detect();
            }
        }
    } else if (mSidebandType & TYPE_SIDEBAND_VTUNNEL) {
        mVTDevFd = rk_vt_open();
        if (mVTDevFd < 0) {
            ALOGE("rk_vt_open mVTDevFd=%d failed", mVTDevFd);
            goto __FAILED;
        }
        err = rk_vt_alloc_id(mVTDevFd, &mVTID);
        if (err < 0 || mVTID < 0) {
            goto __ALLOC_ID_FAILED;
        }
        ALOGW("rk_vt_alloc_id vtunnel_id=%d", mVTID);
        err = rk_vt_connect(mVTDevFd, mVTID, RKVT_ROLE_PRODUCER);
        if (err <  0) {
            ALOGE("rk_vt_connect vtunnel_id=%d failed", mVTID);
            goto __CONNECT_FALED;
        }
    }

#if 0
    mMessageThread = std::unique_ptr<MessageThread>(new MessageThread(this, "VOP Render"));
    if (mMessageThread != NULL) {
        mMessageThread->run();
    }
#endif

    return err;
__CONNECT_FALED:
    rk_vt_free_id(mVTDevFd, mVTID);
__ALLOC_ID_FAILED:
    rk_vt_close(mVTDevFd);
__FAILED:
    return -1;
}

status_t RTSidebandWindow::release() {
    ALOGW("%s mVTDevFd=%d, mVTID=%d", __FUNCTION__, mVTDevFd, mVTID);
    if (mVTID >= 0) {
        rk_vt_disconnect(mVTDevFd, mVTID, RKVT_ROLE_PRODUCER);
        rk_vt_reset(mVTDevFd, mVTID);
        rk_vt_free_id(mVTDevFd, mVTID);
        rk_vt_close(mVTDevFd);
    }

    do {
        android::Mutex::Autolock _l(mLock);
        while (mBufferQueue.size() > 0) {
            vt_buffer_t *tmpBuffer = mBufferQueue.front();
            mBufferQueue.erase(mBufferQueue.begin());
            freeBuffer(&tmpBuffer);
        }
    } while (0);
    mRenderingCnt = 0;

    return 0;
}

status_t RTSidebandWindow::stop() {
    DEBUG_PRINT(3, "%s %d in", __FUNCTION__, __LINE__);
    if (mVopRender) {
        mVopRender->deinitialize();
        //delete mVopRender;
        //mVopRender = NULL;
    }
    return 0;
}

status_t RTSidebandWindow::flush() {
    android::Mutex::Autolock _l(mLock);
    while (mBufferQueue.size() > 0) {
        vt_buffer_t *tmpBuffer = mBufferQueue.front();
        mBufferQueue.erase(mBufferQueue.begin());
        freeBuffer(&tmpBuffer);
    }
    mRenderingCnt = 0;

    return rk_vt_reset(mVTDevFd, mVTID);
}

status_t RTSidebandWindow::setAttr(const vt_win_attr_t *attr) {
    android::Mutex::Autolock _l(mLock);

    if (attr->struct_size != sizeof(vt_win_attr_t)) {
        ALOGE("setAttr: sideband window info struct size is invailed!");
        return -1;
    }

    memcpy(&mSidebandInfo, attr, sizeof(vt_win_attr_t));

    return 0;
}

status_t RTSidebandWindow::getAttr(vt_win_attr_t *info) {
    android::Mutex::Autolock _l(mLock);

    memcpy(info, &mSidebandInfo, sizeof(vt_win_attr_t));

    return 0;
}

status_t RTSidebandWindow::allocateSidebandHandle(buffer_handle_t *handle,
        int VTId) {
    native_handle_t *temp_buffer = NULL;
    vt_sideband_data_t info;

    memset(&info, 0, sizeof(vt_sideband_data_t));

    g_session_id++;
    info.version        = sizeof(vt_sideband_data_t);
    info.tunnel_id      = VTId > -1 ? VTId : mVTID;
    info.crop.left      = mSidebandInfo.left;
    info.crop.top       = mSidebandInfo.top;
    info.crop.right     = mSidebandInfo.right;
    info.crop.bottom    = mSidebandInfo.bottom;
    info.width          = mSidebandInfo.width;
    info.height         = mSidebandInfo.height;
    info.format         = mSidebandInfo.format;
    info.transform      = mSidebandInfo.transform;
    info.usage          = mSidebandInfo.usage;
    info.data_space     = mSidebandInfo.data_space;
    info.compress_mode  = mSidebandInfo.compress_mode;
    info.session_id     = g_session_id;

    temp_buffer = native_handle_create(0, sizeof(vt_sideband_data_t) / sizeof(int));
    temp_buffer->version = sizeof(native_handle_t);
    temp_buffer->numFds  = 0;
    temp_buffer->numInts = sizeof(vt_sideband_data_t) / sizeof(int);
    memcpy(&temp_buffer->data[0], &info, sizeof(vt_sideband_data_t));

    *handle = (buffer_handle_t)temp_buffer;

    ALOGI("allocate handle %p to native window session-id %lld",
            temp_buffer, (long long)info.session_id);
    ALOGI("allocate handle: tid[%d] crop[%d %d %d %d], wxh[%d %d] fmt[%d] " \
           "transform[%d] usage[%p] data_space[%lld] compress_mode[%d]",
           info.tunnel_id, info.crop.left, info.crop.top, info.crop.right,
           info.crop.bottom, info.width, info.height, info.format, info.transform,
           (void *)info.usage, (long long)info.data_space, info.compress_mode);

    return 0;
}

status_t RTSidebandWindow::allocateBuffer(vt_buffer_t **buffer) {
    return allocateBuffer(buffer,
        mSidebandInfo.width, mSidebandInfo.height,
        mSidebandInfo.format, mSidebandInfo.usage);
}

status_t RTSidebandWindow::allocateBuffer(vt_buffer_t **buffer,
        int width, int32_t height, int32_t format, uint64_t usage) {
    GraphicBufferAllocator &allocator = GraphicBufferAllocator::get();
    buffer_handle_t temp_buffer = NULL;
    uint32_t outStride = 0;
    vt_buffer_t *vtBuffer = NULL;

    status_t err = allocator.allocate(width, height,
                                      format,
                                      1,
                                      usage,
                                      &temp_buffer,
                                      &outStride,
                                      0,
                                      std::move("videotunnel"));
    if (err != NO_ERROR) {
        return err;
    }

    vtBuffer = rk_vt_buffer_malloc();
    vtBuffer->handle = (native_handle_t *)temp_buffer;
    *buffer = vtBuffer;
    ALOGI("allocate buffer: fd-0[%d] wxh[%d %d] fmt[0x%x] usage[%p]",
           temp_buffer->data[0], width, height,
           format, (void *)usage);

    return 0;
}

status_t RTSidebandWindow::freeBuffer(vt_buffer_t **buffer) {
    GraphicBufferAllocator &allocator = GraphicBufferAllocator::get();

    ALOGI("free buffer: fd-0[%d] wxh[%d %d] fmt[0x%x] usage[%p]",
           (*buffer)->handle->data[0], mSidebandInfo.width, mSidebandInfo.height,
           mSidebandInfo.format, (void *)mSidebandInfo.usage);
    allocator.free((*buffer)->handle);
    (*buffer)->handle = NULL;
    rk_vt_buffer_free(buffer);
    *buffer = NULL;

    return 0;
}

status_t RTSidebandWindow::dequeueBuffer(vt_buffer_t **buffer, int timeout_ms, int *fence) {
    int err = 0;
    vt_buffer_t *tmpBuffer = NULL;

    {
        android::Mutex::Autolock _l(mLock);
        if (mBufferQueue.size() < mSidebandInfo.buffer_cnt) {
            ALOGW("%s %d do allocateBuffer", __FUNCTION__, __LINE__);
            err = allocateBuffer(buffer);
            if (err == 0) {
                mBufferQueue.push_back(*buffer);
            }
            return err;
        }
    }

    err = rk_vt_dequeue_buffer(mVTDevFd, mVTID, timeout_ms, &tmpBuffer);
    if (err != 0 && tmpBuffer == NULL) {
        return err;
    }
    *buffer = tmpBuffer;
    *fence = -1;
    mRenderingCnt--;

    return 0;
}

status_t RTSidebandWindow::queueBuffer(vt_buffer_t *buffer, int fence, int64_t expected_present_time) {
    mRenderingCnt++;
    buffer->crop.left = mSidebandInfo.left;
    buffer->crop.top = mSidebandInfo.top;
    buffer->crop.right = mSidebandInfo.right;
    buffer->crop.bottom = mSidebandInfo.bottom;
    return rk_vt_queue_buffer(mVTDevFd, mVTID, (vt_buffer_t *)buffer, expected_present_time);
}

status_t RTSidebandWindow::cancelBuffer(vt_buffer_t *buffer) {
    {
        android::Mutex::Autolock _l(mLock);
        if (mRenderingCnt >= mSidebandInfo.remain_cnt) {
            for (auto it = mBufferQueue.begin(); it != mBufferQueue.end(); it++) {
                if (*it == buffer) {
                    mBufferQueue.erase(it);
                    return freeBuffer(&buffer);
                }
            }
            if (buffer) {
                if (buffer->handle) {
                    ALOGW("cancel buffer(%p) fd-0(%d) not allocate by sideband window.",
                        buffer, buffer->handle->data[0]);
                } else {
                    ALOGE("%s cancel buffer(%p) but buffer->handle is NULL.",
                        __FUNCTION__, buffer);
                }
            } else {
                ALOGE("%s cancel NULL buffer.", __FUNCTION__);
            }
        }
    }
    mRenderingCnt++;

    return rk_vt_cancel_buffer(mVTDevFd, mVTID, buffer);
}


status_t RTSidebandWindow::flushCache(buffer_handle_t buffer) {
    if (!buffer) {
        DEBUG_PRINT(3, "%s param buffer is NULL.", __FUNCTION__);
        return -1;
    }
    return mBuffMgr->FlushCache(buffer);
}

status_t RTSidebandWindow::allocateBuffer(buffer_handle_t *buffer) {
    return allocateSidebandHandle(buffer,
        mSidebandInfo.width,
        mSidebandInfo.height,
        mSidebandInfo.format,
        mSidebandInfo.usage);
}

status_t RTSidebandWindow::allocateSidebandHandle(buffer_handle_t *handle,
        int width, int32_t height, int32_t format, uint64_t usage) {
    GraphicBufferAllocator &allocator = GraphicBufferAllocator::get();
    buffer_handle_t temp_buffer = NULL;
    uint32_t outStride = 0;

    status_t err = allocator.allocate(-1 == width?mSidebandInfo.width:width,
                        -1 == height?mSidebandInfo.height:height,
                        -1 == format?mSidebandInfo.format:format,
                        1,
                        -1 == usage?0:usage,
                        &temp_buffer,
                        &outStride,
                        0,
                        std::move("tif_allocate"));
    if (!temp_buffer) {
        DEBUG_PRINT(3, "allocate failed !!!");
    } else {
        *handle = temp_buffer;
        err = 0;
    }
    return err;
}

status_t RTSidebandWindow::freeBuffer(buffer_handle_t *buffer, int type) {
    DEBUG_PRINT(3, "%s in type = %d", __FUNCTION__, type);
    GraphicBufferAllocator &allocator = GraphicBufferAllocator::get();
    allocator.free(*buffer);
    return 0;
}

status_t RTSidebandWindow::setBufferGeometry(int32_t width, int32_t height, int32_t format) {
    DEBUG_PRINT(mDebugLevel, "%s %d width=%d height=%d in", __FUNCTION__, __LINE__, width, height);
    mSidebandInfo.width = width;
    mSidebandInfo.height = height;
    mSidebandInfo.format = format;
    RTSidebandWindow::setAttr(&mSidebandInfo);
    return 0;
}

status_t RTSidebandWindow::setCrop(int32_t left, int32_t top, int32_t right, int32_t bottom) {
    mSidebandInfo.left = left;
    mSidebandInfo.top = top;
    mSidebandInfo.right = right;
    mSidebandInfo.bottom = bottom;
    RTSidebandWindow::setAttr(&mSidebandInfo);
    return 0;
}


status_t RTSidebandWindow::requestExitAndWait()
{
    Message msg;
    memset(&msg, 0, sizeof(Message));
    msg.id = MESSAGE_ID_EXIT;
    status_t status = mMessageQueue.send(&msg, MESSAGE_ID_EXIT);
    //status |= mMessageThread->requestExitAndWait();
    return status;
}

void RTSidebandWindow::messageThreadLoop() {
    mThreadRunning = true;
    while (mThreadRunning) {
        status_t status = NO_ERROR;
        Message msg;
        mMessageQueue.receive(&msg);

        ALOGD("@%s, receive message id:%d", __FUNCTION__, msg.id);
        switch (msg.id) {
        case MESSAGE_ID_EXIT:
          status = handleMessageExit();
        break;
        case MESSAGE_ID_RENDER_REQUEST:
          status = handleRenderRequest(msg);
        break;
        case MESSAGE_ID_DEQUEUE_REQUEST:
          status = handleDequeueRequest(msg);
        break;
        case MESSAGE_ID_FLUSH:
          status = handleFlush();
        break;
        default:
          DEBUG_PRINT(3, "ERROR Unknown message %d", msg.id);
          status = BAD_VALUE;
        break;
        }

        if (status != NO_ERROR)
            DEBUG_PRINT(3, "error %d in handling message: %d", status, static_cast<int>(msg.id));
        DEBUG_PRINT(mDebugLevel, "@%s, finish message id:%d", __FUNCTION__, msg.id);
        mMessageQueue.reply(msg.id, status);
    }
}

status_t RTSidebandWindow::handleMessageExit() {
    mThreadRunning = false;
    return 0;
}

status_t RTSidebandWindow::handleRenderRequest(Message &msg) { 
    buffer_handle_t buffer = msg.streamBuffer.buffer;
    ALOGD("%s %d buffer: %p in", __FUNCTION__, __LINE__, buffer);
    mVopRender->SetDrmPlane(0, mSidebandInfo.right - mSidebandInfo.left, mSidebandInfo.bottom - mSidebandInfo.top, buffer, FULL_SCREEN, HDMIIN_TYPE_HDMIRX);

    mRenderingQueue.push_back(buffer);
    ALOGD("%s    mRenderingQueue.size() = %d", __FUNCTION__, (int32_t)mRenderingQueue.size());

    return 0;
}

status_t RTSidebandWindow::show(buffer_handle_t handle, int displayRatio, int hdmiInType) {
    if (mVopRender) {
        mVopRender->SetDrmPlane(0, mSidebandInfo.right - mSidebandInfo.left, mSidebandInfo.bottom - mSidebandInfo.top,
            handle, displayRatio, hdmiInType);
    }
    return 0;
}

void RTSidebandWindow::setDebugLevel(int debugLevel) {
    if (mDebugLevel != debugLevel && mVopRender) {
        mDebugLevel = debugLevel;
        mVopRender->setDebugLevel(debugLevel);
    }
}

status_t RTSidebandWindow::clearVopArea() {
    ALOGD("RTSidebandWindow::clearVopArea()");
    if (mVopRender) {
        mVopRender->DestoryFB();
        mVopRender->ClearDrmPlaneContent(0, mSidebandInfo.right - mSidebandInfo.left, mSidebandInfo.bottom - mSidebandInfo.top);
    }
    return 0;
}

status_t RTSidebandWindow::handleDequeueRequest(Message &msg) {
    (void)msg;
    mRenderingQueue.erase(mRenderingQueue.begin());
    return 0;
}

status_t RTSidebandWindow::handleFlush() {
    while (mRenderingQueue.size() > 0) {
        buffer_handle_t buffer = NULL;
        buffer = mRenderingQueue.front();
        mRenderingQueue.erase(mRenderingQueue.begin());
        freeBuffer(&buffer, 0);
    }

    return 0;
}

int RTSidebandWindow::getBufferHandleFd(buffer_handle_t buffer){
    if (!buffer) {
        DEBUG_PRINT(3, "%s param buffer is NULL.", __FUNCTION__);
        return -1;
    }
    return mBuffMgr->GetHandleFd(buffer);
}

int RTSidebandWindow::getBufferLength(buffer_handle_t buffer) {
    if (!buffer) {
        DEBUG_PRINT(3, "%s param buffer is NULL.", __FUNCTION__);
        return -1;
    }
    return mBuffMgr->GetHandleBufferSize(buffer);
}

int RTSidebandWindow::importHidlHandleBufferLocked(buffer_handle_t& rawHandle) {
    ALOGD("%s rawBuffer :%p", __FUNCTION__, rawHandle);
    if (rawHandle) {
        if(!mBuffMgr->ImportBufferLocked(rawHandle)) {
            return getBufferHandleFd(rawHandle);
        } else {
            ALOGE("%s failed.", __FUNCTION__);
        }
    }
    return -1;
}

int RTSidebandWindow::buffDataTransfer(buffer_handle_t srcHandle, buffer_handle_t dstHandle) {
    ALOGD("%s in srcHandle=%p, dstHandle=%p", __FUNCTION__, srcHandle, dstHandle);
    std::string file1 = "/data/system/tv_input_src_dump.yuv";
    std::string file2 = "/data/system/tv_input_result_dump.yuv";
    if (srcHandle && dstHandle) {
        void *tmpSrcPtr = NULL, *tmpDstPtr = NULL;
        int srcDatasize = -1;
            int lockMode = GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK | GRALLOC_USAGE_HW_CAMERA_MASK;
            mBuffMgr->Lock(srcHandle, lockMode, 0, 0, mBuffMgr->GetWidth(srcHandle), mBuffMgr->GetHeight(srcHandle), &tmpSrcPtr);
            for (int i = 0; i < mBuffMgr->GetNumPlanes(srcHandle); i++) {
                srcDatasize += mBuffMgr->GetPlaneSize(srcHandle, i);
            }
             writeData2File(file1.c_str(), tmpSrcPtr, srcDatasize);
            ALOGD("data tmpSrcPtr ptr = %p, srcDatasize=%d", tmpSrcPtr, srcDatasize);
            mBuffMgr->LockLocked(dstHandle, lockMode, 0, 0, mBuffMgr->GetWidth(dstHandle), mBuffMgr->GetHeight(dstHandle), &tmpDstPtr);
            ALOGD("data tmpDstPtr ptr = %p, width=%d, height=%d", tmpDstPtr, mBuffMgr->GetWidth(dstHandle), mBuffMgr->GetHeight(dstHandle));
            std::memcpy(tmpDstPtr, tmpSrcPtr, srcDatasize);
             writeData2File(file2.c_str(), tmpDstPtr, srcDatasize);
            mBuffMgr->UnlockLocked(dstHandle);
            mBuffMgr->Unlock(srcHandle);
            ALOGD("%s end", __FUNCTION__);
            return 0;
    }
    return -1;
}

int RTSidebandWindow::buffDataTransfer2(buffer_handle_t srcHandle, buffer_handle_t dstHandle) {
    if (srcHandle && dstHandle) {
        unsigned char* tmpSrcPtr = NULL;
        unsigned char* tmpDstPtr = NULL;
        int srcDatasize = -1;
        int lockMode = GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK | GRALLOC_USAGE_HW_CAMERA_MASK;
        mBuffMgr->Lock(srcHandle, lockMode, 0, 0, mBuffMgr->GetWidth(srcHandle), mBuffMgr->GetHeight(srcHandle), (void**)&tmpSrcPtr);
        for (int i = 0; i < mBuffMgr->GetNumPlanes(srcHandle); i++) {
            srcDatasize += mBuffMgr->GetPlaneSize(srcHandle, i);
        }
        mBuffMgr->LockLocked(dstHandle, lockMode, 0, 0, mBuffMgr->GetWidth(dstHandle), mBuffMgr->GetHeight(dstHandle), (void**)&tmpDstPtr);
        int dstDatesize = -1;
        for (int i = 0; i < mBuffMgr->GetNumPlanes(dstHandle); i++) {
            dstDatesize += mBuffMgr->GetPlaneSize(dstHandle, i);
        }
        //ALOGD("%s %d %d", __FUNCTION__, srcDatasize, dstDatesize);

        std::memcpy(tmpDstPtr, tmpSrcPtr, dstDatesize);

        mBuffMgr->UnlockLocked(dstHandle);
        mBuffMgr->Unlock(srcHandle);

        return 0;
    }
    return -1;
}

int RTSidebandWindow::NV24ToNV12(buffer_handle_t srcHandle, buffer_handle_t dstHandle, int width, int height) {
    if (srcHandle && dstHandle) {
        //void *tmpSrcPtr = NULL, *tmpDstPtr = NULL;
        unsigned char* tmpSrcPtr = NULL;
        unsigned char* tmpDstPtr = NULL;
        int srcDatasize = -1;
        int lockMode = GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK | GRALLOC_USAGE_HW_CAMERA_MASK;
        //DEBUG_PRINT(3, "%d %d", mBuffMgr->GetHandleBufferSize(srcHandle), mBuffMgr->GetHandleBufferSize(dstHandle));
        mBuffMgr->Lock(srcHandle, lockMode, 0, 0, mBuffMgr->GetWidth(srcHandle), mBuffMgr->GetHeight(srcHandle), (void**)&tmpSrcPtr);
        for (int i = 0; i < mBuffMgr->GetNumPlanes(srcHandle); i++) {
            srcDatasize += mBuffMgr->GetPlaneSize(srcHandle, i);
        }
        //ALOGD("data tmpSrcPtr ptr = %p, srcDatasize=%d", tmpSrcPtr, srcDatasize);
        mBuffMgr->LockLocked(dstHandle, lockMode, 0, 0, mBuffMgr->GetWidth(dstHandle), mBuffMgr->GetHeight(dstHandle), (void**)&tmpDstPtr);
        //ALOGD("data tmpDstPtr ptr = %p, width=%d, height=%d", tmpDstPtr, mBuffMgr->GetWidth(dstHandle), mBuffMgr->GetHeight(dstHandle));

        int i,j;
        int width_uv_out = width / 2;
        int height_uv_out = height / 2;

        int uIn = width * height;
        int uOut = width * height;
        //ALOGD("==============start================%dX%d, %dX%d", width_uv_out, height_uv_out, width, height);
        std::memcpy(tmpDstPtr, tmpSrcPtr, width*height);

        for(i = 0; i < height_uv_out; i++) {
            for(j = 0; j < width_uv_out; j++) {
                int dstPos = uOut + i*width + j*2;
                int srcPos = uIn + i*4*width + j*4;
                std::memcpy(tmpDstPtr+dstPos, tmpSrcPtr+srcPos, 2);
            }
        }

        mBuffMgr->UnlockLocked(dstHandle);
        mBuffMgr->Unlock(srcHandle);
        //ALOGD("==============end================");

        return 0;
    }
    return -1;
}

void RTSidebandWindow::readDataFromFile(const char *file_path, buffer_handle_t dstHandle) {
    FILE* fp = fopen(file_path, "rb");
    if(fp == NULL) {
        ALOGD("open file %s , error %s\n", file_path, strerror(errno));
        return;
    }

    unsigned int filesize = -1;
    struct stat statbuff;
    if (stat(file_path, &statbuff) < 0) {
        return;
    }
    filesize = statbuff.st_size;
    ALOGD("%s(%d) size of file :%s size is :\t%u bytes\n",
        __func__, __LINE__, file_path, filesize);

    unsigned char* tmpDstPtr = NULL;
    int lockMode = GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK | GRALLOC_USAGE_HW_CAMERA_MASK;
    mBuffMgr->LockLocked(dstHandle, lockMode, 0, 0, mBuffMgr->GetWidth(dstHandle), mBuffMgr->GetHeight(dstHandle), (void**)&tmpDstPtr);

    unsigned int num_read = fread(tmpDstPtr, filesize, 1,fp);
    ALOGD("fread num is %u\n", num_read);
    fclose(fp);

    mBuffMgr->UnlockLocked(dstHandle);
}

int RTSidebandWindow::writeData2File(const char *fileName, void *data, int dataSize) {
    int ret = 0;
    FILE* fp = NULL;
    fp = fopen(fileName, "wb+");
    if (fp != NULL) {
        if (fwrite(data, dataSize, 1, fp) <= 0) {
            ALOGE("fwrite %s failed.", fileName);
            ret = -1;
        } else {
            ALOGD("fwirte %s success", fileName);
        }
    } else {
        ALOGE("open failed");
        ret = -1;
    }
    fclose(fp);
    return ret;
}

int RTSidebandWindow::dumpImage(buffer_handle_t handle, char* fileName, int mode) {
    int ret = -1;
    if (!handle || !fileName) {
        DEBUG_PRINT(3, "%s param buffer is NULL.", __FUNCTION__);
        return ret;
    }
    ALOGD("%s handle :%p", __FUNCTION__, handle);
    FILE* fp = NULL;
    void *dataPtr = NULL;
    int dataSize = 0;
    fp = fopen(fileName, "wb+");
    if (fp != NULL) {
        struct android_ycbcr ycbrData;
        int lockMode = GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK | GRALLOC_USAGE_HW_CAMERA_MASK;
        if (mode == 1) {
            mBuffMgr->LockYCbCr(handle, lockMode, 0, 0, mBuffMgr->GetWidth(handle), mBuffMgr->GetHeight(handle), &ycbrData);
            dataPtr = ycbrData.y;
        } else {
            ALOGD("width = %d", mBuffMgr->GetWidth(handle));
            ALOGD("height = %d", mBuffMgr->GetHeight(handle));
            mBuffMgr->LockLocked(handle, lockMode, 0, 0, mBuffMgr->GetWidth(handle), mBuffMgr->GetHeight(handle), &dataPtr);
        }
        ALOGD("planesNum = %d", mBuffMgr->GetNumPlanes(handle));
        for (int i = 0; i < mBuffMgr->GetNumPlanes(handle); i++) {
        ALOGD("planesSize = %zu", mBuffMgr->GetPlaneSize(handle, i));
            dataSize += mBuffMgr->GetPlaneSize(handle, i);
        }
        if (dataSize <= 0) {
            ALOGE("dataSize <= 0 , it can't write file.");
            ret = -1;
        } else {
            if (fwrite(dataPtr, dataSize, 1, fp) <= 0) {
                ALOGE("fwrite %s failed.", fileName);
                ret = -1;
            }
        }
        fclose(fp);
	if (mode == 0) {
            mBuffMgr->UnlockLocked(handle);
        } else {
            mBuffMgr->Unlock(handle);
        }
        ALOGI("Write data success to %s",fileName);
        ret = 0;
    } else {
        DEBUG_PRINT(3, "Create %s failed(%p, %s)", fileName, fp, strerror(errno));
    }
    return ret;
}

int RTSidebandWindow::getSidebandPlaneId() {
    if (mVopRender) {
        return mVopRender->getSidebandPlaneId();
    } else {
        return 0;
    }
}
}

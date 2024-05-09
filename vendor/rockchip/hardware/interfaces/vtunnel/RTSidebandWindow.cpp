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

// #define LOG_NDEBUG 0
#define LOG_TAG "RTSidebandWindow"

#include "log/log.h"
#include <sys/time.h>
#include <utils/Timers.h>
#include <string.h>
#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>

#include "RTSidebandWindow.h"
#include "video_tunnel.h"

namespace android {

#define FUNCTION_IN()       ALOGV("%s %d in", __FUNCTION__, __LINE__)

#define MIN_BUFFER_COUNT_UNDEQUEUE      2
#define MALI_GRALLOC_USAGE_NO_AFBC      0x20000000U

static uint64_t g_session_id;

#if HAVE_VDPP
static int gsFrameCcount = 0;
static void debugShowFPS()
{
    static int mFrameCount;
    static int mLastFrameCount = 0;
    static nsecs_t mLastFpsTime = 0;
    static float mFps = 0;

    mFrameCount++;
    nsecs_t now = systemTime();
    nsecs_t diff = now - mLastFpsTime;
    if (diff > ms2ns(500)) {
        mFps =  ((mFrameCount - mLastFrameCount) * float(s2ns(1))) / diff;
        mLastFpsTime = now;
        mLastFrameCount = mFrameCount;
        ALOGD("mFrameCount = %d mFps = %2.3f",mFrameCount, mFps);
    }
}

static vdpp_buffer_handle* get_vdpp_buffer(struct vdpp_dev* mVdppDev, vt_buffer_t *buffer, int mVTID) {
    struct vdpp_buffer_handle* vdppBuffer = NULL;

    if (vdpp_access(mVdppDev, buffer)) {
        mVdppDev->tunnel_id = mVTID;
        vdpp_dev_init(mVdppDev, buffer->handle);
        vdppBuffer = vdpp_get_unused_buf(mVdppDev);
    }

    return vdppBuffer;
}
#endif

RTSidebandWindow::RTSidebandWindow()
        : mVTDevFd(-1),
          mVTID(-1),
          mBufferCnt(0),
          mVTunnelErr(false),
          mRenderingCnt(0) {
    FUNCTION_IN();

    memset(&mWinAttr, 0, sizeof(vt_win_attr_t));
}

RTSidebandWindow::~RTSidebandWindow() {
    FUNCTION_IN();
}

status_t RTSidebandWindow::init(const vt_win_attr_t *attr) {
    FUNCTION_IN();
    status_t err = 0;

    if (attr->struct_size != sizeof(vt_win_attr_t)) {
        ALOGE("init: sideband window info struct size is invailed!");
        goto __FAILED;
    }

    memcpy(&mWinAttr, attr, sizeof(vt_win_attr_t));

    mVTDevFd = rk_vt_open();
    if (mVTDevFd < 0) {
        goto __FAILED;
    }
    err = rk_vt_alloc_id(mVTDevFd, &mVTID);
    if (err < 0 || mVTID < 0) {
        goto __ALLOC_ID_FAILED;
    }
    err = rk_vt_connect(mVTDevFd, mVTID, RKVT_ROLE_PRODUCER);
    if (err <  0) {
        goto __CONNECT_FALED;
    }

#if HAVE_VDPP
    mVdppDev = (struct vdpp_dev*)calloc(sizeof(struct vdpp_dev), 1);
    if (mVdppDev)
        vdpp_create_ctx(mVdppDev);
#endif

    return 0;
__CONNECT_FALED:
    rk_vt_free_id(mVTDevFd, mVTID);
__ALLOC_ID_FAILED:
    rk_vt_close(mVTDevFd);
__FAILED:
    return -1;
}

status_t RTSidebandWindow::release() {
    FUNCTION_IN();

    if (mVTID >= 0) {
        rk_vt_reset(mVTDevFd, mVTID);
        rk_vt_disconnect(mVTDevFd, mVTID, RKVT_ROLE_PRODUCER);
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

#if HAVE_VDPP
    {
        android::Mutex::Autolock _l(mVdppLock);
        mVdppReplaceQueue.clear();
        mVdppRenderingQueue.clear();
        if (mVdppDev) {
            vdpp_destroy_ctx(mVdppDev);
            pthread_mutex_destroy(&mVdppDev->vdppLock);
            free(mVdppDev);
            mVdppDev = NULL;
        }
    }
#endif
    return 0;
}

status_t RTSidebandWindow::start() {
    FUNCTION_IN();
    return 0;
}

status_t RTSidebandWindow::stop() {
    FUNCTION_IN();
    return 0;
}

status_t RTSidebandWindow::flush() {
    FUNCTION_IN();
#if HAVE_VDPP
    {
        android::Mutex::Autolock _l(mVdppLock);
        std::list<struct vdpp_buffer_handle *>::iterator it;
        for (it = mVdppRenderingQueue.begin(); it != mVdppRenderingQueue.end(); it++) {
            struct vdpp_buffer_handle *hdl = *it;
            hdl->used = false;
            mVdppRenderingQueue.erase(it);
        }
        mVdppReplaceQueue.clear();
    }
#endif

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
    FUNCTION_IN();
    android::Mutex::Autolock _l(mLock);

    if (attr->struct_size != sizeof(vt_win_attr_t)) {
        ALOGE("setAttr: sideband window info struct size is invailed!");
        return -1;
    }

    memcpy(&mWinAttr, attr, sizeof(vt_win_attr_t));

    return 0;
}

status_t RTSidebandWindow::getAttr(vt_win_attr_t *info) {
    FUNCTION_IN();
    android::Mutex::Autolock _l(mLock);

    memcpy(info, &mWinAttr, sizeof(vt_win_attr_t));

    return 0;
}

status_t RTSidebandWindow::allocateSidebandHandle(buffer_handle_t *handle) {
    FUNCTION_IN();
    native_handle_t *temp_buffer = NULL;
    vt_sideband_data_t info;

    memset(&info, 0, sizeof(vt_sideband_data_t));

    g_session_id++;
    info.version        = sizeof(vt_sideband_data_t);
    info.tunnel_id      = mVTID;
    info.crop.left      = mWinAttr.left;
    info.crop.top       = mWinAttr.top;
    info.crop.right     = mWinAttr.right;
    info.crop.bottom    = mWinAttr.bottom;
    info.width          = mWinAttr.width;
    info.height         = mWinAttr.height;
    info.format         = mWinAttr.format;
    info.transform      = mWinAttr.transform;
    info.usage          = mWinAttr.usage;
    info.data_space     = mWinAttr.data_space;
    info.compress_mode  = mWinAttr.compress_mode;
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
    FUNCTION_IN();
    GraphicBufferAllocator &allocator = GraphicBufferAllocator::get();
    buffer_handle_t tempBuffer = NULL;
    uint32_t outStride = 0;
    vt_buffer_t *vtBuffer = NULL;
    status_t err = 0;
    ANativeWindowBuffer *nativeWinBuf = NULL;
    ANativeWindow *nativeWindow = (ANativeWindow *)mWinAttr.native_window;

    if (nativeWindow) {
        err = native_window_dequeue_buffer_and_wait(nativeWindow, &nativeWinBuf);
        if (err != NO_ERROR) {
            return err;
        }

        vtBuffer = rk_vt_buffer_malloc();
        vtBuffer->handle = (native_handle_t *)nativeWinBuf->handle;
        vtBuffer->buffer_mode = RKVT_BUFFER_EXTERNAL;
        vtBuffer->private_data = (int64_t)nativeWinBuf;
    } else {
        err = allocator.allocate(mWinAttr.width,
                                  mWinAttr.height,
                                  mWinAttr.format,
                                  1,
                                  mWinAttr.usage,
                                  &tempBuffer,
                                  &outStride,
                                  0,
                                  std::move("videotunnel"));
        if (err != NO_ERROR) {
            return err;
        }

        vtBuffer = rk_vt_buffer_malloc();
        vtBuffer->handle = (native_handle_t *)tempBuffer;
    }
    *buffer = vtBuffer;
    ALOGI("allocate buffer: fd-0[%d] wxh[%d %d] fmt[0x%x] usage[%p] mode[%s] priv[%p]",
           vtBuffer->handle->data[0], mWinAttr.width, mWinAttr.height,
           mWinAttr.format, (void *)mWinAttr.usage,
           vtBuffer->buffer_mode == RKVT_BUFFER_INTERNAL ? "internal" : "external",
           (void *)vtBuffer->private_data);

    return 0;
}

status_t RTSidebandWindow::freeBuffer(vt_buffer_t **buffer) {
    FUNCTION_IN();
    GraphicBufferAllocator &allocator = GraphicBufferAllocator::get();
    ANativeWindow *nativeWindow = (ANativeWindow *)mWinAttr.native_window;

    ALOGI("free buffer: fd-0[%d] wxh[%d %d] fmt[0x%x] usage[%p]",
           (*buffer)->handle->data[0], mWinAttr.width, mWinAttr.height,
           mWinAttr.format, (void *)mWinAttr.usage);
    if (nativeWindow && (*buffer)->private_data) {
        nativeWindow->cancelBuffer(nativeWindow,
                            (ANativeWindowBuffer *)((*buffer)->private_data), -1);
    } else {
        allocator.free((*buffer)->handle);
    }
    (*buffer)->handle = NULL;
    rk_vt_buffer_free(buffer);
    *buffer = NULL;

    return 0;
}

vt_buffer_t* RTSidebandWindow::getSidebandOriginalBuffer(vt_buffer_t *buffer) {
    struct vdpp_buffer_handle* outVdppHdl = NULL;
    vt_buffer_t *tmpBuffer = NULL;
    bool found = false;

#if HAVE_VDPP
    android::Mutex::Autolock _Vdppl(mVdppLock);
    for (auto it = mVdppRenderingQueue.begin(); it != mVdppRenderingQueue.end(); it++) {
        struct vdpp_buffer_handle* outVdppHdl = *it;
        if (outVdppHdl && outVdppHdl->vtBuffer == buffer) {
            outVdppHdl->used = false;
            mVdppRenderingQueue.erase(it);
            outVdppHdl = NULL;
            found = true;
            break;
        }
    }

    if (found) {
        if (mVdppReplaceQueue.size()) {
            tmpBuffer = mVdppReplaceQueue.front();
            mVdppReplaceQueue.pop_front();
        }
    }
#endif

    return tmpBuffer;
}

vt_buffer_t* RTSidebandWindow::findVtBufferByNativeWindow(
        ANativeWindowBuffer *nativeWinBuf) {
    vt_buffer_t *vtBuf = NULL;

    for (auto it : mBufferQueue) {
        if (it->private_data == (int64_t)nativeWinBuf) {
            vtBuf = it;
            break;
        }
    }

    return vtBuf;
}

status_t RTSidebandWindow::dequeueBuffer(vt_buffer_t **buffer, int timeout_ms, int *fence) {
    FUNCTION_IN();
    int err = 0;
    vt_buffer_t *tmpBuffer = NULL;
    ANativeWindowBuffer *nativeWinBuf = NULL;
    ANativeWindow *nativeWindow = (ANativeWindow *)mWinAttr.native_window;

    {
        android::Mutex::Autolock _l(mLock);
        if (mBufferQueue.size() < mWinAttr.buffer_cnt) {
            err = allocateBuffer(buffer);
            if (err == 0) {
                mBufferQueue.push_back(*buffer);
            }
            return err;
        }
    }

    if (nativeWindow && mVTunnelErr) {
        err = native_window_dequeue_buffer_and_wait(nativeWindow, &nativeWinBuf);
        tmpBuffer = findVtBufferByNativeWindow(nativeWinBuf);
    } else {
        err = rk_vt_dequeue_buffer(mVTDevFd, mVTID, timeout_ms, &tmpBuffer);
    }
    if (err != 0 && tmpBuffer == NULL) {
        return err;
    }

#if HAVE_VDPP
    vdpp_update_disp_rect(mVdppDev, tmpBuffer);
    vt_buffer_t *mSideBandBuffer = getSidebandOriginalBuffer(tmpBuffer);
    if (mSideBandBuffer)
        tmpBuffer = mSideBandBuffer;
    mRenderingCnt--;
#endif
    *buffer = tmpBuffer;
    *fence = -1;

    return 0;
}

status_t RTSidebandWindow::queueBuffer(vt_buffer_t *buffer, int fence, int64_t expected_present_time) {
    FUNCTION_IN();
    struct vdpp_buffer_handle* outVdppHdl = NULL;
    ANativeWindow *nativeWindow = (ANativeWindow *)mWinAttr.native_window;

    mRenderingCnt++;
    buffer->crop.left = mWinAttr.left;
    buffer->crop.top = mWinAttr.top;
    buffer->crop.right = mWinAttr.right;
    buffer->crop.bottom = mWinAttr.bottom;

#if HAVE_VDPP
    outVdppHdl = get_vdpp_buffer(mVdppDev, buffer, mVTID);
    if (outVdppHdl) {
        vdpp_process_frame(mVdppDev, buffer, outVdppHdl->vtBuffer);
        outVdppHdl->vtBuffer->crop.left = 0;
        outVdppHdl->vtBuffer->crop.top = 0;
        outVdppHdl->vtBuffer->crop.right = mVdppDev->disp_rect.right;
        outVdppHdl->vtBuffer->crop.bottom = mVdppDev->disp_rect.bottom;
        android::Mutex::Autolock _Vdppl(mVdppLock);
        mVdppRenderingQueue.push_back(outVdppHdl);
        mVdppReplaceQueue.push_back(buffer);
        buffer = outVdppHdl->vtBuffer;
        debugShowFPS();
    }
#endif
    if (nativeWindow && !mVTunnelErr && buffer->private_data) {
        if (!rk_vt_query_has_consumer(mVTDevFd, mVTID)) {
            ALOGW("can't find consumer, change to queue surfaceflinger.");
            mVTunnelErr = true;
            native_window_set_sideband_stream(nativeWindow, NULL);
        }
    }
    if (nativeWindow && mVTunnelErr && buffer->private_data) {
        return nativeWindow->queueBuffer(nativeWindow,
                    (ANativeWindowBuffer *)(buffer->private_data), -1);
    } else {
        return rk_vt_queue_buffer(mVTDevFd, mVTID, (vt_buffer_t *)buffer, expected_present_time);
    }
}

status_t RTSidebandWindow::cancelBuffer(vt_buffer_t *buffer) {
    FUNCTION_IN();
    struct vdpp_buffer_handle* vdppBuffer = NULL;
    ANativeWindow *nativeWindow = (ANativeWindow *)mWinAttr.native_window;

    {
        android::Mutex::Autolock _l(mLock);
        if (mRenderingCnt >= mWinAttr.remain_cnt) {
            for (auto it = mBufferQueue.begin(); it != mBufferQueue.end(); it++) {
                if (*it == buffer) {
                    mBufferQueue.erase(it);
                    return freeBuffer(&buffer);
                }
            }
            ALOGW("cancel buffer(%p) fd-0(%d) not allocate by sideband window.",
                   buffer, buffer->handle->data[0]);
        }
    }
    mRenderingCnt++;

#if HAVE_VDPP
    vdppBuffer = get_vdpp_buffer(mVdppDev, buffer, mVTID);
    if (vdppBuffer) {
        android::Mutex::Autolock _Vdppl(mVdppLock);
        mVdppRenderingQueue.push_back(vdppBuffer);
        mVdppReplaceQueue.push_back(buffer);
        buffer = vdppBuffer->vtBuffer;
    }
#endif

    if (nativeWindow && mVTunnelErr && buffer->private_data) {
        return nativeWindow->cancelBuffer(nativeWindow,
                            (ANativeWindowBuffer *)(buffer->private_data), -1);
    } else {
        return rk_vt_cancel_buffer(mVTDevFd, mVTID, buffer);
    }
}

}

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

#ifndef ROCKIT_OSAL_RTSIDEBANDWINDOW_H_
#define ROCKIT_OSAL_RTSIDEBANDWINDOW_H_

#include <cutils/native_handle.h>
#include <utils/RefBase.h>
#include <utils/Errors.h>
#include <utils/Mutex.h>
#include <list>
#include <atomic>
#include <system/window.h>

#include "video_tunnel.h"
#if HAVE_VDPP
#include "vdpp/vdpp_proc.h"
#endif

namespace android {

class RTSidebandWindow : public RefBase {
 public:
    RTSidebandWindow();
    virtual ~RTSidebandWindow();

    status_t init(const vt_win_attr_t *attr);
    status_t release();
    status_t start();
    status_t stop();
    status_t flush();

    status_t setAttr(const vt_win_attr_t *attr);
    status_t getAttr(vt_win_attr_t *attr);

    status_t dequeueBuffer(vt_buffer_t **buffer, int timeout_ms, int *fence);
    status_t queueBuffer(vt_buffer_t *buffer, int fence, int64_t expected_present_time);
    status_t cancelBuffer(vt_buffer_t *buffer);
    status_t allocateSidebandHandle(buffer_handle_t *handle);

 private:
    RTSidebandWindow(const RTSidebandWindow& other);
    RTSidebandWindow& operator=(const RTSidebandWindow& other);
    status_t allocateBuffer(vt_buffer_t **buffer);
    status_t freeBuffer(vt_buffer_t **buffer);
    vt_buffer_t* getSidebandOriginalBuffer(vt_buffer_t *buffer);
    vt_buffer_t* findVtBufferByNativeWindow(ANativeWindowBuffer *nativeWinBuf);

 private:
    vt_win_attr_t        mWinAttr;
    android::Mutex       mLock;
    int                  mVTDevFd;
    int                  mVTID;
    int                  mBufferCnt;
    bool                 mVTunnelErr;
    std::atomic<uint32_t>    mRenderingCnt;
    std::list<vt_buffer_t *> mBufferQueue;
#if HAVE_VDPP
    struct vdpp_dev*    mVdppDev;
    android::Mutex       mVdppLock;
    std::list<struct vdpp_buffer_handle *> mVdppRenderingQueue;
    std::list<vt_buffer_t *> mVdppReplaceQueue;
#endif
};

}


#endif


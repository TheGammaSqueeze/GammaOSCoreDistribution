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

#include "common/TvInput_Buffer_Manager_gralloc4_impl.h"
#include <cutils/native_handle.h>
#include <utils/RefBase.h>
#include <hardware/hardware.h>
#include <hardware/gralloc.h>
#include "MessageQueue.h"
#include "MessageThread.h"
#include "BufferData.h"
#include <utils/Errors.h>
#include <utils/Mutex.h>
#include <utils/Condition.h>
#include <list>
#include <atomic>
#include <vector>
#include <system/window.h>
#include <sys/types.h>
#include <inttypes.h>
#include "rt_type.h"   // NOLINT
#include "common/Utils.h"
#include "video_tunnel.h"

#ifdef LOG_TAG
#undef LOG_TAG
#define LOG_TAG "tv_input_Sideband"
#endif

namespace android {

class DrmVopRender;
class RTSidebandWindow : public RefBase, IMessageHandler {
 public:
    RTSidebandWindow();
    virtual ~RTSidebandWindow();

    enum MessageId {
        MESSAGE_ID_UNKNOWN = 0,
        MESSAGE_ID_EXIT,
        MESSAGE_ID_RENDER_REQUEST,
        MESSAGE_ID_DEQUEUE_REQUEST,
        MESSAGE_ID_FLUSH,
        MESSAGE_ID_MAX
    };
    struct Message {
        MessageId id;
        rt_stream_buffer_t streamBuffer;
    };

    status_t init(const vt_win_attr_t *attr, int sidebandType);
    status_t release();
    status_t stop();
    //vtunnel
    status_t flush();
    status_t setAttr(const vt_win_attr_t *attr);
    status_t getAttr(vt_win_attr_t *attr);
    status_t dequeueBuffer(vt_buffer_t **buffer, int timeout_ms, int *fence);
    status_t queueBuffer(vt_buffer_t *buffer, int fence, int64_t expected_present_time);
    status_t cancelBuffer(vt_buffer_t *buffer);
    status_t allocateSidebandHandle(buffer_handle_t *handle, int VTId);
    status_t allocateBuffer(vt_buffer_t **buffer,
        int32_t width, int32_t height, int32_t format, uint64_t usage);
    status_t freeBuffer(vt_buffer_t **buffer);

    status_t flushCache(buffer_handle_t buffer);
    status_t allocateBuffer(buffer_handle_t *buffer);
    status_t freeBuffer(buffer_handle_t *buffer, int type);
    status_t allocateSidebandHandle(buffer_handle_t *handle, int32_t width, int32_t height,
        int32_t format, uint64_t usage);
    int getBufferHandleFd(buffer_handle_t buffer);
    int getBufferLength(buffer_handle_t buffer);

    status_t setBufferGeometry(int32_t width, int32_t height, int32_t format);
    status_t setCrop(int32_t left, int32_t top, int32_t right, int32_t bottom);

    status_t dumpImage(buffer_handle_t handle, char* fileName, int mode);
    void readDataFromFile(const char *file_path, buffer_handle_t outHandle);

    int32_t  getWidth() { return mSidebandInfo.width; }
    int32_t  getHeight() { return mSidebandInfo.height; }
    int32_t  getFormat() { return mSidebandInfo.format; }
    int importHidlHandleBufferLocked(/*in&out*/buffer_handle_t& rawHandle);
    int buffDataTransfer(buffer_handle_t srcHandle, buffer_handle_t dstRawHandle);
    int buffDataTransfer2(buffer_handle_t srcHandle, buffer_handle_t dstRawHandle);
    int NV24ToNV12(buffer_handle_t srcHandle, buffer_handle_t dstHandle, int width, int height);
    status_t show(buffer_handle_t buffer, int displayRadio, int hdmiInType);
    status_t clearVopArea();
    void setDebugLevel(int debugLevel);
    int getSidebandPlaneId();

 private:
    RTSidebandWindow(const RTSidebandWindow& other);
    RTSidebandWindow& operator=(const RTSidebandWindow& other);
    status_t allocateBuffer(vt_buffer_t **buffer);
    int writeData2File(const char *fileName, void *data, int dataSize);

    virtual void messageThreadLoop();
    virtual status_t requestExitAndWait();
    virtual status_t handleMessageExit();
    virtual status_t handleRenderRequest(Message &msg);
    virtual status_t handleDequeueRequest(Message &msg);
    virtual status_t handleFlush();

 private:
    common::TvInputBufferManager* mBuffMgr;
    alloc_device_t      *mAllocDevice;
    DrmVopRender        *mVopRender;
    vt_win_attr_t        mSidebandInfo;
    int                  mVTDevFd;
    int                  mVTID;
    std::atomic<uint32_t>    mRenderingCnt;
    std::list<vt_buffer_t *> mBufferQueue;

    bool                                mThreadRunning;
    MessageQueue<Message, MessageId>    mMessageQueue;
    std::vector<buffer_handle_t>        mRenderingQueue;
    std::unique_ptr<MessageThread>      mMessageThread;
    android::Mutex                      mLock;
    android::Condition                  mBufferAvailCondition;
    int mDebugLevel;
    int mSidebandType;
};

}


#endif


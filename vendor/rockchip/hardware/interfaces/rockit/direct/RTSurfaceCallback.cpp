/*
 * Copyright 2018 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "RTSurfaceCallback"

#include <string.h>
#include <gralloc_priv_omx.h>
#include <ui/GraphicBufferAllocator.h>

#include "RTSurfaceCallback.h"
#include "RockitPlayer.h"

#include "video_tunnel_win.h"
#include "RTVdecExtendFeature.h"

using namespace ::android;

RTSurfaceCallback::RTSurfaceCallback(const sp<IGraphicBufferProducer> &bufferProducer)
    : mSidebandHandle(NULL),
      mSidebandWin(NULL) {
    mNativeWindow = new Surface(bufferProducer, true);
}

RTSurfaceCallback::~RTSurfaceCallback() {
    ALOGD("~RTSurfaceCallback(%p) construct", this);
    if (mSidebandHandle) {
        native_handle_delete((native_handle_t *)mSidebandHandle);
        mSidebandHandle = NULL;
    }
    if (mSidebandWin != NULL) {
        rk_vt_win_destroy(&mSidebandWin);
    }

    if (mNativeWindow.get() != NULL) {
        native_window_set_sideband_stream(mNativeWindow.get(), NULL);
        mNativeWindow.clear();
    }
}

INT32 RTSurfaceCallback::setNativeWindow(const sp<IGraphicBufferProducer> &bufferProducer) {
    if (bufferProducer.get() == NULL)
        return 0;

    if(getNativeWindow() == NULL) {
        mNativeWindow = new Surface(bufferProducer, true);
    } else {
        ALOGD("already set native window");
    }
    return 0;
}

INT32 RTSurfaceCallback::connect(INT32 mode) {
    ALOGV("%s %d in", __FUNCTION__, __LINE__);
    (void)mode;
    if (getNativeWindow() == NULL)
        return -1;

    return native_window_api_connect(mNativeWindow.get(), NATIVE_WINDOW_API_MEDIA);
}

INT32 RTSurfaceCallback::disconnect(INT32 mode) {
    ALOGV("%s %d in", __FUNCTION__, __LINE__);
    (void)mode;
    if (getNativeWindow() == NULL)
        return -1;

    // if native window disconnect. we need clear old buffer,
    // so we should flush the sideband win for clear buffer maps.
    if (mSidebandWin != NULL) {
        rk_vt_win_flush(mSidebandWin);
    }

    return native_window_api_disconnect(mNativeWindow.get(), NATIVE_WINDOW_API_MEDIA);;
}

INT32 RTSurfaceCallback::allocateBuffer(RTNativeWindowBufferInfo *info) {
    INT32                       ret = 0;
    buffer_handle_t             bufferHandle = NULL;
    gralloc_private_handle_t    privHandle;
    ANativeWindowBuffer        *buf = NULL;
    vt_buffer_t                *vtBuf = NULL;

    memset(info, 0, sizeof(RTNativeWindowBufferInfo));
    if (mSidebandWin != NULL) {
        ret = rk_vt_win_dequeueBufferAndWait(mSidebandWin, &vtBuf);
        if (vtBuf) {
            bufferHandle = vtBuf->handle;
        }
    } else {
        if (getNativeWindow() == NULL)
            return -1;

        ret = native_window_dequeue_buffer_and_wait(mNativeWindow.get(), &buf);
        if (buf) {
            bufferHandle = buf->handle;
        }

    }

    if (bufferHandle) {
        Rockchip_get_gralloc_private((UINT32 *)bufferHandle, &privHandle);

        if (mSidebandWin != NULL) {
            info->windowBuf = (void *)vtBuf;
        } else {
            info->windowBuf = (void *)buf;
        }

        // use buffer_handle binder
        info->name = 0xFFFFFFFE;
        info->size = privHandle.size;
        info->dupFd = privHandle.share_fd;
    }

    return 0;
}

INT32 RTSurfaceCallback::freeBuffer(void *buf, INT32 fence) {
    ALOGV("%s %d buf=%p in", __FUNCTION__, __LINE__, buf);
    INT32 ret = 0;
    if (mSidebandWin != NULL) {
        ret = rk_vt_win_cancelBuffer(mSidebandWin, (vt_buffer_t *)buf);
    } else {
        if (getNativeWindow() == NULL)
            return -1;

        ret = mNativeWindow->cancelBuffer(mNativeWindow.get(), (ANativeWindowBuffer *)buf, fence);
    }

    return ret;
}

INT32 RTSurfaceCallback::remainBuffer(void *buf, INT32 fence) {
    ALOGV("%s %d buf=%p in", __FUNCTION__, __LINE__, buf);
    INT32 ret = 0;
    if (mSidebandWin != NULL) {
        ret = rk_vt_win_cancelBuffer(mSidebandWin, (vt_buffer_t *)buf);
    } else {
        if (getNativeWindow() == NULL)
            return -1;

        ret = mNativeWindow->cancelBuffer(mNativeWindow.get(), (ANativeWindowBuffer *)buf, fence);
    }

    return ret;
}

INT32 RTSurfaceCallback::queueBuffer(void *buf, INT32 fence) {
    ALOGV("%s %d buf=%p in", __FUNCTION__, __LINE__, buf);
    INT32 ret = 0;
    if (mSidebandWin != NULL) {
        ret = rk_vt_win_queueBuffer(mSidebandWin, (vt_buffer_t *)buf, fence, 0);
    } else {
        if (getNativeWindow() == NULL)
            return -1;

        ret = mNativeWindow->queueBuffer(mNativeWindow.get(), (ANativeWindowBuffer *)buf, fence);
    }
    return ret;
}

INT32 RTSurfaceCallback::dequeueBuffer(void **buf) {
    ALOGV("%s %d in", __FUNCTION__, __LINE__);
    (void)buf;
    return 0;

}

INT32 RTSurfaceCallback::dequeueBufferAndWait(RTNativeWindowBufferInfo *info) {
    ALOGV("%s %d in", __FUNCTION__, __LINE__);
    INT32                       ret = 0;
    buffer_handle_t             bufferHandle = NULL;
    gralloc_private_handle_t    privHandle;
    ANativeWindowBuffer *buf = NULL;
    vt_buffer_t *vtBuf = NULL;

    memset(info, 0, sizeof(RTNativeWindowBufferInfo));
    if (mSidebandWin != NULL) {
        ret = rk_vt_win_dequeueBufferAndWait(mSidebandWin, &vtBuf);
        if (vtBuf) {
            bufferHandle = vtBuf->handle;
        }
    } else {
        if (getNativeWindow() == NULL)
            return -1;

        ret = native_window_dequeue_buffer_and_wait(mNativeWindow.get(), &buf);
        if (buf) {
            bufferHandle = buf->handle;
        }
    }

    if (bufferHandle) {
        Rockchip_get_gralloc_private((UINT32 *)bufferHandle, &privHandle);

        if (mSidebandWin != NULL) {
            info->windowBuf = (void *)vtBuf;
        } else {
            info->windowBuf = (void *)buf;
        }
        info->dupFd = privHandle.share_fd;
    }
    return ret;
}

INT32 RTSurfaceCallback::mmapBuffer(RTNativeWindowBufferInfo *info, void **ptr) {
    status_t err = OK;
    ANativeWindowBuffer *buf = NULL;
    void *tmpPtr = NULL;

    if (info->windowBuf == NULL || ptr == NULL) {
        ALOGE("lockBuffer bad value, windowBuf=%p, &ptr=%p", info->windowBuf, ptr);
        return RT_ERR_VALUE;
    }

    if (mSidebandWin != NULL)
        return RT_ERR_UNSUPPORT;

    buf = static_cast<ANativeWindowBuffer *>(info->windowBuf);

    sp<GraphicBuffer> graphicBuffer(GraphicBuffer::from(buf));
    err = graphicBuffer->lock(GRALLOC_USAGE_SW_WRITE_OFTEN, &tmpPtr);
    if (err != OK) {
        ALOGE("graphicBuffer lock failed err - %d", err);
        return RT_ERR_BAD;
    }

    *ptr = tmpPtr;

    return RT_OK;
}

INT32 RTSurfaceCallback::munmapBuffer(void **ptr, INT32 size, void *buf) {
    status_t err = OK;
    (void)ptr;
    (void)size;

    if (mSidebandWin != NULL)
        return RT_ERR_UNSUPPORT;

    sp<GraphicBuffer> graphicBuffer(
            GraphicBuffer::from(static_cast<ANativeWindowBuffer *>(buf)));
    err = graphicBuffer->unlock();
    if (err != OK) {
        ALOGE("graphicBuffer unlock failed err - %d", err);
        return RT_ERR_BAD;
    }

    return RT_OK;
}

INT32 RTSurfaceCallback::setCrop(
        INT32 left,
        INT32 top,
        INT32 right,
        INT32 bottom) {
    ALOGV("%s %d in crop(%d,%d,%d,%d)", __FUNCTION__, __LINE__, left, top, right, bottom);
    android_native_rect_t crop;

    if (mSidebandWin != NULL) {
        vt_win_attr_t attr;
        rk_vt_win_getAttr(mSidebandWin, &attr);
        attr.left = left;
        attr.top = top;
        attr.right = right;
        attr.bottom = bottom;
        return rk_vt_win_setAttr(mSidebandWin, &attr);
    }

    crop.left = left;
    crop.top = top;
    crop.right = right;
    crop.bottom = bottom;

    if (getNativeWindow() == NULL)
        return -1;

    return native_window_set_crop(mNativeWindow.get(), &crop);
}

INT32 RTSurfaceCallback::setUsage(INT32 usage) {
    ALOGV("%s %d in usage=0x%x", __FUNCTION__, __LINE__, usage);
    if (getNativeWindow() == NULL)
        return -1;

    return native_window_set_usage(mNativeWindow.get(), usage);;
}

INT32 RTSurfaceCallback::setScalingMode(INT32 mode) {
    ALOGV("%s %d in", __FUNCTION__, __LINE__);
    if (getNativeWindow() == NULL)
        return -1;

    return native_window_set_scaling_mode(mNativeWindow.get(), mode);;
}

INT32 RTSurfaceCallback::setDataSpace(INT32 dataSpace) {
    ALOGV("%s %d in dataSpace=0x%x", __FUNCTION__, __LINE__, dataSpace);
    if (getNativeWindow() == NULL)
        return -1;

    return native_window_set_buffers_data_space(mNativeWindow.get(), (android_dataspace_t)dataSpace);
}

INT32 RTSurfaceCallback::setTransform(INT32 transform) {
    ALOGV("%s %d in", __FUNCTION__, __LINE__);
    if (getNativeWindow() == NULL)
        return -1;

    return native_window_set_buffers_transform(mNativeWindow.get(), transform);
}

INT32 RTSurfaceCallback::setSwapInterval(INT32 interval) {
    ALOGV("%s %d in", __FUNCTION__, __LINE__);
    (void)interval;
    return 0;
}

INT32 RTSurfaceCallback::setBufferCount(INT32 bufferCount) {
    ALOGV("%s %d in", __FUNCTION__, __LINE__);
    if (getNativeWindow() == NULL)
        return -1;

    return native_window_set_buffer_count(mNativeWindow.get(), bufferCount);
}

INT32 RTSurfaceCallback::setBufferGeometry(
        INT32 width,
        INT32 height,
        INT32 format) {
    ALOGV("%s %d in width=%d, height=%d, format=0x%x", __FUNCTION__, __LINE__, width, height, format);
    if (getNativeWindow() == NULL)
        return -1;

    native_window_set_buffers_dimensions(mNativeWindow.get(), width, height);
    native_window_set_buffers_format(mNativeWindow.get(), format);
    if (mSidebandWin != NULL) {
        vt_win_attr_t attr;
        rk_vt_win_getAttr(mSidebandWin, &attr);
        attr.width = width;
        attr.height = height;
        attr.format = format;
        return rk_vt_win_setAttr(mSidebandWin, &attr);
    }

    return 0;
}

INT32 RTSurfaceCallback::setSidebandStream(RTSidebandInfo info) {
    ALOGV("%s %d in", __FUNCTION__, __LINE__);
    status_t err = OK;

    if (getNativeWindow() == NULL)
        return -1;

    if (mSidebandWin == NULL) {
        vt_win_attr_t attr;

        memset(&attr, 0, sizeof(vt_win_attr_t));
        attr.struct_size = sizeof(vt_win_attr_t);
        attr.struct_ver = 0;
        attr.left = info.left;
        attr.top = info.top;
        attr.right = info.right;
        attr.bottom = info.bottom;
        attr.usage = info.usage;
        attr.width = info.width;
        attr.height = info.height;
        attr.format = info.format;
        attr.data_space = info.dataSpace;
        attr.compress_mode = info.compressMode;
        attr.transform = info.transform;
        attr.buffer_cnt = info.bufferCnt;
        attr.remain_cnt = info.remainCnt;
        attr.native_window = mNativeWindow.get();
        err = rk_vt_win_create(&attr, &mSidebandWin);
        if (err != 0) {
            ALOGE("sideband winow set attr failed: %s (%d)", strerror(-err), -err);
            return err;
        }
        rk_vt_win_allocSidebandStream(mSidebandWin, &mSidebandHandle);
        if (!mSidebandHandle) {
            ALOGE("allocate buffer from sideband window failed!");
            return -1;
        }
        err = native_window_set_sideband_stream(mNativeWindow.get(), (native_handle_t *)mSidebandHandle);
        if (err != 0) {
            ALOGE("native_window_set_sideband_stream failed: %s (%d)", strerror(-err), -err);
            return err;
        }
    }

    return 0;
}

buffer_handle_t RTSurfaceCallback::buf2hnl(void *buf) {
    buffer_handle_t handle;

    if (mSidebandWin) {
        handle = ((vt_buffer_t *)buf)->handle;
    } else {
        handle = ((ANativeWindowBuffer *)buf)->handle;
    }
    return handle;
}


INT32 RTSurfaceCallback::query(INT32 cmd, INT32 *param) {
    ALOGV("%s %d in", __FUNCTION__, __LINE__);
    INT32 ret = RT_OK;

    switch (cmd) {
      case RT_SURFACE_QUERY_MIN_UNDEQUEUED_BUFFERS : {
        if (getNativeWindow() == NULL)
            return -1;

        ret = mNativeWindow->query(mNativeWindow.get(), cmd, param);
      } break;
      case RT_SURFACE_CMD_SET_HDR_META : {
        RTHdrMeta *hdrMeta = (RTHdrMeta *)param;
        int64_t offset = (int64_t)hdrMeta->offset;
        buffer_handle_t handle = buf2hnl(hdrMeta->buf);

        ret = RTVdecExtendFeature::configFrameHdrDynamicMeta(handle, offset);
      } break;
      case RT_SURFACE_CMD_GET_HDR_META : {

      } break;
      case RT_SURFACE_CMD_SET_SCALE_META : {
        RTScaleMeta *scaleMeta = (RTScaleMeta *)param;
        buffer_handle_t handle = buf2hnl(scaleMeta->buf);

        ret = RTVdecExtendFeature::configFrameScaleMeta(handle, scaleMeta);
      } break;
      case RT_SURFACE_CMD_GET_SCALE_META : {
        RTScaleMeta *scaleMeta = (RTScaleMeta *)param;
        buffer_handle_t handle = buf2hnl(scaleMeta->buf);

        scaleMeta->request = RTVdecExtendFeature::checkNeedScale(handle);
      } break;
      default : {
        ret = RT_ERR_UNSUPPORT;
      } break;
    }

    return ret;
}

void* RTSurfaceCallback::getNativeWindow() {
    return (void *)mNativeWindow.get();
}


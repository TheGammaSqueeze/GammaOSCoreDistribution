/*
 * Copyright 2023 Rockchip Electronics S.LSI Co. LTD
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

#include "HandWritingManager.h"

using namespace android;

HandWritingManager::HandWritingManager() {}

HandWritingManager::~HandWritingManager() {
    ALOGD("%s", __FUNCTION__);
    clear();
    exit();
}

int HandWritingManager::init(int left, int top, int right, int bottom,
        int viewWidth, int viewHeight,int screenWidth, int screenHeight,
        int layerStack) {
    ALOGD("%s left:%d, top:%d, right:%d, bottom:%d, viewWidth:%d, viewHeight:%d, screenWidth:%d, screenHeight:%d, layerStack=%d",
        __FUNCTION__, left, top, right, bottom, viewWidth, viewHeight, screenWidth, screenHeight, layerStack);
    int err = 0;
    // create surface
    sp<SurfaceComposerClient> composerClient = new SurfaceComposerClient;
    err = composerClient->initCheck();
    if (err != NO_ERROR) {
        ALOGE("SurfaceComposerClient initCheck err....");
        return err;
    }
    int z_order = std::numeric_limits<int32_t>::max();
    mSurfaceWidth = viewWidth;
    mSurfaceHeight = viewHeight;
    mSurfaceControl = composerClient->createSurface(
        String8("rk_handwrite_win"), mSurfaceWidth, mSurfaceHeight,
        PIXEL_FORMAT_RGBA_8888);//,
        //0/*ISurfaceComposerClient::eFXSurfaceBufferState*//*ISurfaceComposerClient::eOpaque*/,
        //nullptr);
    if (layerStack > 0) {
        SurfaceComposerClient::Transaction{}
            .setLayer(mSurfaceControl, z_order)
            .setPosition(mSurfaceControl, left, top)
            .setSize(mSurfaceControl, mSurfaceWidth, mSurfaceHeight)
            .setLayerStack(mSurfaceControl, ui::LayerStack::fromValue(layerStack))
            .show(mSurfaceControl)
            .apply();
    } else {
        SurfaceComposerClient::Transaction{}
            .setLayer(mSurfaceControl, z_order)
            .setPosition(mSurfaceControl, left, top)
            .setSize(mSurfaceControl, mSurfaceWidth, mSurfaceHeight)
            .show(mSurfaceControl)
            .apply();
    }
    mSurfaceControl->setDefaultBbqName("rk_handwrite_sf");
    mSurfaceControl->setDefaultBbqChildName("rk_handwrite_sf");
    // conncect awin
    ANativeWindow* nativeWindow = mSurfaceControl->getSurface().get();
    native_window_api_connect(nativeWindow, NATIVE_WINDOW_API_CPU);
    native_window_set_buffers_user_dimensions(nativeWindow, mSurfaceWidth, mSurfaceHeight);
    native_window_set_buffers_format(nativeWindow, PIXEL_FORMAT_RGBA_8888);
    native_window_set_usage(nativeWindow, GRALLOC_USAGE_SW_WRITE_OFTEN/*attr.usage*/);
    //native_window_set_scaling_mode(nativeWindow, NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW);
    int numBufs = 0;
    int minUndequeuedBufs = 0;
    nativeWindow->query(nativeWindow,
            NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS, &minUndequeuedBufs);
    numBufs = minUndequeuedBufs + 1;
    native_window_set_buffer_count(nativeWindow, numBufs);
    // get addr
    sp<Surface> surface = mSurfaceControl->getSurface().get();
    ANativeWindow_Buffer outBuffer;
    ARect rect;
    surface->lock(&outBuffer, &rect);
    surface->unlockAndPost();
    sp<Fence> outFence;
    float outTransformMatrix[16];
    surface->getLastQueuedBuffer(&mOutGraphicBuffer, &outFence, outTransformMatrix);
    mOutGraphicBuffer->lock(GraphicBuffer::USAGE_SW_WRITE_OFTEN, &mVAddr);
    return 1;
}

void HandWritingManager::drawBitmap(void* pixels, int bmp_width, int bmp_height) {
    int buf_width = bmp_width;
    int buf_height = bmp_height;
    int bmp_size = buf_width * buf_height * 4;
    ALOGD("%s bmp_width:%d, bmp_height:%d  %d", __FUNCTION__, bmp_width, bmp_height, bmp_size);
    if (mVAddr) {
        memcpy(mVAddr, (char *)pixels, bmp_size);
    }
}

void HandWritingManager::clear() {
    if (mOutGraphicBuffer) {
        ALOGD("%s", __FUNCTION__);
        memset(mVAddr, 0, mSurfaceWidth*mSurfaceHeight*4);
    }
}

void HandWritingManager::exit() {
    if (mOutGraphicBuffer) {
        ALOGD("%s", __FUNCTION__);
        ANativeWindow* nativeWindow = mSurfaceControl->getSurface().get();
        native_window_api_disconnect(nativeWindow, NATIVE_WINDOW_API_CPU);
        mVAddr = NULL;
        mOutGraphicBuffer->unlock();
        mOutGraphicBuffer = NULL;
        sp<Surface> surface = mSurfaceControl->getSurface().get();
        //surface->clear();
        mSurfaceControl = NULL;
    }
}

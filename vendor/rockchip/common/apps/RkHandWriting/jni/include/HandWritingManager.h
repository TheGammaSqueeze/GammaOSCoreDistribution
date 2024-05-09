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

#ifndef ANDROID_HANDWRITING_MANAGER_H_
#define ANDROID_HANDWRITING_MANAGER_H_

#include <ui/GraphicBuffer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>

using namespace std;
using namespace android;

class HandWritingManager {
    public:
        HandWritingManager();
        ~HandWritingManager();
        int init(int left, int top, int right, int bottom,
            int viewWidth, int viewHeight, int screenWidth, int screenHeight,
            int layerStack);
        void drawBitmap(void* pixels, int bmp_width, int bmp_height);
        void clear();
        void exit();
    private:
        int mSurfaceWidth;
        int mSurfaceHeight;
        sp<SurfaceControl> mSurfaceControl;
        sp<GraphicBuffer> mOutGraphicBuffer = NULL;
        void* mVAddr;
};

#endif
/*
 * Copyright 2023 Rockchip Electronics Co. LTD
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

#ifndef ANDROID_C2_RKVDEC_EXTEND_FEATURE_H_
#define ANDROID_C2_RKVDEC_EXTEND_FEATURE_H_

#include <ui/GraphicBuffer.h>

namespace android {

typedef struct __C2PreScaleParam {
    int32_t thumbWidth;
    int32_t thumbHeight;
    int32_t thumbHorStride;
    int32_t format;
    int32_t yOffset;
    int32_t uvOffset;
} C2PreScaleParam;

class C2VdecExtendFeature {
public:
    static int configFrameHdrDynamicMeta(buffer_handle_t hnd, int64_t offset);
    static int checkNeedScale(buffer_handle_t hnd);
    static int configFrameScaleMeta(buffer_handle_t hnd, C2PreScaleParam *scaleParam);
};

}

#endif  // ANDROID_C2_RKVDEC_EXTEND_FEATURE_H_

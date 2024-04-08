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
 * author: kevin.chen@rock-chips.com
 *   date: 20210414
 * module: chip features define
 */

#ifndef SRC_RT_MEDIA_INCLUDE_RKCHIPFEATURESDEF_H_
#define SRC_RT_MEDIA_INCLUDE_RKCHIPFEATURESDEF_H_

#include <stdio.h>
#include "C2RKChips.h"
#include "mpp/rk_type.h"

typedef enum _C2CompressMode {
    RT_COMPRESS_MODE_NONE = 0,   /* no compress */
    RT_COMPRESS_AFBC_16x16,

    RT_COMPRESS_MODE_BUTT
} C2CompressMode;

typedef struct {
    MppCodingType  codecId;
    C2CompressMode fbcMode;

    /* output padding, for setcrop before display */
    uint32_t       offsetX;
    uint32_t       offsetY;
} C2FbcCaps;

typedef struct {
    const char *chipName;
    RKChipType  chipType;
    int         fbcCapNum;
    C2FbcCaps  *fbcCaps;
    uint32_t    scaleMetaCap     : 1;
    uint32_t    reserved         : 31;
} C2ChipFeatures;

class C2RKChipFeaturesDef {
 public:
    static int   getFbcOutputMode(MppCodingType codecId);
    static void  getFbcOutputOffset(MppCodingType codecId, uint32_t *offsetX, uint32_t *offsetY);
    static uint32_t getScaleMetaCap();
};

#endif  // SRC_RT_MEDIA_INCLUDE_RKCHIPFEATURESDEF_H_
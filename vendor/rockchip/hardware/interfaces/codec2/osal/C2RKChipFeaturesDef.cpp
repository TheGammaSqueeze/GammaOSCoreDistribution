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
 */

#include "C2RKChipFeaturesDef.h"
#include "C2RKLog.h"
#include "C2RKEnv.h"

#include <string.h>

static C2FbcCaps fbcCaps_rk356x[] = {
    { MPP_VIDEO_CodingAVC,  RT_COMPRESS_AFBC_16x16, 0, 4 },
    { MPP_VIDEO_CodingHEVC, RT_COMPRESS_AFBC_16x16, 0, 4 },
    { MPP_VIDEO_CodingVP9,  RT_COMPRESS_AFBC_16x16, 0, 0 },
};

static C2FbcCaps fbcCaps_rk3588[] = {
    { MPP_VIDEO_CodingAVC,  RT_COMPRESS_AFBC_16x16, 0, 4 },
    { MPP_VIDEO_CodingHEVC, RT_COMPRESS_AFBC_16x16, 0, 4 },
    { MPP_VIDEO_CodingVP9,  RT_COMPRESS_AFBC_16x16, 0, 0 },
};

static const C2ChipFeatures FeatrueInfos[] = {
    {
        "unkown",           // chipName
        RK_CHIP_UNKOWN,     // chipType
        0,                  // fbcCapNum
        NULL,               // fbcCaps
        0,                  // scaleMetaCap
        0,                  // reserved
    },
    {
        "rk2928",
        RK_CHIP_2928,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3036",
        RK_CHIP_3036,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3066",
        RK_CHIP_3066,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3188",
        RK_CHIP_3188,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk312x",
        RK_CHIP_312X,
        0,
        NULL,
        0,
        0,
    },
    /* 3128h first for string matching */
    {
        "rk3128h",
        RK_CHIP_3128H,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3128m",
        RK_CHIP_3128M,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3128",
        RK_CHIP_312X,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3126",
        RK_CHIP_312X,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3288",
        RK_CHIP_3288,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3228a",
        RK_CHIP_3228A,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3228b",
        RK_CHIP_3228B,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk322x",
        RK_CHIP_3229,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3229",
        RK_CHIP_3229,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3228h",
        RK_CHIP_3228H,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3328",
        RK_CHIP_3328,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3399",
        RK_CHIP_3399,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3368a",
        RK_CHIP_3368A,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3368h",
        RK_CHIP_3368H,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3368",
        RK_CHIP_3368,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3326",
        RK_CHIP_3326,
        0,
        NULL,
        0,
        0,
    },
    {
        "px30",
        RK_CHIP_3326,
        0,
        NULL,
        0,
        0,
    },
    {
        "rk3566",
        RK_CHIP_3566,
        3,
        fbcCaps_rk356x,
        0,
        0,
    },
    {
        "rk3568",
        RK_CHIP_3568,
        3,
        fbcCaps_rk356x,
        0,
        0,
    },
    {
        "rk3528",
        RK_CHIP_3528,
        3,
        fbcCaps_rk3588,
        1,
        0,
    },
    {
        "rk3588",
        RK_CHIP_3588,
        3,
        fbcCaps_rk3588,
        0,
        0,
    }
};

static const int C2ChipFeatureSize = sizeof(FeatrueInfos) / sizeof((FeatrueInfos)[0]);

int C2RKChipFeaturesDef::getFbcOutputMode(MppCodingType codecId) {
    RKChipInfo *chipInfo = getChipName();

    if (chipInfo == NULL)
        return 0;

    uint32_t value;
    Rockchip_C2_GetEnvU32("codec2_fbc_disable", &value, 0);
    if (value == 1) {
        c2_info("property match, disable fbc output mode");
        return 0;
    }

    int i, j;
    int fbcMode = 0;
    for (i = 0; i < C2ChipFeatureSize; i++) {
        C2ChipFeatures featureInfo = FeatrueInfos[i];

        if (strstr(chipInfo->name, featureInfo.chipName)) {
            for (j = 0; j < featureInfo.fbcCapNum; j++) {
                if (featureInfo.fbcCaps[j].codecId == codecId
                        && featureInfo.fbcCaps[j].fbcMode != 0) {
                    fbcMode = featureInfo.fbcCaps[j].fbcMode;
                    break;
                }
            }
        }
    }

    c2_info("[%s] codec-0x%08x fbc_support_result-%d", chipInfo->name, codecId, fbcMode);

    return fbcMode;
}

void C2RKChipFeaturesDef::getFbcOutputOffset(
        MppCodingType codecId, uint32_t *offsetX, uint32_t *offsetY) {
    *offsetX = *offsetY = 0;
    if (getFbcOutputMode(codecId) == 0) {
        return;
    }

    RKChipInfo *chipInfo = getChipName();

    int i, j;
    for (i = 0; i < C2ChipFeatureSize; i++) {
        C2ChipFeatures featureInfo = FeatrueInfos[i];

        if (strstr(chipInfo->name, featureInfo.chipName)) {
            for (j = 0; j < featureInfo.fbcCapNum; j++) {
                if (featureInfo.fbcCaps[j].codecId == codecId) {
                    *offsetX = featureInfo.fbcCaps[j].offsetX;
                    *offsetY = featureInfo.fbcCaps[j].offsetY;
                    return;
                }
            }
        }
    }
}

uint32_t C2RKChipFeaturesDef::getScaleMetaCap() {
    RKChipInfo *chipInfo = getChipName();

    if (chipInfo == NULL)
        return 0;

    int scaleCap = 0;
    for (int i = 0; i < C2ChipFeatureSize; i++) {
        C2ChipFeatures featureInfo = FeatrueInfos[i];

        if (strstr(chipInfo->name, featureInfo.chipName)) {
            scaleCap = featureInfo.scaleMetaCap;
            break;
        }
    }

    return scaleCap;
}

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

#include "C2RKGrallocDef.h"
#include "C2RKLog.h"
#include "C2RKEnv.h"

#include <string.h>

static const C2GrallocInfo GrallocInfos[] = {
    {
        "unkown",
        0,
    },
    {
        "rk2928",
        3,
    },
    {
        "rk3036",
        3,
    },
    {
        "rk3066",
        3,
    },
    {
        "rk3188",
        3,
    },
    {
        "rk312x",
        3,
    },
    /* 3128h first for string matching */
    {
        "rk3128h",
        3,
    },
    {
        "rk3128m",
        3,
    },
    {
        "rk3128",
        3,
    },
    {
        "rk3126",
        3,
    },
    {
        "rk3288",
        4,
    },
    {
        "rk3228a",
        3,
    },
    {
        "rk3228b",
        3,
    },
    {
        "rk322x",
        3,
    },
    {
        "rk3229",
        3,
    },
    {
        "rk3228h",
        3,
    },
    {
        "rk3328",
        3,
    },
    {
        "rk3399",
        4,
    },
    {
        "rk3368a",
        3,
    },
    {
        "rk3368h",
        3,
    },
    {
        "rk3368",
        3,
    },
    {
        "rk3326",
        4,
    },
    {
        "px30",
        4,
    },
    {
        "rk3566",
        4,
    },
    {
        "rk3568",
        4,
    },
    {
        "rk3588",
        4,
    },
    {
        "rk3562",
        4,
    },
    {
        "rk3528",
        3,
    }
};

static const int GrallocInfoSize = sizeof(GrallocInfos) / sizeof((GrallocInfos)[0]);

uint32_t C2RKGrallocDef::getGrallocVersion() {
    RKChipInfo *chipInfo = getChipName();

    if (chipInfo == NULL)
        return 0;

    int grallocVersion = 0;
    for (int i = 0; i < GrallocInfoSize; i++) {
        C2GrallocInfo fbcInfo = GrallocInfos[i];

        if (strstr(chipInfo->name, fbcInfo.chipName)) {
            grallocVersion = fbcInfo.grallocVersion;
            break;
        }
    }

    c2_info("[%s] gralloc-version-%d", chipInfo->name, grallocVersion);

    return grallocVersion;
}

uint32_t C2RKGrallocDef::getAndroidVerison() {
    uint32_t value;
    Rockchip_C2_GetEnvU32("ro.product.first_api_level", &value, 0);
    c2_info("Android Version %d", value);
    return value;
}

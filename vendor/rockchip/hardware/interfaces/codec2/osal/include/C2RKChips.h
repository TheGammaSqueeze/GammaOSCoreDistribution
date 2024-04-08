/*
 * Copyright 2022 Rockchip Electronics Co. LTD
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

#ifndef C2_RK_CHIPS_H_
#define C2_RK_CHIPS_H_

typedef enum _RKChipType {
    RK_CHIP_UNKOWN = 0,

    //  2928 and 3036 no iep
    RK_CHIP_2928,
    RK_CHIP_3036,

    RK_CHIP_3066,
    RK_CHIP_3188,

    //  iep
    RK_CHIP_3368H,
    RK_CHIP_3368A,
    RK_CHIP_3128H,
    RK_CHIP_3128M,
    RK_CHIP_312X,
    RK_CHIP_3326,

    //  support 10bit chips
    RK_CHIP_10BIT_SUPPORT_BEGIN,

    //  3288 support max width to 3840
    RK_CHIP_3288,

    //  support 4k chips
    RK_CHIP_4K_SUPPORT_BEGIN,
    RK_CHIP_322X_SUPPORT_BEGIN,
    RK_CHIP_3228A,
    RK_CHIP_3228B,
    RK_CHIP_3228H,
    RK_CHIP_3328,
    RK_CHIP_3229,
    RK_CHIP_322X_SUPPORT_END,
    RK_CHIP_3399,
    RK_CHIP_1126,
    RK_CHIP_3562,
    //  support 8k chips
    RK_CHIP_8K_SUPPORT_BEGIN,
    RK_CHIP_3566,
    RK_CHIP_3568,
    RK_CHIP_3528,
    RK_CHIP_3588,
    RK_CHIP_8K_SUPPORT_END,

    RK_CHIP_10BIT_SUPPORT_END,

    RK_CHIP_3368,
    RK_CHIP_4K_SUPPORT_END,
} RKChipType;

typedef struct {
    const char *name;
    RKChipType  type;
} RKChipInfo;

static const RKChipInfo ChipList[] = {
    {"unkown",    RK_CHIP_UNKOWN},
    {"rk2928",    RK_CHIP_2928},
    {"rk3036",    RK_CHIP_3036},
    {"rk3066",    RK_CHIP_3066},
    {"rk3188",    RK_CHIP_3188},
    {"rk312x",    RK_CHIP_312X},
    /* 3128h first for string matching */
    {"rk3128h",   RK_CHIP_3128H},
    {"rk3128m",   RK_CHIP_3128M},
    {"rk3128",    RK_CHIP_312X},
    {"rk3126",    RK_CHIP_312X},
    {"rk3288",    RK_CHIP_3288},
    {"rk3228a",   RK_CHIP_3228A},
    {"rk3228b",   RK_CHIP_3228B},
    {"rk322x",    RK_CHIP_3229},
    {"rk3229",    RK_CHIP_3229},
    {"rk3228h",   RK_CHIP_3228H},
    {"rk3328",    RK_CHIP_3328},
    {"rk3399",    RK_CHIP_3399},
    {"rk3368a",   RK_CHIP_3368A},
    {"rk3368h",   RK_CHIP_3368H},
    {"rk3368",    RK_CHIP_3368},
    {"rk3326",    RK_CHIP_3326},
    {"px30",      RK_CHIP_3326},
    {"rk3566",    RK_CHIP_3566},
    {"rk3568",    RK_CHIP_3568},
    {"rv1126",    RK_CHIP_1126},
    {"rk3588",    RK_CHIP_3588},
    {"rk3562",    RK_CHIP_3562},
    {"rk3528",    RK_CHIP_3528},
};

RKChipInfo* getChipName();

#endif  // C2_RK_CHIPS_H_

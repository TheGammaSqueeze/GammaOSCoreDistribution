/*
 * Copyright (C) 2020 Rockchip Electronics Co. LTD
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

#ifndef ANDROID_C2_RK_RGA_DEF_H__
#define ANDROID_C2_RK_RGA_DEF_H__

#include <stdio.h>

typedef struct {
    int32_t fd;
    int32_t width;
    int32_t height;
    int32_t wstride;
    int32_t hstride;
} RgaInfo;

class C2RKRgaDef {
public:
    static void SetRgaInfo(RgaInfo *param, int32_t fd,
                          int32_t width, int32_t height,
                          int32_t wstride = 0, int32_t hstride = 0);

    static bool RGBToNV12(RgaInfo srcInfo, RgaInfo dstInfo);
    static bool NV12ToNV12(RgaInfo srcInfo, RgaInfo dstInfo);
};

#endif  // ANDROID_C2_RK_RGA_DEF_H__

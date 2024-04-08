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
 * author: hery.xu@rock-chips.com
 *   date: 20220913
 * module: gralloc def.
 */

#ifndef SRC_RT_MEDIA_INCLUDE_C2RKGRALLOCDEF_H_
#define SRC_RT_MEDIA_INCLUDE_C2RKGRALLOCDEF_H_

#include <stdio.h>
#include "C2RKChips.h"

#define GRALLOC_USAGE_RKVDEC_SCALING   0x01000000U

typedef struct {
    const char *chipName;
    int         grallocVersion;
} C2GrallocInfo;

class C2RKGrallocDef {
public:
    static uint32_t   getGrallocVersion();
    static uint32_t   getAndroidVerison();
};

#endif  // SRC_RT_MEDIA_INCLUDE_C2RKGRALLOCDEF_H_

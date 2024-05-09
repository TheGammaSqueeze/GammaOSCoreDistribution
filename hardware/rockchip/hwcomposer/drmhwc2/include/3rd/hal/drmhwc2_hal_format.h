/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef DRMHWC2_HAL_FORMAT_H
#define DRMHWC2_HAL_FORMAT_H

#ifdef ANDROID_S
#include <hardware/hardware_rockchip.h>
#endif

// Android S define HAL_PIXEL_FORMAT_NV30 from
// hardware/rockchip/libhardware_rockchip/include/hardware/hardware_rockchip.h
#ifndef HAL_PIXEL_FORMAT_BGR_888
#define HAL_PIXEL_FORMAT_BGR_888 29
#endif

// Android S define HAL_PIXEL_FORMAT_NV30 from
// hardware/rockchip/libhardware_rockchip/include/hardware/hardware_rockchip.h
#ifndef HAL_PIXEL_FORMAT_NV30
#define HAL_PIXEL_FORMAT_NV30 30
#endif


#endif

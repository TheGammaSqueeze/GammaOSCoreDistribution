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

#ifndef ANDROID_C2_RK_COLORASPECTS_H_
#define ANDROID_C2_RK_COLORASPECTS_H_

#define GRALLOC_COLOR_SPACE_MASK       0x0F000000

enum GrallocColorSpace {
    GRALLOC_NV12_10_BT2020 = 1,
    GRALLOC_NV12_10_HDR_10,
    GRALLOC_NV12_10_HDR_HLG,
    GRALLOC_NV12_10_HDR_DOLBY,
};

enum ColorTransfer : uint32_t  {
    kColorTransferUnspecified = 0,
    kColorTransferLinear = 1,
    kColorTransferSRGB = 2,
    kColorTransferSMPTE_170M = 3, // not in SDK
    kColorTransferGamma22 = 4, // not in SDK
    kColorTransferGamma28 = 5, // not in SDK
    kColorTransferST2084 =	6,
    kColorTransferHLG = 7,
    kColorTransferGamma26 = 8, // not in SDK, new in Android 8.0
    /* This marks a section of color-transfer values that are not supported by graphics HAL,
       but track media-defined color-transfer. These are stable for a given release. */
    kColorTransferExtendedStart = 32,

    /* This marks a section of color-transfer values that are not supported by graphics HAL
       nor defined by media. These may differ per device. */
    kColorTransferVendorStart = 0x10000,
};

#endif  // ANDROID_C2_RK_COLORASPECTS_H_

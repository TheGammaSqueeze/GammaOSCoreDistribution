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

#ifndef DRMHWC2_FOURCC_H
#define DRMHWC2_FOURCC_H
#include "drm_fourcc.h"


#ifndef DRM_FORMAT_NV15
/*
 * 2 plane YCbCr
 * index 0 = Y plane, [39:0] Y3:Y2:Y1:Y0 little endian
 * index 1 = Cr:Cb plane, [39:0] Cr1:Cb1:Cr0:Cb0 little endian
 */
#define DRM_FORMAT_NV15		fourcc_code('N', 'V', '1', '5') /* 2x2 subsampled Cr:Cb plane */
#endif

#ifndef DRM_FORMAT_NV12_10
#define DRM_FORMAT_NV12_10		fourcc_code('N', 'A', '1', '2') /* 2x2 subsampled Cr:Cb plane */
#endif

#ifndef DRM_FORMAT_NV20
#define DRM_FORMAT_NV20		fourcc_code('N', 'V', '2', '0') /* 2x1 subsampled Cr:Cb plane */
#endif

#ifndef DRM_FORMAT_NV30
#define DRM_FORMAT_NV30		fourcc_code('N', 'V', '3', '0') /* non-subsampled Cr:Cb plane */
#endif

#endif

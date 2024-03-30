/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef ANDROID_DRMHWCGRALLOC_H_
#define ANDROID_DRMHWCGRALLOC_H_

#include <cstdint>

constexpr int kHwcDrmBoMaxPlanes = 4;

struct HwcDrmBo {
  uint32_t width;
  uint32_t height;
  uint32_t format;     /* DRM_FORMAT_* from drm_fourcc.h */
  uint32_t hal_format; /* HAL_PIXEL_FORMAT_* */
  uint32_t usage;
  uint32_t pitches[kHwcDrmBoMaxPlanes];
  uint32_t offsets[kHwcDrmBoMaxPlanes];
  /* sizes[] is used only by mapper@4 metadata getter for internal purposes */
  uint32_t sizes[kHwcDrmBoMaxPlanes];
  int prime_fds[kHwcDrmBoMaxPlanes];
  uint64_t modifiers[kHwcDrmBoMaxPlanes];
  int acquire_fence_fd;
};

// NOLINTNEXTLINE(readability-identifier-naming)
using hwc_drm_bo_t = HwcDrmBo;

#endif  // ANDROID_DRMHWCGRALLOC_H_

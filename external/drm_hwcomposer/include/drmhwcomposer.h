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

#ifndef ANDROID_DRM_HWCOMPOSER_H_
#define ANDROID_DRM_HWCOMPOSER_H_

#include <hardware/hardware.h>
#include <hardware/hwcomposer.h>

#include <cstdbool>
#include <cstdint>
#include <vector>

#include "drm/DrmFbImporter.h"
#include "drmhwcgralloc.h"
#include "utils/UniqueFd.h"

namespace android {

class DrmFbIdHandle;

enum class DrmHwcColorSpace : int32_t {
  kUndefined,
  kItuRec601,
  kItuRec709,
  kItuRec2020,
};

enum class DrmHwcSampleRange : int32_t {
  kUndefined,
  kFullRange,
  kLimitedRange,
};

enum DrmHwcTransform : uint32_t {
  kIdentity = 0,
  kFlipH = 1 << 0,
  kFlipV = 1 << 1,
  kRotate90 = 1 << 2,
  kRotate180 = 1 << 3,
  kRotate270 = 1 << 4,
};

enum class DrmHwcBlending : int32_t {
  kNone,
  kPreMult,
  kCoverage,
};

struct DrmHwcLayer {
  buffer_handle_t sf_handle = nullptr;
  hwc_drm_bo_t buffer_info{};
  std::shared_ptr<DrmFbIdHandle> fb_id_handle;

  int gralloc_buffer_usage = 0;
  DrmHwcTransform transform{};
  DrmHwcBlending blending = DrmHwcBlending::kNone;
  uint16_t alpha = UINT16_MAX;
  hwc_frect_t source_crop;
  hwc_rect_t display_frame;
  DrmHwcColorSpace color_space;
  DrmHwcSampleRange sample_range;

  UniqueFd acquire_fence;

  int ImportBuffer(DrmDevice *drm_device);

  bool IsProtected() const {
    return (gralloc_buffer_usage & GRALLOC_USAGE_PROTECTED) ==
           GRALLOC_USAGE_PROTECTED;
  }
};

}  // namespace android

#endif

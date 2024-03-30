/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "hwc-layer"

#include "HwcLayer.h"

#include <fcntl.h>

#include "utils/log.h"

namespace android {

// NOLINTNEXTLINE(readability-convert-member-functions-to-static)
HWC2::Error HwcLayer::SetCursorPosition(int32_t /*x*/, int32_t /*y*/) {
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerBlendMode(int32_t mode) {
  switch (static_cast<HWC2::BlendMode>(mode)) {
    case HWC2::BlendMode::None:
      blending_ = DrmHwcBlending::kNone;
      break;
    case HWC2::BlendMode::Premultiplied:
      blending_ = DrmHwcBlending::kPreMult;
      break;
    case HWC2::BlendMode::Coverage:
      blending_ = DrmHwcBlending::kCoverage;
      break;
    default:
      ALOGE("Unknown blending mode b=%d", blending_);
      blending_ = DrmHwcBlending::kNone;
      break;
  }
  return HWC2::Error::None;
}

/* Find API details at:
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:hardware/libhardware/include/hardware/hwcomposer2.h;l=2314
 */
HWC2::Error HwcLayer::SetLayerBuffer(buffer_handle_t buffer,
                                     int32_t acquire_fence) {
  buffer_ = buffer;
  acquire_fence_ = UniqueFd(acquire_fence);
  return HWC2::Error::None;
}

// NOLINTNEXTLINE(readability-convert-member-functions-to-static)
HWC2::Error HwcLayer::SetLayerColor(hwc_color_t /*color*/) {
  // TODO(nobody): Put to client composition here?
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerCompositionType(int32_t type) {
  sf_type_ = static_cast<HWC2::Composition>(type);
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerDataspace(int32_t dataspace) {
  switch (dataspace & HAL_DATASPACE_STANDARD_MASK) {
    case HAL_DATASPACE_STANDARD_BT709:
      color_space_ = DrmHwcColorSpace::kItuRec709;
      break;
    case HAL_DATASPACE_STANDARD_BT601_625:
    case HAL_DATASPACE_STANDARD_BT601_625_UNADJUSTED:
    case HAL_DATASPACE_STANDARD_BT601_525:
    case HAL_DATASPACE_STANDARD_BT601_525_UNADJUSTED:
      color_space_ = DrmHwcColorSpace::kItuRec601;
      break;
    case HAL_DATASPACE_STANDARD_BT2020:
    case HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE:
      color_space_ = DrmHwcColorSpace::kItuRec2020;
      break;
    default:
      color_space_ = DrmHwcColorSpace::kUndefined;
  }

  switch (dataspace & HAL_DATASPACE_RANGE_MASK) {
    case HAL_DATASPACE_RANGE_FULL:
      sample_range_ = DrmHwcSampleRange::kFullRange;
      break;
    case HAL_DATASPACE_RANGE_LIMITED:
      sample_range_ = DrmHwcSampleRange::kLimitedRange;
      break;
    default:
      sample_range_ = DrmHwcSampleRange::kUndefined;
  }
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerDisplayFrame(hwc_rect_t frame) {
  display_frame_ = frame;
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerPlaneAlpha(float alpha) {
  alpha_ = alpha;
  return HWC2::Error::None;
}

// NOLINTNEXTLINE(readability-convert-member-functions-to-static)
HWC2::Error HwcLayer::SetLayerSidebandStream(
    const native_handle_t * /*stream*/) {
  // TODO(nobody): We don't support sideband
  return HWC2::Error::Unsupported;
}

HWC2::Error HwcLayer::SetLayerSourceCrop(hwc_frect_t crop) {
  source_crop_ = crop;
  return HWC2::Error::None;
}

// NOLINTNEXTLINE(readability-convert-member-functions-to-static)
HWC2::Error HwcLayer::SetLayerSurfaceDamage(hwc_region_t /*damage*/) {
  // TODO(nobody): We don't use surface damage, marking as unsupported
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerTransform(int32_t transform) {
  uint32_t l_transform = 0;

  // 270* and 180* cannot be combined with flips. More specifically, they
  // already contain both horizontal and vertical flips, so those fields are
  // redundant in this case. 90* rotation can be combined with either horizontal
  // flip or vertical flip, so treat it differently
  if (transform == HWC_TRANSFORM_ROT_270) {
    l_transform = DrmHwcTransform::kRotate270;
  } else if (transform == HWC_TRANSFORM_ROT_180) {
    l_transform = DrmHwcTransform::kRotate180;
  } else {
    if ((transform & HWC_TRANSFORM_FLIP_H) != 0)
      l_transform |= DrmHwcTransform::kFlipH;
    if ((transform & HWC_TRANSFORM_FLIP_V) != 0)
      l_transform |= DrmHwcTransform::kFlipV;
    if ((transform & HWC_TRANSFORM_ROT_90) != 0)
      l_transform |= DrmHwcTransform::kRotate90;
  }

  transform_ = static_cast<DrmHwcTransform>(l_transform);
  return HWC2::Error::None;
}

// NOLINTNEXTLINE(readability-convert-member-functions-to-static)
HWC2::Error HwcLayer::SetLayerVisibleRegion(hwc_region_t /*visible*/) {
  // TODO(nobody): We don't use this information, marking as unsupported
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerZOrder(uint32_t order) {
  z_order_ = order;
  return HWC2::Error::None;
}

void HwcLayer::PopulateDrmLayer(DrmHwcLayer *layer) {
  layer->sf_handle = buffer_;
  // TODO(rsglobal): Avoid extra fd duplication
  layer->acquire_fence = UniqueFd(fcntl(acquire_fence_.Get(), F_DUPFD_CLOEXEC));
  layer->display_frame = display_frame_;
  layer->alpha = std::lround(alpha_ * UINT16_MAX);
  layer->blending = blending_;
  layer->source_crop = source_crop_;
  layer->transform = transform_;
  layer->color_space = color_space_;
  layer->sample_range = sample_range_;
}

}  // namespace android
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

#ifndef ANDROID_HWC2_DEVICE_HWC_LAYER_H
#define ANDROID_HWC2_DEVICE_HWC_LAYER_H

#include <hardware/hwcomposer2.h>

#include <cmath>

#include "drmhwcomposer.h"

namespace android {

class HwcLayer {
 public:
  HWC2::Composition GetSfType() const {
    return sf_type_;
  }
  HWC2::Composition GetValidatedType() const {
    return validated_type_;
  }
  void AcceptTypeChange() {
    sf_type_ = validated_type_;
  }
  void SetValidatedType(HWC2::Composition type) {
    validated_type_ = type;
  }
  bool IsTypeChanged() const {
    return sf_type_ != validated_type_;
  }

  uint32_t GetZOrder() const {
    return z_order_;
  }

  buffer_handle_t GetBuffer() {
    return buffer_;
  }

  hwc_rect_t GetDisplayFrame() {
    return display_frame_;
  }

  UniqueFd GetReleaseFence() {
    return std::move(release_fence_);
  }

  void PopulateDrmLayer(DrmHwcLayer *layer);

  bool RequireScalingOrPhasing() const {
    float src_width = source_crop_.right - source_crop_.left;
    float src_height = source_crop_.bottom - source_crop_.top;

    auto dest_width = float(display_frame_.right - display_frame_.left);
    auto dest_height = float(display_frame_.bottom - display_frame_.top);

    bool scaling = src_width != dest_width || src_height != dest_height;
    bool phasing = (source_crop_.left - std::floor(source_crop_.left) != 0) ||
                   (source_crop_.top - std::floor(source_crop_.top) != 0);
    return scaling || phasing;
  }

  // Layer hooks
  HWC2::Error SetCursorPosition(int32_t /*x*/, int32_t /*y*/);
  HWC2::Error SetLayerBlendMode(int32_t mode);
  HWC2::Error SetLayerBuffer(buffer_handle_t buffer, int32_t acquire_fence);
  HWC2::Error SetLayerColor(hwc_color_t /*color*/);
  HWC2::Error SetLayerCompositionType(int32_t type);
  HWC2::Error SetLayerDataspace(int32_t dataspace);
  HWC2::Error SetLayerDisplayFrame(hwc_rect_t frame);
  HWC2::Error SetLayerPlaneAlpha(float alpha);
  HWC2::Error SetLayerSidebandStream(const native_handle_t *stream);
  HWC2::Error SetLayerSourceCrop(hwc_frect_t crop);
  HWC2::Error SetLayerSurfaceDamage(hwc_region_t damage);
  HWC2::Error SetLayerTransform(int32_t transform);
  HWC2::Error SetLayerVisibleRegion(hwc_region_t visible);
  HWC2::Error SetLayerZOrder(uint32_t order);

 private:
  // sf_type_ stores the initial type given to us by surfaceflinger,
  // validated_type_ stores the type after running ValidateDisplay
  HWC2::Composition sf_type_ = HWC2::Composition::Invalid;
  HWC2::Composition validated_type_ = HWC2::Composition::Invalid;

  buffer_handle_t buffer_ = nullptr;
  hwc_rect_t display_frame_;
  static constexpr float kOpaqueFloat = 1.0F;
  float alpha_ = kOpaqueFloat;
  hwc_frect_t source_crop_;
  DrmHwcTransform transform_ = DrmHwcTransform::kIdentity;
  uint32_t z_order_ = 0;
  DrmHwcBlending blending_ = DrmHwcBlending::kNone;
  DrmHwcColorSpace color_space_ = DrmHwcColorSpace::kUndefined;
  DrmHwcSampleRange sample_range_ = DrmHwcSampleRange::kUndefined;

  UniqueFd acquire_fence_;

  /*
   * Release fence is not used.
   * There is no release fence support available in the DRM/KMS. In case no
   * release fence provided application will use this buffer for writing when
   * the next frame present fence is signaled.
   */
  UniqueFd release_fence_;
};

}  // namespace android

#endif

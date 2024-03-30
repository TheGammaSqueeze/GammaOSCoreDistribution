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

#include "DrmMode.h"

#include <cstring>

#include "DrmDevice.h"

namespace android {

DrmMode::DrmMode(drmModeModeInfoPtr m)
    : clock_(m->clock),
      h_display_(m->hdisplay),
      h_sync_start_(m->hsync_start),
      h_sync_end_(m->hsync_end),
      h_total_(m->htotal),
      h_skew_(m->hskew),
      v_display_(m->vdisplay),
      v_sync_start_(m->vsync_start),
      v_sync_end_(m->vsync_end),
      v_total_(m->vtotal),
      v_scan_(m->vscan),
      v_refresh_(m->vrefresh),
      flags_(m->flags),
      type_(m->type),
      name_(m->name) {
}

bool DrmMode::operator==(const drmModeModeInfo &m) const {
  return clock_ == m.clock && h_display_ == m.hdisplay &&
         h_sync_start_ == m.hsync_start && h_sync_end_ == m.hsync_end &&
         h_total_ == m.htotal && h_skew_ == m.hskew &&
         v_display_ == m.vdisplay && v_sync_start_ == m.vsync_start &&
         v_sync_end_ == m.vsync_end && v_total_ == m.vtotal &&
         v_scan_ == m.vscan && flags_ == m.flags && type_ == m.type;
}

uint32_t DrmMode::clock() const {
  return clock_;
}

uint16_t DrmMode::h_display() const {
  return h_display_;
}

uint16_t DrmMode::h_sync_start() const {
  return h_sync_start_;
}

uint16_t DrmMode::h_sync_end() const {
  return h_sync_end_;
}

uint16_t DrmMode::h_total() const {
  return h_total_;
}

uint16_t DrmMode::h_skew() const {
  return h_skew_;
}

uint16_t DrmMode::v_display() const {
  return v_display_;
}

uint16_t DrmMode::v_sync_start() const {
  return v_sync_start_;
}

uint16_t DrmMode::v_sync_end() const {
  return v_sync_end_;
}

uint16_t DrmMode::v_total() const {
  return v_total_;
}

uint16_t DrmMode::v_scan() const {
  return v_scan_;
}

float DrmMode::v_refresh() const {
  if (clock_ == 0) {
    return v_refresh_;
  }
  // Always recalculate refresh to report correct float rate
  return static_cast<float>(clock_) / (float)(v_total_ * h_total_) * 1000.0F;
}

uint32_t DrmMode::flags() const {
  return flags_;
}

uint32_t DrmMode::type() const {
  return type_;
}

std::string DrmMode::name() const {
  return name_ + "@" + std::to_string(v_refresh());
}

auto DrmMode::CreateModeBlob(const DrmDevice &drm)
    -> DrmModeUserPropertyBlobUnique {
  struct drm_mode_modeinfo drm_mode = {
      .clock = clock_,
      .hdisplay = h_display_,
      .hsync_start = h_sync_start_,
      .hsync_end = h_sync_end_,
      .htotal = h_total_,
      .hskew = h_skew_,
      .vdisplay = v_display_,
      .vsync_start = v_sync_start_,
      .vsync_end = v_sync_end_,
      .vtotal = v_total_,
      .vscan = v_scan_,
      .vrefresh = v_refresh_,
      .flags = flags_,
      .type = type_,
  };
  strncpy(drm_mode.name, name_.c_str(), DRM_DISPLAY_MODE_LEN);

  return drm.RegisterUserPropertyBlob(&drm_mode,
                                       sizeof(struct drm_mode_modeinfo));
}

}  // namespace android

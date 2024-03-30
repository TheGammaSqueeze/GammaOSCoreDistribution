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

#define LOG_TAG "hwc-drm-encoder"

#include "DrmEncoder.h"

#include <xf86drmMode.h>

#include <cstdint>

#include "DrmDevice.h"
#include "utils/log.h"

namespace android {

auto DrmEncoder::CreateInstance(DrmDevice &dev, uint32_t encoder_id,
                                uint32_t index) -> std::unique_ptr<DrmEncoder> {
  auto e = MakeDrmModeEncoderUnique(dev.GetFd(), encoder_id);
  if (!e) {
    ALOGE("Failed to get encoder %d", encoder_id);
    return {};
  }

  return std::unique_ptr<DrmEncoder>(new DrmEncoder(std::move(e), index));
}

}  // namespace android

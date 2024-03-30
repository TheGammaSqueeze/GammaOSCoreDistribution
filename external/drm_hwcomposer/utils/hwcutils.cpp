/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define ATRACE_TAG ATRACE_TAG_GRAPHICS
#define LOG_TAG "hwc-drm-utils"

#include <utils/log.h>

#include <cerrno>

#include "bufferinfo/BufferInfoGetter.h"
#include "drm/DrmFbImporter.h"
#include "drmhwcomposer.h"

namespace android {

int DrmHwcLayer::ImportBuffer(DrmDevice *drm_device) {
  buffer_info = hwc_drm_bo_t{};

  int ret = BufferInfoGetter::GetInstance()->ConvertBoInfo(sf_handle,
                                                           &buffer_info);
  if (ret != 0) {
    ALOGE("Failed to convert buffer info %d", ret);
    return ret;
  }

  fb_id_handle = drm_device->GetDrmFbImporter().GetOrCreateFbId(&buffer_info);
  if (!fb_id_handle) {
    ALOGE("Failed to import buffer");
    return -EINVAL;
  }

  return 0;
}

}  // namespace android

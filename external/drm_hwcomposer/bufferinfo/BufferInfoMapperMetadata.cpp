/*
 * Copyright (C) 2020 The Android Open Source Project
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

#if PLATFORM_SDK_VERSION >= 30

#define LOG_TAG "hwc-bufferinfo-mappermetadata"

#include "BufferInfoMapperMetadata.h"

#include <drm/drm_fourcc.h>
#include <ui/GraphicBufferMapper.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <cinttypes>

#include "utils/log.h"

namespace android {

BufferInfoGetter *BufferInfoMapperMetadata::CreateInstance() {
  if (GraphicBufferMapper::getInstance().getMapperVersion() <
      GraphicBufferMapper::GRALLOC_4)
    return nullptr;

  return new BufferInfoMapperMetadata();
}

/* The implementation below makes assumptions on the order and number of file
 * descriptors that Gralloc places in the native_handle_t and as such it very
 * likely needs to be adapted to match the particular Gralloc implementation
 * used in the system. For this reason it is been declared as a weak symbol,
 * so that it can be overridden.
 */
int __attribute__((weak))
BufferInfoMapperMetadata::GetFds(buffer_handle_t handle, hwc_drm_bo_t *bo) {
  int fd_index = 0;

  if (handle->numFds <= 0) {
    ALOGE("Handle has no fds");
    return android::BAD_VALUE;
  }

  for (int i = 0; i < kHwcDrmBoMaxPlanes; i++) {
    /* If no size, we're out of usable planes */
    if (bo->sizes[i] <= 0) {
      if (i == 0) {
        ALOGE("Bad handle metadata");
        return android::BAD_VALUE;
      }
      break;
    }

    /*
     * If the offset is zero, its multi-buffer
     * so move to the next fd
     */
    if (i != 0 && bo->offsets[i] == 0) {
      fd_index++;
      if (fd_index >= handle->numFds) {
        ALOGE("Handle has no more fds");
        return android::BAD_VALUE;
      }
    }

    bo->prime_fds[i] = handle->data[fd_index];
    if (bo->prime_fds[i] <= 0) {
      ALOGE("Invalid prime fd");
      return android::BAD_VALUE;
    }
  }

  return 0;
}

int BufferInfoMapperMetadata::ConvertBoInfo(buffer_handle_t handle,
                                            hwc_drm_bo_t *bo) {
  GraphicBufferMapper &mapper = GraphicBufferMapper::getInstance();
  if (handle == nullptr)
    return -EINVAL;

  uint64_t usage = 0;
  int err = mapper.getUsage(handle, &usage);
  if (err != 0) {
    ALOGE("Failed to get usage err=%d", err);
    return err;
  }
  bo->usage = static_cast<uint32_t>(usage);

  ui::PixelFormat hal_format;
  err = mapper.getPixelFormatRequested(handle, &hal_format);
  if (err != 0) {
    ALOGE("Failed to get HAL Pixel Format err=%d", err);
    return err;
  }
  bo->hal_format = static_cast<uint32_t>(hal_format);

  err = mapper.getPixelFormatFourCC(handle, &bo->format);
  if (err != 0) {
    ALOGE("Failed to get FourCC format err=%d", err);
    return err;
  }

  err = mapper.getPixelFormatModifier(handle, &bo->modifiers[0]);
  if (err != 0) {
    ALOGE("Failed to get DRM Modifier err=%d", err);
    return err;
  }

  uint64_t width = 0;
  err = mapper.getWidth(handle, &width);
  if (err != 0) {
    ALOGE("Failed to get Width err=%d", err);
    return err;
  }
  bo->width = static_cast<uint32_t>(width);

  uint64_t height = 0;
  err = mapper.getHeight(handle, &height);
  if (err != 0) {
    ALOGE("Failed to get Height err=%d", err);
    return err;
  }
  bo->height = static_cast<uint32_t>(height);

  std::vector<ui::PlaneLayout> layouts;
  err = mapper.getPlaneLayouts(handle, &layouts);
  if (err != 0) {
    ALOGE("Failed to get Plane Layouts err=%d", err);
    return err;
  }

  for (uint32_t i = 0; i < layouts.size(); i++) {
    bo->modifiers[i] = bo->modifiers[0];
    bo->pitches[i] = layouts[i].strideInBytes;
    bo->offsets[i] = layouts[i].offsetInBytes;
    bo->sizes[i] = layouts[i].totalSizeInBytes;
  }

  return GetFds(handle, bo);
}

}  // namespace android

#endif

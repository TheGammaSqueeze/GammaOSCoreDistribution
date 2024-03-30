/*
 * Copyright 2022 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef MINIGBM_CROSGRALLOC_AIDL_ALLOCATOR_H_
#define MINIGBM_CROSGRALLOC_AIDL_ALLOCATOR_H_

#include <aidl/android/hardware/graphics/allocator/AllocationResult.h>
#include <aidl/android/hardware/graphics/allocator/BnAllocator.h>
#include <android/hardware/graphics/mapper/4.0/IMapper.h>

#include "cros_gralloc/cros_gralloc_driver.h"
#include "cros_gralloc/cros_gralloc_helpers.h"
#include "cros_gralloc/gralloc4/CrosGralloc4Metadata.h"

namespace aidl::android::hardware::graphics::allocator::impl {

class Allocator : public BnAllocator {
  public:
    Allocator() = default;

    bool init();

    ndk::ScopedAStatus allocate(const std::vector<uint8_t>& descriptor, int32_t count,
                                allocator::AllocationResult* outResult) override;

  protected:
    ndk::SpAIBinder createBinder() override;

  private:
    ndk::ScopedAStatus allocate(
            const ::android::hardware::graphics::mapper::V4_0::IMapper::BufferDescriptorInfo&
                    descriptor,
            int32_t* outStride, native_handle_t** outHandle);

    ndk::ScopedAStatus initializeMetadata(
            cros_gralloc_handle_t crosHandle,
            const struct cros_gralloc_buffer_descriptor& crosDescriptor);

    void releaseBufferAndHandle(native_handle_t* handle);

    cros_gralloc_driver* mDriver = nullptr;
};

}  // namespace aidl::android::hardware::graphics::allocator::impl

#endif
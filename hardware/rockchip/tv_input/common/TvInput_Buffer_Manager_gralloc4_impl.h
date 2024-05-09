/*
 * Copyright 2017 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef COMMON_TVINPUT_BUFFER_MANAGER_IMPL_H_
#define COMMON_TVINPUT_BUFFER_MANAGER_IMPL_H_

#include "TvInput_Buffer_Manager.h"

#include <memory>
#include <unordered_map>
#include <utility>


//#include <base/synchronization/lock.h>


#include <android/hardware/graphics/mapper/4.0/IMapper.h>
#include <android/hardware/graphics/allocator/4.0/IAllocator.h>
#include <android/hardware/graphics/common/1.1/types.h>
#include <gralloctypes/Gralloc4.h>

#include <stdint.h>

#include <cutils/native_handle.h>
#include <utils/Errors.h>

#include <ui/PixelFormat.h>

// A V4L2 extension format which represents 32bit RGBX-8-8-8-8 format. This
// corresponds to DRM_FORMAT_XBGR8888 which is used as the underlying format for
// the HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINEND format on all CrOS boards.
#define V4L2_PIX_FMT_RGBX32 v4l2_fourcc('X', 'B', '2', '4')

struct android_ycbcr;

namespace common {

using android::status_t;
using android::hardware::graphics::mapper::V4_0::IMapper;

struct BufferContext {
    uint64_t buffer_id;
    BufferType type;
    uint64_t usage;
};

typedef std::unordered_map<buffer_handle_t,
        std::unique_ptr<struct BufferContext>>
        BufferContextCache;

class TvInputBufferManagerImpl final : public TvInputBufferManager {
public:
    TvInputBufferManagerImpl();

    // TvInputBufferManager implementation.
    ~TvInputBufferManagerImpl() final;
    int Allocate(size_t width,
                 size_t height,
                 uint32_t format,
                 uint64_t usage,
                 BufferType type,
                 buffer_handle_t* out_buffer,
                 uint32_t* out_stride) final;
    int Free(buffer_handle_t buffer) final;
    int FreeLocked(buffer_handle_t buffer) final;
    int Register(buffer_handle_t buffer, buffer_handle_t* outbuffer) final;
    int Deregister(buffer_handle_t buffer) final;
    int ImportBufferLocked(buffer_handle_t& rawHandle) final;
    int Lock(buffer_handle_t buffer,
             uint32_t flags,
             uint32_t x,
             uint32_t y,
             uint32_t width,
             uint32_t height,
             void** out_addr) final;
    int LockLocked(buffer_handle_t buffer,
             uint32_t flags,
             uint32_t x,
             uint32_t y,
             uint32_t width,
             uint32_t height,
             void** out_addr) final;
    int LockYCbCr(buffer_handle_t buffer,
                  uint32_t flags,
                  uint32_t x,
                  uint32_t y,
                  uint32_t width,
                  uint32_t height,
                  struct android_ycbcr* out_ycbcr) final;
    int Unlock(buffer_handle_t buffer) final;
    int UnlockLocked(buffer_handle_t buffer) final;
    int FlushCache(buffer_handle_t buffer) final;
    int GetHandleFd(buffer_handle_t buffer) final;
    int GetHandleBufferSize(buffer_handle_t handle) final;  
    int GetBufferId(buffer_handle_t buffer) final;
    int GetWidth(buffer_handle_t handle) final;
    int GetHeight(buffer_handle_t handle) final;
    int GetHalPixelFormat(buffer_handle_t buffer) final;

private:
    friend class TvInputBufferManager;

    int AllocateGrallocBuffer(size_t width,
                              size_t height,
                              uint32_t format,
                              uint64_t usage,
                              buffer_handle_t* out_buffer,
                              uint32_t* out_stride);

	//uint64_t get_internal_format_from_fourcc(uint32_t fourcc, uint64_t modifier);
	status_t validateBufferDescriptorInfo(
        IMapper::BufferDescriptorInfo* descriptorInfo) const;
	status_t createDescriptor(void* bufferDescriptorInfo,
                                          void* outBufferDescriptor) const;
	int Lockinternal(buffer_handle_t bufferHandle,
                                  uint32_t flags,
                                  uint32_t x,
                                  uint32_t y,
                                  uint32_t width,
                                  uint32_t height,
                                  void** out_addr);
	int Unlockinternal(buffer_handle_t bufferHandle);
	status_t importBuffer(buffer_handle_t rawHandle,
                                      buffer_handle_t* outBufferHandle) const;
	status_t freeBuffer(buffer_handle_t bufferHandle) const;

    // Lock to guard access member variables.
    //base::Lock lock_;

    // ** Start of lock_ scope **

    // A cache which stores all the context of the registered buffers.
    BufferContextCache buffer_context_;

    // ** End of lock_ scope **

    //DISALLOW_COPY_AND_ASSIGN(TvInputBufferManagerImpl);
};

}  // namespace common

#endif  // COMMON_TVINPUT_BUFFER_MANAGER_IMPL_H_

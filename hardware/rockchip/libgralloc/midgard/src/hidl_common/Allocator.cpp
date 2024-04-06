/*
 * Copyright (C) 2020 ARM Limited. All rights reserved.
 *
 * Copyright 2016 The Android Open Source Project
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

#define ENABLE_DEBUG_LOG
#include "../custom_log.h"

/* Legacy shared attribute region is deprecated from Android 11.
 * Use the new shared metadata region defined for Gralloc 4.
 */
#define GRALLOC_USE_SHARED_METADATA (GRALLOC_VERSION_MAJOR > 3)

#include "Allocator.h"

#if GRALLOC_USE_SHARED_METADATA
#include "SharedMetadata.h"
#else
#include "gralloc_buffer_priv.h"
#endif

#include "core/mali_gralloc_bufferallocation.h"
#include "core/mali_gralloc_bufferdescriptor.h"
#include "core/format_info.h"
#include "allocator/allocator.h"
#include "allocator/mali_gralloc_shared_memory.h"
#include "gralloc_priv.h"

namespace arm
{
namespace allocator
{
namespace common
{
android::base::expected<std::vector<unique_private_handle>, android::status_t> allocate(
    buffer_descriptor_t *buffer_descriptor, uint32_t count)
{
	int stride = 0;
        std::vector<unique_private_handle> gralloc_buffers;

        gralloc_buffers.reserve(count);

	for (uint32_t i = 0; i < count; i++)
	{
		auto hnd = mali_gralloc_buffer_allocate(buffer_descriptor);
		if (hnd == nullptr)
		{
			MALI_GRALLOC_LOGE("buffer allocation failed: %s", strerror(errno));
			return android::base::unexpected{ android::NO_MEMORY };
		}
		
		hnd->imapper_version = HIDL_MAPPER_VERSION_SCALED;

		hnd->reserved_region_size = buffer_descriptor->reserved_size;
		hnd->attr_size = mapper::common::shared_metadata_size() + hnd->reserved_region_size;
		std::tie(hnd->share_attr_fd, hnd->attr_base) =
		    gralloc_shared_memory_allocate("gralloc_shared_memory", hnd->attr_size);
		if (hnd->share_attr_fd < 0 || hnd->attr_base == MAP_FAILED)
		{
			MALI_GRALLOC_LOGE("%s, shared memory allocation failed with errno %d", __func__, errno);
			return android::base::unexpected{ android::BAD_VALUE };
		}

		const uint32_t base_format = buffer_descriptor->alloc_format & MALI_GRALLOC_INTFMT_FMT_MASK;
		const uint64_t usage = buffer_descriptor->consumer_usage | buffer_descriptor->producer_usage;
		android_dataspace_t dataspace;
		get_format_dataspace(base_format, usage, hnd->width, hnd->height, &dataspace,
				     &hnd->yuv_info);
		mapper::common::shared_metadata_init(hnd->attr_base,
						     buffer_descriptor->name,
						     static_cast<mapper::common::Dataspace>(dataspace) );

		/*
		 * We need to set attr_base to MAP_FAILED before the HIDL callback
		 * to avoid sending an invalid pointer to the client process.
		 *
		 * hnd->attr_base = mmap(...);
		 * hidl_callback(hnd); // client receives hnd->attr_base = <dangling pointer>
		 */
		munmap(hnd->attr_base, hnd->attr_size);
		hnd->attr_base = MAP_FAILED;


		{
			buffer_descriptor_t* bufDescriptor = buffer_descriptor;
			D("got new private_handle_t instance for buffer '%s'. share_fd : %d, share_attr_fd : %d, "
				"flags : 0x%x, width : %d, height : %d, "
				"req_format : 0x%x, producer_usage : 0x%" PRIx64 ", consumer_usage : 0x%" PRIx64 ", "
				"internal_format : 0x%" PRIx64 ", stride : %d, byte_stride : %d, "
				"internalWidth : %d, internalHeight : %d, "
				"alloc_format : 0x%" PRIx64 ", size : %d, layer_count : %u, backing_store_size : %d, "
				"allocating_pid : %d, ref_count : %d, yuv_info : %d",
				(bufDescriptor->name).c_str() == nullptr ? "unset" : (bufDescriptor->name).c_str(),
			  hnd->share_fd, hnd->share_attr_fd,
			  hnd->flags, hnd->width, hnd->height,
			  hnd->req_format, hnd->producer_usage, hnd->consumer_usage,
			  hnd->internal_format, hnd->stride, hnd->byte_stride,
			  hnd->internalWidth, hnd->internalHeight,
			  hnd->alloc_format, hnd->size, hnd->layer_count, hnd->backing_store_size,
			  hnd->allocating_pid, hnd->ref_count, hnd->yuv_info);
			ALOGD("plane_info[0]: offset : %u, byte_stride : %u, alloc_width : %u, alloc_height : %u",
					(hnd->plane_info)[0].offset,
					(hnd->plane_info)[0].byte_stride,
					(hnd->plane_info)[0].alloc_width,
					(hnd->plane_info)[0].alloc_height);
			ALOGD("plane_info[1]: offset : %u, byte_stride : %u, alloc_width : %u, alloc_height : %u",
					(hnd->plane_info)[1].offset,
					(hnd->plane_info)[1].byte_stride,
					(hnd->plane_info)[1].alloc_width,
					(hnd->plane_info)[1].alloc_height);
		}

		int tmpStride = buffer_descriptor->pixel_stride;
		if (stride == 0)
		{
			stride = tmpStride;
		}
		else if (stride != tmpStride)
		{
			/* Stride must be the same for all allocations */
			return android::base::unexpected{ android::BAD_VALUE };
		}

                gralloc_buffers.push_back(std::move(hnd));
	}

        return gralloc_buffers;
}

} // namespace common
} // namespace allocator
} // namespace arm

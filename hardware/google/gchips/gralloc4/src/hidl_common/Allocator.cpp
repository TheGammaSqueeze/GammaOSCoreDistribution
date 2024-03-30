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

#define ATRACE_TAG ATRACE_TAG_GRAPHICS

#include <utils/Trace.h>

#include "SharedMetadata.h"
#include "Allocator.h"
#include "core/mali_gralloc_bufferallocation.h"
#include "core/mali_gralloc_bufferdescriptor.h"
#include "core/format_info.h"
#include "allocator/mali_gralloc_ion.h"
#include "allocator/mali_gralloc_shared_memory.h"
#include "gralloc_priv.h"

namespace arm
{
namespace allocator
{
namespace common
{

void allocate(const buffer_descriptor_t &bufferDescriptor, uint32_t count, IAllocator::allocate_cb hidl_cb,
              std::function<int(const buffer_descriptor_t *, buffer_handle_t *)> fb_allocator)
{
#if DISABLE_FRAMEBUFFER_HAL
	GRALLOC_UNUSED(fb_allocator);
#endif
	ATRACE_CALL();

	Error error = Error::NONE;
	int stride = 0;
	std::vector<hidl_handle> grallocBuffers;
	gralloc_buffer_descriptor_t grallocBufferDescriptor[1];

	grallocBufferDescriptor[0] = (gralloc_buffer_descriptor_t)(&bufferDescriptor);
	grallocBuffers.reserve(count);

	for (uint32_t i = 0; i < count; i++)
	{
		buffer_handle_t tmpBuffer = nullptr;

		int allocResult;
#if (DISABLE_FRAMEBUFFER_HAL != 1)
		if (((bufferDescriptor.producer_usage & GRALLOC_USAGE_HW_FB) ||
		     (bufferDescriptor.consumer_usage & GRALLOC_USAGE_HW_FB)) &&
		    fb_allocator)
		{
			allocResult = fb_allocator(&bufferDescriptor, &tmpBuffer);
		}
		else
#endif
		{
			allocResult = mali_gralloc_buffer_allocate(grallocBufferDescriptor, 1, &tmpBuffer, nullptr);
			if (allocResult != 0)
			{
				MALI_GRALLOC_LOGE("%s, buffer allocation failed with %d", __func__, allocResult);
				error = Error::NO_RESOURCES;
				break;
			}
			auto hnd = const_cast<private_handle_t *>(reinterpret_cast<const private_handle_t *>(tmpBuffer));
			hnd->imapper_version = HIDL_MAPPER_VERSION_SCALED;

			// 4k is rougly 7.9 MB with one byte per pixel. We are
			// assuming that the reserved region might be needed for
			// dynamic HDR and that represents the largest size.
			uint64_t max_reserved_region_size = 8ull * 1024 * 1024;
			hnd->reserved_region_size = bufferDescriptor.reserved_size;
			if (hnd->reserved_region_size > max_reserved_region_size) {
				MALI_GRALLOC_LOGE("%s, Requested reserved region size (%" PRIu64 ") is larger than allowed (%" PRIu64 ")",
						__func__, hnd->reserved_region_size, max_reserved_region_size);
				error = Error::BAD_VALUE;
				break;
			}
			hnd->attr_size = mapper::common::shared_metadata_size() + hnd->reserved_region_size;

			if (hnd->get_usage() & GRALLOC_USAGE_ROIINFO)
			{
				hnd->attr_size += 32768;
			}

			/* TODO: must do error checking */
			mali_gralloc_ion_allocate_attr(hnd);

			/* TODO: error check for failure */
			hnd->attr_base = mmap(nullptr, hnd->attr_size, PROT_READ | PROT_WRITE,
					MAP_SHARED, hnd->get_share_attr_fd(), 0);

			memset(hnd->attr_base, 0, hnd->attr_size);

			mapper::common::shared_metadata_init(hnd->attr_base, bufferDescriptor.name);

			const uint32_t base_format = bufferDescriptor.alloc_format & MALI_GRALLOC_INTFMT_FMT_MASK;
			const uint64_t usage = bufferDescriptor.consumer_usage | bufferDescriptor.producer_usage;
			android_dataspace_t dataspace;
			get_format_dataspace(base_format, usage, hnd->width, hnd->height, &dataspace);

			mapper::common::set_dataspace(hnd, static_cast<mapper::common::Dataspace>(dataspace));

			/*
			 * We need to set attr_base to MAP_FAILED before the HIDL callback
			 * to avoid sending an invalid pointer to the client process.
			 *
			 * hnd->attr_base = mmap(...);
			 * hidl_callback(hnd); // client receives hnd->attr_base = <dangling pointer>
			 */
			munmap(hnd->attr_base, hnd->attr_size);
			hnd->attr_base = 0;
		}

		int tmpStride = 0;
		tmpStride = bufferDescriptor.pixel_stride;

		if (stride == 0)
		{
			stride = tmpStride;
		}
		else if (stride != tmpStride)
		{
			/* Stride must be the same for all allocations */
			mali_gralloc_buffer_free(tmpBuffer);
			stride = 0;
			error = Error::UNSUPPORTED;
			break;
		}

		grallocBuffers.emplace_back(hidl_handle(tmpBuffer));
	}

	/* Populate the array of buffers for application consumption */
	hidl_vec<hidl_handle> hidlBuffers;
	if (error == Error::NONE)
	{
		hidlBuffers.setToExternal(grallocBuffers.data(), grallocBuffers.size());
	}
	hidl_cb(error, stride, hidlBuffers);

	/* The application should import the Gralloc buffers using IMapper for
	 * further usage. Free the allocated buffers in IAllocator context
	 */
	for (const auto &buffer : grallocBuffers)
	{
		mali_gralloc_buffer_free(buffer.getNativeHandle());
	}
}

} // namespace common
} // namespace allocator
} // namespace arm

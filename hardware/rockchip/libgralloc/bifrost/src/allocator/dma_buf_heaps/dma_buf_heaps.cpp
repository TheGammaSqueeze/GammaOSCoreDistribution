/*
 * Copyright (C) 2022 Arm Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
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

#include <inttypes.h>

#include <vector>
#include <BufferAllocator/BufferAllocator.h>
#include <android-base/unique_fd.h>

#include <log/log.h>
#include <cutils/properties.h>

#include "allocator/allocator.h"
#include "core/buffer_allocation.h"
#include "core/buffer_descriptor.h"
#include "core/format_info.h"
#include "usages.h"
#include "helper_functions.h"

/*---------------------------------------------------------------------------*/

/* 从该 dmabufheap 分配得到的 buffer 是 cached 的, 且其物理地址在 4G 以内 (for dma32). */
static const char kDmabufSystemDma32HeapName[] = "system-dma32";
/* 从该 dmabufheap 分配得到的 buffer 是 uncached 的, 且其物理地址在 4G 以内. */
static const char kDmabufSystemUncachedDma32HeapName[] = "system-uncached-dma32";

#define DMABUF_CMA		(char*)"cma"

/*---------------------------------------------------------------------------*/

enum class dma_buf_heap
{
	/* Upstream heaps */
	system,
	system_uncached,

	/* Custom heaps */
	physically_contiguous, // cma
	protected_memory,
	system_dma32,
	system_uncached_dma32,
};

struct custom_heap
{
	const char *name;
	struct
	{
		const char *name;
		int flags;
	}
	ion_fallback;
};

const custom_heap physically_contiguous_heap =
{
	DMABUF_CMA,
	{
		"linux,cma",
		0,
	},
};

const custom_heap protected_memory_heap =
{
	"protected",
	{
		"ion_protected_heap",
		0,
	},
};

const custom_heap custom_heaps[] =
{
#if 0
	physically_contiguous_heap,
	protected_memory_heap,
#endif
};

/*---------------------------------------------------------------------------*/

static bool is_platform_rk356x()
{
	return (RK356X == get_rk_board_platform() );
}

static bool is_platform_rk3588()
{
	return (RK3588 == get_rk_board_platform() );
}

static bool is_alloc_all_buffers_from_cma_heap_required_via_prop()
{
        char value[PROPERTY_VALUE_MAX];

        property_get("vendor.gralloc.alloc_all_buf_from_cma_heap", value, "0");

        return (0 == strcmp("1", value) );
}

static bool is_alloc_all_buffers_within_4g_required_via_prop()
{
        char value[PROPERTY_VALUE_MAX];

        property_get("vendor.gralloc.alloc_all_buf_within_4g", value, "0");

        return (0 == strcmp("1", value) );
}

static const char *get_dma_buf_heap_name(dma_buf_heap heap)
{
	switch (heap)
	{
	case dma_buf_heap::system:
		return kDmabufSystemHeapName;
	case dma_buf_heap::system_uncached:
		return kDmabufSystemUncachedHeapName;
	case dma_buf_heap::physically_contiguous:
		return physically_contiguous_heap.name;
	case dma_buf_heap::protected_memory:
		return protected_memory_heap.name;
	case dma_buf_heap::system_dma32:
		return kDmabufSystemDma32HeapName;
	case dma_buf_heap::system_uncached_dma32:
		return kDmabufSystemUncachedDma32HeapName;
	default:
		MALI_GRALLOC_LOGE("Invalid 'heap': %d", heap);
		return NULL;
	}
}

static BufferAllocator *get_global_buffer_allocator()
{
	static struct allocator_initialization
	{
		BufferAllocator allocator;
		allocator_initialization()
		{
			for (const auto &heap : custom_heaps)
			{
				allocator.MapNameToIonHeap(heap.name, heap.ion_fallback.name, heap.ion_fallback.flags);
			}
		}
	}
	instance;

	return &instance.allocator;
}

static bool does_hal_format_need_buffer_within_4G(uint64_t hal_format)
{
	if ( HAL_PIXEL_FORMAT_YV12 == hal_format )
	{
		return true;
	}

	return false;
}

static dma_buf_heap pick_dma_buf_heap(uint64_t usage, uint64_t hal_format)
{
	if ( is_alloc_all_buffers_from_cma_heap_required_via_prop() )
	{
		MALI_GRALLOC_LOGI("to allocate all buffer from cma_heap");
		return dma_buf_heap::physically_contiguous;
	}

	if (usage & GRALLOC_USAGE_PROTECTED)
	{
		MALI_GRALLOC_LOGE("Protected dmabuf_heap memory is not supported yet.");
		return dma_buf_heap::system_uncached;
	}

	if ( is_alloc_all_buffers_within_4g_required_via_prop() )
	{
		MALI_GRALLOC_LOGI("to allocate all buffers within 4G");
		usage |= RK_GRALLOC_USAGE_WITHIN_4G;
	}

	if ( (is_platform_rk356x() || is_platform_rk3588() ) )
	{
		if ( does_usage_have_flag(usage, GRALLOC_USAGE_HW_VIDEO_ENCODER) )
		{
			MALI_GRALLOC_LOGI("rk356x/rk3588: to allocate buffer within 4G for GRALLOC_USAGE_HW_VIDEO_ENCODER");
			usage |= RK_GRALLOC_USAGE_WITHIN_4G;
		}
		else if ( does_hal_format_need_buffer_within_4G(hal_format) )
		{
			MALI_GRALLOC_LOGI("to allocate buffer with 4G for hal_format: %" PRIu64, hal_format);
			usage |= RK_GRALLOC_USAGE_WITHIN_4G;
		}
	}

	if ( usage & RK_GRALLOC_USAGE_PHY_CONTIG_BUFFER )
	{
		return dma_buf_heap::physically_contiguous;
	}
	else if ( usage & RK_GRALLOC_USAGE_WITHIN_4G )
	{
		if ( (usage & GRALLOC_USAGE_SW_READ_MASK) == GRALLOC_USAGE_SW_READ_OFTEN )
		{
			return dma_buf_heap::system_dma32; // cacheable dma32
		}
		else
		{
			return dma_buf_heap::system_uncached_dma32; // uncacheable dma32
		}
	}
	else if ( (usage & GRALLOC_USAGE_SW_READ_MASK) == GRALLOC_USAGE_SW_READ_OFTEN )
	{
		return dma_buf_heap::system; // cacheable
	}
	else
	{
		return dma_buf_heap::system_uncached; // uncacheable
	}
}

unique_private_handle allocator_allocate(const buffer_descriptor_t *descriptor)
{
	auto allocator = get_global_buffer_allocator();

	uint64_t usage = descriptor->consumer_usage | descriptor->producer_usage;
	uint64_t hal_format = descriptor->hal_format;
	auto heap = pick_dma_buf_heap(usage, hal_format);
	auto heap_name = get_dma_buf_heap_name(heap);
	android::base::unique_fd fd{allocator->Alloc(heap_name, descriptor->size)};
	if (fd < 0)
	{
		MALI_GRALLOC_LOGE("libdmabufheap allocation failed for %s heap", heap_name);
		return nullptr;
	}

	return make_private_handle(
	    descriptor->size,
	    descriptor->consumer_usage, descriptor->producer_usage, std::move(fd), descriptor->hal_format,
	    descriptor->alloc_format, descriptor->width, descriptor->height, descriptor->layer_count,
	    descriptor->plane_info, descriptor->pixel_stride);
}

static SyncType make_sync_type(bool read, bool write)
{
	if (read && write)
	{
		return kSyncReadWrite;
	}
	else if (read)
	{
		return kSyncRead;
	}
	else if (write)
	{
		return kSyncWrite;
	}
	else
	{
		return static_cast<SyncType>(0);
	}
}

int allocator_sync_start(const imported_handle *handle, bool read, bool write)
{
	auto allocator = get_global_buffer_allocator();
	return allocator->CpuSyncStart(static_cast<unsigned>(handle->share_fd), make_sync_type(read, write));
}

int allocator_sync_end(const imported_handle *handle, bool read, bool write)
{
	auto allocator = get_global_buffer_allocator();
	return allocator->CpuSyncEnd(static_cast<unsigned>(handle->share_fd), make_sync_type(read, write));
}

int allocator_map(imported_handle *handle)
{
	void *hint = nullptr;
	int protection = PROT_READ | PROT_WRITE, flags = MAP_SHARED;
	off_t page_offset = 0;
	void *mapping = mmap(hint, handle->size, protection, flags, handle->share_fd, page_offset);
	if (MAP_FAILED  == mapping)
	{
		MALI_GRALLOC_LOGE("mmap(share_fd = %d) failed: %s", handle->share_fd, strerror(errno));
		return -errno;
	}

	handle->base = static_cast<std::byte *>(mapping);

	return 0;
}

void allocator_unmap(imported_handle *handle)
{
	void *base = static_cast<std::byte *>(handle->base);
	if (munmap(base, handle->size) < 0)
	{
		MALI_GRALLOC_LOGE("munmap(base = %p, size = %d) failed: %s", base, handle->size, strerror(errno));
	}

	handle->base = nullptr;
	handle->cpu_write = false;
	handle->lock_count = 0;
}

void allocator_close()
{
	/* nop */
}

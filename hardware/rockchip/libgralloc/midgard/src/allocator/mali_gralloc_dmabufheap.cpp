/*
 * Copyright (C) 2016-2020 ARM Limited. All rights reserved.
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

#define ENABLE_DEBUG_LOG
#include "../custom_log.h"

#include <string.h>
#include <errno.h>
#include <inttypes.h>
#include <pthread.h>
#include <stdlib.h>
#include <limits.h>

#include <log/log.h>
#include <cutils/atomic.h>
#include <cutils/properties.h>

#include <BufferAllocator/BufferAllocator.h>

#include <ion/ion.h>
#include <linux/ion_4.12.h>
#include <linux/dma-buf.h>
#include <vector>
#include <sys/ioctl.h>

#include <hardware/hardware.h>
#include <hardware/gralloc1.h>

#include "mali_gralloc_private_interface_types.h"
#include "mali_gralloc_buffer.h"
#include "gralloc_helper.h"
#include "mali_gralloc_formats.h"
#include "mali_gralloc_usages.h"
#include "core/mali_gralloc_bufferdescriptor.h"
#include "core/mali_gralloc_bufferallocation.h"

#include "allocator/allocator.h"

/*---------------------------------------------------------------------------*/

/* 从该 dmabufheap 分配得到的 buffer 是 cached 的, 且其物理地址在 4G 以内 (for dma32). */
static const char kDmabufSystemDma32HeapName[] = "system-dma32";
/* 从该 dmabufheap 分配得到的 buffer 是 uncached 的, 且其物理地址在 4G 以内. */
static const char kDmabufSystemUncachedDma32HeapName[] = "system-uncached-dma32";


#define ION_SYSTEM     (char*)"ion_system_heap"
#define ION_CMA        (char*)"linux,cma"

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

class BufferAllocator* s_buf_allocator;

/*---------------------------------------------------------------------------*/

static bool is_alloc_all_buffers_from_cma_heap_required_via_prop()
{
        char value[PROPERTY_VALUE_MAX];

        property_get("vendor.gralloc.alloc_all_buf_from_cma_heap", value, "0");

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
		MALI_GRALLOC_LOGE("invalid 'heap': %d, still return '%s'", heap, kDmabufSystemHeapName);
		return kDmabufSystemHeapName;
	}
}

/* 原始定义在 drivers/staging/android/uapi/ion.h 中, 这里的定义必须保持一致. */
#define ION_FLAG_DMA32 4

static int call_dma_buf_sync_ioctl(int fd, uint64_t operation, bool read, bool write)
{
	/* Either DMA_BUF_SYNC_START or DMA_BUF_SYNC_END. */
	dma_buf_sync sync_args = { operation };

	if (read)
	{
		sync_args.flags |= DMA_BUF_SYNC_READ;
	}

	if (write)
	{
		sync_args.flags |= DMA_BUF_SYNC_WRITE;
	}

	int ret, retry = 5;
	do
	{
		ret = ioctl(fd, DMA_BUF_IOCTL_SYNC, &sync_args);
		retry--;
	} while ((ret == -EAGAIN || ret == -EINTR) && retry);

	if (ret < 0)
	{
		MALI_GRALLOC_LOGE("ioctl: %#" PRIx64 ", flags: %#" PRIx64 "failed with code %d: %s",
		     (uint64_t)DMA_BUF_IOCTL_SYNC, (uint64_t)sync_args.flags, ret, strerror(errno));
		return -errno;
	}

	return 0;
}

/*---------------------------------------------------------------------------*/

/*
 * Signal start of CPU access to the DMABUF exported from ION.
 *
 * @param hnd   [in]    Buffer handle
 * @param read  [in]    Flag indicating CPU read access to memory
 * @param write [in]    Flag indicating CPU write access to memory
 *
 * @return              0 in case of success
 *                      errno for all error cases
 */
int allocator_sync_start(const private_handle_t * const hnd,
                                const bool read,
                                const bool write)
{
	if (hnd == NULL)
	{
		return -EINVAL;
	}

	return call_dma_buf_sync_ioctl(hnd->share_fd, DMA_BUF_SYNC_START, read, write);
}


/*
 * Signal end of CPU access to the DMABUF exported from ION.
 *
 * @param hnd   [in]    Buffer handle
 * @param read  [in]    Flag indicating CPU read access to memory
 * @param write [in]    Flag indicating CPU write access to memory
 *
 * @return              0 in case of success
 *                      errno for all error cases
 */
int allocator_sync_end(const private_handle_t * const hnd,
                              const bool read,
                              const bool write)
{
	if (hnd == NULL)
	{
		return -EINVAL;
	}

	return call_dma_buf_sync_ioctl(hnd->share_fd, DMA_BUF_SYNC_END, read, write);
}

void allocator_free(private_handle_t * const handle)
{
	if (handle == nullptr)
	{
		return;
	}

	/* Buffer might be unregistered already so we need to assure we have a valid handle */
	if (handle->base != 0)
	{
		if (munmap(handle->base, handle->size) != 0)
		{
			MALI_GRALLOC_LOGE("Failed to munmap handle %p", handle);
		}
	}

	close(handle->share_fd);
	handle->share_fd = -1;
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

static dma_buf_heap pick_dma_buf_heap(uint64_t usage)
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

#if 0
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
#endif

	if ( (usage & GRALLOC_USAGE_SW_READ_MASK) == GRALLOC_USAGE_SW_READ_OFTEN )
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
	int priv_heap_flag = private_handle_t::PRIV_FLAGS_USES_DBH;

	uint64_t usage = descriptor->consumer_usage | descriptor->producer_usage;
	auto heap = pick_dma_buf_heap(usage);
	auto heap_name = get_dma_buf_heap_name(heap);
	android::base::unique_fd fd{allocator->Alloc(heap_name, descriptor->size)};
	if (fd < 0)
	{
		MALI_GRALLOC_LOGE("libdmabufheap allocation failed for %s heap", heap_name);
		return nullptr;
	}

	return make_private_handle(
		priv_heap_flag,
		descriptor->size,
		descriptor->consumer_usage,
		descriptor->producer_usage,
		std::move(fd),
		descriptor->hal_format,
		descriptor->old_internal_format,
		descriptor->alloc_format,
		descriptor->width,
		descriptor->height,
		descriptor->layer_count,
		descriptor->plane_info,
		descriptor->pixel_stride,
		descriptor->old_alloc_width,
		descriptor->old_alloc_height,
		descriptor->old_byte_stride,
		descriptor->size);
}

int allocator_map(private_handle_t *handle)
{
	if (handle == nullptr)
	{
		return -EINVAL;
	}

	void *hint = nullptr;
	int protection = PROT_READ | PROT_WRITE;
	int flags = MAP_SHARED;
	off_t page_offset = 0;
	void *mapping = mmap(hint, handle->size, protection, flags, handle->share_fd, page_offset);
	if (MAP_FAILED == mapping)
	{
		MALI_GRALLOC_LOGE("mmap(share_fd = %d) failed: %s", handle->share_fd, strerror(errno));
		return -errno;
	}

	handle->base = static_cast<std::byte *>(mapping) + handle->offset;

	return 0;
}

void allocator_unmap(private_handle_t *handle)
{
	if (handle == nullptr)
	{
		return;
	}

	void *base = static_cast<std::byte *>(handle->base) - handle->offset;
	if (munmap(base, handle->size) < 0)
	{
		MALI_GRALLOC_LOGE("Could not munmap base:%p size:%d '%s'", base, handle->size, strerror(errno));
	}
	else
	{
		handle->base = 0;
		handle->cpu_read = 0;
		handle->cpu_write = 0;
	}
}

void allocator_close(void)
{
	return;
}


/*
 * Copyright (C) 2016-2021 ARM Limited. All rights reserved.
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
#include "custom_log.h"

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

#include <linux/dma-buf.h>
#include <vector>
#include <sys/ioctl.h>

#include <hardware/hardware.h>
#include <hardware/gralloc1.h>

#include "gralloc_priv.h"
#include "helper_functions.h"
#include "include/gralloc/formats.h"
#include "usages.h"
#include "core/buffer_descriptor.h"
#include "core/buffer_allocation.h"
#include "core/format_info.h"
#include "allocator/allocator.h"

#include <ion/ion.h>

/* ---------------------------------------------------------------------------------------------------------
 * Local Macros
 * ---------------------------------------------------------------------------------------------------------
 */

/* 从该 dmabufheap 分配得到的 buffer 是 cached 的, 且其物理地址在 4G 以内 (for dma32). */
static const char kDmabufSystemDma32HeapName[] = "system-dma32";
/* 从该 dmabufheap 分配得到的 buffer 是 uncached 的, 且其物理地址在 4G 以内. */
static const char kDmabufSystemUncachedDma32HeapName[] = "system-uncached-dma32";



#define ION_SYSTEM     "ion_system_heap"
#define ION_CMA        "linux,cma"

#define DMABUF_CMA	"cma"

/* ---------------------------------------------------------------------------------------------------------
 * Local Variables
 * ---------------------------------------------------------------------------------------------------------
 */

class BufferAllocator* s_buf_allocator;

/* ---------------------------------------------------------------------------------------------------------
 * Local Functions Implementation
 * ---------------------------------------------------------------------------------------------------------
 */

static bool is_alloc_all_buffers_from_cma_heap_required_via_prop()
{
        char value[PROPERTY_VALUE_MAX];

        property_get("vendor.gralloc.alloc_all_buf_from_cma_heap", value, "0");

        return (0 == strcmp("1", value) );
}

static const char* pick_dmabuf_heap(uint64_t usage)
{
	if ( is_alloc_all_buffers_from_cma_heap_required_via_prop() )
	{
		MALI_GRALLOC_LOGI("to allocate all buffer from cma_heap");
		return DMABUF_CMA;
	}

	if (usage & GRALLOC_USAGE_PROTECTED)
	{
		MALI_GRALLOC_LOGE("Protected dmabuf_heap memory is not supported yet.");
		return NULL;
	}
	else if ( usage & RK_GRALLOC_USAGE_PHY_CONTIG_BUFFER )
	{
		return DMABUF_CMA;
	}
	else if ( usage & RK_GRALLOC_USAGE_WITHIN_4G )
	{
		if ( (usage & GRALLOC_USAGE_SW_READ_MASK) == GRALLOC_USAGE_SW_READ_OFTEN )
		{
			return kDmabufSystemDma32HeapName; // cacheable dma32
		}
		else
		{
			return kDmabufSystemUncachedDma32HeapName; // uncacheable dma32
		}
	}
	else if ( (usage & GRALLOC_USAGE_SW_READ_MASK) == GRALLOC_USAGE_SW_READ_OFTEN )
	{
		return kDmabufSystemHeapName; // cacheable
	}
	else
	{
		return kDmabufSystemUncachedHeapName; // uncacheable
	}
}

/* 原始定义在 drivers/staging/android/uapi/ion.h 中, 这里的定义必须保持一致. */
#define ION_FLAG_DMA32 4

static int setup_mappings(BufferAllocator *ba)
{
	int ret;

	/* Setup system-uncached-dma32 heap mapping */
	ret = ba->MapNameToIonHeap(kDmabufSystemUncachedDma32HeapName,
				   ION_SYSTEM,
				   ION_FLAG_DMA32,
				   ION_HEAP_TYPE_SYSTEM,
				   ION_FLAG_DMA32);
	if (ret)
	{
		MALI_GRALLOC_LOGE("No uncached heap! Falling back to system!");
	}

	/* Setup system-dma32 heap mapping. */
	ret = ba->MapNameToIonHeap(kDmabufSystemDma32HeapName,
				   ION_SYSTEM,
				   ION_FLAG_CACHED | ION_FLAG_CACHED_NEEDS_SYNC | ION_FLAG_DMA32,
				   ION_HEAP_TYPE_SYSTEM,
				   ION_FLAG_CACHED | ION_FLAG_CACHED_NEEDS_SYNC | ION_FLAG_DMA32);
	if (ret)
        {
		MALI_GRALLOC_LOGE("failed to map cached_system_heap.");
	}

	/* Setup CMA heap */
	ret = ba->MapNameToIonHeap(DMABUF_CMA,
				   ION_CMA, 0,
				   ION_HEAP_TYPE_DMA, 0);
	if (ret)
        {
		MALI_GRALLOC_LOGE("failed to map cma_heap.");
        }

	return 0;
}

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

int allocator_sync_start(const private_handle_t *handle, bool read, bool write)
{
	if (handle == nullptr)
	{
		return -EINVAL;
	}

	return call_dma_buf_sync_ioctl(handle->share_fd, DMA_BUF_SYNC_START, read, write);
}

int allocator_sync_end(const private_handle_t *handle, bool read, bool write)
{
	if (handle == nullptr)
	{
		return -EINVAL;
	}

	return call_dma_buf_sync_ioctl(handle->share_fd, DMA_BUF_SYNC_END, read, write);
}

void allocator_free(private_handle_t *handle)
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

int allocator_allocate(const buffer_descriptor_t *descriptor, private_handle_t **out_handle)
{
	unsigned int priv_heap_flag = private_handle_t::PRIV_FLAGS_USES_DBH;
	uint64_t usage;
	int shared_fd = -1;
	private_handle_t *handle = nullptr;
	int ret = 0;

	if ( NULL == s_buf_allocator )
	{
                s_buf_allocator = new BufferAllocator();
		if ( NULL == s_buf_allocator )
		{
                        MALI_GRALLOC_LOGE("Failed to new a BufferAllocator instance.");
                        return -1;
                }

		ret = setup_mappings(s_buf_allocator);
		if (ret)
		{
			MALI_GRALLOC_LOGE("Could not setup heap mappings!");
			return ret;
		}
        }

	usage = descriptor->consumer_usage | descriptor->producer_usage;

	const char* heap_name = pick_dmabuf_heap(usage);
	if ( NULL == heap_name )
	{
		MALI_GRALLOC_LOGE("Failed to find an appropriate dmabuf_heap.");
		ret = -1;
		goto fail;
	}
	shared_fd = s_buf_allocator->Alloc(heap_name, descriptor->size);
	if (shared_fd < 0)
	{
		MALI_GRALLOC_LOGE("Alloc failed.");
		ret = -ENOMEM;
		goto fail;
	}

	handle = make_private_handle(
	    priv_heap_flag, descriptor->size, descriptor->consumer_usage,
	    descriptor->producer_usage, shared_fd, descriptor->hal_format, descriptor->alloc_format,
	    descriptor->width, descriptor->height, descriptor->size, descriptor->layer_count,
	    descriptor->plane_info,
	    descriptor->plane_info[0].byte_stride,
	    descriptor->pixel_stride);
	if (nullptr == handle)
	{
		MALI_GRALLOC_LOGE("Private handle could not be created for descriptor");
		ret = -ENOMEM;
		goto fail;
	}
	else
	{
		/* Ownership transferred to handle. */
		shared_fd = -1;
	}

	if (usage & GRALLOC_USAGE_PROTECTED)
	{
		goto success;
	}

	/*-------------------------------------------------------*/

	ret = allocator_map(handle);
	if (ret != 0)
	{
		MALI_GRALLOC_LOGE("mmap failed, fd ( %d )", handle->share_fd);
		goto fail;
	}
	/* 标识当前 buf 已经被 map. */
	handle->lockState = private_handle_t::LOCK_STATE_MAPPED;

	/*-------------------------------------------------------*/

#ifndef GRALLOC_INIT_AFBC
#define GRALLOC_INIT_AFBC 0
#endif
	if (GRALLOC_INIT_AFBC && is_format_afbc(descriptor->alloc_format))
	{
		allocator_sync_start(handle, true, true);

		/* For separated plane YUV, there is a header to initialise per plane. */
		const plane_info_t *plane_info = descriptor->plane_info;
		const bool is_multi_plane = handle->is_multi_plane();
		for (int i = 0; i < MAX_PLANES && (i == 0 || plane_info[i].byte_stride != 0); i++)
		{
			init_afbc(static_cast<uint8_t *>(handle->base) + plane_info[i].offset,
			          descriptor->alloc_format,
			          is_multi_plane,
			          plane_info[i].alloc_width,
			          plane_info[i].alloc_height);
		}

		allocator_sync_end(handle, true, true);
	}
success:
	*out_handle = handle;
	return 0;
fail:
	if (shared_fd != -1)
	{
		close(shared_fd);
	}

	if (handle != nullptr)
	{
		allocator_free(handle);
		native_handle_delete(handle);
	}

	return ret;
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


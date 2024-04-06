/*
 * Copyright (C) 2010 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

// #define ENABLE_DEBUG_LOG
#include "custom_log.h"

#include <vector>

#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <inttypes.h>

#include <cutils/log.h>
#include <cutils/atomic.h>
#include <cutils/properties.h>
#include <hardware/hardware.h>
#include <hardware/gralloc.h>

#define USE_GRALLOC_0
#include <hardware/gralloc_rockchip.h>

#include "gralloc_priv.h"
#include "alloc_device.h"
#include "allocator/allocator.h"

#include <sys/mman.h>

static pthread_mutex_t s_map_lock = PTHREAD_MUTEX_INITIALIZER;

static void init_version_info(void)
{
	char acCommit[128];

	ALOGI(RK_GRAPHICS_VER);

	/* RK_GRAPHICS_VER=commit-id:067e5d0: only keep string after '=' */
	sscanf(RK_GRAPHICS_VER, "%*[^=]=%127s", acCommit);

	property_set("vendor.ggralloc.commit", acCommit);
}

static int gralloc_device_open(const hw_module_t *module, const char *name, hw_device_t **device)
{
	int status = -EINVAL;

	init_version_info();

	if (!strncmp(name, GRALLOC_HARDWARE_GPU0, MALI_GRALLOC_HARDWARE_MAX_STR_LEN))
	{
		status = alloc_device_open(module, name, device);
	}

	return status;
}

static int gralloc_register_buffer(gralloc_module_t const *module, buffer_handle_t handle)
{
	MALI_IGNORE(module);

	if (private_handle_t::validate(handle) < 0)
	{
		AERR("Registering invalid buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	// if this handle was created in this process, then we keep it as is.
	private_handle_t *hnd = static_cast<private_handle_t *>(const_cast<native_handle_t *>(handle) );

	int retval;

	pthread_mutex_lock(&s_map_lock);

	hnd->pid = getpid();

	{
		unsigned char *mappedAddress;
		size_t size = hnd->size;

		mappedAddress = static_cast<unsigned char *>(mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, hnd->share_fd, 0) );

		if (MAP_FAILED == mappedAddress)
		{
			AERR("mmap( share_fd:%d ) failed with %s",  hnd->share_fd, strerror(errno));
			retval = -errno;
			goto cleanup;
		}

		hnd->base = mappedAddress + hnd->offset;
		hnd->lockState &= ~(private_handle_t::LOCK_STATE_UNREGISTERED);

		pthread_mutex_unlock(&s_map_lock);
		return 0;
	}

cleanup:
	pthread_mutex_unlock(&s_map_lock);
	return retval;
}

static void unmap_buffer(private_handle_t *hnd)
{
	{
		void *base = static_cast<void *>(hnd->base);
		size_t size = hnd->size;

		if (munmap(base, size) < 0)
		{
			AERR("Could not munmap base:0x%p size:%lu '%s'", base, (unsigned long)size, strerror(errno));
		}
	}

	hnd->base = 0;
	hnd->lockState = 0;
	hnd->writeOwner = 0;
}

static int gralloc_unregister_buffer(gralloc_module_t const *module, buffer_handle_t handle)
{
	MALI_IGNORE(module);

	if (private_handle_t::validate(handle) < 0)
	{
		AERR("unregistering invalid buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	private_handle_t *hnd = static_cast<private_handle_t *>(const_cast<native_handle_t *>(handle) );

	AERR_IF(hnd->lockState & private_handle_t::LOCK_STATE_READ_MASK, "[unregister] handle %p still locked (state=%08x)", hnd, hnd->lockState);

	if (hnd->pid == getpid()) // never unmap buffers that were not registered in this process
	{
		pthread_mutex_lock(&s_map_lock);

		hnd->lockState &= ~(private_handle_t::LOCK_STATE_MAPPED);

		/* if handle is still locked, the unmapping would not happen until unlocked*/
		if (!(hnd->lockState & private_handle_t::LOCK_STATE_WRITE))
		{
			unmap_buffer(hnd);
		}

		hnd->lockState |= private_handle_t::LOCK_STATE_UNREGISTERED;

		pthread_mutex_unlock(&s_map_lock);
	}
	else
	{
		AERR("Trying to unregister buffer 0x%p from process %d that was not created in current process: %d", hnd, hnd->pid, getpid());
	}

	return 0;
}

/*---------------------------------------------------------------------------*/

enum tx_direction
{
        TX_NONE = 0,
        TX_TO_DEVICE,
        TX_FROM_DEVICE,
        TX_BOTH,
};

/*
 * 根据 'usage' 确定(返回) CUP 将对 buffer 的读写操作.
 */
static enum tx_direction get_tx_direction(const uint64_t usage)
{
        const bool read = (usage & GRALLOC_USAGE_SW_READ_MASK) ? true : false;
        const bool write = (usage & GRALLOC_USAGE_SW_WRITE_MASK) ? true : false;
        enum tx_direction dir = TX_NONE;

        if (read && write)
        {
                dir = TX_BOTH;
        }
        else if (write)
        {
                dir = TX_TO_DEVICE;
        }
        else if (read)
        {
                dir = TX_FROM_DEVICE;
        }

        return dir;
}

/*
 * 完成对 buffer cache 的 sync 操作.
 * 'direction':
 *	若是 TX_NONE, 表征当前函数被 unlock() (mali_gralloc_unlock()) 调用.
 *	否则, 表征当前函数被 lock() (mali_gralloc_lock(), ...) 调用,
 *		其 value 表征 lock 后, client 将对 buffer 执行读还是写操作.
 */
static void buffer_sync(private_handle_t *hnd, tx_direction direction)
{
	/* 若本函数被 lock() 调用, 则... */
        if (direction != TX_NONE)
        {
                hnd->cpu_read = direction == TX_FROM_DEVICE || direction == TX_BOTH;
                hnd->cpu_write = direction == TX_TO_DEVICE || direction == TX_BOTH;

                int status = allocator_sync_start(hnd, hnd->cpu_read, hnd->cpu_write);
                if (status < 0)
                {
                        return;
                }
        }
	/* 若本函数被 unlock() 调用, 且 CPU 对 buffer 已经有读写操作, 则... */
        else if (hnd->cpu_read || hnd->cpu_write)
        {
                int status = allocator_sync_end(hnd, hnd->cpu_read, hnd->cpu_write);
                if (status < 0)
                {
                        return;
                }

                hnd->cpu_read = 0;
                hnd->cpu_write = 0;
        }
}

static int gralloc_lock(gralloc_module_t const *module, buffer_handle_t handle, int usage, int l, int t, int w, int h, void **vaddr)
{


	if (private_handle_t::validate(handle) < 0)
	{
		AERR("Locking invalid buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	private_handle_t *hnd = static_cast<private_handle_t *>(const_cast<native_handle_t *>(handle) );

	pthread_mutex_lock(&s_map_lock);

	if (hnd->lockState & private_handle_t::LOCK_STATE_UNREGISTERED)
	{
		AERR("Locking on an unregistered buffer 0x%p, returning error", hnd);
		pthread_mutex_unlock(&s_map_lock);
		return -EINVAL;
	}

	{
		hnd->writeOwner = usage & GRALLOC_USAGE_SW_WRITE_MASK;
	}

	hnd->lockState |= private_handle_t::LOCK_STATE_WRITE;

	pthread_mutex_unlock(&s_map_lock);

	if (usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK))
	{
		*vaddr = static_cast<void *>(hnd->base);
		buffer_sync(hnd,
			    get_tx_direction(usage)); // direction
	}

	MALI_IGNORE(module);
	MALI_IGNORE(l);
	MALI_IGNORE(t);
	MALI_IGNORE(w);
	MALI_IGNORE(h);
	return 0;
}

static int gralloc_lock_ycbcr(gralloc_module_t const* module,
			      buffer_handle_t handle,
			      int usage,
			      int l, int t, int w, int h,
			      android_ycbcr *ycbcr)
{
	if (private_handle_t::validate(handle) < 0)
	{
		AERR("Locking invalid buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	private_handle_t *hnd = static_cast<private_handle_t *>(const_cast<native_handle_t *>(handle) );

	/*-------------------------------------------------------*/

	pthread_mutex_lock(&s_map_lock);

	if (hnd->lockState & private_handle_t::LOCK_STATE_UNREGISTERED)
	{
		AERR("Locking on an unregistered buffer 0x%p, returning error", hnd);
		pthread_mutex_unlock(&s_map_lock);
		return -EINVAL;
	}

	hnd->writeOwner = usage & GRALLOC_USAGE_SW_WRITE_MASK;

	hnd->lockState |= private_handle_t::LOCK_STATE_WRITE;

	pthread_mutex_unlock(&s_map_lock);

	/*-------------------------------------------------------*/

	if (usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK))
	{
		char *vaddr = static_cast<char *>((hnd->base) );
		buffer_sync(hnd,
			    get_tx_direction(usage)); // direction

		// this is currently only used by camera for yuv420sp
		// if in future other formats are needed, store to private
		// handle and change the below code based on private format.

		int ystride;
		switch (hnd->format) {
			case HAL_PIXEL_FORMAT_YCrCb_420_SP: // NV21
				ystride = hnd->stride;
				ycbcr->y  = static_cast<void*>(vaddr);
				ycbcr->cr = static_cast<void*>(vaddr + ystride * hnd->height); // 'cr' : V
				ycbcr->cb = static_cast<void*>(vaddr + ystride * hnd->height + 1); // 'cb : U
				ycbcr->ystride = ystride;
				ycbcr->cstride = ystride;
				ycbcr->chroma_step = 2;
				memset(ycbcr->reserved, 0, sizeof(ycbcr->reserved));
				break;
			case HAL_PIXEL_FORMAT_YCrCb_NV12:
				ystride = hnd->stride;
				ycbcr->y  = static_cast<void*>(vaddr);
				ycbcr->cr = static_cast<void*>(vaddr + ystride *  hnd->height + 1);
				ycbcr->cb = static_cast<void*>(vaddr + ystride *  hnd->height);
				ycbcr->ystride = ystride;
				ycbcr->cstride = ystride;
				ycbcr->chroma_step = 2;
				memset(ycbcr->reserved, 0, sizeof(ycbcr->reserved));
				break;
			case HAL_PIXEL_FORMAT_YV12:
				ystride = hnd->stride;
				ycbcr->ystride = ystride;
				ycbcr->cstride = (ystride/2 + 15) & ~15;
				ycbcr->y  = static_cast<void*>(vaddr);
				ycbcr->cr = static_cast<void*>(vaddr + ystride * hnd->height);
				ycbcr->cb = static_cast<void*>(vaddr + ystride * hnd->height + ycbcr->cstride * hnd->height/2);
				ycbcr->chroma_step = 1;
				memset(ycbcr->reserved, 0, sizeof(ycbcr->reserved));
				break;
			case HAL_PIXEL_FORMAT_YCbCr_422_SP:
				ystride = hnd->stride;
				ycbcr->y  = static_cast<void*>(vaddr);
				ycbcr->cb = static_cast<void*>(vaddr + ystride * hnd->height);
				ycbcr->cr = static_cast<void*>(vaddr + ystride * hnd->height + 1);
				ycbcr->ystride = ystride;
				ycbcr->cstride = ystride;
				ycbcr->chroma_step = 2;
				memset(ycbcr->reserved, 0, sizeof(ycbcr->reserved));
				break;
			default:
				ALOGE("%s: Invalid format passed: 0x%x", __FUNCTION__, hnd->format);
				return -EINVAL;
		}
	}

	/*-------------------------------------------------------*/

	MALI_IGNORE(module);
	MALI_IGNORE(l);
	MALI_IGNORE(t);
	MALI_IGNORE(w);
	MALI_IGNORE(h);
	return 0;
}

static int gralloc_unlock(gralloc_module_t const *module, buffer_handle_t handle)
{
	MALI_IGNORE(module);

	if (private_handle_t::validate(handle) < 0)
	{
		AERR("Unlocking invalid buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	private_handle_t *hnd = static_cast<private_handle_t *>(const_cast<native_handle_t *>(handle) );

	/*-------------------------------------------------------*/
	pthread_mutex_lock(&s_map_lock);

	hnd->lockState &= ~(private_handle_t::LOCK_STATE_WRITE);

	/* if the handle has already been unregistered, unmap it here*/
	if (hnd->lockState & private_handle_t::LOCK_STATE_UNREGISTERED)
	{
		unmap_buffer(hnd);
	}

	pthread_mutex_unlock(&s_map_lock);

	/*-------------------------------------------------------*/
	buffer_sync(hnd, TX_NONE);

	return 0;
}

/*---------------------------------------------------------------------------*/

/* map rkvdec_scaling_metadata_buf. */
static inline int gralloc_rsm_map( struct private_handle_t *hnd )
{
	int rval = -1;
	int prot_flags = PROT_READ | PROT_WRITE;

	if ( !hnd )
	{
		goto out;
	}
	if ( hnd->rsm_fd < 0 )
	{
		ALOGE("rkvdec_scaling_metadata_buf is not available to be mapped");
		goto out;
	}

	hnd->rsm_base = mmap( NULL, hnd->rsm_size, prot_flags, MAP_SHARED, hnd->rsm_fd, 0 );
	if(hnd->rsm_base == MAP_FAILED)
	{
		ALOGE("Failed to mmap rkvdec_scaling_metadata_buf err=%s",strerror(errno));
		goto out;
	}

	rval = 0;

out:
	return rval;
}

/* unmap rkvdec_scaling_metadata_buf. */
static inline int gralloc_rsm_unmap( struct private_handle_t *hnd )
{
	int rval = -1;

	if( !hnd )
	{
		ALOGE("%s: handle is null",__FUNCTION__);
		goto out;
	}
	if( hnd->rsm_base != MAP_FAILED )
	{
		if ( munmap( hnd->rsm_base, hnd->rsm_size ) == 0 )
		{
			hnd->rsm_base = MAP_FAILED;
			rval = 0;
		}
	}

out:
	return rval;
}

/*---------------------------------------------------------------------------*/

/*
 * Map the rk_ashmem area before attempting to
 * read/write from it.
 *
 * 'readwrite':
 *	若将对 map 后的 buffer 写入数据, 则要传入 1.
 *	否则, 传入 0.
 *
 * Return 0 on success.
 */
static inline int gralloc_rk_ashmem_map( struct private_handle_t *hnd, int readwrite)
{
	int rval = -1;
	int prot_flags = PROT_READ;

	if ( !hnd )
	{
		goto out;
	}

	if ( hnd->ashmem_fd < 0 )
	{
		ALOGE("Shared attribute region not available to be mapped");
		goto out;
	}

	if ( readwrite )
	{
		prot_flags |=  PROT_WRITE;
	}

	hnd->ashmem_base = mmap( NULL, hnd->ashmem_size, prot_flags, MAP_SHARED, hnd->ashmem_fd, 0 );
	if(hnd->ashmem_base == MAP_FAILED)
	{
		ALOGE("Failed to mmap shared attribute region err=%s",strerror(errno));
		goto out;
	}

	rval = 0;

out:
	return rval;
}

/*
 * Unmap the rk_ashmem area when done with it.
 *
 * Return 0 on success.
 */
static inline int gralloc_rk_ashmem_unmap( struct private_handle_t *hnd )
{
	int rval = -1;

	if( !hnd )
	{
		ALOGE("%s:handle is null",__FUNCTION__);
		goto out;
	}

	if( hnd->ashmem_base != MAP_FAILED )
	{
		if ( munmap( hnd->ashmem_base, hnd->ashmem_size ) == 0 )
		{
			hnd->ashmem_base = MAP_FAILED;
			rval = 0;
		}
	}

out:
	return rval;
}

static inline int gralloc_rk_ashmem_read( struct private_handle_t *hnd, struct rk_ashmem_t *val )
{
	int rval = -1;

	if( !hnd || !val )
	{
		ALOGE("%s:parameters is null",__FUNCTION__);
		goto out;
	}

	if( hnd->ashmem_base != MAP_FAILED )
	{
		memcpy(val, hnd->ashmem_base, sizeof(struct rk_ashmem_t));
		rval = 0;
	}

out:
	return rval;
}

static inline int gralloc_rk_ashmem_write( struct private_handle_t *hnd, struct rk_ashmem_t *val )
{
	int rval = -1;

	if( !hnd || !val)
	{
		ALOGE("%s:parameters is null",__FUNCTION__);
		goto out;
	}

	if( hnd->ashmem_base != MAP_FAILED )
	{
		memcpy(hnd->ashmem_base, val, sizeof(struct rk_ashmem_t));
		rval = 0;
	}

out:
	return rval;
}

/*---------------------------------------------------------------------------*/

static bool is_buffer_unregistered(private_handle_t *handle)
{
	return (handle->lockState & private_handle_t::LOCK_STATE_UNREGISTERED);
}

// "rk_ashmem_t": 定义在 hardware/libhardware/include/hardware/gralloc.h 中
// "buffer_handle_t": typedef const native_handle_t* buffer_handle_t;
static int get_rk_ashmem(buffer_handle_t _handle, struct rk_ashmem_t* rk_ashmem)
{
	int ret = 0;
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if ( NULL == handle )
	{
		return -EINVAL;
	}

	if ( is_buffer_unregistered(handle) )
	{
		AERR("getting rk_ashmem on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	if (rk_ashmem)
	{
		ret = 0;
		if ( gralloc_rk_ashmem_map(handle, 0) >= 0 )
		{
			if ( gralloc_rk_ashmem_read(handle, rk_ashmem) < 0 )
			{
				ALOGE("%s: gralloc_rk_ashmem_read fail",__FUNCTION__);
				ret = -EINVAL;
			}
			gralloc_rk_ashmem_unmap(handle);
		} else {
			ALOGE("%s: gralloc_rk_ashmem_map fail",__FUNCTION__);
			ret = -EINVAL;
		}
	}
	else
	{
		ALOGE("%s: rk_ashmem is null",__FUNCTION__);
		ret = -EINVAL;
	}

	return ret;
}

int set_rk_ashmem(buffer_handle_t _handle, struct rk_ashmem_t* rk_ashmem)
{
	int ret = 0;
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if ( NULL == handle )
	{
		return -EINVAL;
	}

	if ( is_buffer_unregistered(handle) )
	{
		E("setting rk_ashmem on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	if (rk_ashmem)
	{
		ret = 0;
		if ( gralloc_rk_ashmem_map(handle, 1) >= 0 )
		{
			if ( gralloc_rk_ashmem_write(handle, rk_ashmem) < 0 )
			{
				ALOGE("%s: gralloc_rk_ashmem_write fail",__FUNCTION__);
				return -EINVAL;
			}
			gralloc_rk_ashmem_unmap(handle);
		}
		else
		{
			ALOGE("%s: gralloc_rk_ashmem_map fail",__FUNCTION__);
			ret = -EINVAL;
		}
	}
	else
	{
		ALOGE("%s: rk_ashmem is null",__FUNCTION__);
		ret = -EINVAL;
	}

	return ret;
}

int get_prime_fd(buffer_handle_t _handle, int *fd)
{
        struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

        if (!handle)
        {
                return -EINVAL;
        }
        if ( is_buffer_unregistered(handle) )
        {
                E("getting prime_fd on an unregistered buffer 0x%p, returning error", handle);
                return -EINVAL;
        }

        *fd = handle->share_fd;

        return 0;
}

int get_attributes(buffer_handle_t _handle, void *attrs)
{
        struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);
        std::vector<int> *attributes = (std::vector<int> *)attrs;

        if (!handle)
        {
                return -EINVAL;
        }
        if ( is_buffer_unregistered(handle) )
        {
                E("getting attributes on an unregistered buffer 0x%p, returning error", handle);
                return -EINVAL;
        }

        attributes->clear();
        attributes->push_back(handle->width);
        attributes->push_back(handle->height);
        attributes->push_back(handle->pixel_stride);
        attributes->push_back(handle->format);
        attributes->push_back(handle->size);
        attributes->push_back(handle->stride);

        return 0;
}

int get_internal_format(buffer_handle_t _handle, uint64_t *internal_format)
{
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if (!handle)
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		E("getting internal_format on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	*internal_format = handle->alloc_format;

	return 0;
}

int get_width(buffer_handle_t _handle, int *width)
{
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if (!handle)
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		E("getting width on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	*width = handle->width;

	return 0;
}

int get_height(buffer_handle_t _handle, int *height)
{
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if (!handle)
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		E("getting height on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	*height = handle->height;

	return 0;
}

int get_pixel_stride(buffer_handle_t _handle, int *pixel_stride)
{
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if (!handle)
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		E("getting pixel_stride on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	*pixel_stride = handle->pixel_stride;

	return 0;
}

int get_byte_stride(buffer_handle_t _handle, int *byte_stride)
{
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if (!handle)
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		E("getting byte_stride on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	*byte_stride = handle->stride;

	return 0;
}

int get_format(buffer_handle_t _handle, int *format)
{
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if (!handle)
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		E("getting format on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	*format = handle->format;

	return 0;
}

int get_size(buffer_handle_t _handle, int *size)
{
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if (!handle)
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		E("getting size on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	*size = handle->size;

	return 0;
}

int get_usage(buffer_handle_t _handle, int *usage)
{
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if (!handle)
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		E("getting usage on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	*usage = handle->usage;

	return 0;
}

int lock_rkvdec_scaling_metadata(buffer_handle_t _handle,
				 metadata_for_rkvdec_scaling_t** metadata)
{
        int ret = 0;
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if ( NULL == handle )
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		AERR("lock rkvdec_scaling_metadata on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	ret = gralloc_rsm_map(handle);
	if ( ret != 0 )
	{
		return ret;
	}
	*metadata = static_cast<metadata_for_rkvdec_scaling_t*>(handle->rsm_base);

        return ret;
}

int unlock_rkvdec_scaling_metadata(buffer_handle_t _handle)
{
        int ret = 0;
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if ( NULL == handle )
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		AERR("unlock rkvdec_scaling_metadata on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	ret = gralloc_rsm_unmap(handle);

        return ret;
}

static int set_offset_of_dynamic_hdr_metadata(buffer_handle_t _handle, int64_t offset)
{
	int ret = 0;
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if ( NULL == handle )
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		AERR("setting offset_of_dynamic_hdr_metadata on an unregistered buffer 0x%p", handle);
		return -EINVAL;
	}

	if ( gralloc_rk_ashmem_map(handle, 1) >= 0 )
	{
		if( handle->ashmem_base != MAP_FAILED )
		{
			struct rk_ashmem_t *rk_ashmem = static_cast<struct rk_ashmem_t*>(handle->ashmem_base);
			D("rk_ashmem: %p, "
					"&(rk_ashmem->offset_of_dynamic_hdr_metadata): %p, "
					"sizeof(struct rk_ashmem_t): %zd",
				rk_ashmem,
				&(rk_ashmem->offset_of_dynamic_hdr_metadata),
				sizeof(struct rk_ashmem_t) );
			rk_ashmem->offset_of_dynamic_hdr_metadata = offset;
		}

		gralloc_rk_ashmem_unmap(handle);
	} else {
		ALOGE("%s: gralloc_rk_ashmem_map fail",__FUNCTION__);
		ret = -EINVAL;
	}

	return ret;
}

static int get_offset_of_dynamic_hdr_metadata(buffer_handle_t _handle, int64_t *offset)
{
	int ret = 0;
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);
	struct rk_ashmem_t *rk_ashmem;

	if ( NULL == handle || NULL == offset )
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		AERR("getting offset_of_dynamic_hdr_metadata on an unregistered buffer 0x%p", handle);
		return -EINVAL;
	}

	if ( gralloc_rk_ashmem_map(handle, 0) >= 0 )
	{
		if( handle->ashmem_base != MAP_FAILED )
		{
			rk_ashmem = static_cast<struct rk_ashmem_t*>(handle->ashmem_base);
			*offset = rk_ashmem->offset_of_dynamic_hdr_metadata;
		}

		gralloc_rk_ashmem_unmap(handle);
	} else {
		ALOGE("%s: gralloc_rk_ashmem_map fail",__FUNCTION__);
		ret = -EINVAL;
	}

	return ret;
}

int get_buffer_id(buffer_handle_t _handle, uint64_t *buffer_id)
{
	struct private_handle_t *handle = private_handle_t::dynamicCast(_handle);

	if (!handle)
	{
		return -EINVAL;
	}
	if ( is_buffer_unregistered(handle) )
	{
		E("getting buffer_id on an unregistered buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	*buffer_id = handle->backing_store_id;

	return 0;
}
// .CP : 

static int gralloc_perform(const struct gralloc_module_t *mod, int op, ...)
{
	MALI_IGNORE(mod);
	va_list args;
	int err;

	/*-------------------------------------------------------*/

	va_start(args, op);
	switch (op)
	{
		case static_cast<int>(GRALLOC_MODULE_PERFORM_GET_RK_ASHMEM):
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			struct rk_ashmem_t* rk_ashmem = va_arg(args, struct rk_ashmem_t*);

			err = get_rk_ashmem(hnd, rk_ashmem);

			break;
		}
		case static_cast<int>(GRALLOC_MODULE_PERFORM_SET_RK_ASHMEM):
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			struct rk_ashmem_t* rk_ashmem = va_arg(args, struct rk_ashmem_t*);

			err = set_rk_ashmem(hnd,rk_ashmem);

			break;
		}
		case GRALLOC_MODULE_PERFORM_GET_HADNLE_PHY_ADDR:
                {
#if 0
                        buffer_handle_t hnd = va_arg(args, buffer_handle_t);
                        uint32_t *phy_addr = va_arg(args, uint32_t *);

                        if (phy_addr != NULL)
                                err = get_phy_addr(hnd,phy_addr);
                        else
                                err = -EINVAL;
#else
                        E("not implemented");
                        err = -1;
#endif
                        break;
                }
		case GRALLOC_MODULE_PERFORM_GET_HADNLE_PRIME_FD:
		{
                        buffer_handle_t hnd = va_arg(args, buffer_handle_t);
                        int *fd = va_arg(args, int *);

                        if (fd != NULL)
                        {
                                err = get_prime_fd(hnd, fd);
                        }
                        else
                        {
                                err = -EINVAL;
                        }

		        break;
		}
		case GRALLOC_MODULE_PERFORM_GET_HADNLE_ATTRIBUTES:
                {
                        buffer_handle_t hnd = va_arg(args, buffer_handle_t);
                        std::vector<int> *attrs = va_arg(args, std::vector<int> *);

                        if (attrs != NULL)
                        {
                                err = get_attributes(hnd, static_cast<void*>(attrs) );
                        }
                        else
                        {
                                err = -EINVAL;
                        }

                        break;
                }
		case GRALLOC_MODULE_PERFORM_GET_INTERNAL_FORMAT:
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			uint64_t *internal_format = va_arg(args, uint64_t *);

			if(internal_format != NULL)
			{
				err = get_internal_format(hnd, internal_format);
			}
			else
			{
				err = -EINVAL;
			}

			break;
		}
		case GRALLOC_MODULE_PERFORM_GET_HADNLE_WIDTH:
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			int *width = va_arg(args, int *);

			if(width != NULL)
			{
				err = get_width(hnd, width);
			}
			else
			{
				err = -EINVAL;
			}

			break;
		}
		case GRALLOC_MODULE_PERFORM_GET_HADNLE_HEIGHT:
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			int *height = va_arg(args, int *);

			if(height != NULL)
			{
				err = get_height(hnd, height);
			}
			else
			{
				err = -EINVAL;
			}

			break;
		}
		case GRALLOC_MODULE_PERFORM_GET_HADNLE_STRIDE:
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			int *stride = va_arg(args, int *);

			if(stride != NULL)
			{
				err = get_pixel_stride(hnd, stride);
			}
			else
			{
				err = -EINVAL;
			}

			break;
		}
		case GRALLOC_MODULE_PERFORM_GET_HADNLE_BYTE_STRIDE:
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			int *byte_stride = va_arg(args, int *);

			if(byte_stride != NULL)
				err = get_byte_stride(hnd, byte_stride);
			else
				err = -EINVAL;
			break;
		}
		case GRALLOC_MODULE_PERFORM_GET_HADNLE_FORMAT:
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			int *format = va_arg(args, int *);

			if(format != NULL)
			{
				err = get_format(hnd, format);
			}
			else
			{
				err = -EINVAL;
			}
			break;
		}
		case GRALLOC_MODULE_PERFORM_GET_HADNLE_SIZE:
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			int *size = va_arg(args, int *);

			if(size != NULL)
			{
				err = get_size(hnd, size);
			}
			else
			{
				err = -EINVAL;
			}

			break;
		}
		case GRALLOC_MODULE_PERFORM_GET_USAGE:
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			int *usage = va_arg(args, int *);

			if(usage != NULL)
			{
				err = get_usage(hnd, usage);
			}
			else
			{
				err = -EINVAL;
			}

			break;
		}
		case static_cast<int>(GRALLOC_MODULE_PERFORM_LOCK_RKVDEC_SCALING_METADATA):
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			metadata_for_rkvdec_scaling_t** metadata = va_arg(args, metadata_for_rkvdec_scaling_t**);

			if (metadata != NULL)
			{
				err = lock_rkvdec_scaling_metadata(hnd, metadata);
				if ( err != 0)
				{
					E("err");
				}
			}
			else
			{
				E("err");
				err = -EINVAL;
			}

			break;
		}
		case static_cast<int>(GRALLOC_MODULE_PERFORM_UNLOCK_RKVDEC_SCALING_METADATA):
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);

			err = unlock_rkvdec_scaling_metadata(hnd);
			if ( err != 0)
			{
				E("err");
			}

			break;
		}
		case static_cast<int>(GRALLOC_MODULE_PERFORM_SET_OFFSET_OF_DYNAMIC_HDR_METADATA):
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			int64_t offset = va_arg(args, int64_t);

			D("offset: %" PRId64, offset);
			err = set_offset_of_dynamic_hdr_metadata(hnd, offset);
			if ( err != 0)
			{
				E("err");
			}

			break;
		}
		case static_cast<int>(GRALLOC_MODULE_PERFORM_GET_OFFSET_OF_DYNAMIC_HDR_METADATA):
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			int64_t *offset = va_arg(args, int64_t*);

			err = get_offset_of_dynamic_hdr_metadata(hnd, offset);
			if ( err != 0)
			{
				E("err");
			}

			break;
		}
		case static_cast<int>(GRALLOC_MODULE_PERFORM_GET_BUFFER_ID):
		{
			buffer_handle_t hnd = va_arg(args, buffer_handle_t);
			uint64_t *buffer_id = va_arg(args, uint64_t*);

			if(buffer_id != NULL)
			{
				err = get_buffer_id(hnd, buffer_id);
			}
			else
			{
				err = -EINVAL;
			}

			break;
		}
#if 0   // .CP : 
#endif
		default:
			err = -EINVAL;
			break;
	}
	va_end(args);

	return err;
}

/*---------------------------------------------------------------------------*/

// There is one global instance of the module

static struct hw_module_methods_t gralloc_module_methods =
{
	.open = gralloc_device_open
};

private_module_t::private_module_t()
{
#define INIT_ZERO(obj) (memset(&(obj),0,sizeof((obj))))

	base.common.tag = HARDWARE_MODULE_TAG;
	base.common.version_major = 1;
	base.common.version_minor = 0;
	base.common.id = GRALLOC_HARDWARE_MODULE_ID;
	base.common.name = "Graphics Memory Allocator Module";
	base.common.author = "ARM Ltd.";
	base.common.methods = &gralloc_module_methods;
	base.common.dso = NULL;
	INIT_ZERO(base.common.reserved);

	base.registerBuffer = gralloc_register_buffer;
	base.unregisterBuffer = gralloc_unregister_buffer;
	base.lock = gralloc_lock;
	base.lock_ycbcr = gralloc_lock_ycbcr;
	base.unlock = gralloc_unlock;
	base.perform = gralloc_perform;
	INIT_ZERO(base.reserved_proc);

	framebuffer = NULL;
	numBuffers = 0;
	bufferMask = 0;
	pthread_mutex_init(&(lock), NULL);
	currentBuffer = NULL;
	INIT_ZERO(info);
	INIT_ZERO(finfo);
	xdpi = 0.0f;
	ydpi = 0.0f;
	fps = 0.0f;

#undef INIT_ZERO
};

/*
 * HAL_MODULE_INFO_SYM will be initialized using the default constructor
 * implemented above
 */
struct private_module_t HAL_MODULE_INFO_SYM;


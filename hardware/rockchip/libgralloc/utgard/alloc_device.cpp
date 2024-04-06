/*
 * Copyright (C) 2010 ARM Limited. All rights reserved.
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
#include <pthread.h>
#include <inttypes.h>

#include <cutils/log.h>
#include <cutils/atomic.h>
#include <hardware/hardware.h>
#include <hardware/gralloc.h>

#define USE_GRALLOC_0
#include <hardware/gralloc_rockchip.h>

#include <sys/ioctl.h>

#include "alloc_device.h"
#include "gralloc_priv.h"
#include "gralloc_helper.h"

#include "log.h"
#include "core/buffer_descriptor.h"
#include "core/buffer_allocation.h"
#include "allocator/shared_memory/shared_memory.h"

#define GRALLOC_ALIGN( value, base ) (((value) + ((base) - 1)) & ~((base) - 1))

static void get_yuv_info(int usage, mali_gralloc_yuv_info *yuv_info)
{
	int private_usage = usage & (GRALLOC_USAGE_PRIVATE_0 | GRALLOC_USAGE_PRIVATE_1);

	switch (private_usage)
	{
		case 0:
			*yuv_info = MALI_YUV_BT601_NARROW;
			break;

		case GRALLOC_USAGE_PRIVATE_1:
			*yuv_info = MALI_YUV_BT601_WIDE;
			break;

		case GRALLOC_USAGE_PRIVATE_0:
			*yuv_info = MALI_YUV_BT709_NARROW;
			break;

		case (GRALLOC_USAGE_PRIVATE_0 | GRALLOC_USAGE_PRIVATE_1):
			*yuv_info = MALI_YUV_BT709_WIDE;
			break;
	}

}

static int get_specific_hal_format(int req_format, int usage)
{
	int specific_format;

	if ( req_format == HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED )
	{
		if ( (usage & GRALLOC_USAGE_HW_VIDEO_ENCODER)
			|| (usage & GRALLOC_USAGE_HW_CAMERA_WRITE) )
		{
			D("to use NV12 for HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED, usage : 0x%x.",
				usage);
			specific_format = HAL_PIXEL_FORMAT_YCrCb_NV12;
		}
		else
		{
			D("to use RGBX_8888, for HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED, usage : 0x%x.",
				usage);
			specific_format = HAL_PIXEL_FORMAT_RGBX_8888;
		}
	}
	else if ( HAL_PIXEL_FORMAT_YCbCr_420_888 == req_format )
	{
		D("to use NV12 for HAL_PIXEL_FORMAT_YCbCr_420_888.");
		specific_format = HAL_PIXEL_FORMAT_YCrCb_NV12;
	}
	else
	{
		specific_format = req_format;
	}

	return specific_format;
}

static int alloc_device_alloc(alloc_device_t *dev,
			      int w,
			      int h,
			      int format,
			      int usage,
			      buffer_handle_t *pHandle,
			      int *pStride) // pixel_stride
#if 1
{
	MALI_IGNORE(dev);

	buffer_descriptor_t grallocDescriptor;
	buffer_descriptor_t* bufferDescriptor = &grallocDescriptor;
	private_handle_t *hnd = nullptr; // 将返回到 *pHandle 中.

	bufferDescriptor->width = w;
	bufferDescriptor->height = h;
	bufferDescriptor->producer_usage = usage;
	bufferDescriptor->consumer_usage = usage;
	bufferDescriptor->hal_format = format;
	bufferDescriptor->layer_count = 1;
	bufferDescriptor->signature = sizeof(buffer_descriptor_t);

	if (mali_gralloc_buffer_allocate(bufferDescriptor, &hnd) != 0)
	{
		MALI_GRALLOC_LOGE("%s, buffer allocation failed with %d", __func__, errno);
		return -1;
	}

	hnd->reserved_region_size = bufferDescriptor->reserved_size;

	hnd->usage = hnd->producer_usage | hnd->consumer_usage;
	hnd->format = get_specific_hal_format(hnd->req_format, hnd->usage);
	get_yuv_info(hnd->usage, &(hnd->yuv_info) );

	/*-------------------------------------------------------*/

	hnd->ashmem_size = PAGE_SIZE;

	std::tie(hnd->ashmem_fd, hnd->ashmem_base) =
		gralloc_shared_memory_allocate("rk_ashmem", hnd->ashmem_size);
	if (hnd->ashmem_fd < 0 || hnd->ashmem_base == MAP_FAILED)
	{
		MALI_GRALLOC_LOGE("%s, shared memory allocation failed with errno %d", __func__, errno);
		mali_gralloc_buffer_free(hnd);
		return -1;
	}

	struct rk_ashmem_t* rk_ashmem = static_cast<struct rk_ashmem_t*>(hnd->ashmem_base);
	rk_ashmem->alreadyStereo = 0;
	rk_ashmem->displayStereo = 0;
	strcpy(rk_ashmem->LayerName, "");
	rk_ashmem->offset_of_dynamic_hdr_metadata = -1;

	munmap(hnd->ashmem_base, hnd->ashmem_size);
	hnd->ashmem_base = MAP_FAILED;

	/*-------------------------------------------------------*/

	hnd->rsm_size = PAGE_SIZE;

	std::tie(hnd->rsm_fd, hnd->rsm_base) =
		gralloc_shared_memory_allocate("rkvdec_scaling_metadata_buf", hnd->rsm_size);
	if (hnd->rsm_fd < 0 || hnd->rsm_base == MAP_FAILED)
	{
		E("rkvdec_scaling_metadata_buf allocation failed with errno %d", errno);
		close(hnd->ashmem_fd);
		mali_gralloc_buffer_free(hnd);
		return -1;
	}

	metadata_for_rkvdec_scaling_t* metadata = static_cast<metadata_for_rkvdec_scaling_t*>(hnd->rsm_base);
	memset(static_cast<void *>(metadata), 0, sizeof(*metadata));

	munmap(hnd->rsm_base, hnd->rsm_size);
	hnd->rsm_base = MAP_FAILED;

	/*-------------------------------------------------------*/
#if 0	// 这段代码引用的函数依赖 gralloc 4.0 的 mapper 接口, 先不引入. 
	hnd->attr_size = mapper::common::shared_metadata_size() + hnd->reserved_region_size;
	std::tie(hnd->share_attr_fd, hnd->attr_base) =
		gralloc_shared_memory_allocate("gralloc_shared_memory", hnd->attr_size);
	if (hnd->share_attr_fd < 0 || hnd->attr_base == MAP_FAILED)
	{
		MALI_GRALLOC_LOGE("%s, shared memory allocation failed with errno %d", __func__, errno);
		mali_gralloc_buffer_free(hnd);
		return -1;
	}

	mapper::common::shared_metadata_init(hnd->attr_base, bufferDescriptor->name);
	const uint32_t base_format = bufferDescriptor->alloc_format & MALI_GRALLOC_INTFMT_FMT_MASK;
	const uint64_t usage = bufferDescriptor->consumer_usage | bufferDescriptor->producer_usage;
	android_dataspace_t dataspace;
	get_format_dataspace(base_format, usage, hnd->width, hnd->height, &dataspace, &hnd->yuv_info);

	mapper::common::set_dataspace(hnd, static_cast<mapper::common::Dataspace>(dataspace));
	/*
	* We need to set attr_base to MAP_FAILED before the HIDL callback
	* to avoid sending an invalid pointer to the client process.
	*
	* hnd->attr_base = mmap(...);
	* hidl_callback(hnd); // client receives hnd->attr_base = <dangling pointer>
	*/
	munmap(hnd->attr_base, hnd->attr_size);
	hnd->attr_base = MAP_FAILED;
#endif

	{
		buffer_descriptor_t* bufDescriptor = bufferDescriptor;
		D("got new private_handle_t instance @%p for buffer '%s'. share_fd : %d, "
			// "share_attr_fd : %d, "
			"flags : 0x%x, width : %d, height : %d, "
			"req_format : 0x%x, producer_usage : 0x%" PRIx64 ", consumer_usage : 0x%" PRIx64 ", "
			"format: %d, stride : %d, pixel_stride: %d, "
			"alloc_format : 0x%" PRIx64 ", size : %d, layer_count : %u, backing_store_size : %d, "
			"backing_store_id : %" PRIu64 ", "
			"allocating_pid : %d, ref_count : %d, yuv_info : %d",
			hnd, (bufDescriptor->name).c_str() == nullptr ? "unset" : (bufDescriptor->name).c_str(),
		  hnd->share_fd,
		  // hnd->share_attr_fd,
		  hnd->flags, hnd->width, hnd->height,
		  hnd->req_format, hnd->producer_usage, hnd->consumer_usage,
		  hnd->format, hnd->stride, hnd->pixel_stride,
		  hnd->alloc_format, hnd->size, hnd->layer_count, hnd->backing_store_size,
		  hnd->backing_store_id,
		  hnd->allocating_pid, hnd->ref_count, hnd->yuv_info);
#ifdef ENABLE_DEBUG_LOG
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
#endif
	}

	*pHandle = hnd;
	*pStride = hnd->pixel_stride;
	D("*pStride: %d", *pStride);
	return 0;
}
#else
{
	if (!pHandle || !pStride)
	{
		return -EINVAL;
	}

	size_t size;
	size_t stride;

	if (format == HAL_PIXEL_FORMAT_YCrCb_420_SP || format == HAL_PIXEL_FORMAT_YV12
	        /* HAL_PIXEL_FORMAT_YCbCr_420_SP, HAL_PIXEL_FORMAT_YCbCr_420_P, HAL_PIXEL_FORMAT_YCbCr_422_I are not defined in Android.
	         * To enable Mali DDK EGLImage support for those formats, firstly, you have to add them in Android system/core/include/system/graphics.h.
	         * Then, define SUPPORT_LEGACY_FORMAT in the same header file(Mali DDK will also check this definition).
	         */
#ifdef SUPPORT_LEGACY_FORMAT
	        || format == HAL_PIXEL_FORMAT_YCbCr_420_SP || format == HAL_PIXEL_FORMAT_YCbCr_420_P || format == HAL_PIXEL_FORMAT_YCbCr_422_I
#endif
	   )
	{
		switch (format)
		{
			case HAL_PIXEL_FORMAT_YCrCb_420_SP:
				stride = GRALLOC_ALIGN(w, 16);
				size = GRALLOC_ALIGN(h, 16) * (stride + GRALLOC_ALIGN(stride / 2, 16));
				break;

			case HAL_PIXEL_FORMAT_YV12:
#ifdef SUPPORT_LEGACY_FORMAT
			case HAL_PIXEL_FORMAT_YCbCr_420_P:
#endif
				stride = GRALLOC_ALIGN(w, 16);
				size = GRALLOC_ALIGN(h, 2) * (stride + GRALLOC_ALIGN(stride / 2, 16));

				break;
#ifdef SUPPORT_LEGACY_FORMAT

			case HAL_PIXEL_FORMAT_YCbCr_420_SP:
				stride = GRALLOC_ALIGN(w, 16);
				size = GRALLOC_ALIGN(h, 16) * (stride + GRALLOC_ALIGN(stride / 2, 16));
				break;

			case HAL_PIXEL_FORMAT_YCbCr_422_I:
				stride = GRALLOC_ALIGN(w, 16);
				size = h * stride * 2;

				break;
#endif

			default:
				return -EINVAL;
		}
	}
	else
	{
		int bpp = 0;

		switch (format)
		{
			case HAL_PIXEL_FORMAT_RGBA_8888:
			case HAL_PIXEL_FORMAT_RGBX_8888:
			case HAL_PIXEL_FORMAT_BGRA_8888:
				bpp = 4;
				break;

			case HAL_PIXEL_FORMAT_RGB_888:
				bpp = 3;
				break;

			case HAL_PIXEL_FORMAT_RGB_565:
				bpp = 2;
				break;

			default:
				return -EINVAL;
		}

		size_t bpr = GRALLOC_ALIGN(w * bpp, 64);
		size = bpr * h;
		stride = bpr / bpp;
	}

	int err;

	err = gralloc_alloc_buffer(dev, size, usage, pHandle);

	if (err < 0)
	{
		return err;
	}

	/* match the framebuffer format */
	if (usage & GRALLOC_USAGE_HW_FB)
	{
#ifdef GRALLOC_16_BITS
		format = HAL_PIXEL_FORMAT_RGB_565;
#else
		format = HAL_PIXEL_FORMAT_BGRA_8888;
#endif
	}

	private_handle_t *hnd = (private_handle_t *)*pHandle;
	int               private_usage = usage & (GRALLOC_USAGE_PRIVATE_0 |
	                                  GRALLOC_USAGE_PRIVATE_1);

	switch (private_usage)
	{
		case 0:
			hnd->yuv_info = MALI_YUV_BT601_NARROW;
			break;

		case GRALLOC_USAGE_PRIVATE_1:
			hnd->yuv_info = MALI_YUV_BT601_WIDE;
			break;

		case GRALLOC_USAGE_PRIVATE_0:
			hnd->yuv_info = MALI_YUV_BT709_NARROW;
			break;

		case (GRALLOC_USAGE_PRIVATE_0 | GRALLOC_USAGE_PRIVATE_1):
			hnd->yuv_info = MALI_YUV_BT709_WIDE;
			break;
	}

	hnd->width = w;
	hnd->height = h;
	hnd->format = format;
	hnd->stride = stride;

	*pStride = stride;
	return 0;
}
#endif

static int alloc_device_free(alloc_device_t *dev, buffer_handle_t handle)
{
	MALI_IGNORE(dev);

	if (private_handle_t::validate(handle) < 0)
	{
		return -EINVAL;
	}

	private_handle_t *hnd = static_cast<private_handle_t *>(const_cast<native_handle_t *>(handle) );

	{
		/* Buffer might be unregistered so we need to check for invalid ump handle*/
		if (0 != hnd->base)
		{
			if (0 != munmap(static_cast<void *>(hnd->base), hnd->size))
			{
				AERR("Failed to munmap handle 0x%p", hnd);
			}
		}
		close(hnd->share_fd);

		close(hnd->ashmem_fd);
		// 目前设计中, 此时 'hnd->ashmem_base' 是 MAP_FAILED.

		close(hnd->rsm_fd);
		// 目前设计中, 预期 'hnd->rsm_base' 是 MAP_FAILED.

		memset(static_cast<void *>(hnd), 0, sizeof(*hnd));
	}

	delete hnd;

	return 0;
}

static int alloc_device_close(struct hw_device_t *device)
{
	alloc_device_t *dev = reinterpret_cast<alloc_device_t *>(device);

	if (dev)
	{
		delete dev;
	}

	return 0;
}

int alloc_device_open(hw_module_t const *module, const char *name, hw_device_t **device)
{
	MALI_IGNORE(name);
	alloc_device_t *dev;

	dev = new alloc_device_t;

	if (NULL == dev)
	{
		return -1;
	}

	/* initialize our state here */
	memset(dev, 0, sizeof(*dev));

	/* initialize the procs */
	dev->common.tag = HARDWARE_DEVICE_TAG;
	dev->common.version = 0;
	dev->common.module = const_cast<hw_module_t *>(module);
	dev->common.close = alloc_device_close;
	dev->alloc = alloc_device_alloc;
	dev->free = alloc_device_free;

	*device = &dev->common;

	return 0;
}

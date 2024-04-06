/*
 * Copyright (C) 2016-2022 ARM Limited. All rights reserved.
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
#include <errno.h>
#include <inttypes.h>
#include <inttypes.h>
#include <mutex>
/* For error codes. */
#include <hardware/gralloc1.h>

#include "buffer_access.h"
#include "buffer.h"
#include "gralloc/formats.h"
#include "usages.h"
#include "allocator/allocator.h"
#include "helper_functions.h"
#include "format_info.h"

enum tx_direction
{
	TX_NONE = 0,
	TX_TO_DEVICE,
	TX_FROM_DEVICE,
	TX_BOTH,
};

/* Mutex used to ensure the buffer map/unmap actions are synchronized */
static std::mutex g_map_mutex;

unique_private_handle make_private_handle(int size, uint64_t consumer_usage, uint64_t producer_usage,
                                          android::base::unique_fd shared_fd, int required_format,
                                          internal_format_t allocated_format, int width, int height,
                                          int layer_count, const plane_layout &plane_info, int stride)
{
	void *mem = native_handle_create(PRIVATE_HANDLE_NUM_FDS, PRIVATE_HANDLE_NUM_INTS);
	if (mem == nullptr)
	{
		MALI_GRALLOC_LOGE("private_handle_t allocation failed");
		return nullptr;
	}

	return unique_private_handle{new (mem) private_handle_t(size, consumer_usage, producer_usage, shared_fd.release(), required_format,
	                                                        allocated_format, width, height, layer_count, plane_info, stride)};
}

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

static void buffer_sync(imported_handle *hnd, tx_direction direction)
{
	if (direction != TX_NONE)
	{
		++hnd->lock_count;
		hnd->cpu_write = direction == TX_TO_DEVICE || direction == TX_BOTH;
		int cpu_read = direction == TX_FROM_DEVICE || direction == TX_BOTH;

		int status = allocator_sync_start(hnd, cpu_read, hnd->cpu_write);
		if (status < 0)
		{
			return;
		}
	}
	else
	{
		if (hnd->cpu_write)
		{
			/* Flush the cache only when there is a cpu_write lock. */
			int status = allocator_sync_end(hnd, false, hnd->cpu_write);
			if (status < 0)
			{
				return;
			}
		}
		hnd->lock_count > 0 ? --hnd->lock_count : hnd->lock_count = 0;
		if (hnd->lock_count == 0)
		{
			hnd->cpu_write = 0;
		}
	}
}

/*
 *  Validates input parameters of lock request.
 *
 * @param buffer   [in]    The buffer to lock.
 * @param l        [in]    Access region left offset (in pixels).
 * @param t        [in]    Access region top offset (in pixels).
 * @param w        [in]    Access region requested width (in pixels).
 * @param h        [in]    Access region requested height (in pixels).
 * @param usage    [in]    Lock request (producer and consumer combined) usage.
 *
 * @return 0,for valid input parameters;
 *         -EINVAL, for erroneous input parameters
 */
int validate_lock_input_parameters(const imported_handle *hnd, const int l,
                                   const int t, const int w, const int h,
                                   uint64_t usage)
{
	GRALLOC_UNUSED(usage);
	const int lock_pid = getpid();
#if 0
	auto alloc_format = hnd->alloc_format;
#endif

	if ((l < 0) || (t < 0) || (w < 0) || (h < 0))
	{
		MALI_GRALLOC_LOGE("Negative values for access region (l = %d t = %d w = %d and "
		     "h = %d) in buffer lock request are invalid. Locking PID:%d",
		      l, t, w, h, lock_pid);
		return -EINVAL;
	}

	/* Test overflow conditions on access region parameters */
	if (((l + w) < 0) || ((t + h) < 0))
	{
		MALI_GRALLOC_LOGE("Encountered overflow with access region (l = %d t = %d w = %d and"
		     " h = %d) in buffer lock request. Locking PID:%d",
		       l, t, w, h, lock_pid);
		return -EINVAL;
	}

	/* Region of interest shall be inside the allocated buffer */
	if (((t + h) > hnd->height)  || ((l + w) > hnd->width))
	{
		MALI_GRALLOC_LOGE("Buffer lock access region (l = %d t = %d w = %d "
		     "and h = %d) is outside allocated buffer (width = %d and height = %d)"
		     " Locking PID:%d", l, t, w, h, hnd->width, hnd->height, lock_pid);
		return -EINVAL;
	}

#if 0
	/* Reject lock requests with:
	 * 1: CPU usages not being present
	 * 2: Consumer/producer flags not set (with the exception of BLOB format)
	 */
	const auto format = alloc_format.get_base();
	if (!(usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK)) ||
	    (format != MALI_GRALLOC_FORMAT_INTERNAL_BLOB && (!(usage & (hnd->consumer_usage | hnd->producer_usage)))))
	{
		MALI_GRALLOC_LOG(ERROR) << std::showbase
		    << "Lock is not supported for non-CPU usages or buffer not compatible. Locked with usage "
		    << std::hex << usage << " buffer (format = " << alloc_format << ", usage = "
		    << (hnd->consumer_usage | hnd->producer_usage) << " )";
		return -EINVAL;
	}
#endif

	/* Locking process must call importBuffer first */
	if (hnd->import_pid != lock_pid)
	{
		MALI_GRALLOC_LOG(ERROR) << "Attempt to lock buffer before importBuffer";
		return -EINVAL;
	}

#if 0
	/* Reject lock requests for AFBC (compressed format) enabled buffers */
	if (alloc_format.has_modifiers())
	{
		MALI_GRALLOC_LOG(ERROR) << "Lock is not supported for AFBC enabled buffers. Internal format: "
		                        << alloc_format;
		return GRALLOC1_ERROR_UNSUPPORTED;
	}
#endif

	return 0;
}


/*
 *  Locks the given buffer for the specified CPU usage.
 *
 * @param m        [in]    Gralloc module.
 * @param buffer   [in]    The buffer to lock.
 * @param usage    [in]    Producer and consumer combined usage.
 * @param l        [in]    Access region left offset (in pixels).
 * @param t        [in]    Access region top offset (in pixels).
 * @param w        [in]    Access region requested width (in pixels).
 * @param h        [in]    Access region requested height (in pixels).
 * @param vaddr    [out]   To be filled with a CPU-accessible pointer to
 *                         the buffer data for CPU usage.
 *
 * @return 0, when the locking is successful;
 *         Appropriate error, otherwise
 *
 * @Note:  There is no way to ascertain whether buffer data is valid or not (for
 *         example, establishing if the h/w needs to finish rendering or if CPU
 *         caches need to be synchronized).
 *
 * @Note:  Locking a buffer simultaneously for write or read/write leaves the
 *         buffer's content in an indeterminate state.
 */
int mali_gralloc_lock(imported_handle *hnd,
                      uint64_t usage, int l, int t, int w, int h, void **vaddr)
{
	/* Validate input parameters for lock request */
	if (auto status = validate_lock_input_parameters(hnd, l, t, w, h, usage); status != 0)
	{
		return status;
	}

	const auto alloc_format = hnd->alloc_format;
	const auto *format_info = alloc_format.get_base_info();
	if (format_info == nullptr)
	{
		MALI_GRALLOC_LOG(ERROR) << "Corrupted buffer format " << alloc_format << " of buffer " << hnd;
		return -EINVAL;
	}

	/* Populate CPU-accessible pointer when requested for CPU usage */
	if ((usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK)) != 0)
	{
		if (vaddr == nullptr)
		{
			return -EINVAL;
		}

		if (auto status = mali_map_buffer(hnd); status != 0)
		{
			return status;
		}

		*vaddr = hnd->base;
		buffer_sync(hnd, get_tx_direction(usage));
	}

	return 0;
}

/*
 *  Locks the given ycbcr buffer for the specified CPU usage. This function can
 *  only be used for buffers with "8 bit sample depth"
 *
 * @param m        [in]    Gralloc module.
 * @param buffer   [in]    The buffer to lock.
 * @param usage    [in]    Producer and consumer combined usage.
 * @param l        [in]    Access region left offset (in pixels).
 * @param t        [in]    Access region top offset (in pixels).
 * @param w        [in]    Access region requested width (in pixels).
 * @param h        [in]    Access region requested height (in pixels).
 * @param ycbcr    [out]   Describes YCbCr formats for consumption by applications.
 *
 * @return 0, when the locking is successful;
 *         Appropriate error, otherwise
 *
 * @Note:  There is no way to ascertain whether buffer data is valid or not (for
 *         example, establishing if the h/w needs to finish rendering or if CPU
 *         caches need to be synchronized).
 *
 * @Note:  Locking a buffer simultaneously for write or read/write leaves the
 *         buffer's content in an indeterminate state.
 *
 */
int mali_gralloc_lock_ycbcr(imported_handle *hnd,
                            const uint64_t usage, const int l, const int t,
                            const int w, const int h, android_ycbcr *ycbcr)
{
	/* Validate input parameters for lock request */
	if (auto status = validate_lock_input_parameters(hnd, l, t, w, h, usage); status != 0)
	{
		return status;
	}

	const auto alloc_format = hnd->alloc_format;
	const auto *format_info = alloc_format.get_base_info();
	if (format_info == nullptr)
	{
		MALI_GRALLOC_LOG(ERROR) << "Corrupted buffer format " << alloc_format << " of buffer " << hnd;
		return -EINVAL;
	}

	if (format_info->is_yuv != true)
	{
		MALI_GRALLOC_LOG(ERROR) << "Buffer format: " << alloc_format << " is not a YUV compatible format";
		return -EINVAL;
	}

	if (usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK))
	{
		if (nullptr == ycbcr)
		{
			return -EINVAL;
		}

		if (auto status = mali_map_buffer(hnd); status != 0)
		{
			return status;
		}

		ycbcr->y = static_cast<char *>(hnd->base);
		ycbcr->ystride = hnd->plane_info[0].byte_stride;

		switch (alloc_format.get_base())
		{
		case MALI_GRALLOC_FORMAT_INTERNAL_Y8:
		case MALI_GRALLOC_FORMAT_INTERNAL_Y16:
			/* No UV plane */
			ycbcr->cstride = 0;
			ycbcr->cb = nullptr;
			ycbcr->cr = nullptr;
			ycbcr->chroma_step = 0;
		break;

		case MALI_GRALLOC_FORMAT_INTERNAL_NV12:
			/* UV plane */
			ycbcr->cstride = hnd->plane_info[1].byte_stride;
			ycbcr->cb = static_cast<char *>(hnd->base) + hnd->plane_info[1].offset;
			ycbcr->cr = static_cast<char *>(ycbcr->cb) + 1;
			ycbcr->chroma_step = 2;
			break;

		case MALI_GRALLOC_FORMAT_INTERNAL_NV21:
			/* VU plane */
			ycbcr->cstride = hnd->plane_info[1].byte_stride;
			ycbcr->cr = static_cast<char *>(hnd->base) + hnd->plane_info[1].offset;
			ycbcr->cb = static_cast<char *>(ycbcr->cr) + 1;
			ycbcr->chroma_step = 2;
			break;

		case MALI_GRALLOC_FORMAT_INTERNAL_YV12:
			/* V plane, U plane */
			ycbcr->cstride = hnd->plane_info[1].byte_stride;
			ycbcr->cr = static_cast<char *>(hnd->base) + hnd->plane_info[1].offset;
			ycbcr->cb = static_cast<char *>(hnd->base) + hnd->plane_info[2].offset;
			ycbcr->chroma_step = 1;
			break;
		case MALI_GRALLOC_FORMAT_INTERNAL_YU12:
			/* U plane, V plane */
			ycbcr->cstride = hnd->plane_info[1].byte_stride;
			ycbcr->cb = static_cast<char *>(hnd->base) + hnd->plane_info[1].offset;
			ycbcr->cr = static_cast<char *>(hnd->base) + hnd->plane_info[2].offset;
			ycbcr->chroma_step = 1;
			break;
		default:
			MALI_GRALLOC_LOG(ERROR) << "Buffer: " << hnd <<" of format " << alloc_format
			                        << "can't be represented in android_ycbcr format";
			return -EINVAL;
		}

		buffer_sync(hnd, get_tx_direction(usage));
	}
	else
	{
		ycbcr->y = NULL;
		ycbcr->cb = NULL;
		ycbcr->cr = NULL;
		ycbcr->ystride = 0;
		ycbcr->cstride = 0;
		ycbcr->chroma_step = 0;
	}

	/* Reserved parameters should be set to 0 by gralloc's (*lock_ycbcr)()*/
	memset(ycbcr->reserved, 0, sizeof(ycbcr->reserved));

	return 0;
}

/*
 *  Unlocks the given buffer.
 *
 * @param m           [in]   Gralloc module.
 * @param buffer      [in]   The buffer to unlock.
 *
 * @return 0, when the locking is successful;
 *         Appropriate error, otherwise
 *
 * Note: unlocking a buffer which is not locked results in an unexpected behaviour.
 *       Though it is possible to create a state machine to track the buffer state to
 *       recognize erroneous conditions, it is expected of client to adhere to API
 *       call sequence
 */
int mali_gralloc_unlock(imported_handle *hnd)
{
	buffer_sync(hnd, TX_NONE);
	return 0;
}

/*
 *  Returns the number of flex layout planes which are needed to represent the
 *  given buffer.
 *
 * @param m           [in]   Gralloc module.
 * @param buffer      [in]   The buffer handle for which the number of planes should be queried
 * @param num_planes  [out]  The number of flex planes required to describe the given buffer
 *
 * @return GRALLOC1_ERROR_NONE         The buffer's format can be represented in flex layout
 *         GRALLOC1_ERROR_UNSUPPORTED - The buffer's format can't be represented in flex layout
 */
int mali_gralloc_get_num_flex_planes(const buffer_handle_t buffer,
                                     uint32_t * const num_planes)
{
	auto handle = handle_cast<private_handle_t>(buffer);
	auto alloc_format = handle->alloc_format;

	if (alloc_format.has_modifiers())
	{
		MALI_GRALLOC_LOG(ERROR) << "AFBC enabled buffers can't be represented in flex layout. Internal format: "
		                        << alloc_format;
		return GRALLOC1_ERROR_UNSUPPORTED;
	}

	const auto *format_info = alloc_format.get_base_info();
	if (format_info == nullptr)
	{
		MALI_GRALLOC_LOG(ERROR) << "Corrupted buffer format " << alloc_format << " of buffer " << buffer;
		return -EINVAL;
	}

	if (format_info->flex != true)
	{
		MALI_GRALLOC_LOG(ERROR) << "Format " << alloc_format << " of " << buffer << " can't be represented in flex";
		return GRALLOC1_ERROR_UNSUPPORTED;
	}

	*num_planes = format_info->total_components();

	return GRALLOC1_ERROR_NONE;
}

/*
 *  Sets Android flex layout parameters.
 *
 * @param top_left            [in]  Pointer to the first byte of the top-left
 *                                  pixel of the plane.
 * @param component           [in]  Plane's flex format (YUVA/RGBA).
 * @param bits_per_component  [in]  Bits allocated for the component in each pixel.
 * @param bits_used           [in]  Number of the most significant bits used in
 *                                  the format for this component.
 * @param h_increment         [in]  Horizontal increment, in bytes, in plane to
 *                                  traverse to next horizontal pixel
 * @param v_increment         [in]  Vertical increment, in bytes, in plane to
 *                                  traverse to next vertical pixel
 * @param h_subsampling       [in]  Horizontal subsampling. Must be a positive power of 2.
 * @param v_subsampling       [in]  Vertical subsampling. Must be a positive power of 2.
 * @param plane               [out] Flex plane layout, to be composed.
 *
 */
static void set_flex_plane_params(uint8_t * const top_left,
                                  const android_flex_component_t component,
                                  const int32_t bits_per_component,
                                  const int32_t bits_used,
                                  const int32_t h_increment,
                                  const int32_t v_increment,
                                  const int32_t h_subsampling,
                                  const int32_t v_subsampling,
                                  android_flex_plane_t * const plane)
{
	plane->top_left = top_left;
	plane->component = component;
	plane->bits_per_component = bits_per_component;
	plane->bits_used = bits_used;
	plane->h_increment = h_increment;
	plane->v_increment = v_increment;
	plane->h_subsampling = h_subsampling;
	plane->v_subsampling = v_subsampling;

	return;
}

/*
 *  Locks the Gralloc 1.0 buffer, for the specified CPU usage.
 *  This function can be called on any format but populates layout parameters
 *  only for formats compatible with Android Flex Format.
 *
 * @param m           [in]   Gralloc module.
 * @param buffer      [in]   The buffer to lock.
 * @param usage       [in]   Producer and consumer combined usage.
 * @param l           [in]   Access region left offset (in pixels).
 * @param t           [in]   Access region top offset (in pixels).
 * @param w           [in]   Access region requested width (in pixels).
 * @param h           [in]   Access region requested height (in pixels).
 * @param flex_layout [out]  Describes flex YUV format for consumption by applications.
 *
 * @return 0, when the locking is successful;
 *         Appropriate error, otherwise
 */
int mali_gralloc_lock_flex(imported_handle *hnd, const uint64_t usage, const int l, const int t, const int w,
                           const int h, struct android_flex_layout *const flex_layout)
{
	/* Validate input parameters for lock request */
	int status = validate_lock_input_parameters(hnd, l, t, w, h, usage);
	if (status != 0)
	{
		return status;
	}

	const auto alloc_format = hnd->alloc_format;
	const auto *format_info = alloc_format.get_base_info();
	if (format_info == nullptr)
	{
		MALI_GRALLOC_LOG(ERROR) << "Corrupted buffer format " << alloc_format << " of buffer " << hnd;
		return -EINVAL;
	}

	if (format_info->flex != true)
	{
		MALI_GRALLOC_LOG(ERROR) << "Format " << alloc_format << " of " << hnd << " can't be represented in flex";
		return GRALLOC1_ERROR_UNSUPPORTED;
	}

	status = mali_map_buffer(hnd);
	if (status != 0)
	{
		return status;
	}

	flex_layout->num_planes = format_info->total_components();
	switch (alloc_format.get_base())
	{
	case MALI_GRALLOC_FORMAT_INTERNAL_Y8:
		flex_layout->format = FLEX_FORMAT_Y;
		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_Y, 8, 8, 1,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_Y16:
		flex_layout->format = FLEX_FORMAT_Y;
		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_Y, 16, 16, 2,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_NV12:
		/* Y:UV 4:2:0 */
		flex_layout->format = FLEX_FORMAT_YCbCr;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_Y, 8, 8, 1,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset,
		                      FLEX_COMPONENT_Cb, 8, 8, 2,
		                      hnd->plane_info[1].byte_stride, 2, 2,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset + 1,
		                      FLEX_COMPONENT_Cr, 8, 8, 2,
		                      hnd->plane_info[1].byte_stride, 2, 2,
		                      &flex_layout->planes[2]);
		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_NV21:
		/* Y:VU 4:2:0 ordering. The flex format plane order must still
		 * follow YCbCr order (as defined by 'android_flex_component_t').
		 */
		flex_layout->format = FLEX_FORMAT_YCbCr;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_Y, 8, 8, 1,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset + 1,
		                      FLEX_COMPONENT_Cb, 8, 8, 2,
		                      hnd->plane_info[1].byte_stride, 2, 2,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset,
		                      FLEX_COMPONENT_Cr, 8, 8, 2,
		                      hnd->plane_info[1].byte_stride, 2, 2,
		                      &flex_layout->planes[2]);
		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_YV12:
		/* Y:V:U 4:2:0 . The flex format plane order must still follow YCbCr
		 * order (as defined by 'android_flex_component_t').
		 */
		flex_layout->format = FLEX_FORMAT_YCbCr;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_Y, 8, 8, 1,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[2].offset,
		                      FLEX_COMPONENT_Cb, 8, 8, 1,
		                      hnd->plane_info[2].byte_stride, 2, 2,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset,
		                      FLEX_COMPONENT_Cr, 8, 8, 1,
		                      hnd->plane_info[1].byte_stride, 2, 2,
		                      &flex_layout->planes[2]);
		break;
	case MALI_GRALLOC_FORMAT_INTERNAL_YU12:
		/* Y:V:U 4:2:0 */
		flex_layout->format = FLEX_FORMAT_YCbCr;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_Y, 8, 8, 1,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset,
		                      FLEX_COMPONENT_Cb, 8, 8, 1,
		                      hnd->plane_info[2].byte_stride, 2, 2,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[2].offset,
		                      FLEX_COMPONENT_Cr, 8, 8, 1,
		                      hnd->plane_info[1].byte_stride, 2, 2,
		                      &flex_layout->planes[2]);
		break;
	case MALI_GRALLOC_FORMAT_INTERNAL_P010:
		/* Y:UV 4:2:0 */
		flex_layout->format = FLEX_FORMAT_YCbCr;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_Y, 16, 10, 2,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset,
		                      FLEX_COMPONENT_Cb, 16, 10, 4,
		                      hnd->plane_info[1].byte_stride, 2, 2,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset + 2,
		                      FLEX_COMPONENT_Cr, 16, 10, 4,
		                      hnd->plane_info[1].byte_stride, 2, 2,
		                      &flex_layout->planes[2]);
		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_P210:
		/* Y:UV 4:2:2 */
		flex_layout->format = FLEX_FORMAT_YCbCr;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_Y, 16, 10, 2,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset,
		                      FLEX_COMPONENT_Cb, 16, 10, 4,
		                      hnd->plane_info[1].byte_stride, 2, 1,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset + 2,
		                      FLEX_COMPONENT_Cr, 16, 10, 4,
		                      hnd->plane_info[1].byte_stride, 2, 1,
		                      &flex_layout->planes[2]);
		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT:
		/* YUYV 4:2:2 */
		flex_layout->format = FLEX_FORMAT_YCbCr;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_Y, 8, 8, 2,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + 1, FLEX_COMPONENT_Cb, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 2, 1,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + 3, FLEX_COMPONENT_Cr, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 2, 1,
		                      &flex_layout->planes[2]);

		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_NV16:
		/* Y:UV 4:2:2 */
		flex_layout->format = FLEX_FORMAT_YCbCr;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_Y, 8, 8, 1,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset,
		                      FLEX_COMPONENT_Cb, 8, 8, 2,
		                      hnd->plane_info[1].byte_stride, 2, 1,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + hnd->plane_info[1].offset + 1,
		                      FLEX_COMPONENT_Cr, 8, 8, 2,
		                      hnd->plane_info[1].byte_stride, 2, 1,
		                      &flex_layout->planes[2]);

		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_Y210:
		/* YUYV 4:2:2 */
		flex_layout->format = FLEX_FORMAT_YCbCr;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_Y, 16, 10, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + 2, FLEX_COMPONENT_Cb, 16, 10, 8,
		                      hnd->plane_info[0].byte_stride, 2, 1,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + 6, FLEX_COMPONENT_Cr, 16, 10, 8,
		                      hnd->plane_info[0].byte_stride, 2, 1,
		                      &flex_layout->planes[2]);

		break;

	/* 64-bit format that has 16-bit R, G, B, and A components, in that order */
	case MALI_GRALLOC_FORMAT_INTERNAL_RGBA_16161616:
		flex_layout->format = FLEX_FORMAT_RGBA;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_R, 16, 16, 8,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + 2, FLEX_COMPONENT_G, 16, 16, 8,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + 4, FLEX_COMPONENT_B, 16, 16, 8,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[2]);
		set_flex_plane_params((uint8_t *)hnd->base + 6, FLEX_COMPONENT_A, 16, 16, 8,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[3]);
		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_RGBA_8888:
		/* 32-bit format that has 8-bit R, G, B, and A components, in that order */
		flex_layout->format = FLEX_FORMAT_RGBA;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_R, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + 1, FLEX_COMPONENT_G, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + 2, FLEX_COMPONENT_B, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[2]);
		set_flex_plane_params((uint8_t *)hnd->base + 3, FLEX_COMPONENT_A, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[3]);
		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_RGBX_8888:
		/* 32-bit format that has 8-bit R, G, B, and unused components, in that order */
		flex_layout->format = FLEX_FORMAT_RGB;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_R, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + 1, FLEX_COMPONENT_G, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + 2, FLEX_COMPONENT_B, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[2]);
		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_RGB_888:
		/* 24-bit format that has 8-bit R, G, and B components, in that order */
		flex_layout->format = FLEX_FORMAT_RGB;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_R, 8, 8, 3,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + 1, FLEX_COMPONENT_G, 8, 8, 3,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + 2, FLEX_COMPONENT_B, 8, 8, 3,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[2]);
		break;

	case MALI_GRALLOC_FORMAT_INTERNAL_BGRA_8888:
		/* 32-bit format that has 8-bit B, G, R, and A components, in that order.
		 * The flex format plane order must still follow FLEX_FORMAT_RGBA
		 * order (as defined by 'android_flex_component_t').
		 */
		flex_layout->format = FLEX_FORMAT_RGBA;

		set_flex_plane_params((uint8_t *)hnd->base, FLEX_COMPONENT_B, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[2]);
		set_flex_plane_params((uint8_t *)hnd->base + 1, FLEX_COMPONENT_G, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[1]);
		set_flex_plane_params((uint8_t *)hnd->base + 2, FLEX_COMPONENT_R, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[0]);
		set_flex_plane_params((uint8_t *)hnd->base + 3, FLEX_COMPONENT_A, 8, 8, 4,
		                      hnd->plane_info[0].byte_stride, 1, 1,
		                      &flex_layout->planes[3]);
		break;

	default:
		MALI_GRALLOC_LOG(ERROR) << "Can't lock buffer " << hnd << ": format " << alloc_format << " not handled";
		return GRALLOC1_ERROR_UNSUPPORTED;
	}

	buffer_sync(hnd, get_tx_direction(usage));

	return GRALLOC1_ERROR_NONE;
}

/*
 * Maps the buffer to make it accessible to CPU
 *
 * @param hnd [in]  The buffer to map
 *
 * @return 0, if mapping was successful;
 *         Appropriate error, otherwise
 *
 * Note: The function can be safely called on buffers
 * that are already mapped as it checks whether the buffer
 * has previously been mapped.
 */
int mali_map_buffer(imported_handle *hnd)
{
	int status = 0;

	/* Ensure that buffer is only mapped once as there can
	   be multiple lock() requests issued for the same buffer */
	std::scoped_lock lock(g_map_mutex);
	if (!hnd->base)
	{
		status = allocator_map(hnd);
	}
	return status;
}

/*
 * Unmaps the buffer to no longer make it CPU accessible
 *
 * @param hnd [in]  The buffer to map
 *
 * Note: The function can be safely called on buffers that
 * are not currently mapped as it will check whether the buffer
 * was previously mapped.
 */
void mali_unmap_buffer(imported_handle *hnd)
{
	std::scoped_lock lock(g_map_mutex);
	if (hnd->base != nullptr)
	{
		allocator_unmap(hnd);

		/* We expect the allocators implementation to clear hnd->base & cpu flags
		 * but since implementations can change, it is also reset here */
		hnd->base = nullptr;
		hnd->cpu_write = 0;
		hnd->lock_count = 0;
	}
}

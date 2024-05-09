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

// #define ENABLE_DEBUG_LOG
#include "custom_log.h"

#include <string.h>
#include <dlfcn.h>
#include <inttypes.h>
#include <log/log.h>
#include <assert.h>
#include <vector>

#include <cutils/properties.h>

#include <hardware/gralloc1.h>
#include <hardware/gralloc.h>

#include <hardware/hardware_rockchip.h>

#include "buffer_allocation.h"
#include "format_info.h"
#include "helper_functions.h"

/* Producer/consumer definitions.
 * CPU: Software access
 * GPU: Graphics processor
 * DPU: Display processor
 * DPU_AEU: AFBC encoder (input to DPU)
 * VPU: Video processor
 * CAM: Camera ISP
 */
#define MALI_GRALLOC_PRODUCER_CPU     ((uint16_t)1 << 0)
#define MALI_GRALLOC_PRODUCER_GPU     ((uint16_t)1 << 1)
#define MALI_GRALLOC_PRODUCER_DPU     ((uint16_t)1 << 2)
#define MALI_GRALLOC_PRODUCER_DPU_AEU ((uint16_t)1 << 3)
#define MALI_GRALLOC_PRODUCER_VPU     ((uint16_t)1 << 4)
#define MALI_GRALLOC_PRODUCER_CAM     ((uint16_t)1 << 5)

#define MALI_GRALLOC_CONSUMER_CPU     ((uint16_t)1 << 0)
#define MALI_GRALLOC_CONSUMER_GPU     ((uint16_t)1 << 1)
#define MALI_GRALLOC_CONSUMER_DPU     ((uint16_t)1 << 2)
#define MALI_GRALLOC_CONSUMER_VPU     ((uint16_t)1 << 3)

#if 0
/*
 * Determines all IP consumers included by the requested buffer usage.
 * Private usage flags are excluded from this process.
 *
 * @param usage   [in]    Buffer usage.
 *
 * @return flags word of all enabled consumers;
 *         0, if no consumers are enabled
 */
static uint16_t get_consumers(uint64_t usage)
{
	uint16_t consumers = 0;

	/* Private usage is not applicable to consumer derivation */
	usage &= ~GRALLOC_USAGE_PRIVATE_MASK;
	/* Exclude usages also not applicable to consumer derivation */
	usage &= ~GRALLOC_USAGE_PROTECTED;

	if (usage == GRALLOC_USAGE_HW_COMPOSER)
	{
		consumers = MALI_GRALLOC_CONSUMER_DPU;
	}
	else
	{
		if (usage & GRALLOC_USAGE_SW_READ_MASK)
		{
			consumers |= MALI_GRALLOC_CONSUMER_CPU;
		}

		/* GRALLOC_USAGE_HW_FB describes a framebuffer which contains a
		 * pre-composited scene that is scanned-out to a display. This buffer
		 * can be consumed by even the most basic display processor which does
		 * not support multi-layer composition.
		 */
		if (usage & GRALLOC_USAGE_HW_FB)
		{
			consumers |= MALI_GRALLOC_CONSUMER_DPU;
		}

		if (usage & GRALLOC_USAGE_HW_VIDEO_ENCODER)
		{
			consumers |= MALI_GRALLOC_CONSUMER_VPU;
		}

		/* GRALLOC_USAGE_HW_COMPOSER does not explicitly define whether the
		 * display processor is producer or consumer. When used in combination
		 * with GRALLOC_USAGE_HW_TEXTURE, it is assumed to be consumer since the
		 * GPU and DPU both act as compositors.
		 */
		if ((usage & (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_COMPOSER)) ==
		    (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_COMPOSER))
		{
			consumers |= MALI_GRALLOC_CONSUMER_DPU;
		}

		if (usage & (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_GPU_DATA_BUFFER))
		{
			consumers |= MALI_GRALLOC_CONSUMER_GPU;
		}
	}

	return consumers;
}
#endif

/*
 * Determines all IP producers included by the requested buffer usage.
 * Private usage flags are excluded from this process.
 *
 * @param usage   [in]    Buffer usage.
 *
 * @return flags word of all enabled producers;
 *         0, if no producers are enabled
 */
static uint16_t get_producers(uint64_t usage)
{
	uint16_t producers = 0;

	/* Private usage is not applicable to producer derivation */
	usage &= ~GRALLOC_USAGE_PRIVATE_MASK;
	/* Exclude usages also not applicable to producer derivation */
	usage &= ~GRALLOC_USAGE_PROTECTED;

	if (usage == GRALLOC_USAGE_HW_COMPOSER)
	{
		producers = MALI_GRALLOC_PRODUCER_DPU_AEU;
	}
	else
	{
		if (usage & GRALLOC_USAGE_SW_WRITE_MASK)
		{
			producers |= MALI_GRALLOC_PRODUCER_CPU;
		}

		/* DPU is normally consumer however, when there is an alternative
		 * consumer (VPU) and no other producer (e.g. VPU), it acts as a producer.
		 */
		if ((usage & GRALLOC_USAGE_DECODER) != GRALLOC_USAGE_DECODER &&
		    (usage & (GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_HW_VIDEO_ENCODER)) ==
		    (GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_HW_VIDEO_ENCODER))
		{
			producers |= MALI_GRALLOC_PRODUCER_DPU;
		}

		if (usage & (GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_GPU_DATA_BUFFER))
		{
			producers |= MALI_GRALLOC_PRODUCER_GPU;
		}

		if (usage & GRALLOC_USAGE_HW_CAMERA_WRITE)
		{
			producers |= MALI_GRALLOC_PRODUCER_CAM;
		}

		/* Video decoder producer is signalled by a combination of usage flags
		 * (see definition of GRALLOC_USAGE_DECODER).
		 */
		if ((usage & GRALLOC_USAGE_DECODER) == GRALLOC_USAGE_DECODER)
		{
			producers |= MALI_GRALLOC_PRODUCER_VPU;
		}
	}

	return producers;
}

/*
 * Update buffer dimensions for producer/consumer constraints. This process is
 * not valid with CPU producer/consumer since the new resolution cannot be
 * communicated to generic clients through the public APIs. Adjustments are
 * likely to be related to AFBC.
 *
 * @param alloc_format   [in]    Format (inc. modifiers) to be allocated.
 * @param usage          [in]    Buffer usage.
 * @param width          [inout] Buffer width (in pixels).
 * @param height         [inout] Buffer height (in pixels).
 *
 * @return none.
 */
void mali_gralloc_adjust_dimensions(const uint64_t alloc_format,
                                    const uint64_t usage,
                                    int* const width,
                                    int* const height)
{
	/* Determine producers and consumers. */
	const uint16_t producers = get_producers(usage);
	// const uint16_t consumers = get_consumers(usage);

	/*-------------------------------------------------------*/

	if (producers & MALI_GRALLOC_PRODUCER_GPU)
	{
		/* Pad all AFBC allocations to multiple of GPU tile size. */
		if (alloc_format & MALI_GRALLOC_INTFMT_AFBC_BASIC)
		{
			*width = GRALLOC_ALIGN(*width, 16);
			*height = GRALLOC_ALIGN(*height, 16);
		}
	}

	MALI_GRALLOC_LOGV("%s: alloc_format=0x%" PRIx64 " usage=0x%" PRIx64
	      " alloc_width=%u, alloc_height=%u",
	      __FUNCTION__, alloc_format, usage, *width, *height);
}

/*
 * Determines whether a base format is subsampled YUV, where each
 * chroma channel has fewer samples than the luma channel. The
 * sub-sampling is always a power of 2.
 *
 * @param base_format   [in]    Base format (internal).
 *
 * @return 1, where format is subsampled YUV;
 *         0, otherwise
 */
bool is_subsampled_yuv(const uint32_t base_format)
{
	unsigned long i;

	for (i = 0; i < num_formats; i++)
	{
		if (formats[i].id == (base_format & MALI_GRALLOC_INTFMT_FMT_MASK))
		{
			if (formats[i].is_yuv == true &&
			    (formats[i].hsub > 1 || formats[i].vsub > 1))
			{
				return true;
			}
		}
	}
	return false;
}

bool is_base_format_used_by_rk_video(const uint32_t base_format)
{
	if ( MALI_GRALLOC_FORMAT_INTERNAL_NV12 == base_format
		|| MALI_GRALLOC_FORMAT_INTERNAL_NV16 == base_format
		|| MALI_GRALLOC_FORMAT_INTERNAL_YUV420_8BIT_I == base_format
		|| MALI_GRALLOC_FORMAT_INTERNAL_YUV420_10BIT_I == base_format
		|| MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT == base_format
		|| MALI_GRALLOC_FORMAT_INTERNAL_Y210 == base_format
		|| MALI_GRALLOC_FORMAT_INTERNAL_NV15 == base_format
		|| MALI_GRALLOC_FORMAT_INTERNAL_NV24 == base_format
		|| MALI_GRALLOC_FORMAT_INTERNAL_YV12 == base_format	// HAL_PIXEL_FORMAT_YV12
		|| MALI_GRALLOC_FORMAT_INTERNAL_NV21 == base_format	// HAL_PIXEL_FORMAT_YCrCb_420_SP
		)
	{
		return true;
	}
	else
	{
		return false;
	}
}

/*
 * 返回 "'base_format' 是否是 RK IPs 支持其 AFBC 形态的 YUV 格式".
 */
bool is_yuv_format_supported_by_rk_ip_in_afbc(const uint32_t base_format)
{
	if ( MALI_GRALLOC_FORMAT_INTERNAL_YUV420_8BIT_I == base_format
		|| MALI_GRALLOC_FORMAT_INTERNAL_YUV420_10BIT_I == base_format
		|| MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT == base_format
		|| MALI_GRALLOC_FORMAT_INTERNAL_Y210 == base_format )
	{
		return true;
	}
	else
	{
		return false;
	}
}

/*---------------------------------------------------------------------------*/

static bool is_rk_ext_hal_format(const uint64_t hal_format)
{
	if ( HAL_PIXEL_FORMAT_YCrCb_NV12 == hal_format
		|| HAL_PIXEL_FORMAT_YCrCb_NV12_10 == hal_format )
	{
		return true;
	}
	else
	{
		return false;
	}
}

/*
 * Select pixel format (base + modifier) for allocation with RK manner.
 *
 * @param req_format       [in]   Format (base + optional modifiers) requested by client.
 * @param usage            [in]   Buffer usage.
 * @param buffer_size      [in]   Buffer resolution (w x h, in pixels).
 *
 * @return alloc_format, format to be used in allocation;
 *         MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED, where no suitable
 *         format could be found.
 */
static uint64_t rk_gralloc_select_format(const uint64_t req_format,
					 const uint64_t usage,
					 const int buffer_size) // Buffer resolution (w x h, in pixels).
{
	uint64_t internal_format = req_format;
	MALI_IGNORE(buffer_size);

	if ( HAL_PIXEL_FORMAT_RGBA_FP16 == req_format )
	{
		I("HAL_PIXEL_FORMAT_RGBA_FP16 is not supported");
		return MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED;
	}

	/*-------------------------------------------------------*/
	/* rk 定义的 从 'req_format' 到 'internal_format' 的映射. */

	if ( HAL_PIXEL_FORMAT_YCrCb_NV12 == req_format )
	{
		D("to use 'MALI_GRALLOC_FORMAT_INTERNAL_NV12' as internal_format for req_format of 'HAL_PIXEL_FORMAT_YCrCb_NV12'");
		internal_format = MALI_GRALLOC_FORMAT_INTERNAL_NV12;
	}
	else if ( HAL_PIXEL_FORMAT_YCbCr_422_SP == req_format )
	{
		D("to use MALI_GRALLOC_FORMAT_INTERNAL_NV16 as internal_format for HAL_PIXEL_FORMAT_YCbCr_422_SP.");
		internal_format = MALI_GRALLOC_FORMAT_INTERNAL_NV16;
	}
	else if ( HAL_PIXEL_FORMAT_YCrCb_NV12_10 == req_format )
	{
		D("to use 'MALI_GRALLOC_FORMAT_INTERNAL_NV15' as internal_format for req_format of 'HAL_PIXEL_FORMAT_YCrCb_NV12_10'");
		internal_format = MALI_GRALLOC_FORMAT_INTERNAL_NV15;
	}
	else if ( HAL_PIXEL_FORMAT_YCBCR_444_888 == req_format )
	{
		D("to use 'MALI_GRALLOC_FORMAT_INTERNAL_NV24' as internal_format for req_format of 'HAL_PIXEL_FORMAT_YCBCR_444_888'");
		internal_format = MALI_GRALLOC_FORMAT_INTERNAL_NV24;
	}
        else if ( req_format == HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED )
	{
		if ( GRALLOC_USAGE_HW_VIDEO_ENCODER == (usage & GRALLOC_USAGE_HW_VIDEO_ENCODER)
			|| GRALLOC_USAGE_HW_CAMERA_WRITE == (usage & GRALLOC_USAGE_HW_CAMERA_WRITE) )
		{
			D("to select NV12 for HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED for usage : 0x%" PRIx64 ".", usage);
			internal_format = MALI_GRALLOC_FORMAT_INTERNAL_NV12;
		}
		else
		{
			D("to select RGBX_8888 for HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED for usage : 0x%" PRIx64 ".", usage);
			internal_format = HAL_PIXEL_FORMAT_RGBX_8888;
		}
	}
	else if ( req_format == HAL_PIXEL_FORMAT_YCbCr_420_888 )
	{
		D("to use NV12 for  %" PRIu64, req_format);
		internal_format = MALI_GRALLOC_FORMAT_INTERNAL_NV12;
	}
	else if ( HAL_PIXEL_FORMAT_YUV420_8BIT_I == req_format )
	{
		D("to use MALI_GRALLOC_FORMAT_INTERNAL_YUV420_8BIT_I as internal_format for HAL_PIXEL_FORMAT_YUV420_8BIT_I.");
		internal_format = MALI_GRALLOC_FORMAT_INTERNAL_YUV420_8BIT_I;
	}
	else if ( HAL_PIXEL_FORMAT_YUV420_10BIT_I == req_format )
	{
		D("to use MALI_GRALLOC_FORMAT_INTERNAL_YUV420_10BIT_I as internal_format for HAL_PIXEL_FORMAT_YUV420_10BIT_I.");
		internal_format = MALI_GRALLOC_FORMAT_INTERNAL_YUV420_10BIT_I;
	}
	else if ( HAL_PIXEL_FORMAT_YCbCr_422_I == req_format )
	{
		D("to use MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT as internal_format for HAL_PIXEL_FORMAT_YCbCr_422_I.");
		internal_format = MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT;
	}
	else if ( HAL_PIXEL_FORMAT_Y210 == req_format )
	{
		D("to use MALI_GRALLOC_FORMAT_INTERNAL_Y210 as internal_format for HAL_PIXEL_FORMAT_Y210.");
		internal_format = MALI_GRALLOC_FORMAT_INTERNAL_Y210;
	}
	else if ( req_format == HAL_PIXEL_FORMAT_YCRCB_420_SP)
	{
		D("to use NV21 for  %" PRIu64, req_format);
		internal_format = MALI_GRALLOC_FORMAT_INTERNAL_NV21;
	}

	/*-------------------------------------------------------*/

	/* 若 'req_format' "不是" rk_ext_hal_format 且 rk "未" 定义 map 方式, 则... */
	if ( !(is_rk_ext_hal_format(req_format) )
		&& internal_format == req_format )
	{
		/* 用 ARM 定义的规则, 从 'req_format' 得到 'internal_format'. */
		internal_format = get_internal_format(req_format,
						      true);	// 'map_to_internal'
		if ( MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED == internal_format )
		{
			internal_format = req_format;
		}
	}

	/*-------------------------------------------------------*/
	/* 处理可能的 AFBC 配置. */

	/* 若当前 buffer "是" 用于 fb_target_layer, 则... */
	if ( GRALLOC_USAGE_HW_FB == (usage & GRALLOC_USAGE_HW_FB) )
	{
		// 221023: cz: 认为不会对 fb_target_layer 使用 AFBC 格式, 因为 GPU 不支持 AFBC.
	}
	/* 否则, 即 当前 buffer 用于 sf_client_layer 等其他用途, 则... */
	else
	{
		const uint32_t base_format = internal_format;

		/* 若 base_format 是 RK IPs 支持其 AFBC 形态的 YUV 格式, 则... */
		if ( is_yuv_format_supported_by_rk_ip_in_afbc(base_format) )
		{
			/* 强制将 'internal_format' 设置为对应的 AFBC 格式. */
			internal_format = internal_format | MALI_GRALLOC_INTFMT_AFBC_BASIC;
			D("use_afbc_layer: force to set 'internal_format' to 0x%" PRIx64 " for usage '0x%" PRIx64,
					internal_format, usage);
		}
	}

	/*-------------------------------------------------------*/

	return internal_format;
}

/*
 * Select pixel format (base + modifier) for allocation.
 *
 * @param req_format       [in]   Format (base + optional modifiers) requested by client.
 * @param type             [in]   Format type (public usage or internal).
 * @param usage            [in]   Buffer usage.
 * @param buffer_size      [in]   Buffer resolution (w x h, in pixels).
 *
 * @return alloc_format, format to be used in allocation;
 *         MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED, where no suitable
 *         format could be found.
 */
uint64_t mali_gralloc_select_format(const uint64_t req_format,
                                    const mali_gralloc_format_type type,
                                    const uint64_t usage,
                                    const int buffer_size)
{
	GRALLOC_UNUSED(type);
	uint64_t alloc_format;

	alloc_format = rk_gralloc_select_format(req_format, usage, buffer_size);

	return alloc_format;
}


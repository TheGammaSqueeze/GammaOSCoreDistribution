/*
 * Copyright (C) 2016-2022 ARM Limited. All rights reserved.
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
#include "../custom_log.h"

#include <inttypes.h>
#include <assert.h>
#include <atomic>
#include <algorithm>

#include <hardware/hardware.h>
#include <hardware/gralloc1.h>

#include "buffer_allocation.h"
#include "allocator/allocator.h"
#include "allocator/shared_memory/shared_memory.h"
#include "buffer.h"
#include "buffer_descriptor.h"
#include "log.h"
#include "format_info.h"
#include "format_selection.h"
#include "usages.h"
#include "helper_functions.h"


#define AFBC_PIXELS_PER_BLOCK 256
#define AFBC_HEADER_BUFFER_BYTES_PER_BLOCKENTRY 16

/*
 * Get a global unique ID
 */
static uint64_t getUniqueId()
{
	static std::atomic<uint32_t> counter(0);
	uint64_t id = static_cast<uint64_t>(getpid()) << 32;
	return id | counter++;
}

void afbc_buffer_align(const bool is_tiled, int *size)
{
	const uint16_t AFBC_BODY_BUFFER_BYTE_ALIGNMENT = 1024;

	int buffer_byte_alignment = AFBC_BODY_BUFFER_BYTE_ALIGNMENT;

	if (is_tiled)
	{
		buffer_byte_alignment = 4 * AFBC_BODY_BUFFER_BYTE_ALIGNMENT;
	}

	*size = GRALLOC_ALIGN(*size, buffer_byte_alignment);
}

static uint32_t afrc_plane_alignment_requirement(uint32_t coding_unit_size)
{
	switch (coding_unit_size)
	{
	case 16:
		return 1024;
	case 24:
		return 512;
	case 32:
		return 2048;
	default:
		MALI_GRALLOC_LOGE("internal error: invalid coding unit size (%" PRIu32 ")", coding_unit_size);
		return 0;
	}
}

/*
 * Obtain AFBC superblock dimensions from type.
 */
static rect_t get_afbc_sb_size(AllocBaseType alloc_base_type)
{
	const uint16_t AFBC_BASIC_BLOCK_WIDTH = 16;
	const uint16_t AFBC_BASIC_BLOCK_HEIGHT = 16;
	const uint16_t AFBC_WIDE_BLOCK_WIDTH = 32;
	const uint16_t AFBC_WIDE_BLOCK_HEIGHT = 8;
	const uint16_t AFBC_EXTRAWIDE_BLOCK_WIDTH = 64;
	const uint16_t AFBC_EXTRAWIDE_BLOCK_HEIGHT = 4;

	rect_t sb = { 0, 0 };

	switch (alloc_base_type)
	{
	case AllocBaseType::AFBC:
		sb.width = AFBC_BASIC_BLOCK_WIDTH;
		sb.height = AFBC_BASIC_BLOCK_HEIGHT;
		break;
	case AllocBaseType::AFBC_WIDEBLK:
		sb.width = AFBC_WIDE_BLOCK_WIDTH;
		sb.height = AFBC_WIDE_BLOCK_HEIGHT;
		break;
	case AllocBaseType::AFBC_EXTRAWIDEBLK:
		sb.width = AFBC_EXTRAWIDE_BLOCK_WIDTH;
		sb.height = AFBC_EXTRAWIDE_BLOCK_HEIGHT;
		break;
	default:
		break;
	}
	return sb;
}

/*
 * Obtain AFBC superblock dimensions for specific plane.
 *
 * See alloc_type_t for more information.
 */
static rect_t get_afbc_sb_size(alloc_type_t alloc_type, const uint8_t plane)
{
	if (plane > 0 && alloc_type.is_afbc() && alloc_type.is_multi_plane)
	{
		return get_afbc_sb_size(AllocBaseType::AFBC_EXTRAWIDEBLK);
	}
	else
	{
		return get_afbc_sb_size(alloc_type.primary_type);
	}
}

static void adjust_rk_video_buffer_size(buffer_descriptor_t* const bufDescriptor, const format_info_t* format)
{
	const uint32_t pixel_stride = bufDescriptor->plane_info[0].byte_stride * 8 / (format->bpp[0]);
	const uint32_t byte_stride = bufDescriptor->plane_info[0].byte_stride;
	const uint32_t alloc_height = (bufDescriptor->plane_info)[0].alloc_height;
	const uint32_t base_format = bufDescriptor->alloc_format.get_base();
	size_t size_needed_by_rk_video = 0;

	switch ( base_format )
	{
		case MALI_GRALLOC_FORMAT_INTERNAL_NV12:
		{
			/*
			 * .KP : from CSY : video_decoder 需要的 NV12 buffer 中除了 YUV 数据还有其他 metadata, 要更多的空间.
			 *		    2 * w * h 一定够.
			 */
			size_needed_by_rk_video = 2 * pixel_stride * alloc_height;
			break;
		}
		case MALI_GRALLOC_FORMAT_INTERNAL_NV16:
		{
			size_needed_by_rk_video = 2.5 * pixel_stride * alloc_height; // 根据 陈锦森的 要求
			break;
		}
		case MALI_GRALLOC_FORMAT_INTERNAL_NV15:
		{
			size_needed_by_rk_video = 2 * byte_stride * alloc_height;
			break;
		}
		case MALI_GRALLOC_FORMAT_INTERNAL_NV24:
		{
			size_needed_by_rk_video = 3 * byte_stride * alloc_height;
			break;
		}
		default:
			return;
	}

	if ( size_needed_by_rk_video > bufDescriptor->size )
	{
		D("to enlarge size of rk_video_buffer with base_format(0x%x) from %zd to %zd",
		  base_format,
		  bufDescriptor->size,
		  size_needed_by_rk_video);
		bufDescriptor->size = size_needed_by_rk_video;
	}
}

/*---------------------------------------------------------------------------*/

std::optional<alloc_type_t> get_alloc_type(const internal_format_t format, const uint64_t usage)
{
	const format_info_t *format_info = format.get_base_info();
	if (format_info == nullptr)
	{
		return std::nullopt;
	}

	alloc_type_t alloc_type{};
	alloc_type.primary_type = AllocBaseType::UNCOMPRESSED;
	alloc_type.is_multi_plane = format_info->npln > 1;
	alloc_type.is_tiled = false;
	alloc_type.is_padded = false;
	alloc_type.is_frontbuffer_safe = false;

	/* Determine AFBC type for this format. This is used to decide alignment.
	   Split block does not affect alignment, and therefore doesn't affect the allocation type. */
	if (format.is_afbc())
	{
		/* YUV transform shall not be enabled for a YUV format */
		if (format_info->is_yuv && format.get_afbc_yuv_transform())
		{
			MALI_GRALLOC_LOG(WARNING) << std::showbase
			                          << "YUV Transform is incorrectly enabled for format = " << std::hex
			                          << format_info->id << ". Extended internal format = " << format;
		}

		/* Determine primary AFBC (superblock) type. */
		alloc_type.primary_type = AllocBaseType::AFBC;
		if (format.get_afbc_32x8())
		{
			alloc_type.primary_type = AllocBaseType::AFBC_WIDEBLK;
		}
		else if (format.get_afbc_64x4())
		{
			alloc_type.primary_type = AllocBaseType::AFBC_EXTRAWIDEBLK;
		}

		if (format.get_afbc_tiled_headers())
		{
			alloc_type.is_tiled = true;

			if (format_info->npln > 1 && !format.get_afbc_64x4())
			{
				MALI_GRALLOC_LOGW("Extra-wide AFBC must be signalled for multi-plane formats. "
				                  "Falling back to single plane AFBC.");
				alloc_type.is_multi_plane = false;
			}

			if (format.get_afbc_double_body())
			{
				alloc_type.is_frontbuffer_safe = true;
			}
		}
		else
		{
			if (format_info->npln > 1)
			{
				MALI_GRALLOC_LOGW("Multi-plane AFBC is not supported without tiling. "
				                  "Falling back to single plane AFBC.");
			}
			alloc_type.is_multi_plane = false;
		}

		if (format.get_afbc_64x4() && !alloc_type.is_tiled)
		{
			/* Headers must be tiled for extra-wide. */
			MALI_GRALLOC_LOGE("ERROR: Invalid to specify extra-wide block without tiled headers.");
			return std::nullopt;
		}

		if (alloc_type.is_frontbuffer_safe && (format.get_afbc_32x8() || format.get_afbc_64x4()))
		{
			MALI_GRALLOC_LOGE("ERROR: Front-buffer safe not supported with wide/extra-wide block.");
		}

		if (format_info->npln == 1 && format.get_afbc_32x8() && format.get_afbc_64x4())
		{
			/* "Wide + Extra-wide" implicitly means "multi-plane". */
			MALI_GRALLOC_LOGE("ERROR: Invalid to specify multiplane AFBC with single plane format.");
			return std::nullopt;
		}

		if (usage & MALI_GRALLOC_USAGE_AFBC_PADDING)
		{
			alloc_type.is_padded = true;
		}
	}
	else if (format.is_afrc())
	{
		alloc_type.primary_type = AllocBaseType::AFRC;

		if (format.get_afrc_rot_layout())
		{
			alloc_type.afrc.paging_tile_width = 8;
			alloc_type.afrc.paging_tile_height = 8;
		}
		else
		{
			alloc_type.afrc.paging_tile_width = 16;
			alloc_type.afrc.paging_tile_height = 4;
		}

		alloc_type.afrc.rgba_luma_coding_unit_bytes = to_bytes(format.get_afrc_rgba_coding_size());
		alloc_type.afrc.rgba_luma_plane_alignment =
		    afrc_plane_alignment_requirement(alloc_type.afrc.rgba_luma_coding_unit_bytes);
		if (alloc_type.afrc.rgba_luma_plane_alignment == 0)
		{
			return std::nullopt;
		}

		alloc_type.afrc.chroma_coding_unit_bytes = to_bytes(format.get_afrc_chroma_coding_size());
		alloc_type.afrc.chroma_plane_alignment =
		    afrc_plane_alignment_requirement(alloc_type.afrc.chroma_coding_unit_bytes);
		if (alloc_type.afrc.chroma_plane_alignment == 0)
		{
			return std::nullopt;
		}

		for (auto plane = 0; plane < format_info->npln; ++plane)
		{
			switch (format_info->ncmp[plane])
			{
			case 1:
				alloc_type.afrc.clump_width[plane] = alloc_type.afrc.paging_tile_width;
				alloc_type.afrc.clump_height[plane] = alloc_type.afrc.paging_tile_height;
				break;
			case 2:
				alloc_type.afrc.clump_width[plane] = 8;
				alloc_type.afrc.clump_height[plane] = 4;
				break;
			case 3:
			case 4:
				alloc_type.afrc.clump_width[plane] = 4;
				alloc_type.afrc.clump_height[plane] = 4;
				break;
			default:
				MALI_GRALLOC_LOGE("internal error: invalid number of components in plane %d (%d)",
				                  static_cast<int>(plane), static_cast<int>(format_info->ncmp[plane]));
				return std::nullopt;
			}
		}
	}
	else if (format.is_block_linear())
	{
		alloc_type.primary_type = AllocBaseType::BLOCK_LINEAR;
	}
	return alloc_type;
}

bool is_subsampled_yuv(const internal_format_t format);

/*
 * Initialise AFBC header based on superblock layout.
 * Width and height should already be AFBC aligned.
 */
void init_afbc(uint8_t *buf, const internal_format_t alloc_format,
               const bool is_multi_plane,
               const int w, const int h)
{
	const bool is_tiled = alloc_format.get_afbc_tiled_headers();
	const uint32_t n_headers = (w * h) / AFBC_PIXELS_PER_BLOCK;
	int body_offset = n_headers * AFBC_HEADER_BUFFER_BYTES_PER_BLOCKENTRY;

	afbc_buffer_align(is_tiled, &body_offset);

	/*
	 * Declare the AFBC header initialisation values for each superblock layout.
	 * Tiled headers (AFBC 1.2) can be initialised to zero for non-subsampled formats
	 * (SB layouts: 0, 3, 4, 7).
	 */
	uint32_t headers[][4] = {
		{ (uint32_t)body_offset, 0x1, 0x10000, 0x0 }, /* Layouts 0, 3, 4, 7 */
		{ ((uint32_t)body_offset + (1 << 28)), 0x80200040, 0x1004000, 0x20080 } /* Layouts 1, 5 */
	};
	if (is_tiled)
	{
		/* Zero out body_offset for non-subsampled formats. */
		memset(headers[0], 0, sizeof(uint32_t) * 4);
	}

	/* Map base format to AFBC header layout */
	const uint32_t base_format = alloc_format.get_base();

	/* Sub-sampled formats use layouts 1 and 5 which is index 1 in the headers array.
	 * 1 = 4:2:0 16x16, 5 = 4:2:0 32x8.
	 *
	 * Non-subsampled use layouts 0, 3, 4 and 7, which is index 0.
	 * 0 = 16x16, 3 = 32x8 + split, 4 = 32x8, 7 = 64x4.
	 *
	 * When using separated planes for YUV formats, the header layout is the non-subsampled one
	 * as there is a header per-plane and there is no sub-sampling within the plane.
	 * Separated plane only supports 32x8 or 64x4 for the luma plane, so the first plane must be 4 or 7.
	 * Seperated plane only supports 64x4 for subsequent planes, so these must be header layout 7.
	 */
	const uint32_t layout = is_subsampled_yuv(alloc_format) && !is_multi_plane ? 1 : 0;

	MALI_GRALLOC_LOGV("Writing AFBC header layout %d for format %" PRIx32, layout, base_format);

	for (uint32_t i = 0; i < n_headers; i++)
	{
		memcpy(buf, headers[layout], sizeof(headers[layout]));
		buf += sizeof(headers[layout]);
	}
}

static int max(int a, int b)
{
	return a > b ? a : b;
}

static int max(int a, int b, int c)
{
	return c > max(a, b) ? c : max(a, b);
}

static int max(int a, int b, int c, int d)
{
	return d > max(a, b, c) ? d : max(a, b, c);
}

/*
 * Obtain plane allocation dimensions (in pixels).
 *
 * NOTE: pixel stride, where defined for format, is
 * incorporated into allocation dimensions.
 */
static void get_pixel_w_h(uint32_t *const width, uint32_t *const height, const format_info_t format,
                          const alloc_type_t alloc_type, const uint8_t plane, bool has_cpu_usage)
{
	const rect_t sb = get_afbc_sb_size(alloc_type, plane);

	/*
	 * Round-up plane dimensions, to multiple of:
	 * - Samples for all channels (sub-sampled formats)
	 * - Memory bytes/words (some packed formats)
	 */
	*width = GRALLOC_ALIGN(*width, format.align_w);
	*height = GRALLOC_ALIGN(*height, format.align_h);

	/*
	 * Sub-sample (sub-sampled) planes.
	 */
	if (plane > 0)
	{
		*width /= format.hsub;
		*height /= format.vsub;
	}

	/*
	 * Pixel alignment (width),
	 * where format stride is stated in pixels.
	 */
	int pixel_align_w = 1, pixel_align_h = 1;
	if (has_cpu_usage)
	{
		pixel_align_w = format.align_w_cpu;
	}
	else if (alloc_type.is_afbc())
	{
#define HEADER_STRIDE_ALIGN_IN_SUPER_BLOCKS (0)
		uint32_t num_sb_align = 0;
		if (alloc_type.is_padded && !format.is_yuv)
		{
			/* Align to 4 superblocks in width --> 64-byte,
			 * assuming 16-byte header per superblock.
			 */
			num_sb_align = 4;
		}
		pixel_align_w = max(HEADER_STRIDE_ALIGN_IN_SUPER_BLOCKS, num_sb_align) * sb.width;

		/*
		 * Determine AFBC tile size when allocating tiled headers.
		 */
		rect_t afbc_tile = sb;
		if (alloc_type.is_tiled)
		{
			afbc_tile.width = format.bpp_afbc[plane] > 32 ? 4 * afbc_tile.width : 8 * afbc_tile.width;
			afbc_tile.height = format.bpp_afbc[plane] > 32 ? 4 * afbc_tile.height : 8 * afbc_tile.height;
		}

		MALI_GRALLOC_LOGV("Plane[%hhu]: [SUB-SAMPLE] w:%d, h:%d\n", plane, *width, *height);
		MALI_GRALLOC_LOGV("Plane[%hhu]: [PIXEL_ALIGN] w:%d\n", plane, pixel_align_w);
		MALI_GRALLOC_LOGV("Plane[%hhu]: [LINEAR_TILE] w:%" PRIu16 "\n", plane, format.tile_size);
		MALI_GRALLOC_LOGV("Plane[%hhu]: [AFBC_TILE] w:%" PRIu16 ", h:%" PRIu16 "\n", plane, afbc_tile.width,
		                  afbc_tile.height);

		pixel_align_w = max(pixel_align_w, afbc_tile.width);
		pixel_align_h = max(pixel_align_h, afbc_tile.height);

		if (AllocBaseType::AFBC_WIDEBLK == alloc_type.primary_type && !alloc_type.is_tiled)
		{
			/*
			 * Special case for wide block (32x8) AFBC with linear (non-tiled)
			 * headers: hardware reads and writes 32x16 blocks so we need to
			 * pad the body buffer accordingly.
			 *
			 * Note that this branch will not be taken for multi-plane AFBC
			 * since that requires tiled headers.
			 */
			pixel_align_h = max(pixel_align_h, 16);
		}
	}
	else if (alloc_type.is_afrc())
	{
		pixel_align_w = alloc_type.afrc.paging_tile_width * alloc_type.afrc.clump_width[plane];
		pixel_align_h = alloc_type.afrc.paging_tile_height * alloc_type.afrc.clump_height[plane];
	}
	else if (alloc_type.is_block_linear())
	{
		pixel_align_w = pixel_align_h = 16;
	}
	*width = GRALLOC_ALIGN(*width, max(1, pixel_align_w, format.tile_size));
	*height = GRALLOC_ALIGN(*height, max(1, pixel_align_h, format.tile_size));
}

static uint32_t gcd(uint32_t a, uint32_t b)
{
	uint32_t r, t;

	if (a == b)
	{
		return a;
	}
	else if (a < b)
	{
		t = a;
		a = b;
		b = t;
	}

	while (b != 0)
	{
		r = a % b;
		a = b;
		b = r;
	}

	return a;
}

uint32_t lcm(uint32_t a, uint32_t b)
{
	if (a != 0 && b != 0)
	{
		return (a * b) / gcd(a, b);
	}

	return max(a, b);
}

/*
 * YV12 stride has additional complexity since chroma stride
 * must conform to the following:
 *
 * c_stride = ALIGN(stride/2, 16)
 *
 * Since the stride alignment must satisfy both CPU and HW
 * constraints, the luma stride must be doubled.
 */
static void update_yv12_stride(int8_t plane, uint32_t luma_stride, uint32_t stride_align, uint32_t *byte_stride)
{
	if (plane == 0)
	{
		/*
		 * Ensure luma stride is aligned to "2*lcm(hw_align, cpu_align)" so
		 * that chroma stride can satisfy both CPU and HW alignment
		 * constraints when only half luma stride (as mandated for format).
		 */
		*byte_stride = GRALLOC_ALIGN(luma_stride, 2 * stride_align);
	}
	else
	{
		/*
		 * Derive chroma stride from luma and verify it is:
		 * 1. Aligned to lcm(hw_align, cpu_align)
		 * 2. Multiple of 16px (16 bytes)
		 */
		*byte_stride = luma_stride / 2;
		assert(*byte_stride == GRALLOC_ALIGN(*byte_stride, stride_align));
		assert((*byte_stride & 15) == 0);
	}
}

/*
 * Calculate allocation size.
 *
 * Determine the width and height of each plane based on pixel alignment for
 * both uncompressed and AFBC allocations.
 *
 * @param width           [in]    Buffer width.
 * @param height          [in]    Buffer height.
 * @param alloc_type      [in]    Allocation type inc. whether tiled and/or multi-plane.
 * @param format          [in]    Pixel format.
 * @param usage           [in]    The usage flags requested.
 * @param pixel_stride    [out]   Calculated pixel stride.
 * @param is_stride_specified 
 *			  [in]	  待分配的 buffer 是否被具体指定了, 和 RK_GRALLOC_USAGE_SPECIFY_STRIDE 有关.
 * @param usage_flag_for_stride_alignmen
 *			  [in]	  若非 0, 表征 client 具体指定的待分配的 buffer pixel_stride 的对齐方式,
 *					value 可能是 如下 bit 中的某一个: 
 *						RK_GRALLOC_USAGE_STRIDE_ALIGN_16,
 *						RK_GRALLOC_USAGE_STRIDE_ALIGN_64,
 *						RK_GRALLOC_USAGE_STRIDE_ALIGN_128,
 *						RK_GRALLOC_USAGE_STRIDE_ALIGN_256_ODD_TIMES.
 * @param size            [out]   Total calculated buffer size including all planes.
 * @param plane_info      [out]   Array of calculated information for each plane. Includes
 *                                offset, byte stride and allocation width and height.
 */
static void calc_allocation_size(const int width,
                                 const int height,
                                 const alloc_type_t alloc_type,
                                 const format_info_t format,
								 const uint64_t usage,
				 const bool is_stride_specified,
				 const uint64_t usage_flag_for_stride_alignment,
                                 int * const pixel_stride,
                                 size_t * const size,
                                 plane_layout &plane_info)
{
	plane_info[0].offset = 0;

	bool has_cpu_usage = usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK);
	bool has_hw_usage = usage & ~(GRALLOC_USAGE_PRIVATE_MASK | GRALLOC_USAGE_SW_READ_MASK |
	                              GRALLOC_USAGE_SW_WRITE_MASK | GRALLOC_USAGE_FRONTBUFFER);

	*size = 0;
	for (uint8_t plane = 0; plane < format.npln; plane++)
	{
		plane_info[plane].alloc_width = width;
		plane_info[plane].alloc_height = height;
		get_pixel_w_h(&plane_info[plane].alloc_width, &plane_info[plane].alloc_height, format, alloc_type, plane,
		              has_cpu_usage);
		MALI_GRALLOC_LOGV("Aligned w=%d, h=%d (in pixels)", plane_info[plane].alloc_width,
		                  plane_info[plane].alloc_height);

		/*
		 * Calculate byte stride (per plane).
		 */
		if (alloc_type.is_afrc())
		{
			uint32_t coding_unit_bytes =
			    plane == 0 ? alloc_type.afrc.rgba_luma_coding_unit_bytes : alloc_type.afrc.chroma_coding_unit_bytes;

			uint32_t paging_tile_stride =
			    plane_info[plane].alloc_width / alloc_type.afrc.clump_width[plane] / alloc_type.afrc.paging_tile_width;
			const uint32_t coding_units_in_paging_tile = 64;
			const uint32_t paging_tile_byte_stride =
			    paging_tile_stride * coding_units_in_paging_tile * coding_unit_bytes;
			const uint32_t paging_tile_sample_height =
			    alloc_type.afrc.paging_tile_height * alloc_type.afrc.clump_height[plane];

			assert(paging_tile_byte_stride % paging_tile_sample_height == 0);
			plane_info[plane].byte_stride = paging_tile_byte_stride / paging_tile_sample_height;
		}
		else if (alloc_type.is_afbc())
		{
			assert((plane_info[plane].alloc_width * format.bpp_afbc[plane]) % 8 == 0);
			plane_info[plane].byte_stride = (plane_info[plane].alloc_width * format.bpp_afbc[plane]) / 8;
		}
		else if (alloc_type.is_block_linear())
		{
			assert((plane_info[plane].alloc_width * format.bpp[plane]) % 8 == 0);
			uint32_t sample_height = 16;
			uint32_t sample_width = 16;
			if (plane > 0)
			{
				sample_height /= format.vsub;
				sample_width /= format.hsub;
			}

			uint32_t bytes_per_block = sample_height * sample_width * format.bpp[plane] / 8;
			assert(bytes_per_block % sample_height == 0);
			uint32_t number_of_x_blocks = plane_info[0].alloc_width / 16;
			assert(number_of_x_blocks > 0);
			uint32_t block_stride = number_of_x_blocks * bytes_per_block;
			plane_info[plane].byte_stride = block_stride / sample_height;
		}
		else
		{
			assert((plane_info[plane].alloc_width * format.bpp[plane]) % 8 == 0);
			plane_info[plane].byte_stride = (plane_info[plane].alloc_width * format.bpp[plane]) / 8;

			/*
			 * Align byte stride (uncompressed allocations only).
			 *
			 * Find the lowest-common-multiple of:
			 * 1. hw_align: Minimum byte stride alignment for HW IP (has_hw_usage == true)
			 * 2. cpu_align: Byte equivalent of 'align_w_cpu' (has_cpu_usage == true)
			 *
			 * NOTE: Pixel stride is defined as multiple of 'align_w_cpu'.
			 */
			uint16_t hw_align = 0;
			if (has_hw_usage)
			{
#if 0
				hw_align = format.is_yuv ? 128 : 64;
#else
				if ( is_base_format_used_by_rk_video(format.id) 
					&& ( is_stride_specified
						|| usage_flag_for_stride_alignment != 0 ) )
				{
					// 此时, 认为 client(rk_video_decoder 等) 通过 width 传入的 pixel_stride 是合理的,
					// 可满足 GPU 等其他组件对 stride 的要求.
					// 即 这里不需要再做更多的对齐处理.
					hw_align = 1;
				}
				else
				{
					hw_align = format.is_yuv ? 128 : 64;
				}
#endif
			}

			uint32_t cpu_align = 0;
			if (has_cpu_usage)
			{
				if ( MALI_GRALLOC_FORMAT_INTERNAL_BGR_888 != format.id )
				{
				assert((format.bpp[plane] * format.align_w_cpu) % 8 == 0);
				cpu_align = (format.bpp[plane] * format.align_w_cpu) / 8;
				}
				else
				{
					MALI_GRALLOC_LOGW("for BGR_888, force 'cpu_align' to 0");
				}
			}

			uint32_t stride_align = lcm(hw_align, cpu_align);
			if (stride_align)
			{
				plane_info[plane].byte_stride =
				    GRALLOC_ALIGN(plane_info[plane].byte_stride * format.tile_size, stride_align) / format.tile_size;
			}

			if ( usage_flag_for_stride_alignment != 0
				&& format.id == MALI_GRALLOC_FORMAT_INTERNAL_NV12 ) // 仅处理 NV12
			{
				uint32_t pixel_stride = 0;

				switch ( usage_flag_for_stride_alignment )
				{
				case RK_GRALLOC_USAGE_STRIDE_ALIGN_16:
					pixel_stride = GRALLOC_ALIGN(width, 16);
					break;

				case RK_GRALLOC_USAGE_STRIDE_ALIGN_64:
					pixel_stride = GRALLOC_ALIGN(width, 64);
					break;

				case RK_GRALLOC_USAGE_STRIDE_ALIGN_128:
					pixel_stride = GRALLOC_ALIGN(width, 128);
					break;

				case RK_GRALLOC_USAGE_STRIDE_ALIGN_256_ODD_TIMES:
					pixel_stride = ( (width + 255) & (~255) ) | (256);
					break;

				default:
					MY_E("unexpected 'usage_flag_for_stride_alignment': 0x%" PRIx64,
					  usage_flag_for_stride_alignment);
					break;
				}

				if ( 0 == plane )
				{
					plane_info[plane].byte_stride = pixel_stride * format.bpp[plane] / 8;
				}
				else // for sub-sample (sub-sampled) planes.
				{
					plane_info[plane].byte_stride = pixel_stride * format.bpp[plane] / 8 / format.hsub;
				}
			}

			/*
			 * Update YV12 stride with both CPU & HW usage due to constraint of chroma stride.
			 * Width is anyway aligned to 16px for luma and chroma (has_cpu_usage).
			 */
			if (format.id == MALI_GRALLOC_FORMAT_INTERNAL_YV12 && has_hw_usage && has_cpu_usage)
			{
				update_yv12_stride(plane, plane_info[0].byte_stride, stride_align, &plane_info[plane].byte_stride);
			}

			/* 按需对 nv12 以外的 rk_video 使用的格式调整 byte_stride. */
			if ( usage_flag_for_stride_alignment != 0
				&& MALI_GRALLOC_FORMAT_INTERNAL_NV12 != format.id )
			{
				uint32_t byte_stride = plane_info[plane].byte_stride;

				switch ( usage_flag_for_stride_alignment )
				{
				case RK_GRALLOC_USAGE_STRIDE_ALIGN_16:
					byte_stride = GRALLOC_ALIGN(byte_stride, 16);
					break;

				case RK_GRALLOC_USAGE_STRIDE_ALIGN_64:
					byte_stride = GRALLOC_ALIGN(byte_stride, 64);
					break;

				case RK_GRALLOC_USAGE_STRIDE_ALIGN_128:
					byte_stride = GRALLOC_ALIGN(byte_stride, 128);
					break;

				case RK_GRALLOC_USAGE_STRIDE_ALIGN_256_ODD_TIMES:
					byte_stride = ( (byte_stride + 255) & (~255) ) | (256);
					break;

				default:
					MY_E("unexpected 'usage_flag_for_stride_alignment': 0x%" PRIx64,
					  usage_flag_for_stride_alignment);
					break;
				}

				plane_info[plane].byte_stride = byte_stride;
			}

			if ( MALI_GRALLOC_FORMAT_INTERNAL_NV30 == format.id )
			{
				uint32_t byte_stride = plane_info[plane].byte_stride;

				byte_stride = GRALLOC_ALIGN(byte_stride, 64);

				plane_info[plane].byte_stride = byte_stride;
			}
		}
		MALI_GRALLOC_LOGV("Byte stride: %d", plane_info[plane].byte_stride);

		/*
		 * Pixel stride (CPU usage only).
		 * Not used in size calculation but exposed to client.
		 */
		if (plane == 0)
		{
			*pixel_stride = 0;
			const bool is_cpu_accessible =
			    !alloc_type.is_afbc() && !alloc_type.is_afrc() && !alloc_type.is_block_linear() && has_cpu_usage;
			if (is_cpu_accessible)
			{
				assert((plane_info[plane].byte_stride * 8) % format.bpp[plane] == 0);
				*pixel_stride = (plane_info[plane].byte_stride * 8) / format.bpp[plane];
			}

			MALI_GRALLOC_LOGV("Pixel stride: %d", *pixel_stride);
		}

		const uint32_t sb_num =
		    (plane_info[plane].alloc_width * plane_info[plane].alloc_height) / AFBC_PIXELS_PER_BLOCK;

		/*
		 * Calculate body size (per plane).
		 */
		int body_size = 0;
		if (alloc_type.is_afbc())
		{
			const rect_t sb = get_afbc_sb_size(alloc_type, plane);
			const int sb_bytes = GRALLOC_ALIGN((format.bpp_afbc[plane] * sb.width * sb.height) / 8, 128);
			body_size = sb_num * sb_bytes;

			/* When AFBC planes are stored in separate buffers and this is not the last plane,
			   also align the body buffer to make the subsequent header aligned. */
			if (format.npln > 1 && plane < 2)
			{
				afbc_buffer_align(alloc_type.is_tiled, &body_size);
			}

			if (alloc_type.is_frontbuffer_safe)
			{
				int back_buffer_size = body_size;
				afbc_buffer_align(alloc_type.is_tiled, &back_buffer_size);
				body_size += back_buffer_size;
			}
		}
		else if (alloc_type.is_afrc())
		{
			uint32_t alignment =
			    plane == 0 ? alloc_type.afrc.rgba_luma_plane_alignment : alloc_type.afrc.chroma_plane_alignment;
			*size = GRALLOC_ALIGN(*size, alignment);

			uint32_t coding_unit_bytes =
			    plane == 0 ? alloc_type.afrc.rgba_luma_coding_unit_bytes : alloc_type.afrc.chroma_coding_unit_bytes;
			uint32_t s_coding_units = plane_info[plane].alloc_width / alloc_type.afrc.clump_width[plane];
			uint32_t t_coding_units = plane_info[plane].alloc_height / alloc_type.afrc.clump_height[plane];
			body_size = s_coding_units * t_coding_units * coding_unit_bytes;
		}
		else if (alloc_type.is_block_linear())
		{
			uint32_t block_height = 16;
			if (plane > 0)
			{
				block_height /= format.vsub;
			}

			uint32_t block_size = plane_info[plane].byte_stride * block_height;
			uint32_t number_of_blocks_y = plane_info[0].alloc_height / 16;
			body_size = block_size * number_of_blocks_y;
		}
		else
		{
			body_size = plane_info[plane].byte_stride * plane_info[plane].alloc_height;
		}
		MALI_GRALLOC_LOGV("Body size: %d", body_size);

		/*
		 * Calculate header size (per plane).
		 */
		int header_size = 0;
		if (alloc_type.is_afbc())
		{
			/* As this is AFBC, calculate header size for this plane.
			 * Always align the header, which will make the body buffer aligned.
			 */
			header_size = sb_num * AFBC_HEADER_BUFFER_BYTES_PER_BLOCKENTRY;
			afbc_buffer_align(alloc_type.is_tiled, &header_size);
		}
		MALI_GRALLOC_LOGV("AFBC Header size: %d", header_size);

		/*
		 * Set offset for separate chroma planes.
		 */
		if (plane > 0)
		{
			plane_info[plane].offset = *size;
		}

		/*
		 * Set overall size.
		 * Size must be updated after offset.
		 */
		*size += body_size + header_size;
		MALI_GRALLOC_LOGV("size=%zu", *size);
	}
}

/*
 * Validate selected format against requested.
 * Return true if valid, false otherwise.
 */
static bool validate_format(const format_info_t *const format, const alloc_type_t alloc_type,
                            const buffer_descriptor_t *const bufDescriptor)
{
	if (alloc_type.is_afbc())
	{
		/*
		 * Validate format is supported by AFBC specification and gralloc.
		 */
		if (format->afbc == false)
		{
			MALI_GRALLOC_LOGE("ERROR: AFBC selected but not supported for base format: 0x%" PRIx32, format->id);
			return false;
		}

		/*
		 * Enforce consistency between number of format planes and
		 * request for single/multi-plane AFBC.
		 */
		if (((format->npln == 1 && alloc_type.is_multi_plane) || (format->npln > 1 && !alloc_type.is_multi_plane)))
		{
			MALI_GRALLOC_LOGE("ERROR: Format (%" PRIx32 ", num planes: %u) is incompatible with %s-plane AFBC request",
			                  format->id, format->npln, (alloc_type.is_multi_plane) ? "multi" : "single");
			return false;
		}
	}
	else if (alloc_type.is_afrc())
	{
		if (!format->afrc)
		{
			MALI_GRALLOC_LOGE("ERROR: AFRC format requested but not supported for base format: %" PRIx32, format->id);
			return false;
		}
	}
	else if (alloc_type.is_block_linear())
	{
		if (!format->block_linear)
		{
			MALI_GRALLOC_LOGE("ERROR: Block Linear format requested but not supported for base format: %" PRIx32,
			                  format->id);
			return false;
		}
	}
	else
	{
		if (format->linear == false)
		{
			MALI_GRALLOC_LOGE("ERROR: Uncompressed format requested but not supported for base format: %" PRIx32,
			                  format->id);
			return false;
		}
	}

	if (format->id == MALI_GRALLOC_FORMAT_INTERNAL_BLOB && bufDescriptor->height != 1)
	{
		MALI_GRALLOC_LOGE("ERROR: Height for format BLOB must be 1.");
		return false;
	}

	return true;
}

/*---------------------------------------------------------------------------*/

int mali_gralloc_derive_format_and_size(buffer_descriptor_t *descriptor)
{

	int alloc_width = descriptor->width;
	int alloc_height = descriptor->height;
	uint64_t usage = descriptor->producer_usage | descriptor->consumer_usage;
	buffer_descriptor_t* bufDescriptor = descriptor; // 'descriptor' 的别名.

	/*
	 * Select optimal internal pixel format based upon
	 * usage and requested format.
	 */
	descriptor->alloc_format = mali_gralloc_select_format(*descriptor,
							      usage,
							      bufDescriptor->width * bufDescriptor->height);
	if (descriptor->alloc_format.is_undefined())
	{
		MALI_GRALLOC_LOGE("ERROR: Unrecognized and/or unsupported format 0x%" PRIx64 " and usage 0x%" PRIx64,
		                  descriptor->hal_format, usage);
		return -EINVAL;
	}

	/* 获取 alloc_format 在 formats 中的 format_info_t 实例的指针. */
	const auto *format_info = descriptor->alloc_format.get_base_info();
	if (format_info == nullptr)
	{
		return -EINVAL;
	}
	MALI_GRALLOC_LOG(INFO) << "alloc_format: " << descriptor->alloc_format;

	/*
	 * Obtain allocation type (uncompressed, AFBC basic, etc...)
	 */
	auto alloc_type = get_alloc_type(descriptor->alloc_format, usage);
	if (!alloc_type.has_value())
	{
		return -EINVAL;
	}

	rk_board_platform_t platform = get_rk_board_platform();
	if (platform == RK356X || platform == RK3326 || platform == RK3588)
	{
		/*
		 * On android.hardware.nativehardware.cts
		 *		glCheckFramebuffer will return GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT
		 *		relative func on mali so is gles_surface_pixel_format_is_depth_renderable
		 * Also, deqp dEQP-VK.api.external.memory.android_hardware_buffer.image_formats *
		 *		on VkAndroidHardwareBufferFormatPropertiesANDROID
		 *		TCU_CHECK(formatProperties.format == format) failed
		 */

		if (format_info->id == MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_16 ||
			format_info->id == MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24 ||
			format_info->id == MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24_STENCIL_8 ||
			format_info->id == MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F ||
			format_info->id == MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F_STENCIL_8 ||
			format_info->id == MALI_GRALLOC_FORMAT_INTERNAL_STENCIL_8)
		{
			ALOGE("rk-debug RK356x/RK3326/RK3588 not support DEPTH & STENCIL format");
			return -EINVAL;
		}
	}

	if (!validate_format(format_info, *alloc_type, descriptor))
	{
		return -EINVAL;
	}

	/*
	 * Resolution of frame (allocation width and height) might require adjustment.
	 * This adjustment is only based upon specific usage and pixel format.
	 * If using AFBC, further adjustments to the allocation width and height will be made later
	 * based on AFBC alignment requirements and, for YUV, the plane properties.
	 */
	mali_gralloc_adjust_dimensions(descriptor->alloc_format, usage, &alloc_width, &alloc_height);

	{
		/* Obtain buffer size and plane information. */
		calc_allocation_size(alloc_width,
				     alloc_height,
				     *alloc_type,
				     *format_info,
				     usage,
				     usage & RK_GRALLOC_USAGE_SPECIFY_STRIDE, // 'is_stride_specified'
				     get_usage_flag_for_stride_alignment(usage),
				     &descriptor->pixel_stride,
				     &descriptor->size,
				     descriptor->plane_info);

		/*-------------------------------------------------------*/
		/* <为满足 implicit_requirement_for_rk_gralloc_alloc_interface_from_rk_video_decoder 的特殊处理.> */

		{
			const uint32_t base_format = bufDescriptor->alloc_format.get_base();
			const bool is_stride_specified = usage & RK_GRALLOC_USAGE_SPECIFY_STRIDE;

			/* 若 base_format "是" 被 rk_video 使用的格式, 且 rk client 要求指定 stride, 则 ... */
			if ( is_base_format_used_by_rk_video(base_format) && is_stride_specified )
			{
				uint8_t bpp = 0;	// bits_per_pixel of plane_0
				const int pixel_stride_asked_by_rk_video = bufDescriptor->width;
				int pixel_stride_calculated_by_arm_gralloc = 0;

				/* 若当前 是 AFBC 格式, 则 ... */
				if ( bufDescriptor->alloc_format.is_afbc() )
				{
					bpp = (format_info->bpp_afbc)[0];
				}
				else
				{
					bpp = (format_info->bpp)[0];
				}

				pixel_stride_calculated_by_arm_gralloc = bufDescriptor->plane_info[0].byte_stride * 8 / bpp;

				if ( pixel_stride_asked_by_rk_video != pixel_stride_calculated_by_arm_gralloc )
				{
					W("pixel_stride_asked_by_rk_video(%d) and pixel_stride_calculated_by_arm_gralloc(%d) are different.",
							  pixel_stride_asked_by_rk_video,
							  pixel_stride_calculated_by_arm_gralloc);

				}

				/* 对某些 格式的 rk_video_buffer 的 size 做必要调整. */
				adjust_rk_video_buffer_size(bufDescriptor, format_info);
			}
			else if ( is_base_format_used_by_rk_video(base_format) && is_stride_alignment_specified(usage) )
			{
				adjust_rk_video_buffer_size(bufDescriptor, format_info);
			}
		}
		/*-------------------------------------------------------*/

		/*
		 * Each layer of a multi-layer buffer must be aligned so that
		 * it is accessible by both producer and consumer. In most cases,
		 * the stride alignment is also sufficient for each layer, however
		 * for AFBC the header buffer alignment is more constrained (see
		 * AFBC specification v3.4, section 2.15: "Alignment requirements").
		 * Also update the buffer size to accommodate all layers.
		 */
		if (descriptor->layer_count > 1)
		{
			if (descriptor->alloc_format.is_afbc())
			{
				if (descriptor->alloc_format.get_afbc_tiled_headers())
				{
					descriptor->size = GRALLOC_ALIGN(descriptor->size, 4096);
				}
				else
				{
					descriptor->size = GRALLOC_ALIGN(descriptor->size, 128);
				}
			}

			descriptor->size *= descriptor->layer_count;
		}
	}
	return 0;
}

unique_private_handle mali_gralloc_buffer_allocate(buffer_descriptor_t *descriptor)
{
	int err = mali_gralloc_derive_format_and_size(descriptor);
	if (err != 0)
	{
		MALI_GRALLOC_LOGE("buffer allocation failed: %s", strerror(-err));
		return nullptr;
	}

	auto handle = allocator_allocate(descriptor);
	if (handle == nullptr)
	{
		MALI_GRALLOC_LOGE("buffer allocation failed: %s", strerror(ENOMEM));
		return nullptr;
	}

	handle->backing_store_id = getUniqueId();

	return handle;
}

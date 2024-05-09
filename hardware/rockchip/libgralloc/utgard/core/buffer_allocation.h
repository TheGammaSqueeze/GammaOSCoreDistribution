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
#pragma once

#include <hardware/hardware.h>

#define USE_GRALLOC_0
#include <hardware/gralloc_rockchip.h>

#include "gralloc_priv.h"
#include "core/buffer_descriptor.h"
#include "usages.h"

/* Compression scheme */
enum class AllocBaseType
{
	/*
	 * No compression scheme
	 */
	UNCOMPRESSED,

	/*
	 * Arm Framebuffer Compression
	 */
	AFBC,              /* 16 x 16 block size */
	AFBC_WIDEBLK,      /* 32 x 8 block size */
	AFBC_EXTRAWIDEBLK, /* 64 x 4 block size */

	/*
	 * Arm Fixed Rate Compression
	 */
	AFRC,

	/* Block Linear */
	BLOCK_LINEAR,
};

/*
 * Allocation type.
 *
 * Allocation-specific properties of format modifiers
 * described by MALI_GRALLOC_INTFMT_*.
 */
struct AllocType
{
	/*
	 * The compression scheme in use
	 *
	 * For AFBC formats, this describes:
	 * - the block size for single plane base formats, or
	 * - the block size of the first/luma plane for multi-plane base formats.
	 */
	AllocBaseType primary_type{AllocBaseType::UNCOMPRESSED};

	/*
	 * Multi-plane AFBC format. AFBC chroma-only plane(s) are
	 * always compressed with superblock type 'AFBC_EXTRAWIDEBLK'.
	 */
	bool is_multi_plane{};

	/*
	 * Allocate tiled AFBC headers.
	 */
	bool is_tiled{};

	/*
	 * Pad AFBC header stride to 64-byte alignment
	 * (multiple of 4x16B headers).
	 */
	bool is_padded{};

	/*
	 * Front-buffer rendering safe AFBC allocations include an
	 * additional 4kB-aligned body buffer.
	 */
	bool is_frontbuffer_safe{};

	struct
	{
		/*
		 * Coding unit size and alignment requirement (in bytes) of the RGBA or
		 * luminance (Y) plane
		 */
		uint32_t rgba_luma_coding_unit_bytes{};
		uint32_t rgba_luma_plane_alignment{};

		/*
		 * Coding unit size and alignment requirement (in bytes) of the
		 * chrominance (U & V) planes
		 */
		uint32_t chroma_coding_unit_bytes{};
		uint32_t chroma_plane_alignment{};

		/*
		 * Clump dimensions (in pixels) for each plane (zero for unused planes)
		 */
		uint32_t clump_width[3]{0, 0, 0};
		uint32_t clump_height[3]{0, 0, 0};

		/*
		 * Paging tile dimensions (in coding units) for the whole buffer
		 */
		uint32_t paging_tile_width{};
		uint32_t paging_tile_height{};
	}
	afrc;

	bool is_afbc() const
	{
		switch (primary_type)
		{
		case AllocBaseType::AFBC:
		case AllocBaseType::AFBC_WIDEBLK:
		case AllocBaseType::AFBC_EXTRAWIDEBLK:
			return true;
		default:
			return false;
		}
	}

	bool is_afrc() const
	{
		return primary_type == AllocBaseType::AFRC;
	}

	bool is_block_linear() const
	{
		return primary_type == AllocBaseType::BLOCK_LINEAR;
	}
};

using alloc_type_t = AllocType;

int mali_gralloc_derive_format_and_size(buffer_descriptor_t *descriptor);

int mali_gralloc_buffer_allocate(buffer_descriptor_t *descriptor, private_handle_t **out_handle);

int mali_gralloc_buffer_free(private_handle_t *handle);

void init_afbc(uint8_t *buf, uint64_t internal_format, const bool is_multi_plane, int w, int h);

uint32_t lcm(uint32_t a, uint32_t b);

bool get_alloc_type(const uint64_t format_ext,
                    const uint32_t format_idx,
                    const uint64_t usage,
                    alloc_type_t * const alloc_type);

static inline uint64_t get_usage_flag_for_stride_alignment(uint64_t usage)
{
	return (usage & (RK_GRALLOC_USAGE_STRIDE_ALIGN_16
				| RK_GRALLOC_USAGE_STRIDE_ALIGN_64
				| RK_GRALLOC_USAGE_STRIDE_ALIGN_128
				| RK_GRALLOC_USAGE_STRIDE_ALIGN_256_ODD_TIMES) );
}

static inline bool is_stride_alignment_specified(uint64_t usage)
{
	return ( get_usage_flag_for_stride_alignment(usage) != 0 );
}

static inline bool has_rkvdec_scaling(uint64_t usage)
{
	return ( (usage & GRALLOC_USAGE_RKVDEC_SCALING) != 0 );
}

static inline bool has_dynamic_hdr(uint64_t usage)
{
	return ( (usage & GRALLOC_USAGE_DYNAMIC_HDR) != 0 );
}

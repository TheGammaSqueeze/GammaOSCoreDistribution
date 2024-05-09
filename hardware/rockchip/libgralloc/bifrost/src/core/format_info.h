/*
 * Copyright (C) 2018-2022 ARM Limited. All rights reserved.
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

#include "buffer.h"

#include <vector>

typedef uint8_t format_support_flags;

/* Base format unsupported */
#define F_NONE 0
/* Base format supports uncompressed */
#define F_LIN (static_cast<format_support_flags>(1) << 0)
/* Base format supports AFBC */
#define F_AFBC (static_cast<format_support_flags>(1) << 1)

/* Base format supports AFRC */
#define F_AFRC (static_cast<format_support_flags>(1) << 2)

#define F_BL_YUV (static_cast<format_support_flags>(1) << 3)

typedef struct
{
	uint16_t width;
	uint16_t height;
} rect_t;

typedef enum rk_board_platform_t
{
    RK3326,
    RK356X,
    RK3588,
    RK_BOARD_PLATFORM_UNKNOWN,
} rk_board_platform_t;

/*
 * Pixel format information.
 *
 * These properties are used by gralloc for buffer allocation.
 * Each format is uniquely identified with 'id'.
 */
struct format_info_t
{
	uint32_t id;                    /* Format ID. */
	uint8_t npln;                   /* Number of planes. */
	uint8_t ncmp[max_planes];       /* Number of components in each plane. */
	uint8_t bps;                    /* Bits per sample (primary/largest). */
	uint8_t bpp_afbc[max_planes];   /* Bits per pixel (AFBC), without implicit padding. 'X' in RGBX is still included. */
	uint8_t bpp[max_planes];        /* Bits per pixel (linear/uncompressed), including any implicit sample padding defined by format (e.g. 10-bit Y210 padded to 16-bits).
	                                 * NOTE: bpp[n] and/or (bpp[n] * align_w_cpu) must be multiples of 8. */
	uint8_t hsub;                   /* Horizontal sub-sampling (YUV formats). Pixel rounding in width (all formats). Must be a power of 2. */
	uint8_t vsub;                   /* Vertical sub-sampling (YUV formats). Pixel rounding in height (all formats). Must be a power of 2. */
	uint8_t align_w;                /* Alignment of width (per plane, in pixels). Must be a power of 2. NOTE: where 'is_yuv == true', this must be a multiple of 'hsub'. */
	uint8_t align_h;                /* Alignment of height (per plane, in pixels). Must be a power of 2. NOTE: where 'is_yuv == true', this must be a multiple of 'vsub'. */
	uint8_t align_w_cpu;            /* Alignment of width for CPU access (per plane, in pixels). ALIGN_W_CPU_DEFAULT: 1. Must be a power of 2. */
	uint16_t tile_size;             /* Tile size (in pixels), assumed square. Uncompressed only. */
	bool has_alpha;                 /* Alpha channel present. */
	bool is_rgb;                    /* RGB format. */
	bool is_yuv;                    /* YUV format. */
	bool afbc;                      /* AFBC supported (per specification and by gralloc). IP support not considered. */
	bool linear;                    /* Linear/uncompressed supported. */
	bool yuv_transform;             /* Supports AFBC YUV transform: 3+ channel RGB (strict R-G-B-? order) with less than 12-bit per sample. */
	bool flex;                      /* Linear version of format can be represented as flex. */
	bool block_linear;              /* Format supports 16x16 Block Linear layout */
	bool afrc;                      /* AFRC supported (per specification and by gralloc). IP support not considered. */
	uint64_t permitted_usage;       /* Buffer usage mask*/

	/* Computes the total number of components in the format. */
	int total_components() const
	{
		int sum = 0;
		for (auto n: ncmp)
		{
			sum += n;
		}
		return sum;
	}
};
/* clang-format: on */

/* Returns true if the formats are the same or if they only differ with respect to the order of components.
	False otherwise. */
static inline bool is_same_or_components_reordered(const format_info_t &x, const format_info_t &y)
{
	return x.npln == y.npln && x.total_components() == y.total_components() && x.bps == y.bps && x.is_yuv == y.is_yuv &&
	       x.hsub == y.hsub && x.vsub == y.vsub;
}

typedef struct
{
	uint32_t id;                       /* Format ID. */
	format_support_flags cpu_wr;       /* CPU producer. */
	format_support_flags cpu_rd;       /* CPU consumer. */
	format_support_flags gpu_wr;       /* GPU producer. */
	format_support_flags gpu_rd;       /* GPU consumer. */
	format_support_flags dpu_wr;       /* DPU producer. */
	format_support_flags dpu_rd;       /* DPU consumer. */
	format_support_flags dpu_aeu_wr;   /* DPU AEU producer. */
	format_support_flags vpu_wr;       /* VPU producer. */
	format_support_flags vpu_rd;       /* VPU consumer. */
	format_support_flags cam_wr;       /* Camera producer. */

} format_ip_support_t;

/**
 * @brief Get the list of all base formats known to Gralloc.
 */
const std::vector<format_info_t> &get_all_base_formats();

const format_info_t *get_format_info(uint32_t base_format);
extern const format_ip_support_t *get_format_ip_support(uint32_t base_format);
extern uint32_t get_internal_format(uint32_t base_format);
void get_format_dataspace(const format_info_t *info,
                          uint64_t usage,
                          int width,
                          int height,
                          android_dataspace_t *dataspace);

rk_board_platform_t get_rk_board_platform();

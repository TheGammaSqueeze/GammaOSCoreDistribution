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
#include <inttypes.h>
#include "helper_functions.h"
#include "gralloc/formats.h"
#include "format_info.h"
#include "usages.h"

/* Default width aligned to whole pixel (CPU access). */
#define ALIGN_W_CPU_DEFAULT .align_w_cpu = 1

/*
 * Format table, containing format properties.
 *
 * NOTE: This table should only be used within
 * the gralloc library and not by clients directly.
 */
/* clang-format off */
const std::vector<format_info_t> formats = {
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGB_565,
		.npln = 1, .ncmp = { 3, 0, 0 }, .bps = 6, .bpp_afbc = { 16, 0, 0 }, .bpp = { 16, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = true, .is_yuv = false,
		.afbc = true, .linear = true, .yuv_transform = true, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGB_888,
		.npln = 1, .ncmp = { 3, 0, 0 }, .bps = 8, .bpp_afbc = { 24, 0, 0 }, .bpp = { 24, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = true, .is_yuv = false,
		.afbc = true, .linear = true, .yuv_transform = true, .flex = true, .block_linear = false, .afrc = true,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_BGR_888,
		.npln = 1, .ncmp = { 3, 0, 0 }, .bps = 8, .bpp_afbc = { 0, 0, 0 }, .bpp = { 24, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = true, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = true, .block_linear = false, .afrc = true,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGBA_8888,
		.npln = 1, .ncmp = { 4, 0, 0 }, .bps = 8, .bpp_afbc = { 32, 0, 0 }, .bpp = { 32, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = true, .is_rgb = true, .is_yuv = false,
		.afbc = true, .linear = true, .yuv_transform = true, .flex = true, .block_linear = false, .afrc = true,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_BGRA_8888,
		.npln = 1, .ncmp = { 4, 0, 0 }, .bps = 8, .bpp_afbc = { 32, 0, 0 }, .bpp = { 32, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = true, .is_rgb = true, .is_yuv = false,
		.afbc = true, .linear = true, .yuv_transform = false, .flex = true, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGBX_8888,
		.npln = 1, .ncmp = { 3, 0, 0 }, .bps = 8, .bpp_afbc = { 32, 0, 0 }, .bpp = { 32, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = true, .is_yuv = false,
		.afbc = true, .linear = true, .yuv_transform = true, .flex = true, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGBA_1010102,
		.npln = 1, .ncmp = { 4, 0, 0 }, .bps = 10, .bpp_afbc = { 32, 0, 0 }, .bpp = { 32, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = true, .is_rgb = true, .is_yuv = false,
		.afbc = true, .linear = true, .yuv_transform = true, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGBA_16161616,
		.npln = 1, .ncmp = { 4, 0, 0 }, .bps = 16, .bpp_afbc = { 64, 0, 0 }, .bpp = { 64, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = true, .is_rgb = true, .is_yuv = false,
		.afbc = true, .linear = true, .yuv_transform = true, .flex = true, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGBA_10101010,
		.npln = 1, .ncmp = { 4, 0, 0 }, .bps = 10, .bpp_afbc = { 40, 0, 0 }, .bpp = { 64, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = true, .is_rgb = true, .is_yuv = false,
		.afbc = true, .linear = true, .yuv_transform = true, .flex = false, .block_linear = false, .afrc = true,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Y8,
		.npln = 1, .ncmp = { 1, 0, 0 }, .bps = 8, .bpp_afbc = { 8, 0, 0 }, .bpp = { 8, 0, 0 },
		.hsub = 1, .vsub = 1, .align_w = 2, .align_h = 2, .align_w_cpu = 16,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = true, .yuv_transform = false, .flex = true, .block_linear = false, .afrc = true,
		.permitted_usage = add_universal_usages(GRALLOC_USAGE_HW_CAMERA_WRITE | GRALLOC_USAGE_HW_CAMERA_READ |
		GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Y16,
		.npln = 1, .ncmp = { 1, 0, 0 }, .bps = 16, .bpp_afbc = { 16, 0, 0 }, .bpp = { 16, 0, 0 },
		.hsub = 1, .vsub = 1, .align_w = 2, .align_h = 2, .align_w_cpu = 16,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = true, .yuv_transform = false, .flex = true, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(GRALLOC_USAGE_HW_CAMERA_WRITE | GRALLOC_USAGE_HW_CAMERA_READ |
		GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER),
	},
	/* 420 (8-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YUV420_8BIT_I,
		.npln = 1, .ncmp = { 3, 0, 0 }, .bps = 8, .bpp_afbc = { 12, 0, 0 }, .bpp = { 0, 0, 0 },
		.hsub = 2, .vsub = 2, .align_w = 2, .align_h = 2, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = false, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV12,
		.npln = 2, .ncmp = { 1, 2, 0 }, .bps = 8, .bpp_afbc = { 8, 16, 0 }, .bpp = { 8, 16, 0 },
		.hsub = 2, .vsub = 2, .align_w = 2, .align_h = 2, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = true, .yuv_transform = false, .flex = true, .block_linear = true, .afrc = true,
		.permitted_usage = add_universal_usages(
		GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_HW_COMPOSER |
		GRALLOC_USAGE_DECODER | GRALLOC_USAGE_HW_VIDEO_ENCODER | GRALLOC_USAGE_HW_FB |
		GRALLOC_USAGE_FRONTBUFFER),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV21,
		.npln = 2, .ncmp = { 1, 2, 0 }, .bps = 8, .bpp_afbc = { 8, 16, 0 }, .bpp = { 8, 16, 0 },
		.hsub = 2, .vsub = 2, .align_w = 2, .align_h = 2, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = true, .yuv_transform = false, .flex = true, .block_linear = false, .afrc = true,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YV12,
		.npln = 3, .ncmp = { 1, 1, 1 }, .bps = 8, .bpp_afbc = { 8, 8, 8 }, .bpp = { 8, 8, 8 },
		.hsub = 2, .vsub = 2, .align_w = 2, .align_h = 2, .align_w_cpu = 16,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = true, .yuv_transform = false, .flex = true, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(GRALLOC_USAGE_HW_CAMERA_WRITE | GRALLOC_USAGE_HW_CAMERA_READ |
		GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_HW_FB |
		GRALLOC_USAGE_HW_VIDEO_ENCODER | GRALLOC_USAGE_DECODER | GRALLOC_USAGE_EXTERNAL_DISP |
		GRALLOC_USAGE_FRONTBUFFER),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YU12,
		.npln = 3, .ncmp = { 1, 1, 1 }, .bps = 8, .bpp_afbc = { 0, 0, 0 }, .bpp = { 8, 8, 8 },
		.hsub = 2, .vsub = 2, .align_w = 2, .align_h = 2, .align_w_cpu = 16,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = true, .block_linear = false, .afrc = true,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	/* 422 (8-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT,
		.npln = 1, .ncmp = { 3, 0, 0 }, .bps = 8, .bpp_afbc = { 16, 0, 0 }, .bpp = { 16, 0, 0 },
		.hsub = 2, .vsub = 1, .align_w = 2, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = true, .yuv_transform = false, .flex = true, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV16,
		.npln = 2, .ncmp = { 1, 2, 0 }, .bps = 8, .bpp_afbc = { 8, 16, 0 }, .bpp = { 8, 16, 0 },
		.hsub = 2, .vsub = 1, .align_w = 2, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = true, .yuv_transform = false, .flex = true, .block_linear = true, .afrc = true,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	/* 444 (8-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV24,
		.npln = 2, .ncmp = { 1, 2, 0 }, .bps = 8, .bpp_afbc = { 0, 0, 0 }, .bpp = { 8, 16, 0 },
		.hsub = 1, .vsub = 1, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = true, .block_linear = false, .afrc = false,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YUV444,
		.npln = 3, .ncmp = { 1, 1, 1 }, .bps = 8, .bpp_afbc = { 0, 0, 0 }, .bpp = { 0, 0, 0 },
		.hsub = 1, .vsub = 1, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = false, .linear = false, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = true,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	/* 444 (10-bit) 2 plane */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV30,
		.npln = 3, .ncmp = { 1, 2, 0 }, .bps = 10, .bpp_afbc = { 0, 0, 0 }, .bpp = { 10, 20, 0 },
		.hsub = 1, .vsub = 1, .align_w = 2, .align_h = 2, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
	},
	/* 444 (10-bit) 3 plane */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Q410,
		.npln = 3, .ncmp = { 1, 1, 1 }, .bps = 10, .bpp_afbc = { 0, 0, 0 }, .bpp = { 0, 0, 0 },
		.hsub = 1, .vsub = 1, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = false, .linear = false, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = true,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Q401,
		.npln = 3, .ncmp = { 1, 1, 1 }, .bps = 10, .bpp_afbc = { 0, 0, 0 }, .bpp = { 0, 0, 0 },
		.hsub = 1, .vsub = 1, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = false, .linear = false, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = true,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	/* 420 (10-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YUV420_10BIT_I,
		.npln = 1, .ncmp = { 3, 0, 0 }, .bps = 10, .bpp_afbc = { 15, 0, 0 }, .bpp = { 0, 0, 0 },
		.hsub = 2, .vsub = 2, .align_w = 2, .align_h = 2, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = false, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Y0L2,
		.npln = 1, .ncmp = { 4, 0, 0 }, .bps = 10, .bpp_afbc = { 0, 0, 0 }, .bpp = { 16, 0, 0 },
		.hsub = 2, .vsub = 2, .align_w = 2, .align_h = 2, ALIGN_W_CPU_DEFAULT,
		.tile_size = 2, .has_alpha = true, .is_rgb = false, .is_yuv = true,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_P010,
		.npln = 2, .ncmp = { 1, 2, 0 }, .bps = 10, .bpp_afbc = { 10, 20, 0 }, .bpp = { 16, 32, 0 },
		.hsub = 2, .vsub = 2, .align_w = 2, .align_h = 2, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = true, .yuv_transform = false, .flex = true, .block_linear = true, .afrc = true,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV15,
		.npln = 2, .ncmp = { 1, 2, 0 }, .bps = 10, .bpp_afbc = { 0, 0, 0 }, .bpp = { 10, 20, 0 },
		.hsub = 2, .vsub = 2, .align_w = 2, .align_h = 2, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = true, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	/* 422 (10-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Y210,
		.npln = 1, .ncmp = { 3, 0, 0 }, .bps = 10, .bpp_afbc = { 20, 0, 0 }, .bpp = { 32, 0, 0 },
		.hsub = 2, .vsub = 1, .align_w = 2, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = true, .yuv_transform = false, .flex = true, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_P210,
		.npln = 2, .ncmp = { 1, 2, 0 }, .bps = 10, .bpp_afbc = { 10, 20, 0 }, .bpp = { 16, 32, 0 },
		.hsub = 2, .vsub = 1, .align_w = 2, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = true,
		.afbc = true, .linear = true, .yuv_transform = false, .flex = true, .block_linear = false, .afrc = true,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	/* 444 (10-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Y410,
		.npln = 1, .ncmp = { 4, 0, 0 }, .bps = 10, .bpp_afbc = { 0, 0, 0 }, .bpp = { 32, 0, 0 },
		.hsub = 1, .vsub = 1, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = true, .is_rgb = false, .is_yuv = true,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	/* Other */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RAW16,
		.npln = 1, .ncmp = { 1, 0, 0 }, .bps = 16, .bpp_afbc = { 0, 0, 0 }, .bpp = { 16, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 2, .align_h = 2, .align_w_cpu = 16,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RAW12,
		.npln = 1, .ncmp = { 1, 0, 0 }, .bps = 12, .bpp_afbc = { 0, 0, 0 }, .bpp = { 12, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 4, .align_h = 2, .align_w_cpu = 4,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(GRALLOC_USAGE_HW_CAMERA_WRITE | GRALLOC_USAGE_HW_CAMERA_READ |
		GRALLOC_USAGE_RENDERSCRIPT | GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RAW10,
		.npln = 1, .ncmp = { 1, 0, 0 }, .bps = 10, .bpp_afbc = { 0, 0, 0 }, .bpp = { 10, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 4, .align_h = 2, .align_w_cpu = 4,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(GRALLOC_USAGE_HW_CAMERA_WRITE | GRALLOC_USAGE_HW_CAMERA_READ |
		GRALLOC_USAGE_RENDERSCRIPT |
		GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_BLOB,
		.npln = 1, .ncmp = { 1, 0, 0 }, .bps = 8, .bpp_afbc = { 0, 0, 0 }, .bpp = { 8, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	/* Depth and Stencil */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_16,
		.npln = 1, .ncmp = { 1, 0, 0 }, .bps = 16, .bpp_afbc = { 0, 0, 0}, .bpp = { 16, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24,
		.npln = 1, .ncmp = { 1, 0, 0 }, .bps = 24, .bpp_afbc = { 0, 0, 0 }, .bpp = { 24, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24_STENCIL_8,
		.npln = 1, .ncmp = { 2, 0, 0 }, .bps = 24, .bpp_afbc = { 0, 0, 0 }, .bpp = { 32, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F,
		.npln = 1, .ncmp = { 1, 0, 0 }, .bps = 32, .bpp_afbc = { 0, 0, 0 }, .bpp = { 32, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F_STENCIL_8,
		.npln = 1, .ncmp = { 2, 0, 0 }, .bps = 32, .bpp_afbc = { 0, 0, 0 }, .bpp = { 40, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_STENCIL_8,
		.npln = 1, .ncmp = { 1, 0, 0 }, .bps = 8, .bpp_afbc = { 0, 0, 0 }, .bpp = { 8, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = false, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(STANDARD_USAGE),
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_R8,
		.npln = 1, .ncmp = { 1, 0, 0 }, .bps = 8, .bpp_afbc = { 0, 0, 0 }, .bpp = { 8, 0, 0 },
		.hsub = 0, .vsub = 0, .align_w = 1, .align_h = 1, ALIGN_W_CPU_DEFAULT,
		.tile_size = 1, .has_alpha = false, .is_rgb = true, .is_yuv = false,
		.afbc = false, .linear = true, .yuv_transform = false, .flex = false, .block_linear = false, .afrc = false,
		.permitted_usage = add_universal_usages(GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER),
	}
};

/*
 * This table represents the superset of flags for each base format and producer/consumer.
 * Where IP does not support a capability, it should be defined and not set.
 */
const format_ip_support_t formats_ip_support[] = {
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGB_565,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC,
		.gpu_wr = F_LIN | F_AFBC,
		.dpu_rd = F_LIN | F_AFBC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_AFBC,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGB_888,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC | F_AFRC,
		.gpu_wr = F_LIN | F_AFBC | F_AFRC,
		.dpu_rd = F_LIN | F_AFBC | F_AFRC,
		.dpu_wr = F_LIN,
		.dpu_aeu_wr = F_AFBC,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_BGR_888,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC | F_AFRC,
		.gpu_wr = F_LIN | F_AFBC | F_AFRC,
		.dpu_rd = F_LIN | F_AFBC | F_AFRC,
		.dpu_wr = F_LIN,
		.dpu_aeu_wr = F_AFBC,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGBA_8888,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC | F_AFRC,
		.gpu_wr = F_LIN | F_AFBC | F_AFRC,
		.dpu_rd = F_LIN | F_AFBC | F_AFRC,
		.dpu_wr = F_LIN,
		.dpu_aeu_wr = F_AFBC,
		.vpu_rd = F_LIN,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_BGRA_8888,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN,
		.gpu_wr = F_LIN,
		.dpu_rd = F_LIN,
		.dpu_wr = F_LIN,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_LIN,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGBX_8888,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC,
		.gpu_wr = F_LIN | F_AFBC,
		.dpu_rd = F_LIN | F_AFBC,
		.dpu_wr = F_LIN,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGBA_1010102,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC,
		.gpu_wr = F_LIN | F_AFBC,
		.dpu_rd = F_LIN | F_AFBC,
		.dpu_wr = F_LIN,
		.dpu_aeu_wr = F_AFBC,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGBA_16161616,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC,
		.gpu_wr = F_LIN | F_AFBC,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RGBA_10101010,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC | F_AFRC,
		.gpu_wr = F_LIN | F_AFBC | F_AFRC,
		.dpu_rd = F_AFRC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Y8,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_NONE,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Y16,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_NONE,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	/* 420 (8-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YUV420_8BIT_I,
		.cpu_rd = F_NONE,
		.cpu_wr = F_NONE,
		.gpu_rd = F_AFBC,
		.gpu_wr = F_AFBC,
		.dpu_rd = F_AFBC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_AFBC,
		.vpu_rd = F_AFBC,
		.vpu_wr = F_AFBC,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV12,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC | F_BL_YUV | F_AFRC,
		.gpu_wr = F_LIN | F_AFRC | F_BL_YUV,
		.dpu_rd = F_LIN | F_AFRC,
		.dpu_wr = F_LIN,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_LIN,
		.vpu_wr = F_LIN | F_BL_YUV,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV21,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFRC,
		.gpu_wr = F_LIN | F_AFRC,
		.dpu_rd = F_NONE | F_AFRC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_LIN,
		.vpu_wr = F_LIN,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YV12,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFRC,
		.gpu_wr = F_LIN | F_AFRC,
		.dpu_rd = F_LIN | F_AFRC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_LIN,
		.vpu_wr = F_LIN,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YU12,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFRC,
		.gpu_wr = F_LIN | F_AFRC,
		.dpu_rd = F_LIN | F_AFRC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_LIN,
		.vpu_wr = F_LIN,
		.cam_wr = F_NONE,
	},
	/* 422 (8-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC,
		.gpu_wr = F_LIN | F_AFBC,
		.dpu_rd = F_LIN | F_AFBC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_AFBC,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV16,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC | F_BL_YUV | F_AFRC,
		.gpu_wr = F_LIN | F_AFRC | F_BL_YUV,
		.dpu_rd = F_NONE | F_AFRC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_BL_YUV,
		.cam_wr = F_NONE,
	},
	/* 444 (8-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV24,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC | F_BL_YUV | F_AFRC,
		.gpu_wr = F_LIN | F_AFRC | F_BL_YUV,
		.dpu_rd = F_NONE | F_AFRC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_BL_YUV,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YUV444,
		.cpu_rd = F_NONE,
		.cpu_wr = F_NONE,
		.gpu_rd = F_AFRC,
		.gpu_wr = F_AFRC,
		.dpu_rd = F_AFRC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	/* 444 (10-bit) 2 plane */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV30,
		.cpu_rd = F_NONE,
		.cpu_wr = F_NONE,
		.gpu_rd = F_BL_YUV,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_BL_YUV,
		.cam_wr = F_NONE,
	},
	/* 444 (10-bit) 3 plane */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Q410,
		.cpu_rd = F_NONE,
		.cpu_wr = F_NONE,
		.gpu_rd = F_AFRC,
		.gpu_wr = F_AFRC,
		.dpu_rd = F_AFRC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Q401,
		.cpu_rd = F_NONE,
		.cpu_wr = F_NONE,
		.gpu_rd = F_AFRC,
		.gpu_wr = F_AFRC,
		.dpu_rd = F_AFRC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	/* 420 (10-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_YUV420_10BIT_I,
		.cpu_rd = F_NONE,
		.cpu_wr = F_NONE,
		.gpu_rd = F_AFBC,
		.gpu_wr = F_AFBC,
		.dpu_rd = F_AFBC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_AFBC,
		.vpu_rd = F_AFBC,
		.vpu_wr = F_AFBC,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Y0L2,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN,
		.gpu_wr = F_LIN,
		.dpu_rd = F_LIN,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_LIN,
		.vpu_wr = F_LIN,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_P010,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_BL_YUV | F_AFRC,
		.gpu_wr = F_LIN | F_BL_YUV | F_AFRC,
		.dpu_rd = F_LIN | F_AFRC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_LIN,
		.vpu_wr = F_LIN | F_BL_YUV,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_NV15,
		.cpu_rd = F_NONE,
		.cpu_wr = F_NONE,
		.gpu_rd = F_BL_YUV,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_BL_YUV,
		.cam_wr = F_NONE,
	},
	/* 422 (10-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Y210,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFBC,
		.gpu_wr = F_LIN | F_AFBC,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_P210,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN | F_AFRC,
		.gpu_wr = F_LIN | F_AFRC,
		.dpu_rd = F_NONE | F_AFRC,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	/* 444 (10-bit) */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_Y410,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN,
		.gpu_wr = F_LIN | F_AFBC,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	/* Other */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RAW16,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_LIN,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RAW12,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_NONE,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_RAW10,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_NONE,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_BLOB,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN,
		.gpu_wr = F_LIN,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	/* Depth and Stencil */
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_16,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_NONE,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_NONE,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24_STENCIL_8,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_NONE,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_NONE,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F_STENCIL_8,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_NONE,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_STENCIL_8,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_NONE,
		.gpu_wr = F_NONE,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
	{
		.id = MALI_GRALLOC_FORMAT_INTERNAL_R8,
		.cpu_rd = F_LIN,
		.cpu_wr = F_LIN,
		.gpu_rd = F_LIN,
		.gpu_wr = F_LIN,
		.dpu_rd = F_NONE,
		.dpu_wr = F_NONE,
		.dpu_aeu_wr = F_NONE,
		.vpu_rd = F_NONE,
		.vpu_wr = F_NONE,
		.cam_wr = F_NONE,
	},
};
/* clang-format on */

typedef struct
{
    uint32_t hal_format;
    uint32_t internal_format;
} hal_int_fmt;


static const hal_int_fmt hal_to_internal_format[] =
{
	{ HAL_PIXEL_FORMAT_RGBA_8888,              MALI_GRALLOC_FORMAT_INTERNAL_RGBA_8888 },
	{ HAL_PIXEL_FORMAT_RGBX_8888,              MALI_GRALLOC_FORMAT_INTERNAL_RGBX_8888 },
	{ HAL_PIXEL_FORMAT_RGB_888,                MALI_GRALLOC_FORMAT_INTERNAL_RGB_888 },
	{ HAL_PIXEL_FORMAT_BGR_888,                MALI_GRALLOC_FORMAT_INTERNAL_BGR_888},
	{ HAL_PIXEL_FORMAT_RGB_565,                MALI_GRALLOC_FORMAT_INTERNAL_RGB_565 },
	{ HAL_PIXEL_FORMAT_BGRA_8888,              MALI_GRALLOC_FORMAT_INTERNAL_BGRA_8888 },
	{ HAL_PIXEL_FORMAT_YCbCr_422_SP,           MALI_GRALLOC_FORMAT_INTERNAL_NV16 },
	{ HAL_PIXEL_FORMAT_YCrCb_420_SP,           MALI_GRALLOC_FORMAT_INTERNAL_NV21 },
	{ HAL_PIXEL_FORMAT_YCbCr_422_I,            MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT },
	{ HAL_PIXEL_FORMAT_RGBA_FP16,              MALI_GRALLOC_FORMAT_INTERNAL_RGBA_16161616 },
	{ HAL_PIXEL_FORMAT_RAW16,                  MALI_GRALLOC_FORMAT_INTERNAL_RAW16 },
	{ HAL_PIXEL_FORMAT_BLOB,                   MALI_GRALLOC_FORMAT_INTERNAL_BLOB },
	{ HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED, MALI_GRALLOC_FORMAT_INTERNAL_NV12 },
	{ HAL_PIXEL_FORMAT_YCbCr_420_888,          MALI_GRALLOC_FORMAT_INTERNAL_NV12 },
	{ HAL_PIXEL_FORMAT_RAW_OPAQUE,             MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED },
	{ HAL_PIXEL_FORMAT_RAW10,                  MALI_GRALLOC_FORMAT_INTERNAL_RAW10 },
	{ HAL_PIXEL_FORMAT_RAW12,                  MALI_GRALLOC_FORMAT_INTERNAL_RAW12 },
	{ HAL_PIXEL_FORMAT_YCbCr_422_888,          MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT },
	{ HAL_PIXEL_FORMAT_YCbCr_444_888,          MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED },
	{ HAL_PIXEL_FORMAT_FLEX_RGB_888,           MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED },
	{ HAL_PIXEL_FORMAT_FLEX_RGBA_8888,         MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED },
	{ HAL_PIXEL_FORMAT_RGBA_1010102,           MALI_GRALLOC_FORMAT_INTERNAL_RGBA_1010102 },
	{ HAL_PIXEL_FORMAT_DEPTH_16,               MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_16 },
	{ HAL_PIXEL_FORMAT_DEPTH_24,               MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24 },
	{ HAL_PIXEL_FORMAT_DEPTH_24_STENCIL_8,     MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24_STENCIL_8 },
	{ HAL_PIXEL_FORMAT_DEPTH_32F,              MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F },
	{ HAL_PIXEL_FORMAT_DEPTH_32F_STENCIL_8,    MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F_STENCIL_8 },
	{ HAL_PIXEL_FORMAT_STENCIL_8,              MALI_GRALLOC_FORMAT_INTERNAL_STENCIL_8 },
	{ HAL_PIXEL_FORMAT_YCBCR_P010,             MALI_GRALLOC_FORMAT_INTERNAL_P010 },
	{ HAL_PIXEL_FORMAT_Y8,                     MALI_GRALLOC_FORMAT_INTERNAL_Y8 },
	{ HAL_PIXEL_FORMAT_Y16,                    MALI_GRALLOC_FORMAT_INTERNAL_Y16 },
	{ HAL_PIXEL_FORMAT_YV12,                   MALI_GRALLOC_FORMAT_INTERNAL_YV12 },
	{ PIXEL_FORMAT_R8,						   MALI_GRALLOC_FORMAT_INTERNAL_R8 },
};

#if PLATFORM_SDK_VERSION >= 33
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
using aidl::android::hardware::graphics::common::PixelFormat;
static_assert(static_cast<uint32_t>(PixelFormat::R_8) == PIXEL_FORMAT_R8);
#endif

const std::vector<format_info_t> &get_all_base_formats()
{
	return formats;
}


/**
 * @brief Find information for the specified base format
 *
 * @param base_format [in] Format for which information is required.
 *
 * @return Pointer to the #format_info_t structure, when the format is found in the look up table
 *         @c nullptr otherwise.
 */
const format_info_t *get_format_info(const uint32_t base_format)
{
	for (const auto &format : formats)
	{
		if (format.id == base_format)
		{
			return &format;
		}
	}

	MALI_GRALLOC_LOGE("ERROR: Format allocation info not found for format: %" PRIx32, base_format);
	return nullptr;
}

const format_ip_support_t *get_format_ip_support(const uint32_t base_format)
{
	for (const auto &table_entry : formats_ip_support)
	{
		if (table_entry.id == base_format)
		{
			return &table_entry;
		}
	}

	MALI_GRALLOC_LOGE("ERROR: IP support not found for format: %" PRIx32, base_format);
	return nullptr;
}

/*
 * Attempt to map base HAL format to an internal format and
 * validate format is supported for allocation.
 *
 * @return internal format corresponding to the Android HAL format.
 *
 *
 * NOTE: Base format might be either a HAL format or (already) an internal format.
 *
 */
uint32_t get_internal_format(const uint32_t base_format)
{
	uint32_t internal_format = base_format;

	for (const auto &table_entry : hal_to_internal_format)
	{
		if (table_entry.hal_format == base_format)
		{
			internal_format = table_entry.internal_format;
			break;
		}
	}

	/* Ensure internal format is valid when expected. */
	if (get_format_info(internal_format) == nullptr)
	{
		return MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED;
	}

	return internal_format;
}

/* Get the dataspace to use based on private usage and format. */
void get_format_dataspace(const format_info_t *format_info, uint64_t usage, int width, int height,
                          android_dataspace_t *dataspace)
{
	*dataspace = HAL_DATASPACE_UNKNOWN;

	if (format_info == nullptr)
	{
		return;
	}

	uint64_t color_space = HAL_DATASPACE_STANDARD_UNSPECIFIED;
	uint64_t range = HAL_DATASPACE_RANGE_UNSPECIFIED;

	/* This resolution is the cut-off point at which BT709 is used (as default)
	 * instead of BT601 for YUV formats < 10 bits.
	 */
	constexpr int yuv_bt601_max_width = 1280;
	constexpr int yuv_bt601_max_height = 720;

	if (format_info->is_yuv)
	{
		/* Default YUV dataspace. */
		color_space = HAL_DATASPACE_STANDARD_BT709;
		range = HAL_DATASPACE_RANGE_LIMITED;

		/* 10-bit YUV is assumed to be wide BT2020.
		 */
		if (format_info->bps >= 10)
		{
			color_space = HAL_DATASPACE_STANDARD_BT2020;
			range = HAL_DATASPACE_RANGE_FULL;
		}
		else if (width < yuv_bt601_max_width || height < yuv_bt601_max_height)
		{
			color_space = HAL_DATASPACE_STANDARD_BT601_625;
			range = HAL_DATASPACE_RANGE_LIMITED;
		}

		/* Override YUV dataspace based on private usage. */
		switch (usage & MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_MASK)
		{
		case MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT601:
			color_space = HAL_DATASPACE_STANDARD_BT601_625;
			break;
		case MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT709:
			color_space = HAL_DATASPACE_STANDARD_BT709;
			break;
		case MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT2020:
			color_space = HAL_DATASPACE_STANDARD_BT2020;
			break;
		}

		switch (usage & MALI_GRALLOC_USAGE_RANGE_MASK)
		{
		case MALI_GRALLOC_USAGE_RANGE_NARROW:
			range = HAL_DATASPACE_RANGE_LIMITED;
			break;
		case MALI_GRALLOC_USAGE_RANGE_WIDE:
			range = HAL_DATASPACE_RANGE_FULL;
			break;
		}

		*dataspace = static_cast<android_dataspace_t>(color_space | range);
	}
	else if (format_info->is_rgb)
	{
		/* Default RGB dataspace. Expected by Mapper VTS. */
		*dataspace = static_cast<android_dataspace_t>(HAL_DATASPACE_UNKNOWN);
	}
}

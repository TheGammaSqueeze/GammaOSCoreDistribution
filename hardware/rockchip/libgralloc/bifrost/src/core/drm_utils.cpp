/*
 * Copyright (C) 2020-2022 Arm Limited.
 * SPDX-License-Identifier: Apache-2.0
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

#include <unordered_map>
#include "drm_utils.h"
#include "gralloc/formats.h"
#include "core/format_info.h"
#include "core/internal_format.h"

enum class format_colormodel
{
	rgb,
	yuv,
};

struct table_entry
{
	uint32_t fourcc;
	format_colormodel colormodel;
};

const static std::unordered_map<mali_gralloc_internal_format, table_entry> table =
{
	{ MALI_GRALLOC_FORMAT_INTERNAL_RAW16, {DRM_FORMAT_R16, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_RGBA_8888, {DRM_FORMAT_ABGR8888, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_BGRA_8888, {DRM_FORMAT_ARGB8888, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_RGB_565, {DRM_FORMAT_RGB565, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_RGBX_8888, {DRM_FORMAT_XBGR8888, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_RGB_888, {DRM_FORMAT_BGR888, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_BGR_888, {DRM_FORMAT_RGB888, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_RGBA_1010102, {DRM_FORMAT_ABGR2101010, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_RGBA_16161616, {DRM_FORMAT_ABGR16161616F, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_RGBA_10101010, {DRM_FORMAT_AXBXGXRX106106106106, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_YV12, {DRM_FORMAT_YVU420, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_YU12, {DRM_FORMAT_YUV420, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_NV12, {DRM_FORMAT_NV12, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_NV15, {DRM_FORMAT_NV15, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_NV30, {DRM_FORMAT_NV30, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_NV16, {DRM_FORMAT_NV16, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_NV24, {DRM_FORMAT_NV24, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_NV21, {DRM_FORMAT_NV21, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_Y0L2, {DRM_FORMAT_Y0L2, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_Y210, {DRM_FORMAT_Y210, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_P010, {DRM_FORMAT_P010, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_P210, {DRM_FORMAT_P210, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_Y410, {DRM_FORMAT_Y410, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_YUV444, {DRM_FORMAT_YUV444, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_Q410, {DRM_FORMAT_Q410, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_Q401, {DRM_FORMAT_Q401, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT, {DRM_FORMAT_YUYV, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_YUV420_8BIT_I, {DRM_FORMAT_YUV420_8BIT, format_colormodel::yuv} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_YUV420_10BIT_I, {DRM_FORMAT_YUV420_10BIT, format_colormodel::yuv} },

	{ MALI_GRALLOC_FORMAT_INTERNAL_R8, {DRM_FORMAT_R8, format_colormodel::rgb} },

	/* workaround for deqp test, DRM fourcc missing Depth and stencil formats, so use other to replace */
	{ MALI_GRALLOC_FORMAT_INTERNAL_STENCIL_8, {DRM_FORMAT_R8, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_16, {DRM_FORMAT_RGB565, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24, {DRM_FORMAT_BGR888, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24_STENCIL_8, {DRM_FORMAT_BGR888, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F, {DRM_FORMAT_ABGR8888, format_colormodel::rgb} },
	{ MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F_STENCIL_8, {DRM_FORMAT_ABGR8888, format_colormodel::rgb} },

	/* Format introduced in Android P, mapped to MALI_GRALLOC_FORMAT_INTERNAL_P010. */
	{ HAL_PIXEL_FORMAT_YCBCR_P010, {DRM_FORMAT_P010, format_colormodel::yuv} },
};

uint32_t drm_fourcc_from_handle(const private_handle_t *hnd)
{
	/* Clean the modifier bits in the internal format. */
	const auto internal_format = hnd->alloc_format;
	const auto base_format = internal_format.get_base();

	auto entry = table.find(base_format);
	if (entry == table.end())
	{
		return DRM_FORMAT_INVALID;
	}

	/* The internal RGB565 format describes two different component orderings depending on AFBC. */
	if (internal_format.is_afbc() && base_format == MALI_GRALLOC_FORMAT_INTERNAL_RGB_565)
	{
		return DRM_FORMAT_BGR565;
	}

	return entry->second.fourcc;
}

static uint64_t get_afrc_modifier_tags(const private_handle_t *hnd)
{
	const auto internal_format = hnd->alloc_format;
	if (!internal_format.is_afrc())
	{
		return 0;
	}

	uint64_t modifier = 0;

	const auto base_format = internal_format.get_base();
	auto entry = table.find(base_format);
	if (entry == table.end())
	{
		return 0;
	}

	if (!internal_format.get_afrc_rot_layout())
	{
		modifier |= AFRC_FORMAT_MOD_LAYOUT_SCAN;
	}

	/* If the afrc format is in yuv colormodel it should also have more than a single plane */
	if (entry->second.colormodel == format_colormodel::yuv && hnd->is_multi_plane())
	{
		switch (internal_format.get_afrc_luma_coding_size())
		{
		case afrc_coding_unit_size_t::bytes_32:
			modifier |= AFRC_FORMAT_MOD_CU_SIZE_P0(AFRC_FORMAT_MOD_CU_SIZE_32);
			break;
		case afrc_coding_unit_size_t::bytes_24:
			modifier |= AFRC_FORMAT_MOD_CU_SIZE_P0(AFRC_FORMAT_MOD_CU_SIZE_24);
			break;
		case afrc_coding_unit_size_t::bytes_16:
			modifier |= AFRC_FORMAT_MOD_CU_SIZE_P0(AFRC_FORMAT_MOD_CU_SIZE_16);
			break;
		}

		switch (internal_format.get_afrc_chroma_coding_size())
		{
		case afrc_coding_unit_size_t::bytes_32:
			modifier |= AFRC_FORMAT_MOD_CU_SIZE_P12(AFRC_FORMAT_MOD_CU_SIZE_32);
			break;
		case afrc_coding_unit_size_t::bytes_24:
			modifier |= AFRC_FORMAT_MOD_CU_SIZE_P12(AFRC_FORMAT_MOD_CU_SIZE_24);
			break;
		case afrc_coding_unit_size_t::bytes_16:
			modifier |= AFRC_FORMAT_MOD_CU_SIZE_P12(AFRC_FORMAT_MOD_CU_SIZE_16);
			break;
		}
	}
	else
	{
		switch (internal_format.get_afrc_rgba_coding_size())
		{
		case afrc_coding_unit_size_t::bytes_32:
			modifier |= AFRC_FORMAT_MOD_CU_SIZE_P0(AFRC_FORMAT_MOD_CU_SIZE_32);
			break;
		case afrc_coding_unit_size_t::bytes_24:
			modifier |= AFRC_FORMAT_MOD_CU_SIZE_P0(AFRC_FORMAT_MOD_CU_SIZE_24);
			break;
		case afrc_coding_unit_size_t::bytes_16:
			modifier |= AFRC_FORMAT_MOD_CU_SIZE_P0(AFRC_FORMAT_MOD_CU_SIZE_16);
			break;
		}
	}

	return DRM_FORMAT_MOD_ARM_AFRC(modifier);
}

static uint64_t get_afbc_modifier_tags(const private_handle_t *hnd)
{
	const auto internal_format = hnd->alloc_format;
	if (!internal_format.is_afbc())
	{
		return 0;
	}

	uint64_t modifier = 0;

	if (internal_format.get_afbc_block_split())
	{
		modifier |= AFBC_FORMAT_MOD_SPLIT;
	}

	if (internal_format.get_afbc_tiled_headers())
	{
		modifier |= AFBC_FORMAT_MOD_TILED;

		/*
		 * For Mali GPUs, solid color (SC) block optimization is enabled together with tiled headers.
		 * For this reason, SC is not tracked separately with a dedicated get_afbc_sc method.
		 * Instead, the AFBC_FORMAT_MOD_SC modifier is reported here for formats using tiled headers.
		 * The logic below requires that all consumers can handle SC when tiled headers are enabled.
		 */
		const auto *info = internal_format.get_base_info();
		if (info != nullptr && !info->is_yuv && info->bpp_afbc[0] <= 64)
		{
			modifier |= AFBC_FORMAT_MOD_SC;
		}
	}

	if (internal_format.get_afbc_double_body())
	{
		modifier |= AFBC_FORMAT_MOD_DB;
	}

	if (internal_format.get_afbc_bch())
	{
		modifier |= AFBC_FORMAT_MOD_BCH;
	}

	if (internal_format.get_afbc_yuv_transform())
	{
		modifier |= AFBC_FORMAT_MOD_YTR;
	}

	if (internal_format.get_afbc_sparse())
	{
		modifier |= AFBC_FORMAT_MOD_SPARSE;
	}

	if (internal_format.get_afbc_usm())
	{
		modifier |= AFBC_FORMAT_MOD_USM;
	}

	/* Extract the block-size modifiers. */
	if (internal_format.get_afbc_32x8())
	{
		modifier |= (hnd->is_multi_plane() ? AFBC_FORMAT_MOD_BLOCK_SIZE_32x8_64x4 : AFBC_FORMAT_MOD_BLOCK_SIZE_32x8);
	}
	else if (internal_format.get_afbc_64x4())
	{
		modifier |= AFBC_FORMAT_MOD_BLOCK_SIZE_64x4;
	}
	else
	{
		modifier |= AFBC_FORMAT_MOD_BLOCK_SIZE_16x16;
	}

	return DRM_FORMAT_MOD_ARM_AFBC(modifier);
}

uint64_t drm_modifier_from_handle(const private_handle_t *hnd)
{
	auto alloc_format = hnd->alloc_format;
	if (alloc_format.is_afbc())
	{
		return get_afbc_modifier_tags(hnd);
	}
	else if (alloc_format.is_afrc())
	{
		return get_afrc_modifier_tags(hnd);
	}
	else if (alloc_format.is_block_linear())
	{
		return DRM_FORMAT_MOD_GENERIC_16_16_TILE;
	}
	return 0;
}

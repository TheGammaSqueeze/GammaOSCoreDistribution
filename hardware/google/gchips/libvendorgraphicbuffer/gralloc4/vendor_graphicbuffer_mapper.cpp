/*
 * Copyright (C) 2020 Samsung Electronics Co. Ltd.
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

#include <ui/Gralloc.h>
#include "VendorGraphicBuffer.h"
#include "mali_gralloc_formats.h"
#include "mali_gralloc_buffer.h"
#include "exynos_format.h"
#include "gralloc_helper.h"
#include <log/log.h>

using namespace android;
using namespace vendor::graphics;


status_t VendorGraphicBufferMapper::lock64(buffer_handle_t handle, uint64_t usage, const Rect& bounds,
	void** vaddr, int32_t* outBytesPerPixel, int32_t* outBytesPerStride)
{
	return lockAsync(handle, usage, usage, bounds, vaddr,
			-1, outBytesPerPixel, outBytesPerStride);
}

status_t VendorGraphicBufferMapper::lockYCbCr64(buffer_handle_t handle,
	uint64_t usage, const Rect& bounds, android_ycbcr *ycbcr)
{
	status_t err = getGrallocMapper().lock(handle, usage, bounds, -1, ycbcr);

	if (!(usage & VendorGraphicBufferUsage::VIDEO_PRIVATE_DATA))
		return err;

	/* TODO: clean this typecasting... */
	const private_handle_t * const hnd = (private_handle_t *)handle;

	switch (hnd->alloc_format & MALI_GRALLOC_INTFMT_FMT_MASK)
	{
	case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC:
	case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC:
			ycbcr->cb = (void *)(hnd->attr_base);
		break;
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC:
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC:
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50:
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75:
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40:
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60:
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80:
			ycbcr->cr = (void *)(hnd->attr_base);
		break;
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC:
			ycbcr->cr = (void *)(hnd->attr_base);
		break;
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC:
			ycbcr->cr = (void *)(hnd->attr_base);
		break;
	case HAL_PIXEL_FORMAT_YCrCb_420_SP:
			ycbcr->cr = (void *)(hnd->attr_base);
		break;
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50:
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75:
			ycbcr->cr = (void *)(hnd->attr_base);
		break;
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40:
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60:
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80:
			ycbcr->cr = (void *)(hnd->attr_base);
		break;
	case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M:
	case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL:
			ycbcr->cb = (void *)(hnd->attr_base);
		break;
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M:
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B:
			ycbcr->cr = (void *)(hnd->attr_base);
		break;
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN:
			ycbcr->cr = (void *)(hnd->attr_base);
		break;
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B:
			ycbcr->cr = (void *)(hnd->attr_base);
		break;
	case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M:
			ycbcr->cr = (void *)(hnd->attr_base);
		break;
	default:
		break;
	}

	return err;
}

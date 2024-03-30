/*
 * Copyright (C) 2020 Samsung Electronics Co. Ltd.
 * Copyright (C) 2021 Google LLC.
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

#if __has_include(<video/mfc_macros.h>)
#error "This header should not be included if mfc_macros is available in UAPI"
#endif

/* helper macros */
#ifndef __ALIGN_UP
#define __ALIGN_UP(x, a)					(((x) + ((a) - 1)) & ~((a) - 1))
#endif

/* SBWC align macros */
#define SBWC_8B_STRIDE(w)					(128 * (((w) + 31) / 32))
#define SBWC_10B_STRIDE(w)					(160 * (((w) + 31) / 32))
#define SBWC_HEADER_STRIDE(w)				((((((w) + 63) / 64) + 15) / 16) * 16)

#define SBWC_Y_VSTRIDE_BLOCKS(h)			((__ALIGN_UP((h), 16) + 3) / 4)
#define SBWC_CBCR_VSTRIDE_BLOCKS(h)			(((__ALIGN_UP((h), 16) / 2) + 3) / 4)

#define SBWC_8B_Y_SIZE(w, h)				((SBWC_8B_STRIDE(w) * ((__ALIGN_UP((h), 16) + 3) / 4)) + 64)
#define SBWC_8B_Y_HEADER_SIZE(w, h)			__ALIGN_UP(((SBWC_HEADER_STRIDE(w) * ((__ALIGN_UP((h), 16) + 3) / 4)) + 256), 32)
#define SBWC_8B_CBCR_SIZE(w, h)				((SBWC_8B_STRIDE(w) * (((__ALIGN_UP((h), 16) / 2) + 3) / 4)) + 64)
#define SBWC_8B_CBCR_HEADER_SIZE(w, h)		((SBWC_HEADER_STRIDE(w) * (((__ALIGN_UP((h), 16) / 2) + 3) / 4)) + 128)

#define SBWC_10B_Y_SIZE(w, h)				((SBWC_10B_STRIDE(w) * ((__ALIGN_UP((h), 16) + 3) / 4)) + 64)
#define SBWC_10B_Y_HEADER_SIZE(w, h)		__ALIGN_UP((((__ALIGN_UP((w), 32) * __ALIGN_UP((h), 16) * 2) + 256) - SBWC_10B_Y_SIZE(w, h)), 32)
#define SBWC_10B_CBCR_SIZE(w, h)			((SBWC_10B_STRIDE(w) * (((__ALIGN_UP((h), 16) / 2) + 3) / 4)) + 64)
#define SBWC_10B_CBCR_HEADER_SIZE(w, h)		(((__ALIGN_UP((w), 32) * __ALIGN_UP((h), 16)) + 256) - SBWC_10B_CBCR_SIZE(w, h))

/* SBWC Lossy align macros */
#define SBWCL_8B_STRIDE(w, r)				(((128 * (r)) / 100) * (((w) + 31) / 32))
#define SBWCL_10B_STRIDE(w, r)				(((160 * (r)) / 100) * (((w) + 31) / 32))

#define SBWCL_8B_Y_SIZE(w, h, r)			((SBWCL_8B_STRIDE(w, r) * ((__ALIGN_UP((h), 8) + 3) / 4)) + 64)
#define SBWCL_8B_CBCR_SIZE(w, h, r)			((SBWCL_8B_STRIDE(w, r) * (((__ALIGN_UP((h), 8) / 2) + 3) / 4)) + 64)

#define SBWCL_10B_Y_SIZE(w, h, r)			((SBWCL_10B_STRIDE(w, r) * ((__ALIGN_UP((h), 8) + 3) / 4)) + 64)
#define SBWCL_10B_CBCR_SIZE(w, h, r)		((SBWCL_10B_STRIDE(w, r) * (((__ALIGN_UP((h), 8) / 2) + 3) / 4)) + 64)

#define SBWCL_8B_CBCR_BASE(base, w, h, r)	((base) + SBWCL_8B_Y_SIZE(w, h, r))
#define SBWCL_10B_CBCR_BASE(base, w, h, r)	((base) + SBWCL_10B_Y_SIZE(w, h, r))


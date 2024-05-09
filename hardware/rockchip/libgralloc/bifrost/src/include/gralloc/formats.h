/*
 * Copyright (C) 2016-2020, 2022 ARM Limited. All rights reserved.
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

#include <system/graphics.h>
#include <hardware/hardware_rockchip.h>

#include "log.h"

/* Defined in aidl/android/hardware/graphics/common/PixelFormat.aidl */
#define PIXEL_FORMAT_R8 56

/**
 * @brief Integer type that matches the type of Android's PixelFormat
 */
typedef int32_t mali_gralloc_android_format;

/**
 * @brief Internal format used inside Gralloc
 *
 * The internal Gralloc format extends the Android HAL format by including modifiers that are specific to Mali
 * and therefore identify more narrowly the layout of data in the graphic buffer.
 * The format is stored in a 32-bit integer in a way that allows to easily discriminate whether the value is
 * a regular HAL format or a - so called - Gralloc "private" format, see mali_gralloc_format_is_private()
 * and mali_gralloc_pixel_format.
 */
typedef uint32_t mali_gralloc_internal_format;

/**
 * @brief Base formats that do not have an identical HAL match are defined starting at the Android private range.
 */
#define MALI_GRALLOC_FORMAT_INTERNAL_RANGE_BASE 0x100

/**
 * @brief Internal formats defined to either match HAL_PIXEL_FORMAT_* or extend where missing.
 *
 * Private formats can be used where no CPU usage is requested. All pixel formats in this list must explicitly define
 * a strict memory layout which can be allocated and used by producer(s) and consumer(s).
 * Flex formats are therefore not included and will be mapped to suitable internal formats.
 */
typedef enum
{
	/* Internal definitions for HAL formats. */
	MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED = 0,
	MALI_GRALLOC_FORMAT_INTERNAL_RGBA_8888 = HAL_PIXEL_FORMAT_RGBA_8888,
	MALI_GRALLOC_FORMAT_INTERNAL_RGBX_8888 = HAL_PIXEL_FORMAT_RGBX_8888,
	MALI_GRALLOC_FORMAT_INTERNAL_RGB_888 = HAL_PIXEL_FORMAT_RGB_888,
	MALI_GRALLOC_FORMAT_INTERNAL_BGR_888 = HAL_PIXEL_FORMAT_BGR_888,
	MALI_GRALLOC_FORMAT_INTERNAL_RGB_565 = HAL_PIXEL_FORMAT_RGB_565,
	MALI_GRALLOC_FORMAT_INTERNAL_BGRA_8888 = HAL_PIXEL_FORMAT_BGRA_8888,
	MALI_GRALLOC_FORMAT_INTERNAL_RGBA_1010102 = HAL_PIXEL_FORMAT_RGBA_1010102,
	/* 16-bit floating point format. */
	MALI_GRALLOC_FORMAT_INTERNAL_RGBA_16161616 = HAL_PIXEL_FORMAT_RGBA_FP16,
	MALI_GRALLOC_FORMAT_INTERNAL_NV16 = HAL_PIXEL_FORMAT_YCbCr_422_SP,

	/* Camera specific HAL formats */
	MALI_GRALLOC_FORMAT_INTERNAL_RAW16 = HAL_PIXEL_FORMAT_RAW16,
	MALI_GRALLOC_FORMAT_INTERNAL_RAW12 = HAL_PIXEL_FORMAT_RAW12,
	MALI_GRALLOC_FORMAT_INTERNAL_RAW10 = HAL_PIXEL_FORMAT_RAW10,
	MALI_GRALLOC_FORMAT_INTERNAL_RAW_OPAQUE = HAL_PIXEL_FORMAT_RAW_OPAQUE,
	MALI_GRALLOC_FORMAT_INTERNAL_BLOB = HAL_PIXEL_FORMAT_BLOB,

	/* Depth and stencil formats */
	MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_16 = HAL_PIXEL_FORMAT_DEPTH_16,
	MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24 = HAL_PIXEL_FORMAT_DEPTH_24,
	MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_24_STENCIL_8 = HAL_PIXEL_FORMAT_DEPTH_24_STENCIL_8,
	MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F = HAL_PIXEL_FORMAT_DEPTH_32F,
	MALI_GRALLOC_FORMAT_INTERNAL_DEPTH_32F_STENCIL_8 = HAL_PIXEL_FORMAT_DEPTH_32F_STENCIL_8,
	MALI_GRALLOC_FORMAT_INTERNAL_STENCIL_8 = HAL_PIXEL_FORMAT_STENCIL_8,

	/* Flexible YUV formats would be parsed but not have any representation as
	 * internal format itself but one of the ones below.
	 */

	/* The internal private formats that have no HAL equivivalent are defined
	 * afterwards starting at a specific base range.
	 */
	MALI_GRALLOC_FORMAT_INTERNAL_NV12 = MALI_GRALLOC_FORMAT_INTERNAL_RANGE_BASE,
	MALI_GRALLOC_FORMAT_INTERNAL_NV21,
	MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT,

	/* Extended YUV formats. */
	MALI_GRALLOC_FORMAT_INTERNAL_Y0L2,
	MALI_GRALLOC_FORMAT_INTERNAL_P010,
	MALI_GRALLOC_FORMAT_INTERNAL_P210,
	MALI_GRALLOC_FORMAT_INTERNAL_Y210,
	MALI_GRALLOC_FORMAT_INTERNAL_Y410,
	MALI_GRALLOC_FORMAT_INTERNAL_YU12,
	MALI_GRALLOC_FORMAT_INTERNAL_YUV444,
	MALI_GRALLOC_FORMAT_INTERNAL_Q410,
	MALI_GRALLOC_FORMAT_INTERNAL_Q401,

	/*
	 * Single-plane (I = interleaved) variants of 8/10-bit YUV formats,
	 * where previously not defined.
	 */
	MALI_GRALLOC_FORMAT_INTERNAL_YUV420_8BIT_I,
	MALI_GRALLOC_FORMAT_INTERNAL_YUV420_10BIT_I,

	/* The three formats below are remapped version of the corresponding HAL formats.
	 * We remap these formats as they have large numerical values that do not fit
	 * in the space reserved in the internal Gralloc representation for formats.
	 */
	MALI_GRALLOC_FORMAT_INTERNAL_YV12,
	MALI_GRALLOC_FORMAT_INTERNAL_Y8,
	MALI_GRALLOC_FORMAT_INTERNAL_Y16,

	MALI_GRALLOC_FORMAT_INTERNAL_RGBA_10101010,
	MALI_GRALLOC_FORMAT_INTERNAL_NV15,
	MALI_GRALLOC_FORMAT_INTERNAL_NV24,
	MALI_GRALLOC_FORMAT_INTERNAL_NV30,

	MALI_GRALLOC_FORMAT_INTERNAL_R8 = PIXEL_FORMAT_R8,

	MALI_GRALLOC_FORMAT_INTERNAL_RANGE_LAST,
} mali_gralloc_pixel_format;

/* Internal format masks */
#define MALI_GRALLOC_INTFMT_FMT_MASK 0x000001ffULL
#define MALI_GRALLOC_INTFMT_EXT_MASK 0xffff0000ULL

/* Format Modifier Bits Locations */
#define MALI_GRALLOC_INTFMT_EXTENSION_BIT_START 16

/* Utility used to define macros below */
#define MALI_GRALLOC_INTFMT_EXTENSION_BIT(num_bit) \
	(static_cast<mali_gralloc_internal_format>(1) << (MALI_GRALLOC_INTFMT_EXTENSION_BIT_START + (num_bit)))

/*
 * Compression type
 */

/* Bit used to discriminate between regular HAL formats and internal Gralloc formats.
 * When the sentinel bit is set, Gralloc treats the format as an internal format.
 */
#define MALI_GRALLOC_INTFMT_SENTINEL MALI_GRALLOC_INTFMT_EXTENSION_BIT(-1)

/* This format will use AFBC */
#define MALI_GRALLOC_INTFMT_AFBC_BASIC MALI_GRALLOC_INTFMT_EXTENSION_BIT(0)

/* This format will use AFRC */
#define MALI_GRALLOC_INTFMT_AFRC_BASIC MALI_GRALLOC_INTFMT_EXTENSION_BIT(1)

/* This format will use Block Linear */
#define MALI_GRALLOC_INTFMT_BLOCK_LINEAR_BASIC MALI_GRALLOC_INTFMT_EXTENSION_BIT(2)

/*
 * AFBC modifier bits (valid with MALI_GRALLOC_INTFMT_AFBC_BASIC)
 */

/*
 * AFBC modifiers affecting the layout of the buffer.
 */

/* This format uses AFBC split block mode */
#define MALI_GRALLOC_INTFMT_AFBC_SPLITBLK MALI_GRALLOC_INTFMT_EXTENSION_BIT(3)

/* This format uses AFBC wide block mode */
#define MALI_GRALLOC_INTFMT_AFBC_WIDEBLK MALI_GRALLOC_INTFMT_EXTENSION_BIT(4)

/* This format uses AFBC tiled headers */
#define MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS MALI_GRALLOC_INTFMT_EXTENSION_BIT(5)

/* This format uses AFBC extra wide superblocks. */
#define MALI_GRALLOC_INTFMT_AFBC_EXTRAWIDEBLK MALI_GRALLOC_INTFMT_EXTENSION_BIT(6)

/* This format is AFBC with double body buffer (used as a frontbuffer) */
#define MALI_GRALLOC_INTFMT_AFBC_DOUBLE_BODY MALI_GRALLOC_INTFMT_EXTENSION_BIT(7)

/* This format uses AFBC buffer content hints in LSB of superblock offset. */
#define MALI_GRALLOC_INTFMT_AFBC_BCH MALI_GRALLOC_INTFMT_EXTENSION_BIT(8)

/* This format uses AFBC with YUV transform. */
#define MALI_GRALLOC_INTFMT_AFBC_YUV_TRANSFORM MALI_GRALLOC_INTFMT_EXTENSION_BIT(9)

/* This format uses Sparse allocated AFBC. */
#define MALI_GRALLOC_INTFMT_AFBC_SPARSE MALI_GRALLOC_INTFMT_EXTENSION_BIT(10)

/* This format uses USM (uncompressed) AFBC. */
#define MALI_GRALLOC_INTFMT_AFBC_USM MALI_GRALLOC_INTFMT_EXTENSION_BIT(11)

/* This mask should be used to check or clear support for AFBC for an internal format.
 */
#define MALI_GRALLOC_INTFMT_AFBCENABLE_MASK MALI_GRALLOC_INTFMT_AFBC_BASIC

/*
 * AFRC modifier bits (valid with MALI_GRALLOC_INTFMT_AFRC_BASIC)
 */

/* This mask should be used to check or clear support for AFRC for an internal format.
 */
#define MALI_GRALLOC_INTFMT_AFRCENABLE_MASK MALI_GRALLOC_INTFMT_AFRC_BASIC

/*
 * This format uses a memory layout capable of inline rotation.
 * If this is unspecified, the format will use a scanline access optimized (linear) memory layout instead.
 * The memory layout is the same for all planes.
 */
#define MALI_GRALLOC_INTFMT_AFRC_ROT_LAYOUT MALI_GRALLOC_INTFMT_EXTENSION_BIT(3)

/* Available options for coding unit size.  */
#define MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_16 0U
#define MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_24 1U
#define MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_32 2U
#define MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_MASK 3U
#define MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_UNWRAP(size) (16 + 8 * (size))

/*
 * This format uses 24/32 bytes for the RGBA plane coding unit size.
 * If this is unspecified, the format will use 16 bytes for the RGBA plane coding unit size.
 */
#define MALI_GRALLOC_INTFMT_AFRC_RGBA_CODING_UNIT_BYTES_SHIFT (MALI_GRALLOC_INTFMT_EXTENSION_BIT_START + 4)
#define MALI_GRALLOC_INTFMT_AFRC_RGBA_CODING_UNIT_BYTES(x) \
	((x) << (MALI_GRALLOC_INTFMT_AFRC_RGBA_CODING_UNIT_BYTES_SHIFT))

/*
 * This format uses 24/32 bytes for the luminance (Y) plane coding unit size.
 * If this is unspecified, the format will use 16 bytes for the luminance plane coding unit size.
 */
#define MALI_GRALLOC_INTFMT_AFRC_LUMA_CODING_UNIT_BYTES_SHIFT MALI_GRALLOC_INTFMT_AFRC_RGBA_CODING_UNIT_BYTES_SHIFT
#define MALI_GRALLOC_INTFMT_AFRC_LUMA_CODING_UNIT_BYTES(x) MALI_GRALLOC_INTFMT_AFRC_RGBA_CODING_UNIT_BYTES(x)

/*
 * This format uses 24/32 bytes for the coding unit size for the chrominance (U and V) planes.
 * If this is unspecified, the format will use 16 bytes for the coding unit size for the U and V planes.
 */
#define MALI_GRALLOC_INTFMT_AFRC_CHROMA_CODING_UNIT_BYTES_SHIFT (MALI_GRALLOC_INTFMT_EXTENSION_BIT_START + 6)
#define MALI_GRALLOC_INTFMT_AFRC_CHROMA_CODING_UNIT_BYTES(x) \
	((x) << (MALI_GRALLOC_INTFMT_AFRC_CHROMA_CODING_UNIT_BYTES_SHIFT))

/*
 * Bit 8 is unused for AFRC.
 */
#define MALI_GRALLOC_INTFMT_RESERVED_BIT_FOR_EXTRA_AFRC_FLAG MALI_GRALLOC_INTFMT_EXTENSION_BIT(8)

/*
 * Avoid using us directly; use the helper macros defined below instead.
 */
#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC(x, modifiers) \
	mali_gralloc_format_wrapper((x), (MALI_GRALLOC_INTFMT_AFRC_BASIC | (modifiers)))

/*
 * Helper macros for marking/wrapping base formats as AFRC-encoded formats.
 *
 * Example usage:
 *
 * int private_format = GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC_YUV_ROT(
 *     HAL_PIXEL_FORMAT_NV12,
 *     MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_24
 *     MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_16
 * );
 */
#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC_DEFAULT(hal_format) GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC(hal_format, 0)

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC_RGBA_SCAN(hal_format, afrc_rgba_coding_unit_bytes) \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC(hal_format,                                            \
	                                    MALI_GRALLOC_INTFMT_AFRC_RGBA_CODING_UNIT_BYTES(afrc_rgba_coding_unit_bytes))

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC_RGBA_ROT(hal_format, afrc_rgba_coding_unit_bytes) \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC(                                                      \
	    hal_format, MALI_GRALLOC_INTFMT_AFRC_ROT_LAYOUT |                                     \
	                    MALI_GRALLOC_INTFMT_AFRC_RGBA_CODING_UNIT_BYTES(afrc_rgba_coding_unit_bytes))

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC_YUV_SCAN(hal_format, afrc_luma_coding_unit_bytes,      \
                                                     afrc_chroma_coding_unit_bytes)                \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC(                                                           \
	    hal_format, MALI_GRALLOC_INTFMT_AFRC_LUMA_CODING_UNIT_BYTES(afrc_luma_coding_unit_bytes) | \
	                    MALI_GRALLOC_INTFMT_AFRC_CHROMA_CODING_UNIT_BYTES(afrc_chroma_coding_unit_bytes))

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC_YUV_ROT(hal_format, afrc_luma_coding_unit_bytes,           \
                                                    afrc_chroma_coding_unit_bytes)                     \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_AFRC(                                                               \
	    hal_format, MALI_GRALLOC_INTFMT_AFRC_ROT_LAYOUT |                                              \
	                    MALI_GRALLOC_INTFMT_AFRC_LUMA_CODING_UNIT_BYTES(afrc_luma_coding_unit_bytes) | \
	                    MALI_GRALLOC_INTFMT_AFRC_CHROMA_CODING_UNIT_BYTES(afrc_chroma_coding_unit_bytes))

/**
 * @brief Internal function that remaps some HAL formats to ensure they fit in the mask @c MALI_GRALLOC_INTFMT_FMT_MASK
 *
 * @param hal_format A HAL format or a member of the mali_gralloc_pixel_format enumeration.
 * @return A hal_format that fits in the mask @c MALI_GRALLOC_INTFMT_FMT_MASK
 */
static inline mali_gralloc_android_format mali_gralloc_format_compress(mali_gralloc_android_format hal_format)
{
	switch (hal_format)
	{
	case HAL_PIXEL_FORMAT_YV12:
		return MALI_GRALLOC_FORMAT_INTERNAL_YV12;
	case HAL_PIXEL_FORMAT_Y8:
		return MALI_GRALLOC_FORMAT_INTERNAL_Y8;
	case HAL_PIXEL_FORMAT_Y16:
		return MALI_GRALLOC_FORMAT_INTERNAL_Y16;
	default:
		break;
	}
	if ((hal_format & ~MALI_GRALLOC_INTFMT_FMT_MASK) != 0)
	{
		/* Formats greater than the wrap mask are meant to be intercepted by the switch above: new format? */
		MALI_GRALLOC_LOGE("Cannot compress HAL format %d", hal_format);
		return MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED;
	}
	return hal_format;
}

/* @brief Support macro that packs modifier bits together with base format into a 32 bit format identifier.
 *
 * Packing:
 *
 * Bits 15-0:    mali_gralloc_pixel_format format
 * Bits 31-16:   modifier bits
 */
static inline mali_gralloc_android_format mali_gralloc_format_wrapper(mali_gralloc_android_format base_format,
                                                                      mali_gralloc_internal_format modifiers)
{
	if (modifiers & ~MALI_GRALLOC_INTFMT_EXT_MASK)
	{
		MALI_GRALLOC_LOGE("Invalid modifiers %x for private format wrapping", (int)modifiers);
		return MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED;
	}
	mali_gralloc_android_format packed_format = mali_gralloc_format_compress(base_format);
	return modifiers | packed_format | MALI_GRALLOC_INTFMT_SENTINEL;
}

#if defined(GRALLOC_USE_PRIVATE_FORMATS) && GRALLOC_USE_PRIVATE_FORMATS
/**
 * @brief Check whether the format is a private format
 *
 * @param wrapped_format An Android pixel format or a value obtained using mali_gralloc_format_wrapper().
 * @return true The format is a private format.
 * @return false The format is a regular Android HAL format.
 */
static inline bool mali_gralloc_format_is_private(mali_gralloc_android_format android_format)
{
	return (android_format & MALI_GRALLOC_INTFMT_SENTINEL) != 0;
}
#endif

/**
 * @brief Extract the base format in a 32-bit format obtained using mali_gralloc_format_wrapper().
 *
 * @param wrapped_format A format produced using the mali_gralloc_format_wrapper() function.
 * @return Return the base format with the modifiers removed.
 */
static inline mali_gralloc_android_format mali_gralloc_format_get_base(mali_gralloc_android_format wrapped_format)
{
	return wrapped_format & MALI_GRALLOC_INTFMT_FMT_MASK;
}

/**
 * @brief Extract the modifiers in a 32-bit format obtained using mali_gralloc_format_wrapper().
 *
 * @param wrapped_format A format produced using the mali_gralloc_format_wrapper() function.
 * @return Return the modifiers bits packed into the @p wrapped_format.
 */
static inline mali_gralloc_internal_format mali_gralloc_format_get_modifiers(mali_gralloc_android_format wrapped_format)
{
	return wrapped_format & MALI_GRALLOC_INTFMT_EXT_MASK;
}

static inline bool mali_gralloc_format_is_uncompressed(mali_gralloc_internal_format format)
{
	return mali_gralloc_format_get_modifiers(format) == 0;
}

static inline bool mali_gralloc_format_is_afbc(mali_gralloc_internal_format format)
{
	return (format & MALI_GRALLOC_INTFMT_AFBCENABLE_MASK) != 0;
}

static inline bool mali_gralloc_format_is_afrc(mali_gralloc_internal_format format)
{
	return (format & MALI_GRALLOC_INTFMT_AFRCENABLE_MASK) != 0;
}

static inline bool mali_gralloc_format_is_block_linear(mali_gralloc_internal_format format)
{
	return (format & MALI_GRALLOC_INTFMT_BLOCK_LINEAR_BASIC) != 0;
}

/*
 * Macro to add additional modifier(s) to existing wrapped private format.
 * Arguments include wrapped private format and new modifier(s) to add.
 */
#define GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(x, modifiers) ((int)((x) | (unsigned)(modifiers)))

/*
 * Macro to remove modifier(s) to existing wrapped private format.
 * Arguments include wrapped private format and modifier(s) to remove.
 */
#define GRALLOC_PRIVATE_FORMAT_WRAPPER_REMOVE_MODIFIER(x, modifiers) \
	((int)((x) & ~((unsigned)(modifiers)))

#define GRALLOC_PRIVATE_FORMAT_WRAPPER(x) (mali_gralloc_format_wrapper(x, 0))

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_BLOCK_LINEAR(x) \
	mali_gralloc_format_wrapper(x, MALI_GRALLOC_INTFMT_BLOCK_LINEAR_BASIC)

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC(x) \
	mali_gralloc_format_wrapper(x, (MALI_GRALLOC_INTFMT_AFBC_BASIC | MALI_GRALLOC_INTFMT_AFBC_SPARSE))

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_SPLITBLK(x)                                 \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC(x), \
	                                            MALI_GRALLOC_INTFMT_AFBC_SPLITBLK)

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_WIDEBLK(x)                                  \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC(x), \
	                                            MALI_GRALLOC_INTFMT_AFBC_WIDEBLK)

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_WIDE_SPLIT(x)                                        \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_SPLITBLK(x), \
	                                            MALI_GRALLOC_INTFMT_AFBC_WIDEBLK)

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_TILED_HEADERS_BASIC(x)                      \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC(x), \
	                                            MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS)

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_TILED_HEADERS_WIDE(x)                               \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_WIDEBLK(x), \
	                                            MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS)

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_TILED_HEADERS_SPLIT(x)                               \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_SPLITBLK(x), \
	                                            MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS)

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_TILED_HEADERS_WIDE_SPLIT(x)                            \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_WIDE_SPLIT(x), \
	                                            MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS)

/*
 * AFBC format with extra-wide (64x4) superblocks.
 *
 * NOTE: Tiled headers are mandatory for this format.
 */
#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_EXTRAWIDEBLK(x)                                                 \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_TILED_HEADERS_BASIC(x), \
	                                            MALI_GRALLOC_INTFMT_AFBC_EXTRAWIDEBLK)

/*
 * AFBC multi-plane YUV format where luma (wide, 32x8) and
 * chroma (extra-wide, 64x4) planes are stored in separate AFBC buffers.
 *
 * NOTE: Tiled headers are mandatory for this format.
 * NOTE: Base format (x) must be a multi-plane YUV format.
 */
#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_WIDE_EXTRAWIDE(x)                                        \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_EXTRAWIDEBLK(x), \
	                                            MALI_GRALLOC_INTFMT_AFBC_WIDEBLK)

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_TILED_DOUBLE_BODY(x)                                            \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_TILED_HEADERS_BASIC(x), \
	                                            MALI_GRALLOC_INTFMT_AFBC_DOUBLE_BODY)

#define GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_TILED_SPLIT_DOUBLE_BODY(x)                                      \
	GRALLOC_PRIVATE_FORMAT_WRAPPER_ADD_MODIFIER(GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_TILED_HEADERS_SPLIT(x), \
	                                            MALI_GRALLOC_INTFMT_AFBC_DOUBLE_BODY)

/* Gralloc IP bit flags. Used to indicate the producers or consumers in various Gralloc functions */
typedef uint32_t mali_gralloc_ip;
const mali_gralloc_ip MALI_GRALLOC_IP_NONE = 0;
const mali_gralloc_ip MALI_GRALLOC_IP_CPU = 1 << 0;
const mali_gralloc_ip MALI_GRALLOC_IP_GPU = 1 << 1;
const mali_gralloc_ip MALI_GRALLOC_IP_DPU = 1 << 2;
const mali_gralloc_ip MALI_GRALLOC_IP_DPU_AEU = 1 << 3;
const mali_gralloc_ip MALI_GRALLOC_IP_VPU = 1 << 4;
const mali_gralloc_ip MALI_GRALLOC_IP_CAM = 1 << 5;

/**
 * @brief Checks whether a particular capability feature has been enabled for that IP in Gralloc.
 *
 * @note This type can be used by code external to Gralloc for testing purposes.
 *
 * @param producers    Gralloc producers
 * @param consumers    Gralloc consumers
 * @param feature_name Name of the capability feature
 */
typedef bool (*mali_gralloc_ip_supports_feature_ptr)(mali_gralloc_ip producers, mali_gralloc_ip consumers,
                                                     const char *feature_name);

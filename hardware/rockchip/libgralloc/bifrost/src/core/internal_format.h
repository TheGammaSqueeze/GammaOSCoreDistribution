/*
 * Copyright (C) 2022 Arm Limited. All rights reserved.
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

/**
 * @brief This file provides the internal_format_t type.
 */

#include <stdint.h>
#include <string>
#include <ostream>
#include <sstream>

#include "gralloc/formats.h"

struct format_info_t;

/**
 * @brief Coding size to be used for AFRC compression formats.
 */
enum class afrc_coding_unit_size_t : mali_gralloc_internal_format
{
	bytes_16 = MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_16,
	bytes_24 = MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_24,
	bytes_32 = MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_32
};

/**
 * @brief Obtain the number of bytes for the specified @c afrc_coding_unit_size_t enum.
 * @param size A @c afrc_coding_unit_size_t value.
 * @return An integer containing the number of bytes for @p size.
 */
inline unsigned int to_bytes(afrc_coding_unit_size_t size)
{
	return MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_UNWRAP(static_cast<mali_gralloc_internal_format>(size));
}

/**
 * @brief Type used internally by Gralloc to identify Gralloc buffer formats.
 *
 * This type is meant to abstract the representation of formats. The main aim of this data structure is to
 * prevent Gralloc's implementation from having to know how formats are internally represented, e.g. via bit
 * fields in unsigned integers.
 *
 * internal_format_t also allows extending the internal representation of formats without having to modify
 * the allocation logic for existing formats. The datastructure also offers some type safety as opposed to
 * using integers that can be victim of implicit casts.
 *
 * internal_format_t has:
 * - a format base (see internal_format_t::get_base) which roughly stores the same information stored by an
 *   Android's PixelFormat enum
 * - some modifiers bits, which store GPU specific information on top of the base format.
 *
 * Modifier information should be accessed by the dedicated method of this class, e.g. is_afbc, make_afbc.
 */
class internal_format_t
{
public:
	/**
	 * @brief The invalid format.
	 * @note The invalid format can be used as a return value to indicate failure, for example.
	 */
	static const internal_format_t invalid;

	/**
	 * @brief Create an internal format from a standard Android's PixelFormat.
	 *
	 * @param android_format An Android PixelFormat. This is one of the formats defined in the Android
	 *   framework or a format in the vendor-reserved space 0x100 - 0x1ff.
	 *
	 * @return An internal format with no modifiers, i.e. for which the method has_modifiers() returns
	 *   @c false. If @p android_format is invalid, this function returns @c internal_format_t::invalid
	 */
	static internal_format_t from_android(mali_gralloc_android_format android_format)
	{
		auto ret = internal_format_t(android_format);
		CHECK(!ret.has_modifiers()) << "invalid format: " << std::showbase << std::hex << android_format;
		return ret;
	}

	/**
	 * @brief Create an internal format from a private format.
	 *
	 * @note Private formats are a testing feature of the Arm reference Gralloc. Private formats extend
	 *   Android's PixelFormat enumeration and use the underlying 32-bit integer to pass extra information.
	 *   Private formats allow to bypass the normal allocation logic in the Arm reference Gralloc and
	 *   select a precise format supported by the GPU. This is intended to be used for testing.
	 *
	 * @param private_format A private format. This is a format generated using macros in formats.h like
	 *   @c GRALLOC_PRIVATE_FORMAT_WRAPPER
	 *
	 * @return An internal format with the modifiers set as encoded in the private format.
	 *   If @p private_format is invalid this function returns @c internal_format_t::invalid
	 */
	static internal_format_t from_private(mali_gralloc_android_format private_format);

	/**
	 * @brief Construct an invalid format.
	 */
	constexpr internal_format_t() = default;

	mali_gralloc_android_format get_base() const;

	const format_info_t *get_base_info() const;

	const format_info_t &base_info() const;

	/**
	 * @brief Get the modifiers as defined in gralloc/format.h
	 * @return An unsigned integer with the modifers only.
	 * @note You should use the get methods (e.g. is_afrc) if possible, rather than using this method.
	 */
	mali_gralloc_internal_format get_modifiers() const
	{
		return m_modifiers;
	}

	/**
	 * @brief Whether this format has some modifiers set.
	 */
	bool has_modifiers() const
	{
		return get_modifiers() != 0;
	}

	/**
	 * @brief Clear the modifiers of the format, thus making the format a linear format.
	 */
	void clear_modifiers()
	{
		m_modifiers = 0;
	}

	bool is_undefined() const
	{
		return m_format == MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED;
	}

	void make_afbc()
	{
		DCHECK(!has_modifiers());
		set_modifier(MALI_GRALLOC_INTFMT_AFBC_BASIC, true);
	}
	bool is_afbc() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFBC_BASIC);
	}

	void set_afbc_32x8(bool value = true)
	{
		set_modifier(MALI_GRALLOC_INTFMT_AFBC_WIDEBLK, value);
	}
	bool get_afbc_32x8() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFBC_WIDEBLK);
	}

	void set_afbc_64x4(bool value = true)
	{
		set_modifier(MALI_GRALLOC_INTFMT_AFBC_EXTRAWIDEBLK, value);
	}
	bool get_afbc_64x4() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFBC_EXTRAWIDEBLK);
	}

	void set_afbc_yuv_transform(bool value = true)
	{
		set_modifier(MALI_GRALLOC_INTFMT_AFBC_YUV_TRANSFORM, value);
	}
	bool get_afbc_yuv_transform() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFBC_YUV_TRANSFORM);
	}

	void set_afbc_sparse(bool value = true)
	{
		set_modifier(MALI_GRALLOC_INTFMT_AFBC_SPARSE, value);
	}
	bool get_afbc_sparse() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFBC_SPARSE);
	}

	void set_afbc_tiled_headers(bool value = true)
	{
		set_modifier(MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS, value);
	}
	bool get_afbc_tiled_headers() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS);
	}

	void set_afbc_double_body(bool value = true)
	{
		set_modifier(MALI_GRALLOC_INTFMT_AFBC_DOUBLE_BODY, value);
	}
	bool get_afbc_double_body() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFBC_DOUBLE_BODY);
	}

	void set_afbc_block_split(bool value = true)
	{
		set_modifier(MALI_GRALLOC_INTFMT_AFBC_SPLITBLK, value);
	}
	bool get_afbc_block_split() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFBC_SPLITBLK);
	}

	void set_afbc_bch(bool value = true)
	{
		set_modifier(MALI_GRALLOC_INTFMT_AFBC_BCH, value);
	}
	bool get_afbc_bch() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFBC_BCH);
	}

	void set_afbc_usm(bool value = true)
	{
		set_modifier(MALI_GRALLOC_INTFMT_AFBC_USM, value);
	}
	bool get_afbc_usm() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFBC_USM);
	}

	void make_afrc()
	{
		DCHECK(!has_modifiers());
		set_modifier(MALI_GRALLOC_INTFMT_AFRC_BASIC, true);
	}
	bool is_afrc() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFRC_BASIC);
	}

	void set_afrc_rot_layout(bool value = true)
	{
		set_modifier(MALI_GRALLOC_INTFMT_AFRC_ROT_LAYOUT, value);
	}
	bool get_afrc_rot_layout() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_AFRC_ROT_LAYOUT);
	}

	void make_block_linear()
	{
		DCHECK(!has_modifiers());
		set_modifier(MALI_GRALLOC_INTFMT_BLOCK_LINEAR_BASIC, true);
	}
	bool is_block_linear() const
	{
		return get_modifier(MALI_GRALLOC_INTFMT_BLOCK_LINEAR_BASIC);
	}

	void set_afrc_rgba_coding_size(afrc_coding_unit_size_t size)
	{
		m_modifiers &= ~MALI_GRALLOC_INTFMT_AFRC_RGBA_CODING_UNIT_BYTES(MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_MASK);
		m_modifiers |= MALI_GRALLOC_INTFMT_AFRC_RGBA_CODING_UNIT_BYTES(static_cast<mali_gralloc_internal_format>(size));
	}

	afrc_coding_unit_size_t get_afrc_rgba_coding_size() const
	{
		return static_cast<afrc_coding_unit_size_t>(
		    (m_modifiers >> MALI_GRALLOC_INTFMT_AFRC_RGBA_CODING_UNIT_BYTES_SHIFT) &
		    MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_MASK);
	}

	void set_afrc_luma_coding_size(afrc_coding_unit_size_t size)
	{
		m_modifiers &= ~MALI_GRALLOC_INTFMT_AFRC_LUMA_CODING_UNIT_BYTES(MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_MASK);
		m_modifiers |= MALI_GRALLOC_INTFMT_AFRC_LUMA_CODING_UNIT_BYTES(static_cast<mali_gralloc_internal_format>(size));
	}

	afrc_coding_unit_size_t get_afrc_luma_coding_size() const
	{
		return static_cast<afrc_coding_unit_size_t>(
		    (m_modifiers >> MALI_GRALLOC_INTFMT_AFRC_LUMA_CODING_UNIT_BYTES_SHIFT) &
		    MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_MASK);
	}

	void set_afrc_chroma_coding_size(afrc_coding_unit_size_t size)
	{
		m_modifiers &=
			~MALI_GRALLOC_INTFMT_AFRC_CHROMA_CODING_UNIT_BYTES(MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_MASK);
		m_modifiers |=
			MALI_GRALLOC_INTFMT_AFRC_CHROMA_CODING_UNIT_BYTES(static_cast<mali_gralloc_internal_format>(size));
	}

	afrc_coding_unit_size_t get_afrc_chroma_coding_size() const
	{
		return static_cast<afrc_coding_unit_size_t>(
		    (m_modifiers >> MALI_GRALLOC_INTFMT_AFRC_CHROMA_CODING_UNIT_BYTES_SHIFT) &
		    MALI_GRALLOC_INTFMT_AFRC_CODING_UNIT_BYTES_MASK);
	}

	std::string str() const
	{
		std::ostringstream out;
		out << *this;
		return out.str();
	}

	bool is_equal(internal_format_t other) const
	{
		return other.m_format == m_format && other.m_modifiers == m_modifiers;
	}

private:
	explicit internal_format_t(mali_gralloc_internal_format value)
		: m_format(mali_gralloc_format_get_base(value))
		, m_modifiers(mali_gralloc_format_get_modifiers(value)) { }

	void set_modifier(mali_gralloc_internal_format flag, bool value)
	{
		if (value)
		{
			m_modifiers |= flag;
		}
		else
		{
			m_modifiers &= ~flag;
		}
	}

	bool get_modifier(mali_gralloc_internal_format flag) const
	{
		return (m_modifiers & flag) != 0;
	}

	friend std::ostream& operator<<(std::ostream& os, const internal_format_t format);

	mali_gralloc_internal_format m_format = MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED;
	mali_gralloc_internal_format m_modifiers = 0;
};

inline constexpr internal_format_t internal_format_t::invalid = internal_format_t();

inline std::ostream& operator<<(std::ostream& os, internal_format_t format)
{
	auto flags = os.flags();
	os << std::showbase << std::hex << "FMT:" << format.m_format << ",MOD:" << format.m_modifiers;
	os.flags(flags);
	return os;
}

inline bool operator==(internal_format_t left, internal_format_t right)
{
	return left.is_equal(right);
}

inline bool operator!=(internal_format_t left, internal_format_t right)
{
	return !(left == right);
}

/* Ensure internal_format_t size and alignment are ABI independent.
 * This is important as internal_format_t is a member of private_handle_t.
 */
static_assert(sizeof(internal_format_t) == 8,
	"internal_format_t should have the same size on all ABIs (32-bit and 64-bit)");
static_assert(alignof(internal_format_t) == 4,
	"internal_format_t should have the same alignment on all ABIs (32-bit and 64-bit)");

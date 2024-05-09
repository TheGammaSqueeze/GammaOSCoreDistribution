/*
 * Copyright (C) 2020, 2022 Arm Limited.
 *
 * Copyright 2016 The Android Open Source Project
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

#include "idl_common/constants.h"
#include "idl_common/shared_metadata.h"
#include "log.h"

namespace arm::mapper::common
{

template <typename T>
struct aligned_optional
{
	enum class state : uint32_t
	{
		vacant,
		occupied,
	};

	state item_state{ state::vacant };
	T item{};

	aligned_optional() = default;

	aligned_optional(T initial_value)
	    : item_state(state::occupied)
	    , item(initial_value)
	{
	}

	aligned_optional(std::optional<T> std_optional)
	    : item_state(std_optional ? state::occupied : state::vacant)
	{
		if (std_optional)
		{
			item = *std_optional;
		}
	}

	std::optional<T> to_std_optional() const
	{
		switch (item_state)
		{
		case state::vacant:
			return std::nullopt;
		case state::occupied:
			return std::make_optional(item);
		default:
			return std::nullopt;
		}
	}

	void reset()
	{
		item_state = state::vacant;
	}
};

template <typename T, size_t N>
struct aligned_inline_vector
{
	uint32_t size;
	T contents[N];

	constexpr uint32_t capacity() const
	{
		return N;
	}

	const T *data() const
	{
		return &contents[0];
	}

	T *data()
	{
		return &contents[0];
	}

	const T *begin() const
	{
		return &contents[0];
	}

	const T *end() const
	{
		return &contents[N];
	}
};

struct shared_metadata
{
	aligned_optional<BlendMode> blend_mode{};
	aligned_optional<Rect> crop{};
	aligned_optional<Cta861_3> cta861_3{};
	aligned_optional<Dataspace> dataspace{};
	/* Store only the value from the ExtendableType as the string in this type is not a fixed size */
	aligned_optional<int64_t> chroma_siting{};
	aligned_optional<Smpte2086> smpte2086{};
	aligned_inline_vector<uint8_t, smpte2094_40_size> smpte2094_40{};
	aligned_inline_vector<uint8_t, smpte2094_10_size> smpte2094_10{};
	aligned_inline_vector<char, 256> name{};

	shared_metadata() = default;

	shared_metadata(std::string_view in_name)
	{
		name.size = std::min(name.capacity(), static_cast<uint32_t>(in_name.size()));
		std::memcpy(name.data(), in_name.data(), name.size);
	}

	std::string_view get_name() const
	{
		return name.size > 0 ? std::string_view(name.data(), name.size) : std::string_view();
	}
};

static_assert(offsetof(shared_metadata, blend_mode) == 0, "bad alignment");
static_assert(sizeof(shared_metadata::blend_mode) == 8, "bad size");

static_assert(offsetof(shared_metadata, crop) == 8, "bad alignment");
static_assert(sizeof(shared_metadata::crop) == 20, "bad size");

static_assert(offsetof(shared_metadata, cta861_3) == 28, "bad alignment");
static_assert(sizeof(shared_metadata::cta861_3) == 12, "bad size");

static_assert(offsetof(shared_metadata, dataspace) == 40, "bad alignment");
static_assert(sizeof(shared_metadata::dataspace) == 8, "bad size");

static_assert(offsetof(shared_metadata, chroma_siting) == 48, "bad alignment");
static_assert(sizeof(shared_metadata::chroma_siting) == 16, "bad size");

static_assert(offsetof(shared_metadata, smpte2086) == 64, "bad alignment");
static_assert(sizeof(shared_metadata::smpte2086) == 44, "bad size");

static_assert(offsetof(shared_metadata, smpte2094_40) == 108, "bad alignment");
static_assert(sizeof(shared_metadata::smpte2094_40) == 1272, "bad size");

static_assert(offsetof(shared_metadata, smpte2094_10) == 1380, "bad alignment");
static_assert(sizeof(shared_metadata::smpte2094_10) == 4836, "bad size");

static_assert(offsetof(shared_metadata, name) == 6216, "bad alignment");
static_assert(sizeof(shared_metadata::name) == 260, "bad size");

static_assert(alignof(shared_metadata) == 8, "bad alignment");
static_assert(sizeof(shared_metadata) == 6480, "bad size");

void shared_metadata_init(void *memory, std::string_view name, Dataspace dataspace, const ExtendableType &chroma_siting)
{
	auto *metadata = new (memory) shared_metadata(name);
	metadata->dataspace = aligned_optional{dataspace};
	metadata->chroma_siting = aligned_optional{chroma_siting.value};
}

size_t shared_metadata_size()
{
	return sizeof(shared_metadata);
}

void get_name(const imported_handle *hnd, std::string *name)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*name = metadata->get_name();
}

void get_crop_rect(const imported_handle *hnd, std::optional<Rect> *crop)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*crop = metadata->crop.to_std_optional();
}

android::status_t set_crop_rect(const imported_handle *hnd, const Rect &crop)
{
	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);

	if (crop.top < 0 || crop.left < 0 || crop.left > crop.right || crop.right > hnd->plane_info[0].alloc_width ||
	    crop.top > crop.bottom || crop.bottom > hnd->plane_info[0].alloc_height ||
	    (crop.right - crop.left) != hnd->width || (crop.bottom - crop.top) != hnd->height)
	{
		MALI_GRALLOC_LOGE("Attempt to set invalid crop rectangle");
		return android::BAD_VALUE;
	}

	metadata->crop = aligned_optional(crop);
	return android::OK;
}

void get_dataspace(const imported_handle *hnd, std::optional<Dataspace> *dataspace)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*dataspace = metadata->dataspace.to_std_optional();
}

void set_dataspace(const imported_handle *hnd, const Dataspace &dataspace)
{
	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	metadata->dataspace = aligned_optional(dataspace);
}

bool chroma_siting_is_arm_value(int64_t val)
{
	switch (val)
	{
	case static_cast<int64_t>(aidl::arm::graphics::ChromaSiting::COSITED_BOTH):
	case static_cast<int64_t>(aidl::arm::graphics::ChromaSiting::COSITED_VERTICAL):
		return true;
		break;
	default:
		return false;
	}
}
void get_chroma_siting(const imported_handle *hnd, std::optional<ExtendableType> *chroma_siting)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	auto stored_value = metadata->chroma_siting.to_std_optional();
	if (stored_value.has_value())
	{
		int64_t value = stored_value.value();
		const std::string name =
		    chroma_siting_is_arm_value(value) ? GRALLOC_ARM_CHROMA_SITING_TYPE_NAME : GRALLOC4_STANDARD_CHROMA_SITING;
		*chroma_siting = ExtendableType{ name, value };
	}
}

void set_chroma_siting(const imported_handle *hnd, const ExtendableType &chroma_siting)
{
	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	metadata->chroma_siting = aligned_optional(chroma_siting.value);
}

void get_blend_mode(const imported_handle *hnd, std::optional<BlendMode> *blend_mode)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*blend_mode = metadata->blend_mode.to_std_optional();
}

void set_blend_mode(const imported_handle *hnd, const BlendMode &blend_mode)
{
	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	metadata->blend_mode = aligned_optional(blend_mode);
}

void get_smpte2086(const imported_handle *hnd, std::optional<Smpte2086> *smpte2086)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*smpte2086 = metadata->smpte2086.to_std_optional();
}

android::status_t set_smpte2086(const imported_handle *hnd, const std::optional<Smpte2086> &smpte2086)
{
	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	if (!smpte2086.has_value())
	{
		if (PLATFORM_SDK_VERSION >= 33)
		{
			metadata->smpte2086.reset();
			return android::OK;
		}
		return android::BAD_VALUE;
	}
	metadata->smpte2086 = aligned_optional(smpte2086);
	return android::OK;
}

void get_cta861_3(const imported_handle *hnd, std::optional<Cta861_3> *cta861_3)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*cta861_3 = metadata->cta861_3.to_std_optional();
}

android::status_t set_cta861_3(const imported_handle *hnd, const std::optional<Cta861_3> &cta861_3)
{
	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	if (!cta861_3.has_value())
	{
		if (PLATFORM_SDK_VERSION >= 33)
		{
			metadata->cta861_3.reset();
			return android::OK;
		}
		return android::BAD_VALUE;
	}
	metadata->cta861_3 = aligned_optional(cta861_3);
	return android::OK;
}

void get_smpte2094_40(const imported_handle *hnd, std::optional<std::vector<uint8_t>> *smpte2094_40)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	if (metadata->smpte2094_40.size > 0)
	{
		smpte2094_40->emplace(metadata->smpte2094_40.begin(), metadata->smpte2094_40.end());
	}
	else
	{
		smpte2094_40->reset();
	}
}

android::status_t set_smpte2094_40(const imported_handle *hnd, const std::optional<std::vector<uint8_t>> &smpte2094_40)
{
	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	if (!smpte2094_40.has_value())
	{
		if (PLATFORM_SDK_VERSION >= 33)
		{
			metadata->smpte2094_40.size = 0;
			return android::OK;
		}
		MALI_GRALLOC_LOGE("Empty SMPTE 2094-40 data");
		return android::BAD_VALUE;
	}

	const size_t size = smpte2094_40->size();
	if (size == 0)
	{
		MALI_GRALLOC_LOGE("SMPTE 2094-40 vector is empty");
		return android::BAD_VALUE;
	}
	else if (size > metadata->smpte2094_40.capacity())
	{
		MALI_GRALLOC_LOGE("SMPTE 2094-40 metadata too large to fit in shared metadata region");
		return android::BAD_VALUE;
	}

	metadata->smpte2094_40.size = size;
	std::memcpy(metadata->smpte2094_40.data(), smpte2094_40->data(), size);
	return android::OK;
}

void get_smpte2094_10(const imported_handle *hnd, std::optional<std::vector<uint8_t>> *smpte2094_10)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	if (metadata->smpte2094_10.size > 0)
	{
		smpte2094_10->emplace(metadata->smpte2094_10.begin(), metadata->smpte2094_10.end());
	}
	else
	{
		smpte2094_10->reset();
	}
}

android::status_t set_smpte2094_10(const imported_handle *hnd, const std::optional<std::vector<uint8_t>> &smpte2094_10)
{
	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	if (!smpte2094_10.has_value())
	{
		metadata->smpte2094_10.size = 0;
		return android::OK;
	}

	const size_t size = smpte2094_10->size();
	if (size == 0)
	{
		MALI_GRALLOC_LOGE("SMPTE 2094-10 vector is empty");
		return android::BAD_VALUE;
	}
	else if (size > metadata->smpte2094_10.capacity())
	{
		MALI_GRALLOC_LOGE("SMPTE 2094-10 metadata too large to fit in shared metadata region");
		return android::BAD_VALUE;
	}

	metadata->smpte2094_10.size = size;
	std::memcpy(metadata->smpte2094_10.data(), smpte2094_10->data(), size);
	return android::OK;
}

} // namespace arm::mapper::common

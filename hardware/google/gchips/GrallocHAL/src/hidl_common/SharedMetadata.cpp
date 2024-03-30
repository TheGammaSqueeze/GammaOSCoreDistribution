/*
 * Copyright (C) 2020 Arm Limited.
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

#include "SharedMetadata.h"
#include "mali_gralloc_log.h"
//#include <VendorVideoAPI.h>

namespace arm
{
namespace mapper
{
namespace common
{

void shared_metadata_init(void *memory, std::string_view name)
{
	new(memory) shared_metadata(name);
}

size_t shared_metadata_size()
{
	return sizeof(shared_metadata);
}

void get_name(const private_handle_t *hnd, std::string *name)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*name = metadata->get_name();
}

void get_crop_rect(const private_handle_t *hnd, std::optional<Rect> *crop)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*crop = metadata->crop.to_std_optional();
}

android::status_t set_crop_rect(const private_handle_t *hnd, const Rect &crop)
{
	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);

	if (crop.top < 0 || crop.left < 0 ||
	    crop.left > crop.right || crop.right > hnd->plane_info[0].alloc_width ||
	    crop.top > crop.bottom || crop.bottom > hnd->plane_info[0].alloc_height ||
	    (crop.right - crop.left) != hnd->width ||
	    (crop.bottom - crop.top) != hnd->height)
	{
		MALI_GRALLOC_LOGE("Attempt to set invalid crop rectangle");
		return android::BAD_VALUE;
	}

	metadata->crop = aligned_optional(crop);
	return android::OK;
}

void get_dataspace(const private_handle_t *hnd, std::optional<Dataspace> *dataspace)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*dataspace = metadata->dataspace.to_std_optional();
}

void set_dataspace(const private_handle_t *hnd, const Dataspace &dataspace)
{
	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	metadata->dataspace = aligned_optional(dataspace);
}

void get_blend_mode(const private_handle_t *hnd, std::optional<BlendMode> *blend_mode)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*blend_mode = metadata->blend_mode.to_std_optional();
}

void set_blend_mode(const private_handle_t *hnd, const BlendMode &blend_mode)
{
	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	metadata->blend_mode = aligned_optional(blend_mode);
}

void get_smpte2086(const private_handle_t *hnd, std::optional<Smpte2086> *smpte2086)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*smpte2086 = metadata->smpte2086.to_std_optional();
}

android::status_t set_smpte2086(const private_handle_t *hnd, const std::optional<Smpte2086> &smpte2086)
{
	if (!smpte2086.has_value())
	{
		return android::BAD_VALUE;
	}

	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	metadata->smpte2086 = aligned_optional(smpte2086);

	return android::OK;
}

void get_cta861_3(const private_handle_t *hnd, std::optional<Cta861_3> *cta861_3)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	*cta861_3 = metadata->cta861_3.to_std_optional();
}

android::status_t set_cta861_3(const private_handle_t *hnd, const std::optional<Cta861_3> &cta861_3)
{
	if (!cta861_3.has_value())
	{
		return android::BAD_VALUE;
	}

	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	metadata->cta861_3 = aligned_optional(cta861_3);

	return android::OK;
}

void get_smpte2094_40(const private_handle_t *hnd, std::optional<std::vector<uint8_t>> *smpte2094_40)
{
	auto *metadata = reinterpret_cast<const shared_metadata *>(hnd->attr_base);
	if (metadata->smpte2094_40.size > 0)
	{
		const uint8_t *begin = metadata->smpte2094_40.data();
		const uint8_t *end = begin + metadata->smpte2094_40.size;
		smpte2094_40->emplace(begin, end);
	}
	else
	{
		smpte2094_40->reset();
	}
}

android::status_t set_smpte2094_40(const private_handle_t *hnd, const std::optional<std::vector<uint8_t>> &smpte2094_40)
{
	if (!smpte2094_40.has_value() || smpte2094_40->size() == 0)
	{
		MALI_GRALLOC_LOGE("Empty SMPTE 2094-40 data");
		return android::BAD_VALUE;
	}

	auto *metadata = reinterpret_cast<shared_metadata *>(hnd->attr_base);
	const size_t size = smpte2094_40.has_value() ? smpte2094_40->size() : 0;
	if (size > metadata->smpte2094_40.capacity())
	{
		MALI_GRALLOC_LOGE("SMPTE 2094-40 metadata too large to fit in shared metadata region");
		return android::BAD_VALUE;
	}

	metadata->smpte2094_40.size = size;
	std::memcpy(metadata->smpte2094_40.data(), smpte2094_40->data(), size);

	return android::OK;
}

} // namespace common
} // namespace mapper
} // namespace arm

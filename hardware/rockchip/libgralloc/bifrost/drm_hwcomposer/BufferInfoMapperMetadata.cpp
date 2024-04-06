/*
 * Copyright (C) 2021-2022 Arm Limited. All rights reserved.
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

/**
 * @file
 * Contains the implementation to get file descriptors for
 * reference Gralloc for the DRM Hardware Composer
 */

#define LOG_TAG "hwc-bufferinfo-mappermetadata-arm"

#include <bufferinfo/BufferInfoMapperMetadata.h>

#include <algorithm>
#include <android/hardware/graphics/mapper/4.0/IMapper.h>
#include <aidl/arm/graphics/ArmMetadataType.h>
#include <gralloctypes/Gralloc4.h>
#include <inttypes.h>
#include <limits.h>
#include <log/log.h>
#include <ui/GraphicBufferMapper.h>
#include <vector>

using android::hardware::hidl_vec;
using android::hardware::graphics::mapper::V4_0::Error;
using android::hardware::graphics::mapper::V4_0::IMapper;

namespace android
{

#define GRALLOC_ARM_METADATA_TYPE_NAME "arm.graphics.ArmMetadataType"
const IMapper::MetadataType arm_plane_fds_metadata_type = {
	GRALLOC_ARM_METADATA_TYPE_NAME, static_cast<int64_t>(aidl::arm::graphics::ArmMetadataType::PLANE_FDS)
};

static status_t DecodePlaneFds(const hidl_vec<uint8_t> &input, std::vector<int64_t> *fds)
{
	int64_t size = 0;
	auto input_size = input.size();

	if (input_size < sizeof(int64_t))
	{
		ALOGE("Bad input size %zu", input_size);
		return android::BAD_VALUE;
	}

	memcpy(&size, input.data(), sizeof(int64_t));
	if (size < 0)
	{
		ALOGE("Bad fds size decoded %" PRId64, size);
		return android::BAD_VALUE;
	}

	auto fds_size = size * sizeof(int64_t);
	if (input_size - sizeof(int64_t) < fds_size)
	{
		ALOGE("Bad input size %d to expected %" PRId64, static_cast<int>(input_size - sizeof(int64_t)), fds_size);
		return android::BAD_VALUE;
	}

	const uint8_t *fds_start = input.data() + sizeof(int64_t);

	fds->resize(size);
	memcpy(fds->data(), fds_start, fds_size);

	return android::OK;
}

int BufferInfoMapperMetadata::GetFds(buffer_handle_t buffer_handle, hwc_drm_bo_t *bo)
{
	std::vector<int64_t> fds;
	android::status_t result = android::BAD_VALUE;
	const void *handle = reinterpret_cast<const void *>(buffer_handle);

	static android::sp<IMapper> mapper = IMapper::getService();
	mapper->get(const_cast<void *>(handle), arm_plane_fds_metadata_type,
		[&result, &fds](Error error, const hidl_vec<uint8_t> &metadata)
		{
			switch (error)
			{
			case Error::NONE:
				break;
			case Error::UNSUPPORTED:
			    ALOGE("Gralloc implementation does not support the metadata needed "
			          "to access the plane fds");
			    result = android::BAD_VALUE;
				return;
			default:
				ALOGE("Gralloc metadata error %d", error);
				result = android::BAD_VALUE;
				return;
			}
			result = android::DecodePlaneFds(metadata, &fds);
		});

	if (result != android::OK)
	{
		return result;
	}
	else if (fds.empty())
	{
		return android::BAD_VALUE;
	}

	for (int i = 0; i < fds.size(); i++)
	{
		/* Check for valid fd and also that it doesn't overflow when casted */
		if (fds[i] <= 0 || fds[i] > UINT_MAX)
		{
			ALOGE("Encountered invalid fd %" PRId64, fds[i]);
			return android::BAD_VALUE;
		}

		bo->prime_fds[i] = static_cast<uint32_t>(fds[i]);
	}

	return result;
}

} /* end namespace android */
/*
 * Copyright (C) 2020, 2022-2023 ARM Limited. All rights reserved.
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
#pragma once

#include "core/buffer_descriptor.h"
#include "4.x/mapper/mapper_hidl_header.h"
#include <algorithm>
#include <assert.h>
#include <inttypes.h>
#include <string.h>
#if GRALLOC_ALLOCATOR_AIDL_VERSION > 0
#include <aidl/android/hardware/graphics/common/BufferUsage.h>
#endif

namespace arm
{
namespace mapper
{
namespace common
{

using android::hardware::hidl_vec;

const size_t DESCRIPTOR_32BIT_FIELDS = 4;
const size_t DESCRIPTOR_64BIT_FIELDS = 2;

const uint64_t validUsageBits =
    BufferUsage::GPU_CUBE_MAP | BufferUsage::GPU_MIPMAP_COMPLETE | BufferUsage::CPU_READ_MASK |
    BufferUsage::CPU_WRITE_MASK | BufferUsage::GPU_TEXTURE | BufferUsage::GPU_RENDER_TARGET |
    BufferUsage::COMPOSER_OVERLAY | BufferUsage::COMPOSER_CLIENT_TARGET | BufferUsage::CAMERA_INPUT |
    BufferUsage::CAMERA_OUTPUT | BufferUsage::PROTECTED | BufferUsage::COMPOSER_CURSOR | BufferUsage::VIDEO_ENCODER |
    BufferUsage::RENDERSCRIPT | BufferUsage::VIDEO_DECODER | BufferUsage::SENSOR_DIRECT_DATA |
#if GRALLOC_ALLOCATOR_AIDL_VERSION > 0
    static_cast<uint64_t>(aidl::android::hardware::graphics::common::BufferUsage::FRONT_BUFFER) |
#endif
    BufferUsage::GPU_DATA_BUFFER | BufferUsage::VENDOR_MASK | BufferUsage::VENDOR_MASK_HI;

const uint32_t DESCRIPTOR_ALLOCATOR_FLAGS =
    ((GRALLOC_ALLOCATOR_AIDL_VERSION > 0) ? GPU_DATA_BUFFER_WITH_ANY_FORMAT | USE_AIDL_FRONTBUFFER_USAGE | SUPPORTS_R8 :
                                            0);

template <typename BufferDescriptorInfoT>
static bool validateDescriptorInfo(const BufferDescriptorInfoT &descriptorInfo)
{
	if (descriptorInfo.width == 0 || descriptorInfo.height == 0 || descriptorInfo.layerCount == 0)
	{
		MALI_GRALLOC_LOGE("Invalid descriptorInfo sizes");
		return false;
	}

	if (static_cast<int32_t>(descriptorInfo.format) == 0)
	{
		MALI_GRALLOC_LOGE("No format supplied in descriptorInfo");
		return false;
	}

	if (descriptorInfo.usage & ~validUsageBits)
	{
		/* It is possible that application uses private usage bits so just warn in this case. */
		MALI_GRALLOC_LOGW("Buffer descriptor with invalid usage bits 0x%" PRIx64,
		                  descriptorInfo.usage & ~validUsageBits);
	}

	return true;
}

template <typename vecT>
static void push_descriptor_uint32(hidl_vec<vecT> *vec, size_t *pos, uint32_t val)
{
	static_assert(sizeof(val) % sizeof(vecT) == 0, "Unsupported vector type");
	vecT *element = vec->data() + *pos;
	memcpy(element, &val, sizeof(val));
	*pos += sizeof(val) / sizeof(vecT);
}

template <typename vecT>
static uint32_t pop_descriptor_uint32(const hidl_vec<vecT> &vec, size_t *pos)
{
	uint32_t val;
	static_assert(sizeof(val) % sizeof(vecT) == 0, "Unsupported vector type");
	const vecT *element = vec.data() + *pos;
	memcpy(&val, element, sizeof(val));
	*pos += sizeof(val) / sizeof(vecT);
	return val;
}

template <typename vecT>
static void push_descriptor_uint64(hidl_vec<vecT> *vec, size_t *pos, uint64_t val)
{
	static_assert(sizeof(val) % sizeof(vecT) == 0, "Unsupported vector type");
	vecT *element = vec->data() + *pos;
	memcpy(element, &val, sizeof(val));
	*pos += sizeof(val) / sizeof(vecT);
}

template <typename vecT>
static uint64_t pop_descriptor_uint64(const hidl_vec<vecT> &vec, size_t *pos)
{
	uint64_t val;
	static_assert(sizeof(val) % sizeof(vecT) == 0, "Unsupported vector type");
	const vecT *element = vec.data() + *pos;
	memcpy(&val, element, sizeof(val));
	*pos += sizeof(val) / sizeof(vecT);
	return val;
}

static void push_descriptor_string(hidl_vec<uint8_t> *vec, size_t *pos, const std::string &str)
{
	strncpy(reinterpret_cast<char *>(vec->data() + *pos), str.c_str(), MAX_NAME_LENGTH);
	(*vec)[*pos + MAX_NAME_LENGTH] = '\0';
	*pos += NAME_BUFFER_SIZE;
}

static std::array<char, NAME_BUFFER_SIZE> pop_descriptor_string(const hidl_vec<uint8_t> &vec, size_t *pos)
{
	std::array<char, NAME_BUFFER_SIZE> name;
	const uint8_t *element = vec.data() + *pos;
	std::copy(element, element + MAX_NAME_LENGTH, name.data());
	name[MAX_NAME_LENGTH] = '\0';
	*pos += NAME_BUFFER_SIZE;
	return name;
}

template <typename vecT, typename BufferDescriptorInfoT>
static const hidl_vec<vecT> grallocEncodeBufferDescriptor(const BufferDescriptorInfoT &descriptorInfo)
{
	hidl_vec<vecT> descriptor;

	static_assert(sizeof(uint32_t) % sizeof(vecT) == 0, "Unsupported vector type");
	constexpr size_t static_size = (DESCRIPTOR_32BIT_FIELDS * sizeof(uint32_t) / sizeof(vecT)) +
	                               (DESCRIPTOR_64BIT_FIELDS * sizeof(uint64_t) / sizeof(vecT)) + NAME_BUFFER_SIZE;

	size_t pos = 0;
	descriptor.resize(static_size);
	push_descriptor_uint32(&descriptor, &pos, descriptorInfo.width);
	push_descriptor_uint32(&descriptor, &pos, descriptorInfo.height);
	push_descriptor_uint32(&descriptor, &pos, descriptorInfo.layerCount);
	push_descriptor_uint32(&descriptor, &pos, static_cast<uint32_t>(descriptorInfo.format));
	push_descriptor_uint64(&descriptor, &pos, static_cast<uint64_t>(descriptorInfo.usage));
	push_descriptor_uint64(&descriptor, &pos, descriptorInfo.reservedSize);
	push_descriptor_string(&descriptor, &pos, descriptorInfo.name);
	assert(pos == static_size);

	return descriptor;
}

template <typename vecT>
static bool grallocDecodeBufferDescriptor(const hidl_vec<vecT> &androidDescriptor,
                                          buffer_descriptor_t &grallocDescriptor)
{
	static_assert(sizeof(uint32_t) % sizeof(vecT) == 0, "Unsupported vector type");
	size_t pos = 0;

	constexpr size_t static_size = (DESCRIPTOR_32BIT_FIELDS * sizeof(uint32_t) / sizeof(vecT)) +
	                               (DESCRIPTOR_64BIT_FIELDS * sizeof(uint64_t) / sizeof(vecT)) + NAME_BUFFER_SIZE;

	if (static_size != androidDescriptor.size())
	{
		MALI_GRALLOC_LOGE("hidl_vec size does not match expected buffer descriptor size");
		return false;
	}

	grallocDescriptor.width = pop_descriptor_uint32(androidDescriptor, &pos);
	grallocDescriptor.height = pop_descriptor_uint32(androidDescriptor, &pos);
	grallocDescriptor.layer_count = pop_descriptor_uint32(androidDescriptor, &pos);
	grallocDescriptor.hal_format = static_cast<uint64_t>(pop_descriptor_uint32(androidDescriptor, &pos));
	grallocDescriptor.producer_usage = pop_descriptor_uint64(androidDescriptor, &pos);
	grallocDescriptor.consumer_usage = grallocDescriptor.producer_usage;
	grallocDescriptor.signature = sizeof(buffer_descriptor_t);
	grallocDescriptor.reserved_size = pop_descriptor_uint64(androidDescriptor, &pos);
	grallocDescriptor.name = pop_descriptor_string(androidDescriptor, &pos);

	return true;
}

} // namespace common
} // namespace mapper
} // namespace arm

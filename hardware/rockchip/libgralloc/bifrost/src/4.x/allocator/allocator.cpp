/*
 * Copyright (C) 2020-2022. Arm Limited.
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

#include "allocator.h"
#include "idl_common/descriptor.h"
#include "idl_common/allocator.h"
#include "allocator/allocator.h"
#include "core/buffer_allocation.h"

namespace arm
{
namespace allocator
{
using android::hardware::hidl_handle;
using android::hardware::hidl_string;
using android::hardware::hidl_vec;
using android::hardware::Return;
using android::hardware::Void;
using android::hardware::graphics::allocator::V4_0::IAllocator;
using android::hardware::graphics::mapper::V4_0::Error;

GrallocAllocator::~GrallocAllocator()
{
	allocator_close();
}

Return<void> GrallocAllocator::allocate(const BufferDescriptor &descriptor, uint32_t count, allocate_cb hidl_cb)
{
	buffer_descriptor_t buffer_descriptor;
	if (!mapper::common::grallocDecodeBufferDescriptor(descriptor, buffer_descriptor))
	{
		hidl_cb(Error::BAD_DESCRIPTOR, 0, hidl_vec<hidl_handle>());
		return Void();
	}
	buffer_descriptor.flags = 0;
	CHECK_EQ(buffer_descriptor.flags, mapper::common::DESCRIPTOR_ALLOCATOR_FLAGS);

	auto result = common::allocate(&buffer_descriptor, count);
	if (!result.has_value())
	{
		switch (result.error())
		{
		case android::NO_ERROR:
			break;
		case android::NO_MEMORY:
			hidl_cb(Error::NO_RESOURCES, 0, hidl_vec<hidl_handle>());
			return Void();
		case android::BAD_VALUE:
			hidl_cb(Error::UNSUPPORTED, 0, hidl_vec<hidl_handle>());
			return Void();
		default:
			MALI_GRALLOC_LOGE("Unknown allocation error %d\n", result.error());
			hidl_cb(Error::UNSUPPORTED, 0, hidl_vec<hidl_handle>());
			return Void();
		}
	}

	CHECK_EQ(count, result->size());

	/* Populate handles for use by the caller. The hidl handles should not own the native handles and leave the
	 * responsibility of destroying the handles to this allocator process. */
	std::vector<hidl_handle> hidl_handles;
	hidl_handles.reserve(count);

	for (const auto &native_handle_ptr : *result)
	{
		hidl_handles.emplace_back(hidl_handle(native_handle_ptr.get()));
	}

	CHECK_EQ(count, hidl_handles.size());
	hidl_cb(Error::NONE, buffer_descriptor.pixel_stride, hidl_handles);

	return Void();
}

} // namespace allocator
} // namespace arm

extern "C" IAllocator *HIDL_FETCH_IAllocator(const char * /* name */)
{
	MALI_GRALLOC_LOGV("Arm Module IAllocator %d, pid = %d ppid = %d", GRALLOC_ALLOCATOR_HIDL_VERSION_MAJOR, getpid(),
	                  getppid());

	return new arm::allocator::GrallocAllocator();
}

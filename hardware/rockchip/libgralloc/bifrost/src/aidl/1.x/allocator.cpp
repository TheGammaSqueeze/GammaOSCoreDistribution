/*
 * Copyright (C) 2022 Arm Limited.
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

#include <log/log.h>
#include <cutils/properties.h>
#include <dlfcn.h>
#include <vndksupport/linker.h>

#include "allocator.h"
#include "arm_allocator.h"
#include "idl_common/descriptor.h"
#include "idl_common/allocator.h"

#include <aidlcommonsupport/NativeHandle.h>
#include <aidl/android/hardware/graphics/allocator/AllocationError.h>

using arm::allocator::aidl::IArmAllocator;

#define FUNC_NAME_OF_ARM_ALLOCATOR_GETTER "get_arm_aidl_allocator"

#define LIB_NAME_FOR_BIFROST "android.hardware.graphics.allocator-V1-bifrost.so"
#define LIB_NAME_FOR_MIDGARD "android.hardware.graphics.allocator-V1-midgard.so"

namespace aidl::android::hardware::graphics::allocator::impl::arm
{
// using ::android::hardware::hidl_vec;

static IArmAllocator* s_arm_allocator;

static IArmAllocator* get_arm_allocator()
{
	IArmAllocator* ret = NULL;
	std::string dir(HAL_LIBRARY_PATH_VENDOR);
	const char* lib_name;
	constexpr int dlMode = RTLD_LAZY;
	void* handle = NULL;
	IArmAllocator* (*getter)();
	char value[PROPERTY_VALUE_MAX];

	property_get("ro.board.platform", value, "0");
	if ( (0 == strcmp("rk3399", value) || 0 == strcmp("rk3288", value) ) )
	{
		lib_name = LIB_NAME_FOR_MIDGARD;
	}
	else
	{
		lib_name = LIB_NAME_FOR_BIFROST;
	}
	std::string name(lib_name);
	std::string path(dir + name);

	ALOGI("to load lib %s", path.c_str());
	handle = android_load_sphal_library(path.c_str(), dlMode);
	if ( NULL == handle )
	{
		const char* error = dlerror();
		LOG_ALWAYS_FATAL("failed to load %s, err: %s", path.c_str(), error);
		return NULL;
	}

	*(void **)(&getter) = dlsym(handle, FUNC_NAME_OF_ARM_ALLOCATOR_GETTER);
	if ( NULL == getter )
	{
		const char* error = dlerror();
		LOG_ALWAYS_FATAL("failed to dlsym %s, err: %s", FUNC_NAME_OF_ARM_ALLOCATOR_GETTER, error);
		return NULL;
	}

	ret = (*getter)();
	if ( NULL == ret )
	{
		LOG_ALWAYS_FATAL("failed to get ptr of IArmAllocator instance");
		return NULL;
	}

	return ret;
}

ndk::ScopedAStatus allocator::allocate(const std::vector<uint8_t> &in_descriptor, int32_t in_count,
                                        AllocationResult *out_result)
{
	if ( NULL == s_arm_allocator )
	{
		s_arm_allocator = get_arm_allocator();
	}

	return s_arm_allocator->allocate(in_descriptor, in_count, out_result);
}

} // namespace aidl::android::hardware::graphics::allocator::impl::arm

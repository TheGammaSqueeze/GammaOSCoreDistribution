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

#include <aidl/android/hardware/graphics/allocator/BnAllocator.h>

#pragma once

namespace aidl::android::hardware::graphics::allocator::impl::arm
{

class allocator : public BnAllocator
{
public:
	ndk::ScopedAStatus allocate(const std::vector<uint8_t> &in_descriptor, int32_t in_count,
	                            AllocationResult *out_result) override;
};

} // namespace aidl::android::hardware::graphics::allocator::impl::arm

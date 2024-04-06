/*
 * Copyright (C) 2020 ARM Limited. All rights reserved.
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
#ifndef GRALLOC_COMMON_ALLOCATOR_H
#define GRALLOC_COMMON_ALLOCATOR_H

#if GRALLOC_VERSION_MAJOR == 2
#include "2.x/gralloc_allocator_hidl_header.h"
#elif GRALLOC_VERSION_MAJOR == 3
#include "3.x/gralloc_allocator_hidl_header.h"
#endif

#if GRALLOC_VERSION_MAJOR == 4
// #include "4.x/gralloc_allocator_hidl_header.h"
#endif

#include <utility>
#include <vector>

#include <android-base/expected.h>
#include <utils/Errors.h>

#include "core/mali_gralloc_bufferdescriptor.h"
#include "BufferDescriptor.h"

namespace arm
{
namespace allocator
{
namespace common
{

using android::hardware::hidl_handle;
using android::hardware::hidl_vec;

/**             
 * Allocates buffers with the properties specified by the descriptor
 *              
 * @param[in, out]  descriptor  Specifies the properties of the buffers to allocate
 * @param           count       Number of buffers to allocate.
 *                      
 * @return Raw handles or error code.
 */             
android::base::expected<std::vector<unique_private_handle>, android::status_t>
allocate(buffer_descriptor_t *descriptor, uint32_t count);

} // namespace common
} // namespace allocator
} // namespace arm

#endif /* GRALLOC_COMMON_ALLOCATOR_H */

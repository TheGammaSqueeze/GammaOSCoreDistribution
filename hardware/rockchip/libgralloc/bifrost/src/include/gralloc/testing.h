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
 * @file
 * @brief Functionality to be used for testing
 */

#include <cutils/native_handle.h>

/* The macros below are an implementation detail and are not expected to be used directly. */
#define MALI_GRALLOC_HANDLE_WIDTH_TYPE int
#define MALI_GRALLOC_HANDLE_WIDTH_OFFSET (sizeof(native_handle_t) + 12)
#define MALI_GRALLOC_HANDLE_HEIGHT_TYPE int
#define MALI_GRALLOC_HANDLE_HEIGHT_OFFSET (sizeof(native_handle_t) + 16)

/**
 * @brief Write the width and height members of a Gralloc native_handle_t (only for testing purposes.)
 *
 * @param handle A Gralloc native handle
 * @param width New width
 * @param height New height
 */
static inline void mali_gralloc_testing_change_logical_size(native_handle_t *handle, int width, int height)
{
	auto *base = reinterpret_cast<char *>(handle);
	auto *handle_width =
	  reinterpret_cast<MALI_GRALLOC_HANDLE_WIDTH_TYPE *>(base + MALI_GRALLOC_HANDLE_WIDTH_OFFSET);
	*handle_width = width;

	auto *handle_height =
	  reinterpret_cast<MALI_GRALLOC_HANDLE_HEIGHT_TYPE *>(base + MALI_GRALLOC_HANDLE_HEIGHT_OFFSET);
	*handle_height = height;
}

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

#include <stdint.h>

#include "gralloc/formats.h"
#include "internal_format.h"
#include "buffer_descriptor.h"

void mali_gralloc_adjust_dimensions(internal_format_t format, uint64_t usage, int *width, int *height);

internal_format_t mali_gralloc_select_format(const buffer_descriptor_t &descriptor, uint64_t usage, const int buffer_size);

bool is_base_format_used_by_rk_video(const uint32_t base_format);

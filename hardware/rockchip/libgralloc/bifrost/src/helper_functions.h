/*
 * Copyright (C) 2010-2020 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
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

#include <unistd.h>
#include <sys/mman.h>
#include <sys/user.h>

#include "log.h"

#define GRALLOC_ALIGN(value, base) ((((value) + (base) -1) / (base)) * (base))

#define GRALLOC_MAX(a, b) (((a)>(b))?(a):(b))

#define GRALLOC_UNUSED(x) ((void)x)

static inline size_t round_up_to_page_size(size_t x)
{
	return (x + (PAGE_SIZE - 1)) & ~(PAGE_SIZE - 1);
}

static inline bool does_usage_have_flag(uint64_t usage, uint64_t flag)
{
       return ( flag == (usage & flag) );
}

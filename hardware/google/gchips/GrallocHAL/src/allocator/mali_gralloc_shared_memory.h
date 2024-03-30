/*
 * Copyright (C) 2020 Arm Limited. All rights reserved.
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

#ifndef GRALLOC_SHARED_MEMORY_H_
#define GRALLOC_SHARED_MEMORY_H_

#include <stdint.h>
#include <utility>
#include "mali_gralloc_buffer.h"

/*
 * Frees resources acquired from gralloc_shared_memory_allocate.
 */
void gralloc_shared_memory_free(private_handle_t *hnd);

#endif /* GRALLOC_SHARED_MEMORY_H_ */

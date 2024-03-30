/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include <kernel.h>
#include <cstdlib>

#include "chre/platform/memory.h"
#include "chre/platform/shared/pal_system_api.h"

K_HEAP_DEFINE(chre_mem, CONFIG_CHRE_DYNAMIC_MEMORY_SIZE);

namespace chre {

void *memoryAlloc(size_t size) {
  return k_heap_alloc(&chre_mem, size, K_NO_WAIT);
}

void memoryFree(void *pointer) {
  k_heap_free(&chre_mem, pointer);
}

}  // namespace chre

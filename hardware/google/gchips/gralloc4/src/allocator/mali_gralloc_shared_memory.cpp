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

#include "mali_gralloc_shared_memory.h"
#include "mali_gralloc_log.h"
#include "mali_gralloc_buffer.h"
#include "mali_gralloc_ion.h"
#include "gralloc_helper.h"

/* TODO: move shared attr memory allocation function here */

void gralloc_shared_memory_free(private_handle_t *hnd)
{
	if (hnd->attr_base)
	{
		munmap(hnd->attr_base, hnd->attr_size);
		hnd->attr_base = nullptr;
	}

	hnd->close_share_attr_fd();
	hnd->set_share_attr_fd(-1);
}

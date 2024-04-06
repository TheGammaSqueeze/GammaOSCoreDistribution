/*
 * Copyright (C) 2020-2022 Arm Limited. All rights reserved.
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

#include <sys/syscall.h>
#include <linux/memfd.h>
#include <fcntl.h>

#include "shared_memory.h"
#include "log.h"
#include "core/buffer.h"

android::base::unique_fd gralloc_shared_memory_allocate(const char *name, off_t size)
{
	auto fd = android::base::unique_fd{static_cast<int>(syscall(__NR_memfd_create, name, MFD_ALLOW_SEALING))};
	if (!fd.ok())
	{
		MALI_GRALLOC_LOGE("memfd_create: %s", strerror(errno));
		return fd;
	}

	if (size > 0)
	{
		if (ftruncate(fd.get(), size) < 0)
		{
			MALI_GRALLOC_LOGE("ftruncate: %s", strerror(errno));
			return android::base::unique_fd{-1};
		}
	}

	if (fcntl(fd.get(), F_ADD_SEALS, F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_SEAL) < 0)
	{
		MALI_GRALLOC_LOGW("Failed to seal fd: %s", strerror(errno));
		return android::base::unique_fd{-1};
	}

	return fd;
}

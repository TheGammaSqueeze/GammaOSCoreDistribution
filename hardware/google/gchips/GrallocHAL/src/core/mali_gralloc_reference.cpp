/*
 * Copyright (C) 2016, 2018-2020 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

#include <hardware/gralloc1.h>

#include "mali_gralloc_buffer.h"
#include "allocator/mali_gralloc_ion.h"
#include "allocator/mali_gralloc_shared_memory.h"
#include "mali_gralloc_bufferallocation.h"
#include "mali_gralloc_debug.h"
#include "mali_gralloc_reference.h"
#include "mali_gralloc_usages.h"

static pthread_mutex_t s_map_lock = PTHREAD_MUTEX_INITIALIZER;

int mali_gralloc_reference_retain(buffer_handle_t handle)
{
	if (private_handle_t::validate(handle) < 0)
	{
		MALI_GRALLOC_LOGE("Registering/Retaining invalid buffer %p, returning error", handle);
		return -EINVAL;
	}

	private_handle_t *hnd = (private_handle_t *)handle;
	pthread_mutex_lock(&s_map_lock);
	int retval = 0;

	if (hnd->allocating_pid == getpid() || hnd->remote_pid == getpid())
	{
		hnd->ref_count++;
	}
	else
	{
		hnd->remote_pid = getpid();
		hnd->ref_count = 1;

		// Reset the handle bases, this is used to check if a buffer is mapped
		for (int fidx = 0; fidx < hnd->fd_count; fidx++) {
			hnd->bases[fidx] = 0;
		}
	}

	pthread_mutex_unlock(&s_map_lock);

	// TODO(b/187145254): CPU_READ/WRITE buffer is not being properly locked from
	// MFC. This is a WA for the time being.
	constexpr auto cpu_access_usage = (
			GRALLOC_USAGE_SW_WRITE_OFTEN |
			GRALLOC_USAGE_SW_READ_OFTEN |
			GRALLOC_USAGE_SW_WRITE_RARELY |
			GRALLOC_USAGE_SW_READ_RARELY
	);

	if (hnd->get_usage() & cpu_access_usage)
		retval = mali_gralloc_reference_map(handle);

	return retval;
}

int mali_gralloc_reference_map(buffer_handle_t handle) {
	private_handle_t *hnd = (private_handle_t *)handle;

	pthread_mutex_lock(&s_map_lock);

	if (hnd->bases[0]) {
		MALI_GRALLOC_LOGV("Buffer is already mapped");
		pthread_mutex_unlock(&s_map_lock);
		return 0;
	}

	int retval = mali_gralloc_ion_map(hnd);

	pthread_mutex_unlock(&s_map_lock);

	return retval;
}

int mali_gralloc_reference_release(buffer_handle_t handle, bool canFree)
{
	if (private_handle_t::validate(handle) < 0)
	{
		MALI_GRALLOC_LOGE("unregistering/releasing invalid buffer %p, returning error", handle);
		return -EINVAL;
	}

	private_handle_t *hnd = (private_handle_t *)handle;
	pthread_mutex_lock(&s_map_lock);

	if (hnd->ref_count == 0)
	{
		MALI_GRALLOC_LOGE("Buffer %p should have already been released", handle);
		pthread_mutex_unlock(&s_map_lock);
		return -EINVAL;
	}

	if (hnd->allocating_pid == getpid())
	{
		hnd->ref_count--;

		if (hnd->ref_count == 0 && canFree)
		{
			mali_gralloc_dump_buffer_erase(hnd);
			mali_gralloc_buffer_free(handle);
		}
	}
	else if (hnd->remote_pid == getpid()) // never unmap buffers that were not imported into this process
	{
		hnd->ref_count--;

		if (hnd->ref_count == 0)
		{
			mali_gralloc_ion_unmap(hnd);

			/* TODO: Make this unmapping of shared meta fd into a function? */
			if (hnd->attr_base)
			{
				munmap(hnd->attr_base, hnd->attr_size);
				hnd->attr_base = nullptr;
			}
		}
	}
	else
	{
		MALI_GRALLOC_LOGE("Trying to unregister buffer %p from process %d that was not imported into current process: %d", hnd,
		     hnd->remote_pid, getpid());
	}

	pthread_mutex_unlock(&s_map_lock);
	return 0;
}

int mali_gralloc_reference_validate(buffer_handle_t handle)
{
	if (private_handle_t::validate(handle) < 0)
	{
		MALI_GRALLOC_LOGE("Reference invalid buffer %p, returning error", handle);
		return -EINVAL;
	}

	const auto *hnd = (private_handle_t *)handle;
	pthread_mutex_lock(&s_map_lock);

	if (hnd->allocating_pid == getpid() || hnd->remote_pid == getpid()) {
		pthread_mutex_unlock(&s_map_lock);
		return 0;
	} else {
		pthread_mutex_unlock(&s_map_lock);
		MALI_GRALLOC_LOGE("Reference unimported buffer %p, returning error", handle);
		return -EINVAL;
	}
}


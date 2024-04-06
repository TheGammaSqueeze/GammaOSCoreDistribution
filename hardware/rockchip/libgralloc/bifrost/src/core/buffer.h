/*
 * Copyright (C) 2017-2022 ARM Limited. All rights reserved.
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

#include <array>
#include <atomic>
#include <cerrno>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <new>

#include <android-base/scopeguard.h>
#include <android-base/unique_fd.h>
#include <cutils/native_handle.h>
#include <unistd.h>
#include <sys/mman.h>

#include "log.h"
#include "gralloc/testing.h"
#include "core/internal_format.h"

#define PRIVATE_HANDLE_NUM_FDS 2
#define PRIVATE_HANDLE_NUM_INTS ((sizeof(private_handle_t) - sizeof(native_handle_t)) / sizeof(int) - PRIVATE_HANDLE_NUM_FDS)

/*
 * Maximum number of pixel format planes.
 * Plane [0]: Single plane formats (inc. RGB, YUV) and Y
 * Plane [1]: U/V, UV
 * Plane [2]: V/U
 */
static constexpr size_t max_planes = 3;

struct plane_info_t
{

	/*
	 * Offset to plane (in bytes),
	 * from the start of the allocation.
	 */
	uint32_t offset{};

	/*
	 * Byte Stride: number of bytes between two vertically adjacent
	 * pixels in given plane. This can be mathematically described by:
	 *
	 * byte_stride = ALIGN((alloc_width * bpp)/8, alignment)
	 *
	 * where,
	 *
	 * alloc_width: width of plane in pixels (c.f. pixel_stride)
	 * bpp: average bits per pixel
	 * alignment (in bytes): dependent upon pixel format and usage
	 *
	 * For uncompressed allocations, byte_stride might contain additional
	 * padding beyond the alloc_width. For AFBC, alignment is zero.
	 */
	uint32_t byte_stride{};

	/*
	 * Dimensions of plane (in pixels).
	 *
	 * For single plane formats, pixels equates to luma samples.
	 * For multi-plane formats, pixels equates to the number of sample sites
	 * for the corresponding plane, even if subsampled.
	 *
	 * AFBC compressed formats: requested width/height are rounded-up
	 * to a whole AFBC superblock/tile (next superblock at minimum).
	 * Uncompressed formats: dimensions typically match width and height
	 * but might require pixel stride alignment.
	 *
	 * See 'byte_stride' for relationship between byte_stride and alloc_width.
	 *
	 * Any crop rectangle defined by GRALLOC_ARM_BUFFER_ATTR_CROP_RECT must
	 * be wholly within the allocation dimensions. The crop region top-left
	 * will be relative to the start of allocation.
	 */
	uint32_t alloc_width{};
	uint32_t alloc_height{};
};

using plane_layout = std::array<plane_info_t, max_planes>;

/* Forward declarations of C++ types. */
class internal_format_t;

enum class handle_type : int
{
	raw,
	imported,
};

/*
 * The following code is gralloc's implementation of the native_handle data structure provided
 * by cutils/native_handle.h. Its purpose is to permit transfer of file descriptors and buffer
 * metadata across processes via. binder or otherwise.
 *
 * It is assumed the inherited native_handle memory is placed before the private_handle memory.
 * For the implementation to function correctly, we must ensure:
 *  - The same memory layout between 64-bit and 32-bit processes. Pointers are padded to the
 *    size of a uint64_t to ensure offsetof returns the same value.
 *  - The structure is trivially copyable, that is, able to be copied using memcpy.
 *  - The structure is trivially destructible since the destructor will never be called.
 */
struct private_handle_t : public native_handle
{
	static constexpr handle_type this_type = handle_type::raw;

	/*
	 * Shared file descriptor for dma_buf sharing. This must be the first element in the
	 * structure so that binder knows where it is and can properly share it between
	 * processes.
	 * DO NOT MOVE THIS ELEMENT!
	 */
	int share_fd{-1};
	int share_attr_fd{-1};

	// ints
	int magic{sMagic};

	/*
	 * Input properties.
	 *
	 * req_format: Pixel format, base + private modifiers.
	 * width/height: Buffer dimensions.
	 * producer/consumer_usage: Buffer usage (indicates IP)
	 */
	int width{};
	int height{};
	int req_format{};
	uint64_t producer_usage{};
	uint64_t consumer_usage{};

	/* Used for validation purposes. */
	int stride{};

	/*
	 * Allocation properties.
	 *
	 * alloc_format: Pixel format (base + modifiers). NOTE: base might differ from requested
	 *               format (req_format) where fallback to single-plane format was required.
	 * plane_info:   Per plane allocation information.
	 * size:         Total bytes allocated for buffer (inc. all planes, layers. etc.).
	 * layer_count:  Number of layers allocated to buffer.
	 *               All layers are the same size (in bytes).
	 *               Multi-layers supported in v1.0, where GRALLOC1_CAPABILITY_LAYERED_BUFFERS is enabled.
	 *               Layer size: 'size' / 'layer_count'.
	 *               Layer (n) offset: n * ('size' / 'layer_count'), n=0 for the first layer.
	 *
	 */
	internal_format_t alloc_format{};
	plane_layout plane_info{};
	int size{};
	int layer_count{};

	uint64_t backing_store_id{};

	/* Size of the attribute shared region in bytes. */
	uint64_t attr_size{};

	uint64_t reserved_region_size{};
	handle_type type{handle_type::raw};

	/**
	 * This magic number is used to check that the native_handle passed to Gralloc is our private_handle_t type.
	 * The value is chosen arbitrarily.
	 */
	static const int sMagic = 0x3141592;

	private_handle_t(int in_size, uint64_t in_consumer_usage, uint64_t in_producer_usage, int in_shared_fd,
	                 int in_req_format, internal_format_t in_alloc_format, int in_width, int in_height,
	                 int in_layer_count, const plane_layout &in_plane_info, int in_stride)
	    : share_fd{ in_shared_fd }
	    , width{ in_width }
	    , height{ in_height }
	    , req_format{ in_req_format }
	    , producer_usage{ in_producer_usage }
	    , consumer_usage{ in_consumer_usage }
	    , stride{ in_stride }
	    , alloc_format{ in_alloc_format }
	    , plane_info{ in_plane_info }
	    , size{ in_size }
	    , layer_count{ in_layer_count }
	{
		version = sizeof(native_handle);
		numFds = PRIVATE_HANDLE_NUM_FDS;
		numInts = PRIVATE_HANDLE_NUM_INTS;
	}

	bool is_multi_plane() const
	{
		/* For multi-plane, the byte stride for the second plane will always be non-zero. */
		return (plane_info[1].alloc_width != 0);
	}
};

struct imported_handle : private_handle_t
{
	static constexpr handle_type this_type = handle_type::imported;

	std::atomic<int> lock_count{};
	int cpu_write{};              /**< Buffer is locked for CPU write when non-zero. */
	int import_pid{-1};
	void *base{nullptr};
	void *attr_base{MAP_FAILED};
};

#define IMPORTED_HANDLE_NUM_INTS ((sizeof(imported_handle) - sizeof(private_handle_t)) / sizeof(int))
static_assert(alignof(private_handle_t) == alignof(imported_handle));

/* Check the correctness of the macros defined in gralloc/testing.h */
static_assert(MALI_GRALLOC_HANDLE_WIDTH_OFFSET == offsetof(private_handle_t, width));
static_assert(MALI_GRALLOC_HANDLE_HEIGHT_OFFSET == offsetof(private_handle_t, height));

template <typename T>
T *handle_cast(native_handle *x)
{
	auto handle = static_cast<private_handle_t *>(x);
	if (handle == nullptr)
	{
		MALI_GRALLOC_LOGE("bad handle (nullptr)");
		return nullptr;
	}
	else if (handle->version != sizeof(native_handle))
	{
		MALI_GRALLOC_LOGE("bad handle: version %d", handle->version);
		return nullptr;
	}
	else if (handle->numFds != PRIVATE_HANDLE_NUM_FDS)
	{
		MALI_GRALLOC_LOGE("bad handle: numFds = %d", handle->numFds);
		return nullptr;
	}
	else if (handle->numInts != PRIVATE_HANDLE_NUM_INTS)
	{
		MALI_GRALLOC_LOGE("bad handle: numInts = %d", handle->numInts);
		return nullptr;
	}
	else if (handle->magic != private_handle_t::sMagic)
	{
		MALI_GRALLOC_LOGE("bad handle: magic = %#x", handle->magic);
		return nullptr;
	}
	else if (handle->type < T::this_type)
	{
		MALI_GRALLOC_LOGW("handle not imported");
		return nullptr;
	}
	else
	{
		return static_cast<T *>(x);
	}
}

template <typename T>
T *handle_cast(const native_handle *x)
{
	return handle_cast<T>(const_cast<native_handle *>(x));
}

struct native_handle_deleter
{
	void operator()(native_handle_t *native_handle)
	{
		native_handle_close(native_handle);
		native_handle_delete(native_handle);
	}
};

template <typename T>
using unique_handle = std::unique_ptr<T, native_handle_deleter>;

using unique_native_handle = unique_handle<native_handle_t>;
using unique_private_handle = unique_handle<private_handle_t>;
using unique_imported_handle = unique_handle<imported_handle>;

unique_private_handle make_private_handle(int size, uint64_t consumer_usage,
                                          uint64_t producer_usage, android::base::unique_fd shared_fd,
                                          int required_format, internal_format_t allocated_format, int width, int height,
                                          int layer_count, const plane_layout &plane_info, int stride);

static inline unique_imported_handle make_imported_handle(private_handle_t *raw_handle)
{
	auto *new_handle = native_handle_create(PRIVATE_HANDLE_NUM_FDS, PRIVATE_HANDLE_NUM_INTS + IMPORTED_HANDLE_NUM_INTS);
	if (new_handle == nullptr)
	{
		return nullptr;
	}

	/*
	 * The numInts member of an imported handle is altered so that its data is never
	 * copied by native_handle_clone and therefore never transferred across between
	 * processes.
	 */
	new_handle->numInts = PRIVATE_HANDLE_NUM_INTS;
	new_handle->numFds = 0;

	auto import_handle = unique_imported_handle{static_cast<imported_handle *>(new_handle)};

	/* Clone file descriptors with care. */
	for (int i = 0; i < PRIVATE_HANDLE_NUM_FDS; ++i)
	{
		import_handle->data[i] = dup(raw_handle->data[i]);
		if (import_handle->data[i] == -1)
		{
			return nullptr;
		}
		else
		{
			++import_handle->numFds;
		}
	}

	/* Copy shared portion of handle metadata. */
	for (int i = PRIVATE_HANDLE_NUM_FDS; i < PRIVATE_HANDLE_NUM_FDS + PRIVATE_HANDLE_NUM_INTS; ++i)
	{
		import_handle->data[i] = raw_handle->data[i];
	}

	/* Overwrite handle type. */
	import_handle->type = handle_type::imported;

	return import_handle;
}

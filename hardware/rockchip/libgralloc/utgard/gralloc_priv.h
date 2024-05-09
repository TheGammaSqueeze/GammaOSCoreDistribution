/*
 * Copyright (C) 2010 ARM Limited. All rights reserved.
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

#ifndef GRALLOC_PRIV_H_
#define GRALLOC_PRIV_H_

#include <stdint.h>
#include <stdlib.h>
#include <pthread.h>
#include <errno.h>
#include <linux/fb.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <unistd.h>
#include <string.h>

#ifdef __cplusplus
#include <new>
#endif

#include <hardware/gralloc.h>
#include <cutils/native_handle.h>
#include <alloc_device.h>
#include <utils/Log.h>
#include "log.h"

#define GRALLOC_ARM_DMA_BUF_MODULE 1 // 编译 mali_so 需要.

/* the max string size of GRALLOC_HARDWARE_GPU0 & GRALLOC_HARDWARE_FB0
 * 8 is big enough for "gpu0" & "fb0" currently
 */
#define MALI_GRALLOC_HARDWARE_MAX_STR_LEN 8
#define NUM_FB_BUFFERS 3

#define MALI_IGNORE(x) (void)x
typedef enum
{
	MALI_YUV_NO_INFO,
	MALI_YUV_BT601_NARROW,
	MALI_YUV_BT601_WIDE,
	MALI_YUV_BT709_NARROW,
	MALI_YUV_BT709_WIDE,
} mali_gralloc_yuv_info;

struct private_handle_t;

/*---------------------------------------------------------------------------*/

struct private_module_t
{
	gralloc_module_t base;

	private_handle_t *framebuffer;
	uint32_t numBuffers;
	uint32_t bufferMask;
	pthread_mutex_t lock;
	buffer_handle_t currentBuffer;

	struct fb_var_screeninfo info;
	struct fb_fix_screeninfo finfo;
	float xdpi;
	float ydpi;
	float fps;

	/* default constructor */
	private_module_t();
};

/*---------------------------------------------------------------------------*/

/*
 * Maximum number of pixel format planes.
 * Plane [0]: Single plane formats (inc. RGB, YUV) and Y
 * Plane [1]: U/V, UV
 * Plane [2]: V/U
 */
#define MAX_PLANES 3

#ifdef __cplusplus
#define DEFAULT_INITIALIZER(x) = x
#else
#define DEFAULT_INITIALIZER(x)
#endif

typedef struct plane_info {

	/*
	 * Offset to plane (in bytes),
	 * from the start of the allocation.
	 */
	uint32_t offset;

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
	uint32_t byte_stride;

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
	uint32_t alloc_width;
	uint32_t alloc_height;
} plane_info_t;

struct private_handle_t;

#ifndef __cplusplus
/* C99 with pedantic don't allow anonymous unions which is used in below struct
 * Disable pedantic for C for this struct only.
 */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wpedantic"
#endif

#ifdef __cplusplus
struct private_handle_t : public native_handle
{
#else
struct private_handle_t
{
	struct native_handle nativeHandle;
#endif

#ifdef __cplusplus
	/* Never intended to be used from C code */
	enum
	{
		PRIV_FLAGS_FRAMEBUFFER = 0x00000001,
		PRIV_FLAGS_USES_UMP    = 0x00000002, // 编译 mali_so 需要.
		// PRIV_FLAGS_USES_ION    = 0x00000004,

		/* allocated from dmabuf_heaps. */
		PRIV_FLAGS_USES_DBH = 1 << 6,
	};

	enum
	{
		LOCK_STATE_WRITE     =   1 << 30,
		LOCK_STATE_MAPPED    =   1 << 29,
		LOCK_STATE_UNREGISTERED  =   1 << 28,
		LOCK_STATE_READ_MASK =   0x1FFFFFFF
	};
#endif

	// fds
	/*shared file descriptor for dma_buf sharing*/
	int	share_fd DEFAULT_INITIALIZER(-1);

	/*
	 * 用于存储和 rk 平台相关的 attributes 的 shared_memory (rk_ashmem) 的 fd.
	 * 对应 buffer 的具体类型是 rk_ashmem_t,
	 *      具体定义在 hardware/libhardware/include/hardware/gralloc.h 中.
	 */
	int	ashmem_fd;

	/* 用于存储 rkvdec_scaling_metadata 的 shared_memory 的 fd.
	 * 该 memory 也被记为 rkvdec_scaling_metadata_buf,
	 * 存储的数据的具体类型是 metadata_for_rkvdec_scaling_t,
	 *      定义在 hardware/libhardware/include/hardware/gralloc.h 中.
	 *
	 * 原理上 rkvdec_scaling_metadata 可以存储到 rk_ashmem 中,
	 * 但因为对应的 perform 接口的设计, 这里使用一个独立的 shared_memory 来存储.
	 *
	 * "rsm": rkvdec_scaling_metadata
	 */
	int	rsm_fd;
	/*-------------------------------------------------------*/

	// ints
	int	magic DEFAULT_INITIALIZER(sMagic);
	int     usage DEFAULT_INITIALIZER(0);
	int	size DEFAULT_INITIALIZER(0);
	int	width DEFAULT_INITIALIZER(0);
	int	height DEFAULT_INITIALIZER(0);
	/* 将被 mali_so 引用的具体的 hal_format.
	 * 和 'req_format' 不是总是相同,
	 * 因为 'req_format' 可能是 HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED
	 * 或 HAL_PIXEL_FORMAT_YCbCr_420_888 这样的笼统格式.
	 * 参见 get_specific_hal_format().
	 */
	int	format DEFAULT_INITIALIZER(0);
	int	stride DEFAULT_INITIALIZER(0); // byte_stride
	int	pixel_stride DEFAULT_INITIALIZER(0);
	union
	{
		void	*base DEFAULT_INITIALIZER(NULL);
		uint64_t padding;
	};

	/* rk_ashmem 的 size, in bytes. */
        int	ashmem_size DEFAULT_INITIALIZER(0);
	/* 将保存对 'ashmem_fd' mmap() 返回的虚拟地址.
	 */
	union
	{
		void*    ashmem_base;
		uint64_t padding5;
	};

	/* rkvdec_scaling_metadata_buf 的 size, in bytes. */
        int	rsm_size DEFAULT_INITIALIZER(0);
	/* 将保存对 'rsm_fd' mmap() 返回的虚拟地址.
	 */
	union
	{
		void*    rsm_base;
		uint64_t padding6;
	};

	/*
	 * 当前 buffer 的 lock 状态, 可能的取值 LOCK_STATE_MAPPED, ...
	 */
	int     lockState;
	int     writeOwner;
	/*
	 * 分配的时候会被设置为当前进程的 pid;
	 * 可能在其他进程中被 registerBuffer (import) 时, 会被变更为当前进程的 pid.
	 */
	int     pid;

	mali_gralloc_yuv_info yuv_info DEFAULT_INITIALIZER(MALI_YUV_NO_INFO);

	// Following members is for framebuffer only
	int     fd;
	int     offset DEFAULT_INITIALIZER(0);

	/*-----------------------------------*/
	// 来自 bifrost gralloc on g7 的数据成员.
	// .KP : 目前 mali400/450 的 mali_so "不会" 引用这些成员.

	int flags DEFAULT_INITIALIZER(0); // 目前唯一有效的取值是 PRIV_FLAGS_USES_DBH.

	/*
	 * Input properties.
	 *
	 * req_format: Pixel format, base + private modifiers.
	 * width/height: Buffer dimensions.
	 * producer/consumer_usage: Buffer usage (indicates IP)
	 */
	// int width DEFAULT_INITIALIZER(0);
	// int height DEFAULT_INITIALIZER(0);
	int req_format DEFAULT_INITIALIZER(0);
	uint64_t producer_usage DEFAULT_INITIALIZER(0);
	uint64_t consumer_usage DEFAULT_INITIALIZER(0);

	/* DEPRECATED. Kept for valiation purposes */
	// int stride DEFAULT_INITIALIZER(0);

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
	uint64_t alloc_format DEFAULT_INITIALIZER(0);
	plane_info_t plane_info[MAX_PLANES] DEFAULT_INITIALIZER({});
	// int size DEFAULT_INITIALIZER(0);
	uint32_t layer_count DEFAULT_INITIALIZER(0); // 在 3528_9.0 中, 预期总是 1.

	uint64_t backing_store_id DEFAULT_INITIALIZER(0x0);
	int backing_store_size DEFAULT_INITIALIZER(0);

	int cpu_read DEFAULT_INITIALIZER(0);               /**< Buffer is locked for CPU read when non-zero. */
	int cpu_write DEFAULT_INITIALIZER(0);              /**< Buffer is locked for CPU write when non-zero. */

	// 下面的一组 extended_members, 目前暂未实际使用.
	int allocating_pid DEFAULT_INITIALIZER(0);
	int remote_pid DEFAULT_INITIALIZER(-1);
	int ref_count DEFAULT_INITIALIZER(0);

	// locally mapped shared attribute area
	/*
	union
	{
		void *attr_base DEFAULT_INITIALIZER(MAP_FAILED);
		uint64_t padding3;
	};
	 */

	/*
	 * Deprecated.
	 * Use GRALLOC_ARM_BUFFER_ATTR_DATASPACE
	 * instead.
	 */
	// mali_gralloc_yuv_info yuv_info DEFAULT_INITIALIZER(MALI_YUV_NO_INFO);

	// For framebuffer only
	/*
	int fd DEFAULT_INITIALIZER(-1);
	union
	{
		off_t offset DEFAULT_INITIALIZER(0);
		uint64_t padding4;
	};
	 */

	/* Size of the attribute shared region in bytes. */
	// uint64_t attr_size DEFAULT_INITIALIZER(0);

	uint64_t reserved_region_size DEFAULT_INITIALIZER(0);

	// uint64_t imapper_version DEFAULT_INITIALIZER(0);

	/*-------------------------------------------------------*/

#define GRALLOC_ARM_NUM_FDS 3

#define NUM_INTS_IN_PRIVATE_HANDLE ((sizeof(struct private_handle_t) - sizeof(native_handle)) / sizeof(int) - GRALLOC_ARM_NUM_FDS)

#ifdef __cplusplus
	static const int sNumFds = GRALLOC_ARM_NUM_FDS;
	static const int sMagic = 0x3141592;


#if 0
	private_handle_t(int usage, int size, void *base, int lock_state):
		share_fd(-1),
		magic(sMagic),
		usage(usage),
		size(size),
		width(0),
		height(0),
		format(0),
		stride(0),
		base(base),
		lockState(lock_state),
		writeOwner(0),
		pid(getpid()),
		yuv_info(MALI_YUV_NO_INFO),
		fd(0),
		offset(0)
	{
		version = sizeof(native_handle);
		numFds = sNumFds;
		numInts = (sizeof(private_handle_t) - sizeof(native_handle)) / sizeof(int) - sNumFds;
	}


	private_handle_t(int usage, int size, void *base, int lock_state, int fb_file, int fb_offset):
		share_fd(-1),
		magic(sMagic),
		usage(usage),
		size(size),
		width(0),
		height(0),
		format(0),
		stride(0),
		base(base),
		lockState(lock_state),
		writeOwner(0),
		pid(getpid()),
		yuv_info(MALI_YUV_NO_INFO),
		fd(fb_file),
		offset(fb_offset)
	{
		version = sizeof(native_handle);
		numFds = sNumFds;
		numInts = (sizeof(private_handle_t) - sizeof(native_handle)) / sizeof(int) - sNumFds;
	}
#endif

	private_handle_t(int _flags,
			 int _size,
			 uint64_t _consumer_usage,
			 uint64_t _producer_usage,
			 int _shared_fd,
	                 int _req_format,
			 uint64_t _alloc_format,
			 int _width,
			 int _height,
			 int _backing_store_size,
	                 uint64_t _layer_count,
			 const plane_info_t *_plane_info,
			 int _stride, // 'byte_stride'
			 int _pixel_stride)
	    : share_fd(_shared_fd)
	    , size(_size)
	    , width(_width)
	    , height(_height)
	    , stride(_stride)
	    , pixel_stride(_pixel_stride)
	    , flags(_flags)
	    , req_format(_req_format)
	    , producer_usage(_producer_usage)
	    , consumer_usage(_consumer_usage)
	    , alloc_format(_alloc_format)
	    , layer_count(_layer_count)
	    , backing_store_size(_backing_store_size)
	    , allocating_pid(getpid())
	    , ref_count(1)
	{
		version = sizeof(native_handle);
		numFds = sNumFds;
		numInts = NUM_INTS_IN_PRIVATE_HANDLE;
		memcpy(plane_info, _plane_info, sizeof(plane_info_t) * MAX_PLANES);
	}

	~private_handle_t()
	{
		magic = 0;
	}

	static int validate(const native_handle *h)
	{
		private_handle_t *hnd = static_cast<private_handle_t *>(const_cast<native_handle_t *>(h) );

		if (!h || h->version != sizeof(native_handle) || h->numFds != sNumFds ||
		        h->numInts != (sizeof(private_handle_t) - sizeof(native_handle)) / sizeof(int) - sNumFds ||
		        hnd->magic != sMagic)
		{
			return -EINVAL;
		}

		return 0;
	}

	bool is_multi_plane() const
	{
		/* For multi-plane, the byte stride for the second plane will always be non-zero. */
		return (plane_info[1].alloc_width != 0);
	}

	static private_handle_t *dynamicCast(const native_handle *in)
	{
		if (validate(in) == 0)
		{
			return (private_handle_t *) in;
		}

		return NULL;
	}
#endif
};

#ifndef __cplusplus
/* Restore previous diagnostic for pedantic */
#pragma GCC diagnostic pop
#endif

#ifdef __cplusplus
static inline private_handle_t *make_private_handle(int flags, int size, uint64_t consumer_usage,
                                                    uint64_t producer_usage, int shared_fd, int required_format,
                                                    uint64_t allocated_format, int width, int height,
                                                    int backing_store_size, uint64_t layer_count,
                                                    const plane_info_t *plane_info, int byte_stride, int pixel_stride)
{
	void *mem = native_handle_create(GRALLOC_ARM_NUM_FDS, NUM_INTS_IN_PRIVATE_HANDLE);
	if (mem == nullptr)
	{
		MALI_GRALLOC_LOGE("private_handle_t allocation failed");
		return nullptr;
	}

	return new (mem) // 定位 new
	    private_handle_t(flags,
			     size,
			     consumer_usage,
			     producer_usage,
			     shared_fd,
			     required_format,
			     allocated_format,
	                     width,
			     height,
			     backing_store_size,
			     layer_count,
			     plane_info,
			     byte_stride,
			     pixel_stride);
}
#endif

#endif /* GRALLOC_PRIV_H_ */

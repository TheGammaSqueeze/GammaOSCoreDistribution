/*
 * Copyright (C) 2017 ARM Limited. All rights reserved.
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

#ifndef GRALLOC3_PRIV_H_
#define GRALLOC3_PRIV_H_

#include <linux/fb.h>
#include <hardware/gralloc1.h>
#include <inttypes.h>
#include <log/log.h>

#define AFBC_INFO_SIZE                              (sizeof(int))
#define AFBC_ENABLE                                 (0xafbc)

/* Exynos specific usages */
#define GRALLOC1_PRODUCER_USAGE_PRIVATE_NONSECURE               GRALLOC1_PRODUCER_USAGE_PRIVATE_8
#define GRALLOC1_PRODUCER_USAGE_NOZEROED                        GRALLOC1_PRODUCER_USAGE_PRIVATE_9

#define GRALLOC1_CONSUMER_USAGE_VIDEO_PRIVATE_DATA              GRALLOC1_CONSUMER_USAGE_PRIVATE_7

#define GRALLOC_USAGE_GOOGLE_IP_BO                              GRALLOC1_CONSUMER_USAGE_PRIVATE_16
#define GRALLOC_USAGE_GOOGLE_IP_MFC                             GRALLOC1_CONSUMER_USAGE_PRIVATE_17

#define GS101_GRALLOC_USAGE_TPU_INPUT                           GRALLOC1_CONSUMER_USAGE_PRIVATE_5
#define GS101_GRALLOC_USAGE_TPU_OUTPUT                          GRALLOC1_PRODUCER_USAGE_PRIVATE_3
#define GS101_GRALLOC_USAGE_CAMERA_STATS                        GRALLOC1_PRODUCER_USAGE_PRIVATE_2

/* for legacy */
#define GRALLOC_USAGE_PRIVATE_NONSECURE                         GRALLOC1_PRODUCER_USAGE_PRIVATE_NONSECURE

typedef int ion_user_handle_t;

typedef enum
{
	/*
	 * Allocation will be used as a front-buffer, which
	 * supports concurrent producer-consumer access.
	 *
	 * NOTE: Must not be used with MALI_GRALLOC_USAGE_FORCE_BACKBUFFER
	 */
	MALI_GRALLOC_USAGE_FRONTBUFFER = GRALLOC1_PRODUCER_USAGE_PRIVATE_12,

	/*
	 * Allocation will be used as a back-buffer.
	 * Use when switching from front-buffer as a workaround for Android
	 * buffer queue, which does not re-allocate for a sub-set of
	 * existing usage.
	 *
	 * NOTE: Must not be used with MALI_GRALLOC_USAGE_FRONTBUFFER.
	 */
	MALI_GRALLOC_USAGE_FORCE_BACKBUFFER = GRALLOC1_PRODUCER_USAGE_PRIVATE_13,

	/*
	 * Buffer will not be allocated with AFBC.
	 *
	 * NOTE: Not compatible with MALI_GRALLOC_USAGE_FORCE_BACKBUFFER so cannot be
	 * used when switching from front-buffer to back-buffer.
	 */
	MALI_GRALLOC_USAGE_NO_AFBC = GRALLOC1_PRODUCER_USAGE_PRIVATE_1,

	/* Custom alignment for AFBC headers.
	 *
	 * NOTE: due to usage flag overlap, AFBC_PADDING cannot be used with FORCE_BACKBUFFER.
	 */
	MALI_GRALLOC_USAGE_AFBC_PADDING = GRALLOC1_PRODUCER_USAGE_PRIVATE_14,
	/* Private format usage.
	 * 'format' argument to allocation function will be interpreted in a
	 * private manner and must be constructed via GRALLOC_PRIVATE_FORMAT_WRAPPER_*
	 * macros which pack base format and AFBC format modifiers into 32-bit value.
	 */
	MALI_GRALLOC_USAGE_PRIVATE_FORMAT = GRALLOC1_PRODUCER_USAGE_PRIVATE_15,

	/* YUV only. */
	MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_DEFAULT = 0,
	MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT601 = GRALLOC1_PRODUCER_USAGE_PRIVATE_18,
	MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT709 = GRALLOC1_PRODUCER_USAGE_PRIVATE_19,
	MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT2020 = (GRALLOC1_PRODUCER_USAGE_PRIVATE_18 | GRALLOC1_PRODUCER_USAGE_PRIVATE_19),
	MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_MASK = MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT2020,

	MALI_GRALLOC_USAGE_RANGE_DEFAULT = 0,
	MALI_GRALLOC_USAGE_RANGE_NARROW = GRALLOC1_PRODUCER_USAGE_PRIVATE_16,
	MALI_GRALLOC_USAGE_RANGE_WIDE = GRALLOC1_PRODUCER_USAGE_PRIVATE_17,
	MALI_GRALLOC_USAGE_RANGE_MASK = (GRALLOC1_PRODUCER_USAGE_PRIVATE_16 | GRALLOC1_PRODUCER_USAGE_PRIVATE_17),
} mali_gralloc_usage_type;

typedef struct
{
	struct hw_module_t common;
} gralloc1_module_t;

typedef enum
{
	MALI_DPY_TYPE_UNKNOWN = 0,
	MALI_DPY_TYPE_CLCD,
	MALI_DPY_TYPE_HDLCD
} mali_dpy_type;

typedef enum
{
	MALI_YUV_NO_INFO,
	MALI_YUV_BT601_NARROW,
	MALI_YUV_BT601_WIDE,
	MALI_YUV_BT709_NARROW,
	MALI_YUV_BT709_WIDE,
	MALI_YUV_BT2020_NARROW,
	MALI_YUV_BT2020_WIDE
} mali_gralloc_yuv_info;

typedef struct private_module
{
	gralloc1_module_t base;

	struct private_handle_t *framebuffer;
	uint32_t flags;
	uint32_t numBuffers;
	uint32_t bufferMask;
	pthread_mutex_t lock;
	buffer_handle_t currentBuffer;
	mali_dpy_type dpy_type;

	struct fb_var_screeninfo info;
	struct fb_fix_screeninfo finfo;
	float xdpi;
	float ydpi;
	float fps;
	int swapInterval;
	uint64_t fbdev_format;
	int ionfd;
} private_module_t;

/*
 * Maximum number of pixel format planes.
 * Plane [0]: Single plane formats (inc. RGB, YUV) and Y
 * Plane [1]: U/V, UV
 * Plane [2]: V/U
 */
#define MAX_PLANES 3

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

struct private_handle_t : public native_handle
{
	enum
	{
		PRIV_FLAGS_FRAMEBUFFER            = 1U << 0,
		PRIV_FLAGS_USES_ION_COMPOUND_HEAP = 1U << 1,
		PRIV_FLAGS_USES_ION               = 1U << 2,
		PRIV_FLAGS_USES_ION_DMA_HEAP      = 1U << 3,
		PRIV_FLAGS_USES_2PRIVATE_DATA     = 1U << 4,
		PRIV_FLAGS_USES_3PRIVATE_DATA     = 1U << 5,
	};

	/*
	 * Shared file descriptor for dma_buf sharing. This must be the first element in the
	 * structure so that binder knows where it is and can properly share it between
	 * processes.
	 * DO NOT MOVE THIS ELEMENT!
	 */
	int fd;
	int fd1;
	int fd2;
	int fd3;
	int fd4;

	// ints
	int magic;
	int flags;

	/*
	 * Input properties.
	 *
	 * req_format: Pixel format, base + private modifiers.
	 * width/height: Buffer dimensions.
	 * producer/consumer_usage: Buffer usage (indicates IP)
	 */
	int width;
	int height;
	/* LSI integration: Needed by Camera */
	int frameworkFormat;

	uint64_t producer_usage;
	uint64_t consumer_usage;

	union
	{
		int format;
		uint64_t internal_format;
	};

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
	uint64_t alloc_format;
	union
	{
		plane_info_t plane_info[MAX_PLANES];
		struct
		{
			int plane_offset;
			int byte_stride;
			int alloc_width;
			int vstride;
		};
	};
	int size;
	int size1;
	int size2;
	uint32_t stride;
	uint32_t layer_count;

	union
	{
		void *base;
		uint64_t bases[3];
	};

	uint64_t backing_store_id;
	int backing_store_size;
	int cpu_read;               /**< Buffer is locked for CPU read when non-zero. */
	int cpu_write;              /**< Buffer is locked for CPU write when non-zero. */
	int allocating_pid;
	int remote_pid;
	int ref_count;
	// locally mapped shared attribute area
	union
	{
		void *attr_base;
		uint64_t padding3;
	};

	mali_gralloc_yuv_info yuv_info;

	// Following members is for framebuffer only
	int fb_fd;
	union
	{
		off_t offset;
		uint64_t padding4;
	};

	/*
	 * min_pgsz denotes minimum phys_page size used by this buffer.
	 * if buffer memory is physical contiguous set min_pgsz to buff->size
	 * if not sure buff's real phys_page size, you can use SZ_4K for safe.
	 */
	int min_pgsz;

	int is_compressible;

	ion_user_handle_t ion_handles[3];

	int     PRIVATE_1 = 0;
	int     PRIVATE_2 = 0;
	int     plane_count = 0;

	static int validate(const native_handle *h)
	{
#define GRALLOC_ARM_NUM_FDS 2
#define NUM_INTS_IN_PRIVATE_HANDLE ((sizeof(struct private_handle_t) - sizeof(native_handle)) / sizeof(int) - GRALLOC_ARM_NUM_FDS)

		static const int sNumFds = GRALLOC_ARM_NUM_FDS;
		static const int sMagic = 0x3141592;
		const private_handle_t *hnd = (const private_handle_t *)h;

		if (!h || h->version != sizeof(native_handle) ||
				hnd->numInts + hnd->numFds != NUM_INTS_IN_PRIVATE_HANDLE + sNumFds ||
				hnd->magic != sMagic)
		{
			return -EINVAL;
		}

		return 0;
	}

	static private_handle_t *dynamicCast(const native_handle *in)
	{
		if (validate(in) == 0)
		{
			return (private_handle_t *)in;
		}

		return NULL;
	}

	int get_num_ion_fds() const
	{
		return numFds - 1;
	}

	void dump(const char *str) const
	{
		ALOGD("[%s] "
				"fd(%d %d %d %d) "
				"flags(%d) "
				"wh(%d %d) "
				"req_format(0x%x) "
				"usage_pc(0x%" PRIx64 " 0x%" PRIx64 ") "
				"format(0x%x) "
				"interal_format(0x%" PRIx64 ") "
				"stride(%d) byte_stride(%d) internal_wh(%d %d) "
				"alloc_format(0x%" PRIx64 ") "
				"size(%d %d %d) "
				"layer_count(%d) plane_count(%d)"
				"bases(0x%" PRIx64 " 0x%" PRIx64 " 0x%" PRIx64 ") "
				"\n",
				str,
				fd, fd1, fd2, fd3,
				flags,
				width, height,
				frameworkFormat,
				producer_usage, consumer_usage,
				format, internal_format,
				stride, plane_info[0].byte_stride, plane_info[0].alloc_width, plane_info[0].alloc_height,
				alloc_format,
				size, size1, size2,
				layer_count, plane_count,
				bases[0], bases[1], bases[2]
			);
	}
};

#endif /* GRALLOC3_PRIV_H_ */

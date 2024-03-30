/*
 * Copyright (C) 2016-2020 ARM Limited. All rights reserved.
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

#include <string.h>
#include <errno.h>
#include <inttypes.h>
#include <pthread.h>
#include <stdlib.h>
#include <limits.h>

#include <log/log.h>
#include <cutils/atomic.h>

#include <linux/dma-buf.h>
#include <vector>
#include <sys/ioctl.h>

#include <hardware/hardware.h>
#include <hardware/gralloc1.h>

#include <hardware/exynos/ion.h>
#include <hardware/exynos/dmabuf_container.h>

#include <BufferAllocator/BufferAllocator.h>
#include "mali_gralloc_buffer.h"
#include "gralloc_helper.h"
#include "mali_gralloc_formats.h"
#include "mali_gralloc_usages.h"
#include "core/format_info.h"
#include "core/mali_gralloc_bufferdescriptor.h"
#include "core/mali_gralloc_bufferallocation.h"

#include "mali_gralloc_ion.h"

#include <array>

#define INIT_ZERO(obj) (memset(&(obj), 0, sizeof((obj))))

#define HEAP_MASK_FROM_ID(id) (1 << id)
#define HEAP_MASK_FROM_TYPE(type) (1 << type)

#if defined(ION_HEAP_SECURE_MASK)
#if (HEAP_MASK_FROM_TYPE(ION_HEAP_TYPE_SECURE) != ION_HEAP_SECURE_MASK)
#error "ION_HEAP_TYPE_SECURE value is not compatible with ION_HEAP_SECURE_MASK"
#endif
#endif

static const char kDmabufSensorDirectHeapName[] = "sensor_direct_heap";
static const char kDmabufFaceauthTpuHeapName[] = "faceauth_tpu-secure";
static const char kDmabufFaceauthImgHeapName[] = "faimg-secure";
static const char kDmabufFaceauthRawImgHeapName[] = "farawimg-secure";
static const char kDmabufFaceauthPrevHeapName[] = "faprev-secure";
static const char kDmabufFaceauthModelHeapName[] = "famodel-secure";
static const char kDmabufVframeSecureHeapName[] = "vframe-secure";
static const char kDmabufVstreamSecureHeapName[] = "vstream-secure";

struct ion_device
{
	int client()
	{
		return ion_client;
	}

	static void close()
	{
		ion_device &dev = get_inst();
		if (dev.ion_client >= 0)
		{
			exynos_ion_close(dev.ion_client);
			dev.ion_client = -1;
		}

		dev.buffer_allocator.reset();
	}

	static ion_device *get()
	{
		ion_device &dev = get_inst();
		if (!dev.buffer_allocator)
		{
			dev.buffer_allocator = std::make_unique<BufferAllocator>();
			if (!dev.buffer_allocator)
				ALOGE("Unable to create BufferAllocator object");
		}

		if (dev.ion_client < 0)
		{
			if (dev.open_and_query_ion() != 0)
			{
				close();
			}
		}

		if (dev.ion_client < 0)
		{
			return nullptr;
		}
		return &dev;
	}

	/*
	 *  Identifies a heap and retrieves file descriptor from ION for allocation
	 *
	 * @param usage     [in]    Producer and consumer combined usage.
	 * @param size      [in]    Requested buffer size (in bytes).
	 * @param heap_type [in]    Requested heap type.
	 * @param flags     [in]    ION allocation attributes defined by ION_FLAG_*.
	 * @param min_pgsz  [out]   Minimum page size (in bytes).
	 *
	 * @return File handle which can be used for allocation, on success
	 *         -1, otherwise.
	 */
	int alloc_from_ion_heap(uint64_t usage, size_t size, unsigned int flags, int *min_pgsz);

	/*
	 *  Signals the start or end of a region where the CPU is accessing a
	 *  buffer, allowing appropriate cache synchronization.
	 *
	 * @param fd        [in]    fd for the buffer
	 * @param read      [in]    True if the CPU is reading from the buffer
	 * @param write     [in]    True if the CPU is writing to the buffer
	 * @param start     [in]    True if the CPU has not yet performed the
	 *                          operations; false if the operations are
	 *                          completed.
	 *
	 * @return 0 on success; an error code otherwise.
	 */
	int sync(int fd, bool read, bool write, bool start);

private:
	int ion_client;
	std::unique_ptr<BufferAllocator> buffer_allocator;

	ion_device()
	    : ion_client(-1)
	{
	}

	static ion_device& get_inst()
	{
		static ion_device dev;
		return dev;
	}

	/*
	 * Opens the ION module. Queries heap information and stores it for later use
	 *
	 * @return              0 in case of success
	 *                      -1 for all error cases
	 */
	int open_and_query_ion();

	/*
	 *  Allocates in the DMA-BUF heap with name @heap_name. If allocation fails from
	 *  the DMA-BUF heap or if it does not exist, falls back to an ION heap of the
	 *  same name.
	 *
	 * @param heap_name [in]    DMA-BUF heap name for allocation
	 * @param size      [in]    Requested buffer size (in bytes).
	 * @param flags     [in]    ION allocation attributes defined by ION_FLAG_* to
	 *                          be used for ION allocations. Will not be used with
	 *                          DMA-BUF heaps since the framework does not support
	 *                          allocation flags.
	 *
	 * @return fd of the allocated buffer on success, -1 otherwise;
	 */

	int alloc_from_dmabuf_heap(const std::string& heap_name, size_t size, unsigned int flags);
};

static void set_ion_flags(uint64_t usage, unsigned int *ion_flags)
{
	if (ion_flags == nullptr)
		return;

	if ((usage & GRALLOC_USAGE_SW_READ_MASK) == GRALLOC_USAGE_SW_READ_OFTEN)
	{
		*ion_flags |= ION_FLAG_CACHED;
	}

	// DRM or Secure Camera
	if (usage & (GRALLOC_USAGE_PROTECTED))
	{
		*ion_flags |= ION_FLAG_PROTECTED;
	}

	/* TODO: used for exynos3830. Add this as an option to Android.bp */
#if defined(GRALLOC_SCALER_WFD) && GRALLOC_SCALER_WFD == 1
	if (usage & GRALLOC_USAGE_PRIVATE_NONSECURE && usage & GRALLOC_USAGE_HW_COMPOSER)
	{
		*ion_flags |= ION_FLAG_PROTECTED;
	}
#endif
	/* Sensor direct channels require uncached allocations. */
	if (usage & GRALLOC_USAGE_SENSOR_DIRECT_DATA)
	{
		*ion_flags &= ~ION_FLAG_CACHED;
	}
}

static unsigned int select_faceauth_heap_mask(uint64_t usage)
{
	struct HeapSpecifier
	{
		uint64_t      usage_bits; // exact match required
		unsigned int  mask;
	};

	static constexpr std::array<HeapSpecifier, 5> faceauth_heaps =
	{{
		{ // isp_image_heap
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_CAMERA_WRITE | GS101_GRALLOC_USAGE_TPU_INPUT,
			EXYNOS_ION_HEAP_FA_IMG_MASK
		},
		{ // isp_internal_heap
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_CAMERA_WRITE | GRALLOC_USAGE_HW_CAMERA_READ,
			EXYNOS_ION_HEAP_FA_RAWIMG_MASK
		},
		{ // isp_preview_heap
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_CAMERA_WRITE | GRALLOC_USAGE_HW_COMPOSER |
            GRALLOC_USAGE_HW_TEXTURE,
			EXYNOS_ION_HEAP_FA_PREV_MASK
		},
		{ // ml_model_heap
			GRALLOC_USAGE_PROTECTED | GS101_GRALLOC_USAGE_TPU_INPUT,
			EXYNOS_ION_HEAP_FA_MODEL_MASK
		},
		{ // tpu_heap
			GRALLOC_USAGE_PROTECTED | GS101_GRALLOC_USAGE_TPU_OUTPUT | GS101_GRALLOC_USAGE_TPU_INPUT,
			EXYNOS_ION_HEAP_FA_TPU_MASK
		}
	}};

	for (const HeapSpecifier &heap : faceauth_heaps)
	{
		if (usage == heap.usage_bits)
		{
			ALOGV("Using FaceAuth heap mask 0x%x for usage 0x%" PRIx64 "\n",
			      heap.mask, usage);
			return heap.mask;
		}
	}

	return 0;
}

static unsigned int select_heap_mask(uint64_t usage)
{
	if (unsigned int faceauth_heap_mask = select_faceauth_heap_mask(usage);
	    faceauth_heap_mask != 0)
	{
		return faceauth_heap_mask;
	}

	unsigned int heap_mask;

	if (usage & GRALLOC_USAGE_PROTECTED)
	{
		if (usage & GRALLOC_USAGE_PRIVATE_NONSECURE)
		{
			heap_mask = EXYNOS_ION_HEAP_SYSTEM_MASK;
		}
		else if ((usage & GRALLOC_USAGE_HW_COMPOSER) &&
			!(usage & GRALLOC_USAGE_HW_TEXTURE) &&
			!(usage & GRALLOC_USAGE_HW_RENDER))
		{
			heap_mask = EXYNOS_ION_HEAP_VIDEO_SCALER_MASK;
		}
		else
		{
			heap_mask = EXYNOS_ION_HEAP_VIDEO_FRAME_MASK;
		}
	}
	/* TODO: used for exynos3830. Add this as a an option to Android.bp */
#if defined(GRALLOC_SCALER_WFD) && GRALLOC_SCALER_WFD == 1
	else if (usage & GRALLOC_USAGE_PRIVATE_NONSECURE && usage & GRALLOC_USAGE_HW_COMPOSER)
	{
		heap_mask = EXYNOS_ION_HEAP_EXT_UI_MASK;
	}
#endif
	else if (usage & GRALLOC_USAGE_SENSOR_DIRECT_DATA)
	{
		heap_mask = EXYNOS_ION_HEAP_SENSOR_DIRECT_MASK;
	}
	else
	{
		heap_mask = EXYNOS_ION_HEAP_SYSTEM_MASK;
	}

	return heap_mask;
}

/*
 * Selects a DMA-BUF heap name.
 *
 * @param heap_mask     [in]    heap_mask for which the equivalent DMA-BUF heap
 *                              name must be found.
 *
 * @return the name of the DMA-BUF heap equivalent to the ION heap of mask
 *         @heap_mask.
 *
 */
static std::string select_dmabuf_heap(unsigned int heap_mask)
{
	switch (heap_mask) {
		case EXYNOS_ION_HEAP_SENSOR_DIRECT_MASK:
			return kDmabufSensorDirectHeapName;
		case EXYNOS_ION_HEAP_FA_TPU_MASK:
			return kDmabufFaceauthTpuHeapName;
		case EXYNOS_ION_HEAP_FA_IMG_MASK:
			return kDmabufFaceauthImgHeapName;
		case EXYNOS_ION_HEAP_FA_RAWIMG_MASK:
			return kDmabufFaceauthRawImgHeapName;
		case EXYNOS_ION_HEAP_FA_PREV_MASK:
			return kDmabufFaceauthPrevHeapName;
		case EXYNOS_ION_HEAP_FA_MODEL_MASK:
			return kDmabufFaceauthModelHeapName;
		case EXYNOS_ION_HEAP_VIDEO_FRAME_MASK:
			return kDmabufVframeSecureHeapName;
		case EXYNOS_ION_HEAP_VIDEO_STREAM_MASK:
			return kDmabufVstreamSecureHeapName;
		default:
			return {};
	}
}

int ion_device::alloc_from_dmabuf_heap(const std::string& heap_name, size_t size,
				       unsigned int flags)
{
	if (!buffer_allocator)
	{
		return -1;
	}

	int shared_fd = buffer_allocator->Alloc(heap_name, size, flags);
	if (shared_fd < 0)
	{
		ALOGE("Allocation failed for heap %s error: %d\n", heap_name.c_str(), shared_fd);
	}

	return shared_fd;
}

int ion_device::alloc_from_ion_heap(uint64_t usage, size_t size, unsigned int flags, int *min_pgsz)
{
	/* TODO: remove min_pgsz? I don't think this is useful on Exynos */
	if (size == 0 || min_pgsz == NULL)
	{
		return -1;
	}

	unsigned int heap_mask = select_heap_mask(usage);

	int shared_fd;
	auto dmabuf_heap_name = select_dmabuf_heap(heap_mask);
	if (!dmabuf_heap_name.empty())
	{
		shared_fd = alloc_from_dmabuf_heap(dmabuf_heap_name, size, flags);
	}
	else
	{
		if (ion_client < 0)
		{
			return -1;
		}

		shared_fd = exynos_ion_alloc(ion_client, size, heap_mask, flags);
	}

	*min_pgsz = SZ_4K;

	return shared_fd;
}

int ion_device::open_and_query_ion()
{
	if (ion_client >= 0)
	{
		MALI_GRALLOC_LOGW("ION device already open");
		return 0;
	}

	ion_client = exynos_ion_open();
	if (ion_client < 0)
	{
		MALI_GRALLOC_LOGE("ion_open failed with %s", strerror(errno));
		return -1;
	}

	return 0;
}

static SyncType sync_type_for_flags(const bool read, const bool write)
{
	if (read && !write)
	{
		return SyncType::kSyncRead;
	}
	else if (write && !read)
	{
		return SyncType::kSyncWrite;
	}
	else
	{
		// Deliberately also allowing "not sure" to map to ReadWrite.
		return SyncType::kSyncReadWrite;
	}
}

int ion_device::sync(const int fd, const bool read, const bool write, const bool start)
{
	if (!buffer_allocator)
	{
		return -1;
	}

	if (start)
	{
		return buffer_allocator->CpuSyncStart(fd, sync_type_for_flags(read, write));
	}
	else
	{
		return buffer_allocator->CpuSyncEnd(fd, sync_type_for_flags(read, write));
	}
}

static int mali_gralloc_ion_sync(const private_handle_t * const hnd,
                                       const bool read,
                                       const bool write,
                                       const bool start)
{
	if (hnd == NULL)
	{
		return -EINVAL;
	}

	ion_device *dev = ion_device::get();
	if (!dev)
	{
		return -1;
	}

	for (int i = 0; i < hnd->fd_count; i++)
	{
		const int fd = hnd->fds[i];
		if (const int ret = dev->sync(fd, read, write, start))
		{
			return ret;
		}
	}

	return 0;
}


/*
 * Signal start of CPU access to the DMABUF exported from ION.
 *
 * @param hnd   [in]    Buffer handle
 * @param read  [in]    Flag indicating CPU read access to memory
 * @param write [in]    Flag indicating CPU write access to memory
 *
 * @return              0 in case of success
 *                      errno for all error cases
 */
int mali_gralloc_ion_sync_start(const private_handle_t * const hnd,
                                const bool read,
                                const bool write)
{
	return mali_gralloc_ion_sync(hnd, read, write, true);
}


/*
 * Signal end of CPU access to the DMABUF exported from ION.
 *
 * @param hnd   [in]    Buffer handle
 * @param read  [in]    Flag indicating CPU read access to memory
 * @param write [in]    Flag indicating CPU write access to memory
 *
 * @return              0 in case of success
 *                      errno for all error cases
 */
int mali_gralloc_ion_sync_end(const private_handle_t * const hnd,
                              const bool read,
                              const bool write)
{
	return mali_gralloc_ion_sync(hnd, read, write, false);
}


void mali_gralloc_ion_free(private_handle_t * const hnd)
{
	for (int i = 0; i < hnd->fd_count; i++)
	{
		void* mapped_addr = reinterpret_cast<void*>(hnd->bases[i]);

		/* Buffer might be unregistered already so we need to assure we have a valid handle */
		if (mapped_addr != nullptr)
		{
			if (munmap(mapped_addr, hnd->alloc_sizes[i]) != 0)
			{
				/* TODO: more detailed error logs */
				MALI_GRALLOC_LOGE("Failed to munmap handle %p", hnd);
			}
		}
		close(hnd->fds[i]);
		hnd->fds[i] = -1;
		hnd->bases[i] = 0;
	}
	delete hnd;
}

static void mali_gralloc_ion_free_internal(buffer_handle_t * const pHandle,
                                           const uint32_t num_hnds)
{
	for (uint32_t i = 0; i < num_hnds; i++)
	{
		if (pHandle[i] != NULL)
		{
			private_handle_t * const hnd = (private_handle_t * const)pHandle[i];
			mali_gralloc_ion_free(hnd);
		}
	}
}

int mali_gralloc_ion_allocate_attr(private_handle_t *hnd)
{
	ion_device *dev = ion_device::get();
	if (!dev)
	{
		return -1;
	}

	int idx = hnd->get_share_attr_fd_index();
	int ion_flags = 0;
	int min_pgsz;
	uint64_t usage = GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN;

	ion_flags = ION_FLAG_CACHED;

	hnd->fds[idx] = dev->alloc_from_ion_heap(usage, hnd->attr_size, ion_flags, &min_pgsz);
	if (hnd->fds[idx] < 0)
	{
		MALI_GRALLOC_LOGE("ion_alloc failed from client ( %d )", dev->client());
		return -1;
	}

	hnd->incr_numfds(1);

	return 0;
}

/*
 *  Allocates ION buffers
 *
 * @param descriptors     [in]    Buffer request descriptors
 * @param numDescriptors  [in]    Number of descriptors
 * @param pHandle         [out]   Handle for each allocated buffer
 * @param shared_backend  [out]   Shared buffers flag
 *
 * @return File handle which can be used for allocation, on success
 *         -1, otherwise.
 */
int mali_gralloc_ion_allocate(const gralloc_buffer_descriptor_t *descriptors,
                              uint32_t numDescriptors, buffer_handle_t *pHandle,
                              bool *shared_backend, int ion_fd)
{
	GRALLOC_UNUSED(shared_backend);

	unsigned int priv_heap_flag = 0;
	uint64_t usage;
	uint32_t i;
	unsigned int ion_flags = 0;
	int min_pgsz = 0;
	int fds[5] = {-1, -1, -1, -1, -1};

	ion_device *dev = ion_device::get();
	if (!dev)
	{
		return -1;
	}

	for (i = 0; i < numDescriptors; i++)
	{
		buffer_descriptor_t *bufDescriptor = (buffer_descriptor_t *)(descriptors[i]);
		usage = bufDescriptor->consumer_usage | bufDescriptor->producer_usage;

		ion_flags = 0;
		set_ion_flags(usage, &ion_flags);

		for (int fidx = 0; fidx < bufDescriptor->fd_count; fidx++)
		{
			if (ion_fd >= 0 && fidx == 0) {
				fds[fidx] = ion_fd;
			} else {
				fds[fidx] = dev->alloc_from_ion_heap(usage, bufDescriptor->alloc_sizes[fidx], ion_flags, &min_pgsz);
			}
			if (fds[fidx] < 0)
			{
				MALI_GRALLOC_LOGE("ion_alloc failed from client ( %d )", dev->client());

				for (int cidx = 0; cidx < fidx; cidx++)
				{
					close(fds[cidx]);
				}

				/* need to free already allocated memory. not just this one */
				mali_gralloc_ion_free_internal(pHandle, numDescriptors);

				return -1;
			}
		}

		private_handle_t *hnd = new private_handle_t(
		    priv_heap_flag,
		    bufDescriptor->alloc_sizes,
		    bufDescriptor->consumer_usage, bufDescriptor->producer_usage,
		    fds, bufDescriptor->fd_count,
		    bufDescriptor->hal_format, bufDescriptor->alloc_format,
		    bufDescriptor->width, bufDescriptor->height, bufDescriptor->pixel_stride,
		    bufDescriptor->layer_count, bufDescriptor->plane_info);

		if (NULL == hnd)
		{
			MALI_GRALLOC_LOGE("Private handle could not be created for descriptor:%d in non-shared usecase", i);

			/* Close the obtained shared file descriptor for the current handle */
			for (int j = 0; j < bufDescriptor->fd_count; j++)
			{
				close(fds[j]);
			}

			mali_gralloc_ion_free_internal(pHandle, numDescriptors);
			return -1;
		}

		pHandle[i] = hnd;
	}

#if defined(GRALLOC_INIT_AFBC) && (GRALLOC_INIT_AFBC == 1)
	unsigned char *cpu_ptr = NULL;
	for (i = 0; i < numDescriptors; i++)
	{
		buffer_descriptor_t *bufDescriptor = (buffer_descriptor_t *)(descriptors[i]);
		private_handle_t *hnd = (private_handle_t *)(pHandle[i]);

		usage = bufDescriptor->consumer_usage | bufDescriptor->producer_usage;

		if ((bufDescriptor->alloc_format & MALI_GRALLOC_INTFMT_AFBCENABLE_MASK)
			&& !(usage & GRALLOC_USAGE_PROTECTED))
		{
			/* TODO: only map for AFBC buffers */
			cpu_ptr =
			    (unsigned char *)mmap(NULL, bufDescriptor->alloc_sizes[0], PROT_READ | PROT_WRITE, MAP_SHARED, hnd->fds[0], 0);

			if (MAP_FAILED == cpu_ptr)
			{
				MALI_GRALLOC_LOGE("mmap failed from client ( %d ), fd ( %d )", dev->client(), hnd->fds[0]);
				mali_gralloc_ion_free_internal(pHandle, numDescriptors);
				return -1;
			}

			mali_gralloc_ion_sync_start(hnd, true, true);

			/* For separated plane YUV, there is a header to initialise per plane. */
			const plane_info_t *plane_info = bufDescriptor->plane_info;
			const bool is_multi_plane = hnd->is_multi_plane();
			for (int i = 0; i < MAX_PLANES && (i == 0 || plane_info[i].byte_stride != 0); i++)
			{
				init_afbc(cpu_ptr + plane_info[i].offset,
				          bufDescriptor->alloc_format,
				          is_multi_plane,
				          plane_info[i].alloc_width,
				          plane_info[i].alloc_height);
			}

			mali_gralloc_ion_sync_end(hnd, true, true);

			munmap(cpu_ptr, bufDescriptor->alloc_sizes[0]);
		}
	}
#endif

	return 0;
}


int mali_gralloc_ion_map(private_handle_t *hnd)
{
	uint64_t usage = hnd->producer_usage | hnd->consumer_usage;

	/* Do not allow cpu access to secure buffers */
	if (usage & (GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_NOZEROED)
			&& !(usage & GRALLOC_USAGE_PRIVATE_NONSECURE))
	{
		return 0;
	}

	for (int fidx = 0; fidx < hnd->fd_count; fidx++) {
		unsigned char *mappedAddress =
			(unsigned char *)mmap(NULL, hnd->alloc_sizes[fidx], PROT_READ | PROT_WRITE,
					MAP_SHARED, hnd->fds[fidx], 0);

		if (MAP_FAILED == mappedAddress)
		{
			int err = errno;
			MALI_GRALLOC_LOGE("mmap( fds[%d]:%d size:%" PRIu64 " ) failed with %s",
					fidx, hnd->fds[fidx], hnd->alloc_sizes[fidx], strerror(err));
			hnd->dump("map fail");

			for (int cidx = 0; cidx < fidx; fidx++)
			{
				munmap((void*)hnd->bases[cidx], hnd->alloc_sizes[cidx]);
				hnd->bases[cidx] = 0;
			}

			return -err;
		}

		hnd->bases[fidx] = uintptr_t(mappedAddress);
	}

	return 0;
}

int import_exynos_ion_handles(private_handle_t *hnd)
{
	int retval = -1;

	ion_device *dev = ion_device::get();

	for (int idx = 0; idx < hnd->fd_count; idx++)
	{
		if (hnd->fds[idx] >= 0)
		{
			retval = exynos_ion_import_handle(dev->client(), hnd->fds[idx], &hnd->ion_handles[idx]);
			if (retval)
			{
				MALI_GRALLOC_LOGE("error importing ion_handle. ion_client(%d), ion_handle[%d](%d) format(%s %#" PRIx64 ")",
				     dev->client(), idx, hnd->ion_handles[idx], format_name(hnd->alloc_format), hnd->alloc_format);
				goto error;
			}
		}
	}

	return retval;

error:
	for (int idx = 0; idx < hnd->fd_count; idx++)
	{
		if (hnd->ion_handles[idx])
		{
			exynos_ion_free_handle(dev->client(), hnd->ion_handles[idx]);
		}
	}

	return retval;
}

void free_exynos_ion_handles(private_handle_t *hnd)
{
	ion_device *dev = ion_device::get();

	for (int idx = 0; idx < hnd->fd_count; idx++)
	{
		if (hnd->ion_handles[idx])
		{
			if (hnd->ion_handles[idx] &&
			    exynos_ion_free_handle(dev->client(), hnd->ion_handles[idx]))
			{
				MALI_GRALLOC_LOGE("error freeing ion_handle. ion_client(%d), ion_handle[%d](%d) format(%s %#" PRIx64 ")",
					dev->client(), idx, hnd->ion_handles[idx], format_name(hnd->alloc_format), hnd->alloc_format);
			}
		}
	}
}


void mali_gralloc_ion_unmap(private_handle_t *hnd)
{
	for (int i = 0; i < hnd->fd_count; i++)
	{
		int err = 0;

		if (hnd->bases[i])
		{
			err = munmap((void*)hnd->bases[i], hnd->alloc_sizes[i]);
		}

		if (err)
		{
			MALI_GRALLOC_LOGE("Could not munmap base:%p size:%" PRIu64 " '%s'",
					(void*)hnd->bases[i], hnd->alloc_sizes[i], strerror(errno));
		}
		else
		{
			hnd->bases[i] = 0;
		}
	}

	hnd->cpu_read = 0;
	hnd->cpu_write = 0;
}

void mali_gralloc_ion_close(void)
{
	ion_device::close();
}


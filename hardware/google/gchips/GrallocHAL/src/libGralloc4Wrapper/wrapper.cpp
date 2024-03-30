#include "gralloc4/gralloc_vendor_interface.h"
#include <vector>
#include <sys/stat.h>

#include "core/format_info.h"
#include "core/mali_gralloc_bufferdescriptor.h"
#include "core/mali_gralloc_bufferallocation.h"
#include "core/mali_gralloc_reference.h"
#include "allocator/mali_gralloc_ion.h"
#include "hidl_common/SharedMetadata.h"
#include "gralloc_priv.h"

namespace android::hardware::graphics::allocator::priv {

struct Descriptor {
    unsigned int size = 0;
    uint64_t producer_usage = 0;
    uint64_t consumer_usage = 0;

    struct PlaneDescriptor {
        int fd = -1;
        size_t size = 0;
        off_t offset = 0;
        int stride_byte = 0;
    };
    std::vector<PlaneDescriptor> planes;

    int width = 0;
    int height = 0;
    int stride_pixel = 0;
    int format = 0;
};

Descriptor *createDescriptor() { return new Descriptor(); }
void deleteDescriptor(Descriptor *descriptor) { delete descriptor; }

void setProducerUsage(Descriptor &descriptor, uint64_t usage) {
    descriptor.producer_usage = usage;
}

void setConsumerUsage(Descriptor &descriptor, uint64_t usage) {
    descriptor.consumer_usage = usage;
}

void setPlaneCount(Descriptor &descriptor, int count) {
    descriptor.planes.resize(count);
}

void setPlane(Descriptor &descriptor, int index, int fd, size_t size, off_t offset, int stride_byte) {
    descriptor.planes[index].fd = fd;
    descriptor.planes[index].size = size;
    descriptor.planes[index].offset = offset;
    descriptor.planes[index].stride_byte = stride_byte;
}

void setWidth(Descriptor &descriptor, int width) {
    descriptor.width = width;
}

void setHeight(Descriptor &descriptor, int height) {
    descriptor.height = height;
}

void setStridePixel(Descriptor &descriptor, int stride_pixel) {
    descriptor.stride_pixel = stride_pixel;
}

void setFormat(Descriptor &descriptor, int format) {
    descriptor.format = format;
}

buffer_handle_t createNativeHandle(const Descriptor &descriptor) {
    for (int i = 0; i < descriptor.planes.size(); ++i) {
        struct stat st;
        fstat(descriptor.planes[i].fd, &st);
        off64_t fd_size = st.st_size;
        if (fd_size < descriptor.planes[i].size) {
            ALOGE("libGralloc4Wrapper: createNativeHandle failed: plane[%d] requested size greater than fd size.",
                i);
            return nullptr;
        }
    }

    buffer_descriptor_t buffer_descriptor;

    buffer_descriptor.pixel_stride = descriptor.stride_pixel;
    buffer_descriptor.width = descriptor.width;
    buffer_descriptor.height = descriptor.height;
    buffer_descriptor.layer_count = 1;
    buffer_descriptor.hal_format = buffer_descriptor.alloc_format
        = descriptor.format;
    buffer_descriptor.producer_usage = descriptor.producer_usage;
    buffer_descriptor.consumer_usage = descriptor.consumer_usage;
    buffer_descriptor.format_type = MALI_GRALLOC_FORMAT_TYPE_USAGE;
    buffer_descriptor.signature = sizeof(buffer_descriptor_t);

    buffer_descriptor.fd_count = buffer_descriptor.plane_count
        = descriptor.planes.size();
    for (int i = 0; i < descriptor.planes.size(); ++i) {
        buffer_descriptor.alloc_sizes[i] = descriptor.planes[i].size;
    }

    auto format_index = get_format_index(descriptor.format);
    if (format_index == -1) {
        ALOGE("libGralloc4Wrapper: invalid format 0x%x",
            descriptor.format);
        return 0;
    }
    for (int i = 0; i < descriptor.planes.size(); ++i) {
        uint8_t bpp = formats[format_index].bpp[i];
        if (bpp == 0) {
            ALOGE("libGralloc4Wrapper: format 0x%x has bpp[%d]=0",
                descriptor.format, i);
            return nullptr;
        }
        buffer_descriptor.plane_info[i] = {
            .byte_stride = static_cast<uint32_t>((descriptor.planes[i].stride_byte * bpp) / 8),
            .alloc_width = buffer_descriptor.width,
            .alloc_height = buffer_descriptor.height,
        };
    }

    if (mali_gralloc_derive_format_and_size(&buffer_descriptor)) {
        ALOGE("libGralloc4Wrapper: mali_gralloc_derive_format_and_size failed");
        return nullptr;
    }

    const gralloc_buffer_descriptor_t gralloc_buffer_descriptor =
        reinterpret_cast<const gralloc_buffer_descriptor_t>(&buffer_descriptor);

    buffer_handle_t tmp_buffer;
    bool shared_backend;
    // TODO(modan@, make mali_gralloc_ion_allocate accept multiple fds)
    {
        int result = mali_gralloc_buffer_allocate(&gralloc_buffer_descriptor, 1, &tmp_buffer,
            &shared_backend, descriptor.planes[0].fd);
        if (result < 0) {
            ALOGE("mali_gralloc_buffer_allocate failed");
            return nullptr;
        }
    }

    private_handle_t *hnd = const_cast<private_handle_t *>(
        static_cast<const private_handle_t *>(tmp_buffer));

    hnd->imapper_version = HIDL_MAPPER_VERSION_SCALED;

    hnd->reserved_region_size = buffer_descriptor.reserved_size;
    hnd->attr_size = arm::mapper::common::shared_metadata_size() + hnd->reserved_region_size;

    {
        int result = mali_gralloc_ion_allocate_attr(hnd);
        if (result < 0) {
            ALOGE("mali_gralloc_ion_allocate_attr failed");
            mali_gralloc_buffer_free(tmp_buffer);
            return nullptr;
        }
    }

    {
        hnd->attr_base = mmap(nullptr, hnd->attr_size, PROT_READ | PROT_WRITE,
                MAP_SHARED, hnd->get_share_attr_fd(), 0);
        if (hnd->attr_base == MAP_FAILED) {
            ALOGE("mmap hnd->get_share_attr_fd() failed");
            mali_gralloc_buffer_free(tmp_buffer);
            return nullptr;
        }

        memset(hnd->attr_base, 0, hnd->attr_size);

        arm::mapper::common::shared_metadata_init(hnd->attr_base, buffer_descriptor.name);

        const uint32_t base_format = buffer_descriptor.alloc_format & MALI_GRALLOC_INTFMT_FMT_MASK;
        const uint64_t usage = buffer_descriptor.consumer_usage | buffer_descriptor.producer_usage;
        android_dataspace_t dataspace;
        get_format_dataspace(base_format, usage, hnd->width, hnd->height, &dataspace);

        arm::mapper::common::set_dataspace(hnd, static_cast<arm::mapper::common::Dataspace>(dataspace));

        munmap(hnd->attr_base, hnd->attr_size);
        hnd->attr_base = 0;
    }

    // TODO(modan@, handle all plane offsets)
    hnd->offset = hnd->plane_info[0].offset = descriptor.planes[0].offset;
    hnd->layer_count = 1;

    return tmp_buffer;
}

int freeImportedHandle(void *handle)
{
    using android::hardware::graphics::mapper::V4_0::IMapper;
    using android::hardware::graphics::mapper::V4_0::Error;

    const private_handle_t *hnd = static_cast<private_handle_t *>(handle);

    struct UnmapWork { void *base; size_t alloc_size; };
    std::vector<UnmapWork> work(hnd->fd_count);

    for (size_t i = 0; i < hnd->fd_count; ++i)
    {
        work[i].base = reinterpret_cast<void*>(hnd->bases[i]);
        work[i].alloc_size = hnd->alloc_sizes[i];
    }

    static android::sp<IMapper> mapper = IMapper::getService();
    if (!mapper)
    {
        ALOGE("libGralloc4Wrapper: %s failed to get a mapper", __func__);
        return -1;
    }

    if (mapper->freeBuffer(handle) != Error::NONE)
    {
        ALOGE("libGralloc4Wrapper: %s couldn't freeBuffer(%p\n", __func__, handle);
        return -1;
    }

    {
        bool unmapFailed = false;
        for (const UnmapWork &w : work)
        {
            if (!w.base) { continue; }
            if (int ret = munmap(w.base, w.alloc_size); ret)
            {
                ALOGE("libGralloc4Wrapper: %s couldn't unmap address %p (size %zu): %s", __func__, w.base, w.alloc_size, strerror(ret));
                unmapFailed = true;
            }
        }
        if (unmapFailed) { return -1; }
    }

    return 0;
}

}  // namespace android::hardware::graphics::allocator::priv

#include <sys/types.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <errno.h>

#include "rk_dmabuf.h"
#include "log.h"

#ifdef SUPPORT_DMABUF_ALLOCATOR
#include "BufferAllocator.h"

static class BufferAllocator* s_dmabuf_allocator = NULL;
#endif

static int dmabuf_ioctl(int fd, int req, void* arg) {
    int ret = ioctl(fd, req, arg);
    if (ret < 0) {
        return -errno;
    }
    return ret;
}

int dmabuf_sync(int fd, uint64_t flags)
{
    struct dma_buf_sync sync;

    sync.flags = flags;
    return dmabuf_ioctl(fd, DMA_BUF_IOCTL_SYNC, &sync);
}

int dmabuf_sync_partial(int fd, uint32_t offset, uint32_t len, uint64_t flags)
{
    struct dma_buf_sync_partial sync_p;

    sync_p.flags = flags;
    sync_p.offset = offset;
    sync_p.len = len;

    return dmabuf_ioctl(fd, DMA_BUF_IOCTL_SYNC_PARTIAL, &sync_p);
}

off_t dmabuf_get_size(int fd)
{
    off_t len = lseek(fd, 0, SEEK_END);

    lseek(fd, 0, SEEK_SET);
    return len;
}

void* dmabuf_mmap(int fd, off_t offset, size_t len)
{
    void* vaddr = mmap(NULL, len, PROT_READ|PROT_WRITE, MAP_SHARED, fd, offset);

    return (vaddr==MAP_FAILED) ? NULL : vaddr;
}

int dmabuf_alloc(uint32_t len, bool is_cma, bool is_cacheable, bool is_dma32, int *fd)
{
#ifdef SUPPORT_DMABUF_ALLOCATOR
    int ret = -1;
    if (s_dmabuf_allocator == NULL) {
        s_dmabuf_allocator = new BufferAllocator();
    }

    if (is_cma) {
        ret = s_dmabuf_allocator->Alloc(is_cacheable?kDmabufCmaHeapName:kDmabufCmaUncachedHeapName, len);
    } else {
        if (is_dma32)
            ret = s_dmabuf_allocator->Alloc(is_cacheable?kDmabufSystemDma32HeapName:kDmabufSystemUncachedDma32HeapName, len);
        else
            ret = s_dmabuf_allocator->Alloc(is_cacheable?kDmabufSystemHeapName:kDmabufSystemUncachedHeapName, len);
    }

    if (ret < 0)
        return -1;

    *fd = ret;

    return 0;
#else
    (void)len;
    (void)is_cma;
    (void)is_cacheable;
    (void)is_dma32;
    (void)fd;

    return -1;
#endif
}

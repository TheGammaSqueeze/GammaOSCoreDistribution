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

#include "mali_gralloc_reference.h"

#include <android-base/thread_annotations.h>
#include <hardware/gralloc1.h>

#include <algorithm>
#include <map>
#include <mutex>

#include "allocator/mali_gralloc_ion.h"
#include "allocator/mali_gralloc_shared_memory.h"
#include "mali_gralloc_buffer.h"
#include "mali_gralloc_bufferallocation.h"
#include "mali_gralloc_usages.h"

class BufferManager {
private:
    // This struct for now is just for validation and reference counting. When we
    // are sure that private_handle_t::bases is not being used outside gralloc, this
    // should become the only place where address mapping is maintained and can be
    // queried from.
    struct MappedData {
        void *bases[MAX_BUFFER_FDS] = {};
        size_t alloc_sizes[MAX_BUFFER_FDS] = {};
        uint64_t ref_count = 0;
    };

    BufferManager() = default;

    std::mutex lock;
    std::map<const private_handle_t *, std::unique_ptr<MappedData>> buffer_map GUARDED_BY(lock);

    static bool should_map_dmabuf(buffer_handle_t handle) {
        private_handle_t *hnd = (private_handle_t *)handle;

        // TODO(b/187145254): CPU_READ/WRITE buffer is not being properly locked from
        // MFC. This is a WA for the time being.
        constexpr auto cpu_access_usage =
                (GRALLOC_USAGE_SW_WRITE_OFTEN | GRALLOC_USAGE_SW_READ_OFTEN |
                 GRALLOC_USAGE_SW_WRITE_RARELY | GRALLOC_USAGE_SW_READ_RARELY);
        return hnd->get_usage() & cpu_access_usage;
    }

    static off_t get_buffer_size(unsigned int fd) {
        off_t current = lseek(fd, 0, SEEK_CUR);
        off_t size = lseek(fd, 0, SEEK_END);
        lseek(fd, current, SEEK_SET);
        return size;
    }

    static bool dmabuf_sanity_check(buffer_handle_t handle) {
        private_handle_t *hnd =
                static_cast<private_handle_t *>(const_cast<native_handle_t *>(handle));

        int valid_fd_count = std::find(hnd->fds, hnd->fds + MAX_FDS, -1) - hnd->fds;
        // One fd is reserved for metadata which is not accounted for in fd_count
        if (hnd->fd_count != valid_fd_count - 1) {
            MALI_GRALLOC_LOGE("%s failed: count of valid buffer fds does not match fd_count",
                              __func__);
            return false;
        }

        auto check_pid = [&](int fd, uint64_t allocated_size) -> bool {
            auto size = get_buffer_size(fd);
            auto size_padding = size - (off_t)allocated_size;
            if ((size != -1) && ((size_padding < 0) || (size_padding > PAGE_SIZE))) {
                MALI_GRALLOC_LOGE("%s failed: fd (%d) size (%jd) is not within a PAGE_SIZE of "
                                  "expected size (%" PRIx64 ")",
                                  __func__, fd, static_cast<intmax_t>(size), allocated_size);
                return false;
            }
            return true;
        };

        // Check client facing dmabufs
        for (auto i = 0; i < hnd->fd_count; i++) {
            if (!check_pid(hnd->fds[i], hnd->alloc_sizes[i])) {
                MALI_GRALLOC_LOGE("%s failed: Size check failed for alloc_sizes[%d]", __func__, i);
                return false;
            }
        }

        // Check metadata dmabuf
        if (!check_pid(hnd->get_share_attr_fd(), hnd->attr_size)) {
            MALI_GRALLOC_LOGE("%s failed: Size check failed for metadata fd", __func__);
            return false;
        }

        return true;
    }

    int map_locked(buffer_handle_t handle) REQUIRES(lock) {
        private_handle_t *hnd = (private_handle_t *)handle;
        auto it = buffer_map.find(hnd);

        if (it == buffer_map.end()) {
            MALI_GRALLOC_LOGE("BUG: Map called without importing buffer");
            return -EINVAL;
        }

        auto &data = *(it->second.get());
        if (data.ref_count == 0) {
            MALI_GRALLOC_LOGE("BUG: Found an imported buffer with ref count 0, expect errors");
        }

        // Return early if buffer is already mapped
        if (data.bases[0] != nullptr) {
            return 0;
        }

        if (!dmabuf_sanity_check(handle)) {
            return -EINVAL;
        }

        int error = mali_gralloc_ion_map(hnd);
        if (error != 0) {
            return error;
        }

        for (auto i = 0; i < MAX_BUFFER_FDS; i++) {
            data.bases[i] = reinterpret_cast<void *>(hnd->bases[i]);
            data.alloc_sizes[i] = hnd->alloc_sizes[i];
        }

        return 0;
    }

    int validate_locked(buffer_handle_t handle) REQUIRES(lock) {
        if (private_handle_t::validate(handle) < 0) {
            MALI_GRALLOC_LOGE("Reference invalid buffer %p, returning error", handle);
            return -EINVAL;
        }

        const auto *hnd = (private_handle_t *)handle;
        auto it = buffer_map.find(hnd);
        if (it == buffer_map.end()) {
            MALI_GRALLOC_LOGE("Reference unimported buffer %p, returning error", handle);
            return -EINVAL;
        }

        auto &data = *(it->second.get());
        if (data.bases[0] != nullptr) {
            for (auto i = 0; i < MAX_BUFFER_FDS; i++) {
                if (data.bases[i] != reinterpret_cast<void *>(hnd->bases[i]) ||
                    data.alloc_sizes[i] != hnd->alloc_sizes[i]) {
                    MALI_GRALLOC_LOGE(
                            "Validation failed: Buffer attributes inconsistent with mapper");
                    return -EINVAL;
                }
            }
        } else {
            for (auto i = 0; i < MAX_BUFFER_FDS; i++) {
                if (hnd->bases[i] != 0 || data.bases[i] != nullptr) {
                    MALI_GRALLOC_LOGE("Validation failed: Expected nullptr for unmapped buffer");
                    return -EINVAL;
                }
            }
        }

        return 0;
    }

public:
    static BufferManager &getInstance() {
        static BufferManager instance;
        return instance;
    }

    int retain(buffer_handle_t handle) EXCLUDES(lock) {
        if (private_handle_t::validate(handle) < 0) {
            MALI_GRALLOC_LOGE("Registering/Retaining invalid buffer %p, returning error", handle);
            return -EINVAL;
        }
        std::lock_guard<std::mutex> _l(lock);

        private_handle_t *hnd = (private_handle_t *)handle;

        auto it = buffer_map.find(hnd);
        if (it == buffer_map.end()) {
            bool success = false;
            auto _data = std::make_unique<MappedData>();

            std::tie(it, success) = buffer_map.insert({hnd, std::move(_data)});
            if (!success) {
                MALI_GRALLOC_LOGE("Failed to create buffer data mapping");
                return -EINVAL;
            }

            for (int i = 0; i < MAX_BUFFER_FDS; i++) {
                hnd->bases[i] = 0;
            }
        } else if (it->second->ref_count == 0) {
            MALI_GRALLOC_LOGE("BUG: Import counter of an imported buffer is 0, expect errors");
        }
        auto &data = *(it->second.get());

        data.ref_count++;
        if (!should_map_dmabuf(handle)) return 0;
        return map_locked(handle);
    }

    int map(buffer_handle_t handle) EXCLUDES(lock) {
        std::lock_guard<std::mutex> _l(lock);
        auto error = validate_locked(handle);
        if (error != 0) {
            return error;
        }

        return map_locked(handle);
    }

    int release(buffer_handle_t handle) EXCLUDES(lock) {
        std::lock_guard<std::mutex> _l(lock);

        // Always call locked variant of validate from this function. On calling
        // the other validate variant, an attacker might launch a timing attack
        // where they would try to time their attack between the return of
        // validate and before taking the lock in this function again.
        auto error = validate_locked(handle);
        if (error != 0) {
            return error;
        }

        private_handle_t *hnd = (private_handle_t *)handle;
        auto it = buffer_map.find(hnd);
        if (it == buffer_map.end()) {
            MALI_GRALLOC_LOGE("Trying to release a non-imported buffer");
            return -EINVAL;
        }

        auto &data = *(it->second.get());
        if (data.ref_count == 0) {
            MALI_GRALLOC_LOGE("BUG: Reference held for buffer whose counter is 0");
            return -EINVAL;
        }

        data.ref_count--;
        if (data.ref_count == 0) {
            if (data.bases[0] != nullptr) {
                mali_gralloc_ion_unmap(hnd);
            }

            /* TODO: Make this unmapping of shared meta fd into a function? */
            if (hnd->attr_base) {
                munmap(hnd->attr_base, hnd->attr_size);
                hnd->attr_base = nullptr;
            }
            buffer_map.erase(it);
        }

        return 0;
    }

    int validate(buffer_handle_t handle) EXCLUDES(lock) {
        std::lock_guard<std::mutex> _l(lock);
        return validate_locked(handle);
    }
};

int mali_gralloc_reference_retain(buffer_handle_t handle) {
    return BufferManager::getInstance().retain(handle);
}

int mali_gralloc_reference_map(buffer_handle_t handle) {
    return BufferManager::getInstance().map(handle);
}

int mali_gralloc_reference_release(buffer_handle_t handle) {
    return BufferManager::getInstance().release(handle);
}

int mali_gralloc_reference_validate(buffer_handle_t handle) {
    return BufferManager::getInstance().validate(handle);
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <linux/bpf.h>

#include <android-base/result.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>
#include <utils/Log.h>
#include "bpf/BpfUtils.h"

namespace android {
namespace bpf {

// This is a class wrapper for eBPF maps. The eBPF map is a special in-kernel
// data structure that stores data in <Key, Value> pairs. It can be read/write
// from userspace by passing syscalls with the map file descriptor. This class
// is used to generalize the procedure of interacting with eBPF maps and hide
// the implementation detail from other process. Besides the basic syscalls
// wrapper, it also provides some useful helper functions as well as an iterator
// nested class to iterate the map more easily.
//
// NOTE: A kernel eBPF map may be accessed by both kernel and userspace
// processes at the same time. Or if the map is pinned as a virtual file, it can
// be obtained by multiple eBPF map class object and accessed concurrently.
// Though the map class object and the underlying kernel map are thread safe, it
// is not safe to iterate over a map while another thread or process is deleting
// from it. In this case the iteration can return duplicate entries.
template <class Key, class Value>
class BpfMap {
  public:
    BpfMap<Key, Value>() {};

  protected:
    // flag must be within BPF_OBJ_FLAG_MASK, ie. 0, BPF_F_RDONLY, BPF_F_WRONLY
    BpfMap<Key, Value>(const char* pathname, uint32_t flags) {
        mMapFd.reset(mapRetrieve(pathname, flags));
        if (mMapFd < 0) abort();
        if (isAtLeastKernelVersion(4, 14, 0)) {
            if (bpfGetFdKeySize(mMapFd) != sizeof(Key)) abort();
            if (bpfGetFdValueSize(mMapFd) != sizeof(Value)) abort();
        }
    }

  public:
    explicit BpfMap<Key, Value>(const char* pathname) : BpfMap<Key, Value>(pathname, 0) {}

    BpfMap<Key, Value>(bpf_map_type map_type, uint32_t max_entries, uint32_t map_flags = 0) {
        mMapFd.reset(createMap(map_type, sizeof(Key), sizeof(Value), max_entries, map_flags));
        if (mMapFd < 0) abort();
    }

    base::Result<Key> getFirstKey() const {
        Key firstKey;
        if (getFirstMapKey(mMapFd, &firstKey)) {
            return ErrnoErrorf("Get firstKey map {} failed", mMapFd.get());
        }
        return firstKey;
    }

    base::Result<Key> getNextKey(const Key& key) const {
        Key nextKey;
        if (getNextMapKey(mMapFd, &key, &nextKey)) {
            return ErrnoErrorf("Get next key of map {} failed", mMapFd.get());
        }
        return nextKey;
    }

    base::Result<void> writeValue(const Key& key, const Value& value, uint64_t flags) {
        if (writeToMapEntry(mMapFd, &key, &value, flags)) {
            return ErrnoErrorf("Write to map {} failed", mMapFd.get());
        }
        return {};
    }

    base::Result<Value> readValue(const Key key) const {
        Value value;
        if (findMapEntry(mMapFd, &key, &value)) {
            return ErrnoErrorf("Read value of map {} failed", mMapFd.get());
        }
        return value;
    }

    base::Result<void> deleteValue(const Key& key) {
        if (deleteMapEntry(mMapFd, &key)) {
            return ErrnoErrorf("Delete entry from map {} failed", mMapFd.get());
        }
        return {};
    }

  protected:
    [[clang::reinitializes]] base::Result<void> init(const char* path, int fd) {
        mMapFd.reset(fd);
        if (mMapFd == -1) {
            return ErrnoErrorf("Pinned map not accessible or does not exist: ({})", path);
        }
        if (isAtLeastKernelVersion(4, 14, 0)) {
            // Normally we should return an error here instead of calling abort,
            // but this cannot happen at runtime without a massive code bug (K/V type mismatch)
            // and as such it's better to just blow the system up and let the developer fix it.
            // Crashes are much more likely to be noticed than logs and missing functionality.
            if (bpfGetFdKeySize(mMapFd) != sizeof(Key)) abort();
            if (bpfGetFdValueSize(mMapFd) != sizeof(Value)) abort();
        }
        return {};
    }

  public:
    // Function that tries to get map from a pinned path.
    [[clang::reinitializes]] base::Result<void> init(const char* path) {
        return init(path, mapRetrieveRW(path));
    }


#ifdef TEST_BPF_MAP
    // due to Android SELinux limitations which prevent map creation by anyone besides the bpfloader
    // this should only ever be used by test code, it is equivalent to:
    //   .reset(createMap(type, keysize, valuesize, max_entries, map_flags)
    // TODO: derive map_flags from BpfMap vs BpfMapRO
    [[clang::reinitializes]] base::Result<void> resetMap(bpf_map_type map_type,
                                                         uint32_t max_entries,
                                                         uint32_t map_flags = 0) {
        int map_fd = createMap(map_type, sizeof(Key), sizeof(Value), max_entries, map_flags);
        if (map_fd < 0) {
             auto err = ErrnoErrorf("Unable to create map.");
             mMapFd.reset();
             return err;
        };
        mMapFd.reset(map_fd);
        return {};
    }
#endif

    // Iterate through the map and handle each key retrieved based on the filter
    // without modification of map content.
    base::Result<void> iterate(
            const std::function<base::Result<void>(const Key& key, const BpfMap<Key, Value>& map)>&
                    filter) const;

    // Iterate through the map and get each <key, value> pair, handle each <key,
    // value> pair based on the filter without modification of map content.
    base::Result<void> iterateWithValue(
            const std::function<base::Result<void>(const Key& key, const Value& value,
                                                   const BpfMap<Key, Value>& map)>& filter) const;

    // Iterate through the map and handle each key retrieved based on the filter
    base::Result<void> iterate(
            const std::function<base::Result<void>(const Key& key, BpfMap<Key, Value>& map)>&
                    filter);

    // Iterate through the map and get each <key, value> pair, handle each <key,
    // value> pair based on the filter.
    base::Result<void> iterateWithValue(
            const std::function<base::Result<void>(const Key& key, const Value& value,
                                                   BpfMap<Key, Value>& map)>& filter);

    const base::unique_fd& getMap() const { return mMapFd; };

    // Copy assignment operator
    BpfMap<Key, Value>& operator=(const BpfMap<Key, Value>& other) {
        if (this != &other) mMapFd.reset(fcntl(other.mMapFd.get(), F_DUPFD_CLOEXEC, 0));
        return *this;
    }

    // Move assignment operator
    BpfMap<Key, Value>& operator=(BpfMap<Key, Value>&& other) noexcept {
        if (this != &other) {
            mMapFd = std::move(other.mMapFd);
            other.reset();
        }
        return *this;
    }

    void reset(base::unique_fd fd) = delete;

    [[clang::reinitializes]] void reset(int fd = -1) {
        mMapFd.reset(fd);
        if ((fd >= 0) && isAtLeastKernelVersion(4, 14, 0)) {
            if (bpfGetFdKeySize(mMapFd) != sizeof(Key)) abort();
            if (bpfGetFdValueSize(mMapFd) != sizeof(Value)) abort();
            if (bpfGetFdMapFlags(mMapFd) != 0) abort(); // TODO: fix for BpfMapRO
        }
    }

    bool isValid() const { return mMapFd != -1; }

    base::Result<void> clear() {
        while (true) {
            auto key = getFirstKey();
            if (!key.ok()) {
                if (key.error().code() == ENOENT) return {};  // empty: success
                return key.error();                           // Anything else is an error
            }
            auto res = deleteValue(key.value());
            if (!res.ok()) {
                // Someone else could have deleted the key, so ignore ENOENT
                if (res.error().code() == ENOENT) continue;
                ALOGE("Failed to delete data %s", strerror(res.error().code()));
                return res.error();
            }
        }
    }

    base::Result<bool> isEmpty() const {
        auto key = getFirstKey();
        if (!key.ok()) {
            // Return error code ENOENT means the map is empty
            if (key.error().code() == ENOENT) return true;
            return key.error();
        }
        return false;
    }

  private:
    base::unique_fd mMapFd;
};

template <class Key, class Value>
base::Result<void> BpfMap<Key, Value>::iterate(
        const std::function<base::Result<void>(const Key& key, const BpfMap<Key, Value>& map)>&
                filter) const {
    base::Result<Key> curKey = getFirstKey();
    while (curKey.ok()) {
        const base::Result<Key>& nextKey = getNextKey(curKey.value());
        base::Result<void> status = filter(curKey.value(), *this);
        if (!status.ok()) return status;
        curKey = nextKey;
    }
    if (curKey.error().code() == ENOENT) return {};
    return curKey.error();
}

template <class Key, class Value>
base::Result<void> BpfMap<Key, Value>::iterateWithValue(
        const std::function<base::Result<void>(const Key& key, const Value& value,
                                               const BpfMap<Key, Value>& map)>& filter) const {
    base::Result<Key> curKey = getFirstKey();
    while (curKey.ok()) {
        const base::Result<Key>& nextKey = getNextKey(curKey.value());
        base::Result<Value> curValue = readValue(curKey.value());
        if (!curValue.ok()) return curValue.error();
        base::Result<void> status = filter(curKey.value(), curValue.value(), *this);
        if (!status.ok()) return status;
        curKey = nextKey;
    }
    if (curKey.error().code() == ENOENT) return {};
    return curKey.error();
}

template <class Key, class Value>
base::Result<void> BpfMap<Key, Value>::iterate(
        const std::function<base::Result<void>(const Key& key, BpfMap<Key, Value>& map)>& filter) {
    base::Result<Key> curKey = getFirstKey();
    while (curKey.ok()) {
        const base::Result<Key>& nextKey = getNextKey(curKey.value());
        base::Result<void> status = filter(curKey.value(), *this);
        if (!status.ok()) return status;
        curKey = nextKey;
    }
    if (curKey.error().code() == ENOENT) return {};
    return curKey.error();
}

template <class Key, class Value>
base::Result<void> BpfMap<Key, Value>::iterateWithValue(
        const std::function<base::Result<void>(const Key& key, const Value& value,
                                               BpfMap<Key, Value>& map)>& filter) {
    base::Result<Key> curKey = getFirstKey();
    while (curKey.ok()) {
        const base::Result<Key>& nextKey = getNextKey(curKey.value());
        base::Result<Value> curValue = readValue(curKey.value());
        if (!curValue.ok()) return curValue.error();
        base::Result<void> status = filter(curKey.value(), curValue.value(), *this);
        if (!status.ok()) return status;
        curKey = nextKey;
    }
    if (curKey.error().code() == ENOENT) return {};
    return curKey.error();
}

template <class Key, class Value>
class BpfMapRO : public BpfMap<Key, Value> {
  public:
    BpfMapRO<Key, Value>() {};

    explicit BpfMapRO<Key, Value>(const char* pathname)
        : BpfMap<Key, Value>(pathname, BPF_F_RDONLY) {}

    // Function that tries to get map from a pinned path.
    [[clang::reinitializes]] base::Result<void> init(const char* path) {
        return BpfMap<Key,Value>::init(path, mapRetrieveRO(path));
    }
};

}  // namespace bpf
}  // namespace android

/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ANDROID_SENSORS_AIDL_TEST_SHARED_MEMORY_H
#define ANDROID_SENSORS_AIDL_TEST_SHARED_MEMORY_H

#include "sensors-vts-utils/GrallocWrapper.h"

#include <aidlcommonsupport/NativeHandle.h>
#include <android-base/macros.h>
#include <log/log.h>

#include <sys/mman.h>
#include <cinttypes>

#include <cutils/ashmem.h>

using ::aidl::android::hardware::sensors::BnSensors;
using ::aidl::android::hardware::sensors::Event;
using ::aidl::android::hardware::sensors::ISensors;
using ::aidl::android::hardware::sensors::SensorType;

template <class SensorType, class Event>
class SensorsAidlTestSharedMemory {
  public:
    static SensorsAidlTestSharedMemory* create(ISensors::SharedMemInfo::SharedMemType type,
                                               size_t size) {
        constexpr size_t kMaxSize =
                128 * 1024 * 1024;  // sensor test should not need more than 128M
        if (size == 0 || size >= kMaxSize) {
            return nullptr;
        }

        auto m = new SensorsAidlTestSharedMemory<SensorType, Event>(type, size);
        if (m->mSize != size || m->mBuffer == nullptr) {
            delete m;
            m = nullptr;
        }
        return m;
    }

    ISensors::SharedMemInfo getSharedMemInfo() const {
        ISensors::SharedMemInfo mem = {
                .type = mType,
                .format = ISensors::SharedMemInfo::SharedMemFormat::SENSORS_EVENT,
                .size = static_cast<int32_t>(mSize),
                .memoryHandle = android::dupToAidl(mNativeHandle)};
        return mem;
    }
    char* getBuffer() const { return mBuffer; }
    size_t getSize() const { return mSize; }
    std::vector<Event> parseEvents(int64_t lastCounter = -1, size_t offset = 0) const {
        constexpr size_t kEventSize =
                static_cast<size_t>(BnSensors::DIRECT_REPORT_SENSOR_EVENT_TOTAL_LENGTH);
        constexpr size_t kOffsetSize =
                static_cast<size_t>(BnSensors::DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_FIELD);
        constexpr size_t kOffsetToken =
                static_cast<size_t>(BnSensors::DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_REPORT_TOKEN);
        constexpr size_t kOffsetType =
                static_cast<size_t>(BnSensors::DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_SENSOR_TYPE);
        constexpr size_t kOffsetAtomicCounter = static_cast<size_t>(
                BnSensors::DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_ATOMIC_COUNTER);
        constexpr size_t kOffsetTimestamp =
                static_cast<size_t>(BnSensors::DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_TIMESTAMP);
        constexpr size_t kOffsetData =
                static_cast<size_t>(BnSensors::DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_DATA);

        std::vector<Event> events;
        std::vector<float> data(16);

        while (offset + kEventSize <= mSize) {
            int64_t atomicCounter =
                    *reinterpret_cast<uint32_t*>(mBuffer + offset + kOffsetAtomicCounter);
            if (atomicCounter <= lastCounter) {
                ALOGV("atomicCounter = %" PRId64 ", lastCounter = %" PRId64, atomicCounter,
                      lastCounter);
                break;
            }

            int32_t size = *reinterpret_cast<int32_t*>(mBuffer + offset + kOffsetSize);
            if (size != kEventSize) {
                // unknown error, events parsed may be wrong, remove all
                events.clear();
                break;
            }

            int32_t token = *reinterpret_cast<int32_t*>(mBuffer + offset + kOffsetToken);
            int32_t type = *reinterpret_cast<int32_t*>(mBuffer + offset + kOffsetType);
            int64_t timestamp = *reinterpret_cast<int64_t*>(mBuffer + offset + kOffsetTimestamp);

            ALOGV("offset = %zu, cnt %" PRId64 ", token %" PRId32 ", type %" PRId32
                  ", timestamp %" PRId64,
                  offset, atomicCounter, token, type, timestamp);

            Event event = {
                    .timestamp = timestamp,
                    .sensorHandle = token,
                    .sensorType = type,
            };

            event.set<Event::Data>(reinterpret_cast<float*>(mBuffer + offset + kOffsetData));
            // event.u.data = android::hardware::hidl_array<float,
            // 16>(reinterpret_cast<float*>(mBuffer + offset + kOffsetData));

            events.push_back(event);

            lastCounter = atomicCounter;
            offset += kEventSize;
        }

        return events;
    }

    virtual ~SensorsAidlTestSharedMemory() {
        switch (mType) {
            case ISensors::SharedMemInfo::SharedMemType::ASHMEM: {
                if (mSize != 0) {
                    ::munmap(mBuffer, mSize);
                    mBuffer = nullptr;

                    ::native_handle_close(mNativeHandle);
                    ::native_handle_delete(mNativeHandle);

                    mNativeHandle = nullptr;
                    mSize = 0;
                }
                break;
            }
            case ISensors::SharedMemInfo::SharedMemType::GRALLOC: {
                if (mSize != 0) {
                    mGrallocWrapper->freeBuffer(mNativeHandle);
                    mNativeHandle = nullptr;
                    mSize = 0;
                }
                break;
            }
            default: {
                if (mNativeHandle != nullptr || mSize != 0 || mBuffer != nullptr) {
                    ALOGE("SensorsAidlTestSharedMemory %p not properly destructed: "
                          "type %d, native handle %p, size %zu, buffer %p",
                          this, static_cast<int>(mType), mNativeHandle, mSize, mBuffer);
                }
                break;
            }
        }
    }

  private:
    SensorsAidlTestSharedMemory(ISensors::SharedMemInfo::SharedMemType type, size_t size)
        : mType(type), mSize(0), mBuffer(nullptr) {
        native_handle_t* handle = nullptr;
        char* buffer = nullptr;
        switch (type) {
            case ISensors::SharedMemInfo::SharedMemType::ASHMEM: {
                int fd;
                handle = ::native_handle_create(1 /*nFds*/, 0 /*nInts*/);
                if (handle != nullptr) {
                    handle->data[0] = fd =
                            ::ashmem_create_region("SensorsAidlTestSharedMemory", size);
                    if (handle->data[0] > 0) {
                        // memory is pinned by default
                        buffer = static_cast<char*>(
                                ::mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0));
                        if (buffer != reinterpret_cast<char*>(MAP_FAILED)) {
                            break;
                        }
                        ::native_handle_close(handle);
                    }
                    ::native_handle_delete(handle);
                    handle = nullptr;
                }
                break;
            }
            case ISensors::SharedMemInfo::SharedMemType::GRALLOC: {
                mGrallocWrapper = std::make_unique<::android::GrallocWrapper>();
                if (!mGrallocWrapper->isInitialized()) {
                    break;
                }

                std::pair<native_handle_t*, void*> buf = mGrallocWrapper->allocate(size);
                handle = buf.first;
                buffer = static_cast<char*>(buf.second);
                break;
            }
            default:
                break;
        }

        if (buffer != nullptr) {
            mNativeHandle = handle;
            mSize = size;
            mBuffer = buffer;
        }
    }

    ISensors::SharedMemInfo::SharedMemType mType;
    native_handle_t* mNativeHandle;
    size_t mSize;
    char* mBuffer;
    std::unique_ptr<::android::GrallocWrapper> mGrallocWrapper;

    DISALLOW_COPY_AND_ASSIGN(SensorsAidlTestSharedMemory);
};

#endif  // ANDROID_SENSORS_TEST_SHARED_MEMORY_H

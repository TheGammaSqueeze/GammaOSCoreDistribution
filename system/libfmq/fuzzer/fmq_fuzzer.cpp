/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <stddef.h>
#include <stdint.h>
#include <iostream>
#include <limits>
#include <thread>

#include <android-base/logging.h>
#include <android-base/scopeguard.h>
#include <fmq/AidlMessageQueue.h>
#include <fmq/ConvertMQDescriptors.h>
#include <fmq/EventFlag.h>
#include <fmq/MessageQueue.h>

#include "fuzzer/FuzzedDataProvider.h"

using aidl::android::hardware::common::fmq::SynchronizedReadWrite;
using aidl::android::hardware::common::fmq::UnsynchronizedWrite;
using android::hardware::kSynchronizedReadWrite;
using android::hardware::kUnsynchronizedWrite;

typedef int32_t payload_t;

// The reader will wait for 10 ms
static constexpr int kBlockingTimeoutNs = 10000000;

/*
 * MessageQueueBase.h contains asserts when memory allocation fails. So we need
 * to set a reasonable limit if we want to avoid those asserts.
 */
static constexpr size_t kAlignment = 8;
static constexpr size_t kMaxNumElements = PAGE_SIZE * 10 / sizeof(payload_t) - kAlignment + 1;

/*
 * The read counter can be found in the shared memory 16 bytes before the start
 * of the ring buffer.
 */
static constexpr int kReadCounterOffsetBytes = 16;
/*
 * The write counter can be found in the shared memory 8 bytes before the start
 * of the ring buffer.
 */
static constexpr int kWriteCounterOffsetBytes = 8;

static constexpr int kMaxNumSyncReaders = 1;
static constexpr int kMaxNumUnsyncReaders = 5;
static constexpr int kMaxDataPerReader = 1000;

typedef android::AidlMessageQueue<payload_t, SynchronizedReadWrite> AidlMessageQueueSync;
typedef android::AidlMessageQueue<payload_t, UnsynchronizedWrite> AidlMessageQueueUnsync;
typedef android::hardware::MessageQueue<payload_t, kSynchronizedReadWrite> MessageQueueSync;
typedef android::hardware::MessageQueue<payload_t, kUnsynchronizedWrite> MessageQueueUnsync;
typedef aidl::android::hardware::common::fmq::MQDescriptor<payload_t, SynchronizedReadWrite>
        AidlMQDescSync;
typedef aidl::android::hardware::common::fmq::MQDescriptor<payload_t, UnsynchronizedWrite>
        AidlMQDescUnsync;
typedef android::hardware::MQDescriptorSync<payload_t> MQDescSync;
typedef android::hardware::MQDescriptorUnsync<payload_t> MQDescUnsync;

static inline uint64_t* getCounterPtr(payload_t* start, int byteOffset) {
    return reinterpret_cast<uint64_t*>(reinterpret_cast<uint8_t*>(start) - byteOffset);
}

template <typename Queue, typename Desc>
void reader(const Desc& desc, std::vector<uint8_t> readerData, bool userFd) {
    Queue readMq(desc);
    if (!readMq.isValid()) {
        LOG(ERROR) << "read mq invalid";
        return;
    }
    FuzzedDataProvider fdp(&readerData[0], readerData.size());
    payload_t* ring = nullptr;
    while (fdp.remaining_bytes()) {
        typename Queue::MemTransaction tx;
        size_t numElements = fdp.ConsumeIntegralInRange<size_t>(0, kMaxNumElements);
        if (!readMq.beginRead(numElements, &tx)) {
            continue;
        }
        const auto& region = tx.getFirstRegion();
        payload_t* firstStart = region.getAddress();

        // the ring buffer is only next to the read/write counters when there is
        // no user supplied fd
        if (!userFd) {
            if (ring == nullptr) {
                ring = firstStart;
            }
            if (fdp.ConsumeIntegral<uint8_t>() == 1) {
                uint64_t* writeCounter = getCounterPtr(ring, kWriteCounterOffsetBytes);
                *writeCounter = fdp.ConsumeIntegral<uint64_t>();
            }
        }
        (void)std::to_string(*firstStart);

        readMq.commitRead(numElements);
    }
}

template <typename Queue, typename Desc>
void readerBlocking(const Desc& desc, std::vector<uint8_t>& readerData,
                    std::atomic<size_t>& readersNotFinished,
                    std::atomic<size_t>& writersNotFinished) {
    android::base::ScopeGuard guard([&readersNotFinished]() { readersNotFinished--; });
    Queue readMq(desc);
    if (!readMq.isValid()) {
        LOG(ERROR) << "read mq invalid";
        return;
    }
    FuzzedDataProvider fdp(&readerData[0], readerData.size());
    do {
        size_t count = fdp.remaining_bytes()
                               ? fdp.ConsumeIntegralInRange<size_t>(1, readMq.getQuantumCount())
                               : 1;
        std::vector<payload_t> data;
        data.resize(count);
        readMq.readBlocking(data.data(), count, kBlockingTimeoutNs);
    } while (fdp.remaining_bytes() > sizeof(size_t) && writersNotFinished > 0);
}

// Can't use blocking calls with Unsync queues(there is a static_assert)
template <>
void readerBlocking<AidlMessageQueueUnsync, AidlMQDescUnsync>(const AidlMQDescUnsync&,
                                                              std::vector<uint8_t>&,
                                                              std::atomic<size_t>&,
                                                              std::atomic<size_t>&) {}
template <>
void readerBlocking<MessageQueueUnsync, MQDescUnsync>(const MQDescUnsync&, std::vector<uint8_t>&,
                                                      std::atomic<size_t>&, std::atomic<size_t>&) {}

template <typename Queue>
void writer(Queue& writeMq, FuzzedDataProvider& fdp, bool userFd) {
    payload_t* ring = nullptr;
    while (fdp.remaining_bytes()) {
        typename Queue::MemTransaction tx;
        size_t numElements = 1;
        if (!writeMq.beginWrite(numElements, &tx)) {
            // need to consume something so we don't end up looping forever
            fdp.ConsumeIntegral<uint8_t>();
            continue;
        }

        const auto& region = tx.getFirstRegion();
        payload_t* firstStart = region.getAddress();
        // the ring buffer is only next to the read/write counters when there is
        // no user supplied fd
        if (!userFd) {
            if (ring == nullptr) {
                ring = firstStart;
            }
            if (fdp.ConsumeIntegral<uint8_t>() == 1) {
                uint64_t* readCounter = getCounterPtr(ring, kReadCounterOffsetBytes);
                *readCounter = fdp.ConsumeIntegral<uint64_t>();
            }
        }
        *firstStart = fdp.ConsumeIntegral<payload_t>();

        writeMq.commitWrite(numElements);
    }
}

template <typename Queue>
void writerBlocking(Queue& writeMq, FuzzedDataProvider& fdp,
                    std::atomic<size_t>& writersNotFinished,
                    std::atomic<size_t>& readersNotFinished) {
    android::base::ScopeGuard guard([&writersNotFinished]() { writersNotFinished--; });
    while (fdp.remaining_bytes() > sizeof(size_t) && readersNotFinished > 0) {
        size_t count = fdp.ConsumeIntegralInRange<size_t>(1, writeMq.getQuantumCount());
        std::vector<payload_t> data;
        for (int i = 0; i < count; i++) {
            data.push_back(fdp.ConsumeIntegral<payload_t>());
        }
        writeMq.writeBlocking(data.data(), count, kBlockingTimeoutNs);
    }
}

// Can't use blocking calls with Unsync queues(there is a static_assert)
template <>
void writerBlocking<AidlMessageQueueUnsync>(AidlMessageQueueUnsync&, FuzzedDataProvider&,
                                            std::atomic<size_t>&, std::atomic<size_t>&) {}
template <>
void writerBlocking<MessageQueueUnsync>(MessageQueueUnsync&, FuzzedDataProvider&,
                                        std::atomic<size_t>&, std::atomic<size_t>&) {}

template <typename Queue, typename Desc>
void fuzzAidlWithReaders(std::vector<uint8_t>& writerData,
                         std::vector<std::vector<uint8_t>>& readerData, bool blocking) {
    FuzzedDataProvider fdp(&writerData[0], writerData.size());
    bool evFlag = blocking || fdp.ConsumeBool();
    android::base::unique_fd dataFd;
    size_t bufferSize = 0;
    size_t numElements = fdp.ConsumeIntegralInRange<size_t>(1, kMaxNumElements);
    bool userFd = fdp.ConsumeBool();
    if (userFd) {
        // run test with our own data region
        bufferSize = numElements * sizeof(payload_t);
        dataFd.reset(::ashmem_create_region("SyncReadWrite", bufferSize));
    }
    Queue writeMq(numElements, evFlag, std::move(dataFd), bufferSize);
    if (!writeMq.isValid()) {
        LOG(ERROR) << "AIDL write mq invalid";
        return;
    }
    const auto desc = writeMq.dupeDesc();
    CHECK(desc.handle.fds[0].get() != -1);

    std::atomic<size_t> readersNotFinished = readerData.size();
    std::atomic<size_t> writersNotFinished = 1;
    std::vector<std::thread> readers;
    for (int i = 0; i < readerData.size(); i++) {
        if (blocking) {
            readers.emplace_back(readerBlocking<Queue, Desc>, std::ref(desc),
                                 std::ref(readerData[i]), std::ref(readersNotFinished),
                                 std::ref(writersNotFinished));

        } else {
            readers.emplace_back(reader<Queue, Desc>, std::ref(desc), std::ref(readerData[i]),
                                 userFd);
        }
    }

    if (blocking) {
        writerBlocking<Queue>(writeMq, fdp, writersNotFinished, readersNotFinished);
    } else {
        writer<Queue>(writeMq, fdp, userFd);
    }

    for (auto& reader : readers) {
        reader.join();
    }
}

template <typename Queue, typename Desc>
void fuzzHidlWithReaders(std::vector<uint8_t>& writerData,
                         std::vector<std::vector<uint8_t>>& readerData, bool blocking) {
    FuzzedDataProvider fdp(&writerData[0], writerData.size());
    bool evFlag = blocking || fdp.ConsumeBool();
    android::base::unique_fd dataFd;
    size_t bufferSize = 0;
    size_t numElements = fdp.ConsumeIntegralInRange<size_t>(1, kMaxNumElements);
    bool userFd = fdp.ConsumeBool();
    if (userFd) {
        // run test with our own data region
        bufferSize = numElements * sizeof(payload_t);
        dataFd.reset(::ashmem_create_region("SyncReadWrite", bufferSize));
    }
    Queue writeMq(numElements, evFlag, std::move(dataFd), bufferSize);
    if (!writeMq.isValid()) {
        LOG(ERROR) << "HIDL write mq invalid";
        return;
    }
    const auto desc = writeMq.getDesc();
    CHECK(desc->isHandleValid());

    std::atomic<size_t> readersNotFinished = readerData.size();
    std::atomic<size_t> writersNotFinished = 1;
    std::vector<std::thread> readers;
    for (int i = 0; i < readerData.size(); i++) {
        if (blocking) {
            readers.emplace_back(readerBlocking<Queue, Desc>, std::ref(*desc),
                                 std::ref(readerData[i]), std::ref(readersNotFinished),
                                 std::ref(writersNotFinished));
        } else {
            readers.emplace_back(reader<Queue, Desc>, std::ref(*desc), std::ref(readerData[i]),
                                 userFd);
        }
    }

    if (blocking) {
        writerBlocking<Queue>(writeMq, fdp, writersNotFinished, readersNotFinished);
    } else {
        writer<Queue>(writeMq, fdp, userFd);
    }

    for (auto& reader : readers) {
        reader.join();
    }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    if (size < 1 || size > 50000) {
        return 0;
    }
    FuzzedDataProvider fdp(data, size);

    bool fuzzSync = fdp.ConsumeBool();
    std::vector<std::vector<uint8_t>> readerData;
    uint8_t numReaders = fuzzSync ? fdp.ConsumeIntegralInRange<uint8_t>(0, kMaxNumSyncReaders)
                                  : fdp.ConsumeIntegralInRange<uint8_t>(0, kMaxNumUnsyncReaders);
    for (int i = 0; i < numReaders; i++) {
        readerData.emplace_back(fdp.ConsumeBytes<uint8_t>(kMaxDataPerReader));
    }
    bool fuzzBlocking = fdp.ConsumeBool();
    std::vector<uint8_t> writerData = fdp.ConsumeRemainingBytes<uint8_t>();
    if (fuzzSync) {
        fuzzHidlWithReaders<MessageQueueSync, MQDescSync>(writerData, readerData, fuzzBlocking);
        fuzzAidlWithReaders<AidlMessageQueueSync, AidlMQDescSync>(writerData, readerData,
                                                                  fuzzBlocking);
    } else {
        fuzzHidlWithReaders<MessageQueueUnsync, MQDescUnsync>(writerData, readerData, false);
        fuzzAidlWithReaders<AidlMessageQueueUnsync, AidlMQDescUnsync>(writerData, readerData,
                                                                      false);
    }

    return 0;
}

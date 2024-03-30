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

#include <android-base/result.h>
#include <android-base/unique_fd.h>
#include <linux/vm_sockets.h>
#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cinttypes>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <random>
#include <string>
#include <vector>

using android::base::ErrnoError;
using android::base::Error;
using android::base::Result;
using android::base::unique_fd;

namespace {

constexpr int kBlockSize = 4096;

[[noreturn]] void PrintUsage(const char* exe_name) {
    std::printf("Usage: %s path size (read|write|both) [rounds]\n", exe_name);
    std::exit(EXIT_FAILURE);
}

void DropCache() {
    system("echo 1 > /proc/sys/vm/drop_caches");
}

struct BenchmarkResult {
    struct timespec elapsed;
    std::uint64_t size;
};

enum class BenchmarkOption {
    READ = 0,
    WRITE = 1,
    RANDREAD = 2,
    RANDWRITE = 3,
};

Result<BenchmarkResult> runTest(const char* path, BenchmarkOption option, std::uint64_t size) {
    bool is_read = (option == BenchmarkOption::READ || option == BenchmarkOption::RANDREAD);
    bool is_rand = (option == BenchmarkOption::RANDREAD || option == BenchmarkOption::RANDWRITE);

    unique_fd fd(open(path, is_read ? O_RDONLY : O_WRONLY | O_CREAT, 0644));
    if (fd.get() == -1) {
        return ErrnoError() << "opening " << path << " failed";
    }

    uint64_t block_count = (size + kBlockSize - 1) / kBlockSize;
    std::vector<uint64_t> offsets;
    if (is_rand) {
        std::mt19937 rd{std::random_device{}()};
        offsets.reserve(block_count);
        for (uint64_t i = 0; i < block_count; i++) offsets.push_back(i * kBlockSize);
        std::shuffle(offsets.begin(), offsets.end(), rd);
    }

    uint64_t total_processed = 0;
    char buf[kBlockSize] = {};

    struct timespec start;
    if (clock_gettime(CLOCK_REALTIME, &start) < 0) {
        return ErrnoError() << "failed to get start time";
    }

    for (uint64_t i = 0; i < block_count; i++) {
        if (!offsets.empty()) {
            if (lseek(fd.get(), offsets[i], SEEK_SET) == -1) {
                return ErrnoError() << "failed to lseek";
            }
        }

        auto ret = is_read ? read(fd.get(), buf, kBlockSize) : write(fd.get(), buf, kBlockSize);
        if (ret == 0) {
            return Error() << "unexpected end of file";
        } else if (ret == -1) {
            return ErrnoError() << "file io failed";
        }
        total_processed += ret;
    }

    struct timespec stop;
    if (clock_gettime(CLOCK_REALTIME, &stop) < 0) {
        return ErrnoError() << "failed to get finish time";
    }

    struct timespec elapsed;
    if ((stop.tv_nsec - start.tv_nsec) < 0) {
        elapsed.tv_sec = stop.tv_sec - start.tv_sec - 1;
        elapsed.tv_nsec = stop.tv_nsec - start.tv_nsec + 1000000000;
    } else {
        elapsed.tv_sec = stop.tv_sec - start.tv_sec;
        elapsed.tv_nsec = stop.tv_nsec - start.tv_nsec;
    }

    return BenchmarkResult{elapsed, total_processed};
}

} // namespace

int main(int argc, char* argv[]) {
    // without this, stdout isn't immediately flushed when running via "adb shell"
    std::setvbuf(stdout, nullptr, _IONBF, 0);
    std::setvbuf(stderr, nullptr, _IONBF, 0);

    if (argc < 4 || argc > 5) {
        PrintUsage(argv[0]);
    }

    const char* path = argv[1];

    std::uint64_t size = std::strtoull(argv[2], nullptr, 0);
    if (size == 0 || size == UINT64_MAX) {
        std::fprintf(stderr, "invalid size %s\n", argv[1]);
        PrintUsage(argv[0]);
    }

    std::vector<std::pair<BenchmarkOption, std::string>> benchmarkList;
    if (strcmp(argv[3], "read") != 0) {
        benchmarkList.emplace_back(BenchmarkOption::WRITE, "write");
        benchmarkList.emplace_back(BenchmarkOption::RANDWRITE, "randwrite");
    }
    if (strcmp(argv[3], "write") != 0) {
        benchmarkList.emplace_back(BenchmarkOption::READ, "read");
        benchmarkList.emplace_back(BenchmarkOption::RANDREAD, "randread");
    }

    std::shuffle(benchmarkList.begin(), benchmarkList.end(), std::mt19937{std::random_device{}()});

    int rounds = 1;
    if (argc == 5) {
        rounds = std::atoi(argv[4]);
        if (rounds <= 0) {
            std::fprintf(stderr, "invalid round %s\n", argv[4]);
            PrintUsage(argv[0]);
        }
    }

    for (auto [option, name] : benchmarkList) {
        std::printf("%s test:\n", name.c_str());

        for (int i = 0; i < rounds; i++) {
            DropCache();
            auto res = runTest(path, option, size);
            if (!res.ok()) {
                std::fprintf(stderr, "Error while benchmarking: %s\n",
                             res.error().message().c_str());
                return EXIT_FAILURE;
            }

            double elapsed_time = res->elapsed.tv_sec + res->elapsed.tv_nsec / 1e9;
            std::printf("total %" PRIu64 " bytes, took %.3g seconds ", res->size, elapsed_time);

            double speed = res->size / elapsed_time;
            const char* unit = "bytes";
            if (speed >= 1000) {
                speed /= 1024;
                unit = "KB";
            }
            if (speed >= 1000) {
                speed /= 1024;
                unit = "MB";
            }
            if (speed >= 1000) {
                speed /= 1024;
                unit = "GB";
            }
            std::printf("(%.3g %s/s)\n", speed, unit);
        }
        std::printf("\n");
    }
}

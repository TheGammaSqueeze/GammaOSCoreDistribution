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

#pragma once

#include <android-base/logging.h>
#include <string_view>
#include <utils/Trace.h>

// #define LOG_FUNC
#define TRACE_FUNC

#ifdef TRACE_FUNC
#define DEBUG_FUNC() constexpr static FullMethodName __kFullNameObj__ =                     \
                                           FullMethodName{ __PRETTY_FUNCTION__ };           \
                     constexpr static const char *__kFullName__ =  __kFullNameObj__.get();  \
                     ATRACE_NAME(__kFullName__)
#else

#ifdef LOG_FUNC
#define DEBUG_FUNC() DebugFunction _dbgFnObj_(__func__)
#else
#define DEBUG_FUNC()
#endif

#endif

#define RET_IF_ERR(expr)                  \
    do {                                  \
        auto err = (expr);                \
        if (err) [[unlikely]] return err; \
    } while (0)


#define TO_BINDER_STATUS(x) x == 0                                                \
                            ? ndk::ScopedAStatus::ok()                            \
                            : ndk::ScopedAStatus::fromServiceSpecificError(x)

namespace aidl::android::hardware::graphics::composer3::impl {

class DebugFunction {
public:
    DebugFunction(const char* name) : mName(name) { LOG(INFO) << mName << " Enter"; }

    ~DebugFunction() { LOG(INFO) << mName << " Exit"; }

private:
    const char* mName;
};

class FullMethodName {
public:
    constexpr FullMethodName(const std::string_view prettyName) : mBuf() {
        // remove every thing before 'impl::'
        auto start = prettyName.find("impl::");
        if (start == prettyName.npos) {
            start = 0;
        }
        // remove everything after '('
        auto end = prettyName.rfind('(');
        if (end == prettyName.npos) {
            end = prettyName.length();
        }

        auto len = std::min(end - start, mBuf.size());
        // to a null-terminated string
        // prettyName.copy(mBuf.data(), len, start) is available in c++20
        for (int i = 0; i < len; ++i) {
            mBuf[i] = prettyName[start + i];
        }
    }

    constexpr const char *get() const {
        return mBuf.data();
    }

private:
    std::array<char, 256> mBuf;
};

} // namespace aidl::android::hardware::graphics::composer3::impl

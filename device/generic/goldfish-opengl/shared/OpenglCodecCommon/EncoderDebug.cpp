/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "EncoderDebug.h"

#include <string>
#include <fstream>
#include <streambuf>

#include <android/log.h>

namespace {

bool encoderShouldLog() {
#if defined(ENABLE_ENCODER_DEBUG_LOGGING_FOR_ALL_APPS)
    return true;
#elif defined(ENABLE_ENCODER_DEBUG_LOGGING_FOR_APP)
    static const bool sEnabled = []() {
        std::ifstream cmdlineStream("/proc/self/cmdline");
        const std::string cmdline((std::istreambuf_iterator<char>(cmdlineStream)),
                                   std::istreambuf_iterator<char>());

        if (cmdline.find(ENABLE_ENCODER_DEBUG_LOGGING_FOR_APP) != std::string::npos) {
            const std::string message = "Enabling gfxstream encoder logging for " + cmdline;
            __android_log_write(ANDROID_LOG_DEBUG, "gfxstream", message.c_str());
            return true;
        } else {
            const std::string message = "Not enabling gfxstream encoder logging for " + cmdline;
            __android_log_write(ANDROID_LOG_DEBUG, "gfxstream", message.c_str());
            return false;
        }
    }();
    return sEnabled;
#else
    return false;
#endif
}

}  // namespace

void encoderLog(const char* format, ...) {
#if defined(ENABLE_ENCODER_DEBUG_LOGGING_FOR_ALL_APPS) || \
    defined(ENABLE_ENCODER_DEBUG_LOGGING_FOR_APP)
    if (!encoderShouldLog()) {
        return;
    }

    char buffer[2048];

    va_list args;
    va_start(args, format);
    vsnprintf(buffer, 2048, format, args);
    va_end(args);

    __android_log_write(ANDROID_LOG_DEBUG, "gfxstream", buffer);
#else
    (void)format;
#endif
}
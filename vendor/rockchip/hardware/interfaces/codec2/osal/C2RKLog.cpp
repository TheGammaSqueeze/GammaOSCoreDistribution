/*
 * Copyright (C) 2021 Rockchip Electronics Co. LTD
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

#include <stdio.h>
#include <string.h>
#include <android/log.h>

#include "C2RKLog.h"
#include "C2RKDump.h"

#define MAX_LINE_LEN  256

uint32_t getALogLevel(uint32_t level) {
    switch (level) {
    case C2_LOG_TRACE: {
        if (C2RKDump::getDumpFlag() & C2_DUMP_LOG_TRACE) {
            return ANDROID_LOG_DEBUG;
        }
    } break;
    case C2_LOG_DEBUG:      return ANDROID_LOG_DEBUG;
    case C2_LOG_INFO:       return ANDROID_LOG_INFO;
    case C2_LOG_WARNING:    return ANDROID_LOG_WARN;
    case C2_LOG_ERROR:      return ANDROID_LOG_ERROR;
    }

    return ANDROID_LOG_UNKNOWN;
}

void _c2_log(uint32_t level, const char *tag, const char *fmt,
             const char *fname, const uint32_t row, ...) {
    uint32_t ALevel = getALogLevel(level);

    if (ALevel == ANDROID_LOG_UNKNOWN) {
        return;
    }

    va_list args;
    va_start(args, row);

    if (C2RKDump::getDumpFlag() & C2_DUMP_LOG_DETAIL) {
        char line[MAX_LINE_LEN];
        snprintf(line, sizeof(line), "{%-16.16s:%04u} %s\r\n", fname, row, fmt);

        __android_log_vprint(ALevel, tag, line, args);
    } else {
        __android_log_vprint(ALevel, tag, fmt, args);
    }

    va_end(args);
}

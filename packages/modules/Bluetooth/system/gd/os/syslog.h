/*
 * Copyright 2021 The Android Open Source Project
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

#include <cstdarg>
#include <cstdint>

/**
 * This header is used for systems targeting syslog as their log target (i.e.
 * Floss builds).
 */

/**
 * We separately define these tags and map them to syslog levels because the
 * log headers re-define LOG_DEBUG and LOG_INFO which are already existing in
 * the syslog header. Also, LOG_TAG_VERBOSE doesn't actually exist in syslog
 * definitions and needs to be mapped to another log level.
 */
constexpr uint32_t LOG_TAG_VERBOSE = 0x0;
constexpr uint32_t LOG_TAG_DEBUG = 0x1;
constexpr uint32_t LOG_TAG_INFO = 0x2;
constexpr uint32_t LOG_TAG_WARN = 0x3;
constexpr uint32_t LOG_TAG_ERROR = 0x4;
constexpr uint32_t LOG_TAG_FATAL = 0x5;

/**
 * Write log to syslog.
 */
void write_syslog(int tag, const char* format, ...);

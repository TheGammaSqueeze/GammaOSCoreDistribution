/******************************************************************************
 *
 *  Copyright 2019 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#pragma once

#include <cstdlib>

#ifndef LOG_TAG
#define LOG_TAG "bluetooth"
#endif

static_assert(LOG_TAG != nullptr, "LOG_TAG should never be NULL");

#if defined(OS_ANDROID)

#include <log/log.h>
#include <log/log_event_list.h>

#include "common/init_flags.h"

#ifdef FUZZ_TARGET
#define LOG_VERBOSE(...)
#define LOG_DEBUG(...)
#define LOG_INFO(...)
#define LOG_WARN(...)
#else

static_assert(LOG_TAG != nullptr, "LOG_TAG is null after header inclusion");

#define LOG_VERBOSE(fmt, args...)                                             \
  do {                                                                        \
    if (bluetooth::common::InitFlags::IsDebugLoggingEnabledForTag(LOG_TAG)) { \
      ALOGV("%s:%d %s: " fmt, __FILE__, __LINE__, __func__, ##args);          \
    }                                                                         \
  } while (false)

#define LOG_DEBUG(fmt, args...)                                               \
  do {                                                                        \
    if (bluetooth::common::InitFlags::IsDebugLoggingEnabledForTag(LOG_TAG)) { \
      ALOGD("%s:%d %s: " fmt, __FILE__, __LINE__, __func__, ##args);          \
    }                                                                         \
  } while (false)

#define LOG_INFO(fmt, args...) ALOGI("%s:%d %s: " fmt, __FILE__, __LINE__, __func__, ##args)
#define LOG_WARN(fmt, args...) ALOGW("%s:%d %s: " fmt, __FILE__, __LINE__, __func__, ##args)
#endif /* FUZZ_TARGET */
#define LOG_ERROR(fmt, args...) ALOGE("%s:%d %s: " fmt, __FILE__, __LINE__, __func__, ##args)

#elif defined (ANDROID_EMULATOR)
// Log using android emulator logging mechanism
#include "android/utils/debug.h"

#define LOGWRAPPER(fmt, args...) VERBOSE_INFO(bluetooth, "bluetooth: %s:%d - %s: " fmt, \
                                              __FILE__, __LINE__, __func__, ##args)

#define LOG_VEBOSE(...) LOGWRAPPER(__VA_ARGS__)
#define LOG_DEBUG(...)  LOGWRAPPER(__VA_ARGS__)
#define LOG_INFO(...)   LOGWRAPPER(__VA_ARGS__)
#define LOG_WARN(...)   LOGWRAPPER(__VA_ARGS__)
#define LOG_ERROR(...)  LOGWRAPPER(__VA_ARGS__)
#define LOG_ALWAYS_FATAL(fmt, args...)                                              \
  do {                                                                              \
    fprintf(stderr, "%s:%d - %s: " fmt "\n", __FILE__, __LINE__, __func__, ##args); \
    abort();                                                                        \
  } while (false)
#elif defined(TARGET_FLOSS)
#include "gd/os/syslog.h"

// Prefix the log with tag, file, line and function
#define LOGWRAPPER(tag, fmt, args...) \
  write_syslog(tag, "%s:%s:%d - %s: " fmt, LOG_TAG, __FILE__, __LINE__, __func__, ##args)

#ifdef FUZZ_TARGET
#define LOG_VERBOSE(...)
#define LOG_DEBUG(...)
#define LOG_INFO(...)
#define LOG_WARN(...)
#else
#define LOG_VERBOSE(...)                                                      \
  do {                                                                        \
    if (bluetooth::common::InitFlags::IsDebugLoggingEnabledForTag(LOG_TAG)) { \
      LOGWRAPPER(LOG_TAG_VERBOSE, __VA_ARGS__);                               \
    }                                                                         \
  } while (false)
#define LOG_DEBUG(...)                                                        \
  do {                                                                        \
    if (bluetooth::common::InitFlags::IsDebugLoggingEnabledForTag(LOG_TAG)) { \
      LOGWRAPPER(LOG_TAG_DEBUG, __VA_ARGS__);                                 \
    }                                                                         \
  } while (false)
#define LOG_INFO(...) LOGWRAPPER(LOG_TAG_INFO, __VA_ARGS__)
#define LOG_WARN(...) LOGWRAPPER(LOG_TAG_WARN, __VA_ARGS__)
#endif /*FUZZ_TARGET*/
#define LOG_ERROR(...) LOGWRAPPER(LOG_TAG_ERROR, __VA_ARGS__)

#define LOG_ALWAYS_FATAL(...)               \
  do {                                      \
    LOGWRAPPER(LOG_TAG_FATAL, __VA_ARGS__); \
    abort();                                \
  } while (false)

#ifndef LOG_EVENT_INT
#define LOG_EVENT_INT(...)
#endif

#else
/* syslog didn't work well here since we would be redefining LOG_DEBUG. */
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

#include <chrono>
#include <cstdio>
#include <ctime>

#define LOGWRAPPER(fmt, args...)                                                                                    \
  do {                                                                                                              \
    auto _now = std::chrono::system_clock::now();                                                                   \
    auto _now_ms = std::chrono::time_point_cast<std::chrono::milliseconds>(_now);                                   \
    auto _now_t = std::chrono::system_clock::to_time_t(_now);                                                       \
    /* YYYY-MM-DD_HH:MM:SS.sss is 23 byte long, plus 1 for null terminator */                                       \
    char _buf[24];                                                                                                  \
    auto l = std::strftime(_buf, sizeof(_buf), "%Y-%m-%d %H:%M:%S", std::localtime(&_now_t));                       \
    snprintf(                                                                                                       \
        _buf + l, sizeof(_buf) - l, ".%03u", static_cast<unsigned int>(_now_ms.time_since_epoch().count() % 1000)); \
    /* pid max is 2^22 = 4194304 in 64-bit system, and 32768 by default, hence 7 digits are needed most */          \
    fprintf(                                                                                                        \
        stderr,                                                                                                     \
        "%s %7d %7ld %s - %s:%d - %s: " fmt "\n",                                                                   \
        _buf,                                                                                                       \
        static_cast<int>(getpid()),                                                                                 \
        syscall(SYS_gettid),                                                                                        \
        LOG_TAG,                                                                                                    \
        __FILE__,                                                                                                   \
        __LINE__,                                                                                                   \
        __func__,                                                                                                   \
        ##args);                                                                                                    \
  } while (false)

#ifdef FUZZ_TARGET
#define LOG_VERBOSE(...)
#define LOG_DEBUG(...)
#define LOG_INFO(...)
#define LOG_WARN(...)
#else
#define LOG_VERBOSE(fmt, args...)                                             \
  do {                                                                        \
    if (bluetooth::common::InitFlags::IsDebugLoggingEnabledForTag(LOG_TAG)) { \
      LOGWRAPPER(fmt, ##args);                                                \
    }                                                                         \
  } while (false)
#define LOG_DEBUG(fmt, args...)                                               \
  do {                                                                        \
    if (bluetooth::common::InitFlags::IsDebugLoggingEnabledForTag(LOG_TAG)) { \
      LOGWRAPPER(fmt, ##args);                                                \
    }                                                                         \
  } while (false)
#define LOG_INFO(...) LOGWRAPPER(__VA_ARGS__)
#define LOG_WARN(...) LOGWRAPPER(__VA_ARGS__)
#endif /* FUZZ_TARGET */
#define LOG_ERROR(...) LOGWRAPPER(__VA_ARGS__)

#ifndef LOG_ALWAYS_FATAL
#define LOG_ALWAYS_FATAL(...) \
  do {                        \
    LOGWRAPPER(__VA_ARGS__);  \
    abort();                  \
  } while (false)
#endif

#ifndef LOG_EVENT_INT
#define LOG_EVENT_INT(...)
#endif

#endif /* defined(OS_ANDROID) */

#define ASSERT(condition)                                    \
  do {                                                       \
    if (!(condition)) {                                      \
      LOG_ALWAYS_FATAL("assertion '" #condition "' failed"); \
    }                                                        \
  } while (false)

#define ASSERT_LOG(condition, fmt, args...)                                 \
  do {                                                                      \
    if (!(condition)) {                                                     \
      LOG_ALWAYS_FATAL("assertion '" #condition "' failed - " fmt, ##args); \
    }                                                                       \
  } while (false)

#ifndef CASE_RETURN_TEXT
#define CASE_RETURN_TEXT(code) \
  case code:                   \
    return #code
#endif

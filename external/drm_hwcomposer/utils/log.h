#ifndef UTILS_LOG_H_
#define UTILS_LOG_H_

#ifdef ANDROID

#include <log/log.h>

#else

#include <cinttypes>
#include <cstdio>

// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define ALOGE(args...) printf("ERR: " args)
// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define ALOGW(args...) printf("WARN: " args)
// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define ALOGI(args...) printf("INFO: " args)
// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define ALOGD(args...) printf("DBG:" args)
// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define ALOGV(args...) printf("VERBOSE: " args)

#endif

#endif
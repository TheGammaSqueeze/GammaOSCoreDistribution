#pragma once

#include <cstdint>
#include <cstdio>

// Outputs a log line using Google's standard prefix. (http://go/logging#prefix)
//
// Do not use this function directly. Instead, use one of the logging macros below.
//
// stream: file handle to output to.
// severity: single character to indicate severity: 'V', 'D', 'I', 'W', 'E', or 'F'.
// file: name of the file where the message comes from (typically __FILE__)
// line: line number where the message comes from (typically __LINE__)
// timestamp_us: for testing only - timestamp of the log in microseconds since the Unix epoch.
//               Pass 0 to use the current time.
// format: printf-style format specifier
void OutputLog(FILE* stream, char severity, const char* file, unsigned int line,
               int64_t timestamp_us, const char* format, ...);

#define GFXSTREAM_LOG(file, severity, fmt, ...) \
    OutputLog(file, severity, __FILE__, __LINE__, 0, fmt, ##__VA_ARGS__)

//#define ENABLE_GL_LOG 1
#if defined(ENABLE_GL_LOG)
#define GL_LOG(fmt, ...) GFXSTREAM_LOG(stderr, 'I', fmt, ##__VA_ARGS__)
#else
#define GL_LOG(...) ((void)0)
#endif

//#define ENABLE_DECODER_LOG 1
#if defined(ENABLE_DECODER_LOG)
#define DECODER_DEBUG_LOG(fmt, ...) GFXSTREAM_LOG(stderr, 'I', fmt, ##__VA_ARGS__)
#else
#define DECODER_DEBUG_LOG(...) ((void)0)
#endif

//#define ENABLE_DISPATCH_LOG 1
#if defined(ENABLE_DISPATCH_LOG)
#define DISPATCH_DEBUG_LOG(fmt, ...) GFXSTREAM_LOG(stderr, 'I', fmt, ##__VA_ARGS__)
#else
#define DISPATCH_DEBUG_LOG(...) ((void)0)
#endif

#define ERR(fmt, ...)                                   \
    do {                                                \
        GFXSTREAM_LOG(stderr, 'E', fmt, ##__VA_ARGS__); \
    } while (0)

#define INFO(fmt, ...)                                  \
    do {                                                \
        GFXSTREAM_LOG(stdout, 'I', fmt, ##__VA_ARGS__); \
    } while (0)

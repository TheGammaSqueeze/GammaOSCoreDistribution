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

#ifndef ANDROID_C2_RK_LOG_H__
#define ANDROID_C2_RK_LOG_H__

#ifndef ROCKCHIP_LOG_TAG
#define ROCKCHIP_LOG_TAG    "rk_c2_log"
#endif

#define C2_LOG_ERROR        0   /* error conditions */
#define C2_LOG_WARNING      1   /* warning conditions */
#define C2_LOG_INFO         2   /* informational */
#define C2_LOG_DEBUG        3   /* debug-level messages */
#define C2_LOG_TRACE        4   /* trace messages */

#define c2_info(format, ...)    c2_log(C2_LOG_INFO,     format, ##__VA_ARGS__)
#define c2_warn(format, ...)    c2_log(C2_LOG_WARNING,  format, ##__VA_ARGS__)
#define c2_err(format, ...)     c2_log(C2_LOG_ERROR,    format, ##__VA_ARGS__)
#define c2_debug(format, ...)   c2_log(C2_LOG_DEBUG,    format, ##__VA_ARGS__)
#define c2_trace(format, ...)   c2_log(C2_LOG_TRACE,    format, ##__VA_ARGS__)

// function call log
#define c2_log_func_enter()     c2_info("%s enter", __FUNCTION__)
#define c2_log_func_leave()     c2_info("%s leave", __FUNCTION__)
#define c2_log_func_called()    c2_info("%s called", __FUNCTION__)
#define c2_trace_func_enter()   c2_trace("%s enter", __FUNCTION__)
#define c2_trace_func_leave()   c2_trace("%s leave", __FUNCTION__)
#define c2_trace_func_called()  c2_trace("%s called", __FUNCTION__)

#define c2_log(level, fmt, ...) \
        _c2_log(level, ROCKCHIP_LOG_TAG, fmt, __FUNCTION__, __LINE__, ##__VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

void _c2_log(uint32_t level, const char *tag, const char *fmt,
             const char *fname, const uint32_t row, ...);

#ifdef __cplusplus
}
#endif

#endif  // ANDROID_C2_RK_LOG_H__

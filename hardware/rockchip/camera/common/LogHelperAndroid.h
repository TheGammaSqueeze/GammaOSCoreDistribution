/*
 * Copyright (C) 2012-2017 Intel Corporation
 * Copyright (c) 2017, Fuzhou Rockchip Electronics Co., Ltd
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

#ifndef __LOGHELPERANDROID__
#define __LOGHELPERANDROID__

// System dependencies
#include <utils/Log.h>
#include "EnumPrinthelper.h"
#include <cutils/properties.h>
#include <sys/prctl.h>

#define ENV_CAMERA_HAL_DEBUG  "persist.vendor.camera.debug"
#define ENV_CAMERA_HAL_PERF   "persist.vendor.camera.perf"
#define ENV_CAMERA_HAL_DUMP   "persist.vendor.camera.dump"
// Properties for debugging.
#define ENV_CAMERA_HAL_DUMP_SKIP_NUM  "persist.vendor.camera.dump.skip"
#define ENV_CAMERA_HAL_DUMP_INTERVAL  "persist.vendor.camera.dump.gap"
#define ENV_CAMERA_HAL_DUMP_COUNT     "persist.vendor.camera.dump.cnt"
#define ENV_CAMERA_HAL_DUMP_PATH      "persist.vendor.camera.dump.path"

#ifdef RKCAMERA_REDEFINE_LOG

typedef enum {
    CAM_NO_MODULE,
    CAM_HAL_MODULE,
    CAM_JPEG_MODULE,
    CAM_LAST_MODULE
} cam_modules_t;

/* values that persist.vendor.camera.global.debug can be set to */
/* all camera modules need to map their internal debug levels to this range */
typedef enum {
    CAM_GLBL_DBG_NONE  = 0,
    CAM_GLBL_DBG_ERR   = 1,
    CAM_GLBL_DBG_WARN  = 2,
    CAM_GLBL_DBG_INFO  = 3,
    CAM_GLBL_DBG_DEBUG = 4,
    CAM_GLBL_DBG_HIGH  = 5,
    CAM_GLBL_DBG_LOW   = 6
} cam_global_debug_level_t;

extern int g_cam_log[CAM_LAST_MODULE][CAM_GLBL_DBG_LOW + 1];

/* #define FATAL_IF(cond, ...) LOG_ALWAYS_FATAL_IF(cond, ## __VA_ARGS__) */
#ifndef CAM_MODULE
#define CAM_MODULE CAM_HAL_MODULE
#endif

#undef CLOGx
#define CLOGx(module, level, fmt, args...)                         \
{\
if (g_cam_log[module][level]) {                                  \
  rk_camera_debug_log(module, level, LOG_TAG, fmt, ##args); \
}\
}

#undef CLOGI
#define CLOGI(module, fmt, args...)                \
    CLOGx(module, CAM_GLBL_DBG_INFO, fmt, ##args)
#undef CLOGD
#define CLOGD(module, fmt, args...)                \
    CLOGx(module, CAM_GLBL_DBG_DEBUG, fmt, ##args)
#undef CLOGL
#define CLOGL(module, fmt, args...)                \
    CLOGx(module, CAM_GLBL_DBG_LOW, fmt, ##args)
#undef CLOGW
#define CLOGW(module, fmt, args...)                \
    CLOGx(module, CAM_GLBL_DBG_WARN, fmt, ##args)
#undef CLOGH
#define CLOGH(module, fmt, args...)                \
    CLOGx(module, CAM_GLBL_DBG_HIGH, fmt, ##args)
#undef CLOGE
#define CLOGE(module, fmt, args...)                \
    CLOGx(module, CAM_GLBL_DBG_ERR, fmt, ##args)

#undef LOGD
#define LOGD(fmt, args...) CLOGD(CAM_MODULE, fmt, ##args)
#undef LOGL
#define LOGL(fmt, args...) CLOGL(CAM_MODULE, fmt, ##args)
#undef LOGW
#define LOGW(fmt, args...) CLOGW(CAM_MODULE, fmt, ##args)
#undef LOGH
#define LOGH(fmt, args...) CLOGH(CAM_MODULE, fmt, ##args)
#undef LOGE
#define LOGE(fmt, args...) CLOGE(CAM_MODULE, fmt, ##args)
#undef LOGI
#define LOGI(fmt, args...) CLOGI(CAM_MODULE, fmt, ##args)

#define NS_PER_MS (NS_PER_SEC /MS_PER_SEC)

static inline long get_time_diff_ms(struct timespec *from,
                                    struct timespec *to) {
    return (to->tv_sec - from->tv_sec) * (long)MS_PER_SEC +
           (to->tv_nsec - from->tv_nsec) / (long)NS_PER_MS;
}

class ScopedLog {
public:
inline ScopedLog(int level, const char* name) :
        mLevel(level),
        mName(name) {
            prctl(PR_GET_NAME, threadName);
            clock_gettime(CLOCK_MONOTONIC_COARSE, &last_tm);
            if (g_cam_log[CAM_MODULE][mLevel] == 1) {
                ALOGD("ENTER-%s, Thread[%s]", mName, threadName);
            }
}

inline ~ScopedLog() {
    clock_gettime(CLOCK_MONOTONIC_COARSE, &curr_tm);
    diff_time = get_time_diff_ms(&last_tm,&curr_tm);

    if (g_cam_log[CAM_MODULE][mLevel] == 1) {
        ALOGD("EXIT-%s use %ldms, Thread[%s]",mName, diff_time, threadName);
    } else if (diff_time > 100) {
        if (diff_time > 1000)
            ALOGE("EXIT-%s over 1s, use %ldms, Thread[%s]",mName, diff_time, threadName);
        else
            ALOGW("EXIT-%s use %ldms, Thread[%s]",mName, diff_time, threadName);
    }
}

private:
    int mLevel;
    const char* mName;
    char threadName[20];
    struct timespec last_tm;
    struct timespec curr_tm;
    long diff_time;
};

/* reads and updates camera logging properties */
void rk_camera_set_dbg_log_properties(void);

/* generic logger function */
void rk_camera_debug_log(const cam_modules_t module,
                   const cam_global_debug_level_t level,
                   const char *tag, const char *fmt, ...);

void rk_camera_debug_open(void);
void rk_camera_debug_close(void);

#define LOG1(fmt, args...) LOGI(fmt, ##args)
#define LOG2(fmt, args...) LOGI(fmt, ##args)
#define LOGR(fmt, args...) LOGI(fmt, ##args)
#define LOGAIQ(fmt, args...) LOGI(fmt, ##args)
#define LOGV(fmt, args...) LOGI(fmt, ##args)

/* #undef LOGE */
/* #define LOGE(fmt, args...) LOGI(fmt, ##args) */
/* #undef LOGW */
/* #define LOGW(fmt, args...) LOGE(fmt, ##args) */
// CAMTRACE_NAME traces the beginning and end of the current scope.  To trace
// the correct start and end times this macro should be declared first in the
// scope body.
#define HAL_TRACE_NAME(level, name) ScopedLog __tracer(level, name )
#define HAL_TRACE_CALL(level) HAL_TRACE_NAME(level, __FUNCTION__)
#define HAL_TRACE_CALL_PRETTY(level) HAL_TRACE_NAME(level, __PRETTY_FUNCTION__)



/* use a 64 bits value to represent all modules bug level, and the
 * module bit maps is as follow:
 *
 * bit:      7-4                                       3-0
 * meaning:  [sub modules]                             [level]
 *
 * bit:      15          14       13          12       11-8
 * meaning:  [MSG]      [POOL]     [CAPTURE]       [FLASH]    [sub modules]
 *
 * bit:      23          22       21          20       19       18        17          16
 * meaning:  [U]    [U]   [U]  [U]   [U]   [U]     [U]      [U]
 *
 * bit:      31          30       29          28       27       26        25          24
 * meaning:  [U]    [U]    [U]       [U]   [U]  [U]  [U]   [U]
 *
 * bit:      39          38       37          36       35       34        33          32
 * meaning:  [U]  [U]  [U]  [U]  [U]    [U]    [U]      [U]
 *
 * bit:      47          46       45          44       43       42        41          40
 * meaning:  [U]         [U]      [U] [U]  [U]   [U]    [U]       [U]
 *
 * bit:     [63-48]
 * meaning:  [U]
 *
 * [U] means unused now.
 * [level]: use 4 bits to define log levels.
 *     each module log has following ascending levels:
 *          0: error
 *          1: warning
 *          2: info
 *          3: debug
 *          4: verbose
 *          5: low1
 *          6-7: unused, now the same as debug
 * [sub modules]: use bits 4-11 to define the sub modules of each module, the
 *     specific meaning of each bit is decided by the module itself. These bits
 *     is designed to implement the sub module's log switch.
 * [modules]: FLASH ...
 *
 * set debug level example:
 * eg. set module flash log level to debug, and enable all sub modules of flash:
 *    Android:
 *      setprop persist.vendor.rkisp.log 0x1ff5
 */

int hal_get_log_level();
void hal_print_log (int module, int sub_modules, const char *tag, int level, const char* format, ...);

typedef struct hal_cam_log_module_info_s {
    const char* module_name;
    int log_level;
    int sub_modules;
} hal_cam_log_module_info_t;


typedef enum {
    HAL_LOG_LEVEL_NONE,
    HAL_LOG_LEVEL_ERR,
    HAL_LOG_LEVEL_WARNING,
    HAL_LOG_LEVEL_INFO,
    HAL_LOG_LEVEL_DEBUG,
    HAL_LOG_LEVEL_VERBOSE,
    HAL_LOG_LEVEL_LOW1,
} hal_log_level_t;

typedef enum {
    HAL_LOG_MODULE_FLASH,
    HAL_LOG_MODULE_CAP,
    HAL_LOG_MODULE_POOL,
    HAL_LOG_MODULE_MSG,
    HAL_LOG_MODULE_MAX,
} hal_log_modules_t;

extern hal_cam_log_module_info_t g_hal_log_infos[HAL_LOG_MODULE_MAX];
static unsigned long long g_cam_hal3_log_level = 0xff0;
#define HAL_PROPERTY_VALUE_MAX 128
#define HAL_MAX_STR_SIZE 4096

// module debug
#define HAL_MODULE_LOG_ERROR(module, submodules, format, ...)    \
    do { \
        hal_print_log(module, submodules, LOG_TAG, HAL_LOG_LEVEL_ERR, "E:" format "\n", ##__VA_ARGS__); \
    } while (0)

#define HAL_MODULE_LOG_WARNING(module, submodules, format, ...)   \
    do { \
        if (HAL_LOG_LEVEL_WARNING <= g_hal_log_infos[module].log_level && \
                (submodules & g_hal_log_infos[module].sub_modules)) \
            hal_print_log(module, submodules, LOG_TAG, HAL_LOG_LEVEL_WARNING, "W:" format "\n", \
                           ##__VA_ARGS__);                                                \
    } while (0)

#define HAL_MODULE_LOG_INFO(module, submodules, format, ...)   \
    do { \
        if (HAL_LOG_LEVEL_INFO <= g_hal_log_infos[module].log_level && \
                (submodules & g_hal_log_infos[module].sub_modules)) \
            hal_print_log (module, submodules, LOG_TAG, HAL_LOG_LEVEL_INFO, "I:" format "\n",  ## __VA_ARGS__); \
    } while(0)

#define HAL_MODULE_LOG_DEBUG(module, submodules, format, ...)   \
    do { \
        if (HAL_LOG_LEVEL_DEBUG <= g_hal_log_infos[module].log_level && \
            (submodules & g_hal_log_infos[module].sub_modules))                       \
            hal_print_log(module, submodules, LOG_TAG, HAL_LOG_LEVEL_DEBUG, "D:" format "\n", \
                           ##__VA_ARGS__);                                              \
    } while(0)

#define HAL_MODULE_LOG_VERBOSE(module, submodules, format, ...)   \
    do { \
        if (HAL_LOG_LEVEL_VERBOSE <= g_hal_log_infos[module].log_level && \
                (submodules & g_hal_log_infos[module].sub_modules)) \
            hal_print_log (module, submodules, LOG_TAG, HAL_LOG_LEVEL_VERBOSE, "XCAM VERBOSE %s:%d: " format "\n", __BI_FILENAME__ , __LINE__, ## __VA_ARGS__); \
    } while(0) \

#define HAL_MODULE_LOG_LOW1(module, submodules, format, ...)   \
    do { \
        if (HAL_LOG_LEVEL_LOW1 <= g_hal_log_infos[module].log_level && \
                (submodules & g_hal_log_infos[module].sub_modules)) \
          hal_print_log (module, submodules, LOG_TAG, HAL_LOG_LEVEL_LOW1, "XCAM LOW1 %s:%d: " format "\n", __BI_FILENAME__, __LINE__, ## __VA_ARGS__); \
    } while(0)

// define flash module logs
#define LOGE_FLASH_SUBM(sub_modules, ...) HAL_MODULE_LOG_ERROR(HAL_LOG_MODULE_FLASH, sub_modules, ##__VA_ARGS__)
#define LOGW_FLASH_SUBM(sub_modules, ...) HAL_MODULE_LOG_WARNING(HAL_LOG_MODULE_FLASH, sub_modules, ##__VA_ARGS__)
#define LOGI_FLASH_SUBM(sub_modules, ...) HAL_MODULE_LOG_INFO(HAL_LOG_MODULE_FLASH, sub_modules, ##__VA_ARGS__)
#define LOGD_FLASH_SUBM(sub_modules, ...) HAL_MODULE_LOG_DEBUG(HAL_LOG_MODULE_FLASH, sub_modules, ##__VA_ARGS__)
#define LOGV_FLASH_SUBM(sub_modules, ...) HAL_MODULE_LOG_VERBOSE(HAL_LOG_MODULE_FLASH, sub_modules, ##__VA_ARGS__)
#define LOG1_FLASH_SUBM(sub_modules, ...) HAL_MODULE_LOG_LOW1(HAL_LOG_MODULE_FLASH, sub_modules, ##__VA_ARGS__)

#define LOGE_FLASH(...) LOGE_FLASH_SUBM(0xff, ##__VA_ARGS__)
#define LOGW_FLASH(...) LOGW_FLASH_SUBM(0xff, ##__VA_ARGS__)
#define LOGI_FLASH(...) LOGI_FLASH_SUBM(0xff, ##__VA_ARGS__)
#define LOGD_FLASH(...) LOGD_FLASH_SUBM(0xff, ##__VA_ARGS__)
#define LOGV_FLASH(...) LOGV_FLASH_SUBM(0xff, ##__VA_ARGS__)
#define LOG1_FLASH(...) LOG1_FLASH_SUBM(0xff, ##__VA_ARGS__)

// define capture module logs
#define LOGE_CAP_SUBM(sub_modules, ...) HAL_MODULE_LOG_ERROR(HAL_LOG_MODULE_CAP, sub_modules, ##__VA_ARGS__)
#define LOGW_CAP_SUBM(sub_modules, ...) HAL_MODULE_LOG_WARNING(HAL_LOG_MODULE_CAP, sub_modules, ##__VA_ARGS__)
#define LOGI_CAP_SUBM(sub_modules, ...) HAL_MODULE_LOG_INFO(HAL_LOG_MODULE_CAP, sub_modules, ##__VA_ARGS__)
#define LOGD_CAP_SUBM(sub_modules, ...) HAL_MODULE_LOG_DEBUG(HAL_LOG_MODULE_CAP, sub_modules, ##__VA_ARGS__)
#define LOGV_CAP_SUBM(sub_modules, ...) HAL_MODULE_LOG_VERBOSE(HAL_LOG_MODULE_CAP, sub_modules, ##__VA_ARGS__)
#define LOG1_CAP_SUBM(sub_modules, ...) HAL_MODULE_LOG_LOW1(HAL_LOG_MODULE_CAP, sub_modules, ##__VA_ARGS__)

#define LOGE_CAP(...) LOGE_CAP_SUBM(0xff, ##__VA_ARGS__)
#define LOGW_CAP(...) LOGW_CAP_SUBM(0xff, ##__VA_ARGS__)
#define LOGI_CAP(...) LOGI_CAP_SUBM(0xff, ##__VA_ARGS__)
#define LOGD_CAP(...) LOGD_CAP_SUBM(0xff, ##__VA_ARGS__)
#define LOGV_CAP(...) LOGV_CAP_SUBM(0xff, ##__VA_ARGS__)
#define LOG1_CAP(...) LOG1_CAP_SUBM(0xff, ##__VA_ARGS__)

#define LOGE_POOL_SUBM(sub_modules, ...) HAL_MODULE_LOG_ERROR(HAL_LOG_MODULE_POOL, sub_modules, ##__VA_ARGS__)
#define LOGW_POOL_SUBM(sub_modules, ...) HAL_MODULE_LOG_WARNING(HAL_LOG_MODULE_POOL, sub_modules, ##__VA_ARGS__)
#define LOGI_POOL_SUBM(sub_modules, ...) HAL_MODULE_LOG_INFO(HAL_LOG_MODULE_POOL, sub_modules, ##__VA_ARGS__)
#define LOGD_POOL_SUBM(sub_modules, ...) HAL_MODULE_LOG_DEBUG(HAL_LOG_MODULE_POOL, sub_modules, ##__VA_ARGS__)
#define LOGV_POOL_SUBM(sub_modules, ...) HAL_MODULE_LOG_VERBOSE(HAL_LOG_MODULE_POOL, sub_modules, ##__VA_ARGS__)
#define LOG1_POOL_SUBM(sub_modules, ...) HAL_MODULE_LOG_LOW1(HAL_LOG_MODULE_POOL, sub_modules, ##__VA_ARGS__)

#define LOGE_POOL(...) LOGE_POOL_SUBM(0xff, ##__VA_ARGS__)
#define LOGW_POOL(...) LOGW_POOL_SUBM(0xff, ##__VA_ARGS__)
#define LOGI_POOL(...) LOGI_POOL_SUBM(0xff, ##__VA_ARGS__)
#define LOGD_POOL(...) LOGD_POOL_SUBM(0xff, ##__VA_ARGS__)
#define LOGV_POOL(...) LOGV_POOL_SUBM(0xff, ##__VA_ARGS__)
#define LOG1_POOL(...) LOG1_POOL_SUBM(0xff, ##__VA_ARGS__)

#define LOGE_MSG_SUBM(sub_modules, ...) HAL_MODULE_LOG_ERROR(HAL_LOG_MODULE_MSG, sub_modules, ##__VA_ARGS__)
#define LOGW_MSG_SUBM(sub_modules, ...) HAL_MODULE_LOG_WARNING(HAL_LOG_MODULE_MSG, sub_modules, ##__VA_ARGS__)
#define LOGI_MSG_SUBM(sub_modules, ...) HAL_MODULE_LOG_INFO(HAL_LOG_MODULE_MSG, sub_modules, ##__VA_ARGS__)
#define LOGD_MSG_SUBM(sub_modules, ...) HAL_MODULE_LOG_DEBUG(HAL_LOG_MODULE_MSG, sub_modules, ##__VA_ARGS__)
#define LOGV_MSG_SUBM(sub_modules, ...) HAL_MODULE_LOG_VERBOSE(HAL_LOG_MODULE_MSG, sub_modules, ##__VA_ARGS__)
#define LOG1_MSG_SUBM(sub_modules, ...) HAL_MODULE_LOG_LOW1(HAL_LOG_MODULE_MSG, sub_modules, ##__VA_ARGS__)

#define LOGE_MSG(...) LOGE_MSG_SUBM(0xff, ##__VA_ARGS__)
#define LOGW_MSG(...) LOGW_MSG_SUBM(0xff, ##__VA_ARGS__)
#define LOGI_MSG(...) LOGI_MSG_SUBM(0xff, ##__VA_ARGS__)
#define LOGD_MSG(...) LOGD_MSG_SUBM(0xff, ##__VA_ARGS__)
#define LOGV_MSG(...) LOGV_MSG_SUBM(0xff, ##__VA_ARGS__)
#define LOG1_MSG(...) LOG1_MSG_SUBM(0xff, ##__VA_ARGS__)

#else

#undef LOGD
#define LOGD(fmt, args...) ALOGD(fmt, ##args)
#undef LOGL
#define LOGL(fmt, args...) ALOGD(fmt, ##args)
#undef LOGW
#define LOGW(fmt, args...) ALOGW(fmt, ##args)
#undef LOGH
#define LOGH(fmt, args...) ALOGD(fmt, ##args)
#undef LOGE
#define LOGE(fmt, args...) ALOGE(fmt, ##args)
#undef LOGI
#define LOGI(fmt, args...) ALOGV(fmt, ##args)



#define LOG1(...)
#define LOG2(...)
#define LOGR(...)
#define LOGAIQ(...)

#define LOGD(...)
#define LOGV(...)
#define LOGI(...)

#define HAL_TRACE_NAME(level, name)
#define HAL_TRACE_CALL(level)
#define HAL_TRACE_CALL_PRETTY(level)

#endif
#endif /* __LOGHELPERANDROID__ */

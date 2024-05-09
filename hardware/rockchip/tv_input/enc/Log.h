/*
 * Copyright 2021 Rockchip Electronics Co. LTD
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
 *
 * author: kevin.chen@rock-chips.com
 */
#ifndef LOG_H_
#define LOG_H_

#include <stdarg.h>

#define LOGD(fmt, ...) _LOGD(LOG_TAG, fmt, NULL, ##__VA_ARGS__)
#define LOGE(fmt, ...) _LOGE(LOG_TAG, fmt, NULL, ##__VA_ARGS__)

#if OPEN_DEBUG
#define ALOGI(fmt, ...) _LOGD(LOG_TAG, fmt, NULL, ##__VA_ARGS__)
#define ALOGW(fmt, ...) _LOGD(LOG_TAG, fmt, NULL, ##__VA_ARGS__)
#define ALOGV(fmt, ...) _LOGD(LOG_TAG, fmt, NULL, ##__VA_ARGS__)
#define ALOGD(fmt, ...) _LOGD(LOG_TAG, fmt, NULL, ##__VA_ARGS__)
#define ALOGE(fmt, ...) _LOGE(LOG_TAG, fmt, NULL, ##__VA_ARGS__)

#else

#define ALOGI(fmt, ...)
#define ALOGW(fmt, ...)
#define ALOGV(fmt, ...)
#define ALOGD(fmt, ...)
#define ALOGE(fmt, ...)
#endif

void _LOGD(const char* tag, const char* fmt, const char* func, ...);
void _LOGE(const char* tag, const char* fmt, const char* func, ...);

#define Trace() ALOGD("file: %s func %s line %d \n", __FILE__, __FUNCTION__,__LINE__)
#endif  // LOG_H_

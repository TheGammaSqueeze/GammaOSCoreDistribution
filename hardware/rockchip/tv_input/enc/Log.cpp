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
#define MODULE_TAG "os_log"

#include "Log.h"
#include <android/log.h>
#include <stdlib.h>

void _LOGD(const char* tag, const char* fmt, const char* fname, ...) {
    va_list args;
    va_start(args, fname);
    __android_log_vprint(ANDROID_LOG_INFO, tag, fmt, args);
    va_end(args);
}

void _LOGE(const char* tag, const char* fmt, const char* fname, ...) {
    va_list args;
    va_start(args, fname);
    __android_log_vprint(ANDROID_LOG_ERROR, tag, fmt, args);
    va_end(args);
}

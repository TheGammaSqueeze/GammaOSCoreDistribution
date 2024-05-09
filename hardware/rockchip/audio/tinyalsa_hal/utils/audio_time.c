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
 * hh@rock-chip.com
 *
 */

#include "audio_time.h"
#include <sys/time.h>
#include <time.h>
#include <unistd.h>
#include <log/log.h>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "RKAudioTime"

uint64_t getNowUs() {
    struct timespec time = {0, 0};
    clock_gettime(CLOCK_REALTIME, &time);
    return (uint64_t)time.tv_sec * 1000000 + (uint64_t)time.tv_nsec / 1000;  /* microseconds */
}

uint64_t getNowMs() {
    return getNowUs()/1000;    /* milliseconds */
}

uint64_t getRelativeUs() {
    struct timespec time = {0, 0};
    clock_gettime(CLOCK_MONOTONIC, &time);
    return (uint64_t)time.tv_sec * 1000000 + (uint64_t)time.tv_nsec / 1000; /* microseconds */
}

uint64_t getRelativeMs() {
    return getRelativeUs()/1000;    /* milliseconds */
}

void sleepMs(uint64_t time) {
    usleep(time*1000);
}

void sleepUs(uint64_t time) {
    usleep(time);
}

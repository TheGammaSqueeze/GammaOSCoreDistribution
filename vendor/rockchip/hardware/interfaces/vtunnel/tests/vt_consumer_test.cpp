/* Copyright 2022 Rockchip Electronics Co. LTD
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
#include <errno.h>
#include <unistd.h>
#include <stdlib.h>
#include <utils/Log.h>
#include <sys/time.h>
#include <time.h>

#include "../include/video_tunnel.h"

static uint64_t get_relative_time_us() {
    struct timespec time = {0, 0};
    clock_gettime(CLOCK_MONOTONIC, &time);
    return (uint64_t)time.tv_sec * 1000000 + (uint64_t)time.tv_nsec / 1000; /* microseconds */
}

int main(int argc, const char **argv) {
    (void)argc;
    (void)argv;
    int vt_fd = -1;
    int tunnel_id = 0;
    vt_buffer_t *buffer = 0;
    int64_t expected_present_time = 0ll;
    int ret = 0;

    if (argc == 2) {
        tunnel_id = atoi(argv[1]);
    }

    vt_fd = rk_vt_open();

    ALOGE("tunnel id %d", tunnel_id);
    ret = rk_vt_connect(vt_fd, tunnel_id, RKVT_ROLE_CONSUMER);
    if (ret < 0) {
        return ret;
    }

    while (true) {
        buffer = NULL;
        uint64_t start = get_relative_time_us();
        ret = rk_vt_acquire_buffer(vt_fd, tunnel_id, 0, &buffer, &expected_present_time);
        uint64_t end = get_relative_time_us();
        ALOGE("acquired buffer %p space time %lld us", buffer, (long long)(end - start));
        if (ret != 0) {
            continue;
        }
        ALOGE("acquire buffer %p", buffer);
        rk_vt_release_buffer(vt_fd, tunnel_id, buffer);
        usleep(10000);
        ALOGE("release buffer %p", buffer);
    }

    ret = rk_vt_disconnect(vt_fd, tunnel_id, RKVT_ROLE_CONSUMER);
    if (ret < 0) {
        return ret;
    }

    ret = rk_vt_close(vt_fd);
    if (ret < 0) {
        return ret;
    }

    return 0;
}


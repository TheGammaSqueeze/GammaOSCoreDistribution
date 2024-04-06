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
#include <string.h>
#include <utils/Log.h>
#include <cutils/native_handle.h>

#include "../include/video_tunnel_win.h"

int main(int argc, const char **argv) {
    (void)argc;
    (void)argv;
    int fence_fd = -1;
    int ret = 0;
    int runningCnt = 1000;
    vt_buffer_t *buffers[16];
    vt_win_attr_t attr;
    void *win = NULL;

    memset(&attr, 0, sizeof(vt_win_attr_t));
    attr.width = 1280;
    attr.height = 720;
    attr.format = 27;
    attr.buffer_cnt = 16;
    ret = rk_vt_win_create(&attr, &win);
    if (ret < 0) {
        return ret;
    }

    memset(buffers, 0, sizeof(buffers));
    for (int i = 0; i < 16; i++) {
        ret = rk_vt_win_dequeueBufferAndWait(win, &(buffers[i]));
        if (ret < 0) {
            break;
        }
        printf("buffers[%d] %p\n", i, buffers[i]);
        printf("buffer handle fds %d, ints %d, fd[0] %d\n",
                buffers[i]->handle->numFds,
                buffers[i]->handle->numInts,
                buffers[i]->handle->data[0]);
        rk_vt_win_queueBuffer(win, buffers[i], -1, 0);
    }

    usleep(1000000);

    while (runningCnt-- > 0) {
        vt_buffer_t *buffer = NULL;
        ret = rk_vt_win_dequeueBuffer(win, &buffer, -1, &fence_fd);
        if (ret < 0) {
            continue;
        }
        printf("dequeue buffer handle %p\n", buffer);
        usleep(100000);
        rk_vt_win_queueBuffer(win, buffer, fence_fd, 0ll);
        printf("queue buffer handle %p\n", buffer);
    }

    ret = rk_vt_win_destroy(&win);
    if (ret < 0) {
        return ret;
    }

    return 0;
}


/*
 * Copyright 2022 Rockchip Electronics Co. LTD
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

#include "log/log.h"
#include <sys/time.h>
#include <utils/Timers.h>
#include <string.h>

#include "RTSidebandWindow.h"
#include "video_tunnel_win.h"

#ifdef __cplusplus
extern "C" {
#endif

using namespace android;

int rk_vt_win_create(const vt_win_attr_t *attr, void **win) {
    int err = 0;
    RTSidebandWindow *sidebandWin = NULL;

    sidebandWin = new RTSidebandWindow();
    err = sidebandWin->init(attr);
    if (err != 0) {
        delete sidebandWin;
        return err;
    }

    *win = (void *)sidebandWin;

    return err;
}

int rk_vt_win_destroy(void **win) {
    int err = 0;
    RTSidebandWindow *sidebandWin = (RTSidebandWindow *)(*win);

    err = sidebandWin->release();
    delete sidebandWin;
    *win = NULL;

    return err;
}

int rk_vt_win_setAttr(void *win, const vt_win_attr_t *data) {
    int err = 0;
    RTSidebandWindow *sidebandWin = (RTSidebandWindow *)win;

    return sidebandWin->setAttr(data);
}

int rk_vt_win_getAttr(void *win, vt_win_attr_t *data) {
    int err = 0;
    RTSidebandWindow *sidebandWin = (RTSidebandWindow *)win;

    return sidebandWin->getAttr(data);
}

int rk_vt_win_start(void *win) {
    int err = 0;
    RTSidebandWindow *sidebandWin = (RTSidebandWindow *)win;

    return sidebandWin->start();
}

int rk_vt_win_stop(void *win) {
    int err = 0;
    RTSidebandWindow *sidebandWin = (RTSidebandWindow *)win;

    return sidebandWin->stop();
}

int rk_vt_win_flush(void *win) {
    int err = 0;
    RTSidebandWindow *sidebandWin = (RTSidebandWindow *)win;

    return sidebandWin->flush();
}

int rk_vt_win_cancelBuffer(void *win, vt_buffer_t *buffer) {
    int err = 0;
    RTSidebandWindow *sidebandWin = (RTSidebandWindow *)win;

    return sidebandWin->cancelBuffer(buffer);
}

int rk_vt_win_dequeueBuffer(
        void *win, vt_buffer_t **buffer, int timeout_ms, int *fence) {
    int err = 0;
    RTSidebandWindow *sidebandWin = (RTSidebandWindow *)win;

    return sidebandWin->dequeueBuffer(buffer, timeout_ms, fence);
}

int rk_vt_win_dequeueBufferAndWait(void *win, vt_buffer_t **buffer) {
    int err = 0;
    int fence = -1;
    RTSidebandWindow *sidebandWin = (RTSidebandWindow *)win;

    return sidebandWin->dequeueBuffer(buffer, -1, &fence);
}

int rk_vt_win_queueBuffer(
        void *win, vt_buffer_t *buffer, int fence, int64_t expected_present_time) {
    int err = 0;
    RTSidebandWindow *sidebandWin = (RTSidebandWindow *)win;

    return sidebandWin->queueBuffer(buffer, fence, expected_present_time);
}

int rk_vt_win_allocSidebandStream(void *win, buffer_handle_t *handle) {
    int err = 0;
    RTSidebandWindow *sidebandWin = (RTSidebandWindow *)win;

    return sidebandWin->allocateSidebandHandle(handle);
}

#ifdef __cplusplus
}
#endif


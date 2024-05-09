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

#ifndef _RK_VIDEO_TUNNEL_WIN_H
#define _RK_VIDEO_TUNNEL_WIN_H

#include <cutils/native_handle.h>

#include "video_tunnel.h"

#ifdef __cplusplus
extern "C" {
#endif

int rk_vt_win_create(const vt_win_attr_t *attr, void **win);
int rk_vt_win_destroy(void **win);

int rk_vt_win_setAttr(void *win, const vt_win_attr_t *data);
int rk_vt_win_getAttr(void *win, vt_win_attr_t *data);

int rk_vt_win_start(void *win);
int rk_vt_win_stop(void *win);
int rk_vt_win_flush(void *win);

int rk_vt_win_dequeueBuffer(
        void *win, vt_buffer_t **buffer, int timeout_ms, int *fence);
int rk_vt_win_dequeueBufferAndWait(void *win, vt_buffer_t **buffer);
int rk_vt_win_queueBuffer(
        void *win, vt_buffer_t *buffer, int fence, int64_t expected_present_time);
int rk_vt_win_cancelBuffer(void *win, vt_buffer_t *buffer);

int rk_vt_win_allocSidebandStream(void *win, buffer_handle_t *handle);

#ifdef __cplusplus
}
#endif

#endif  // _RK_VIDEO_TUNNEL_WIN_H


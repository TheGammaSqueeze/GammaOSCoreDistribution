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

#ifndef _RK_VIDEO_TUNNEL_H
#define _RK_VIDEO_TUNNEL_H

#include <stdint.h>
#include <stdbool.h>
#include <cutils/native_handle.h>

#ifdef __cplusplus
extern "C" {
#endif

#define VT_BUFFER_MAGIC             'V'

typedef enum vt_cmd {
    VT_CMD_SET_VIDEO_STATUS,
    VT_CMD_GET_VIDEO_STATUS,
    VT_CMD_SET_GAME_MODE,
    VT_CMD_SET_SOURCE_CROP,
    VT_CMD_SET_SOLID_COLOR_BUF,
    VT_CMD_SET_VIDEO_TYPE,
} vt_cmd_t;

typedef enum vt_role {
    RKVT_ROLE_PRODUCER,
    RKVT_ROLE_CONSUMER,
    RKVT_ROLE_INVALID,
} vt_role_t;

typedef enum vt_buffer_mode {
    RKVT_BUFFER_INTERNAL,
    RKVT_BUFFER_EXTERNAL,
    RKVT_BUFFER_MODE_BUTT,
} vt_bufmode_t;

typedef struct vt_rect {
    int left;
    int top;
    int right;
    int bottom;
} vt_rect_t;

typedef struct vt_sideband_data {
    int         version;
    int         tunnel_id;
    uint64_t    session_id;
    vt_rect_t   crop;
    int         width;
    int         height;
    int         hor_stride;
    int         ver_stride;
    int         byte_stride;
    int         format;
    int         transform;
    int         size;
    int         modifier;
    uint64_t    usage;
    uint64_t    data_space;
    uint64_t    fps;
    int         compress_mode;
    int         reserved[13];
} vt_sideband_data_t;

typedef struct vt_win_attr {
    int      struct_size;
    int      struct_ver;
    int      left;
    int      top;
    int      right;
    int      bottom;
    int      width;
    int      height;
    int      format;
    uint64_t usage;
    uint64_t data_space;
    int      transform;
    int      compress_mode;
    uint32_t buffer_cnt;
    uint32_t remain_cnt;
    void    *native_window;
} vt_win_attr_t;

typedef struct vt_cmd_data {
    struct vt_rect crop;
    int data;
    int client;
} vt_cmd_data_t;

typedef struct vt_buffer {
    int magic;
    int struct_size;
    native_handle_t *handle;
    int fence_fd;
    uint64_t buffer_id;
    vt_rect_t crop;
    vt_rect_t dis_rect;
    int64_t private_data;
    vt_bufmode_t buffer_mode;
    int reserve[5];
} vt_buffer_t;

int rk_vt_open();
int rk_vt_close(int fd);
int rk_vt_alloc_id(int fd, int *tunnel_id);
int rk_vt_free_id(int fd, int tunnel_id);
int rk_vt_reset(int fd, int tunnel_id);
int rk_vt_connect(int fd, int tunnel_id, int role);
int rk_vt_disconnect(int fd, int tunnel_id, int role);

/* for producer */
int rk_vt_queue_buffer(int fd, int tunnel_id, vt_buffer_t *buffer, int64_t expected_present_time);
int rk_vt_dequeue_buffer(int fd, int tunnel_id, int timeout_ms, vt_buffer_t **buffer);
int rk_vt_cancel_buffer(int fd, int tunnel_id, vt_buffer_t *buffer);
int rk_vt_set_sourceCrop(int fd, int tunnel_id, struct vt_rect rect);
int rk_vt_getDisplayVsyncAndPeroid(int fd, int tunnel_id, uint64_t *timestamp, uint32_t *period);

/* for consumer */
int rk_vt_acquire_buffer(int fd, int tunnel_id, int timeout_ms,
        vt_buffer_t **buffer, int64_t *expected_present_time);
int rk_vt_release_buffer(int fd, int tunnel_id, vt_buffer_t *buffer);
int rk_vt_poll_cmd(int fd, int time_out);
int rk_vt_setDisplayVsyncAndPeroid(int fd, int tunnel_id, uint64_t timestamp, uint32_t period);

/* for video cmd */
int rk_vt_set_mode(int fd, int block_mode);
int rk_vt_send_cmd(int fd, int tunnel_id, enum vt_cmd cmd, int cmd_data);
int rk_vt_recv_cmd(int fd, int tunnel_id, enum vt_cmd *cmd, struct vt_cmd_data *cmd_data);
bool rk_vt_query_has_consumer(int fd, int tunnel_id);

/* for buffer operation */
vt_buffer_t* rk_vt_buffer_malloc();
int rk_vt_buffer_free(vt_buffer_t **buffer);
int rk_vt_buffer_checkAvail(const vt_buffer_t *buffer);

#ifdef __cplusplus
}
#endif

#endif /* _RK_VIDEO_TUNNEL_H */


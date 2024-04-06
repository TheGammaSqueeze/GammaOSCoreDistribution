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
// #define LOG_NDEBUG 0
#define LOG_TAG "RKVideoTunnel"

#include "video_tunnel.h"
#include "rkvtunnel.h"

#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <utils/Log.h>
#include <sys/time.h>
#include <time.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RKVT_DEV_NAME "/dev/rkvtunnel"

static uint64_t get_relative_time_us() {
    struct timespec time = {0, 0};
    clock_gettime(CLOCK_MONOTONIC, &time);
    return (uint64_t)time.tv_sec * 1000000 + (uint64_t)time.tv_nsec / 1000; /* microseconds */
}

int rk_vt_open() {
    int fd = -1;

    fd = open(RKVT_DEV_NAME, O_RDWR, 0);
    if (fd < 0) {
        ALOGE("couldn't open %s, err is: %s.", RKVT_DEV_NAME, strerror(errno));
        return fd;
    }

    return fd;
}

int rk_vt_close(int fd) {
    int ret = close(fd);
    if (ret < 0) {
        return -errno;
    }

    return ret;
}

int rk_vt_alloc_id(int fd, int *tunnel_id) {
    int ret = 0;
    struct rkvt_alloc_id_data alloc_id_data;

    alloc_id_data.vt_id = -1;
    ret = ioctl(fd, RKVT_IOC_ALLOC_ID, &alloc_id_data);
    if (ret < 0) {
        ALOGE("fail to alloc tunnel id(fd=%d), error: %s", fd, strerror(errno));
        return -errno;
    }

    *tunnel_id = alloc_id_data.vt_id;
    ALOGV("VT alloc tunnel id %d.", *tunnel_id);

    return 0;
}

int rk_vt_free_id(int fd, int tunnel_id) {
    int ret = 0;
    struct rkvt_alloc_id_data free_id_data;

    free_id_data.vt_id = tunnel_id;
    ret = ioctl(fd, RKVT_IOC_FREE_ID, &free_id_data);
    if (ret < 0) {
        ALOGE("fail to alloc tunnel id(fd=%d), error: %s", fd, strerror(errno));
        return -errno;
    }
    ALOGV("VT free tunnel id %d.", tunnel_id);

    return 0;
}

int rk_vt_connect(int fd, int tunnel_id, int role) {
    int ret = 0;
    struct rkvt_ctrl_data connect_data;

    ALOGV("VT connect tunnel id %d role %s start.",
           tunnel_id, role == RKVT_ROLE_PRODUCER ? "producer" : "consumer");

    connect_data.ctrl_cmd = RKVT_CTRL_CONNECT;
    connect_data.vt_id = tunnel_id;
    connect_data.caller = role;
    ret = ioctl(fd, RKVT_IOC_CTRL, &connect_data);
    if (ret < 0) {
        ALOGE("fail to connect vt(fd=%d), error: %s", fd, strerror(errno));
        return -errno;
    }

    ALOGV("VT connect tunnel id %d role %s done.",
           tunnel_id, role == RKVT_ROLE_PRODUCER ? "producer" : "consumer");

    return 0;

}

int rk_vt_disconnect(int fd, int tunnel_id, int role) {
    int ret = 0;
    struct rkvt_ctrl_data disconnect_data;

    ALOGV("VT disconnect tunnel id %d role %s start.",
           tunnel_id, role == RKVT_ROLE_PRODUCER ? "producer" : "consumer");

    disconnect_data.ctrl_cmd = RKVT_CTRL_DISCONNECT;
    disconnect_data.vt_id = tunnel_id;
    disconnect_data.caller = role;
    ret = ioctl(fd, RKVT_IOC_CTRL, &disconnect_data);
    if (ret < 0) {
        ALOGW("fail to disconnect vt(fd=%d), error: %s", fd, strerror(errno));
    }

    ALOGV("VT disconnect tunnel id %d role %s done.",
           tunnel_id, role == RKVT_ROLE_PRODUCER ? "producer" : "consumer");

    return 0;
}

int rk_vt_reset(int fd, int tunnel_id) {
    int ret = 0;
    struct rkvt_ctrl_data reset_data;

    reset_data.ctrl_cmd = RKVT_CTRL_RESET;
    reset_data.vt_id = tunnel_id;
    ret = ioctl(fd, RKVT_IOC_CTRL, &reset_data);
    if (ret < 0) {
        ALOGE("fail to reset vt(fd=%d), error: %s", fd, strerror(errno));
        return -errno;
    }

    ALOGV("VT reset tunnel id %d.", tunnel_id);

    return 0;
}

/* for producer */
int rk_vt_queue_buffer(
        int fd, int tunnel_id, vt_buffer_t *buffer, int64_t expected_present_time) {
    (void)expected_present_time;
    int ret = 0;
    struct rkvt_buf_data buf_data;
    native_handle_t *handle = NULL;

    ret = rk_vt_buffer_checkAvail(buffer);
    if (ret != 0) {
        ALOGE("VTQB [%d] vt buffer is illegal", tunnel_id);
        return -errno;
    }

    if (buffer->handle->numFds > MAX_BUF_HANDLE_FDS ||
          buffer->handle->numInts > MAX_BUF_HANDLE_INTS) {
        ALOGE("VTQB [%d] fds(%d) or ints(%d) out of range(%d, %d)",
               tunnel_id, buffer->handle->numFds, buffer->handle->numInts,
               MAX_BUF_HANDLE_FDS, MAX_BUF_HANDLE_INTS);
        return -errno;
    }

    handle = buffer->handle;
    memset(&buf_data, 0, sizeof(buf_data));
    buf_data.vt_id = tunnel_id;
    buf_data.base.num_fds = handle->numFds;
    buf_data.base.num_ints = handle->numInts;
    buf_data.base.fence_fd = buffer->fence_fd;
    buf_data.base.priv_data = (int64_t)buffer;
    buf_data.base.buffer_id = buffer->buffer_id;
    buf_data.base.crop.left = buffer->crop.left;
    buf_data.base.crop.top = buffer->crop.top;
    buf_data.base.crop.right = buffer->crop.right;
    buf_data.base.crop.bottom = buffer->crop.bottom;
    // TODO expected present time may need to be send by user
    buf_data.base.expected_present_time = get_relative_time_us();
    // copy handle fds
    memcpy(&buf_data.base.fds[0], &handle->data[0],
           sizeof(int) * handle->numFds);
    // copy handle ints
    memcpy(&buf_data.base.ints[0], &handle->data[handle->numFds],
           sizeof(int) * handle->numInts);

    ALOGV("VTQB [%d] crop(%d %d %d %d) numFd(%d) numInts(%d) fence(%d) "
          "priv_data(%p) fd-0(%d) buffer-id(%lld) pts(%lld)",
            tunnel_id, buffer->crop.left, buffer->crop.top, buffer->crop.right,
            buffer->crop.bottom, buf_data.base.num_fds, buf_data.base.num_ints,
            buf_data.base.fence_fd, (void *)buf_data.base.priv_data,
            buf_data.base.fds[0], (long long)buf_data.base.buffer_id,
            (long long)buf_data.base.expected_present_time);

    ret = ioctl(fd, RKVT_IOC_QUEUE_BUF, &buf_data);
    if (ret < 0) {
        ALOGE("VTQB [%d] ioctl fail vt(fd=%d), error: %s",
               tunnel_id, fd, strerror(errno));
        return -errno;
    }

    return 0;
}

int rk_vt_dequeue_buffer(
        int fd, int tunnel_id, int timeout_ms, vt_buffer_t **buffer) {
    int ret = 0;
    struct rkvt_buf_data buf_data;

    memset(&buf_data, 0, sizeof(buf_data));
    buf_data.vt_id = tunnel_id;
    buf_data.timeout_ms = timeout_ms;
    ret = ioctl(fd, RKVT_IOC_DEQUE_BUF, &buf_data);
    if (ret < 0) {
        ALOGE("VTDB [%d] ioctl fail vt(fd=%d), error: %s", tunnel_id, fd, strerror(errno));
        return -errno;
    }

    *buffer = (vt_buffer_t *)buf_data.base.priv_data;
    (*buffer)->fence_fd = buf_data.base.fence_fd;
    (*buffer)->buffer_id = buf_data.base.buffer_id;
    (*buffer)->dis_rect.left = buf_data.base.crop.left;
    (*buffer)->dis_rect.top = buf_data.base.crop.top;
    (*buffer)->dis_rect.right = buf_data.base.crop.right;
    (*buffer)->dis_rect.bottom = buf_data.base.crop.bottom;

    ALOGV("VTDB [%d] crop(%d %d %d %d) numFd(%d) numInts(%d) fence(%d) "
          "priv_data(%p) fd-0(%d) buffer-id(%lld)",
            tunnel_id, (*buffer)->dis_rect.left, (*buffer)->dis_rect.top, (*buffer)->dis_rect.right,
            (*buffer)->dis_rect.bottom, buf_data.base.num_fds, buf_data.base.num_ints,
            buf_data.base.fence_fd, (void *)buf_data.base.priv_data,
            (*buffer)->handle->data[0], (long long)(*buffer)->buffer_id);

    return 0;
}

int rk_vt_cancel_buffer(int fd, int tunnel_id, vt_buffer_t *buffer) {
    int ret = 0;
    struct rkvt_buf_data buf_data;
    native_handle_t *handle = NULL;

    ret = rk_vt_buffer_checkAvail(buffer);
    if (ret != 0) {
        ALOGE("VTCB [%d] vt buffer is illegal", tunnel_id);
        return -errno;
    }

    if (buffer->handle->numFds > MAX_BUF_HANDLE_FDS ||
          buffer->handle->numInts > MAX_BUF_HANDLE_INTS) {
        ALOGE("VTCB [%d] fds(%d) or ints(%d) out of range(%d, %d)",
               tunnel_id, buffer->handle->numFds, buffer->handle->numInts,
               MAX_BUF_HANDLE_FDS, MAX_BUF_HANDLE_INTS);
        return -errno;
    }

    handle = buffer->handle;
    memset(&buf_data, 0, sizeof(buf_data));
    buf_data.vt_id = tunnel_id;
    buf_data.base.num_fds = handle->numFds;
    buf_data.base.num_ints = handle->numInts;
    buf_data.base.fence_fd = buffer->fence_fd;
    buf_data.base.priv_data = (int64_t)buffer;
    buf_data.base.buffer_id = 0;
    // copy handle fds
    memcpy(&buf_data.base.fds[0], &handle->data[0],
           sizeof(int) * handle->numFds);
    // copy handle ints
    memcpy(&buf_data.base.ints[0], &handle->data[handle->numFds],
           sizeof(int) * handle->numInts);

    ALOGV("VTCB [%d] numFd(%d) numInts(%d) fence(%d) priv_data(%p) fd-0(%d) buffer-id(%lld)",
            tunnel_id, buf_data.base.num_fds, buf_data.base.num_ints,
            buf_data.base.fence_fd, (void *)buf_data.base.priv_data,
            buf_data.base.fds[0], (long long)buf_data.base.buffer_id);

    ret = ioctl(fd, RKVT_IOC_CANCEL_BUF, &buf_data);
    if (ret < 0) {
        ALOGE("VTQB [%d] ioctl fail vt(fd=%d), error: %s",
               tunnel_id, fd, strerror(errno));
        return -errno;
    }

    return 0;
}

int rk_vt_set_sourceCrop(int fd, int tunnel_id, struct vt_rect rect) {
    return 0;
}

int rk_vt_getDisplayVsyncAndPeroid(
        int fd, int tunnel_id, uint64_t *timestamp, uint32_t *period) {
    return 0;
}

/* for consumer */
int rk_vt_acquire_buffer(int fd, int tunnel_id, int timeout_ms,
        vt_buffer_t **buffer, int64_t *expected_present_time) {
    int ret = 0;
    struct rkvt_buf_data buf_data;
    vt_buffer_t *tmpVtBuf = NULL;

    memset(&buf_data, 0, sizeof(buf_data));
    buf_data.vt_id = tunnel_id;
    buf_data.timeout_ms = timeout_ms;
    ret = ioctl(fd, RKVT_IOC_ACQUIRE_BUF, &buf_data);
    if (ret < 0) {
        ALOGV("VTAB [%d] ioctl fail vt(fd=%d), error: %s", tunnel_id, fd, strerror(errno));
        return -errno;
    }

    tmpVtBuf = rk_vt_buffer_malloc();
    tmpVtBuf->handle = native_handle_create(buf_data.base.num_fds, buf_data.base.num_ints);
    memcpy(&tmpVtBuf->handle->data[0], &buf_data.base.fds[0],
           sizeof(int) * tmpVtBuf->handle->numFds);
    memcpy(&tmpVtBuf->handle->data[tmpVtBuf->handle->numFds],
           &buf_data.base.ints[0],
           sizeof(int) * tmpVtBuf->handle->numInts);
    tmpVtBuf->buffer_id = buf_data.base.buffer_id;
    tmpVtBuf->fence_fd = buf_data.base.fence_fd;
    tmpVtBuf->crop.left = buf_data.base.crop.left;
    tmpVtBuf->crop.top = buf_data.base.crop.top;
    tmpVtBuf->crop.right = buf_data.base.crop.right;
    tmpVtBuf->crop.bottom = buf_data.base.crop.bottom;

    *buffer = tmpVtBuf;
    *expected_present_time = buf_data.base.expected_present_time;

    ALOGV("VTAB [%d] crop(%d %d %d %d) numFd(%d) numInts(%d) fence(%d) "
          "priv_data(%p) fd-0(%d) buffer-id(%lld), pts(%lld)",
            tunnel_id, tmpVtBuf->crop.left, tmpVtBuf->crop.top,
            tmpVtBuf->crop.right, tmpVtBuf->crop.bottom, buf_data.base.num_fds,
            buf_data.base.num_ints,  buf_data.base.fence_fd,
            (void *)buf_data.base.priv_data, tmpVtBuf->handle->data[0],
            (long long)(*buffer)->buffer_id, (long long)buf_data.base.expected_present_time);

    return 0;
}

int rk_vt_release_buffer(
        int fd, int tunnel_id, vt_buffer_t *buffer) {
    int ret = 0;
    struct rkvt_buf_data buf_data;

    memset(&buf_data, 0, sizeof(buf_data));
    buf_data.vt_id = tunnel_id;
    buf_data.base.fence_fd = buffer->fence_fd;
    buf_data.base.num_fds = buffer->handle->numFds;
    buf_data.base.num_ints = buffer->handle->numInts;
    buf_data.base.buffer_id = buffer->buffer_id;
    buf_data.base.crop.left = buffer->dis_rect.left;
    buf_data.base.crop.top = buffer->dis_rect.top;
    buf_data.base.crop.right = buffer->dis_rect.right;
    buf_data.base.crop.bottom = buffer->dis_rect.bottom;
    memcpy(&buf_data.base.fds[0], &buffer->handle->data[0],
           sizeof(int) * buffer->handle->numFds);

    ALOGV("VTRB [%d] crop(%d %d %d %d) numFd(%d) numInts(%d) fence(%d) "
          "priv_data(%p) fd-0(%d) buffer-id(%lld)",
            tunnel_id, buffer->dis_rect.left, buffer->dis_rect.top,
            buffer->dis_rect.right, buffer->dis_rect.bottom,
            buf_data.base.num_fds, buf_data.base.num_ints,
            buf_data.base.fence_fd, (void *)buf_data.base.priv_data,
            buf_data.base.fds[0], (long long)buf_data.base.buffer_id);

    ret = ioctl(fd, RKVT_IOC_RELEASE_BUF, &buf_data);
    if (ret < 0) {
        ALOGE("VTRB [%d] ioctl fail vt(fd=%d), error: %s", tunnel_id, fd, strerror(errno));
        rk_vt_buffer_free(&buffer);
        return -errno;
    }
    // close fd by driver already, can't close again
    for (int i = 0; i < buffer->handle->numFds; i++) {
        buffer->handle->data[i] = -1;
    }

    rk_vt_buffer_free(&buffer);

    return 0;
}

int rk_vt_poll_cmd(int fd, int time_out) {
    return 0;
}

int rk_vt_setDisplayVsyncAndPeroid(
        int fd, int tunnel_id, uint64_t timestamp, uint32_t period) {
    return 0;
}

/* for video cmd */
int rk_vt_set_mode(int fd, int block_mode) {
    return 0;
}

int rk_vt_send_cmd(int fd, int tunnel_id, enum vt_cmd cmd, int cmd_data) {
    return 0;
}

int rk_vt_recv_cmd(int fd, int tunnel_id, enum vt_cmd *cmd, struct vt_cmd_data *cmd_data) {
    return 0;
}

bool rk_vt_query_has_consumer(int fd, int tunnel_id) {
    int ret = 0;
    struct rkvt_ctrl_data has_data;

    has_data.ctrl_cmd = RKVT_CTRL_HAS_CONSUMER;
    has_data.vt_id = tunnel_id;
    ret = ioctl(fd, RKVT_IOC_CTRL, &has_data);
    if (ret < 0) {
        ALOGE("fail to query has consumer vt(fd=%d), error: %s", fd, strerror(errno));
        return -errno;
    }

    ALOGV("VT query has consumer tunnel id %d. has consumer %d", tunnel_id, has_data.ctrl_data);

    return has_data.ctrl_data ? true : false;
}

vt_buffer_t* rk_vt_buffer_malloc() {
    vt_buffer_t *buffer = NULL;


    buffer = (vt_buffer_t *)malloc(sizeof(vt_buffer_t));
    memset(buffer, 0, sizeof(vt_buffer_t));
    buffer->magic = VT_BUFFER_MAGIC;
    buffer->fence_fd = -1;
    buffer->struct_size = sizeof(vt_buffer_t);

    return buffer;
}
int rk_vt_buffer_free(vt_buffer_t **buffer) {
    vt_buffer_t *tmpVtBuf = *buffer;

    if (tmpVtBuf == NULL) {
        return -1;
    }

    if (rk_vt_buffer_checkAvail(tmpVtBuf)) {
        return -1;
    }

    if (tmpVtBuf->fence_fd) {
        close(tmpVtBuf->fence_fd);
    }

    if (tmpVtBuf->handle) {
        native_handle_close(tmpVtBuf->handle);
        native_handle_delete(tmpVtBuf->handle);
    }

    free(tmpVtBuf);
    *buffer = NULL;

    return 0;
}

int rk_vt_buffer_checkAvail(const vt_buffer_t *buffer) {
    if (buffer->magic != VT_BUFFER_MAGIC ||
          buffer->struct_size != sizeof(vt_buffer_t) ||
          buffer->handle == NULL) {
        return -1;
    }

    return 0;
}

#ifdef __cplusplus
}
#endif


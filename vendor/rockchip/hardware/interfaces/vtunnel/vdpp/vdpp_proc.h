// SPDX-License-Identifier: GPL-2.0
/*
 * Copyright (C) Rockchip Electronics Co., Ltd.
 *
 * Author: Rimon Xu <rimon.xu@rock-chips.com>
 */

#ifndef __UAPI_VDPP_VPROC_H
#define __UAPI_VDPP_VPROC_H

#include <linux/ioctl.h>
#include <linux/types.h>

#include <cutils/native_handle.h>

#include "drmgralloc.h"
#include "rk_mpi.h"
#include "video_tunnel.h"

#ifdef __cplusplus
extern "C" {
#endif

using namespace android;

#define ALIGN(value, x)         ((value + (x-1)) & (~(x-1)))

#define VDPP_MAX_BUF_NUM 6

#define VDPP_ALOGD_IF_DEBUG(x, ...)  \
    ALOGD_IF(LogLevel(DBG_DEBUG),"%s,line=%d " x ,__FUNCTION__,__LINE__, ##__VA_ARGS__)


struct vdpp_dmsr_info {
    const char *name;
    uint32_t value;
    uint32_t defaultValue;
};

enum vdpp_dmsr_property {
    VDPP_DMSR_STR_PRI_Y,
    VDPP_DMSR_STR_SEC_Y,
    VDPP_DMSR_DUMPING_Y,
    VDPP_DMSR_WGT_PRI_GAIN_EVEN_1,
    VDPP_DMSR_WGT_PRI_GAIN_EVEN_2,
    VDPP_DMSR_WGT_PRI_GAIN_ODD_1,
    VDPP_DMSR_WGT_PRI_GAIN_ODD_2,
    VDPP_DMSR_WGT_SEC_GAIN,
    VDPP_DMSR_BLK_FLAT_TH,
    VDPP_DMSR_CONTRAST_TO_CONF_MAP_X0,
    VDPP_DMSR_CONTRAST_TO_CONF_MAP_X1,
    VDPP_DMSR_CONTRAST_TO_CONF_MAP_Y0,
    VDPP_DMSR_CONTRAST_TO_CONF_MAP_Y1,
    VDPP_DMSR_DIFF_CORE_TH0,
    VDPP_DMSR_DIFF_CORE_TH1,
    VDPP_DMSR_DIFF_CORE_WGT0,
    VDPP_DMSR_DIFF_CORE_WGT1,
    VDPP_DMSR_DIFF_CORE_WGT2,
    VDPP_DMSR_EDGE_TH_LOW_ARR0,
    VDPP_DMSR_EDGE_TH_LOW_ARR1,
    VDPP_DMSR_EDGE_TH_LOW_ARR2,
    VDPP_DMSR_EDGE_TH_LOW_ARR3,
    VDPP_DMSR_EDGE_TH_LOW_ARR4,
    VDPP_DMSR_EDGE_TH_LOW_ARR5,
    VDPP_DMSR_EDGE_TH_LOW_ARR6,
    VDPP_DMSR_EDGE_TH_HIGH_ARR0,
    VDPP_DMSR_EDGE_TH_HIGH_ARR1,
    VDPP_DMSR_EDGE_TH_HIGH_ARR2,
    VDPP_DMSR_EDGE_TH_HIGH_ARR3,
    VDPP_DMSR_EDGE_TH_HIGH_ARR4,
    VDPP_DMSR_EDGE_TH_HIGH_ARR5,
    VDPP_DMSR_EDGE_TH_HIGH_ARR6,
    VDPP_DMSR_PROPERTY_COUNT,
};

enum LOG_LEVEL
{
    //Log level flag
    /*1*/
    DBG_FETAL = 1 << 0,
    /*2*/
    DBG_ERROR = 1 << 1,
    /*4*/
    DBG_WARN  = 1 << 2,
    /*8*/
    DBG_INFO  = 1 << 3,
    /*16*/
    DBG_DEBUG = 1 << 4,
    /*32*/
    DBG_VERBOSE = 1 << 5,
    /*Mask*/
    DBG_MARSK = 0xFF,
};

struct vdpp_buffer_handle {
    vt_buffer_t* vtBuffer;

    int slot;
    int vir_w;
    int vir_h;
    uint32_t stride;
    int format;
    int usage;
    int prime_fd;

    bool used;
};

typedef struct vdpp_rect {
    int left;
    int top;
    int right;
    int bottom;
} vdpp_rect_t;

struct vdpp_dev {
    void* ctx;

    DrmGralloc* drm_gralloc;
    struct vdpp_buffer_handle hdl[VDPP_MAX_BUF_NUM];
    vdpp_rect_t disp_rect;

    pthread_mutex_t vdppLock;

    bool initial;
    bool vdpp_enable;
    int tunnel_id;
};

void vdpp_create_ctx(struct vdpp_dev* dev);
void vdpp_destroy_ctx(struct vdpp_dev* dev);
void vdpp_dev_init(struct vdpp_dev* dev, buffer_handle_t handle);
void vdpp_update_disp_rect(struct vdpp_dev* dev, vt_buffer_t* buffer);
struct vdpp_buffer_handle* vdpp_get_unused_buf(struct vdpp_dev* dev);
bool vdpp_access(struct vdpp_dev* dev, vt_buffer_t* buffer);
int vdpp_process_frame(struct vdpp_dev* dev, vt_buffer_t* srcbuf, vt_buffer_t* dstbuf);

#ifdef __cplusplus
}
#endif
#endif

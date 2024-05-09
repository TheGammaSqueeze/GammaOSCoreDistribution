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

#ifndef __VDPP_API_H__
#define __VDPP_API_H__

#include <stdint.h>
#include <stdbool.h>

#include "rk_type.h"
#include "rk_mpi.h"

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Platform video codec hardware feature
 */
typedef enum MppClientType_e {
    VPU_CLIENT_VDPU1        = 0,    /* 0x00000001 */
    VPU_CLIENT_VDPU2        = 1,    /* 0x00000002 */
    VPU_CLIENT_VDPU1_PP     = 2,    /* 0x00000004 */
    VPU_CLIENT_VDPU2_PP     = 3,    /* 0x00000008 */
    VPU_CLIENT_AV1DEC       = 4,    /* 0x00000010 */

    VPU_CLIENT_HEVC_DEC     = 8,    /* 0x00000100 */
    VPU_CLIENT_RKVDEC       = 9,    /* 0x00000200 */
    VPU_CLIENT_AVSPLUS_DEC  = 12,   /* 0x00001000 */
    VPU_CLIENT_JPEG_DEC     = 13,   /* 0x00002000 */

    VPU_CLIENT_RKVENC       = 16,   /* 0x00010000 */
    VPU_CLIENT_VEPU1        = 17,   /* 0x00020000 */
    VPU_CLIENT_VEPU2        = 18,   /* 0x00040000 */
    VPU_CLIENT_VEPU2_JPEG   = 19,   /* 0x00080000 */
    VPU_CLIENT_VEPU22       = 24,   /* 0x01000000 */

    IEP_CLIENT_TYPE         = 28,   /* 0x10000000 */
    VDPP_CLIENT_TYPE        = 29,   /* 0x20000000 */

    VPU_CLIENT_BUTT,
} MppClientType;

enum VDPP_FMT {
    VDPP_FMT_YUV444 = 0,
    VDPP_FMT_YUV420 = 3,
};

enum VDPP_YUV_SWAP {
    VDPP_YUV_SWAP_SP_UV,
    VDPP_YUV_SWAP_SP_VU,
};

enum VDPP_PARAM_TYPE {
    VDPP_PARAM_TYPE_COM,
    VDPP_PARAM_TYPE_DMSR,
    VDPP_PARAM_TYPE_ZME_COM,
    VDPP_PARAM_TYPE_ZME_COEFF,
};

typedef enum VdppCmd_e {
    VDPP_CMD_INIT,                           // reset msg to all zero
    VDPP_CMD_SET_SRC,                        // config source image info
    VDPP_CMD_SET_DST,                        // config destination image info
    VDPP_CMD_SET_COM_CFG,

    // DMSR command
    VDPP_CMD_SET_DMSR_CFG         = 0x0100,   // config DMSR configure
    // ZME command
    VDPP_CMD_SET_ZME_COM_CFG      = 0x0200,   // config ZME COM configure
    VDPP_CMD_SET_ZME_COEFF_CFG,               // config ZME COEFF configure
    // hardware trigger command
    VDPP_CMD_RUN_SYNC             = 0x1000,   // start sync mode process

} VdppCmd;

typedef void* VdppCtx;
typedef struct vdpp_com_ctx_t vdpp_com_ctx;


// iep image for external user
typedef struct VdppImg_t {
    // RK_U16  act_w;          // act_width
    // RK_U16  act_h;          // act_height
    // RK_S16  x_off;          // x offset for the vir,word unit
    // RK_S16  y_off;          // y offset for the vir,word unit

    // RK_U16  vir_w;          // unit in byte
    // RK_U16  vir_h;          // unit in byte
    // RK_U32  format;         // IepFormat
    RK_U32  mem_addr;       // base address fd
    RK_U32  uv_addr;        // chroma address fd + (offset << 10)
    RK_U32  uv_off;
    // RK_U32  v_addr;
} VdppImg;

typedef struct vdpp_com_ops_t {
    int (*init)(VdppCtx *ctx);
    MPP_RET (*deinit)(VdppCtx ctx);
    MPP_RET (*control)(VdppCtx ctx, VdppCmd cmd, void *param);
    void (*release)(vdpp_com_ctx *ctx);
} vdpp_com_ops;

typedef struct vdpp_com_ctx_t {
    vdpp_com_ops *ops;
    VdppCtx priv;
    RK_S32 ver;
} vdpp_com_ctx;

union vdpp_api_content {
    struct {
        // enum VDPP_FMT sfmt;
        enum VDPP_YUV_SWAP sswap;
        enum VDPP_FMT dfmt;
        enum VDPP_YUV_SWAP dswap;
        RK_S32 src_width;
        RK_S32 src_height;
        RK_S32 src_vir_w;
        RK_S32 dst_width;
        RK_S32 dst_height;
        RK_S32 dst_vir_w;
    } com;

    struct {
        bool enable;
        RK_U32 str_pri_y;
        RK_U32 str_sec_y;
        RK_U32 dumping_y;
        RK_U32 wgt_pri_gain_even_1;
        RK_U32 wgt_pri_gain_even_2;
        RK_U32 wgt_pri_gain_odd_1;
        RK_U32 wgt_pri_gain_odd_2;
        RK_U32 wgt_sec_gain;
        RK_U32 blk_flat_th;
        RK_U32 contrast_to_conf_map_x0;
        RK_U32 contrast_to_conf_map_x1;
        RK_U32 contrast_to_conf_map_y0;
        RK_U32 contrast_to_conf_map_y1;
        RK_U32 diff_core_th0;
        RK_U32 diff_core_th1;
        RK_U32 diff_core_wgt0;
        RK_U32 diff_core_wgt1;
        RK_U32 diff_core_wgt2;
        RK_U32 edge_th_low_arr[7];
        RK_U32 edge_th_high_arr[7];
    } dmsr;

    struct {
        bool bypass_enable;
        bool dering_enable;
        RK_U32 dering_sen_0;
        RK_U32 dering_sen_1;
        RK_U32 dering_blend_alpha;
        RK_U32 dering_blend_beta;
        RK_S16 (*tap8_coeff)[17][8];
        RK_S16 (*tap6_coeff)[17][8];
    } zme;

};

struct vdpp_api_params {
    enum VDPP_PARAM_TYPE ptype;
    union vdpp_api_content param;
};

vdpp_com_ctx* rockchip_vdpp_api_alloc_ctx(void);
void rockchip_vdpp_api_release_ctx(vdpp_com_ctx *com_ctx);

#ifdef __cplusplus
}
#endif

#endif

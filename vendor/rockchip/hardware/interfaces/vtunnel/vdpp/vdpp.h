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

#ifndef __VDPP_H__
#define __VDPP_H__

#include <stdint.h>

#include "rk_type.h"
#include "vdpp_reg.h"

/* vdpp log marco */
#define VDPP_DBG_TRACE             (0x00000001)
#define VDPP_DBG_INT               (0x00000002)

extern RK_U32 vdpp_debug;

#define VDPP_DBG(level, fmt, ...)\
do {\
    if (level & vdpp_debug)\
    { ALOGE(fmt, ## __VA_ARGS__); }\
} while (0)

/* marco define */
#define RKMIN(a, b)                     ( (a)<(b)?(a):(b) )
#define RKMAX(a, b)                     ( (a)>(b)?(a):(b) )
#define RKCLIP(x, a, b)                 ( RKMIN( (RKMAX((x), (a))), (b)))

#define SCALE_FACTOR_DN_FIXPOINT_SHIFT 12
#define SCALE_FACTOR_UP_FIXPOINT_SHIFT 16
#define GET_SCALE_FACTOR_DN(src,dst)     ((((src) - 1) << SCALE_FACTOR_DN_FIXPOINT_SHIFT)  / ((dst) - 1))
#define GET_SCALE_FACTOR_UP(src,dst)     ((((src) - 1) << SCALE_FACTOR_UP_FIXPOINT_SHIFT)  / ((dst) - 1))

enum ZME_FMT {
    FMT_YCbCr420_888    = 4 ,
    FMT_YCbCr444_888    = 6 ,
};

enum {
    SCL_NEI = 0,
    SCL_BIL = 1,
    SCL_BIC = 2,
    SCL_MPH = 3,
};

typedef struct {
    RK_U16 act_width  ;
    RK_U16 dsp_width  ;

    RK_U16 act_height ;
    RK_U16 dsp_height ;

    RK_U8  dering_en  ;

    RK_U8  xsd_en     ;
    RK_U8  xsu_en     ;
    RK_U8  xsd_bypass ;
    RK_U8  xsu_bypass ;
    RK_U8  xscl_mode  ;
    RK_U16 xscl_factor;
    RK_U8  xscl_offset;

    RK_U8  ysd_en     ;
    RK_U8  ysu_en     ;
    RK_U8  ys_bypass  ;
    RK_U8  yscl_mode  ;
    RK_U16 yscl_factor;
    RK_U8  yscl_offset;

    RK_U8  xavg_en    ;
    RK_U8  xgt_en     ;
    RK_U8  xgt_mode   ;

    RK_U8  yavg_en    ;
    RK_U8  ygt_en     ;
    RK_U8  ygt_mode   ;

    RK_S16 (*xscl_zme_coe)[8] ;
    RK_S16 (*yscl_zme_coe)[8] ;
} scl_info;

typedef struct FdTransInfo_t {
    RK_U32        reg_idx;
    RK_U32        offset;
} RegOffsetInfo;

struct vdpp_addr {
    RK_U32 y;
    RK_U32 cbcr;
    RK_U32 cbcr_offset;
};

struct vdpp_params {
    RK_U32 src_yuv_swap;
    RK_U32 dst_fmt;
    RK_U32 dst_yuv_swap;
    RK_U32 src_width;
    RK_U32 src_height;
    RK_U32 src_vir_w;
    RK_U32 dst_width;
    RK_U32 dst_height;
    RK_U32 dst_vir_w;

    struct vdpp_addr src; // src frame
    struct vdpp_addr dst; // dst frame

    // DMSR params
    RK_U32 dmsr_enable;
    RK_U32 dmsr_str_pri_y;
    RK_U32 dmsr_str_sec_y;
    RK_U32 dmsr_dumping_y;
    RK_U32 dmsr_wgt_pri_gain_even_1;
    RK_U32 dmsr_wgt_pri_gain_even_2;
    RK_U32 dmsr_wgt_pri_gain_odd_1;
    RK_U32 dmsr_wgt_pri_gain_odd_2;
    RK_U32 dmsr_wgt_sec_gain;
    RK_U32 dmsr_blk_flat_th;
    RK_U32 dmsr_contrast_to_conf_map_x0;
    RK_U32 dmsr_contrast_to_conf_map_x1;
    RK_U32 dmsr_contrast_to_conf_map_y0;
    RK_U32 dmsr_contrast_to_conf_map_y1;
    RK_U32 dmsr_diff_core_th0;
    RK_U32 dmsr_diff_core_th1;
    RK_U32 dmsr_diff_core_wgt0;
    RK_U32 dmsr_diff_core_wgt1;
    RK_U32 dmsr_diff_core_wgt2;
    RK_U32 dmsr_edge_th_low_arr[7];
    RK_U32 dmsr_edge_th_high_arr[7];

    // ZME params
    RK_U32 zme_bypass_en;
    RK_U32 zme_dering_enable;
    RK_U32 zme_dering_sen_0;
    RK_U32 zme_dering_sen_1;
    RK_U32 zme_dering_blend_alpha;
    RK_U32 zme_dering_blend_beta;
    RK_S16 (*zme_tap8_coeff)[17][8];
    RK_S16 (*zme_tap6_coeff)[17][8];
};

struct vdpp_api_ctx {
    RK_S32 fd;
    struct vdpp_params params;
    struct vdpp_reg reg;
};

#endif

/*
 * Copyright (C) 2022 Rockchip Electronics Co. LTD
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

#undef  ROCKCHIP_LOG_TAG
#define ROCKCHIP_LOG_TAG    "C2RKMlvecLegacy"

#include <string.h>

#include "C2RKMlvecLegacy.h"
#include "C2RKLog.h"

C2RKMlvecLegacy::C2RKMlvecLegacy(MppCtx ctx, MppApi *mpi, MppEncCfg cfg) :
    mMppCtx(ctx),
    mMppMpi(mpi),
    mEncCfg(cfg) {
    /* default disable frame_qp setup */
    mDynamicCfg.frameQP = -1;
}

C2RKMlvecLegacy::~C2RKMlvecLegacy() {
}

bool C2RKMlvecLegacy::setupMaxTid(int32_t maxTid) {
    MppEncRefLtFrmCfg ltRef[16];
    MppEncRefStFrmCfg stRef[16];
    int32_t ltCfgCnt = 0;
    int32_t stCfgCnt = 0;
    int32_t tid0Loop = 0;
    int32_t numLtrFrms = mStaticCfg.ltrFrames;
    int ret;

    memset(ltRef, 0, sizeof(ltRef));
    memset(stRef, 0, sizeof(stRef));

    c2_info("max_tid %d numLtrFrms %d ", maxTid, numLtrFrms);

    switch (maxTid) {
    case 1: {
        stRef[0].is_non_ref    = 0;
        stRef[0].temporal_id   = 0;
        stRef[0].ref_mode      = REF_TO_PREV_REF_FRM;
        stRef[0].ref_arg       = 0;
        stRef[0].repeat        = 0;

        stCfgCnt = 1;
        tid0Loop = 1;
        c2_info("no tsvc");
    } break;
    case 2: {
        /* set tsvc2 st-ref struct */
        /* st 0 layer 0 - ref */
        stRef[0].is_non_ref    = 0;
        stRef[0].temporal_id   = 0;
        stRef[0].ref_mode      = REF_TO_PREV_REF_FRM;
        stRef[0].ref_arg       = 0;
        stRef[0].repeat        = 0;
        /* st 1 layer 1 - non-ref */
        stRef[1].is_non_ref    = 1;
        stRef[1].temporal_id   = 1;
        stRef[1].ref_mode      = REF_TO_PREV_REF_FRM;
        stRef[1].ref_arg       = 0;
        stRef[1].repeat        = 0;
        /* st 2 layer 0 - ref */
        stRef[2].is_non_ref    = 0;
        stRef[2].temporal_id   = 0;
        stRef[2].ref_mode      = REF_TO_PREV_REF_FRM;
        stRef[2].ref_arg       = 0;
        stRef[2].repeat        = 0;

        stCfgCnt = 3;
        tid0Loop = 2;
        c2_info("tsvc2");
    } break;
    case 3: {
        /* set tsvc3 st-ref struct */
        /* st 0 layer 0 - ref */
        stRef[0].is_non_ref    = 0;
        stRef[0].temporal_id   = 0;
        stRef[0].ref_mode      = REF_TO_TEMPORAL_LAYER;
        stRef[0].ref_arg       = 0;
        stRef[0].repeat        = 0;
        /* st 1 layer 2 - non-ref */
        stRef[1].is_non_ref    = 0;
        stRef[1].temporal_id   = 2;
        stRef[1].ref_mode      = REF_TO_TEMPORAL_LAYER;
        stRef[1].ref_arg       = 0;
        stRef[1].repeat        = 0;
        /* st 2 layer 1 - ref */
        stRef[2].is_non_ref    = 0;
        stRef[2].temporal_id   = 1;
        stRef[2].ref_mode      = REF_TO_TEMPORAL_LAYER;
        stRef[2].ref_arg       = 0;
        stRef[2].repeat        = 0;
        /* st 3 layer 2 - non-ref */
        stRef[3].is_non_ref    = 0;
        stRef[3].temporal_id   = 2;
        stRef[3].ref_mode      = REF_TO_TEMPORAL_LAYER;
        stRef[3].ref_arg       = 1;
        stRef[3].repeat        = 0;
        /* st 4 layer 0 - ref */
        stRef[4].is_non_ref    = 0;
        stRef[4].temporal_id   = 0;
        stRef[4].ref_mode      = REF_TO_TEMPORAL_LAYER;
        stRef[4].ref_arg       = 0;
        stRef[4].repeat        = 0;

        stCfgCnt = 5;
        tid0Loop = 4;
        c2_info("tsvc3");
    } break;
    case 4: {
        /* set tsvc3 st-ref struct */
        /* st 0 layer 0 - ref */
        stRef[0].is_non_ref    = 0;
        stRef[0].temporal_id   = 0;
        stRef[0].ref_mode      = REF_TO_TEMPORAL_LAYER;
        stRef[0].ref_arg       = 0;
        stRef[0].repeat        = 0;
        /* st 1 layer 3 - non-ref */
        stRef[1].is_non_ref    = 1;
        stRef[1].temporal_id   = 3;
        stRef[1].ref_mode      = REF_TO_PREV_REF_FRM;
        stRef[1].ref_arg       = 0;
        stRef[1].repeat        = 0;
        /* st 2 layer 2 - ref */
        stRef[2].is_non_ref    = 0;
        stRef[2].temporal_id   = 2;
        stRef[2].ref_mode      = REF_TO_PREV_REF_FRM;
        stRef[2].ref_arg       = 0;
        stRef[2].repeat        = 0;
        /* st 3 layer 3 - non-ref */
        stRef[3].is_non_ref    = 1;
        stRef[3].temporal_id   = 3;
        stRef[3].ref_mode      = REF_TO_PREV_REF_FRM;
        stRef[3].ref_arg       = 0;
        stRef[3].repeat        = 0;
        /* st 4 layer 1 - ref */
        stRef[4].is_non_ref    = 0;
        stRef[4].temporal_id   = 1;
        stRef[4].ref_mode      = REF_TO_TEMPORAL_LAYER;
        stRef[4].ref_arg       = 0;
        stRef[4].repeat        = 0;
        /* st 5 layer 3 - non-ref */
        stRef[5].is_non_ref    = 1;
        stRef[5].temporal_id   = 3;
        stRef[5].ref_mode      = REF_TO_PREV_REF_FRM;
        stRef[5].ref_arg       = 0;
        stRef[5].repeat        = 0;
        /* st 6 layer 2 - ref */
        stRef[6].is_non_ref    = 0;
        stRef[6].temporal_id   = 2;
        stRef[6].ref_mode      = REF_TO_PREV_REF_FRM;
        stRef[6].ref_arg       = 0;
        stRef[6].repeat        = 0;
        /* st 7 layer 3 - non-ref */
        stRef[7].is_non_ref    = 1;
        stRef[7].temporal_id   = 3;
        stRef[7].ref_mode      = REF_TO_PREV_REF_FRM;
        stRef[7].ref_arg       = 0;
        stRef[7].repeat        = 0;
        /* st 8 layer 0 - ref */
        stRef[8].is_non_ref    = 0;
        stRef[8].temporal_id   = 0;
        stRef[8].ref_mode      = REF_TO_PREV_REF_FRM;
        stRef[8].ref_arg       = 0;
        stRef[8].repeat        = 0;

        stCfgCnt = 9;
        tid0Loop = 8;
        c2_info("tsvc4");
    } break;
    default : {
        c2_err("invalid max temporal layer id %d", maxTid);
    } break;
    }

    if (numLtrFrms) {
        int32_t i;

        ltCfgCnt = numLtrFrms;
        for (i = 0; i < numLtrFrms; i++) {
            ltRef[i].lt_idx        = i;
            ltRef[i].temporal_id   = 0;
            ltRef[i].ref_mode      = REF_TO_PREV_LT_REF;
            ltRef[i].lt_gap        = 0;
            ltRef[i].lt_delay      = tid0Loop * i;
        }
    }

    c2_info("ltCfgCnt %d stCfgCnt %d", ltCfgCnt, stCfgCnt);
    if (ltCfgCnt || stCfgCnt) {
        MppEncRefCfg ref = nullptr;

        mpp_enc_ref_cfg_init(&ref);

        mpp_enc_ref_cfg_set_cfg_cnt(ref, ltCfgCnt, stCfgCnt);
        mpp_enc_ref_cfg_add_lt_cfg(ref, ltCfgCnt, ltRef);
        mpp_enc_ref_cfg_add_st_cfg(ref, stCfgCnt, stRef);
        mpp_enc_ref_cfg_set_keep_cpb(ref, 1);
        mpp_enc_ref_cfg_check(ref);

        ret = mMppMpi->control(mMppCtx, MPP_ENC_SET_REF_CFG, ref);
        if (ret) {
            c2_err("failed to set ref cfg, ret %d", ret);
            return false;
        }

        mpp_enc_ref_cfg_deinit(&ref);
    } else {
        ret = mMppMpi->control(mMppCtx, MPP_ENC_SET_REF_CFG, nullptr);
        if (ret) {
            c2_err("failed to set ref cfg, ret %d", ret);
            return false;
        }
    }

    return true;
}

bool C2RKMlvecLegacy::setupStaticConfig(MStaticCfg *cfg) {
    int32_t magic = cfg->magic;

    if ((((magic >> 24) & 0xff) != MLVEC_MAGIC) ||
        (((magic >> 16) & 0xff) != MLVEC_VERSION)) {
        c2_err("failed to check mlvec cfg magic %08x", magic);
        return false;
    }

    c2_info("add_prefix %d", cfg->addPrefix);
    mpp_enc_cfg_set_s32(mEncCfg, "h264:prefix_mode", cfg->addPrefix);

    c2_info("slice_mbs  %d", cfg->sliceMbs);
    if (cfg->sliceMbs) {
        mpp_enc_cfg_set_u32(mEncCfg, "split:mode", MPP_ENC_SPLIT_BY_CTU);
        mpp_enc_cfg_set_u32(mEncCfg, "split:arg", cfg->sliceMbs);
    } else {
        mpp_enc_cfg_set_u32(mEncCfg, "split:mode", MPP_ENC_SPLIT_NONE);
    }

    memcpy(&mStaticCfg, cfg, sizeof(mStaticCfg));

    /* NOTE: ltr_frames is already configured */
    setupMaxTid(cfg->maxTid);

    return true;
}

bool C2RKMlvecLegacy::setupDynamicConfig(MDynamicCfg *cfg, MppMeta meta) {
    MDynamicCfg *dst = &mDynamicCfg;

    /* clear non-sticky flag first */
    dst->markLtr = -1;
    dst->useLtr  = -1;
    /* frame qp and base layer pid is sticky flag */

    /* update flags */
    if (cfg->updated) {
        if (cfg->updated & MLVEC_ENC_MARK_LTR_UPDATED)
            dst->markLtr = cfg->markLtr;

        if (cfg->updated & MLVEC_ENC_USE_LTR_UPDATED) {
            int32_t i = 0, found = 0;
            for (i = 0; i < (MLVEC_MAX_LAYER_COUNT - 1); i++) {
                if (((cfg->useLtr >> i) & 1) == 1) {
                    found = 1;
                    break;
                }
            }
            dst->useLtr = (found == 1) ? i : 0;
        }

        if (cfg->updated & MLVEC_ENC_FRAME_QP_UPDATED)
            dst->frameQP = cfg->frameQP;

        if (cfg->updated & MLVEC_ENC_BASE_PID_UPDATED)
            dst->baseLayerPid = cfg->baseLayerPid;

        cfg->updated = 0;
    }

    c2_info("ltr mark %2d use %2d frm qp %2d blpid %d", dst->markLtr,
              dst->useLtr, dst->frameQP, dst->baseLayerPid);

    /* setup next frame configure */
    if (dst->markLtr >= 0)
        mpp_meta_set_s32(meta, KEY_ENC_MARK_LTR, dst->markLtr);

    if (dst->useLtr >= 0)
        mpp_meta_set_s32(meta, KEY_ENC_USE_LTR, dst->useLtr);

    if (dst->frameQP >= 0)
        mpp_meta_set_s32(meta, KEY_ENC_FRAME_QP, dst->frameQP);

    if (dst->baseLayerPid >= 0)
        mpp_meta_set_s32(meta, KEY_ENC_BASE_LAYER_PID, dst->baseLayerPid);

    return true;
}

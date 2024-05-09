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

#ifndef ANDROID_C2_RK_MLVEC_LEGACY_H__
#define ANDROID_C2_RK_MLVEC_LEGACY_H__

#include "mpp/rk_mpi.h"

#define  MLVEC_MAGIC                       'M'
#define  MLVEC_VERSION                     '0'

#define  MLVEC_ENC_MARK_LTR_UPDATED        (0x00000001)
#define  MLVEC_ENC_USE_LTR_UPDATED         (0x00000002)
#define  MLVEC_ENC_FRAME_QP_UPDATED        (0x00000004)
#define  MLVEC_ENC_BASE_PID_UPDATED        (0x00000008)

/* hardware driver version */
#define  MLVEC_DRIVER_VERSION               3588

/* the maximal number of support tsvc layer count */
#define  MLVEC_MAX_LAYER_COUNT              4

/* low-latency mode of decoder/encoder support */
#define  MLVEC_LOW_LATENCY_MODE_ENABLE      1

/* the maximal number of long-term frames supported by the encoder */
#define  MLVEC_MAX_LTR_FRAMES_COUNT         4

/* whether down scaling factors supported by the encoder */
#define  MLVEC_PRE_PROCESS_SCALE_SUPPORT    1

/* whether rotation supported by the encoder */
#define  MLVEC_PRE_PROCESS_ROTATION_SUPPORT 1

class C2RKMlvecLegacy {
public:
    C2RKMlvecLegacy(MppCtx ctx, MppApi *mpi, MppEncCfg cfg);
    ~C2RKMlvecLegacy();

    struct MStaticCfg {
        int32_t width;
        int32_t height;
        int32_t sarWidth;
        int32_t sarHeight;

        int32_t magic;
        /* static configure */
        int32_t maxTid      : 8;        /* max temporal layer id */
        int32_t ltrFrames   : 8;        /* max long-term reference frame count */
        int32_t addPrefix   : 8;        /* add prefix before each frame */
        int32_t sliceMbs    : 16;       /* macroblock row count for each slice */
        int32_t reserved    : 16;
    } MStaticCfg_t;

    struct MDynamicCfg {
        /* dynamic configure */
        int32_t updated;
        int32_t markLtr;
        int32_t useLtr;
        int32_t frameQP;
        int32_t baseLayerPid;
    } MDynamicCfg_t;

    bool setupMaxTid(int32_t maxTid);
    bool setupStaticConfig(MStaticCfg *cfg);
    bool setupDynamicConfig(MDynamicCfg *cfg, MppMeta meta);

private:
    MppCtx      mMppCtx;
    MppApi     *mMppMpi;
    MppEncCfg   mEncCfg;

    MStaticCfg  mStaticCfg;
    MDynamicCfg mDynamicCfg;
};

#endif  // ANDROID_C2_RK_MLVEC_LEGACY_H__

/*
 * Copyright 2021 Rockchip Electronics Co. LTD
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
 *
 * author: zj@rock-chips.com
 */

//#define OPEN_DEBUG 1
#define LOG_TAG "RKMppEncApi"
#include "Log.h"
#include "RKMppEncApi.h"

#include <media/stagefright/MediaCodecConstants.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>



RKMppEncApi::RKMppEncApi()
    : mMppCtx(nullptr),
      mMppMpi(nullptr),
      mEncCfg(nullptr),
      mCodingType(MPP_VIDEO_CodingAVC),
      mStarted(false),
      mSpsPpsHeaderReceived(false),
      mSawInputEOS(false),
      mOutputEOS(false),
      mSignalledError(false),
      mHorStride(0),
      mVerStride(0),
      mInFile(nullptr),
      mOutFile(nullptr) {
    Trace();
}

RKMppEncApi::~RKMppEncApi() {
    Trace();
    releaseEncoder();
}

bool RKMppEncApi::init(EncCfgInfo* cfg) {
    Trace();
    bool ret = true;
    int err = 0;
    MppPollType timeout = MPP_POLL_NON_BLOCK;
    //MppPollType timeoutOutput = MPP_POLL_BLOCK;
    RK_S64 outPutTimout =48;
    /* default stride */

    mCodingType = MPP_VIDEO_CodingAVC;
    mWidth = cfg->width;
    mHeight = cfg->height;
    mHorStride = _ALIGN(cfg->width, 16);
    mVerStride = _ALIGN(cfg->height, 16);

    mFormat = cfg->format;
    mIDRInterval = cfg->IDRInterval;
    mBitrateMode = cfg->bitrateMode;
    mBitRate = cfg->bitRate;
    mFrameRate = cfg->framerate;
    mQp = cfg->qp;
    mScaleWidth = cfg->scaleWidth;
    mSaleHeight = cfg->scaleHeight;
    mProfile = cfg->profile;
    mLevel = cfg->level;
    mRotation = cfg->rotation;

    /*
     * create vpumem for mpp input
     *
     * NOTE: We need temporary buffer to store rga nv12 output for some rgba
     * input, since mpp can't process rgba input properly. in addition to this,
     * alloc buffer within 4G in view of rga efficiency.
     */
    // create mpp and init mpp
    err = mpp_create(&mMppCtx, &mMppMpi);
    if (err) {
        ALOGE("failed to mpp_create, ret %d", err);
        ret = false;
        goto error;
    }
    err = mMppMpi->control(mMppCtx, MPP_SET_INPUT_TIMEOUT, &timeout);

    err = mMppMpi->control(mMppCtx, MPP_SET_OUTPUT_TIMEOUT, &outPutTimout);
    if (MPP_OK != err) {
        ALOGE("failed to set output timeout %d, ret %d", timeout, err);
        ret = false;
        goto error;
    }

    err = mpp_init(mMppCtx, MPP_CTX_ENC, mCodingType);
    if (err) {
        ALOGE("failed to mpp_init, ret %d", err);
        ret = false;
        goto error;
    }

    ret = setupEncCfg();
    if (ret == false) {
        ALOGE("failed to set config, ret=0x%x", ret);
        goto error;
    }

    mStarted = true;
    return true;
error:
    releaseEncoder();

    return ret;
}

bool RKMppEncApi::onInit() {
    Trace();
    return true;
}

bool RKMppEncApi::onStop() {
    Trace();
    return onFlush_sm();
}

bool RKMppEncApi::onReset() {
    Trace();
    return onStop();
}

bool RKMppEncApi::onRelease() {
    Trace();
    return true;
}

bool RKMppEncApi::onFlush_sm() {
    bool ret = true;

    Trace();

    return ret;
}

bool RKMppEncApi::sendFrame(MyDmaBuffer_t dBuffer, int32_t size, uint64_t pts,
                            uint32_t flags) {
    Trace();
    int err = 0;
    bool ret = true;
    MppFrame frame = nullptr;

    MppBufferInfo commit;
    memset(&commit, 0, sizeof(commit));
    commit.type = MPP_BUFFER_TYPE_ION;

    mpp_frame_init(&frame);

    if (flags & BUFFER_FLAG_END_OF_STREAM) {
        ALOGD("send input eos");
        mpp_frame_set_eos(frame, 1);
    }

    ALOGD("send frame fd %d size %d pts %lld", dBuffer.fd, dBuffer.size, pts);

    if (dBuffer.fd > 0) {
        MppBuffer buffer = nullptr;

        commit.fd = dBuffer.fd;
        commit.size = dBuffer.size;

        commit.index = dBuffer.index;
        err = mpp_buffer_import(&buffer, &commit);
        if (err) {
            ALOGE("failed to import input buffer");
            ret = false;
            goto error;
        }
        mpp_frame_set_buffer(frame, buffer);
        mpp_buffer_put(buffer);
        buffer = nullptr;
    } else {
        mpp_frame_set_buffer(frame, nullptr);
    }

    mpp_frame_set_width(frame, mWidth);
    mpp_frame_set_height(frame, mHeight);
    mpp_frame_set_hor_stride(frame, mHorStride);
    mpp_frame_set_ver_stride(frame, mVerStride);
    mpp_frame_set_pts(frame, pts);
    mpp_frame_set_fmt(frame, MPP_FMT_YUV420SP);

    err = mMppMpi->encode_put_frame(mMppCtx, frame);
    if (err) {
        ALOGE("failed to put_frame, err %d", err);
        ret = false;
        goto error;
    }

    ret = true;
    return ret;
error:
    if (frame) {
        mpp_frame_deinit(&frame);
    }

    return ret;
}

bool RKMppEncApi::getoutpacket(OutWorkEntry* entry) {
    Trace();
    int err = 0;
    MppPacket packet = nullptr;

    err = mMppMpi->encode_get_packet(mMppCtx, &packet);
    if (err) {
        return false;
    } else {
        int64_t pts = mpp_packet_get_pts(packet);
        size_t len = mpp_packet_get_length(packet);
        uint32_t eos = mpp_packet_get_eos(packet);

        ALOGD("get outpacket pts %lld size %d eos %d", pts, len, eos);

        if (eos) {
            ALOGD("get output eos");
            mOutputEOS = true;
            if (pts == 0 || !len) {
                ALOGD("eos with empty pkt");
                return false;
            }
        }

        if (!len) {
            ALOGD("ignore empty output with pts %lld", pts);
            return false;
        }

        entry->frameIndex = pts;
        entry->outPacket = packet;
        if (mpp_packet_has_meta(packet)) {
            MppMeta meta = mpp_packet_get_meta(packet);
            MppFrame frm = NULL;
            RK_S32 temporal_id = 0;
            RK_S32 lt_idx = -1;
            RK_S32 avg_qp = -1;

            if (MPP_OK ==
                mpp_meta_get_s32(meta, KEY_TEMPORAL_ID, &temporal_id)) {
            }

            if (MPP_OK == mpp_meta_get_s32(meta, KEY_LONG_REF_IDX, &lt_idx)) {
            }

            if (MPP_OK == mpp_meta_get_s32(meta, KEY_ENC_AVERAGE_QP, &avg_qp)) {
            }

            if (MPP_OK == mpp_meta_get_frame(meta, KEY_INPUT_FRAME, &frm)) {
                MppBuffer frm_buf = NULL;

                if (NULL == frm) {
                    ALOGE("mpp_meta_get_frame failed");
                }
                frm_buf = mpp_frame_get_buffer(frm);
                if (NULL == frm_buf) {
                    ALOGE("mpp_frame_get_buffer failed");
                }

                entry->index = mpp_buffer_get_index(frm_buf);
                ALOGE("mpp_buffer_get_index %d",  entry->index);
                {
                    // AutoMutex autolock(list_buf->mutex());
                    // list_buf->add_at_tail(&frm_buf, sizeof(frm_buf));
                    // list_buf->signal();
                }

                mpp_frame_deinit(&frm);
            }
        }
        return true;
    }
    return true;
}

bool RKMppEncApi::sendFrame(char* data, int32_t size, int64_t pts,
                            int32_t flag) {
    Trace();
    return true;
}

bool RKMppEncApi::sendFrame(int32_t fd, int32_t size, int64_t pts,
                            int32_t flag) {
    Trace();
    return true;
}

bool RKMppEncApi::getOutStream(EncoderOut_t* encOut) {
    Trace();
    return true;
}

bool RKMppEncApi::setupBaseCodec() {
    // ALOGD("setupBaseCodec: coding %d w %d h %d hor %d ver %d",
    //       mCodingType, width, height, mHorStride, mVerStride);

    mpp_enc_cfg_set_s32(mEncCfg, "codec:type", mCodingType);

    mpp_enc_cfg_set_s32(mEncCfg, "prep:width", mWidth);
    mpp_enc_cfg_set_s32(mEncCfg, "prep:height", mHeight);
    mpp_enc_cfg_set_s32(mEncCfg, "prep:hor_stride", mHorStride);
    mpp_enc_cfg_set_s32(mEncCfg, "prep:ver_stride", mVerStride);
    mpp_enc_cfg_set_s32(mEncCfg, "prep:format", mFormat);
    mpp_enc_cfg_set_s32(mEncCfg, "prep:rotation", mRotation);

    return true;
}

bool RKMppEncApi::setupSceneMode() {
    // ALOGD("setupSceneMode: scene-mode %d", c2Mode->value);

    /*
     * scene-mode of encoder, this feature only support on rk3588
     *   - 0: deault none ipc mode
     *   - 1: ipc mode
     */
    // mpp_enc_cfg_set_s32(mEncCfg, "tune:scene_mode", c2Mode->value);

    return true;
}

bool RKMppEncApi::setupFrameRate() {
    float frameRate = mFrameRate;
    uint32_t idrInterval = mIDRInterval, gop = mFrameRate * mIDRInterval;

    // std::shared_ptr<C2StreamGopTuning::output> c2Gop = mIntf->getGop_l();
    // std::shared_ptr<C2StreamFrameRateInfo::output> c2FrameRate
    //         = mIntf->getFrameRate_l();

    // idrInterval = mIntf->getSyncFramePeriod_l();
    // frameRate = c2FrameRate->value;

    if (frameRate == 1) {
        // set default frameRate 30
        frameRate = 60;
    }

    // if (c2Gop && c2Gop->flexCount() > 0) {
    //     uint32_t syncInterval = 30;
    //     uint32_t iInterval = 0;
    //     uint32_t maxBframes = 0;

    //     ParseGop(*c2Gop, &syncInterval, &iInterval, &maxBframes);
    //     if (syncInterval > 0) {
    //         ALOGD("updating IDR interval: %d -> %d", idrInterval,
    //         syncInterval); idrInterval = syncInterval;
    //     }
    // }

    ALOGD("setupFrameRate: framerate %.2f idrInterval %d gop %d", frameRate,
          idrInterval, gop);

    gop = (idrInterval < 8640000 && idrInterval > 1)
              ? idrInterval
              : mFrameRate * mIDRInterval;
    mpp_enc_cfg_set_s32(mEncCfg, "rc:gop", gop);

    /* fix input / output frame rate */
    mpp_enc_cfg_set_s32(mEncCfg, "rc:fps_in_flex", 0);
    mpp_enc_cfg_set_s32(mEncCfg, "rc:fps_in_num", frameRate);
    mpp_enc_cfg_set_s32(mEncCfg, "rc:fps_in_denorm", 1);
    mpp_enc_cfg_set_s32(mEncCfg, "rc:fps_out_flex", 0);
    mpp_enc_cfg_set_s32(mEncCfg, "rc:fps_out_num", frameRate);
    mpp_enc_cfg_set_s32(mEncCfg, "rc:fps_out_denorm", 1);

    return true;
}

bool RKMppEncApi::setupBitRate() {
    uint32_t bitrate = mBitRate;
    uint32_t bitrateMode = mBitrateMode;
    // IntfImpl::Lock lock = mIntf->lock();

    // mBitrate = mIntf->getBitrate_l();
    // mBitrateMode = mIntf->getBitrateMode_l();

    // bitrate = mBitrate->value;
    // bitrateMode = mBitrateMode->value;

    ALOGD("setupBitRate: mode %d bitrate %d", bitrateMode, bitrate);

    mpp_enc_cfg_set_s32(mEncCfg, "rc:bps_target", bitrate);
    switch (bitrateMode) {
        case BITRATE_CONST_SKIP_ALLOWED:
        case BITRATE_CONST: {
            /* CBR mode has narrow bound */
            mpp_enc_cfg_set_s32(mEncCfg, "rc:mode", MPP_ENC_RC_MODE_CBR);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:bps_max", bitrate * 17 / 16);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:bps_min", bitrate * 15 / 16);
        } break;
        case BITRATE_IGNORE:
        case BITRATE_VARIABLE_SKIP_ALLOWED:
        case BITRATE_VARIABLE: {
            /* VBR mode has wide bound */
            mpp_enc_cfg_set_s32(mEncCfg, "rc:mode", MPP_ENC_RC_MODE_VBR);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:bps_max", bitrate * 17 / 16);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:bps_min", bitrate * 1 / 16);
        } break;
        default: {
            /* default use CBR mode */
            mpp_enc_cfg_set_s32(mEncCfg, "rc:mode", MPP_ENC_RC_MODE_CBR);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:bps_max", bitrate * 17 / 16);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:bps_min", bitrate * 15 / 16);
        } break;
    }

    return true;
}

bool RKMppEncApi::setupProfileParams() {
    int32_t profile, level;

    // IntfImpl::Lock lock = mIntf->lock();

    // profile = mIntf->getProfile_l(mCodingType);
    // level = mIntf->getLevel_l(mCodingType);
    profile = mProfile;
    level = mLevel;
    ALOGD("setupProfileParams: profile %d level %d", profile, level);

    switch (mCodingType) {
        case MPP_VIDEO_CodingAVC: {
            mpp_enc_cfg_set_s32(mEncCfg, "h264:profile", profile);
            mpp_enc_cfg_set_s32(mEncCfg, "h264:level", level);
            if (profile >= H264_PROFILE_HIGH) {
                mpp_enc_cfg_set_s32(mEncCfg, "h264:cabac_en", 1);
                mpp_enc_cfg_set_s32(mEncCfg, "h264:cabac_idc", 0);
                mpp_enc_cfg_set_s32(mEncCfg, "h264:trans8x8", 1);
            }
        } break;
        case MPP_VIDEO_CodingHEVC: {
            mpp_enc_cfg_set_s32(mEncCfg, "h265:profile", profile);
            mpp_enc_cfg_set_s32(mEncCfg, "h265:level", level);
        } break;
        default: {
            ALOGE("setupProfileParams: unsupport coding type %d", mCodingType);
        } break;
    }

    return true;
}

bool RKMppEncApi::setupQp() {
    int32_t defaultIMin = 0, defaultIMax = 0;
    int32_t defaultPMin = 0, defaultPMax = 0;
    int32_t qpInit = 0;

    if (mCodingType == MPP_VIDEO_CodingVP8) {
        defaultIMin = defaultPMin = 0;
        defaultIMax = defaultPMax = 127;
        qpInit = 40;
    } else {
        /* the quality of h264/265 range from 10~51 */
        defaultIMin = defaultPMin = 10;
        defaultIMax = 51;
        // TODO: CTS testEncoderQualityAVCCBR 49
        defaultPMax = 49;
        qpInit = 26;
    }
    // qpInit = mQp;
    int32_t iMin = defaultIMin, iMax = defaultIMax;
    int32_t pMin = defaultPMin, pMax = defaultPMax;

    // // IntfImpl::Lock lock = mIntf->lock();

    // // std::shared_ptr<C2StreamPictureQuantizationTuning::output> qp =
    // //         mIntf->getPictureQuantization_l();

    // for (size_t i = 0; i < qp->flexCount(); ++i) {
    //     const C2PictureQuantizationStruct &layer = qp->m.values[i];

    //     if (layer.type_ == C2Config::picture_type_t(I_FRAME)) {
    //         iMax = layer.max;
    //         iMin = layer.min;
    //         ALOGD("PictureQuanlitySetter: iMin %d iMax %d", iMin, iMax);
    //     } else if (layer.type_ == C2Config::picture_type_t(P_FRAME)) {
    //         pMax = layer.max;
    //         pMin = layer.min;
    //         ALOGD("PictureQuanlitySetter: pMin %d pMax %d", pMin, pMax);
    //     }
    // }

    // iMax = std::clamp(iMax, defaultIMin, defaultIMax);
    // iMin = std::clamp(iMin, defaultIMin, defaultIMax);
    // pMax = std::clamp(pMax, defaultPMin, defaultPMax);
    // pMin = std::clamp(pMin, defaultPMin, defaultPMax);

    if (qpInit > iMax || qpInit < iMin) {
        qpInit = iMin;
    }

    ALOGD("setupQp: qpInit %d i %d-%d p %d-%d", qpInit, iMin, iMax, pMin, pMax);

    switch (mCodingType) {
        case MPP_VIDEO_CodingAVC:
        case MPP_VIDEO_CodingHEVC: {
            /*
             * disable mb_rc for vepu, this cfg does not apply to rkvenc.
             * since the vepu has pool performance, mb_rc will cause mosaic.
             */
            mpp_enc_cfg_set_s32(mEncCfg, "hw:mb_rc_disable", 1);

            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_min", pMin);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_max", pMax);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_min_i", iMin);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_max_i", iMax);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_init", qpInit);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_ip", 2);
        } break;
        case MPP_VIDEO_CodingVP8: {
            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_min", pMin);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_max", pMax);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_min_i", iMin);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_max_i", iMax);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_init", qpInit);
            mpp_enc_cfg_set_s32(mEncCfg, "rc:qp_ip", 6);
        } break;
        default: {
            ALOGE("setupQp: unsupport coding type %d", mCodingType);
            break;
        }
    }

    return true;
}

bool RKMppEncApi::setupVuiParams() {
    // ColorAspects sfAspects;
    // int32_t primaries, transfer, matrixCoeffs;
    // bool range;

    // IntfImpl::Lock lock = mIntf->lock();

    // std::shared_ptr<C2StreamColorAspectsInfo::output> colorAspects
    //         = mIntf->getCodedColorAspects_l();

    // if (!C2Mapper::map(colorAspects->primaries, &sfAspects.mPrimaries)) {
    //     sfAspects.mPrimaries = android::ColorAspects::PrimariesUnspecified;
    // }
    // if (!C2Mapper::map(colorAspects->range, &sfAspects.mRange)) {
    //     sfAspects.mRange = android::ColorAspects::RangeUnspecified;
    // }
    // if (!C2Mapper::map(colorAspects->matrix, &sfAspects.mMatrixCoeffs)) {
    //     sfAspects.mMatrixCoeffs = android::ColorAspects::MatrixUnspecified;
    // }
    // if (!C2Mapper::map(colorAspects->transfer, &sfAspects.mTransfer)) {
    //     sfAspects.mTransfer = android::ColorAspects::TransferUnspecified;
    // }

    // ColorUtils::convertCodecColorAspectsToIsoAspects(
    //         sfAspects, &primaries, &transfer,
    //         &matrixCoeffs, &range);

    // if (mEncCfg != NULL) {
    //     mpp_enc_cfg_set_s32(mEncCfg, "prep:range", range ? 2 : 0);
    //     mpp_enc_cfg_set_s32(mEncCfg, "prep:colorprim", primaries);
    //     mpp_enc_cfg_set_s32(mEncCfg, "prep:colortrc", transfer);
    //     mpp_enc_cfg_set_s32(mEncCfg, "prep:colorspace", matrixCoeffs);
    // }

    return true;
}

bool RKMppEncApi::setupTemporalLayers() {
    // size_t temporalLayers = 0;

    // IntfImpl::Lock lock = mIntf->lock();

    // std::shared_ptr<C2StreamTemporalLayeringTuning::output> layering =
    //         mIntf->getTemporalLayers_l();

    // temporalLayers = layering->m.layerCount;
    // if (temporalLayers == 0) {
    //     return true;
    // }

    // if (temporalLayers < 2 || temporalLayers > 4) {
    //     ALOGD("only support tsvc layer 2 ~ 4(%zu); ignored.",
    //     temporalLayers); return true;
    // }

    // /*
    //  * NOTE:
    //  * 1. not support set bLayerCount and bitrateRatios yet.
    //  *    - layering->m.bLayerCount
    //  *    - layering->m.bitrateRatios
    //  * 2. only support tsvc layer 2 ~ 4.
    //  */

    // int ret = 0;
    // MppEncRefCfg ref;
    // MppEncRefLtFrmCfg ltRef[4];
    // MppEncRefStFrmCfg stRef[16];
    // RK_S32 ltCnt = 0;
    // RK_S32 stCnt = 0;

    // memset(&ltRef, 0, sizeof(ltRef));
    // memset(&stRef, 0, sizeof(stRef));

    // mpp_enc_ref_cfg_init(&ref);

    // ALOGD("setupTemporalLayers: layers %zu", temporalLayers);

    // switch (temporalLayers) {
    // case 4: {
    //     // tsvc4
    //     //      /-> P1      /-> P3        /-> P5      /-> P7
    //     //     /           /             /           /
    //     //    //--------> P2            //--------> P6
    //     //   //                        //
    //     //  ///---------------------> P4
    //     // ///
    //     // P0 ------------------------------------------------> P8
    //     ltCnt = 1;

    //     /* set 8 frame lt-ref gap */
    //     ltRef[0].lt_idx        = 0;
    //     ltRef[0].temporal_id   = 0;
    //     ltRef[0].ref_mode      = REF_TO_PREV_LT_REF;
    //     ltRef[0].lt_gap        = 8;
    //     ltRef[0].lt_delay      = 0;

    //     stCnt = 9;
    //     /* set tsvc4 st-ref struct */
    //     /* st 0 layer 0 - ref */
    //     stRef[0].is_non_ref    = 0;
    //     stRef[0].temporal_id   = 0;
    //     stRef[0].ref_mode      = REF_TO_TEMPORAL_LAYER;
    //     stRef[0].ref_arg       = 0;
    //     stRef[0].repeat        = 0;
    //     /* st 1 layer 3 - non-ref */
    //     stRef[1].is_non_ref    = 1;
    //     stRef[1].temporal_id   = 3;
    //     stRef[1].ref_mode      = REF_TO_PREV_REF_FRM;
    //     stRef[1].ref_arg       = 0;
    //     stRef[1].repeat        = 0;
    //     /* st 2 layer 2 - ref */
    //     stRef[2].is_non_ref    = 0;
    //     stRef[2].temporal_id   = 2;
    //     stRef[2].ref_mode      = REF_TO_PREV_REF_FRM;
    //     stRef[2].ref_arg       = 0;
    //     stRef[2].repeat        = 0;
    //     /* st 3 layer 3 - non-ref */
    //     stRef[3].is_non_ref    = 1;
    //     stRef[3].temporal_id   = 3;
    //     stRef[3].ref_mode      = REF_TO_PREV_REF_FRM;
    //     stRef[3].ref_arg       = 0;
    //     stRef[3].repeat        = 0;
    //     /* st 4 layer 1 - ref */
    //     stRef[4].is_non_ref    = 0;
    //     stRef[4].temporal_id   = 1;
    //     stRef[4].ref_mode      = REF_TO_PREV_LT_REF;
    //     stRef[4].ref_arg       = 0;
    //     stRef[4].repeat        = 0;
    //     /* st 5 layer 3 - non-ref */
    //     stRef[5].is_non_ref    = 1;
    //     stRef[5].temporal_id   = 3;
    //     stRef[5].ref_mode      = REF_TO_PREV_REF_FRM;
    //     stRef[5].ref_arg       = 0;
    //     stRef[5].repeat        = 0;
    //     /* st 6 layer 2 - ref */
    //     stRef[6].is_non_ref    = 0;
    //     stRef[6].temporal_id   = 2;
    //     stRef[6].ref_mode      = REF_TO_PREV_REF_FRM;
    //     stRef[6].ref_arg       = 0;
    //     stRef[6].repeat        = 0;
    //     /* st 7 layer 3 - non-ref */
    //     stRef[7].is_non_ref    = 1;
    //     stRef[7].temporal_id   = 3;
    //     stRef[7].ref_mode      = REF_TO_PREV_REF_FRM;
    //     stRef[7].ref_arg       = 0;
    //     stRef[7].repeat        = 0;
    //     /* st 8 layer 0 - ref */
    //     stRef[8].is_non_ref    = 0;
    //     stRef[8].temporal_id   = 0;
    //     stRef[8].ref_mode      = REF_TO_TEMPORAL_LAYER;
    //     stRef[8].ref_arg       = 0;
    //     stRef[8].repeat        = 0;
    // } break;
    // case 3: {
    //     // tsvc3
    //     //     /-> P1      /-> P3
    //     //    /           /
    //     //   //--------> P2
    //     //  //
    //     // P0/---------------------> P4
    //     ltCnt = 0;

    //     stCnt = 5;
    //     /* set tsvc4 st-ref struct */
    //     /* st 0 layer 0 - ref */
    //     stRef[0].is_non_ref    = 0;
    //     stRef[0].temporal_id   = 0;
    //     stRef[0].ref_mode      = REF_TO_TEMPORAL_LAYER;
    //     stRef[0].ref_arg       = 0;
    //     stRef[0].repeat        = 0;
    //     /* st 1 layer 2 - non-ref */
    //     stRef[1].is_non_ref    = 1;
    //     stRef[1].temporal_id   = 2;
    //     stRef[1].ref_mode      = REF_TO_PREV_REF_FRM;
    //     stRef[1].ref_arg       = 0;
    //     stRef[1].repeat        = 0;
    //     /* st 2 layer 1 - ref */
    //     stRef[2].is_non_ref    = 0;
    //     stRef[2].temporal_id   = 1;
    //     stRef[2].ref_mode      = REF_TO_PREV_REF_FRM;
    //     stRef[2].ref_arg       = 0;
    //     stRef[2].repeat        = 0;
    //     /* st 3 layer 2 - non-ref */
    //     stRef[3].is_non_ref    = 1;
    //     stRef[3].temporal_id   = 2;
    //     stRef[3].ref_mode      = REF_TO_PREV_REF_FRM;
    //     stRef[3].ref_arg       = 0;
    //     stRef[3].repeat        = 0;
    //     /* st 4 layer 0 - ref */
    //     stRef[4].is_non_ref    = 0;
    //     stRef[4].temporal_id   = 0;
    //     stRef[4].ref_mode      = REF_TO_TEMPORAL_LAYER;
    //     stRef[4].ref_arg       = 0;
    //     stRef[4].repeat        = 0;
    // } break;
    // case 2: {
    //     // tsvc2
    //     //   /-> P1
    //     //  /
    //     // P0--------> P2
    //     ltCnt = 0;

    //     stCnt = 3;
    //     /* set tsvc4 st-ref struct */
    //     /* st 0 layer 0 - ref */
    //     stRef[0].is_non_ref    = 0;
    //     stRef[0].temporal_id   = 0;
    //     stRef[0].ref_mode      = REF_TO_TEMPORAL_LAYER;
    //     stRef[0].ref_arg       = 0;
    //     stRef[0].repeat        = 0;
    //     /* st 1 layer 2 - non-ref */
    //     stRef[1].is_non_ref    = 1;
    //     stRef[1].temporal_id   = 1;
    //     stRef[1].ref_mode      = REF_TO_PREV_REF_FRM;
    //     stRef[1].ref_arg       = 0;
    //     stRef[1].repeat        = 0;
    //     /* st 2 layer 1 - ref */
    //     stRef[2].is_non_ref    = 0;
    //     stRef[2].temporal_id   = 0;
    //     stRef[2].ref_mode      = REF_TO_PREV_REF_FRM;
    //     stRef[2].ref_arg       = 0;
    //     stRef[2].repeat        = 0;
    // } break;
    // default : {
    // } break;
    // }

    // if (ltCnt || stCnt) {
    //     mpp_enc_ref_cfg_set_cfg_cnt(ref, ltCnt, stCnt);

    //     if (ltCnt)
    //         mpp_enc_ref_cfg_add_lt_cfg(ref, ltCnt, ltRef);

    //     if (stCnt)
    //         mpp_enc_ref_cfg_add_st_cfg(ref, stCnt, stRef);

    //     /* check and get dpb size */
    //     mpp_enc_ref_cfg_check(ref);
    // }

    // ret = mMppMpi->control(mMppCtx, MPP_ENC_SET_REF_CFG, ref);
    // if (ret) {
    //     ALOGE("setupTemporalLayers: failed to set ref cfg ret %d", ret);
    //     return C2_CORRUPTED;
    // }

    return true;
}

bool RKMppEncApi::setupEncCfg() {
    bool ret = true;
    int err = 0;

    err = mpp_enc_cfg_init(&mEncCfg);
    if (err) {
        ALOGE("failed to get enc_cfg, ret %d", err);
        return false;
    }
    /* Video control Set Base Codec */
    setupBaseCodec();

    /* Video control Set Scene Mode */
    setupSceneMode();

    /* Video control Set FrameRates and gop */
    setupFrameRate();

    /* Video control Set Bitrate */
    setupBitRate();

    /* Video control Set Profile params */
    setupProfileParams();

    /* Video control Set QP */
    setupQp();

    /* Video control Set VUI params */
    setupVuiParams();

    /* Video control Set Temporal Layers */
    setupTemporalLayers();

    err = mMppMpi->control(mMppCtx, MPP_ENC_SET_CFG, mEncCfg);
    if (err) {
        ALOGE("failed to setup codec cfg, ret %d", err);
        ret = false;
    } else {
        /* optional */
        MppEncSeiMode seiMode = MPP_ENC_SEI_MODE_ONE_FRAME;
        err = mMppMpi->control(mMppCtx, MPP_ENC_SET_SEI_CFG, &seiMode);
        if (err) {
            ALOGE("failed to setup sei cfg, ret %d", err);
            ret = false;
        }
    }

    return ret;
}

bool RKMppEncApi::initEncoder() { return true; }

bool RKMppEncApi::releaseEncoder() {
    mStarted = false;
    mSpsPpsHeaderReceived = false;
    mSawInputEOS = false;
    mOutputEOS = false;
    mSignalledError = false;

    if (mEncCfg) {
        mpp_enc_cfg_deinit(mEncCfg);
        mEncCfg = nullptr;
    }

    if (mMppCtx) {
        mpp_destroy(mMppCtx);
        mMppCtx = nullptr;
    }

    if (mInFile != nullptr) {
        fclose(mInFile);
        mInFile = nullptr;
    }

    if (mOutFile != nullptr) {
        fclose(mOutFile);
        mOutFile = nullptr;
    }
    return true;
}

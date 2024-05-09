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

#ifndef __RKVPU_ENC_API_H__
#define __RKVPU_ENC_API_H__

#include <stdio.h>
#include "vpu_api.h"
#include "rk_mpi.h"
#include <linux/videodev2.h>
#include <hardware/hardware.h>
#include <hardware/gralloc.h>
#include "h264_syntax.h"
#include "h265_syntax.h"


#define BUFFERFLAG_EOS 0x00000001
#define _ALIGN(x, a) (((x) + (a)-1) & ~((a)-1))

typedef enum {
    UNSUPPORT_PROFILE = -1,
    BASELINE_PROFILE = 66,
    MAIN_PROFILE = 77,
    HIGHT_PROFILE = 100,
} EncProfile;

/*
 * This enumeration is for levels. The value follows the level_idc in sequence
 * parameter set rbsp. See Annex A.
 * @published All
 */
typedef enum AVCLevel {
    AVC_LEVEL_AUTO = 0,
    AVC_LEVEL1_B = 9,
    AVC_LEVEL1 = 10,
    AVC_LEVEL1_1 = 11,
    AVC_LEVEL1_2 = 12,
    AVC_LEVEL1_3 = 13,
    AVC_LEVEL2 = 20,
    AVC_LEVEL2_1 = 21,
    AVC_LEVEL2_2 = 22,
    AVC_LEVEL3 = 30,
    AVC_LEVEL3_1 = 31,
    AVC_LEVEL3_2 = 32,
    AVC_LEVEL4 = 40,
    AVC_LEVEL4_1 = 41,
    AVC_LEVEL4_2 = 42,
    AVC_LEVEL5 = 50,
    AVC_LEVEL5_1 = 51
} AVCLevel;

/**
 * Bitrate mode.
 *
 * TODO: refine this with bitrate ranges and suggested window
 */
typedef enum {
    BITRATE_CONST_SKIP_ALLOWED = 0,      ///< constant bitrate, frame skipping allowed
    BITRATE_CONST = 1,                   ///< constant bitrate, keep all frames
    BITRATE_VARIABLE_SKIP_ALLOWED = 2,   ///< bitrate can vary, frame skipping allowed
    BITRATE_VARIABLE = 3,                ///< bitrate can vary, keep all frames
    BITRATE_IGNORE = 7,                  ///< bitrate can be exceeded at will to achieve
    ///< quality or other settings

    // bitrate modes are composed of the following flags
    BITRATE_FLAG_KEEP_ALL_FRAMES = 1,
    BITRATE_FLAG_CAN_VARY = 2,
    BITRATE_FLAG_CAN_EXCEED = 4,
} BITRATE_MODE;

class RKMppEncApi {
public:
    RKMppEncApi();
    ~RKMppEncApi();

    typedef struct EncCfgInfo {
        int32_t width;
        int32_t height;
        int32_t horStride;
        int32_t verStride;
        int32_t format; /* input yuv format */
        int32_t IDRInterval;
        int32_t bitrateMode;   /* 0 - VBR mode; 1 - CBR mode; 2 - FIXQP mode */
        int32_t bitRate;   /* target bitrate */
        int32_t framerate; /* target framerate */
        int32_t qp;        /* coding quality, from 1~51 */
        int32_t scaleWidth;
        int32_t scaleHeight;
        int32_t profile;
        int32_t level;
        int32_t rotation;
    } EncCfgInfo_t;

    typedef struct {
        int32_t  fd;
        int32_t  size;
        void    *handler; /* buffer_handle_t */
        int index;
    } MyDmaBuffer_t;

    typedef struct {
        MppPacket outPacket;
        uint64_t  frameIndex;
        int fd;
        int index;
    } OutWorkEntry;

    bool init(EncCfgInfo* cfg);


    bool onInit();
    bool onStop();
    bool onReset();
    bool onRelease();
    bool onFlush_sm();

    bool getoutpacket(OutWorkEntry *entry);
    // send video frame to encoder only, async interface

    bool sendFrame(MyDmaBuffer_t dBuffer, int32_t size, uint64_t pts, uint32_t flags);

    bool sendFrame(char* data, int32_t size, int64_t pts, int32_t flag);

    bool sendFrame(int32_t fd, int32_t size, int64_t pts, int32_t flag);
    // get encoded video packet from encoder only, async interface
    bool getOutStream(EncoderOut_t* encOut);

    /* MPI interface parameters */
    MppCtx         mMppCtx;
    MppApi        *mMppMpi;
    MppEncCfg      mEncCfg;
    MppCodingType  mCodingType;

    bool           mStarted;
    bool           mSpsPpsHeaderReceived;
    bool           mSawInputEOS;
    bool           mOutputEOS;
    bool           mSignalledError;
    int32_t        mWidth;
    int32_t        mHeight;
    int32_t        mHorStride;
    int32_t        mVerStride;
    int32_t        mFormat;
    int32_t        mIDRInterval;
    int32_t        mBitrateMode;
    int32_t        mBitRate;
    int32_t        mFrameRate;
    int32_t        mQp;
    int32_t        mScaleWidth;
    int32_t        mSaleHeight;
    int32_t        mProfile;
    int32_t        mLevel;
    int32_t        mRotation;

    /*dump file*/
    FILE           *mInFile;
    FILE           *mOutFile;

private:

    bool setupBaseCodec();
    bool setupSceneMode();
    bool setupFrameRate();
    bool setupBitRate();
    bool setupProfileParams();
    bool setupQp();
    bool setupVuiParams();
    bool setupTemporalLayers();
    bool setupEncCfg();

    bool initEncoder();
    bool releaseEncoder();
};

#endif  // __RKVPU_ENC_API_H__

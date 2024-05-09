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
 *
 *
 */

#ifndef _ROCKIT_EXT_ADEC_H_
#define _ROCKIT_EXT_ADEC_H_

#include "RTCodecProfiles.h"

namespace android {

typedef enum rkAUDIO_BIT_WIDTH_E {
    AUDIO_BIT_WIDTH_8   = 0,   /* 8bit width */
    AUDIO_BIT_WIDTH_16  = 1,   /* 16bit width */
    AUDIO_BIT_WIDTH_24  = 2,   /* 24bit width */
    AUDIO_BIT_WIDTH_32  = 3,   /* 32bit width */
    AUDIO_BIT_WIDTH_FLT = 4,   /* float, 32bit width */
    AUDIO_BIT_WIDTH_BUTT,
} AUDIO_BIT_WIDTH_E;

/* result of register ADEC */
typedef enum rkADEC_DECODER_RESULT {
    ADEC_DECODER_OK = RT_OK,
    ADEC_DECODER_TRY_AGAIN,
    ADEC_DECODER_ERROR,
    ADEC_DECODER_EOS,
} ADEC_DECODER_RESULT;

typedef struct rkADEC_FRAME_INFO_S {
    uint32_t         u32SampleRate;
    uint32_t         u32Channels;
    uint32_t         u32FrameSize;
    uint64_t         u64ChnLayout;
    AUDIO_BIT_WIDTH_E enBitWidth;
    uint32_t         resv[2];
} ADEC_FRAME_INFO_S;

typedef struct rkADEC_ATTR_CODEC_S {
    int32_t    enType;                // see RTCodecID
    uint32_t   u32Channels;
    uint32_t   u32SampleRate;
    uint32_t   u32Bitrate;

    void       *pExtraData;
    uint32_t   u32ExtraDataSize;

    uint32_t   u32Resv[4];           // resv for user
    void       *pstResv;             // resv for user
} ADEC_ATTR_CODEC_S;

typedef struct rkAUDIO_ADENC_PARAM_S {
    uint8_t    *pu8InBuf;
    uint32_t    u32InLen;
    uint64_t    u64InTimeStamp;

    uint8_t    *pu8OutBuf;
    uint32_t    u32OutLen;
    uint64_t    u64OutTimeStamp;
} AUDIO_ADENC_PARAM_S;

typedef struct _RTAdecDecoder {
    int32_t  enType;                 // see RTCodecID
    char aszName[17];
    RTCodecProfiles *profiles;       // profiles this decoder support, see RTCodecProfiles.h
    // open decoder
    int32_t (*pfnOpenDecoder)(void *pDecoderAttr, void **ppDecoder);
    int32_t (*pfnDecodeFrm)(void *pDecoder, void *pParam);
    // get audio frames infor
    int32_t (*pfnGetFrmInfo)(void *pDecoder, void *pInfo);
    // close audio decoder
    int32_t (*pfnCloseDecoder)(void *pDecoder);
    // reset audio decoder
    int32_t (*pfnResetDecoder)(void *pDecoder);
} RTAdecDecoder;

}

#endif  // _ROCKIT_EXT_ADEC_H_


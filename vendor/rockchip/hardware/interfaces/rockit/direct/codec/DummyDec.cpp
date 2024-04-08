/*
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
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "DummyDec"

#include <stdio.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <cerrno>
#include <stdlib.h>
#include <utils/Log.h>
#include "rt_error.h"
#include "DummyDec.h"
#include "RockitExtAdec.h"

namespace android {

typedef struct _ExtDummyContext {
    HANDLE_XXXDECODER    mHandle;
    int32_t              mOutDataOffset;
    int32_t              mOutDataLeftLen;
    int64_t              mTimeStamp;
    AUDIO_BIT_WIDTH_E    enBitwidth;
} ExtDummyContext;

static uint32_t getBytesPerSample(AUDIO_BIT_WIDTH_E enBitwidth) {
    uint32_t u32BytesPerSample = -1;

    switch(enBitwidth) {
    case AUDIO_BIT_WIDTH_8:
        u32BytesPerSample = 1; break;
    case AUDIO_BIT_WIDTH_16:
        u32BytesPerSample = 2; break;
    case AUDIO_BIT_WIDTH_24:
        u32BytesPerSample = 3; break;
    case AUDIO_BIT_WIDTH_32:
        u32BytesPerSample = 4; break;
    default:
        ALOGD("Unsupported enBitwidth %d", enBitwidth);
        break;
    }

    return u32BytesPerSample;
}

int32_t DummyDec::open(void *pDecoderAttr, void **ppDecoder) {
    ADEC_ATTR_CODEC_S *attr = (ADEC_ATTR_CODEC_S *)pDecoderAttr;
    TRANSPORT_TYPE transportFmt = (TRANSPORT_TYPE)attr->u32Resv[0];
    XXX_DECODER_ERROR err = XXX_DEC_OK;
    ExtDummyContext *ctx = (ExtDummyContext *)malloc(sizeof(ExtDummyContext));
    memset(ctx, 0, sizeof(ExtDummyContext));

    ctx->mHandle = xxxDecoder_Open(transportFmt, 1);
    if(!ctx->mHandle) {
        ALOGD("xxxDecoder_Open failed");
        goto _FAIL;
    }

    if (attr->u32ExtraDataSize > 0 && attr->pExtraData != NULL) {
        ALOGD("config extradata size:%d", attr->u32ExtraDataSize);
        err = xxxDecoder_ConfigRaw(ctx->mHandle, (uint8_t **)&attr->pExtraData, &attr->u32ExtraDataSize);
        if (err != XXX_DEC_OK) {
            ALOGD("xxxDecoder_ConfigRaw fail : 0x%x", err);
            goto _FAIL;
        }
    }
    ctx->enBitwidth = AUDIO_BIT_WIDTH_16;
    *ppDecoder = (void *)ctx;
    return RT_OK;

_FAIL:
    if (ctx->mHandle) {
        xxxDecoder_Close(ctx->mHandle);
    }
    free(ctx);
    *ppDecoder = NULL;
    return RT_ERR_UNSUPPORT;
}

int32_t DummyDec::decode(void *pDecoder, void *pDecParam) {
    XXX_DECODER_ERROR ret = XXX_DEC_OK;
    ExtDummyContext *ctx = (ExtDummyContext *)pDecoder;
    if (ctx == NULL || ctx->mHandle == NULL || pDecParam == NULL)
        return RT_ERR_UNKNOWN;

    AUDIO_ADENC_PARAM_S *pParam = (AUDIO_ADENC_PARAM_S *)pDecParam;
    uint8_t *pInput = pParam->pu8InBuf;
    uint32_t inLength = pParam->u32InLen;
    uint32_t validLength = inLength;
    bool eos = false;

    if ((pInput == NULL) || (inLength == 0))
        eos = true;

    // send input data to XXX dec
    ret = xxxDecoder_Fill(ctx->mHandle, &pInput, &inLength, &validLength);
    if (ret != XXX_DEC_OK) {
        ALOGD("xxxDecoder_Fill failed[%d]", ret);
        return RT_ERR_UNKNOWN;
    }
    pParam->u32InLen = validLength;
    // XXX decode frame
    ret = xxxDecoder_DecodeFrame(ctx->mHandle, (INT_PCM *)pParam->pu8OutBuf, pParam->u32OutLen / sizeof(INT_PCM), 0);
    if(ret != XXX_DEC_OK) {
        pParam->u32OutLen = 0;
        if(ret == XXX_DEC_NOT_ENOUGH_BITS) {
            if(eos) {
                return ADEC_DECODER_EOS;
            } else {
                ALOGD("data not enough");
                return ADEC_DECODER_TRY_AGAIN;
            }
        } else if (ret == XXX_DEC_OUTPUT_BUFFER_TOO_SMALL) {
            ALOGD("output buffer is too small");
            return ADEC_DECODER_ERROR;
        }

        ALOGD("xxxDecoder_DecodeFrame failed[%d]", ret);
        return ADEC_DECODER_ERROR;
    }

    CStreamInfo* info = xxxDecoder_GetStreamInfo(ctx->mHandle);
    if(!info) {
        ALOGD("xxxDecoder_GetStreamInfo failed[%d]", ret);
        return ADEC_DECODER_ERROR;
    }

    pParam->u64OutTimeStamp = ctx->mTimeStamp;
    ctx->mTimeStamp += ((int64_t)info->frameSize*1000000)/info->sampleRate;

    uint32_t u32BytesPerSample = getBytesPerSample(ctx->enBitwidth);
    if(u32BytesPerSample == -1)
        return ADEC_DECODER_ERROR;

    pParam->u32OutLen = info->frameSize * info->numChannels * u32BytesPerSample;
    return ADEC_DECODER_OK;
}

int32_t DummyDec::getFrameInfo(void *pDecoder, void *pInfo) {
    ADEC_FRAME_INFO_S stFrameInfo;
    ExtDummyContext *ctx = (ExtDummyContext *)pDecoder;

    if (ctx == NULL || pInfo == NULL)
        return RT_ERR_UNKNOWN;

    CStreamInfo *pstStreamInfo = xxxDecoder_GetStreamInfo(ctx->mHandle);
    if(!pstStreamInfo) {
        ALOGD("xxxDecoder_GetStreamInfo failed");
        return RT_ERR_UNKNOWN;
    }

    memset(&stFrameInfo, 0, sizeof(ADEC_FRAME_INFO_S));
    stFrameInfo.u32Channels = pstStreamInfo->numChannels;
    stFrameInfo.u32SampleRate = pstStreamInfo->sampleRate;
    stFrameInfo.u32FrameSize = pstStreamInfo->frameSize;
    stFrameInfo.enBitWidth = ctx->enBitwidth;

    memcpy(pInfo, &stFrameInfo, sizeof(ADEC_FRAME_INFO_S));
    return RT_OK;
}

int32_t DummyDec::close(void *pDecoder) {
    ExtDummyContext *ctx = (ExtDummyContext *)pDecoder;
    if (ctx == NULL)
        return RT_ERR_UNKNOWN;

    xxxDecoder_Close(ctx->mHandle);
    free(ctx);
    return RT_OK;
}

int32_t DummyDec::reset(void *pDecoder) {
    ExtDummyContext *ctx = reinterpret_cast<ExtDummyContext *>(pDecoder);

    if (ctx == NULL || ctx->mHandle == NULL)
        return RT_ERR_UNKNOWN;

    return RT_OK;
}

}

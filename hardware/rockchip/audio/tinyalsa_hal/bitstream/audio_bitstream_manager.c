/*
 * Copyright 2020 Rockchip Electronics Co. LTD
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
 * Author: hh@rock-chips.com
 * Date: 2021/11/27
 */


#include "audio_bitstream_manager.h"
#include <utils/Log.h>
#include <string.h>
#include "stdio.h"
#include <stdlib.h>

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG "bitstream_manager"
#define PADDING_SIZE 32

rk_bistream* bitstream_init(enum pcm_format format, int samplerate, int channel) {
    rk_bistream *bs = (rk_bistream*)malloc(sizeof(rk_bistream));
    memset(bs, 0, sizeof(rk_bistream));
    bs->format = format;
    bs->samplerate = samplerate;
    bs->channel = channel;
    bs->buffer = NULL;
    bs->chnStatus = NULL;
    bs->firstFrame = true;

    if (format == PCM_FORMAT_S24_LE) {
        bs->chnStatus = malloc(CHASTA_SUB_NUM);
        initchnsta(bs->chnStatus);
        setChanSta(bs->chnStatus, samplerate, channel);
    } else if (format == PCM_FORMAT_IEC958_SUBFRAME_LE) {
        iec958_init(&bs->iec958, samplerate, channel, false);
    } else {
        ALOGD("%s: format = %d not support", __FUNCTION__, (int)format);
        free(bs);
        return NULL;
    }
    ALOGD("%s:%d format = %d, samplerate = %d, channel = %d",
        __FUNCTION__, __LINE__, format, samplerate, channel);
    return bs;
}

int bitstream_encode(rk_bistream *bs, char *inBuffer, int inSize, char **outBuffer, int *outSize) {
    if (bs == NULL)
        return -1;

    if ((bs->buffer == NULL) || (bs->capaticy < inSize*2)) {
        if (bs->buffer != NULL) {
            free(bs->buffer);
        }

        bs->capaticy = inSize*2 + PADDING_SIZE;
        bs->buffer = (char *)malloc(bs->capaticy);
        ALOGD("%s: %d malloc bistream buffer(size = %d)", __FUNCTION__, __LINE__, bs->capaticy);
    }

    *outBuffer = bs->buffer;
    *outSize   = 0;
    int ret = -1;
    int offset = 0;
    int size = 0;
    /*
     * this is for TV compatibility
     * Our's HDMI/DRIVER may drop 1 sample for some reason which is 4 bytes for ac3/eac3/dts
     * or 16bytes for DTS-HD/Atoms/TrueHD, some TV only identify the first frame which first
     * received, if the sync word is lost in first frame, the TV don't identify the next frames
     * and keep silence.
     */
    char padding[16] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    int paddingSize = (bs->channel == 8) ? 16 : 4;
    if (bs->format == PCM_FORMAT_S24_LE) {
#ifdef ADD_PADDING
        if (bs->firstFrame) {
            bs->firstFrame = false;
            ALOGD("add padding size = %d", paddingSize);
            fill_hdmi_bitstream_buf((void *)padding, (void *)bs->buffer, (void*)bs->chnStatus, paddingSize);
            offset = 2*paddingSize;
        }
#endif

        ret = fill_hdmi_bitstream_buf((void *)inBuffer, (void *)&bs->buffer[offset], (void*)bs->chnStatus, (int)inSize);
        *outSize = 2*inSize+offset;
    } else if (bs->format == PCM_FORMAT_IEC958_SUBFRAME_LE) {
#ifdef ADD_PADDING
        if (bs->firstFrame) {
            bs->firstFrame = false;
            ALOGD("add padding size = %d", paddingSize);
            iec958_frame_encode(&bs->iec958, padding, paddingSize, bs->buffer, &offset);
            *outSize += offset;
        }
#endif
        ret = iec958_frame_encode(&bs->iec958, inBuffer, inSize, &bs->buffer[offset], &size);
        *outSize += size;
    } else {
        ALOGD("%s: format = %d not support", __FUNCTION__, (int)bs->format);
        *outSize = 0;
    }

    return ret;
}

void bitstream_destory(rk_bistream **bitstream) {
    rk_bistream *bs = *bitstream;
    if (bs == NULL)
        return;

    if (bs->format == PCM_FORMAT_S24_LE) {
        if (bs->chnStatus != NULL) {
            free(bs->chnStatus);
            bs->chnStatus = NULL;
        }
    } else if (bs->format == PCM_FORMAT_IEC958_SUBFRAME_LE) {
    }

    if (bs->buffer != NULL) {
        free(bs->buffer);
        bs->buffer = NULL;
    }

    bs->capaticy = 0;
    iec958_deInit(&bs->iec958);
    free(bs);
    *bitstream = NULL;
}


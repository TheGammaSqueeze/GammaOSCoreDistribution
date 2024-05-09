/*
 * Copyright (C) 2020 Rockchip Electronics Co. LTD
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
#define ROCKCHIP_LOG_TAG    "C2RKNalParser"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "mpp/rk_mpi.h"
#include "C2RKNalParser.h"
#include "C2RKLog.h"

#define H264_NAL_SPS            7
#define H264_PROFILE_HIGH10     110
#define H265_NAL_SPS            33
#define H265_MAX_VPS_COUNT      16
#define H265_MAX_SUB_LAYERS     7
#define H265_PROFILE_MAIN_10    2

bool C2RKNalParser::avcGetBitDepth(uint8_t *buf, int32_t size, int32_t *bitDepth) {
    BitReadContext  gbCtx;
    BitReadContext *gb = &gbCtx;
    int32_t startCodeLen = 0;
    int32_t value = 0;

    c2_set_bitread_ctx(gb, buf, size);
    c2_set_pre_detection(gb);
    if (!c2_update_curbyte(gb)) {
        c2_err("failed to update curbyte, skipping.");
        goto error;
    }

    /*
     * ExtraData carry h264 sps_info in two ways.
     * 1. start with 0x000001 or 0x00000001
     * 2. AVC extraData configuration
     */

    if (buf[0] == 0x00 && buf[1] == 0x00 && buf[2] == 0x01) {
        startCodeLen = 3;
    } else if (buf[0] == 0x00 && buf[1] == 0x00 && buf[2] == 0x00 && buf[3] == 0x01) {
        startCodeLen = 4;
    } else {
        startCodeLen = 0;
    }

    if (startCodeLen > 0) {
        SKIP_BITS(gb, startCodeLen * 8);
    } else {
        // AVC extraData configuration
        SKIP_BITS(gb, 32);
        SKIP_BITS(gb, 16);

        SKIP_BITS(gb, 16);  // sequenceParameterSetLength
    }

    /* parse h264 sps info */
    READ_ONEBIT(gb, &value);  // forbidden_bit

    SKIP_BITS(gb, 2);  // nal_ref_idc
    READ_BITS(gb, 5, &value);  // nalu_type
    // stop traversal if not SPS nalu type
    if (value != H264_NAL_SPS) {
        goto error;
    }

    READ_BITS(gb, 8, &value);  // profile_idc
    if (value == H264_PROFILE_HIGH10) {
        *bitDepth = 10;
    } else {
        *bitDepth = 8;
    }

    return true;

__BR_ERR:
error:
    return false;
}

bool C2RKNalParser::hevcParseNalSps(BitReadContext *gb, int32_t *bitDepth) {
    int32_t value = 0;

    READ_BITS(gb, 4, &value); // vps-id
    if (value > H265_MAX_VPS_COUNT) {
        c2_err("VPS id out of range: %d", value);
        goto error;
    }

    READ_BITS(gb, 3, &value);
    value += 1;

    if (value > H265_MAX_SUB_LAYERS) {
        c2_err("sps_max_sub_layers out of range: %d", value);
        goto error;
    }

    SKIP_BITS(gb, 1); // temporal_id_nesting_flag

    SKIP_BITS(gb, 3); // profile_space & tier_flag
    READ_BITS(gb, 5, &value); // profile_idc

    if (value == H265_PROFILE_MAIN_10) {
        *bitDepth = 10;
    } else {
        *bitDepth = 8;
    }

    return true;

__BR_ERR:
error:
    return false;
}

bool C2RKNalParser::hevcParseNalUnit(uint8_t *buf, int32_t size, int32_t *bitDepth) {
    BitReadContext gb_ctx;
    BitReadContext *gb = &gb_ctx;
    int32_t nalUnitType = 0;
    int32_t nuhLayerId = 0;
    int32_t temporalId = 0;

    c2_set_bitread_ctx(gb, buf, size);
    c2_set_pre_detection(gb);
    if (!c2_update_curbyte(gb)) {
        c2_err("failed to update curbyte, skipping.");
        return false;
    }

    SKIP_BITS(gb, 1); /* this bit should be zero */
    READ_BITS(gb, 6, &nalUnitType);
    READ_BITS(gb, 6, &nuhLayerId);
    READ_BITS(gb, 3, &temporalId);

    temporalId = temporalId -1;

    c2_trace("nal_unit_type: %d, nuh_layer_id: %d temporal_id: %d",
             nalUnitType, nuhLayerId, temporalId);

    if (temporalId < 0) {
        c2_err("Invalid NAL unit %d, skipping.", nalUnitType);
        goto error;
    }

    if (nalUnitType == H265_NAL_SPS) {
        if (hevcParseNalSps(gb, bitDepth)) {
            return true;
        }
    }

__BR_ERR:
error:
    return false;
}

/* parse hevc sps info to find out bitdepth info */
bool C2RKNalParser::hevcGetBitDepth(uint8_t *buf, int32_t size, int32_t *bitDepth) {
    if (buf[0] || buf[1] || buf[2] > 1) {
        int32_t i = 0, j = 0;
        int32_t nalLenSize = 0;
        uint32_t numOfArrays = 0, numOfNals = 0;

        /* It seems the extradata is encoded as hvcC format.
         * Temporarily, we support configurationVersion==0 until 14496-15 3rd
         * is finalized. When finalized, configurationVersion will be 1 and we
         * can recognize hvcC by checking if h265dctx->extradata[0]==1 or not. */
        if (size < 7) {
            goto error;
        }

        c2_info("extradata is encoded as hvcC format");

        nalLenSize = 1 + (buf[14 + 7] & 3);
        buf += 22;
        size -= 22;
        numOfArrays = static_cast<char>(buf[0]);
        buf += 1;
        size -= 1;
        for (i = 0; i < numOfArrays; i++) {
            buf += 1;
            size -= 1;
            // Num of nals
            numOfNals = buf[0] << 8 | buf[1];
            buf += 2;
            size -= 2;

            for (j = 0; j < numOfNals; j++) {
                uint32_t length = 0;
                if (size < 2) {
                    goto error;
                }

                length = buf[0] << 8 | buf[1];

                buf += 2;
                size -= 2;
                if (size < length) {
                    goto error;
                }
                if (hevcParseNalUnit(buf, length, bitDepth)) {
                    return true;
                }
                buf += length;
                size -= length;
            }
        }
    } else {
        int32_t i;
        for (i = 0; i < size - 4; i++) {
            // find sps start code
            if (buf[i] == 0x00 && buf[i + 1] == 0x00 &&
                buf[i + 2] == 0x01 && ((buf[i + 3] & 0x7f) >> 1) == H265_NAL_SPS) {
                c2_info("find h265 sps start code.");
                i += 3;
                if (hevcParseNalUnit(buf + i, size - i, bitDepth)) {
                    return true;
                }
            }
        }
    }

error:
    return false;
}

int32_t C2RKNalParser::getBitDepth(uint8_t *buf, int32_t size, int32_t codingType) {
    int32_t bitDepth = 0;

    if (size < 4) {
        // default 8bit
        return 8;
    }

    if (codingType == MPP_VIDEO_CodingAVC) {
        if (!avcGetBitDepth(buf, size, &bitDepth)) {
            bitDepth = 8;
        }
    } else if (codingType == MPP_VIDEO_CodingHEVC) {
        if (!hevcGetBitDepth(buf, size, &bitDepth)) {
            bitDepth = 8;
        }
    } else {
        bitDepth = 8;
        c2_trace("not support coding %d yet, set default 8bit.", codingType);
    }

    return bitDepth;
}

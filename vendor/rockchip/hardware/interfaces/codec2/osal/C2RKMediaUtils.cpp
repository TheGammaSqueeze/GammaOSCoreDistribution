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
#define ROCKCHIP_LOG_TAG    "C2RKMediaUtils"

#include <string.h>

#include <C2Config.h>
#include "hardware/hardware_rockchip.h"
#include "hardware/gralloc_rockchip.h"
#include "C2RKMediaUtils.h"
#include "C2RKLog.h"
#include "C2RKEnv.h"
#include "mpp/mpp_soc.h"

using namespace android;

std::atomic<int32_t> sDecConcurrentInstances = 0;
std::atomic<int32_t> sEncConcurrentInstances = 0;

static C2LevelInfo h264LevelInfos[] = {
    /*  level            maxDpbPixs(maxDpbMbs * 256) name    */
    {   C2Config::LEVEL_AVC_5,    110400 * 256,    "h264 level 5"   },
    {   C2Config::LEVEL_AVC_5_1,  184320 * 256,    "h264 level 5.1" },
    {   C2Config::LEVEL_AVC_5_2,  184320 * 256,    "h264 level 5.2" },
    {   C2Config::LEVEL_AVC_6,    696320 * 256,    "h264 level 6"   },
    {   C2Config::LEVEL_AVC_6_1,  696320 * 256,    "h264 level 6.1" },
    {   C2Config::LEVEL_AVC_6_2,  696320 * 256,    "h264 level 6.2" },
};

static C2LevelInfo h265LevelInfos[] = {
    /*  level                     maxDpbMBs(maxPicSize * 6) name    */
    {   C2Config::LEVEL_HEVC_MAIN_5,     8912896 * 6,    "h265 level 5"   },
    {   C2Config::LEVEL_HEVC_MAIN_5_1,   8912896 * 6,    "h265 level 5.1" },
    {   C2Config::LEVEL_HEVC_MAIN_5_2,   8912896 * 6,    "h265 level 5.2" },
    {   C2Config::LEVEL_HEVC_MAIN_6,    35651584 * 6,    "h265 level 6"   },
    {   C2Config::LEVEL_HEVC_MAIN_6_1,  35651584 * 6,    "h265 level 6.1" },
    {   C2Config::LEVEL_HEVC_MAIN_6_2,  35651584 * 6,    "h265 level 6.2" },
    {   C2Config::LEVEL_HEVC_HIGH_5,     8912896 * 6,    "h265 level 5"   },
    {   C2Config::LEVEL_HEVC_HIGH_5_1,   8912896 * 6,    "h265 level 5.1" },
    {   C2Config::LEVEL_HEVC_HIGH_5_2,   8912896 * 6,    "h265 level 5.2" },
    {   C2Config::LEVEL_HEVC_HIGH_6,    35651584 * 6,    "h265 level 6"   },
    {   C2Config::LEVEL_HEVC_HIGH_6_1,  35651584 * 6,    "h265 level 6.1" },
    {   C2Config::LEVEL_HEVC_HIGH_6_2,  35651584 * 6,    "h265 level 6.2" },
};

static C2LevelInfo vp9LevelInfos[] = {
    /*  level                     maxDpbMBs(maxPicSize * 4) name    */
    {   C2Config::LEVEL_VP9_5,      8912896 * 4,    "vp9 level 5"   },
    {   C2Config::LEVEL_VP9_5_1,    8912896 * 4,    "vp9 level 5.1" },
    {   C2Config::LEVEL_VP9_5_2,    8912896 * 4,    "vp9 level 5.2" },
    {   C2Config::LEVEL_VP9_6,     35651584 * 4,    "vp9 level 6"   },
    {   C2Config::LEVEL_VP9_6_1,   35651584 * 4,    "vp9 level 6.1" },
    {   C2Config::LEVEL_VP9_6_2,   35651584 * 4,    "vp9 level 6.2" },
};

bool C2RKMediaUtils::getCodingTypeFromComponentName(
        C2String componentName, MppCodingType *codingType) {
    for (int i = 0; i < C2_RK_ARRAY_ELEMS(kComponentMapEntry); ++i) {
        if (!strcasecmp(componentName.c_str(), kComponentMapEntry[i].componentName.c_str())) {
            *codingType = kComponentMapEntry[i].codingType;
            return true;
        }
    }

    *codingType = MPP_VIDEO_CodingUnused;

    return false;
}

bool C2RKMediaUtils::getMimeFromComponentName(C2String componentName, C2String *mime) {
    for (int i = 0; i < C2_RK_ARRAY_ELEMS(kComponentMapEntry); ++i) {
        if (!strcasecmp(componentName.c_str(), kComponentMapEntry[i].componentName.c_str())) {
            *mime = kComponentMapEntry[i].mime;
            return true;
        }
    }

    return false;
}
bool C2RKMediaUtils::getKindFromComponentName(C2String componentName, C2Component::kind_t *kind) {
    C2Component::kind_t tmp_kind = C2Component::KIND_OTHER;
    if (componentName.find("encoder") != std::string::npos) {
        tmp_kind = C2Component::KIND_ENCODER;
    } else if (componentName.find("decoder") != std::string::npos) {
        tmp_kind = C2Component::KIND_DECODER;
    } else {
        return false;
    }

    *kind = tmp_kind;

    return true;
}

bool C2RKMediaUtils::getDomainFromComponentName(C2String componentName, C2Component::domain_t *domain) {
    MppCodingType codingType;
    C2Component::domain_t tmp_domain;

    if (!getCodingTypeFromComponentName(componentName, &codingType)) {
        c2_err("get coding type from component name failed");
        return false;
    }

    switch (codingType) {
        case MPP_VIDEO_CodingAVC:
        case MPP_VIDEO_CodingVP9:
        case MPP_VIDEO_CodingHEVC:
        case MPP_VIDEO_CodingVP8:
        case MPP_VIDEO_CodingMPEG2:
        case MPP_VIDEO_CodingMPEG4:
        case MPP_VIDEO_CodingH263:
        case MPP_VIDEO_CodingAV1: {
            tmp_domain = C2Component::DOMAIN_VIDEO;
        } break;
        default: {
            c2_err("unsupport coding type: %d", codingType);
            return false;
        }
    }

    *domain = tmp_domain;

    return true;
}


int32_t C2RKMediaUtils::colorFormatMpiToAndroid(uint32_t format, bool fbcMode) {
    int32_t aFormat = HAL_PIXEL_FORMAT_YCrCb_NV12;

    switch (format & MPP_FRAME_FMT_MASK) {
        case MPP_FMT_YUV422SP:
        case MPP_FMT_YUV422P: {
            if (fbcMode) {
                aFormat = HAL_PIXEL_FORMAT_YCbCr_422_I;
            } else {
                aFormat = HAL_PIXEL_FORMAT_YCbCr_422_SP;
            }
        } break;
        case MPP_FMT_YUV420SP:
        case MPP_FMT_YUV420P: {
            if (fbcMode) {
                aFormat = HAL_PIXEL_FORMAT_YUV420_8BIT_I;
            } else {
                aFormat = HAL_PIXEL_FORMAT_YCrCb_NV12;
            }
        } break;
        case MPP_FMT_YUV420SP_10BIT: {
            if (fbcMode) {
                aFormat = HAL_PIXEL_FORMAT_YUV420_10BIT_I;
            } else {
                aFormat = HAL_PIXEL_FORMAT_YCrCb_NV12_10;
            }
        } break;
        case MPP_FMT_YUV422SP_10BIT: {
            if (fbcMode) {
                aFormat = HAL_PIXEL_FORMAT_Y210;
            } else {
                aFormat = HAL_PIXEL_FORMAT_YCbCr_422_SP_10;
            }
        } break;
        default: {
            c2_err("unsupport color format: 0x%x", format);
        }
    }

    return aFormat;
}

bool C2RKMediaUtils::checkHWSupport(MppCtxType type, MppCodingType codingType) {
    c2_info("type:%d codingType:%d", type, codingType);

    if (!mpp_check_soc_cap(type, codingType)) {
        return false;
    }

    return true;
}

uint64_t C2RKMediaUtils::getStrideUsage(int32_t width, int32_t stride) {
    if (stride == C2_ALIGN_ODD(width, 256)) {
        return RK_GRALLOC_USAGE_STRIDE_ALIGN_256_ODD_TIMES;
    } else if (stride == C2_ALIGN(width, 128)) {
        return  RK_GRALLOC_USAGE_STRIDE_ALIGN_128;
    } else if (stride == C2_ALIGN(width, 64)) {
        return RK_GRALLOC_USAGE_STRIDE_ALIGN_64;
    } else {
        return RK_GRALLOC_USAGE_STRIDE_ALIGN_16;
    }
}

uint64_t C2RKMediaUtils::getHStrideUsage(int32_t height, int32_t hstride) {
    if (hstride == C2_ALIGN(height, 64)) {
        return RK_GRALLOC_USAGE_ALLOC_HEIGHT_ALIGN_64;
    } else if (hstride == C2_ALIGN(height, 16)) {
        return  RK_GRALLOC_USAGE_ALLOC_HEIGHT_ALIGN_16;
    } else {
        return RK_GRALLOC_USAGE_ALLOC_HEIGHT_ALIGN_8;
    }
}

uint32_t C2RKMediaUtils::calculateOutputDelay(
        int32_t width, int32_t height, MppCodingType type, int32_t level) {
    uint32_t maxDpbPixs = 0;
    uint32_t outputDelay = 0;

    switch (type) {
      case MPP_VIDEO_CodingAVC: {
        // default max Dpb Mbs is level 5.1
        maxDpbPixs = h264LevelInfos[1].maxDpbPixs;
        for (int idx = 0; idx < C2_RK_ARRAY_ELEMS(h264LevelInfos); idx++) {
            if (h264LevelInfos[idx].level == level) {
                maxDpbPixs = h264LevelInfos[idx].maxDpbPixs;
            }
        }
        outputDelay = maxDpbPixs / (width * height);
        outputDelay = C2_CLIP(outputDelay, AVC_MIN_OUTPUT_DELAY, AVC_MAX_OUTPUT_DELAY);
        if (width <= 1920 || height <= 1920) {
            // reserved for deinterlace
            outputDelay += IEP_MAX_OUTPUT_DELAY;
        }
      } break;
      case MPP_VIDEO_CodingHEVC: {
        // default max Dpb Mbs is level 5.1
        maxDpbPixs = h265LevelInfos[1].maxDpbPixs;
        for (int idx = 0; idx < C2_RK_ARRAY_ELEMS(h265LevelInfos); idx++) {
            if (h265LevelInfos[idx].level == level) {
                maxDpbPixs = h265LevelInfos[idx].maxDpbPixs;
            }
        }
        outputDelay = maxDpbPixs / (width * height);
        outputDelay = C2_CLIP(outputDelay, HEVC_MIN_OUTPUT_DELAY, HEVC_MAX_OUTPUT_DELAY);
      } break;
      case MPP_VIDEO_CodingVP9: {
        // default max Dpb Mbs is level 5.1
        maxDpbPixs = vp9LevelInfos[1].maxDpbPixs;
        for (int idx = 0; idx < C2_RK_ARRAY_ELEMS(vp9LevelInfos); idx++) {
            if (vp9LevelInfos[idx].level == level) {
                maxDpbPixs = vp9LevelInfos[idx].maxDpbPixs;
            }
        }
        outputDelay = maxDpbPixs / (width * height);
        outputDelay = C2_CLIP(outputDelay, VP9_MIN_OUTPUT_DELAY, VP9_MAX_OUTPUT_DELAY);
      } break;
      case MPP_VIDEO_CodingAV1:
        outputDelay = AV1_OUTPUT_DELAY;
        break;
      default: {
        c2_err("use default ref frame count(%d) with no CodecID", C2_DEFAULT_OUTPUT_DELAY);
        outputDelay = C2_DEFAULT_OUTPUT_DELAY;
      }
    }

    return outputDelay;
}

bool C2RKMediaUtils::isP010Allowed() {
    // The first SDK the device shipped with.
    uint32_t productFirstApiLevel;
    Rockchip_C2_GetEnvU32("ro.product.first_api_level", &productFirstApiLevel, 0);

    // GRF devices (introduced in Android 11) list the first and possibly the current api levels
    // to signal which VSR requirements they conform to even if the first device SDK was higher.
    uint32_t boardFirstApiLevel;
    Rockchip_C2_GetEnvU32("ro.board.first_api_level", &boardFirstApiLevel, 0);

    // Some devices that launched prior to Android S may not support P010 correctly, even
    // though they may advertise it as supported.
    if (productFirstApiLevel != 0 && productFirstApiLevel < 31) {
        return false;
    }

    if (boardFirstApiLevel != 0 && boardFirstApiLevel < 31) {
        return false;
    }

    uint32_t boardApiLevel;
    Rockchip_C2_GetEnvU32("ro.board.api_level", &boardApiLevel, 0);
    // For non-GRF devices, use the first SDK version by the product.
    uint32_t kFirstApiLevel =
        boardApiLevel != 0 ? boardApiLevel :
        boardFirstApiLevel != 0 ? boardFirstApiLevel :
        productFirstApiLevel;

    return kFirstApiLevel >= 33;
}

void C2RKMediaUtils::convert10BitNV12ToP010(
        uint8_t *dstY, uint8_t *dstUV, size_t dstYStride,
        size_t dstUVStride, uint8_t *src, size_t hstride,
        size_t vstride, size_t width, size_t height) {
    uint32_t i, k;
    uint8_t *base_y = src;
    uint8_t *base_uv = src + hstride * vstride;
    for (i = 0; i < height; i++, base_y += hstride, dstY += dstYStride) {
        for (k = 0; k < (width + 7) / 8; k++) {
            uint16_t *pix = (uint16_t *)(dstY + k * 16);
            uint16_t *base_u16 = (uint16_t *)(base_y + k * 10);

            pix[0] =  (base_u16[0] & 0x03FF) << 6;
            pix[1] = ((base_u16[0] & 0xFC00) >> 10 | (base_u16[1] & 0x000F) << 6) << 6;
            pix[2] = ((base_u16[1] & 0x3FF0) >> 4) << 6;
            pix[3] = ((base_u16[1] & 0xC000) >> 14 | (base_u16[2] & 0x00FF) << 2) << 6;
            pix[4] = ((base_u16[2] & 0xFF00) >> 8  | (base_u16[3] & 0x0003) << 8) << 6;
            pix[5] = ((base_u16[3] & 0x0FFC) >> 2) << 6;
            pix[6] = ((base_u16[3] & 0xF000) >> 12 | (base_u16[4] & 0x003F) << 4) << 6;
            pix[7] = ((base_u16[4] & 0xFFC0) >> 6) << 6;
        }
    }
    for (i = 0; i < height / 2; i++, base_uv += hstride, dstUV += dstUVStride) {
        for (k = 0; k < (width + 7) / 8; k++) {
            uint16_t *pix = (uint16_t *)(dstUV + k * 16);
            uint16_t *base_u16 = (uint16_t *)(base_uv + k * 10);

            pix[0] =  (base_u16[0] & 0x03FF) << 6;
            pix[1] = ((base_u16[0] & 0xFC00) >> 10 | (base_u16[1] & 0x000F) << 6) << 6;
            pix[2] = ((base_u16[1] & 0x3FF0) >> 4) << 6;
            pix[3] = ((base_u16[1] & 0xC000) >> 14 | (base_u16[2] & 0x00FF) << 2) << 6;
            pix[4] = ((base_u16[2] & 0xFF00) >> 8  | (base_u16[3] & 0x0003) << 8) << 6;
            pix[5] = ((base_u16[3] & 0x0FFC) >> 2) << 6;
            pix[6] = ((base_u16[3] & 0xF000) >> 12 | (base_u16[4] & 0x003F) << 4) << 6;
            pix[7] = ((base_u16[4] & 0xFFC0) >> 6) << 6;
        }
    }
}

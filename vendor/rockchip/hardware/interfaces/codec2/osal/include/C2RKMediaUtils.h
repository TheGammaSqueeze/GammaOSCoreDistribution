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

#ifndef ANDROID_C2_RK_MEDIA_UTILS_H_
#define ANDROID_C2_RK_MEDIA_UTILS_H_

#include <media/stagefright/foundation/MediaDefs.h>

#include "mpp/rk_mpi.h"
#include <C2.h>
#include <C2Component.h>

using namespace android;

extern std::atomic<int32_t> sDecConcurrentInstances;
extern std::atomic<int32_t> sEncConcurrentInstances;

#define kMaxDecConcurrentInstances     32
#define kMaxEncConcurrentInstances     32

#define AVC_MIN_OUTPUT_DELAY            4
#define AVC_MAX_OUTPUT_DELAY           16
#define HEVC_MIN_OUTPUT_DELAY           6
#define HEVC_MAX_OUTPUT_DELAY          16
#define VP9_MIN_OUTPUT_DELAY            5
#define VP9_MAX_OUTPUT_DELAY            8
#define AV1_OUTPUT_DELAY               10
#define IEP_MAX_OUTPUT_DELAY            5

#define C2_DEFAULT_OUTPUT_DELAY        12
#define C2_MAX_OUTPUT_DELAY            21


#define C2_SAFE_FREE(p) { if (p) {free(p); (p)=NULL;} }
#define C2_ALIGN(x, a)         (((x)+(a)-1)&~((a)-1))
#define C2_ALIGN_ODD(x, a)     (((x)+(a)-1)&~((a)-1) | a)
#define C2_CLIP(a, l, h)       ((a) < (l) ? (l) : ((a) > (h) ? (h) : (a)))

#define C2_RK_ARRAY_ELEMS(a)      (sizeof(a) / sizeof((a)[0]))

static const struct ComponentMapEntry {
    C2String        componentName;
    MppCodingType   codingType;
    C2String        mime;
    MppCtxType      type;
} kComponentMapEntry[] = {
    { "c2.rk.avc.decoder", MPP_VIDEO_CodingAVC, MEDIA_MIMETYPE_VIDEO_AVC, MPP_CTX_DEC },
    { "c2.rk.vp9.decoder", MPP_VIDEO_CodingVP9, MEDIA_MIMETYPE_VIDEO_VP9, MPP_CTX_DEC },
    { "c2.rk.hevc.decoder", MPP_VIDEO_CodingHEVC, MEDIA_MIMETYPE_VIDEO_HEVC, MPP_CTX_DEC },
    { "c2.rk.vp8.decoder", MPP_VIDEO_CodingVP8, MEDIA_MIMETYPE_VIDEO_VP8, MPP_CTX_DEC },
    { "c2.rk.mpeg2.decoder", MPP_VIDEO_CodingMPEG2, MEDIA_MIMETYPE_VIDEO_MPEG2, MPP_CTX_DEC },
    { "c2.rk.m4v.decoder", MPP_VIDEO_CodingMPEG4, MEDIA_MIMETYPE_VIDEO_MPEG4, MPP_CTX_DEC },
    { "c2.rk.h263.decoder", MPP_VIDEO_CodingH263, MEDIA_MIMETYPE_VIDEO_H263, MPP_CTX_DEC },
    { "c2.rk.av1.decoder", MPP_VIDEO_CodingAV1, MEDIA_MIMETYPE_VIDEO_AV1, MPP_CTX_DEC },
    { "c2.rk.avc.decoder.secure", MPP_VIDEO_CodingAVC, MEDIA_MIMETYPE_VIDEO_AVC, MPP_CTX_DEC },
    { "c2.rk.vp9.decoder.secure", MPP_VIDEO_CodingVP9, MEDIA_MIMETYPE_VIDEO_VP9, MPP_CTX_DEC },
    { "c2.rk.hevc.decoder.secure", MPP_VIDEO_CodingHEVC, MEDIA_MIMETYPE_VIDEO_HEVC, MPP_CTX_DEC },
    { "c2.rk.vp8.decoder.secure", MPP_VIDEO_CodingVP8, MEDIA_MIMETYPE_VIDEO_VP8, MPP_CTX_DEC },
    { "c2.rk.mpeg2.decoder.secure", MPP_VIDEO_CodingMPEG2, MEDIA_MIMETYPE_VIDEO_MPEG2, MPP_CTX_DEC },
    { "c2.rk.m4v.decoder.secure", MPP_VIDEO_CodingMPEG4, MEDIA_MIMETYPE_VIDEO_MPEG4, MPP_CTX_DEC },
    { "c2.rk.avc.encoder", MPP_VIDEO_CodingAVC, MEDIA_MIMETYPE_VIDEO_AVC, MPP_CTX_ENC },
    { "c2.rk.hevc.encoder", MPP_VIDEO_CodingHEVC, MEDIA_MIMETYPE_VIDEO_HEVC, MPP_CTX_ENC },
    { "c2.rk.vp8.encoder", MPP_VIDEO_CodingVP8, MEDIA_MIMETYPE_VIDEO_VP8, MPP_CTX_ENC }
};

typedef struct {
    int32_t      level;
    uint32_t     maxDpbPixs;     /* Max dpb picture total pixels */
    const char  *name;
} C2LevelInfo;

class C2RKMediaUtils {
public:
    static bool getCodingTypeFromComponentName(C2String componentName, MppCodingType *codingType);
    static bool getMimeFromComponentName(C2String componentName, C2String *mime);
    static bool getKindFromComponentName(C2String componentName, C2Component::kind_t *kind);
    static bool getDomainFromComponentName(C2String componentName, C2Component::domain_t *domain);
    static bool checkHWSupport(MppCtxType type, MppCodingType codingType);
    static int32_t colorFormatMpiToAndroid(uint32_t format, bool fbcMode);
    static uint64_t getStrideUsage(int32_t width, int32_t stride);
    static uint64_t getHStrideUsage(int32_t height, int32_t hstride);
    static uint32_t calculateOutputDelay(int32_t width, int32_t height, MppCodingType type, int32_t level);
    static bool isP010Allowed();
    static void convert10BitNV12ToP010(
                uint8_t *dstY, uint8_t *dstUV, size_t dstYStride,
                size_t dstUVStride, uint8_t *src, size_t hstride,
                size_t vstride, size_t width, size_t height);
};

#endif  // ANDROID_C2_RK_MEDIA_UTILS_H_


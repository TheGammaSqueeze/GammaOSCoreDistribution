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
#define ROCKCHIP_LOG_TAG    "C2RKCodecMapper"

#include "C2RKCodecMapper.h"
#include "C2RKLog.h"
#include "mpp/rk_mpi.h"

#include <media/stagefright/MediaCodecConstants.h>
#include <C2Config.h>

const char *toStr_Profile(uint32_t i, uint32_t coding) {
    if (coding == MPP_VIDEO_CodingAVC) {
        switch (i) {
            case MPP_H264_BASELINE:     return "Baseline";
            case MPP_H264_MAIN:         return "Main";
            case MPP_H264_EXTENDED:     return "Extended";
            case MPP_H264_HIGH:         return "High";
            case MPP_H264_HIGH10:       return "High10";
            case MPP_H264_HIGH422:      return "High422";
            case MPP_H264_HIGH444:      return "High444";
            default:                    return "unknown";
        }
    } else if (coding == MPP_VIDEO_CodingHEVC) {
        switch (i) {
            case MPP_PROFILE_HEVC_MAIN:                 return "Main";
            case MPP_PROFILE_HEVC_MAIN_10:              return "Main10";
            case MPP_PROFILE_HEVC_MAIN_STILL_PICTURE:   return "MainStill";
            default:                                    return "unknown";
        }
    }
    c2_warn("unsupport coding type %d profile", coding, i);
    return "unknown";
}

const char *toStr_Level(uint32_t i, uint32_t coding) {
    if (coding == MPP_VIDEO_CodingAVC) {
        switch (i) {
            case MPP_H264_LEVEL1_0:     return "1";
            case MPP_H264_LEVEL1_B:     return "1b";
            case MPP_H264_LEVEL1_1:     return "1.1";
            case MPP_H264_LEVEL1_2:     return "1.2";
            case MPP_H264_LEVEL1_3:     return "1.3";
            case MPP_H264_LEVEL2_0:     return "2";
            case MPP_H264_LEVEL2_1:     return "2.1";
            case MPP_H264_LEVEL2_2:     return "2.2";
            case MPP_H264_LEVEL3_0:     return "3";
            case MPP_H264_LEVEL3_1:     return "3.1";
            case MPP_H264_LEVEL3_2:     return "3.2";
            case MPP_H264_LEVEL4_0:     return "4";
            case MPP_H264_LEVEL4_1:     return "4.1";
            case MPP_H264_LEVEL4_2:     return "4.2";
            case MPP_H264_LEVEL5_0:     return "5";
            case MPP_H264_LEVEL5_1:     return "5.1";
            case MPP_H264_LEVEL5_2:     return "5.2";
            case MPP_H264_LEVEL6_0:     return "6";
            case MPP_H264_LEVEL6_1:     return "6.1";
            case MPP_H264_LEVEL6_2:     return "6.2";
            default:                    return "unknown";
        }
    } else if (coding == MPP_VIDEO_CodingHEVC) {
        switch (i) {
            case MPP_H265_LEVEL1:       return "Main 1";
            case MPP_H265_LEVEL2:       return "Main 2";
            case MPP_H265_LEVEL2_1:     return "Main 2.1";
            case MPP_H265_LEVEL3:       return "Main 3";
            case MPP_H265_LEVEL3_1:     return "Main 3.1";
            case MPP_H265_LEVEL4:       return "Main 4";
            case MPP_H265_LEVEL4_1:     return "Main 4.1";
            case MPP_H265_LEVEL5:       return "Main 5";
            case MPP_H265_LEVEL5_1:     return "Main 5.1";
            case MPP_H265_LEVEL5_2:     return "Main 5.2";
            case MPP_H265_LEVEL6:       return "Main 6";
            case MPP_H265_LEVEL6_1:     return "Main 6.1";
            case MPP_H265_LEVEL6_2:     return "Main 6.2";
            default:                    return "unknown";
        }
    }
    c2_warn("unsupport coding type %d level %d", coding, i);
    return "unknown";
}

const char *toStr_BitrateMode(uint32_t i) {
    switch (i) {
        case MPP_ENC_RC_MODE_FIXQP: return "FIXQP";
        case MPP_ENC_RC_MODE_VBR:   return "VBR";
        case MPP_ENC_RC_MODE_CBR:   return "CBR";
        default:                    return "unknown";
    }
}

uint32_t C2RKCodecMapper::getMppH264Profile(uint32_t profile, bool c2Type) {
    struct AVCProfileMap {
        uint32_t c2Profile;
        uint32_t codecProfile;
        uint32_t mppProfile;
    };

    static const AVCProfileMap kAProfileMaps[] = {
        { PROFILE_AVC_BASELINE,             AVCProfileBaseline,            MPP_H264_BASELINE },
        { PROFILE_AVC_CONSTRAINED_BASELINE, AVCProfileConstrainedBaseline, MPP_H264_BASELINE },
        { PROFILE_AVC_MAIN,                 AVCProfileMain,                MPP_H264_MAIN },
        { PROFILE_AVC_EXTENDED,             AVCProfileExtended,            MPP_H264_EXTENDED },
        { PROFILE_AVC_HIGH,                 AVCProfileHigh,                MPP_H264_HIGH },
        { PROFILE_AVC_PROGRESSIVE_HIGH,     AVCProfileHigh,                MPP_H264_HIGH },
        { PROFILE_AVC_CONSTRAINED_HIGH,     AVCProfileConstrainedHigh,     MPP_H264_HIGH },
        { PROFILE_AVC_HIGH_10,              AVCProfileHigh10,              MPP_H264_HIGH10 },
        { PROFILE_AVC_PROGRESSIVE_HIGH_10,  AVCProfileHigh10,              MPP_H264_HIGH10 },
        { PROFILE_AVC_HIGH_422,             AVCProfileHigh422,             MPP_H264_HIGH422 },
        { PROFILE_AVC_HIGH_444_PREDICTIVE,  AVCProfileHigh444,             MPP_H264_HIGH444 },
        { PROFILE_AVC_HIGH_10_INTRA,        AVCProfileHigh10,              MPP_H264_HIGH10 },
        { PROFILE_AVC_HIGH_422_INTRA,       AVCProfileHigh422,             MPP_H264_HIGH422 },
        { PROFILE_AVC_HIGH_444_INTRA,       AVCProfileHigh444,             MPP_H264_HIGH444 },
        { PROFILE_AVC_CAVLC_444_INTRA,      AVCProfileHigh444,             MPP_H264_HIGH444 },
    };

    static const size_t kNumAProfileMaps =
        sizeof(kAProfileMaps) / sizeof(kAProfileMaps[0]);

    int32_t i;
    for (i = 0; i < kNumAProfileMaps; i++) {
        uint32_t dstProfile =
            c2Type ? kAProfileMaps[i].c2Profile : kAProfileMaps[i].codecProfile;
        if (profile == dstProfile)
            break;
    }

    if (i == kNumAProfileMaps) {
        c2_warn("get unsupport %s profile %d, set default main profile",
                c2Type ? "c2" : "codec", profile);
        return MPP_H264_MAIN;
    }

    return kAProfileMaps[i].mppProfile;
}

uint32_t C2RKCodecMapper::getMppH264Level(uint32_t level, bool c2Type) {
    struct AVCLevelMap {
        uint32_t c2Level;
        uint32_t codecLevel;
        uint32_t mppLevel;
    };

    static const AVCLevelMap kALevelMaps[] = {
        { LEVEL_AVC_1,    AVCLevel1,   MPP_H264_LEVEL1_0 },
        { LEVEL_AVC_1B,   AVCLevel1b,  MPP_H264_LEVEL1_B },
        { LEVEL_AVC_1_1,  AVCLevel11,  MPP_H264_LEVEL1_1 },
        { LEVEL_AVC_1_2,  AVCLevel12,  MPP_H264_LEVEL1_2 },
        { LEVEL_AVC_1_3,  AVCLevel13,  MPP_H264_LEVEL1_3 },
        { LEVEL_AVC_2,    AVCLevel2,   MPP_H264_LEVEL2_0 },
        { LEVEL_AVC_2_1,  AVCLevel21,  MPP_H264_LEVEL2_1 },
        { LEVEL_AVC_2_2,  AVCLevel22,  MPP_H264_LEVEL2_2 },
        { LEVEL_AVC_3,    AVCLevel3,   MPP_H264_LEVEL3_0 },
        { LEVEL_AVC_3_1,  AVCLevel31,  MPP_H264_LEVEL3_1 },
        { LEVEL_AVC_3_2,  AVCLevel32,  MPP_H264_LEVEL3_2 },
        { LEVEL_AVC_4,    AVCLevel4,   MPP_H264_LEVEL4_0 },
        { LEVEL_AVC_4_1,  AVCLevel41,  MPP_H264_LEVEL4_1 },
        { LEVEL_AVC_4_2,  AVCLevel42,  MPP_H264_LEVEL4_2 },
        { LEVEL_AVC_5,    AVCLevel5,   MPP_H264_LEVEL5_0 },
        { LEVEL_AVC_5_1,  AVCLevel51,  MPP_H264_LEVEL5_1 },
        { LEVEL_AVC_5_2,  AVCLevel52,  MPP_H264_LEVEL5_2 },
        { LEVEL_AVC_6,    AVCLevel6,   MPP_H264_LEVEL6_0 },
        { LEVEL_AVC_6_1,  AVCLevel61,  MPP_H264_LEVEL6_1 },
        { LEVEL_AVC_6_2,  AVCLevel62,  MPP_H264_LEVEL6_2 },
    };

    static const size_t kNumALevelMaps =
        sizeof(kALevelMaps) / sizeof(kALevelMaps[0]);

    int32_t i;
    for (i = 0; i < kNumALevelMaps; i++) {
        uint32_t dstLevel =
            c2Type ? kALevelMaps[i].c2Level : kALevelMaps[i].codecLevel;
        if (level == dstLevel)
            break;
    }

    if (i == kNumALevelMaps) {
        c2_warn("get unsupport %s level %d, set default level4_1",
                c2Type ? "c2" : "codec", level);
        return MPP_H264_LEVEL4_1;
    }

    return kALevelMaps[i].mppLevel;
}

uint32_t C2RKCodecMapper::getMppH265Profile(uint32_t profile) {
    struct HEVCProfileMap {
        uint32_t profile;
        uint32_t mppProfile;
    };

    static const HEVCProfileMap kHProfileMaps[] = {
        { PROFILE_HEVC_MAIN,          MPP_PROFILE_HEVC_MAIN },
        { PROFILE_HEVC_MAIN_10,       MPP_PROFILE_HEVC_MAIN_10 },
        { PROFILE_HEVC_MAIN_STILL,    MPP_PROFILE_HEVC_MAIN_STILL_PICTURE },
        { PROFILE_HEVC_MAIN_INTRA,    MPP_PROFILE_HEVC_MAIN },
        { PROFILE_HEVC_MAIN_10_INTRA, MPP_PROFILE_HEVC_MAIN_10 },
    };

    static const size_t kNumHProfileMaps =
        sizeof(kHProfileMaps) / sizeof(kHProfileMaps[0]);

    int32_t i;
    for (i = 0; i < kNumHProfileMaps; i++) {
        if (profile == kHProfileMaps[i].profile)
            break;
    }

    if (i == kNumHProfileMaps) {
        c2_warn("get unsupport profile %d, set default main profile", profile);
        return MPP_PROFILE_HEVC_MAIN;
    }

    return kHProfileMaps[i].mppProfile;
}

uint32_t C2RKCodecMapper::getMppH265Level(uint32_t level) {
    struct HEVCLevelMap {
        uint32_t level;
        uint32_t mppLevel;
    };

    static const HEVCLevelMap kHLevelMaps[] = {
        { LEVEL_HEVC_MAIN_1,      MPP_H265_LEVEL1 },
        { LEVEL_HEVC_MAIN_2,      MPP_H265_LEVEL2 },
        { LEVEL_HEVC_MAIN_2_1,    MPP_H265_LEVEL2_1 },
        { LEVEL_HEVC_MAIN_3,      MPP_H265_LEVEL3 },
        { LEVEL_HEVC_MAIN_3_1,    MPP_H265_LEVEL3_1 },
        { LEVEL_HEVC_MAIN_4,      MPP_H265_LEVEL4 },
        { LEVEL_HEVC_MAIN_4_1,    MPP_H265_LEVEL4_1 },
        { LEVEL_HEVC_MAIN_5,      MPP_H265_LEVEL5 },
        { LEVEL_HEVC_MAIN_5_1,    MPP_H265_LEVEL5_1 },
        { LEVEL_HEVC_MAIN_5_2,    MPP_H265_LEVEL5_2 },
        { LEVEL_HEVC_MAIN_6,      MPP_H265_LEVEL6 },
        { LEVEL_HEVC_MAIN_6_1,    MPP_H265_LEVEL6_1 },
        { LEVEL_HEVC_MAIN_6_2,    MPP_H265_LEVEL6_2 },
    };

    static const size_t kNumHLevelMaps =
        sizeof(kHLevelMaps) / sizeof(kHLevelMaps[0]);

    int32_t i;
    for (i = 0; i < kNumHLevelMaps; i++) {
        if (level == kHLevelMaps[i].level)
            break;
    }

    if (i == kNumHLevelMaps) {
        c2_warn("get unsupport level %d, set default level4_1", level);
        return MPP_H265_LEVEL4_1;
    }

    return kHLevelMaps[i].mppLevel;
}

uint32_t C2RKCodecMapper::getMppBitrateMode(int32_t mode, bool c2Type) {
    struct BitrateModeMap {
        uint32_t c2Mode;
        uint32_t codecMode;
        uint32_t mppMode;
    };

    static const BitrateModeMap kBModeMaps[] = {
        { BITRATE_IGNORE,             BITRATE_MODE_CQ,      MPP_ENC_RC_MODE_FIXQP },
        { BITRATE_VARIABLE,           BITRATE_MODE_VBR,     MPP_ENC_RC_MODE_VBR },
        { BITRATE_CONST,              BITRATE_MODE_CBR,     MPP_ENC_RC_MODE_CBR },
        { BITRATE_CONST_SKIP_ALLOWED, BITRATE_MODE_CBR_FD,  MPP_ENC_RC_MODE_CBR },
    };

    static const size_t kNumBModeMaps =
        sizeof(kBModeMaps) / sizeof(kBModeMaps[0]);

    int32_t i;
    for (i = 0; i < kNumBModeMaps; i++) {
        uint32_t dstMode = c2Type ? kBModeMaps[i].c2Mode : kBModeMaps[i].codecMode;
        if (dstMode == mode)
            break;
    }

    if (i == kNumBModeMaps) {
        c2_warn("get unsupport %s bitrate mode %d, set default cbr mode",
                c2Type ? "c2" : "codec", mode);
        return MPP_ENC_RC_MODE_CBR;
    }

    return kBModeMaps[i].mppMode;
}

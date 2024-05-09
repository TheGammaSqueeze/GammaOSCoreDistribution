/*
 * Copyright 2023 Rockchip Electronics Co. LTD
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

#ifndef _ROCKIT_CODEC_PROFILES_HEAD_
#define _ROCKIT_CODEC_PROFILES_HEAD_

namespace android {

#define RT_PROFILE_UNKNOWN             -99

typedef enum _RTAACProfile {
    RT_PROFILE_AAC_MAIN                = 0,
    RT_PROFILE_AAC_LOW                 = 1,
    RT_PROFILE_AAC_SSR                 = 2,
    RT_PROFILE_AAC_LTP                 = 3,
    RT_PROFILE_AAC_HE                  = 4,
    RT_PROFILE_AAC_LD                  = 22,
    RT_PROFILE_AAC_HE_V2               = 28,
    RT_PROFILE_AAC_ELD                 = 38,
} RTAACProfile;

typedef enum _RTMPEG2Profile {
    RT_PROFILE_MPEG2_422               = 0,
    RT_PROFILE_MPEG2_HIGH              = 1,
    RT_PROFILE_MPEG2_SS                = 2,
    RT_PROFILE_MPEG2_SNR_SCALABLE      = 3,
    RT_PROFILE_MPEG2_MAIN              = 4,
    RT_PROFILE_MPEG2_SIMPLE            = 5,
} RTMPEG2Profile;

typedef enum _RTH264Profile {
    RT_PROFILE_H264_CAVLC_444          = 44,   // YUV 4:4:4/14 "CAVLC 4:4:4"
    RT_PROFILE_H264_BASELINE           = 66,   // YUV 4:2:0/8  "Baseline"
    RT_PROFILE_H264_MAIN               = 77,   // YUV 4:2:0/8  "Main"
    RT_PROFILE_H264_EXTENDED           = 88,   // YUV 4:2:0/8  "Extended"
    RT_PROFILE_H264_HIGH               = 100,  // YUV 4:2:0/8  "High"
    RT_PROFILE_H264_HIGH_10            = 110,  // YUV 4:2:0/10 "High 10"
    RT_PROFILE_H264_MVC_HIGH           = 118,  // YUV 4:2:0/8  "Multiview High"
    RT_PROFILE_H264_HIGH_422           = 122,  // YUV 4:2:2/10 "High 4:2:2"
    RT_PROFILE_H264_STEREO_HIGH        = 128,  // YUV 4:2:0/8  "Stereo High"
    RT_PROFILE_H264_HIGH_444           = 144,  // YUV 4:4:4/14 "High 4:4:4"
} RTH264Profile;

typedef enum _RTVP9Profile {
    RT_PROFILE_VP9_0                   = 0,
    RT_PROFILE_VP9_1                   = 1,
    RT_PROFILE_VP9_2                   = 2,
    RT_PROFILE_VP9_3                   = 3,
} RTVP9Profile;

typedef enum _RTHEVCProfile {
    RT_PROFILE_HEVC_MAIN               = 1,
    RT_PROFILE_HEVC_MAIN_10            = 2,
    RT_PROFILE_HEVC_MAIN_STILL_PICTURE = 3,
    RT_PROFILE_HEVC_HEVC_REXT          = 4,
} RTHEVCProfile;

typedef struct _RTCodecProfiles {
    int profile;
    const char *name;  // short name for the profile
} RTCodecProfiles;


class RTMediaProfiles {
public:
    static RTCodecProfiles* getSupportProfile(int rtCodecId);
};

}

#endif  // _ROCKIT_CODEC_PROFILES_HEAD_


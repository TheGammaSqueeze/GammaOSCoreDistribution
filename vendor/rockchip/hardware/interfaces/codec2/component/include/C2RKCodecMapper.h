/*
 * Copyright (C) 2022 Rockchip Electronics Co. LTD
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

#ifndef ANDROID_C2_RK_CODEC_MAPPER_H__
#define ANDROID_C2_RK_CODEC_MAPPER_H__

#include <stdio.h>

//!< AVC Profile IDC definitions
enum MppH264Profile {
    MPP_H264_BASELINE    = 66,   //!< YUV 4:2:0/8  "Baseline"
    MPP_H264_MAIN        = 77,   //!< YUV 4:2:0/8  "Main"
    MPP_H264_EXTENDED    = 88,   //!< YUV 4:2:0/8  "Extended"
    MPP_H264_HIGH        = 100,  //!< YUV 4:2:0/8  "High"
    MPP_H264_HIGH10      = 110,  //!< YUV 4:2:0/10 "High 10"
    MPP_H264_HIGH422     = 122,  //!< YUV 4:2:2/10 "High 4:2:2"
    MPP_H264_HIGH444     = 244,  //!< YUV 4:4:4/14 "High 4:4:4"
    MPP_H264_MVC_HIGH    = 118,  //!< YUV 4:2:0/8  "Multiview High"
    MPP_H264_STEREO_HIGH = 128,  //!< YUV 4:2:0/8  "Stereo High"
};

//!< AVC Level IDC definitions
enum MppH264Level {
    MPP_H264_LEVEL1_0 = 10,
    MPP_H264_LEVEL1_B = 99,
    MPP_H264_LEVEL1_1 = 11,
    MPP_H264_LEVEL1_2 = 12,
    MPP_H264_LEVEL1_3 = 13,
    MPP_H264_LEVEL2_0 = 20,
    MPP_H264_LEVEL2_1 = 21,
    MPP_H264_LEVEL2_2 = 22,
    MPP_H264_LEVEL3_0 = 30,
    MPP_H264_LEVEL3_1 = 31,
    MPP_H264_LEVEL3_2 = 32,
    MPP_H264_LEVEL4_0 = 40,
    MPP_H264_LEVEL4_1 = 41,
    MPP_H264_LEVEL4_2 = 42,
    MPP_H264_LEVEL5_0 = 50,
    MPP_H264_LEVEL5_1 = 51,
    MPP_H264_LEVEL5_2 = 52,
    MPP_H264_LEVEL6_0 = 60,
    MPP_H264_LEVEL6_1 = 61,
    MPP_H264_LEVEL6_2 = 62,
};

//!< HEVC Profile IDC definitions
enum MppH265Profile {
    MPP_PROFILE_HEVC_MAIN               = 1,
    MPP_PROFILE_HEVC_MAIN_10            = 2,
    MPP_PROFILE_HEVC_MAIN_STILL_PICTURE = 3,
};

//!< HEVC level IDC definitions
enum MppH265Level {
    MPP_H265_LEVEL_NONE = 0,
    MPP_H265_LEVEL1 = 30,
    MPP_H265_LEVEL2 = 60,
    MPP_H265_LEVEL2_1 = 63,
    MPP_H265_LEVEL3 = 90,
    MPP_H265_LEVEL3_1 = 93,
    MPP_H265_LEVEL4 = 120,
    MPP_H265_LEVEL4_1 = 123,
    MPP_H265_LEVEL5 = 150,
    MPP_H265_LEVEL5_1 = 153,
    MPP_H265_LEVEL5_2 = 156,
    MPP_H265_LEVEL6 = 180,
    MPP_H265_LEVEL6_1 = 183,
    MPP_H265_LEVEL6_2 = 186,
    MPP_H265_LEVEL8_5 = 255,
};

const char *toStr_Profile(uint32_t i, uint32_t coding);
const char *toStr_Level(uint32_t i, uint32_t coding);
const char *toStr_BitrateMode(uint32_t i);

class C2RKCodecMapper {
public:
    /* profile level mapper */
    static uint32_t getMppH264Profile(uint32_t profile, bool c2Type);
    static uint32_t getMppH264Level(uint32_t level, bool c2Type);
    static uint32_t getMppH265Profile(uint32_t profile);
    static uint32_t getMppH265Level(uint32_t level);

    /* bitrate mode mapper */
    static uint32_t getMppBitrateMode(int32_t mode, bool c2Type);
};

#endif  // ANDROID_C2_RK_CODEC_MAPPER_H__

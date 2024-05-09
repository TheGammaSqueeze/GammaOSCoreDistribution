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

#define LOG_TAG "RTCodecProfiles"

#include <utils/Log.h>
#include "RTCodecProfiles.h"
#include "RTLibDefine.h"

namespace android {

static RTCodecProfiles sAACProfiles[] = {
    { RT_PROFILE_AAC_LOW,                "LC"       },
    { RT_PROFILE_AAC_SSR,                "SSR"      },
    { RT_PROFILE_AAC_LTP,                "LTP"      },
    { RT_PROFILE_AAC_HE,                 "HE-AAC"   },
    { RT_PROFILE_AAC_HE_V2,              "HE-AACv2" },
    { RT_PROFILE_AAC_LD,                 "LD"       },
    { RT_PROFILE_AAC_ELD,                "ELD"      },
    { RT_PROFILE_UNKNOWN,                "UNKNOWN"  },
};

static RTCodecProfiles sH264Profiles[] = {
    { RT_PROFILE_H264_BASELINE,          "Baseline"    },
    { RT_PROFILE_H264_MAIN,              "Main"        },
    { RT_PROFILE_H264_HIGH,              "High"        },
    { RT_PROFILE_H264_HIGH_10,           "High 10"     },
    { RT_PROFILE_H264_HIGH_422,          "High 4:2:2"  },
    { RT_PROFILE_H264_HIGH_444,          "CAVLC 4:4:4" },
    { RT_PROFILE_UNKNOWN,                "UNKNOWN"     },
};

static RTCodecProfiles sHEVCProfiles[] = {
    { RT_PROFILE_HEVC_MAIN,               "Main"               },
    { RT_PROFILE_HEVC_MAIN_10,            "Main 10"            },
    { RT_PROFILE_HEVC_MAIN_STILL_PICTURE, "Main Still Picture" },
    { RT_PROFILE_HEVC_HEVC_REXT,          "Rext"               },
    { RT_PROFILE_UNKNOWN,                 "UNKNOWN"            },
};

static RTCodecProfiles sVP9Profiles[] = {
    { RT_PROFILE_VP9_0,                   "Profile 0" },
    { RT_PROFILE_VP9_1,                   "Profile 1" },
    { RT_PROFILE_VP9_2,                   "Profile 2" },
    { RT_PROFILE_VP9_3,                   "Profile 3" },
    { RT_PROFILE_UNKNOWN,                 "UNKNOWN"   },
};

RTCodecProfiles* RTMediaProfiles::getSupportProfile(int rtCodecId) {
    RTCodecProfiles *profiles = NULL;
    switch (rtCodecId) {
        case RT_VIDEO_ID_AVC:
            profiles = sH264Profiles;
            break;
        case RT_VIDEO_ID_HEVC:
            profiles = sHEVCProfiles;
            break;
        case RT_VIDEO_ID_VP9:
            profiles = sVP9Profiles;
            break;
        case RT_AUDIO_ID_AAC:
            profiles = sAACProfiles;
            break;
        default:  // add more
            ALOGD("not find profiles, codec = 0x%x", rtCodecId);
            break;
    }

    return profiles;
}

}

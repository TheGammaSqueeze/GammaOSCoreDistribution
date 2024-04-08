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
 */


#define LOG_TAG "RkAudioSettingUtils"

#include "system/audio-hal-enums.h"
#include "RkAudioSettingUtils.h"
#include "audio_hw_hdmi.h"

namespace android {

#define RK_ARRAY_ELEMS(a) (sizeof(a) / sizeof((a)[0]))

#define FIND_ENTRY_VALUE(MAP, INPUT, KEY)                               \
    do {                                                                \
        for (int i = 0; i < RK_ARRAY_ELEMS(MAP); i++) {                 \
            if (INPUT == MAP[i].KEY)                                    \
                return &MAP[i];                                         \
        }                                                               \
        return NULL;                                                    \
    } while (0)

const static AudioFormatMaps gFormatsMaps[] = {
    { AUDIO_FORMAT_AC3,          AUDIO_SETTING_FORMAT_AC3,      HDMI_AUDIO_AC3,     "AC3"},
    { AUDIO_FORMAT_E_AC3,        AUDIO_SETTING_FORMAT_EAC3,     HDMI_AUDIO_E_AC3,   "EAC3"},
//   { AUDIO_FORMAT_E_AC3_JOC,    AUDIO_SETTING_FORMAT_EAC3,     HDMI_AUDIO_E_AC3,   "EAC3-JOC"},
    { AUDIO_FORMAT_DTS,          AUDIO_SETTING_FORMAT_DTS,      HDMI_AUDIO_DTS,     "DTS"},
    { AUDIO_FORMAT_DTS_HD,       AUDIO_SETTING_FORMAT_DTSHD,    HDMI_AUDIO_DTS_HD,  "DTSHD"},
    { AUDIO_FORMAT_DOLBY_TRUEHD, AUDIO_SETTING_FORMAT_TRUEHD,   HDMI_AUDIO_MLP,     "TRUEHD"},
};

int RkAudioSettingUtils::getFormatsArraySize() {
    return RK_ARRAY_ELEMS(gFormatsMaps);
}

const AudioFormatMaps* RkAudioSettingUtils::getFormatMapByAndroidFormat(int format) {
    FIND_ENTRY_VALUE(gFormatsMaps, format, androidformat);
}

const AudioFormatMaps* RkAudioSettingUtils::getFormatMapBySettingFormat(int format) {
    FIND_ENTRY_VALUE(gFormatsMaps, format, settingFormat);
}

const AudioFormatMaps* RkAudioSettingUtils::getFormatMapByHdmiFormat(int format) {
    FIND_ENTRY_VALUE(gFormatsMaps, format, hdmiFormat);
}

const AudioFormatMaps* RkAudioSettingUtils::getFormatMapByName(char* name) {
    for (int i = 0; i < RK_ARRAY_ELEMS(gFormatsMaps); i++) {
        const AudioFormatMaps *map = &gFormatsMaps[i];
        if (!strcmp(map->name, name))
            return map;
    }

    return NULL;
}


const AudioFormatMaps* RkAudioSettingUtils::getFormatMapByIndex(int index) {
    return &gFormatsMaps[index];
}


}

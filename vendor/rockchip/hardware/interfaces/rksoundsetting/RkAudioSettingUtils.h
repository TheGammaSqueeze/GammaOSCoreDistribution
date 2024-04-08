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

#ifndef _RK_AUDIO_SETTING_UTILS_H_
#define _RK_AUDIO_SETTING_UTILS_H_

namespace android {

typedef struct _RKAudioFormatMaps {
    int androidformat;    // format value defined in android
    int settingFormat;    // format value settted by setting app
    int hdmiFormat;       // format value get/set by hdmi edid
    char name[16];
} AudioFormatMaps;

enum {
    AUDIO_DEVICE_DECODE = 0,
    AUDIO_DEVICE_HDMI_BITSTREAM = 1,
    AUDIO_DEVICE_SPDIF_PASSTHROUGH = 2,
};

enum {
    AUDIO_DECODE_MODE_PCM       = 0,
    AUDIO_DECODE_MODE_MULTI_PCM = 1,
    AUDIO_DECODE_MODE_BUTT      = 2,
};

enum {
    AUDIO_BITSTREAM_MODE_AUTO   = 0,
    AUDIO_BITSTREAM_MODE_MANUAL = 1,
    AUDIO_BITSTREAM_MODE_BUTT   = 2,
};

enum {
    AUDIO_SETTING_UNSUPPORT = 0,
    AUDIO_SETTING_SUPPORT   = 1,
};

enum {
    AUDIO_FORMAT_INSERT = 0,
    AUDIO_FORMAT_DELETE = 1,
};

typedef enum rkAUDIO_SETTING_FORMAT_E {
    AUDIO_SETTING_FORMAT_AC3  = 0,
    AUDIO_SETTING_FORMAT_EAC3,
    AUDIO_SETTING_FORMAT_DTS,
    AUDIO_SETTING_FORMAT_TRUEHD,
    AUDIO_SETTING_FORMAT_DTSHD,
    AUDIO_SETTING_FORMAT_MLP,
    AUDIO_SETTING_FORMAT_BUTT,
} AUDIO_SETTING_FORMAT_E;

class RkAudioSettingUtils {
public:
    static int getFormatsArraySize();
    static const AudioFormatMaps* getFormatMapByIndex(int index);
    static const AudioFormatMaps* getFormatMapByAndroidFormat(int format);
    static const AudioFormatMaps* getFormatMapBySettingFormat(int format);
    static const AudioFormatMaps* getFormatMapByHdmiFormat(int format);
    static const AudioFormatMaps* getFormatMapByName(char* name);
};

}

#endif  // _RK_AUDIO_SETTING_UTILS_H_
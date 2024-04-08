/*
 * Copyright 2019 Rockchip Electronics Co. LTD
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
 * author: hh@rock-chips.com
 * date: 2023/03/10
 * module: RKAudioSetting.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "RkAudioSettingManager"

#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <log/log.h>
#include "RkAudioXmlParser.h"
#include "RkAudioSettingManager.h"

namespace android {

#define RK_AUDIO_SETTING_CONFIG_FILE "/data/system/rt_audio_config.xml"
#define RK_AUDIO_SETTING_SYSTEM_FILE "/system/etc/rt_audio_config.xml"

RkAudioSettingManager::RkAudioSettingManager() {
    mParser = new RKAudioXmlParser();
}

RkAudioSettingManager::~RkAudioSettingManager() {
    if (mParser) {
        delete mParser;
        mParser = NULL;
    }
}

int RkAudioSettingManager::copyFile() {
    if (access(RK_AUDIO_SETTING_CONFIG_FILE, F_OK) < 0) {
        // copy /system/etc/rt_audio_config.xml to /data/system/rt_audio_config.xml
        // because there is no permission to modify /system/etc/rt_audio_config.xml.
        if (access(RK_AUDIO_SETTING_SYSTEM_FILE, F_OK) == 0) {
            char buff[1024];
            FILE *fin = fopen(RK_AUDIO_SETTING_SYSTEM_FILE,  "r");
            if (fin == NULL) {
                ALOGE("%s,%d, open %s fail, %s",__FUNCTION__,__LINE__, RK_AUDIO_SETTING_SYSTEM_FILE, strerror(errno));
            }

            FILE *fout = fopen(RK_AUDIO_SETTING_CONFIG_FILE, "w");
            if (fout == NULL) {
                ALOGE("%s:%d, open %s fail, %s",__FUNCTION__,__LINE__, RK_AUDIO_SETTING_CONFIG_FILE, strerror(errno));
            }
            if(fout && fin){
                while (1) {
                    int size = fread(buff, 1, 1024, fin);
                    if (size <= 0)
                        break;

                    fwrite(buff, size, 1, fout);
                }
            }

            if (fout != NULL) {
                fclose(fout);
                fout = NULL;
            }

            if (fin != NULL) {
                fclose(fin);
                fin = NULL;
            }

            // for avoid datas cached
            sync();

            // chmod mode
            chmod(RK_AUDIO_SETTING_CONFIG_FILE, S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH);
        }
    }

    return 0;
}

int RkAudioSettingManager::init() {
    int err = 0;

    // if /data/system/rt_audio_config.xml is not exist, copy it from /system/etc
    copyFile();

    // parser configs
    ALOGD("load XML file(%s)", RK_AUDIO_SETTING_CONFIG_FILE);
    if (access(RK_AUDIO_SETTING_CONFIG_FILE, F_OK) >= 0) {
        // load /data/system/rt_audio_config.xml
        err = mParser->load(RK_AUDIO_SETTING_CONFIG_FILE);
        // if set hdmi auto mode, get the formats from edid of hdmi
        int device = mParser->getDevice();
        int mode   = mParser->getMode(device);
        if ((device == AUDIO_DEVICE_HDMI_BITSTREAM) &&
                           mode == AUDIO_BITSTREAM_MODE_AUTO) {
            updataFormatByHdmiEdid();
        }
    } else {
        ALOGD("not find XML file %s", RK_AUDIO_SETTING_CONFIG_FILE);
        err = -1;
    }
    return err;
}

/*<sound>
 *   <decode setting="yes">
 *     ......
 *   </decode>
 *   <bitstream setting="no">
 *      ...
 *     <devices>
 *         <device>hdmi</device>
 *     <devices>
 *     ......
 *   </bitstream>
 *
 *
 *  apk interface : query audio device
 *
 *  [param] :
 *   device :
 *       0 : decode
 *       1 : hdmi bitstream,
 *       2 : spdif passthrough
 *
 *  [return] :
 *       1 : audio device set
 *       0 : audio device not set
*/
int RkAudioSettingManager::checkDevice(int device) {
    int setting = mParser->getDevice();
    return (device == setting)? 1 : 0;
}

/*  apk interface : set audio device
 *
 *  [params] :
 *   device :
 *       0 : decode
 *       1 : hdmi bitstream,
 *       2 : spdif passthrough
 *
 *  [return] : void
*/
void RkAudioSettingManager::setDevice(int device) {
    ALOGV("%s:%d: device = %d",  __FUNCTION__, __LINE__, device);
    mParser->setDevice(device);
}

/*  apk interface : set format
 *
 *  [param] :
 *   device :
 *       0 : decode
 *       1 : hdmi bitstream,
 *       2 : spdif passthrough
 *   option :
 *       0 : insert format
 *       1 : delete format
 *   format : audio format (e.g : AC3/EAC3/TRUEHD/DTSHD/DTS/MLP)
 *
 *  [return] : void
*/
void RkAudioSettingManager::setFormat(int device, int option, const char *format) {
    (void)device;
    ALOGV("%s:%d: device = %d, option = %d, format = %s",
        __FUNCTION__, __LINE__, device, option, format);
    const AudioFormatMaps *map = RkAudioSettingUtils::getFormatMapByName((char*)format);
    if (map == NULL) {
        ALOGE("%s: name = %s not support", __FUNCTION__, format);
        return;
    }

    if (option == AUDIO_FORMAT_INSERT) {
        mParser->insertFormat((char*)format, map->settingFormat);
    } else if (option == AUDIO_FORMAT_DELETE) {
        mParser->deleteFormat((char*)format, map->settingFormat);
    } else {
        ALOGE("not support set format option(%d)", option);
    }
}

/*  apk interface : check format is support?
 *
 *  [param] :
 *   device :
 *       0 : decode
 *       1 : hdmi bitstream,
 *       2 : spdif passthrough
 *   format : audio format (AC3/EAC3/TRUEHD/DTSHD/DTS/MLP)
 *
 *  [return] :
 *       0 : query format unsupport
 *       1 : query format support
*/
int RkAudioSettingManager::getFormat(int device, const char *format) {
    const AudioFormatMaps *map = RkAudioSettingUtils::getFormatMapByName((char*)format);
    if (map == NULL) {
        ALOGE("%s: name = %s not support", __FUNCTION__, format);
        return AUDIO_SETTING_UNSUPPORT;
    }

    int ret = mParser->checkFormatSupport(device, map->settingFormat);
    ALOGV("%s:%d: device = %d, format = %s %s",
        __FUNCTION__, __LINE__, device, format, ret ? "support": "unsupport");

    return ret;
}

/*  apk interface : set mode
 *
 *  [param] :
 *   device :
 *       0 : decode
 *       1 : hdmi bitstream
 *   mode :
 *       0 : pcm (when device == 0) / auto (when device == 1)
 *       1 : multi_pcm (when device == 0) / manual (when device == 1)
 *
 *  [return] : void
 */
void RkAudioSettingManager::setMode(int device, int mode) {
    ALOGV("%s:%d: device = %d, mode = %d", __FUNCTION__, __LINE__, device, mode);
    mParser->setMode(device, mode);
    if ((device == AUDIO_DEVICE_HDMI_BITSTREAM) && (mode == AUDIO_BITSTREAM_MODE_AUTO)) {
        updataFormatByHdmiEdid();
    }
}

/*  apk interface : get mode
 *
 *  [param] :
 *   device :
 *       0 : decode
 *       1 : hdmi bitstream
 *
 *  [return] :
 *       0 : pcm (when device == 0) / auto (when device == 1)
 *       1 : multi_pcm (when device == 0) / manual (when device == 1)
 *
 */
int RkAudioSettingManager::getMode(int device) {
    return mParser->getMode(device);
}

/*  1. parse hdmi edid information HDMI_EDID_NODE, get hdmi support information of audio.
 *  2. according to audio format of edid info which parsed, updata XML bitstream formats when
 *     select "auto" mode.
 */
void RkAudioSettingManager::updataFormatByHdmiEdid() {
    int i = 0;
    struct hdmi_audio_infors hdmi_edid;
    int support[AUDIO_SETTING_FORMAT_BUTT] = {0};
    int size = RkAudioSettingUtils::getFormatsArraySize();

    // get bitstream mode of hdmi
    int mode = getMode(AUDIO_DEVICE_HDMI_BITSTREAM);
    int device = mParser->getDevice();

    // only udpate support formats from hdmi's edid when setting mode is hdmi auto
    if (mode == AUDIO_BITSTREAM_MODE_MANUAL || device != AUDIO_DEVICE_HDMI_BITSTREAM) {
        ALOGV("%s:%d, mode = %d, device = %d", __FUNCTION__, __LINE__, mode, device);
        return;
    }
    
    init_hdmi_audio(&hdmi_edid);
    if (parse_hdmi_audio(&hdmi_edid, 0) >= 0) {
        for (i = 0; i < size; i++) {
            const AudioFormatMaps* maps = RkAudioSettingUtils::getFormatMapByIndex(i);
            if (is_support_format(&hdmi_edid, maps->hdmiFormat)) {
                support[maps->settingFormat] = 1;
            }
        }
    }
    destory_hdmi_audio(&hdmi_edid);

    // clear all format
    mParser->clearFormats(AUDIO_DEVICE_HDMI_BITSTREAM);
    for (i = 0; i < size; i++) {
        const AudioFormatMaps* maps = RkAudioSettingUtils::getFormatMapByIndex(i);
        if (support[maps->settingFormat]) {
            setFormat(AUDIO_DEVICE_HDMI_BITSTREAM, AUDIO_FORMAT_INSERT, maps->name);
        } else {
            setFormat(AUDIO_DEVICE_HDMI_BITSTREAM, AUDIO_FORMAT_DELETE, maps->name);
        }
    }
}

}

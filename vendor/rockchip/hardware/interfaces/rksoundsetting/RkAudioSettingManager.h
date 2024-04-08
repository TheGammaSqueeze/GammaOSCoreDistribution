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
 * author: shika.zhou@rock-chips.com
 * date: 2019/11/19
 * module: RkAudioSettingManager
 */

#ifndef RKSOUNDSETTING_RKAUDIOSETTINGMANAGER_H_
#define RKSOUNDSETTING_RKAUDIOSETTINGMANAGER_H_

#include "audio_hw_hdmi.h"
#include "RkAudioSettingUtils.h"

namespace android {
class RKAudioXmlParser;

class RkAudioSettingManager {
 public:
    RkAudioSettingManager();
    virtual ~RkAudioSettingManager();
    int init();
    int  checkDevice(int device);
    void setDevice(int device);
    void setFormat(int device, int option, const char *format);
    int getFormat(int device, const char *format);
    void setMode(int device, int mode);
    int getMode(int device);
    void updataFormatByHdmiEdid();

 protected:
    int  copyFile();

 private:
    RkAudioSettingManager(const RkAudioSettingManager &manager) = delete;
    RkAudioSettingManager& operator=(const RkAudioSettingManager &manager) = delete;

 private:
    RKAudioXmlParser  *mParser;
};

}

#endif  //  RKSOUNDSETTING_RKAUDIOSETTINGMANAGER_H_


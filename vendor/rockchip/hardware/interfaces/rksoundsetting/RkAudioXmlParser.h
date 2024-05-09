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
 * author: hh@rock-chips.com
 * date: 2023/03/10
 * module: RKAudioSetting
 */

#ifndef RK_AUDIO_SETTING_XML_PARSER_H
#define RK_AUDIO_SETTING_XML_PARSER_H

#include "tinyxml2.h"
#include "RkAudioSettingUtils.h"

namespace android {
using namespace tinyxml2;

class RKAudioXmlParser {
public:
    RKAudioXmlParser();
    ~RKAudioXmlParser();
    int  load(const char *path);
    int  readXml();
    int  getDevice();
    int  setDevice(int device);
    int  insertFormat(char *name, int settingFormat);
    int  deleteFormat(char *name, int settingFormat);
    int  checkFormatSupport(int device, int settingFormat);

    int  setMode(int device, int mode);
    int  getMode(int device);

    int  clearFormats(int device);

    void dump();

private:
    bool isEnable(XMLElement *pEle);
    bool isFormatSupport(char* format);
    void resetFormats();
    void insertXmlFormat( char *name);
    void deleteXmlFormat(char *name);
    void updateXmlMode(int device, int mode);
    void updateXmlDevice(XMLElement *pNodeEle, int device);
    int  readBitstreamMode(XMLElement *pBitstream);
    void readBitstreamFormats(XMLElement *pBitstream);
    void updateElement(XMLElement *pEle, const char *value);
    void clearXmlFormats();
    void saveFile();

    RKAudioXmlParser(const RKAudioXmlParser &parser) = delete;
    RKAudioXmlParser& operator=(const RKAudioXmlParser &parser) = delete;

private:
    XMLDocument *mXmlDoc;
    int  mDevice; // decode mode or hdmi or spdif passthrough
    int  mMode;   // bitsteam mode, auto or manual
    int  mFormat[AUDIO_SETTING_FORMAT_BUTT];
    char *mPath;
};

} // namespace android

#endif  // RK_AUDIO_SETTING_XML_PARSER_H


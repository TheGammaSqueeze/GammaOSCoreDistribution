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

//#define LOG_NDEBUG 0
#define LOG_TAG "RkAudioXmlParser"

#include <log/log.h>
#include "RkAudioXmlParser.h"

namespace android {

//#############audio settting configs##############
//<sound>
//    <decode setting="yes">
//       ......
//    </decode>
//    <bitstream setting="yes">       # yes means bistream enable, no means bistream disable
//        <mode>auto</mode>           # auto means get formats by edid of hdmi
//        <devices>
//            <device>hdmi</device>   # passthrough device(spdif or hdmi)
//        </devices>
//        <formats>
//            <format>AC3</format>    # formats support to bistream
//            <format>DTS</format>
//            ......
//        </formats>
//    </bitstream>
//</sound>
//##################################################

// string tag in setting xml
#define ROOT         "sound"
#define DECODE       "decode"
#define BITSTREAM    "bitstream"
#define MODE         "mode"
#define SETTING      "setting"
#define DEVICES      "devices"
#define DEVICE       "device"
#define FORMATS      "formats"
#define FORMAT       "format"
#define SPEAKER      "speaker"
#define HDMI         "hdmi"
#define SPDIF        "spdif"
#define PCM          "pcm"
#define PCM          "pcm"
#define AUTO         "auto"
#define MANUAL       "manual"
#define ENABLE       "yes"
#define DISABLE      "no"

//formats tag in setting xml
#define AC3          "AC3"
#define EAC3         "EAC3"
#define EAC3_JOC     "EAC3-JOC"
#define TRUEHD       "TRUEHD"
#define MLP          "MLP"
#define DTS          "DTS"
#define DTSHD        "DTSHD"

RKAudioXmlParser::RKAudioXmlParser() {
    mXmlDoc = new XMLDocument();
    mPath   = new char[256];
    mDevice = AUDIO_DEVICE_DECODE;
    mMode   = AUDIO_BITSTREAM_MODE_BUTT;
    for (int i = 0; i < AUDIO_SETTING_FORMAT_BUTT; i++) {
        mFormat[i] = AUDIO_SETTING_UNSUPPORT;
    }
    memset(mPath, 0, 256);
}

RKAudioXmlParser::~RKAudioXmlParser() {
    if (mXmlDoc) {
        delete mXmlDoc;
        mXmlDoc = NULL;
    }

    if (mPath) {
        delete[] mPath;
        mPath = NULL;
    }
}

int RKAudioXmlParser::load(const char *path) {
    int ret = -1;
    if (path && access(path, F_OK) >= 0) {
        // load /data/system/rt_audio_config.xml
        if (XML_SUCCESS != mXmlDoc->LoadFile(path)) {
            ALOGD("load XML file error(%s)", mXmlDoc->ErrorStr());
            ret = -1;
        } else {
            memcpy(mPath, path, strlen(path));
            ret = readXml();
        }
    }

    return ret;
}

bool RKAudioXmlParser::isEnable(XMLElement *pEle) {
    bool ret = false;
    const XMLAttribute *mAttri = pEle->FirstAttribute();
    if (mAttri) {
        if (!strcmp(mAttri->Value(), ENABLE)) {
            ret = true;
        } else if (!strcmp(mAttri->Value(), DISABLE)) {
            ret = false;
        }
    }
    return ret;
}

bool RKAudioXmlParser::isFormatSupport(char* format) {
    bool support = false;
    int size = RkAudioSettingUtils::getFormatsArraySize();
    for (int i = 0; i < size; i++) {
        const AudioFormatMaps* maps = RkAudioSettingUtils::getFormatMapByIndex(i);
        if (!strcmp(maps->name, format)) {
            support = true;
            break;
        }
    }

    return support;
}

int RKAudioXmlParser::readXml() {
    XMLElement *pRoot      = NULL;
    XMLElement *pBitstream = NULL;
    XMLElement *pElement   = NULL;

    pRoot = mXmlDoc->RootElement();
    if (strcmp(pRoot->Value(), ROOT) != 0) {
        ALOGE("load AudioSetting XML error, not contain<sound!>");
        return -1;
    }

    // parser bitstream configs
    pBitstream = pRoot->FirstChildElement(BITSTREAM);
    if (pBitstream && isEnable(pBitstream)) {
        // get the device, spdif or hdmi
        pElement = pBitstream->FirstChildElement(DEVICES)->FirstChildElement(DEVICE);
        if (pElement == NULL) {
            ALOGE("%s: not find bistream node", __FUNCTION__);
            return -1;
        }

        if (!strcmp(pElement->GetText(), HDMI)) {
            mDevice = AUDIO_DEVICE_HDMI_BITSTREAM;
        } else if (!strcmp(pElement->GetText(), SPDIF)) {
            mDevice = AUDIO_DEVICE_SPDIF_PASSTHROUGH;
        } else {
            ALOGE("%s: device = %s is not support", __FUNCTION__, pElement->GetText());
            return -1;
        }

        // read the bistream mode, auto or manual
        mMode = readBitstreamMode(pBitstream);
        // get support formats
        readBitstreamFormats(pBitstream);
    }

    return 0;
}

int RKAudioXmlParser::readBitstreamMode(XMLElement *pBitstream) {
    XMLElement *pMode = pBitstream->FirstChildElement(MODE);
    if (pMode) {
        if (!strcmp(pMode->GetText(), MANUAL)) {
            return AUDIO_BITSTREAM_MODE_MANUAL;
        } else if (!strcmp(pMode->GetText(), AUTO)) {
            return AUDIO_BITSTREAM_MODE_AUTO;
        }
    }

    return AUDIO_BITSTREAM_MODE_BUTT;
}

void RKAudioXmlParser::readBitstreamFormats(XMLElement *pBitstream) {
    XMLElement *pFormat = pBitstream->FirstChildElement(FORMATS)->FirstChildElement(FORMAT);
    while (pFormat) {
        int size = RkAudioSettingUtils::getFormatsArraySize();
        for (int i = 0; i < size; i++) {
            const AudioFormatMaps* maps = RkAudioSettingUtils::getFormatMapByIndex(i);
            if (!strcmp(maps->name, pFormat->GetText())) {
                mFormat[maps->settingFormat] = AUDIO_SETTING_SUPPORT;
                ALOGV("%s: add format = %s", __FUNCTION__, maps->name);
                break;
            }
        }

        pFormat = pFormat->NextSiblingElement();
    }
}

int RKAudioXmlParser::getDevice() {
    return mDevice;
}

void RKAudioXmlParser::resetFormats() {
    for (int i = 0; i < AUDIO_SETTING_FORMAT_BUTT; i++) {
        mFormat[i] = AUDIO_SETTING_UNSUPPORT;
    }
}

void RKAudioXmlParser::clearXmlFormats() {
    // clean all formats in xml
    int size = RkAudioSettingUtils::getFormatsArraySize();
    for (int i = 0; i < size; i++) {
        const AudioFormatMaps* maps = RkAudioSettingUtils::getFormatMapByIndex(i);
        if (maps) {
            ALOGV("%s: delete format = %s", __FUNCTION__, maps->name);
            deleteXmlFormat((char *)maps->name);
        }
    }
    saveFile();
}

int RKAudioXmlParser::setDevice(int device) {
    if (device == mDevice)
        return 0;

    mDevice = device;
    resetFormats();
    XMLElement *pBitstream = mXmlDoc->RootElement()->FirstChildElement(BITSTREAM);
    if (device == AUDIO_DEVICE_DECODE) {  // set decode
        // set bistream disable to xml
        pBitstream->SetAttribute(SETTING, DISABLE);
        saveFile();
    } else { // set spdif/hdmi bitstream
        // set bistream enable to xml
        pBitstream->SetAttribute(SETTING, ENABLE);
        clearXmlFormats();
        updateXmlDevice(pBitstream, device);
        saveFile();
        // read format from xml
        if (device == AUDIO_DEVICE_HDMI_BITSTREAM) {
            // set auto mode default for hdmi
            setMode(AUDIO_DEVICE_HDMI_BITSTREAM, AUDIO_BITSTREAM_MODE_AUTO);
        } else {
            // set manual mode default for spdif
            setMode(AUDIO_DEVICE_SPDIF_PASSTHROUGH, AUDIO_BITSTREAM_MODE_MANUAL);
        }
    }

    return 0;
}

int RKAudioXmlParser::checkFormatSupport(int device, int format) {
    if (device != mDevice) {
        ALOGD("%s: query device = %d not equal the setting device = %d", __FUNCTION__, device, mDevice);
        return AUDIO_SETTING_UNSUPPORT;
    }

    return mFormat[format];
}

int RKAudioXmlParser::setMode(int device, int mode) {
    updateXmlMode(device, mode);
    saveFile();
    mMode = mode;

    return 0;
}

void RKAudioXmlParser::updateXmlDevice(XMLElement *pNodeEle, int device) {
    XMLElement *pDeviceEle = NULL;
    if (device == AUDIO_DEVICE_DECODE) {
    } else {
        pDeviceEle = pNodeEle->FirstChildElement(DEVICES)->FirstChildElement(DEVICE);
        if (pDeviceEle) {
            if (device == AUDIO_DEVICE_HDMI_BITSTREAM) {
                updateElement(pDeviceEle, HDMI);
            } else if (device == AUDIO_DEVICE_SPDIF_PASSTHROUGH) {
                updateElement(pDeviceEle, SPDIF);
            }
        }
    }
}

int RKAudioXmlParser::getMode(int device) {
    if (device == AUDIO_DEVICE_DECODE) {
        return AUDIO_DEVICE_DECODE;
    }

    return mMode;
}


void RKAudioXmlParser::saveFile() {
    if (mXmlDoc && mPath) {
        if (access(mPath, F_OK) >= 0) {
            mXmlDoc->SaveFile(mPath);
            sync();
        }
    }
}

int RKAudioXmlParser::insertFormat(char *name, int settingFormat) {
    // insert to list
    ALOGV("%s: name = %s, format = %d", __FUNCTION__, name, settingFormat);
    mFormat[settingFormat] = AUDIO_SETTING_SUPPORT;
    // insert to xml
    insertXmlFormat(name);
    saveFile();
    return 0;
}

int RKAudioXmlParser::deleteFormat(char *name, int settingFormat) {
    ALOGV("%s: name = %s, format = %d", __FUNCTION__, name, settingFormat);
    // delete from list
    mFormat[settingFormat] = AUDIO_SETTING_UNSUPPORT;
    // delete from xml
    deleteXmlFormat(name);
    saveFile();
    return 0;
}

void RKAudioXmlParser::insertXmlFormat(char *name) {
    XMLDocument *pDoc = mXmlDoc;
    XMLElement *pFormatsEle = NULL;
    if (mDevice == AUDIO_DEVICE_DECODE) {
        pFormatsEle = pDoc->RootElement()
                         ->FirstChildElement(DECODE)->FirstChildElement(FORMATS);
    } else {
        pFormatsEle = pDoc->RootElement()
                         ->FirstChildElement(BITSTREAM)->FirstChildElement(FORMATS);
    }

    if (pFormatsEle) {
        XMLElement *pFormatEle = pDoc->NewElement(FORMAT);
        pFormatEle->InsertEndChild(pDoc->NewText(name));
        pFormatsEle->InsertEndChild(pFormatEle);
    }
}

void RKAudioXmlParser::deleteXmlFormat(char *name) {
    XMLDocument *pDoc = mXmlDoc;
    XMLElement *pDevicesEle = NULL;
    XMLElement *pDeviceEle = NULL;
    if (mDevice == AUDIO_DEVICE_DECODE) {
        pDevicesEle = pDoc->RootElement()
                         ->FirstChildElement(DECODE)->FirstChildElement(FORMATS);
        pDeviceEle = pDevicesEle->FirstChildElement(FORMAT);
    } else {
        pDevicesEle = pDoc->RootElement()
                          ->FirstChildElement(BITSTREAM)->FirstChildElement(FORMATS);
        pDeviceEle = pDevicesEle->FirstChildElement(FORMAT);
    }

    while (pDeviceEle) {
        if (!strcmp(pDeviceEle->GetText(), name)) {
            pDevicesEle->DeleteChild(pDeviceEle);
            break;
        }

        pDeviceEle = pDeviceEle->NextSiblingElement();
    }
}

void RKAudioXmlParser::updateXmlMode(int device, int mode) {
    XMLDocument *pDoc = mXmlDoc;
    XMLElement *pModeEle = NULL;
    if (device == AUDIO_DEVICE_DECODE) {
        pModeEle = pDoc->RootElement()
                         ->FirstChildElement(DECODE)->FirstChildElement(MODE);
        // add codes here
    } else {
        pModeEle = pDoc->RootElement()
                         ->FirstChildElement(BITSTREAM)->FirstChildElement(MODE);
        if (pModeEle) {
            if (mode == AUDIO_BITSTREAM_MODE_AUTO) {
                updateElement(pModeEle, AUTO);
            } else if (mode == AUDIO_BITSTREAM_MODE_MANUAL) {
                updateElement(pModeEle, MANUAL);
            } else {
                ALOGE("not support mode(%d)", mode);
            }
        }
    }
}

void RKAudioXmlParser::updateElement(XMLElement *pEle, const char *value) {
    if (pEle) {
        if (strcmp(pEle->GetText(), value)) {
            pEle->SetText(value);
        }
    }
}

int RKAudioXmlParser::clearFormats(int device) {
    if (device == AUDIO_DEVICE_DECODE)
        return -1;

    resetFormats();
    clearXmlFormats();
    return 0;
}

void RKAudioXmlParser::dump() {
    if (mDevice == AUDIO_DEVICE_DECODE) {
        ALOGD("decode mode");
    } else if(mDevice == AUDIO_DEVICE_HDMI_BITSTREAM) {
        const char *mode = (mMode == AUDIO_BITSTREAM_MODE_AUTO) ? "auto" : "manual"; 
        ALOGD("hdmi passthrough %s mode",mode);
    } else if(mDevice == AUDIO_DEVICE_SPDIF_PASSTHROUGH) {
        ALOGD("spdif passthrough mode");
    }


    for (int i = 0; i < AUDIO_SETTING_FORMAT_BUTT; i++) {
        if (mFormat[i] == AUDIO_SETTING_SUPPORT) {
            const AudioFormatMaps* maps = RkAudioSettingUtils::getFormatMapBySettingFormat(i);
            if (maps != NULL) {
                ALOGD("support Format: %s", maps->name);
            }
        }
    }
}

}

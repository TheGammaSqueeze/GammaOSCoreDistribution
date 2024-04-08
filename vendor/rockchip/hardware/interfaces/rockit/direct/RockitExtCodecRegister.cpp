/*
 * Copyright 2022 Rockchip Electronics Co. LTD
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

//#define LOG_NDEBUG 0
#define LOG_TAG "RockitExtCodecRegister"

#include "string.h"
#include <utils/Log.h>
#include <utils/Mutex.h>
#include "RockitExtCodecRegister.h"
#include "RockitExtAdec.h"
#include "DummyDec.h"
#include "RTCodecProfiles.h"

namespace android {

static Mutex gCodecLock;
static bool gReisgter = false;

// this is demo to register dummy decoder to rockit
int registerDummyDec(registerDecoderFunc* func) {
    (void)func;
    // open here to register codec
#if 0
    RTAdecDecoder adecCtx;
    RT_RET ret = RT_OK;
    int32_t ps32Handle = -1;

    // define context of DummyDec codec
    memset(&adecCtx, 0, sizeof(RTAdecDecoder));
    adecCtx.enType = RT_AUDIO_ID_XXX;   // for example RT_AUDIO_ID_PCM_ALAW
    adecCtx.profiles = RTMediaProfiles::getSupportProfile(adecCtx.enType);
    // the name of register codec must start with "ext_"
    snprintf(reinterpret_cast<char*>(adecCtx.aszName), sizeof(adecCtx.aszName), "ext_dummy");
    adecCtx.pfnOpenDecoder  = DummyDec::open;
    adecCtx.pfnDecodeFrm    = DummyDec::decode;
    adecCtx.pfnGetFrmInfo   = DummyDec::getFrameInfo;
    adecCtx.pfnCloseDecoder = DummyDec::close;
    adecCtx.pfnResetDecoder = DummyDec::reset;
    // register to rockitx
    ret = func(&ps32Handle, &adecCtx);
    if (ret != RT_OK) {
        ALOGE("adec register decoder fail, ret = 0x%x", ret);
        return -1;
    }
#endif
    return 0;
}

int RockitExtCodecRegister::rockitRegisterCodec(registerDecoderFunc* func) {
    // only resigter once
    Mutex::Autolock autoLock(gCodecLock);
    if (func == NULL || gReisgter) {
        return 0;
    }

    gReisgter = true;
    // this is demo to register dummy to rockit
    registerDummyDec(func);

    // add codes here to register more codec

    return 0;
}

}


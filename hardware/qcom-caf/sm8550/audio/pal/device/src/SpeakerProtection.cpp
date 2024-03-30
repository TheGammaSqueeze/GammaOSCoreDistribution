/*
 * Copyright (c) 2020-2021, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022 - 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#define LOG_TAG "PAL: SpeakerProtection"

#include "SpeakerProtection.h"
#include "SessionAlsaUtils.h"
#include "kvh2xml.h"
#include <errno.h>
#include <agm/agm_api.h>

#include<fstream>
#include<sstream>

#ifndef PAL_SP_TEMP_PATH
#define PAL_SP_I_TEMP_PATH "/data/vendor/audio/audio_sp1.cal"
#define PAL_SP_II_TEMP_PATH "/data/vendor/audio/audio_sp2.cal"
#endif

#define PAL_SP_XMAX_TMAX_DATA_PATH "/data/vendor/audio/spkr_xmax_tmax.txt"
#define PAL_SP_XMAX_TMAX_LOG_PATH "/data/vendor/audio/log_spkr_xmax_tmax.cal"
#define FEEDBACK_MONO_1 "-mono-1"

#define MIN_SPKR_IDLE_SEC (60 * 30)
#define WAKEUP_MIN_IDLE_CHECK (1000 * 30)

#define SPKR_RIGHT_WSA_TEMP "SpkrRight WSA Temp"
#define SPKR_LEFT_WSA_TEMP "SpkrLeft WSA Temp"
#define SPKR2_RIGHT_WSA_TEMP "Spkr2Right WSA Temp"
#define SPKR2_LEFT_WSA_TEMP "Spkr2Left WSA Temp"

#define SPKR_RIGHT_WSA_DEV_NUM "SpkrRight WSA Get DevNum"
#define SPKR_LEFT_WSA_DEV_NUM "SpkrLeft WSA Get DevNum"
#define SPKR2_RIGHT_WSA_DEV_NUM "Spkr2Right WSA Get DevNum"
#define SPKR2_LEFT_WSA_DEV_NUM "Spkr2Left WSA Get DevNum"

#define SPKR_RIGHT_WSA_DC_DET "SpkrRight WSA PA Disable"
#define SPKR_LEFT_WSA_DC_DET "SpkrLeft WSA PA Disable"
#define SPKR2_RIGHT_WSA_DC_DET "Spkr2Right WSA PA Disable"
#define SPKR2_LEFT_WSA_DC_DET "Spkr2Left WSA PA Disable"

#define TZ_TEMP_MIN_THRESHOLD    (-30)
#define TZ_TEMP_MAX_THRESHOLD    (80)

/*Set safe temp value to 40C*/
#define SAFE_SPKR_TEMP 40
#define SAFE_SPKR_TEMP_Q6 (SAFE_SPKR_TEMP * (1 << 6))

#define MIN_RESISTANCE_SPKR_Q24 (2 * (1 << 24))

#define DEFAULT_PERIOD_SIZE 256
#define DEFAULT_PERIOD_COUNT 4

//TODO : remove this and add proper file
#define EVENT_ID_VI_CALIBRATION 0x0800119F

#define NORMAL_MODE 0
#define CALIBRATION_MODE 1
#define FACTORY_TEST_MODE 2
#define V_VALIDATION_MODE 3

#define CALIBRATION_STATUS_SUCCESS 4
#define CALIBRATION_STATUS_FAILURE 5

std::thread SpeakerProtection::mCalThread;
std::thread SpeakerProtection::XmaxTmaxLogThread;
std::condition_variable SpeakerProtection::cv;
std::mutex SpeakerProtection::cvMutex;
std::mutex SpeakerProtection::calibrationMutex;

bool SpeakerProtection::isSpkrInUse;
bool SpeakerProtection::calThrdCreated;
bool SpeakerProtection::isDynamicCalTriggered = false;
bool SpeakerProtection::startXmaxLogging = false;
struct timespec SpeakerProtection::spkrLastTimeUsed;
struct mixer *SpeakerProtection::virtMixer;
struct mixer *SpeakerProtection::hwMixer;
speaker_prot_cal_state SpeakerProtection::spkrCalState;
struct pcm * SpeakerProtection::rxPcm = NULL;
struct pcm * SpeakerProtection::txPcm = NULL;
struct pcm * SpeakerProtection::cps1Pcm = NULL;
struct pcm * SpeakerProtection::cps2Pcm = NULL;
struct param_id_sp_th_vi_calib_res_cfg_t * SpeakerProtection::callback_data;
int SpeakerProtection::numberOfChannels;
int SpeakerProtection::MaxCH;
uint32_t SpeakerProtection::source_miid;
uint32_t SpeakerProtection::vi_miid_II;
uint32_t SpeakerProtection::vi_miid_I;
struct pal_device_info SpeakerProtection::vi_device;
struct pal_device_info SpeakerProtection::cps_device;
int SpeakerProtection::calibrationCallbackStatus;
int SpeakerProtection::numberOfRequest;
bool SpeakerProtection::mDspCallbackRcvd;
std::shared_ptr<Device> SpeakerFeedback::obj = nullptr;
int SpeakerFeedback::numSpeaker;

std::string getDefaultSpkrTempCtrl(uint8_t spkr_pos)
{
    switch(spkr_pos)
    {
        case SPKR_LEFT:
            return std::string(SPKR_LEFT_WSA_TEMP);
        break;
        case SPKR_RIGHT:
            return std::string(SPKR_RIGHT_WSA_TEMP);
        break;
        case SPKR_REAR_LEFT:
            return std::string(SPKR2_LEFT_WSA_TEMP);
        break;
        case SPKR_REAR_RIGHT:
            return std::string(SPKR2_RIGHT_WSA_TEMP);
        break;
        default:
            return std::string(SPKR_RIGHT_WSA_TEMP);
    }
}

std::string SpeakerProtection::getDCDetSpkrCtrl(uint8_t spkr_pos, uint32_t miid)
{
    if (miid == vi_miid_I) {
        if (spkr_pos == CHANNELS_2)
           return std::string(SPKR_LEFT_WSA_DC_DET);
        else
           return std::string(SPKR_RIGHT_WSA_DC_DET);
    } else if (miid == vi_miid_II) {
        if (spkr_pos == CHANNELS_2)
           return std::string(SPKR2_LEFT_WSA_DC_DET);
        else
           return std::string(SPKR2_RIGHT_WSA_DC_DET);
    }
    return std::string(SPKR_RIGHT_WSA_DC_DET);
}

cps_reg_wr_values_t sp_cps_thrsh_values = {
    .value_normal_threshold = {0x8E003049, 0x1000304A, 0x0F003472},
    .value_lower_threshold_1 = {0x8F003049, 0xD000304A, 0x0F003472},
    .value_lower_threshold_2 = {0x8F003049, 0xD000304A, 0x18003472}
};

/* Function to check if Speaker is in use or not.
 * It returns the time as well for which speaker is not in use.
 */
bool SpeakerProtection::isSpeakerInUse(unsigned long *sec)
{
    struct timespec temp;
    PAL_DBG(LOG_TAG, "Enter");

    if (!sec) {
        PAL_ERR(LOG_TAG, "Improper argument");
        return false;
    }

    if (isSpkrInUse) {
        PAL_INFO(LOG_TAG, "Speaker in use");
        *sec = 0;
        return true;
    } else {
        PAL_INFO(LOG_TAG, "Speaker not in use");
        clock_gettime(CLOCK_BOOTTIME, &temp);
        *sec = temp.tv_sec - spkrLastTimeUsed.tv_sec;
    }

    PAL_DBG(LOG_TAG, "Idle time %ld", *sec);

    return false;
}

/* Function to set status of speaker */
void SpeakerProtection::spkrProtSetSpkrStatus(bool enable)
{
    PAL_DBG(LOG_TAG, "Enter");

    if (enable)
        isSpkrInUse = true;
    else {
        isSpkrInUse = false;
        clock_gettime(CLOCK_BOOTTIME, &spkrLastTimeUsed);
        PAL_INFO(LOG_TAG, "Speaker used last time %ld", spkrLastTimeUsed.tv_sec);
    }

    PAL_DBG(LOG_TAG, "Exit");
}

/* Wait function for WAKEUP_MIN_IDLE_CHECK  */
void SpeakerProtection::spkrCalibrateWait()
{
    std::unique_lock<std::mutex> lock(cvMutex);
    cv.wait_for(lock,
            std::chrono::milliseconds(WAKEUP_MIN_IDLE_CHECK));
}

// Callback from DSP for Ressistance value
void SpeakerProtection::handleSPCallback (uint64_t hdl __unused, uint32_t event_id,
                                            void *event_data, uint32_t event_size, uint32_t miid)
{
    param_id_sp_th_vi_calib_res_cfg_t *param_data = nullptr;
    param_id_sp_vi_spkr_diag_getpkt_param_t *diag_data = nullptr;
    std::unique_lock<std::mutex> EveLock(calibrationMutex);
    PAL_DBG(LOG_TAG, "Got event from DSP %x for miid %d", event_id, miid);

    switch(event_id) {
    case EVENT_ID_VI_CALIBRATION:
        // Received callback for Calibration state
        param_data = (param_id_sp_th_vi_calib_res_cfg_t *) event_data;
        PAL_DBG(LOG_TAG, "Calibration state %d", param_data->state);

        if (param_data->state == CALIBRATION_STATUS_SUCCESS) {
            PAL_DBG(LOG_TAG, "Calibration is successfull");
            callback_data = (param_id_sp_th_vi_calib_res_cfg_t *) calloc(1, event_size);
            if (!callback_data) {
                PAL_ERR(LOG_TAG, "Unable to allocate memory");
            } else {
                callback_data->num_ch = param_data->num_ch;
                callback_data->state = param_data->state;
                source_miid = miid;
                for (int i = 0; i < callback_data->num_ch; i++) {
                  callback_data->r0_cali_q24[i] = param_data->r0_cali_q24[i];
                }
            }
            mDspCallbackRcvd = true;
            calibrationCallbackStatus = CALIBRATION_STATUS_SUCCESS;
            cv.notify_all();
            cv.wait(EveLock);
        }
        else if (param_data->state == CALIBRATION_STATUS_FAILURE) {
            PAL_DBG(LOG_TAG, "Calibration is unsuccessfull");
            // Restart the calibration and abort current run.
            mDspCallbackRcvd = true;
            calibrationCallbackStatus = CALIBRATION_STATUS_FAILURE;
            cv.notify_all();
        }
        break;
    case EVENT_ID_SPv5_SPEAKER_DIAGNOSTICS:
        struct mixer_ctl *ctl;
        std::string mixer_ctl_name;

        diag_data = (param_id_sp_vi_spkr_diag_getpkt_param_t *) event_data;
        if (diag_data->num_ch == 1) {
                PAL_DBG(LOG_TAG, "Calibration state %d", diag_data->spkr_cond[0]);
                if (diag_data->spkr_cond[0] == SPKR_DC) {
                    mixer_ctl_name = getDCDetSpkrCtrl(CHANNELS_1, miid);
                    ctl = mixer_get_ctl_by_name(hwMixer, mixer_ctl_name.c_str());
                    if (!ctl) {
                         PAL_ERR(LOG_TAG, "invalid mixer control for DC : %s", mixer_ctl_name.c_str());
                         return;
                    }
                    mixer_ctl_set_value(ctl, 0, 1);
                    mixer_ctl_set_value(ctl, 0, 0);
                }
        } else {
                PAL_DBG(LOG_TAG, "Calibration state left %d, right %d", diag_data->spkr_cond[0],
                                  diag_data->spkr_cond[1]);
                 if (diag_data->spkr_cond[0] == SPKR_DC) {
                    mixer_ctl_name = getDCDetSpkrCtrl(CHANNELS_2, miid);
                    ctl = mixer_get_ctl_by_name(hwMixer, mixer_ctl_name.c_str());
                    if (!ctl) {
                        PAL_ERR(LOG_TAG, "invalid mixer control for DC : %s", mixer_ctl_name.c_str());
                        goto spkr_right;
                    }
                    mixer_ctl_set_value(ctl, 0, 1);
                    mixer_ctl_set_value(ctl, 0, 0);
                }
spkr_right:
                if (diag_data->spkr_cond[1] == SPKR_DC) {
                    mixer_ctl_name = getDCDetSpkrCtrl(CHANNELS_1, miid);
                    ctl = mixer_get_ctl_by_name(hwMixer, mixer_ctl_name.c_str());
                    if (!ctl) {
                        PAL_ERR(LOG_TAG, "invalid mixer control for DC : %s", mixer_ctl_name.c_str());
                        return;
                    }
                    mixer_ctl_set_value(ctl, 0, 1);
                    mixer_ctl_set_value(ctl, 0, 0);
                }
        }
    }
}

/* Function to get CPS device number */
int SpeakerProtection::getCpsDevNumber(std::string mixer_name)
{
    struct mixer_ctl *ctl;
    int status = 0;

    PAL_DBG(LOG_TAG, "Mixer control %s", mixer_name.c_str());
    PAL_DBG(LOG_TAG, "audio_hw_mixer %pK", hwMixer);

    ctl = mixer_get_ctl_by_name(hwMixer, mixer_name.c_str());
    if (!ctl) {
        PAL_ERR(LOG_TAG, "Invalid mixer control: %s\n", mixer_name.c_str());
        status = -ENOENT;
        return status;
    }

    status = mixer_ctl_get_value(ctl, 0);
    PAL_DBG(LOG_TAG, "Value for Mixer control %d", status);
    return status;
}

int SpeakerProtection::getSpeakerTemperature(int spkr_pos)
{
    struct mixer_ctl *ctl;
    std::string mixer_ctl_name;
    int status = 0;
    /**
     * It is assumed that for Mono speakers only right speaker will be there.
     * Thus we will get the Temperature just for right speaker.
     * TODO: Get the channel from RM.xml
     */
    PAL_DBG(LOG_TAG, "Enter Speaker Get Temperature %d", spkr_pos);
    mixer_ctl_name = rm->getSpkrTempCtrl(spkr_pos);
    if (mixer_ctl_name.empty()) {
        PAL_DBG(LOG_TAG, "Using default mixer control");
        mixer_ctl_name = getDefaultSpkrTempCtrl(spkr_pos);
    }

    PAL_DBG(LOG_TAG, "audio_mixer %pK", hwMixer);

    ctl = mixer_get_ctl_by_name(hwMixer, mixer_ctl_name.c_str());
    if (!ctl) {
        PAL_ERR(LOG_TAG, "Invalid mixer control: %s\n", mixer_ctl_name.c_str());
        status = -EINVAL;
        return status;
    }

    status = mixer_ctl_get_value(ctl, 0);

    PAL_DBG(LOG_TAG, "Exiting Speaker Get Temperature %d", status);

    return status;
}

void SpeakerProtection::disconnectFeandBe(std::vector<int> pcmDevIds,
                                         std::string backEndName) {

    std::ostringstream disconnectCtrlName;
    std::ostringstream disconnectCtrlNameBe;
    struct mixer_ctl *disconnectCtrl = NULL;
    struct mixer_ctl *beMetaDataMixerCtrl = nullptr;
    struct agmMetaData deviceMetaData(nullptr, 0);
    uint32_t devicePropId[] = { 0x08000010, 2, 0x2, 0x5 };
    std::vector <std::pair<int, int>> emptyKV;
    int ret = 0;

    emptyKV.clear();

    SessionAlsaUtils::getAgmMetaData(emptyKV, emptyKV,
                    (struct prop_data *)devicePropId, deviceMetaData);
    if (!deviceMetaData.size) {
        ret = -ENOMEM;
        PAL_ERR(LOG_TAG, "Error: %d, Device metadata is zero", ret);
        goto exit;
    }

    disconnectCtrlNameBe<< backEndName << " metadata";
    beMetaDataMixerCtrl = mixer_get_ctl_by_name(virtMixer, disconnectCtrlNameBe.str().data());
    if (!beMetaDataMixerCtrl) {
        ret = -EINVAL;
        PAL_ERR(LOG_TAG, "Error: %d, invalid mixer control %s", ret, backEndName.c_str());
        goto exit;
    }

    disconnectCtrlName << "PCM" << pcmDevIds.at(0) << " disconnect";
    disconnectCtrl = mixer_get_ctl_by_name(virtMixer, disconnectCtrlName.str().data());
    if (!disconnectCtrl) {
        ret = -EINVAL;
        PAL_ERR(LOG_TAG, "Error: %d, invalid mixer control: %s", ret, disconnectCtrlName.str().data());
        goto exit;
    }
    ret = mixer_ctl_set_enum_by_string(disconnectCtrl, backEndName.c_str());
    if (ret) {
        PAL_ERR(LOG_TAG, "Error: %d, Mixer control %s set with %s failed", ret,
        disconnectCtrlName.str().data(), backEndName.c_str());
    }

    if (deviceMetaData.size) {
        ret = mixer_ctl_set_array(beMetaDataMixerCtrl, (void *)deviceMetaData.buf,
                    deviceMetaData.size);
        free(deviceMetaData.buf);
        deviceMetaData.buf = nullptr;
    } else {
        PAL_ERR(LOG_TAG, "Error: %d, Device Metadata not cleaned up", ret);
        goto exit;
    }

exit:
    return;
}

int SpeakerProtection::spkrStartCalibration()
{
    FILE *fp = NULL;
    struct pal_device device, deviceRx;
    struct pal_channel_info ch_info;
    struct pal_device_info deviceRxSpkr;
    struct pal_stream_attributes sAttr;
    struct pcm_config config;
    struct mixer_ctl *connectCtrl = NULL;
    struct audio_route *audioRoute = NULL;
    struct agm_event_reg_cfg event_cfg;
    struct agmMetaData deviceMetaData(nullptr, 0);
    struct mixer_ctl *beMetaDataMixerCtrl = nullptr;
    int ret = 0, status = 0, dir = 0, i = 0, flags = 0, payload_size = 0;
    int eventCh = 0,SpkrTempIndex =0;
    uint32_t miid = 0;
    uint32_t tagid;
    char mSndDeviceName_rx[128] = {0};
    char mSndDeviceName_vi[128] = {0};
    uint8_t* payload = NULL;
    size_t payloadSize = 0;
    uint32_t devicePropId[] = {0x08000010, 1, 0x2};
    bool isTxStarted = false, isRxStarted = false;
    bool isTxFeandBeConnected = false, isRxFeandBeConnected = false;
    std::string backEndNameTx, backEndNameRx;
    std::vector <std::pair<int, int>> keyVector, calVector;
    std::vector<int> pcmDevIdsRx, pcmDevIdsTx;
    std::shared_ptr<ResourceManager> rm;
    std::ostringstream connectCtrlName;
    std::ostringstream connectCtrlNameRx;
    std::ostringstream connectCtrlNameBe;
    std::ostringstream connectCtrlNameBeVI;
    param_id_sp_vi_op_mode_cfg_t modeConfg;
    param_id_sp_vi_channel_map_cfg_t viChannelMapConfg;
    param_id_sp_op_mode_t spModeConfg;
    param_id_sp_ex_vi_mode_cfg_t viExModeConfg;
    session_callback sessionCb;

    std::unique_lock<std::mutex> calLock(calibrationMutex);

    memset(&device, 0, sizeof(device));
    memset(&deviceRx, 0, sizeof(deviceRx));
    memset(&sAttr, 0, sizeof(sAttr));
    memset(&config, 0, sizeof(config));
    memset(&modeConfg, 0, sizeof(modeConfg));
    memset(&viChannelMapConfg, 0, sizeof(viChannelMapConfg));
    memset(&spModeConfg, 0, sizeof(spModeConfg));
    memset(&viExModeConfg, 0, sizeof(viExModeConfg));

    sessionCb = handleSPCallback;
    PayloadBuilder* builder = new PayloadBuilder();

    keyVector.clear();
    calVector.clear();

    PAL_DBG(LOG_TAG, "Enter");

    if (customPayloadSize) {
        free(customPayload);
        customPayloadSize = 0;
        customPayload = NULL;
    }

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Error: %d Failed to get resource manager instance", -EINVAL);
        goto exit;
    }

    rm->getDeviceInfo(PAL_DEVICE_IN_VI_FEEDBACK, PAL_STREAM_PROXY, "", &vi_device);

    // Configure device attribute
    rm->getChannelMap(&(ch_info.ch_map[0]), vi_device.channels);
    switch (vi_device.channels) {
        case 1 :
            ch_info.channels = CHANNELS_1;
        break;
        case 2 :
            ch_info.channels = CHANNELS_2;
        break;
        case 4 :
            ch_info.channels = CHANNELS_4;
        break;
        default:
            PAL_DBG(LOG_TAG, "Unsupported channel. Set default as 2");
            ch_info.channels = CHANNELS_2;
        break;
    }

    device.config.ch_info = ch_info;
    device.config.sample_rate = vi_device.samplerate;
    device.config.bit_width = vi_device.bit_width;
    device.config.aud_fmt_id = rm->getAudioFmt(vi_device.bit_width);

    // Setup TX path
    ret = rm->getAudioRoute(&audioRoute);
    if (0 != ret) {
        PAL_ERR(LOG_TAG, "Failed to get the audio_route name status %d", ret);
        goto exit;
    }

    device.id = PAL_DEVICE_IN_VI_FEEDBACK;
    strlcpy(mSndDeviceName_vi, vi_device.sndDevName.c_str(), DEVICE_NAME_MAX_SIZE);
    PAL_DBG(LOG_TAG, "got the audio_route name %s", mSndDeviceName_vi);

    rm->getBackendName(device.id, backEndNameTx);
    if (!strlen(backEndNameTx.c_str())) {
        PAL_ERR(LOG_TAG, "Failed to obtain tx backend name for %d", device.id);
        goto exit;
    }

    ret = PayloadBuilder::getDeviceKV(device.id, keyVector);
    if (0 != ret) {
        PAL_ERR(LOG_TAG, "Failed to obtain device KV for %d", device.id);
        goto exit;
    }

    // Enable VI module
    if (numberOfChannels == CHANNELS_4) {
        switch(numberOfChannels) {
            case 1 :
                // TODO: check it from RM.xml for left or right configuration
                calVector.push_back(std::make_pair(SPK_PRO_VI_MAP, RIGHT_SPKR_WITH_DISABLED_2SP));
                calVector.push_back(std::make_pair(CHANNELS, CHANNELS_1));
            break;
            case 2 :
                calVector.push_back(std::make_pair(SPK_PRO_VI_MAP, STEREO_SPKR_WITH_DISABLED_2SP));
                calVector.push_back(std::make_pair(CHANNELS, CHANNELS_2));
            break;
            case 4 :
                calVector.push_back(std::make_pair(SPK_PRO_VI_MAP, STEREO_SPKR));
                calVector.push_back(std::make_pair(CHANNELS, CHANNELS_4));
            break;
            default :
                PAL_ERR(LOG_TAG, "Unsupported channel");
                goto exit;
        }
    } else if (numberOfChannels <= CHANNELS_2) {
        switch(numberOfChannels) {
            case 1:
                calVector.push_back(std::make_pair(SPK_PRO_VI_MAP, RIGHT_SPKR));
            break;
            case 2:
                calVector.push_back(std::make_pair(SPK_PRO_VI_MAP, STEREO_SPKR));
            break;
            default :
                PAL_ERR(LOG_TAG, "Unsupported channel");
                goto exit;
        }
    }

    SessionAlsaUtils::getAgmMetaData(keyVector, calVector,
                    (struct prop_data *)devicePropId, deviceMetaData);
    if (!deviceMetaData.size) {
        PAL_ERR(LOG_TAG, "VI device metadata is zero");
        ret = -ENOMEM;
        goto exit;
    }

    connectCtrlNameBeVI<< backEndNameTx << " metadata";
    beMetaDataMixerCtrl = mixer_get_ctl_by_name(virtMixer, connectCtrlNameBeVI.str().data());
    if (!beMetaDataMixerCtrl) {
        PAL_ERR(LOG_TAG, "invalid mixer control for VI : %s", backEndNameTx.c_str());
        ret = -EINVAL;
        goto exit;
    }

    if (deviceMetaData.size) {
        ret = mixer_ctl_set_array(beMetaDataMixerCtrl, (void *)deviceMetaData.buf,
                    deviceMetaData.size);
        free(deviceMetaData.buf);
        deviceMetaData.buf = nullptr;
    } else {
        PAL_ERR(LOG_TAG, "Device Metadata not set for TX path");
        ret = -EINVAL;
        goto exit;
    }

    ret = SessionAlsaUtils::setDeviceMediaConfig(rm, backEndNameTx, &device);
    if (ret) {
        PAL_ERR(LOG_TAG, "setDeviceMediaConfig for feedback device failed");
        goto exit;
    }

    sAttr.type = PAL_STREAM_LOW_LATENCY;
    sAttr.direction = PAL_AUDIO_INPUT_OUTPUT;
    dir = TX_HOSTLESS;
    pcmDevIdsTx = rm->allocateFrontEndIds(sAttr, dir);
    if (pcmDevIdsTx.size() == 0) {
        PAL_ERR(LOG_TAG, "allocateFrontEndIds failed");
        ret = -ENOSYS;
        goto exit;
    }

    connectCtrlName << "PCM" << pcmDevIdsTx.at(0) << " connect";
    connectCtrl = mixer_get_ctl_by_name(virtMixer, connectCtrlName.str().data());
    if (!connectCtrl) {
        PAL_ERR(LOG_TAG, "invalid mixer control: %s", connectCtrlName.str().data());
        goto free_fe;
    }
    ret = mixer_ctl_set_enum_by_string(connectCtrl, backEndNameTx.c_str());
    if (ret) {
        PAL_ERR(LOG_TAG, "Mixer control %s set with %s failed: %d",
        connectCtrlName.str().data(), backEndNameTx.c_str(), ret);
        goto free_fe;
    }

    isTxFeandBeConnected = true;

    config.rate = vi_device.samplerate;
    switch (vi_device.bit_width) {
        case 32 :
            config.format = PCM_FORMAT_S32_LE;
        break;
        case 24 :
            config.format = PCM_FORMAT_S24_LE;
        break;
        case 16:
            config.format = PCM_FORMAT_S16_LE;
        break;
        default:
            PAL_DBG(LOG_TAG, "Unsupported bit width. Set default as 16");
            config.format = PCM_FORMAT_S16_LE;
        break;
    }

    switch (vi_device.channels) {
        case 1 :
            config.channels = CHANNELS_1;
        break;
        case 2 :
            config.channels = CHANNELS_2;
        break;
        default:
            PAL_DBG(LOG_TAG, "Unsupported channel. Set default as 2");
            config.channels = CHANNELS_2;
        break;
    }
    config.period_size = DEFAULT_PERIOD_SIZE;
    config.period_count = DEFAULT_PERIOD_COUNT;
    config.start_threshold = 0;
    config.stop_threshold = INT_MAX;
    config.silence_threshold = 0;

    flags = PCM_IN;

    for (int ch = numberOfChannels; ch != 0; ch = ch >> 2) {
        // Setting the mode of VI module

        if (numberOfChannels == CHANNELS_4)
            modeConfg.num_speakers = MaxCH;
        else {
            modeConfg.num_speakers = numberOfChannels;
        }

        modeConfg.th_operation_mode = CALIBRATION_MODE;
        if (minIdleTime > 0 && minIdleTime < MIN_SPKR_IDLE_SEC) {
            // Quick calibration set
            modeConfg.th_quick_calib_flag = 1;
        }
        else
            modeConfg.th_quick_calib_flag = 0;

        if (ch == CHANNELS_4)
            tagid = MODULE_VI2;
        else
            tagid = MODULE_VI;

        ret = SessionAlsaUtils::getModuleInstanceId(virtMixer, pcmDevIdsTx.at(0),
                                                               backEndNameTx.c_str(),
                                                               tagid, &miid);
        if (0 != ret) {
            PAL_ERR(LOG_TAG, "Failed to get tag info %x, status = %d", tagid, ret);
            goto free_fe;
        }

        if (ch == CHANNELS_4)
            vi_miid_II = miid;
        else
            vi_miid_I = miid;

        builder->payloadSPConfig(&payload, &payloadSize, miid,
                        PARAM_ID_SP_VI_OP_MODE_CFG,(void *)&modeConfg);
        if (payloadSize) {
            ret = updateCustomPayload(payload, payloadSize);
            free(payload);
            if (0 != ret) {
                PAL_ERR(LOG_TAG,"updateCustomPayload Failed for VI_OP_MODE_CFG\n");
                // Fatal error as calibration mode will not be set
                goto free_fe;
            }
        }

        // Setting Channel Map configuration for VI module
        // TODO: Move this to ACDB
        if (numberOfChannels == CHANNELS_4)
            viChannelMapConfg.num_ch = MaxCH * 2;
        else
            viChannelMapConfg.num_ch = numberOfChannels * 2;
        payloadSize = 0;

        builder->payloadSPConfig(&payload, &payloadSize, miid,
                 PARAM_ID_SP_VI_CHANNEL_MAP_CFG,(void *)&viChannelMapConfg);
        if (payloadSize) {
            ret = updateCustomPayload(payload, payloadSize);
            free(payload);
            if (0 != ret) {
                PAL_ERR(LOG_TAG," updateCustomPayload Failed for CHANNEL_MAP_CFG\n");
                // Not a fatal error
                ret = 0;
            }
        }

        // Setting Excursion mode
        viExModeConfg.ex_FTM_mode_enable_flag = 0; // Normal Mode
        payloadSize = 0;

        builder->payloadSPConfig(&payload, &payloadSize, miid,
                 PARAM_ID_SP_EX_VI_MODE_CFG,(void *)&viExModeConfg);
        if (payloadSize) {
            ret = updateCustomPayload(payload, payloadSize);
            free(payload);
            if (0 != ret) {
                PAL_ERR(LOG_TAG," updateCustomPayload Failed for EX_VI_MODE_CFG\n");
                // Not a fatal error
                ret = 0;
            }
        }
    }

        // Setting the values for VI module
    if (customPayloadSize) {
        ret = SessionAlsaUtils::setDeviceCustomPayload(rm, backEndNameTx,
                  customPayload, customPayloadSize);
        if (ret) {
            PAL_ERR(LOG_TAG, "Unable to set custom param for mode");
            goto free_fe;
        }
    }


    txPcm = pcm_open(rm->getVirtualSndCard(), pcmDevIdsTx.at(0), flags, &config);
    if (!txPcm) {
        PAL_ERR(LOG_TAG, "txPcm open failed");
        goto free_fe;
    }

    if (!pcm_is_ready(txPcm)) {
        PAL_ERR(LOG_TAG, "txPcm open not ready");
        goto err_pcm_open;
    }

    for (int ch = numberOfChannels; ch != 0; ch = ch >> CHANNELS_2) {
        //Register for VI module callback

        PAL_DBG(LOG_TAG, "registering event for VI module");
        payload_size = sizeof(struct agm_event_reg_cfg);

        event_cfg.event_id = EVENT_ID_VI_CALIBRATION;
        event_cfg.event_config_payload_size = 0;
        event_cfg.is_register = 1;
        if (ch == CHANNELS_4)
            tagid = MODULE_VI2;
        else
            tagid = MODULE_VI;

        ret = SessionAlsaUtils::registerMixerEvent(virtMixer, pcmDevIdsTx.at(0),
                           backEndNameTx.c_str(), tagid, (void *)&event_cfg,
                           payload_size);
        if (ret) {
            PAL_ERR(LOG_TAG, "Unable to register event to DSP");
            // Fatal Error. Calibration Won't work
            goto err_pcm_open;
        }
    }

    // Register to mixtureControlEvents and wait for the R0T0 values
    ret = rm->registerMixerEventCallback(pcmDevIdsTx, sessionCb, (uint64_t)this, true);
    if (ret != 0) {
        PAL_ERR(LOG_TAG, "Failed to register callback to rm");
        // Fatal Error. Calibration Won't work
        goto err_pcm_open;
    }

    enableDevice(audioRoute, mSndDeviceName_vi);

    PAL_DBG(LOG_TAG, "pcm start for TX path");
    if (pcm_start(txPcm) < 0) {
        PAL_ERR(LOG_TAG, "pcm start failed for TX path");
        ret = -ENOSYS;
        goto err_pcm_open;
    }
    isTxStarted = true;

    // Setup RX path
    deviceRx.id = PAL_DEVICE_OUT_SPEAKER;
    rm->getDeviceInfo(deviceRx.id, PAL_STREAM_PROXY, "", &deviceRxSpkr);
    strlcpy(mSndDeviceName_rx, deviceRxSpkr.sndDevName.c_str(), DEVICE_NAME_MAX_SIZE);
    rm->getChannelMap(&(deviceRx.config.ch_info.ch_map[0]), numberOfChannels);

    switch (numberOfChannels) {
        case 1 :
            deviceRx.config.ch_info.channels = CHANNELS_1;
        break;
        case 2 :
            deviceRx.config.ch_info.channels = CHANNELS_2;
        break;
        case 4 :
            deviceRx.config.ch_info.channels = CHANNELS_4;
        break;
        default:
            PAL_DBG(LOG_TAG, "Unsupported channel. Set default as 2");
            deviceRx.config.ch_info.channels = CHANNELS_2;
        break;
    }
    // TODO: Fetch these data from rm.xml
    deviceRx.config.sample_rate = SAMPLINGRATE_48K;
    deviceRx.config.bit_width = BITWIDTH_16;
    deviceRx.config.aud_fmt_id = PAL_AUDIO_FMT_PCM_S16_LE;

    rm->getBackendName(deviceRx.id, backEndNameRx);
    if (!strlen(backEndNameRx.c_str())) {
        PAL_ERR(LOG_TAG, "Failed to obtain rx backend name for %d", deviceRx.id);
        goto err_pcm_open;
    }

    keyVector.clear();
    calVector.clear();

    PayloadBuilder::getDeviceKV(deviceRx.id, keyVector);
    if (0 != ret) {
        PAL_ERR(LOG_TAG, "Failed to obtain device KV for %d", deviceRx.id);
        goto err_pcm_open;
    }

    // Enable the SP module
    if (numberOfChannels == CHANNELS_4) {
        switch (numberOfChannels) {
            case 1 :
                // TODO: Fetch the configuration from RM.xml
                calVector.push_back(std::make_pair(SPK_PRO_DEV_MAP, RIGHT_MONO_WITH_DISABLED_2SP));
                calVector.push_back(std::make_pair(CHANNELS, CHANNELS_1));
            break;
            case 2 :
                calVector.push_back(std::make_pair(SPK_PRO_DEV_MAP, LEFT_RIGHT_WITH_DISABLED_2SP));
                calVector.push_back(std::make_pair(CHANNELS, CHANNELS_2));
            break;
            case 4 :
                calVector.push_back(std::make_pair(SPK_PRO_DEV_MAP, LEFT_RIGHT));
                calVector.push_back(std::make_pair(CHANNELS, CHANNELS_4));
            break;
            default :
                PAL_ERR(LOG_TAG, "Unsupported channels for speaker");
                goto err_pcm_open;
        }
    } else if (numberOfChannels <= CHANNELS_2) {
        switch(numberOfChannels) {
            case 1:
                calVector.push_back(std::make_pair(SPK_PRO_DEV_MAP, RIGHT_MONO));
            break;
            case 2:
                calVector.push_back(std::make_pair(SPK_PRO_DEV_MAP, LEFT_RIGHT));
            break;
            default :
                PAL_ERR(LOG_TAG, "Unsupported channel");
                goto err_pcm_open;
        }
    }

    SessionAlsaUtils::getAgmMetaData(keyVector, calVector,
                    (struct prop_data *)devicePropId, deviceMetaData);
    if (!deviceMetaData.size) {
        PAL_ERR(LOG_TAG, "device metadata is zero");
        ret = -ENOMEM;
        goto err_pcm_open;
    }

    connectCtrlNameBe<< backEndNameRx << " metadata";

    beMetaDataMixerCtrl = mixer_get_ctl_by_name(virtMixer, connectCtrlNameBe.str().data());
    if (!beMetaDataMixerCtrl) {
        PAL_ERR(LOG_TAG, "invalid mixer control: %s", backEndNameRx.c_str());
        ret = -EINVAL;
        goto err_pcm_open;
    }

    if (deviceMetaData.size) {
        ret = mixer_ctl_set_array(beMetaDataMixerCtrl, (void *)deviceMetaData.buf,
                                    deviceMetaData.size);
        free(deviceMetaData.buf);
        deviceMetaData.buf = nullptr;
    }
    else {
        PAL_ERR(LOG_TAG, "Device Metadata not set for RX path");
        ret = -EINVAL;
        goto err_pcm_open;
    }

    ret = SessionAlsaUtils::setDeviceMediaConfig(rm, backEndNameRx, &deviceRx);
    if (ret) {
        PAL_ERR(LOG_TAG, "setDeviceMediaConfig for speaker failed");
        goto err_pcm_open;
    }

    /* Retrieve Hostless PCM device id */
    sAttr.type = PAL_STREAM_LOW_LATENCY;
    sAttr.direction = PAL_AUDIO_INPUT_OUTPUT;
    dir = RX_HOSTLESS;
    pcmDevIdsRx = rm->allocateFrontEndIds(sAttr, dir);
    if (pcmDevIdsRx.size() == 0) {
        PAL_ERR(LOG_TAG, "allocateFrontEndIds failed");
        ret = -ENOSYS;
        goto err_pcm_open;
    }

    connectCtrlNameRx << "PCM" << pcmDevIdsRx.at(0) << " connect";
    connectCtrl = mixer_get_ctl_by_name(virtMixer, connectCtrlNameRx.str().data());
    if (!connectCtrl) {
        PAL_ERR(LOG_TAG, "invalid mixer control: %s", connectCtrlNameRx.str().data());
        ret = -ENOSYS;
        goto err_pcm_open;
    }
    ret = mixer_ctl_set_enum_by_string(connectCtrl, backEndNameRx.c_str());
    if (ret) {
        PAL_ERR(LOG_TAG, "Mixer control %s set with %s failed: %d",
        connectCtrlNameRx.str().data(), backEndNameRx.c_str(), ret);
        goto err_pcm_open;
    }
    isRxFeandBeConnected = true;

    config.rate = SAMPLINGRATE_48K;
    config.format = PCM_FORMAT_S16_LE;
    if (numberOfChannels > 1)
        config.channels = CHANNELS_2;
    else
        config.channels = CHANNELS_1;
    config.period_size = DEFAULT_PERIOD_SIZE;
    config.period_count = DEFAULT_PERIOD_COUNT;
    config.start_threshold = 0;
    config.stop_threshold = INT_MAX;
    config.silence_threshold = 0;

    flags = PCM_OUT;

    // Set the operation mode for SP module
    spModeConfg.operation_mode = CALIBRATION_MODE;
    if (customPayloadSize) {
        free(customPayload);
        customPayloadSize = 0;
        customPayload = NULL;
    }
    for (int ch = numberOfChannels; ch != 0; ch = ch >> CHANNELS_2) {
        if (ch == CHANNELS_4)
            tagid = MODULE_SP2;
        else
            tagid = MODULE_SP;

        ret = SessionAlsaUtils::getModuleInstanceId(virtMixer, pcmDevIdsRx.at(0),
                                                        backEndNameRx.c_str(),
                                                        tagid, &miid);
        if (0 != ret) {
           PAL_ERR(LOG_TAG, "Failed to get miid info %x, status = %d", MODULE_SP, ret);
           goto err_pcm_open;
        }
        PAL_DBG(LOG_TAG, "set sp mode to %d for miid = %x", spModeConfg.operation_mode,miid);
        payloadSize = 0;

        builder->payloadSPConfig(&payload, &payloadSize, miid,
                 PARAM_ID_SP_OP_MODE,(void *)&spModeConfg);
        if (payloadSize) {
            ret = updateCustomPayload(payload, payloadSize);
            free(payload);
            if (0 != ret) {
                PAL_ERR(LOG_TAG," updateCustomPayload Failed\n");
                // Fatal error as SP module will not run in Calibration mode
                goto err_pcm_open;
            }
        }
    }

    // Setting the values for SP module
    if (customPayloadSize) {
        ret = SessionAlsaUtils::setDeviceCustomPayload(rm, backEndNameRx,
                    customPayload, customPayloadSize);
        if (ret) {
            PAL_ERR(LOG_TAG, "Unable to set custom param for SP mode");
            free(customPayload);
            customPayloadSize = 0;
            customPayload = NULL;
            goto err_pcm_open;
        }
    }

    rxPcm = pcm_open(rm->getVirtualSndCard(), pcmDevIdsRx.at(0), flags, &config);
    if (!rxPcm) {
        PAL_ERR(LOG_TAG, "pcm open failed for RX path");
        ret = -ENOSYS;
        goto err_pcm_open;
    }

    if (!pcm_is_ready(rxPcm)) {
        PAL_ERR(LOG_TAG, "pcm open not ready for RX path");
        ret = -ENOSYS;
        goto err_pcm_open;
    }


    enableDevice(audioRoute, mSndDeviceName_rx);
    PAL_DBG(LOG_TAG, "pcm start for RX path");
    if (pcm_start(rxPcm) < 0) {
        PAL_ERR(LOG_TAG, "pcm start failed for RX path");
        ret = -ENOSYS;
        goto err_pcm_open;
    }
    isRxStarted = true;

    spkrCalState = SPKR_CALIB_IN_PROGRESS;

    PAL_DBG(LOG_TAG, "Waiting for the event from DSP or PAL");

    for (int ch = numberOfChannels; ch != 0; ch = ch >> CHANNELS_2) {
        // TODO: Make this to wait in While loop
        cv.wait(calLock);

        if (numberOfChannels == CHANNELS_4)
            eventCh = MaxCH;
        else
            eventCh = numberOfChannels;

        // Store the R0T0 values
        if (mDspCallbackRcvd) {
            if (calibrationCallbackStatus == CALIBRATION_STATUS_SUCCESS) {
                if (vi_miid_II == source_miid) {
                    PAL_DBG(LOG_TAG, " opening this path %s",PAL_SP_II_TEMP_PATH);
                   fp = fopen(PAL_SP_II_TEMP_PATH, "w+");
                }
                else if (vi_miid_I == source_miid) {
                    PAL_DBG(LOG_TAG, " opening this path %s",PAL_SP_I_TEMP_PATH);
                   fp = fopen(PAL_SP_I_TEMP_PATH, "w+");
                }
                if (!fp) {
                    PAL_ERR(LOG_TAG, "Unable to open file for write %s",strerror(errno));

                } else {
                    PAL_DBG(LOG_TAG, "Write the R0T0 value to file");
                    for (i = 0; i < eventCh; i++) {
                        fwrite(&callback_data->r0_cali_q24[i],
                                    sizeof(callback_data->r0_cali_q24[i]), 1, fp);
                        fwrite(&spkerTempList[i + SpkrTempIndex], sizeof(int16_t), 1, fp);
                    }
                    spkrCalState = SPKR_CALIBRATED;
                    free(callback_data);
                    fclose(fp);
                }
                SpkrTempIndex = 2;
            }
            else if (calibrationCallbackStatus == CALIBRATION_STATUS_FAILURE) {
                PAL_DBG(LOG_TAG, "Calibration is not done");
                spkrCalState = SPKR_NOT_CALIBRATED;
                // reset the timer for retry
                clock_gettime(CLOCK_BOOTTIME, &spkrLastTimeUsed);
            }
        }
        cv.notify_all();
    }

err_pcm_open :
    if (txPcm) {
        event_cfg.is_register = 0;
        for (int ch = numberOfChannels; ch != 0; ch = ch >> CHANNELS_2) {
            //Register for VI module callback
            //update tagid based on channels.
            if (ch == CHANNELS_4)
                tagid = MODULE_VI2;
            else
                tagid = MODULE_VI;

            status = SessionAlsaUtils::registerMixerEvent(virtMixer, pcmDevIdsTx.at(0),
                            backEndNameTx.c_str(), tagid, (void *)&event_cfg,
                            payload_size);
            if (status) {
                PAL_ERR(LOG_TAG, "Unable to deregister event to DSP");
            }
        }
        status = rm->registerMixerEventCallback (pcmDevIdsTx, sessionCb, (uint64_t)this, false);
        if (status) {
            PAL_ERR(LOG_TAG, "Failed to deregister callback to rm");
        }
        if (isTxStarted)
            pcm_stop(txPcm);

        pcm_close(txPcm);
        disableDevice(audioRoute, mSndDeviceName_vi);

        txPcm = NULL;
    }

    if (rxPcm) {
        if (isRxStarted)
            pcm_stop(rxPcm);
        pcm_close(rxPcm);
        disableDevice(audioRoute, mSndDeviceName_rx);
        rxPcm = NULL;
    }

free_fe:
    if (pcmDevIdsRx.size() != 0) {
        if (isRxFeandBeConnected) {
            disconnectFeandBe(pcmDevIdsRx, backEndNameRx);
        }
        rm->freeFrontEndIds(pcmDevIdsRx, sAttr, RX_HOSTLESS);
    }

    if (pcmDevIdsTx.size() != 0) {
        if (isTxFeandBeConnected) {
            disconnectFeandBe(pcmDevIdsTx, backEndNameTx);
        }
        rm->freeFrontEndIds(pcmDevIdsTx, sAttr, TX_HOSTLESS);
    }
    pcmDevIdsRx.clear();
    pcmDevIdsTx.clear();

exit:

    if (!mDspCallbackRcvd) {
        // the lock is unlocked due to processing mode. It will be waiting
        // for the unlock. So notify it.
        PAL_DBG(LOG_TAG, "Unlocked due to processing mode");
        spkrCalState = SPKR_NOT_CALIBRATED;
        clock_gettime(CLOCK_BOOTTIME, &spkrLastTimeUsed);
    }
    cv.notify_all();

    if (ret != 0) {
        // Error happened. Reset timer
        clock_gettime(CLOCK_BOOTTIME, &spkrLastTimeUsed);
    }

    if(builder) {
       delete builder;
       builder = NULL;
    }
    PAL_DBG(LOG_TAG, "Exiting");
    return ret;
}

/**
  * This function sets the temperature of each speakers.
  * Currently values are supported like:
  * spkerTempList[0] - Right Speaker Temperature
  * spkerTempList[1] - Left Speaker Temperature
  * spkerTempList[2] - Rightbottom Speaker Temperature
  * spkerTempList[3] - Leftbottom Speaker Temperature
  */
void SpeakerProtection::getSpeakerTemperatureList()
{
    int i = 0;
    int value;
    PAL_DBG(LOG_TAG, "Enter Speaker Get Temperature List");

    for(i = 0; i < numberOfChannels; i++) {
         value = getSpeakerTemperature(i);
         PAL_DBG(LOG_TAG, "Temperature %d ", value);
         spkerTempList[i] = value;
    }
    PAL_DBG(LOG_TAG, "Exit Speaker Get Temperature List");
}

void SpeakerProtection::spkrCalibrationThread()
{
    unsigned long sec = 0;
    bool proceed = false;
    int i;

    while (!threadExit) {
        PAL_DBG(LOG_TAG, "Inside calibration while loop");
        proceed = false;
        if (isSpeakerInUse(&sec)) {
            PAL_DBG(LOG_TAG, "Speaker in use. Wait for proper time");
            spkrCalibrateWait();
            PAL_DBG(LOG_TAG, "Waiting done");
            continue;
        }
        else {
            PAL_DBG(LOG_TAG, "Speaker not in use");
            if (isDynamicCalTriggered) {
                PAL_DBG(LOG_TAG, "Dynamic Calibration triggered");
            }
            else if (sec < minIdleTime) {
                PAL_DBG(LOG_TAG, "Speaker not idle for minimum time. %lu", sec);
                spkrCalibrateWait();
                PAL_DBG(LOG_TAG, "Waited for speaker to be idle for min time");
                continue;
            }
            proceed = true;
        }

        if (proceed) {
            PAL_DBG(LOG_TAG, "Getting temperature of speakers");
            getSpeakerTemperatureList();

            for (i = 0; i < numberOfChannels; i++) {
                if ((spkerTempList[i] != -EINVAL) &&
                    (spkerTempList[i] < TZ_TEMP_MIN_THRESHOLD ||
                     spkerTempList[i] > TZ_TEMP_MAX_THRESHOLD)) {
                    PAL_ERR(LOG_TAG, "Temperature out of range. Retry");
                    spkrCalibrateWait();
                    continue;
                }
            }
            for (i = 0; i < numberOfChannels; i++) {
                // Converting to Q6 format
                spkerTempList[i] = (spkerTempList[i]*(1<<6));
            }
        }
        else {
            continue;
        }

        // Check whether speaker was in use in the meantime when temperature
        // was being read.
        proceed = false;
        if (isSpeakerInUse(&sec)) {
            PAL_DBG(LOG_TAG, "Speaker in use. Wait for proper time");
            spkrCalibrateWait();
            PAL_DBG(LOG_TAG, "Waiting done");
            continue;
        }
        else {
            PAL_DBG(LOG_TAG, "Speaker not in use");
            if (isDynamicCalTriggered) {
                PAL_DBG(LOG_TAG, "Dynamic calibration triggered");
            }
            else if (sec < minIdleTime) {
                PAL_DBG(LOG_TAG, "Speaker not idle for minimum time. %lu", sec);
                spkrCalibrateWait();
                PAL_DBG(LOG_TAG, "Waited for speaker to be idle for min time");
                continue;
            }
            proceed = true;
        }

        if (proceed) {
            // Start calibrating the speakers.
            PAL_DBG(LOG_TAG, "Speaker not in use, start calibration");
            spkrStartCalibration();
            if (spkrCalState == SPKR_CALIBRATED) {
                threadExit = true;
            }
        }
        else {
            continue;
        }
    }
    isDynamicCalTriggered = false;
    calThrdCreated = false;
    PAL_DBG(LOG_TAG, "Calibration done, exiting the thread");
}

SpeakerProtection::SpeakerProtection(struct pal_device *device,
                        std::shared_ptr<ResourceManager> Rm):Device(device, Rm)
{
    int status = 0;
    struct pal_device_info devinfo = {};
    FILE *fp = NULL;

    spkerTempList = NULL;

    if (ResourceManager::spQuickCalTime > 0 &&
        ResourceManager::spQuickCalTime < MIN_SPKR_IDLE_SEC)
        minIdleTime = ResourceManager::spQuickCalTime;
    else
        minIdleTime = MIN_SPKR_IDLE_SEC;

    rm = Rm;

    memset(&mDeviceAttr, 0, sizeof(struct pal_device));
    memcpy(&mDeviceAttr, device, sizeof(struct pal_device));

    threadExit = false;
    calThrdCreated = false;

    triggerCal = false;
    spkrCalState = SPKR_NOT_CALIBRATED;
    spkrProcessingState = SPKR_PROCESSING_IN_IDLE;

    isSpkrInUse = false;

    calibrationCallbackStatus = 0;
    mDspCallbackRcvd = false;

    rm->getDeviceInfo(device->id, PAL_STREAM_PROXY, "", &devinfo);
    numberOfChannels = devinfo.channels;
    PAL_DBG(LOG_TAG, "Number of Channels %d", numberOfChannels);

    if (numberOfChannels == CHANNELS_4)
        MaxCH = CHANNELS_2;
    else
        MaxCH = numberOfChannels;

    rm->getDeviceInfo(PAL_DEVICE_IN_VI_FEEDBACK, PAL_STREAM_PROXY, "", &vi_device);
    PAL_DBG(LOG_TAG, "Number of Channels for VI path is %d", vi_device.channels);

    rm->getDeviceInfo(PAL_DEVICE_IN_CPS_FEEDBACK, PAL_STREAM_PROXY, "", &cps_device);
    PAL_DBG(LOG_TAG, "Number of Channels for CPS path is %d", cps_device.channels);

    spkerTempList = new int [numberOfChannels];
    // Get current time
    clock_gettime(CLOCK_BOOTTIME, &spkrLastTimeUsed);

    // Getting mixture controls from Resource Manager
    status = rm->getVirtualAudioMixer(&virtMixer);
    if (status) {
        PAL_ERR(LOG_TAG,"virt mixer error %d", status);
    }
    status = rm->getHwAudioMixer(&hwMixer);
    if (status) {
        PAL_ERR(LOG_TAG,"hw mixer error %d", status);
    }

    if (device->id == PAL_DEVICE_OUT_HANDSET) {
        vi_device.channels = 1;
        cps_device.channels = 1;
        PAL_DBG(LOG_TAG, "Device id: %d vi_device.channels: %d cps_device.channels: %d",
                              device->id, vi_device.channels, cps_device.channels);
        goto exit;
    }

    fp = fopen(PAL_SP_I_TEMP_PATH, "rb");
    if (fp) {
        PAL_DBG(LOG_TAG, "Cal File exists. Reading from it");
        spkrCalState = SPKR_CALIBRATED;
    }
    else {
        PAL_DBG(LOG_TAG, "Calibration Not done");
        mCalThread = std::thread(&SpeakerProtection::spkrCalibrationThread,
                            this);
        calThrdCreated = true;
    }
exit:
    PAL_DBG(LOG_TAG, "exit. calThrdCreated :%d", calThrdCreated);
}

SpeakerProtection::~SpeakerProtection()
{
    if (spkerTempList)
        delete[] spkerTempList;

    if (customPayload)
        free(customPayload);

    customPayload = NULL;
    customPayloadSize = 0;
}

int32_t SpeakerProtection::getSpkrXmaxTmaxData()
{
    const char* getParamControl = "getParam";
    char* pcmDeviceName = NULL;
    uint8_t* payload = NULL;
    int ret = 0;
    uint32_t miid = 0, num_ch = 0, stringLen =0;
    int32_t pcmID = -EINVAL;
    size_t payloadSize = 0, bytesWritten = -1;
    struct mixer_ctl* ctl;
    FILE* fp;
    std::ostringstream cntrlName;
    std::string backendName;
    param_id_sp_tmax_xmax_logging_t sp_xmax_tmax;
    param_id_sp_tmax_xmax_logging_t* sp_xmax_tmax_value;
    PayloadBuilder* builder = new PayloadBuilder();

    /* Frontend ID information for RX Session presents at SessionAlsaPCM/Compress
     * In Kalama, there is no getter func for pcmDevIds (private attribute)
     * Assuming there will be only one session. So hardcoded here*/

    pcmID = 125;
    pcmDeviceName = rm->getDeviceNameFromID(pcmID);

    if (pcmDeviceName == NULL) {
        /*To check for PAL_STREAM_COMPRESSED*/
        pcmID = 105;
        pcmDeviceName = rm->getDeviceNameFromID(pcmID);
    }

    if (pcmDeviceName) {
        cntrlName << pcmDeviceName << " " << getParamControl;
    }
    else {
        PAL_ERR(LOG_TAG, "Error: %d Unable to get Device name\n", -EINVAL);
        ret = -EINVAL;
        goto exit;
    }

    ctl = mixer_get_ctl_by_name(virtMixer, cntrlName.str().data());

    if (!ctl) {
        ret = -ENOENT;
        PAL_ERR(LOG_TAG, "Error: %d Invalid mixer control: %s\n", ret, cntrlName.str().data());
        goto exit;
    }

    rm->getBackendName(PAL_DEVICE_OUT_SPEAKER, backendName);

    if (!strlen(backendName.c_str())) {
        ret = -ENOENT;
        PAL_ERR(LOG_TAG, "Error: %d Failed to obtain RX backend name", ret);
        goto exit;
    }

    ret = SessionAlsaUtils::getModuleInstanceId(virtMixer, pcmID,
        backendName.c_str(), MODULE_SP, &miid);

    if (0 != ret) {
        PAL_ERR(LOG_TAG, "Error: %d Failed to get tag info %x", ret, MODULE_SP);
        goto exit;
    }

    sp_xmax_tmax.num_ch = vi_device.channels;
    builder->payloadSPConfig(&payload, &payloadSize, miid,
        PARAM_ID_SP_TMAX_XMAX_LOGGING, (void *) &sp_xmax_tmax);

    if (!payloadSize) {
        PAL_ERR(LOG_TAG, "Payload memory allocation failed");
        ret = -EINVAL;
        goto exit;
    }

    ret = mixer_ctl_set_array(ctl, payload, payloadSize);
    if (0 != ret) {
        PAL_ERR(LOG_TAG, "Set failed with return value = %d", ret);
        goto exit;
    }

    memset(payload, 0, payloadSize);

    ret = mixer_ctl_get_array(ctl, payload, payloadSize);
    if (0 != ret) {
        PAL_ERR(LOG_TAG, "Get failed with return value = %d", ret);
        goto exit;
    }
    else {
        sp_xmax_tmax_value = (param_id_sp_tmax_xmax_logging_t*)(payload +
            sizeof(struct apm_module_param_data_t));
        fp = fopen(PAL_SP_XMAX_TMAX_DATA_PATH, "a");

        if (!fp) {
            PAL_ERR(LOG_TAG, "Unable to open file for write");
            ret = -EBADF;
            goto exit;
        }
        else {
            num_ch = sp_xmax_tmax_value->num_ch;
            auto currentTime = std::chrono::system_clock::now();
            std::time_t currentTimeT = std::chrono::system_clock::to_time_t(currentTime);
            const char* currentTimeStr = std::ctime(&currentTimeT);
            stringLen = std::strlen(currentTimeStr);

            PAL_DBG(LOG_TAG, "Current Timestamp : %s and size of string : %d", currentTimeStr, stringLen);
            bytesWritten = fprintf(fp, "%s ", currentTimeStr);

            if (bytesWritten < 0) {
                PAL_ERR(LOG_TAG, "Error in writing to file");
                fclose(fp);
                ret = -EBADF;
                goto exit;
            }

            for (int i = 0; i < num_ch; i++) {
                PAL_DBG(LOG_TAG, "Channel: %d, Max Excursion Value =%d",i, sp_xmax_tmax_value->tmax_xmax_params[i].max_excursion);
                bytesWritten = fprintf(fp, "Ch: %d <Xmax> : %3.4f", i, (float)sp_xmax_tmax_value->tmax_xmax_params[i].max_excursion / (1 << 27));

                if (bytesWritten < 0) {
                    PAL_ERR(LOG_TAG, "Error in writing to file");
                    fclose(fp);
                    ret = -EBADF;
                    goto exit;
                }

                PAL_DBG(LOG_TAG, "Channel: %d, Max Temperature Value =%d",i, sp_xmax_tmax_value->tmax_xmax_params[i].max_temperature);
                bytesWritten = fprintf(fp, " <Tmax> : %3.4f ", (float)sp_xmax_tmax_value->tmax_xmax_params[i].max_temperature / (1 << 22));

                if (bytesWritten < 0) {
                    PAL_ERR(LOG_TAG, "Error in writing to file");
                    fclose(fp);
                    ret = -EBADF;
                    goto exit;
                }
            }
            bytesWritten = fprintf(fp, "\n");
            if (bytesWritten < 0) {
                PAL_ERR(LOG_TAG, "Error in writing to file");
                fclose(fp);
                ret = -EBADF;
                goto exit;
            }
            fclose(fp);
        }
    }
exit:
    if (builder) {
        delete builder;
        builder = NULL;
    }
    return ret;

}

void SpeakerProtection::startSpkrXmaxTmaxLogging()
{
    FILE* log_fp = NULL;
    int32_t ret = 0;

    PAL_DBG(LOG_TAG, "Enter");

    /* This condition is added to know if the Speaker_RX graph started or not.
     * Assuming this file will be pushed to target after playback started
     * That's how, we will be sure Speaker_RX graph has been started */

    while (!log_fp) {
        log_fp = fopen(PAL_SP_XMAX_TMAX_LOG_PATH, "rb");
    }
    fclose(log_fp);

    if (remove(PAL_SP_XMAX_TMAX_LOG_PATH) == 0) {
        PAL_DBG(LOG_TAG, "log_spkr_xmax_tmax file deleted successfully");
    }

    startXmaxLogging = true;
    while (startXmaxLogging && (ret == 0)) {
        ret = getSpkrXmaxTmaxData();
        if (ret != 0) {
            PAL_ERR(LOG_TAG, "Failed to get Param for spkr_xmax_tmax");
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(1000));
    }
}

/*
 * CPS related custom payload
 */
void SpeakerProtection::updateCpsCustomPayload(int miid, uint32_t phy_add[3], int wsa2_flag)
{
    PayloadBuilder* builder = new PayloadBuilder();
    uint8_t* payload = NULL;
    size_t payloadSize = 0;
    int max_channels = numberOfChannels;
    lpass_swr_hw_reg_cfg_t *cpsRegCfg = NULL;
    int val, ret = 0;

    if (numberOfChannels > 2)
        max_channels = 2;
    else if (numberOfChannels < 1) {
        PAL_ERR(LOG_TAG,"Received invalid number of channels for CPS payload\n");
        goto exit;
    }

    pkd_reg_addr_t pkedRegAddr[max_channels];
    cps_reg_wr_values_t *cps_thrsh_values;
    param_id_cps_lpass_swr_thresholds_cfg_t *cps_thrsh_cfg;
    int dev_num;

    memset(&pkedRegAddr, 0, sizeof(pkd_reg_addr_t) * max_channels);
    // Payload for ParamID : PARAM_ID_CPS_LPASS_HW_INTF_CFG
    cpsRegCfg = (lpass_swr_hw_reg_cfg_t *) calloc(1, sizeof(lpass_swr_hw_reg_cfg_t)
                       + sizeof(pkd_reg_addr_t) * max_channels);
    if (cpsRegCfg == NULL) {
        PAL_ERR(LOG_TAG,"Unable to allocate Memory for CPS config\n");
        goto exit;
    }

    if (!phy_add) {
        PAL_ERR(LOG_TAG, "Invalid WSA addresses received for SP module instance: %d", miid);
        goto exit;
    }

    cpsRegCfg->num_spkr = max_channels;
    cpsRegCfg->lpass_wr_cmd_reg_phy_addr = phy_add[0];
    cpsRegCfg->lpass_rd_cmd_reg_phy_addr = phy_add[1];
    cpsRegCfg->lpass_rd_fifo_reg_phy_addr = phy_add[2];

    // Payload for ParamID : PARAM_ID_CPS_LPASS_SWR_THRESHOLDS_CFG
    cps_thrsh_cfg = (param_id_cps_lpass_swr_thresholds_cfg_t*) calloc(1,
                       sizeof(param_id_cps_lpass_swr_thresholds_cfg_t)
                       + (sizeof(cps_reg_wr_values_t) * max_channels));
    if (cps_thrsh_cfg == NULL) {
        PAL_ERR(LOG_TAG,"Unable to allocate Memory for CPS SWR Threshold config\n");
        goto exit;
    }
    cps_thrsh_cfg->num_spkr = max_channels;
    cps_thrsh_cfg->vbatt_lower_threshold_1 = CPS_WSA_VBATT_LOWER_THRESHOLD_1;
    cps_thrsh_cfg->vbatt_lower_threshold_2 = CPS_WSA_VBATT_LOWER_THRESHOLD_2;
    cps_thrsh_values = (cps_reg_wr_values_t *)((uint8_t*)cps_thrsh_cfg
                         + sizeof(param_id_cps_lpass_swr_thresholds_cfg_t));

    for (int i = 0; i < max_channels; i++) {
        switch (i)
        {
            case 0 :
                if (wsa2_flag) {
                    dev_num = getCpsDevNumber(SPKR2_RIGHT_WSA_DEV_NUM);
                } else {
                    dev_num = getCpsDevNumber(SPKR_RIGHT_WSA_DEV_NUM);
                }
            break;
            case 1 :
                if (wsa2_flag) {
                    dev_num = getCpsDevNumber(SPKR2_LEFT_WSA_DEV_NUM);
                } else {
                    dev_num = getCpsDevNumber(SPKR_LEFT_WSA_DEV_NUM);
                }
            break;
        }
        PAL_DBG(LOG_TAG, "CPS Dev number%d for Channel %d",dev_num,i);
        pkedRegAddr[i].vbatt_pkd_reg_addr = CPS_WSA_VBATT_REG_ADDR;
        pkedRegAddr[i].temp_pkd_reg_addr = CPS_WSA_TEMP_REG_ADDR;

        pkedRegAddr[i].vbatt_pkd_reg_addr &= 0xFFFF;
        pkedRegAddr[i].temp_pkd_reg_addr &= 0xFFFF;

        val = 0;

        /* bits 20:23 carry swr device number */
        val |= dev_num << 20;
        /* bits 24:27 carry read length in bytes */
        val |= 1 << 24;

        /* bits 16:19 carry command id */
        val |= (i*2) << 16;

        /* Update dev num in packed reg addr */
        pkedRegAddr[i].vbatt_pkd_reg_addr |= val;

        val &= 0xFF0FFFF;
        val |= ((i*2)+1) << 16;

        pkedRegAddr[i].temp_pkd_reg_addr |= val;

        /* payload for PARAM_ID_CPS_LPASS_SWR_THRESHOLDS_CFG.*/
        memcpy(cps_thrsh_values, &sp_cps_thrsh_values, sizeof(cps_reg_wr_values_t));
        /* Retain dev_num from val */
        val &= 0x00F00000;
        for (int j = 0;j < CPS_NUM_VBATT_THRESHOLD_VALUES;j++) {
            val &= 0xFFF0FFFF;
            /* Update cmd id and dev_num to the payload.*/
            val |= ((i * 3) + j) << 16;
            cps_thrsh_values->value_normal_threshold[j] |= val;
            cps_thrsh_values->value_lower_threshold_1[j] |= val;
            cps_thrsh_values->value_lower_threshold_2[j] |= val;
        }
        cps_thrsh_values++;
    }
    memcpy(cpsRegCfg->pkd_reg_addr, pkedRegAddr, sizeof(pkd_reg_addr_t) *
                    max_channels);

    // Payload builder for ParamID : PARAM_ID_CPS_LPASS_HW_INTF_CFG
    payloadSize = 0;
    builder->payloadSPConfig(&payload, &payloadSize, miid,
            PARAM_ID_CPS_LPASS_HW_INTF_CFG,(void *)cpsRegCfg);
    if (payloadSize) {
        ret = updateCustomPayload(payload, payloadSize);
        free(payload);
        free(cpsRegCfg);
        if (0 != ret) {
            PAL_ERR(LOG_TAG," updateCustomPayload Failed\n");
        }
    }

    // Payload builder for ParamID : PARAM_ID_CPS_LPASS_SWR_THRESHOLDS_CFG
    payloadSize = 0;
    payload = NULL;
    builder->payloadSPConfig(&payload, &payloadSize, miid,
            PARAM_ID_CPS_LPASS_SWR_THRESHOLDS_CFG,(void *)cps_thrsh_cfg);
    if (payloadSize) {
        ret = updateCustomPayload(payload, payloadSize);
        free(payload);
        free(cps_thrsh_cfg);
        if (0 != ret) {
            PAL_ERR(LOG_TAG," updateCustomPayload Failed\n");
        }
    }

exit:
    if(builder) {
       delete builder;
       builder = NULL;
    }
}

/*
 * Function to trigger Processing mode.
 * The parameter that it accepts are below:
 * true - Start Processing Mode
 * false - Stop Processing Mode
 */
int32_t SpeakerProtection::spkrProtProcessingMode(bool flag)
{
    int ret = 0, dir = TX_HOSTLESS, flags, viParamId = 0, tempCH, Channels;
    char mSndDeviceName_vi[128] = {0};
    char mSndDeviceName_cps[128] = {0};
    char mSndDeviceName_SP[128] = {0};
    uint8_t* payload = NULL;
    uint32_t devicePropId[] = {0x08000010, 1, 0x2};
    uint32_t miid = 0, SP_II_miid = 0, SP_miid = 0, tagid, deviceid;
    bool isTxFeandBeConnected = true;
    bool isCPSFeandBeConnected = true;
    size_t payloadSize = 0;
    struct pal_device device, deviceCPS;
    struct pal_channel_info ch_info;
    struct pal_stream_attributes sAttr;
    struct pcm_config config;
    struct mixer_ctl *connectCtrl = NULL;
    struct mixer_ctl *connectCtrl2 = NULL;
    struct audio_route *audioRoute = NULL;
    struct vi_r0t0_cfg_t r0t0Array[numberOfChannels];
    struct agmMetaData deviceMetaData(nullptr, 0);
    struct mixer_ctl *beMetaDataMixerCtrl = nullptr;
    FILE *fp;
    std::string backEndName, backEndNameRx, backEndNameCPS;
    std::vector <std::pair<int, int>> keyVector;
    std::vector <std::pair<int, int>> calVector;
    std::shared_ptr<ResourceManager> rm;
    std::ostringstream connectCtrlNameBeCPS;
    std::ostringstream connectCtrlNameBeVI;
    std::ostringstream connectCtrlNameBeSP;
    std::ostringstream connectCtrlNameCPS;
    std::ostringstream connectCtrlName;
    param_id_sp_th_vi_r0t0_cfg_t *spR0T0confg;
    param_id_sp_vi_op_mode_cfg_t modeConfg;
    param_id_sp_vi_channel_map_cfg_t viChannelMapConfg;
    param_id_sp_op_mode_t spModeConfg;
    param_id_sp_ex_vi_mode_cfg_t viExModeConfg;
    param_id_cps_ch_map_t cpsChannelMapConfg;
    std::shared_ptr<Device> dev = nullptr;
    Stream *stream = NULL;
    Session *session = NULL;
    std::vector<Stream*> activeStreams;
    PayloadBuilder* builder = new PayloadBuilder();
    std::unique_lock<std::mutex> lock(calibrationMutex);
    struct agm_event_reg_cfg event_cfg;
    session_callback sessionCb;
    uint32_t phy_add[3];

    PAL_DBG(LOG_TAG, "Flag %d", flag);
    deviceMutex.lock();

    sessionCb = handleSPCallback;

    if (flag) {
        if (spkrCalState == SPKR_CALIB_IN_PROGRESS) {
            // Close the Graphs
            cv.notify_all();
            // Wait for cleanup
            cv.wait(lock);
            spkrCalState = SPKR_NOT_CALIBRATED;
            txPcm = NULL;
            rxPcm = NULL;
            cps1Pcm = NULL;
            cps2Pcm = NULL;
            PAL_DBG(LOG_TAG, "Stopped calibration mode");
        }
        numberOfRequest++;
        if (numberOfRequest > 1) {
            // R0T0 already set, we don't need to process the request
            goto exit;
        }
        PAL_DBG(LOG_TAG, "Custom payload size %zu, Payload %p", customPayloadSize,
                customPayload);

        if (customPayload) {
            free(customPayload);
        }
        customPayloadSize = 0;
        customPayload = NULL;

        spkrProtSetSpkrStatus(flag);
        // Speaker in use. Start the Processing Mode
        rm = ResourceManager::getInstance();
        if (!rm) {
            PAL_ERR(LOG_TAG, "Failed to get resource manager instance");
            goto exit;
        }

        memset(&device, 0, sizeof(device));
        memset(&deviceCPS, 0, sizeof(device));
        memset(&sAttr, 0, sizeof(sAttr));
        memset(&config, 0, sizeof(config));
        memset(&modeConfg, 0, sizeof(modeConfg));
        memset(&viChannelMapConfg, 0, sizeof(viChannelMapConfg));
        memset(&viExModeConfg, 0, sizeof(viExModeConfg));
        memset(&spModeConfg, 0, sizeof(spModeConfg));

        keyVector.clear();
        calVector.clear();

        dev = Device::getInstance(&mDeviceAttr, rm);
        dev->getCurrentSndDevName(mSndDeviceName_SP);

        if (mDeviceAttr.id == PAL_DEVICE_OUT_SPEAKER && strstr(mSndDeviceName_SP, "mono"))
            rm->getDeviceInfo(PAL_DEVICE_IN_VI_FEEDBACK, PAL_STREAM_VOICE_CALL, "", &vi_device);
        else if (mDeviceAttr.id == PAL_DEVICE_OUT_SPEAKER)
            rm->getDeviceInfo(PAL_DEVICE_IN_VI_FEEDBACK, PAL_STREAM_PROXY, "", &vi_device);

        if (numberOfChannels == CHANNELS_4)
            tempCH = MaxCH;
        else
            tempCH = numberOfChannels;

        // Configure device attribute
        switch(vi_device.channels) {
            case 1:
                ch_info.channels = CHANNELS_1;
                if (mDeviceAttr.id == PAL_DEVICE_OUT_HANDSET)
                    ch_info.ch_map[0] = PAL_CHMAP_CHANNEL_FL;
                else
                    ch_info.ch_map[0] = PAL_CHMAP_CHANNEL_FR;
            break;
            case 2:
                ch_info.channels = CHANNELS_2;
                ch_info.ch_map[0] = PAL_CHMAP_CHANNEL_FL;
                ch_info.ch_map[1] = PAL_CHMAP_CHANNEL_FR;
            break;
            case 4:
                ch_info.channels = CHANNELS_4;
                ch_info.ch_map[0] = PAL_CHMAP_CHANNEL_FL;
                ch_info.ch_map[1] = PAL_CHMAP_CHANNEL_FR;
                ch_info.ch_map[2] = PAL_CHMAP_CHANNEL_LB;
                ch_info.ch_map[3] = PAL_CHMAP_CHANNEL_RB;
            break;
            default:
                PAL_DBG(LOG_TAG, "Unsupported channels");
            break;
        }

        device.config.ch_info = ch_info;
        device.config.sample_rate = vi_device.samplerate;
        device.config.bit_width = vi_device.bit_width;
        device.config.aud_fmt_id = rm->getAudioFmt(vi_device.bit_width);

        // Setup TX path
        device.id = PAL_DEVICE_IN_VI_FEEDBACK;

        ret = rm->getAudioRoute(&audioRoute);
        if (0 != ret) {
            PAL_ERR(LOG_TAG, "Failed to get the audio_route address status %d", ret);
            goto exit;
        }
        strlcpy(mSndDeviceName_vi, vi_device.sndDevName.c_str(), DEVICE_NAME_MAX_SIZE);

        if (mDeviceAttr.id == PAL_DEVICE_OUT_HANDSET) {
           strlcat(mSndDeviceName_vi, FEEDBACK_MONO_1, DEVICE_NAME_MAX_SIZE);
        }
        PAL_DBG(LOG_TAG, "get the audio route %s", mSndDeviceName_vi);

        rm->getBackendName(device.id, backEndName);
        if (!strlen(backEndName.c_str())) {
            PAL_ERR(LOG_TAG, "Failed to obtain tx backend name for %d", device.id);
            goto exit;
        }

        PayloadBuilder::getDeviceKV(device.id, keyVector);
        if (0 != ret) {
            PAL_ERR(LOG_TAG, "Failed to obtain device KV for %d", device.id);
            goto exit;
        }

        // Enable VI module
        if (numberOfChannels == CHANNELS_4) {
            switch(vi_device.channels) {
                case 1 :
                    // TODO: check it from RM.xml for left or right configuration
                    calVector.push_back(std::make_pair(SPK_PRO_VI_MAP, RIGHT_SPKR_WITH_DISABLED_2SP));
                    calVector.push_back(std::make_pair(CHANNELS, CHANNELS_1));
                break;
                case 2 :
                    calVector.push_back(std::make_pair(SPK_PRO_VI_MAP, STEREO_SPKR_WITH_DISABLED_2SP));
                    calVector.push_back(std::make_pair(CHANNELS, CHANNELS_2));
                break;
                case 4 :
                    calVector.push_back(std::make_pair(SPK_PRO_VI_MAP, STEREO_SPKR));
                    calVector.push_back(std::make_pair(CHANNELS, CHANNELS_4));
                break;
                default :
                    PAL_ERR(LOG_TAG, "Unsupported channel");
                    goto exit;
            }
        } else if (numberOfChannels <= CHANNELS_2) {
            switch(vi_device.channels) {
                case 1:
                    if (mDeviceAttr.id == PAL_DEVICE_OUT_HANDSET)
                        calVector.push_back(std::make_pair(SPK_PRO_VI_MAP, LEFT_SPKR));
                    else
                        calVector.push_back(std::make_pair(SPK_PRO_VI_MAP, RIGHT_SPKR));
                break;
                case 2:
                    calVector.push_back(std::make_pair(SPK_PRO_VI_MAP, STEREO_SPKR));
                break;
                default :
                    PAL_ERR(LOG_TAG, "Unsupported channel");
                    goto exit;
            }
        }

        SessionAlsaUtils::getAgmMetaData(keyVector, calVector,
                (struct prop_data *)devicePropId, deviceMetaData);
        if (!deviceMetaData.size) {
            PAL_ERR(LOG_TAG, "VI device metadata is zero");
            ret = -ENOMEM;
            goto exit;
        }
        connectCtrlNameBeVI<< backEndName << " metadata";
        beMetaDataMixerCtrl = mixer_get_ctl_by_name(virtMixer,
                                    connectCtrlNameBeVI.str().data());
        if (!beMetaDataMixerCtrl) {
            PAL_ERR(LOG_TAG, "invalid mixer control for VI : %s", backEndName.c_str());
            ret = -EINVAL;
            goto exit;
        }

        if (deviceMetaData.size) {
            ret = mixer_ctl_set_array(beMetaDataMixerCtrl, (void *)deviceMetaData.buf,
                        deviceMetaData.size);
            free(deviceMetaData.buf);
            deviceMetaData.buf = nullptr;
        }
        else {
            PAL_ERR(LOG_TAG, "Device Metadata not set for TX path");
            ret = -EINVAL;
            goto exit;
        }
        PAL_DBG(LOG_TAG, "device attribute ch = %d",device.config.ch_info.channels);
        ret = SessionAlsaUtils::setDeviceMediaConfig(rm, backEndName, &device);
        if (ret) {
            PAL_ERR(LOG_TAG, "setDeviceMediaConfig for feedback device failed");
            goto exit;
        }

        /* Retrieve Hostless PCM device id */
        sAttr.type = PAL_STREAM_LOW_LATENCY;
        sAttr.direction = PAL_AUDIO_INPUT_OUTPUT;
        dir = TX_HOSTLESS;
        pcmDevIdTx = rm->allocateFrontEndIds(sAttr, dir);
        if (pcmDevIdTx.size() == 0) {
            PAL_ERR(LOG_TAG, "allocateFrontEndIds failed");
            ret = -ENOSYS;
            goto exit;
        }

        connectCtrlName << "PCM" << pcmDevIdTx.at(0) << " connect";
        connectCtrl = mixer_get_ctl_by_name(virtMixer, connectCtrlName.str().data());
        if (!connectCtrl) {
            PAL_ERR(LOG_TAG, "invalid mixer control: %s", connectCtrlName.str().data());
            goto free_fe;
        }

        ret = mixer_ctl_set_enum_by_string(connectCtrl, backEndName.c_str());
        if (ret) {
            PAL_ERR(LOG_TAG, "Mixer control %s set with %s failed: %d",
            connectCtrlName.str().data(), backEndName.c_str(), ret);
            goto free_fe;
        }

        isTxFeandBeConnected = true;

        config.rate = vi_device.samplerate;
        switch (vi_device.bit_width) {
            case 32 :
                config.format = PCM_FORMAT_S32_LE;
            break;
            case 24 :
                config.format = PCM_FORMAT_S24_LE;
            break;
            case 16 :
                config.format = PCM_FORMAT_S16_LE;
            break;
            default:
                PAL_DBG(LOG_TAG, "Unsupported bit width. Set default as 16");
                config.format = PCM_FORMAT_S16_LE;
            break;
        }

        switch (vi_device.channels) {
            case 1 :
                config.channels = CHANNELS_1;
            break;
            case 2 :
                config.channels = CHANNELS_2;
            break;
            default :
                PAL_DBG(LOG_TAG, "Unsupported channel. Set default as 2");
                config.channels = CHANNELS_2;
            break;
        }
        config.period_size = DEFAULT_PERIOD_SIZE;
        config.period_count = DEFAULT_PERIOD_COUNT;
        config.start_threshold = 0;
        config.stop_threshold = INT_MAX;
        config.silence_threshold = 0;

        flags = PCM_IN;

        for (int ch = numberOfChannels; ch != 0; ch = ch >> CHANNELS_2) {
            if (numberOfChannels == CHANNELS_4) {
                modeConfg.num_speakers = CHANNELS_2;
            } else {
                modeConfg.num_speakers = numberOfChannels;
            }

            switch (rm->mSpkrProtModeValue.operationMode) {
                case PAL_SP_MODE_FACTORY_TEST:
                    modeConfg.th_operation_mode = FACTORY_TEST_MODE;
                break;
                case PAL_SP_MODE_V_VALIDATION:
                    modeConfg.th_operation_mode = V_VALIDATION_MODE;
                break;
                case PAL_SP_MODE_DYNAMIC_CAL:
                default:
                    PAL_INFO(LOG_TAG, "Normal mode being used");
                    modeConfg.th_operation_mode = NORMAL_MODE;
            }
            modeConfg.th_quick_calib_flag = 0;

            if (ch == CHANNELS_4)
                tagid = MODULE_VI2;
            else
                tagid = MODULE_VI;
            ret = SessionAlsaUtils::getModuleInstanceId(virtMixer, pcmDevIdTx.at(0),
                            backEndName.c_str(), tagid, &miid);
            if (0 != ret) {
                PAL_ERR(LOG_TAG, "Failed to get tag info %x, status = %d", tagid, ret);
                goto free_fe;
            }

            builder->payloadSPConfig(&payload, &payloadSize, miid,
                                PARAM_ID_SP_VI_OP_MODE_CFG,(void *)&modeConfg);
            if (payloadSize) {
                ret = updateCustomPayload(payload, payloadSize);
                free(payload);
                if (0 != ret) {
                    PAL_ERR(LOG_TAG," updateCustomPayload Failed for VI_OP_MODE_CFG\n");
                    // Not fatal as by default VI module runs in Normal mode
                    ret = 0;
                }
            }

            // Setting Channel Map configuration for VI module
            // TODO: Move this to ACDB file
            if (vi_device.channels == CHANNELS_4)
                viChannelMapConfg.num_ch = tempCH * 2;
            else
                viChannelMapConfg.num_ch = vi_device.channels * 2;

            payloadSize = 0;
            builder->payloadSPConfig(&payload, &payloadSize, miid,
                        PARAM_ID_SP_VI_CHANNEL_MAP_CFG,(void *)&viChannelMapConfg);
            if (payloadSize) {
                ret = updateCustomPayload(payload, payloadSize);
                free(payload);
                if (0 != ret) {
                    PAL_ERR(LOG_TAG," updateCustomPayload Failed for CHANNEL_MAP_CFG\n");
                }
            }

            // Setting Excursion mode
            if (rm->mSpkrProtModeValue.operationMode == PAL_SP_MODE_FACTORY_TEST)
                viExModeConfg.ex_FTM_mode_enable_flag = 1; // FTM Mode
            else
                viExModeConfg.ex_FTM_mode_enable_flag = 0; // Normal Mode
            payloadSize = 0;

            builder->payloadSPConfig(&payload, &payloadSize, miid,
                     PARAM_ID_SP_EX_VI_MODE_CFG,(void *)&viExModeConfg);
            if (payloadSize) {
                ret = updateCustomPayload(payload, payloadSize);
                free(payload);
                if (0 != ret) {
                    PAL_ERR(LOG_TAG," updateCustomPayload Failed for EX_VI_MODE_CFG\n");
                    ret = 0;
                }
            }

            if (rm->mSpkrProtModeValue.operationMode) {
                PAL_DBG(LOG_TAG, "Operation mode %d", rm->mSpkrProtModeValue.operationMode);
                param_id_sp_th_vi_ftm_cfg_t viFtmConfg;
                if (numberOfChannels == CHANNELS_4)
                    viFtmConfg.num_ch = CHANNELS_2;
                else
                    viFtmConfg.num_ch = numberOfChannels;
                switch (rm->mSpkrProtModeValue.operationMode) {
                    case PAL_SP_MODE_FACTORY_TEST:
                        viParamId = PARAM_ID_SP_TH_VI_FTM_CFG;
                        payloadSize = 0;
                        builder->payloadSPConfig (&payload, &payloadSize, miid,
                                 viParamId, (void *) &viFtmConfg);
                        if (payloadSize) {
                            ret = updateCustomPayload(payload, payloadSize);
                            free(payload);
                            if (0 != ret) {
                                PAL_ERR(LOG_TAG," Payload Failed for FTM mode\n");
                            }
                        }
                        viParamId = PARAM_ID_SP_EX_VI_FTM_CFG;
                        payloadSize = 0;
                        builder->payloadSPConfig (&payload, &payloadSize, miid,
                                viParamId, (void *) &viFtmConfg);
                        if (payloadSize) {
                            ret = updateCustomPayload(payload, payloadSize);
                            free(payload);
                            if (0 != ret) {
                                PAL_ERR(LOG_TAG," Payload Failed for FTM mode\n");
                            }
                        }
                    break;
                    case PAL_SP_MODE_V_VALIDATION:
                        viParamId = PARAM_ID_SP_TH_VI_V_VALI_CFG;
                        payloadSize = 0;
                        builder->payloadSPConfig (&payload, &payloadSize, miid,
                                viParamId, (void *) &viFtmConfg);
                        if (payloadSize) {
                            ret = updateCustomPayload(payload, payloadSize);
                            free(payload);
                            if (0 != ret) {
                                PAL_ERR(LOG_TAG," Payload Failed for FTM mode\n");
                            }
                        }
                    break;
                    case PAL_SP_MODE_DYNAMIC_CAL:
                        PAL_ERR(LOG_TAG, "Dynamic cal in Processing mode!!");
                    break;
                }
            }

            // Setting the R0T0 values
            PAL_DBG(LOG_TAG, "Read R0T0 from file");
            if (ch == CHANNELS_4)
                fp = fopen(PAL_SP_II_TEMP_PATH, "rb");
            else
                fp = fopen(PAL_SP_I_TEMP_PATH, "rb");

            if (fp) {
                for (int i = 0; i < tempCH; i++) {
                    fread(&r0t0Array[i].r0_cali_q24,
                          sizeof(r0t0Array[i].r0_cali_q24), 1, fp);
                    fread(&r0t0Array[i].t0_cali_q6,
                          sizeof(r0t0Array[i].t0_cali_q6), 1, fp);
                }
                fclose(fp);
            }
            else {
                PAL_DBG(LOG_TAG, "Speaker not calibrated. Send safe value");
                for (int i = 0; i < tempCH; i++) {
                    r0t0Array[i].r0_cali_q24 = MIN_RESISTANCE_SPKR_Q24;
                    r0t0Array[i].t0_cali_q6 = SAFE_SPKR_TEMP_Q6;
                }
            }

            spR0T0confg = (param_id_sp_th_vi_r0t0_cfg_t *)calloc(1,
                            sizeof(param_id_sp_th_vi_r0t0_cfg_t) +
                            sizeof(vi_r0t0_cfg_t) * tempCH);
            if (!spR0T0confg) {
                PAL_ERR(LOG_TAG," unable to create speaker config payload\n");
                goto free_fe;
            }

            if (numberOfChannels == CHANNELS_4)
                spR0T0confg->num_ch = CHANNELS_2;
            else
                spR0T0confg->num_ch = numberOfChannels;

            for (int i = 0; i < spR0T0confg->num_ch; i++) {
                spR0T0confg->r0t0_cfg[i].r0_cali_q24 = r0t0Array[i].r0_cali_q24;
                spR0T0confg->r0t0_cfg[i].t0_cali_q6 = r0t0Array[i].t0_cali_q6;
                PAL_DBG (LOG_TAG,"R0 %x ", spR0T0confg->r0t0_cfg[i].r0_cali_q24);
                PAL_DBG (LOG_TAG,"T0 %x ", spR0T0confg->r0t0_cfg[i].t0_cali_q6);
            }

            payloadSize = 0;
            builder->payloadSPConfig(&payload, &payloadSize, miid,
                    PARAM_ID_SP_TH_VI_R0T0_CFG,(void *)spR0T0confg);
            if (payloadSize) {
                ret = updateCustomPayload(payload, payloadSize);
                free(payload);
                free(spR0T0confg);
                if (0 != ret) {
                    PAL_ERR(LOG_TAG," updateCustomPayload Failed\n");
                    ret = 0;
                }
            }

        }

        // Setting the values for VI module
        if (customPayloadSize) {
            ret = SessionAlsaUtils::setDeviceCustomPayload(rm, backEndName,
                            customPayload, customPayloadSize);
            if (ret) {
                PAL_ERR(LOG_TAG, "Unable to set custom param for mode");
                goto free_fe;
            }
        }

        txPcm = pcm_open(rm->getVirtualSndCard(), pcmDevIdTx.at(0), flags, &config);
        if (!txPcm) {
            PAL_ERR(LOG_TAG, "txPcm open failed");
            goto free_fe;
        }

        if (!pcm_is_ready(txPcm)) {
            PAL_ERR(LOG_TAG, "txPcm open not ready");
            goto err_pcm_open;
        }

        PAL_DBG(LOG_TAG, "registering DC detection event for VI module");
        payloadSize = sizeof(struct agm_event_reg_cfg);
        for (int ch = numberOfChannels; ch != 0; ch = ch >> CHANNELS_2) {
            //Register for VI module callback
            //update tagid based on channels.
            if (ch == CHANNELS_4)
                tagid = MODULE_VI2;
            else
                tagid = MODULE_VI;
             /* Register for EVENT_ID_SPv5_SPEAKER_DIAGNOSTICS. */
            event_cfg.event_id = EVENT_ID_SPv5_SPEAKER_DIAGNOSTICS;
            event_cfg.event_config_payload_size = 0;
            event_cfg.is_register = 1;

            ret = SessionAlsaUtils::registerMixerEvent(virtMixer, pcmDevIdTx.at(0),
                        backEndName.c_str(), tagid,
                        (void *)&event_cfg, payloadSize);
              if (ret) {
                PAL_ERR(LOG_TAG, "Unable to register event to DSP");
            }
        }

        ret = rm->registerMixerEventCallback(pcmDevIdTx, sessionCb, (uint64_t)this, true);
        if (ret != 0)
            PAL_ERR(LOG_TAG, "Failed to register callback to rm");

        // Setting up SP mode
        rm->getBackendName(mDeviceAttr.id, backEndNameRx);
        if (!strlen(backEndNameRx.c_str())) {
            PAL_ERR(LOG_TAG, "Failed to obtain rx backend name for %d", mDeviceAttr.id);
            goto err_pcm_open;
        }

        ret = rm->getActiveStream_l(activeStreams, dev);
        if ((0 != ret) || (activeStreams.size() == 0)) {
            PAL_ERR(LOG_TAG, " no active stream available");
            ret = -EINVAL;
            goto err_pcm_open;
        }

        stream = static_cast<Stream *>(activeStreams[0]);
        stream->getAssociatedSession(&session);

        for (int ch = numberOfChannels; ch != 0; ch = ch >> CHANNELS_2) {
            if (ch == CHANNELS_4)
                tagid = MODULE_SP2;
            else
                tagid = MODULE_SP;

        // Set the operation mode for SP module
        PAL_DBG(LOG_TAG, "Operation mode for SP %d",
                        rm->mSpkrProtModeValue.operationMode);
        switch (rm->mSpkrProtModeValue.operationMode) {
            case PAL_SP_MODE_FACTORY_TEST:
                spModeConfg.operation_mode = FACTORY_TEST_MODE;
            break;
            case PAL_SP_MODE_V_VALIDATION:
                spModeConfg.operation_mode = V_VALIDATION_MODE;
            break;
            default:
                PAL_INFO(LOG_TAG, "Normal mode being used");
                spModeConfg.operation_mode = NORMAL_MODE;
        }
            ret = session->getMIID(backEndNameRx.c_str(), tagid, &miid);
            if (ret) {
                PAL_ERR(LOG_TAG, "Failed to get tag info %x, status = %d", tagid, ret);
                goto err_pcm_open;
            }

            if (ch == CHANNELS_4)
            {
                SP_II_miid = miid;
            } else {
                SP_miid = miid;
            }

            // Set the operation mode for SP module
            PAL_DBG(LOG_TAG, "Operation mode for SP %d",
                            rm->mSpkrProtModeValue.operationMode);
            switch (rm->mSpkrProtModeValue.operationMode) {
                case PAL_SP_MODE_FACTORY_TEST:
                    spModeConfg.operation_mode = FACTORY_TEST_MODE;
                break;
                case PAL_SP_MODE_V_VALIDATION:
                    spModeConfg.operation_mode = V_VALIDATION_MODE;
                break;
                default:
                    PAL_INFO(LOG_TAG, "Normal mode being used");
                    spModeConfg.operation_mode = NORMAL_MODE;
            }

            payloadSize = 0;
            builder->payloadSPConfig(&payload, &payloadSize, miid,
                    PARAM_ID_SP_OP_MODE,(void *)&spModeConfg);
            if (payloadSize) {
                if (customPayload) {
                    free (customPayload);
                    customPayloadSize = 0;
                    customPayload = NULL;
                }
                ret = updateCustomPayload(payload, payloadSize);
                free(payload);
                if (0 != ret) {
                    PAL_ERR(LOG_TAG," updateCustomPayload Failed\n");
                }
            }
        }

        switch(ResourceManager::cpsMode)
        {
            case 1:
                goto cps_dev_setup;
            case 2:
                memset(phy_add, 0, sizeof(phy_add));
                // wsa883x specific cps payload

                if(ResourceManager::wsa2_enable)
                {
                    phy_add[0] = ResourceManager::wsa2_wr_cmd_reg_phy_addr;
                    phy_add[1] = ResourceManager::wsa2_rd_cmd_reg_phy_addr;
                    phy_add[2] = ResourceManager::wsa2_rd_fifo_reg_phy_addr;
                    PAL_DBG(LOG_TAG, "Update the CPS payload for SP_II_miid : %d ",SP_II_miid);
                    updateCpsCustomPayload(SP_II_miid, phy_add, WSA2_REGISTER_ADD);
                    PAL_DBG(LOG_TAG, "Update the CPS payload for SP_miid: %d",SP_miid);
                    memset(phy_add, 0, sizeof(phy_add));
                }
                phy_add[0] = ResourceManager::wsa_wr_cmd_reg_phy_addr;
                phy_add[1] = ResourceManager::wsa_rd_cmd_reg_phy_addr;
                phy_add[2] = ResourceManager::wsa_rd_fifo_reg_phy_addr;
                updateCpsCustomPayload(SP_miid, phy_add, WSA_REGISTER_ADD);
                PAL_DBG(LOG_TAG, "Updated the CPS payload for both WSA");
           default:
                enableDevice(audioRoute, mSndDeviceName_vi);
                PAL_DBG(LOG_TAG, "pcm start for TX");
             if (pcm_start(txPcm) < 0) {
                    PAL_ERR(LOG_TAG, "pcm start failed for TX path");
                    goto err_pcm_open;
                }
                // Free up the local variables
                goto exit;
        }

cps_dev_setup:
        enableDevice(audioRoute, mSndDeviceName_vi);
        PAL_DBG(LOG_TAG, "pcm start for TX");
        if (pcm_start(txPcm) < 0) {
            PAL_ERR(LOG_TAG, "pcm start failed for TX path");
            goto err_pcm_open;
        }

        keyVector.clear();
        calVector.clear();

        if (mDeviceAttr.id == PAL_DEVICE_OUT_SPEAKER && strstr(mSndDeviceName_SP, "mono"))
            rm->getDeviceInfo(PAL_DEVICE_IN_CPS_FEEDBACK, PAL_STREAM_VOICE_CALL, "", &cps_device);
        else if (mDeviceAttr.id == PAL_DEVICE_OUT_SPEAKER)
            rm->getDeviceInfo(PAL_DEVICE_IN_CPS_FEEDBACK, PAL_STREAM_PROXY, "", &cps_device);

        // Configure device attribute
        for (int ch = numberOfChannels; ch != 0; ch = ch >> 2) {
             PAL_ERR(LOG_TAG, "Opening CPS ");
            // Configure device attribute
            if (cps_device.channels > 1) {
                ch_info.channels = CHANNELS_2;
                ch_info.ch_map[0] = PAL_CHMAP_CHANNEL_FL;
                ch_info.ch_map[1] = PAL_CHMAP_CHANNEL_FR;
            }
            else {
                ch_info.channels = CHANNELS_1;
                if (mDeviceAttr.id == PAL_DEVICE_OUT_HANDSET)
                    ch_info.ch_map[0] = PAL_CHMAP_CHANNEL_FL;
                else
                    ch_info.ch_map[0] = PAL_CHMAP_CHANNEL_FR;
            }

            deviceCPS.config.ch_info = ch_info;
            deviceCPS.config.sample_rate = cps_device.samplerate;
            deviceCPS.config.bit_width = cps_device.bit_width;
            deviceCPS.config.aud_fmt_id = rm->getAudioFmt(cps_device.bit_width);

            if (ch != CHANNELS_4)
                deviceCPS.id = PAL_DEVICE_IN_CPS_FEEDBACK;
            else
                deviceCPS.id = PAL_DEVICE_IN_CPS2_FEEDBACK;

            ret = rm->getAudioRoute(&audioRoute);
            if (0 != ret) {
                PAL_ERR(LOG_TAG, "Failed to get the audio_route address status %d", ret);
                goto err_pcm_open;
            }
            strlcpy(mSndDeviceName_cps, cps_device.sndDevName.c_str(), DEVICE_NAME_MAX_SIZE);

            if (mDeviceAttr.id == PAL_DEVICE_OUT_HANDSET) {
              strlcat(mSndDeviceName_cps, FEEDBACK_MONO_1, DEVICE_NAME_MAX_SIZE);
            }

            PAL_DBG(LOG_TAG, "get the audio route %s", mSndDeviceName_cps);

            rm->getBackendName(deviceCPS.id, backEndNameCPS);
            if (!strlen(backEndNameCPS.c_str())) {
                PAL_ERR(LOG_TAG, "Failed to obtain CPS backend name for %d", deviceCPS.id);
                goto err_pcm_open;
            }

            PayloadBuilder::getDeviceKV(deviceCPS.id, keyVector);
            if (0 != ret) {
                PAL_ERR(LOG_TAG, "Failed to obtain device KV for %d", device.id);
                goto err_pcm_open;
            }

            // Enable the CPS module
            switch (cps_device.channels) {
                case 1 :
                    if (mDeviceAttr.id == PAL_DEVICE_OUT_HANDSET)
                        calVector.push_back(std::make_pair(SPK_PRO_CPS_MAP, L_SPKR));
                    else
                        calVector.push_back(std::make_pair(SPK_PRO_CPS_MAP, R_SPKR));
                break;
                case 2 :
                    calVector.push_back(std::make_pair(SPK_PRO_CPS_MAP, ST_SPKR));
                break;
                default :
                    PAL_ERR(LOG_TAG, "Unsupported channel");
                    goto err_pcm_open;
            }

            SessionAlsaUtils::getAgmMetaData(keyVector, calVector,
                    (struct prop_data *)devicePropId, deviceMetaData);
            if (!deviceMetaData.size) {
                PAL_ERR(LOG_TAG, "CPS device metadata is zero");
                ret = -ENOMEM;
                goto err_pcm_open;
            }
            connectCtrlNameBeCPS<< backEndNameCPS << " metadata";
            beMetaDataMixerCtrl = mixer_get_ctl_by_name(virtMixer,
                                        connectCtrlNameBeCPS.str().data());
            if (!beMetaDataMixerCtrl) {
                PAL_ERR(LOG_TAG, "invalid mixer control for CPS : %s", backEndNameCPS.c_str());
                ret = -EINVAL;
                goto err_pcm_open;
            }

            if (deviceMetaData.size) {
                ret = mixer_ctl_set_array(beMetaDataMixerCtrl, (void *)deviceMetaData.buf,
                            deviceMetaData.size);
                free(deviceMetaData.buf);
                deviceMetaData.buf = nullptr;
            }
            else {
                PAL_ERR(LOG_TAG, "Device Metadata not set for CPS path");
                ret = -EINVAL;
                goto err_pcm_open;
            }

            ret = SessionAlsaUtils::setDeviceMediaConfig(rm, backEndNameCPS, &deviceCPS);
            if (ret) {
                PAL_ERR(LOG_TAG, "setDeviceMediaConfig for feedback device failed");
                goto err_pcm_open;
            }

            /* Retrieve Hostless PCM device id */
            sAttr.type = PAL_STREAM_LOW_LATENCY;
            sAttr.direction = PAL_AUDIO_INPUT_OUTPUT;
            dir = TX_HOSTLESS;
            if (ch != CHANNELS_4) {
               pcmDevIdCPS = rm->allocateFrontEndIds(sAttr, dir);
               if (pcmDevIdCPS.size() == 0) {
                   PAL_ERR(LOG_TAG, "allocateFrontEndIds failed");
                   ret = -ENOSYS;
                   goto err_pcm_open;
               }
               connectCtrlNameCPS << "PCM" << pcmDevIdCPS.at(0) << " connect";
            } else {
               pcmDevIdCPS2 = rm->allocateFrontEndIds(sAttr, dir);

               if (pcmDevIdCPS2.size() == 0) {
                   PAL_ERR(LOG_TAG, "allocateFrontEndIds failed");
                   ret = -ENOSYS;
                   goto err_pcm_open;
               }
               connectCtrlNameCPS << "PCM" << pcmDevIdCPS2.at(0) << " connect";
            }
            connectCtrl2 = mixer_get_ctl_by_name(virtMixer, connectCtrlNameCPS.str().data());

            if (!connectCtrl2) {
                PAL_ERR(LOG_TAG, "invalid mixer control: %s", connectCtrlNameCPS.str().data());
                goto free_fe;
            }

            ret = mixer_ctl_set_enum_by_string(connectCtrl2, backEndNameCPS.c_str());
            if (ret) {
                PAL_ERR(LOG_TAG, "Mixer control %s set with %s failed: %d",
                connectCtrlNameCPS.str().data(), backEndNameCPS.c_str(), ret);
                goto free_fe;
            }

            isCPSFeandBeConnected = true;

            config.rate = cps_device.samplerate;
            switch (cps_device.bit_width) {
                case 32 :
                    config.format = PCM_FORMAT_S32_LE;
                break;
                default:
                    PAL_DBG(LOG_TAG, "Unsupported bit width. Set default as 16");
                    config.format = PCM_FORMAT_S16_LE;
                break;
            }

            switch (cps_device.channels) {
                case 1 :
                    config.channels = CHANNELS_1;
                break;
                case 2 :
                    config.channels = CHANNELS_2;
                break;
                default :
                    PAL_DBG(LOG_TAG, "Unsupported channel. Set default as 2");
                    config.channels = CHANNELS_2;
                break;
            }
            config.period_size = DEFAULT_PERIOD_SIZE;
            config.period_count = DEFAULT_PERIOD_COUNT;
            config.start_threshold = 0;
            config.stop_threshold = INT_MAX;
            config.silence_threshold = 0;

            flags = PCM_IN;
            if (ch != CHANNELS_4) {
               deviceid = pcmDevIdCPS.at(0);
               tagid = TAG_MODULE_CPS;
            } else {
               deviceid = pcmDevIdCPS2.at(0);
               tagid = TAG_MODULE_CPS2;
            }
            ret = SessionAlsaUtils::getModuleInstanceId(virtMixer, deviceid,
                            backEndNameCPS.c_str(), tagid, &miid);
            if (0 != ret) {
                PAL_ERR(LOG_TAG, "Failed to get tag info %x, status = %d", tagid, ret);
                goto free_fe;
            }

            // Setting Channel Map configuration for CPS module
            // TODO: Move this to ACDB file
            if (ch != CHANNELS_4)
                cpsChannelMapConfg.num_ch = CHANNELS_2;
            else
                cpsChannelMapConfg.num_ch = cps_device.channels;
            payloadSize = 0;

            builder->payloadSPConfig(&payload, &payloadSize, miid,
                    PARAM_ID_CPS_CHANNEL_MAP,(void *)&cpsChannelMapConfg);
            if (payloadSize) {
                ret = updateCustomPayload(payload, payloadSize);
                free(payload);
                if (0 != ret) {
                    PAL_ERR(LOG_TAG," updateCustomPayload Failed for CPS CHANNEL_MAP_CFG\n");
                }
            }
            if (ch != CHANNELS_4) {
                cps1Pcm = pcm_open(rm->getVirtualSndCard(), pcmDevIdCPS.at(0), flags, &config);
                if (!cps1Pcm) {
                   PAL_ERR(LOG_TAG, "cps1Pcm open failed");
                   goto free_fe;
                }

                if (!pcm_is_ready(cps1Pcm)) {
                    PAL_ERR(LOG_TAG, "cps1Pcm open not ready");
                    goto err_pcm_open;
                }

                enableDevice(audioRoute, mSndDeviceName_cps);
                PAL_DBG(LOG_TAG, "pcm start for CPS");
                if (pcm_start(cps1Pcm) < 0) {
                    PAL_ERR(LOG_TAG, "pcm start failed for CPS path");
                    goto err_pcm_open;
                }
            } else {
                cps2Pcm = pcm_open(rm->getVirtualSndCard(), pcmDevIdCPS.at(0), flags, &config);
                if (!cps2Pcm) {
                    PAL_ERR(LOG_TAG, "cps2Pcm open failed");
                    goto free_fe;
                }

                if (!pcm_is_ready(cps2Pcm)) {
                    PAL_ERR(LOG_TAG, "cps2Pcm open not ready");
                    goto err_pcm_open;
                }

                enableDevice(audioRoute, mSndDeviceName_cps);
                PAL_DBG(LOG_TAG, "pcm start for CPS");
                if (pcm_start(cps2Pcm) < 0) {
                    PAL_ERR(LOG_TAG, "pcm start failed for CPS path");
                    goto err_pcm_open;
                }
            }
        }

        if (ResourceManager::isSpkrXmaxTmaxLoggingEnabled) {
            XmaxTmaxLogThread = std::thread(&SpeakerProtection::startSpkrXmaxTmaxLogging,
                this);
        }
        // Free up the local variables
        goto exit;
    }
    else {
        if (numberOfRequest == 0) {
            PAL_ERR(LOG_TAG, "Device not started yet, Stop not expected");
            goto exit;
        }
        numberOfRequest--;
        if (numberOfRequest > 0) {
            // R0T0 already set, we don't need to process the request.
            goto exit;
        }
        spkrProtSetSpkrStatus(flag);
        // Speaker not in use anymore. Stop the processing mode
        startXmaxLogging = false;

        PAL_DBG(LOG_TAG, "Closing VI path");
        if (txPcm) {
            rm = ResourceManager::getInstance();
            device.id = PAL_DEVICE_IN_VI_FEEDBACK;

            ret = rm->getAudioRoute(&audioRoute);
            if (0 != ret) {
                PAL_ERR(LOG_TAG, "Failed to get the audio_route address status %d", ret);
                goto exit;
            }

            strlcpy(mSndDeviceName_vi, vi_device.sndDevName.c_str(), DEVICE_NAME_MAX_SIZE);
            rm->getBackendName(device.id, backEndName);
            if (!strlen(backEndName.c_str())) {
                PAL_ERR(LOG_TAG, "Failed to obtain tx backend name for %d", device.id);
                goto exit;
            }
            pcm_stop(txPcm);
            if (pcmDevIdTx.size() != 0) {
                if (isTxFeandBeConnected) {
                    disconnectFeandBe(pcmDevIdTx, backEndName);
                }
                sAttr.type = PAL_STREAM_LOW_LATENCY;
                sAttr.direction = PAL_AUDIO_INPUT_OUTPUT;
                rm->freeFrontEndIds(pcmDevIdTx, sAttr, dir);
                pcmDevIdTx.clear();
            }
            pcm_close(txPcm);
            disableDevice(audioRoute, mSndDeviceName_vi);
            txPcm = NULL;
        }
        PAL_DBG(LOG_TAG, "Closing CPS path");
        if(cps1Pcm || cps2Pcm) {
            rm = ResourceManager::getInstance();
            for (int ch = numberOfChannels; ch != 0; ch = ch >> 2) {
                if (ch != CHANNELS_4)
                    deviceCPS.id = PAL_DEVICE_IN_CPS_FEEDBACK;
                else
                    deviceCPS.id = PAL_DEVICE_IN_CPS2_FEEDBACK;

                ret = rm->getAudioRoute(&audioRoute);
                if (0 != ret) {
                    PAL_ERR(LOG_TAG, "Failed to get the audio_route address status %d", ret);
                    goto exit;
                }

                strlcpy(mSndDeviceName_cps, cps_device.sndDevName.c_str(), DEVICE_NAME_MAX_SIZE);
                rm->getBackendName(deviceCPS.id, backEndNameCPS);
                if (!strlen(backEndNameCPS.c_str())) {
                    PAL_ERR(LOG_TAG, "Failed to obtain CPS backend name for %d", deviceCPS.id);
                    goto exit;
                }
                if (ch != CHANNELS_4) {
                    pcm_stop(cps1Pcm);
                    if (pcmDevIdCPS.size() != 0) {
                        if (isCPSFeandBeConnected) {
                            disconnectFeandBe(pcmDevIdCPS, backEndNameCPS);
                        }
                        sAttr.type = PAL_STREAM_LOW_LATENCY;
                        sAttr.direction = PAL_AUDIO_INPUT_OUTPUT;
                        rm->freeFrontEndIds(pcmDevIdCPS, sAttr, dir);
                        pcmDevIdCPS.clear();
                    }
                    pcm_close(cps1Pcm);
                    disableDevice(audioRoute, mSndDeviceName_cps);
                    cps1Pcm = NULL;
                } else {
                    pcm_stop(cps2Pcm);
                    if (pcmDevIdCPS2.size() != 0) {
                        if (isCPSFeandBeConnected) {
                            disconnectFeandBe(pcmDevIdCPS2, backEndNameCPS);
                        }
                        sAttr.type = PAL_STREAM_LOW_LATENCY;
                        sAttr.direction = PAL_AUDIO_INPUT_OUTPUT;
                        rm->freeFrontEndIds(pcmDevIdCPS2, sAttr, dir);
                        pcmDevIdCPS2.clear();
                    }
                    pcm_close(cps2Pcm);
                    disableDevice(audioRoute, mSndDeviceName_cps);
                    cps2Pcm = NULL;
                }
            }
            goto exit;
        }
    }

err_pcm_open :
    if (txPcm) {
        pcm_close(txPcm);
        disableDevice(audioRoute, mSndDeviceName_vi);
        txPcm = NULL;
    }

    if (cps1Pcm) {
        pcm_close(cps1Pcm);
        ret = rm->getSndDeviceName(PAL_DEVICE_IN_CPS_FEEDBACK , mSndDeviceName_cps);
        disableDevice(audioRoute, mSndDeviceName_cps);
        cps1Pcm = NULL;
    }

    if (cps2Pcm) {
        pcm_close(cps2Pcm);
        ret = rm->getSndDeviceName(PAL_DEVICE_IN_CPS2_FEEDBACK , mSndDeviceName_cps);
        disableDevice(audioRoute, mSndDeviceName_cps);
        cps2Pcm = NULL;
    }

free_fe:
    if (pcmDevIdTx.size() != 0) {
        if (isTxFeandBeConnected) {
            disconnectFeandBe(pcmDevIdTx, backEndName);
        }
        rm->freeFrontEndIds(pcmDevIdTx, sAttr, dir);
        pcmDevIdTx.clear();
    }
    if (pcmDevIdCPS.size() != 0) {
        if (isCPSFeandBeConnected) {
            deviceCPS.id = PAL_DEVICE_IN_CPS_FEEDBACK;
            ret = rm->getSndDeviceName(deviceCPS.id , mSndDeviceName_cps);
            rm->getBackendName(deviceCPS.id, backEndNameCPS);
            disconnectFeandBe(pcmDevIdCPS, backEndNameCPS);
        }
        rm->freeFrontEndIds(pcmDevIdCPS, sAttr, dir);
        pcmDevIdCPS.clear();
    }
    if (pcmDevIdCPS2.size() != 0) {
        if (isCPSFeandBeConnected) {
            deviceCPS.id = PAL_DEVICE_IN_CPS2_FEEDBACK;
            ret = rm->getSndDeviceName(deviceCPS.id , mSndDeviceName_cps);
            rm->getBackendName(deviceCPS.id, backEndNameCPS);
            disconnectFeandBe(pcmDevIdCPS2, backEndNameCPS);
        }
        rm->freeFrontEndIds(pcmDevIdCPS2, sAttr, dir);
        pcmDevIdCPS2.clear();
    }
exit:
    deviceMutex.unlock();
    if(builder) {
       delete builder;
       builder = NULL;
    }
    return ret;
}

void SpeakerProtection::updateSPcustomPayload()
{
    PayloadBuilder* builder = new PayloadBuilder();
    uint8_t* payload = NULL;
    size_t payloadSize = 0;
    std::string backEndName;
    std::shared_ptr<Device> dev = nullptr;
    Stream *stream = NULL;
    Session *session = NULL;
    std::vector<Stream*> activeStreams;
    uint32_t miid = 0, ret, tagid;
    param_id_sp_op_mode_t spModeConfg;

    rm->getBackendName(mDeviceAttr.id, backEndName);
    dev = Device::getInstance(&mDeviceAttr, rm);
    ret = rm->getActiveStream_l(activeStreams, dev);
    if ((0 != ret) || (activeStreams.size() == 0)) {
        PAL_ERR(LOG_TAG, " no active stream available");
        goto exit;
    }
    stream = static_cast<Stream *>(activeStreams[0]);
    stream->getAssociatedSession(&session);
    for (int ch = numberOfChannels; ch != 0; ch == ch >> CHANNELS_2) {
        if (ch == CHANNELS_4)
            tagid = MODULE_SP2;
        else {
            tagid = MODULE_SP;
        }
        ret = session->getMIID(backEndName.c_str(), tagid, &miid);
        if (ret) {
            PAL_ERR(LOG_TAG, "Failed to get tag info %x, status = %d", tagid, ret);
            goto exit;
        }

        if (customPayloadSize) {
            free(customPayload);
            customPayloadSize = 0;
        }

        spModeConfg.operation_mode = NORMAL_MODE;
        payloadSize = 0;
        builder->payloadSPConfig(&payload, &payloadSize, miid,
                        PARAM_ID_SP_OP_MODE,(void *)&spModeConfg);
        if (payloadSize) {
            ret = updateCustomPayload(payload, payloadSize);
            free(payload);
            if (0 != ret) {
                PAL_ERR(LOG_TAG," updateCustomPayload Failed\n");
            }
        }
    }
exit:
    if(builder) {
       delete builder;
       builder = NULL;
    }
    return;
}


int SpeakerProtection::speakerProtectionDynamicCal()
{
    int ret = 0;

    PAL_DBG(LOG_TAG, "Enter");

    if (calThrdCreated) {
        PAL_DBG(LOG_TAG, "Calibration already triggered Thread State %d",
                        calThrdCreated);
        return ret;
    }

    threadExit = false;
    spkrCalState = SPKR_NOT_CALIBRATED;

    calibrationCallbackStatus = 0;
    mDspCallbackRcvd = false;

    calThrdCreated = true;
    isDynamicCalTriggered = true;

    std::thread dynamicCalThread(&SpeakerProtection::spkrCalibrationThread, this);

    dynamicCalThread.detach();

    PAL_DBG(LOG_TAG, "Exit");

    return ret;
}

int SpeakerProtection::start()
{
    PAL_DBG(LOG_TAG, "Enter");

    if (ResourceManager::isVIRecordStarted) {
        PAL_DBG(LOG_TAG, "record running so just update SP payload");
        updateSPcustomPayload();
    }
    else {
        spkrProtProcessingMode(true);
    }

    PAL_DBG(LOG_TAG, "Calling Device start");
    Device::start();
    return 0;
}

int SpeakerProtection::stop()
{
    PAL_DBG(LOG_TAG, "Inside Speaker Protection stop");
    Device::stop();
    if (ResourceManager::isVIRecordStarted) {
        PAL_DBG(LOG_TAG, "record running so no need to proceed");
        ResourceManager::isVIRecordStarted = false;
        return 0;
    }
    spkrProtProcessingMode(false);
    return 0;
}


int32_t SpeakerProtection::setParameter(uint32_t param_id, void *param)
{
    PAL_DBG(LOG_TAG, "Inside Speaker Protection Set parameters");
    (void ) param;
    if (param_id == PAL_SP_MODE_DYNAMIC_CAL)
        speakerProtectionDynamicCal();
    return 0;
}

int32_t SpeakerProtection::getFTMParameter(void **param)
{
    int size = 0, status = 0, tagid = 0;
    int spkr1_status = 0;
    int spkr2_status = 0;
    int spkr3_status = 0;
    int spkr4_status = 0;
    uint32_t miid = 0;
    const char *getParamControl = "getParam";
    char *pcmDeviceName = NULL;
    uint8_t* payload = NULL;
    size_t payloadSize = 0;
    struct mixer_ctl *ctl;
    std::ostringstream cntrlName;
    std::ostringstream resString;
    std::string backendName;
    param_id_sp_th_vi_ftm_params_t ftm;
    param_id_sp_ex_vi_ftm_params_t exFtm;
    PayloadBuilder* builder = new PayloadBuilder();
    vi_th_ftm_params_t ftm_ret[numberOfChannels];
    vi_ex_ftm_params_t exFtm_ret[numberOfChannels];
    param_id_sp_th_vi_ftm_params_t *ftmValue;
    param_id_sp_ex_vi_ftm_params_t *exFtmValue;

    memset(&ftm_ret, 0,sizeof(vi_th_ftm_params_t) * numberOfChannels);
    memset(&exFtm_ret, 0,sizeof(vi_ex_ftm_params_t) * numberOfChannels);

    pcmDeviceName = rm->getDeviceNameFromID(pcmDevIdTx.at(0));
    if (pcmDeviceName) {
        cntrlName<<pcmDeviceName<<" "<<getParamControl;
    } else {
        PAL_ERR(LOG_TAG, "Error: %d Unable to get Device name\n", -EINVAL);
        goto exit;
    }

    ctl = mixer_get_ctl_by_name(virtMixer, cntrlName.str().data());
    if (!ctl) {
        status = -ENOENT;
        PAL_ERR(LOG_TAG, "Error: %d Invalid mixer control: %s\n", status,cntrlName.str().data());
        goto exit;
    }
    rm->getBackendName(PAL_DEVICE_IN_VI_FEEDBACK, backendName);
    if (!strlen(backendName.c_str())) {
        status = -ENOENT;
        PAL_ERR(LOG_TAG, "Error: %d Failed to obtain VI backend name", status);
        goto exit;
    }

    for (int ch = numberOfChannels; ch != 0; ch == ch >> CHANNELS_2) {

        if (ch == CHANNELS_4)
            tagid = MODULE_VI2;
        else
            tagid = MODULE_VI;

        status = SessionAlsaUtils::getModuleInstanceId(virtMixer, pcmDevIdTx.at(0),
                            backendName.c_str(), tagid, &miid);
        if (0 != status) {
            PAL_ERR(LOG_TAG, "Error: %d Failed to get tag info %x", status, MODULE_VI);
            goto exit;
        }
        if (numberOfChannels == CHANNELS_4)
            ftm.num_ch = CHANNELS_2;
        else
            ftm.num_ch = numberOfChannels;

        builder->payloadSPConfig (&payload, &payloadSize, miid,
                PARAM_ID_SP_TH_VI_FTM_PARAMS, &ftm);

        status = mixer_ctl_set_array(ctl, payload, payloadSize);
        if (0 != status) {
            PAL_ERR(LOG_TAG, "Set failed status = %d", status);
            goto exit;
        }

        memset(payload, 0, payloadSize);

        status = mixer_ctl_get_array(ctl, payload, payloadSize);
        if (0 != status) {
            PAL_ERR(LOG_TAG, "Get failed status = %d", status);
        } else {
            ftmValue = (param_id_sp_th_vi_ftm_params_t *) (payload +
                            sizeof(struct apm_module_param_data_t));
            if (ch == CHANNELS_4) {
                for (int i = 2, j = 0; i < numberOfChannels; i++, j++) {
                    ftm_ret[i].ftm_rDC_q24 = ftmValue->vi_th_ftm_params[j].ftm_rDC_q24;
                    ftm_ret[i].ftm_temp_q22 = ftmValue->vi_th_ftm_params[j].ftm_temp_q22;
                    ftm_ret[i].status = ftmValue->vi_th_ftm_params[j].status;
                }
            } else {
                for (int i = 0, j = 0; i < ftm.num_ch; i++, j++) {
                    ftm_ret[i].ftm_rDC_q24 = ftmValue->vi_th_ftm_params[j].ftm_rDC_q24;
                    ftm_ret[i].ftm_temp_q22 = ftmValue->vi_th_ftm_params[j].ftm_temp_q22;
                    ftm_ret[i].status = ftmValue->vi_th_ftm_params[j].status;
                }
            }
        }

        PAL_DBG(LOG_TAG, "Got FTM value with status %d", ftm_ret[0].status);

        if (payload) {
            delete payload;
            payloadSize = 0;
            payload = NULL;
        }
        if (numberOfChannels == CHANNELS_4)
            exFtm.num_ch = CHANNELS_2;
        else
            exFtm.num_ch = numberOfChannels;

        builder->payloadSPConfig (&payload, &payloadSize, miid,
                PARAM_ID_SP_EX_VI_FTM_PARAMS, &exFtm);

        status = mixer_ctl_set_array(ctl, payload, payloadSize);
        if (0 != status) {
            PAL_ERR(LOG_TAG, "Error: %d Mixer cntrl Set failed", status);
            goto exit;
        }

        memset(payload, 0, payloadSize);

        status = mixer_ctl_get_array(ctl, payload, payloadSize);
        if (0 != status) {
            PAL_ERR(LOG_TAG, "Error: %d Get failed ", status);
        }
        else {
            exFtmValue = (param_id_sp_ex_vi_ftm_params_t *) (payload +
                                    sizeof(struct apm_module_param_data_t));
            if (ch == CHANNELS_4) {
                for (int i = 2, j = 0; i < numberOfChannels; i++, j++) {
                    exFtm_ret[i].ftm_Re_q24 = exFtmValue->fbsp_ex_vi_ftm_get_param[j].ftm_Re_q24;
                    exFtm_ret[i].ftm_Bl_q24 = exFtmValue->fbsp_ex_vi_ftm_get_param[j].ftm_Bl_q24;
                    exFtm_ret[i].ftm_Kms_q24 = exFtmValue->fbsp_ex_vi_ftm_get_param[j].ftm_Kms_q24;
                    exFtm_ret[i].ftm_Fres_q20 = exFtmValue->fbsp_ex_vi_ftm_get_param[j].ftm_Fres_q20;
                    exFtm_ret[i].ftm_Qms_q24 = exFtmValue->fbsp_ex_vi_ftm_get_param[j].ftm_Qms_q24;
                    exFtm_ret[i].status = exFtmValue->fbsp_ex_vi_ftm_get_param[j].status;
                }
            } else {
                for (int i = 0, j = 0; i < exFtm.num_ch; i++, j++) {
                    exFtm_ret[i].ftm_Re_q24 = exFtmValue->fbsp_ex_vi_ftm_get_param[j].ftm_Re_q24;
                    exFtm_ret[i].ftm_Bl_q24 = exFtmValue->fbsp_ex_vi_ftm_get_param[j].ftm_Bl_q24;
                    exFtm_ret[i].ftm_Kms_q24 = exFtmValue->fbsp_ex_vi_ftm_get_param[j].ftm_Kms_q24;
                    exFtm_ret[i].ftm_Fres_q20 = exFtmValue->fbsp_ex_vi_ftm_get_param[j].ftm_Fres_q20;
                    exFtm_ret[i].ftm_Qms_q24 = exFtmValue->fbsp_ex_vi_ftm_get_param[j].ftm_Qms_q24;
                    exFtm_ret[i].status = exFtmValue->fbsp_ex_vi_ftm_get_param[j].status;
                }
            }
        }
        PAL_DBG(LOG_TAG, "Got FTM Excursion value with status %d", exFtm_ret[0].status);

        if (payload) {
            delete payload;
            payloadSize = 0;
            payload = NULL;
        }
    }
    switch(numberOfChannels) {
        case 1 :
            if (exFtm_ret[0].status == 4 && ftm_ret[0].status == 4)
                spkr1_status = 1;
            resString << "SpkrParamStatus: " << spkr1_status << "; Rdc: "
                    << ((ftm_ret[0].ftm_rDC_q24)/(1<<24)) << "; Temp: "
                    << ((ftm_ret[0].ftm_temp_q22)/(1<<22)) << "; Res: "
                    << ((exFtm_ret[0].ftm_Re_q24)/(1<<24)) << "; Bl: "
                    << ((exFtm_ret[0].ftm_Bl_q24)/(1<<24)) << "; Rms: "
                    << ((exFtm_ret[0].ftm_Rms_q24)/(1<<24)) << "; Kms: "
                    << ((exFtm_ret[0].ftm_Kms_q24)/(1<<24)) << "; Fres: "
                    << ((exFtm_ret[0].ftm_Fres_q20)/(1<<20)) << "; Qms: "
                    << ((exFtm_ret[0].ftm_Qms_q24)/(1<<24));
        break;
        case 2 :
            if (exFtm_ret[0].status == 4 && ftm_ret[0].status == 4)
                spkr1_status = 1;
            if (exFtm_ret[1].status == 4 && ftm_ret[1].status == 4)
                spkr2_status = 1;
            resString << "SpkrParamStatus: " << spkr1_status <<", "<< spkr2_status
                    << "; Rdc: " << (((float)ftm_ret[0].ftm_rDC_q24)/(1<<24)) << ", "
                    << (((float)ftm_ret[1].ftm_rDC_q24)/(1<<24)) << "; Temp: "
                    << ((ftm_ret[0].ftm_temp_q22)/(1<<22)) << ", "
                    << ((ftm_ret[1].ftm_temp_q22)/(1<<22)) <<"; Res: "
                    << ((exFtm_ret[0].ftm_Re_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[1].ftm_Re_q24)/(1<<24)) << "; Bl: "
                    << ((exFtm_ret[0].ftm_Bl_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[1].ftm_Bl_q24)/(1<<24)) << "; Rms: "
                    << ((exFtm_ret[0].ftm_Rms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[1].ftm_Rms_q24)/(1<<24)) << "; Kms: "
                    << ((exFtm_ret[0].ftm_Kms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[1].ftm_Kms_q24)/(1<<24)) << "; Fres: "
                    << (((float)exFtm_ret[0].ftm_Fres_q20)/(1<<20)) << ", "
                    << (((float)exFtm_ret[1].ftm_Fres_q20)/(1<<20)) << "; Qms: "
                    << ((exFtm_ret[0].ftm_Qms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[1].ftm_Qms_q24)/(1<<24));
            break;
            case 4 :
            if (exFtm_ret[0].status == 4 && ftm_ret[0].status == 4)
                spkr1_status = 1;
            if (exFtm_ret[1].status == 4 && ftm_ret[1].status == 4)
                spkr2_status = 1;
            if (exFtm_ret[2].status == 4 && ftm_ret[2].status == 4)
                spkr3_status = 1;
            if (exFtm_ret[3].status == 4 && ftm_ret[3].status == 4)
                spkr4_status = 1;
            resString << "SpkrParamStatus: " << spkr1_status <<", "<< spkr2_status
                    <<", "<< spkr3_status <<", "<< spkr4_status << "; Rdc: "
                    << ((ftm_ret[0].ftm_rDC_q24)/(1<<24)) << ", "
                    << ((ftm_ret[1].ftm_rDC_q24)/(1<<24)) << ", "
                    << ((ftm_ret[2].ftm_rDC_q24)/(1<<24)) <<", "
                    << ((ftm_ret[3].ftm_rDC_q24)/(1<<24)) <<"; Temp: "
                    << ((ftm_ret[0].ftm_temp_q22)/(1<<22)) << ", "
                    << ((ftm_ret[1].ftm_temp_q22)/(1<<22)) << ", "
                    << ((ftm_ret[2].ftm_temp_q22)/(1<<22)) << ", "
                    << ((ftm_ret[3].ftm_temp_q22)/(1<<22)) <<"; Res: "
                    << ((exFtm_ret[0].ftm_Re_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[1].ftm_Re_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[2].ftm_Re_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[3].ftm_Re_q24)/(1<<24)) << "; Bl: "
                    << ((exFtm_ret[0].ftm_Bl_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[1].ftm_Bl_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[2].ftm_Bl_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[3].ftm_Bl_q24)/(1<<24)) << "; Rms: "
                    << ((exFtm_ret[0].ftm_Rms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[1].ftm_Rms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[2].ftm_Rms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[3].ftm_Rms_q24)/(1<<24)) << "; Kms: "
                    << ((exFtm_ret[0].ftm_Kms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[1].ftm_Kms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[2].ftm_Kms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[3].ftm_Kms_q24)/(1<<24)) << "; Fres: "
                    << ((exFtm_ret[0].ftm_Fres_q20)/(1<<20)) << ", "
                    << ((exFtm_ret[1].ftm_Fres_q20)/(1<<20)) << ", "
                    << ((exFtm_ret[2].ftm_Fres_q20)/(1<<20)) << ", "
                    << ((exFtm_ret[3].ftm_Fres_q20)/(1<<20)) << "; Qms: "
                    << ((exFtm_ret[0].ftm_Qms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[1].ftm_Qms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[2].ftm_Qms_q24)/(1<<24)) << ", "
                    << ((exFtm_ret[3].ftm_Qms_q24)/(1<<24));
            break;
            default :
                PAL_ERR(LOG_TAG, "No support for Speakers > 2");
            goto exit;
    }

    PAL_DBG(LOG_TAG, "Get param value %s", resString.str().c_str());
    if (resString.str().length() > 0) {
        memcpy((char *) (param), resString.str().c_str(),
                resString.str().length());
        size = resString.str().length();

        // Get is done now, we will clear up the stored mode now
        memset(&(rm->mSpkrProtModeValue), 0, sizeof(pal_spkr_prot_payload));
    }

exit :
    if(builder) {
       delete builder;
       builder = NULL;
    }
    if(!status)
       return size;
    else
      return status;

}

int32_t SpeakerProtection::getCalibrationData(void **param)
{
    int i, tempCH = 0, status = 0;
    struct vi_r0t0_cfg_t r0t0Array[numberOfChannels];
    double dr0[numberOfChannels];
    double dt0[numberOfChannels];
    std::ostringstream resString;
    FILE *fp;

    memset(r0t0Array, 0, sizeof(vi_r0t0_cfg_t) * numberOfChannels);
    memset(dr0, 0, sizeof(double) * numberOfChannels);
    memset(dt0, 0, sizeof(double) * numberOfChannels);
    for (int ch = numberOfChannels; ch != 0; ch = ch >> CHANNELS_2) {
        if (ch == CHANNELS_4) {
            fp = fopen(PAL_SP_II_TEMP_PATH, "rb");
            tempCH = CHANNELS_2;
        } else
            fp = fopen(PAL_SP_I_TEMP_PATH, "rb");
        if (fp) {
            for (i = 0; i < MaxCH; i++) {
                fread(&r0t0Array[i].r0_cali_q24,
                        sizeof(r0t0Array[i].r0_cali_q24), 1, fp);
                fread(&r0t0Array[i].t0_cali_q6,
                        sizeof(r0t0Array[i].t0_cali_q6), 1, fp);
                // Convert to readable format
                dr0[i+tempCH] = ((double)r0t0Array[i].r0_cali_q24)/(1 << 24);
                dt0[i+tempCH] = ((double)r0t0Array[i].t0_cali_q6)/(1 << 6);
                PAL_DBG(LOG_TAG, "R0= %lf, T0= %lf", dr0[i+tempCH], dt0[i+tempCH]);
            }
            fclose(fp);
        }
        else {
            status = -EINVAL;
            PAL_ERR(LOG_TAG, "No cal file present");
        }
        tempCH = CHANNELS_2;
    }
    if (numberOfChannels == CHANNELS_4)
        resString << "SpkrCalStatus: " << status << "; R0: " << dr0[0] << ", "
                  << dr0[1] << ", " << dr0[2] << ", " << dr0[3] << "; T0: "
                  << dt0[0] << ", " << dt0[1] << ", " << dt0[2] << ", " << dt0[3]
                  << ";";
    else
        resString << "SpkrCalStatus: " << status << "; R0: " << dr0[0] << ", "
                  << dr0[1] << "; T0: "<< dt0[0] << ", " << dt0[1] << ";";

    PAL_DBG(LOG_TAG, "Calibration value %s", resString.str().c_str());

    memcpy((char *) (param), resString.str().c_str(), resString.str().length());

    if(!status)
       return resString.str().length();
    else
    return status;

}

int32_t SpeakerProtection::getParameter(uint32_t param_id, void **param)
{
    int32_t status = 0;
    switch(param_id) {
        case PAL_PARAM_ID_SP_GET_CAL:
            status = getCalibrationData(param);
        break;
        case PAL_PARAM_ID_SP_MODE:
            status = getFTMParameter(param);
        break;
        default :
            PAL_ERR(LOG_TAG, "Unsupported operation");
            status = -EINVAL;
        break;
    }
    return status;
}

/*
 * VI feedack related functionalities
 */

void SpeakerFeedback::updateVIcustomPayload()
{
    PayloadBuilder* builder = new PayloadBuilder();
    uint8_t* payload = NULL;
    size_t payloadSize = 0;
    std::string backEndName;
    std::shared_ptr<Device> dev = nullptr;
    Stream *stream = NULL;
    Session *session = NULL;
    std::vector<Stream*> activeStreams;
    uint32_t miid = 0, ret = 0,tagid;
    struct vi_r0t0_cfg_t r0t0Array[numSpeaker];
    FILE *fp = NULL;
    int Channels, tempCH;
    param_id_sp_th_vi_r0t0_cfg_t *spR0T0confg;
    param_id_sp_vi_op_mode_cfg_t modeConfg;
    param_id_sp_vi_channel_map_cfg_t viChannelMapConfg;
    param_id_sp_ex_vi_mode_cfg_t viExModeConfg;

    rm->getBackendName(mDeviceAttr.id, backEndName);
    dev = Device::getInstance(&mDeviceAttr, rm);
    ret = rm->getActiveStream_l(activeStreams, dev);
    if ((0 != ret) || (activeStreams.size() == 0)) {
        PAL_ERR(LOG_TAG, " no active stream available");
        goto exit;
    }
    stream = static_cast<Stream *>(activeStreams[0]);
    stream->getAssociatedSession(&session);

    if (numSpeaker == CHANNELS_4)
        tempCH = CHANNELS_2;
    else
        tempCH = numSpeaker;

    for (int ch = numSpeaker; ch != 0; ch >> CHANNELS_2) {
        if (ch == CHANNELS_4)
            tagid = MODULE_VI2;
        else{
            tagid = MODULE_VI;
        }
        ret = session->getMIID(backEndName.c_str(), tagid, &miid);
        if (ret) {
            PAL_ERR(LOG_TAG, "Failed to get tag info %x, status = %d", tagid, ret);
            goto exit;
        }

        if (customPayloadSize) {
            free(customPayload);
            customPayloadSize = 0;
        }

        memset(&modeConfg, 0, sizeof(modeConfg));
        memset(&viChannelMapConfg, 0, sizeof(viChannelMapConfg));
        memset(&viExModeConfg, 0, sizeof(viExModeConfg));
        memset(&r0t0Array, 0, sizeof(struct vi_r0t0_cfg_t) * numSpeaker);

        // Setting the mode of VI module
        if (numSpeaker == CHANNELS_4)
            modeConfg.num_speakers = CHANNELS_2;
        else {
            modeConfg.num_speakers = numSpeaker;
        }
        modeConfg.th_operation_mode = NORMAL_MODE;
        modeConfg.th_quick_calib_flag = 0;
        builder->payloadSPConfig(&payload, &payloadSize, miid,
                                 PARAM_ID_SP_VI_OP_MODE_CFG,(void *)&modeConfg);
        if (payloadSize) {
            ret = updateCustomPayload(payload, payloadSize);
            free(payload);
            if (0 != ret) {
                PAL_ERR(LOG_TAG," updateCustomPayload Failed for VI_OP_MODE_CFG\n");
            }
        }

        // Setting Channel Map configuration for VI module
        if (numSpeaker == CHANNELS_4)
            viChannelMapConfg.num_ch = CHANNELS_2 * 2;
        else
            viChannelMapConfg.num_ch = numSpeaker * 2;
        payloadSize = 0;

        builder->payloadSPConfig(&payload, &payloadSize, miid,
                        PARAM_ID_SP_VI_CHANNEL_MAP_CFG,(void *)&viChannelMapConfg);
        if (payloadSize) {
            ret = updateCustomPayload(payload, payloadSize);
            free(payload);
            if (0 != ret) {
                PAL_ERR(LOG_TAG," updateCustomPayload Failed for CHANNEL_MAP_CFG\n");
            }
        }

        if (ch == CHANNELS_4) {
            fp = fopen(PAL_SP_II_TEMP_PATH, "rb");
        }
        else
            fp = fopen(PAL_SP_I_TEMP_PATH, "rb");

        if (fp) {
            PAL_DBG(LOG_TAG, "Speaker calibrated. Send calibrated value");
            for (int i = 0; i < tempCH; i++) {
                fread(&r0t0Array[i].r0_cali_q24,
                        sizeof(r0t0Array[i].r0_cali_q24), 1, fp);
                fread(&r0t0Array[i].t0_cali_q6,
                        sizeof(r0t0Array[i].t0_cali_q6), 1, fp);
            }
            fclose(fp);
        }
        else {
            PAL_DBG(LOG_TAG, "Speaker not calibrated. Send safe value");
            for (int i = 0; i < tempCH; i++) {
                r0t0Array[i].r0_cali_q24 = MIN_RESISTANCE_SPKR_Q24;
                r0t0Array[i].t0_cali_q6 = SAFE_SPKR_TEMP_Q6;
            }
        }
        spR0T0confg = (param_id_sp_th_vi_r0t0_cfg_t *)calloc(1,
                       sizeof(param_id_sp_th_vi_r0t0_cfg_t) +
                       sizeof(vi_r0t0_cfg_t) * tempCH);
        if (!spR0T0confg) {
            PAL_ERR(LOG_TAG," unable to create speaker config payload\n");
            return;
        }


        if (numSpeaker == CHANNELS_4)
            spR0T0confg->num_ch = CHANNELS_2;
        else
            spR0T0confg->num_ch = numSpeaker;

        memcpy(spR0T0confg->r0t0_cfg, r0t0Array, sizeof(vi_r0t0_cfg_t) *
                spR0T0confg->num_ch);

        payloadSize = 0;
        builder->payloadSPConfig(&payload, &payloadSize, miid,
                        PARAM_ID_SP_TH_VI_R0T0_CFG,(void *)spR0T0confg);
        if (payloadSize) {
            ret = updateCustomPayload(payload, payloadSize);
            free(payload);
            free(spR0T0confg);
            if (0 != ret) {
                PAL_ERR(LOG_TAG," updateCustomPayload Failed\n");
            }
        }
    }
exit:
    if(builder) {
       delete builder;
       builder = NULL;
    }
    return;
}

SpeakerFeedback::SpeakerFeedback(struct pal_device *device,
                                std::shared_ptr<ResourceManager> Rm):Device(device, Rm)
{
    struct pal_device_info devinfo = {};

    memset(&mDeviceAttr, 0, sizeof(struct pal_device));
    memcpy(&mDeviceAttr, device, sizeof(struct pal_device));
    rm = Rm;


    rm->getDeviceInfo(mDeviceAttr.id, PAL_STREAM_PROXY, mDeviceAttr.custom_config.custom_key, &devinfo);
    numSpeaker = devinfo.channels;
}

SpeakerFeedback::~SpeakerFeedback()
{
}

int32_t SpeakerFeedback::start()
{
    ResourceManager::isVIRecordStarted = true;
    // Do the customPayload configuration for VI path and call the Device::start
    PAL_DBG(LOG_TAG," Feedback start\n");
    if (rm->isSpeakerProtectionEnabled)
        updateVIcustomPayload();

    Device::start();

    return 0;
}

int32_t SpeakerFeedback::stop()
{
    ResourceManager::isVIRecordStarted = false;
    PAL_DBG(LOG_TAG," Feedback stop\n");
    Device::stop();

    return 0;
}

std::shared_ptr<Device> SpeakerFeedback::getInstance(struct pal_device *device,
                                                     std::shared_ptr<ResourceManager> Rm)
{
    PAL_DBG(LOG_TAG," Feedback getInstance\n");
    if (!obj) {
        std::shared_ptr<Device> sp(new SpeakerFeedback(device, Rm));
        obj = sp;
    }
    return obj;
}

std::shared_ptr<Device> SpeakerFeedback::getObject()
{
    return obj;
}

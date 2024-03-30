/*
 * Copyright (c) 2020, 2021 The Linux Foundation. All rights reserved.
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
 * Copyright (c) 2022-2023 Qualcomm Innovation Center, Inc. All rights reserved.
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

#ifndef SPEAKER_PROT
#define SPEAKER_PROT

#include "Device.h"
#include "sp_vi.h"
#include "sp_rx.h"
#include "cps_data_router.h"
#include <tinyalsa/asoundlib.h>
#include <mutex>
#include <condition_variable>
#include <thread>
#include<vector>
#include "apm_api.h"

class Device;

#define CPS_WSA_VBATT_REG_ADDR 0x0003429
#define CPS_WSA_TEMP_REG_ADDR 0x0003422

#define CPS_WSA_VBATT_LOWER_THRESHOLD_1 168
#define CPS_WSA_VBATT_LOWER_THRESHOLD_2 148

#define WSA2_REGISTER_ADD 1
#define WSA_REGISTER_ADD 0

typedef enum speaker_prot_cal_state {
    SPKR_NOT_CALIBRATED,     /* Speaker not calibrated  */
    SPKR_CALIBRATED,         /* Speaker calibrated  */
    SPKR_CALIB_IN_PROGRESS,  /* Speaker calibration in progress  */
}spkr_prot_cal_state;

typedef enum speaker_prot_proc_state {
    SPKR_PROCESSING_IN_IDLE,     /* Processing mode in idle state */
    SPKR_PROCESSING_IN_PROGRESS, /* Processing mode in running state */
}spkr_prot_proc_state;

enum {
    SPKR_RIGHT,    /* Right Speaker */
    SPKR_LEFT,     /* Left Speaker */
    SPKR_REAR_RIGHT, /*Rear Right Speaker */
    SPKR_REAR_LEFT,  /*Rear Left Speaker */
};

/* enum that indicates speaker condition. */
enum {
    SPKR_OK = 0,
    SPKR_DC = 1,
    SPKR_OPEN = 2,
    SPKR_CLOSE = 3,
};

struct agmMetaData {
    uint8_t *buf;
    uint32_t size;
    agmMetaData(uint8_t *b, uint32_t s)
        :buf(b),size(s) {}
};


class SpeakerProtection : public Device
{
protected :
    bool spkrProtEnable;
    bool threadExit;
    bool triggerCal;
    int minIdleTime;
    static speaker_prot_cal_state spkrCalState;
    spkr_prot_proc_state spkrProcessingState;
    int *spkerTempList;
    static bool isSpkrInUse;
    static bool startXmaxLogging;
    static bool calThrdCreated;
    static bool isDynamicCalTriggered;
    static struct timespec spkrLastTimeUsed;
    static struct mixer *virtMixer;
    static struct mixer *hwMixer;
    static struct pcm *rxPcm;
    static struct pcm *txPcm;
    static struct pcm *cps1Pcm;
    static struct pcm *cps2Pcm;
    static int numberOfChannels;
    static int MaxCH;
    static uint32_t source_miid, vi_miid_I, vi_miid_II;
    static bool mDspCallbackRcvd;
    static param_id_sp_th_vi_calib_res_cfg_t *callback_data;
    struct pal_device mDeviceAttr;
    std::vector<int> pcmDevIdTx;
    std::vector<int> pcmDevIdCPS;
    std::vector<int> pcmDevIdCPS2;
    static int calibrationCallbackStatus;
    static int numberOfRequest;
    static struct pal_device_info vi_device;
    static struct pal_device_info cps_device;

private :

public:
    static std::thread mCalThread;
    static std::thread XmaxTmaxLogThread;
    static std::condition_variable cv;
    static std::mutex cvMutex;
    std::mutex deviceMutex;
    static std::mutex calibrationMutex;
    void spkrCalibrationThread();
    void startSpkrXmaxTmaxLogging();
    int getSpeakerTemperature(int spkr_pos);
    void spkrCalibrateWait();
    int spkrStartCalibration();
    void speakerProtectionInit();
    void speakerProtectionDeinit();
    void getSpeakerTemperatureList();
    static void spkrProtSetSpkrStatus(bool enable);
    static int setConfig(int type, int tag, int tagValue, int devId, const char *aif);
    bool isSpeakerInUse(unsigned long *sec);

    SpeakerProtection(struct pal_device *device,
                      std::shared_ptr<ResourceManager> Rm);
    ~SpeakerProtection();

    int32_t start();
    int32_t stop();

    int32_t setParameter(uint32_t param_id, void *param) override;
    int32_t getParameter(uint32_t param_id, void **param) override;

    int32_t spkrProtProcessingMode(bool flag);
    int speakerProtectionDynamicCal();
    void updateSPcustomPayload();
    static int32_t spkrProtSetR0T0Value(vi_r0t0_cfg_t r0t0Array[]);
    static std::string getDCDetSpkrCtrl(uint8_t spkr_pos, uint32_t miid);
    static void handleSPCallback (uint64_t hdl, uint32_t event_id, void *event_data,
                                  uint32_t event_size, uint32_t miid);
    void updateCpsCustomPayload(int miid, uint32_t phy_add[], int wsa2_enable);
    int getCpsDevNumber(std::string mixer);
    int32_t getCalibrationData(void **param);
    int32_t getFTMParameter(void **param);
    int32_t getSpkrXmaxTmaxData();
    void disconnectFeandBe(std::vector<int> pcmDevIds, std::string backEndName);
};

class SpeakerFeedback : public Device
{
    protected :
    struct pal_device mDeviceAttr;
    static std::shared_ptr<Device> obj;
    static int numSpeaker;
    public :
    int32_t start();
    int32_t stop();
    SpeakerFeedback(struct pal_device *device,
                    std::shared_ptr<ResourceManager> Rm);
    ~SpeakerFeedback();
    void updateVIcustomPayload();
    static std::shared_ptr<Device> getInstance(struct pal_device *device,
                                               std::shared_ptr<ResourceManager> Rm);
    static std::shared_ptr<Device> getObject();
};

#endif

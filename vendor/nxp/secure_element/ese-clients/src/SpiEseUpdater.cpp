/******************************************************************************
 *
 *  Copyright 2018-2020 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#define LOG_TAG "JcDnld_SPI"
#include "SpiEseUpdater.h"
#include <cutils/log.h>
#include <dirent.h>
#include <stdlib.h>
#include <pthread.h>
#include <JcDnld.h>
#include "phNxpEse_Apdu_Api.h"
#include <unistd.h>
#include <string.h>
#include <sys/stat.h>
#include <ese_config.h>
#include "phNxpEse_Spm.h"
#include "hal_nxpese.h"
#include "NxpEse.h"
#include "NfcAdaptation.h"
#include <vendor/nxp/nxpnfc/2.0/INxpNfc.h>
#include "SeChannelCallback.h"
#include "SeEvtCallback.h"

using vendor::nxp::nxpese::V1_0::implementation::NxpEse;
using vendor::nxp::nxpnfc::V2_0::INxpNfc;
using android::sp;
using android::hardware::Void;
using android::hardware::hidl_vec;
sp <INxpNfc> mHalNxpNfc = nullptr;
ESE_UPDATE_STATE SpiEseUpdater::msEseUpdate;
ESE_UPDATE_STATE SpiEseUpdater::msDwpEseUpdate;
spSeChannel SpiEseUpdater::seChannelCallback = nullptr;
spSeEvt SpiEseUpdater::seEventCallback = nullptr;
SpiEseUpdater SpiEseUpdater::sEseUpdaterInstance;
eseUpdateInfo_t SpiEseUpdater::msEseSpiIntfInfo, SpiEseUpdater::msEseDwpIntfInfo;

/*******************************************************************************
**
** Function:        eSEUpdate_SE_SeqHandler
**
** Description:     Perform JCOP Download
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
void* eSEUpdate_SE_SeqHandler(void* data);

SpiEseUpdater::SpiEseUpdater() { mEseUpdaterClientContext = NULL; }
SpiEseUpdater& SpiEseUpdater::getInstance() { return sEseUpdaterInstance; }

void SpiEseUpdater::checkIfEseClientUpdateReqd() {
  ALOGD("%s enter:  ", __func__);
  msEseSpiIntfInfo = eseUpdateChecker.checkEseUpdateRequired(ESE_INTF_SPI);
  msEseDwpIntfInfo = eseUpdateChecker.checkEseUpdateRequired(ESE_INTF_NFC);
  if ((msEseSpiIntfInfo.isJcopUpdateRequired || msEseSpiIntfInfo.isLSUpdateRequired))
    SpiEseUpdater::setSpiEseClientState(ESE_UPDATE_STARTED);
}

/*******************************************************************************
**
** Function:    IoctlCallback
**
** Description: Callback from HAL stub for IOCTL api invoked.
**              Output data for IOCTL is sent as argument
**
** Returns:     None.
**
*******************************************************************************/
void IoctlCallback_eSEClient(::android::hardware::nfc::V1_0::NfcData outputData) {
  const char* func = "IoctlCallback_eSEClient";
  ese_nxp_ExtnOutputData_t* pOutData =
      (ese_nxp_ExtnOutputData_t*)&outputData[0];
  ALOGD("%s Ioctl Type=%lu",
         func,(unsigned long)pOutData->ioctlType);
  NfcAdaptation* pAdaptation = (NfcAdaptation*)pOutData->context;
  /*Output Data from stub->Proxy is copied back to output data
   * This data will be sent back to libnfc*/
  memcpy(&pAdaptation->mCurrentIoctlData->out, &outputData[0],
         sizeof(ese_nxp_ExtnOutputData_t));
  ALOGD("%s Ioctl Type status = %d ",
         func,pOutData->data.status);
}

SESTATUS SpiEseUpdater::doEseUpdateIfReqd(
    std::shared_ptr<IEseUpdateCompletedCallback> eseUpdateCompletedCallback,
    void *context) {
  eSEClientUpdateHandler();
  msEseUpdateCompletedCallback = eseUpdateCompletedCallback;
  mEseUpdaterClientContext = context;
  return SESTATUS_SUCCESS;
}

void eSEClientUpdate_SE_Thread()
{
  SESTATUS status = SESTATUS_FAILED;
  pthread_t thread;
  pthread_attr_t attr;
  pthread_attr_init(&attr);
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
  if (pthread_create(&thread, &attr, &eSEUpdate_SE_SeqHandler, NULL) != 0) {
    ALOGD("Thread creation failed");
    status = SESTATUS_FAILED;
  } else {
    status = SESTATUS_SUCCESS;
    ALOGD("Thread creation success");
  }
    pthread_attr_destroy(&attr);
}

void* eSEUpdate_SE_SeqHandler(void* data) {
  (void)data;
  ALOGD("%s Enter\n", __func__);
  SpiEseUpdater::eseUpdateSeqHandler();
  ALOGD("%s Exit eseUpdate_SE_SeqHandler\n", __func__);
  return NULL;
}

void SpiEseUpdater::eSEClientUpdateHandler() {
  ALOGD("%s Enter\n", __func__);
  if(msEseDwpIntfInfo.isJcopUpdateRequired) {
      SpiEseUpdater::setDwpEseClientState(ESE_JCOP_UPDATE_REQUIRED);
      return;
    }
    else if(msEseSpiIntfInfo.isJcopUpdateRequired) {
      SpiEseUpdater::setSpiEseClientState(ESE_JCOP_UPDATE_REQUIRED);
    }

  if((ESE_JCOP_UPDATE_REQUIRED != SpiEseUpdater::msEseUpdate) &&
      (msEseDwpIntfInfo.isLSUpdateRequired)) {
      SpiEseUpdater::setDwpEseClientState(ESE_LS_UPDATE_REQUIRED);
      return;
  } else if(msEseSpiIntfInfo.isLSUpdateRequired) {
    SpiEseUpdater::setSpiEseClientState(ESE_LS_UPDATE_REQUIRED);
  }

  if((SpiEseUpdater::msEseUpdate == ESE_JCOP_UPDATE_REQUIRED) ||
    (SpiEseUpdater::msEseUpdate == ESE_LS_UPDATE_REQUIRED)) {
      eseUpdateSeqHandler();
    }
  ALOGD("%s Exit \n", __func__);
}

SESTATUS SpiEseUpdater::handleJcopOsDownload() {
  SESTATUS status = SESTATUS_FAILED;
  status = SpiEseUpdater::initializeEse(ESE_MODE_OSU, ESE);
  ALOGD("%s doUpdate:  ", __func__);
  seChannelCallback = std::make_shared<SeChannelCallback>();
  seEventCallback = std::make_shared<SeEvtCallback>();
  jcDnld.registerSeCallback(seChannelCallback, seEventCallback);
  status = (SESTATUS) jcDnld.doUpdate();
  if (status != SESTATUS_SUCCESS)
    ALOGD("%s Exit JCOP update failed\n", __func__);
  return status;
}

SESTATUS SpiEseUpdater::initializeEse(phNxpEse_initMode mode, __attribute__((unused)) SEDomainID Id) {
  uint8_t retstat;
  SESTATUS status = SESTATUS_FAILED;
  phNxpEse_initParams initParams;
  memset(&initParams, 0x00, sizeof(phNxpEse_initParams));

  initParams.initMode = mode;
  ALOGE("%s: Mode = %d", __FUNCTION__, mode);
  retstat = phNxpEse_open(initParams, false);
  if (retstat != ESESTATUS_SUCCESS) {
    return status;
  }
  retstat = phNxpEse_init(initParams);
  if(retstat != ESESTATUS_SUCCESS)
  {
    phNxpEse_close(false);
    return status;
  }
  status = SESTATUS_SUCCESS;
  return status;
}

void SpiEseUpdater::setSpiEseClientState(uint8_t state) {
  ALOGE("%s: State = %d", __FUNCTION__, state);
  SpiEseUpdater::msEseUpdate = (ESE_UPDATE_STATE)state;
}

void SpiEseUpdater::setDwpEseClientState(uint8_t state) {
  ALOGE("%s: State = %d", __FUNCTION__, state);
  SpiEseUpdater::msDwpEseUpdate = (ESE_UPDATE_STATE)state;
}

void SpiEseUpdater::sendeSEUpdateState(ESE_UPDATE_STATE state) {
  ALOGE("%s: State = %d", __FUNCTION__, state);
  phNxpEse_SPM_SetEseClientUpdateState(state);
}

SESTATUS SpiEseUpdater::eseUpdateSeqHandler()
{
    switch(SpiEseUpdater::msEseUpdate)
    {
      case ESE_UPDATE_STARTED:
      break;
      case ESE_JCOP_UPDATE_REQUIRED:
        ALOGE("%s: ESE_JCOP_UPDATE_REQUIRED", __FUNCTION__);
        if(msEseSpiIntfInfo.isJcopUpdateRequired) {
          if (msEseSpiIntfInfo.sJcopUpdateInterface == ESE_INTF_SPI) {
            SpiEseUpdater::handleJcopOsDownload();
            return SESTATUS_SUCCESS;
          } else if (msEseSpiIntfInfo.sJcopUpdateInterface == ESE_INTF_NFC) {
            return SESTATUS_SUCCESS;
          }
        }
        [[fallthrough]];
      case ESE_JCOP_UPDATE_COMPLETED:
        ALOGE("%s: ESE_JCOP_UPDATE_COMPLETED", __FUNCTION__);
        [[fallthrough]];
      case ESE_LS_UPDATE_REQUIRED:
        ALOGE("%s: ESE_LS_UPDATE_REQUIRED BUT DON't DO ANYTHING", __FUNCTION__);
        [[fallthrough]];
      case ESE_LS_UPDATE_COMPLETED:
        ALOGE("%s: ESE_LS_UPDATE_COMPLETED", __FUNCTION__);
        [[fallthrough]];
      case ESE_UPDATE_COMPLETED:
        SpiEseUpdater::setSpiEseClientState(ESE_UPDATE_COMPLETED);
        SpiEseUpdater::sendeSEUpdateState(ESE_UPDATE_COMPLETED);
        spiEseUpdater.msEseUpdateCompletedCallback->updateEseUpdateState(
            ESE_UPDATE_COMPLETED, spiEseUpdater.mEseUpdaterClientContext);
        ALOGE("%s: ESE_UPDATE_COMPLETED", __FUNCTION__);
      break;
    }
    return SESTATUS_SUCCESS;
}

void SpiEseUpdater::reInitSeService() {
  spiEseUpdater.msEseUpdateCompletedCallback->updateEseUpdateState(
            ESE_UPDATE_COMPLETED, spiEseUpdater.mEseUpdaterClientContext);
}

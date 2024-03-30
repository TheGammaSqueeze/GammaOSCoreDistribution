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

#define LOG_TAG "SpiAddaptation"
#include "NfcAdaptation.h"
#include <android/hardware/nfc/1.0/types.h>
#include <hwbinder/ProcessState.h>
#include <log/log.h>
#include <pthread.h>

using android::sp;
using android::hardware::Return;
using android::hardware::Void;
using android::hardware::hidl_vec;
using vendor::nxp::nxpnfc::V2_0::INxpNfc;
using vendor::nxp::nxpnfclegacy::V1_0::INxpNfcLegacy;
using ::android::hardware::hidl_death_recipient;
using ::android::wp;
using ::android::hidl::base::V1_0::IBase;

Mutex NfcAdaptation::sLock;
Mutex NfcAdaptation::sIoctlLock;

sp<INxpNfc> NfcAdaptation::mHalNxpNfc = nullptr;
sp<INxpNfcLegacy> NfcAdaptation::mHalNxpNfcLegacy = nullptr;
NfcAdaptation *NfcAdaptation::mpInstance = nullptr;

int omapi_status;
extern bool ese_debug_enabled;

class NxpNfcDeathRecipient : public hidl_death_recipient {
 public:
  sp<INxpNfc> mHalNxpNfcDeathRsp;
  NxpNfcDeathRecipient(sp<INxpNfc>& mHalNxpNfc) {
    mHalNxpNfcDeathRsp = mHalNxpNfc;
  }
  virtual void serviceDied(
      uint64_t /* cookie */,
      const wp<::android::hidl::base::V1_0::IBase>& /* who */) {
    ALOGE("NxpNfcDeathRecipient::serviceDied - Nfc HalService died");
    mHalNxpNfcDeathRsp->unlinkToDeath(this);
    mHalNxpNfcDeathRsp = NULL;
    NfcAdaptation::GetInstance().resetNxpNfcHalReference();
  }
};

/*******************************************************************************
**
** Function:    NfcAdaptation::Initialize()
**
** Description: Tries to get reference to Hw service
**
** Returns:     none
**
*******************************************************************************/
void NfcAdaptation::Initialize() {
  const char* func = "NfcAdaptation::Initialize";
  ALOGD_IF(ese_debug_enabled, "%s", func);
  if (mHalNxpNfc != nullptr) return;
  resetNxpNfcHalReference();
  ALOGD_IF(ese_debug_enabled, "%s: exit", func);
}

/*******************************************************************************
**
** Function:    NfcAdaptation::resetNxpNfcHalReference()
**
** Description: Resets and gets the new hardware service reference
**
** Returns:     none
**
*******************************************************************************/
void NfcAdaptation::resetNxpNfcHalReference() {
  mHalNxpNfc = nullptr;
  for (int cnt = 0; ((mHalNxpNfc == nullptr) && (cnt < 3)); cnt++) {
    mHalNxpNfc = INxpNfc::tryGetService();
    LOG_FATAL_IF(mHalNxpNfc == nullptr, "Failed to retrieve the NXP NFC HAL!");
    if (mHalNxpNfc != nullptr) {
      ALOGD_IF(ese_debug_enabled, "%s: INxpNfc::getService() returned %p (%s)",
               __func__, mHalNxpNfc.get(),
               (mHalNxpNfc->isRemote() ? "remote" : "local"));
      mNxpNfcDeathRecipient = new NxpNfcDeathRecipient(mHalNxpNfc);
      mHalNxpNfc->linkToDeath(mNxpNfcDeathRecipient, 0);
    } else {
      usleep(100 * 1000);
    }
  }

  ALOGD_IF(ese_debug_enabled, "%s: INxpNfcLegacy::getService()", __func__);
  mHalNxpNfcLegacy = INxpNfcLegacy::tryGetService();
  if(mHalNxpNfcLegacy == nullptr) {
    ALOGD_IF(ese_debug_enabled,"Failed to retrieve the NXPNFC Legacy HAL!");
  } else {
    ALOGD_IF(ese_debug_enabled, "%s: INxpNfcLegacy::getService() returned %p (%s)", __func__, mHalNxpNfcLegacy.get(),
          (mHalNxpNfcLegacy->isRemote() ? "remote" : "local"));
  }

}

/*******************************************************************************
**
** Function:    NfcAdaptation::GetInstance()
**
** Description: access class singleton
**
** Returns:     pointer to the singleton object
**
*******************************************************************************/
NfcAdaptation& NfcAdaptation::GetInstance() {
  AutoMutex guard(sLock);

  if (!mpInstance) mpInstance = new NfcAdaptation;
  return *mpInstance;
}

/*******************************************************************************
**
** Function:    NfcAdaptation::NfcAdaptation()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
NfcAdaptation::NfcAdaptation() {
  mCurrentIoctlData = NULL;
  memset(&mNciResp, 0, sizeof(NxpNciExtnResp));
}

/*******************************************************************************
**
** Function:    NfcAdaptation::~NfcAdaptation()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
NfcAdaptation::~NfcAdaptation() { mpInstance = NULL; }

/*******************************************************************************
**
** Function:    NfcAdaptation::resetEse
**
** Description:  This function a wrapper function which triggers Ese reset
**
**
**
** Returns:     -1 or 0.
**
*******************************************************************************/
ESESTATUS NfcAdaptation::resetEse(uint64_t level) {
  const char* func = "NfcAdaptation::resetEse";
  ESESTATUS result = ESESTATUS_FAILED;
  bool ret = 0;

  ALOGD_IF(ese_debug_enabled, "%s : Enter", func);

  if (mHalNxpNfc != nullptr) {
    ret = mHalNxpNfc->resetEse(level);
    if(ret){
      ALOGE("NfcAdaptation::resetEse mHalNxpNfc completed");
      result = ESESTATUS_SUCCESS;
    } else {
      result = ESESTATUS_FEATURE_NOT_SUPPORTED;
      ALOGE("NfcAdaptation::resetEse mHalNxpNfc feature not supported");
    }
  }

  return result;
}

/*******************************************************************************
**
** Function:    NfcAdaptation::setEseUpdateState
**
** Description:  This is a wrapper functions notifies upper layer about
** the jcob download comple
** tion.
** Returns:     -1 or 0.
**
*******************************************************************************/
ESESTATUS NfcAdaptation::setEseUpdateState(void* p_data) {
  const char* func = "NfcAdaptation::setEseUpdateState";
  ::android::hardware::nfc::V1_0::NfcData data;
  ESESTATUS result = ESESTATUS_FAILED;
  bool ret = 0;

  ALOGD_IF(ese_debug_enabled, "%s : Enter", func);

  ese_nxp_IoctlInOutData_t* pInpOutData = (ese_nxp_IoctlInOutData_t*)p_data;
  data.setToExternal((uint8_t*)pInpOutData, sizeof(ese_nxp_IoctlInOutData_t));

  if (mHalNxpNfc != nullptr) {
    ret = mHalNxpNfc->setEseUpdateState((::vendor::nxp::nxpnfc::V2_0::NxpNfcHalEseState)pInpOutData->inp.data.nxpCmd.p_cmd[0]);
    if(ret){
      ALOGE("NfcAdaptation::setEseUpdateState completed");
      result = ESESTATUS_SUCCESS;
    } else {
      ALOGE("NfcAdaptation::setEseUpdateState failed");
    }
  }

  return result;
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
void IoctlCallback(::android::hardware::nfc::V1_0::NfcData outputData) {
  const char* func = "IoctlCallback";
  ese_nxp_ExtnOutputData_t* pOutData =
      (ese_nxp_ExtnOutputData_t*)&outputData[0];
  ALOGD_IF(ese_debug_enabled, "%s Ioctl Type=%lu", func,
           (unsigned long)pOutData->ioctlType);
  NfcAdaptation* pAdaptation = (NfcAdaptation*)pOutData->context;
  /*Output Data from stub->Proxy is copied back to output data
   * This data will be sent back to libnfc*/
  memcpy(&pAdaptation->mCurrentIoctlData->out, &outputData[0],
         sizeof(ese_nxp_ExtnOutputData_t));
  ALOGD_IF(ese_debug_enabled, "%s Ioctl Type value[0]:0x%x and value[3] 0x%x",
           func, pOutData->data.nxpRsp.p_rsp[0],
           pOutData->data.nxpRsp.p_rsp[3]);
  omapi_status = pOutData->data.nxpRsp.p_rsp[3];
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalIoctl
**
** Description: Calls ioctl to the Nfc driver.
**              If called with a arg value of 0x01 than wired access requested,
**              status of the requst would be updated to p_data.
**              If called with a arg value of 0x00 than wired access will be
**              released, status of the requst would be updated to p_data.
**              If called with a arg value of 0x02 than current p61 state would
*be
**              updated to p_data.
**
** Returns:     -1 or 0.
**
*******************************************************************************/
ESESTATUS NfcAdaptation::HalIoctl(long arg, void* p_data) {
  const char* func = "NfcAdaptation::HalIoctl";
  ::android::hardware::nfc::V1_0::NfcData data;
  ESESTATUS result = ESESTATUS_FAILED;
  AutoMutex guard(sIoctlLock);
  ese_nxp_IoctlInOutData_t* pInpOutData = (ese_nxp_IoctlInOutData_t*)p_data;
  ALOGD_IF(ese_debug_enabled, "%s arg=%ld", func, arg);
  pInpOutData->inp.context = &NfcAdaptation::GetInstance();
  NfcAdaptation::GetInstance().mCurrentIoctlData = pInpOutData;
  data.setToExternal((uint8_t*)pInpOutData, sizeof(ese_nxp_IoctlInOutData_t));
#if 0
  if (mHalNxpNfc != nullptr) {
    mHalNxpNfc->ioctl(arg, data, IoctlCallback);
  }
#endif
  ALOGD_IF(ese_debug_enabled, "%s Ioctl Completed for Type=%lu", func,
           (unsigned long)pInpOutData->out.ioctlType);
  result = (ESESTATUS)(pInpOutData->out.result);
  return result;
}

/*******************************************************************************
**
** Function:    NfcAdaptation::notifyHciEvtProcessComplete
**
** Description: Calls the legacy interface api to call the
**              phNxpNciHal_notifyHciEvtProcessComplete()
**
** Returns:     ESESTATUS
**
*******************************************************************************/
ESESTATUS NfcAdaptation::notifyHciEvtProcessComplete() {
  const char* func = "NfcAdaptation::notifyHciEvtProcessComplete";

  ALOGD_IF(ese_debug_enabled, "%s Entry", func);
  int32_t result = ESESTATUS_FAILED;
  if (mHalNxpNfcLegacy != nullptr) {
    result = mHalNxpNfcLegacy->hciInitUpdateStateComplete();
  }
  ALOGD_IF(ese_debug_enabled, "%s Exit", func);
  return (ESESTATUS)result;
}


/*******************************************************************************
 ** Function         HalNciTransceive_cb
 **
 ** Description      This is a callback for HalNciTransceive. It shall be called
 **                  from HAL to return the value of requested property.
 **
 ** Parameters       NxpNciExtnResp
 **
 ** Return           void
 *********************************************************************/
static void HalNciTransceive_cb(const NxpNciExtnResp& out) {
    const char* func = "HalNciTransceive_cb";
    ALOGD_IF(ese_debug_enabled, "%s", func);
    memset(&(NfcAdaptation::GetInstance().mNciResp),0,sizeof(NxpNciExtnResp));
    memcpy(&(NfcAdaptation::GetInstance().mNciResp),&out,sizeof(NxpNciExtnResp));
    ALOGD_IF(ese_debug_enabled, "%s Ioctl Type value[0]:0x%x and value[3] 0x%x",
           func, out.p_rsp[0], out.p_rsp[3]);
    omapi_status = out.p_rsp[3];
  return;
}

/***************************************************************************
**
** Function         NfcAdaptation::HalNciTransceive
**
** Description      This function does tarnsceive of nci command
**
** Returns          NfcStatus.
**
***************************************************************************/
uint32_t NfcAdaptation::HalNciTransceive(phNxpNci_Extn_Cmd_t* NciCmd,phNxpNci_Extn_Resp_t* NciResp) {
  const char* func = "NfcAdaptation::HalNciTransceive";
  NxpNciExtnCmd inNciCmd;
  uint32_t status = 0;

  ALOGD_IF(ese_debug_enabled,"%s : Enter", func);
  memset(&inNciCmd,0,sizeof(NxpNciExtnCmd));
  memcpy(&inNciCmd,NciCmd,sizeof(NxpNciExtnCmd));

  if (mHalNxpNfcLegacy != nullptr) {
     mHalNxpNfcLegacy->nciTransceive(inNciCmd,HalNciTransceive_cb);
     status = (NfcAdaptation::GetInstance().mNciResp).status;
     memcpy(NciResp , &(NfcAdaptation::GetInstance().mNciResp) , sizeof(phNxpNci_Extn_Resp_t));
  }

  ALOGD_IF(ese_debug_enabled,"%s : Exit", func);
  return status;
}
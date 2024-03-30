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
#pragma once
#include <pthread.h>

#include "SyncEvent.h"
#include "hal_nxpese.h"
#include <android/hardware/nfc/1.0/types.h>
#include <phEseStatus.h>
#include <utils/RefBase.h>
#include <vendor/nxp/nxpnfc/2.0/INxpNfc.h>
#include <vendor/nxp/nxpnfclegacy/1.0/INxpNfcLegacy.h>

using ::vendor::nxp::nxpnfc::V2_0::NxpNfcHalEseState;
using vendor::nxp::nxpnfc::V2_0::INxpNfc;
using vendor::nxp::nxpnfclegacy::V1_0::INxpNfcLegacy;
using ::android::sp;
using vendor::nxp::nxpnfclegacy::V1_0::NxpNciExtnCmd;
using vendor::nxp::nxpnfclegacy::V1_0::NxpNciExtnResp;
class NxpNfcDeathRecipient;

typedef struct phNxpNci_Extn_Cmd{
  uint16_t cmd_len;
  uint8_t p_cmd[256];
}phNxpNci_Extn_Cmd_t;

typedef struct phNxpNci_Extn_Resp{
  uint32_t status;
  uint16_t rsp_len;
  uint8_t p_rsp[256];
}phNxpNci_Extn_Resp_t;

class NfcAdaptation {
 public:
   ~NfcAdaptation();
   void Initialize();
   static NfcAdaptation &GetInstance();
   static ESESTATUS HalIoctl(long data_len, void *p_data);
   static ESESTATUS notifyHciEvtProcessComplete();
   void resetNxpNfcHalReference();
   static ESESTATUS resetEse(uint64_t level);
   static ESESTATUS setEseUpdateState(void* p_data);
   static uint32_t HalNciTransceive(phNxpNci_Extn_Cmd_t* in,phNxpNci_Extn_Resp_t* out);
   ese_nxp_IoctlInOutData_t *mCurrentIoctlData;
   NxpNciExtnResp mNciResp;

 private:
  NfcAdaptation();
  static Mutex sLock;
  static Mutex sIoctlLock;
  static NfcAdaptation *mpInstance;
  static android::sp<INxpNfc> mHalNxpNfc;
  static android::sp<INxpNfcLegacy> mHalNxpNfcLegacy;

  sp<NxpNfcDeathRecipient> mNxpNfcDeathRecipient;
};

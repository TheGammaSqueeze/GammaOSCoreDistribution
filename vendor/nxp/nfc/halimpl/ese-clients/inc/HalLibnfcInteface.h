/*
 * Copyright (C) 2018-2019 NXP Semiconductors
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
 */
#pragma once
#include <phNfcStatus.h>
#include <cutils/log.h>
#include "nfa_api.h"
#include "HalNfcAdaptation.h"
#include "nfa_ee_api.h"
#include "nfa_hci_api.h"
#include <phNxpLog.h>

#include "phNxpNciHal_Adaptation.h"
#include <phNxpNciHal_utils.h>
#include "DwpSeChannelCallback.h"
#define halLibnfcInteface (HalLibnfcInteface::getInstance())
struct tHAL_NFC_CB {
  uint8_t state;
};
extern bool nfc_debug_enabled;


class HalLibnfcInteface {
 public:
  static HalLibnfcInteface &getInstance();


  NFCSTATUS phNxpNfc_ResetEseJcopUpdate();
  NFCSTATUS phNxpNfc_openEse();
  void phNxpNfc_closeEse();
  bool phNxpNfc_EseTransceive(uint8_t* xmitBuffer, int32_t xmitBufferSize,
                              uint8_t* recvBuffer, int32_t recvBufferMaxSize,
                              int32_t& recvBufferActualSize,
                              int32_t timeoutMillisec);

private:
  static const int ESE_HANDLE = 0x4C0;
  static const unsigned int MAX_RESPONSE_SIZE = 0x8800;  // 1024; //34K
  tNFA_HANDLE mActiveEeHandle = 0x4C0;
  uint8_t mResponseData[MAX_RESPONSE_SIZE];
  static SyncEvent mModeSetEvt;
  static SyncEvent mPowerLinkEvt;
  static SyncEvent mTransEvt;
  static tNFA_HANDLE mNfaHciHandle; /* NFA handle to NFA's HCI component */
  static int mActualResponseSize;   /* number of bytes in the response received
                                       from secure element */
  HalLibnfcInteface();
  NFCSTATUS initHalLibnfc();
  NFCSTATUS deInitHalLibnfc();
  static HalLibnfcInteface sHalLibnfcIntefaceInstance;
  static void nfaDeviceManagementCallback(uint8_t dmEvent,
                                   tNFA_DM_CBACK_DATA* eventData);
  static void nfaConnectionCallback(uint8_t connEvent,
                                  tNFA_CONN_EVT_DATA* eventData);
  static void nfaEeCallback(tNFA_EE_EVT event, tNFA_EE_CBACK_DATA* eventData);

  static void HalOpen(tHAL_NFC_CBACK* p_hal_cback, tHAL_NFC_DATA_CBACK* p_data_cback);
  static void HalClose();
  static void HalWrite(uint16_t data_len, uint8_t* p_data);
  static void HalCoreInitialized(uint16_t data_len, uint8_t* p_core_init_rsp_params);
  void setHalFunctionEntries(tHAL_NFC_ENTRY* halFuncEntries);
  static void nfaHciCallback(tNFA_HCI_EVT event, tNFA_HCI_EVT_DATA* eventData);
};

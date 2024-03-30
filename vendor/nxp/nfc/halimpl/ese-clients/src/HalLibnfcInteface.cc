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
#define LOG_TAG "JcDnld_DWP"
#include <HalLibnfcInteface.h>
#include <phNfcCommon.h>
#include <SyncEvent.h>

#define MAX_TRANSCEIVE_RETRY_COUNT 0x03

SyncEvent HalLibnfcInteface::mModeSetEvt;
SyncEvent HalLibnfcInteface::mPowerLinkEvt;
SyncEvent HalLibnfcInteface::mTransEvt;
tNFA_HANDLE HalLibnfcInteface::mNfaHciHandle;
int HalLibnfcInteface::mActualResponseSize;
bool gbIsTransceiveSuccess = true;
HalLibnfcInteface HalLibnfcInteface::sHalLibnfcIntefaceInstance;

HalLibnfcInteface::HalLibnfcInteface(){}

HalLibnfcInteface& HalLibnfcInteface::getInstance() { return sHalLibnfcIntefaceInstance; }
uint8_t sIsNfaEnabled = FALSE;
const char* APP_NAME = "nfc_jni";
const uint8_t MAX_NUM_EE = 4;
phNxpNciHal_Sem_t cb_data;

struct timespec ts;
uint8_t isNfcInitialzed = false;
NFCSTATUS HalLibnfcInteface::phNxpNfc_openEse() {
  tNFA_STATUS nfaStat = NFA_STATUS_FAILED;
  ALOGE("phNxpNfc_openEse enter");
  initHalLibnfc();
  usleep(50 * 1000);
  {
    SyncEventGuard guard(mPowerLinkEvt);
    nfaStat = NFA_SendPowerLinkCommand((uint8_t)ESE_HANDLE, 0x03);
    if (nfaStat == NFA_STATUS_OK) {
      mPowerLinkEvt.wait(500);
    }
  }
  {
    ALOGE("phNxpNfc_openEse mode set");
    SyncEventGuard guard(mModeSetEvt);
    nfaStat = NFA_EeModeSet((uint8_t)ESE_HANDLE, NFA_EE_MD_ACTIVATE);
    if (nfaStat == NFA_STATUS_OK) {
      mModeSetEvt.wait(500);
    }
  }
  ALOGE("phNxpNfc_openEse exit: status : %d", nfaStat);

  return nfaStat;
}
NFCSTATUS HalLibnfcInteface::initHalLibnfc() {
  tNFA_STATUS stat = NFCSTATUS_SUCCESS;
  ALOGE("%s enter", __func__);
  if (isNfcInitialzed) {
    ALOGE("%s already initialized", __func__);
    return stat;
  }
  if (sem_init(&((cb_data).sem), 0, 0) == -1) ALOGE("semaphore init error");
  if (clock_gettime(CLOCK_REALTIME, &ts) == -1) ALOGE("clock getitme  failed");

  HalNfcAdaptation& theInstance = HalNfcAdaptation::GetInstance();
  theInstance.Initialize();  // start GKI, NCI task, NFC task
  {
    tHAL_NFC_ENTRY* halFuncEntries = theInstance.GetHalEntryFuncs();
    setHalFunctionEntries(halFuncEntries);
    NFA_Init(halFuncEntries);
    stat = NFA_Enable(nfaDeviceManagementCallback, nfaConnectionCallback);
    if (stat == NFA_STATUS_OK) {
      if (SEM_WAIT(cb_data)) {
        ALOGE("semaphore error");
        return stat;
      }
    }
    isNfcInitialzed = true;
    ALOGD("%s: try ee register", __func__);
    stat = NFA_EeRegister(nfaEeCallback);
    if (stat != NFA_STATUS_OK) {
      ALOGE("%s: fail ee register; error=0x%X", __func__, stat);
      return false;
    }
    if (SEM_WAIT(cb_data)) {
      ALOGE("semaphore error");
      return stat;
    }
    tNFA_EE_INFO mEeInfo[MAX_NUM_EE];  // actual size stored in mActualNumEe
    uint8_t max_num_nfcee = MAX_NUM_EE;
    NFA_EeGetInfo(&max_num_nfcee, mEeInfo);
    for (size_t xx = 0; xx < MAX_NUM_EE; xx++) {
      if ((mEeInfo[xx].ee_handle != ESE_HANDLE) ||
          ((((mEeInfo[xx].ee_interface[0] == NCI_NFCEE_INTERFACE_HCI_ACCESS) &&
             (mEeInfo[xx].ee_status == NFC_NFCEE_STATUS_ACTIVE)) ||
            (NFA_GetNCIVersion() == NCI_VERSION_2_0)))) {
        ALOGD("%s: Found HCI network, try hci register", __func__);
        stat =
            NFA_HciRegister(const_cast<char*>(APP_NAME), nfaHciCallback, true);
        if (stat != NFA_STATUS_OK) {
          ALOGE("%s: fail hci register; error=0x%X", __func__, stat);
          return (false);
        }
        if (SEM_WAIT(cb_data)) {
          ALOGE("semaphore error");
        }
        break;
      }
    }
  }

  ALOGE("%s exit", __func__);
  return stat;
}

NFCSTATUS HalLibnfcInteface::deInitHalLibnfc() {
  ALOGD("phNxpNfc_DeInitLib enter");
  tNFA_STATUS stat = NFA_STATUS_FAILED;
  if (sIsNfaEnabled) {
    SyncEventGuard guard(mTransEvt);
    tNFA_STATUS stat = NFA_Disable(TRUE /* graceful */);
    if (stat == NFA_STATUS_OK) {
      ALOGE("%s: wait for completion", __func__);
      mTransEvt.wait();
  } else {
      ALOGE("%s: fail disable; error=0x%X", __func__, stat);
    }
  }
  isNfcInitialzed = false;
  NXPLOG_NCIHAL_E("phNxpNfc_DeInitLib exit");
  return stat;
}

bool HalLibnfcInteface::phNxpNfc_EseTransceive(uint8_t* xmitBuffer, int32_t xmitBufferSize,
                            uint8_t* recvBuffer, int32_t recvBufferMaxSize,
                            int32_t& recvBufferActualSize,
                            int32_t timeoutMillisec) {
  (void)xmitBuffer;
  (void)xmitBufferSize;
  (void)timeoutMillisec;
  static const char fn[] = "SE_Transmit";
  tNFA_STATUS nfaStat = NFA_STATUS_FAILED;
  bool isSuccess = false;
  gbIsTransceiveSuccess = true;
  uint8_t retryCount = 0;

  ALOGE("phNxpNfc_EseTransceive enter");
  do
    {
      if(!gbIsTransceiveSuccess) {
        ALOGE("Transcieve failed. HCI Network Init may be ongoing. retry after 1sec");
        usleep(1*1000*1000);
      }
      mActualResponseSize = 0;
      memset(mResponseData, 0, sizeof(mResponseData));
      {
        SyncEventGuard guard(mTransEvt);
        nfaStat = NFA_HciSendApdu(mNfaHciHandle, mActiveEeHandle, xmitBufferSize,
                                  xmitBuffer, sizeof(mResponseData), mResponseData,
                                  timeoutMillisec);
        ALOGE("%s: status code; nfaStat=0x%X", fn, nfaStat);
        retryCount++;
        if (nfaStat == NFA_STATUS_OK) {
          ALOGE("phNxpNfc_EseTransceive before waiting");
          mTransEvt.wait();
          ALOGE("phNxpNfc_EseTransceive after waiting");
        } else {
          ALOGE("%s: fail send data; error=0x%X", fn, nfaStat);
          goto TheEnd;
        }
      }
    } while (!gbIsTransceiveSuccess && (retryCount < MAX_TRANSCEIVE_RETRY_COUNT));
  if (mActualResponseSize > recvBufferMaxSize)
    recvBufferActualSize = recvBufferMaxSize;
  else
    recvBufferActualSize = mActualResponseSize;

  memcpy(recvBuffer, mResponseData, recvBufferActualSize);
  isSuccess = true;
TheEnd:
  ALOGE("phNxpNfc_EseTransceive exit");
  return (isSuccess);
}

NFCSTATUS HalLibnfcInteface::phNxpNfc_ResetEseJcopUpdate() {
  ALOGE("%s enter", __func__);
  tNFA_STATUS nfaStat = NFA_STATUS_FAILED;
  {
    SyncEventGuard guard(mPowerLinkEvt);
    nfaStat = NFA_SendPowerLinkCommand((uint8_t)ESE_HANDLE, 0x00);
    if (nfaStat == NFA_STATUS_OK) {
      mPowerLinkEvt.wait(500);
    }
  }
  {
    ALOGE("phNxpNfc_ResetEseJcopUpdate mode set");
    SyncEventGuard guard(mModeSetEvt);
    nfaStat = NFA_EeModeSet((uint8_t)ESE_HANDLE, NFA_EE_MD_DEACTIVATE);
    if (nfaStat == NFA_STATUS_OK) {
      mModeSetEvt.wait(500);
    }
  }
  ALOGE("phNxpNfc_ResetEseJcopUpdate power link");
  {
    SyncEventGuard guard(mPowerLinkEvt);
    nfaStat = NFA_SendPowerLinkCommand((uint8_t)ESE_HANDLE, 0x03);
    if (nfaStat == NFA_STATUS_OK) {
      mPowerLinkEvt.wait(500);
    }
  }
  {
    ALOGE("phNxpNfc_ResetEseJcopUpdate mode set");
    SyncEventGuard guard(mModeSetEvt);
    nfaStat = NFA_EeModeSet((uint8_t)ESE_HANDLE, NFA_EE_MD_ACTIVATE);
    if (nfaStat == NFA_STATUS_OK) {
      mModeSetEvt.wait(500);
    }
  }
  ALOGE("%s exit", __func__);
  return nfaStat;
}

void HalLibnfcInteface::nfaDeviceManagementCallback(
    uint8_t dmEvent, tNFA_DM_CBACK_DATA* eventData) {
  ALOGE("nfaDeviceManagementCallback enter");
  switch (dmEvent) {
    case NFA_DM_ENABLE_EVT: /* Result of NFA_Enable */
    {
      ALOGD("%s: NFA_DM_ENABLE_EVT; status=0x%X", __func__, eventData->status);
      sIsNfaEnabled = eventData->status == NFA_STATUS_OK;
      ALOGE("NFA_DM_ENABLE_EVT event");
      SEM_POST(&cb_data);
    } break;
    case NFA_DM_DISABLE_EVT: /* Result of NFA_Disable */
    {
      ALOGD("%s: NFA_DM_DISABLE_EVT", __func__);
      sIsNfaEnabled = false;
      ALOGE("NFA_DM_DISABLE_EVT event");
      SyncEventGuard guard(mTransEvt);
      mTransEvt.notifyOne();
    } break;
  }
}

void HalLibnfcInteface::nfaConnectionCallback(uint8_t connEvent,
                                              tNFA_CONN_EVT_DATA* eventData) {
  UNUSED(connEvent);
  UNUSED(eventData);
  ALOGE("nfaConnectionCallback connEvent = %d", connEvent);
}

void HalLibnfcInteface::HalOpen(tHAL_NFC_CBACK* p_hal_cback,
                                tHAL_NFC_DATA_CBACK* p_data_cback) {
  ESE_UPDATE_STATE old_state_dwp = eseUpdateDwp;
  eseUpdateDwp = ESE_UPDATE_COMPLETED;
  phNxpNciHal_open(p_hal_cback, p_data_cback);
  eseUpdateDwp = old_state_dwp;
}

void HalLibnfcInteface::nfaEeCallback(tNFA_EE_EVT event,
                                      tNFA_EE_CBACK_DATA* eventData) {
  ALOGE("%s: event = %x", __func__, event);
  UNUSED(eventData);
  switch (event) {
    case NFA_EE_REGISTER_EVT: {
      ALOGE("NFA_EE_REGISTER_EVT");
      SEM_POST(&cb_data);
    } break;

    case NFA_EE_MODE_SET_EVT: {
      ALOGE("NFA_EE_MODE_SET_EVT");
      SyncEventGuard guard(mModeSetEvt);
      mModeSetEvt.notifyOne();
    } break;
    case NFA_EE_PWR_LINK_CTRL_EVT: {
      ALOGE("NFA_EE_PWR_LINK_CTRL_EVT");
      SyncEventGuard guard(mPowerLinkEvt);
      mPowerLinkEvt.notifyOne();
    } break;
  }
}

void HalLibnfcInteface::phNxpNfc_closeEse() { deInitHalLibnfc();}

void HalLibnfcInteface::HalClose() { phNxpNciHal_close(false); }
/*******************************************************************************
**
** Function:    NfcAdaptation::HalWrite
**
** Description: Write NCI message to the controller.
**
** Returns:     None.
**
*******************************************************************************/
void HalLibnfcInteface::HalWrite(uint16_t data_len, uint8_t* p_data) {
  phNxpNciHal_write(data_len, p_data);
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalCoreInitialized
**
** Description: Adjust the configurable parameters in the controller.
**
** Returns:     None.
**
*******************************************************************************/
void HalLibnfcInteface::HalCoreInitialized(uint16_t data_len,
                                           uint8_t* p_core_init_rsp_params) {
  const char* func = "NfcAdaptation::HalCoreInitialized";
  UNUSED(data_len);
  UNUSED(p_core_init_rsp_params);

  ALOGD("%s", func);
  phNxpNciHal_core_initialized(p_core_init_rsp_params);
}

void HalLibnfcInteface::setHalFunctionEntries(tHAL_NFC_ENTRY* halFuncEntries) {
  halFuncEntries->initialize = NULL;
  halFuncEntries->terminate = NULL;
  halFuncEntries->open = HalOpen;
  halFuncEntries->close = HalClose;
  halFuncEntries->core_initialized = HalCoreInitialized;
  halFuncEntries->write = HalWrite;
  halFuncEntries->get_max_ee = NULL;
  return;
}

void HalLibnfcInteface::nfaHciCallback(tNFA_HCI_EVT event,
                                       tNFA_HCI_EVT_DATA* eventData) {
  static const char fn[] = "nfaHciCallback";
  ALOGD("%s: event=0x%X", fn, event);
  ALOGE("nfaHciCallback");
  switch (event) {
    case NFA_HCI_REGISTER_EVT: {
      ALOGD("%s: NFA_HCI_REGISTER_EVT; status=0x%X; handle=0x%X", fn,
            eventData->hci_register.status, eventData->hci_register.hci_handle);
      mNfaHciHandle = eventData->hci_register.hci_handle;
      SEM_POST(&cb_data);

    } break;

    case NFA_HCI_EVENT_RCVD_EVT: {
      ALOGE("%s: NFA_HCI_EVENT_RCVD_EVT; code: 0x%X; pipe: 0x%X; data len: %u",
            fn, eventData->rcvd_evt.evt_code, eventData->rcvd_evt.pipe,
            eventData->rcvd_evt.evt_len);
      SyncEventGuard guard(mTransEvt);
      mTransEvt.notifyOne();
    }
      [[fallthrough]];
    case NFA_HCI_RSP_APDU_RCVD_EVT: {
      ALOGD("%s: NFA_HCI_RSP_APDU_RCVD_EVT", fn);
      if (eventData->apdu_rcvd.apdu_len > 0) {
        mActualResponseSize =
            (eventData->apdu_rcvd.apdu_len > MAX_RESPONSE_SIZE)
                ? MAX_RESPONSE_SIZE
                : eventData->apdu_rcvd.apdu_len;
      }
      if (eventData->apdu_rcvd.status != NFA_STATUS_OK) {
        ALOGD("%s: DWP transceive failed.", __func__);
        gbIsTransceiveSuccess = false;
      } else {
        gbIsTransceiveSuccess = true;
      }
      SyncEventGuard guard(mTransEvt);
      mTransEvt.notifyOne();
    } break;
  }
}
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

/*
 * DAL spi port implementation for linux
 *
 * Project: Trusted ESE Linux
 *
 */
#define LOG_TAG "NxpEseHal"
#include <log/log.h>

#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include "NfcAdaptation.h"
#include "StateMachine.h"
#include "StateMachineInfo.h"
#include "hal_nxpese.h"
#include "phNxpEse_Api.h"
#include <ese_config.h>
#include <hardware/nfc.h>
#include <phEseStatus.h>
#include <phNxpEsePal.h>
#include <phNxpEsePal_spi.h>
#include <string.h>
#include "SpiEseUpdater.h"

#define MAX_RETRY_CNT 10
#define HAL_NFC_SPI_DWP_SYNC 21
#define RF_ON 1
#define SHA1_LEN 20
#define FIRST_MFC_APP_OPEN_NUM 1
#define LAST_MFC_APP_CLOSE_NUM 0

extern int omapi_status;
extern bool ese_debug_enabled;
extern SyncEvent gSpiTxLock;
extern SyncEvent gSpiOpenLock;

static bool gIsRfStateOn; /*RF ON-True, RF-Off-False updated after debounce timer expiry*/
unsigned long configNum1, configNum2, gFelicaAppTimeout;
unsigned long gRfOffDebounceTimeout;

static const uint8_t MAX_SPI_WRITE_RETRY_COUNT_HW_ERR = 3;
static IntervalTimer sTimerInstance;

uint8_t gMfcAppSessionCount = 0;
static std::vector<uint8_t> gOmapiAppSignature1(20, 0xFF);
static std::vector<uint8_t> gOmapiAppSignature2(20, 0xFF);
static std::vector<uint8_t> gOmapiAppSignature3(20, 0xFF);
static std::vector<uint8_t> gOmapiAppSignature4(20, 0xFF);
static std::vector<uint8_t> gOmapiAppSignature5(20, 0xFF);

/*******************************************************************************
**
** Function         phPalEse_spi_close
**
** Description      Closes PN547 device
**
** Parameters       pDevHandle - device handle
**
** Returns          None
**
*******************************************************************************/
void phPalEse_spi_close(void* pDevHandle) {
  if (NULL != pDevHandle) {
    close((intptr_t)pDevHandle);
  }
  ALOGD_IF(ese_debug_enabled, "halimpl close exit.");
  return;
}

ESESTATUS phNxpEse_spiIoctl(uint64_t ioctlType, void* p_data) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  ese_nxp_IoctlInOutData_t *inpOutData = NULL;
  if (!p_data) {
    ALOGD_IF(ese_debug_enabled, "%s:p_data is null ioctltyp: %ld", __FUNCTION__,
             (long)ioctlType);
    return ESESTATUS_FAILED;
  }
  inpOutData = (ese_nxp_IoctlInOutData_t *)p_data;
  ALOGD_IF(ese_debug_enabled, "phNxpEse_spiIoctl(): ioctlType: %ld",
           (long)ioctlType);
  switch (ioctlType) {
  case HAL_ESE_IOCTL_HCI_INIT_STATUS_UPDATE: {
    int hciInitReq = inpOutData->inp.data.nxpCmd.p_cmd[0];
    if (hciInitReq == 1) {
      StateMachine::GetInstance().ProcessExtEvent(EVT_HCI_INIT_START);
    } else {
      StateMachine::GetInstance().ProcessExtEvent(EVT_HCI_INIT_COMPLETE);
    }
  } break;
  case HAL_ESE_IOCTL_RF_STATUS_UPDATE: {
    int isRfNtfForOn = inpOutData->inp.data.nxpCmd.p_cmd[0];
    if (isRfNtfForOn == 1) {
      ALOGD_IF(
          ese_debug_enabled,
          "*******************RF IS ON*************************************");
      phPalEse_spi_stop_debounce_timer();
      gIsRfStateOn = true;
      if (gMfcAppSessionCount) {
        StateMachine::GetInstance().ProcessExtEvent(EVT_RF_ON_FELICA_APP);
      } else {
        StateMachine::GetInstance().ProcessExtEvent(EVT_RF_ON);
      }
    } else {
      ALOGD_IF(
          ese_debug_enabled,
          "*******************RF IS OFF************************************");
      phPalEse_spi_start_debounce_timer(gRfOffDebounceTimeout);
    }
  } break;
  case HAL_ESE_IOCTL_RF_ACTION_NTF: {
    ALOGD_IF(
        ese_debug_enabled,
        "*******************RF ACT NTF*************************************");
    /* Parsing NFCEE Action Notification to detect type of routing either SCBR
     * or Technology F for ESE to resume SPI session for ESE-UICC concurrency */
    if (inpOutData->inp.data.nxpCmd.p_cmd[0] == 0xC0) {
      StateMachine::GetInstance().ProcessExtEvent(EVT_RF_ACT_NTF_ESE);
      {
        SyncEventGuard guard(gSpiTxLock);
        ALOGD_IF(ese_debug_enabled, "%s: Notifying SPI_TX Wait if waiting...",
                 __FUNCTION__);
        gSpiTxLock.notifyOne();
      }
    }
  } break;
  case HAL_ESE_IOCTL_OMAPI_RELEASE_ESE_SESSION: {
    std::vector<uint8_t> signature(inpOutData->inp.data.nxpCmd.p_cmd,
                                   inpOutData->inp.data.nxpCmd.p_cmd +
                                       inpOutData->inp.data.nxpCmd.cmd_len);
    if (!phPalEse_spi_match_app_signatures(signature)) {
      ALOGD_IF(ese_debug_enabled, "****RELEASE SESSION:SIGNATURE MATCHED****");
      if (gMfcAppSessionCount) {
        gMfcAppSessionCount--;
        if (gMfcAppSessionCount == LAST_MFC_APP_CLOSE_NUM)
          StateMachine::GetInstance().ProcessExtEvent(EVT_SPI_SESSION_CLOSE);
      }
    } else {
      ALOGD_IF(ese_debug_enabled, "**RELEASE SESSION:SIGNATURE NOT MATCHED**");
    }
  } break;
  case HAL_ESE_IOCTL_OMAPI_TRY_GET_ESE_SESSION: {
    std::vector<uint8_t> signature(inpOutData->inp.data.nxpCmd.p_cmd,
                                   inpOutData->inp.data.nxpCmd.p_cmd +
                                       inpOutData->inp.data.nxpCmd.cmd_len);
    if (!phPalEse_spi_match_app_signatures(signature)) {
      ALOGD_IF(ese_debug_enabled, "******GET SESSION:SIGNATURE MATCHED******");
      if (gIsRfStateOn) {
        ALOGD_IF(ese_debug_enabled, "**GET SESSION:SIGNATURE MATCHED RF ON**");
        status = ESESTATUS_NOT_ALLOWED;
      } else {
        gMfcAppSessionCount++;
        if (gMfcAppSessionCount == FIRST_MFC_APP_OPEN_NUM)
          StateMachine::GetInstance().ProcessExtEvent(EVT_SPI_SESSION_OPEN);
      }
    } else {
      ALOGD_IF(ese_debug_enabled, "****GET SESSION:SIGNATURE NOT MATCHED****");
    }
  } break;
    case HAL_ESE_IOCTL_NFC_JCOP_DWNLD:
    {
      int update_state = inpOutData->inp.data.nxpCmd.p_cmd[0];
      if (update_state == 1)
        ALOGD_IF(ese_debug_enabled, "******************JCOP Download started*************************************");
      else
        ALOGD_IF(ese_debug_enabled, "******************JCOP Download stopped*************************************");
    }
    break;
  default:
    break;
  }
  return status;
}

/*******************************************************************************
**
** Function         phPalEse_spi_open_and_configure
**
** Description      Open and configure pn547 device
**
** Parameters       pConfig     - hardware information
**                  pLinkHandle - device handle
**
** Returns          ESE status:
**                  ESESTATUS_SUCCESS            - open_and_configure operation
*success
**                  ESESTATUS_INVALID_DEVICE     - device open operation failure
**
*******************************************************************************/
ESESTATUS phPalEse_spi_open_and_configure(pphPalEse_Config_t pConfig) {
  int nHandle;
  int retryCnt = 0;
  ese_nxp_IoctlInOutData_t inpOutData;
  NfcAdaptation& pNfcAdapt = NfcAdaptation::GetInstance();
  pNfcAdapt.Initialize();
  static uint8_t cmd_omapi_concurrent[] = {0x2F, 0x01, 0x01, 0x01};

  if (EseConfig::hasKey(NAME_NXP_SOF_WRITE)) {
    configNum1 = EseConfig::getUnsigned(NAME_NXP_SOF_WRITE);
    ALOGD_IF(ese_debug_enabled, "NXP_SOF_WRITE value from config file = %ld",
             configNum1);
  }

  if (EseConfig::hasKey(NAME_NXP_SPI_WRITE_TIMEOUT)) {
    configNum2 = EseConfig::getUnsigned(NAME_NXP_SPI_WRITE_TIMEOUT);
    ALOGD_IF(ese_debug_enabled,
             "NXP_SPI_WRITE_TIMEOUT value from config file = %ld", configNum2);
  }
  if (EseConfig::hasKey(NAME_NXP_OMAPI_APP_TIMEOUT)) {
    gFelicaAppTimeout = EseConfig::getUnsigned(NAME_NXP_OMAPI_APP_TIMEOUT);
    ALOGD_IF(ese_debug_enabled,
             "NXP_OMAPI_APP_TIMEOUT value from config file = %ld",
             gFelicaAppTimeout);
  }
  if (EseConfig::hasKey(NAME_NXP_RF_OFF_DEBOUNCE_TIMEOUT)) {
    gRfOffDebounceTimeout =
        EseConfig::getUnsigned(NAME_NXP_RF_OFF_DEBOUNCE_TIMEOUT);
    if (gRfOffDebounceTimeout == 0) {
      gRfOffDebounceTimeout = 1;
    }
    ALOGD_IF(ese_debug_enabled,
             "NAME_NXP_RF_OFF_DEBOUNCE_TIMEOUT value from config file = %ld",
             gRfOffDebounceTimeout);
  }
  if (EseConfig::hasKey(NAME_NXP_OMAPI_APP_SIGNATURE_1)) {
    gOmapiAppSignature1 = EseConfig::getBytes(NAME_NXP_OMAPI_APP_SIGNATURE_1);
  }
  if (EseConfig::hasKey(NAME_NXP_OMAPI_APP_SIGNATURE_2)) {
    gOmapiAppSignature2 = EseConfig::getBytes(NAME_NXP_OMAPI_APP_SIGNATURE_2);
  }
  if (EseConfig::hasKey(NAME_NXP_OMAPI_APP_SIGNATURE_3)) {
    gOmapiAppSignature3 = EseConfig::getBytes(NAME_NXP_OMAPI_APP_SIGNATURE_3);
  }
  if (EseConfig::hasKey(NAME_NXP_OMAPI_APP_SIGNATURE_4)) {
    gOmapiAppSignature4 = EseConfig::getBytes(NAME_NXP_OMAPI_APP_SIGNATURE_4);
  }
  if (EseConfig::hasKey(NAME_NXP_OMAPI_APP_SIGNATURE_5)) {
    gOmapiAppSignature5 = EseConfig::getBytes(NAME_NXP_OMAPI_APP_SIGNATURE_5);
  }

  ALOGD_IF(ese_debug_enabled, "halimpl open enter.");
  memset(&inpOutData, 0x00, sizeof(ese_nxp_IoctlInOutData_t));
  inpOutData.inp.data.nxpCmd.cmd_len = sizeof(cmd_omapi_concurrent);
  inpOutData.inp.data_source = 1;
  memcpy(inpOutData.inp.data.nxpCmd.p_cmd, cmd_omapi_concurrent,
         sizeof(cmd_omapi_concurrent));

  /* open port */
  ALOGD_IF(ese_debug_enabled, "Opening port=%s\n", pConfig->pDevName);
retry:
  nHandle = open((char const*)pConfig->pDevName, O_RDWR);
  if (nHandle < 0) {
    ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
    if (errno == -EBUSY || errno == EBUSY) {
      retryCnt++;
      ALOGE("Retry open eSE driver, retry cnt : %d", retryCnt);
      if (retryCnt < MAX_RETRY_CNT) {
        phPalEse_sleep(1000000);
        goto retry;
      }
    }
    ALOGE("_spi_open() Failed: retval %x", nHandle);
    pConfig->pDevHandle = NULL;
    return ESESTATUS_INVALID_DEVICE;
  }
  ALOGD_IF(ese_debug_enabled, "eSE driver opened :: fd = [%d]", nHandle);
  pConfig->pDevHandle = (void*)((intptr_t)nHandle);
  ALOGD_IF(ese_debug_enabled, "halimpl open exit");
  return ESESTATUS_SUCCESS;
}

/*******************************************************************************
**
** Function         phPalEse_spi_read
**
** Description      Reads requested number of bytes from pn547 device into given
*buffer
**
** Parameters       pDevHandle       - valid device handle
**                  pBuffer          - buffer for read data
**                  nNbBytesToRead   - number of bytes requested to be read
**
** Returns          numRead   - number of successfully read bytes
**                  -1        - read operation failure
**
*******************************************************************************/
int phPalEse_spi_read(void* pDevHandle, uint8_t* pBuffer, int nNbBytesToRead) {
  int ret = -1;
  ALOGD_IF(ese_debug_enabled, "%s Read Requested %d bytes", __FUNCTION__,
           nNbBytesToRead);
  ret = read((intptr_t)pDevHandle, (void*)pBuffer, (nNbBytesToRead));
  ALOGD_IF(ese_debug_enabled, "Read Returned = %d", ret);
  return ret;
}

/*******************************************************************************
**
** Function         phPalEse_spi_write
**
** Description      Writes requested number of bytes from given buffer into
*pn547 device
**
** Parameters       pDevHandle       - valid device handle
**                  pBuffer          - buffer for read data
**                  nNbBytesToWrite  - number of bytes requested to be written
**
** Returns          numWrote   - number of successfully written bytes
**                  -1         - write operation failure
**
*******************************************************************************/
int phPalEse_spi_write(void* pDevHandle, uint8_t* pBuffer,
                       int nNbBytesToWrite) {
  int ret = -1;
  int numWrote = 0;
  unsigned long int retryCount = 0;

  if (NULL == pDevHandle) {
    return -1;
  }

  if (configNum1 == 1) {
    /* Appending SOF for SPI write */
    pBuffer[0] = SEND_PACKET_SOF;
  } else {
    /* Do Nothing */
  }

  while (numWrote < nNbBytesToWrite) {
    // usleep(5000);
    ret = write((intptr_t)pDevHandle, pBuffer + numWrote,
                nNbBytesToWrite - numWrote);
    if (ret > 0) {
      numWrote += ret;
    } else if (ret == 0) {
      ALOGE("_spi_write() EOF");
      return -1;
    } else {
      ALOGE("_spi_write() failed errno : %x", errno);
      if (retryCount < MAX_SPI_WRITE_RETRY_COUNT_HW_ERR) {
        retryCount++;
        /*wait for eSE wake up*/
        phPalEse_sleep(WRITE_WAKE_UP_DELAY);
        ALOGE("_spi_write() failed. Going to retry, counter:%ld !", retryCount);
        continue;
      }
      return -1;
    }
  }
  return numWrote;
}

/*******************************************************************************
**
** Function         phPalEse_spi_ioctl
**
** Description      Exposed ioctl by p61 spi driver
**
** Parameters       pDevHandle     - valid device handle
**                  level          - reset level
**
** Returns           0   - ioctl operation success
**                  -1   - ioctl operation failure
**
*******************************************************************************/
ESESTATUS phPalEse_spi_ioctl(phPalEse_ControlCode_t eControlCode,
                             void* pDevHandle, long level) {
  ESESTATUS ret = ESESTATUS_IOCTL_FAILED;
  ALOGD_IF(ese_debug_enabled, "phPalEse_spi_ioctl(), ioctl %x , level %lx",
           eControlCode, level);
  ese_nxp_IoctlInOutData_t inpOutData;
  inpOutData.inp.level = level;
  NfcAdaptation& pNfcAdapt = NfcAdaptation::GetInstance();
  if ((NULL == pDevHandle) && (eControlCode != phPalEse_e_SetEseUpdateStatus)) {
    return ESESTATUS_IOCTL_FAILED;
  }
  switch (eControlCode) {
    // Nfc Driver communication part
    case phPalEse_e_ChipRst:
      ret = pNfcAdapt.resetEse(level);
      if (ret == ESESTATUS_FEATURE_NOT_SUPPORTED) {
        ret = (ESESTATUS)ioctl((intptr_t)pDevHandle, P61_SET_SPM_PWR, level);
      }
      break;

    case phPalEse_e_SetPowerScheme:
      // ret = sendIoctlData(p, HAL_NFC_SET_POWER_SCHEME, &inpOutData);
      ret = ESESTATUS_SUCCESS;
      break;

    case phPalEse_e_GetSPMStatus:
      // ret = sendIoctlData(p, HAL_NFC_GET_SPM_STATUS, &inpOutData);
      ret = ESESTATUS_SUCCESS;
      break;

    case phPalEse_e_GetEseAccess:
      ret = ESESTATUS_SUCCESS;
      break;
#ifdef NXP_ESE_JCOP_DWNLD_PROTECTION
    case phPalEse_e_SetEseUpdateStatus:
    {
      ALOGD_IF(ese_debug_enabled, "phPalEse_spi_ioctl state = phPalEse_e_SetEseUpdateStatus");
      ese_nxp_IoctlInOutData_t inpOutData;
      memset(&inpOutData, 0x00, sizeof(ese_nxp_IoctlInOutData_t));
      inpOutData.inp.data.nxpCmd.cmd_len = 1;
      inpOutData.inp.data_source = 1;
      uint8_t data = (uint8_t)level;
      memcpy(inpOutData.inp.data.nxpCmd.p_cmd, &data,
             sizeof(data));
      ALOGD_IF(ese_debug_enabled, "Before phPalEse_e_SetEseUpdateStatus");
      ret = pNfcAdapt.setEseUpdateState(&inpOutData);
      ALOGD_IF(ese_debug_enabled, "After phPalEse_e_SetEseUpdateStatus");
    }
      break;
#endif
    default:
      ret = ESESTATUS_IOCTL_FAILED;
      break;
  }
  return ret;
}

/*******************************************************************************
**
** Function         phPalEse_spi_rf_off_timer_expired_cb
**
** Description      Sends event RF-OFF after expiry of debounce timer.
**
** Parameters       union sigval
**
** Returns          none
**
*******************************************************************************/
void phPalEse_spi_rf_off_timer_expired_cb(union sigval) {
  ALOGD_IF(true, "RF debounce timer expired...");
  gIsRfStateOn = false;
  StateMachine::GetInstance().ProcessExtEvent(EVT_RF_OFF);
  // just to be sure that we acquired dwp channel before allowing any activity
  // on SPI
  usleep(100);
  {
    SyncEventGuard guard(gSpiTxLock);
    ALOGD_IF(ese_debug_enabled, "%s: Notifying SPI_TX Wait if waiting...",
             __FUNCTION__);
    gSpiTxLock.notifyOne();
  }
  {
    SyncEventGuard guard(gSpiOpenLock);
    ALOGD_IF(ese_debug_enabled, "%s: Notifying SPI_OPEN Wait if waiting...",
             __FUNCTION__);
    gSpiOpenLock.notifyOne();
  }
}

/*******************************************************************************
**
** Function         phPalEse_spi_start_debounce_timer
**
** Description      Starts a rf debounce timer
**
** Parameters       unsigned long millisecs
**
** Returns          none
**
*******************************************************************************/
void phPalEse_spi_start_debounce_timer(unsigned long millisecs) {
  if (sTimerInstance.set(millisecs, phPalEse_spi_rf_off_timer_expired_cb) ==
      true) {
    ALOGD_IF(true, "Starting RF debounce timer...");
  } else {
    ALOGE_IF(true, "Error, Starting RF debounce timer...");
  }
}

/*******************************************************************************
**
** Function         phPalEse_spi_stop_debounce_timer
**
** Description      Stops the rf debounce timer.
**
** Parameters       none
**
** Returns          none
**
*******************************************************************************/
void phPalEse_spi_stop_debounce_timer() {
  ALOGD_IF(true, "Stopping RF debounce timer...");
  sTimerInstance.kill();
}

/*******************************************************************************
**
** Function         phPalEse_spi_match_app_signatures
**
** Description      Utility function to match the app signatures
**
** Parameters       none
**
** Returns          none
**
*******************************************************************************/
ESESTATUS phPalEse_spi_match_app_signatures(std::vector<uint8_t> signature) {
  if (gOmapiAppSignature1 == signature)
    return ESESTATUS_SUCCESS;
  else if (gOmapiAppSignature2 == signature)
    return ESESTATUS_SUCCESS;
  else if (gOmapiAppSignature3 == signature)
    return ESESTATUS_SUCCESS;
  else if (gOmapiAppSignature4 == signature)
    return ESESTATUS_SUCCESS;
  else if (gOmapiAppSignature5 == signature)
    return ESESTATUS_SUCCESS;
  else
    return ESESTATUS_FAILED;
}

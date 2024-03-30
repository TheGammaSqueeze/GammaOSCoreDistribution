/******************************************************************************
 *
 *  Copyright 2019 NXP
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
#define LOG_TAG "JCOP_Se_Extn"
#include <android-base/stringprintf.h>
#include <base/logging.h>
#include "JcDnld.h"
#include <semaphore.h>
#include <string.h>
#include <sys/stat.h>

using android::base::StringPrintf;

static const uint32_t TRANSCEIVE_TIMEOUT_MS = 120000;

bool JcDnld::msIsOsuOngoing = false;
int16_t JcDnld::msOsuHandle;

STATUS_JCOP_OSU(JcDnld::*seqHandler[])
(JcopOs_ImageInfo_t *pContext, STATUS_JCOP_OSU status,
 JcopOs_TranscieveInfo_t *pInfo) = {
    &JcDnld::SwitchToJcopOsuState, &JcDnld::GetInfo,
    &JcDnld::LoadJcopOSImage,      &JcDnld::GetInfo,
    &JcDnld::LoadJcopOSImage,      &JcDnld::GetInfo,
    &JcDnld::LoadJcopOSImage,      NULL};

pJcopOs_Dwnld_Context_t gpOsuContext = NULL;
spSeEvt JcDnld::mspSeEvtCallback = nullptr;
spSeChannel JcDnld::mspSeChannelCallback = nullptr;

JcDnld JcDnld::sOsuInstance;

static const char *OSU_APDU_FILE_PATH_LIST[3] = {
    "/vendor/etc/JcopOs_Update1.apdu", "/vendor/etc/JcopOs_Update2.apdu",
    "/vendor/etc/JcopOs_Update3.apdu"};
static const char *JCOP_INFO_PATH[2] = {
    "/data/vendor/nfc/jcop_info.txt",
    "/data/vendor/secure_element/jcop_info.txt"};

inline int FSCANF_BYTE(FILE *stream, const char *format, void *pVal) {
  int Result = 0;

  if ((NULL != stream) && (NULL != format) && (NULL != pVal)) {
    unsigned int dwVal;
    unsigned char *pTmp = (unsigned char *)pVal;
    Result = fscanf(stream, format, &dwVal);

    (*pTmp) = (unsigned char)(dwVal & 0x000000FF);
  }
  return Result;
}

JcDnld::JcDnld() { mIsInit = false; }

JcDnld &JcDnld::getInstance() { return sOsuInstance; }

void JcDnld::registerSeCallback(const spSeChannel &pSeChannelCallback,
                                const spSeEvt &pSeEvtCallback) {
  mspSeChannelCallback = pSeChannelCallback;
  mspSeEvtCallback = pSeEvtCallback;
}

STATUS_JCOP_OSU JcDnld::doUpdate() {
  STATUS_JCOP_OSU status;
  pthread_t thread;
  pthread_attr_t attr;
  pthread_attr_init(&attr);
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
  if (pthread_create(&thread, &attr, &JcDnld::osuHandlerThread, NULL) != 0) {
    status = STATUS_FAILED;
  } else {
    status = STATUS_SUCCESS;
  }
  pthread_attr_destroy(&attr);
  return status;
}

void *JcDnld::osuHandlerThread(void *data) {
  (void)data;
  STATUS_JCOP_OSU retstat;
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", __func__);

  retstat = jcDnld.Init();
  if (retstat != STATUS_SUCCESS) {
    LOG(ERROR) << StringPrintf("%s: JCDND initialization failed", __FUNCTION__);
  } else {
    retstat = jcDnld.StartDownload();
    if (retstat != STATUS_SUCCESS) {
      LOG(ERROR) << StringPrintf("%s: StartDownload failed", __FUNCTION__);
    }
  }
  jcDnld.DeInit();
  mspSeEvtCallback->evtCallback((SESTATUS)retstat);
  return NULL;
}

STATUS_JCOP_OSU JcDnld::Init() {
  bool stat = false;
  msOsuHandle = EE_ERROR_OPEN_FAIL;
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", __func__);

  if (msIsOsuOngoing == true) {
    return STATUS_INUSE;
  }
  msIsOsuOngoing = true;
  stat = initializeOsuContext();
  if (stat != true) {
    LOG(ERROR) << StringPrintf("%s: failed", __func__);
  } else {
    if (mspSeChannelCallback != NULL) {
      msOsuHandle = mspSeChannelCallback->open();
      if (msOsuHandle == EE_ERROR_OPEN_FAIL) {
        LOG(ERROR) << StringPrintf("%s:Open DWP communication is failed",
                                   __func__);
        stat = false;
      } else {
        LOG(ERROR) << StringPrintf("%s:Open DWP communication is success",
                                   __func__);
        stat = true;
      }
    } else {
      LOG(ERROR) << StringPrintf("%s: NULL DWP channel", __func__);
      stat = false;
    }
  }
  return (stat == true) ? STATUS_SUCCESS : STATUS_FAILED;
}

bool JcDnld::DeInit() {
  bool stat = false;
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", __func__);

  if (gpOsuContext != NULL) {
    if (mspSeChannelCallback != NULL) {
      mspSeChannelCallback->doEseHardReset();
      stat = mspSeChannelCallback->close(msOsuHandle);
      if (stat != true) {
        LOG(ERROR) << StringPrintf("%s:closing DWP channel is failed",
                                   __func__);
      }
    }
  } else {
    LOG(ERROR) << StringPrintf("%s: NULL dwnld context", __func__);
  }
  finalize();
  msIsOsuOngoing = false;
  return stat;
}

bool JcDnld::getJcopOsFileInfo() {
  bool status = true;
  struct stat st;
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: Enter", __func__);
  for (int num = 0; num < 3; num++) {
    if (stat(OSU_APDU_FILE_PATH_LIST[num], &st))
      status = false;
  }
  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("%s: Exit Status %d", __func__, status);
  return status;
}

bool JcDnld::initializeOsuContext() {
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", __func__);

  if (!getJcopOsFileInfo()) {
    DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
        "%s: insufficient resources, file not present", __func__);
    return (false);
  }
  gpOsuContext =
      (pJcopOs_Dwnld_Context_t)malloc(sizeof(struct JcopOs_Dwnld_Context));
  if (gpOsuContext == NULL) {
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("%s: Memory allocation failed", __func__);
    return (false);
  }
  memset((void *)gpOsuContext, 0,
         (uint32_t)sizeof(struct JcopOs_Dwnld_Context));
  if (mspSeChannelCallback == nullptr) {
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("%s: No channel available", __func__);
    return (false);
  }
  gpOsuContext->pJcopOs_TransInfo.sSendData =
      (uint8_t *)malloc(sizeof(uint8_t) * JCOP_MAX_BUF_SIZE);
  if (gpOsuContext->pJcopOs_TransInfo.sSendData != NULL) {
    memset(gpOsuContext->pJcopOs_TransInfo.sSendData, 0, JCOP_MAX_BUF_SIZE);
  } else {
    DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
        "%s: Memory allocation for SendBuf is failed", __func__);
    return (false);
  }
  mIsInit = true;
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: exit", __func__);
  return (true);
}

void JcDnld::finalize() {
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", __func__);
  mIsInit = false;
  if (gpOsuContext != NULL) {
    if (gpOsuContext->pJcopOs_TransInfo.sSendData != NULL) {
      free(gpOsuContext->pJcopOs_TransInfo.sSendData);
      gpOsuContext->pJcopOs_TransInfo.sSendData = NULL;
    }
    free(gpOsuContext);
    gpOsuContext = NULL;
  }
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: exit", __func__);
}

STATUS_JCOP_OSU JcDnld::StartDownload() {
  STATUS_JCOP_OSU wstatus = STATUS_FAILED;
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: Enter:", __func__);
  if (mIsInit == false) {
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("%s: JcopOs Dwnld is not initialized", __func__);
    wstatus = STATUS_FAILED;
  } else {
    wstatus = JcDnld::osuUpdateSeqHandler();
  }
  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("%s: Exit; status = 0x%x", __func__, wstatus);
  return wstatus;
}

STATUS_JCOP_OSU JcDnld::osuUpdateSeqHandler() {
  uint8_t seq_counter = 0;
  JcopOs_ImageInfo_t update_info = (JcopOs_ImageInfo_t)gpOsuContext->Image_info;
  JcopOs_TranscieveInfo_t trans_info =
      (JcopOs_TranscieveInfo_t)gpOsuContext->pJcopOs_TransInfo;
  update_info.index = 0x00;
  update_info.cur_state = 0x00;
  STATUS_JCOP_OSU status = STATUS_FAILED;

  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", __func__);
  status = GetJcopOsState(&update_info, &seq_counter);
  if (status != STATUS_SUCCESS) {
    LOG(ERROR) << StringPrintf("Error in getting JcopOsState info");
  } else {
    LOG(ERROR) << StringPrintf("seq_counter %d", seq_counter);
    while ((seqHandler[seq_counter]) != NULL) {
      status = STATUS_FAILED;
      status =
          (*this.*(seqHandler[seq_counter]))(&update_info, status, &trans_info);
      if ((STATUS_SUCCESS != status) && (STATUS_UPTO_DATE != status)) {
        LOG(ERROR) << StringPrintf("%s: exiting; status=0x0%X", __func__,
                                   status);
        break;
      }
      seq_counter++;
    }
    if (status == STATUS_SUCCESS) {
      int32_t recvBufferActualSize = 0;
      uint8_t select[] = {0, 0xA4, 0x04, 0, 0};
      mspSeChannelCallback->open();
      usleep(100 * 1000);
      LOG(ERROR) << StringPrintf("%s: Issue First APDU", __func__);
      mspSeChannelCallback->transceive(
          select, sizeof(select), trans_info.sRecvData, 1024,
          recvBufferActualSize, trans_info.timeout);
    }
  }
  return status;
}

STATUS_JCOP_OSU JcDnld::SwitchToJcopOsuState(
    JcopOs_ImageInfo_t *pVersionInfo, STATUS_JCOP_OSU status,
    JcopOs_TranscieveInfo_t *pTranscv_Info) {
  bool stat = false;
  int32_t recvBufferActualSize = 0;

  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter;", __func__);
  if (pTranscv_Info == NULL || pVersionInfo == NULL) {
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("%s: Invalid parameter", __func__);
    status = STATUS_FAILED;
    return status;
  }
  pTranscv_Info->timeout = TRANSCEIVE_TIMEOUT_MS;
  pTranscv_Info->sSendlength = (uint32_t)sizeof(TRIGGER_APDU);
  pTranscv_Info->sRecvlength = 1024;
  memcpy(pTranscv_Info->sSendData, TRIGGER_APDU, pTranscv_Info->sSendlength);

  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("%s: Calling Secure Element Transceive", __func__);
  stat = mspSeChannelCallback->transceive(
      pTranscv_Info->sSendData, pTranscv_Info->sSendlength,
      pTranscv_Info->sRecvData, pTranscv_Info->sRecvlength,
      recvBufferActualSize, pTranscv_Info->timeout);
  if (stat != true) {
    status = STATUS_FAILED;
    LOG(ERROR) << StringPrintf("%s: SE transceive failed status = 0x%X",
                               __func__, status);
  } else if (((pTranscv_Info->sRecvData[recvBufferActualSize - 2] == 0x68) &&
              (pTranscv_Info->sRecvData[recvBufferActualSize - 1] == 0x81)) ||
             ((pTranscv_Info->sRecvData[recvBufferActualSize - 2] == 0x90) &&
              (pTranscv_Info->sRecvData[recvBufferActualSize - 1] == 0x00)) ||
             ((pTranscv_Info->sRecvData[recvBufferActualSize - 2] == 0x6F) &&
              (pTranscv_Info->sRecvData[recvBufferActualSize - 1] == 0x00))) {
    mspSeChannelCallback->doEseHardReset();
    status = STATUS_SUCCESS;
    DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
        "%s: Trigger APDU Transceive status = 0x%X", __func__, status);
  } else {
    /* status {90, 00} */
    status = STATUS_SUCCESS;
  }
  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("%s: exit; status = 0x%X", __func__, status);
  return status;
}

STATUS_JCOP_OSU JcDnld::GetInfo(JcopOs_ImageInfo_t *pImageInfo,
                                STATUS_JCOP_OSU status,
                                JcopOs_TranscieveInfo_t *pTranscv_Info) {
  bool stat = false;
  int32_t recvBufferActualSize = 0;

  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter;", __func__);

  if (pTranscv_Info == NULL || pImageInfo == NULL) {
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("%s: Invalid parameter", __func__);
    status = STATUS_FAILED;
    mspSeChannelCallback->doEseHardReset();
    return status;
  }
  if (strlen(OSU_APDU_FILE_PATH_LIST[pImageInfo->index]) <
      sizeof(pImageInfo->fls_path)) {
    memcpy(pImageInfo->fls_path,
           (char *)OSU_APDU_FILE_PATH_LIST[pImageInfo->index],
           strlen(OSU_APDU_FILE_PATH_LIST[pImageInfo->index]));
  } else {
    DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
        "%s: Copying failed as length of pImageInfo->fls_path is insufficient ",
        __func__);
    status = STATUS_FAILED;
    return status;
  }

  memset(pTranscv_Info->sSendData, 0, JCOP_MAX_BUF_SIZE);
  pTranscv_Info->sRecvlength = 1024;
  pTranscv_Info->timeout = TRANSCEIVE_TIMEOUT_MS;
  pTranscv_Info->sSendlength = (uint32_t)sizeof(GETINFO_APDU);
  memcpy(pTranscv_Info->sSendData, GETINFO_APDU, pTranscv_Info->sSendlength);

  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("%s: Calling Secure Element Transceive", __func__);
  stat = mspSeChannelCallback->transceive(
      pTranscv_Info->sSendData, pTranscv_Info->sSendlength,
      pTranscv_Info->sRecvData, pTranscv_Info->sRecvlength,
      recvBufferActualSize, pTranscv_Info->timeout);
  if (stat != true) {
    status = STATUS_FAILED;
    pImageInfo->index = 0;
    LOG(ERROR) << StringPrintf("%s: SE transceive failed status = 0x%X",
                               __func__, status);  // Stop JcopOs Update
  } else if ((pTranscv_Info->sRecvData[recvBufferActualSize - 2] == 0x90) &&
             (pTranscv_Info->sRecvData[recvBufferActualSize - 1] == 0x00)) {
    pImageInfo->version_info.osid =
        pTranscv_Info->sRecvData[recvBufferActualSize - 6];
    pImageInfo->version_info.ver1 =
        pTranscv_Info->sRecvData[recvBufferActualSize - 5];
    pImageInfo->version_info.ver0 =
        pTranscv_Info->sRecvData[recvBufferActualSize - 4];
    pImageInfo->version_info.OtherValid =
        pTranscv_Info->sRecvData[recvBufferActualSize - 3];
    {
      LOG(ERROR) << StringPrintf("Starting 3-Step update");
      if (strlen(OSU_APDU_FILE_PATH_LIST[pImageInfo->index]) <
          sizeof(pImageInfo->fls_path)) {
        memcpy(pImageInfo->fls_path, OSU_APDU_FILE_PATH_LIST[pImageInfo->index],
               strlen(OSU_APDU_FILE_PATH_LIST[pImageInfo->index]) + 1);
      } else {
        DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
            "%s: Copying failed as length of pImageInfo->fls_path is "
            "insufficient ",
            __func__);
        status = STATUS_FAILED;
        return status;
      }

      pImageInfo->index++;
    }
    status = STATUS_SUCCESS;
    DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
        "%s: GetInfo Transceive status = 0x%X", __func__, status);
  } else if ((pTranscv_Info->sRecvData[recvBufferActualSize - 2] == 0x6A) &&
             (pTranscv_Info->sRecvData[recvBufferActualSize - 1] == 0x82) &&
             pImageInfo->version_info.ver_status == STATUS_UPTO_DATE) {
    status = STATUS_UPTO_DATE;
  } else {
    status = STATUS_FAILED;
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("%s; Invalid response for GetInfo", __func__);
  }

  if (status == STATUS_FAILED) {
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("%s; status failed, doing reset...", __func__);
    mspSeChannelCallback->doEseHardReset();
  }
  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("%s: exit; status = 0x%X", __func__, status);
  return status;
}

STATUS_JCOP_OSU JcDnld::LoadJcopOSImage(
    JcopOs_ImageInfo_t *Os_info, STATUS_JCOP_OSU status,
    JcopOs_TranscieveInfo_t *pTranscv_Info) {
  bool stat = false;
  int wResult;
  int32_t wIndex, wCount = 0;
  int32_t wLen;
  int32_t recvBufferActualSize = 0;

  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", __func__);
  if (Os_info == NULL || pTranscv_Info == NULL) {
    LOG(ERROR) << StringPrintf("%s: invalid parameter", __func__);
    return status;
  }
  Os_info->fp = fopen(Os_info->fls_path, "r");

  if (Os_info->fp == NULL) {
    LOG(ERROR) << StringPrintf(
        "Error opening OS image file <%s> for reading: %s", Os_info->fls_path,
        strerror(errno));
    return STATUS_FILE_NOT_FOUND;
  }
  wResult = fseek(Os_info->fp, 0L, SEEK_END);
  if (wResult) {
    LOG(ERROR) << StringPrintf("Error seeking end OS image file %s",
                               strerror(errno));
    goto exit;
  }
  Os_info->fls_size = ftell(Os_info->fp);
  if (Os_info->fls_size < 0) {
    LOG(ERROR) << StringPrintf("Error ftelling file %s", strerror(errno));
    goto exit;
  }
  wResult = fseek(Os_info->fp, 0L, SEEK_SET);
  if (wResult) {
    LOG(ERROR) << StringPrintf("Error seeking start image file %s",
                               strerror(errno));
    goto exit;
  }
  while (!feof(Os_info->fp)) {
    LOG(ERROR) << StringPrintf("%s; Start of line processing", __func__);

    wIndex = 0;
    wLen = 0;
    wCount = 0;
    memset(pTranscv_Info->sSendData, 0x00, JCOP_MAX_BUF_SIZE);
    pTranscv_Info->sSendlength = 0;

    LOG(ERROR) << StringPrintf("%s; wIndex = 0", __func__);
    for (wCount = 0; (wCount < 5 && !feof(Os_info->fp)); wCount++, wIndex++) {
      wResult =
          FSCANF_BYTE(Os_info->fp, "%2X", &pTranscv_Info->sSendData[wIndex]);
    }
    if (wResult != 0) {
      wLen = pTranscv_Info->sSendData[4];
      LOG(ERROR) << StringPrintf("%s; Read 5byes success & len=%d", __func__,
                                 wLen);
      if (wLen == 0x00) {
        LOG(ERROR) << StringPrintf("%s: Extended APDU", __func__);
        wResult = FSCANF_BYTE(Os_info->fp, "%2X",
                              &pTranscv_Info->sSendData[wIndex++]);
        wResult = FSCANF_BYTE(Os_info->fp, "%2X",
                              &pTranscv_Info->sSendData[wIndex++]);
        wLen = ((pTranscv_Info->sSendData[5] << 8) |
                (pTranscv_Info->sSendData[6]));
      }
      for (wCount = 0; (wCount < wLen && !feof(Os_info->fp));
           wCount++, wIndex++) {
        wResult =
            FSCANF_BYTE(Os_info->fp, "%2X", &pTranscv_Info->sSendData[wIndex]);
      }
    } else {
      LOG(ERROR) << StringPrintf("%s: JcopOs image Read failed", __func__);
      goto exit;
    }

    pTranscv_Info->sSendlength = wIndex;
    LOG(ERROR) << StringPrintf("%s: start transceive for length %d", __func__,
                               pTranscv_Info->sSendlength);
    if ((pTranscv_Info->sSendlength != 0x03) &&
        (pTranscv_Info->sSendData[0] != 0x00) &&
        (pTranscv_Info->sSendData[1] != 0x00)) {
      stat = mspSeChannelCallback->transceive(
          pTranscv_Info->sSendData, pTranscv_Info->sSendlength,
          pTranscv_Info->sRecvData, pTranscv_Info->sRecvlength,
          recvBufferActualSize, pTranscv_Info->timeout);
    } else {
      LOG(ERROR) << StringPrintf("%s: Invalid packet", __func__);
      continue;
    }
    if (stat != true) {
      LOG(ERROR) << StringPrintf("%s: Transceive failed; status=0x%X", __func__,
                                 stat);
      status = STATUS_FAILED;
      goto exit;
    } else if (recvBufferActualSize != 0 &&
               pTranscv_Info->sRecvData[recvBufferActualSize - 2] == 0x90 &&
               pTranscv_Info->sRecvData[recvBufferActualSize - 1] == 0x00) {
      status = STATUS_SUCCESS;
    } else if (pTranscv_Info->sRecvData[recvBufferActualSize - 2] == 0x6F &&
               pTranscv_Info->sRecvData[recvBufferActualSize - 1] == 0x00) {
      LOG(ERROR) << StringPrintf(
          "%s: JcopOs is already upto date-No update required exiting",
          __func__);
      Os_info->version_info.ver_status = STATUS_UPTO_DATE;
      status = STATUS_UPTO_DATE;
      break;
    } else if (pTranscv_Info->sRecvData[recvBufferActualSize - 2] == 0x6F &&
               pTranscv_Info->sRecvData[recvBufferActualSize - 1] == 0xA1) {
      LOG(ERROR) << StringPrintf(
          "%s: JcopOs is already up to date-No update required exiting",
          __func__);
      Os_info->version_info.ver_status = STATUS_UPTO_DATE;
      status = STATUS_UPTO_DATE;
      break;
    } else {
      status = STATUS_FAILED;
      LOG(ERROR) << StringPrintf("%s: Invalid response", __func__);
    }
    LOG(ERROR) << StringPrintf("%s: Going for next line", __func__);
  }

  if ((status == STATUS_SUCCESS) || (status == STATUS_UPTO_DATE)) {
    Os_info->cur_state++;
    /*If Patch Update is required*/
    SetJcopOsState(Os_info, Os_info->cur_state);
  }

exit:
  mspSeChannelCallback->doEseHardReset();
  LOG(ERROR) << StringPrintf("%s close fp and exit; status= 0x%X", __func__,
                             status);
  wResult = fclose(Os_info->fp);
  return status;
}

STATUS_JCOP_OSU JcDnld::GetJcopOsState(JcopOs_ImageInfo_t *Os_info,
                                       uint8_t *counter) {
  STATUS_JCOP_OSU status = STATUS_SUCCESS;
  FILE *fp;
  uint8_t xx = 0;
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", __func__);
  if (Os_info == NULL) {
    LOG(ERROR) << StringPrintf("%s: invalid parameter", __func__);
    return STATUS_FAILED;
  }
  fp = fopen(JCOP_INFO_PATH[mspSeChannelCallback->getInterfaceInfo()], "r");

  if (fp == NULL) {
    LOG(ERROR) << StringPrintf(
        "file <%s> not exits for reading- creating new file: %s",
        JCOP_INFO_PATH[mspSeChannelCallback->getInterfaceInfo()],
        strerror(errno));
    fp = fopen(JCOP_INFO_PATH[mspSeChannelCallback->getInterfaceInfo()], "w+");
    if (fp == NULL) {
      LOG(ERROR) << StringPrintf(
          "Error opening OS image file <%s> for reading: %s",
          JCOP_INFO_PATH[mspSeChannelCallback->getInterfaceInfo()],
          strerror(errno));
      return STATUS_FAILED;
    }
    fprintf(fp, "%u", xx);
    fclose(fp);
    if (chmod(JCOP_INFO_PATH[mspSeChannelCallback->getInterfaceInfo()], 0777) !=
        0)
      LOG(ERROR) << StringPrintf("chmod failed for jcop_info.txt ");
  } else {
    if (FSCANF_BYTE(fp, "%u", &xx) == 0) {
      LOG(ERROR) << StringPrintf("Failed in fscanf function");
    }
    LOG(ERROR) << StringPrintf("JcopOsState %d", xx);
    fclose(fp);
  }

  switch (xx) {
    case JCOP_UPDATE_STATE0:
      LOG(ERROR) << StringPrintf("Starting update from step1");
      Os_info->index = JCOP_UPDATE_STATE0;
      Os_info->cur_state = JCOP_UPDATE_STATE0;
      *counter = 0;
      break;
    case JCOP_UPDATE_STATE1:
      LOG(ERROR) << StringPrintf("Starting update from step2");
      Os_info->index = JCOP_UPDATE_STATE1;
      Os_info->cur_state = JCOP_UPDATE_STATE1;
      *counter = 3;
      break;
    case JCOP_UPDATE_STATE2:
      LOG(ERROR) << StringPrintf("Starting update from step3");
      Os_info->index = JCOP_UPDATE_STATE2;
      Os_info->cur_state = JCOP_UPDATE_STATE2;
      *counter = 5;
      break;
    case JCOP_UPDATE_STATE3:
      LOG(ERROR) << StringPrintf(
          "JCOP Update state is 3. No Need to update again");
      Os_info->index = JCOP_UPDATE_STATE3;
      Os_info->cur_state = JCOP_UPDATE_STATE3;
      *counter = 7;
      break;
    default:
      LOG(ERROR) << StringPrintf("invalid state");
      status = STATUS_FAILED;
      break;
  }
  return status;
}

STATUS_JCOP_OSU JcDnld::SetJcopOsState(JcopOs_ImageInfo_t *Os_info,
                                       uint8_t state) {
  STATUS_JCOP_OSU status = STATUS_FAILED;
  FILE *fp;
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", __func__);
  if (Os_info == NULL) {
    LOG(ERROR) << StringPrintf("%s: invalid parameter", __func__);
    return status;
  }
  fp = fopen(JCOP_INFO_PATH[mspSeChannelCallback->getInterfaceInfo()], "w");

  if (fp == NULL) {
    LOG(ERROR) << StringPrintf(
        "Error opening OS image file <%s> for reading: %s",
        JCOP_INFO_PATH[mspSeChannelCallback->getInterfaceInfo()],
        strerror(errno));
  } else {
    fprintf(fp, "%u", state);
    fflush(fp);
    LOG(ERROR) << StringPrintf("Current JcopOsState: %d", state);
    status = STATUS_SUCCESS;
    int fd = fileno(fp);
    int ret = fdatasync(fd);
    LOG(ERROR) << StringPrintf("ret value: %d", ret);
    fclose(fp);
  }
  return status;
}

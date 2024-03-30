/******************************************************************************
 *
 *  Copyright 2021 NXP
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
#define LOG_TAG "EseUpdateChecker"
#include "EseUpdateChecker.h"
#include "phNxpConfig.h"
#include <android-base/stringprintf.h>
#include <base/logging.h>
#include <sys/stat.h>

#define TERMINAL_LEN 5
using android::base::StringPrintf;

bool nfc_debug_enabled;
static const char *OSU_APDU_FILE_PATH_LIST[3] = {
    "/vendor/etc/JcopOs_Update1.apdu", "/vendor/etc/JcopOs_Update2.apdu",
    "/vendor/etc/JcopOs_Update3.apdu"};

static const char *OSU_STATUS_FILE_PATH_LIST[2] = {
    "/data/vendor/nfc/jcop_info.txt",
    "/data/vendor/secure_element/jcop_info.txt"};
static const char *LS_UPDATE_BACKUP_FILE_PATH =
    "/vendor/etc/loaderservice_updater.txt";

EseUpdateChecker EseUpdateChecker::sEseUpdateCheckerInstance;

EseUpdateChecker::EseUpdateChecker() {}

EseUpdateChecker &EseUpdateChecker::getInstance() {
  return sEseUpdateCheckerInstance;
}

eseUpdateInfo_t EseUpdateChecker::checkEseUpdateRequired(ESE_CLIENT_INTF intf) {
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", __func__);
  unsigned long int num;
  bool areOsuApduFilesPresent = true;
  bool isLsScriptPresent = true;
  struct stat st;
  eseUpdateInfo_t seUpdateInfo;
#if (NXP_EXTNS == TRUE)
  memset(&seUpdateInfo, 0, sizeof(eseUpdateInfo_t));
#endif
  /*Check APDU files are present*/
  for (int num = 0; num < 3; num++) {
    if (stat(OSU_APDU_FILE_PATH_LIST[num], &st) != 0) {
      areOsuApduFilesPresent = false;
      break;
    }
  }

  /*Check if LS script present*/
  if (stat(LS_UPDATE_BACKUP_FILE_PATH, &st) != 0) {
    isLsScriptPresent = false;
  }

  if (GetNxpNumValue(NAME_NXP_ESE_JCOP_DEFAULT_INTERFACE, &num, sizeof(num))) {
    seUpdateInfo.sJcopUpdateInterface = num;
  }
  if (GetNxpNumValue(NAME_NXP_ESE_LS_DEFAULT_INTERFACE, &num, sizeof(num))) {
    seUpdateInfo.sLsUpdateIntferface = num;
  }

  if (areOsuApduFilesPresent && (seUpdateInfo.sJcopUpdateInterface == intf) &&
      isOsuRequired(intf)) {
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("%s: Jcop update required", __func__);
    seUpdateInfo.isJcopUpdateRequired = true;
  } else {
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("%s: Jcop update not required", __func__);
    seUpdateInfo.isJcopUpdateRequired = false;
  }

  if (isLsScriptPresent && (seUpdateInfo.sJcopUpdateInterface == intf)) {
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("%s: LS update required", __func__);
    seUpdateInfo.isLSUpdateRequired = true;
  } else {
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("%s: LS update not required", __func__);
    seUpdateInfo.isLSUpdateRequired = false;
  }
  return seUpdateInfo;
}

bool EseUpdateChecker::isOsuRequired(ESE_CLIENT_INTF intf) {
  bool isUpdateRequired = false;
  uint32_t status = 0;
  FILE *fp = fopen(OSU_STATUS_FILE_PATH_LIST[intf - 1], "r");

  if (fp == NULL) {
    DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
        "%s: isOsuRequired : file not exits for reading", __func__);
    isUpdateRequired = true;
  } else {
    if (fscanf(fp, "%u", &status) == 0) {
      LOG(ERROR) << StringPrintf("%s: jcop status read fail", __func__);
      isUpdateRequired = true;
    } else {
      DLOG_IF(INFO, nfc_debug_enabled)
          << StringPrintf("%s: JcopOsState : %x", __func__, status);
      if (status == JCOP_UPDATE_3STEP_DONE) {
        isUpdateRequired = false;
        DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
            "%s: isOsuRequired : Jcop update completed", __func__);
      } else {
        DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
            "%s: isOsuRequired : Jcop update required ", __func__);
        isUpdateRequired = true;
      }
    }
    fclose(fp);
  }
  return isUpdateRequired;
}

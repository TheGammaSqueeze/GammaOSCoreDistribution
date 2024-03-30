/*******************************************************************************
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
#pragma once
#include <pthread.h>
#include <string>

#define eseUpdateChecker (EseUpdateChecker::getInstance())
#define JCOP_UPDATE_3STEP_DONE 3

typedef struct se_extns_entry {
  uint8_t isJcopUpdateRequired;
  uint8_t isLSUpdateRequired;
  unsigned long int sJcopUpdateInterface;
  unsigned long int sLsUpdateIntferface;
} eseUpdateInfo_t;

typedef enum {
  ESE_INTF_INVALID = 0,
  ESE_INTF_NFC = 1,
  ESE_INTF_SPI = 2
} ESE_CLIENT_INTF;

typedef enum {
  SESTATUS_SUCCESS = (0x0000),
  SESTATUS_FAILED = (0x0003),
  SESTATUS_FILE_NOT_FOUND = (0x0005)
} SESTATUS;

typedef enum {
  ESE_UPDATE_COMPLETED = 0,
  ESE_UPDATE_STARTED,
  ESE_JCOP_UPDATE_REQUIRED,
  ESE_JCOP_UPDATE_COMPLETED,
  ESE_LS_UPDATE_REQUIRED,
  ESE_LS_UPDATE_COMPLETED
} ESE_UPDATE_STATE;

extern bool nfc_debug_enabled;

class EseUpdateChecker {
public:
  /*****************************************************************************
  **
  ** Function:        getInstance
  **
  ** Description:     Get the EseUpdateChecker singleton object.
  **
  ** Returns:         EseUpdateChecker object.
  **
  *******************************************************************************/
  static EseUpdateChecker &getInstance();

  /*******************************************************************************
  **
  ** Function:        checkEseUpdateRequired
  **
  ** Description:     HAL service entry criteria is verified
  **                  Read the interface and condition for ese Update(JCOP
  *download/LS download)
  **                  from the config file and file path and validate.
  **
  ** Returns:         returns se_extns_entry
  **
  *******************************************************************************/
  eseUpdateInfo_t checkEseUpdateRequired(ESE_CLIENT_INTF intf);

private:
  EseUpdateChecker();
  static EseUpdateChecker sEseUpdateCheckerInstance;
  /*******************************************************************************
  **
  ** Function:        isOsuRequired
  **
  ** Description:     Get JCOP update required
  **
  ** Returns:         TRUE/FALSE
  **
  *******************************************************************************/
  bool isOsuRequired(ESE_CLIENT_INTF intf);
};

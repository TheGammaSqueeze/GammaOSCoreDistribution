/*******************************************************************************
 *
 *  Copyright 2018-2019 NXP
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
#include "EseUpdateChecker.h"
#include "JcDnld.h"

extern bool nfc_debug_enabled;

#define eseClient (DwpEseUpdater::getInstance())
class DwpEseUpdater {
 public:
  /*****************************************************************************
**
** Function:        getInstance
**
** Description:     Get the DwpEseUpdater singleton object.
**
** Returns:         DwpEseUpdater object.
**
*******************************************************************************/
  static DwpEseUpdater& getInstance();

  /*******************************************************************************
  **
  ** Function:        seteSEClientState
  **
  ** Description:     Function to set the eSEUpdate state for DWP INTF
  **
  ** Returns:         SUCCESS of ok
  **
  *******************************************************************************/
  static void setDwpEseClientState(uint8_t state);

  /*******************************************************************************
  **
  ** Function:        seteSEClientState
  **
  ** Description:     Function to set the eSEUpdate state for SPI INTF
  **
  ** Returns:         SUCCESS of ok
  **
  *******************************************************************************/
  static void setSpiEseClientState(uint8_t state);

  /*******************************************************************************
**
** Function:        handleJcopOsDownload
**
** Description:     Perform JCOP update
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
  static SESTATUS handleJcopOsDownload();

/*******************************************************************************
**
** Function:        eSEUpdate_SeqHandler
**
** Description:     ESE client update handler
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
  static SESTATUS eSEUpdate_SeqHandler();

/*******************************************************************************
**
** Function:        checkIfEseClientUpdateReqd
**
** Description:     Check the initial condition
                    and interafce for eSE Client update for LS and JCOP download
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
  void checkIfEseClientUpdateReqd();

  /***************************************************************************
**
** Function:        doEseUpdateIfReqd
**
** Description:     Perform LS and JCOP download during hal service init
**
** Returns:         SUCCESS / SESTATUS_FAILED
**
*******************************************************************************/
  SESTATUS doEseUpdateIfReqd();

/*******************************************************************************
**
** Function:        sendeSEUpdateState
**
** Description:     Notify NFC HAL LS / JCOP download state
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
  static void sendeSEUpdateState(uint8_t state);
  static void eSEClientUpdate_NFC_Thread();
 private:
  static DwpEseUpdater sEseClientInstance;
  static spSeChannel sDwpSeChannelCallback;
  static spSeEvt sDwpSeEventCallback;
  static eseUpdateInfo_t msEseSpiIntfInfo, msEseDwpIntfInfo;
  DwpEseUpdater();

  /*******************************************************************************
  **
  ** Function:        eSeClientUpdateHandler
  **
  ** Description:     Perform JCOP Download
  **
  ** Returns:         SUCCESS of ok
  **
  *******************************************************************************/
  void eSeClientUpdateHandler();
};

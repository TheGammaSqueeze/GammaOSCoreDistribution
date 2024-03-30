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
#include <android/hardware/secure_element/1.0/ISecureElementHalCallback.h>
#include "phNxpEse_Api.h"
#include "EseUpdateChecker.h"
#include "JcDnld.h"

#define spiEseUpdater (SpiEseUpdater::getInstance())
using ::android::hardware::secure_element::V1_0::ISecureElementHalCallback;
typedef enum {
  ESE = 0,
  EUICC = 1,
} SEDomainID;

class SpiEseUpdater {
public:
  class IEseUpdateCompletedCallback {
  public:
    virtual void updateEseUpdateState(ESE_UPDATE_STATE evt, void *context) = 0;
    virtual ~IEseUpdateCompletedCallback(){};
  };
  static ESE_UPDATE_STATE msEseUpdate;
  static ESE_UPDATE_STATE msDwpEseUpdate;

  void reInitSeService();

  /*****************************************************************************
  **
  ** Function:        getInstance
  **
  ** Description:     Get the SpiEseUpdater singleton object.
  **
  ** Returns:         SpiEseUpdater object.
  **
  *******************************************************************************/
  static SpiEseUpdater &getInstance();

  /*******************************************************************************
  **
  ** Function:        setSpiEseClientState
  **
  ** Description:     Function to set the eSEUpdate state for SPI INTF
  **
  ** Returns:         SUCCESS of ok
  **
  *******************************************************************************/
  static void setSpiEseClientState(uint8_t state);

  /*******************************************************************************
  **
  ** Function:        setDwpEseClientState
  **
  ** Description:     Function to set the eSEUpdate state for SPI INTF
  **
  ** Returns:         SUCCESS of ok
  **
  *******************************************************************************/
  static void setDwpEseClientState(uint8_t state);

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
  ** Function:        eseUpdateSeqHandler
  **
  ** Description:     ESE client update handler
  **
  ** Returns:         SUCCESS of ok
  **
  *******************************************************************************/
  static SESTATUS eseUpdateSeqHandler();

  /*******************************************************************************
  **
  ** Function:        initializeEse
  **
  ** Description:     Open & Initialize libese
  **
  ** Returns:         SUCCESS of ok
  **
  *******************************************************************************/
  static SESTATUS initializeEse(phNxpEse_initMode mode, SEDomainID Id);

  /*******************************************************************************
  **
  ** Function:        checkIfEseClientUpdateReqd
  **
  ** Description:     Check the initial condition
                      and interafce for eSE Client update for LS and JCOP
  download
  **
  ** Returns:         SUCCESS of ok
  **
  *******************************************************************************/
  void checkIfEseClientUpdateReqd();

  /*******************************************************************************
  **
  ** Function:        sendeSEUpdateState
  **
  ** Description:     Notify NFC HAL LS / JCOP download state
  **
  ** Returns:         SUCCESS of ok
  **
  *******************************************************************************/
  static void sendeSEUpdateState(ESE_UPDATE_STATE state);

  /***************************************************************************
  **
  ** Function:        doEseUpdateIfReqd
  **
  ** Description:     Perform LS and JCOP download during hal service init
  **
  ** Returns:         SUCCESS / SESTATUS_FAILED
  **
  *******************************************************************************/
  SESTATUS doEseUpdateIfReqd(
      std::shared_ptr<IEseUpdateCompletedCallback> eseUpdateCompletedCallback,
      void *context);

private:
  SpiEseUpdater();
  static SpiEseUpdater sEseUpdaterInstance;
  static spSeChannel seChannelCallback;
  static spSeEvt seEventCallback;
  static eseUpdateInfo_t msEseSpiIntfInfo, msEseDwpIntfInfo;
  std::shared_ptr<IEseUpdateCompletedCallback> msEseUpdateCompletedCallback;
  void *mEseUpdaterClientContext;

  /*******************************************************************************
  **
  ** Function:        eSEClientUpdateHandler
  **
  ** Description:     Perform JCOP Download
  **
  ** Returns:         SUCCESS of ok
  **
  *******************************************************************************/
  void eSEClientUpdateHandler();
};

/*******************************************************************************
**
** Function:        eSEClientUpdate_SE_Thread
**
** Description:     Perform eSE update
**
** Returns:         None
**
*******************************************************************************/
void eSEClientUpdate_SE_Thread();

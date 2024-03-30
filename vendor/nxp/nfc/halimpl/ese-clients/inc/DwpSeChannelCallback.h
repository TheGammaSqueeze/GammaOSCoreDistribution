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
#include "DwpEseUpdater.h"
#include <phNxpNciHal_utils.h>
#include "nfa_api.h"
#include <SyncEvent.h>

class DwpSeChannelCallback : public ISeChannelCallback {
 public:
  /*******************************************************************************
  **
  ** Function:        Open
  **
  ** Description:     Initialize the channel.
  **
  ** Returns:         True if ok.
  **
  *******************************************************************************/
  int16_t open();

  /*******************************************************************************
  **
  ** Function:        close
  **
  ** Description:     Close the channel.
  **
  ** Returns:         True if ok.
  **
  *******************************************************************************/
  bool close(int16_t mHandle);

  /*******************************************************************************
  **
  ** Function:        transceive
  **
  ** Description:     Send data to the secure element; read it's response.
  **                  xmitBuffer: Data to transmit.
  **                  xmitBufferSize: Length of data.
  **                  recvBuffer: Buffer to receive response.
  **                  recvBufferMaxSize: Maximum size of buffer.
  **                  recvBufferActualSize: Actual length of response.
  **                  timeoutMillisec: timeout in millisecond
  **
  ** Returns:         True if ok.
  **
  *******************************************************************************/
  bool transceive(uint8_t* xmitBuffer, int32_t xmitBufferSize,
                  uint8_t* recvBuffer, int32_t recvBufferMaxSize,
                  int32_t& recvBufferActualSize, int32_t timeoutMillisec);

  /*******************************************************************************
  **
  ** Function:        doEseHardReset
  **
  ** Description:     Power OFF and ON to eSE during JCOP Update
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  void doEseHardReset();

  /*******************************************************************************
  **
  ** Function:        tNfc_featureList
  **
  ** Description:     NFCC and eSE feature flags
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  uint8_t getInterfaceInfo();

  typedef enum InterfaceInfo {
    INTF_NFC = 0,
    INTF_SE = 1,
  } IntfInfo;

 private:

};

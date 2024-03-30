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
class ISeChannelCallback {
 public:
  /*******************************************************************************
  **
  ** Function:        Open
  **
  ** Description:     Initialize the channel.
  **
  ** Returns:         returns unt16_t.
  **
  *******************************************************************************/
  virtual int16_t open() = 0;

  /*******************************************************************************
  **
  ** Function:        close
  **
  ** Description:     Close the channel.
  **
  ** Returns:         True if ok.
  **
  *******************************************************************************/
  virtual bool close(int16_t mHandle) = 0;

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
  virtual bool transceive(uint8_t* xmitBuffer, int32_t xmitBufferSize,
                          uint8_t* recvBuffer, int32_t recvBufferMaxSize,
                          int32_t& recvBufferActualSize,
                          int32_t timeoutMillisec) = 0;

  /*******************************************************************************
  **
  ** Function:        doEseHardReset
  **
  ** Description:     Power OFF and ON to eSE during JCOP Update
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  virtual void doEseHardReset() = 0;

  /*******************************************************************************
  **
  ** Function:        getInterfaceInfo
  **
  ** Description:     get the interface type (NFC/SPI).
  **
  ** Returns:         returns interface type.
  **
  *******************************************************************************/
  virtual uint8_t getInterfaceInfo() = 0;

  virtual ~ISeChannelCallback(){};
};
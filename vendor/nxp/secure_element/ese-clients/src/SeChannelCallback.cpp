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
#include <stdlib.h>
#include <cutils/log.h>
#include "SeChannelCallback.h"
#include "EseUpdateChecker.h"
#include "phNxpEse_Api.h"
/** abstract class having pure virtual functions to be implemented be each
 * client  - spi, nfc etc**/

/*******************************************************************************
**
** Function:        Open
**
** Description:     Initialize the channel.
**
** Returns:         True if ok.
**
*******************************************************************************/
int16_t SeChannelCallback ::open() { return SESTATUS_SUCCESS; }

/*******************************************************************************
**
** Function:        close
**
** Description:     Close the channel.
**
** Returns:         True if ok.
**
*******************************************************************************/
bool SeChannelCallback::close(__attribute__((unused)) int16_t mHandle) {
  if (phNxpEse_deInit() == ESESTATUS_SUCCESS) {
    if (phNxpEse_close() != ESESTATUS_SUCCESS) {
      return false;
    }
  }
  return true;
    }

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
    bool SeChannelCallback::transceive(uint8_t* xmitBuffer, int32_t xmitBufferSize,
                               uint8_t* recvBuffer, int32_t recvBufferMaxSize,
                               int32_t& recvBufferActualSize,
                               int32_t timeoutMillisec) {
      phNxpEse_data cmdData;
      phNxpEse_data rspData;

      cmdData.len = xmitBufferSize;
      cmdData.p_data = xmitBuffer;

      recvBufferMaxSize++;
      timeoutMillisec++;
      if (ESESTATUS_SUCCESS != phNxpEse_Transceive(&cmdData, &rspData)) {
        ALOGE("%s: phNxpEse_Transceive Failed", __FUNCTION__);
      }

      recvBufferActualSize = rspData.len;

      if (rspData.p_data != NULL && rspData.len) {
        memcpy(&recvBuffer[0], rspData.p_data, rspData.len);
      }
      // free (rspData.p_data);
      //&recvBuffer = rspData.p_data;
      ALOGE("%s: size = 0x%x recv[0] = 0x%x", __FUNCTION__,
            recvBufferActualSize, recvBuffer[0]);
      return true;
    }

    /*******************************************************************************
    **
    ** Function:        doEseHardReset
    **
    ** Description:     Power OFF and ON to eSE during JCOP Update
    **
    ** Returns:         None.
    **
    *******************************************************************************/
    void SeChannelCallback::doEseHardReset() { phNxpEse_resetJcopUpdate(); }

    /*******************************************************************************
    **
    ** Function:        getInterfaceInfo
    **
    ** Description:     NFCC and eSE feature flags
    **
    ** Returns:         None.
    **
    *******************************************************************************/
    uint8_t SeChannelCallback :: getInterfaceInfo() { return INTF_SE; }

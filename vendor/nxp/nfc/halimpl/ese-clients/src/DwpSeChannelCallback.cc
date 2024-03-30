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
#include "DwpSeChannelCallback.h"
#include "phNxpEse_Api.h"
#include "nfa_ee_api.h"
#include "nfa_hci_api.h"
#include "HalLibnfcInteface.h"
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
int16_t DwpSeChannelCallback::open() {
  if (halLibnfcInteface.phNxpNfc_openEse() == SESTATUS_SUCCESS) {
    ALOGD("%s enter: success ", __func__);
    return SESTATUS_SUCCESS;
  } else {
    ALOGD("%s enter: failed ", __func__);
    return SESTATUS_FAILED;
  }
}

/*******************************************************************************
**
** Function:        close
**
** Description:     Close the channel.
**
** Returns:         True if ok.
**
*******************************************************************************/
bool DwpSeChannelCallback::close(int16_t mHandle) {
  halLibnfcInteface.phNxpNfc_closeEse();
  if (mHandle != 0)
    return true;
  else
    return false;
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
bool DwpSeChannelCallback::transceive(
    uint8_t* xmitBuffer, int32_t xmitBufferSize,
    __attribute__((unused)) uint8_t* recvBuffer, int32_t recvBufferMaxSize,
    __attribute__((unused)) int32_t& recvBufferActualSize,
    int32_t timeoutMillisec) {
  bool isSuccess = false;
  isSuccess = halLibnfcInteface.phNxpNfc_EseTransceive(
      xmitBuffer, xmitBufferSize, recvBuffer, recvBufferMaxSize,
      recvBufferActualSize, timeoutMillisec);
  return isSuccess;
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
void DwpSeChannelCallback::doEseHardReset() {
  halLibnfcInteface.phNxpNfc_ResetEseJcopUpdate();
}

/*******************************************************************************
**
** Function:        getInterfaceInfo
**
** Description:     NFCC and eSE feature flags
**
** Returns:         None.
**
*******************************************************************************/
uint8_t DwpSeChannelCallback::getInterfaceInfo() { return INTF_NFC; }

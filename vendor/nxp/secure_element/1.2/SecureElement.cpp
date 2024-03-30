/******************************************************************************
 *
 *  Copyright 2020 NXP
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
#define LOG_TAG "NxpEseHal"
#define MAX_INIT_RETRY_CNT 2
#define DEFAULT_BASIC_CHANNEL 0x00
#define INVALID_LEN_SW1 0x64
#define INVALID_LEN_SW2 0xFF

#define SW1_BYTES_REMAINING 0x61
#include <log/log.h>

#include "LsClient.h"
#include "SecureElement.h"
#include "phNxpEse_Api.h"
#include "NxpEse.h"
#include "SpiEseUpdater.h"
#include "phNxpEse_Apdu_Api.h"

extern bool ese_debug_enabled;
extern uint8_t gMfcAppSessionCount;

namespace android {
namespace hardware {
namespace secure_element {
namespace V1_2 {
namespace implementation {

using vendor::nxp::nxpese::V1_0::implementation::NxpEse;

sp<V1_0::ISecureElementHalCallback> SecureElement::mCallbackV1_0 = nullptr;
sp<V1_1::ISecureElementHalCallback> SecureElement::mCallbackV1_1 = nullptr;

static void onLSCompleted(bool result, std::string reason, void* arg) {
  ((SecureElement*)arg)->onStateChange(result, reason);
}

static Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
getResponseInternal(uint8_t cla, phNxpEse_7816_rpdu_t& rpdu,
                    hidl_vec<uint8_t>& result);

SecureElement::SecureElement()
    : mOpenedchannelCount(0),
      mOpenedChannels{false, false, false, false} {}

Return<void> SecureElement::init(
    const sp<V1_0::ISecureElementHalCallback>& clientCallback) {
  ESESTATUS status = ESESTATUS_SUCCESS;

  if (clientCallback == nullptr) {
    return Void();
  } else {
    mCallbackV1_0 = clientCallback;
    mCallbackV1_1 = nullptr;
    if (!mCallbackV1_0->linkToDeath(this, 0 /*cookie*/)) {
      ALOGE("%s: Failed to register death notification", __func__);
    }
  }

  if ((SpiEseUpdater::msDwpEseUpdate != ESE_UPDATE_COMPLETED) || (SpiEseUpdater::msEseUpdate != ESE_UPDATE_COMPLETED)) {
    ALOGE("%s: EseClientUpdate is going on SPI disabled", __func__);
    clientCallback->onStateChange(false);
    return Void();
  }

  if (isSeInitialized()) {
    clientCallback->onStateChange(true);
    return Void();
  }

  int initRetryCount = 0;

  do {
    status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      initRetryCount ++;
      if (phNxpEse_close() != ESESTATUS_SUCCESS)
        ALOGE("%s: phNxpEse_close failed!!!", __func__);
      usleep(2* 1000 * 1000);
    }else {
      break;
    }
  } while( initRetryCount < MAX_INIT_RETRY_CNT);

  if (status != ESESTATUS_SUCCESS) {
    clientCallback->onStateChange(false);
    return Void();
  }

  LSCSTATUS lsStatus = LSC_doDownload(onLSCompleted, (void*)this);
  /*
   * LSC_doDownload returns LSCSTATUS_FAILED in case thread creation fails.
   * So return callback as false.
   * Otherwise callback will be called in LSDownload module.
   */
  if (lsStatus != LSCSTATUS_SUCCESS) {
    ALOGE("%s: LSDownload thread creation failed!!!", __func__);
    SecureElementStatus sestatus = seHalDeInit();
    if (sestatus != SecureElementStatus::SUCCESS) {
      ALOGE("%s: seHalDeInit failed!!!", __func__);
    }
    clientCallback->onStateChange(false);
  }
  return Void();
}

Return<void> SecureElement::init_1_1(
    const sp<V1_1::ISecureElementHalCallback>& clientCallback) {
  ESESTATUS status = ESESTATUS_SUCCESS;

  if (clientCallback == nullptr) {
    return Void();
  } else {
    mCallbackV1_1 = clientCallback;
    mCallbackV1_0 = nullptr;
    if (!mCallbackV1_1->linkToDeath(this, 0 /*cookie*/)) {
      ALOGE("%s: Failed to register death notification", __func__);
    }
  }

  if ((SpiEseUpdater::msDwpEseUpdate != ESE_UPDATE_COMPLETED) || (SpiEseUpdater::msEseUpdate != ESE_UPDATE_COMPLETED)) {
    ALOGE("%s: EseClientUpdate is going on SPI disabled", __func__);
    clientCallback->onStateChange_1_1(false, "EseClient Update going on");
    return Void();
  }

  if (isSeInitialized()) {
    clientCallback->onStateChange_1_1(true, "NXP SE HAL init ok");
    return Void();
  }

  int initRetryCount = 0;

  do {
    status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      initRetryCount ++;
      if (phNxpEse_close() != ESESTATUS_SUCCESS)
        ALOGE("%s: phNxpEse_close failed!!!", __func__);
      usleep(2* 1000 * 1000);
    }else {
      break;
    }
  } while( initRetryCount < MAX_INIT_RETRY_CNT);

  if (status != ESESTATUS_SUCCESS) {
    clientCallback->onStateChange_1_1(false, "NXP SE HAL init failed");
    return Void();
  }

  LSCSTATUS lsStatus = LSC_doDownload(onLSCompleted, (void*)this);
  /*
   * LSC_doDownload returns LSCSTATUS_FAILED in case thread creation fails.
   * So return callback as false.
   * Otherwise callback will be called in LSDownload module.
   */
  if (lsStatus != LSCSTATUS_SUCCESS) {
    ALOGE("%s: LSDownload thread creation failed!!!", __func__);
    SecureElementStatus sestatus = seHalDeInit();
    if (sestatus != SecureElementStatus::SUCCESS) {
      ALOGE("%s: seHalDeInit failed!!!", __func__);
    }
    clientCallback->onStateChange_1_1(false,
                                      "Failed to create LS download thread");
  }
  return Void();
}

Return<void> SecureElement::getAtr(getAtr_cb _hidl_cb) {
  hidl_vec<uint8_t> response;
  _hidl_cb(response);
  return Void();
}

Return<bool> SecureElement::isCardPresent() { return true; }

Return<void> SecureElement::transmit(const hidl_vec<uint8_t>& data,
                                     transmit_cb _hidl_cb) {
  AutoMutex guard(seHalLock);
  ESESTATUS status = ESESTATUS_FAILED;
  phNxpEse_data cmdApdu;
  phNxpEse_data rspApdu;
  phNxpEse_memset(&cmdApdu, 0x00, sizeof(phNxpEse_data));
  phNxpEse_memset(&rspApdu, 0x00, sizeof(phNxpEse_data));

  cmdApdu.len = data.size();
  if (cmdApdu.len >= MIN_APDU_LENGTH) {
    cmdApdu.p_data = (uint8_t*)phNxpEse_memalloc(data.size() * sizeof(uint8_t));
    memcpy(cmdApdu.p_data, data.data(), cmdApdu.len);
    status = phNxpEse_Transceive(&cmdApdu, &rspApdu);
  }

  hidl_vec<uint8_t> result;
  if (status == ESESTATUS_WRITE_FAILED) {
    ALOGE("%s: transmit failed, nfc in use!!!", __func__);
    result.resize(2);
    result[0] = 0x65;
    result[1] = ESESTATUS_WRITE_FAILED;
  } else if (status == ESESTATUS_SUCCESS) {
    result.resize(rspApdu.len);
    memcpy(&result[0], rspApdu.p_data, rspApdu.len);
  } else {
    ALOGE("%s: transmit failed!!!", __func__);
  }
  _hidl_cb(result);
  phNxpEse_free(cmdApdu.p_data);
  phNxpEse_free(rspApdu.p_data);
  return Void();
}

Return<void> SecureElement::openLogicalChannel(const hidl_vec<uint8_t>& aid,
                                               uint8_t p2,
                                               openLogicalChannel_cb _hidl_cb) {
  AutoMutex guard(seHalLock);
  hidl_vec<uint8_t> manageChannelCommand = {0x00, 0x70, 0x00, 0x00, 0x01};

  LogicalChannelResponse resApduBuff;
  resApduBuff.channelNumber = 0xff;
  memset(&resApduBuff, 0x00, sizeof(resApduBuff));

  if (!isSeInitialized()) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      ALOGE("%s: seHalInit Failed!!!", __func__);
      _hidl_cb(resApduBuff, SecureElementStatus::IOERROR);
      return Void();
    }
  }

  SecureElementStatus sestatus = SecureElementStatus::IOERROR;
  ESESTATUS status = ESESTATUS_FAILED;
  phNxpEse_data cmdApdu;
  phNxpEse_data rspApdu;

  phNxpEse_memset(&cmdApdu, 0x00, sizeof(phNxpEse_data));
  phNxpEse_memset(&rspApdu, 0x00, sizeof(phNxpEse_data));

  cmdApdu.len = manageChannelCommand.size();
  cmdApdu.p_data = (uint8_t*)phNxpEse_memalloc(manageChannelCommand.size() *
                                               sizeof(uint8_t));
  if (cmdApdu.p_data != NULL) {
    memcpy(cmdApdu.p_data, manageChannelCommand.data(), cmdApdu.len);
    status = phNxpEse_Transceive(&cmdApdu, &rspApdu);
  }
  if (status != ESESTATUS_SUCCESS) {
    /*Transceive failed*/
    sestatus = SecureElementStatus::IOERROR;
  } else if (rspApdu.p_data[rspApdu.len - 2] == 0x90 &&
             rspApdu.p_data[rspApdu.len - 1] == 0x00) {
    /*ManageChannel successful*/
    resApduBuff.channelNumber = rspApdu.p_data[0];
    mOpenedchannelCount++;
    mOpenedChannels[resApduBuff.channelNumber] = true;
    sestatus = SecureElementStatus::SUCCESS;
  } else if (rspApdu.p_data[rspApdu.len - 2] == 0x6A &&
             rspApdu.p_data[rspApdu.len - 1] == 0x81) {
    sestatus = SecureElementStatus::CHANNEL_NOT_AVAILABLE;
  } else if (((rspApdu.p_data[rspApdu.len - 2] == 0x6E) ||
              (rspApdu.p_data[rspApdu.len - 2] == 0x6D)) &&
             rspApdu.p_data[rspApdu.len - 1] == 0x00) {
    sestatus = SecureElementStatus::UNSUPPORTED_OPERATION;
  }

  /*Free the allocations*/
  phNxpEse_free(cmdApdu.p_data);
  phNxpEse_free(rspApdu.p_data);

  if (sestatus != SecureElementStatus::SUCCESS) {
    /*If first logical channel open fails, DeInit SE*/
    if (isSeInitialized() && (mOpenedchannelCount == 0)) {
      SecureElementStatus deInitStatus = seHalDeInit();
      if (deInitStatus != SecureElementStatus::SUCCESS) {
        ALOGE("%s: seDeInit Failed", __func__);
      }
    }
    /*If manageChanle is failed in any of above cases
    send the callback and return*/
    _hidl_cb(resApduBuff, sestatus);
    return Void();
  }

  ALOGD_IF(ese_debug_enabled, "%s: Sending selectApdu", __func__);
  /*Reset variables if manageChannel is success*/
  sestatus = SecureElementStatus::IOERROR;
  status = ESESTATUS_FAILED;

  phNxpEse_memset(&cmdApdu, 0x00, sizeof(phNxpEse_data));
  phNxpEse_memset(&rspApdu, 0x00, sizeof(phNxpEse_data));

  cmdApdu.len = (int32_t)(5 + aid.size());
  cmdApdu.p_data = (uint8_t*)phNxpEse_memalloc(cmdApdu.len * sizeof(uint8_t));
  if (cmdApdu.p_data != NULL) {
    uint8_t xx = 0;
    cmdApdu.p_data[xx++] = resApduBuff.channelNumber;
    cmdApdu.p_data[xx++] = 0xA4;        // INS
    cmdApdu.p_data[xx++] = 0x04;        // P1
    cmdApdu.p_data[xx++] = p2;          // P2
    cmdApdu.p_data[xx++] = aid.size();  // Lc
    memcpy(&cmdApdu.p_data[xx], aid.data(), aid.size());

    status = phNxpEse_Transceive(&cmdApdu, &rspApdu);
  }

  if (status != ESESTATUS_SUCCESS) {
    /*Transceive failed*/
    sestatus = SecureElementStatus::IOERROR;
  } else {
    uint8_t sw1 = rspApdu.p_data[rspApdu.len - 2];
    uint8_t sw2 = rspApdu.p_data[rspApdu.len - 1];
    resApduBuff.selectResponse.resize(rspApdu.len);
    memcpy(&resApduBuff.selectResponse[0], rspApdu.p_data, rspApdu.len);
    if (sw1 == SW1_BYTES_REMAINING) {
      phNxpEse_7816_rpdu_t rpdu;
      sestatus = getResponseInternal(cmdApdu.p_data[0], rpdu,
                                     resApduBuff.selectResponse);
      sw1 = rpdu.sw1;
      sw2 = rpdu.sw2;
    }

    /*Return response on success, empty vector on failure*/
    /*Status is success*/
    if ((sw1 == 0x90 && sw2 == 0x00) || (sw1 == 0x62) || (sw1 == 0x63)) {
      /*Copy the response including status word*/
      sestatus = SecureElementStatus::SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if ((sw1 == 0x6A && sw2 == 0x82) || (sw1 == 0x69 && (sw2 == 0x99 || sw2 == 0x85))) {
      sestatus = SecureElementStatus::NO_SUCH_ELEMENT_ERROR;
    }
    /*Operation provided by the P2 parameter is not permitted by the applet.*/
    else if (sw1 == 0x6A && sw2 == 0x86) {
      sestatus = SecureElementStatus::UNSUPPORTED_OPERATION;
    }
  }

  if (sestatus != SecureElementStatus::SUCCESS) {
    SecureElementStatus closeChannelStatus =
        internalCloseChannel(resApduBuff.channelNumber);
    if (closeChannelStatus != SecureElementStatus::SUCCESS) {
      ALOGE("%s: internalCloseChannel Failed", __func__);
    } else {
      resApduBuff.channelNumber = 0xff;
    }
  }
  _hidl_cb(resApduBuff, sestatus);
  phNxpEse_free(cmdApdu.p_data);
  phNxpEse_free(rspApdu.p_data);

  return Void();
}

Return<void> SecureElement::openBasicChannel(const hidl_vec<uint8_t>& aid,
                                             uint8_t p2,
                                             openBasicChannel_cb _hidl_cb) {
  AutoMutex guard(seHalLock);
  hidl_vec<uint8_t> result;

  if (!isSeInitialized()) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      ALOGE("%s: seHalInit Failed!!!", __func__);
      _hidl_cb(result, SecureElementStatus::IOERROR);
      return Void();
    }
  }

  SecureElementStatus sestatus = SecureElementStatus::IOERROR;
  ESESTATUS status = ESESTATUS_FAILED;
  phNxpEse_data cmdApdu;
  phNxpEse_data rspApdu;

  phNxpEse_memset(&cmdApdu, 0x00, sizeof(phNxpEse_data));
  phNxpEse_memset(&rspApdu, 0x00, sizeof(phNxpEse_data));

  cmdApdu.len = (int32_t)(5 + aid.size());
  cmdApdu.p_data = (uint8_t*)phNxpEse_memalloc(cmdApdu.len * sizeof(uint8_t));
  if (cmdApdu.p_data != NULL) {
    uint8_t xx = 0;
    cmdApdu.p_data[xx++] = 0x00;        // basic channel
    cmdApdu.p_data[xx++] = 0xA4;        // INS
    cmdApdu.p_data[xx++] = 0x04;        // P1
    cmdApdu.p_data[xx++] = p2;          // P2
    cmdApdu.p_data[xx++] = aid.size();  // Lc
    memcpy(&cmdApdu.p_data[xx], aid.data(), aid.size());

    status = phNxpEse_Transceive(&cmdApdu, &rspApdu);
  }

  if (status != ESESTATUS_SUCCESS) {
    /* Transceive failed */
    sestatus = SecureElementStatus::IOERROR;
  } else {
    uint8_t sw1 = rspApdu.p_data[rspApdu.len - 2];
    uint8_t sw2 = rspApdu.p_data[rspApdu.len - 1];
    result.resize(rspApdu.len);
    memcpy(&result[0], rspApdu.p_data, rspApdu.len);
    if (sw1 == SW1_BYTES_REMAINING) {
      phNxpEse_7816_rpdu_t rpdu;
      sestatus = getResponseInternal(cmdApdu.p_data[0], rpdu, result);
      sw1 = rpdu.sw1;
      sw2 = rpdu.sw2;
    }

    /*Return response on success, empty vector on failure*/
    /*Status is success*/
    if ((sw1 == 0x90 && sw2 == 0x00) || (sw1 == 0x62) || (sw1 == 0x63)) {
      /*Copy the response including status word*/
      /*Set basic channel reference if it is not set */
      if (!mOpenedChannels[0]) {
        mOpenedChannels[0] = true;
        mOpenedchannelCount++;
      }
      sestatus = SecureElementStatus::SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if ((sw1 == 0x6A && sw2 == 0x82) ||
             (sw1 == 0x69 && (sw2 == 0x99 || sw2 == 0x85))) {
      sestatus = SecureElementStatus::NO_SUCH_ELEMENT_ERROR;
    }
    /*Operation provided by the P2 parameter is not permitted by the applet.*/
    else if (sw1 == 0x6A && sw2 == 0x86) {
      sestatus = SecureElementStatus::UNSUPPORTED_OPERATION;
    }
  }

  if (sestatus != SecureElementStatus::SUCCESS) {
    SecureElementStatus closeChannelStatus =
        internalCloseChannel(DEFAULT_BASIC_CHANNEL);
    if (closeChannelStatus != SecureElementStatus::SUCCESS) {
      ALOGE("%s: internalCloseChannel Failed", __func__);
    }
  }
  _hidl_cb(result, sestatus);
  phNxpEse_free(cmdApdu.p_data);
  phNxpEse_free(rspApdu.p_data);
  return Void();
}

Return<SecureElementStatus> SecureElement::closeChannel(uint8_t channelNumber) {
  AutoMutex guard(seHalLock);
   return internalCloseChannel(channelNumber);
}

Return<SecureElementStatus> SecureElement::internalCloseChannel(uint8_t channelNumber) {
  ESESTATUS status = ESESTATUS_FAILED;
  SecureElementStatus sestatus = SecureElementStatus::SUCCESS;

  phNxpEse_data cmdApdu;
  phNxpEse_data rspApdu;

  if (channelNumber >= MAX_LOGICAL_CHANNELS) {
    ALOGE("%s: invalid channel!!!", __func__);
    return SecureElementStatus::FAILED;
  }
  if (channelNumber > DEFAULT_BASIC_CHANNEL) {
    phNxpEse_memset(&cmdApdu, 0x00, sizeof(phNxpEse_data));
    phNxpEse_memset(&rspApdu, 0x00, sizeof(phNxpEse_data));
    cmdApdu.p_data = (uint8_t*)phNxpEse_memalloc(5 * sizeof(uint8_t));
    if (cmdApdu.p_data != NULL) {
      uint8_t xx = 0;

      cmdApdu.p_data[xx++] = channelNumber;
      cmdApdu.p_data[xx++] = 0x70;           // INS
      cmdApdu.p_data[xx++] = 0x80;           // P1
      cmdApdu.p_data[xx++] = channelNumber;  // P2
      cmdApdu.p_data[xx++] = 0x00;           // Lc
      cmdApdu.len = xx;

      status = phNxpEse_Transceive(&cmdApdu, &rspApdu);
    }
    if (status != ESESTATUS_SUCCESS) {
      sestatus = SecureElementStatus::FAILED;
    } else if ((rspApdu.p_data[rspApdu.len - 2] == 0x90) &&
               (rspApdu.p_data[rspApdu.len - 1] == 0x00)) {
      sestatus = SecureElementStatus::SUCCESS;
    } else {
      sestatus = SecureElementStatus::FAILED;
    }
    phNxpEse_free(cmdApdu.p_data);
    phNxpEse_free(rspApdu.p_data);
  }

  if (sestatus != SecureElementStatus::SUCCESS) {
    ALOGE("%s: failed to close a logical channel", __func__);
  }

  if(mOpenedChannels[channelNumber] != false)
    mOpenedchannelCount--;
  mOpenedChannels[channelNumber] = false;
  /*If there are no channels remaining close secureElement*/
  if (mOpenedchannelCount == 0) {
    sestatus = seHalDeInit();
  } else {
    sestatus = SecureElementStatus::SUCCESS;
  }
  return sestatus;
}

void SecureElement::serviceDied(uint64_t /*cookie*/, const wp<IBase>& /*who*/) {
  ALOGE("%s: SecureElement serviceDied!!!", __func__);
  gMfcAppSessionCount = 0;
  SecureElementStatus sestatus = seHalDeInit();
  if (sestatus != SecureElementStatus::SUCCESS) {
    ALOGE("%s: seHalDeInit Faliled!!!", __func__);
  }
  if (mCallbackV1_0 != nullptr) {
    mCallbackV1_0->unlinkToDeath(this);
    mCallbackV1_0 = nullptr;
  }
  if (mCallbackV1_1 != nullptr) {
    mCallbackV1_1->unlinkToDeath(this);
    mCallbackV1_1 = nullptr;
  }
}

bool SecureElement::isSeInitialized() { return phNxpEse_isOpen(); }

ESESTATUS SecureElement::seHalInit() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  phNxpEse_initParams initParams;
  memset(&initParams, 0x00, sizeof(phNxpEse_initParams));
  initParams.initMode = ESE_MODE_NORMAL;

  status = phNxpEse_open(initParams);
  if (status != ESESTATUS_SUCCESS) {
    ALOGE("%s: SecureElement open failed!!!", __func__);
  } else {
    status = phNxpEse_init(initParams);
    if (status != ESESTATUS_SUCCESS) {
      ALOGE("%s: SecureElement init failed!!!", __func__);
    }
  }
  return status;
}

Return<SecureElementStatus> SecureElement::seHalDeInit() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  SecureElementStatus sestatus = SecureElementStatus::FAILED;
  phNxpEse_deInit();
  status = phNxpEse_close();
  if (status != ESESTATUS_SUCCESS) {
    sestatus = SecureElementStatus::FAILED;
  } else {
    sestatus = SecureElementStatus::SUCCESS;

    for (uint8_t xx = 0; xx < MAX_LOGICAL_CHANNELS; xx++) {
      mOpenedChannels[xx] = false;
    }
    mOpenedchannelCount = 0;
  }
  return sestatus;
}

void SecureElement::onStateChange(bool result, std::string reason) {
  ALOGD("%s: result: %d, reaon= %s", __func__, result, reason.c_str());
  if (mCallbackV1_1 != nullptr) {
    mCallbackV1_1->onStateChange_1_1(result, reason);
  } else if (mCallbackV1_0 != nullptr) {
    mCallbackV1_0->onStateChange(result);
  }
}

void SecureElement::reInitSeService(const sp<ISecureElement> &seContxt) {
  if (mCallbackV1_1 != nullptr) {
    seContxt->init_1_1(mCallbackV1_1);
  } else if (mCallbackV1_0 != nullptr) {
    seContxt->init(mCallbackV1_0);
  }
}

Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
SecureElement::reset() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  SecureElementStatus sestatus = SecureElementStatus::FAILED;
  ALOGD("DBBG> %s: Enter", __func__);
  if (!isSeInitialized()) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      ALOGE("%s: seHalInit Failed!!!", __func__);
    }
  }
  if (status == ESESTATUS_SUCCESS) {
    mCallbackV1_1->onStateChange_1_1(false, "reset the SE");
    status = phNxpEse_reset();
    ALOGD("DBBG> %s status = %d", __func__, status);
    if (status != ESESTATUS_SUCCESS) {
      ALOGE("%s: SecureElement reset failed!!", __func__);
    } else {
      sestatus = SecureElementStatus::SUCCESS;
      for (uint8_t xx = 0; xx < MAX_LOGICAL_CHANNELS; xx++) {
        mOpenedChannels[xx] = false;
      }
      mOpenedchannelCount = 0;
      mCallbackV1_1->onStateChange_1_1(true, "SE initialized");
    }
  }
  ALOGD("DBBG> %s: Exit", __func__);
  return sestatus;
}

static Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
getResponseInternal(uint8_t cla, phNxpEse_7816_rpdu_t& rpdu,
                    hidl_vec<uint8_t>& result) {
  SecureElementStatus sestatus = SecureElementStatus::SUCCESS;
  ESESTATUS status = ESESTATUS_SUCCESS;
  phNxpEse_data cmdApdu;
  phNxpEse_data rspApdu;
  uint16_t responseLen = rpdu.len;  // Response already copied
  uint8_t getRespLe = rpdu.sw2;     // Response pending to receive
  uint8_t getResponse[5] = {0x00, 0xC0, 0x00, 0x00, 0x00};

  getResponse[0] = cla;

  phNxpEse_memset(&cmdApdu, 0x00, sizeof(phNxpEse_data));

  cmdApdu.len = (uint32_t)sizeof(getResponse);
  cmdApdu.p_data = getResponse;

  do {
    // update GET response 61 xx(Le)
    getResponse[4] = getRespLe;

    phNxpEse_memset(&rspApdu, 0x00, sizeof(phNxpEse_data));

    status = phNxpEse_Transceive(&cmdApdu, &rspApdu);
    if (status != ESESTATUS_SUCCESS) {
      /*Transceive failed*/
      if (rspApdu.len > 0 && (rspApdu.p_data[rspApdu.len - 2] == 0x64 &&
                              rspApdu.p_data[rspApdu.len - 1] == 0xFF)) {
        sestatus = SecureElementStatus::IOERROR;
      } else {
        sestatus = SecureElementStatus::FAILED;
      }
      break;
    } else {
      uint32_t respLen = rspApdu.len;

      // skip 2 bytes in case of 61xx SW again
      if (rspApdu.p_data[respLen - 2] == SW1_BYTES_REMAINING) {
        respLen -= 2;
        getRespLe = rspApdu.p_data[respLen - 1];
      }
      // copy response chunk received
      result.resize(responseLen + respLen);
      memcpy(&result[responseLen], rspApdu.p_data, respLen);
      responseLen += respLen;
    }
  } while (rspApdu.p_data[rspApdu.len - 2] == SW1_BYTES_REMAINING);

  // Propagate SW as it is received from card
  if (sestatus == SecureElementStatus::SUCCESS) {
    rpdu.sw1 = rspApdu.p_data[rspApdu.len - 2];
    rpdu.sw2 = rspApdu.p_data[rspApdu.len - 1];
  } else {  // Other Failure cases update failure SW:64FF
    rpdu.sw1 = INVALID_LEN_SW1;
    rpdu.sw2 = INVALID_LEN_SW2;
  }

  return sestatus;
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace secure_element
}  // namespace hardware
}  // namespace android

/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Copyright 2021 NXP.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "UwbJniInternal.h"
#include "UwbRfTestManager.h"
#include "JniLog.h"
#include "ScopedJniEnv.h"
#include "SyncEvent.h"
#include "UwbAdaptation.h"
#include "uwb_config.h"
#include "uwb_hal_int.h"

namespace android {

const char *PERIODIC_TX_DATA_CLASS_NAME =
    "com/android/uwb/test/UwbTestPeriodicTxResult";
const char *PER_RX_DATA_CLASS_NAME =
    "com/android/uwb/test/UwbTestRxPacketErrorRateResult";
const char *UWB_LOOPBACK_DATA_CLASS_NAME =
    "com/android/uwb/test/UwbTestLoopBackTestResult";
const char *RX_DATA_CLASS_NAME = "com/android/uwb/test/UwbTestRxResult";

static SyncEvent sUwaRfTestEvent;
static SyncEvent sUwaSetTestConfigEvent;
static SyncEvent sUwaGetTestConfigEvent;
static uint8_t sSetTestConfig[UCI_MAX_PAYLOAD_SIZE];
static uint8_t sGetTestConfig[UCI_MAX_PAYLOAD_SIZE];
static uint8_t sNoOfTestConfigIds = 0x00;
static uint16_t sGetTestConfigLen;
static uint16_t sSetTestConfigLen;
static uint8_t sGetTestConfigStatus;
static uint8_t sSetTestConfigStatus;

/* command response status */
static bool setTestConfigRespStatus = false;
static bool getTestConfigRespStatus = false;
static bool rfTestStatus = false;
bool IsRfTestOngoing = false; // to track the RF test status whether test is
                              // sussesffuly completed or not

static UwbRfTestManager &uwbRfTestManager = UwbRfTestManager::getInstance();

void clearRfTestContext() { IsRfTestOngoing = false; }

UwbRfTestManager UwbRfTestManager::mObjTestManager;

UwbRfTestManager &UwbRfTestManager::getInstance() { return mObjTestManager; }

UwbRfTestManager::UwbRfTestManager() {
  mVm = NULL;
  mClass = NULL;
  mObject = NULL;
  ;
  mPeriodicTxDataClass = NULL;
  mPerRxDataClass = NULL;
  mUwbLoopBackDataClass = NULL;
  mRxDataClass = NULL;
  mOnPeriodicTxDataNotificationReceived = NULL;
  mOnPerRxDataNotificationReceived = NULL;
  mOnLoopBackTestDataNotificationReceived = NULL;
  mOnRxTestDataNotificationReceived = NULL;
}

void UwbRfTestManager::onPeriodicTxDataNotificationReceived(uint16_t len,
                                                            uint8_t *data) {
  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", __func__);
    return;
  }

  tPERIODIC_TX_DATA sPeriodic_tx_data;
  memset(&sPeriodic_tx_data, 0, sizeof(tPERIODIC_TX_DATA));
  if (len != 0) {
    STREAM_TO_UINT8(sPeriodic_tx_data.status, data);

    jmethodID periodicTxCtor =
        env->GetMethodID(mPeriodicTxDataClass, "<init>", "(I)V");
    jobject periodicTxObject = env->NewObject(
        mPeriodicTxDataClass, periodicTxCtor, (int)sPeriodic_tx_data.status);

    if (mOnPeriodicTxDataNotificationReceived != NULL) {
      env->CallVoidMethod(mObject, mOnPeriodicTxDataNotificationReceived,
                          periodicTxObject);
      if (env->ExceptionCheck()) {
        env->ExceptionClear();
        JNI_TRACE_E("%s: fail to send periodic TX test status", __func__);
      }
    } else {
      JNI_TRACE_E("%s: periodic TX data MID is NULL", __func__);
    }
  }
}

void UwbRfTestManager::onPerRxDataNotificationReceived(uint16_t len,
                                                       uint8_t *data) {
  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", __func__);
    return;
  }

  tPER_RX_DATA sPer_rx_data;

  memset(&sPer_rx_data, 0, sizeof(tPER_RX_DATA));
  if (len != 0) {
    STREAM_TO_UINT8(sPer_rx_data.status, data);
    STREAM_TO_UINT32(sPer_rx_data.attempts, data);
    STREAM_TO_UINT32(sPer_rx_data.ACQ_detect, data);
    STREAM_TO_UINT32(sPer_rx_data.ACQ_rejects, data);
    STREAM_TO_UINT32(sPer_rx_data.RX_fail, data);
    STREAM_TO_UINT32(sPer_rx_data.sync_cir_ready, data);
    STREAM_TO_UINT32(sPer_rx_data.sfd_fail, data);
    STREAM_TO_UINT32(sPer_rx_data.sfd_found, data);
    STREAM_TO_UINT32(sPer_rx_data.phr_dec_error, data);
    STREAM_TO_UINT32(sPer_rx_data.phr_bit_error, data);
    STREAM_TO_UINT32(sPer_rx_data.psdu_dec_error, data);
    STREAM_TO_UINT32(sPer_rx_data.psdu_bit_error, data);
    STREAM_TO_UINT32(sPer_rx_data.sts_found, data);
    STREAM_TO_UINT32(sPer_rx_data.eof, data);

    jmethodID perRxCtor =
        env->GetMethodID(mPerRxDataClass, "<init>", "(IJJJJJJJJJJJJJ)V");
    jobject perRxObject = env->NewObject(
        mPerRxDataClass, perRxCtor, (int)sPer_rx_data.status,
        (long)sPer_rx_data.attempts, (long)sPer_rx_data.ACQ_detect,
        (long)sPer_rx_data.ACQ_rejects, (long)sPer_rx_data.RX_fail,
        (long)sPer_rx_data.sync_cir_ready, (long)sPer_rx_data.sfd_fail,
        (long)sPer_rx_data.sfd_found, (long)sPer_rx_data.phr_dec_error,
        (long)sPer_rx_data.phr_bit_error, (long)sPer_rx_data.psdu_dec_error,
        (long)sPer_rx_data.psdu_bit_error, (long)sPer_rx_data.sts_found,
        (long)sPer_rx_data.eof);
    if (mOnPerRxDataNotificationReceived != NULL) {
      env->CallVoidMethod(mObject, mOnPerRxDataNotificationReceived,
                          perRxObject);
      if (env->ExceptionCheck()) {
        env->ExceptionClear();
        JNI_TRACE_E("%s: fail to send PER Rx test data", __func__);
      }
    } else {
      JNI_TRACE_E("%s: PER Rx data MID is NULL", __func__);
    }
  }
}

void UwbRfTestManager::onLoopBackTestDataNotificationReceived(uint16_t len,
                                                              uint8_t *data) {
  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", __func__);
    return;
  }

  tUWB_LOOPBACK_DATA sUwb_loopback_data;
  jbyteArray psduData = NULL;
  uint16_t psduDataLen = 0;
  memset(&sUwb_loopback_data, 0, sizeof(tUWB_LOOPBACK_DATA));
  if (len != 0) {
    STREAM_TO_UINT8(sUwb_loopback_data.status, data);
    STREAM_TO_UINT32(sUwb_loopback_data.txts_int, data);
    STREAM_TO_UINT16(sUwb_loopback_data.txts_frac, data);
    STREAM_TO_UINT32(sUwb_loopback_data.rxts_int, data);
    STREAM_TO_UINT16(sUwb_loopback_data.rxts_frac, data);
    STREAM_TO_UINT16(sUwb_loopback_data.aoa_azimuth, data);
    STREAM_TO_UINT16(sUwb_loopback_data.aoa_elevation, data);
    STREAM_TO_UINT16(sUwb_loopback_data.phr, data);
    STREAM_TO_UINT16(sUwb_loopback_data.psdu_data_length, data);
    psduDataLen = sUwb_loopback_data.psdu_data_length;
    if (sUwb_loopback_data.psdu_data_length > 0) {
      STREAM_TO_ARRAY(sUwb_loopback_data.psdu_data, data,
                      sUwb_loopback_data.psdu_data_length);
      psduData = env->NewByteArray(sUwb_loopback_data.psdu_data_length);
      env->SetByteArrayRegion(psduData, 0, sUwb_loopback_data.psdu_data_length,
                              (jbyte *)sUwb_loopback_data.psdu_data);
    }

    jmethodID uwbLoopBackCtor =
        env->GetMethodID(mUwbLoopBackDataClass, "<init>", "(IJIJIIII[B)V");
    jobject uwbLoopBackObject = env->NewObject(
        mUwbLoopBackDataClass, uwbLoopBackCtor, (int)sUwb_loopback_data.status,
        (long)sUwb_loopback_data.txts_int, (int)sUwb_loopback_data.txts_frac,
        (long)sUwb_loopback_data.rxts_int, (int)sUwb_loopback_data.rxts_frac,
        (int)sUwb_loopback_data.aoa_azimuth,
        (int)sUwb_loopback_data.aoa_elevation, (int)sUwb_loopback_data.phr,
        psduData);
    if (mOnLoopBackTestDataNotificationReceived != NULL) {
      env->CallVoidMethod(mObject, mOnLoopBackTestDataNotificationReceived,
                          uwbLoopBackObject);
      if (env->ExceptionCheck()) {
        env->ExceptionClear();
        JNI_TRACE_E("%s: fail to send rf loopback test data", __func__);
      }
    } else {
      JNI_TRACE_E("%s: rf loopback data MID is NULL", __func__);
    }
  }
}

void UwbRfTestManager::onRxTestDataNotificationReceived(uint16_t len,
                                                        uint8_t *data) {
  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", __func__);
    return;
  }

  tUWB_RX_DATA sRx_data;
  jbyteArray psduData = NULL;
  uint16_t psduDataLen = 0;
  uint16_t aoaFirst, aoaSecond;
  memset(&sRx_data, 0, sizeof(tUWB_RX_DATA));
  if (len != 0) {
    STREAM_TO_UINT8(sRx_data.status, data);
    STREAM_TO_UINT32(sRx_data.rx_done_ts_int, data);
    STREAM_TO_UINT16(sRx_data.rx_done_ts_frac, data);
    /* Extract First AoA (first 2 bytes) */
    STREAM_TO_UINT16(aoaFirst, data);
    /* Extract Second AoA (next 2 bytes) */
    STREAM_TO_UINT16(aoaSecond, data);
    STREAM_TO_UINT8(sRx_data.toa_gap, data);
    STREAM_TO_UINT16(sRx_data.phr, data);
    STREAM_TO_UINT16(sRx_data.psdu_data_length, data);
    psduDataLen = sRx_data.psdu_data_length;
    if (sRx_data.psdu_data_length > 0) {
      STREAM_TO_ARRAY(sRx_data.psdu_data, data, sRx_data.psdu_data_length);
      psduData = env->NewByteArray(sRx_data.psdu_data_length);
      env->SetByteArrayRegion(psduData, 0, sRx_data.psdu_data_length,
                              (jbyte *)sRx_data.psdu_data);
    }

    jmethodID rxDataCtor =
        env->GetMethodID(mRxDataClass, "<init>", "(IJIIIII[B)V");
    jobject rxDataObject = env->NewObject(
        mRxDataClass, rxDataCtor, (int)sRx_data.status,
        (long)sRx_data.rx_done_ts_int, (int)sRx_data.rx_done_ts_frac,
        (int)aoaFirst, (int)aoaSecond, (int)sRx_data.toa_gap, (int)sRx_data.phr,
        psduData);
    if (mOnRxTestDataNotificationReceived != NULL) {
      env->CallVoidMethod(mObject, mOnRxTestDataNotificationReceived,
                          rxDataObject);
      if (env->ExceptionCheck()) {
        env->ExceptionClear();
        JNI_TRACE_E("%s: fail to send Rx test data", __func__);
      }
    } else {
      JNI_TRACE_E("%s: Rx test data MID is NULL", __func__);
    }
  }
}

void UwbRfTestManager::doLoadSymbols(JNIEnv *env, jobject thiz) {
  JNI_TRACE_I("%s: enter", __func__);
  env->GetJavaVM(&mVm);

  jclass clazz = env->GetObjectClass(thiz);
  if (clazz != NULL) {
    mClass = (jclass)env->NewGlobalRef(clazz);
    // The reference is only used as a proxy for callbacks.
    mObject = env->NewGlobalRef(thiz);
    mOnPeriodicTxDataNotificationReceived =
        env->GetMethodID(clazz, "onPeriodicTxDataNotificationReceived",
                         "(Lcom/android/uwb/test/UwbTestPeriodicTxResult;)V");
    mOnPerRxDataNotificationReceived = env->GetMethodID(
        clazz, "onPerRxDataNotificationReceived",
        "(Lcom/android/uwb/test/UwbTestRxPacketErrorRateResult;)V");
    mOnLoopBackTestDataNotificationReceived =
        env->GetMethodID(clazz, "onLoopBackTestDataNotificationReceived",
                         "(Lcom/android/uwb/test/UwbTestLoopBackTestResult;)V");
    mOnRxTestDataNotificationReceived =
        env->GetMethodID(clazz, "onRxTestDataNotificationReceived",
                         "(Lcom/android/uwb/test/UwbTestRxResult;)V");

    uwb_jni_cache_jclass(env, PERIODIC_TX_DATA_CLASS_NAME,
                         &mPeriodicTxDataClass);
    uwb_jni_cache_jclass(env, PER_RX_DATA_CLASS_NAME, &mPerRxDataClass);
    uwb_jni_cache_jclass(env, UWB_LOOPBACK_DATA_CLASS_NAME,
                         &mUwbLoopBackDataClass);
    uwb_jni_cache_jclass(env, RX_DATA_CLASS_NAME, &mRxDataClass);
  }
  JNI_TRACE_I("%s: exit", __func__);
}

/*******************************************************************************
**
** Function:        setTestConfigurations()
**
** Description:     application shall configure the Test configuration
*parameters
**
** Params:          env: JVM environment.
**                     o: Java object.
**                     sessionId: All Test configurations belonging to this
*Session ID
**                     noOfParams : The number of Test Configuration fields to
*follow
**                     testConfigLen : Length of TestConfigData
**                     TestConfig : Test Configurations for session
**
** Returns:         Returns byte array
**
**
*******************************************************************************/
jbyteArray UwbRfTestManager::setTestConfigurations(JNIEnv *env, jobject o,
                                                   jint sessionId,
                                                   jint noOfParams,
                                                   jint testConfigLen,
                                                   jbyteArray TestConfig) {
  tUWA_STATUS status;
  jbyteArray testConfigArray = NULL;
  uint8_t *testConfigData = NULL;
  JNI_TRACE_I("%s: Enter", __func__);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", __func__);
    return testConfigArray;
  }

  testConfigData = (uint8_t *)malloc(sizeof(uint8_t) * testConfigLen);
  if (testConfigData != NULL) {
    memset(testConfigData, 0, (sizeof(uint8_t) * testConfigLen));
    env->GetByteArrayRegion(TestConfig, 0, testConfigLen,
                            (jbyte *)testConfigData);
    setTestConfigRespStatus = false;
    SyncEventGuard guard(sUwaSetTestConfigEvent);
    JNI_TRACE_I("%d: testConfigLen", testConfigLen);
    status =
        UWA_TestSetConfig(sessionId, noOfParams, testConfigLen, testConfigData);
    free(testConfigData);
    if (status == UWA_STATUS_OK) {
      sUwaSetTestConfigEvent.wait(UWB_CMD_TIMEOUT);
      JNI_TRACE_E("%s: Success UWA_TestSetConfig Command", __func__);
      if (setTestConfigRespStatus) {
        testConfigArray =
            env->NewByteArray(sSetTestConfigLen + sizeof(sSetTestConfigStatus) +
                              sizeof(sNoOfTestConfigIds));
        env->SetByteArrayRegion(testConfigArray, 0, 1,
                                (jbyte *)&sSetTestConfigStatus);
        env->SetByteArrayRegion(testConfigArray, 1, 1,
                                (jbyte *)&sNoOfTestConfigIds);
        if (sSetTestConfigLen > 0) {
          env->SetByteArrayRegion(testConfigArray, 2, sSetTestConfigLen,
                                  (jbyte *)&sSetTestConfig[0]);
        }
      }
    } else {
      JNI_TRACE_E("%s: Failed UWA_TestSetConfig", __func__);
      return testConfigArray;
    }
  } else {
    JNI_TRACE_E("%s: Unable to Allocate Memory", __func__);
  }
  JNI_TRACE_I("%s: Exit", __func__);
  return testConfigArray;
}

/*******************************************************************************
**
** Function:        getTestConfigurations
**
** Description:     application shall retrieve the Test configuration parameters
**
** Params:         env: JVM environment.
**                     o: Java object.
**                     session id : Session Id to which get All test Config list
**                     noOfParams: Number of Test Config Params
**                     testConfigLen: Total Test Config lentgh
**                     TestConfig: Test Config Id List
**
** Returns:          Returns byte array
**
*******************************************************************************/
jbyteArray UwbRfTestManager::getTestConfigurations(JNIEnv *env, jobject o,
                                                   jint sessionId,
                                                   jint noOfParams,
                                                   jint testConfigLen,
                                                   jbyteArray TestConfig) {
  tUWA_STATUS status;
  jbyteArray testConfigArray = NULL;
  uint8_t *testConfigData = NULL;
  JNI_TRACE_I("%s: Enter", __func__);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", __func__);
    return testConfigArray;
  }

  getTestConfigRespStatus = false;
  testConfigData = (uint8_t *)malloc(sizeof(uint8_t) * testConfigLen);
  if (testConfigData != NULL) {
    memset(testConfigData, 0, (sizeof(uint8_t) * testConfigLen));
    env->GetByteArrayRegion(TestConfig, 0, testConfigLen,
                            (jbyte *)testConfigData);
    SyncEventGuard guard(sUwaGetTestConfigEvent);
    status =
        UWA_TestGetConfig(sessionId, noOfParams, testConfigLen, testConfigData);
    if (status == UWA_STATUS_OK) {
      sUwaGetTestConfigEvent.wait(UWB_CMD_TIMEOUT);
      if (getTestConfigRespStatus) {
        testConfigArray =
            env->NewByteArray(sGetTestConfigLen + sizeof(sNoOfTestConfigIds) +
                              sizeof(sGetTestConfigStatus));
        env->SetByteArrayRegion(testConfigArray, 0, 1,
                                (jbyte *)&sGetTestConfigStatus);
        env->SetByteArrayRegion(testConfigArray, 1, 1,
                                (jbyte *)&sNoOfTestConfigIds);
        env->SetByteArrayRegion(testConfigArray, 2, sGetTestConfigLen,
                                (jbyte *)&sGetTestConfig[0]);
      }
    } else {
      JNI_TRACE_E("%s: Failed UWA_TestGetConfig", __func__);
    }
    free(testConfigData);
  } else {
    JNI_TRACE_E("%s: Unable to Allocate Memory", __func__);
  }
  JNI_TRACE_I("%s: Exit", __func__);
  return testConfigArray;
}

/*******************************************************************************
**
** Function:        startPerRxTest
**
** Description:     start PER RX performance test
**
** Params:          env: JVM environment.
**                     o: Java object.
**                     refPsduData : Reference Psdu Data
**
** Returns:         UWA_STATUS_OK if success else returns
**                     UWA_STATUS_FAILED
*******************************************************************************/
jbyte UwbRfTestManager::startPerRxTest(JNIEnv *env, jobject o,
                                       jbyteArray refPsduData) {
  tUWA_STATUS status = UWA_STATUS_FAILED;
  uint16_t dataLen = 0;
  uint8_t *ref_psdu_data = NULL;
  JNI_TRACE_I("%s: Enter; ", __func__);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", __func__);
    return status;
  }

  if (IsRfTestOngoing) {
    JNI_TRACE_E("%s: UWB device Rf Test is Ongoing already", __func__);
    return status;
  }

  rfTestStatus = false;
  if (refPsduData != NULL) {
    dataLen = env->GetArrayLength(refPsduData);
    if (dataLen > 0) {
      ref_psdu_data = (uint8_t *)malloc(sizeof(uint8_t) * dataLen);
      if (ref_psdu_data != NULL) {
        memset(ref_psdu_data, 0, (sizeof(uint8_t) * dataLen));
        env->GetByteArrayRegion(refPsduData, 0, dataLen,
                                (jbyte *)ref_psdu_data);

        SyncEventGuard guard(sUwaRfTestEvent);
        IsRfTestOngoing = true;
        status = UWA_PerRxTest(dataLen, ref_psdu_data);
        if (UWA_STATUS_OK == status) {
          sUwaRfTestEvent.wait(UWB_CMD_TIMEOUT);
          if (!rfTestStatus) {
            IsRfTestOngoing = false;
          }
        } else {
          IsRfTestOngoing = false;
          JNI_TRACE_E("%s: UWA_PerRxTest Failed", __func__);
        }
        free(ref_psdu_data);
      } else {
        JNI_TRACE_E("%s: Unable to Allocate Memory", __func__);
      }
    } else {
      JNI_TRACE_I("%s: Length of refPsduData array is 0; ", __func__);
    }
  }
  JNI_TRACE_I("%s: Exit", __func__);
  return (rfTestStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        startPeriodicTxTest
**
** Description:     start PERIODIC Tx Test
**
** Params:         env: JVM environment.
**                    o: Java object.
**                    psduData : Reference Psdu Data
**
** Returns:         UWb_STATUS_OK if success  else returns
**                     UWA_STATUS_FAILED
**
*******************************************************************************/
jbyte UwbRfTestManager::startPeriodicTxTest(JNIEnv *env, jobject o,
                                            jbyteArray psduData) {
  tUWA_STATUS status = UWA_STATUS_FAILED;
  uint16_t dataLen = 0;
  uint8_t *psdu_Data = NULL;
  JNI_TRACE_I("%s: Enter; ", __func__);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", __func__);
    return status;
  }

  if (IsRfTestOngoing) {
    JNI_TRACE_E("%s: UWB device Rf Test is Ongoing already", __func__);
    return status;
  }

  rfTestStatus = false;
  if (psduData != NULL) {
    dataLen = env->GetArrayLength(psduData);
    if (dataLen > UCI_MAX_PAYLOAD_SIZE) {
      JNI_TRACE_E("%s: PER TX data size exceeds %d", __func__,
                  UCI_MAX_PAYLOAD_SIZE);
      return status;
    }
    psdu_Data = (uint8_t *)malloc(sizeof(uint8_t) * dataLen);
    if (psdu_Data != NULL) {
      memset(psdu_Data, 0, (sizeof(uint8_t) * dataLen));
      env->GetByteArrayRegion(psduData, 0, dataLen, (jbyte *)psdu_Data);

      SyncEventGuard guard(sUwaRfTestEvent);
      IsRfTestOngoing = true;
      status = UWA_PeriodicTxTest(dataLen, psdu_Data);
      if (UWA_STATUS_OK == status) {
        sUwaRfTestEvent.wait(UWB_CMD_TIMEOUT);
        if (!rfTestStatus) {
          IsRfTestOngoing = false;
        }
      } else {
        IsRfTestOngoing = false;
        JNI_TRACE_E("%s: UWA_PeriodicTxTest Failed", __func__);
      }
      free(psdu_Data);
    } else {
      JNI_TRACE_E("%s: Unable to Allocate Memory", __func__);
    }
  }

  JNI_TRACE_I("%s: Exit", __func__);
  return (rfTestStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        startUwbLoopBackTest
**
** Description:     start Rf Loop back test
**
** Params:          env: JVM environment.
**                     o: Java object.
**                     psduData : Reference Psdu Data
**
** Returns:         UWA_STATUS_OK if success else returns
**                     UWA_STATUS_FAILED
**
*******************************************************************************/
jbyte UwbRfTestManager::startUwbLoopBackTest(JNIEnv *env, jobject o,
                                             jbyteArray psduData) {
  tUWA_STATUS status = UWA_STATUS_FAILED;
  uint16_t dataLen = 0;
  uint8_t *psdu_Data = NULL;
  JNI_TRACE_I("%s: Enter; ", __func__);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", __func__);
    return status;
  }

  if (IsRfTestOngoing) {
    JNI_TRACE_I("%s: UWB device Rf Test is Ongoing already", __func__);
    return status;
  }

  rfTestStatus = false;
  if (psduData != NULL) {
    dataLen = env->GetArrayLength(psduData);
    if (dataLen > UCI_MAX_PAYLOAD_SIZE) {
      JNI_TRACE_E("%s: Loopback data size exceeds %d", __func__,
                  UCI_MAX_PAYLOAD_SIZE);
      return UWA_STATUS_FAILED;
    }
    psdu_Data = (uint8_t *)malloc(sizeof(uint8_t) * dataLen);
    if (psdu_Data != NULL) {
      memset(psdu_Data, 0, (sizeof(uint8_t) * dataLen));
      env->GetByteArrayRegion(psduData, 0, dataLen, (jbyte *)psdu_Data);

      SyncEventGuard guard(sUwaRfTestEvent);
      IsRfTestOngoing = true;
      status = UWA_UwbLoopBackTest(dataLen, psdu_Data);
      if (UWA_STATUS_OK == status) {
        sUwaRfTestEvent.wait(UWB_CMD_TIMEOUT);
        if (!rfTestStatus) {
          IsRfTestOngoing = false;
        }
      } else {
        IsRfTestOngoing = false;
        JNI_TRACE_E("%s: UWA_UwbLoopBackTest failed", __func__);
      }
      free(psdu_Data);
    } else {
      JNI_TRACE_E("%s: Unable to Allocate Memory", __func__);
    }
  }

  JNI_TRACE_I("%s: Exit", __func__);
  return (rfTestStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        startRxTest
**
** Description:     start Rx test
**
** Params:          env: JVM environment.
**                      o: Java object.
**
** Returns:         UWA_STATUS_OK if success  else returns
**                     UWA_STATUS_FAILED
**
*******************************************************************************/
jbyte UwbRfTestManager::startRxTest(JNIEnv *env, jobject o) {
  tUWA_STATUS status = UWA_STATUS_FAILED;
  JNI_TRACE_I("%s: Enter; ", __func__);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", __func__);
    return status;
  }

  if (IsRfTestOngoing) {
    JNI_TRACE_I("%s: UWB device Rf Test is Ongoing already", __func__);
    return status;
  }

  rfTestStatus = false;
  SyncEventGuard guard(sUwaRfTestEvent);
  IsRfTestOngoing = true;
  status = UWA_RxTest();
  if (UWA_STATUS_OK == status) {
    sUwaRfTestEvent.wait(UWB_CMD_TIMEOUT);
    if (!rfTestStatus) {
      IsRfTestOngoing = false;
    }
  } else {
    IsRfTestOngoing = false;
    JNI_TRACE_E("%s: UWA_RxTest failed", __func__);
  }

  JNI_TRACE_I("%s: Exit", __func__);
  return (rfTestStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        stopRfTest
**
** Description:     stop PER performance test
**
** Params:          env: JVM environment.
**                      o: Java object.
**
** Returns:         UWA_STATUS_OK if success  else returns
**                     UWA_STATUS_FAILED
*******************************************************************************/
jbyte UwbRfTestManager::stopRfTest(JNIEnv *env, jobject o) {
  tUWA_STATUS status = UWA_STATUS_FAILED;
  JNI_TRACE_I("%s: Enter; ", __func__);
  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", __func__);
    return status;
  }

  rfTestStatus = false;
  SyncEventGuard guard(sUwaRfTestEvent);
  status = UWA_TestStopSession();
  if (UWA_STATUS_OK == status) {
    sUwaRfTestEvent.wait(UWB_CMD_TIMEOUT);
  } else {
    JNI_TRACE_E("%s: UWA_TestStopSession failed", __func__);
  }

  if (rfTestStatus) {
    IsRfTestOngoing = false;
  }
  JNI_TRACE_I("%s: Exit", __func__);
  return (rfTestStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        uwaRfTestDeviceManagementCallback
**
** Description:     Receive Rf Test related device management events from UCI
*stack
**                  dmEvent: Device-management event ID.
**                  eventData: Data associated with event ID.
**
** Returns:         None
**
*******************************************************************************/
void uwaRfTestDeviceManagementCallback(uint8_t dmEvent,
                                       tUWA_DM_TEST_CBACK_DATA *eventData) {
  JNI_TRACE_I("%s: enter; event=0x%X", __func__, dmEvent);

  switch (dmEvent) {
  case UWA_DM_TEST_SET_CONFIG_RSP_EVT: // result of UWA_TestSetConfig
    JNI_TRACE_I("%s: UWA_DM_TEST_SET_CONFIG_RSP_EVT", __func__);
    {
      SyncEventGuard guard(sUwaSetTestConfigEvent);
      setTestConfigRespStatus = true;
      sSetTestConfigStatus = eventData->status;
      sSetTestConfigLen = eventData->sTest_set_config.tlv_size;
      sNoOfTestConfigIds = eventData->sTest_set_config.num_param_id;
      if (eventData->sTest_set_config.tlv_size > 0) {
        memcpy(sSetTestConfig, eventData->sTest_set_config.param_ids,
               eventData->sTest_set_config.tlv_size);
      }
      sUwaSetTestConfigEvent.notifyOne();
    }
    break;
  case UWA_DM_TEST_GET_CONFIG_RSP_EVT: /* Result of UWA_TestGetConfig */
    JNI_TRACE_I("%s: UWA_DM_TEST_GET_CONFIG_RSP_EVT", __func__);
    {
      SyncEventGuard guard(sUwaGetTestConfigEvent);
      getTestConfigRespStatus = true;
      sGetTestConfigStatus = eventData->status;
      sGetTestConfigLen = eventData->sTest_get_config.tlv_size;
      sNoOfTestConfigIds = eventData->sTest_get_config.no_of_ids;
      if (eventData->sTest_get_config.tlv_size) {
        memcpy(sGetTestConfig, eventData->sTest_get_config.param_tlvs,
               eventData->sTest_get_config.tlv_size);
      }
      sUwaGetTestConfigEvent.notifyOne();
    }
    break;

  case UWA_DM_TEST_PERIODIC_TX_RSP_EVT: /* result of periodic tx command */
    JNI_TRACE_I("%s: UWA_DM_TEST_PERIODIC_TX_RSP_EVT", __func__);
    {
      SyncEventGuard guard(sUwaRfTestEvent);
      if (eventData->status == UWA_STATUS_OK) {
        rfTestStatus = true;
        JNI_TRACE_I("%s: UWA_DM_TEST_PERIODIC_TX_RSP_EVT Success", __func__);
      } else {
        JNI_TRACE_E("%s: UWA_DM_TEST_PERIODIC_TX_RSP_EVT failed", __func__);
      }
      sUwaRfTestEvent.notifyOne();
    }
    break;

  case UWA_DM_TEST_PER_RX_RSP_EVT: /* result of per rx command */
    JNI_TRACE_I("%s: UWA_DM_TEST_PER_RX_RSP_EVT", __func__);
    {
      SyncEventGuard guard(sUwaRfTestEvent);
      if (eventData->status == UWA_STATUS_OK) {
        rfTestStatus = true;
        JNI_TRACE_I("%s: UWA_DM_TEST_PER_RX_RSP_EVT Success", __func__);
      } else {
        JNI_TRACE_E("%s: UWA_DM_TEST_PER_RX_RSP_EVT failed", __func__);
      }
      sUwaRfTestEvent.notifyOne();
    }
    break;

  case UWA_DM_TEST_LOOPBACK_RSP_EVT: /* result of rf loop back command */
    JNI_TRACE_I("%s: UWA_DM_TEST_UWB_LOOPBACK_EVT", __func__);
    {
      SyncEventGuard guard(sUwaRfTestEvent);
      if (eventData->status == UWA_STATUS_OK) {
        rfTestStatus = true;
        JNI_TRACE_I("%s: UWA_DM_TEST_UWB_LOOPBACK_EVT Success", __func__);
      } else {
        JNI_TRACE_E("%s: UWA_DM_TEST_UWB_LOOPBACK_EVT failed", __func__);
      }
      sUwaRfTestEvent.notifyOne();
    }
    break;

  case UWA_DM_TEST_RX_RSP_EVT: /* result of rx test command */
    JNI_TRACE_I("%s: UWA_DM_TEST_RX_RSP_EVT", __func__);
    {
      SyncEventGuard guard(sUwaRfTestEvent);
      if (eventData->status == UWA_STATUS_OK) {
        rfTestStatus = true;
        JNI_TRACE_I("%s: UWA_DM_TEST_RX_RSP_EVT Success", __func__);
      } else {
        JNI_TRACE_E("%s: UWA_DM_TEST_RX_RSP_EVT failed", __func__);
      }
      sUwaRfTestEvent.notifyOne();
    }
    break;

  case UWA_DM_TEST_STOP_SESSION_RSP_EVT: /* result of per stop command */
    JNI_TRACE_I("%s: UWA_DM_TEST_STOP_SESSION_RSP_EVT", __func__);
    {
      SyncEventGuard guard(sUwaRfTestEvent);
      if (eventData->status == UWA_STATUS_OK) {
        rfTestStatus = true;
        JNI_TRACE_I("%s: UWA_DM_TEST_STOP_SESSION_RSP_EVT Success", __func__);
      } else {
        JNI_TRACE_E("%s: UWA_DM_TEST_STOP_SESSION_RSP_EVT failed", __func__);
      }
      sUwaRfTestEvent.notifyOne();
    }
    break;

  case UWA_DM_TEST_PERIODIC_TX_NTF_EVT:
    JNI_TRACE_I("%s: UWA_DM_TEST_PERIODIC_TX_NTF_EVT", __func__);
    {
      IsRfTestOngoing = false;
      if (eventData->rf_test_data.length > 0) {
        uwbRfTestManager.onPeriodicTxDataNotificationReceived(
            eventData->rf_test_data.length, &eventData->rf_test_data.data[0]);
      }
    }
    break;

  case UWA_DM_TEST_PER_RX_NTF_EVT:
    JNI_TRACE_I("%s: UWA_DM_TEST_PER_RX_NTF_EVT", __func__);
    {
      IsRfTestOngoing = false;
      if (eventData->rf_test_data.length > 0) {
        uwbRfTestManager.onPerRxDataNotificationReceived(
            eventData->rf_test_data.length, &eventData->rf_test_data.data[0]);
      }
    }
    break;

  case UWA_DM_TEST_LOOPBACK_NTF_EVT:
    JNI_TRACE_I("%s: UWA_DM_TEST_LOOPBACK_NTF_EVT", __func__);
    {
      IsRfTestOngoing = false;
      if (eventData->rf_test_data.length > 0) {
        uwbRfTestManager.onLoopBackTestDataNotificationReceived(
            eventData->rf_test_data.length, &eventData->rf_test_data.data[0]);
      }
    }
    break;

  case UWA_DM_TEST_RX_NTF_EVT:
    JNI_TRACE_I("%s: UWA_DM_TEST_RX_NTF_EVT", __func__);
    {
      IsRfTestOngoing = false;
      if (eventData->rf_test_data.length > 0) {
        uwbRfTestManager.onRxTestDataNotificationReceived(
            eventData->rf_test_data.length, &eventData->rf_test_data.data[0]);
      }
    }
    break;

  default:
    JNI_TRACE_I("%s: unhandled event", __func__);
    break;
  }
}
} // namespace android

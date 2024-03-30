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

/* UwbRfTestNativeManager is unused now*/
/*#include "JniLog.h"
#include "ScopedJniEnv.h"
#include "SyncEvent.h"
#include "UwbAdaptation.h"
#include "UwbJniInternal.h"
#include "UwbRfTestManager.h"
#include "uwb_config.h"
#include "uwb_hal_int.h"

namespace android {

const char *UWB_RFTEST_NATIVE_MANAGER_CLASS_NAME =
    "com/android/uwb/jni/NativeUwbRfTestManager";

static UwbRfTestManager &uwbRfTestManager = UwbRfTestManager::getInstance();

*//*******************************************************************************
**
** Function:        uwbRfTestNativeManager_setTestConfigurations()
**
** Description:     application shall configure the Test configuration
*parameters
**
** Params:          env: JVM environment.
**                  o: Java object.
**                  sessionId: All Test configurations belonging to this Session
*ID
**                  noOfParams : The number of Test Configuration fields to
*follow
**                  testConfigLen : Length of TestConfigData
**                  TestConfig : Test Configurations for session
**
** Returns:         Returns byte array
**
**
*******************************************************************************//*
jbyteArray uwbRfTestNativeManager_setTestConfigurations(
    JNIEnv *env, jobject o, jint sessionId, jint noOfParams, jint testConfigLen,
    jbyteArray testConfigArray) {
  return uwbRfTestManager.setTestConfigurations(env, o, sessionId, noOfParams,
                                                testConfigLen, testConfigArray);
}

*//*******************************************************************************
**
** Function:        uwbRfTestNativeManager_getTestConfigurations
**
** Description:     application shall retrieve the Test configuration parameters
**
** Params:       env: JVM environment.
**                  o: Java object.
**                  session id : Session Id to which get All test Config list
**                  noOfParams: Number of Test Config Params
**                  testConfigLen: Total Test Config lentgh
**                  TestConfig: Test Config Id List
**
** Returns:         Returns byte array
**
*******************************************************************************//*
jbyteArray uwbRfTestNativeManager_getTestConfigurations(
    JNIEnv *env, jobject o, jint sessionId, jint noOfParams, jint testConfigLen,
    jbyteArray testConfigArray) {
  return uwbRfTestManager.getTestConfigurations(env, o, sessionId, noOfParams,
                                                testConfigLen, testConfigArray);
}

*//*******************************************************************************
**
** Function:        uwbRfTestNativeManager_startPerRxTest
**
** Description:     start Packet Error Rate (PER) RX performance test
**
** Params:          env: JVM environment.
**                      o: Java object.
**                      refPsduData : Reference Psdu Data
**
** Returns:      UWA_STATUS_OK if success  else returns
**                  UWA_STATUS_FAILED
*******************************************************************************//*
jbyte uwbRfTestNativeManager_startPerRxTest(JNIEnv *env, jobject o,
                                            jbyteArray refPsduData) {
  return uwbRfTestManager.startPerRxTest(env, o, refPsduData);
}

*//*******************************************************************************
**
** Function:        uwbRfTestNativeManager_startPeriodicTxTest
**
** Description:     start PERIODIC Tx Test
**
** Params:          env: JVM environment.
**                      o: Java object.
**                      psduData : Reference Psdu Data
**
** Returns:      UWA_STATUS_OK if success  else returns
**                  UWA_STATUS_FAILED
**
*******************************************************************************//*
jbyte uwbRfTestNativeManager_startPeriodicTxTest(JNIEnv *env, jobject o,
                                                 jbyteArray psduData) {
  return uwbRfTestManager.startPeriodicTxTest(env, o, psduData);
}

*//*******************************************************************************
**
** Function:        uwbRfTestNativeManager_startUwbLoopBackTest
**
** Description:     start Rf Loop back test
**
** Params:          env: JVM environment.
**                     o: Java object.
**                     psduData : Reference Psdu Data
**
** Returns:      UWA_STATUS_OK if success  else returns
**                  UWA_STATUS_FAILED
**
*******************************************************************************//*
jbyte uwbRfTestNativeManager_startUwbLoopBackTest(JNIEnv *env, jobject o,
                                                  jbyteArray psduData) {
  return uwbRfTestManager.startUwbLoopBackTest(env, o, psduData);
}

*//*******************************************************************************
**
** Function:        uwbRfTestNativeManager_stopRfTest
**
** Description:     stop PER performance test
**
** Params:          env: JVM environment.
**                      o: Java object.
**
** Returns:      UWA_STATUS_OK if success  else returns
**                  UWA_STATUS_FAILED
*******************************************************************************//*
jbyte uwbRfTestNativeManager_stopRfTest(JNIEnv *env, jobject o) {
  return uwbRfTestManager.stopRfTest(env, o);
}

*//*******************************************************************************
**
** Function:        uwbRfTestNativeManager_startRxTest
**
** Description:     start RX test
**
** Params:       env: JVM environment.
**                  o: Java object.
**
** Returns:      UWA_STATUS_OK if success  else returns
**                  UWA_STATUS_FAILED
*******************************************************************************//*
jbyte uwbRfTestNativeManager_startRxTest(JNIEnv *env, jobject o) {
  return uwbRfTestManager.startRxTest(env, o);
}

*//*******************************************************************************
**
** Function:        uwbRfTestNativeManager_init
**
** Description:     Initialize variables.
**
** Params           env: JVM environment.
**                     o: Java object.
**
** Returns:         True if ok.
**
*******************************************************************************//*
jboolean uwbRfTestNativeManager_init(JNIEnv *env, jobject o) {
  uwbRfTestManager.doLoadSymbols(env, o);
  return JNI_TRUE;
}

*//*****************************************************************************
**
** JNI functions for android
** UWB service layer has to invoke these APIs to get required functionality
**
*****************************************************************************//*
static JNINativeMethod gMethods[] = {
    {"nativeInit", "()Z", (void *)uwbRfTestNativeManager_init},
    {"nativeSetTestConfigurations", "(III[B)[B",
     (void *)uwbRfTestNativeManager_setTestConfigurations},
    {"nativeGetTestConfigurations", "(III[B)[B",
     (void *)uwbRfTestNativeManager_getTestConfigurations},
    {"nativeStartPerRxTest", "([B)B",
     (void *)uwbRfTestNativeManager_startPerRxTest},
    {"nativeStartPeriodicTxTest", "([B)B",
     (void *)uwbRfTestNativeManager_startPeriodicTxTest},
    {"nativeStartUwbLoopBackTest", "([B)B",
     (void *)uwbRfTestNativeManager_startUwbLoopBackTest},
    {"nativeStartRxTest", "()B", (void *)uwbRfTestNativeManager_startRxTest},
    {"nativeStopRfTest", "()B", (void *)uwbRfTestNativeManager_stopRfTest}};

*//*******************************************************************************
**
** Function:        register_UwbRfTestNativeManager
**
** Description:     Regisgter JNI functions of UwbEventManager class with Java
*Virtual Machine.
**
** Params:          env: Environment of JVM.
**
** Returns:         Status of registration (JNI version).
**
*******************************************************************************//*
int register_com_android_uwb_dhimpl_UwbRfTestNativeManager(JNIEnv *env) {
  JNI_TRACE_I("%s: enter", __func__);
  return jniRegisterNativeMethods(env, UWB_RFTEST_NATIVE_MANAGER_CLASS_NAME,
                                  gMethods,
                                  sizeof(gMethods) / sizeof(gMethods[0]));
}

} // namespace android*/

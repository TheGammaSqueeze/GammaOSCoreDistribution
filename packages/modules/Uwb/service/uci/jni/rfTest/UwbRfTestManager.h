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

#ifndef _UWB_RFTEST_NATIVE_MANAGER_H_
#define _UWB_RFTEST_NATIVE_MANAGER_H_
namespace android {

typedef struct {
  uint8_t status;          ///< Status
  uint32_t attempts;       ///< No. of RX attempts
  uint32_t ACQ_detect;     ///< No. of times signal was detected
  uint32_t ACQ_rejects;    ///< No. of times signal was rejected
  uint32_t RX_fail;        ///< No. of times RX did not go beyond ACQ stage
  uint32_t sync_cir_ready; ///< No. of times sync CIR ready event was received
  uint32_t sfd_fail;  ///< No. of time RX was stuck at either ACQ detect or sync
                      ///< CIR ready
  uint32_t sfd_found; ///< No. of times SFD was found
  uint32_t phr_dec_error;  ///< No. of times PHR decode failed
  uint32_t phr_bit_error;  ///< No. of times PHR bits in error
  uint32_t psdu_dec_error; ///< No. of times payload decode failed
  uint32_t psdu_bit_error; ///< No. of times payload bits in error
  uint32_t sts_found;      ///< No. of times STS detection was successful
  uint32_t eof;            ///< No. of times end of frame event was triggered
} tPER_RX_DATA;

typedef struct {
  uint8_t status;    //< Status
  uint32_t txts_int; //< Integer part of timestamp in 1/124.8 us resolution
  uint16_t
      txts_frac; //< Fractional part of timestamp in 1/124.8/512 µs resolution
  uint32_t rxts_int; //< Integer part of timestamp in 1/124.8 us resolution
  uint16_t
      rxts_frac; //< Fractional part of timestamp in 1/124.8/512 us resolution
  uint16_t aoa_azimuth;   //< AoA Azimuth in degrees and it is signed value in
                          // Q9.7 format
  uint16_t aoa_elevation; //< AoA Elevation  in degrees and it is signed value
                          // in Q9.7 format
  uint16_t phr;           //< Received PHR (bits 0-12 as per IEEE spec)
  uint16_t psdu_data_length;           //<PSDU Data Length
  uint8_t psdu_data[UCI_PSDU_SIZE_4K]; ///< Received PSDU Data bytes
} tUWB_LOOPBACK_DATA;

typedef struct {
  uint8_t status;           //< Status
  uint32_t rx_done_ts_int;  //< Integer part of timestamp in 1/124.8MHz ticks
  uint16_t rx_done_ts_frac; //< Fractional part of timestamp in 1/(128 *
                            // 499.2MHz) ticks resolution
  uint16_t aoa_azimuth;     //< AoA Azimuth in degrees and it is signed value in
                            // Q9.7 format
  uint16_t aoa_elevation;   //< AoA Elevation  in degrees and it is signed value
                            // in Q9.7 format
  uint8_t toa_gap; //< ToA of main path minus ToA of first path in nanoseconds
  uint16_t phr;    //<Received PHR (bits 0-12 as per IEEE spec)
  uint16_t psdu_data_length;           //<PSDU Data Length
  uint8_t psdu_data[UCI_PSDU_SIZE_4K]; //< Received PSDU Data bytes
} tUWB_RX_DATA;

typedef struct {
  uint8_t status; ///< Status
} tPERIODIC_TX_DATA;

class UwbRfTestManager {
public:
  static UwbRfTestManager &getInstance();
  void doLoadSymbols(JNIEnv *env, jobject o);

  /* CallBack functions */
  void onPeriodicTxDataNotificationReceived(uint16_t len, uint8_t *data);
  void onPerRxDataNotificationReceived(uint16_t len, uint8_t *data);
  void onLoopBackTestDataNotificationReceived(uint16_t len, uint8_t *data);
  void onRxTestDataNotificationReceived(uint16_t len, uint8_t *data);

  /* API functions */
  jbyteArray setTestConfigurations(JNIEnv *env, jobject o, jint sessionId,
                                   jint noOfParams, jint testConfigLen,
                                   jbyteArray TestConfig);
  jbyteArray getTestConfigurations(JNIEnv *env, jobject o, jint sessionId,
                                   jint noOfParams, jint testConfigLen,
                                   jbyteArray TestConfig);
  jbyte startPerRxTest(JNIEnv *env, jobject o, jbyteArray refPsduData);
  jbyte startPeriodicTxTest(JNIEnv *env, jobject o, jbyteArray psduData);
  jbyte startUwbLoopBackTest(JNIEnv *env, jobject o, jbyteArray psduData);
  jbyte startRxTest(JNIEnv *env, jobject o);
  jbyte stopRfTest(JNIEnv *env, jobject o);

private:
  UwbRfTestManager();

  static UwbRfTestManager mObjTestManager;

  JavaVM *mVm;

  jclass mClass;   // Reference to Java  class
  jobject mObject; // Weak ref to Java object to call on

  jclass mPeriodicTxDataClass;
  jclass mPerRxDataClass;
  jclass mUwbLoopBackDataClass;
  jclass mRxDataClass;

  jmethodID mOnPeriodicTxDataNotificationReceived;
  jmethodID mOnPerRxDataNotificationReceived;
  jmethodID mOnLoopBackTestDataNotificationReceived;
  jmethodID mOnRxTestDataNotificationReceived;
};

} // namespace android
#endif
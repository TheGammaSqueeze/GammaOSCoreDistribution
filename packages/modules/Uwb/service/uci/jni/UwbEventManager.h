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

#ifndef _UWB_NATIVE_MANAGER_H_
#define _UWB_NATIVE_MANAGER_H_

namespace android {

class UwbEventManager {
public:
  static UwbEventManager &getInstance();
  void doLoadSymbols(JNIEnv *env, jobject o);

  void onDeviceStateNotificationReceived(uint8_t state);
  void onRangeDataNotificationReceived(tUWA_RANGE_DATA_NTF *ranging_ntf_data);
  void onRawUciNotificationReceived(uint8_t *data, uint16_t length);
  void onSessionStatusNotificationReceived(uint32_t sessionId, uint8_t state,
                                           uint8_t reasonCode);
  void onCoreGenericErrorNotificationReceived(uint8_t state);
  void onMulticastListUpdateNotificationReceived(
      tUWA_SESSION_UPDATE_MULTICAST_LIST_NTF *multicast_list_ntf);
  void onBlinkDataTxNotificationReceived(uint8_t state);
  void onVendorUciNotificationReceived(uint8_t gid, uint8_t oid, uint8_t* data, uint16_t length);
  void onVendorDeviceInfo(uint8_t* data, uint8_t length);

private:
  UwbEventManager();

  static UwbEventManager mObjUwbManager;

  JavaVM *mVm;

  jclass mClass;   // Reference to Java  class
  jobject mObject; // Weak ref to Java object to call on

  jclass mRangeDataClass;
  jclass mRangingTwoWayMeasuresClass;
  jclass mRangeTdoaMeasuresClass;
  jclass mMulticastUpdateListDataClass;

  jmethodID mOnRangeDataNotificationReceived;
  jmethodID mOnSessionStatusNotificationReceived;
  jmethodID mOnCoreGenericErrorNotificationReceived;
  jmethodID mOnMulticastListUpdateNotificationReceived;
  // TODO following native methods to be implemented in native layer.
  jmethodID mOnDeviceStateNotificationReceived;
  jmethodID mOnBlinkDataTxNotificationReceived;
  jmethodID mOnRawUciNotificationReceived;
  jmethodID mOnVendorUciNotificationReceived;
  jmethodID mOnVendorDeviceInfo;
};

} // namespace android
#endif

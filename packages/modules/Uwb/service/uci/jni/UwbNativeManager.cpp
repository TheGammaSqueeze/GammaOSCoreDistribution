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

#include <vector>

#include "UwbJniInternal.h"
#include "JniLog.h"
#include "ScopedJniEnv.h"
#include "SyncEvent.h"
#include "UwbAdaptation.h"
#include "UwbEventManager.h"
#include "uwb_api.h"
#include "uwb_config.h"
#include "uwb_hal_int.h"

#define INVALID_SESSION_ID 0xFFFFFFFF

namespace android {

const char *UWB_NATIVE_MANAGER_CLASS_NAME =
    "com/android/server/uwb/jni/NativeUwbManager";

bool uwb_debug_enabled = true;
static conformanceTestData_t ConformanceDataConf;

static std::map<unsigned int, SessionRangingData> sAveragedRangingData;
static std::mutex sSessionMutex;

bool gIsUwaEnabled = false;
bool gIsMaxPpmValueAvailable = false;

static SyncEvent sUwaEnableEvent;        // event for UWA_Enable()
static SyncEvent sUwaDisableEvent;       // event for UWA_Disable
static SyncEvent sUwaSetConfigEvent;     // event for Set_Config....
static SyncEvent sUwaSetAppConfigEvent;  // event for Set_AppConfig....
static SyncEvent sUwaGetConfigEvent;     // event for Get_Config....
static SyncEvent sUwaGetAppConfigEvent;  // event for Get_AppConfig....
static SyncEvent sUwaDeviceResetEvent;   // event for deviceResetEvent
static SyncEvent sUwaRngStartEvent;      // event for ranging start
static SyncEvent sUwaRngStopEvent;       // event for ranging stop
static SyncEvent sUwadeviceNtfEvent;     // event for device status NTF
static SyncEvent sUwaSessionInitEvent;   // event for sessionInit resp
static SyncEvent sUwaSessionDeInitEvent; // event for sessionDeInit resp
static SyncEvent
    sUwaGetSessionCountEvent;            // event for get session count response
static SyncEvent sUwaGetDeviceInfoEvent; // event for get Device Info
static SyncEvent
    sUwaGetRangingCountEvent; // event for get ranging count response
static SyncEvent sUwaGetSessionStatusEvent; // event for get the session status
static SyncEvent
    sUwaMulticastListUpdateEvent; // event for
                                  // UWA_ControllerMulticastListUpdate
static SyncEvent sUwaSendBlinkDataEvent;
static SyncEvent sErrNotify;
static SyncEvent sUwaSetCountryCodeEvent; // event for
                                          // UWA_ControllerSetCountryCode
static SyncEvent sUwaSendRawUciEvt; // event for UWA_SendRawCommand
static SyncEvent sUwaGetDeviceCapsEvent; // event for Get Device Capabilities

static deviceInfo_t sUwbDeviceInfo;
static uint8_t sSetAppConfig[UCI_MAX_PAYLOAD_SIZE];
static uint8_t sGetAppConfig[UCI_MAX_PAYLOAD_SIZE];
static uint8_t sGetCoreConfig[UCI_MAX_PAYLOAD_SIZE];
static uint8_t sSetCoreConfig[UCI_MAX_PAYLOAD_SIZE];
static uint8_t sUwbDeviceCapability[UCI_MAX_PKT_SIZE];
static uint8_t sSendRawResData[UCI_MAX_PAYLOAD_SIZE];
static uint32_t sRangingCount = 0;
static uint8_t sNoOfAppConfigIds = 0x00;
static uint8_t sNoOfCoreConfigIds = 0x00;
static uint8_t sSessionCount = -1;
static uint16_t sDevCapInfoLen = 0;
static uint16_t sDevCapInfoIds = 0x00;
static uint16_t sGetCoreConfigLen;
static uint16_t sGetAppConfigLen;
static uint16_t sSetAppConfigLen;
static uint8_t sGetAppConfigStatus;
static uint8_t sSetAppConfigStatus;
static uint8_t sSendBlinkDataStatus;
static uint16_t sSendRawResLen;

/* command response status */
static bool sSessionInitStatus = false;
static bool sSessionDeInitStatus = false;
static bool sIsDeviceResetDone =
    false; // whether Reset Performed is Successful is done or not
static bool sRangeStartStatus = false;
static bool sRangeStopStatus = false;
static bool sSetAppConfigRespStatus = false;
static bool sGetAppConfigRespStatus = false;
static bool sMulticastListUpdateStatus = false;
static bool sSetCountryCodeStatus = false;
static bool sGetDeviceCapsRespStatus = false;

static uint8_t sSessionState = UWB_UNKNOWN_SESSION;

static eUWBS_DEVICE_STATUS_t sDeviceState = UWBS_STATUS_ERROR;

static UwbEventManager &uwbEventManager = UwbEventManager::getInstance();

jint MSB_BITMASK = 0x000000FF;

/* Function to calculate and update ranging data averaging value into ranging
 * data notification */
static void update_ranging_data_average(tUWA_RANGE_DATA_NTF *rangingDataNtf) {
  static const char fn[] = "update_ranging_data_average";
  UNUSED(fn);
  // Get Current Session Data
  SessionRangingData &sessionData =
      sAveragedRangingData[rangingDataNtf->session_id];

  // Calculate the Average of N Distances for every Anchor, Where N is Sampling
  // Rate for that Anchor
  for (int i = 0; i < rangingDataNtf->no_of_measurements; i++) {
    // Get current Two way measures object
    tUWA_TWR_RANGING_MEASR &twr_range_measr =
        rangingDataNtf->ranging_measures.twr_range_measr[i];
    // Get the Current Anchor Distance Queue
    auto &anchorDistanceQueue = sessionData.anchors[i];
    JNI_TRACE_I("%s: Input Distance is: %d", fn, twr_range_measr.distance);
    // If Number of distances in Queue is more than Sampling Rate
    if (anchorDistanceQueue.size() >= sessionData.samplingRate) {
      // Remove items from the queue until items in the queue is one less than
      // sampling rate
      while (anchorDistanceQueue.size() >= sessionData.samplingRate) {
        JNI_TRACE_I("%s: Distance Popped from Queue: %d", fn,
                    anchorDistanceQueue.front());
        anchorDistanceQueue.pop_front();
      }
    }
    // Push the New distance item into the Anchor Distance Queue
    anchorDistanceQueue.push_back(twr_range_measr.distance);
    // Calculate average of items(Except where distance is FFFF)
    // in the Queue and update averaged distance into the distance field
    uint32_t divider = 0;
    uint32_t sum = 0;
    for (auto it = anchorDistanceQueue.begin(); it != anchorDistanceQueue.end();
         ++it) {
      if (*it != 0xFFFF) {
        sum = (uint32_t)(sum + *it);
        divider++;
      }
    }
    if (divider > 0) {
      twr_range_measr.distance = sum / divider;
    } else {
      twr_range_measr.distance = 0xFFFF;
    }
    JNI_TRACE_I("%s: Averaged Distance is: %d", fn, twr_range_measr.distance);
  }
}

/*******************************************************************************
**
** Function:        notifyRangeDataNotification
**
** Description:     Notify the Range data  to application
**
** Returns:         void
**
*******************************************************************************/
void notifyRangeDataNotification(tUWA_RANGE_DATA_NTF *ranging_data) {
  static const char fn[] = "notifyRangeDataNotification";
  UNUSED(fn);
  JNI_TRACE_I("%s: Enter", fn);

  if (ranging_data->ranging_measure_type == ONE_WAY_RANGING) {
    uwbEventManager.onRangeDataNotificationReceived(ranging_data);
  } else {
    {
      std::unique_lock<std::mutex> lock(sSessionMutex);
      unsigned int session_id = ranging_data->session_id;
      auto it = sAveragedRangingData.find(session_id);
      if (it != sAveragedRangingData.end()) {
        if (sAveragedRangingData[session_id].samplingRate > 1) {
          JNI_TRACE_I("%s: Before Averaging", fn);
          update_ranging_data_average(ranging_data);
          JNI_TRACE_I("%s: After Averaging", fn);
        }
      }
    }
    uwbEventManager.onRangeDataNotificationReceived(ranging_data);
  }
}

/*******************************************************************************
**
** Function:        uwaDeviceManagementCallback
**
** Description:     Receive device management events from UCI stack.
**                  dmEvent: Device-management event ID.
**                  eventData: Data associated with event ID.
**
** Returns:         None
**
*******************************************************************************/
static void uwaDeviceManagementCallback(uint8_t dmEvent,
                                        tUWA_DM_CBACK_DATA *eventData) {
  static const char fn[] = "uwaDeviceManagementCallback";
  UNUSED(fn);
  JNI_TRACE_I("%s: enter; event=0x%X", fn, dmEvent);

  switch (dmEvent) {
  case UWA_DM_ENABLE_EVT: /* Result of UWA_Enable */
  {
    SyncEventGuard guard(sUwaEnableEvent);
    JNI_TRACE_I("%s: uwa_dm_enable_EVT; status=0x%X", fn, eventData->status);
    gIsUwaEnabled = eventData->status == UWA_STATUS_OK;
    sUwaEnableEvent.notifyOne();
  } break;

  case UWA_DM_DISABLE_EVT: /* Result of UWA_Disable */
  {
    SyncEventGuard guard(sUwaDisableEvent);
    JNI_TRACE_I("%s: UWA_DM_DISABLE_EVT", fn);
    gIsUwaEnabled = false;
    sUwaDisableEvent.notifyOne();
  } break;
  case UWA_DM_DEVICE_RESET_RSP_EVT: // result of UWA_SendDeviceReset
  {
    JNI_TRACE_I("%s: UWA_DM_DEVICE_RESET_RSP_EVT", fn);
    SyncEventGuard guard(sUwaDeviceResetEvent);
    if (eventData->status != UWA_STATUS_OK) {
      JNI_TRACE_E("%s: UWA_DM_DEVICE_RESET_RSP_EVT failed", fn);
    } else {
      sIsDeviceResetDone = true;
    }
    sUwaDeviceResetEvent.notifyOne();
  } break;
  case UWA_DM_DEVICE_STATUS_NTF_EVT:
    JNI_TRACE_I("%s: UWA_DM_DEVICE_STATUS_NTF_EVT", fn);
    {
      JNI_TRACE_I("device status = %x", eventData->dev_status.status);
      SyncEventGuard guard(sUwadeviceNtfEvent);
      sDeviceState = (eUWBS_DEVICE_STATUS_t)eventData->dev_status.status;
      if (sDeviceState == UWBS_STATUS_ERROR)
        sErrNotify.notifyAll();
      else
        sUwadeviceNtfEvent.notifyOne();
      uwbEventManager.onDeviceStateNotificationReceived(sDeviceState);
    }
    break;
  case UWA_DM_CORE_GET_DEVICE_INFO_RSP_EVT:
    JNI_TRACE_I("%s: UWA_DM_CORE_GET_DEVICE_INFO_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaGetDeviceInfoEvent);
      if (eventData->status == UWA_STATUS_OK) {
        sUwbDeviceInfo.uciVersion = eventData->sGet_device_info.uci_version;
        sUwbDeviceInfo.macVersion = eventData->sGet_device_info.mac_version;
        sUwbDeviceInfo.phyVersion = eventData->sGet_device_info.phy_version;
        sUwbDeviceInfo.uciTestVersion =
            eventData->sGet_device_info.uciTest_version;
        uwbEventManager.onVendorDeviceInfo(eventData->sGet_device_info.vendor_info, eventData->sGet_device_info.vendor_info_len);
      } else {
        JNI_TRACE_E("%s: UWA_DM_CORE_GET_DEVICE_INFO_RSP_EVT failed", fn);
      }
      sUwaGetDeviceInfoEvent.notifyOne();
    }
    break;
  case UWA_DM_CORE_SET_CONFIG_RSP_EVT: // result of UWA_SetCoreConfig
    JNI_TRACE_I("%s: UWA_DM_CORE_SET_CONFIG_RSP_EVT", fn);
    {
      if (eventData->status != UWA_STATUS_OK) {
        JNI_TRACE_E("%s: UWA_DM_CORE_SET_CONFIG_RSP_EVT failed", fn);
      }
      if (eventData->sCore_set_config.tlv_size > 0) {
        memcpy(sSetCoreConfig, eventData->sCore_set_config.param_ids,
               eventData->sCore_set_config.tlv_size);
      }
      SyncEventGuard guard(sUwaSetConfigEvent);
      sUwaSetConfigEvent.notifyOne();
    }
    break;
  case UWA_DM_CORE_GET_CONFIG_RSP_EVT: /* Result of UWA_GetCoreConfig */
    JNI_TRACE_I("%s: UWA_DM_CORE_GET_CONFIG_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaGetConfigEvent);
      if (eventData->status == UWA_STATUS_OK) {
        sGetCoreConfigLen = eventData->sCore_get_config.tlv_size;
        sNoOfCoreConfigIds = eventData->sCore_get_config.no_of_ids;
      } else {
        JNI_TRACE_E("%s: UWA_DM_GET_CONFIG failed", fn);
        /* As of now will cary the failed ids list till this point */
        sGetCoreConfigLen = 0;
        sNoOfCoreConfigIds = 0;
      }
      if (eventData->sCore_get_config.tlv_size > 0 &&
          eventData->sCore_get_config.tlv_size <= sizeof(sGetCoreConfig)) {
        memcpy(sGetCoreConfig, eventData->sCore_get_config.param_tlvs,
               eventData->sCore_get_config.tlv_size);
      }
      sUwaGetConfigEvent.notifyOne();
    }
    break;
  case UWA_DM_SESSION_INIT_RSP_EVT:
    JNI_TRACE_I("%s: UWA_DM_SESSION_INIT_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaSessionInitEvent);
      if (eventData->status == UWA_STATUS_OK) {
        sSessionInitStatus = true;
        JNI_TRACE_I("%s: UWA_DM_SESSION_INIT_RSP_EVT Success", fn);
      } else {
        JNI_TRACE_E("%s: UWA_DM_SESSION_INIT_RSP_EVT failed", fn);
      }
      sUwaSessionInitEvent.notifyOne();
    }
    break;
  case UWA_DM_SESSION_DEINIT_RSP_EVT:
    JNI_TRACE_I("%s: UWA_DM_SESSION_DEINIT_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaSessionDeInitEvent);
      if (eventData->status == UWA_STATUS_OK) {
        sSessionDeInitStatus = true;
        JNI_TRACE_I("%s: UWA_DM_SESSION_DEINIT_RSP_EVT Success", fn);
      } else {
        JNI_TRACE_E("%s: UWA_DM_SESSION_DEINIT_RSP_EVT failed", fn);
      }
      sUwaSessionDeInitEvent.notifyOne();
    }
    break;
  case UWA_DM_SESSION_STATUS_NTF_EVT:
    JNI_TRACE_I("%s: UWA_DM_SESSION_STATUS_NTF_EVT", fn);
    {
      unsigned int session_id = eventData->sSessionStatus.session_id;

      if (UWB_SESSION_DEINITIALIZED == eventData->sSessionStatus.state) {
        std::unique_lock<std::mutex> lock(sSessionMutex);
        auto it = sAveragedRangingData.find(session_id);
        if (it != sAveragedRangingData.end()) {
          sAveragedRangingData.erase(session_id);
          JNI_TRACE_E("%s: deinit: Averaging Disabled for Session %d", fn,
                      session_id);
        }
      }
      uwbEventManager.onSessionStatusNotificationReceived(
          eventData->sSessionStatus.session_id, eventData->sSessionStatus.state,
          eventData->sSessionStatus.reason_code);
    }
    break;
  case UWA_DM_SESSION_SET_CONFIG_RSP_EVT: // result of UWA_SetAppConfig
    JNI_TRACE_I("%s: UWA_DM_SESSION_SET_CONFIG_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaSetAppConfigEvent);
      sSetAppConfigRespStatus = true;
      sSetAppConfigStatus = eventData->status;
      sSetAppConfigLen = eventData->sApp_set_config.tlv_size;
      sNoOfAppConfigIds = eventData->sApp_set_config.num_param_id;
      if (eventData->sApp_set_config.tlv_size > 0) {
        memcpy(sSetAppConfig, eventData->sApp_set_config.param_ids,
               eventData->sApp_set_config.tlv_size);
      }
      sUwaSetAppConfigEvent.notifyOne();
    }
    break;
  case UWA_DM_SESSION_GET_CONFIG_RSP_EVT: /* Result of UWA_GetAppConfig */
    JNI_TRACE_I("%s: UWA_DM_SESSION_GET_CONFIG_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaGetAppConfigEvent);
      sGetAppConfigRespStatus = true;
      sGetAppConfigStatus = eventData->status;
      sGetAppConfigLen = eventData->sApp_get_config.tlv_size;
      sNoOfAppConfigIds = eventData->sApp_get_config.no_of_ids;
      if (eventData->sApp_get_config.tlv_size > 0) {
        memcpy(sGetAppConfig, eventData->sApp_get_config.param_tlvs,
               eventData->sApp_get_config.tlv_size);
      }
      sUwaGetAppConfigEvent.notifyOne();
    }
    break;
  case UWA_DM_RANGE_START_RSP_EVT: /* result of range start command */
    JNI_TRACE_I("%s: UWA_DM_RANGE_START_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaRngStartEvent);
      if (eventData->status == UWA_STATUS_OK) {
        sRangeStartStatus = true;
        JNI_TRACE_I("%s: UWA_DM_RANGE_START_RSP_EVT Success", fn);
      } else {
        sRangeStartStatus = false;
        JNI_TRACE_E("%s: UWA_DM_RANGE_START_RSP_EVT failed", fn);
      }
      sUwaRngStartEvent.notifyOne();
    }
    break;
  case UWA_DM_RANGE_STOP_RSP_EVT: /* result of range stop command */
    JNI_TRACE_I("%s: UWA_DM_RANGE_STOP_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaRngStopEvent);
      if (eventData->status == UWA_STATUS_OK) {
        sRangeStopStatus = true;
        JNI_TRACE_I("%s: UWA_DM_RANGE_STOP_RSP_EVT Success", fn);
      } else {
        sRangeStopStatus = false;
        JNI_TRACE_E("%s: UWA_DM_RANGE_STOP_RSP_EVT failed", fn);
      }
      sUwaRngStopEvent.notifyOne();
    }
    break;
  case UWA_DM_GET_RANGE_COUNT_RSP_EVT:
    JNI_TRACE_I("%s: UWA_DM_GET_RANGE_COUNT_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaGetRangingCountEvent);
      if (eventData->status == UWA_STATUS_OK) {
        sRangingCount = eventData->sGet_range_cnt.count;
      } else {
        JNI_TRACE_E("%s: get range count Request is failed", fn);
        sRangingCount = 0;
      }
      sUwaGetRangingCountEvent.notifyOne();
    }
    break;
  case UWA_DM_RANGE_DATA_NTF_EVT:
    JNI_TRACE_I("%s: UWA_DM_RANGE_DATA_NTF_EVT", fn);
    { notifyRangeDataNotification(&eventData->sRange_data); }
    break;
  case UWA_DM_SESSION_GET_COUNT_RSP_EVT:
    JNI_TRACE_I("%s: UWA_DM_SESSION_GET_COUNT_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaGetSessionCountEvent);
      if (eventData->status == UWA_STATUS_OK) {
        sSessionCount = eventData->sGet_session_cnt.count;
      } else {
        JNI_TRACE_E("%s: get session count Request is failed", fn);
        sSessionCount = -1;
      }
      sUwaGetSessionCountEvent.notifyOne();
    }
    break;

  case UWA_DM_SESSION_GET_STATE_RSP_EVT:
    JNI_TRACE_I("%s: UWA_DM_SESSION_GET_STATE_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaGetSessionStatusEvent);
      if (eventData->status == UWA_STATUS_OK) {
        sSessionState = eventData->sGet_session_state.session_state;
      } else {
        JNI_TRACE_E("%s: get session state Request is failed", fn);
        sSessionState = UWB_UNKNOWN_SESSION;
      }
      sUwaGetSessionStatusEvent.notifyOne();
    }
    break;

  case UWA_DM_SESSION_MC_LIST_UPDATE_RSP_EVT: /* result of session update
                                                 multicast list */
    JNI_TRACE_I("%s: UWA_DM_SESSION_MC_LIST_UPDATE_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaMulticastListUpdateEvent);
      if (eventData->status == UWA_STATUS_OK) {
        sMulticastListUpdateStatus = true;
        JNI_TRACE_I("%s: UWA_DM_SESSION_MC_LIST_UPDATE_RSP_EVT Success", fn);
      } else {
        JNI_TRACE_E("%s: UWA_DM_SESSION_MC_LIST_UPDATE_RSP_EVT failed", fn);
      }
      sUwaMulticastListUpdateEvent.notifyOne();
    }
    break;

  case UWA_DM_SESSION_MC_LIST_UPDATE_NTF_EVT:
    JNI_TRACE_I("%s: UWA_DM_SESSION_MC_LIST_UPDATE_NTF_EVT", fn);
    {
      uwbEventManager.onMulticastListUpdateNotificationReceived(
          &eventData->sMulticast_list_ntf);
    }
    break;

  case UWA_DM_SET_COUNTRY_CODE_RSP_EVT:
    JNI_TRACE_I("%s: UWA_DM_COUNTRY_CODE_UPDATE_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaSetCountryCodeEvent);
      if (eventData->status == UWA_STATUS_OK) {
        sSetCountryCodeStatus = true;
        JNI_TRACE_I("%s: UWA_DM_COUNTRY_CODE_UPDATE_RSP_EVT Success", fn);
      } else {
        JNI_TRACE_E("%s: UWA_DM_COUNTRY_CODE_UPDATE_RSP_EVT failed", fn);
      }
      sUwaSetCountryCodeEvent.notifyOne();
    }
    break;

  case UWA_DM_SEND_BLINK_DATA_RSP_EVT:
    JNI_TRACE_I("%s: UWA_DM_SEND_BLINK_DATA_RSP_EVT", fn);
    {
      SyncEventGuard guard(sUwaSendBlinkDataEvent);
      sSendBlinkDataStatus = eventData->status;
      sUwaSendBlinkDataEvent.notifyOne();
    }
    break;

  case UWA_DM_GET_CORE_DEVICE_CAP_RSP_EVT:
    JNI_TRACE_D("%s: UWA_DM_API_CORE_GET_DEVICE_CAPABILITY_EVT", fn);
    {
     SyncEventGuard guard(sUwaGetDeviceCapsEvent);
     sGetDeviceCapsRespStatus = true;
     sDevCapInfoLen = 0;
     if (eventData->sGet_device_capability.status == UWA_STATUS_OK) {
        sDevCapInfoIds = eventData->sGet_device_capability.no_of_tlvs;
        sDevCapInfoLen = eventData->sGet_device_capability.tlv_buffer_len;
        if (eventData->sGet_device_capability.tlv_buffer_len > 0 && (eventData->sGet_device_capability.tlv_buffer_len <= UCI_MAX_PKT_SIZE)) {
            memcpy(sUwbDeviceCapability, eventData->sGet_device_capability.tlv_buffer, eventData->sGet_device_capability.tlv_buffer_len);
        }
     }
     sUwaGetDeviceCapsEvent.notifyOne();
     }
    break;
  case UWA_DM_SEND_BLINK_DATA_NTF_EVT:
    JNI_TRACE_I("%s: UWA_DM_SEND_BLINK_DATA_NTF_EVT", fn);
    {
      uwbEventManager.onBlinkDataTxNotificationReceived(
          eventData->sBlink_data_ntf.repetition_count_status);
    }
    break;
  case UWA_VENDOR_SPECIFIC_UCI_NTF_EVT:
     JNI_TRACE_I("%s: UWA_VENDOR_SPECIfIC_UCI_NTF_EVT", fn);
     {
      uint8_t mt, pbf, gid, oid, *ntf_data, *p_ntf_hdr;
      uint16_t len = eventData->sVendor_specific_ntf.len-UCI_MSG_HDR_SIZE;
      p_ntf_hdr = eventData->sVendor_specific_ntf.data;
      ntf_data = (uint8_t *) eventData->sVendor_specific_ntf.data + UCI_MSG_HDR_SIZE;
      UCI_MSG_PRS_HDR0(p_ntf_hdr, mt, pbf, gid);
      UCI_MSG_PRS_HDR1(p_ntf_hdr, oid);
      uwbEventManager.onVendorUciNotificationReceived(gid, oid,
       ntf_data, len);
     }
     break;
  case UWA_DM_CONFORMANCE_NTF_EVT:
    JNI_TRACE_I("%s: UWA_DM_CONFORMANCE_NTF_EVT", fn);
    {
      uwbEventManager.onRawUciNotificationReceived(eventData->sConformance_ntf.data,
      eventData->sConformance_ntf.length);
    }
    break;
  case UWA_DM_CORE_GEN_ERR_STATUS_EVT:
    JNI_TRACE_I("%s: UWA_DM_CORE_GEN_ERR_STATUS_EVT", fn);
    {
      uwbEventManager.onCoreGenericErrorNotificationReceived(
          eventData->sCore_gen_err_status.status);
    }
    break;

    //    case UWA_DM_UWBS_RESP_TIMEOUT_EVT:
    //      JNI_TRACE_I("%s: UWA_DM_UWBS_RESP_TIMEOUT_EVT", fn);
    //      {
    //        sErrNotify.notifyAll();
    //        sDeviceState = UWBS_STATUS_TIMEOUT;
    //        uwbEventManager.onDeviceStateNotificationReceived(sDeviceState);
    //      }
    //      break;
  default:
    JNI_TRACE_I("%s: unhandled event", fn);
    break;
  }
}

/*******************************************************************************
**
** Function:        CommandResponse_Cb
**
** Description:     Receive response from the stack for raw command sent from
*                   jni.
**
**                  event:  event ID.
**                  paramLength: length of the response
**                  pResponseBuffer: pointer to data
**
** Returns:         None
**
*******************************************************************************/
static void CommandResponse_Cb(uint8_t event, uint16_t paramLength,
                               uint8_t *pResponseBuffer) {
  JNI_TRACE_I("%s: Entry", __func__);

  if ((paramLength > UCI_RESPONSE_STATUS_OFFSET) && (pResponseBuffer != NULL)) {
    JNI_TRACE_I("CommandResponse_Cb Received length data = 0x%x status = 0x%x",
                paramLength, pResponseBuffer[UCI_RESPONSE_STATUS_OFFSET]);
   sSendRawResLen = paramLength-UCI_MSG_HDR_SIZE;
   memcpy(sSendRawResData, pResponseBuffer+UCI_MSG_HDR_SIZE, sSendRawResLen);
  } else {
    JNI_TRACE_E("%s:CommandResponse_Cb responseBuffer is NULL or Length < "
                "UCI_RESPONSE_STATUS_OFFSET",
                __func__);
  }
  SyncEventGuard guard(sUwaSendRawUciEvt);
  sUwaSendRawUciEvt.notifyOne();

  JNI_TRACE_I("%s: Exit", __func__);
}

/*******************************************************************************
**
** Function:        setAppConfiguration
**
** Description:     Set the session specific App Config
**
** Params:          session_id: Session Id of the required App Config
**                  noOfParams: Number of Params need to configure
**                  paramLen: Total Params Lentgh
**                  appConfigParams: AppConfigs List in TLV format
**
** Returns:         If Success UWA_STATUS_OK  else UWA_STATUS_FAILED
**
*******************************************************************************/
static tUWA_STATUS setAppConfiguration(uint32_t session_id, uint8_t noOfParams,
                                       uint8_t paramLen,
                                       uint8_t appConfigParams[]) {
  static const char fn[] = "setAppConfiguration";
  UNUSED(fn);
  tUWA_STATUS status;
  sSetAppConfigRespStatus = false;
  SyncEventGuard guard(sUwaSetAppConfigEvent);
  status = UWA_SetAppConfig(session_id, noOfParams, paramLen, appConfigParams);
  if (status == UWA_STATUS_OK) {
    sUwaSetAppConfigEvent.wait(UWB_CMD_TIMEOUT);
    JNI_TRACE_I("%s: Success UWA_SetAppConfig Command", fn);
  } else {
    JNI_TRACE_E("%s: Failed UWA_SetAppConfig Command", fn);
    return UWA_STATUS_FAILED;
  }
  return (sSetAppConfigRespStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        sendRawUci
**
** Description:     Invoked this API to send raw uci cmds
**
** Params:          rawCmd: Ponter to the raw uci command
**                  cmdLen: Length of the command
**                  rsoData: Pointer to the response for sendRawUci cmd
**                  rspLen: Length of response
**
** Returns:         If Success UWA_STATUS_OK  else UWA_STATUS_FAILED
**
*******************************************************************************/
static tUWA_STATUS sendRawUci(uint8_t gid, uint8_t oid, uint8_t *rawCmd, uint16_t cmdLen) {
  tUWA_STATUS status = UWA_STATUS_FAILED;
  uint8_t* pp;
  uint8_t* p;
  uint16_t len = cmdLen+UCI_MSG_HDR_SIZE;
  p = pp =  (uint8_t *) phUwb_GKI_getbuf(len);

  if (pp != NULL) {
     SyncEventGuard guard(sUwaSendRawUciEvt);
     UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, gid);
     UCI_MSG_BLD_HDR1(pp, oid);
     UINT8_TO_STREAM(pp, 0x00);
     if (cmdLen ==1 && rawCmd[0] == 0  ) {
       UINT8_TO_STREAM(pp, 0);
       ARRAY_TO_STREAM(pp, rawCmd, 1);
     } else {
       UINT8_TO_STREAM(pp,cmdLen);
       ARRAY_TO_STREAM(pp, rawCmd, cmdLen);
     }

     status = UWA_SendRawCommand(len, p, CommandResponse_Cb);
     phUwb_GKI_freebuf(p);

     if (status == UWA_STATUS_OK) {
       JNI_TRACE_I("%s: Success UWA_SendRawCommand", __func__);
        sUwaSendRawUciEvt.wait(UWB_CMD_TIMEOUT);
     } else {
       JNI_TRACE_E("%s: Failed UWA_SendRawCommand", __func__);
       return status;
     }
  }

  JNI_TRACE_I("%s: Exit", __func__);
  return status;
}

/*******************************************************************************
**
** Function:        SetCoreDeviceConfigurations
**
** Description:     Set the Core Device Config
**
**
** Returns:         If Success UWA_STATUS_OK  else UWA_STATUS_FAILED
**
*******************************************************************************/
static tUWA_STATUS SetCoreDeviceConfigurations() {
  uint8_t coreConfigsCount = 1;
  static const char fn[] = "SetCoreDeviceConfigurations";
  UNUSED(fn);
  tUWA_STATUS status;
  uint8_t configParam[] = {0x00, 0x00, 0x00};
  uint16_t config = 0;
  JNI_TRACE_I("%s: Enter ", fn);

  config = UwbConfig::getUnsigned(NAME_UWB_LOW_POWER_MODE, 0x00);
  JNI_TRACE_I("%s: NAME_UWB_LOW_POWER_MODE value %d ", fn, (uint8_t)config);

  configParam[0] = (uint8_t)config;

  {
    SyncEventGuard guard(sUwaSetConfigEvent);
    status = UWA_SetCoreConfig(UCI_PARAM_ID_LOW_POWER_MODE, coreConfigsCount,
                               &configParam[0]);
    if (status == UWA_STATUS_OK) {
      sUwaSetConfigEvent.wait(UWB_CMD_TIMEOUT);
      JNI_TRACE_I("%s: low power mode config is success", fn);
    } else {
      JNI_TRACE_E("%s: low power mode config is failed", fn);
      return UWA_STATUS_FAILED;
    }
  }

  JNI_TRACE_I("%s: Exit ", fn);
  return status;
}

/*******************************************************************************
**
** Function:        clearAllSessionContext
**
** Description:     This API is invoked before Init and during DeInit to clear
**                  All the Session specific context.
**
** Returns:         Nothing
**
*******************************************************************************/
void clearAllSessionContext() {
  {
    std::unique_lock<std::mutex> lock(sSessionMutex);
    sAveragedRangingData.clear();
  }
  clearRfTestContext();
}

/*******************************************************************************
**
** Function:        UwbDeviceReset
**
** Description:     Send Device Reset Command.
**
** Params:          resetConfig: Manufacturer/Vendor Specific Reset Data

** Returns:         If Success UWA_STATUS_OK  else UWA_STATUS_FAILED
**
*******************************************************************************/
bool UwbDeviceReset(uint8_t resetConfig) {
  static const char fn[] = "UwbDeviceReset";
  UNUSED(fn);
  tUWA_STATUS status;
  JNI_TRACE_I("%s: Enter", fn);

  sIsDeviceResetDone = false;
  {
    SyncEventGuard guard(sUwaDeviceResetEvent);
    status = UWA_SendDeviceReset((uint8_t)resetConfig);
    if (status == UWA_STATUS_OK)
      sUwaDeviceResetEvent.wait(UWB_CMD_TIMEOUT); /* wait for callback */
  }
  if (status == UWA_STATUS_OK) {
    JNI_TRACE_E("%s: Success UWA_SendDeviceReset", fn);
    if (sIsDeviceResetDone) {
      SyncEventGuard guard(sUwadeviceNtfEvent);
      sUwadeviceNtfEvent.wait(UWB_CMD_TIMEOUT);
      switch (sDeviceState) {
      case UWBS_STATUS_READY: {
        clearAllSessionContext();
        JNI_TRACE_I("%s: Device Reset is success %d", fn, sDeviceState);
      } break;
      default: {
        JNI_TRACE_E("%s: Device state is = %d", fn, sDeviceState);
      } break;
      }
    }
  } else {
    JNI_TRACE_E("%s: Failed UWA_SendDeviceReset", fn);
  }
  JNI_TRACE_I("%s: Exit", fn);
  return sIsDeviceResetDone ? TRUE : FALSE;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_doInitialize
**
** Description:     Turn on UWB. initialize the GKI module and HAL module for
*UWB device.
**
** Params:          env: JVM environment.
**                  o: Java object.
**
** Returns:         True if UWB device initialization is success.
**
*******************************************************************************/
jboolean uwbNativeManager_doInitialize(JNIEnv *env, jobject o) {
  static const char fn[] = "uwbNativeManager_doInitialize";
  UNUSED(fn);
  tUWA_STATUS status;
  uint8_t resetConfig = 0;
  JNI_TRACE_I("%s: enter", fn);

  if (gIsUwaEnabled) {
    JNI_TRACE_I("%s: Already Initialized", fn);
    UwbDeviceReset(resetConfig);
    return JNI_TRUE;
  }

  sDeviceState = UWBS_STATUS_ERROR;
  UwbAdaptation &theInstance = UwbAdaptation::GetInstance();
  theInstance.Initialize(); // start GKI, UCI task, UWB task
  tHAL_UWB_ENTRY *halFuncEntries = theInstance.GetHalEntryFuncs();
  UWA_Init(halFuncEntries);
  clearAllSessionContext();
  {
    SyncEventGuard guard(sUwaEnableEvent);
    status = UWA_Enable(uwaDeviceManagementCallback,
                        uwaRfTestDeviceManagementCallback);
    if (status == UWA_STATUS_OK)
      sUwaEnableEvent.wait(UWB_CMD_TIMEOUT);
  }
  if (status == UWA_STATUS_OK) {
    if (!gIsUwaEnabled) {
      JNI_TRACE_E("%s: UWB Enable failed", fn);
      goto error;
    }
    status = theInstance.CoreInitialization();
    JNI_TRACE_I("%s: CoreInitialization status: %d", fn, status);

    if (status == UWA_STATUS_OK) {
#if 1 // WA waiting binding status NTF/ SE comm error NTF
#endif
      {
        /* Get Device Info */
        {
          SyncEventGuard guard(sUwaGetDeviceInfoEvent);
          status = UWA_GetDeviceInfo();

          if (status == UWA_STATUS_OK) {
            sUwaGetDeviceInfoEvent.wait();
            JNI_TRACE_I("UCI Version : %x.%x",
                        (sUwbDeviceInfo.uciVersion & 0X00FF),
                        (sUwbDeviceInfo.uciVersion >> 8));
          }
        }

        if (status == UWA_STATUS_OK) {
          gIsUwaEnabled = true;
          status = SetCoreDeviceConfigurations();
          if (status == UWA_STATUS_OK) {
            JNI_TRACE_I("%s: SetCoreDeviceConfigurations is SUCCESS %d", fn,
                        status);
          } else {
            JNI_TRACE_I("%s: SetCoreDeviceConfigurations is Failed %d", fn,
                        status);
            goto error;
          }
          goto end;
        }
      }
    }
  }
error:
  JNI_TRACE_E("%s: device status is failed %d", fn, sDeviceState);
  gIsUwaEnabled = false;
  status = UWA_Disable(false); /* gracefull exit */
  if (status == UWA_STATUS_OK) {
    JNI_TRACE_I("%s: UWA_Disable(false) SUCCESS %d", fn, status);
  } else {
    JNI_TRACE_E("%s: UWA_Disable(false) is failed %d", fn, status);
  }
  theInstance.Finalize(false); // disable GKI, UCI task, UWB task
end:
  if (gIsUwaEnabled) {
    sDeviceState = UWBS_STATUS_READY;
  }
  JNI_TRACE_I("%s: exit", fn);
  return gIsUwaEnabled ? JNI_TRUE : JNI_FALSE;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_doDeinitialize
**
** Description:     Turn off UWB. Deinitilize the GKI and HAL module, power
**                  of the UWB device.
** Params:          env: JVM environment.
**                  o: Java object.
**
** Returns:         True if UWB device De-initialization is success.
**
*******************************************************************************/
jboolean uwbNativeManager_doDeinitialize(JNIEnv *env, jobject obj) {
  static const char fn[] = "uwbNativeManager_doDeinitialize";
  UNUSED(fn);
  tUWA_STATUS status;
  JNI_TRACE_I("%s: Enter", fn);
  UwbAdaptation &theInstance = UwbAdaptation::GetInstance();

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is already De-initialized", fn);
    return JNI_TRUE;
  }

  SyncEventGuard guard(sUwaDisableEvent);
  status = UWA_Disable(true); /* gracefull exit */
  if (status == UWA_STATUS_OK) {
    JNI_TRACE_I("%s: wait for de-init completion:", fn);
    sUwaDisableEvent.wait();
  } else {
    JNI_TRACE_E("%s: De-Init is failed:", fn);
  }
  clearAllSessionContext();
  gIsUwaEnabled = false;
  theInstance.Finalize(true); // disable GKI, UCI task, UWB task
  JNI_TRACE_I("%s: Exit", fn);
  return JNI_TRUE;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_getDeviceInfo
**
** Description:     retrieve the UWB device information etc.
** Params:          env: JVM environment.
**                  o: Java object.
**
** Returns:         device info class object or NULL.
**
*******************************************************************************/
jobject uwbNativeManager_getDeviceInfo(JNIEnv *env, jobject obj) {
  static const char fn[] = "uwbNativeManager_getDeviceInfo";
  UNUSED(fn);
  JNI_TRACE_I("%s: Enter", fn);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return NULL;
  }

  // TODO need to change this implemenatation based on service.
  const char *DEVICE_DATA_CLASS_NAME = "com/android/server/uwb/UwbDeviceData";
  jclass deviceDataClass = env->FindClass(DEVICE_DATA_CLASS_NAME);
  jmethodID constructor =
      env->GetMethodID(deviceDataClass, "<init>",
                       "(IIII)V"); // TODO to be updated based on service
  if (constructor == JNI_NULL) {
    JNI_TRACE_E("%s: jni cannot find the method deviceInfoClass", fn);
    return NULL;
  }

  jint uciVersion = sUwbDeviceInfo.uciVersion;
  jint uciTestVersion = sUwbDeviceInfo.uciTestVersion;
  jint macVersion = sUwbDeviceInfo.macVersion;
  jint phyVersion = sUwbDeviceInfo.phyVersion;

  JNI_TRACE_I("%s: Exit", fn);

  return env->NewObject(deviceDataClass, constructor, uciVersion, macVersion,
                        phyVersion, uciTestVersion);
}

/*******************************************************************************
**
** Function:        uwbNativeManager_getSpecificationInfo
**
** Description:     retrieve the UWB device specific information etc.
** Params:          env: JVM environment.
**                  o: Java object.
**
** Returns:         device info class object or NULL.
**
*******************************************************************************/
jobject uwbNativeManager_getSpecificationInfo(JNIEnv *env, jobject obj) {
  static const char fn[] = "uwbNativeManager_getSpecificationInfo";
  UNUSED(fn);
  JNI_TRACE_I("%s: Enter", fn);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return NULL;
  }

  const char *DEVICE_DATA_CLASS_NAME =
      "com/android/server/uwb/info/UwbSpecificationInfo";
  jclass deviceDataClass = env->FindClass(DEVICE_DATA_CLASS_NAME);
  jmethodID constructor =
      env->GetMethodID(deviceDataClass, "<init>", "(IIIIIIIIIIIIIIII)V");
  if (constructor == JNI_NULL) {
    JNI_TRACE_E("%s: jni cannot find the method deviceInfoClass", fn);
    return NULL;
  }

  jint uciMajor = (sUwbDeviceInfo.uciVersion & MSB_BITMASK);
  jint uciMaintenance = (sUwbDeviceInfo.uciVersion >> 8) & 0x0F;
  jint uciMinor = (sUwbDeviceInfo.uciVersion >> 12) & 0x0F;
  jint macMajor = (sUwbDeviceInfo.macVersion & MSB_BITMASK);
  jint macMaintenance = (sUwbDeviceInfo.macVersion >> 8) & 0x0F;
  jint macMinor = (sUwbDeviceInfo.macVersion >> 12) & 0x0F;
  jint phyMajor = (sUwbDeviceInfo.phyVersion & MSB_BITMASK);
  jint phyMaintenance = (sUwbDeviceInfo.phyVersion >> 8) & 0x0F;
  jint phyMinor = (sUwbDeviceInfo.phyVersion >> 12) & 0x0F;
  jint uciTestMajor = (sUwbDeviceInfo.uciTestVersion) & MSB_BITMASK;
  jint uciTestMaintenance = (sUwbDeviceInfo.uciTestVersion >> 8) & 0x0F;
  jint uciTestMinor = (sUwbDeviceInfo.uciTestVersion >> 12) & 0x0F;

  JNI_TRACE_I("%s: Exit", fn);

  return env->NewObject(deviceDataClass, constructor, uciMajor, uciMaintenance,
                        uciMinor, macMajor, macMaintenance, macMinor, phyMajor,
                        phyMaintenance, phyMinor, uciTestMajor,
                        uciTestMaintenance, uciTestMinor,
                        1 /* firaMajorVersion */, 0 /* firaMinorVersion */,
                        1 /* cccMajorVersion */, 0 /* cccMinorVersion*/);
}

/*******************************************************************************
**
** Function:        uwbNativeManager_getUwbDeviceState
**
** Description:     Retrieve the UWB device state..
**
** Params :         env: JVM environment.
**                  o: Java object.
**
** Returns:         device state.
**
*******************************************************************************/
jint uwbNativeManager_getUwbDeviceState(JNIEnv *env, jobject obj) {
  static const char fn[] = "uwbNativeManager_getUwbDeviceState";
  UNUSED(fn);
  eUWBS_DEVICE_STATUS_t sDeviceState = UWBS_STATUS_ERROR;
  JNI_TRACE_I("%s: Enter", fn);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return sDeviceState;
  }

  tUWA_PMID configParam[] = {UCI_PARAM_ID_DEVICE_STATE};
  SyncEventGuard guard(sUwaGetConfigEvent);
  tUWA_STATUS status = UWA_GetCoreConfig(sizeof(configParam), configParam);
  if (status == UWA_STATUS_OK) {
    sUwaGetConfigEvent.wait(UWB_CMD_TIMEOUT);
    if (sGetCoreConfigLen > 0) {
      if (sGetCoreConfig[0] == UCI_PARAM_ID_DEVICE_STATE) {
        sDeviceState = (eUWBS_DEVICE_STATUS_t)sGetCoreConfig[2];
      }
    }
  }
  JNI_TRACE_I("%s: Exit", fn);
  return sDeviceState;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_deviceReset
**
** Description:     Send Device Reset Command.
**
** Params:          env: JVM environment.
**                  obj: Java object.
**                  resetConfig: Manufacturer/Vendor Specific Reset Data
**
** Returns:         If Success UWA_STATUS_OK  else UWA_STATUS_FAILED
**
*******************************************************************************/
jbyte uwbNativeManager_deviceReset(JNIEnv *env, jobject obj,
                                   jbyte resetConfig) {
  static const char fn[] = "uwbNativeManager_deviceReset";
  UNUSED(fn);
  bool status;
  JNI_TRACE_I("%s: Enter", fn);

  // WA: commented reset functionality as this wiill trigger ESE communication
  // and Helios will send binding status NTF again
  // if Helos is turned off without reading response from ESE, then this makes
  // ESE unresponsive sitiation
  // Sending reset command as part of MW enable every time to reset both Helios
  // and SUS applet from ESE
  status = true; // true always
#if 0
  if(!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return UWA_STATUS_FAILED;
  }

  status = UwbDeviceReset((uint8_t)resetConfig);
#endif
  JNI_TRACE_I("%s: Exit", fn);
  return status ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_sessionInit
**
** Description:     Initialize the session for the particular activity.
**
** Params:          env: JVM environment.
**                  o: Java object.
**
** Returns:         If Success UWA_STATUS_OK  else UWA_STATUS_FAILED
**
*******************************************************************************/
jbyte uwbNativeManager_sessionInit(JNIEnv *env, jobject o, jint sessionId,
                                   jbyte sessionType) {
  static const char fn[] = "uwbNativeManager_sessionInit";
  UNUSED(fn);
  tUWA_STATUS status = UWA_STATUS_FAILED;
  JNI_TRACE_I("%s: Enter", fn);
  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return status;
  }

  sSessionInitStatus = false;
  SyncEventGuard guard(sUwaSessionInitEvent);
  status = UWA_SendSessionInit(sessionId, sessionType);
  if (UWA_STATUS_OK == status) {
    sUwaSessionInitEvent.wait(UWB_CMD_TIMEOUT);
  } else {
    JNI_TRACE_E("%s: Session Init command is  failed", fn);
  }

  JNI_TRACE_I("%s: Exit", fn);
  return (sSessionInitStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_sessionDeInit
**
** Description:     This API is invoked from the application to DeInitialize
**                  Session Specific context.
**
** Params:          env: JVM environment.
**                  o: Java object.
**
** Returns:         If Success UWA_STATUS_OK  else UWA_STATUS_FAILED
**
*******************************************************************************/
jbyte uwbNativeManager_sessionDeInit(JNIEnv *env, jobject o, jint sessionId) {
  static const char fn[] = "uwbNativeManager_sessionDeInit";
  UNUSED(fn);
  tUWA_STATUS status = UWA_STATUS_FAILED;
  JNI_TRACE_I("%s: Enter", fn);
  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return status;
  }

  sSessionDeInitStatus = false;
  SyncEventGuard guard(sUwaSessionDeInitEvent);
  status = UWA_SendSessionDeInit(sessionId);
  if (UWA_STATUS_OK == status) {
    sUwaSessionDeInitEvent.wait(UWB_CMD_TIMEOUT);
  } else {
    JNI_TRACE_E("%s: Session DeInit command is  failed", fn);
  }
  JNI_TRACE_I("%s: Exit", fn);
  return (sSessionDeInitStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_setAppConfigurations()
**
** Description:     Invoked this API to set the session specific Application
**                  Configuration.
**
** Params:          env: JVM environment.
**                  o: Java object.
**                  sessionId: All APP configurations belonging to this Session
*ID
**                  noOfParams : The number of APP Configuration fields to
*follow
**                  appConfigLen : Length of AppConfigData
**                  AppConfig : App Configurations for session
**
** Returns:         Returns byte array
**
*******************************************************************************/
jobject uwbNativeManager_setAppConfigurations(JNIEnv *env, jobject o,
                                                 jint sessionId,
                                                 jint noOfParams,
                                                 jint appConfigLen,
                                                 jbyteArray AppConfig) {
  static const char fn[] = "uwbNativeManager_setAppConfigurations";
  UNUSED(fn);
  uint8_t *appConfigData = NULL;
  tUWA_STATUS status = UWA_STATUS_FAILED;
  JNI_TRACE_I("%s: Enter", fn);
  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return NULL;
  }

  appConfigData = (uint8_t *)malloc(sizeof(uint8_t) * appConfigLen);
  if (appConfigData != NULL) {
      memset(appConfigData, 0, (sizeof(uint8_t) * appConfigLen));
      env->GetByteArrayRegion(AppConfig, 0, appConfigLen, (jbyte *)appConfigData);
      JNI_TRACE_I("%d: appConfigLen", appConfigLen);
      status =
          setAppConfiguration(sessionId, noOfParams, appConfigLen, appConfigData);
      free(appConfigData);
      if (sSetAppConfigRespStatus) {
          const char *UWB_CONFIG_STATUS_DATA =
                  "com/android/server/uwb/data/UwbConfigStatusData";
          jclass configStatusDataClass = env->FindClass(UWB_CONFIG_STATUS_DATA);
          jmethodID constructor =
              env->GetMethodID(configStatusDataClass, "<init>", "(II[B)V");
          if (constructor == JNI_NULL) {
            JNI_TRACE_E("%s: jni cannot find the method for UwbTlvDATA", fn);
            return NULL;
          }
          jbyteArray appConfigArray =
              env->NewByteArray(sSetAppConfigLen);
          env->SetByteArrayRegion(appConfigArray, 0, sSetAppConfigLen,
                                  (jbyte *)&sSetAppConfig[0]);
          return env->NewObject(configStatusDataClass, constructor, sSetAppConfigStatus, sNoOfAppConfigIds, appConfigArray);
      } else {
          JNI_TRACE_E("%s: Failed setAppConfigurations, Status = %d", fn,
                    sSetAppConfigRespStatus);
      }
  } else {
    JNI_TRACE_E("%s: Unable to Allocate Memory", fn);
  }
  JNI_TRACE_I("%s: Exit", fn);
  return NULL;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_sendRawUci()
**
** Description:     Invoked this API to send raw uci cmds
**
** Params:          env: JVM environment.
**                  o: Java object.
**                  rawUci: Uci data to send to controller
**                  cmdLen: uci data lentgh
**
** Returns:         Returns byte array for raw uci rsp
**
*******************************************************************************/
jobject uwbNativeManager_sendRawUci(JNIEnv *env, jobject o,
                                       jint gid, jint oid,
                                       jbyteArray rawUci) {
  static const char fn[] = "uwbNativeManager_sendRawUci";
  UNUSED(fn);
  JNI_TRACE_I("%s: enter; ", fn);
  uint8_t *cmd = NULL;
  tUWA_STATUS status = UWA_STATUS_FAILED;
  jint cmdLen = env->GetArrayLength(rawUci);
  if (cmdLen > UCI_MAX_PAYLOAD_SIZE) {
    JNI_TRACE_E("%s: CmdLen %d is beyond max allowed range %d", fn, cmdLen,
                UCI_MAX_PAYLOAD_SIZE);
    return NULL;
  }

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return NULL;
  }

  cmd = (uint8_t *)malloc(sizeof(uint8_t) * cmdLen);
  if (cmd == NULL) {
    JNI_TRACE_E("%s: malloc failure for raw cmd", __func__);
    return NULL;
  }
  memset(cmd, 0, (sizeof(uint8_t) * cmdLen));
  env->GetByteArrayRegion(rawUci, 0, cmdLen, (jbyte *)cmd);

  status = sendRawUci(gid, oid, cmd, cmdLen);
  free(cmd);

  const char *UWB_VENDOR_RES_DATA =
          "com/android/server/uwb/data/UwbVendorUciResponse";
  jclass resDataClass = env->FindClass(UWB_VENDOR_RES_DATA);
  jmethodID constructor =
      env->GetMethodID(resDataClass, "<init>", "(BII[B)V");
  if (constructor == JNI_NULL) {
    JNI_TRACE_E("%s: jni cannot find the method for UwbTlvDATA", fn);
    return NULL;
  }
  JNI_TRACE_I("%s: exit sendRawUCi= 0x%x", __func__, status);
  if (status == UWA_STATUS_OK) {
     jbyteArray rawResArray =
         env->NewByteArray(sSendRawResLen);
     env->SetByteArrayRegion(rawResArray, 0, sSendRawResLen,
                             (jbyte *)&sSendRawResData[0]);
     return env->NewObject(resDataClass, constructor, status, gid, oid, rawResArray);
  } else {
     return env->NewObject(resDataClass, constructor, status, gid, oid, NULL);
  }

}

/*******************************************************************************
**
** Function:        uwbNativeManager_getAppConfigurations
**
** Description:     retrieve the session specific App Configs
**
** Params:          env: JVM environment.
**                  o: Java object.
**                  session id : Session Id for the given set of App params
**                  noOfParams: Number of Params
**                  appConfigLen: Total App config Lentgh
**                  AppConfig: App Config List to retrieve
**
** Returns:         Returns byte array
**
*******************************************************************************/
jobject uwbNativeManager_getAppConfigurations(JNIEnv *env, jobject o,
                                                 jint sessionId,
                                                 jint noOfParams,
                                                 jint appConfigLen,
                                                 jbyteArray AppConfig) {
  static const char fn[] = "uwbNativeManager_getAppConfigurations";
  UNUSED(fn);
  tUWA_STATUS status;
  uint8_t *appConfigData = NULL;
  JNI_TRACE_I("%s: Enter", fn);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return NULL;
  }

  sGetAppConfigRespStatus = false;
  appConfigData = (uint8_t *)malloc(sizeof(uint8_t) * appConfigLen);
  if (appConfigData != NULL) {
      memset(appConfigData, 0, (sizeof(uint8_t) * appConfigLen));
      env->GetByteArrayRegion(AppConfig, 0, appConfigLen, (jbyte *)appConfigData);
      SyncEventGuard guard(sUwaGetAppConfigEvent);
      status =
          UWA_GetAppConfig(sessionId, noOfParams, appConfigLen, appConfigData);
      free(appConfigData);
      if (status == UWA_STATUS_OK) {
          sUwaGetAppConfigEvent.wait(UWB_CMD_TIMEOUT);
          if (sGetAppConfigRespStatus) {
              const char *UWB_TLV_DATA =
                 "com/android/server/uwb/data/UwbTlvData";
              jclass tlvDataClass = env->FindClass(UWB_TLV_DATA);
              jmethodID constructor =
                env->GetMethodID(tlvDataClass, "<init>", "(II[B)V");
               if (constructor == JNI_NULL) {
                   JNI_TRACE_E("%s: jni cannot find the method for UwbTlvDATA", fn);
                   return NULL;
               }
               jbyteArray appConfigArray =
                   env->NewByteArray(sGetAppConfigLen);
               env->SetByteArrayRegion(appConfigArray, 0, sGetAppConfigLen,
                                  (jbyte *)&sGetAppConfig[0]);
               return env->NewObject(tlvDataClass, constructor, sGetAppConfigStatus, sNoOfAppConfigIds, appConfigArray);
          } else {
               JNI_TRACE_E("%s: Failed getAppConfigurations, Status = %d", fn,
                      sGetAppConfigRespStatus);
          }
      } else {
        JNI_TRACE_E("%s: Failed UWA_GetAppConfig", fn);
      }
  } else {
      JNI_TRACE_E("%s: Unable to Allocate Memory", fn);
  }
  JNI_TRACE_I("%s: Exit", fn);
  return NULL;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_startRanging
**
** Description:     start the ranging session with required configs and notify
**                  the peer device information.
**
** Params:          env: JVM environment.
**                  o: Java object.
**                  sessionId :  Session ID for which ranging shall start
**
** Returns:         If Success UWA_STATUS_OK  else UWA_STATUS_FAILED
**
*******************************************************************************/
jbyte uwbNativeManager_startRanging(JNIEnv *env, jobject obj, jint sessionId) {
  static const char fn[] = "uwbNativeManager_startRanging";
  UNUSED(fn);
  tUWA_STATUS status = UWA_STATUS_FAILED;
  JNI_TRACE_I("%s: enter", fn);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not enabled", fn);
    return status;
  }

  sRangeStartStatus = false;
  SyncEventGuard guard(sUwaRngStartEvent);
  status = UWA_StartRangingSession(sessionId);
  if (status == UWA_STATUS_OK) {
    sUwaRngStartEvent.wait(UWB_CMD_TIMEOUT);
  }
  JNI_TRACE_I("%s: exit", fn);
  return (sRangeStartStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_stopRanging
**
** Description:     stop the ranging session.
**
** Params:          env: JVM environment.
**                  o: Java object.
**                  sessionId :  Session ID for which ranging shall start
**
** Returns:         UWA_STATUS_OK if ranging session stop is success.
**
*******************************************************************************/
jbyte uwbNativeManager_stopRanging(JNIEnv *env, jobject obj, jint sessionId) {
  static const char fn[] = "uwbNativeManager_stopRanging";
  UNUSED(fn);
  tUWA_STATUS status = UWA_STATUS_FAILED;
  JNI_TRACE_I("%s: enter", fn);
  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not enabled", fn);
    return status;
  }

  sRangeStopStatus = false;
  SyncEventGuard guard(sUwaRngStopEvent);
  status = UWA_StopRangingSession(sessionId);
  if (status == UWA_STATUS_OK) {
    sUwaRngStopEvent.wait(UWB_CMD_TIMEOUT);
  } else {
    JNI_TRACE_E("%s: Stop ranging is failed  error:%x:", fn, status);
  }
  JNI_TRACE_I("%s: exit", fn);
  return (sRangeStopStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_getSessionCount
**
** Description:     Get session count.
**
** Params:          env: JVM environment.
**                  o: Java object.
**
** Returns:         session count on success
**
*******************************************************************************/
jbyte uwbNativeManager_getSessionCount(JNIEnv *env, jobject obj) {
  static const char fn[] = "uwbNativeManager_getSessionCount";
  UNUSED(fn);
  tUWA_STATUS status;
  sSessionCount = -1;
  JNI_TRACE_I("%s: Enter", fn);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return sSessionCount;
  }

  SyncEventGuard guard(sUwaGetSessionCountEvent);
  status = UWA_GetSessionCount();
  if (UWA_STATUS_OK == status) {
    sUwaGetSessionCountEvent.wait(UWB_CMD_TIMEOUT);
  } else {
    JNI_TRACE_E("%s: get session count command is  failed", fn);
  }
  JNI_TRACE_I("%s: Exit", fn);
  return sSessionCount;
}

jint uwbNativeManager_getMaxSessionNumber(JNIEnv *env, jobject obj) {
  static const char fn[] = "uwbNativeManager_getMaxSessionNumber";
  UNUSED(fn);

  return 5;
}

jbyte uwbNativeManager_resetDevice(JNIEnv *env, jbyte resetConfig) {
  static const char fn[] = "uwbNativeManager_resetDevice";
  UNUSED(fn);

  return UWA_STATUS_OK;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_getSessionState
**
** Description:     get the current session status for the given session id
**
** Params:          env: JVM environment.
**                  o: Java object.
**                  sessionId :  Session ID for which to get the session status
**
** Returns:         current session status if UWb_STATUS_OK else returns
**                  UWA_STATUS_FAILED.
**
*******************************************************************************/
jbyte uwbNativeManager_getSessionState(JNIEnv *env, jobject obj,
                                       jint sessionId) {
  static const char fn[] = "uwbNativeManager_getSessionState";
  UNUSED(fn);
  tUWA_STATUS status;
  JNI_TRACE_I("%s: enter", fn);
  sSessionState = UWB_UNKNOWN_SESSION;

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not enabled", fn);
    return sSessionState;
  }

  SyncEventGuard guard(sUwaGetSessionStatusEvent);
  status = UWA_GetSessionStatus(sessionId);
  if (status == UWA_STATUS_OK) {
    sUwaGetSessionStatusEvent.wait(UWB_CMD_TIMEOUT);
  }
  JNI_TRACE_I("%s: exit", fn);
  return sSessionState;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_ControllerMulticastListUpdate()
**
** Description:     API to set Controller Multicast List Update
**
** Params:          env: JVM environment.
**                  o: Java object.
**                  sessionId: Session Id to which update the list
**                  action: Required Action to be taken
**                  noOfControlees: Number of Responders
**                  shortAddressList: Short Address list for each responder
**                  subSessionIdList: Sub session Id list of each responder
**
** Returns:         UFA_STATUS_OK on success or UFA_STATUS_FAILED on failure
**
*******************************************************************************/
jbyte uwbNativeManager_ControllerMulticastListUpdate(
    JNIEnv *env, jobject o, jint sessionId, jbyte action, jbyte noOfControlees,
    jshortArray shortAddressList, jintArray subSessionIdList) {
  static const char fn[] = "uwbNativeManager_ControllerMulticastListUpdate";
  UNUSED(fn);
  tUWA_STATUS status = UWA_STATUS_FAILED;
  uint16_t *shortAddressArray = NULL;
  uint32_t *subSessionIdArray = NULL;
  JNI_TRACE_E("%s: enter; ", fn);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return status;
  }

  if ((shortAddressList == NULL) || (subSessionIdList == NULL)) {
    JNI_TRACE_E("%s: subSessionIdList or shortAddressList value is NULL", fn);
    return status;
  }
  uint8_t shortAddressLen = env->GetArrayLength(shortAddressList);
  uint8_t subSessionIdLen = env->GetArrayLength(subSessionIdList);
  if (noOfControlees > MAX_NUM_CONTROLLEES) {
    JNI_TRACE_E("%s: no Of Controlees %d exceeded than %d ", fn,
                shortAddressLen, MAX_NUM_CONTROLLEES);
    return status;
  }

  if ((shortAddressLen > 0) && (subSessionIdLen > 0)) {
    shortAddressArray = (uint16_t *)malloc(shortAddressLen);
    if (shortAddressArray == NULL) {
      JNI_TRACE_E("%s: malloc failure for shortAddressArray", fn);
      return status;
    }
    memset(shortAddressArray, 0, shortAddressLen);
    env->GetShortArrayRegion(shortAddressList, 0, shortAddressLen,
                            (jshort *)shortAddressArray);

    subSessionIdArray = (uint32_t *)malloc(SESSION_ID_LEN * subSessionIdLen);
    if (subSessionIdArray == NULL) {
      free(shortAddressArray);
      JNI_TRACE_E("%s: malloc failure for subSessionIdArray", fn);
      return status;
    }
    memset(subSessionIdArray, 0, (SESSION_ID_LEN * subSessionIdLen));
    env->GetIntArrayRegion(subSessionIdList, 0, subSessionIdLen,
                           (jint *)subSessionIdArray);

    sMulticastListUpdateStatus = false;
    SyncEventGuard guard(sUwaMulticastListUpdateEvent);
    status = UWA_ControllerMulticastListUpdate(
        sessionId, action, noOfControlees, shortAddressArray, subSessionIdArray);
    if (status == UWA_STATUS_OK) {
      sUwaMulticastListUpdateEvent.wait(UWB_CMD_TIMEOUT);
    }
    free(shortAddressArray);
    free(subSessionIdArray);
  } else {
    JNI_TRACE_E("%s: controleeListArray length is not valid", fn);
  }
  JNI_TRACE_I("%s: exit", fn);
  return (sMulticastListUpdateStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_SetCountryCode()
**
** Description:     API to set country code
**
** Params:          env: JVM environment.
**                  o: Java object.
**                  countryCode: ISO country code
**
** Returns:         UFA_STATUS_OK on success or UFA_STATUS_FAILED on failure
**
*******************************************************************************/
jbyte uwbNativeManager_SetCountryCode(JNIEnv *env, jobject o,
                                      jbyteArray countryCode) {
  static const char fn[] = "uwbNativeManager_SetCountryCode";
  tUWA_STATUS status = UWA_STATUS_FAILED;
  uint8_t *countryCodeArray = NULL;
  JNI_TRACE_E("%s: enter; ", fn);

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", fn);
    return status;
  }
  if (countryCode == NULL) {
    JNI_TRACE_E("%s: country code value is NULL", fn);
    return status;
  }
  uint8_t countryCodeArrayLen = env->GetArrayLength(countryCode);
  if (countryCodeArrayLen != 2) {
    JNI_TRACE_E("%s: Malformed country code arraylen %d", fn,
                countryCodeArrayLen);
    return status;
  }

  countryCodeArray = (uint8_t *)malloc(countryCodeArrayLen);
  if (countryCodeArray == NULL) {
    JNI_TRACE_E("%s: malloc failure for countryCodeArray", fn);
    return status;
  }
  memset(countryCodeArray, 0, countryCodeArrayLen);
  env->GetByteArrayRegion(countryCode, 0, countryCodeArrayLen,
                          (jbyte *)countryCodeArray);
  sSetCountryCodeStatus = false;
  SyncEventGuard guard(sUwaSetCountryCodeEvent);
  status = UWA_ControllerSetCountryCode(countryCodeArray);
  if (status == UWA_STATUS_OK) {
    sUwaSetCountryCodeEvent.wait(UWB_CMD_TIMEOUT);
  }
  free(countryCodeArray);
  JNI_TRACE_I("%s: exit", fn);
  return (sSetCountryCodeStatus) ? UWA_STATUS_OK : UWA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_init
**
** Description:     Initialize variables.
**
** Params           env: JVM environment.
**                  o: Java object.
**
** Returns:         True if ok.
**
*******************************************************************************/
jboolean uwbNativeManager_init(JNIEnv *env, jobject o) {
  uwbEventManager.doLoadSymbols(env, o);
  return JNI_TRUE;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_enableConformanceTest
**
** Description:     Enable or disable MCTT mode of operation.
**
** Params:          env: JVM environment.
**                  o: Java object.
**                  enable: enable/disable MCTT mode
**
********************************************************************************/
jbyte uwbNativeManager_enableConformanceTest(JNIEnv *env, jobject o,
                                             jboolean enable) {
  static const char fn[] = "uwbNativeManager_enableConformanceTest";
  UNUSED(fn);
  JNI_TRACE_I("%s: enter", fn);
  tUWA_STATUS status = UWA_STATUS_FAILED;

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not enabled", fn);
    return status;
  }
  UWB_EnableConformanceTest(enable);
  JNI_TRACE_I("%s: exit", fn);
  return UWA_STATUS_OK;
}

/*******************************************************************************
**
** Function:        uwbNativeManager_GetDeviceCapebilityParams()
**
** Description:     Invoked this API to get the device capability information.
**
** Params:          env: JVM environment.
**                  o: Java object.
**
** Returns:         Returns byte array
**
*******************************************************************************/
jobject uwbNativeManager_GetDeviceCapebilityParams(JNIEnv* env, jobject o) {
  JNI_TRACE_I("%s: Entry", __func__);
  tUWA_STATUS status;

  if (!gIsUwaEnabled) {
    JNI_TRACE_E("%s: UWB device is not initialized", __func__);
    return NULL;
  }

  sGetDeviceCapsRespStatus = false;
  SyncEventGuard guard(sUwaGetDeviceCapsEvent);
  status = UWA_GetCoreGetDeviceCapability();
  if (status == UWA_STATUS_OK) {
    JNI_TRACE_D("%s: Success UWA_GetCoreGetDeviceCapability", __func__);
    sUwaGetDeviceCapsEvent.wait(UWB_CMD_TIMEOUT);
  } else {
    JNI_TRACE_E("%s: Failed UWA_GetCoreGetDeviceCapability", __func__);
    return NULL;
  }

  if (!sGetDeviceCapsRespStatus) {
    JNI_TRACE_E("%s: Failed getDeviceCapabilityInfo, Status = %d", __func__,
                sGetDeviceCapsRespStatus);
    return NULL;
  }

  const char *UWB_TLV_DATA =
	"com/android/server/uwb/data/UwbTlvData";
  jclass tlvDataClass = env->FindClass(UWB_TLV_DATA);
  jmethodID constructor =
      env->GetMethodID(tlvDataClass, "<init>", "(II[B)V");
  if (constructor == JNI_NULL) {
    JNI_TRACE_E("%s: jni cannot find the method for UwbTlvDATA", __func__);
    return NULL;
  }

  //remove vendor ext parameters
  uint8_t sUwbDeviceCapaInfos[UCI_MAX_PKT_SIZE];
  uint16_t capLen = 0;
  for( uint16_t index= 0;index< sDevCapInfoLen;) {
      if (sUwbDeviceCapability[index] == 0xE0 ) { //Ext id
         switch (sUwbDeviceCapability[index+1]) { //Ext sub id
             case 0x00:
             case 0x01:
             case 0x02:
	       int lenofParam =  sUwbDeviceCapability[index+2]; //Get the length of Ext paramaeter
	       index = index + (lenofParam+3); // increament index by (Ext id+ Ext sub id + length of ext param + velue of param)
               sDevCapInfoIds--;
	      break;
	 }
      } else {
	 sUwbDeviceCapaInfos[capLen++] = sUwbDeviceCapability[index];
         index++;
      }
  }
  jbyteArray deviceCapabilityInfo = env->NewByteArray(capLen);
  env->SetByteArrayRegion(deviceCapabilityInfo, 0, capLen,
                          (jbyte*)&sUwbDeviceCapaInfos[0]);
  JNI_TRACE_I("%s: Exit", __func__);
  return env->NewObject(tlvDataClass, constructor, status, sDevCapInfoIds, deviceCapabilityInfo);
}

/*****************************************************************************
**
** JNI functions for android
** UWB service layer has to invoke these APIs to get required functionality
**
*****************************************************************************/
static JNINativeMethod gMethods[] = {
    {"nativeInit", "()Z", (void *)uwbNativeManager_init},
    {"nativeDoInitialize", "()Z", (void *)uwbNativeManager_doInitialize},
    {"nativeDoDeinitialize", "()Z", (void *)uwbNativeManager_doDeinitialize},
    {"nativeSessionInit", "(IB)B", (void *)uwbNativeManager_sessionInit},
    {"nativeSessionDeInit", "(I)B", (void *)uwbNativeManager_sessionDeInit},
    {"nativeSetAppConfigurations",
      "(III[B)Lcom/android/server/uwb/data/UwbConfigStatusData;",
     (void *)uwbNativeManager_setAppConfigurations},
    {"nativeGetAppConfigurations",
      "(III[B)Lcom/android/server/uwb/data/UwbTlvData;",
     (void *)uwbNativeManager_getAppConfigurations},
    {"nativeRangingStart", "(I)B", (void *)uwbNativeManager_startRanging},
    {"nativeRangingStop", "(I)B", (void *)uwbNativeManager_stopRanging},
    {"nativeGetSessionCount", "()B", (void *)uwbNativeManager_getSessionCount},
    {"nativeGetSessionState", "(I)B", (void *)uwbNativeManager_getSessionState},
    {"nativeControllerMulticastListUpdate", "(IBB[S[I)B",
     (void *)uwbNativeManager_ControllerMulticastListUpdate},
    {"nativeSetCountryCode", "([B)B", (void *)uwbNativeManager_SetCountryCode},
    {"nativeSendRawVendorCmd", "(II[B)Lcom/android/server/uwb/data/UwbVendorUciResponse;",
    (void*)uwbNativeManager_sendRawUci},
    {"nativeEnableConformanceTest", "(Z)B",
     (void*)uwbNativeManager_enableConformanceTest},
    {"nativeGetMaxSessionNumber", "()I",
     (void *)uwbNativeManager_getMaxSessionNumber},
    {"nativeResetDevice", "(B)B", (void *)uwbNativeManager_resetDevice},
    {"nativeGetSpecificationInfo",
     "()Lcom/android/server/uwb/info/UwbSpecificationInfo;",
     (void *)uwbNativeManager_getSpecificationInfo},
    {"nativeGetCapsInfo", "()Lcom/android/server/uwb/data/UwbTlvData;",
     (void*)uwbNativeManager_GetDeviceCapebilityParams}
};

/*******************************************************************************
**
** Function:        register_UwbNativeManager
**
** Description:     Regisgter JNI functions of UwbEventManager class with Java
*Virtual Machine.
**
** Params:          env: Environment of JVM.
**
** Returns:         Status of registration (JNI version).
**
*******************************************************************************/
int register_com_android_uwb_dhimpl_UwbNativeManager(JNIEnv *env) {
  static const char fn[] = "register_com_android_uwb_dhimpl_UwbNativeManager";
  UNUSED(fn);
  JNI_TRACE_I("%s: enter", fn);
  return jniRegisterNativeMethods(env, UWB_NATIVE_MANAGER_CLASS_NAME, gMethods,
                                  sizeof(gMethods) / sizeof(gMethods[0]));
}

} // namespace android


/******************************************************************************
 *
 *  Copyright (C) 1999-2014 Broadcom Corporation
 *  Copyright 2018-2020 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
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
/******************************************************************************
 *
 *  This file contains the action functions for device manager state
 *  machine.
 *
 ******************************************************************************/
#include <string.h>

#include "uci_hmsgs.h"
#include "uci_log.h"
#include "uwa_api.h"
#include "uwa_dm_int.h"
#include "uwa_sys.h"
#include "uwa_sys_int.h"
#include "uwb_api.h"
#include "uwb_osal_common.h"

static void uwa_dm_set_init_uci_params(void);

/*******************************************************************************
**
** Function         uwa_dm_sys_enable
**
** Description      This function on enable
**
** Returns          void
**
*******************************************************************************/
void uwa_dm_sys_enable(void) { uwa_dm_set_init_uci_params(); }

/*******************************************************************************
**
** Function         uwa_dm_set_init_uci_params
**
** Description      Set initial UCI configuration parameters
**
** Returns          void
**
*******************************************************************************/
static void uwa_dm_set_init_uci_params(void) { return; }

/*******************************************************************************
**
** Function         uwa_dm_disable_event
**
** Description      report disable event
**
** Returns          void
**
*******************************************************************************/
static void uwa_dm_disable_event(void) {
  /* Deregister DM from sys */
  uwa_sys_deregister(UWA_ID_DM);

  /* Notify app */
  if (uwa_dm_cb.p_dm_cback != NULL) {
    uwa_dm_cb.flags &= (uint32_t)(~UWA_DM_FLAGS_DM_IS_ACTIVE);
    (*uwa_dm_cb.p_dm_cback)(UWA_DM_DISABLE_EVT, NULL);
  }
}

/*******************************************************************************
**
** Function         uwa_dm_uwb_response_cback
**
** Description      Call DM event hanlder with UWB response callback data
**
** Returns          void
**
*******************************************************************************/
static void uwa_dm_uwb_response_cback(tUWB_RESPONSE_EVT event,
                                      tUWB_RESPONSE* p_data) {
  tUWA_DM_CBACK_DATA dm_cback_data;

  UCI_TRACE_I("uwa_dm_uwb_response_cback:%s(0x%x)",
              uwa_dm_uwb_revt_2_str(event).c_str(), event);
  switch (event) {
    case UWB_ENABLE_REVT: /* 0  Enable event */
      /* UWB stack enabled. Enable uwa sub-systems */
      /* Notify app */
      uwa_dm_cb.flags &= UWA_DM_FLAGS_DM_IS_ACTIVE;
      dm_cback_data.status = p_data->enable.status;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_ENABLE_EVT, &dm_cback_data);
      break;

    case UWB_DISABLE_REVT: /* 1  Disable event */
      uwa_dm_disable_event();
      break;

    case UWB_DEVICE_STATUS_REVT: /* device status notification */
      dm_cback_data.dev_status.status = p_data->sDevice_status.status;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_DEVICE_STATUS_NTF_EVT, &dm_cback_data);
      break;

    case UWB_GET_DEVICE_INFO_REVT: {
      if (p_data->sGet_device_info.status == UWB_STATUS_OK) {
        dm_cback_data.sGet_device_info.status = UWA_STATUS_OK;
        dm_cback_data.sGet_device_info.uci_version =
            p_data->sGet_device_info.uci_version;
        dm_cback_data.sGet_device_info.mac_version =
            p_data->sGet_device_info.mac_version;
        dm_cback_data.sGet_device_info.phy_version =
            p_data->sGet_device_info.phy_version;
        dm_cback_data.sGet_device_info.uciTest_version =
            p_data->sGet_device_info.uciTest_version;
        dm_cback_data.sGet_device_info.vendor_info_len =
            p_data->sGet_device_info.vendor_info_len;
        memcpy(dm_cback_data.sGet_device_info.vendor_info,
               p_data->sGet_device_info.vendor_info,
               p_data->sGet_device_info.vendor_info_len);
      } else {
        dm_cback_data.sGet_device_info.status = UWA_STATUS_FAILED;
      }

      (*uwa_dm_cb.p_dm_cback)(UWA_DM_CORE_GET_DEVICE_INFO_RSP_EVT,
                              &dm_cback_data);
    } break;

    case UWB_SET_CORE_CONFIG_REVT: /* 2  Set Config Response */
      dm_cback_data.sCore_set_config.status = p_data->sCore_set_config.status;
      dm_cback_data.sCore_set_config.num_param_id =
          p_data->sCore_set_config.num_param_id;
      dm_cback_data.sCore_set_config.tlv_size =
          p_data->sCore_set_config.tlv_size;
      if (dm_cback_data.sCore_set_config.tlv_size > 0) {
        memcpy(dm_cback_data.sCore_set_config.param_ids,
               p_data->sCore_set_config.param_ids,
               p_data->sCore_set_config.tlv_size);
      }
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_CORE_SET_CONFIG_RSP_EVT, &dm_cback_data);
      break;

    case UWB_GET_CORE_CONFIG_REVT: /* 3  Get Config Response */
      dm_cback_data.sCore_get_config.status = p_data->sCore_get_config.status;
      dm_cback_data.sCore_get_config.no_of_ids =
          p_data->sCore_get_config.no_of_ids;
      dm_cback_data.sCore_get_config.tlv_size =
          p_data->sCore_get_config.tlv_size;
      if (dm_cback_data.sCore_get_config.tlv_size > 0) {
        memcpy(dm_cback_data.sCore_get_config.param_tlvs,
               p_data->sCore_get_config.p_param_tlvs,
               p_data->sCore_get_config.tlv_size);
      }
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_CORE_GET_CONFIG_RSP_EVT, &dm_cback_data);
      break;

    case UWB_DEVICE_RESET_REVT: /* Device Reset Response */
      if (p_data->sDevice_reset.status == UWB_STATUS_OK) {
        dm_cback_data.sDevice_reset.status = p_data->sDevice_reset.status;
      } else {
        dm_cback_data.sDevice_reset.status = UWA_STATUS_FAILED;
      }
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_DEVICE_RESET_RSP_EVT, &dm_cback_data);
      break;

    case UWB_CORE_GEN_ERR_STATUS_REVT: /* Generic error notification */
    {
      dm_cback_data.sCore_gen_err_status.status =
          p_data->sCore_gen_err_status.status;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_CORE_GEN_ERR_STATUS_EVT, &dm_cback_data);
    } break;

    case UWB_SESSION_INIT_REVT: /* Session init response */
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E(" Session Init request is failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SESSION_INIT_RSP_EVT, &dm_cback_data);
      break;

    case UWB_SESSION_DEINIT_REVT: /* session de-init response */
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E(" Session De Init request is failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SESSION_DEINIT_RSP_EVT, &dm_cback_data);
      break;

    case UWB_SESSION_STATUS_NTF_REVT: /* session status notification */
    {
      tUWA_SESSION_STATUS_NTF_REVT* p_session_ntf =
          &dm_cback_data.sSessionStatus;
      p_session_ntf->session_id = p_data->sSessionStatus.session_id;
      p_session_ntf->state = p_data->sSessionStatus.state;
      p_session_ntf->reason_code = p_data->sSessionStatus.reason_code;
      if (UWB_SESSION_INITIALIZED == p_session_ntf->state) {
        // Trigger session initialization HAL API.
        tUWB_STATUS status;
        status = UWB_HalSessionInit(dm_cback_data.sSessionStatus.session_id);
        if (UWB_STATUS_OK == status) {
          UCI_TRACE_I("HAL session init: success ,status=0x%X", status);
        } else {
          UCI_TRACE_E("HAL session init: status=0x%X. Deinitializing session", status);
          p_session_ntf->state = UWB_SESSION_DEINITIALIZED;
        }
      }
    }
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SESSION_STATUS_NTF_EVT, &dm_cback_data);
      break;

    case UWB_SESSION_GET_COUNT_REVT: /* get session count response */
      if (p_data->status == UWB_STATUS_OK) {
        tUWA_SESSION_GET_COUNT* p_sGet_session_cnt =
            &dm_cback_data.sGet_session_cnt;
        p_sGet_session_cnt->status = p_data->sGet_session_cnt.status;
        p_sGet_session_cnt->count = p_data->sGet_session_cnt.count;

      } else {
        UCI_TRACE_E("Get session count command failed");
        dm_cback_data.status = UWA_STATUS_FAILED;
      }
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SESSION_GET_COUNT_RSP_EVT, &dm_cback_data);
      break;

    case UWB_SESSION_GET_STATE_REVT: /*get session state response */
      if (p_data->sGet_session_state.status == UWB_STATUS_OK) {
        tUWA_SESSION_GET_STATE* p_sGet_session_state =
            &dm_cback_data.sGet_session_state;
        p_sGet_session_state->status = p_data->sGet_session_state.status;
        p_sGet_session_state->session_state =
            p_data->sGet_session_state.session_state;

      } else {
        UCI_TRACE_E("Get session state command failed");
        dm_cback_data.status = UWA_STATUS_FAILED;
      }
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SESSION_GET_STATE_RSP_EVT, &dm_cback_data);
      break;

    case UWB_SET_APP_CONFIG_REVT: /*set session app config response */
      dm_cback_data.sApp_set_config.status = p_data->sApp_set_config.status;
      dm_cback_data.sApp_set_config.num_param_id =
          p_data->sApp_set_config.num_param_id;
      dm_cback_data.sApp_set_config.tlv_size = p_data->sApp_set_config.tlv_size;
      if (dm_cback_data.sApp_set_config.tlv_size > 0) {
        memcpy(dm_cback_data.sApp_set_config.param_ids,
               p_data->sApp_set_config.param_ids,
               p_data->sApp_set_config.tlv_size);
      }
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SESSION_SET_CONFIG_RSP_EVT,
                              &dm_cback_data);
      break;

    case UWB_GET_APP_CONFIG_REVT: /*get session app config response */
      dm_cback_data.sApp_get_config.status = p_data->sApp_get_config.status;
      dm_cback_data.sApp_get_config.no_of_ids =
          p_data->sApp_get_config.no_of_ids;
      dm_cback_data.sApp_get_config.tlv_size = p_data->sApp_get_config.tlv_size;
      if (dm_cback_data.sApp_get_config.tlv_size > 0) {
        memcpy(dm_cback_data.sApp_get_config.param_tlvs,
               p_data->sApp_get_config.p_param_tlvs,
               p_data->sApp_get_config.tlv_size);
      }
      /* Return result of getAppConfig to the app */
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SESSION_GET_CONFIG_RSP_EVT,
                              &dm_cback_data);
      break;

    case UWB_START_RANGE_REVT: /* range start response */
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E("Range start command failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_RANGE_START_RSP_EVT, &dm_cback_data);
      break;

    case UWB_STOP_RANGE_REVT: /* range start response */
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E("Range stop command failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_RANGE_STOP_RSP_EVT, &dm_cback_data);
      break;

    case UWB_RANGE_DATA_REVT: /* range data notification */
    {
      tUWA_RANGE_DATA_NTF* p_sRange_data = &dm_cback_data.sRange_data;
      memset(p_sRange_data, 0, sizeof(tUWA_RANGE_DATA_NTF));
      if (p_data->sRange_data.range_data_len != 0) {
        memcpy((uint8_t*)p_sRange_data, (uint8_t*)&p_data->sRange_data,
               sizeof(tUWA_RANGE_DATA_NTF));
        (*uwa_dm_cb.p_dm_cback)(UWA_DM_RANGE_DATA_NTF_EVT, &dm_cback_data);
      }
    } break;

    case UWB_GET_RANGE_COUNT_REVT: /* get ranging round count response */
      if (p_data->status == UWB_STATUS_OK) {
        tUWA_RANGE_GET_RNG_COUNT_REVT* p_sGet_range_cnt =
            &dm_cback_data.sGet_range_cnt;
        p_sGet_range_cnt->status = p_data->sGet_range_cnt.status;
        p_sGet_range_cnt->count = p_data->sGet_range_cnt.count;
      } else {
        UCI_TRACE_E("Get range count command failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_GET_RANGE_COUNT_RSP_EVT, &dm_cback_data);
      break;

    case UWB_CORE_GET_DEVICE_CAPABILITY_REVT: /* Core Get device capability
                                                 Response */
    {
      dm_cback_data.sGet_device_capability.status =
          p_data->sGet_device_capability.status;
      dm_cback_data.sGet_device_capability.no_of_tlvs =
          p_data->sGet_device_capability.no_of_tlvs;
      dm_cback_data.sGet_device_capability.tlv_buffer_len =
          p_data->sGet_device_capability.tlv_buffer_len;
      if (dm_cback_data.sGet_device_capability.tlv_buffer_len > 0) {
        memcpy(dm_cback_data.sGet_device_capability.tlv_buffer,
               p_data->sGet_device_capability.tlv_buffer,
               p_data->sGet_device_capability.tlv_buffer_len);
      }
      /* Return result of core get device capability to the app */
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_GET_CORE_DEVICE_CAP_RSP_EVT,
                              &dm_cback_data);
    } break;

    case UWB_SESSION_UPDATE_MULTICAST_LIST_REVT: /* multi-cast list update
                                                    response*/
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E(" Session update multicast list request is failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SESSION_MC_LIST_UPDATE_RSP_EVT,
                              &dm_cback_data);
      break;

    case UWB_SESSION_UPDATE_MULTICAST_LIST_NTF_REVT: /* session update multicast
                                                        list data notification
                                                      */
    {
      tUWA_SESSION_UPDATE_MULTICAST_LIST_NTF* p_sMulticast_list_ntf =
          &dm_cback_data.sMulticast_list_ntf;
      memset(p_sMulticast_list_ntf, 0,
             sizeof(tUWA_SESSION_UPDATE_MULTICAST_LIST_NTF));
      memcpy((uint8_t*)p_sMulticast_list_ntf,
             (uint8_t*)&p_data->sMulticast_list_ntf,
             sizeof(tUWA_SESSION_UPDATE_MULTICAST_LIST_NTF));
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SESSION_MC_LIST_UPDATE_NTF_EVT,
                              &dm_cback_data);
    } break;

    case UWB_SET_COUNTRY_CODE_REVT: /* set country code response*/
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E(" Set country code request failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SET_COUNTRY_CODE_RSP_EVT,
                              &dm_cback_data);
      break;

    case UWB_BLINK_DATA_TX_REVT: /* blink data send response */
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E(" Blink data tx request is failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SEND_BLINK_DATA_RSP_EVT, &dm_cback_data);
      break;

    case UWB_BLINK_DATA_TX_NTF_REVT: /* blink data tx notification */
    {
      tUWA_SEND_BLINK_DATA_NTF* p_sBlink_data_ntf =
          &dm_cback_data.sBlink_data_ntf;
      p_sBlink_data_ntf->repetition_count_status =
          p_data->sSend_blink_data_ntf.repetition_count_status;

      (*uwa_dm_cb.p_dm_cback)(UWA_DM_SEND_BLINK_DATA_NTF_EVT, &dm_cback_data);
    } break;

    case UWB_CONFORMANCE_TEST_DATA: /* conformance test notification */
    {
      tUWA_CONFORMANCE_TEST_DATA* p_sConformance_data_ntf =
          &dm_cback_data.sConformance_ntf;
      p_sConformance_data_ntf->length = p_data->sConformance_test_data.length;
      memcpy((uint8_t*)p_sConformance_data_ntf->data,
             (uint8_t*)p_data->sConformance_test_data.data,
             p_data->sConformance_test_data.length);
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_CONFORMANCE_NTF_EVT, &dm_cback_data);
    } break;
    case UWB_UWBS_RESP_TIMEOUT_REVT: /* event to notify response timeout */
    {
      dm_cback_data.status = UWB_STATUS_FAILED;
      (*uwa_dm_cb.p_dm_cback)(UWA_DM_UWBS_RESP_TIMEOUT_EVT, &dm_cback_data);
    } break;
    case UWB_VENDOR_SPECIFIC_UCI_NTF_EVT:
    {
      dm_cback_data.sVendor_specific_ntf.len = p_data->sVendor_specific_ntf.len;
      memcpy((uint8_t*)dm_cback_data.sVendor_specific_ntf.data, p_data->sVendor_specific_ntf.data, p_data->sVendor_specific_ntf.len);
      (*uwa_dm_cb.p_dm_cback)(UWA_VENDOR_SPECIFIC_UCI_NTF_EVT, &dm_cback_data);
    } break;
    default:
      UCI_TRACE_E("unknown event.");
      break;
  }
}

/*******************************************************************************
**
** Function         uwa_dm_uwb_test_response_cback
**
** Description      callback handles all RF test responses and notifications
**
** Returns          void
**
*******************************************************************************/
static void uwa_dm_uwb_test_response_cback(tUWB_TEST_RESPONSE_EVT event,
                                           tUWB_TEST_RESPONSE* p_data) {
  tUWA_DM_TEST_CBACK_DATA dm_cback_data;

  UCI_TRACE_I("uwa_dm_uwb_test_response_cback:%s(0x%x)",
              uwa_test_dm_uwb_revt_2_str(event).c_str(), event);
  switch (event) {
    case UWB_TEST_SET_CONFIG_REVT: /* set test configs response */
      dm_cback_data.sTest_set_config.status = p_data->sTest_set_config.status;
      dm_cback_data.sTest_set_config.num_param_id =
          p_data->sTest_set_config.num_param_id;
      dm_cback_data.sTest_set_config.tlv_size =
          p_data->sTest_set_config.tlv_size;
      if (p_data->sTest_set_config.tlv_size > 0) {
        memcpy(dm_cback_data.sTest_set_config.param_ids,
               p_data->sTest_set_config.param_ids,
               p_data->sTest_set_config.tlv_size);
      }
      (*uwa_dm_cb.p_dm_test_cback)(UWA_DM_TEST_SET_CONFIG_RSP_EVT,
                                   &dm_cback_data);
      break;

    case UWB_TEST_GET_CONFIG_REVT: /* get test configs response */
      dm_cback_data.sTest_get_config.status = p_data->sTest_get_config.status;
      dm_cback_data.sTest_get_config.no_of_ids =
          p_data->sTest_get_config.no_of_ids;
      dm_cback_data.sTest_get_config.tlv_size =
          p_data->sTest_get_config.tlv_size;
      if (p_data->sTest_get_config.tlv_size > 0) {
        memcpy(dm_cback_data.sTest_get_config.param_tlvs,
               p_data->sTest_get_config.p_param_tlvs,
               p_data->sTest_get_config.tlv_size);
      }
      /* Return result of getTestConfig to the app */
      (*uwa_dm_cb.p_dm_test_cback)(UWA_DM_TEST_GET_CONFIG_RSP_EVT,
                                   &dm_cback_data);
      break;

    case UWB_TEST_PERIODIC_TX_REVT: /* periodic tx response */
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E("per tx command failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_test_cback)(UWA_DM_TEST_PERIODIC_TX_RSP_EVT,
                                   &dm_cback_data);
      break;

    case UWB_TEST_PER_RX_REVT: /* per rx response */
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E("per rx command failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_test_cback)(UWA_DM_TEST_PER_RX_RSP_EVT, &dm_cback_data);
      break;

    case UWB_TEST_LOOPBACK_REVT: /* rf loop back response */
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E("rf loop back command failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_test_cback)(UWA_DM_TEST_LOOPBACK_RSP_EVT,
                                   &dm_cback_data);
      break;

    case UWB_TEST_RX_REVT: /* rx test response */
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E("rx test command failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_test_cback)(UWA_DM_TEST_RX_RSP_EVT, &dm_cback_data);
      break;

    case UWB_TEST_STOP_SESSION_REVT: /* per rx response */
      if (p_data->status != UWB_STATUS_OK) {
        UCI_TRACE_E("test stop command failed");
      }
      dm_cback_data.status = p_data->status;
      (*uwa_dm_cb.p_dm_test_cback)(UWA_DM_TEST_STOP_SESSION_RSP_EVT,
                                   &dm_cback_data);
      break;

    case UWB_TEST_PER_RX_DATA_REVT: /* PER test data notification */
    {
      tUWA_RF_TEST_DATA* p_per_rx_test_data = &dm_cback_data.rf_test_data;
      if (p_data->sRf_test_result.length > 0) {
        p_per_rx_test_data->length = p_data->sRf_test_result.length;
        memcpy(&p_per_rx_test_data->data[0], &p_data->sRf_test_result.data[0],
               p_per_rx_test_data->length);
      }
      (*uwa_dm_cb.p_dm_test_cback)(UWA_DM_TEST_PER_RX_NTF_EVT, &dm_cback_data);
    } break;

    case UWB_TEST_PERIODIC_TX_DATA_REVT: /* periodic Tx test data notification
                                          */
    {
      tUWA_RF_TEST_DATA* p_rf_test_data = &dm_cback_data.rf_test_data;
      if (p_data->sRf_test_result.length > 0) {
        p_rf_test_data->length = p_data->sRf_test_result.length;
        memcpy(&p_rf_test_data->data[0], &p_data->sRf_test_result.data[0],
               p_rf_test_data->length);
      }
      (*uwa_dm_cb.p_dm_test_cback)(UWA_DM_TEST_PERIODIC_TX_NTF_EVT,
                                   &dm_cback_data);
    } break;

    case UWB_TEST_LOOPBACK_DATA_REVT: /* loopback test data notification */
    {
      tUWA_RF_TEST_DATA* p_uwb_loopback_test_data = &dm_cback_data.rf_test_data;
      if (p_data->sRf_test_result.length > 0) {
        p_uwb_loopback_test_data->length = p_data->sRf_test_result.length;
        memcpy(&p_uwb_loopback_test_data->data[0],
               &p_data->sRf_test_result.data[0],
               p_uwb_loopback_test_data->length);
      }
      (*uwa_dm_cb.p_dm_test_cback)(UWA_DM_TEST_LOOPBACK_NTF_EVT,
                                   &dm_cback_data);
    } break;

    case UWB_TEST_RX_DATA_REVT: /* Rx test data notification */
    {
      tUWA_RF_TEST_DATA* p_uwb_rx_test_data = &dm_cback_data.rf_test_data;
      if (p_data->sRf_test_result.length > 0) {
        p_uwb_rx_test_data->length = p_data->sRf_test_result.length;
        memcpy(&p_uwb_rx_test_data->data[0], &p_data->sRf_test_result.data[0],
               p_uwb_rx_test_data->length);
      }
      (*uwa_dm_cb.p_dm_test_cback)(UWA_DM_TEST_RX_NTF_EVT, &dm_cback_data);
    } break;

    default:
      UCI_TRACE_E("unknown event.");
      break;
  }
}

/*******************************************************************************
**
** Function         uwa_dm_enable
**
** Description      Initializes the UWB device manager
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_enable(tUWA_DM_MSG* p_data) {
  tUWA_DM_CBACK_DATA dm_cback_data;
  UCI_TRACE_I("uwa_dm_enable ()");

  /* Check if UWA is already enabled */
  if (!(uwa_dm_cb.flags & UWA_DM_FLAGS_DM_IS_ACTIVE)) {
    uwa_dm_cb.flags |=
        (UWA_DM_FLAGS_DM_IS_ACTIVE | UWA_DM_FLAGS_ENABLE_EVT_PEND);

    /* Store Enable parameters */
    uwa_dm_cb.p_dm_cback = p_data->enable.p_dm_cback;
    uwa_dm_cb.p_dm_test_cback = p_data->enable.p_dm_test_cback;
    /* Enable UWB stack */
    UWB_Enable(uwa_dm_uwb_response_cback, uwa_dm_uwb_test_response_cback);
  } else {
    UCI_TRACE_E("uwa_dm_enable: ERROR ALREADY ENABLED.");
    dm_cback_data.status = UWA_STATUS_FAILED;
    (*(p_data->enable.p_dm_cback))(UWA_DM_ENABLE_EVT, &dm_cback_data);
  }

  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_disable
**
** Description      Disables the UWB device manager
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_disable(tUWA_DM_MSG* p_data) {
  UCI_TRACE_I("uwa_dm_disable (): graceful:%d", p_data->disable.graceful);

  /* Disable all subsystems other than DM (DM will be disabled after all  */
  /* the other subsystem have been disabled)                              */
  uwa_sys_disable_subsystems(p_data->disable.graceful);

  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_disable_complete
**
** Description      Called when all UWA subsytems are disabled.
**
**                  UWB core stack can now be disabled.
**
** Returns          void
**
*******************************************************************************/
void uwa_dm_disable_complete(void) {
  UCI_TRACE_I("uwa_dm_disable_complete ()");
  /* Disable uwb core stack */
  UWB_Disable();
}

/*******************************************************************************
**
** Function         uwa_dm_act_get_device_info
**
** Description      Function to get the UWBS device information by issuing get
**                  device UCI command
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_get_device_info(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_get_device_info(): p_data is NULL)");
    return false;
  } else {
    status = UWB_GetDeviceInfo();
    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_get_device_info(): success ,status=0x%X", status);
    } else {
      UCI_TRACE_E("uwa_dm_act_get_device_info(): failed ,status=0x%X", status);
    }
  }
  return true;
}

/*******************************************************************************
**
** Function         uwa_dm_set_core_config
**
** Description      Process set core config command
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_set_core_config(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  uint8_t buff[255];
  uint8_t* p = buff;

  tUWA_DM_CBACK_DATA dm_cback_data;

  if (p_data->setconfig.length + 2 > 255) {
    /* Total length of TLV must be less than 256 (1 byte) */
    status = UWB_STATUS_FAILED;
  } else {
    UINT8_TO_STREAM(p, p_data->setconfig.param_id);
    UINT8_TO_STREAM(p, p_data->setconfig.length);
    ARRAY_TO_STREAM(p, p_data->setconfig.p_data, p_data->setconfig.length);

    status = UWB_SetCoreConfig((uint8_t)(p_data->setconfig.length + 2), buff);
  }

  if (status != UWB_STATUS_OK) {
    dm_cback_data.sCore_set_config.status = UWA_STATUS_INVALID_PARAM;
    (*uwa_dm_cb.p_dm_cback)(UWA_DM_CORE_SET_CONFIG_RSP_EVT, &dm_cback_data);
  }

  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_get_core_config
**
** Description      Process get config command
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_get_core_config(tUWA_DM_MSG* p_data) {
  UWB_GetCoreConfig(p_data->getconfig.num_ids, p_data->getconfig.p_pmids);

  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_act_device_reset
**
** Description      Process core device reset command
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_device_reset(tUWA_DM_MSG* pResetConfig) {
  tUWB_STATUS status;

  if (pResetConfig == NULL) {
    UCI_TRACE_E("uwa_dm_act_device_reset(): pResetConfig is NULL)");
  } else {
    status = UWB_DeviceResetCommand(pResetConfig->sDevice_reset.resetConfig);
    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_device_reset(): success ,status=0x%X", status);
    } else {
      UCI_TRACE_E("uwa_dm_act_device_reset(): failed ,status=0x%X", status);
    }
  }

  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_act_send_session_init
**
** Description      send session init command
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_send_session_init(tUWA_DM_MSG* p_data) {
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_send_session_init(): p_data is NULL)");
    return false;
  } else {
    UWB_SessionInit(p_data->sessionInit.session_id,
                    p_data->sessionInit.sessionType);
  }
  return true;
}
/*******************************************************************************
**
** Function         uwa_dm_act_send_session_deinit
**
** Description      send session de init command
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_send_session_deinit(tUWA_DM_MSG* p_data) {
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_send_session_deinit(): p_data is NULL)");
    return false;
  } else {
    UWB_SessionDeInit(p_data->sessionInit.session_id);
  }
  return true;
}

/*******************************************************************************
**
** Function         uwa_dm_act_get_session_count
**
** Description      send get session count to get no of active sessions
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_get_session_count(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_get_session_count(): p_data is NULL)");
    return false;
  } else {
    status = UWB_GetSessionCount();
    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_get_session_count(): success ,status=0x%X",
                  status);
    } else {
      UCI_TRACE_E("uwa_dm_act_get_session_count(): failed ,status=0x%X",
                  status);
    }
  }
  return true;
}

/*******************************************************************************
**
** Function         uwa_dm_act_app_set_config
**
** Description      Send set configurations command to set the app configuration
**                  parameters
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_app_set_config(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;

  if (p_data->sApp_set_config.length + 2 > 255) {
    /* Total length of TLV must be less than 256 (1 byte) */
    status = UWB_STATUS_FAILED;
  } else {
    status = UWB_SetAppConfig(
        p_data->sApp_set_config.session_id, p_data->sApp_set_config.num_ids,
        p_data->sApp_set_config.length, p_data->sApp_set_config.p_data);
  }

  if (status != UWB_STATUS_OK) {
    UCI_TRACE_E("uwa_dm_act_app_set_config(): failed ,status=0x%X", status);
  } else {
    UCI_TRACE_I("uwa_dm_act_app_set_config(): success ,status=0x%X", status);
  }

  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_act_app_get_config
**
** Description      Send get configurations command to get the app configuration
**                  parameters
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_app_get_config(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;

  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_app_get_config(): p_data is NULL)");
  } else {
    status = UWB_GetAppConfig(
        p_data->sApp_get_config.session_id, p_data->sApp_get_config.num_ids,
        p_data->sApp_get_config.length, p_data->sApp_get_config.p_pmids);

    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_app_get_config(): success ,status=0x%X", status);
    } else {
      UCI_TRACE_E("uwa_dm_act_app_get_config(): failed ,status=0x%X", status);
    }
  }
  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_act_start_range_session
**
** Description      start the ranging session
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_start_range_session(tUWA_DM_MSG* p_data) {
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_start_range_session(): p_data is NULL)");
  } else {
    UWB_StartRanging(p_data->rang_start.session_id);
  }
  return true;
}

/*******************************************************************************
**
** Function         uwa_dm_act_stop_range_session
**
** Description      stop the ranging session
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_stop_range_session(tUWA_DM_MSG* p_data) {
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_stop_range_session(): p_data is NULL");
  } else {
    UWB_StopRanging(p_data->rang_stop.session_id);
  }
  return true;
}

/*******************************************************************************
**
** Function         uwa_dm_act_send_raw_vs
**
** Description      Send the raw vs command to the UCI command queue
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_send_raw_cmd(tUWA_DM_MSG* p_data) {
  UWB_HDR* p_cmd = (UWB_HDR*)p_data;

  p_cmd->offset = sizeof(tUWA_DM_API_SEND_RAW) - UWB_HDR_SIZE;
  p_cmd->len = p_data->send_raw.cmd_params_len;
  UWB_SendRawCommand(p_cmd, p_data->send_raw.p_cback);

  return false;
}

/*******************************************************************************
**
** Function         uwa_dm_act_get_range_count
**
** Description      Send the get range count command to the ranging count
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_get_range_count(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;

  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_get_range_count(): p_data is NULL)");
  } else {
    status = UWB_GetRangingCount(p_data->sGet_rang_count.session_id);

    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_get_range_count(): success ,status=0x%X", status);
    } else {
      UCI_TRACE_E("uwa_dm_act_get_range_count(): failed ,status=0x%X", status);
    }
  }
  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_act_get_session_status
**
** Description      Send the get session status command to get the session
**                  status
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_get_session_status(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;

  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_get_session_status(): p_data is NULL)");
  } else {
    status = UWB_GetSessionStatus(p_data->sGet_session_status.session_id);

    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_get_session_status(): success ,status=0x%X",
                  status);
    } else {
      UCI_TRACE_E("uwa_dm_act_get_session_status(): failed ,status=0x%X",
                  status);
    }
  }
  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_act_get_device_capability
**
** Description      send get capability info command
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_get_device_capability(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_get_device_capability(): p_data is NULL)");
  } else {
    status = UWB_CoreGetDeviceCapability();
    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_get_device_capability(): success ,status=0x%X",
                  status);
    } else {
      UCI_TRACE_E("uwa_dm_act_get_device_capability(): failed ,status=0x%X",
                  status);
    }
  }
  return true;
}

/*******************************************************************************
**
** Function         uwa_dm_act_multicast_list_update
**
** Description      send controlee multicast list update command
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_multicast_list_update(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_multicast_list_update(): p_data is NULL)");
  } else {
    status = UWB_MulticastListUpdate(
        p_data->sMulticast_list.session_id, p_data->sMulticast_list.action,
        p_data->sMulticast_list.no_of_controlee,
        p_data->sMulticast_list.short_address_list,
        p_data->sMulticast_list.subsession_id_list);
    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_multicast_list_update(): success ,status=0x%X",
                  status);
    } else {
      UCI_TRACE_E("uwa_dm_act_multicast_list_update(): failed ,status=0x%X",
                  status);
    }
  }
  return true;
}

/*******************************************************************************
**
** Function         uwa_dm_act_set_country_code
**
** Description      send country code set command.
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_set_country_code(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_set_country_code(): p_data is NULL)");
  } else {
    status = UWB_SetCountryCode(p_data->sCountryCode.country_code);
    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_set_country_code(): success ,status=0x%X",
                  status);
    } else {
      UCI_TRACE_E("uwa_dm_set_country_code(): failed ,status=0x%X", status);
    }
  }
  return true;
}

/*******************************************************************************
**
** Function         uwa_dm_act_send_blink_data
**
** Description      send blink data tx command
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_send_blink_data(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_send_blink_data(): p_data is NULL)");
  } else {
    status = UWB_SendBlinkData(p_data->sSend_blink_data.session_id,
                               p_data->sSend_blink_data.repetition_count,
                               p_data->sSend_blink_data.app_data_len,
                               p_data->sSend_blink_data.app_data);
    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_send_blink_data(): success ,status=0x%X", status);
    } else {
      UCI_TRACE_E("uwa_dm_act_send_blink_data(): failed ,status=0x%X", status);
    }
  }
  return true;
}
/* APIs for RF Test Functionality */

/*******************************************************************************
**
** Function         uwa_dm_act_test_set_config
**
** Description      Send set configurations command to set the test
**                  configuration parameters
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_test_set_config(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;

  if (p_data->sTest_set_config.length + 2 > UCI_MAX_PAYLOAD_SIZE) {
    /* Total length of TLV must be less than 256 (1 byte) */
    status = UWB_STATUS_FAILED;
  } else {
    status = UWB_SetTestConfig(
        p_data->sTest_set_config.session_id, p_data->sTest_set_config.num_ids,
        p_data->sTest_set_config.length, p_data->sTest_set_config.p_data);
  }
  if (status != UWB_STATUS_OK) {
    UCI_TRACE_E("uwa_dm_act_test_set_config(): failed ,status=0x%X", status);
  } else {
    UCI_TRACE_I("uwa_dm_act_test_set_config(): success ,status=0x%X", status);
  }

  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_act_test_get_config
**
** Description      Send get configurations command to get the test
**                  configuration parameters
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_test_get_config(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;

  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_test_get_config(): p_data is NULL)");
  } else {
    status = UWB_TestGetConfig(
        p_data->sTest_get_config.session_id, p_data->sTest_get_config.num_ids,
        p_data->sTest_get_config.length, p_data->sTest_get_config.p_pmids);

    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_test_get_config(): success ,status=0x%X", status);
    } else {
      UCI_TRACE_E("uwa_dm_act_test_get_config(): failed ,status=0x%X", status);
    }
  }
  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_act_test_periodic_tx
**
** Description      Send periodic tx command to start periodic Tx test
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_test_periodic_tx(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_test_periodic_tx(): p_data is NULL)");
  } else {
    status = UWB_TestPeriodicTx(p_data->sPeriodic_tx.length,
                                p_data->sPeriodic_tx.p_data);

    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_test_periodic_tx(): success ,status=0x%X",
                  status);
    } else {
      UCI_TRACE_E("uwa_dm_act_test_periodic_tx(): failed ,status=0x%X", status);
    }
  }
  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_act_test_per_rx
**
** Description      Send PER Rx command to start Packet error rate Rx test
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_test_per_rx(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_test_per_rx(): p_data is NULL)");
  } else {
    status = UWB_TestPerRx(p_data->sPer_rx.length, p_data->sPer_rx.p_data);

    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_test_per_rx(): success ,status=0x%X", status);
    } else {
      UCI_TRACE_E("uwa_dm_act_test_per_rx(): failed ,status=0x%X", status);
    }
  }
  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_act_test_uwb_loopback
**
** Description      Send Rf loop back command
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_test_uwb_loopback(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_test_uwb_loopback(): p_data is NULL)");
  } else {
    status = UWB_TestUwbLoopBack(p_data->sUwb_loopback.length,
                                 p_data->sUwb_loopback.p_data);

    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_test_uwb_loopback(): success ,status=0x%X",
                  status);
    } else {
      UCI_TRACE_E("uwa_dm_act_test_uwb_loopback(): failed ,status=0x%X",
                  status);
    }
  }
  return (true);
}

/*******************************************************************************
**
** Function         uwa_dm_act_test_rx
**
** Description      Send Test Rx Command
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_test_rx(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_test_rx(): p_data is NULL)");
    return false;
  } else {
    status = UWB_TestRx();
    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_test_rx(): success , status=0x%X", status);
    } else {
      UCI_TRACE_E("uwa_dm_act_test_rx(): failed , status=0x%X", status);
    }
  }
  return true;
}

/*******************************************************************************
**
** Function         uwa_dm_act_test_stop_session
**
** Description      send test stop session command
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool uwa_dm_act_test_stop_session(tUWA_DM_MSG* p_data) {
  tUWB_STATUS status;
  if (p_data == NULL) {
    UCI_TRACE_E("uwa_dm_act_test_stop_session(): p_data is NULL)");
    return false;
  } else {
    status = UWB_TestStopSession();
    if (UWB_STATUS_OK == status) {
      UCI_TRACE_I("uwa_dm_act_test_stop_session(): success , status=0x%X",
                  status);
    } else {
      UCI_TRACE_E("uwa_dm_act_test_stop_session(): failed , status=0x%X",
                  status);
    }
  }
  return true;
}

/*******************************************************************************
**
** Function         uwa_dm_uwb_revt_2_str
**
** Description      convert uwb revt to string
**
*******************************************************************************/
std::string uwa_dm_uwb_revt_2_str(tUWB_RESPONSE_EVT event) {
  switch (event) {
    case UWB_ENABLE_REVT:
      return "UWB_ENABLE_REVT";

    case UWB_DISABLE_REVT:
      return "UWB_DISABLE_REVT";

    case UWB_DEVICE_STATUS_REVT:
      return "UWB_DEVICE_STATUS_REVT";

    case UWB_GET_DEVICE_INFO_REVT:
      return "UWB_GET_DEVICE_INFO_REVT";

    case UWB_SET_CORE_CONFIG_REVT:
      return "UWB_SET_CORE_CONFIG_REVT";

    case UWB_GET_CORE_CONFIG_REVT:
      return "UWB_GET_CORE_CONFIG_REVT";

    case UWB_DEVICE_RESET_REVT:
      return "UWB_DEVICE_RESET_REVT";

    case UWB_CORE_GEN_ERR_STATUS_REVT:
      return "UWB_CORE_GEN_ERR_STATUS_REVT";

    case UWB_SESSION_INIT_REVT:
      return "UWB_SESSION_INIT_REVT";

    case UWB_SESSION_DEINIT_REVT:
      return "UWB_SESSION_DEINIT_REVT";

    case UWB_SESSION_STATUS_NTF_REVT:
      return "UWB_SESSION_STATUS_NTF_REVT";

    case UWB_SESSION_GET_COUNT_REVT:
      return "UWB_SESSION_GET_COUNT_REVT";

    case UWB_SESSION_GET_STATE_REVT:
      return "UWB_SESSION_GET_STATE_REVT";

    case UWB_GET_APP_CONFIG_REVT:
      return "UWB_GET_APP_CONFIG_REVT";

    case UWB_SET_APP_CONFIG_REVT:
      return "UWB_SET_APP_CONFIG_REVT";

    case UWB_START_RANGE_REVT:
      return "UWB_START_RANGE_REVT";

    case UWB_STOP_RANGE_REVT:
      return "UWB_STOP_RANGE_REVT";

    case UWB_RANGE_DATA_REVT:
      return "UWB_RANGE_DATA_REVT";

    case UWB_GET_RANGE_COUNT_REVT:
      return "UWB_GET_RANGE_COUNT_REVT";

    case UWB_CORE_GET_DEVICE_CAPABILITY_REVT:
      return "UWB_CORE_GET_DEVICE_CAPABILITY_REVT";

    case UWB_SESSION_UPDATE_MULTICAST_LIST_REVT:
      return "UWB_SESSION_UPDATE_MULTICAST_LIST_REVT";

    case UWB_SESSION_UPDATE_MULTICAST_LIST_NTF_REVT:
      return "UWB_SESSION_UPDATE_MULTICAST_LIST_NTF_REVT";

    case UWB_SET_COUNTRY_CODE_REVT:
      return "UWB_SET_COUNTRY_CODE_REVT";

    case UWB_BLINK_DATA_TX_REVT:
      return "UWB_BLINK_DATA_TX_REVT";

    case UWB_BLINK_DATA_TX_NTF_REVT:
      return "UWB_BLINK_DATA_TX_NTF_REVT";

    case UWB_CONFORMANCE_TEST_DATA:
      return "UWB_CONFORMANCE_TEST_DATA";

    case UWB_VENDOR_SPECIFIC_UCI_NTF_EVT:
      return "UWB_VENDOR_SPECIfIC_UCI_NTF_EVT";

    default:
      return "unknown revt";
      break;
  }
}

/*******************************************************************************
**
** Function         uwa_test_dm_uwb_revt_2_str
**
** Description      convert uwb revt to string for RF test events
**
*******************************************************************************/
std::string uwa_test_dm_uwb_revt_2_str(tUWB_TEST_RESPONSE_EVT event) {
  switch (event) {
    case UWB_TEST_GET_CONFIG_REVT:
      return "UWB_TEST_GET_CONFIG_REVT";

    case UWB_TEST_SET_CONFIG_REVT:
      return "UWB_TEST_SET_CONFIG_REVT";

    case UWB_TEST_PERIODIC_TX_DATA_REVT:
      return "UWB_TEST_PERIODIC_TX_DATA_REVT";

    case UWB_TEST_PER_RX_DATA_REVT:
      return "UWB_TEST_PER_RX_DATA_REVT";

    case UWB_TEST_PERIODIC_TX_REVT:
      return "UWB_TEST_PERIODIC_TX_REVT";

    case UWB_TEST_PER_RX_REVT:
      return "UWB_TEST_PER_RX_REVT";

    case UWB_TEST_STOP_SESSION_REVT:
      return "UWB_TEST_STOP_SESSION_REVT";

    case UWB_TEST_LOOPBACK_DATA_REVT:
      return "UWB_TEST_LOOPBACK_DATA_REVT";

    case UWB_TEST_LOOPBACK_REVT:
      return "UWB_TEST_LOOPBACK_REVT";

    case UWB_TEST_RX_REVT:
      return "UWB_TEST_RX_REVT";

    case UWB_TEST_RX_DATA_REVT:
      return "UWB_TEST_RX_DATA_REVT";

    default:
      return "unknown revt";
      break;
  }
}

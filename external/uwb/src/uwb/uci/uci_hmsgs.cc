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

/******************************************************************************
 *
 *  This file contains function of the UCI unit to format and send UCI
 *  commands(for DH).
 *
 ******************************************************************************/
#include "uci_hmsgs.h"

#include "uci_defs.h"
#include "uci_log.h"
#include "uci_test_defs.h"
#include "uwb_api.h"
#include "uwb_int.h"
#include "uwb_osal_common.h"
#include "uwb_target.h"

/*******************************************************************************
**
** Function         uci_snd_get_device_info_cmd
**
** Description      compose and send CORE_GET_DEVICE_INFO_CMD command to command
**                  queue
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_get_device_info_cmd(void) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(UCI_MSG_CORE_DEVICE_INFO_CMD_SIZE)) == NULL)
    return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_CORE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_CORE_DEVICE_INFO);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_CORE_DEVICE_INFO_CMD_SIZE);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_device_reset_cmd
**
** Description      compose and send CORE_DEVICE_RESET_CMD command to command
**                  queue
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_device_reset_cmd(uint8_t resetConfig) {
  UWB_HDR* p;
  uint8_t* pp;
  p = UCI_GET_CMD_BUF(UCI_MSG_CORE_DEVICE_RESET_CMD_SIZE);
  if (p == NULL) return (UCI_STATUS_FAILED);

  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE + UCI_MSG_CORE_DEVICE_RESET_CMD_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_CORE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_CORE_DEVICE_RESET);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_CORE_DEVICE_RESET_CMD_SIZE);
  UINT8_TO_STREAM(pp, resetConfig);
  uwb_ucif_send_cmd(p);

  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_core_set_config_cmd
**
** Description      compose and send CORE CORE_SET_CONFIG_CMD command to command
**                  queue
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_core_set_config_cmd(uint8_t* p_param_tlvs, uint8_t tlv_size) {
  UWB_HDR* p;
  uint8_t* pp;
  uint8_t num = 0, ulen, len, *pt;

  p = UCI_GET_CMD_BUF(tlv_size + 1);
  if (p == NULL) return (UCI_STATUS_FAILED);

  p->event = BT_EVT_TO_UWB_UCI;
  p->len = (uint16_t)(UCI_MSG_HDR_SIZE + tlv_size + 1);
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_CORE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_CORE_SET_CONFIG);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, (uint8_t)(tlv_size + 1));
  len = tlv_size;
  pt = p_param_tlvs;
  while (len > 1) {
    len = (uint8_t)(len - 2);
    pt++;
    num++;
    ulen = *pt++;
    pt += ulen;
    if (len >= ulen) {
      len = (uint8_t)(len - ulen);
    } else {
      phUwb_GKI_freebuf(p);
      return UCI_STATUS_FAILED;
    }
  }

  UINT8_TO_STREAM(pp, num);
  ARRAY_TO_STREAM(pp, p_param_tlvs, tlv_size);
  uwb_ucif_send_cmd(p);

  return (UCI_STATUS_OK);
}
/*******************************************************************************
**
** Function         uci_snd_core_get_config_cmd
**
** Description      compose and send CORE_GET_CONFIG_CMD command to command
**                  queue
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_core_get_config_cmd(uint8_t* param_ids, uint8_t num_ids) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(num_ids)) == NULL) return (UCI_STATUS_FAILED);

  p->event = BT_EVT_TO_UWB_UCI;

  p->len = (uint16_t)(UCI_MSG_HDR_SIZE + num_ids + 1);

  p->offset = UCI_MSG_OFFSET_SIZE;
  p->layer_specific = 0;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_CORE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_CORE_GET_CONFIG);
  UINT8_TO_STREAM(pp, 0x00);

  UINT8_TO_STREAM(pp, (uint8_t)(num_ids + 1));
  UINT8_TO_STREAM(pp, num_ids);
  ARRAY_TO_STREAM(pp, param_ids, num_ids);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_session_init_cmd
**
** Description      compose and send SESSION_INIT_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_session_init_cmd(uint32_t session_id, uint8_t sessionType) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(UCI_MSG_SESSION_INIT_CMD_SIZE)) == NULL)
    return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE + UCI_MSG_SESSION_INIT_CMD_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_SESSION_MANAGE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_SESSION_INIT);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_SESSION_INIT_CMD_SIZE);
  UINT32_TO_STREAM(pp, session_id);
  UINT8_TO_STREAM(pp, sessionType);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_session_deinit_cmd
**
** Description      compose and send SESSION_DEINIT_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_session_deinit_cmd(uint32_t session_id) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(UCI_MSG_SESSION_DEINIT_CMD_SIZE)) == NULL)
    return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE + UCI_MSG_SESSION_DEINIT_CMD_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_SESSION_MANAGE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_SESSION_DEINIT);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_SESSION_DEINIT_CMD_SIZE);
  UINT32_TO_STREAM(pp, session_id);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_app_set_config_cmd
**
** Description      compose and send SESSION_SET_APP_CONFIG_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_app_set_config_cmd(uint32_t session_id, uint8_t num_ids,
                                   uint8_t length, uint8_t* data) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(num_ids)) == NULL) return (UCI_STATUS_FAILED);

  p->event = BT_EVT_TO_UWB_UCI;

  p->len = (uint16_t)(UCI_MSG_HDR_SIZE + sizeof(session_id) + sizeof(num_ids) +
                      length);

  p->offset = UCI_MSG_OFFSET_SIZE;
  p->layer_specific = 0;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_SESSION_MANAGE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_SESSION_SET_APP_CONFIG);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, sizeof(session_id) + sizeof(num_ids) + length);

  UINT32_TO_STREAM(pp, session_id);
  UINT8_TO_STREAM(pp, num_ids);
  ARRAY_TO_STREAM(pp, data, length);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_app_get_config_cmd
**
** Description      compose and send SESSION_GET_APP_CONFIG_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_app_get_config_cmd(uint32_t session_id, uint8_t num_ids,
                                   uint8_t length, uint8_t* param_ids) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(length)) == NULL) return (UCI_STATUS_FAILED);

  p->event = BT_EVT_TO_UWB_UCI;

  p->len = (uint16_t)(UCI_MSG_HDR_SIZE + sizeof(session_id) + sizeof(num_ids) +
                      length);

  p->offset = UCI_MSG_OFFSET_SIZE;
  p->layer_specific = 0;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_SESSION_MANAGE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_SESSION_GET_APP_CONFIG);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, sizeof(session_id) + sizeof(num_ids) + length);

  UINT32_TO_STREAM(pp, session_id);
  UINT8_TO_STREAM(pp, num_ids);
  ARRAY_TO_STREAM(pp, param_ids, length);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_get_session_count_cmd
**
** Description      compose and send SESSION_GET_COUNT_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_get_session_count_cmd(void) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(UCI_MSG_SESSION_GET_COUNT_CMD_SIZE)) == NULL)
    return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_SESSION_MANAGE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_SESSION_GET_COUNT);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_SESSION_GET_COUNT_CMD_SIZE);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_get_session_status_cmd
**
** Description      compose and send SESSION_GET_STATE_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_get_session_status_cmd(uint32_t session_id) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(UCI_MSG_SESSION_GET_STATE_SIZE)) == NULL)
    return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE + UCI_MSG_SESSION_GET_STATE_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_SESSION_MANAGE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_SESSION_GET_STATE);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_SESSION_GET_STATE_SIZE);
  UINT32_TO_STREAM(pp, session_id);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_get_range_count_cmd
**
** Description      compose and send RANGE_GET_RANGING_COUNT_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_get_range_count_cmd(uint32_t session_id) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(UCI_MSG_RANGE_GET_COUNT_CMD_SIZE)) == NULL)
    return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE + UCI_MSG_RANGE_GET_COUNT_CMD_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_RANGE_MANAGE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_RANGE_GET_RANGING_COUNT);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_RANGE_GET_COUNT_CMD_SIZE);
  UINT32_TO_STREAM(pp, session_id);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}
/*******************************************************************************
**
** Function         uci_snd_range_start_cmd
**
** Description      compose and send RANGE_START_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_range_start_cmd(uint32_t session_id) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(UCI_MSG_RANGE_START_CMD_SIZE)) == NULL)
    return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE + UCI_MSG_RANGE_START_CMD_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_RANGE_MANAGE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_RANGE_START);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_RANGE_START_CMD_SIZE);
  UINT32_TO_STREAM(pp, session_id);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_range_stop_cmd
**
** Description      compose and send RANGE_STOP_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_range_stop_cmd(uint32_t session_id) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(UCI_MSG_RANGE_STOP_CMD_SIZE)) == NULL)
    return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE + UCI_MSG_RANGE_STOP_CMD_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_RANGE_MANAGE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_RANGE_STOP);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_RANGE_STOP_CMD_SIZE);
  UINT32_TO_STREAM(pp, session_id);
  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_core_get_device_capability
**
** Description      compose and send CORE_GET_CAPS_INFO_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_core_get_device_capability(void) {
  UWB_HDR* p;
  uint8_t* pp;
  p = UCI_GET_CMD_BUF(UCI_MSG_CORE_GET_CAPS_INFO_CMD_SIZE);
  if (p == NULL) return (UCI_STATUS_FAILED);

  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE + UCI_MSG_CORE_GET_CAPS_INFO_CMD_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_CORE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_CORE_GET_CAPS_INFO);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_CORE_GET_CAPS_INFO_CMD_SIZE);
  uwb_ucif_send_cmd(p);

  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_multicast_list_update_cmd
**
** Description      compose and send SESSION_UPDATE_CONTROLLER
**                   _MULTICAST_LIST_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_multicast_list_update_cmd(uint32_t session_id, uint8_t action,
                                          uint8_t noOfControlees,
                                          uint16_t* shortAddressList,
                                          uint32_t* subSessionIdList) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(noOfControlees)) == NULL) return (UCI_STATUS_FAILED);

  p->event = BT_EVT_TO_UWB_UCI;

  p->len =
      (uint16_t)(UCI_MSG_HDR_SIZE + sizeof(session_id) + sizeof(action) +
                 sizeof(noOfControlees) + (noOfControlees * SHORT_ADDRESS_LEN) +
                 (noOfControlees * SESSION_ID_LEN));

  p->offset = UCI_MSG_OFFSET_SIZE;
  p->layer_specific = 0;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_SESSION_MANAGE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_SESSION_UPDATE_CONTROLLER_MULTICAST_LIST);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, sizeof(session_id) + sizeof(action) +
                          sizeof(noOfControlees) +
                          (noOfControlees * SHORT_ADDRESS_LEN) +
                          (noOfControlees * SESSION_ID_LEN));

  UINT32_TO_STREAM(pp, session_id);
  UINT8_TO_STREAM(pp, action);
  UINT8_TO_STREAM(pp, noOfControlees);
  for (uint8_t i = 0; i < noOfControlees; i++) {
    UINT16_TO_BE_STREAM(pp, shortAddressList[i]);
    UINT32_TO_STREAM(pp, subSessionIdList[i]);
  }

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_set_country_code_cmd
**
** Description      compose and send SET_COUNTRY_CODE_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_set_country_code_cmd(uint8_t *country_code) {
  UWB_HDR* p;
  uint8_t* pp;
  p = UCI_GET_CMD_BUF(UCI_MSG_ANDROID_SET_COUNTRY_CODE_CMD_SIZE);
  if (p == NULL) return (UCI_STATUS_FAILED);

  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE + UCI_MSG_ANDROID_SET_COUNTRY_CODE_CMD_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_ANDROID);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_ANDROID_SET_COUNTRY_CODE);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_ANDROID_SET_COUNTRY_CODE_CMD_SIZE);
  ARRAY8_TO_STREAM(pp, country_code);
  uwb_ucif_send_cmd(p);

  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_blink_data_cmd
**
** Description      compose and send BLINK_DATA_TX_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_blink_data_cmd(uint32_t session_id, uint8_t repetition_count,
                               uint8_t app_data_len, uint8_t* app_data) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(sizeof(session_id) + sizeof(repetition_count) +
                           sizeof(app_data_len) + app_data_len)) == NULL)
    return (UCI_STATUS_FAILED);

  p->event = BT_EVT_TO_UWB_UCI;

  p->len = (uint16_t)(UCI_MSG_HDR_SIZE + sizeof(session_id) +
                      sizeof(repetition_count) + sizeof(app_data_len) +
                      app_data_len);

  p->offset = UCI_MSG_OFFSET_SIZE;
  p->layer_specific = 0;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_RANGE_MANAGE);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_RANGE_BLINK_DATA_TX);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, sizeof(session_id) + sizeof(repetition_count) +
                          sizeof(app_data_len) + app_data_len);

  UINT32_TO_STREAM(pp, session_id);
  UINT8_TO_STREAM(pp, repetition_count);
  UINT8_TO_STREAM(pp, app_data_len);
  if ((app_data_len > 0) && (app_data != NULL)) {
    ARRAY_TO_STREAM(pp, app_data, app_data_len);
  }

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*  APIs for UWB RF test functionality */

/*******************************************************************************
**
** Function         uci_snd_test_set_config_cmd
**
** Description      compose and send TEST_CONFIG_SET_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_test_set_config_cmd(uint32_t session_id, uint8_t num_ids,
                                    uint8_t length, uint8_t* data) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(num_ids)) == NULL) return (UCI_STATUS_FAILED);

  p->event = BT_EVT_TO_UWB_UCI;

  p->len = (uint16_t)(UCI_MSG_HDR_SIZE + sizeof(session_id) + sizeof(num_ids) +
                      length);

  p->offset = UCI_MSG_OFFSET_SIZE;
  p->layer_specific = 0;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_TEST);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_TEST_SET_CONFIG);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, sizeof(session_id) + sizeof(num_ids) + length);

  UINT32_TO_STREAM(pp, session_id);
  UINT8_TO_STREAM(pp, num_ids);
  ARRAY_TO_STREAM(pp, data, length);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_test_get_config_cmd
**
** Description      compose and send TEST_CONFIG_GET_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_test_get_config_cmd(uint32_t session_id, uint8_t num_ids,
                                    uint8_t length, uint8_t* param_ids) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(length)) == NULL) return (UCI_STATUS_FAILED);

  p->event = BT_EVT_TO_UWB_UCI;

  p->len = (uint16_t)(UCI_MSG_HDR_SIZE + sizeof(session_id) + sizeof(num_ids) +
                      length);

  p->offset = UCI_MSG_OFFSET_SIZE;
  p->layer_specific = 0;
  pp = (uint8_t*)(p + 1) + p->offset;

  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_TEST);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_TEST_GET_CONFIG);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, sizeof(session_id) + sizeof(num_ids) + length);

  UINT32_TO_STREAM(pp, session_id);
  UINT8_TO_STREAM(pp, num_ids);
  ARRAY_TO_STREAM(pp, param_ids, length);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_test_periodic_tx_cmd
**
** Description      compose and send TEST_PERIODIC_TX_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_test_periodic_tx_cmd(uint16_t psduLen, uint8_t* psduData) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(psduLen)) == NULL) return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = (uint16_t)(UCI_MSG_HDR_SIZE + psduLen);
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_TEST);
  if (psduLen <= UCI_MAX_PAYLOAD_SIZE) {
    UCI_MSG_BLD_HDR1(pp, UCI_MSG_TEST_PERIODIC_TX);
    UINT8_TO_STREAM(pp, 0x00);
    UINT8_TO_STREAM(pp, psduLen);
  } else { /* extended len apdu */
    UCI_MSG_BLD_HDR1(pp, (1 << 7) | UCI_MSG_TEST_PERIODIC_TX);
    UINT8_TO_STREAM(pp, psduLen & 0x00FF);
    UINT8_TO_STREAM(pp, (psduLen & 0xFF00) >> 8);
  }
  if ((psduLen > 0) && (psduData != NULL)) {
    ARRAY_TO_STREAM(pp, psduData, psduLen);
  }

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_test_per_rx_cmd
**
** Description      compose and send TEST_PER_RX_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_test_per_rx_cmd(uint16_t psduLen, uint8_t* psduData) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(psduLen)) == NULL) return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = (uint16_t)(UCI_MSG_HDR_SIZE + psduLen);
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_TEST);
  if (psduLen <= UCI_MAX_PAYLOAD_SIZE) {
    UCI_MSG_BLD_HDR1(pp, UCI_MSG_TEST_PER_RX);
    UINT8_TO_STREAM(pp, 0x00);
    UINT8_TO_STREAM(pp, psduLen);
  } else { /* extended len apdu */
    UCI_MSG_BLD_HDR1(pp, (1 << 7) | UCI_MSG_TEST_PER_RX);
    UINT8_TO_STREAM(pp, psduLen & 0x00FF);
    UINT8_TO_STREAM(pp, (psduLen & 0xFF00) >> 8);
  }
  if ((psduLen > 0) && (psduData != NULL)) {
    ARRAY_TO_STREAM(pp, psduData, psduLen);
  }

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_test_uwb_loopback_cmd
**
** Description      compose and send TEST_LOOPBACK_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_test_uwb_loopback_cmd(uint16_t psduLen, uint8_t* psduData) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(psduLen)) == NULL) return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = (uint16_t)(UCI_MSG_HDR_SIZE + psduLen);
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_TEST);
  if (psduLen <= UCI_MAX_PAYLOAD_SIZE) {
    UCI_MSG_BLD_HDR1(pp, UCI_MSG_TEST_LOOPBACK);
    UINT8_TO_STREAM(pp, 0x00);
    UINT8_TO_STREAM(pp, psduLen);
  } else { /* extended len apdu */
    UCI_MSG_BLD_HDR1(pp, (1 << 7) | UCI_MSG_TEST_LOOPBACK);
    UINT8_TO_STREAM(pp, psduLen & 0x00FF);
    UINT8_TO_STREAM(pp, (psduLen & 0xFF00) >> 8);
  }
  if ((psduLen > 0) && (psduData != NULL)) {
    ARRAY_TO_STREAM(pp, psduData, psduLen);
  }

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_test_rx_cmd
**
** Description      compose and send TEST_RX_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_test_rx_cmd(void) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(UCI_MSG_TEST_RX_CMD_SIZE)) == NULL)
    return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_TEST);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_TEST_RX);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_TEST_RX_CMD_SIZE);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

/*******************************************************************************
**
** Function         uci_snd_test_stop_session_cmd
**
** Description      compose and send TEST_STOP_SESSION_CMD command
**
** Returns          status
**
*******************************************************************************/
uint8_t uci_snd_test_stop_session_cmd(void) {
  UWB_HDR* p;
  uint8_t* pp;

  if ((p = UCI_GET_CMD_BUF(UCI_MSG_TEST_STOP_SESSION_CMD_SIZE)) == NULL)
    return (UCI_STATUS_FAILED);
  p->event = BT_EVT_TO_UWB_UCI;
  p->len = UCI_MSG_HDR_SIZE;
  p->offset = UCI_MSG_OFFSET_SIZE;
  pp = (uint8_t*)(p + 1) + p->offset;
  UCI_MSG_BLD_HDR0(pp, UCI_MT_CMD, UCI_GID_TEST);
  UCI_MSG_BLD_HDR1(pp, UCI_MSG_TEST_STOP_SESSION);
  UINT8_TO_STREAM(pp, 0x00);
  UINT8_TO_STREAM(pp, UCI_MSG_TEST_STOP_SESSION_CMD_SIZE);

  uwb_ucif_send_cmd(p);
  return (UCI_STATUS_OK);
}

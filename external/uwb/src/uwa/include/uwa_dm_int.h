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

#ifndef UWA_DM_INT_H
#define UWA_DM_INT_H

#include <string.h>

#include "uwa_api.h"
#include "uwa_sys.h"
#include "uwb_api.h"

/*****************************************************************************
**  Constants and data types
*****************************************************************************/

/* UWA_DM flags */
/* DM is enabled                                                        */
#define UWA_DM_FLAGS_DM_IS_ACTIVE 0x00000001
#define UWA_DM_FLAGS_ENABLE_EVT_PEND 0x00000002

/* DM events */
enum {
  /* device manager local device API events */
  UWA_DM_API_ENABLE_EVT = UWA_SYS_EVT_START(UWA_ID_DM),
  UWA_DM_API_DISABLE_EVT,
  UWA_DM_API_GET_DEVICE_INFO_EVT,
  UWA_DM_API_SET_CORE_CONFIG_EVT,
  UWA_DM_API_GET_CORE_CONFIG_EVT,
  UWA_DM_API_DEVICE_RESET_EVT,
  UWA_DM_API_SESSION_INIT_EVT,
  UWA_DM_API_SESSION_DEINIT_EVT,
  UWA_DM_API_SESSION_GET_COUNT_EVT,
  UWA_DM_API_SET_APP_CONFIG_EVT,
  UWA_DM_API_GET_APP_CONFIG_EVT,
  UWA_DM_API_START_RANGE_EVT,
  UWA_DM_API_STOP_RANGE_EVT,
  UWA_DM_API_SEND_RAW_EVT,
  UWA_DM_API_GET_RANGE_COUNT_EVT,
  UWA_DM_API_GET_SESSION_STATUS_EVT,
  UWA_DM_API_CORE_GET_DEVICE_CAPABILITY_EVT,
  UWA_DM_API_SESSION_UPDATE_MULTICAST_LIST_EVT,
  UWA_DM_API_SET_COUNTRY_CODE_EVT,
  UWA_DM_API_SEND_BLINK_DATA_EVT,
  /*    UWB RF Test API events   */
  UWA_DM_API_TEST_SET_CONFIG_EVT,
  UWA_DM_API_TEST_GET_CONFIG_EVT,
  UWA_DM_API_TEST_PERIODIC_TX_EVT,
  UWA_DM_API_TEST_PER_RX_EVT,
  UWA_DM_API_TEST_UWB_LOOPBACK_EVT,
  UWA_DM_API_TEST_RX_EVT,
  UWA_DM_API_TEST_STOP_SESSION_EVT,
  /* UWB Data packet events */
  UWA_DM_MAX_EVT
};

/* data type for UWA_DM_API_ENABLE_EVT */
typedef struct {
  UWB_HDR hdr;
  tUWA_DM_CBACK* p_dm_cback;
  tUWA_DM_TEST_CBACK* p_dm_test_cback;
} tUWA_DM_API_ENABLE;

/* data type for UWA_DM_API_DISABLE_EVT */
typedef struct {
  UWB_HDR hdr;
  bool graceful;
} tUWA_DM_API_DISABLE;

/* data type for UWA_DM_API_SET_CORE_CONFIG_EVT */
typedef struct {
  UWB_HDR hdr;
  tUWA_PMID param_id;
  uint8_t length;
  uint8_t* p_data;
} tUWA_DM_API_CORE_SET_CONFIG;

/* data type for UWA_DM_API_SET_APP_CONFIG_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id;
  uint8_t num_ids;
  uint8_t length;
  uint8_t* p_data;
} tUWA_DM_API_SET_APP_CONFIG;

/* data type for UWA_DM_API_GET_CORE_CONFIG_EVT */
typedef struct {
  UWB_HDR hdr;
  uint8_t num_ids;
  tUWA_PMID* p_pmids;
} tUWA_DM_API_CORE_GET_CONFIG;

/* data type for UWA_DM_API_GET_APP_CONFIG_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id;
  uint8_t num_ids;
  uint8_t length;
  uint8_t* p_pmids;
} tUWA_DM_API_GET_APP_CONFIG;

/* data type for UWA_DM_API_DEVICE_RESET_EVT */
typedef struct {
  UWB_HDR hdr;
  uint8_t resetConfig; /* Vendor Specific Reset Config*/
} tUWA_DM_API_DEVICE_RESET;

/* data type for UWA_DM_API_SEND_RAW_EVT */
typedef struct {
  UWB_HDR hdr;
  tUWA_RAW_CMD_CBACK* p_cback;
  uint8_t oid;
  uint16_t cmd_params_len;
  uint8_t* p_cmd_params;
} tUWA_DM_API_SEND_RAW;

/* data type for UWA_DM_API_START_RANGE_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id; /* Session ID for which ranging shall start */
} tUWA_DM_API_RANGING_START;

/* data type for UWA_DM_API_STOP_RANGE_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id; /* Session ID for which ranging shall stop */
} tUWA_DM_API_RANGING_STOP;

/* data type for UWA_DM_API_SESSION_GET_COUNT_EVT */
typedef struct {
  UWB_HDR hdr;
} tUWA_DM_API_GET_SESSION_COUNT;

/* data type for UWA_DM_API_SESSION_GET_COUNT_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id;
} tUWA_DM_API_GET_RANGING_COUNT;

/* data type for UWA_DM_API_SESSION_GET_STATUS_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id;
} tUWA_DM_API_GET_SESSION_STATUS;

/* data type for UWA_DM_API_SESSION_INIT_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id; /* session_id for Particular Activity */
  uint8_t sessionType; /* session type for Particular Activity */
} tUWA_DM_API_SESSION_INIT;

/* data type for UWA_DM_API_SESSION_DEINIT_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id; /* session_id for Particular activity */
} tUWA_DM_API_SESSION_DEINIT;

/* data type for UWA_DM_API_GET_DEVICE_INFO_EVT */
typedef struct {
  UWB_HDR hdr;
} tUWA_DM_API_GET_DEVICE_INFO;

/* data type for UWA_DM_API_CORE_GET_DEVICE_CAPABILITY_EVT */
typedef struct {
  UWB_HDR hdr;
} tUWA_DM_API_CORE_GET_DEVICE_CAPABILITY;

/* data type for UWA_DM_API_SESSION_UPDATE_MULTICAST_LIST_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id;
  uint8_t action;
  uint8_t no_of_controlee;
  uint16_t short_address_list[MAX_NUM_CONTROLLEES];
  uint32_t subsession_id_list[MAX_NUM_CONTROLLEES];
} tUWA_DM_API_SESSION_UPDATE_MULTICAST_LIST;

/* data type for UWA_DM_API_SESSION_UPDATE_MULTICAST_LIST_EVT */
typedef struct {
  UWB_HDR hdr;
  uint8_t country_code[COUNTRY_CODE_ARRAY_LEN];
} tUWA_DM_API_SET_COUNTRY_CODE;

/* data type for UWA_DM_API_DM_API_SEND_BLINK_DATA_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id;
  uint8_t repetition_count;
  uint8_t app_data_len;
  uint8_t app_data[UCI_MAX_PAYLOAD_SIZE];
} tUWA_DM_API_SEND_BLINK_DATA;

/* data type for UWA_DM_API_TEST_SET_CONFIG_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id;
  uint8_t num_ids;
  uint8_t length;
  uint8_t* p_data;
} tUWA_DM_API_TEST_SET_CONFIG;

/* data type for UWA_DM_API_TEST_GET_CONFIG_EVT */
typedef struct {
  UWB_HDR hdr;
  uint32_t session_id;
  uint8_t num_ids;
  uint8_t length;
  uint8_t* p_pmids;
} tUWA_DM_API_TEST_GET_CONFIG;

/* data type for UWA_DM_API_TEST_PERIODIC_TX_EVT */
typedef struct {
  UWB_HDR hdr;
  uint16_t length;
  uint8_t* p_data;
} tUWA_DM_API_TEST_PERIODIC_TX;

/* data type for UWA_DM_API_TEST_PER_RX_EVT */
typedef struct {
  UWB_HDR hdr;
  uint16_t length;
  uint8_t* p_data;
} tUWA_DM_API_TEST_PER_RX;

/* data type for UWA_DM_API_TEST_UWB_LOOPBACK_EVT */
typedef struct {
  UWB_HDR hdr;
  uint16_t length;
  uint8_t* p_data;
} tUWA_DM_API_TEST_UWB_LOOPBACK;

/* data type for UWA_DM_API_TEST_RX_EVT */
typedef struct {
  UWB_HDR hdr;
} tUWA_DM_API_TEST_RX;

/* data type for UWA_DM_API_TEST_STOP_SESSION_EVT */
typedef struct {
  UWB_HDR hdr;
} tUWA_DM_API_TEST_STOP_SESSION;

/* union of all data types */
typedef union {
  /* GKI event buffer header */
  UWB_HDR hdr;
  tUWA_DM_API_ENABLE enable;   /* UWA_DM_API_ENABLE_EVT           */
  tUWA_DM_API_DISABLE disable; /* UWA_DM_API_DISABLE_EVT          */
  tUWA_DM_API_GET_DEVICE_INFO
      sGet_device_info;                   /* UWA_DM_API_GET_DEVICE_INFO      */
  tUWA_DM_API_DEVICE_RESET sDevice_reset; /* UWA_DM_API_DEVICE_RESET_EVT */
  tUWA_DM_API_CORE_SET_CONFIG setconfig;  /* UWA_DM_API_SET_CORE_CONFIG_EVT  */
  tUWA_DM_API_CORE_GET_CONFIG getconfig;  /* UWA_DM_API_GET_CORE_CONFIG_EVT  */
  tUWA_DM_API_SESSION_INIT sessionInit;   /* UWA_DM_API_SESSION_INIT         */
  tUWA_DM_API_SESSION_DEINIT sessionDeInit; /* UWA_DM_API_SESSION_DEINIT */
  tUWA_DM_API_GET_SESSION_COUNT
      sGet_session_cnt; /* UWA_DM_API_SESSION_GET_COUNT_EVT*/
  tUWA_DM_API_GET_APP_CONFIG
      sApp_get_config; /* UWA_DM_API_GET_CORE_CONFIG_EVT       */
  tUWA_DM_API_SET_APP_CONFIG
      sApp_set_config; /* UWA_DM_API_SET_CORE_CONFIG_EVT       */
  tUWA_DM_API_RANGING_START rang_start; /* UWA_DM_API_START_RANGE_EVT        */
  tUWA_DM_API_RANGING_STOP rang_stop;   /* UWA_DM_API_STOP_RANGE_EVT         */
  tUWA_DM_API_SEND_RAW send_raw;        /* UWA_DM_API_SEND_RAW_EVT         */
  tUWA_DM_API_GET_RANGING_COUNT
      sGet_rang_count; /* UWA_DM_API_GET_RANGE_COUNT_EVT         */
  tUWA_DM_API_GET_SESSION_STATUS
      sGet_session_status; /* UWA_DM_API_GET_SESSION_STATUS_EVT         */
  tUWA_DM_API_CORE_GET_DEVICE_CAPABILITY
      get_device_capability; /* UWA_DM_API_CORE_GET_DEVICE_CAPABILITY_EVT  */
  tUWA_DM_API_TEST_UWB_LOOPBACK
      sUwb_loopback; /* UWA_DM_API_TEST_UWB_LOOPBACK_EVT  */
  tUWA_DM_API_SESSION_UPDATE_MULTICAST_LIST
      sMulticast_list; /* UWA_DM_API_SESSION_UPDATE_MULTICAST_LIST_EVT */
  tUWA_DM_API_SET_COUNTRY_CODE
      sCountryCode; /* UWA_DM_API_SET_COUNTRY_CODE_EVT */
  tUWA_DM_API_SEND_BLINK_DATA
      sSend_blink_data; /* UWA_DM_API_SEND_BLINK_DATA_EVT */
                        /*  data types for all UWB RF TEST events */
  tUWA_DM_API_TEST_GET_CONFIG
      sTest_get_config; /* UWA_DM_API_TEST_GET_CONFIG_EVT       */
  tUWA_DM_API_TEST_SET_CONFIG
      sTest_set_config; /* UWA_DM_API_TEST_SET_CONFIG_EVT       */
  tUWA_DM_API_TEST_PERIODIC_TX
      sPeriodic_tx; /* UWA_DM_API_TEST_PERIODIC_TX_EVT            */
  tUWA_DM_API_TEST_PER_RX sPer_rx; /* UWA_DM_API_TEST_PER_RX_EVT            */
  tUWA_DM_API_TEST_RX sTest_rx;    /*UWA_DM_API_TEST_RX_EVT*/
  tUWA_DM_API_TEST_STOP_SESSION
      sTest_stop_session; /* UWA_DM_API_TEST_STOP_SESSION_EVT     */
} tUWA_DM_MSG;

typedef struct {
  uint32_t flags; /* UWA_DM flags (see definitions for UWA_DM_FLAGS_*)    */
  tUWA_DM_CBACK* p_dm_cback;           /* UWA DM callback */
  tUWA_DM_TEST_CBACK* p_dm_test_cback; /* UWA DM callback for RF test events */
  TIMER_LIST_ENT tle;
} tUWA_DM_CB;

tUWA_STATUS uwa_rw_send_raw_frame(UWB_HDR* p_data);
void uwa_dm_disable_complete(void);

/* UWA device manager control block */
extern tUWA_DM_CB uwa_dm_cb;

void uwa_dm_init(void);

/* Action function prototypes */
bool uwa_dm_enable(tUWA_DM_MSG* p_data);
bool uwa_dm_disable(tUWA_DM_MSG* p_data);
bool uwa_dm_act_get_device_info(tUWA_DM_MSG* p_data);
bool uwa_dm_act_device_reset(tUWA_DM_MSG* p_data);
bool uwa_dm_set_core_config(tUWA_DM_MSG* p_data);
bool uwa_dm_get_core_config(tUWA_DM_MSG* p_data);
bool uwa_dm_act_send_session_init(tUWA_DM_MSG* p_data);
bool uwa_dm_act_send_session_deinit(tUWA_DM_MSG* p_data);
bool uwa_dm_act_get_session_count(tUWA_DM_MSG* p_data);
bool uwa_dm_act_app_set_config(tUWA_DM_MSG* p_data);
bool uwa_dm_act_app_get_config(tUWA_DM_MSG* p_data);
bool uwa_dm_act_start_range_session(tUWA_DM_MSG* p_data);
bool uwa_dm_act_stop_range_session(tUWA_DM_MSG* p_data);
bool uwa_dm_act_send_raw_cmd(tUWA_DM_MSG* p_data);
bool uwa_dm_act_get_range_count(tUWA_DM_MSG* p_data);
bool uwa_dm_act_get_session_status(tUWA_DM_MSG* p_data);
bool uwa_dm_act_get_device_capability(tUWA_DM_MSG* p_data);
bool uwa_dm_act_multicast_list_update(tUWA_DM_MSG* p_data);
bool uwa_dm_act_set_country_code(tUWA_DM_MSG* p_data);
bool uwa_dm_act_send_blink_data(tUWA_DM_MSG* p_data);

/* Action function prototypes for all RF test functionality */
bool uwa_dm_act_test_set_config(tUWA_DM_MSG* p_data);
bool uwa_dm_act_test_get_config(tUWA_DM_MSG* p_data);
bool uwa_dm_act_test_periodic_tx(tUWA_DM_MSG* p_data);
bool uwa_dm_act_test_per_rx(tUWA_DM_MSG* p_data);
bool uwa_dm_act_test_uwb_loopback(tUWA_DM_MSG* p_data);
bool uwa_dm_act_test_rx(tUWA_DM_MSG* p_data);
bool uwa_dm_act_test_stop_session(tUWA_DM_MSG* p_data);

/* Main function prototypes */
bool uwa_dm_evt_hdlr(UWB_HDR* p_msg);
void uwa_dm_sys_enable(void);
void uwa_dm_sys_disable(void);

std::string uwa_dm_uwb_revt_2_str(tUWB_RESPONSE_EVT event);
std::string uwa_test_dm_uwb_revt_2_str(tUWB_RESPONSE_EVT event);

#endif /* UWA_DM_INT_H */

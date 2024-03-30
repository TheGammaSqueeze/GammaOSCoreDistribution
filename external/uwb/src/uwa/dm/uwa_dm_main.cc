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
 *  This is the main implementation file for the UWA device manager.
 *
 ******************************************************************************/
#include <string>

#include "uci_log.h"
#include "uwa_api.h"
#include "uwa_dm_int.h"
#include "uwb_osal_common.h"

/*****************************************************************************
** Constants and types
*****************************************************************************/
static const tUWA_SYS_REG uwa_dm_sys_reg = {uwa_dm_sys_enable, uwa_dm_evt_hdlr,
                                            uwa_dm_sys_disable};
tUWA_DM_CB uwa_dm_cb = {};

#define UWA_DM_NUM_ACTIONS (UWA_DM_MAX_EVT & 0x00ff)

/* type for action functions */
typedef bool (*tUWA_DM_ACTION)(tUWA_DM_MSG* p_data);

/* action function list */
const tUWA_DM_ACTION uwa_dm_action[] = {
    /* device manager local device API events */
    uwa_dm_enable,                    /* UWA_DM_API_ENABLE_EVT            */
    uwa_dm_disable,                   /* UWA_DM_API_DISABLE_EVT           */
    uwa_dm_act_get_device_info,       /* UWA_DM_API_GET_DEVICE_INFO_EVT  */
    uwa_dm_set_core_config,           /* UWA_DM_API_SET_CORE_CONFIG_EVT   */
    uwa_dm_get_core_config,           /* UWA_DM_API_GET_CORE_CONFIG_EVT   */
    uwa_dm_act_device_reset,          /* UWA_DM_API_DEVICE_RESET_EVT      */
    uwa_dm_act_send_session_init,     /* UWA_DM_API_SESSION_INIT_EVT      */
    uwa_dm_act_send_session_deinit,   /* UWA_DM_API_SESSION_DEINIT_EVT    */
    uwa_dm_act_get_session_count,     /* UWA_DM_API_SESSION_GET_COUNT_EVT */
    uwa_dm_act_app_set_config,        /* UWA_DM_API_SET_APP_CONFIG_EVT    */
    uwa_dm_act_app_get_config,        /* UWA_DM_API_GET_APP_CONFIG_EVT    */
    uwa_dm_act_start_range_session,   /* UWA_DM_API_START_RANGE_EVT       */
    uwa_dm_act_stop_range_session,    /* UWA_DM_API_STOP_RANGE_EVT        */
    uwa_dm_act_send_raw_cmd,          /* UWA_DM_API_SEND_RAW_EVT          */
    uwa_dm_act_get_range_count,       /* UWA_DM_API_GET_RANGE_COUNT_EVT   */
    uwa_dm_act_get_session_status,    /* UWA_DM_API_GET_SESSION_STATUS_EVT   */
    uwa_dm_act_get_device_capability, /* UWA_DM_API_CORE_GET_DEVICE_CAPABILITY_EVT
                                       */
    uwa_dm_act_multicast_list_update, /* UWA_DM_API_SESSION_UPDATE_MULTICAST_LIST_EVT */
    uwa_dm_act_set_country_code,      /* UWA_DM_API_SET_COUNTRY_CODE_EVT */
    uwa_dm_act_send_blink_data,       /* UWA_DM_API_SEND_BLINK_DATA_EVT */
    /*  local API events for RF test functionality */
    uwa_dm_act_test_set_config,   /* UWA_DM_API_TEST_SET_CONFIG_EVT  */
    uwa_dm_act_test_get_config,   /* UWA_DM_API_TEST_GET_CONFIG_EVT  */
    uwa_dm_act_test_periodic_tx,  /* UWA_DM_API_TEST_PERIODIC_TX_EVT    */
    uwa_dm_act_test_per_rx,       /* UWA_DM_API_TEST_PER_RX_EVT     */
    uwa_dm_act_test_uwb_loopback, /* UWA_DM_API_TEST_UWB_LOOPBACK_EVT */
    uwa_dm_act_test_rx,           /* UWA_DM_API_TEST_RX_EVT */
    uwa_dm_act_test_stop_session  /* UWA_DM_API_TEST_STOP_SESSION_EVT     */
};

/*****************************************************************************
** Local function prototypes
*****************************************************************************/
std::string uwa_dm_evt_2_str(uint16_t event);
/*******************************************************************************
**
** Function         uwa_dm_init
**
** Description      Initialises the UWB device manager
**
** Returns          void
**
*******************************************************************************/
void uwa_dm_init(void) {
  UCI_TRACE_I(__func__);
  /* register message handler on UWA SYS */
  memset(&uwa_dm_cb, 0, sizeof(tUWA_DM_CB));
  uwa_sys_register(UWA_ID_DM, &uwa_dm_sys_reg);
}

/*******************************************************************************
**
** Function         uwa_dm_evt_hdlr
**
** Description      Event handling function for DM
**
**
** Returns          void
**
*******************************************************************************/
bool uwa_dm_evt_hdlr(UWB_HDR* p_msg) {
  bool freebuf = true;
  uint16_t event = p_msg->event & 0x00ff;

  UCI_TRACE_I("event: %s (0x%02x)", uwa_dm_evt_2_str(event).c_str(), event);
  /* execute action functions */
  if (event < UWA_DM_NUM_ACTIONS) {
    freebuf = (*uwa_dm_action[event])((tUWA_DM_MSG*)p_msg);
  }
  return freebuf;
}

/*******************************************************************************
**
** Function         uwa_dm_sys_disable
**
** Description      This function is called after all subsystems have been
**                  disabled.
**
** Returns          void
**
*******************************************************************************/
void uwa_dm_sys_disable(void) {
  /* Disable the DM sub-system */
  uwa_dm_disable_complete();
}

/*******************************************************************************
**
** Function         uwa_dm_uwb_revt_2_str
**
** Description      convert uwb revt to string
**
*******************************************************************************/
std::string uwa_dm_evt_2_str(uint16_t event) {
  switch (UWA_SYS_EVT_START(UWA_ID_DM) | event) {
    case UWA_DM_API_ENABLE_EVT:
      return "UWA_DM_API_ENABLE_EVT";

    case UWA_DM_API_DISABLE_EVT:
      return "UWA_DM_API_DISABLE_EVT";

    case UWA_DM_API_GET_DEVICE_INFO_EVT:
      return "UWA_DM_API_GET_DEVICE_INFO_EVT";

    case UWA_DM_API_SET_CORE_CONFIG_EVT:
      return "UWA_DM_API_SET_CORE_CONFIG_EVT";

    case UWA_DM_API_GET_CORE_CONFIG_EVT:
      return "UWA_DM_API_GET_CORE_CONFIG_EVT";

    case UWA_DM_API_DEVICE_RESET_EVT:
      return "UWA_DM_API_DEVICE_RESET_EVT";

    case UWA_DM_API_SESSION_INIT_EVT:
      return "UWA_DM_API_SESSION_INIT_EVT";

    case UWA_DM_API_SESSION_DEINIT_EVT:
      return "UWA_DM_API_SESSION_DEINIT_EVT";

    case UWA_DM_API_SESSION_GET_COUNT_EVT:
      return "UWA_DM_API_SESSION_GET_COUNT_EVT";

    case UWA_DM_API_SET_APP_CONFIG_EVT:
      return "UWA_DM_API_SET_APP_CONFIG_EVT";

    case UWA_DM_API_GET_APP_CONFIG_EVT:
      return "UWA_DM_API_GET_APP_CONFIG_EVT";

    case UWA_DM_API_START_RANGE_EVT:
      return "UWA_DM_API_START_RANGE_EVT";

    case UWA_DM_API_STOP_RANGE_EVT:
      return "UWA_DM_API_STOP_RANGE_EVT";

    case UWA_DM_API_SEND_RAW_EVT:
      return "UWA_DM_API_SEND_RAW_EVT";

    case UWA_DM_API_GET_RANGE_COUNT_EVT:
      return "UWA_DM_API_GET_RANGE_COUNT_EVT";

    case UWA_DM_API_GET_SESSION_STATUS_EVT:
      return "UWA_DM_API_GET_SESSION_STATUS_EVT";

    case UWA_DM_API_TEST_SET_CONFIG_EVT:
      return "UWA_DM_API_TEST_SET_CONFIG_EVT";

    case UWA_DM_API_TEST_GET_CONFIG_EVT:
      return "UWA_DM_API_TEST_GET_CONFIG_EVT";

    case UWA_DM_API_TEST_PERIODIC_TX_EVT:
      return "UWA_DM_API_TEST_PERIODIC_TX_EVT";

    case UWA_DM_API_TEST_PER_RX_EVT:
      return "UWA_DM_API_TEST_PER_RX_EVT";

    case UWA_DM_API_TEST_STOP_SESSION_EVT:
      return "UWA_DM_API_TEST_STOP_SESSION_EVT";

    case UWA_DM_API_TEST_RX_EVT:
      return "UWA_DM_API_TEST_RX_EVT";
  }

  return "Unknown or Vendor Specific";
}

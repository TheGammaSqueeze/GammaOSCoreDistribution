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

#ifndef UWB_INT_H_
#define UWB_INT_H_

#include "uci_defs.h"
#include "uwb_api.h"
#include "uwb_gki.h"
#include "uwb_target.h"

/****************************************************************************
** UWB_TASK definitions
****************************************************************************/

/* UWB_TASK event masks */
#define UWB_TASK_EVT_TRANSPORT_READY EVENT_MASK(APPL_EVT_0)

/* UWB Timer events */
#define UWB_TTYPE_UCI_WAIT_RSP 0x00
#define UWB_WAIT_RSP_RAW_CMD 0x01

#define UWB_SAVED_HDR_SIZE 2

/* UWB Task event messages */
enum {
  UWB_STATE_NONE,         /* not start up yet                         */
  UWB_STATE_W4_HAL_OPEN,  /* waiting for HAL_UWB_OPEN_CPLT_EVT   */
  UWB_STATE_IDLE,         /* normal operation( device is in idle state) */
  UWB_STATE_ACTIVE,       /* UWB device is in active                    */
  UWB_STATE_W4_HAL_CLOSE, /* waiting for HAL_UWB_CLOSE_CPLT_EVT  */
  UWB_STATE_CLOSING
};
typedef uint8_t tUWB_STATE;

/* This data type is for UWB task to send a UCI VS command to UCIT task */
typedef struct {
  UWB_HDR bt_hdr;          /* the UCI command          */
  tUWB_RAW_CBACK* p_cback; /* the callback function to receive RSP   */
} tUWB_UCI_RAW_MSG;

/* This data type is for HAL event */
typedef struct {
  UWB_HDR hdr;
  uint8_t hal_evt; /* HAL event code  */
  uint8_t status;  /* tHAL_UWB_STATUS */
} tUWB_HAL_EVT_MSG;

/* callback function pointer(8; use 8 to be safe + UWB_SAVED_CMD_SIZE(2) */
#define UWB_RECEIVE_MSGS_OFFSET (10)

/* UWB control blocks */
typedef struct {
  tUWB_RESPONSE_CBACK* p_resp_cback;
  tUWB_TEST_RESPONSE_CBACK* p_test_resp_cback;

  /* UWB_TASK timer management */
  TIMER_LIST_Q timer_queue; /* 1-sec timer event queue */
  TIMER_LIST_Q quick_timer_queue;

  tUWB_STATE uwb_state;

  uint8_t trace_level;
  uint8_t last_hdr[UWB_SAVED_HDR_SIZE]; /* part of last UCI command header */
  uint8_t last_cmd[UWB_SAVED_HDR_SIZE]; /* part of last UCI command payload */

  void* p_raw_cmd_cback;   /* the callback function for last raw command */
  BUFFER_Q uci_cmd_xmit_q; /* UCI command queue */

  TIMER_LIST_ENT
  uci_wait_rsp_timer;         /* Timer for waiting for uci command response */
  uint16_t uci_wait_rsp_tout; /* UCI command timeout (in ms) */
  uint16_t retry_rsp_timeout; /* UCI command timeout during retry */

  uint8_t uci_cmd_window;       /* Number of commands the controller can accecpt
                                   without waiting for response */
  bool is_resp_pending;         /* response is pending from UWBS */
  bool is_recovery_in_progress; /* recovery in progresss  */

  tHAL_UWB_ENTRY* p_hal;
  uint8_t rawCmdCbflag;
  uint8_t device_state;

  uint16_t cmd_retry_count;
  UWB_HDR* pLast_cmd_buf;

  bool IsConformaceTestEnabled; /* MCTT mode indicator */
} tUWB_CB;

/*****************************************************************************
 **  EXTERNAL FUNCTION DECLARATIONS
 *****************************************************************************/

/* Global UWB data */
extern tUWB_CB uwb_cb;

/****************************************************************************
 ** Internal uwb functions
 ****************************************************************************/

extern void uwb_init(void);

extern bool uwb_ucif_process_event(UWB_HDR* p_msg);
extern void uwb_ucif_check_cmd_queue(UWB_HDR* p_buf);
extern void uwb_ucif_retransmit_cmd(UWB_HDR* p_buf);
extern void uwb_ucif_send_cmd(UWB_HDR* p_buf);
extern void uwb_ucif_update_cmd_window(void);
extern void uwb_ucif_cmd_timeout(void);
extern void uwb_ucif_event_status(tUWB_RESPONSE_EVT event, uint8_t status);
extern void uwb_ucif_error_status(uint8_t conn_id, uint8_t status);
extern void uwb_ucif_uwb_recovery(void);

extern bool uwb_proc_core_rsp(uint8_t op_code, uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_get_device_info_rsp(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_get_device_capability_rsp(uint8_t* p_buf,
                                                    uint16_t len);
extern void uwb_ucif_proc_core_set_config_status(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_core_get_config_rsp(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_core_device_reset_rsp_status(uint8_t* p_buf,
                                                       uint16_t len);
extern void uwb_ucif_proc_core_device_status(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_core_generic_error_ntf(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_conformance_ntf(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_app_get_config_status(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_app_set_config_status(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_ranging_data(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_send_blink_data_ntf(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_range_management_status(tUWB_RESPONSE_EVT event,
                                             uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_get_range_count_status(tUWB_RESPONSE_EVT event,
                                            uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_session_management_status(tUWB_RESPONSE_EVT event,
                                               uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_session_status(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_multicast_list_update_ntf(uint8_t* p_buf,
                                                    uint16_t len);
void uwb_ucif_proc_android_set_country_code_status(uint8_t* p_buf,
                                                   uint16_t len);

/* APIs for handling UWB RF test command responses and notifications */
extern void uwb_ucif_test_management_status(tUWB_RESPONSE_EVT event,
                                            uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_test_get_config_status(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_test_set_config_status(uint8_t* p_buf, uint16_t len);
extern void uwb_ucif_proc_rf_test_data(tUWB_RESPONSE_EVT event, uint8_t* p_buf,
                                       uint16_t len);

/* From uwb_task.c */
extern uint32_t uwb_task(uint32_t param);
void uwb_task_shutdown_uwbc(void);

/* From uwb_main.c */
void uwb_enabled(tUWB_STATUS uwb_status, UWB_HDR* p_init_rsp_msg);
void uwb_set_state(tUWB_STATE uwb_state);
void uwb_main_flush_cmd_queue(void);
void uwb_main_handle_hal_evt(tUWB_HAL_EVT_MSG* p_msg);
void uwb_gen_cleanup(void);

/* Timer functions */
void uwb_start_timer(TIMER_LIST_ENT* p_tle, uint16_t type, uint32_t timeout);
uint32_t uwb_remaining_time(TIMER_LIST_ENT* p_tle);
void uwb_stop_timer(TIMER_LIST_ENT* p_tle);

void uwb_start_quick_timer(TIMER_LIST_ENT* p_tle, uint16_t type,
                           uint32_t timeout);
void uwb_stop_quick_timer(TIMER_LIST_ENT* p_tle);
void uwb_process_quick_timer_evt(void);

#endif /* UWB_INT_H_ */

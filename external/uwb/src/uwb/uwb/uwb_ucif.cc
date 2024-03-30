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

#include <stdlib.h>
#include <string.h>

#include "uci_defs.h"
#include "uci_hmsgs.h"
#include "uci_log.h"
#include "uci_test_defs.h"
#include "uwa_dm_int.h"
#include "uwa_sys.h"
#include "uwb_api.h"
#include "uwb_config.h"
#include "uwb_hal_api.h"
#include "uwb_hal_int.h"
#include "uwb_int.h"
#include "uwb_osal_common.h"
#include "uwb_target.h"

#define NORMAL_MODE_LENGTH_OFFSET 0x03
#define MAC_SHORT_ADD_LEN 2
#define MAC_EXT_ADD_LEN 8
#define PDOA_LEN 4
#define AOA_LEN 4
#define AOA_DEST_LEN 4
#define CONFIG_TLV_OFFSET 2
#define TWO_WAY_MEASUREMENT_LENGTH 31
#define ONE_WAY_MEASUREMENT_LENGTH 36
#define RANGING_DATA_LENGTH 25

#define VENDOR_SPEC_INFO_LEN 2

uint8_t last_cmd_buff[UCI_MAX_PAYLOAD_SIZE];
uint8_t last_data_buff[4096];
static uint8_t device_info_buffer[MAX_NUM_OF_TDOA_MEASURES]
                                 [UCI_MAX_PAYLOAD_SIZE];
static uint8_t blink_payload_buffer[MAX_NUM_OF_TDOA_MEASURES]
                                   [UCI_MAX_PAYLOAD_SIZE];
static uint8_t range_data_ntf_buffer[2048];
static uint8_t range_data_ntf_len =0;

struct chained_uci_packet {
  uint8_t buffer[4192];
  uint8_t oid;
  uint8_t gid;
  uint16_t offset;
  uint8_t is_first_frgmnt_done;
};

typedef struct chained_uci_packet chained_uci_packet;

/*******************************************************************************
 **
 ** Function         uwb_ucif_update_cmd_window
 **
 ** Description      Update tx cmd window to indicate that UWBC can received
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_update_cmd_window(void) {
  /* Sanity check - see if we were expecting a update_window */
  if (uwb_cb.uci_cmd_window == UCI_MAX_CMD_WINDOW) {
    if (uwb_cb.uwb_state != UWB_STATE_W4_HAL_CLOSE) {
      UCI_TRACE_E("uwb_ucif_update_window: Unexpected call");
    }
    return;
  }
  /* Stop command-pending timer */
  uwb_stop_quick_timer(&uwb_cb.uci_wait_rsp_timer);

  uwb_cb.p_raw_cmd_cback = NULL;
  uwb_cb.uci_cmd_window++;
  uwb_cb.is_resp_pending = false;
  uwb_cb.cmd_retry_count = 0; /* reset the retry count as response is received*/

  uwb_ucif_check_cmd_queue(NULL);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_cmd_timeout
 **
 ** Description      Handle a command timeout
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_cmd_timeout(void) {
  UCI_TRACE_I("uwb_ucif_cmd_timeout");
  /* if enabling UWB, notify upper layer of failure */
  if (uwb_cb.is_resp_pending &&
      (uwb_cb.cmd_retry_count < UCI_CMD_MAX_RETRY_COUNT)) {
    uwb_stop_quick_timer(
        &uwb_cb.uci_wait_rsp_timer); /*stop the pending timer */
    uwb_ucif_retransmit_cmd(uwb_cb.pLast_cmd_buf);
    uwb_cb.cmd_retry_count++;
  } else {
    uwb_ucif_event_status(UWB_UWBS_RESP_TIMEOUT_REVT, UWB_STATUS_FAILED);
    uwb_ucif_uwb_recovery();
  }
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_retransmit_cmd
 **
 ** Description      Retransmission of last packet
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_retransmit_cmd(UWB_HDR* p_buf) {
  UCI_TRACE_I("uwb_ucif_retransmit_cmd");
  if (p_buf == NULL) {
    UCI_TRACE_E("uwb_ucif_retransmit_cmd: p_data is NULL");
    return;
  }
  HAL_RE_WRITE(p_buf);
  /* start UWB command-timeout timer */
  uwb_start_quick_timer(&uwb_cb.uci_wait_rsp_timer,
                        (uint16_t)(UWB_TTYPE_UCI_WAIT_RSP),
                        uwb_cb.retry_rsp_timeout);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_check_cmd_queue
 **
 ** Description      Send UCI command to the transport
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_check_cmd_queue(UWB_HDR* p_buf) {
  uint8_t* ps;
  uint8_t* pTemp;
  // tUWB_CONN_CB* p_cb = NULL;
  UCI_TRACE_I("uwb_ucif_check_cmd_queue()");

  if (uwb_cb.uwb_state == UWB_STATE_W4_HAL_CLOSE ||
      uwb_cb.uwb_state == UWB_STATE_NONE) {
    UCI_TRACE_E("%s: HAL is not initialized", __func__);
    phUwb_GKI_freebuf(p_buf);
    return;
  }

  /* If there are commands waiting in the xmit queue, or if the UWBS
   * cannot accept any more commands, */
  /* then enqueue this command */
  if (p_buf) {
    if ((uwb_cb.uci_cmd_xmit_q.count) || (uwb_cb.uci_cmd_window == 0)) {
      phUwb_GKI_enqueue(&uwb_cb.uci_cmd_xmit_q, p_buf);
      if (p_buf != NULL) {
        UCI_TRACE_E("uwb_ucif_check_cmd_queue : making  p_buf NULL.");
        p_buf = NULL;
      }
    }
  }

  /* If Helios can accept another command, then send the next command */
  if (uwb_cb.uci_cmd_window > 0) {
    /* If no command was provided, or if older commands were in the queue, then
     * get cmd from the queue */
    if (!p_buf) p_buf = (UWB_HDR*)phUwb_GKI_dequeue(&uwb_cb.uci_cmd_xmit_q);

    if (p_buf) {
      /* save the message header to double check the response */
      ps = (uint8_t*)(p_buf + 1) + p_buf->offset;
      uint8_t pbf = (*(ps)&UCI_PBF_MASK) >> UCI_PBF_SHIFT;
      memcpy(uwb_cb.last_hdr, ps, UWB_SAVED_HDR_SIZE);
      memcpy(uwb_cb.last_cmd, ps + UCI_MSG_HDR_SIZE, UWB_SAVED_HDR_SIZE);
      /* copying command to temp buff for retransmission */
      uwb_cb.pLast_cmd_buf = (UWB_HDR*)last_cmd_buff;
      uwb_cb.pLast_cmd_buf->offset = p_buf->offset;
      pTemp =
          (uint8_t*)(uwb_cb.pLast_cmd_buf + 1) + uwb_cb.pLast_cmd_buf->offset;
      uwb_cb.pLast_cmd_buf->len = p_buf->len;
      memcpy(pTemp, ps, p_buf->len);
      if (p_buf->layer_specific == UWB_WAIT_RSP_RAW_CMD) {
        /* save the callback for RAW VS */
        uwb_cb.p_raw_cmd_cback = (void*)((tUWB_UCI_RAW_MSG*)p_buf)->p_cback;
        uwb_cb.rawCmdCbflag = true;
      }

      /* Indicate command is pending */
      uwb_cb.uci_cmd_window--;
      uwb_cb.is_resp_pending = true;
      uwb_cb.cmd_retry_count = 0;

      /* send to HAL */
      HAL_WRITE(p_buf);
      if (!(pbf && uwb_cb.IsConformaceTestEnabled)) {  // if pbf bit is set for
                                                       // conformance test skip
                                                       // timer start.
        /* start UWB command-timeout timer */
        uwb_start_quick_timer(&uwb_cb.uci_wait_rsp_timer,
                              (uint16_t)(UWB_TTYPE_UCI_WAIT_RSP),
                              uwb_cb.uci_wait_rsp_tout);
      }
    }
  }
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_send_cmd
 **
 ** Description      Send UCI command to the UCIT task
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_send_cmd(UWB_HDR* p_buf) {
  UCI_TRACE_I("uwb_ucif_send_cmd.");
  if (p_buf == NULL) {
    UCI_TRACE_E("p_buf is NULL.");
    return;
  }
  /* post the p_buf to UCIT task */
  p_buf->event = BT_EVT_TO_UWB_UCI;
  p_buf->layer_specific = 0;
  uwb_ucif_check_cmd_queue(p_buf);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_process_event
 **
 ** Description      This function is called to process the
 **                  data/response/notification from UWBC
 **
 ** Returns          true if need to free buffer
 **
 *******************************************************************************/
bool uwb_ucif_process_event(UWB_HDR* p_msg) {
  uint8_t mt, pbf, gid, oid, *p, *pp;
  bool free = true;
  uint16_t payload_length;
  uint8_t *p_old, old_gid, old_oid, old_mt;
  static chained_uci_packet chained_packet;

  p = (uint8_t*)(p_msg + 1) + p_msg->offset;
  pp = p;

  if ((p != NULL) & (pp != NULL)) {
    UCI_MSG_PRS_HDR0(pp, mt, pbf, gid);
    UCI_MSG_PRS_HDR1(pp, oid);
    pp = pp + 2;  // Skip payload fields
    UCI_TRACE_E("uwb_ucif_process_event enter gid:0x%x status:0x%x", p[0],
                pp[0]);
    payload_length = p[NORMAL_MODE_LENGTH_OFFSET];

    if (!uwb_cb.IsConformaceTestEnabled) {
      if (pbf) {
        if (!chained_packet.is_first_frgmnt_done) {
          chained_packet.oid = oid;
          chained_packet.gid = gid;
          memcpy(&chained_packet.buffer[chained_packet.offset], p,
                 p_msg->len);  // Copy first fragment(uci packet with header)(p)
          chained_packet.offset = p_msg->len;
          chained_packet.is_first_frgmnt_done = true;
        } else {
          // if first fragment is copied, then copy only uci payload(pp) for
          // subsequent fragments
          if ((chained_packet.oid == oid) && (chained_packet.gid == gid)) {
            memcpy(&chained_packet.buffer[chained_packet.offset], pp,
                   payload_length);
            chained_packet.offset =
                (uint16_t)(chained_packet.offset + payload_length);
          } else {
            UCI_TRACE_E(
                "uwb_ucif_process_event: unexpected chain packet: "
                "chained_packed_gid: 0x%x, chained_packet_oid=0x%x, received "
                "packet gid:0x%x, recived packet oid:0x%x",
                chained_packet.gid, chained_packet.oid, gid, oid);
          }
        }
        return (free);
      } else {
        if (chained_packet.is_first_frgmnt_done) {
          if ((chained_packet.oid == oid) && (chained_packet.gid == gid)) {
            memcpy(&chained_packet.buffer[chained_packet.offset], pp,
                   payload_length);  // Append only payload to chained packet
            chained_packet.offset =
                (uint16_t)(chained_packet.offset + payload_length);

            // Update P & PP
            p = &chained_packet
                     .buffer[0];  // p -> points to complete UCI packet
            pp = p + 2;           // Skip oid & gid bytes
            payload_length =
                (uint16_t)(chained_packet.offset - UCI_MSG_HDR_SIZE);
            UINT16_TO_STREAM(pp,
                             payload_length);  // Update overall payload length
                                               // into the chained packet

            // Clear flags
            chained_packet.offset = 0;
            chained_packet.is_first_frgmnt_done = false;
            chained_packet.oid = 0xFF;
            chained_packet.gid = 0xFF;
          }
        }
      }
    }

    if ((uwb_cb.rawCmdCbflag == true) && (mt != UCI_MT_NTF)) {
      uci_proc_raw_cmd_rsp(p, p_msg->len);
      uwb_cb.rawCmdCbflag = false;
      return (free);
    }

    switch (mt) {
      case UCI_MT_RSP:
        UCI_TRACE_I("uwb_ucif_process_event: UWB received rsp gid:%d", gid);
        p_old = uwb_cb.last_hdr;
        UCI_MSG_PRS_HDR0(p_old, old_mt, pbf, old_gid);
        UCI_MSG_PRS_HDR1(p_old, old_oid);
        (void)old_mt;  // Dummy conversion to fix the warning
        /* make sure this is the RSP we are waiting for before updating the
         * command window */
        if ((old_gid != gid) || (old_oid != oid)) {
          UCI_TRACE_E(
              "uwb_ucif_process_event unexpected rsp: gid:0x%x, oid:0x%x", gid,
              oid);
          return true;
        }

        switch (gid) {
          case UCI_GID_CORE: /* 0000b UCI Core group */
            free = uwb_proc_core_rsp(oid, pp, payload_length);
            break;
          case UCI_GID_SESSION_MANAGE: /* 0001b UCI Session Config group */
            uci_proc_session_management_rsp(oid, pp, payload_length);
            break;
          case UCI_GID_RANGE_MANAGE: /* 0010b UCI Range group */
            uci_proc_rang_management_rsp(oid, pp, payload_length);
            break;
          case UCI_GID_ANDROID: /* 1110b UCI vendor Android group */
            uci_proc_android_rsp(oid, pp, payload_length);
            break;
          case UCI_GID_TEST: /* 1101b test group */
            uci_proc_test_management_rsp(oid, pp, payload_length);
            break;
          default:
            UCI_TRACE_E("uwb_ucif_process_event: Unknown gid:%d", gid);
            break;
        }

        uwb_ucif_update_cmd_window();
        break;

      case UCI_MT_NTF:
        UCI_TRACE_I("uwb_ucif_process_event: UWB received ntf gid:%d", gid);
        if ((!(gid == UCI_GID_CORE && oid == UCI_MSG_CORE_GENERIC_ERROR_NTF &&
               pp[0] == UCI_STATUS_COMMAND_RETRY)) &&
            uwb_cb.IsConformaceTestEnabled) {
          // handling of ntf for conformance test
          uwb_ucif_proc_conformance_ntf(p, payload_length + 4);
          return (free);
        }

        switch (gid) {
          case UCI_GID_CORE:
            uci_proc_core_management_ntf(oid, pp, payload_length);
            break;
          case UCI_GID_SESSION_MANAGE: /* 0010b UCI management group */
            uci_proc_session_management_ntf(oid, pp, payload_length);
            break;
          case UCI_GID_RANGE_MANAGE: /* 0011b UCI Range management group */
            range_data_ntf_len = p_msg->len;
            for (int i=0; i<p_msg->len;i++) {
                 range_data_ntf_buffer[i] = p[i];
            }
            uci_proc_rang_management_ntf(oid, pp, payload_length);
            break;
          case UCI_GID_TEST: /* 1101b test group */
            //uci_proc_test_management_ntf(oid, pp, payload_length);
            //send vendor specific ntf as it is handled by vendor extension
            uci_proc_vendor_specific_ntf(gid, p, (payload_length + UCI_MSG_HDR_SIZE));
            break;
          case UCI_GID_VENDOR_SPECIFIC_0x09:
          case UCI_GID_VENDOR_SPECIFIC_0x0A:
          case UCI_GID_VENDOR_SPECIFIC_0x0B:
          case UCI_GID_VENDOR_SPECIFIC_0x0C:
          case UCI_GID_VENDOR_SPECIFIC_0x0E:
          case UCI_GID_VENDOR_SPECIFIC_0x0F:
            uci_proc_vendor_specific_ntf(gid, p, (payload_length + UCI_MSG_HDR_SIZE));
            break;
          default:
            UCI_TRACE_E("uwb_ucif_process_event: UWB Unknown gid:%d", gid);
            break;
        }
        break;
      default:
        UCI_TRACE_E(
            "uwb_ucif_process_event: UWB received unknown mt:0x%x, gid:%d", mt,
            gid);
    }
  } else {
    UCI_TRACE_E("uwb_ucif_process_event: NULL pointer");
  }
  return (free);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_core_device_reset_rsp_status
 **
 ** Description      This function is called to report UWB_DEVICE_RESET_REVT
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_core_device_reset_rsp_status(uint8_t* p_buf, uint16_t len) {
  tUWB_RESPONSE evt_data;
  tUWB_STATUS status;

  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  status = (tUWB_STATUS)*p_buf;
  UCI_TRACE_I("StatusName:%s and StatusValue:%d", UWB_GetStatusName(status),
              status);
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  evt_data.sDevice_reset.status = status;
  if (status == UWA_STATUS_OK) {
    UCI_TRACE_I("%s: Device Reset Successful", __func__);
  } else {
    UCI_TRACE_E("%s: Device Reset Failed", __func__);
  }
  (*uwb_cb.p_resp_cback)(UWB_DEVICE_RESET_REVT, &evt_data);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_core_set_config_status
 **
 ** Description      This function is called to report UWB_SET_CORE_CONFIG_REVT
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_core_set_config_status(uint8_t* p_buf, uint16_t len) {
  tUWB_RESPONSE evt_data;
  tUWB_STATUS status;
  uint8_t* p = p_buf;
  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  status = (tUWB_STATUS)*p++;
  UCI_TRACE_I("StatusName:%s and StatusValue:%d", UWB_GetStatusName(status),
              status);
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  evt_data.sCore_set_config.status = status;
  evt_data.sCore_set_config.num_param_id = *p++;
  evt_data.sCore_set_config.tlv_size = (uint16_t)(len - CONFIG_TLV_OFFSET);
  if (evt_data.sCore_set_config.tlv_size > 0) {
    STREAM_TO_ARRAY(evt_data.sCore_set_config.param_ids, p,
                    evt_data.sCore_set_config.tlv_size);
  }
  (*uwb_cb.p_resp_cback)(UWB_SET_CORE_CONFIG_REVT, &evt_data);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_core_get_config_rsp
 **
 ** Description      This function is called to process get config response
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_core_get_config_rsp(uint8_t* p_buf, uint16_t len) {
  tUWB_RESPONSE evt_data;
  tUWB_STATUS status;
  uint8_t* p = p_buf;
  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  status = *p++;
  UCI_TRACE_I("StatusName:%s and StatusValue:%d", UWB_GetStatusName(status),
              status);
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  evt_data.sCore_get_config.status = status;
  evt_data.sCore_get_config.no_of_ids = *p++;
  evt_data.sCore_get_config.tlv_size = (uint16_t)(len - CONFIG_TLV_OFFSET);
  if (evt_data.sCore_get_config.tlv_size > 0) {
    memcpy(evt_data.sCore_get_config.p_param_tlvs, p,
           evt_data.sCore_get_config.tlv_size);
  }

  (*uwb_cb.p_resp_cback)(UWB_GET_CORE_CONFIG_REVT, &evt_data);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_session_management_status
 **
 ** Description      This function is called to process session command
 *responses
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_session_management_status(tUWB_RESPONSE_EVT event, uint8_t* p_buf,
                                        uint16_t len) {
  tUWB_RESPONSE evt_data;
  tUWB_RESPONSE_EVT evt = 0;
  tUWB_STATUS status;
  uint8_t* p = p_buf;

  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  status = *p++;
  UCI_TRACE_I("StatusName:%s and StatusValue:%d", UWB_GetStatusName(status),
              status);
  switch (event) {
    case UWB_SESSION_INIT_REVT:
      evt = UWB_SESSION_INIT_REVT;
      evt_data.status = status;
      break;
    case UWB_SESSION_DEINIT_REVT:
      evt = UWB_SESSION_DEINIT_REVT;
      evt_data.status = status;
      break;
    case UWB_SESSION_GET_COUNT_REVT:
      evt = UWB_SESSION_GET_COUNT_REVT;
      evt_data.sGet_session_cnt.status = status;
      evt_data.sGet_session_cnt.count = *p;
      break;
    case UWB_SESSION_GET_STATE_REVT:
      evt = UWB_SESSION_GET_STATE_REVT;
      evt_data.sGet_session_state.status = status;
      evt_data.sGet_session_state.session_state = *p;
      break;
    case UWB_SESSION_UPDATE_MULTICAST_LIST_REVT:
      evt = UWB_SESSION_UPDATE_MULTICAST_LIST_REVT;
      evt_data.status = status;
      break;
    default:
      UCI_TRACE_E("unknown response event %x", event);
  }
  if (evt) {
    (*uwb_cb.p_resp_cback)(evt, &evt_data);
  }
}
/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_app_get_config_status
 **
 ** Description      This function is called to process get config response
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_app_get_config_status(uint8_t* p_buf, uint16_t len) {
  tUWB_RESPONSE evt_data;
  tUWB_STATUS status;
  uint8_t* p = p_buf;

  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  status = *p++;
  UCI_TRACE_I("StatusName:%s and StatusValue:%d", UWB_GetStatusName(status),
              status);
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  evt_data.sApp_get_config.status = status;
  evt_data.sApp_get_config.no_of_ids = *p++;
  evt_data.sApp_get_config.tlv_size = (uint16_t)(len - CONFIG_TLV_OFFSET);
  if (evt_data.sApp_get_config.tlv_size > 0) {
    memcpy(evt_data.sApp_get_config.p_param_tlvs, p,
           evt_data.sApp_get_config.tlv_size);
  }
  (*uwb_cb.p_resp_cback)(UWB_GET_APP_CONFIG_REVT, &evt_data);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_app_set_config_status
 **
 ** Description      This function is called to report UWB_SET_APP_CONFIG_REVT
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_app_set_config_status(uint8_t* p_buf, uint16_t len) {
  tUWB_RESPONSE evt_data;
  tUWB_STATUS status;
  uint8_t* p = p_buf;

  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  status = *p++;
  UCI_TRACE_I("StatusName:%s and StatusValue:%d", UWB_GetStatusName(status),
              status);
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  evt_data.sApp_set_config.status = status;
  evt_data.sApp_set_config.num_param_id = *p++;
  evt_data.sApp_set_config.tlv_size = (uint16_t)(len - CONFIG_TLV_OFFSET);
  if (evt_data.sApp_set_config.tlv_size > 0) {
    STREAM_TO_ARRAY(evt_data.sApp_set_config.param_ids, p,
                    evt_data.sApp_set_config.tlv_size);
  }

  (*uwb_cb.p_resp_cback)(UWB_SET_APP_CONFIG_REVT, &evt_data);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_range_management_status
 **
 ** Description      This function is called to process raning start/stop
 *command responses
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_range_management_status(tUWB_RESPONSE_EVT event, uint8_t* p_buf,
                                      uint16_t len) {
  tUWB_RESPONSE evt_data;
  tUWB_RESPONSE_EVT evt = 0;
  uint8_t status = *p_buf;

  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  UCI_TRACE_I("StatusName:%s and StatusValue:%d", UWB_GetStatusName(status),
              status);
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  switch (event) {
    case UWB_START_RANGE_REVT:
      evt = UWB_START_RANGE_REVT;
      break;
    case UWB_STOP_RANGE_REVT:
      evt = UWB_STOP_RANGE_REVT;
      break;
    case UWB_BLINK_DATA_TX_REVT:
      evt = UWB_BLINK_DATA_TX_REVT;
      break;
    default:
      UCI_TRACE_E("unknown response event %x", event);
  }
  if (evt) {
    evt_data.status = status;
    (*uwb_cb.p_resp_cback)(evt, &evt_data);
  }
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_get_range_count_status
 **
 ** Description      This function is called to process get range command
 **                  responses
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_get_range_count_status(tUWB_RESPONSE_EVT event, uint8_t* p_buf,
                                     uint16_t len) {
  tUWB_RESPONSE evt_data;
  tUWB_RESPONSE_EVT evt = 0;
  tUWB_GET_RANGE_COUNT_REVT get_count;
  uint8_t* p = p_buf;

  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  switch (event) {
    case UWB_GET_RANGE_COUNT_REVT:
      STREAM_TO_UINT8(get_count.status, p);
      STREAM_TO_UINT32(get_count.count, p);
      UCI_TRACE_I("get_count status = %d", get_count.status);
      evt_data.sGet_range_cnt = get_count;
      evt = UWB_GET_RANGE_COUNT_REVT;
      break;
    default:
      UCI_TRACE_E("unknown response event %x", event);
  }
  if (evt) {
    evt_data.status = get_count.status;
    (*uwb_cb.p_resp_cback)(evt, &evt_data);
  }
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_core_device_status
 **
 ** Description      This function is called to device status notification
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_core_device_status(uint8_t* p_buf, uint16_t len) {
  tUWB_RESPONSE uwb_response;
  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  uint8_t status = *p_buf;

  UCI_TRACE_I("uwb_ucif_proc_core_device_status dev_status = %x", status);
  uwb_response.sDevice_status.status = status;
  uwb_cb.device_state = status;

  (*uwb_cb.p_resp_cback)(UWB_DEVICE_STATUS_REVT, &uwb_response);
  if (status == UWBS_STATUS_ERROR) {
    uwb_stop_quick_timer(&uwb_cb.uci_wait_rsp_timer);
    uwb_ucif_uwb_recovery();
  }
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_core_generic_error_ntf
 **
 ** Description      This function is called to process core generic error
 **                  notification
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_core_generic_error_ntf(uint8_t* p_buf, uint16_t len) {
  tUWB_RESPONSE uwb_response;
  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  uint8_t status = *p_buf;
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  UCI_TRACE_I("uwb_ucif_proc_core_generic_error_ntf: status = %x", status);
  uwb_response.sCore_gen_err_status.status = status;
  if ((status == UCI_STATUS_COMMAND_RETRY) && uwb_cb.is_resp_pending) {
    uwb_stop_quick_timer(
        &uwb_cb.uci_wait_rsp_timer); /*stop the pending timer */
    uwb_ucif_retransmit_cmd(uwb_cb.pLast_cmd_buf);
    uwb_cb.cmd_retry_count++;
  } else {
    (*uwb_cb.p_resp_cback)(UWB_CORE_GEN_ERR_STATUS_REVT, &uwb_response);
  }
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_ranging_data
 **
 ** Description      This function is called to process ranging data
 **                  notifications
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_ranging_data(uint8_t* p, uint16_t len) {
  tUWB_RANGE_DATA_REVT sRange_data;
  tUWB_RESPONSE uwb_response;
  int16_t ranging_measures_length = 0;
  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  memset(&sRange_data, 0, sizeof(tUWB_RANGE_DATA_REVT));
  sRange_data.range_data_len = len;
  STREAM_TO_UINT32(sRange_data.seq_counter, p);
  STREAM_TO_UINT32(sRange_data.session_id, p);
  STREAM_TO_UINT8(sRange_data.rcr_indication, p);
  STREAM_TO_UINT32(sRange_data.curr_range_interval, p);
  STREAM_TO_UINT8(sRange_data.ranging_measure_type, p);
  STREAM_TO_UINT8(sRange_data.rfu, p);
  STREAM_TO_UINT8(sRange_data.mac_addr_mode_indicator, p);
  STREAM_TO_ARRAY(&sRange_data.reserved[0], p, 8);
  STREAM_TO_UINT8(sRange_data.no_of_measurements, p);
  ranging_measures_length = len - RANGING_DATA_LENGTH;
  if (sRange_data.ranging_measure_type == MEASUREMENT_TYPE_TWOWAY &&
      sRange_data.no_of_measurements > MAX_NUM_RESPONDERS) {
    UCI_TRACE_E(
        "%s: MEASUREMENT_TYPE_TWOWAY Wrong number of measurements received:%d",
        __func__, sRange_data.no_of_measurements);
    return;
  } else if (sRange_data.ranging_measure_type == MEASUREMENT_TYPE_ONEWAY &&
             sRange_data.no_of_measurements > MAX_NUM_OF_TDOA_MEASURES) {
    UCI_TRACE_E(
        "%s: MEASUREMENT_TYPE_ONEWAY Wrong number of measurements received:%d",
        __func__, sRange_data.no_of_measurements);
    return;
  }
  if (sRange_data.ranging_measure_type == MEASUREMENT_TYPE_TWOWAY) {
    for (uint8_t i = 0; i < sRange_data.no_of_measurements; i++) {
      tUWA_TWR_RANGING_MEASR* twr_range_measr =
          (tUWA_TWR_RANGING_MEASR*)&sRange_data.ranging_measures
              .twr_range_measr[i];
      if (ranging_measures_length < TWO_WAY_MEASUREMENT_LENGTH) {
        UCI_TRACE_E("%s: Invalid ranging_measures_length = %x", __func__,
                    ranging_measures_length);
        return;
      }
      ranging_measures_length -= TWO_WAY_MEASUREMENT_LENGTH;
      if (sRange_data.mac_addr_mode_indicator == SHORT_MAC_ADDRESS) {
        STREAM_TO_ARRAY(&twr_range_measr->mac_addr[0], p, MAC_SHORT_ADD_LEN);
      } else if (sRange_data.mac_addr_mode_indicator == EXTENDED_MAC_ADDRESS) {
        STREAM_TO_ARRAY(&twr_range_measr->mac_addr[0], p, MAC_EXT_ADD_LEN);
      } else {
        UCI_TRACE_E("%s: Invalid mac addressing indicator", __func__);
        return;
      }
      STREAM_TO_UINT8(twr_range_measr->status, p);
      STREAM_TO_UINT8(twr_range_measr->nLos, p);
      STREAM_TO_UINT16(twr_range_measr->distance, p);
      STREAM_TO_UINT16(twr_range_measr->aoa_azimuth, p);
      STREAM_TO_UINT8(twr_range_measr->aoa_azimuth_FOM, p);
      STREAM_TO_UINT16(twr_range_measr->aoa_elevation, p);
      STREAM_TO_UINT8(twr_range_measr->aoa_elevation_FOM, p);
      STREAM_TO_UINT16(twr_range_measr->aoa_dest_azimuth, p);
      STREAM_TO_UINT8(twr_range_measr->aoa_dest_azimuth_FOM, p);
      STREAM_TO_UINT16(twr_range_measr->aoa_dest_elevation, p);
      STREAM_TO_UINT8(twr_range_measr->aoa_dest_elevation_FOM, p);
      STREAM_TO_UINT8(twr_range_measr->slot_index, p);
      /* Read & Ignore RFU bytes
         if mac address format is short, then 12 bytes
         if mac address format is extended, then read 6 bytes */
      if (sRange_data.mac_addr_mode_indicator == SHORT_MAC_ADDRESS) {
        STREAM_TO_ARRAY(&twr_range_measr->rfu[0], p, 12);
      } else {
        STREAM_TO_ARRAY(&twr_range_measr->rfu[0], p, 6);
      }
    }
  } else if (sRange_data.ranging_measure_type == MEASUREMENT_TYPE_ONEWAY) {
    for (uint8_t i = 0; i < sRange_data.no_of_measurements; i++) {
      tUWA_TDoA_RANGING_MEASR* tdoa_range_measr =
          (tUWA_TDoA_RANGING_MEASR*)&sRange_data.ranging_measures
              .tdoa_range_measr[i];
      uint16_t blink_payload_length = 0;
      uint16_t device_info_length = 0;
      if (ranging_measures_length < ONE_WAY_MEASUREMENT_LENGTH) {
        UCI_TRACE_E("%s: Invalid ranging_measures_length = %x", __func__,
                    ranging_measures_length);
        return;
      }
      ranging_measures_length -= ONE_WAY_MEASUREMENT_LENGTH;
      if (sRange_data.mac_addr_mode_indicator == SHORT_MAC_ADDRESS) {
        STREAM_TO_ARRAY(&tdoa_range_measr->mac_addr[0], p, MAC_SHORT_ADD_LEN);
      } else if (sRange_data.mac_addr_mode_indicator == EXTENDED_MAC_ADDRESS) {
        STREAM_TO_ARRAY(&tdoa_range_measr->mac_addr[0], p, MAC_EXT_ADD_LEN);
      } else {
        UCI_TRACE_E("%s: Invalid mac addressing indicator", __func__);
        return;
      }
      STREAM_TO_UINT8(tdoa_range_measr->frame_type, p);
      STREAM_TO_UINT8(tdoa_range_measr->nLos, p);
      STREAM_TO_UINT16(tdoa_range_measr->aoa_azimuth, p);
      STREAM_TO_UINT8(tdoa_range_measr->aoa_azimuth_FOM, p);
      STREAM_TO_UINT16(tdoa_range_measr->aoa_elevation, p);
      STREAM_TO_UINT8(tdoa_range_measr->aoa_elevation_FOM, p);
      STREAM_TO_UINT64(tdoa_range_measr->timeStamp, p);
      STREAM_TO_UINT32(tdoa_range_measr->blink_frame_number, p);
      if (sRange_data.mac_addr_mode_indicator == SHORT_MAC_ADDRESS) {
        STREAM_TO_ARRAY(&tdoa_range_measr->rfu[0], p, 12);
      } else {
        STREAM_TO_ARRAY(&tdoa_range_measr->rfu[0], p, 6);
      }
      STREAM_TO_UINT8(tdoa_range_measr->device_info_size, p);
      device_info_length = tdoa_range_measr->device_info_size;
      ranging_measures_length -= device_info_length;
      if (ranging_measures_length < device_info_length) {
        UCI_TRACE_E(
            "%s: Invalid ranging_measures_length to copy device_info_length = "
            "%x",
            __func__, ranging_measures_length);
        return;
      }
      STREAM_TO_ARRAY(&device_info_buffer[i][0], p,
                      tdoa_range_measr->device_info_size);
      tdoa_range_measr->device_info = &device_info_buffer[i][0];
      STREAM_TO_UINT8(tdoa_range_measr->blink_payload_size, p);
      blink_payload_length = tdoa_range_measr->blink_payload_size;
      ranging_measures_length -= blink_payload_length;
      if (ranging_measures_length < blink_payload_length) {
        UCI_TRACE_E(
            "%s: Invalid ranging_measures_length to copy blink_payload_length "
            "= %x",
            __func__, ranging_measures_length);
        return;
      }
      STREAM_TO_ARRAY(&blink_payload_buffer[i][0], p,
                      tdoa_range_measr->blink_payload_size);
      tdoa_range_measr->blink_payload_data = &blink_payload_buffer[i][0];
    }
  } else {
    UCI_TRACE_E("%s: Measurement type not matched", __func__);
  }
  uwb_response.sRange_data = sRange_data;

  (*uwb_cb.p_resp_cback)(UWB_RANGE_DATA_REVT, &uwb_response);

  UCI_TRACE_I("%s: ranging_measures_length = %d range_data_ntf_len = %d", __func__,ranging_measures_length,range_data_ntf_len);
  if (ranging_measures_length >= VENDOR_SPEC_INFO_LEN) {
     uint16_t vendor_specific_length =0;
     STREAM_TO_UINT16(vendor_specific_length, p);
     if (vendor_specific_length > 0) {
        if (vendor_specific_length > MAX_VENDOR_INFO_LENGTH) {
            UCI_TRACE_E("%s: Invalid Range_data vendor_specific_length = %x",
                           __func__, vendor_specific_length);
            return;
        }

        uint8_t *range_data_with_vendor_info =  range_data_ntf_buffer;
        uwb_response.sVendor_specific_ntf.len = range_data_ntf_len;
        STREAM_TO_ARRAY(uwb_response.sVendor_specific_ntf.data, range_data_with_vendor_info, range_data_ntf_len);
        (*uwb_cb.p_resp_cback)(UWB_VENDOR_SPECIFIC_UCI_NTF_EVT, &uwb_response);
     }
  }
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_send_blink_data_ntf
 **
 ** Description      This function is called to process blink data tx
 **                  notification
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_send_blink_data_ntf(uint8_t* p_buf, uint16_t len) {
  tUWB_SEND_BLINK_DATA_NTF_REVT blink_data_tx_ntf;
  tUWB_RESPONSE uwb_response;
  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  memset(&blink_data_tx_ntf, 0, sizeof(tUWB_SEND_BLINK_DATA_NTF_REVT));
  if (len != 0) {
    STREAM_TO_UINT8(blink_data_tx_ntf.repetition_count_status, p_buf);
  } else {
    UCI_TRACE_E("blink_data_tx ntf error");
  }
  uwb_response.sSend_blink_data_ntf = blink_data_tx_ntf;

  (*uwb_cb.p_resp_cback)(UWB_BLINK_DATA_TX_NTF_REVT, &uwb_response);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_android_set_country_code_status
 **
 ** Description      This function is called to set country code status
 **                  notification
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_android_set_country_code_status(uint8_t* p_buf,
                                                   uint16_t len) {
  tUWB_RESPONSE uwb_response;
  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  uint8_t status = *p_buf;

  UCI_TRACE_I("uwb_ucif_proc_android_set_country_code_status country code status = %x", status);
  uwb_response.sSet_country_code_status.status = status;
  uwb_cb.device_state = status;

  (*uwb_cb.p_resp_cback)(UWB_SET_COUNTRY_CODE_REVT, &uwb_response);
  if (status == UWBS_STATUS_ERROR) {
    uwb_stop_quick_timer(&uwb_cb.uci_wait_rsp_timer);
    uwb_ucif_uwb_recovery();
  }
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_conformance_ntf
 **
 ** Description      This function is called to process conformance test ntf
 **
 ** Returns          void
 **
 *******************************************************************************/

void uwb_ucif_proc_conformance_ntf(uint8_t* p_buf, uint16_t len) {
  tUWB_RESPONSE uwb_response;
  tUWB_CONFORMANCE_TEST_DATA conformance_data_ntf;

  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  memset(&conformance_data_ntf, 0, sizeof(tUWB_CONFORMANCE_TEST_DATA));
  if (len < CONFORMANCE_TEST_MAX_UCI_PKT_LENGTH) {
    conformance_data_ntf.length = len;
    STREAM_TO_ARRAY(&conformance_data_ntf.data[0], p_buf, len);
  } else {
    conformance_data_ntf.length = CONFORMANCE_TEST_MAX_UCI_PKT_LENGTH;
    STREAM_TO_ARRAY(&conformance_data_ntf.data[0], p_buf,
                    CONFORMANCE_TEST_MAX_UCI_PKT_LENGTH);
  }
  uwb_response.sConformance_test_data = conformance_data_ntf;
  (*uwb_cb.p_resp_cback)(UWB_CONFORMANCE_TEST_DATA, &uwb_response);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_session_status
 **
 ** Description      This function is called to process session related
 **                  notification
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_session_status(uint8_t* p_buf, uint16_t len) {
  tUWB_SESSION_NTF_REVT sessionNtf;
  tUWB_RESPONSE uwb_response;
  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  memset(&sessionNtf, 0, sizeof(tUWB_SESSION_NTF_REVT));
  if (len != 0) {
    STREAM_TO_UINT32(sessionNtf.session_id, p_buf);
    STREAM_TO_UINT8(sessionNtf.state, p_buf);
    STREAM_TO_UINT8(sessionNtf.reason_code, p_buf);
  } else {
    UCI_TRACE_E("session ntf error");
  }
  uwb_response.sSessionStatus = sessionNtf;

  (*uwb_cb.p_resp_cback)(UWB_SESSION_STATUS_NTF_REVT, &uwb_response);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_multicast_list_update_ntf
 **
 ** Description      This function is called to process multicast list update
 **                  notification
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_multicast_list_update_ntf(uint8_t* p_buf, uint16_t len) {
  tUWB_SESSION_UPDATE_MULTICAST_LIST_NTF_REVT sMulticast_list_ntf;
  tUWB_RESPONSE uwb_response;
  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  memset(&sMulticast_list_ntf, 0,
         sizeof(tUWB_SESSION_UPDATE_MULTICAST_LIST_NTF_REVT));
  if (len != 0) {
    STREAM_TO_UINT32(sMulticast_list_ntf.session_id, p_buf);
    STREAM_TO_UINT8(sMulticast_list_ntf.remaining_list, p_buf);
    STREAM_TO_UINT8(sMulticast_list_ntf.no_of_controlees, p_buf);
    if (sMulticast_list_ntf.no_of_controlees > MAX_NUM_CONTROLLEES) {
      UCI_TRACE_E("%s: wrong number of controless : %d", __func__,
                  sMulticast_list_ntf.no_of_controlees);
      return;
    }
    for (uint8_t i = 0; i < sMulticast_list_ntf.no_of_controlees; i++) {
      REVERSE_STREAM_TO_ARRAY(
          &sMulticast_list_ntf.controlee_mac_address_list[i], p_buf,
          SHORT_ADDRESS_LEN);
      STREAM_TO_UINT32(sMulticast_list_ntf.subsession_id_list[i], p_buf);
      STREAM_TO_UINT8(sMulticast_list_ntf.status_list[i], p_buf);
    }
  } else {
    UCI_TRACE_E("multicast list update ntf error");
  }
  uwb_response.sMulticast_list_ntf = sMulticast_list_ntf;

  (*uwb_cb.p_resp_cback)(UWB_SESSION_UPDATE_MULTICAST_LIST_NTF_REVT,
                         &uwb_response);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_get_device_info_rsp
 **
 ** Description      This function is called to process get device info response
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_get_device_info_rsp(uint8_t* p_buf, uint16_t len) {
  tUWB_RESPONSE evt_data;
  uint8_t* p = p_buf;
  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  memset(&evt_data.sGet_device_info, 0, sizeof(tUWB_GET_DEVICE_INFO_REVT));
  evt_data.sGet_device_info.status = *p++;
  STREAM_TO_UINT16(evt_data.sGet_device_info.uci_version, p);
  STREAM_TO_UINT16(evt_data.sGet_device_info.mac_version, p);
  STREAM_TO_UINT16(evt_data.sGet_device_info.phy_version, p);
  STREAM_TO_UINT16(evt_data.sGet_device_info.uciTest_version, p);
  STREAM_TO_UINT8(evt_data.sGet_device_info.vendor_info_len, p);
  STREAM_TO_ARRAY(evt_data.sGet_device_info.vendor_info, p,
                  evt_data.sGet_device_info.vendor_info_len);

  (*uwb_cb.p_resp_cback)(UWB_GET_DEVICE_INFO_REVT, &evt_data);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_get_device_capability_rsp
 **
 ** Description      This function is called to process get device capability
 **                  response.
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_get_device_capability_rsp(uint8_t* p_buf, uint16_t len) {
  tUWB_RESPONSE evt_data;
  tUWB_STATUS status;
  uint8_t* p = p_buf;

  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  status = *p++;
  UCI_TRACE_I("StatusName:%s and StatusValue:%d", UWB_GetStatusName(status),
              status);
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  evt_data.sGet_device_capability.status = status;
  evt_data.sGet_device_capability.no_of_tlvs = *p++;
  evt_data.sGet_device_capability.tlv_buffer_len =
      (uint16_t)(len - CONFIG_TLV_OFFSET);
  if (evt_data.sGet_device_capability.tlv_buffer_len > 0) {
    memcpy(evt_data.sGet_device_capability.tlv_buffer, p,
           evt_data.sGet_device_capability.tlv_buffer_len);
  }

  (*uwb_cb.p_resp_cback)(UWB_CORE_GET_DEVICE_CAPABILITY_REVT, &evt_data);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_test_get_config_status
 **
 ** Description      This function is called to process get test config response
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_test_get_config_status(uint8_t* p_buf, uint16_t len) {
  tUWB_TEST_RESPONSE evt_data;
  tUWB_STATUS status;
  uint8_t* p = p_buf;

  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  status = *p++;
  UCI_TRACE_I("Status:%s", UWB_GetStatusName(status));
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  evt_data.sTest_get_config.status = status;
  evt_data.sTest_get_config.no_of_ids = *p++;
  evt_data.sTest_get_config.tlv_size = (uint16_t)(len - CONFIG_TLV_OFFSET);
  if (evt_data.sTest_get_config.tlv_size > 0) {
    memcpy(evt_data.sTest_get_config.p_param_tlvs, p,
           evt_data.sTest_get_config.tlv_size);
  }
  (*uwb_cb.p_test_resp_cback)(UWB_TEST_GET_CONFIG_REVT, &evt_data);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_test_set_config_status
 **
 ** Description      This function is called to report UWB_SET_TEST_CONFIG_REVT
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_test_set_config_status(uint8_t* p_buf, uint16_t len) {
  tUWB_TEST_RESPONSE evt_data;
  tUWB_STATUS status;
  uint8_t* p = p_buf;

  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  status = *p++;
  UCI_TRACE_I("Status:%s", UWB_GetStatusName(status));
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  evt_data.sTest_set_config.status = status;
  evt_data.sTest_set_config.num_param_id = *p++;
  evt_data.sTest_set_config.tlv_size = (uint16_t)(len - CONFIG_TLV_OFFSET);
  if (evt_data.sTest_set_config.tlv_size > 0) {
    STREAM_TO_ARRAY(evt_data.sTest_set_config.param_ids, p,
                    evt_data.sTest_set_config.tlv_size);
  }
  (*uwb_cb.p_test_resp_cback)(UWB_TEST_SET_CONFIG_REVT, &evt_data);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_test_management_status
 **
 ** Description      This function is called to process test command responses
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_test_management_status(tUWB_TEST_RESPONSE_EVT event,
                                     uint8_t* p_buf, uint16_t len) {
  tUWB_TEST_RESPONSE evt_data;
  tUWB_TEST_RESPONSE_EVT evt = 0;
  tUWB_STATUS status;
  uint8_t* p = p_buf;

  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  status = *p++;
  UCI_TRACE_I("Status:%s", UWB_GetStatusName(status));
  switch (event) {
    case UWB_TEST_PERIODIC_TX_REVT:
      evt = UWB_TEST_PERIODIC_TX_REVT;
      evt_data.status = status;
      break;
    case UWB_TEST_PER_RX_REVT:
      evt = UWB_TEST_PER_RX_REVT;
      evt_data.status = status;
      break;
    case UWB_TEST_STOP_SESSION_REVT:
      evt = UWB_TEST_STOP_SESSION_REVT;
      evt_data.status = status;
      break;
    case UWB_TEST_LOOPBACK_REVT:
      evt = UWB_TEST_LOOPBACK_REVT;
      evt_data.status = status;
      break;
    case UWB_TEST_RX_REVT:
      evt = UWB_TEST_RX_REVT;
      evt_data.status = status;
      break;
    default:
      UCI_TRACE_E("unknown response event %x", event);
  }
  if (evt) {
    (*uwb_cb.p_test_resp_cback)(evt, &evt_data);
  }
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_proc_rf_test_data
 **
 ** Description      This function is called to report the RF test notifications
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_proc_rf_test_data(tUWB_TEST_RESPONSE_EVT event, uint8_t* p_buf,
                                uint16_t len) {
  tUWB_RF_TEST_DATA rf_test_data;
  tUWB_TEST_RESPONSE uwb_response;
  if (len == 0) {
    UCI_TRACE_E("%s: len is zero", __func__);
    return;
  }
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  memset(&rf_test_data, 0, sizeof(tUWB_RF_TEST_DATA));
  rf_test_data.length = len;
  memcpy(&rf_test_data.data[0], p_buf, len);
  uwb_response.sRf_test_result = rf_test_data;

  (*uwb_cb.p_test_resp_cback)(event, &uwb_response);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_event_status
 **
 ** Description      This function is called to report the event
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_event_status(tUWB_RESPONSE_EVT event, uint8_t status) {
  tUWB_RESPONSE uwb_response;

  UCI_TRACE_E("Timeout error ");
  if (uwb_cb.p_resp_cback == NULL) {
    UCI_TRACE_E("%s: response callback is null", __func__);
    return;
  }
  uwb_response.status = status;
  (*uwb_cb.p_resp_cback)(event, &uwb_response);
}

/*******************************************************************************
 **
 ** Function         uwb_ucif_uwb_recovery
 **
 ** Description      uwb recovery
 **                  1) spi reset
 **                  2) FW download
 **
 ** Returns          void
 **
 *******************************************************************************/
void uwb_ucif_uwb_recovery(void) {
  uint8_t stat;
  UCI_TRACE_I("uwb_ucif_uwb_recovery");
  if (uwb_cb.is_recovery_in_progress) {
    UCI_TRACE_I("uwb_ucif_uwb_recovery: recovery is already in progreess");
    return;
  }
  uwb_cb.cmd_retry_count = 0;
  uwb_cb.is_resp_pending = false;
  uwb_cb.is_recovery_in_progress = true;

  if (uwb_cb.uwb_state == UWB_STATE_W4_HAL_CLOSE ||
      uwb_cb.uwb_state == UWB_STATE_NONE) {
    UCI_TRACE_E("%s: HAL is not initialized", __func__);
    uwb_cb.is_recovery_in_progress = false;
    return;
  }
  stat = uwb_cb.p_hal->CoreInitialization();
  if (stat == UWA_STATUS_OK) {
    UCI_TRACE_I("%s: uwb fw download successfull", __func__);
  } else {
    UCI_TRACE_E("%s: uwb fw download Failed", __func__);
  }
  uwb_main_flush_cmd_queue();
  uwb_cb.is_recovery_in_progress = false;
}

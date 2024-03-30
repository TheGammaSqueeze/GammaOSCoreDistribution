/******************************************************************************
 *
 *  Copyright (C) 2010-2014 Broadcom Corporation
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
 *  The original Work has been changed by NXP Semiconductors.
 *
 *  Copyright (C) 2015-2018 NXP Semiconductors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
 *  NFA interface for device management
 *
 ******************************************************************************/
#include <string.h>

#include <android-base/stringprintf.h>
#include <base/logging.h>

#include "nfa_api.h"
#include "nfa_sys_int.h"
#include "nfa_dm_int.h"

#if (NXP_EXTNS == TRUE)
#include "nfc_int.h"
#endif

using android::base::StringPrintf;

extern bool nfc_debug_enabled;

/*****************************************************************************
**  Constants
*****************************************************************************/

/*****************************************************************************
**  APIs
*****************************************************************************/
/*******************************************************************************
**
** Function         NFA_Init
**
** Description      This function initializes control blocks for NFA
**
**                  p_hal_entry_tbl points to a table of HAL entry points
**
**                  NOTE: the buffer that p_hal_entry_tbl points must be
**                  persistent until NFA is disabled.
**
** Returns          none
**
*******************************************************************************/
void NFA_Init(tHAL_NFC_ENTRY *p_hal_entry_tbl) {
  nfc_debug_enabled = true;
  DLOG_IF(INFO, nfc_debug_enabled) << __func__;
  nfa_sys_init();
  nfa_dm_init();
  nfa_hci_init();
  nfa_ee_init();
  /* Initialize NFC module */
  NFC_Init(p_hal_entry_tbl);
}

/*******************************************************************************
**
** Function         NFA_Enable
**
** Description      This function enables NFC. Prior to calling NFA_Enable,
**                  the NFCC must be powered up, and ready to receive commands.
**                  This function enables the tasks needed by NFC, opens the NCI
**                  transport, resets the NFC controller, downloads patches to
**                  the NFCC (if necessary), and initializes the NFC subsystems.
**
**                  This function should only be called once - typically when
**                  NFC is enabled during boot-up, or when NFC is enabled from a
**                  settings UI. Subsequent calls to NFA_Enable while NFA is
**                  enabling or enabled will be ignored. When the NFC startup
**                  procedure is completed, an NFA_DM_ENABLE_EVT is returned to
**                  the application using the tNFA_DM_CBACK.
**
** Returns          NFA_STATUS_OK if successfully initiated
**                  NFA_STATUS_FAILED otherwise
**
*******************************************************************************/
tNFA_STATUS NFA_Enable(tNFA_DM_CBACK *p_dm_cback,
                       tNFA_CONN_CBACK *p_conn_cback) {
  tNFA_DM_API_ENABLE *p_msg;

  DLOG_IF(INFO, nfc_debug_enabled) << __func__;

  /* Validate parameters */
  if ((!p_dm_cback) || (!p_conn_cback)) {
    LOG(ERROR) << StringPrintf("NFA_Enable (): error null callback");
    return (NFA_STATUS_FAILED);
  }

  p_msg = (tNFA_DM_API_ENABLE *)GKI_getbuf(sizeof(tNFA_DM_API_ENABLE));
  if (p_msg != NULL) {
    p_msg->hdr.event = NFA_DM_API_ENABLE_EVT;
    p_msg->p_dm_cback = p_dm_cback;
    p_msg->p_conn_cback = p_conn_cback;

    nfa_sys_sendmsg(p_msg);

    return (NFA_STATUS_OK);
  }

  return (NFA_STATUS_FAILED);
}

/*******************************************************************************
**
** Function         NFA_Disable
**
** Description      This function is called to shutdown NFC. The tasks for NFC
**                  are terminated, and clean up routines are performed. This
**                  function is typically called during platform shut-down, or
**                  when NFC is disabled from a settings UI. When the NFC
**                  shutdown procedure is completed, an NFA_DM_DISABLE_EVT is
**                  returned to the application using the tNFA_DM_CBACK.
**
**                  The platform should wait until the NFC_DISABLE_REVT is
**                  received before powering down the NFC chip and NCI
**                  transport. This is required to so that NFA can gracefully
**                  shut down any open connections.
**
** Returns          NFA_STATUS_OK if successfully initiated
**                  NFA_STATUS_FAILED otherwise
**
*******************************************************************************/
tNFA_STATUS NFA_Disable(bool graceful) {
  tNFA_DM_API_DISABLE *p_msg;

  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("NFA_Disable (graceful=%i)", graceful);

  p_msg = (tNFA_DM_API_DISABLE *)GKI_getbuf(sizeof(tNFA_DM_API_DISABLE));
  if (p_msg != NULL) {
    p_msg->hdr.event = NFA_DM_API_DISABLE_EVT;
    p_msg->graceful = graceful;

    nfa_sys_sendmsg(p_msg);

    return (NFA_STATUS_OK);
  }

  return (NFA_STATUS_FAILED);
}

/*******************************************************************************
**
** Function         NFA_GetNCIVersion
**
** Description      Returns the NCI version of the NFCC to upper layer
**
**
** Returns          NCI version NCI2.0 / NCI1.0
**
*******************************************************************************/
uint8_t NFA_GetNCIVersion() { return NFC_GetNCIVersion(); }

/*******************************************************************************
**
** Function         NFA_SendRawFrame
**
** Description      Send a raw frame over the activated interface with the NFCC.
**                  This function can only be called after NFC link is
**                  activated.
**
**                  If the activated interface is a tag and auto-presence check
**                  is enabled then presence_check_start_delay can be used to
**                  indicate the delay in msec after which the next auto
**                  presence check command can be sent.
**                  NFA_DM_DEFAULT_PRESENCE_CHECK_START_DELAY can be used as the
**                  default value for the delay.
**
** Returns          NFA_STATUS_OK if successfully initiated
**                  NFA_STATUS_FAILED otherwise
**
*******************************************************************************/
tNFA_STATUS NFA_SendRawFrame(uint8_t *p_raw_data, uint16_t data_len,
                             uint16_t presence_check_start_delay) {
  NFC_HDR *p_msg;
  uint16_t size;
  uint8_t *p;
#if (NXP_EXTNS == TRUE)
  tNFC_CONN_CB *p_cb = &nfc_cb.conn_cb[NFC_RF_CONN_ID];
#endif

  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("NFA_SendRawFrame () data_len:%d", data_len);

  /* Validate parameters */
  if (nfcFL.nfccFL._NXP_NFCC_EMPTY_DATA_PACKET) {
    if (((data_len == 0) || (p_raw_data == NULL)) &&
        (!(nfa_dm_cb.disc_cb.disc_state == NFA_DM_RFST_LISTEN_ACTIVE &&
           nfa_dm_cb.disc_cb.activated_protocol == NFA_PROTOCOL_T3T))) {
      return (NFA_STATUS_INVALID_PARAM);
    } else {
      // Do Nothing.
    }
  } else if ((data_len == 0) || (p_raw_data == NULL)) {
    return (NFA_STATUS_INVALID_PARAM);
  }

  size = NFC_HDR_SIZE + NCI_MSG_OFFSET_SIZE + NCI_DATA_HDR_SIZE + data_len;
  p_msg = (NFC_HDR *)GKI_getbuf(size);

  if (p_msg != NULL) {
    p_msg->event = NFA_DM_API_RAW_FRAME_EVT;
    p_msg->layer_specific = presence_check_start_delay;
    p_msg->offset = NCI_MSG_OFFSET_SIZE + NCI_DATA_HDR_SIZE;
    p_msg->len = data_len;
#if (NXP_EXTNS == TRUE)
    if (data_len <= p_cb->buff_size) {
#endif
      p = (uint8_t *)(p_msg + 1) + p_msg->offset;
      if (nfcFL.nfccFL._NXP_NFCC_EMPTY_DATA_PACKET) {
        if (p_raw_data != NULL) {
          memcpy(p, p_raw_data, data_len);
        }
      } else {
        memcpy(p, p_raw_data, data_len);
      }
#if (NXP_EXTNS == TRUE)
    }
#endif

    nfa_sys_sendmsg(p_msg);

    return (NFA_STATUS_OK);
  }

  return (NFA_STATUS_FAILED);
}

tNFA_STATUS NFA_SendRawFrame(uint8_t *p_raw_data, uint16_t data_len,
                             uint16_t presence_check_start_delay,
                             tNFC_VS_CBACK *p_cback) {
  NFC_HDR *p_msg;
  uint16_t size;
  uint8_t *p;

  NFC_SetNfceeCback(p_cback);

  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("NFA_SendRawFrame () data_len:%d", data_len);

/* Validate parameters */
#if (NFC_NXP_NOT_OPEN_INCLUDED == TRUE && NXP_NFCC_EMPTY_DATA_PACKET == TRUE)
  if (((data_len == 0) || (p_raw_data == NULL)) &&
      (!(nfa_dm_cb.disc_cb.disc_state == NFA_DM_RFST_LISTEN_ACTIVE &&
         nfa_dm_cb.disc_cb.activated_protocol == NFA_PROTOCOL_T3T)))
#else
  if ((data_len == 0) || (p_raw_data == NULL))
#endif
    return (NFA_STATUS_INVALID_PARAM);

  size = NFC_HDR_SIZE + NCI_MSG_OFFSET_SIZE + NCI_DATA_HDR_SIZE + data_len;
  p_msg = (NFC_HDR *)GKI_getbuf(size);
  if (p_msg != NULL) {
    p_msg->event = NFA_DM_API_RAW_FRAME_EVT;
    p_msg->layer_specific = presence_check_start_delay;
    p_msg->offset = NCI_MSG_OFFSET_SIZE + NCI_DATA_HDR_SIZE;
    p_msg->len = data_len;

    p = (uint8_t *)(p_msg + 1) + p_msg->offset;
#if (NFC_NXP_NOT_OPEN_INCLUDED == TRUE && NXP_NFCC_EMPTY_DATA_PACKET == TRUE)
    if (p_raw_data != NULL) {
      memcpy(p, p_raw_data, data_len);
    }
#else
    memcpy(p, p_raw_data, data_len);
#endif

    nfa_sys_sendmsg(p_msg);

    return (NFA_STATUS_OK);
  }

  return (NFA_STATUS_FAILED);
}

/*******************************************************************************
**
** Function         NFA_RegVSCback
**
** Description      This function is called to register or de-register a
**                  callback function to receive Proprietary NCI response and
**                  notification events. The maximum number of callback
**                  functions allowed is NFC_NUM_VS_CBACKS
**
** Returns          tNFC_STATUS
**
*******************************************************************************/
tNFC_STATUS NFA_RegVSCback(bool is_register, tNFA_VSC_CBACK *p_cback) {
  tNFA_DM_API_REG_VSC *p_msg;

  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("NFA_RegVSCback() is_register=%d", is_register);

  if (p_cback == NULL) {
    LOG(ERROR) << StringPrintf(
        "NFA_RegVSCback() requires a valid callback function");
    return (NFA_STATUS_FAILED);
  }

  p_msg = (tNFA_DM_API_REG_VSC *)GKI_getbuf(sizeof(tNFA_DM_API_REG_VSC));
  if (p_msg != NULL) {
    p_msg->hdr.event = NFA_DM_API_REG_VSC_EVT;
    p_msg->is_register = is_register;
    p_msg->p_cback = p_cback;

    nfa_sys_sendmsg(p_msg);

    return (NFA_STATUS_OK);
  }

  return (NFA_STATUS_FAILED);
}

/*******************************************************************************
**
** Function         NFA_SendVsCommand
**
** Description      This function is called to send an NCI Vendor Specific
**                  command to NFCC.
**
**                  oid             - The opcode of the VS command.
**                  cmd_params_len  - The command parameter len
**                  p_cmd_params    - The command parameter
**                  p_cback         - The callback function to receive the
**                                    command status
**
** Returns          NFA_STATUS_OK if successfully initiated
**                  NFA_STATUS_FAILED otherwise
**
*******************************************************************************/
tNFA_STATUS NFA_SendVsCommand(uint8_t oid, uint8_t cmd_params_len,
                              uint8_t *p_cmd_params, tNFA_VSC_CBACK *p_cback) {
  tNFA_DM_API_SEND_VSC *p_msg;
  uint16_t size = sizeof(tNFA_DM_API_SEND_VSC) + cmd_params_len;

  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("NFA_SendVsCommand() oid=0x%x", oid);

  p_msg = (tNFA_DM_API_SEND_VSC *)GKI_getbuf(size);
  if (p_msg != NULL) {
    p_msg->hdr.event = NFA_DM_API_SEND_VSC_EVT;
    p_msg->oid = oid;
    p_msg->p_cback = p_cback;
    if (cmd_params_len && p_cmd_params) {
      p_msg->cmd_params_len = cmd_params_len;
      p_msg->p_cmd_params = (uint8_t *)(p_msg + 1);
      memcpy(p_msg->p_cmd_params, p_cmd_params, cmd_params_len);
    } else {
      p_msg->cmd_params_len = 0;
      p_msg->p_cmd_params = NULL;
    }

    nfa_sys_sendmsg(p_msg);

    return (NFA_STATUS_OK);
  }

  return (NFA_STATUS_FAILED);
}

/*******************************************************************************
**
** Function         NFA_SendRawVsCommand
**
** Description      This function is called to send raw Vendor Specific
**                  command to NFCC.
**
**                  cmd_params_len  - The command parameter len
**                  p_cmd_params    - The command parameter
**                  p_cback         - The callback function to receive the
**                                    command
**
** Returns          NFA_STATUS_OK if successfully initiated
**                  NFA_STATUS_FAILED otherwise
**
*******************************************************************************/
tNFA_STATUS NFA_SendRawVsCommand(uint8_t cmd_params_len, uint8_t *p_cmd_params,
                                 tNFA_VSC_CBACK *p_cback) {
  if (cmd_params_len == 0x00 || p_cmd_params == NULL || p_cback == NULL) {
    return NFA_STATUS_INVALID_PARAM;
  }
  uint16_t size = sizeof(tNFA_DM_API_SEND_VSC) + cmd_params_len;
  tNFA_DM_API_SEND_VSC *p_msg = (tNFA_DM_API_SEND_VSC *)GKI_getbuf(size);

  if (p_msg != NULL) {
    p_msg->hdr.event = NFA_DM_API_SEND_RAW_VS_EVT;
    p_msg->p_cback = p_cback;
    p_msg->cmd_params_len = cmd_params_len;
    p_msg->p_cmd_params = (uint8_t *)(p_msg + 1);
    memcpy(p_msg->p_cmd_params, p_cmd_params, cmd_params_len);
    nfa_sys_sendmsg(p_msg);

    return NFA_STATUS_OK;
  }

  return NFA_STATUS_FAILED;
}

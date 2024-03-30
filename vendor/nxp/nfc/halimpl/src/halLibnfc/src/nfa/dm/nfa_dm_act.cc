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
 *  Copyright 2015-2020 NXP
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
 *  This file contains the action functions for device manager state
 *  machine.
 *
 ******************************************************************************/
#include <string.h>

#include <android-base/stringprintf.h>
#include <base/logging.h>

#include "nci_hmsgs.h"
#include "nfa_dm_int.h"
#include "nfc_api.h"
#include "nfa_ee_int.h"
#if (NFC_NFCEE_INCLUDED == true)
#include "nfc_int.h"
#endif

#if (NFA_SNEP_INCLUDED == true)
#include "nfa_snep_int.h"
#endif

using android::base::StringPrintf;

extern bool nfc_debug_enabled;

/* This is the timeout value to guarantee disable is performed within reasonable
 * amount of time */
#ifndef NFA_DM_DISABLE_TIMEOUT_VAL
#define NFA_DM_DISABLE_TIMEOUT_VAL 1000
#endif

/*******************************************************************************
**
** Function         nfa_dm_module_init_cback
**
** Description      Processing initialization complete event from sub-modules
**
** Returns          None
**
*******************************************************************************/
static void nfa_dm_module_init_cback(void) {
  tNFA_DM_CBACK_DATA dm_cback_data;

  nfa_dm_cb.flags &= ~NFA_DM_FLAGS_ENABLE_EVT_PEND;

  /* All subsystem are initialized */
  dm_cback_data.status = NFA_STATUS_OK;
  (*nfa_dm_cb.p_dm_cback)(NFA_DM_ENABLE_EVT, &dm_cback_data);
}

/*******************************************************************************
**
** Function         nfa_dm_sys_enable
**
** Description      This function on enable
**
** Returns          void
**
*******************************************************************************/
void nfa_dm_sys_enable(void) {}

/*******************************************************************************
**
** Function         nfa_dm_disable_event
**
** Description      report disable event
**
** Returns          void
**
*******************************************************************************/
static void nfa_dm_disable_event(void) {
  /* Deregister DM from sys */
  nfa_sys_deregister(NFA_ID_DM);

  /* Notify app */
  nfa_dm_cb.flags &=
      ~(NFA_DM_FLAGS_DM_IS_ACTIVE | NFA_DM_FLAGS_DM_DISABLING_NFC |
        NFA_DM_FLAGS_ENABLE_EVT_PEND);
  (*nfa_dm_cb.p_dm_cback)(NFA_DM_DISABLE_EVT, NULL);
}

/*******************************************************************************
**
** Function         nfa_dm_nfc_response_cback
**
** Description      Call DM event hanlder with NFC response callback data
**
** Returns          void
**
*******************************************************************************/
static void nfa_dm_nfc_response_cback(tNFC_RESPONSE_EVT event,
                                      tNFC_RESPONSE *p_data) {
  tNFA_DM_CBACK_DATA dm_cback_data;
  uint8_t dm_cback_evt;

  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("%s(0x%x)", nfa_dm_nfc_revt_2_str(event).c_str(), event);

  switch (event) {
  case NFC_ENABLE_REVT: /* 0  Enable event */
    /* NFC stack enabled. Enable nfa sub-systems */
    if (p_data->enable.status == NFC_STATUS_OK) {
      /* Initialize NFA subsystems */
      nfa_sys_enable_subsystems();
    } else if (nfa_dm_cb.flags & NFA_DM_FLAGS_ENABLE_EVT_PEND) {
      /* Notify app */
      nfa_dm_cb.flags &=
          ~(NFA_DM_FLAGS_ENABLE_EVT_PEND | NFA_DM_FLAGS_DM_IS_ACTIVE);
      dm_cback_data.status = p_data->enable.status;
      (*nfa_dm_cb.p_dm_cback)(NFA_DM_ENABLE_EVT, &dm_cback_data);
    }
    break;

  case NFC_DISABLE_REVT: /* 1  Disable event */
    nfa_dm_disable_event();
    break;

  case HAL_NFC_WRITE_COMPLETE:
    tNFA_DM_CBACK_DATA write_data;
    write_data.write_status = p_data->write_status;
    (*nfa_dm_cb.p_dm_cback)(HAL_NFC_WRITE_COMPLETE, &write_data);
    break;

#if (NFC_NFCEE_INCLUDED == true)
  case NFC_NFCEE_DISCOVER_REVT: /* NFCEE Discover response */
  case NFC_NFCEE_INFO_REVT:     /* NFCEE Discover Notification */
  case NFC_EE_ACTION_REVT:      /* EE Action notification */
  case NFC_NFCEE_MODE_SET_REVT: /* NFCEE Mode Set response */
  case NFC_NFCEE_PWR_LNK_CTRL_REVT:
  case NFC_NFCEE_PL_CONTROL_REVT:
  case NFC_NFCEE_MODE_SET_INFO:
  case NFC_SET_ROUTING_REVT:    /* Configure Routing response */
  case NFC_EE_DISCOVER_REQ_REVT: /* EE Discover Req notification */
    nfa_ee_proc_evt(event, p_data);
      break;
  case NFC_RF_FIELD_REVT: /* RF Field information            */
    dm_cback_data.rf_field.status = NFA_STATUS_OK;
    dm_cback_data.rf_field.rf_field_status = p_data->rf_field.rf_field;
    (*nfa_dm_cb.p_dm_cback)(NFA_DM_RF_FIELD_EVT, &dm_cback_data);
    break;
  case NFC_NFCC_TIMEOUT_REVT:
    break;
#endif
  case NFC_NFCC_TRANSPORT_ERR_REVT:
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("flags:0x%08x", nfa_dm_cb.flags);
    dm_cback_evt = NFA_DM_NFCC_TRANSPORT_ERR_EVT;
    (*nfa_dm_cb.p_dm_cback)(dm_cback_evt, NULL);
    break;
  default:
    break;
  }
}

/*******************************************************************************
**
** Function         nfa_dm_enable
**
** Description      Initialises the NFC device manager
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool nfa_dm_enable(tNFA_DM_MSG *p_data) {
  LOG(ERROR) << StringPrintf(" nfa_dm_enable ()");
  LOG(ERROR) << StringPrintf("  nfa_dm_enable ()");

  /* Check if NFA is already enabled */
  if (!(nfa_dm_cb.flags & NFA_DM_FLAGS_DM_IS_ACTIVE)) {
    /* Initialize BRCM control block, it musb be called before setting any flags
     */
    nfa_dm_cb.flags |=
        (NFA_DM_FLAGS_DM_IS_ACTIVE | NFA_DM_FLAGS_ENABLE_EVT_PEND);
    nfa_sys_cback_reg_enable_complete(nfa_dm_module_init_cback);

    /* Store Enable parameters */
    nfa_dm_cb.p_dm_cback = p_data->enable.p_dm_cback;
    nfa_dm_cb.p_conn_cback = p_data->enable.p_conn_cback;

    /* Enable NFC stack */
    NFC_Enable(nfa_dm_nfc_response_cback);
  } else {
    LOG(ERROR) << StringPrintf("nfa_dm_enable: ALREADY ENABLED, still registering halLibnfc Callback");
    NFC_Enable(nfa_dm_nfc_response_cback);
  }

  return (true);
}
/*******************************************************************************
**
** Function         nfa_dm_disable
**
** Description      Disables the NFC device manager
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool nfa_dm_disable(tNFA_DM_MSG *p_data) {
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
      "nfa_dm_disable (): graceful:%d", p_data->disable.graceful);

  if (p_data->disable.graceful) {
    /* if RF discovery is enabled */
    if (nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_ENABLED) {
      nfa_dm_cb.disc_cb.disc_flags &= ~NFA_DM_DISC_FLAGS_ENABLED;

      if (nfa_dm_cb.disc_cb.disc_state == NFA_DM_RFST_IDLE) {
        /* if waiting RSP in idle state */
        if (nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_W4_RSP) {
          nfa_dm_cb.disc_cb.disc_flags |= NFA_DM_DISC_FLAGS_DISABLING;
        }
      } else {
        nfa_dm_cb.disc_cb.disc_flags |= NFA_DM_DISC_FLAGS_DISABLING;
        tNFA_DM_RF_DISC_DATA nfa_dm_rf_disc_data;
        nfa_dm_rf_disc_data.deactivate_type = NFA_DEACTIVATE_TYPE_IDLE;

        nfa_dm_disc_sm_execute(NFA_DM_RF_DEACTIVATE_CMD, &nfa_dm_rf_disc_data);
        if ((nfa_dm_cb.disc_cb.disc_flags &
             (NFA_DM_DISC_FLAGS_W4_RSP | NFA_DM_DISC_FLAGS_W4_NTF)) == 0) {
          /* not waiting to deactivate, clear the flag now */
          nfa_dm_cb.disc_cb.disc_flags &= ~NFA_DM_DISC_FLAGS_DISABLING;
        }
      }
    }
  }

  /* Disable all subsystems other than DM (DM will be disabled after all  */
  /* the other subsystem have been disabled)                              */
  nfa_sys_disable_subsystems(p_data->disable.graceful);
  return (true);
}

/*******************************************************************************
**
** Function         nfa_dm_disable_complete
**
** Description      Called when all NFA subsytems are disabled.
**
**                  NFC core stack can now be disabled.
**
** Returns          void
**
*******************************************************************************/
void nfa_dm_disable_complete(void) {
  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("nfa_dm_disable_complete ()");

  if ((nfa_dm_cb.flags & NFA_DM_FLAGS_DM_DISABLING_NFC) == 0) {
    DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
        "nfa_dm_disable_complete (): proceeding with nfc core shutdown.");

    nfa_dm_cb.flags |= NFA_DM_FLAGS_DM_DISABLING_NFC;

    nfa_sys_stop_timer(&nfa_dm_cb.tle);

    /* Disable nfc core stack */
    NFC_Disable();
  }
}

/*******************************************************************************
**
** Function         nfa_dm_conn_cback_event_notify
**
** Description      Notify application of CONN_CBACK event, using appropriate
**                  callback
**
** Returns          nothing
**
*******************************************************************************/
void nfa_dm_conn_cback_event_notify(uint8_t event, tNFA_CONN_EVT_DATA *p_data) {
  if (nfa_dm_cb.flags & NFA_DM_FLAGS_EXCL_RF_ACTIVE) {
    /* Use exclusive RF mode callback */
    if (nfa_dm_cb.p_excl_conn_cback)
      (*nfa_dm_cb.p_excl_conn_cback)(event, p_data);
  } else {
    (*nfa_dm_cb.p_conn_cback)(event, p_data);
  }
}

/*******************************************************************************
**
** Function         nfa_dm_act_reg_vsc
**
** Description      Process registers VSC callback
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool nfa_dm_act_reg_vsc(tNFA_DM_MSG *p_data) {
  if (NFC_RegVSCback(p_data->reg_vsc.is_register, p_data->reg_vsc.p_cback) !=
      NFC_STATUS_OK) {
    LOG(ERROR) << StringPrintf("NFC_RegVSCback failed");
  }
  return (true);
}

/*******************************************************************************
**
** Function         nfa_dm_act_send_vsc
**
** Description      Send the NCI Vendor Specific command to the NCI command
**                  queue
**
** Returns          false (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool nfa_dm_act_send_vsc(tNFA_DM_MSG *p_data) {
  NFC_HDR *p_cmd = (NFC_HDR *)p_data;

  p_cmd->offset = sizeof(tNFA_DM_API_SEND_VSC) - NFC_HDR_SIZE;
  p_cmd->len = p_data->send_vsc.cmd_params_len;
  NFC_SendVsCommand(p_data->send_vsc.oid, p_cmd, p_data->send_vsc.p_cback);

  /* Most dm action functions return true, so nfa-sys frees the GKI buffer
   * carrying the message,
   * This action function re-use the GKI buffer to send the VSC, so the GKI
   * buffer can not be freed by nfa-sys */
  return (false);
}

/*******************************************************************************
**
** Function         nfa_dm_act_send_raw_vs
**
** Description      Send the raw vs command to the NCI command queue
**
** Returns          FALSE (message buffer is NOT freed by caller)
**
*******************************************************************************/
bool nfa_dm_act_send_raw_vs(tNFA_DM_MSG *p_data) {
  NFC_HDR *p_cmd = (NFC_HDR *)p_data;

  p_cmd->offset = sizeof(tNFA_DM_API_SEND_VSC) - NFC_HDR_SIZE;
  p_cmd->len = p_data->send_vsc.cmd_params_len;
  NFC_SendRawVsCommand(p_cmd, p_data->send_vsc.p_cback);

  /* Most dm action functions return TRUE, so nfa-sys frees the GKI buffer
   * carrying the message,
   * This action function re-use the GKI buffer to send the VSC, so the GKI
   * buffer can not be freed by nfa-sys */
  return false;
}

/*******************************************************************************
**
** Function         nfa_dm_act_send_raw_frame
**
** Description      Send an raw frame on RF link
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool nfa_dm_act_send_raw_frame(tNFA_DM_MSG *p_data) {
  tNFC_STATUS status = NFC_STATUS_FAILED;

  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("nfa_dm_act_send_raw_frame ()");

  /* If NFC link is activated */
  /*  if ((nfa_dm_cb.disc_cb.disc_state == NFA_DM_RFST_POLL_ACTIVE) ||
        (nfa_dm_cb.disc_cb.disc_state == NFA_DM_RFST_LISTEN_ACTIVE)) {*/
  nfa_dm_cb.flags |= NFA_DM_FLAGS_RAW_FRAME;
  NFC_SetReassemblyFlag(false);
  /* If not in exclusive mode, and not activated for LISTEN, then forward raw
   * data to NFA_RW to send */
  {
    /* Remove nci Header added by upper libnfc-nci */
    NFC_HDR *p = (NFC_HDR *)p_data;
    uint8_t *conn_id = (uint8_t *)(p + 1) + p->offset;
    p->len -= 3;
    p->offset += 3;
    status = NFC_SendData(*conn_id, (NFC_HDR *)p_data);
    if (status != NFC_STATUS_OK) {
      NFC_SetReassemblyFlag(true);
    }
    /* Already freed or NCI layer will free buffer */
    return false;
  }
  //}
}

bool nfa_dm_is_hci_supported()
{
  bool status = false;
  if(NFA_GetNCIVersion() == NCI_VERSION_2_0)
    status = true;
  return status;
}
/*******************************************************************************
**
** Function         nfa_dm_act_set_rf_disc_duration
**
** Description      Set duration for RF discovery
**
** Returns          true (message buffer to be freed by caller)
**
*******************************************************************************/
bool nfa_dm_act_set_rf_disc_duration(tNFA_DM_MSG *p_data) {
  nfa_dm_cb.disc_cb.disc_duration = p_data->disc_duration.rf_disc_dur_ms;
  return (true);
}

/*******************************************************************************
**
** Function         nfa_dm_nfc_revt_2_str
**
** Description      convert nfc revt to string
**
*******************************************************************************/
std::string nfa_dm_nfc_revt_2_str(tNFC_RESPONSE_EVT event) {
  switch (event) {
  case NFC_ENABLE_REVT:
    return "NFC_ENABLE_REVT";

  case NFC_DISABLE_REVT:
    return "NFC_DISABLE_REVT";

  case NFC_RF_FIELD_REVT:
    return "NFC_RF_FIELD_REVT";

  case NFC_EE_ACTION_REVT:
    return "NFC_EE_ACTION_REVT";

  default:
    return "unknown revt";
    break;
  }
}

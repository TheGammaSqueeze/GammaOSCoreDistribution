/******************************************************************************
 *
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
 *  Copyright (C) 2013-2014 NXP Semiconductors
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
 *  This file contains the action functions for device manager discovery
 *  function.
 *
 ******************************************************************************/
#include "nci_hmsgs.h"
#include "nfa_api.h"
#include "nfa_dm_int.h"
#include "nfa_sys.h"
#include "nfa_sys_int.h"
#include <android-base/stringprintf.h>
#include <base/logging.h>
#include <string.h>

#include "nfc_int.h"

using android::base::StringPrintf;

extern bool nfc_debug_enabled;

static std::string nfa_dm_disc_state_2_str(uint8_t state);
static std::string nfa_dm_disc_event_2_str(uint8_t event);

/*******************************************************************************
**
** Function         nfa_dm_disc_get_rf_state
**
** Description      This function is used to get the NFC state.
**
**
** Returns          void
**
*******************************************************************************/
void nfa_dm_disc_get_rf_state(/*arg*/) {}

/*******************************************************************************
**
** Function         nfa_dm_disc_discovery_cback
**
** Description      Discovery callback event from NFC
**
** Returns          void
**
*******************************************************************************/
static void nfa_dm_disc_discovery_cback(tNFC_DISCOVER_EVT event,
                                        tNFC_DISCOVER *p_data) {
  tNFA_DM_RF_DISC_SM_EVENT dm_disc_event = NFA_DM_DISC_SM_MAX_EVENT;

  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("nfa_dm_disc_discovery_cback (): event:0x%X", event);

  switch (event) {
  case NFC_START_DEVT:
    dm_disc_event = NFA_DM_RF_DISCOVER_RSP;
    break;
  case NFC_RESULT_DEVT:
    dm_disc_event = NFA_DM_RF_DISCOVER_NTF;
    break;
  case NFC_SELECT_DEVT:
    dm_disc_event = NFA_DM_RF_DISCOVER_SELECT_RSP;
    break;
  case NFC_ACTIVATE_DEVT:
    dm_disc_event = NFA_DM_RF_INTF_ACTIVATED_NTF;
    break;
  case NFC_DEACTIVATE_DEVT:
    if (p_data->deactivate.is_ntf) {
      dm_disc_event = NFA_DM_RF_DEACTIVATE_NTF;
    } else
      dm_disc_event = NFA_DM_RF_DEACTIVATE_RSP;
    break;
  default:
    LOG(ERROR) << StringPrintf("Unexpected event");
    return;
  }

  nfa_dm_disc_sm_execute(dm_disc_event, (tNFA_DM_RF_DISC_DATA *)p_data);
}

/*******************************************************************************
**
** Function         nfa_dm_enable_discovery_cback
**
** Description      This api enables cback for RF related events
**
** Returns          NFA_STATUS_OK if success
**
*******************************************************************************/
void nfa_dm_enable_discovery_cback() { nfa_dm_disc_discovery_cback(0, NULL); }

/*******************************************************************************
**
** Function         nfa_dm_disc_notify_activation
**
** Description      Send RF activation notification to sub-module
**
** Returns          NFA_STATUS_OK if success
**
*******************************************************************************/
static tNFA_STATUS nfa_dm_disc_notify_activation(tNFC_DISCOVER *p_data) {
  p_data = 0;
#if 0
  uint8_t xx, host_id_in_LRT;
  uint8_t iso_dep_t3t__listen = NFA_DM_DISC_NUM_ENTRIES;

  tNFC_RF_TECH_N_MODE tech_n_mode = p_data->activate.rf_tech_param.mode;
  tNFC_PROTOCOL protocol = p_data->activate.protocol;

  tNFA_DM_DISC_TECH_PROTO_MASK activated_disc_mask;

  DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf(
      "nfa_dm_disc_notify_activation (): tech_n_mode:0x%X, proto:0x%X",
      tech_n_mode, protocol);

  if (nfa_dm_cb.disc_cb.excl_disc_entry.in_use) {
    nfa_dm_cb.disc_cb.activated_tech_mode = tech_n_mode;
    nfa_dm_cb.disc_cb.activated_rf_disc_id = p_data->activate.rf_disc_id;
    nfa_dm_cb.disc_cb.activated_rf_interface = p_data->activate.intf_param.type;
    nfa_dm_cb.disc_cb.activated_protocol = protocol;
    nfa_dm_cb.disc_cb.activated_handle = NFA_HANDLE_INVALID;

    if (protocol == NFC_PROTOCOL_KOVIO) {
      /* check whether it's new or repeated activation */
      if (nfa_dm_disc_handle_kovio_activation(
              p_data, nfa_dm_cb.disc_cb.excl_disc_entry.p_disc_cback)) {
        /* do not notify activation of Kovio to upper layer */
        return (NFA_STATUS_OK);
      }
    }

    if (nfa_dm_cb.disc_cb.excl_disc_entry.p_disc_cback)
      (*(nfa_dm_cb.disc_cb.excl_disc_entry.p_disc_cback))(
          NFA_DM_RF_DISC_ACTIVATED_EVT, p_data);

    return (NFA_STATUS_OK);
  }

  /* if this is NFCEE direct RF interface, notify activation to whoever
   * listening UICC */
  if (p_data->activate.intf_param.type == NFC_INTERFACE_EE_DIRECT_RF) {
    for (xx = 0; xx < NFA_DM_DISC_NUM_ENTRIES; xx++) {
      if ((nfa_dm_cb.disc_cb.entry[xx].in_use) &&
          (nfa_dm_cb.disc_cb.entry[xx].host_id != NFA_DM_DISC_HOST_ID_DH)) {
        nfa_dm_cb.disc_cb.activated_rf_disc_id = p_data->activate.rf_disc_id;
        nfa_dm_cb.disc_cb.activated_rf_interface =
            p_data->activate.intf_param.type;
        nfa_dm_cb.disc_cb.activated_protocol = NFC_PROTOCOL_UNKNOWN;
        nfa_dm_cb.disc_cb.activated_handle = xx;

        DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("activated_rf_interface:0x%x, activated_handle: 0x%x",
                         nfa_dm_cb.disc_cb.activated_rf_interface,
                         nfa_dm_cb.disc_cb.activated_handle);

        if (nfa_dm_cb.disc_cb.entry[xx].p_disc_cback)
          (*(nfa_dm_cb.disc_cb.entry[xx].p_disc_cback))(
              NFA_DM_RF_DISC_ACTIVATED_EVT, p_data);

        return (NFA_STATUS_OK);
      }
    }
    return (NFA_STATUS_FAILED);
  }

#if (NFC_NXP_NOT_OPEN_INCLUDED == TRUE)
  /*
   * if this is Proprietary RF interface, notify activation as START_READER_EVT.
   *
   * Code to handle the Reader over SWP.
   * 1. Pass this info to JNI as START_READER_EVT.
   * return (NFA_STATUS_OK)
   */
  if (p_data->activate.intf_param.type == NCI_INTERFACE_UICC_DIRECT ||
      p_data->activate.intf_param.type == NCI_INTERFACE_ESE_DIRECT) {
    for (xx = 0; xx < NFA_DM_DISC_NUM_ENTRIES; xx++) {
      if ((nfa_dm_cb.disc_cb.entry[xx].in_use)
#if (NFC_NXP_NOT_OPEN_INCLUDED != TRUE)
          && (nfa_dm_cb.disc_cb.entry[xx].host_id != NFA_DM_DISC_HOST_ID_DH)
#endif
              ) {
        nfa_dm_cb.disc_cb.activated_rf_interface =
            p_data->activate.intf_param.type;
        nfa_dm_cb.disc_cb.activated_handle = xx;

        DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf(
            "activated_rf_uicc-ese_interface:0x%x, activated_handle: 0x%x",
            nfa_dm_cb.disc_cb.activated_rf_interface,
            nfa_dm_cb.disc_cb.activated_handle);

        if (nfa_dm_cb.disc_cb.entry[xx].p_disc_cback)
          (*(nfa_dm_cb.disc_cb.entry[xx].p_disc_cback))(
              NFA_DM_RF_DISC_ACTIVATED_EVT, p_data);

        return (NFA_STATUS_OK);
      }
    }
    return (NFA_STATUS_FAILED);
  }
#endif

  /* get bit mask of technolgies/mode and protocol */
  activated_disc_mask = nfa_dm_disc_get_disc_mask(tech_n_mode, protocol);

  /* get host ID of technology from listen mode routing table */
  if (tech_n_mode == NFC_DISCOVERY_TYPE_LISTEN_A) {
    host_id_in_LRT = nfa_dm_cb.disc_cb.listen_RT[NFA_DM_DISC_LRT_NFC_A];
  } else if (tech_n_mode == NFC_DISCOVERY_TYPE_LISTEN_B) {
    host_id_in_LRT = nfa_dm_cb.disc_cb.listen_RT[NFA_DM_DISC_LRT_NFC_B];
  } else if (tech_n_mode == NFC_DISCOVERY_TYPE_LISTEN_F) {
    host_id_in_LRT = nfa_dm_cb.disc_cb.listen_RT[NFA_DM_DISC_LRT_NFC_F];
  } else if (tech_n_mode == NFC_DISCOVERY_TYPE_LISTEN_B_PRIME) {
    host_id_in_LRT = nfa_dm_cb.disc_cb.listen_RT[NFA_DM_DISC_LRT_NFC_BP];
  } else /* DH only */
  {
    host_id_in_LRT = NFA_DM_DISC_HOST_ID_DH;
  }

  if (protocol == NFC_PROTOCOL_NFC_DEP) {
    /* Force NFC-DEP to the host */
    host_id_in_LRT = NFA_DM_DISC_HOST_ID_DH;
  }

  for (xx = 0; xx < NFA_DM_DISC_NUM_ENTRIES; xx++) {
    /* if any matching NFC technology and protocol */
    if (nfa_dm_cb.disc_cb.entry[xx].in_use) {
      if (nfa_dm_cb.disc_cb.entry[xx].host_id == host_id_in_LRT) {
        if (nfa_dm_cb.disc_cb.entry[xx].selected_disc_mask &
            activated_disc_mask) {
          break;
        }
      } else {
        /* check ISO-DEP listening even if host in LRT is not matched */
        if (protocol == NFC_PROTOCOL_ISO_DEP) {
          if ((tech_n_mode == NFC_DISCOVERY_TYPE_LISTEN_A) &&
              (nfa_dm_cb.disc_cb.entry[xx].selected_disc_mask &
               NFA_DM_DISC_MASK_LA_ISO_DEP)) {
            iso_dep_t3t__listen = xx;
          } else if ((tech_n_mode == NFC_DISCOVERY_TYPE_LISTEN_B) &&
                     (nfa_dm_cb.disc_cb.entry[xx].selected_disc_mask &
                      NFA_DM_DISC_MASK_LB_ISO_DEP)) {
            iso_dep_t3t__listen = xx;
          }
        }
        /* check T3T listening even if host in LRT is not matched */
        else if (protocol == NFC_PROTOCOL_T3T) {
          if ((tech_n_mode == NFC_DISCOVERY_TYPE_LISTEN_F) &&
              (nfa_dm_cb.disc_cb.entry[xx].selected_disc_mask &
               NFA_DM_DISC_MASK_LF_T3T)) {
            iso_dep_t3t__listen = xx;
          }
        }
      }
    }
  }

  if (xx >= NFA_DM_DISC_NUM_ENTRIES) {
    /* if any ISO-DEP or T3T listening even if host in LRT is not matched */
    xx = iso_dep_t3t__listen;
  }
  if (protocol == NFC_PROTOCOL_NFC_DEP &&
      (tech_n_mode == NFC_DISCOVERY_TYPE_LISTEN_F_ACTIVE ||
       tech_n_mode == NFC_DISCOVERY_TYPE_LISTEN_A_ACTIVE ||
       tech_n_mode == NFC_DISCOVERY_TYPE_LISTEN_A)) {
    if (appl_dta_mode_flag == 1 && tech_n_mode == NFC_DISCOVERY_TYPE_LISTEN_A) {
      DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("DTA Mode Enabled : NFC-A Passive Listen Mode");
    }
    else {
      extern tNFA_P2P_CB nfa_p2p_cb;
      xx = nfa_p2p_cb.dm_disc_handle;
    }
  }

  if (xx < NFA_DM_DISC_NUM_ENTRIES) {
    nfa_dm_cb.disc_cb.activated_tech_mode = tech_n_mode;
    nfa_dm_cb.disc_cb.activated_rf_disc_id = p_data->activate.rf_disc_id;
    nfa_dm_cb.disc_cb.activated_rf_interface = p_data->activate.intf_param.type;
    nfa_dm_cb.disc_cb.activated_protocol = protocol;
    nfa_dm_cb.disc_cb.activated_handle = xx;

    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("activated_protocol:0x%x, activated_handle: 0x%x",
                        nfa_dm_cb.disc_cb.activated_protocol,
                        nfa_dm_cb.disc_cb.activated_handle);

    if (protocol == NFC_PROTOCOL_KOVIO) {
      /* check whether it's new or repeated activation */
      if (nfa_dm_disc_handle_kovio_activation(
              p_data, nfa_dm_cb.disc_cb.entry[xx].p_disc_cback)) {
        /* do not notify activation of Kovio to upper layer */
        return (NFA_STATUS_OK);
      }
    }

    if (nfa_dm_cb.disc_cb.entry[xx].p_disc_cback)
      (*(nfa_dm_cb.disc_cb.entry[xx].p_disc_cback))(
          NFA_DM_RF_DISC_ACTIVATED_EVT, p_data);

    return (NFA_STATUS_OK);
  } else {
    nfa_dm_cb.disc_cb.activated_protocol = NFA_PROTOCOL_INVALID;
    nfa_dm_cb.disc_cb.activated_handle = NFA_HANDLE_INVALID;
    return (NFA_STATUS_FAILED);
  }
#endif
  return NFA_STATUS_FAILED;
}

/*******************************************************************************
**
** Function         nfa_dm_disc_notify_deactivation
**
** Description      Send deactivation notification to sub-module
**
** Returns          None
**
*******************************************************************************/
static void nfa_dm_disc_notify_deactivation(tNFA_DM_RF_DISC_SM_EVENT sm_event,
                                            tNFC_DISCOVER *p_data) {
  sm_event = 0;
  p_data = NULL;
#if 0
  tNFA_HANDLE xx;
  tNFA_CONN_EVT_DATA evt_data;
  tNFC_DISCOVER disc_data;

#if (NFC_NXP_NOT_OPEN_INCLUDED == TRUE)
  static tNFA_HANDLE zz = NFA_HANDLE_INVALID;/* handle for deactivatation */
#endif

  DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("nfa_dm_disc_notify_deactivation (): activated_handle=%d",
                   nfa_dm_cb.disc_cb.activated_handle);

  if (nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_CHECKING) {
    DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("nfa_dm_disc_notify_deactivation (): for sleep wakeup");
    return;
  }

#if (NFC_NXP_NOT_OPEN_INCLUDED == TRUE)
  if(nfa_dm_cb.disc_cb.disc_state > NFA_DM_RFST_POLL_ACTIVE)
  {
    if(p_data->deactivate.type == NFA_DEACTIVATE_TYPE_SLEEP)
    {
      DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("nfa_dm_disc_notify_deactivation (): STORE handle for sleep");
      zz = nfa_dm_cb.disc_cb.activated_handle;
    }
    else if(zz == nfa_dm_cb.disc_cb.activated_handle || 
      nfa_dm_cb.disc_cb.activated_handle == NFA_HANDLE_INVALID)
    {
      zz = NFA_HANDLE_INVALID;
      DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("nfa_dm_disc_notify_deactivation (): CLEAR handle");
    }
  
    if(zz != NFA_HANDLE_INVALID && 
       zz != nfa_dm_cb.disc_cb.activated_handle && 
       p_data->deactivate.type != NFA_DEACTIVATE_TYPE_SLEEP)
    {
      if ((zz < NFA_DM_DISC_NUM_ENTRIES) &&
          (nfa_dm_cb.disc_cb.entry[zz].in_use)) {
        if (nfa_dm_cb.disc_cb.entry[zz].p_disc_cback)
        {
          DLOG_IF(INFO, nfc_debug_enabled)
        << StringPrintf("nfa_dm_disc_notify_deactivation (): extra DEACTIVATE CB");
          (*(nfa_dm_cb.disc_cb.entry[zz].p_disc_cback))(
              NFA_DM_RF_DISC_DEACTIVATED_EVT, p_data);
        }
      }
      zz = NFA_HANDLE_INVALID;
    }
  }
#endif

  if (sm_event == NFA_DM_RF_DEACTIVATE_RSP) {
    /*
    ** Activation has been aborted by upper layer in
    ** NFA_DM_RFST_W4_ALL_DISCOVERIES or NFA_DM_RFST_W4_HOST_SELECT
    ** Deactivation by upper layer or RF link loss in
    ** NFA_DM_RFST_LISTEN_SLEEP
    ** No sub-module is activated at this state.
    */

    if (nfa_dm_cb.disc_cb.disc_state == NFA_DM_RFST_LISTEN_SLEEP) {
      if (nfa_dm_cb.disc_cb.excl_disc_entry.in_use) {
        if (nfa_dm_cb.disc_cb.excl_disc_entry.p_disc_cback) {
          disc_data.deactivate.type = NFA_DEACTIVATE_TYPE_IDLE;
          (*(nfa_dm_cb.disc_cb.excl_disc_entry.p_disc_cback))(
              NFA_DM_RF_DISC_DEACTIVATED_EVT, &disc_data);
        }
      } else {
        /* let each sub-module handle deactivation */
        for (xx = 0; xx < NFA_DM_DISC_NUM_ENTRIES; xx++) {
          if ((nfa_dm_cb.disc_cb.entry[xx].in_use) &&
              (nfa_dm_cb.disc_cb.entry[xx].selected_disc_mask &
               NFA_DM_DISC_MASK_LISTEN)) {
            disc_data.deactivate.type = NFA_DEACTIVATE_TYPE_IDLE;
            (*(nfa_dm_cb.disc_cb.entry[xx].p_disc_cback))(
                NFA_DM_RF_DISC_DEACTIVATED_EVT, &disc_data);
          }
        }
      }
    } else if ((!(nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_STOPPING)) ||
               (nfa_dm_cb.disc_cb.deact_notify_pending)) {
      xx = nfa_dm_cb.disc_cb.activated_handle;

      /* notify event to activated module if failed while reactivation */
      if (nfa_dm_cb.disc_cb.excl_disc_entry.in_use) {
        if (nfa_dm_cb.disc_cb.excl_disc_entry.p_disc_cback) {
          disc_data.deactivate.type = NFA_DEACTIVATE_TYPE_IDLE;
          (*(nfa_dm_cb.disc_cb.excl_disc_entry.p_disc_cback))(
              NFA_DM_RF_DISC_DEACTIVATED_EVT, p_data);
        }
      } else if ((xx < NFA_DM_DISC_NUM_ENTRIES) &&
                 (nfa_dm_cb.disc_cb.entry[xx].in_use) &&
                 (nfa_dm_cb.disc_cb.entry[xx].p_disc_cback)) {
        (*(nfa_dm_cb.disc_cb.entry[xx].p_disc_cback))(
            NFA_DM_RF_DISC_DEACTIVATED_EVT, p_data);
      } else {
        /* notify deactivation to application if there is no activated module */
        evt_data.deactivated.type = NFA_DEACTIVATE_TYPE_IDLE;
        nfa_dm_conn_cback_event_notify(NFA_DEACTIVATED_EVT, &evt_data);
      }
    }
  } else {
    if (nfa_dm_cb.disc_cb.activated_protocol == NFC_PROTOCOL_KOVIO) {
      if (nfa_dm_cb.disc_cb.kovio_tle.in_use) {
        /* restart timer and do not notify upper layer */
        nfa_sys_start_timer(&nfa_dm_cb.disc_cb.kovio_tle, 0,
                            NFA_DM_DISC_TIMEOUT_KOVIO_PRESENCE_CHECK);
        return;
      }
      /* Otherwise, upper layer initiated deactivation. */
    }

    /* notify event to activated module */
    if (nfa_dm_cb.disc_cb.excl_disc_entry.in_use) {
      if (nfa_dm_cb.disc_cb.excl_disc_entry.p_disc_cback) {
        disc_data.deactivate.type = NFA_DEACTIVATE_TYPE_IDLE;
        (*(nfa_dm_cb.disc_cb.excl_disc_entry.p_disc_cback))(
            NFA_DM_RF_DISC_DEACTIVATED_EVT, p_data);
      }
    } else {
      xx = nfa_dm_cb.disc_cb.activated_handle;

      if ((xx < NFA_DM_DISC_NUM_ENTRIES) &&
          (nfa_dm_cb.disc_cb.entry[xx].in_use)) {
        if (nfa_dm_cb.disc_cb.entry[xx].p_disc_cback)
          (*(nfa_dm_cb.disc_cb.entry[xx].p_disc_cback))(
              NFA_DM_RF_DISC_DEACTIVATED_EVT, p_data);
      }
    }
  }

  /* clear activated information */
  nfa_dm_cb.disc_cb.activated_tech_mode = 0;
  nfa_dm_cb.disc_cb.activated_rf_disc_id = 0;
  nfa_dm_cb.disc_cb.activated_rf_interface = 0;
  nfa_dm_cb.disc_cb.activated_protocol = NFA_PROTOCOL_INVALID;
  nfa_dm_cb.disc_cb.activated_handle = NFA_HANDLE_INVALID;
  nfa_dm_cb.disc_cb.deact_notify_pending = false;
#endif
}

/*******************************************************************************
**
** Function         nfa_dm_is_raw_frame_session
**
** Description      If NFA_SendRawFrame is called since RF activation,
**                  this function returns TRUE.
**
** Returns          TRUE if NFA_SendRawFrame is called
**
*******************************************************************************/
bool nfa_dm_is_raw_frame_session(void) {
  return ((nfa_dm_cb.flags & NFA_DM_FLAGS_RAW_FRAME) ? true : false);
}

/*******************************************************************************
**
** Function         nfa_dm_disc_new_state
**
** Description      Processing discovery events in NFA_DM_RFST_IDLE state
**
** Returns          void
**
*******************************************************************************/
void nfa_dm_disc_new_state(tNFA_DM_RF_DISC_STATE new_state) {
  tNFA_CONN_EVT_DATA evt_data;
  tNFA_DM_RF_DISC_STATE old_state = nfa_dm_cb.disc_cb.disc_state;

  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
      "old_state: %s (%d), new_state: %s (%d) "
      "disc_flags: 0x%x",
      nfa_dm_disc_state_2_str(nfa_dm_cb.disc_cb.disc_state).c_str(),
      nfa_dm_cb.disc_cb.disc_state, nfa_dm_disc_state_2_str(new_state).c_str(),
      new_state, nfa_dm_cb.disc_cb.disc_flags);

  nfa_dm_cb.disc_cb.disc_state = new_state;

  /* not error recovering */
  if ((new_state == NFA_DM_RFST_IDLE) &&
      (!(nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_W4_RSP))) {
    if (nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_STOPPING) {
      nfa_dm_cb.disc_cb.disc_flags &= ~NFA_DM_DISC_FLAGS_STOPPING;

      /* if exclusive RF control is stopping */
      if (nfa_dm_cb.flags & NFA_DM_FLAGS_EXCL_RF_ACTIVE) {
        if (old_state > NFA_DM_RFST_DISCOVERY) {
          /* notify deactivation to application */
          evt_data.deactivated.type = NFA_DEACTIVATE_TYPE_IDLE;
          nfa_dm_conn_cback_event_notify(NFA_DEACTIVATED_EVT, &evt_data);
        }

        // nfa_dm_rel_excl_rf_control_and_notify();
      } else {
        evt_data.status = NFA_STATUS_OK;
        nfa_dm_conn_cback_event_notify(NFA_RF_DISCOVERY_STOPPED_EVT, &evt_data);
      }
    }
    if (nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_DISABLING) {
      nfa_dm_cb.disc_cb.disc_flags &= ~NFA_DM_DISC_FLAGS_DISABLING;
      nfa_sys_check_disabled();
    }
  }
}

/*******************************************************************************
**
** Function         nfa_dm_disc_sm_idle
**
** Description      Processing discovery events in NFA_DM_RFST_IDLE state
**
** Returns          void
**
*******************************************************************************/
static void nfa_dm_disc_sm_idle(tNFA_DM_RF_DISC_SM_EVENT event,
                                tNFA_DM_RF_DISC_DATA *p_data) {
  uint8_t xx;

  switch (event) {
  case NFA_DM_RF_DISCOVER_CMD:
    // nfa_dm_start_rf_discover();
    break;

  case NFA_DM_RF_DISCOVER_RSP:
    nfa_dm_cb.disc_cb.disc_flags &= ~NFA_DM_DISC_FLAGS_W4_RSP;

    if (p_data->nfc_discover.status == NFC_STATUS_OK) {
      nfa_dm_disc_new_state(NFA_DM_RFST_DISCOVERY);

      /* if RF discovery was stopped while waiting for response */
      if (nfa_dm_cb.disc_cb.disc_flags &
          (NFA_DM_DISC_FLAGS_STOPPING | NFA_DM_DISC_FLAGS_DISABLING)) {
        /* stop discovery */
        nfa_dm_cb.disc_cb.disc_flags |= NFA_DM_DISC_FLAGS_W4_RSP;
        NFC_Deactivate(NFA_DEACTIVATE_TYPE_IDLE);
        break;
      }

      if (nfa_dm_cb.disc_cb.excl_disc_entry.in_use) {
        if (nfa_dm_cb.disc_cb.excl_disc_entry.disc_flags &
            NFA_DM_DISC_FLAGS_NOTIFY) {
          nfa_dm_cb.disc_cb.excl_disc_entry.disc_flags &=
              ~NFA_DM_DISC_FLAGS_NOTIFY;

          if (nfa_dm_cb.disc_cb.excl_disc_entry.p_disc_cback)
            (*(nfa_dm_cb.disc_cb.excl_disc_entry.p_disc_cback))(
                NFA_DM_RF_DISC_START_EVT, &p_data->nfc_discover);
        }
      } else {
        /* notify event to each module which is waiting for start */
        for (xx = 0; xx < NFA_DM_DISC_NUM_ENTRIES; xx++) {
          /* if registered module is waiting for starting discovery */
          if ((nfa_dm_cb.disc_cb.entry[xx].in_use) &&
              (nfa_dm_cb.disc_cb.dm_disc_mask &
               nfa_dm_cb.disc_cb.entry[xx].selected_disc_mask) &&
              (nfa_dm_cb.disc_cb.entry[xx].disc_flags &
               NFA_DM_DISC_FLAGS_NOTIFY)) {
            nfa_dm_cb.disc_cb.entry[xx].disc_flags &= ~NFA_DM_DISC_FLAGS_NOTIFY;

            if (nfa_dm_cb.disc_cb.entry[xx].p_disc_cback)
              (*(nfa_dm_cb.disc_cb.entry[xx].p_disc_cback))(
                  NFA_DM_RF_DISC_START_EVT, &p_data->nfc_discover);
          }
        }
      }
      // nfa_dm_disc_notify_started(p_data->nfc_discover.status);
    } else {
      /* in rare case that the discovery states of NFCC and DH mismatch and
       * NFCC rejects Discover Cmd
       * deactivate idle and then start disvocery when got deactivate rsp */
      nfa_dm_cb.disc_cb.disc_flags |= NFA_DM_DISC_FLAGS_W4_RSP;
      NFC_Deactivate(NFA_DEACTIVATE_TYPE_IDLE);
    }
    break;

  case NFA_DM_RF_DEACTIVATE_RSP:
    nfa_dm_cb.disc_cb.disc_flags &= ~NFA_DM_DISC_FLAGS_W4_RSP;

    /* if NFCC goes to idle successfully */
    if (p_data->nfc_discover.status == NFC_STATUS_OK) {
      /* if DH forced to go idle while waiting for deactivation NTF */
      if (!(nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_W4_NTF)) {
        nfa_dm_disc_notify_deactivation(NFA_DM_RF_DEACTIVATE_NTF,
                                        &(p_data->nfc_discover));

        /* check any pending flags like NFA_DM_DISC_FLAGS_STOPPING or
         * NFA_DM_DISC_FLAGS_DISABLING */
        nfa_dm_disc_new_state(NFA_DM_RFST_IDLE);
        /* check if need to restart discovery after resync discovery state
         * with NFCC */
        // nfa_dm_start_rf_discover();
      }
      /* Otherwise, deactivating when getting unexpected activation */
    }
    /* Otherwise, wait for deactivation NTF */
    break;

  case NFA_DM_RF_DEACTIVATE_NTF:
    /* if NFCC sent this after NFCC had rejected deactivate CMD to idle while
     * deactivating */
    if (!(nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_W4_NTF)) {
      if (p_data->nfc_discover.deactivate.type ==
          NFC_DEACTIVATE_TYPE_DISCOVERY) {
        /* stop discovery */
        nfa_dm_cb.disc_cb.disc_flags |= NFA_DM_DISC_FLAGS_W4_RSP;
        NFC_Deactivate(NFA_DEACTIVATE_TYPE_IDLE);
      } else {
        nfa_dm_disc_notify_deactivation(NFA_DM_RF_DEACTIVATE_NTF,
                                        &(p_data->nfc_discover));
        /* check any pending flags like NFA_DM_DISC_FLAGS_STOPPING or
         * NFA_DM_DISC_FLAGS_DISABLING */
        nfa_dm_disc_new_state(NFA_DM_RFST_IDLE);
        /* check if need to restart discovery after resync discovery state
         * with NFCC */
        // nfa_dm_start_rf_discover();
      }
    }
    /* Otherwise, deactivated when received unexpected activation in idle
     * state */
    nfa_dm_cb.disc_cb.disc_flags &= ~NFA_DM_DISC_FLAGS_W4_NTF;
    break;

  case NFA_DM_RF_INTF_ACTIVATED_NTF:
    /* unexpected activation, deactivate to idle */
    nfa_dm_cb.disc_cb.disc_flags |=
        (NFA_DM_DISC_FLAGS_W4_RSP | NFA_DM_DISC_FLAGS_W4_NTF);
    NFC_Deactivate(NFA_DEACTIVATE_TYPE_IDLE);
    break;

  default:
    LOG(ERROR) << StringPrintf("Unexpected discovery event");
    break;
  }
}

/*******************************************************************************
**
** Function         nfa_dm_disc_sm_discovery
**
** Description      Processing discovery events in NFA_DM_RFST_DISCOVERY state
**
** Returns          void
**
*******************************************************************************/
static void nfa_dm_disc_sm_discovery(tNFA_DM_RF_DISC_SM_EVENT event,
                                     tNFA_DM_RF_DISC_DATA *p_data) {
  switch (event) {

  case NFA_DM_RF_DEACTIVATE_RSP:
    nfa_dm_cb.disc_cb.disc_flags &= ~NFA_DM_DISC_FLAGS_W4_RSP;

    /* if it's not race condition between deactivate CMD and activate NTF */
    if (!(nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_W4_NTF)) {
      /* do not notify deactivated to idle in RF discovery state
      ** because it is internal or stopping RF discovery
      */

      /* there was no activation while waiting for deactivation RSP */
      nfa_dm_disc_new_state(NFA_DM_RFST_IDLE);
      // nfa_dm_start_rf_discover();
    }
    break;
  case NFA_DM_RF_DISCOVER_NTF:
    nfa_dm_disc_new_state(NFA_DM_RFST_W4_ALL_DISCOVERIES);
    // nfa_dm_notify_discovery(p_data);
    break;
  case NFA_DM_RF_INTF_ACTIVATED_NTF:
    if (nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_W4_RSP) {
      DLOG_IF(INFO, nfc_debug_enabled)
          << StringPrintf("RF Activated while waiting for deactivation RSP");
      /* it's race condition. DH has to wait for deactivation NTF */
      nfa_dm_cb.disc_cb.disc_flags |= NFA_DM_DISC_FLAGS_W4_NTF;
    } else {
      if (p_data->nfc_discover.activate.intf_param.type ==
          NFC_INTERFACE_EE_DIRECT_RF) {
        nfa_dm_disc_new_state(NFA_DM_RFST_LISTEN_ACTIVE);
      } else if (p_data->nfc_discover.activate.rf_tech_param.mode & 0x80) {
        /* Listen mode */
        nfa_dm_disc_new_state(NFA_DM_RFST_LISTEN_ACTIVE);
      } else {
        /* Poll mode */
        nfa_dm_disc_new_state(NFA_DM_RFST_POLL_ACTIVE);
      }

      if (nfa_dm_disc_notify_activation(&(p_data->nfc_discover)) ==
          NFA_STATUS_FAILED) {
        DLOG_IF(INFO, nfc_debug_enabled)
            << StringPrintf("Not matched, restart discovery after receiving "
                            "deactivate ntf");

        /* after receiving deactivate event, restart discovery */
        nfa_dm_cb.disc_cb.disc_flags |=
            (NFA_DM_DISC_FLAGS_W4_RSP | NFA_DM_DISC_FLAGS_W4_NTF);
        NFC_Deactivate(NFA_DEACTIVATE_TYPE_IDLE);
      }
    }
    break;

  case NFA_DM_RF_DEACTIVATE_NTF:
    /* if there was race condition between deactivate CMD and activate NTF */
    if (nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_W4_NTF) {
      /* race condition is resolved */
      nfa_dm_cb.disc_cb.disc_flags &= ~NFA_DM_DISC_FLAGS_W4_NTF;

      if (!(nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_W4_RSP)) {
        /* do not notify deactivated to idle in RF discovery state
        ** because it is internal or stopping RF discovery
        */

        nfa_dm_disc_new_state(NFA_DM_RFST_IDLE);
        // nfa_dm_start_rf_discover();
      }
    }
    break;
  case NFA_DM_CORE_INTF_ERROR_NTF:
    break;
  default:
    LOG(ERROR) << StringPrintf("Unexpected discovery event");
    break;
  }
}

/*******************************************************************************
**
** Function         nfa_dm_disc_sm_listen_active
**
** Description      Processing discovery events in NFA_DM_RFST_LISTEN_ACTIVE
**                  state
**
** Returns          void
**
*******************************************************************************/
static void nfa_dm_disc_sm_listen_active(tNFA_DM_RF_DISC_SM_EVENT event,
                                         tNFA_DM_RF_DISC_DATA *p_data) {
  tNFC_DEACTIVATE_DEVT deact = tNFC_DEACTIVATE_DEVT();

  switch (event) {
  case NFA_DM_RF_DEACTIVATE_RSP:
    nfa_dm_cb.disc_cb.disc_flags &= ~NFA_DM_DISC_FLAGS_W4_RSP;
    if (!(nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_W4_NTF)) {
      /* it's race condition. received deactivate NTF before receiving RSP */

      deact.status = NFC_STATUS_OK;
      deact.type = NFC_DEACTIVATE_TYPE_IDLE;
      deact.is_ntf = true;
      tNFC_DISCOVER nfc_discover;
      nfc_discover.deactivate = deact;
      nfa_dm_disc_notify_deactivation(NFA_DM_RF_DEACTIVATE_NTF, &nfc_discover);

      /* NFCC is in IDLE state */
      nfa_dm_disc_new_state(NFA_DM_RFST_IDLE);
      // nfa_dm_start_rf_discover();
    }
    break;
  case NFA_DM_RF_DEACTIVATE_NTF:
    nfa_dm_cb.disc_cb.disc_flags &= ~NFA_DM_DISC_FLAGS_W4_NTF;

    nfa_sys_stop_timer(&nfa_dm_cb.disc_cb.tle);

    if (nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_W4_RSP) {
      /* it's race condition. received deactivate NTF before receiving RSP */
      /* notify deactivation after receiving deactivate RSP */
      DLOG_IF(INFO, nfc_debug_enabled)
          << StringPrintf("Rx deactivate NTF while waiting for deactivate RSP");
    } else {
      nfa_dm_disc_notify_deactivation(NFA_DM_RF_DEACTIVATE_NTF,
                                      &(p_data->nfc_discover));

      if (p_data->nfc_discover.deactivate.type == NFC_DEACTIVATE_TYPE_IDLE) {
        nfa_dm_disc_new_state(NFA_DM_RFST_IDLE);
        // nfa_dm_start_rf_discover();
      } else if ((p_data->nfc_discover.deactivate.type ==
                  NFC_DEACTIVATE_TYPE_SLEEP) ||
                 (p_data->nfc_discover.deactivate.type ==
                  NFC_DEACTIVATE_TYPE_SLEEP_AF)) {
        nfa_dm_disc_new_state(NFA_DM_RFST_LISTEN_SLEEP);
      } else if (p_data->nfc_discover.deactivate.type ==
                 NFC_DEACTIVATE_TYPE_DISCOVERY) {
        /* Discovery */
        nfa_dm_disc_new_state(NFA_DM_RFST_DISCOVERY);
        if (nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_STOPPING) {
          /* stop discovery */
          NFC_Deactivate(NFA_DEACTIVATE_TYPE_IDLE);
        }
      }
    }
    break;

  case NFA_DM_CORE_INTF_ERROR_NTF:
    break;
  default:
    LOG(ERROR) << StringPrintf("Unexpected discovery event");
    break;
  }
}

/*******************************************************************************
**
** Function         nfa_dm_disc_sm_execute
**
** Description      Processing discovery related events
**
** Returns          void
**
*******************************************************************************/
void nfa_dm_disc_sm_execute(tNFA_DM_RF_DISC_SM_EVENT event,
                            tNFA_DM_RF_DISC_DATA *p_data) {
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
      "state: %s (%d), event: %s(%d) disc_flags: "
      "0x%x",
      nfa_dm_disc_state_2_str(nfa_dm_cb.disc_cb.disc_state).c_str(),
      nfa_dm_cb.disc_cb.disc_state, nfa_dm_disc_event_2_str(event).c_str(),
      event, nfa_dm_cb.disc_cb.disc_flags);

  switch (nfa_dm_cb.disc_cb.disc_state) {
  /*  RF Discovery State - Idle */
  case NFA_DM_RFST_IDLE:
    nfa_dm_disc_sm_idle(event, p_data);
    break;

  /* RF Discovery State - Discovery */
  case NFA_DM_RFST_DISCOVERY:
    nfa_dm_disc_sm_discovery(event, p_data);
    break;

  /* RF Discovery State - listen mode activated */
  case NFA_DM_RFST_LISTEN_ACTIVE:
    nfa_dm_disc_sm_listen_active(event, p_data);
    break;
  }
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
      "new state: %s (%d), disc_flags: 0x%x",
      nfa_dm_disc_state_2_str(nfa_dm_cb.disc_cb.disc_state).c_str(),
      nfa_dm_cb.disc_cb.disc_state, nfa_dm_cb.disc_cb.disc_flags);
}

/*******************************************************************************
**
** Function         nfa_dm_disc_state_2_str
**
** Description      convert nfc discovery state to string
**
*******************************************************************************/
static std::string nfa_dm_disc_state_2_str(uint8_t state) {
  switch (state) {
  case NFA_DM_RFST_IDLE:
    return "IDLE";

  case NFA_DM_RFST_DISCOVERY:
    return "DISCOVERY";

  case NFA_DM_RFST_W4_ALL_DISCOVERIES:
    return "W4_ALL_DISCOVERIES";

  case NFA_DM_RFST_W4_HOST_SELECT:
    return "W4_HOST_SELECT";

  case NFA_DM_RFST_POLL_ACTIVE:
    return "POLL_ACTIVE";

  case NFA_DM_RFST_LISTEN_ACTIVE:
    return "LISTEN_ACTIVE";

  case NFA_DM_RFST_LISTEN_SLEEP:
    return "LISTEN_SLEEP";

  case NFA_DM_RFST_LP_LISTEN:
    return "LP_LISTEN";

  case NFA_DM_RFST_LP_ACTIVE:
    return "LP_ACTIVE";
  }
  return "Unknown";
}

/*******************************************************************************
**
** Function         nfa_dm_disc_event_2_str
**
** Description      convert nfc discovery RSP/NTF to string
**
*******************************************************************************/
static std::string nfa_dm_disc_event_2_str(uint8_t event) {
  switch (event) {
  case NFA_DM_RF_DISCOVER_CMD:
    return "DISCOVER_CMD";
  case NFA_DM_RF_DISCOVER_RSP:
    return "DISCOVER_RSP";
  case NFA_DM_RF_DISCOVER_NTF:
    return "DISCOVER_NTF";
  case NFA_DM_RF_DISCOVER_SELECT_CMD:
    return "SELECT_CMD";
  case NFA_DM_RF_DISCOVER_SELECT_RSP:
    return "SELECT_RSP";
  case NFA_DM_RF_INTF_ACTIVATED_NTF:
    return "ACTIVATED_NTF";
  case NFA_DM_RF_DEACTIVATE_CMD:
    return "DEACTIVATE_CMD";
  case NFA_DM_RF_DEACTIVATE_RSP:
    return "DEACTIVATE_RSP";
  case NFA_DM_RF_DEACTIVATE_NTF:
    return "DEACTIVATE_NTF";
  case NFA_DM_LP_LISTEN_CMD:
    return "NFA_DM_LP_LISTEN_CMD";
  case NFA_DM_CORE_INTF_ERROR_NTF:
    return "INTF_ERROR_NTF";
  default:
    return "Unknown";
  }
}

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
 *  This is the main implementation file for the NFA device manager.
 *
 ******************************************************************************/

#include <string.h>

#include <android-base/stringprintf.h>
#include <base/logging.h>

#include "nfa_api.h"
#include "nfa_dm_int.h"
#if (NXP_EXTNS == TRUE)
#include "nfc_int.h"
#endif

using android::base::StringPrintf;

extern bool nfc_debug_enabled;

/*****************************************************************************
** Constants and types
*****************************************************************************/
static const tNFA_SYS_REG nfa_dm_sys_reg = {nfa_dm_sys_enable, nfa_dm_evt_hdlr,
                                            nfa_dm_sys_disable, NULL};

#if (NXP_EXTNS == TRUE)
tNFA_DM_CB nfa_dm_cb;
#else
tNFA_DM_CB nfa_dm_cb = {};
#endif

#define NFA_DM_NUM_ACTIONS (NFA_DM_MAX_EVT & 0x00ff)

/* type for action functions */
typedef bool (*tNFA_DM_ACTION)(tNFA_DM_MSG *p_data);

/* action function list */
const tNFA_DM_ACTION nfa_dm_action[] = {
    /* device manager local device API events */
    nfa_dm_enable,             /* NFA_DM_API_ENABLE_EVT                */
    nfa_dm_disable,            /* NFA_DM_API_DISABLE_EVT               */
    nfa_dm_act_send_raw_frame, /* NFA_DM_API_RAW_FRAME_EVT             */
    nfa_dm_act_reg_vsc,        /* NFA_DM_API_REG_VSC_EVT               */
    nfa_dm_act_send_vsc,       /* NFA_DM_API_SEND_VSC_EVT              */
    nfa_dm_act_send_raw_vs     /* NFA_DM_API_SEND_RAW_VS_EVT           */
};

/*****************************************************************************
** Local function prototypes
*****************************************************************************/
static std::string nfa_dm_evt_2_str(uint16_t event);
/*******************************************************************************
**
** Function         nfa_dm_init
**
** Description      Initialises the NFC device manager
**
** Returns          void
**
*******************************************************************************/
void nfa_dm_init(void) {
  LOG(ERROR) << StringPrintf(" nfa_dm_init ()");
  memset(&nfa_dm_cb, 0, sizeof(tNFA_DM_CB));
  nfa_dm_cb.nfcc_pwr_mode = NFA_DM_PWR_MODE_FULL;
  /* register message handler on NFA SYS */
  nfa_sys_register(NFA_ID_DM, &nfa_dm_sys_reg);
}

/*******************************************************************************
**
** Function         nfa_dm_evt_hdlr
**
** Description      Event handling function for DM
**
**
** Returns          void
**
*******************************************************************************/
bool nfa_dm_evt_hdlr(NFC_HDR *p_msg) {
  bool freebuf = true;
  uint16_t event = p_msg->event & 0x00ff;

  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf(
      "event: %s (0x%02x)", nfa_dm_evt_2_str(event).c_str(), event);

  /* execute action functions */
  if (event < NFA_DM_NUM_ACTIONS) {
    freebuf = (*nfa_dm_action[event])((tNFA_DM_MSG *)p_msg);
  }
  return freebuf;
}

/*******************************************************************************
**
** Function         nfa_dm_sys_disable
**
** Description      This function is called after all subsystems have been
**                  disabled.
**
** Returns          void
**
*******************************************************************************/
void nfa_dm_sys_disable(void) {
  /* Disable the DM sub-system */
  /* If discovery state is not IDLE or DEACTIVATED and graceful disable, */
  /* then we need to deactivate link or stop discovery                   */

  if (nfa_sys_is_graceful_disable()) {
    if ((nfa_dm_cb.disc_cb.disc_state == NFA_DM_RFST_IDLE) &&
        ((nfa_dm_cb.disc_cb.disc_flags & NFA_DM_DISC_FLAGS_DISABLING) == 0)) {
      /* discovery is not started */
      nfa_dm_disable_complete();
    } else {
      /* probably waiting to be disabled */
      LOG(WARNING) << StringPrintf("DM disc_state state = %d disc_flags:0x%x",
                                   nfa_dm_cb.disc_cb.disc_state,
                                   nfa_dm_cb.disc_cb.disc_flags);
    }

  } else {
    nfa_dm_disable_complete();
  }
}

/*******************************************************************************
**
** Function         nfa_dm_is_active
**
** Description      check if all modules of NFA is done with enable process and
**                  NFA is not restoring NFCC.
**
** Returns          true, if NFA_DM_ENABLE_EVT is reported and it is not
**                  restoring NFCC
**
*******************************************************************************/
bool nfa_dm_is_active(void) {
  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("nfa_dm_is_active () flags:0x%x", nfa_dm_cb.flags);
  if ((nfa_dm_cb.flags & NFA_DM_FLAGS_DM_IS_ACTIVE) &&
      ((nfa_dm_cb.flags &
        (NFA_DM_FLAGS_ENABLE_EVT_PEND | NFA_DM_FLAGS_NFCC_IS_RESTORING |
         NFA_DM_FLAGS_POWER_OFF_SLEEP)) == 0)) {
    return true;
  } else
    return false;
}

/*******************************************************************************
**
** Function         nfa_dm_nfc_revt_2_str
**
** Description      convert nfc revt to string
**
*******************************************************************************/
static std::string nfa_dm_evt_2_str(uint16_t event) {
  switch (NFA_SYS_EVT_START(NFA_ID_DM) | event) {
  case NFA_DM_API_ENABLE_EVT:
    return "NFA_DM_API_ENABLE_EVT";

  case NFA_DM_API_DISABLE_EVT:
    return "NFA_DM_API_DISABLE_EVT";

  case NFA_DM_API_RAW_FRAME_EVT:
    return "NFA_DM_API_RAW_FRAME_EVT";
  }

  return "Unknown or Vendor Specific";
}

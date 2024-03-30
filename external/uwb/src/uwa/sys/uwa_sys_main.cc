/******************************************************************************
 *
 *  Copyright (C) 1999-2012 Broadcom Corporation
 *  Copyright 2018-2019 NXP
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
 *  This is the main implementation file for the UWA system manager.
 *
 ******************************************************************************/
#include <string.h>

#include "uci_log.h"
#include "uwa_api.h"
#include "uwa_dm_int.h"
#include "uwa_sys.h"
#include "uwa_sys_int.h"
#include "uwb_osal_common.h"

/* protocol timer update period, in milliseconds */
#ifndef UWA_SYS_TIMER_PERIOD
#define UWA_SYS_TIMER_PERIOD 10
#endif

/* system manager control block definition */
tUWA_SYS_CB uwa_sys_cb =
    {}; /* uwa_sys control block. statically initialize 'flags' field to 0 */

/*******************************************************************************
**
** Function         uwa_sys_init
**
** Description      UWA initialization; called from task initialization.
**
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_init(void) {
  memset(&uwa_sys_cb, 0, sizeof(tUWA_SYS_CB));
  uwa_sys_cb.flags |= UWA_SYS_FL_INITIALIZED;
  uwa_sys_ptim_init(&uwa_sys_cb.ptim_cb, UWA_SYS_TIMER_PERIOD,
                    p_uwa_sys_cfg->timer);
}

/*******************************************************************************
**
** Function         uwa_sys_event
**
** Description      BTA event handler; called from task event handler.
**
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_event(UWB_HDR* p_msg) {
  uint8_t id;
  bool freebuf = true;

  UCI_TRACE_I("UWA got event 0x%04X", p_msg->event);

  /* get subsystem id from event */
  id = (uint8_t)(p_msg->event >> 8);

  /* verify id and call subsystem event handler */
  if ((id < UWA_ID_MAX) && (uwa_sys_cb.is_reg[id])) {
    freebuf = (*uwa_sys_cb.reg[id]->evt_hdlr)(p_msg);
  } else {
    UCI_TRACE_W("UWA got unregistered event id %d", id);
  }

  if (freebuf) {
    phUwb_GKI_freebuf(p_msg);
  }
}

/*******************************************************************************
**
** Function         uwa_sys_timer_update
**
** Description      Update the BTA timer list and handle expired timers.
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_timer_update(void) {
  if (!uwa_sys_cb.timers_disabled) {
    uwa_sys_ptim_timer_update(&uwa_sys_cb.ptim_cb);
  }
}

/*******************************************************************************
**
** Function         uwa_sys_register
**
** Description      Called by other BTA subsystems to register their event
**                  handler.
**
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_register(uint8_t id, const tUWA_SYS_REG* p_reg) {
  uwa_sys_cb.reg[id] = (tUWA_SYS_REG*)p_reg;
  uwa_sys_cb.is_reg[id] = true;

  if ((id != UWA_ID_DM) && (id != UWA_ID_SYS))
    uwa_sys_cb.enable_cplt_mask |= (uint16_t)(0x0001 << id);

  UCI_TRACE_I("id=%i, enable_cplt_mask=0x%x", id, uwa_sys_cb.enable_cplt_mask);
}

/*******************************************************************************
**
** Function         uwa_sys_check_disabled
**
** Description      If all subsystems above DM have been disabled, then
**                  disable DM. Called during UWA shutdown
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_check_disabled(void) {
  /* Disable DM */
  if (uwa_sys_cb.is_reg[UWA_ID_DM]) {
    (*uwa_sys_cb.reg[UWA_ID_DM]->disable)();
  }
}

/*******************************************************************************
**
** Function         uwa_sys_deregister
**
** Description      Called by other BTA subsystems to de-register
**                  handler.
**
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_deregister(uint8_t id) {
  UCI_TRACE_I("uwa_sys: deregistering subsystem %i", id);

  uwa_sys_cb.is_reg[id] = false;

  /* If not deregistering DM, then check if any other subsystems above DM are
   * still  */
  /* registered. */
  if (id != UWA_ID_DM) {
    /* If all subsystems above UWA_DM have been disabled, then okay to disable
     * DM */
    uwa_sys_check_disabled();
  } else {
    /* DM (the final sub-system) is deregistering. Clear pending timer events in
     * uwa_sys. */
    uwa_sys_ptim_init(&uwa_sys_cb.ptim_cb, UWA_SYS_TIMER_PERIOD,
                      p_uwa_sys_cfg->timer);
  }
}

/*******************************************************************************
**
** Function         uwa_sys_is_register
**
** Description      Called by other BTA subsystems to get registeration
**                  status.
**
**
** Returns          void
**
*******************************************************************************/
bool uwa_sys_is_register(uint8_t id) { return uwa_sys_cb.is_reg[id]; }

/*******************************************************************************
**
** Function         uwa_sys_is_graceful_disable
**
** Description      Called by other BTA subsystems to get disable
**                  parameter.
**
**
** Returns          void
**
*******************************************************************************/
bool uwa_sys_is_graceful_disable(void) { return uwa_sys_cb.graceful_disable; }

/*******************************************************************************
**
** Function         uwa_sys_enable_subsystems
**
** Description      Call on UWA Start up
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_enable_subsystems(void) {
  uint8_t id;

  UCI_TRACE_I("uwa_sys: enabling subsystems");

  /* Enable all subsystems except SYS */
  for (id = UWA_ID_DM; id < UWA_ID_MAX; id++) {
    if (uwa_sys_cb.is_reg[id]) {
      if (uwa_sys_cb.reg[id]->enable != NULL) {
        /* Subsytem has a Disable fuuciton. Call it now */
        (*uwa_sys_cb.reg[id]->enable)();
      } else {
        /* Subsytem does not have a Enable function. Report Enable on behalf of
         * subsystem */
        uwa_sys_cback_notify_enable_complete(id);
      }
    }
  }
}

/*******************************************************************************
**
** Function         uwa_sys_disable_subsystems
**
** Description      Call on UWA shutdown. Disable all subsystems above UWA_DM
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_disable_subsystems(bool graceful) {
  UCI_TRACE_I("uwa_sys: disabling subsystems:%d", graceful);
  uwa_sys_cb.graceful_disable = graceful;

  /* Disable DM */
  if (uwa_sys_cb.is_reg[UWA_ID_DM]) {
    (*uwa_sys_cb.reg[UWA_ID_DM]->disable)();
  }
}

/*******************************************************************************
**
** Function         uwa_sys_sendmsg
**
** Description      Send a GKI message to BTA.  This function is designed to
**                  optimize sending of messages to BTA.  It is called by BTA
**                  API functions and call-in functions.
**
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_sendmsg(void* p_msg) {
  phUwb_GKI_send_msg(UWB_TASK, p_uwa_sys_cfg->mbox, p_msg);
}

/*******************************************************************************
**
** Function         uwa_sys_start_timer
**
** Description      Start a protocol timer for the specified amount
**                  of time in milliseconds.
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_start_timer(TIMER_LIST_ENT* p_tle, uint16_t type,
                         uint32_t timeout) {
  uwa_sys_ptim_start_timer(&uwa_sys_cb.ptim_cb, p_tle, type, timeout);
}

/*******************************************************************************
**
** Function         uwa_sys_stop_timer
**
** Description      Stop a BTA timer.
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_stop_timer(TIMER_LIST_ENT* p_tle) {
  uwa_sys_ptim_stop_timer(&uwa_sys_cb.ptim_cb, p_tle);
}

/*******************************************************************************
**
** Function         uwa_sys_disable_timers
**
** Description      Disable sys timer event handling
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_disable_timers(void) { uwa_sys_cb.timers_disabled = true; }

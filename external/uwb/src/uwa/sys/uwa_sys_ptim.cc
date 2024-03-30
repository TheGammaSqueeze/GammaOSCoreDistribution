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
 *  Protocol timer services (taken from bta ptim)
 *
 ******************************************************************************/
#include "uwa_sys_ptim.h"

#include "uci_log.h"
#include "uwa_sys.h"
#include "uwa_sys_int.h"
#include "uwb_gki.h"
#include "uwb_osal_common.h"
#include "uwb_target.h"

/*******************************************************************************
**
** Function         uwa_sys_ptim_init
**
** Description      Initialize a protocol timer control block.  Parameter
**                  period is the GKI timer period in milliseconds.  Parameter
**                  timer_id is the GKI timer id.
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_ptim_init(tPTIM_CB* p_cb, uint16_t period, uint8_t timer_id) {
  phUwb_GKI_init_timer_list(&p_cb->timer_queue);
  p_cb->period = period;
  p_cb->timer_id = timer_id;
}

/*******************************************************************************
**
** Function         uwa_sys_ptim_timer_update
**
** Description      Update the protocol timer list and handle expired timers.
**                  This function is called from the task running the protocol
**                  timers when the periodic GKI timer expires.
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_ptim_timer_update(tPTIM_CB* p_cb) {
  TIMER_LIST_ENT* p_tle;
  UWB_HDR* p_msg;
  uint32_t new_ticks_count;
  uint32_t period_in_ticks;

  /* To handle the case when the function is called less frequently than the
     period
     we must convert determine the number of ticks since the last update, then
     convert back to milliseconds before updating timer list */
  new_ticks_count = phUwb_GKI_get_tick_count();

  /* Check for wrapped condition */
  if (new_ticks_count >= p_cb->last_gki_ticks) {
    period_in_ticks = (uint32_t)(new_ticks_count - p_cb->last_gki_ticks);
  } else {
    period_in_ticks = (uint32_t)(((uint32_t)0xffffffff - p_cb->last_gki_ticks) +
                                 new_ticks_count + 1);
  }

  /* update timer list */
  phUwb_GKI_update_timer_list(&p_cb->timer_queue,
                              GKI_TICKS_TO_MS(period_in_ticks));

  p_cb->last_gki_ticks = new_ticks_count;

  /* while there are expired timers */
  while ((p_cb->timer_queue.p_first) &&
         (p_cb->timer_queue.p_first->ticks <= 0)) {
    /* removed expired timer from list */
    p_tle = p_cb->timer_queue.p_first;
    UCI_TRACE_I("uwa_sys_ptim_timer_update expired: %p", p_tle);
    phUwb_GKI_remove_from_timer_list(&p_cb->timer_queue, p_tle);

    /* call timer callback */
    if (p_tle->p_cback) {
      (*p_tle->p_cback)(p_tle);
    } else if (p_tle->event) {
      p_msg = (UWB_HDR*)phUwb_GKI_getbuf(sizeof(UWB_HDR));
      if (p_msg != NULL) {
        p_msg->event = p_tle->event;
        p_msg->layer_specific = 0;
        uwa_sys_sendmsg(p_msg);
      }
    }
  }

  /* if timer list is empty stop periodic GKI timer */
  if (p_cb->timer_queue.p_first == NULL) {
    UCI_TRACE_I("ptim timer stop");
    phUwb_GKI_stop_timer(p_cb->timer_id, 0);
  }
}

/*******************************************************************************
**
** Function         uwa_sys_ptim_start_timer
**
** Description      Start a protocol timer for the specified amount
**                  of time in seconds.
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_ptim_start_timer(tPTIM_CB* p_cb, TIMER_LIST_ENT* p_tle,
                              uint16_t type, uint32_t timeout) {
  UCI_TRACE_I("uwa_sys_ptim_start_timer %p", p_tle);

  /* if timer list is currently empty, start periodic GKI timer */
  if (p_cb->timer_queue.p_first == NULL) {
    UCI_TRACE_I("ptim timer start");
    p_cb->last_gki_ticks = phUwb_GKI_get_tick_count();
    phUwb_GKI_start_timer(p_cb->timer_id, GKI_MS_TO_TICKS(p_cb->period), true);
  }

  phUwb_GKI_remove_from_timer_list(&p_cb->timer_queue, p_tle);

  p_tle->event = type;
  p_tle->ticks = timeout;

  phUwb_GKI_add_to_timer_list(&p_cb->timer_queue, p_tle);
}

/*******************************************************************************
**
** Function         uwa_sys_ptim_stop_timer
**
** Description      Stop a protocol timer.
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_ptim_stop_timer(tPTIM_CB* p_cb, TIMER_LIST_ENT* p_tle) {
  UCI_TRACE_I("uwa_sys_ptim_stop_timer %p", p_tle);

  phUwb_GKI_remove_from_timer_list(&p_cb->timer_queue, p_tle);

  /* if timer list is empty stop periodic GKI timer */
  if (p_cb->timer_queue.p_first == NULL) {
    UCI_TRACE_I("ptim timer stop");
    phUwb_GKI_stop_timer(p_cb->timer_id, 0);
  }
}

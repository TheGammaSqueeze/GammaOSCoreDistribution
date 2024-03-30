/******************************************************************************
 *
 *  Copyright (C) 2010-2014 Broadcom Corporation
 *..Copyright 2018-2020 NXP
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
 *  Entry point for UWB_TASK
 *
 ******************************************************************************/
#include <string.h>

#include "uci_hmsgs.h"
#include "uci_log.h"
#include "uwa_dm_int.h"
#include "uwa_sys.h"
#include "uwb_api.h"
#include "uwb_gki.h"
#include "uwb_hal_api.h"
#include "uwb_int.h"
#include "uwb_osal_common.h"
#include "uwb_target.h"

/*******************************************************************************
**
** Function         uwb_start_timer
**
** Description      Start a timer for the specified amount of time.
**                  NOTE: The timeout resolution is in SECONDS! (Even
**                          though the timer structure field is ticks)
**
** Returns          void
**
*******************************************************************************/
void uwb_start_timer(TIMER_LIST_ENT* p_tle, uint16_t type, uint32_t timeout) {
  UWB_HDR* p_msg;

  /* if timer list is currently empty, start periodic GKI timer */
  if (uwb_cb.timer_queue.p_first == NULL) {
    /* if timer starts on other than UWB task (scritp wrapper) */
    if (phUwb_GKI_get_taskid() != UWB_TASK) {
      /* post event to start timer in UWB task */
      p_msg = (UWB_HDR*)phUwb_GKI_getbuf(UWB_HDR_SIZE);
      if (p_msg != NULL) {
        p_msg->event = BT_EVT_TO_START_TIMER;
        phUwb_GKI_send_msg(UWB_TASK, UWB_MBOX_ID, p_msg);
      }
    } else {
      /* Start uwb_task 1-sec resolution timer */
      phUwb_GKI_start_timer(UWB_TIMER_ID, GKI_SECS_TO_TICKS(1), true);
    }
  }

  phUwb_GKI_remove_from_timer_list(&uwb_cb.timer_queue, p_tle);

  p_tle->event = type;
  p_tle->ticks = timeout; /* Save the number of seconds for the timer */

  phUwb_GKI_add_to_timer_list(&uwb_cb.timer_queue, p_tle);
}

/*******************************************************************************
**
** Function         uwb_remaining_time
**
** Description      Return amount of time to expire
**
** Returns          time in second
**
*******************************************************************************/
uint32_t uwb_remaining_time(TIMER_LIST_ENT* p_tle) {
  return (phUwb_GKI_get_remaining_ticks(&uwb_cb.timer_queue, p_tle));
}

/*******************************************************************************
**
** Function         uwb_process_timer_evt
**
** Description      Process uwb GKI timer event
**
** Returns          void
**
*******************************************************************************/
void uwb_process_timer_evt(void) {
  TIMER_LIST_ENT* p_tle;

  phUwb_GKI_update_timer_list(&uwb_cb.timer_queue, 1);

  while ((uwb_cb.timer_queue.p_first) && (!uwb_cb.timer_queue.p_first->ticks)) {
    p_tle = uwb_cb.timer_queue.p_first;
    phUwb_GKI_remove_from_timer_list(&uwb_cb.timer_queue, p_tle);

    if (uwb_cb.uwb_state == UWB_STATE_W4_HAL_CLOSE ||
        uwb_cb.uwb_state == UWB_STATE_NONE) {
      return;
    }
    switch (p_tle->event) {
      default:
        UCI_TRACE_I("uwb_process_timer_evt: timer:0x%p event (0x%04x)", p_tle,
                    p_tle->event);
        UCI_TRACE_W("uwb_process_timer_evt: unhandled timer event (0x%04x)",
                    p_tle->event);
    }
  }

  /* if timer list is empty stop periodic GKI timer */
  if (uwb_cb.timer_queue.p_first == NULL) {
    phUwb_GKI_stop_timer(UWB_TIMER_ID, 0);
  }
}

/*******************************************************************************
**
** Function         uwb_stop_timer
**
** Description      Stop a timer.
**
** Returns          void
**
*******************************************************************************/
void uwb_stop_timer(TIMER_LIST_ENT* p_tle) {
  phUwb_GKI_remove_from_timer_list(&uwb_cb.timer_queue, p_tle);

  /* if timer list is empty stop periodic GKI timer */
  if (uwb_cb.timer_queue.p_first == NULL) {
    phUwb_GKI_stop_timer(UWB_TIMER_ID, 0);
  }
}

/*******************************************************************************
**
** Function         uwb_start_quick_timer
**
** Description      Start a timer for the specified amount of time.
**                  NOTE: The timeout resolution depends on including modules.
**                  QUICK_TIMER_TICKS_PER_SEC should be used to convert from
**                  time to ticks.
**
**
** Returns          void
**
*******************************************************************************/
void uwb_start_quick_timer(TIMER_LIST_ENT* p_tle, uint16_t type,
                           uint32_t timeout) {
  UCI_TRACE_I("uwb_start_quick_timer enter: timeout: %d", timeout);
  UWB_HDR* p_msg;

  /* if timer list is currently empty, start periodic GKI timer */
  if (uwb_cb.quick_timer_queue.p_first == NULL) {
    /* if timer starts on other than UWB task (scritp wrapper) */
    if (phUwb_GKI_get_taskid() != UWB_TASK) {
      /* post event to start timer in UWB task */
      p_msg = (UWB_HDR*)phUwb_GKI_getbuf(UWB_HDR_SIZE);
      if (p_msg != NULL) {
        p_msg->event = BT_EVT_TO_START_QUICK_TIMER;
        phUwb_GKI_send_msg(UWB_TASK, UWB_MBOX_ID, p_msg);
      }
    } else {
      /* Quick-timer is required for LLCP */
      phUwb_GKI_start_timer(
          UWB_QUICK_TIMER_ID,
          ((GKI_SECS_TO_TICKS(1) / QUICK_TIMER_TICKS_PER_SEC)), true);
    }
  }

  phUwb_GKI_remove_from_timer_list(&uwb_cb.quick_timer_queue, p_tle);

  p_tle->event = type;
  p_tle->ticks = timeout; /* Save the number of ticks for the timer */

  phUwb_GKI_add_to_timer_list(&uwb_cb.quick_timer_queue, p_tle);
}

/*******************************************************************************
**
** Function         uwb_stop_quick_timer
**
** Description      Stop a timer.
**
** Returns          void
**
*******************************************************************************/
void uwb_stop_quick_timer(TIMER_LIST_ENT* p_tle) {
  UCI_TRACE_I("uwb_stop_quick_timer: enter");
  phUwb_GKI_remove_from_timer_list(&uwb_cb.quick_timer_queue, p_tle);

  /* if timer list is empty stop periodic GKI timer */
  if (uwb_cb.quick_timer_queue.p_first == NULL) {
    phUwb_GKI_stop_timer(UWB_QUICK_TIMER_ID, 0);
  }
}

/*******************************************************************************
**
** Function         uwb_process_quick_timer_evt
**
** Description      Process quick timer event
**
** Returns          void
**
*******************************************************************************/
void uwb_process_quick_timer_evt(void) {
  TIMER_LIST_ENT* p_tle;

  if (uwb_cb.uwb_state == UWB_STATE_W4_HAL_CLOSE ||
      uwb_cb.uwb_state == UWB_STATE_NONE) {
    return;
  }

  phUwb_GKI_update_timer_list(&uwb_cb.quick_timer_queue, 1);

  while ((uwb_cb.quick_timer_queue.p_first) &&
         (!uwb_cb.quick_timer_queue.p_first->ticks)) {
    p_tle = uwb_cb.quick_timer_queue.p_first;
    phUwb_GKI_remove_from_timer_list(&uwb_cb.quick_timer_queue, p_tle);

    switch (p_tle->event) {
      case UWB_TTYPE_UCI_WAIT_RSP:
        uwb_ucif_cmd_timeout();
        break;
      default:
        UCI_TRACE_I(
            "uwb_process_quick_timer_evt: unhandled timer event (0x%04x)",
            p_tle->event);
        break;
    }
  }

  /* if timer list is empty stop periodic GKI timer */
  if (uwb_cb.quick_timer_queue.p_first == NULL) {
    phUwb_GKI_stop_timer(UWB_QUICK_TIMER_ID, 0);
  }
}

/*******************************************************************************
**
** Function         uwb_task_shutdown_uwbc
**
** Description      Handle UWB shutdown
**
** Returns          nothing
**
*******************************************************************************/
void uwb_task_shutdown_uwbc(void) {
  UWB_HDR* p_msg;

  /* Free any messages still in the mbox */
  while ((p_msg = (UWB_HDR*)phUwb_GKI_read_mbox(UWB_MBOX_ID)) != NULL) {
    phUwb_GKI_freebuf(p_msg);
  }
  uwb_gen_cleanup();

  uwb_set_state(UWB_STATE_W4_HAL_CLOSE);
  uwb_cb.p_hal->close();

  /* Stop the timers */
  phUwb_GKI_stop_timer(UWB_TIMER_ID, 0);
  phUwb_GKI_stop_timer(UWB_QUICK_TIMER_ID, 0);
  phUwb_GKI_stop_timer(UWA_TIMER_ID, 0);
}

#define UWB_TASK_ARGS __attribute__((unused)) uint32_t arg

uint32_t uwb_task(__attribute__((unused)) uint32_t arg) {
  uint16_t event;
  UWB_HDR* p_msg;
  bool free_buf;
  /* Initialize the uwb control block */
  memset(&uwb_cb, 0, sizeof(tUWB_CB));

  UCI_TRACE_I("UWB_TASK started.");

  /* main loop */
  while (true) {
    event = phUwb_GKI_wait(0xFFFF, 0);
    if (event == EVENT_MASK(GKI_SHUTDOWN_EVT)) {
      break;
    }
    /* Handle UWB_TASK_EVT_TRANSPORT_READY from UWB HAL */
    if (event & UWB_TASK_EVT_TRANSPORT_READY) {
      UCI_TRACE_I("UWB_TASK got UWB_TASK_EVT_TRANSPORT_READY.");

      /* Reset the UWB controller. */
      uwb_set_state(UWB_STATE_IDLE);
      uwb_enabled(UWB_STATUS_OK, NULL);
    }

    if (event & UWB_MBOX_EVT_MASK) {
      /* Process all incoming UCI messages */
      while ((p_msg = (UWB_HDR*)phUwb_GKI_read_mbox(UWB_MBOX_ID)) != NULL) {
        free_buf = true;

        /* Determine the input message type. */
        switch (p_msg->event & UWB_EVT_MASK) {
          case BT_EVT_TO_UWB_UCI:
            free_buf = uwb_ucif_process_event(p_msg);
            break;

          case BT_EVT_TO_START_TIMER:
            /* Start uwb_task 1-sec resolution timer */
            phUwb_GKI_start_timer(UWB_TIMER_ID, GKI_SECS_TO_TICKS(1), true);
            break;

          case BT_EVT_TO_START_QUICK_TIMER:
            /* Quick-timer is required for LLCP */
            phUwb_GKI_start_timer(
                UWB_QUICK_TIMER_ID,
                ((GKI_SECS_TO_TICKS(1) / QUICK_TIMER_TICKS_PER_SEC)), true);
            break;

          case BT_EVT_TO_UWB_MSGS:
            uwb_main_handle_hal_evt((tUWB_HAL_EVT_MSG*)p_msg);
            break;

          default:
            UCI_TRACE_E("uwb_task: unhandle mbox message, event=%04x",
                        p_msg->event);
            break;
        }

        if (free_buf) {
          phUwb_GKI_freebuf(p_msg);
        }
      }
    }

    /* Process gki timer tick */
    if (event & UWB_TIMER_EVT_MASK) {
      uwb_process_timer_evt();
    }

    /* Process quick timer tick */
    if (event & UWB_QUICK_TIMER_EVT_MASK) {
      uwb_process_quick_timer_evt();
    }

    if (event & UWA_MBOX_EVT_MASK) {
      while ((p_msg = (UWB_HDR*)phUwb_GKI_read_mbox(UWA_MBOX_ID)) != NULL) {
        uwa_sys_event(p_msg);
      }
    }

    if (event & UWA_TIMER_EVT_MASK) {
      uwa_sys_timer_update();
    }
  }

  UCI_TRACE_I("uwb_task terminated");

  phUwb_GKI_exit_task(phUwb_GKI_get_taskid());
  return 0;
}

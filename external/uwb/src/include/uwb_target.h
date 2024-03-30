/******************************************************************************
 *
 *  Copyright (C) 1999-2012 Broadcom Corporation
 *  Copyright 2018-2020 NXP
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

#ifndef UWB_TARGET_H
#define UWB_TARGET_H

#include "data_types.h"

#ifndef USERIAL_DEBUG
#define USERIAL_DEBUG false
#endif

/* Include common GKI definitions used by this platform */
#include "uwb_gki_target.h"

/* UCI Command, Notification or Data*/
#define BT_EVT_TO_UWB_UCI 0x4000
/* messages between UWB and UCI task */
#define BT_EVT_TO_UWB_MSGS 0x4300

/* start timer */
#define BT_EVT_TO_START_TIMER 0x3c00

/* start quick timer */
#define BT_EVT_TO_START_QUICK_TIMER 0x3e00

/******************************************************************************
**
** GKI Mail Box and Timer
**
******************************************************************************/

/* Mailbox event mask for UWB stack */
#ifndef UWB_MBOX_EVT_MASK
#define UWB_MBOX_EVT_MASK (TASK_MBOX_0_EVT_MASK)
#endif

/* Mailbox ID for UWB stack */
#ifndef UWB_MBOX_ID
#define UWB_MBOX_ID (TASK_MBOX_0)
#endif

/* Mailbox event mask for UWA */
#ifndef UWA_MBOX_EVT_MASK
#define UWA_MBOX_EVT_MASK (TASK_MBOX_2_EVT_MASK)
#endif

/* Mailbox ID for UWA */
#ifndef UWA_MBOX_ID
#define UWA_MBOX_ID (TASK_MBOX_2)
#endif

/* GKI timer id used for protocol timer in UWB stack */
#ifndef UWB_TIMER_ID
#define UWB_TIMER_ID (TIMER_0)
#endif

/* GKI timer event mask used for protocol timer in UWB stack */
#ifndef UWB_TIMER_EVT_MASK
#define UWB_TIMER_EVT_MASK (TIMER_0_EVT_MASK)
#endif

/* GKI timer id used for quick timer in UWB stack */
#ifndef UWB_QUICK_TIMER_ID
#define UWB_QUICK_TIMER_ID (TIMER_1)
#endif

/* GKI timer event mask used for quick timer in UWB stack */
#ifndef UWB_QUICK_TIMER_EVT_MASK
#define UWB_QUICK_TIMER_EVT_MASK (TIMER_1_EVT_MASK)
#endif

/* GKI timer id used for protocol timer in UWA */
#ifndef UWA_TIMER_ID
#define UWA_TIMER_ID (TIMER_2)
#endif

/* GKI timer event mask used for protocol timer in UWA */
#ifndef UWA_TIMER_EVT_MASK
#define UWA_TIMER_EVT_MASK (TIMER_2_EVT_MASK)
#endif

/* Quick Timer */
#ifndef QUICK_TIMER_TICKS_PER_SEC
#define QUICK_TIMER_TICKS_PER_SEC 100 /* 10ms timer */
#endif

/******************************************************************************
**
** GKI Buffer Pools
**
******************************************************************************/

/* UCI command/notification/data */
#ifndef UWB_UCI_POOL_ID
#define UWB_UCI_POOL_ID GKI_POOL_ID_2
#endif

/******************************************************************************
**
** UCI Transport definitions
**
******************************************************************************/
/* offset of the first UCI packet in buffer for outgoing */
#ifndef UCI_MSG_OFFSET_SIZE
#define UCI_MSG_OFFSET_SIZE 1
#endif

/******************************************************************************
**
** UWB
**
******************************************************************************/

/* Timeout for receiving response to UCI command in case of retry */
#ifndef UWB_CMD_RETRY_TIMEOUT
#define UWB_CMD_RETRY_TIMEOUT 75  // 75ms
#endif

/* Timeout for receiving response to UCI command */
#ifndef UWB_CMD_CMPL_TIMEOUT
#define UWB_CMD_CMPL_TIMEOUT 100  // 100ms
#endif

/* Maximum number of UCI commands that the UWBC accepts without needing to wait
 * for response */
#ifndef UCI_MAX_CMD_WINDOW
#define UCI_MAX_CMD_WINDOW 1
#endif

#ifndef UCI_CMD_MAX_RETRY_COUNT
#define UCI_CMD_MAX_RETRY_COUNT 10
#endif

/*****************************************************************************
**  Define HAL_WRITE depending on whether HAL is using shared GKI resources
**  as the UWB stack.
*****************************************************************************/
#ifndef HAL_WRITE
#define HAL_WRITE(p)                                            \
  {                                                             \
    uwb_cb.p_hal->write(p->len, (uint8_t*)(p + 1) + p->offset); \
    phUwb_GKI_freebuf(p);                                       \
  }
#define HAL_RE_WRITE(p) \
  { uwb_cb.p_hal->write(p->len, (uint8_t*)(p + 1) + p->offset); }

#define HAL_UCI_CMD_WRITE(len, buf) \
  { uwb_cb.p_hal->write(len, (uint8_t*)buf); }
/* Mem allocation with 8 byte alignment */
#define HAL_MALLOC(x) malloc(((x - 1) | 7) + 1)
#endif /* HAL_WRITE */

#endif /* UWB_TARGET_H */

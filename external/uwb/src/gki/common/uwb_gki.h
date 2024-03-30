/******************************************************************************
 *
 *  Copyright (C) 1999-2012 Broadcom Corporation
 *  Copyright 2019 NXP
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
#ifndef UWB_GKI_H
#define UWB_GKI_H

#include <string>

#include "uwb_gki_target.h"
#include "uwb_target.h"

/* Error codes */
#define GKI_SUCCESS 0x00
#define GKI_FAILURE 0x01
#define GKI_INVALID_TASK 0xF0
#define GKI_INVALID_POOL 0xFF

/************************************************************************
** Mailbox definitions. Each task has 4 mailboxes that are used to
** send buffers to the task.
*/
#define TASK_MBOX_0 0
#define TASK_MBOX_2 2

#define NUM_TASK_MBOX 4

/************************************************************************
** Event definitions.
**
** There are 4 reserved events used to signal messages rcvd in task mailboxes.
** There are 4 reserved events used to signal timeout events.
** There are 8 general purpose events available for applications.
*/

#define TASK_MBOX_0_EVT_MASK 0x0001
#define TASK_MBOX_1_EVT_MASK 0x0002
#define TASK_MBOX_2_EVT_MASK 0x0004
#define TASK_MBOX_3_EVT_MASK 0x0008

#define TIMER_0 0
#define TIMER_1 1
#define TIMER_2 2
#define TIMER_3 3

#define TIMER_0_EVT_MASK 0x0010
#define TIMER_1_EVT_MASK 0x0020
#define TIMER_2_EVT_MASK 0x0040
#define TIMER_3_EVT_MASK 0x0080

#define APPL_EVT_0 8
#define APPL_EVT_7 15

#define EVENT_MASK(evt) ((uint16_t)(0x0001 << (evt)))

/************************************************************************
**  Max Time Queue
**/
#ifndef GKI_MAX_TIMER_QUEUES
#define GKI_MAX_TIMER_QUEUES 3
#endif

/************************************************************************
**  Utility macros for timer conversion
**/
#ifdef TICKS_PER_SEC
#define GKI_MS_TO_TICKS(x) ((x) / (1000 / TICKS_PER_SEC))
#define GKI_SECS_TO_TICKS(x) ((x) * (TICKS_PER_SEC))
#define GKI_TICKS_TO_MS(x) ((x) * (1000 / TICKS_PER_SEC))
#define GKI_TICKS_TO_SECS(x) ((x) * (1 / TICKS_PER_SEC))
#endif

#ifndef GKI_SHUTDOWN_EVT
#define GKI_SHUTDOWN_EVT APPL_EVT_7
#endif

/* Timer list entry callback type
 */
struct TIMER_LIST_ENT;
typedef void(TIMER_CBACK)(TIMER_LIST_ENT* p_tle);

/* Define a timer list entry
 */
struct TIMER_LIST_ENT {
  TIMER_LIST_ENT* p_next;
  TIMER_LIST_ENT* p_prev;
  TIMER_CBACK* p_cback;
  int32_t ticks;
  uintptr_t param;
  uint16_t event;
  uint8_t in_use;
};

/* Define a timer list queue
 */
typedef struct {
  TIMER_LIST_ENT* p_first;
  TIMER_LIST_ENT* p_last;
  int32_t last_ticks;
} TIMER_LIST_Q;

/***********************************************************************
** This queue is a general purpose buffer queue, for application use.
*/
typedef struct {
  void* p_first;
  void* p_last;
  uint16_t count;
} BUFFER_Q;

/* Task constants
 */
#ifndef TASKPTR
typedef uint32_t (*TASKPTR)(uint32_t);
#endif

/* General pool accessible to GKI_getbuf() */
#define GKI_RESTRICTED_POOL 1 /* Inaccessible pool to GKI_getbuf() */

/***********************************************************************
** Function prototypes
*/

/* Task management
 */
extern uint8_t phUwb_GKI_create_task(TASKPTR, uint8_t, int8_t*, uint16_t*,
                                     uint16_t, void*, void*);
extern void phUwb_GKI_exit_task(uint8_t);
extern uint8_t phUwb_GKI_get_taskid(void);
extern void phUwb_GKI_init(void);
extern void phUwb_GKI_run(void*);

/* To send buffers and events between tasks
 */
extern uint8_t phUwb_GKI_isend_event(uint8_t, uint16_t);
extern void* phUwb_GKI_read_mbox(uint8_t);
extern void phUwb_GKI_send_msg(uint8_t, uint8_t, void*);
extern uint8_t phUwb_GKI_send_event(uint8_t, uint16_t);

/* To get and release buffers, change owner and get size
 */
extern void phUwb_GKI_freebuf(void*);
extern void* phUwb_GKI_getbuf(uint16_t);
extern uint16_t phUwb_GKI_get_buf_size(void*);
extern void* phUwb_GKI_getpoolbuf(uint8_t);

/* User buffer queue management
 */
extern void* phUwb_GKI_dequeue(BUFFER_Q*);
extern void phUwb_GKI_enqueue(BUFFER_Q*, void*);
extern void phUwb_GKI_init_q(BUFFER_Q*);

/* Timer management
 */
extern void phUwb_GKI_add_to_timer_list(TIMER_LIST_Q*, TIMER_LIST_ENT*);
extern uint32_t phUwb_GKI_get_tick_count(void);
extern void phUwb_GKI_init_timer_list(TIMER_LIST_Q*);
extern void phUwb_GKI_remove_from_timer_list(TIMER_LIST_Q*, TIMER_LIST_ENT*);
extern void phUwb_GKI_start_timer(uint8_t, int32_t, bool);
extern void phUwb_GKI_stop_timer(uint8_t, int);
extern void phUwb_GKI_timer_update(uint32_t);
extern uint16_t phUwb_GKI_update_timer_list(TIMER_LIST_Q*, uint32_t);
extern uint32_t phUwb_GKI_get_remaining_ticks(TIMER_LIST_Q*, TIMER_LIST_ENT*);
extern uint16_t phUwb_GKI_wait(uint16_t, uint32_t);

/* Start and Stop system time tick callback
 * true for start system tick if time queue is not empty
 * false to stop system tick if time queue is empty
 */
typedef void(SYSTEM_TICK_CBACK)(bool);

/* Time queue management for system ticks
 */
extern void phUwb_GKI_timer_queue_register_callback(SYSTEM_TICK_CBACK*);

/* Disable Interrupts, Enable Interrupts
 */
extern void phUwb_GKI_enable(void);
extern void phUwb_GKI_disable(void);

/* Allocate (Free) memory from an OS
 */
extern void* phUwb_GKI_os_malloc(uint32_t);
extern void phUwb_GKI_os_free(void*);

/* Exception handling
 */
extern void phUwb_GKI_exception(uint16_t, std::string);

#endif

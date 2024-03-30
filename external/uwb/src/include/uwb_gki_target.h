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
#ifndef UWB_GKI_TARGET_H
#define UWB_GKI_TARGET_H

#include "data_types.h"

/******************************************************************************
**
** Task configuration
**
******************************************************************************/

/* Definitions of task IDs for inter-task messaging */
#ifndef MMI_TASK
#define MMI_TASK 0
#endif

#ifndef HCISU_TASK
#define HCISU_TASK 1
#endif

#ifndef UCIT_TASK
#define UCIT_TASK 2
#endif

#ifndef UWB_TASK
#define UWB_TASK 3
#endif

#ifndef BTU_TASK
#define BTU_TASK 4
#endif

/* The number of GKI tasks in the software system. */
#ifndef GKI_MAX_TASKS
#define GKI_MAX_TASKS 5
#endif

/******************************************************************************
**
** Timer configuration
**
******************************************************************************/

/* The number of GKI timers in the software system. */
#ifndef GKI_NUM_TIMERS
#define GKI_NUM_TIMERS 3
#endif

/* A conversion value for translating ticks to calculate GKI timer.  */
#ifndef TICKS_PER_SEC
#define TICKS_PER_SEC 100
#endif

/* delay in ticks before stopping system tick. */
#ifndef GKI_DELAY_STOP_SYS_TICK
#define GKI_DELAY_STOP_SYS_TICK 10
#endif

/******************************************************************************
**
** Buffer configuration
**
******************************************************************************/
#define GKI_ENABLE_BUF_CORRUPTION_CHECK TRUE
#define GKI_DEF_BUFPOOL_PERM_MASK 0xfff0
#define GKI_NUM_TOTAL_BUF_POOLS 10
#define GKI_NUM_FIXED_BUF_POOLS 4

/* The size of the buffers in pool 0 */
#define GKI_POOL_ID_0 0
#define GKI_BUF0_SIZE 268
#define GKI_BUF0_MAX 40

/* The size of the buffers in pool 1 */
#define GKI_POOL_ID_1
#define GKI_BUF1_SIZE 428
#define GKI_BUF1_MAX 26

/* The size of the buffers in pool 2 */
#define GKI_POOL_ID_2 2
#define GKI_BUF2_SIZE 4200
#define GKI_BUF2_MAX 50

/* The size of the buffers in pool 3 */
#define GKI_POOL_ID_3 3
#define GKI_BUF3_SIZE 4200
#define GKI_BUF3_MAX 30

#endif /* UWB_GKI_TARGET_H */

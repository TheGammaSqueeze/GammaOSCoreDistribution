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
#ifndef UWA_SYS_INT_H
#define UWA_SYS_INT_H

#include "uwa_sys_ptim.h"

/*****************************************************************************
**  Constants and data types
*****************************************************************************/

/* uwa_sys flags */
#define UWA_SYS_FL_INITIALIZED 0x00000001 /* uwa_sys initialized */

/*****************************************************************************
**  state table
*****************************************************************************/

/* system manager control block */
typedef struct {
  uint32_t flags; /* uwa_sys flags (must be first element of structure) */
  tUWA_SYS_REG* reg[UWA_ID_MAX]; /* registration structures */
  bool is_reg[UWA_ID_MAX];       /* registration structures */
  tPTIM_CB ptim_cb;              /* protocol timer list */
  tUWA_SYS_ENABLE_CBACK* p_enable_cback;
  uint16_t enable_cplt_flags;
  uint16_t enable_cplt_mask;

  bool graceful_disable; /* TRUE if UWA_Disable () is called with TRUE */
  bool timers_disabled;  /* TRUE if sys timers disabled */
} tUWA_SYS_CB;

/*****************************************************************************
**  Global variables
*****************************************************************************/

/* system manager control block */
extern tUWA_SYS_CB uwa_sys_cb;

/* system manager configuration structure */
extern tUWA_SYS_CFG* p_uwa_sys_cfg;

#endif /* UWA_SYS_INT_H */

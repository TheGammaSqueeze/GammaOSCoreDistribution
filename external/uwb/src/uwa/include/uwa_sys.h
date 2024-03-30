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

#ifndef UWA_SYS_H
#define UWA_SYS_H

#include "uwa_api.h"
#include "uwb_gki.h"
#include "uwb_target.h"

/*****************************************************************************
**  Constants and data types
*****************************************************************************/

/* SW sub-systems */
enum {
  UWA_ID_SYS, /* system manager                      */
  UWA_ID_DM,  /* device manager                      */
  UWA_ID_MAX
};
typedef uint8_t tUWA_SYS_ID;

/* enable function type */
typedef void(tUWA_SYS_ENABLE)(void);

/* event handler function type */
typedef bool(tUWA_SYS_EVT_HDLR)(UWB_HDR* p_msg);

/* disable function type */
typedef void(tUWA_SYS_DISABLE)(void);

typedef void(tUWA_SYS_ENABLE_CBACK)(void);

/* registration structure */
typedef struct {
  tUWA_SYS_ENABLE* enable;
  tUWA_SYS_EVT_HDLR* evt_hdlr;
  tUWA_SYS_DISABLE* disable;
} tUWA_SYS_REG;

/* system manager configuration structure */
typedef struct {
  uint16_t mbox_evt; /* GKI mailbox event */
  uint8_t mbox;      /* GKI mailbox id */
  uint8_t timer;     /* GKI timer id */
} tUWA_SYS_CFG;

/*****************************************************************************
**  Global data
*****************************************************************************/

/*****************************************************************************
**  Macros
*****************************************************************************/

/* Calculate start of event enumeration; id is top 8 bits of event */
#define UWA_SYS_EVT_START(id) ((id) << 8)

/*****************************************************************************
**  Function declarations
*****************************************************************************/
extern void uwa_sys_init(void);
extern void uwa_sys_event(UWB_HDR* p_msg);
extern void uwa_sys_timer_update(void);
extern void uwa_sys_disable_timers(void);

extern void uwa_sys_register(uint8_t id, const tUWA_SYS_REG* p_reg);
extern void uwa_sys_deregister(uint8_t id);
extern void uwa_sys_check_disabled(void);
extern bool uwa_sys_is_register(uint8_t id);
extern void uwa_sys_disable_subsystems(bool graceful);
extern void uwa_sys_enable_subsystems(void);

extern bool uwa_sys_is_graceful_disable(void);
extern void uwa_sys_sendmsg(void* p_msg);
extern void uwa_sys_start_timer(TIMER_LIST_ENT* p_tle, uint16_t type,
                                uint32_t timeout);
extern void uwa_sys_stop_timer(TIMER_LIST_ENT* p_tle);

extern void uwa_sys_cback_reg_enable_complete(tUWA_SYS_ENABLE_CBACK* p_cback);
extern void uwa_sys_cback_notify_enable_complete(uint8_t id);

#endif /* UWA_SYS_H */

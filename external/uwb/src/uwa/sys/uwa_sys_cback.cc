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
 *  Registration/deregistration functions for inter-module callbacks
 *
 ******************************************************************************/

#include "uci_log.h"
#include "uwa_sys.h"
#include "uwa_sys_int.h"
#include "uwb_osal_common.h"

/*******************************************************************************
**
** Function         uwa_sys_cback_reg_enable_complete
**
** Description      Called to register an initialization complete callback
**                  function
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_cback_reg_enable_complete(tUWA_SYS_ENABLE_CBACK* p_cback) {
  uwa_sys_cb.p_enable_cback = p_cback;
  uwa_sys_cb.enable_cplt_flags = 0;
}

/*******************************************************************************
**
** Function         uwa_sys_cback_notify_enable_complete
**
** Description      Called by other UWA subsystems to notify initialization is
**                  complete
**
** Returns          void
**
*******************************************************************************/
void uwa_sys_cback_notify_enable_complete(uint8_t id) {
  uwa_sys_cb.enable_cplt_flags |= (uint16_t)(0x0001 << id);

  UCI_TRACE_I("enable_cplt_flags=0x%x, enable_cplt_mask=0x%x",
              uwa_sys_cb.enable_cplt_flags, uwa_sys_cb.enable_cplt_mask);

  if ((uwa_sys_cb.enable_cplt_flags == uwa_sys_cb.enable_cplt_mask) &&
      (uwa_sys_cb.p_enable_cback)) {
    uwa_sys_cb.p_enable_cback();
    uwa_sys_cb.p_enable_cback = NULL;
  }
}

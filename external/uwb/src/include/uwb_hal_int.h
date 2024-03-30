/******************************************************************************
 *
 *  Copyright (C) 1999-2014 Broadcom Corporation
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
 *  this file contains the UCI transport internal definitions and functions.
 *
 ******************************************************************************/

#ifndef UWB_HAL_INT_H
#define UWB_HAL_INT_H

#include "uci_defs.h"
#include "uwb_gki.h"
#include "uwb_hal_api.h"

enum {
  HAL_UWB_OPEN_CPLT_EVT = 0x00,
  HAL_UWB_CLOSE_CPLT_EVT = 0x01,
  HAL_UWB_ERROR_EVT = 0x02
};

typedef uint8_t uwb_event_t;
typedef uint8_t uwb_status_t;

/*`
 * The callback passed in from the UWB stack that the HAL
 * can use to pass events back to the stack.
 */
typedef void(uwb_stack_callback_t)(uwb_event_t event,
                                   uwb_status_t event_status);

/*
 * The callback passed in from the UWB stack that the HAL
 * can use to pass incoming data to the stack.
 */
typedef void(uwb_stack_data_callback_t)(uint16_t data_len, uint8_t* p_data);

#endif /* UWB_HAL_INT_H */

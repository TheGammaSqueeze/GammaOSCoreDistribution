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
 *  UWB Hardware Abstraction Layer API
 *
 ******************************************************************************/
#ifndef UWB_HAL_API_H
#define UWB_HAL_API_H
#include "data_types.h"
#include "hal_uwb.h"

typedef uint8_t tUWB_STATUS;
typedef void(tHAL_UWB_CBACK)(uint8_t event, tUWB_STATUS status);
typedef void(tHAL_UWB_DATA_CBACK)(uint16_t data_len, uint8_t* p_data);

/*******************************************************************************
** tHAL_UWB_ENTRY HAL entry-point lookup table
*******************************************************************************/
typedef void(tHAL_API_OPEN)(tHAL_UWB_CBACK* p_hal_cback,
                            tHAL_UWB_DATA_CBACK* p_data_cback);
typedef void(tHAL_API_CLOSE)(void);
typedef tUWB_STATUS(tHAL_API_CORE_INIT)();
typedef tUWB_STATUS(tHAL_API_SESSION_INIT)(int);
typedef void(tHAL_API_WRITE)(uint16_t data_len, uint8_t* p_data);
typedef tUWB_STATUS(tHAL_API_IOCTL)(long arg, void* p_data);

typedef struct {
  tHAL_API_OPEN* open;
  tHAL_API_CLOSE* close;
  tHAL_API_WRITE* write;
  tHAL_API_IOCTL* ioctl;
  tHAL_API_CORE_INIT* CoreInitialization;
  tHAL_API_SESSION_INIT* SessionInitialization;
} tHAL_UWB_ENTRY;

typedef struct {
  tHAL_UWB_ENTRY* hal_entry_func;
} tHAL_UWB_CONTEXT;

#endif /* UWB_HAL_API_H  */

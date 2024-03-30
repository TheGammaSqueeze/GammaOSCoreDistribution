/******************************************************************************
 *
 *  Copyright 2018-2020 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
#ifndef ANDROID_HARDWARE_HAL_NXPUWB_V1_0_H
#define ANDROID_HARDWARE_HAL_NXPUWB_V1_0_H
#include <string>
#include <vector>

enum {
  HAL_UWB_STATUS_OK = 0x00,
  HAL_UWB_STATUS_ERR_TRANSPORT = 0x01,
  HAL_UWB_STATUS_ERR_CMD_TIMEOUT = 0x02
};

enum NxpUwbHalStatus {
  /** In case of an error, HCI network needs to be re-initialized */
  HAL_STATUS_OK = 0x00,
};

#endif  // ANDROID_HARDWARE_HAL_NXPUWB_V1_0_H

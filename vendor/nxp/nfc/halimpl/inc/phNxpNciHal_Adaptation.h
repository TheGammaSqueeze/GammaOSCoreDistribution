/*
 * Copyright 2015-2020 NXP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _PHNXPNCIHAL_ADAPTATION_H_
#define _PHNXPNCIHAL_ADAPTATION_H_

#include "EseUpdateChecker.h"
#include <android/hardware/nfc/1.2/INfc.h>
#include <android/hardware/nfc/1.2/types.h>
#include <hardware/hardware.h>
#include <hardware/nfc.h>

using ::android::hardware::nfc::V1_2::NfcConfig;

extern ESE_UPDATE_STATE eseUpdateSpi;
extern ESE_UPDATE_STATE eseUpdateDwp;

/* NXP HAL functions */

int phNxpNciHal_open(nfc_stack_callback_t* p_cback,
                     nfc_stack_data_callback_t* p_data_cback);
int phNxpNciHal_write(uint16_t data_len, const uint8_t* p_data);
int phNxpNciHal_write_internal(uint16_t data_len, const uint8_t* p_data);
int phNxpNciHal_ioctl(long arg, void* p_data);
int phNxpNciHal_core_initialized(uint8_t* p_core_init_rsp_params);
int phNxpNciHal_pre_discover(void);
int phNxpNciHal_close(bool);
int phNxpNciHal_configDiscShutdown(void);
int phNxpNciHal_control_granted(void);
int phNxpNciHal_power_cycle(void);
void phNxpNciHal_getVendorConfig(
    android::hardware::nfc::V1_1::NfcConfig &config);
void phNxpNciHal_getVendorConfig_1_2(NfcConfig &config);
int phNxpNciHal_MinInit(nfc_stack_callback_t* p_cback,
                        nfc_stack_data_callback_t* p_data_cback);
void phNxpNciHal_reset_nfcee_session(bool force_session_reset);
int phNxpNciHal_Minclose(void);
int phNxpNciHal_getFWDownloadFlag(uint8_t* fwDnldRequest);
#endif /* _PHNXPNCIHAL_ADAPTATION_H_ */

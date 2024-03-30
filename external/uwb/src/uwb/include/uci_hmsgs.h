/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Copyright 2021 NXP.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/******************************************************************************
 *
 *  defines UCI interface messages (for DH)
 *
 ******************************************************************************/
#ifndef UWB_UCI_HMSGS_H
#define UWB_UCI_HMSGS_H

#include <stdbool.h>

#include "uci_defs.h"
#include "uwb_types.h"

uint8_t uci_snd_get_device_info_cmd(void);
uint8_t uci_snd_device_reset_cmd(uint8_t resetConfig);
uint8_t uci_snd_core_set_config_cmd(uint8_t* p_param_tlvs, uint8_t tlv_size);
uint8_t uci_snd_core_get_config_cmd(uint8_t* param_ids, uint8_t num_ids);
uint8_t uci_snd_core_get_device_capability(void);
uint8_t uci_snd_session_init_cmd(uint32_t session_id, uint8_t session_type);
uint8_t uci_snd_session_deinit_cmd(uint32_t session_id);
uint8_t uci_snd_get_session_count_cmd(void);
uint8_t uci_snd_get_range_count_cmd(uint32_t session_id);
uint8_t uci_snd_get_session_status_cmd(uint32_t session_id);
uint8_t uci_snd_multicast_list_update_cmd(uint32_t session_id, uint8_t action,
                                          uint8_t noOfControlees,
                                          uint16_t* shortAddressList,
                                          uint32_t* subSessionIdList);
uint8_t uci_snd_set_country_code_cmd(uint8_t* country_code);
uint8_t uci_snd_app_get_config_cmd(uint32_t session_id, uint8_t num_ids,
                                   uint8_t length, uint8_t* param_ids);
uint8_t uci_snd_app_set_config_cmd(uint32_t session_id, uint8_t num_ids,
                                   uint8_t length, uint8_t* data);
uint8_t uci_snd_range_start_cmd(uint32_t session_id);
uint8_t uci_snd_range_stop_cmd(uint32_t session_id);
uint8_t uci_snd_blink_data_cmd(uint32_t session_id, uint8_t repetition_count,
                               uint8_t app_data_len, uint8_t* app_data);

/*  APIs for UWB RF test functionality */
uint8_t uci_snd_test_get_config_cmd(uint32_t session_id, uint8_t num_ids,
                                    uint8_t length, uint8_t* param_ids);
uint8_t uci_snd_test_set_config_cmd(uint32_t session_id, uint8_t num_ids,
                                    uint8_t length, uint8_t* data);
uint8_t uci_snd_test_periodic_tx_cmd(uint16_t length, uint8_t* data);
uint8_t uci_snd_test_per_rx_cmd(uint16_t length, uint8_t* data);
uint8_t uci_snd_test_uwb_loopback_cmd(uint16_t length, uint8_t* data);
uint8_t uci_snd_test_stop_session_cmd(void);
uint8_t uci_snd_test_rx_cmd(void);

extern void uci_proc_session_management_rsp(uint8_t op_code, uint8_t* p_buf,
                                            uint16_t len);
extern void uci_proc_session_management_ntf(uint8_t op_code, uint8_t* p_buf,
                                            uint16_t len);
extern void uci_proc_device_management_ntf(uint8_t op_code, uint8_t* p_buf,
                                           uint16_t len);
extern void uci_proc_core_management_ntf(uint8_t op_code, uint8_t* p_buf,
                                         uint16_t len);
extern void uci_proc_rang_management_rsp(uint8_t op_code, uint8_t* p_buf,
                                         uint16_t len);
extern void uci_proc_rang_management_ntf(uint8_t op_code, uint8_t* p_buf,
                                         uint16_t len);
extern void uci_proc_android_rsp(uint8_t op_code, uint8_t* p_buf, uint16_t len);
extern void uci_proc_test_management_ntf(uint8_t op_code, uint8_t* p_buf,
                                         uint16_t len);
extern void uci_proc_test_management_rsp(uint8_t op_code, uint8_t* p_buf,
                                         uint16_t len);
extern void uci_proc_raw_cmd_rsp(uint8_t* p_buf, uint16_t len);


extern void uci_proc_vendor_specific_ntf(uint8_t gid, uint8_t* p_buf, uint16_t len);
#endif /* UWB_UCI_MSGS_H */

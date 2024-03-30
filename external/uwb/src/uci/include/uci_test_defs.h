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
 *  This file contains the definition from UCI specification
 *
 ******************************************************************************/

#ifndef UWB_UCI_TEST_DEFS_H
#define UWB_UCI_TEST_DEFS_H

#include <stdint.h>

/* GID: Group Identifier (byte 0) */

#define UCI_GID_TEST 0x0D /* 1101b UCI Test group */

/**********************************************
 * UCI test group(UCI_GID_TEST)- 7: Opcodes
 **********************************************/
#define UCI_MSG_TEST_SET_CONFIG 0
#define UCI_MSG_TEST_GET_CONFIG 1
#define UCI_MSG_TEST_PERIODIC_TX 2
#define UCI_MSG_TEST_PER_RX 3
#define UCI_MSG_TEST_TX 4
#define UCI_MSG_TEST_RX 5
#define UCI_MSG_TEST_LOOPBACK 6
#define UCI_MSG_TEST_STOP_SESSION 7

#define UCI_MSG_TEST_PERIODIC_TX_CMD_SIZE 0
#define UCI_MSG_TEST_PER_RX_CMD_SIZE 0
#define UCI_MSG_TEST_STOP_SESSION_CMD_SIZE 0
#define UCI_MSG_TEST_RX_CMD_SIZE 0

/**********************************************************
 * UCI test Parameter IDs : RF Test Configurations
 *********************************************************/
#define UCI_TEST_PARAM_ID_NUM_PACKETS 0x00
#define UCI_TEST_PARAM_ID_T_GAP 0x01
#define UCI_TEST_PARAM_ID_T_START 0x02
#define UCI_TEST_PARAM_ID_T_WIN 0x03
#define UCI_TEST_PARAM_ID_RANDOMIZE_PSDU 0x04
#define UCI_TEST_PARAM_ID_PHR_RANGING_BIT 0x05
#define UCI_TEST_PARAM_ID_RMARKER_TX_START 0x06
#define UCI_TEST_PARAM_ID_RMARKER_RX_START 0x07
#define UCI_TEST_PARAM_ID_STS_INDEX_AUTO_INCR 0x08

#endif
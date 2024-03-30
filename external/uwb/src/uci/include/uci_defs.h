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

#ifndef UWB_UCI_DEFS_H
#define UWB_UCI_DEFS_H

#include <stdint.h>

/* Define the message header size for all UCI Commands and Notifications.
 */
#define UCI_MSG_HDR_SIZE 0x04     /* per UCI spec */
#define UCI_MAX_PAYLOAD_SIZE 4096 /* max control message size */
#define UCI_MAX_PKT_SIZE 259 /* Max payload size + header size */
#define UCI_PSDU_SIZE_4K 4096     /* PSDU Data size */
#define UCI_VENDOR_INFO_MAX_SIZE 0xFF

#define UCI_PAYLOAD_SUPPORT 1
#define MAX_UCI_DATA_PKT_SIZE 4096
#define LENGTH_INDCATOR_BIT 0x80  // 1000 0000
#define UCI_LENGTH_SHIFT 8
#define UCI_RESPONSE_STATUS_OFFSET 0x04

/* UCI Command and Notification Format:
 * 4 byte message header:
 * byte 0: MT PBF GID
 * byte 1: OID
 * byte 2: RFU - To be used for extended playload length
 * byte 3: Message Length */

/* MT: Message Type (byte 0) */
#define UCI_MT_MASK 0xE0
#define UCI_MT_SHIFT 0x05
#define UCI_MT_CMD 0x01 /* (UCI_MT_CMD << UCI_MT_SHIFT) = 0x20 */
#define UCI_MT_RSP 0x02 /* (UCI_MT_RSP << UCI_MT_SHIFT) = 0x40 */
#define UCI_MT_NTF 0x03 /* (UCI_MT_NTF << UCI_MT_SHIFT) = 0x60 */

#define UCI_MTS_CMD 0x20
#define UCI_MTS_RSP 0x40
#define UCI_MTS_NTF 0x60

#define UCI_NTF_BIT 0x80 /* the tUWB_VS_EVT is a notification */
#define UCI_RSP_BIT 0x40 /* the tUWB_VS_EVT is a response     */

/* PBF: Packet Boundary Flag (byte 0) */
#define UCI_PBF_MASK 0x10
#define UCI_PBF_SHIFT 0x04
#define UCI_PBF_NO_OR_LAST 0x00 /* not fragmented or last fragment */
#define UCI_PBF_ST_CONT 0x10    /* start or continuing fragment */

/* GID: Group Identifier (byte 0) */
#define UCI_GID_MASK 0x0F
#define UCI_GID_SHIFT 0x00
#define UCI_GID_CORE 0x00           /* 0000b UCI Core group */
#define UCI_GID_SESSION_MANAGE 0x01 /* 0001b Session Config commands */
#define UCI_GID_RANGE_MANAGE 0x02   /* 0010b Range Management group */
#define UCI_GID_ANDROID 0x0E        /* 1110b Android vendor group */
#define UCI_GID_TEST 0x0D           /* 1101b RF Test Gropup */

/* Vendor specific group Identifier */
#define UCI_GID_VENDOR_SPECIFIC_0x09 0x09
#define UCI_GID_VENDOR_SPECIFIC_0x0A 0x0A
#define UCI_GID_VENDOR_SPECIFIC_0x0B 0x0B
#define UCI_GID_VENDOR_SPECIFIC_0x0C 0x0C
#define UCI_GID_VENDOR_SPECIFIC_0x0E 0x0E
#define UCI_GID_VENDOR_SPECIFIC_0x0F 0x0F

/* OID: Opcode Identifier (byte 1) */
#define UCI_OID_MASK 0x3F
#define UCI_OID_SHIFT 0x00

/* builds byte0 of UCI Command and Notification packet */
#define UCI_MSG_BLD_HDR0(p, mt, gid) \
  *(p)++ = (uint8_t)(((mt) << UCI_MT_SHIFT) | (gid));

#define UCI_MSG_PBLD_HDR0(p, mt, pbf, gid) \
  *(p)++ = (uint8_t)(((mt) << UCI_MT_SHIFT) | ((pbf) << UCI_PBF_SHIFT) | (gid));

/* builds byte1 of UCI Command and Notification packet */
#define UCI_MSG_BLD_HDR1(p, oid) *(p)++ = (uint8_t)(((oid) << UCI_OID_SHIFT));

/* parse byte0 of UCI packet */
#define UCI_MSG_PRS_HDR0(p, mt, pbf, gid)     \
  mt = (*(p)&UCI_MT_MASK) >> UCI_MT_SHIFT;    \
  pbf = (*(p)&UCI_PBF_MASK) >> UCI_PBF_SHIFT; \
  gid = *(p)++ & UCI_GID_MASK;

/* parse MT and PBF bits of UCI packet */
#define UCI_MSG_PRS_MT_PBF(p, mt, pbf)     \
  mt = (*(p)&UCI_MT_MASK) >> UCI_MT_SHIFT; \
  pbf = (*(p)&UCI_PBF_MASK) >> UCI_PBF_SHIFT;

/* parse byte1 of UCI Cmd/Ntf */
#define UCI_MSG_PRS_HDR1(p, oid) \
  oid = (*(p)&UCI_OID_MASK);     \
  (p)++;

/* Allocate smallest possible buffer (for platforms with limited RAM) */
#define UCI_GET_CMD_BUF(paramlen)                                          \
  ((UWB_HDR*)phUwb_GKI_getbuf((uint16_t)(UWB_HDR_SIZE + UCI_MSG_HDR_SIZE + \
                                         UCI_MSG_OFFSET_SIZE + (paramlen))))

/**********************************************
 * UCI Core Group-0: Opcodes and size of commands
 **********************************************/
#define UCI_MSG_CORE_DEVICE_RESET 0x00
#define UCI_MSG_CORE_DEVICE_STATUS_NTF 0x01
#define UCI_MSG_CORE_DEVICE_INFO 0x02
#define UCI_MSG_CORE_GET_CAPS_INFO 0x03
#define UCI_MSG_CORE_SET_CONFIG 0x04
#define UCI_MSG_CORE_GET_CONFIG 0x05
#define UCI_MSG_CORE_DEVICE_SUSPEND 0x06
#define UCI_MSG_CORE_GENERIC_ERROR_NTF 0x07

#define UCI_MSG_CORE_DEVICE_RESET_CMD_SIZE 0x01
#define UCI_MSG_CORE_DEVICE_INFO_CMD_SIZE 0x00
#define UCI_MSG_CORE_GET_CAPS_INFO_CMD_SIZE 0x00

/*********************************************************
 * UCI session config Group-2: Opcodes and size of command
 ********************************************************/
#define UCI_MSG_SESSION_INIT 0x00
#define UCI_MSG_SESSION_DEINIT 0x01
#define UCI_MSG_SESSION_STATUS_NTF 0x02
#define UCI_MSG_SESSION_SET_APP_CONFIG 0x03
#define UCI_MSG_SESSION_GET_APP_CONFIG 0x04
#define UCI_MSG_SESSION_GET_COUNT 0x05
#define UCI_MSG_SESSION_GET_STATE 0x06
#define UCI_MSG_SESSION_UPDATE_CONTROLLER_MULTICAST_LIST 0x07

/* Pay load size for each command*/
#define UCI_MSG_SESSION_INIT_CMD_SIZE 0x05
#define UCI_MSG_SESSION_DEINIT_CMD_SIZE 0x04
#define UCI_MSG_SESSION_STATUS_NTF_LEN 0x06
#define UCI_MSG_SESSION_GET_COUNT_CMD_SIZE 0x00
#define UCI_MSG_SESSION_GET_STATE_SIZE 0x04

/*********************************************************
 * UWB Ranging Control Group-3: Opcodes and size of command
 *********************************************************/
#define UCI_MSG_RANGE_START 0x00
#define UCI_MSG_RANGE_STOP 0x01
#define UCI_MSG_RANGE_GET_RANGING_COUNT 0x03
#define UCI_MSG_RANGE_BLINK_DATA_TX 0x04

#define UCI_MSG_RANGE_DATA_NTF 0x00
#define UCI_MSG_RANGE_BLINK_DATA_TX_NTF 0x04

#define UCI_MSG_RANGE_START_CMD_SIZE 0x04
#define UCI_MSG_RANGE_STOP_CMD_SIZE 0x04
#define UCI_MSG_RANGE_GET_COUNT_CMD_SIZE 0x04

/**********************************************
 * UCI Android Vendor Group-E: Opcodes and size of commands
 **********************************************/
#define UCI_MSG_ANDROID_GET_POWER_STATS 0x00
#define UCI_MSG_ANDROID_SET_COUNTRY_CODE 0x01

#define UCI_MSG_ANDROID_SET_COUNTRY_CODE_CMD_SIZE COUNTRY_CODE_ARRAY_LEN

/**********************************************
 * UCI Parameter IDs : Device Configurations
 **********************************************/
#define UCI_PARAM_ID_DEVICE_STATE 0x00
#define UCI_PARAM_ID_LOW_POWER_MODE 0x01

/* UCI Parameter ID Length */
#define UCI_PARAM_LEN_DEVICE_STATE 0x01
#define UCI_PARAM_LEN_LOW_POWER_MODE 0x01

/*************************************************
 * UCI Parameter IDs : Application Configurations
 ************************************************/
#define UCI_PARAM_ID_DEVICE_TYPE 0x00
#define UCI_PARAM_ID_RANGING_ROUND_USAGE 0x01
#define UCI_PARAM_ID_STS_CONFIG 0x02
#define UCI_PARAM_ID_MULTI_NODE_MODE 0x03
#define UCI_PARAM_ID_CHANNEL_NUMBER 0x04
#define UCI_PARAM_ID_NO_OF_CONTROLEE 0x05
#define UCI_PARAM_ID_DEVICE_MAC_ADDRESS 0x06
#define UCI_PARAM_ID_DST_MAC_ADDRESS 0x07
#define UCI_PARAM_ID_SLOT_DURATION 0x08
#define UCI_PARAM_ID_RANGING_INTERVAL 0x09
#define UCI_PARAM_ID_STS_INDEX 0x0A
#define UCI_PARAM_ID_MAC_FCS_TYPE 0x0B
#define UCI_PARAM_ID_RANGING_ROUND_CONTROL 0x0C
#define UCI_PARAM_ID_AOA_RESULT_REQ 0x0D
#define UCI_PARAM_ID_RNG_DATA_NTF 0x0E
#define UCI_PARAM_ID_RNG_DATA_NTF_PROXIMITY_NEAR 0x0F
#define UCI_PARAM_ID_RNG_DATA_NTF_PROXIMITY_FAR 0x10
#define UCI_PARAM_ID_DEVICE_ROLE 0x11
#define UCI_PARAM_ID_RFRAME_CONFIG 0x12
#define UCI_PARAM_ID_PREAMBLE_CODE_INDEX 0x14
#define UCI_PARAM_ID_SFD_ID 0x15
#define UCI_PARAM_ID_PSDU_DATA_RATE 0x16
#define UCI_PARAM_ID_PREAMBLE_DURATION 0x17
#define UCI_PARAM_ID_RANGING_TIME_STRUCT 0x1A
#define UCI_PARAM_ID_SLOTS_PER_RR 0x1B
#define UCI_PARAM_ID_TX_ADAPTIVE_PAYLOAD_POWER 0x1C
#define UCI_PARAM_ID_RESPONDER_SLOT_INDEX 0x1E
#define UCI_PARAM_ID_PRF_MODE 0x1F
#define UCI_PARAM_ID_SCHEDULED_MODE 0x22
#define UCI_PARAM_ID_KEY_ROTATION 0x23
#define UCI_PARAM_ID_KEY_ROTATION_RATE 0x24
#define UCI_PARAM_ID_SESSION_PRIORITY 0x25
#define UCI_PARAM_ID_MAC_ADDRESS_MODE 0x26
#define UCI_PARAM_ID_VENDOR_ID 0x27
#define UCI_PARAM_ID_STATIC_STS_IV 0x28
#define UCI_PARAM_ID_NUMBER_OF_STS_SEGMENTS 0x29
#define UCI_PARAM_ID_MAX_RR_RETRY 0x2A
#define UCI_PARAM_ID_UWB_INITIATION_TIME 0x2B
#define UCI_PARAM_ID_HOPPING_MODE 0x2C
#define UCI_PARAM_ID_BLOCK_STRIDE_LENGTH 0x2D
#define UCI_PARAM_ID_RESULT_REPORT_CONFIG 0x2E
#define UCI_PARAM_ID_IN_BAND_TERMINATION_ATTEMPT_COUNT 0x2F
#define UCI_PARAM_ID_SUB_SESSION_ID 0x30
#define UCI_PARAM_ID_BPRF_PHR_DATA_RATE 0x31
#define UCI_PARAM_ID_MAX_NUMBER_OF_MEASUREMENTS 0x32

/* UCI Parameter ID Length */
#define UCI_PARAM_LEN_DEVICE_ROLE 0x01
#define UCI_PARAM_LEN_RANGING_ROUND_USAGE 0x01
#define UCI_PARAM_LEN_STS_CONFIG 0x01
#define UCI_PARAM_LEN_MULTI_NODE_MODE 0x01
#define UCI_PARAM_LEN_CHANNEL_NUMBER 0x01
#define UCI_PARAM_LEN_NO_OF_CONTROLEE 0x01
#define UCI_PARAM_LEN_DEVICE_MAC_ADDRESS 0x02
#define UCI_PARAM_LEN_DEST_MAC_ADDRESS 0x02
#define UCI_PARAM_LEN_SLOT_DURATION 0x02
#define UCI_PARAM_LEN_RANGING_INTERVAL 0x02
#define UCI_PARAM_LEN_STS_INDEX 0x01
#define UCI_PARAM_LEN_MAC_FCS_TYPE 0x01
#define UCI_PARAM_LEN_RANGING_ROUND_CONTROL 0x01
#define UCI_PARAM_LEN_AOA_RESULT_REQ 0x01
#define UCI_PARAM_LEN_RNG_DATA_NTF 0x01
#define UCI_PARAM_LEN_RNG_DATA_NTF_PROXIMITY_NEAR 0x02
#define UCI_PARAM_LEN_RNG_DATA_NTF_PROXIMITY_FAR 0x02
#define UCI_PARAM_LEN_DEVICE_TYPE 0x01
#define UCI_PARAM_LEN_RFRAME_CONFIG 0x01
#define UCI_PARAM_LEN_RX_MODE 0x01
#define UCI_PARAM_LEN_PREAMBLE_CODE_INDEX 0x01
#define UCI_PARAM_LEN_SFD_ID 0x01
#define UCI_PARAM_LEN_PSDU_DATA_RATE 0x01
#define UCI_PARAM_LEN_PREAMPLE_DURATION 0x01
#define UCI_PARAM_LEN_ANTENA_PAIR_SELECTION 0x01
#define UCI_PARAM_LEN_MAC_CFG 0x01
#define UCI_PARAM_LEN_RANGING_TIME_STRUCT 0x01
#define UCI_PARAM_LEN_SLOTS_PER_RR 0x01
#define UCI_PARAM_LEN_TX_POWER_ID 0x01
#define UCI_PARAM_LEN_TX_ADAPTIVE_PAYLOAD_POWER 0x01
#define UCI_PARAM_LEN_VENDOR_ID 0x02
#define UCI_PARAM_LEN_STATIC_STS_IV 0x06
#define UCI_PARAM_LEN_NUMBER_OF_STS_SEGMENTS 0x01
#define UCI_PARAM_LEN_MAX_RR_RETRY 0x02
#define UCI_PARAM_LEN_UWB_INITIATION_TIME 0x04
#define UCI_PARAM_LEN_HOPPING_MODE 0x01
#define UCI_PARAM_LEN_RESULT_REPORT_CONFIG 0x01
#define UCI_PARAM_LEN_IN_BAND_TERMINATION_ATTEMPT_COUNT 0x01
#define UCI_PARAM_LEN_SUB_SESSION_ID 0x04
#define UCI_PARAM_LEN_BLOCK_STRIDE_LENGTH 0x01

#define MAX_VENDOR_INFO_LENGTH   1000 // vendor specific info of rangedata max length considering 24 measures for TDOA

/*************************************************
 * Status codes
 ************************************************/
/* Generic Status Codes */
#define UCI_STATUS_OK 0x00
#define UCI_STATUS_REJECTED 0x01
#define UCI_STATUS_FAILED 0x02
#define UCI_STATUS_SYNTAX_ERROR 0x03
#define UCI_STATUS_INVALID_PARAM 0x04
#define UCI_STATUS_INVALID_RANGE 0x05
#define UCI_STATUS_INVALID_MSG_SIZE 0x06
#define UCI_STATUS_UNKNOWN_GID 0x07
#define UCI_STATUS_UNKNOWN_OID 0x08
#define UCI_STATUS_READ_ONLY 0x09
#define UCI_STATUS_COMMAND_RETRY 0x0A

/* UWB Session Specific Status Codes*/
#define UCI_STATUS_SESSSION_NOT_EXIST 0x11
#define UCI_STATUS_SESSSION_DUPLICATE 0x12
#define UCI_STATUS_SESSSION_ACTIVE 0x13
#define UCI_STATUS_MAX_SESSSIONS_EXCEEDED 0x14
#define UCI_STATUS_SESSION_NOT_CONFIGURED 0x15

/* UWB Ranging Session Specific Status Codes */
#define UCI_STATUS_RANGING_TX_FAILED 0x20
#define UCI_STATUS_RANGING_RX_TIMEOUT 0x21
#define UCI_STATUS_RANGING_RX_PHY_DEC_FAILED 0x22
#define UCI_STATUS_RANGING_RX_PHY_TOA_FAILED 0x23
#define UCI_STATUS_RANGING_RX_PHY_STS_FAILED 0x24
#define UCI_STATUS_RANGING_RX_MAC_DEC_FAILED 0x25
#define UCI_STATUS_RANGING_RX_MAC_IE_DEC_FAILED 0x26
#define UCI_STATUS_RANGING_RX_MAC_IE_MISSING 0x27

/* UWB Data Session Specific Status Codes */
#define UCI_STATUS_DATA_MAX_TX_PSDU_SIZE_EXCEEDED 0x30
#define UCI_STATUS_DATA_RX_CRC_ERROR 0x31

/*************************************************
 * Device Role config
 **************************************************/
#define UWB_CONTROLLER 0x00
#define UWB_CONTROLEE 0x01

/*************************************************
 * Ranging Method config
 **************************************************/
#define ONE_WAY_RANGING 0x00
#define SS_TWR_RANGING 0x01
#define DS_TWR_RANGING 0x02

/*************************************************
 * Ranging Mesaurement type
 **************************************************/
#define MEASUREMENT_TYPE_ONEWAY 0x00
#define MEASUREMENT_TYPE_TWOWAY 0x01

/*************************************************
 * Mac Addressing Mode Indicator
 **************************************************/
#define SHORT_MAC_ADDRESS 0x00
#define EXTENDED_MAC_ADDRESS 0x01
#define EXTENDED_MAC_ADDRESS_AND_HEADER 0x02

#define SESSION_ID_LEN 0x04
#define SHORT_ADDRESS_LEN 0x02
#define EXTENDED_ADDRESS_LEN 0x08
#define MAX_NUM_OF_TDOA_MEASURES 24
#define MAX_NUM_RESPONDERS \
  12  // max number of responders for contention based raning
#define MAX_NUM_CONTROLLEES \
  8  // max bumber of controlees for  time schedules rangng ( multicast)
#define COUNTRY_CODE_ARRAY_LEN 2

/* device status */
typedef enum {
  UWBS_STATUS_READY = 0x01,   /* UWBS is ready for  performing uwb session with
                                 non SE use cases */
  UWBS_STATUS_ACTIVE,         /* UWBS is busy running uwb session */
  UWBS_STATUS_TIMEOUT = 0xFE, /* To notify timeout to UWB service layer */
  UWBS_STATUS_ERROR = 0xFF    /* error occured in UWBS*/
} eUWBS_DEVICE_STATUS_t;

/* Session status */
typedef enum {
  UWB_SESSION_INITIALIZED,
  UWB_SESSION_DEINITIALIZED,
  UWB_SESSION_ACTIVE,
  UWB_SESSION_IDLE,
  UWB_UNKNOWN_SESSION = 0xFF
} eSESSION_STATUS_t;

/* Session status idle reason code */
typedef enum {
  UWB_SESSION_STATE_CHANGED = 0x00,
  UWB_SESSION_MAX_RR_RETRY_COUNT_REACHED = 0x01,
  UWB_SESSION_MAX_RANGING_MEASUREMENTS_REACHED = 0x02,
  UWB_SESSION_SLOT_LENTGH_NOT_SUPPORTED = 0x20,
  UWB_SESSION_SLOTS_PER_RR_NOT_SUFFICIENT = 0x21,
  UWB_SESSION_MAC_ADDRESS_MODE_NOT_SUPPORTED = 0x22,
  UWB_SESSION_INVALID_RANGING_INTERVAL = 0x23,
  UWB_SESSION_INVALID_STS_CONFIG = 0x24,
  UWB_SESSION_INVALID_RFRAME_CONFIG = 0x25
} eSESSION_STATUS_REASON_CODES_t;

#endif /* UWB_UCI_DEFS_H */

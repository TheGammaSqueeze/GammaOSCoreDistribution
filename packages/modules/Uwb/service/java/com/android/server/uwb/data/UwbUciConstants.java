/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.uwb.data;

import static android.hardware.uwb.fira_android.UwbVendorSessionInitSessionType.CCC;
import static android.hardware.uwb.fira_android.UwbVendorStatusCodes.STATUS_ERROR_CCC_LIFECYCLE;
import static android.hardware.uwb.fira_android.UwbVendorStatusCodes.STATUS_ERROR_CCC_SE_BUSY;

import com.google.uwb.support.fira.FiraParams;

public class UwbUciConstants {
    /**
     * Table 10:Device State Values
     */
    public static final byte DEVICE_STATE_OFF = 0x00; //NOT defined in the UCI spec
    public static final byte DEVICE_STATE_READY = 0x01;
    public static final byte DEVICE_STATE_ACTIVE = 0x02;
    public static final byte DEVICE_STATE_ERROR = (byte) 0xFF;

    public static final byte UWBS_RESET = 0x00;

    /**
     * Table 13: Control Messages to Initialize UWB session
     */
    public static final byte SESSION_TYPE_RANGING = 0x00;
    public static final byte SESSION_TYPE_DATA_TRANSFER = 0x01;
    public static final byte SESSION_TYPE_CCC = (byte) CCC;
    public static final byte SESSION_TYPE_DEVICE_TEST_MODE = (byte) 0xD0;

    /**
     * Table 14: Control Messages to De-Initialize UWB session - SESSION_STATUS_NTF
     * RangingSession.State
     */
    public static final int UWB_SESSION_STATE_INIT = 0x00;
    public static final int UWB_SESSION_STATE_DEINIT = 0x01;
    public static final int UWB_SESSION_STATE_ACTIVE = 0x02;
    public static final int UWB_SESSION_STATE_IDLE = 0x03;
    public static final int UWB_SESSION_STATE_ERROR = 0xFF;

    /**
     * Table 15: state change with reason codes
     */
    public static final int REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS = 0x00;
    /* Below reason codes shall be reported with SESSION_STATE_IDLE state only. */
    public static final int REASON_MAX_RANGING_ROUND_RETRY_COUNT_REACHED = 0x01;
    public static final int REASON_MAX_NUMBER_OF_MEASUREMENTS_REACHED = 0x02;
    public static final int REASON_ERROR_SLOT_LENGTH_NOT_SUPPORTED = 0x20;
    public static final int REASON_ERROR_INSUFFICIENT_SLOTS_PER_RR = 0x21;
    public static final int REASON_ERROR_MAC_ADDRESS_MODE_NOT_SUPPORTED = 0x22;
    public static final int REASON_ERROR_INVALID_RANGING_INTERVAL = 0x23;
    public static final int REASON_ERROR_INVALID_STS_CONFIG = 0x24;
    public static final int REASON_ERROR_INVALID_RFRAME_CONFIG = 0x25;

    /**
     * Table 27: Multicast list update status codes
     */
    /* Multicast update status codes */
    public static final int MULTICAST_LIST_UPDATE_STATUS_OK = 0x00;
    public static final int MULTICAST_LIST_UPDATE_STATUS_ERROR_FULL = 0x01;
    public static final int MULTICAST_LIST_UPDATE_STATUS_ERROR_KEY_FETCH_FAIL = 0x02;
    public static final int MULTICAST_LIST_UPDATE_STATUS_ERROR_SUB_SESSION_ID_NOT_FOUND = 0x03;

    /**
     * Table 29:APP Configuration Parameters IDs
     */
    public static final int DEVICE_TYPE_CONTROLEE = FiraParams.RANGING_DEVICE_TYPE_CONTROLEE;
    public static final int DEVICE_TYPE_CONTROLLER = FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;

    public static final int ROUND_USAGE_SS_TWR_DEFERRED_MODE =
            FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
    public static final int ROUND_USAGE_DS_TWR_DEFERRED_MODE =
            FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
    public static final int ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE =
            FiraParams.RANGING_ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE;
    public static final int ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE =
            FiraParams.RANGING_ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE;

    public static final int MULTI_NODE_MODE_UNICAST = FiraParams.MULTI_NODE_MODE_UNICAST;
    public static final int MULTI_NODE_MODE_ONE_TO_MANY = FiraParams.MULTI_NODE_MODE_ONE_TO_MANY;
    public static final int MULTI_NODE_MODE_MANY_TO_MANY = FiraParams.MULTI_NODE_MODE_MANY_TO_MANY;

    public static final int CHANNEL_5 = FiraParams.UWB_CHANNEL_5;
    public static final int CHANNEL_6 = FiraParams.UWB_CHANNEL_6;
    public static final int CHANNEL_8 = FiraParams.UWB_CHANNEL_8;
    public static final int CHANNEL_9 = FiraParams.UWB_CHANNEL_9;
    public static final int CHANNEL_10 = FiraParams.UWB_CHANNEL_10;
    public static final int CHANNEL_12 = FiraParams.UWB_CHANNEL_12;
    public static final int CHANNEL_13 = FiraParams.UWB_CHANNEL_13;
    public static final int CHANNEL_14 = FiraParams.UWB_CHANNEL_14;

    public static final int MAC_FCS_TYPE_CRC_16 = FiraParams.MAC_FCS_TYPE_CRC_16;
    public static final int MAC_FCS_TYPE_CRC_32 = FiraParams.MAC_FCS_TYPE_CRC_32;

    public static final int AOA_RESULT_REQ_DISABLE = 0x00;
    public static final int AOA_RESULT_REQ_ENABLE = 0x01;

    public static final int RANGE_DATA_NTF_CONFIG_DISABLE =
            FiraParams.RANGE_DATA_NTF_CONFIG_DISABLE;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE = FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY =
            FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY;

    public static final int RANGING_DEVICE_ROLE_RESPONDER =
            FiraParams.RANGING_DEVICE_ROLE_RESPONDER;
    public static final int RANGING_DEVICE_ROLE_INITIATOR =
            FiraParams.RANGING_DEVICE_ROLE_INITIATOR;

    public static final byte RANGING_MEASUREMENT_TYPE_TWO_WAY = 0X01;

    /**
     * Table 32: Status Codes
     */
    /* Generic Status Codes */
    public static final int STATUS_CODE_OK = FiraParams.STATUS_CODE_OK;
    public static final int STATUS_CODE_REJECTED = FiraParams.STATUS_CODE_REJECTED;
    public static final int STATUS_CODE_FAILED = FiraParams.STATUS_CODE_FAILED;
    public static final int STATUS_CODE_SYNTAX_ERROR = FiraParams.STATUS_CODE_SYNTAX_ERROR;
    public static final int STATUS_CODE_INVALID_PARAM = FiraParams.STATUS_CODE_INVALID_PARAM;
    public static final int STATUS_CODE_INVALID_RANGE = FiraParams.STATUS_CODE_INVALID_RANGE;
    public static final int STATUS_CODE_INVALID_MESSAGE_SIZE =
            FiraParams.STATUS_CODE_INVALID_MESSAGE_SIZE;
    public static final int STATUS_CODE_UNKNOWN_GID = FiraParams.STATUS_CODE_UNKNOWN_GID;
    public static final int STATUS_CODE_UNKNOWN_OID = FiraParams.STATUS_CODE_UNKNOWN_OID;
    public static final int STATUS_CODE_READ_ONLY = FiraParams.STATUS_CODE_READ_ONLY;
    public static final int STATUS_CODE_COMMAND_RETRY = FiraParams.STATUS_CODE_COMMAND_RETRY;
    /* UWB Session Specific Status Codes */
    public static final int STATUS_CODE_ERROR_SESSION_NOT_EXIST =
            FiraParams.STATUS_CODE_ERROR_SESSION_NOT_EXIST;
    public static final int STATUS_CODE_ERROR_SESSION_DUPLICATE =
            FiraParams.STATUS_CODE_ERROR_SESSION_DUPLICATE;
    public static final int STATUS_CODE_ERROR_SESSION_ACTIVE =
            FiraParams.STATUS_CODE_ERROR_SESSION_ACTIVE;
    public static final int STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED =
            FiraParams.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED;
    public static final int STATUS_CODE_ERROR_SESSION_NOT_CONFIGURED =
            FiraParams.STATUS_CODE_ERROR_SESSION_NOT_CONFIGURED;
    public static final int STATUS_CODE_ERROR_ACTIVE_SESSIONS_ONGOING =
            FiraParams.STATUS_CODE_ERROR_ACTIVE_SESSIONS_ONGOING;
    public static final int STATUS_CODE_ERROR_MULTICAST_LIST_FULL =
            FiraParams.STATUS_CODE_ERROR_MULTICAST_LIST_FULL;
    public static final int STATUS_CODE_ERROR_ADDRESS_NOT_FOUND =
            FiraParams.STATUS_CODE_ERROR_ADDRESS_NOT_FOUND;
    public static final int STATUS_CODE_ERROR_ADDRESS_ALREADY_PRESENT =
            FiraParams.STATUS_CODE_ERROR_ADDRESS_ALREADY_PRESENT;
    /* UWB Ranging Session Specific Status Codes */
    public static final int STATUS_CODE_RANGING_TX_FAILED =
            FiraParams.STATUS_CODE_RANGING_TX_FAILED;
    public static final int STATUS_CODE_RANGING_RX_TIMEOUT =
            FiraParams.STATUS_CODE_RANGING_RX_TIMEOUT;
    public static final int STATUS_CODE_RANGING_RX_PHY_DEC_FAILED =
            FiraParams.STATUS_CODE_RANGING_RX_PHY_DEC_FAILED;
    public static final int STATUS_CODE_RANGING_RX_PHY_TOA_FAILED =
            FiraParams.STATUS_CODE_RANGING_RX_PHY_TOA_FAILED;
    public static final int STATUS_CODE_RANGING_RX_PHY_STS_FAILED =
            FiraParams.STATUS_CODE_RANGING_RX_PHY_STS_FAILED;
    public static final int STATUS_CODE_RANGING_RX_MAC_DEC_FAILED =
            FiraParams.STATUS_CODE_RANGING_RX_MAC_DEC_FAILED;
    public static final int STATUS_CODE_RANGING_RX_MAC_IE_DEC_FAILED =
            FiraParams.STATUS_CODE_RANGING_RX_MAC_IE_DEC_FAILED;
    public static final int STATUS_CODE_RANGING_RX_MAC_IE_MISSING =
            FiraParams.STATUS_CODE_RANGING_RX_MAC_IE_MISSING;

    public static final int STATUS_CODE_CCC_SE_BUSY = STATUS_ERROR_CCC_SE_BUSY;
    public static final int STATUS_CODE_CCC_LIFECYCLE = STATUS_ERROR_CCC_LIFECYCLE;

    /* UWB Data Session Specific Status Codes */
    public static final int STATUS_CODE_DATA_MAX_TX_APDU_SIZE_EXCEEDED = 0x30;
    public static final int STATUS_CODE_DATA_RX_CRC_ERROR = 0x31;

    /* UWB STS Mode Codes */
    public static final int STS_MODE_STATIC = 0x00;
    public static final int STS_MODE_DYNAMIC = 0x01;
}
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

package com.google.uwb.support.fira;

import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.uwb.UwbAddress;

import androidx.annotation.IntDef;
import androidx.annotation.RequiresApi;

import com.google.uwb.support.base.FlagEnum;
import com.google.uwb.support.base.Params;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Defines parameters for FiRa operation */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public abstract class FiraParams extends Params {
    public static final String PROTOCOL_NAME = "fira";

    @Override
    public final String getProtocolName() {
        return PROTOCOL_NAME;
    }

    public static boolean isCorrectProtocol(PersistableBundle bundle) {
        return isProtocol(bundle, PROTOCOL_NAME);
    }

    public static final FiraProtocolVersion PROTOCOL_VERSION_1_1 = new FiraProtocolVersion(1, 1);

    /** Service ID for FiRa profile */
    @IntDef(
            value = {
                    PACS_PROFILE_SERVICE_ID,
            })
    public @interface ServiceID {}

    public static final int PACS_PROFILE_SERVICE_ID = 1;

    /** UWB Channel selections */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                UWB_CHANNEL_5,
                UWB_CHANNEL_6,
                UWB_CHANNEL_8,
                UWB_CHANNEL_9,
                UWB_CHANNEL_10,
                UWB_CHANNEL_12,
                UWB_CHANNEL_13,
                UWB_CHANNEL_14,
            })
    public @interface UwbChannel {}

    public static final int UWB_CHANNEL_5 = 5;
    public static final int UWB_CHANNEL_6 = 6;
    public static final int UWB_CHANNEL_8 = 8;
    public static final int UWB_CHANNEL_9 = 9;
    public static final int UWB_CHANNEL_10 = 10;
    public static final int UWB_CHANNEL_12 = 12;
    public static final int UWB_CHANNEL_13 = 13;
    public static final int UWB_CHANNEL_14 = 14;

    /** UWB Channel selections */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                UWB_PREAMBLE_CODE_INDEX_9,
                UWB_PREAMBLE_CODE_INDEX_10,
                UWB_PREAMBLE_CODE_INDEX_11,
                UWB_PREAMBLE_CODE_INDEX_12,
                UWB_PREAMBLE_CODE_INDEX_25,
                UWB_PREAMBLE_CODE_INDEX_26,
                UWB_PREAMBLE_CODE_INDEX_27,
                UWB_PREAMBLE_CODE_INDEX_28,
                UWB_PREAMBLE_CODE_INDEX_29,
                UWB_PREAMBLE_CODE_INDEX_30,
                UWB_PREAMBLE_CODE_INDEX_31,
                UWB_PREAMBLE_CODE_INDEX_32,
            })
    public @interface UwbPreambleCodeIndex {}

    public static final int UWB_PREAMBLE_CODE_INDEX_9 = 9;
    public static final int UWB_PREAMBLE_CODE_INDEX_10 = 10;
    public static final int UWB_PREAMBLE_CODE_INDEX_11 = 11;
    public static final int UWB_PREAMBLE_CODE_INDEX_12 = 12;
    public static final int UWB_PREAMBLE_CODE_INDEX_25 = 25;
    public static final int UWB_PREAMBLE_CODE_INDEX_26 = 26;
    public static final int UWB_PREAMBLE_CODE_INDEX_27 = 27;
    public static final int UWB_PREAMBLE_CODE_INDEX_28 = 28;
    public static final int UWB_PREAMBLE_CODE_INDEX_29 = 29;
    public static final int UWB_PREAMBLE_CODE_INDEX_30 = 30;
    public static final int UWB_PREAMBLE_CODE_INDEX_31 = 31;
    public static final int UWB_PREAMBLE_CODE_INDEX_32 = 32;

    /** Ranging frame type */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                RFRAME_CONFIG_SP0,
                RFRAME_CONFIG_SP1,
                RFRAME_CONFIG_SP3,
            })
    public @interface RframeConfig {}

    /** Ranging frame without STS */
    public static final int RFRAME_CONFIG_SP0 = 0;

    /** Ranging frame with STS following SFD */
    public static final int RFRAME_CONFIG_SP1 = 1;

    /** Ranging frame with STS following SFD, no data */
    public static final int RFRAME_CONFIG_SP3 = 3;

    /** Device type defined in FiRa */
    @IntDef(
            value = {
                RANGING_DEVICE_TYPE_CONTROLEE,
                RANGING_DEVICE_TYPE_CONTROLLER,
            })
    public @interface RangingDeviceType {}

    public static final int RANGING_DEVICE_TYPE_CONTROLEE = 0;

    public static final int RANGING_DEVICE_TYPE_CONTROLLER = 1;

    /** Device role defined in FiRa */
    @IntDef(
            value = {
                RANGING_DEVICE_ROLE_RESPONDER,
                RANGING_DEVICE_ROLE_INITIATOR,
            })
    public @interface RangingDeviceRole {}

    public static final int RANGING_DEVICE_ROLE_RESPONDER = 0;

    public static final int RANGING_DEVICE_ROLE_INITIATOR = 1;

    /** Ranging Round Usage */
    @IntDef(
            value = {
                RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE,
                RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE,
                RANGING_ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE,
                RANGING_ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE,
            })
    public @interface RangingRoundUsage {}

    /** Single-sided two-way ranging, deferred */
    public static final int RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE = 1;

    /** Double-sided two-way ranging, deferred */
    public static final int RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE = 2;

    /** Single-sided two-way ranging, non-deferred */
    public static final int RANGING_ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE = 3;

    /** Double-sided two-way ranging, non-deferred */
    public static final int RANGING_ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE = 4;

    /** Multi-Node mode */
    @IntDef(
            value = {
                MULTI_NODE_MODE_UNICAST,
                MULTI_NODE_MODE_ONE_TO_MANY,
                MULTI_NODE_MODE_MANY_TO_MANY,
            })
    public @interface MultiNodeMode {}

    public static final int MULTI_NODE_MODE_UNICAST = 0;

    public static final int MULTI_NODE_MODE_ONE_TO_MANY = 1;

    /** Unuported in Fira 1.1 */
    public static final int MULTI_NODE_MODE_MANY_TO_MANY = 2;

    /** Measurement Report */
    @IntDef(
            value = {
                MEASUREMENT_REPORT_TYPE_INITIATOR_TO_RESPONDER,
                MEASUREMENT_REPORT_TYPE_RESPONDER_TO_INITIATOR,
            })
    public @interface MeasurementReportType {}

    public static final int MEASUREMENT_REPORT_TYPE_INITIATOR_TO_RESPONDER = 0;

    public static final int MEASUREMENT_REPORT_TYPE_RESPONDER_TO_INITIATOR = 1;

    /** PRF Mode */
    @IntDef(
            value = {
                PRF_MODE_BPRF,
                PRF_MODE_HPRF,
            })
    public @interface PrfMode {}

    public static final int PRF_MODE_BPRF = 0;

    public static final int PRF_MODE_HPRF = 1;

    /** Preamble duration: BPRF always uses 64 symbols */
    @IntDef(
            value = {
                PREAMBLE_DURATION_T32_SYMBOLS,
                PREAMBLE_DURATION_T64_SYMBOLS,
            })
    public @interface PreambleDuration {}

    /** HPRF only */
    public static final int PREAMBLE_DURATION_T32_SYMBOLS = 0;

    public static final int PREAMBLE_DURATION_T64_SYMBOLS = 1;

    /** PSDU data Rate */
    @IntDef(
            value = {
                PSDU_DATA_RATE_6M81,
                PSDU_DATA_RATE_7M80,
                PSDU_DATA_RATE_27M2,
                PSDU_DATA_RATE_31M2,
            })
    public @interface PsduDataRate {}

    /** 6.81 Mbps, default BPRF rate */
    public static final int PSDU_DATA_RATE_6M81 = 0;

    /** 7.80 Mbps, BPRF rate with convolutional encoding K = 7 */
    public static final int PSDU_DATA_RATE_7M80 = 1;

    /** 27.2 Mbps, default HPRF rate */
    public static final int PSDU_DATA_RATE_27M2 = 2;

    /** 31.2 Mbps, HPRF rate with convolutional encoding K = 7 */
    public static final int PSDU_DATA_RATE_31M2 = 3;

    /** BPRF PHY Header data rate */
    @IntDef(
            value = {
                BPRF_PHR_DATA_RATE_850K,
                BPRF_PHR_DATA_RATE_6M81,
            })
    public @interface BprfPhrDataRate {}

    /** 850 kbps */
    public static final int BPRF_PHR_DATA_RATE_850K = 0;

    /** 6.81 Mbps */
    public static final int BPRF_PHR_DATA_RATE_6M81 = 1;

    /** MAC FCS type */
    @IntDef(
            value = {
                MAC_FCS_TYPE_CRC_16,
                MAC_FCS_TYPE_CRC_32,
            })
    public @interface MacFcsType {}

    public static final int MAC_FCS_TYPE_CRC_16 = 0;
    /** HPRF only */
    public static final int MAC_FCS_TYPE_CRC_32 = 1;

    /** STS Config */
    @IntDef(
            value = {
                STS_CONFIG_STATIC,
                STS_CONFIG_DYNAMIC,
                STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY,
            })
    public @interface StsConfig {}

    public static final int STS_CONFIG_STATIC = 0;

    public static final int STS_CONFIG_DYNAMIC = 1;

    public static final int STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY = 2;

    /** AoA request */
    @IntDef(
            value = {
                AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT,
                AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS,
                AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_AZIMUTH_ONLY,
                AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_ELEVATION_ONLY,
                AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED,
            })
    public @interface AoaResultRequestMode {}

    public static final int AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT = 0;

    public static final int AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS = 1;

    public static final int AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_AZIMUTH_ONLY = 2;

    public static final int AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_ELEVATION_ONLY = 3;

    public static final int AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED = 0xF0;

    /** STS Segment count */
    @IntDef(
            value = {
                STS_SEGMENT_COUNT_VALUE_0,
                STS_SEGMENT_COUNT_VALUE_1,
                STS_SEGMENT_COUNT_VALUE_2,
            })
    public @interface StsSegmentCountValue {}

    public static final int STS_SEGMENT_COUNT_VALUE_0 = 0;

    public static final int STS_SEGMENT_COUNT_VALUE_1 = 1;

    public static final int STS_SEGMENT_COUNT_VALUE_2 = 2;

    /** SFD ID */
    @IntDef(
            value = {
                SFD_ID_VALUE_1,
                SFD_ID_VALUE_2,
                SFD_ID_VALUE_3,
                SFD_ID_VALUE_4,
            })
    public @interface SfdIdValue {}

    public static final int SFD_ID_VALUE_1 = 1;
    public static final int SFD_ID_VALUE_2 = 2;
    public static final int SFD_ID_VALUE_3 = 3;
    public static final int SFD_ID_VALUE_4 = 4;

    /**
     * Hopping mode (Since FiRa supports vendor-specific values. This annotation is not enforced.)
     */
    @IntDef(
            value = {
                HOPPING_MODE_DISABLE,
                HOPPING_MODE_FIRA_HOPPING_ENABLE,
            })
    public @interface HoppingMode {}

    public static final int HOPPING_MODE_DISABLE = 0;
    public static final int HOPPING_MODE_FIRA_HOPPING_ENABLE = 1;

    /** STS Length */
    @IntDef(
            value = {
                STS_LENGTH_32_SYMBOLS,
                STS_LENGTH_64_SYMBOLS,
                STS_LENGTH_128_SYMBOLS,
            })
    public @interface StsLength {}

    public static final int STS_LENGTH_32_SYMBOLS = 0;
    public static final int STS_LENGTH_64_SYMBOLS = 1;
    public static final int STS_LENGTH_128_SYMBOLS = 2;

    /** Range Data Notification Config */
    @IntDef(
            value = {
                RANGE_DATA_NTF_CONFIG_DISABLE,
                RANGE_DATA_NTF_CONFIG_ENABLE,
                RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY,
            })
    public @interface RangeDataNtfConfig {}

    public static final int RANGE_DATA_NTF_CONFIG_DISABLE = 0;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE = 1;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY = 2;

    /** MAC address mode: short (2 bytes) or extended (8 bytes) */
    @IntDef(
            value = {
                MAC_ADDRESS_MODE_2_BYTES,
                MAC_ADDRESS_MODE_8_BYTES_2_BYTES_HEADER,
                MAC_ADDRESS_MODE_8_BYTES,
            })
    public @interface MacAddressMode {}

    public static final int MAC_ADDRESS_MODE_2_BYTES = 0;

    /** Not supported by UCI 1.0 */
    public static final int MAC_ADDRESS_MODE_8_BYTES_2_BYTES_HEADER = 1;

    public static final int MAC_ADDRESS_MODE_8_BYTES = 2;

    /** AoA type is not defined in UCI. This decides what AoA result we want to get */
    @IntDef(
            value = {
                AOA_TYPE_AZIMUTH,
                AOA_TYPE_ELEVATION,
                AOA_TYPE_AZIMUTH_AND_ELEVATION,
            })
    public @interface AoaType {}

    public static final int AOA_TYPE_AZIMUTH = 0;
    public static final int AOA_TYPE_ELEVATION = 1;

    /**
     * How to get both angles is hardware dependent. Some hardware can get both angle in one round,
     * some needs two rounds.
     */
    public static final int AOA_TYPE_AZIMUTH_AND_ELEVATION = 2;

    /** Status codes defined in UCI */
    @IntDef(
            value = {
                STATUS_CODE_OK,
                STATUS_CODE_REJECTED,
                STATUS_CODE_FAILED,
                STATUS_CODE_SYNTAX_ERROR,
                STATUS_CODE_INVALID_PARAM,
                STATUS_CODE_INVALID_RANGE,
                STATUS_CODE_INVALID_MESSAGE_SIZE,
                STATUS_CODE_UNKNOWN_GID,
                STATUS_CODE_UNKNOWN_OID,
                STATUS_CODE_READ_ONLY,
                STATUS_CODE_COMMAND_RETRY,
                STATUS_CODE_ERROR_SESSION_NOT_EXIST,
                STATUS_CODE_ERROR_SESSION_DUPLICATE,
                STATUS_CODE_ERROR_SESSION_ACTIVE,
                STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED,
                STATUS_CODE_ERROR_SESSION_NOT_CONFIGURED,
                STATUS_CODE_ERROR_ACTIVE_SESSIONS_ONGOING,
                STATUS_CODE_ERROR_MULTICAST_LIST_FULL,
                STATUS_CODE_ERROR_ADDRESS_NOT_FOUND,
                STATUS_CODE_ERROR_ADDRESS_ALREADY_PRESENT,
                STATUS_CODE_RANGING_TX_FAILED,
                STATUS_CODE_RANGING_RX_TIMEOUT,
                STATUS_CODE_RANGING_RX_PHY_DEC_FAILED,
                STATUS_CODE_RANGING_RX_PHY_TOA_FAILED,
                STATUS_CODE_RANGING_RX_PHY_STS_FAILED,
                STATUS_CODE_RANGING_RX_MAC_DEC_FAILED,
                STATUS_CODE_RANGING_RX_MAC_IE_DEC_FAILED,
                STATUS_CODE_RANGING_RX_MAC_IE_MISSING,
            })
    public @interface StatusCode {}

    public static final int STATUS_CODE_OK = 0x00;
    public static final int STATUS_CODE_REJECTED = 0x01;
    public static final int STATUS_CODE_FAILED = 0x02;
    public static final int STATUS_CODE_SYNTAX_ERROR = 0x03;
    public static final int STATUS_CODE_INVALID_PARAM = 0x04;
    public static final int STATUS_CODE_INVALID_RANGE = 0x05;
    public static final int STATUS_CODE_INVALID_MESSAGE_SIZE = 0x06;
    public static final int STATUS_CODE_UNKNOWN_GID = 0x07;
    public static final int STATUS_CODE_UNKNOWN_OID = 0x08;
    public static final int STATUS_CODE_READ_ONLY = 0x09;
    public static final int STATUS_CODE_COMMAND_RETRY = 0x0A;
    public static final int STATUS_CODE_ERROR_SESSION_NOT_EXIST = 0x11;
    public static final int STATUS_CODE_ERROR_SESSION_DUPLICATE = 0x12;
    public static final int STATUS_CODE_ERROR_SESSION_ACTIVE = 0x13;
    public static final int STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED = 0x14;
    public static final int STATUS_CODE_ERROR_SESSION_NOT_CONFIGURED = 0x15;
    public static final int STATUS_CODE_ERROR_ACTIVE_SESSIONS_ONGOING = 0x16;
    public static final int STATUS_CODE_ERROR_MULTICAST_LIST_FULL = 0x17;
    public static final int STATUS_CODE_ERROR_ADDRESS_NOT_FOUND = 0x18;
    public static final int STATUS_CODE_ERROR_ADDRESS_ALREADY_PRESENT = 0x19;
    public static final int STATUS_CODE_RANGING_TX_FAILED = 0x20;
    public static final int STATUS_CODE_RANGING_RX_TIMEOUT = 0x21;
    public static final int STATUS_CODE_RANGING_RX_PHY_DEC_FAILED = 0x22;
    public static final int STATUS_CODE_RANGING_RX_PHY_TOA_FAILED = 0x23;
    public static final int STATUS_CODE_RANGING_RX_PHY_STS_FAILED = 0x24;
    public static final int STATUS_CODE_RANGING_RX_MAC_DEC_FAILED = 0x25;
    public static final int STATUS_CODE_RANGING_RX_MAC_IE_DEC_FAILED = 0x26;
    public static final int STATUS_CODE_RANGING_RX_MAC_IE_MISSING = 0x27;

    /** State change reason codes defined in UCI table-15 */
    @IntDef(
            value = {
                STATE_CHANGE_REASON_CODE_BY_COMMANDS,
                STATE_CHANGE_REASON_CODE_MAX_RR_RETRY_REACHED,
                STATE_CHANGE_REASON_CODE_ERROR_SLOT_LENGTH_NOT_SUPPORTED,
                STATE_CHANGE_REASON_CODE_ERROR_INSUFFICIENT_SLOTS_PER_RR,
                STATE_CHANGE_REASON_CODE_ERROR_MAC_ADDRESS_MODE_NOT_SUPPORTED,
                STATE_CHANGE_REASON_CODE_ERROR_INVALID_RANGING_INTERVAL,
                STATE_CHANGE_REASON_CODE_ERROR_INVALID_STS_CONFIG,
                STATE_CHANGE_REASON_CODE_ERROR_INVALID_RFRAME_CONFIG,
            })
    public @interface StateChangeReasonCode {}

    public static final int STATE_CHANGE_REASON_CODE_BY_COMMANDS = 0;
    public static final int STATE_CHANGE_REASON_CODE_MAX_RR_RETRY_REACHED = 1;
    public static final int STATE_CHANGE_REASON_CODE_ERROR_SLOT_LENGTH_NOT_SUPPORTED = 0x20;
    public static final int STATE_CHANGE_REASON_CODE_ERROR_INSUFFICIENT_SLOTS_PER_RR = 0x21;
    public static final int STATE_CHANGE_REASON_CODE_ERROR_MAC_ADDRESS_MODE_NOT_SUPPORTED = 0x22;
    public static final int STATE_CHANGE_REASON_CODE_ERROR_INVALID_RANGING_INTERVAL = 0x23;
    public static final int STATE_CHANGE_REASON_CODE_ERROR_INVALID_STS_CONFIG = 0x24;
    public static final int STATE_CHANGE_REASON_CODE_ERROR_INVALID_RFRAME_CONFIG = 0x25;

    /** Multicast controlee add/delete actions defined in UCI */
    @IntDef(
            value = {
                MULTICAST_LIST_UPDATE_ACTION_ADD,
                MULTICAST_LIST_UPDATE_ACTION_DELETE,
            })
    public @interface MulticastListUpdateAction {}

    public static final int MULTICAST_LIST_UPDATE_ACTION_ADD = 0;
    public static final int MULTICAST_LIST_UPDATE_ACTION_DELETE = 1;

    @IntDef(
            value = {
                MULTICAST_LIST_UPDATE_STATUS_OK,
                MULTICAST_LIST_UPDATE_STATUS_ERROR_MULTICAST_LIST_FULL,
                MULTICAST_LIST_UPDATE_STATUS_ERROR_KEY_FETCH_FAIL,
                MULTICAST_LIST_UPDATE_STATUS_ERROR_SUB_SESSION_ID_NOT_FOUND,
            })
    public @interface MulticastListUpdateStatus {}

    public static final int MULTICAST_LIST_UPDATE_STATUS_OK = 0;
    public static final int MULTICAST_LIST_UPDATE_STATUS_ERROR_MULTICAST_LIST_FULL = 1;
    public static final int MULTICAST_LIST_UPDATE_STATUS_ERROR_KEY_FETCH_FAIL = 2;
    public static final int MULTICAST_LIST_UPDATE_STATUS_ERROR_SUB_SESSION_ID_NOT_FOUND = 3;

    /** Capability related definitions starts from here */
    @IntDef(
            value = {
                DEVICE_CLASS_1,
                DEVICE_CLASS_2,
                DEVICE_CLASS_3,
            })
    public @interface DeviceClass {}

    public static final int DEVICE_CLASS_1 = 1; // Controller & controlee
    public static final int DEVICE_CLASS_2 = 2; // Controller
    public static final int DEVICE_CLASS_3 = 3; // Controlee

    public enum AoaCapabilityFlag implements FlagEnum {
        HAS_AZIMUTH_SUPPORT(1),
        HAS_ELEVATION_SUPPORT(1 << 1),
        HAS_FOM_SUPPORT(1 << 2),
        HAS_FULL_AZIMUTH_SUPPORT(1 << 3),
        HAS_INTERLEAVING_SUPPORT(1 << 4);

        private final long mValue;

        private AoaCapabilityFlag(long value) {
            mValue = value;
        }

        @Override
        public long getValue() {
            return mValue;
        }
    }

    public enum DeviceRoleCapabilityFlag implements FlagEnum {
        HAS_CONTROLEE_INITIATOR_SUPPORT(1),
        HAS_CONTROLEE_RESPONDER_SUPPORT(1 << 1),
        HAS_CONTROLLER_INITIATOR_SUPPORT(1 << 2),
        HAS_CONTROLLER_RESPONDER_SUPPORT(1 << 3);

        private final long mValue;

        private DeviceRoleCapabilityFlag(long value) {
            mValue = value;
        }

        @Override
        public long getValue() {
            return mValue;
        }
    }

    public enum MultiNodeCapabilityFlag implements FlagEnum {
        HAS_UNICAST_SUPPORT(1),
        HAS_ONE_TO_MANY_SUPPORT(1 << 1),
        HAS_MANY_TO_MANY_SUPPORT(1 << 2);

        private final long mValue;

        private MultiNodeCapabilityFlag(long value) {
            mValue = value;
        }

        @Override
        public long getValue() {
            return mValue;
        }
    }

    public enum PrfCapabilityFlag implements FlagEnum {
        HAS_BPRF_SUPPORT(1),
        HAS_HPRF_SUPPORT(1 << 1);

        private final long mValue;

        private PrfCapabilityFlag(long value) {
            mValue = value;
        }

        @Override
        public long getValue() {
            return mValue;
        }
    }

    public enum RangingRoundCapabilityFlag implements FlagEnum {
        HAS_DS_TWR_SUPPORT(1),
        HAS_SS_TWR_SUPPORT(1 << 1);

        private final long mValue;

        private RangingRoundCapabilityFlag(long value) {
            mValue = value;
        }

        @Override
        public long getValue() {
            return mValue;
        }
    }

    public enum RframeCapabilityFlag implements FlagEnum {
        HAS_SP0_RFRAME_SUPPORT(1),
        HAS_SP1_RFRAME_SUPPORT(1 << 1),
        HAS_SP3_RFRAME_SUPPORT(1 << 3);

        private final long mValue;

        private RframeCapabilityFlag(long value) {
            mValue = value;
        }

        @Override
        public long getValue() {
            return mValue;
        }
    }

    public enum StsCapabilityFlag implements FlagEnum {
        HAS_STATIC_STS_SUPPORT(1),
        HAS_DYNAMIC_STS_SUPPORT(1 << 1),
        HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT(1 << 2);

        private final long mValue;

        private StsCapabilityFlag(long value) {
            mValue = value;
        }

        @Override
        public long getValue() {
            return mValue;
        }
    }

    public enum PsduDataRateCapabilityFlag implements FlagEnum {
        HAS_6M81_SUPPORT(1),
        HAS_7M80_SUPPORT(1 << 1),
        HAS_27M2_SUPPORT(1 << 2),
        HAS_31M2_SUPPORT(1 << 3);

        private final long mValue;

        private PsduDataRateCapabilityFlag(long value) {
            mValue = value;
        }

        @Override
        public long getValue() {
            return mValue;
        }
    }

    public enum BprfParameterSetCapabilityFlag implements FlagEnum {
        HAS_SET_1_SUPPORT(1),
        HAS_SET_2_SUPPORT(1 << 1),
        HAS_SET_3_SUPPORT(1 << 2),
        HAS_SET_4_SUPPORT(1 << 3),
        HAS_SET_5_SUPPORT(1 << 4),
        HAS_SET_6_SUPPORT(1 << 5);

        private final long mValue;

        private BprfParameterSetCapabilityFlag(long value) {
            mValue = value;
        }

        @Override
        public long getValue() {
            return mValue;
        }
    }

    public enum HprfParameterSetCapabilityFlag implements FlagEnum {
        HAS_SET_1_SUPPORT(1L),
        HAS_SET_2_SUPPORT(1L << 1),
        HAS_SET_3_SUPPORT(1L << 2),
        HAS_SET_4_SUPPORT(1L << 3),
        HAS_SET_5_SUPPORT(1L << 4),
        HAS_SET_6_SUPPORT(1L << 5),
        HAS_SET_7_SUPPORT(1L << 6),
        HAS_SET_8_SUPPORT(1L << 7),
        HAS_SET_9_SUPPORT(1L << 8),
        HAS_SET_10_SUPPORT(1L << 9),
        HAS_SET_11_SUPPORT(1L << 10),
        HAS_SET_12_SUPPORT(1L << 11),
        HAS_SET_13_SUPPORT(1L << 12),
        HAS_SET_14_SUPPORT(1L << 13),
        HAS_SET_15_SUPPORT(1L << 14),
        HAS_SET_16_SUPPORT(1L << 15),
        HAS_SET_17_SUPPORT(1L << 16),
        HAS_SET_18_SUPPORT(1L << 17),
        HAS_SET_19_SUPPORT(1L << 18),
        HAS_SET_20_SUPPORT(1L << 19),
        HAS_SET_21_SUPPORT(1L << 20),
        HAS_SET_22_SUPPORT(1L << 21),
        HAS_SET_23_SUPPORT(1L << 22),
        HAS_SET_24_SUPPORT(1L << 23),
        HAS_SET_25_SUPPORT(1L << 24),
        HAS_SET_26_SUPPORT(1L << 25),
        HAS_SET_27_SUPPORT(1L << 26),
        HAS_SET_28_SUPPORT(1L << 27),
        HAS_SET_29_SUPPORT(1L << 28),
        HAS_SET_30_SUPPORT(1L << 29),
        HAS_SET_31_SUPPORT(1L << 30),
        HAS_SET_32_SUPPORT(1L << 31),
        HAS_SET_33_SUPPORT(1L << 32),
        HAS_SET_34_SUPPORT(1L << 33),
        HAS_SET_35_SUPPORT(1L << 34);

        private final long mValue;

        private HprfParameterSetCapabilityFlag(long value) {
            mValue = value;
        }

        @Override
        public long getValue() {
            return mValue;
        }
    }

    // Helper functions
    protected static UwbAddress longToUwbAddress(long value, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        return UwbAddress.fromBytes(Arrays.copyOf(buffer.array(), length));
    }

    protected static long uwbAddressToLong(UwbAddress address) {
        ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOf(address.toBytes(), Long.BYTES));
        return buffer.getLong();
    }
}

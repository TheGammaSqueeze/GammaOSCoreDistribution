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

package com.android.server.uwb.config;

import android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes;
import android.hardware.uwb.fira_android.UwbVendorCapabilityTlvValues;

public class CapabilityParam {
    /**
     *  CR 287 params
     */
    public static final int SUPPORTED_FIRA_PHY_VERSION_RANGE = 0x0;
    public static final int SUPPORTED_FIRA_MAC_VERSION_RANGE = 0x1;
    public static final int SUPPORTED_DEVICE_ROLES = 0x2;
    public static final int SUPPORTED_RANGING_METHOD = 0x3;
    public static final int SUPPORTED_STS_CONFIG = 0x4;
    public static final int SUPPORTED_MULTI_NODE_MODES = 0x5;
    public static final int SUPPORTED_RANGING_TIME_STRUCT = 0x6;
    public static final int SUPPORTED_SCHEDULED_MODE = 0x7;
    public static final int SUPPORTED_HOPPING_MODE = 0x8;
    public static final int SUPPORTED_BLOCK_STRIDING = 0x9;
    public static final int SUPPORTED_UWB_INITIATION_TIME = 0x0A;
    public static final int SUPPORTED_CHANNELS = 0x0B;
    public static final int SUPPORTED_RFRAME_CONFIG = 0x0C;
    public static final int SUPPORTED_CC_CONSTRAINT_LENGTH = 0x0D;
    public static final int SUPPORTED_BPRF_PARAMETER_SETS = 0x0E;
    public static final int SUPPORTED_HPRF_PARAMETER_SETS = 0x0F;
    public static final int SUPPORTED_AOA = 0x10;
    public static final int SUPPORTED_EXTENDED_MAC_ADDRESS = 0x11;
    public static final int SUPPORTED_AOA_RESULT_REQ_INTERLEAVING =
            UwbVendorCapabilityTlvTypes.SUPPORTED_AOA_RESULT_REQ_ANTENNA_INTERLEAVING;

    // CCC specific
    public static final int CCC_SUPPORTED_VERSIONS =
             UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_VERSIONS;
    public static final int CCC_SUPPORTED_UWB_CONFIGS =
             UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_UWB_CONFIGS;
    public static final int CCC_SUPPORTED_PULSE_SHAPE_COMBOS =
             UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_PULSE_SHAPE_COMBOS;
    public static final int CCC_SUPPORTED_RAN_MULTIPLIER =
             UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_RAN_MULTIPLIER;
    public static final int CCC_SUPPORTED_CHAPS_PER_SLOT =
             UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_CHAPS_PER_SLOT;
    public static final int CCC_SUPPORTED_SYNC_CODES =
             UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_SYNC_CODES;
    public static final int CCC_SUPPORTED_CHANNELS =
             UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_CHANNELS;
    public static final int CCC_SUPPORTED_HOPPING_CONFIG_MODES_AND_SEQUENCES =
             UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_HOPPING_CONFIG_MODES_AND_SEQUENCES;

    public static final int RESPONDER = 0x01;
    public static final int INITIATOR = 0x02;

    public static final int OWR = 0x01;
    public static final int SS_TWR_DEFERRED = 0x02;
    public static final int DS_TWR_DEFERRED = 0x04;
    public static final int SS_TWR_NON_DEFERRED = 0x08;
    public static final int DS_TWR_NON_DEFERRED = 0x10;

    public static final int STATIC_STS = 0x1;
    public static final int DYNAMIC_STS = 0x2;
    public static final int DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY = 0x4;

    public static final int UNICAST = 0x1;
    public static final int ONE_TO_MANY = 0x2;
    public static final int MANY_TO_MANY = 0x4;

    public static final int NO_BLOCK_STRIDING = 0x0;
    public static final int BLOCK_STRIDING = 0x1;

    public static final int NO_UWB_INITIATION_TIME = 0x0;
    public static final int UWB_INITIATION_TIME = 0x1;

    public static final int CHANNEL_5 = 0x1;
    public static final int CHANNEL_6 = 0x2;
    public static final int CHANNEL_8 = 0x4;
    public static final int CHANNEL_9 = 0x8;
    public static final int CHANNEL_10 = 0x10;
    public static final int CHANNEL_12 = 0x20;
    public static final int CHANNEL_13 = 0x40;
    public static final int CHANNEL_14 = 0x80;

    public static final int SP0 = 0x1;
    public static final int SP1 = 0x2;
    public static final int SP2 = 0x4;
    public static final int SP3 = 0x8;

    public static final int CC_CONSTRAINT_LENGTH_K3 = 0x1;
    public static final int CC_CONSTRAINT_LENGTH_K7 = 0x2;

    public static final int AOA_AZIMUTH_90 = 0x1;
    public static final int AOA_AZIMUTH_180 = 0x2;
    public static final int AOA_ELEVATION = 0x4;
    public static final int AOA_FOM = 0x4;

    public static final int NO_EXTENDED_MAC = 0x0;
    public static final int EXTENDED_MAC = 0x1;

    public static final int NO_AOA_RESULT_REQ_INTERLEAVING = 0x0;
    public static final int AOA_RESULT_REQ_INTERLEAVING = 0x1;

    public static final int CCC_CHANNEL_5 = (int) UwbVendorCapabilityTlvValues.CCC_CHANNEL_5;
    public static final int CCC_CHANNEL_9 = (int) UwbVendorCapabilityTlvValues.CCC_CHANNEL_9;

    public static final int CCC_CHAPS_PER_SLOT_3 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_3;
    public static final int CCC_CHAPS_PER_SLOT_4 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_4;
    public static final int CCC_CHAPS_PER_SLOT_6 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_6;
    public static final int CCC_CHAPS_PER_SLOT_8 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_8;
    public static final int CCC_CHAPS_PER_SLOT_9 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_9;
    public static final int CCC_CHAPS_PER_SLOT_12 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_12;
    public static final int CCC_CHAPS_PER_SLOT_24 =
            (int) UwbVendorCapabilityTlvValues.CHAPS_PER_SLOT_24;

    public static final int CCC_HOPPING_CONFIG_MODE_NONE =
            (int) UwbVendorCapabilityTlvValues.HOPPING_CONFIG_MODE_NONE;
    public static final int CCC_HOPPING_CONFIG_MODE_CONTINUOUS =
            (int) UwbVendorCapabilityTlvValues.HOPPING_CONFIG_MODE_CONTINUOUS;
    public static final int CCC_HOPPING_CONFIG_MODE_ADAPTIVE =
            (int) UwbVendorCapabilityTlvValues.HOPPING_CONFIG_MODE_ADAPTIVE;

    public static final int CCC_HOPPING_SEQUENCE_AES =
            (int) UwbVendorCapabilityTlvValues.HOPPING_SEQUENCE_AES;
    public static final int CCC_HOPPING_SEQUENCE_DEFAULT =
            (int) UwbVendorCapabilityTlvValues.HOPPING_SEQUENCE_DEFAULT;

    public static final int SUPPORTED_POWER_STATS_QUERY =
            UwbVendorCapabilityTlvTypes.SUPPORTED_POWER_STATS_QUERY;
}

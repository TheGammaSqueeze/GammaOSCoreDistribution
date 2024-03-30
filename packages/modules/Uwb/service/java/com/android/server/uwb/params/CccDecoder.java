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

package com.android.server.uwb.params;

import static com.android.server.uwb.config.CapabilityParam.CCC_CHANNEL_5;
import static com.android.server.uwb.config.CapabilityParam.CCC_CHANNEL_9;
import static com.android.server.uwb.config.CapabilityParam.CCC_CHAPS_PER_SLOT_12;
import static com.android.server.uwb.config.CapabilityParam.CCC_CHAPS_PER_SLOT_24;
import static com.android.server.uwb.config.CapabilityParam.CCC_CHAPS_PER_SLOT_3;
import static com.android.server.uwb.config.CapabilityParam.CCC_CHAPS_PER_SLOT_4;
import static com.android.server.uwb.config.CapabilityParam.CCC_CHAPS_PER_SLOT_6;
import static com.android.server.uwb.config.CapabilityParam.CCC_CHAPS_PER_SLOT_8;
import static com.android.server.uwb.config.CapabilityParam.CCC_CHAPS_PER_SLOT_9;
import static com.android.server.uwb.config.CapabilityParam.CCC_HOPPING_CONFIG_MODE_ADAPTIVE;
import static com.android.server.uwb.config.CapabilityParam.CCC_HOPPING_CONFIG_MODE_CONTINUOUS;
import static com.android.server.uwb.config.CapabilityParam.CCC_HOPPING_CONFIG_MODE_NONE;
import static com.android.server.uwb.config.CapabilityParam.CCC_HOPPING_SEQUENCE_AES;
import static com.android.server.uwb.config.CapabilityParam.CCC_HOPPING_SEQUENCE_DEFAULT;
import static com.android.server.uwb.config.CapabilityParam.CCC_SUPPORTED_CHANNELS;
import static com.android.server.uwb.config.CapabilityParam.CCC_SUPPORTED_CHAPS_PER_SLOT;
import static com.android.server.uwb.config.CapabilityParam.CCC_SUPPORTED_HOPPING_CONFIG_MODES_AND_SEQUENCES;
import static com.android.server.uwb.config.CapabilityParam.CCC_SUPPORTED_PULSE_SHAPE_COMBOS;
import static com.android.server.uwb.config.CapabilityParam.CCC_SUPPORTED_RAN_MULTIPLIER;
import static com.android.server.uwb.config.CapabilityParam.CCC_SUPPORTED_SYNC_CODES;
import static com.android.server.uwb.config.CapabilityParam.CCC_SUPPORTED_UWB_CONFIGS;
import static com.android.server.uwb.config.CapabilityParam.CCC_SUPPORTED_VERSIONS;

import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_12;
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_24;
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_3;
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_4;
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_6;
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_8;
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_9;
import static com.google.uwb.support.ccc.CccParams.HOPPING_CONFIG_MODE_ADAPTIVE;
import static com.google.uwb.support.ccc.CccParams.HOPPING_CONFIG_MODE_CONTINUOUS;
import static com.google.uwb.support.ccc.CccParams.HOPPING_CONFIG_MODE_NONE;
import static com.google.uwb.support.ccc.CccParams.HOPPING_SEQUENCE_AES;
import static com.google.uwb.support.ccc.CccParams.HOPPING_SEQUENCE_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.UWB_CHANNEL_5;
import static com.google.uwb.support.ccc.CccParams.UWB_CHANNEL_9;

import com.android.server.uwb.config.ConfigParam;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccProtocolVersion;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccRangingStartedParams;
import com.google.uwb.support.ccc.CccSpecificationParams;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * CCC decoder
 */
public class CccDecoder extends TlvDecoder {
    @Override
    public <T extends Params> T getParams(TlvDecoderBuffer tlvs, Class<T> paramsType)
            throws IllegalArgumentException {
        if (CccRangingStartedParams.class.equals(paramsType)) {
            return (T) getCccRangingStartedParamsFromTlvBuffer(tlvs);
        }
        if (CccSpecificationParams.class.equals(paramsType)) {
            return (T) getCccSpecificationParamsFromTlvBuffer(tlvs);
        }
        return null;
    }

    private static boolean isBitSet(int flags, int mask) {
        return (flags & mask) != 0;
    }

    private CccRangingStartedParams getCccRangingStartedParamsFromTlvBuffer(TlvDecoderBuffer tlvs) {
        byte[] hopModeKey = tlvs.getByteArray(ConfigParam.HOP_MODE_KEY);
        int hopModeKeyInt = ByteBuffer.wrap(hopModeKey).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return new CccRangingStartedParams.Builder()
                // STS_Index0  0 - 0x3FFFFFFFF
                .setStartingStsIndex(tlvs.getInt(ConfigParam.STS_INDEX))
                .setHopModeKey(hopModeKeyInt)
                //  UWB_Time0 0 - 0xFFFFFFFFFFFFFFFF  UWB_INITIATION_TIME
                .setUwbTime0(tlvs.getLong(ConfigParam.UWB_TIME0))
                // RANGING_INTERVAL = RAN_Multiplier * 96
                .setRanMultiplier(tlvs.getInt(ConfigParam.RANGING_INTERVAL) / 96)
                .setSyncCodeIndex(tlvs.getByte(ConfigParam.PREAMBLE_CODE_INDEX))
                .build();
    }

    private CccSpecificationParams getCccSpecificationParamsFromTlvBuffer(TlvDecoderBuffer tlvs) {
        CccSpecificationParams.Builder builder = new CccSpecificationParams.Builder();
        byte[] versions = tlvs.getByteArray(CCC_SUPPORTED_VERSIONS);
        if (versions.length % 2 != 0) {
            throw new IllegalArgumentException("Invalid supported protocol versions len "
                    + versions.length);
        }
        for (int i = 0; i < versions.length; i += 2) {
            builder.addProtocolVersion(CccProtocolVersion.fromBytes(versions, i));
        }
        byte[] configs = tlvs.getByteArray(CCC_SUPPORTED_UWB_CONFIGS);
        for (int i = 0; i < configs.length; i++) {
            builder.addUwbConfig(configs[i]);
        }
        byte[] pulse_shape_combos = tlvs.getByteArray(CCC_SUPPORTED_PULSE_SHAPE_COMBOS);
        for (int i = 0; i < pulse_shape_combos.length; i++) {
            builder.addPulseShapeCombo(CccPulseShapeCombo.fromBytes(pulse_shape_combos, i));
        }
        builder.setRanMultiplier(tlvs.getInt(CCC_SUPPORTED_RAN_MULTIPLIER));
        byte chapsPerslot = tlvs.getByte(CCC_SUPPORTED_CHAPS_PER_SLOT);
        if (isBitSet(chapsPerslot, CCC_CHAPS_PER_SLOT_3)) {
            builder.addChapsPerSlot(CHAPS_PER_SLOT_3);
        }
        if (isBitSet(chapsPerslot, CCC_CHAPS_PER_SLOT_4)) {
            builder.addChapsPerSlot(CHAPS_PER_SLOT_4);
        }
        if (isBitSet(chapsPerslot, CCC_CHAPS_PER_SLOT_6)) {
            builder.addChapsPerSlot(CHAPS_PER_SLOT_6);
        }
        if (isBitSet(chapsPerslot, CCC_CHAPS_PER_SLOT_8)) {
            builder.addChapsPerSlot(CHAPS_PER_SLOT_8);
        }
        if (isBitSet(chapsPerslot, CCC_CHAPS_PER_SLOT_9)) {
            builder.addChapsPerSlot(CHAPS_PER_SLOT_9);
        }
        if (isBitSet(chapsPerslot, CCC_CHAPS_PER_SLOT_12)) {
            builder.addChapsPerSlot(CHAPS_PER_SLOT_12);
        }
        if (isBitSet(chapsPerslot, CCC_CHAPS_PER_SLOT_24)) {
            builder.addChapsPerSlot(CHAPS_PER_SLOT_24);
        }
        // Don't use TlvDecodeBuffer#getInt() to avoid conversion to little endian.
        int syncCodes = ByteBuffer.wrap(tlvs.getByteArray(CCC_SUPPORTED_SYNC_CODES)).getInt();
        for (int i = 0; i < 32; i++) {
            if (isBitSet(syncCodes, 1 << i)) {
                builder.addSyncCode(i + 1);
            }
        }
        byte channels = tlvs.getByte(CCC_SUPPORTED_CHANNELS);
        if (isBitSet(channels, CCC_CHANNEL_5)) {
            builder.addChannel(UWB_CHANNEL_5);
        }
        if (isBitSet(channels, CCC_CHANNEL_9)) {
            builder.addChannel(UWB_CHANNEL_9);
        }
        byte hoppingConfigModesAndSequences =
                tlvs.getByte(CCC_SUPPORTED_HOPPING_CONFIG_MODES_AND_SEQUENCES);
        if (isBitSet(hoppingConfigModesAndSequences, CCC_HOPPING_CONFIG_MODE_NONE)) {
            builder.addHoppingConfigMode(HOPPING_CONFIG_MODE_NONE);
        }
        if (isBitSet(hoppingConfigModesAndSequences, CCC_HOPPING_CONFIG_MODE_CONTINUOUS)) {
            builder.addHoppingConfigMode(HOPPING_CONFIG_MODE_CONTINUOUS);
        }
        if (isBitSet(hoppingConfigModesAndSequences, CCC_HOPPING_CONFIG_MODE_ADAPTIVE)) {
            builder.addHoppingConfigMode(HOPPING_CONFIG_MODE_ADAPTIVE);
        }
        if (isBitSet(hoppingConfigModesAndSequences, CCC_HOPPING_SEQUENCE_AES)) {
            builder.addHoppingSequence(HOPPING_SEQUENCE_AES);
        }
        if (isBitSet(hoppingConfigModesAndSequences, CCC_HOPPING_SEQUENCE_DEFAULT)) {
            builder.addHoppingSequence(HOPPING_SEQUENCE_DEFAULT);
        }
        return builder.build();
    }
}

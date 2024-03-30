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

import com.android.server.uwb.config.ConfigParam;
import com.android.server.uwb.data.UwbCccConstants;
import com.android.server.uwb.data.UwbUciConstants;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.fira.FiraParams;

public class CccEncoder extends TlvEncoder {
    @Override
    public TlvBuffer getTlvBuffer(Params param) {
        if (param instanceof CccOpenRangingParams) {
            return getTlvBufferFromCccOpenRangingParams(param);
        }
        return null;
    }

    private TlvBuffer getTlvBufferFromCccOpenRangingParams(Params baseParam) {
        CccOpenRangingParams params = (CccOpenRangingParams) baseParam;
        int hoppingConfig = params.getHoppingConfigMode();
        int hoppingSequence = params.getHoppingSequence();

        int hoppingMode = CccParams.HOPPING_CONFIG_MODE_NONE;

        switch (hoppingConfig) {

            case CccParams.HOPPING_CONFIG_MODE_CONTINUOUS:
                if (hoppingSequence == CccParams.HOPPING_SEQUENCE_DEFAULT) {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_CONTINUOUS_DEFAULT;
                } else {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_CONTINUOUS_AES;
                }
                break;
            case CccParams.HOPPING_CONFIG_MODE_ADAPTIVE:
                if (hoppingSequence == CccParams.HOPPING_SEQUENCE_DEFAULT) {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_MODE_ADAPTIVE_DEFAULT;
                } else {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_MODE_ADAPTIVE_AES;
                }
                break;
        }

        TlvBuffer tlvBuffer = new TlvBuffer.Builder()
                .putByte(ConfigParam.DEVICE_TYPE,
                        (byte) UwbUciConstants.DEVICE_TYPE_CONTROLEE) // DEVICE_TYPE
                .putByte(ConfigParam.STS_CONFIG,
                        (byte) UwbUciConstants.STS_MODE_DYNAMIC) // STS_CONFIG
                .putByte(ConfigParam.CHANNEL_NUMBER, (byte) params.getChannel()) // CHANNEL_ID
                .putByte(ConfigParam.NUMBER_OF_CONTROLEES,
                        (byte) params.getNumResponderNodes()) // NUMBER_OF_ANCHORS
                .putInt(ConfigParam.RANGING_INTERVAL,
                        params.getRanMultiplier() * 96) //RANGING_INTERVAL = RAN_Multiplier * 96
                .putByte(ConfigParam.RANGE_DATA_NTF_CONFIG,
                        (byte) UwbUciConstants.RANGE_DATA_NTF_CONFIG_DISABLE) // RNG_DATA_NTF
                .putByte(ConfigParam.DEVICE_ROLE,
                        (byte) UwbUciConstants.RANGING_DEVICE_ROLE_INITIATOR) // DEVICE_ROLE
                .putByte(ConfigParam.MULTI_NODE_MODE,
                        (byte) FiraParams.MULTI_NODE_MODE_ONE_TO_MANY) // MULTI_NODE_MODE
                .putByte(ConfigParam.SLOTS_PER_RR,
                        (byte) params.getNumSlotsPerRound()) // SLOTS_PER_RR
                .putByte(ConfigParam.KEY_ROTATION, (byte) 0X01) // KEY_ROTATION
                .putByte(ConfigParam.HOPPING_MODE, (byte) hoppingMode) // HOPPING_MODE
                .putByteArray(ConfigParam.RANGING_PROTOCOL_VER,
                        ConfigParam.RANGING_PROTOCOL_VER_BYTE_COUNT,
                        params.getProtocolVersion().toBytes()) // RANGING_PROTOCOL_VER
                .putShort(ConfigParam.UWB_CONFIG_ID, (short) params.getUwbConfig()) // UWB_CONFIG_ID
                .putByte(ConfigParam.PULSESHAPE_COMBO,
                        params.getPulseShapeCombo().toBytes()[0]) // PULSESHAPE_COMBO
                .putShort(ConfigParam.URSK_TTL, (short) 0x2D0) // URSK_TTL
                // T(Slotk) =  N(Chap_per_Slot) * T(Chap)
                // T(Chap) = 400RSTU
                // reference : digital key release 3 20.2 MAC Time Grid
                .putShort(ConfigParam.SLOT_DURATION,
                        (short) (params.getNumChapsPerSlot() * 400)) // SLOT_DURATION
                .putByte(ConfigParam.PREAMBLE_CODE_INDEX,
                        (byte) params.getSyncCodeIndex()) // PREAMBLE_CODE_INDEX
                .build();

        return tlvBuffer;
    }
}

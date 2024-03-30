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

import android.uwb.UwbAddress;

import com.android.server.uwb.config.ConfigParam;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class FiraEncoder extends TlvEncoder {
    @Override
    public TlvBuffer getTlvBuffer(Params param) {
        if (param instanceof FiraOpenSessionParams) {
            return getTlvBufferFromFiraOpenSessionParams(param);
        }

        if (param instanceof FiraRangingReconfigureParams) {
            return getTlvBufferFromFiraRangingReconfigureParams(param);
        }
        return null;
    }

    private TlvBuffer getTlvBufferFromFiraOpenSessionParams(Params baseParam) {
        FiraOpenSessionParams params = (FiraOpenSessionParams) baseParam;
        ByteBuffer dstAddressList = ByteBuffer.allocate(1024);
        for (UwbAddress address : params.getDestAddressList()) {
            dstAddressList.put(TlvUtil.getReverseBytes(address.toBytes()));
        }

        int resultReportConfig = getResultReportConfig(params);
        int rangingRoundControl = getRangingRoundControl(params);

        TlvBuffer tlvBuffer = new TlvBuffer.Builder()
                .putByte(ConfigParam.DEVICE_TYPE, (byte) params.getDeviceType())
                .putByte(ConfigParam.RANGING_ROUND_USAGE, (byte) params.getRangingRoundUsage())
                .putByte(ConfigParam.STS_CONFIG, (byte) params.getStsConfig())
                .putByte(ConfigParam.MULTI_NODE_MODE, (byte) params.getMultiNodeMode())
                .putByte(ConfigParam.CHANNEL_NUMBER, (byte) params.getChannelNumber())
                .putByte(ConfigParam.NUMBER_OF_CONTROLEES,
                        (byte) params.getDestAddressList().size())
                .putByteArray(ConfigParam.DEVICE_MAC_ADDRESS, params.getDeviceAddress().size(),
                        TlvUtil.getReverseBytes(params.getDeviceAddress().toBytes()))
                .putByteArray(ConfigParam.DST_MAC_ADDRESS, dstAddressList.position(),
                        Arrays.copyOf(dstAddressList.array(), dstAddressList.position()))
                .putShort(ConfigParam.SLOT_DURATION, (short) params.getSlotDurationRstu())
                .putInt(ConfigParam.RANGING_INTERVAL, params.getRangingIntervalMs())
                .putByte(ConfigParam.MAC_FCS_TYPE, (byte) params.getFcsType())
                .putByte(ConfigParam.RANGING_ROUND_CONTROL,
                        (byte) rangingRoundControl/* params.getMeasurementReportType()*/)
                .putByte(ConfigParam.AOA_RESULT_REQ, (byte) params.getAoaResultRequest())
                .putByte(ConfigParam.RANGE_DATA_NTF_CONFIG, (byte) params.getRangeDataNtfConfig())
                .putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_NEAR,
                        (short) params.getRangeDataNtfProximityNear())
                .putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_FAR,
                        (short) params.getRangeDataNtfProximityFar())
                .putByte(ConfigParam.DEVICE_ROLE, (byte) params.getDeviceRole())
                .putByte(ConfigParam.RFRAME_CONFIG, (byte) params.getRframeConfig())
                .putByte(ConfigParam.PREAMBLE_CODE_INDEX, (byte) params.getPreambleCodeIndex())
                .putByte(ConfigParam.SFD_ID, (byte) params.getSfdId())
                .putByte(ConfigParam.PSDU_DATA_RATE, (byte) params.getPsduDataRate())
                .putByte(ConfigParam.PREAMBLE_DURATION, (byte) params.getPreambleDuration())
                .putByte(ConfigParam.SLOTS_PER_RR, (byte) params.getSlotsPerRangingRound())
                .putByte(ConfigParam.TX_ADAPTIVE_PAYLOAD_POWER,
                        params.isTxAdaptivePayloadPowerEnabled() ? (byte) 1 : (byte) 0)
                .putByte(ConfigParam.PRF_MODE, (byte) params.getPrfMode())
                .putByte(ConfigParam.KEY_ROTATION,
                        params.isKeyRotationEnabled() ? (byte) 1 : (byte) 0)
                .putByte(ConfigParam.KEY_ROTATION_RATE, (byte) params.getKeyRotationRate())
                .putByte(ConfigParam.SESSION_PRIORITY, (byte) params.getSessionPriority())
                .putByte(ConfigParam.MAC_ADDRESS_MODE, (byte) params.getMacAddressMode())
                .putByteArray(ConfigParam.VENDOR_ID,
                        TlvUtil.getReverseBytes(params.getVendorId()))
                .putByteArray(ConfigParam.STATIC_STS_IV,
                        params.getStaticStsIV())
                .putByte(ConfigParam.NUMBER_OF_STS_SEGMENTS, (byte) params.getStsSegmentCount())
                .putShort(ConfigParam.MAX_RR_RETRY, (short) params.getMaxRangingRoundRetries())
                .putInt(ConfigParam.UWB_INITIATION_TIME, params.getInitiationTimeMs())
                .putByte(ConfigParam.HOPPING_MODE,
                        (byte) params.getHoppingMode())
                .putByte(ConfigParam.BLOCK_STRIDE_LENGTH, (byte) params.getBlockStrideLength())
                .putByte(ConfigParam.RESULT_REPORT_CONFIG, (byte) resultReportConfig)
                .putByte(ConfigParam.IN_BAND_TERMINATION_ATTEMPT_COUNT,
                        (byte) params.getInBandTerminationAttemptCount())
                .putInt(ConfigParam.SUB_SESSION_ID, params.getSubSessionId())
                .putByte(ConfigParam.BPRF_PHR_DATA_RATE, (byte) params.getBprfPhrDataRate())
                .putByte(ConfigParam.STS_LENGTH, (byte) params.getStsLength())
                .putByte(ConfigParam.NUM_RANGE_MEASUREMENTS,
                        (byte) params.getNumOfMsrmtFocusOnRange())
                .putByte(ConfigParam.NUM_AOA_AZIMUTH_MEASUREMENTS,
                        (byte) params.getNumOfMsrmtFocusOnAoaAzimuth())
                .putByte(ConfigParam.NUM_AOA_ELEVATION_MEASUREMENTS,
                        (byte) params.getNumOfMsrmtFocusOnAoaElevation())
                .build();
        return tlvBuffer;
    }

    private TlvBuffer getTlvBufferFromFiraRangingReconfigureParams(Params baseParam) {
        FiraRangingReconfigureParams params = (FiraRangingReconfigureParams) baseParam;
        TlvBuffer.Builder tlvBuilder = new TlvBuffer.Builder();
        Integer blockStrideLength = params.getBlockStrideLength();
        Integer rangeDataNtfConfig = params.getRangeDataNtfConfig();
        Integer rangeDataProximityNear = params.getRangeDataProximityNear();
        Integer rangeDataProximityFar = params.getRangeDataProximityFar();

        if (blockStrideLength != null) {
            tlvBuilder.putByte(ConfigParam.BLOCK_STRIDE_LENGTH,
                    (byte) blockStrideLength.intValue());
        }

        if (rangeDataNtfConfig != null) {
            tlvBuilder.putByte(ConfigParam.RANGE_DATA_NTF_CONFIG,
                    (byte) rangeDataNtfConfig.intValue());
        }

        if (rangeDataProximityNear != null) {
            tlvBuilder.putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_NEAR,
                    (short) rangeDataProximityNear.intValue());
        }

        if (rangeDataProximityFar != null) {
            tlvBuilder.putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_FAR,
                    (short) rangeDataProximityFar.intValue());
        }

        return tlvBuilder.build();
    }

    // Merged data from other parameter values
    private int getResultReportConfig(FiraOpenSessionParams params) {
        int resultReportConfig = 0x00;
        resultReportConfig |= params.hasTimeOfFlightReport() ? 0x01 : 0x00;
        resultReportConfig |= params.hasAngleOfArrivalAzimuthReport() ? 0x02 : 0x00;
        resultReportConfig |= params.hasAngleOfArrivalElevationReport() ? 0x04 : 0x00;
        resultReportConfig |= params.hasAngleOfArrivalFigureOfMeritReport() ? 0x08 : 0x00;
        return resultReportConfig;
    }

    private int getRangingRoundControl(FiraOpenSessionParams params) {
        //RANGING_ROUND_CONTROL
        int rangingRoundControl = 0x02;

        // b0 : Ranging Result Report Message
        rangingRoundControl |= params.hasResultReportPhase() ? 0x01 : 0x00;

        // b7 : Measurement Report Message
        if (params.getMeasurementReportType()
                == FiraParams.MEASUREMENT_REPORT_TYPE_RESPONDER_TO_INITIATOR) {
            rangingRoundControl |= 0x80;
        }
        return rangingRoundControl;
    }
}

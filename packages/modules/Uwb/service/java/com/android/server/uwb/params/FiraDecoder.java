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

import static com.android.server.uwb.config.CapabilityParam.AOA_AZIMUTH_180;
import static com.android.server.uwb.config.CapabilityParam.AOA_AZIMUTH_90;
import static com.android.server.uwb.config.CapabilityParam.AOA_ELEVATION;
import static com.android.server.uwb.config.CapabilityParam.AOA_FOM;
import static com.android.server.uwb.config.CapabilityParam.AOA_RESULT_REQ_INTERLEAVING;
import static com.android.server.uwb.config.CapabilityParam.BLOCK_STRIDING;
import static com.android.server.uwb.config.CapabilityParam.CC_CONSTRAINT_LENGTH_K3;
import static com.android.server.uwb.config.CapabilityParam.CC_CONSTRAINT_LENGTH_K7;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_10;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_12;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_13;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_14;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_5;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_6;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_8;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_9;
import static com.android.server.uwb.config.CapabilityParam.DS_TWR_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.DS_TWR_NON_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.DYNAMIC_STS;
import static com.android.server.uwb.config.CapabilityParam.DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY;
import static com.android.server.uwb.config.CapabilityParam.INITIATOR;
import static com.android.server.uwb.config.CapabilityParam.MANY_TO_MANY;
import static com.android.server.uwb.config.CapabilityParam.ONE_TO_MANY;
import static com.android.server.uwb.config.CapabilityParam.RESPONDER;
import static com.android.server.uwb.config.CapabilityParam.SP0;
import static com.android.server.uwb.config.CapabilityParam.SP1;
import static com.android.server.uwb.config.CapabilityParam.SP3;
import static com.android.server.uwb.config.CapabilityParam.SS_TWR_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.SS_TWR_NON_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.STATIC_STS;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_AOA;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_AOA_RESULT_REQ_INTERLEAVING;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_BLOCK_STRIDING;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_BPRF_PARAMETER_SETS;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_CC_CONSTRAINT_LENGTH;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_CHANNELS;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_DEVICE_ROLES;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_EXTENDED_MAC_ADDRESS;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_FIRA_MAC_VERSION_RANGE;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_FIRA_PHY_VERSION_RANGE;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_HPRF_PARAMETER_SETS;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_MULTI_NODE_MODES;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_RANGING_METHOD;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_RFRAME_CONFIG;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_STS_CONFIG;
import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_UWB_INITIATION_TIME;
import static com.android.server.uwb.config.CapabilityParam.UNICAST;
import static com.android.server.uwb.config.CapabilityParam.UWB_INITIATION_TIME;

import com.google.uwb.support.base.FlagEnum;
import com.google.uwb.support.base.Params;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraParams.BprfParameterSetCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.HprfParameterSetCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.MultiNodeCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.StsCapabilityFlag;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraSpecificationParams;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;

public class FiraDecoder extends TlvDecoder {
    @Override
    public <T extends Params> T getParams(TlvDecoderBuffer tlvs, Class<T> paramType) {
        if (FiraSpecificationParams.class.equals(paramType)) {
            return (T) getFiraSpecificationParamsFromTlvBuffer(tlvs);
        }
        return null;
    }

    private static boolean isBitSet(int flags, int mask) {
        return (flags & mask) != 0;
    }

    private FiraSpecificationParams getFiraSpecificationParamsFromTlvBuffer(TlvDecoderBuffer tlvs) {
        FiraSpecificationParams.Builder builder = new FiraSpecificationParams.Builder();
        byte[] phyVersions = tlvs.getByteArray(SUPPORTED_FIRA_PHY_VERSION_RANGE);
        builder.setMinPhyVersionSupported(FiraProtocolVersion.fromBytes(phyVersions, 0));
        builder.setMaxPhyVersionSupported(FiraProtocolVersion.fromBytes(phyVersions, 2));
        byte[] macVersions = tlvs.getByteArray(SUPPORTED_FIRA_MAC_VERSION_RANGE);
        builder.setMinMacVersionSupported(FiraProtocolVersion.fromBytes(macVersions, 0));
        builder.setMaxMacVersionSupported(FiraProtocolVersion.fromBytes(macVersions, 2));

        byte deviceRolesUci = tlvs.getByte(SUPPORTED_DEVICE_ROLES);
        EnumSet<DeviceRoleCapabilityFlag> deviceRoles =
                EnumSet.noneOf(DeviceRoleCapabilityFlag.class);
        if (isBitSet(deviceRolesUci, INITIATOR)) {
            // This assumes both controller + controlee is supported.
            deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLLER_INITIATOR_SUPPORT);
            deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLEE_INITIATOR_SUPPORT);
        }
        if (isBitSet(deviceRolesUci, RESPONDER)) {
            // This assumes both controller + controlee is supported.
            deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLLER_RESPONDER_SUPPORT);
            deviceRoles.add(DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT);
        }
        builder.setDeviceRoleCapabilities(deviceRoles);

        byte rangingMethodUci = tlvs.getByte(SUPPORTED_RANGING_METHOD);
        EnumSet<RangingRoundCapabilityFlag> rangingRoundFlag = EnumSet.noneOf(
                RangingRoundCapabilityFlag.class);
        if (isBitSet(rangingMethodUci, DS_TWR_DEFERRED)) {
            rangingRoundFlag.add(RangingRoundCapabilityFlag.HAS_DS_TWR_SUPPORT);
        }
        if (isBitSet(rangingMethodUci, SS_TWR_DEFERRED)) {
            rangingRoundFlag.add(RangingRoundCapabilityFlag.HAS_SS_TWR_SUPPORT);
        }
        builder.setRangingRoundCapabilities(rangingRoundFlag);

        // TODO(b/209053358): This does not align with UCI spec.
        if (isBitSet(rangingMethodUci, DS_TWR_NON_DEFERRED)
                || isBitSet(rangingMethodUci, SS_TWR_NON_DEFERRED)) {
            builder.hasNonDeferredModeSupport(true);
        }

        byte stsConfigUci = tlvs.getByte(SUPPORTED_STS_CONFIG);
        EnumSet<StsCapabilityFlag> stsCapabilityFlag = EnumSet.noneOf(StsCapabilityFlag.class);
        if (isBitSet(stsConfigUci, STATIC_STS)) {
            stsCapabilityFlag.add(StsCapabilityFlag.HAS_STATIC_STS_SUPPORT);
        }
        if (isBitSet(stsConfigUci, DYNAMIC_STS)) {
            stsCapabilityFlag.add(StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT);
        }
        if (isBitSet(stsConfigUci, DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY)) {
            stsCapabilityFlag.add(
                    StsCapabilityFlag.HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT);
        }
        builder.setStsCapabilities(stsCapabilityFlag);

        byte multiNodeUci = tlvs.getByte(SUPPORTED_MULTI_NODE_MODES);
        EnumSet<MultiNodeCapabilityFlag> multiNodeFlag =
                EnumSet.noneOf(MultiNodeCapabilityFlag.class);
        if (isBitSet(multiNodeUci, UNICAST)) {
            multiNodeFlag.add(MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT);
        }
        if (isBitSet(multiNodeUci, ONE_TO_MANY)) {
            multiNodeFlag.add(MultiNodeCapabilityFlag.HAS_ONE_TO_MANY_SUPPORT);
        }
        if (isBitSet(multiNodeUci, MANY_TO_MANY)) {
            multiNodeFlag.add(MultiNodeCapabilityFlag.HAS_MANY_TO_MANY_SUPPORT);
        }
        builder.setMultiNodeCapabilities(multiNodeFlag);

        byte blockStridingUci = tlvs.getByte(SUPPORTED_BLOCK_STRIDING);
        if (isBitSet(blockStridingUci, BLOCK_STRIDING)) {
            builder.hasBlockStridingSupport(true);
        }

        byte initiationTimeUci = tlvs.getByte(SUPPORTED_UWB_INITIATION_TIME);
        if (isBitSet(initiationTimeUci, UWB_INITIATION_TIME)) {
            builder.hasInitiationTimeSupport(true);
        }

        byte channelsUci = tlvs.getByte(SUPPORTED_CHANNELS);
        List<Integer> channels = new ArrayList<>();
        if (isBitSet(channelsUci, CHANNEL_5)) {
            channels.add(5);
        }
        if (isBitSet(channelsUci, CHANNEL_6)) {
            channels.add(6);
        }
        if (isBitSet(channelsUci, CHANNEL_8)) {
            channels.add(8);
        }
        if (isBitSet(channelsUci, CHANNEL_9)) {
            channels.add(9);
        }
        if (isBitSet(channelsUci, CHANNEL_10)) {
            channels.add(10);
        }
        if (isBitSet(channelsUci, CHANNEL_12)) {
            channels.add(12);
        }
        if (isBitSet(channelsUci, CHANNEL_13)) {
            channels.add(13);
        }
        if (isBitSet(channelsUci, CHANNEL_14)) {
            channels.add(14);
        }
        builder.setSupportedChannels(channels);

        byte rframeConfigUci = tlvs.getByte(SUPPORTED_RFRAME_CONFIG);
        EnumSet<RframeCapabilityFlag> rframeConfigFlag =
                EnumSet.noneOf(RframeCapabilityFlag.class);
        if (isBitSet(rframeConfigUci, SP0)) {
            rframeConfigFlag.add(RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT);
        }
        if (isBitSet(rframeConfigUci, SP1)) {
            rframeConfigFlag.add(RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT);
        }
        if (isBitSet(rframeConfigUci, SP3)) {
            rframeConfigFlag.add(RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT);
        }
        builder.setRframeCapabilities(rframeConfigFlag);

        byte bprfSets = tlvs.getByte(SUPPORTED_BPRF_PARAMETER_SETS);
        int bprfSetsValue = Integer.valueOf(bprfSets);
        EnumSet<BprfParameterSetCapabilityFlag> bprfFlag;
        bprfFlag = FlagEnum.toEnumSet(bprfSetsValue, BprfParameterSetCapabilityFlag.values());
        builder.setBprfParameterSetCapabilities(bprfFlag);

        byte[] hprfSets = tlvs.getByteArray(SUPPORTED_HPRF_PARAMETER_SETS);
        // Extend the 5 bytes from HAL to 8 bytes for long.
        long hprfSetsValue = new BigInteger(hprfSets).longValue();
        EnumSet<HprfParameterSetCapabilityFlag> hprfFlag;
        hprfFlag = FlagEnum.longToEnumSet(
                hprfSetsValue, HprfParameterSetCapabilityFlag.values());
        builder.setHprfParameterSetCapabilities(hprfFlag);

        EnumSet<FiraParams.PrfCapabilityFlag> prfFlag =
                EnumSet.noneOf(FiraParams.PrfCapabilityFlag.class);
        boolean hasBprfSupport = bprfSets != 0;
        if (hasBprfSupport) {
            prfFlag.add(FiraParams.PrfCapabilityFlag.HAS_BPRF_SUPPORT);
        }
        boolean hasHprfSupport =
                IntStream.range(0, hprfSets.length).parallel().anyMatch(i -> hprfSets[i] != 0);
        if (hasHprfSupport) {
            prfFlag.add(FiraParams.PrfCapabilityFlag.HAS_HPRF_SUPPORT);
        }
        builder.setPrfCapabilities(prfFlag);

        byte ccConstraintUci = tlvs.getByte(SUPPORTED_CC_CONSTRAINT_LENGTH);
        EnumSet<PsduDataRateCapabilityFlag> psduRateFlag =
                EnumSet.noneOf(PsduDataRateCapabilityFlag.class);
        if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K3) && hasBprfSupport) {
            psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_6M81_SUPPORT);
        }
        if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K7) && hasBprfSupport) {
            psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_7M80_SUPPORT);
        }
        if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K3) && hasHprfSupport) {
            psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_27M2_SUPPORT);
        }
        if (isBitSet(ccConstraintUci, CC_CONSTRAINT_LENGTH_K7) && hasHprfSupport) {
            psduRateFlag.add(PsduDataRateCapabilityFlag.HAS_31M2_SUPPORT);
        }
        builder.setPsduDataRateCapabilities(psduRateFlag);

        byte aoaUci = tlvs.getByte(SUPPORTED_AOA);
        EnumSet<FiraParams.AoaCapabilityFlag> aoaFlag =
                EnumSet.noneOf(FiraParams.AoaCapabilityFlag.class);
        if (isBitSet(aoaUci, AOA_AZIMUTH_90)) {
            aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT);
        }
        if (isBitSet(aoaUci, AOA_AZIMUTH_180)) {
            aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT);
        }
        if (isBitSet(aoaUci, AOA_ELEVATION)) {
            aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT);
        }
        if (isBitSet(aoaUci, AOA_FOM)) {
            aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT);
        }
        byte aoaInterleavingUci = tlvs.getByte(SUPPORTED_AOA_RESULT_REQ_INTERLEAVING);
        if (isBitSet(aoaInterleavingUci, AOA_RESULT_REQ_INTERLEAVING)) {
            aoaFlag.add(FiraParams.AoaCapabilityFlag.HAS_INTERLEAVING_SUPPORT);
        }
        builder.setAoaCapabilities(aoaFlag);

        // TODO(b/209053358): This is not present in the FiraSpecificationParams.
        byte extendedMacUci = tlvs.getByte(SUPPORTED_EXTENDED_MAC_ADDRESS);
        return builder.build();
    }
}

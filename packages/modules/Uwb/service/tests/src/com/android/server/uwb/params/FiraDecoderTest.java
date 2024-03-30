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

import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_INTERLEAVING_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_INITIATOR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_INITIATOR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_RESPONDER_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.MultiNodeCapabilityFlag.HAS_ONE_TO_MANY_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PrfCapabilityFlag.HAS_BPRF_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PrfCapabilityFlag.HAS_HPRF_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag.HAS_27M2_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag.HAS_31M2_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag.HAS_6M81_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag.HAS_7M80_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag.HAS_DS_TWR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag.HAS_SS_TWR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.StsCapabilityFlag.HAS_STATIC_STS_SUPPORT;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraParams.BprfParameterSetCapabilityFlag;
import com.google.uwb.support.fira.FiraParams.HprfParameterSetCapabilityFlag;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraSpecificationParams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.EnumSet;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.uwb.params.FiraDecoder}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class FiraDecoderTest {
    public static final String TEST_FIRA_SPECIFICATION_TLV_STRING =
            "000401010102"
                    + "010401050103"
                    + "020103"
                    + "03011F"
                    + "040103"
                    + "050103"
                    + "060100"
                    + "070100"
                    + "080100"
                    + "090101"
                    + "0A0101"
                    + "0B0109"
                    + "0C010B"
                    + "0D0103"
                    + "0E0101"
                    + "0F050000000003"
                    + "10010F"
                    + "110101"
                    + "E30101";
    private static final byte[] TEST_FIRA_SPECIFICATION_TLV_DATA =
            UwbUtil.getByteArray(TEST_FIRA_SPECIFICATION_TLV_STRING);
    public static final int TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS = 19;
    private final FiraDecoder mFiraDecoder = new FiraDecoder();

    public static void verifyFiraSpecification(FiraSpecificationParams firaSpecificationParams) {
        assertThat(firaSpecificationParams).isNotNull();

        assertThat(firaSpecificationParams.getMinPhyVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[] {1, 1},  0));
        assertThat(firaSpecificationParams.getMaxPhyVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[] {1, 2},  0));
        assertThat(firaSpecificationParams.getMinMacVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[] {1, 5},  0));
        assertThat(firaSpecificationParams.getMaxMacVersionSupported()).isEqualTo(
                FiraProtocolVersion.fromBytes(new byte[] {1, 3},  0));

        assertThat(firaSpecificationParams.getDeviceRoleCapabilities()).isEqualTo(
                EnumSet.of(HAS_CONTROLEE_RESPONDER_SUPPORT, HAS_CONTROLLER_RESPONDER_SUPPORT,
                        HAS_CONTROLEE_INITIATOR_SUPPORT, HAS_CONTROLLER_INITIATOR_SUPPORT));

        assertThat(firaSpecificationParams.getRangingRoundCapabilities()).isEqualTo(
                EnumSet.of(HAS_DS_TWR_SUPPORT, HAS_SS_TWR_SUPPORT));
        assertThat(firaSpecificationParams.hasNonDeferredModeSupport()).isTrue();

        assertThat(firaSpecificationParams.getStsCapabilities()).isEqualTo(
                EnumSet.of(HAS_STATIC_STS_SUPPORT, HAS_DYNAMIC_STS_SUPPORT));

        assertThat(firaSpecificationParams.getMultiNodeCapabilities()).isEqualTo(
                EnumSet.of(HAS_ONE_TO_MANY_SUPPORT, HAS_UNICAST_SUPPORT));

        assertThat(firaSpecificationParams.hasBlockStridingSupport()).isEqualTo(true);

        assertThat(firaSpecificationParams.getSupportedChannels()).isEqualTo(List.of(5, 9));

        assertThat(firaSpecificationParams.getRframeCapabilities()).isEqualTo(
                EnumSet.of(HAS_SP0_RFRAME_SUPPORT, HAS_SP1_RFRAME_SUPPORT,
                        HAS_SP3_RFRAME_SUPPORT));

        assertThat(firaSpecificationParams.getPrfCapabilities()).isEqualTo(
                EnumSet.of(HAS_BPRF_SUPPORT, HAS_HPRF_SUPPORT));
        assertThat(firaSpecificationParams.getPsduDataRateCapabilities()).isEqualTo(
                EnumSet.of(HAS_6M81_SUPPORT, HAS_7M80_SUPPORT, HAS_27M2_SUPPORT, HAS_31M2_SUPPORT));

        assertThat(firaSpecificationParams.getAoaCapabilities()).isEqualTo(
                EnumSet.of(HAS_AZIMUTH_SUPPORT, HAS_ELEVATION_SUPPORT, HAS_FULL_AZIMUTH_SUPPORT,
                        HAS_FOM_SUPPORT, HAS_INTERLEAVING_SUPPORT));

        assertThat(firaSpecificationParams.getBprfParameterSetCapabilities()).isEqualTo(
                EnumSet.of(BprfParameterSetCapabilityFlag.HAS_SET_1_SUPPORT));

        assertThat(firaSpecificationParams.getHprfParameterSetCapabilities()).isEqualTo(
                EnumSet.of(HprfParameterSetCapabilityFlag.HAS_SET_1_SUPPORT,
                        HprfParameterSetCapabilityFlag.HAS_SET_2_SUPPORT));
    }

    @Test
    public void testGetFiraSpecification() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_FIRA_SPECIFICATION_TLV_DATA, TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        FiraSpecificationParams firaSpecificationParams = mFiraDecoder.getParams(
                tlvDecoderBuffer, FiraSpecificationParams.class);
        verifyFiraSpecification(firaSpecificationParams);
    }

    @Test
    public void testGetFiraSpecificationViaTlvDecoder() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_FIRA_SPECIFICATION_TLV_DATA, TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        FiraSpecificationParams firaSpecificationParams = TlvDecoder
                .getDecoder(FiraParams.PROTOCOL_NAME)
                .getParams(tlvDecoderBuffer, FiraSpecificationParams.class);
        verifyFiraSpecification(firaSpecificationParams);
    }
}

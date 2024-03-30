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
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_3;
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_9;
import static com.google.uwb.support.ccc.CccParams.HOPPING_CONFIG_MODE_ADAPTIVE;
import static com.google.uwb.support.ccc.CccParams.HOPPING_CONFIG_MODE_CONTINUOUS;
import static com.google.uwb.support.ccc.CccParams.HOPPING_SEQUENCE_AES;
import static com.google.uwb.support.ccc.CccParams.PULSE_SHAPE_PRECURSOR_FREE;
import static com.google.uwb.support.ccc.CccParams.PULSE_SHAPE_PRECURSOR_FREE_SPECIAL;
import static com.google.uwb.support.ccc.CccParams.UWB_CONFIG_0;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccProtocolVersion;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccRangingStartedParams;
import com.google.uwb.support.ccc.CccSpecificationParams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Unit tests for {@link com.android.server.uwb.params.CccDecoder}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class CccDecoderTest {
    private static final byte[] TEST_CCC_RANGING_OPENED_TLV_DATA =
            UwbUtil.getByteArray("0a0402000100"
                            + "a01001000200000000000000000000000000"
                            + "a1080200010002000100"
                            + "090402000100"
                            + "140101");
    private static final int TEST_CCC_RANGING_OPENED_TLV_NUM_PARAMS = 5;
    public static final String TEST_CCC_SPECIFICATION_TLV_DATA_STRING =
            "a00111"
                    + "a10400000082"
                    + "a20168"
                    + "a30103"
                    + "a4020102"
                    + "a50100"
                    + "a60112"
                    + "a7040a000000";

    private static final byte[] TEST_CCC_SPECIFICATION_TLV_DATA =
            UwbUtil.getByteArray(TEST_CCC_SPECIFICATION_TLV_DATA_STRING);
    public static final int TEST_CCC_SPECIFICATION_TLV_NUM_PARAMS = 8;
    private final CccDecoder mCccDecoder = new CccDecoder();

    private void verifyCccRangingOpend(CccRangingStartedParams cccRangingStartedParams) {
        assertThat(cccRangingStartedParams).isNotNull();

        assertThat(cccRangingStartedParams.getStartingStsIndex()).isEqualTo(0x00010002);
        assertThat(cccRangingStartedParams.getHopModeKey()).isEqualTo(0x00020001);
        assertThat(cccRangingStartedParams.getUwbTime0()).isEqualTo(0x0001000200010002L);
        assertThat(cccRangingStartedParams.getRanMultiplier()).isEqualTo(0x00010002 / 96);
    }

    public static void verifyCccSpecification(CccSpecificationParams cccSpecificationParams) {
        assertThat(cccSpecificationParams).isNotNull();

        assertThat(cccSpecificationParams.getProtocolVersions()).isEqualTo(List.of(
                CccProtocolVersion.fromBytes(new byte[] {1, 2}, 0)));
        assertThat(cccSpecificationParams.getUwbConfigs()).isEqualTo(List.of(UWB_CONFIG_0));
        assertThat(cccSpecificationParams.getPulseShapeCombos()).isEqualTo(
                List.of(new CccPulseShapeCombo(
                        PULSE_SHAPE_PRECURSOR_FREE, PULSE_SHAPE_PRECURSOR_FREE_SPECIAL)));
        assertThat(cccSpecificationParams.getRanMultiplier()).isEqualTo(10);
        assertThat(cccSpecificationParams.getChapsPerSlot()).isEqualTo(
                List.of(CHAPS_PER_SLOT_3, CHAPS_PER_SLOT_9));
        assertThat(cccSpecificationParams.getSyncCodes()).isEqualTo(
                List.of(2, 8));
        assertThat(cccSpecificationParams.getChannels()).isEqualTo(List.of(5, 9));
        assertThat(cccSpecificationParams.getHoppingConfigModes()).isEqualTo(
                List.of(HOPPING_CONFIG_MODE_CONTINUOUS, HOPPING_CONFIG_MODE_ADAPTIVE));
        assertThat(cccSpecificationParams.getHoppingSequences()).isEqualTo(
                List.of(HOPPING_SEQUENCE_AES));
    }

    @Test
    public void testGetCccRangingOpened() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_CCC_RANGING_OPENED_TLV_DATA, TEST_CCC_RANGING_OPENED_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        CccRangingStartedParams cccRangingStartedParams = mCccDecoder.getParams(
                tlvDecoderBuffer, CccRangingStartedParams.class);
        verifyCccRangingOpend(cccRangingStartedParams);
    }

    @Test
    public void testGetCccSpecification() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_CCC_SPECIFICATION_TLV_DATA, TEST_CCC_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        CccSpecificationParams cccSpecificationParams = mCccDecoder.getParams(
                tlvDecoderBuffer, CccSpecificationParams.class);
        verifyCccSpecification(cccSpecificationParams);
    }

    @Test
    public void testGetCccRangingOpenedViaTlvDecoder() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_CCC_RANGING_OPENED_TLV_DATA, TEST_CCC_RANGING_OPENED_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        CccRangingStartedParams cccRangingStartedParams = TlvDecoder
                .getDecoder(CccParams.PROTOCOL_NAME)
                .getParams(tlvDecoderBuffer, CccRangingStartedParams.class);
        verifyCccRangingOpend(cccRangingStartedParams);
    }

    @Test
    public void testGetCccSpecificationViaTlvDecoder() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_CCC_SPECIFICATION_TLV_DATA, TEST_CCC_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        CccSpecificationParams cccSpecificationParams = TlvDecoder
                .getDecoder(CccParams.PROTOCOL_NAME)
                .getParams(tlvDecoderBuffer, CccSpecificationParams.class);
        verifyCccSpecification(cccSpecificationParams);
    }
}

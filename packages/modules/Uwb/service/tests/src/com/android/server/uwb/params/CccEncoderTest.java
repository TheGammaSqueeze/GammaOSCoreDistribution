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
import static com.google.uwb.support.ccc.CccParams.HOPPING_CONFIG_MODE_NONE;
import static com.google.uwb.support.ccc.CccParams.HOPPING_SEQUENCE_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE;
import static com.google.uwb.support.ccc.CccParams.SLOTS_PER_ROUND_6;
import static com.google.uwb.support.ccc.CccParams.UWB_CHANNEL_9;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccPulseShapeCombo;

import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Unit tests for {@link com.android.server.uwb.params.CccEncoder}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class CccEncoderTest {
    private static final CccOpenRangingParams.Builder TEST_CCC_OPEN_RANGING_PARAMS =
            new CccOpenRangingParams.Builder()
                    .setProtocolVersion(CccParams.PROTOCOL_VERSION_1_0)
                    .setUwbConfig(CccParams.UWB_CONFIG_0)
                    .setPulseShapeCombo(
                            new CccPulseShapeCombo(
                                    PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                                    PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE))
                    .setSessionId(1)
                    .setRanMultiplier(4)
                    .setChannel(UWB_CHANNEL_9)
                    .setNumChapsPerSlot(CHAPS_PER_SLOT_3)
                    .setNumResponderNodes(1)
                    .setNumSlotsPerRound(SLOTS_PER_ROUND_6)
                    .setSyncCodeIndex(1)
                    .setHoppingConfigMode(HOPPING_CONFIG_MODE_NONE)
                    .setHoppingSequence(HOPPING_SEQUENCE_DEFAULT);

    private static final byte[] TEST_CCC_OPEN_RANGING_TLV_DATA =
            UwbUtil.getByteArray("0001000201010401090501010904800100000E010011010103010"
                    + "11B01062301012C0100A3020100A4020000A50100A602D0020802B004140101");

    private final CccEncoder mCccEncoder = new CccEncoder();

    @Test
    public void testCccOpenRangingParams() throws Exception {
        CccOpenRangingParams params = TEST_CCC_OPEN_RANGING_PARAMS.build();
        TlvBuffer tlvs = mCccEncoder.getTlvBuffer(params);

        assertThat(tlvs.getNoOfParams()).isEqualTo(17);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_CCC_OPEN_RANGING_TLV_DATA);
    }

    @Test
    public void testCccOpenRangingParamsViaTlvEncoder() throws Exception {
        CccOpenRangingParams params = TEST_CCC_OPEN_RANGING_PARAMS.build();
        TlvBuffer tlvs = TlvEncoder.getEncoder(CccParams.PROTOCOL_NAME).getTlvBuffer(params);

        assertThat(tlvs.getNoOfParams()).isEqualTo(17);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_CCC_OPEN_RANGING_TLV_DATA);
    }
}

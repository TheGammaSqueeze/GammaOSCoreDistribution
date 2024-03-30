/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY;
import static com.android.server.uwb.util.UwbUtil.convertFloatToQFormat;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.fira.FiraParams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Unit tests for {@link com.android.server.uwb.data.UwbRangingData}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbRangingDataTest {
    private static final long TEST_SEQ_COUNTER = 5;
    private static final long TEST_SESSION_ID = 7;
    private static final int TEST_RCR_INDICATION = 7;
    private static final long TEST_CURR_RANGING_INTERVAL = 100;
    private static final int TEST_RANGING_MEASURES_TYPE = RANGING_MEASUREMENT_TYPE_TWO_WAY;
    private static final int TEST_MAC_ADDRESS_MODE = 1;
    private static final byte[] TEST_MAC_ADDRESS = {0x1, 0x3};
    private static final int TEST_STATUS = FiraParams.STATUS_CODE_OK;
    private static final int TEST_LOS = 0;
    private static final int TEST_DISTANCE = 101;
    private static final float TEST_AOA_AZIMUTH = 67;
    private static final int TEST_AOA_AZIMUTH_FOM = 50;
    private static final float TEST_AOA_ELEVATION = 37;
    private static final int TEST_AOA_ELEVATION_FOM = 90;
    private static final float TEST_AOA_DEST_AZIMUTH = 67;
    private static final int TEST_AOA_DEST_AZIMUTH_FOM = 50;
    private static final float TEST_AOA_DEST_ELEVATION = 37;
    private static final int TEST_AOA_DEST_ELEVATION_FOM = 90;
    private static final int TEST_SLOT_IDX = 10;

    private UwbRangingData mUwbRangingData;

    @Test
    public void testInitializeUwbRangingData() throws Exception {
        final int noOfRangingMeasures = 1;
        final UwbTwoWayMeasurement[] uwbTwoWayMeasurements =
                new UwbTwoWayMeasurement[noOfRangingMeasures];
        uwbTwoWayMeasurements[0] = new UwbTwoWayMeasurement(TEST_MAC_ADDRESS, TEST_STATUS, TEST_LOS,
                TEST_DISTANCE, convertFloatToQFormat(TEST_AOA_AZIMUTH, 9, 7),
                TEST_AOA_AZIMUTH_FOM, convertFloatToQFormat(TEST_AOA_ELEVATION, 9, 7),
                TEST_AOA_ELEVATION_FOM, convertFloatToQFormat(TEST_AOA_DEST_AZIMUTH, 9, 7),
                TEST_AOA_DEST_AZIMUTH_FOM, convertFloatToQFormat(TEST_AOA_DEST_ELEVATION, 9, 7),
                TEST_AOA_DEST_ELEVATION_FOM, TEST_SLOT_IDX);
        mUwbRangingData = new UwbRangingData(TEST_SEQ_COUNTER, TEST_SESSION_ID,
                TEST_RCR_INDICATION, TEST_CURR_RANGING_INTERVAL, TEST_RANGING_MEASURES_TYPE,
                TEST_MAC_ADDRESS_MODE, noOfRangingMeasures, uwbTwoWayMeasurements);

        assertThat(mUwbRangingData.getSequenceCounter()).isEqualTo(TEST_SEQ_COUNTER);
        assertThat(mUwbRangingData.getSessionId()).isEqualTo(TEST_SESSION_ID);
        assertThat(mUwbRangingData.getRcrIndication()).isEqualTo(TEST_RCR_INDICATION);
        assertThat(mUwbRangingData.getCurrRangingInterval()).isEqualTo(TEST_CURR_RANGING_INTERVAL);
        assertThat(mUwbRangingData.getRangingMeasuresType()).isEqualTo(TEST_RANGING_MEASURES_TYPE);
        assertThat(mUwbRangingData.getMacAddressMode()).isEqualTo(TEST_MAC_ADDRESS_MODE);
        assertThat(mUwbRangingData.getNoOfRangingMeasures()).isEqualTo(1);

        final String testString = "UwbRangingData { "
                + " SeqCounter = " + TEST_SEQ_COUNTER
                + ", SessionId = " + TEST_SESSION_ID
                + ", RcrIndication = " + TEST_RCR_INDICATION
                + ", CurrRangingInterval = " + TEST_CURR_RANGING_INTERVAL
                + ", RangingMeasuresType = " + TEST_RANGING_MEASURES_TYPE
                + ", MacAddressMode = " + TEST_MAC_ADDRESS_MODE
                + ", NoOfRangingMeasures = " + noOfRangingMeasures
                + ", RangingTwoWayMeasures = " + Arrays.toString(uwbTwoWayMeasurements)
                + '}';

        assertThat(mUwbRangingData.toString()).isEqualTo(testString);
    }
}

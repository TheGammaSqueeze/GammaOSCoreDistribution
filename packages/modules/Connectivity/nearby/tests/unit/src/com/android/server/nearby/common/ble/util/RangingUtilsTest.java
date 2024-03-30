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

package com.android.server.nearby.common.ble.util;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RangingUtilsTest {
    // relative error to be used in comparing doubles
    private static final double DELTA = 1e-5;

    @Test
    public void distanceFromRssi_getCorrectValue() {
        // Distance expected to be 1.0 meters based on an RSSI/TxPower of -41dBm
        // Using params: int rssi (dBm), int calibratedTxPower (dBm)
        double distance = RangingUtils.distanceFromRssiAndTxPower(-82, -41);
        assertThat(distance).isWithin(DELTA).of(1.0);

        double distance2 = RangingUtils.distanceFromRssiAndTxPower(-111, -50);
        assertThat(distance2).isWithin(DELTA).of(10.0);

        //rssi txpower
        double distance4 = RangingUtils.distanceFromRssiAndTxPower(-50, -29);
        assertThat(distance4).isWithin(DELTA).of(0.1);
    }

    @Test
    public void testRssiFromDistance() {
        // RSSI expected at 1 meter based on the calibrated tx field of -41dBm
        // Using params: distance (m), int calibratedTxPower (dBm),
        int rssi = RangingUtils.rssiFromTargetDistance(1.0, -41);

        assertThat(rssi).isEqualTo(-82);
    }

    @Test
    public void testOutOfRange() {
        double distance = RangingUtils.distanceFromRssiAndTxPower(-200, -41);
        assertThat(distance).isWithin(DELTA).of(177.82794);

        distance = RangingUtils.distanceFromRssiAndTxPower(200, -41);
        assertThat(distance).isWithin(DELTA).of(0);
    }
}

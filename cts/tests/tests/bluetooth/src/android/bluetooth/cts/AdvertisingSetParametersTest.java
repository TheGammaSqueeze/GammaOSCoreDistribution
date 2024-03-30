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

package android.bluetooth.cts;

import static android.bluetooth.BluetoothDevice.PHY_LE_1M;
import static android.bluetooth.BluetoothDevice.PHY_LE_2M;
import static android.bluetooth.BluetoothDevice.PHY_LE_CODED;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_LOW;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_MAX;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_MEDIUM;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_MIN;
import static android.bluetooth.le.AdvertisingSetParameters.TX_POWER_MAX;
import static android.bluetooth.le.AdvertisingSetParameters.TX_POWER_MEDIUM;
import static android.bluetooth.le.AdvertisingSetParameters.TX_POWER_MIN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.bluetooth.le.AdvertisingSetParameters;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AdvertisingSetParametersTest {

    @Test
    public void testCreateFromParcel() {
        final Parcel parcel = Parcel.obtain();
        try {
            AdvertisingSetParameters params = new AdvertisingSetParameters.Builder().build();
            params.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            AdvertisingSetParameters paramsFromParcel =
                    AdvertisingSetParameters.CREATOR.createFromParcel(parcel);
            assertParamsEquals(params, paramsFromParcel);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void testDefaultParameters() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder().build();

        assertFalse(params.isConnectable());
        assertFalse(params.isScannable());
        assertFalse(params.isLegacy());
        assertFalse(params.isAnonymous());
        assertFalse(params.includeTxPower());
        assertEquals(PHY_LE_1M, params.getPrimaryPhy());
        assertEquals(PHY_LE_1M, params.getSecondaryPhy());
        assertEquals(INTERVAL_LOW, params.getInterval());
        assertEquals(TX_POWER_MEDIUM, params.getTxPowerLevel());
    }

    @Test
    public void testIsConnectable() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setConnectable(true)
                .build();
        assertTrue(params.isConnectable());
    }

    @Test
    public void testIsScannable() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setScannable(true)
                .build();
        assertTrue(params.isScannable());
    }

    @Test
    public void testIsLegacyMode() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .build();
        assertTrue(params.isLegacy());
    }

    @Test
    public void testIncludeTxPower() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setIncludeTxPower(true)
                .build();
        assertTrue(params.includeTxPower());
    }

    @Test
    public void testSetPrimaryPhyWithInvalidValue() {
        try {
            // Set invalid value
            new AdvertisingSetParameters.Builder().setPrimaryPhy(PHY_LE_2M);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSetPrimaryPhyWithLE1M() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setPrimaryPhy(PHY_LE_1M)
                .build();
        assertEquals(PHY_LE_1M, params.getPrimaryPhy());
    }

    @Test
    public void testSetPrimaryPhyWithLECoded() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setPrimaryPhy(PHY_LE_CODED)
                .build();
        assertEquals(PHY_LE_CODED, params.getPrimaryPhy());
    }

    @Test
    public void testSetSecondaryPhyWithInvalidValue() {
        int INVALID_SECONDARY_PHY = -1;
        try {
            // Set invalid value
            new AdvertisingSetParameters.Builder().setSecondaryPhy(INVALID_SECONDARY_PHY);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSetSecondaryPhyWithLE1M() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setSecondaryPhy(PHY_LE_1M)
                .build();
        assertEquals(PHY_LE_1M, params.getSecondaryPhy());
    }

    @Test
    public void testSetSecondaryPhyWithLE2M() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setSecondaryPhy(PHY_LE_2M)
                .build();
        assertEquals(PHY_LE_2M, params.getSecondaryPhy());
    }

    @Test
    public void testSetSecondaryPhyWithLECoded() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setSecondaryPhy(PHY_LE_CODED)
                .build();
        assertEquals(PHY_LE_CODED, params.getSecondaryPhy());
    }

    @Test
    public void testIntervalWithInvalidValues() {
        int[] invalidValues = {INTERVAL_MIN - 1, INTERVAL_MAX + 1};
        for (int i = 0; i < invalidValues.length; i++) {
            try {
                // Set invalid value
                new AdvertisingSetParameters.Builder().setInterval(invalidValues[i]);
                fail();
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    @Test
    public void testInterval() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setInterval(INTERVAL_MEDIUM)
                .build();
        assertEquals(INTERVAL_MEDIUM, params.getInterval());
    }

    @Test
    public void testTxPowerLevelWithInvalidValues() {
        int[] invalidValues = { TX_POWER_MIN - 1, TX_POWER_MAX + 1 };
        for (int i = 0; i < invalidValues.length; i++) {
            try {
                // Set invalid value
                new AdvertisingSetParameters.Builder().setTxPowerLevel(TX_POWER_MIN - 1);
                fail();
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    @Test
    public void testTxPowerLevel() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                .setTxPowerLevel(TX_POWER_MEDIUM)
                .build();
        assertEquals(TX_POWER_MEDIUM, params.getTxPowerLevel());
    }

    @Test
    public void testIsAnonymous() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setAnonymous(true).build();
        assertTrue(params.isAnonymous());
    }

    @Test
    public void testDescribeContents() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder().build();
        assertEquals(0, params.describeContents());
    }

    private void assertParamsEquals(AdvertisingSetParameters p, AdvertisingSetParameters other) {
        if (p == null && other == null) {
            return;
        }

        if (p == null || other == null) {
            fail("Cannot compare null with non-null value: p=" + p + ", other=" + other);
        }

        assertEquals(p.isConnectable(), other.isConnectable());
        assertEquals(p.isScannable(), other.isScannable());
        assertEquals(p.isLegacy(), other.isLegacy());
        assertEquals(p.isAnonymous(), other.isAnonymous());
        assertEquals(p.includeTxPower(), other.includeTxPower());
        assertEquals(p.getPrimaryPhy(), other.getPrimaryPhy());
        assertEquals(p.getSecondaryPhy(), other.getSecondaryPhy());
        assertEquals(p.getInterval(), other.getInterval());
        assertEquals(p.getTxPowerLevel(), other.getTxPowerLevel());
    }
}

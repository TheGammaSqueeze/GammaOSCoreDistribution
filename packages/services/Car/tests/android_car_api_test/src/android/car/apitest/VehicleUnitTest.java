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

package android.car.apitest;

import static com.google.common.truth.Truth.assertThat;

import android.car.VehicleUnit;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@SmallTest
@RunWith(Parameterized.class)
public final class VehicleUnitTest {
    private final int mJavaConstantValue;
    private final int mHalConstantValue;

    public VehicleUnitTest(int javaConstantValue, int halConstantValue) {
        mJavaConstantValue = javaConstantValue;
        mHalConstantValue = halConstantValue;
    }

    @Parameterized.Parameters
    public static Collection constantValues() {
        return Arrays.asList(new Object[][]{
                {VehicleUnit.SHOULD_NOT_USE,
                        android.hardware.automotive.vehicle.VehicleUnit.SHOULD_NOT_USE},
                {VehicleUnit.METER_PER_SEC,
                        android.hardware.automotive.vehicle.VehicleUnit.METER_PER_SEC},
                {VehicleUnit.RPM, android.hardware.automotive.vehicle.VehicleUnit.RPM},
                {VehicleUnit.HERTZ, android.hardware.automotive.vehicle.VehicleUnit.HERTZ},
                {VehicleUnit.PERCENTILE,
                        android.hardware.automotive.vehicle.VehicleUnit.PERCENTILE},
                {VehicleUnit.MILLIMETER,
                        android.hardware.automotive.vehicle.VehicleUnit.MILLIMETER},
                {VehicleUnit.METER, android.hardware.automotive.vehicle.VehicleUnit.METER},
                {VehicleUnit.KILOMETER, android.hardware.automotive.vehicle.VehicleUnit.KILOMETER},
                {VehicleUnit.MILE, android.hardware.automotive.vehicle.VehicleUnit.MILE},
                {VehicleUnit.CELSIUS, android.hardware.automotive.vehicle.VehicleUnit.CELSIUS},
                {VehicleUnit.FAHRENHEIT,
                        android.hardware.automotive.vehicle.VehicleUnit.FAHRENHEIT},
                {VehicleUnit.KELVIN, android.hardware.automotive.vehicle.VehicleUnit.KELVIN},
                {VehicleUnit.MILLILITER,
                        android.hardware.automotive.vehicle.VehicleUnit.MILLILITER},
                {VehicleUnit.LITER, android.hardware.automotive.vehicle.VehicleUnit.LITER},
                {VehicleUnit.US_GALLON, android.hardware.automotive.vehicle.VehicleUnit.US_GALLON},
                {VehicleUnit.IMPERIAL_GALLON,
                        android.hardware.automotive.vehicle.VehicleUnit.IMPERIAL_GALLON},
                {VehicleUnit.NANO_SECS, android.hardware.automotive.vehicle.VehicleUnit.NANO_SECS},
                {VehicleUnit.SECS, android.hardware.automotive.vehicle.VehicleUnit.SECS},
                {VehicleUnit.YEAR, android.hardware.automotive.vehicle.VehicleUnit.YEAR},
                {VehicleUnit.WATT_HOUR, android.hardware.automotive.vehicle.VehicleUnit.WATT_HOUR},
                {VehicleUnit.MILLIAMPERE,
                        android.hardware.automotive.vehicle.VehicleUnit.MILLIAMPERE},
                {VehicleUnit.MILLIVOLT, android.hardware.automotive.vehicle.VehicleUnit.MILLIVOLT},
                {VehicleUnit.MILLIWATTS,
                        android.hardware.automotive.vehicle.VehicleUnit.MILLIWATTS},
                {VehicleUnit.AMPERE_HOURS,
                        android.hardware.automotive.vehicle.VehicleUnit.AMPERE_HOURS},
                {VehicleUnit.KILOWATT_HOUR,
                        android.hardware.automotive.vehicle.VehicleUnit.KILOWATT_HOUR},
                {VehicleUnit.KILOPASCAL,
                        android.hardware.automotive.vehicle.VehicleUnit.KILOPASCAL},
                {VehicleUnit.PSI, android.hardware.automotive.vehicle.VehicleUnit.PSI},
                {VehicleUnit.BAR, android.hardware.automotive.vehicle.VehicleUnit.BAR},
                {VehicleUnit.DEGREES, android.hardware.automotive.vehicle.VehicleUnit.DEGREES},
                {VehicleUnit.MILES_PER_HOUR,
                        android.hardware.automotive.vehicle.VehicleUnit.MILES_PER_HOUR},
                {VehicleUnit.KILOMETERS_PER_HOUR,
                        android.hardware.automotive.vehicle.VehicleUnit.KILOMETERS_PER_HOUR},
        });
    }

    @Test
    public void testMatchWithVehicleHal() {
        assertThat(mJavaConstantValue).isEqualTo(mHalConstantValue);
    }
}

/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.hal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.hardware.automotive.vehicle.VehicleProperty;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class PropertyHalServiceTest {
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private VehicleHal mVehicleHal;

    @Mock
    private HalPropConfig mHalPropConfig;

    @Mock
    private HalPropValueBuilder mHalPropValueBuilder;

    @Mock
    private HalPropValue mHalPropValue;

    @Mock
    private CarPropertyValue<?> mCarPropertyValue;

    private PropertyHalService mPropertyHalService;


    @Before
    public void setUp() {
        when(mVehicleHal.getHalPropValueBuilder()).thenReturn(mHalPropValueBuilder);
        mPropertyHalService = new PropertyHalService(mVehicleHal);
        mPropertyHalService.init();
    }

    @After
    public void tearDown() {
        mPropertyHalService.release();
        mPropertyHalService = null;
    }

    @Test
    public void isDisplayUnitsProperty_returnsTrueForAllDisplayUnitProperties() {
        for (int propId : ImmutableList.of(VehiclePropertyIds.DISTANCE_DISPLAY_UNITS,
                VehiclePropertyIds.FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME,
                VehiclePropertyIds.FUEL_VOLUME_DISPLAY_UNITS,
                VehiclePropertyIds.TIRE_PRESSURE_DISPLAY_UNITS,
                VehiclePropertyIds.EV_BATTERY_DISPLAY_UNITS,
                VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS)) {
            Assert.assertTrue(mPropertyHalService.isDisplayUnitsProperty(propId));
        }
    }

    @Test
    public void setProperty_handlesHalAndMgrPropIdMismatch() {
        when(mCarPropertyValue.getPropertyId()).thenReturn(
                VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS);
        when(mHalPropConfig.getPropId()).thenReturn(VehicleProperty.VEHICLE_SPEED_DISPLAY_UNITS);
        when(mHalPropValueBuilder.build(mCarPropertyValue,
                VehicleProperty.VEHICLE_SPEED_DISPLAY_UNITS, mHalPropConfig)).thenReturn(
                mHalPropValue);
        mPropertyHalService.takeProperties(ImmutableList.of(mHalPropConfig));

        mPropertyHalService.setProperty(mCarPropertyValue);

        verify(mHalPropValueBuilder).build(mCarPropertyValue,
                VehicleProperty.VEHICLE_SPEED_DISPLAY_UNITS, mHalPropConfig);
        verify(mVehicleHal).set(mHalPropValue);
    }
}

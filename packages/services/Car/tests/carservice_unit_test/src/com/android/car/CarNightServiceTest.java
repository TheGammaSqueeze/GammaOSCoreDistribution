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

package com.android.car;

import static android.car.hardware.CarPropertyValue.STATUS_AVAILABLE;

import static com.android.car.CarNightService.FORCED_DAY_MODE;
import static com.android.car.CarNightService.FORCED_NIGHT_MODE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.UiModeManager;
import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.hardware.automotive.vehicle.VehicleProperty;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link CarNightService}.
 */
@RunWith(MockitoJUnitRunner.class)
public class CarNightServiceTest {
    private static final CarPropertyValue<Boolean> NIGHT_MODE_PROPERTY_ENABLED =
            new CarPropertyValue<>(VehicleProperty.NIGHT_MODE, 0, STATUS_AVAILABLE, 1000, true);
    private static final CarPropertyValue<Boolean> NIGHT_MODE_PROPERTY_DISABLED =
            new CarPropertyValue<>(VehicleProperty.NIGHT_MODE, 0, STATUS_AVAILABLE, 1000, false);
    private static final CarPropertyValue<Boolean> NIGHT_MODE_PROPERTY_DISABLED_NO_TIMESTAMP =
            new CarPropertyValue<>(VehicleProperty.NIGHT_MODE, 0, STATUS_AVAILABLE, 0, false);
    private static final int INVALID_NIGHT_MODE = 100;

    @Mock
    private CarPropertyService mCarPropertyService;
    @Mock
    private Context mContext;
    @Mock
    private UiModeManager mUiModeManager;

    private CarNightService mService;

    @Before
    public void setUp() {
        when(mContext.getSystemService(Context.UI_MODE_SERVICE)).thenReturn(mUiModeManager);
        mService = new CarNightService(mContext, mCarPropertyService);
    }

    @Test
    public void onInit_setsNightModeFromProperty() {
        initServiceWithNightMode(UiModeManager.MODE_NIGHT_YES);
    }

    @Test
    public void onInit_propertyTimestampMissing_setsDefaultNightMode() {
        when(mCarPropertyService.getPropertySafe(VehicleProperty.NIGHT_MODE, 0))
                .thenReturn(NIGHT_MODE_PROPERTY_DISABLED_NO_TIMESTAMP);
        mService.init();
        verify(mUiModeManager).setNightMode(UiModeManager.MODE_NIGHT_YES);
    }

    @Test
    public void onInit_propertyMissing_setsDefaultNightMode() {
        when(mCarPropertyService.getPropertySafe(VehicleProperty.NIGHT_MODE, 0))
                .thenReturn(null);
        mService.init();
        verify(mUiModeManager).setNightMode(UiModeManager.MODE_NIGHT_YES);
    }

    @Test
    public void forceDayNightMode_setsDayMode() {
        int expectedNewMode = UiModeManager.MODE_NIGHT_YES;
        initServiceWithNightMode(UiModeManager.MODE_NIGHT_NO);

        when(mUiModeManager.getNightMode()).thenReturn(expectedNewMode);
        int updatedMode = mService.forceDayNightMode(FORCED_NIGHT_MODE);

        verify(mUiModeManager).setNightMode(expectedNewMode);
        assertThat(updatedMode).isEqualTo(expectedNewMode);
    }

    @Test
    public void forceDayNightMode_setsNightMode() {
        int expectedNewMode = UiModeManager.MODE_NIGHT_NO;
        initServiceWithNightMode(UiModeManager.MODE_NIGHT_YES);

        when(mUiModeManager.getNightMode()).thenReturn(expectedNewMode);
        int updatedMode = mService.forceDayNightMode(FORCED_DAY_MODE);

        verify(mUiModeManager).setNightMode(expectedNewMode);
        assertThat(updatedMode).isEqualTo(expectedNewMode);
    }

    @Test
    public void forceDayNightMode_invalidMode() {
        initServiceWithNightMode(UiModeManager.MODE_NIGHT_YES);

        int updatedMode = mService.forceDayNightMode(INVALID_NIGHT_MODE);

        verifyNoMoreInteractions(mUiModeManager);
        assertThat(updatedMode).isEqualTo(-1);
    }

    private void initServiceWithNightMode(int mode) {
        when(mCarPropertyService.getPropertySafe(VehicleProperty.NIGHT_MODE, 0))
                .thenReturn(mode == UiModeManager.MODE_NIGHT_YES
                        ? NIGHT_MODE_PROPERTY_ENABLED
                        : NIGHT_MODE_PROPERTY_DISABLED);
        mService.init();
        verify(mUiModeManager).setNightMode(mode);
    }
}

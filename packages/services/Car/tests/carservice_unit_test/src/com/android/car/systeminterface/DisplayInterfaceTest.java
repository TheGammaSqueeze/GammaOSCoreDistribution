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

package com.android.car.systeminterface;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.view.Display;

import com.android.car.power.CarPowerManagementService;
import com.android.car.user.CarUserService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class DisplayInterfaceTest {

    @Mock
    private Context mContext;

    @Mock
    private WakeLockInterface mWakeLockInterface;

    @Mock
    private CarPowerManagementService mCarPowerManagementService;

    @Mock
    private DisplayManager mDisplayManager;

    @Mock
    private ContentResolver mContentResolver;

    @Mock
    private CarUserService mCarUserService;

    @Mock
    private PowerManager mPowerManager;

    @Mock
    private Display mDisplay;

    private DisplayInterface.DefaultImpl mDisplayInterface;

    @Before
    public void setUp() {
        when(mContext.createContextAsUser(any(), anyInt())).thenReturn(mContext);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContext.getSystemService(DisplayManager.class)).thenReturn(mDisplayManager);
        when(mContext.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        when(mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY)).thenReturn(mDisplay);

        mDisplayInterface = new DisplayInterface.DefaultImpl(mContext, mWakeLockInterface) {
            @Override
            public void refreshDisplayBrightness() {
            }
        };
        mDisplayInterface.init(mCarPowerManagementService, mCarUserService);
    }

    @Test
    public void testStartDisplayStateMonitoring() {
        when(mDisplayManager.getDisplay(anyInt())).thenReturn(mDisplay);
        when(mDisplay.getState()).thenReturn(Display.STATE_ON);

        mDisplayInterface.startDisplayStateMonitoring();

        verify(mContentResolver).registerContentObserver(any(), eq(false), any());
        verify(mDisplayManager).registerDisplayListener(any(), isNull());
        verify(mCarUserService).addUserLifecycleListener(any(), any());
    }

    @Test
    public void testStopDisplayStateMonitoring() {
        mDisplayInterface.startDisplayStateMonitoring();
        mDisplayInterface.stopDisplayStateMonitoring();

        verify(mDisplayManager).unregisterDisplayListener(any());
        verify(mContentResolver).unregisterContentObserver(any());
        verify(mCarUserService).removeUserLifecycleListener(any());
    }
}

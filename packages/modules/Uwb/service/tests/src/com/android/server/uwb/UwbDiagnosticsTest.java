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

package com.android.server.uwb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.BugreportManager;
import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.uwb.UwbDiagnostics}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbDiagnosticsTest {
    @Mock SystemBuildProperties mBuildProperties;
    @Mock Context mContext;
    @Mock UwbInjector mUwbInjector;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock BugreportManager mBugreportManager;
    UwbDiagnostics mUwbDiagnostics;

    private static final int BUG_REPORT_MIN_INTERVAL_MS = 3600_000;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mUwbInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.getBugReportMinIntervalMs())
                .thenReturn(BUG_REPORT_MIN_INTERVAL_MS);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        when(mContext.getSystemService(BugreportManager.class)).thenReturn(mBugreportManager);
        mUwbDiagnostics = new UwbDiagnostics(mContext, mUwbInjector, mBuildProperties);
    }

    @Test
    public void takeBugReportDoesNothingOnUserBuild() throws Exception {
        when(mBuildProperties.isUserBuild()).thenReturn(true);
        mUwbDiagnostics.takeBugReport("");
        verify(mBugreportManager, never()).requestBugreport(any(), any(), any());
    }

    @Test
    public void takeBugReportTwiceWithInsufficientTimeGapSkipSecondRequest() throws Exception {
        // 1st attempt should succeed
        when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(10L);
        mUwbDiagnostics.takeBugReport("");
        verify(mBugreportManager, times(1)).requestBugreport(any(), any(), any());
        // 2nd attempt should fail
        when(mUwbInjector.getWallClockMillis()).thenReturn(BUG_REPORT_MIN_INTERVAL_MS - 20L);
        mUwbDiagnostics.takeBugReport("");
        verify(mBugreportManager, times(1)).requestBugreport(any(), any(), any());
    }
}

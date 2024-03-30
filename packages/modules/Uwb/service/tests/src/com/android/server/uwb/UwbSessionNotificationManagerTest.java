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

package com.android.server.uwb;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.RangingChangeReason;
import android.uwb.RangingReport;
import android.uwb.SessionHandle;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbUciConstants;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.uwb.UwbSettingsStore}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbSessionNotificationManagerTest {
    private static final long TEST_ELAPSED_NANOS = 100L;
    private static final int UID = 343453;
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();

    @Mock private UwbInjector mUwbInjector;
    @Mock private UwbSessionManager.UwbSession mUwbSession;
    @Mock private SessionHandle mSessionHandle;
    @Mock private IUwbRangingCallbacks mIUwbRangingCallbacks;
    @Mock private FiraOpenSessionParams mFiraParams;

    private UwbSessionNotificationManager mUwbSessionNotificationManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mUwbSession.getSessionHandle()).thenReturn(mSessionHandle);
        when(mUwbSession.getIUwbRangingCallbacks()).thenReturn(mIUwbRangingCallbacks);
        when(mUwbSession.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);
        when(mUwbSession.getParams()).thenReturn(mFiraParams);
        when(mUwbSession.getAttributionSource()).thenReturn(ATTRIBUTION_SOURCE);
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS);
        when(mFiraParams.hasResultReportPhase()).thenReturn(false);
        when(mUwbInjector.checkUwbRangingPermissionForDataDelivery(any(), any())).thenReturn(true);
        when(mUwbInjector.getElapsedSinceBootNanos()).thenReturn(TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager = new UwbSessionNotificationManager(mUwbInjector);
    }

    /**
     * Called after each testGG
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    @Test
    public void testOnRangingResultWithoutUwbRangingPermission() throws Exception {
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        true, true, false, false, TEST_ELAPSED_NANOS);
        when(mUwbInjector.checkUwbRangingPermissionForDataDelivery(eq(ATTRIBUTION_SOURCE), any()))
                .thenReturn(false);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);

        verify(mIUwbRangingCallbacks, never()).onRangingResult(any(), any());
    }

    @Test
    public void testOnRangingResultWithAoa() throws Exception {
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        true, true, false, false, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
    }

    @Test
    public void testOnRangingResultWithNoAoa() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        false, false, false, false, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
    }

    @Test
    public void testOnRangingResultWithNoAoaElevation() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_AZIMUTH_ONLY);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        true, false, false, false, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
    }

    @Test
    public void testOnRangingResultWithNoAoaAzimuth() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_ELEVATION_ONLY);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        false, true, false, false, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
    }
  
    @Test
    public void testOnRangingResultWithAoaAndDestAoa() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS);
        when(mFiraParams.hasResultReportPhase()).thenReturn(true);
        when(mFiraParams.hasAngleOfArrivalAzimuthReport()).thenReturn(true);
        when(mFiraParams.hasAngleOfArrivalElevationReport()).thenReturn(true);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        true, true, true, true, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
    }


    @Test
    public void testOnRangingOpened() throws Exception {
        mUwbSessionNotificationManager.onRangingOpened(mUwbSession);

        verify(mIUwbRangingCallbacks).onRangingOpened(mSessionHandle);
    }

    @Test
    public void testOnRangingOpenFailed() throws Exception {
        int status = UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED;
        mUwbSessionNotificationManager.onRangingOpenFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingOpenFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingStarted() throws Exception {
        mUwbSessionNotificationManager.onRangingStarted(mUwbSession, mUwbSession.getParams());

        verify(mIUwbRangingCallbacks).onRangingStarted(mSessionHandle,
                mUwbSession.getParams().toBundle());
    }

    @Test
    public void testOnRangingStartFailed() throws Exception {
        int status =  UwbUciConstants.STATUS_CODE_INVALID_PARAM;
        mUwbSessionNotificationManager.onRangingStartFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingStartFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingStopped() throws Exception {
        int status = UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS;
        mUwbSessionNotificationManager.onRangingStopped(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingStopped(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p-> p.getInt("status_code") == status));
    }

    @Test
    public void testORangingStopFailed() throws Exception {
        int status = UwbUciConstants.STATUS_CODE_INVALID_RANGE;
        mUwbSessionNotificationManager.onRangingStopFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingStopFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingReconfigured() throws Exception {
        mUwbSessionNotificationManager.onRangingReconfigured(mUwbSession);

        verify(mIUwbRangingCallbacks).onRangingReconfigured(eq(mSessionHandle), any());
    }

    @Test
    public void testOnRangingReconfigureFailed() throws Exception {
        int status =  UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE;
        mUwbSessionNotificationManager.onRangingReconfigureFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingReconfigureFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnControleeAdded() throws Exception {
        mUwbSessionNotificationManager.onControleeAdded(mUwbSession);

        verify(mIUwbRangingCallbacks).onControleeAdded(eq(mSessionHandle), any());
    }

    @Test
    public void testOnControleeAddFailed() throws Exception {
        int status =  UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE;
        mUwbSessionNotificationManager.onControleeAddFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onControleeAddFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnControleeRemoved() throws Exception {
        mUwbSessionNotificationManager.onControleeRemoved(mUwbSession);

        verify(mIUwbRangingCallbacks).onControleeRemoved(eq(mSessionHandle), any());
    }

    @Test
    public void testOnControleeRemoveFailed() throws Exception {
        int status =  UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE;
        mUwbSessionNotificationManager.onControleeRemoveFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onControleeRemoveFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingClosed() throws Exception {
        int status = UwbUciConstants.REASON_ERROR_SLOT_LENGTH_NOT_SUPPORTED;
        mUwbSessionNotificationManager.onRangingClosed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingClosed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p-> p.getInt("status_code") == status));
    }

    @Test
    public void testOnRangingClosedWithReasonCode() throws Exception {
        int reasonCode = RangingChangeReason.SYSTEM_POLICY;
        mUwbSessionNotificationManager.onRangingClosedWithApiReasonCode(mUwbSession, reasonCode);

        verify(mIUwbRangingCallbacks).onRangingClosed(eq(mSessionHandle),
                eq(reasonCode),
                argThat(p-> p.isEmpty()));
    }
}

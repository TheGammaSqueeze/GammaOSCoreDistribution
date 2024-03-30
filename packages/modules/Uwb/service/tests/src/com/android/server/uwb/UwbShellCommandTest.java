/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.uwb.RangingSession.Callback.REASON_LOCAL_REQUEST;

import static com.android.server.uwb.UwbShellCommand.DEFAULT_CCC_OPEN_RANGING_PARAMS;
import static com.android.server.uwb.UwbShellCommand.DEFAULT_FIRA_OPEN_SESSION_PARAMS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.os.PersistableBundle;
import android.os.Process;
import android.util.Pair;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.RangingMeasurement;
import android.uwb.RangingReport;
import android.uwb.SessionHandle;
import android.uwb.UwbManager;
import android.uwb.UwbTestUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccStartRangingParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;

/**
 * Unit tests for {@link com.android.server.uwb.UwbShellCommand}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class UwbShellCommandTest {
    private static final String TEST_PACKAGE = "com.android.test";

    @Mock UwbInjector mUwbInjector;
    @Mock UwbServiceImpl mUwbService;
    @Mock UwbCountryCode mUwbCountryCode;
    @Mock Context mContext;

    UwbShellCommand mUwbShellCommand;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mUwbInjector.getUwbCountryCode()).thenReturn(mUwbCountryCode);

        mUwbShellCommand = new UwbShellCommand(mUwbInjector, mUwbService, mContext);

        // by default emulate shell uid.
        BinderUtil.setUid(Process.SHELL_UID);
    }

    @After
    public void tearDown() throws Exception {
        mUwbShellCommand.reset();
        validateMockitoUsage();
    }

    @Test
    public void testStatus() throws Exception {
        when(mUwbService.getAdapterState())
                .thenReturn(UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE);

        // unrooted shell.
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"status"});
        verify(mUwbService).getAdapterState();
    }

    @Test
    public void testForceSetCountryCode() throws Exception {
        // not allowed for unrooted shell.
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-country-code", "enabled", "US"});
        verify(mUwbCountryCode, never()).setOverrideCountryCode(any());
        assertThat(mUwbShellCommand.getErrPrintWriter().toString().isEmpty()).isFalse();

        BinderUtil.setUid(Process.ROOT_UID);

        // rooted shell.
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-country-code", "enabled", "US"});
        verify(mUwbCountryCode).setOverrideCountryCode(any());

    }

    @Test
    public void testForceClearCountryCode() throws Exception {
        // not allowed for unrooted shell.
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-country-code", "disabled"});
        verify(mUwbCountryCode, never()).setOverrideCountryCode(any());
        assertThat(mUwbShellCommand.getErrPrintWriter().toString().isEmpty()).isFalse();

        BinderUtil.setUid(Process.ROOT_UID);

        // rooted shell.
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-country-code", "disabled"});
        verify(mUwbCountryCode).clearOverrideCountryCode();
    }

    @Test
    public void testGetCountryCode() throws Exception {
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"get-country-code"});
        verify(mUwbCountryCode).getCountryCode();
    }

    @Test
    public void testEnableUwb() throws Exception {
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"enable-uwb"});
        verify(mUwbService).setEnabled(true);
    }

    @Test
    public void testDisableUwb() throws Exception {
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"disable-uwb"});
        verify(mUwbService).setEnabled(false);
    }

    private static class MutableCb {
        @Nullable public IUwbRangingCallbacks cb;
    }

    private Pair<IUwbRangingCallbacks, SessionHandle> triggerAndVerifyRangingStart(
            String[] rangingStartCmd, @NonNull Params openRangingParams) throws Exception {
        return triggerAndVerifyRangingStart(rangingStartCmd, openRangingParams, null);
    }

    private Pair<IUwbRangingCallbacks, SessionHandle> triggerAndVerifyRangingStart(
            String[] rangingStartCmd, @NonNull Params openRangingParams, @Nullable Params
            startRangingParams) throws Exception {
        final MutableCb cbCaptor = new MutableCb();
        doAnswer(invocation -> {
            cbCaptor.cb = invocation.getArgument(2);
            cbCaptor.cb.onRangingOpened(invocation.getArgument(1));
            return true;
        }).when(mUwbService).openRanging(any(), any(), any(), any(), any());
        doAnswer(invocation -> {
            cbCaptor.cb.onRangingStarted(invocation.getArgument(0), new PersistableBundle());
            return true;
        }).when(mUwbService).startRanging(any(), any());

        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                rangingStartCmd);

        ArgumentCaptor<SessionHandle> sessionHandleCaptor =
                ArgumentCaptor.forClass(SessionHandle.class);
        ArgumentCaptor<PersistableBundle> paramsCaptor =
                ArgumentCaptor.forClass(PersistableBundle.class);

        verify(mUwbService).openRanging(
                eq(new AttributionSource.Builder(Process.SHELL_UID)
                        .setPackageName(UwbShellCommand.SHELL_PACKAGE_NAME)
                        .build()),
                sessionHandleCaptor.capture(), any(), paramsCaptor.capture(), any());
        // PersistableBundle does not implement equals, so use toString equals.
        assertThat(paramsCaptor.getValue().toString())
                .isEqualTo(openRangingParams.toBundle().toString());

        verify(mUwbService).startRanging(
                eq(sessionHandleCaptor.getValue()), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().toString())
                .isEqualTo(startRangingParams != null
                        ? startRangingParams.toBundle().toString()
                        : new PersistableBundle().toString());

        return Pair.create(cbCaptor.cb, sessionHandleCaptor.getValue());
    }

    private void triggerAndVerifyRangingStop(
            String[] rangingStopCmd, IUwbRangingCallbacks cb, SessionHandle sessionHandle)
            throws Exception {
        doAnswer(invocation -> {
            cb.onRangingStopped(sessionHandle, REASON_LOCAL_REQUEST, new PersistableBundle());
            return true;
        }).when(mUwbService).stopRanging(any());
        doAnswer(invocation -> {
            cb.onRangingClosed(
                    sessionHandle, REASON_LOCAL_REQUEST,
                    new PersistableBundle());
            return true;
        }).when(mUwbService).closeRanging(any());

        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                rangingStopCmd);

        verify(mUwbService).stopRanging(sessionHandle);
        verify(mUwbService).closeRanging(sessionHandle);
    }

    private CccStartRangingParams getCccStartRangingParamsFromOpenRangingParams(
            @NonNull CccOpenRangingParams openRangingParams) {
        return new CccStartRangingParams.Builder()
                .setSessionId(openRangingParams.getSessionId())
                .setRanMultiplier(openRangingParams.getRanMultiplier())
                .build();
    }

    @Test
    public void testStartFiraRanging() throws Exception {
        triggerAndVerifyRangingStart(
                new String[]{"start-fira-ranging-session"},
                DEFAULT_FIRA_OPEN_SESSION_PARAMS.build());
    }

    @Test
    public void testStartFiraRangingWithNonDefaultParams() throws Exception {
        FiraOpenSessionParams.Builder openSessionParamsBuilder =
                new FiraOpenSessionParams.Builder(DEFAULT_FIRA_OPEN_SESSION_PARAMS);
        openSessionParamsBuilder.setSessionId(5);
        triggerAndVerifyRangingStart(
                new String[]{"start-fira-ranging-session", "-i", "5"},
                openSessionParamsBuilder.build());
    }

    @Test
    public void testStartFiraRangingWithBothInterleavingAndAoaResultReq() throws Exception {
        // Both AOA result req and interleaving are not allowed in the same command.
        assertThat(mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"start-fira-ranging-session", "-i", "5", "-z", "4,5,6", "-e",
                        "enabled"})).isEqualTo(-1);
    }

    private RangingMeasurement getRangingMeasurement() {
        return new RangingMeasurement.Builder()
                .setStatus(RangingMeasurement.RANGING_STATUS_SUCCESS)
                .setElapsedRealtimeNanos(67)
                .setDistanceMeasurement(UwbTestUtils.getDistanceMeasurement())
                .setAngleOfArrivalMeasurement(UwbTestUtils.getAngleOfArrivalMeasurement())
                .setRemoteDeviceAddress(UwbTestUtils.getUwbAddress(true))
                .build();
    }

    @Test
    public void testRangingReportFiraRanging() throws Exception {
        Pair<IUwbRangingCallbacks, SessionHandle> cbAndSessionHandle =
                triggerAndVerifyRangingStart(
                        new String[]{"start-fira-ranging-session"},
                        DEFAULT_FIRA_OPEN_SESSION_PARAMS.build());
        int sessionId = DEFAULT_FIRA_OPEN_SESSION_PARAMS.build().getSessionId();
        cbAndSessionHandle.first.onRangingResult(
                cbAndSessionHandle.second,
                new RangingReport.Builder().addMeasurement(getRangingMeasurement()).build());
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"get-ranging-session-reports", String.valueOf(sessionId)});
    }

    @Test
    public void testRangingReportAllFiraRanging() throws Exception {
        Pair<IUwbRangingCallbacks, SessionHandle> cbAndSessionHandle =
                triggerAndVerifyRangingStart(
                        new String[]{"start-fira-ranging-session"},
                        DEFAULT_FIRA_OPEN_SESSION_PARAMS.build());
        cbAndSessionHandle.first.onRangingResult(
                cbAndSessionHandle.second,
                new RangingReport.Builder().addMeasurement(getRangingMeasurement()).build());
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"get-all-ranging-session-reports"});
    }

    @Test
    public void testStopFiraRanging() throws Exception {
        Pair<IUwbRangingCallbacks, SessionHandle> cbAndSessionHandle =
                triggerAndVerifyRangingStart(
                        new String[]{"start-fira-ranging-session"},
                        DEFAULT_FIRA_OPEN_SESSION_PARAMS.build());
        int sessionId = DEFAULT_FIRA_OPEN_SESSION_PARAMS.build().getSessionId();
        triggerAndVerifyRangingStop(
                new String[]{"stop-ranging-session", String.valueOf(sessionId)},
                cbAndSessionHandle.first, cbAndSessionHandle.second);
    }

    @Test
    public void testStartCccRanging() throws Exception {
        CccOpenRangingParams openSessionParams = DEFAULT_CCC_OPEN_RANGING_PARAMS.build();
        triggerAndVerifyRangingStart(
                new String[]{"start-ccc-ranging-session"},
                openSessionParams,
                getCccStartRangingParamsFromOpenRangingParams(openSessionParams));
    }

    @Test
    public void testStartCccRangingWithNonDefaultParams() throws Exception {
        CccOpenRangingParams.Builder openSessionParamsBuilder =
                new CccOpenRangingParams.Builder(DEFAULT_CCC_OPEN_RANGING_PARAMS);
        openSessionParamsBuilder.setSessionId(5);
        CccOpenRangingParams openSessionParams = openSessionParamsBuilder.build();
        triggerAndVerifyRangingStart(
                new String[]{"start-ccc-ranging-session", "-i", "5"},
                openSessionParams,
                getCccStartRangingParamsFromOpenRangingParams(openSessionParams));
    }

    @Test
    public void testStopCccRanging() throws Exception {
        CccOpenRangingParams openSessionParams = DEFAULT_CCC_OPEN_RANGING_PARAMS.build();
        Pair<IUwbRangingCallbacks, SessionHandle> cbAndSessionHandle =
                triggerAndVerifyRangingStart(
                        new String[]{"start-ccc-ranging-session"},
                        openSessionParams,
                        getCccStartRangingParamsFromOpenRangingParams(openSessionParams));
        int sessionId = openSessionParams.getSessionId();
        triggerAndVerifyRangingStop(
                new String[]{"stop-ranging-session", String.valueOf(sessionId)},
                cbAndSessionHandle.first, cbAndSessionHandle.second);
    }

    @Test
    public void testStopAllRanging() throws Exception {
        CccOpenRangingParams openSessionParams = DEFAULT_CCC_OPEN_RANGING_PARAMS.build();
        Pair<IUwbRangingCallbacks, SessionHandle> cbAndSessionHandle =
                triggerAndVerifyRangingStart(
                        new String[]{"start-ccc-ranging-session"},
                        openSessionParams,
                        getCccStartRangingParamsFromOpenRangingParams(openSessionParams));
        triggerAndVerifyRangingStop(
                new String[]{"stop-all-ranging-sessions"},
                cbAndSessionHandle.first, cbAndSessionHandle.second);
    }
}

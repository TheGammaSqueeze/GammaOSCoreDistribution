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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.AttributionSource;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.RangingChangeReason;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.uwb.UwbSessionManager.UwbSession;
import com.android.server.uwb.UwbSessionManager.WaitObj;
import com.android.server.uwb.data.UwbMulticastListUpdateStatus;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.NativeUwbManager;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccStartRangingParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

public class UwbSessionManagerTest {
    private static final int TEST_SESSION_ID = 7;
    private static final int MAX_SESSION_NUM = 8;
    private static final int UID = 343453;
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();

    @Mock
    private UwbConfigurationManager mUwbConfigurationManager;
    @Mock
    private NativeUwbManager mNativeUwbManager;
    @Mock
    private UwbMetrics mUwbMetrics;
    @Mock
    private UwbSessionNotificationManager mUwbSessionNotificationManager;
    @Mock
    private UwbInjector mUwbInjector;
    @Mock
    private ExecutorService mExecutorService;
    @Mock
    private AlarmManager mAlarmManager;
    private TestLooper mTestLooper = new TestLooper();
    private UwbSessionManager mUwbSessionManager;
    private MockitoSession mMockitoSession;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mNativeUwbManager.getMaxSessionNumber()).thenReturn(MAX_SESSION_NUM);

        // TODO: Don't use spy.
        mUwbSessionManager = spy(new UwbSessionManager(
                mUwbConfigurationManager,
                mNativeUwbManager,
                mUwbMetrics,
                mUwbSessionNotificationManager,
                mUwbInjector,
                mAlarmManager,
                mTestLooper.getLooper()));

        // static mocking for executor service.
        mMockitoSession = ExtendedMockito.mockitoSession()
                .mockStatic(Executors.class, Mockito.withSettings().lenient())
                .strictness(Strictness.LENIENT)
                .startMocking();

        doAnswer(invocation -> {
            FutureTask t = invocation.getArgument(0);
            t.run();
            return t;
        }).when(mExecutorService).submit(any(Runnable.class));
        when(Executors.newSingleThreadExecutor()).thenReturn(mExecutorService);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void onRangeDataNotificationReceivedWithValidUwbSession() {
        UwbRangingData uwbRangingData =
                UwbTestUtils.generateRangingData(UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(uwbRangingData));
    }

    @Test
    public void onRangeDataNotificationReceivedWithInvalidSession() {
        UwbRangingData uwbRangingData =
                UwbTestUtils.generateRangingData(UwbUciConstants.STATUS_CODE_OK);
        doReturn(null)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager, never())
                .onRangingResult(any(), eq(uwbRangingData));
        verify(mUwbMetrics, never()).logRangingResult(anyInt(), eq(uwbRangingData));
    }

    @Test
    public void onMulticastListUpdateNotificationReceivedWithValidSession() {
        UwbMulticastListUpdateStatus mockUwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(anyInt());

        mUwbSessionManager.onMulticastListUpdateNotificationReceived(
                mockUwbMulticastListUpdateStatus);

        verify(mockUwbSession, times(2)).getWaitObj();
        verify(mockUwbSession)
                .setMulticastListUpdateStatus(eq(mockUwbMulticastListUpdateStatus));
    }

    @Test
    public void onSessionStatusNotificationReceived_max_retry() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getSessionState()).thenReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);

        mUwbSessionManager.onSessionStatusNotificationReceived(
                TEST_SESSION_ID,
                UwbUciConstants.UWB_SESSION_STATE_IDLE,
                UwbUciConstants.REASON_MAX_RANGING_ROUND_RETRY_COUNT_REACHED);

        verify(mockUwbSession, times(2)).getWaitObj();
        verify(mockUwbSession).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_IDLE));
        verify(mUwbSessionNotificationManager).onRangingStoppedWithUciReasonCode(
                eq(mockUwbSession),
                eq(UwbUciConstants.REASON_MAX_RANGING_ROUND_RETRY_COUNT_REACHED));
    }

    @Test
    public void onSessionStatusNotificationReceived_session_mgmt_cmds() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getSessionState()).thenReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);

        mUwbSessionManager.onSessionStatusNotificationReceived(
                TEST_SESSION_ID,
                UwbUciConstants.UWB_SESSION_STATE_IDLE,
                UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS);

        verify(mockUwbSession, times(2)).getWaitObj();
        verify(mockUwbSession).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_IDLE));
        verify(mUwbSessionNotificationManager, never()).onRangingStoppedWithUciReasonCode(
                any(), anyInt());
    }

    @Test
    public void initSession_ExistedSession() throws RemoteException {
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        doReturn(true).when(mUwbSessionManager).isExistedSession(anyInt());

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mock(SessionHandle.class),
                TEST_SESSION_ID, "any", mock(Params.class), mockRangingCallbacks);

        verify(mockRangingCallbacks).onRangingOpenFailed(
                any(), eq(RangingChangeReason.BAD_PARAMETERS), any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initSession_maxSession() throws RemoteException {
        doReturn(MAX_SESSION_NUM).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mock(SessionHandle.class),
                TEST_SESSION_ID, "any", mock(Params.class), mockRangingCallbacks);

        verify(mockRangingCallbacks).onRangingOpenFailed(any(), anyInt(), any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initSession_UwbSession_RemoteException() throws RemoteException {
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        Params mockParams = mock(FiraParams.class);
        IBinder mockBinder = mock(IBinder.class);
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, FiraParams.PROTOCOL_NAME, mockParams,
                        mockRangingCallbacks));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyString(), any(), any());
        doThrow(new RemoteException()).when(mockBinder).linkToDeath(any(), anyInt());

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mockSessionHandle, TEST_SESSION_ID,
                FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks);

        verify(uwbSession).binderDied();
        verify(mockRangingCallbacks).onRangingOpenFailed(any(), anyInt(), any());
        verify(mockBinder, atLeast(1)).unlinkToDeath(any(), anyInt());
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initSession_success() throws RemoteException {
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        Params mockParams = mock(FiraParams.class);
        IBinder mockBinder = mock(IBinder.class);
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, FiraParams.PROTOCOL_NAME, mockParams,
                        mockRangingCallbacks));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyString(), any(), any());

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mockSessionHandle, TEST_SESSION_ID,
                FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks);

        verify(uwbSession, never()).binderDied();
        verify(mockRangingCallbacks, never()).onRangingOpenFailed(any(), anyInt(), any());
        verify(mockBinder, never()).unlinkToDeath(any(), anyInt());
        assertThat(mUwbSessionManager.getUwbSession(TEST_SESSION_ID)).isEqualTo(uwbSession);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(1); // SESSION_OPEN_RANGING
    }

    @Test
    public void deInitSession_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());

        mUwbSessionManager.deInitSession(mock(SessionHandle.class));

        verify(mUwbSessionManager, never()).getSessionId(any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void deInitSession_success() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());

        mUwbSessionManager.deInitSession(mock(SessionHandle.class));

        verify(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));
        assertThat(mTestLooper.nextMessage().what).isEqualTo(5); // SESSION_CLOSE
    }

    @Test
    public void startRanging_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void startRanging_currentSessionStateIdle() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        UwbSession uwbSession = mock(UwbSession.class);
        when(uwbSession.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);
        doReturn(uwbSession).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        assertThat(mTestLooper.nextMessage().what).isEqualTo(2); // SESSION_START_RANGING
    }

    @Test
    public void startRanging_currentSessionStateActive() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        UwbSession mockUwbSession = mock(UwbSession.class);
        doReturn(mockUwbSession).when(mUwbSessionManager).getUwbSession(anyInt());
        when(mockUwbSession.getProtocolName()).thenReturn(CccParams.PROTOCOL_NAME);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                any(), eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void startRanging_currentSessiionStateInvalid() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        verify(mUwbSessionNotificationManager)
                .onRangingStartFailed(any(), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void stopRanging_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void stopRanging_currentSessionStateActive() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        assertThat(mTestLooper.nextMessage().what).isEqualTo(3); // SESSION_STOP_RANGING
    }

    @Test
    public void stopRanging_currentSessionStateIdle() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        verify(mUwbSessionNotificationManager).onRangingStopped(any(),
                eq(UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS));
    }

    @Test
    public void stopRanging_currentSessionStateInvalid() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        verify(mUwbSessionNotificationManager).onRangingStopFailed(any(),
                eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void getUwbSession_success() {
        UwbSession expectedUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, expectedUwbSession);

        UwbSession actualUwbSession = mUwbSessionManager.getUwbSession(TEST_SESSION_ID);

        assertThat(actualUwbSession).isEqualTo(expectedUwbSession);
    }

    @Test
    public void getUwbSession_failed() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);

        UwbSession actualUwbSession = mUwbSessionManager.getUwbSession(TEST_SESSION_ID - 1);

        assertThat(actualUwbSession).isNull();
    }

    @Test
    public void getSessionId_success() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        when(mockUwbSession.getSessionHandle()).thenReturn(mockSessionHandle);

        int actualSessionId = mUwbSessionManager.getSessionId(mockSessionHandle);

        assertThat(actualSessionId).isEqualTo(TEST_SESSION_ID);
    }

    @Test
    public void getSessionId_failed() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        when(mockUwbSession.getSessionHandle()).thenReturn(mockSessionHandle);

        Integer actualSessionId = mUwbSessionManager.getSessionId(mock(SessionHandle.class));

        assertThat(actualSessionId).isNull();
    }

    @Test
    public void isExistedSession_sessionHandle_success() {
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());

        boolean result = mUwbSessionManager.isExistedSession(mock(SessionHandle.class));

        assertThat(result).isTrue();
    }

    @Test
    public void iexExistedSession_sessionHandle_failed() {
        doReturn(null).when(mUwbSessionManager).getSessionId(any());

        boolean result = mUwbSessionManager.isExistedSession(mock(SessionHandle.class));

        assertThat(result).isFalse();
    }

    @Test
    public void isExistedSession_sessionId_success() {
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mock(UwbSession.class));

        boolean result = mUwbSessionManager.isExistedSession(TEST_SESSION_ID);

        assertThat(result).isTrue();
    }

    @Test
    public void iexExistedSession_sessionId_failed() {
        boolean result = mUwbSessionManager.isExistedSession(TEST_SESSION_ID);

        assertThat(result).isFalse();
    }

    @Test
    public void stopAllRanging() {
        UwbSession mockUwbSession1 = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession1);
        UwbSession mockUwbSession2 = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID + 100, mockUwbSession2);
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID + 100)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.stopAllRanging();

        verify(mNativeUwbManager, times(2)).stopRanging(anyInt());
        verify(mockUwbSession1, never()).setSessionState(anyInt());
        verify(mockUwbSession2).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_IDLE));
    }

    @Test
    public void deinitAllSession() {
        UwbSession mockUwbSession1 = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession1);
        when(mockUwbSession1.getBinder()).thenReturn(mock(IBinder.class));
        when(mockUwbSession1.getSessionId()).thenReturn(TEST_SESSION_ID);
        UwbSession mockUwbSession2 = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID + 100, mockUwbSession2);
        when(mockUwbSession2.getBinder()).thenReturn(mock(IBinder.class));
        when(mockUwbSession2.getSessionId()).thenReturn(TEST_SESSION_ID + 100);

        mUwbSessionManager.deinitAllSession();

        verify(mUwbSessionNotificationManager, times(2))
                .onRangingClosedWithApiReasonCode(any(), eq(RangingChangeReason.SYSTEM_POLICY));
        verify(mUwbSessionManager, times(2)).removeSession(any());
        // TODO: enable it when the resetDevice is enabled.
        // verify(mNativeUwbManager).resetDevice(eq(UwbUciConstants.UWBS_RESET));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
    }

    @Test
    public void setCurrentSessionState() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);

        mUwbSessionManager.setCurrentSessionState(
                TEST_SESSION_ID, UwbUciConstants.UWB_SESSION_STATE_ACTIVE);

        verify(mockUwbSession).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_ACTIVE));
    }

    @Test
    public void getCurrentSessionState_nullSession() {
        int actualStatus = mUwbSessionManager.getCurrentSessionState(TEST_SESSION_ID);

        assertThat(actualStatus).isEqualTo(UwbUciConstants.UWB_SESSION_STATE_ERROR);
    }

    @Test
    public void getCurrentSessionState_success() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);
        when(mockUwbSession.getSessionState()).thenReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);

        int actualStatus = mUwbSessionManager.getCurrentSessionState(TEST_SESSION_ID);

        assertThat(actualStatus).isEqualTo(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);
    }

    @Test
    public void getSessionIdSet() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);

        Set<Integer> actualSessionIds = mUwbSessionManager.getSessionIdSet();

        assertThat(actualSessionIds).hasSize(1);
        assertThat(actualSessionIds.contains(TEST_SESSION_ID)).isTrue();
    }

    @Test
    public void reconfigure_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());

        int actualStatus = mUwbSessionManager.reconfigure(
                mock(SessionHandle.class), mock(Params.class));

        assertThat(actualStatus).isEqualTo(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST);
    }

    @Test
    public void reconfigure_calledSuccess() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        FiraRangingReconfigureParams params =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(10)
                        .setRangeDataNtfConfig(1)
                        .setRangeDataProximityFar(10)
                        .setRangeDataProximityNear(2)
                        .build();

        int actualStatus = mUwbSessionManager.reconfigure(mock(SessionHandle.class), params);

        assertThat(actualStatus).isEqualTo(0);
        assertThat(mTestLooper.nextMessage().what)
                .isEqualTo(4); // SESSION_RECONFIG_RANGING
    }

    private UwbSession setUpUwbSessionForExecution() throws RemoteException {
        // setup message
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        Params params = new FiraOpenSessionParams.Builder()
                .setDeviceAddress(UwbAddress.fromBytes(new byte[] {(byte) 0x01, (byte) 0x02 }))
                .setVendorId(new byte[] { (byte) 0x00, (byte) 0x01 })
                .setStaticStsIV(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                        (byte) 0x04, (byte) 0x05, (byte) 0x06 })
                .setDestAddressList(Arrays.asList(
                        UwbAddress.fromBytes(new byte[] {(byte) 0x03, (byte) 0x04 })))
                .setProtocolVersion(new FiraProtocolVersion(1, 0))
                .setSessionId(10)
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .build();
        IBinder mockBinder = mock(IBinder.class);
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, FiraParams.PROTOCOL_NAME, params, mockRangingCallbacks));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyString(), any(), any());
        doReturn(mock(WaitObj.class)).when(uwbSession).getWaitObj();

        return uwbSession;
    }

    private UwbSession setUpCccUwbSessionForExecution() throws RemoteException {
        // setup message
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        Params params = new CccOpenRangingParams.Builder()
                .setProtocolVersion(CccParams.PROTOCOL_VERSION_1_0)
                .setUwbConfig(CccParams.UWB_CONFIG_0)
                .setPulseShapeCombo(
                        new CccPulseShapeCombo(
                                CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                                CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE))
                .setSessionId(1)
                .setRanMultiplier(4)
                .setChannel(CccParams.UWB_CHANNEL_9)
                .setNumChapsPerSlot(CccParams.CHAPS_PER_SLOT_3)
                .setNumResponderNodes(1)
                .setNumSlotsPerRound(CccParams.SLOTS_PER_ROUND_6)
                .setSyncCodeIndex(1)
                .setHoppingConfigMode(CccParams.HOPPING_CONFIG_MODE_NONE)
                .setHoppingSequence(CccParams.HOPPING_SEQUENCE_DEFAULT)
                .build();
        IBinder mockBinder = mock(IBinder.class);
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, CccParams.PROTOCOL_NAME, params, mockRangingCallbacks));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyString(), any(), any());
        doReturn(mock(WaitObj.class)).when(uwbSession).getWaitObj();

        return uwbSession;
    }

    @Test
    public void openRanging_success() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mNativeUwbManager).initSession(eq(TEST_SESSION_ID), anyByte());
        verify(mUwbConfigurationManager).setAppConfigurations(eq(TEST_SESSION_ID), any());
        verify(mUwbSessionNotificationManager).onRangingOpened(eq(uwbSession));
        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_OK));
    }

    @Test
    public void openRanging_timeout() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenThrow(new IllegalStateException());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID));
    }

    @Test
    public void openRanging_nativeInitSessionFailed() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID));
    }

    @Test
    public void openRanging_setAppConfigurationFailed() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID));
    }

    @Test
    public void openRanging_wrongInitState() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID));
    }

    @Test
    public void openRanging_wrongIdleState() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_ERROR).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession),
                        eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID));
    }

    private UwbSession prepareExistingUwbSession() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.nextMessage(); // remove the OPEN_RANGING msg;

        assertThat(mTestLooper.isIdle()).isFalse();

        return uwbSession;
    }

    private UwbSession prepareExistingCccUwbSession() throws Exception {
        UwbSession uwbSession = setUpCccUwbSessionForExecution();
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, CccParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.nextMessage(); // remove the OPEN_RANGING msg;

        assertThat(mTestLooper.isIdle()).isFalse();

        return uwbSession;
    }

    @Test
    public void startRanging_sessionStateIdle() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());

        assertThat(mTestLooper.isIdle()).isTrue();
        assertThat(mTestLooper.nextMessage().what).isEqualTo(2); // SESSION_START_RANGING
    }

    @Test
    public void startRanging_sessionStateActive() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());

        assertThat(mTestLooper.isIdle()).isFalse();
        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void startRanging_sessionStateError() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(uwbSession).getSessionState();

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());

        assertThat(mTestLooper.isIdle()).isFalse();
        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execStartRanging_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
    }

    @Test
    public void execStartRanging_onRangeDataNotification() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Now send a range data notification.
        UwbRangingData uwbRangingData =
                UwbTestUtils.generateRangingData(UwbUciConstants.STATUS_CODE_OK);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);
    }

    @Test
    public void execStartRanging_onRangeDataNotificationContinuousErrors() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Now send a range data notification with an error.
        UwbRangingData uwbRangingData =
                UwbTestUtils.generateRangingData(UwbUciConstants.STATUS_CODE_RANGING_RX_TIMEOUT);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);
        ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        verify(mAlarmManager).set(
                anyInt(), anyLong(), anyString(), alarmListenerCaptor.capture(), any());
        assertThat(alarmListenerCaptor.getValue()).isNotNull();

        // Send one more error and ensure that the timer is not cancelled.
        uwbRangingData =
                UwbTestUtils.generateRangingData(UwbUciConstants.STATUS_CODE_RANGING_RX_TIMEOUT);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);

        verify(mAlarmManager, never()).cancel(any(AlarmManager.OnAlarmListener.class));

        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        // Now fire the timer callback.
        alarmListenerCaptor.getValue().onAlarm();

        // Expect session stop.
        mTestLooper.dispatchNext();
        verify(mUwbSessionNotificationManager)
                .onRangingStopped(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void execStartRanging_onRangeDataNotificationErrorFollowedBySuccess() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Now send a range data notification with an error.
        UwbRangingData uwbRangingData =
                UwbTestUtils.generateRangingData(UwbUciConstants.STATUS_CODE_RANGING_RX_TIMEOUT);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);
        ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        verify(mAlarmManager).set(
                anyInt(), anyLong(), anyString(), alarmListenerCaptor.capture(), any());
        assertThat(alarmListenerCaptor.getValue()).isNotNull();

        // Send success and ensure that the timer is cancelled.
        uwbRangingData =
                UwbTestUtils.generateRangingData(UwbUciConstants.STATUS_CODE_OK);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);

        verify(mAlarmManager).cancel(any(AlarmManager.OnAlarmListener.class));
    }

    @Test
    public void execStartCccRanging_success() throws Exception {
        UwbSession uwbSession = prepareExistingCccUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        CccStartRangingParams cccStartRangingParams = new CccStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(8)
                .build();
        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), cccStartRangingParams);
        mTestLooper.dispatchAll();

        // Verify the update logic.
        CccOpenRangingParams cccOpenRangingParams = (CccOpenRangingParams) uwbSession.getParams();
        assertThat(cccOpenRangingParams.getRanMultiplier()).isEqualTo(8);
    }

    @Test
    public void execStartCccRangingWithNoStartParams_success() throws Exception {
        UwbSession uwbSession = prepareExistingCccUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        mUwbSessionManager.startRanging(uwbSession.getSessionHandle(), null /* params */);
        mTestLooper.dispatchAll();

        // Verify that RAN multiplier from open is used.
        CccOpenRangingParams cccOpenRangingParams = (CccOpenRangingParams) uwbSession.getParams();
        assertThat(cccOpenRangingParams.getRanMultiplier()).isEqualTo(4);
    }

    @Test
    public void execStartRanging_executionException() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenThrow(new IllegalStateException());

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execStartRanging_nativeStartRangingFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execStartRanging_wrongSessionState() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void stopRanging_sessionStateActive() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());

        assertThat(mTestLooper.nextMessage().what).isEqualTo(3); // SESSION_STOP_RANGING
    }

    @Test
    public void stopRanging_sessionStateIdle() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());

        verify(mUwbSessionNotificationManager).onRangingStopped(
                eq(uwbSession),
                eq(UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS));
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void stopRanging_sessionStateError() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR).when(uwbSession).getSessionState();

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());

        verify(mUwbSessionNotificationManager).onRangingStopFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void execStopRanging_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager)
                .onRangingStopped(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void execStopRanging_exception() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID)))
                .thenThrow(new IllegalStateException());

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager, never()).onRangingStopped(any(), anyInt());
    }

    @Test
    public void execStopRanging_nativeFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager)
                .onRangingStopFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics, never()).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void reconfigure_notExistingSession() {
        int status = mUwbSessionManager.reconfigure(mock(SessionHandle.class), mock(Params.class));

        assertThat(status).isEqualTo(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST);
    }

    private FiraRangingReconfigureParams buildReconfigureParams() {
        return buildReconfigureParams(FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD);
    }

    private FiraRangingReconfigureParams buildReconfigureParams(int action) {
        FiraRangingReconfigureParams reconfigureParams =
                new FiraRangingReconfigureParams.Builder()
                        .setAddressList(new UwbAddress[] {
                                UwbAddress.fromBytes(new byte[] { (byte) 0x01, (byte) 0x02 }) })
                        .setAction(action)
                        .setSubSessionIdList(new int[] { 2 })
                        .build();

        return spy(reconfigureParams);
    }

    @Test
    public void reconfigure_existingSession() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        int status = mUwbSessionManager.reconfigure(
                uwbSession.getSessionHandle(), buildReconfigureParams());

        assertThat(status).isEqualTo(0);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(4); // SESSION_RECONFIGURE_RANGING
    }

    @Test
    public void execReconfigureAddControlee_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        short dstAddress =
                ByteBuffer.wrap(reconfigureParams.getAddressList()[0].toBytes()).getShort(0);
        verify(mNativeUwbManager).controllerMulticastListUpdate(
                uwbSession.getSessionId(), reconfigureParams.getAction(), 1,
                new short[] {dstAddress}, reconfigureParams.getSubSessionIdList());
        verify(mUwbSessionNotificationManager).onControleeAdded(eq(uwbSession));
        verify(mUwbSessionNotificationManager).onRangingReconfigured(eq(uwbSession));
    }

    @Test
    public void execReconfigureRemoveControlee_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams(FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE);
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        short dstAddress =
                ByteBuffer.wrap(reconfigureParams.getAddressList()[0].toBytes()).getShort(0);
        verify(mNativeUwbManager).controllerMulticastListUpdate(
                uwbSession.getSessionId(), reconfigureParams.getAction(), 1,
                new short[] {dstAddress}, reconfigureParams.getSubSessionIdList());
        verify(mUwbSessionNotificationManager).onControleeRemoved(eq(uwbSession));
        verify(mUwbSessionNotificationManager).onRangingReconfigured(eq(uwbSession));
    }

    @Test
    public void execReconfigure_nativeUpdateFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onControleeAddFailed(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execReconfigure_uwbSessionUpdateFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_FAILED });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onControleeAddFailed(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execReconfigure_setAppConfigurationsFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void deInitSession() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());

        assertThat(mTestLooper.nextMessage().what).isEqualTo(5); // SESSION_CLOSE
    }

    @Test
    public void execCloseSession_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(TEST_SESSION_ID))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
    }

    @Test
    public void execCloseSession_failed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(TEST_SESSION_ID))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
    }

    @Test
    public void onSessionStatusNotification_session_deinit() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(TEST_SESSION_ID))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.onSessionStatusNotificationReceived(
                uwbSession.getSessionId(), UwbUciConstants.UWB_SESSION_STATE_DEINIT,
                UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosedWithApiReasonCode(
                eq(uwbSession), eq(RangingChangeReason.SYSTEM_POLICY));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
    }

    @Test
    public void testHandleClientDeath() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(TEST_SESSION_ID))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        uwbSession.binderDied();

        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
    }
}

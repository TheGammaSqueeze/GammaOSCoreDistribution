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


import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_3;
import static com.google.uwb.support.ccc.CccParams.HOPPING_CONFIG_MODE_NONE;
import static com.google.uwb.support.ccc.CccParams.HOPPING_SEQUENCE_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE;
import static com.google.uwb.support.ccc.CccParams.SLOTS_PER_ROUND_6;
import static com.google.uwb.support.ccc.CccParams.UWB_CHANNEL_9;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_UNICAST;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.Context;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;
import android.uwb.AdapterState;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.IUwbVendorUciCallback;
import android.uwb.SessionHandle;
import android.uwb.StateChangeReason;
import android.uwb.UwbAddress;
import android.uwb.UwbManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.data.UwbVendorUciResponse;
import com.android.server.uwb.jni.NativeUwbManager;

import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccStartRangingParams;
import com.google.uwb.support.fira.FiraControleeParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.generic.GenericParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/**
 * Tests for {@link UwbServiceCore}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbServiceCoreTest {
    private static final int TEST_UID = 44;
    private static final String TEST_PACKAGE_NAME = "com.android.uwb";
    private static final AttributionSource TEST_ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(TEST_UID)
                    .setPackageName(TEST_PACKAGE_NAME)
                    .build();
    private static final FiraOpenSessionParams.Builder TEST_FIRA_OPEN_SESSION_PARAMS =
            new FiraOpenSessionParams.Builder()
                    .setProtocolVersion(FiraParams.PROTOCOL_VERSION_1_1)
                    .setSessionId(1)
                    .setDeviceType(RANGING_DEVICE_TYPE_CONTROLLER)
                    .setDeviceRole(RANGING_DEVICE_ROLE_RESPONDER)
                    .setDeviceAddress(UwbAddress.fromBytes(new byte[] { 0x4, 0x6}))
                    .setDestAddressList(Arrays.asList(UwbAddress.fromBytes(new byte[] { 0x4, 0x6})))
                    .setMultiNodeMode(MULTI_NODE_MODE_UNICAST)
                    .setRangingRoundUsage(RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE)
                    .setVendorId(new byte[]{0x5, 0x78})
                    .setStaticStsIV(new byte[]{0x1a, 0x55, 0x77, 0x47, 0x7e, 0x7d});

    @VisibleForTesting
    private static final CccOpenRangingParams.Builder TEST_CCC_OPEN_RANGING_PARAMS =
            new CccOpenRangingParams.Builder()
                    .setProtocolVersion(CccParams.PROTOCOL_VERSION_1_0)
                    .setUwbConfig(CccParams.UWB_CONFIG_0)
                    .setPulseShapeCombo(
                            new CccPulseShapeCombo(
                                    PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                                    PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE))
                    .setSessionId(1)
                    .setRanMultiplier(4)
                    .setChannel(UWB_CHANNEL_9)
                    .setNumChapsPerSlot(CHAPS_PER_SLOT_3)
                    .setNumResponderNodes(1)
                    .setNumSlotsPerRound(SLOTS_PER_ROUND_6)
                    .setSyncCodeIndex(1)
                    .setHoppingConfigMode(HOPPING_CONFIG_MODE_NONE)
                    .setHoppingSequence(HOPPING_SEQUENCE_DEFAULT);
    @Mock private Context mContext;
    @Mock private NativeUwbManager mNativeUwbManager;
    @Mock private UwbMetrics mUwbMetrics;
    @Mock private UwbCountryCode mUwbCountryCode;
    @Mock private UwbSessionManager mUwbSessionManager;
    @Mock private UwbConfigurationManager mUwbConfigurationManager;
    @Mock private UwbInjector mUwbInjector;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    private TestLooper mTestLooper;
    private MockitoSession mMockitoSession;

    private UwbServiceCore mUwbServiceCore;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();
        PowerManager powerManager = mock(PowerManager.class);
        when(powerManager.newWakeLock(anyInt(), anyString()))
                .thenReturn(mock(PowerManager.WakeLock.class));
        when(mContext.getSystemService(PowerManager.class)).thenReturn(powerManager);
        when(mUwbInjector.isSystemApp(TEST_UID, TEST_PACKAGE_NAME)).thenReturn(true);
        when(mUwbInjector.isForegroundAppOrService(TEST_UID, TEST_PACKAGE_NAME)).thenReturn(true);
        when(mUwbInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.getBugReportMinIntervalMs())
                .thenReturn(DeviceConfigFacade.DEFAULT_BUG_REPORT_MIN_INTERVAL_MS);
        mUwbServiceCore = new UwbServiceCore(mContext, mNativeUwbManager, mUwbMetrics,
                mUwbCountryCode, mUwbSessionManager, mUwbConfigurationManager,
                mUwbInjector, mTestLooper.getLooper());

        // static mocking for executor service.
        mMockitoSession = ExtendedMockito.mockitoSession()
                .mockStatic(Executors.class, Mockito.withSettings().lenient())
                .strictness(Strictness.LENIENT)
                .startMocking();
        ExecutorService executorService = mock(ExecutorService.class);
        doAnswer(invocation -> {
            FutureTask t = invocation.getArgument(1);
            t.run();
            return t;
        }).when(executorService).submit(any(Callable.class));
        doAnswer(invocation -> {
            FutureTask t = invocation.getArgument(0);
            t.run();
            return t;
        }).when(executorService).submit(any(Runnable.class));
        when(Executors.newSingleThreadExecutor()).thenReturn(executorService);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    private void verifyGetSpecificationInfoSuccess() throws Exception {
        GenericSpecificationParams genericSpecificationParams =
                mock(GenericSpecificationParams.class);
        PersistableBundle genericSpecificationBundle = mock(PersistableBundle.class);
        when(genericSpecificationParams.toBundle()).thenReturn(genericSpecificationBundle);

        when(mUwbConfigurationManager.getCapsInfo(eq(GenericParams.PROTOCOL_NAME), any()))
                .thenReturn(Pair.create(
                        UwbUciConstants.STATUS_CODE_OK, genericSpecificationParams));

        PersistableBundle specifications = mUwbServiceCore.getSpecificationInfo();
        assertThat(specifications).isEqualTo(genericSpecificationBundle);
        verify(mUwbConfigurationManager).getCapsInfo(eq(GenericParams.PROTOCOL_NAME), any());
    }

    @Test
    public void testGetSpecificationInfoSuccess() throws Exception {
        verifyGetSpecificationInfoSuccess();
    }

    private void enableUwb() throws Exception {
        when(mNativeUwbManager.doInitialize()).thenReturn(true);
        when(mUwbCountryCode.setCountryCode(anyBoolean())).thenReturn(true);

        mUwbServiceCore.setEnabled(true);
        mTestLooper.dispatchAll();
    }

    private void disableUwb() throws Exception {
        when(mNativeUwbManager.doDeinitialize()).thenReturn(true);

        mUwbServiceCore.setEnabled(false);
        mTestLooper.dispatchAll();
    }

    @Test
    public void testEnable() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);

        enableUwb();

        verify(mNativeUwbManager).doInitialize();
        verify(mUwbCountryCode).setCountryCode(true);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);
    }

    @Test
    public void testEnableWhenAlreadyEnabled() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);

        enableUwb();

        verify(mNativeUwbManager).doInitialize();
        verify(mUwbCountryCode).setCountryCode(true);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);

        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);
        // Enable again. should be ignored.
        enableUwb();
        verifyNoMoreInteractions(mNativeUwbManager, mUwbCountryCode, cb);
    }


    @Test
    public void testDisable() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);

        // Enable first
        enableUwb();

        disableUwb();

        verify(mNativeUwbManager).doDeinitialize();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
    }


    @Test
    public void testDisableWhenAlreadyDisabled() throws Exception {
        when(mNativeUwbManager.doInitialize()).thenReturn(true);
        when(mUwbCountryCode.setCountryCode(anyBoolean())).thenReturn(true);
        when(mNativeUwbManager.doDeinitialize()).thenReturn(true);

        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);

        // Enable first
        enableUwb();

        disableUwb();

        verify(mNativeUwbManager).doDeinitialize();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);

        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);
        // Disable again. should be ignored.
        disableUwb();
        verifyNoMoreInteractions(mNativeUwbManager, mUwbCountryCode, cb);
    }

    @Test
    public void testOpenFiraRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        AttributionSource attributionSource = TEST_ATTRIBUTION_SOURCE;
        FiraOpenSessionParams params = TEST_FIRA_OPEN_SESSION_PARAMS.build();
        mUwbServiceCore.openRanging(
                attributionSource, sessionHandle, cb, params.toBundle());

        verify(mUwbSessionManager).initSession(
                eq(attributionSource),
                eq(sessionHandle), eq(params.getSessionId()), eq(FiraParams.PROTOCOL_NAME),
                argThat(p -> ((FiraOpenSessionParams) p).getSessionId() == params.getSessionId()),
                eq(cb));

    }

    @Test
    public void testOpenCccRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        CccOpenRangingParams params = TEST_CCC_OPEN_RANGING_PARAMS.build();
        AttributionSource attributionSource = TEST_ATTRIBUTION_SOURCE;
        mUwbServiceCore.openRanging(
                attributionSource, sessionHandle, cb, params.toBundle());

        verify(mUwbSessionManager).initSession(
                eq(attributionSource),
                eq(sessionHandle), eq(params.getSessionId()), eq(CccParams.PROTOCOL_NAME),
                argThat(p -> ((CccOpenRangingParams) p).getSessionId() == params.getSessionId()),
                eq(cb));
    }

    @Test
    public void testOpenRangingWhenUwbDisabled() throws Exception {
        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        CccOpenRangingParams params = TEST_CCC_OPEN_RANGING_PARAMS.build();
        AttributionSource attributionSource = TEST_ATTRIBUTION_SOURCE;

        try {
            mUwbServiceCore.openRanging(attributionSource, sessionHandle, cb, params.toBundle());
            fail();
        } catch (IllegalStateException e) {
            // pass
        }

        // Should be ignored.
        verifyNoMoreInteractions(mUwbSessionManager);
    }

    @Test
    public void testOpenRangingWithNonSystemAppInFg() throws Exception {
        enableUwb();

        when(mUwbInjector.isSystemApp(TEST_UID, TEST_PACKAGE_NAME)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(TEST_UID, TEST_PACKAGE_NAME)).thenReturn(true);

        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        AttributionSource attributionSource = TEST_ATTRIBUTION_SOURCE;
        FiraOpenSessionParams params = TEST_FIRA_OPEN_SESSION_PARAMS.build();
        mUwbServiceCore.openRanging(
                attributionSource, sessionHandle, cb, params.toBundle());

        verify(mUwbSessionManager).initSession(
                eq(attributionSource),
                eq(sessionHandle), eq(params.getSessionId()), eq(FiraParams.PROTOCOL_NAME),
                argThat(p -> ((FiraOpenSessionParams) p).getSessionId() == params.getSessionId()),
                eq(cb));
    }

    @Test
    public void testOpenRangingWithNonSystemAppNotInFg() throws Exception {
        enableUwb();

        when(mUwbInjector.isSystemApp(TEST_UID, TEST_PACKAGE_NAME)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(TEST_UID, TEST_PACKAGE_NAME)).thenReturn(false);

        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        AttributionSource attributionSource = TEST_ATTRIBUTION_SOURCE;
        FiraOpenSessionParams params = TEST_FIRA_OPEN_SESSION_PARAMS.build();
        mUwbServiceCore.openRanging(
                attributionSource, sessionHandle, cb, params.toBundle());

        verify(mUwbSessionManager, never()).initSession(
                any(), any(), anyInt(), any(), any(), any());
        verify(cb).onRangingOpenFailed(
                eq(sessionHandle), eq(StateChangeReason.SYSTEM_POLICY), any());
    }

    @Test
    public void testOpenRangingWithNonSystemAppInFgInChain() throws Exception {
        enableUwb();

        int test_uid_2 = 67;
        String test_package_name_2 = "com.android.uwb.2";
        when(mUwbInjector.isSystemApp(test_uid_2, test_package_name_2)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(test_uid_2, test_package_name_2))
                .thenReturn(true);

        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        // simulate system app triggered the request on behalf of a fg app in fg.
        AttributionSource attributionSource = new AttributionSource.Builder(TEST_UID)
                .setPackageName(TEST_PACKAGE_NAME)
                .setNext(new AttributionSource.Builder(test_uid_2)
                        .setPackageName(test_package_name_2)
                        .build())
                .build();
        FiraOpenSessionParams params = TEST_FIRA_OPEN_SESSION_PARAMS.build();
        mUwbServiceCore.openRanging(
                attributionSource, sessionHandle, cb, params.toBundle());

        verify(mUwbSessionManager).initSession(
                eq(attributionSource),
                eq(sessionHandle), eq(params.getSessionId()), eq(FiraParams.PROTOCOL_NAME),
                argThat(p -> ((FiraOpenSessionParams) p).getSessionId() == params.getSessionId()),
                eq(cb));
    }

    @Test
    public void testOpenRangingWithNonSystemAppNotInFgInChain() throws Exception {
        enableUwb();

        int test_uid_2 = 67;
        String test_package_name_2 = "com.android.uwb.2";
        when(mUwbInjector.isSystemApp(test_uid_2, test_package_name_2)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(test_uid_2, test_package_name_2))
                .thenReturn(false);

        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        // simulate system app triggered the request on behalf of a fg app not in fg.
        AttributionSource attributionSource = new AttributionSource.Builder(TEST_UID)
                .setPackageName(TEST_PACKAGE_NAME)
                .setNext(new AttributionSource.Builder(test_uid_2)
                        .setPackageName(test_package_name_2)
                        .build())
                .build();
        FiraOpenSessionParams params = TEST_FIRA_OPEN_SESSION_PARAMS.build();
        mUwbServiceCore.openRanging(
                attributionSource, sessionHandle, cb, params.toBundle());

        verify(mUwbSessionManager, never()).initSession(
                any(), any(), anyInt(), any(), any(), any());
        verify(cb).onRangingOpenFailed(
                eq(sessionHandle), eq(StateChangeReason.SYSTEM_POLICY), any());
    }

    @Test
    public void testStartCccRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        CccStartRangingParams params = new CccStartRangingParams.Builder()
                .setRanMultiplier(6)
                .setSessionId(1)
                .build();
        mUwbServiceCore.startRanging(sessionHandle, params.toBundle());

        verify(mUwbSessionManager).startRanging(eq(sessionHandle),
                argThat(p -> ((CccStartRangingParams) p).getSessionId() == params.getSessionId()));
    }

    @Test
    public void testStartCccRangingWithNoStartParams() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        mUwbServiceCore.startRanging(sessionHandle, new PersistableBundle());

        verify(mUwbSessionManager).startRanging(eq(sessionHandle), argThat(p -> (p == null)));
    }

    @Test
    public void testReconfigureRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        final FiraRangingReconfigureParams parameters =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(6)
                        .setRangeDataNtfConfig(RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY)
                        .setRangeDataProximityFar(6)
                        .setRangeDataProximityNear(4)
                        .build();
        mUwbServiceCore.reconfigureRanging(sessionHandle, parameters.toBundle());
        verify(mUwbSessionManager).reconfigure(eq(sessionHandle),
                argThat((x) ->
                        ((FiraRangingReconfigureParams) x).getBlockStrideLength().equals(6)));
    }

    @Test
    public void testAddControlee() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        UwbAddress uwbAddress1 = UwbAddress.fromBytes(new byte[] {1, 2});
        UwbAddress uwbAddress2 = UwbAddress.fromBytes(new byte[] {4, 5});
        UwbAddress[] addressList = new UwbAddress[] {uwbAddress1, uwbAddress2};
        int[] subSessionIdList = new int[] {3, 4};
        FiraControleeParams params =
                new FiraControleeParams.Builder()
                        .setAddressList(addressList)
                        .setSubSessionIdList(subSessionIdList)
                        .build();

        mUwbServiceCore.addControlee(sessionHandle, params.toBundle());
        verify(mUwbSessionManager).reconfigure(eq(sessionHandle),
                argThat((x) -> {
                    FiraRangingReconfigureParams reconfigureParams =
                            (FiraRangingReconfigureParams) x;
                    return reconfigureParams.getAction().equals(MULTICAST_LIST_UPDATE_ACTION_ADD)
                            && Arrays.equals(
                                    reconfigureParams.getAddressList(), params.getAddressList())
                            && Arrays.equals(
                                    reconfigureParams.getSubSessionIdList(),
                                    params.getSubSessionIdList());
                }));
    }

    @Test
    public void testRemoveControlee() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        UwbAddress uwbAddress1 = UwbAddress.fromBytes(new byte[] {1, 2});
        UwbAddress uwbAddress2 = UwbAddress.fromBytes(new byte[] {4, 5});
        UwbAddress[] addressList = new UwbAddress[] {uwbAddress1, uwbAddress2};
        int[] subSessionIdList = new int[] {3, 4};
        FiraControleeParams params =
                new FiraControleeParams.Builder()
                        .setAddressList(addressList)
                        .setSubSessionIdList(subSessionIdList)
                        .build();

        mUwbServiceCore.removeControlee(sessionHandle, params.toBundle());
        verify(mUwbSessionManager).reconfigure(eq(sessionHandle),
                argThat((x) -> {
                    FiraRangingReconfigureParams reconfigureParams =
                            (FiraRangingReconfigureParams) x;
                    return reconfigureParams.getAction().equals(MULTICAST_LIST_UPDATE_ACTION_DELETE)
                            && Arrays.equals(
                                    reconfigureParams.getAddressList(), params.getAddressList())
                            && Arrays.equals(
                                    reconfigureParams.getSubSessionIdList(),
                                    params.getSubSessionIdList());
                }));
    }

    @Test
    public void testStopRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        mUwbServiceCore.stopRanging(sessionHandle);

        verify(mUwbSessionManager).stopRanging(sessionHandle);
    }


    @Test
    public void testCloseRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        mUwbServiceCore.closeRanging(sessionHandle);

        verify(mUwbSessionManager).deInitSession(sessionHandle);
    }

    @Test
    public void testGetAdapterState() throws Exception {
        enableUwb();
        assertThat(mUwbServiceCore.getAdapterState())
                .isEqualTo(AdapterState.STATE_ENABLED_INACTIVE);

        disableUwb();
        assertThat(mUwbServiceCore.getAdapterState())
                .isEqualTo(AdapterState.STATE_DISABLED);
    }


    @Test
    public void testSendVendorUciCommand() throws Exception {
        enableUwb();

        int gid = 0;
        int oid = 0;
        byte[] payload = new byte[0];
        UwbVendorUciResponse rsp = new UwbVendorUciResponse(
                (byte) UwbUciConstants.STATUS_CODE_OK, gid, oid, payload);
        when(mNativeUwbManager.sendRawVendorCmd(anyInt(), anyInt(), any()))
                .thenReturn(rsp);

        IUwbVendorUciCallback vendorCb = mock(IUwbVendorUciCallback.class);
        mUwbServiceCore.registerVendorExtensionCallback(vendorCb);

        assertThat(mUwbServiceCore.sendVendorUciMessage(0, 0, new byte[0]))
                .isEqualTo(UwbUciConstants.STATUS_CODE_OK);

        verify(vendorCb).onVendorResponseReceived(gid, oid, payload);
    }

    @Test
    public void testDeviceStateCallback() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);

        enableUwb();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);

        mUwbServiceCore.onDeviceStatusNotificationReceived(UwbUciConstants.DEVICE_STATE_ACTIVE);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE,
                StateChangeReason.SESSION_STARTED);
    }

    @Test
    public void testToggleOfOnDeviceStateErrorCallback() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);

        enableUwb();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);

        when(mNativeUwbManager.doDeinitialize()).thenReturn(true);

        mUwbServiceCore.onDeviceStatusNotificationReceived(UwbUciConstants.DEVICE_STATE_ERROR);
        mTestLooper.dispatchAll();
        // Verify UWB toggle off.
        verify(mNativeUwbManager).doDeinitialize();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
    }

    @Test
    public void testVendorUciNotificationCallback() throws Exception {
        enableUwb();

        IUwbVendorUciCallback vendorCb = mock(IUwbVendorUciCallback.class);
        mUwbServiceCore.registerVendorExtensionCallback(vendorCb);
        int gid = 0;
        int oid = 0;
        byte[] payload = new byte[0];
        mUwbServiceCore.onVendorUciNotificationReceived(gid, oid, payload);
        verify(vendorCb).onVendorNotificationReceived(gid, oid, payload);
    }
}

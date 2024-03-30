/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi;

import static com.android.server.wifi.HalDeviceManager.CHIP_CAPABILITY_ANY;
import static com.android.server.wifi.HalDeviceManager.HAL_IFACE_MAP;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP_BRIDGE;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_NAN;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_P2P;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_STA;
import static com.android.server.wifi.HalDeviceManager.START_HAL_RETRY_TIMES;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
import android.content.res.Resources;
import android.hardware.wifi.V1_0.IWifi;
import android.hardware.wifi.V1_0.IWifiApIface;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.IWifiChipEventCallback;
import android.hardware.wifi.V1_0.IWifiEventCallback;
import android.hardware.wifi.V1_0.IWifiIface;
import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.IWifiP2pIface;
import android.hardware.wifi.V1_0.IWifiRttController;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.IfaceType;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hardware.wifi.V1_5.WifiBand;
import android.hardware.wifi.V1_6.IfaceConcurrencyType;
import android.hardware.wifi.V1_6.WifiRadioCombination;
import android.hardware.wifi.V1_6.WifiRadioCombinationMatrix;
import android.hardware.wifi.V1_6.WifiRadioConfiguration;
import android.hidl.manager.V1_0.IServiceNotification;
import android.hidl.manager.V1_2.IServiceManager;
import android.net.wifi.WifiContext;
import android.os.Handler;
import android.os.IHwBinder;
import android.os.RemoteException;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.HalDeviceManager.InterfaceDestroyedListener;
import com.android.server.wifi.util.WorkSourceHelper;
import com.android.wifi.resources.R;

import com.google.common.collect.ImmutableList;

import org.hamcrest.core.IsNull;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit test harness for HalDeviceManagerTest.
 */
@SmallTest
public class HalDeviceManagerTest extends WifiBaseTest {
    private static final WorkSource TEST_WORKSOURCE_0 = new WorkSource(450, "com.test.0");
    private static final WorkSource TEST_WORKSOURCE_1 = new WorkSource(451, "com.test.1");
    private static final WorkSource TEST_WORKSOURCE_2 = new WorkSource(452, "com.test.2");

    private HalDeviceManager mDut;
    @Mock IServiceManager mServiceManagerMock;
    @Mock IWifi mWifiMock;
    @Mock android.hardware.wifi.V1_5.IWifi mWifiMockV15;
    @Mock IWifiRttController mRttControllerMock;
    @Mock HalDeviceManager.ManagerStatusListener mManagerStatusListenerMock;
    @Mock private WifiContext mContext;
    @Mock private Resources mResources;
    @Mock private Clock mClock;
    @Mock private WifiInjector mWifiInjector;
    @Mock private SoftApManager mSoftApManager;
    @Mock private WifiSettingsConfigStore mWifiSettingsConfigStore;
    @Mock private WorkSourceHelper mWorkSourceHelper0;
    @Mock private WorkSourceHelper mWorkSourceHelper1;
    @Mock private WorkSourceHelper mWorkSourceHelper2;
    private android.hardware.wifi.V1_5.IWifiChip mWifiChipV15 = null;
    private android.hardware.wifi.V1_6.IWifiChip mWifiChipV16 = null;
    private TestLooper mTestLooper;
    private Handler mHandler;
    private ArgumentCaptor<IHwBinder.DeathRecipient> mDeathRecipientCaptor =
            ArgumentCaptor.forClass(IHwBinder.DeathRecipient.class);
    private ArgumentCaptor<IServiceNotification.Stub> mServiceNotificationCaptor =
            ArgumentCaptor.forClass(IServiceNotification.Stub.class);
    private ArgumentCaptor<IWifiEventCallback> mWifiEventCallbackCaptor = ArgumentCaptor.forClass(
            IWifiEventCallback.class);
    private ArgumentCaptor<android.hardware.wifi.V1_5.IWifiEventCallback>
            mWifiEventCallbackCaptorV15 = ArgumentCaptor.forClass(
            android.hardware.wifi.V1_5.IWifiEventCallback.class);
    private InOrder mInOrder;
    @Rule public ErrorCollector collector = new ErrorCollector();
    private WifiStatus mStatusOk;
    private WifiStatus mStatusFail;
    private boolean mIsBridgedSoftApSupported = false;
    private boolean mIsStaWithBridgedSoftApConcurrencySupported = false;

    private class HalDeviceManagerSpy extends HalDeviceManager {
        HalDeviceManagerSpy() {
            super(mContext, mClock, mWifiInjector, mHandler);
        }

        @Override
        protected IWifi getWifiServiceMockable() {
            return mWifiMock;
        }

        @Override
        protected android.hardware.wifi.V1_5.IWifi getWifiServiceForV1_5Mockable(IWifi iWifi) {
            return (mWifiMockV15 != null)
                    ? mWifiMockV15
                    : null;
        }

        @Override
        protected IServiceManager getServiceManagerMockable() {
            return mServiceManagerMock;
        }

        @Override
        protected android.hardware.wifi.V1_5.IWifiChip getWifiChipForV1_5Mockable(IWifiChip chip) {
            return mWifiChipV15;
        }

        @Override
        protected android.hardware.wifi.V1_6.IWifiChip getWifiChipForV1_6Mockable(IWifiChip chip) {
            return mWifiChipV16;
        }

        @Override
        protected android.hardware.wifi.V1_5.IWifiApIface getIWifiApIfaceForV1_5Mockable(
                IWifiApIface iface) {
            if (iface instanceof android.hardware.wifi.V1_5.IWifiApIface) {
                return (android.hardware.wifi.V1_5.IWifiApIface) iface;
            }
            return null;
        }

        @Override
        protected boolean isBridgedSoftApSupportedMockable() {
            return mIsBridgedSoftApSupported;
        }

        @Override
        protected boolean isStaWithBridgedSoftApConcurrencySupportedMockable() {
            return mIsStaWithBridgedSoftApConcurrencySupported;
        }
    }

    @Before
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();
        mHandler = new Handler(mTestLooper.getLooper());

        // initialize placeholder status objects
        mStatusOk = getStatus(WifiStatusCode.SUCCESS);
        mStatusFail = getStatus(WifiStatusCode.ERROR_UNKNOWN);

        setupWifiV15(mWifiMock);

        when(mWifiInjector.makeWsHelper(TEST_WORKSOURCE_0)).thenReturn(mWorkSourceHelper0);
        when(mWifiInjector.makeWsHelper(TEST_WORKSOURCE_1)).thenReturn(mWorkSourceHelper1);
        when(mWifiInjector.makeWsHelper(TEST_WORKSOURCE_2)).thenReturn(mWorkSourceHelper2);
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(true);
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(true);
        when(mWorkSourceHelper2.hasAnyPrivilegedAppRequest()).thenReturn(true);
        when(mWorkSourceHelper0.getWorkSource()).thenReturn(TEST_WORKSOURCE_0);
        when(mWorkSourceHelper1.getWorkSource()).thenReturn(TEST_WORKSOURCE_1);
        when(mWorkSourceHelper2.getWorkSource()).thenReturn(TEST_WORKSOURCE_2);
        when(mWifiInjector.getSettingsConfigStore()).thenReturn(mWifiSettingsConfigStore);

        when(mServiceManagerMock.linkToDeath(any(IHwBinder.DeathRecipient.class),
                anyLong())).thenReturn(true);
        when(mServiceManagerMock.registerForNotifications(anyString(), anyString(),
                any(IServiceNotification.Stub.class))).thenReturn(true);
        when(mServiceManagerMock.listManifestByInterface(eq(IWifi.kInterfaceName)))
                .thenReturn(new ArrayList(Arrays.asList("default")));
        when(mWifiMock.linkToDeath(any(IHwBinder.DeathRecipient.class), anyLong())).thenReturn(
                true);
        when(mWifiMock.registerEventCallback(any(IWifiEventCallback.class))).thenReturn(mStatusOk);
        when(mWifiMock.start()).thenReturn(mStatusOk);
        when(mWifiMock.stop()).thenReturn(mStatusOk);
        when(mWifiMock.isStarted()).thenReturn(true);
        when(mWifiMockV15.isStarted()).thenReturn(true);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(R.bool.config_wifiBridgedSoftApSupported))
                .thenReturn(mIsBridgedSoftApSupported);
        when(mResources.getBoolean(R.bool.config_wifiStaWithBridgedSoftApConcurrencySupported))
                .thenReturn(mIsStaWithBridgedSoftApConcurrencySupported);

        mDut = new HalDeviceManagerSpy();
    }

    /**
     * Print out the dump of the device manager after each test. Not used in test validation
     * (internal state) - but can help in debugging failed tests.
     */
    @After
    public void after() throws Exception {
        dumpDut("after: ");
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Chip Independent Tests
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Test basic startup flow:
     * - IServiceManager registrations
     * - IWifi registrations
     * - IWifi startup delayed
     * - Start Wi-Fi -> onStart
     * - Stop Wi-Fi -> onStop
     */
    @Test
    public void testStartStopFlow() throws Exception {
        TestChipV5 chipMock = new TestChipV5();
        setupWifiChipV15(chipMock);
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // act: stop Wi-Fi
        mDut.stop();
        mTestLooper.dispatchAll();

        // verify: onStop called
        mInOrder.verify(mWifiMock).stop();
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Test the service manager notification coming in after
     * {@link HalDeviceManager#initIWifiIfNecessary()} is already invoked as a part of
     * {@link HalDeviceManager#initialize()}.
     */
    @Test
    public void testServiceRegisterationAfterInitialize() throws Exception {
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();

        // This should now be ignored since IWifi is already non-null.
        mServiceNotificationCaptor.getValue().onRegistration(IWifi.kInterfaceName, "", true);

        verifyNoMoreInteractions(mManagerStatusListenerMock, mWifiMock, mServiceManagerMock);
    }

    /**
     * Validate that multiple callback registrations are called and that duplicate ones are
     * only called once.
     */
    @Test
    public void testMultipleCallbackRegistrations() throws Exception {
        TestChipV5 chipMock = new TestChipV5();
        setupWifiChipV15(chipMock);
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();

        // register another 2 callbacks - one of them twice
        HalDeviceManager.ManagerStatusListener callback1 = mock(
                HalDeviceManager.ManagerStatusListener.class);
        HalDeviceManager.ManagerStatusListener callback2 = mock(
                HalDeviceManager.ManagerStatusListener.class);
        mDut.registerStatusListener(callback2, mHandler);
        mDut.registerStatusListener(callback1, mHandler);
        mDut.registerStatusListener(callback2, mHandler);

        // startup
        executeAndValidateStartupSequence();

        // verify
        verify(callback1).onStatusChanged();
        verify(callback2).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock, callback1, callback2);
    }

    /**
     * Validate IWifi death listener and registration flow.
     */
    @Test
    public void testWifiDeathAndRegistration() throws Exception {
        TestChipV5 chipMock = new TestChipV5();
        setupWifiChipV15(chipMock);
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, mWifiMockV15,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // act: IWifi service death
        mDeathRecipientCaptor.getValue().serviceDied(0);
        mTestLooper.dispatchAll();

        // verify: getting onStop
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        // act: service startup
        mServiceNotificationCaptor.getValue().onRegistration(IWifi.kInterfaceName, "", false);

        // verify: initialization of IWifi
        mInOrder.verify(mWifiMock).linkToDeath(mDeathRecipientCaptor.capture(), anyLong());
        if (null != mWifiMockV15) {
            mInOrder.verify(mWifiMockV15).registerEventCallback_1_5(
                    mWifiEventCallbackCaptorV15.capture());
        } else {
            mInOrder.verify(mWifiMock).registerEventCallback(mWifiEventCallbackCaptor.capture());
        }

        // act: start
        collector.checkThat(mDut.start(), equalTo(true));
        if (null != mWifiMockV15) {
            mWifiEventCallbackCaptorV15.getValue().onStart();
        } else {
            mWifiEventCallbackCaptor.getValue().onStart();
        }
        mTestLooper.dispatchAll();

        // verify: service and callback calls
        mInOrder.verify(mWifiMock).start();
        mInOrder.verify(mManagerStatusListenerMock, times(2)).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validate IWifi onFailure causes notification
     */
    @Test
    public void testWifiFail() throws Exception {
        TestChipV5 chipMock = new TestChipV5();
        setupWifiChipV15(chipMock);
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, mWifiMockV15,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // act: IWifi failure
        if (null != mWifiMockV15) {
            mWifiEventCallbackCaptorV15.getValue().onFailure(mStatusFail);
        } else {
            mWifiEventCallbackCaptor.getValue().onFailure(mStatusFail);
        }
        mTestLooper.dispatchAll();

        // verify: getting onStop
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        // act: start again
        collector.checkThat(mDut.start(), equalTo(true));
        if (null != mWifiMockV15) {
            mWifiEventCallbackCaptorV15.getValue().onStart();
        } else {
            mWifiEventCallbackCaptor.getValue().onStart();
        }
        mTestLooper.dispatchAll();

        // verify: service and callback calls
        mInOrder.verify(mWifiMock).start();
        mInOrder.verify(mManagerStatusListenerMock, times(2)).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validates that when (for some reason) the cache is out-of-sync with the actual chip status
     * then Wi-Fi is shut-down.
     *
     * Uses TestChipV1 - but nothing specific to its configuration. The test validates internal
     * HDM behavior.
     */
    @Test
    public void testCacheMismatchError() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // Request STA
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA can't be created", staIface, IsNull.notNullValue());

        // Request NAN
        IWifiIface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.STA_CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("NAN can't be created", nanIface, IsNull.notNullValue());

        // fiddle with the "chip" by removing the STA
        chipMock.interfaceNames.get(IfaceType.STA).remove("wlan0");

        // now try to request another NAN
        IWifiIface nanIface2 =
                mDut.createNanIface(nanDestroyedListener, mHandler, TEST_WORKSOURCE_0);
        collector.checkThat("NAN can't be created", nanIface2, IsNull.nullValue());
        mTestLooper.dispatchAll();

        // verify that Wi-Fi is shut-down: should also get all onDestroyed messages that are
        // registered (even if they seem out-of-sync to chip)
        verify(mWifiMock, times(2)).stop();
        verify(mManagerStatusListenerMock, times(2)).onStatusChanged();
        verify(staDestroyedListener).onDestroyed(getName(staIface));
        verify(nanDestroyedListener).onDestroyed(getName(nanIface));

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener,
                nanDestroyedListener);
    }

    /**
     * Validate that when no chip info is found an empty list is returned.
     */
    @Test
    public void testGetSupportedIfaceTypesError() throws Exception {
        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        assertEquals(0, results.size());
    }

    /**
     * Test start HAL can retry upon failure.
     *
     * Uses TestChipV1 - but nothing specific to its configuration. The test validates internal
     * HDM behavior.
     */
    @Test
    public void testStartHalRetryUponNotAvailableFailure() throws Exception {
        // Override the stubbing for mWifiMock in before().
        when(mWifiMock.start())
                .thenReturn(getStatus(WifiStatusCode.ERROR_NOT_AVAILABLE))
                .thenReturn(mStatusOk);

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence(2, true);
    }

    /**
     * Test start HAL fails after multiple retry failures.
     *
     * Uses TestChipV1 - but nothing specific to its configuration. The test validates internal
     * HDM behavior.
     */
    @Test
    public void testStartHalRetryFailUponMultipleNotAvailableFailures() throws Exception {
        // Override the stubbing for mWifiMock in before().
        when(mWifiMock.start()).thenReturn(getStatus(WifiStatusCode.ERROR_NOT_AVAILABLE));

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence(START_HAL_RETRY_TIMES + 1, false);
    }

    /**
     * Test start HAL fails after multiple retry failures.
     *
     * Uses TestChipV1 - but nothing specific to its configuration. The test validates internal
     * HDM behavior.
     */
    @Test
    public void testStartHalRetryFailUponTrueFailure() throws Exception {
        // Override the stubbing for mWifiMock in before().
        when(mWifiMock.start()).thenReturn(getStatus(WifiStatusCode.ERROR_UNKNOWN));

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence(1, false);
    }

    /**
     * Validate that isSupported() returns true when IServiceManager finds the vendor HAL daemon in
     * the VINTF.
     */
    @Test
    public void testIsSupportedTrue() throws Exception {
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15);
        executeAndValidateInitializationSequence();
        assertTrue(mDut.isSupported());
    }

    /**
     * Validate that isSupported() returns false when IServiceManager does not find the vendor HAL
     * daemon in the VINTF.
     */
    @Test
    public void testIsSupportedFalse() throws Exception {
        when(mServiceManagerMock.listManifestByInterface(eq(IWifi.kInterfaceName)))
                .thenReturn(new ArrayList());
        mInOrder = inOrder(mServiceManagerMock, mWifiMock);
        executeAndValidateInitializationSequence(false);
        assertFalse(mDut.isSupported());
    }

    /**
     * Validate RTT configuration when the callback is registered first and the chip is
     * configured later - i.e. RTT isn't available immediately.
     */
    @Test
    public void testAndTriggerRttLifecycleCallbacksRegBeforeChipConfig() throws Exception {
        HalDeviceManager.InterfaceRttControllerLifecycleCallback cb = mock(
                HalDeviceManager.InterfaceRttControllerLifecycleCallback.class);

        InOrder io = inOrder(cb);

        // initialize a test chip (V1 is fine since we're not testing any specifics of
        // concurrency in this test).
        ChipMockBase chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // register initial cb: don't expect RTT since chip isn't configured
        mDut.registerRttControllerLifecycleCallback(cb, mHandler);
        mTestLooper.dispatchAll();
        io.verify(cb, times(0)).onNewRttController(any());

        // create a STA - that will get the chip configured and get us an RTT controller
        validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV1.STA_CHIP_MODE_ID,
                null, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        verify(chipMock.chip).createRttController(any(), any());
        io.verify(cb).onNewRttController(any());

        verifyNoMoreInteractions(cb);
    }

    /**
     * Validate the RTT Controller lifecycle using a multi-mode chip (i.e. a chip which can
     * switch modes, during which RTT is destroyed).
     *
     * 1. Validate that an RTT is created as soon as the callback is registered - if the chip
     * is already configured (i.e. it is possible to create the RTT controller).
     *
     * 2. Validate that only the registered callback is triggered, not previously registered ones
     * and not duplicate ones.
     *
     * 3. Validate that onDestroy callbacks are triggered on mode change.
     */
    @Test
    public void testAndTriggerRttLifecycleCallbacksMultiModeChip() throws Exception {
        HalDeviceManager.InterfaceRttControllerLifecycleCallback cb1 = mock(
                HalDeviceManager.InterfaceRttControllerLifecycleCallback.class);
        HalDeviceManager.InterfaceRttControllerLifecycleCallback cb2 = mock(
                HalDeviceManager.InterfaceRttControllerLifecycleCallback.class);

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InOrder io1 = inOrder(cb1);
        InOrder io2 = inOrder(cb2);

        // initialize a test chip (V1 is a must since we're testing a multi-mode chip) & create a
        // STA (which will configure the chip).
        ChipMockBase chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV1.STA_CHIP_MODE_ID,
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        mInOrder.verify(chipMock.chip, times(0)).createRttController(any(), any());

        // register initial cb - expect the cb right away
        mDut.registerRttControllerLifecycleCallback(cb1, mHandler);
        mTestLooper.dispatchAll();
        verify(chipMock.chip).createRttController(any(), any());
        io1.verify(cb1).onNewRttController(mRttControllerMock);

        // register a second callback and the first one again
        mDut.registerRttControllerLifecycleCallback(cb2, mHandler);
        mDut.registerRttControllerLifecycleCallback(cb1, mHandler);
        mTestLooper.dispatchAll();
        io2.verify(cb2).onNewRttController(mRttControllerMock);

        // change to AP mode (which for TestChipV1 doesn't allow RTT): trigger onDestroyed for all
        doAnswer(new GetBoundIfaceAnswer(false)).when(mRttControllerMock).getBoundIface(any());
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.STA_CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP,
                "wlan0",
                TestChipV1.AP_CHIP_MODE_ID,
                new IWifiIface[]{staIface}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(staIface), staDestroyedListener)
        );
        mTestLooper.dispatchAll();
        verify(chipMock.chip, times(2)).createRttController(any(), any()); // but returns a null!
        io1.verify(cb1).onRttControllerDestroyed();
        io2.verify(cb2).onRttControllerDestroyed();

        // change back to STA mode (which for TestChipV1 will re-allow RTT): trigger onNew for all
        validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.AP_CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV1.STA_CHIP_MODE_ID,
                new IWifiIface[]{apIface}, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_0, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(apIface), apDestroyedListener)
        );
        mTestLooper.dispatchAll();
        verify(chipMock.chip, times(3)).createRttController(any(), any());
        io1.verify(cb1).onNewRttController(mRttControllerMock);
        io2.verify(cb2).onNewRttController(mRttControllerMock);

        verifyNoMoreInteractions(cb1, cb2);
    }

    /**
     * Validate the RTT Controller lifecycle using a single-mode chip. Specifically validate
     * that RTT isn't impacted during STA -> AP change.
     */
    @Test
    public void testAndTriggerRttLifecycleCallbacksSingleModeChip() throws Exception {
        HalDeviceManager.InterfaceRttControllerLifecycleCallback cb = mock(
                HalDeviceManager.InterfaceRttControllerLifecycleCallback.class);

        InOrder io = inOrder(cb);

        // initialize a test chip (V2 is a must since we need a single mode chip)
        // & create a STA (which will configure the chip).
        ChipMockBase chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();
        IWifiIface sta = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV2.CHIP_MODE_ID,
                null, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        mInOrder.verify(chipMock.chip, times(0)).createRttController(any(), any());

        // register initial cb - expect the cb right away
        mDut.registerRttControllerLifecycleCallback(cb, mHandler);
        mTestLooper.dispatchAll();
        verify(chipMock.chip).createRttController(any(), any());
        io.verify(cb).onNewRttController(mRttControllerMock);

        // create an AP: no mode change for TestChipV2 -> expect no impact on RTT
        validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP,
                "wlan1",
                TestChipV2.CHIP_MODE_ID,
                null, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        mTestLooper.dispatchAll();

        doAnswer(new GetBoundIfaceAnswer(false)).when(mRttControllerMock).getBoundIface(any());
        chipMock.chipModeIdValidForRtt = -1;
        mDut.removeIface(sta);
        mTestLooper.dispatchAll();
        verify(chipMock.chip, times(2)).createRttController(any(), any());
        io.verify(cb).onRttControllerDestroyed();

        verifyNoMoreInteractions(cb);
    }

    /**
     * Validate a flow sequence for test chip 1:
     * - create STA (privileged app)
     * - create AP (system app): will get refused
     * - replace STA requestorWs with fg app
     * - create AP (system app)
     */
    @Test
    public void testReplaceRequestorWs() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // initialize a test chip & create a STA (which will configure the chip).
        ChipMockBase chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create STA interface from privileged app: should succeed.
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV1.STA_CHIP_MODE_ID,
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // get AP interface from a system app: should fail
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(true);
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_1);
        assertNull("Should not create this AP", apDetails);
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_1);
        assertNull("Should not create this AP", apDetails);
        IWifiApIface apIface = mDut.createApIface(
                CHIP_CAPABILITY_ANY, null, null, TEST_WORKSOURCE_1, false, mSoftApManager);
        collector.checkThat("not allocated interface", apIface, IsNull.nullValue());

        // Now replace the requestorWs (fg app now) for the STA iface.
        when(mWorkSourceHelper2.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper2.hasAnyForegroundAppRequest(true)).thenReturn(true);
        assertTrue(mDut.replaceRequestorWs(staIface, TEST_WORKSOURCE_2));

        // get AP interface again from a system app: should succeed now
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(true);
        apIface = (IWifiApIface) validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.STA_CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP,
                "wlan0",
                TestChipV1.AP_CHIP_MODE_ID,
                new IWifiIface[]{staIface}, // tearDownList
                null, // destroyedListener
                TEST_WORKSOURCE_1, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(staIface), staDestroyedListener)
        );
        collector.checkThat("not allocated interface", apIface, IsNull.notNullValue());
    }


    //////////////////////////////////////////////////////////////////////////////////////
    // Chip Specific Tests - but should work on all chips!
    // (i.e. add copies for each test chip)
    //////////////////////////////////////////////////////////////////////////////////////

    // TestChipV1

    /**
     * Validate creation of STA interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateStaInterfaceNoInitModeTestChipV1() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV1(), HDM_CREATE_IFACE_STA, "wlan0",
                TestChipV1.STA_CHIP_MODE_ID);
    }

    /**
     * Validate creation of AP interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateApInterfaceNoInitModeTestChipV1() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV1(), HDM_CREATE_IFACE_AP, "wlan0",
                TestChipV1.AP_CHIP_MODE_ID);
    }

    /**
     * Validate creation of P2P interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateP2pInterfaceNoInitModeTestChipV1() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV1(), HDM_CREATE_IFACE_P2P, "p2p0",
                TestChipV1.STA_CHIP_MODE_ID);
    }

    /**
     * Validate creation of NAN interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateNanInterfaceNoInitModeTestChipV1() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV1(), HDM_CREATE_IFACE_NAN, "wlan0",
                TestChipV1.STA_CHIP_MODE_ID);
    }

    // TestChipV2

    /**
     * Validate creation of AP interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateApInterfaceNoInitModeTestChipV2() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV2(), HDM_CREATE_IFACE_AP, "wlan0",
                TestChipV2.CHIP_MODE_ID);
    }

    /**
     * Validate creation of P2P interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateP2pInterfaceNoInitModeTestChipV2() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV2(), HDM_CREATE_IFACE_P2P, "p2p0",
                TestChipV2.CHIP_MODE_ID);
    }

    /**
     * Validate creation of NAN interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateNanInterfaceNoInitModeTestChipV2() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV2(), HDM_CREATE_IFACE_NAN, "wlan0",
                TestChipV2.CHIP_MODE_ID);
    }

    // TestChipV3
    /**
     * Validate creation of AP interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateApInterfaceNoInitModeTestChipV3() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV3(), HDM_CREATE_IFACE_AP, "wlan0",
                TestChipV3.CHIP_MODE_ID);
    }

    /**
     * Validate creation of P2P interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateP2pInterfaceNoInitModeTestChipV3() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV3(), HDM_CREATE_IFACE_P2P, "p2p0",
                TestChipV3.CHIP_MODE_ID);
    }

    /**
     * Validate creation of NAN interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateNanInterfaceNoInitModeTestChipV3() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV3(), HDM_CREATE_IFACE_NAN, "wlan0",
                TestChipV3.CHIP_MODE_ID);
    }

    // TestChipV4

    /**
     * Validate creation of STA interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateStaInterfaceNoInitModeTestChipV4() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV4(), HDM_CREATE_IFACE_STA, "wlan0",
                TestChipV4.CHIP_MODE_ID);
    }

    /**
     * Validate creation of AP interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateApInterfaceNoInitModeTestChipV4() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV4(), HDM_CREATE_IFACE_AP, "wlan0",
                TestChipV4.CHIP_MODE_ID);
    }

    /**
     * Validate creation of P2P interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateP2pInterfaceNoInitModeTestChipV4() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV4(), HDM_CREATE_IFACE_P2P, "p2p0",
                TestChipV4.CHIP_MODE_ID);
    }

    /**
     * Validate creation of NAN interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateNanInterfaceNoInitModeTestChipV4() throws Exception {
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV4(), HDM_CREATE_IFACE_NAN, "wlan0",
                TestChipV4.CHIP_MODE_ID);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // TestChipV1 Specific Tests
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Validate creation of AP interface when in STA mode - but with no interface created. Expect
     * a change in chip mode.
     */
    @Test
    public void testCreateApWithStaModeUpTestChipV1() throws Exception {
        final String name = "wlan0";

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        IWifiApIface iface = (IWifiApIface) validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.STA_CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                name, // ifaceName
                TestChipV1.AP_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: stop Wi-Fi
        mDut.stop();
        mTestLooper.dispatchAll();

        // verify: callback triggered
        verify(idl).onDestroyed(getName(iface));
        verify(mManagerStatusListenerMock, times(2)).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl);
    }

    /**
     * Verify that when the thread that caused an iface to get destroyed is not the thread the
     * onDestroy callback is intended to be invoked on, then onDestroy is will get posted to the
     * correct thread.
     */
    @Test
    public void testOnDestroyedWithHandlerTriggeredOnDifferentThread() throws Exception {
        long currentThreadId = 983757; // arbitrary current thread ID
        when(mWifiInjector.getCurrentThreadId()).thenReturn(currentThreadId);
        // RETURNS_DEEP_STUBS allows mocking nested method calls
        Handler staIfaceOnDestroyedHandler = mock(Handler.class, Mockito.RETURNS_DEEP_STUBS);
        // Configure the handler to be on a different thread as the current thread.
        when(staIfaceOnDestroyedHandler.getLooper().getThread().getId())
                .thenReturn(currentThreadId + 1);
        InterfaceDestroyedListener staIdl = mock(InterfaceDestroyedListener.class);
        ArgumentCaptor<Runnable> lambdaCaptor = ArgumentCaptor.forClass(Runnable.class);

        // simulate adding a STA iface and then stopping wifi
        simulateStartAndStopWifi(staIdl, staIfaceOnDestroyedHandler);

        // Verify a runnable is posted because current thread is different than the intended thread
        // for running "onDestroyed"
        verify(staIfaceOnDestroyedHandler).postAtFrontOfQueue(lambdaCaptor.capture());

        // Verify onDestroyed is only run after the posted runnable is dispatched
        verify(staIdl, never()).onDestroyed("wlan0");
        lambdaCaptor.getValue().run();
        verify(staIdl).onDestroyed("wlan0");
    }

    /**
     * Verify that when the thread that caused an iface to get destroyed is already the thread the
     * onDestroy callback is intended to be invoked on, then onDestroy is invoked directly.
     */
    @Test
    public void testOnDestroyedWithHandlerTriggeredOnSameThread() throws Exception {
        long currentThreadId = 983757; // arbitrary current thread ID
        when(mWifiInjector.getCurrentThreadId()).thenReturn(currentThreadId);
        // RETURNS_DEEP_STUBS allows mocking nested method calls
        Handler staIfaceOnDestroyedHandler = mock(Handler.class, Mockito.RETURNS_DEEP_STUBS);
        // Configure the handler thread ID so it's the same as the current thread.
        when(staIfaceOnDestroyedHandler.getLooper().getThread().getId())
                .thenReturn(currentThreadId);
        InterfaceDestroyedListener staIdl = mock(InterfaceDestroyedListener.class);

        // simulate adding a STA iface and then stopping wifi
        simulateStartAndStopWifi(staIdl, staIfaceOnDestroyedHandler);

        // Verify a runnable is never posted
        verify(staIfaceOnDestroyedHandler, never()).postAtFrontOfQueue(any());
        // Verify onDestroyed is triggered directly
        verify(staIdl).onDestroyed("wlan0");
    }

    private void simulateStartAndStopWifi(InterfaceDestroyedListener staIdl,
            Handler staIfaceOnDestroyedHandler) throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();

        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();

        // start Wi-Fi
        assertTrue(mDut.start());

        // Create STA Iface.
        IWifiStaIface staIface = mock(IWifiStaIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(staIface).getName(
                any(IWifiIface.getNameCallback.class));
        doAnswer(new GetTypeAnswer(IfaceType.STA)).when(staIface).getType(
                any(IWifiIface.getTypeCallback.class));
        doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, staIface)).when(
                chipMock.chip).createStaIface(any(IWifiChip.createStaIfaceCallback.class));
        assertEquals(staIface, mDut.createStaIface(staIdl, staIfaceOnDestroyedHandler,
                TEST_WORKSOURCE_0));

        mInOrder.verify(chipMock.chip).configureChip(TestChipV1.STA_CHIP_MODE_ID);

        // Stop Wi-Fi
        mDut.stop();
        mInOrder.verify(mWifiMock).stop();
    }

    /**
     * Validate creation of AP interface when in STA mode with a single STA iface created.
     * Expect a change in chip mode.
     */
    @Test
    public void testCreateApWithStaIfaceUpTestChipV1UsingHandlerListeners() throws Exception {
        // Make the creation and InterfaceDestroyListener running on the same thread to verify the
        // order in the real scenario.
        when(mWifiInjector.getCurrentThreadId())
                .thenReturn(mTestLooper.getLooper().getThread().getId());

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();

        InterfaceDestroyedListener staIdl = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener apIdl = mock(
                InterfaceDestroyedListener.class);

        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock, staIdl, apIdl);
        executeAndValidateInitializationSequence();

        // Register listener & start Wi-Fi
        mDut.registerStatusListener(mManagerStatusListenerMock, null);
        assertTrue(mDut.start());
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        // Create STA Iface first.
        IWifiStaIface staIface = mock(IWifiStaIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(staIface).getName(
                any(IWifiIface.getNameCallback.class));
        doAnswer(new GetTypeAnswer(IfaceType.STA)).when(staIface).getType(
                any(IWifiIface.getTypeCallback.class));
        doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, staIface)).when(
                chipMock.chip).createStaIface(any(IWifiChip.createStaIfaceCallback.class));
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertTrue("Expecting nothing to destroy on creating STA", staDetails.isEmpty());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertTrue("Expecting nothing to destroy on creating STA", staDetails.isEmpty());
        assertEquals(staIface, mDut.createStaIface(staIdl, mHandler, TEST_WORKSOURCE_0));

        mInOrder.verify(chipMock.chip).configureChip(TestChipV1.STA_CHIP_MODE_ID);

        // Now Create AP Iface.
        IWifiApIface apIface = mock(IWifiApIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(apIface).getName(
                any(IWifiIface.getNameCallback.class));
        doAnswer(new GetTypeAnswer(IfaceType.AP)).when(apIface).getType(
                any(IWifiIface.getTypeCallback.class));
        doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, apIface)).when(
                chipMock.chip).createApIface(
                any(IWifiChip.createApIfaceCallback.class));
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_0);
        assertEquals("Should get STA destroy details", 1, apDetails.size());
        assertEquals("Need to destroy the STA", Pair.create(IfaceType.STA, TEST_WORKSOURCE_0),
                apDetails.get(0));
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_0);
        assertEquals("Should get STA destroy details", 1, apDetails.size());
        assertEquals("Need to destroy the STA", Pair.create(IfaceType.STA, TEST_WORKSOURCE_0),
                apDetails.get(0));
        assertEquals(apIface, mDut.createApIface(
                CHIP_CAPABILITY_ANY, apIdl, mHandler, TEST_WORKSOURCE_0, false, mSoftApManager));
        mInOrder.verify(chipMock.chip).removeStaIface(getName(staIface));
        mInOrder.verify(staIdl).onDestroyed(getName(staIface));
        mInOrder.verify(chipMock.chip).configureChip(TestChipV1.AP_CHIP_MODE_ID);

        // Stop Wi-Fi
        mDut.stop();

        mInOrder.verify(mWifiMock).stop();
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();
        mInOrder.verify(apIdl).onDestroyed(getName(apIface));

        verifyNoMoreInteractions(mManagerStatusListenerMock, staIdl, apIdl);
    }

    /**
     * Validate creation of interface with valid listener but Null handler will be failed.
     */
    @Test
    public void testCreateIfaceTestChipV1UsingNullHandlerListeners() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock, idl);
        executeAndValidateInitializationSequence();

        // Register listener & start Wi-Fi
        mDut.registerStatusListener(mManagerStatusListenerMock, null);
        assertTrue(mDut.start());
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        // Create STA Iface will be failure because null handler.
        IWifiStaIface staIface = mock(IWifiStaIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(staIface).getName(
                any(IWifiIface.getNameCallback.class));
        doAnswer(new GetTypeAnswer(IfaceType.STA)).when(staIface).getType(
                any(IWifiIface.getTypeCallback.class));
        doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, staIface)).when(
                chipMock.chip).createStaIface(any(IWifiChip.createStaIfaceCallback.class));
        assertNull(mDut.createStaIface(idl, null, TEST_WORKSOURCE_0));

        // Create AP Iface will be failure because null handler.
        IWifiApIface apIface = mock(IWifiApIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(apIface).getName(
                any(IWifiIface.getNameCallback.class));
        doAnswer(new GetTypeAnswer(IfaceType.AP)).when(apIface).getType(
                any(IWifiIface.getTypeCallback.class));
        doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, apIface)).when(
                chipMock.chip).createApIface(
                any(IWifiChip.createApIfaceCallback.class));
        assertNull(mDut.createApIface(
                CHIP_CAPABILITY_ANY, idl, null, TEST_WORKSOURCE_0, false, mSoftApManager));

        // Create NAN Iface will be failure because null handler.
        IWifiNanIface nanIface = mock(IWifiNanIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(nanIface).getName(
                any(IWifiIface.getNameCallback.class));
        doAnswer(new GetTypeAnswer(IfaceType.NAN)).when(nanIface).getType(
                any(IWifiIface.getTypeCallback.class));
        doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, nanIface)).when(
                chipMock.chip).createNanIface(
                any(IWifiChip.createNanIfaceCallback.class));
        assertNull(mDut.createNanIface(idl, null, TEST_WORKSOURCE_0));

        // Create P2P Iface will be failure because null handler.
        IWifiP2pIface p2pIface = mock(IWifiP2pIface.class);
        doAnswer(new GetNameAnswer("wlan0")).when(p2pIface).getName(
                any(IWifiIface.getNameCallback.class));
        doAnswer(new GetTypeAnswer(IfaceType.P2P)).when(p2pIface).getType(
                any(IWifiIface.getTypeCallback.class));
        doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, p2pIface)).when(
                chipMock.chip).createP2pIface(
                any(IWifiChip.createP2pIfaceCallback.class));
        assertNull(mDut.createP2pIface(idl, null, TEST_WORKSOURCE_0));

        // Stop Wi-Fi
        mDut.stop();

        mInOrder.verify(mWifiMock).stop();
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl);
    }

    /**
     * Validate creation of AP interface when in AP mode - but with no interface created. Expect
     * no change in chip mode.
     */
    @Test
    public void testCreateApWithApModeUpTestChipV1() throws Exception {
        final String name = "wlan0";

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        IWifiApIface iface = (IWifiApIface) validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV1.AP_CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                name, // ifaceName
                TestChipV1.AP_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: stop Wi-Fi
        mDut.stop();
        mTestLooper.dispatchAll();

        // verify: callback triggered
        verify(idl).onDestroyed(getName(iface));
        verify(mManagerStatusListenerMock, times(2)).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl);
    }

    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - get nan available listener
     *   - Can create NAN when requested
     */
    @Test
    public void testP2pAndNanInteractionsTestChipV1() throws Exception {
        runP2pAndNanExclusiveInteractionsTestChip(new TestChipV1(), TestChipV1.STA_CHIP_MODE_ID);
    }

    /**
     * Validates that trying to allocate a STA from a lower priority app and then another STA from
     * a privileged app exists, the request fails. Only one STA at a time is permitted (by
     * TestChipV1 chip).
     */
    @Test
    public void testDuplicateStaRequestsFromLowerPriorityAppTestChipV1() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener1 = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener staDestroyedListener2 = mock(
                InterfaceDestroyedListener.class);

        // get STA interface (from a privileged app)
        IWifiIface staIface1 = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener1, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface1, IsNull.notNullValue());

        // get STA interface again (from a system app)
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(true);
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_1);
        assertNotNull("Should not have a problem if STA already exists", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_1);
        assertNull("Should not be able to create a new STA", staDetails);
        IWifiIface staIface2 = mDut.createStaIface(
                staDestroyedListener2, mHandler, TEST_WORKSOURCE_1);
        collector.checkThat("STA created", staIface2, IsNull.nullValue());

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener1,
                staDestroyedListener2);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for all chips.
     */
    @Test
    public void testGetSupportedIfaceTypesAllTestChipV1() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(IfaceType.AP);
        correctResults.add(IfaceType.STA);
        correctResults.add(IfaceType.P2P);
        correctResults.add(IfaceType.NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for a specific chip.
     */
    @Test
    public void testGetSupportedIfaceTypesOneChipTestChipV1() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes(chipMock.chip);

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(IfaceType.AP);
        correctResults.add(IfaceType.STA);
        correctResults.add(IfaceType.P2P);
        correctResults.add(IfaceType.NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate {@link HalDeviceManager#canDeviceSupportCreateTypeCombo(SparseArray)}
     */
    @Test
    public void testCanDeviceSupportCreateTypeComboChipV1() throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();

        // Try to query iface support before starting the HAL. Should return false without any
        // stored static chip info.
        when(mWifiMock.isStarted()).thenReturn(false);
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
            }}
        ));
        verify(mWifiMock, never()).getChipIds(any());
        when(mWifiMock.isStarted()).thenReturn(true);
        executeAndValidateStartupSequence();

        clearInvocations(mWifiMock);

        // Verify that the latest static chip info is saved to store.
        verify(mWifiSettingsConfigStore).put(eq(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO),
                eq(new JSONArray(TestChipV1.STATIC_CHIP_INFO_JSON_STRING).toString()));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
            }}
        ));
        // AP should now be supported after we read directly from the chip.
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.NAN, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.NAN, 1);
            }}
        ));

        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.AP, 1);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 2);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.P2P, 1);
                put(IfaceConcurrencyType.NAN, 1);
            }}
        ));

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validate {@link HalDeviceManager#canDeviceSupportCreateTypeCombo(SparseArray)} with stored
     * static chip info.
     */
    @Test
    public void testCanDeviceSupportCreateTypeComboChipV1WithStoredStaticChipInfo()
            throws Exception {
        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();

        // Try to query iface support before starting the HAL. Should return true with the stored
        // static chip info.
        when(mWifiMock.isStarted()).thenReturn(false);
        when(mWifiSettingsConfigStore.get(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO))
                .thenReturn(TestChipV1.STATIC_CHIP_INFO_JSON_STRING);
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.NAN, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.NAN, 1);
            }}
        ));

        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.AP, 1);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 2);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.P2P, 1);
                put(IfaceConcurrencyType.NAN, 1);
            }}
        ));

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV1() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // get STA interface from system app.
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // FG app not allowed to create AP interface.
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // New system app not allowed to create AP interface.
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(true);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // Privileged app allowed to create AP interface.
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(true);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // FG app allowed to create NAN interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // BG app allowed to create P2P interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(false);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV1ForR() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV1 chipMock = new TestChipV1();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // get STA interface.
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV1.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // Allowed to create AP interface (since AP can teardown STA interface)
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // Allow to create NAN interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // Allow to create P2P interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(false);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // TestChipV2 Specific Tests
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Validate a flow sequence for test chip 2:
     * - create STA (system app)
     * - create P2P (system app)
     * - create NAN (privileged app): should tear down P2P first
     * - create AP (privileged app)
     * - create STA (system app): will get refused
     * - create AP (system app): will get refuse
     * - tear down AP
     * - create STA (system app)
     * - create STA (system app): will get refused
     * - create AP (privileged app): should get created and the last created STA should get
     *   destroyed
     * - tear down P2P
     * - create NAN (system app)
     */
    @Test
    public void testInterfaceCreationFlowTestChipV2() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        TestChipV2 chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener staDestroyedListener2 = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create STA (system app)
        when(mClock.getUptimeSinceBootMillis()).thenReturn(15L);
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA interface wasn't created", staIface, IsNull.notNullValue());

        // create P2P (system app)
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("P2P interface wasn't created", p2pIface, IsNull.notNullValue());

        // create NAN (system app)
        IWifiIface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{p2pIface}, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(
                        getName(p2pIface), p2pDestroyedListener)
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        // create AP (privileged app)
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2 // requestorWs
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());

        // request STA2 (system app): should fail
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not be able to create a new STA", staDetails);
        IWifiIface staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // request AP2 (system app): should fail
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_0);
        assertNotNull("Should not fail when asking for same AP", apDetails);
        assertEquals(0, apDetails.size());
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_0);
        assertNull("Should not be able to create a new AP", apDetails);
        IWifiIface apIface2 = mDut.createApIface(
                CHIP_CAPABILITY_ANY, null, null, TEST_WORKSOURCE_0, false, mSoftApManager);
        collector.checkThat("AP2 should not be created", apIface2, IsNull.nullValue());

        // tear down AP
        mDut.removeIface(apIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeApIface("wlan1");
        verify(apDestroyedListener).onDestroyed(getName(apIface));

        // create STA2 (system app): using a later clock
        when(mClock.getUptimeSinceBootMillis()).thenReturn(20L);
        staIface2 = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener2, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA 2 interface wasn't created", staIface2, IsNull.notNullValue());

        // request STA3 (system app): should fail
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not create this STA", staDetails);
        IWifiIface staIface3 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("STA3 should not be created", staIface3, IsNull.nullValue());

        // create AP (privileged app) - this will destroy the last STA created, i.e. STA2
        apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{staIface2}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                // destroyedInterfacesDestroyedListeners...
                new InterfaceDestroyedListenerWithIfaceName(
                        getName(staIface2), staDestroyedListener2)
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());

        // tear down NAN
        mDut.removeIface(nanIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeNanIface("wlan0");
        verify(nanDestroyedListener).onDestroyed(getName(nanIface));

        // create NAN
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener,
                staDestroyedListener2, apDestroyedListener, p2pDestroyedListener,
                nanDestroyedListener);
    }

    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - get nan available listener
     *   - Can create NAN when requested
     */
    @Test
    public void testP2pAndNanInteractionsTestChipV2() throws Exception {
        runP2pAndNanExclusiveInteractionsTestChip(new TestChipV2(), TestChipV2.CHIP_MODE_ID);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for all chips.
     */
    @Test
    public void testGetSupportedIfaceTypesAllTestChipV2() throws Exception {
        TestChipV2 chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(IfaceType.AP);
        correctResults.add(IfaceType.STA);
        correctResults.add(IfaceType.P2P);
        correctResults.add(IfaceType.NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for a specific chip.
     */
    @Test
    public void testGetSupportedIfaceTypesOneChipTestChipV2() throws Exception {
        TestChipV2 chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes(chipMock.chip);

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(IfaceType.AP);
        correctResults.add(IfaceType.STA);
        correctResults.add(IfaceType.P2P);
        correctResults.add(IfaceType.NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate {@link HalDeviceManager#canDeviceSupportCreateTypeCombo(SparseArray)}
     */
    @Test
    public void testCanDeviceSupportIfaceComboTestChipV2() throws Exception {
        TestChipV2 chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        // Try to query iface support before starting the HAL. Should return true with the stored
        // static chip info.
        when(mWifiMock.isStarted()).thenReturn(false);
        when(mWifiSettingsConfigStore.get(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO))
                .thenReturn(TestChipV2.STATIC_CHIP_INFO_JSON_STRING);
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
            }}
        ));
        verify(mWifiMock, never()).getChipIds(any());
        when(mWifiMock.isStarted()).thenReturn(true);
        executeAndValidateStartupSequence();

        clearInvocations(mWifiMock);

        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.NAN, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.P2P, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.NAN, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 2);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.P2P, 1);
                put(IfaceConcurrencyType.AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.P2P, 1);
                put(IfaceConcurrencyType.AP, 1);
            }}
        ));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.AP, 1);
                put(IfaceConcurrencyType.NAN, 1);
            }}
        ));

        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.P2P, 1);
                put(IfaceConcurrencyType.NAN, 1);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.AP, 2);
            }}
        ));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                put(IfaceConcurrencyType.STA, 2);
                put(IfaceConcurrencyType.AP, 1);
            }}
        ));

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV2() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV2 chipMock = new TestChipV2();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // get STA interface from system app.
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // get AP interface from system app.
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV2.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV2.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("AP created", apIface, IsNull.notNullValue());

        // FG app not allowed to create STA interface.
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // New system app not allowed to create STA interface.
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(true);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // Privileged app allowed to create STA interface.
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(true);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // FG app allowed to create NAN interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // BG app allowed to create P2P interface (since there is no need to delete any interfaces).
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(false);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // TestChipV3 Specific Tests
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Validate a flow sequence for test chip 3:
     * - create STA (system app)
     * - create P2P (system app)
     * - create NAN (privileged app): should tear down P2P first
     * - create AP (privileged app): should tear down NAN first
     * - create STA (system app): will get refused
     * - create AP (system app): will get refused
     * - request P2P (system app): failure
     * - request P2P (privileged app): failure
     * - tear down AP
     * - create STA (system app)
     * - create STA (system app): will get refused
     * - create NAN (privileged app): should tear down last created STA
     * - create STA (foreground app): will get refused
     */
    @Test
    public void testInterfaceCreationFlowTestChipV3() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        TestChipV3 chipMock = new TestChipV3();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener staDestroyedListener2 = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create STA (system app)
        when(mClock.getUptimeSinceBootMillis()).thenReturn(15L);
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA interface wasn't created", staIface, IsNull.notNullValue());

        // create P2P (system app)
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("P2P interface wasn't created", p2pIface, IsNull.notNullValue());

        // create NAN (privileged app): will destroy P2P
        IWifiIface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{p2pIface}, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(p2pIface), p2pDestroyedListener)
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        // create AP (privileged app): will destroy NAN
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{nanIface}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(nanIface), nanDestroyedListener)
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());
        verify(chipMock.chip).removeP2pIface("p2p0");

        // request STA2 (system app): should fail
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not create this STA", staDetails);
        IWifiIface staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // request AP2 (system app): should fail
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_0);
        assertNotNull("Should not fail when asking for same AP", apDetails);
        assertEquals(0, apDetails.size());
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this AP", apDetails);
        IWifiIface apIface2 = mDut.createApIface(
                CHIP_CAPABILITY_ANY, null, null, TEST_WORKSOURCE_0, false, mSoftApManager);
        collector.checkThat("AP2 should not be created", apIface2, IsNull.nullValue());

        // request P2P (system app): should fail
        List<Pair<Integer, WorkSource>> p2pDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_P2P, true, TEST_WORKSOURCE_0);
        assertNull("should not create this p2p", p2pDetails);
        p2pIface = mDut.createP2pIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("P2P should not be created", p2pIface, IsNull.nullValue());

        // request P2P (privileged app): should fail
        p2pDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_P2P, true, TEST_WORKSOURCE_2);
        assertNull("should not create this p2p", p2pDetails);
        p2pIface = mDut.createP2pIface(null, null, TEST_WORKSOURCE_2);
        collector.checkThat("P2P should not be created", p2pIface, IsNull.nullValue());

        // tear down AP
        mDut.removeIface(apIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeApIface("wlan1");
        verify(apDestroyedListener).onDestroyed(getName(apIface));

        // create STA2 (system app): using a later clock
        when(mClock.getUptimeSinceBootMillis()).thenReturn(20L);
        staIface2 = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener2, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA 2 interface wasn't created", staIface2, IsNull.notNullValue());

        // request STA3 (system app): should fail
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not create this STA", staDetails);
        IWifiIface staIface3 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("STA3 should not be created", staIface3, IsNull.nullValue());

        // create NAN (privileged app): should destroy the last created STA (STA2)
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{staIface2}, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(
                        getName(staIface2), staDestroyedListener2)
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        verify(chipMock.chip).removeStaIface("wlan1");
        verify(staDestroyedListener2).onDestroyed(getName(staIface2));

        // request STA2 (foreground app): should fail
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_1);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_1);
        assertNull("should not create this STA", staDetails);
        staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_1);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener,
                staDestroyedListener2, apDestroyedListener, p2pDestroyedListener,
                nanDestroyedListener);
    }

    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - get nan available listener
     *   - Can create NAN when requested
     */
    @Test
    public void testP2pAndNanInteractionsTestChipV3() throws Exception {
        runP2pAndNanExclusiveInteractionsTestChip(new TestChipV3(), TestChipV3.CHIP_MODE_ID);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for all chips.
     */
    @Test
    public void testGetSupportedIfaceTypesAllTestChipV3() throws Exception {
        TestChipV3 chipMock = new TestChipV3();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(IfaceType.AP);
        correctResults.add(IfaceType.STA);
        correctResults.add(IfaceType.P2P);
        correctResults.add(IfaceType.NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for a specific chip.
     */
    @Test
    public void testGetSupportedIfaceTypesOneChipTestChipV3() throws Exception {
        TestChipV3 chipMock = new TestChipV3();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes(chipMock.chip);

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(IfaceType.AP);
        correctResults.add(IfaceType.STA);
        correctResults.add(IfaceType.P2P);
        correctResults.add(IfaceType.NAN);

        assertEquals(correctResults, results);
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV3() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV3 chipMock = new TestChipV3();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // get STA interface from system app.
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // get AP interface from system app.
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("AP created", apIface, IsNull.notNullValue());

        // FG app not allowed to create STA interface.
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // New system app not allowed to create STA interface.
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(true);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // Privileged app allowed to create STA interface.
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(true);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // FG app not allowed to create NAN interface.
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // Privileged app allowed to create P2P interface.
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(true);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // TestChipV4 Specific Tests
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Validate a flow sequence for test chip 4:
     * - create STA (system app)
     * - create P2P (system app)
     * - create NAN (privileged app): should tear down P2P first
     * - create AP (privileged app): should tear down NAN first
     * - create STA (system app): will get refused
     * - create AP (system app): will get refused
     * - request P2P (system app): failure
     * - request P2P (privileged app): failure
     * - tear down AP
     * - create STA (system app): will get refused
     * - create NAN (privileged app)
     * - create STA (foreground app): will get refused
     */
    @Test
    public void testInterfaceCreationFlowTestChipV4() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener staDestroyedListener2 = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create STA (system app)
        when(mClock.getUptimeSinceBootMillis()).thenReturn(15L);
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA interface wasn't created", staIface, IsNull.notNullValue());

        // create P2P (system app)
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("P2P interface wasn't created", p2pIface, IsNull.notNullValue());

        // create NAN (privileged app): will destroy P2P
        IWifiIface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{p2pIface}, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(p2pIface), p2pDestroyedListener)
        );
        collector.checkThat("allocated NAN interface", nanIface, IsNull.notNullValue());

        // create AP (privileged app): will destroy NAN
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{nanIface}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(nanIface), nanDestroyedListener)
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());
        verify(chipMock.chip).removeP2pIface("p2p0");

        // request STA2 (system app): should fail
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not create this STA", staDetails);
        IWifiIface staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // request AP2 (system app): should fail
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_0);
        assertNotNull("Should not fail when asking for same AP", apDetails);
        assertEquals(0, apDetails.size());
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this AP", apDetails);
        IWifiIface apIface2 = mDut.createApIface(
                CHIP_CAPABILITY_ANY, null, null, TEST_WORKSOURCE_0, false, mSoftApManager);
        collector.checkThat("AP2 should not be created", apIface2, IsNull.nullValue());

        // request P2P (system app): should fail
        List<Pair<Integer, WorkSource>> p2pDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_P2P, true, TEST_WORKSOURCE_0);
        assertNull("should not create this p2p", p2pDetails);
        p2pIface = mDut.createP2pIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("P2P should not be created", p2pIface, IsNull.nullValue());

        // request P2P (privileged app): should fail
        p2pDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_P2P, true, TEST_WORKSOURCE_2);
        assertNull("should not create this p2p", p2pDetails);
        p2pIface = mDut.createP2pIface(null, null, TEST_WORKSOURCE_2);
        collector.checkThat("P2P should not be created", p2pIface, IsNull.nullValue());

        // tear down AP
        mDut.removeIface(apIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeApIface("wlan1");
        verify(apDestroyedListener).onDestroyed(getName(apIface));

        // request STA2 (system app): should fail
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("should not create this STA", staDetails);
        staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // create NAN (privileged app)
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2 // requestorWs
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        // request STA2 (foreground app): should fail
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_1);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_1);
        assertNull("should not create this STA", staDetails);
        staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_1);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // tear down STA
        mDut.removeIface(staIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeStaIface("wlan0");
        verify(staDestroyedListener).onDestroyed(getName(staIface));

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener,
                staDestroyedListener2, apDestroyedListener, p2pDestroyedListener,
                nanDestroyedListener);
    }

    @Test
    public void testInterfaceCreationFlowTestChipV4ForR() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);
        InterfaceDestroyedListener staDestroyedListener2 = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create STA
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA interface wasn't created", staIface, IsNull.notNullValue());

        // create P2P
        IWifiIface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("P2P interface wasn't created", p2pIface, IsNull.notNullValue());

        // create NAN: will destroy P2P
        IWifiIface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{p2pIface}, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(p2pIface), p2pDestroyedListener)
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        // create AP: will destroy NAN
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{nanIface}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(nanIface), nanDestroyedListener)
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());
        verify(chipMock.chip).removeP2pIface("p2p0");

        // request STA2 (system app): should fail
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this STA", staDetails);
        IWifiIface staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // request AP2: should fail
        List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP, false, TEST_WORKSOURCE_0);
        assertNotNull("Should not fail when asking for same AP", apDetails);
        assertEquals(0, apDetails.size());
        apDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_AP, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this AP", apDetails);
        IWifiIface apIface2 = mDut.createApIface(
                CHIP_CAPABILITY_ANY, null, null, TEST_WORKSOURCE_0, false, mSoftApManager);
        collector.checkThat("AP2 should not be created", apIface2, IsNull.nullValue());

        // request P2P: should fail
        List<Pair<Integer, WorkSource>> p2pDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_P2P, true, TEST_WORKSOURCE_0);
        assertNull("should not create this p2p", p2pDetails);
        p2pIface = mDut.createP2pIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("P2P should not be created", p2pIface, IsNull.nullValue());

        // request NAN: should fail
        List<Pair<Integer, WorkSource>> nanDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_NAN, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this nan", nanDetails);
        nanIface = mDut.createNanIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("NAN should not be created", nanIface, IsNull.nullValue());

        // tear down AP
        mDut.removeIface(apIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeApIface("wlan1");
        verify(apDestroyedListener).onDestroyed(getName(apIface));

        // request STA2: should fail
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, false, TEST_WORKSOURCE_0);
        assertNotNull("should not fail when asking for same STA", staDetails);
        assertEquals(0, staDetails.size());
        staDetails = mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertNull("Should not create this STA", staDetails);
        staIface2 = mDut.createStaIface(null, null, TEST_WORKSOURCE_0);
        collector.checkThat("STA2 should not be created", staIface2, IsNull.nullValue());

        // create NAN
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_2 // requestorWs
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());
    }

    /**
     * Validate a flow sequence for test chip 3:
     * - create NAN (internal request)
     * - create AP (privileged app): should tear down NAN first
     */
    @Test
    public void testInterfaceCreationFlowTestChipV3WithInternalRequest() throws Exception {
        TestChipV3 chipMock = new TestChipV3();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener apDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // create P2P (internal request)
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnyInternalRequest()).thenReturn(true);
        // create NAN (privileged app): will destroy P2P
        IWifiIface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs)
        );
        collector.checkThat("NAN interface wasn't created", nanIface, IsNull.notNullValue());

        // create AP (privileged app): will destroy NAN
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV3.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV3.CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{nanIface}, // tearDownList
                apDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_1, // requestorWs
                new InterfaceDestroyedListenerWithIfaceName(getName(nanIface), nanDestroyedListener)
        );
        collector.checkThat("AP interface wasn't created", apIface, IsNull.notNullValue());
        verify(chipMock.chip).removeNanIface("wlan0");

        // tear down AP
        mDut.removeIface(apIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip).removeApIface("wlan1");
        verify(apDestroyedListener).onDestroyed(getName(apIface));

        verifyNoMoreInteractions(mManagerStatusListenerMock, apDestroyedListener,
                nanDestroyedListener);
    }


    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - get nan available listener
     *   - Can create NAN when requested
     */
    @Test
    public void testP2pAndNanInteractionsTestChipV4() throws Exception {
        runP2pAndNanExclusiveInteractionsTestChip(new TestChipV4(), TestChipV4.CHIP_MODE_ID);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for all chips.
     */
    @Test
    public void testGetSupportedIfaceTypesAllTestChipV4() throws Exception {
        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(IfaceType.AP);
        correctResults.add(IfaceType.STA);
        correctResults.add(IfaceType.P2P);
        correctResults.add(IfaceType.NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for a specific chip.
     */
    @Test
    public void testGetSupportedIfaceTypesOneChipTestChipV4() throws Exception {
        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes(chipMock.chip);

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(IfaceType.AP);
        correctResults.add(IfaceType.STA);
        correctResults.add(IfaceType.P2P);
        correctResults.add(IfaceType.NAN);

        assertEquals(correctResults, results);
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV4() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // get STA interface from system app.
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // get AP interface from system app.
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("AP created", apIface, IsNull.notNullValue());

        // FG app not allowed to create STA interface.
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // New system app not allowed to create STA interface.
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(true);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // Privileged app allowed to create STA interface.
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(true);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // FG app not allowed to create NAN interface.
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // Privileged app allowed to create P2P interface.
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(true);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    @Test
    public void testIsItPossibleToCreateIfaceTestChipV4ForR() throws Exception {
        assumeFalse(SdkLevel.isAtLeastS());
        final String name = "wlan0";

        TestChipV4 chipMock = new TestChipV4();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // get STA interface.
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // get AP interface.
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV4.CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV4.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("AP created", apIface, IsNull.notNullValue());

        // Not allowed to create STA interface.
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_STA, TEST_WORKSOURCE_1));

        // Not allowed to create AP interface.
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_1));

        // Not allowed to create NAN interface.
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_NAN, TEST_WORKSOURCE_1));

        // Not allowed to create P2P interface.
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P, TEST_WORKSOURCE_1));
    }

    public void verify60GhzIfaceCreation(
            ChipMockBase chipMock, int chipModeId, int finalChipModeId, boolean isWigigSupported)
            throws Exception {
        long requiredChipCapabilities =
                android.hardware.wifi.V1_5.IWifiChip.ChipCapabilityMask.WIGIG;
        chipMock.initialize();
        if (mWifiChipV15 != null) {
            mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                    mWifiChipV15, mManagerStatusListenerMock);
        } else {
            mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                    mManagerStatusListenerMock);
        }
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // get STA interface from system app.
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface staIface;
        if (isWigigSupported) {
            staIface = validateInterfaceSequence(chipMock,
                    false, // chipModeValid
                    -1000, // chipModeId (only used if chipModeValid is true)
                    HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                    "wlan0", // ifaceName
                    finalChipModeId, // finalChipMode
                    requiredChipCapabilities, // requiredChipCapabilities
                    null, // tearDownList
                    mock(InterfaceDestroyedListener.class), // destroyedListener
                    TEST_WORKSOURCE_0 // requestorWs
            );
            collector.checkThat("STA created", staIface, IsNull.notNullValue());
        } else {
            List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                    HDM_CREATE_IFACE_STA, true, requiredChipCapabilities, TEST_WORKSOURCE_1);
            assertNull("Should not create this STA", staDetails);
            staIface = mDut.createStaIface(
                    requiredChipCapabilities, null, null, TEST_WORKSOURCE_1);
            mInOrder.verify(chipMock.chip, times(0)).configureChip(anyInt());
            collector.checkThat("STA should not be created", staIface, IsNull.nullValue());
        }

        // get AP interface from system app.
        when(mWorkSourceHelper0.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper0.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface apIface;
        if (isWigigSupported) {
            apIface = validateInterfaceSequence(chipMock,
                    true, // chipModeValid
                    chipModeId, // chipModeId (only used if chipModeValid is true)
                    HDM_CREATE_IFACE_AP, // createIfaceType
                    "wlan0", // ifaceName
                    finalChipModeId, // finalChipMode
                    requiredChipCapabilities, // requiredChipCapabilities
                    null, // tearDownList
                    mock(InterfaceDestroyedListener.class), // destroyedListener
                    TEST_WORKSOURCE_0 // requestorWs
            );
            collector.checkThat("AP created", apIface, IsNull.notNullValue());
        } else {
            List<Pair<Integer, WorkSource>> apDetails = mDut.reportImpactToCreateIface(
                    HDM_CREATE_IFACE_AP, true, requiredChipCapabilities, TEST_WORKSOURCE_0);
            assertNull("Should not create this AP", apDetails);
            apIface = mDut.createApIface(
                    requiredChipCapabilities, null, null, TEST_WORKSOURCE_0, false, mSoftApManager);
            collector.checkThat("AP should not be created", apIface, IsNull.nullValue());
        }
        if (SdkLevel.isAtLeastS()) {
            // Privileged app allowed to create P2P interface.
            when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(true);
            assertThat(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_P2P,
                    android.hardware.wifi.V1_5.IWifiChip.ChipCapabilityMask.WIGIG,
                    TEST_WORKSOURCE_1), is(isWigigSupported));
        }
    }

    /*
     * Verify that 60GHz iface creation request could be procceed by a chip supports
     * WIGIG.
     */
    @Test
    public void testIsItPossibleToCreate60GhzIfaceTestChipV5() throws Exception {
        TestChipV5 chipMock = new TestChipV5();
        setupWifiChipV15(chipMock);
        verify60GhzIfaceCreation(
                chipMock, TestChipV5.CHIP_MODE_ID, TestChipV5.CHIP_MODE_ID, true);
    }

    /*
     * Verify that 60GHz iface creation request could not be procceed by a chip does
     * not supports WIGIG on V1.5 HAL.
     */
    @Test
    public void testIsItPossibleToCreate60GhzIfaceTestChipV4() throws Exception {
        TestChipV4 chipMock = new TestChipV4();
        setupWifiChipV15(chipMock);
        verify60GhzIfaceCreation(
                chipMock, TestChipV4.CHIP_MODE_ID, TestChipV4.CHIP_MODE_ID, false);
    }

    /*
     * Verify that 60GHz iface creation request could be procceed by a chip does
     * not supports WIGIG on a HAL older than v1.5.
     */
    @Test
    public void testIsItPossibleToCreate60GhzIfaceTestChipV4WithHalOlderThan1_5() throws Exception {
        TestChipV4 chipMock = new TestChipV4();
        verify60GhzIfaceCreation(
                chipMock, TestChipV4.CHIP_MODE_ID, TestChipV4.CHIP_MODE_ID, true);
    }

    /**
     * Validate creation of AP interface from blank start-up in chip V1.5
     */
    @Test
    public void testCreateApInterfaceNoInitModeTestChipV15() throws Exception {
        mWifiChipV15 = mock(android.hardware.wifi.V1_5.IWifiChip.class);
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV5(), HDM_CREATE_IFACE_AP, "wlan0",
                TestChipV5.CHIP_MODE_ID);
    }
    /**
     * Validate creation of AP Bridge interface from blank start-up in chip V1.5
     */
    @Test
    public void testCreateApBridgeInterfaceNoInitModeTestChipV15() throws Exception {
        mWifiChipV15 = mock(android.hardware.wifi.V1_5.IWifiChip.class);
        mIsBridgedSoftApSupported = true;
        mIsStaWithBridgedSoftApConcurrencySupported = true;
        runCreateSingleXxxInterfaceNoInitMode(new TestChipV5(), HDM_CREATE_IFACE_AP_BRIDGE, "wlan0",
                TestChipV5.CHIP_MODE_ID);
    }

    /**
     * Validate creation of AP Bridge interface fails if there is a STA up and the device doesn't
     * support STA + Bridged AP.
     */
    @Test
    public void testCreateApBridgeInterfaceWithStaV15() throws Exception {
        mIsBridgedSoftApSupported = true;
        mIsStaWithBridgedSoftApConcurrencySupported = false;
        TestChipV5 chipMock = new TestChipV5();
        setupWifiChipV15(chipMock);
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mWifiChipV15, mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        IWifiIface iface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA,
                "wlan0",
                TestChipV5.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());

        List<Pair<Integer, WorkSource>> bridgedApDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP_BRIDGE, true, TEST_WORKSOURCE_1);
        // STA + AP_BRIDGED is not supported
        assertNull(bridgedApDetails);

        mIsStaWithBridgedSoftApConcurrencySupported = true;
        bridgedApDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_AP_BRIDGE, true, TEST_WORKSOURCE_1);
        // STA + AP_BRIDGED supported
        assertEquals(0, bridgedApDetails.size());
    }

    /**
     * Validate {@link HalDeviceManager#canDeviceSupportCreateTypeCombo(SparseArray)}
     */
    @Test
    public void testCanDeviceSupportIfaceComboTestChipV6() throws Exception {
        TestChipV6 testChip = new TestChipV6();
        testChip.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, testChip.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        // Try to query iface support before starting the HAL. Should return true with the stored
        // static chip info.
        when(mWifiMock.isStarted()).thenReturn(false);
        when(mWifiSettingsConfigStore.get(WifiSettingsConfigStore.WIFI_STATIC_CHIP_INFO))
                .thenReturn(TestChipV6.STATIC_CHIP_INFO_JSON_STRING);
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(IfaceConcurrencyType.STA, 1);
            }
        }));
        verify(mWifiMock, never()).getChipIds(any());
        when(mWifiMock.isStarted()).thenReturn(true);
        executeAndValidateStartupSequence();

        clearInvocations(mWifiMock);

        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(IfaceConcurrencyType.STA, 1);
            }
        }));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(IfaceConcurrencyType.AP, 1);
            }
        }));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.AP, 1);
            }
        }));
        assertTrue(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.AP_BRIDGED, 1);
            }
        }));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(IfaceConcurrencyType.AP, 1);
                put(IfaceConcurrencyType.AP_BRIDGED, 1);
            }
        }));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(IfaceConcurrencyType.STA, 1);
                put(IfaceConcurrencyType.AP, 1);
                put(IfaceConcurrencyType.AP_BRIDGED, 1);
            }
        }));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(IfaceConcurrencyType.NAN, 1);
            }
        }));
        assertFalse(mDut.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {
            {
                put(IfaceConcurrencyType.P2P, 2);
            }
        }));

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validate creation of AP Bridge interface from blank start-up in TestChipV6
     */
    @Test
    public void testCreateApBridgeInterfaceNoInitModeTestChipV6() throws Exception {
        TestChipV6 testChip = new TestChipV6();
        setupWifiChipV15(testChip);
        runCreateSingleXxxInterfaceNoInitMode(testChip, HDM_CREATE_IFACE_AP_BRIDGE, "wlan0",
                TestChipV6.CHIP_MODE_ID);
    }

    /**
     * Validate creation of STA will not downgrade an AP Bridge interface in TestChipV6, since it
     * can support STA and AP Bridge concurrently.
     */
    @Test
    public void testCreateStaDoesNotDowngradeApBridgeInterfaceTestChipV6() throws Exception {
        mIsBridgedSoftApSupported = true;
        mIsStaWithBridgedSoftApConcurrencySupported = false;
        TestChipV6 chipMock = new TestChipV6();
        setupWifiChipV15(chipMock);
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mWifiChipV15, mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        // Create the bridged AP
        ArrayList<String> bridgedApInstances = new ArrayList<>();
        bridgedApInstances.add("instance0");
        bridgedApInstances.add("instance1");
        chipMock.bridgedApInstancesByName.put("wlan0", bridgedApInstances);
        IWifiIface iface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP_BRIDGE,
                "wlan0",
                TestChipV6.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());

        when(mSoftApManager.getBridgedApDowngradeIfaceInstanceForRemoval()).thenReturn("instance1");
        // Should be able to create a STA without downgrading the bridged AP
        iface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV6.CHIP_MODE_ID,
                HDM_CREATE_IFACE_STA,
                "wlan3",
                TestChipV6.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());
        assertEquals(2, bridgedApInstances.size());
    }

    private IWifiIface setupDbsSupportTest(ChipMockBase testChip, int onlyChipMode,
            ImmutableList<ArrayList<Integer>> radioCombinationMatrix) throws Exception {
        WifiRadioCombinationMatrix matrix = new WifiRadioCombinationMatrix();
        for (ArrayList<Integer> comb: radioCombinationMatrix) {
            WifiRadioCombination combination = new WifiRadioCombination();
            for (Integer b: comb) {
                WifiRadioConfiguration config = new WifiRadioConfiguration();
                config.bandInfo = b;
                combination.radioConfigurations.add(config);
            }
            matrix.radioCombinations.add(combination);
        }

        testChip.chipSupportedRadioCombinationsMatrix = matrix;

        testChip.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, testChip.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // Request STA
        IWifiIface staIface = validateInterfaceSequence(testChip,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // createIfaceType
                "wlan0", // ifaceName
                onlyChipMode, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA can't be created", staIface, IsNull.notNullValue());

        // Request P2P
        IWifiIface p2pIface = validateInterfaceSequence(testChip,
                true, // chipModeValid
                onlyChipMode, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                onlyChipMode, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("P2P can't be created", p2pIface, IsNull.notNullValue());
        mTestLooper.dispatchAll();

        return staIface;
    }

    /**
     * Validate 24GHz/5GHz DBS support.
     */
    @Test
    public void test24g5gDbsSupport() throws Exception {
        TestChipV6 testChip = new TestChipV6();
        ImmutableList<ArrayList<Integer>> radioCombinationMatrix = ImmutableList.of(
                new ArrayList(Arrays.asList(WifiBand.BAND_24GHZ, WifiBand.BAND_5GHZ)));
        IWifiIface iface = setupDbsSupportTest(testChip, TestChipV6.CHIP_MODE_ID,
                radioCombinationMatrix);

        assertTrue(mDut.is24g5gDbsSupported(iface));
        assertFalse(mDut.is5g6gDbsSupported(iface));
    }

    /**
     * Validate 5GHz/6GHz DBS support.
     */
    @Test
    public void test5g6gDbsSupport() throws Exception {
        TestChipV6 testChip = new TestChipV6();
        ImmutableList<ArrayList<Integer>> radioCombinationMatrix = ImmutableList.of(
                new ArrayList(Arrays.asList(WifiBand.BAND_5GHZ, WifiBand.BAND_6GHZ)));
        IWifiIface iface = setupDbsSupportTest(testChip, TestChipV6.CHIP_MODE_ID,
                radioCombinationMatrix);

        assertFalse(mDut.is24g5gDbsSupported(iface));
        assertTrue(mDut.is5g6gDbsSupported(iface));
    }

    /**
     * Validate 2.4GHz/5GHz DBS and 5GHz/6GHz DBS support.
     */
    @Test
    public void test24g5gAnd5g6gDbsSupport() throws Exception {
        TestChipV6 testChip = new TestChipV6();
        ImmutableList<ArrayList<Integer>> radioCombinationMatrix = ImmutableList.of(
                new ArrayList(Arrays.asList(WifiBand.BAND_24GHZ, WifiBand.BAND_5GHZ)),
                new ArrayList(Arrays.asList(WifiBand.BAND_5GHZ, WifiBand.BAND_6GHZ)));
        IWifiIface iface = setupDbsSupportTest(testChip, TestChipV6.CHIP_MODE_ID,
                radioCombinationMatrix);

        assertTrue(mDut.is24g5gDbsSupported(iface));
        assertTrue(mDut.is5g6gDbsSupported(iface));
    }

    /**
     * Validate that a requested iface should have higher priority than ALL of the existing ifaces
     * for a mode change.
     */
    @Test
    public void testIsItPossibleToCreateIfaceBetweenChipModesTestChipV7() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());

        TestChipV7 chipMock = new TestChipV7();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // get STA interface from privileged app.
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV7.DUAL_STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // get STA interface from foreground app.
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnyForegroundAppRequest(true)).thenReturn(true);
        staIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV7.DUAL_STA_CHIP_MODE_ID, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV7.DUAL_STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // New system app not allowed to create AP interface since it would tear down the privileged
        // app STA during the chip mode change.
        when(mWorkSourceHelper2.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper2.hasAnySystemAppRequest()).thenReturn(true);
        assertFalse(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_2));

        // Privileged app allowed to create AP interface since it is able to tear down the
        // privileged app STA during the chip mode change.
        when(mWorkSourceHelper2.hasAnyPrivilegedAppRequest()).thenReturn(true);
        assertTrue(mDut.isItPossibleToCreateIface(HDM_CREATE_IFACE_AP, TEST_WORKSOURCE_2));
    }

    /**
     * Validate that a requested iface should delete the correct AP/AP_BRIDGED based on available
     * concurrency and not priority.
     */
    @Test
    public void testCreateInterfaceRemovesCorrectApIfaceTestChipV8() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());

        TestChipV8 chipMock = new TestChipV8();
        chipMock.initialize();
        setupWifiChipV15(chipMock);
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mWifiChipV15, mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // get AP_BRIDGED interface for a privileged app.
        ArrayList<String> bridgedApInstances = new ArrayList<>();
        bridgedApInstances.add("instance0");
        bridgedApInstances.add("instance1");
        chipMock.bridgedApInstancesByName.put("wlan0", bridgedApInstances);
        IWifiIface apBridgedIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP_BRIDGE, // ifaceTypeToCreate
                "wlan0", // ifaceName
                TestChipV8.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("Bridged AP created", apBridgedIface, IsNull.notNullValue());

        // get AP interface for a system app.
        when(mWorkSourceHelper1.hasAnyPrivilegedAppRequest()).thenReturn(false);
        when(mWorkSourceHelper1.hasAnySystemAppRequest()).thenReturn(true);
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV8.CHIP_MODE_ID, // chipModeId
                HDM_CREATE_IFACE_AP, // ifaceTypeToCreate
                "wlan1", // ifaceName
                TestChipV8.CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                mock(InterfaceDestroyedListener.class), // destroyedListener
                TEST_WORKSOURCE_1 // requestorWs
        );
        collector.checkThat("AP created", apIface, IsNull.notNullValue());

        // Check that the impact to add a STA will remove the AP_BRIDGED (TEST_WORKSOURCE_0) and not
        // the AP (TEST_WORKSOURCE_1), even though the AP has lower priority.
        List<Pair<Integer, WorkSource>> impactToCreateSta =
                mDut.reportImpactToCreateIface(HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_0);
        assertFalse(impactToCreateSta.isEmpty());
        assertEquals(TEST_WORKSOURCE_0, impactToCreateSta.get(0).second);
    }

    /**
     * Validate creation of STA with a downgraded bridged AP in chip V9 with no STA + Bridged AP
     * concurrency.
     */
    @Test
    public void testCreateStaInterfaceWithDowngradedBridgedApTestChipV9()
            throws Exception {
        mIsBridgedSoftApSupported = true;
        mIsStaWithBridgedSoftApConcurrencySupported = false;
        TestChipV9 chipMock = new TestChipV9();
        setupWifiChipV15(chipMock);
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mWifiChipV15, mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        // Create the bridged AP
        ArrayList<String> bridgedApInstances = new ArrayList<>();
        bridgedApInstances.add("instance0");
        bridgedApInstances.add("instance1");
        chipMock.bridgedApInstancesByName.put("wlan0", bridgedApInstances);
        IWifiIface iface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP_BRIDGE,
                "wlan0",
                TestChipV9.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());

        // Downgrade the bridged AP.
        chipMock.bridgedApInstancesByName.get("wlan0").remove(0);

        // Should be able to create a STA now since STA + AP is supported
        List<Pair<Integer, WorkSource>> staDetails = mDut.reportImpactToCreateIface(
                HDM_CREATE_IFACE_STA, true, TEST_WORKSOURCE_1);
        assertNotNull(staDetails);
        assertEquals(0, staDetails.size());
    }

    /**
     * Validate a bridged AP will be downgraded to make room for a STA in chip V9 with no STA +
     * Bridged AP concurrency
     */
    @Test
    public void testCreateStaInterfaceWillDowngradeBridgedApTestChipV9()
            throws Exception {
        mIsBridgedSoftApSupported = true;
        mIsStaWithBridgedSoftApConcurrencySupported = false;
        TestChipV9 chipMock = new TestChipV9();
        setupWifiChipV15(chipMock);
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mWifiChipV15, mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        // Create the bridged AP
        ArrayList<String> bridgedApInstances = new ArrayList<>();
        bridgedApInstances.add("instance0");
        bridgedApInstances.add("instance1");
        chipMock.bridgedApInstancesByName.put("wlan0", bridgedApInstances);
        IWifiIface iface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_AP_BRIDGE,
                "wlan0",
                TestChipV9.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());

        when(mSoftApManager.getBridgedApDowngradeIfaceInstanceForRemoval()).thenReturn("instance1");
        // Should be able to create a STA by downgrading the bridged AP
        iface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                TestChipV9.CHIP_MODE_ID,
                HDM_CREATE_IFACE_STA,
                "wlan3",
                TestChipV9.CHIP_MODE_ID,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("interface was null", iface, IsNull.notNullValue());
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    // utilities
    ///////////////////////////////////////////////////////////////////////////////////////
    private void setupWifiChipV15(ChipMockBase chipMock) throws RemoteException {
        mWifiChipV15 = mock(android.hardware.wifi.V1_5.IWifiChip.class);
        doAnswer(new GetCapabilities_1_5Answer(chipMock))
                .when(mWifiChipV15).getCapabilities_1_5(any(
                        android.hardware.wifi.V1_5.IWifiChip.getCapabilities_1_5Callback.class));
        when(mWifiChipV15.removeIfaceInstanceFromBridgedApIface(any(), any()))
                .thenAnswer((invocation) -> {
                    chipMock.bridgedApInstancesByName.get(invocation.getArgument(0))
                            .remove(invocation.getArgument(1));
                    return getStatus(WifiStatusCode.SUCCESS);
                });
    }

    private void setupWifiV15(IWifi iWifiMock) throws RemoteException {
        mWifiMockV15 = mock(android.hardware.wifi.V1_5.IWifi.class);
        when(mWifiMockV15.registerEventCallback_1_5(
                any(android.hardware.wifi.V1_5.IWifiEventCallback.class))).thenReturn(mStatusOk);
    }

    private void dumpDut(String prefix) {
        StringWriter sw = new StringWriter();
        mDut.dump(null, new PrintWriter(sw), null);
        Log.e("HalDeviceManager", prefix + sw.toString());
    }

    private void executeAndValidateInitializationSequence() throws Exception {
        executeAndValidateInitializationSequence(true);
    }

    private void executeAndValidateInitializationSequence(boolean isSupported) throws Exception {
        // act:
        mDut.initialize();

        // verify: service manager initialization sequence
        mInOrder.verify(mServiceManagerMock).linkToDeath(any(IHwBinder.DeathRecipient.class),
                anyLong());
        mInOrder.verify(mServiceManagerMock).registerForNotifications(eq(IWifi.kInterfaceName),
                eq(""), mServiceNotificationCaptor.capture());

        // If not using the lazy version of the IWifi service, the process should already be up at
        // this point.
        mInOrder.verify(mServiceManagerMock).listManifestByInterface(eq(IWifi.kInterfaceName));

        // verify: wifi initialization sequence if vendor HAL is supported.
        if (isSupported) {
            mInOrder.verify(mWifiMock).linkToDeath(mDeathRecipientCaptor.capture(), anyLong());
            // verify: onStop called as a part of initialize.
            mInOrder.verify(mWifiMock).stop();
            if (null != mWifiMockV15) {
                mInOrder.verify(mWifiMockV15).registerEventCallback_1_5(
                        mWifiEventCallbackCaptorV15.capture());
            } else {
                mInOrder.verify(mWifiMock).registerEventCallback(
                        mWifiEventCallbackCaptor.capture());
            }
            collector.checkThat("isReady is true", mDut.isReady(), equalTo(true));
        } else {
            collector.checkThat("isReady is false", mDut.isReady(), equalTo(false));
        }
    }

    private void executeAndValidateStartupSequence() throws Exception {
        executeAndValidateStartupSequence(1, true);
    }

    private void executeAndValidateStartupSequence(int numAttempts, boolean success)
            throws Exception {
        // act: register listener & start Wi-Fi
        mDut.registerStatusListener(mManagerStatusListenerMock, mHandler);
        collector.checkThat(mDut.start(), equalTo(success));

        // verify
        mInOrder.verify(mWifiMock, times(numAttempts)).start();

        if (success) {
            // act: trigger onStart callback of IWifiEventCallback
            if (mWifiMockV15 != null) {
                mWifiEventCallbackCaptorV15.getValue().onStart();
            } else {
                mWifiEventCallbackCaptor.getValue().onStart();
            }
            mTestLooper.dispatchAll();

            // verify: onStart called on registered listener
            mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();
        }
    }

    private void runCreateSingleXxxInterfaceNoInitMode(ChipMockBase chipMock, int ifaceTypeToCreate,
            String ifaceName, int finalChipMode) throws Exception {
        chipMock.initialize();
        if (mWifiChipV15 != null) {
            mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                    mWifiChipV15, mManagerStatusListenerMock);
        } else {
            mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                    mManagerStatusListenerMock);
        }
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener idl = mock(
                InterfaceDestroyedListener.class);

        IWifiIface iface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                ifaceTypeToCreate,
                ifaceName,
                finalChipMode,
                null, // tearDownList
                idl, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: remove interface
        mDut.removeIface(iface);
        mTestLooper.dispatchAll();

        // verify: callback triggered
        switch (ifaceTypeToCreate) {
            case HDM_CREATE_IFACE_STA:
                mInOrder.verify(chipMock.chip).removeStaIface(ifaceName);
                break;
            case HDM_CREATE_IFACE_AP_BRIDGE:
            case HDM_CREATE_IFACE_AP:
                mInOrder.verify(chipMock.chip).removeApIface(ifaceName);
                break;
            case HDM_CREATE_IFACE_P2P:
                mInOrder.verify(chipMock.chip).removeP2pIface(ifaceName);
                break;
            case HDM_CREATE_IFACE_NAN:
                mInOrder.verify(chipMock.chip).removeNanIface(ifaceName);
                break;
        }

        verify(idl).onDestroyed(ifaceName);

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl);
    }

    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - get nan available listener
     *   - Can create NAN when requested
     *
     * Relevant for any chip which supports STA + NAN || P2P (or a richer combination - but bottom
     * line of NAN and P2P being exclusive).
     */
    public void runP2pAndNanExclusiveInteractionsTestChip(ChipMockBase chipMock,
            int onlyChipMode) throws Exception {
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiMockV15, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        InterfaceDestroyedListener staDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener nanDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        InterfaceDestroyedListener p2pDestroyedListener = mock(
                InterfaceDestroyedListener.class);

        // Request STA
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                HDM_CREATE_IFACE_STA, // createIfaceType
                "wlan0", // ifaceName
                onlyChipMode, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("STA can't be created", staIface, IsNull.notNullValue());

        // Request NAN
        IWifiIface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                onlyChipMode, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                onlyChipMode, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );

        // Request P2P
        IWifiIface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                onlyChipMode, // chipModeId
                HDM_CREATE_IFACE_P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                onlyChipMode, // finalChipMode
                new IWifiIface[]{nanIface}, // tearDownList
                p2pDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0, // requestorWs
                // destroyedInterfacesDestroyedListeners...
                new InterfaceDestroyedListenerWithIfaceName(
                        getName(nanIface), nanDestroyedListener)
        );
        collector.checkThat("P2P can't be created", p2pIface, IsNull.notNullValue());
        mTestLooper.dispatchAll();
        verify(nanDestroyedListener).onDestroyed(getName(nanIface));

        // Request NAN
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                onlyChipMode, // chipModeId
                HDM_CREATE_IFACE_NAN, // ifaceTypeToCreate
                "wlan0", // ifaceName
                onlyChipMode, // finalChipMode
                new IWifiIface[]{p2pIface}, // tearDownList
                nanDestroyedListener, // destroyedListener
                TEST_WORKSOURCE_0 // requestorWs
        );
        collector.checkThat("NAN can't be created", nanIface, IsNull.notNullValue());

        mTestLooper.dispatchAll();
        verify(p2pDestroyedListener).onDestroyed(getName(p2pIface));

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener,
                nanDestroyedListener, p2pDestroyedListener);
    }

    private IWifiIface validateInterfaceSequence(ChipMockBase chipMock,
            boolean chipModeValid, int chipModeId,
            int createIfaceType, String ifaceName, int finalChipMode,
            long requiredChipCapabilities,
            IWifiIface[] tearDownList,
            InterfaceDestroyedListener destroyedListener,
            WorkSource requestorWs,
            InterfaceDestroyedListenerWithIfaceName...destroyedInterfacesDestroyedListeners)
            throws Exception {
        // configure chip mode response
        chipMock.chipModeValid = chipModeValid;
        chipMock.chipModeId = chipModeId;

        // check if can create interface
        List<Pair<Integer, WorkSource>> details = mDut.reportImpactToCreateIface(
                createIfaceType, true, requiredChipCapabilities, requestorWs);
        if (tearDownList == null || tearDownList.length == 0) {
            assertTrue("Details list must be empty - can create" + details, details.isEmpty());
        } else { // TODO: assumes that at most a single entry - which is the current usage
            assertEquals("Details don't match " + details, tearDownList.length, details.size());
            assertEquals("Details don't match " + details, getType(tearDownList[0]),
                    HAL_IFACE_MAP.get(details.get(0).first.intValue()));
        }

        IWifiIface iface = null;

        // configure: interface to be created
        // act: request the interface
        switch (createIfaceType) {
            case HDM_CREATE_IFACE_STA:
                iface = mock(IWifiStaIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName(
                        any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.STA)).when(iface).getType(
                        any(IWifiIface.getTypeCallback.class));
                doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, iface)).when(
                        chipMock.chip).createStaIface(any(IWifiChip.createStaIfaceCallback.class));

                mDut.createStaIface(requiredChipCapabilities,
                        destroyedListener, mHandler, requestorWs);
                break;
            case HDM_CREATE_IFACE_AP_BRIDGE:
            case HDM_CREATE_IFACE_AP:
                iface = mock(IWifiApIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName(
                        any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.AP)).when(iface).getType(
                        any(IWifiIface.getTypeCallback.class));
                if (mWifiChipV15 != null && createIfaceType == HDM_CREATE_IFACE_AP_BRIDGE) {
                    android.hardware.wifi.V1_5.IWifiApIface ifaceApV15 =
                            mock(android.hardware.wifi.V1_5.IWifiApIface.class);
                    doAnswer(new GetNameAnswer(ifaceName)).when(ifaceApV15).getName(
                            any(IWifiIface.getNameCallback.class));
                    doAnswer(new GetTypeAnswer(IfaceType.AP)).when(ifaceApV15).getType(
                            any(IWifiIface.getTypeCallback.class));
                    doAnswer(new GetBridgedInstancesAnswer(chipMock, ifaceName))
                            .when(ifaceApV15).getBridgedInstances(
                                    any(android.hardware.wifi.V1_5.IWifiApIface
                                            .getBridgedInstancesCallback.class));
                    doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, ifaceApV15)).when(
                            mWifiChipV15).createBridgedApIface(
                            any(android.hardware.wifi.V1_5.IWifiChip
                            .createBridgedApIfaceCallback.class));
                } else {
                    doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, iface)).when(
                            chipMock.chip).createApIface(
                            any(IWifiChip.createApIfaceCallback.class));
                }
                mDut.createApIface(requiredChipCapabilities,
                        destroyedListener, mHandler, requestorWs,
                        createIfaceType == HDM_CREATE_IFACE_AP_BRIDGE, mSoftApManager);
                break;
            case HDM_CREATE_IFACE_P2P:
                iface = mock(IWifiP2pIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName(
                        any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.P2P)).when(iface).getType(
                        any(IWifiIface.getTypeCallback.class));
                doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, iface)).when(
                        chipMock.chip).createP2pIface(any(IWifiChip.createP2pIfaceCallback.class));

                mDut.createP2pIface(requiredChipCapabilities,
                        destroyedListener, mHandler, requestorWs);
                break;
            case HDM_CREATE_IFACE_NAN:
                iface = mock(IWifiNanIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName(
                        any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.NAN)).when(iface).getType(
                        any(IWifiIface.getTypeCallback.class));
                doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, iface)).when(
                        chipMock.chip).createNanIface(any(IWifiChip.createNanIfaceCallback.class));

                mDut.createNanIface(destroyedListener, mHandler, requestorWs);
                break;
        }

        // validate: optional tear down of interfaces
        if (tearDownList != null) {
            for (IWifiIface tearDownIface: tearDownList) {
                switch (getType(tearDownIface)) {
                    case IfaceType.STA:
                        mInOrder.verify(chipMock.chip).removeStaIface(getName(tearDownIface));
                        break;
                    case IfaceType.AP:
                        mInOrder.verify(chipMock.chip).removeApIface(getName(tearDownIface));
                        break;
                    case IfaceType.P2P:
                        mInOrder.verify(chipMock.chip).removeP2pIface(getName(tearDownIface));
                        break;
                    case IfaceType.NAN:
                        mInOrder.verify(chipMock.chip).removeNanIface(getName(tearDownIface));
                        break;
                }
            }
        }

        // validate: optional switch to the requested mode
        if (!chipModeValid || chipModeId != finalChipMode) {
            mInOrder.verify(chipMock.chip).configureChip(finalChipMode);
        } else {
            mInOrder.verify(chipMock.chip, times(0)).configureChip(anyInt());
        }

        // validate: create interface
        switch (createIfaceType) {
            case HDM_CREATE_IFACE_STA:
                mInOrder.verify(chipMock.chip).createStaIface(
                        any(IWifiChip.createStaIfaceCallback.class));
                break;
            case HDM_CREATE_IFACE_AP_BRIDGE:
            case HDM_CREATE_IFACE_AP:
                if (mWifiChipV15 != null && createIfaceType == HDM_CREATE_IFACE_AP_BRIDGE) {
                    mInOrder.verify(mWifiChipV15)
                            .createBridgedApIface(any(android.hardware.wifi.V1_5.IWifiChip
                            .createBridgedApIfaceCallback.class));
                } else {
                    mInOrder.verify(chipMock.chip).createApIface(
                            any(IWifiChip.createApIfaceCallback.class));
                }
                break;
            case HDM_CREATE_IFACE_P2P:
                mInOrder.verify(chipMock.chip).createP2pIface(
                        any(IWifiChip.createP2pIfaceCallback.class));
                break;
            case HDM_CREATE_IFACE_NAN:
                mInOrder.verify(chipMock.chip).createNanIface(
                        any(IWifiChip.createNanIfaceCallback.class));
                break;
        }

        // verify: callbacks on deleted interfaces
        mTestLooper.dispatchAll();
        for (int i = 0; i < destroyedInterfacesDestroyedListeners.length; ++i) {
            destroyedInterfacesDestroyedListeners[i].validate();
        }
        return iface;
    }

    private IWifiIface validateInterfaceSequence(ChipMockBase chipMock,
            boolean chipModeValid, int chipModeId,
            int createIfaceType, String ifaceName, int finalChipMode,
            IWifiIface[] tearDownList,
            InterfaceDestroyedListener destroyedListener,
            WorkSource requestorWs,
            InterfaceDestroyedListenerWithIfaceName...destroyedInterfacesDestroyedListeners)
            throws Exception {
        return validateInterfaceSequence(chipMock, chipModeValid, chipModeId,
                createIfaceType, ifaceName,
                finalChipMode, CHIP_CAPABILITY_ANY,
                tearDownList, destroyedListener, requestorWs,
                destroyedInterfacesDestroyedListeners);
    }

    private int getType(IWifiIface iface) throws Exception {
        Mutable<Integer> typeResp = new Mutable<>();
        iface.getType((WifiStatus status, int type) -> {
            typeResp.value = type;
        });
        return typeResp.value;
    }

    private String getName(IWifiIface iface) throws Exception {
        Mutable<String> nameResp = new Mutable<>();
        iface.getName((WifiStatus status, String name) -> {
            nameResp.value = name;
        });
        return nameResp.value;
    }

    private WifiStatus getStatus(int code) {
        WifiStatus status = new WifiStatus();
        status.code = code;
        return status;
    }

    private static class InterfaceDestroyedListenerWithIfaceName {
        private final String mIfaceName;
        @Mock private final InterfaceDestroyedListener mListener;

        InterfaceDestroyedListenerWithIfaceName(
                String ifaceName, InterfaceDestroyedListener listener) {
            mIfaceName = ifaceName;
            mListener = listener;
        }

        public void validate() {
            verify(mListener).onDestroyed(mIfaceName);
        }
    }

    private static class Mutable<E> {
        public E value;

        Mutable() {
            value = null;
        }

        Mutable(E value) {
            this.value = value;
        }
    }

    // Answer objects
    private class GetChipIdsAnswer extends MockAnswerUtil.AnswerWithArguments {
        private WifiStatus mStatus;
        private ArrayList<Integer> mChipIds;

        GetChipIdsAnswer(WifiStatus status, ArrayList<Integer> chipIds) {
            mStatus = status;
            mChipIds = chipIds;
        }

        public void answer(IWifi.getChipIdsCallback cb) {
            cb.onValues(mStatus, mChipIds);
        }
    }

    private class GetChipAnswer extends MockAnswerUtil.AnswerWithArguments {
        private WifiStatus mStatus;
        private IWifiChip mChip;

        GetChipAnswer(WifiStatus status, IWifiChip chip) {
            mStatus = status;
            mChip = chip;
        }

        public void answer(int chipId, IWifi.getChipCallback cb) {
            cb.onValues(mStatus, mChip);
        }
    }

    private class GetCapabilitiesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetCapabilitiesAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(IWifiChip.getCapabilitiesCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.chipCapabilities);
        }
    }

    private class GetCapabilities_1_5Answer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetCapabilities_1_5Answer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(
                android.hardware.wifi.V1_5.IWifiChip.getCapabilities_1_5Callback cb) {
            cb.onValues(mStatusOk, mChipMockBase.chipCapabilities);
        }
    }

    private class GetIdAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetIdAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(IWifiChip.getIdCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.chipId);
        }
    }

    private class GetAvailableModesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetAvailableModesAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(IWifiChip.getAvailableModesCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.availableModes);
        }
    }

    private class GetAvailableModesAnswer_1_6 extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetAvailableModesAnswer_1_6(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(android.hardware.wifi.V1_6.IWifiChip.getAvailableModes_1_6Callback cb) {
            cb.onValues(mStatusOk, mChipMockBase.availableModes_1_6);
        }
    }

    private class GetModeAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetModeAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(IWifiChip.getModeCallback cb) {
            cb.onValues(mChipMockBase.chipModeValid ? mStatusOk
                    : getStatus(WifiStatusCode.ERROR_NOT_AVAILABLE), mChipMockBase.chipModeId);
        }
    }

    private class ConfigureChipAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        ConfigureChipAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public WifiStatus answer(int chipMode) {
            mChipMockBase.chipModeValid = true;
            mChipMockBase.chipModeId = chipMode;
            return mStatusOk;
        }
    }

    private class GetXxxIfaceNamesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetXxxIfaceNamesAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(IWifiChip.getStaIfaceNamesCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.interfaceNames.get(IfaceType.STA));
        }

        public void answer(IWifiChip.getApIfaceNamesCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.interfaceNames.get(IfaceType.AP));
        }

        public void answer(IWifiChip.getP2pIfaceNamesCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.interfaceNames.get(IfaceType.P2P));
        }

        public void answer(IWifiChip.getNanIfaceNamesCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.interfaceNames.get(IfaceType.NAN));
        }
    }

    private class GetXxxIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetXxxIfaceAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(String name, IWifiChip.getStaIfaceCallback cb) {
            IWifiIface iface = mChipMockBase.interfacesByName.get(IfaceType.STA).get(name);
            cb.onValues(iface != null ? mStatusOk : mStatusFail, (IWifiStaIface) iface);
        }

        public void answer(String name, IWifiChip.getApIfaceCallback cb) {
            IWifiIface iface = mChipMockBase.interfacesByName.get(IfaceType.AP).get(name);
            cb.onValues(iface != null ? mStatusOk : mStatusFail, (IWifiApIface) iface);
        }

        public void answer(String name, IWifiChip.getP2pIfaceCallback cb) {
            IWifiIface iface = mChipMockBase.interfacesByName.get(IfaceType.P2P).get(name);
            cb.onValues(iface != null ? mStatusOk : mStatusFail, (IWifiP2pIface) iface);
        }

        public void answer(String name, IWifiChip.getNanIfaceCallback cb) {
            IWifiIface iface = mChipMockBase.interfacesByName.get(IfaceType.NAN).get(name);
            cb.onValues(iface != null ? mStatusOk : mStatusFail, (IWifiNanIface) iface);
        }
    }

    private class CreateXxxIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;
        private WifiStatus mStatus;
        private IWifiIface mWifiIface;

        CreateXxxIfaceAnswer(ChipMockBase chipMockBase, WifiStatus status, IWifiIface wifiIface) {
            mChipMockBase = chipMockBase;
            mStatus = status;
            mWifiIface = wifiIface;
        }

        private void addInterfaceInfo(int type) {
            if (mStatus.code == WifiStatusCode.SUCCESS) {
                try {
                    mChipMockBase.interfaceNames.get(type).add(getName(mWifiIface));
                    mChipMockBase.interfacesByName.get(type).put(getName(mWifiIface), mWifiIface);
                } catch (Exception e) {
                    // do nothing
                }
            }
        }

        public void answer(IWifiChip.createStaIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiStaIface) mWifiIface);
            addInterfaceInfo(IfaceType.STA);
        }

        public void answer(IWifiChip.createApIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiApIface) mWifiIface);
            addInterfaceInfo(IfaceType.AP);
        }

        public void answer(android.hardware.wifi.V1_5.IWifiChip.createBridgedApIfaceCallback cb) {
            cb.onValues(mStatus, (android.hardware.wifi.V1_5.IWifiApIface) mWifiIface);
            addInterfaceInfo(IfaceType.AP);
        }

        public void answer(IWifiChip.createP2pIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiP2pIface) mWifiIface);
            addInterfaceInfo(IfaceType.P2P);
        }

        public void answer(IWifiChip.createNanIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiNanIface) mWifiIface);
            addInterfaceInfo(IfaceType.NAN);
        }
    }

    private class CreateRttControllerAnswer extends MockAnswerUtil.AnswerWithArguments {
        private final ChipMockBase mChipMockBase;
        private final IWifiRttController mRttController;

        CreateRttControllerAnswer(ChipMockBase chipMockBase, IWifiRttController rttController) {
            mChipMockBase = chipMockBase;
            mRttController = rttController;
        }

        public void answer(IWifiIface boundIface, IWifiChip.createRttControllerCallback cb) {
            if (mChipMockBase.chipModeIdValidForRtt == mChipMockBase.chipModeId) {
                cb.onValues(mStatusOk, mRttController);
            } else {
                cb.onValues(mStatusFail, null);
            }
        }
    }
    private class GetBoundIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private final boolean mIsValid;

        GetBoundIfaceAnswer(boolean isValid) {
            mIsValid = isValid;
        }

        public void answer(IWifiRttController.getBoundIfaceCallback cb) {
            if (mIsValid) {
                cb.onValues(mStatusOk, null);
            } else {
                cb.onValues(mStatusFail, null);
            }
        }
    }

    private class RemoveXxxIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;
        private int mType;

        RemoveXxxIfaceAnswer(ChipMockBase chipMockBase, int type) {
            mChipMockBase = chipMockBase;
            mType = type;
        }

        private WifiStatus removeIface(int type, String ifname) {
            try {
                if (!mChipMockBase.interfaceNames.get(type).remove(ifname)) {
                    return mStatusFail;
                }
                if (mChipMockBase.interfacesByName.get(type).remove(ifname) == null) {
                    return mStatusFail;
                }
            } catch (Exception e) {
                return mStatusFail;
            }
            return mStatusOk;
        }

        public WifiStatus answer(String ifname) {
            return removeIface(mType, ifname);
        }
    }

    private class GetNameAnswer extends MockAnswerUtil.AnswerWithArguments {
        private String mName;

        GetNameAnswer(String name) {
            mName = name;
        }

        public void answer(IWifiIface.getNameCallback cb) {
            cb.onValues(mStatusOk, mName);
        }
    }

    private class GetTypeAnswer extends MockAnswerUtil.AnswerWithArguments {
        private int mType;

        GetTypeAnswer(int type) {
            mType = type;
        }

        public void answer(IWifiIface.getTypeCallback cb) {
            cb.onValues(mStatusOk, mType);
        }
    }

    private class GetSupportedRadioCombinationsMatrixAnswer
            extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetSupportedRadioCombinationsMatrixAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(
                android.hardware.wifi.V1_6.IWifiChip.getSupportedRadioCombinationsMatrixCallback
                cb) {
            cb.onValues(mStatusOk, mChipMockBase.chipSupportedRadioCombinationsMatrix);
        }
    }

    private class GetBridgedInstancesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;
        private String mName;

        GetBridgedInstancesAnswer(ChipMockBase chipMockBase, String name) {
            mChipMockBase = chipMockBase;
            mName = name;
        }

        public void answer(android.hardware.wifi.V1_5.IWifiApIface.getBridgedInstancesCallback cb) {
            ArrayList<String> bridgedApInstances =
                    mChipMockBase.bridgedApInstancesByName.get(mName);
            if (bridgedApInstances == null) {
                bridgedApInstances = new ArrayList<>();
            }
            cb.onValues(mStatusOk, bridgedApInstances);
        }
    }

    // chip configuration

    private static final int CHIP_MOCK_V1 = 0;
    private static final int CHIP_MOCK_V2 = 1;
    private static final int CHIP_MOCK_V3 = 2;
    private static final int CHIP_MOCK_V4 = 3;
    private static final int CHIP_MOCK_V5 = 4;
    private static final int CHIP_MOCK_V6 = 6;

    private class ChipMockBase {
        public int chipMockId;

        public android.hardware.wifi.V1_6.IWifiChip chip;
        public int chipId;
        public boolean chipModeValid = false;
        public int chipModeId = -1000;
        public int chipModeIdValidForRtt = -1; // single chip mode ID where RTT can be created
        public int chipCapabilities = 0;
        public WifiRadioCombinationMatrix chipSupportedRadioCombinationsMatrix = null;
        public Map<Integer, ArrayList<String>> interfaceNames = new HashMap<>();
        public Map<Integer, Map<String, IWifiIface>> interfacesByName = new HashMap<>();
        public Map<String, ArrayList<String>> bridgedApInstancesByName = new HashMap<>();

        public ArrayList<IWifiChip.ChipMode> availableModes;
        public ArrayList<android.hardware.wifi.V1_6.IWifiChip.ChipMode> availableModes_1_6;

        void initialize() throws Exception {
            chip = mock(android.hardware.wifi.V1_6.IWifiChip.class);

            interfaceNames.put(IfaceType.STA, new ArrayList<>());
            interfaceNames.put(IfaceType.AP, new ArrayList<>());
            interfaceNames.put(IfaceType.P2P, new ArrayList<>());
            interfaceNames.put(IfaceType.NAN, new ArrayList<>());

            interfacesByName.put(IfaceType.STA, new HashMap<>());
            interfacesByName.put(IfaceType.AP, new HashMap<>());
            interfacesByName.put(IfaceType.P2P, new HashMap<>());
            interfacesByName.put(IfaceType.NAN, new HashMap<>());

            when(chip.registerEventCallback(any(IWifiChipEventCallback.class))).thenReturn(
                    mStatusOk);
            when(chip.configureChip(anyInt())).thenAnswer(new ConfigureChipAnswer(this));
            doAnswer(new GetCapabilitiesAnswer(this))
                    .when(chip).getCapabilities(any(IWifiChip.getCapabilitiesCallback.class));
            doAnswer(new GetIdAnswer(this)).when(chip).getId(any(IWifiChip.getIdCallback.class));
            doAnswer(new GetModeAnswer(this)).when(chip).getMode(
                    any(IWifiChip.getModeCallback.class));
            GetXxxIfaceNamesAnswer getXxxIfaceNamesAnswer = new GetXxxIfaceNamesAnswer(this);
            doAnswer(getXxxIfaceNamesAnswer).when(chip).getStaIfaceNames(
                    any(IWifiChip.getStaIfaceNamesCallback.class));
            doAnswer(getXxxIfaceNamesAnswer).when(chip).getApIfaceNames(
                    any(IWifiChip.getApIfaceNamesCallback.class));
            doAnswer(getXxxIfaceNamesAnswer).when(chip).getP2pIfaceNames(
                    any(IWifiChip.getP2pIfaceNamesCallback.class));
            doAnswer(getXxxIfaceNamesAnswer).when(chip).getNanIfaceNames(
                    any(IWifiChip.getNanIfaceNamesCallback.class));
            GetXxxIfaceAnswer getXxxIfaceAnswer = new GetXxxIfaceAnswer(this);
            doAnswer(getXxxIfaceAnswer).when(chip).getStaIface(anyString(),
                    any(IWifiChip.getStaIfaceCallback.class));
            doAnswer(getXxxIfaceAnswer).when(chip).getApIface(anyString(),
                    any(IWifiChip.getApIfaceCallback.class));
            doAnswer(getXxxIfaceAnswer).when(chip).getP2pIface(anyString(),
                    any(IWifiChip.getP2pIfaceCallback.class));
            doAnswer(getXxxIfaceAnswer).when(chip).getNanIface(anyString(),
                    any(IWifiChip.getNanIfaceCallback.class));
            doAnswer(new RemoveXxxIfaceAnswer(this, IfaceType.STA)).when(chip).removeStaIface(
                    anyString());
            doAnswer(new RemoveXxxIfaceAnswer(this, IfaceType.AP)).when(chip).removeApIface(
                    anyString());
            doAnswer(new RemoveXxxIfaceAnswer(this, IfaceType.P2P)).when(chip).removeP2pIface(
                    anyString());
            doAnswer(new RemoveXxxIfaceAnswer(this, IfaceType.NAN)).when(chip).removeNanIface(
                    anyString());

            doAnswer(new CreateRttControllerAnswer(this, mRttControllerMock)).when(
                    chip).createRttController(any(), any());

            doAnswer(new GetBoundIfaceAnswer(true)).when(mRttControllerMock).getBoundIface(any());
            doAnswer(new GetSupportedRadioCombinationsMatrixAnswer(this))
                    .when(chip).getSupportedRadioCombinationsMatrix(
                            any(android.hardware.wifi.V1_6.IWifiChip
                                    .getSupportedRadioCombinationsMatrixCallback.class));
        }
    }

    // test chip configuration V1:
    // mode: STA + (NAN || P2P)
    // mode: AP
    private class TestChipV1 extends ChipMockBase {
        static final int STA_CHIP_MODE_ID = 0;
        static final int AP_CHIP_MODE_ID = 1;
        static final String STATIC_CHIP_INFO_JSON_STRING = "["
                + "    {"
                + "        \"chipId\": 10,"
                + "        \"chipCapabilities\": -1,"
                + "        \"availableModes\": ["
                + "            {"
                + "                \"id\": 0,"
                + "                \"availableCombinations\": ["
                + "                    {"
                + "                        \"limits\": ["
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [0]"
                + "                            },"
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [3, 4]"
                + "                            }"
                + "                        ]"
                + "                    }"
                + "                ]"
                + "            },"
                + "            {"
                + "                \"id\": 1,"
                + "                \"availableCombinations\": ["
                + "                    {"
                + "                        \"limits\": ["
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [1]"
                + "                            }"
                + "                        ]"
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "]";

        void initialize() throws Exception {
            super.initialize();

            chipMockId = CHIP_MOCK_V1;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 10;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(mStatusOk, chipIds)).when(mWifiMock).getChipIds(
                    any(IWifi.getChipIdsCallback.class));

            doAnswer(new GetChipAnswer(mStatusOk, chip)).when(mWifiMock).getChip(eq(10),
                    any(IWifi.getChipCallback.class));

            // initialize placeholder chip modes
            IWifiChip.ChipMode cm;
            IWifiChip.ChipIfaceCombination cic;
            IWifiChip.ChipIfaceCombinationLimit cicl;

            //   Mode 0: 1xSTA + 1x{P2P,NAN}
            //   Mode 1: 1xAP
            availableModes = new ArrayList<>();
            cm = new IWifiChip.ChipMode();
            cm.id = STA_CHIP_MODE_ID;

            cic = new IWifiChip.ChipIfaceCombination();

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.STA);
            cic.limits.add(cicl);

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.P2P);
            cicl.types.add(IfaceType.NAN);
            cic.limits.add(cicl);
            cm.availableCombinations.add(cic);
            availableModes.add(cm);

            cm = new IWifiChip.ChipMode();
            cm.id = AP_CHIP_MODE_ID;
            cic = new IWifiChip.ChipIfaceCombination();
            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.AP);
            cic.limits.add(cicl);
            cm.availableCombinations.add(cic);
            availableModes.add(cm);

            chipModeIdValidForRtt = STA_CHIP_MODE_ID;

            doAnswer(new GetAvailableModesAnswer(this)).when(chip)
                    .getAvailableModes(any(IWifiChip.getAvailableModesCallback.class));
        }
    }

    // test chip configuration V2:
    // mode: STA + (STA || AP) + (NAN || P2P)
    private class TestChipV2 extends ChipMockBase {
        // only mode (different number from any in TestChipV1 so can catch test errors)
        static final int CHIP_MODE_ID = 5;
        static final String STATIC_CHIP_INFO_JSON_STRING = "["
                + "    {"
                + "        \"chipId\": 12,"
                + "        \"chipCapabilities\": 0,"
                + "        \"availableModes\": ["
                + "            {"
                + "                \"id\": 5,"
                + "                \"availableCombinations\": ["
                + "                    {"
                + "                        \"limits\": ["
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [0]"
                + "                            },"
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [0, 1]"
                + "                            },"
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [3, 4]"
                + "                            }"
                + "                        ]"
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "]";

        void initialize() throws Exception {
            super.initialize();

            chipMockId = CHIP_MOCK_V2;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 12;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(mStatusOk, chipIds)).when(mWifiMock).getChipIds(
                    any(IWifi.getChipIdsCallback.class));

            doAnswer(new GetChipAnswer(mStatusOk, chip)).when(mWifiMock).getChip(eq(12),
                    any(IWifi.getChipCallback.class));

            // initialize placeholder chip modes
            IWifiChip.ChipMode cm;
            IWifiChip.ChipIfaceCombination cic;
            IWifiChip.ChipIfaceCombinationLimit cicl;

            //   Mode 0 (only one): 1xSTA + 1x{STA,AP} + 1x{P2P,NAN}
            availableModes = new ArrayList<>();
            cm = new IWifiChip.ChipMode();
            cm.id = CHIP_MODE_ID;

            cic = new IWifiChip.ChipIfaceCombination();

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.STA);
            cic.limits.add(cicl);

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.STA);
            cicl.types.add(IfaceType.AP);
            cic.limits.add(cicl);

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.P2P);
            cicl.types.add(IfaceType.NAN);
            cic.limits.add(cicl);
            cm.availableCombinations.add(cic);
            availableModes.add(cm);

            chipModeIdValidForRtt = CHIP_MODE_ID;

            doAnswer(new GetAvailableModesAnswer(this)).when(chip)
                    .getAvailableModes(any(IWifiChip.getAvailableModesCallback.class));
        }
    }

    // test chip configuration V3:
    // mode:
    //    STA + (STA || AP)
    //    STA + (NAN || P2P)
    private class TestChipV3 extends ChipMockBase {
        // only mode (different number from any in other TestChips so can catch test errors)
        static final int CHIP_MODE_ID = 7;

        void initialize() throws Exception {
            super.initialize();

            chipMockId = CHIP_MOCK_V3;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 15;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(mStatusOk, chipIds)).when(mWifiMock).getChipIds(
                    any(IWifi.getChipIdsCallback.class));

            doAnswer(new GetChipAnswer(mStatusOk, chip)).when(mWifiMock).getChip(eq(15),
                    any(IWifi.getChipCallback.class));

            // initialize placeholder chip modes
            IWifiChip.ChipMode cm;
            IWifiChip.ChipIfaceCombination cic;
            IWifiChip.ChipIfaceCombinationLimit cicl;

            //   Mode 0 (only one): 1xSTA + 1x{STA,AP}, 1xSTA + 1x{P2P,NAN}
            availableModes = new ArrayList<>();
            cm = new IWifiChip.ChipMode();
            cm.id = CHIP_MODE_ID;

            cic = new IWifiChip.ChipIfaceCombination();

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.STA);
            cic.limits.add(cicl);

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.STA);
            cicl.types.add(IfaceType.AP);
            cic.limits.add(cicl);

            cm.availableCombinations.add(cic);

            cic = new IWifiChip.ChipIfaceCombination();

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.STA);
            cic.limits.add(cicl);

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.P2P);
            cicl.types.add(IfaceType.NAN);
            cic.limits.add(cicl);

            cm.availableCombinations.add(cic);
            availableModes.add(cm);

            chipModeIdValidForRtt = CHIP_MODE_ID;

            doAnswer(new GetAvailableModesAnswer(this)).when(chip)
                    .getAvailableModes(any(IWifiChip.getAvailableModesCallback.class));
        }
    }

    // test chip configuration V4:
    // mode:
    //    STA + AP
    //    STA + (NAN || P2P)
    private class TestChipV4 extends ChipMockBase {
        // only mode (different number from any in other TestChips so can catch test errors)
        static final int CHIP_MODE_ID = 15;

        void initialize() throws Exception {
            super.initialize();

            chipMockId = CHIP_MOCK_V4;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 23;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(mStatusOk, chipIds)).when(mWifiMock).getChipIds(
                    any(IWifi.getChipIdsCallback.class));

            doAnswer(new GetChipAnswer(mStatusOk, chip)).when(mWifiMock).getChip(eq(23),
                    any(IWifi.getChipCallback.class));

            // initialize placeholder chip modes
            IWifiChip.ChipMode cm;
            IWifiChip.ChipIfaceCombination cic;
            IWifiChip.ChipIfaceCombinationLimit cicl;

            //   Mode 0 (only one): 1xSTA + 1xAP, 1xSTA + 1x{P2P,NAN}
            availableModes = new ArrayList<>();
            cm = new IWifiChip.ChipMode();
            cm.id = CHIP_MODE_ID;

            cic = new IWifiChip.ChipIfaceCombination();

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.STA);
            cic.limits.add(cicl);

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.AP);
            cic.limits.add(cicl);

            cm.availableCombinations.add(cic);

            cic = new IWifiChip.ChipIfaceCombination();

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.STA);
            cic.limits.add(cicl);

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.P2P);
            cicl.types.add(IfaceType.NAN);
            cic.limits.add(cicl);

            cm.availableCombinations.add(cic);
            availableModes.add(cm);

            chipModeIdValidForRtt = CHIP_MODE_ID;

            doAnswer(new GetAvailableModesAnswer(this)).when(chip)
                    .getAvailableModes(any(IWifiChip.getAvailableModesCallback.class));
        }
    }

    // test chip configuration V5 for 60GHz:
    // mode:
    //    STA + AP
    //    STA + (NAN || P2P)
    private class TestChipV5 extends ChipMockBase {
        // only mode (different number from any in other TestChips so can catch test errors)
        static final int CHIP_MODE_ID = 3;
        static final int CHIP_ID = 5;

        void initialize() throws Exception {
            super.initialize();
            chipMockId = CHIP_MOCK_V5;

            chipCapabilities |= android.hardware.wifi.V1_5.IWifiChip.ChipCapabilityMask.WIGIG;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = CHIP_ID;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(mStatusOk, chipIds)).when(mWifiMock).getChipIds(
                    any(IWifi.getChipIdsCallback.class));

            doAnswer(new GetChipAnswer(mStatusOk, chip)).when(mWifiMock).getChip(eq(CHIP_ID),
                    any(IWifi.getChipCallback.class));

            // initialize placeholder chip modes
            IWifiChip.ChipMode cm;
            IWifiChip.ChipIfaceCombination cic;
            IWifiChip.ChipIfaceCombinationLimit cicl;

            //   Mode 0 (only one): 1xSTA + 1xAP, 1xSTA + 1x{P2P,NAN}
            availableModes = new ArrayList<>();
            cm = new IWifiChip.ChipMode();
            cm.id = CHIP_MODE_ID;

            cic = new IWifiChip.ChipIfaceCombination();

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.STA);
            cic.limits.add(cicl);

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.AP);
            cic.limits.add(cicl);

            cm.availableCombinations.add(cic);

            cic = new IWifiChip.ChipIfaceCombination();

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.STA);
            cic.limits.add(cicl);

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.P2P);
            cicl.types.add(IfaceType.NAN);
            cic.limits.add(cicl);

            cm.availableCombinations.add(cic);
            availableModes.add(cm);

            chipModeIdValidForRtt = CHIP_MODE_ID;

            doAnswer(new GetAvailableModesAnswer(this)).when(chip)
                    .getAvailableModes(any(IWifiChip.getAvailableModesCallback.class));
        }
    }

    // test chip configuration V6 for Bridged AP:
    // mode:
    //    STA + (AP || AP_BRIDGED)
    //    STA + (NAN || P2P)
    private class TestChipV6 extends ChipMockBase {
        // only mode (different number from any in other TestChips so can catch test errors)
        static final int CHIP_MODE_ID = 60;
        static final int CHIP_ID = 6;
        static final String STATIC_CHIP_INFO_JSON_STRING = "["
                + "    {"
                + "        \"chipId\": 6,"
                + "        \"chipCapabilities\": 0,"
                + "        \"availableModes\": ["
                + "            {"
                + "                \"id\": 60,"
                + "                \"availableCombinations\": ["
                + "                    {"
                + "                        \"limits\": ["
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [0]"
                + "                            },"
                + "                            {"
                + "                                \"maxIfaces\": 1,"
                + "                                \"types\": [1, 2]"
                + "                            }"
                + "                        ]"
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "]";

        void initialize() throws Exception {
            super.initialize();
            chipMockId = CHIP_MOCK_V6;

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = CHIP_ID;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(mStatusOk, chipIds)).when(mWifiMock).getChipIds(
                    any(IWifi.getChipIdsCallback.class));

            doAnswer(new GetChipAnswer(mStatusOk, chip)).when(mWifiMock).getChip(eq(CHIP_ID),
                    any(IWifi.getChipCallback.class));

            // initialize placeholder chip modes
            android.hardware.wifi.V1_6.IWifiChip.ChipMode cm;
            android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination ccc;
            android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit cccl;

            // Mode 60 (only one): 1xSTA + 1x{AP,AP_BRIDGED}, 1xSTA + 1x{P2P,NAN}
            availableModes_1_6 = new ArrayList<>();
            cm = new android.hardware.wifi.V1_6.IWifiChip.ChipMode();
            cm.id = CHIP_MODE_ID;

            ccc = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination();

            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.STA);
            ccc.limits.add(cccl);

            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.AP);
            cccl.types.add(IfaceConcurrencyType.AP_BRIDGED);
            ccc.limits.add(cccl);

            cm.availableCombinations.add(ccc);

            ccc = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination();

            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceType.STA);
            ccc.limits.add(cccl);

            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceType.P2P);
            cccl.types.add(IfaceType.NAN);
            ccc.limits.add(cccl);

            cm.availableCombinations.add(ccc);

            availableModes_1_6.add(cm);

            chipModeIdValidForRtt = CHIP_MODE_ID;

            doAnswer(new GetAvailableModesAnswer_1_6(this))
                    .when(chip).getAvailableModes_1_6(any(
                            android.hardware.wifi.V1_6.IWifiChip.getAvailableModes_1_6Callback
                                    .class));
            mWifiChipV16 = chip;
        }
    }

    // test chip configuration V7 for testing interface priorities for mode switching
    // mode 0: STA + STA
    // mode 1: AP
    // mode 2: STA + AP || AP + AP_BRIDGED
    private class TestChipV7 extends ChipMockBase {
        static final int DUAL_STA_CHIP_MODE_ID = 71;
        static final int AP_CHIP_MODE_ID = 72;
        static final int AP_AP_BRIDGED_CHIP_MODE_ID = 73;

        void initialize() throws Exception {
            super.initialize();

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 70;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(mStatusOk, chipIds)).when(mWifiMock).getChipIds(
                    any(IWifi.getChipIdsCallback.class));

            doAnswer(new GetChipAnswer(mStatusOk, chip)).when(mWifiMock).getChip(eq(chipId),
                    any(IWifi.getChipCallback.class));

            // initialize placeholder chip modes
            android.hardware.wifi.V1_6.IWifiChip.ChipMode cm;
            android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination ccc;
            android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit cccl;

            availableModes_1_6 = new ArrayList<>();

            cm = new android.hardware.wifi.V1_6.IWifiChip.ChipMode();
            cm.id = DUAL_STA_CHIP_MODE_ID;
            ccc = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination();
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 2;
            cccl.types.add(IfaceConcurrencyType.STA);
            ccc.limits.add(cccl);
            cm.availableCombinations.add(ccc);
            availableModes_1_6.add(cm);

            cm = new android.hardware.wifi.V1_6.IWifiChip.ChipMode();
            cm.id = AP_CHIP_MODE_ID;
            ccc = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination();
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.AP);
            ccc.limits.add(cccl);
            cm.availableCombinations.add(ccc);
            availableModes_1_6.add(cm);

            cm = new android.hardware.wifi.V1_6.IWifiChip.ChipMode();
            cm.id = AP_AP_BRIDGED_CHIP_MODE_ID;
            ccc = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination();
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.STA);
            ccc.limits.add(cccl);
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.AP);
            ccc.limits.add(cccl);
            cm.availableCombinations.add(ccc);
            ccc = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination();
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.AP);
            ccc.limits.add(cccl);
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.AP_BRIDGED);
            ccc.limits.add(cccl);
            cm.availableCombinations.add(ccc);
            availableModes_1_6.add(cm);

            chipModeIdValidForRtt = DUAL_STA_CHIP_MODE_ID;

            doAnswer(new GetAvailableModesAnswer_1_6(this))
                    .when(chip).getAvailableModes_1_6(any(
                            android.hardware.wifi.V1_6.IWifiChip.getAvailableModes_1_6Callback
                                    .class));
            mWifiChipV16 = chip;
        }
    }

    // test chip configuration V8 for testing AP/AP_BRIDGED deletion
    // mode 0: STA + AP || AP + AP_BRIDGED
    private class TestChipV8 extends ChipMockBase {
        static final int CHIP_MODE_ID = 71;

        void initialize() throws Exception {
            super.initialize();

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 80;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(mStatusOk, chipIds)).when(mWifiMock).getChipIds(
                    any(IWifi.getChipIdsCallback.class));

            doAnswer(new GetChipAnswer(mStatusOk, chip)).when(mWifiMock).getChip(eq(chipId),
                    any(IWifi.getChipCallback.class));

            // initialize placeholder chip modes
            android.hardware.wifi.V1_6.IWifiChip.ChipMode cm;
            android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination ccc;
            android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit cccl;

            availableModes_1_6 = new ArrayList<>();

            cm = new android.hardware.wifi.V1_6.IWifiChip.ChipMode();
            cm.id = CHIP_MODE_ID;
            ccc = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination();
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.STA);
            ccc.limits.add(cccl);
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.AP);
            ccc.limits.add(cccl);
            cm.availableCombinations.add(ccc);
            ccc = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination();
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.AP);
            ccc.limits.add(cccl);
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.AP_BRIDGED);
            ccc.limits.add(cccl);
            cm.availableCombinations.add(ccc);
            availableModes_1_6.add(cm);

            chipModeIdValidForRtt = CHIP_MODE_ID;

            doAnswer(new GetAvailableModesAnswer_1_6(this))
                    .when(chip).getAvailableModes_1_6(any(
                            android.hardware.wifi.V1_6.IWifiChip.getAvailableModes_1_6Callback
                                    .class));
            mWifiChipV16 = chip;
        }
    }

    // test chip configuration V9 for Bridged AP without STA + Bridged AP concurrency:
    // mode:
    //    (STA + AP) || (AP_BRIDGED)
    private class TestChipV9 extends ChipMockBase {
        static final int CHIP_MODE_ID = 90;

        void initialize() throws Exception {
            super.initialize();

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 9;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(mStatusOk, chipIds)).when(mWifiMock).getChipIds(
                    any(IWifi.getChipIdsCallback.class));

            doAnswer(new GetChipAnswer(mStatusOk, chip)).when(mWifiMock).getChip(eq(chipId),
                    any(IWifi.getChipCallback.class));

            // initialize placeholder chip modes
            android.hardware.wifi.V1_6.IWifiChip.ChipMode cm;
            android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination ccc;
            android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit cccl;

            // Mode 90 (only one): (1xSTA + 1xAP) || (1xAP_BRIDGED)
            availableModes_1_6 = new ArrayList<>();
            cm = new android.hardware.wifi.V1_6.IWifiChip.ChipMode();
            cm.id = CHIP_MODE_ID;

            ccc = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination();
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.STA);
            ccc.limits.add(cccl);
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.AP);
            ccc.limits.add(cccl);
            cm.availableCombinations.add(ccc);

            ccc = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombination();
            cccl = new android.hardware.wifi.V1_6.IWifiChip.ChipConcurrencyCombinationLimit();
            cccl.maxIfaces = 1;
            cccl.types.add(IfaceConcurrencyType.AP_BRIDGED);
            ccc.limits.add(cccl);
            cm.availableCombinations.add(ccc);

            availableModes_1_6.add(cm);

            chipModeIdValidForRtt = CHIP_MODE_ID;

            doAnswer(new GetAvailableModesAnswer_1_6(this))
                    .when(chip).getAvailableModes_1_6(any(
                            android.hardware.wifi.V1_6.IWifiChip.getAvailableModes_1_6Callback
                                    .class));
            mWifiChipV16 = chip;
        }
    }
}

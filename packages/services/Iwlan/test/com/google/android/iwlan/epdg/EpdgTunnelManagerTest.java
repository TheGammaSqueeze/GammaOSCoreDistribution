/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan.epdg;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.IpSecManager;
import android.net.IpSecTransform;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.ipsec.ike.ChildSessionCallback;
import android.net.ipsec.ike.ChildSessionConfiguration;
import android.net.ipsec.ike.ChildSessionParams;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSession;
import android.net.ipsec.ike.IkeSessionCallback;
import android.net.ipsec.ike.IkeSessionConfiguration;
import android.net.ipsec.ike.IkeSessionConnectionInfo;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.SaProposal;
import android.net.ipsec.ike.TunnelModeChildSessionParams;
import android.net.ipsec.ike.exceptions.IkeException;
import android.net.ipsec.ike.exceptions.IkeInternalException;
import android.net.ipsec.ike.exceptions.IkeProtocolException;
import android.net.ipsec.ike.ike3gpp.Ike3gppBackoffTimer;
import android.net.ipsec.ike.ike3gpp.Ike3gppData;
import android.net.ipsec.ike.ike3gpp.Ike3gppExtension;
import android.os.PersistableBundle;
import android.os.test.TestLooper;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;

import com.google.android.iwlan.IwlanError;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;

@RunWith(JUnit4.class)
public class EpdgTunnelManagerTest {
    public static final int DEFAULT_SLOT_INDEX = 0;
    public static final int DEFAULT_SUBID = 0;
    public static final int DEFAULT_TOKEN = 0;

    private static final String EPDG_ADDRESS = "127.0.0.1";
    private static final String TEST_APN_NAME = "www.xyz.com";

    private static final ArrayList<InetAddress> EXPECTED_LOCAL_ADDRESSES =
            new ArrayList<>(
                    Arrays.asList(
                            new InetAddress[] {InetAddresses.parseNumericAddress("201.1.100.10")}));
    private static final ArrayList<InetAddress> EXPECTED_EPDG_ADDRESSES =
            new ArrayList<>(
                    Arrays.asList(
                            new InetAddress[] {InetAddresses.parseNumericAddress(EPDG_ADDRESS)}));
    private static final ArrayList<LinkAddress> EXPECTED_INTERNAL_ADDRESSES =
            new ArrayList<>(
                    Arrays.asList(
                            new LinkAddress[] {
                                new LinkAddress(
                                        InetAddresses.parseNumericAddress("198.50.100.10"), 24)
                            }));
    private static final ArrayList<InetAddress> EXPECTED_PCSCF_ADDRESSES =
            new ArrayList<>(
                    Arrays.asList(
                            new InetAddress[] {
                                InetAddresses.parseNumericAddress("198.51.100.10")
                            }));
    private static final ArrayList<InetAddress> EXPECTED_DNS_ADDRESSES =
            new ArrayList<>(
                    Arrays.asList(
                            new InetAddress[] {
                                InetAddresses.parseNumericAddress("198.50.100.10")
                            }));

    private EpdgTunnelManager mEpdgTunnelManager;

    private class IwlanTunnelCallback implements EpdgTunnelManager.TunnelCallback {
        public void onOpened(String apnName, TunnelLinkProperties linkProperties) {}

        public void onClosed(String apnName, IwlanError error) {}
    }

    private static class IwlanTunnelCallbackMetrics
            implements EpdgTunnelManager.TunnelCallbackMetrics {
        public void onOpened(
                String apnName,
                String epdgServerAddress,
                int epdgServerSelectionDuration,
                int ikeTunnelEstablishmentDuration) {}

        public void onClosed(
                String apnName,
                String epdgServerAddress,
                int epdgServerSelectionDuration,
                int ikeTunnelEstablishmentDuration) {}
    }

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    private TestLooper mTestLooper = new TestLooper();

    @Mock private Context mMockContext;
    @Mock private IwlanTunnelCallback mMockIwlanTunnelCallback;
    @Mock private IwlanTunnelCallbackMetrics mMockIwlanTunnelCallbackMetrics;
    @Mock private IkeSession mMockIkeSession;
    @Mock private EpdgSelector mMockEpdgSelector;
    @Mock private Network mMockNetwork;
    @Mock CarrierConfigManager mMockCarrierConfigManager;
    @Mock ConnectivityManager mMockConnectivityManager;
    @Mock SubscriptionManager mMockSubscriptionManager;
    @Mock SubscriptionInfo mMockSubscriptionInfo;
    @Mock TelephonyManager mMockTelephonyManager;
    @Mock IpSecManager mMockIpSecManager;
    @Mock EpdgTunnelManager.IkeSessionCreator mMockIkeSessionCreator;
    @Mock IkeException mMockIkeException;
    @Mock IkeSessionConfiguration mMockIkeSessionConfiguration;
    @Mock ChildSessionConfiguration mMockChildSessionConfiguration;
    @Mock IpSecManager.IpSecTunnelInterface mMockIpSecTunnelInterface;
    @Mock IkeSessionConnectionInfo mMockIkeSessionConnectionInfo;
    @Mock IpSecTransform mMockedIpSecTransformIn;
    @Mock IpSecTransform mMockedIpSecTransformOut;
    @Mock LinkProperties mMockLinkProperties;

    Queue<Object> mSpyPendingBringUpRequests;

    class IkeSessionArgumentCaptors {
        ArgumentCaptor<IkeSessionParams> mIkeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<ChildSessionParams> mChildSessionParamsCaptor =
                ArgumentCaptor.forClass(ChildSessionParams.class);
        ArgumentCaptor<IkeSessionCallback> mIkeSessionCallbackCaptor =
                ArgumentCaptor.forClass(IkeSessionCallback.class);
        ArgumentCaptor<ChildSessionCallback> mChildSessionCallbackCaptor =
                ArgumentCaptor.forClass(ChildSessionCallback.class);
    }

    @Before
    public void setUp() throws Exception {
        EpdgTunnelManager.resetAllInstances();
        mEpdgTunnelManager = spy(EpdgTunnelManager.getInstance(mMockContext, DEFAULT_SLOT_INDEX));

        when(mMockContext.getSystemService(eq(IpSecManager.class))).thenReturn(mMockIpSecManager);

        doReturn(mTestLooper.getLooper()).when(mEpdgTunnelManager).getLooper();
        setVariable(mEpdgTunnelManager, "mContext", mMockContext);
        mEpdgTunnelManager.initHandler();
        mEpdgTunnelManager.resetTunnelManagerState();
        doReturn(mMockEpdgSelector).when(mEpdgTunnelManager).getEpdgSelector();
        when(mEpdgTunnelManager.getIkeSessionCreator()).thenReturn(mMockIkeSessionCreator);

        mSpyPendingBringUpRequests = spy(new LinkedList<>());
        setVariable(mEpdgTunnelManager, "mPendingBringUpRequests", mSpyPendingBringUpRequests);
        doAnswer(i -> mSpyPendingBringUpRequests.remove()).when(mSpyPendingBringUpRequests).poll();

        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        anyBoolean(),
                        anyBoolean(),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));

        // initialize carrier configuration
        PersistableBundle bundle = new PersistableBundle();
        setupMockForGetConfig(bundle);

        when(mMockIkeSessionConfiguration.getPcscfServers()).thenReturn(EXPECTED_PCSCF_ADDRESSES);

        when(mMockChildSessionConfiguration.getInternalDnsServers())
                .thenReturn(EXPECTED_DNS_ADDRESSES);
        when(mMockChildSessionConfiguration.getInternalAddresses())
                .thenReturn(EXPECTED_INTERNAL_ADDRESSES);

        when(mMockIpSecManager.createIpSecTunnelInterface(any(), any(), any()))
                .thenReturn(mMockIpSecTunnelInterface);

        when(mMockIpSecTunnelInterface.getInterfaceName()).thenReturn("wlan0");

        when(mMockIkeSessionConnectionInfo.getNetwork()).thenReturn(mMockNetwork);

        doReturn(EXPECTED_LOCAL_ADDRESSES)
                .when(mEpdgTunnelManager)
                .getAddressForNetwork(any(), any());
    }

    @Test
    public void testBringUpTunnelWithInvalidProtocol() {
        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_PPP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertFalse(ret);
    }

    @Test
    public void testBringUpTunnelWithInvalidPduSessionId() {
        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IPV6, 16),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertFalse(ret);

        ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IPV6, -1),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertFalse(ret);
    }

    @Test
    public void testBringUpTunnelWithValidProtocols() {
        String testApnName1 = "www.xyz.com1";
        String testApnName2 = "www.xyz.com2";
        String testApnName3 = "www.xyz.com3";

        TunnelSetupRequest TSR_v4 =
                getBasicTunnelSetupRequest(testApnName1, ApnSetting.PROTOCOL_IP);

        TunnelSetupRequest TSR_v6 =
                getBasicTunnelSetupRequest(testApnName2, ApnSetting.PROTOCOL_IPV6);

        TunnelSetupRequest TSR_v4v6 =
                getBasicTunnelSetupRequest(testApnName3, ApnSetting.PROTOCOL_IPV4V6);

        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName1));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName2));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName3));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR_v4, mMockIwlanTunnelCallback, mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);

        ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR_v6, mMockIwlanTunnelCallback, mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);

        ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR_v4v6, mMockIwlanTunnelCallback, mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
    }

    @Test
    public void testBringUpTunnelWithNullApn() {

        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);

        when(mEpdgTunnelManager.getTunnelSetupRequestApnName(TSR)).thenReturn(null);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelCallbackMetrics);
        assertFalse(ret);
        verify(mEpdgTunnelManager).getTunnelSetupRequestApnName(TSR);
    }

    @Test
    public void testBringUpTunnelWithExistApn() {
        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);

        when(mEpdgTunnelManager.isTunnelConfigContainExistApn(TEST_APN_NAME)).thenReturn(true);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelCallbackMetrics);
        assertFalse(ret);
        verify(mEpdgTunnelManager).isTunnelConfigContainExistApn(TEST_APN_NAME);
    }

    @Test
    public void testBringUPTunnelWithNoBringUpInProcess() {
        String testApnName2 = "www.abc.com";

        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);

        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName2,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelCallbackMetrics,
                null,
                0);
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
    }

    @Test
    public void testBringUPTunnelSuccess() throws Exception {

        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));

        PersistableBundle bundle = new PersistableBundle();
        setupMockForGetConfig(bundle);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockEpdgSelector)
                .getValidatedServerList(
                        anyInt(),
                        eq(EpdgSelector.PROTO_FILTER_IPV4),
                        eq(false),
                        eq(false),
                        eq(mMockNetwork),
                        any());
    }

    private void setupTunnelBringup(String apnName, int transactionId) throws Exception {
        setupMockForGetConfig(null);
        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(apnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(apnName, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), transactionId);
        mTestLooper.dispatchAll();
    }

    private void setupTunnelBringup() throws Exception {
        setupTunnelBringup(TEST_APN_NAME, 1 /* transactionId */);
    }

    @Test
    @Ignore("b/239753287- Telus carrier errors out on parsing DEVICE_IDENTITY response")
    public void testBringUpTunnelSetsDeviceIdentityImeiSv() throws Exception {
        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(DEFAULT_SUBID))
                .thenReturn(mMockTelephonyManager);

        String TEST_IMEI = "012345678901234";
        String TEST_IMEI_SUFFIX = "56";
        String EXPECTED_IMEISV = TEST_IMEI.substring(0, TEST_IMEI.length() - 1) + TEST_IMEI_SUFFIX;
        when(mMockTelephonyManager.getImei()).thenReturn(TEST_IMEI);
        when(mMockTelephonyManager.getDeviceSoftwareVersion()).thenReturn(TEST_IMEI_SUFFIX);

        setupTunnelBringup();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(
                ikeSessionParams.getIke3gppExtension().getIke3gppParams().getMobileDeviceIdentity(),
                EXPECTED_IMEISV);
    }

    @Test
    @Ignore("b/239753287- Telus carrier errors out on parsing DEVICE_IDENTITY response")
    public void testBringUpTunnelSetsDeviceIdentityImei() throws Exception {
        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(DEFAULT_SUBID))
                .thenReturn(mMockTelephonyManager);

        String TEST_IMEI = "012345678901234";
        when(mMockTelephonyManager.getImei()).thenReturn(TEST_IMEI);
        when(mMockTelephonyManager.getDeviceSoftwareVersion()).thenReturn(null);

        setupTunnelBringup();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(
                ikeSessionParams.getIke3gppExtension().getIke3gppParams().getMobileDeviceIdentity(),
                TEST_IMEI);
    }

    @Test
    @Ignore("b/239753287- Telus carrier errors out on parsing DEVICE_IDENTITY response")
    public void testBringUpTunnelNoDeviceIdentityWhenImeiUnavailable() throws Exception {
        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(DEFAULT_SUBID))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.getImei()).thenReturn(null);

        setupTunnelBringup();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(
                ikeSessionParams.getIke3gppExtension().getIke3gppParams().getMobileDeviceIdentity(),
                null);
    }

    @Test
    public void testBringUpTunnelWithMobilityOptions() throws Exception {
        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertTrue(ikeSessionParams.hasIkeOption(IkeSessionParams.IKE_OPTION_MOBIKE));
        assertTrue(ikeSessionParams.hasIkeOption(IkeSessionParams.IKE_OPTION_REKEY_MOBILITY));
    }

    @Test
    public void testInitialContactForFirstTunnelOnly() throws Exception {
        final String firstApnName = "ims";
        final String secondApnName = "mms";

        IkeSessionArgumentCaptors firstTunnelArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(firstApnName);
        ChildSessionCallback firstCallback =
                firstTunnelArgumentCaptors.mChildSessionCallbackCaptor.getValue();

        IkeSessionArgumentCaptors secondTunnelArgumentCaptors =
                verifyBringUpTunnel(secondApnName, true /* needPendingBringUpReq */);
        verifyTunnelOnOpened(firstApnName, firstCallback);

        ChildSessionCallback secondCallback =
                secondTunnelArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        verifyTunnelOnOpened(secondApnName, secondCallback);

        IkeSessionParams firstTunnelParams =
                firstTunnelArgumentCaptors.mIkeSessionParamsCaptor.getValue();
        IkeSessionParams secondTunnelParams =
                secondTunnelArgumentCaptors.mIkeSessionParamsCaptor.getValue();
        assertTrue(firstTunnelParams.hasIkeOption(IkeSessionParams.IKE_OPTION_INITIAL_CONTACT));
        assertFalse(secondTunnelParams.hasIkeOption(IkeSessionParams.IKE_OPTION_INITIAL_CONTACT));
    }

    @Test
    public void testCloseTunnelWithNoTunnelForApn() throws Exception {
        String testApnName = "www.xyz.com";

        boolean ret = mEpdgTunnelManager.closeTunnel(testApnName, false /*forceClose*/);
        assertTrue(ret);
        mTestLooper.dispatchAll();
        verify(mEpdgTunnelManager).closePendingRequestsForApn(eq(testApnName));
    }

    @Test
    public void testCloseTunnelWithForceClose() throws Exception {
        String testApnName = "www.xyz.com";

        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelCallbackMetrics,
                null,
                0);

        boolean ret = mEpdgTunnelManager.closeTunnel(testApnName, true /*forceClose*/);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockIkeSession).kill();
        verify(mEpdgTunnelManager).closePendingRequestsForApn(eq(testApnName));
    }

    @Test
    public void testCloseTunnelWithNonForceClose() throws Exception {
        String testApnName = "www.xyz.com";

        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelCallbackMetrics,
                null,
                0);

        boolean ret = mEpdgTunnelManager.closeTunnel(testApnName, false /*forceClose*/);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockIkeSession).close();
        verify(mEpdgTunnelManager).closePendingRequestsForApn(eq(testApnName));
    }

    @Test
    public void testRekeyAndNattTimerFromCarrierConfig() throws Exception {
        String testApnName = "www.xyz.com";

        // Test values
        int hardTime = 50000;
        int softTime = 20000;
        int hardTimeChild = 10000;
        int softTimeChild = 1000;
        int nattTimer = 60;

        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(CarrierConfigManager.Iwlan.KEY_IKE_REKEY_HARD_TIMER_SEC_INT, hardTime);
        bundle.putInt(CarrierConfigManager.Iwlan.KEY_IKE_REKEY_SOFT_TIMER_SEC_INT, softTime);
        bundle.putInt(
                CarrierConfigManager.Iwlan.KEY_CHILD_SA_REKEY_HARD_TIMER_SEC_INT, hardTimeChild);
        bundle.putInt(
                CarrierConfigManager.Iwlan.KEY_CHILD_SA_REKEY_SOFT_TIMER_SEC_INT, softTimeChild);
        bundle.putInt(CarrierConfigManager.Iwlan.KEY_NATT_KEEP_ALIVE_TIMER_SEC_INT, nattTimer);

        setupMockForGetConfig(bundle);

        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        eq(false),
                        eq(false),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<ChildSessionParams> childSessionParamsCaptor =
                ArgumentCaptor.forClass(ChildSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        childSessionParamsCaptor.capture(),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        ChildSessionParams childSessionParams = childSessionParamsCaptor.getValue();

        assertEquals(ikeSessionParams.getHardLifetimeSeconds(), hardTime);
        assertEquals(ikeSessionParams.getSoftLifetimeSeconds(), softTime);
        assertEquals(childSessionParams.getHardLifetimeSeconds(), hardTimeChild);
        assertEquals(childSessionParams.getSoftLifetimeSeconds(), softTimeChild);
        assertEquals(ikeSessionParams.getNattKeepAliveDelaySeconds(), nattTimer);
    }

    @Test
    public void testSetRetransmissionTimeoutsFromCarrierConfig() throws Exception {
        String testApnName = "www.xyz.com";

        int[] testTimeouts = {1000, 1200, 1400, 1600, 2000, 4000};

        PersistableBundle bundle = new PersistableBundle();
        bundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_RETRANSMIT_TIMER_MSEC_INT_ARRAY, testTimeouts);

        setupMockForGetConfig(bundle);

        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        eq(false),
                        eq(false),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertArrayEquals(ikeSessionParams.getRetransmissionTimeoutsMillis(), testTimeouts);
    }

    @Test
    public void testSetDpdDelayFromCarrierConfig() throws Exception {
        String testApnName = "www.xyz.com";

        // Test values
        int testDpdDelay = 600;

        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(CarrierConfigManager.Iwlan.KEY_DPD_TIMER_SEC_INT, testDpdDelay);

        setupMockForGetConfig(bundle);
        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        eq(false),
                        eq(false),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(ikeSessionParams.getDpdDelaySeconds(), testDpdDelay);
    }

    @Test
    public void testGetValidEpdgAddress_DiffAddr() throws Exception {
        String testApnName = "www.xyz.com";

        List<InetAddress> ipList1 = new ArrayList<>();
        ipList1.add(InetAddress.getByName("1.1.1.1"));
        mEpdgTunnelManager.validateAndSetEpdgAddress(ipList1);

        IwlanError error = new IwlanError(new IkeInternalException(new IOException()));

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        setupMockForGetConfig(null);
        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        eq(false),
                        eq(false),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));

        doReturn(null)
                .doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        ArrayList<InetAddress> ipList2 = new ArrayList<>();
        ipList2.add(InetAddress.getByName("8.8.8.8"));
        mEpdgTunnelManager.sendSelectionRequestComplete(
                ipList2, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        EpdgTunnelManager.TmIkeSessionCallback ikeSessionCallback =
                verifyCreateIkeSession(ipList2.get(0));
        ikeSessionCallback.onClosedExceptionally(
                new IkeInternalException(new IOException("Retransmitting failure")));
        mTestLooper.dispatchAll();

        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
    }

    @Test
    public void testGetValidEpdgAddress_NextAddr() throws Exception {
        String testApnName = "www.xyz.com";

        List<InetAddress> ipList1 = new ArrayList<>();
        ipList1.add(InetAddress.getByName("1.1.1.1"));
        ipList1.add(InetAddress.getByName("8.8.8.8"));
        mEpdgTunnelManager.validateAndSetEpdgAddress(ipList1);

        IwlanError error = new IwlanError(new IkeInternalException(new IOException()));

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        setupMockForGetConfig(null);
        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        eq(false),
                        eq(false),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));

        doReturn(null)
                .doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        ArrayList<InetAddress> ipList2 = new ArrayList<>();
        ipList2.add(InetAddress.getByName("1.1.1.1"));
        ipList2.add(InetAddress.getByName("8.8.8.8"));
        mEpdgTunnelManager.sendSelectionRequestComplete(
                ipList2, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        EpdgTunnelManager.TmIkeSessionCallback ikeSessionCallback =
                verifyCreateIkeSession(ipList2.get(1));
        ikeSessionCallback.onClosedExceptionally(
                new IkeInternalException(new IOException("Retransmitting failure")));
        mTestLooper.dispatchAll();

        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
    }

    private EpdgTunnelManager.TmIkeSessionCallback verifyCreateIkeSession(InetAddress ip)
            throws Exception {
        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<EpdgTunnelManager.TmIkeSessionCallback> ikeSessionCallbackCaptor =
                ArgumentCaptor.forClass(EpdgTunnelManager.TmIkeSessionCallback.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        ikeSessionCallbackCaptor.capture(),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(ikeSessionParams.getServerHostname(), ip.getHostAddress());
        return ikeSessionCallbackCaptor.getValue();
    }

    @Test
    public void testIpv6PrefixMatching() throws Exception {
        InetAddress a1 = InetAddress.getByName("2600:381:4872:5d1e:ac45:69c7:bab2:639b");
        LinkAddress l1 = new LinkAddress(a1, 64);
        InetAddress src = InetAddress.getByName("2600:381:4872:5d1e:0:10:3582:a501");
        EpdgTunnelManager.TunnelConfig tf =
                mEpdgTunnelManager.new TunnelConfig(null, null, null, src, 64);
        assertTrue(tf.isPrefixSameAsSrcIP(l1));

        // different prefix length
        LinkAddress l2 = new LinkAddress(a1, 63);
        assertFalse(tf.isPrefixSameAsSrcIP(l2));
    }

    @Test
    public void testBackOffTimeCalculation() throws Exception {
        int transactionId = 1;

        // unit: 10 mins value: 2 expectedTime: 1200 (10 * 60 * 2)
        verifyBackOffTimer("00000010", 1200, transactionId++);
        // unit: 1 hour value: 4 expectedTime: 14400 (1 * 60 * 60 * 4)
        verifyBackOffTimer("00100100", 14400, transactionId++);
        // unit: 10 hours value: 3 expectedTime: (10 * 60 * 60 * 3)
        verifyBackOffTimer("01000011", 108000, transactionId++);
        // unit: 2 secs value: 21 expectedTime: 42 (2 * 21)
        verifyBackOffTimer("01110101", 42, transactionId++);
        // unit: 30 secs value: 31 expectedTime: 930 (30 * 31)
        verifyBackOffTimer("10011111", 930, transactionId++);
        // unit: 1 min value: 25 expectedTime: 1500 (1 * 60 * 25)
        verifyBackOffTimer("10111001", 1500, transactionId++);
        // unit: 1 hour value: 12 expectedTime: 43200 (1 * 60 * 60 * 12)
        verifyBackOffTimer("11001100", 43200, transactionId++);
        // deactivate - Should not report backoff time.
        verifyBackOffTimer("11100100", -1, transactionId++);
    }

    private void verifyBackOffTimer(String backoffByte, long expectedBackoffTime, int transactionId)
            throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(new IkeInternalException(new Exception()));
        Ike3gppBackoffTimer mockIke3gppBackoffTimer = mock(Ike3gppBackoffTimer.class);
        List<Ike3gppData> ike3gppInfoList = new ArrayList<>();
        ike3gppInfoList.add(mockIke3gppBackoffTimer);
        doReturn(Ike3gppData.DATA_TYPE_NOTIFY_BACKOFF_TIMER)
                .when(mockIke3gppBackoffTimer)
                .getDataType();
        doReturn((byte) (int) Integer.parseInt(backoffByte, 2))
                .when(mockIke3gppBackoffTimer)
                .getBackoffTimer();

        // if back off time expected is negative normal reportIwlanError should be called.
        if (expectedBackoffTime < 0) {
            doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        } else {
            doReturn(0L)
                    .when(mEpdgTunnelManager)
                    .reportIwlanError(eq(testApnName), eq(error), anyLong());
        }

        setupMockForGetConfig(null);
        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        eq(false),
                        eq(false),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));
        doReturn(null)
                .doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), transactionId);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<EpdgTunnelManager.TmIkeSessionCallback> ikeSessionCallbackCaptor =
                ArgumentCaptor.forClass(EpdgTunnelManager.TmIkeSessionCallback.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        ikeSessionCallbackCaptor.capture(),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(
                ikeSessionParams.getServerHostname(),
                EXPECTED_EPDG_ADDRESSES.get(0).getHostAddress());

        Ike3gppExtension.Ike3gppDataListener ike3gppCallback =
                ikeSessionParams.getIke3gppExtension().getIke3gppDataListener();
        ike3gppCallback.onIke3gppDataReceived(ike3gppInfoList);
        EpdgTunnelManager.TmIkeSessionCallback ikeSessionCallback =
                ikeSessionCallbackCaptor.getValue();
        ikeSessionCallback.onClosedExceptionally(new IkeInternalException(new Exception()));
        mTestLooper.dispatchAll();

        // if expected backoff time is negative - verify that backoff time is not reported.
        if (expectedBackoffTime < 0) {
            verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
        } else {
            // Else - Verify reportIwlanError with correct backoff time is being called.
            verify(mEpdgTunnelManager, times(1))
                    .reportIwlanError(eq(testApnName), eq(error), eq(expectedBackoffTime));
        }
        verify(mMockIwlanTunnelCallback, atLeastOnce()).onClosed(eq(testApnName), eq(error));
    }

    private TunnelSetupRequest getBasicTunnelSetupRequest(String apnName, int apnIpProtocol) {
        return getBasicTunnelSetupRequest(apnName, apnIpProtocol, 1);
    }

    private TunnelSetupRequest getBasicTunnelSetupRequest(
            String apnName, int apnIpProtocol, int pduSessionId) {
        TunnelSetupRequest ret =
                TunnelSetupRequest.builder()
                        .setApnName(apnName)
                        .setNetwork(mMockNetwork)
                        .setIsRoaming(false /*isRoaming*/)
                        .setIsEmergency(false /*IsEmergency*/)
                        .setRequestPcscf(false /*requestPcscf*/)
                        .setApnIpProtocol(apnIpProtocol)
                        .setPduSessionId(pduSessionId)
                        .build();
        return ret;
    }

    private TunnelSetupRequest getHandoverTunnelSetupRequest(String apnName, int apnIpProtocol) {
        TunnelSetupRequest.Builder bld = TunnelSetupRequest.builder();
        bld.setApnName(apnName)
                .setNetwork(mMockNetwork)
                .setIsRoaming(false /*isRoaming*/)
                .setIsEmergency(false /*IsEmergency*/)
                .setRequestPcscf(false /*requestPcscf*/)
                .setApnIpProtocol(apnIpProtocol)
                .setPduSessionId(1);
        switch (apnIpProtocol) {
            case ApnSetting.PROTOCOL_IP:
                bld.setSrcIpv4Address(InetAddresses.parseNumericAddress("10.10.10.10"));
                break;
            case ApnSetting.PROTOCOL_IPV6:
                bld.setSrcIpv6Address(
                        InetAddresses.parseNumericAddress(
                                "2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
                break;
            case ApnSetting.PROTOCOL_IPV4V6:
                bld.setSrcIpv4Address(InetAddresses.parseNumericAddress("10.10.10.10"));
                bld.setSrcIpv6Address(
                        InetAddresses.parseNumericAddress(
                                "2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
                break;
        }
        return bld.build();
    }

    private void setupMockForGetConfig(PersistableBundle bundle) {
        if (bundle == null) {
            bundle = new PersistableBundle();
        }
        bundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_SUPPORTED_INTEGRITY_ALGORITHMS_INT_ARRAY,
                new int[] {
                    SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96,
                    SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128,
                    SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_384_192,
                    SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_512_256,
                });
        when(mMockContext.getSystemService(eq(CarrierConfigManager.class)))
                .thenReturn(mMockCarrierConfigManager);
        when(mMockContext.getSystemService(eq(ConnectivityManager.class)))
                .thenReturn(mMockConnectivityManager);
        when(mMockContext.getSystemService(eq(SubscriptionManager.class)))
                .thenReturn(mMockSubscriptionManager);
        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);
        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(DEFAULT_SLOT_INDEX))
                .thenReturn(mMockSubscriptionInfo);
        when(mMockSubscriptionInfo.getSubscriptionId()).thenReturn(DEFAULT_SUBID);
        when(mMockSubscriptionInfo.getMncString()).thenReturn("344");
        when(mMockTelephonyManager.createForSubscriptionId(DEFAULT_SUBID))
                .thenReturn(mMockTelephonyManager);
        when(mMockCarrierConfigManager.getConfigForSubId(DEFAULT_SLOT_INDEX)).thenReturn(bundle);
    }

    private void setVariable(Object target, String variableName, Object value) throws Exception {
        FieldSetter.setField(target, target.getClass().getDeclaredField(variableName), value);
    }

    @Test
    public void testHandleOnClosedWithEpdgAddressSelected_True() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(IwlanError.NETWORK_FAILURE);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelCallbackMetrics,
                null,
                0);
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);

        mEpdgTunnelManager.setIsEpdgAddressSelected(true);
        mEpdgTunnelManager.setEpdgAddress(InetAddresses.parseNumericAddress(EPDG_ADDRESS));

        mEpdgTunnelManager.getTmIkeSessionCallback(testApnName, token).onClosed();
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
        verify(mEpdgTunnelManager, times(2)).resetTunnelManagerState();
        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    @Test
    public void testHandleOnClosedWithEpdgAddressSelected_False() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(IwlanError.NETWORK_FAILURE);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));

        PersistableBundle bundle = new PersistableBundle();
        setupMockForGetConfig(bundle);
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.setIsEpdgAddressSelected(false);

        mEpdgTunnelManager.getTmIkeSessionCallback(testApnName, DEFAULT_TOKEN).onClosed();
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
        verify(mEpdgTunnelManager, times(2)).resetTunnelManagerState();
        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    private void setOneTunnelOpened(String apnName) throws Exception {
        mEpdgTunnelManager.putApnNameToTunnelConfig(
                apnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelCallbackMetrics,
                null,
                0);
        setVariable(mEpdgTunnelManager, "mLocalAddresses", EXPECTED_LOCAL_ADDRESSES);
        mEpdgTunnelManager.validateAndSetEpdgAddress(EXPECTED_EPDG_ADDRESSES);
        mEpdgTunnelManager.setIsEpdgAddressSelected(true);
    }

    private IkeSessionArgumentCaptors verifyBringUpTunnelWithDnsQuery(String apnName) {
        return verifyBringUpTunnelWithDnsQuery(apnName, null);
    }

    private IkeSessionArgumentCaptors verifyBringUpTunnelWithDnsQuery(
            String apnName, IkeSession ikeSession) {
        reset(mMockIwlanTunnelCallback);
        IkeSessionArgumentCaptors ikeSessionArgumentCaptors = new IkeSessionArgumentCaptors();

        verifyBringUpTunnel(apnName, true /* needPendingBringUpReq */);

        doReturn(ikeSession)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionArgumentCaptors.mIkeSessionParamsCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionParamsCaptor.capture(),
                        any(Executor.class),
                        ikeSessionArgumentCaptors.mIkeSessionCallbackCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.capture());

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        verify(mMockIkeSessionCreator, times(1))
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionArgumentCaptors.mIkeSessionParamsCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionParamsCaptor.capture(),
                        any(Executor.class),
                        ikeSessionArgumentCaptors.mIkeSessionCallbackCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.capture());

        return ikeSessionArgumentCaptors;
    }

    private IkeSessionArgumentCaptors verifyBringUpTunnel(
            String apnName, boolean needPendingBringUpReq) {
        reset(mMockIkeSessionCreator);
        IkeSessionArgumentCaptors ikeSessionArgumentCaptors = new IkeSessionArgumentCaptors();

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionArgumentCaptors.mIkeSessionParamsCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionParamsCaptor.capture(),
                        any(Executor.class),
                        ikeSessionArgumentCaptors.mIkeSessionCallbackCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.capture());

        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(apnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(apnName, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockIkeSessionCreator, times(needPendingBringUpReq ? 0 : 1))
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionArgumentCaptors.mIkeSessionParamsCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionParamsCaptor.capture(),
                        any(Executor.class),
                        ikeSessionArgumentCaptors.mIkeSessionCallbackCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.capture());

        return ikeSessionArgumentCaptors;
    }

    private void verifyTunnelOnOpened(String apnName, ChildSessionCallback childSessionCallback) {
        doReturn(0L)
                .when(mEpdgTunnelManager)
                .reportIwlanError(eq(apnName), eq(new IwlanError(IwlanError.NO_ERROR)));
        mEpdgTunnelManager
                .getTmIkeSessionCallback(apnName, mEpdgTunnelManager.getCurrentTokenForApn(apnName))
                .onOpened(mMockIkeSessionConfiguration);
        mTestLooper.dispatchAll();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);
        mTestLooper.dispatchAll();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformOut, IpSecManager.DIRECTION_OUT);
        mTestLooper.dispatchAll();
        childSessionCallback.onOpened(mMockChildSessionConfiguration);
        mTestLooper.dispatchAll();

        verify(mEpdgTunnelManager, times(1))
                .reportIwlanError(eq(apnName), eq(new IwlanError(IwlanError.NO_ERROR)));
        verify(mMockIwlanTunnelCallback, times(1)).onOpened(eq(apnName), any());
    }

    @Test
    public void testHandleOnOpenedWithEpdgAddressSelected_True() throws Exception {
        final String openedApnName = "ims";
        final String toBeOpenedApnName = "mms";

        setOneTunnelOpened(openedApnName);

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnel(toBeOpenedApnName, false /* needPendingBringUpReq */);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        verifyTunnelOnOpened(toBeOpenedApnName, childSessionCallback);
    }

    @Test
    public void testServicePendingRequests() throws Exception {
        final String firstApnName = "ims";
        final String secondApnName = "mms";

        IkeSessionArgumentCaptors firstTunnelArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(firstApnName);
        ChildSessionCallback firstCallback =
                firstTunnelArgumentCaptors.mChildSessionCallbackCaptor.getValue();

        IkeSessionArgumentCaptors secondTunnelArgumentCaptors =
                verifyBringUpTunnel(secondApnName, true /* needPendingBringUpReq */);
        verifyTunnelOnOpened(firstApnName, firstCallback);

        ChildSessionCallback secondCallback =
                secondTunnelArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        verifyTunnelOnOpened(secondApnName, secondCallback);
    }

    @Test
    public void testHandleOnClosedExceptionallyWithEpdgAddressSelected_True() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(mMockIkeException);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));

        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelCallbackMetrics,
                null,
                0);
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);

        mEpdgTunnelManager.setIsEpdgAddressSelected(true);
        mEpdgTunnelManager.setEpdgAddress(InetAddresses.parseNumericAddress(EPDG_ADDRESS));

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, token)
                .onClosedExceptionally(mMockIkeException);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
        verify(mEpdgTunnelManager, times(2)).resetTunnelManagerState();
        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    @Test
    public void testHandleOnClosedExceptionallyWithEpdgAddressSelected_False() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(mMockIkeException);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));

        PersistableBundle bundle = new PersistableBundle();
        setupMockForGetConfig(bundle);
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.setIsEpdgAddressSelected(false);

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, DEFAULT_TOKEN)
                .onClosedExceptionally(mMockIkeException);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), any(IwlanError.class));
        verify(mEpdgTunnelManager, times(2)).resetTunnelManagerState();
        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    @Test
    public void testIkeSessionOnOpenedUpdatesPcscfAddrInTunnelConfig() throws Exception {
        String testApnName = "ims";
        IwlanError error = new IwlanError(IwlanError.NO_ERROR);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelCallbackMetrics,
                null,
                0);
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);

        when(mMockIkeSessionConfiguration.getPcscfServers()).thenReturn(EXPECTED_EPDG_ADDRESSES);

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, token)
                .onOpened(mMockIkeSessionConfiguration);
        mTestLooper.dispatchAll();

        EpdgTunnelManager.TunnelConfig testApnTunnelConfig =
                mEpdgTunnelManager.getTunnelConfigForApn(testApnName);
        assertEquals(testApnTunnelConfig.getPcscfAddrList(), EXPECTED_EPDG_ADDRESSES);
    }

    @Test
    public void testIkeSessionClosesWhenChildSessionTransformThrows() throws Exception {
        String testApnName = "ims";

        doThrow(new IllegalArgumentException())
                .when(mMockIpSecManager)
                .applyTunnelModeTransform(eq(mMockIpSecTunnelInterface), anyInt(), any());
        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(testApnName, mMockIkeSession);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);
        mTestLooper.dispatchAll();

        verify(mMockIkeSession, times(1)).close();
    }

    @Test
    public void testIkeSessionConnectionInfoChangedSetsUnderlyingNetwork() throws Exception {
        String testApnName = "ims";
        when(mMockConnectivityManager.getLinkProperties(any())).thenReturn(mMockLinkProperties);

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(testApnName, mMockIkeSession);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, DEFAULT_TOKEN)
                .onIkeSessionConnectionInfoChanged(mMockIkeSessionConnectionInfo);
        mTestLooper.dispatchAll();

        verify(mMockIpSecTunnelInterface, times(1)).setUnderlyingNetwork(mMockNetwork);
    }

    @Test
    public void testIkeSessionConnectionInfoChangedWithNullLinkPropertiesDoesNothing()
            throws Exception {
        String testApnName = "ims";
        when(mMockConnectivityManager.getLinkProperties(any())).thenReturn(null);

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(testApnName, mMockIkeSession);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, DEFAULT_TOKEN)
                .onIkeSessionConnectionInfoChanged(mMockIkeSessionConnectionInfo);
        mTestLooper.dispatchAll();

        verify(mMockIpSecTunnelInterface, times(0)).setUnderlyingNetwork(any());
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv4() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IP, false);
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv6() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IPV6, false);
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv4v6() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IPV4V6, false);
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv4_handover() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IP, true);
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv6_handover() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IPV6, true);
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv4v6_handover() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IPV4V6, true);
    }

    private void testSetIkeTrafficSelectors(int apnProtocol, boolean handover) throws Exception {
        String testApnName = "www.xyz.com";

        PersistableBundle bundle = new PersistableBundle();
        setupMockForGetConfig(bundle);

        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        eq(false),
                        eq(false),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret;

        if (handover) {
            ret =
                    mEpdgTunnelManager.bringUpTunnel(
                            getHandoverTunnelSetupRequest(TEST_APN_NAME, apnProtocol),
                            mMockIwlanTunnelCallback,
                            mMockIwlanTunnelCallbackMetrics);
        } else {
            ret =
                    mEpdgTunnelManager.bringUpTunnel(
                            getBasicTunnelSetupRequest(TEST_APN_NAME, apnProtocol),
                            mMockIwlanTunnelCallback,
                            mMockIwlanTunnelCallbackMetrics);
        }

        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<ChildSessionParams> childSessionParamsCaptor =
                ArgumentCaptor.forClass(ChildSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        childSessionParamsCaptor.capture(),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        ChildSessionParams childSessionParams = childSessionParamsCaptor.getValue();

        switch (apnProtocol) {
            case ApnSetting.PROTOCOL_IPV4V6:
                assertEquals(childSessionParams.getInboundTrafficSelectors().size(), 2);
                assertEquals(childSessionParams.getOutboundTrafficSelectors().size(), 2);
                assertTrue(
                        childSessionParams.getInboundTrafficSelectors().get(0).endingAddress
                                != childSessionParams
                                        .getInboundTrafficSelectors()
                                        .get(1)
                                        .endingAddress);
                assertTrue(
                        childSessionParams.getInboundTrafficSelectors().get(0).startingAddress
                                != childSessionParams
                                        .getInboundTrafficSelectors()
                                        .get(1)
                                        .startingAddress);
                break;
            case ApnSetting.PROTOCOL_IPV6:
                assertEquals(childSessionParams.getInboundTrafficSelectors().size(), 1);
                assertEquals(childSessionParams.getOutboundTrafficSelectors().size(), 1);
                assertEquals(
                        childSessionParams.getOutboundTrafficSelectors().get(0),
                        childSessionParams.getInboundTrafficSelectors().get(0));
                assertEquals(
                        childSessionParams.getInboundTrafficSelectors().get(0).endingAddress,
                        InetAddresses.parseNumericAddress(
                                "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"));
                assertEquals(
                        childSessionParams.getInboundTrafficSelectors().get(0).startingAddress,
                        InetAddresses.parseNumericAddress("::"));
                break;
            case ApnSetting.PROTOCOL_IP:
                assertEquals(childSessionParams.getInboundTrafficSelectors().size(), 1);
                assertEquals(childSessionParams.getOutboundTrafficSelectors().size(), 1);
                assertEquals(
                        childSessionParams.getOutboundTrafficSelectors().get(0),
                        childSessionParams.getInboundTrafficSelectors().get(0));
                assertEquals(
                        childSessionParams.getInboundTrafficSelectors().get(0).endingAddress,
                        InetAddresses.parseNumericAddress("255.255.255.255"));
                assertEquals(
                        childSessionParams.getInboundTrafficSelectors().get(0).startingAddress,
                        InetAddresses.parseNumericAddress("0.0.0.0"));
                break;
        }
    }

    @Test
    public void testN1modeCapabilityInclusion() throws Exception {
        {
            testN1modeCapability(8);
        }
    }

    @Test
    public void testN1modeCapabilityNonInclusion() throws Exception {
        {
            testN1modeCapability(0);
        }
    }

    @Test
    public void testReportIwlanErrorIkeProtocolException() throws Exception {
        String testApnName = "www.xyz.com";

        IkeProtocolException mockException = mock(IkeProtocolException.class);
        doReturn(IkeProtocolException.ERROR_TYPE_INVALID_IKE_SPI)
                .when(mockException)
                .getErrorType();
        doReturn(new byte[0]).when(mockException).getErrorData();
        IwlanError error = new IwlanError(mockException);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));

        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelCallbackMetrics,
                null,
                0);
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);

        mEpdgTunnelManager.setIsEpdgAddressSelected(true);
        mEpdgTunnelManager.setEpdgAddress(InetAddresses.parseNumericAddress(EPDG_ADDRESS));

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, token)
                .onClosedExceptionally(mockException);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
        verify(mEpdgTunnelManager, times(2)).resetTunnelManagerState();
        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    @Test
    public void testReportIwlanErrorServerSelectionFailed() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        PersistableBundle bundle = new PersistableBundle();
        setupMockForGetConfig(bundle);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(null, error, 1);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.setIsEpdgAddressSelected(false);

        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    @Test
    public void testNeverReportIwlanErrorWhenCloseAnOpenedTunnel() throws Exception {
        IkeInternalException ikeException =
                new IkeInternalException(new IOException("Retransmitting failure"));

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(TEST_APN_NAME);

        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        verifyTunnelOnOpened(TEST_APN_NAME, childSessionCallback);

        reset(mEpdgTunnelManager); // reset number of times of reportIwlanError()

        mEpdgTunnelManager
                .getTmIkeSessionCallback(TEST_APN_NAME, 0)
                .onClosedExceptionally(ikeException);
        mTestLooper.dispatchAll();
        verify(mEpdgTunnelManager, never()).reportIwlanError(eq(TEST_APN_NAME), any());
        verify(mMockIwlanTunnelCallback, times(1))
                .onClosed(eq(TEST_APN_NAME), eq(new IwlanError(ikeException)));
    }

    @Test
    public void testCanBringUpTunnel() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(mMockIkeException);

        doReturn(false).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));
        doReturn(error).when(mEpdgTunnelManager).getLastError(eq(testApnName));

        PersistableBundle bundle = new PersistableBundle();
        setupMockForGetConfig(bundle);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
    }

    private void testN1modeCapability(int pduSessionId) throws Exception {
        String testApnName = "www.xyz.com";
        int PDU_SESSION_ID = pduSessionId;
        byte PDU_SESSION_ID_BYTE = (byte) PDU_SESSION_ID;

        PersistableBundle bundle = new PersistableBundle();
        setupMockForGetConfig(bundle);

        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        eq(false),
                        eq(false),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret;

        ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(
                                TEST_APN_NAME, ApnSetting.PROTOCOL_IPV6, PDU_SESSION_ID),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);

        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<ChildSessionParams> childSessionParamsCaptor =
                ArgumentCaptor.forClass(ChildSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        childSessionParamsCaptor.capture(),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();

        if (pduSessionId == 0) {
            assertNull(ikeSessionParams.getIke3gppExtension());
        } else {
            byte pduSessionIdByte =
                    ikeSessionParams.getIke3gppExtension().getIke3gppParams().getPduSessionId();
            assertEquals(pduSessionIdByte, PDU_SESSION_ID_BYTE);
        }
    }

    @Test
    public void testInvalidNattTimerFromCarrierConfig() throws Exception {
        String testApnName = "www.xyz.com";

        int nattTimer = 4500; // valid range for natt timer is 0-3600
        int ikeDefaultNattTimerValue = 20; // default value for natt timer is 20 secs

        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(CarrierConfigManager.Iwlan.KEY_NATT_KEEP_ALIVE_TIMER_SEC_INT, nattTimer);

        setupMockForGetConfig(bundle);
        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        eq(false),
                        eq(false),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);

        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(ikeSessionParams.getNattKeepAliveDelaySeconds(), ikeDefaultNattTimerValue);
    }

    @Test
    public void testTunnelSetupRequestParams() throws Exception {
        String testApnName = "www.xyz.com";
        Inet6Address testAddressV6 = Inet6Address.getByAddress("25.25.25.25", new byte[16], 0);
        Inet4Address testAddressV4 = (Inet4Address) Inet4Address.getByName("30.30.30.30");
        int pduSessionId = 5;
        boolean isRoaming = false;
        boolean isEmergency = true;
        boolean requestPcscf = true;
        int ipv6AddressLen = 64;

        TunnelSetupRequest tsr =
                TunnelSetupRequest.builder()
                        .setApnName(testApnName)
                        .setNetwork(mMockNetwork)
                        .setApnIpProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setSrcIpv6Address(testAddressV6)
                        .setSrcIpv6AddressPrefixLength(ipv6AddressLen)
                        .setSrcIpv4Address(testAddressV4)
                        .setPduSessionId(pduSessionId)
                        .setIsRoaming(isRoaming)
                        .setIsEmergency(isEmergency)
                        .setRequestPcscf(requestPcscf)
                        .build();

        setupMockForGetConfig(null);
        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        eq(isRoaming),
                        eq(isEmergency),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        tsr, mMockIwlanTunnelCallback, mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        // verify isRoaming, isEmergency and Network variables.
        verify(mMockEpdgSelector)
                .getValidatedServerList(
                        anyInt(),
                        anyInt(), // only Ipv6 address is added
                        eq(isRoaming),
                        eq(isEmergency),
                        eq(mMockNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class));

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<TunnelModeChildSessionParams> childSessionParamsCaptor =
                ArgumentCaptor.forClass(TunnelModeChildSessionParams.class);

        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        childSessionParamsCaptor.capture(),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        TunnelModeChildSessionParams childSessionParams = childSessionParamsCaptor.getValue();

        // apnName verification. By default remote identification is type fqdn
        IkeFqdnIdentification ikeId =
                (IkeFqdnIdentification) ikeSessionParams.getRemoteIdentification();
        assertEquals(ikeId.fqdn, testApnName);

        // verify Network
        assertEquals(ikeSessionParams.getNetwork(), mMockNetwork);

        // verify requestPcscf (true) with Apn protocol IPV6
        // it should add the pcscf config requests of type ConfigRequestIpv6PcscfServer and
        // ConfigRequestIpv4PcscfServer
        assertTrue(
                ikeSessionParams.getConfigurationRequests().stream()
                        .anyMatch(c -> c instanceof IkeSessionParams.ConfigRequestIpv6PcscfServer));
        assertTrue(
                ikeSessionParams.getConfigurationRequests().stream()
                        .anyMatch(c -> c instanceof IkeSessionParams.ConfigRequestIpv4PcscfServer));

        // verify pduSessionID
        assertEquals(
                ikeSessionParams.getIke3gppExtension().getIke3gppParams().getPduSessionId(),
                pduSessionId);

        // verify src ipv6  and src ipv4 address
        List<TunnelModeChildSessionParams.TunnelModeChildConfigRequest> configRequests =
                childSessionParams.getConfigurationRequests();
        boolean ipv6ConfigRequestPresent = false;
        boolean ipv4ConfigRequestPresent = true;
        for (TunnelModeChildSessionParams.TunnelModeChildConfigRequest configRequest :
                configRequests) {
            if (configRequest instanceof TunnelModeChildSessionParams.ConfigRequestIpv6Address) {
                ipv6ConfigRequestPresent = true;
                assertEquals(
                        ((TunnelModeChildSessionParams.ConfigRequestIpv6Address) configRequest)
                                .getAddress(),
                        testAddressV6);
                assertEquals(
                        ((TunnelModeChildSessionParams.ConfigRequestIpv6Address) configRequest)
                                .getPrefixLength(),
                        ipv6AddressLen);
            }
            if (configRequest instanceof TunnelModeChildSessionParams.ConfigRequestIpv4Address) {
                ipv4ConfigRequestPresent = true;
                assertEquals(
                        ((TunnelModeChildSessionParams.ConfigRequestIpv4Address) configRequest)
                                .getAddress(),
                        testAddressV4);
            }
        }
        assertTrue(ipv6ConfigRequestPresent);
        assertTrue(ipv4ConfigRequestPresent);
    }

    @Test
    public void testBringupTunnelFailWithInvalidSimState() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(IwlanError.SIM_NOT_READY_EXCEPTION);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        PersistableBundle bundle = new PersistableBundle();
        setupMockForGetConfig(bundle);

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(DEFAULT_SLOT_INDEX))
                .thenReturn(null);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
    }

    @Test
    public void testBringupTunnelFailWithInvalidNai() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(IwlanError.SIM_NOT_READY_EXCEPTION);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        PersistableBundle bundle = new PersistableBundle();
        setupMockForGetConfig(bundle);

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(DEFAULT_SLOT_INDEX))
                .thenReturn(mMockSubscriptionInfo)
                .thenReturn(null);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
    }

    @Test
    public void testCloseTunnelWithEpdgSelectionIncomplete() throws Exception {
        // Bring up tunnel
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelCallbackMetrics);
        assertTrue(ret);

        // close tunnel when ePDG selection is incomplete
        ret = mEpdgTunnelManager.closeTunnel(TEST_APN_NAME, false /*forceClose*/);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallbackMetrics, times(1))
                .onClosed(eq(TEST_APN_NAME), eq(null), eq(0), eq(0));
    }

    @Test
    public void testIgnoreSignalFromObsoleteCallback() throws Exception {
        int transactionId = 0;

        // testApnName with token 0
        setupTunnelBringup(TEST_APN_NAME, ++transactionId);
        mEpdgTunnelManager.setIsEpdgAddressSelected(true);

        IwlanError error = new IwlanError(mMockIkeException);
        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(TEST_APN_NAME), eq(error));

        mEpdgTunnelManager
                .getTmIkeSessionCallback(TEST_APN_NAME, 0 /* token */)
                .onClosedExceptionally(mMockIkeException);
        mTestLooper.dispatchAll();
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(TEST_APN_NAME), eq(error));
        assertNull(mEpdgTunnelManager.getTunnelConfigForApn(TEST_APN_NAME));

        // testApnName1 with token 1
        setupTunnelBringup(TEST_APN_NAME, ++transactionId);
        mEpdgTunnelManager.setIsEpdgAddressSelected(true);

        // signal from obsolete callback (token 0), ignore it
        reset(mMockIwlanTunnelCallback);
        mEpdgTunnelManager
                .getTmIkeSessionCallback(TEST_APN_NAME, 0 /* token */)
                .onClosedExceptionally(mMockIkeException);
        mTestLooper.dispatchAll();
        verify(mMockIwlanTunnelCallback, never()).onClosed(eq(TEST_APN_NAME), eq(error));
        assertNotNull(mEpdgTunnelManager.getTunnelConfigForApn(TEST_APN_NAME));

        // signals from active callback
        mEpdgTunnelManager
                .getTmIkeSessionCallback(TEST_APN_NAME, 1 /* token */)
                .onClosedExceptionally(mMockIkeException);
        mTestLooper.dispatchAll();
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(TEST_APN_NAME), eq(error));
        assertNull(mEpdgTunnelManager.getTunnelConfigForApn(TEST_APN_NAME));
    }

    private boolean testIsN1ModeSupported(int[] nrAvailability) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putIntArray(
                CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY, nrAvailability);

        setupMockForGetConfig(bundle);

        return mEpdgTunnelManager.isN1ModeSupported();
    }

    @Test
    public void testIsN1ModeSupportedTrue() throws Exception {
        assertTrue(
                testIsN1ModeSupported(
                        new int[] {
                            CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                            CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA
                        }));
    }

    @Test
    public void testIsN1ModeSupportedFalse() throws Exception {
        assertFalse(
                testIsN1ModeSupported(
                        new int[] {CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA}));
    }
}

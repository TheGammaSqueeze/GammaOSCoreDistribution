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

package com.android.internal.net.ipsec.test.ike.net;

import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_FORCE_PORT_4500;

import static com.android.internal.net.ipsec.test.ike.net.IkeConnectionController.NAT_TRAVERSAL_SUPPORT_NOT_CHECKED;
import static com.android.internal.net.ipsec.test.ike.net.IkeConnectionController.NAT_TRAVERSAL_UNSUPPORTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.ipsec.test.ike.IkeSessionParams;
import android.net.ipsec.test.ike.exceptions.IkeIOException;
import android.net.ipsec.test.ike.exceptions.IkeInternalException;
import android.os.Looper;

import com.android.internal.net.ipsec.test.ike.IkeContext;
import com.android.internal.net.ipsec.test.ike.IkeSessionTestBase;
import com.android.internal.net.ipsec.test.ike.IkeSocket;
import com.android.internal.net.ipsec.test.ike.IkeUdp4Socket;
import com.android.internal.net.ipsec.test.ike.IkeUdp6Socket;
import com.android.internal.net.ipsec.test.ike.IkeUdp6WithEncapPortSocket;
import com.android.internal.net.ipsec.test.ike.IkeUdpEncapSocket;
import com.android.internal.net.ipsec.test.ike.SaRecord.IkeSaRecord;
import com.android.internal.net.ipsec.test.ike.keepalive.IkeNattKeepalive;
import com.android.internal.net.ipsec.test.ike.utils.IkeAlarm.IkeAlarmConfig;
import com.android.internal.net.ipsec.test.ike.utils.RandomnessFactory;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.util.HashSet;

public class IkeConnectionControllerTest extends IkeSessionTestBase {
    private static final long IKE_LOCAL_SPI = 11L;

    private IkeSessionParams mMockIkeParams;
    private IkeAlarmConfig mMockAlarmConfig;
    private IkeNattKeepalive mMockIkeNattKeepalive;
    private IkeConnectionController.Callback mMockConnectionCtrlCb;
    private IkeConnectionController.Dependencies mMockConnectionCtrlDeps;
    private Network mMockCallerConfiguredNetwork;
    private IkeSaRecord mMockIkeSaRecord;

    private IkeUdp4Socket mMockIkeUdp4Socket;
    private IkeUdp6Socket mMockIkeUdp6Socket;
    private IkeUdpEncapSocket mMockIkeUdpEncapSocket;
    private IkeUdp6WithEncapPortSocket mMockIkeUdp6WithEncapPortSocket;

    private IkeContext mIkeContext;
    private IkeConnectionController mIkeConnectionCtrl;

    private IkeConnectionController buildIkeConnectionCtrl() throws Exception {
        mMockConnectionCtrlCb = mock(IkeConnectionController.Callback.class);
        mMockConnectionCtrlDeps = mock(IkeConnectionController.Dependencies.class);

        when(mMockConnectionCtrlDeps.newIkeLocalAddressGenerator())
                .thenReturn(mMockIkeLocalAddressGenerator);
        when(mMockConnectionCtrlDeps.newIkeNattKeepalive(any(), any(), any(), any(), any(), any()))
                .thenReturn(mMockIkeNattKeepalive);

        when(mMockConnectionCtrlDeps.newIkeUdp4Socket(any(), any(), any()))
                .thenReturn(mMockIkeUdp4Socket);
        when(mMockConnectionCtrlDeps.newIkeUdp6Socket(any(), any(), any()))
                .thenReturn(mMockIkeUdp6Socket);
        when(mMockConnectionCtrlDeps.newIkeUdpEncapSocket(any(), any(), any(), any()))
                .thenReturn(mMockIkeUdpEncapSocket);
        when(mMockConnectionCtrlDeps.newIkeUdp6WithEncapPortSocket(any(), any(), any()))
                .thenReturn(mMockIkeUdp6WithEncapPortSocket);

        return new IkeConnectionController(
                mIkeContext,
                new IkeConnectionController.Config(
                        mMockIkeParams, mMockAlarmConfig, mMockConnectionCtrlCb),
                mMockConnectionCtrlDeps);
    }

    private IkeConnectionController buildIkeConnectionCtrlWithNetwork(Network callerConfiguredNw)
            throws Exception {
        when(mMockIkeParams.getConfiguredNetwork()).thenReturn(callerConfiguredNw);

        Network networkBeingUsed =
                callerConfiguredNw == null ? mMockDefaultNetwork : callerConfiguredNw;
        setupLocalAddressForNetwork(networkBeingUsed, LOCAL_ADDRESS);
        setupRemoteAddressForNetwork(networkBeingUsed, REMOTE_ADDRESS);

        return buildIkeConnectionCtrl();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mIkeContext =
                new IkeContext(mock(Looper.class), mSpyContext, mock(RandomnessFactory.class));
        mMockIkeParams = mock(IkeSessionParams.class);
        mMockAlarmConfig = mock(IkeAlarmConfig.class);
        mMockIkeNattKeepalive = mock(IkeNattKeepalive.class);
        mMockCallerConfiguredNetwork = mock(Network.class);
        mMockIkeSaRecord = mock(IkeSaRecord.class);

        mMockIkeUdp4Socket = newMockIkeSocket(IkeUdp4Socket.class);
        mMockIkeUdp6Socket = newMockIkeSocket(IkeUdp6Socket.class);
        mMockIkeUdpEncapSocket = newMockIkeSocket(IkeUdpEncapSocket.class);
        mMockIkeUdp6WithEncapPortSocket = newMockIkeSocket(IkeUdp6WithEncapPortSocket.class);

        when(mMockIkeParams.hasIkeOption(eq(IKE_OPTION_FORCE_PORT_4500))).thenReturn(false);
        when(mMockIkeParams.getServerHostname()).thenReturn(REMOTE_HOSTNAME);
        when(mMockIkeParams.getConfiguredNetwork()).thenReturn(null);

        setupLocalAddressForNetwork(mMockDefaultNetwork, LOCAL_ADDRESS);
        setupRemoteAddressForNetwork(mMockDefaultNetwork, REMOTE_ADDRESS);

        when(mMockIkeSaRecord.getLocalSpi()).thenReturn(IKE_LOCAL_SPI);

        mIkeConnectionCtrl = buildIkeConnectionCtrl();
        mIkeConnectionCtrl.setUp();
        mIkeConnectionCtrl.registerIkeSaRecord(mMockIkeSaRecord);
    }

    @After
    public void tearDown() throws Exception {
        mIkeConnectionCtrl.tearDown();
    }

    private void verifyKeepalive() {
        boolean isIkeUdpEncapSocket =
                mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket;
        if (isIkeUdpEncapSocket) {
            assertNotNull(mIkeConnectionCtrl.getIkeNattKeepalive());
        } else {
            assertNull(mIkeConnectionCtrl.getIkeNattKeepalive());
        }
    }

    private void verifySetup(
            Network expectedNetwork,
            InetAddress expectedLocalAddress,
            InetAddress expectedRemoteAddress,
            Class<? extends IkeSocket> socketType)
            throws Exception {
        assertEquals(expectedNetwork, mIkeConnectionCtrl.getNetwork());
        assertEquals(expectedLocalAddress, mIkeConnectionCtrl.getLocalAddress());
        assertEquals(expectedRemoteAddress, mIkeConnectionCtrl.getRemoteAddress());
        assertTrue(socketType.isInstance(mIkeConnectionCtrl.getIkeSocket()));
        assertEquals(NAT_TRAVERSAL_SUPPORT_NOT_CHECKED, mIkeConnectionCtrl.getNatStatus());
        verifyKeepalive();
    }

    private void verifyTearDown() {
        verify(mMockConnectManager).unregisterNetworkCallback(any(NetworkCallback.class));
        verify(mMockIkeUdp4Socket).releaseReference(mIkeConnectionCtrl);
    }

    private void verifySetupAndTeardownWithNw(Network callerConfiguredNw) throws Exception {
        mIkeConnectionCtrl.tearDown();

        // Clear the network callback registration call in #setUp()
        resetMockConnectManager();

        mIkeConnectionCtrl = buildIkeConnectionCtrlWithNetwork(callerConfiguredNw);
        mIkeConnectionCtrl.setUp();

        Network expectedNetwork =
                callerConfiguredNw == null ? mMockDefaultNetwork : callerConfiguredNw;

        if (callerConfiguredNw == null) {
            verify(mMockConnectManager)
                    .registerDefaultNetworkCallback(any(NetworkCallback.class), any());
        } else {
            verify(mMockConnectManager)
                    .registerNetworkCallback(any(), any(NetworkCallback.class), any());
        }

        verifySetup(expectedNetwork, LOCAL_ADDRESS, REMOTE_ADDRESS, IkeUdp4Socket.class);

        mIkeConnectionCtrl.tearDown();
        verifyTearDown();
    }

    private void verifyTearDownInSecondSetup(Network callerConfiguredNw) throws Exception {
        mIkeConnectionCtrl.tearDown();

        // Clear the network callback registration call in #setUp()
        resetMockConnectManager();

        mIkeConnectionCtrl = buildIkeConnectionCtrlWithNetwork(callerConfiguredNw);
        mIkeConnectionCtrl.setUp();
        mIkeConnectionCtrl.setUp();

        verifyTearDown();
    }

    private Class<? extends IkeSocket> getExpectedSocketType(boolean isIpv4, boolean force4500) {
        if (force4500) {
            if (isIpv4) {
                return IkeUdpEncapSocket.class;
            } else {
                return IkeUdp6WithEncapPortSocket.class;
            }
        } else {
            if (isIpv4) {
                return IkeUdp4Socket.class;
            } else {
                return IkeUdp6Socket.class;
            }
        }
    }

    private void verifySetupAndTeardownWithIpVersionAndPort(boolean isIpv4, boolean force4500)
            throws Exception {
        mIkeConnectionCtrl.tearDown();

        // Clear the network callback registration call in #setUp()
        resetMockConnectManager();

        when(mMockIkeParams.hasIkeOption(eq(IKE_OPTION_FORCE_PORT_4500))).thenReturn(force4500);

        InetAddress expectedLocalAddress = isIpv4 ? LOCAL_ADDRESS : LOCAL_ADDRESS_V6;
        InetAddress expectedRemoteAddress = isIpv4 ? REMOTE_ADDRESS : REMOTE_ADDRESS_V6;
        setupLocalAddressForNetwork(mMockDefaultNetwork, expectedLocalAddress);
        setupRemoteAddressForNetwork(mMockDefaultNetwork, expectedRemoteAddress);

        mIkeConnectionCtrl = buildIkeConnectionCtrl();
        mIkeConnectionCtrl.setUp();
        verifySetup(
                mMockDefaultNetwork,
                expectedLocalAddress,
                expectedRemoteAddress,
                getExpectedSocketType(isIpv4, force4500));

        mIkeConnectionCtrl.tearDown();
        verify(mMockConnectManager).unregisterNetworkCallback(any(NetworkCallback.class));
    }

    @Test
    public void testSetupAndTeardownWithDefaultNw() throws Exception {
        verifySetupAndTeardownWithNw(null /* callerConfiguredNw */);
    }

    @Test
    public void testSetupAndTeardownWithConfiguredNw() throws Exception {
        verifySetupAndTeardownWithNw(mMockCallerConfiguredNetwork);
    }

    @Test
    public void testTearDownInSecondSetupWithDefaultNw() throws Exception {
        verifyTearDownInSecondSetup(null /* callerConfiguredNw */);
    }

    @Test
    public void testTearDownInSecondSetupWithConfiguredNw() throws Exception {
        verifyTearDownInSecondSetup(mMockCallerConfiguredNetwork);
    }

    @Test
    public void testSetupAndTeardownIpv4Force4500() throws Exception {
        verifySetupAndTeardownWithIpVersionAndPort(true /* isIpv4 */, true /* force4500 */);
    }

    @Test
    public void testSetupAndTeardownIpv4NotForce4500() throws Exception {
        verifySetupAndTeardownWithIpVersionAndPort(true /* isIpv4 */, false /* force4500 */);
    }

    @Test
    public void testSetupAndTeardownIpv6Force4500() throws Exception {
        verifySetupAndTeardownWithIpVersionAndPort(false /* isIpv4 */, true /* force4500 */);
    }

    @Test
    public void testSetupAndTeardownIpv6NotForce4500() throws Exception {
        verifySetupAndTeardownWithIpVersionAndPort(false /* isIpv4 */, false /* force4500 */);
    }

    @Test
    public void testSendIkePacket() throws Exception {
        byte[] ikePacket = "testSendIkePacket".getBytes();
        mIkeConnectionCtrl.sendIkePacket(ikePacket);

        verify(mMockIkeUdp4Socket).sendIkePacket(eq(ikePacket), eq(REMOTE_ADDRESS));
    }

    @Test
    public void testRegisterAndUnregisterIkeSpi() throws Exception {
        // Clear invocation in setup
        reset(mMockIkeUdp4Socket);

        mIkeConnectionCtrl.registerIkeSpi(IKE_LOCAL_SPI);
        verify(mMockIkeUdp4Socket).registerIke(IKE_LOCAL_SPI, mIkeConnectionCtrl);

        mIkeConnectionCtrl.unregisterIkeSpi(IKE_LOCAL_SPI);
        verify(mMockIkeUdp4Socket).unregisterIke(IKE_LOCAL_SPI);
    }

    @Test
    public void testRegisterAndUnregisterIkeSaRecord() throws Exception {
        // Clear invocation in setup
        reset(mMockIkeUdp4Socket);
        mIkeConnectionCtrl.registerIkeSaRecord(mMockIkeSaRecord);
        verify(mMockIkeUdp4Socket).registerIke(IKE_LOCAL_SPI, mIkeConnectionCtrl);
        HashSet<IkeSaRecord> expectedSet = new HashSet<>();
        expectedSet.add(mMockIkeSaRecord);
        assertEquals(expectedSet, mIkeConnectionCtrl.getIkeSaRecords());

        mIkeConnectionCtrl.unregisterIkeSaRecord(mMockIkeSaRecord);
        verify(mMockIkeUdp4Socket).unregisterIke(IKE_LOCAL_SPI);
        assertTrue(mIkeConnectionCtrl.getIkeSaRecords().isEmpty());
    }

    @Test
    public void testMarkSeverNattUnsupported() throws Exception {
        mIkeConnectionCtrl.markSeverNattUnsupported();

        assertEquals(NAT_TRAVERSAL_UNSUPPORTED, mIkeConnectionCtrl.getNatStatus());
    }

    @Test
    public void testHandleNatDetectionResultInIkeInit() throws Exception {
        // Clear call in IkeConnectionController#setUp()
        reset(mMockConnectionCtrlCb);

        // Either NAT detected or not detected won't affect the test since both cases indicate
        // the server support NAT-T
        mIkeConnectionCtrl.handleNatDetectionResultInIkeInit(
                true /* isNatDetected */, IKE_LOCAL_SPI);

        assertTrue(mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket);
        verifyKeepalive();
    }

    private IkeDefaultNetworkCallback getDefaultNetworkCallback() throws Exception {
        ArgumentCaptor<IkeNetworkCallbackBase> networkCallbackCaptor =
                ArgumentCaptor.forClass(IkeNetworkCallbackBase.class);

        verify(mMockConnectManager)
                .registerDefaultNetworkCallback(networkCallbackCaptor.capture(), any());

        return (IkeDefaultNetworkCallback) networkCallbackCaptor.getValue();
    }

    @Test
    public void testNetworkLossWhenMobilityDisabled() throws Exception {
        getDefaultNetworkCallback().onLost(mMockDefaultNetwork);
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkDied(eq(mMockDefaultNetwork));
    }

    @Test
    public void testNetworkUpdateWhenMobilityDisabled() throws Exception {
        getDefaultNetworkCallback().onAvailable(mock(Network.class));
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkDied(eq(mMockDefaultNetwork));
    }

    @Test
    public void testLinkPropertiesUpdateWhenMobilityDisabled() throws Exception {
        LinkProperties linkProperties = new LinkProperties();
        linkProperties.addLinkAddress(mock(LinkAddress.class));
        getDefaultNetworkCallback().onLinkPropertiesChanged(mMockDefaultNetwork, linkProperties);
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkDied(eq(mMockDefaultNetwork));
    }

    private IkeNetworkCallbackBase enableMobilityAndReturnCb(boolean isDefaultNetwork)
            throws Exception {
        // Clear call in IkeConnectionController#setUp()
        reset(mMockConnectionCtrlCb);
        reset(mMockIkeNattKeepalive);

        mIkeConnectionCtrl.enableMobility();

        ArgumentCaptor<IkeNetworkCallbackBase> networkCallbackCaptor =
                ArgumentCaptor.forClass(IkeNetworkCallbackBase.class);

        if (isDefaultNetwork) {
            verify(mMockConnectManager)
                    .registerDefaultNetworkCallback(networkCallbackCaptor.capture(), any());
        } else {
            verify(mMockConnectManager)
                    .registerNetworkCallback(any(), networkCallbackCaptor.capture(), any());
        }

        return networkCallbackCaptor.getValue();
    }

    @Test
    public void testEnableMobilityWithDefaultNw() throws Exception {
        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(true /* isDefaultNetwork */);

        assertEquals(mMockDefaultNetwork, callback.getNetwork());
        assertEquals(LOCAL_ADDRESS, callback.getAddress());
    }

    @Test
    public void testEnableMobilityWithConfiguredNw() throws Exception {
        mIkeConnectionCtrl.tearDown();
        mIkeConnectionCtrl = buildIkeConnectionCtrlWithNetwork(mMockCallerConfiguredNetwork);
        mIkeConnectionCtrl.setUp();

        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(false /* isDefaultNetwork */);
        assertEquals(mMockCallerConfiguredNetwork, callback.getNetwork());
        assertEquals(LOCAL_ADDRESS, callback.getAddress());
    }

    @Test
    public void testEnableMobilityWithServerSupportNatt() throws Exception {
        mIkeConnectionCtrl.handleNatDetectionResultInIkeInit(
                true /* isNatDetected */, IKE_LOCAL_SPI);
        enableMobilityAndReturnCb(true /* isDefaultNetwork */);

        assertTrue(mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket);
        verifyKeepalive();
    }

    @Test
    public void testEnableMobilityWithServerNotSupportNatt() throws Exception {
        mIkeConnectionCtrl.markSeverNattUnsupported();
        enableMobilityAndReturnCb(true /* isDefaultNetwork */);

        assertTrue(mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdp4Socket);
        verifyKeepalive();
    }

    @Test
    public void handleNatDetectionResultInMobike() throws Exception {
        mIkeConnectionCtrl.handleNatDetectionResultInMobike(true /* isNatDetected */);

        assertTrue(mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket);
        verifyKeepalive();
    }

    private void verifyNetworkAndAddressesAfterMobilityEvent(
            Network expectedNetwork,
            InetAddress expectedLocalAddress,
            InetAddress expectedRemoteAddress,
            IkeNetworkCallbackBase callback) {
        assertEquals(expectedNetwork, mIkeConnectionCtrl.getNetwork());
        assertEquals(expectedLocalAddress, mIkeConnectionCtrl.getLocalAddress());
        assertEquals(expectedRemoteAddress, mIkeConnectionCtrl.getRemoteAddress());

        assertEquals(expectedNetwork, callback.getNetwork());
        assertEquals(expectedLocalAddress, callback.getAddress());
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedWithNewNetwork() throws Exception {
        Network newNetwork = mock(Network.class);
        setupLocalAddressForNetwork(newNetwork, UPDATED_LOCAL_ADDRESS);
        setupRemoteAddressForNetwork(newNetwork, REMOTE_ADDRESS);

        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(true /* isDefaultNetwork */);
        mIkeConnectionCtrl.onUnderlyingNetworkUpdated(newNetwork);

        verifyNetworkAndAddressesAfterMobilityEvent(
                newNetwork, UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS, callback);
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkUpdated();
        verify(mMockIkeSaRecord).migrate(UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedWithNewLp() throws Exception {
        reset(mMockDefaultNetwork);
        setupLocalAddressForNetwork(mMockDefaultNetwork, UPDATED_LOCAL_ADDRESS);
        setupRemoteAddressForNetwork(mMockDefaultNetwork, REMOTE_ADDRESS);

        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(true /* isDefaultNetwork */);
        mIkeConnectionCtrl.onUnderlyingNetworkUpdated(mMockDefaultNetwork);

        verifyNetworkAndAddressesAfterMobilityEvent(
                mMockDefaultNetwork, UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS, callback);
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkUpdated();
        verify(mMockIkeSaRecord).migrate(UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS);
    }

    // Test updating network from IPv4 network to IPv6 network
    private void verifyUnderlyingNetworkUpdated(
            boolean force4500,
            boolean doesPeerSupportNatt,
            Class<? extends IkeSocket> expectedSocketType)
            throws Exception {
        mIkeConnectionCtrl.tearDown();

        // Clear the network callback registration call in #setUp()
        resetMockConnectManager();

        // Set up mIkeConnectionCtrl for the test case
        when(mMockIkeParams.hasIkeOption(eq(IKE_OPTION_FORCE_PORT_4500))).thenReturn(force4500);

        mIkeConnectionCtrl = buildIkeConnectionCtrl();
        mIkeConnectionCtrl.setUp();
        mIkeConnectionCtrl.registerIkeSaRecord(mMockIkeSaRecord);
        if (doesPeerSupportNatt) {
            // Either NAT detected or not detected won't affect the test since both cases indicate
            // the server support NAT-T
            mIkeConnectionCtrl.handleNatDetectionResultInIkeInit(
                    true /* isNatDetected */, IKE_LOCAL_SPI);
        } else {
            mIkeConnectionCtrl.markSeverNattUnsupported();
        }

        // Update network from IPv4 network to IPv6 network
        Network newNetwork = mock(Network.class);
        setupLocalAddressForNetwork(newNetwork, UPDATED_LOCAL_ADDRESS_V6);
        setupRemoteAddressForNetwork(newNetwork, REMOTE_ADDRESS_V6);
        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(true /* isDefaultNetwork */);

        // Clear call in IkeConnectionController#setUp() and
        // IkeConnectionController#enableMobility()
        reset(mMockConnectionCtrlCb);
        mIkeConnectionCtrl.onUnderlyingNetworkUpdated(newNetwork);

        // Validation
        verifyNetworkAndAddressesAfterMobilityEvent(
                newNetwork, UPDATED_LOCAL_ADDRESS_V6, REMOTE_ADDRESS_V6, callback);
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkUpdated();
        verify(mMockIkeSaRecord).migrate(UPDATED_LOCAL_ADDRESS_V6, REMOTE_ADDRESS_V6);
        assertTrue(expectedSocketType.isInstance(mIkeConnectionCtrl.getIkeSocket()));
        verifyKeepalive();
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedForce4500NattSupported() throws Exception {
        verifyUnderlyingNetworkUpdated(
                true /* force4500 */,
                true /* doesPeerSupportNatt */,
                IkeUdp6WithEncapPortSocket.class);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedForce4500NattUnsupported() throws Exception {
        verifyUnderlyingNetworkUpdated(
                true /* force4500 */,
                false /* doesPeerSupportNatt */,
                IkeUdp6WithEncapPortSocket.class);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedNotForce4500NattSupported() throws Exception {
        verifyUnderlyingNetworkUpdated(
                false /* force4500 */,
                true /* doesPeerSupportNatt */,
                IkeUdp6WithEncapPortSocket.class);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedNOtForce4500NattUnsupported() throws Exception {
        verifyUnderlyingNetworkUpdated(
                false /* force4500 */, false /* doesPeerSupportNatt */, IkeUdp6Socket.class);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedFail() throws Exception {
        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(true /* isDefaultNetwork */);
        mIkeConnectionCtrl.onUnderlyingNetworkUpdated(mock(Network.class));

        // Expected to fail due to DNS resolution failure
        if (SdkLevel.isAtLeastT()) {
            verify(mMockConnectionCtrlCb).onError(any(IkeIOException.class));
        } else {
            verify(mMockConnectionCtrlCb).onError(any(IkeInternalException.class));
        }
    }

    @Test
    public void testOnUnderlyingNetworkDied() throws Exception {
        mIkeConnectionCtrl.onUnderlyingNetworkDied();
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkDied(eq(mMockDefaultNetwork));
    }
}

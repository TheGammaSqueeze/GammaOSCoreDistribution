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

package com.android.server.ethernet;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.EthernetNetworkManagementException;
import android.net.EthernetNetworkSpecifier;
import android.net.INetworkInterfaceOutcomeReceiver;
import android.net.IpConfiguration;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.NetworkProvider.NetworkOfferCallback;
import android.net.NetworkRequest;
import android.net.StaticIpConfiguration;
import android.net.ip.IpClientCallbacks;
import android.net.ip.IpClientManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.InterfaceParams;
import com.android.testutils.DevSdkIgnoreRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class EthernetNetworkFactoryTest {
    private static final int TIMEOUT_MS = 2_000;
    private static final String TEST_IFACE = "test123";
    private static final INetworkInterfaceOutcomeReceiver NULL_LISTENER = null;
    private static final String IP_ADDR = "192.0.2.2/25";
    private static final LinkAddress LINK_ADDR = new LinkAddress(IP_ADDR);
    private static final String HW_ADDR = "01:02:03:04:05:06";
    private TestLooper mLooper;
    private Handler mHandler;
    private EthernetNetworkFactory mNetFactory = null;
    private IpClientCallbacks mIpClientCallbacks;
    private NetworkOfferCallback mNetworkOfferCallback;
    private NetworkRequest mRequestToKeepNetworkUp;
    @Mock private Context mContext;
    @Mock private Resources mResources;
    @Mock private EthernetNetworkFactory.Dependencies mDeps;
    @Mock private IpClientManager mIpClient;
    @Mock private EthernetNetworkAgent mNetworkAgent;
    @Mock private InterfaceParams mInterfaceParams;
    @Mock private Network mMockNetwork;
    @Mock private NetworkProvider mNetworkProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        setupNetworkAgentMock();
        setupIpClientMock();
        setupContext();
    }

    //TODO: Move away from usage of TestLooper in order to move this logic back into @Before.
    private void initEthernetNetworkFactory() {
        mLooper = new TestLooper();
        mHandler = new Handler(mLooper.getLooper());
        mNetFactory = new EthernetNetworkFactory(mHandler, mContext, mNetworkProvider, mDeps);
    }

    private void setupNetworkAgentMock() {
        when(mDeps.makeEthernetNetworkAgent(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(new AnswerWithArguments() {
                                       public EthernetNetworkAgent answer(
                                               Context context,
                                               Looper looper,
                                               NetworkCapabilities nc,
                                               LinkProperties lp,
                                               NetworkAgentConfig config,
                                               NetworkProvider provider,
                                               EthernetNetworkAgent.Callbacks cb) {
                                           when(mNetworkAgent.getCallbacks()).thenReturn(cb);
                                           when(mNetworkAgent.getNetwork())
                                                   .thenReturn(mMockNetwork);
                                           return mNetworkAgent;
                                       }
                                   }
        );
    }

    private void setupIpClientMock() throws Exception {
        doAnswer(inv -> {
            // these tests only support one concurrent IpClient, so make sure we do not accidentally
            // create a mess.
            assertNull("An IpClient has already been created.", mIpClientCallbacks);

            mIpClientCallbacks = inv.getArgument(2);
            mIpClientCallbacks.onIpClientCreated(null);
            mLooper.dispatchAll();
            return null;
        }).when(mDeps).makeIpClient(any(Context.class), anyString(), any());

        doAnswer(inv -> {
            mIpClientCallbacks.onQuit();
            mLooper.dispatchAll();
            mIpClientCallbacks = null;
            return null;
        }).when(mIpClient).shutdown();

        when(mDeps.makeIpClientManager(any())).thenReturn(mIpClient);
    }

    private void triggerOnProvisioningSuccess() {
        mIpClientCallbacks.onProvisioningSuccess(new LinkProperties());
        mLooper.dispatchAll();
    }

    private void triggerOnProvisioningFailure() {
        mIpClientCallbacks.onProvisioningFailure(new LinkProperties());
        mLooper.dispatchAll();
    }

    private void triggerOnReachabilityLost() {
        mIpClientCallbacks.onReachabilityLost("ReachabilityLost");
        mLooper.dispatchAll();
    }

    private void setupContext() {
        when(mDeps.getTcpBufferSizesFromResource(eq(mContext))).thenReturn("");
    }

    @After
    public void tearDown() {
        // looper is shared with the network agents, so there may still be messages to dispatch on
        // tear down.
        mLooper.dispatchAll();
    }

    private NetworkCapabilities createDefaultFilterCaps() {
        return NetworkCapabilities.Builder.withoutDefaultCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build();
    }

    private NetworkCapabilities.Builder createInterfaceCapsBuilder(final int transportType) {
        return new NetworkCapabilities.Builder()
                .addTransportType(transportType)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
    }

    private NetworkRequest.Builder createDefaultRequestBuilder() {
        return new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private NetworkRequest createDefaultRequest() {
        return createDefaultRequestBuilder().build();
    }

    private IpConfiguration createDefaultIpConfig() {
        IpConfiguration ipConfig = new IpConfiguration();
        ipConfig.setIpAssignment(IpConfiguration.IpAssignment.DHCP);
        ipConfig.setProxySettings(IpConfiguration.ProxySettings.NONE);
        return ipConfig;
    }

    /**
     * Create an {@link IpConfiguration} with an associated {@link StaticIpConfiguration}.
     *
     * @return {@link IpConfiguration} with its {@link StaticIpConfiguration} set.
     */
    private IpConfiguration createStaticIpConfig() {
        final IpConfiguration ipConfig = new IpConfiguration();
        ipConfig.setIpAssignment(IpConfiguration.IpAssignment.STATIC);
        ipConfig.setStaticIpConfiguration(
                new StaticIpConfiguration.Builder().setIpAddress(LINK_ADDR).build());
        return ipConfig;
    }

    // creates an interface with provisioning in progress (since updating the interface link state
    // automatically starts the provisioning process)
    private void createInterfaceUndergoingProvisioning(String iface) {
        // Default to the ethernet transport type.
        createInterfaceUndergoingProvisioning(iface, NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    private void createInterfaceUndergoingProvisioning(
            @NonNull final String iface, final int transportType) {
        final IpConfiguration ipConfig = createDefaultIpConfig();
        mNetFactory.addInterface(iface, HW_ADDR, ipConfig,
                createInterfaceCapsBuilder(transportType).build());
        assertTrue(mNetFactory.updateInterfaceLinkState(iface, true, NULL_LISTENER));

        ArgumentCaptor<NetworkOfferCallback> captor = ArgumentCaptor.forClass(
                NetworkOfferCallback.class);
        verify(mNetworkProvider).registerNetworkOffer(any(), any(), any(), captor.capture());
        mRequestToKeepNetworkUp = createDefaultRequest();
        mNetworkOfferCallback = captor.getValue();
        mNetworkOfferCallback.onNetworkNeeded(mRequestToKeepNetworkUp);

        verifyStart(ipConfig);
        clearInvocations(mDeps);
        clearInvocations(mIpClient);
        clearInvocations(mNetworkProvider);
    }

    // creates a provisioned interface
    private void createAndVerifyProvisionedInterface(String iface) throws Exception {
        // Default to the ethernet transport type.
        createAndVerifyProvisionedInterface(iface, NetworkCapabilities.TRANSPORT_ETHERNET,
                ConnectivityManager.TYPE_ETHERNET);
    }

    private void createVerifyAndRemoveProvisionedInterface(final int transportType,
            final int expectedLegacyType) throws Exception {
        createAndVerifyProvisionedInterface(TEST_IFACE, transportType,
                expectedLegacyType);
        mNetFactory.removeInterface(TEST_IFACE);
    }

    private void createAndVerifyProvisionedInterface(
            @NonNull final String iface, final int transportType, final int expectedLegacyType)
            throws Exception {
        createInterfaceUndergoingProvisioning(iface, transportType);
        triggerOnProvisioningSuccess();
        // provisioning succeeded, verify that the network agent is created, registered, marked
        // as connected and legacy type are correctly set.
        final ArgumentCaptor<NetworkCapabilities> ncCaptor = ArgumentCaptor.forClass(
                NetworkCapabilities.class);
        verify(mDeps).makeEthernetNetworkAgent(any(), any(), ncCaptor.capture(), any(),
                argThat(x -> x.getLegacyType() == expectedLegacyType), any(), any());
        assertEquals(
                new EthernetNetworkSpecifier(iface), ncCaptor.getValue().getNetworkSpecifier());
        verifyNetworkAgentRegistersAndConnects();
        clearInvocations(mDeps);
        clearInvocations(mNetworkAgent);
    }

    // creates an unprovisioned interface
    private void createUnprovisionedInterface(String iface) throws Exception {
        // To create an unprovisioned interface, provision and then "stop" it, i.e. stop its
        // NetworkAgent and IpClient. One way this can be done is by provisioning an interface and
        // then calling onNetworkUnwanted.
        mNetFactory.addInterface(iface, HW_ADDR, createDefaultIpConfig(),
                createInterfaceCapsBuilder(NetworkCapabilities.TRANSPORT_ETHERNET).build());
        assertTrue(mNetFactory.updateInterfaceLinkState(iface, true, NULL_LISTENER));

        clearInvocations(mIpClient);
        clearInvocations(mNetworkAgent);
    }

    @Test
    public void testUpdateInterfaceLinkStateForActiveProvisioningInterface() throws Exception {
        initEthernetNetworkFactory();
        createInterfaceUndergoingProvisioning(TEST_IFACE);
        final TestNetworkManagementListener listener = new TestNetworkManagementListener();

        // verify that the IpClient gets shut down when interface state changes to down.
        final boolean ret =
                mNetFactory.updateInterfaceLinkState(TEST_IFACE, false /* up */, listener);

        assertTrue(ret);
        verify(mIpClient).shutdown();
        assertEquals(listener.expectOnResult(), TEST_IFACE);
    }

    @Test
    public void testUpdateInterfaceLinkStateForProvisionedInterface() throws Exception {
        initEthernetNetworkFactory();
        createAndVerifyProvisionedInterface(TEST_IFACE);
        final TestNetworkManagementListener listenerDown = new TestNetworkManagementListener();
        final TestNetworkManagementListener listenerUp = new TestNetworkManagementListener();

        final boolean retDown =
                mNetFactory.updateInterfaceLinkState(TEST_IFACE, false /* up */, listenerDown);

        assertTrue(retDown);
        verifyStop();
        assertEquals(listenerDown.expectOnResult(), TEST_IFACE);

        final boolean retUp =
                mNetFactory.updateInterfaceLinkState(TEST_IFACE, true /* up */, listenerUp);

        assertTrue(retUp);
        assertEquals(listenerUp.expectOnResult(), TEST_IFACE);
    }

    @Test
    public void testUpdateInterfaceLinkStateForUnprovisionedInterface() throws Exception {
        initEthernetNetworkFactory();
        createUnprovisionedInterface(TEST_IFACE);
        final TestNetworkManagementListener listener = new TestNetworkManagementListener();

        final boolean ret =
                mNetFactory.updateInterfaceLinkState(TEST_IFACE, false /* up */, listener);

        assertTrue(ret);
        // There should not be an active IPClient or NetworkAgent.
        verify(mDeps, never()).makeIpClient(any(), any(), any());
        verify(mDeps, never())
                .makeEthernetNetworkAgent(any(), any(), any(), any(), any(), any(), any());
        assertEquals(listener.expectOnResult(), TEST_IFACE);
    }

    @Test
    public void testUpdateInterfaceLinkStateForNonExistingInterface() throws Exception {
        initEthernetNetworkFactory();
        final TestNetworkManagementListener listener = new TestNetworkManagementListener();

        // if interface was never added, link state cannot be updated.
        final boolean ret =
                mNetFactory.updateInterfaceLinkState(TEST_IFACE, true /* up */, listener);

        assertFalse(ret);
        verifyNoStopOrStart();
        listener.expectOnError();
    }

    @Test
    public void testUpdateInterfaceLinkStateWithNoChanges() throws Exception {
        initEthernetNetworkFactory();
        createAndVerifyProvisionedInterface(TEST_IFACE);
        final TestNetworkManagementListener listener = new TestNetworkManagementListener();

        final boolean ret =
                mNetFactory.updateInterfaceLinkState(TEST_IFACE, true /* up */, listener);

        assertFalse(ret);
        verifyNoStopOrStart();
        listener.expectOnError();
    }

    @Test
    public void testProvisioningLoss() throws Exception {
        initEthernetNetworkFactory();
        when(mDeps.getNetworkInterfaceByName(TEST_IFACE)).thenReturn(mInterfaceParams);
        createAndVerifyProvisionedInterface(TEST_IFACE);

        triggerOnProvisioningFailure();
        verifyStop();
        // provisioning loss should trigger a retry, since the interface is still there
        verify(mIpClient).startProvisioning(any());
    }

    @Test
    public void testProvisioningLossForDisappearedInterface() throws Exception {
        initEthernetNetworkFactory();
        // mocked method returns null by default, but just to be explicit in the test:
        when(mDeps.getNetworkInterfaceByName(eq(TEST_IFACE))).thenReturn(null);

        createAndVerifyProvisionedInterface(TEST_IFACE);
        triggerOnProvisioningFailure();

        // the interface disappeared and getNetworkInterfaceByName returns null, we should not retry
        verify(mIpClient, never()).startProvisioning(any());
        verifyNoStopOrStart();
    }

    private void verifyNoStopOrStart() {
        verify(mNetworkAgent, never()).register();
        verify(mIpClient, never()).shutdown();
        verify(mNetworkAgent, never()).unregister();
        verify(mIpClient, never()).startProvisioning(any());
    }

    @Test
    public void testLinkPropertiesChanged() throws Exception {
        initEthernetNetworkFactory();
        createAndVerifyProvisionedInterface(TEST_IFACE);

        LinkProperties lp = new LinkProperties();
        mIpClientCallbacks.onLinkPropertiesChange(lp);
        mLooper.dispatchAll();
        verify(mNetworkAgent).sendLinkPropertiesImpl(same(lp));
    }

    @Test
    public void testNetworkUnwanted() throws Exception {
        initEthernetNetworkFactory();
        createAndVerifyProvisionedInterface(TEST_IFACE);

        mNetworkAgent.getCallbacks().onNetworkUnwanted();
        mLooper.dispatchAll();
        verifyStop();
    }

    @Test
    public void testNetworkUnwantedWithStaleNetworkAgent() throws Exception {
        initEthernetNetworkFactory();
        // ensures provisioning is restarted after provisioning loss
        when(mDeps.getNetworkInterfaceByName(TEST_IFACE)).thenReturn(mInterfaceParams);
        createAndVerifyProvisionedInterface(TEST_IFACE);

        EthernetNetworkAgent.Callbacks oldCbs = mNetworkAgent.getCallbacks();
        // replace network agent in EthernetNetworkFactory
        // Loss of provisioning will restart the ip client and network agent.
        triggerOnProvisioningFailure();
        verify(mDeps).makeIpClient(any(), any(), any());

        triggerOnProvisioningSuccess();
        verify(mDeps).makeEthernetNetworkAgent(any(), any(), any(), any(), any(), any(), any());

        // verify that unwanted is ignored
        clearInvocations(mIpClient);
        clearInvocations(mNetworkAgent);
        oldCbs.onNetworkUnwanted();
        verify(mIpClient, never()).shutdown();
        verify(mNetworkAgent, never()).unregister();
    }

    @Test
    public void testTransportOverrideIsCorrectlySet() throws Exception {
        initEthernetNetworkFactory();
        // createProvisionedInterface() has verifications in place for transport override
        // functionality which for EthernetNetworkFactory is network score and legacy type mappings.
        createVerifyAndRemoveProvisionedInterface(NetworkCapabilities.TRANSPORT_ETHERNET,
                ConnectivityManager.TYPE_ETHERNET);
        createVerifyAndRemoveProvisionedInterface(NetworkCapabilities.TRANSPORT_BLUETOOTH,
                ConnectivityManager.TYPE_BLUETOOTH);
        createVerifyAndRemoveProvisionedInterface(NetworkCapabilities.TRANSPORT_WIFI,
                ConnectivityManager.TYPE_WIFI);
        createVerifyAndRemoveProvisionedInterface(NetworkCapabilities.TRANSPORT_CELLULAR,
                ConnectivityManager.TYPE_MOBILE);
        createVerifyAndRemoveProvisionedInterface(NetworkCapabilities.TRANSPORT_LOWPAN,
                ConnectivityManager.TYPE_NONE);
        createVerifyAndRemoveProvisionedInterface(NetworkCapabilities.TRANSPORT_WIFI_AWARE,
                ConnectivityManager.TYPE_NONE);
        createVerifyAndRemoveProvisionedInterface(NetworkCapabilities.TRANSPORT_TEST,
                ConnectivityManager.TYPE_NONE);
    }

    @Test
    public void testReachabilityLoss() throws Exception {
        initEthernetNetworkFactory();
        createAndVerifyProvisionedInterface(TEST_IFACE);

        triggerOnReachabilityLost();

        // Reachability loss should trigger a stop and start, since the interface is still there
        verifyRestart(createDefaultIpConfig());
    }

    private IpClientCallbacks getStaleIpClientCallbacks() throws Exception {
        createAndVerifyProvisionedInterface(TEST_IFACE);
        final IpClientCallbacks staleIpClientCallbacks = mIpClientCallbacks;
        mNetFactory.removeInterface(TEST_IFACE);
        verifyStop();
        assertNotSame(mIpClientCallbacks, staleIpClientCallbacks);
        return staleIpClientCallbacks;
    }

    @Test
    public void testIgnoreOnIpLayerStartedCallbackForStaleCallback() throws Exception {
        initEthernetNetworkFactory();
        final IpClientCallbacks staleIpClientCallbacks = getStaleIpClientCallbacks();

        staleIpClientCallbacks.onProvisioningSuccess(new LinkProperties());
        mLooper.dispatchAll();

        verify(mIpClient, never()).startProvisioning(any());
        verify(mNetworkAgent, never()).register();
    }

    @Test
    public void testIgnoreOnIpLayerStoppedCallbackForStaleCallback() throws Exception {
        initEthernetNetworkFactory();
        when(mDeps.getNetworkInterfaceByName(TEST_IFACE)).thenReturn(mInterfaceParams);
        final IpClientCallbacks staleIpClientCallbacks = getStaleIpClientCallbacks();

        staleIpClientCallbacks.onProvisioningFailure(new LinkProperties());
        mLooper.dispatchAll();

        verify(mIpClient, never()).startProvisioning(any());
    }

    @Test
    public void testIgnoreLinkPropertiesCallbackForStaleCallback() throws Exception {
        initEthernetNetworkFactory();
        final IpClientCallbacks staleIpClientCallbacks = getStaleIpClientCallbacks();
        final LinkProperties lp = new LinkProperties();

        staleIpClientCallbacks.onLinkPropertiesChange(lp);
        mLooper.dispatchAll();

        verify(mNetworkAgent, never()).sendLinkPropertiesImpl(eq(lp));
    }

    @Test
    public void testIgnoreNeighborLossCallbackForStaleCallback() throws Exception {
        initEthernetNetworkFactory();
        final IpClientCallbacks staleIpClientCallbacks = getStaleIpClientCallbacks();

        staleIpClientCallbacks.onReachabilityLost("Neighbor Lost");
        mLooper.dispatchAll();

        verify(mIpClient, never()).startProvisioning(any());
        verify(mNetworkAgent, never()).register();
    }

    private void verifyRestart(@NonNull final IpConfiguration ipConfig) {
        verifyStop();
        verifyStart(ipConfig);
    }

    private void verifyStart(@NonNull final IpConfiguration ipConfig) {
        verify(mDeps).makeIpClient(any(Context.class), anyString(), any());
        verify(mIpClient).startProvisioning(
                argThat(x -> Objects.equals(x.mStaticIpConfig, ipConfig.getStaticIpConfiguration()))
        );
    }

    private void verifyStop() {
        verify(mIpClient).shutdown();
        verify(mNetworkAgent).unregister();
    }

    private void verifyNetworkAgentRegistersAndConnects() {
        verify(mNetworkAgent).register();
        verify(mNetworkAgent).markConnected();
    }

    private static final class TestNetworkManagementListener
            implements INetworkInterfaceOutcomeReceiver {
        private final CompletableFuture<String> mResult = new CompletableFuture<>();

        @Override
        public void onResult(@NonNull String iface) {
            mResult.complete(iface);
        }

        @Override
        public void onError(@NonNull EthernetNetworkManagementException exception) {
            mResult.completeExceptionally(exception);
        }

        String expectOnResult() throws Exception {
            return mResult.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        void expectOnError() throws Exception {
            assertThrows(EthernetNetworkManagementException.class, () -> {
                try {
                    mResult.get();
                } catch (ExecutionException e) {
                    throw e.getCause();
                }
            });
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }

    @Test
    public void testUpdateInterfaceCallsListenerCorrectlyOnSuccess() throws Exception {
        initEthernetNetworkFactory();
        createAndVerifyProvisionedInterface(TEST_IFACE);
        final NetworkCapabilities capabilities = createDefaultFilterCaps();
        final IpConfiguration ipConfiguration = createStaticIpConfig();
        final TestNetworkManagementListener listener = new TestNetworkManagementListener();

        mNetFactory.updateInterface(TEST_IFACE, ipConfiguration, capabilities, listener);
        triggerOnProvisioningSuccess();

        assertEquals(listener.expectOnResult(), TEST_IFACE);
    }

    @DevSdkIgnoreRule.IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
    @Test
    public void testUpdateInterfaceAbortsOnConcurrentRemoveInterface() throws Exception {
        initEthernetNetworkFactory();
        verifyNetworkManagementCallIsAbortedWhenInterrupted(
                TEST_IFACE,
                () -> mNetFactory.removeInterface(TEST_IFACE));
    }

    @DevSdkIgnoreRule.IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
    @Test
    public void testUpdateInterfaceAbortsOnConcurrentUpdateInterfaceLinkState() throws Exception {
        initEthernetNetworkFactory();
        verifyNetworkManagementCallIsAbortedWhenInterrupted(
                TEST_IFACE,
                () -> mNetFactory.updateInterfaceLinkState(TEST_IFACE, false, NULL_LISTENER));
    }

    @DevSdkIgnoreRule.IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
    @Test
    public void testUpdateInterfaceAbortsOnNetworkUneededRemovesAllRequests() throws Exception {
        initEthernetNetworkFactory();
        verifyNetworkManagementCallIsAbortedWhenInterrupted(
                TEST_IFACE,
                () -> mNetworkOfferCallback.onNetworkUnneeded(mRequestToKeepNetworkUp));
    }

    @Test
    public void testUpdateInterfaceCallsListenerCorrectlyOnConcurrentRequests() throws Exception {
        initEthernetNetworkFactory();
        final NetworkCapabilities capabilities = createDefaultFilterCaps();
        final IpConfiguration ipConfiguration = createStaticIpConfig();
        final TestNetworkManagementListener successfulListener =
                new TestNetworkManagementListener();

        // If two calls come in before the first one completes, the first listener will be aborted
        // and the second one will be successful.
        verifyNetworkManagementCallIsAbortedWhenInterrupted(
                TEST_IFACE,
                () -> {
                    mNetFactory.updateInterface(
                            TEST_IFACE, ipConfiguration, capabilities, successfulListener);
                    triggerOnProvisioningSuccess();
                });

        assertEquals(successfulListener.expectOnResult(), TEST_IFACE);
    }

    private void verifyNetworkManagementCallIsAbortedWhenInterrupted(
            @NonNull final String iface,
            @NonNull final Runnable interruptingRunnable) throws Exception {
        createAndVerifyProvisionedInterface(iface);
        final NetworkCapabilities capabilities = createDefaultFilterCaps();
        final IpConfiguration ipConfiguration = createStaticIpConfig();
        final TestNetworkManagementListener failedListener = new TestNetworkManagementListener();

        // An active update request will be aborted on interrupt prior to provisioning completion.
        mNetFactory.updateInterface(iface, ipConfiguration, capabilities, failedListener);
        interruptingRunnable.run();

        failedListener.expectOnError();
    }

    @Test
    public void testUpdateInterfaceRestartsAgentCorrectly() throws Exception {
        initEthernetNetworkFactory();
        createAndVerifyProvisionedInterface(TEST_IFACE);
        final NetworkCapabilities capabilities = createDefaultFilterCaps();
        final IpConfiguration ipConfiguration = createStaticIpConfig();
        final TestNetworkManagementListener listener = new TestNetworkManagementListener();

        mNetFactory.updateInterface(TEST_IFACE, ipConfiguration, capabilities, listener);
        triggerOnProvisioningSuccess();

        assertEquals(listener.expectOnResult(), TEST_IFACE);
        verify(mDeps).makeEthernetNetworkAgent(any(), any(),
                eq(capabilities), any(), any(), any(), any());
        verifyRestart(ipConfiguration);
    }

    @Test
    public void testUpdateInterfaceForNonExistingInterface() throws Exception {
        initEthernetNetworkFactory();
        // No interface exists due to not calling createAndVerifyProvisionedInterface(...).
        final NetworkCapabilities capabilities = createDefaultFilterCaps();
        final IpConfiguration ipConfiguration = createStaticIpConfig();
        final TestNetworkManagementListener listener = new TestNetworkManagementListener();

        mNetFactory.updateInterface(TEST_IFACE, ipConfiguration, capabilities, listener);

        verifyNoStopOrStart();
        listener.expectOnError();
    }

    @Test
    public void testUpdateInterfaceWithNullIpConfiguration() throws Exception {
        initEthernetNetworkFactory();
        createAndVerifyProvisionedInterface(TEST_IFACE);

        final IpConfiguration initialIpConfig = createStaticIpConfig();
        mNetFactory.updateInterface(TEST_IFACE, initialIpConfig, null /*capabilities*/,
                null /*listener*/);
        triggerOnProvisioningSuccess();
        verifyRestart(initialIpConfig);

        // TODO: have verifyXyz functions clear invocations.
        clearInvocations(mDeps);
        clearInvocations(mIpClient);
        clearInvocations(mNetworkAgent);


        // verify that sending a null ipConfig does not update the current ipConfig.
        mNetFactory.updateInterface(TEST_IFACE, null /*ipConfig*/, null /*capabilities*/,
                null /*listener*/);
        triggerOnProvisioningSuccess();
        verifyRestart(initialIpConfig);
    }
}

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

package com.android.server;

import static libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import static libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.compat.testing.PlatformCompatChangeRule;
import android.content.ContentResolver;
import android.content.Context;
import android.net.INetd;
import android.net.InetAddresses;
import android.net.mdns.aidl.DiscoveryInfo;
import android.net.mdns.aidl.GetAddressInfo;
import android.net.mdns.aidl.IMDnsEventListener;
import android.net.mdns.aidl.ResolutionInfo;
import android.net.nsd.INsdManagerCallback;
import android.net.nsd.INsdServiceConnector;
import android.net.nsd.MDnsManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.HandlerUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedList;
import java.util.Queue;

// TODOs:
//  - test client can send requests and receive replies
//  - test NSD_ON ENABLE/DISABLED listening
@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
public class NsdServiceTest {

    static final int PROTOCOL = NsdManager.PROTOCOL_DNS_SD;
    private static final long CLEANUP_DELAY_MS = 500;
    private static final long TIMEOUT_MS = 500;

    // Records INsdManagerCallback created when NsdService#connect is called.
    // Only accessed on the test thread, since NsdService#connect is called by the NsdManager
    // constructor called on the test thread.
    private final Queue<INsdManagerCallback> mCreatedCallbacks = new LinkedList<>();

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();
    @Mock Context mContext;
    @Mock ContentResolver mResolver;
    @Mock MDnsManager mMockMDnsM;
    HandlerThread mThread;
    TestHandler mHandler;

    private static class LinkToDeathRecorder extends Binder {
        IBinder.DeathRecipient mDr;

        @Override
        public void linkToDeath(@NonNull DeathRecipient recipient, int flags) {
            super.linkToDeath(recipient, flags);
            mDr = recipient;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mThread = new HandlerThread("mock-service-handler");
        mThread.start();
        mHandler = new TestHandler(mThread.getLooper());
        when(mContext.getContentResolver()).thenReturn(mResolver);
        doReturn(MDnsManager.MDNS_SERVICE).when(mContext)
                .getSystemServiceName(MDnsManager.class);
        doReturn(mMockMDnsM).when(mContext).getSystemService(MDnsManager.MDNS_SERVICE);
        doReturn(true).when(mMockMDnsM).registerService(
                anyInt(), anyString(), anyString(), anyInt(), any(), anyInt());
        doReturn(true).when(mMockMDnsM).stopOperation(anyInt());
        doReturn(true).when(mMockMDnsM).discover(anyInt(), anyString(), anyInt());
        doReturn(true).when(mMockMDnsM).resolve(
                anyInt(), anyString(), anyString(), anyString(), anyInt());
    }

    @After
    public void tearDown() throws Exception {
        if (mThread != null) {
            mThread.quit();
            mThread = null;
        }
    }

    @Test
    @DisableCompatChanges(NsdManager.RUN_NATIVE_NSD_ONLY_IF_LEGACY_APPS)
    public void testPreSClients() throws Exception {
        NsdService service = makeService();

        // Pre S client connected, the daemon should be started.
        connectClient(service);
        waitForIdle();
        final INsdManagerCallback cb1 = getCallback();
        final IBinder.DeathRecipient deathRecipient1 = verifyLinkToDeath(cb1);
        verify(mMockMDnsM, times(1)).registerEventListener(any());
        verify(mMockMDnsM, times(1)).startDaemon();

        connectClient(service);
        waitForIdle();
        final INsdManagerCallback cb2 = getCallback();
        final IBinder.DeathRecipient deathRecipient2 = verifyLinkToDeath(cb2);
        // Daemon has been started, it should not try to start it again.
        verify(mMockMDnsM, times(1)).registerEventListener(any());
        verify(mMockMDnsM, times(1)).startDaemon();

        deathRecipient1.binderDied();
        // Still 1 client remains, daemon shouldn't be stopped.
        waitForIdle();
        verify(mMockMDnsM, never()).stopDaemon();

        deathRecipient2.binderDied();
        // All clients are disconnected, the daemon should be stopped.
        verifyDelayMaybeStopDaemon(CLEANUP_DELAY_MS);
    }

    @Test
    @EnableCompatChanges(NsdManager.RUN_NATIVE_NSD_ONLY_IF_LEGACY_APPS)
    public void testNoDaemonStartedWhenClientsConnect() throws Exception {
        final NsdService service = makeService();

        // Creating an NsdManager will not cause daemon startup.
        connectClient(service);
        waitForIdle();
        verify(mMockMDnsM, never()).registerEventListener(any());
        verify(mMockMDnsM, never()).startDaemon();
        final INsdManagerCallback cb1 = getCallback();
        final IBinder.DeathRecipient deathRecipient1 = verifyLinkToDeath(cb1);

        // Creating another NsdManager will not cause daemon startup either.
        connectClient(service);
        waitForIdle();
        verify(mMockMDnsM, never()).registerEventListener(any());
        verify(mMockMDnsM, never()).startDaemon();
        final INsdManagerCallback cb2 = getCallback();
        final IBinder.DeathRecipient deathRecipient2 = verifyLinkToDeath(cb2);

        // If there is no active request, try to clean up the daemon but should not do it because
        // daemon has not been started.
        deathRecipient1.binderDied();
        verify(mMockMDnsM, never()).unregisterEventListener(any());
        verify(mMockMDnsM, never()).stopDaemon();
        deathRecipient2.binderDied();
        verify(mMockMDnsM, never()).unregisterEventListener(any());
        verify(mMockMDnsM, never()).stopDaemon();
    }

    private IBinder.DeathRecipient verifyLinkToDeath(INsdManagerCallback cb)
            throws Exception {
        final IBinder.DeathRecipient dr = ((LinkToDeathRecorder) cb.asBinder()).mDr;
        assertNotNull(dr);
        return dr;
    }

    @Test
    @EnableCompatChanges(NsdManager.RUN_NATIVE_NSD_ONLY_IF_LEGACY_APPS)
    public void testClientRequestsAreGCedAtDisconnection() throws Exception {
        NsdService service = makeService();

        NsdManager client = connectClient(service);
        waitForIdle();
        final INsdManagerCallback cb1 = getCallback();
        final IBinder.DeathRecipient deathRecipient = verifyLinkToDeath(cb1);
        verify(mMockMDnsM, never()).registerEventListener(any());
        verify(mMockMDnsM, never()).startDaemon();

        NsdServiceInfo request = new NsdServiceInfo("a_name", "a_type");
        request.setPort(2201);

        // Client registration request
        NsdManager.RegistrationListener listener1 = mock(NsdManager.RegistrationListener.class);
        client.registerService(request, PROTOCOL, listener1);
        waitForIdle();
        verify(mMockMDnsM, times(1)).registerEventListener(any());
        verify(mMockMDnsM, times(1)).startDaemon();
        verify(mMockMDnsM, times(1)).registerService(
                eq(2), eq("a_name"), eq("a_type"), eq(2201), any(), eq(0));

        // Client discovery request
        NsdManager.DiscoveryListener listener2 = mock(NsdManager.DiscoveryListener.class);
        client.discoverServices("a_type", PROTOCOL, listener2);
        waitForIdle();
        verify(mMockMDnsM, times(1)).discover(eq(3), eq("a_type"), eq(0));

        // Client resolve request
        NsdManager.ResolveListener listener3 = mock(NsdManager.ResolveListener.class);
        client.resolveService(request, listener3);
        waitForIdle();
        verify(mMockMDnsM, times(1)).resolve(
                eq(4), eq("a_name"), eq("a_type"), eq("local."), eq(0));

        // Client disconnects, stop the daemon after CLEANUP_DELAY_MS.
        deathRecipient.binderDied();
        verifyDelayMaybeStopDaemon(CLEANUP_DELAY_MS);
        // checks that request are cleaned
        verify(mMockMDnsM, times(1)).stopOperation(eq(2));
        verify(mMockMDnsM, times(1)).stopOperation(eq(3));
        verify(mMockMDnsM, times(1)).stopOperation(eq(4));
    }

    @Test
    @EnableCompatChanges(NsdManager.RUN_NATIVE_NSD_ONLY_IF_LEGACY_APPS)
    public void testCleanupDelayNoRequestActive() throws Exception {
        NsdService service = makeService();
        NsdManager client = connectClient(service);

        NsdServiceInfo request = new NsdServiceInfo("a_name", "a_type");
        request.setPort(2201);
        NsdManager.RegistrationListener listener1 = mock(NsdManager.RegistrationListener.class);
        client.registerService(request, PROTOCOL, listener1);
        waitForIdle();
        verify(mMockMDnsM, times(1)).registerEventListener(any());
        verify(mMockMDnsM, times(1)).startDaemon();
        final INsdManagerCallback cb1 = getCallback();
        final IBinder.DeathRecipient deathRecipient = verifyLinkToDeath(cb1);
        verify(mMockMDnsM, times(1)).registerService(
                eq(2), eq("a_name"), eq("a_type"), eq(2201), any(), eq(0));

        client.unregisterService(listener1);
        waitForIdle();
        verify(mMockMDnsM, times(1)).stopOperation(eq(2));

        verifyDelayMaybeStopDaemon(CLEANUP_DELAY_MS);
        reset(mMockMDnsM);
        deathRecipient.binderDied();
        // Client disconnects, daemon should not be stopped after CLEANUP_DELAY_MS.
        verify(mMockMDnsM, never()).unregisterEventListener(any());
        verify(mMockMDnsM, never()).stopDaemon();
    }

    @Test
    public void testDiscoverOnTetheringDownstream() throws Exception {
        NsdService service = makeService();
        NsdManager client = connectClient(service);

        final String serviceType = "a_type";
        final String serviceName = "a_name";
        final String domainName = "mytestdevice.local";
        final int interfaceIdx = 123;
        final NsdManager.DiscoveryListener discListener = mock(NsdManager.DiscoveryListener.class);
        client.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discListener);
        waitForIdle();

        final ArgumentCaptor<IMDnsEventListener> listenerCaptor =
                ArgumentCaptor.forClass(IMDnsEventListener.class);
        verify(mMockMDnsM).registerEventListener(listenerCaptor.capture());
        final ArgumentCaptor<Integer> discIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).discover(discIdCaptor.capture(), eq(serviceType),
                eq(0) /* interfaceIdx */);
        // NsdManager uses a separate HandlerThread to dispatch callbacks (on ServiceHandler), so
        // this needs to use a timeout
        verify(discListener, timeout(TIMEOUT_MS)).onDiscoveryStarted(serviceType);

        final DiscoveryInfo discoveryInfo = new DiscoveryInfo(
                discIdCaptor.getValue(),
                IMDnsEventListener.SERVICE_FOUND,
                serviceName,
                serviceType,
                domainName,
                interfaceIdx,
                INetd.LOCAL_NET_ID); // LOCAL_NET_ID (99) used on tethering downstreams
        final IMDnsEventListener eventListener = listenerCaptor.getValue();
        eventListener.onServiceDiscoveryStatus(discoveryInfo);
        waitForIdle();

        final ArgumentCaptor<NsdServiceInfo> discoveredInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        verify(discListener, timeout(TIMEOUT_MS)).onServiceFound(discoveredInfoCaptor.capture());
        final NsdServiceInfo foundInfo = discoveredInfoCaptor.getValue();
        assertEquals(serviceName, foundInfo.getServiceName());
        assertEquals(serviceType, foundInfo.getServiceType());
        assertNull(foundInfo.getHost());
        assertNull(foundInfo.getNetwork());
        assertEquals(interfaceIdx, foundInfo.getInterfaceIndex());

        // After discovering the service, verify resolving it
        final NsdManager.ResolveListener resolveListener = mock(NsdManager.ResolveListener.class);
        client.resolveService(foundInfo, resolveListener);
        waitForIdle();

        final ArgumentCaptor<Integer> resolvIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).resolve(resolvIdCaptor.capture(), eq(serviceName), eq(serviceType),
                eq("local.") /* domain */, eq(interfaceIdx));

        final int servicePort = 10123;
        final String serviceFullName = serviceName + "." + serviceType;
        final ResolutionInfo resolutionInfo = new ResolutionInfo(
                resolvIdCaptor.getValue(),
                IMDnsEventListener.SERVICE_RESOLVED,
                null /* serviceName */,
                null /* serviceType */,
                null /* domain */,
                serviceFullName,
                domainName,
                servicePort,
                new byte[0] /* txtRecord */,
                interfaceIdx);

        doReturn(true).when(mMockMDnsM).getServiceAddress(anyInt(), any(), anyInt());
        eventListener.onServiceResolutionStatus(resolutionInfo);
        waitForIdle();

        final ArgumentCaptor<Integer> getAddrIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockMDnsM).getServiceAddress(getAddrIdCaptor.capture(), eq(domainName),
                eq(interfaceIdx));

        final String serviceAddress = "192.0.2.123";
        final GetAddressInfo addressInfo = new GetAddressInfo(
                getAddrIdCaptor.getValue(),
                IMDnsEventListener.SERVICE_GET_ADDR_SUCCESS,
                serviceFullName,
                serviceAddress,
                interfaceIdx,
                INetd.LOCAL_NET_ID);
        eventListener.onGettingServiceAddressStatus(addressInfo);
        waitForIdle();

        final ArgumentCaptor<NsdServiceInfo> resInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        verify(resolveListener, timeout(TIMEOUT_MS)).onServiceResolved(resInfoCaptor.capture());
        final NsdServiceInfo resolvedService = resInfoCaptor.getValue();
        assertEquals(serviceName, resolvedService.getServiceName());
        assertEquals("." + serviceType, resolvedService.getServiceType());
        assertEquals(InetAddresses.parseNumericAddress(serviceAddress), resolvedService.getHost());
        assertEquals(servicePort, resolvedService.getPort());
        assertNull(resolvedService.getNetwork());
        assertEquals(interfaceIdx, resolvedService.getInterfaceIndex());
    }

    private void waitForIdle() {
        HandlerUtils.waitForIdle(mHandler, TIMEOUT_MS);
    }

    NsdService makeService() {
        final NsdService service = new NsdService(mContext, mHandler, CLEANUP_DELAY_MS) {
            @Override
            public INsdServiceConnector connect(INsdManagerCallback baseCb) {
                // Wrap the callback in a transparent mock, to mock asBinder returning a
                // LinkToDeathRecorder. This will allow recording the binder death recipient
                // registered on the callback. Use a transparent mock and not a spy as the actual
                // implementation class is not public and cannot be spied on by Mockito.
                final INsdManagerCallback cb = mock(INsdManagerCallback.class,
                        AdditionalAnswers.delegatesTo(baseCb));
                doReturn(new LinkToDeathRecorder()).when(cb).asBinder();
                mCreatedCallbacks.add(cb);
                return super.connect(cb);
            }
        };
        return service;
    }

    private INsdManagerCallback getCallback() {
        return mCreatedCallbacks.remove();
    }

    NsdManager connectClient(NsdService service) {
        return new NsdManager(mContext, service);
    }

    void verifyDelayMaybeStopDaemon(long cleanupDelayMs) throws Exception {
        waitForIdle();
        // Stop daemon shouldn't be called immediately.
        verify(mMockMDnsM, never()).unregisterEventListener(any());
        verify(mMockMDnsM, never()).stopDaemon();

        // Clean up the daemon after CLEANUP_DELAY_MS.
        verify(mMockMDnsM, timeout(cleanupDelayMs + TIMEOUT_MS)).unregisterEventListener(any());
        verify(mMockMDnsM, timeout(cleanupDelayMs + TIMEOUT_MS)).stopDaemon();
    }

    public static class TestHandler extends Handler {
        public Message lastMessage;

        TestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            lastMessage = obtainMessage();
            lastMessage.copyFrom(msg);
        }
    }
}

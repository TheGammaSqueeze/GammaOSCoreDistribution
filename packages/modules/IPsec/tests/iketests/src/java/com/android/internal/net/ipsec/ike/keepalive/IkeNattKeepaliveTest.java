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

package com.android.internal.net.ipsec.test.ike.keepalive;

import static android.net.SocketKeepalive.ERROR_INVALID_IP_ADDRESS;

import static com.android.internal.net.ipsec.test.ike.utils.IkeAlarm.IkeAlarmConfig;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IpSecManager.UdpEncapsulationSocket;
import android.net.Network;
import android.net.SocketKeepalive;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.Inet4Address;

public class IkeNattKeepaliveTest {
    private static final int KEEPALIVE_DELAY = 10;

    private ConnectivityManager mMockConnectManager;
    private IkeNattKeepalive.Dependencies mMockDeps;
    private SocketKeepalive mMockSocketKeepalive;
    private SoftwareKeepaliveImpl mMockSoftwareKeepalive;

    private IkeNattKeepalive mIkeNattKeepalive;

    private ArgumentCaptor<SocketKeepalive.Callback> mSocketKeepaliveCbCaptor =
            ArgumentCaptor.forClass(SocketKeepalive.Callback.class);
    private SocketKeepalive.Callback mSocketKeepaliveCb;

    @Before
    public void setUp() throws Exception {
        mMockConnectManager = mock(ConnectivityManager.class);
        mMockSocketKeepalive = mock(SocketKeepalive.class);
        doReturn(mMockSocketKeepalive)
                .when(mMockConnectManager)
                .createSocketKeepalive(
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        anyObject());

        mMockDeps = mock(IkeNattKeepalive.Dependencies.class);
        mMockSoftwareKeepalive = mock(SoftwareKeepaliveImpl.class);
        doReturn(mMockSoftwareKeepalive)
                .when(mMockDeps)
                .createSoftwareKeepaliveImpl(anyObject(), anyObject(), anyObject(), anyObject());

        mIkeNattKeepalive =
                new IkeNattKeepalive(
                        mock(Context.class),
                        mMockConnectManager,
                        KEEPALIVE_DELAY,
                        mock(Inet4Address.class),
                        mock(Inet4Address.class),
                        mock(UdpEncapsulationSocket.class),
                        mock(Network.class),
                        mock(IkeAlarmConfig.class),
                        mMockDeps);
    }

    @After
    public void tearDown() throws Exception {
        mIkeNattKeepalive.stop();
    }

    @Test
    public void testStartStopHardwareKeepalive() throws Exception {
        mIkeNattKeepalive.start();
        verify(mMockSocketKeepalive).start(KEEPALIVE_DELAY);

        mIkeNattKeepalive.stop();
        verify(mMockSocketKeepalive).stop();
    }

    @Test
    public void testSwitchToSoftwareKeepalive() throws Exception {
        verify(mMockConnectManager)
                .createSocketKeepalive(
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        mSocketKeepaliveCbCaptor.capture());
        SocketKeepalive.Callback socketKeepaliveCb = mSocketKeepaliveCbCaptor.getValue();
        socketKeepaliveCb.onError(ERROR_INVALID_IP_ADDRESS);

        verify(mMockSocketKeepalive).stop();
        verify(mMockDeps)
                .createSoftwareKeepaliveImpl(anyObject(), anyObject(), anyObject(), anyObject());

        mIkeNattKeepalive.stop();
        verify(mMockSocketKeepalive).stop();
        verify(mMockSoftwareKeepalive).stop();
    }
}

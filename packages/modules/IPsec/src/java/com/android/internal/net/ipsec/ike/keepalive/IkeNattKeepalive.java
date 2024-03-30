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

package com.android.internal.net.ipsec.ike.keepalive;

import static android.net.ipsec.ike.IkeManager.getIkeLog;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IpSecManager.UdpEncapsulationSocket;
import android.net.Network;

import com.android.internal.net.ipsec.ike.utils.IkeAlarm.IkeAlarmConfig;

import java.io.IOException;
import java.net.Inet4Address;

/**
 * This class provides methods to manage NAT-T keepalive for a UdpEncapsulationSocket.
 *
 * <p>Upon calling {@link start()}, this class will start a NAT-T keepalive, using hardware offload
 * if available. If hardware offload is not available, a software keepalive will be attempted.
 */
public class IkeNattKeepalive {
    private static final String TAG = "IkeNattKeepalive";

    private final Dependencies mDeps;

    private NattKeepalive mNattKeepalive;

    /** Construct an instance of IkeNattKeepalive */
    public IkeNattKeepalive(
            Context context,
            ConnectivityManager connectMgr,
            int keepaliveDelaySeconds,
            Inet4Address src,
            Inet4Address dest,
            UdpEncapsulationSocket socket,
            Network network,
            IkeAlarmConfig ikeAlarmConfig)
            throws IOException {
        this(
                context,
                connectMgr,
                keepaliveDelaySeconds,
                src,
                dest,
                socket,
                network,
                ikeAlarmConfig,
                new Dependencies());
    }

    IkeNattKeepalive(
            Context context,
            ConnectivityManager connectMgr,
            int keepaliveDelaySeconds,
            Inet4Address src,
            Inet4Address dest,
            UdpEncapsulationSocket socket,
            Network network,
            IkeAlarmConfig ikeAlarmConfig,
            Dependencies deps)
            throws IOException {
        mNattKeepalive =
                new HardwareKeepaliveImpl(
                        context,
                        connectMgr,
                        keepaliveDelaySeconds,
                        src,
                        dest,
                        socket,
                        network,
                        new HardwareKeepaliveCb(context, dest, socket, ikeAlarmConfig));
        mDeps = deps;
    }

    /** Start keepalive */
    public void start() {
        // Try keepalive using hardware offload first
        getIkeLog().d(TAG, "Start NAT-T keepalive");
        mNattKeepalive.start();
    }

    /** Stop keepalive */
    public void stop() {
        getIkeLog().d(TAG, "Stop NAT-T keepalive");

        mNattKeepalive.stop();
    }

    /** Receive a keepalive alarm */
    public void onAlarmFired() {
        mNattKeepalive.onAlarmFired();
    }

    /** Interface that a keepalive implementation MUST provide to support NAT-T keepalive for IKE */
    public interface NattKeepalive {
        /** Start keepalive */
        void start();
        /** Stop keepalive */
        void stop();
        /** Receive a keepalive alarm */
        void onAlarmFired();
    }

    static class Dependencies {
        SoftwareKeepaliveImpl createSoftwareKeepaliveImpl(
                Context context,
                Inet4Address dest,
                UdpEncapsulationSocket socket,
                IkeAlarmConfig alarmConfig) {
            return new SoftwareKeepaliveImpl(context, dest, socket, alarmConfig);
        }
    }

    private class HardwareKeepaliveCb implements HardwareKeepaliveImpl.HardwareKeepaliveCallback {
        private final Context mContext;
        private final Inet4Address mDest;
        private final UdpEncapsulationSocket mSocket;
        private final IkeAlarmConfig mIkeAlarmConfig;

        HardwareKeepaliveCb(
                Context context,
                Inet4Address dest,
                UdpEncapsulationSocket socket,
                IkeAlarmConfig ikeAlarmConfig) {
            mContext = context;
            mDest = dest;
            mSocket = socket;
            mIkeAlarmConfig = ikeAlarmConfig;
        }

        @Override
        public void onHardwareOffloadError() {
            getIkeLog().d(TAG, "Switch to software keepalive");
            mNattKeepalive.stop();

            mNattKeepalive =
                    mDeps.createSoftwareKeepaliveImpl(mContext, mDest, mSocket, mIkeAlarmConfig);
            mNattKeepalive.start();
        }

        @Override
        public void onNetworkError() {
            // Stop doing keepalive when getting network error since it will also fail software
            // keepalive. Considering the only user of IkeNattKeepalive is IkeSessionStateMachine,
            // not notifying user this error won't bring user extra risk. When there is a network
            // error, IkeSessionStateMachine will eventually hit the max request retransmission
            // times and be terminated anyway.

            // TODO: b/182209475 Terminate IKE Sessions when
            // HardwareKeepaliveCallback#onNetworkError is fired
            stop();
        }
    }
}

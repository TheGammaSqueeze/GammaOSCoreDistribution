/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.libraries.testing.deviceshadower.internal.bluetooth.connection;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.utils.Logger;

import com.google.common.collect.Sets;

import java.io.FileDescriptor;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A class represents a physical link for communications between two Bluetooth devices.
 */
public class PhysicalLink {

    // Intended to use RfcommDelegate
    private static final Logger LOGGER = Logger.create("RfcommDelegate");

    private final Object mLock;
    // Every socket has unique FileDescriptor, so use it as socket identifier during communication
    private final Map<FileDescriptor, RfcommSocketConnection> mConnectionLookup;
    // Map fd of a socket to the fd of the other socket it connects to
    private final Map<FileDescriptor, FileDescriptor> mFdMap;
    private final Set<RfcommSocketConnection> mConnections;
    private final AtomicBoolean mIsEncrypted;
    private final Map<String, RfcommDelegate.Callback> mCallbacks = new HashMap<>();

    public PhysicalLink(String address1, String address2) {
        this(address1,
                DeviceShadowEnvironmentImpl.getBlueletImpl(address1).getRfcommDelegate().mCallback,
                address2,
                DeviceShadowEnvironmentImpl.getBlueletImpl(address2).getRfcommDelegate().mCallback,
                new ConcurrentHashMap<FileDescriptor, RfcommSocketConnection>(),
                new ConcurrentHashMap<FileDescriptor, FileDescriptor>(),
                Sets.<RfcommSocketConnection>newConcurrentHashSet());
    }

    @VisibleForTesting
    PhysicalLink(String address1, RfcommDelegate.Callback callback1,
            String address2, RfcommDelegate.Callback callback2,
            Map<FileDescriptor, RfcommSocketConnection> connectionLookup,
            Map<FileDescriptor, FileDescriptor> fdMap,
            Set<RfcommSocketConnection> connections) {
        mLock = new Object();
        mCallbacks.put(address1, callback1);
        mCallbacks.put(address2, callback2);
        this.mConnectionLookup = connectionLookup;
        this.mFdMap = fdMap;
        this.mConnections = connections;
        mIsEncrypted = new AtomicBoolean(false);
    }

    public void addConnection(FileDescriptor fd1, FileDescriptor fd2) {
        synchronized (mLock) {
            int oldSize = mConnections.size();
            RfcommSocketConnection connection = new RfcommSocketConnection(
                    FileDescriptorFactory.getInstance().getAddress(fd1),
                    FileDescriptorFactory.getInstance().getAddress(fd2)
            );
            mConnections.add(connection);
            mConnectionLookup.put(fd1, connection);
            mConnectionLookup.put(fd2, connection);
            mFdMap.put(fd1, fd2);
            mFdMap.put(fd2, fd1);
            if (oldSize == 0) {
                onConnectionStateChange(true);
            }
        }
    }

    // TODO(b/79994182): see go/objecttostring-lsc
    @SuppressWarnings("ObjectToString")
    public void closeConnection(FileDescriptor fd) {
        // check for early return without locking
        if (!mConnectionLookup.containsKey(fd)) {
            // TODO(b/79994182): FileDescriptor does not implement toString() in fd
            LOGGER.d("Connection doesn't exist, FileDescriptor: " + fd);
            return;
        }
        synchronized (mLock) {
            RfcommSocketConnection connection = mConnectionLookup.get(fd);
            if (connection == null) {
                // TODO(b/79994182): FileDescriptor does not implement toString() in fd
                LOGGER.d("Connection doesn't exist, FileDescriptor: " + fd);
                return;
            }
            int oldSize = mConnections.size();
            FileDescriptor connectingFd = mFdMap.get(fd);
            mConnectionLookup.remove(fd);
            mConnectionLookup.remove(connectingFd);
            mFdMap.remove(fd);
            mFdMap.remove(connectingFd);
            mConnections.remove(connection);
            if (oldSize == 1) {
                onConnectionStateChange(false);
            }
        }
    }

    public RfcommSocketConnection getConnection(FileDescriptor fd) {
        return mConnectionLookup.get(fd);
    }

    public void encrypt() {
        mIsEncrypted.set(true);
    }

    public boolean isEncrypted() {
        return mIsEncrypted.get();
    }

    public boolean isConnected() {
        return !mConnections.isEmpty();
    }

    private void onConnectionStateChange(boolean isConnected) {
        for (Entry<String, RfcommDelegate.Callback> entry : mCallbacks.entrySet()) {
            RfcommDelegate.Callback callback = entry.getValue();
            String localAddress = entry.getKey();
            callback.onConnectionStateChange(getRemoteAddress(localAddress), isConnected);
        }
    }

    private String getRemoteAddress(String address) {
        String remoteAddress = null;
        for (String addr : mCallbacks.keySet()) {
            if (!addr.equals(address)) {
                remoteAddress = addr;
                break;
            }
        }
        return remoteAddress;
    }

    /**
     * Represents a Rfcomm socket connection between two {@link android.bluetooth.BluetoothSocket}.
     */
    public static class RfcommSocketConnection {

        final Map<String, BlockingQueue<Integer>> mIncomingDataMap; // address : incomingData

        public RfcommSocketConnection(String address1, String address2) {
            mIncomingDataMap = new ConcurrentHashMap<>();
            mIncomingDataMap.put(address1, new LinkedBlockingQueue<Integer>());
            mIncomingDataMap.put(address2, new LinkedBlockingQueue<Integer>());
        }

        public void write(String address, int b) throws InterruptedException {
            mIncomingDataMap.get(address).put(b);
        }

        public int read(String address) throws InterruptedException {
            return mIncomingDataMap.get(address).take();
        }
    }
}

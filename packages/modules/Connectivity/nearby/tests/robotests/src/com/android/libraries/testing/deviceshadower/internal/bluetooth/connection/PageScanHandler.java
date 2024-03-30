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

import android.os.Build.VERSION;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.testing.deviceshadower.internal.utils.MacAddressGenerator;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Encapsulate page scan operations -- handle connection establishment between Bluetooth devices.
 */
public class PageScanHandler {

    private static final ConnectionRequest REQUEST_SERVER_SOCKET_CLOSE = new ConnectionRequest();

    private static PageScanHandler sInstance = null;

    public static synchronized PageScanHandler getInstance() {
        if (sInstance == null) {
            sInstance = new PageScanHandler();
        }
        return sInstance;
    }

    public static synchronized void reset() {
        sInstance = null;
    }

    // use FileDescriptor to identify incoming data before socket is connected.
    private final Map<FileDescriptor, BlockingQueue<Integer>> mIncomingDataMap;
    // map a server socket fd to a connection request queue
    private final Map<FileDescriptor, BlockingQueue<ConnectionRequest>> mConnectionRequests;
    // map a fd on client side to a fd of BluetoothSocket(not BluetoothServerSocket) on server side
    private final Map<FileDescriptor, FileDescriptor> mClientServerFdMap;
    // map a client fd to a connection request so the client socket can finish the pending
    // connection
    private final Map<FileDescriptor, ConnectionRequest> mPendingConnections;

    private PageScanHandler() {
        mIncomingDataMap = new ConcurrentHashMap<>();
        mConnectionRequests = new ConcurrentHashMap<>();
        mClientServerFdMap = new ConcurrentHashMap<>();
        mPendingConnections = new ConcurrentHashMap<>();
    }

    public void postConnectionRequest(FileDescriptor serverSocketFd, ConnectionRequest request)
            throws InterruptedException {
        // used by the returning socket on server-side
        FileDescriptor fd = FileDescriptorFactory.getInstance()
                .createFileDescriptor(request.mServerAddress);
        mClientServerFdMap.put(request.mClientFd, fd);
        BlockingQueue<ConnectionRequest> requests = mConnectionRequests.get(serverSocketFd);
        requests.put(request);
        mPendingConnections.put(request.mClientFd, request);
    }

    public void addServerSocket(FileDescriptor serverSocketFd) {
        mConnectionRequests.put(serverSocketFd, new LinkedBlockingQueue<ConnectionRequest>());
    }

    public FileDescriptor getServerFd(FileDescriptor clientFd) {
        return mClientServerFdMap.get(clientFd);
    }

    // TODO(b/79994182): see go/objecttostring-lsc
    @SuppressWarnings("ObjectToString")
    public FileDescriptor processNextConnectionRequest(FileDescriptor serverSocketFd)
            throws IOException, InterruptedException {
        ConnectionRequest request = mConnectionRequests.get(serverSocketFd).take();
        if (request == REQUEST_SERVER_SOCKET_CLOSE) {
            // TODO(b/79994182): FileDescriptor does not implement toString() in serverSocketFd
            throw new IOException("Server socket is closed. fd: " + serverSocketFd);
        }
        writeInitialConnectionInfo(serverSocketFd, request.mClientAddress, request.mPort);
        return request.mClientFd;
    }

    public void waitForConnectionEstablished(FileDescriptor clientFd) throws InterruptedException {
        ConnectionRequest request = mPendingConnections.get(clientFd);
        if (request != null) {
            request.mCountDownLatch.await();
        }
    }

    public void finishPendingConnection(FileDescriptor clientFd) {
        ConnectionRequest request = mPendingConnections.get(clientFd);
        if (request != null) {
            request.mCountDownLatch.countDown();
        }
    }

    public void cancelServerSocket(FileDescriptor serverSocketFd) throws InterruptedException {
        mConnectionRequests.get(serverSocketFd).put(REQUEST_SERVER_SOCKET_CLOSE);
    }

    public void writeInitialConnectionInfo(FileDescriptor fd, String address, int port)
            throws InterruptedException {
        for (byte b : initialConnectionInfo(address, port)) {
            write(fd, Integer.valueOf(b));
        }
    }

    public void writePort(FileDescriptor fd, int port) throws InterruptedException {
        byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(port).array();
        for (byte b : bytes) {
            write(fd, Integer.valueOf(b));
        }
    }

    public void write(FileDescriptor fd, int data) throws InterruptedException {
        BlockingQueue<Integer> incomingData = mIncomingDataMap.get(fd);
        if (incomingData == null) {
            synchronized (mIncomingDataMap) {
                incomingData = mIncomingDataMap.get(fd);
                if (incomingData == null) {
                    incomingData = new LinkedBlockingQueue<Integer>();
                    mIncomingDataMap.put(fd, incomingData);
                }
            }
        }
        incomingData.put(data);
    }

    public int read(FileDescriptor fd) throws InterruptedException {
        return mIncomingDataMap.get(fd).take();
    }

    /**
     * A connection request from a {@link android.bluetooth.BluetoothSocket}.
     */
    @VisibleForTesting
    public static class ConnectionRequest {

        final FileDescriptor mClientFd;
        final String mClientAddress;
        final String mServerAddress;
        final int mPort;
        final CountDownLatch mCountDownLatch; // block server socket until connection established

        public ConnectionRequest(FileDescriptor fd, String clientAddress, String serverAddress,
                int port) {
            mClientFd = fd;
            this.mClientAddress = clientAddress;
            this.mServerAddress = serverAddress;
            this.mPort = port;
            mCountDownLatch = new CountDownLatch(1);
        }

        private ConnectionRequest() {
            mClientFd = null;
            mClientAddress = null;
            mServerAddress = null;
            mPort = -1;
            mCountDownLatch = new CountDownLatch(0);
        }
    }

    private static byte[] initialConnectionInfo(String addr, int port) {
        byte[] mac = MacAddressGenerator.convertStringMacAddress(addr);
        int channel = port;
        int status = 0;

        if (VERSION.SDK_INT < 23) {
            byte[] signal = new byte[16];
            short signalSize = 16;
            ByteBuffer buffer = ByteBuffer.wrap(signal);
            buffer.order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(signalSize)
                    .put(mac)
                    .putInt(channel)
                    .putInt(status);
            return buffer.array();
        } else {
            byte[] signal = new byte[20];
            short signalSize = 20;
            short maxTxPacketSize = 10000;
            short maxRxPacketSize = 10000;
            ByteBuffer buffer = ByteBuffer.wrap(signal);
            buffer.order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(signalSize)
                    .put(mac)
                    .putInt(channel)
                    .putInt(status)
                    .putShort(maxTxPacketSize)
                    .putShort(maxRxPacketSize);
            return buffer.array();
        }
    }
}

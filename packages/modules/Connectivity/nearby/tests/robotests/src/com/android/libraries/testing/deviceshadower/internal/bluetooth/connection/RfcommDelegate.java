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

import android.os.ParcelFileDescriptor;
import android.os.ParcelUuid;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.BlueletImpl;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.BluetoothConstants;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.connection.PageScanHandler.ConnectionRequest;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.connection.PhysicalLink.RfcommSocketConnection;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.connection.SdpHandler.ServiceRecord;
import com.android.libraries.testing.deviceshadower.internal.common.Interrupter;
import com.android.libraries.testing.deviceshadower.internal.utils.Logger;

import com.google.errorprone.annotations.FormatMethod;

import org.robolectric.util.ReflectionHelpers;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delegate for Bluetooth Rfcommon operations, including creating service record, establishing
 * connection, and data communications.
 * <p>Socket connection with uuid is supported. Listen on port and connect to port are not
 * supported.</p>
 */
public class RfcommDelegate {

    private static final Logger LOGGER = Logger.create("RfcommDelegate");
    private static final Object LOCK = new Object();

    /**
     * Callback for Rfcomm operations
     */
    public interface Callback {

        void onConnectionStateChange(String remoteAddress, boolean isConnected);
    }

    public static void reset() {
        PageScanHandler.reset();
        FileDescriptorFactory.reset();
    }

    final Callback mCallback;
    private final String mAddress;
    private final Interrupter mInterrupter;
    private final SdpHandler mSdpHandler;
    private final PageScanHandler mPageScanHandler;
    private final Map<String, PhysicalLink> mConnectionMap; // remoteAddress : physicalLink

    public RfcommDelegate(String address, Callback callback, Interrupter interrupter) {
        this.mAddress = address;
        this.mCallback = callback;
        this.mInterrupter = interrupter;
        mSdpHandler = new SdpHandler(address);
        mPageScanHandler = PageScanHandler.getInstance();
        mConnectionMap = new ConcurrentHashMap<>();
    }

    @SuppressWarnings("ObjectToString")
    public ParcelFileDescriptor createSocketChannel(String serviceName, ParcelUuid uuid) {
        ServiceRecord record = mSdpHandler.createServiceRecord(uuid.getUuid(), serviceName);
        if (record == null) {
            LOGGER.e(
                    String.format("Address %s: failed to create socket channel, uuid: %s", mAddress,
                            uuid));
            return null;
        }
        try {
            mPageScanHandler.writePort(record.mServerSocketFd, record.mPort);
        } catch (InterruptedException e) {
            LOGGER.e(String.format("Address %s: failed to write port to incoming data, fd: %s",
                    mAddress,
                    record.mServerSocketFd), e);
            return null;
        }
        return parcelFileDescriptor(record.mServerSocketFd);
    }

    @SuppressWarnings("ObjectToString")
    public ParcelFileDescriptor connectSocket(String remoteAddress, UUID uuid) {
        BlueletImpl remote = DeviceShadowEnvironmentImpl.getBlueletImpl(remoteAddress);
        if (remote == null) {
            LOGGER.e(String.format("Device %s is not defined.", remoteAddress));
            return null;
        }
        ServiceRecord record = remote.getRfcommDelegate().mSdpHandler.lookupChannel(uuid);
        if (record == null) {
            LOGGER.e(String.format("Address %s: failed to connect socket, uuid: %s", mAddress,
                    uuid));
            return null;
        }
        FileDescriptor fd = FileDescriptorFactory.getInstance().createFileDescriptor(mAddress);
        try {
            mPageScanHandler.writePort(fd, record.mPort);
        } catch (InterruptedException e) {
            LOGGER.e(String.format("Address %s: failed to write port to incoming data, fd: %s",
                    mAddress,
                    fd), e);
            return null;
        }

        // establish connection
        try {
            initiateConnectToServer(fd, record, remoteAddress);
        } catch (IOException e) {
            LOGGER.e(
                    String.format("Address %s: fail to initiate connection to server, clientFd: %s",
                            mAddress, fd), e);
            return null;
        }
        return parcelFileDescriptor(fd);
    }

    /**
     * Creates connection and unblocks server socket.
     * <p>ShadowBluetoothSocket calls the method at the end of connect().</p>
     */
    public void finishPendingConnection(
            String serverAddress, FileDescriptor clientFd, boolean isEncrypted) {
        // update states
        PhysicalLink physicalChannel = mConnectionMap.get(serverAddress);
        if (physicalChannel == null) {
            // use class level lock to ensure two RfcommDelegate hold reference to the same Physical
            // Link
            synchronized (LOCK) {
                physicalChannel = mConnectionMap.get(serverAddress);
                if (physicalChannel == null) {
                    physicalChannel = new PhysicalLink(
                            serverAddress,
                            FileDescriptorFactory.getInstance().getAddress(clientFd));
                    addPhysicalChannel(serverAddress, physicalChannel);
                    BlueletImpl remote = DeviceShadowEnvironmentImpl.getBlueletImpl(serverAddress);
                    remote.getRfcommDelegate().addPhysicalChannel(mAddress, physicalChannel);
                }
            }
        }
        physicalChannel.addConnection(clientFd, mPageScanHandler.getServerFd(clientFd));

        if (isEncrypted) {
            physicalChannel.encrypt();
        }
        mPageScanHandler.finishPendingConnection(clientFd);
    }

    /**
     * Process the next {@link ConnectionRequest} to {@link android.bluetooth.BluetoothServerSocket}
     * identified by serverSocketFd. This call will block until next connection request is
     * available.
     */
    @SuppressWarnings("ObjectToString")
    public FileDescriptor processNextConnectionRequest(FileDescriptor serverSocketFd)
            throws IOException {
        try {
            return mPageScanHandler.processNextConnectionRequest(serverSocketFd);
        } catch (InterruptedException e) {
            throw new IOException(
                    logError(e, "failed to process next connection request, serverSocketFd: %s",
                            serverSocketFd),
                    e);
        }
    }

    /**
     * Waits for a connection established.
     * <p>ShadowBluetoothServerSocket calls the method at the end of accept(). Ensure that a
     * connection is established when accept() returns.</p>
     */
    @SuppressWarnings("ObjectToString")
    public void waitForConnectionEstablished(FileDescriptor clientFd) throws IOException {
        try {
            mPageScanHandler.waitForConnectionEstablished(clientFd);
        } catch (InterruptedException e) {
            throw new IOException(
                    logError(e, "failed to wait for connection established. clientFd: %s",
                            clientFd), e);
        }
    }

    @SuppressWarnings("ObjectToString")
    public void write(String remoteAddress, FileDescriptor localFd, int b)
            throws IOException {
        checkInterrupt();
        RfcommSocketConnection connection =
                mConnectionMap.get(remoteAddress).getConnection(localFd);
        if (connection == null) {
            throw new IOException("closed");
        }
        try {
            connection.write(remoteAddress, b);
        } catch (InterruptedException e) {
            throw new IOException(
                    logError(e, "failed to write to target %s, fd: %s", remoteAddress,
                            localFd), e);
        }
    }

    @SuppressWarnings("ObjectToString")
    public int read(String remoteAddress, FileDescriptor localFd) throws IOException {
        checkInterrupt();
        // remoteAddress is null: 1. server socket, 2. client socket before connected
        try {
            if (remoteAddress == null) {
                return mPageScanHandler.read(localFd);
            }
        } catch (InterruptedException e) {
            throw new IOException(logError(e, "failed to read, fd: %s", localFd), e);
        }

        RfcommSocketConnection connection =
                mConnectionMap.get(remoteAddress).getConnection(localFd);
        if (connection == null) {
            throw new IOException("closed");
        }
        try {
            return connection.read(mAddress);
        } catch (InterruptedException e) {
            throw new IOException(logError(e, "failed to read, fd: %s", localFd), e);
        }
    }

    @SuppressWarnings("ObjectToString")
    public void shutdownInput(String remoteAddress, FileDescriptor localFd)
            throws IOException {
        // remoteAddress is null: 1. server socket, 2. client socket before connected
        try {
            if (remoteAddress == null) {
                mPageScanHandler.write(localFd, BluetoothConstants.SOCKET_CLOSE);
                return;
            }
        } catch (InterruptedException e) {
            throw new IOException(logError(e, "failed to shutdown input. fd: %s", localFd), e);
        }

        RfcommSocketConnection connection =
                mConnectionMap.get(remoteAddress).getConnection(localFd);
        if (connection == null) {
            LOGGER.d(String.format("Address %s: Connection already closed. fd: %s.", mAddress,
                    localFd));
            return;
        }
        try {
            connection.write(mAddress, BluetoothConstants.SOCKET_CLOSE);
        } catch (InterruptedException e) {
            throw new IOException(logError(e, "failed to shutdown input. fd: %s", localFd), e);
        }
    }

    @SuppressWarnings("ObjectToString")
    public void shutdownOutput(String remoteAddress, FileDescriptor localFd)
            throws IOException {
        RfcommSocketConnection connection =
                mConnectionMap.get(remoteAddress).getConnection(localFd);
        if (connection == null) {
            LOGGER.d(String.format("Address %s: Connection already closed. fd: %s.", mAddress,
                    localFd));
            return;
        }
        try {
            connection.write(remoteAddress, BluetoothConstants.SOCKET_CLOSE);
        } catch (InterruptedException e) {
            throw new IOException(logError(e, "failed to shutdown output. fd: %s", localFd), e);
        }
    }

    @SuppressWarnings("ObjectToString")
    public void closeServerSocket(FileDescriptor serverSocketFd) throws IOException {
        // remove service record
        UUID uuid = mSdpHandler.getUuid(serverSocketFd);
        mSdpHandler.removeServiceRecord(uuid);
        // unblock accept()
        try {
            mPageScanHandler.cancelServerSocket(serverSocketFd);
        } catch (InterruptedException e) {
            throw new IOException(
                    logError(e, "failed to cancel server socket, serverSocketFd: %s",
                            serverSocketFd),
                    e);
        }
    }

    public FileDescriptor getServerFd(FileDescriptor clientFd) {
        return mPageScanHandler.getServerFd(clientFd);
    }

    @VisibleForTesting
    public void addPhysicalChannel(String remoteAddress, PhysicalLink channel) {
        mConnectionMap.put(remoteAddress, channel);
    }

    @SuppressWarnings("ObjectToString")
    public void initiateConnectToClient(FileDescriptor clientFd, int port)
            throws IOException {
        checkInterrupt();
        String clientAddress = FileDescriptorFactory.getInstance().getAddress(clientFd);
        LOGGER.d(String.format("Address %s: init connection to %s, clientFd: %s",
                mAddress, clientAddress, clientFd));
        try {
            mPageScanHandler.writeInitialConnectionInfo(clientFd, mAddress, port);
        } catch (InterruptedException e) {
            throw new IOException(
                    logError(e,
                            "failed to write initial connection info to %s, clientFd: %s",
                            clientAddress, clientFd),
                    e);
        }
    }

    @SuppressWarnings("ObjectToString")
    private void initiateConnectToServer(FileDescriptor clientFd, ServiceRecord serviceRecord,
            String serverAddress) throws IOException {
        checkInterrupt();
        LOGGER.d(
                String.format("Address %s: init connection to %s, serverSocketFd: %s, clientFd: %s",
                        mAddress, serverAddress, serviceRecord.mServerSocketFd, clientFd));
        try {
            ConnectionRequest request = new ConnectionRequest(clientFd, mAddress, serverAddress,
                    serviceRecord.mPort);
            mPageScanHandler.postConnectionRequest(serviceRecord.mServerSocketFd, request);
        } catch (InterruptedException e) {
            throw new IOException(
                    logError(e,
                            "failed to post connection request, serverSocketFd: %s, "
                                    + "clientFd: %s",
                            serviceRecord.mServerSocketFd, clientFd),
                    e);
        }
    }

    public void checkInterrupt() throws IOException {
        mInterrupter.checkInterrupt();
    }

    private ParcelFileDescriptor parcelFileDescriptor(FileDescriptor fd) {
        return ReflectionHelpers.callConstructor(ParcelFileDescriptor.class,
                ReflectionHelpers.ClassParameter.from(FileDescriptor.class, fd));
    }

    @FormatMethod
    private String logError(Exception e, String msgTmpl, Object... args) {
        String errMsg = String.format("Address %s: ", mAddress) + String.format(msgTmpl, args);
        LOGGER.e(errMsg, e);
        return errMsg;
    }
}

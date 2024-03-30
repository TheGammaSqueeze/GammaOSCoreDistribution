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

package com.android.libraries.testing.deviceshadower.helpers.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.android.libraries.testing.deviceshadower.DeviceShadowEnvironment;
import com.android.libraries.testing.deviceshadower.DeviceShadowEnvironmentInternal;
import com.android.libraries.testing.deviceshadower.helpers.utils.IOUtils;

import java.io.IOException;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helper class to operate a device with basic functionality to accept BluetoothRfcommConnection.
 *
 * <p>
 * Usage: // Create a virtual device to accept incoming connection. BluetoothRfcommAcceptor acceptor
 * = new BluetoothRfcommAcceptor(address, uuid, callback); // Start accepting incoming connection,
 * with given uuid. acceptor.start(); // Connector needs to wait till acceptor started to make sure
 * there is a server socket created. acceptor.waitTillServerSocketStarted();
 *
 * // Connector can initiate connection.
 *
 * // A blocking call to wait for connection. acceptor.waitTillConnected();
 *
 * // Acceptor sends a message acceptor.send("Hello".getBytes());
 *
 * // Cancel acceptor to release all blocking calls. acceptor.cancel();
 */
public class BluetoothRfcommAcceptor {

    private static final String TAG = "BluetoothRfcommAcceptor";

    /**
     * Identifiers to control Bluetooth operation.
     */
    public static final int PRE_START = 4;
    public static final int PRE_ACCEPT = 1;
    public static final int PRE_WRITE = 3;
    public static final int PRE_READ = 2;

    private final String mAddress;
    private final UUID mUuid;
    private BluetoothSocket mSocket;
    private BluetoothServerSocket mServerSocket;

    private final AtomicBoolean mCancelled;
    private final Callback mCallback;
    private final CountDownLatch mStartLatch = new CountDownLatch(1);
    private final CountDownLatch mConnectLatch = new CountDownLatch(1);
    private final Queue<CountDownLatch> mReadLatches = new ConcurrentLinkedQueue<>();

    /**
     * Callback of BluetoothRfcommAcceptor.
     */
    public interface Callback {

        void onSocketAccepted(BluetoothSocket socket);

        void onDataReceived(byte[] data);

        void onDataWritten(byte[] data);

        void onError(Exception exception);
    }

    public BluetoothRfcommAcceptor(String address, UUID uuid, Callback callback) {
        this.mAddress = address;
        this.mUuid = uuid;
        this.mCallback = callback;
        this.mCancelled = new AtomicBoolean(false);
        DeviceShadowEnvironment.addDevice(address).bluetooth()
                .setAdapterInitialState(BluetoothAdapter.STATE_ON);
    }

    /**
     * Start bluetooth server socket, accept incoming connection, and receive incoming data once
     * connected.
     */
    public Future<Void> start() {
        return DeviceShadowEnvironment.run(mAddress, mCode);
    }

    /**
     * Blocking call to wait bluetooth server socket started.
     */
    public void waitTillServerSocketStarted() {
        try {
            mStartLatch.await();
        } catch (InterruptedException e) {
            Log.w(TAG, mAddress + " fail to wait till started: ", e);
        }
    }

    public void waitTillConnected() {
        try {
            mConnectLatch.await();
        } catch (InterruptedException e) {
            Log.w(TAG, mAddress + " fail to wait till started: ", e);
        }
    }

    public void waitTillDataReceived() {
        try {
            if (mReadLatches.size() > 0) {
                mReadLatches.poll().await();
            }
        } catch (InterruptedException e) {
            // no-op
        }
    }

    /**
     * Stop receiving data by closing socket.
     */
    public Future<Void> cancel() {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mCancelled.set(true);
                try {
                    mSocket.close();
                } catch (IOException e) {
                    Log.w(TAG, mAddress + " fail to close server socket", e);
                }
            }
        });
    }

    /**
     * Send data to connected device.
     */
    public Future<Void> send(final byte[] data) {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                if (mSocket != null) {
                    try {
                        DeviceShadowEnvironmentInternal.setInterruptibleBluetooth(PRE_WRITE);
                        IOUtils.write(mSocket.getOutputStream(), data);
                        Log.d(TAG, mAddress + " write: " + new String(data));
                        mCallback.onDataWritten(data);
                    } catch (IOException e) {
                        Log.w(TAG, mAddress + " fail to write: ", e);
                        mCallback.onError(new IOException("Fail to write", e));
                    }
                }
            }
        });
    }

    private Runnable mCode = new Runnable() {
        @Override
        public void run() {
            try {
                DeviceShadowEnvironmentInternal.setInterruptibleBluetooth(PRE_START);
                mServerSocket = BluetoothAdapter.getDefaultAdapter()
                        .listenUsingInsecureRfcommWithServiceRecord("AA", mUuid);
            } catch (IOException e) {
                Log.w(TAG, mAddress + " fail to start server socket: ", e);
                mCallback.onError(new IOException("Fail to start server socket", e));
                return;
            } finally {
                mStartLatch.countDown();
            }

            try {
                DeviceShadowEnvironmentInternal.setInterruptibleBluetooth(PRE_ACCEPT);
                mSocket = mServerSocket.accept();
                Log.d(TAG, mAddress + " accept: " + mSocket.getRemoteDevice().getAddress());
                mCallback.onSocketAccepted(mSocket);
                mServerSocket.close();
            } catch (IOException e) {
                Log.w(TAG, mAddress + " fail to connect: ", e);
                mCallback.onError(new IOException("Fail to connect", e));
                return;
            } finally {
                mConnectLatch.countDown();
            }

            do {
                try {
                    CountDownLatch latch = new CountDownLatch(1);
                    mReadLatches.add(latch);
                    DeviceShadowEnvironmentInternal.setInterruptibleBluetooth(PRE_READ);
                    byte[] data = IOUtils.read(mSocket.getInputStream());
                    Log.d(TAG, mAddress + " read: " + new String(data));
                    mCallback.onDataReceived(data);
                    latch.countDown();
                } catch (IOException e) {
                    Log.w(TAG, mAddress + " fail to read: ", e);
                    mCallback.onError(new IOException("Fail to read", e));
                    return;
                }
            } while (!mCancelled.get());

            Log.d(TAG, mAddress + " stop receiving");
        }
    };

}

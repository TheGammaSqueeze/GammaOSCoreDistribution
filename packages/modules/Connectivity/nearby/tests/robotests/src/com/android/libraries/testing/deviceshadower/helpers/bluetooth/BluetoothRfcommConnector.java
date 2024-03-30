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
 * Usage: // Create a virtual device to initiate connection. BluetoothRfcommConnector connector =
 * new BluetoothRfcommConnector(address, callback); // Start connection to a remote address with
 * given uuid. connector.start(remoteAddress, remoteUuid);
 *
 * // A blocking call to wait for connection. connector.waitTillConnected();
 *
 * // Connector sends a message connector.send("Hello".getBytes());
 *
 * // Cancel connector to release all blocking calls. connector.cancel();
 */
public class BluetoothRfcommConnector {

    private static final String TAG = "BluetoothRfcommConnector";

    /**
     * Identifiers to control Bluetooth operation.
     */
    public static final int PRE_CONNECT = 1;
    public static final int PRE_READ = 2;
    public static final int PRE_WRITE = 3;

    private final String mAddress;
    private String mRemoteAddress = null;
    private final UUID mRemoteUuid;
    private BluetoothSocket mSocket;

    private final Callback mCallback;
    private final AtomicBoolean mCancelled;
    private final CountDownLatch mConnectLatch = new CountDownLatch(1);
    private final Queue<CountDownLatch> mReadLatches = new ConcurrentLinkedQueue<>();

    /**
     * Callback of BluetoothRfcommConnector.
     */
    public interface Callback {

        void onConnected(BluetoothSocket socket);

        void onDataReceived(byte[] data);

        void onDataWritten(byte[] data);

        void onError(Exception exception);
    }

    public BluetoothRfcommConnector(String address, UUID uuid, Callback callback) {
        this.mAddress = address;
        this.mRemoteUuid = uuid;
        this.mCallback = callback;
        this.mCancelled = new AtomicBoolean(false);
        DeviceShadowEnvironment.addDevice(address).bluetooth()
                .setAdapterInitialState(BluetoothAdapter.STATE_ON);
    }

    /**
     * Start connection to a remote address, and receive data once connected.
     */
    public Future<Void> start(String remoteAddress) {
        this.mRemoteAddress = remoteAddress;
        return DeviceShadowEnvironment.run(mAddress, mCode);
    }

    /**
     * Stop receiving data.
     */
    public Future<Void> cancel() {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mCancelled.set(true);
                try {
                    mSocket.close();
                } catch (IOException e) {
                    Log.w(TAG, mAddress + " fail to close socket", e);
                }
            }
        });
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
            // no-op.
        }
    }

    /**
     * Send data to conneceted device.
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
                DeviceShadowEnvironmentInternal.setInterruptibleBluetooth(PRE_CONNECT);
                mSocket = BluetoothAdapter.getDefaultAdapter()
                        .getRemoteDevice(mRemoteAddress)
                        .createInsecureRfcommSocketToServiceRecord(mRemoteUuid);
                mSocket.connect();
                Log.d(TAG, mAddress + " accept: " + mSocket.getRemoteDevice().getAddress());
                mCallback.onConnected(mSocket);
            } catch (IOException e) {
                Log.w(TAG, mAddress + " fail to connect: ", e);
                mCallback.onError(new IOException("Fail to connect", e));
            } finally {
                mConnectLatch.countDown();
            }

            try {
                do {
                    CountDownLatch latch = new CountDownLatch(1);
                    mReadLatches.add(latch);
                    DeviceShadowEnvironmentInternal.setInterruptibleBluetooth(PRE_READ);
                    byte[] data = IOUtils.read(mSocket.getInputStream());
                    Log.d(TAG, mAddress + " read: " + new String(data));
                    mCallback.onDataReceived(data);
                    latch.countDown();
                } while (!mCancelled.get());
            } catch (IOException e) {
                Log.w(TAG, mAddress + " fail to read: ", e);
                mCallback.onError(new IOException("Fail to read", e));
            }
            Log.d(TAG, mAddress + " stop receiving");
        }
    };

}

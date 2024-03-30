/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.nearby.fastpair.provider.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.BluetoothGattException;
import com.android.server.nearby.common.bluetooth.testability.VersionProvider;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothDevice;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothGattServer;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothGattServerCallback;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.Operation;

import com.google.common.base.Preconditions;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Helper for simplifying operations on {@link BluetoothGattServer}.
 */
@TargetApi(18)
public class BluetoothGattServerHelper {
    private static final String TAG = BluetoothGattServerHelper.class.getSimpleName();

    @VisibleForTesting
    static final long OPERATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(1);
    private static final int MAX_PARALLEL_OPERATIONS = 5;

    /** BT operation types that can be in flight. */
    public enum OperationType {
        ADD_SERVICE,
        CLOSE_CONNECTION,
        START_ADVERTISING
    }

    private final Object mOperationLock = new Object();
    @VisibleForTesting
    final BluetoothGattServerCallback mGattServerCallback =
            new GattServerCallback();
    @VisibleForTesting
    BluetoothOperationExecutor mBluetoothOperationScheduler =
            new BluetoothOperationExecutor(MAX_PARALLEL_OPERATIONS);

    private final Context mContext;
    private final BluetoothManager mBluetoothManager;
    private final VersionProvider mVersionProvider;

    @Nullable
    @VisibleForTesting
    volatile BluetoothGattServerConfig mServerConfig = null;

    @Nullable
    @VisibleForTesting
    volatile BluetoothGattServer mBluetoothGattServer = null;

    @VisibleForTesting
    final ConcurrentMap<BluetoothDevice, BluetoothGattServerConnection>
            mConnections = new ConcurrentHashMap<BluetoothDevice, BluetoothGattServerConnection>();

    public BluetoothGattServerHelper(Context context, BluetoothManager bluetoothManager) {
        this(
                Preconditions.checkNotNull(context),
                Preconditions.checkNotNull(bluetoothManager),
                new VersionProvider()
        );
    }

    @VisibleForTesting
    BluetoothGattServerHelper(
            Context context, BluetoothManager bluetoothManager, VersionProvider versionProvider) {
        mContext = context;
        mBluetoothManager = bluetoothManager;
        mVersionProvider = versionProvider;
    }

    @Nullable
    public BluetoothGattServerConfig getConfig() {
        return mServerConfig;
    }

    public void open(final BluetoothGattServerConfig gattServerConfig) throws BluetoothException {
        synchronized (mOperationLock) {
            Preconditions.checkState(mBluetoothGattServer == null, "Gatt server is already open.");
            final BluetoothGattServer server =
                    mBluetoothManager.openGattServer(mContext, mGattServerCallback);
            if (server == null) {
                throw new BluetoothException(
                        "Failed to open the GATT server, openGattServer returned null.");
            }

            try {
                for (final BluetoothGattService service :
                        gattServerConfig.getBluetoothGattServices()) {
                    if (service == null) {
                        continue;
                    }
                    mBluetoothOperationScheduler.execute(
                            new Operation<Void>(OperationType.ADD_SERVICE, service) {
                                @Override
                                public void run() throws BluetoothException {
                                    boolean success = server.addService(service);
                                    if (!success) {
                                        throw new BluetoothException("Fails on adding service");
                                    }
                                }
                            }, OPERATION_TIMEOUT_MILLIS);
                }
                mBluetoothGattServer = server;
                mServerConfig = gattServerConfig;
            } catch (BluetoothException e) {
                server.close();
                throw e;
            }
        }
    }

    public boolean isOpen() {
        synchronized (mOperationLock) {
            return mBluetoothGattServer != null;
        }
    }

    public void close() {
        synchronized (mOperationLock) {
            BluetoothGattServer bluetoothGattServer = mBluetoothGattServer;
            if (bluetoothGattServer == null) {
                return;
            }
            bluetoothGattServer.close();
            mBluetoothGattServer = null;
        }
    }

    private BluetoothGattServerConnection getConnectionByDevice(BluetoothDevice device)
            throws BluetoothGattException {
        BluetoothGattServerConnection bluetoothLeConnection = mConnections.get(device);
        if (bluetoothLeConnection == null) {
            throw new BluetoothGattException(
                    String.format("Received operation on an unknown device: %s", device),
                    BluetoothGatt.GATT_FAILURE);
        }
        return bluetoothLeConnection;
    }

    public void sendNotification(
            BluetoothDevice device,
            BluetoothGattCharacteristic characteristic,
            byte[] data,
            boolean confirm)
            throws BluetoothException {
        Log.d(TAG, String.format("Sending a %s of %d bytes on characteristics %s on device %s.",
                confirm ? "indication" : "notification",
                data.length,
                characteristic.getUuid(),
                device));
        synchronized (mOperationLock) {
            BluetoothGattServer bluetoothGattServer = mBluetoothGattServer;
            if (bluetoothGattServer == null) {
                throw new BluetoothException("Server is not open.");
            }
            BluetoothGattCharacteristic clonedCharacteristic =
                    BluetoothGattUtils.clone(characteristic);
            clonedCharacteristic.setValue(data);
            bluetoothGattServer.notifyCharacteristicChanged(device, clonedCharacteristic, confirm);
        }
    }

    public void closeConnection(final BluetoothDevice bluetoothDevice) throws BluetoothException {
        final BluetoothGattServer bluetoothGattServer = mBluetoothGattServer;
        if (bluetoothGattServer == null) {
            throw new BluetoothException("Server is not open.");
        }
        int connectionSate =
                mBluetoothManager.getConnectionState(bluetoothDevice, BluetoothProfile.GATT);
        if (connectionSate != BluetoothGatt.STATE_CONNECTED) {
            return;
        }
        mBluetoothOperationScheduler.execute(
                new Operation<Void>(OperationType.CLOSE_CONNECTION) {
                    @Override
                    public void run() throws BluetoothException {
                        bluetoothGattServer.cancelConnection(bluetoothDevice);
                    }
                },
                OPERATION_TIMEOUT_MILLIS);
    }

    private class GattServerCallback extends BluetoothGattServerCallback {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            mBluetoothOperationScheduler.notifyCompletion(
                    new Operation<Void>(OperationType.ADD_SERVICE, service), status);
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            BluetoothGattServerConfig serverConfig = mServerConfig;
            BluetoothGattServer bluetoothGattServer = mBluetoothGattServer;
            BluetoothGattServerConnection bluetoothLeConnection;
            if (serverConfig == null || bluetoothGattServer == null) {
                return;
            }
            switch (newState) {
                case BluetoothGattServer.STATE_CONNECTED:
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, String.format("Connection to %s failed: %s", device,
                                BluetoothGattUtils.getMessageForStatusCode(status)));
                        return;
                    }
                    Log.i(TAG, String.format("Connected to device %s.", device));
                    if (mConnections.containsKey(device)) {
                        Log.w(TAG, String.format("A connection is already open with device %s. "
                                + "Keeping existing one.", device));
                        return;
                    }

                    BluetoothGattServerConnection connection = new BluetoothGattServerConnection(
                            BluetoothGattServerHelper.this,
                            device,
                            serverConfig);
                    if (serverConfig.getServerListener() != null) {
                        serverConfig.getServerListener().onConnection(connection);
                    }
                    mConnections.put(device, connection);

                    // By default, Android disconnects active GATT server connection if the
                    // advertisement is
                    // stop (or sometime stopScanning also disconnect, see b/62667394). Asking
                    // the server to
                    // reverse connect will tell Android to keep the connection open.
                    // Code handling connect() on Android OS is: btif_gatt_server.c
                    // Note: for Android < P, unknown type devices don't connect. See b/62827460.
                    //       for Android P+, unknown type devices always use LE to connect (see
                    //       code)
                    // Note: for Android < N, dual mode devices always connect using BT classic,
                    // so connect()
                    //       should *NOT* be called for those devices. See b/29819614.
                    if (mVersionProvider.getSdkInt() >= VERSION_CODES.N
                            || device.getType() != BluetoothDevice.DEVICE_TYPE_DUAL) {
                        boolean success = bluetoothGattServer.connect(device, /* autoConnect */
                                false);
                        if (!success) {
                            Log.w(TAG, String.format(
                                    "Keeping connection open on stop advertising failed for "
                                            + "device %s.",
                                    device));
                        }
                    }
                    break;
                case BluetoothGattServer.STATE_DISCONNECTED:
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, String.format(
                                "Disconnection from %s error: %s. Proceeding anyway.",
                                device, BluetoothGattUtils.getMessageForStatusCode(status)));
                    }
                    bluetoothLeConnection = mConnections.remove(device);
                    if (bluetoothLeConnection != null) {
                        // Disconnect the server, required after connecting to it.
                        bluetoothGattServer.cancelConnection(device);
                        bluetoothLeConnection.onClose();
                    }
                    mBluetoothOperationScheduler.notifyCompletion(
                            new Operation<Void>(OperationType.CLOSE_CONNECTION), status);
                    break;
                default:
                    Log.e(TAG, String.format("Unexpected connection state: %d", newState));
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattCharacteristic characteristic) {
            BluetoothGattServer bluetoothGattServer = mBluetoothGattServer;
            if (bluetoothGattServer == null) {
                return;
            }
            try {
                byte[] value =
                        getConnectionByDevice(device).readCharacteristic(offset, characteristic);
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                        offset,
                        value);
            } catch (BluetoothGattException e) {
                Log.e(TAG,
                        String.format(
                                "Could not read  %s on device %s at offset %d",
                                BluetoothGattUtils.toString(characteristic),
                                device,
                                offset),
                        e);
                bluetoothGattServer.sendResponse(
                        device, requestId, e.getGattErrorCode(), offset, null);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                int requestId,
                BluetoothGattCharacteristic characteristic,
                boolean preparedWrite,
                boolean responseNeeded,
                int offset,
                byte[] value) {
            BluetoothGattServer bluetoothGattServer = mBluetoothGattServer;
            if (bluetoothGattServer == null) {
                return;
            }
            try {
                getConnectionByDevice(device).writeCharacteristic(characteristic,
                        preparedWrite,
                        offset,
                        value);
                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                }
            } catch (BluetoothGattException e) {
                Log.e(TAG,
                        String.format(
                                "Could not write %s on device %s at offset %d",
                                BluetoothGattUtils.toString(characteristic),
                                device,
                                offset),
                        e);
                bluetoothGattServer.sendResponse(
                        device, requestId, e.getGattErrorCode(), offset, null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattDescriptor descriptor) {
            BluetoothGattServer bluetoothGattServer = mBluetoothGattServer;
            if (bluetoothGattServer == null) {
                return;
            }
            try {
                byte[] value = getConnectionByDevice(device).readDescriptor(offset, descriptor);
                bluetoothGattServer.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            } catch (BluetoothGattException e) {
                Log.e(TAG, String.format(
                                "Could not read %s on device %s at %d",
                                BluetoothGattUtils.toString(descriptor),
                                device,
                                offset),
                        e);
                bluetoothGattServer.sendResponse(
                        device, requestId, e.getGattErrorCode(), offset, null);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device,
                int requestId,
                BluetoothGattDescriptor descriptor,
                boolean preparedWrite,
                boolean responseNeeded,
                int offset,
                byte[] value) {
            BluetoothGattServer bluetoothGattServer = mBluetoothGattServer;
            if (bluetoothGattServer == null) {
                return;
            }
            try {
                getConnectionByDevice(device)
                        .writeDescriptor(descriptor, preparedWrite, offset, value);
                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                }
                Log.d(TAG, "Operation onDescriptorWriteRequest successful.");
            } catch (BluetoothGattException e) {
                Log.e(TAG,
                        String.format(
                                "Could not write %s on device %s at %d",
                                BluetoothGattUtils.toString(descriptor),
                                device,
                                offset),
                        e);
                bluetoothGattServer.sendResponse(
                        device, requestId, e.getGattErrorCode(), offset, null);
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            BluetoothGattServer bluetoothGattServer = mBluetoothGattServer;
            if (bluetoothGattServer == null) {
                return;
            }
            try {
                getConnectionByDevice(device).executeWrite(execute);
                bluetoothGattServer.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            } catch (BluetoothGattException e) {
                Log.e(TAG, "Could not execute write.", e);
                bluetoothGattServer.sendResponse(device, requestId, e.getGattErrorCode(), 0, null);
            }
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            Log.d(TAG,
                    String.format("Received onNotificationSent for device %s with status %s",
                            device, status));
            try {
                getConnectionByDevice(device).notifyNotificationSent(status);
            } catch (BluetoothGattException e) {
                Log.e(TAG, "An error occurred when receiving onNotificationSent: " + e);
            }
        }
    }

    /** Listener for {@link BluetoothGattServerHelper}'s events. */
    public interface Listener {
        /** Called when a new connection to the server is established. */
        void onConnection(BluetoothGattServerConnection connection);
    }
}

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

package com.android.server.nearby.common.bluetooth.gatt;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothAdapter;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothDevice;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothGattCallback;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothGattWrapper;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.le.BluetoothLeScanner;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.le.ScanCallback;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.le.ScanResult;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.BluetoothOperationTimeoutException;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.Operation;

import com.google.common.base.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Wrapper of {@link BluetoothGattWrapper} that provides blocking methods, errors and timeout
 * handling.
 */
@SuppressWarnings("Guava") // java.util.Optional is not available until API 24
public class BluetoothGattHelper {

    private static final String TAG = BluetoothGattHelper.class.getSimpleName();

    @VisibleForTesting
    static final long LOW_LATENCY_SCAN_MILLIS = TimeUnit.SECONDS.toMillis(5);
    private static final long POLL_INTERVAL_MILLIS = 5L /* milliseconds */;

    /**
     * BT operation types that can be in flight.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    OperationType.SCAN,
                    OperationType.CONNECT,
                    OperationType.DISCOVER_SERVICES,
                    OperationType.DISCOVER_SERVICES_INTERNAL,
                    OperationType.NOTIFICATION_CHANGE,
                    OperationType.READ_CHARACTERISTIC,
                    OperationType.WRITE_CHARACTERISTIC,
                    OperationType.READ_DESCRIPTOR,
                    OperationType.WRITE_DESCRIPTOR,
                    OperationType.READ_RSSI,
                    OperationType.WRITE_RELIABLE,
                    OperationType.CHANGE_MTU,
                    OperationType.DISCONNECT,
            })
    public @interface OperationType {
        int SCAN = 0;
        int CONNECT = 1;
        int DISCOVER_SERVICES = 2;
        int DISCOVER_SERVICES_INTERNAL = 3;
        int NOTIFICATION_CHANGE = 4;
        int READ_CHARACTERISTIC = 5;
        int WRITE_CHARACTERISTIC = 6;
        int READ_DESCRIPTOR = 7;
        int WRITE_DESCRIPTOR = 8;
        int READ_RSSI = 9;
        int WRITE_RELIABLE = 10;
        int CHANGE_MTU = 11;
        int DISCONNECT = 12;
    }

    @VisibleForTesting
    final ScanCallback mScanCallback = new InternalScanCallback();
    @VisibleForTesting
    final BluetoothGattCallback mBluetoothGattCallback =
            new InternalBluetoothGattCallback();
    @VisibleForTesting
    final ConcurrentMap<BluetoothGattWrapper, BluetoothGattConnection> mConnections =
            new ConcurrentHashMap<>();

    private final Context mApplicationContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothOperationExecutor mBluetoothOperationExecutor;

    @VisibleForTesting
    BluetoothGattHelper(
            Context applicationContext,
            BluetoothAdapter bluetoothAdapter,
            BluetoothOperationExecutor bluetoothOperationExecutor) {
        mApplicationContext = applicationContext;
        mBluetoothAdapter = bluetoothAdapter;
        mBluetoothOperationExecutor = bluetoothOperationExecutor;
    }

    public BluetoothGattHelper(Context applicationContext, BluetoothAdapter bluetoothAdapter) {
        this(
                Preconditions.checkNotNull(applicationContext),
                Preconditions.checkNotNull(bluetoothAdapter),
                new BluetoothOperationExecutor(5));
    }

    /**
     * Auto-connects a serice Uuid.
     */
    public BluetoothGattConnection autoConnect(final UUID serviceUuid) throws BluetoothException {
        Log.d(TAG, String.format("Starting autoconnection to a device advertising service %s.",
                serviceUuid));
        BluetoothDevice device = null;
        int retries = 3;
        final BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            throw new BluetoothException("Bluetooth is disabled or LE is not supported.");
        }
        final ScanFilter serviceFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(serviceUuid))
                .build();
        ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder()
                .setReportDelay(0);
        final ScanSettings scanSettingsLowLatency = scanSettingsBuilder
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        final ScanSettings scanSettingsLowPower = scanSettingsBuilder
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();
        while (true) {
            long startTimeMillis = System.currentTimeMillis();
            try {
                Log.d(TAG, "Starting low latency scanning.");
                device =
                        mBluetoothOperationExecutor.executeNonnull(
                                new Operation<BluetoothDevice>(OperationType.SCAN) {
                                    @Override
                                    public void run() throws BluetoothException {
                                        scanner.startScan(Arrays.asList(serviceFilter),
                                                scanSettingsLowLatency, mScanCallback);
                                    }
                                }, LOW_LATENCY_SCAN_MILLIS);
            } catch (BluetoothOperationTimeoutException e) {
                Log.d(TAG, String.format(
                        "Cannot find a nearby device in low latency scanning after %s ms.",
                        LOW_LATENCY_SCAN_MILLIS));
            } finally {
                scanner.stopScan(mScanCallback);
            }
            if (device == null) {
                Log.d(TAG, "Starting low power scanning.");
                try {
                    device = mBluetoothOperationExecutor.executeNonnull(
                            new Operation<BluetoothDevice>(OperationType.SCAN) {
                                @Override
                                public void run() throws BluetoothException {
                                    scanner.startScan(Arrays.asList(serviceFilter),
                                            scanSettingsLowPower, mScanCallback);
                                }
                            });
                } finally {
                    scanner.stopScan(mScanCallback);
                }
            }
            Log.d(TAG, String.format("Scanning done in %d ms. Found device %s.",
                    System.currentTimeMillis() - startTimeMillis, device));

            try {
                return connect(device);
            } catch (BluetoothException e) {
                retries--;
                if (retries == 0) {
                    throw e;
                } else {
                    Log.d(TAG, String.format(
                            "Connection failed: %s. Retrying %d more times.", e, retries));
                }
            }
        }
    }

    /**
     * Connects to a device using default connection options.
     */
    public BluetoothGattConnection connect(BluetoothDevice bluetoothDevice)
            throws BluetoothException {
        return connect(bluetoothDevice, ConnectionOptions.builder().build());
    }

    /**
     * Connects to a device using specifies connection options.
     */
    public BluetoothGattConnection connect(
            BluetoothDevice bluetoothDevice, ConnectionOptions options) throws BluetoothException {
        Log.d(TAG, String.format("Connecting to device %s.", bluetoothDevice));
        long startTimeMillis = System.currentTimeMillis();

        Operation<BluetoothGattConnection> connectOperation =
                new Operation<BluetoothGattConnection>(OperationType.CONNECT, bluetoothDevice) {
                    private final Object mLock = new Object();

                    @GuardedBy("mLock")
                    private boolean mIsCanceled = false;

                    @GuardedBy("mLock")
                    @Nullable(/* null before operation is executed */)
                    private BluetoothGattWrapper mBluetoothGatt;

                    @Override
                    public void run() throws BluetoothException {
                        synchronized (mLock) {
                            if (mIsCanceled) {
                                return;
                            }
                            BluetoothGattWrapper bluetoothGattWrapper;
                            Log.d(TAG, "Use LE transport");
                            bluetoothGattWrapper =
                                    bluetoothDevice.connectGatt(
                                            mApplicationContext,
                                            options.autoConnect(),
                                            mBluetoothGattCallback,
                                            android.bluetooth.BluetoothDevice.TRANSPORT_LE);
                            if (bluetoothGattWrapper == null) {
                                throw new BluetoothException("connectGatt() returned null.");
                            }

                            try {
                                // Set connection priority without waiting for connection callback.
                                // Per code, btif_gatt_client.c, when priority is set before
                                // connection, this sets preferred connection parameters that will
                                // be used during the connection establishment.
                                Optional<Integer> connectionPriorityOption =
                                        options.connectionPriority();
                                if (connectionPriorityOption.isPresent()) {
                                    // requestConnectionPriority can only be called when
                                    // BluetoothGatt is connected to the system BluetoothGatt
                                    // service (see android/bluetooth/BluetoothGatt.java code).
                                    // However, there is no callback to the app to inform when this
                                    // is done. requestConnectionPriority will returns false with no
                                    // side-effect before the service is connected, so we just poll
                                    // here until true is returned.
                                    int connectionPriority = connectionPriorityOption.get();
                                    long startTimeMillis = System.currentTimeMillis();
                                    while (!bluetoothGattWrapper.requestConnectionPriority(
                                            connectionPriority)) {
                                        if (System.currentTimeMillis() - startTimeMillis
                                                > options.connectionTimeoutMillis()) {
                                            throw new BluetoothException(
                                                    String.format(
                                                            Locale.US,
                                                            "Failed to set connectionPriority "
                                                                    + "after %dms.",
                                                            options.connectionTimeoutMillis()));
                                        }
                                        try {
                                            Thread.sleep(POLL_INTERVAL_MILLIS);
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            throw new BluetoothException(
                                                    "connect() operation interrupted.");
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Make sure to clean connection.
                                bluetoothGattWrapper.disconnect();
                                bluetoothGattWrapper.close();
                                throw e;
                            }

                            BluetoothGattConnection connection = new BluetoothGattConnection(
                                    bluetoothGattWrapper, mBluetoothOperationExecutor, options);
                            mConnections.put(bluetoothGattWrapper, connection);
                            mBluetoothGatt = bluetoothGattWrapper;
                        }
                    }

                    @Override
                    public void cancel() {
                        // Clean connection if connection times out.
                        synchronized (mLock) {
                            if (mIsCanceled) {
                                return;
                            }
                            mIsCanceled = true;
                            BluetoothGattWrapper bluetoothGattWrapper = mBluetoothGatt;
                            if (bluetoothGattWrapper == null) {
                                return;
                            }
                            mConnections.remove(bluetoothGattWrapper);
                            bluetoothGattWrapper.disconnect();
                            bluetoothGattWrapper.close();
                        }
                    }
                };
        BluetoothGattConnection result;
        if (options.autoConnect()) {
            result = mBluetoothOperationExecutor.executeNonnull(connectOperation);
        } else {
            result =
                    mBluetoothOperationExecutor.executeNonnull(
                            connectOperation, options.connectionTimeoutMillis());
        }
        Log.d(TAG, String.format("Connection success in %d ms.",
                System.currentTimeMillis() - startTimeMillis));
        return result;
    }

    private BluetoothGattConnection getConnectionByGatt(BluetoothGattWrapper gatt)
            throws BluetoothException {
        BluetoothGattConnection connection = mConnections.get(gatt);
        if (connection == null) {
            throw new BluetoothException("Receive callback on unexpected device: " + gatt);
        }
        return connection;
    }

    private class InternalBluetoothGattCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGattWrapper gatt, int status, int newState) {
            BluetoothGattConnection connection;
            BluetoothDevice device = gatt.getDevice();
            switch (newState) {
                case BluetoothGatt.STATE_CONNECTED: {
                    connection = mConnections.get(gatt);
                    if (connection == null) {
                        Log.w(TAG, String.format(
                                "Received unexpected successful connection for dev %s! Ignoring.",
                                device));
                        break;
                    }

                    Operation<BluetoothGattConnection> operation =
                            new Operation<>(OperationType.CONNECT, device);
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        mConnections.remove(gatt);
                        gatt.disconnect();
                        gatt.close();
                        mBluetoothOperationExecutor.notifyCompletion(operation, status, null);
                        break;
                    }

                    // Process connection options
                    ConnectionOptions options = connection.getConnectionOptions();
                    Optional<Integer> mtuOption = options.mtu();
                    if (mtuOption.isPresent()) {
                        // Requesting MTU and waiting for MTU callback.
                        boolean success = gatt.requestMtu(mtuOption.get());
                        if (!success) {
                            mBluetoothOperationExecutor.notifyFailure(operation,
                                    new BluetoothException(String.format(Locale.US,
                                            "Failed to request MTU of %d for dev %s: "
                                                    + "returned false.",
                                            mtuOption.get(), device)));
                            // Make sure to clean connection.
                            mConnections.remove(gatt);
                            gatt.disconnect();
                            gatt.close();
                        }
                        break;
                    }

                    // Connection successful
                    connection.onConnected();
                    mBluetoothOperationExecutor.notifyCompletion(operation, status, connection);
                    break;
                }
                case BluetoothGatt.STATE_DISCONNECTED: {
                    connection = mConnections.remove(gatt);
                    if (connection == null) {
                        Log.w(TAG, String.format("Received unexpected disconnection"
                                + " for device %s! Ignoring.", device));
                        break;
                    }
                    if (!connection.isConnected()) {
                        // This is a failed connection attempt
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            // This is weird... considering this as a failure
                            Log.w(TAG, String.format(
                                    "Received a success for a failed connection "
                                            + "attempt for device %s! Ignoring.", device));
                            status = BluetoothGatt.GATT_FAILURE;
                        }
                        mBluetoothOperationExecutor
                                .notifyCompletion(new Operation<BluetoothGattConnection>(
                                        OperationType.CONNECT, device), status, null);
                        // Clean Gatt object in every case.
                        gatt.disconnect();
                        gatt.close();
                        break;
                    }
                    connection.onClosed();
                    mBluetoothOperationExecutor.notifyCompletion(
                            new Operation<>(OperationType.DISCONNECT, device), status);
                    break;
                }
                default:
                    Log.e(TAG, "Unexpected connection state: " + newState);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGattWrapper gatt, int mtu, int status) {
            BluetoothGattConnection connection = mConnections.get(gatt);
            BluetoothDevice device = gatt.getDevice();
            if (connection == null) {
                Log.w(TAG, String.format(
                        "Received unexpected MTU change for device %s! Ignoring.", device));
                return;
            }
            if (connection.isConnected()) {
                // This is the callback for the deprecated BluetoothGattConnection.requestMtu.
                mBluetoothOperationExecutor.notifyCompletion(
                        new Operation<>(OperationType.CHANGE_MTU, gatt), status, mtu);
            } else {
                // This is the callback when requesting MTU right after connecting.
                connection.onConnected();
                mBluetoothOperationExecutor.notifyCompletion(
                        new Operation<>(OperationType.CONNECT, device), status, connection);
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, String.format(
                            "%s responds MTU change failed, status %s.", device, status));
                    // Clean connection if it's failed.
                    mConnections.remove(gatt);
                    gatt.disconnect();
                    gatt.close();
                    return;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGattWrapper gatt, int status) {
            mBluetoothOperationExecutor.notifyCompletion(
                    new Operation<Void>(OperationType.DISCOVER_SERVICES_INTERNAL, gatt), status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGattWrapper gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            mBluetoothOperationExecutor.notifyCompletion(
                    new Operation<byte[]>(OperationType.READ_CHARACTERISTIC, gatt, characteristic),
                    status, characteristic.getValue());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGattWrapper gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            mBluetoothOperationExecutor.notifyCompletion(new Operation<Void>(
                    OperationType.WRITE_CHARACTERISTIC, gatt, characteristic), status);
        }

        @Override
        public void onDescriptorRead(BluetoothGattWrapper gatt, BluetoothGattDescriptor descriptor,
                int status) {
            mBluetoothOperationExecutor.notifyCompletion(
                    new Operation<byte[]>(OperationType.READ_DESCRIPTOR, gatt, descriptor), status,
                    descriptor.getValue());
        }

        @Override
        public void onDescriptorWrite(BluetoothGattWrapper gatt, BluetoothGattDescriptor descriptor,
                int status) {
            Log.d(TAG, String.format("onDescriptorWrite %s, %s, %d",
                    gatt.getDevice(), descriptor.getUuid(), status));
            mBluetoothOperationExecutor.notifyCompletion(
                    new Operation<Void>(OperationType.WRITE_DESCRIPTOR, gatt, descriptor), status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGattWrapper gatt, int rssi, int status) {
            mBluetoothOperationExecutor.notifyCompletion(
                    new Operation<Integer>(OperationType.READ_RSSI, gatt), status, rssi);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGattWrapper gatt, int status) {
            mBluetoothOperationExecutor.notifyCompletion(
                    new Operation<Void>(OperationType.WRITE_RELIABLE, gatt), status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGattWrapper gatt,
                BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (value == null) {
                // Value is not supposed to be null, but just to be safe...
                value = new byte[0];
            }
            Log.d(TAG, String.format("Characteristic %s changed, Gatt device: %s",
                    characteristic.getUuid(), gatt.getDevice()));
            try {
                getConnectionByGatt(gatt).onCharacteristicChanged(characteristic, value);
            } catch (BluetoothException e) {
                Log.e(TAG, "Error in onCharacteristicChanged", e);
            }
        }
    }

    private class InternalScanCallback extends ScanCallback {

        @Override
        public void onScanFailed(int errorCode) {
            String errorMessage;
            switch (errorCode) {
                case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                    errorMessage = "SCAN_FAILED_ALREADY_STARTED";
                    break;
                case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    errorMessage = "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED";
                    break;
                case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                    errorMessage = "SCAN_FAILED_FEATURE_UNSUPPORTED";
                    break;
                case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                    errorMessage = "SCAN_FAILED_INTERNAL_ERROR";
                    break;
                default:
                    errorMessage = "Unknown error code - " + errorCode;
            }
            mBluetoothOperationExecutor.notifyFailure(
                    new Operation<BluetoothDevice>(OperationType.SCAN),
                    new BluetoothException("Scan failed: " + errorMessage));
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            mBluetoothOperationExecutor.notifySuccess(
                    new Operation<BluetoothDevice>(OperationType.SCAN), result.getDevice());
        }
    }

    /**
     * Options for {@link #connect}.
     */
    public static class ConnectionOptions {

        private boolean mAutoConnect;
        private long mConnectionTimeoutMillis;
        private Optional<Integer> mConnectionPriority;
        private Optional<Integer> mMtu;

        private ConnectionOptions(boolean autoConnect, long connectionTimeoutMillis,
                Optional<Integer> connectionPriority,
                Optional<Integer> mtu) {
            this.mAutoConnect = autoConnect;
            this.mConnectionTimeoutMillis = connectionTimeoutMillis;
            this.mConnectionPriority = connectionPriority;
            this.mMtu = mtu;
        }

        boolean autoConnect() {
            return mAutoConnect;
        }

        long connectionTimeoutMillis() {
            return mConnectionTimeoutMillis;
        }

        Optional<Integer> connectionPriority() {
            return mConnectionPriority;
        }

        Optional<Integer> mtu() {
            return mMtu;
        }

        @Override
        public String toString() {
            return "ConnectionOptions{"
                    + "autoConnect=" + mAutoConnect + ", "
                    + "connectionTimeoutMillis=" + mConnectionTimeoutMillis + ", "
                    + "connectionPriority=" + mConnectionPriority + ", "
                    + "mtu=" + mMtu
                    + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof ConnectionOptions) {
                ConnectionOptions that = (ConnectionOptions) o;
                return this.mAutoConnect == that.autoConnect()
                        && this.mConnectionTimeoutMillis == that.connectionTimeoutMillis()
                        && this.mConnectionPriority.equals(that.connectionPriority())
                        && this.mMtu.equals(that.mtu());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mAutoConnect, mConnectionTimeoutMillis, mConnectionPriority, mMtu);
        }

        /**
         * Creates a builder of ConnectionOptions.
         */
        public static Builder builder() {
            return new ConnectionOptions.Builder()
                    .setAutoConnect(false)
                    .setConnectionTimeoutMillis(TimeUnit.SECONDS.toMillis(5));
        }

        /**
         * Builder for {@link ConnectionOptions}.
         */
        public static class Builder {

            private boolean mAutoConnect;
            private long mConnectionTimeoutMillis;
            private Optional<Integer> mConnectionPriority = Optional.empty();
            private Optional<Integer> mMtu = Optional.empty();

            /**
             * See {@link android.bluetooth.BluetoothDevice#connectGatt}.
             */
            public Builder setAutoConnect(boolean autoConnect) {
                this.mAutoConnect = autoConnect;
                return this;
            }

            /**
             * See {@link android.bluetooth.BluetoothGatt#requestConnectionPriority(int)}.
             */
            public Builder setConnectionPriority(int connectionPriority) {
                this.mConnectionPriority = Optional.of(connectionPriority);
                return this;
            }

            /**
             * See {@link android.bluetooth.BluetoothGatt#requestMtu(int)}.
             */
            public Builder setMtu(int mtu) {
                this.mMtu = Optional.of(mtu);
                return this;
            }

            /**
             * Sets the timeout for the GATT connection.
             */
            public Builder setConnectionTimeoutMillis(long connectionTimeoutMillis) {
                this.mConnectionTimeoutMillis = connectionTimeoutMillis;
                return this;
            }

            /**
             * Builds ConnectionOptions.
             */
            public ConnectionOptions build() {
                return new ConnectionOptions(mAutoConnect, mConnectionTimeoutMillis,
                        mConnectionPriority, mMtu);
            }
        }
    }
}

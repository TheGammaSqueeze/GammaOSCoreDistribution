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

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothStatusCodes;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.server.nearby.common.bluetooth.BluetoothConsts;
import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.BluetoothGattException;
import com.android.server.nearby.common.bluetooth.BluetoothTimeoutException;
import com.android.server.nearby.common.bluetooth.ReservedUuids;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattHelper.ConnectionOptions;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattHelper.OperationType;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothDevice;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothGattWrapper;
import com.android.server.nearby.common.bluetooth.util.BluetoothGattUtils;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.Operation;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.SynchronousOperation;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Gatt connection to a Bluetooth device.
 */
public class BluetoothGattConnection implements AutoCloseable {

    private static final String TAG = BluetoothGattConnection.class.getSimpleName();

    @VisibleForTesting
    static final long OPERATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(1);
    @VisibleForTesting
    static final long SLOW_OPERATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);

    @VisibleForTesting
    static final int GATT_INTERNAL_ERROR = 129;
    @VisibleForTesting
    static final int GATT_ERROR = 133;

    private final BluetoothGattWrapper mGatt;
    private final BluetoothOperationExecutor mBluetoothOperationExecutor;
    private final ConnectionOptions mConnectionOptions;

    private volatile boolean mServicesDiscovered = false;

    private volatile boolean mIsConnected = false;

    private volatile int mMtu = BluetoothConsts.DEFAULT_MTU;

    private final ConcurrentMap<BluetoothGattCharacteristic, ChangeObserver> mChangeObservers =
            new ConcurrentHashMap<>();

    private final List<ConnectionCloseListener> mCloseListeners = new ArrayList<>();

    private long mOperationTimeoutMillis = OPERATION_TIMEOUT_MILLIS;

    BluetoothGattConnection(
            BluetoothGattWrapper gatt,
            BluetoothOperationExecutor bluetoothOperationExecutor,
            ConnectionOptions connectionOptions) {
        mGatt = gatt;
        mBluetoothOperationExecutor = bluetoothOperationExecutor;
        mConnectionOptions = connectionOptions;
    }

    /**
     * Set operation timeout.
     */
    public void setOperationTimeout(long timeoutMillis) {
        Preconditions.checkArgument(timeoutMillis > 0, "invalid time out value");
        mOperationTimeoutMillis = timeoutMillis;
    }

    /**
     * Returns connected device.
     */
    public BluetoothDevice getDevice() {
        return mGatt.getDevice();
    }

    public ConnectionOptions getConnectionOptions() {
        return mConnectionOptions;
    }

    public boolean isConnected() {
        return mIsConnected;
    }

    /**
     * Get service.
     */
    public BluetoothGattService getService(UUID uuid) throws BluetoothException {
        Log.d(TAG, String.format("Getting service %s.", uuid));
        if (!mServicesDiscovered) {
            discoverServices();
        }
        BluetoothGattService match = null;
        for (BluetoothGattService service : mGatt.getServices()) {
            if (service.getUuid().equals(uuid)) {
                if (match != null) {
                    throw new BluetoothException(
                            String.format("More than one service %s found on device %s.",
                                    uuid,
                                    mGatt.getDevice()));
                }
                match = service;
            }
        }
        if (match == null) {
            throw new BluetoothException(String.format("Service %s not found on device %s.",
                    uuid,
                    mGatt.getDevice()));
        }
        Log.d(TAG, "Service found.");
        return match;
    }

    /**
     * Returns a list of all characteristics under a given service UUID.
     */
    private List<BluetoothGattCharacteristic> getCharacteristics(UUID serviceUuid)
            throws BluetoothException {
        if (!mServicesDiscovered) {
            discoverServices();
        }
        ArrayList<BluetoothGattCharacteristic> characteristics = new ArrayList<>();
        for (BluetoothGattService service : mGatt.getServices()) {
            // Add all characteristics under this service if its service UUID matches.
            if (service.getUuid().equals(serviceUuid)) {
                characteristics.addAll(service.getCharacteristics());
            }
        }
        return characteristics;
    }

    /**
     * Get characteristic.
     */
    public BluetoothGattCharacteristic getCharacteristic(UUID serviceUuid,
            UUID characteristicUuid) throws BluetoothException {
        Log.d(TAG, String.format("Getting characteristic %s on service %s.", characteristicUuid,
                serviceUuid));
        BluetoothGattCharacteristic match = null;
        for (BluetoothGattCharacteristic characteristic : getCharacteristics(serviceUuid)) {
            if (characteristic.getUuid().equals(characteristicUuid)) {
                if (match != null) {
                    throw new BluetoothException(String.format(
                            "More than one characteristic %s found on service %s on device %s.",
                            characteristicUuid,
                            serviceUuid,
                            mGatt.getDevice()));
                }
                match = characteristic;
            }
        }
        if (match == null) {
            throw new BluetoothException(String.format(
                    "Characteristic %s not found on service %s of device %s.",
                    characteristicUuid,
                    serviceUuid,
                    mGatt.getDevice()));
        }
        Log.d(TAG, "Characteristic found.");
        return match;
    }

    /**
     * Get descriptor.
     */
    public BluetoothGattDescriptor getDescriptor(UUID serviceUuid,
            UUID characteristicUuid, UUID descriptorUuid) throws BluetoothException {
        Log.d(TAG, String.format("Getting descriptor %s on characteristic %s on service %s.",
                descriptorUuid, characteristicUuid, serviceUuid));
        BluetoothGattDescriptor match = null;
        for (BluetoothGattDescriptor descriptor :
                getCharacteristic(serviceUuid, characteristicUuid).getDescriptors()) {
            if (descriptor.getUuid().equals(descriptorUuid)) {
                if (match != null) {
                    throw new BluetoothException(String.format("More than one descriptor %s found "
                                    + "on characteristic %s service %s on device %s.",
                            descriptorUuid,
                            characteristicUuid,
                            serviceUuid,
                            mGatt.getDevice()));
                }
                match = descriptor;
            }
        }
        if (match == null) {
            throw new BluetoothException(String.format(
                    "Descriptor %s not found on characteristic %s on service %s of device %s.",
                    descriptorUuid,
                    characteristicUuid,
                    serviceUuid,
                    mGatt.getDevice()));
        }
        Log.d(TAG, "Descriptor found.");
        return match;
    }

    /**
     * Discover services.
     */
    public void discoverServices() throws BluetoothException {
        mBluetoothOperationExecutor.execute(
                new SynchronousOperation<Void>(OperationType.DISCOVER_SERVICES) {
                    @Nullable
                    @Override
                    public Void call() throws BluetoothException {
                        if (mServicesDiscovered) {
                            return null;
                        }
                        boolean forceRefresh = false;
                        try {
                            discoverServicesInternal();
                        } catch (BluetoothException e) {
                            if (!(e instanceof BluetoothGattException)) {
                                throw e;
                            }
                            int errorCode = ((BluetoothGattException) e).getGattErrorCode();
                            if (errorCode != GATT_ERROR && errorCode != GATT_INTERNAL_ERROR) {
                                throw e;
                            }
                            Log.e(TAG, e.getMessage()
                                    + "\n Ignore the gatt error for post MNC apis and force "
                                    + "a refresh");
                            forceRefresh = true;
                        }

                        forceRefreshServiceCacheIfNeeded(forceRefresh);

                        mServicesDiscovered = true;

                        return null;
                    }
                });
    }

    private void discoverServicesInternal() throws BluetoothException {
        Log.i(TAG, "Starting services discovery.");
        long startTimeMillis = System.currentTimeMillis();
        try {
            mBluetoothOperationExecutor.execute(
                    new Operation<Void>(OperationType.DISCOVER_SERVICES_INTERNAL, mGatt) {
                        @Override
                        public void run() throws BluetoothException {
                            boolean success = mGatt.discoverServices();
                            if (!success) {
                                throw new BluetoothException(
                                        "gatt.discoverServices returned false.");
                            }
                        }
                    },
                    SLOW_OPERATION_TIMEOUT_MILLIS);
            Log.i(TAG, String.format("Services discovered successfully in %s ms.",
                    System.currentTimeMillis() - startTimeMillis));
        } catch (BluetoothException e) {
            if (e instanceof BluetoothGattException) {
                throw new BluetoothGattException(String.format(
                        "Failed to discover services on device: %s.",
                        mGatt.getDevice()), ((BluetoothGattException) e).getGattErrorCode(), e);
            } else {
                throw new BluetoothException(String.format(
                        "Failed to discover services on device: %s.",
                        mGatt.getDevice()), e);
            }
        }
    }

    private boolean hasDynamicServices() {
        BluetoothGattService gattService =
                mGatt.getService(ReservedUuids.Services.GENERIC_ATTRIBUTE);
        if (gattService != null) {
            BluetoothGattCharacteristic serviceChange =
                    gattService.getCharacteristic(ReservedUuids.Characteristics.SERVICE_CHANGE);
            if (serviceChange != null) {
                return true;
            }
        }

        // Check whether the server contains a self defined service dynamic characteristic.
        gattService = mGatt.getService(BluetoothConsts.SERVICE_DYNAMIC_SERVICE);
        if (gattService != null) {
            BluetoothGattCharacteristic serviceChange =
                    gattService.getCharacteristic(BluetoothConsts.SERVICE_DYNAMIC_CHARACTERISTIC);
            if (serviceChange != null) {
                return true;
            }
        }

        return false;
    }

    private void forceRefreshServiceCacheIfNeeded(boolean forceRefresh) throws BluetoothException {
        if (mGatt.getDevice().getBondState() != BluetoothDevice.BOND_BONDED) {
            // Device is not bonded, so services should not have been cached.
            return;
        }

        if (!forceRefresh && !hasDynamicServices()) {
            return;
        }
        Log.i(TAG, "Forcing a refresh of local cache of GATT services");
        boolean success = mGatt.refresh();
        if (!success) {
            throw new BluetoothException("gatt.refresh returned false.");
        }
        discoverServicesInternal();
    }

    /**
     * Read characteristic.
     */
    public byte[] readCharacteristic(UUID serviceUuid, UUID characteristicUuid)
            throws BluetoothException {
        return readCharacteristic(getCharacteristic(serviceUuid, characteristicUuid));
    }

    /**
     * Read characteristic.
     */
    public byte[] readCharacteristic(final BluetoothGattCharacteristic characteristic)
            throws BluetoothException {
        try {
            return mBluetoothOperationExecutor.executeNonnull(
                    new Operation<byte[]>(OperationType.READ_CHARACTERISTIC, mGatt,
                            characteristic) {
                        @Override
                        public void run() throws BluetoothException {
                            boolean success = mGatt.readCharacteristic(characteristic);
                            if (!success) {
                                throw new BluetoothException(
                                        "gatt.readCharacteristic returned false.");
                            }
                        }
                    },
                    mOperationTimeoutMillis);
        } catch (BluetoothException e) {
            throw new BluetoothException(String.format(
                    "Failed to read %s on device %s.",
                    BluetoothGattUtils.toString(characteristic),
                    mGatt.getDevice()), e);
        }
    }

    /**
     * Writes Characteristic.
     */
    public void writeCharacteristic(UUID serviceUuid, UUID characteristicUuid, byte[] value)
            throws BluetoothException {
        writeCharacteristic(getCharacteristic(serviceUuid, characteristicUuid), value);
    }

    /**
     * Writes Characteristic.
     */
    public void writeCharacteristic(final BluetoothGattCharacteristic characteristic,
            final byte[] value) throws BluetoothException {
        Log.d(TAG, String.format("Writing %d bytes on %s on device %s.",
                value.length,
                BluetoothGattUtils.toString(characteristic),
                mGatt.getDevice()));
        if ((characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) {
            throw new BluetoothException(String.format("%s is not writable!", characteristic));
        }
        try {
            mBluetoothOperationExecutor.execute(
                    new Operation<Void>(OperationType.WRITE_CHARACTERISTIC, mGatt, characteristic) {
                        @Override
                        public void run() throws BluetoothException {
                            int writeCharacteristicResponseCode = mGatt.writeCharacteristic(
                                    characteristic, value, characteristic.getWriteType());
                            if (writeCharacteristicResponseCode != BluetoothStatusCodes.SUCCESS) {
                                throw new BluetoothException(
                                        "gatt.writeCharacteristic returned "
                                        + writeCharacteristicResponseCode);
                            }
                        }
                    },
                    mOperationTimeoutMillis);
        } catch (BluetoothException e) {
            throw new BluetoothException(String.format(
                    "Failed to write %s on device %s.",
                    BluetoothGattUtils.toString(characteristic),
                    mGatt.getDevice()), e);
        }
        Log.d(TAG, "Writing characteristic done.");
    }

    /**
     * Reads descriptor.
     */
    public byte[] readDescriptor(UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid)
            throws BluetoothException {
        return readDescriptor(getDescriptor(serviceUuid, characteristicUuid, descriptorUuid));
    }

    /**
     * Reads descriptor.
     */
    public byte[] readDescriptor(final BluetoothGattDescriptor descriptor)
            throws BluetoothException {
        try {
            return mBluetoothOperationExecutor.executeNonnull(
                    new Operation<byte[]>(OperationType.READ_DESCRIPTOR, mGatt, descriptor) {
                        @Override
                        public void run() throws BluetoothException {
                            boolean success = mGatt.readDescriptor(descriptor);
                            if (!success) {
                                throw new BluetoothException("gatt.readDescriptor returned false.");
                            }
                        }
                    },
                    mOperationTimeoutMillis);
        } catch (BluetoothException e) {
            throw new BluetoothException(String.format(
                    "Failed to read %s on %s on device %s.",
                    descriptor.getUuid(),
                    BluetoothGattUtils.toString(descriptor),
                    mGatt.getDevice()), e);
        }
    }

    /**
     * Writes descriptor.
     */
    public void writeDescriptor(UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid,
            byte[] value) throws BluetoothException {
        writeDescriptor(getDescriptor(serviceUuid, characteristicUuid, descriptorUuid), value);
    }

    /**
     * Writes descriptor.
     */
    public void writeDescriptor(final BluetoothGattDescriptor descriptor, final byte[] value)
            throws BluetoothException {
        Log.d(TAG, String.format(
                "Writing %d bytes on %s on device %s.",
                value.length,
                BluetoothGattUtils.toString(descriptor),
                mGatt.getDevice()));
        long startTimeMillis = System.currentTimeMillis();
        try {
            mBluetoothOperationExecutor.execute(
                    new Operation<Void>(OperationType.WRITE_DESCRIPTOR, mGatt, descriptor) {
                        @Override
                        public void run() throws BluetoothException {
                            int writeDescriptorResponseCode = mGatt.writeDescriptor(descriptor,
                                    value);
                            if (writeDescriptorResponseCode != BluetoothStatusCodes.SUCCESS) {
                                throw new BluetoothException(
                                        "gatt.writeDescriptor returned "
                                        + writeDescriptorResponseCode);
                            }
                        }
                    },
                    mOperationTimeoutMillis);
            Log.d(TAG, String.format("Writing descriptor done in %s ms.",
                    System.currentTimeMillis() - startTimeMillis));
        } catch (BluetoothException e) {
            throw new BluetoothException(String.format(
                    "Failed to write %s on device %s.",
                    BluetoothGattUtils.toString(descriptor),
                    mGatt.getDevice()), e);
        }
    }

    /**
     * Reads remote Rssi.
     */
    public int readRemoteRssi() throws BluetoothException {
        try {
            return mBluetoothOperationExecutor.executeNonnull(
                    new Operation<Integer>(OperationType.READ_RSSI, mGatt) {
                        @Override
                        public void run() throws BluetoothException {
                            boolean success = mGatt.readRemoteRssi();
                            if (!success) {
                                throw new BluetoothException("gatt.readRemoteRssi returned false.");
                            }
                        }
                    },
                    mOperationTimeoutMillis);
        } catch (BluetoothException e) {
            throw new BluetoothException(
                    String.format("Failed to read rssi on device %s.", mGatt.getDevice()), e);
        }
    }

    public int getMtu() {
        return mMtu;
    }

    /**
     * Get max data packet size.
     */
    public int getMaxDataPacketSize() {
        // Per BT specs (3.2.9), only MTU - 3 bytes can be used to transmit data
        return mMtu - 3;
    }

    /** Set notification enabled or disabled. */
    @VisibleForTesting
    public void setNotificationEnabled(BluetoothGattCharacteristic characteristic, boolean enabled)
            throws BluetoothException {
        boolean isIndication;
        int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            isIndication = false;
        } else if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            isIndication = true;
        } else {
            throw new BluetoothException(String.format(
                    "%s on device %s supports neither notifications nor indications.",
                    BluetoothGattUtils.toString(characteristic),
                    mGatt.getDevice()));
        }
        BluetoothGattDescriptor clientConfigDescriptor =
                characteristic.getDescriptor(
                        ReservedUuids.Descriptors.CLIENT_CHARACTERISTIC_CONFIGURATION);
        if (clientConfigDescriptor == null) {
            throw new BluetoothException(String.format(
                    "%s on device %s is missing client config descriptor.",
                    BluetoothGattUtils.toString(characteristic),
                    mGatt.getDevice()));
        }
        long startTime = System.currentTimeMillis();
        Log.d(TAG, String.format("%s %s on characteristic %s.", enabled ? "Enabling" : "Disabling",
                isIndication ? "indication" : "notification", characteristic.getUuid()));

        if (enabled) {
            mGatt.setCharacteristicNotification(characteristic, enabled);
        }
        writeDescriptor(clientConfigDescriptor,
                enabled
                        ? (isIndication
                        ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE :
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        if (!enabled) {
            mGatt.setCharacteristicNotification(characteristic, enabled);
        }

        Log.d(TAG, String.format("Done in %d ms.", System.currentTimeMillis() - startTime));
    }

    /**
     * Enables notification.
     */
    public ChangeObserver enableNotification(UUID serviceUuid, UUID characteristicUuid)
            throws BluetoothException {
        return enableNotification(getCharacteristic(serviceUuid, characteristicUuid));
    }

    /**
     * Enables notification.
     */
    public ChangeObserver enableNotification(final BluetoothGattCharacteristic characteristic)
            throws BluetoothException {
        return mBluetoothOperationExecutor.executeNonnull(
                new SynchronousOperation<ChangeObserver>(
                        OperationType.NOTIFICATION_CHANGE,
                        characteristic) {
                    @Override
                    public ChangeObserver call() throws BluetoothException {
                        ChangeObserver changeObserver = new ChangeObserver();
                        mChangeObservers.put(characteristic, changeObserver);
                        setNotificationEnabled(characteristic, true);
                        return changeObserver;
                    }
                });
    }

    /**
     * Disables notification.
     */
    public void disableNotification(UUID serviceUuid, UUID characteristicUuid)
            throws BluetoothException {
        disableNotification(getCharacteristic(serviceUuid, characteristicUuid));
    }

    /**
     * Disables notification.
     */
    public void disableNotification(final BluetoothGattCharacteristic characteristic)
            throws BluetoothException {
        mBluetoothOperationExecutor.execute(
                new SynchronousOperation<Void>(
                        OperationType.NOTIFICATION_CHANGE,
                        characteristic) {
                    @Nullable
                    @Override
                    public Void call() throws BluetoothException {
                        setNotificationEnabled(characteristic, false);
                        mChangeObservers.remove(characteristic);
                        return null;
                    }
                });
    }

    /**
     * Adds a close listener.
     */
    public void addCloseListener(ConnectionCloseListener listener) {
        mCloseListeners.add(listener);
        if (!mIsConnected) {
            listener.onClose();
            return;
        }
    }

    /**
     * Removes a close listener.
     */
    public void removeCloseListener(ConnectionCloseListener listener) {
        mCloseListeners.remove(listener);
    }

    /** onCharacteristicChanged callback. */
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic, byte[] value) {
        ChangeObserver observer = mChangeObservers.get(characteristic);
        if (observer == null) {
            return;
        }
        observer.onValueChange(value);
    }

    @Override
    public void close() throws BluetoothException {
        Log.d(TAG, "close");
        try {
            if (!mIsConnected) {
                // Don't call disconnect on a closed connection, since Android framework won't
                // provide any feedback.
                return;
            }
            mBluetoothOperationExecutor.execute(
                    new Operation<Void>(OperationType.DISCONNECT, mGatt.getDevice()) {
                        @Override
                        public void run() throws BluetoothException {
                            mGatt.disconnect();
                        }
                    }, mOperationTimeoutMillis);
        } finally {
            mGatt.close();
        }
    }

    /** onConnected callback. */
    public void onConnected() {
        Log.d(TAG, "onConnected");
        mIsConnected = true;
    }

    /** onClosed callback. */
    public void onClosed() {
        Log.d(TAG, "onClosed");
        if (!mIsConnected) {
            return;
        }
        mIsConnected = false;
        for (ConnectionCloseListener listener : mCloseListeners) {
            listener.onClose();
        }
        mGatt.close();
    }

    /**
     * Observer to wait or be called back when value change.
     */
    public static class ChangeObserver {

        private final BlockingDeque<byte[]> mValues = new LinkedBlockingDeque<byte[]>();

        @GuardedBy("mValues")
        @Nullable
        private volatile CharacteristicChangeListener mListener;

        /**
         * Set listener.
         */
        public void setListener(@Nullable CharacteristicChangeListener listener) {
            synchronized (mValues) {
                mListener = listener;
                if (listener != null) {
                    byte[] value;
                    while ((value = mValues.poll()) != null) {
                        listener.onValueChange(value);
                    }
                }
            }
        }

        /**
         * onValueChange callback.
         */
        public void onValueChange(byte[] newValue) {
            synchronized (mValues) {
                CharacteristicChangeListener listener = mListener;
                if (listener == null) {
                    mValues.add(newValue);
                } else {
                    listener.onValueChange(newValue);
                }
            }
        }

        /**
         * Waits for update for a given time.
         */
        public byte[] waitForUpdate(long timeoutMillis) throws BluetoothException {
            byte[] result;
            try {
                result = mValues.poll(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BluetoothException("Operation interrupted.");
            }
            if (result == null) {
                throw new BluetoothTimeoutException(
                        String.format("Operation timed out after %dms", timeoutMillis));
            }
            return result;
        }
    }

    /**
     * Listener for characteristic data changes over notifications or indications.
     */
    public interface CharacteristicChangeListener {

        /**
         * onValueChange callback.
         */
        void onValueChange(byte[] newValue);
    }

    /**
     * Listener for connection close events.
     */
    public interface ConnectionCloseListener {

        /**
         * onClose callback.
         */
        void onClose();
    }
}

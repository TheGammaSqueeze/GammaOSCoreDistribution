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
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.BluetoothGattException;
import com.android.server.nearby.common.bluetooth.ReservedUuids;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothDevice;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.Operation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Connection to a bluetooth LE device over Gatt.
 */
@TargetApi(18)
public class BluetoothGattServerConnection implements Closeable {
    @SuppressWarnings("unused")
    private static final String TAG = BluetoothGattServerConnection.class.getSimpleName();

    /** See {@link BluetoothGattDescriptor#DISABLE_NOTIFICATION_VALUE}. */
    private static final short DISABLE_NOTIFICATION_VALUE = 0x0000;

    /** See {@link BluetoothGattDescriptor#ENABLE_NOTIFICATION_VALUE}. */
    private static final short ENABLE_NOTIFICATION_VALUE = 0x0001;

    /** See {@link BluetoothGattDescriptor#ENABLE_INDICATION_VALUE}. */
    private static final short ENABLE_INDICATION_VALUE = 0x0002;

    /** Default MTU when value is unknown. */
    public static final int DEFAULT_MTU = 23;

    @VisibleForTesting
    static final long OPERATION_TIMEOUT = TimeUnit.SECONDS.toMillis(1);

    /** Notification types as defined by the BLE spec vol 4, sec G, part 3.3.3.3 */
    public enum NotificationType {
        NOTIFICATION,
        INDICATION
    }

    /** BT operation types that can be in flight. */
    public enum OperationType {
        SEND_NOTIFICATION
    }

    private final Map<ScopedKey, Object> mContextValues = new HashMap<ScopedKey, Object>();
    private final List<Listener> mCloseListeners = new ArrayList<Listener>();

    private final BluetoothGattServerHelper mBluetoothGattServerHelper;
    private final BluetoothDevice mBluetoothDevice;

    @VisibleForTesting
    BluetoothOperationExecutor mBluetoothOperationScheduler =
            new BluetoothOperationExecutor(1);

    /** Stores pending writes. For each UUID, we store an offset and a byte[] of data. */
    @VisibleForTesting
    final Map<BluetoothGattServlet, SortedMap<Integer, byte[]>> mQueuedCharacteristicWrites =
            new HashMap<BluetoothGattServlet, SortedMap<Integer, byte[]>>();

    @VisibleForTesting
    final Map<BluetoothGattCharacteristic, Notifier> mRegisteredNotifications =
            new HashMap<BluetoothGattCharacteristic, Notifier>();

    private final Map<BluetoothGattCharacteristic, BluetoothGattServlet> mServlets;

    public BluetoothGattServerConnection(
            BluetoothGattServerHelper bluetoothGattServerHelper,
            BluetoothDevice device,
            BluetoothGattServerConfig serverConfig) {
        mBluetoothGattServerHelper = bluetoothGattServerHelper;
        mBluetoothDevice = device;
        mServlets = serverConfig.getServlets();
    }

    public void setContextValue(Object scope, String key, @Nullable Object value) {
        mContextValues.put(new ScopedKey(scope, key), value);
    }

    @Nullable
    public Object getContextValue(Object scope, String key) {
        return mContextValues.get(new ScopedKey(scope, key));
    }

    public BluetoothDevice getDevice() {
        return mBluetoothDevice;
    }

    public int getMtu() {
        return DEFAULT_MTU;
    }

    public int getMaxDataPacketSize() {
        // Per BT specs (3.2.9), only MTU - 3 bytes can be used to transmit data
        return getMtu() - 3;
    }

    public void addCloseListener(Listener listener) {
        synchronized (mCloseListeners) {
            mCloseListeners.add(listener);
        }
    }

    public void removeCloseListener(Listener listener) {
        synchronized (mCloseListeners) {
            mCloseListeners.remove(listener);
        }
    }

    private BluetoothGattServlet getServlet(BluetoothGattCharacteristic characteristic)
            throws BluetoothGattException {
        BluetoothGattServlet servlet = mServlets.get(characteristic);
        if (servlet == null) {
            throw new BluetoothGattException(
                    String.format("No handler registered for characteristic %s.",
                            characteristic.getUuid()),
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
        }
        return servlet;
    }

    public byte[] readCharacteristic(int offset, BluetoothGattCharacteristic characteristic)
            throws BluetoothGattException {
        return getServlet(characteristic).read(this, offset);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic,
            boolean preparedWrite,
            int offset, byte[] value) throws BluetoothGattException {
        Log.d(TAG, String.format(
                "Received %d bytes at offset %d on %s from device %s, prepareWrite=%s.",
                value.length,
                offset,
                BluetoothGattUtils.toString(characteristic),
                mBluetoothDevice,
                preparedWrite));
        BluetoothGattServlet servlet = getServlet(characteristic);
        if (preparedWrite) {
            SortedMap<Integer, byte[]> bytePackets = mQueuedCharacteristicWrites.get(servlet);
            if (bytePackets == null) {
                bytePackets = new TreeMap<Integer, byte[]>();
                mQueuedCharacteristicWrites.put(servlet, bytePackets);
            }
            bytePackets.put(offset, value);
            return;
        }

        Log.d(TAG, servlet.toString());
        servlet.write(this, offset, value);
    }

    public byte[] readDescriptor(int offset, BluetoothGattDescriptor descriptor)
            throws BluetoothGattException {
        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
        if (characteristic == null) {
            throw new BluetoothGattException(String.format(
                    "Descriptor %s not associated with a characteristics!",
                    BluetoothGattUtils.toString(descriptor)), BluetoothGatt.GATT_FAILURE);
        }
        return getServlet(characteristic).readDescriptor(this, descriptor, offset);
    }

    public void writeDescriptor(
            BluetoothGattDescriptor descriptor,
            boolean preparedWrite,
            int offset,
            byte[] value) throws BluetoothGattException {
        Log.d(TAG, String.format(
                "Received %d bytes at offset %d on %s from device %s, prepareWrite=%s.",
                value.length,
                offset,
                BluetoothGattUtils.toString(descriptor),
                mBluetoothDevice,
                preparedWrite));
        if (preparedWrite) {
            throw new BluetoothGattException(
                    String.format("Prepare write not supported for descriptor %s.", descriptor),
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
        }

        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
        if (characteristic == null) {
            throw new BluetoothGattException(String.format(
                    "Descriptor %s not associated with a characteristics!",
                    BluetoothGattUtils.toString(descriptor)), BluetoothGatt.GATT_FAILURE);
        }
        BluetoothGattServlet servlet = getServlet(characteristic);
        if (descriptor.getUuid().equals(
                ReservedUuids.Descriptors.CLIENT_CHARACTERISTIC_CONFIGURATION)) {
            handleCharacteristicConfigurationChange(characteristic, servlet, offset, value);
            return;
        }
        servlet.writeDescriptor(this, descriptor, offset, value);
    }

    private void handleCharacteristicConfigurationChange(
            final BluetoothGattCharacteristic characteristic, BluetoothGattServlet servlet,
            int offset,
            byte[] value)
            throws BluetoothGattException {
        if (offset != 0) {
            throw new BluetoothGattException(String.format(
                    "Offset should be 0 when changing the client characteristic config: %d.",
                    offset),
                    BluetoothGatt.GATT_INVALID_OFFSET);
        }
        if (value.length != 2) {
            throw new BluetoothGattException(String.format(
                    "Value 0x%s is undefined for the client characteristic config",
                    BaseEncoding.base16().encode(value)),
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH);
        }

        boolean notificationRegistered = mRegisteredNotifications.containsKey(characteristic);
        Notifier notifier;
        switch (toShort(value)) {
            case ENABLE_NOTIFICATION_VALUE:
                if (!notificationRegistered) {
                    notifier = new Notifier() {
                        @Override
                        public void notify(byte[] data) throws BluetoothException {
                            sendNotification(characteristic, NotificationType.NOTIFICATION, data);
                        }
                    };
                    mRegisteredNotifications.put(characteristic, notifier);
                    servlet.enableNotification(this, notifier);
                }
                break;
            case ENABLE_INDICATION_VALUE:
                if (!notificationRegistered) {
                    notifier = new Notifier() {
                        @Override
                        public void notify(byte[] data) throws BluetoothException {
                            sendNotification(characteristic, NotificationType.INDICATION, data);
                        }
                    };
                    mRegisteredNotifications.put(characteristic, notifier);
                    servlet.enableNotification(this, notifier);
                }
                break;
            case DISABLE_NOTIFICATION_VALUE:
                // Note: this disables notifications or indications.
                if (notificationRegistered) {
                    notifier = mRegisteredNotifications.remove(characteristic);
                    if (notifier == null) {
                        return; // this is not supposed to happen
                    }
                    servlet.disableNotification(this, notifier);
                }
                break;
            default:
                throw new BluetoothGattException(String.format(
                        "Value 0x%s is undefined for the client characteristic config",
                        BaseEncoding.base16().encode(value)),
                        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
        }
    }

    private static short toShort(byte[] value) {
        Preconditions.checkNotNull(value);
        Preconditions.checkArgument(value.length == 2, "Length should be 2 bytes.");

        return (short) ((value[0] & 0x00FF) | (value[1] << 8));
    }

    public void executeWrite(boolean execute) throws BluetoothGattException {
        if (!execute) {
            mQueuedCharacteristicWrites.clear();
            return;
        }

        try {
            for (Entry<BluetoothGattServlet, SortedMap<Integer, byte[]>> queuedWrite :
                    mQueuedCharacteristicWrites.entrySet()) {
                BluetoothGattServlet servlet = queuedWrite.getKey();
                SortedMap<Integer, byte[]> chunks = queuedWrite.getValue();
                if (servlet == null || chunks == null) {
                    // This is not supposed to happen
                    throw new IllegalStateException();
                }
                assembleByteChunksAndHandle(servlet, chunks);
            }
        } finally {
            mQueuedCharacteristicWrites.clear();
        }
    }

    /**
     * Assembles the specified queued writes and calls the provided write handler on the assembled
     * chunks. Tries to assemble all the chunks into one write request. For example, if the content
     * of byteChunks is:
     * <code>
     * offset data_size
     * 0       10
     * 10        1
     * 11        5
     * </code>
     *
     * then this method would call <code>writeHandler.onWrite(0, byte[16])</code>
     *
     * However, if all the chunks cannot be assembled into a continuous byte[], then onWrite() will
     * be called multiple times with the largest continuous chunks. For example, if the content of
     * byteChunks is:
     * <code>
     * offset data_size
     * 10       12
     * 30        5
     * 35        9
     * </code>
     *
     * then this method would call <code>writeHandler.onWrite(10, byte[12)</code> and
     * <code>writeHandler.onWrite(30, byte[14]).
     */
    private void assembleByteChunksAndHandle(BluetoothGattServlet servlet,
            SortedMap<Integer, byte[]> byteChunks) throws BluetoothGattException {
        ByteArrayOutputStream assembledRequest = new ByteArrayOutputStream();
        Integer startWritingAtOffset = 0;

        while (!byteChunks.isEmpty()) {
            Integer offset = byteChunks.firstKey();

            if (offset.intValue() < startWritingAtOffset + assembledRequest.size()) {
                throw new BluetoothGattException(
                        "Expected offset of at least " + assembledRequest.size()
                                + ", but got offset " + offset, BluetoothGatt.GATT_INVALID_OFFSET);
            }

            // If we have a hole, then write what we've already assembled and start assembling a new
            // long write
            if (offset.intValue() > startWritingAtOffset + assembledRequest.size()) {
                servlet.write(this, startWritingAtOffset.intValue(),
                        assembledRequest.toByteArray());
                startWritingAtOffset = offset;
                assembledRequest.reset();
            }

            try {
                byte[] dataChunk = byteChunks.remove(offset);
                if (dataChunk == null) {
                    // This is not supposed to happen
                    throw new IllegalStateException();
                }
                assembledRequest.write(dataChunk);
            } catch (IOException e) {
                throw new BluetoothGattException("Error assembling request",
                        BluetoothGatt.GATT_FAILURE);
            }
        }

        // If there is anything to write, write it
        if (assembledRequest.size() > 0) {
            Preconditions.checkNotNull(startWritingAtOffset); // should never be null at this point
            servlet.write(this, startWritingAtOffset.intValue(), assembledRequest.toByteArray());
        }
    }

    private void sendNotification(final BluetoothGattCharacteristic characteristic,
            final NotificationType notificationType, final byte[] data)
            throws BluetoothException {
        mBluetoothOperationScheduler.execute(
                new Operation<Void>(OperationType.SEND_NOTIFICATION) {
                    @Override
                    public void run() throws BluetoothException {
                        mBluetoothGattServerHelper.sendNotification(mBluetoothDevice,
                                characteristic,
                                data,
                                notificationType == NotificationType.INDICATION ? true : false);
                    }
                },
                OPERATION_TIMEOUT);
    }

    @Override
    public void close() throws IOException {
        try {
            mBluetoothGattServerHelper.closeConnection(mBluetoothDevice);
        } catch (BluetoothException e) {
            throw new IOException("Failed to close connection", e);
        }
    }

    public void notifyNotificationSent(int status) {
        mBluetoothOperationScheduler.notifyCompletion(
                new Operation<Void>(OperationType.SEND_NOTIFICATION), status);
    }

    public void onClose() {
        synchronized (mCloseListeners) {
            for (Listener listener : mCloseListeners) {
                listener.onClose();
            }
        }
    }

    /** Scope/key pair to use to reference contextual values. */
    private static class ScopedKey {
        private final Object mScope;
        private final String mKey;

        ScopedKey(Object scope, String key) {
            mScope = scope;
            mKey = key;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (!(o instanceof ScopedKey)) {
                return false;
            }
            ScopedKey other = (ScopedKey) o;
            return other.mScope.equals(mScope) && other.mKey.equals(mKey);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mScope, mKey);
        }
    }

    /** Listener to be notified when the connection closes. */
    public interface Listener {
        void onClose();
    }

    /** Notifier to notify data over notification or indication. */
    public interface Notifier {
        void notify(byte[] data) throws BluetoothException;
    }
}

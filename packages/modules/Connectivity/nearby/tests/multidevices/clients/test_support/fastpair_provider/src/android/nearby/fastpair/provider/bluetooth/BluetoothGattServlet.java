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
import android.nearby.fastpair.provider.bluetooth.BluetoothGattServerConnection.Notifier;

import com.android.server.nearby.common.bluetooth.BluetoothGattException;

/** Servlet to handle GATT operations on a characteristic. */
@TargetApi(18)
public abstract class BluetoothGattServlet {
    public byte[] read(BluetoothGattServerConnection connection,
            @SuppressWarnings("unused") int offset) throws BluetoothGattException {
        throw new BluetoothGattException("Read not supported.",
                BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
    }

    public void write(BluetoothGattServerConnection connection,
            @SuppressWarnings("unused") int offset, @SuppressWarnings("unused") byte[] value)
            throws BluetoothGattException {
        throw new BluetoothGattException("Write not supported.",
                BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
    }

    public byte[] readDescriptor(BluetoothGattServerConnection connection,
            BluetoothGattDescriptor descriptor, @SuppressWarnings("unused") int offset)
            throws BluetoothGattException {
        throw new BluetoothGattException("Read not supported.",
                BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
    }

    public void writeDescriptor(BluetoothGattServerConnection connection,
            BluetoothGattDescriptor descriptor,
            @SuppressWarnings("unused") int offset, @SuppressWarnings("unused") byte[] value)
            throws BluetoothGattException {
        throw new BluetoothGattException("Write not supported.",
                BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
    }

    public void enableNotification(BluetoothGattServerConnection connection, Notifier notifier)
            throws BluetoothGattException {
        throw new BluetoothGattException("Notification not supported.",
                BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
    }

    public void disableNotification(BluetoothGattServerConnection connection, Notifier notifier)
            throws BluetoothGattException {
        throw new BluetoothGattException("Notification not supported.",
                BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
    }

    public abstract BluetoothGattCharacteristic getCharacteristic();
}

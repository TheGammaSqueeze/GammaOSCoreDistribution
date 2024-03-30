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

package com.android.server.nearby.common.bluetooth.testability.android.bluetooth;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Mockable wrapper of {@link android.bluetooth.BluetoothGattServer}.
 */
public class BluetoothGattServer {

    /** See {@link android.bluetooth.BluetoothGattServer#STATE_CONNECTED}. */
    public static final int STATE_CONNECTED = android.bluetooth.BluetoothGattServer.STATE_CONNECTED;

    /** See {@link android.bluetooth.BluetoothGattServer#STATE_DISCONNECTED}. */
    public static final int STATE_DISCONNECTED =
            android.bluetooth.BluetoothGattServer.STATE_DISCONNECTED;

    private android.bluetooth.BluetoothGattServer mWrappedInstance;

    private BluetoothGattServer(android.bluetooth.BluetoothGattServer instance) {
        mWrappedInstance = instance;
    }

    /** Wraps a Bluetooth Gatt server. */
    @Nullable
    public static BluetoothGattServer wrap(
            @Nullable android.bluetooth.BluetoothGattServer instance) {
        if (instance == null) {
            return null;
        }
        return new BluetoothGattServer(instance);
    }

    /**
     * See {@link android.bluetooth.BluetoothGattServer#connect(
     * android.bluetooth.BluetoothDevice, boolean)}
     */
    public boolean connect(BluetoothDevice device, boolean autoConnect) {
        return mWrappedInstance.connect(device.unwrap(), autoConnect);
    }

    /** See {@link android.bluetooth.BluetoothGattServer#addService(BluetoothGattService)}. */
    public boolean addService(BluetoothGattService service) {
        return mWrappedInstance.addService(service);
    }

    /** See {@link android.bluetooth.BluetoothGattServer#clearServices()}. */
    public void clearServices() {
        mWrappedInstance.clearServices();
    }

    /** See {@link android.bluetooth.BluetoothGattServer#close()}. */
    public void close() {
        mWrappedInstance.close();
    }

    /**
     * See {@link android.bluetooth.BluetoothGattServer#notifyCharacteristicChanged(
     * android.bluetooth.BluetoothDevice, BluetoothGattCharacteristic, boolean)}.
     */
    public boolean notifyCharacteristicChanged(BluetoothDevice device,
            BluetoothGattCharacteristic characteristic, boolean confirm) {
        return mWrappedInstance.notifyCharacteristicChanged(
                device.unwrap(), characteristic, confirm);
    }

    /**
     * See {@link android.bluetooth.BluetoothGattServer#sendResponse(
     * android.bluetooth.BluetoothDevice, int, int, int, byte[])}.
     */
    public void sendResponse(BluetoothDevice device, int requestId, int status, int offset,
            @Nullable byte[] value) {
        mWrappedInstance.sendResponse(device.unwrap(), requestId, status, offset, value);
    }

    /**
     * See {@link android.bluetooth.BluetoothGattServer#cancelConnection(
     * android.bluetooth.BluetoothDevice)}.
     */
    public void cancelConnection(BluetoothDevice device) {
        mWrappedInstance.cancelConnection(device.unwrap());
    }

    /** See {@link android.bluetooth.BluetoothGattServer#getService(UUID uuid)}. */
    public BluetoothGattService getService(UUID uuid) {
        return mWrappedInstance.getService(uuid);
    }
}

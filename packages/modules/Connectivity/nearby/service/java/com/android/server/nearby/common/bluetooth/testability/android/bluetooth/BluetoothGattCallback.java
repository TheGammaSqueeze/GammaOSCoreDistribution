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
import android.bluetooth.BluetoothGattDescriptor;

/**
 * Wrapper of {@link android.bluetooth.BluetoothGattCallback} that uses mockable objects.
 */
public abstract class BluetoothGattCallback {

    private final android.bluetooth.BluetoothGattCallback mWrappedBluetoothGattCallback =
            new InternalBluetoothGattCallback();

    /**
     * See {@link android.bluetooth.BluetoothGattCallback#onConnectionStateChange(
     * android.bluetooth.BluetoothGatt, int, int)}
     */
    public void onConnectionStateChange(BluetoothGattWrapper gatt, int status, int newState) {}

    /**
     * See {@link android.bluetooth.BluetoothGattCallback#onServicesDiscovered(
     * android.bluetooth.BluetoothGatt,int)}
     */
    public void onServicesDiscovered(BluetoothGattWrapper gatt, int status) {}

    /**
     * See {@link android.bluetooth.BluetoothGattCallback#onCharacteristicRead(
     * android.bluetooth.BluetoothGatt, BluetoothGattCharacteristic, int)}
     */
    public void onCharacteristicRead(BluetoothGattWrapper gatt, BluetoothGattCharacteristic
            characteristic, int status) {}

    /**
     * See {@link android.bluetooth.BluetoothGattCallback#onCharacteristicWrite(
     * android.bluetooth.BluetoothGatt, BluetoothGattCharacteristic, int)}
     */
    public void onCharacteristicWrite(BluetoothGattWrapper gatt,
            BluetoothGattCharacteristic characteristic, int status) {}

    /**
     * See {@link android.bluetooth.BluetoothGattCallback#onDescriptorRead(
     * android.bluetooth.BluetoothGatt, BluetoothGattDescriptor, int)}
     */
    public void onDescriptorRead(
            BluetoothGattWrapper gatt, BluetoothGattDescriptor descriptor, int status) {}

    /**
     * See {@link android.bluetooth.BluetoothGattCallback#onDescriptorWrite(
     * android.bluetooth.BluetoothGatt, BluetoothGattDescriptor, int)}
     */
    public void onDescriptorWrite(BluetoothGattWrapper gatt, BluetoothGattDescriptor descriptor,
            int status) {}

    /**
     * See {@link android.bluetooth.BluetoothGattCallback#onReadRemoteRssi(
     * android.bluetooth.BluetoothGatt, int, int)}
     */
    public void onReadRemoteRssi(BluetoothGattWrapper gatt, int rssi, int status) {}

    /**
     * See {@link android.bluetooth.BluetoothGattCallback#onReliableWriteCompleted(
     * android.bluetooth.BluetoothGatt, int)}
     */
    public void onReliableWriteCompleted(BluetoothGattWrapper gatt, int status) {}

    /**
     * See
     * {@link android.bluetooth.BluetoothGattCallback#onMtuChanged(android.bluetooth.BluetoothGatt,
     * int, int)}
     */
    public void onMtuChanged(BluetoothGattWrapper gatt, int mtu, int status) {}

    /**
     * See
     * {@link android.bluetooth.BluetoothGattCallback#onCharacteristicChanged(
     * android.bluetooth.BluetoothGatt, BluetoothGattCharacteristic)}
     */
    public void onCharacteristicChanged(BluetoothGattWrapper gatt,
            BluetoothGattCharacteristic characteristic) {}

    /** Unwraps a Bluetooth Gatt callback. */
    public android.bluetooth.BluetoothGattCallback unwrap() {
        return mWrappedBluetoothGattCallback;
    }

    /** Forward callback to testable instance. */
    private class InternalBluetoothGattCallback extends android.bluetooth.BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(android.bluetooth.BluetoothGatt gatt, int status,
                int newState) {
            BluetoothGattCallback.this.onConnectionStateChange(BluetoothGattWrapper.wrap(gatt),
                    status, newState);
        }

        @Override
        public void onServicesDiscovered(android.bluetooth.BluetoothGatt gatt, int status) {
            BluetoothGattCallback.this.onServicesDiscovered(BluetoothGattWrapper.wrap(gatt),
                    status);
        }

        @Override
        public void onCharacteristicRead(android.bluetooth.BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            BluetoothGattCallback.this.onCharacteristicRead(
                    BluetoothGattWrapper.wrap(gatt), characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(android.bluetooth.BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            BluetoothGattCallback.this.onCharacteristicWrite(
                    BluetoothGattWrapper.wrap(gatt), characteristic, status);
        }

        @Override
        public void onDescriptorRead(android.bluetooth.BluetoothGatt gatt,
                BluetoothGattDescriptor descriptor, int status) {
            BluetoothGattCallback.this.onDescriptorRead(
                    BluetoothGattWrapper.wrap(gatt), descriptor, status);
        }

        @Override
        public void onDescriptorWrite(android.bluetooth.BluetoothGatt gatt,
                BluetoothGattDescriptor descriptor, int status) {
            BluetoothGattCallback.this.onDescriptorWrite(
                    BluetoothGattWrapper.wrap(gatt), descriptor, status);
        }

        @Override
        public void onReadRemoteRssi(android.bluetooth.BluetoothGatt gatt, int rssi, int status) {
            BluetoothGattCallback.this.onReadRemoteRssi(BluetoothGattWrapper.wrap(gatt), rssi,
                    status);
        }

        @Override
        public void onReliableWriteCompleted(android.bluetooth.BluetoothGatt gatt, int status) {
            BluetoothGattCallback.this.onReliableWriteCompleted(BluetoothGattWrapper.wrap(gatt),
                    status);
        }

        @Override
        public void onMtuChanged(android.bluetooth.BluetoothGatt gatt, int mtu, int status) {
            BluetoothGattCallback.this.onMtuChanged(BluetoothGattWrapper.wrap(gatt), mtu, status);
        }

        @Override
        public void onCharacteristicChanged(android.bluetooth.BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic) {
            BluetoothGattCallback.this.onCharacteristicChanged(
                    BluetoothGattWrapper.wrap(gatt), characteristic);
        }
    }
}

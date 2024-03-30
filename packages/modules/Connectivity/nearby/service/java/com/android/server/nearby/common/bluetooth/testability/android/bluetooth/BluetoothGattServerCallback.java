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
import android.bluetooth.BluetoothGattService;

/**
 * Wrapper of {@link android.bluetooth.BluetoothGattServerCallback} that uses mockable objects.
 */
public abstract class BluetoothGattServerCallback {

    private final android.bluetooth.BluetoothGattServerCallback mWrappedInstance =
            new InternalBluetoothGattServerCallback();

    /**
     * See {@link android.bluetooth.BluetoothGattServerCallback#onCharacteristicReadRequest(
     * android.bluetooth.BluetoothDevice, int, int, BluetoothGattCharacteristic)}
     */
    public void onCharacteristicReadRequest(BluetoothDevice device, int requestId,
            int offset, BluetoothGattCharacteristic characteristic) {}

    /**
     * See {@link android.bluetooth.BluetoothGattServerCallback#onCharacteristicWriteRequest(
     * android.bluetooth.BluetoothDevice, int, BluetoothGattCharacteristic, boolean, boolean, int,
     * byte[])}
     */
    public void onCharacteristicWriteRequest(BluetoothDevice device,
            int requestId,
            BluetoothGattCharacteristic characteristic,
            boolean preparedWrite,
            boolean responseNeeded,
            int offset,
            byte[] value) {}

    /**
     * See {@link android.bluetooth.BluetoothGattServerCallback#onConnectionStateChange(
     * android.bluetooth.BluetoothDevice, int, int)}
     */
    public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {}

    /**
     * See {@link android.bluetooth.BluetoothGattServerCallback#onDescriptorReadRequest(
     * android.bluetooth.BluetoothDevice, int, int, BluetoothGattDescriptor)}
     */
    public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
            BluetoothGattDescriptor descriptor) {}

    /**
     * See {@link android.bluetooth.BluetoothGattServerCallback#onDescriptorWriteRequest(
     * android.bluetooth.BluetoothDevice, int, BluetoothGattDescriptor, boolean, boolean, int,
     * byte[])}
     */
    public void onDescriptorWriteRequest(BluetoothDevice device,
            int requestId,
            BluetoothGattDescriptor descriptor,
            boolean preparedWrite,
            boolean responseNeeded,
            int offset,
            byte[] value) {}

    /**
     * See {@link android.bluetooth.BluetoothGattServerCallback#onExecuteWrite(
     * android.bluetooth.BluetoothDevice, int, boolean)}
     */
    public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {}

    /**
     * See {@link android.bluetooth.BluetoothGattServerCallback#onMtuChanged(
     * android.bluetooth.BluetoothDevice, int)}
     */
    public void onMtuChanged(BluetoothDevice device, int mtu) {}

    /**
     * See {@link android.bluetooth.BluetoothGattServerCallback#onNotificationSent(
     * android.bluetooth.BluetoothDevice, int)}
     */
    public void onNotificationSent(BluetoothDevice device, int status) {}

    /**
     * See {@link android.bluetooth.BluetoothGattServerCallback#onServiceAdded(int,
     * BluetoothGattService)}
     */
    public void onServiceAdded(int status, BluetoothGattService service) {}

    /** Unwraps a Bluetooth Gatt server callback. */
    public android.bluetooth.BluetoothGattServerCallback unwrap() {
        return mWrappedInstance;
    }

    /** Forward callback to testable instance. */
    private class InternalBluetoothGattServerCallback extends
            android.bluetooth.BluetoothGattServerCallback {
        @Override
        public void onCharacteristicReadRequest(android.bluetooth.BluetoothDevice device,
                int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            BluetoothGattServerCallback.this.onCharacteristicReadRequest(
                    BluetoothDevice.wrap(device), requestId, offset, characteristic);
        }

        @Override
        public void onCharacteristicWriteRequest(android.bluetooth.BluetoothDevice device,
                int requestId,
                BluetoothGattCharacteristic characteristic,
                boolean preparedWrite,
                boolean responseNeeded,
                int offset,
                byte[] value) {
            BluetoothGattServerCallback.this.onCharacteristicWriteRequest(
                    BluetoothDevice.wrap(device),
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value);
        }

        @Override
        public void onConnectionStateChange(android.bluetooth.BluetoothDevice device, int status,
                int newState) {
            BluetoothGattServerCallback.this.onConnectionStateChange(
                    BluetoothDevice.wrap(device), status, newState);
        }

        @Override
        public void onDescriptorReadRequest(android.bluetooth.BluetoothDevice device, int requestId,
                int offset, BluetoothGattDescriptor descriptor) {
            BluetoothGattServerCallback.this.onDescriptorReadRequest(BluetoothDevice.wrap(device),
                    requestId, offset, descriptor);
        }

        @Override
        public void onDescriptorWriteRequest(android.bluetooth.BluetoothDevice device,
                int requestId,
                BluetoothGattDescriptor descriptor,
                boolean preparedWrite,
                boolean responseNeeded,
                int offset,
                byte[] value) {
            BluetoothGattServerCallback.this.onDescriptorWriteRequest(BluetoothDevice.wrap(device),
                    requestId,
                    descriptor,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value);
        }

        @Override
        public void onExecuteWrite(android.bluetooth.BluetoothDevice device, int requestId,
                boolean execute) {
            BluetoothGattServerCallback.this.onExecuteWrite(BluetoothDevice.wrap(device), requestId,
                    execute);
        }

        @Override
        public void onMtuChanged(android.bluetooth.BluetoothDevice device, int mtu) {
            BluetoothGattServerCallback.this.onMtuChanged(BluetoothDevice.wrap(device), mtu);
        }

        @Override
        public void onNotificationSent(android.bluetooth.BluetoothDevice device, int status) {
            BluetoothGattServerCallback.this.onNotificationSent(
                    BluetoothDevice.wrap(device), status);
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            BluetoothGattServerCallback.this.onServiceAdded(status, service);
        }
    }
}

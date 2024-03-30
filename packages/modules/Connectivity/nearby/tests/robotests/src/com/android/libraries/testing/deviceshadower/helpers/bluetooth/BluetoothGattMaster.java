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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import com.android.libraries.testing.deviceshadower.DeviceShadowEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * Helper class to operate a device as gatt server.
 */
public class BluetoothGattMaster {

    private static final String TAG = "BluetoothGattMaster";

    /**
     * Callback of BluetoothGattMaster.
     */
    public interface Callback {

        void onConnectionStateChange(String address, int status, int newState);

        void onCharacteristicReadRequest(String address, UUID uuid);

        void onCharacteristicWriteRequest(String address, UUID uuid, byte[] value,
                boolean preparedWrite, boolean responseNeeded);

        void onDescriptorReadRequest(String address, UUID uuid);

        void onDescriptorWriteRequest(String address, UUID uuid, byte[] value,
                boolean preparedWrite, boolean responseNeeded);

        void onNotificationSent(String address, int status);

        void onExecuteWrite(String address, boolean execute);

        void onServiceAdded(UUID uuid, int status);

        void onMtuChanged(String address, int mtu);
    }

    private final String mAddress;
    private final Callback mCallback;
    private final Context mContext;
    private BluetoothGattServer mGattServer;
    private final Map<UUID, BluetoothGattCharacteristic> mCharacteristics = new HashMap<>();

    public BluetoothGattMaster(String address, Callback callback, Context context) {
        this.mAddress = address;
        this.mCallback = callback;
        this.mContext = context;
        DeviceShadowEnvironment.addDevice(address).bluetooth()
                .setAdapterInitialState(BluetoothAdapter.STATE_ON);
    }

    public Future<Void> start(final BluetoothGattService service) {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
                mGattServer = manager.openGattServer(mContext, mGattServerCallback);
                mGattServer.addService(service);
            }
        });
    }

    public Future<Void> stop() {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mGattServer.close();
            }
        });
    }

    public Future<Void> notifyCharacteristic(
            final String remoteAddress, final UUID uuid, final byte[] value,
            final boolean confirm) {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                BluetoothGattCharacteristic characteristic = mCharacteristics.get(uuid);
                characteristic.setValue(value);
                mGattServer.notifyCharacteristicChanged(
                        BluetoothAdapter.getDefaultAdapter().getRemoteDevice(remoteAddress),
                        characteristic, confirm);
            }
        });
    }

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            String address = device.getAddress();
            Log.v(TAG, String.format(
                    "BluetoothGattServerManager.onConnectionStateChange on %s: status %d,"
                            + " newState %d", address, status, newState));
            mCallback.onConnectionStateChange(address, status, newState);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattCharacteristic characteristic) {
            String address = device.getAddress();
            UUID uuid = characteristic.getUuid();
            Log.v(TAG,
                    String.format("BluetoothGattServerManager.onCharacteristicReadRequest on %s: "
                                    + "characteristic %s, request %d, offset %d",
                            address, uuid, requestId, offset));
            mCallback.onCharacteristicReadRequest(address, uuid);
            mGattServer.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                    characteristic.getValue());
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                boolean responseNeeded,
                int offset, byte[] value) {
            String address = device.getAddress();
            UUID uuid = characteristic.getUuid();
            Log.v(TAG,
                    String.format("BluetoothGattServerManager.onCharacteristicWriteRequest on %s: "
                                    + "characteristic %s, request %d, offset %d, preparedWrite %b, "
                                    + "responseNeeded %b",
                            address, uuid, requestId, offset, preparedWrite, responseNeeded));
            mCallback.onCharacteristicWriteRequest(address, uuid, value, preparedWrite,
                    responseNeeded);

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                        null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattDescriptor descriptor) {
            String address = device.getAddress();
            UUID uuid = descriptor.getUuid();
            Log.v(TAG, String.format("BluetoothGattServerManager.onDescriptorReadRequest on %s: "
                            + " descriptor %s, requestId %d, offset %d",
                    address, uuid, requestId, offset));
            mCallback.onDescriptorReadRequest(address, uuid);
            mGattServer.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset, descriptor.getValue());
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
                int offset, byte[] value) {
            String address = device.getAddress();
            UUID uuid = descriptor.getUuid();
            Log.v(TAG, String.format("BluetoothGattServerManager.onDescriptorWriteRequest on %s: "
                            + "descriptor %s, requestId %d, offset %d, preparedWrite %b, "
                            + "responseNeeded %b",
                    address, uuid, requestId, offset, preparedWrite, responseNeeded));
            mCallback.onDescriptorWriteRequest(address, uuid, value, preparedWrite, responseNeeded);

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                        null);
            }
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            String address = device.getAddress();
            Log.v(TAG,
                    String.format("BluetoothGattServerManager.onNotificationSent on %s: status %d",
                            address, status));
            mCallback.onNotificationSent(address, status);
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            /*** Not implemented yet
             String address = device.getAddress();
             Log.v(TAG, String.format(
             "BluetoothGattServerManager.onExecuteWrite on %s: requestId %d, execute %b",
             address, requestId, execute));
             callback.onExecuteWrite(address, execute);
             */
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            UUID uuid = service.getUuid();
            Log.v(TAG, String.format(
                    "BluetoothGattServerManager.onServiceAdded: service %s, status %d",
                    uuid, status));
            mCallback.onServiceAdded(uuid, status);

            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                mCharacteristics.put(characteristic.getUuid(), characteristic);
            }
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Log.v(TAG, String.format("onMtuChanged(mtu: %s)", mtu));
            mCallback.onMtuChanged(device.getAddress(), mtu);
        }
    };
}

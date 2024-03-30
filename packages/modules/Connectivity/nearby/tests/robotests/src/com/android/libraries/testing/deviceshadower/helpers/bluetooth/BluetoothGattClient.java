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
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import com.android.libraries.testing.deviceshadower.DeviceShadowEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to operate a device as gatt client.
 */
public class BluetoothGattClient {

    private static final String TAG = "BluetoothGattClient";
    private static final int LATCH_TIMEOUT_MILLIS = 1000;

    /**
     * Callback of BluetoothGattClient.
     */
    public interface Callback {

        void onConnectionStateChange(String address, int status, int newState);

        void onCharacteristicChanged(String address, UUID uuid, byte[] value);

        void onCharacteristicRead(String address, UUID uuid, byte[] value, int status);

        void onCharacteristicWrite(String address, UUID uuid, byte[] value, int status);

        void onDescriptorRead(String address, UUID uuid, byte[] value, int status);

        void onDescriptorWrite(String address, UUID uuid, byte[] value, int status);

        void onServicesDiscovered(
                UUID[] serviceUuid, UUID[] characteristicUuid, UUID[] descriptorUuid, int status);

        void onConfigureMTU(String address, int mtu, int status);
    }

    private final String mAddress;
    private final Callback mCallback;
    private final Context mContext;
    private final Map<UUID, BluetoothGattCharacteristic> mCharacteristics = new HashMap<>();
    private final Map<UUID, BluetoothGattDescriptor> mDescriptors = new HashMap<>();
    private BluetoothGatt mGatt;
    private CountDownLatch mConnectionLatch;
    private CountDownLatch mServiceDiscoverLatch;

    public BluetoothGattClient(String address, Callback callback, Context context) {
        this.mAddress = address;
        this.mCallback = callback;
        this.mContext = context;
        DeviceShadowEnvironment.addDevice(address).bluetooth()
                .setAdapterInitialState(BluetoothAdapter.STATE_ON);
    }

    public Future<Void> connect(final String remoteAddress) {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mConnectionLatch = new CountDownLatch(1);
                mGatt = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(remoteAddress)
                        .connectGatt(mContext, false /* auto connect */, mGattCallback);
                try {
                    mConnectionLatch.await(LATCH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // no-op.
                }

                mServiceDiscoverLatch = new CountDownLatch(1);
                mGatt.discoverServices();
                try {
                    mServiceDiscoverLatch.await(LATCH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // no-op.
                }
            }
        });
    }

    public Future<Void> close() {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mGatt.disconnect();
                mGatt.close();
            }
        });
    }

    public Future<Void> readCharacteristic(final UUID uuid) {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mGatt.readCharacteristic(mCharacteristics.get(uuid));
            }
        });
    }

    public Future<Void> setNotification(final UUID uuid) {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mGatt.setCharacteristicNotification(mCharacteristics.get(uuid), true);
            }
        });
    }

    public Future<Void> writeCharacteristic(final UUID uuid, final byte[] value) {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                BluetoothGattCharacteristic characteristic = mCharacteristics.get(uuid);
                characteristic.setValue(value);
                mGatt.writeCharacteristic(characteristic);
            }
        });
    }

    /**
     * Reads the value of a descriptor with given UUID.
     *
     * <p>If different characteristics on the service have the same descriptor, use {@link
     * BluetoothGattClient#readDescriptor(UUID, UUID)} instead.
     */
    public Future<Void> readDescriptor(final UUID uuid) {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mGatt.readDescriptor(mDescriptors.get(uuid));
            }
        });
    }

    /**
     * Reads the descriptor value of the specified characteristic.
     */
    public Future<Void> readDescriptor(final UUID descriptorUuid, final UUID characteristicUuid) {
        return DeviceShadowEnvironment.run(
                mAddress,
                new Runnable() {
                    @Override
                    public void run() {
                        mGatt.readDescriptor(
                                mCharacteristics.get(characteristicUuid)
                                        .getDescriptor(descriptorUuid));
                    }
                });
    }

    /**
     * Writes to the descriptor with given UUID.
     *
     * <p>If different characteristics on the service have the same descriptor, use {@link
     * BluetoothGattClient#writeDescriptor(UUID, UUID, byte[])} instead.
     */
    public Future<Void> writeDescriptor(final UUID uuid, final byte[] value) {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                BluetoothGattDescriptor descriptor = mDescriptors.get(uuid);
                descriptor.setValue(value);
                mGatt.writeDescriptor(descriptor);
            }
        });
    }

    /**
     * Writes to the descriptor of the specified characteristic.
     */
    public Future<Void> writeDescriptor(
            final UUID descriptorUuid, final UUID characteristicUuid, final byte[] value) {
        return DeviceShadowEnvironment.run(
                mAddress,
                new Runnable() {
                    @Override
                    public void run() {
                        BluetoothGattDescriptor descriptor =
                                mCharacteristics.get(characteristicUuid)
                                        .getDescriptor(descriptorUuid);
                        descriptor.setValue(value);
                        mGatt.writeDescriptor(descriptor);
                    }
                });
    }

    public Future<Void> requestMtu(int mtu) {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mGatt.requestMtu(mtu);
            }
        });
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.v(TAG, String.format("onConnectionStateChange(status: %s, newState: %s)",
                    status, newState));
            if (mConnectionLatch != null) {
                mConnectionLatch.countDown();
            }
            mCallback.onConnectionStateChange(gatt.getDevice().getAddress(), status, newState);
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.v(TAG, String.format("onCharacteristicChanged(characteristic: %s, value: %s)",
                    characteristic.getUuid(), Arrays.toString(characteristic.getValue())));
            mCallback.onCharacteristicChanged(
                    gatt.getDevice().getAddress(), characteristic.getUuid(),
                    characteristic.getValue());
        }

        @Override
        public void onCharacteristicRead(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.v(TAG, String.format("onCharacteristicRead(descriptor: %s, status: %s)",
                    characteristic.getUuid(), status));
            mCallback.onCharacteristicRead(
                    gatt.getDevice().getAddress(), characteristic.getUuid(),
                    characteristic.getValue(),
                    status);
        }

        @Override
        public void onCharacteristicWrite(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.v(TAG, String.format("onCharacteristicWrite(descriptor: %s, status: %s)",
                    characteristic.getUuid(), status));
            mCallback.onCharacteristicWrite(gatt.getDevice().getAddress(),
                    characteristic.getUuid(), characteristic.getValue(), status);
        }

        @Override
        public void onDescriptorRead(
                BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.v(TAG, String.format("onDescriptorRead(descriptor: %s, status: %s)",
                    descriptor.getUuid(), status));
            mCallback.onDescriptorRead(
                    gatt.getDevice().getAddress(), descriptor.getUuid(), descriptor.getValue(),
                    status);
        }

        @Override
        public void onDescriptorWrite(
                BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.v(TAG, String.format("onDescriptorWrite(descriptor: %s, status: %s)",
                    descriptor.getUuid(), status));
            mCallback.onDescriptorWrite(
                    gatt.getDevice().getAddress(), descriptor.getUuid(), descriptor.getValue(),
                    status);
        }

        @Override
        public synchronized void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.v(TAG, "Discovered service: " + gatt.getServices());
            List<UUID> serviceUuid = new ArrayList<>();
            List<UUID> characteristicUuid = new ArrayList<>();
            List<UUID> descriptorUuid = new ArrayList<>();
            for (BluetoothGattService service : gatt.getServices()) {
                serviceUuid.add(service.getUuid());
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    mCharacteristics.put(characteristic.getUuid(), characteristic);
                    characteristicUuid.add(characteristic.getUuid());
                    for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                        mDescriptors.put(descriptor.getUuid(), descriptor);
                        descriptorUuid.add(descriptor.getUuid());
                    }
                }
            }

            Collections.sort(serviceUuid);
            Collections.sort(characteristicUuid);
            Collections.sort(descriptorUuid);

            mCallback.onServicesDiscovered(serviceUuid.toArray(new UUID[serviceUuid.size()]),
                    characteristicUuid.toArray(new UUID[characteristicUuid.size()]),
                    descriptorUuid.toArray(new UUID[descriptorUuid.size()]),
                    status);
            mServiceDiscoverLatch.countDown();
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.v(TAG, String.format("onMtuChanged(mtu: %s, status: %s)", mtu, status));
            mCallback.onConfigureMTU(gatt.getDevice().getAddress(), mtu, status);
        }
    };
}

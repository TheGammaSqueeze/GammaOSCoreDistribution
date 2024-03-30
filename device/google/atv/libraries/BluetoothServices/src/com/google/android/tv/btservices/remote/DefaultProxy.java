/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.tv.btservices.remote;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;
import java.util.concurrent.CompletableFuture;

public class DefaultProxy extends RemoteProxy {
    private static final String TAG = "Atv.DefaultProxy";
    private static final boolean DEBUG = false;

    private static final int BATTERY_LEVEL_LOW = 5;

    private BleConnection bleConnection;
    private final boolean isBleSupported;
    private BluetoothGattCharacteristic batteryLevelCharacteristic;
    // return value: whether the subscription was successfully created
    private final CompletableFuture<Boolean> subscribeBatteryLevelRes;

    private BatteryResult lastKnownBatteryLevel = BatteryResult.RESULT_NOT_IMPLEMENTED;
    private Runnable batteryLevelCallback;

    public DefaultProxy(Context context, BluetoothDevice device) {
        super(context, device);
        subscribeBatteryLevelRes = new CompletableFuture<>();

        if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
            bleConnection = new BleConnection(new Callback());
            isBleSupported = true;
        } else {
            isBleSupported = false;
            subscribeBatteryLevelRes.complete(true);
        }
    }

    @Override
    public boolean initialize(Context context) {
        if (isBleSupported) {
            return bleConnection.connect(context, mDevice);
        }
        return true;
    }

    @Override
    public CompletableFuture<Boolean> refreshBatteryLevel() {
        if (!isBleSupported) {
            return CompletableFuture.completedFuture(true);
        }

        if (batteryLevelCharacteristic == null) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> ret = new CompletableFuture<>();

        bleConnection.readCharacteristic(
            batteryLevelCharacteristic,
            (BluetoothGattCharacteristic characteristic, int status) -> {
                if (status != BluetoothGatt.GATT_SUCCESS ||
                        characteristic.getValue() == null ||
                        characteristic.getValue().length == 0) {
                    Log.w(TAG, "GATT_FAILURE: " + status);
                    ret.complete(false);
                }
                final int battery = characteristic.getValue()[0] & 0xff;
                Log.i(TAG, "Refresh device " + mDevice + " battery level: " + battery);

                BatteryResult batteryLevel = new BatteryResult(battery);
                lastKnownBatteryLevel = batteryLevel;
                ret.complete(true);
            });

        return ret;
    }

    @Override
    public BatteryResult getLastKnownBatteryLevel() {
        // set lastKnownBatteryLevel for classic devices here instead of in refreshBatteryLevel()
        // since BluetoothDevice#getBatteryLevel() synchronously returns a cached value in the stack
        if (!isBleSupported) {
            int battery = mDevice.getBatteryLevel();
            if (battery != lastKnownBatteryLevel.battery()) {
                BatteryResult batteryLevel = new BatteryResult(battery);

                lastKnownBatteryLevel = batteryLevel;
                if (batteryLevelCallback != null) {
                    batteryLevelCallback.run();
                }
            }
        }

        return lastKnownBatteryLevel;
    }

    @Override
    public CompletableFuture<Boolean> registerBatteryLevelCallback(Runnable callback) {
        return subscribeBatteryLevelRes.thenApply(result -> {
            if (!result) return false;

            batteryLevelCallback = callback;
            return true;
        });
    }

    @Override
    public int lowBatteryLevel() {
        return BATTERY_LEVEL_LOW;
    }

    @Override
    public CompletableFuture<Boolean> refreshVersion(){
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Version getLastKnownVersion(){
        return Version.BAD_VERSION;
    }

    @Override
    public CompletableFuture<DfuResult> requestDfu(
            DfuBinary dfu, DfuManager.Listener listener, boolean background){
        return null;
    }

    @Override
    public DfuResult getDfuState() {
        return null;
    }

    @Override
    public boolean supportsBackgroundDfu() {
        return false;
    }

    private class Callback implements BleConnection.Callback {
        @Override
        public void onGattReady(BluetoothGatt gatt) {
            // Get characteristic for battery level
            final BluetoothGattService battService = gatt.getService(UUID_BATTERY_SERVICE);
            if (battService != null) {
                batteryLevelCharacteristic =
                    battService.getCharacteristic(UUID_BATTERY_LEVEL_CHARACTERISTIC);
            }

            // Subscribe to battery level update notification
            if (batteryLevelCharacteristic != null) {
                Log.d(TAG, "subscribing to battery level update notification");
                BluetoothGattDescriptor batteryCCCDescriptor =
                    batteryLevelCharacteristic.getDescriptor(
                        CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);

                bleConnection.writeDescriptor(
                    batteryCCCDescriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                    (BluetoothGattDescriptor descriptor, int status) -> {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.w(TAG, "GATT_FAILURE: " + status);
                            subscribeBatteryLevelRes.complete(false);
                        }
                        if (!bleConnection.setCharacteristicNotification(
                            batteryLevelCharacteristic, true)) {
                            subscribeBatteryLevelRes.complete(false);
                        }
                        subscribeBatteryLevelRes.complete(true);
                      });
            } else {
                Log.w(TAG, "subscribing to battery level unsuccessful for device " + mDevice);
                subscribeBatteryLevelRes.complete(false);
            }
        }

        @Override
        public void onNotification(BluetoothGattCharacteristic characteristic, byte[] data) {
            if (DEBUG) {
                Log.d(TAG, "GattCallback#onNotification: " + characteristic);
            }

            if (characteristic == batteryLevelCharacteristic && data.length > 0) {
                final int battery = data[0] & 0xff;
                BatteryResult batteryLevel = new BatteryResult(battery);

                lastKnownBatteryLevel = batteryLevel;
                if (batteryLevelCallback != null) {
                    if (DEBUG) {
                        Log.d(TAG, "Running batteryLevelCallback");
                    }
                    batteryLevelCallback.run();
                }
            }
        }

        @Override
        public void onDisconnect(BluetoothGatt gatt, int status) {
            return;
        }
    }
}

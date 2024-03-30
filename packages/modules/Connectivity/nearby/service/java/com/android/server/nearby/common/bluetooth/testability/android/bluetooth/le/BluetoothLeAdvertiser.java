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

package com.android.server.nearby.common.bluetooth.testability.android.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.os.Build;

import javax.annotation.Nullable;

/**
 * Mockable wrapper of {@link android.bluetooth.le.BluetoothLeAdvertiser}.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BluetoothLeAdvertiser {

    private final android.bluetooth.le.BluetoothLeAdvertiser mWrappedInstance;

    private BluetoothLeAdvertiser(
            android.bluetooth.le.BluetoothLeAdvertiser bluetoothLeAdvertiser) {
        mWrappedInstance = bluetoothLeAdvertiser;
    }

    /**
     * See {@link android.bluetooth.le.BluetoothLeAdvertiser#startAdvertising(AdvertiseSettings,
     * AdvertiseData, AdvertiseCallback)}.
     */
    public void startAdvertising(AdvertiseSettings settings, AdvertiseData advertiseData,
            AdvertiseCallback callback) {
        mWrappedInstance.startAdvertising(settings, advertiseData, callback);
    }

    /**
     * See {@link android.bluetooth.le.BluetoothLeAdvertiser#startAdvertising(AdvertiseSettings,
     * AdvertiseData, AdvertiseData, AdvertiseCallback)}.
     */
    public void startAdvertising(AdvertiseSettings settings, AdvertiseData advertiseData,
            AdvertiseData scanResponse, AdvertiseCallback callback) {
        mWrappedInstance.startAdvertising(settings, advertiseData, scanResponse, callback);
    }

    /**
     * See {@link android.bluetooth.le.BluetoothLeAdvertiser#stopAdvertising(AdvertiseCallback)}.
     */
    public void stopAdvertising(AdvertiseCallback callback) {
        mWrappedInstance.stopAdvertising(callback);
    }

    /** Wraps a Bluetooth LE advertiser. */
    @Nullable
    public static BluetoothLeAdvertiser wrap(
            @Nullable android.bluetooth.le.BluetoothLeAdvertiser bluetoothLeAdvertiser) {
        if (bluetoothLeAdvertiser == null) {
            return null;
        }
        return new BluetoothLeAdvertiser(bluetoothLeAdvertiser);
    }
}

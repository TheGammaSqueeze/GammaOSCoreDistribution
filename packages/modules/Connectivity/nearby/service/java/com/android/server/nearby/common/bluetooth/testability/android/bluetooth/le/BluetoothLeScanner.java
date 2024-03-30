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
import android.app.PendingIntent;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.os.Build;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Mockable wrapper of {@link android.bluetooth.le.BluetoothLeScanner}.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BluetoothLeScanner {

    private final android.bluetooth.le.BluetoothLeScanner mWrappedBluetoothLeScanner;

    private BluetoothLeScanner(android.bluetooth.le.BluetoothLeScanner bluetoothLeScanner) {
        mWrappedBluetoothLeScanner = bluetoothLeScanner;
    }

    /**
     * See {@link android.bluetooth.le.BluetoothLeScanner#startScan(List, ScanSettings,
     * android.bluetooth.le.ScanCallback)}.
     */
    public void startScan(List<ScanFilter> filters, ScanSettings settings,
            ScanCallback callback) {
        mWrappedBluetoothLeScanner.startScan(filters, settings, callback.unwrap());
    }

    /**
     * See {@link android.bluetooth.le.BluetoothLeScanner#startScan(List, ScanSettings,
     * PendingIntent)}.
     */
    public void startScan(
            List<ScanFilter> filters, ScanSettings settings, PendingIntent callbackIntent) {
        mWrappedBluetoothLeScanner.startScan(filters, settings, callbackIntent);
    }

    /**
     * See {@link
     * android.bluetooth.le.BluetoothLeScanner#startScan(android.bluetooth.le.ScanCallback)}.
     */
    public void startScan(ScanCallback callback) {
        mWrappedBluetoothLeScanner.startScan(callback.unwrap());
    }

    /**
     * See
     * {@link android.bluetooth.le.BluetoothLeScanner#stopScan(android.bluetooth.le.ScanCallback)}.
     */
    public void stopScan(ScanCallback callback) {
        mWrappedBluetoothLeScanner.stopScan(callback.unwrap());
    }

    /** See {@link android.bluetooth.le.BluetoothLeScanner#stopScan(PendingIntent)}. */
    public void stopScan(PendingIntent callbackIntent) {
        mWrappedBluetoothLeScanner.stopScan(callbackIntent);
    }

    /** Wraps a Bluetooth LE scanner. */
    @Nullable
    public static BluetoothLeScanner wrap(
            @Nullable android.bluetooth.le.BluetoothLeScanner bluetoothLeScanner) {
        if (bluetoothLeScanner == null) {
            return null;
        }
        return new BluetoothLeScanner(bluetoothLeScanner);
    }
}

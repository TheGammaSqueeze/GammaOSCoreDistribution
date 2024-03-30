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
import android.bluetooth.le.ScanRecord;
import android.os.Build;

import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothDevice;

import javax.annotation.Nullable;

/**
 * Mockable wrapper of {@link android.bluetooth.le.ScanResult}.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ScanResult {

    private final android.bluetooth.le.ScanResult mWrappedScanResult;

    private ScanResult(android.bluetooth.le.ScanResult scanResult) {
        mWrappedScanResult = scanResult;
    }

    /** See {@link android.bluetooth.le.ScanResult#getScanRecord()}. */
    @Nullable
    public ScanRecord getScanRecord() {
        return mWrappedScanResult.getScanRecord();
    }

    /** See {@link android.bluetooth.le.ScanResult#getRssi()}. */
    public int getRssi() {
        return mWrappedScanResult.getRssi();
    }

    /** See {@link android.bluetooth.le.ScanResult#getTimestampNanos()}. */
    public long getTimestampNanos() {
        return mWrappedScanResult.getTimestampNanos();
    }

    /** See {@link android.bluetooth.le.ScanResult#getDevice()}. */
    public BluetoothDevice getDevice() {
        return BluetoothDevice.wrap(mWrappedScanResult.getDevice());
    }

    /** Creates a wrapper of scan result. */
    public static ScanResult wrap(android.bluetooth.le.ScanResult scanResult) {
        return new ScanResult(scanResult);
    }
}

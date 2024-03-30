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
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper of {@link android.bluetooth.le.ScanCallback} that uses mockable objects.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public abstract class ScanCallback {

    /** See {@link android.bluetooth.le.ScanCallback#SCAN_FAILED_ALREADY_STARTED} */
    public static final int SCAN_FAILED_ALREADY_STARTED =
            android.bluetooth.le.ScanCallback.SCAN_FAILED_ALREADY_STARTED;

    /** See {@link android.bluetooth.le.ScanCallback#SCAN_FAILED_APPLICATION_REGISTRATION_FAILED} */
    public static final int SCAN_FAILED_APPLICATION_REGISTRATION_FAILED =
            android.bluetooth.le.ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED;

    /** See {@link android.bluetooth.le.ScanCallback#SCAN_FAILED_FEATURE_UNSUPPORTED} */
    public static final int SCAN_FAILED_FEATURE_UNSUPPORTED =
            android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED;

    /** See {@link android.bluetooth.le.ScanCallback#SCAN_FAILED_INTERNAL_ERROR} */
    public static final int SCAN_FAILED_INTERNAL_ERROR =
            android.bluetooth.le.ScanCallback.SCAN_FAILED_INTERNAL_ERROR;

    private final android.bluetooth.le.ScanCallback mWrappedScanCallback =
            new InternalScanCallback();

    /**
     * See {@link android.bluetooth.le.ScanCallback#onScanFailed(int)}
     */
    public void onScanFailed(int errorCode) {}

    /**
     * See
     * {@link android.bluetooth.le.ScanCallback#onScanResult(int, android.bluetooth.le.ScanResult)}.
     */
    public void onScanResult(int callbackType, ScanResult result) {}

    /**
     * See {@link
     * android.bluetooth.le.ScanCallback#onBatchScanResult(List<android.bluetooth.le.ScanResult>)}.
     */
    public void onBatchScanResults(List<ScanResult> results) {}

    /** Unwraps scan callback. */
    public android.bluetooth.le.ScanCallback unwrap() {
        return mWrappedScanCallback;
    }

    /** Forward callback to testable instance. */
    private class InternalScanCallback extends android.bluetooth.le.ScanCallback {
        @Override
        public void onScanFailed(int errorCode) {
            ScanCallback.this.onScanFailed(errorCode);
        }

        @Override
        public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
            ScanCallback.this.onScanResult(callbackType, ScanResult.wrap(result));
        }

        @Override
        public void onBatchScanResults(List<android.bluetooth.le.ScanResult> results) {
            List<ScanResult> wrappedScanResults = new ArrayList<>();
            for (android.bluetooth.le.ScanResult result : results) {
                wrappedScanResults.add(ScanResult.wrap(result));
            }
            ScanCallback.this.onBatchScanResults(wrappedScanResults);
        }
    }
}

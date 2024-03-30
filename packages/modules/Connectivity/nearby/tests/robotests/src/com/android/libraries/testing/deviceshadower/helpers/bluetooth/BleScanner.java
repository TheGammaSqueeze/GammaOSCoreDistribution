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
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.util.Log;

import com.android.libraries.testing.deviceshadower.DeviceShadowEnvironment;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to operate a device as BLE scanner.
 */
public class BleScanner {

    private static final String TAG = "BleScanner";

    private static final int DEFAULT_MODE = ScanSettings.SCAN_MODE_LOW_LATENCY;
    private static final int DEFAULT_CALLBACK_TYPE = ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
    private static final long DEFAULT_DELAY = 0L;

    /**
     * Callback of {@link BleScanner}.
     */
    public interface Callback {

        void onScanResult(String address, int callbackType, ScanResult result);

        void onBatchScanResults(String address, List<ScanResult> results);

        void onScanFailed(String address, int errorCode);
    }

    /**
     * Builder class of {@link BleScanner}.
     */
    public static final class Builder {

        private final String mAddress;
        private final Callback mCallback;
        private ScanSettings mSettings = defaultSettings();
        private List<ScanFilter> mFilters;
        private int mNumOfExpectedScanCallbacks = 1;

        public Builder(String address, Callback callback) {
            this.mAddress = Preconditions.checkNotNull(address);
            this.mCallback = Preconditions.checkNotNull(callback);
        }

        public Builder setScanSettings(ScanSettings settings) {
            this.mSettings = settings;
            return this;
        }

        public Builder addScanFilter(ScanFilter... filterArgs) {
            if (this.mFilters == null) {
                this.mFilters = new ArrayList<>();
            }
            for (ScanFilter filter : filterArgs) {
                this.mFilters.add(filter);
            }
            return this;
        }

        /**
         * Sets number of expected scan result callback.
         *
         * @param num Number of expected scan result callback, default to 1.
         */
        public Builder setNumOfExpectedScanCallbacks(int num) {
            mNumOfExpectedScanCallbacks = num;
            return this;
        }

        public BleScanner build() {
            return new BleScanner(
                    mAddress, mCallback, mSettings, mFilters, mNumOfExpectedScanCallbacks);
        }
    }

    private static ScanSettings defaultSettings() {
        return new ScanSettings.Builder()
                .setScanMode(DEFAULT_MODE)
                .setCallbackType(DEFAULT_CALLBACK_TYPE)
                .setReportDelay(DEFAULT_DELAY).build();
    }

    private final String mAddress;
    private final Callback mCallback;
    private final ScanSettings mSettings;
    private final List<ScanFilter> mFilters;
    private final BlockingQueue<Integer> mScanResultCounts;
    private int mNumOfExpectedScanCallbacks;
    private int mNumOfReceivedScanCallbacks;
    private BluetoothLeScanner mScanner;

    private BleScanner(String address, Callback callback, ScanSettings settings,
            List<ScanFilter> filters, int numOfExpectedScanResult) {
        this.mAddress = address;
        this.mCallback = callback;
        this.mSettings = settings;
        this.mFilters = filters;
        this.mNumOfExpectedScanCallbacks = numOfExpectedScanResult;
        this.mNumOfReceivedScanCallbacks = 0;
        this.mScanResultCounts = new LinkedBlockingQueue<>(numOfExpectedScanResult);
        DeviceShadowEnvironment.addDevice(address).bluetooth()
                .setAdapterInitialState(BluetoothAdapter.STATE_ON);
    }

    public Future<Void> start() {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
                mScanner.startScan(mFilters, mSettings, mScanCallback);
            }
        });
    }

    public void waitTillNextScanResult(long timeoutMillis) {
        Integer result = null;
        if (mNumOfReceivedScanCallbacks >= mNumOfExpectedScanCallbacks) {
            return;
        }
        try {
            if (timeoutMillis < 0) {
                result = mScanResultCounts.take();
            } else {
                result = mScanResultCounts.poll(timeoutMillis, TimeUnit.MILLISECONDS);
            }
            if (result != null && result >= 0) {
                mNumOfReceivedScanCallbacks++;
            }
            Log.v(TAG, "Scan results: " + result);
        } catch (InterruptedException e) {
            Log.w(TAG, mAddress + " fails to wait till next scan result: ", e);
        }
    }

    public void waitTillNextScanResult() {
        waitTillNextScanResult(-1);
    }

    public void waitTillAllScanResults() {
        while (mNumOfReceivedScanCallbacks < mNumOfExpectedScanCallbacks) {
            try {
                if (mScanResultCounts.take() >= 0) {
                    mNumOfReceivedScanCallbacks++;
                }
            } catch (InterruptedException e) {
                Log.w(TAG, String.format("%s fails to wait scan result", mAddress), e);
                return;
            }
        }
    }

    public Future<Void> stop() {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
                mScanner.stopScan(mScanCallback);
            }
        });
    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.v(TAG, String.format("onScanResult(callbackType: %d, result: %s) on %s",
                    callbackType, result, mAddress));
            mCallback.onScanResult(mAddress, callbackType, result);
            try {
                mScanResultCounts.put(1);
            } catch (InterruptedException e) {
                // no-op.
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            /**** Not supported yet.
             Log.v(TAG, String.format("onBatchScanResults(results: %s) on %s",
             Arrays.toString(results.toArray()), address));
             callback.onBatchScanResults(address, results);
             try {
             scanResultCounts.put(results.size());
             } catch (InterruptedException e) {
             // no-op.
             }
             */
        }

        @Override
        public void onScanFailed(int errorCode) {
            /**** Not supported yet.
             Log.v(TAG, String.format("onScanFailed(errorCode: %d) on %s", errorCode, address));
             callback.onScanFailed(address, errorCode);
             try {
             scanResultCounts.put(-1);
             } catch (InterruptedException e) {
             // no-op.
             }
             */
        }
    };
}


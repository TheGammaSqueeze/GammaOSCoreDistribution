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
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.util.Log;

import com.android.libraries.testing.deviceshadower.DeviceShadowEnvironment;

import com.google.common.base.Preconditions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

/**
 * Helper class to operate a device as BLE advertiser.
 */
public class BleAdvertiser {

    private static final String TAG = "BleAdvertiser";

    private static final int DEFAULT_MODE = AdvertiseSettings.ADVERTISE_MODE_BALANCED;
    private static final int DEFAULT_TX_POWER_LEVEL = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;
    private static final boolean DEFAULT_CONNECTABLE = true;
    private static final int DEFAULT_TIMEOUT = 0;


    /**
     * Callback of {@link BleAdvertiser}.
     */
    public interface Callback {

        void onStartFailure(String address, int errorCode);

        void onStartSuccess(String address, AdvertiseSettings settingsInEffect);
    }

    /**
     * Builder class of {@link BleAdvertiser}.
     */
    public static final class Builder {

        private final String mAddress;
        private final Callback mCallback;
        private AdvertiseSettings mSettings = defaultSettings();
        private AdvertiseData mData;
        private AdvertiseData mResponse;

        public Builder(String address, Callback callback) {
            this.mAddress = Preconditions.checkNotNull(address);
            this.mCallback = Preconditions.checkNotNull(callback);
        }

        public Builder setAdvertiseSettings(AdvertiseSettings settings) {
            this.mSettings = settings;
            return this;
        }

        public Builder setAdvertiseData(AdvertiseData data) {
            this.mData = data;
            return this;
        }

        public Builder setResponseData(AdvertiseData response) {
            this.mResponse = response;
            return this;
        }

        public BleAdvertiser build() {
            return new BleAdvertiser(mAddress, mCallback, mSettings, mData, mResponse);
        }
    }

    private static AdvertiseSettings defaultSettings() {
        return new AdvertiseSettings.Builder()
                .setAdvertiseMode(DEFAULT_MODE)
                .setConnectable(DEFAULT_CONNECTABLE)
                .setTimeout(DEFAULT_TIMEOUT)
                .setTxPowerLevel(DEFAULT_TX_POWER_LEVEL).build();
    }

    private final String mAddress;
    private final Callback mCallback;
    private final AdvertiseSettings mSettings;
    private final AdvertiseData mData;
    private final AdvertiseData mResponse;
    private final CountDownLatch mStartAdvertiseLatch;
    private BluetoothLeAdvertiser mAdvertiser;

    private BleAdvertiser(String address, Callback callback, AdvertiseSettings settings,
            AdvertiseData data, AdvertiseData response) {
        this.mAddress = address;
        this.mCallback = callback;
        this.mSettings = settings;
        this.mData = data;
        this.mResponse = response;
        mStartAdvertiseLatch = new CountDownLatch(1);
        DeviceShadowEnvironment.addDevice(address).bluetooth()
                .setAdapterInitialState(BluetoothAdapter.STATE_ON);
    }

    /**
     * Starts advertising.
     */
    public Future<Void> start() {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
                mAdvertiser.startAdvertising(mSettings, mData, mResponse, mAdvertiseCallback);
            }
        });
    }

    /**
     * Stops advertising.
     */
    public Future<Void> stop() {
        return DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mAdvertiser.stopAdvertising(mAdvertiseCallback);
            }
        });
    }

    public void waitTillAdvertiseCompleted() {
        try {
            mStartAdvertiseLatch.await();
        } catch (InterruptedException e) {
            Log.w(TAG, mAddress + " fails to wait till advertise completed: ", e);
        }
    }

    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.v(TAG,
                    String.format("onStartSuccess(settingsInEffect: %s) on %s ", settingsInEffect,
                            mAddress));
            mCallback.onStartSuccess(mAddress, settingsInEffect);
            mStartAdvertiseLatch.countDown();
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.v(TAG, String.format("onStartFailure(errorCode: %d) on %s", errorCode, mAddress));
            mCallback.onStartFailure(mAddress, errorCode);
            mStartAdvertiseLatch.countDown();
        }
    };
}


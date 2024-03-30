/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.nearby.provider;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.nearby.BroadcastCallback;
import android.os.ParcelUuid;

import com.android.server.nearby.injector.Injector;

import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * A provider for Bluetooth Low Energy advertisement.
 */
public class BleBroadcastProvider extends AdvertiseCallback {

    /**
     * Listener for Broadcast status changes.
     */
    interface BroadcastListener {
        void onStatusChanged(int status);
    }

    private final Injector mInjector;
    private final Executor mExecutor;

    private BroadcastListener mBroadcastListener;
    private boolean mIsAdvertising;

    BleBroadcastProvider(Injector injector, Executor executor) {
        mInjector = injector;
        mExecutor = executor;
    }

    void start(byte[] advertisementPackets, BroadcastListener listener) {
        if (mIsAdvertising) {
            stop();
        }
        boolean advertiseStarted = false;
        BluetoothAdapter adapter = mInjector.getBluetoothAdapter();
        if (adapter != null) {
            BluetoothLeAdvertiser bluetoothLeAdvertiser =
                    mInjector.getBluetoothAdapter().getBluetoothLeAdvertiser();
            if (bluetoothLeAdvertiser != null) {
                advertiseStarted = true;
                AdvertiseSettings settings =
                        new AdvertiseSettings.Builder()
                                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                                .setConnectable(true)
                                .build();

                // TODO(b/230538655) Use empty data until Presence V1 protocol is implemented.
                ParcelUuid emptyParcelUuid = new ParcelUuid(new UUID(0L, 0L));
                byte[] emptyAdvertisementPackets = new byte[0];
                AdvertiseData advertiseData =
                        new AdvertiseData.Builder()
                                .addServiceData(emptyParcelUuid, emptyAdvertisementPackets).build();
                try {
                    mBroadcastListener = listener;
                    bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, this);
                } catch (NullPointerException | IllegalStateException | SecurityException e) {
                    advertiseStarted = false;
                }
            }
        }
        if (!advertiseStarted) {
            listener.onStatusChanged(BroadcastCallback.STATUS_FAILURE);
        }
    }

    void stop() {
        if (mIsAdvertising) {
            BluetoothAdapter adapter = mInjector.getBluetoothAdapter();
            if (adapter != null) {
                BluetoothLeAdvertiser bluetoothLeAdvertiser =
                        mInjector.getBluetoothAdapter().getBluetoothLeAdvertiser();
                if (bluetoothLeAdvertiser != null) {
                    bluetoothLeAdvertiser.stopAdvertising(this);
                }
            }
            mBroadcastListener = null;
            mIsAdvertising = false;
        }
    }

    @Override
    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
        mExecutor.execute(() -> {
            if (mBroadcastListener != null) {
                mBroadcastListener.onStatusChanged(BroadcastCallback.STATUS_OK);
            }
            mIsAdvertising = true;
        });
    }

    @Override
    public void onStartFailure(int errorCode) {
        if (mBroadcastListener != null) {
            mBroadcastListener.onStatusChanged(BroadcastCallback.STATUS_FAILURE);
        }
    }
}

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

package android.nearby.fastpair.provider;

import static com.google.common.io.BaseEncoding.base16;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.nearby.fastpair.provider.utils.Logger;
import android.os.ParcelUuid;

import androidx.annotation.Nullable;

import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Fast Pair advertiser taking advantage of new Android Oreo advertising features. */
public final class OreoFastPairAdvertiser implements FastPairAdvertiser {
    private static final String TAG = "OreoFastPairAdvertiser";
    private final Logger mLogger = new Logger(TAG);

    private final FastPairSimulator mSimulator;
    private final BluetoothLeAdvertiser mAdvertiser;
    private final AdvertisingSetCallback mAdvertisingSetCallback;
    private AdvertisingSet mAdvertisingSet;

    public OreoFastPairAdvertiser(FastPairSimulator simulator) {
        this.mSimulator = simulator;
        this.mAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        this.mAdvertisingSetCallback = new AdvertisingSetCallback() {

            @Override
            public void onAdvertisingSetStarted(
                    AdvertisingSet set, int txPower, int status) {
                if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    mLogger.log("Advertising succeeded, advertising at %s dBm", txPower);
                    simulator.setIsAdvertising(true);
                    mAdvertisingSet = set;
                    mAdvertisingSet.getOwnAddress();
                } else {
                    mLogger.log(
                            new IllegalStateException(),
                            "Advertising failed, error code=%d", status);
                }
            }

            @Override
            public void onAdvertisingDataSet(AdvertisingSet set, int status) {
                if (status != AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    mLogger.log(
                            new IllegalStateException(),
                            "Updating advertisement failed, error code=%d",
                            status);
                    stopAdvertising();
                }
            }

            // Callback for AdvertisingSet.getOwnAddress().
            @Override
            public void onOwnAddressRead(
                    AdvertisingSet set, int addressType, String address) {
                if (!address.equals(simulator.getBleAddress())) {
                    mLogger.log(
                            "Read own BLE address=%s at %s",
                            address,
                            new SimpleDateFormat("HH:mm:ss:SSS", Locale.US)
                                    .format(Calendar.getInstance().getTime()));
                    // Implicitly start the advertising once BLE address callback arrived.
                    simulator.setBleAddress(address);
                }
            }
        };
    }

    @Override
    public void startAdvertising(@Nullable byte[] serviceData) {
        // To be informed that BLE address is rotated, we need to polling query it asynchronously.
        if (mAdvertisingSet != null) {
            mAdvertisingSet.getOwnAddress();
        }

        if (mSimulator.isDestroyed()) {
            return;
        }

        if (serviceData == null) {
            mLogger.log("Service data is null, stop advertising");
            stopAdvertising();
            return;
        }

        AdvertiseData data =
                new AdvertiseData.Builder()
                        .addServiceData(new ParcelUuid(FastPairService.ID), serviceData)
                        .setIncludeTxPowerLevel(true)
                        .build();

        mLogger.log("Advertising FE2C service data=%s", base16().encode(serviceData));

        if (mAdvertisingSet != null) {
            mAdvertisingSet.setAdvertisingData(data);
            return;
        }

        stopAdvertising();
        AdvertisingSetParameters parameters =
                new AdvertisingSetParameters.Builder()
                        .setLegacyMode(true)
                        .setConnectable(true)
                        .setScannable(true)
                        .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                        .setTxPowerLevel(convertAdvertiseSettingsTxPower(mSimulator.getTxPower()))
                        .build();
        mAdvertiser.startAdvertisingSet(parameters, data, null, null, null,
                mAdvertisingSetCallback);
    }

    private static int convertAdvertiseSettingsTxPower(int txPower) {
        switch (txPower) {
            case AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW:
                return AdvertisingSetParameters.TX_POWER_ULTRA_LOW;
            case AdvertiseSettings.ADVERTISE_TX_POWER_LOW:
                return AdvertisingSetParameters.TX_POWER_LOW;
            case AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM:
                return AdvertisingSetParameters.TX_POWER_MEDIUM;
            default:
                return AdvertisingSetParameters.TX_POWER_HIGH;
        }
    }

    @Override
    public void stopAdvertising() {
        if (mSimulator.isDestroyed()) {
            return;
        }

        mAdvertiser.stopAdvertisingSet(mAdvertisingSetCallback);
        mAdvertisingSet = null;
        mSimulator.setIsAdvertising(false);
    }
}

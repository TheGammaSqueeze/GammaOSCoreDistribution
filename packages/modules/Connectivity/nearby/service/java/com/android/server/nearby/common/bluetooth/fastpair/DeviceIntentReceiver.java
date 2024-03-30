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

package com.android.server.nearby.common.bluetooth.fastpair;

import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothAddress.maskBluetoothAddress;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Like {@link SimpleBroadcastReceiver}, but for intents about a certain {@link BluetoothDevice}.
 */
abstract class DeviceIntentReceiver extends SimpleBroadcastReceiver {

    private static final String TAG = DeviceIntentReceiver.class.getSimpleName();

    private final BluetoothDevice mDevice;

    static DeviceIntentReceiver oneShotReceiver(
            Context context, Preferences preferences, BluetoothDevice device, String... actions) {
        return new DeviceIntentReceiver(context, preferences, device, actions) {
            @Override
            protected void onReceiveDeviceIntent(Intent intent) throws Exception {
                close();
            }
        };
    }

    /**
     * @param context The context to use to register / unregister the receiver.
     * @param device The interesting device. We ignore intents about other devices.
     * @param actions The actions to include in our intent filter.
     */
    protected DeviceIntentReceiver(
            Context context, Preferences preferences, BluetoothDevice device, String... actions) {
        super(context, preferences, actions);
        this.mDevice = device;
    }

    /**
     * Called with intents about the interesting device (see {@link #DeviceIntentReceiver}). Any
     * exception thrown by this method will be delivered via {@link #await}.
     */
    protected abstract void onReceiveDeviceIntent(Intent intent) throws Exception;

    // incompatible types in argument.
    @Override
    protected void onReceive(Intent intent) throws Exception {
        BluetoothDevice intentDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (mDevice == null || mDevice.equals(intentDevice)) {
            onReceiveDeviceIntent(intent);
        } else {
            Log.v(TAG,
                    "Ignoring intent for device=" + maskBluetoothAddress(intentDevice)
                            + "(expected "
                            + maskBluetoothAddress(mDevice) + ")");
        }
    }
}

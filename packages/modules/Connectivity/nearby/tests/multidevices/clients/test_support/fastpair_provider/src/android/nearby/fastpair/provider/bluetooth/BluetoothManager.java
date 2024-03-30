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

package android.nearby.fastpair.provider.bluetooth;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothAdapter;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothDevice;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothGattServer;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothGattServerCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Mockable wrapper of {@link android.bluetooth.BluetoothManager}.
 */
public class BluetoothManager {

    private android.bluetooth.BluetoothManager mWrappedInstance;

    private BluetoothManager(android.bluetooth.BluetoothManager instance) {
        mWrappedInstance = instance;
    }

    /**
     * See {@link android.bluetooth.BluetoothManager#openGattServer(Context,
     * android.bluetooth.BluetoothGattServerCallback)}.
     */
    @Nullable
    public BluetoothGattServer openGattServer(Context context,
            BluetoothGattServerCallback callback) {
        return BluetoothGattServer.wrap(
                mWrappedInstance.openGattServer(context, callback.unwrap()));
    }

    /**
     * See {@link android.bluetooth.BluetoothManager#getConnectionState(
     *android.bluetooth.BluetoothDevice, int)}.
     */
    public int getConnectionState(BluetoothDevice device, int profile) {
        return mWrappedInstance.getConnectionState(device.unwrap(), profile);
    }

    /** See {@link android.bluetooth.BluetoothManager#getConnectedDevices(int)}. */
    public List<BluetoothDevice> getConnectedDevices(int profile) {
        List<android.bluetooth.BluetoothDevice> devices = mWrappedInstance.getConnectedDevices(
                profile);
        List<BluetoothDevice> wrappedDevices = new ArrayList<>(devices.size());
        for (android.bluetooth.BluetoothDevice device : devices) {
            wrappedDevices.add(BluetoothDevice.wrap(device));
        }
        return wrappedDevices;
    }

    /** See {@link android.bluetooth.BluetoothManager#getAdapter()}. */
    public BluetoothAdapter getAdapter() {
        return BluetoothAdapter.wrap(mWrappedInstance.getAdapter());
    }

    public static BluetoothManager wrap(android.bluetooth.BluetoothManager bluetoothManager) {
        return new BluetoothManager(bluetoothManager);
    }
}

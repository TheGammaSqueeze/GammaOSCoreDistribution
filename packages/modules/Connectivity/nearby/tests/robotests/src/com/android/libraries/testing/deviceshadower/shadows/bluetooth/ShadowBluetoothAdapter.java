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

package com.android.libraries.testing.deviceshadower.shadows.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.AttributionSource;

import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.BlueletImpl;
import com.android.libraries.testing.deviceshadower.internal.utils.MacAddressGenerator;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

/**
 * Shadow of {@link BluetoothAdapter} to be used with Device Shadower in Robolectric test.
 */
@Implements(BluetoothAdapter.class)
public class ShadowBluetoothAdapter {

    @RealObject
    BluetoothAdapter mRealAdapter;

    public ShadowBluetoothAdapter() {
    }

    @Implementation
    public static synchronized BluetoothAdapter getDefaultAdapter() {
        // Add a device and set local devicelet in case no local bluelet set
        if (!DeviceShadowEnvironmentImpl.hasLocalDeviceletImpl()) {
            String address = MacAddressGenerator.get().generateMacAddress();
            DeviceShadowEnvironmentImpl.addDevice(address);
            DeviceShadowEnvironmentImpl.setLocalDevice(address);
        }
        BlueletImpl localBluelet = DeviceShadowEnvironmentImpl.getLocalBlueletImpl();
        return localBluelet.getAdapter();
    }

    @Implementation
    public static BluetoothAdapter createAdapter(AttributionSource attributionSource) {
        // Add a device and set local devicelet in case no local bluelet set
        if (!DeviceShadowEnvironmentImpl.hasLocalDeviceletImpl()) {
            String address = MacAddressGenerator.get().generateMacAddress();
            DeviceShadowEnvironmentImpl.addDevice(address);
            DeviceShadowEnvironmentImpl.setLocalDevice(address);
        }
        BlueletImpl localBluelet = DeviceShadowEnvironmentImpl.getLocalBlueletImpl();
        return localBluelet.getAdapter();
    }
}

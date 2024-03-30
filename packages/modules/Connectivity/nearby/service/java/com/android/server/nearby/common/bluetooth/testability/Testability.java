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

package com.android.server.nearby.common.bluetooth.testability;

import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothAdapter;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothDevice;

import javax.annotation.Nullable;

/** Util class to convert from or to testable classes. */
public class Testability {
    /** Wraps a Bluetooth device. */
    public static BluetoothDevice wrap(android.bluetooth.BluetoothDevice bluetoothDevice) {
        return BluetoothDevice.wrap(bluetoothDevice);
    }

    /** Wraps a Bluetooth adapter. */
    @Nullable
    public static BluetoothAdapter wrap(
            @Nullable android.bluetooth.BluetoothAdapter bluetoothAdapter) {
        return BluetoothAdapter.wrap(bluetoothAdapter);
    }
}

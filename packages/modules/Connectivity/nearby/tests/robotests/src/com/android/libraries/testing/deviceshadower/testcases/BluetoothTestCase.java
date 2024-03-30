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

package com.android.libraries.testing.deviceshadower.testcases;

import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.callConstructor;

import android.bluetooth.BluetoothManager;
import android.content.Context;

import com.android.libraries.testing.deviceshadower.shadows.bluetooth.ShadowBluetoothA2dp;
import com.android.libraries.testing.deviceshadower.shadows.bluetooth.ShadowBluetoothAdapter;
import com.android.libraries.testing.deviceshadower.shadows.bluetooth.ShadowBluetoothDevice;
import com.android.libraries.testing.deviceshadower.shadows.bluetooth.ShadowBluetoothLeScanner;
import com.android.libraries.testing.deviceshadower.shadows.bluetooth.ShadowBluetoothServerSocket;
import com.android.libraries.testing.deviceshadower.shadows.bluetooth.ShadowBluetoothSocket;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * Base class for Bluetooth Test
 */
@Config(
        shadows = {
                ShadowBluetoothAdapter.class,
                ShadowBluetoothDevice.class,
                ShadowBluetoothLeScanner.class,
                ShadowBluetoothSocket.class,
                ShadowBluetoothServerSocket.class,
                ShadowBluetoothA2dp.class
        })
public class BluetoothTestCase extends BaseTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        // TODO(b/28087747): Get bluetooth Manager from robolectric framework.
        shadowOf(RuntimeEnvironment.application)
                .setSystemService(
                        Context.BLUETOOTH_SERVICE,
                        callConstructor(BluetoothManager.class,
                                ClassParameter.from(Context.class, mContext)));
    }
}

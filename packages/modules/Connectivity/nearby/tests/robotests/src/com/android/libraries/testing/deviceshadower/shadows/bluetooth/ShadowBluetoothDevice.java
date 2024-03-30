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

import android.bluetooth.BluetoothDevice;

import com.android.libraries.testing.deviceshadower.internal.bluetooth.IBluetoothImpl;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.HashMap;
import java.util.Map;

/**
 * Placeholder for BluetoothDevice improvements
 */
@Implements(BluetoothDevice.class)
public class ShadowBluetoothDevice {

    @RealObject
    private BluetoothDevice mBluetoothDevice;
    private static final Map<String, Integer> sBondTransport = new HashMap<>();
    private static Map<String, Boolean> sPairingConfirmation = new HashMap<>();

    public ShadowBluetoothDevice() {
    }

    @Implementation
    public boolean setPasskey(int passkey) {
        return new IBluetoothImpl().setPasskey(mBluetoothDevice, passkey);
    }

    @Implementation
    public boolean createBond(int transport) {
        sBondTransport.put(mBluetoothDevice.getAddress(), transport);
        return Shadow.directlyOn(
                mBluetoothDevice,
                BluetoothDevice.class,
                "createBond",
                ClassParameter.from(int.class, transport));
    }

    public static int getBondTransport(String address) {
        return sBondTransport.containsKey(address)
                ? sBondTransport.get(address)
                : BluetoothDevice.TRANSPORT_AUTO;
    }

    @Implementation
    public boolean setPairingConfirmation(boolean confirm) {
        sPairingConfirmation.put(mBluetoothDevice.getAddress(), confirm);
        return Shadow.directlyOn(
                mBluetoothDevice,
                BluetoothDevice.class,
                "setPairingConfirmation",
                ClassParameter.from(boolean.class, confirm));
    }

    /**
     * Gets the confirmation value previously set with a call to {@link
     * BluetoothDevice#setPairingConfirmation(boolean)}. Default is false.
     */
    public static boolean getPairingConfirmation(String address) {
        return sPairingConfirmation.containsKey(address) && sPairingConfirmation.get(address);
    }

    /**
     * Resets the confirmation values.
     */
    public static void resetPairingConfirmation() {
        sPairingConfirmation = new HashMap<>();
    }
}

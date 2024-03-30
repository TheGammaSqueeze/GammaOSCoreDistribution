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

import static com.google.common.io.BaseEncoding.base16;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.common.base.Ascii;
import com.google.common.io.BaseEncoding;

import java.util.Locale;

/** Utils for dealing with Bluetooth addresses. */
public final class BluetoothAddress {

    private static final BaseEncoding ENCODING = base16().upperCase().withSeparator(":", 2);

    @VisibleForTesting
    static final String SECURE_SETTINGS_KEY_BLUETOOTH_ADDRESS = "bluetooth_address";

    /**
     * @return The string format used by e.g. {@link android.bluetooth.BluetoothDevice}. Upper case.
     *     Example: "AA:BB:CC:11:22:33"
     */
    public static String encode(byte[] address) {
        return ENCODING.encode(address);
    }

    /**
     * @param address The string format used by e.g. {@link android.bluetooth.BluetoothDevice}.
     *     Case-insensitive. Example: "AA:BB:CC:11:22:33"
     */
    public static byte[] decode(String address) {
        return ENCODING.decode(address.toUpperCase(Locale.US));
    }

    /**
     * Get public bluetooth address.
     *
     * @param context a valid {@link Context} instance.
     */
    public static @Nullable byte[] getPublicAddress(Context context) {
        String publicAddress =
                Settings.Secure.getString(
                        context.getContentResolver(), SECURE_SETTINGS_KEY_BLUETOOTH_ADDRESS);
        return publicAddress != null && BluetoothAdapter.checkBluetoothAddress(publicAddress)
                ? decode(publicAddress)
                : null;
    }

    /**
     * Hides partial information of Bluetooth address.
     * ex1: input is null, output should be empty string
     * ex2: input is String(AA:BB:CC), output should be AA:BB:CC
     * ex3: input is String(AA:BB:CC:DD:EE:FF), output should be XX:XX:XX:XX:EE:FF
     * ex4: input is String(Aa:Bb:Cc:Dd:Ee:Ff), output should be XX:XX:XX:XX:EE:FF
     * ex5: input is BluetoothDevice(AA:BB:CC:DD:EE:FF), output should be XX:XX:XX:XX:EE:FF
     */
    public static String maskBluetoothAddress(@Nullable Object address) {
        if (address == null) {
            return "";
        }

        if (address instanceof String) {
            String originalAddress = (String) address;
            String upperCasedAddress = Ascii.toUpperCase(originalAddress);
            if (!BluetoothAdapter.checkBluetoothAddress(upperCasedAddress)) {
                return originalAddress;
            }
            return convert(upperCasedAddress);
        } else if (address instanceof BluetoothDevice) {
            return convert(((BluetoothDevice) address).getAddress());
        }

        // For others, returns toString().
        return address.toString();
    }

    private static String convert(String address) {
        return "XX:XX:XX:XX:" + address.substring(12);
    }

    private BluetoothAddress() {}
}

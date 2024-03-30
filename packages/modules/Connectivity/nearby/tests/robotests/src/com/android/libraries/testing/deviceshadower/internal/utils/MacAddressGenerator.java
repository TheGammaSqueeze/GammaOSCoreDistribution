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

package com.android.libraries.testing.deviceshadower.internal.utils;

import android.bluetooth.BluetoothAdapter;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Locale;

import javax.annotation.concurrent.GuardedBy;

/**
 * A class which generates and converts valid Bluetooth MAC addresses.
 */
public class MacAddressGenerator {

    @GuardedBy("MacAddressGenerator.class")
    private static MacAddressGenerator sInstance = new MacAddressGenerator();

    @VisibleForTesting
    public static synchronized void setInstanceForTest(MacAddressGenerator generator) {
        sInstance = generator;
    }

    public static synchronized MacAddressGenerator get() {
        return sInstance;
    }

    private long mLastAddress = 0x0L;

    private MacAddressGenerator() {
    }

    public String generateMacAddress() {
        byte[] bytes = generateMacAddressBytes();
        return convertByteMacAddress(bytes);
    }

    public byte[] generateMacAddressBytes() {
        long addr = mLastAddress++;
        byte[] bytes = new byte[6];
        for (int i = 5; i >= 0; i--) {
            bytes[i] = (byte) (addr & 0xFF);
            addr = addr >> 8;
        }
        return bytes;
    }

    public static byte[] convertStringMacAddress(String address) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw new IllegalArgumentException("Not a valid bluetooth mac hex string: " + address);
        }
        byte[] bytes = new byte[6];
        String[] macValues = address.split(":");
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = Integer.decode("0x" + macValues[i]).byteValue();
        }
        return bytes;
    }

    public static String convertByteMacAddress(byte[] address) {
        if (address == null || address.length != 6) {
            throw new IllegalArgumentException("Bluetooth address must have 6 bytes");
        }
        return String.format(Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
                address[0], address[1], address[2], address[3], address[4], address[5]);
    }
}

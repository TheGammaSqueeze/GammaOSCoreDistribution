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

package com.android.server.nearby.common.ble.testing;

import com.android.server.nearby.util.ArrayUtils;
import com.android.server.nearby.util.Hex;

/**
 * Test class to provide example to unit test.
 */
public class FastPairTestData {
    private static final byte[] FAST_PAIR_RECORD_BIG_ENDIAN =
            Hex.stringToBytes("02011E020AF006162CFEAABBCC");

    /**
     * A Fast Pair frame, Note: The service UUID is FE2C, but in the
     * packet it's 2CFE, since the core Bluetooth data types are little-endian.
     *
     * <p>However, the model ID is big-endian (multi-byte values in our spec are now big-endian, aka
     * network byte order).
     *
     * @see {http://go/fast-pair-service-data}
     */
    public static byte[] getFastPairRecord() {
        return FAST_PAIR_RECORD_BIG_ENDIAN;
    }

    /** A Fast Pair frame, with a shared account key. */
    public static final byte[] FAST_PAIR_SHARED_ACCOUNT_KEY_RECORD =
            Hex.stringToBytes("02011E020AF00C162CFE007011223344556677");

    /** Model ID in {@link #getFastPairRecord()}. */
    public static final byte[] FAST_PAIR_MODEL_ID = Hex.stringToBytes("AABBCC");

    /** @see #getFastPairRecord() */
    public static byte[] newFastPairRecord(byte header, byte[] modelId) {
        return newFastPairRecord(
                modelId.length == 3 ? modelId : ArrayUtils.concatByteArrays(new byte[] {header},
                        modelId));
    }

    /** @see #getFastPairRecord() */
    public static byte[] newFastPairRecord(byte[] serviceData) {
        int length = /* length of type and service UUID = */ 3 + serviceData.length;
        return Hex.stringToBytes(
                String.format("02011E020AF0%02X162CFE%s", length,
                        Hex.bytesToStringUppercase(serviceData)));
    }

    // This is an example of advertising data with AD types
    public static byte[] adv_1 = {
            0x02, // Length of this Data
            0x01, // <<Flags>>
            0x01, // LE Limited Discoverable Mode
            0x0A, // Length of this Data
            0x09, // <<Complete local name>>
            'P', 'e', 'd', 'o', 'm', 'e', 't', 'e', 'r'
    };

    // This is an example of advertising data with positive TX Power
    // Level.
    public static byte[] adv_2 = {
            0x02, // Length of this Data
            0x0a, // <<TX Power Level>>
            127 // Level = 127
    };

    // Example data including a service data block
    public static byte[] sd1 = {
            0x02, // Length of this Data
            0x01, // <<Flags>>
            0x04, // BR/EDR Not Supported.
            0x03, // Length of this Data
            0x02, // <<Incomplete List of 16-bit Service UUIDs>>
            0x04,
            0x18, // TX Power Service UUID
            0x1e, // Length of this Data
            (byte) 0x16, // <<Service Specific Data>>
            // Service UUID
            (byte) 0xe0,
            0x00,
            // gBeacon Header
            0x15,
            // Running time ENCRYPT
            (byte) 0xd2,
            0x77,
            0x01,
            0x00,
            // Scan Freq ENCRYPT
            0x32,
            0x05,
            // Time in slow mode
            0x00,
            0x00,
            // Time in fast mode
            0x7f,
            0x17,
            // Subset of UID
            0x56,
            0x00,
            // ID Mask
            (byte) 0xd4,
            0x7c,
            0x18,
            // RFU (reserved)
            0x00,
            // GUID = decimal 1297482358
            0x76,
            0x02,
            0x56,
            0x4d,
            0x00,
            // Ranging Payload Header
            0x24,
            // MAC of scanning address
            (byte) 0xa4,
            (byte) 0xbb,
            // NORM RX RSSI -67dBm
            (byte) 0xb0,
            // NORM TX POWER -77dBm, so actual TX POWER = -36dBm
            (byte) 0xb3,
            // Note based on the values aboves PATH LOSS = (-36) - (-67) = 31dBm
            // Below zero padding added to test it is handled correctly
            0x00
    };

}

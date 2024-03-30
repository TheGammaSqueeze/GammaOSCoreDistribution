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

/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.nearby.common.ble;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SdkSuppress;

import org.junit.Test;

/** Test for Bluetooth LE {@link BleRecord}. */
public class BleRecordTest {

    // iBeacon (Apple) Packet 1
    private static final byte[] BEACON = {
            // Flags
            (byte) 0x02,
            (byte) 0x01,
            (byte) 0x06,
            // Manufacturer-specific data header
            (byte) 0x1a,
            (byte) 0xff,
            (byte) 0x4c,
            (byte) 0x00,
            // iBeacon Type
            (byte) 0x02,
            // Frame length
            (byte) 0x15,
            // iBeacon Proximity UUID
            (byte) 0xf7,
            (byte) 0x82,
            (byte) 0x6d,
            (byte) 0xa6,
            (byte) 0x4f,
            (byte) 0xa2,
            (byte) 0x4e,
            (byte) 0x98,
            (byte) 0x80,
            (byte) 0x24,
            (byte) 0xbc,
            (byte) 0x5b,
            (byte) 0x71,
            (byte) 0xe0,
            (byte) 0x89,
            (byte) 0x3e,
            // iBeacon Instance ID (Major/Minor)
            (byte) 0x44,
            (byte) 0xd0,
            (byte) 0x25,
            (byte) 0x22,
            // Tx Power
            (byte) 0xb3,
            // RSP
            (byte) 0x08,
            (byte) 0x09,
            (byte) 0x4b,
            (byte) 0x6f,
            (byte) 0x6e,
            (byte) 0x74,
            (byte) 0x61,
            (byte) 0x6b,
            (byte) 0x74,
            (byte) 0x02,
            (byte) 0x0a,
            (byte) 0xf4,
            (byte) 0x0a,
            (byte) 0x16,
            (byte) 0x0d,
            (byte) 0xd0,
            (byte) 0x74,
            (byte) 0x6d,
            (byte) 0x4d,
            (byte) 0x6b,
            (byte) 0x32,
            (byte) 0x36,
            (byte) 0x64,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00
    };

    // iBeacon (Apple) Packet 1
    private static final byte[] SAME_BEACON = {
            // Flags
            (byte) 0x02,
            (byte) 0x01,
            (byte) 0x06,
            // Manufacturer-specific data header
            (byte) 0x1a,
            (byte) 0xff,
            (byte) 0x4c,
            (byte) 0x00,
            // iBeacon Type
            (byte) 0x02,
            // Frame length
            (byte) 0x15,
            // iBeacon Proximity UUID
            (byte) 0xf7,
            (byte) 0x82,
            (byte) 0x6d,
            (byte) 0xa6,
            (byte) 0x4f,
            (byte) 0xa2,
            (byte) 0x4e,
            (byte) 0x98,
            (byte) 0x80,
            (byte) 0x24,
            (byte) 0xbc,
            (byte) 0x5b,
            (byte) 0x71,
            (byte) 0xe0,
            (byte) 0x89,
            (byte) 0x3e,
            // iBeacon Instance ID (Major/Minor)
            (byte) 0x44,
            (byte) 0xd0,
            (byte) 0x25,
            (byte) 0x22,
            // Tx Power
            (byte) 0xb3,
            // RSP
            (byte) 0x08,
            (byte) 0x09,
            (byte) 0x4b,
            (byte) 0x6f,
            (byte) 0x6e,
            (byte) 0x74,
            (byte) 0x61,
            (byte) 0x6b,
            (byte) 0x74,
            (byte) 0x02,
            (byte) 0x0a,
            (byte) 0xf4,
            (byte) 0x0a,
            (byte) 0x16,
            (byte) 0x0d,
            (byte) 0xd0,
            (byte) 0x74,
            (byte) 0x6d,
            (byte) 0x4d,
            (byte) 0x6b,
            (byte) 0x32,
            (byte) 0x36,
            (byte) 0x64,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00
    };

    // iBeacon (Apple) Packet 1 with a modified second field.
    private static final byte[] OTHER_BEACON = {
            (byte) 0x02, // Length of this Data
            (byte) 0x02, // <<Flags>>
            (byte) 0x04, // BR/EDR Not Supported.
            // Apple Specific Data
            26, // length of data that follows
            (byte) 0xff, // <<Manufacturer Specific Data>>
            // Company Identifier Code = Apple
            (byte) 0x4c, // LSB
            (byte) 0x00, // MSB
            // iBeacon Header
            0x02,
            // iBeacon Length
            0x15,
            // UUID = PROXIMITY_NOW
            // IEEE 128-bit UUID represented as UUID[15]: msb To UUID[0]: lsb
            (byte) 0x14,
            (byte) 0xe4,
            (byte) 0xfd,
            (byte) 0x9f, // UUID[15] - UUID[12]
            (byte) 0x66,
            (byte) 0x67,
            (byte) 0x4c,
            (byte) 0xcb, // UUID[11] - UUID[08]
            (byte) 0xa6,
            (byte) 0x1b,
            (byte) 0x24,
            (byte) 0xd0, // UUID[07] - UUID[04]
            (byte) 0x9a,
            (byte) 0xb1,
            (byte) 0x7e,
            (byte) 0x93, // UUID[03] - UUID[00]
            // ID as an int (decimal) = 1297482358
            (byte) 0x76, // Major H
            (byte) 0x02, // Major L
            (byte) 0x56, // Minor H
            (byte) 0x4d, // Minor L
            // Normalized Tx Power of -77dbm
            (byte) 0xb3,
            0x00, // Zero padding for testing
    };

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEquals() {
        BleRecord record = BleRecord.parseFromBytes(BEACON);
        BleRecord record2 = BleRecord.parseFromBytes(SAME_BEACON);


        assertThat(record).isEqualTo(record2);

        // Different items.
        record2 = BleRecord.parseFromBytes(OTHER_BEACON);
        assertThat(record).isNotEqualTo(record2);
        assertThat(record.hashCode()).isNotEqualTo(record2.hashCode());
    }
}


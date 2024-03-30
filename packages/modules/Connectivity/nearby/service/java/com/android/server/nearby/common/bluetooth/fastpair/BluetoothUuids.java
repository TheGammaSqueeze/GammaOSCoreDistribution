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

import java.util.UUID;

/**
 * Utilities for dealing with UUIDs assigned by the Bluetooth SIG. Has a lot in common with
 * com.android.BluetoothUuid, but that class is hidden.
 */
public class BluetoothUuids {

    /**
     * The Base UUID is used for calculating 128-bit UUIDs from "short UUIDs" (16- and 32-bit).
     *
     * @see {https://www.bluetooth.com/specifications/assigned-numbers/service-discovery}
     */
    private static final UUID BASE_UUID = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB");

    /**
     * Fast Pair custom GATT characteristics 128-bit UUIDs base.
     *
     * <p>Notes: The 16-bit value locates at the 3rd and 4th bytes.
     *
     * @see {go/fastpair-128bit-gatt}
     */
    private static final UUID FAST_PAIR_BASE_UUID =
            UUID.fromString("FE2C0000-8366-4814-8EB0-01DE32100BEA");

    private static final int BIT_INDEX_OF_16_BIT_UUID = 32;

    private BluetoothUuids() {}

    /**
     * Returns the 16-bit version of the UUID. If this is not a 16-bit UUID, throws
     * IllegalArgumentException.
     */
    public static short get16BitUuid(UUID uuid) {
        if (!is16BitUuid(uuid)) {
            throw new IllegalArgumentException("Not a 16-bit Bluetooth UUID: " + uuid);
        }
        return (short) (uuid.getMostSignificantBits() >> BIT_INDEX_OF_16_BIT_UUID);
    }

    /** Checks whether the UUID is 16 bit */
    public static boolean is16BitUuid(UUID uuid) {
        // See Service Discovery Protocol in the Bluetooth Core Specification. Bits at index 32-48
        // are the 16-bit UUID, and the rest must match the Base UUID.
        return uuid.getLeastSignificantBits() == BASE_UUID.getLeastSignificantBits()
                && (uuid.getMostSignificantBits() & 0xFFFF0000FFFFFFFFL)
                == BASE_UUID.getMostSignificantBits();
    }

    /** Converts short UUID to 128 bit UUID */
    public static UUID to128BitUuid(short shortUuid) {
        return new UUID(
                ((shortUuid & 0xFFFFL) << BIT_INDEX_OF_16_BIT_UUID)
                        | BASE_UUID.getMostSignificantBits(), BASE_UUID.getLeastSignificantBits());
    }

    /** Transfers the 16-bit Fast Pair custom GATT characteristics to 128-bit. */
    public static UUID toFastPair128BitUuid(short shortUuid) {
        return new UUID(
                ((shortUuid & 0xFFFFL) << BIT_INDEX_OF_16_BIT_UUID)
                        | FAST_PAIR_BASE_UUID.getMostSignificantBits(),
                FAST_PAIR_BASE_UUID.getLeastSignificantBits());
    }
}

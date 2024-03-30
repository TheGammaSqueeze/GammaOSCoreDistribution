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

package com.android.server.nearby.common.ble.decode;

import androidx.annotation.Nullable;

import com.android.server.nearby.common.ble.BleRecord;

/**
 * This class encapsulates the logic specific to each manufacturer for parsing formats for beacons,
 * and presents a common API to access important ADV/EIR packet fields such as:
 *
 * <ul>
 *   <li><b>UUID (universally unique identifier)</b>, a value uniquely identifying a group of one or
 *       more beacons as belonging to an organization or of a certain type, up to 128 bits.
 *   <li><b>Instance</b> a 32-bit unsigned integer that can be used to group related beacons that
 *       have the same UUID.
 *   <li>the mathematics of <b>TX signal strength</b>, used for proximity calculations.
 * </ul>
 *
 * ...and others.
 *
 * @see <a href="http://go/ble-glossary">BLE Glossary</a>
 * @see <a href="https://www.bluetooth.org/docman/handlers/downloaddoc.ashx?doc_id=245130">Bluetooth
 * Data Types Specification</a>
 */
public abstract class BeaconDecoder {
    /**
     * Returns true if the bleRecord corresponds to a beacon format that contains sufficient
     * information to construct a BeaconId and contains the Tx power.
     */
    public boolean supportsBeaconIdAndTxPower(@SuppressWarnings("unused") BleRecord bleRecord) {
        return true;
    }

    /**
     * Returns true if this decoder supports returning TxPower via {@link
     * #getCalibratedBeaconTxPower(BleRecord)}.
     */
    public boolean supportsTxPower() {
        return true;
    }

    /**
     * Reads the calibrated transmitted power at 1 meter of the beacon in dBm. This value is
     * contained
     * in the scan record, as set by the transmitting beacon. Suitable for use in computing path
     * loss,
     * distance, and related derived values.
     *
     * @param bleRecord the parsed payload contained in the beacon packet
     * @return integer value of the calibrated Tx power in dBm or null if the bleRecord doesn't
     * contain sufficient information to calculate the Tx power.
     */
    @Nullable
    public abstract Integer getCalibratedBeaconTxPower(BleRecord bleRecord);

    /**
     * Extract telemetry information from the beacon. Byte 0 of the returned telemetry block should
     * encode the telemetry format.
     *
     * @return telemetry block for this beacon, or null if no telemetry data is found in the scan
     * record.
     */
    @Nullable
    public byte[] getTelemetry(@SuppressWarnings("unused") BleRecord bleRecord) {
        return null;
    }

    /** Returns the appropriate type for this scan record. */
    public abstract int getBeaconIdType();

    /**
     * Returns an array of bytes which uniquely identify this beacon, for beacons from any of the
     * supported beacon types. This unique identifier is the indexing key for various internal
     * services. Returns null if the bleRecord doesn't contain sufficient information to construct
     * the
     * ID.
     */
    @Nullable
    public abstract byte[] getBeaconIdBytes(BleRecord bleRecord);

    /**
     * Returns the URL of the beacon. Returns null if the bleRecord doesn't contain a URL or
     * contains
     * a malformed URL.
     */
    @Nullable
    public String getUrl(BleRecord bleRecord) {
        return null;
    }
}

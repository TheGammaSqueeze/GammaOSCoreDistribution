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

import android.bluetooth.le.ScanRecord;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.android.server.nearby.common.ble.BleFilter;
import com.android.server.nearby.common.ble.BleRecord;

import java.util.Arrays;

/**
 * Parses Fast Pair information out of {@link BleRecord}s.
 *
 * <p>There are 2 different packet formats that are supported, which is used can be determined by
 * packet length:
 *
 * <p>For 3-byte packets, the full packet is the model ID.
 *
 * <p>For all other packets, the first byte is the header, followed by the model ID, followed by
 * zero or more extra fields. Each field has its own header byte followed by the field value. The
 * packet header is formatted as 0bVVVLLLLR (V = version, L = model ID length, R = reserved) and
 * each extra field header is 0bLLLLTTTT (L = field length, T = field type).
 *
 * @see <a href="http://go/fast-pair-2-service-data">go/fast-pair-2-service-data</a>
 */
public class FastPairDecoder extends BeaconDecoder {

    private static final int FIELD_TYPE_BLOOM_FILTER = 0;
    private static final int FIELD_TYPE_BLOOM_FILTER_SALT = 1;
    private static final int FIELD_TYPE_BLOOM_FILTER_NO_NOTIFICATION = 2;
    private static final int FIELD_TYPE_BATTERY = 3;
    private static final int FIELD_TYPE_BATTERY_NO_NOTIFICATION = 4;
    public static final int FIELD_TYPE_CONNECTION_STATE = 5;
    private static final int FIELD_TYPE_RANDOM_RESOLVABLE_DATA = 6;

    /** FE2C is the 16-bit Service UUID. The rest is the base UUID. See BluetoothUuid (hidden). */
    private static final ParcelUuid FAST_PAIR_SERVICE_PARCEL_UUID =
            ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB");

    /** The filter you use to scan for Fast Pair BLE advertisements. */
    public static final BleFilter FILTER =
            new BleFilter.Builder().setServiceData(FAST_PAIR_SERVICE_PARCEL_UUID,
                    new byte[0]).build();

    // NOTE: Ensure that all bitmasks are always ints, not bytes so that bitshifting works correctly
    // without needing worry about signing errors.
    private static final int HEADER_VERSION_BITMASK = 0b11100000;
    private static final int HEADER_LENGTH_BITMASK = 0b00011110;
    private static final int HEADER_VERSION_OFFSET = 5;
    private static final int HEADER_LENGTH_OFFSET = 1;

    private static final int EXTRA_FIELD_LENGTH_BITMASK = 0b11110000;
    private static final int EXTRA_FIELD_TYPE_BITMASK = 0b00001111;
    private static final int EXTRA_FIELD_LENGTH_OFFSET = 4;
    private static final int EXTRA_FIELD_TYPE_OFFSET = 0;

    private static final int MIN_ID_LENGTH = 3;
    private static final int MAX_ID_LENGTH = 14;
    private static final int HEADER_INDEX = 0;
    private static final int HEADER_LENGTH = 1;
    private static final int FIELD_HEADER_LENGTH = 1;

    // Not using java.util.IllegalFormatException because it is unchecked.
    private static class IllegalFormatException extends Exception {
        private IllegalFormatException(String message) {
            super(message);
        }
    }

    @Nullable
    @Override
    public Integer getCalibratedBeaconTxPower(BleRecord bleRecord) {
        return null;
    }

    // TODO(b/205320613) create beacon type
    @Override
    public int getBeaconIdType() {
        return 1;
    }

    /** Returns the Model ID from our service data, if present. */
    @Nullable
    @Override
    public byte[] getBeaconIdBytes(BleRecord bleRecord) {
        return getModelId(bleRecord.getServiceData(FAST_PAIR_SERVICE_PARCEL_UUID));
    }

    /** Returns the Model ID from our service data, if present. */
    @Nullable
    public static byte[] getModelId(@Nullable byte[] serviceData) {
        if (serviceData == null) {
            return null;
        }

        if (serviceData.length >= MIN_ID_LENGTH) {
            if (serviceData.length == MIN_ID_LENGTH) {
                // If the length == 3, all bytes are the ID. See flag docs for more about
                // endianness.
                return serviceData;
            } else {
                // Otherwise, the first byte is a header which contains the length of the
                // big-endian model
                // ID that follows. The model ID will be trimmed if it contains leading zeros.
                int idIndex = 1;
                int end = idIndex + getIdLength(serviceData);
                while (serviceData[idIndex] == 0 && end - idIndex > MIN_ID_LENGTH) {
                    idIndex++;
                }
                return Arrays.copyOfRange(serviceData, idIndex, end);
            }
        }
        return null;
    }

    /** Gets the FastPair service data array if available, otherwise returns null. */
    @Nullable
    public static byte[] getServiceDataArray(BleRecord bleRecord) {
        return bleRecord.getServiceData(FAST_PAIR_SERVICE_PARCEL_UUID);
    }

    /** Gets the FastPair service data array if available, otherwise returns null. */
    @Nullable
    public static byte[] getServiceDataArray(ScanRecord scanRecord) {
        return scanRecord.getServiceData(FAST_PAIR_SERVICE_PARCEL_UUID);
    }

    /** Gets the bloom filter from the extra fields if available, otherwise returns null. */
    @Nullable
    public static byte[] getBloomFilter(@Nullable byte[] serviceData) {
        return getExtraField(serviceData, FIELD_TYPE_BLOOM_FILTER);
    }

    /** Gets the bloom filter salt from the extra fields if available, otherwise returns null. */
    @Nullable
    public static byte[] getBloomFilterSalt(byte[] serviceData) {
        return getExtraField(serviceData, FIELD_TYPE_BLOOM_FILTER_SALT);
    }

    /**
     * Gets the suppress notification with bloom filter from the extra fields if available,
     * otherwise
     * returns null.
     */
    @Nullable
    public static byte[] getBloomFilterNoNotification(@Nullable byte[] serviceData) {
        return getExtraField(serviceData, FIELD_TYPE_BLOOM_FILTER_NO_NOTIFICATION);
    }

    /** Gets the battery level from extra fields if available, otherwise return null. */
    @Nullable
    public static byte[] getBatteryLevel(byte[] serviceData) {
        return getExtraField(serviceData, FIELD_TYPE_BATTERY);
    }

    /**
     * Gets the suppress notification with battery level from extra fields if available, otherwise
     * return null.
     */
    @Nullable
    public static byte[] getBatteryLevelNoNotification(byte[] serviceData) {
        return getExtraField(serviceData, FIELD_TYPE_BATTERY_NO_NOTIFICATION);
    }

    /**
     * Gets the random resolvable data from extra fields if available, otherwise
     * return null.
     */
    @Nullable
    public static byte[] getRandomResolvableData(byte[] serviceData) {
        return getExtraField(serviceData, FIELD_TYPE_RANDOM_RESOLVABLE_DATA);
    }

    @Nullable
    private static byte[] getExtraField(@Nullable byte[] serviceData, int fieldId) {
        if (serviceData == null || serviceData.length < HEADER_INDEX + HEADER_LENGTH) {
            return null;
        }
        try {
            return getExtraFields(serviceData).get(fieldId);
        } catch (IllegalFormatException e) {
            Log.v("FastPairDecode", "Extra fields incorrectly formatted.");
            return null;
        }
    }

    /** Gets extra field data at the end of the packet, defined by the extra field header. */
    private static SparseArray<byte[]> getExtraFields(byte[] serviceData)
            throws IllegalFormatException {
        SparseArray<byte[]> extraFields = new SparseArray<>();
        if (getVersion(serviceData) != 0) {
            return extraFields;
        }
        int headerIndex = getFirstExtraFieldHeaderIndex(serviceData);
        while (headerIndex < serviceData.length) {
            int length = getExtraFieldLength(serviceData, headerIndex);
            int index = headerIndex + FIELD_HEADER_LENGTH;
            int type = getExtraFieldType(serviceData, headerIndex);
            int end = index + length;
            if (extraFields.get(type) == null) {
                if (end <= serviceData.length) {
                    extraFields.put(type, Arrays.copyOfRange(serviceData, index, end));
                } else {
                    throw new IllegalFormatException(
                            "Invalid length, " + end + " is longer than service data size "
                                    + serviceData.length);
                }
            }
            headerIndex = end;
        }
        return extraFields;
    }

    /** Checks whether or not a valid ID is included in the service data packet. */
    public static boolean hasBeaconIdBytes(BleRecord bleRecord) {
        byte[] serviceData = bleRecord.getServiceData(FAST_PAIR_SERVICE_PARCEL_UUID);
        return checkModelId(serviceData);
    }

    /** Check whether byte array is FastPair model id or not. */
    public static boolean checkModelId(@Nullable byte[] scanResult) {
        return scanResult != null
                // The 3-byte format has no header byte (all bytes are the ID).
                && (scanResult.length == MIN_ID_LENGTH
                // Header byte exists. We support only format version 0. (A different version
                // indicates
                // a breaking change in the format.)
                || (scanResult.length > MIN_ID_LENGTH
                && getVersion(scanResult) == 0
                && isIdLengthValid(scanResult)));
    }

    /** Checks whether or not bloom filter is included in the service data packet. */
    public static boolean hasBloomFilter(BleRecord bleRecord) {
        return (getBloomFilter(getServiceDataArray(bleRecord)) != null
                || getBloomFilterNoNotification(getServiceDataArray(bleRecord)) != null);
    }

    /** Checks whether or not bloom filter is included in the service data packet. */
    public static boolean hasBloomFilter(ScanRecord scanRecord) {
        return (getBloomFilter(getServiceDataArray(scanRecord)) != null
                || getBloomFilterNoNotification(getServiceDataArray(scanRecord)) != null);
    }

    private static int getVersion(byte[] serviceData) {
        return serviceData.length == MIN_ID_LENGTH
                ? 0
                : (serviceData[HEADER_INDEX] & HEADER_VERSION_BITMASK) >> HEADER_VERSION_OFFSET;
    }

    private static int getIdLength(byte[] serviceData) {
        return serviceData.length == MIN_ID_LENGTH
                ? MIN_ID_LENGTH
                : (serviceData[HEADER_INDEX] & HEADER_LENGTH_BITMASK) >> HEADER_LENGTH_OFFSET;
    }

    private static int getFirstExtraFieldHeaderIndex(byte[] serviceData) {
        return HEADER_INDEX + HEADER_LENGTH + getIdLength(serviceData);
    }

    private static int getExtraFieldLength(byte[] serviceData, int extraFieldIndex) {
        return (serviceData[extraFieldIndex] & EXTRA_FIELD_LENGTH_BITMASK)
                >> EXTRA_FIELD_LENGTH_OFFSET;
    }

    private static int getExtraFieldType(byte[] serviceData, int extraFieldIndex) {
        return (serviceData[extraFieldIndex] & EXTRA_FIELD_TYPE_BITMASK) >> EXTRA_FIELD_TYPE_OFFSET;
    }

    private static boolean isIdLengthValid(byte[] serviceData) {
        int idLength = getIdLength(serviceData);
        return MIN_ID_LENGTH <= idLength
                && idLength <= MAX_ID_LENGTH
                && idLength + HEADER_LENGTH <= serviceData.length;
    }
}


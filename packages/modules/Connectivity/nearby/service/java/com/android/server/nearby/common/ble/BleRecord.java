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

import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.android.server.nearby.common.ble.util.StringUtils;

import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a BLE record from Bluetooth LE scan.
 */
public final class BleRecord {

    // The following data type values are assigned by Bluetooth SIG.
    // For more details refer to Bluetooth 4.1 specification, Volume 3, Part C, Section 18.
    private static final int DATA_TYPE_FLAGS = 0x01;
    private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL = 0x02;
    private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE = 0x03;
    private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL = 0x04;
    private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE = 0x05;
    private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL = 0x06;
    private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE = 0x07;
    private static final int DATA_TYPE_LOCAL_NAME_SHORT = 0x08;
    private static final int DATA_TYPE_LOCAL_NAME_COMPLETE = 0x09;
    private static final int DATA_TYPE_TX_POWER_LEVEL = 0x0A;
    private static final int DATA_TYPE_SERVICE_DATA = 0x16;
    private static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 0xFF;

    /** The base 128-bit UUID representation of a 16-bit UUID. */
    private static final ParcelUuid BASE_UUID =
            ParcelUuid.fromString("00000000-0000-1000-8000-00805F9B34FB");
    /** Length of bytes for 16 bit UUID. */
    private static final int UUID_BYTES_16_BIT = 2;
    /** Length of bytes for 32 bit UUID. */
    private static final int UUID_BYTES_32_BIT = 4;
    /** Length of bytes for 128 bit UUID. */
    private static final int UUID_BYTES_128_BIT = 16;

    // Flags of the advertising data.
    // -1 when the scan record is not valid.
    private final int mAdvertiseFlags;

    private final ImmutableList<ParcelUuid> mServiceUuids;

    // null when the scan record is not valid.
    @Nullable
    private final SparseArray<byte[]> mManufacturerSpecificData;

    // null when the scan record is not valid.
    @Nullable
    private final Map<ParcelUuid, byte[]> mServiceData;

    // Transmission power level(in dB).
    // Integer.MIN_VALUE when the scan record is not valid.
    private final int mTxPowerLevel;

    // Local name of the Bluetooth LE device.
    // null when the scan record is not valid.
    @Nullable
    private final String mDeviceName;

    // Raw bytes of scan record.
    // Never null, whether valid or not.
    private final byte[] mBytes;

    // If the raw scan record byte[] cannot be parsed, all non-primitive args here other than the
    // raw scan record byte[] and serviceUudis will be null. See parsefromBytes().
    private BleRecord(
            List<ParcelUuid> serviceUuids,
            @Nullable SparseArray<byte[]> manufacturerData,
            @Nullable Map<ParcelUuid, byte[]> serviceData,
            int advertiseFlags,
            int txPowerLevel,
            @Nullable String deviceName,
            byte[] bytes) {
        this.mServiceUuids = ImmutableList.copyOf(serviceUuids);
        mManufacturerSpecificData = manufacturerData;
        this.mServiceData = serviceData;
        this.mDeviceName = deviceName;
        this.mAdvertiseFlags = advertiseFlags;
        this.mTxPowerLevel = txPowerLevel;
        this.mBytes = bytes;
    }

    /**
     * Returns a list of service UUIDs within the advertisement that are used to identify the
     * bluetooth GATT services.
     */
    public ImmutableList<ParcelUuid> getServiceUuids() {
        return mServiceUuids;
    }

    /**
     * Returns a sparse array of manufacturer identifier and its corresponding manufacturer specific
     * data.
     */
    @Nullable
    public SparseArray<byte[]> getManufacturerSpecificData() {
        return mManufacturerSpecificData;
    }

    /**
     * Returns the manufacturer specific data associated with the manufacturer id. Returns {@code
     * null} if the {@code manufacturerId} is not found.
     */
    @Nullable
    public byte[] getManufacturerSpecificData(int manufacturerId) {
        if (mManufacturerSpecificData == null) {
            return null;
        }
        return mManufacturerSpecificData.get(manufacturerId);
    }

    /** Returns a map of service UUID and its corresponding service data. */
    @Nullable
    public Map<ParcelUuid, byte[]> getServiceData() {
        return mServiceData;
    }

    /**
     * Returns the service data byte array associated with the {@code serviceUuid}. Returns {@code
     * null} if the {@code serviceDataUuid} is not found.
     */
    @Nullable
    public byte[] getServiceData(ParcelUuid serviceDataUuid) {
        if (serviceDataUuid == null || mServiceData == null) {
            return null;
        }
        return mServiceData.get(serviceDataUuid);
    }

    /**
     * Returns the transmission power level of the packet in dBm. Returns {@link Integer#MIN_VALUE}
     * if
     * the field is not set. This value can be used to calculate the path loss of a received packet
     * using the following equation:
     *
     * <p><code>pathloss = txPowerLevel - rssi</code>
     */
    public int getTxPowerLevel() {
        return mTxPowerLevel;
    }

    /** Returns the local name of the BLE device. The is a UTF-8 encoded string. */
    @Nullable
    public String getDeviceName() {
        return mDeviceName;
    }

    /** Returns raw bytes of scan record. */
    public byte[] getBytes() {
        return mBytes;
    }

    /**
     * Parse scan record bytes to {@link BleRecord}.
     *
     * <p>The format is defined in Bluetooth 4.1 specification, Volume 3, Part C, Section 11 and 18.
     *
     * <p>All numerical multi-byte entities and values shall use little-endian <strong>byte</strong>
     * order.
     *
     * @param scanRecord The scan record of Bluetooth LE advertisement and/or scan response.
     */
    public static BleRecord parseFromBytes(byte[] scanRecord) {
        int currentPos = 0;
        int advertiseFlag = -1;
        List<ParcelUuid> serviceUuids = new ArrayList<>();
        String localName = null;
        int txPowerLevel = Integer.MIN_VALUE;

        SparseArray<byte[]> manufacturerData = new SparseArray<>();
        Map<ParcelUuid, byte[]> serviceData = new HashMap<>();

        try {
            while (currentPos < scanRecord.length) {
                // length is unsigned int.
                int length = scanRecord[currentPos++] & 0xFF;
                if (length == 0) {
                    break;
                }
                // Note the length includes the length of the field type itself.
                int dataLength = length - 1;
                // fieldType is unsigned int.
                int fieldType = scanRecord[currentPos++] & 0xFF;
                switch (fieldType) {
                    case DATA_TYPE_FLAGS:
                        advertiseFlag = scanRecord[currentPos] & 0xFF;
                        break;
                    case DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL:
                    case DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE:
                        parseServiceUuid(scanRecord, currentPos, dataLength, UUID_BYTES_16_BIT,
                                serviceUuids);
                        break;
                    case DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL:
                    case DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE:
                        parseServiceUuid(scanRecord, currentPos, dataLength, UUID_BYTES_32_BIT,
                                serviceUuids);
                        break;
                    case DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL:
                    case DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE:
                        parseServiceUuid(scanRecord, currentPos, dataLength, UUID_BYTES_128_BIT,
                                serviceUuids);
                        break;
                    case DATA_TYPE_LOCAL_NAME_SHORT:
                    case DATA_TYPE_LOCAL_NAME_COMPLETE:
                        localName = new String(extractBytes(scanRecord, currentPos, dataLength));
                        break;
                    case DATA_TYPE_TX_POWER_LEVEL:
                        txPowerLevel = scanRecord[currentPos];
                        break;
                    case DATA_TYPE_SERVICE_DATA:
                        // The first two bytes of the service data are service data UUID in little
                        // endian. The rest bytes are service data.
                        int serviceUuidLength = UUID_BYTES_16_BIT;
                        byte[] serviceDataUuidBytes = extractBytes(scanRecord, currentPos,
                                serviceUuidLength);
                        ParcelUuid serviceDataUuid = parseUuidFrom(serviceDataUuidBytes);
                        byte[] serviceDataArray =
                                extractBytes(
                                        scanRecord, currentPos + serviceUuidLength,
                                        dataLength - serviceUuidLength);
                        serviceData.put(serviceDataUuid, serviceDataArray);
                        break;
                    case DATA_TYPE_MANUFACTURER_SPECIFIC_DATA:
                        // The first two bytes of the manufacturer specific data are
                        // manufacturer ids in little endian.
                        int manufacturerId =
                                ((scanRecord[currentPos + 1] & 0xFF) << 8) + (scanRecord[currentPos]
                                        & 0xFF);
                        byte[] manufacturerDataBytes = extractBytes(scanRecord, currentPos + 2,
                                dataLength - 2);
                        manufacturerData.put(manufacturerId, manufacturerDataBytes);
                        break;
                    default:
                        // Just ignore, we don't handle such data type.
                        break;
                }
                currentPos += dataLength;
            }

            return new BleRecord(
                    serviceUuids,
                    manufacturerData,
                    serviceData,
                    advertiseFlag,
                    txPowerLevel,
                    localName,
                    scanRecord);
        } catch (Exception e) {
            Log.w("BleRecord", "Unable to parse scan record: " + Arrays.toString(scanRecord), e);
            // As the record is invalid, ignore all the parsed results for this packet
            // and return an empty record with raw scanRecord bytes in results
            // check at the top of this method does? Maybe we expect callers to use the
            // scanRecord part in
            // some fallback. But if that's the reason, it would seem we still can return null.
            // They still
            // have the raw scanRecord in hand, 'cause they passed it to us. It seems too easy for a
            // caller to misuse this "empty" BleRecord (as in b/22693067).
            return new BleRecord(ImmutableList.of(), null, null, -1, Integer.MIN_VALUE, null,
                    scanRecord);
        }
    }

    // Parse service UUIDs.
    private static int parseServiceUuid(
            byte[] scanRecord,
            int currentPos,
            int dataLength,
            int uuidLength,
            List<ParcelUuid> serviceUuids) {
        while (dataLength > 0) {
            byte[] uuidBytes = extractBytes(scanRecord, currentPos, uuidLength);
            serviceUuids.add(parseUuidFrom(uuidBytes));
            dataLength -= uuidLength;
            currentPos += uuidLength;
        }
        return currentPos;
    }

    // Helper method to extract bytes from byte array.
    private static byte[] extractBytes(byte[] scanRecord, int start, int length) {
        byte[] bytes = new byte[length];
        System.arraycopy(scanRecord, start, bytes, 0, length);
        return bytes;
    }

    @Override
    public String toString() {
        return "BleRecord [advertiseFlags="
                + mAdvertiseFlags
                + ", serviceUuids="
                + mServiceUuids
                + ", manufacturerSpecificData="
                + StringUtils.toString(mManufacturerSpecificData)
                + ", serviceData="
                + StringUtils.toString(mServiceData)
                + ", txPowerLevel="
                + mTxPowerLevel
                + ", deviceName="
                + mDeviceName
                + "]";
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof BleRecord)) {
            return false;
        }
        BleRecord record = (BleRecord) obj;
        // BleRecord objects are built from bytes, so we only need that field.
        return Arrays.equals(mBytes, record.mBytes);
    }

    @Override
    public int hashCode() {
        // BleRecord objects are built from bytes, so we only need that field.
        return Arrays.hashCode(mBytes);
    }

    /**
     * Parse UUID from bytes. The {@code uuidBytes} can represent a 16-bit, 32-bit or 128-bit UUID,
     * but the returned UUID is always in 128-bit format. Note UUID is little endian in Bluetooth.
     *
     * @param uuidBytes Byte representation of uuid.
     * @return {@link ParcelUuid} parsed from bytes.
     * @throws IllegalArgumentException If the {@code uuidBytes} cannot be parsed.
     */
    private static ParcelUuid parseUuidFrom(byte[] uuidBytes) {
        if (uuidBytes == null) {
            throw new IllegalArgumentException("uuidBytes cannot be null");
        }
        int length = uuidBytes.length;
        if (length != UUID_BYTES_16_BIT
                && length != UUID_BYTES_32_BIT
                && length != UUID_BYTES_128_BIT) {
            throw new IllegalArgumentException("uuidBytes length invalid - " + length);
        }
        // Construct a 128 bit UUID.
        if (length == UUID_BYTES_128_BIT) {
            ByteBuffer buf = ByteBuffer.wrap(uuidBytes).order(ByteOrder.LITTLE_ENDIAN);
            long msb = buf.getLong(8);
            long lsb = buf.getLong(0);
            return new ParcelUuid(new UUID(msb, lsb));
        }
        // For 16 bit and 32 bit UUID we need to convert them to 128 bit value.
        // 128_bit_value = uuid * 2^96 + BASE_UUID
        long shortUuid;
        if (length == UUID_BYTES_16_BIT) {
            shortUuid = uuidBytes[0] & 0xFF;
            shortUuid += (uuidBytes[1] & 0xFF) << 8;
        } else {
            shortUuid = uuidBytes[0] & 0xFF;
            shortUuid += (uuidBytes[1] & 0xFF) << 8;
            shortUuid += (uuidBytes[2] & 0xFF) << 16;
            shortUuid += (uuidBytes[3] & 0xFF) << 24;
        }
        long msb = BASE_UUID.getUuid().getMostSignificantBits() + (shortUuid << 32);
        long lsb = BASE_UUID.getUuid().getLeastSignificantBits();
        return new ParcelUuid(new UUID(msb, lsb));
    }
}


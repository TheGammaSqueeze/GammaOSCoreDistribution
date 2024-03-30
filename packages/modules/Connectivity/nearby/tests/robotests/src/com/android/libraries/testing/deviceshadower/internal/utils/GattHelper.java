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

import android.bluetooth.le.AdvertiseData;
import android.os.ParcelUuid;
import android.util.SparseArray;

import com.android.libraries.testing.deviceshadower.internal.bluetooth.BluetoothConstants;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * Helper class for Gatt functionality.
 */
public class GattHelper {

    public static byte[] convertAdvertiseData(
            AdvertiseData data, int txPowerLevel, String localName, boolean isConnectable) {
        if (data == null) {
            return new byte[0];
        }
        ByteArrayDataOutput result = ByteStreams.newDataOutput();
        if (isConnectable) {
            writeDataUnit(
                    result,
                    BluetoothConstants.DATA_TYPE_FLAGS,
                    new byte[]{BluetoothConstants.FLAGS_IN_CONNECTABLE_PACKETS});
        }
        // tx power level is signed 8-bit int, range -100 to 20.
        if (data.getIncludeTxPowerLevel()) {
            writeDataUnit(
                    result,
                    BluetoothConstants.DATA_TYPE_TX_POWER_LEVEL,
                    new byte[]{(byte) txPowerLevel});
        }
        // Local name
        if (data.getIncludeDeviceName()) {
            writeDataUnit(
                    result,
                    BluetoothConstants.DATA_TYPE_LOCAL_NAME_COMPLETE,
                    localName.getBytes(Charset.defaultCharset()));
        }
        // Manufacturer data
        SparseArray<byte[]> manufacturerData = data.getManufacturerSpecificData();
        for (int i = 0; i < manufacturerData.size(); i++) {
            int manufacturerId = manufacturerData.keyAt(i);
            writeDataUnit(
                    result,
                    BluetoothConstants.DATA_TYPE_MANUFACTURER_SPECIFIC_DATA,
                    parseManufacturerData(manufacturerId, manufacturerData.get(manufacturerId))
            );
        }
        // Service data
        Map<ParcelUuid, byte[]> serviceData = data.getServiceData();
        for (Entry<ParcelUuid, byte[]> entry : serviceData.entrySet()) {
            writeDataUnit(
                    result,
                    BluetoothConstants.DATA_TYPE_SERVICE_DATA,
                    parseServiceData(entry.getKey().getUuid(), entry.getValue())
            );
        }
        // Service UUID, 128-bit UUID in little endian
        if (data.getServiceUuids() != null && !data.getServiceUuids().isEmpty()) {
            ByteBuffer uuidBytes =
                    ByteBuffer.allocate(data.getServiceUuids().size() * 16)
                            .order(ByteOrder.LITTLE_ENDIAN);
            for (ParcelUuid parcelUuid : data.getServiceUuids()) {
                UUID uuid = parcelUuid.getUuid();
                uuidBytes.putLong(uuid.getLeastSignificantBits())
                        .putLong(uuid.getMostSignificantBits());
            }
            writeDataUnit(
                    result,
                    BluetoothConstants.DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE,
                    uuidBytes.array()
            );
        }
        return result.toByteArray();
    }

    private static byte[] parseServiceData(UUID uuid, byte[] serviceData) {
        // First two bytes of the data are data UUID in little endian
        int length = 2 + serviceData.length;
        byte[] result = new byte[length];
        // extract 16-bit UUID value
        int uuidValue = (int) ((uuid.getMostSignificantBits() & 0x0000FFFF00000000L) >>> 32);
        result[0] = (byte) (uuidValue & 0xFF);
        result[1] = (byte) ((uuidValue >> 8) & 0xFF);
        System.arraycopy(serviceData, 0, result, 2, serviceData.length);
        return result;

    }

    private static byte[] parseManufacturerData(int manufacturerId, byte[] manufacturerData) {
        // First two bytes are manufacturer id in little endian.
        int length = 2 + manufacturerData.length;
        byte[] result = new byte[length];
        result[0] = (byte) (manufacturerId & 0xFF);
        result[1] = (byte) ((manufacturerId >> 8) & 0xFF);
        System.arraycopy(manufacturerData, 0, result, 2, manufacturerData.length);
        return result;
    }

    private static void writeDataUnit(ByteArrayDataOutput output, int type, byte[] data) {
        // Length includes the length of the field type, which is 1 byte.
        int length = 1 + data.length;
        // Length and type are unsigned 8-bit int. Assume the values are valid.
        output.write(length);
        output.write(type);
        output.write(data);
    }

    private GattHelper() {
    }
}

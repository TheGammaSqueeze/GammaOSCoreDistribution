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

package com.android.server.nearby.common.ble;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.nearby.common.ble.testing.FastPairTestData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class BleFilterTest {


    public static final ParcelUuid EDDYSTONE_SERVICE_DATA_PARCELUUID =
            ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");

    private ParcelUuid mServiceDataUuid;
    private BleSighting mBleSighting;
    private BleFilter.Builder mFilterBuilder;

    @Before
    public void setUp() throws Exception {
        // This is the service data UUID in TestData.sd1.
        // Can't be static because of Robolectric.
        mServiceDataUuid = ParcelUuid.fromString("000000E0-0000-1000-8000-00805F9B34FB");

        byte[] bleRecordBytes =
                new byte[]{
                        0x02,
                        0x01,
                        0x1a, // advertising flags
                        0x05,
                        0x02,
                        0x0b,
                        0x11,
                        0x0a,
                        0x11, // 16 bit service uuids
                        0x04,
                        0x09,
                        0x50,
                        0x65,
                        0x64, // setName
                        0x02,
                        0x0A,
                        (byte) 0xec, // tx power level
                        0x05,
                        0x16,
                        0x0b,
                        0x11,
                        0x50,
                        0x64, // service data
                        0x05,
                        (byte) 0xff,
                        (byte) 0xe0,
                        0x00,
                        0x02,
                        0x15, // manufacturer specific data
                        0x03,
                        0x50,
                        0x01,
                        0x02, // an unknown data type won't cause trouble
                };

        mBleSighting = new BleSighting(null /* device */, bleRecordBytes,
                -10, 1397545200000000L);
        mFilterBuilder = new BleFilter.Builder();
    }

    @Test
    public void setNameFilter() {
        BleFilter filter = mFilterBuilder.setDeviceName("Ped").build();
        assertThat(filter.matches(mBleSighting)).isTrue();

        filter = mFilterBuilder.setDeviceName("Pem").build();
        assertThat(filter.matches(mBleSighting)).isFalse();
    }

    @Test
    public void setServiceUuidFilter() {
        BleFilter filter =
                mFilterBuilder.setServiceUuid(
                        ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB"))
                        .build();
        assertThat(filter.matches(mBleSighting)).isTrue();

        filter =
                mFilterBuilder.setServiceUuid(
                        ParcelUuid.fromString("0000110C-0000-1000-8000-00805F9B34FB"))
                        .build();
        assertThat(filter.matches(mBleSighting)).isFalse();

        filter =
                mFilterBuilder
                        .setServiceUuid(
                                ParcelUuid.fromString("0000110C-0000-1000-8000-00805F9B34FB"),
                                ParcelUuid.fromString("FFFFFFF0-FFFF-FFFF-FFFF-FFFFFFFFFFFF"))
                        .build();
        assertThat(filter.matches(mBleSighting)).isTrue();
    }

    @Test
    public void setServiceDataFilter() {
        byte[] setServiceData = new byte[]{0x50, 0x64};
        ParcelUuid serviceDataUuid = ParcelUuid.fromString("0000110B-0000-1000-8000-00805F9B34FB");
        BleFilter filter = mFilterBuilder.setServiceData(serviceDataUuid, setServiceData).build();
        assertThat(filter.matches(mBleSighting)).isTrue();

        byte[] emptyData = new byte[0];
        filter = mFilterBuilder.setServiceData(serviceDataUuid, emptyData).build();
        assertThat(filter.matches(mBleSighting)).isTrue();

        byte[] prefixData = new byte[]{0x50};
        filter = mFilterBuilder.setServiceData(serviceDataUuid, prefixData).build();
        assertThat(filter.matches(mBleSighting)).isTrue();

        byte[] nonMatchData = new byte[]{0x51, 0x64};
        byte[] mask = new byte[]{(byte) 0x00, (byte) 0xFF};
        filter = mFilterBuilder.setServiceData(serviceDataUuid, nonMatchData, mask).build();
        assertThat(filter.matches(mBleSighting)).isTrue();

        filter = mFilterBuilder.setServiceData(serviceDataUuid, nonMatchData).build();
        assertThat(filter.matches(mBleSighting)).isFalse();
    }

    @Test
    public void manufacturerSpecificData() {
        byte[] setManufacturerData = new byte[]{0x02, 0x15};
        int manufacturerId = 0xE0;
        BleFilter filter =
                mFilterBuilder.setManufacturerData(manufacturerId, setManufacturerData).build();
        assertThat(filter.matches(mBleSighting)).isTrue();

        byte[] emptyData = new byte[0];
        filter = mFilterBuilder.setManufacturerData(manufacturerId, emptyData).build();
        assertThat(filter.matches(mBleSighting)).isTrue();

        byte[] prefixData = new byte[]{0x02};
        filter = mFilterBuilder.setManufacturerData(manufacturerId, prefixData).build();
        assertThat(filter.matches(mBleSighting)).isTrue();

        // Data and mask are nullable. Check that we still match when they're null.
        filter = mFilterBuilder.setManufacturerData(manufacturerId,
                null /* data */).build();
        assertThat(filter.matches(mBleSighting)).isTrue();
        filter = mFilterBuilder.setManufacturerData(manufacturerId,
                null /* data */, null /* mask */).build();
        assertThat(filter.matches(mBleSighting)).isTrue();

        // Test data mask
        byte[] nonMatchData = new byte[]{0x02, 0x14};
        filter = mFilterBuilder.setManufacturerData(manufacturerId, nonMatchData).build();
        assertThat(filter.matches(mBleSighting)).isFalse();
        byte[] mask = new byte[]{(byte) 0xFF, (byte) 0x00};
        filter = mFilterBuilder.setManufacturerData(manufacturerId, nonMatchData, mask).build();
        assertThat(filter.matches(mBleSighting)).isTrue();
    }

    @Test
    public void manufacturerDataNotInBleRecord() {
        byte[] bleRecord = FastPairTestData.adv_2;
        // Verify manufacturer with no data
        byte[] data = {(byte) 0xe0, (byte) 0x00};
        BleFilter filter = mFilterBuilder.setManufacturerData(0x00e0, data).build();
        assertThat(matches(filter, null, 0, bleRecord)).isFalse();
    }

    @Test
    public void manufacturerDataMaskNotInBleRecord() {
        byte[] bleRecord = FastPairTestData.adv_2;

        // Verify matching partial manufacturer with data and mask
        byte[] data = {(byte) 0x15};
        byte[] mask = {(byte) 0xff};

        BleFilter filter = mFilterBuilder
                .setManufacturerData(0x00e0, data, mask).build();
        assertThat(matches(filter, null, 0, bleRecord)).isFalse();
    }


    @Test
    public void serviceData() throws Exception {
        byte[] bleRecord = FastPairTestData.sd1;
        byte[] serviceData = {(byte) 0x15};

        // Verify manufacturer 2-byte UUID with no data
        BleFilter filter = mFilterBuilder.setServiceData(mServiceDataUuid, serviceData).build();
        assertMatches(filter, null, 0, bleRecord);
    }

    @Test
    public void serviceDataNoMatch() {
        byte[] bleRecord = FastPairTestData.sd1;
        byte[] serviceData = {(byte) 0xe1, (byte) 0x00};

        // Verify manufacturer 2-byte UUID with no data
        BleFilter filter = mFilterBuilder.setServiceData(mServiceDataUuid, serviceData).build();
        assertThat(matches(filter, null, 0, bleRecord)).isFalse();
    }

    @Test
    public void serviceDataMask() {
        byte[] bleRecord = FastPairTestData.sd1;
        BleFilter filter;

        // Verify matching partial manufacturer with data and mask
        byte[] serviceData1 = {(byte) 0x15};
        byte[] mask1 = {(byte) 0xff};
        filter = mFilterBuilder.setServiceData(mServiceDataUuid, serviceData1, mask1).build();
        assertMatches(filter, null, 0, bleRecord);
    }

    @Test
    public void serviceDataMaskNoMatch() {
        byte[] bleRecord = FastPairTestData.sd1;
        BleFilter filter;

        // Verify non-matching partial manufacturer with data and mask
        byte[] serviceData2 = {(byte) 0xe0, (byte) 0x00, (byte) 0x10};
        byte[] mask2 = {(byte) 0xff, (byte) 0xff, (byte) 0xff};
        filter = mFilterBuilder.setServiceData(mServiceDataUuid, serviceData2, mask2).build();
        assertThat(matches(filter, null, 0, bleRecord)).isFalse();
    }

    @Test(expected = IllegalArgumentException.class)
    public void serviceDataMaskWithDifferentLength() {
        // Different lengths for data and mask.
        byte[] serviceData = {(byte) 0xe0, (byte) 0x00, (byte) 0x10};
        byte[] mask = {(byte) 0xff, (byte) 0xff};

        //expected.expect(IllegalArgumentException.class);

        mFilterBuilder.setServiceData(mServiceDataUuid, serviceData, mask).build();
    }


    @Test
    public void deviceNameTest() {
        // Verify the name filter matches
        byte[] bleRecord = FastPairTestData.adv_1;
        BleFilter filter = mFilterBuilder.setDeviceName("Pedometer").build();
        assertMatches(filter, null, 0, bleRecord);
    }

    @Test
    public void deviceNameNoMatch() {
        // Verify the name filter does not match
        byte[] bleRecord = FastPairTestData.adv_1;
        BleFilter filter = mFilterBuilder.setDeviceName("Foo").build();
        assertThat(matches(filter, null, 0, bleRecord)).isFalse();
    }

    private static boolean matches(
            BleFilter filter, BluetoothDevice device, int rssi, byte[] bleRecord) {
        return filter.matches(new BleSighting(device,
                bleRecord, rssi, 0 /* timestampNanos */));
    }


    private static void assertMatches(
            BleFilter filter, BluetoothDevice device, int rssi, byte[] bleRecordBytes) {

        // Device match.
        if (filter.getDeviceAddress() != null
                && (device == null || !filter.getDeviceAddress().equals(device.getAddress()))) {
            fail("Filter specified a device address ("
                    + filter.getDeviceAddress()
                    + ") which doesn't match the actual value: ["
                    + (device == null ? "null device" : device.getAddress())
                    + "]");
        }

        // BLE record is null but there exist filters on it.
        BleRecord bleRecord = BleRecord.parseFromBytes(bleRecordBytes);
        if (bleRecord == null
                && (filter.getDeviceName() != null
                || filter.getServiceUuid() != null
                || filter.getManufacturerData() != null
                || filter.getServiceData() != null)) {
            fail(
                    "The bleRecordBytes given parsed to a null bleRecord, but the filter"
                            + "has a non-null field which depends on the scan record");
        }

        // Local name match.
        if (filter.getDeviceName() != null
                && !filter.getDeviceName().equals(bleRecord.getDeviceName())) {
            fail(
                    "The filter's device name ("
                            + filter.getDeviceName()
                            + ") doesn't match the scan record device name ("
                            + bleRecord.getDeviceName()
                            + ")");
        }

        // UUID match.
        if (filter.getServiceUuid() != null
                && !matchesServiceUuids(filter.getServiceUuid(), filter.getServiceUuidMask(),
                bleRecord.getServiceUuids())) {
            fail("The filter specifies a service UUID but it doesn't match "
                    + "what's in the scan record");
        }

        // Service data match
        if (filter.getServiceDataUuid() != null
                && !BleFilter.matchesPartialData(
                filter.getServiceData(),
                filter.getServiceDataMask(),
                bleRecord.getServiceData(filter.getServiceDataUuid()))) {
            fail(
                    "The filter's service data doesn't match what's in the scan record.\n"
                            + "Service data: "
                            + byteString(filter.getServiceData())
                            + "\n"
                            + "Service data UUID: "
                            + filter.getServiceDataUuid().toString()
                            + "\n"
                            + "Service data mask: "
                            + byteString(filter.getServiceDataMask())
                            + "\n"
                            + "Scan record service data: "
                            + byteString(bleRecord.getServiceData(filter.getServiceDataUuid()))
                            + "\n"
                            + "Scan record data map:\n"
                            + byteString(bleRecord.getServiceData()));
        }

        // Manufacturer data match.
        if (filter.getManufacturerId() >= 0
                && !BleFilter.matchesPartialData(
                filter.getManufacturerData(),
                filter.getManufacturerDataMask(),
                bleRecord.getManufacturerSpecificData(filter.getManufacturerId()))) {
            fail(
                    "The filter's manufacturer data doesn't match what's in the scan record.\n"
                            + "Manufacturer ID: "
                            + filter.getManufacturerId()
                            + "\n"
                            + "Manufacturer data: "
                            + byteString(filter.getManufacturerData())
                            + "\n"
                            + "Manufacturer data mask: "
                            + byteString(filter.getManufacturerDataMask())
                            + "\n"
                            + "Scan record manufacturer-specific data: "
                            + byteString(bleRecord.getManufacturerSpecificData(
                            filter.getManufacturerId()))
                            + "\n"
                            + "Manufacturer data array:\n"
                            + byteString(bleRecord.getManufacturerSpecificData()));
        }

        // All filters match.
        assertThat(
                matches(filter, device, rssi, bleRecordBytes)).isTrue();
    }


    private static String byteString(byte[] bytes) {
        if (bytes == null) {
            return "[null]";
        } else {
            final char[] hexArray = "0123456789ABCDEF".toCharArray();
            char[] hexChars = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int v = bytes[i] & 0xFF;
                hexChars[i * 2] = hexArray[v >>> 4];
                hexChars[i * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        }
    }

    // Ref to beacon.decode.AppleBeaconDecoder.getFilterData
    private static byte[] getFilterData(ParcelUuid uuid) {
        byte[] data = new byte[18];
        data[0] = (byte) 0x02;
        data[1] = (byte) 0x15;
        // Check if UUID is needed in data
        if (uuid != null) {
            // Convert UUID to array in big endian order
            byte[] uuidBytes = uuidToByteArray(uuid);
            for (int i = 0; i < 16; i++) {
                // Adding uuid bytes in big-endian order to match iBeacon format
                data[i + 2] = uuidBytes[i];
            }
        }
        return data;
    }

    // Ref to beacon.decode.AppleBeaconDecoder.uuidToByteArray
    private static byte[] uuidToByteArray(ParcelUuid uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getUuid().getMostSignificantBits());
        bb.putLong(uuid.getUuid().getLeastSignificantBits());
        return bb.array();
    }

    private static boolean matchesServiceUuids(
            ParcelUuid uuid, ParcelUuid parcelUuidMask, List<ParcelUuid> uuids) {
        if (uuid == null) {
            return true;
        }

        for (ParcelUuid parcelUuid : uuids) {
            UUID uuidMask = parcelUuidMask == null ? null : parcelUuidMask.getUuid();
            if (matchesServiceUuid(uuid.getUuid(), uuidMask, parcelUuid.getUuid())) {
                return true;
            }
        }
        return false;
    }

    // Check if the uuid pattern matches the particular service uuid.
    private static boolean matchesServiceUuid(UUID uuid, UUID mask, UUID data) {
        if (mask == null) {
            return uuid.equals(data);
        }
        if ((uuid.getLeastSignificantBits() & mask.getLeastSignificantBits())
                != (data.getLeastSignificantBits() & mask.getLeastSignificantBits())) {
            return false;
        }
        return ((uuid.getMostSignificantBits() & mask.getMostSignificantBits())
                == (data.getMostSignificantBits() & mask.getMostSignificantBits()));
    }

    private static String byteString(Map<ParcelUuid, byte[]> bytesMap) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<ParcelUuid, byte[]> entry : bytesMap.entrySet()) {
            builder.append(builder.toString().isEmpty() ? "  " : "\n  ");
            builder.append(entry.getKey().toString());
            builder.append(" --> ");
            builder.append(byteString(entry.getValue()));
        }
        return builder.toString();
    }

    private static String byteString(SparseArray<byte[]> bytesArray) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < bytesArray.size(); i++) {
            builder.append(builder.toString().isEmpty() ? "  " : "\n  ");
            builder.append(byteString(bytesArray.valueAt(i)));
        }
        return builder.toString();
    }
}

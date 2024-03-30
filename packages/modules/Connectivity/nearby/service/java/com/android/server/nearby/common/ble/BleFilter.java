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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Criteria for filtering BLE devices. A {@link BleFilter} allows clients to restrict BLE devices to
 * only those that are of interest to them.
 *
 *
 * <p>Current filtering on the following fields are supported:
 * <li>Service UUIDs which identify the bluetooth gatt services running on the device.
 * <li>Name of remote Bluetooth LE device.
 * <li>Mac address of the remote device.
 * <li>Service data which is the data associated with a service.
 * <li>Manufacturer specific data which is the data associated with a particular manufacturer.
 *
 * @see BleSighting
 */
public final class BleFilter implements Parcelable {

    @Nullable
    private String mDeviceName;

    @Nullable
    private String mDeviceAddress;

    @Nullable
    private ParcelUuid mServiceUuid;

    @Nullable
    private ParcelUuid mServiceUuidMask;

    @Nullable
    private ParcelUuid mServiceDataUuid;

    @Nullable
    private byte[] mServiceData;

    @Nullable
    private byte[] mServiceDataMask;

    private int mManufacturerId;

    @Nullable
    private byte[] mManufacturerData;

    @Nullable
    private byte[] mManufacturerDataMask;

    @Override
    public int describeContents() {
        return 0;
    }

    BleFilter() {
    }

    BleFilter(
            @Nullable String deviceName,
            @Nullable String deviceAddress,
            @Nullable ParcelUuid serviceUuid,
            @Nullable ParcelUuid serviceUuidMask,
            @Nullable ParcelUuid serviceDataUuid,
            @Nullable byte[] serviceData,
            @Nullable byte[] serviceDataMask,
            int manufacturerId,
            @Nullable byte[] manufacturerData,
            @Nullable byte[] manufacturerDataMask) {
        this.mDeviceName = deviceName;
        this.mDeviceAddress = deviceAddress;
        this.mServiceUuid = serviceUuid;
        this.mServiceUuidMask = serviceUuidMask;
        this.mServiceDataUuid = serviceDataUuid;
        this.mServiceData = serviceData;
        this.mServiceDataMask = serviceDataMask;
        this.mManufacturerId = manufacturerId;
        this.mManufacturerData = manufacturerData;
        this.mManufacturerDataMask = manufacturerDataMask;
    }

    public static final Parcelable.Creator<BleFilter> CREATOR = new Creator<BleFilter>() {
        @Override
        public BleFilter createFromParcel(Parcel source) {
            BleFilter nBleFilter = new BleFilter();
            nBleFilter.mDeviceName = source.readString();
            nBleFilter.mDeviceAddress = source.readString();
            nBleFilter.mManufacturerId = source.readInt();
            nBleFilter.mManufacturerData = source.marshall();
            nBleFilter.mManufacturerDataMask = source.marshall();
            nBleFilter.mServiceDataUuid = source.readParcelable(null);
            nBleFilter.mServiceData = source.marshall();
            nBleFilter.mServiceDataMask = source.marshall();
            nBleFilter.mServiceUuid = source.readParcelable(null);
            nBleFilter.mServiceUuidMask = source.readParcelable(null);
            return nBleFilter;
        }

        @Override
        public BleFilter[] newArray(int size) {
            return new BleFilter[size];
        }
    };


    /** Returns the filter set on the device name field of Bluetooth advertisement data. */
    @Nullable
    public String getDeviceName() {
        return mDeviceName;
    }

    /** Returns the filter set on the service uuid. */
    @Nullable
    public ParcelUuid getServiceUuid() {
        return mServiceUuid;
    }

    /** Returns the mask for the service uuid. */
    @Nullable
    public ParcelUuid getServiceUuidMask() {
        return mServiceUuidMask;
    }

    /** Returns the filter set on the device address. */
    @Nullable
    public String getDeviceAddress() {
        return mDeviceAddress;
    }

    /** Returns the filter set on the service data. */
    @Nullable
    public byte[] getServiceData() {
        return mServiceData;
    }

    /** Returns the mask for the service data. */
    @Nullable
    public byte[] getServiceDataMask() {
        return mServiceDataMask;
    }

    /** Returns the filter set on the service data uuid. */
    @Nullable
    public ParcelUuid getServiceDataUuid() {
        return mServiceDataUuid;
    }

    /** Returns the manufacturer id. -1 if the manufacturer filter is not set. */
    public int getManufacturerId() {
        return mManufacturerId;
    }

    /** Returns the filter set on the manufacturer data. */
    @Nullable
    public byte[] getManufacturerData() {
        return mManufacturerData;
    }

    /** Returns the mask for the manufacturer data. */
    @Nullable
    public byte[] getManufacturerDataMask() {
        return mManufacturerDataMask;
    }

    /**
     * Check if the filter matches a {@code BleSighting}. A BLE sighting is considered as a match if
     * it matches all the field filters.
     */
    public boolean matches(@Nullable BleSighting bleSighting) {
        if (bleSighting == null) {
            return false;
        }
        BluetoothDevice device = bleSighting.getDevice();
        // Device match.
        if (mDeviceAddress != null && (device == null || !mDeviceAddress.equals(
                device.getAddress()))) {
            return false;
        }

        BleRecord bleRecord = bleSighting.getBleRecord();

        // Scan record is null but there exist filters on it.
        if (bleRecord == null
                && (mDeviceName != null
                || mServiceUuid != null
                || mManufacturerData != null
                || mServiceData != null)) {
            return false;
        }

        // Local name match.
        if (mDeviceName != null && !mDeviceName.equals(bleRecord.getDeviceName())) {
            return false;
        }

        // UUID match.
        if (mServiceUuid != null
                && !matchesServiceUuids(mServiceUuid, mServiceUuidMask,
                bleRecord.getServiceUuids())) {
            return false;
        }

        // Service data match
        if (mServiceDataUuid != null
                && !matchesPartialData(
                mServiceData, mServiceDataMask, bleRecord.getServiceData(mServiceDataUuid))) {
            return false;
        }

        // Manufacturer data match.
        if (mManufacturerId >= 0
                && !matchesPartialData(
                mManufacturerData,
                mManufacturerDataMask,
                bleRecord.getManufacturerSpecificData(mManufacturerId))) {
            return false;
        }

        // All filters match.
        return true;
    }

    /**
     * Determines if the characteristics of this filter are a superset of the characteristics of the
     * given filter.
     */
    public boolean isSuperset(@Nullable BleFilter bleFilter) {
        if (bleFilter == null) {
            return false;
        }

        if (equals(bleFilter)) {
            return true;
        }

        // Verify device address matches.
        if (mDeviceAddress != null && !mDeviceAddress.equals(bleFilter.getDeviceAddress())) {
            return false;
        }

        // Verify device name matches.
        if (mDeviceName != null && !mDeviceName.equals(bleFilter.getDeviceName())) {
            return false;
        }

        // Verify UUID is a superset.
        if (mServiceUuid != null
                && !serviceUuidIsSuperset(
                mServiceUuid,
                mServiceUuidMask,
                bleFilter.getServiceUuid(),
                bleFilter.getServiceUuidMask())) {
            return false;
        }

        // Verify service data is a superset.
        if (mServiceDataUuid != null
                && (!mServiceDataUuid.equals(bleFilter.getServiceDataUuid())
                || !partialDataIsSuperset(
                mServiceData,
                mServiceDataMask,
                bleFilter.getServiceData(),
                bleFilter.getServiceDataMask()))) {
            return false;
        }

        // Verify manufacturer data is a superset.
        if (mManufacturerId >= 0
                && (mManufacturerId != bleFilter.getManufacturerId()
                || !partialDataIsSuperset(
                mManufacturerData,
                mManufacturerDataMask,
                bleFilter.getManufacturerData(),
                bleFilter.getManufacturerDataMask()))) {
            return false;
        }

        return true;
    }

    /** Determines if the first uuid and mask are a superset of the second uuid and mask. */
    private static boolean serviceUuidIsSuperset(
            @Nullable ParcelUuid uuid1,
            @Nullable ParcelUuid uuidMask1,
            @Nullable ParcelUuid uuid2,
            @Nullable ParcelUuid uuidMask2) {
        // First uuid1 is null so it can match any service UUID.
        if (uuid1 == null) {
            return true;
        }

        // uuid2 is a superset of uuid1, but not the other way around.
        if (uuid2 == null) {
            return false;
        }

        // Without a mask, the uuids must match.
        if (uuidMask1 == null) {
            return uuid1.equals(uuid2);
        }

        // Mask2 should be at least as specific as mask1.
        if (uuidMask2 != null) {
            long uuid1MostSig = uuidMask1.getUuid().getMostSignificantBits();
            long uuid1LeastSig = uuidMask1.getUuid().getLeastSignificantBits();
            long uuid2MostSig = uuidMask2.getUuid().getMostSignificantBits();
            long uuid2LeastSig = uuidMask2.getUuid().getLeastSignificantBits();
            if (((uuid1MostSig & uuid2MostSig) != uuid1MostSig)
                    || ((uuid1LeastSig & uuid2LeastSig) != uuid1LeastSig)) {
                return false;
            }
        }

        if (!matchesServiceUuids(uuid1, uuidMask1, Arrays.asList(uuid2))) {
            return false;
        }

        return true;
    }

    /** Determines if the first data and mask are the superset of the second data and mask. */
    private static boolean partialDataIsSuperset(
            @Nullable byte[] data1,
            @Nullable byte[] dataMask1,
            @Nullable byte[] data2,
            @Nullable byte[] dataMask2) {
        if (Arrays.equals(data1, data2) && Arrays.equals(dataMask1, dataMask2)) {
            return true;
        }

        if (data1 == null) {
            return true;
        }

        if (data2 == null) {
            return false;
        }

        // Mask2 should be at least as specific as mask1.
        if (dataMask1 != null && dataMask2 != null) {
            for (int i = 0, j = 0; i < dataMask1.length && j < dataMask2.length; i++, j++) {
                if ((dataMask1[i] & dataMask2[j]) != dataMask1[i]) {
                    return false;
                }
            }
        }

        if (!matchesPartialData(data1, dataMask1, data2)) {
            return false;
        }

        return true;
    }

    /** Check if the uuid pattern is contained in a list of parcel uuids. */
    private static boolean matchesServiceUuids(
            @Nullable ParcelUuid uuid, @Nullable ParcelUuid parcelUuidMask,
            List<ParcelUuid> uuids) {
        if (uuid == null) {
            // No service uuid filter has been set, so there's a match.
            return true;
        }

        UUID uuidMask = parcelUuidMask == null ? null : parcelUuidMask.getUuid();
        for (ParcelUuid parcelUuid : uuids) {
            if (matchesServiceUuid(uuid.getUuid(), uuidMask, parcelUuid.getUuid())) {
                return true;
            }
        }
        return false;
    }

    /** Check if the uuid pattern matches the particular service uuid. */
    private static boolean matchesServiceUuid(UUID uuid, @Nullable UUID mask, UUID data) {
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

    /**
     * Check whether the data pattern matches the parsed data. Assumes that {@code data} and {@code
     * dataMask} have the same length.
     */
    /* package */
    static boolean matchesPartialData(
            @Nullable byte[] data, @Nullable byte[] dataMask, @Nullable byte[] parsedData) {
        if (data == null || parsedData == null || parsedData.length < data.length) {
            return false;
        }
        if (dataMask == null) {
            for (int i = 0; i < data.length; ++i) {
                if (parsedData[i] != data[i]) {
                    return false;
                }
            }
            return true;
        }
        for (int i = 0; i < data.length; ++i) {
            if ((dataMask[i] & parsedData[i]) != (dataMask[i] & data[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "BleFilter [deviceName="
                + mDeviceName
                + ", deviceAddress="
                + mDeviceAddress
                + ", uuid="
                + mServiceUuid
                + ", uuidMask="
                + mServiceUuidMask
                + ", serviceDataUuid="
                + mServiceDataUuid
                + ", serviceData="
                + Arrays.toString(mServiceData)
                + ", serviceDataMask="
                + Arrays.toString(mServiceDataMask)
                + ", manufacturerId="
                + mManufacturerId
                + ", manufacturerData="
                + Arrays.toString(mManufacturerData)
                + ", manufacturerDataMask="
                + Arrays.toString(mManufacturerDataMask)
                + "]";
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mDeviceName);
        out.writeString(mDeviceAddress);
        out.writeInt(mManufacturerId);
        out.writeByteArray(mManufacturerData);
        out.writeByteArray(mManufacturerDataMask);
        out.writeParcelable(mServiceDataUuid, flags);
        out.writeByteArray(mServiceData);
        out.writeByteArray(mServiceDataMask);
        out.writeParcelable(mServiceUuid, flags);
        out.writeParcelable(mServiceUuidMask, flags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mDeviceName,
                mDeviceAddress,
                mManufacturerId,
                Arrays.hashCode(mManufacturerData),
                Arrays.hashCode(mManufacturerDataMask),
                mServiceDataUuid,
                Arrays.hashCode(mServiceData),
                Arrays.hashCode(mServiceDataMask),
                mServiceUuid,
                mServiceUuidMask);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BleFilter other = (BleFilter) obj;
        return mDeviceName.equals(other.mDeviceName)
                && mDeviceAddress.equals(other.mDeviceAddress)
                && mManufacturerId == other.mManufacturerId
                && Arrays.equals(mManufacturerData, other.mManufacturerData)
                && Arrays.equals(mManufacturerDataMask, other.mManufacturerDataMask)
                && mServiceDataUuid.equals(other.mServiceDataUuid)
                && Arrays.equals(mServiceData, other.mServiceData)
                && Arrays.equals(mServiceDataMask, other.mServiceDataMask)
                && mServiceUuid.equals(other.mServiceUuid)
                && mServiceUuidMask.equals(other.mServiceUuidMask);
    }

    /** Builder class for {@link BleFilter}. */
    public static final class Builder {

        private String mDeviceName;
        private String mDeviceAddress;

        @Nullable
        private ParcelUuid mServiceUuid;
        @Nullable
        private ParcelUuid mUuidMask;

        private ParcelUuid mServiceDataUuid;
        @Nullable
        private byte[] mServiceData;
        @Nullable
        private byte[] mServiceDataMask;

        private int mManufacturerId = -1;
        private byte[] mManufacturerData;
        @Nullable
        private byte[] mManufacturerDataMask;

        /** Set filter on device name. */
        public Builder setDeviceName(String deviceName) {
            this.mDeviceName = deviceName;
            return this;
        }

        /**
         * Set filter on device address.
         *
         * @param deviceAddress The device Bluetooth address for the filter. It needs to be in the
         *                      format of "01:02:03:AB:CD:EF". The device address can be validated
         *                      using {@link
         *                      BluetoothAdapter#checkBluetoothAddress}.
         * @throws IllegalArgumentException If the {@code deviceAddress} is invalid.
         */
        public Builder setDeviceAddress(String deviceAddress) {
            if (!BluetoothAdapter.checkBluetoothAddress(deviceAddress)) {
                throw new IllegalArgumentException("invalid device address " + deviceAddress);
            }
            this.mDeviceAddress = deviceAddress;
            return this;
        }

        /** Set filter on service uuid. */
        public Builder setServiceUuid(@Nullable ParcelUuid serviceUuid) {
            this.mServiceUuid = serviceUuid;
            mUuidMask = null; // clear uuid mask
            return this;
        }

        /**
         * Set filter on partial service uuid. The {@code uuidMask} is the bit mask for the {@code
         * serviceUuid}. Set any bit in the mask to 1 to indicate a match is needed for the bit in
         * {@code serviceUuid}, and 0 to ignore that bit.
         *
         * @throws IllegalArgumentException If {@code serviceUuid} is {@code null} but {@code
         *                                  uuidMask}
         *                                  is not {@code null}.
         */
        public Builder setServiceUuid(@Nullable ParcelUuid serviceUuid,
                @Nullable ParcelUuid uuidMask) {
            if (uuidMask != null && serviceUuid == null) {
                throw new IllegalArgumentException("uuid is null while uuidMask is not null!");
            }
            this.mServiceUuid = serviceUuid;
            this.mUuidMask = uuidMask;
            return this;
        }

        /**
         * Set filtering on service data.
         */
        public Builder setServiceData(ParcelUuid serviceDataUuid, @Nullable byte[] serviceData) {
            this.mServiceDataUuid = serviceDataUuid;
            this.mServiceData = serviceData;
            mServiceDataMask = null; // clear service data mask
            return this;
        }

        /**
         * Set partial filter on service data. For any bit in the mask, set it to 1 if it needs to
         * match
         * the one in service data, otherwise set it to 0 to ignore that bit.
         *
         * <p>The {@code serviceDataMask} must have the same length of the {@code serviceData}.
         *
         * @throws IllegalArgumentException If {@code serviceDataMask} is {@code null} while {@code
         *                                  serviceData} is not or {@code serviceDataMask} and
         *                                  {@code serviceData} has different
         *                                  length.
         */
        public Builder setServiceData(
                ParcelUuid serviceDataUuid,
                @Nullable byte[] serviceData,
                @Nullable byte[] serviceDataMask) {
            if (serviceDataMask != null) {
                if (serviceData == null) {
                    throw new IllegalArgumentException(
                            "serviceData is null while serviceDataMask is not null");
                }
                // Since the serviceDataMask is a bit mask for serviceData, the lengths of the two
                // byte array need to be the same.
                if (serviceData.length != serviceDataMask.length) {
                    throw new IllegalArgumentException(
                            "size mismatch for service data and service data mask");
                }
            }
            this.mServiceDataUuid = serviceDataUuid;
            this.mServiceData = serviceData;
            this.mServiceDataMask = serviceDataMask;
            return this;
        }

        /**
         * Set filter on on manufacturerData. A negative manufacturerId is considered as invalid id.
         *
         * <p>Note the first two bytes of the {@code manufacturerData} is the manufacturerId.
         *
         * @throws IllegalArgumentException If the {@code manufacturerId} is invalid.
         */
        public Builder setManufacturerData(int manufacturerId, @Nullable byte[] manufacturerData) {
            return setManufacturerData(manufacturerId, manufacturerData, null /* mask */);
        }

        /**
         * Set filter on partial manufacture data. For any bit in the mask, set it to 1 if it needs
         * to
         * match the one in manufacturer data, otherwise set it to 0.
         *
         * <p>The {@code manufacturerDataMask} must have the same length of {@code
         * manufacturerData}.
         *
         * @throws IllegalArgumentException If the {@code manufacturerId} is invalid, or {@code
         *                                  manufacturerData} is null while {@code
         *                                  manufacturerDataMask} is not, or {@code
         *                                  manufacturerData} and {@code manufacturerDataMask} have
         *                                  different length.
         */
        public Builder setManufacturerData(
                int manufacturerId,
                @Nullable byte[] manufacturerData,
                @Nullable byte[] manufacturerDataMask) {
            if (manufacturerData != null && manufacturerId < 0) {
                throw new IllegalArgumentException("invalid manufacture id");
            }
            if (manufacturerDataMask != null) {
                if (manufacturerData == null) {
                    throw new IllegalArgumentException(
                            "manufacturerData is null while manufacturerDataMask is not null");
                }
                // Since the manufacturerDataMask is a bit mask for manufacturerData, the lengths
                // of the two byte array need to be the same.
                if (manufacturerData.length != manufacturerDataMask.length) {
                    throw new IllegalArgumentException(
                            "size mismatch for manufacturerData and manufacturerDataMask");
                }
            }
            this.mManufacturerId = manufacturerId;
            this.mManufacturerData = manufacturerData == null ? new byte[0] : manufacturerData;
            this.mManufacturerDataMask = manufacturerDataMask;
            return this;
        }


        /**
         * Builds the filter.
         *
         * @throws IllegalArgumentException If the filter cannot be built.
         */
        public BleFilter build() {
            return new BleFilter(
                    mDeviceName,
                    mDeviceAddress,
                    mServiceUuid,
                    mUuidMask,
                    mServiceDataUuid,
                    mServiceData,
                    mServiceDataMask,
                    mManufacturerId,
                    mManufacturerData,
                    mManufacturerDataMask);
        }
    }

    /**
     * Changes ble filter to os filter
     */
    public ScanFilter toOsFilter() {
        ScanFilter.Builder osFilterBuilder = new ScanFilter.Builder();
        if (!TextUtils.isEmpty(getDeviceAddress())) {
            osFilterBuilder.setDeviceAddress(getDeviceAddress());
        }
        if (!TextUtils.isEmpty(getDeviceName())) {
            osFilterBuilder.setDeviceName(getDeviceName());
        }

        byte[] manufacturerData = getManufacturerData();
        if (getManufacturerId() != -1 && manufacturerData != null) {
            byte[] manufacturerDataMask = getManufacturerDataMask();
            if (manufacturerDataMask != null) {
                osFilterBuilder.setManufacturerData(
                        getManufacturerId(), manufacturerData, manufacturerDataMask);
            } else {
                osFilterBuilder.setManufacturerData(getManufacturerId(), manufacturerData);
            }
        }

        ParcelUuid serviceDataUuid = getServiceDataUuid();
        byte[] serviceData = getServiceData();
        if (serviceDataUuid != null && serviceData != null) {
            byte[] serviceDataMask = getServiceDataMask();
            if (serviceDataMask != null) {
                osFilterBuilder.setServiceData(serviceDataUuid, serviceData, serviceDataMask);
            } else {
                osFilterBuilder.setServiceData(serviceDataUuid, serviceData);
            }
        }

        ParcelUuid serviceUuid = getServiceUuid();
        if (serviceUuid != null) {
            ParcelUuid serviceUuidMask = getServiceUuidMask();
            if (serviceUuidMask != null) {
                osFilterBuilder.setServiceUuid(serviceUuid, serviceUuidMask);
            } else {
                osFilterBuilder.setServiceUuid(serviceUuid);
            }
        }
        return osFilterBuilder.build();
    }
}

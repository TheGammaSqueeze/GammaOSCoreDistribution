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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.Build.VERSION_CODES;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A sighting of a BLE device found in a Bluetooth LE scan.
 */

public class BleSighting implements Parcelable {

    public static final Parcelable.Creator<BleSighting> CREATOR = new Creator<BleSighting>() {
        @Override
        public BleSighting createFromParcel(Parcel source) {
            BleSighting nBleSighting = new BleSighting(source.readParcelable(null),
                    source.marshall(), source.readInt(), source.readLong());
            return null;
        }

        @Override
        public BleSighting[] newArray(int size) {
            return new BleSighting[size];
        }
    };

    // Max and min rssi value which is from {@link android.bluetooth.le.ScanResult#getRssi()}.
    @VisibleForTesting
    public static final int MAX_RSSI_VALUE = 126;
    @VisibleForTesting
    public static final int MIN_RSSI_VALUE = -127;

    /** Remote bluetooth device. */
    private final BluetoothDevice mDevice;

    /**
     * BLE record, including advertising data and response data. BleRecord is not parcelable, so
     * this
     * is created from bleRecordBytes.
     */
    private final BleRecord mBleRecord;

    /** The bytes of a BLE record. */
    private final byte[] mBleRecordBytes;

    /** Received signal strength. */
    private final int mRssi;

    /** Nanos timestamp when the ble device was observed (epoch time). */
    private final long mTimestampEpochNanos;

    /**
     * Constructor of a BLE sighting.
     *
     * @param device              Remote bluetooth device that is found.
     * @param bleRecordBytes      The bytes that will create a BleRecord.
     * @param rssi                Received signal strength.
     * @param timestampEpochNanos Nanos timestamp when the BLE device was observed (epoch time).
     */
    public BleSighting(BluetoothDevice device, byte[] bleRecordBytes, int rssi,
            long timestampEpochNanos) {
        this.mDevice = device;
        this.mBleRecordBytes = bleRecordBytes;
        this.mRssi = rssi;
        this.mTimestampEpochNanos = timestampEpochNanos;
        mBleRecord = BleRecord.parseFromBytes(bleRecordBytes);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Returns the remote bluetooth device identified by the bluetooth device address. */
    public BluetoothDevice getDevice() {
        return mDevice;
    }

    /** Returns the BLE record, which is a combination of advertisement and scan response. */
    public BleRecord getBleRecord() {
        return mBleRecord;
    }

    /** Returns the bytes of the BLE record. */
    public byte[] getBleRecordBytes() {
        return mBleRecordBytes;
    }

    /** Returns the received signal strength in dBm. The valid range is [-127, 127]. */
    public int getRssi() {
        return mRssi;
    }

    /**
     * Returns the received signal strength normalized with the offset specific to the given device.
     * 3 is the rssi offset to calculate fast init distance.
     * <p>This method utilized the rssi offset maintained by Nearby Sharing.
     *
     * @return normalized rssi which is between [-127, 126] according to {@link
     * android.bluetooth.le.ScanResult#getRssi()}.
     */
    public int getNormalizedRSSI() {
        int adjustedRssi = mRssi + 3;
        if (adjustedRssi < MIN_RSSI_VALUE) {
            return MIN_RSSI_VALUE;
        } else if (adjustedRssi > MAX_RSSI_VALUE) {
            return MAX_RSSI_VALUE;
        } else {
            return adjustedRssi;
        }
    }

    /** Returns timestamp in epoch time when the scan record was observed. */
    public long getTimestampNanos() {
        return mTimestampEpochNanos;
    }

    /** Returns timestamp in epoch time when the scan record was observed, in millis. */
    public long getTimestampMillis() {
        return TimeUnit.NANOSECONDS.toMillis(mTimestampEpochNanos);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mDevice, flags);
        dest.writeByteArray(mBleRecordBytes);
        dest.writeInt(mRssi);
        dest.writeLong(mTimestampEpochNanos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDevice, mRssi, mTimestampEpochNanos, Arrays.hashCode(mBleRecordBytes));
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BleSighting)) {
            return false;
        }
        BleSighting other = (BleSighting) obj;
        return Objects.equals(mDevice, other.mDevice)
                && mRssi == other.mRssi
                && Arrays.equals(mBleRecordBytes, other.mBleRecordBytes)
                && mTimestampEpochNanos == other.mTimestampEpochNanos;
    }

    @Override
    public String toString() {
        return "BleSighting{"
                + "device="
                + mDevice
                + ", bleRecord="
                + mBleRecord
                + ", rssi="
                + mRssi
                + ", timestampNanos="
                + mTimestampEpochNanos
                + "}";
    }

    /** Creates {@link BleSighting} using the {@link ScanResult}. */
    @RequiresApi(api = VERSION_CODES.LOLLIPOP)
    @Nullable
    public static BleSighting createFromOsScanResult(ScanResult osResult) {
        ScanRecord osScanRecord = osResult.getScanRecord();
        if (osScanRecord == null) {
            return null;
        }

        return new BleSighting(
                osResult.getDevice(),
                osScanRecord.getBytes(),
                osResult.getRssi(),
                // The timestamp from ScanResult is 'nanos since boot', Beacon lib will change it
                // as 'nanos
                // since epoch', but Nearby never reference this field, just pass it as 'nanos
                // since boot'.
                // ref to beacon/scan/impl/LBluetoothLeScannerCompat.fromOs for beacon design
                // about how to
                // convert nanos since boot to epoch.
                osResult.getTimestampNanos());
    }
}


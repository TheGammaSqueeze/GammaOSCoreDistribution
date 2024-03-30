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

package android.nearby.cts;

import static android.nearby.ScanRequest.SCAN_TYPE_NEARBY_PRESENCE;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.NearbyDevice;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.PublicCredential;
import android.os.Build;
import android.os.Parcel;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class NearbyDeviceParcelableTest {

    private static final String BLUETOOTH_ADDRESS = "00:11:22:33:FF:EE";
    private static final byte[] SCAN_DATA = new byte[] {1, 2, 3, 4};
    private static final String FAST_PAIR_MODEL_ID = "1234";
    private static final int RSSI = -60;

    private NearbyDeviceParcelable.Builder mBuilder;

    @Before
    public void setUp() {
        mBuilder =
                new NearbyDeviceParcelable.Builder()
                        .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                        .setName("testDevice")
                        .setMedium(NearbyDevice.Medium.BLE)
                        .setRssi(RSSI)
                        .setFastPairModelId(FAST_PAIR_MODEL_ID)
                        .setBluetoothAddress(BLUETOOTH_ADDRESS)
                        .setData(SCAN_DATA);
    }

    /** Verify toString returns expected string. */
    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testToString() {
        PublicCredential publicCredential =
                new PublicCredential.Builder(
                                new byte[] {1},
                                new byte[] {2},
                                new byte[] {3},
                                new byte[] {4},
                                new byte[] {5})
                        .build();
        NearbyDeviceParcelable nearbyDeviceParcelable =
                mBuilder.setFastPairModelId(null)
                        .setData(null)
                        .setPublicCredential(publicCredential)
                        .build();

        assertThat(nearbyDeviceParcelable.toString())
                .isEqualTo(
                        "NearbyDeviceParcelable[scanType=2, name=testDevice, medium=BLE, "
                                + "txPower=0, rssi=-60, action=0, bluetoothAddress="
                                + BLUETOOTH_ADDRESS
                                + ", fastPairModelId=null, data=null, salt=null]");
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void test_defaultNullFields() {
        NearbyDeviceParcelable nearbyDeviceParcelable =
                new NearbyDeviceParcelable.Builder()
                        .setMedium(NearbyDevice.Medium.BLE)
                        .setRssi(RSSI)
                        .build();

        assertThat(nearbyDeviceParcelable.getName()).isNull();
        assertThat(nearbyDeviceParcelable.getFastPairModelId()).isNull();
        assertThat(nearbyDeviceParcelable.getBluetoothAddress()).isNull();
        assertThat(nearbyDeviceParcelable.getData()).isNull();

        assertThat(nearbyDeviceParcelable.getMedium()).isEqualTo(NearbyDevice.Medium.BLE);
        assertThat(nearbyDeviceParcelable.getRssi()).isEqualTo(RSSI);
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testWriteParcel() {
        NearbyDeviceParcelable nearbyDeviceParcelable = mBuilder.build();

        Parcel parcel = Parcel.obtain();
        nearbyDeviceParcelable.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NearbyDeviceParcelable actualNearbyDevice =
                NearbyDeviceParcelable.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(actualNearbyDevice.getRssi()).isEqualTo(RSSI);
        assertThat(actualNearbyDevice.getFastPairModelId()).isEqualTo(FAST_PAIR_MODEL_ID);
        assertThat(actualNearbyDevice.getBluetoothAddress()).isEqualTo(BLUETOOTH_ADDRESS);
        assertThat(Arrays.equals(actualNearbyDevice.getData(), SCAN_DATA)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testWriteParcel_nullModelId() {
        NearbyDeviceParcelable nearbyDeviceParcelable = mBuilder.setFastPairModelId(null).build();

        Parcel parcel = Parcel.obtain();
        nearbyDeviceParcelable.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NearbyDeviceParcelable actualNearbyDevice =
                NearbyDeviceParcelable.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(actualNearbyDevice.getFastPairModelId()).isNull();
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testWriteParcel_nullBluetoothAddress() {
        NearbyDeviceParcelable nearbyDeviceParcelable = mBuilder.setBluetoothAddress(null).build();

        Parcel parcel = Parcel.obtain();
        nearbyDeviceParcelable.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NearbyDeviceParcelable actualNearbyDevice =
                NearbyDeviceParcelable.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(actualNearbyDevice.getBluetoothAddress()).isNull();
    }
}

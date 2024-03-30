/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.bluetooth.cts;

import android.bluetooth.le.TransportDiscoveryData;
import android.bluetooth.le.TransportBlock;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test cases for {@link TransportDiscoveryData}.
 * <p>
 * To run the test, use adb shell am instrument -e class 'android.bluetooth.le.TransportDiscoveryDataTest' -w
 * 'com.android.bluetooth.tests/android.bluetooth.BluetoothTestRunner'
 */
public class TransportDiscoveryDataTest extends AndroidTestCase {

    @SmallTest
    public void testInitList() {
        Parcel parcel = Parcel.obtain();
        List<TransportBlock> transportBlocks = new ArrayList();
        transportBlocks.add(new TransportBlock(1, 0, 4, new byte[] {
                (byte) 0xF0, 0x00, 0x02, 0x15 }));
        TransportDiscoveryData data = new TransportDiscoveryData(1, transportBlocks);
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TransportDiscoveryData dataFromParcel =
                TransportDiscoveryData.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
    }


    @SmallTest
    public void testInitByteArray() {
        Parcel parcel = Parcel.obtain();
        TransportDiscoveryData data = new TransportDiscoveryData(new byte[] {
                0x01, 0x01, 0x00, 0x04, (byte) 0xF0, 0x00, 0x02, 0x15 });
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TransportDiscoveryData dataFromParcel =
                TransportDiscoveryData.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
    }

    @SmallTest
    public void testGetValues() {
        Parcel parcel = Parcel.obtain();
        TransportDiscoveryData data = new TransportDiscoveryData(new byte[] {
                0x01, 0x01, 0x00, 0x04, (byte) 0xF0, 0x00, 0x02, 0x15 });
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TransportDiscoveryData dataFromParcel =
                TransportDiscoveryData.CREATOR.createFromParcel(parcel);
        assertEquals(data.getTransportBlocks().size(), 1);
        assertEquals(dataFromParcel.getTransportBlocks().size(), 1);
        assertEquals(data.getTransportBlocks().get(0), dataFromParcel.getTransportBlocks().get(0));
        assertEquals(data.getTransportDataType(), 1);
        assertEquals(dataFromParcel.getTransportDataType(), 1);
        assertEquals(data, dataFromParcel);
    }

    @SmallTest
    public void testTotalBytes() {
        Parcel parcel = Parcel.obtain();
        TransportDiscoveryData data = new TransportDiscoveryData(new byte[] {
                0x01, 0x01, 0x00, 0x04, (byte) 0xF0, 0x00, 0x02, 0x15 });
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TransportDiscoveryData dataFromParcel =
                TransportDiscoveryData.CREATOR.createFromParcel(parcel);
        assertEquals(data.totalBytes(), 8);
        assertEquals(dataFromParcel.totalBytes(), 8);
        assertEquals(data, dataFromParcel);
    }

    @SmallTest
    public void testToByteArray() {
        Parcel parcel = Parcel.obtain();
        TransportDiscoveryData data = new TransportDiscoveryData(new byte[] {
                0x01, 0x01, 0x00, 0x04, (byte) 0xF0, 0x00, 0x02, 0x15 });
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TransportDiscoveryData dataFromParcel =
                TransportDiscoveryData.CREATOR.createFromParcel(parcel);
        TestUtils.assertArrayEquals(data.toByteArray(), dataFromParcel.toByteArray());
        assertEquals(data, dataFromParcel);
    }
}

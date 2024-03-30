/*
 * Copyright 2022 The Android Open Source Project
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

import android.bluetooth.BluetoothGattService;
import android.test.AndroidTestCase;

import java.util.UUID;

public class BluetoothGattServiceTest extends AndroidTestCase {

    private UUID TEST_UUID = UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb");
    private BluetoothGattService mBluetoothGattService;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mBluetoothGattService = new BluetoothGattService(TEST_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mBluetoothGattService = null;
    }

    public void test_getInstanceId() {
       assertEquals(mBluetoothGattService.getInstanceId(), 0);
    }

    public void test_getType() {
        assertEquals(mBluetoothGattService.getType(), BluetoothGattService.SERVICE_TYPE_PRIMARY);
    }
}

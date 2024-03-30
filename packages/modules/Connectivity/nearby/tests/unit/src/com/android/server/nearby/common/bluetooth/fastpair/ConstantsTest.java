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

package com.android.server.nearby.common.bluetooth.fastpair;

import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothUuids.to128BitUuid;
import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothUuids.toFastPair128BitUuid;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.bluetooth.BluetoothGattCharacteristic;

import androidx.test.filters.SdkSuppress;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattConnection;

import junit.framework.TestCase;

import org.mockito.Mock;

import java.util.UUID;

/**
 * Unit tests for {@link Constants}.
 */
public class ConstantsTest extends TestCase {

    @Mock
    private BluetoothGattConnection mMockGattConnection;

    private static final UUID OLD_KEY_BASE_PAIRING_CHARACTERISTICS = to128BitUuid((short) 0x1234);

    private static final UUID NEW_KEY_BASE_PAIRING_CHARACTERISTICS =
            toFastPair128BitUuid((short) 0x1234);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        initMocks(this);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getId_whenSupportNewCharacteristics() throws BluetoothException {
        when(mMockGattConnection.getCharacteristic(any(UUID.class), any(UUID.class)))
                .thenReturn(new BluetoothGattCharacteristic(NEW_KEY_BASE_PAIRING_CHARACTERISTICS, 0,
                        0));

        assertThat(KeyBasedPairingCharacteristic.getId(mMockGattConnection))
                .isEqualTo(NEW_KEY_BASE_PAIRING_CHARACTERISTICS);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getId_whenNotSupportNewCharacteristics() throws BluetoothException {
        // {@link BluetoothGattConnection#getCharacteristic(UUID, UUID)} throws {@link
        // BluetoothException} if the characteristic not found .
        when(mMockGattConnection.getCharacteristic(any(UUID.class), any(UUID.class)))
                .thenThrow(new BluetoothException(""));

        assertThat(KeyBasedPairingCharacteristic.getId(mMockGattConnection))
                .isEqualTo(OLD_KEY_BASE_PAIRING_CHARACTERISTICS);
    }
}

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

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link BluetoothAddress}. */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothAddressTest {

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void maskBluetoothAddress_whenInputIsNull() {
        assertThat(BluetoothAddress.maskBluetoothAddress(null)).isEqualTo("");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void maskBluetoothAddress_whenInputStringNotMatchFormat() {
        assertThat(BluetoothAddress.maskBluetoothAddress("AA:BB:CC")).isEqualTo("AA:BB:CC");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void maskBluetoothAddress_whenInputStringMatchFormat() {
        assertThat(BluetoothAddress.maskBluetoothAddress("AA:BB:CC:DD:EE:FF"))
                .isEqualTo("XX:XX:XX:XX:EE:FF");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void maskBluetoothAddress_whenInputStringContainLowerCaseMatchFormat() {
        assertThat(BluetoothAddress.maskBluetoothAddress("Aa:Bb:cC:dD:eE:Ff"))
                .isEqualTo("XX:XX:XX:XX:EE:FF");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void maskBluetoothAddress_whenInputBluetoothDevice() {
        assertThat(
                BluetoothAddress.maskBluetoothAddress(
                        BluetoothAdapter.getDefaultAdapter().getRemoteDevice("FF:EE:DD:CC:BB:AA")))
                .isEqualTo("XX:XX:XX:XX:BB:AA");
    }
}

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

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/** Unit tests for {@link BluetoothUuids}. */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothUuidsTest {

    // According to {@code android.bluetooth.BluetoothUuid}
    private static final short A2DP_SINK_SHORT_UUID = (short) 0x110B;
    private static final UUID A2DP_SINK_CHARACTERISTICS =
            UUID.fromString("0000110B-0000-1000-8000-00805F9B34FB");

    // According to {go/fastpair-128bit-gatt}, the short uuid locates at the 3rd and 4th bytes based
    // on the Fast Pair custom GATT characteristics 128-bit UUIDs base -
    // "FE2C0000-8366-4814-8EB0-01DE32100BEA".
    private static final short CUSTOM_SHORT_UUID = (short) 0x9487;
    private static final UUID CUSTOM_CHARACTERISTICS =
            UUID.fromString("FE2C9487-8366-4814-8EB0-01DE32100BEA");

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void get16BitUuid() {
        assertThat(BluetoothUuids.get16BitUuid(A2DP_SINK_CHARACTERISTICS))
                .isEqualTo(A2DP_SINK_SHORT_UUID);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void is16BitUuid() {
        assertThat(BluetoothUuids.is16BitUuid(A2DP_SINK_CHARACTERISTICS)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void to128BitUuid() {
        assertThat(BluetoothUuids.to128BitUuid(A2DP_SINK_SHORT_UUID))
                .isEqualTo(A2DP_SINK_CHARACTERISTICS);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void toFastPair128BitUuid() {
        assertThat(BluetoothUuids.toFastPair128BitUuid(CUSTOM_SHORT_UUID))
                .isEqualTo(CUSTOM_CHARACTERISTICS);
    }
}

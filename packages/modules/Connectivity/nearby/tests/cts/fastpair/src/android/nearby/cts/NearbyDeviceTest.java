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

import android.annotation.TargetApi;
import android.nearby.FastPairDevice;
import android.nearby.NearbyDevice;
import android.os.Build;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@TargetApi(Build.VERSION_CODES.TIRAMISU)
public class NearbyDeviceTest {

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_isValidMedium() {
        assertThat(NearbyDevice.isValidMedium(1)).isTrue();
        assertThat(NearbyDevice.isValidMedium(2)).isTrue();

        assertThat(NearbyDevice.isValidMedium(0)).isFalse();
        assertThat(NearbyDevice.isValidMedium(3)).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getMedium_fromChild() {
        FastPairDevice fastPairDevice = new FastPairDevice.Builder()
                .addMedium(NearbyDevice.Medium.BLE)
                .setRssi(-60)
                .build();

        assertThat(fastPairDevice.getMediums()).contains(1);
        assertThat(fastPairDevice.getRssi()).isEqualTo(-60);
    }
}

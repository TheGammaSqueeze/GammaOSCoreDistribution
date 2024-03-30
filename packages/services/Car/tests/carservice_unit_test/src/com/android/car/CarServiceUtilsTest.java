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

package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.automotive.vehicle.SubscribeOptions;

import org.junit.Test;

public class CarServiceUtilsTest {

    private static final int TEST_PROP = 1;
    private static final int TEST_AREA_ID = 2;
    private static final float MIN_SAMPLE_RATE = 1.0f;

    @Test
    public void testSubscribeOptionsToHidl() {
        SubscribeOptions aidlOptions = new SubscribeOptions();
        aidlOptions.propId = TEST_PROP;
        aidlOptions.sampleRate = MIN_SAMPLE_RATE;
        // areaIds would be ignored because HIDL subscribeOptions does not support it.
        aidlOptions.areaIds = new int[]{TEST_AREA_ID};
        android.hardware.automotive.vehicle.V2_0.SubscribeOptions hidlOptions =
                new android.hardware.automotive.vehicle.V2_0.SubscribeOptions();
        hidlOptions.propId = TEST_PROP;
        hidlOptions.sampleRate = MIN_SAMPLE_RATE;
        hidlOptions.flags = android.hardware.automotive.vehicle.V2_0.SubscribeFlags.EVENTS_FROM_CAR;

        android.hardware.automotive.vehicle.V2_0.SubscribeOptions gotHidlOptions =
                CarServiceUtils.subscribeOptionsToHidl(aidlOptions);

        assertThat(gotHidlOptions).isEqualTo(hidlOptions);
    }
}

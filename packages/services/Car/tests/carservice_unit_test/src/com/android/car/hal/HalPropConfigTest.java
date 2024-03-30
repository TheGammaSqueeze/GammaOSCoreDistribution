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

package com.android.car.hal;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.automotive.vehicle.VehicleAreaConfig;
import android.hardware.automotive.vehicle.VehiclePropConfig;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public final class HalPropConfigTest {

    private static final int TEST_PROP = 1;
    private static final int TEST_AREA_ID = 2;
    private static final int TEST_ACCESS = 2;
    private static final int TEST_CHANGE_MODE = 3;
    private static final int[] TEST_CONFIG_ARRAY = new int[]{1, 2, 3};
    private static final ArrayList<Integer> TEST_CONFIG_ARRAY_LIST = new ArrayList<Integer>(
            Arrays.asList(1, 2, 3));
    private static final String TEST_CONFIG_STRING = "test_config";
    private static final float MIN_SAMPLE_RATE = 1.0f;
    private static final float MAX_SAMPLE_RATE = 10.0f;
    private static final int MIN_INT32_VALUE = 11;
    private static final int MAX_INT32_VALUE = 20;
    private static final long MIN_INT64_VALUE = 21;
    private static final long MAX_INT64_VALUE = 30;
    private static final float MIN_FLOAT_VALUE = 31.0f;
    private static final float MAX_FLOAT_VALUE = 40.0f;

    private static android.hardware.automotive.vehicle.V2_0.VehiclePropConfig
            getTestHidlPropConfig() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropConfig hidlConfig =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropConfig();
        hidlConfig.prop = TEST_PROP;
        hidlConfig.access = TEST_ACCESS;
        hidlConfig.changeMode = TEST_CHANGE_MODE;
        hidlConfig.configArray = TEST_CONFIG_ARRAY_LIST;
        hidlConfig.configString = TEST_CONFIG_STRING;
        hidlConfig.minSampleRate = MIN_SAMPLE_RATE;
        hidlConfig.maxSampleRate = MAX_SAMPLE_RATE;
        return hidlConfig;
    }

    private static VehiclePropConfig getTestAidlPropConfig() {
        VehiclePropConfig aidlConfig = new VehiclePropConfig();
        aidlConfig.prop = TEST_PROP;
        aidlConfig.access = TEST_ACCESS;
        aidlConfig.changeMode = TEST_CHANGE_MODE;
        aidlConfig.configArray = TEST_CONFIG_ARRAY;
        aidlConfig.configString = TEST_CONFIG_STRING;
        aidlConfig.minSampleRate = MIN_SAMPLE_RATE;
        aidlConfig.maxSampleRate = MAX_SAMPLE_RATE;
        aidlConfig.areaConfigs = new VehicleAreaConfig[0];
        return aidlConfig;
    }

    private static android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig
            getTestHidlAreaConfig() {
        android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig hidlAreaConfig =
                new android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig();
        hidlAreaConfig.areaId = TEST_AREA_ID;
        hidlAreaConfig.minInt32Value = MIN_INT32_VALUE;
        hidlAreaConfig.maxInt32Value = MAX_INT32_VALUE;
        hidlAreaConfig.minInt64Value = MIN_INT64_VALUE;
        hidlAreaConfig.maxInt64Value = MAX_INT64_VALUE;
        hidlAreaConfig.minFloatValue = MIN_FLOAT_VALUE;
        hidlAreaConfig.maxFloatValue = MAX_FLOAT_VALUE;
        return hidlAreaConfig;
    }

    private static VehicleAreaConfig getTestAidlAreaConfig() {
        VehicleAreaConfig aidlAreaConfig = new VehicleAreaConfig();
        aidlAreaConfig.areaId = TEST_AREA_ID;
        aidlAreaConfig.minInt32Value = MIN_INT32_VALUE;
        aidlAreaConfig.maxInt32Value = MAX_INT32_VALUE;
        aidlAreaConfig.minInt64Value = MIN_INT64_VALUE;
        aidlAreaConfig.maxInt64Value = MAX_INT64_VALUE;
        aidlAreaConfig.minFloatValue = MIN_FLOAT_VALUE;
        aidlAreaConfig.maxFloatValue = MAX_FLOAT_VALUE;
        return aidlAreaConfig;
    }

    @Test
    public void testAidlHalPropConfigWithNoArea() {
        VehiclePropConfig aidlConfig = getTestAidlPropConfig();
        AidlHalPropConfig halPropConfig = new AidlHalPropConfig(aidlConfig);

        assertThat(halPropConfig.getPropId()).isEqualTo(TEST_PROP);
        assertThat(halPropConfig.getAccess()).isEqualTo(TEST_ACCESS);
        assertThat(halPropConfig.getChangeMode()).isEqualTo(TEST_CHANGE_MODE);
        assertThat(halPropConfig.getAreaConfigs().length).isEqualTo(0);
        assertThat(halPropConfig.getConfigArray()).isEqualTo(TEST_CONFIG_ARRAY);
        assertThat(halPropConfig.getConfigString()).isEqualTo(TEST_CONFIG_STRING);
        assertThat(halPropConfig.getMinSampleRate()).isEqualTo(MIN_SAMPLE_RATE);
        assertThat(halPropConfig.getMaxSampleRate()).isEqualTo(MAX_SAMPLE_RATE);
    }

    @Test
    public void testAidlHalPropConfigWithArea() {
        VehiclePropConfig aidlConfig = getTestAidlPropConfig();
        aidlConfig.areaConfigs = new VehicleAreaConfig[]{getTestAidlAreaConfig()};
        AidlHalPropConfig halPropConfig = new AidlHalPropConfig(aidlConfig);

        assertThat(halPropConfig.getAreaConfigs().length).isEqualTo(1);

        HalAreaConfig halAreaConfig = halPropConfig.getAreaConfigs()[0];
        assertThat(halAreaConfig.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(halAreaConfig.getMinInt32Value()).isEqualTo(MIN_INT32_VALUE);
        assertThat(halAreaConfig.getMaxInt32Value()).isEqualTo(MAX_INT32_VALUE);
        assertThat(halAreaConfig.getMinInt64Value()).isEqualTo(MIN_INT64_VALUE);
        assertThat(halAreaConfig.getMaxInt64Value()).isEqualTo(MAX_INT64_VALUE);
        assertThat(halAreaConfig.getMinFloatValue()).isEqualTo(MIN_FLOAT_VALUE);
        assertThat(halAreaConfig.getMaxFloatValue()).isEqualTo(MAX_FLOAT_VALUE);
    }

    @Test
    public void testHidlHalPropConfigWithNoArea() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropConfig hidlConfig =
                getTestHidlPropConfig();
        HidlHalPropConfig halPropConfig = new HidlHalPropConfig(hidlConfig);

        assertThat(halPropConfig.getPropId()).isEqualTo(TEST_PROP);
        assertThat(halPropConfig.getAccess()).isEqualTo(TEST_ACCESS);
        assertThat(halPropConfig.getChangeMode()).isEqualTo(TEST_CHANGE_MODE);
        assertThat(halPropConfig.getAreaConfigs().length).isEqualTo(0);
        assertThat(halPropConfig.getConfigArray()).isEqualTo(TEST_CONFIG_ARRAY);
        assertThat(halPropConfig.getConfigString()).isEqualTo(TEST_CONFIG_STRING);
        assertThat(halPropConfig.getMinSampleRate()).isEqualTo(MIN_SAMPLE_RATE);
        assertThat(halPropConfig.getMaxSampleRate()).isEqualTo(MAX_SAMPLE_RATE);
    }

    @Test
    public void testHidlHalPropConfigWithArea() {
        android.hardware.automotive.vehicle.V2_0.VehiclePropConfig hidlConfig =
                getTestHidlPropConfig();
        hidlConfig.areaConfigs =
                new ArrayList<android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig>(
                        Arrays.asList(getTestHidlAreaConfig()));
        HidlHalPropConfig halPropConfig = new HidlHalPropConfig(hidlConfig);

        assertThat(halPropConfig.getAreaConfigs().length).isEqualTo(1);

        HalAreaConfig halAreaConfig = halPropConfig.getAreaConfigs()[0];
        assertThat(halAreaConfig.getAreaId()).isEqualTo(TEST_AREA_ID);
        assertThat(halAreaConfig.getMinInt32Value()).isEqualTo(MIN_INT32_VALUE);
        assertThat(halAreaConfig.getMaxInt32Value()).isEqualTo(MAX_INT32_VALUE);
        assertThat(halAreaConfig.getMinInt64Value()).isEqualTo(MIN_INT64_VALUE);
        assertThat(halAreaConfig.getMaxInt64Value()).isEqualTo(MAX_INT64_VALUE);
        assertThat(halAreaConfig.getMinFloatValue()).isEqualTo(MIN_FLOAT_VALUE);
        assertThat(halAreaConfig.getMaxFloatValue()).isEqualTo(MAX_FLOAT_VALUE);
    }
}

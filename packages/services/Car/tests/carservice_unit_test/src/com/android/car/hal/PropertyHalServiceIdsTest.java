/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.car.CarServiceUtils.toIntArray;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.Car;
import android.car.VehicleHvacFanDirection;
import android.car.VehiclePropertyIds;
import android.hardware.automotive.vehicle.VehicleGear;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehicleUnit;
import android.hardware.automotive.vehicle.VehicleVendorPermission;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.car.internal.PropertyPermissionMapping;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PropertyHalServiceIdsTest {
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    private PropertyHalServiceIds mPropertyHalServiceIds;
    private PropertyPermissionMapping mPermissionMapping;
    private static final String TAG = PropertyHalServiceIdsTest.class.getSimpleName();
    private static final int VENDOR_PROPERTY_1 = 0x21e01111;
    private static final int VENDOR_PROPERTY_2 = 0x21e01112;
    private static final int VENDOR_PROPERTY_3 = 0x21e01113;
    private static final int VENDOR_PROPERTY_4 = 0x21e01114;
    private static final int[] VENDOR_PROPERTY_IDS = {
            VENDOR_PROPERTY_1, VENDOR_PROPERTY_2, VENDOR_PROPERTY_3, VENDOR_PROPERTY_4};
    private static final int[] SYSTEM_PROPERTY_IDS = {VehiclePropertyIds.ENGINE_OIL_LEVEL,
            VehiclePropertyIds.CURRENT_GEAR, VehiclePropertyIds.NIGHT_MODE,
            VehiclePropertyIds.HVAC_FAN_SPEED, VehiclePropertyIds.DOOR_LOCK};
    private static final List<Integer> CONFIG_ARRAY = new ArrayList<>();
    private static final List<Integer> CONFIG_ARRAY_INVALID = new ArrayList<>();
    private static final HalPropValueBuilder PROP_VALUE_BUILDER =
            new HalPropValueBuilder(/*isAidl=*/true);
    //payload test
    private static final HalPropValue GEAR_WITH_VALID_VALUE =
            PROP_VALUE_BUILDER.build(VehicleProperty.GEAR_SELECTION, /*areaId=*/0,
                    /*timestamp=*/SystemClock.elapsedRealtimeNanos(), /*status=*/0,
                    VehicleGear.GEAR_DRIVE);
    private static final HalPropValue GEAR_WITH_EXTRA_VALUE =
            PROP_VALUE_BUILDER.build(VehicleProperty.GEAR_SELECTION, /*areaId=*/0,
                    /*timestamp=*/SystemClock.elapsedRealtimeNanos(), /*status=*/0,
                    new int[]{VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_1});
    private static final HalPropValue GEAR_WITH_INVALID_VALUE =
            PROP_VALUE_BUILDER.build(VehicleProperty.GEAR_SELECTION, /*areaId=*/0,
                    /*timestamp=*/SystemClock.elapsedRealtimeNanos(), /*status=*/0,
                    VehicleUnit.KILOPASCAL);
    private static final HalPropValue GEAR_WITH_INVALID_TYPE_VALUE =
            PROP_VALUE_BUILDER.build(VehicleProperty.GEAR_SELECTION, /*areaId=*/0,
                    /*timestamp=*/SystemClock.elapsedRealtimeNanos(), /*status=*/0,
                    1.0f);
    private static final HalPropValue HVAC_FAN_DIRECTIONS_VALID =
            PROP_VALUE_BUILDER.build(VehicleProperty.HVAC_FAN_DIRECTION, /*areaId=*/0,
                    /*timestamp=*/SystemClock.elapsedRealtimeNanos(), /*status=*/0,
                    VehicleHvacFanDirection.FACE | VehicleHvacFanDirection.FLOOR);
    private static final HalPropValue HVAC_FAN_DIRECTIONS_INVALID =
            PROP_VALUE_BUILDER.build(VehicleProperty.HVAC_FAN_DIRECTION, /*areaId=*/0,
                    /*timestamp=*/SystemClock.elapsedRealtimeNanos(), /*status=*/0,
                    VehicleHvacFanDirection.FACE | 0x100);

    @Before
    public void setUp() {
        mPropertyHalServiceIds = new PropertyHalServiceIds();
        mPermissionMapping = new PropertyPermissionMapping();
        // set up read permission and write permission to VENDOR_PROPERTY_1
        CONFIG_ARRAY.add(VENDOR_PROPERTY_1);
        CONFIG_ARRAY.add(VehicleVendorPermission.PERMISSION_DEFAULT);
        CONFIG_ARRAY.add(VehicleVendorPermission.PERMISSION_NOT_ACCESSIBLE);
        // set up read permission and write permission to VENDOR_PROPERTY_2
        CONFIG_ARRAY.add(VENDOR_PROPERTY_2);
        CONFIG_ARRAY.add(VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_ENGINE);
        CONFIG_ARRAY.add(VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_ENGINE);
        // set up read permission and write permission to VENDOR_PROPERTY_3
        CONFIG_ARRAY.add(VENDOR_PROPERTY_3);
        CONFIG_ARRAY.add(VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_INFO);
        CONFIG_ARRAY.add(VehicleVendorPermission.PERMISSION_DEFAULT);

        // set a invalid config
        CONFIG_ARRAY_INVALID.add(VehiclePropertyIds.CURRENT_GEAR);
        CONFIG_ARRAY_INVALID.add(VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_ENGINE);
        CONFIG_ARRAY_INVALID.add(VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_ENGINE);
    }

    /**
     * Test {@link PropertyHalServiceIds#getReadPermission(int)}
     * and {@link PropertyHalServiceIds#getWritePermission(int)} for system properties
     */
    @Test
    public void checkPermissionForSystemProperty() {
        assertThat(mPropertyHalServiceIds.getReadPermission(VehiclePropertyIds.ENGINE_OIL_LEVEL))
                .isEqualTo(Car.PERMISSION_CAR_ENGINE_DETAILED);
        assertThat(mPropertyHalServiceIds.getWritePermission(VehiclePropertyIds.ENGINE_OIL_LEVEL))
                .isNull();
        assertThat(mPropertyHalServiceIds.getReadPermission(VehiclePropertyIds.HVAC_FAN_SPEED))
                .isEqualTo(Car.PERMISSION_CONTROL_CAR_CLIMATE);
        assertThat(mPropertyHalServiceIds.getWritePermission(VehiclePropertyIds.HVAC_FAN_SPEED))
                .isEqualTo(Car.PERMISSION_CONTROL_CAR_CLIMATE);
        assertThat(mPermissionMapping.getReadPermission(VehiclePropertyIds.HVAC_FAN_SPEED))
                .isEqualTo(Car.PERMISSION_CONTROL_CAR_CLIMATE);
        assertThat(mPermissionMapping.getWritePermission(VehiclePropertyIds.HVAC_FAN_SPEED))
                .isEqualTo(Car.PERMISSION_CONTROL_CAR_CLIMATE);
    }
    /**
     * Test {@link PropertyHalServiceIds#customizeVendorPermission(List)}
     */
    @Test
    public void checkPermissionForVendorProperty() {
        // test insert a valid config
        mPropertyHalServiceIds.customizeVendorPermission(toIntArray(CONFIG_ARRAY));
        assertThat(mPropertyHalServiceIds.getReadPermission(VENDOR_PROPERTY_1))
                .isEqualTo(Car.PERMISSION_VENDOR_EXTENSION);
        assertThat(mPropertyHalServiceIds.getWritePermission(VENDOR_PROPERTY_1)).isNull();

        assertThat(mPropertyHalServiceIds.getReadPermission(VENDOR_PROPERTY_2))
                .isEqualTo(android.car.hardware.property
                        .VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_ENGINE);
        assertThat(mPropertyHalServiceIds.getWritePermission(VENDOR_PROPERTY_2))
                .isEqualTo(android.car.hardware.property
                        .VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_ENGINE);

        assertThat(mPropertyHalServiceIds.getReadPermission(VENDOR_PROPERTY_3))
                .isEqualTo(android.car.hardware.property
                        .VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO);
        assertThat(mPropertyHalServiceIds.getWritePermission(VENDOR_PROPERTY_3))
                .isEqualTo(Car.PERMISSION_VENDOR_EXTENSION);

        assertThat(mPropertyHalServiceIds.getReadPermission(VENDOR_PROPERTY_4))
                .isEqualTo(Car.PERMISSION_VENDOR_EXTENSION);
        assertThat(mPropertyHalServiceIds.getWritePermission(VENDOR_PROPERTY_4))
                .isEqualTo(Car.PERMISSION_VENDOR_EXTENSION);
        assertThat(mPermissionMapping.getReadPermission(VENDOR_PROPERTY_4))
                .isEqualTo(Car.PERMISSION_VENDOR_EXTENSION);
        assertThat(mPermissionMapping.getWritePermission(VENDOR_PROPERTY_4))
                .isEqualTo(Car.PERMISSION_VENDOR_EXTENSION);

        // test insert invalid config
        try {
            mPropertyHalServiceIds.customizeVendorPermission(toIntArray(CONFIG_ARRAY_INVALID));
            Assert.fail("Insert system properties with vendor permissions");
        } catch (IllegalArgumentException e) {
            Log.v(TAG, e.getMessage());
        }
    }

    /**
     * Test {@link PropertyHalServiceIds#isSupportedProperty(int)}
     */
    @Test
    public void checkVendorPropertyId() {
        for (int vendorProp : VENDOR_PROPERTY_IDS) {
            assertWithMessage("Property does not exist.").that(
                    mPropertyHalServiceIds.isSupportedProperty(vendorProp)).isTrue();
        }
        for (int systemProp : SYSTEM_PROPERTY_IDS) {
            assertWithMessage("Property does not exist.").that(
                    mPropertyHalServiceIds.isSupportedProperty(systemProp)).isTrue();
        }
    }

    /**
     * Test {@link PropertyHalServiceIds#checkPayload(HalPropValue)}
     */
    @Test
    public void testPayload() {
        assertThat(mPropertyHalServiceIds.checkPayload(GEAR_WITH_VALID_VALUE)).isTrue();
        assertThat(mPropertyHalServiceIds.checkPayload(GEAR_WITH_EXTRA_VALUE)).isFalse();
        assertThat(mPropertyHalServiceIds.checkPayload(GEAR_WITH_INVALID_VALUE)).isFalse();
        assertThat(mPropertyHalServiceIds.checkPayload(GEAR_WITH_INVALID_TYPE_VALUE)).isFalse();
        assertThat(mPropertyHalServiceIds.checkPayload(HVAC_FAN_DIRECTIONS_VALID)).isTrue();
        assertThat(mPropertyHalServiceIds.checkPayload(HVAC_FAN_DIRECTIONS_INVALID)).isFalse();
    }
}

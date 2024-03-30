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

package com.android.server.nearby.common.bluetooth.util;

import static com.android.server.nearby.common.bluetooth.util.BluetoothGattUtils.getMessageForStatusCode;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothGatt;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;

/** Unit tests for {@link BluetoothGattUtils}. */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothGattUtilsTest {
    private static final UUID TEST_UUID = UUID.randomUUID();
    private static final ImmutableSet<String> GATT_HIDDEN_CONSTANTS = ImmutableSet.of(
            "GATT_WRITE_REQUEST_BUSY", "GATT_WRITE_REQUEST_FAIL", "GATT_WRITE_REQUEST_SUCCESS");

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGetMessageForStatusCode() throws Exception {
        Field[] publicFields = BluetoothGatt.class.getFields();
        for (Field field : publicFields) {
            if ((field.getModifiers() & Modifier.STATIC) == 0
                    || field.getDeclaringClass() != BluetoothGatt.class) {
                continue;
            }
            String fieldName = field.getName();
            if (!fieldName.startsWith("GATT_") || GATT_HIDDEN_CONSTANTS.contains(fieldName)) {
                continue;
            }
            int fieldValue = (Integer) field.get(null);
            assertThat(getMessageForStatusCode(fieldValue)).isEqualTo(fieldName);
        }
    }
}

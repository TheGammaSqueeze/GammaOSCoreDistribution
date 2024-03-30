/*
 * Copyright 2021 The Android Open Source Project
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

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import javax.annotation.Nullable;

/**
 * Utils for Gatt profile.
 */
public class BluetoothGattUtils {

    /**
     * Returns a string message for a BluetoothGatt status codes.
     */
    public static String getMessageForStatusCode(int statusCode) {
        switch (statusCode) {
            case BluetoothGatt.GATT_SUCCESS:
                return "GATT_SUCCESS";
            case BluetoothGatt.GATT_FAILURE:
                return "GATT_FAILURE";
            case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
                return "GATT_INSUFFICIENT_AUTHENTICATION";
            case BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION:
                return "GATT_INSUFFICIENT_AUTHORIZATION";
            case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION:
                return "GATT_INSUFFICIENT_ENCRYPTION";
            case BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH:
                return "GATT_INVALID_ATTRIBUTE_LENGTH";
            case BluetoothGatt.GATT_INVALID_OFFSET:
                return "GATT_INVALID_OFFSET";
            case BluetoothGatt.GATT_READ_NOT_PERMITTED:
                return "GATT_READ_NOT_PERMITTED";
            case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED:
                return "GATT_REQUEST_NOT_SUPPORTED";
            case BluetoothGatt.GATT_WRITE_NOT_PERMITTED:
                return "GATT_WRITE_NOT_PERMITTED";
            case BluetoothGatt.GATT_CONNECTION_CONGESTED:
                return "GATT_CONNECTION_CONGESTED";
            default:
                return "Unknown error code";
        }
    }

    /** Creates a user-readable string from a {@link BluetoothGattDescriptor}. */
    public static String toString(@Nullable BluetoothGattDescriptor descriptor) {
        if (descriptor == null) {
            return "null descriptor";
        }
        return String.format("descriptor %s on %s",
                descriptor.getUuid(),
                toString(descriptor.getCharacteristic()));
    }

    /** Creates a user-readable string from a {@link BluetoothGattCharacteristic}. */
    public static String toString(@Nullable BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return "null characteristic";
        }
        return String.format("characteristic %s on %s",
                characteristic.getUuid(),
                toString(characteristic.getService()));
    }

    /** Creates a user-readable string from a {@link BluetoothGattService}. */
    public static String toString(@Nullable BluetoothGattService service) {
        if (service == null) {
            return "null service";
        }
        return String.format("service %s", service.getUuid());
    }
}

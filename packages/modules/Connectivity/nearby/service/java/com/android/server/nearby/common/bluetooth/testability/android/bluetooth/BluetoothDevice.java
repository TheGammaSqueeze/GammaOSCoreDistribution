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

package com.android.server.nearby.common.bluetooth.testability.android.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.ParcelUuid;

import java.io.IOException;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Mockable wrapper of {@link android.bluetooth.BluetoothDevice}.
 */
@TargetApi(18)
public class BluetoothDevice {
    /** See {@link android.bluetooth.BluetoothDevice#BOND_BONDED}. */
    public static final int BOND_BONDED = android.bluetooth.BluetoothDevice.BOND_BONDED;

    /** See {@link android.bluetooth.BluetoothDevice#BOND_BONDING}. */
    public static final int BOND_BONDING = android.bluetooth.BluetoothDevice.BOND_BONDING;

    /** See {@link android.bluetooth.BluetoothDevice#BOND_NONE}. */
    public static final int BOND_NONE = android.bluetooth.BluetoothDevice.BOND_NONE;

    /** See {@link android.bluetooth.BluetoothDevice#ACTION_ACL_CONNECTED}. */
    public static final String ACTION_ACL_CONNECTED =
            android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED;

    /** See {@link android.bluetooth.BluetoothDevice#ACTION_ACL_DISCONNECT_REQUESTED}. */
    public static final String ACTION_ACL_DISCONNECT_REQUESTED =
            android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED;

    /** See {@link android.bluetooth.BluetoothDevice#ACTION_ACL_DISCONNECTED}. */
    public static final String ACTION_ACL_DISCONNECTED =
            android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED;

    /** See {@link android.bluetooth.BluetoothDevice#ACTION_BOND_STATE_CHANGED}. */
    public static final String ACTION_BOND_STATE_CHANGED =
            android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED;

    /** See {@link android.bluetooth.BluetoothDevice#ACTION_CLASS_CHANGED}. */
    public static final String ACTION_CLASS_CHANGED =
            android.bluetooth.BluetoothDevice.ACTION_CLASS_CHANGED;

    /** See {@link android.bluetooth.BluetoothDevice#ACTION_FOUND}. */
    public static final String ACTION_FOUND = android.bluetooth.BluetoothDevice.ACTION_FOUND;

    /** See {@link android.bluetooth.BluetoothDevice#ACTION_NAME_CHANGED}. */
    public static final String ACTION_NAME_CHANGED =
            android.bluetooth.BluetoothDevice.ACTION_NAME_CHANGED;

    /** See {@link android.bluetooth.BluetoothDevice#ACTION_PAIRING_REQUEST}. */
    // API 19 only
    public static final String ACTION_PAIRING_REQUEST =
            "android.bluetooth.device.action.PAIRING_REQUEST";

    /** See {@link android.bluetooth.BluetoothDevice#ACTION_UUID}. */
    public static final String ACTION_UUID = android.bluetooth.BluetoothDevice.ACTION_UUID;

    /** See {@link android.bluetooth.BluetoothDevice#DEVICE_TYPE_CLASSIC}. */
    public static final int DEVICE_TYPE_CLASSIC =
            android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC;

    /** See {@link android.bluetooth.BluetoothDevice#DEVICE_TYPE_DUAL}. */
    public static final int DEVICE_TYPE_DUAL = android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL;

    /** See {@link android.bluetooth.BluetoothDevice#DEVICE_TYPE_LE}. */
    public static final int DEVICE_TYPE_LE = android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE;

    /** See {@link android.bluetooth.BluetoothDevice#DEVICE_TYPE_UNKNOWN}. */
    public static final int DEVICE_TYPE_UNKNOWN =
            android.bluetooth.BluetoothDevice.DEVICE_TYPE_UNKNOWN;

    /** See {@link android.bluetooth.BluetoothDevice#ERROR}. */
    public static final int ERROR = android.bluetooth.BluetoothDevice.ERROR;

    /** See {@link android.bluetooth.BluetoothDevice#EXTRA_BOND_STATE}. */
    public static final String EXTRA_BOND_STATE =
            android.bluetooth.BluetoothDevice.EXTRA_BOND_STATE;

    /** See {@link android.bluetooth.BluetoothDevice#EXTRA_CLASS}. */
    public static final String EXTRA_CLASS = android.bluetooth.BluetoothDevice.EXTRA_CLASS;

    /** See {@link android.bluetooth.BluetoothDevice#EXTRA_DEVICE}. */
    public static final String EXTRA_DEVICE = android.bluetooth.BluetoothDevice.EXTRA_DEVICE;

    /** See {@link android.bluetooth.BluetoothDevice#EXTRA_NAME}. */
    public static final String EXTRA_NAME = android.bluetooth.BluetoothDevice.EXTRA_NAME;

    /** See {@link android.bluetooth.BluetoothDevice#EXTRA_PAIRING_KEY}. */
    // API 19 only
    public static final String EXTRA_PAIRING_KEY = "android.bluetooth.device.extra.PAIRING_KEY";

    /** See {@link android.bluetooth.BluetoothDevice#EXTRA_PAIRING_VARIANT}. */
    // API 19 only
    public static final String EXTRA_PAIRING_VARIANT =
            "android.bluetooth.device.extra.PAIRING_VARIANT";

    /** See {@link android.bluetooth.BluetoothDevice#EXTRA_PREVIOUS_BOND_STATE}. */
    public static final String EXTRA_PREVIOUS_BOND_STATE =
            android.bluetooth.BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE;

    /** See {@link android.bluetooth.BluetoothDevice#EXTRA_RSSI}. */
    public static final String EXTRA_RSSI = android.bluetooth.BluetoothDevice.EXTRA_RSSI;

    /** See {@link android.bluetooth.BluetoothDevice#EXTRA_UUID}. */
    public static final String EXTRA_UUID = android.bluetooth.BluetoothDevice.EXTRA_UUID;

    /** See {@link android.bluetooth.BluetoothDevice#PAIRING_VARIANT_PASSKEY_CONFIRMATION}. */
    // API 19 only
    public static final int PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2;

    /** See {@link android.bluetooth.BluetoothDevice#PAIRING_VARIANT_PIN}. */
    // API 19 only
    public static final int PAIRING_VARIANT_PIN = 0;

    private final android.bluetooth.BluetoothDevice mWrappedBluetoothDevice;

    private BluetoothDevice(android.bluetooth.BluetoothDevice bluetoothDevice) {
        mWrappedBluetoothDevice = bluetoothDevice;
    }

    /**
     * See {@link android.bluetooth.BluetoothDevice#connectGatt(Context, boolean,
     * android.bluetooth.BluetoothGattCallback)}.
     */
    @Nullable(/* when bt service is not available */)
    public BluetoothGattWrapper connectGatt(Context context, boolean autoConnect,
            BluetoothGattCallback callback) {
        android.bluetooth.BluetoothGatt gatt =
                mWrappedBluetoothDevice.connectGatt(context, autoConnect, callback.unwrap());
        if (gatt == null) {
            return null;
        }
        return BluetoothGattWrapper.wrap(gatt);
    }

    /**
     * See {@link android.bluetooth.BluetoothDevice#connectGatt(Context, boolean,
     * android.bluetooth.BluetoothGattCallback, int)}.
     */
    @TargetApi(23)
    @Nullable(/* when bt service is not available */)
    public BluetoothGattWrapper connectGatt(Context context, boolean autoConnect,
            BluetoothGattCallback callback, int transport) {
        android.bluetooth.BluetoothGatt gatt =
                mWrappedBluetoothDevice.connectGatt(
                        context, autoConnect, callback.unwrap(), transport);
        if (gatt == null) {
            return null;
        }
        return BluetoothGattWrapper.wrap(gatt);
    }


    /**
     * See {@link android.bluetooth.BluetoothDevice#createRfcommSocketToServiceRecord(UUID)}.
     */
    public BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        return mWrappedBluetoothDevice.createRfcommSocketToServiceRecord(uuid);
    }

    /**
     * See
     * {@link android.bluetooth.BluetoothDevice#createInsecureRfcommSocketToServiceRecord(UUID)}.
     */
    public BluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        return mWrappedBluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
    }

    /** See {@link android.bluetooth.BluetoothDevice#setPin(byte[])}. */
    @TargetApi(19)
    public boolean setPairingConfirmation(byte[] pin) {
        return mWrappedBluetoothDevice.setPin(pin);
    }

    /** See {@link android.bluetooth.BluetoothDevice#setPairingConfirmation(boolean)}. */
    public boolean setPairingConfirmation(boolean confirm) {
        return mWrappedBluetoothDevice.setPairingConfirmation(confirm);
    }

    /** See {@link android.bluetooth.BluetoothDevice#fetchUuidsWithSdp()}. */
    public boolean fetchUuidsWithSdp() {
        return mWrappedBluetoothDevice.fetchUuidsWithSdp();
    }

    /** See {@link android.bluetooth.BluetoothDevice#createBond()}. */
    public boolean createBond() {
        return mWrappedBluetoothDevice.createBond();
    }

    /** See {@link android.bluetooth.BluetoothDevice#getUuids()}. */
    @Nullable(/* on error */)
    public ParcelUuid[] getUuids() {
        return mWrappedBluetoothDevice.getUuids();
    }

    /** See {@link android.bluetooth.BluetoothDevice#getBondState()}. */
    public int getBondState() {
        return mWrappedBluetoothDevice.getBondState();
    }

    /** See {@link android.bluetooth.BluetoothDevice#getAddress()}. */
    public String getAddress() {
        return mWrappedBluetoothDevice.getAddress();
    }

    /** See {@link android.bluetooth.BluetoothDevice#getBluetoothClass()}. */
    @Nullable(/* on error */)
    public BluetoothClass getBluetoothClass() {
        return mWrappedBluetoothDevice.getBluetoothClass();
    }

    /** See {@link android.bluetooth.BluetoothDevice#getType()}. */
    public int getType() {
        return mWrappedBluetoothDevice.getType();
    }

    /** See {@link android.bluetooth.BluetoothDevice#getName()}. */
    @Nullable(/* on error */)
    public String getName() {
        return mWrappedBluetoothDevice.getName();
    }

    /** See {@link android.bluetooth.BluetoothDevice#toString()}. */
    @Override
    public String toString() {
        return mWrappedBluetoothDevice.toString();
    }

    /** See {@link android.bluetooth.BluetoothDevice#hashCode()}. */
    @Override
    public int hashCode() {
        return mWrappedBluetoothDevice.hashCode();
    }

    /** See {@link android.bluetooth.BluetoothDevice#equals(Object)}. */
    @Override
    public boolean equals(@Nullable Object o) {
        if (o ==  this) {
            return true;
        }
        if (!(o instanceof BluetoothDevice)) {
            return false;
        }
        return mWrappedBluetoothDevice.equals(((BluetoothDevice) o).unwrap());
    }

    /** Unwraps a Bluetooth device. */
    public android.bluetooth.BluetoothDevice unwrap() {
        return mWrappedBluetoothDevice;
    }

    /** Wraps a Bluetooth device. */
    public static BluetoothDevice wrap(android.bluetooth.BluetoothDevice bluetoothDevice) {
        return new BluetoothDevice(bluetoothDevice);
    }
}

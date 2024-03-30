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
import android.os.Build;

import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.le.BluetoothLeAdvertiser;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.le.BluetoothLeScanner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Mockable wrapper of {@link android.bluetooth.BluetoothAdapter}.
 */
public class BluetoothAdapter {
    /** See {@link android.bluetooth.BluetoothAdapter#ACTION_REQUEST_ENABLE}. */
    public static final String ACTION_REQUEST_ENABLE =
            android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE;

    /** See {@link android.bluetooth.BluetoothAdapter#ACTION_STATE_CHANGED}. */
    public static final String ACTION_STATE_CHANGED =
            android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;

    /** See {@link android.bluetooth.BluetoothAdapter#EXTRA_STATE}. */
    public static final String EXTRA_STATE =
            android.bluetooth.BluetoothAdapter.EXTRA_STATE;

    /** See {@link android.bluetooth.BluetoothAdapter#STATE_OFF}. */
    public static final int STATE_OFF =
            android.bluetooth.BluetoothAdapter.STATE_OFF;

    /** See {@link android.bluetooth.BluetoothAdapter#STATE_ON}. */
    public static final int STATE_ON =
            android.bluetooth.BluetoothAdapter.STATE_ON;

    /** See {@link android.bluetooth.BluetoothAdapter#STATE_TURNING_OFF}. */
    public static final int STATE_TURNING_OFF =
            android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF;

    /** See {@link android.bluetooth.BluetoothAdapter#STATE_TURNING_ON}. */
    public static final int STATE_TURNING_ON =
            android.bluetooth.BluetoothAdapter.STATE_TURNING_ON;

    private final android.bluetooth.BluetoothAdapter mWrappedBluetoothAdapter;

    private BluetoothAdapter(android.bluetooth.BluetoothAdapter bluetoothAdapter) {
        mWrappedBluetoothAdapter = bluetoothAdapter;
    }

    /** See {@link android.bluetooth.BluetoothAdapter#disable()}. */
    public boolean disable() {
        return mWrappedBluetoothAdapter.disable();
    }

    /** See {@link android.bluetooth.BluetoothAdapter#enable()}. */
    public boolean enable() {
        return mWrappedBluetoothAdapter.enable();
    }

    /** See {@link android.bluetooth.BluetoothAdapter#getBluetoothLeScanner}. */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Nullable
    public BluetoothLeScanner getBluetoothLeScanner() {
        return BluetoothLeScanner.wrap(mWrappedBluetoothAdapter.getBluetoothLeScanner());
    }

    /** See {@link android.bluetooth.BluetoothAdapter#getBluetoothLeAdvertiser()}. */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Nullable
    public BluetoothLeAdvertiser getBluetoothLeAdvertiser() {
        return BluetoothLeAdvertiser.wrap(mWrappedBluetoothAdapter.getBluetoothLeAdvertiser());
    }

    /** See {@link android.bluetooth.BluetoothAdapter#getBondedDevices()}. */
    @Nullable
    public Set<BluetoothDevice> getBondedDevices() {
        Set<android.bluetooth.BluetoothDevice> bondedDevices =
                mWrappedBluetoothAdapter.getBondedDevices();
        if (bondedDevices == null) {
            return null;
        }
        Set<BluetoothDevice> result = new HashSet<BluetoothDevice>();
        for (android.bluetooth.BluetoothDevice device : bondedDevices) {
            if (device == null) {
                continue;
            }
            result.add(BluetoothDevice.wrap(device));
        }
        return Collections.unmodifiableSet(result);
    }

    /** See {@link android.bluetooth.BluetoothAdapter#getRemoteDevice(byte[])}. */
    public BluetoothDevice getRemoteDevice(byte[] address) {
        return BluetoothDevice.wrap(mWrappedBluetoothAdapter.getRemoteDevice(address));
    }

    /** See {@link android.bluetooth.BluetoothAdapter#getRemoteDevice(String)}. */
    public BluetoothDevice getRemoteDevice(String address) {
        return BluetoothDevice.wrap(mWrappedBluetoothAdapter.getRemoteDevice(address));
    }

    /** See {@link android.bluetooth.BluetoothAdapter#isEnabled()}. */
    public boolean isEnabled() {
        return mWrappedBluetoothAdapter.isEnabled();
    }

    /** See {@link android.bluetooth.BluetoothAdapter#isDiscovering()}. */
    public boolean isDiscovering() {
        return mWrappedBluetoothAdapter.isDiscovering();
    }

    /** See {@link android.bluetooth.BluetoothAdapter#startDiscovery()}. */
    public boolean startDiscovery() {
        return mWrappedBluetoothAdapter.startDiscovery();
    }

    /** See {@link android.bluetooth.BluetoothAdapter#cancelDiscovery()}. */
    public boolean cancelDiscovery() {
        return mWrappedBluetoothAdapter.cancelDiscovery();
    }

    /** See {@link android.bluetooth.BluetoothAdapter#getDefaultAdapter()}. */
    @Nullable
    public static BluetoothAdapter getDefaultAdapter() {
        android.bluetooth.BluetoothAdapter adapter =
                android.bluetooth.BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return null;
        }
        return new BluetoothAdapter(adapter);
    }

    /** Wraps a Bluetooth adapter. */
    @Nullable
    public static BluetoothAdapter wrap(
            @Nullable android.bluetooth.BluetoothAdapter bluetoothAdapter) {
        if (bluetoothAdapter == null) {
            return null;
        }
        return new BluetoothAdapter(bluetoothAdapter);
    }

    /** Unwraps a Bluetooth adapter. */
    public android.bluetooth.BluetoothAdapter unwrap() {
        return mWrappedBluetoothAdapter;
    }
}

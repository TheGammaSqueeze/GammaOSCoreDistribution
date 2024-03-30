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

package com.android.libraries.testing.deviceshadower.shadows.bluetooth;

import static com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl.getBlueletImpl;
import static com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl.getLocalBlueletImpl;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.Context;
import android.content.Intent;

import com.android.libraries.testing.deviceshadower.internal.bluetooth.BlueletImpl;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shadow of the Bluetooth A2DP service.
 */
@Implements(BluetoothA2dp.class)
public class ShadowBluetoothA2dp {

    /**
     * Hidden in {@link BluetoothProfile}.
     */
    public static final int A2DP_SINK = 11;

    private final Map<BluetoothDevice, Integer> mDeviceToConnectionState = new HashMap<>();
    private Context mContext;
    @RealObject
    private BluetoothA2dp mRealObject;

    public void __constructor__(Context context, ServiceListener l) {
        this.mContext = context;
        l.onServiceConnected(BluetoothProfile.A2DP, mRealObject);
    }

    @Implementation
    public List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> result = new ArrayList<>();
        for (BluetoothDevice device : mDeviceToConnectionState.keySet()) {
            if (getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                result.add(device);
            }
        }
        return result;
    }

    @Implementation
    public int getConnectionState(BluetoothDevice device) {
        return mDeviceToConnectionState.containsKey(device)
                ? mDeviceToConnectionState.get(device)
                : BluetoothProfile.STATE_DISCONNECTED;
    }

    @Implementation
    public boolean connect(BluetoothDevice device) {
        setConnectionState(BluetoothProfile.STATE_CONNECTING, device);
        // Only successfully connect if the device is in the environment (i.e. nearby) and accepts
        // connections.
        BlueletImpl blueLet = getBlueletImpl(device.getAddress());
        if (blueLet != null && !blueLet.getRefuseConnections()) {
            setConnectionState(BluetoothProfile.STATE_CONNECTED, device);
        } else {
            // If the device isn't in the environment, still return true (no immediate failure, i.e.
            // we're trying to connect) but send CONNECTING -> DISCONNECTED (like the OS does).
            setConnectionState(BluetoothProfile.STATE_DISCONNECTED, device);
        }
        return true;
    }

    @Implementation
    public void close() {
    }

    private void setConnectionState(int state, BluetoothDevice device) {
        int previousState = getConnectionState(device);
        mDeviceToConnectionState.put(device, state);

        getLocalBlueletImpl()
                .setProfileConnectionState(BluetoothProfile.A2DP, state, device.getAddress());
        BlueletImpl remoteDevice = getBlueletImpl(device.getAddress());
        if (remoteDevice != null) {
            remoteDevice.setProfileConnectionState(A2DP_SINK, state, getLocalBlueletImpl().address);
        }

        mContext.sendBroadcast(
                new Intent(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                        .putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, previousState)
                        .putExtra(BluetoothProfile.EXTRA_STATE, state)
                        .putExtra(BluetoothDevice.EXTRA_DEVICE, device));
    }
}

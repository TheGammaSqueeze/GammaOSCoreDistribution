/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.server.bluetooth;

import android.provider.DeviceConfig;
import android.provider.DeviceConfig.Properties;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * The BluetoothDeviceConfigChangeTracker receives changes to the DeviceConfig for
 * NAMESPACE_BLUETOOTH, and determines whether we should queue a restart, if any Bluetooth-related
 * INIT_ flags have been changed.
 *
 * <p>The initialProperties should be fetched from the BLUETOOTH namespace in DeviceConfig
 */
public final class BluetoothDeviceConfigChangeTracker {
    private static final String TAG = "BluetoothDeviceConfigChangeTracker";

    private final HashMap<String, String> mCurrFlags;

    public BluetoothDeviceConfigChangeTracker(Properties initialProperties) {
        mCurrFlags = getFlags(initialProperties);
    }

    /**
     * Updates the instance state tracking the latest init flag values, and determines whether an
     * init flag has changed (requiring a restart at some point)
     */
    public boolean shouldRestartWhenPropertiesUpdated(Properties newProperties) {
        if (!newProperties.getNamespace().equals(DeviceConfig.NAMESPACE_BLUETOOTH)) {
            return false;
        }
        ArrayList<String> flags = new ArrayList<>();
        for (String name : newProperties.getKeyset()) {
            flags.add(name + "='" + newProperties.getString(name, "") + "'");
        }
        Log.d(TAG, "shouldRestartWhenPropertiesUpdated: " + String.join(",", flags));
        boolean shouldRestart = false;
        for (String name : newProperties.getKeyset()) {
            if (!isInitFlag(name)) {
                continue;
            }
            var oldValue = mCurrFlags.get(name);
            var newValue = newProperties.getString(name, "");
            if (newValue.equals(oldValue)) {
                continue;
            }
            Log.d(TAG, "Property " + name + " changed from " + oldValue + " -> " + newValue);
            mCurrFlags.put(name, newValue);
            shouldRestart = true;
        }
        return shouldRestart;
    }

    private HashMap<String, String> getFlags(Properties initialProperties) {
        var out = new HashMap();
        for (var name : initialProperties.getKeyset()) {
            if (isInitFlag(name)) {
                out.put(name, initialProperties.getString(name, ""));
            }
        }
        return out;
    }

    private Boolean isInitFlag(String flagName) {
        return flagName.startsWith("INIT_");
    }
}

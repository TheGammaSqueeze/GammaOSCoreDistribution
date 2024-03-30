/*
 * Copyright 2020 The Android Open Source Project
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

import static com.android.server.bluetooth.BluetoothAirplaneModeListener.APM_ENHANCEMENT;
import static com.android.server.bluetooth.BluetoothAirplaneModeListener.BT_DEFAULT_APM_STATE;

import android.content.Context;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.util.Log;

/**
 * The BluetoothDeviceConfigListener handles system device config change callback and checks
 * whether we need to inform BluetoothManagerService on this change.
 *
 * The information of device config change would not be passed to the BluetoothManagerService
 * when Bluetooth is on and Bluetooth is in one of the following situations:
 *   1. Bluetooth A2DP is connected.
 *   2. Bluetooth Hearing Aid profile is connected.
 */
public class BluetoothDeviceConfigListener {
    private static final String TAG = "BluetoothDeviceConfigListener";

    private static final int DEFAULT_APM_ENHANCEMENT = 0;
    private static final int DEFAULT_BT_APM_STATE = 0;

    private final BluetoothManagerService mService;
    private final boolean mLogDebug;
    private final Context mContext;
    private final BluetoothDeviceConfigChangeTracker mConfigChangeTracker;

    private boolean mPrevApmEnhancement;
    private boolean mPrevBtApmState;

    BluetoothDeviceConfigListener(BluetoothManagerService service, boolean logDebug,
            Context context) {
        mService = service;
        mLogDebug = logDebug;
        mContext = context;
        mConfigChangeTracker =
                new BluetoothDeviceConfigChangeTracker(
                        DeviceConfig.getProperties(DeviceConfig.NAMESPACE_BLUETOOTH));
        updateApmConfigs();
        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_BLUETOOTH,
                (Runnable r) -> r.run(),
                mDeviceConfigChangedListener);
    }

    private void updateApmConfigs() {
        mPrevApmEnhancement = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_BLUETOOTH,
                APM_ENHANCEMENT, false);
        mPrevBtApmState = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_BLUETOOTH,
                BT_DEFAULT_APM_STATE, false);

        Settings.Global.putInt(mContext.getContentResolver(),
                APM_ENHANCEMENT, mPrevApmEnhancement ? 1 : 0);
        Settings.Global.putInt(mContext.getContentResolver(),
                BT_DEFAULT_APM_STATE, mPrevBtApmState ? 1 : 0);
    }

    private final DeviceConfig.OnPropertiesChangedListener mDeviceConfigChangedListener =
            new DeviceConfig.OnPropertiesChangedListener() {
                @Override
                public void onPropertiesChanged(DeviceConfig.Properties newProperties) {
                    boolean apmEnhancement = newProperties.getBoolean(
                            APM_ENHANCEMENT, mPrevApmEnhancement);
                    if (apmEnhancement != mPrevApmEnhancement) {
                        mPrevApmEnhancement = apmEnhancement;
                        Settings.Global.putInt(mContext.getContentResolver(),
                                APM_ENHANCEMENT, apmEnhancement ? 1 : 0);
                    }

                    boolean btApmState = newProperties.getBoolean(
                            BT_DEFAULT_APM_STATE, mPrevBtApmState);
                    if (btApmState != mPrevBtApmState) {
                        mPrevBtApmState = btApmState;
                        Settings.Global.putInt(mContext.getContentResolver(),
                                BT_DEFAULT_APM_STATE, btApmState ? 1 : 0);
                    }

                    if (mConfigChangeTracker.shouldRestartWhenPropertiesUpdated(newProperties)) {
                        Log.d(TAG, "Properties changed, enqueuing restart");
                        mService.onInitFlagsChanged();
                    } else {
                        Log.d(TAG, "All properties unchanged, skipping restart");
                    }
                }
            };
}

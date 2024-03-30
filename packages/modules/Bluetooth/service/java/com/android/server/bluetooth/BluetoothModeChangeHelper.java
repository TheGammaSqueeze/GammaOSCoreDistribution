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

import static com.android.server.bluetooth.BluetoothAirplaneModeListener.BLUETOOTH_APM_STATE;
import static com.android.server.bluetooth.BluetoothAirplaneModeListener.BT_DEFAULT_APM_STATE;

import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Helper class that handles callout and callback methods without
 * complex logic.
 */
public class BluetoothModeChangeHelper {
    private static final String TAG = "BluetoothModeChangeHelper";

    private volatile BluetoothA2dp mA2dp;
    private volatile BluetoothHearingAid mHearingAid;
    private volatile BluetoothLeAudio mLeAudio;
    private final BluetoothAdapter mAdapter;
    private final Context mContext;

    private String mBluetoothPackageName;

    BluetoothModeChangeHelper(Context context) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mContext = context;

        mAdapter.getProfileProxy(mContext, mProfileServiceListener, BluetoothProfile.A2DP);
        mAdapter.getProfileProxy(mContext, mProfileServiceListener,
                BluetoothProfile.HEARING_AID);
        mAdapter.getProfileProxy(mContext, mProfileServiceListener, BluetoothProfile.LE_AUDIO);
    }

    private final ServiceListener mProfileServiceListener = new ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            // Setup Bluetooth profile proxies
            switch (profile) {
                case BluetoothProfile.A2DP:
                    mA2dp = (BluetoothA2dp) proxy;
                    break;
                case BluetoothProfile.HEARING_AID:
                    mHearingAid = (BluetoothHearingAid) proxy;
                    break;
                case BluetoothProfile.LE_AUDIO:
                    mLeAudio = (BluetoothLeAudio) proxy;
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            // Clear Bluetooth profile proxies
            switch (profile) {
                case BluetoothProfile.A2DP:
                    mA2dp = null;
                    break;
                case BluetoothProfile.HEARING_AID:
                    mHearingAid = null;
                    break;
                case BluetoothProfile.LE_AUDIO:
                    mLeAudio = null;
                    break;
                default:
                    break;
            }
        }
    };

    @VisibleForTesting
    public boolean isMediaProfileConnected() {
        return isA2dpConnected() || isHearingAidConnected() || isLeAudioConnected();
    }

    @VisibleForTesting
    public boolean isBluetoothOn() {
        final BluetoothAdapter adapter = mAdapter;
        if (adapter == null) {
            return false;
        }
        return adapter.isLeEnabled();
    }

    @VisibleForTesting
    public boolean isAirplaneModeOn() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    @VisibleForTesting
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public void onAirplaneModeChanged(BluetoothManagerService managerService) {
        managerService.onAirplaneModeChanged();
    }

    @VisibleForTesting
    public int getSettingsInt(String name) {
        return Settings.Global.getInt(mContext.getContentResolver(),
                name, 0);
    }

    @VisibleForTesting
    public void setSettingsInt(String name, int value) {
        Settings.Global.putInt(mContext.getContentResolver(),
                name, value);
    }

    /**
     * Helper method to get Settings Secure Int value
     */
    public int getSettingsSecureInt(String name, int def) {
        Context userContext = mContext.createContextAsUser(
                UserHandle.of(ActivityManager.getCurrentUser()), 0);
        return Settings.Secure.getInt(userContext.getContentResolver(), name, def);
    }

    /**
     * Helper method to set Settings Secure Int value
     */
    public void setSettingsSecureInt(String name, int value) {
        Context userContext = mContext.createContextAsUser(
                UserHandle.of(ActivityManager.getCurrentUser()), 0);
        Settings.Secure.putInt(userContext.getContentResolver(), name, value);
    }

    @VisibleForTesting
    public void showToastMessage() {
        Resources r = mContext.getResources();
        final CharSequence text = r.getString(Resources.getSystem().getIdentifier(
                "bluetooth_airplane_mode_toast", "string", "android"));
        Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
    }

    private boolean isA2dpConnected() {
        final BluetoothA2dp a2dp = mA2dp;
        if (a2dp == null) {
            return false;
        }
        return a2dp.getConnectedDevices().size() > 0;
    }

    private boolean isHearingAidConnected() {
        final BluetoothHearingAid hearingAid = mHearingAid;
        if (hearingAid == null) {
            return false;
        }
        return hearingAid.getConnectedDevices().size() > 0;
    }

    private boolean isLeAudioConnected() {
        final BluetoothLeAudio leAudio = mLeAudio;
        if (leAudio == null) {
            return false;
        }
        return leAudio.getConnectedDevices().size() > 0;
    }

    /**
     * Helper method to check whether BT should be enabled on APM
     */
    public boolean isBluetoothOnAPM() {
        Context userContext = mContext.createContextAsUser(
                UserHandle.of(ActivityManager.getCurrentUser()), 0);
        int defaultBtApmState = getSettingsInt(BT_DEFAULT_APM_STATE);
        return Settings.Secure.getInt(userContext.getContentResolver(),
                BLUETOOTH_APM_STATE, defaultBtApmState) == 1;
    }

    /**
     * Helper method to retrieve BT package name with APM resources
     */
    public String getBluetoothPackageName() {
        if (mBluetoothPackageName != null) {
            return mBluetoothPackageName;
        }
        var allPackages = mContext.getPackageManager().getPackagesForUid(Process.BLUETOOTH_UID);
        for (String candidatePackage : allPackages) {
            Resources resources;
            try {
                resources = mContext.getPackageManager()
                        .getResourcesForApplication(candidatePackage);
            } catch (PackageManager.NameNotFoundException e) {
                // ignore, try next package
                Log.e(TAG, "Could not find package " + candidatePackage);
                continue;
            } catch (Exception e) {
                Log.e(TAG, "Error while loading package" + e);
                continue;
            }
            if (resources.getIdentifier("bluetooth_and_wifi_stays_on_title",
                    "string", candidatePackage) == 0) {
                continue;
            }
            mBluetoothPackageName = candidatePackage;
        }
        return mBluetoothPackageName;
    }
}

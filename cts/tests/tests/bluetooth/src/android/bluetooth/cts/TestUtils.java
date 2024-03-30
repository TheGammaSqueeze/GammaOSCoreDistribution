/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.bluetooth.cts;

import static org.junit.Assert.assertNotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanRecord;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.Assert;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Utility class for Bluetooth CTS test.
 */
class TestUtils {
    /**
     * Checks whether this device has Bluetooth feature
     * @return true if this device has Bluetooth feature
     */
    static boolean hasBluetooth() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
    }

    /**
     * Get the current enabled status of a given profile
     */
    static boolean isProfileEnabled(int profile) {
        switch (profile) {
            case BluetoothProfile.A2DP:
                return BluetoothProperties.isProfileA2dpSourceEnabled().orElse(false);
            case BluetoothProfile.A2DP_SINK:
                return BluetoothProperties.isProfileA2dpSinkEnabled().orElse(false);
            // Hidden profile
            // case BluetoothProfile.AVRCP:
            //     return BluetoothProperties.isProfileAvrcpTargetEnabled().orElse(false);
            case BluetoothProfile.AVRCP_CONTROLLER:
                return BluetoothProperties.isProfileAvrcpControllerEnabled().orElse(false);
            case BluetoothProfile.CSIP_SET_COORDINATOR:
                return BluetoothProperties.isProfileCsipSetCoordinatorEnabled().orElse(false);
            case BluetoothProfile.GATT:
                return BluetoothProperties.isProfileGattEnabled().orElse(true);
            case BluetoothProfile.HAP_CLIENT:
                return BluetoothProperties.isProfileHapClientEnabled().orElse(false);
            case BluetoothProfile.HEADSET:
                return BluetoothProperties.isProfileHfpAgEnabled().orElse(false);
            case BluetoothProfile.HEADSET_CLIENT:
                return BluetoothProperties.isProfileHfpHfEnabled().orElse(false);
            case BluetoothProfile.HEARING_AID:
                return BluetoothProperties.isProfileAshaCentralEnabled().orElse(false);
            case BluetoothProfile.HID_DEVICE:
                return BluetoothProperties.isProfileHidDeviceEnabled().orElse(false);
            case BluetoothProfile.HID_HOST:
                return BluetoothProperties.isProfileHidHostEnabled().orElse(false);
            case BluetoothProfile.LE_AUDIO:
                return BluetoothProperties.isProfileBapUnicastClientEnabled().orElse(false);
            case BluetoothProfile.LE_AUDIO_BROADCAST:
                return BluetoothProperties.isProfileBapBroadcastSourceEnabled().orElse(false);
            case BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT:
                return BluetoothProperties.isProfileBapBroadcastAssistEnabled().orElse(false);
            // Hidden profile
            // case BluetoothProfile.LE_CALL_CONTROL:
            //     return BluetoothProperties.isProfileCcpServerEnabled().orElse(false);
            case BluetoothProfile.MAP:
                return BluetoothProperties.isProfileMapServerEnabled().orElse(false);
            case BluetoothProfile.MAP_CLIENT:
                return BluetoothProperties.isProfileMapClientEnabled().orElse(false);
            // Hidden profile
            // case BluetoothProfile.MCP_SERVER:
            //     return BluetoothProperties.isProfileMcpServerEnabled().orElse(false);
            case BluetoothProfile.OPP:
                return BluetoothProperties.isProfileOppEnabled().orElse(false);
            case BluetoothProfile.PAN:
                return BluetoothProperties.isProfilePanNapEnabled().orElse(false)
                        || BluetoothProperties.isProfilePanPanuEnabled().orElse(false);
            case BluetoothProfile.PBAP:
                return BluetoothProperties.isProfilePbapServerEnabled().orElse(false);
            case BluetoothProfile.PBAP_CLIENT:
                return BluetoothProperties.isProfilePbapClientEnabled().orElse(false);
            case BluetoothProfile.SAP:
                return BluetoothProperties.isProfileSapServerEnabled().orElse(false);
            case BluetoothProfile.VOLUME_CONTROL:
                return BluetoothProperties.isProfileVcpControllerEnabled().orElse(false);
            default:
                return false;
        }
    }

    /**
     * Adopt shell UID's permission via {@link android.app.UiAutomation}
     * @param permission permission to adopt
     */
    static void adoptPermissionAsShellUid(@Nullable String... permission) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(permission);
    }

    /**
     * Drop all permissions adopted as shell UID
     */
    static void dropPermissionAsShellUid() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Get {@link BluetoothAdapter} via {@link android.bluetooth.BluetoothManager}
     * Fail the test if {@link BluetoothAdapter} is null
     * @return instance of {@link BluetoothAdapter}
     */
    @NonNull static BluetoothAdapter getBluetoothAdapterOrDie() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        assertNotNull(manager);
        BluetoothAdapter adapter = manager.getAdapter();
        assertNotNull(adapter);
        return adapter;
    }

    /**
     * Utility method to call hidden ScanRecord.parseFromBytes method.
     */
    static ScanRecord parseScanRecord(byte[] bytes) {
        Class<?> scanRecordClass = ScanRecord.class;
        try {
            Method method = scanRecordClass.getDeclaredMethod("parseFromBytes", byte[].class);
            return (ScanRecord) method.invoke(null, bytes);
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            return null;
        }
    }

    /**
     * Assert two byte arrays are equal.
     */
    static void assertArrayEquals(byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            Assert.fail("expected:<" + Arrays.toString(expected) +
                    "> but was:<" + Arrays.toString(actual) + ">");
        }
    }

    /**
     * Get current location mode settings.
     */
    static int getLocationMode(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
    }

    /**
     * Set location settings mode.
     */
    static void setLocationMode(Context context, int mode) {
        Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE,
                mode);
    }

    /**
     * Return true if location is on.
     */
    static boolean isLocationOn(Context context) {
        return getLocationMode(context) != Settings.Secure.LOCATION_MODE_OFF;
    }

    /**
     * Enable location and set the mode to GPS only.
     */
    static void enableLocation(Context context) {
        setLocationMode(context, Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
    }

    /**
     * Disable location.
     */
    static void disableLocation(Context context) {
        setLocationMode(context, Settings.Secure.LOCATION_MODE_OFF);
    }

    /**
     * Check if BLE is supported by this platform
     * @param context current device context
     * @return true if BLE is supported, false otherwise
     */
    static boolean isBleSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * Put the current thread to sleep.
     * @param sleepMillis number of milliseconds to sleep for
     */
    static void sleep(int sleepMillis) {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Log.e(TestUtils.class.getSimpleName(), "interrupted", e);
        }
    }
}

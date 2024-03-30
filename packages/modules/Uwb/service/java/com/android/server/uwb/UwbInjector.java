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

package com.android.server.uwb;

import static android.Manifest.permission.UWB_RANGING;
import static android.permission.PermissionManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.ApexEnvironment;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.provider.Settings;
import android.util.AtomicFile;
import android.util.Log;

import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.multchip.UwbMultichipData;

import java.io.File;
import java.util.Locale;

/**
 * To be used for dependency injection (especially helps mocking static dependencies).
 */
public class UwbInjector {
    private static final String TAG = "UwbInjector";
    private static final String APEX_NAME = "com.android.uwb";
    private static final String VENDOR_SERVICE_NAME = "uwb_vendor";
    private static final String BOOT_DEFAULT_UWB_COUNTRY_CODE = "ro.boot.uwbcountrycode";

    /**
     * The path where the Uwb apex is mounted.
     * Current value = "/apex/com.android.uwb"
     */
    private static final String UWB_APEX_PATH =
            new File("/apex", APEX_NAME).getAbsolutePath();
    private static final int APP_INFO_FLAGS_SYSTEM_APP =
            ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;

    private final UwbContext mContext;
    private final Looper mLooper;
    private final PermissionManager mPermissionManager;
    private final UwbSettingsStore mUwbSettingsStore;
    private final NativeUwbManager mNativeUwbManager;
    private final UwbCountryCode mUwbCountryCode;
    private final UwbServiceCore mUwbService;
    private final UwbMetrics mUwbMetrics;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final UwbMultichipData mUwbMultichipData;
    private final SystemBuildProperties mSystemBuildProperties;
    private final UwbDiagnostics mUwbDiagnostics;

    public UwbInjector(@NonNull UwbContext context) {
        // Create UWB service thread.
        HandlerThread uwbHandlerThread = new HandlerThread("UwbService");
        uwbHandlerThread.start();
        mLooper = uwbHandlerThread.getLooper();

        mContext = context;
        mPermissionManager = context.getSystemService(PermissionManager.class);
        mUwbSettingsStore = new UwbSettingsStore(
                context, new Handler(mLooper),
                new AtomicFile(new File(getDeviceProtectedDataDir(),
                        UwbSettingsStore.FILE_NAME)), this);
        mNativeUwbManager = new NativeUwbManager(this);
        mUwbCountryCode =
                new UwbCountryCode(mContext, mNativeUwbManager, new Handler(mLooper), this);
        mUwbMetrics = new UwbMetrics(this);
        mDeviceConfigFacade = new DeviceConfigFacade(new Handler(mLooper), this);
        mUwbMultichipData = new UwbMultichipData(mContext);
        UwbConfigurationManager uwbConfigurationManager =
                new UwbConfigurationManager(mNativeUwbManager);
        UwbSessionNotificationManager uwbSessionNotificationManager =
                new UwbSessionNotificationManager(this);
        UwbSessionManager uwbSessionManager =
                new UwbSessionManager(uwbConfigurationManager, mNativeUwbManager, mUwbMetrics,
                        uwbSessionNotificationManager, this,
                        mContext.getSystemService(AlarmManager.class), mLooper);
        mUwbService = new UwbServiceCore(mContext, mNativeUwbManager, mUwbMetrics,
                mUwbCountryCode, uwbSessionManager, uwbConfigurationManager, this, mLooper);
        mSystemBuildProperties = new SystemBuildProperties();
        mUwbDiagnostics = new UwbDiagnostics(mContext, this, mSystemBuildProperties);
    }

    public UwbSettingsStore getUwbSettingsStore() {
        return mUwbSettingsStore;
    }

    public NativeUwbManager getNativeUwbManager() {
        return mNativeUwbManager;
    }

    public UwbCountryCode getUwbCountryCode() {
        return mUwbCountryCode;
    }

    public UwbMetrics getUwbMetrics() {
        return mUwbMetrics;
    }

    public DeviceConfigFacade getDeviceConfigFacade() {
        return mDeviceConfigFacade;
    }

    public UwbMultichipData getMultichipData() {
        return mUwbMultichipData;
    }

    public UwbServiceCore getUwbServiceCore() {
        return mUwbService;
    }

    public UwbDiagnostics getUwbDiagnostics() {
        return mUwbDiagnostics;
    }

    /**
     * Create a UwbShellCommand instance.
     */
    public UwbShellCommand makeUwbShellCommand(UwbServiceImpl uwbService) {
        return new UwbShellCommand(this, uwbService, mContext);
    }

    /**
     * Throws security exception if the UWB_RANGING permission is not granted for the calling app.
     *
     * <p>Should be used in situations where the app op should not be noted.
     */
    public void enforceUwbRangingPermissionForPreflight(
            @NonNull AttributionSource attributionSource) {
        if (!attributionSource.checkCallingUid()) {
            throw new SecurityException("Invalid attribution source " + attributionSource
                    + ", callingUid: " + Binder.getCallingUid());
        }
        int permissionCheckResult = mPermissionManager.checkPermissionForPreflight(
                UWB_RANGING, attributionSource);
        if (permissionCheckResult != PERMISSION_GRANTED) {
            throw new SecurityException("Caller does not hold UWB_RANGING permission");
        }
    }

    /**
     * Returns true if the UWB_RANGING permission is granted for the calling app.
     *
     * <p>Should be used in situations where data will be delivered and hence the app op should
     * be noted.
     */
    public boolean checkUwbRangingPermissionForDataDelivery(
            @NonNull AttributionSource attributionSource, @NonNull String message) {
        int permissionCheckResult = mPermissionManager.checkPermissionForDataDelivery(
                UWB_RANGING, attributionSource, message);
        return permissionCheckResult == PERMISSION_GRANTED;
    }

    /**
     * Get device protected storage dir for the UWB apex.
     */
    @NonNull
    public File getDeviceProtectedDataDir() {
        return ApexEnvironment.getApexEnvironment(APEX_NAME).getDeviceProtectedDataDir();
    }

    /**
     * Get integer value from Settings.
     *
     * @throws Settings.SettingNotFoundException
     */
    public int getSettingsInt(@NonNull String key) throws Settings.SettingNotFoundException {
        return Settings.Global.getInt(mContext.getContentResolver(), key);
    }

    /**
     * Get integer value from Settings.
     */
    public int getSettingsInt(@NonNull String key, int defValue) {
        return Settings.Global.getInt(mContext.getContentResolver(), key, defValue);
    }

    /**
     * Returns true if the app is in the Uwb apex, false otherwise.
     * Checks if the app's path starts with "/apex/com.android.uwb".
     */
    public static boolean isAppInUwbApex(ApplicationInfo appInfo) {
        return appInfo.sourceDir.startsWith(UWB_APEX_PATH);
    }

    /**
     * Get the current time of the clock in milliseconds.
     *
     * @return Current time in milliseconds.
     */
    public long getWallClockMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Returns milliseconds since boot, including time spent in sleep.
     *
     * @return Current time since boot in milliseconds.
     */
    public long getElapsedSinceBootMillis() {
        return SystemClock.elapsedRealtime();
    }

    /**
     * Returns nanoseconds since boot, including time spent in sleep.
     *
     * @return Current time since boot in milliseconds.
     */
    public long getElapsedSinceBootNanos() {
        return SystemClock.elapsedRealtimeNanos();
    }

    /**
     * Is this a valid country code
     * @param countryCode A 2-Character alphanumeric country code.
     * @return true if the countryCode is valid, false otherwise.
     */
    private static boolean isValidCountryCode(String countryCode) {
        return countryCode != null && countryCode.length() == 2
                && countryCode.chars().allMatch(Character::isLetterOrDigit);
    }

    /**
     * Default country code stored in system property
     *
     * @return Country code if available, null otherwise.
     */
    public String getOemDefaultCountryCode() {
        String country = SystemProperties.get(BOOT_DEFAULT_UWB_COUNTRY_CODE);
        return isValidCountryCode(country) ? country.toUpperCase(Locale.US) : null;
    }

    /**
     * Helper method creating a context based on the app's uid (to deal with multi user scenarios)
     */
    @Nullable
    private Context createPackageContextAsUser(int uid) {
        Context userContext;
        try {
            userContext = mContext.createPackageContextAsUser(mContext.getPackageName(), 0,
                    UserHandle.getUserHandleForUid(uid));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unknown package name");
            return null;
        }
        if (userContext == null) {
            Log.e(TAG, "Unable to retrieve user context for " + uid);
            return null;
        }
        return userContext;
    }

    /** Helper method to check if the app is a system app. */
    public boolean isSystemApp(int uid, @NonNull String packageName) {
        try {
            ApplicationInfo info = createPackageContextAsUser(uid)
                    .getPackageManager()
                    .getApplicationInfo(packageName, 0);
            return (info.flags & APP_INFO_FLAGS_SYSTEM_APP) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            // In case of exception, assume unknown app (more strict checking)
            // Note: This case will never happen since checkPackage is
            // called to verify validity before checking App's version.
            Log.e(TAG, "Failed to get the app info", e);
        }
        return false;
    }

    /** Helper method to retrieve app importance. */
    private int getPackageImportance(int uid, @NonNull String packageName) {
        try {
            return createPackageContextAsUser(uid)
                    .getSystemService(ActivityManager.class)
                    .getPackageImportance(packageName);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to retrieve the app importance", e);
            return ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;
        }
    }

    /** Helper method to check if the app is from foreground app/service. */
    public boolean isForegroundAppOrService(int uid, @NonNull String packageName) {
        try {
            return getPackageImportance(uid, packageName)
                    <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to retrieve the app importance", e);
            return false;
        }
    }
}

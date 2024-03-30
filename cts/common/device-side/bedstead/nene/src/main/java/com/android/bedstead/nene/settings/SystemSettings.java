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

package com.android.bedstead.nene.settings;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.RequiresApi;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Versions;

/** APIs related to {@link Settings.System}. */
public final class SystemSettings {

    public static final SystemSettings sInstance = new SystemSettings();

    private SystemSettings() {

    }

    /**
     * See {@link Settings.System#putInt(ContentResolver, String, int)}
     */
    @RequiresApi(Build.VERSION_CODES.S)
    public void putInt(ContentResolver contentResolver, String key, int value) {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S);
        try (PermissionContext p = TestApis.permissions().withPermission(
                INTERACT_ACROSS_USERS_FULL, WRITE_SECURE_SETTINGS)) {
            Settings.System.putInt(contentResolver, key, value);
        }
    }

    /**
     * Put int to global settings for the given {@link UserReference}.
     *
     * <p>If the user is not the instrumented user, this will only succeed when running on Android S
     * and above.
     *
     * <p>See {@link #putInt(ContentResolver, String, int)}
     */
    @SuppressLint("NewApi")
    public void putInt(UserReference user, String key, int value) {
        if (user.equals(TestApis.users().instrumented())) {
            putInt(key, value);
            return;
        }

        putInt(TestApis.context().androidContextAsUser(user).getContentResolver(), key, value);
    }

    /**
     * Put int to global settings for the instrumented user.
     *
     * <p>See {@link #putInt(ContentResolver, String, int)}
     */
    public void putInt(String key, int value) {
        try (PermissionContext p = TestApis.permissions().withPermission(WRITE_SECURE_SETTINGS)) {
            Settings.System.putInt(
                    TestApis.context().instrumentedContext().getContentResolver(), key, value);
        }
    }

    /**
     * See {@link Settings.System#putString(ContentResolver, String, String)}
     */
    @RequiresApi(Build.VERSION_CODES.S)
    public void putString(ContentResolver contentResolver, String key, String value) {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S);
        try (PermissionContext p = TestApis.permissions().withPermission(
                INTERACT_ACROSS_USERS_FULL, WRITE_SECURE_SETTINGS)) {
            Settings.System.putString(contentResolver, key, value);
        }
    }

    /**
     * Put string to global settings for the given {@link UserReference}.
     *
     * <p>If the user is not the instrumented user, this will only succeed when running on Android S
     * and above.
     *
     * <p>See {@link #putString(ContentResolver, String, String)}
     */
    @SuppressLint("NewApi")
    public void putString(UserReference user, String key, String value) {
        if (user.equals(TestApis.users().instrumented())) {
            putString(key, value);
            return;
        }

        putString(TestApis.context().androidContextAsUser(user).getContentResolver(), key, value);
    }

    /**
     * Put string to global settings for the instrumented user.
     *
     * <p>See {@link #putString(ContentResolver, String, String)}
     */
    public void putString(String key, String value) {
        try (PermissionContext p = TestApis.permissions().withPermission(WRITE_SECURE_SETTINGS)) {
            Settings.System.putString(
                    TestApis.context().instrumentedContext().getContentResolver(), key, value);
        }
    }

    /**
     * See {@link Settings.System#getInt(ContentResolver, String)}
     */
    @RequiresApi(Build.VERSION_CODES.S)
    public int getInt(ContentResolver contentResolver, String key) {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S);
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            return getIntInner(contentResolver, key);
        }
    }

    /**
     * See {@link Settings.System#getInt(ContentResolver, String, int)}
     */
    @RequiresApi(Build.VERSION_CODES.S)
    public int getInt(ContentResolver contentResolver, String key, int defaultValue) {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S);
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            return getIntInner(contentResolver, key, defaultValue);
        }
    }

    private int getIntInner(ContentResolver contentResolver, String key) {
        try {
            return Settings.System.getInt(contentResolver, key);
        } catch (Settings.SettingNotFoundException e) {
            throw new NeneException("Error getting int setting", e);
        }
    }

    private int getIntInner(ContentResolver contentResolver, String key, int defaultValue) {
        return Settings.System.getInt(contentResolver, key, defaultValue);
    }

    /**
     * Get int from System settings for the given {@link UserReference}.
     *
     * <p>If the user is not the instrumented user, this will only succeed when running on Android S
     * and above.
     *
     * <p>See {@link #getInt(ContentResolver, String)}
     */
    @SuppressLint("NewApi")
    public int getInt(UserReference user, String key) {
        if (user.equals(TestApis.users().instrumented())) {
            return getInt(key);
        }
        return getInt(TestApis.context().androidContextAsUser(user).getContentResolver(), key);
    }

    /**
     * Get int from System settings for the given {@link UserReference}, or the default value.
     *
     * <p>If the user is not the instrumented user, this will only succeed when running on Android S
     * and above.
     *
     * <p>See {@link #getInt(ContentResolver, String, int)}
     */
    @SuppressLint("NewApi")
    public int getInt(UserReference user, String key, int defaultValue) {
        if (user.equals(TestApis.users().instrumented())) {
            return getInt(key, defaultValue);
        }
        return getInt(
                TestApis.context().androidContextAsUser(user).getContentResolver(),
                key, defaultValue);
    }

    /**
     * Get int from System settings for the instrumented user.
     *
     * <p>See {@link #getInt(ContentResolver, String)}
     */
    public int getInt(String key) {
        return getIntInner(TestApis.context().instrumentedContext().getContentResolver(), key);
    }

    /**
     * Get int from System settings for the instrumented user, or the default value.
     *
     * <p>See {@link #getInt(ContentResolver, String)}
     */
    public int getInt(String key, int defaultValue) {
        return getIntInner(
                TestApis.context().instrumentedContext().getContentResolver(), key, defaultValue);
    }

    /**
     * See {@link Settings.System#getString(ContentResolver, String)}
     */
    @RequiresApi(Build.VERSION_CODES.S)
    public String getString(ContentResolver contentResolver, String key) {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S);
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            return getStringInner(contentResolver, key);
        }
    }

    private String getStringInner(ContentResolver contentResolver, String key) {
        return Settings.System.getString(contentResolver, key);
    }

    /**
     * Get string from System settings for the given {@link UserReference}.
     *
     * <p>If the user is not the instrumented user, this will only succeed when running on Android S
     * and above.
     *
     * <p>See {@link #getString(ContentResolver, String)}
     */
    @SuppressLint("NewApi")
    public String getString(UserReference user, String key) {
        if (user.equals(TestApis.users().instrumented())) {
            return getString(key);
        }
        return getString(TestApis.context().androidContextAsUser(user).getContentResolver(), key);
    }

    /**
     * Get string from System settings for the instrumented user.
     *
     * <p>See {@link #getString(ContentResolver, String)}
     */
    public String getString(String key) {
        return getStringInner(TestApis.context().instrumentedContext().getContentResolver(), key);
    }
}

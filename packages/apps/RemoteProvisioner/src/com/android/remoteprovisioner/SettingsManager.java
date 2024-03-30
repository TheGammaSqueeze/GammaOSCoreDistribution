/**
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

package com.android.remoteprovisioner;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * SettingsManager makes use of SharedPreferences in order to store key/value pairs related to
 * configuration settings that can be retrieved from the server. In the event that none have yet
 * been retrieved, or for some reason a reset has occurred, there are reasonable default values.
 */
public class SettingsManager {

    public static final int ID_UPPER_BOUND = 1000000;
    public static final int EXTRA_SIGNED_KEYS_AVAILABLE_DEFAULT = 6;
    // Check for expiring certs in the next 3 days
    public static final int EXPIRING_BY_MS_DEFAULT = 1000 * 60 * 60 * 24 * 3;
    public static final String URL_DEFAULT = "https://remoteprovisioning.googleapis.com/v1";
    public static final boolean IS_TEST_MODE = false;
    // Limit data consumption from failures within a window of time to 1 MB.
    public static final int FAILURE_DATA_USAGE_MAX = 1024 * 1024;
    public static final Duration FAILURE_DATA_USAGE_WINDOW = Duration.ofDays(1);

    private static final String KEY_EXPIRING_BY = "expiring_by";
    private static final String KEY_EXTRA_KEYS = "extra_keys";
    private static final String KEY_ID = "settings_id";
    private static final String KEY_FAILURE_DATA_WINDOW_START_TIME = "failure_start_time";
    private static final String KEY_FAILURE_COUNTER = "failure_counter";
    private static final String KEY_FAILURE_BYTES = "failure_data";
    private static final String KEY_URL = "url";
    private static final String PREFERENCES_NAME = "com.android.remoteprovisioner.preferences";
    private static final String TAG = "RemoteProvisionerSettings";

    /**
     * Determines whether or not there is enough data budget remaining to attempt provisioning.
     * If {@code FAILURE_DATA_USAGE_MAX} bytes have already been used up in previous calls that
     * resulted in errors, then false will be returned.
     *
     * Additionally, the rolling window of data usage is managed within this call. The used data
     * budget will be reset if a time greater than @{code FAILURE_DATA_USAGE_WINDOW} has passed.
     *
     * @param context The application context
     * @param curTime An instant representing the current time to measure the window against. If
     *                null, then the code will use {@code Instant.now()} instead.
     * @return whether or not the data budget has been exceeded.
     */
    public static boolean hasErrDataBudget(Context context, Instant curTime) {
        if (curTime == null) {
            curTime = Instant.now();
        }
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Instant logged =
                Instant.ofEpochMilli(sharedPref.getLong(KEY_FAILURE_DATA_WINDOW_START_TIME, 0));
        if (Duration.between(logged, curTime).compareTo(FAILURE_DATA_USAGE_WINDOW) > 0) {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putLong(KEY_FAILURE_DATA_WINDOW_START_TIME, curTime.toEpochMilli());
            editor.putInt(KEY_FAILURE_BYTES, 0);
            editor.apply();
            return true;
        }
        return sharedPref.getInt(KEY_FAILURE_BYTES, 0) < FAILURE_DATA_USAGE_MAX;
    }

    /**
     * Fetches the amount of data currently consumed by calls within the current accounting window
     * to the backend that resulted in errors and returns it.
     *
     * @param context the application context.
     * @return the amount of data consumed.
     */
    public static int getErrDataBudgetConsumed(Context context) {
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return sharedPref.getInt(KEY_FAILURE_BYTES, 0);
    }

    /**
     * Increments the counter of data currently used up in transactions with the backend server.
     * This call will not check the current state of the rolling window, leaving that up to
     * {@code hasDataBudget}.
     *
     * @param context the application context.
     * @param bytesTransacted the number of bytes sent or received over the network. Must be a value
     *                        greater than {@code 0}.
     */
    public static void consumeErrDataBudget(Context context, int bytesTransacted) {
        if (bytesTransacted < 1) return;
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        int budgetUsed = 0;
        try {
            budgetUsed = Math.addExact(sharedPref.getInt(KEY_FAILURE_BYTES, 0), bytesTransacted);
        } catch (Exception e) {
            Log.e(TAG, "Overflow on number of bytes sent over the network.");
            budgetUsed = Integer.MAX_VALUE;
        }
        editor.putInt(KEY_FAILURE_BYTES, budgetUsed);
        editor.apply();
    }

    /**
     * Generates a random ID for the use of gradual ramp up of remote provisioning.
     */
    public static void generateAndSetId(Context context) {
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (sharedPref.contains(KEY_ID)) {
            // ID is already set, don't rotate it.
            return;
        }
        Log.i(TAG, "Setting ID");
        Random rand = new Random();
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(KEY_ID, rand.nextInt(ID_UPPER_BOUND));
        editor.apply();
    }

    /**
     * Fetches the generated ID.
     */
    public static int getId(Context context) {
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Random rand = new Random();
        return sharedPref.getInt(KEY_ID, rand.nextInt(ID_UPPER_BOUND) /* defaultValue */);
    }

    public static void resetDefaultConfig(Context context) {
        setDeviceConfig(
                context,
                EXTRA_SIGNED_KEYS_AVAILABLE_DEFAULT,
                Duration.ofMillis(EXPIRING_BY_MS_DEFAULT),
                URL_DEFAULT);
        clearFailureCounter(context);
    }

    /**
     * Sets the remote provisioning configuration values based on what was fetched from the server.
     * The server is not guaranteed to have sent every available parameter in the config that
     * was returned to the device, so the parameters should be checked for null values.
     *
     * @param extraKeys How many server signed remote provisioning key pairs that should be kept
     *                  available in KeyStore.
     * @param expiringBy How far in the future the app should check for expiring keys.
     * @param url The base URL for the provisioning server.
     * @return {@code true} if any settings were updated.
     */
    public static boolean setDeviceConfig(Context context, int extraKeys,
                                          Duration expiringBy, String url) {
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        boolean wereUpdatesMade = false;
        if (extraKeys != GeekResponse.NO_EXTRA_KEY_UPDATE
                && sharedPref.getInt(KEY_EXTRA_KEYS, -5) != extraKeys) {
            editor.putInt(KEY_EXTRA_KEYS, extraKeys);
            wereUpdatesMade = true;
        }
        if (expiringBy != null
                && sharedPref.getLong(KEY_EXPIRING_BY, -1) != expiringBy.toMillis()) {
            editor.putLong(KEY_EXPIRING_BY, expiringBy.toMillis());
            wereUpdatesMade = true;
        }
        if (url != null && !sharedPref.getString(KEY_URL, "").equals(url)) {
            editor.putString(KEY_URL, url);
            wereUpdatesMade = true;
        }
        if (wereUpdatesMade) {
            editor.apply();
        }
        return wereUpdatesMade;
    }

    /**
     * Gets the setting for how many extra keys should be kept signed and available in KeyStore.
     */
    public static int getExtraSignedKeysAvailable(Context context) {
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return sharedPref.getInt(KEY_EXTRA_KEYS, EXTRA_SIGNED_KEYS_AVAILABLE_DEFAULT);
    }

    /**
     * Gets the setting for how far into the future the provisioner should check for expiring keys.
     */
    public static Duration getExpiringBy(Context context) {
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return Duration.ofMillis(sharedPref.getLong(KEY_EXPIRING_BY, EXPIRING_BY_MS_DEFAULT));
    }

    /**
     * Returns an Instant which represents the point in time that the provisioner should check
     * keys for expiration.
     */
    public static Instant getExpirationTime(Context context) {
        return Instant.now().plusMillis(getExpiringBy(context).toMillis());
    }

    /**
     * Gets the setting for what base URL the provisioner should use to talk to provisioning
     * servers.
     */
    public static String getUrl(Context context) {
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return sharedPref.getString(KEY_URL, URL_DEFAULT);
    }

    /**
     * Increments the failure counter. This is intended to be used when reaching the server fails
     * for any reason so that the app logic can decide if the preferences should be reset to
     * defaults in the event that a bad push stored an incorrect URL string.
     *
     * @return the current failure counter after incrementing.
     */
    public static int incrementFailureCounter(Context context) {
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        int failures = sharedPref.getInt(KEY_FAILURE_COUNTER, 0 /* defaultValue */);
        editor.putInt(KEY_FAILURE_COUNTER, ++failures);
        editor.apply();
        return failures;
    }

    /**
     * Gets the current failure counter.
     */
    public static int getFailureCounter(Context context) {
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return sharedPref.getInt(KEY_FAILURE_COUNTER, 0 /* defaultValue */);
    }

    /**
     * Resets the failure counter to {@code 0}.
     */
    public static void clearFailureCounter(Context context) {
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (sharedPref.getInt(KEY_FAILURE_COUNTER, 0) != 0) {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt(KEY_FAILURE_COUNTER, 0);
            editor.apply();
        }
    }

    /**
     * Clears all preferences, thus restoring the defaults.
     */
    public static void clearPreferences(Context context) {
        SharedPreferences sharedPref =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.clear();
        editor.apply();
    }

    /**
     * Checks whether RKP is in test mode.
     * @return true if device is in test mode, false otherwise.
     */
    public static boolean isTestMode() {
        return IS_TEST_MODE;
    }
}

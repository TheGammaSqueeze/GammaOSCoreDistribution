/*
 * Copyright (C) 2024
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

package com.android.tv.settings.gammasystemtweaks;

import android.os.Bundle;
import android.util.Log;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.R;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Fragment to manage multiple system properties with toggle switches.
 * Properties: persist.sys.enable_mem_clear, persist.sys.disable_32bit_mode, persist.sys.disable_webview
 */
public class GammaSystemTweaksFragment extends SettingsPreferenceFragment {

    private static final String TAG = "GammaSystemTweaks";  // Tag for logging

    // System properties we want to toggle
    private static final String PROP_MEM_CLEAR = "persist.sys.enable_mem_clear";
    private static final String PROP_DISABLE_32BIT_MODE = "persist.sys.disable_32bit_mode";
    private static final String PROP_DISABLE_WEBVIEW = "persist.sys.disable_webview";

    // Preference keys (matching the XML keys)
    private static final String KEY_MEM_CLEAR = "mem_clear_toggle";
    private static final String KEY_DISABLE_32BIT_MODE = "disable_32bit_mode_toggle";
    private static final String KEY_DISABLE_WEBVIEW = "disable_webview_toggle";

    // Switch preferences
    private SwitchPreference memClearSwitch;
    private SwitchPreference disable32BitModeSwitch;
    private SwitchPreference disableWebviewSwitch;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preference layout (res/xml/gamma_system_tweaks.xml)
        setPreferencesFromResource(R.xml.gamma_system_tweaks, rootKey);

        // Find preferences by key
        memClearSwitch = findPreference(KEY_MEM_CLEAR);
        disable32BitModeSwitch = findPreference(KEY_DISABLE_32BIT_MODE);
        disableWebviewSwitch = findPreference(KEY_DISABLE_WEBVIEW);

        // Initialize the switch states based on the system properties
        if (memClearSwitch != null) {
            memClearSwitch.setChecked(getSystemProperty(PROP_MEM_CLEAR).equals("1"));
            memClearSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                setSystemProperty(PROP_MEM_CLEAR, (Boolean) newValue ? "1" : "0");
                return true;
            });
        }

        if (disable32BitModeSwitch != null) {
            disable32BitModeSwitch.setChecked(getSystemProperty(PROP_DISABLE_32BIT_MODE).equals("1"));
            disable32BitModeSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                setSystemProperty(PROP_DISABLE_32BIT_MODE, (Boolean) newValue ? "1" : "0");
                return true;
            });
        }

        if (disableWebviewSwitch != null) {
            disableWebviewSwitch.setChecked(getSystemProperty(PROP_DISABLE_WEBVIEW).equals("1"));
            disableWebviewSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                setSystemProperty(PROP_DISABLE_WEBVIEW, (Boolean) newValue ? "1" : "0");
                return true;
            });
        }
    }

    /**
     * Get the current value of a system property.
     * 
     * @param property The name of the system property.
     * @return The current value of the property as a string.
     */
    private String getSystemProperty(String property) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"getprop", property});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return reader.readLine().trim();
        } catch (IOException e) {
            Log.e(TAG, "Error retrieving system property: " + property, e);
        }
        return "0";  // Default to "0" if property doesn't exist or fails
    }

    /**
     * Set a system property to a given value.
     * 
     * @param property The name of the system property.
     * @param value The value to set for the property.
     */
    private void setSystemProperty(String property, String value) {
        try {
            Process process = new ProcessBuilder("setprop", property, value).start();
            process.waitFor();  // Ensure the command completes
            Log.d(TAG, "Set system property: " + property + " to " + value);
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error setting system property: " + property, e);
        }
    }

    @Override
    public int getPageId() {
        return 0;  // Replace with the actual page ID if needed
    }
}

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

package com.android.tv.settings.accessibility;

import static com.android.tv.settings.library.ManagerUtil.STATE_ACCESSIBILITY_SHORTCUT;
import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.app.tvsettings.TvSettingsEnums;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;
import com.android.tv.settings.library.accessibility.AccessibilityShortcutState;

/**
 * Fragment for configuring the accessibility shortcut
 */
@Keep
public class AccessibilityShortcutFragmentCompat extends PreferenceControllerFragmentCompat {
    private static final String KEY_ENABLE = "enable";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager()
                .setPreferenceComparisonCallback(
                        new PreferenceManager.SimplePreferenceComparisonCallback());
        setPreferencesFromResource(R.xml.accessibility_shortcut_compat, null);

        final TwoStatePreference enablePref = findPreference(KEY_ENABLE);
        enablePref.setOnPreferenceChangeListener((preference, newValue) -> {
            logToggleInteracted(TvSettingsEnums.SYSTEM_A11Y_SHORTCUT_ON_OFF, (Boolean) newValue);
            AccessibilityShortcutState state = getState(AccessibilityShortcutState.class);
            state.setAccessibilityShortcutEnabled((Boolean) newValue);
            return true;
        });
    }

    @Override
    public int getStateIdentifier() {
        return STATE_ACCESSIBILITY_SHORTCUT;
    }
}

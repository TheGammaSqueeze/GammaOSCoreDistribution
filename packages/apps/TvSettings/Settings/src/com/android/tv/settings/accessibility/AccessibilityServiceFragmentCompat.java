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

import static com.android.tv.settings.library.ManagerUtil.STATE_ACCESSIBILITY_SERVICE;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceManager;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.HasKeys;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;
import com.android.tv.settings.compat.RenderUtil;
import com.android.tv.settings.compat.TsPreferenceCategory;
import com.android.tv.settings.compat.TsRestrictedSwitchPreference;
import com.android.tv.settings.library.PreferenceCompat;

/**
 * Fragment for controlling accessibility service
 */
@Keep
public class AccessibilityServiceFragmentCompat extends
        PreferenceControllerFragmentCompat {
    private static final String KEY_SCREEN = "screen";
    private static final String KEY_ENABLE = "enable";

    @Override
    public int getStateIdentifier() {
        return STATE_ACCESSIBILITY_SERVICE;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager()
                .setPreferenceComparisonCallback(
                        new PreferenceManager.SimplePreferenceComparisonCallback());
        setPreferencesFromResource(R.xml.accessibility_service_compat, null);
    }

    @Override
    public HasKeys updatePref(PreferenceCompat prefCompat) {
        HasKeys preference = super.updatePref(prefCompat);
        if (preference == null) {
            return null;
        }
        switch (prefCompat.getKey()[0]) {
            case KEY_SCREEN:
                final TsPreferenceCategory screen = findPreference(KEY_SCREEN);
                RenderUtil.updatePreferenceGroup(screen, prefCompat.getChildPrefCompats());
                // Set OnPreferenceChangeListener to the ENABLE switch after them created.
                TsRestrictedSwitchPreference enable = findPreference(KEY_ENABLE);
                enable.setOnPreferenceChangeListener(this);
                break;
            default:
                // no-op
        }
        return preference;
    }
}

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

import static com.android.tv.settings.library.ManagerUtil.STATE_ACCESSIBILITY;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.HasKeys;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;
import com.android.tv.settings.compat.RenderUtil;
import com.android.tv.settings.library.PreferenceCompat;

/**
 * Fragment for Accessibility settings
 */
@Keep
public class AccessibilityFragmentCompat extends PreferenceControllerFragmentCompat {
    private static final String ACCESSIBILITY_SERVICES_KEY = "system_accessibility_services";
    private static final String TOGGLE_HIGH_TEXT_CONTRAST_KEY = "toggle_high_text_contrast";
    private static final String TOGGLE_AUDIO_DESCRIPTION_KEY = "toggle_audio_description";

    private PreferenceGroup mServicesPref;

    @Override
    public int getStateIdentifier() {
        return STATE_ACCESSIBILITY;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager()
                .setPreferenceComparisonCallback(
                        new PreferenceManager.SimplePreferenceComparisonCallback());
        setPreferencesFromResource(R.xml.accessibility_compat, null);

        mServicesPref = (PreferenceGroup) findPreference(ACCESSIBILITY_SERVICES_KEY);
        findPreference(TOGGLE_HIGH_TEXT_CONTRAST_KEY).setOnPreferenceChangeListener(this);
        findPreference(TOGGLE_AUDIO_DESCRIPTION_KEY).setOnPreferenceChangeListener(this);
    }

    @Override
    public HasKeys updatePref(PreferenceCompat prefCompat) {
        HasKeys preference = super.updatePref(prefCompat);
        if (preference == null) {
            return null;
        }
        switch (prefCompat.getKey()[0]) {
            case ACCESSIBILITY_SERVICES_KEY:
                RenderUtil.updatePreferenceGroup(
                        mServicesPref, prefCompat.getChildPrefCompats());
                break;
            default:
                // no-op
        }
        return preference;
    }
}

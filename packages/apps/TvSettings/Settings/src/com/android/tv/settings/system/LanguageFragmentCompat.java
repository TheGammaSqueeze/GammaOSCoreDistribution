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

package com.android.tv.settings.system;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.Preference;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.HasKeys;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;
import com.android.tv.settings.compat.RenderUtil;
import com.android.tv.settings.compat.TsRadioPreference;
import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;

import java.util.Arrays;

/**
 * Fragment compat for language settings screen in TV Settings.
 */
@Keep
public class LanguageFragmentCompat extends PreferenceControllerFragmentCompat {
    private static final String[] LANGUAGE_RADIO_GROUP = new String[]{"language"};

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.language_compat, null);
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_LANGUAGE;
    }

    @Override
    public HasKeys updatePref(PreferenceCompat preferenceCompat) {
        if (preferenceCompat == null) {
            return null;
        }
        if (Arrays.equals(preferenceCompat.getKey(), LANGUAGE_RADIO_GROUP)
                && preferenceCompat.getChildPrefCompats() != null) {
            preferenceCompat.getChildPrefCompats().stream().forEach(
                    childPrefCompat -> {
                        Preference preference = findTargetPreference(childPrefCompat.getKey());
                        if (preference == null) {
                            preference = (Preference) RenderUtil.createPreference(getContext(),
                                    childPrefCompat);
                            getPreferenceScreen().addPreference(preference);
                        }
                        if ((preference instanceof TsRadioPreference)) {
                            final TsRadioPreference languagePref = (TsRadioPreference) preference;
                            RenderUtil.updatePreference(
                                    getContext(), languagePref, childPrefCompat,
                                    preference.getOrder());
                            languagePref.setRadioGroup(LANGUAGE_RADIO_GROUP[0]);
                            if (childPrefCompat.isFocused()) {
                                final Preference focusedPref = preference;
                                scrollToPreference(focusedPref.getKey());
                            }
                        }
                    }
            );
        }
        return null;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference instanceof TsRadioPreference) {
            ((TsRadioPreference) preference).clearOtherRadioPreferences(getPreferenceScreen());
        }
        return super.onPreferenceTreeClick(preference);
    }
}

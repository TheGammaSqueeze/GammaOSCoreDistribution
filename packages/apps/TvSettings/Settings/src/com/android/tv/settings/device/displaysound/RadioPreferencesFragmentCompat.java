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

package com.android.tv.settings.device.displaysound;

import androidx.preference.Preference;

import com.android.tv.settings.compat.HasKeys;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;
import com.android.tv.settings.compat.RenderUtil;
import com.android.tv.settings.compat.TsPreferenceCategory;
import com.android.tv.settings.compat.TsRadioPreference;
import com.android.tv.settings.library.PreferenceCompat;

/**
 * The fragment compat for handling a full screen of radio buttons which belong to a single group.
 */
public abstract class RadioPreferencesFragmentCompat extends PreferenceControllerFragmentCompat {
    protected TsPreferenceCategory mPrefGroup;

    @Override
    public HasKeys updatePref(PreferenceCompat preferenceCompat) {
        if (preferenceCompat == null) {
            return null;
        }
        if (preferenceCompat.getChildPrefCompats() != null) {
            if (mPrefGroup == null) {
                HasKeys pref = RenderUtil.createPreference(getContext(),
                        preferenceCompat);
                if (pref instanceof TsPreferenceCategory) {
                    mPrefGroup = (TsPreferenceCategory) pref;
                    getPreferenceScreen().addPreference(mPrefGroup);
                }
            }

            preferenceCompat.getChildPrefCompats().stream().forEach(
                    childPrefCompat -> {
                        Preference preference = findTargetPreference(childPrefCompat.getKey());
                        if (preference == null) {
                            preference = (Preference) RenderUtil.createPreference(getContext(),
                                    childPrefCompat);
                            mPrefGroup.addPreference(preference);
                        }
                        if ((preference instanceof TsRadioPreference)) {
                            final TsRadioPreference radioPref =
                                    (TsRadioPreference) preference;
                            RenderUtil.updatePreference(
                                    getContext(), radioPref, childPrefCompat,
                                    preference.getOrder());
                            radioPref.setRadioGroup(preferenceCompat.getRadioGroup());
                        }
                    }
            );
        }
        return super.updatePref(preferenceCompat);
    }


    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference instanceof TsRadioPreference) {
            ((TsRadioPreference) preference).clearOtherRadioPreferences(mPrefGroup);
        }
        return super.onPreferenceTreeClick(preference);
    }
}

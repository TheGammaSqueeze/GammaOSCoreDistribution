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

import static com.android.tv.settings.library.PreferenceCompat.STATUS_OFF;
import static com.android.tv.settings.library.PreferenceCompat.STATUS_ON;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceGroup;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.HasKeys;
import com.android.tv.settings.compat.RenderUtil;
import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;

/**
 * The fragment compat for advanced volume screen in TV settings.
 */
@Keep
public class AdvancedVolumeFragmentCompat extends RadioPreferencesFragmentCompat {
    private static final String KEY_ADVANCED_SOUND_OPTION = "advanced_sound_settings_option";
    private static final String KEY_FORMAT_INFO = "surround_sound_format_info";
    private static final String KEY_FORMAT_INFO_ON_MANUAL = "surround_sound_format_info_on_manual";

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferencesFromResource(R.xml.advanced_volume_compat, null);
        mPrefGroup = findPreference(KEY_ADVANCED_SOUND_OPTION);
    }

    @Override
    public HasKeys updatePref(PreferenceCompat preferenceCompat) {
        if (preferenceCompat != null && preferenceCompat.getKey().length == 1) {
            String categoryKey = preferenceCompat.getKey()[0];
            switch (categoryKey) {
                case KEY_ADVANCED_SOUND_OPTION:
                    return super.updatePref(preferenceCompat);
                case KEY_FORMAT_INFO:
                case KEY_FORMAT_INFO_ON_MANUAL:
                    if (preferenceCompat.getVisible() == STATUS_OFF) {
                        findPreference(categoryKey).setVisible(false);
                    } else if (preferenceCompat.getVisible() == STATUS_ON) {
                        findPreference(categoryKey).setVisible(true);
                    }
                    RenderUtil.updatePreferenceGroup(
                            ((PreferenceGroup) findPreference(categoryKey)),
                            preferenceCompat.getChildPrefCompats());
                    return null;
                default:
                    return null;
            }
        }
        return super.updatePref(preferenceCompat);
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_ADVANCED_VOLUME;
    }
}

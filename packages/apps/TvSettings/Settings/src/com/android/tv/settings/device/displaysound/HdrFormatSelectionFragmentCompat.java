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
 * This Fragment compat is responsible for allowing the user enable or disable the Hdr types which
 * are supported by device.
 */
@Keep
public class HdrFormatSelectionFragmentCompat extends RadioPreferencesFragmentCompat {
    private static final String KEY_HDR_FORMAT_SELECTION_OPTION = "hdr_format_selection_option";
    private static final String KEY_FORMAT_INFO = "hdr_format_info";
    private static final String KEY_FORMAT_INFO_ON_MANUAL = "hdr_format_info_on_manual";

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferencesFromResource(R.xml.hdr_format_selection_compat, null);
        mPrefGroup = findPreference(KEY_HDR_FORMAT_SELECTION_OPTION);
    }

    @Override
    public HasKeys updatePref(PreferenceCompat preferenceCompat) {
        if (preferenceCompat != null && preferenceCompat.getKey().length == 1) {
            String categoryKey = preferenceCompat.getKey()[0];
            switch (categoryKey) {
                case KEY_HDR_FORMAT_SELECTION_OPTION:
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
        return null;
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_HDR_FORMAT_SELECTION;
    }
}

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

package com.android.tv.settings.device.apps;

import static com.android.tv.settings.library.ManagerUtil.STATE_ALL_APPS;

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
 * Fragment compat to handle all apps screen.
 */
@Keep
public class AllAppsFragmentCompat extends PreferenceControllerFragmentCompat {
    private PreferenceGroup mInstalledPreferenceGroup;
    private PreferenceGroup mDisabledPreferenceGroup;
    private PreferenceGroup mOtherPreferenceGroup;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager()
                .setPreferenceComparisonCallback(
                        new PreferenceManager.SimplePreferenceComparisonCallback());
        setPreferencesFromResource(R.xml.all_apps_compat, null);
        mInstalledPreferenceGroup = (PreferenceGroup) findPreference("InstalledPreferenceGroup");
        mDisabledPreferenceGroup = (PreferenceGroup) findPreference("DisabledPreferenceGroup");
        mOtherPreferenceGroup = (PreferenceGroup) findPreference("OtherPreferenceGroup");
        mOtherPreferenceGroup.setVisible(false);
    }

    @Override
    public HasKeys updatePref(PreferenceCompat prefParcelable) {
        HasKeys preference = super.updatePref(prefParcelable);
        if (preference == null) {
            return null;
        }
        switch (prefParcelable.getKey()[0]) {
            case "InstalledPreferenceGroup":
                RenderUtil.updatePreferenceGroup(
                        mInstalledPreferenceGroup, prefParcelable.getChildPrefCompats());
                break;
            case "DisabledPreferenceGroup":
                RenderUtil.updatePreferenceGroup(
                        mDisabledPreferenceGroup, prefParcelable.getChildPrefCompats());
                break;
            case "OtherPreferenceGroup":
                RenderUtil.updatePreferenceGroup(
                        mOtherPreferenceGroup, prefParcelable.getChildPrefCompats());
                break;
            default:
                // no-op
        }
        return preference;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_ALL_APPS;
    }
}

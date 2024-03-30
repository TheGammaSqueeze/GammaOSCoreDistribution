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

package com.android.tv.settings.privacy;

import static com.android.tv.settings.library.ManagerUtil.STATE_SENSOR;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.HasKeys;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;
import com.android.tv.settings.compat.RenderUtil;
import com.android.tv.settings.compat.TsCollapsibleCategory;
import com.android.tv.settings.library.PreferenceCompat;

/**
 * The fragment compat for the microphone/camera settings screen in TV settings.
 * Allows the user to turn of the respective sensor.
 */
@Keep
public class SensorFragmentCompat extends PreferenceControllerFragmentCompat {
    private static final String KEY_RECENT_REQUESTS = "recent_requests";
    private static final String KEY_COLLAPSE = "collapse";
    private Preference mCollapsePref;
    private Preference mRecentQuestsCategory;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferencesFromResource(R.xml.sensor_compat, null);
        mCollapsePref = findTargetPreference(new String[]{KEY_COLLAPSE});
        mRecentQuestsCategory = findTargetPreference(new String[]{KEY_RECENT_REQUESTS});
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCollapsePref();
    }

    @Override
    public HasKeys updatePref(PreferenceCompat prefCompat) {
        super.updatePref(prefCompat);
        if (prefCompat.getKey().length == 1
                && prefCompat.getKey()[0].equals(KEY_RECENT_REQUESTS)
                && prefCompat.getChildPrefCompats() != null) {
            RenderUtil.updatePreferenceGroup(
                    ((PreferenceGroup) findPreference(prefCompat.getKey()[0])),
                    prefCompat.getChildPrefCompats());
            updateCollapsePref();
        }
        return null;
    }

    private void updateCollapsePref() {
        // Once user click "See all", collapse preference should be hidden, this behavior is
        // different from "See all networks" in Wi-Fi settings screen.
        if (mCollapsePref != null) {
            mCollapsePref.setVisible(mRecentQuestsCategory instanceof TsCollapsibleCategory
                    && mRecentQuestsCategory.isVisible()
                    && ((TsCollapsibleCategory) mRecentQuestsCategory).shouldShowCollapsePref()
                    && ((TsCollapsibleCategory) mRecentQuestsCategory).isCollapsed());
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey() != null && preference.getKey().equals(KEY_COLLAPSE)) {
            final boolean collapse = !((TsCollapsibleCategory) mRecentQuestsCategory).isCollapsed();
            ((TsCollapsibleCategory) mRecentQuestsCategory).setCollapsed(collapse);
            mCollapsePref.setVisible(false);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public int getStateIdentifier() {
        return STATE_SENSOR;
    }
}

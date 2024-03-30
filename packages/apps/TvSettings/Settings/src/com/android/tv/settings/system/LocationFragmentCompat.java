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

import static com.android.tv.settings.library.ManagerUtil.STATE_LOCATION;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceGroup;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.HasKeys;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;
import com.android.tv.settings.compat.RenderUtil;
import com.android.tv.settings.library.PreferenceCompat;

/**
 * The fragment compat for location settings screen in TV settings.
 */
@Keep
public class LocationFragmentCompat extends PreferenceControllerFragmentCompat {
    private static final String KEY_RECENT_LOCATION_REQUESTS = "recent_location_requests";

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferencesFromResource(R.xml.location_compat, null);
    }

    @Override
    public HasKeys updatePref(PreferenceCompat prefCompat) {
        super.updatePref(prefCompat);
        if (prefCompat.getKey().length == 1
                && prefCompat.getKey()[0].equals(KEY_RECENT_LOCATION_REQUESTS)
                && prefCompat.getChildPrefCompats() != null) {
            RenderUtil.updatePreferenceGroup(
                    ((PreferenceGroup) findPreference(prefCompat.getKey()[0])),
                    prefCompat.getChildPrefCompats());
        }
        return null;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_LOCATION;
    }
}

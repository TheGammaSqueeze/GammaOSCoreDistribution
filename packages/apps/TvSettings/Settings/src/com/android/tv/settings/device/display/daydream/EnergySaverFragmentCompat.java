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

package com.android.tv.settings.device.display.daydream;

import static com.android.tv.settings.library.ManagerUtil.STATE_ENERGY_SAVER;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.Preference;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;

/**
 * The fragment compat for energy saver screen in TV settings.
 */
@Keep
public class EnergySaverFragmentCompat extends PreferenceControllerFragmentCompat {
    private static final String KEY_ALLOW_TURN_SCREEN_OFF = "allowTurnScreenOff";
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.energy_saver_compat, null);
        Preference preference = findPreference(KEY_ALLOW_TURN_SCREEN_OFF);
        preference.setVisible(showStandbyTimeout());
    }

    private boolean showStandbyTimeout() {
        return getResources().getBoolean(R.bool.config_show_standby_timeout);
    }

    @Override
    public int getStateIdentifier() {
        return STATE_ENERGY_SAVER;
    }
}

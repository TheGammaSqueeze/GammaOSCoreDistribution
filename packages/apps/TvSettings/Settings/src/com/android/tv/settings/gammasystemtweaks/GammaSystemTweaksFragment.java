/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings.gammasystemtweaks;

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.R;

/**
 * The "GammaOS Core System Tweaks" screen in TV settings.
 */
public class GammaSystemTweaksFragment extends SettingsPreferenceFragment {

    public static GammaSystemTweaksFragment newInstance() {
        return new GammaSystemTweaksFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the title of the fragment using the string resource
        getActivity().setTitle(R.string.gamma_system_tweaks_title);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Create a new PreferenceScreen programmatically
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getContext());
        setPreferenceScreen(screen);

        // Create a new preference item
        Preference helloWorldPreference = new Preference(getContext());
        helloWorldPreference.setTitle("Hello, World!");
        helloWorldPreference.setSummary("This is the only option available.");

        // Add the new preference to the screen
        screen.addPreference(helloWorldPreference);
    }
}

/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.overlay;

import android.app.AppGlobals;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.VpnManager;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.android.tv.settings.SettingsFragmentProvider;
import com.android.tv.settings.library.basic.BasicModeFeatureProvider;
import com.android.tv.settings.library.basic.BasicModeFeatureProviderImpl;
import com.android.tv.settings.library.enterprise.EnterprisePrivacyFeatureProvider;
import com.android.tv.settings.library.enterprise.EnterprisePrivacyFeatureProviderImpl;
import com.android.tv.settings.library.enterprise.apps.ApplicationFeatureProvider;
import com.android.tv.settings.library.enterprise.apps.ApplicationFeatureProviderImpl;
import com.android.tv.settings.library.help.SupportFeatureProvider;
import com.android.tv.settings.library.help.SupportFeatureProviderImpl;
import com.android.tv.settings.library.startup.startup.StartupVerificationFeatureProvider;
import com.android.tv.settings.library.startup.startup.StartupVerificationFeatureProviderImpl;
import com.android.tv.settings.system.LeanbackPickerDialogFragment;
import com.android.tv.settings.system.LeanbackPickerDialogPreference;
import com.android.tv.twopanelsettings.TwoPanelSettingsFragment;

/** Two panel customized implementation of the feature factory. */
@Keep
public class FeatureFactoryImplTwoPanel implements FeatureFactory {

    protected static final String TAG = "FeatureFactoryImplTwoP";

    @Override
    public SettingsFragmentProvider getSettingsFragmentProvider() {
        return SettingsFragment::newInstance;
    }
    /**
     * A settings fragment suitable for displaying in the two panel layout. Handles launching
     * fragments and dialogs in a reasonably generic way.
     */
    public static class SettingsFragment extends TwoPanelSettingsFragment {

        private static final String EXTRA_FRAGMENT_CLASS_NAME = "fragmentClassName";

        public SettingsFragment() {}

        /** Constructs a new instance of a settings fragment. */
        public static SettingsFragment newInstance(String className, Bundle arguments) {
            SettingsFragment fragment = new SettingsFragment();
            Bundle args = arguments == null ? new Bundle() : new Bundle(arguments);
            args.putString(EXTRA_FRAGMENT_CLASS_NAME, className);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public final boolean onPreferenceStartScreen(
                PreferenceFragmentCompat caller, PreferenceScreen pref) {
            return false;
        }

        @Override
        public Fragment onCreatePreviewFragment(@NonNull Fragment caller, Preference pref) {
            Fragment f;
            if (pref instanceof LeanbackPickerDialogPreference) {
                final LeanbackPickerDialogPreference dialogPreference =
                        (LeanbackPickerDialogPreference) pref;
                f = dialogPreference.getType().equals("date")
                        ? LeanbackPickerDialogFragment.newDatePickerInstance(pref.getKey())
                        : LeanbackPickerDialogFragment.newTimePickerInstance(pref.getKey());
                f.setTargetFragment(caller, 0);
                return f;
            } else {
                return super.onCreatePreviewFragment(caller, pref);
            }
        }

        @Override
        public void onPreferenceStartInitialScreen() {
            try {
                String className = getArguments().getString(EXTRA_FRAGMENT_CLASS_NAME);
                final Fragment fragment = (Fragment) Class.forName(className).newInstance();
                fragment.setArguments(getArguments());
                startPreferenceFragment(fragment);
            } catch (IllegalAccessException | ClassNotFoundException
                    | java.lang.InstantiationException e) {
                Log.e(TAG, "Unable to start initial preference screen.", e);
            }
        }
    }
}

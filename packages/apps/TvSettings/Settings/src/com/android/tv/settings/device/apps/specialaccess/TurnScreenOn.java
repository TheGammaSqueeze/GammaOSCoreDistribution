/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tv.settings.device.apps.specialaccess;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.tvsettings.TvSettingsEnums;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

/**
 * Fragment for managing which apps are allowed to turn the screen on
 */
@Keep
public class TurnScreenOn extends SettingsPreferenceFragment
        implements ManageApplicationsController.Callback {
    private static final String TAG = TurnScreenOn.class.getSimpleName();
    private static final boolean DEBUG = false;
    private ManageApplicationsController mManageApplicationsController;
    private AppOpsManager mAppOpsManager;

    private final ApplicationsState.AppFilter mFilter = new ApplicationsState.CompoundFilter(
            new ApplicationsState.CompoundFilter(
                    ApplicationsState.FILTER_WITHOUT_DISABLED_UNTIL_USED,
                    ApplicationsState.FILTER_ALL_ENABLED),
            new ApplicationsState.AppFilter() {
                @Override
                public void init() {
                }

                @Override
                public boolean filterApp(ApplicationsState.AppEntry info) {
                    info.extraInfo = mAppOpsManager.checkOpNoThrow(AppOpsManager.OP_TURN_SCREEN_ON,
                            info.info.uid, info.info.packageName) == AppOpsManager.MODE_ALLOWED;
                    return !ManageAppOp.shouldIgnorePackage(
                            getContext(),
                            info.info.packageName, /* customizedIgnoredPackagesArray= */ 0)
                            && !info.info.isPrivilegedApp()
                            && ActivityManager.getCurrentUser() == UserHandle.getUserId(
                            info.info.uid)
                            && checkPackageHasWakeLockPermission(info.info.packageName);
                }
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAppOpsManager = getContext().getSystemService(AppOpsManager.class);
        mManageApplicationsController = new ManageApplicationsController(getContext(), this,
                getLifecycle(), mFilter, ApplicationsState.ALPHA_COMPARATOR);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.turn_screen_on, null);
    }

    @Override
    public void onResume() {
        super.onResume();
        mManageApplicationsController.updateAppList();
    }

    private boolean checkPackageHasWakeLockPermission(String packageName) {
        return getContext().getPackageManager().checkPermission(Manifest.permission.WAKE_LOCK,
                packageName) == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    @Override
    public Preference bindPreference(@NonNull Preference preference,
            ApplicationsState.AppEntry entry) {
        final TwoStatePreference switchPref = (SwitchPreference) preference;
        switchPref.setTitle(entry.label);
        switchPref.setKey(entry.info.packageName);
        switchPref.setIcon(entry.icon);
        switchPref.setChecked((Boolean) entry.extraInfo);
        switchPref.setOnPreferenceChangeListener((pref, newValue) -> {
            int newMode =
                    (Boolean) newValue ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED;
            if (DEBUG) {
                Log.d(TAG, "setting OP_TURN_SCREEN_ON to " + newMode
                        + ", uid=" + entry.info.uid
                        + ", packageName=" + entry.info.packageName
                        + ", userId=" + UserHandle.getUserId(entry.info.uid)
                        + ", currentUser=" + ActivityManager.getCurrentUser());
            }
            mAppOpsManager.setMode(AppOpsManager.OP_TURN_SCREEN_ON,
                    entry.info.uid,
                    entry.info.packageName,
                    newMode);
            return true;
        });
        switchPref.setSummaryOn(R.string.app_permission_summary_allowed);
        switchPref.setSummaryOff(R.string.app_permission_summary_not_allowed);
        return switchPref;
    }

    @NonNull
    @Override
    public Preference createAppPreference() {
        return new SwitchPreference(getPreferenceManager().getContext());
    }

    @NonNull
    @Override
    public Preference getEmptyPreference() {
        final Preference empty = new Preference(getPreferenceManager().getContext());
        empty.setKey("empty");
        empty.setTitle(R.string.noApplications);
        empty.setEnabled(false);
        return empty;
    }

    @NonNull
    @Override
    public PreferenceGroup getAppPreferenceGroup() {
        return getPreferenceScreen();
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.APPS_SPECIAL_APP_ACCESS_TURN_SCREEN_ON;
    }
}

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

package com.android.tv.settings.library.device.apps.specialaccess;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.os.Bundle;
import android.util.ArrayMap;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.device.apps.ApplicationsState;
import com.android.tv.settings.library.util.ResourcesUtil;

/**
 * Settings state for managing apps which can write system settings
 */
public class WriteSettingsState extends ManageAppOpState {
    private AppOpsManager mAppOpsManager;
    private final ArrayMap<String, ApplicationsState.AppEntry> mAppEntryByKey = new ArrayMap<>();

    public WriteSettingsState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public int getAppOpsOpCode() {
        return AppOpsManager.OP_WRITE_SETTINGS;
    }

    @Override
    public String getPermission() {
        return Manifest.permission.WRITE_SETTINGS;
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_WRITE_SETTINGS;
    }

    @Override
    public PreferenceCompat createAppPreference(ApplicationsState.AppEntry entry) {
        final PreferenceCompat appPref = mPreferenceCompatManager
                .getOrCreatePrefCompat(entry.info.packageName);
        appPref.setTitle(entry.label);
        appPref.setIcon(entry.icon);

        appPref.setSummary(getPreferenceSummary(entry).toString());
        appPref.setChecked(((ManageAppOpState.PermissionState) entry.extraInfo).isAllowed());
        appPref.setType(PreferenceCompat.TYPE_SWITCH);
        appPref.setHasOnPreferenceChangeListener(true);
        mAppEntryByKey.put(appPref.getKey()[0], entry);
        return appPref;
    }

    private CharSequence getPreferenceSummary(ApplicationsState.AppEntry entry) {
        if (entry.extraInfo instanceof ManageAppOpState.PermissionState) {
            return ((ManageAppOpState.PermissionState) entry.extraInfo).isAllowed()
                    ? ResourcesUtil.getString(mContext, "write_settings_on")
                    : ResourcesUtil.getString(mContext, "write_settings_off");
        } else {
            return null;
        }
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        ApplicationsState.AppEntry appEntry = mAppEntryByKey.get(key);
        if (appEntry != null) {
            setWriteSettingsAccess(appEntry, (Boolean) newValue);
        }
        return true;
    }


    private void setWriteSettingsAccess(ApplicationsState.AppEntry entry, Boolean grant) {
        mAppOpsManager.setMode(AppOpsManager.OP_WRITE_SETTINGS,
                entry.info.uid, entry.info.packageName,
                grant ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED);
        updateAppList();
    }
}

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

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.device.apps.ApplicationsState;
import com.android.tv.settings.library.util.AbstractPreferenceController;

import java.util.List;

/**
 * State for managing which apps are granted PIP access
 */
public class PictureInPictureState extends PreferenceControllerState implements
        ManageApplicationsController.Callback {
    private static final String TAG = "PictureInPicture";

    private ManageApplicationsController mManageApplicationsController;
    private AppOpsManager mAppOpsManager;
    private final ArrayMap<String, ApplicationsState.AppEntry> mAppEntryByKey = new ArrayMap<>();

    private final ApplicationsState.AppFilter mFilter =
            new ApplicationsState.CompoundFilter(
                    new ApplicationsState.CompoundFilter(
                            ApplicationsState.FILTER_WITHOUT_DISABLED_UNTIL_USED,
                            ApplicationsState.FILTER_ALL_ENABLED),

                    new ApplicationsState.AppFilter() {
                        @Override
                        public void init() {
                        }

                        @Override
                        public boolean filterApp(ApplicationsState.AppEntry info) {
                            info.extraInfo = mAppOpsManager.checkOpNoThrow(
                                    AppOpsManager.OP_PICTURE_IN_PICTURE,
                                    info.info.uid,
                                    info.info.packageName) == AppOpsManager.MODE_ALLOWED;
                            return !ManageAppOpState.shouldIgnorePackage(
                                    mContext, info.info.packageName, 0)
                                    && checkPackageHasPipActivities(info.info.packageName);
                        }
                    });

    public PictureInPictureState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    private boolean checkPackageHasPipActivities(String packageName) {
        try {
            final PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                    packageName, PackageManager.GET_ACTIVITIES);
            if (packageInfo.activities == null) {
                return false;
            }
            for (ActivityInfo info : packageInfo.activities) {
                if (info.supportsPictureInPicture()) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Exception while fetching package info for " + packageName, e);
            return false;
        }
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        mManageApplicationsController = new ManageApplicationsController(mContext,
                getStateIdentifier(),
                getLifecycle(), mFilter, ApplicationsState.ALPHA_COMPARATOR, this,
                mUIUpdateCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
        mManageApplicationsController.updateAppList();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_PICTURE_IN_PICTURE;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }

    @Override
    public PreferenceCompat createAppPreference(ApplicationsState.AppEntry entry) {
        final PreferenceCompat appPref = mPreferenceCompatManager
                .getOrCreatePrefCompat(entry.info.packageName);
        appPref.setTitle(entry.label);
        appPref.setIcon(entry.icon);
        appPref.setChecked((Boolean) entry.extraInfo);
        mAppEntryByKey.put(appPref.getKey()[0], entry);
        appPref.setType(PreferenceCompat.TYPE_SWITCH);
        appPref.setHasOnPreferenceChangeListener(true);
        return appPref;
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        ApplicationsState.AppEntry appEntry = mAppEntryByKey.get(key);
        if (appEntry != null) {
            mAppOpsManager.setMode(AppOpsManager.OP_PICTURE_IN_PICTURE,
                    appEntry.info.uid,
                    appEntry.info.packageName,
                    (Boolean) newValue ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED);
        }
        return true;
    }


    @Override
    public PreferenceCompat getEmptyPreference() {
        return null;
    }
}

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

package com.android.tv.settings.library.device.apps;

import static com.android.tv.settings.library.device.apps.AppsState.EXTRA_VOLUME_NAME;
import static com.android.tv.settings.library.device.apps.AppsState.EXTRA_VOLUME_UUID;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AllAppsState extends PreferenceControllerState {
    static final String ARG_PACKAGE_NAME = "packageName";
    private static final String TAG = "AllAppsState";
    private static final String KEY_SHOW_OTHER_APPS = "ShowOtherApps";
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSessionInstalled;
    private ApplicationsState.AppFilter mFilterInstalled;
    private ApplicationsState.Session mSessionDisabled;
    private ApplicationsState.AppFilter mFilterDisabled;
    private ApplicationsState.Session mSessionOther;
    private ApplicationsState.AppFilter mFilterOther;
    private PreferenceCompat mInstalledPreferenceGroup;
    private PreferenceCompat mDisabledPreferenceGroup;
    private PreferenceCompat mOtherPreferenceGroup;
    private PreferenceCompat mShowOtherApps;
    private static final @ApplicationsState.SessionFlags
    int SESSION_FLAGS =
            ApplicationsState.FLAG_SESSION_REQUEST_HOME_APP
                    | ApplicationsState.FLAG_SESSION_REQUEST_ICONS
                    | ApplicationsState.FLAG_SESSION_REQUEST_SIZES
                    | ApplicationsState.FLAG_SESSION_REQUEST_LEANBACK_LAUNCHER;
    private final Map<PreferenceCompat,
            ArrayList<ApplicationsState.AppEntry>> mUpdateMap = new ArrayMap<>(3);
    private long mRunAt = Long.MIN_VALUE;
    private final Handler mHandler = new Handler();

    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            for (final PreferenceCompat group : mUpdateMap.keySet()) {
                final ArrayList<ApplicationsState.AppEntry> entries = mUpdateMap.get(group);
                updateAppListInternal(group, entries);
            }
            mUpdateMap.clear();
            mRunAt = 0;
        }
    };

    private static final ApplicationsState.AppFilter
            FILTER_INSTALLED = new ApplicationsState.AppFilter() {

        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(ApplicationsState.AppEntry info) {
            return !FILTER_DISABLED.filterApp(info)
                    && info.info != null
                    && info.info.enabled
                    && info.hasLauncherEntry
                    && info.launcherEntryEnabled;
        }
    };

    private static final ApplicationsState.AppFilter
            FILTER_DISABLED =
            new ApplicationsState.AppFilter() {

                @Override
                public void init() {
                }

                @Override
                public boolean filterApp(
                        ApplicationsState.AppEntry info) {
                    return info.info != null
                            && (info.info.enabledSetting
                            == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            || info.info.enabledSetting
                            == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                            || (info.info.enabledSetting
                            == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                            && !info.info.enabled));
                }
            };

    private static final ApplicationsState.AppFilter
            FILTER_OTHER = new ApplicationsState.AppFilter() {

        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(
                ApplicationsState.AppEntry info) {
            return !FILTER_INSTALLED.filterApp(info) && !FILTER_DISABLED.filterApp(info);
        }
    };

    public AllAppsState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }


    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mApplicationsState = ApplicationsState.getInstance(((Activity) mContext).getApplication());
        final String volumeUuid = extras.getString(EXTRA_VOLUME_UUID);
        final String volumeName = extras.getString(EXTRA_VOLUME_NAME);

        // The UUID of internal storage is null, so we check if there's a volume name to see if we
        // should only be showing the apps on the internal storage or all apps.
        if (!TextUtils.isEmpty(volumeUuid) || !TextUtils.isEmpty(volumeName)) {
            ApplicationsState.AppFilter volumeFilter = new ApplicationsState.VolumeFilter(
                    volumeUuid);

            mFilterInstalled = new ApplicationsState.CompoundFilter(FILTER_INSTALLED, volumeFilter);
            mFilterDisabled = new ApplicationsState.CompoundFilter(FILTER_DISABLED, volumeFilter);
            mFilterOther = new ApplicationsState.CompoundFilter(FILTER_OTHER, volumeFilter);
        } else {
            mFilterInstalled = FILTER_INSTALLED;
            mFilterDisabled = FILTER_DISABLED;
            mFilterOther = FILTER_OTHER;
        }
        mSessionInstalled = mApplicationsState.newSession(new RowUpdateCallbacks() {
            @Override
            protected void doRebuild() {
                rebuildInstalled();
            }

            @Override
            public void onRebuildComplete(
                    ArrayList<ApplicationsState.AppEntry> apps) {
                updateAppList(mInstalledPreferenceGroup, apps);
            }
        }, getLifecycle());
        mSessionInstalled.setSessionFlags(SESSION_FLAGS);

        mSessionDisabled = mApplicationsState.newSession(new RowUpdateCallbacks() {
            @Override
            protected void doRebuild() {
                rebuildDisabled();
            }

            @Override
            public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
                updateAppList(mDisabledPreferenceGroup, apps);
            }
        }, getLifecycle());
        mSessionDisabled.setSessionFlags(SESSION_FLAGS);

        mSessionOther = mApplicationsState.newSession(new RowUpdateCallbacks() {
            @Override
            protected void doRebuild() {
                if (!ManagerUtil.isVisible(mShowOtherApps)) {
                    rebuildOther();
                }
            }

            @Override
            public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
                updateAppList(mOtherPreferenceGroup, apps);
            }
        }, getLifecycle());
        mSessionOther.setSessionFlags(SESSION_FLAGS);

        rebuildInstalled();
        rebuildDisabled();
        mInstalledPreferenceGroup = mPreferenceCompatManager.getOrCreatePrefCompat(
                "InstalledPreferenceGroup");
        mDisabledPreferenceGroup = mPreferenceCompatManager.getOrCreatePrefCompat(
                "DisabledPreferenceGroup");
        mOtherPreferenceGroup = mPreferenceCompatManager.getOrCreatePrefCompat(
                "OtherPreferenceGroup");
        mOtherPreferenceGroup.setVisible(false);
        mShowOtherApps = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_SHOW_OTHER_APPS);
        mShowOtherApps.setVisible(TextUtils.isEmpty(volumeUuid));
    }

    private void rebuildInstalled() {
        ArrayList<ApplicationsState.AppEntry> apps =
                mSessionInstalled.rebuild(mFilterInstalled, ApplicationsState.ALPHA_COMPARATOR);
        if (apps != null) {
            updateAppList(mInstalledPreferenceGroup, apps);
        }
    }

    private void rebuildDisabled() {
        ArrayList<ApplicationsState.AppEntry> apps =
                mSessionDisabled.rebuild(mFilterDisabled, ApplicationsState.ALPHA_COMPARATOR);
        if (apps != null) {
            updateAppList(mDisabledPreferenceGroup, apps);
        }
    }

    private void rebuildOther() {
        ArrayList<ApplicationsState.AppEntry> apps =
                mSessionOther.rebuild(mFilterOther, ApplicationsState.ALPHA_COMPARATOR);
        if (apps != null) {
            updateAppList(mOtherPreferenceGroup, apps);
        }
    }

    private void updateAppList(PreferenceCompat group,
            ArrayList<ApplicationsState.AppEntry> entries) {
        if (group == null) {
            Log.d(TAG, "Not updating list for null group");
            return;
        }
        mUpdateMap.put(group, entries);

        // We can get spammed with updates, so coalesce them to reduce jank and flicker
        if (mRunAt == Long.MIN_VALUE) {
            // First run, no delay
            mHandler.removeCallbacks(mUpdateRunnable);
            mHandler.post(mUpdateRunnable);
        } else {
            if (mRunAt == 0) {
                mRunAt = SystemClock.uptimeMillis() + 1000;
            }
            int delay = (int) (mRunAt - SystemClock.uptimeMillis());
            delay = delay < 0 ? 0 : delay;

            mHandler.removeCallbacks(mUpdateRunnable);
            mHandler.postDelayed(mUpdateRunnable, delay);
        }
    }

    private void updateAppListInternal(PreferenceCompat group,
            ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null) {
            String[] key = group.getKey();
            group.initChildPreferences();
            for (final ApplicationsState.AppEntry entry : entries) {
                String packageName = entry.info.packageName;
                String[] entryKey = new String[key.length + 1];
                System.arraycopy(key, 0, entryKey, 0, key.length);
                entryKey[key.length] = packageName;
                PreferenceCompat entryPref = new PreferenceCompat(entryKey);
                group.addChildPrefCompat(entryPref);
                updatePreferenceParcelable(entryPref, entry);
            }
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), group);
        }
        mDisabledPreferenceGroup.setVisible(mDisabledPreferenceGroup.getChildPrefsCount() > 0);
    }

    /**
     * Update a PreferenceParcelable based upon {@link ApplicationsState.AppEntry}.
     */
    private PreferenceCompat updatePreferenceParcelable(
            PreferenceCompat preference, ApplicationsState.AppEntry entry) {
        entry.ensureLabel(mContext);
        preference.setTitle(entry.label);
        preference.setSummary(entry.sizeStr);
        preference.setNextState(ManagerUtil.STATE_APP_MANAGEMENT);
        Bundle nextStateExtras = new Bundle();
        AppManagementState.prepareArgs(nextStateExtras, entry.info.packageName);
        preference.setExtras(nextStateExtras);
        preference.setIcon(entry.icon);
        return preference;
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        super.onPreferenceTreeClick(key, status);
        if (KEY_SHOW_OTHER_APPS.equals(key[0])) {
            showOtherApps();
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }

    private void showOtherApps() {
        mShowOtherApps.setVisible(false);
        mOtherPreferenceGroup.setVisible(true);
        rebuildOther();
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_ALL_APPS;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }

    private abstract class RowUpdateCallbacks implements ApplicationsState.Callbacks {

        protected abstract void doRebuild();

        @Override
        public void onRunningStateChanged(boolean running) {
            doRebuild();
        }

        @Override
        public void onPackageListChanged() {
            doRebuild();
        }

        @Override
        public void onPackageIconChanged() {
            doRebuild();
        }

        @Override
        public void onPackageSizeChanged(String packageName) {
            doRebuild();
        }

        @Override
        public void onAllSizesComputed() {
            doRebuild();
        }

        @Override
        public void onLauncherInfoChanged() {
            doRebuild();
        }

        @Override
        public void onLoadEntriesCompleted() {
            doRebuild();
        }
    }

}

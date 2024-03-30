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

package com.android.tv.settings.library.device.display.daydream;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.settingslib.DreamBackend;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * State to provide data for rendering daydream settings screen.
 */
public class DaydreamState extends PreferenceControllerState {
    private static final String TAG = "DaydreamState";

    static final String KEY_ACTIVE_DREAM = "activeDream";
    static final String KEY_DREAM_TIME = "dreamTime";
    static final String KEY_DREAM_NOW = "dreamNow";

    private static final String DREAM_COMPONENT_NONE = "NONE";
    private static final String PACKAGE_SCHEME = "package";

    private static final int DEFAULT_DREAM_TIME_MS = (int) (30 * DateUtils.MINUTE_IN_MILLIS);

    private final PackageReceiver mPackageReceiver = new PackageReceiver();
    private DreamTimePC mDreamTimePC;
    private ActiveDreamPC mActiveDreamPC;
    private PreferenceCompat mDreamNowPref;

    private DreamBackend mBackend;
    private final Map<String, DreamBackend.DreamInfo> mDreamInfos = new ArrayMap<>();

    public DaydreamState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mBackend = new DreamBackend(mContext);
        refreshActiveDreamPref();
        if (mDreamTimePC != null) {
            mDreamTimePC.setValue(Integer.toString(getDreamTime()));
        }

        mDreamNowPref = mPreferenceCompatManager.getOrCreatePrefCompat(new String[]{KEY_DREAM_NOW});
        mDreamNowPref.setEnabled(mBackend.isEnabled());
    }


    private int getDreamTime() {
        return Settings.System.getInt(mContext.getContentResolver(), SCREEN_OFF_TIMEOUT,
                DEFAULT_DREAM_TIME_MS);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFromBackend();

        // listen for package changes
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme(PACKAGE_SCHEME);
        mContext.registerReceiver(mPackageReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        mContext.unregisterReceiver(mPackageReceiver);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_DAYDREAM;
    }

    private void refreshActiveDreamPref() {
        if (mActiveDreamPC == null) {
            return;
        }

        final List<DreamBackend.DreamInfo> infos = mBackend.getDreamInfos();
        final CharSequence[] dreamEntries = new CharSequence[infos.size() + 1];
        final CharSequence[] dreamEntryValues = new CharSequence[infos.size() + 1];
        refreshDreamInfoMap(infos, dreamEntries, dreamEntryValues);
        final ComponentName currentDreamComponent = mBackend.getActiveDream();

        mActiveDreamPC.setEntries(dreamEntries);
        mActiveDreamPC.setEntryValues(dreamEntryValues);
        mActiveDreamPC.setValue(mBackend.isEnabled() && currentDreamComponent != null
                ? currentDreamComponent.toShortString() : DREAM_COMPONENT_NONE);
        mActiveDreamPC.updateAndNotify();
    }

    private void refreshDreamInfoMap(List<DreamBackend.DreamInfo> infos,
            CharSequence[] listEntries, CharSequence[] listEntryValues) {
        mDreamInfos.clear();
        listEntries[0] = ResourcesUtil.getString(mContext, "device_daydreams_none");
        listEntryValues[0] = DREAM_COMPONENT_NONE;
        int index = 1;
        for (final DreamBackend.DreamInfo info : infos) {
            final String componentNameString = info.componentName.toShortString();
            mDreamInfos.put(componentNameString, info);
            listEntries[index] = info.caption;
            listEntryValues[index] = componentNameString;
            index++;
        }
    }

    private void refreshFromBackend() {
        if (mContext == null) {
            Log.d(TAG, "No activity, not refreshing");
            return;
        }

        refreshActiveDreamPref();
        if (mDreamTimePC != null) {
            mDreamTimePC.setValue(Integer.toString(getDreamTime()));
        }

        if (mDreamNowPref != null) {
            mDreamNowPref.setEnabled(mBackend.isEnabled());
        }
    }


    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        switch (key[0]) {
            case KEY_ACTIVE_DREAM:
                setActiveDream((String) newValue);
                return true;
            case KEY_DREAM_TIME:
                final int sleepTimeout = Integer.parseInt((String) newValue);
                setDreamTime(sleepTimeout);
                return true;
        }
        return super.onPreferenceChange(key, newValue);
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        switch (key[0]) {
            case KEY_DREAM_NOW:
                mBackend.startDreaming();
                return true;
            default:
                return super.onPreferenceTreeClick(key, status);
        }
    }

    private void setActiveDream(String componentNameString) {
        final DreamBackend.DreamInfo dreamInfo = mDreamInfos.get(componentNameString);
        if (dreamInfo != null) {
            if (dreamInfo.settingsComponentName != null) {
                mContext.startActivity(new Intent().setComponent(dreamInfo.settingsComponentName));
            }
            if (!mBackend.isEnabled()) {
                mBackend.setEnabled(true);
            }
            if (!Objects.equals(mBackend.getActiveDream(), dreamInfo.componentName)) {
                mBackend.setActiveDream(dreamInfo.componentName);
            }
        } else {
            if (mBackend.isEnabled()) {
                mBackend.setActiveDream(null);
                mBackend.setEnabled(false);
            }
        }
    }

    private void setDreamTime(int ms) {
        Settings.System.putInt(mContext.getContentResolver(), SCREEN_OFF_TIMEOUT, ms);
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        List<AbstractPreferenceController> preferenceControllers = new ArrayList<>();
        mDreamTimePC = new DreamTimePC(mContext, mUIUpdateCallback, getStateIdentifier(),
                mPreferenceCompatManager);
        mActiveDreamPC = new ActiveDreamPC(mContext, mUIUpdateCallback, getStateIdentifier(),
                mPreferenceCompatManager);
        preferenceControllers.add(mActiveDreamPC);
        preferenceControllers.add(mDreamTimePC);
        return preferenceControllers;
    }

    private class PackageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshFromBackend();
        }
    }
}

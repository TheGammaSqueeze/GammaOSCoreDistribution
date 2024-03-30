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

package com.android.tv.settings.library.system;

import static com.android.tv.settings.library.ManagerUtil.STATE_LANGUAGE;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.app.LocalePicker;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.about.DevelopmentSettingsEnabler;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * State to handle the business logic for LanguageFragment.
 */
public class LanguageState extends PreferenceControllerState {
    private static final String TAG = "LanguageFragment";

    // Pseudo locales used for internal purposes only should not be shown in the
    // language picker.
    private static final String PSEUDO_LOCALE_EN_XC = "en-XC";

    private static final String LANGUAGE_RADIO_GROUP = "language";

    private final Map<String, LocalePicker.LocaleInfo> mLocaleInfoMap = new ArrayMap<>();

    // Adjust this value to keep things relatively responsive without janking animations
    private static final int LANGUAGE_SET_DELAY_MS = 500;
    private final Handler mDelayHandler = new Handler();
    private Locale mNewLocale;
    private final Runnable mSetLanguageRunnable = new Runnable() {
        @Override
        public void run() {
            LocalePicker.updateLocale(mNewLocale);
        }
    };

    public LanguageState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        Locale currentLocale = null;
        try {
            currentLocale = ActivityManager.getService().getConfiguration()
                    .getLocales().get(0);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not retrieve locale", e);
        }

        final List<LocalePicker.LocaleInfo> localeInfoList =
                LocalePicker.getAllAssetLocales(mContext,
                        DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(mContext));

        PreferenceCompat languageList = mPreferenceCompatManager.getOrCreatePrefCompat(
                LANGUAGE_RADIO_GROUP);
        for (final LocalePicker.LocaleInfo localeInfo : localeInfoList) {
            final String languageTag = localeInfo.getLocale().toLanguageTag();
            if (PSEUDO_LOCALE_EN_XC.equals(languageTag)) {
                continue;
            }
            mLocaleInfoMap.put(languageTag, localeInfo);

            final PreferenceCompat radioPreference = mPreferenceCompatManager.getOrCreatePrefCompat(
                    new String[]{LANGUAGE_RADIO_GROUP, languageTag});
            radioPreference.setType(PreferenceCompat.TYPE_RADIO);
            radioPreference.setTitle(localeInfo.getLabel());
            if (localeInfo.getLocale().equals(currentLocale)) {
                radioPreference.setChecked(true);
                radioPreference.setFocused(true);
            } else {
                radioPreference.setChecked(false);
                radioPreference.setFocused(false);
            }
            languageList.addChildPrefCompat(radioPreference);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), languageList);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }

    @Override
    public int getStateIdentifier() {
        return STATE_LANGUAGE;
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        if (key.length < 2) {
            return false;
        }
        PreferenceCompat preferenceCompat = mPreferenceCompatManager.getPrefCompat(key);
        PreferenceCompat languageList = mPreferenceCompatManager
                .getPrefCompat(new String[]{LANGUAGE_RADIO_GROUP});
        if (preferenceCompat.getChecked() == PreferenceCompat.STATUS_OFF) {
            mNewLocale = mLocaleInfoMap.get(preferenceCompat.getKey()[1]).getLocale();
            mDelayHandler.removeCallbacks(mSetLanguageRunnable);
            mDelayHandler.postDelayed(mSetLanguageRunnable, LANGUAGE_SET_DELAY_MS);
            preferenceCompat.setChecked(true);
            clearOtherRadioPreferences(preferenceCompat, languageList);
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), languageList);
        }
        return true;
    }

    public PreferenceCompat clearOtherRadioPreferences(PreferenceCompat checkedPrefCompat,
            PreferenceCompat languageList) {
        languageList.getChildPrefCompats().stream().filter(preferenceCompat ->
                !keyEquals(preferenceCompat.getKey(), checkedPrefCompat.getKey())).forEach(
                preferenceCompat -> {
                    preferenceCompat.setChecked(false);
                }
        );
        return languageList;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}

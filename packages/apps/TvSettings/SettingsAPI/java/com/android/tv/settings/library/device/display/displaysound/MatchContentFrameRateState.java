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

package com.android.tv.settings.library.device.display.displaysound;


import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.List;

/**
 * State to provide data for rendering match content frame rate screen.
 */
public class MatchContentFrameRateState extends PreferenceControllerState {
    private static final String KEY_MATCH_CONTENT_FRAME_RATE = "match_content_frame_rate_option";

    private static final String KEY_MATCH_CONTENT_FRAME_RATE_SEAMLESS =
            "match_content_frame_rate_seamless";
    private static final String KEY_MATCH_CONTENT_FRAME_RATE_NON_SEAMLESS =
            "match_content_frame_rate_non_seamless";
    private static final String KEY_MATCH_CONTENT_FRAME_RATE_NEVER =
            "match_content_frame_rate_never";
    private PreferenceCompat mPrefGroup;
    private PreferenceCompat mCurrentPreference;
    private PreferenceCompat mNeverPref;
    private PreferenceCompat mSeamlessPref;
    private PreferenceCompat mNonSeamlessPref;

    public MatchContentFrameRateState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_MATCH_CONTENT_FRAME;
    }

    private PreferenceCompat getCurrentPreference() {
        int matchContentSetting = getCurrentSettingValue();
        switch (matchContentSetting) {
            case (Settings.Secure.MATCH_CONTENT_FRAMERATE_NEVER): {
                return mNeverPref;
            }
            case (Settings.Secure.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY): {
                return mSeamlessPref;
            }
            case (Settings.Secure.MATCH_CONTENT_FRAMERATE_ALWAYS): {
                return mNonSeamlessPref;
            }
            default:
                throw new IllegalArgumentException("Unknown match content frame rate pref "
                        + "value in stored settings");
        }
    }

    private int getCurrentSettingValue() {
        return Settings.Secure.getInt(
                mContext.getContentResolver(),
                Settings.Secure.MATCH_CONTENT_FRAME_RATE,
                Settings.Secure.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY);
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        int newValue;
        switch (key[1]) {
            case KEY_MATCH_CONTENT_FRAME_RATE_SEAMLESS: {
                newValue = Settings.Secure.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY;
                break;
            }
            case KEY_MATCH_CONTENT_FRAME_RATE_NON_SEAMLESS: {
                newValue = Settings.Secure.MATCH_CONTENT_FRAMERATE_ALWAYS;
                break;
            }
            case KEY_MATCH_CONTENT_FRAME_RATE_NEVER: {
                newValue = Settings.Secure.MATCH_CONTENT_FRAMERATE_NEVER;
                break;
            }
            default:
                return false;
        }
        int oldValue = getCurrentSettingValue();
        if (newValue != oldValue) {
            Settings.Secure.putInt(
                    mContext.getContentResolver(),
                    Settings.Secure.MATCH_CONTENT_FRAME_RATE,
                    newValue);
        }
        return true;
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mPrefGroup = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_MATCH_CONTENT_FRAME_RATE);
        mPrefGroup.setType(PreferenceCompat.TYPE_PREFERENCE_CATEGORY);
        mSeamlessPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                new String[]{KEY_MATCH_CONTENT_FRAME_RATE, KEY_MATCH_CONTENT_FRAME_RATE_SEAMLESS});
        mSeamlessPref.setType(PreferenceCompat.TYPE_RADIO);
        mSeamlessPref.setTitle(
                ResourcesUtil.getString(mContext, "match_content_frame_rate_seamless"));
        mSeamlessPref.setRadioGroup(KEY_MATCH_CONTENT_FRAME_RATE);

        mNonSeamlessPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                new String[]{KEY_MATCH_CONTENT_FRAME_RATE,
                        KEY_MATCH_CONTENT_FRAME_RATE_NON_SEAMLESS});
        mNonSeamlessPref.setType(PreferenceCompat.TYPE_RADIO);
        mNonSeamlessPref.setTitle(ResourcesUtil.getString(mContext,
                "match_content_frame_rate_non_seamless"));
        mNonSeamlessPref.setRadioGroup(KEY_MATCH_CONTENT_FRAME_RATE);

        mNeverPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                new String[]{KEY_MATCH_CONTENT_FRAME_RATE, KEY_MATCH_CONTENT_FRAME_RATE_NEVER});
        mNeverPref.setType(PreferenceCompat.TYPE_RADIO);
        mNeverPref.setTitle(ResourcesUtil.getString(mContext, "match_content_frame_rate_never"));
        mNeverPref.setRadioGroup(KEY_MATCH_CONTENT_FRAME_RATE);

        mPrefGroup.addChildPrefCompat(mSeamlessPref);
        mPrefGroup.addChildPrefCompat(mNonSeamlessPref);
        mPrefGroup.addChildPrefCompat(mNeverPref);

        mCurrentPreference = getCurrentPreference();
        mCurrentPreference.setChecked(true);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mPrefGroup);
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}

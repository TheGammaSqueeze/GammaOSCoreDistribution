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

import android.content.ContentResolver;
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
 * State to provide data for rendering font scale settings screen.
 */
public class FontScaleState extends PreferenceControllerState {
    private static final String FONT_SCALE_GROUP = "font_scale_radio_group";
    private PreferenceCompat mFontScaleGroup;

    /** Value of FONT_SCALE. */
    private float mCurrentFontScaleValue;

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mFontScaleGroup = mPreferenceCompatManager.getOrCreatePrefCompat(FONT_SCALE_GROUP);
        mFontScaleGroup.setType(PreferenceCompat.TYPE_PREFERENCE_CATEGORY);
        mFontScaleGroup.setTitle(FONT_SCALE_GROUP);
        final String[] entryValues = ResourcesUtil.getStringArray(mContext,
                "font_scale_entry_values");
        final String[] entries = ResourcesUtil.getStringArray(mContext, "font_scale_entries");
        initFontScaleValue();

        for (int i = 0; i < entryValues.length; i++) {
            final PreferenceCompat preference = mPreferenceCompatManager.getOrCreatePrefCompat(
                    new String[]{FONT_SCALE_GROUP, entryValues[i]});
            preference.setPersistent(false);
            preference.setType(PreferenceCompat.TYPE_RADIO);
            preference.setRadioGroup(FONT_SCALE_GROUP);
            int scaleValue = (int) (Float.valueOf(entryValues[i]) * 100);
            String summary = ResourcesUtil.getString(
                    mContext, "font_scale_item_detail", scaleValue);
            preference.setSummary(summary);
            preference.setTitle(entries[i]);
            Bundle b = new Bundle();
            b.putString(ManagerUtil.INFO_PREVIEW_FONT_SCALE_VALUE, entryValues[i]);
            extras.putFloat(
                    ManagerUtil.INFO_CURRENT_FONT_SCALE_VALUE, mCurrentFontScaleValue);

            if (Float.compare(mCurrentFontScaleValue, Float.parseFloat(entryValues[i])) == 0) {
                preference.setChecked(true);
            }
            mFontScaleGroup.addChildPrefCompat(preference);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mFontScaleGroup);
    }


    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        if (key.length != 2) {
            return false;
        }
        mCurrentFontScaleValue = Float.parseFloat(key[1]);
        commit();
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mFontScaleGroup);
        return true;
    }

    protected void commit() {
        if (mContext == null) return;
        final ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putFloat(resolver, Settings.System.FONT_SCALE, mCurrentFontScaleValue);
    }

    private void initFontScaleValue() {
        final ContentResolver resolver = mContext.getContentResolver();
        mCurrentFontScaleValue =
                Settings.System.getFloat(resolver, Settings.System.FONT_SCALE, 1.0f);
    }

    public FontScaleState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_FONT_SCALE;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}

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

package com.android.tv.settings.compat;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

import java.util.Arrays;

public class TsDialogPreference extends DialogPreference implements HasKeys {
    private String[] mKeys;

    public TsDialogPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mKeys = getDefaultPreferenceKeys();
    }

    public TsDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mKeys = getDefaultPreferenceKeys();
    }

    public TsDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mKeys = getDefaultPreferenceKeys();
    }

    public TsDialogPreference(Context context) {
        super(context);
        mKeys = getDefaultPreferenceKeys();
    }

    public TsDialogPreference(Context context, String[] keys) {
        super(context);
        setKeys(keys);
    }

    @Override
    public void setKeys(String[] keys) {
        mKeys = Arrays.copyOf(keys, keys.length);
        if (keys.length != 0) {
            setKey(keys[keys.length - 1]);
        }
    }

    @Override
    public String[] getKeys() {
        return mKeys;
    }

    private String[] getDefaultPreferenceKeys() {
        return new String[]{ getKey() };
    }
}

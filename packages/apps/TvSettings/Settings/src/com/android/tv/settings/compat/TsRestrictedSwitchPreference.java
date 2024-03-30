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

import androidx.core.content.res.TypedArrayUtils;

import com.android.settingslib.R;
import com.android.settingslib.RestrictedSwitchPreference;

import java.util.Arrays;

/** RestrictedSwitchPreference for TV to provide extra functionality. */
public class TsRestrictedSwitchPreference extends RestrictedSwitchPreference implements HasKeys {
    private boolean mDisabledByAdmin;
    private String[] mKeys;

    public TsRestrictedSwitchPreference(Context context,
            AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mKeys = new String[]{getKey()};
    }

    public TsRestrictedSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TsRestrictedSwitchPreference(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context, R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
    }

    public TsRestrictedSwitchPreference(Context context) {
        this(context, null);
    }

    public TsRestrictedSwitchPreference(String[] key, Context context) {
        this(context, null);
        setKeys(key);
    }

    @Override
    public boolean isDisabledByAdmin() {
        return mDisabledByAdmin;
    }

    public void setDisabledByAdmin(boolean disabledByAdmin) {
        mDisabledByAdmin = disabledByAdmin;
    }

    @Override
    public void setKeys(String[] keys) {
        mKeys = Arrays.copyOf(keys, keys.length);
    }

    @Override
    public String[] getKeys() {
        return mKeys;
    }
}

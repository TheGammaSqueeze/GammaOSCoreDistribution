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

import androidx.preference.Preference;

import java.util.Arrays;

/** Preference for TV to provide extra functionality. */
public class TsPreference extends Preference implements HasKeys {
    private String[] mKeys;

    public TsPreference(Context context, AttributeSet attributeSet, int i, int i1) {
        super(context, attributeSet, i, i1);
        mKeys = new String[]{getKey()};
    }

    public TsPreference(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        mKeys = new String[]{getKey()};
    }

    public TsPreference(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mKeys = new String[]{getKey()};
    }

    public TsPreference(Context context) {
        super(context);
        mKeys = new String[]{getKey()};
    }

    public TsPreference(Context context, String[] keys) {
        super(context);
        setKeys(keys);
    }

    @Override
    public void setKeys(String[] keys) {
        this.mKeys = Arrays.copyOf(keys, keys.length);
        if (keys.length != 0) {
            setKey(keys[keys.length - 1]);
        }
    }

    @Override
    public String[] getKeys() {
        return mKeys;
    }
}


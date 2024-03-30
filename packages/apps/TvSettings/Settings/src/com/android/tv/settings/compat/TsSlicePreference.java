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

import com.android.tv.settings.library.util.SliceUtils;
import com.android.tv.twopanelsettings.slices.SlicePreference;

public class TsSlicePreference extends SlicePreference implements HasKeys {
    private String[] mKeys;
    public TsSlicePreference(Context context, String[] keys, String uri) {
        super(context);
        mKeys = keys;
        setFragment(SliceUtils.PATH_SLICE_FRAGMENT);
        setUri(uri);
    }

    public TsSlicePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
        setKeys(new String[]{getKey()});
    }

    @Override
    public void setKeys(String[] keys) {
        mKeys = keys;
    }

    @Override
    public String[] getKeys() {
        return mKeys;
    }
}

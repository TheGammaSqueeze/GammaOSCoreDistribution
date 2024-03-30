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

/** A compat preference to show Wi-Fi access point information. */
public class TsAccessPointPreference extends TsPreference {
    public TsAccessPointPreference(Context context, AttributeSet attributeSet, int i, int i1) {
        super(context, attributeSet, i, i1);
    }

    public TsAccessPointPreference(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
    }

    public TsAccessPointPreference(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public TsAccessPointPreference(Context context) {
        super(context);
    }

    public TsAccessPointPreference(Context context, String[] keys) {
        super(context, keys);
        setKeys(keys);
    }
}

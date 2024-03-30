/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.safetycenter.ui;

import android.content.Context;
import android.safetycenter.SafetyCenterStaticEntry;
import android.util.Log;

import androidx.preference.Preference;

/** A preference which displays a visual representation of a {@link SafetyCenterStaticEntry}. */
public class StaticSafetyEntryPreference extends Preference {

    private static final String TAG = StaticSafetyEntryPreference.class.getSimpleName();

    public StaticSafetyEntryPreference(Context context, SafetyCenterStaticEntry entry) {
        super(context);
        setTitle(entry.getTitle());
        setSummary(entry.getSummary());
        if (entry.getPendingIntent() != null) {
            setOnPreferenceClickListener(unused -> {
                try {
                    entry.getPendingIntent().send();
                } catch (Exception ex) {
                    Log.e(TAG,
                            String.format(
                                    "Failed to execute pending intent for static entry: %s", entry),
                            ex);
                }
                return true;
            });
        }
    }
}

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
import android.safetycenter.SafetyCenterEntry;
import android.util.Log;

import androidx.preference.Preference;

/** A preference that displays a visual representation of a {@link SafetyCenterEntry}. */
public class SafetyEntryPreference extends Preference {

    private static final String TAG = SafetyEntryPreference.class.getSimpleName();

    public SafetyEntryPreference(Context context, SafetyCenterEntry entry) {
        super(context);
        setTitle(entry.getTitle());
        setSummary(entry.getSummary());
        setIcon(toSeverityLevel(entry.getSeverityLevel()).getEntryIconResId());
        if (entry.getPendingIntent() != null) {
            setOnPreferenceClickListener(unused -> {
                try {
                    entry.getPendingIntent().send();
                } catch (Exception ex) {
                    Log.e(TAG,
                            String.format("Failed to execute pending intent for entry: %s", entry),
                            ex);
                }
                return true;
            });
        }
    }

    private static SeverityLevel toSeverityLevel(int entrySeverityLevel) {
        switch (entrySeverityLevel) {
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN:
                return SeverityLevel.SEVERITY_LEVEL_UNKNOWN;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED:
                return SeverityLevel.NONE;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK:
                return SeverityLevel.INFORMATION;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION:
                return SeverityLevel.RECOMMENDATION;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING:
                return SeverityLevel.CRITICAL_WARNING;
        }
        throw new IllegalArgumentException(
                String.format("Unexpected SafetyCenterEntry.EntrySeverityLevel: %s",
                        entrySeverityLevel));
    }
}

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

package com.android.tv.settings.system;

import static com.android.tv.settings.library.ManagerUtil.STATE_SYSTEM_DATE_TIME;

import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserManager;

import androidx.annotation.Keep;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.android.settingslib.datetime.ZoneGetter;
import com.android.tv.settings.R;
import com.android.tv.settings.RestrictedPreferenceAdapter;
import com.android.tv.settings.compat.HasKeys;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.system.DateTimeState;

import java.util.Calendar;

/**
 * The date and time screen in TV settings.
 */
@Keep
public class DateTimeFragmentCompat extends PreferenceControllerFragmentCompat {
    private static final String KEY_AUTO_DATE_TIME = "auto_date_time";
    private static final String KEY_SET_DATE = "set_date";
    private static final String KEY_SET_TIME = "set_time";
    private static final String KEY_SET_TIME_ZONE = "set_time_zone";
    private static final String KEY_USE_24_HOUR = "use_24_hour";

    @Override
    public int getStateIdentifier() {
        return STATE_SYSTEM_DATE_TIME;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager()
                .setPreferenceComparisonCallback(
                        new PreferenceManager.SimplePreferenceComparisonCallback());
        setPreferencesFromResource(R.xml.date_time_compat, null);

        Preference datePref = findPreference(KEY_SET_DATE);
        Preference timePref = findPreference(KEY_SET_TIME);
        final boolean tsTimeCapable = SystemProperties.getBoolean("ro.config.ts.date.time", false);
        final ListPreference autoDateTimePref =
                (ListPreference) findPreference(KEY_AUTO_DATE_TIME);
        autoDateTimePref.setOnPreferenceChangeListener(this);
        if (tsTimeCapable) {
            autoDateTimePref.setEntries(R.array.auto_date_time_ts_entries);
            autoDateTimePref.setEntryValues(R.array.auto_date_time_ts_entry_values);
        }

        Preference time24Pref = findPreference(KEY_USE_24_HOUR);
        time24Pref.setOnPreferenceChangeListener(this);

        Preference timeZonePref = findPreference(KEY_SET_TIME_ZONE);
        final String userRestriction = UserManager.DISALLOW_CONFIG_DATE_TIME;
        RestrictedPreferenceAdapter.adapt(datePref, userRestriction);
        RestrictedPreferenceAdapter.adapt(timePref, userRestriction);
        RestrictedPreferenceAdapter.adapt(timeZonePref, userRestriction);
        RestrictedPreferenceAdapter.adapt(autoDateTimePref, userRestriction);
    }

    @Override
    public HasKeys updatePref(PreferenceCompat prefCompat) {
        HasKeys preference = super.updatePref(prefCompat);
        if (preference == null) {
            return null;
        }
        String[] key = preference.getKeys();
        final Calendar now = getDateTimeState().getNow();
        switch (key[0]) {
            case KEY_SET_TIME_ZONE:
                ((Preference) preference).setSummary(
                        ZoneGetter.getTimeZoneOffsetAndName(getActivity(),
                                now.getTimeZone(), now.getTime()).toString());
                break;
            default:
        }
        return preference;
    }

    private DateTimeState getDateTimeState() {
        return (DateTimeState) super.getState();
    }
}

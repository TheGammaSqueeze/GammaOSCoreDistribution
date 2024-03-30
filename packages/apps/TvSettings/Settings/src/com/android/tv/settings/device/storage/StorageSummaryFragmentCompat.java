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

package com.android.tv.settings.device.storage;

import android.os.Bundle;

import androidx.annotation.Keep;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.HasKeys;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;
import com.android.tv.settings.compat.RenderUtil;
import com.android.tv.settings.compat.TsPreferenceCategory;
import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;

/**
 * Storage fragment compat for sotrage summary settings.
 */
@Keep
public class StorageSummaryFragmentCompat extends PreferenceControllerFragmentCompat {
    private static final String KEY_DEVICE_STORAGE = "device_storage";
    private static final String KEY_REMOVABLE_STORAGE = "removable_storage";
    private TsPreferenceCategory mDeviceStorageCategory;
    private TsPreferenceCategory mRemovableStorageCategory;

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_STORAGE_SUMMARY;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.storage_summary_compat, null);
        mDeviceStorageCategory = findPreference(KEY_DEVICE_STORAGE);
        mRemovableStorageCategory = findPreference(KEY_REMOVABLE_STORAGE);
    }


    @Override
    public HasKeys updatePref(PreferenceCompat prefCompat) {
        if (prefCompat.getKey() == null || prefCompat.getKey().length != 1) {
            return super.updatePref(prefCompat);
        }
        if (KEY_DEVICE_STORAGE.equals(prefCompat.getKey()[0])) {
            RenderUtil.updatePreferenceGroup(
                    mDeviceStorageCategory, prefCompat.getChildPrefCompats());
            return (HasKeys) mDeviceStorageCategory;
        } else if (KEY_REMOVABLE_STORAGE.equals(prefCompat.getKey()[0])) {
            RenderUtil.updatePreferenceGroup(
                    mRemovableStorageCategory, prefCompat.getChildPrefCompats());
            return (HasKeys) mRemovableStorageCategory;

        }
        return super.updatePref(prefCompat);
    }
}

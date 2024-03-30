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

package com.android.tv.settings.inputmethod;

import static com.android.tv.settings.library.ManagerUtil.STATE_KEYBOARD;

import android.os.Bundle;

import androidx.annotation.Keep;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.HasKeys;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;
import com.android.tv.settings.compat.RenderUtil;
import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;

/**
 * Fragment compat for managing IMES and Autofills.
 */
@Keep
public class KeyboardFragmentCompat extends PreferenceControllerFragmentCompat {
    // Order of input methods, make sure they are inserted between 1 (currentKeyboard) and
    // 3 (manageKeyboards).
    private static final int INPUT_METHOD_PREFERENCE_ORDER = 2;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.keyboard_compat, null);
    }

    @Override
    public HasKeys updatePref(PreferenceCompat prefCompat) {
        if (prefCompat.getKey() != null && prefCompat.getKey().length > 0
                && ManagerUtil.KEY_KEYBOARD_SETTINGS.equals(prefCompat.getKey()[0])) {
            RenderUtil.updatePreferenceGroup(
                    getPreferenceScreen(), prefCompat.getChildPrefCompats(),
                    INPUT_METHOD_PREFERENCE_ORDER);
            return null;
        }
        return super.updatePref(prefCompat);
    }

    @Override
    public int getStateIdentifier() {
        return STATE_KEYBOARD;
    }
}

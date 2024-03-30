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

package com.android.tv.settings.privacy;

import static com.android.tv.settings.library.ManagerUtil.STATE_PRIVACY;

import android.os.Bundle;

import androidx.annotation.Keep;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.PreferenceControllerFragmentCompat;

/**
 * The fragment compat for privacy policies screen in Settings.
 */
@Keep
public class PrivacyFragmentCompat extends PreferenceControllerFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferencesFromResource(R.xml.privacy_compat, null);
    }

    @Override
    public int getStateIdentifier() {
        return STATE_PRIVACY;
    }
}

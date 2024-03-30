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

package com.android.tv.settings.library.device.display.daydream;

import static com.android.tv.settings.library.device.display.daydream.DaydreamState.KEY_ACTIVE_DREAM;

import android.content.Context;
import android.os.UserManager;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.RestrictedPreferenceController;

public class ActiveDreamPC extends RestrictedPreferenceController {
    public ActiveDreamPC(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_ACTIVE_DREAM};
    }

    @Override
    public void update() {
        mPreferenceCompat.setHasOnPreferenceChangeListener(true);
        mPreferenceCompat.setType(PreferenceCompat.TYPE_LIST);
        UserManager userManager = UserManager.get(mContext);
        if (userManager.hasUserRestriction(getAttrUserRestriction())) {
            mPreferenceCompat.setEnabled(false);
        }
        super.update();
    }

    @Override
    public boolean useAdminDisabledSummary() {
        return false;
    }

    void setEntries(CharSequence[] dreamEntries) {
        mPreferenceCompat.setEntries(dreamEntries);
    }

    void setEntryValues(CharSequence[] dreamEntryValues) {
        mPreferenceCompat.setEntryValues(dreamEntryValues);
    }

    void setValue(String value) {
        mPreferenceCompat.setValue(value);
    }

    @Override
    public String getAttrUserRestriction() {
        return UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT;
    }
}

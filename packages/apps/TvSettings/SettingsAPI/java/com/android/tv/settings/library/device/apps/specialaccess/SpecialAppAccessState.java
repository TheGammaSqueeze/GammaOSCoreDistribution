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

package com.android.tv.settings.library.device.apps.specialaccess;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;

import java.util.List;

/** {@link State} to handle special access screen. */
public class SpecialAppAccessState extends PreferenceControllerState {
    private static final String KEY_FEATURE_PIP = "picture_in_picture";
    static final String KEY_FEATURE_NOTIFICATION_ACCESS = "notification_access";
    private static final String[] DISABLED_FEATURES_LOW_RAM_TV =
            new String[]{KEY_FEATURE_PIP, KEY_FEATURE_NOTIFICATION_ACCESS};

    public SpecialAppAccessState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onResume() {
        updatePreferenceStates();
    }

    protected void updatePreferenceStates() {
        ActivityManager activityManager = (ActivityManager) mContext
                .getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager.isLowRamDevice()) {
            for (String disabledFeature : DISABLED_FEATURES_LOW_RAM_TV) {
                PreferenceCompat preferenceCompat =
                        mPreferenceCompatManager.getOrCreatePrefCompat(disabledFeature);
                preferenceCompat.setShouldRemove(true);
                mUIUpdateCallback.notifyUpdate(getStateIdentifier(), preferenceCompat);
            }
        }
        PackageManager packageManager = mContext.getPackageManager();
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            PreferenceCompat preferenceCompat =
                    mPreferenceCompatManager.getOrCreatePrefCompat(KEY_FEATURE_PIP);
            preferenceCompat.setShouldRemove(true);
            mUIUpdateCallback.notifyUpdate(getStateIdentifier(), preferenceCompat);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_SPECIAL_ACCESS;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}

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

package com.android.tv.settings.library.privacy;

import static com.android.tv.settings.library.ManagerUtil.STATE_PRIVACY;

import android.content.Context;
import android.os.Bundle;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;

import java.util.List;

/**
 * Provide data for privacy settings screen in TV settings.
 */
public class PrivacyState extends PreferenceControllerState {
    private static final String KEY_MIC = "microphone";
    private static final String KEY_CAMERA = "camera";

    public PrivacyState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        PreferenceCompat micPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_MIC);
        PreferenceCompat cameraPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_CAMERA);
        PrivacyToggle.MIC_TOGGLE.preparePreferenceWithSensorState(mContext,
                micPref, SensorState.TOGGLE_EXTRA);
        PrivacyToggle.CAMERA_TOGGLE.preparePreferenceWithSensorState(mContext,
                cameraPref, SensorState.TOGGLE_EXTRA);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), micPref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), cameraPref);
    }

    @Override
    public int getStateIdentifier() {
        return STATE_PRIVACY;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}

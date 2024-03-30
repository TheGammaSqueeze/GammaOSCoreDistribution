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

package com.android.tv.settings.library.device.display.displaysound;

import android.content.ContentResolver;
import android.content.Context;
import android.hardware.hdmi.HdmiControlManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.List;

/**
 * State to provide data for rendering display sound settings screen.
 */
public class DisplaySoundState extends PreferenceControllerState {
    static final String KEY_SOUND_EFFECTS = "sound_effects";
    private static final String KEY_CEC = "cec";

    private AudioManager mAudioManager;
    private HdmiControlManager mHdmiControlManager;
    private PreferenceCompat mSoundPref;
    private PreferenceCompat mCecPref;

    public DisplaySoundState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    private static boolean getSoundEffectsEnabled(ContentResolver contentResolver) {
        return Settings.System.getInt(contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1)
                != 0;
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mSoundPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                new String[]{KEY_SOUND_EFFECTS});
        mCecPref = mPreferenceCompatManager.getOrCreatePrefCompat(new String[]{KEY_CEC});
        mSoundPref.setChecked(getSoundEffectsEnabled());
        updateCecPreference();
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mSoundPref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mCecPref);
    }


    @Override
    public void onResume() {
        super.onResume();
        updateCecPreference();
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        if (KEY_SOUND_EFFECTS.equals(key[0])) {
            setSoundEffectsEnabled(status);
            return true;
        }
        return false;
    }

    @Override
    public void onAttach() {
        mAudioManager = mContext.getSystemService(AudioManager.class);
        mHdmiControlManager = mContext.getSystemService(HdmiControlManager.class);
        super.onAttach();
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_DISPLAY_SOUND;
    }

    private boolean getSoundEffectsEnabled() {
        return getSoundEffectsEnabled(mContext.getContentResolver());
    }

    private void setSoundEffectsEnabled(boolean enabled) {
        if (enabled) {
            mAudioManager.loadSoundEffects();
        } else {
            mAudioManager.unloadSoundEffects();
        }
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SOUND_EFFECTS_ENABLED, enabled ? 1 : 0);
    }

    private void updateCecPreference() {
        // Rendering layer should determine whether to show cec toggle as this is a slice
        // preference.
        boolean cecEnabled = mHdmiControlManager.getHdmiCecEnabled()
                == HdmiControlManager.HDMI_CEC_CONTROL_ENABLED;
        mCecPref.setSummary(cecEnabled
                ? ResourcesUtil.getString(mContext, "enabled")
                : ResourcesUtil.getString(mContext, "disabled"));
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mCecPref);
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}

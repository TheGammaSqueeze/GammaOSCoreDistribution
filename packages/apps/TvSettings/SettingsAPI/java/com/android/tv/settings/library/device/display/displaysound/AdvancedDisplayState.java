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

import static android.provider.Settings.Secure.MINIMAL_POST_PROCESSING_ALLOWED;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;

import java.util.List;

/**
 * State to provide data for rendering advanced display screen.
 */
public class AdvancedDisplayState extends PreferenceControllerState {
    private static final String KEY_GAME_MODE = "game_mode";
    private PreferenceCompat mAllowGameMode;

    public AdvancedDisplayState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }


    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mAllowGameMode = mPreferenceCompatManager
                .getOrCreatePrefCompat(new String[]{KEY_GAME_MODE});
        mAllowGameMode.setType(PreferenceCompat.TYPE_SWITCH);
        mAllowGameMode.setChecked(getGameModeStatus() == 1);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mAllowGameMode);
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_ADVANCED_DISPLAY;
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        if (TextUtils.equals(key[0], KEY_GAME_MODE)) {
            setGameModeStatus(status ? 1 : 0);
        }
        return super.onPreferenceTreeClick(key, status);
    }

    private void setGameModeStatus(int state) {
        Settings.Secure.putInt(mContext.getContentResolver(), MINIMAL_POST_PROCESSING_ALLOWED,
                state);
    }

    private int getGameModeStatus() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                MINIMAL_POST_PROCESSING_ALLOWED,
                1);
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}

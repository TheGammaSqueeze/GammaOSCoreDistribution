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

package com.android.tv.settings.library;

import android.annotation.SystemApi;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.tv.settings.library.data.StateManager;

import java.util.List;

/**
 * @hide Provides access to TvSettings data.
 */
@SystemApi
public final class SettingsManager {
    private static final String TAG = "TvSettingsManager";
    private com.android.tv.settings.library.UIUpdateCallback mUIUpdateCallback;
    private final Context mContext;

    /** @hide */
    @SystemApi
    public SettingsManager(Context context) {
        this.mContext = context;
    }

    /** @hide */
    @SystemApi
    public List<PreferenceCompat> getPreferences(int state) {
        return null;
    }

    /** @hide */
    @SystemApi
    public PreferenceCompat getPreference(int state, String key) {
        return null;
    }

    /** @hide */
    @SystemApi
    public void registerListener(com.android.tv.settings.library.UIUpdateCallback callback) {
        mUIUpdateCallback = callback;
    }

    /** @hide */
    @SystemApi
    public void unRegisterListener() {
        mUIUpdateCallback = null;
    }


    /** @hide */
    @SystemApi
    public State createState(int stateIdentifier) {
        return StateManager.createState(mContext, stateIdentifier, mUIUpdateCallback);
    }

    /** @hide */
    @SystemApi
    public void onAttach(State state) {
        state.onAttach();
    }

    /** @hide */
    @SystemApi
    public void onCreate(State state, Bundle extras) {
        state.onCreate(extras);
    }

    /** @hide */
    @SystemApi
    public void onStart(State state) {
        state.onStart();
    }

    /** @hide */
    @SystemApi
    public void onResume(State state) {
        state.onResume();
    }

    /** @hide */
    @SystemApi
    public void onPause(State state) {
        state.onPause();
    }

    /** @hide */
    @SystemApi
    public void onStop(State state) {
        state.onStop();
    }

    /** @hide */
    @SystemApi
    public void onDestroy(State state) {
        state.onDestroy();
    }

    /** @hide */
    @SystemApi
    public boolean onPreferenceClick(State state, String[] key, boolean status) {
        if (state == null || key == null || key.length == 0) {
            return false;
        }
        return state.onPreferenceTreeClick(key, status);
    }

    /** @hide */
    @SystemApi
    public void onDisplayPreferenceDialog(State state, String[] key) {
        if (state != null && key != null && key.length > 0) {
            state.onDisplayDialogPreference(key);
        }
    }

    /** @hide */
    @SystemApi
    public void onActivityResult(State state, int code, int resultCode, Intent data) {
        state.onActivityResult(ManagerUtil.getRequestCode(code), resultCode, data);
    }

    /** @hide */
    @SystemApi
    public boolean onPreferenceChange(State state, String[] key, Object newValue) {
        return state.onPreferenceChange(key, newValue);
    }
}

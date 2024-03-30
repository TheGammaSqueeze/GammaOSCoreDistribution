/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.vendor;

import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;

import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.util.ResolutionSelectionUtils;

import java.util.Objects;

/** A vendor sample of display preview settings. */
@Keep
public class DisplayPreviewFragment extends SettingsPreferenceFragment implements
        DisplayManager.DisplayListener {
    private DisplayManager mDisplayManager;
    private Display.Mode mCurrentMode = null;
    private static final String KEY_RESOLUTION_TITLE = "resolution_selection";

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferencesFromResource(R.xml.preview_display_vendor, null);
        mDisplayManager = getDisplayManager();
        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display.getSystemPreferredDisplayMode() != null) {
            mDisplayManager.registerDisplayListener(this, null);
            mCurrentMode = mDisplayManager.getGlobalUserPreferredDisplayMode();
            updateResolutionTitleDescription(ResolutionSelectionUtils.modeToString(
                    mCurrentMode, getContext()));
        } else {
            removeResolutionPreference();
        }
    }

    @VisibleForTesting
    DisplayManager getDisplayManager() {
        return getContext().getSystemService(DisplayManager.class);
    }

    @Override
    public void onDisplayAdded(int displayId) {}

    @Override
    public void onDisplayRemoved(int displayId) {}

    @Override
    public void onDisplayChanged(int displayId) {
        Display.Mode newMode = mDisplayManager.getGlobalUserPreferredDisplayMode();
        if (!Objects.equals(mCurrentMode, newMode)) {
            updateResolutionTitleDescription(
                    ResolutionSelectionUtils.modeToString(newMode, getContext()));
            mCurrentMode = newMode;
        }
    }

    private void updateResolutionTitleDescription(String summary) {
        Preference titlePreference = findPreference(KEY_RESOLUTION_TITLE);
        if (titlePreference != null) {
            titlePreference.setSummary(summary);
        }
    }

    private void removeResolutionPreference() {
        Preference resolutionPreference = findPreference(KEY_RESOLUTION_TITLE);
        if (resolutionPreference != null) {
            getPreferenceScreen().removePreference(resolutionPreference);
        }
    }
}

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

import static com.android.tv.settings.library.ManagerUtil.STATE_RESOLUTION_SELECTION;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Provide data for resolution selection settings screen.
 */
public class ResolutionSelectionState extends PreferenceControllerState {
    private static final String KEY_MODE_SELECTION = "resolution_selection_option";
    private static final String KEY_RESOLUTION_PREFIX = "resolution_selection_";
    private static final String KEY_RESOLUTION_SELECTION_AUTO = "resolution_selection_auto";

    private DisplayManager mDisplayManager;
    private Display.Mode[] mModes;
    private int mUserPreferredModeIndex;
    private PreferenceCompat mResolutionCategory;

    static final Set<Integer> STANDARD_RESOLUTIONS_IN_ORDER = Set.of(2160, 1080, 720, 576, 480);

    public ResolutionSelectionState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onAttach() {
        super.onAttach();
        mDisplayManager = getDisplayManager();
    }


    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mResolutionCategory = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_MODE_SELECTION);

        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        mModes = display.getSupportedModes();
        Arrays.sort(mModes, new Comparator<Display.Mode>() {
            // Sort in descending order of refresh rate.
            @Override
            public int compare(Display.Mode o1, Display.Mode o2) {
                int resolution1 = Math.min(o1.getPhysicalHeight(), o1.getPhysicalWidth());
                int resolution2 = Math.min(o2.getPhysicalHeight(), o2.getPhysicalWidth());

                // The resolution which is in list of standard resolutions appears before the one
                // which is not.
                if (STANDARD_RESOLUTIONS_IN_ORDER.contains(resolution2)
                        && !STANDARD_RESOLUTIONS_IN_ORDER.contains(resolution1)) {
                    return 1;
                }
                if (STANDARD_RESOLUTIONS_IN_ORDER.contains(resolution1)
                        && !STANDARD_RESOLUTIONS_IN_ORDER.contains(resolution2)) {
                    return -1;
                }
                if (resolution2 == resolution1) {
                    return (int) o2.getRefreshRate() - (int) o1.getRefreshRate();
                }
                return resolution2 - resolution1;
            }
        });

        createPreferences();

        mUserPreferredModeIndex = lookupModeIndex(
                mDisplayManager.getGlobalUserPreferredDisplayMode());
        if (mUserPreferredModeIndex != -1) {
            selectRadioPreference(new String[]{KEY_MODE_SELECTION,
                    KEY_RESOLUTION_PREFIX + mUserPreferredModeIndex});
        } else {
            selectRadioPreference(new String[]{KEY_MODE_SELECTION, KEY_RESOLUTION_SELECTION_AUTO});
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mResolutionCategory);
    }

    DisplayManager getDisplayManager() {
        return mContext.getSystemService(DisplayManager.class);
    }

    private void selectRadioPreference(String[] key) {
        final PreferenceCompat radioPreference = mPreferenceCompatManager.getPrefCompat(key);
        if (radioPreference != null) {
            radioPreference.setChecked(true);
        }
    }

    /** Returns the index of Display mode that matches UserPreferredMode */
    public int lookupModeIndex(Display.Mode userPreferredMode) {
        if (userPreferredMode != null) {
            for (int i = 0; i < mModes.length; i++) {
                if (mModes[i].matches(userPreferredMode.getPhysicalWidth(),
                        userPreferredMode.getPhysicalHeight(),
                        userPreferredMode.getRefreshRate())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void createPreferences() {
        PreferenceCompat pref = mPreferenceCompatManager.getOrCreatePrefCompat(
                new String[]{KEY_MODE_SELECTION, KEY_RESOLUTION_SELECTION_AUTO});
        pref.setTitle(ResourcesUtil.getString(mContext, "resolution_selection_auto_title"));
        pref.setRadioGroup(KEY_MODE_SELECTION);
        pref.setType(PreferenceCompat.TYPE_RADIO);
        mResolutionCategory.addChildPrefCompat(pref);

        for (int i = 0; i < mModes.length; i++) {
            int resolution = Math.min(mModes[i].getPhysicalHeight(), mModes[i].getPhysicalWidth());
            String title = resolution + "p";
            if (resolution == 2160) {
                title = "4k";
            }
            mResolutionCategory.addChildPrefCompat(createResolutionPreferenceCompat(
                    title,
                    getRefreshRateString(mModes[i].getRefreshRate()) + " Hz",
                    i));
        }
    }

    private String getRefreshRateString(float refreshRate) {
        float roundedRefreshRate = Math.round(refreshRate * 100.0f) / 100.0f;
        if (roundedRefreshRate % 1 == 0) {
            return Integer.toString((int) roundedRefreshRate);
        } else {
            return Float.toString(roundedRefreshRate);
        }
    }

    /** Returns a radio preference for each display mode. */
    private PreferenceCompat createResolutionPreferenceCompat(
            String title, String summary, int resolution) {
        PreferenceCompat pref = mPreferenceCompatManager.getOrCreatePrefCompat(new String[]{
                KEY_MODE_SELECTION,
                KEY_RESOLUTION_PREFIX + resolution});
        pref.setTitle(title);
        pref.setSummary(summary);
        pref.setType(PreferenceCompat.TYPE_RADIO);
        pref.setRadioGroup(KEY_MODE_SELECTION);
        return pref;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_RESOLUTION_SELECTION;
    }


    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        if (key.length != 2) {
            return false;
        }

        if (key[1].equals(KEY_RESOLUTION_SELECTION_AUTO)) {
            mDisplayManager.clearGlobalUserPreferredDisplayMode();
            return true;
        } else if (key[1].contains(KEY_RESOLUTION_PREFIX)) {
            int modeIndex = Integer.valueOf(key[1].substring(KEY_RESOLUTION_PREFIX.length()));
            Display.Mode mode = mModes[modeIndex];
            mDisplayManager.setGlobalUserPreferredDisplayMode(mode);
            return true;
        }
        return false;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}

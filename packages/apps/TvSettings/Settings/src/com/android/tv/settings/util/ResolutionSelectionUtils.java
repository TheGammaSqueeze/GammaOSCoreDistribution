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

package com.android.tv.settings.util;

import android.content.Context;
import android.content.res.Resources;
import android.view.Display;

import com.android.tv.settings.R;

/** This utility class for Resolution Setting **/
public class ResolutionSelectionUtils {

    /**
     * Returns the refresh rate converted to a string. If the refresh rate has only 0s after the
     * floating point, they are removed. The unit "Hz" is added to end of refresh rate.
     */
    public static String getRefreshRateString(Resources resources, float refreshRate) {
        float roundedRefreshRate = Math.round(refreshRate * 100.0f) / 100.0f;
        if (roundedRefreshRate % 1 == 0) {
            return ((int) roundedRefreshRate) + " "
                    + resources.getString(R.string.resolution_selection_hz);
        } else {
            return roundedRefreshRate + " " + resources.getString(R.string.resolution_selection_hz);
        }
    }

    /**
     * Returns the resolution converted to a string. The unit "p" is added to end of refresh rate.
     * If the resolution in 2160p, the string returned is "4k".
     */
    public static String getResolutionString(int width, int height) {
        int resolution = Math.min(width, height);
        if (resolution == 2160) {
            return "4k";
        }
        return resolution + "p";
    }

    /**
     * Returns the {@link Display.Mode} converted to a string.
     * Format: Resolution + "p" + RefreshRate + "Hz"
     */
    public static String modeToString(Display.Mode mode, Context context) {
        if (mode == null) {
            return context.getString(R.string.resolution_selection_auto_title);
        }
        String modeString = getResolutionString(mode.getPhysicalWidth(), mode.getPhysicalHeight());
        modeString += " " + getRefreshRateString(context.getResources(), mode.getRefreshRate());
        return modeString;
    }
}

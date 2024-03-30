/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.displayarea;

import static android.view.Display.DEFAULT_DISPLAY;

import android.car.app.CarActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.Log;

/**
 * Utils for CarDisplayArea package.
 */
public class CarDisplayAreaUtils {

    private static final String TAG = "CarDisplayAreaUtils";

    private CarDisplayAreaUtils() {
    }

    static Intent getMapsIntent(Context context) {
        Intent defaultIntent =
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS);
        defaultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return defaultIntent;
    }

    static boolean isCustomDisplayPolicyDefined(Context context) {
        Resources resources = context.getResources();
        String customPolicyName = null;
        try {
            customPolicyName = resources
                    .getString(
                            com.android.internal
                                    .R.string.config_deviceSpecificDisplayAreaPolicyProvider);
        } catch (Resources.NotFoundException ex) {
            Log.w(TAG, "custom policy provider not defined");
        }
        return customPolicyName != null && !customPolicyName.isEmpty();
    }

    static void setPersistentActivity(CarActivityManager am,
            ComponentName activity, int featureId, String featureName) {
        if (activity == null) {
            Log.e(TAG, "Empty activity for " + featureName + " (" + featureId + ")");
            return;
        }
        int ret = am.setPersistentActivity(activity, DEFAULT_DISPLAY, featureId);
        if (ret != CarActivityManager.RESULT_SUCCESS) {
            Log.e(TAG, "Failed to set PersistentActivity: activity=" + activity
                    + ", ret=" + ret);
        }
    }
}

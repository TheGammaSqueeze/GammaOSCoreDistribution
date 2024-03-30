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

package com.android.car.settings.qc;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;

import com.android.car.settings.enterprise.ActionDisabledByAdminActivity;

/**
 * General helper methods for quick controls.
 */
public class QCUtils {
    private QCUtils() {
    }

    /**
     * See {@link #getActionDisabledDialogIntent(Context, String, int)}.
     */
    public static PendingIntent getActionDisabledDialogIntent(Context context, String restriction) {
        return getActionDisabledDialogIntent(context, restriction, /* requestCode= */ 0);
    }

    /**
     * Returns a {@link PendingIntent} for launching a {@link ActionDisabledByAdminActivity} with
     * the specified restriction and request code.
     */
    public static PendingIntent getActionDisabledDialogIntent(Context context, String restriction,
            int requestCode) {
        Intent intent = new Intent();
        intent.setClass(context, ActionDisabledByAdminActivity.class);
        intent.putExtra(DevicePolicyManager.EXTRA_RESTRICTION, restriction);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE);
    }
}

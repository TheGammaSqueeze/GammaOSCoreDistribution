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

package com.android.permissioncontroller.permission.ui.handheld.v31;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageControlPreferenceUtils;

/**
 * Preference for the top level privacy hub page
 */
@RequiresApi(Build.VERSION_CODES.S)
public class PermissionUsageV2ControlPreference extends Preference {

    private final Context mContext;

    public PermissionUsageV2ControlPreference(@NonNull Context context, @NonNull String groupName,
            int count, boolean showSystem, long sessionId, boolean show7Days) {
        super(context);
        mContext = context;

        PermissionUsageControlPreferenceUtils.initPreference(this, mContext, groupName,
                count, showSystem, sessionId, show7Days);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);

        if (SdkLevel.isAtLeastS()) {
            TextView titleView = (TextView) view.findViewById(android.R.id.title);
            TypedArray ta = mContext.obtainStyledAttributes(
                    new int[]{android.R.attr.textAppearanceListItem});
            int resId = ta.getResourceId(0, 0);
            ta.recycle();
            titleView.setTextAppearance(resId);
        }
    }
}

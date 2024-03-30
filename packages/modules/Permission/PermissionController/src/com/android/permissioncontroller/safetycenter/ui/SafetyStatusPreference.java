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

package com.android.permissioncontroller.safetycenter.ui;

import android.content.Context;
import android.safetycenter.SafetyCenterStatus;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

/** Preference which displays a visual representation of {@link SafetyCenterStatus}. */
public class SafetyStatusPreference extends Preference {

    @Nullable
    private SafetyCenterStatus mStatus;
    @Nullable
    private View.OnClickListener mRescanButtonOnClickListener;

    public SafetyStatusPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_safety_status);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        if (mStatus == null) {
            return;
        }

        ((ImageView) holder.findViewById(R.id.status_image))
                .setImageResource(toStatusImageResId(mStatus.getSeverityLevel()));

        ((TextView) holder.findViewById(R.id.status_title)).setText(mStatus.getTitle());
        ((TextView) holder.findViewById(R.id.status_summary)).setText(mStatus.getSummary());

        if (mRescanButtonOnClickListener != null) {
            holder.findViewById(R.id.rescan_button)
                    .setOnClickListener(mRescanButtonOnClickListener);
        }
    }

    void setSafetyStatus(SafetyCenterStatus status) {
        mStatus = status;
        notifyChanged();
    }

    void setRescanButtonOnClickListener(View.OnClickListener listener) {
        mRescanButtonOnClickListener = listener;
        notifyChanged();
    }

    private static int toStatusImageResId(int overallSeverityLevel) {
        switch (overallSeverityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
                return R.drawable.safety_status_info;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                return R.drawable.safety_status_info;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return R.drawable.safety_status_recommendation;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return R.drawable.safety_status_warn;
        }
        throw new IllegalArgumentException(
                String.format("Unexpected SafetyCenterStatus.OverallSeverityLevel: %s",
                        overallSeverityLevel));
    }
}

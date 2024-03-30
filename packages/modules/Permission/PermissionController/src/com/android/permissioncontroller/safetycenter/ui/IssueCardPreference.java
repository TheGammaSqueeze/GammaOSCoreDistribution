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

package com.android.permissioncontroller.safetycenter.ui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static java.util.Objects.requireNonNull;

import android.app.PendingIntent;
import android.content.Context;
import android.safetycenter.SafetyCenterIssue;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

/** A preference that displays a card representing a {@link SafetyCenterIssue}. */
public class IssueCardPreference extends Preference {

    public static final String TAG = IssueCardPreference.class.getSimpleName();

    private final SafetyCenterIssue mIssue;

    public IssueCardPreference(Context context, SafetyCenterIssue issue) {
        super(context);
        setLayoutResource(R.layout.preference_issue_card);

        mIssue = requireNonNull(issue);
    }

    // TODO: Add real todos with bug numbers once UI bug breakdown is finished.
    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ((ImageView) holder.findViewById(R.id.issue_card_banner_icon)).setImageResource(
                toSeverityLevel(mIssue.getSeverityLevel()).getWarningCardIconResId());

        // TODO: Fire off real dismissal (to the API)
        holder.findViewById(R.id.issue_card_dismiss_btn).setOnClickListener(
                (view) -> this.getParent().removePreference(this));

        ((TextView) holder.findViewById(R.id.issue_card_title)).setText(mIssue.getTitle());
        ((TextView) holder.findViewById(R.id.issue_card_subtitle)).setText(mIssue.getSubtitle());
        ((TextView) holder.findViewById(R.id.issue_card_summary)).setText(mIssue.getSummary());

        LinearLayout buttonList =
                ((LinearLayout) holder.findViewById(R.id.issue_card_action_button_list));
        buttonList.removeAllViews(); // This view may be recycled from another issue
        for (SafetyCenterIssue.Action action : mIssue.getActions()) {
            buttonList.addView(buildActionButton(action, holder.itemView.getContext()));
        }
    }

    private Button buildActionButton(
            SafetyCenterIssue.Action action,
            Context context) {
        Button button = new Button(
                context, null, 0, R.style.SafetyCenter_IssueCard_ActionButton);

        button.setText(action.getLabel());
        button.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        button.setOnClickListener((view) -> {
            try {
                action.getPendingIntent().send();
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, String.format("Executing action \"%s\" failed", action.getLabel()), e);
                Toast.makeText(getContext(), "Action failed", Toast.LENGTH_SHORT).show();
            }
        });

        return button;
    }

    private static SeverityLevel toSeverityLevel(int issueSeverityLevel) {
        switch (issueSeverityLevel) {
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK:
                return SeverityLevel.INFORMATION;
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION:
                return SeverityLevel.RECOMMENDATION;
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING:
                return SeverityLevel.CRITICAL_WARNING;
        }
        throw new IllegalArgumentException(
                String.format("Unexpected SafetyCenterIssue.IssueSeverityLevel: %s",
                        issueSeverityLevel));
    }

}

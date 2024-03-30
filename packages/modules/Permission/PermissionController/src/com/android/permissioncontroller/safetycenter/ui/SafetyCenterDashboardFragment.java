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

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.os.Bundle;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterEntryOrGroup;
import android.safetycenter.SafetyCenterIssue;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener;
import android.safetycenter.SafetyCenterStaticEntryGroup;
import android.safetycenter.SafetyCenterStatus;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import com.android.permissioncontroller.R;

import java.util.List;
import java.util.stream.Stream;

/** Dashboard fragment for the Safety Center **/
public final class SafetyCenterDashboardFragment extends PreferenceFragmentCompat {

    private static final String TAG = SafetyCenterDashboardFragment.class.getSimpleName();

    private static final String SAFETY_STATUS_KEY = "safety_status";
    private static final String ISSUES_GROUP_KEY = "issues_group";
    private static final String ENTRIES_GROUP_KEY = "entries_group";
    private static final String STATIC_ENTRIES_GROUP_KEY = "static_entries_group";

    private SafetyCenterManager mSafetyCenterManager;

    private SafetyStatusPreference mSafetyStatusPreference;
    private PreferenceGroup mIssuesGroup;
    private PreferenceGroup mEntriesGroup;
    private PreferenceGroup mStaticEntriesGroup;

    private final OnSafetyCenterDataChangedListener mOnSafetyCenterDataChangedListener =
            new OnSafetyCenterDataChangedListener() {
                @Override
                public void onSafetyCenterDataChanged(@NonNull SafetyCenterData data) {
                    Log.i(TAG, String.format("onSafetyCenterDataChanged called with: %s", data));

                    Context context = getContext();
                    if (context == null) {
                        return;
                    }

                    mSafetyStatusPreference.setSafetyStatus(data.getStatus());

                    // TODO(b/208212820): Only update entries that have changed since last
                    // update, rather than deleting and re-adding all.

                    updateIssues(context, data.getIssues());
                    updateSafetyEntries(context, data.getEntriesOrGroups());
                    updateStaticSafetyEntries(context, data.getStaticEntryGroups());
                }
            };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.safety_center_dashboard, rootKey);

        Context context = requireNonNull(getContext());

        mSafetyCenterManager = requireNonNull(context.getSystemService(SafetyCenterManager.class));

        mSafetyStatusPreference = requireNonNull(
                getPreferenceScreen().findPreference(SAFETY_STATUS_KEY));
        // TODO: Use real strings here, or set more sensible defaults in the layout
        mSafetyStatusPreference.setSafetyStatus(new SafetyCenterStatus.Builder("Looks good", "")
                .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
                .build());
        mSafetyStatusPreference.setRescanButtonOnClickListener(unused ->
                mSafetyCenterManager.refreshSafetySources(
                        SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK));

        mIssuesGroup = getPreferenceScreen().findPreference(ISSUES_GROUP_KEY);
        mEntriesGroup = getPreferenceScreen().findPreference(ENTRIES_GROUP_KEY);
        mStaticEntriesGroup = getPreferenceScreen().findPreference(STATIC_ENTRIES_GROUP_KEY);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSafetyCenterManager.addOnSafetyCenterDataChangedListener(
                ContextCompat.getMainExecutor(requireNonNull(getContext())),
                mOnSafetyCenterDataChangedListener);
        mSafetyCenterManager.refreshSafetySources(SafetyCenterManager.REFRESH_REASON_PAGE_OPEN);
    }

    @Override
    public void onPause() {
        mSafetyCenterManager.removeOnSafetyCenterDataChangedListener(
                mOnSafetyCenterDataChangedListener);
        super.onPause();
    }

    private void updateIssues(Context context, List<SafetyCenterIssue> issues) {
        mIssuesGroup.removeAll();

        issues.stream()
                .map(issue -> new IssueCardPreference(context, issue))
                .forEachOrdered(mIssuesGroup::addPreference);
    }

    // TODO(b/208212820): Add groups and move to separate controller
    private void updateSafetyEntries(Context context,
            List<SafetyCenterEntryOrGroup> entriesOrGroups) {
        mEntriesGroup.removeAll();

        entriesOrGroups.stream()
                .flatMap(entryOrGroup ->
                        entryOrGroup.getEntry() != null
                                ? Stream.of(entryOrGroup.getEntry())
                                : entryOrGroup.getEntryGroup().getEntries().stream())
                .map(entry -> new SafetyEntryPreference(context, entry))
                .forEachOrdered(mEntriesGroup::addPreference);
    }

    private void updateStaticSafetyEntries(Context context,
            List<SafetyCenterStaticEntryGroup> staticEntryGroups) {
        mStaticEntriesGroup.removeAll();

        staticEntryGroups.stream()
                .flatMap(group -> group.getStaticEntries().stream())
                .map(entry -> new StaticSafetyEntryPreference(context, entry))
                .forEachOrdered(mStaticEntriesGroup::addPreference);
    }

}

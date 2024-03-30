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

package com.android.permissioncontroller.permission.ui.handheld.v31;

import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_DETAILS_INTERACTION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_DETAILS_INTERACTION__ACTION__INFO_ICON_CLICKED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.write;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.compat.IntentCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Preference for the permission history page
 */
public class PermissionHistoryPreference extends Preference {

    private static final String LOG_TAG = "PermissionHistoryPreference";

    private final Context mContext;
    private final UserHandle mUserHandle;
    private final String mPackageName;
    private final String mPermissionGroup;
    private final String mAccessTime;
    private final Drawable mAppIcon;
    private final String mTitle;
    private final List<Long> mAccessTimeList;
    private final ArrayList<String> mAttributionTags;
    private final boolean mIsLastUsage;
    private final Intent mIntent;
    private final boolean mShowingAttribution;
    private final PackageManager mPackageManager;

    private final long mSessionId;

    private Drawable mWidgetIcon;

    public PermissionHistoryPreference(@NonNull Context context,
            @NonNull UserHandle userHandle, @NonNull String pkgName,
            @NonNull Drawable appIcon,
            @NonNull String preferenceTitle,
            @NonNull String permissionGroup, @NonNull String accessTime,
            @Nullable CharSequence summaryText, boolean showingAttribution,
            @NonNull List<Long> accessTimeList,
            @NonNull ArrayList<String> attributionTags, boolean isLastUsage, long sessionId) {
        super(context);
        mContext = context;
        mUserHandle = userHandle;
        mPackageName = pkgName;
        mPermissionGroup = permissionGroup;
        mAccessTime = accessTime;
        mAppIcon = appIcon;
        mTitle = preferenceTitle;
        mWidgetIcon = null;
        mAccessTimeList = accessTimeList;
        mAttributionTags = attributionTags;
        mIsLastUsage = isLastUsage;
        mSessionId = sessionId;
        mShowingAttribution = showingAttribution;
        mPackageManager = context.getPackageManager();

        setTitle(mTitle);
        if (summaryText != null) {
            setSummary(summaryText);
        }

        mIntent = getViewPermissionUsageForPeriodIntent(showingAttribution);
        if (mIntent != null) {
            mWidgetIcon = mContext.getDrawable(R.drawable.ic_info_outline);
            setWidgetLayoutResource(R.layout.image_view_with_divider);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ViewGroup widgetFrame = (ViewGroup) holder.findViewById(android.R.id.widget_frame);
        LinearLayout widgetFrameParent = (LinearLayout) widgetFrame.getParent();

        View iconFrame = holder.findViewById(R.id.icon_frame);
        widgetFrameParent.removeView(iconFrame);

        ViewGroup widget = (ViewGroup) holder.findViewById(R.id.permission_history_layout);
        if (widget == null) {
            LayoutInflater inflater = mContext.getSystemService(LayoutInflater.class);
            widget = (ViewGroup) inflater.inflate(R.layout.permission_history_widget,
                    widgetFrameParent, false);

            widgetFrameParent.addView(widget, 0);
        }

        widgetFrameParent.setGravity(Gravity.TOP);

        TextView permissionHistoryTime = widget.findViewById(R.id.permission_history_time);
        permissionHistoryTime.setText(mAccessTime);

        ImageView permissionIcon = widget.findViewById(R.id.permission_history_icon);
        permissionIcon.setImageDrawable(mAppIcon);

        ImageView widgetView = widgetFrame.findViewById(R.id.icon);
        setInfoIcon(widgetView);

        View dashLine = widget.findViewById(R.id.permission_history_dash_line);
        dashLine.setVisibility(mIsLastUsage ? View.GONE : View.VISIBLE);

        Intent intent = getManagePermissionUsageIntent();
        if (intent == null) {
            intent = getDefaultManageAppPermissionsIntent();
        }

        Intent finalIntent = intent;
        setOnPreferenceClickListener((preference) -> {
            mContext.startActivity(finalIntent);
            return true;
        });
    }

    private void setInfoIcon(ImageView widgetView) {
        if (mIntent != null) {
            widgetView.setImageDrawable(mWidgetIcon);
            widgetView.setOnClickListener(v -> {
                write(PERMISSION_DETAILS_INTERACTION,
                        mSessionId,
                        mPermissionGroup,
                        mPackageName,
                        PERMISSION_DETAILS_INTERACTION__ACTION__INFO_ICON_CLICKED);
                try {
                    mContext.startActivity(mIntent);
                } catch (ActivityNotFoundException e) {
                    Log.e(LOG_TAG, "No activity found for viewing permission usage.");
                }
            });
        }
    }

    private Intent getDefaultManageAppPermissionsIntent() {
        Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS);
        intent.putExtra(Intent.EXTRA_USER, mUserHandle);
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, mPackageName);

        return intent;
    }

    /**
     * Get a {@link Intent#ACTION_MANAGE_PERMISSION_USAGE} intent, or null if the intent
     * can't be handled.
     *
     * Suppressing the NewApi warning since we already have a isAtLeastT() check.
     */
    @Nullable
    @SuppressWarnings("NewApi")
    private Intent getManagePermissionUsageIntent() {
        if (!mShowingAttribution || !SdkLevel.isAtLeastT() || mPackageName == null) {
            return null;
        }

        Intent intent = new Intent(Intent.ACTION_MANAGE_PERMISSION_USAGE);
        intent.setPackage(mPackageName);
        intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, mPermissionGroup);
        intent.putExtra(Intent.EXTRA_ATTRIBUTION_TAGS, mAttributionTags.toArray(new String[0]));
        intent.putExtra(Intent.EXTRA_START_TIME, mAccessTimeList.get(mAccessTimeList.size() - 1));
        intent.putExtra(Intent.EXTRA_END_TIME, mAccessTimeList.get(0));
        intent.putExtra(IntentCompat.EXTRA_SHOWING_ATTRIBUTION, mShowingAttribution);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        ResolveInfo resolveInfo = mPackageManager.resolveActivity(intent,
                PackageManager.ResolveInfoFlags.of(0));
        if (resolveInfo == null || resolveInfo.activityInfo == null || !Objects.equals(
                resolveInfo.activityInfo.permission,
                android.Manifest.permission.START_VIEW_PERMISSION_USAGE)) {
            return null;
        }
        intent.setComponent(new ComponentName(mPackageName, resolveInfo.activityInfo.name));

        return intent;
    }

    /**
     * Get a {@link Intent#ACTION_VIEW_PERMISSION_USAGE_FOR_PERIOD} intent, or null if the intent
     * can't be handled.
     */
    @Nullable
    private Intent getViewPermissionUsageForPeriodIntent(boolean showingAttribution) {
        Intent viewUsageIntent = new Intent();
        viewUsageIntent.setAction(Intent.ACTION_VIEW_PERMISSION_USAGE_FOR_PERIOD);
        viewUsageIntent.setPackage(mPackageName);
        viewUsageIntent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, mPermissionGroup);
        viewUsageIntent.putExtra(Intent.EXTRA_ATTRIBUTION_TAGS,
                mAttributionTags.toArray(new String[0]));
        viewUsageIntent.putExtra(Intent.EXTRA_START_TIME,
                mAccessTimeList.get(mAccessTimeList.size() - 1));
        viewUsageIntent.putExtra(Intent.EXTRA_END_TIME, mAccessTimeList.get(0));
        viewUsageIntent.putExtra(IntentCompat.EXTRA_SHOWING_ATTRIBUTION, showingAttribution);
        viewUsageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        ResolveInfo resolveInfo = mPackageManager.resolveActivity(viewUsageIntent,
                PackageManager.MATCH_INSTANT);
        if (resolveInfo != null && resolveInfo.activityInfo != null && Objects.equals(
                resolveInfo.activityInfo.permission,
                android.Manifest.permission.START_VIEW_PERMISSION_USAGE)) {
            return viewUsageIntent;
        }
        return null;
    }
}
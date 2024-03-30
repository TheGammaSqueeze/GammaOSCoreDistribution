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

package com.android.permissioncontroller.permission.ui.auto.dashboard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.preference.Preference.OnPreferenceClickListener
import com.android.car.ui.preference.CarUiPreference
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.compat.IntentCompat
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel
import java.util.Objects

/** Preference that displays a permission usage for an app. */
class AutoPermissionHistoryPreference(
    private val context: Context,
    private val historyPreferenceData: PermissionUsageDetailsViewModel.HistoryPreferenceData
) : CarUiPreference(context) {

    init {
        title = historyPreferenceData.preferenceTitle
        summary =
            if (historyPreferenceData.summaryText != null) {
                context.getString(
                    R.string.auto_permission_usage_timeline_summary,
                    historyPreferenceData.accessTime,
                    historyPreferenceData.summaryText)
            } else {
                historyPreferenceData.accessTime
            }
        if (historyPreferenceData.appIcon != null) {
            icon = historyPreferenceData.appIcon
        }

        // TODO(b/268413649) this logic should be shared across form factors
        val intent = getManagePermissionUsageIntent() ?: getDefaultManageAppPermissionsIntent()
        onPreferenceClickListener = OnPreferenceClickListener {
            context.startActivity(intent)
            true
        }
    }

    /** Creates the [Intent] for the click action of a privacy dashboard app usage event. */
    private fun getDefaultManageAppPermissionsIntent(): Intent {
        return Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS).apply {
            putExtra(Intent.EXTRA_USER, historyPreferenceData.userHandle)
            putExtra(Intent.EXTRA_PACKAGE_NAME, historyPreferenceData.pkgName)
        }
    }

    /**
     * Gets an [Intent.ACTION_MANAGE_PERMISSION_USAGE] intent, or null if attribution shouldn't be
     * shown or the intent can't be handled.
     */
    private fun getManagePermissionUsageIntent(): Intent? {
        // TODO(b/255992934) only location provider apps should be able to provide this intent
        if (!historyPreferenceData.showingAttribution || !SdkLevel.isAtLeastT()) {
            return null
        }
        val intent =
            Intent(Intent.ACTION_MANAGE_PERMISSION_USAGE).apply {
                setPackage(historyPreferenceData.pkgName)
                putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, historyPreferenceData.permissionGroup)
                putExtra(
                    Intent.EXTRA_ATTRIBUTION_TAGS,
                    historyPreferenceData.attributionTags.toTypedArray())
                putExtra(
                    Intent.EXTRA_START_TIME,
                    historyPreferenceData.accessTimeList[
                            historyPreferenceData.accessTimeList.size - 1])
                putExtra(Intent.EXTRA_END_TIME, historyPreferenceData.accessTimeList[0])
                putExtra(
                    IntentCompat.EXTRA_SHOWING_ATTRIBUTION,
                    historyPreferenceData.showingAttribution)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val resolveInfo =
            context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
        if (resolveInfo?.activityInfo == null ||
            !Objects.equals(
                resolveInfo.activityInfo.permission,
                Manifest.permission.START_VIEW_PERMISSION_USAGE)) {
            return null
        }
        intent.component =
            ComponentName(historyPreferenceData.pkgName, resolveInfo.activityInfo.name)
        return intent
    }
}

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

package com.android.permissioncontroller.permission.ui.model.v31

import android.Manifest
import android.app.LoaderManager
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.util.ArrayMap
import android.util.ArraySet
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.permission.model.AppPermissionGroup
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage
import com.android.permissioncontroller.permission.model.v31.PermissionUsages
import com.android.permissioncontroller.permission.model.legacy.PermissionApps.PermissionApp
import com.android.permissioncontroller.permission.ui.handheld.v31.is7DayToggleEnabled
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupLabel
import com.android.permissioncontroller.permission.utils.Utils
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.max

@RequiresApi(Build.VERSION_CODES.S)
class PermissionUsageViewModel(val roleManager: RoleManager) : ViewModel() {

    companion object {
        private const val LOG_TAG = "PermissionUsageViewModel"

        /** TODO(ewol): Use the config setting to determine amount of time to show. */
        private val TIME_FILTER_MILLIS = TimeUnit.DAYS.toMillis(7)
        private val TIME_7_DAYS_DURATION = TimeUnit.DAYS.toMillis(7)
        private val TIME_24_HOURS_DURATION = TimeUnit.DAYS.toMillis(1)

        @JvmStatic
        val PERMISSION_GROUP_ORDER: Map<String, Int> = mapOf(
            Manifest.permission_group.LOCATION to 0,
            Manifest.permission_group.CAMERA to 1,
            Manifest.permission_group.MICROPHONE to 2
        )
        private const val DEFAULT_ORDER = 3
    }

    fun loadPermissionUsages(
        loaderManager: LoaderManager,
        permissionUsages: PermissionUsages,
        callback: PermissionUsages.PermissionsUsagesChangeCallback
    ) {
        val filterTimeBeginMillis = max(System.currentTimeMillis() - TIME_FILTER_MILLIS,
            Instant.EPOCH.toEpochMilli())
        permissionUsages.load(null /*filterPackageName*/, null /*filterPermissionGroups*/,
            filterTimeBeginMillis, Long.MAX_VALUE, PermissionUsages.USAGE_FLAG_LAST
                or PermissionUsages.USAGE_FLAG_HISTORICAL, loaderManager,
            false /*getUiInfo*/, false /*getNonPlatformPermissions*/, callback /*callback*/,
            false /*sync*/)
    }

    fun extractUsages(
        permissionUsages: List<AppPermissionUsage>,
        show7Days: Boolean,
        showSystem: Boolean
    ): Triple<MutableMap<String, Int>, ArrayList<PermissionApp>, Boolean> {
        val curTime = System.currentTimeMillis()
        val showPermissionUsagesDuration = if (is7DayToggleEnabled() && show7Days) {
            TIME_7_DAYS_DURATION
        } else {
            TIME_24_HOURS_DURATION
        }
        val startTime = max(curTime - showPermissionUsagesDuration, Instant.EPOCH.toEpochMilli())

        // Permission group to count mapping.
        val usages: MutableMap<String, Int> = HashMap()
        val permissionGroups: List<AppPermissionGroup> = getOSPermissionGroups(permissionUsages)
        for (i in permissionGroups.indices) {
            usages[permissionGroups[i].name] = 0
        }
        val permApps = ArrayList<PermissionApp>()

        val exemptedPackages = Utils.getExemptedPackages(roleManager)

        val seenSystemApp: Boolean = extractPermissionUsage(exemptedPackages,
            usages, permApps, startTime, permissionUsages, showSystem)

        return Triple(usages, permApps, seenSystemApp)
    }

    fun createGroupUsagesList(
        context: Context,
        usages: Map<String, Int>
    ): List<Map.Entry<String, Int>> {
        val groupUsageNameToLabel: MutableMap<String, CharSequence> = HashMap()
        val groupUsagesList: MutableList<Map.Entry<String, Int>> = ArrayList(usages.entries)
        val usagesEntryCount = groupUsagesList.size
        for (usageEntryIndex in 0 until usagesEntryCount) {
            val (key) = groupUsagesList[usageEntryIndex]
            groupUsageNameToLabel[key] = getPermGroupLabel(context, key)
        }

        groupUsagesList.sortWith { e1: Map.Entry<String, Int>, e2: Map.Entry<String, Int> ->
            comparePermissionGroupUsage(e1, e2, groupUsageNameToLabel)
        }

        return groupUsagesList
    }

    private fun comparePermissionGroupUsage(
        first: Map.Entry<String, Int>,
        second: Map.Entry<String, Int>,
        groupUsageNameToLabelMapping: Map<String, CharSequence>
    ): Int {
        val firstPermissionOrder = PERMISSION_GROUP_ORDER
            .getOrDefault(first.key, DEFAULT_ORDER)
        val secondPermissionOrder = PERMISSION_GROUP_ORDER
            .getOrDefault(second.key, DEFAULT_ORDER)
        return if (firstPermissionOrder != secondPermissionOrder) {
            firstPermissionOrder - secondPermissionOrder
        } else groupUsageNameToLabelMapping[first.key].toString()
            .compareTo(groupUsageNameToLabelMapping[second.key].toString())
    }

    /**
     * Get the permission groups declared by the OS.
     *
     * @return a list of the permission groups declared by the OS.
     */
    private fun getOSPermissionGroups(
        permissionUsages: List<AppPermissionUsage>
    ): List<AppPermissionGroup> {
        val groups: MutableList<AppPermissionGroup> = java.util.ArrayList()
        val seenGroups: MutableSet<String> = ArraySet()
        val numGroups: Int = permissionUsages.size
        for (i in 0 until numGroups) {
            val appUsage: AppPermissionUsage = permissionUsages.get(i)
            val groupUsages = appUsage.groupUsages
            val groupUsageCount = groupUsages.size
            for (j in 0 until groupUsageCount) {
                val groupUsage = groupUsages[j]
                if (Utils.isModernPermissionGroup(groupUsage.group.name)) {
                    if (seenGroups.add(groupUsage.group.name)) {
                        groups.add(groupUsage.group)
                    }
                }
            }
        }
        return groups
    }

    /**
     * Extract the permission usages from mAppPermissionUsages and put the extracted usages
     * into usages and permApps. Returns whether we have seen a system app during the process.
     *
     * TODO: theianchen
     * It's doing two things at the same method which is violating the SOLID principle.
     * We should fix this.
     *
     * @param exemptedPackages packages that are the role holders for exempted roles
     * @param usages an empty List that will be filled with permission usages.
     * @param permApps an empty List that will be filled with permission apps.
     * @return whether we have seen a system app.
     */
    private fun extractPermissionUsage(
        exemptedPackages: Set<String>,
        usages: MutableMap<String, Int>,
        permApps: java.util.ArrayList<PermissionApp>,
        startTime: Long,
        permissionUsages: List<AppPermissionUsage>,
        showSystem: Boolean
    ): Boolean {

        val mGroupAppCounts: ArrayMap<String?, Int> = ArrayMap()
        var seenSystemApp = false
        val numApps: Int = permissionUsages.size
        for (appNum in 0 until numApps) {
            val appUsage: AppPermissionUsage = permissionUsages.get(appNum)
            if (exemptedPackages.contains(appUsage.packageName)) {
                continue
            }
            var used = false
            val appGroups = appUsage.groupUsages
            val numGroups = appGroups.size
            for (groupNum in 0 until numGroups) {
                val groupUsage = appGroups[groupNum]
                val groupName = groupUsage.group.name
                val lastAccessTime = groupUsage.lastAccessTime
                if (lastAccessTime == 0L) {
                    Log.w(
                        LOG_TAG,
                        "Unexpected access time of 0 for ${appUsage.app.key} " +
                            groupUsage.group.name)
                    continue
                }
                if (lastAccessTime < startTime) {
                    continue
                }
                val isSystemApp = !Utils.isGroupOrBgGroupUserSensitive(
                    groupUsage.group)
                seenSystemApp = seenSystemApp || isSystemApp

                // If not showing system apps, skip.
                if (!showSystem && isSystemApp) {
                    continue
                }
                used = true
                addGroupUser(mGroupAppCounts, groupName)
                usages[groupName] = usages.getOrDefault(groupName, 0) + 1
            }
            if (used) {
                permApps.add(appUsage.app)
                addGroupUser(mGroupAppCounts, null)
            }
        }
        return seenSystemApp
    }

    private fun addGroupUser(groupAppCounts: ArrayMap<String?, Int>, app: String?) {
        val count: Int? = groupAppCounts[app]
        if (count == null) {
            groupAppCounts[app] = 1
        } else {
            groupAppCounts[app] = count + 1
        }
    }
}

/**
 * Factory for an PermissionUsageViewModel
 */
@RequiresApi(Build.VERSION_CODES.S)
class PermissionUsageViewModelFactory(
    private val roleManager: RoleManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PermissionUsageViewModel(roleManager) as T
    }
}

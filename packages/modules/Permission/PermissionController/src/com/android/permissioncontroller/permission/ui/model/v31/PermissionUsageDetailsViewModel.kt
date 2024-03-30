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
import android.app.AppOpsManager
import android.app.Application
import android.app.LoaderManager
import android.app.role.RoleManager
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.text.format.DateFormat
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.model.AppPermissionGroup
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage.GroupUsage
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage.GroupUsage.AttributionLabelledGroupUsage
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage.TimelineUsage
import com.android.permissioncontroller.permission.model.v31.PermissionUsages
import com.android.permissioncontroller.permission.model.legacy.PermissionApps.PermissionApp
import com.android.permissioncontroller.permission.ui.handheld.v31.getDurationUsedStr
import com.android.permissioncontroller.permission.ui.handheld.v31.is7DayToggleEnabled
import com.android.permissioncontroller.permission.ui.handheld.v31.shouldShowSubattributionInPermissionsDashboard
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPackageLabel
import com.android.permissioncontroller.permission.utils.SubattributionUtils
import com.android.permissioncontroller.permission.utils.Utils
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Objects
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors
import kotlin.math.max

/**
 * View model for the permission details fragment.
 */
@RequiresApi(Build.VERSION_CODES.S)
class PermissionUsageDetailsViewModel(
    val application: Application,
    val roleManager: RoleManager,
    private val filterGroup: String,
    val sessionId: Long
) : ViewModel() {

    companion object {
        private const val ONE_HOUR_MS = 3_600_000
        private const val ONE_MINUTE_MS = 60_000
        private const val CLUSTER_MINUTES_APART = 1
        private val TIME_7_DAYS_DURATION: Long = DAYS.toMillis(7)
        private val TIME_24_HOURS_DURATION: Long = DAYS.toMillis(1)
        private val ALLOW_CLUSTERING_PERMISSION_GROUPS = listOf(
            Manifest.permission_group.LOCATION,
            Manifest.permission_group.CAMERA,
            Manifest.permission_group.MICROPHONE
        )
    }

    private val filterTimes = mutableListOf<TimeFilterItem>()

    // Truncate to midnight in current timezone.
    private val midnightToday = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS)
        .toEpochSecond() * 1000L
    private val midnightYesterday = ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.DAYS)
        .toEpochSecond() * 1000L

    init {
        initializeTimeFilter(application)
    }

    /**
     * Loads permission usages using [PermissionUsages]. Response is returned to the [callback].
     */
    fun loadPermissionUsages(
        loaderManager: LoaderManager,
        permissionUsages: PermissionUsages,
        callback: PermissionUsages.PermissionsUsagesChangeCallback,
        filterTimesIndex: Int
    ) {
        val timeFilterItem: TimeFilterItem = filterTimes[filterTimesIndex]
        val filterTimeBeginMillis = max(System.currentTimeMillis() - timeFilterItem.time, 0)
        permissionUsages.load(
            /* filterPackageName= */ null,
            /* filterPermissionGroups= */ null,
            filterTimeBeginMillis,
            Long.MAX_VALUE,
            PermissionUsages.USAGE_FLAG_LAST or PermissionUsages.USAGE_FLAG_HISTORICAL,
            loaderManager,
            /* getUiInfo= */ false,
            /* getNonPlatformPermissions= */ false,
            /* callback= */ callback,
            /* sync= */ false)
    }

    /**
     * Returns whether app subattribution should be shown.
     */
    private fun shouldShowSubattributionForApp(appPermissionUsage: AppPermissionUsage): Boolean {
        return shouldShowSubattributionInPermissionsDashboard() &&
            SubattributionUtils.isSubattributionSupported(application,
                appPermissionUsage.app.appInfo)
    }

    /**
     * Create a list of [AppPermissionUsageEntry]s based on the provided data.
     *
     * @param appPermissionUsages data about app permission usages
     * @param exemptedPackages packages whose usage should not be included in the out
     * @param permApps mutable list of [PermissionApp] for keeping track of information about apps.
     *                 This field is updated as a side effect of running this method.
     * @param seenSystemApp mutable field to track whether a system app has recent usage. Updated
     *                           as a side effect of running this method.
     * @param showSystemApp whether System apps should be shown
     * @param show7Days whether the last 7 days of history should be shown
     */
    fun parseUsages(
        appPermissionUsages: List<AppPermissionUsage>,
        exemptedPackages: Set<String>,
        permApps: MutableList<PermissionApp>,
        seenSystemApp: AtomicBoolean,
        showSystemApp: Boolean,
        show7Days: Boolean
    ): List<AppPermissionUsageEntry> {
        val curTime = System.currentTimeMillis()
        val showPermissionUsagesDuration = if (is7DayToggleEnabled() && show7Days) {
            TIME_7_DAYS_DURATION
        } else {
            TIME_24_HOURS_DURATION
        }
        val startTime = (curTime - showPermissionUsagesDuration)
            .coerceAtLeast(Instant.EPOCH.toEpochMilli())

        return appPermissionUsages
            .asSequence()
            .filter { appUsage: AppPermissionUsage ->
                !exemptedPackages.contains(appUsage.packageName)
            }
            .map { appUsage: AppPermissionUsage ->
                filterAndConvert(appUsage, filterGroup)
            }
            .flatten()
            .map { usageData: UsageData ->
                // Fetch the access time list of the app accesses mFilterGroup permission group
                // The DiscreteAccessTime is a Triple of (access time, access duration,
                // proxy) of that app
                val discreteAccessTimeList:
                    MutableList<Triple<Long, Long, AppOpsManager.OpEventProxyInfo>> =
                    mutableListOf()
                val timelineUsages = usageData.timelineUsages
                val numGroups = timelineUsages.size
                for (groupIndex in 0 until numGroups) {
                    val timelineUsage = timelineUsages[groupIndex]
                    if (!timelineUsage.hasDiscreteData()) {
                        continue
                    }
                    val isSystemApp = !Utils.isGroupOrBgGroupUserSensitive(timelineUsage.group)
                    seenSystemApp.set(seenSystemApp.get() || isSystemApp)
                    if (isSystemApp && !showSystemApp) {
                        continue
                    }
                    for (discreteAccessTime in timelineUsage.allDiscreteAccessTime) {
                        if (discreteAccessTime.first == 0L ||
                            discreteAccessTime.first < startTime) {
                            continue
                        }
                        discreteAccessTimeList.add(discreteAccessTime)
                    }
                }
                discreteAccessTimeList.sortWith { x, y -> y.first.compareTo(x.first) }
                if (discreteAccessTimeList.size > 0) {
                    permApps.add(usageData.app)
                }

                // If the current permission group is not LOCATION or there's only one access
                // for the app, return individual entry early.
                if (!ALLOW_CLUSTERING_PERMISSION_GROUPS.contains(filterGroup) ||
                    discreteAccessTimeList.size <= 1) {
                    return@map discreteAccessTimeList.map { time ->
                        AppPermissionUsageEntry(usageData, time.first, mutableListOf(time))
                    }
                }

                // Group access time list
                val usageEntries = mutableListOf<AppPermissionUsageEntry>()
                var ongoingEntry: AppPermissionUsageEntry? = null
                for (time in discreteAccessTimeList) {
                    if (ongoingEntry == null) {
                        ongoingEntry = AppPermissionUsageEntry(usageData, time.first,
                            mutableListOf(time))
                    } else {
                        val ongoingAccessTimeList:
                            MutableList<Triple<Long, Long, AppOpsManager.OpEventProxyInfo>> =
                            ongoingEntry.clusteredAccessTimeList
                        if (time.first / ONE_HOUR_MS !=
                            ongoingAccessTimeList[0].first / ONE_HOUR_MS ||
                            ongoingAccessTimeList[ongoingAccessTimeList.size - 1].first /
                            ONE_MINUTE_MS - time.first / ONE_MINUTE_MS > CLUSTER_MINUTES_APART
                        ) {
                            // If the current access time is not in the same hour nor within
                            // CLUSTER_MINUTES_APART, add the ongoing entry to the usage list
                            // and start a new ongoing entry.
                            usageEntries.add(ongoingEntry)
                            ongoingEntry = AppPermissionUsageEntry(usageData, time.first,
                                mutableListOf(time))
                        } else {
                            ongoingAccessTimeList.add(time)
                        }
                    }
                }
                ongoingEntry?.let { usageEntries.add(it) }
                usageEntries
            }
            .flatten()
            .sortedWith { x, y ->
                // Sort all usage entries by startTime desc, and then by app name.
                val timeCompare = java.lang.Long.compare(y.endTime, x.endTime)
                if (timeCompare != 0) {
                    return@sortedWith timeCompare
                }
                x.usageData.app.label.compareTo(y.usageData.app.label)
            }
            .toList()
    }

    /**
     * Render [usages] into the [preferenceScreen] UI.
     */
    fun renderTimelinePreferences(
        usages: List<AppPermissionUsageEntry>,
        category: AtomicReference<PreferenceCategory>,
        preferenceScreen: PreferenceScreen,
        historyPreferenceFactory: HistoryPreferenceFactory
    ) {
        val context = application
        var hasADateLabel = false
        var lastDateLabel = 0L
        usages.forEachIndexed { usageNum, usage ->
            val usageTimestamp = usage.endTime
            val usageDateLabel = ZonedDateTime.ofInstant(Instant.ofEpochMilli(usageTimestamp),
                Clock.systemDefaultZone().zone)
                .truncatedTo(ChronoUnit.DAYS).toEpochSecond() * 1000L
            if (!hasADateLabel || usageDateLabel != lastDateLabel) {
                if (hasADateLabel) {
                    category.set(historyPreferenceFactory.createDayCategoryPreference())
                    preferenceScreen.addPreference(category.get())
                }
                val formattedDateTitle = DateFormat.getDateFormat(context)
                    .format(usageDateLabel)
                if (usageTimestamp > midnightToday) {
                    category.get().setTitle(R.string.permission_history_category_today)
                } else if (usageTimestamp > midnightYesterday) {
                    category.get().setTitle(R.string.permission_history_category_yesterday)
                } else {
                    category.get().setTitle(formattedDateTitle)
                }
                hasADateLabel = true
            }

            lastDateLabel = usageDateLabel

            val accessTime = DateFormat.getTimeFormat(context).format(usage.endTime)
            var durationLong: Long = usage.clusteredAccessTimeList
                .map { p -> p.second }
                .filter { dur -> dur > 0 }
                .sum()

            val accessTimeList: List<Long> = usage.clusteredAccessTimeList.map { p -> p.first }

            // Determine the preference summary. Start with the duration string
            var summaryLabel: String? = null
            // Since Location accesses are atomic, we manually calculate the access duration
            // by comparing the first and last access within the cluster
            if (filterGroup == Manifest.permission_group.LOCATION) {
                if (accessTimeList.size > 1) {
                    durationLong = (accessTimeList[0] - accessTimeList[accessTimeList.size - 1])

                    // Similar to other history items, only show the duration if it's longer
                    // than the clustering granularity.
                    if (durationLong
                        >= TimeUnit.MINUTES.toMillis(CLUSTER_MINUTES_APART.toLong()) + 1) {
                        summaryLabel = getDurationUsedStr(context, durationLong)
                    }
                }
            } else {
                // Only show the duration if it is at least (cluster + 1) minutes. Displaying
                // times that are the same as the cluster granularity does not convey useful
                // information.
                if (durationLong != null &&
                    durationLong >=
                    TimeUnit.MINUTES.toMillis((CLUSTER_MINUTES_APART + 1).toLong())) {
                    summaryLabel = getDurationUsedStr(context, durationLong)
                }
            }

            var proxyPackageLabel: String? = null
            for (clusteredAccessTime in usage.clusteredAccessTimeList) {
                val proxy = clusteredAccessTime.third
                if (proxy != null && proxy.packageName != null) {
                    proxyPackageLabel = getPackageLabel(
                        PermissionControllerApplication.get(), proxy.packageName!!,
                        UserHandle.getUserHandleForUid(proxy.uid))
                    break
                }
            }

            // fetch the subattribution label for this usage.
            var subattributionLabel: String? = null
            if (usage.usageData.label != Resources.ID_NULL) {
                val attributionLabels: Map<Int, String>? = usage.usageData.app.attributionLabels
                if (attributionLabels != null) {
                    subattributionLabel = attributionLabels[usage.usageData.label]
                }
            }

            // create subtext string.
            val subTextStrings: MutableList<String?> = mutableListOf()
            val showingAttribution = subattributionLabel != null && subattributionLabel.isNotEmpty()
            if (showingAttribution) {
                subTextStrings.add(subattributionLabel)
            }
            if (proxyPackageLabel != null) {
                subTextStrings.add(proxyPackageLabel)
            }
            if (summaryLabel != null) {
                subTextStrings.add(summaryLabel)
            }
            var subText: String? = null
            when (subTextStrings.size) {
                3 -> {
                    subText = context.getString(
                        R.string.history_preference_subtext_3,
                        subTextStrings[0],
                        subTextStrings[1],
                        subTextStrings[2])
                }
                2 -> {
                    subText = context.getString(R.string.history_preference_subtext_2,
                        subTextStrings[0],
                        subTextStrings[1])
                }
                1 -> {
                    subText = subTextStrings[0]
                }
            }

            val permissionUsagePreference = historyPreferenceFactory
                .createPermissionHistoryPreference(
                    HistoryPreferenceData(
                        UserHandle.getUserHandleForUid(usage.usageData.app.getUid()),
                        usage.usageData.app.packageName,
                        usage.usageData.app.icon,
                        usage.usageData.app.label,
                        filterGroup, accessTime, subText,
                        showingAttribution, accessTimeList,
                        usage.usageData.attributionTags,
                        usageNum == usages.size - 1,
                        sessionId)
                )
            category.get().addPreference(permissionUsagePreference)
        }
    }

    /**
     * Filter the usage data from [appPermissionUsage] into a list of [UsageData].
     */
    private fun filterAndConvert(
        appPermissionUsage: AppPermissionUsage,
        filterGroup: String
    ): List<UsageData> {
        if (shouldShowSubattributionForApp(appPermissionUsage)) {
            return appPermissionUsage.groupUsages
                .filter { groupUsage: GroupUsage -> groupUsage.group.name == filterGroup }
                .map(GroupUsage::getAttributionLabelledGroupUsages)
                .flatten()
                .map { labelledGroupUsage: AttributionLabelledGroupUsage ->
                    UsageData(filterGroup, appPermissionUsage.app,
                        listOf<TimelineUsage>(labelledGroupUsage),
                        labelledGroupUsage.label)
                }
        }
        val groupUsages = appPermissionUsage.groupUsages
            .filter { groupUsage: GroupUsage -> groupUsage.group.name == filterGroup }
        return listOf(
            UsageData(filterGroup, appPermissionUsage.app, groupUsages,
                Resources.ID_NULL)
        )
    }

    /**
     * Get an AppPermissionGroup that represents the given permission group (and an arbitrary app).
     *
     * @param groupName The name of the permission group.
     *
     * @return an AppPermissionGroup representing the given permission group or null if no such
     * AppPermissionGroup is found.
     */
    fun getGroup(
        groupName: String,
        appPermissionUsages: List<AppPermissionUsage>
    ): AppPermissionGroup? {
        val groups = getOSPermissionGroups(appPermissionUsages)
        return groups.firstOrNull { it.name == groupName }
    }

    /**
     * Get the permission groups declared by the OS.
     *
     * TODO: theianchen change the method name to make that clear,
     * and return a list of string group names, not AppPermissionGroups.
     * @return a list of the permission groups declared by the OS.
     */
    private fun getOSPermissionGroups(
        appPermissionUsages: List<AppPermissionUsage>
    ): List<AppPermissionGroup> {
        val groups: MutableList<AppPermissionGroup> = mutableListOf()
        val seenGroups: MutableSet<String> = mutableSetOf()
        for (appUsage in appPermissionUsages) {
            val groupUsages = appUsage.groupUsages
            for (groupUsage in groupUsages) {
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
     * Initialize the time filter to show the smallest entry greater than the time passed in as an
     * argument.  If nothing is passed, this simply initializes the possible values.
     */
    private fun initializeTimeFilter(context: Context) {
        filterTimes.add(
            TimeFilterItem(Long.MAX_VALUE,
            context.getString(R.string.permission_usage_any_time))
        )
        filterTimes.add(
            TimeFilterItem(DAYS.toMillis(7),
            context.getString(R.string.permission_usage_last_7_days))
        )
        filterTimes.add(
            TimeFilterItem(DAYS.toMillis(1),
            context.getString(R.string.permission_usage_last_day))
        )
        filterTimes.add(
            TimeFilterItem(TimeUnit.HOURS.toMillis(1),
            context.getString(R.string.permission_usage_last_hour))
        )
        filterTimes.add(
            TimeFilterItem(TimeUnit.MINUTES.toMillis(15),
            context.getString(R.string.permission_usage_last_15_minutes))
        )
        filterTimes.add(
            TimeFilterItem(TimeUnit.MINUTES.toMillis(1),
            context.getString(R.string.permission_usage_last_minute))
        )

        // TODO: theianchen add code for filtering by time here.
    }

    /**
     * Factory for creating preferences to be added to the screen.
     */
    interface HistoryPreferenceFactory {
        /**
         * Returns a new [PreferenceCategory] representing a day of permission usage.
         */
        fun createDayCategoryPreference(): PreferenceCategory

        /**
         * Returns a preference representing an app's permission usage, including its timestamp and
         * usage details.
         */
        fun createPermissionHistoryPreference(
            historyPreferenceData: HistoryPreferenceData
        ): Preference
    }

    /**
     * Data used to create a preference for an app's permission usage.
     */
    data class HistoryPreferenceData(
        val userHandle: UserHandle,
        val pkgName: String,
        val appIcon: Drawable?,
        val preferenceTitle: String,
        val permissionGroup: String,
        val accessTime: String,
        val summaryText: CharSequence?,
        val showingAttribution: Boolean,
        val accessTimeList: List<Long>,
        val attributionTags: ArrayList<String>,
        val isLastUsage: Boolean,
        val sessionId: Long
    )

    /**
     * A class representing a given time, e.g., "in the last hour".
     *
     * @param time the time represented by this object in milliseconds.
     * @param label the label to describe the timeframe
     */
    data class TimeFilterItem(
        val time: Long,
        val label: String
    )

    /** A class representing an app's usage for a group.  */
    data class UsageData(
        val group: String,
        // we need a PermissionApp because the loader takes the PermissionApp
        // object and loads the icon and label information asynchronously
        val app: PermissionApp,
        val timelineUsages: List<TimelineUsage>,
        val label: Int
    ) {
        val attributionTags: java.util.ArrayList<String>
            get() = timelineUsages.stream()
                .map { obj: TimelineUsage -> obj.attributionTags }
                .filter { obj: List<String>? -> Objects.nonNull(obj) }
                .flatMap { obj: List<String> -> obj.stream() }
                .collect(Collectors.toCollection { ArrayList() })
    }

    /**
     * A class representing an app usage entry in Permission Usage.
     */
    data class AppPermissionUsageEntry(
        val usageData: UsageData,
        val endTime: Long,
        val clusteredAccessTimeList: MutableList<Triple<Long, Long, AppOpsManager.OpEventProxyInfo>>
    )
}

/**
 * Factory for an [PermissionUsageDetailsViewModel]
 */
@RequiresApi(Build.VERSION_CODES.S)
class PermissionUsageDetailsViewModelFactory(
    private val application: Application,
    private val roleManager: RoleManager,
    private val filterGroup: String,
    private val sessionId: Long
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PermissionUsageDetailsViewModel(application, roleManager, filterGroup,
            sessionId) as T
    }
}

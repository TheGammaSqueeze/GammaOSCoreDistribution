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

package com.android.server.wm.traces.common.windowmanager

import com.android.server.wm.traces.common.ITraceEntry
import com.android.server.wm.traces.common.prettyTimestamp
import com.android.server.wm.traces.common.windowmanager.windows.Activity
import com.android.server.wm.traces.common.windowmanager.windows.DisplayContent
import com.android.server.wm.traces.common.windowmanager.windows.KeyguardControllerState
import com.android.server.wm.traces.common.windowmanager.windows.RootWindowContainer
import com.android.server.wm.traces.common.windowmanager.windows.Task
import com.android.server.wm.traces.common.windowmanager.windows.TaskFragment
import com.android.server.wm.traces.common.windowmanager.windows.WindowContainer
import com.android.server.wm.traces.common.windowmanager.windows.WindowManagerPolicy
import com.android.server.wm.traces.common.windowmanager.windows.WindowState

/**
 * Represents a single WindowManager trace entry.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot
 * access internal Java/Android functionality
 *
 * The timestamp constructor must be a string due to lack of Kotlin/KotlinJS Long compatibility
 *
 **/
open class WindowManagerState(
    val where: String,
    val policy: WindowManagerPolicy?,
    val focusedApp: String,
    val focusedDisplayId: Int,
    val focusedWindow: String,
    val inputMethodWindowAppToken: String,
    val isHomeRecentsComponent: Boolean,
    val isDisplayFrozen: Boolean,
    val pendingActivities: Array<String>,
    val root: RootWindowContainer,
    val keyguardControllerState: KeyguardControllerState,
    _timestamp: String = "0"
) : ITraceEntry {
    override val timestamp: Long = _timestamp.toLong()
    val isVisible: Boolean = true
    val stableId: String get() = this::class.simpleName ?: error("Unable to determine class")
    val name: String get() = prettyTimestamp(timestamp)

    val windowContainers: Array<WindowContainer>
        get() = root.collectDescendants()

    val children: Array<WindowContainer>
        get() = root.children.reversedArray()

    // Displays in z-order with the top most at the front of the list, starting with primary.
    val displays: Array<DisplayContent>
        get() = windowContainers.filterIsInstance<DisplayContent>().toTypedArray()

    // Root tasks in z-order with the top most at the front of the list, starting with primary display.
    val rootTasks: Array<Task>
        get() = displays.flatMap { it.rootTasks.toList() }.toTypedArray()

    // TaskFragments  in z-order with the top most at the front of the list.
    val taskFragments: Array<TaskFragment>
        get() = windowContainers.filterIsInstance<TaskFragment>().toTypedArray()

    // Windows in z-order with the top most at the front of the list.
    val windowStates: Array<WindowState>
        get() = windowContainers.filterIsInstance<WindowState>().toTypedArray()

    @Deprecated("Please use windowStates instead", replaceWith = ReplaceWith("windowStates"))
    val windows: Array<WindowState>
        get() = windowStates

    val appWindows: Array<WindowState>
        get() = windowStates.filter { it.isAppWindow }.toTypedArray()
    val nonAppWindows: Array<WindowState>
        get() = windowStates.filterNot { it.isAppWindow }.toTypedArray()
    val aboveAppWindows: Array<WindowState>
        get() = windowStates.takeWhile { !appWindows.contains(it) }.toTypedArray()
    val belowAppWindows: Array<WindowState>
        get() = windowStates
            .dropWhile { !appWindows.contains(it) }.drop(appWindows.size).toTypedArray()
    val visibleWindows: Array<WindowState>
        get() = windowStates
            .filter { it.isVisible }
            .filter { window ->
                val activities = getActivitiesForWindow(window.title)
                val activity = activities.firstOrNull { it.children.contains(window) }
                activity?.isVisible ?: true
            }
            .toTypedArray()
    val visibleAppWindows: Array<WindowState>
        get() = visibleWindows.filter { it.isAppWindow }.toTypedArray()
    val topVisibleAppWindow: String
        get() = visibleAppWindows
            .map { it.title }
            .firstOrNull() ?: ""
    val pinnedWindows: Array<WindowState>
        get() = visibleWindows
            .filter { it.windowingMode == WINDOWING_MODE_PINNED }
            .toTypedArray()

    /**
     * Checks if the device state supports rotation, i.e., if the rotation sensor is
     * enabled (e.g., launcher) and if the rotation not fixed
     */
    val canRotate: Boolean
        get() = policy?.isFixedOrientation != true && policy?.isOrientationNoSensor != true
    val focusedDisplay: DisplayContent? get() = getDisplay(focusedDisplayId)
    val focusedStackId: Int get() = focusedDisplay?.focusedRootTaskId ?: -1
    val focusedActivity: String get() {
        val focusedDisplay = focusedDisplay
        return if (focusedDisplay != null && focusedDisplay.resumedActivity.isNotEmpty()) {
            focusedDisplay.resumedActivity
        } else {
            getActivitiesForWindow(focusedWindow, focusedDisplayId).firstOrNull()?.name ?: ""
        }
    }
    val resumedActivities: Array<String>
        get() = rootTasks.flatMap { it.resumedActivities.toList() }.toTypedArray()
    val resumedActivitiesCount: Int get() = resumedActivities.size
    val stackCount: Int get() = rootTasks.size
    val homeTask: Task? get() = getStackByActivityType(ACTIVITY_TYPE_HOME)?.topTask
    val recentsTask: Task? get() = getStackByActivityType(ACTIVITY_TYPE_RECENTS)?.topTask
    val homeActivity: Activity? get() = homeTask?.activities?.lastOrNull()
    val recentsActivity: Activity? get() = recentsTask?.activities?.lastOrNull()
    val isRecentsActivityVisible: Boolean get() = recentsActivity?.isVisible ?: false
    val frontWindow: String? get() = windowStates.map { it.title }.firstOrNull()
    val inputMethodWindowState: WindowState?
        get() = getWindowStateForAppToken(inputMethodWindowAppToken)

    fun getDefaultDisplay(): DisplayContent? =
        displays.firstOrNull { it.id == DEFAULT_DISPLAY }

    fun getDisplay(displayId: Int): DisplayContent? =
        displays.firstOrNull { it.id == displayId }

    fun countStacks(windowingMode: Int, activityType: Int): Int {
        var count = 0
        for (stack in rootTasks) {
            if (activityType != ACTIVITY_TYPE_UNDEFINED && activityType != stack.activityType) {
                continue
            }
            if (windowingMode != WINDOWING_MODE_UNDEFINED && windowingMode != stack.windowingMode) {
                continue
            }
            ++count
        }
        return count
    }

    fun getRootTask(taskId: Int): Task? =
        rootTasks.firstOrNull { it.rootTaskId == taskId }

    fun getRotation(displayId: Int): Int =
            getDisplay(displayId)?.rotation ?: error("Default display not found")

    fun getOrientation(displayId: Int): Int =
            getDisplay(displayId)?.lastOrientation ?: error("Default display not found")

    fun getStackByActivityType(activityType: Int): Task? =
        rootTasks.firstOrNull { it.activityType == activityType }

    fun getStandardStackByWindowingMode(windowingMode: Int): Task? =
        rootTasks.firstOrNull {
            it.activityType == ACTIVITY_TYPE_STANDARD &&
                it.windowingMode == windowingMode
        }

    /**
     * Get the all activities on display with id [displayId], containing a window whose title
     * contains [partialWindowTitle]
     *
     * @param partialWindowTitle window title to search
     * @param displayId display where to search the activity
     */
    fun getActivitiesForWindow(
        partialWindowTitle: String,
        displayId: Int = DEFAULT_DISPLAY
    ): List<Activity> {
        return displays.firstOrNull { it.id == displayId }?.rootTasks?.mapNotNull { stack ->
            stack.getActivity { activity ->
                activity.hasWindow(partialWindowTitle)
            }
        } ?: emptyList()
    }

    fun containsActivity(activityName: String): Boolean =
        rootTasks.any { it.containsActivity(activityName) }

    fun isActivityVisible(activityName: String): Boolean {
        val activity = rootTasks.mapNotNull { it.getActivity(activityName) }.firstOrNull()
        return activity?.isVisible ?: false
    }

    fun hasActivityState(activityName: String, activityState: String): Boolean =
        rootTasks.any { it.getActivity(activityName)?.state == activityState }

    fun pendingActivityContain(activityName: String): Boolean {
        return pendingActivities.contains(activityName)
    }

    fun getMatchingVisibleWindowState(windowName: String): Array<WindowState> {
        return windowStates.filter { it.isSurfaceShown && it.title.contains(windowName) }
                .toTypedArray()
    }

    fun getNavBarWindow(displayId: Int): WindowState? {
        val navWindow = windowStates.filter { it.isValidNavBarType && it.displayId == displayId }

        // We may need some time to wait for nav bar showing.
        // It's Ok to get 0 nav bar here.
        if (navWindow.size > 1) {
            throw IllegalStateException("There should be at most one navigation bar on a display")
        }
        return navWindow.firstOrNull()
    }

    fun getWindowStateForAppToken(appToken: String): WindowState? =
        windowStates.firstOrNull { it.token == appToken }

    /**
     * Check if there exists a window record with matching windowName.
     */
    fun containsWindow(windowName: String): Boolean =
        windowStates.any { it.title.contains(windowName) }

    /**
     * Check if at least one window which matches the specified name has shown it's surface.
     */
    fun isWindowSurfaceShown(windowName: String): Boolean =
            getMatchingVisibleWindowState(windowName).isNotEmpty()

    /**
     * Check if at least one window which matches provided window name is visible.
     */
    fun isWindowVisible(windowName: String): Boolean =
        visibleWindows.any { it.title == windowName }

    /**
     * Checks if the state has any window in PIP mode
     */
    fun hasPipWindow(): Boolean = pinnedWindows.isNotEmpty()

    /**
     * Checks that an activity [windowName] is in PIP mode
     */
    fun isInPipMode(windowName: String): Boolean {
        return pinnedWindows.any { it.title.contains(windowName) }
    }

    fun getZOrder(w: WindowState): Int = windowStates.size - windowStates.indexOf(w)

    fun defaultMinimalTaskSize(displayId: Int): Int =
        dpToPx(DEFAULT_RESIZABLE_TASK_SIZE_DP.toFloat(), getDisplay(displayId)!!.dpi)

    fun defaultMinimalDisplaySizeForSplitScreen(displayId: Int): Int {
        return dpToPx(DEFAULT_MINIMAL_SPLIT_SCREEN_DISPLAY_SIZE_DP.toFloat(),
            getDisplay(displayId)!!.dpi)
    }

    fun getIsIncompleteReason(): String {
        return buildString {
            if (rootTasks.isEmpty()) {
                append("No stacks found...")
            }
            if (focusedStackId == -1) {
                append("No focused stack found...")
            }
            if (focusedActivity.isEmpty()) {
                append("No focused activity found...")
            }
            if (resumedActivities.isEmpty()) {
                append("No resumed activities found...")
            }
            if (windowStates.isEmpty()) {
                append("No Windows found...")
            }
            if (focusedWindow.isEmpty()) {
                append("No Focused Window...")
            }
            if (focusedApp.isEmpty()) {
                append("No Focused App...")
            }
            if (keyguardControllerState.isKeyguardShowing) {
                append("Keyguard showing...")
            }
        }
    }

    fun isComplete(): Boolean = !isIncomplete()
    fun isIncomplete(): Boolean {
        return rootTasks.isEmpty() || focusedStackId == -1 || windowStates.isEmpty() ||
            // overview screen has no focused window
            ((focusedApp.isEmpty() || focusedWindow.isEmpty()) && homeActivity == null) ||
            (focusedActivity.isEmpty() || resumedActivities.isEmpty()) &&
            !keyguardControllerState.isKeyguardShowing
    }

    fun asTrace(): WindowManagerTrace = WindowManagerTrace(arrayOf(this))

    override fun toString(): String {
        return "${prettyTimestamp(timestamp)} (timestamp=$timestamp)"
    }

    companion object {
        const val STATE_INITIALIZING = "INITIALIZING"
        const val STATE_RESUMED = "RESUMED"
        const val STATE_PAUSED = "PAUSED"
        const val STATE_STOPPED = "STOPPED"
        const val STATE_DESTROYED = "DESTROYED"
        const val APP_STATE_IDLE = "APP_STATE_IDLE"
        internal const val ACTIVITY_TYPE_UNDEFINED = 0
        internal const val ACTIVITY_TYPE_STANDARD = 1
        internal const val DEFAULT_DISPLAY = 0
        internal const val DEFAULT_MINIMAL_SPLIT_SCREEN_DISPLAY_SIZE_DP = 440
        internal const val ACTIVITY_TYPE_HOME = 2
        internal const val ACTIVITY_TYPE_RECENTS = 3
        internal const val WINDOWING_MODE_UNDEFINED = 0
        private const val DENSITY_DEFAULT = 160
        /**
         * @see android.app.WindowConfiguration.WINDOWING_MODE_PINNED
         */
        private const val WINDOWING_MODE_PINNED = 2

        /**
         * @see android.view.WindowManager.LayoutParams
         */
        internal const val TYPE_NAVIGATION_BAR_PANEL = 2024

        // Default minimal size of resizable task, used if none is set explicitly.
        // Must be kept in sync with 'default_minimal_size_resizable_task'
        // dimen from frameworks/base.
        internal const val DEFAULT_RESIZABLE_TASK_SIZE_DP = 220

        fun dpToPx(dp: Float, densityDpi: Int): Int {
            return (dp * densityDpi / DENSITY_DEFAULT + 0.5f).toInt()
        }
    }
    override fun equals(other: Any?): Boolean {
        return other is WindowManagerState && other.timestamp == this.timestamp
    }
}

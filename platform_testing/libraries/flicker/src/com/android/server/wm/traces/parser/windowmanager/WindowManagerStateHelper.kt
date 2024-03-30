/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wm.traces.parser.windowmanager

import android.app.ActivityTaskManager
import android.app.Instrumentation
import android.app.WindowConfiguration
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.wm.traces.common.windowmanager.windows.ConfigurationContainer
import com.android.server.wm.traces.common.windowmanager.windows.WindowContainer
import com.android.server.wm.traces.common.windowmanager.windows.WindowState
import com.android.server.wm.traces.common.Condition
import com.android.server.wm.traces.common.ConditionList
import com.android.server.wm.traces.common.DeviceStateDump
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.FlickerComponentName.Companion.IME
import com.android.server.wm.traces.common.FlickerComponentName.Companion.SNAPSHOT
import com.android.server.wm.traces.parser.LOG_TAG
import com.android.server.wm.traces.common.WaitCondition
import com.android.server.wm.traces.common.layers.BaseLayerTraceEntry
import com.android.server.wm.traces.common.WindowManagerConditionsFactory
import com.android.server.wm.traces.common.region.Region
import com.android.server.wm.traces.common.windowmanager.WindowManagerState
import com.android.server.wm.traces.parser.getCurrentStateDump

open class WindowManagerStateHelper @JvmOverloads constructor(
    /**
     * Instrumentation to run the tests
     */
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    /**
     * Predicate to supply a new UI information
     */
    private val deviceDumpSupplier:
        () -> DeviceStateDump<WindowManagerState, BaseLayerTraceEntry> =
            {
            val currState = getCurrentStateDump(instrumentation.uiAutomation)
            DeviceStateDump(
                currState.wmState ?: error("Unable to parse WM trace"),
                currState.layerState ?: error("Unable to parse Layers trace")
            )
        },
    /**
     * Number of attempts to satisfy a wait condition
     */
    private val numRetries: Int = WaitCondition.DEFAULT_RETRY_LIMIT,
    /**
     * Interval between wait for state dumps during wait conditions
     */
    private val retryIntervalMs: Long = WaitCondition.DEFAULT_RETRY_INTERVAL_MS
) {
    private var internalState: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>? = null

    /**
     * Queries the supplier for a new device state
     */
    val currentState: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        get() {
            if (internalState == null) {
                internalState = deviceDumpSupplier.invoke()
            } else {
                waitForValidState()
            }
            return internalState ?: error("Unable to fetch an internal state")
        }

    protected open fun updateCurrState(
        value: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
    ) {
        internalState = value
    }

    private fun createConditionBuilder():
        WaitCondition.Builder<DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>> =
        WaitCondition.Builder(deviceDumpSupplier, numRetries)
            .onSuccess { updateCurrState(it) }
            .onFailure { updateCurrState(it) }
            .onLog { msg, isError -> if (isError) Log.e(LOG_TAG, msg) else Log.d(LOG_TAG, msg) }
            .onRetry { SystemClock.sleep(retryIntervalMs) }

    /**
     * Wait for the activities to appear in proper stacks and for valid state in AM and WM.
     * @param waitForActivityState array of activity states to wait for.
     */
    private fun waitForValidState(vararg waitForActivityState: WaitForValidActivityState) =
        waitFor(waitForValidStateCondition(*waitForActivityState))

    fun waitForFullScreenApp(component: FlickerComponentName) =
        require(
        waitFor(isAppFullScreen(component), snapshotGoneCondition)) {
        "Expected ${component.toWindowName()} to be in full screen"
    }

    fun waitForHomeActivityVisible() = require(waitFor(isHomeActivityVisible)) {
        "Expected home activity to be visible"
    }

    fun waitForRecentsActivityVisible() = require(
        waitFor("isRecentsActivityVisible") { it.wmState.isRecentsActivityVisible }) {
        "Expected recents activity to be visible"
    }

    /**
     * Wait for specific rotation for the default display. Values are Surface#Rotation
     */
    @JvmOverloads
    fun waitForRotation(rotation: Int, displayId: Int = Display.DEFAULT_DISPLAY) {
        val hasRotationCondition = WindowManagerConditionsFactory.hasRotation(rotation, displayId)
        val result = waitFor(
            WindowManagerConditionsFactory.isAppTransitionIdle(Display.DEFAULT_DISPLAY),
            Condition("waitForRotation[$rotation]") {
                if (!it.wmState.canRotate) {
                    Log.v(LOG_TAG, "Rotation is not allowed in the state")
                    true
                } else {
                    hasRotationCondition.isSatisfied(it)
                }
            })
        require(result) { "Could not change rotation" }
    }

    fun waitForActivityState(activity: FlickerComponentName, activityState: String): Boolean {
        val activityName = activity.toActivityName()
        return waitFor("state of $activityName to be $activityState") {
            it.wmState.hasActivityState(activityName, activityState)
        }
    }

    /**
     * Waits until the navigation and status bars are visible (windows and layers)
     */
    fun waitForNavBarStatusBarVisible(): Boolean =
        waitFor(
            WindowManagerConditionsFactory.isNavBarVisible(),
            WindowManagerConditionsFactory.isStatusBarVisible())

    fun waitForVisibleWindow(component: FlickerComponentName) = require(
        waitFor(
            WindowManagerConditionsFactory.isWindowVisible(component),
            WindowManagerConditionsFactory.isAppTransitionIdle(Display.DEFAULT_DISPLAY))) {
        "Expected window ${component.toWindowName()} to be visible"
    }

    fun waitForActivityRemoved(component: FlickerComponentName) = require(
        waitFor(
            WindowManagerConditionsFactory.containsActivity(component).negate(),
            WindowManagerConditionsFactory.containsWindow(component).negate(),
            WindowManagerConditionsFactory.isAppTransitionIdle(Display.DEFAULT_DISPLAY))) {
        "Expected activity ${component.toWindowName()} to have been removed"
    }

    @JvmOverloads
    fun waitForAppTransitionIdle(displayId: Int = Display.DEFAULT_DISPLAY): Boolean =
        waitFor(WindowManagerConditionsFactory.isAppTransitionIdle(displayId))

    fun waitForWindowSurfaceDisappeared(component: FlickerComponentName) = require(
        waitFor(
            WindowManagerConditionsFactory.isWindowSurfaceShown(component).negate(),
            WindowManagerConditionsFactory.isAppTransitionIdle(Display.DEFAULT_DISPLAY))) {
        "Expected surface ${component.toLayerName()} to disappear"
    }

    fun waitForSurfaceAppeared(component: FlickerComponentName) = require(
        waitFor(
            WindowManagerConditionsFactory.isWindowSurfaceShown(component),
            WindowManagerConditionsFactory.isAppTransitionIdle(Display.DEFAULT_DISPLAY))) {
        "Expected surface ${component.toLayerName()} to appear"
    }

    fun waitFor(
        vararg conditions: Condition<DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>>
    ): Boolean {
        val builder = createConditionBuilder()
        conditions.forEach { builder.withCondition(it) }
        return builder.build().waitFor()
    }

    @JvmOverloads
    fun waitFor(
        message: String = "",
        waitCondition: (DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>) -> Boolean
    ): Boolean = waitFor(Condition(message, waitCondition))

    /**
     * Waits until the IME window and layer are visible
     */
    fun waitImeShown() = require(waitFor(imeShownCondition)) { "Expected IME to be visible" }

    /**
     * Waits until the IME layer is no longer visible. Cannot wait for the window as
     * its visibility information is updated at a later state and is not reliable in
     * the trace
     */
    fun waitImeGone() = require(waitFor(imeGoneCondition)) { "Expected IME not to be visible" }

    /**
     * Waits until a window is in PIP mode. That is:
     *
     * - wait until a window is pinned ([WindowManagerState.pinnedWindows])
     * - no layers animating
     * - and [FlickerComponentName.PIP_CONTENT_OVERLAY] is no longer visible
     */
    fun waitPipShown() = require(waitFor(pipShownCondition)) { "Expected PIP window to be visible" }

    /**
     * Waits until a window is no longer in PIP mode. That is:
     *
     * - wait until there are no pinned ([WindowManagerState.pinnedWindows])
     * - no layers animating
     * - and [FlickerComponentName.PIP_CONTENT_OVERLAY] is no longer visible
     */
    fun waitPipGone() = require(waitFor(pipGoneCondition)) { "Expected PIP window to be gone" }

    /**
     * Obtains a [WindowContainer] from the current device state, or null if the WindowContainer
     * doesn't exist
     */
    fun getWindow(activity: FlickerComponentName): WindowState? {
        val windowName = activity.toWindowName()
        return this.currentState.wmState.windowStates
            .firstOrNull { it.title == windowName }
    }

    /**
     * Obtains the region of a window in the state, or an empty [Rect] is there are none
     */
    fun getWindowRegion(activity: FlickerComponentName): Region {
        val window = getWindow(activity)
        return window?.frameRegion ?: Region.EMPTY
    }

    companion object {
        @JvmStatic
        val isHomeActivityVisible = ConditionList(
            WindowManagerConditionsFactory.isHomeActivityVisible(),
            WindowManagerConditionsFactory.isAppTransitionIdle(Display.DEFAULT_DISPLAY),
            WindowManagerConditionsFactory.isNavBarVisible(),
            WindowManagerConditionsFactory.isStatusBarVisible())

        @JvmStatic
        val imeGoneCondition = ConditionList(
            WindowManagerConditionsFactory.isLayerVisible(IME).negate(),
            WindowManagerConditionsFactory.isAppTransitionIdle(Display.DEFAULT_DISPLAY))

        @JvmStatic
        val imeShownCondition = ConditionList(
            WindowManagerConditionsFactory.isImeShown(Display.DEFAULT_DISPLAY),
            WindowManagerConditionsFactory.isAppTransitionIdle(Display.DEFAULT_DISPLAY)
        )

        @JvmStatic
        val snapshotGoneCondition = ConditionList(
                WindowManagerConditionsFactory.isLayerVisible(SNAPSHOT).negate(),
                WindowManagerConditionsFactory.isAppTransitionIdle(Display.DEFAULT_DISPLAY))

        @JvmStatic
        val pipShownCondition = ConditionList(
            WindowManagerConditionsFactory.hasLayersAnimating().negate(),
            WindowManagerConditionsFactory.hasPipWindow(),
            WindowManagerConditionsFactory.isAppTransitionIdle(Display.DEFAULT_DISPLAY))

        @JvmStatic
        val pipGoneCondition = ConditionList(
            WindowManagerConditionsFactory.hasLayersAnimating().negate(),
            WindowManagerConditionsFactory.hasPipWindow().negate(),
            WindowManagerConditionsFactory.isAppTransitionIdle(Display.DEFAULT_DISPLAY))

        fun waitForValidStateCondition(
            vararg waitForActivitiesVisible: WaitForValidActivityState
        ): Condition<DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>> {
            val conditions = mutableListOf(WindowManagerConditionsFactory.isWMStateComplete())

            if (waitForActivitiesVisible.isNotEmpty()) {
                conditions.add(Condition("!shouldWaitForActivities") {
                    !shouldWaitForActivities(it, *waitForActivitiesVisible)
                })
            }

            return ConditionList(*conditions.toTypedArray())
        }

        fun isAppFullScreen(component: FlickerComponentName) =
            waitForValidStateCondition(WaitForValidActivityState
                .Builder(component)
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN)
                .setActivityType(WindowConfiguration.ACTIVITY_TYPE_STANDARD)
                .build()
            )

        /**
         * @return true if should wait for some activities to become visible.
         */
        private fun shouldWaitForActivities(
            state: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            vararg waitForActivitiesVisible: WaitForValidActivityState
        ): Boolean {
            if (waitForActivitiesVisible.isEmpty()) {
                return false
            }
            // If the caller is interested in waiting for some particular activity windows to be
            // visible before compute the state. Check for the visibility of those activity windows
            // and for placing them in correct stacks (if requested).
            var allActivityWindowsVisible = true
            var tasksInCorrectStacks = true
            for (activityState in waitForActivitiesVisible) {
                val matchingWindowStates = state.wmState.getMatchingVisibleWindowState(
                    activityState.windowName ?: "")
                val activityWindowVisible = matchingWindowStates.isNotEmpty()

                if (!activityWindowVisible) {
                    Log.i(LOG_TAG, "Activity window not visible: ${activityState.windowName}")
                    allActivityWindowsVisible = false
                } else if (activityState.activityName != null &&
                    !state.wmState.isActivityVisible(activityState.activityName.toActivityName())) {
                    Log.i(LOG_TAG, "Activity not visible: ${activityState.activityName}")
                    allActivityWindowsVisible = false
                } else {
                    // Check if window is already the correct state requested by test.
                    var windowInCorrectState = false
                    for (ws in matchingWindowStates) {
                        if (activityState.stackId != ActivityTaskManager.INVALID_STACK_ID &&
                            ws.stackId != activityState.stackId) {
                            continue
                        }
                        if (!ws.isWindowingModeCompatible(activityState.windowingMode)) {
                            continue
                        }
                        if (activityState.activityType !=
                                WindowConfiguration.ACTIVITY_TYPE_UNDEFINED &&
                            ws.activityType != activityState.activityType) {
                            continue
                        }
                        windowInCorrectState = true
                        break
                    }
                    if (!windowInCorrectState) {
                        Log.i(LOG_TAG, "Window in incorrect stack: $activityState")
                        tasksInCorrectStacks = false
                    }
                }
            }
            return !allActivityWindowsVisible || !tasksInCorrectStacks
        }

        private fun ConfigurationContainer.isWindowingModeCompatible(
            requestedWindowingMode: Int
        ): Boolean {
            return when (requestedWindowingMode) {
                WindowConfiguration.WINDOWING_MODE_UNDEFINED -> true
                else -> windowingMode == requestedWindowingMode
            }
        }
    }
}

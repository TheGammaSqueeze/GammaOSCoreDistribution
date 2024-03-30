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

package com.android.server.wm.flicker.traces.windowmanager

import android.view.Display
import androidx.annotation.VisibleForTesting
import com.android.server.wm.flicker.assertions.Assertion
import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.flicker.traces.FlickerFailureStrategy
import com.android.server.wm.flicker.traces.region.RegionSubject
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.region.Region
import com.android.server.wm.traces.common.windowmanager.WindowManagerState
import com.android.server.wm.traces.common.windowmanager.windows.Activity
import com.android.server.wm.traces.common.windowmanager.windows.WindowState
import com.google.common.truth.Fact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.FailureStrategy
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Subject
import com.google.common.truth.Subject.Factory

/**
 * Truth subject for [WindowManagerState] objects, used to make assertions over behaviors that
 * occur on a single WM state.
 *
 * To make assertions over a specific state from a trace it is recommended to create a subject
 * using [WindowManagerTraceSubject.assertThat](myTrace) and select the specific state using:
 *     [WindowManagerTraceSubject.first]
 *     [WindowManagerTraceSubject.last]
 *     [WindowManagerTraceSubject.entry]
 *
 * Alternatively, it is also possible to use [WindowManagerStateSubject.assertThat](myState) or
 * Truth.assertAbout([WindowManagerStateSubject.getFactory]), however they will provide less debug
 * information because it uses Truth's default [FailureStrategy].
 *
 * Example:
 *    val trace = WindowManagerTraceParser.parseFromTrace(myTraceFile)
 *    val subject = WindowManagerTraceSubject.assertThat(trace).first()
 *        .contains("ValidWindow")
 *        .notContains("ImaginaryWindow")
 *        .showsAboveAppWindow("NavigationBar")
 *        .invoke { myCustomAssertion(this) }
 */
class WindowManagerStateSubject private constructor(
    fm: FailureMetadata,
    val wmState: WindowManagerState,
    val trace: WindowManagerTraceSubject?,
    override val parent: FlickerSubject?
) : FlickerSubject(fm, wmState) {
    override val timestamp: Long get() = wmState.timestamp
    override val selfFacts = listOf(Fact.fact("Entry", wmState))

    val subjects by lazy {
        wmState.windowStates.map { WindowStateSubject.assertThat(it, this, timestamp) }
    }

    val appWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.appWindows.contains(it.windowState) }

    val nonAppWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.nonAppWindows.contains(it.windowState) }

    val aboveAppWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.aboveAppWindows.contains(it.windowState) }

    val belowAppWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.belowAppWindows.contains(it.windowState) }

    val visibleWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.visibleWindows.contains(it.windowState) }

    val visibleAppWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.visibleAppWindows.contains(it.windowState) }

    /**
     * Executes a custom [assertion] on the current subject
     */
    operator fun invoke(assertion: Assertion<WindowManagerState>): WindowManagerStateSubject =
        apply { assertion(this.wmState) }

    /**
     * Asserts the current WindowManager state doesn't contain [WindowState]s
     */
    fun isEmpty(): WindowManagerStateSubject = apply {
        check("State is empty").that(subjects).isEmpty()
    }

    /**
     * Asserts the current WindowManager state contains [WindowState]s
     */
    fun isNotEmpty(): WindowManagerStateSubject = apply {
        check("State is not empty").that(subjects).isNotEmpty()
    }

    /**
     * Obtains the region occupied by all windows with name containing any of [components]
     * alias of [visibleRegion]
     *
     * @param components Components to search
     * @deprecated using [visibleRegion] is preferred
     */
    fun frameRegion(vararg components: FlickerComponentName): RegionSubject {
        return visibleRegion(*components)
    }

    /**
     * Obtains the region occupied by all windows with name containing any of [components]
     *
     * @param components Components to search
     */
    fun visibleRegion(vararg components: FlickerComponentName): RegionSubject {
        val windowNames = components.map { it.toWindowName() }

        val selectedWindows = if (components.isEmpty()) {
            // No filters so use all subjects
            subjects
        } else {
            subjects.filter {
                subject -> windowNames.any {
                    layerName -> subject.name.contains(layerName)
                }
            }
        }

        if (selectedWindows.isEmpty()) {
            val str = if (windowNames.isNotEmpty()) windowNames.joinToString() else "<any>"
            fail(Fact.fact(ASSERTION_TAG, "visibleRegion($str)"),
                    Fact.fact("Could not find windows", str))
        }

        val visibleWindows = selectedWindows.filter { it.isVisible }
        val visibleRegions = visibleWindows
                .mapNotNull { it.windowState?.frameRegion }.toTypedArray()
        return RegionSubject.assertThat(visibleRegions, this, timestamp)
    }

    /**
     * Asserts the state contains a [WindowState] with title matching [component] above the
     * app windows
     *
     * @param component Component to search
     */
    fun containsAboveAppWindow(component: FlickerComponentName): WindowManagerStateSubject = apply {
        contains(aboveAppWindows, component)
    }

    /**
     * Asserts the state contains a [WindowState] with title matching [component] below the
     * app windows
     *
     * @param component Component to search
     */
    fun containsBelowAppWindow(component: FlickerComponentName): WindowManagerStateSubject = apply {
        contains(belowAppWindows, component)
    }

    /**
     * Asserts the state contains [WindowState]s with titles matching [aboveWindowComponent] and
     * [belowWindowComponent], and that [aboveWindowComponent] is above [belowWindowComponent]
     *
     * This assertion can be used, for example, to assert that a PIP window is shown above
     * other apps.
     *
     * @param aboveWindowComponent name of the window that should be above
     * @param belowWindowComponent name of the window that should be below
     */
    fun isAboveWindow(
        aboveWindowComponent: FlickerComponentName,
        belowWindowComponent: FlickerComponentName
    ): WindowManagerStateSubject = apply {
        contains(aboveWindowComponent)
        contains(belowWindowComponent)

        // windows are ordered by z-order, from top to bottom
        val aboveWindowTitle = aboveWindowComponent.toWindowName()
        val belowWindowTitle = belowWindowComponent.toWindowName()
        val aboveZ = wmState.windowStates.indexOfFirst { aboveWindowTitle in it.name }
        val belowZ = wmState.windowStates.indexOfFirst { belowWindowTitle in it.name }
        if (aboveZ >= belowZ) {
            val aboveWindow = subjects.first { aboveWindowTitle in it.name }
            aboveWindow.fail(Fact.fact(ASSERTION_TAG, "isAboveWindow(above=$aboveWindowTitle, " +
                "below=$belowWindowTitle"),
                Fact.fact("Above", aboveWindowTitle),
                Fact.fact("Below", belowWindowTitle))
        }
    }

    /**
     * Asserts the state contains a non-app [WindowState] with title matching [component]
     *
     * @param component Component to search
     */
    fun containsNonAppWindow(component: FlickerComponentName): WindowManagerStateSubject = apply {
        contains(nonAppWindows, component)
    }

    /**
     * Asserts the title of the top visible app window in the state contains [component]
     *
     * @param component Component to search
     */
    fun isAppWindowOnTop(component: FlickerComponentName): WindowManagerStateSubject = apply {
        val windowName = component.toWindowName()
        if (wmState.visibleAppWindows.isEmpty()) {
            fail(
                Fact.fact(ASSERTION_TAG, "isAppWindowOnTop(${component.toWindowName()})"),
                Fact.fact("Not found", "No visible app windows found")
            )
        }
        if (!wmState.topVisibleAppWindow.contains(windowName)) {
            isNotEmpty()
            val topWindow = subjects.first { it.name == wmState.topVisibleAppWindow }
            topWindow.fail(
                Fact.fact(ASSERTION_TAG, "isAppWindowOnTop(${component.toWindowName()})"),
                Fact.fact("Not on top", component.toWindowName()),
                Fact.fact("Found", wmState.topVisibleAppWindow)
            )
        }
    }

    /**
     * Asserts the title of the top visible app window in the state contains [component]
     *
     * @param component Component to search
     */
    fun isAppWindowNotOnTop(component: FlickerComponentName): WindowManagerStateSubject = apply {
        val windowName = component.toWindowName()
        if (wmState.topVisibleAppWindow.contains(windowName)) {
            val topWindow = subjects.first { it.name == wmState.topVisibleAppWindow }
            topWindow.fail(
                Fact.fact(ASSERTION_TAG, "isAppWindowNotOnTop(${component.toWindowName()})"),
                Fact.fact("On top", component.toWindowName())
            )
        }
    }

    /**
     * Asserts the bounds of the [WindowState]s title matching [component] don't overlap.
     *
     * @param component Component to search
     */
    fun doNotOverlap(
        vararg component: FlickerComponentName
    ): WindowManagerStateSubject = apply {
        component.forEach { contains(it) }
        val foundWindows = component.toSet()
            .associateWith { act ->
                wmState.windowStates.firstOrNull { it.name.contains(act.toWindowName()) }
            }
            // keep entries only for windows that we actually found by removing nulls
            .filterValues { it != null }
        val foundWindowsRegions = foundWindows
            .mapValues { (_, v) -> v?.frameRegion ?: Region.EMPTY }

        val regions = foundWindowsRegions.entries.toList()
        for (i in regions.indices) {
            val (ourTitle, ourRegion) = regions[i]
            for (j in i + 1 until regions.size) {
                val (otherTitle, otherRegion) = regions[j]
                if (ourRegion.op(otherRegion,
                        Region.Op.INTERSECT)) {
                    val window = foundWindows[ourTitle] ?: error("Window $ourTitle not found")
                    val windowSubject = subjects.first { it.windowState == window }
                    windowSubject.fail(Fact.fact(ASSERTION_TAG,
                            "noWindowsOverlap${component.joinToString { it.toWindowName() }}"),
                        Fact.fact("Overlap", ourTitle),
                        Fact.fact("Overlap", otherTitle))
                }
            }
        }
    }

    /**
     * Asserts the state contains an app [WindowState] with title matching [component]
     *
     * @param component Component to search
     */
    fun containsAppWindow(component: FlickerComponentName): WindowManagerStateSubject = apply {
        val windowName = component.toWindowName()
        // Check existence of activity
        val activity = wmState.getActivitiesForWindow(windowName).firstOrNull()
        check("Activity for window $windowName must exist.")
            .that(activity).isNotNull()
        // Check existence of window.
        contains(component)
    }

    /**
     * Asserts the display with id [displayId] has rotation [rotation]
     *
     * @param rotation to assert
     * @param displayId of the target display
     */
    @JvmOverloads
    fun hasRotation(
        rotation: Int,
        displayId: Int = Display.DEFAULT_DISPLAY
    ): WindowManagerStateSubject = apply {
        check("Rotation should be $rotation")
            .that(rotation)
            .isEqualTo(wmState.getRotation(displayId))
    }

    /**
     * Asserts the state contains a [WindowState] with title matching [component].
     *
     * @param component Component name to search
     */
    fun contains(component: FlickerComponentName): WindowManagerStateSubject = apply {
        contains(subjects, component)
    }

    /**
     * Asserts the state doesn't contain a [WindowState] nor an [Activity] with title
     * matching [component].
     *
     * @param component Component name to search
     */
    fun notContainsAppWindow(component: FlickerComponentName): WindowManagerStateSubject = apply {
        val activityName = component.toActivityName()
        // system components (e.g., NavBar, StatusBar, PipOverlay) don't have a package name
        // nor an activity, ignore them
        check("Activity=$activityName must NOT exist.")
            .that(wmState.containsActivity(activityName)).isFalse()
        notContains(component)
    }

    /**
     * Asserts the state doesn't contain a [WindowState] with title matching [component].
     *
     * @param component Component name to search
     */
    fun notContains(component: FlickerComponentName): WindowManagerStateSubject = apply {
        val windowName = component.toWindowName()
        check("Window=$windowName must NOT exits.")
            .that(wmState.containsWindow(windowName)).isFalse()
    }

    fun isRecentsActivityVisible(): WindowManagerStateSubject = apply {
        if (wmState.isHomeRecentsComponent) {
            isHomeActivityVisible()
        } else {
            check("Recents activity visibility")
                .that(wmState.isRecentsActivityVisible)
                .isTrue()
        }
    }

    fun isRecentsActivityInvisible(): WindowManagerStateSubject = apply {
        if (wmState.isHomeRecentsComponent) {
            isHomeActivityInvisible()
        } else {
            check("Recents activity visibility")
                .that(wmState.isRecentsActivityVisible)
                .isFalse()
        }
    }

    /**
     * Asserts the state is valid, that is, if it has:
     *   - a resumed activity
     *   - a focused activity
     *   - a focused window
     *   - a front window
     *   - a focused app
     */
    @VisibleForTesting
    fun isValid(): WindowManagerStateSubject = apply {
        check("Must have stacks").that(wmState.stackCount).isGreaterThan(0)
        // TODO: Update when keyguard will be shown on multiple displays
        if (!wmState.keyguardControllerState.isKeyguardShowing) {
            check("There should be at least one resumed activity in the system.")
                .that(wmState.resumedActivitiesCount).isGreaterThan(0)
        }
        check("Must have focus activity.")
            .that(wmState.focusedActivity).isNotEmpty()
        wmState.rootTasks.forEach { aStack ->
            val stackId = aStack.rootTaskId
            aStack.tasks.forEach { aTask ->
                check("Stack can only contain its own tasks")
                    .that(stackId).isEqualTo(aTask.rootTaskId)
            }
        }
        check("Must have front window.")
            .that(wmState.frontWindow).isNotEmpty()
        check("Must have focused window.")
            .that(wmState.focusedWindow).isNotEmpty()
        check("Must have app.")
            .that(wmState.focusedApp).isNotEmpty()
    }

    /**
     * Asserts the state contains a visible window with [WindowState.title] matching [component].
     *
     * Also, if [component] has a package name (i.e., is not a system component), also checks that
     * it contains a visible [Activity] with [Activity.title] matching [component].
     *
     * @param component Component name to search
     */
    fun isNonAppWindowVisible(component: FlickerComponentName): WindowManagerStateSubject = apply {
        checkWindowVisibility("isVisible", nonAppWindows, component, isVisible = true)
    }

    /**
     * Asserts the state contains a visible window with [WindowState.title] matching [component].
     *
     * Also, if [component] has a package name (i.e., is not a system component), also checks that
     * it contains a visible [Activity] with [Activity.title] matching [component].
     *
     * @param component Component name to search
     */
    fun isAppWindowVisible(
        component: FlickerComponentName
    ): WindowManagerStateSubject = apply {
        containsAppWindow(component)

        val windowName = component.toWindowName()
        // Check existence of activity
        val activity = wmState.getActivitiesForWindow(windowName).firstOrNull()
        // Check visibility of activity and window.
        check("Activity=${activity?.name} must be visible.")
            .that(activity?.isVisible ?: false).isTrue()
        checkWindowVisibility("isVisible", appWindows, component, isVisible = true)
    }

    /**
     * Asserts the state contains no visible app windows.
     */
    fun hasNoVisibleAppWindow() {
        if (visibleAppWindows.isNotEmpty()) {
            val visibleAppWindows = visibleAppWindows.joinToString { it.name }
            fail(
                Fact.fact(ASSERTION_TAG, "hasNoVisibleAppWindow()"),
                Fact.fact("Found visible windows", visibleAppWindows)
            )
        }
    }

    /**
     * Asserts the state contains an invisible window with [WindowState.title] matching [component].
     *
     * Also, if [component] has a package name (i.e., is not a system component), also checks that
     * it contains an invisible [Activity] with [Activity.title] matching [component].
     *
     * @param component Component name to search
     */
    fun isAppWindowInvisible(
        component: FlickerComponentName
    ): WindowManagerStateSubject = apply {
        val activityName = component.toActivityName()

        // system components (e.g., NavBar, StatusBar, PipOverlay) don't have a package name
        // nor an activity, ignore them
        // activity is visible, check window
        if (wmState.isActivityVisible(activityName)) {
            checkWindowVisibility("isInvisible", appWindows, component, isVisible = false)
        }
    }

    /**
     * Asserts the state contains an invisible window with [WindowState.title] matching [component].
     *
     * Also, if [component] has a package name (i.e., is not a system component), also checks that
     * it contains an invisible [Activity] with [Activity.title] matching [component].
     *
     * @param component Component name to search
     */
    fun isNonAppWindowInvisible(
        component: FlickerComponentName
    ): WindowManagerStateSubject = apply {
        checkWindowVisibility("isInvisible", nonAppWindows, component, isVisible = false)
    }

    private fun checkWindowVisibility(
        assertionName: String,
        subjectList: List<WindowStateSubject>,
        component: FlickerComponentName,
        isVisible: Boolean
    ) {
        // Check existence of window.
        contains(subjectList, component)

        val windowName = component.toWindowName()
        val foundWindows = subjectList.filter { it.name.contains(windowName) }
        val windowsWithVisibility = foundWindows.filter { it.isVisible == isVisible }

        if (windowsWithVisibility.isEmpty()) {
            val errorTag = if (isVisible) "Is Invisible" else "Is Visible"
            val facts = listOf<Fact>(
                Fact.fact(ASSERTION_TAG, "$assertionName(${component.toWindowName()})"),
                Fact.fact(errorTag, windowName)
            )
            foundWindows.first().fail(facts)
        }
    }

    private fun contains(subjectList: List<WindowStateSubject>, component: FlickerComponentName) {
        val windowName = component.toWindowName()
        check("Window=$windowName must exist.")
            .that(wmState.containsWindow(windowName)).isTrue()
    }

    /**
     * Asserts the state home activity is visible
     */
    fun isHomeActivityVisible(): WindowManagerStateSubject = apply {
        val homeIsVisible = wmState.homeActivity?.isVisible ?: false
        check("Home activity doesn't exist").that(wmState.homeActivity).isNotNull()
        check("Home activity is not visible").that(homeIsVisible).isTrue()
    }

    /**
     * Asserts the state home activity is invisible
     */
    fun isHomeActivityInvisible(): WindowManagerStateSubject = apply {
        val homeIsVisible = wmState.homeActivity?.isVisible ?: false
        check("Home activity is visible").that(homeIsVisible).isFalse()
    }

    /**
     * Asserts that [component] exists and is pinned (in PIP mode)
     *
     * @param component Component name to search
     */
    fun isPinned(component: FlickerComponentName): WindowManagerStateSubject = apply {
        contains(component)
        val windowName = component.toWindowName()
        val pinnedWindows = wmState.pinnedWindows.map { it.title }
        check("Window not in PIP mode").that(pinnedWindows).contains(windowName)
    }

    /**
     * Asserts that [component] exists and is not pinned (not in PIP mode)
     *
     * @param component Component name to search
     */
    fun isNotPinned(component: FlickerComponentName): WindowManagerStateSubject = apply {
        contains(component)
        val windowName = component.toWindowName()
        val pinnedWindows = wmState.pinnedWindows.map { it.title }
        check("Window not in PIP mode").that(pinnedWindows).doesNotContain(windowName)
    }

    /**
     * Checks if the activity with title containing [component] is visible
     *
     * In the case that an app is stopped in the background (e.g. OS stopped it to release memory)
     * the app window will not be immediately visible when switching back to the app. Checking if a
     * snapshotStartingWindow is present for that app instead can decrease flakiness levels of the
     * assertion.
     *
     * @param component Component to search
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun isAppSnapshotStartingWindowVisibleFor(
        component: FlickerComponentName
    ) {
        val windowName = component.toWindowName()
        val activity = wmState.getActivitiesForWindow(windowName).firstOrNull()

        // Check existence and visibility of SnapshotStartingWindow
        val snapshotStartingWindow = activity?.children
            ?.firstOrNull { it.name.startsWith("SnapshotStartingWindow for taskId=") }
        check("SnapshotStartingWindow for Activity=${activity?.name} must be visible.")
            .that(snapshotStartingWindow?.isVisible ?: false).isTrue()
    }

    /**
     * Obtains the first subject with [WindowState.title] containing [name].
     *
     * Always returns a subject, event when the layer doesn't exist. To verify if layer
     * actually exists in the hierarchy use [WindowStateSubject.exists] or
     * [WindowStateSubject.doesNotExist]
     */
    fun windowState(name: String): WindowStateSubject {
        return subjects.firstOrNull {
            it.windowState?.name?.contains(name) == true
        } ?: WindowStateSubject.assertThat(name, this, timestamp)
    }

    /**
     * Obtains the first subject matching  [predicate].
     *
     * Always returns a subject, event when the layer doesn't exist. To verify if layer
     * actually exists in the hierarchy use [WindowStateSubject.exists] or
     * [WindowStateSubject.doesNotExist]
     *
     * @param predicate to search for a subject
     * @param name Name of the subject to use when not found (optional)
     */
    fun windowState(name: String = "", predicate: (WindowState) -> Boolean): WindowStateSubject {
        return subjects.firstOrNull {
            it.windowState?.run { predicate(this) } ?: false
        } ?: WindowStateSubject.assertThat(name, this, timestamp)
    }

    override fun toString(): String {
        return "WindowManagerStateSubject($wmState)"
    }

    companion object {
        /**
         * Boiler-plate Subject.Factory for WindowManagerStateSubject
         *
         * @param parent containing the entry
         */
        private fun getFactory(
            trace: WindowManagerTraceSubject?,
            parent: FlickerSubject?
        ): Factory<Subject, WindowManagerState> =
            Factory { fm, subject -> WindowManagerStateSubject(fm, subject, trace, parent) }

        /**
         * User-defined entry point
         *
         * @param entry to assert
         * @param parent containing the entry
         */
        @JvmStatic
        @JvmOverloads
        fun assertThat(
            entry: WindowManagerState,
            trace: WindowManagerTraceSubject? = null,
            parent: FlickerSubject? = null
        ): WindowManagerStateSubject {
            val strategy = FlickerFailureStrategy()
            val subject = StandardSubjectBuilder.forCustomFailureStrategy(strategy)
                .about(getFactory(trace, parent))
                .that(entry) as WindowManagerStateSubject
            strategy.init(subject)
            return subject
        }
    }
}

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

import com.android.server.wm.flicker.assertions.Assertion
import com.android.server.wm.flicker.traces.FlickerFailureStrategy
import com.android.server.wm.flicker.traces.FlickerTraceSubject
import com.android.server.wm.flicker.traces.region.RegionTraceSubject
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.region.RegionTrace
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace
import com.android.server.wm.traces.common.windowmanager.windows.WindowState
import com.google.common.truth.FailureMetadata
import com.google.common.truth.FailureStrategy
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Subject
import com.google.common.truth.Subject.Factory

/**
 * Truth subject for [WindowManagerTrace] objects, used to make assertions over behaviors that
 * occur throughout a whole trace.
 *
 * To make assertions over a trace it is recommended to create a subject using
 * [WindowManagerTraceSubject.assertThat](myTrace). Alternatively, it is also possible to use
 * Truth.assertAbout(WindowManagerTraceSubject.FACTORY), however it will provide less debug
 * information because it uses Truth's default [FailureStrategy].
 *
 * Example:
 *    val trace = WindowManagerTraceParser.parseFromTrace(myTraceFile)
 *    val subject = WindowManagerTraceSubject.assertThat(trace)
 *        .contains("ValidWindow")
 *        .notContains("ImaginaryWindow")
 *        .showsAboveAppWindow("NavigationBar")
 *        .forAllEntries()
 *
 * Example2:
 *    val trace = WindowManagerTraceParser.parseFromTrace(myTraceFile)
 *    val subject = WindowManagerTraceSubject.assertThat(trace) {
 *        check("Custom check") { myCustomAssertion(this) }
 *    }
 */
class WindowManagerTraceSubject private constructor(
    fm: FailureMetadata,
    val trace: WindowManagerTrace,
    override val parent: WindowManagerTraceSubject?
) : FlickerTraceSubject<WindowManagerStateSubject>(fm, trace) {
    override val selfFacts
        get() = super.selfFacts.toMutableList()

    override val subjects by lazy {
        trace.entries.map { WindowManagerStateSubject.assertThat(it, this, this) }
    }

    /** {@inheritDoc} */
    override fun then(): WindowManagerTraceSubject = apply { super.then() }

    /** {@inheritDoc} */
    override fun skipUntilFirstAssertion(): WindowManagerTraceSubject =
        apply { super.skipUntilFirstAssertion() }

    fun isEmpty(): WindowManagerTraceSubject = apply {
        check("Trace is empty").that(trace).isEmpty()
    }

    fun isNotEmpty(): WindowManagerTraceSubject = apply {
        check("Trace is not empty").that(trace).isNotEmpty()
    }

    /**
     * @return List of [WindowStateSubject]s matching [partialWindowTitle] in the order they
     *      appear on the trace
     */
    fun windowStates(partialWindowTitle: String): List<WindowStateSubject> {
        return subjects
            .map { it.windowState { windows -> windows.title.contains(partialWindowTitle) } }
            .filter { it.isNotEmpty }
    }

    /**
     * @return List of [WindowStateSubject]s matching [predicate] in the order they
     *      appear on the trace
     */
    fun windowStates(predicate: (WindowState) -> Boolean): List<WindowStateSubject> {
        return subjects
            .map { it.windowState { window -> predicate(window) } }
            .filter { it.isNotEmpty }
    }

    /** {@inheritDoc} */
    fun notContains(
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion("notContains(${component.toWindowName()})", isOptional) {
            it.notContains(component)
        }
    }

    /**
     * Checks if the non-app window with title containing [component] exists above the app
     * windows and is visible
     *
     * @param component Component to search
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun isAboveAppWindowVisible(
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion("isAboveAppWindowVisible(${component.toWindowName()})", isOptional) {
            it.containsAboveAppWindow(component)
                .isNonAppWindowVisible(component)
        }
    }

    /**
     * Checks if the non-app window with title containing [component] exists above the app
     * windows and is invisible
     *
     * @param component Component to search
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun isAboveAppWindowInvisible(
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion("isAboveAppWindowInvisible(${component.toWindowName()})", isOptional) {
            it.containsAboveAppWindow(component)
                .isNonAppWindowInvisible(component)
        }
    }

    /**
     * Checks if the non-app window with title containing [component] exists below the app
     * windows and is visible
     *
     * @param component Component to search
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun isBelowAppWindowVisible(
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion("isBelowAppWindowVisible(${component.toWindowName()})", isOptional) {
            it.containsBelowAppWindow(component)
                .isNonAppWindowVisible(component)
        }
    }

    /**
     * Checks if the non-app window with title containing [component] exists below the app
     * windows and is invisible
     *
     * @param component Component to search
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun isBelowAppWindowInvisible(
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion("isBelowAppWindowInvisible(${component.toWindowName()})", isOptional) {
            it.containsBelowAppWindow(component)
                .isNonAppWindowInvisible(component)
        }
    }

    /**
     * Checks if non-app window with title containing the [component] exists above or
     * below the app windows and is visible
     *
     * @param component Component to search
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun isNonAppWindowVisible(
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion("isNonAppWindowVisible(${component.toWindowName()})", isOptional) {
            it.isNonAppWindowVisible(component)
        }
    }

    /**
     * Checks if non-app window with title containing the [component] exists above or
     * below the app windows and is invisible
     *
     * @param component Component to search
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun isNonAppWindowInvisible(
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion("isNonAppWindowInvisible(${component.toWindowName()})", isOptional) {
            it.isNonAppWindowInvisible(component)
        }
    }

    /**
     * Checks if app window with title containing the [component] is on top
     *
     * @param component Component to search
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun isAppWindowOnTop(
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion("isAppWindowOnTop(${component.toWindowName()})", isOptional) {
            it.isAppWindowOnTop(component)
        }
    }

    /**
     * Checks if app window with title containing the [component] is not on top
     *
     * @param component Component to search
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun isAppWindowNotOnTop(
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion("appWindowNotOnTop(${component.toWindowName()})", isOptional) {
            it.isAppWindowNotOnTop(component)
        }
    }

    /**
     * Checks if app window with title containing the [component] is visible
     *
     * @param component Component to search
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun isAppWindowVisible(
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion("isAppWindowVisible(${component.toWindowName()})", isOptional) {
            it.isAppWindowVisible(component)
        }
    }

    /**
     * Checks if there are no visible app windows.
     *
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun hasNoVisibleAppWindow(isOptional: Boolean = false): WindowManagerTraceSubject = apply {
        addAssertion("hasNoVisibleAppWindow()", isOptional) {
            it.hasNoVisibleAppWindow()
        }
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
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion(
            "isAppSnapshotStartingWindowVisibleFor(${component.toWindowName()})", isOptional) {
            it.isAppSnapshotStartingWindowVisibleFor(component)
        }
    }

    /**
     * Checks if app window with title containing the [component] is invisible
     *
     * Note: This assertion have issues with the launcher window, because it contains 2 windows
     * with the same name and only 1 is visible at a time. Prefer [isAppWindowOnTop] for launcher
     * instead
     *
     * @param component Component to search
     * @param isOptional If this assertion is optional or must pass
     */
    @JvmOverloads
    fun isAppWindowInvisible(
        component: FlickerComponentName,
        isOptional: Boolean = false
    ): WindowManagerTraceSubject = apply {
        addAssertion("isAppWindowInvisible(${component.toWindowName()})", isOptional) {
            it.isAppWindowInvisible(component)
        }
    }

    /**
     * Checks if no app windows containing the [component] overlap with each other.
     *
     * @param component Component to search
     */
    fun noWindowsOverlap(
        vararg component: FlickerComponentName
    ): WindowManagerTraceSubject = apply {
        val repr = component.joinToString(", ") { it.toWindowName() }
        verify("Must give more than one window to check! (Given $repr)")
                .that(component)
                .hasLength(1)
        addAssertion("noWindowsOverlap($repr)") {
            it.doNotOverlap(*component)
        }
    }

    /**
     * Checks if the window named [aboveWindow] is above the one named [belowWindow] in
     * z-order.
     *
     * @param aboveWindow Expected top window
     * @param belowWindow Expected bottom window
     */
    fun isAboveWindow(
        aboveWindow: FlickerComponentName,
        belowWindow: FlickerComponentName
    ): WindowManagerTraceSubject = apply {
        val aboveWindowTitle = aboveWindow.toWindowName()
        val belowWindowTitle = belowWindow.toWindowName()
        require(aboveWindowTitle != belowWindowTitle)
        addAssertion("$aboveWindowTitle is above $belowWindowTitle") {
            it.isAboveWindow(aboveWindow, belowWindow)
        }
    }

    /**
     * Obtains the trace of regions occupied by all windows with name containing any of [components]
     *
     * @param components Components to search
     */
    fun visibleRegion(vararg components: FlickerComponentName): RegionTraceSubject {
        val regionTrace = RegionTrace(components, subjects.map {
            it.visibleRegion(*components).regionEntry
        }.toTypedArray())

        return RegionTraceSubject.assertThat(regionTrace, this)
    }

    /**
     * Checks that all visible layers are shown for more than one consecutive entry
     */
    @JvmOverloads
    fun visibleWindowsShownMoreThanOneConsecutiveEntry(
        ignoreWindows: List<FlickerComponentName> = listOf(
            FlickerComponentName.SPLASH_SCREEN,
            FlickerComponentName.SNAPSHOT)
    ): WindowManagerTraceSubject = apply {
        visibleEntriesShownMoreThanOneConsecutiveTime { subject ->
            subject.wmState.windowStates
                .filter { it.isVisible }
                .filter {
                    ignoreWindows.none { windowName -> windowName.toWindowName() in it.title }
                }
                .map { it.name }
                .toSet()
        }
    }

    /**
     * Executes a custom [assertion] on the current subject
     */
    operator fun invoke(
        name: String,
        isOptional: Boolean = false,
        assertion: Assertion<WindowManagerStateSubject>
    ): WindowManagerTraceSubject = apply { addAssertion(name, isOptional, assertion) }

    /**
     * Run the assertions for all trace entries within the specified time range
     */
    fun forRange(startTime: Long, endTime: Long) {
        val subjectsInRange = subjects.filter { it.wmState.timestamp in startTime..endTime }
        assertionsChecker.test(subjectsInRange)
    }

    /**
     * User-defined entry point for the trace entry with [timestamp]
     *
     * @param timestamp of the entry
     */
    fun entry(timestamp: Long): WindowManagerStateSubject =
        subjects.first { it.wmState.timestamp == timestamp }

    companion object {
        /**
         * Boiler-plate Subject.Factory for WmTraceSubject
         */
        private fun getFactory(
            parent: WindowManagerTraceSubject?
        ): Factory<Subject, WindowManagerTrace> =
            Factory { fm, subject -> WindowManagerTraceSubject(fm, subject, parent) }

        /**
         * Creates a [WindowManagerTraceSubject] representing a WindowManager trace,
         * which can be used to make assertions.
         *
         * @param trace WindowManager trace
         */
        @JvmStatic
        @JvmOverloads
        fun assertThat(
            trace: WindowManagerTrace,
            parent: WindowManagerTraceSubject? = null
        ): WindowManagerTraceSubject {
            val strategy = FlickerFailureStrategy()
            val subject = StandardSubjectBuilder.forCustomFailureStrategy(strategy)
                .about(getFactory(parent))
                .that(trace) as WindowManagerTraceSubject
            strategy.init(subject)
            return subject
        }

        /**
         * Static method for getting the subject factory (for use with assertAbout())
         */
        @JvmStatic
        fun entries(
            parent: WindowManagerTraceSubject?
        ): Factory<Subject, WindowManagerTrace> = getFactory(parent)
    }
}

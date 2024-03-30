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

package com.android.server.wm.flicker

import android.app.Instrumentation
import android.platform.test.rule.NavigationModeRule
import android.platform.test.rule.PressHomeRule
import android.platform.test.rule.UnlockScreenRule
import android.view.Surface
import android.view.WindowManagerPolicyConstants
import androidx.annotation.VisibleForTesting
import com.android.server.wm.flicker.assertions.AssertionData
import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.flicker.dsl.AssertionTag
import com.android.server.wm.flicker.dsl.FlickerBuilder
import com.android.server.wm.flicker.helpers.SampleAppHelper
import com.android.server.wm.flicker.rules.ChangeDisplayOrientationRule
import com.android.server.wm.flicker.rules.LaunchAppRule
import com.android.server.wm.flicker.rules.RemoveAllTasksButHomeRule
import com.android.server.wm.flicker.traces.eventlog.EventLogSubject
import com.android.server.wm.flicker.traces.layers.LayerTraceEntrySubject
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject
import com.android.server.wm.flicker.traces.region.RegionTraceSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerStateSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject
import com.android.server.wm.traces.common.FlickerComponentName
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/**
 * Specification of a flicker test for JUnit ParameterizedRunner class
 */
data class FlickerTestParameter(
    @JvmField val config: MutableMap<String, Any?>,
    private val nameOverride: String? = null
) {
    private var internalFlicker: Flicker? = null

    private val flicker: Flicker get() = internalFlicker ?: error("Flicker not initialized")
    private val name: String get() = nameOverride ?: defaultName(this)

    internal val isInitialized: Boolean get() = internalFlicker != null
    internal val result: FlickerResult? get() = internalFlicker?.result

    /**
     * If the initial screen rotation is 90 (landscape) or 180 (seascape) degrees
     */
    val isLandscapeOrSeascapeAtStart: Boolean
        get() = startRotation == Surface.ROTATION_90 || startRotation == Surface.ROTATION_270

    /**
     * Initial screen rotation (see [Surface] for values)
     *
     * Defaults to [Surface.ROTATION_0]
     */
    val startRotation: Int
        get() = config.getOrDefault(START_ROTATION, Surface.ROTATION_0) as Int

    /**
     * Final screen rotation (see [Surface] for values)
     *
     * Defaults to [startRotation]
     */
    val endRotation: Int
        get() = config.getOrDefault(END_ROTATION, startRotation) as Int

    /**
     * Navigation mode, such as 3 button or gestural.
     *
     * See [WindowManagerPolicyConstants].NAV_BAR_MODE_* for possible values
     *
     * Defaults to [WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY]
     */
    val navBarMode: String
        get() = config.getOrDefault(NAV_BAR_MODE,
            WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY) as String

    val navBarModeName
        get() = when (this.navBarMode) {
            WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON_OVERLAY -> "3_BUTTON_NAV"
            WindowManagerPolicyConstants.NAV_BAR_MODE_2BUTTON_OVERLAY -> "2_BUTTON_NAV"
            WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY -> "GESTURAL_NAV"
            else -> "UNKNOWN_NAV_BAR_MODE(${this.navBarMode}"
        }

    val isGesturalNavigation =
        navBarMode == WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY

    /**
     * Clean the internal flicker reference (cache)
     */
    fun clear() {
        internalFlicker?.clear()
    }

    /**
     * Builds a flicker object and assigns it to the test parameters
     */
    fun initialize(builder: FlickerBuilder, testName: String) {
        internalFlicker = builder
            .withTestName { "${testName}_$name" }
            .repeat { config.getOrDefault(REPETITIONS, 1) as Int }
            .build(TransitionRunnerWithRules(getTestSetupRules(builder.instrumentation)))
    }

    /**
     * Execute [assertion] on the initial state of a WM trace (before transition)
     *
     * @param assertion Assertion predicate
     */
    fun assertWmStart(assertion: WindowManagerStateSubject.() -> Unit) {
        val assertionData = buildWmStartAssertion(assertion)
        this.flicker.checkAssertion(assertionData)
    }

    /**
     * Execute [assertion] on the final state of a WM trace (after transition)
     *
     * @param assertion Assertion predicate
     */
    fun assertWmEnd(assertion: WindowManagerStateSubject.() -> Unit) {
        val assertionData = buildWmEndAssertion(assertion)
        this.flicker.checkAssertion(assertionData)
    }

    /**
     * Execute [assertion] on a WM trace
     *
     * @param assertion Assertion predicate
     */
    fun assertWm(assertion: WindowManagerTraceSubject.() -> Unit) {
        val assertionData = buildWMAssertion(assertion)
        this.flicker.checkAssertion(assertionData)
    }

    /**
     * Execute [assertion] on a user defined moment ([tag]) of a WM trace
     *
     * @param assertion Assertion predicate
     */
    fun assertWmTag(tag: String, assertion: WindowManagerStateSubject.() -> Unit) {
        val assertionData = buildWMTagAssertion(tag, assertion)
        this.flicker.checkAssertion(assertionData)
    }

    /**
     * Execute [assertion] on the visible region of a component on the WM trace
     *
     * @param component The component for which we want to get the visible region for to run the
     *                  assertion on
     * @param assertion Assertion predicate
     */
    fun assertWmVisibleRegion(
        vararg components: FlickerComponentName,
        assertion: RegionTraceSubject.() -> Unit
    ) {
        val assertionData = buildWmVisibleRegionAssertion(components = components, assertion)
        this.flicker.checkAssertion(assertionData)
    }

    /**
     * Execute [assertion] on the initial state of a SF trace (before transition)
     *
     * @param assertion Assertion predicate
     */
    fun assertLayersStart(assertion: LayerTraceEntrySubject.() -> Unit) {
        val assertionData = buildLayersStartAssertion(assertion)
        this.flicker.checkAssertion(assertionData)
    }

    /**
     * Execute [assertion] on the final state of a SF trace (after transition)
     *
     * @param assertion Assertion predicate
     */
    fun assertLayersEnd(assertion: LayerTraceEntrySubject.() -> Unit) {
        val assertionData = buildLayersEndAssertion(assertion)
        this.flicker.checkAssertion(assertionData)
    }

    /**
     * Execute [assertion] on a SF trace
     *
     * @param assertion Assertion predicate
     */
    fun assertLayers(assertion: LayersTraceSubject.() -> Unit) {
        val assertionData = buildLayersAssertion(assertion)
        this.flicker.checkAssertion(assertionData)
    }

    /**
     * Execute [assertion] on a user defined moment ([tag]) of a SF trace
     *
     * @param assertion Assertion predicate
     */
    fun assertLayersTag(tag: String, assertion: LayerTraceEntrySubject.() -> Unit) {
        val assertionData = buildLayersTagAssertion(tag, assertion)
        this.flicker.checkAssertion(assertionData)
    }

    /**
     * Execute [assertion] on the visible region of a component on the layers trace
     *
     * @param components The components for which we want to get the visible region for to run the
     *   assertion on. The assertion will run on the union of the regions of these components.
     * @param useCompositionEngineRegionOnly If true, uses only the region calculated from the
     *   Composition Engine (CE) -- visibleRegion in the proto definition. Otherwise calculates
     *   the visible region when the information is not available from the CE
     * @param assertion Assertion predicate
     */
    @JvmOverloads
    fun assertLayersVisibleRegion(
        vararg components: FlickerComponentName,
        useCompositionEngineRegionOnly: Boolean = true,
        assertion: RegionTraceSubject.() -> Unit
    ) {
        val assertionData = buildLayersVisibleRegionAssertion(
                components = components, useCompositionEngineRegionOnly, assertion)
        this.flicker.checkAssertion(assertionData)
    }

    /**
     * Execute [assertion] on a sequence of event logs
     *
     * @param assertion Assertion predicate
     */
    fun assertEventLog(assertion: EventLogSubject.() -> Unit) {
        val assertionData = buildEventLogAssertion(assertion)
        this.flicker.checkAssertion(assertionData)
    }

    /**
     * Create the default flicker test setup rules. In order:
     *   - unlock device
     *   - change orientation
     *   - change navigation mode
     *   - launch an app
     *   - remove all apps
     *   - go to home screen
     *
     * (b/186740751) An app should be launched because, after changing the navigation mode,
     * the first app launch is handled as a screen size change (similar to a rotation), this
     * causes different problems during testing (e.g. IME now shown on app launch)
     */
    fun getTestSetupRules(instrumentation: Instrumentation): TestRule =
        RuleChain.outerRule(UnlockScreenRule())
            .around(NavigationModeRule(navBarMode))
            .around(LaunchAppRule(SampleAppHelper(instrumentation)))
            .around(RemoveAllTasksButHomeRule())
            .around(ChangeDisplayOrientationRule(startRotation))
            .around(PressHomeRule())

    override fun toString(): String = name

    companion object {
        internal const val REPETITIONS = "repetitions"
        internal const val START_ROTATION = "startRotation"
        internal const val END_ROTATION = "endRotation"
        internal const val NAV_BAR_MODE = "navBarMode"

        @VisibleForTesting
        @JvmStatic
        fun buildWmStartAssertion(assertion: WindowManagerStateSubject.() -> Unit): AssertionData =
            AssertionData(tag = AssertionTag.START,
                expectedSubjectClass = WindowManagerStateSubject::class,
                assertion = assertion as FlickerSubject.() -> Unit)

        @VisibleForTesting
        @JvmStatic
        fun buildWmEndAssertion(assertion: WindowManagerStateSubject.() -> Unit): AssertionData =
            AssertionData(tag = AssertionTag.END,
                expectedSubjectClass = WindowManagerStateSubject::class,
                assertion = assertion as FlickerSubject.() -> Unit)

        @VisibleForTesting
        @JvmStatic
        fun buildWMAssertion(assertion: WindowManagerTraceSubject.() -> Unit): AssertionData {
            val closedAssertion: WindowManagerTraceSubject.() -> Unit = {
                this.clear()
                assertion()
                this.forAllEntries()
            }
            return AssertionData(tag = AssertionTag.ALL,
                expectedSubjectClass = WindowManagerTraceSubject::class,
                assertion = closedAssertion as FlickerSubject.() -> Unit)
        }

        @VisibleForTesting
        @JvmStatic
        fun buildWMTagAssertion(
            tag: String,
            assertion: WindowManagerStateSubject.() -> Unit
        ): AssertionData = AssertionData(tag = tag,
            expectedSubjectClass = WindowManagerStateSubject::class,
            assertion = assertion as FlickerSubject.() -> Unit)

        @VisibleForTesting
        @JvmStatic
        fun buildWmVisibleRegionAssertion(
            vararg components: FlickerComponentName,
            assertion: RegionTraceSubject.() -> Unit
        ): AssertionData {
            val closedAssertion: WindowManagerTraceSubject.() -> Unit = {
                this.clear()
                // convert WindowManagerTraceSubject to RegionTraceSubject
                val regionTraceSubject = visibleRegion(*components)
                // add assertions to the regionTraceSubject's AssertionChecker
                assertion(regionTraceSubject)
                // loop through all entries to validate assertions
                regionTraceSubject.forAllEntries()
            }

            return AssertionData(tag = AssertionTag.ALL,
                expectedSubjectClass = WindowManagerTraceSubject::class,
                assertion = closedAssertion as FlickerSubject.() -> Unit)
        }

        @VisibleForTesting
        @JvmStatic
        fun buildLayersStartAssertion(assertion: LayerTraceEntrySubject.() -> Unit): AssertionData =
            AssertionData(tag = AssertionTag.START,
                expectedSubjectClass = LayerTraceEntrySubject::class,
                assertion = assertion as FlickerSubject.() -> Unit)

        @VisibleForTesting
        @JvmStatic
        fun buildLayersEndAssertion(assertion: LayerTraceEntrySubject.() -> Unit): AssertionData =
            AssertionData(tag = AssertionTag.END,
                expectedSubjectClass = LayerTraceEntrySubject::class,
                assertion = assertion as FlickerSubject.() -> Unit)

        @VisibleForTesting
        @JvmStatic
        fun buildLayersAssertion(assertion: LayersTraceSubject.() -> Unit): AssertionData {
            val closedAssertion: LayersTraceSubject.() -> Unit = {
                this.clear()
                assertion()
                this.forAllEntries()
            }

            return AssertionData(tag = AssertionTag.ALL,
                expectedSubjectClass = LayersTraceSubject::class,
                assertion = closedAssertion as FlickerSubject.() -> Unit)
        }

        @VisibleForTesting
        @JvmStatic
        fun buildLayersTagAssertion(
            tag: String,
            assertion: LayerTraceEntrySubject.() -> Unit
        ): AssertionData = AssertionData(tag = tag,
            expectedSubjectClass = LayerTraceEntrySubject::class,
            assertion = assertion as FlickerSubject.() -> Unit)

        @VisibleForTesting
        @JvmOverloads
        @JvmStatic
        fun buildLayersVisibleRegionAssertion(
            vararg components: FlickerComponentName,
            useCompositionEngineRegionOnly: Boolean = true,
            assertion: RegionTraceSubject.() -> Unit
        ): AssertionData {
            val closedAssertion: LayersTraceSubject.() -> Unit = {
                this.clear()
                // convert LayersTraceSubject to RegionTraceSubject
                val regionTraceSubject =
                        visibleRegion(components = components, useCompositionEngineRegionOnly)

                // add assertions to the regionTraceSubject's AssertionChecker
                assertion(regionTraceSubject)
                // loop through all entries to validate assertions
                regionTraceSubject.forAllEntries()
            }

            return AssertionData(tag = AssertionTag.ALL,
                    expectedSubjectClass = LayersTraceSubject::class,
                    assertion = closedAssertion as FlickerSubject.() -> Unit)
        }

        @JvmStatic
        fun buildEventLogAssertion(assertion: EventLogSubject.() -> Unit): AssertionData =
            AssertionData(tag = AssertionTag.ALL,
                expectedSubjectClass = EventLogSubject::class,
                assertion = assertion as FlickerSubject.() -> Unit)

        fun defaultName(test: FlickerTestParameter) = buildString {
            append(Surface.rotationToString(test.startRotation))
            if (test.endRotation != test.startRotation) {
                append("_${Surface.rotationToString(test.endRotation)}")
            }
            if (test.navBarMode.isNotEmpty()) {
                append("_${test.navBarModeName}")
            }
        }
    }
}
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

package com.android.server.wm.flicker.windowmanager

import android.view.Surface
import androidx.test.filters.FlakyTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.wm.flicker.readWmTraceFromDumpFile
import com.android.server.wm.flicker.readWmTraceFromFile
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerStateSubject
import com.android.server.wm.traces.common.ActiveBuffer
import com.android.server.wm.traces.common.Color
import com.android.server.wm.traces.common.DeviceStateDump
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.Matrix33
import com.android.server.wm.traces.common.Rect
import com.android.server.wm.traces.common.RectF
import com.android.server.wm.traces.common.region.Region
import com.android.server.wm.traces.common.layers.Layer
import com.android.server.wm.traces.common.layers.BaseLayerTraceEntry
import com.android.server.wm.traces.common.layers.LayerTraceEntryBuilder
import com.android.server.wm.traces.common.layers.Transform
import com.android.server.wm.traces.common.windowmanager.WindowManagerState
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace
import com.android.server.wm.traces.parser.windowmanager.WindowManagerStateHelper
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [WindowManagerStateHelper] tests. To run this test: `atest
 * FlickerLibTest:WindowManagerTraceHelperTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WindowManagerStateHelperTest {
    class TestWindowManagerStateHelper(
        _wmState: WindowManagerState,
        /**
         * Predicate to supply a new UI information
         */
        deviceDumpSupplier: () -> DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
        numRetries: Int = 5,
        retryIntervalMs: Long = 500L
    ) : WindowManagerStateHelper(InstrumentationRegistry.getInstrumentation(),
        deviceDumpSupplier, numRetries, retryIntervalMs) {
        var wmState: WindowManagerState = _wmState
            private set

        override fun updateCurrState(
            value: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ) {
            wmState = value.wmState
        }
    }

    private val chromeComponent = FlickerComponentName.unflattenFromString(
        "com.android.chrome/org.chromium.chrome.browser" +
        ".firstrun.FirstRunActivity")
    private val simpleAppComponentName = FlickerComponentName.unflattenFromString(
        "com.android.server.wm.flicker.testapp/.SimpleActivity")

    private fun createImaginaryLayer(name: String, index: Int, id: Int, parentId: Int): Layer {
        val transform = Transform(0, Matrix33.EMPTY)
        val rect = RectF(
            left = index.toFloat(),
            top = index.toFloat(),
            right = index.toFloat() + 1,
            bottom = index.toFloat() + 1
        )
        return Layer(
            name,
            id,
            parentId,
            z = 0,
            visibleRegion = Region.from(rect.toRect()),
            activeBuffer = ActiveBuffer(1, 1, 1, 1),
            flags = 0,
            bounds = rect,
            color = Color(0f, 0f, 0f, 1f),
            _isOpaque = true,
            shadowRadius = 0f,
            cornerRadius = 0f,
            type = "",
            _screenBounds = rect,
            transform = transform,
            sourceBounds = rect,
            currFrame = 0,
            effectiveScalingMode = 0,
            bufferTransform = transform,
            hwcCompositionType = 0,
            hwcCrop = RectF.EMPTY,
            hwcFrame = Rect.EMPTY,
            crop = rect.toRect(),
            backgroundBlurRadius = 0,
            isRelativeOf = false,
            zOrderRelativeOfId = -1,
            stackId = 0
        )
    }

    private fun createImaginaryVisibleLayers(names: List<FlickerComponentName>): Array<Layer> {
        val root = createImaginaryLayer("root", -1, id = "root".hashCode(), parentId = -1)
        val layers = mutableListOf(root)
        names.forEachIndexed { index, name ->
            layers.add(
                createImaginaryLayer(name.toLayerName(), index, id = name.hashCode(),
                        parentId = root.id)
            )
        }
        return layers.toTypedArray()
    }

    private fun WindowManagerTrace.asSupplier(
        startingTimestamp: Long = 0
    ): () -> DeviceStateDump<WindowManagerState, BaseLayerTraceEntry> {
        val iterator = this.dropWhile { it.timestamp < startingTimestamp }.iterator()
        return {
            if (iterator.hasNext()) {
                val wmState = iterator.next()
                val layerList = mutableListOf(FlickerComponentName.STATUS_BAR,
                    FlickerComponentName.NAV_BAR)

                if (wmState.inputMethodWindowState?.isSurfaceShown == true) {
                    layerList.add(FlickerComponentName.IME)
                }
                val ILayerTraceEntry = LayerTraceEntryBuilder(timestamp = 0,
                    displays = emptyArray(),
                    layers = createImaginaryVisibleLayers(layerList)).build()
                DeviceStateDump(wmState, ILayerTraceEntry)
            } else {
                error("Reached the end of the trace")
            }
        }
    }

    @Test
    fun canWaitForIme() {
        val trace = readWmTraceFromFile("wm_trace_ime.pb")
        val supplier = trace.asSupplier()
        val helper = TestWindowManagerStateHelper(trace.first(), supplier,
            numRetries = trace.entries.size, retryIntervalMs = 1)
        try {
            WindowManagerStateSubject
                .assertThat(helper.wmState)
                .isNonAppWindowVisible(FlickerComponentName.IME)
            error("IME state should not be available")
        } catch (e: AssertionError) {
            helper.waitImeShown()
            WindowManagerStateSubject
                .assertThat(helper.wmState)
                .isNonAppWindowVisible(FlickerComponentName.IME)
        }
    }

    @Test
    fun canFailImeNotShown() {
        val trace = readWmTraceFromFile("wm_trace_ime.pb")
        val supplier = trace.asSupplier()
        val helper = TestWindowManagerStateHelper(trace.first(), supplier,
            numRetries = trace.entries.size, retryIntervalMs = 1)
        try {
            WindowManagerStateSubject
                .assertThat(helper.wmState)
                .isNonAppWindowVisible(FlickerComponentName.IME)
            error("IME state should not be available")
        } catch (e: AssertionError) {
            helper.waitImeShown()
            WindowManagerStateSubject
                .assertThat(helper.wmState)
                .isNonAppWindowVisible(FlickerComponentName.IME)
        }
    }

    @Test
    fun canWaitForWindow() {
        val trace = readWmTraceFromFile("wm_trace_open_app_cold.pb")
        val supplier = trace.asSupplier()
        val helper = TestWindowManagerStateHelper(trace.first(), supplier,
            numRetries = trace.entries.size, retryIntervalMs = 1)
        try {
            WindowManagerStateSubject
                .assertThat(helper.wmState)
                .containsAppWindow(simpleAppComponentName)
            error("Chrome window should not exist in the start of the trace")
        } catch (e: AssertionError) {
            helper.waitForVisibleWindow(simpleAppComponentName)
            WindowManagerStateSubject
                .assertThat(helper.wmState)
                .isAppWindowVisible(simpleAppComponentName)
        }
    }

    @Test
    fun canFailWindowNotShown() {
        val trace = readWmTraceFromFile("wm_trace_open_app_cold.pb")
        val supplier = trace.asSupplier()
        val helper = TestWindowManagerStateHelper(trace.first(), supplier,
            numRetries = 3, retryIntervalMs = 1)
        try {
            WindowManagerStateSubject
                .assertThat(helper.wmState)
                .containsAppWindow(simpleAppComponentName)
            error("SimpleActivity window should not exist in the start of the trace")
        } catch (e: AssertionError) {
            // nothing to do
        }

        try {
            helper.waitForVisibleWindow(simpleAppComponentName)
        } catch (e: IllegalArgumentException) {
            WindowManagerStateSubject
                .assertThat(helper.wmState)
                .notContains(simpleAppComponentName)
        }
    }

    @Test
    fun canDetectHomeActivityVisibility() {
        val trace = readWmTraceFromFile("wm_trace_open_and_close_chrome.pb")
        val supplier = trace.asSupplier()
        val helper = TestWindowManagerStateHelper(trace.first(), supplier,
            numRetries = trace.entries.size, retryIntervalMs = 1)
        WindowManagerStateSubject
            .assertThat(helper.wmState)
            .isHomeActivityVisible()
        helper.waitForVisibleWindow(chromeComponent)
        WindowManagerStateSubject
            .assertThat(helper.wmState)
            .isHomeActivityInvisible()
        helper.waitForHomeActivityVisible()
        WindowManagerStateSubject
            .assertThat(helper.wmState)
            .isHomeActivityVisible()
    }

    @Test
    fun canWaitActivityRemoved() {
        val trace = readWmTraceFromFile("wm_trace_open_and_close_chrome.pb")
        val supplier = trace.asSupplier()
        val helper = TestWindowManagerStateHelper(trace.first(), supplier,
            numRetries = trace.entries.size, retryIntervalMs = 1)
        WindowManagerStateSubject
            .assertThat(helper.wmState)
            .isHomeActivityVisible()
            .notContains(chromeComponent)
        helper.waitForVisibleWindow(chromeComponent)
        WindowManagerStateSubject
            .assertThat(helper.wmState)
            .isAppWindowVisible(chromeComponent)
        helper.waitForActivityRemoved(chromeComponent)
        WindowManagerStateSubject
            .assertThat(helper.wmState)
            .notContains(chromeComponent)
            .isHomeActivityVisible()
    }

    @Test
    fun canWaitAppStateIdle() {
        val trace = readWmTraceFromFile("wm_trace_open_and_close_chrome.pb")
        val initialTimestamp = 69443911868523
        val supplier = trace.asSupplier(startingTimestamp = initialTimestamp)
        val initialEntry = trace.getEntry(initialTimestamp)
        val helper = TestWindowManagerStateHelper(initialEntry, supplier,
            numRetries = trace.entries.size, retryIntervalMs = 1)
        try {
            WindowManagerStateSubject
                .assertThat(helper.wmState)
                .isValid()
            error("Initial state in the trace should not be valid")
        } catch (e: AssertionError) {
            Truth.assertWithMessage("App transition never became idle")
                .that(helper.waitForAppTransitionIdle())
                .isTrue()
            WindowManagerStateSubject
                .assertThat(helper.wmState)
                .isValid()
        }
    }

    @Test
    fun canWaitForRotation() {
        val trace = readWmTraceFromFile("wm_trace_rotation.pb")
        val supplier = trace.asSupplier()
        val helper = TestWindowManagerStateHelper(trace.first(), supplier,
            numRetries = trace.entries.size, retryIntervalMs = 1)
        WindowManagerStateSubject
            .assertThat(helper.wmState)
            .hasRotation(Surface.ROTATION_0)
        helper.waitForRotation(Surface.ROTATION_270)
        WindowManagerStateSubject
            .assertThat(helper.wmState)
            .hasRotation(Surface.ROTATION_270)
        helper.waitForRotation(Surface.ROTATION_0)
        WindowManagerStateSubject
            .assertThat(helper.wmState)
            .hasRotation(Surface.ROTATION_0)
    }

    @Test
    fun canDetectResumedActivitiesInStacks() {
        val trace = readWmTraceFromDumpFile("wm_trace_resumed_activities_in_stack.pb")
        val entry = trace.first()
        Truth.assertWithMessage("Trace should have a resumed activity in stacks")
            .that(entry.resumedActivities)
            .asList()
            .hasSize(1)
    }

    @FlakyTest
    @Test
    fun canWaitForRecents() {
        val trace = readWmTraceFromFile("wm_trace_open_recents.pb")
        val supplier = trace.asSupplier()
        val helper = TestWindowManagerStateHelper(trace.first(), supplier,
            numRetries = trace.entries.size, retryIntervalMs = 1)
        WindowManagerStateSubject
            .assertThat(helper.wmState)
            .isRecentsActivityInvisible()
        helper.waitForRecentsActivityVisible()
        WindowManagerStateSubject
            .assertThat(helper.wmState)
            .isRecentsActivityVisible()
    }
}
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

package com.android.server.wm.flicker.layers

import androidx.test.filters.FlakyTest
import com.android.server.wm.flicker.DOCKER_STACK_DIVIDER_COMPONENT
import com.android.server.wm.flicker.LAUNCHER_COMPONENT
import com.android.server.wm.flicker.SIMPLE_APP_COMPONENT
import com.android.server.wm.flicker.assertFailure
import com.android.server.wm.flicker.assertThrows
import com.android.server.wm.flicker.readLayerTraceFromFile
import com.android.server.wm.flicker.traces.FlickerSubjectException
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject.Companion.assertThat
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.region.Region
import com.android.server.wm.traces.common.layers.LayersTrace
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [LayersTraceSubject] tests. To run this test: `atest
 * FlickerLibTest:LayersTraceSubjectTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LayersTraceSubjectTest {
    @Test
    fun exceptionContainsDebugInfo() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_launch_split_screen.pb")
        val error = assertThrows(AssertionError::class.java) {
            assertThat(layersTraceEntries)
                .isEmpty()
        }
        Truth.assertThat(error).hasMessageThat().contains("Trace start")
        Truth.assertThat(error).hasMessageThat().contains("Trace end")
    }

    @Test
    fun testCanDetectEmptyRegionFromLayerTrace() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_emptyregion.pb")
        try {
            assertThat(layersTraceEntries)
                .visibleRegion()
                .coversAtLeast(DISPLAY_REGION)
                .forAllEntries()
            error("Assertion should not have passed")
        } catch (e: Throwable) {
            assertFailure(e).factValue("Region to test").contains(DISPLAY_REGION.toString())
            assertFailure(e).factValue("Uncovered region").contains("SkRegion((0,1440,1440,2880))")
        }
    }

    @Test
    fun testCanInspectBeginning() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_launch_split_screen.pb")
        assertThat(layersTraceEntries)
            .first()
            .isVisible(FlickerComponentName.NAV_BAR)
            .notContains(DOCKER_STACK_DIVIDER_COMPONENT)
            .isVisible(LAUNCHER_COMPONENT)
    }

    @Test
    fun testCanInspectEnd() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_launch_split_screen.pb")
        assertThat(layersTraceEntries)
            .last()
            .isVisible(FlickerComponentName.NAV_BAR)
            .isVisible(DOCKER_STACK_DIVIDER_COMPONENT)
    }

    @Test
    fun testAssertionsOnRange() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_launch_split_screen.pb")

        assertThat(layersTraceEntries)
            .isVisible(FlickerComponentName.NAV_BAR)
            .isInvisible(DOCKER_STACK_DIVIDER_COMPONENT)
            .forRange(90480846872160L, 90480994138424L)

        assertThat(layersTraceEntries)
            .isVisible(FlickerComponentName.NAV_BAR)
            .isVisible(DOCKER_STACK_DIVIDER_COMPONENT)
            .forRange(90491795074136L, 90493757372977L)
    }

    @Test
    fun testCanDetectChangingAssertions() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_launch_split_screen.pb")
        assertThat(layersTraceEntries)
            .isVisible(FlickerComponentName.NAV_BAR)
            .notContains(DOCKER_STACK_DIVIDER_COMPONENT)
            .then()
            .isVisible(FlickerComponentName.NAV_BAR)
            .isInvisible(DOCKER_STACK_DIVIDER_COMPONENT)
            .then()
            .isVisible(FlickerComponentName.NAV_BAR)
            .isVisible(DOCKER_STACK_DIVIDER_COMPONENT)
            .forAllEntries()
    }

    @FlakyTest
    @Test
    fun testCanDetectIncorrectVisibilityFromLayerTrace() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_invalid_layer_visibility.pb")
        val error = assertThrows(AssertionError::class.java) {
            assertThat(layersTraceEntries)
                .isVisible(SIMPLE_APP_COMPONENT)
                .then()
                .isInvisible(SIMPLE_APP_COMPONENT)
                .forAllEntries()
        }

        assertFailure(error)
            .hasMessageThat()
            .contains("layers_trace_invalid_layer_visibility.pb")
        assertFailure(error)
            .hasMessageThat()
            .contains("2d22h13m14s303ms")
        assertFailure(error)
            .hasMessageThat()
            .contains("!isVisible")
        assertFailure(error)
            .hasMessageThat()
            .contains("com.android.server.wm.flicker.testapp/" +
                "com.android.server.wm.flicker.testapp.SimpleActivity#0 is visible")
    }

    @Test
    fun testCanDetectInvalidVisibleLayerForMoreThanOneConsecutiveEntry() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_invalid_visible_layers.pb")
        val error = assertThrows(AssertionError::class.java) {
            assertThat(layersTraceEntries)
                .visibleLayersShownMoreThanOneConsecutiveEntry()
                .forAllEntries()
            error("Assertion should not have passed")
        }

        Truth.assertThat(error).hasMessageThat().contains("2d18h35m56s397ms")
        assertFailure(error)
            .hasMessageThat()
            .contains("StatusBar#0")
        assertFailure(error)
            .hasMessageThat()
            .contains("is not visible for 2 entries")
    }

    private fun testCanDetectVisibleLayersMoreThanOneConsecutiveEntry(trace: LayersTrace) {
        assertThat(trace)
            .visibleLayersShownMoreThanOneConsecutiveEntry()
            .forAllEntries()
    }

    @Test
    fun testCanDetectVisibleLayersMoreThanOneConsecutiveEntry() {
        testCanDetectVisibleLayersMoreThanOneConsecutiveEntry(
            readLayerTraceFromFile("layers_trace_snapshot_visible.pb"))
    }

    @Test
    fun testCanIgnoreLayerEqualNameInVisibleLayersMoreThanOneConsecutiveEntry() {
        val layersTraceEntries = readLayerTraceFromFile(
                "layers_trace_invalid_visible_layers.pb")
        assertThat(layersTraceEntries)
                .visibleLayersShownMoreThanOneConsecutiveEntry(
                    listOf(FlickerComponentName.STATUS_BAR))
                .forAllEntries()
    }

    @Test
    fun testCanIgnoreLayerShorterNameInVisibleLayersMoreThanOneConsecutiveEntry() {
        val layersTraceEntries = readLayerTraceFromFile(
                "one_visible_layer_launcher_trace.pb")
        val launcherComponent = FlickerComponentName("com.google.android.apps.nexuslauncher",
                "com.google.android.apps.nexuslauncher.NexusLauncherActivity#1")
        assertThat(layersTraceEntries)
                .visibleLayersShownMoreThanOneConsecutiveEntry(listOf(launcherComponent))
                .forAllEntries()
    }

    private fun detectRootLayer(fileName: String) {
        val layersTrace = readLayerTraceFromFile(fileName)
        for (entry in layersTrace.entries) {
            val rootLayers = entry.children
            Truth.assertWithMessage("Does not have any root layer")
                    .that(rootLayers.size)
                    .isGreaterThan(0)
            val firstParentId = rootLayers.first().parentId
            Truth.assertWithMessage("Has multiple root layers")
                    .that(rootLayers.all { it.parentId == firstParentId })
                    .isTrue()
        }
    }

    @Test
    fun testCanDetectRootLayer() {
        detectRootLayer("layers_trace_root.pb")
    }

    @Test
    fun testCanDetectRootLayerAOSP() {
        detectRootLayer("layers_trace_root_aosp.pb")
    }

    @Test
    fun canTestLayerOccludedByAppLayerIsNotVisible() {
        val trace = readLayerTraceFromFile("layers_trace_occluded.pb")
        val entry = assertThat(trace).entry(1700382131522L)
        entry.isVisible(SIMPLE_APP_COMPONENT)
    }

    @Test
    fun testCanDetectLayerExpanding() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_openchrome.pb")
        val animation = assertThat(layersTraceEntries).layers("animation-leash of app_transition#0")
        // Obtain the area of each layer and checks if the next area is
        // greater or equal to the previous one
        val areas = animation.map {
            val region = it.layer?.visibleRegion ?: Region()
            val area = region.width * region.height
            area
        }
        val expanding = areas.zipWithNext { currentArea, nextArea ->
            nextArea >= currentArea
        }

        Truth.assertWithMessage("Animation leash should be expanding")
            .that(expanding.all { it })
            .isTrue()
    }

    @Test
    fun checkVisibleRegionAppMinusPipLayer() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_pip_wmshell.pb")
        val subject = assertThat(layersTraceEntries).last()

        try {
            subject.visibleRegion(FIXED_APP).coversExactly(DISPLAY_REGION_ROTATED)
            error("Layer is partially covered by a Pip layer and should " +
                "not cover the device screen")
        } catch (e: AssertionError) {
            val pipRegion = subject.visibleRegion(PIP_APP).region
            val expectedWithoutPip = DISPLAY_REGION_ROTATED.minus(pipRegion)
            subject.visibleRegion(FIXED_APP)
                    .coversExactly(expectedWithoutPip)
        }
    }

    @Test
    fun checkVisibleRegionAppPlusPipLayer() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_pip_wmshell.pb")
        val subject = assertThat(layersTraceEntries).last()
        val pipRegion = subject.visibleRegion(PIP_APP).region
        subject.visibleRegion(FIXED_APP)
                .plus(pipRegion)
                .coversExactly(DISPLAY_REGION_ROTATED)
    }

    @Test
    fun checkCanDetectSplashScreen() {
        val trace = readLayerTraceFromFile("layers_trace_splashscreen.pb")
        val newLayer = FlickerComponentName("com.android.server.wm.flicker.testapp",
            "com.android.server.wm.flicker.testapp.SimpleActivity")
        assertThat(trace)
            .isVisible(LAUNCHER_COMPONENT)
            .then()
            .isSplashScreenVisibleFor(newLayer, isOptional = false)
            .then()
            .isVisible(newLayer)
            .forAllEntries()

        val failure = assertThrows(FlickerSubjectException::class.java) {
            assertThat(trace)
                .isVisible(LAUNCHER_COMPONENT)
                .then()
                .isVisible(newLayer)
                .forAllEntries()
        }
        assertFailure(failure).hasMessageThat().contains("Is Invisible")
    }

    @Test
    fun checkCanDetectMissingSplashScreen() {
        val trace = readLayerTraceFromFile("layers_trace_splashscreen.pb")
        val newLayer = FlickerComponentName("com.android.server.wm.flicker.testapp",
            "com.android.server.wm.flicker.testapp.SimpleActivity")

        // No splashscreen because no matching activity record
        var failure = assertThrows(FlickerSubjectException::class.java) {
            assertThat(trace).first().isSplashScreenVisibleFor(newLayer)
        }
        assertFailure(failure).hasMessageThat().contains("Could not find Activity Record layer")

        // No splashscreen for target activity record
        failure = assertThrows(FlickerSubjectException::class.java) {
            assertThat(trace).first().isSplashScreenVisibleFor(LAUNCHER_COMPONENT)
        }
        assertFailure(failure).hasMessageThat().contains("No splash screen visible")
    }

    @Test
    fun checkCanDetectOccludedLayerWithDisplaySizeCrop() {
        val trace = readLayerTraceFromFile("layers_trace_display_size_crop.winscope")
        val subject = assertThat(trace)
        subject.visibleLayersShownMoreThanOneConsecutiveEntry()

        val entrySubject = subject.entry(262022343289032)
            .layer("SimpleActivity#30168", frameNumber = 1)
        Truth.assertWithMessage("Layer visibility")
            .that(entrySubject.isVisible)
            .isFalse()
        Truth.assertWithMessage("Layer occluded")
            .that(entrySubject.layer?.occludedBy)
            .asList()
            .isNotEmpty()
        Truth.assertWithMessage("Layer occluded")
            .that(entrySubject.layer?.occludedBy?.first()?.name)
            .contains("RotationLayer")
    }

    companion object {
        private val DISPLAY_REGION = Region.from(0, 0, 1440, 2880)
        private val DISPLAY_REGION_ROTATED = Region.from(0, 0, 2160, 1080)
        private const val SHELL_APP_PACKAGE = "com.android.wm.shell.flicker.testapp"
        private val FIXED_APP = FlickerComponentName(SHELL_APP_PACKAGE,
                "$SHELL_APP_PACKAGE.FixedActivity")
        private val PIP_APP = FlickerComponentName(SHELL_APP_PACKAGE,
            "$SHELL_APP_PACKAGE.PipActivity")
    }
}

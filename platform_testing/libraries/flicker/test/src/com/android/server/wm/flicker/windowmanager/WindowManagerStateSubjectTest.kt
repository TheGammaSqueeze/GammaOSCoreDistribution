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

import com.android.server.wm.flicker.CHROME_SPLASH_SCREEN_COMPONENT
import com.android.server.wm.flicker.IMAGINARY_COMPONENT
import com.android.server.wm.flicker.LAUNCHER_COMPONENT
import com.android.server.wm.flicker.PIP_DISMISS_COMPONENT
import com.android.server.wm.flicker.SCREEN_DECOR_COMPONENT
import com.android.server.wm.flicker.SHELL_SPLIT_SCREEN_PRIMARY_COMPONENT
import com.android.server.wm.flicker.SHELL_SPLIT_SCREEN_SECONDARY_COMPONENT
import com.android.server.wm.flicker.WALLPAPER_COMPONENT
import com.android.server.wm.flicker.assertFailure
import com.android.server.wm.flicker.assertThatErrorContainsDebugInfo
import com.android.server.wm.flicker.assertThrows
import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.flicker.readWmTraceFromDumpFile
import com.android.server.wm.flicker.readWmTraceFromFile
import com.android.server.wm.flicker.traces.FlickerSubjectException
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerStateSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject.Companion.assertThat
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.region.Region
import com.android.server.wm.traces.common.windowmanager.WindowManagerState
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace
import com.android.server.wm.traces.common.windowmanager.windows.ConfigurationContainer
import com.android.server.wm.traces.common.windowmanager.windows.KeyguardControllerState
import com.android.server.wm.traces.common.windowmanager.windows.RootWindowContainer
import com.android.server.wm.traces.common.windowmanager.windows.WindowContainer
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [WindowManagerStateSubject] tests.
 * To run this test: `atest FlickerLibTest:WindowManagerStateSubjectTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WindowManagerStateSubjectTest {
    private val trace: WindowManagerTrace by lazy { readWmTraceFromFile("wm_trace_openchrome.pb") }
    // Launcher is visible in fullscreen in the first frame of the trace
    private val traceFirstFrameTimestamp = 9213763541297
    // The first frame where the chrome splash screen is shown
    private val traceFirstChromeFlashScreenTimestamp = 9215551505798
    // The bounds of the display used to generate the trace [trace]
    private val displayBounds = Region.from(0, 0, 1440, 2960)
    // The region covered by the status bar in the trace
    private val statusBarRegion = Region.from(0, 0, 1440, 171)

    @Test
    fun exceptionContainsDebugInfo() {
        val error = assertThrows(AssertionError::class.java) {
            assertThat(trace).first().visibleRegion(IMAGINARY_COMPONENT)
        }
        assertThatErrorContainsDebugInfo(error)
        Truth.assertThat(error).hasMessageThat().contains(IMAGINARY_COMPONENT.className)
        Truth.assertThat(error).hasMessageThat().contains(FlickerSubject.ASSERTION_TAG)
    }

    @Test
    fun canDetectAboveAppWindowVisibility_isVisible() {
        assertThat(trace)
            .entry(traceFirstFrameTimestamp)
            .containsAboveAppWindow(FlickerComponentName.NAV_BAR)
            .containsAboveAppWindow(SCREEN_DECOR_COMPONENT)
            .containsAboveAppWindow(FlickerComponentName.STATUS_BAR)
    }

    @Test
    fun canDetectAboveAppWindowVisibility_isInvisible() {
        val subject = assertThat(trace).entry(traceFirstFrameTimestamp)
        var failure = assertThrows(AssertionError::class.java) {
            subject.containsAboveAppWindow(PIP_DISMISS_COMPONENT)
                .isNonAppWindowVisible(PIP_DISMISS_COMPONENT)
        }
        assertFailure(failure).factValue("Is Invisible").contains("pip-dismiss-overlay")

        failure = assertThrows(AssertionError::class.java) {
            subject.containsAboveAppWindow(FlickerComponentName.NAV_BAR)
                .isNonAppWindowInvisible(FlickerComponentName.NAV_BAR)
        }
        assertFailure(failure).factValue("Is Visible").contains("NavigationBar")
    }

    @Test
    fun canDetectWindowCoversAtLeastRegion_exactSize() {
        val entry = assertThat(trace)
            .entry(traceFirstFrameTimestamp)

        entry.visibleRegion(FlickerComponentName.STATUS_BAR)
                .coversAtLeast(statusBarRegion)
        entry.visibleRegion(LAUNCHER_COMPONENT)
            .coversAtLeast(displayBounds)
    }

    @Test
    fun canDetectWindowCoversAtLeastRegion_smallerRegion() {
        val entry = assertThat(trace)
            .entry(traceFirstFrameTimestamp)
        entry.visibleRegion(FlickerComponentName.STATUS_BAR)
                .coversAtLeast(Region.from(0, 0, 100, 100))
        entry.visibleRegion(LAUNCHER_COMPONENT)
            .coversAtLeast(Region.from(0, 0, 100, 100))
    }

    @Test
    fun canDetectWindowCoversAtLeastRegion_largerRegion() {
        val subject = assertThat(trace).entry(traceFirstFrameTimestamp)
        var failure = assertThrows(FlickerSubjectException::class.java) {
            subject.visibleRegion(FlickerComponentName.STATUS_BAR)
                    .coversAtLeast(Region.from(0, 0, 1441, 171))
        }
        assertFailure(failure).factValue("Uncovered region").contains("SkRegion((1440,0,1441,171))")

        failure = assertThrows(FlickerSubjectException::class.java) {
            subject.visibleRegion(LAUNCHER_COMPONENT)
                .coversAtLeast(Region.from(0, 0, 1440, 2961))
        }
        assertFailure(failure).factValue("Uncovered region")
            .contains("SkRegion((0,2960,1440,2961))")
    }

    @Test
    fun canDetectWindowCoversExactlyRegion_exactSize() {
        val entry = assertThat(trace)
                .entry(traceFirstFrameTimestamp)

        entry.visibleRegion(FlickerComponentName.STATUS_BAR)
                .coversExactly(statusBarRegion)
        entry.visibleRegion(LAUNCHER_COMPONENT)
                .coversExactly(displayBounds)
    }

    @Test
    fun canDetectWindowCoversExactlyRegion_smallerRegion() {
        val subject = assertThat(trace).entry(traceFirstFrameTimestamp)
        var failure = assertThrows(FlickerSubjectException::class.java) {
            subject.visibleRegion(FlickerComponentName.STATUS_BAR)
                    .coversAtMost(Region.from(0, 0, 100, 100))
        }
        assertFailure(failure).factValue("Out-of-bounds region")
                .contains("SkRegion((100,0,1440,100)(0,100,1440,171))")

        failure = assertThrows(FlickerSubjectException::class.java) {
            subject.visibleRegion(LAUNCHER_COMPONENT)
                    .coversAtMost(Region.from(0, 0, 100, 100))
        }
        assertFailure(failure).factValue("Out-of-bounds region")
                .contains("SkRegion((100,0,1440,100)(0,100,1440,2960))")
    }

    @Test
    fun canDetectWindowCoversExactlyRegion_largerRegion() {
        val subject = assertThat(trace).entry(traceFirstFrameTimestamp)
        var failure = assertThrows(FlickerSubjectException::class.java) {
            subject.visibleRegion(FlickerComponentName.STATUS_BAR)
                    .coversAtLeast(Region.from(0, 0, 1441, 171))
        }
        assertFailure(failure).factValue("Uncovered region").contains("SkRegion((1440,0,1441,171))")

        failure = assertThrows(FlickerSubjectException::class.java) {
            subject.visibleRegion(LAUNCHER_COMPONENT)
                    .coversAtLeast(Region.from(0, 0, 1440, 2961))
        }
        assertFailure(failure).factValue("Uncovered region")
                .contains("SkRegion((0,2960,1440,2961))")
    }

    @Test
    fun canDetectWindowCoversAtMostRegion_extactSize() {
        val entry = assertThat(trace)
            .entry(traceFirstFrameTimestamp)
        entry.visibleRegion(FlickerComponentName.STATUS_BAR)
                .coversAtMost(statusBarRegion)
        entry.visibleRegion(LAUNCHER_COMPONENT)
            .coversAtMost(displayBounds)
    }

    @Test
    fun canDetectWindowCoversAtMostRegion_smallerRegion() {
        val subject = assertThat(trace).entry(traceFirstFrameTimestamp)
        var failure = assertThrows(FlickerSubjectException::class.java) {
            subject.visibleRegion(FlickerComponentName.STATUS_BAR)
                    .coversAtMost(Region.from(0, 0, 100, 100))
        }
        assertFailure(failure).factValue("Out-of-bounds region")
            .contains("SkRegion((100,0,1440,100)(0,100,1440,171))")

        failure = assertThrows(FlickerSubjectException::class.java) {
            subject.visibleRegion(LAUNCHER_COMPONENT)
                .coversAtMost(Region.from(0, 0, 100, 100))
        }
        assertFailure(failure).factValue("Out-of-bounds region")
            .contains("SkRegion((100,0,1440,100)(0,100,1440,2960))")
    }

    @Test
    fun canDetectWindowCoversAtMostRegion_largerRegion() {
        val entry = assertThat(trace)
            .entry(traceFirstFrameTimestamp)

        entry.visibleRegion(FlickerComponentName.STATUS_BAR)
                .coversAtMost(Region.from(0, 0, 1441, 171))
        entry.visibleRegion(LAUNCHER_COMPONENT)
            .coversAtMost(Region.from(0, 0, 1440, 2961))
    }

    @Test
    fun canDetectBelowAppWindowVisibility() {
        assertThat(trace)
            .entry(traceFirstFrameTimestamp)
            .containsNonAppWindow(WALLPAPER_COMPONENT)
    }

    @Test
    fun canDetectAppWindowVisibility() {
        assertThat(trace)
            .entry(traceFirstFrameTimestamp)
            .containsAppWindow(LAUNCHER_COMPONENT)

        assertThat(trace)
            .entry(traceFirstChromeFlashScreenTimestamp)
            .containsAppWindow(CHROME_SPLASH_SCREEN_COMPONENT)
    }

    @Test
    fun canDetectAppWindowVisibilitySubject() {
        val trace = readWmTraceFromFile("wm_trace_launcher_visible_background.pb")
        val firstEntry = assertThat(trace).first()
        val appWindowNames = firstEntry.wmState.appWindows.map { it.name }
        firstEntry.verify("has1AppWindow").that(appWindowNames).hasSize(3)
        firstEntry.verify("has1AppWindow").that(appWindowNames)
                .contains("com.android.server.wm.flicker.testapp/" +
                        "com.android.server.wm.flicker.testapp.SimpleActivity")
    }

    @Test
    fun canDetectLauncherVisibility() {
        val trace = readWmTraceFromFile("wm_trace_launcher_visible_background.pb")
        val subject = assertThat(trace)
        val firstTrace = subject.first()
        firstTrace.isAppWindowInvisible(LAUNCHER_COMPONENT)

        // launcher is at the same time visible an invisible because it
        // contains 2 windows with the exact same name
        val lastTrace = subject.last()
        lastTrace.isAppWindowInvisible(LAUNCHER_COMPONENT)

        subject.isAppWindowNotOnTop(LAUNCHER_COMPONENT)
            .isAppWindowInvisible(LAUNCHER_COMPONENT)
            .then()
            .isAppWindowOnTop(LAUNCHER_COMPONENT)
            .forAllEntries()

        subject.isAppWindowInvisible(LAUNCHER_COMPONENT)
            .forAllEntries()
    }

    @Test
    fun canFailWithReasonForVisibilityChecks_windowNotFound() {
        val failure = assertThrows(FlickerSubjectException::class.java) {
            assertThat(trace)
                .entry(traceFirstFrameTimestamp)
                .containsNonAppWindow(IMAGINARY_COMPONENT)
        }
        assertFailure(failure).hasMessageThat()
            .contains(IMAGINARY_COMPONENT.packageName)
    }

    @Test
    fun canFailWithReasonForVisibilityChecks_windowNotVisible() {
        val failure = assertThrows(FlickerSubjectException::class.java) {
            assertThat(trace)
                .entry(traceFirstFrameTimestamp)
                .containsNonAppWindow(FlickerComponentName.IME)
                .isNonAppWindowVisible(FlickerComponentName.IME)
        }
        assertFailure(failure).factValue("Is Invisible")
            .contains(FlickerComponentName.IME.packageName)
    }

    @Test
    fun canDetectAppZOrder() {
        assertThat(trace)
            .entry(traceFirstChromeFlashScreenTimestamp)
            .containsAppWindow(LAUNCHER_COMPONENT)
            .isAppWindowVisible(LAUNCHER_COMPONENT)
            .isAboveWindow(CHROME_SPLASH_SCREEN_COMPONENT, LAUNCHER_COMPONENT)
            .isAppWindowOnTop(LAUNCHER_COMPONENT)
    }

    @Test
    fun canFailWithReasonForZOrderChecks_windowNotOnTop() {
        val failure = assertThrows(FlickerSubjectException::class.java) {
            assertThat(trace)
                .entry(traceFirstChromeFlashScreenTimestamp)
                .isAppWindowOnTop(CHROME_SPLASH_SCREEN_COMPONENT)
        }
        assertFailure(failure)
            .factValue("Found")
            .contains(LAUNCHER_COMPONENT.packageName)
    }

    @Test
    fun canDetectActivityVisibility() {
        val trace = readWmTraceFromFile("wm_trace_split_screen.pb")
        val lastEntry = assertThat(trace).last()
        lastEntry.isAppWindowVisible(SHELL_SPLIT_SCREEN_PRIMARY_COMPONENT)
        lastEntry.isAppWindowVisible(SHELL_SPLIT_SCREEN_SECONDARY_COMPONENT)
    }

    @Test
    fun canHandleNoSubjects() {
        val emptyRootContainer = RootWindowContainer(
            WindowContainer(
                title = "root",
                token = "",
                orientation = 0,
                layerId = 0,
                _isVisible = true,
                children = emptyArray(),
                configurationContainer = ConfigurationContainer(null, null, null)
            )
        )
        val noWindowsState = WindowManagerState(
            where = "",
            policy = null,
            focusedApp = "",
            focusedDisplayId = 0,
            focusedWindow = "",
            inputMethodWindowAppToken = "",
            isHomeRecentsComponent = false,
            isDisplayFrozen = false,
            pendingActivities = emptyArray(),
            root = emptyRootContainer,
            keyguardControllerState = KeyguardControllerState(
                isAodShowing = false,
                isKeyguardShowing = false,
                keyguardOccludedStates = mapOf()
            )
        )

        val mockComponent = FlickerComponentName("", "Mock")

        val failure = assertThrows(FlickerSubjectException::class.java) {
            WindowManagerStateSubject
                .assertThat(noWindowsState).isAppWindowOnTop(mockComponent)
        }
        assertFailure(failure).hasMessageThat().contains("No visible app windows found")
    }

    @Test
    fun canDetectNoVisibleAppWindows() {
        val trace = readWmTraceFromFile("wm_trace_unlock.pb")
        val firstEntry = assertThat(trace).first()
        firstEntry.hasNoVisibleAppWindow()
    }

    @Test
    fun canDetectHasVisibleAppWindows() {
        val trace = readWmTraceFromFile("wm_trace_unlock.pb")
        val lastEntry = assertThat(trace).last()
        val failure = assertThrows(FlickerSubjectException::class.java) {
            lastEntry.hasNoVisibleAppWindow()
        }
        assertFailure(failure).hasMessageThat().contains("Found visible windows")
    }

    @Test
    fun canDetectTaskFragment() {
        // Verify if parser can read a dump file with 2 TaskFragments showed side-by-side.
        val trace = readWmTraceFromDumpFile("wm_trace_taskfragment.winscope")
        // There's only one entry in dump file.
        val entry = assertThat(trace).first()
        // Verify there's exact 2 TaskFragments in window hierarchy.
        Truth.assertThat(entry.wmState.taskFragments.size).isEqualTo(2)
    }
}

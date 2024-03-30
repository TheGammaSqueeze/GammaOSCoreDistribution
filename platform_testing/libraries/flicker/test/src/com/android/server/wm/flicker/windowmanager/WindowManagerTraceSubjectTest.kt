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

import com.android.server.wm.flicker.CHROME_COMPONENT
import com.android.server.wm.flicker.CHROME_SPLASH_SCREEN_COMPONENT
import com.android.server.wm.flicker.IMAGINARY_COMPONENT
import com.android.server.wm.flicker.IME_ACTIVITY_COMPONENT
import com.android.server.wm.flicker.LAUNCHER_COMPONENT
import com.android.server.wm.flicker.SCREEN_DECOR_COMPONENT
import com.android.server.wm.flicker.WALLPAPER_COMPONENT
import com.android.server.wm.flicker.assertFailure
import com.android.server.wm.flicker.assertThatErrorContainsDebugInfo
import com.android.server.wm.flicker.assertThrows
import com.android.server.wm.flicker.readWmTraceFromFile
import com.android.server.wm.flicker.traces.FlickerSubjectException
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject.Companion.assertThat
import com.android.server.wm.traces.common.FlickerComponentName
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [WindowManagerTraceSubject] tests. To run this test: `atest
 * FlickerLibTest:WindowManagerTraceSubjectTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WindowManagerTraceSubjectTest {
    private val chromeTrace by lazy { readWmTraceFromFile("wm_trace_openchrome.pb") }
    private val imeTrace by lazy { readWmTraceFromFile("wm_trace_ime.pb") }

    @Test
    fun testVisibleAppWindowForRange() {
        assertThat(chromeTrace)
            .isAppWindowOnTop(LAUNCHER_COMPONENT)
            .isAboveAppWindowVisible(SCREEN_DECOR_COMPONENT)
            .forRange(9213763541297L, 9215536878453L)

        assertThat(chromeTrace)
            .isAppWindowOnTop(LAUNCHER_COMPONENT)
            .isAppWindowInvisible(CHROME_SPLASH_SCREEN_COMPONENT)
            .isAboveAppWindowVisible(SCREEN_DECOR_COMPONENT)
            .then()
            .isAppWindowOnTop(CHROME_SPLASH_SCREEN_COMPONENT)
            .isAppWindowInvisible(LAUNCHER_COMPONENT)
            .isAboveAppWindowVisible(SCREEN_DECOR_COMPONENT)
            .then()
            .isAppWindowOnTop(CHROME_COMPONENT)
            .isAppWindowInvisible(LAUNCHER_COMPONENT)
            .isAboveAppWindowVisible(SCREEN_DECOR_COMPONENT)
            .forRange(9215551505798L, 9216093628925L)
    }

    @Test
    fun testCanTransitionInAppWindow() {
        assertThat(chromeTrace)
            .isAppWindowOnTop(LAUNCHER_COMPONENT)
            .isAboveAppWindowVisible(SCREEN_DECOR_COMPONENT)
            .then()
            .isAppWindowOnTop(CHROME_SPLASH_SCREEN_COMPONENT)
            .isAboveAppWindowVisible(SCREEN_DECOR_COMPONENT)
            .then()
            .isAppWindowOnTop(CHROME_COMPONENT)
            .isAboveAppWindowVisible(SCREEN_DECOR_COMPONENT)
            .forAllEntries()
    }

    @Test
    fun testCanDetectTransitionWithOptionalValue() {
        val trace = readWmTraceFromFile("wm_trace_open_from_overview.pb")
        val subject = assertThat(trace)
        subject.isAppWindowOnTop(LAUNCHER_COMPONENT)
                .then()
                .isAppWindowOnTop(FlickerComponentName.SNAPSHOT)
                .then()
                .isAppWindowOnTop(CHROME_COMPONENT)
    }

    @Test
    fun testCanTransitionInAppWindow_withOptional() {
        assertThat(chromeTrace)
                .isAppWindowOnTop(LAUNCHER_COMPONENT)
                .isAboveAppWindowVisible(SCREEN_DECOR_COMPONENT)
                .then()
                .isAppWindowOnTop(CHROME_SPLASH_SCREEN_COMPONENT)
                .isAboveAppWindowVisible(SCREEN_DECOR_COMPONENT)
                .then()
                .isAppWindowOnTop(CHROME_COMPONENT)
                .isAboveAppWindowVisible(SCREEN_DECOR_COMPONENT)
                .forAllEntries()
    }

    @Test
    fun testCanInspectBeginning() {
        assertThat(chromeTrace)
            .first()
            .isAppWindowOnTop(LAUNCHER_COMPONENT)
            .containsAboveAppWindow(SCREEN_DECOR_COMPONENT)
    }

    @Test
    fun testCanInspectAppWindowOnTop() {
        assertThat(chromeTrace)
            .first()
            .isAppWindowOnTop(LAUNCHER_COMPONENT)

        val failure = assertThrows(FlickerSubjectException::class.java) {
            assertThat(chromeTrace)
                .first()
                .isAppWindowOnTop(IMAGINARY_COMPONENT)
                .fail("Could not detect the top app window")
        }
        assertFailure(failure).hasMessageThat().contains("ImaginaryWindow")
    }

    @Test
    fun testCanInspectEnd() {
        assertThat(chromeTrace)
            .last()
            .isAppWindowOnTop(CHROME_COMPONENT)
            .containsAboveAppWindow(SCREEN_DECOR_COMPONENT)
    }

    @Test
    fun testCanTransitionNonAppWindow() {
        assertThat(imeTrace)
            .skipUntilFirstAssertion()
            .isNonAppWindowInvisible(FlickerComponentName.IME)
            .then()
            .isNonAppWindowVisible(FlickerComponentName.IME)
            .forAllEntries()
    }

    @Test(expected = AssertionError::class)
    fun testCanDetectOverlappingWindows() {
        assertThat(imeTrace)
            .noWindowsOverlap(FlickerComponentName.IME, FlickerComponentName.NAV_BAR,
                    IME_ACTIVITY_COMPONENT)
            .forAllEntries()
    }

    @Test
    fun testCanTransitionAboveAppWindow() {
        assertThat(imeTrace)
            .skipUntilFirstAssertion()
            .isAboveAppWindowInvisible(FlickerComponentName.IME)
            .then()
            .isAboveAppWindowVisible(FlickerComponentName.IME)
            .forAllEntries()
    }

    @Test
    fun testCanTransitionBelowAppWindow() {
        val trace = readWmTraceFromFile("wm_trace_open_app_cold.pb")
        assertThat(trace)
            .skipUntilFirstAssertion()
            .isBelowAppWindowVisible(WALLPAPER_COMPONENT)
            .then()
            .isBelowAppWindowInvisible(WALLPAPER_COMPONENT)
            .forAllEntries()
    }

    @Test
    fun testCanDetectVisibleWindowsMoreThanOneConsecutiveEntry() {
        val trace = readWmTraceFromFile("wm_trace_valid_visible_windows.pb")
        assertThat(trace).visibleWindowsShownMoreThanOneConsecutiveEntry().forAllEntries()
    }

    @Test
    fun testCanAssertWindowStateSequence() {
        val windowStates = assertThat(chromeTrace).windowStates(
            "com.android.chrome/org.chromium.chrome.browser.firstrun.FirstRunActivity")
        val visibilityChange = windowStates.zipWithNext { current, next ->
            current.windowState?.isVisible != next.windowState?.isVisible
        }

        Truth.assertWithMessage("Visibility should have changed only 1x in the trace")
            .that(visibilityChange.count { it })
            .isEqualTo(1)
    }

    @Test
    fun exceptionContainsDebugInfo() {
        val error = assertThrows(AssertionError::class.java) {
            assertThat(chromeTrace).isEmpty()
        }
        assertThatErrorContainsDebugInfo(error, withBlameEntry = false)
    }

    @Test
    fun testCanDetectSnapshotStartingWindow() {
        val trace = readWmTraceFromFile("quick_switch_to_app_killed_in_background_trace.pb")
        val app1 = FlickerComponentName("com.android.server.wm.flicker.testapp",
            "com.android.server.wm.flicker.testapp.ImeActivity")
        val app2 = FlickerComponentName("com.android.server.wm.flicker.testapp",
            "com.android.server.wm.flicker.testapp.SimpleActivity")
        assertThat(trace).isAppWindowVisible(app1)
            .then()
            .isAppSnapshotStartingWindowVisibleFor(app2, isOptional = true)
            .then()
            .isAppWindowVisible(app2)
            .then()
            .isAppSnapshotStartingWindowVisibleFor(app1, isOptional = true)
            .then()
            .isAppWindowVisible(app1)
            .forAllEntries()

        val failure = assertThrows(FlickerSubjectException::class.java) {
            assertThat(trace)
                .isAppWindowVisible(app1)
                .then()
                .isAppWindowVisible(app2)
                .then()
                .isAppWindowVisible(app1)
                .forAllEntries()
        }
        assertFailure(failure).hasMessageThat().contains("Is Invisible")
    }
}

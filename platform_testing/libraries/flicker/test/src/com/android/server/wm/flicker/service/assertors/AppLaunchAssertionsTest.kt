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

package com.android.server.wm.flicker.service.assertors

import com.android.server.wm.flicker.readLayerTraceFromFile
import com.android.server.wm.flicker.readTestFile
import com.android.server.wm.flicker.readWmTraceFromFile
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.tags.Transition
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains tests for App Launch assertions. To run this test:
 * `atest FlickerLibTest:AppLaunchAssertionsTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppLaunchAssertionsTest {
    private val jsonByteArray = readTestFile("assertors/config.json")
    private val assertions =
        AssertionConfigParser.parseConfigFile(String(jsonByteArray))
            .filter { it.transitionType == Transition.APP_LAUNCH }

    private val appLaunchAssertor = TransitionAssertor(assertions) { }

    @Test
    fun testValidAppLaunchTraces() {
        val wmTrace = readWmTraceFromFile(
            "assertors/appLaunch/WindowManagerTrace.winscope")
        val layersTrace = readLayerTraceFromFile(
            "assertors/appLaunch/SurfaceFlingerTrace.winscope")
        val errorTrace = appLaunchAssertor.analyze(VALID_APP_LAUNCH_TAG, wmTrace, layersTrace)

        Truth.assertThat(errorTrace).isEmpty()
    }

    @Test
    fun testInvalidAppLaunchTraces() {
        val wmTrace = readWmTraceFromFile(
            "assertors/appLaunch/WindowManagerInvalidTrace.winscope")
        val layersTrace = readLayerTraceFromFile(
            "assertors/appLaunch/SurfaceFlingerInvalidTrace.winscope")
        val errorTrace = appLaunchAssertor.analyze(INVALID_APP_LAUNCH_TAG, wmTrace, layersTrace)

        Truth.assertThat(errorTrace).isNotEmpty()
        Truth.assertThat(errorTrace.entries.size).isEqualTo(4)
    }

    companion object {
        private val VALID_APP_LAUNCH_TAG = Tag(1, Transition.APP_LAUNCH, true,
            windowToken = "e91fbda")
        private val INVALID_APP_LAUNCH_TAG = Tag(2, Transition.APP_LAUNCH, true,
            windowToken = "ffc3b1f")
    }
}
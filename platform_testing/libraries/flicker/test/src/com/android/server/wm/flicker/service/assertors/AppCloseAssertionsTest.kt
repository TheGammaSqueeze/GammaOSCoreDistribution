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
 * Contains tests for App Close assertions. To run this test:
 * `atest FlickerLibTest:AppCloseAssertionsTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppCloseAssertionsTest {
    private val jsonByteArray = readTestFile("assertors/config.json")
    private val assertions =
        AssertionConfigParser.parseConfigFile(String(jsonByteArray))
            .filter { it.transitionType == Transition.APP_CLOSE }

    private val appCloseAssertor = TransitionAssertor(assertions) { }

    @Test
    fun testValidAppCloseTraces() {
        val wmTrace = readWmTraceFromFile(
            "assertors/appClose/WindowManagerTrace.winscope")
        val layersTrace = readLayerTraceFromFile(
            "assertors/appClose/SurfaceFlingerTrace.winscope")
        val errorTrace = appCloseAssertor.analyze(VALID_APP_CLOSE_TAG, wmTrace, layersTrace)

        Truth.assertThat(errorTrace).isEmpty()
    }

    @Test
    fun testInvalidAppCloseTraces() {
        val wmTrace = readWmTraceFromFile(
            "assertors/appClose/WindowManagerInvalidTrace.winscope")
        val layersTrace = readLayerTraceFromFile(
            "assertors/appClose/SurfaceFlingerInvalidTrace.winscope")
        val errorTrace = appCloseAssertor.analyze(INVALID_APP_CLOSE_TAG, wmTrace, layersTrace)

        Truth.assertWithMessage("Number of entries with failures")
            .that(errorTrace.entries)
            .asList()
            .hasSize(5)
        val numErrors = errorTrace.entries.sumOf { it.errors.size }
        Truth.assertWithMessage("Numer of errors")
            .that(numErrors)
            .isEqualTo(5)
    }

    companion object {
        private val VALID_APP_CLOSE_TAG = Tag(1, Transition.APP_CLOSE, true,
            windowToken = "b0e56e5")
        private val INVALID_APP_CLOSE_TAG = Tag(2, Transition.APP_CLOSE, true,
            windowToken = "31291d8")
    }
}
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
 * Contains tests for Pip Exit assertions. To run this test:
 * `atest FlickerLibTest:PipExitAssertionsTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PipExitAssertionsTest {
    private val jsonByteArray = readTestFile("assertors/config.json")
    private val assertions =
        AssertionConfigParser.parseConfigFile(String(jsonByteArray))
            .filter { it.transitionType == Transition.PIP_EXIT }

    private val pipExitAssertor = TransitionAssertor(assertions) { }

    @Test
    fun testValidPipExitTraces() {
        val wmTrace = readWmTraceFromFile(
            "assertors/pip/exit/WindowManagerTrace.winscope")
        val layersTrace = readLayerTraceFromFile(
            "assertors/pip/exit/SurfaceFlingerTrace.winscope")
        val errorTrace = pipExitAssertor.analyze(VALID_PIP_EXIT_TAG, wmTrace, layersTrace)

        Truth.assertThat(errorTrace).isEmpty()
    }

    @Test
    fun testInvalidPipExitTraces() {
        val wmTrace = readWmTraceFromFile(
            "assertors/pip/exit/WindowManagerInvalidTrace.winscope")
        val layersTrace = readLayerTraceFromFile(
            "assertors/pip/exit/SurfaceFlingerInvalidTrace.winscope")
        val errorTrace = pipExitAssertor.analyze(INVALID_PIP_EXIT_TAG, wmTrace, layersTrace)

        Truth.assertThat(errorTrace).isNotEmpty()
        Truth.assertThat(errorTrace.entries).asList().hasSize(2)
        val allErrors = errorTrace.entries.flatMap { it.errors.toList() }
        Truth.assertThat(allErrors).hasSize(2)
    }

    companion object {
        private val VALID_PIP_EXIT_TAG = Tag(1, Transition.PIP_ENTER, true,
            layerId = 180)
        private val INVALID_PIP_EXIT_TAG = Tag(2, Transition.PIP_ENTER, true,
            layerId = 188)
    }
}
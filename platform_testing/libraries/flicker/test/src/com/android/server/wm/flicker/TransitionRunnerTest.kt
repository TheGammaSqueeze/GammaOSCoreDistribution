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

import android.view.WindowManagerGlobal
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.android.server.wm.flicker.FlickerRunResult.Companion.RunStatus
import com.android.server.wm.flicker.dsl.FlickerBuilder
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.mockito.junit.MockitoJUnitRunner
import java.lang.RuntimeException

/**
 * Contains [TransitionRunner] tests.
 *
 * To run this test: `atest FlickerLibTest:TransitionRunnerTest`
 */
@RunWith(MockitoJUnitRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TransitionRunnerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun assertTracingStopped() {
        val windowManager = WindowManagerGlobal.getWindowManagerService()
        Truth.assertWithMessage("Layers Trace not stopped")
                .that(windowManager.isLayerTracing).isFalse()
        Truth.assertWithMessage("WM Trace not stopped")
                .that(windowManager.isWindowTraceEnabled).isFalse()
    }

    @Before
    fun clearOutputDir() {
        SystemUtil.runShellCommand("rm -rf ${getDefaultFlickerOutputDir()}")
    }

    @Test
    fun canRunTransition() {
        val runner = TransitionRunner()
        var executed = false
        val flicker = FlickerBuilder(instrumentation)
            .apply {
                transitions {
                    executed = true
                }
            }.build(runner)
        Truth.assertThat(executed).isFalse()
        val result = runner.execute(flicker)
        runner.cleanUp()
        Truth.assertThat(executed).isTrue()
        Truth.assertThat(result.executionErrors).isEmpty()
        Truth.assertThat(result.successfulRuns).hasSize(4)
    }

    @Test
    fun storesTransitionExecutionErrors() {
        val runner = TransitionRunner()
        val flicker = FlickerBuilder(instrumentation)
            .apply {
                transitions {
                    throw RuntimeException("Failed to execute transition")
                }
            }.build(runner)
        val result = runner.execute(flicker)
        runner.cleanUp()
        Truth.assertThat(result.executionErrors).isNotEmpty()
    }

    @Test
    fun keepsSuccessfulTransitionExecutions() {
        val repetitions = 3
        var transitionRunCounter = 0

        val runner = TransitionRunner()
        val flicker = FlickerBuilder(instrumentation)
                .apply {
                    transitions {
                        transitionRunCounter++
                        if (transitionRunCounter == repetitions) {
                            // fail on last transition repetition
                            throw RuntimeException("Failed to execute transition")
                        }
                    }
                }.repeat { repetitions }.build(runner)
        val result = runner.execute(flicker)
        runner.cleanUp()
        Truth.assertThat(result.executionErrors).isNotEmpty()
        // One for each monitor for each repetition expect the last one
        // for which the transition failed to execute
        val expectedResultCount = flicker.traceMonitors.size * (repetitions - 1)
        Truth.assertThat(result.successfulRuns.size).isEqualTo(expectedResultCount)
    }

    @Test
    fun storesSuccessExecutionStatusInRunResult() {
        val runner = TransitionRunner()
        val flicker = FlickerBuilder(instrumentation)
                .apply {
                    transitions {}
                }.repeat { 3 }.build(runner)
        val results = runner.execute(flicker).runResults
        for (result in results) {
            Truth.assertThat(result.status).isEqualTo(RunStatus.ASSERTION_SUCCESS)
        }
    }

    @Test
    fun storesFailedExecutionStatusInRunResult() {
        val runner = TransitionRunner()
        val flicker = FlickerBuilder(instrumentation)
                .apply {
                    transitions {
                        throw RuntimeException("Failed to execute transition")
                    }
                }.repeat { 3 }.build(runner)
        val results = runner.execute(flicker).runResults
        for (result in results) {
            Truth.assertThat(result.status).isEqualTo(RunStatus.RUN_FAILED)
        }
    }

    @Test
    fun savesTraceOnTransitionExecutionErrors() {
        val runner = TransitionRunner()
        val flicker = FlickerBuilder(instrumentation)
                .apply {
                    transitions {
                        throw Throwable()
                    }
                }
                .build(runner)
        runner.execute(flicker)

        assertArchiveContainsAllTraces(runStatus = RunStatus.RUN_FAILED)
    }

    @Test
    fun savesTraceOnRunCleanupErrors() {
        val runner = TransitionRunner()
        val flicker = FlickerBuilder(instrumentation)
                .apply {
                    transitions {}
                    teardown {
                        eachRun {
                            throw RuntimeException("Fail on run teardown")
                        }
                    }
                }
                .build(runner)
        runner.execute(flicker)

        assertArchiveContainsAllTraces(runStatus = RunStatus.RUN_FAILED)
    }

    @Test
    fun savesTraceOnTestCleanupErrors() {
        val runner = TransitionRunner()
        val flicker = FlickerBuilder(instrumentation)
                .apply {
                    transitions {}
                    teardown {
                        test {
                            throw RuntimeException("Fail on test teardown")
                        }
                    }
                }
                .build(runner)
        runner.execute(flicker)

        assertArchiveContainsAllTraces(runStatus = RunStatus.RUN_FAILED)
    }
}

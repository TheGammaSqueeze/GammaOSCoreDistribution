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

package com.android.server.wm.flicker.rules

import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.wm.flicker.FlickerTestParameter
import com.android.server.wm.flicker.dsl.AssertionTag
import com.android.server.wm.flicker.service.FlickerService
import com.android.server.wm.flicker.service.assertors.AssertionConfigParser
import com.android.server.wm.flicker.service.assertors.AssertionData
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject
import com.android.server.wm.traces.common.errors.ErrorTrace
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.regex.Pattern

/**
 * A test rule reusing flicker data from [FlickerTestParameter], and fetching the traces
 * to call the WM Flicker Service after the test and report metrics from the results.
 */
@Deprecated("This test rule should be only used with legacy flicker tests. " +
    "For new tests use WMFlickerServiceRule instead")
class WMFlickerServiceRuleForTestSpec(
    private val testSpec: FlickerTestParameter
) : TestWatcher() {
    private val flickerResultsCollector = FlickerResultsCollector()

    init {
        flickerResultsCollector.instrumentation = InstrumentationRegistry.getInstrumentation()
    }

    override fun starting(description: Description?) {
        val runParameterString = extractRunParameterStringFromMethodName(description?.methodName)
        val cuj = if (runParameterString.isNullOrBlank()) {
            description?.className
        } else {
            "${description?.className}[$runParameterString]"
        }
        flickerResultsCollector.setCriticalUserJourneyName(cuj)
        flickerResultsCollector.testStarted(description)
    }

    private fun extractRunParameterStringFromMethodName(methodName: String?): String? {
        if (methodName.isNullOrBlank()) {
            return null
        }

        val pattern = Pattern.compile("\\[(.+)\\]")
        val matcher = pattern.matcher(methodName)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    override fun finished(description: Description?) {
        flickerResultsCollector.testFinished(description)
    }

    fun getMetrics(): Map<String, Int> {
        return flickerResultsCollector.getMetrics()
    }

    private fun checkFlicker(category: String): List<ErrorTrace> {
        // run flicker if it was not executed before
        testSpec.result ?: testSpec.assertWm { isNotEmpty() }

        val errors = mutableListOf<ErrorTrace>()
        val result = testSpec.result ?: error("No flicker results for $testSpec")
        val assertions = AssertionData.readConfiguration().filter { it.category == category }
        val flickerService = FlickerService(assertions)

        result.successfulRuns
            .filter { it.assertionTag == AssertionTag.ALL }
            .filter {
                val hasWmTrace = it.wmSubject?.let { true } ?: false
                val hasLayersTrace = it.layersSubject?.let { true } ?: false
                hasWmTrace || hasLayersTrace
            }
            .forEach { run ->
                val wmSubject = run.wmSubject as WindowManagerTraceSubject
                val layersSubject = run.layersSubject as LayersTraceSubject

                val outputDir = run.mTraceFile?.traceFile?.parent
                        ?: error("Output dir not detected")

                val wmTrace = wmSubject.trace
                val layersTrace = layersSubject.trace
                val (errorTrace, assertionsResults) =
                        flickerService.process(wmTrace, layersTrace, outputDir)
                errors.add(errorTrace)
                flickerResultsCollector.postRunResults(assertionsResults)
            }

        return errors
    }

    /**
     * @return true if all assertions pass, false otherwise
     */
    @JvmOverloads
    fun checkPresubmitAssertions(failOnAssertionFailure: Boolean = true): Boolean {
        val errors = checkFlicker(AssertionConfigParser.PRESUBMIT_KEY)
        return handleErrors(errors, failOnAssertionFailure)
    }

    /**
     * @return true if all assertions pass, false otherwise
     */
    fun checkPostsubmitAssertions(failOnAssertionFailure: Boolean = true): Boolean {
        val errors = checkFlicker(AssertionConfigParser.POSTSUBMIT_KEY)
        return handleErrors(errors, failOnAssertionFailure)
    }

    /**
     * @return true if all assertions pass, false otherwise
     */
    fun checkFlakyAssertions(failOnAssertionFailure: Boolean = true): Boolean {
        val errors = checkFlicker(AssertionConfigParser.FLAKY_KEY)
        return handleErrors(errors, failOnAssertionFailure)
    }

    private fun handleErrors(errors: List<ErrorTrace>, failOnAssertionFailure: Boolean): Boolean {
        return if (failOnAssertionFailure) {
            failIfAnyError(errors)
        } else {
            !hasErrors(errors)
        }
    }

    /**
     * @return true if there were no errors
     */
    private fun failIfAnyError(errors: List<ErrorTrace>): Boolean {
        val errorMsg = errors.joinToString("\n") { runs ->
            runs.entries.joinToString { state ->
                state.errors.joinToString { "${it.assertionName}\n${it.message}" }
            }
        }
        if (errorMsg.isNotEmpty()) {
            error(errorMsg)
        }
        return true
    }

    private fun hasErrors(errors: List<ErrorTrace>): Boolean {
        return errors.any { runs ->
            runs.entries.any { state ->
                state.errors.any { error ->
                    error.assertionName.isNotEmpty()
                }
            }
        }
    }
}

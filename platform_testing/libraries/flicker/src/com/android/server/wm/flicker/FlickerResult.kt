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

import com.android.server.wm.flicker.FlickerRunResult.Companion.RunStatus
import com.android.server.wm.flicker.assertions.AssertionData
import com.android.server.wm.flicker.assertions.FlickerAssertionError
import com.google.common.truth.Truth

/**
 * Result of a flicker run, including transitions, errors and create tags
 */
data class FlickerResult(
    /**
     * Result of each transition run
     */
    @JvmField val runResults: List<FlickerRunResult> = listOf(),
    /**
     * List of test created during the execution
     */
    @JvmField val tags: Set<String> = setOf(),
    /**
     * Execution errors which happened during the execution of the Flicker test
     */
    @JvmField val executionErrors: List<Throwable> = listOf()
) {
    init {
        for (result in runResults) {
            require(result.status != RunStatus.UNDEFINED) {
                "Can't create FlickerResult from RunResult ${result.traceName} " +
                        "(${result.assertionTag}) with UNDEFINED status."
            }
        }
    }

    /** Successful runs on which we can run assertions */
    val successfulRuns: List<FlickerRunResult> = runResults.filter { it.isSuccessfulRun }
    /** Failed runs due to execution errors which we shouldn't run assertions on */
    private val failedRuns: List<FlickerRunResult> = runResults.filter { it.isFailedRun }

    val combinedExecutionError = CombinedExecutionError(executionErrors)

    /**
     * List of failures during assertion
     */
    private val failures: MutableList<FlickerAssertionError> = mutableListOf()

    /**
     * Run the assertion on the trace
     *
     * @throws AssertionError If the assertion fail or the transition crashed
     */
    internal fun checkAssertion(assertion: AssertionData): List<FlickerAssertionError> {
        Truth.assertWithMessage("Expected to have runResults but none were found")
                .that(runResults).isNotEmpty()
        Truth.assertWithMessage("No transitions were not executed successful")
                .that(successfulRuns).isNotEmpty()

        val filteredRuns = successfulRuns.filter { it.assertionTag == assertion.tag }
        val currFailures = filteredRuns.mapNotNull { run -> run.checkAssertion(assertion) }
        failures.addAll(currFailures)
        return currFailures
    }

    /**
     * Asserts if there have been any execution errors while running the transitions
     */
    internal fun checkForExecutionErrors() {
        if (executionErrors.isNotEmpty()) {
            if (executionErrors.size == 1) {
                throw executionErrors[0]
            }
            throw combinedExecutionError
        }
    }

    fun isEmpty(): Boolean = executionErrors.isEmpty() && successfulRuns.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    companion object {
        class CombinedExecutionError(val errors: List<Throwable>?) : Throwable() {
            override val message: String? get() {
                if (errors == null || errors.isEmpty()) {
                    return null
                }
                if (errors.size == 1) {
                    return errors[0].toString()
                }
                return "Combined Errors Of\n\t- " +
                        errors.joinToString("\n\t\tAND\n\t- ") { it.toString() } +
                        "\n[NOTE: any details below are only for the first error]"
            }

            override val cause: Throwable?
                get() = errors?.get(0)
        }
    }
}

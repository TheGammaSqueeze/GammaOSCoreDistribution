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

package com.android.server.wm.flicker.assertions

import android.util.Log
import com.android.server.wm.flicker.FLICKER_TAG
import com.google.common.truth.Fact
import kotlin.math.max

/**
 * Runs sequences of assertions on sequences of subjects.
 *
 * Starting at the first assertion and first trace entry, executes the assertions iteratively
 * on the trace until all assertions and trace entries succeed.
 *
 * @param <T> trace entry type </T>
 */
class AssertionsChecker<T : FlickerSubject> {
    private val assertions = mutableListOf<CompoundAssertion<T>>()
    private var skipUntilFirstAssertion = false

    /**
     * Empty the list of assertions.
     */
    internal fun clear() {
        assertions.clear()
    }

    /**
     * Add [assertion] to a new [CompoundAssertion] block.
     */
    fun add(name: String, isOptional: Boolean = false, assertion: Assertion<T>) {
        assertions.add(CompoundAssertion(assertion, name, isOptional))
    }

    /**
     * Append [assertion] to the last existing set of assertions.
     */
    fun append(name: String, isOptional: Boolean = false, assertion: Assertion<T>) {
        assertions.last().add(assertion, name, isOptional)
    }

    /**
     * Filters trace entries then runs assertions returning a list of failures.
     *
     * @param entries list of entries to perform assertions on
     * @return list of failed assertion results
     */
    fun test(entries: List<T>): Unit = assertChanges(entries)

    /**
     * Steps through each trace entry checking if provided assertions are true in the order they are
     * added. Each assertion must be true for at least a single trace entry.
     *
     *
     * This can be used to check for asserting a change in property over a trace. Such as
     * visibility for a window changes from true to false or top-most window changes from A to B and
     * back to A again.
     *
     *
     * It is also possible to ignore failures on initial elements, until the first assertion
     * passes, this allows the trace to be recorded for longer periods, and the checks to happen
     * only after some time.
     */
    private fun assertChanges(entries: List<T>) {
        if (assertions.isEmpty() || entries.isEmpty()) {
            return
        }

        val failures = mutableListOf<Throwable>()
        var entryIndex = 0
        var assertionIndex = 0
        var lastPassedAssertionIndex = -1
        val assertionTrace = mutableListOf<String>()
        while (assertionIndex < assertions.size && entryIndex < entries.size) {
            val currentAssertion = assertions[assertionIndex]
            val currEntry = entries[entryIndex]
            try {
                val log = "${assertionIndex + 1}/${assertions.size}:[${currentAssertion.name}]\t" +
                        "Entry: ${entryIndex + 1}/${entries.size} $currEntry"
                Log.v(FLICKER_TAG, "Checking Assertion: $log")
                assertionTrace.add(log)
                currentAssertion.invoke(currEntry)
                lastPassedAssertionIndex = assertionIndex
                entryIndex++
            } catch (e: Throwable) {
                // ignore errors at the start of the trace
                val ignoreFailure = skipUntilFirstAssertion && lastPassedAssertionIndex == -1
                if (ignoreFailure) {
                    entryIndex++
                    continue
                }
                // failure is an optional assertion, just consider it passed skip it
                if (currentAssertion.isOptional) {
                    lastPassedAssertionIndex = assertionIndex
                    assertionIndex++
                    continue
                }
                if (lastPassedAssertionIndex != assertionIndex) {
                    val prevEntry = entries[max(entryIndex - 1, 0)]
                    prevEntry.fail(e)
                }
                assertionIndex++
                if (assertionIndex == assertions.size) {
                    val prevEntry = entries[max(entryIndex - 1, 0)]
                    prevEntry.fail(e)
                }
            }
        }
        // Didn't pass any assertions
        if (lastPassedAssertionIndex == -1 && assertions.isNotEmpty() && failures.isEmpty()) {
            entries.first().fail("Assertion never passed", assertions.first())
        }

        val untestedAssertions = assertions.drop(assertionIndex + 1)
        if (failures.isEmpty() && untestedAssertions.any { !it.isOptional }) {
            val passedAssertionsFacts = assertions.take(assertionIndex)
                    .map { Fact.fact("Passed", it) }
            val untestedAssertionsFacts = untestedAssertions
                    .map { Fact.fact("Untested", it) }
            val trace = assertionTrace.map { Fact.fact("Trace", it) }
            val reason = mutableListOf<Fact>()
            reason.addAll(passedAssertionsFacts)
            reason.add(Fact.fact("Assertion never failed", assertions[assertionIndex]))
            reason.addAll(untestedAssertionsFacts)
            reason.addAll(trace)
            entries.first().fail(reason)
        }
    }

    /**
     * Ignores the first entries in the trace, until the first assertion passes. If it reaches the
     * end of the trace without passing any assertion, return a failure with the name/reason from
     * the first assertion
     */
    fun skipUntilFirstAssertion() {
        skipUntilFirstAssertion = true
    }
}

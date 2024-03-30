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

import android.util.Log
import com.android.server.wm.flicker.FLICKER_TAG
import com.android.server.wm.flicker.traces.FlickerSubjectException
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject
import com.android.server.wm.traces.common.errors.Error
import com.android.server.wm.traces.common.errors.ErrorState
import com.android.server.wm.traces.common.errors.ErrorTrace
import com.android.server.wm.traces.common.layers.LayersTrace
import com.android.server.wm.traces.common.service.ITransitionAssertor
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace

/**
 * Class that runs FASS assertions.
 */
class TransitionAssertor(
    private val assertions: List<AssertionData>,
    private val logger: (String) -> Unit
) : ITransitionAssertor {
    /** {@inheritDoc} */
    override fun analyze(
        tag: Tag,
        wmTrace: WindowManagerTrace,
        layersTrace: LayersTrace
    ): ErrorTrace {
        val errorStates = mutableMapOf<Long, MutableList<Error>>()

        errorStates.putAll(
            runCategoryAssertions(tag, wmTrace, layersTrace, AssertionConfigParser.PRESUBMIT_KEY))
        errorStates.putAll(
            runCategoryAssertions(tag, wmTrace, layersTrace, AssertionConfigParser.POSTSUBMIT_KEY))
        errorStates.putAll(
            runCategoryAssertions(tag, wmTrace, layersTrace, AssertionConfigParser.FLAKY_KEY))

        return buildErrorTrace(errorStates)
    }

    private fun runCategoryAssertions(
        tag: Tag,
        wmTrace: WindowManagerTrace,
        layersTrace: LayersTrace,
        categoryKey: String
    ): Map<Long, MutableList<Error>> {
        logger.invoke("Running assertions for $tag $categoryKey")
        val assertions = assertions.filter { it.category == categoryKey }
        return runAssertionsOnSubjects(tag, wmTrace, layersTrace, assertions)
    }

    private fun runAssertionsOnSubjects(
        tag: Tag,
        wmTrace: WindowManagerTrace,
        layersTrace: LayersTrace,
        assertions: List<AssertionData>
    ): Map<Long, MutableList<Error>> {
        val errors = mutableMapOf<Long, MutableList<Error>>()

        try {
            assertions.forEach {
                val assertion = it.assertion
                logger.invoke("Running assertion $assertion")
                val wmSubject = WindowManagerTraceSubject.assertThat(wmTrace)
                val layersSubject = LayersTraceSubject.assertThat(layersTrace)
                val result = assertion.runCatching { evaluate(tag, wmSubject, layersSubject) }
                if (result.isFailure) {
                    val layer = assertion.getFailureLayer(tag, wmSubject, layersSubject)
                    val window = assertion.getFailureWindow(tag, wmSubject, layersSubject)
                    val exception = result.exceptionOrNull() ?: error("Exception not found")
                    // if it's not a flicker exception, we don't know what
                    // happened, raise the exception back to the test
                    if (exception !is FlickerSubjectException) {
                        throw exception
                    }
                    errors.putIfAbsent(exception.timestamp, mutableListOf())
                    val errorEntry = Error(
                        stacktrace = exception.stackTraceToString(),
                        message = exception.message,
                        layerId = layer?.id ?: 0,
                        windowToken = window?.token ?: "",
                        assertionName = assertion.name
                    )
                    errors.getValue(exception.timestamp).add(errorEntry)
                }
            }
        } catch (e: NoSuchMethodException) {
            Log.e("$FLICKER_TAG-ASSERT", "Assertion method not found", e)
        } catch (e: SecurityException) {
            Log.e("$FLICKER_TAG-ASSERT", "Unable to get assertion method", e)
        }

        return errors
    }

    private fun buildErrorTrace(errors: MutableMap<Long, MutableList<Error>>): ErrorTrace {
        val errorStates = errors.map { entry ->
            val timestamp = entry.key
            val stateTags = entry.value
            ErrorState(stateTags.toTypedArray(), timestamp.toString())
        }
        return ErrorTrace(errorStates.toTypedArray())
    }
}
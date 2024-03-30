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

import android.app.Instrumentation
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.wm.flicker.service.FlickerCollectionListener
import com.android.server.wm.flicker.service.assertors.AssertionConfigParser
import com.android.server.wm.flicker.service.assertors.AssertionData
import com.android.server.wm.traces.common.errors.Error
import com.google.common.annotations.VisibleForTesting
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Call the {@link FlickerCollectionListener} and get the generated {@link ErrorTrace} from
 * the {@link FlickerService}.
 */
open class FlickerMetricsCollectorRule(
    private val collectionListener: FlickerCollectionListener = FlickerCollectionListener(),
    instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
) : TestWatcher() {

    init {
        collectionListener.instrumentation = instrumentation
    }

    override fun starting(description: Description?) {
        // The class name we get from the test description object may contain the iteration number
        // (e.g. #3 for iteration 3) at the end of the class name. We want to remove that as that
        // isn't actually part of the class name.
        val className = description?.className?.replace("\\$[0-9]+\$".toRegex(), "")
        collectionListener.setTransitionClassName(className)
        collectionListener.testStarted(description)
    }

    override fun finished(description: Description?) {
        collectionListener.testFinished(description)
    }

    private fun filterErrors(category: String): List<Error> {
        val errorTrace = collectionListener.getErrorTrace()
        val assertions = AssertionData.readConfiguration().filter {
            it.category == category
        }.map { it.assertion.name }

        return errorTrace.entries.flatMap {
            entry -> entry.errors.asList()
        }.filter { error -> assertions.contains(error.assertionName) }
    }

    fun checkPresubmitAssertions() {
        val errors = filterErrors(AssertionConfigParser.PRESUBMIT_KEY)
        failIfAnyError(errors)
    }

    fun checkPostsubmitAssertions() {
        val errors = filterErrors(AssertionConfigParser.POSTSUBMIT_KEY)
        failIfAnyError(errors)
    }

    fun checkFlakyAssertions() {
        val errors = filterErrors(AssertionConfigParser.FLAKY_KEY)
        failIfAnyError(errors)
    }

    @VisibleForTesting
    fun getMetrics(): Map<String, Int> {
        return collectionListener.getMetrics()
    }

    private fun failIfAnyError(errors: List<Error>) {
        val errorMsg = errors.joinToString("\n") { "${it.assertionName}\n${it.message}" }

        if (errorMsg.isNotEmpty()) {
            error(errorMsg)
        }
    }
}

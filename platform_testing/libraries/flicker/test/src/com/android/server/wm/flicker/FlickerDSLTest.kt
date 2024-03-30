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

import android.app.Instrumentation
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.android.server.wm.flicker.FlickerResult.Companion.CombinedExecutionError
import com.android.server.wm.flicker.FlickerRunResult.Companion.RunStatus.ASSERTION_FAILED
import com.android.server.wm.flicker.FlickerRunResult.Companion.RunStatus.ASSERTION_SUCCESS
import com.android.server.wm.flicker.TransitionRunner.Companion.TestSetupFailure
import com.android.server.wm.flicker.TransitionRunner.Companion.TestTeardownFailure
import com.android.server.wm.flicker.TransitionRunner.Companion.TransitionExecutionFailure
import com.android.server.wm.flicker.TransitionRunner.Companion.TransitionSetupFailure
import com.android.server.wm.flicker.TransitionRunner.Companion.TransitionTeardownFailure
import com.android.server.wm.flicker.assertions.AssertionData
import com.android.server.wm.flicker.assertions.FlickerAssertionError
import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.flicker.dsl.AssertionTag
import com.android.server.wm.flicker.dsl.FlickerBuilder
import com.android.server.wm.flicker.traces.eventlog.EventLogSubject
import com.android.server.wm.flicker.traces.layers.LayerTraceEntrySubject
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerStateSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.lang.RuntimeException
import kotlin.reflect.KClass

/**
 * Contains [Flicker] and [FlickerBuilder] tests.
 *
 * To run this test: `atest FlickerLibTest:FlickerDSLTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlickerDSLTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private var executed = false

    @Before
    fun before() {
        // Clear the trace output directory
        SystemUtil.runShellCommand("rm -rf $OUT_DIR && mkdir $OUT_DIR")
    }

    @Test
    fun checkBuiltWMStartAssertion() {
        val assertion = FlickerTestParameter.buildWmStartAssertion { executed = true }
        validateAssertion(assertion, WindowManagerStateSubject::class, AssertionTag.START)
        runAndAssertExecuted(assertion)
    }

    @Test
    fun checkBuiltWMEndAssertion() {
        val assertion = FlickerTestParameter.buildWmEndAssertion { executed = true }
        validateAssertion(assertion, WindowManagerStateSubject::class, AssertionTag.END)
        runAndAssertExecuted(assertion)
    }

    @Test
    fun checkBuiltWMAssertion() {
        val assertion = FlickerTestParameter.buildWMAssertion { executed = true }
        validateAssertion(assertion, WindowManagerTraceSubject::class, AssertionTag.ALL)
        runAndAssertExecuted(assertion)
    }

    @Test
    fun checkBuiltWMTagAssertion() {
        val assertion = FlickerTestParameter.buildWMTagAssertion(TAG) { executed = true }
        validateAssertion(assertion, WindowManagerStateSubject::class, TAG)
        runAndAssertExecuted(assertion)
    }

    @Test
    fun checkBuiltLayersStartAssertion() {
        val assertion = FlickerTestParameter.buildLayersStartAssertion { executed = true }
        validateAssertion(assertion, LayerTraceEntrySubject::class, AssertionTag.START)
        runAndAssertExecuted(assertion)
    }

    @Test
    fun checkBuiltLayersEndAssertion() {
        val assertion = FlickerTestParameter.buildLayersEndAssertion { executed = true }
        validateAssertion(assertion, LayerTraceEntrySubject::class, AssertionTag.END)
        runAndAssertExecuted(assertion)
    }

    @Test
    fun checkBuiltLayersAssertion() {
        val assertion = FlickerTestParameter.buildLayersAssertion { executed = true }
        validateAssertion(assertion, LayersTraceSubject::class, AssertionTag.ALL)
        runAndAssertExecuted(assertion)
    }

    @Test
    fun checkBuiltLayersTagAssertion() {
        val assertion = FlickerTestParameter.buildLayersTagAssertion(TAG) { executed = true }
        validateAssertion(assertion, LayerTraceEntrySubject::class, TAG)
        runAndAssertExecuted(assertion)
    }

    @Test
    fun checkBuiltEventLogAssertion() {
        val assertion = FlickerTestParameter.buildEventLogAssertion { executed = true }
        validateAssertion(assertion, EventLogSubject::class, AssertionTag.ALL)
        runAndAssertExecuted(assertion)
    }

    @Test
    fun supportDuplicatedTag() {
        var count = 0
        val assertion = FlickerTestParameter.buildWMTagAssertion(TAG) {
            count++
        }

        val builder = FlickerBuilder(instrumentation).apply {
            transitions {
                this.createTag(TAG)
                this.withTag(TAG) {
                    this.device.pressHome()
                }
            }
        }
        val flicker = builder.build().execute()

        flicker.checkAssertion(assertion)

        Truth.assertWithMessage("Should have asserted $TAG 2x")
            .that(count)
            .isEqualTo(2)
    }

    @Test
    fun preventInvalidTagNames() {
        try {
            val builder = FlickerBuilder(instrumentation).apply {
                transitions {
                    this.createTag("inv lid")
                }
            }
            builder.build().execute()
            Assert.fail("Should not have allowed invalid tag name")
        } catch (e: Throwable) {
            Truth.assertWithMessage("Did not validate tag name")
                .that(e.cause?.message)
                .contains("The test tag inv lid can not contain spaces")
        }
    }

    @Test
    fun assertCreatedTags() {
        val builder = FlickerBuilder(instrumentation).apply {
            transitions {
                this.createTag(TAG)
                device.pressHome()
            }
        }
        val flicker = builder.build()
        val passAssertion = FlickerTestParameter.buildWMTagAssertion(TAG) {
            this.isNotEmpty()
        }
        val ignoredAssertion = FlickerTestParameter.buildWMTagAssertion("invalid") {
            fail("`Invalid` tag was not created, so it should not " +
                "have been asserted")
        }
        flicker.checkAssertion(passAssertion)
        flicker.checkAssertion(ignoredAssertion)
    }

    @Test
    fun detectEmptyResults() {
        try {
            FlickerBuilder(instrumentation).build().execute()
            Assert.fail("Should not have allowed empty transition")
        } catch (e: Throwable) {
            Truth.assertWithMessage("Flicker did not warn of empty transitions")
                .that(e.message)
                .contains("A flicker test must include transitions to run")
        }
    }

    @Test
    fun detectCrashedTransition() {
        val exceptionMessage = "Crashed transition"
        val builder = FlickerBuilder(instrumentation)
        builder.transitions { error("Crashed transition") }
        val flicker = builder.build()
        try {
            flicker.execute()
            Assert.fail("Should have raised an exception with message $exceptionMessage")
        } catch (e: Throwable) {
            Truth.assertWithMessage("Incorrect exception type")
                    .that(e)
                    .isInstanceOf(TransitionExecutionFailure::class.java)
            Truth.assertWithMessage("Exception does not contain the original crash message")
                .that(e.message)
                .contains(exceptionMessage)
        }
    }

    @Test
    fun exceptionContainsDebugInfo() {
        val builder = FlickerBuilder(instrumentation)
        builder.transitions { device.pressHome() }
        val flicker = builder.build()
        flicker.execute()

        val error = assertThrows(AssertionError::class.java) {
            flicker.checkAssertion(FAIL_ASSERTION)
        }
        // Exception message
        Truth.assertThat(error).hasMessageThat().contains("Expected exception")
        // Subject facts
        assertThatErrorContainsDebugInfo(error)
        Truth.assertThat(error).hasMessageThat().contains("Trace file")
        Truth.assertThat(error).hasMessageThat().contains("Location")
        // Correct stack trace point
        Truth.assertThat(error).hasMessageThat().contains("FAIL_ASSERTION")
    }

    @Test
    fun canDetectTestSetupExecutionError() {
        val builder = FlickerBuilder(instrumentation)
        builder.transitions(SIMPLE_TRANSITION)
        builder.setup {
            test {
                throw RuntimeException("Failed to execute test setup")
            }
        }
        val flicker = builder.build()
        runAndAssertFlickerFailsWithException(flicker, TestSetupFailure::class.java)
    }

    @Test
    fun canDetectTransitionSetupExecutionError() {
        val builder = FlickerBuilder(instrumentation)
        builder.transitions(SIMPLE_TRANSITION)
        builder.setup {
            eachRun {
                throw RuntimeException("Failed to execute transition setup")
            }
        }
        val flicker = builder.build()
        runAndAssertFlickerFailsWithException(flicker, TransitionSetupFailure::class.java)
    }

    @Test
    fun canDetectTransitionExecutionError() {
        val builder = FlickerBuilder(instrumentation)
        builder.transitions {
            throw RuntimeException("Failed to execute transition")
        }
        val flicker = builder.build()
        runAndAssertFlickerFailsWithException(flicker, TransitionExecutionFailure::class.java)
    }

    @Test
    fun canDetectTransitionTeardownExecutionError() {
        val builder = FlickerBuilder(instrumentation)
        builder.transitions(SIMPLE_TRANSITION)
        builder.teardown {
            eachRun {
                throw RuntimeException("Failed to execute transition teardown")
            }
        }
        val flicker = builder.build()
        runAndAssertFlickerFailsWithException(flicker, TransitionTeardownFailure::class.java)
    }

    @Test
    fun canDetectTestTeardownExecutionError() {
        val builder = FlickerBuilder(instrumentation)
        builder.transitions(SIMPLE_TRANSITION)
        builder.teardown {
            test {
                throw RuntimeException("Failed to execute test teardown")
            }
        }
        val flicker = builder.build()
        runAndAssertFlickerFailsWithException(flicker, TestTeardownFailure::class.java)
    }

    @Test
    fun runsAssertionsOnSuccessfulTransitionsEvenIfSomeFailToExecute() {
        val repetitions = 3
        val builder = FlickerBuilder(instrumentation)
        failOnLastTransitionRun(builder, repetitions)
        var assertionExecutionCounter = 0
        val assertions = listOf(
                FlickerTestParameter.buildWmStartAssertion { assertionExecutionCounter++ },
                FlickerTestParameter.buildWmEndAssertion { assertionExecutionCounter++ },
                FlickerTestParameter.buildWMAssertion { assertionExecutionCounter++ },
                FlickerTestParameter.buildWMTagAssertion(TAG) { assertionExecutionCounter++ },
                FlickerTestParameter.buildLayersStartAssertion { assertionExecutionCounter++ },
                FlickerTestParameter.buildLayersEndAssertion { assertionExecutionCounter++ },
                FlickerTestParameter.buildLayersAssertion { assertionExecutionCounter++ },
                FlickerTestParameter.buildLayersTagAssertion(TAG) { assertionExecutionCounter++ },
                FlickerTestParameter.buildEventLogAssertion { assertionExecutionCounter++ }
        )
        val flicker = builder.build()
        runAndAssertFlickerFailsWithException(flicker, TransitionExecutionFailure::class.java,
                assertions = assertions)
        Truth.assertWithMessage("All assertions ran on all iterations except the last one")
                .that(assertionExecutionCounter)
                .isEqualTo(assertions.size * (repetitions - 1))
    }

    @Test
    fun canHandleAndTrackMultipleExecutionErrors() {
        val repetitions = 2
        val builder = FlickerBuilder(instrumentation)
        failOnLastTransitionRun(builder, repetitions)
        builder.teardown {
            test {
                throw RuntimeException("Failed to execute test teardown")
            }
        }
        val flicker = builder.build()
        try {
            runFlicker(flicker, PASS_ASSERTION)
            Assert.fail("Should have raised an execution exception")
        } catch (e: Throwable) {
            Truth.assertWithMessage("Incorrect exception type")
                    .that(e)
                    .isInstanceOf(CombinedExecutionError::class.java)

            val errors = (e as CombinedExecutionError).errors!!

            // All exception are shown in the error message
            for (error in errors) {
                Truth.assertThat(e.message).contains("Failed to execute last transition")
                Truth.assertThat(e.message).contains("Failed to execute test teardown")
                Truth.assertThat(e.message).contains("TransitionExecutionFailure")
                Truth.assertThat(e.message).contains("TestTeardownFailure")
            }

            // First exception is shown as cause
            Truth.assertWithMessage("Incorrect exception type")
                    .that(e.cause)
                    .isInstanceOf(TransitionExecutionFailure::class.java)
        }
    }

    @Test
    fun savesTracesOfFailedTransitionExecution() {
        val builder = FlickerBuilder(instrumentation)
        builder.transitions {
            throw RuntimeException("Failed to execute transition")
        }
        val flicker = builder.build()
        val OUT_DIR = getDefaultFlickerOutputDir()

        try {
            runFlicker(flicker, PASS_ASSERTION)
        } catch (e: TransitionExecutionFailure) {
            // A TransitionExecutionFailure is expected
        }
        assertArchiveContainsAllTraces(runStatus = FlickerRunResult.Companion.RunStatus.RUN_FAILED)
    }

    @Test
    fun savesTracesForAllIterations() {
        val runner = TransitionRunner()
        val repetitions = 5
        val flicker = FlickerBuilder(instrumentation)
                .apply {
                    transitions {}
                }
                .repeat { repetitions }
                .build(runner)
        runner.execute(flicker)

        for (iteration in 0 until repetitions) {
            assertArchiveContainsAllTraces(
                runStatus = ASSERTION_SUCCESS,
                iteration = iteration
            )
        }
    }

    @Test
    fun savesTracesAsFailureOnLayersStartAssertionFailure() {
        val assertion = FlickerTestParameter.buildLayersStartAssertion {
            throw Throwable("Failed layers start assertion")
        }
        checkTracesAreSavedWithAssertionFailure(assertion)
    }

    @Test
    fun savesTracesAsFailureOnLayersEndAssertionFailure() {
        val assertion = FlickerTestParameter.buildLayersEndAssertion {
            throw Throwable("Failed layers end assertion")
        }
        checkTracesAreSavedWithAssertionFailure(assertion)
    }

    @Test
    fun savesTracesAsFailureOnWindowsStartAssertionFailure() {
        val assertion = FlickerTestParameter.buildWmStartAssertion {
            throw Throwable("Failed wm start assertion")
        }
        checkTracesAreSavedWithAssertionFailure(assertion)
    }

    @Test
    fun savesTracesAsFailureOnWindowsEndAssertionFailure() {
        val assertion = FlickerTestParameter.buildWmEndAssertion {
            throw Throwable("Failed wm end assertion")
        }
        checkTracesAreSavedWithAssertionFailure(assertion)
    }

    private fun checkTracesAreSavedWithAssertionFailure(assertion: AssertionData) {
        val runner = TransitionRunner()
        val flicker = FlickerBuilder(instrumentation)
                .apply {
                    transitions {}
                }
                .build(runner)
        runAndAssertFlickerFailsWithException(flicker, FlickerAssertionError::class.java,
                listOf(assertion))

        assertArchiveContainsAllTraces(runStatus = ASSERTION_FAILED)
    }

    private fun runAndAssertExecuted(assertion: AssertionData) {
        executed = false
        val builder = FlickerBuilder(instrumentation)
        builder.transitions(SIMPLE_TRANSITION)
        val flicker = builder.build()
        runFlicker(flicker, assertion)
        assertAssertionExecuted()
    }

    private fun assertAssertionExecuted() {
        Truth.assertWithMessage("Assertion was not executed")
                .that(executed)
                .isTrue()
    }

    private fun runFlicker(flicker: Flicker, assertion: AssertionData) {
        runFlicker(flicker, listOf(assertion))
    }

    private fun runFlicker(flicker: Flicker, assertions: List<AssertionData>) {
        // TODO: We should probably test that these methods actually get called like this and in
        //       this order from the ParameterizedRunner/FlickerBlockJUnit4ClassRunner.
        flicker.execute()
        for (assertion in assertions) {
            flicker.checkAssertion(assertion)
        }
        flicker.clear()
    }

    private fun validateAssertion(
        assertion: AssertionData,
        expectedSubjectClass: KClass<out FlickerSubject>,
        expectedTag: String
    ) {
        Truth.assertWithMessage("Unexpected subject type")
                .that(assertion.expectedSubjectClass)
                .isEqualTo(expectedSubjectClass)
        Truth.assertWithMessage("Unexpected tag")
                .that(assertion.tag)
                .isEqualTo(expectedTag)
    }

    private fun runAndAssertFlickerFailsWithException(
        flicker: Flicker,
        clazz: Class<*>,
        assertions: List<AssertionData> = listOf(PASS_ASSERTION)
    ) {
        try {
            runFlicker(flicker, assertions)
            Assert.fail("Should have raised an execution exception")
        } catch (e: Throwable) {
            Truth.assertWithMessage("Incorrect exception type")
                    .that(e)
                    .isInstanceOf(clazz)
        }
    }

    private fun failOnLastTransitionRun(builder: FlickerBuilder, repetitions: Int) {
        var repetitionsCounter = 0
        builder.transitions {
            repetitionsCounter++
            if (repetitionsCounter == repetitions) {
                throw RuntimeException("Failed to execute last transition")
            }
            withTag(TAG) {
                device.pressHome()
            }
        }
        builder.repeat { repetitions }
    }

    companion object {
        private val TAG = "tag"
        private val SIMPLE_TRANSITION: Flicker.() -> Unit = {
            withTag(TAG) {
                device.pressHome()
            }
        }
        private val PASS_ASSERTION = AssertionData(tag = AssertionTag.END,
                expectedSubjectClass = LayerTraceEntrySubject::class) {}
        private val FAIL_ASSERTION = AssertionData(tag = AssertionTag.END,
                expectedSubjectClass = LayerTraceEntrySubject::class) {
            this.fail("Expected exception")
        }
        private val OUT_DIR = getDefaultFlickerOutputDir()
    }
}

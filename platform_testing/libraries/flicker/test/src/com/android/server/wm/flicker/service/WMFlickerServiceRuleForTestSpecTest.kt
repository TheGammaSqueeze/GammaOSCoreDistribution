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

package com.android.server.wm.flicker.service

import android.app.Instrumentation
import android.view.Surface
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.wm.flicker.FlickerBuilderProvider
import com.android.server.wm.flicker.FlickerParametersRunnerFactory
import com.android.server.wm.flicker.FlickerTestParameter
import com.android.server.wm.flicker.FlickerTestParameterFactory
import com.android.server.wm.flicker.dsl.FlickerBuilder
import com.android.server.wm.flicker.helpers.SampleAppHelper
import com.android.server.wm.flicker.helpers.wakeUpAndGoToHomeScreen
import com.android.server.wm.flicker.rules.WMFlickerServiceRuleForTestSpec
import org.hamcrest.core.StringContains
import org.hamcrest.core.StringStartsWith
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * A test for [WMFlickerServiceRuleForTestSpec] checking that metrics are reported
 * To run this test: `atest FlickerLibTest:WMFlickerServiceRuleForTestSpecTest`
 */
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WMFlickerServiceRuleForTestSpecTest(private val testSpec: FlickerTestParameter) {
    @get:Rule
    val flickerRule = WMFlickerServiceRuleForTestSpec(testSpec)

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val testApp: SampleAppHelper = SampleAppHelper(instrumentation)

    @FlickerBuilderProvider
    fun openAndCloseTestApp(): FlickerBuilder {
        return FlickerBuilder(instrumentation).apply {
            transitions {
                device.wakeUpAndGoToHomeScreen()
                wmHelper.waitForAppTransitionIdle()

                testApp.launchViaIntent(wmHelper)
                wmHelper.waitForFullScreenApp(testApp.component)

                device.pressHome()
                wmHelper.waitForHomeActivityVisible()
            }
        }
    }

    @Test
    fun runAssertion() {
        flickerRule.checkPresubmitAssertions(failOnAssertionFailure = false)
    }

    @Test
    fun hasMetricsToReport() {
        flickerRule.checkPresubmitAssertions(failOnAssertionFailure = false)
        val metrics = flickerRule.getMetrics()
        Assert.assertTrue(metrics.isNotEmpty())
    }

    @Test
    fun metricsKeyContainsCuj() {
        flickerRule.checkPresubmitAssertions(failOnAssertionFailure = false)
        val metrics = flickerRule.getMetrics()
        val cujSource = this::class.java.simpleName
        assertMetricsContain("CUJ", metrics, cujSource)
    }

    @Test
    fun metricsKeyContainsTestParams() {
        flickerRule.checkPresubmitAssertions(failOnAssertionFailure = false)
        val metrics = flickerRule.getMetrics()
        var paramsString = Surface.rotationToString(testSpec.startRotation)
        if (testSpec.endRotation != testSpec.startRotation) {
            paramsString += "_${Surface.rotationToString(testSpec.endRotation)}"
        }
        if (testSpec.navBarMode.isNotEmpty()) {
            paramsString += "_${testSpec.navBarModeName}"
        }
        assertMetricsContain("Test Params", metrics, paramsString)
    }

    @Test
    fun metricsKeyContainsFassPrefix() {
        flickerRule.checkPresubmitAssertions(failOnAssertionFailure = false)
        val metrics = flickerRule.getMetrics()
        assertMetricsContain("FASS Prefix", metrics, "FASS")
        metrics.forEach { (key, _) ->
            run {
                Assert.assertThat("Contains FASS Prefix",
                        key, StringStartsWith(false, "FASS"))
            }
        }
    }

    @Test
    fun metricsValuesAreValid() {
        flickerRule.checkPresubmitAssertions(failOnAssertionFailure = false)
        val metrics = flickerRule.getMetrics()
        metrics.forEach { (_, value) ->
            Assert.assertTrue("Value is valid", value == 0 || value == 1)
        }
    }

    private fun assertMetricsContain(message: String, metrics: Map<String, Int>, string: String) {
        metrics.forEach { (key, _) ->
            run {
                Assert.assertThat("Contains $message ($string)",
                        key, StringContains.containsString(string))
            }
        }
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTestParameter> {
            return FlickerTestParameterFactory.getInstance()
                .getConfigNonRotationTests(repetitions = 2)
        }
    }
}
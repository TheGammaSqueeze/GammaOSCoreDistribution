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

import android.platform.test.util.TestFilter
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.wm.flicker.dsl.FlickerBuilder
import org.junit.internal.runners.statements.RunAfters
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.TestWithParameters
import java.lang.reflect.Modifier

/**
 * Implements the JUnit 4 standard test case class model, parsing from a flicker DSL.
 *
 * Supports both assertions in {@link org.junit.Test} and assertions defined in the DSL
 *
 * When using this runnr the default `atest class#method` command doesn't work.
 * Instead use: -- --test-arg \
 *     com.android.tradefed.testtype.AndroidJUnitTest:instrumentation-arg:filter-tests:=<TEST_NAME>
 *
 * For example:
 * `atest FlickerTests -- \
 *     --test-arg com.android.tradefed.testtype.AndroidJUnitTest:instrumentation-arg:filter-tests\
 *     :=com.android.server.wm.flicker.close.\
 *     CloseAppBackButtonTest#launcherWindowBecomesVisible[ROTATION_90_GESTURAL_NAV]`
 */
class FlickerBlockJUnit4ClassRunner @JvmOverloads constructor(
    test: TestWithParameters,
    private val parameters: Array<Any> = test.parameters.toTypedArray(),
    private val flickerTestParameter: FlickerTestParameter? =
        parameters.filterIsInstance(FlickerTestParameter::class.java).firstOrNull()
) : BlockJUnit4ClassRunnerWithParameters(test) {
    private var flickerMethod: FrameworkMethod? = null

    /**
     * {@inheritDoc}
     */
    override fun getChildren(): MutableList<FrameworkMethod> {
        val arguments = InstrumentationRegistry.getArguments()
        val validChildren = super.getChildren().filter {
            val childDescription = describeChild(it)
            TestFilter.isFilteredOrUnspecified(arguments, childDescription)
        }
        return validChildren.toMutableList()
    }

    /**
     * {@inheritDoc}
     */
    override fun classBlock(notifier: RunNotifier): Statement {
        val statement = childrenInvoker(notifier)
        val cleanUpMethod = getFlickerCleanUpMethod()
        val frameworkMethod = FrameworkMethod(cleanUpMethod)
        return RunAfters(statement, listOf(frameworkMethod), flickerTestParameter)
    }

    /**
     * Adds to `errors` for each method annotated with `@Test`that
     * is not a public, void instance method with no arguments.
     */
    fun validateFlickerObject(errors: MutableList<Throwable>) {
        val methods = testClass.getAnnotatedMethods(FlickerBuilderProvider::class.java)

        if (methods.isEmpty() || methods.size > 1) {
            val prefix = if (methods.isEmpty()) "One" else "Only one"
            errors.add(Exception("$prefix object should be annotated with @FlickerObject"))
        } else {
            val method = methods.first()

            if (Modifier.isStatic(method.method.modifiers)) {
                errors.add(Exception("Method ${method.name}() show not be static"))
            }
            if (!Modifier.isPublic(method.method.modifiers)) {
                errors.add(Exception("Method ${method.name}() should be public"))
            }
            if (method.returnType != FlickerBuilder::class.java) {
                errors.add(Exception("Method ${method.name}() should return a " +
                    "${FlickerBuilder::class.java.simpleName} object"))
            }
            if (method.method.parameterTypes.isNotEmpty()) {
                errors.add(Exception("Method ${method.name} should have no parameters"))
            }
        }

        if (errors.isEmpty()) {
            flickerMethod = methods.first()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun createTest(): Any {
        val test = super.createTest()
        if (flickerTestParameter?.isInitialized != true) {
            Log.v(FLICKER_TAG, "Flicker object is not yet initialized")
            injectFlickerOnTestParams(test)
        }
        return test
    }

    /**
     * Builds a flicker object and assigns it to the test parameters
     */
    private fun injectFlickerOnTestParams(test: Any) {
        val flickerTestParameter = flickerTestParameter
        val flickerMethod = flickerMethod
        if (flickerTestParameter != null && flickerMethod != null) {
            val testName = test::class.java.simpleName
            Log.v(FLICKER_TAG, "Creating flicker object for $testName and adding it into " +
                "test parameter")
            val builder = flickerMethod.invokeExplosively(test) as FlickerBuilder
            flickerTestParameter.initialize(builder, testName)
        } else {
            Log.v(FLICKER_TAG, "Missing flicker builder provider method " +
                "in ${test::class.java.simpleName}")
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun validateInstanceMethods(errors: MutableList<Throwable>) {
        validateFlickerObject(errors)
        super.validateInstanceMethods(errors)
    }

    /**
     * {@inheritDoc}
     */
    override fun validateConstructor(errors: MutableList<Throwable>) {
        super.validateConstructor(errors)

        if (errors.isEmpty()) {
            // should have only one constructor, otherwise parent
            // validator will create an exception
            val ctor = testClass.javaClass.constructors.first()
            if (ctor.parameterTypes.none { it == FlickerTestParameter::class.java }) {
                errors.add(Exception("Constructor should have a parameter of type " +
                    FlickerTestParameter::class.java.simpleName))
            }
        }
    }

    /**
     * Obtains the method to clean up a flicker object cache,
     * necessary to release memory after a configuration is executed
     */
    private fun getFlickerCleanUpMethod() = FlickerTestParameter::class.java.getMethod("clear")
}

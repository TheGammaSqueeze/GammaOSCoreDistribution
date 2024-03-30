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

import android.view.Surface
import com.google.common.truth.Truth.assertWithMessage
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [FlickerTestParameterFactory] tests.
 *
 * To run this test: `atest FlickerLibTest:FlickerTestFactoryRunnerTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlickerTestParameterFactoryTest {
    private val testFactory = FlickerTestParameterFactory.getInstance()

    @Test
    fun checkBuildTest() {
        val actual = testFactory.getConfigNonRotationTests()
        assertWithMessage("Flicker should create tests for 0 and 90 degrees")
            .that(actual).hasSize(4)
    }

    @Test
    fun checkBuildRotationTest() {
        val actual = testFactory.getConfigRotationTests()
        assertWithMessage("Flicker should create tests for 0 and 90 degrees")
            .that(actual).hasSize(4)
    }

    @Test
    fun checkBuildCustomRotationsTest() {
        val rotations = listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180,
                Surface.ROTATION_270)
        val actual = testFactory.getConfigRotationTests(
            supportedRotations = rotations)
        // Should have config for each rotation pair
        assertWithMessage("Flicker should create tests for 0/90/180/270 degrees")
            .that(actual).hasSize(24)
    }
}
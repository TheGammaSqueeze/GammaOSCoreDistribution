/*
 * Copyright 2022 The Android Open Source Project
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

package platform.test.screenshot

import android.content.Context
import android.util.SparseIntArray
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A rule to overload the target context system colors by [colors].
 *
 * This is especially useful to apply the colors before you start an activity using an
 * [ActivityScenarioRule] or any other rule, given that the colors must be [applied]
 * [MaterialYouColors.apply] *before* doing any resource resolution.
 */
class MaterialYouColorsRule(private val colors: MaterialYouColors = MaterialYouColors.Orange) :
    TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                colors.apply(InstrumentationRegistry.getInstrumentation().targetContext)
                base.evaluate()
            }
        }
    }
}

/**
 * A util class to overload the Material You colors of a [Context] to some fixed values. This can be
 * used by screenshot tests so that device-specific colors don't impact the outcome of the test.
 *
 * @see apply
 */
class MaterialYouColors(
    @get:VisibleForTesting val colors: SparseIntArray,
) {
    /**
     * Apply these colors to [context].
     *
     * Important: No resource resolution must have be done on the context given to that method.
     */
    fun apply(context: Context) {
        RemoteViews.ColorResources.create(context, colors).apply(context)
    }

    companion object {
        private const val FIRST_RESOURCE_COLOR_ID = android.R.color.system_neutral1_0
        private const val LAST_RESOURCE_COLOR_ID = android.R.color.system_accent3_1000

        /**
         * An instance of [MaterialYouColors] with orange seed, that can be used directly by tests.
         */
        val Orange = fromColors(ORANGE)

        /**
         * Create a [MaterialYouColors] from [colors], where:
         * - `colors[i]` should be the value of `FIRST_RESOURCE_COLOR_ID + i`.
         * - [colors] must contain all values of all system colors, i.e. `colors.size` should be
         *   `LAST_RESOURCE_COLOR_ID - FIRST_RESOURCE_COLOR_ID + 1`.
         */
        private fun fromColors(colors: IntArray): MaterialYouColors {
            val expectedSize = LAST_RESOURCE_COLOR_ID - FIRST_RESOURCE_COLOR_ID + 1
            check(colors.size == expectedSize) {
                "colors should have exactly $expectedSize elements"
            }

            val sparseArray = SparseIntArray(/* initialCapacity= */ expectedSize)
            colors.forEachIndexed { i, color ->
                sparseArray.put(FIRST_RESOURCE_COLOR_ID + i, color)
            }

            return MaterialYouColors(sparseArray)
        }
    }
}

/** Some orange colors, extracted using 0xA66800 as seed color. */
private val ORANGE =
    intArrayOf(
        -1,
        -1025,
        -397337,
        -1318439,
        -3226179,
        -5068125,
        -6844535,
        -8620690,
        -10199721,
        -11778496,
        -13291734,
        -14738666,
        -16777216,
        -1,
        -1025,
        -4386,
        -925488,
        -2833227,
        -4675174,
        -6451583,
        -8293273,
        -9937840,
        -11516615,
        -13095132,
        -14542319,
        -16777216,
        -1,
        -1025,
        -4386,
        -8775,
        -476303,
        -2449319,
        -4422078,
        -6394838,
        -8235756,
        -10076672,
        -12113408,
        -13953280,
        -16777216,
        -1,
        -1025,
        -4386,
        -139843,
        -2113118,
        -4020599,
        -5862288,
        -7703977,
        -9348543,
        -11058389,
        -12636905,
        -14149627,
        -16777216,
        -1,
        -393241,
        -1640507,
        -2561608,
        -4403810,
        -6180476,
        -7956885,
        -9667501,
        -11246787,
        -12760281,
        -14207725,
        -15524094,
        -16777216,
    )

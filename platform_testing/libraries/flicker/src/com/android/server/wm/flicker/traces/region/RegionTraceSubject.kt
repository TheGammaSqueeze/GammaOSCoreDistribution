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

package com.android.server.wm.flicker.traces.region

import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.flicker.traces.FlickerFailureStrategy
import com.android.server.wm.flicker.traces.FlickerTraceSubject
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.Rect
import com.android.server.wm.traces.common.region.Region
import com.android.server.wm.traces.common.region.RegionTrace
import com.google.common.truth.FailureMetadata
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Subject.Factory

class RegionTraceSubject(
    fm: FailureMetadata,
    val trace: RegionTrace,
    override val parent: FlickerSubject?
) : FlickerTraceSubject<RegionSubject>(fm, trace) {

    private val components: Array<out FlickerComponentName> = trace.components

    override val subjects by lazy {
        trace.entries.map { RegionSubject.assertThat(it, this, it.timestamp) }
    }

    private val componentsAsString get() =
        if (components.isEmpty()) {
            "<any>"
        } else {
            "[" + components.joinToString() + "]"
        }

    /**
     * Asserts that the visible area covered by any element in the state covers at most
     * [testRegion], that is, if the area of no elements cover any point outside of [testRegion].
     *
     * @param testRegion Expected covered area
     */
    fun coversAtMost(
        testRegion: Region
    ): RegionTraceSubject = apply {
        addAssertion("coversAtMost($testRegion, $componentsAsString") {
            it.coversAtMost(testRegion)
        }
    }

    /**
     * Asserts that the visible area covered by any element in the state covers at most
     * [testRegion], that is, if the area of no elements cover any point outside of [testRegion].
     *
     * @param testRegion Expected covered area
     */
    fun coversAtMost(
        testRegion: Rect
    ): RegionTraceSubject = this.coversAtMost(testRegion)

    /**
     * Asserts that the visible area covered by any element in the state covers at least
     * [testRegion], that is, if the area of its elements visible region covers each point in
     * the region.
     *
     * @param testRegion Expected covered area
     */
    fun coversAtLeast(
        testRegion: Region
    ): RegionTraceSubject = apply {
        addAssertion("coversAtLeast($testRegion, $componentsAsString)") {
            it.coversAtLeast(testRegion)
        }
    }

    /**
     * Asserts that the visible area covered by any element in the state covers at least
     * [testRegion], that is, if the area of its elements visible region covers each point in
     * the region.
     *
     * @param testRegion Expected covered area
     */
    fun coversAtLeast(
        testRegion: Rect
    ): RegionTraceSubject = this.coversAtLeast(Region.from(testRegion))

    /**
     * Asserts that the visible region of the trace entries is exactly [expectedVisibleRegion].
     *
     * @param expectedVisibleRegion Expected visible region of the layer
     */
    fun coversExactly(
        expectedVisibleRegion: Region
    ): RegionTraceSubject = apply {
        addAssertion("coversExactly($expectedVisibleRegion, $componentsAsString)") {
            it.coversExactly(expectedVisibleRegion)
        }
    }

    companion object {
        /**
         * Boiler-plate Subject.Factory for RegionTraceSubject
         */
        private fun getFactory(
            parent: FlickerSubject?
        ): Factory<RegionTraceSubject, RegionTrace> =
                Factory { fm, subject -> RegionTraceSubject(fm, subject, parent) }

        /**
         * Creates a [RegionTraceSubject] representing a trace of the visible region of a
         * window or layer which can be used to make assertions.
         *
         * @param trace The region trace to assert on
         */
        @JvmStatic
        @JvmOverloads
        fun assertThat(
            trace: RegionTrace,
            parent: FlickerSubject? = null
        ): RegionTraceSubject {
            val strategy = FlickerFailureStrategy()
            val subject = StandardSubjectBuilder.forCustomFailureStrategy(strategy)
                    .about(getFactory(parent))
                    .that(trace) as RegionTraceSubject
            strategy.init(subject)
            return subject
        }

        /**
         * Static method for getting the subject factory (for use with assertAbout())
         */
        @JvmStatic
        fun entries(
            parent: FlickerSubject?
        ): Factory<RegionTraceSubject, RegionTrace> = getFactory(parent)
    }
}
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

package com.android.server.wm.flicker.service.processors

import com.android.server.wm.flicker.readLayerTraceFromFile
import com.android.server.wm.flicker.readWmTraceFromFile
import com.android.server.wm.traces.common.service.processors.PipExpandProcessor
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [PipExpandProcessor] tests. To run this test:
 * `atest FlickerLibTest:PipExpandProcessorTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PipExpandProcessorTest {
    private val processor = PipExpandProcessor { }

    private val tagsPipExpanding by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/pip/expand/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/pip/expand/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsPipEnterTwice by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/pip/enter/twice/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/pip/enter/twice/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    @Test
    fun generatesPipExpandTags() {
        val tagTrace = tagsPipExpanding
        Truth.assertWithMessage("Should have 2 pip expand tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 2881856703699 // 0d0h48m1s856ms
        val endTagTimestamp = 2882177502376 // 0d0h0m48m2s177ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun doesntTagPipEnterTwice() {
        val tagTrace = tagsPipEnterTwice
        Truth.assertWithMessage("Should have 0 pip expand tags")
            .that(tagTrace)
            .hasSize(0)
    }
}
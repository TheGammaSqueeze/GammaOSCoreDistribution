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
import com.android.server.wm.traces.common.service.processors.PipResizeProcessor
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [PipResizeProcessor] tests. To run this test:
 * `atest FlickerLibTest:PipResizeProcessorTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PipResizeProcessorTest {
    private val processor = PipResizeProcessor { }

    private val tagsPipResizingToExpand by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/pip/resize/expand/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/pip/resize/expand/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsPipResizingToShrink by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/pip/resize/shrink/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/pip/resize/shrink/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    @Test
    fun generatesPipResizeTagsOnExpand() {
        val tagTrace = tagsPipResizingToExpand
        Truth.assertWithMessage("Should have 2 pip resize tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 175188084434996 // 2d0h39m48s84ms
        val endTagTimestamp = 175188414547217 // 2d0h39m48s414ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesPipResizeTagsOnShrink() {
        val tagTrace = tagsPipResizingToShrink
        Truth.assertWithMessage("Should have 2 pip resize tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 183718779433562 // 2d3h1m58s779ms
        val endTagTimestamp = 183719115416407 // 2d3h1m59s115ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }
}
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
import com.android.server.wm.traces.common.service.processors.PipEnterProcessor
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [PipEnterProcessor] tests. To run this test:
 * `atest FlickerLibTest:PipEnterProcessorTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PipEnterProcessorTest {
    private val processor = PipEnterProcessor {}

    private val tagsPipEnterWithoutRotation by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/pip/enter/norotation/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/pip/enter/norotation/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsPipEnterWithRotation by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/pip/enter/rotation/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/pip/enter/rotation/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsPipEnterWithSplitScreen by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/pip/enter/splitscreen/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/pip/enter/splitscreen/SurfaceFlingerTrace.winscope"
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

    private val tagsStationaryPip by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/pip/enter/stationary/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/pip/enter/stationary/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    @Test
    fun generatesPipEnterTagsWithoutRotation() {
        val tagTrace = tagsPipEnterWithoutRotation
        Truth.assertWithMessage("Should have 2 PIP enter tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 224969581940 // 3m44s969ms
        val endTagTimestamp = 225315964162 // 3m45s315ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesPipEnterTagsWithRotation() {
        val tagTrace = tagsPipEnterWithRotation
        Truth.assertWithMessage("Should have 2 PIP enter tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 7546761373566 // 2h5m46s761ms
        val endTagTimestamp = 7547420542538 // 2h5m47s420ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesPipEnterTagsWithSplitScreen() {
        val tagTrace = tagsPipEnterWithSplitScreen
        Truth.assertWithMessage("Should have 2 PIP enter tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 7706716317365 // 2h8m26s716ms
        val endTagTimestamp = 7707029441615 // 2h8m27s29ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesPipEnterTagsTwice() {
        val tagTrace = tagsPipEnterTwice
        Truth.assertWithMessage("Should have 4 PIP enter tags")
            .that(tagTrace)
            .hasSize(4)
        val firstPipEnter = arrayOf(126177655536, 126747555592) // 2m6s177ms, 2m6s747ms
        val secondPipEnter = arrayOf(132780039058, 133367243856) // 2m12s780ms, 2m13s367ms
        Truth.assertThat(tagTrace[0].timestamp).isEqualTo(firstPipEnter[0])
        Truth.assertThat(tagTrace[1].timestamp).isEqualTo(firstPipEnter[1])
        Truth.assertThat(tagTrace[2].timestamp).isEqualTo(secondPipEnter[0])
        Truth.assertThat(tagTrace[3].timestamp).isEqualTo(secondPipEnter[1])
    }

    @Test
    fun doesNotGeneratePipEnterTagsOnStationaryPip() {
        val tagTrace = tagsStationaryPip
        Truth.assertWithMessage("Should have no PIP enter tags")
            .that(tagTrace)
            .hasSize(0)
    }
}
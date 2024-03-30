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
import com.android.server.wm.traces.common.service.processors.ImeAppearProcessor
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [ImeAppearProcessor] tests. To run this test:
 * `atest FlickerLibTest:ImeAppearProcessorTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ImeAppearProcessorTest {
    private val processor = ImeAppearProcessor { }

    private val tagsImeAppearWithGesture by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/ime/appear/bygesture/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/ime/appear/bygesture/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsImeAppearWithoutGestureOnAppLaunch by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/ime/appear/applaunchnogesture/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/ime/appear/applaunchnogesture/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val doesntTagStationaryIme by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/ime/appear/stationary/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/ime/appear/stationary/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsImeAppearHorizontal by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/ime/appear/horizontal/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/ime/appear/horizontal/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    @Test
    fun generatesImeTagsOnGesture() {
        val tagTrace = tagsImeAppearWithGesture
        Truth.assertWithMessage("Should have 2 Ime appear tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 11120377206699 // 0d3h5m20s377ms
        val endTagTimestamp = 11120645554330 // 0d3h5m20s645ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesTagsOnAppLaunchWithNoGesture() {
        val tagTrace = tagsImeAppearWithoutGestureOnAppLaunch
        Truth.assertWithMessage("Should have 2 Ime appear tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 437392442580 // 0d0h7m17s392ms
        val endTagTimestamp = 437396516487 // 0d0h7m17s396ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesTagsOnHorizontalAppLaunchWithGesture() {
        val tagTrace = tagsImeAppearHorizontal
        Truth.assertWithMessage("Should have 2 Ime appear tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 1015548115255262 // 11d18h5m48s115ms
        val endTagTimestamp = 1015548233177878 // 11d18h5m48s223ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun doNotGenerateTagsOnStationaryIme() {
        val tagTrace = doesntTagStationaryIme
        Truth.assertWithMessage("Should have no Ime appear tags")
            .that(tagTrace)
            .hasSize(0)
    }
}
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
import com.android.server.wm.traces.common.service.processors.ImeDisappearProcessor
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [ImeDisappearProcessor] tests. To run this test:
 * `atest FlickerLibTest:ImeDisappearProcessorTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ImeDisappearProcessorTest {
    private val processor = ImeDisappearProcessor { }

    private val tagsImeDisappearWithGestureOpenAndClose by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/ime/disappear/bygesture/openandclose/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/ime/disappear/bygesture/openandclose/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsImeDisappearOnAppOpenAndClose by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/ime/disappear/closeapp/openandclose/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/ime/disappear/closeapp/openandclose/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsImeDisappearWithGestureClose by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/ime/disappear/bygesture/close/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/ime/disappear/bygesture/close/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsImeDisappearOnAppClose by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/ime/disappear/closeapp/close/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/ime/disappear/closeapp/close/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    @Test
    fun generatesImeDisappearTagsWithGestureOpenAndCloseIme() {
        val tagTrace = tagsImeDisappearWithGestureOpenAndClose
        Truth.assertWithMessage("Should have 2 IME disappear tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 69234720627579 // 19h13m54s720ms
        val endTagTimestamp = 69234929459162 // 19h13m54s929ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesImeDisappearTagsOnAppOpenAndClose() {
        val tagTrace = tagsImeDisappearOnAppOpenAndClose
        Truth.assertWithMessage("Should have 2 IME disappear tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 69524600678331 // 19h18m44s600ms
        val endTagTimestamp = 69524958584304 // 19h18m44s958ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesImeDisappearTagsWithGestureCloseIme() {
        val tagTrace = tagsImeDisappearWithGestureClose
        Truth.assertWithMessage("Should have 2 IME disappear tags")
            .that(tagTrace)
            .hasSize(2)

        val startTagTimestamp = 69387450340971 // 19h16m27s450ms
        val endTagTimestamp = 69387644316302 // 19h16m27s644ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesImeDisappearTagsOnAppClose() {
        val tagTrace = tagsImeDisappearOnAppClose
        Truth.assertWithMessage("Should have 2 IME disappear tags")
            .that(tagTrace)
            .hasSize(2)

        val startTagTimestamp = 69635457764375 // 19h20m35s457ms
        val endTagTimestamp = 69635765486645 // 19h20m35s765ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }
}
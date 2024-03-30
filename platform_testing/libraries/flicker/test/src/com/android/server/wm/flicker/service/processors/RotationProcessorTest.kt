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
import com.android.server.wm.traces.common.service.processors.RotationProcessor
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [RotationProcessor] tests. To run this test:
 * `atest FlickerLibTest:RotationProcessorTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RotationProcessorTest {
    companion object {
        const val REGULAR_ROTATION1_START = 280186737540384
        const val REGULAR_ROTATION1_END = 280187243649340
        const val REGULAR_ROTATION2_START = 280188522078113
        const val REGULAR_ROTATION2_END = 280189020672174
        const val SEAMLESS_ROTATION_START = 981157456801L
        const val SEAMLESS_ROTATION_END = 981560560070L
        const val DISPLAYS_ROTATION1_START = 67585958089516
        const val DISPLAYS_ROTATION1_END = 67586545923169
        const val DISPLAYS_ROTATION2_START = 67587517164151
        const val DISPLAYS_ROTATION2_END = 67588122219420
    }

    private val rotationProcessor = RotationProcessor { }
    private val tagsRegularRotationTagFinalState by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/rotation/regular/WindowManagerTrace.winscope")
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/rotation/regular/SurfaceFlingerTrace.winscope")
        rotationProcessor.generateTags(wmTrace, layersTrace)
    }

    private val tagsSeamlessRotation by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/rotation/seamless/WindowManagerTrace.winscope")
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/rotation/seamless/SurfaceFlingerTrace.winscope")
        rotationProcessor.generateTags(wmTrace, layersTrace)
    }

    private val tagsDisplaysRotation by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/rotation/displays/WindowManagerTrace.winscope")
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/rotation/displays/SurfaceFlingerTrace.winscope")
        rotationProcessor.generateTags(wmTrace, layersTrace)
    }

    @Test
    fun canDetectMultipleRegularRotations() {
        Truth.assertWithMessage("Number of tags")
            .that(tagsRegularRotationTagFinalState)
            .hasSize(4)
        val tags = tagsRegularRotationTagFinalState.flatMap { it.tags.toList() }
        Truth.assertWithMessage("Number of start tags")
            .that(tags.filter { it.isStartTag })
            .hasSize(2)
        Truth.assertWithMessage("Number of end tags")
            .that(tags.filterNot { it.isStartTag })
            .hasSize(2)
    }

    @Test
    fun canDetectRegularRotation1Start() {
        val tag = tagsRegularRotationTagFinalState
            .firstOrNull { it.tags.any { tag -> tag.isStartTag } }
            ?.timestamp ?: 0L
        Truth.assertWithMessage("Start tag timestamp")
            .that(tag)
            .isEqualTo(REGULAR_ROTATION1_START)
    }

    @Test
    fun canDetectRegularRotation1End() {
        val tag = tagsRegularRotationTagFinalState
            .firstOrNull { it.tags.any { tag -> !tag.isStartTag } }
            ?.timestamp ?: 0L
        Truth.assertWithMessage("End tag timestamp")
            .that(tag)
            .isEqualTo(REGULAR_ROTATION1_END)
    }

    @Test
    fun canDetectRegularRotation2Start() {
        val tag = tagsRegularRotationTagFinalState
            .lastOrNull { it.tags.any { tag -> tag.isStartTag } }
            ?.timestamp ?: 0L
        Truth.assertWithMessage("Start tag timestamp")
            .that(tag)
            .isEqualTo(REGULAR_ROTATION2_START)
    }

    @Test
    fun canDetectRegularRotation2End() {
        val tag = tagsRegularRotationTagFinalState
            .lastOrNull { it.tags.any { tag -> !tag.isStartTag } }
            ?.timestamp ?: 0L
        Truth.assertWithMessage("End tag timestamp")
            .that(tag)
            .isEqualTo(REGULAR_ROTATION2_END)
    }

    @Test
    fun canDetectSeamlessRotation() {
        Truth.assertWithMessage("Number of tags")
            .that(tagsSeamlessRotation)
            .hasSize(2)
        val tags = tagsSeamlessRotation.flatMap { it.tags.toList() }
        Truth.assertWithMessage("Number of start tags")
            .that(tags.filter { it.isStartTag })
            .hasSize(1)
        Truth.assertWithMessage("Number of end  tags")
            .that(tags.filterNot { it.isStartTag })
            .hasSize(1)
    }

    @Test
    fun canDetectSeamlessRotationStart() {
        val tag = tagsSeamlessRotation
            .lastOrNull { it.tags.any { tag -> tag.isStartTag } }
            ?.timestamp ?: 0L
        Truth.assertWithMessage("Start tag timestamp")
            .that(tag)
            .isEqualTo(SEAMLESS_ROTATION_START)
    }

    @Test
    fun canDetectSeamlessRotationEnd() {
        val tag = tagsSeamlessRotation
            .lastOrNull { it.tags.any { tag -> !tag.isStartTag } }
            ?.timestamp ?: 0L
        Truth.assertWithMessage("End tag timestamp")
            .that(tag)
            .isEqualTo(SEAMLESS_ROTATION_END)
    }

    @Test
    fun canDetectDisplaysRotation() {
        Truth.assertWithMessage("Number of tags")
            .that(tagsDisplaysRotation)
            .hasSize(4)
        val tags = tagsDisplaysRotation.flatMap { it.tags.toList() }
        Truth.assertWithMessage("Number of start tags")
            .that(tags.filter { it.isStartTag })
            .hasSize(2)
        Truth.assertWithMessage("Number of end  tags")
            .that(tags.filterNot { it.isStartTag })
            .hasSize(2)
    }

    @Test
    fun canDetectDisplaysRotation1Start() {
        val tag = tagsDisplaysRotation
            .firstOrNull { it.tags.any { tag -> tag.isStartTag } }
            ?.timestamp ?: 0L
        Truth.assertWithMessage("Start tag timestamp")
            .that(tag)
            .isEqualTo(DISPLAYS_ROTATION1_START)
    }

    @Test
    fun canDetectDisplaysRotation1End() {
        val tag = tagsDisplaysRotation
            .firstOrNull { it.tags.any { tag -> !tag.isStartTag } }
            ?.timestamp ?: 0L
        Truth.assertWithMessage("End tag timestamp")
            .that(tag)
            .isEqualTo(DISPLAYS_ROTATION1_END)
    }

    @Test
    fun canDetectDisplaysRotation2Start() {
        val tag = tagsDisplaysRotation
            .lastOrNull { it.tags.any { tag -> tag.isStartTag } }
            ?.timestamp ?: 0L
        Truth.assertWithMessage("Start tag timestamp")
            .that(tag)
            .isEqualTo(DISPLAYS_ROTATION2_START)
    }

    @Test
    fun canDetectDisplaysRotation2End() {
        val tag = tagsDisplaysRotation
            .lastOrNull { it.tags.any { tag -> !tag.isStartTag } }
            ?.timestamp ?: 0L
        Truth.assertWithMessage("End tag timestamp")
            .that(tag)
            .isEqualTo(DISPLAYS_ROTATION2_END)
    }
}
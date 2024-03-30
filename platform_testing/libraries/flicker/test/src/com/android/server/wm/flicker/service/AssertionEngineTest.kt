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

package com.android.server.wm.flicker.service

import com.android.server.wm.flicker.readLayerTraceFromFile
import com.android.server.wm.flicker.readTagTraceFromFile
import com.android.server.wm.flicker.readWmTraceFromFile
import com.android.server.wm.traces.common.tags.Transition
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [AssertionEngine] tests. To run this test:
 * `atest FlickerLibTest:AssertionEngineTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssertionEngineTest {
    private val assertionEngine = AssertionEngine(emptyList()) { }
    private val wmTrace by lazy {
        readWmTraceFromFile("assertors/AppLaunchAndRotationsWindowManagerTrace.winscope")
    }
    private val layersTrace by lazy {
        readLayerTraceFromFile("assertors/AppLaunchAndRotationsSurfaceFlingerTrace.winscope")
    }
    private val tagTrace by lazy {
        readTagTraceFromFile("assertors/AppLaunchAndRotationsTagTrace.winscope")
    }
    private val tagTraceEndTag by lazy {
        readTagTraceFromFile("assertors/PipExitTagTrace.winscope")
    }
    private val transitionTags by lazy { assertionEngine.getTransitionTags(tagTrace) }

    @Test
    fun canExtractTransitionTags() {
        Truth.assertThat(transitionTags).isNotEmpty()
        Truth.assertThat(transitionTags.size).isEqualTo(3)
    }

    @Test
    fun canExtractTransitionTags_noEndInfo() {
        Truth.assertThat(tagTraceEndTag.entries.size).isEqualTo(2)

        val transitionTags = assertionEngine.getTransitionTags(tagTraceEndTag)
        Truth.assertThat(transitionTags).isNotEmpty()
        Truth.assertThat(transitionTags.size).isEqualTo(1)
    }

    @Test
    fun canSplitTraces_singleTag() {
        val blocks = transitionTags
            .filter { it.tag.transition == Transition.APP_LAUNCH }
            .map { assertionEngine.splitTraces(it, wmTrace, layersTrace) }

        Truth.assertThat(blocks).isNotEmpty()
        Truth.assertThat(blocks.size).isEqualTo(1)

        val entries = blocks.first().first.entries
        Truth.assertThat(entries.first().timestamp).isEqualTo(294063112453765)
        Truth.assertThat(entries.last().timestamp).isEqualTo(294063379330458)
    }

    @Test
    fun canSplitLayersTrace_mergedTags() {
        val blocks = transitionTags
            .filter { it.tag.transition == Transition.ROTATION }
            .map { assertionEngine.splitTraces(it, wmTrace, layersTrace) }

        Truth.assertThat(blocks).isNotEmpty()
        Truth.assertThat(blocks.size).isEqualTo(2)

        val entries = blocks.last().second.entries
        Truth.assertThat(entries.first().timestamp).isEqualTo(294064497020048)
        Truth.assertThat(entries.last().timestamp).isEqualTo(294064981192909)
    }

    @Test
    fun canSplitLayersTrace_noTags() {
        val blocks = transitionTags
            .filter { it.tag.transition == Transition.APP_CLOSE }
            .map { assertionEngine.splitTraces(it, wmTrace, layersTrace) }

        Truth.assertThat(blocks).isEmpty()
    }
}
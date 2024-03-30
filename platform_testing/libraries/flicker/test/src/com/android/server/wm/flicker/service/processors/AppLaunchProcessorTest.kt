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
import com.android.server.wm.traces.common.service.processors.AppLaunchProcessor
import com.google.common.truth.Truth
import org.junit.Test

/**
 * Contains [AppLaunchProcessor] tests. To run this test:
 * `atest FlickerLibTest:AppLaunchProcessorTest`
 */
class AppLaunchProcessorTest {
    private val processor = AppLaunchProcessor { }

    /**
     * Scenarios expecting tags
     */
    private val tagsColdAppLaunch by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/applaunch/cold/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/applaunch/cold/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsWarmAppLaunch by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/applaunch/warm/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/applaunch/warm/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsAppLaunchByIntent by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/applaunch/intent/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/applaunch/intent/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsAppLaunchWithRotation by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/applaunch/withrot/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/applaunch/withrot/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    /**
     * Scenarios expecting no tags
     */
    private val tagsComposeNewMessage by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/ime/appear/bygesture/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/ime/appear/bygesture/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsRotation by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/rotation/verticaltohorizontal/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/rotation/verticaltohorizontal/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    @Test
    fun tagsColdAppLaunch() {
        val tagTrace = tagsColdAppLaunch
        Truth.assertWithMessage("Should have 2 app launch tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 677617096496959 // Represents 7d20h13m37s96ms
        val endTagTimestamp = 677617685370716 // Represents 7d20h13m37s685ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun tagsWarmAppLaunch() {
        val tagTrace = tagsWarmAppLaunch
        Truth.assertWithMessage("Should have 2 app launch tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 677630638463152 // Represents 7d20h13m50s638ms
        val endTagTimestamp = 677631170881851 // Represents 7d20h13m51s170ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun tagsAppLaunchByIntent() {
        val tagTrace = tagsAppLaunchByIntent
        Truth.assertWithMessage("Should have 2 app launch tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 678152269074856 // Represents 7d20h22m32s269ms
        val endTagTimestamp = 678152921944244 // Represents 7d20h22m32s921ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun tagsAppLaunchWithRotation() {
        val tagTrace = tagsAppLaunchWithRotation
        Truth.assertWithMessage("Should have 2 app launch tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 765902849663314 // Represents 8d20h45m2s849ms
        val endTagTimestamp = 765903475287491 // Represents 8d20h45m4s139ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun doesNotTagComposeNewMessage() {
        val tagTrace = tagsComposeNewMessage
        Truth.assertWithMessage("Should have 0 app launch tags")
            .that(tagTrace)
            .isEmpty()
    }

    @Test
    fun doesNotTagRotation() {
        val tagTrace = tagsRotation
        Truth.assertWithMessage("Should have 0 app launch tags")
            .that(tagTrace)
            .isEmpty()
    }
}
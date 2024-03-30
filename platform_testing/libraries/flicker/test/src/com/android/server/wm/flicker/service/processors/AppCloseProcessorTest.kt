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
import com.android.server.wm.traces.common.service.processors.AppCloseProcessor
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [AppCloseProcessor] tests. To run this test:
 * `atest FlickerLibTest:AppCloseProcessorTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppCloseProcessorTest {
    private val processor = AppCloseProcessor { }

    private val tagAppCloseByBackButton by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/appclose/backbutton/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/appclose/backbutton/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagAppCloseBySwipeUp by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/appclose/swipeup/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/appclose/swipeup/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagAppCloseBySwitchingApps by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/appclose/switchapp/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/appclose/switchapp/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

    private val tagsAppCloseByClosingRotatedApp by lazy {
        val wmTrace = readWmTraceFromFile(
            "tagprocessors/appclose/rotated/WindowManagerTrace.winscope"
        )
        val layersTrace = readLayerTraceFromFile(
            "tagprocessors/appclose/rotated/SurfaceFlingerTrace.winscope"
        )
        processor.generateTags(wmTrace, layersTrace)
    }

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

    @Test
    fun generatesAppCloseTagsByPressingBackButton() {
        val tagTrace = tagAppCloseByBackButton
        Truth.assertWithMessage("Should have 2 app close tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 9295108146952 // 0d2h34m55s108ms
        val endTagTimestamp = 9295480256103 // 0d2h34m55s480ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesAppCloseTagsBySwipe() {
        val tagTrace = tagAppCloseBySwipeUp
        Truth.assertWithMessage("Should have 2 app close tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 9320574596259 // 0d2h35m20s578ms
        val endTagTimestamp = 9321301178051 // 0d2h35m21s301ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesAppCloseTagsBySwitchingApps() {
        val tagTrace = tagAppCloseBySwitchingApps
        Truth.assertWithMessage("Should have 2 app close tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 4129701437903 // 0d1h8m49s701ms
        val endTagTimestamp = 4132063745690 // 0d1h8m52s52ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesAppCloseTagsWhenAppRotated90() {
        val tagTrace = tagsAppCloseByClosingRotatedApp
        Truth.assertWithMessage("Should have 2 app close tags")
            .that(tagTrace)
            .hasSize(2)
        val startTagTimestamp = 343127388040903 // 3d23h18m47s388ms
        val endTagTimestamp = 343128129419414 // 3d23h18m48s129ms
        Truth.assertThat(tagTrace.first().timestamp).isEqualTo(startTagTimestamp)
        Truth.assertThat(tagTrace.last().timestamp).isEqualTo(endTagTimestamp)
    }

    @Test
    fun generatesNoTagsOnColdAppLaunch() {
        val tagTrace = tagsColdAppLaunch
        Truth.assertWithMessage("Should have 0 app launch tags")
            .that(tagTrace)
            .hasSize(0)
    }

    @Test
    fun generatesNoTagsOnWarmAppLaunch() {
        val tagTrace = tagsWarmAppLaunch
        Truth.assertWithMessage("Should have 0 app launch tags")
            .that(tagTrace)
            .hasSize(0)
    }
}
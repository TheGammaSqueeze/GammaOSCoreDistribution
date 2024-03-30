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

package com.android.server.wm.flicker.layers

import com.android.server.wm.flicker.assertThatErrorContainsDebugInfo
import com.android.server.wm.flicker.assertThrows
import com.android.server.wm.flicker.readLayerTraceFromFile
import com.android.server.wm.flicker.traces.layers.LayerSubject
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject.Companion.assertThat
import com.android.server.wm.traces.common.Size
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [LayerSubject] tests. To run this test:
 * `atest FlickerLibTest:LayerSubjectTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LayerSubjectTest {
    @Test
    fun exceptionContainsDebugInfoImaginary() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_emptyregion.pb")
        val error = assertThrows(AssertionError::class.java) {
            assertThat(layersTraceEntries)
                .first()
                .layer("ImaginaryLayer", 0)
                .exists()
        }
        assertThatErrorContainsDebugInfo(error)
        Truth.assertThat(error).hasMessageThat().contains("ImaginaryLayer")
        Truth.assertThat(error).hasMessageThat().contains("Layer name")
    }

    @Test
    fun exceptionContainsDebugInfoConcrete() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_emptyregion.pb")
        val error = assertThrows(AssertionError::class.java) {
            assertThat(layersTraceEntries)
                    .first()
                    .subjects
                    .first()
                    .doesNotExist()
        }
        assertThatErrorContainsDebugInfo(error)
    }

    @Test
    fun canTestAssertionsOnLayer() {
        val layersTraceEntries = readLayerTraceFromFile("layers_trace_emptyregion.pb")
        assertThat(layersTraceEntries)
            .layer("SoundVizWallpaperV2", 26033)
            .hasBufferSize(Size(1440, 2960))
            .hasScalingMode(0)

        assertThat(layersTraceEntries)
            .layer("DoesntExist", 1)
            .doesNotExist()
    }
}

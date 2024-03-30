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

package com.android.server.wm.traces.common.service

import com.android.server.wm.traces.common.layers.LayersTrace
import com.android.server.wm.traces.common.service.processors.AppCloseProcessor
import com.android.server.wm.traces.common.service.processors.AppLaunchProcessor
import com.android.server.wm.traces.common.service.processors.ImeAppearProcessor
import com.android.server.wm.traces.common.service.processors.ImeDisappearProcessor
import com.android.server.wm.traces.common.service.processors.PipEnterProcessor
import com.android.server.wm.traces.common.service.processors.PipExitProcessor
import com.android.server.wm.traces.common.service.processors.PipExpandProcessor
import com.android.server.wm.traces.common.service.processors.PipResizeProcessor
import com.android.server.wm.traces.common.service.processors.RotationProcessor
import com.android.server.wm.traces.common.tags.TagState
import com.android.server.wm.traces.common.tags.TagTrace
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace

/**
 * Invokes all concrete tag producers and writes to a .winscope file
 *
 * The timestamp constructor must be a string due to lack of Kotlin/KotlinJS Long compatibility
 *
 * The traces are passed as constructor arguments due to name mangling in KotlinJS
 *
 * @param logger Platform dependent function for logging
 * @param wmTrace WindowManager trace
 * @param layersTrace SurfaceFlinger trace
 */
class TaggingEngine(
    private val wmTrace: WindowManagerTrace,
    private val layersTrace: LayersTrace,
    private val logger: (String) -> Unit
) {
    private val transitions = listOf(
        // TODO: Keep adding new transition processors to invoke
        RotationProcessor(logger),
        AppLaunchProcessor(logger),
        AppCloseProcessor(logger),
        ImeAppearProcessor(logger),
        ImeDisappearProcessor(logger),
        PipEnterProcessor(logger),
        PipResizeProcessor(logger),
        PipExpandProcessor(logger),
        PipExitProcessor(logger)
    )

    /**
     * Generate tags denoting start and end points for all [transitions] within traces
     */
    fun run(): TagTrace {
        val allStates = transitions.flatMap {
            logger.invoke("Generating tags for ${it::class.simpleName}")
            it.generateTags(wmTrace, layersTrace).entries.asList()
        }

        /**
         * Ensure all tag states with the same timestamp are merged
         */
        val tagStates = allStates.distinct()
            .groupBy({ it.timestamp }, { it.tags.asList() })
            .mapValues { (key, value) -> TagState(key.toString(), value.flatten().toTypedArray()) }
            .values.toTypedArray()

        return TagTrace(tagStates)
    }
}

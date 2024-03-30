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

import android.util.Log
import com.android.server.wm.flicker.FLICKER_TAG
import com.android.server.wm.flicker.monitor.TraceMonitor.Companion.WINSCOPE_EXT
import com.android.server.wm.flicker.service.assertors.AssertionData
import com.android.server.wm.traces.common.errors.ErrorTrace
import com.android.server.wm.traces.common.layers.LayersTrace
import com.android.server.wm.traces.common.service.TaggingEngine
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace
import com.android.server.wm.traces.parser.errors.writeToFile
import com.android.server.wm.traces.parser.tags.writeToFile
import java.nio.file.Path

/**
 * Contains the logic for Flicker as a Service.
 */
class FlickerService @JvmOverloads constructor(
    private val assertions: List<AssertionData> = AssertionData.readConfiguration()
) {
    /**
     * The entry point for WM Flicker Service.
     *
     * Calls the Tagging Engine and the Assertion Engine.
     *
     * @param wmTrace Window Manager trace
     * @param layersTrace Surface Flinger trace
     * @return A pair with an [ErrorTrace] and a map that associates assertion names with
     * 0 if it fails and 1 if it passes
     */
    fun process(
        wmTrace: WindowManagerTrace,
        layersTrace: LayersTrace,
        outputDir: Path
    ): Pair<ErrorTrace, Map<String, Int>> {
        val taggingEngine = TaggingEngine(wmTrace, layersTrace) { Log.v("$FLICKER_TAG-PROC", it) }
        val tagTrace = taggingEngine.run()
        val tagTraceFile = getFassFilePath(outputDir, "tag_trace")
        tagTrace.writeToFile(tagTraceFile)

        val assertionEngine = AssertionEngine(assertions) { Log.v("$FLICKER_TAG-ASSERT", it) }
        val (errorTrace, assertions) = assertionEngine.analyze(wmTrace, layersTrace, tagTrace)
        val errorTraceFile = getFassFilePath(outputDir, "error_trace")
        errorTrace.writeToFile(errorTraceFile)
        return errorTrace to assertions
    }

    companion object {
        /**
         * Returns the computed path for the Fass files.
         *
         * @param outputDir the output directory for the trace file
         * @param file the name of the trace file
         * @return the path to the trace file
         */
        internal fun getFassFilePath(outputDir: Path, file: String): Path =
                outputDir.resolve("$file$WINSCOPE_EXT")
    }
}

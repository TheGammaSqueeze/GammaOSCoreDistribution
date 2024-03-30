/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wm.flicker.monitor

import androidx.annotation.VisibleForTesting
import com.android.server.wm.flicker.FlickerRunResult
import com.android.server.wm.flicker.Utils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Base class for monitors containing common logic to read the trace as a byte array and save the
 * trace to another location.
 */
abstract class TraceMonitor internal constructor(
    outputDir: Path,
    val sourceFile: Path
) : ITransitionMonitor, FlickerRunResult.IResultSetter, IFileGeneratingMonitor {
    @VisibleForTesting
    override val outputFile: Path = outputDir.resolve(sourceFile.fileName)
    abstract val isEnabled: Boolean

    final override fun start() {
        startTracing()
    }

    final override fun stop() {
        stopTracing()
        moveTraceFileToOutputDir()
    }

    abstract fun startTracing()
    abstract fun stopTracing()

    internal fun moveTraceFileToOutputDir(): Path {
        Files.createDirectories(outputFile.parent)
        if (sourceFile != outputFile) {
            Utils.moveFile(sourceFile, outputFile)
        }
        require(Files.exists(outputFile)) { "Unable to save trace file $outputFile" }
        return outputFile
    }

    companion object {
        @JvmStatic
        protected val TRACE_DIR = Paths.get("/data/misc/wmtrace/")
        internal const val WINSCOPE_EXT = ".winscope"
    }
}

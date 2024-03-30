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

package com.android.server.wm.flicker.rules

import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.wm.flicker.getDefaultFlickerOutputDir
import com.android.server.wm.flicker.monitor.LayersTraceMonitor
import com.android.server.wm.flicker.monitor.ScreenRecorder
import com.android.server.wm.flicker.monitor.TraceMonitor
import com.android.server.wm.flicker.monitor.WindowManagerTraceMonitor
import com.android.server.wm.flicker.service.FlickerService
import com.android.server.wm.flicker.service.FlickerService.Companion.getFassFilePath
import com.android.server.wm.traces.common.layers.LayersTrace
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace
import com.android.server.wm.traces.parser.layers.LayersTraceParser
import com.android.server.wm.traces.parser.windowmanager.WindowManagerTraceParser
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.nio.file.Files
import java.nio.file.Path

/**
 * Collect the WM and SF traces, parse them and call the WM Flicker Service after the test
 */
open class WMFlickerServiceRule @JvmOverloads constructor(
    private val outputDir: Path = getDefaultFlickerOutputDir()
) : TestWatcher() {
    private val traceMonitors = mutableListOf<TraceMonitor>()

    protected var wmTrace: WindowManagerTrace = WindowManagerTrace(emptyArray())
    protected var layersTrace: LayersTrace = LayersTrace(emptyArray())

    override fun starting(description: Description?) {
        setupMonitors()
        cleanupTraceFiles()
        traceMonitors.forEach {
            it.start()
        }
    }

    override fun finished(description: Description?) {
        traceMonitors.forEach {
            it.stop()
        }

        Files.createDirectories(outputDir)
        wmTrace = getWindowManagerTrace(getFassFilePath(outputDir, "wm_trace"))
        layersTrace = getLayersTrace(getFassFilePath(outputDir, "layers_trace"))

        val flickerService = FlickerService()
        flickerService.process(wmTrace, layersTrace, outputDir)
    }

    private fun setupMonitors() {
        traceMonitors.add(WindowManagerTraceMonitor(outputDir))
        traceMonitors.add(LayersTraceMonitor(outputDir))
        traceMonitors.add(ScreenRecorder(
            InstrumentationRegistry.getInstrumentation().targetContext, outputDir)
        )
    }

    /**
     * Remove the WM trace and layers trace files collected from previous test runs if the
     * directory exists.
     */
    private fun cleanupTraceFiles() {
        if (Files.exists(outputDir)) {
            Files.list(outputDir).forEach { file ->
                if (!Files.isDirectory(file)) {
                    Files.delete(file)
                }
            }
        }
    }

    /**
     * Parse the window manager trace file.
     *
     * @param traceFilePath
     * @return parsed window manager trace.
     */
    private fun getWindowManagerTrace(traceFilePath: Path): WindowManagerTrace {
        val wmTraceByteArray: ByteArray = Files.readAllBytes(traceFilePath)
        return WindowManagerTraceParser.parseFromTrace(wmTraceByteArray)
    }

    /**
     * Parse the layers trace file.
     *
     * @param traceFilePath
     * @return parsed layers trace.
     */
    private fun getLayersTrace(traceFilePath: Path): LayersTrace {
        val layersTraceByteArray: ByteArray = Files.readAllBytes(traceFilePath)
        return LayersTraceParser.parseFromTrace(layersTraceByteArray)
    }
}

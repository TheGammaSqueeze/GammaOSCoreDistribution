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

import android.os.RemoteException
import android.util.Log
import android.view.WindowManagerGlobal
import com.android.server.wm.flicker.FLICKER_TAG
import com.android.server.wm.flicker.FlickerRunResult
import com.android.server.wm.flicker.getDefaultFlickerOutputDir
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace
import com.android.server.wm.traces.parser.windowmanager.WindowManagerTraceParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * Captures [WindowManagerTrace] from WindowManager.
 *
 * Use [WindowManagerTraceSubject.assertThat] to make assertions on the trace
 */
open class WindowManagerTraceMonitor @JvmOverloads constructor(
    outputDir: Path = getDefaultFlickerOutputDir(),
    sourceFile: Path = TRACE_DIR.resolve("wm_trace$WINSCOPE_EXT")
) : TransitionMonitor(outputDir, sourceFile) {
    private val windowManager = WindowManagerGlobal.getWindowManagerService()
    override fun startTracing() {
        try {
            windowManager.startWindowTrace()
        } catch (e: RemoteException) {
            throw RuntimeException("Could not start trace", e)
        }
    }

    override fun stopTracing() {
        try {
            windowManager.stopWindowTrace()
        } catch (e: RemoteException) {
            throw RuntimeException("Could not stop trace", e)
        }
    }

    override val isEnabled: Boolean
        get() = windowManager.isWindowTraceEnabled

    override fun setResult(flickerRunResultBuilder: FlickerRunResult.Builder) {
        flickerRunResultBuilder.setWmTrace(outputFile) {
            Log.v(FLICKER_TAG, "Parsing WM trace")
            val traceData = Files.readAllBytes(outputFile)
            val wmTrace = WindowManagerTraceParser.parseFromTrace(traceData)
            WindowManagerTraceSubject.assertThat(wmTrace)
        }
    }
}

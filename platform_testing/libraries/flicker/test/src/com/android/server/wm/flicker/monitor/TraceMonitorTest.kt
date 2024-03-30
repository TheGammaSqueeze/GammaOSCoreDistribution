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

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.getDefaultFlickerOutputDir
import com.android.server.wm.traces.common.DeviceTraceDump
import com.android.server.wm.traces.parser.DeviceDumpParser
import com.google.common.io.Files
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Path

abstract class TraceMonitorTest<T : TransitionMonitor> {

    lateinit var savedTrace: Path
    abstract fun getMonitor(outputDir: Path): T
    abstract fun assertTrace(traceData: ByteArray)

    protected val instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val device = UiDevice.getInstance(instrumentation)
    private val traceMonitor by lazy {
        val outputDir = getDefaultFlickerOutputDir()
        getMonitor(outputDir)
    }

    @Before
    fun before() {
        Truth.assertWithMessage("Trace already enabled before starting test")
                .that(traceMonitor.isEnabled).isFalse()
    }

    @After
    fun teardown() {
        device.pressHome()
        if (traceMonitor.isEnabled) {
            traceMonitor.stop()
        }
        if (::savedTrace.isInitialized) {
            savedTrace.toFile().delete()
        }
        Truth.assertWithMessage("Failed to disable trace at end of test")
                .that(traceMonitor.isEnabled).isFalse()
    }

    @Test
    @Throws(Exception::class)
    fun canStartTrace() {
        traceMonitor.start()
        Truth.assertThat(traceMonitor.isEnabled).isTrue()
    }

    @Test
    @Throws(Exception::class)
    fun canStopTrace() {
        traceMonitor.start()
        Truth.assertThat(traceMonitor.isEnabled).isTrue()
        traceMonitor.stop()
        Truth.assertThat(traceMonitor.isEnabled).isFalse()
    }

    @Test
    @Throws(Exception::class)
    fun captureTrace() {
        traceMonitor.start()
        traceMonitor.stop()
        val savedTrace = traceMonitor.outputFile
        val testFile = savedTrace.toFile()
        Truth.assertWithMessage("File $testFile exists").that(testFile.exists()).isTrue()
        val trace = Files.toByteArray(testFile)
        Truth.assertThat(trace.size).isGreaterThan(0)
        assertTrace(trace)
    }

    private fun validateTrace(dump: DeviceTraceDump) {
        Truth.assertWithMessage("Could not obtain SF trace")
            .that(dump.layersTrace?.entries ?: emptyArray())
            .asList()
            .isNotEmpty()
        Truth.assertWithMessage("Could not obtain WM trace")
            .that(dump.wmTrace?.entries ?: emptyArray())
            .asList()
            .isNotEmpty()
    }

    @Test
    fun withTracing() {
        val trace = withTracing {
            device.pressHome()
            device.pressRecentApps()
        }

        this.validateTrace(trace)
    }

    @Test
    fun recordTraces() {
        val trace = recordTraces {
            device.pressHome()
            device.pressRecentApps()
        }

        val dump = DeviceDumpParser.fromTrace(trace.first, trace.second)
        this.validateTrace(dump)
    }
}

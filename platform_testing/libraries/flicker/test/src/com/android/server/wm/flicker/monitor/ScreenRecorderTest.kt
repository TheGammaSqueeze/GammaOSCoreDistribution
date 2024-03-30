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

import android.app.Instrumentation
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.android.server.wm.flicker.getDefaultFlickerOutputDir
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.nio.file.Files

/**
 * Contains [ScreenRecorder] tests. To run this test: `atest
 * FlickerLibTest:ScreenRecorderTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenRecorderTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var mScreenRecorder: ScreenRecorder
    @Before
    fun setup() {
        val outputDir = getDefaultFlickerOutputDir()
        mScreenRecorder = ScreenRecorder(instrumentation.targetContext, outputDir)
    }

    @Before
    fun clearOutputDir() {
        SystemUtil.runShellCommand("rm -rf ${getDefaultFlickerOutputDir()}")
    }

    @After
    fun teardown() {
        mScreenRecorder.stop()
        Files.deleteIfExists(mScreenRecorder.outputFile)
    }

    @Test
    fun videoIsRecorded() {
        mScreenRecorder.start()
        SystemClock.sleep(100)
        mScreenRecorder.stop()
        val file = mScreenRecorder.outputFile
        Truth.assertWithMessage("Screen recording file not found")
            .that(Files.exists(file))
            .isTrue()
    }

    @Test
    fun videoCanBeSaved() {
        mScreenRecorder.start()
        SystemClock.sleep(3000)
        mScreenRecorder.stop()
        val trace = mScreenRecorder.outputFile
        Truth.assertWithMessage("Trace file $trace not found").that(Files.exists(trace)).isTrue()
    }
}

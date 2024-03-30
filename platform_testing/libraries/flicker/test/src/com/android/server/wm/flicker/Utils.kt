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

package com.android.server.wm.flicker

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.wm.flicker.FlickerRunResult.Companion.RunStatus.ASSERTION_SUCCESS
import com.android.server.wm.flicker.traces.FlickerSubjectException
import com.android.server.wm.traces.common.layers.LayersTrace
import com.android.server.wm.traces.common.tags.TagTrace
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace
import com.android.server.wm.traces.parser.layers.LayersTraceParser
import com.android.server.wm.traces.parser.tags.TagTraceParserUtil
import com.android.server.wm.traces.parser.windowmanager.WindowManagerTraceParser
import com.google.common.io.ByteStreams
import com.google.common.truth.ExpectFailure
import com.google.common.truth.Truth
import com.google.common.truth.TruthFailureSubject
import java.io.FileInputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream

internal fun readWmTraceFromFile(relativePath: String): WindowManagerTrace {
    return try {
        WindowManagerTraceParser.parseFromTrace(readTestFile(relativePath))
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

internal fun readWmTraceFromDumpFile(relativePath: String): WindowManagerTrace {
    return try {
        WindowManagerTraceParser.parseFromDump(readTestFile(relativePath))
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

internal fun readLayerTraceFromFile(
    relativePath: String,
    ignoreOrphanLayers: Boolean = true
): LayersTrace {
    return try {
        LayersTraceParser.parseFromTrace(
            readTestFile(relativePath),
            ignoreLayersStackMatchNoDisplay = false,
            ignoreLayersInVirtualDisplay = false
        ) { ignoreOrphanLayers }
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

internal fun readTagTraceFromFile(relativePath: String): TagTrace {
    return try {
        TagTraceParserUtil.parseFromTrace(readTestFile(relativePath))
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

@Throws(Exception::class)
internal fun readTestFile(relativePath: String): ByteArray {
    val context: Context = InstrumentationRegistry.getInstrumentation().context
    val inputStream = context.resources.assets.open("testdata/$relativePath")
    return ByteStreams.toByteArray(inputStream)
}

/**
 * Runs `r` and asserts that an exception of type `expectedThrowable` is thrown.
 * @param expectedThrowable the type of throwable that is expected to be thrown
 * @param r the [Runnable] which is run and expected to throw.
 * @throws AssertionError if `r` does not throw, or throws a runnable that is not an
 * instance of `expectedThrowable`.
 */
// TODO: remove once Android migrates to JUnit 4.13, which provides assertThrows
fun assertThrows(expectedThrowable: Class<out Throwable>, r: () -> Any): Throwable {
    try {
        r()
    } catch (t: Throwable) {
        when {
            expectedThrowable.isInstance(t) -> return t
            t is Exception ->
                throw AssertionError("Expected $expectedThrowable, but got ${t.javaClass}", t)
            // Re-throw Errors and other non-Exception throwables.
            else -> throw t
        }
    }
    error("Expected $expectedThrowable, but nothing was thrown")
}

fun assertFailure(failure: Throwable?): TruthFailureSubject {
    val target = when (failure) {
        is FlickerSubjectException -> failure.cause
        is AssertionError -> failure
        else -> error("Expected assertion error, received $failure")
    }
    require(target is AssertionError) { "Unknown failure $target" }
    return ExpectFailure.assertThat(target)
}

fun assertThatErrorContainsDebugInfo(error: Throwable, withBlameEntry: Boolean = true) {
    Truth.assertThat(error).hasMessageThat().contains("What?")
    Truth.assertThat(error).hasMessageThat().contains("Where?")
    Truth.assertThat(error).hasMessageThat().contains("Facts")
    Truth.assertThat(error).hasMessageThat().contains("Trace start")
    Truth.assertThat(error).hasMessageThat().contains("Trace end")

    if (withBlameEntry) {
        Truth.assertThat(error).hasMessageThat().contains("Entry")
    }
}

fun assertArchiveContainsAllTraces(
    runStatus: FlickerRunResult.Companion.RunStatus = ASSERTION_SUCCESS,
    testName: String = "",
    iteration: Int = 0
) {
    val archiveFileName = "${runStatus.prefix}_${testName}_$iteration.zip"
    val archivePath = getDefaultFlickerOutputDir().resolve(archiveFileName)
    Truth.assertWithMessage("Expected trace archive `$archivePath` to exist")
            .that(Files.exists(archivePath)).isTrue()

    val archiveStream = ZipInputStream(FileInputStream(archivePath.toFile()))

    val expectedFiles = listOf("wm_trace.winscope", "layers_trace.winscope", "transition.mp4")
    val actualFiles = generateSequence { archiveStream.nextEntry }.map { it.name }.toList()

    Truth.assertThat(actualFiles).hasSize(expectedFiles.size)
    Truth.assertWithMessage("Trace archive doesn't contain all expected traces")
            .that(actualFiles.containsAll(expectedFiles)).isTrue()
}

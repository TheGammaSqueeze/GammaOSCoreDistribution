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

import androidx.annotation.VisibleForTesting
import com.android.compatibility.common.util.ZipUtil
import com.android.server.wm.flicker.assertions.AssertionData
import com.android.server.wm.flicker.assertions.FlickerAssertionError
import com.android.server.wm.flicker.assertions.FlickerAssertionErrorBuilder
import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.flicker.dsl.AssertionTag
import com.android.server.wm.flicker.traces.FlickerTraceSubject
import com.android.server.wm.flicker.traces.eventlog.EventLogSubject
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace
import com.android.server.wm.flicker.traces.eventlog.FocusEvent
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject
import com.android.server.wm.traces.common.layers.BaseLayerTraceEntry
import com.android.server.wm.traces.common.layers.LayersTrace
import com.android.server.wm.traces.common.windowmanager.WindowManagerState
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Defines the result of a flicker run
 */
class FlickerRunResult private constructor(
    /**
     * The trace files associated with the result (incl. screen recording)
     */
    _traceFile: Path?,
    /**
     * Determines which assertions to run (e.g., start, end, all, or a custom tag)
     */
    @JvmField var assertionTag: String,
    /**
     * Truth subject that corresponds to a [WindowManagerTrace] or [WindowManagerState]
     */
    internal val wmSubject: FlickerSubject?,
    /**
     * Truth subject that corresponds to a [LayersTrace] or [BaseLayerTraceEntry]
     */
    internal val layersSubject: FlickerSubject?,
    /**
     * Truth subject that corresponds to a list of [FocusEvent]
     */
    @VisibleForTesting
    val eventLogSubject: EventLogSubject?
) {
    /**
     * The object responsible for managing the trace file associated with this result.
     *
     * By default the file manager is the RunResult itself but in the case the RunResult is
     * derived or extracted from another RunResult then that other RunResult should be the trace
     * file manager.
     */
    internal var mTraceFile: TraceFile? =
            if (_traceFile != null) TraceFile(_traceFile) else null

    internal val traceName = mTraceFile?.traceFile?.fileName ?: "UNNAMED_TRACE"

    var status: RunStatus = RunStatus.UNDEFINED
        internal set(value) {
            if (field != value) {
                require(value != RunStatus.UNDEFINED) {
                    "Can't set status to UNDEFINED after being defined"
                }
                require(!field.isFailure) {
                    "Status of run already set to a failed status $field " +
                            "and can't be changed to $value."
                }
                field = value
            }

            mTraceFile?.status = status
        }

    fun setRunFailed() {
        status = RunStatus.RUN_FAILED
    }

    val isSuccessfulRun: Boolean get() = !isFailedRun
    val isFailedRun: Boolean get() {
        require(status != RunStatus.UNDEFINED) {
            "RunStatus cannot be UNDEFINED for $traceName ($assertionTag)"
        }
        // Other types of failures can only happen if the run has succeeded
        return status == RunStatus.RUN_FAILED
    }

    fun getSubjects(): List<FlickerSubject> {
        val result = mutableListOf<FlickerSubject>()

        wmSubject?.run { result.add(this) }
        layersSubject?.run { result.add(this) }
        eventLogSubject?.run { result.add(this) }

        return result
    }

    fun checkAssertion(assertion: AssertionData): FlickerAssertionError? {
        require(status != RunStatus.UNDEFINED) { "A valid RunStatus has not been provided" }
        return try {
            assertion.checkAssertion(this)
            null
        } catch (error: Throwable) {
            status = RunStatus.ASSERTION_FAILED
            FlickerAssertionErrorBuilder()
                    .fromError(error)
                    .atTag(assertion.tag)
                    .withTrace(this.mTraceFile)
                    .build()
        }
    }

    /**
     * Parse a [trace] into a [SubjectType] asynchronously
     *
     * The parsed subject is available in [promise]
     */
    class AsyncSubjectParser<SubjectType : FlickerTraceSubject<*>>(
        val trace: Path,
        parser: ((Path) -> SubjectType?)?
    ) {
        val promise: Deferred<SubjectType?>? = parser?.run { SCOPE.async { parser(trace) } }
    }

    class Builder {
        private var wmTraceData: AsyncSubjectParser<WindowManagerTraceSubject>? = null
        private var layersTraceData: AsyncSubjectParser<LayersTraceSubject>? = null
        var screenRecording: Path? = null

        /**
         * List of focus events, if collected
         */
        var eventLog: List<FocusEvent>? = null

        /**
         * Parses a [WindowManagerTraceSubject]
         *
         * @param traceFile of the trace file to parse
         * @param parser lambda to parse the trace into a [WindowManagerTraceSubject]
         */
        fun setWmTrace(traceFile: Path, parser: (Path) -> WindowManagerTraceSubject?) {
            wmTraceData = AsyncSubjectParser(traceFile, parser)
        }

        /**
         * Parses a [LayersTraceSubject]
         *
         * @param traceFile of the trace file to parse
         * @param parser lambda to parse the trace into a [LayersTraceSubject]
         */
        fun setLayersTrace(traceFile: Path, parser: (Path) -> LayersTraceSubject?) {
            layersTraceData = AsyncSubjectParser(traceFile, parser)
        }

        private fun buildResult(
            assertionTag: String,
            wmSubject: FlickerSubject?,
            layersSubject: FlickerSubject?,
            status: RunStatus,
            traceFile: Path? = null,
            eventLogSubject: EventLogSubject? = null
        ): FlickerRunResult {
            val result = FlickerRunResult(
                traceFile,
                assertionTag,
                wmSubject,
                layersSubject,
                eventLogSubject
            )
            result.status = status
            return result
        }

        /**
         * Builds a new [FlickerRunResult] for a trace
         *
         * @param assertionTag Tag to associate with the result
         * @param wmTrace WindowManager trace
         * @param layersTrace Layers trace
         */
        fun buildStateResult(
            assertionTag: String,
            wmTrace: WindowManagerTrace?,
            layersTrace: LayersTrace?,
            wmTraceFile: Path?,
            layersTraceFile: Path?,
            testName: String,
            iteration: Int,
            status: RunStatus
        ): FlickerRunResult {
            val wmSubject = wmTrace?.let { WindowManagerTraceSubject.assertThat(it).first() }
            val layersSubject = layersTrace?.let { LayersTraceSubject.assertThat(it).first() }

            val traceFiles = mutableListOf<File>()
            wmTraceFile?.let { traceFiles.add(it.toFile()) }
            layersTraceFile?.let { traceFiles.add(it.toFile()) }
            val traceFile = compress(traceFiles, "${assertionTag}_${testName}_$iteration.zip")

            return buildResult(assertionTag, wmSubject, layersSubject, status,
                    traceFile = traceFile)
        }

        @VisibleForTesting
        fun buildEventLogResult(status: RunStatus): FlickerRunResult {
            val events = eventLog ?: emptyList()
            return buildResult(
                AssertionTag.ALL,
                wmSubject = null,
                layersSubject = null,
                eventLogSubject = EventLogSubject.assertThat(events),
                status = status
            )
        }

        @VisibleForTesting
        fun buildTraceResults(
            testName: String,
            iteration: Int,
            status: RunStatus
        ): List<FlickerRunResult> = runBlocking {
            val wmSubject = wmTraceData?.promise?.await()
            val layersSubject = layersTraceData?.promise?.await()

            val traceFile = compress(testName, iteration)
            val traceResult = buildResult(
                AssertionTag.ALL, wmSubject, layersSubject, traceFile = traceFile, status = status)

            val initialStateResult = buildResult(
                AssertionTag.START, wmSubject?.first(), layersSubject?.first(), status = status)
            initialStateResult.mTraceFile = traceResult.mTraceFile

            val finalStateResult = buildResult(
                AssertionTag.END, wmSubject?.last(), layersSubject?.last(), status = status)
            finalStateResult.mTraceFile = traceResult.mTraceFile

            listOf(initialStateResult, finalStateResult, traceResult)
        }

        private fun compress(testName: String, iteration: Int): Path? {
            val traceFiles = mutableListOf<File>()
            wmTraceData?.trace?.let { traceFiles.add(it.toFile()) }
            layersTraceData?.trace?.let { traceFiles.add(it.toFile()) }
            screenRecording?.let { traceFiles.add(it.toFile()) }

            return compress(traceFiles, "${testName}_$iteration.zip")
        }

        private fun compress(traceFiles: List<File>, archiveName: String): Path? {
            val files = traceFiles.filter { it.exists() }
            if (files.isEmpty()) {
                return null
            }

            val firstFile = files.first()
            val compressedFile = firstFile.resolveSibling(archiveName)
            ZipUtil.createZip(traceFiles, compressedFile)
            traceFiles.forEach {
                it.delete()
            }

            return compressedFile.toPath()
        }

        fun buildAll(testName: String, iteration: Int, status: RunStatus): List<FlickerRunResult> {
            val results = buildTraceResults(testName, iteration, status).toMutableList()
            if (eventLog != null) {
                results.add(buildEventLogResult(status = status))
            }

            return results
        }

        fun setResultFrom(resultSetter: IResultSetter) {
            resultSetter.setResult(this)
        }
    }

    interface IResultSetter {
        fun setResult(builder: Builder)
    }

    companion object {
        private val SCOPE = CoroutineScope(Dispatchers.IO + SupervisorJob())

        enum class RunStatus(val prefix: String = "", val isFailure: Boolean) {
            UNDEFINED("???", false),

            RUN_SUCCESS("UNCHECKED", false),
            ASSERTION_SUCCESS("PASS", false),

            RUN_FAILED("FAILED_RUN", true),
            PARSING_FAILURE("FAILED_PARSING", true),
            ASSERTION_FAILED("FAIL", true);

            companion object {
                fun merge(runStatuses: List<RunStatus>): RunStatus {
                    val precedence = listOf(ASSERTION_FAILED, RUN_FAILED, ASSERTION_SUCCESS)
                    for (status in precedence) {
                        if (runStatuses.any { it == status }) {
                            return status
                        }
                    }

                    return UNDEFINED
                }
            }
        }
    }
}

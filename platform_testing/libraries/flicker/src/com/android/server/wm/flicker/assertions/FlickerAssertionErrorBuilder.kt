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

package com.android.server.wm.flicker.assertions

import com.android.server.wm.flicker.TraceFile
import com.android.server.wm.flicker.dsl.AssertionTag
import com.android.server.wm.flicker.traces.FlickerSubjectException
import com.google.common.truth.Fact
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class FlickerAssertionErrorBuilder {
    private var error: Throwable? = null
    private var traceFile: TraceFile? = null
    private var tag = ""

    fun fromError(error: Throwable): FlickerAssertionErrorBuilder = apply {
        this.error = error
    }

    fun withTrace(traceFile: TraceFile?): FlickerAssertionErrorBuilder = apply {
        this.traceFile = traceFile
    }

    fun atTag(tag: String): FlickerAssertionErrorBuilder = apply {
        this.tag = when (tag) {
            AssertionTag.START -> "before transition (initial state)"
            AssertionTag.END -> "after transition (final state)"
            AssertionTag.ALL -> "during transition"
            else -> "at user-defined location ($tag)"
        }
    }

    fun build(): FlickerAssertionError {
        return FlickerAssertionError(errorMessage, rootCause, traceFile)
    }

    private val errorMessage get() = buildString {
        val error = error
        requireNotNull(error)
        if (error is FlickerSubjectException) {
            appendLine(error.errorType)
            appendLine()
            append(error.errorDescription)
            appendLine()
            append(error.subjectInformation)
            append("\t").appendLine(Fact.fact("Location", tag))
            appendLine()
        } else {
            appendLine(error.message)
        }
        append("Trace file:").append(traceFileMessage)
        appendLine()
        appendLine("Cause:")
        append(rootCauseStackTrace)
        appendLine()
        appendLine("Full stacktrace:")
        appendLine()
    }

    private val traceFileMessage get() = buildString {
        traceFile?.traceFile?.let {
            append("\t")
            append(it)
        }
    }

    private val rootCauseStackTrace: String get() {
        val rootCause = rootCause
        return if (rootCause != null) {
            val baos = ByteArrayOutputStream()
            PrintStream(baos, true)
                    .use { ps -> rootCause.printStackTrace(ps) }
            "\t$baos"
        } else {
            ""
        }
    }

    /**
     * In some paths the exceptions are encapsulated by the Truth subjects
     * To make sure the correct error is printed, located the first non-subject
     * related exception and use that as cause.
     */
    private val rootCause: Throwable? get() {
        var childCause: Throwable? = this.error?.cause
        if (childCause != null && childCause is FlickerSubjectException) {
            childCause = childCause.cause
        }
        return childCause
    }
}

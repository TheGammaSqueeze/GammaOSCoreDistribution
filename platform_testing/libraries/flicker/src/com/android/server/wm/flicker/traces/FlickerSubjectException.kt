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

package com.android.server.wm.flicker.traces

import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.traces.common.prettyTimestamp

/**
 * Exception thrown by [FlickerSubject]s
 */
class FlickerSubjectException(
    internal val subject: FlickerSubject,
    cause: Throwable
) : AssertionError(cause.message, if (cause is FlickerSubjectException) null else cause) {
    internal val timestamp = subject.timestamp
    private val prettyTimestamp =
            if (timestamp > 0) "${prettyTimestamp(timestamp)} (timestamp=$timestamp)" else ""

    internal val errorType: String =
            if (cause is AssertionError) "Flicker assertion error" else "Unknown error"

    internal val errorDescription = buildString {
        appendln("Where? $prettyTimestamp")
        val message = (cause.message ?: "").split(("\n"))
        append("What? ")
        if (message.size == 1) {
            // Single line error message
            appendln(message.first())
        } else {
            // Multi line error message
            appendln()
            message.forEach { appendln("\t$it") }
        }
    }

    internal val subjectInformation = buildString {
        appendln("Facts:")
        subject.completeFacts.forEach { append("\t").appendln(it) }
    }

    override val message: String
        get() = buildString {
            appendln(errorType)
            appendln()
            append(errorDescription)
            appendln()
            append(subjectInformation)
        }
}
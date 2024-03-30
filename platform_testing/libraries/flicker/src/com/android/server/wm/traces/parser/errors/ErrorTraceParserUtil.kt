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

package com.android.server.wm.traces.parser.errors

import android.util.Log
import com.android.server.wm.flicker.FlickerErrorTraceProto
import com.android.server.wm.traces.common.errors.Error
import com.android.server.wm.traces.common.errors.ErrorState
import com.android.server.wm.traces.common.errors.ErrorTrace
import com.android.server.wm.traces.parser.LOG_TAG
import kotlin.system.measureTimeMillis

/**
 * Class that holds the methods to parse error proto files into error classes.
 */
class ErrorTraceParserUtil {
    companion object {
        /**
         * Parses [FlickerErrorTraceProto] from [data] and uses the proto to generates a list
         * of trace entries, storing the flattened layers into its hierarchical structure.
         *
         * @param data binary proto data
         */
        @JvmOverloads
        @JvmStatic
        fun parseFromTrace(
            data: ByteArray
        ): ErrorTrace {
            var fileProto: FlickerErrorTraceProto? = null
            try {
                measureTimeMillis {
                    fileProto = FlickerErrorTraceProto.parseFrom(data)
                }.also {
                    Log.v(LOG_TAG, "Parsing proto (Flicker Errors Trace): ${it}ms")
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
            return fileProto?.let {
                parseFromTrace(it)
            } ?: error("Unable to read flicker errors trace file")
        }

        /**
         * Parses [FlickerErrorTraceProto] from [proto] and uses the proto to generates a list
         * of trace entries, storing the flattened layers into its hierarchical structure.
         *
         * @param proto Parsed proto data
         */
        @JvmOverloads
        @JvmStatic
        fun parseFromTrace(
            proto: FlickerErrorTraceProto
        ): ErrorTrace {
            val states = mutableListOf<ErrorState>()
            var traceParseTime = 0L
            for (stateProto in proto.statesList) {
                val errorParseTime = measureTimeMillis {
                    val errors = mutableListOf<Error>()
                    for (errorProto in stateProto.errorsList) {
                        errors.add(
                                Error(
                                    stacktrace = errorProto.stacktrace,
                                    message = errorProto.message,
                                    layerId = errorProto.layerId,
                                    windowToken = errorProto.windowToken,
                                    taskId = errorProto.taskId,
                                    assertionName = errorProto.assertionName
                                ))
                    }
                    states.add(
                            ErrorState(
                                _timestamp = stateProto.timestamp.toString(),
                                errors = errors.toTypedArray()))
                }
                traceParseTime += errorParseTime
            }
            return ErrorTrace(
                    entries = states.toTypedArray()
            )
        }
    }
}
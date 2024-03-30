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

package com.android.server.wm.traces.parser.tags

import android.util.Log
import com.android.server.wm.flicker.FlickerTagTraceProto
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.tags.TagState
import com.android.server.wm.traces.common.tags.TagTrace
import com.android.server.wm.traces.common.tags.Transition
import com.android.server.wm.traces.parser.LOG_TAG
import kotlin.system.measureTimeMillis

/**
 * Class that holds the methods to parse tag proto files into tag classes.
 */
class TagTraceParserUtil {
    companion object {
        /**
         * Parses [FlickerTagTraceProto] from [data] and uses the proto to generates a list
         * of trace entries, storing the flattened layers into its hierarchical structure.
         *
         * @param data binary proto data
         */
        @JvmOverloads
        @JvmStatic
        fun parseFromTrace(
            data: ByteArray
        ): TagTrace {
            var fileProto: FlickerTagTraceProto? = null
            try {
                measureTimeMillis {
                    fileProto = FlickerTagTraceProto.parseFrom(data)
                }.also {
                    Log.v(LOG_TAG, "Parsing proto (Flicker Tags Trace): ${it}ms")
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
            return fileProto?.let {
                parseFromTrace(it)
            } ?: error("Unable to read flicker tags trace file")
        }

        /**
         * Parses [FlickerTagTraceProto] from [proto] and uses the proto to generates a list
         * of trace entries, storing the flattened layers into its hierarchical structure.
         *
         * @param proto Parsed proto data
         */
        @JvmOverloads
        @JvmStatic
        fun parseFromTrace(
            proto: FlickerTagTraceProto
        ): TagTrace {
            val states = mutableListOf<TagState>()
            var traceParseTime = 0L
            for (stateProto in proto.statesList) {
                val tagParseTime = measureTimeMillis {
                    val tags = mutableListOf<Tag>()
                    for (tagProto in stateProto.tagsList) {
                        tags.add(
                            Tag(
                                layerId = tagProto.layerId,
                                windowToken = tagProto.windowToken,
                                taskId = tagProto.taskId,
                                transition = Transition.valueOf(tagProto.transition.name),
                                id = tagProto.id,
                                isStartTag = tagProto.isStartTag
                        ))
                    }
                    states.add(
                            TagState(
                                    _timestamp = stateProto.timestamp.toString(),
                                    tags = tags.toTypedArray()))
                }
                traceParseTime += tagParseTime
            }
            return TagTrace(
                    entries = states.toTypedArray()
            )
        }
    }
}
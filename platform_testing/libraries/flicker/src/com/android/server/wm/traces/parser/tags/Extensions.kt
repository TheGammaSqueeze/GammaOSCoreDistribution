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

@file:JvmName("Extensions")

package com.android.server.wm.traces.parser.tags

import android.util.Log
import com.android.server.wm.flicker.FlickerTagProto
import com.android.server.wm.flicker.FlickerTagStateProto
import com.android.server.wm.flicker.FlickerTagTraceProto
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.tags.TagState
import com.android.server.wm.traces.common.tags.TagTrace
import com.android.server.wm.traces.parser.LOG_TAG
import java.nio.file.Files
import java.nio.file.Path

fun TagTrace.writeToFile(outputFile: Path) {
    val proto = FlickerTagTraceProto
        .newBuilder()
        .addAllStates(this.entries.map { it.toProto() })
        .setMagicNumber(
            FlickerTagTraceProto.MagicNumber.MAGIC_NUMBER_H.number.toLong() shl 32 or
                FlickerTagTraceProto.MagicNumber.MAGIC_NUMBER_L.number.toLong()
        )
        .build()

    val tagTraceBytes = proto.toByteArray()

    try {
        Log.d(LOG_TAG, outputFile.toString())
        Files.createDirectories(outputFile.parent)
        Files.write(outputFile, tagTraceBytes)
    } catch (e: Exception) {
        throw RuntimeException("Unable to create error trace file: ${e.message}", e)
    }
}

fun TagState.toProto(): FlickerTagStateProto = FlickerTagStateProto
    .newBuilder()
    .addAllTags(this.tags.map { it.toProto() })
    .setTimestamp(this.timestamp)
    .build()

fun Tag.toProto(): FlickerTagProto = FlickerTagProto
    .newBuilder()
    .setIsStartTag(this.isStartTag)
    .setTransition(FlickerTagProto.Transition.valueOf(this.transition.name))
    .setId(this.id)
    .setTaskId(this.taskId)
    .setWindowToken(this.windowToken)
    .setLayerId(this.layerId)
    .build()

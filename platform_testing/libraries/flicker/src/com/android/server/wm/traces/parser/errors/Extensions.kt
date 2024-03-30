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

package com.android.server.wm.traces.parser.errors

import android.util.Log
import com.android.server.wm.flicker.FlickerErrorProto
import com.android.server.wm.flicker.FlickerErrorStateProto
import com.android.server.wm.flicker.FlickerErrorTraceProto
import com.android.server.wm.traces.common.errors.ErrorState
import com.android.server.wm.traces.common.errors.ErrorTrace
import com.android.server.wm.traces.common.errors.Error
import com.android.server.wm.traces.parser.LOG_TAG
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Stores the error trace in a .winscope file.
 */
fun ErrorTrace.writeToFile(outputFile: Path) {
    val proto = FlickerErrorTraceProto
        .newBuilder()
        .addAllStates(this.entries.map { it.toProto() })
        .setMagicNumber(
            FlickerErrorTraceProto.MagicNumber.MAGIC_NUMBER_H.number.toLong() shl 32 or
                FlickerErrorTraceProto.MagicNumber.MAGIC_NUMBER_L.number.toLong()
        )
        .build()
    val errorTraceBytes = proto.toByteArray()

    try {
        Log.d(LOG_TAG, outputFile.toString())
        Files.createDirectories(outputFile.parent)
        Files.write(outputFile, errorTraceBytes)
    } catch (e: IOException) {
        throw RuntimeException("Unable to create error trace file: ${e.message}", e)
    }
}

fun ErrorState.toProto(): FlickerErrorStateProto = FlickerErrorStateProto
    .newBuilder()
    .addAllErrors(this.errors.map { it.toProto() })
    .setTimestamp(this.timestamp)
    .build()

fun Error.toProto(): FlickerErrorProto = FlickerErrorProto
    .newBuilder()
    .setStacktrace(this.stacktrace)
    .setMessage(this.message)
    .setLayerId(this.layerId)
    .setWindowToken(this.windowToken)
    .setTaskId(this.taskId)
    .setAssertionName(this.assertionName)
    .build()
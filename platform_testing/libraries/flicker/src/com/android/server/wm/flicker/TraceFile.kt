/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.util.Log
import com.android.server.wm.flicker.FlickerRunResult.Companion.RunStatus
import java.io.IOException
import java.nio.file.Path

class TraceFile(_traceFile: Path) {

    var traceFile = _traceFile
        private set

    internal val traceName = traceFile.fileName ?: "UNNAMED_TRACE"

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
                syncFileWithStatus()
            }
        }

    private fun syncFileWithStatus() {
        // Since we don't expect this to run in a multi-threaded context this is fine
        val localTraceFile = traceFile
        try {
            val newFileName = "${status.prefix}_$traceName"
            val dst = localTraceFile.resolveSibling(newFileName)
            Utils.renameFile(localTraceFile, dst)
            traceFile = dst
        } catch (e: IOException) {
            Log.e(FLICKER_TAG, "Unable to update file status $this", e)
        }
    }
}

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

import com.android.compatibility.common.util.SystemUtil
import com.android.server.wm.flicker.FlickerRunResult.Companion.RunStatus
import java.nio.file.Path

object Utils {
    fun renameFile(src: Path, dst: Path) {
        SystemUtil.runShellCommand("mv $src $dst")
    }

    fun copyFile(src: Path, dst: Path) {
        SystemUtil.runShellCommand("cp $src $dst")
        SystemUtil.runShellCommand("chmod a+r $dst")
    }

    fun moveFile(src: Path, dst: Path) {
        // Move the  file to the output directory
        // Note: Due to b/141386109, certain devices do not allow moving the files between
        //       directories with different encryption policies, so manually copy and then
        //       remove the original file
        //       Moreover, the copied trace file may end up with different permissions, resulting
        //       in b/162072200, to prevent this, ensure the files are readable after copying
        copyFile(src, dst)
        SystemUtil.runShellCommand("rm $src")
    }

    fun addStatusToFileName(traceFile: Path, status: RunStatus) {
        val newFileName = "${status.prefix}_${traceFile.fileName}"
        val dst = traceFile.resolveSibling(newFileName)
        renameFile(traceFile, dst)
    }
}

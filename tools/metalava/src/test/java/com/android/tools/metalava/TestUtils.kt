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

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles
import org.intellij.lang.annotations.Language
import java.io.File
import kotlin.io.path.createTempDirectory

fun java(to: String, @Language("JAVA") source: String): TestFile {
    return TestFiles.java(to, source.trimIndent())
}

fun java(@Language("JAVA") source: String): TestFile {
    return TestFiles.java(source.trimIndent())
}

fun kotlin(@Language("kotlin") source: String): TestFile {
    return TestFiles.kotlin(source.trimIndent())
}

fun kotlin(to: String, @Language("kotlin") source: String): TestFile {
    return TestFiles.kotlin(to, source.trimIndent())
}

/** Creates a temporary directory and cleans up afterwards */
inline fun tempDirectory(action: (File) -> Unit) {
    val tempDirectory = createTempDirectory().toFile()
    try {
        action(tempDirectory)
    } finally {
        tempDirectory.deleteRecursively()
    }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.ARG_ANDROID_JAR_PATTERN
import com.android.tools.metalava.ARG_CURRENT_CODENAME
import com.android.tools.metalava.ARG_CURRENT_VERSION
import com.android.tools.metalava.ARG_FIRST_VERSION
import com.android.tools.metalava.ARG_GENERATE_API_LEVELS
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.getApiLookup
import com.android.tools.metalava.java
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.text.Charsets.UTF_8

class ApiGeneratorTest : DriverTest() {
    @Test
    fun `Extract API levels`() {
        var oldSdkJars = File("prebuilts/tools/common/api-versions")
        if (!oldSdkJars.isDirectory) {
            oldSdkJars = File("../../prebuilts/tools/common/api-versions")
            if (!oldSdkJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found - is \$PWD set to an Android source tree?")
                return
            }
        }

        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${oldSdkJars.path}/android-%/android.jar",
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_CURRENT_CODENAME,
                "Z",
                ARG_CURRENT_VERSION,
                "89" // not real api level of Z
            ),
            sourceFiles = arrayOf(
                java(
                    """
                    package android.pkg;
                    public class MyTest {
                    }
                    """
                )
            )
        )

        assertTrue(output.isFile)

        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<class name=\"android/Manifest\$permission\" since=\"1\">"))
        assertTrue(xml.contains("<field name=\"BIND_CARRIER_MESSAGING_SERVICE\" since=\"22\" deprecated=\"23\"/>"))
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"90\""))
        assertFalse(xml.contains("<implements name=\"java/lang/annotation/Annotation\" removed=\""))
        assertFalse(xml.contains("<extends name=\"java/lang/Enum\" removed=\""))
        assertFalse(xml.contains("<method name=\"append(C)Ljava/lang/AbstractStringBuilder;\""))

        // Also make sure package private super classes are pruned
        assertFalse(xml.contains("<extends name=\"android/icu/util/CECalendar\""))

        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())

        // Make sure we're really using the correct database, not the SDK one. (This placeholder
        // class is provided as a source file above.)
        assertEquals(90, apiLookup.getClassVersion("android.pkg.MyTest"))

        apiLookup.getClassVersion("android.v")
        assertEquals(5, apiLookup.getFieldVersion("android.Manifest\$permission", "AUTHENTICATE_ACCOUNTS"))

        val methodVersion = apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(24, methodVersion)
    }

    @Test
    fun `Extract System API`() {
        // These are the wrong jar paths but this test doesn't actually care what the
        // content of the jar files, just checking the logic of starting the database
        // at some higher number than 1
        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }

        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_FIRST_VERSION,
                "21"
            )
        )

        assertTrue(output.isFile)
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<api version=\"2\" min=\"21\">"))
        assertTrue(xml.contains("<class name=\"android/Manifest\" since=\"21\">"))
        assertTrue(xml.contains("<field name=\"showWhenLocked\" since=\"27\"/>"))

        val apiLookup = getApiLookup(output)
        apiLookup.getClassVersion("android.v")
        // This field was added in API level 5, but when we're starting the count higher
        // (as in the system API), the first introduced API level is the one we use
        assertEquals(21, apiLookup.getFieldVersion("android.Manifest\$permission", "AUTHENTICATE_ACCOUNTS"))

        val methodVersion = apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(24, methodVersion)
    }

    @Test
    fun `Correct API Level for release`() {
        var oldSdkJars = File("prebuilts/tools/common/api-versions")
        if (!oldSdkJars.isDirectory) {
            oldSdkJars = File("../../prebuilts/tools/common/api-versions")
            if (!oldSdkJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found - is \$PWD set to an Android source tree?")
                return
            }
        }

        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${oldSdkJars.path}/android-%/android.jar",
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_CURRENT_CODENAME,
                "REL",
                ARG_CURRENT_VERSION,
                "89" // not real api level
            ),
            sourceFiles = arrayOf(
                java(
                    """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                )
            )
        )

        assertTrue(output.isFile)
        // Anything with a REL codename is in the current API level
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"89\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        assertEquals(89, apiLookup.getClassVersion("android.pkg.MyTest"))
    }

    @Test
    fun `Correct API Level for non-release`() {
        var oldSdkJars = File("prebuilts/tools/common/api-versions")
        if (!oldSdkJars.isDirectory) {
            oldSdkJars = File("../../prebuilts/tools/common/api-versions")
            if (!oldSdkJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found - is \$PWD set to an Android source tree?")
                return
            }
        }

        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${oldSdkJars.path}/android-%/android.jar",
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_CURRENT_CODENAME,
                "ZZZ", // not just Z, but very ZZZ
                ARG_CURRENT_VERSION,
                "89" // not real api level
            ),
            sourceFiles = arrayOf(
                java(
                    """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                )
            )
        )

        assertTrue(output.isFile)
        // Metalava should understand that a codename means "current api + 1"
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"90\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        assertEquals(90, apiLookup.getClassVersion("android.pkg.MyTest"))
    }
}

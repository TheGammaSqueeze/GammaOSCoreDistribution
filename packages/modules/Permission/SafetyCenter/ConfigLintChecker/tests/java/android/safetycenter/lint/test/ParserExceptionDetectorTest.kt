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

package android.safetycenter.lint.test

import android.safetycenter.lint.ParserExceptionDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("UnstableApiUsage")
@RunWith(JUnit4::class)
class ParserExceptionDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = ParserExceptionDetector()

    override fun getIssues(): List<Issue> = listOf(ParserExceptionDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun validConfig_doesNotThrow() {
        lint()
            .files(
                (xml(
                    "res/raw/safety_center_config.xml",
                    """
<safety-center-config>
    <safety-sources-config>
        <safety-sources-group
            id="group"
            title="@lint.test.pkg:string/reference"
            summary="@lint.test.pkg:string/reference">
            <static-safety-source
                id="source"
                title="@lint.test.pkg:string/reference"
                summary="@lint.test.pkg:string/reference"
                intentAction="intent"
                profile="primary_profile_only"/>
        </safety-sources-group>
    </safety-sources-config>
</safety-center-config>
                """)),
                (xml(
                    "res/values/strings.xml",
                    """
<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <string name="reference" translatable="false">Reference</string>
</resources>
                """)))
            .run()
            .expectClean()
    }

    @Test
    fun invalidConfig_throws() {
        lint()
            .files((xml("res/raw/safety_center_config.xml", "<invalid-root/>")))
            .run()
            .expect(
                "res/raw/safety_center_config.xml: Error: Parser exception: " +
                    "\"Element safety-center-config missing\", cause: \"null\" " +
                    "[InvalidSafetyCenterConfig]\n1 errors, 0 warnings")
    }

    @Test
    fun unrelatedFile_doesNotThrow() {
        lint()
            .files((xml("res/raw/some_other_config.xml", "<some-other-root/>")))
            .run()
            .expectClean()
    }

    @Test
    fun unrelatedFolder_doesNotThrow() {
        lint().files((xml("res/values/strings.xml", "<some-other-root/>"))).run().expectClean()
    }
}

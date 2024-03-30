/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.testutils

import android.os.Build
import androidx.test.InstrumentationRegistry
import com.android.modules.utils.build.SdkLevel
import kotlin.test.fail
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.regex.Pattern

// TODO: Remove it when Build.VERSION_CODES.SC_V2 is available
const val SC_V2 = 32

private val MAX_TARGET_SDK_ANNOTATION_RE = Pattern.compile("MaxTargetSdk([0-9]+)$")
private val targetSdk = InstrumentationRegistry.getContext().applicationInfo.targetSdkVersion

/**
 * Returns true if the development SDK version of the device is in the provided range.
 *
 * If the device is not using a release SDK, the development SDK is considered to be higher than
 * [Build.VERSION.SDK_INT].
 */
fun isDevSdkInRange(minExclusive: Int?, maxInclusive: Int?): Boolean {
    return (minExclusive == null || isDevSdkAfter(minExclusive)) &&
            (maxInclusive == null || isDevSdkUpTo(maxInclusive))
}

private fun isDevSdkAfter(minExclusive: Int): Boolean {
    // A development build for T typically has SDK_INT = 30 (R) or SDK_INT = 31 (S), so SDK_INT
    // alone cannot be used to check the SDK version.
    // For recent SDKs that still have development builds used for testing, use SdkLevel utilities
    // instead of SDK_INT.
    return when (minExclusive) {
        // TODO: Use Build.VERSION_CODES.SC_V2 when it is available
        SC_V2 -> SdkLevel.isAtLeastT()
        // TODO: To use SdkLevel.isAtLeastSv2 when available
        Build.VERSION_CODES.S -> fail("Do you expect to ignore the test until T? Use SC_V2 instead")
        Build.VERSION_CODES.R -> SdkLevel.isAtLeastS()
        // Development builds of SDK versions <= R are not used anymore
        else -> Build.VERSION.SDK_INT > minExclusive
    }
}

private fun isDevSdkUpTo(maxInclusive: Int): Boolean {
    return when (maxInclusive) {
        // TODO: Use Build.VERSION_CODES.SC_V2 when it is available
        SC_V2 -> !SdkLevel.isAtLeastT()
        // TODO: To use SdkLevel.isAtLeastSv2 when available
        Build.VERSION_CODES.S ->
                fail("Do you expect to ignore the test before T? Use SC_V2 instead")
        Build.VERSION_CODES.R -> !SdkLevel.isAtLeastS()
        // Development builds of SDK versions <= R are not used anymore
        else -> Build.VERSION.SDK_INT <= maxInclusive
    }
}

private fun getMaxTargetSdk(description: Description): Int? {
    return description.annotations.firstNotNullOfOrNull {
        MAX_TARGET_SDK_ANNOTATION_RE.matcher(it.annotationClass.simpleName).let { m ->
            if (m.find()) m.group(1).toIntOrNull() else null
        }
    }
}

/**
 * A test rule to ignore tests based on the development SDK level.
 *
 * If the device is not using a release SDK, the development SDK is considered to be higher than
 * [Build.VERSION.SDK_INT].
 *
 * @param ignoreClassUpTo Skip all tests in the class if the device dev SDK is <= this value.
 * @param ignoreClassAfter Skip all tests in the class if the device dev SDK is > this value.
 */
class DevSdkIgnoreRule @JvmOverloads constructor(
    private val ignoreClassUpTo: Int? = null,
    private val ignoreClassAfter: Int? = null
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return IgnoreBySdkStatement(base, description)
    }

    /**
     * Ignore the test for any development SDK that is strictly after [value].
     *
     * If the device is not using a release SDK, the development SDK is considered to be higher
     * than [Build.VERSION.SDK_INT].
     */
    annotation class IgnoreAfter(val value: Int)

    /**
     * Ignore the test for any development SDK that lower than or equal to [value].
     *
     * If the device is not using a release SDK, the development SDK is considered to be higher
     * than [Build.VERSION.SDK_INT].
     */
    annotation class IgnoreUpTo(val value: Int)

    private inner class IgnoreBySdkStatement(
        private val base: Statement,
        private val description: Description
    ) : Statement() {
        override fun evaluate() {
            val ignoreAfter = description.getAnnotation(IgnoreAfter::class.java)
            val ignoreUpTo = description.getAnnotation(IgnoreUpTo::class.java)

            val devSdkMessage = "Skipping test for build ${Build.VERSION.CODENAME} " +
                    "with SDK ${Build.VERSION.SDK_INT}"
            assumeTrue(devSdkMessage, isDevSdkInRange(ignoreClassUpTo, ignoreClassAfter))
            assumeTrue(devSdkMessage, isDevSdkInRange(ignoreUpTo?.value, ignoreAfter?.value))

            val maxTargetSdk = getMaxTargetSdk(description)
            if (maxTargetSdk != null) {
                assumeTrue("Skipping test, target SDK $targetSdk greater than $maxTargetSdk",
                        targetSdk <= maxTargetSdk)
            }
            base.evaluate()
        }
    }
}

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

package com.android.testutils

import com.android.ddmlib.testrunner.TestResult
import com.android.tradefed.invoker.TestInformation
import com.android.tradefed.result.CollectingTestListener
import com.android.tradefed.result.ddmlib.DefaultRemoteAndroidTestRunner
import com.android.tradefed.targetprep.BaseTargetPreparer
import com.android.tradefed.targetprep.suite.SuiteApkInstaller

private const val CONNECTIVITY_CHECKER_APK = "ConnectivityChecker.apk"
private const val CONNECTIVITY_PKG_NAME = "com.android.testutils.connectivitychecker"
// As per the <instrumentation> defined in the checker manifest
private const val CONNECTIVITY_CHECK_RUNNER_NAME = "androidx.test.runner.AndroidJUnitRunner"

/**
 * A target preparer that verifies that the device was setup correctly for connectivity tests.
 *
 * For quick and dirty local testing, can be disabled by running tests with
 * "atest -- --test-arg com.android.testutils.ConnectivityCheckTargetPreparer:disable:true".
 */
class ConnectivityCheckTargetPreparer : BaseTargetPreparer() {
    val installer = SuiteApkInstaller()

    override fun setUp(testInformation: TestInformation) {
        if (isDisabled) return
        installer.setCleanApk(true)
        installer.addTestFileName(CONNECTIVITY_CHECKER_APK)
        installer.setShouldGrantPermission(true)
        installer.setUp(testInformation)

        val runner = DefaultRemoteAndroidTestRunner(
                CONNECTIVITY_PKG_NAME,
                CONNECTIVITY_CHECK_RUNNER_NAME,
                testInformation.device.iDevice)
        runner.runOptions = "--no-hidden-api-checks"

        val receiver = CollectingTestListener()
        if (!testInformation.device.runInstrumentationTests(runner, receiver)) {
            throw AssertionError("Device state check failed to complete")
        }

        val runResult = receiver.currentRunResults
        if (runResult.isRunFailure) {
            throw AssertionError("Failed to check device state before the test: " +
                    runResult.runFailureMessage)
        }

        if (!runResult.hasFailedTests()) return
        val errorMsg = runResult.testResults.mapNotNull { (testDescription, testResult) ->
            if (TestResult.TestStatus.FAILURE != testResult.status) null
            else "$testDescription: ${testResult.stackTrace}"
        }.joinToString("\n")

        throw AssertionError("Device setup checks failed. Check the test bench: \n$errorMsg")
    }

    override fun tearDown(testInformation: TestInformation?, e: Throwable?) {
        if (isTearDownDisabled) return
        installer.tearDown(testInformation, e)
    }
}
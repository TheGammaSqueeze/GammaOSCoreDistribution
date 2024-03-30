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

package android.nearby.integration.ui

import android.platform.test.rule.ArtifactSaver
import android.platform.test.rule.ScreenRecordRule
import android.platform.test.rule.TestWatcher
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.Description

abstract class BaseUiTest {
    @get:Rule
    var mGlobalTimeout: Timeout = Timeout.seconds(100) // Test times out in 1.67 minutes

    @get:Rule
    val mTestWatcherRule: TestRule = object : TestWatcher() {
        override fun failed(throwable: Throwable?, description: Description?) {
            super.failed(throwable, description)
            ArtifactSaver.onError(description, throwable)
        }
    }

    @get:Rule
    val mScreenRecordRule: TestRule = ScreenRecordRule()
}
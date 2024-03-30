/*
 * Copyright 2022 The Android Open Source Project
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

package platform.test.screenshot

import android.content.Context

/**
 * Rule to be used in platform project tests. Set's up the proper repository name and golden
 * directory.
 *
 * @param moduleDirectory Directory to be used for the module that contains the tests. This is
 * just a helper to avoid mixing goldens between different projects.
 * Example for module directory: "compose/material/material"
 * @param outputRootDir The root directory for output files.
 *
 * @hide
 */
class PlatformScreenshotTestRule(
    context: Context,
    moduleDirectory: String,
    outputRootDir: String? = null
) : ScreenshotTestRule(
        GoldenImagePathManager(context)
)

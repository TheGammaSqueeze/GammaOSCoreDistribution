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

package com.android.server.wm.flicker.helpers

import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.android.server.wm.traces.common.FlickerComponentName

/**
 * Helper to launch the default browser app (compatible with AOSP)
 *
 * This helper has no other functionality but the app launch.
 *
 * This helper is used to launch an app after some operations (e.g., navigation mode change),
 * so that the device is stable before executing flicker tests
 */
class SampleAppHelper(
    instrumentation: Instrumentation,
    private val pkgManager: PackageManager = instrumentation.context.packageManager
) : StandardAppHelper(
    instrumentation,
    "SampleApp",
    getBrowserComponent(pkgManager)
) {
    override fun getOpenAppIntent(): Intent =
        pkgManager.getLaunchIntentForPackage(component.packageName)
            ?: error("Unable to find intent for browser")

    companion object {
        private fun getBrowserIntent(): Intent {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }

        private fun getBrowserComponent(pkgManager: PackageManager): FlickerComponentName {
            val intent = getBrowserIntent()
            val resolveInfo = pkgManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?: error("Unable to resolve browser activity")
            return FlickerComponentName(resolveInfo.activityInfo.packageName, className = "")
        }
    }
}
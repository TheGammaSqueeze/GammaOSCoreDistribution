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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.MATCH_SYSTEM_ONLY
import android.content.pm.PackageManager.ResolveInfoFlags
import android.content.pm.ResolveInfo
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import com.android.server.nearby.fastpair.FastPairManager
import com.android.server.nearby.util.Environment
import com.google.common.truth.Truth.assertThat

/** UiMap for Nearby Mainline Half Sheet. */
object NearbyHalfSheetUiMap {
    private val PACKAGE_NAME: String = getHalfSheetApkPkgName()
    private const val ANDROID_WIDGET_BUTTON = "android.widget.Button"
    private const val ANDROID_WIDGET_IMAGE_VIEW = "android.widget.ImageView"
    private const val ANDROID_WIDGET_TEXT_VIEW = "android.widget.TextView"

    object DevicePairingFragment {
        val halfSheetTitle: BySelector =
            By.res(PACKAGE_NAME, "toolbar_title").clazz(ANDROID_WIDGET_TEXT_VIEW)
        val halfSheetSubtitle: BySelector =
            By.res(PACKAGE_NAME, "header_subtitle").clazz(ANDROID_WIDGET_TEXT_VIEW)
        val deviceImage: BySelector =
            By.res(PACKAGE_NAME, "pairing_pic").clazz(ANDROID_WIDGET_IMAGE_VIEW)
        val connectButton: BySelector =
            By.res(PACKAGE_NAME, "connect_btn").clazz(ANDROID_WIDGET_BUTTON).text("Connect")
        val infoButton: BySelector =
            By.res(PACKAGE_NAME, "info_icon").clazz(ANDROID_WIDGET_IMAGE_VIEW)
    }

    // Vendors might override HalfSheetUX in their vendor partition, query the package name
    // instead of hard coding. ex: Google overrides it in vendor/google/modules/TetheringGoogle.
    fun getHalfSheetApkPkgName(): String {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val resolveInfos: MutableList<ResolveInfo> =
            appContext.packageManager.queryIntentActivities(
                Intent(FastPairManager.ACTION_RESOURCES_APK),
                ResolveInfoFlags.of(MATCH_SYSTEM_ONLY.toLong())
            )

        // remove apps that don't live in the nearby apex
        resolveInfos.removeIf { !Environment.isAppInNearbyApex(it.activityInfo.applicationInfo) }

        assertThat(resolveInfos).hasSize(1)

        val halfSheetApkPkgName: String = resolveInfos[0].activityInfo.applicationInfo.packageName
        Log.i("NearbyHalfSheetUiMap", "Found half-sheet APK at: $halfSheetApkPkgName")
        return halfSheetApkPkgName
    }
}
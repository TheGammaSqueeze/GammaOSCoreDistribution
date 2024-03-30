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

package android.safetycenter.cts.testing

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** A class that knows the Settings app package name. */
object SettingsPackage {

    /** Returns the Settings app package name. */
    fun Context.getSettingsPackageName() =
            packageManager
                    .resolveActivity(
                            Intent(Settings.ACTION_SETTINGS),
                            PackageManager.MATCH_DEFAULT_ONLY or
                                    PackageManager.MATCH_DIRECT_BOOT_AWARE or
                                    PackageManager.MATCH_DIRECT_BOOT_UNAWARE)!!
                    .activityInfo
                    .packageName
}

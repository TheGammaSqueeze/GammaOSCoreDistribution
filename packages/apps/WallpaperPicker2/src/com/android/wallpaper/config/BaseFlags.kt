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
package com.android.wallpaper.config

import android.content.Context
import com.android.systemui.shared.customization.data.content.CustomizationProviderClient
import com.android.systemui.shared.customization.data.content.CustomizationProviderClientImpl
import com.android.systemui.shared.customization.data.content.CustomizationProviderContract as Contract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

abstract class BaseFlags {
    var customizationProviderClient: CustomizationProviderClient? = null
    open fun isStagingBackdropContentEnabled() = false
    open fun isWallpaperEffectEnabled() = false
    open fun isFullscreenWallpaperPreviewEnabled(context: Context): Boolean {
        return runBlocking { getCustomizationProviderClient(context).queryFlags() }
            .firstOrNull { flag ->
                flag.name == Contract.FlagsTable.FLAG_NAME_WALLPAPER_FULLSCREEN_PREVIEW
            }
            ?.value == true
    }
    fun isUseRevampedUiEnabled(context: Context): Boolean {
        return runBlocking { getCustomizationProviderClient(context).queryFlags() }
            .firstOrNull { flag ->
                flag.name == Contract.FlagsTable.FLAG_NAME_REVAMPED_WALLPAPER_UI
            }
            ?.value == true
    }
    fun isCustomClocksEnabled(context: Context): Boolean {
        return runBlocking { getCustomizationProviderClient(context).queryFlags() }
            .firstOrNull { flag ->
                flag.name == Contract.FlagsTable.FLAG_NAME_CUSTOM_CLOCKS_ENABLED
            }
            ?.value == true
    }
    fun isMonochromaticThemeEnabled(context: Context): Boolean {
        return runBlocking { getCustomizationProviderClient(context).queryFlags() }
            .firstOrNull { flag -> flag.name == Contract.FlagsTable.FLAG_NAME_MONOCHROMATIC_THEME }
            ?.value == true
    }

    fun isAIWallpaperEnabled(context: Context): Boolean {
        return runBlocking { getCustomizationProviderClient(context).queryFlags() }
            .firstOrNull { flag ->
                flag.name == Contract.FlagsTable.FLAG_NAME_WALLPAPER_PICKER_UI_FOR_AIWP
            }
            ?.value == true
    }

    private fun getCustomizationProviderClient(context: Context): CustomizationProviderClient {
        return customizationProviderClient
            ?: CustomizationProviderClientImpl(context, Dispatchers.IO).also {
                customizationProviderClient = it
            }
    }
}

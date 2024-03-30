/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 */
package com.android.customization.picker.color.data.repository

import android.app.WallpaperColors
import android.util.Log
import com.android.customization.model.CustomizationManager
import com.android.customization.model.color.ColorBundle
import com.android.customization.model.color.ColorCustomizationManager
import com.android.customization.model.color.ColorOption
import com.android.customization.model.color.ColorSeedOption
import com.android.customization.picker.color.shared.model.ColorOptionModel
import com.android.customization.picker.color.shared.model.ColorType
import com.android.systemui.monet.Style
import com.android.wallpaper.model.WallpaperColorsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine

// TODO (b/262924623): refactor to remove dependency on ColorCustomizationManager & ColorOption
// TODO (b/268203200): Create test for ColorPickerRepositoryImpl
class ColorPickerRepositoryImpl(
    wallpaperColorsViewModel: WallpaperColorsViewModel,
    private val colorManager: ColorCustomizationManager,
) : ColorPickerRepository {

    private val homeWallpaperColors: StateFlow<WallpaperColors?> =
        wallpaperColorsViewModel.homeWallpaperColors
    private val lockWallpaperColors: StateFlow<WallpaperColors?> =
        wallpaperColorsViewModel.lockWallpaperColors

    override val colorOptions: Flow<Map<ColorType, List<ColorOptionModel>>> =
        combine(homeWallpaperColors, lockWallpaperColors) { homeColors, lockColors ->
                homeColors to lockColors
            }
            .map { (homeColors, lockColors) ->
                suspendCancellableCoroutine { continuation ->
                    colorManager.setWallpaperColors(homeColors, lockColors)
                    colorManager.fetchOptions(
                        object : CustomizationManager.OptionsFetchedListener<ColorOption?> {
                            override fun onOptionsLoaded(options: MutableList<ColorOption?>?) {
                                val wallpaperColorOptions: MutableList<ColorOptionModel> =
                                    mutableListOf()
                                val presetColorOptions: MutableList<ColorOptionModel> =
                                    mutableListOf()
                                options?.forEach { option ->
                                    when (option) {
                                        is ColorSeedOption ->
                                            wallpaperColorOptions.add(option.toModel())
                                        is ColorBundle -> presetColorOptions.add(option.toModel())
                                    }
                                }
                                continuation.resumeWith(
                                    Result.success(
                                        mapOf(
                                            ColorType.WALLPAPER_COLOR to wallpaperColorOptions,
                                            ColorType.PRESET_COLOR to presetColorOptions
                                        )
                                    )
                                )
                            }

                            override fun onError(throwable: Throwable?) {
                                Log.e(TAG, "Error loading theme bundles", throwable)
                                continuation.resumeWith(
                                    Result.failure(
                                        throwable ?: Throwable("Error loading theme bundles")
                                    )
                                )
                            }
                        },
                        /* reload= */ false
                    )
                }
            }

    override suspend fun select(colorOptionModel: ColorOptionModel) =
        suspendCancellableCoroutine { continuation ->
            colorManager.apply(
                colorOptionModel.colorOption,
                object : CustomizationManager.Callback {
                    override fun onSuccess() {
                        continuation.resumeWith(Result.success(Unit))
                    }

                    override fun onError(throwable: Throwable?) {
                        Log.w(TAG, "Apply theme with error", throwable)
                        continuation.resumeWith(
                            Result.failure(throwable ?: Throwable("Error loading theme bundles"))
                        )
                    }
                }
            )
        }

    override fun getCurrentColorOption(): ColorOptionModel {
        val overlays = colorManager.currentOverlays
        val styleOrNull = colorManager.currentStyle
        val style = styleOrNull?.let { Style.valueOf(it) } ?: Style.TONAL_SPOT
        val colorOptionBuilder =
            // Does not matter whether ColorSeedOption or ColorBundle builder is used here
            // because to apply the color, one just needs a generic ColorOption
            ColorSeedOption.Builder().setSource(colorManager.currentColorSource).setStyle(style)
        for (overlay in overlays) {
            colorOptionBuilder.addOverlayPackage(overlay.key, overlay.value)
        }
        val colorOption = colorOptionBuilder.build()
        return ColorOptionModel(
            key = "${colorOption.style}::${colorOption.serializedPackages}",
            colorOption = colorOption,
            isSelected = false,
        )
    }

    override fun getCurrentColorSource(): String? {
        return colorManager.currentColorSource
    }

    private fun ColorOption.toModel(): ColorOptionModel {
        return ColorOptionModel(
            key = "${this.style}::${this.serializedPackages}",
            colorOption = this,
            isSelected = isActive(colorManager),
        )
    }

    companion object {
        private const val TAG = "ColorPickerRepositoryImpl"
    }
}

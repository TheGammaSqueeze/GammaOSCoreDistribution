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

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import com.android.customization.model.color.ColorBundle
import com.android.customization.model.color.ColorOptionsProvider
import com.android.customization.model.color.ColorSeedOption
import com.android.customization.picker.color.shared.model.ColorOptionModel
import com.android.customization.picker.color.shared.model.ColorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeColorPickerRepository(private val context: Context) : ColorPickerRepository {

    private lateinit var selectedColorOption: ColorOptionModel

    private val _colorOptions =
        MutableStateFlow(
            mapOf<ColorType, List<ColorOptionModel>>(
                ColorType.WALLPAPER_COLOR to listOf(),
                ColorType.PRESET_COLOR to listOf()
            )
        )
    override val colorOptions: StateFlow<Map<ColorType, List<ColorOptionModel>>> =
        _colorOptions.asStateFlow()

    init {
        setOptions(4, 4, ColorType.WALLPAPER_COLOR, 0)
    }

    fun setOptions(
        numWallpaperOptions: Int,
        numPresetOptions: Int,
        selectedColorOptionType: ColorType,
        selectedColorOptionIndex: Int
    ) {
        _colorOptions.value =
            mapOf(
                ColorType.WALLPAPER_COLOR to
                    buildList {
                        repeat(times = numWallpaperOptions) { index ->
                            val isSelected =
                                selectedColorOptionType == ColorType.WALLPAPER_COLOR &&
                                    selectedColorOptionIndex == index
                            val colorOption =
                                ColorOptionModel(
                                    key = "${ColorType.WALLPAPER_COLOR}::$index",
                                    colorOption = buildWallpaperOption(index),
                                    isSelected = isSelected,
                                )
                            if (isSelected) {
                                selectedColorOption = colorOption
                            }
                            add(colorOption)
                        }
                    },
                ColorType.PRESET_COLOR to
                    buildList {
                        repeat(times = numPresetOptions) { index ->
                            val isSelected =
                                selectedColorOptionType == ColorType.PRESET_COLOR &&
                                    selectedColorOptionIndex == index
                            val colorOption =
                                ColorOptionModel(
                                    key = "${ColorType.PRESET_COLOR}::$index",
                                    colorOption = buildPresetOption(index),
                                    isSelected =
                                        selectedColorOptionType == ColorType.PRESET_COLOR &&
                                            selectedColorOptionIndex == index,
                                )
                            if (isSelected) {
                                selectedColorOption = colorOption
                            }
                            add(colorOption)
                        }
                    }
            )
    }

    private fun buildPresetOption(index: Int): ColorBundle {
        return ColorBundle.Builder()
            .addOverlayPackage("TEST_PACKAGE_TYPE", "preset_color")
            .addOverlayPackage("TEST_PACKAGE_INDEX", "$index")
            .setIndex(index)
            .build(context)
    }

    private fun buildWallpaperOption(index: Int): ColorSeedOption {
        return ColorSeedOption.Builder()
            .setLightColors(
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT
                )
            )
            .setDarkColors(
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT
                )
            )
            .addOverlayPackage("TEST_PACKAGE_TYPE", "wallpaper_color")
            .addOverlayPackage("TEST_PACKAGE_INDEX", "$index")
            .setIndex(index)
            .build()
    }

    override suspend fun select(colorOptionModel: ColorOptionModel) {
        val colorOptions = _colorOptions.value
        val wallpaperColorOptions = colorOptions[ColorType.WALLPAPER_COLOR]!!
        val newWallpaperColorOptions = buildList {
            wallpaperColorOptions.forEach { option ->
                add(
                    ColorOptionModel(
                        key = option.key,
                        colorOption = option.colorOption,
                        isSelected = option.testEquals(colorOptionModel),
                    )
                )
            }
        }
        val basicColorOptions = colorOptions[ColorType.PRESET_COLOR]!!
        val newBasicColorOptions = buildList {
            basicColorOptions.forEach { option ->
                add(
                    ColorOptionModel(
                        key = option.key,
                        colorOption = option.colorOption,
                        isSelected = option.testEquals(colorOptionModel),
                    )
                )
            }
        }
        _colorOptions.value =
            mapOf(
                ColorType.WALLPAPER_COLOR to newWallpaperColorOptions,
                ColorType.PRESET_COLOR to newBasicColorOptions
            )
    }

    override fun getCurrentColorOption(): ColorOptionModel = selectedColorOption

    override fun getCurrentColorSource(): String? =
        when (selectedColorOption.colorOption) {
            is ColorSeedOption -> ColorOptionsProvider.COLOR_SOURCE_HOME
            is ColorBundle -> ColorOptionsProvider.COLOR_SOURCE_PRESET
            else -> null
        }

    private fun ColorOptionModel.testEquals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        return if (other is ColorOptionModel) {
            TextUtils.equals(this.key, other.key)
        } else {
            false
        }
    }
}

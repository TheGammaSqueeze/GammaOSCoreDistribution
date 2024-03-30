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
package com.android.customization.picker.color.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.customization.model.color.ColorBundle
import com.android.customization.model.color.ColorSeedOption
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.color.shared.model.ColorType
import com.android.wallpaper.R
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Models UI state for a color picker experience. */
class ColorPickerViewModel
private constructor(
    context: Context,
    private val interactor: ColorPickerInteractor,
) : ViewModel() {

    private val selectedColorTypeTabId = MutableStateFlow<ColorType?>(null)

    /** View-models for each color tab. */
    val colorTypeTabs: Flow<Map<ColorType, ColorTypeTabViewModel>> =
        combine(
            interactor.colorOptions,
            selectedColorTypeTabId,
        ) { colorOptions, selectedColorTypeIdOrNull ->
            colorOptions.keys
                .mapIndexed { index, colorType ->
                    val isSelected =
                        (selectedColorTypeIdOrNull == null && index == 0) ||
                            selectedColorTypeIdOrNull == colorType
                    colorType to
                        ColorTypeTabViewModel(
                            name =
                                when (colorType) {
                                    ColorType.WALLPAPER_COLOR ->
                                        context.resources.getString(R.string.wallpaper_color_tab)
                                    ColorType.PRESET_COLOR ->
                                        context.resources.getString(R.string.preset_color_tab_2)
                                },
                            isSelected = isSelected,
                            onClick =
                                if (isSelected) {
                                    null
                                } else {
                                    { this.selectedColorTypeTabId.value = colorType }
                                },
                        )
                }
                .toMap()
        }

    /** View-models for each color tab subheader */
    val colorTypeTabSubheader: Flow<String> =
        selectedColorTypeTabId.map { selectedColorTypeIdOrNull ->
            when (selectedColorTypeIdOrNull ?: ColorType.WALLPAPER_COLOR) {
                ColorType.WALLPAPER_COLOR ->
                    context.resources.getString(R.string.wallpaper_color_subheader)
                ColorType.PRESET_COLOR ->
                    context.resources.getString(R.string.preset_color_subheader)
            }
        }

    /** The list of all color options mapped by their color type */
    private val allColorOptions:
        Flow<Map<ColorType, List<OptionItemViewModel<ColorOptionIconViewModel>>>> =
        interactor.colorOptions.map { colorOptions ->
            colorOptions
                .map { colorOptionEntry ->
                    colorOptionEntry.key to
                        when (colorOptionEntry.key) {
                            ColorType.WALLPAPER_COLOR -> {
                                colorOptionEntry.value.map { colorOptionModel ->
                                    val colorSeedOption: ColorSeedOption =
                                        colorOptionModel.colorOption as ColorSeedOption
                                    val colors =
                                        colorSeedOption.previewInfo.resolveColors(context.resources)
                                    val isSelectedFlow: StateFlow<Boolean> =
                                        interactor.activeColorOption
                                            .map {
                                                it?.colorOption?.isEquivalent(
                                                    colorOptionModel.colorOption
                                                )
                                                    ?: colorOptionModel.isSelected
                                            }
                                            .stateIn(viewModelScope)
                                    OptionItemViewModel<ColorOptionIconViewModel>(
                                        key =
                                            MutableStateFlow(colorOptionModel.key)
                                                as StateFlow<String>,
                                        payload =
                                            ColorOptionIconViewModel(
                                                colors[0],
                                                colors[1],
                                                colors[2],
                                                colors[3]
                                            ),
                                        text =
                                            Text.Loaded(
                                                colorSeedOption
                                                    .getContentDescription(context)
                                                    .toString()
                                            ),
                                        isSelected = isSelectedFlow,
                                        onClicked =
                                            isSelectedFlow.map { isSelected ->
                                                if (isSelected) {
                                                    null
                                                } else {
                                                    {
                                                        viewModelScope.launch {
                                                            interactor.select(colorOptionModel)
                                                        }
                                                    }
                                                }
                                            },
                                    )
                                }
                            }
                            ColorType.PRESET_COLOR -> {
                                colorOptionEntry.value.map { colorOptionModel ->
                                    val colorBundle: ColorBundle =
                                        colorOptionModel.colorOption as ColorBundle
                                    val primaryColor =
                                        colorBundle.previewInfo.resolvePrimaryColor(
                                            context.resources
                                        )
                                    val secondaryColor =
                                        colorBundle.previewInfo.resolveSecondaryColor(
                                            context.resources
                                        )
                                    val isSelectedFlow: StateFlow<Boolean> =
                                        interactor.activeColorOption
                                            .map {
                                                it?.colorOption?.isEquivalent(
                                                    colorOptionModel.colorOption
                                                )
                                                    ?: colorOptionModel.isSelected
                                            }
                                            .stateIn(viewModelScope)
                                    OptionItemViewModel<ColorOptionIconViewModel>(
                                        key =
                                            MutableStateFlow(colorOptionModel.key)
                                                as StateFlow<String>,
                                        payload =
                                            ColorOptionIconViewModel(
                                                primaryColor,
                                                secondaryColor,
                                                primaryColor,
                                                secondaryColor
                                            ),
                                        text =
                                            Text.Loaded(
                                                colorBundle
                                                    .getContentDescription(context)
                                                    .toString()
                                            ),
                                        isSelected = isSelectedFlow,
                                        onClicked =
                                            isSelectedFlow.map { isSelected ->
                                                if (isSelected) {
                                                    null
                                                } else {
                                                    {
                                                        viewModelScope.launch {
                                                            interactor.select(colorOptionModel)
                                                        }
                                                    }
                                                }
                                            },
                                    )
                                }
                            }
                        }
                }
                .toMap()
        }

    /** The list of all available color options for the selected Color Type. */
    val colorOptions: Flow<List<OptionItemViewModel<ColorOptionIconViewModel>>> =
        combine(allColorOptions, selectedColorTypeTabId) {
            allColorOptions: Map<ColorType, List<OptionItemViewModel<ColorOptionIconViewModel>>>,
            selectedColorTypeIdOrNull ->
            val selectedColorTypeId = selectedColorTypeIdOrNull ?: ColorType.WALLPAPER_COLOR
            allColorOptions[selectedColorTypeId]!!
        }

    /** The list of color options for the color section */
    val colorSectionOptions: Flow<List<OptionItemViewModel<ColorOptionIconViewModel>>> =
        allColorOptions.map { allColorOptions ->
            val wallpaperOptions = allColorOptions[ColorType.WALLPAPER_COLOR]
            val presetOptions = allColorOptions[ColorType.PRESET_COLOR]
            val subOptions =
                wallpaperOptions!!.subList(0, min(COLOR_SECTION_OPTION_SIZE, wallpaperOptions.size))
            // Add additional options based on preset colors if size of wallpaper color options is
            // less than COLOR_SECTION_OPTION_SIZE
            val additionalSubOptions =
                presetOptions!!.subList(
                    0,
                    min(
                        max(0, COLOR_SECTION_OPTION_SIZE - wallpaperOptions.size),
                        presetOptions.size,
                    )
                )
            subOptions + additionalSubOptions
        }

    class Factory(
        private val context: Context,
        private val interactor: ColorPickerInteractor,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ColorPickerViewModel(
                context = context,
                interactor = interactor,
            )
                as T
        }
    }

    companion object {
        private const val COLOR_SECTION_OPTION_SIZE = 5
    }
}

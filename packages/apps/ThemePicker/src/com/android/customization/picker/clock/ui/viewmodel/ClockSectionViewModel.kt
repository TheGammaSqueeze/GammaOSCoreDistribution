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
package com.android.customization.picker.clock.ui.viewmodel

import android.content.Context
import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import com.android.customization.picker.clock.shared.ClockSize
import com.android.wallpaper.R
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** View model for the clock section view on the lockscreen customization surface. */
class ClockSectionViewModel(context: Context, interactor: ClockPickerInteractor) {
    val appContext: Context = context.applicationContext
    val clockColorMap: Map<String, ClockColorViewModel> =
        ClockColorViewModel.getPresetColorMap(appContext.resources)
    val selectedClockColorAndSizeText: Flow<String> =
        combine(interactor.selectedColorId, interactor.selectedClockSize, ::Pair).map {
            (selectedColorId, selectedClockSize) ->
            val colorText =
                clockColorMap[selectedColorId]?.colorName
                    ?: context.getString(R.string.default_theme_title)
            val sizeText =
                when (selectedClockSize) {
                    ClockSize.SMALL -> appContext.getString(R.string.clock_size_small)
                    ClockSize.DYNAMIC -> appContext.getString(R.string.clock_size_dynamic)
                }
            appContext
                .getString(R.string.clock_color_and_size_description, colorText, sizeText)
                .lowercase()
                .replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
        }
}

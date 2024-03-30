/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.customization.module

import android.app.Activity
import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.android.customization.model.theme.OverlayManagerCompat
import com.android.customization.model.theme.ThemeBundleProvider
import com.android.customization.model.theme.ThemeManager
import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.customization.picker.clock.ui.viewmodel.ClockCarouselViewModel
import com.android.customization.picker.clock.ui.viewmodel.ClockSectionViewModel
import com.android.customization.picker.clock.ui.viewmodel.ClockSettingsViewModel
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.customization.picker.quickaffordance.domain.interactor.KeyguardQuickAffordancePickerInteractor
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.wallpaper.model.WallpaperColorsViewModel
import com.android.wallpaper.module.Injector

interface CustomizationInjector : Injector {
    fun getCustomizationPreferences(context: Context): CustomizationPreferences

    fun getThemeManager(
        provider: ThemeBundleProvider,
        activity: FragmentActivity,
        overlayManagerCompat: OverlayManagerCompat,
        logger: ThemesUserEventLogger,
    ): ThemeManager

    fun getKeyguardQuickAffordancePickerInteractor(
        context: Context,
    ): KeyguardQuickAffordancePickerInteractor

    fun getClockRegistry(context: Context): ClockRegistry

    fun getClockPickerInteractor(context: Context): ClockPickerInteractor

    fun getClockSectionViewModel(context: Context): ClockSectionViewModel

    fun getColorPickerInteractor(
        context: Context,
        wallpaperColorsViewModel: WallpaperColorsViewModel,
    ): ColorPickerInteractor

    fun getColorPickerViewModelFactory(
        context: Context,
        wallpaperColorsViewModel: WallpaperColorsViewModel,
    ): ColorPickerViewModel.Factory

    fun getClockCarouselViewModel(context: Context): ClockCarouselViewModel

    fun getClockViewFactory(activity: Activity): ClockViewFactory

    fun getClockSettingsViewModelFactory(
        context: Context,
        wallpaperColorsViewModel: WallpaperColorsViewModel,
    ): ClockSettingsViewModel.Factory
}

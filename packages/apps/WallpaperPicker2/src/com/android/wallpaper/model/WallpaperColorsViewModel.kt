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
package com.android.wallpaper.model

import android.app.WallpaperColors
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel class to keep track of WallpaperColors for the current wallpaper
 *
 * TODO (b/269451870): Rename to WallpaperColorsRepository
 */
class WallpaperColorsViewModel {

    /**
     * WallpaperColors exposed as live data to allow Java integration
     *
     * TODO (b/262924584): Remove after ColorSectionController2 & ColorCustomizationManager refactor
     */
    private val _homeWallpaperColorsLiveData: MutableLiveData<WallpaperColors> by lazy {
        MutableLiveData<WallpaperColors>()
    }
    val homeWallpaperColorsLiveData: LiveData<WallpaperColors> = _homeWallpaperColorsLiveData
    private val _lockWallpaperColorsLiveData: MutableLiveData<WallpaperColors> by lazy {
        MutableLiveData<WallpaperColors>()
    }
    val lockWallpaperColorsLiveData: LiveData<WallpaperColors> = _lockWallpaperColorsLiveData

    private val _homeWallpaperColors = MutableStateFlow<WallpaperColors?>(null)
    /** WallpaperColors for the currently set home wallpaper */
    val homeWallpaperColors: StateFlow<WallpaperColors?> = _homeWallpaperColors.asStateFlow()

    private val _lockWallpaperColors = MutableStateFlow<WallpaperColors?>(null)
    /** WallpaperColors for the currently set lock wallpaper */
    val lockWallpaperColors: StateFlow<WallpaperColors?> = _lockWallpaperColors.asStateFlow()

    fun setHomeWallpaperColors(colors: WallpaperColors?) {
        _homeWallpaperColors.value = colors
        if (colors != _homeWallpaperColorsLiveData.value) {
            _homeWallpaperColorsLiveData.value = colors
        }
    }

    fun setLockWallpaperColors(colors: WallpaperColors?) {
        _lockWallpaperColors.value = colors
        if (colors != _lockWallpaperColorsLiveData.value) {
            _lockWallpaperColorsLiveData.value = colors
        }
    }
}

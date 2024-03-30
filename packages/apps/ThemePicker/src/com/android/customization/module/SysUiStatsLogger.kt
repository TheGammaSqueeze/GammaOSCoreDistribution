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
package com.android.customization.module

import android.stats.style.StyleEnums
import com.android.systemui.shared.system.SysUiStatsLog
import com.android.systemui.shared.system.SysUiStatsLog.STYLE_UI_CHANGED

/** The builder for [SysUiStatsLog]. */
class SysUiStatsLogger {

    private var atom = STYLE_UI_CHANGED
    private var action = StyleEnums.DEFAULT_ACTION
    private var colorPackageHash = 0
    private var fontPackageHash = 0
    private var shapePackageHash = 0
    private var clockPackageHash = 0
    private var launcherGrid = 0
    private var wallpaperCategoryHash = 0
    private var wallpaperIdHash = 0
    private var colorPreference = 0
    private var locationPreference = StyleEnums.EFFECT_PREFERENCE_UNSPECIFIED
    private var datePreference = StyleEnums.DATE_PREFERENCE_UNSPECIFIED
    private var launchedPreference = StyleEnums.LAUNCHED_PREFERENCE_UNSPECIFIED
    private var effectPreference = StyleEnums.EFFECT_PREFERENCE_UNSPECIFIED
    private var effectIdHash = 0
    private var lockWallpaperCategoryHash = 0
    private var lockWallpaperIdHash = 0
    private var firstLaunchDateSinceSetup = 0
    private var firstWallpaperApplyDateSinceSetup = 0
    private var appLaunchCount = 0
    private var colorVariant = 0
    private var timeElapsedMillis = 0L
    private var effectResultCode = -1

    fun setAction(action: Int) = apply { this.action = action }

    fun setColorPackageHash(color_package_hash: Int) = apply {
        this.colorPackageHash = color_package_hash
    }

    fun setFontPackageHash(font_package_hash: Int) = apply {
        this.fontPackageHash = font_package_hash
    }

    fun setShapePackageHash(shape_package_hash: Int) = apply {
        this.shapePackageHash = shape_package_hash
    }

    fun setClockPackageHash(clock_package_hash: Int) = apply {
        this.clockPackageHash = clock_package_hash
    }

    fun setLauncherGrid(launcher_grid: Int) = apply { this.launcherGrid = launcher_grid }

    fun setWallpaperCategoryHash(wallpaper_category_hash: Int) = apply {
        this.wallpaperCategoryHash = wallpaper_category_hash
    }

    fun setWallpaperIdHash(wallpaper_id_hash: Int) = apply {
        this.wallpaperIdHash = wallpaper_id_hash
    }

    fun setColorPreference(color_preference: Int) = apply {
        this.colorPreference = color_preference
    }

    fun setLocationPreference(location_preference: Int) = apply {
        this.locationPreference = location_preference
    }

    fun setDatePreference(date_preference: Int) = apply { this.datePreference = date_preference }

    fun setLaunchedPreference(launched_preference: Int) = apply {
        this.launchedPreference = launched_preference
    }

    fun setEffectPreference(effect_preference: Int) = apply {
        this.effectPreference = effect_preference
    }

    fun setEffectIdHash(effect_id_hash: Int) = apply { this.effectIdHash = effect_id_hash }

    fun setLockWallpaperCategoryHash(lock_wallpaper_category_hash: Int) = apply {
        this.lockWallpaperCategoryHash = lock_wallpaper_category_hash
    }

    fun setLockWallpaperIdHash(lock_wallpaper_id_hash: Int) = apply {
        this.lockWallpaperIdHash = lock_wallpaper_id_hash
    }

    fun setFirstLaunchDateSinceSetup(first_launch_date_since_setup: Int) = apply {
        this.firstLaunchDateSinceSetup = first_launch_date_since_setup
    }

    fun setFirstWallpaperApplyDateSinceSetup(first_wallpaper_apply_date_since_setup: Int) = apply {
        this.firstWallpaperApplyDateSinceSetup = first_wallpaper_apply_date_since_setup
    }

    fun setAppLaunchCount(app_launch_count: Int) = apply { this.appLaunchCount = app_launch_count }

    fun setColorVariant(color_variant: Int) = apply { this.colorVariant = color_variant }

    fun setTimeElapsed(time_elapsed_millis: Long) = apply {
      this.timeElapsedMillis = time_elapsed_millis
    }

    fun setEffectResultCode(effect_result_code: Int) = apply {
        this.effectResultCode = effect_result_code
    }

    fun log() {
        SysUiStatsLog.write(
            atom,
            action,
            colorPackageHash,
            fontPackageHash,
            shapePackageHash,
            clockPackageHash,
            launcherGrid,
            wallpaperCategoryHash,
            wallpaperIdHash,
            colorPreference,
            locationPreference,
            datePreference,
            launchedPreference,
            effectPreference,
            effectIdHash,
            lockWallpaperCategoryHash,
            lockWallpaperIdHash,
            firstLaunchDateSinceSetup,
            firstWallpaperApplyDateSinceSetup,
            appLaunchCount,
            colorVariant,
            timeElapsedMillis,
            effectResultCode,
        )
    }
}

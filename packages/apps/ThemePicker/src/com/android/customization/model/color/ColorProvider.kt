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
package com.android.customization.model.color

import android.app.WallpaperColors
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils.setAlphaComponent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.customization.model.CustomizationManager.OptionsFetchedListener
import com.android.customization.model.ResourceConstants.COLOR_BUNDLES_ARRAY_NAME
import com.android.customization.model.ResourceConstants.COLOR_BUNDLE_MAIN_COLOR_PREFIX
import com.android.customization.model.ResourceConstants.COLOR_BUNDLE_NAME_PREFIX
import com.android.customization.model.ResourceConstants.COLOR_BUNDLE_STYLE_PREFIX
import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR
import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SYSTEM_PALETTE
import com.android.customization.model.ResourcesApkProvider
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_HOME
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_LOCK
import com.android.customization.model.color.ColorUtils.toColorString
import com.android.systemui.monet.ColorScheme
import com.android.systemui.monet.Style
import com.android.wallpaper.compat.WallpaperManagerCompat
import com.android.wallpaper.module.InjectorProvider
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Default implementation of {@link ColorOptionsProvider} that reads preset colors from a stub APK.
 */
class ColorProvider(context: Context, stubPackageName: String) :
    ResourcesApkProvider(context, stubPackageName), ColorOptionsProvider {

    companion object {
        const val themeStyleEnabled = true
        val styleSize = if (themeStyleEnabled) Style.values().size else 1
        private const val TAG = "ColorProvider"
        private const val MAX_SEED_COLORS = 4
        private const val MAX_PRESET_COLORS = 4
        private const val ALPHA_MASK = 0xFF
    }

    private val monetEnabled = ColorUtils.isMonetEnabled(context)
    // TODO(b/202145216): Use style method to fetch the list of style.
    private var styleList =
        if (themeStyleEnabled)
            arrayOf(Style.TONAL_SPOT, Style.SPRITZ, Style.VIBRANT, Style.EXPRESSIVE)
        else arrayOf(Style.TONAL_SPOT)

    private val scope =
        if (mContext is LifecycleOwner) {
            mContext.lifecycleScope
        } else {
            CoroutineScope(Dispatchers.Default + SupervisorJob())
        }

    private var colorsAvailable = true
    private var colorBundles: List<ColorOption>? = null
    private var homeWallpaperColors: WallpaperColors? = null
    private var lockWallpaperColors: WallpaperColors? = null

    override fun isAvailable(): Boolean {
        return monetEnabled && super.isAvailable() && colorsAvailable
    }

    override fun fetch(
        callback: OptionsFetchedListener<ColorOption>?,
        reload: Boolean,
        homeWallpaperColors: WallpaperColors?,
        lockWallpaperColors: WallpaperColors?
    ) {
        val wallpaperColorsChanged =
            this.homeWallpaperColors != homeWallpaperColors ||
                this.lockWallpaperColors != lockWallpaperColors
        if (wallpaperColorsChanged) {
            this.homeWallpaperColors = homeWallpaperColors
            this.lockWallpaperColors = lockWallpaperColors
        }
        if (colorBundles == null || reload || wallpaperColorsChanged) {
            scope.launch {
                try {
                    if (colorBundles == null || reload) {
                        loadPreset()
                    }
                    if (wallpaperColorsChanged || reload) {
                        loadSeedColors(homeWallpaperColors, lockWallpaperColors)
                    }
                } catch (e: Throwable) {
                    colorsAvailable = false
                    callback?.onError(e)
                    return@launch
                }
                callback?.onOptionsLoaded(colorBundles)
            }
        } else {
            callback?.onOptionsLoaded(colorBundles)
        }
    }

    private fun isLockScreenWallpaperLastApplied(): Boolean {
        // The WallpaperId increases every time a new wallpaper is set, so the larger wallpaper id
        // is the most recently set wallpaper
        val manager = InjectorProvider.getInjector().getWallpaperManagerCompat(mContext)
        return manager.getWallpaperId(WallpaperManagerCompat.FLAG_LOCK) >
            manager.getWallpaperId(WallpaperManagerCompat.FLAG_SYSTEM)
    }

    private fun loadSeedColors(
        homeWallpaperColors: WallpaperColors?,
        lockWallpaperColors: WallpaperColors?
    ) {
        if (homeWallpaperColors == null) return

        val bundles: MutableList<ColorOption> = ArrayList()
        val colorsPerSource =
            if (lockWallpaperColors == null) {
                MAX_SEED_COLORS
            } else {
                MAX_SEED_COLORS / 2
            }

        if (lockWallpaperColors != null) {
            val shouldLockColorsGoFirst = isLockScreenWallpaperLastApplied()
            // First half of the colors
            buildColorSeeds(
                if (shouldLockColorsGoFirst) lockWallpaperColors else homeWallpaperColors,
                colorsPerSource,
                if (shouldLockColorsGoFirst) COLOR_SOURCE_LOCK else COLOR_SOURCE_HOME,
                true,
                bundles
            )
            // Second half of the colors
            buildColorSeeds(
                if (shouldLockColorsGoFirst) homeWallpaperColors else lockWallpaperColors,
                MAX_SEED_COLORS - bundles.size / styleSize,
                if (shouldLockColorsGoFirst) COLOR_SOURCE_HOME else COLOR_SOURCE_LOCK,
                false,
                bundles
            )
        } else {
            buildColorSeeds(homeWallpaperColors, colorsPerSource, COLOR_SOURCE_HOME, true, bundles)
        }

        bundles.addAll(colorBundles?.filterNot { it is ColorSeedOption } ?: emptyList())
        colorBundles = bundles
    }

    private fun buildColorSeeds(
        wallpaperColors: WallpaperColors,
        maxColors: Int,
        source: String,
        containsDefault: Boolean,
        bundles: MutableList<ColorOption>
    ) {
        val seedColors = ColorScheme.getSeedColors(wallpaperColors)
        val defaultSeed = seedColors.first()
        buildBundle(defaultSeed, 0, containsDefault, source, bundles)
        for ((i, colorInt) in seedColors.drop(1).take(maxColors - 1).withIndex()) {
            buildBundle(colorInt, i + 1, false, source, bundles)
        }
    }

    private fun buildBundle(
        colorInt: Int,
        i: Int,
        isDefault: Boolean,
        source: String,
        bundles: MutableList<ColorOption>
    ) {
        // TODO(b/202145216): Measure time cost in the loop.
        for (style in styleList) {
            val builder = ColorSeedOption.Builder()
            val lightColorScheme = ColorScheme(colorInt, /* darkTheme= */ false, style)
            val darkColorScheme = ColorScheme(colorInt, /* darkTheme= */ true, style)
            builder
                .setLightColors(lightColorScheme.getLightColorPreview())
                .setDarkColors(darkColorScheme.getDarkColorPreview())
                .addOverlayPackage(
                    OVERLAY_CATEGORY_SYSTEM_PALETTE,
                    if (isDefault) "" else toColorString(colorInt)
                )
                .addOverlayPackage(
                    OVERLAY_CATEGORY_COLOR,
                    if (isDefault) "" else toColorString(colorInt)
                )
                .setSource(source)
                .setStyle(style)
                // Color option index value starts from 1.
                .setIndex(i + 1)

            if (isDefault) builder.asDefault()

            bundles.add(builder.build())
        }
    }

    /**
     * Returns the colors for the light theme version of the preview of a ColorScheme based on this
     * order: |-------| | 0 | 1 | |---+---| | 2 | 3 | |-------|
     */
    @ColorInt
    private fun ColorScheme.getLightColorPreview(): IntArray {
        return when (this.style) {
            Style.EXPRESSIVE ->
                intArrayOf(
                    setAlphaComponent(this.accent1.s100, ALPHA_MASK),
                    setAlphaComponent(this.accent1.s100, ALPHA_MASK),
                    ColorStateList.valueOf(this.neutral2.s500).withLStar(80f).colors[0],
                    setAlphaComponent(this.accent2.s500, ALPHA_MASK)
                )
            else ->
                intArrayOf(
                    setAlphaComponent(this.accent1.s100, ALPHA_MASK),
                    setAlphaComponent(this.accent1.s100, ALPHA_MASK),
                    ColorStateList.valueOf(this.accent3.s500).withLStar(85f).colors[0],
                    setAlphaComponent(this.accent1.s500, ALPHA_MASK)
                )
        }
    }

    /**
     * Returns the color for the dark theme version of the preview of a ColorScheme based on this
     * order: |-------| | 0 | 1 | |---+---| | 2 | 3 | |-------|
     */
    @ColorInt
    private fun ColorScheme.getDarkColorPreview(): IntArray {
        return getLightColorPreview()
    }

    private fun ColorScheme.getPresetColorPreview(seed: Int): IntArray {
        return when (this.style) {
            Style.FRUIT_SALAD -> intArrayOf(seed, this.accent1.s100)
            Style.TONAL_SPOT -> intArrayOf(this.accentColor, this.accentColor)
            Style.MONOCHROMATIC ->
                intArrayOf(
                    setAlphaComponent(0x000000, 255),
                    setAlphaComponent(0xFFFFFF, 255),
                )
            else -> intArrayOf(this.accent1.s100, this.accent1.s100)
        }
    }

    private suspend fun loadPreset() =
        withContext(Dispatchers.IO) {
            val extractor = ColorBundlePreviewExtractor(mContext)
            val bundles: MutableList<ColorOption> = ArrayList()

            val bundleNames = getItemsFromStub(COLOR_BUNDLES_ARRAY_NAME)
            // Color option index value starts from 1.
            var index = 1
            val maxPresetColors = if (themeStyleEnabled) bundleNames.size else MAX_PRESET_COLORS
            for (bundleName in bundleNames.take(maxPresetColors)) {
                val builder = ColorBundle.Builder()
                builder.title = getItemStringFromStub(COLOR_BUNDLE_NAME_PREFIX, bundleName)
                builder.setIndex(index)
                val colorFromStub = getItemColorFromStub(COLOR_BUNDLE_MAIN_COLOR_PREFIX, bundleName)
                extractor.addPrimaryColor(builder, colorFromStub)
                extractor.addSecondaryColor(builder, colorFromStub)
                if (themeStyleEnabled) {
                    val styleName =
                        try {
                            getItemStringFromStub(COLOR_BUNDLE_STYLE_PREFIX, bundleName)
                        } catch (e: Resources.NotFoundException) {
                            null
                        }
                    extractor.addColorStyle(builder, styleName)
                    val style =
                        try {
                            if (styleName != null) Style.valueOf(styleName) else Style.TONAL_SPOT
                        } catch (e: IllegalArgumentException) {
                            Style.TONAL_SPOT
                        }

                    if (
                        style == Style.MONOCHROMATIC &&
                            !InjectorProvider.getInjector()
                                .getFlags()
                                .isMonochromaticThemeEnabled(mContext)
                    ) {
                        continue
                    }

                    val darkColors =
                        ColorScheme(colorFromStub, true, style).getPresetColorPreview(colorFromStub)
                    val lightColors =
                        ColorScheme(colorFromStub, false, style)
                            .getPresetColorPreview(colorFromStub)
                    builder.setColorPrimaryDark(darkColors[0]).setColorSecondaryDark(darkColors[1])
                    builder
                        .setColorPrimaryLight(lightColors[0])
                        .setColorSecondaryLight(lightColors[1])
                }

                bundles.add(builder.build(mContext))
                index++
            }

            colorBundles = bundles
        }
}

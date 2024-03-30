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
package com.android.customization.module;

import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_FONT;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;
import static com.android.systemui.shared.system.SysUiStatsLog.STYLE_UICHANGED__ACTION__APP_LAUNCHED;
import static com.android.systemui.shared.system.SysUiStatsLog.STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_CROP_AND_SET_ACTION;
import static com.android.systemui.shared.system.SysUiStatsLog.STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_DEEP_LINK;
import static com.android.systemui.shared.system.SysUiStatsLog.STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_LAUNCHER;
import static com.android.systemui.shared.system.SysUiStatsLog.STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_LAUNCH_ICON;
import static com.android.systemui.shared.system.SysUiStatsLog.STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_PREFERENCE_UNSPECIFIED;
import static com.android.systemui.shared.system.SysUiStatsLog.STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_SETTINGS;
import static com.android.systemui.shared.system.SysUiStatsLog.STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_SETTINGS_SEARCH;
import static com.android.systemui.shared.system.SysUiStatsLog.STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_SUW;
import static com.android.systemui.shared.system.SysUiStatsLog.STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_TIPS;
import static com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SETTINGS_SEARCH;
import static com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SOURCE_DEEP_LINK;
import static com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SOURCE_LAUNCHER;
import static com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SOURCE_SETTINGS;
import static com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SOURCE_SUW;
import static com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SOURCE_TIPS;
import static com.android.wallpaper.util.LaunchSourceUtils.WALLPAPER_LAUNCH_SOURCE;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.stats.style.StyleEnums;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.customization.model.clock.Clockface;
import com.android.customization.model.color.ColorOption;
import com.android.customization.model.grid.GridOption;
import com.android.customization.model.theme.ThemeBundle;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.NoOpUserEventLogger;
import com.android.wallpaper.module.WallpaperPreferences;
import com.android.wallpaper.module.WallpaperStatusChecker;

import java.util.Map;
import java.util.Objects;

/**
 * StatsLog-backed implementation of {@link ThemesUserEventLogger}.
 */
public class StatsLogUserEventLogger extends NoOpUserEventLogger implements ThemesUserEventLogger {

    private static final String TAG = "StatsLogUserEventLogger";
    private final Context mContext;
    private final WallpaperPreferences mPreferences;
    private final WallpaperStatusChecker mWallpaperStatusChecker;

    public StatsLogUserEventLogger(Context appContext) {
        mContext = appContext;
        Injector injector = InjectorProvider.getInjector();
        mPreferences = injector.getPreferences(appContext);
        mWallpaperStatusChecker = injector.getWallpaperStatusChecker();
    }

    @Override
    public void logAppLaunched(Intent launchSource) {
        new SysUiStatsLogger()
                .setAction(STYLE_UICHANGED__ACTION__APP_LAUNCHED)
                .setLaunchedPreference(getAppLaunchSource(launchSource))
                .log();
    }

    @Override
    public void logResumed(boolean provisioned, boolean wallpaper) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.ONRESUME)
                .log();
    }

    @Override
    public void logStopped() {
        new SysUiStatsLogger()
                .setAction(StyleEnums.ONSTOP)
                .log();
    }

    @Override
    public void logActionClicked(String collectionId, int actionLabelResId) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.WALLPAPER_EXPLORE)
                .setWallpaperCategoryHash(getIdHashCode(collectionId))
                .log();
    }

    @Override
    public void logIndividualWallpaperSelected(String collectionId) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.WALLPAPER_SELECT)
                .setWallpaperCategoryHash(getIdHashCode(collectionId))
                .log();
    }

    @Override
    public void logCategorySelected(String collectionId) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.WALLPAPER_OPEN_CATEGORY)
                .setWallpaperCategoryHash(getIdHashCode(collectionId))
                .log();
    }

    @Override
    public void logLiveWallpaperInfoSelected(String collectionId, @Nullable String wallpaperId) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.LIVE_WALLPAPER_INFO_SELECT)
                .setWallpaperCategoryHash(getIdHashCode(collectionId))
                .setWallpaperIdHash(getIdHashCode(wallpaperId))
                .log();
    }

    @Override
    public void logLiveWallpaperCustomizeSelected(String collectionId,
            @Nullable String wallpaperId) {
        new SysUiStatsLogger().setAction(StyleEnums.LIVE_WALLPAPER_CUSTOMIZE_SELECT)
                .setWallpaperCategoryHash(getIdHashCode(collectionId))
                .setWallpaperIdHash(getIdHashCode(wallpaperId))
                .log();

    }

    @Override
    public void logSnapshot() {
        final boolean isLockWallpaperSet = mWallpaperStatusChecker.isLockWallpaperSet(mContext);
        final String homeCollectionId = mPreferences.getHomeWallpaperCollectionId();
        final String homeRemoteId = mPreferences.getHomeWallpaperRemoteId();
        final String effects = mPreferences.getHomeWallpaperEffects();
        String homeWallpaperId = TextUtils.isEmpty(homeRemoteId)
                ? mPreferences.getHomeWallpaperServiceName() : homeRemoteId;
        String lockCollectionId = isLockWallpaperSet ? mPreferences.getLockWallpaperCollectionId()
                : homeCollectionId;
        String lockWallpaperId = isLockWallpaperSet ? mPreferences.getLockWallpaperRemoteId()
                : homeWallpaperId;

        new SysUiStatsLogger().setAction(StyleEnums.SNAPSHOT)
                .setWallpaperCategoryHash(getIdHashCode(homeCollectionId))
                .setWallpaperIdHash(getIdHashCode(homeWallpaperId))
                .setLockWallpaperCategoryHash(getIdHashCode(lockCollectionId))
                .setLockWallpaperIdHash(getIdHashCode(lockWallpaperId))
                .setFirstLaunchDateSinceSetup(mPreferences.getFirstLaunchDateSinceSetup())
                .setFirstWallpaperApplyDateSinceSetup(
                        mPreferences.getFirstWallpaperApplyDateSinceSetup())
                .setAppLaunchCount(mPreferences.getAppLaunchCount())
                .setEffectIdHash(getIdHashCode(effects))
                .log();
    }

    @Override
    public void logWallpaperSet(String collectionId, @Nullable String wallpaperId,
            @Nullable String effects) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.WALLPAPER_APPLIED)
                .setWallpaperCategoryHash(getIdHashCode(collectionId))
                .setWallpaperIdHash(getIdHashCode(wallpaperId))
                .setEffectIdHash(getIdHashCode(effects))
                .log();
    }

    @Override
    public void logEffectApply(String effect, @EffectStatus int status, long timeElapsedMillis,
            int resultCode) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.WALLPAPER_EFFECT_APPLIED)
                .setEffectPreference(status)
                .setEffectIdHash(getIdHashCode(effect))
                .setTimeElapsed(timeElapsedMillis)
                .setEffectResultCode(resultCode)
                .log();
    }

    @Override
    public void logEffectProbe(String effect, @EffectStatus int status) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.WALLPAPER_EFFECT_PROBE)
                .setEffectPreference(status)
                .setEffectIdHash(getIdHashCode(effect))
                .log();
    }

    @Nullable
    private String getThemePackage(ThemeBundle theme, String category) {
        Map<String, String> packages = theme.getPackagesByCategory();
        return packages.get(category);
    }

    @Override
    public void logThemeSelected(ThemeBundle theme, boolean isCustomTheme) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.PICKER_SELECT)
                .setColorPackageHash(
                        Objects.hashCode(getThemePackage(theme, OVERLAY_CATEGORY_COLOR)))
                .setFontPackageHash(Objects.hashCode(getThemePackage(theme, OVERLAY_CATEGORY_FONT)))
                .setShapePackageHash(
                        Objects.hashCode(getThemePackage(theme, OVERLAY_CATEGORY_SHAPE)))
                .log();
    }

    @Override
    public void logThemeApplied(ThemeBundle theme, boolean isCustomTheme) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.PICKER_APPLIED)
                .setColorPackageHash(
                        Objects.hashCode(getThemePackage(theme, OVERLAY_CATEGORY_COLOR)))
                .setFontPackageHash(Objects.hashCode(getThemePackage(theme, OVERLAY_CATEGORY_FONT)))
                .setShapePackageHash(
                        Objects.hashCode(getThemePackage(theme, OVERLAY_CATEGORY_SHAPE)))
                .log();
    }

    @Override
    public void logColorApplied(int action, ColorOption colorOption) {
        new SysUiStatsLogger()
                .setAction(action)
                .setColorPreference(colorOption.getIndex())
                .setColorVariant(colorOption.getStyle().ordinal() + 1)
                .log();
    }

    @Override
    public void logClockSelected(Clockface clock) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.PICKER_SELECT)
                .setClockPackageHash(Objects.hashCode(clock.getId()))
                .log();
    }

    @Override
    public void logClockApplied(Clockface clock) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.PICKER_APPLIED)
                .setClockPackageHash(Objects.hashCode(clock.getId()))
                .log();
    }

    @Override
    public void logGridSelected(GridOption grid) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.PICKER_SELECT)
                .setLauncherGrid(grid.cols)
                .log();
    }

    @Override
    public void logGridApplied(GridOption grid) {
        new SysUiStatsLogger()
                .setAction(StyleEnums.PICKER_APPLIED)
                .setLauncherGrid(grid.cols)
                .log();
    }

    private int getAppLaunchSource(Intent launchSource) {
        if (launchSource.hasExtra(WALLPAPER_LAUNCH_SOURCE)) {
            switch (launchSource.getStringExtra(WALLPAPER_LAUNCH_SOURCE)) {
                case LAUNCH_SOURCE_LAUNCHER:
                    return STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_LAUNCHER;
                case LAUNCH_SOURCE_SETTINGS:
                    return STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_SETTINGS;
                case LAUNCH_SOURCE_SUW:
                    return STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_SUW;
                case LAUNCH_SOURCE_TIPS:
                    return STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_TIPS;
                case LAUNCH_SOURCE_DEEP_LINK:
                    return STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_DEEP_LINK;
                default:
                    return STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_PREFERENCE_UNSPECIFIED;
            }
        } else if (launchSource.hasExtra(LAUNCH_SETTINGS_SEARCH)) {
            return STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_SETTINGS_SEARCH;
        } else if (launchSource.getAction() != null && launchSource.getAction().equals(
                WallpaperManager.ACTION_CROP_AND_SET_WALLPAPER)) {
            return STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_CROP_AND_SET_ACTION;
        } else if (launchSource.getCategories() != null
                && launchSource.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
            return STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_LAUNCH_ICON;
        } else {
            return STYLE_UICHANGED__LAUNCHED_PREFERENCE__LAUNCHED_PREFERENCE_UNSPECIFIED;
        }
    }

    private int getIdHashCode(String id) {
        return id != null ? id.hashCode() : 0;
    }
}

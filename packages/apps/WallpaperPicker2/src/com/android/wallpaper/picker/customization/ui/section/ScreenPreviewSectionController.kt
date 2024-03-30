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
 *
 */

package com.android.wallpaper.picker.customization.ui.section

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.systemui.shared.clocks.shared.model.ClockPreviewConstants
import com.android.wallpaper.R
import com.android.wallpaper.model.CustomizationSectionController
import com.android.wallpaper.model.WallpaperColorsViewModel
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.CurrentWallpaperInfoFactory
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.picker.CategorySelectorFragment
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.ui.binder.ScreenPreviewBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.ScreenPreviewViewModel
import com.android.wallpaper.util.DisplayUtils
import com.android.wallpaper.util.PreviewUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Controls the screen preview section. */
@OptIn(ExperimentalCoroutinesApi::class)
open class ScreenPreviewSectionController(
    private val activity: Activity,
    private val lifecycleOwner: LifecycleOwner,
    private val initialScreen: CustomizationSections.Screen,
    private val wallpaperInfoFactory: CurrentWallpaperInfoFactory,
    private val colorViewModel: WallpaperColorsViewModel,
    private val displayUtils: DisplayUtils,
    private val navigator: CustomizationSectionController.CustomizationSectionNavigationController,
    private val wallpaperInteractor: WallpaperInteractor,
) : CustomizationSectionController<ScreenPreviewView> {

    private lateinit var lockScreenBinding: ScreenPreviewBinder.Binding
    private lateinit var homeScreenBinding: ScreenPreviewBinder.Binding

    /** Override to hide the lock screen clock preview. */
    open val hideLockScreenClockPreview = false

    override fun shouldRetainInstanceWhenSwitchingTabs(): Boolean {
        return true
    }

    override fun isAvailable(context: Context): Boolean {
        // Assumption is that, if this section controller is included, we are using the revamped UI
        // so it should always be shown.
        return true
    }

    @SuppressLint("InflateParams")
    override fun createView(context: Context): ScreenPreviewView {
        val view =
            LayoutInflater.from(context)
                .inflate(
                    R.layout.screen_preview_section,
                    /* parent= */ null,
                ) as ScreenPreviewView
        val onClickListener =
            View.OnClickListener { navigator.navigateTo(CategorySelectorFragment()) }
        view.setOnClickListener(onClickListener)
        val lockScreenView: CardView = view.requireViewById(R.id.lock_preview)
        val homeScreenView: CardView = view.requireViewById(R.id.home_preview)

        lockScreenBinding =
            ScreenPreviewBinder.bind(
                activity = activity,
                previewView = lockScreenView,
                viewModel =
                    ScreenPreviewViewModel(
                        previewUtils =
                            PreviewUtils(
                                context = context,
                                authority =
                                    context.getString(
                                        R.string.lock_screen_preview_provider_authority,
                                    ),
                            ),
                        wallpaperInfoProvider = {
                            suspendCancellableCoroutine { continuation ->
                                wallpaperInfoFactory.createCurrentWallpaperInfos(
                                    { homeWallpaper, lockWallpaper, _ ->
                                        val wallpaper = lockWallpaper ?: homeWallpaper
                                        loadInitialColors(
                                            context = context,
                                            wallpaper = wallpaper,
                                            screen = CustomizationSections.Screen.LOCK_SCREEN,
                                        )
                                        continuation.resume(wallpaper, null)
                                    },
                                    /* forceRefresh= */ true,
                                )
                            }
                        },
                        onWallpaperColorChanged = { colors ->
                            colorViewModel.setLockWallpaperColors(colors)
                        },
                        initialExtrasProvider = {
                            Bundle().apply {
                                // Hide the clock from the system UI rendered preview so we can
                                // place the carousel on top of it.
                                putBoolean(
                                    ClockPreviewConstants.KEY_HIDE_CLOCK,
                                    hideLockScreenClockPreview,
                                )
                            }
                        },
                        wallpaperInteractor = wallpaperInteractor,
                    ),
                lifecycleOwner = lifecycleOwner,
                offsetToStart = displayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(activity),
                screen = CustomizationSections.Screen.LOCK_SCREEN,
                onPreviewDirty = {
                    // only the visible binding should recreate the activity so it's not done twice
                    if (lockScreenView.isVisible) {
                        activity.recreate()
                    }
                },
            )
        homeScreenBinding =
            ScreenPreviewBinder.bind(
                activity = activity,
                previewView = homeScreenView,
                viewModel =
                    ScreenPreviewViewModel(
                        previewUtils =
                            PreviewUtils(
                                context = context,
                                authorityMetadataKey =
                                    context.getString(
                                        R.string.grid_control_metadata_name,
                                    ),
                            ),
                        wallpaperInfoProvider = {
                            suspendCancellableCoroutine { continuation ->
                                wallpaperInfoFactory.createCurrentWallpaperInfos(
                                    { homeWallpaper, lockWallpaper, _ ->
                                        val wallpaper = homeWallpaper ?: lockWallpaper
                                        loadInitialColors(
                                            context = context,
                                            wallpaper = wallpaper,
                                            screen = CustomizationSections.Screen.HOME_SCREEN
                                        )
                                        continuation.resume(wallpaper, null)
                                    },
                                    /* forceRefresh= */ true,
                                )
                            }
                        },
                        onWallpaperColorChanged = { colors ->
                            colorViewModel.setHomeWallpaperColors(colors)
                        },
                        wallpaperInteractor = wallpaperInteractor,
                    ),
                lifecycleOwner = lifecycleOwner,
                offsetToStart = displayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(activity),
                screen = CustomizationSections.Screen.HOME_SCREEN,
                onPreviewDirty = {
                    // only the visible binding should recreate the activity so it's not done twice
                    if (homeScreenView.isVisible) {
                        activity.recreate()
                    }
                },
            )

        onScreenSwitched(isOnLockScreen = initialScreen == CustomizationSections.Screen.LOCK_SCREEN)

        return view
    }

    override fun onScreenSwitched(isOnLockScreen: Boolean) {
        if (isOnLockScreen) {
            lockScreenBinding.show()
            homeScreenBinding.hide()
        } else {
            lockScreenBinding.hide()
            homeScreenBinding.show()
        }
    }

    private fun loadInitialColors(
        context: Context,
        wallpaper: WallpaperInfo?,
        screen: CustomizationSections.Screen,
    ) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val colors = wallpaper?.computeColorInfo(context)?.get()?.wallpaperColors
            withContext(Dispatchers.Main) {
                if (colors != null) {
                    if (screen == CustomizationSections.Screen.LOCK_SCREEN) {
                        colorViewModel.setLockWallpaperColors(colors)
                    } else {
                        colorViewModel.setHomeWallpaperColors(colors)
                    }
                }
            }
        }
    }
}

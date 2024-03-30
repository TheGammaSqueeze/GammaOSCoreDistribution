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
 */
package com.android.customization.picker.color.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import com.android.customization.model.mode.DarkModeSectionController
import com.android.customization.module.ThemePickerInjector
import com.android.customization.picker.color.ui.binder.ColorPickerBinder
import com.android.wallpaper.R
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.customization.ui.binder.ScreenPreviewBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.ScreenPreviewViewModel
import com.android.wallpaper.util.DisplayUtils
import com.android.wallpaper.util.PreviewUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class ColorPickerFragment : AppbarFragment() {
    companion object {
        @JvmStatic
        fun newInstance(): ColorPickerFragment {
            return ColorPickerFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view =
            inflater.inflate(
                R.layout.fragment_color_picker,
                container,
                false,
            )
        setUpToolbar(view)
        val injector = InjectorProvider.getInjector() as ThemePickerInjector
        val lockScreenView: CardView = view.requireViewById(R.id.lock_preview)
        val homeScreenView: CardView = view.requireViewById(R.id.home_preview)
        val wallpaperInfoFactory = injector.getCurrentWallpaperInfoFactory(requireContext())
        val displayUtils: DisplayUtils = injector.getDisplayUtils(requireContext())
        val wcViewModel = injector.getWallpaperColorsViewModel()
        ColorPickerBinder.bind(
            view = view,
            viewModel =
                ViewModelProvider(
                        requireActivity(),
                        injector.getColorPickerViewModelFactory(
                            context = requireContext(),
                            wallpaperColorsViewModel = wcViewModel,
                        ),
                    )
                    .get(),
            lifecycleOwner = this,
        )
        ScreenPreviewBinder.bind(
            activity = requireActivity(),
            previewView = lockScreenView,
            viewModel =
                ScreenPreviewViewModel(
                    previewUtils =
                        PreviewUtils(
                            context = requireContext(),
                            authority =
                                requireContext()
                                    .getString(
                                        R.string.lock_screen_preview_provider_authority,
                                    ),
                        ),
                    wallpaperInfoProvider = {
                        suspendCancellableCoroutine { continuation ->
                            wallpaperInfoFactory.createCurrentWallpaperInfos(
                                { homeWallpaper, lockWallpaper, _ ->
                                    continuation.resume(lockWallpaper ?: homeWallpaper, null)
                                },
                                /* forceRefresh= */ true,
                            )
                        }
                    },
                    onWallpaperColorChanged = { colors ->
                        wcViewModel.setLockWallpaperColors(colors)
                    },
                ),
            lifecycleOwner = this,
            offsetToStart =
                displayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(requireActivity()),
        )
        ScreenPreviewBinder.bind(
            activity = requireActivity(),
            previewView = homeScreenView,
            viewModel =
                ScreenPreviewViewModel(
                    previewUtils =
                        PreviewUtils(
                            context = requireContext(),
                            authorityMetadataKey =
                                requireContext()
                                    .getString(
                                        R.string.grid_control_metadata_name,
                                    ),
                        ),
                    wallpaperInfoProvider = {
                        suspendCancellableCoroutine { continuation ->
                            wallpaperInfoFactory.createCurrentWallpaperInfos(
                                { homeWallpaper, lockWallpaper, _ ->
                                    continuation.resume(homeWallpaper ?: lockWallpaper, null)
                                },
                                /* forceRefresh= */ true,
                            )
                        }
                    },
                    onWallpaperColorChanged = { colors ->
                        wcViewModel.setLockWallpaperColors(colors)
                    },
                ),
            lifecycleOwner = this,
            offsetToStart =
                displayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(requireActivity()),
        )
        val darkModeToggleContainerView: FrameLayout =
            view.requireViewById(R.id.dark_mode_toggle_container)
        val darkModeSectionView =
            DarkModeSectionController(
                    context,
                    lifecycle,
                    injector.getDarkModeSnapshotRestorer(requireContext())
                )
                .createView(requireContext())
        darkModeSectionView.background = null
        darkModeToggleContainerView.addView(darkModeSectionView)
        return view
    }

    override fun getDefaultTitle(): CharSequence {
        return requireContext().getString(R.string.color_picker_title)
    }

    override fun getToolbarColorId(): Int {
        return android.R.color.transparent
    }
}

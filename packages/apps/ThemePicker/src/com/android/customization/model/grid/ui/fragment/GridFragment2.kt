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

package com.android.customization.model.grid.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.android.customization.model.grid.ui.binder.GridScreenBinder
import com.android.customization.model.grid.ui.viewmodel.GridScreenViewModel
import com.android.customization.module.ThemePickerInjector
import com.android.wallpaper.R
import com.android.wallpaper.module.CurrentWallpaperInfoFactory
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.ui.binder.ScreenPreviewBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.ScreenPreviewViewModel
import com.android.wallpaper.util.PreviewUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class GridFragment2 : AppbarFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view =
            inflater.inflate(
                R.layout.fragment_grid,
                container,
                false,
            )
        setUpToolbar(view)

        val injector = InjectorProvider.getInjector() as ThemePickerInjector

        val wallpaperInfoFactory = injector.getCurrentWallpaperInfoFactory(requireContext())
        var screenPreviewBinding =
            bindScreenPreview(
                view,
                wallpaperInfoFactory,
                injector.getWallpaperInteractor(requireContext())
            )

        val viewModelFactory = injector.getGridScreenViewModelFactory(requireContext())
        GridScreenBinder.bind(
            view = view,
            viewModel =
                ViewModelProvider(
                    this,
                    viewModelFactory,
                )[GridScreenViewModel::class.java],
            lifecycleOwner = this,
            backgroundDispatcher = Dispatchers.IO,
            onOptionsChanged = {
                screenPreviewBinding.destroy()
                screenPreviewBinding =
                    bindScreenPreview(
                        view,
                        wallpaperInfoFactory,
                        injector.getWallpaperInteractor(requireContext())
                    )
            }
        )

        return view
    }

    override fun getDefaultTitle(): CharSequence {
        return getString(R.string.grid_title)
    }

    private fun bindScreenPreview(
        view: View,
        wallpaperInfoFactory: CurrentWallpaperInfoFactory,
        wallpaperInteractor: WallpaperInteractor,
    ): ScreenPreviewBinder.Binding {
        return ScreenPreviewBinder.bind(
            activity = requireActivity(),
            previewView = view.requireViewById(R.id.preview),
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
                    wallpaperInteractor = wallpaperInteractor,
                ),
            lifecycleOwner = this,
            offsetToStart = false,
            screen = CustomizationSections.Screen.HOME_SCREEN,
            onPreviewDirty = { activity?.recreate() },
        )
    }
}

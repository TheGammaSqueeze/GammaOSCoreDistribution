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

package com.android.wallpaper.picker.customization.ui.viewmodel

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/** Models UI state for views that can render wallpaper quick switching. */
@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperQuickSwitchViewModel
@VisibleForTesting
constructor(
    private val interactor: WallpaperInteractor,
    maxOptions: Int,
) : ViewModel() {
    private val isLockScreenSelected = MutableStateFlow(false)

    private val selectedWallpaperId: Flow<String> =
        isLockScreenSelected
            .flatMapLatest { isOnLockScreen ->
                interactor.selectedWallpaperId(
                    destination =
                        if (isOnLockScreen) {
                            WallpaperDestination.LOCK
                        } else {
                            WallpaperDestination.HOME
                        },
                )
            }
            .shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                replay = 1,
            )
    private val selectingWallpaperId: Flow<String?> =
        isLockScreenSelected
            .flatMapLatest { isOnLockScreen ->
                interactor.selectingWallpaperId(
                    destination =
                        if (isOnLockScreen) {
                            WallpaperDestination.LOCK
                        } else {
                            WallpaperDestination.HOME
                        },
                )
            }
            .shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                replay = 1,
            )

    val options: Flow<List<WallpaperQuickSwitchOptionViewModel>> =
        isLockScreenSelected
            .flatMapLatest { isOnLockScreen ->
                interactor
                    .previews(
                        destination =
                            if (isOnLockScreen) {
                                WallpaperDestination.LOCK
                            } else {
                                WallpaperDestination.HOME
                            },
                        maxResults = maxOptions,
                    )
                    .distinctUntilChangedBy { previews ->
                        // Produce a key that's the same if the same set of wallpapers is available,
                        // even if in a different order. This is so that the view can keep from
                        // moving the wallpaper options around when the sort order changes as the
                        // user selects different wallpapers.
                        previews.map { preview -> preview.wallpaperId }.sorted().joinToString(",")
                    }
                    .map { previews ->
                        // True if any option is becoming selected following user click.
                        val isSomethingBecomingSelectedFlow: Flow<Boolean> =
                            selectingWallpaperId.distinctUntilChanged().map { it != null }

                        previews.map { preview ->
                            // True if this option is currently selected.
                            val isSelectedFlow: Flow<Boolean> =
                                selectedWallpaperId.distinctUntilChanged().map {
                                    it == preview.wallpaperId
                                }
                            // True if this option is becoming the selected one following user
                            // click.
                            val isBecomingSelectedFlow: Flow<Boolean> =
                                selectingWallpaperId.distinctUntilChanged().map {
                                    it == preview.wallpaperId
                                }

                            WallpaperQuickSwitchOptionViewModel(
                                wallpaperId = preview.wallpaperId,
                                placeholderColor = preview.placeholderColor,
                                thumbnail = {
                                    interactor.loadThumbnail(
                                        wallpaperId = preview.wallpaperId,
                                    )
                                },
                                isLarge =
                                    combine(
                                        isSelectedFlow,
                                        isBecomingSelectedFlow,
                                        isSomethingBecomingSelectedFlow,
                                    ) { isSelected, isBecomingSelected, isSomethingBecomingSelected
                                        ->
                                        // The large option is the one that's currently selected or
                                        // the one that is becoming the selected one following user
                                        // click.
                                        (isSelected && !isSomethingBecomingSelected) ||
                                            isBecomingSelected
                                    },
                                // We show the progress indicator if the option is in the process of
                                // becoming the selected one following user click.
                                isProgressIndicatorVisible = isBecomingSelectedFlow,
                                isSelectionBorderVisible =
                                    combine(
                                        isSelectedFlow,
                                        isBecomingSelectedFlow,
                                        isSomethingBecomingSelectedFlow,
                                    ) { isSelected, isBeingSelected, isSomethingBecomingSelected ->
                                        // The selection border is shown for the option that is the
                                        // one that's currently selected or the one that is becoming
                                        // the selected one following user click.
                                        (isSelected && !isSomethingBecomingSelected) ||
                                            isBeingSelected
                                    },
                                isSelectionIconVisible =
                                    combine(
                                        isSelectedFlow,
                                        isSomethingBecomingSelectedFlow,
                                    ) { isSelected, isSomethingBecomingSelected ->
                                        // The selection icon is shown for the option that is
                                        // currently selected but only if nothing else is becoming
                                        // selected. If anything is being selected following user
                                        // click, the selection icon is not shown on any option.
                                        isSelected && !isSomethingBecomingSelected
                                    },
                                onSelected =
                                    combine(
                                            isSelectedFlow,
                                            isBecomingSelectedFlow,
                                            isSomethingBecomingSelectedFlow,
                                        ) { isSelected, isBeingSelected, isSomethingBecomingSelected
                                            ->
                                            // An option is selectable if it is not itself becoming
                                            // selected following user click or if nothing else is
                                            // becoming selected but this option is not the selected
                                            // one.
                                            (isSomethingBecomingSelected && !isBeingSelected) ||
                                                (!isSomethingBecomingSelected && !isSelected)
                                        }
                                        .distinctUntilChanged()
                                        .map { isSelectable ->
                                            if (isSelectable) {
                                                {
                                                    // A selectable option can become selected.
                                                    viewModelScope.launch {
                                                        interactor.setWallpaper(
                                                            destination =
                                                                if (isOnLockScreen) {
                                                                    WallpaperDestination.LOCK
                                                                } else {
                                                                    WallpaperDestination.HOME
                                                                },
                                                            wallpaperId = preview.wallpaperId,
                                                        )
                                                    }
                                                }
                                            } else {
                                                // A non-selectable option cannot become selected.
                                                null
                                            }
                                        }
                            )
                        }
                    }
            }
            .shareIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                replay = 1,
            )

    fun setOnLockScreen(isLockScreenSelected: Boolean) {
        this.isLockScreenSelected.value = isLockScreenSelected
    }

    companion object {
        @JvmStatic
        fun newFactory(
            owner: SavedStateRegistryOwner,
            defaultArgs: Bundle? = null,
            interactor: WallpaperInteractor,
        ): AbstractSavedStateViewModelFactory =
            object : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    key: String,
                    modelClass: Class<T>,
                    handle: SavedStateHandle,
                ): T {
                    return WallpaperQuickSwitchViewModel(
                        interactor = interactor,
                        maxOptions = MAX_OPTIONS,
                    )
                        as T
                }
            }

        /** The maximum number of options to show, including the currently-selected one. */
        private const val MAX_OPTIONS = 5
    }
}

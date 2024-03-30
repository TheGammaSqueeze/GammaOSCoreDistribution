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

package com.android.wallpaper.picker.customization.domain.interactor

import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotRestorer
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotStore
import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Stores and restores undo snapshots for wallpaper state. */
class WallpaperSnapshotRestorer(
    private val scope: CoroutineScope,
    private val interactor: WallpaperInteractor,
) : SnapshotRestorer {

    private var store: SnapshotStore = SnapshotStore.NOOP

    override suspend fun setUpSnapshotRestorer(
        store: SnapshotStore,
    ): RestorableSnapshot {
        this.store = store
        startObserving()
        return snapshot()
    }

    override suspend fun restoreToSnapshot(
        snapshot: RestorableSnapshot,
    ) {
        val homeWallpaperId = snapshot.args[SELECTED_HOME_SCREEN_WALLPAPER_ID]
        if (!homeWallpaperId.isNullOrEmpty()) {
            interactor.setWallpaper(
                destination = WallpaperDestination.HOME,
                wallpaperId = homeWallpaperId
            )
        }

        val lockWallpaperId = snapshot.args[SELECTED_LOCK_SCREEN_WALLPAPER_ID]
        if (!lockWallpaperId.isNullOrEmpty()) {
            interactor.setWallpaper(
                destination = WallpaperDestination.LOCK,
                wallpaperId = lockWallpaperId
            )
        }
    }

    private fun startObserving() {
        scope.launch {
            combine(
                    interactor.selectedWallpaperId(destination = WallpaperDestination.HOME),
                    interactor.selectedWallpaperId(destination = WallpaperDestination.LOCK),
                    ::Pair,
                )
                .drop(1) // We skip the first value because it's the same as the initial.
                .collect { (homeWallpaperId, lockWallpaperId) ->
                    store.store(
                        snapshot(
                            homeWallpaperId,
                            lockWallpaperId,
                        )
                    )
                }
        }
    }

    private fun snapshot(
        homeWallpaperId: String = querySelectedWallpaperId(destination = WallpaperDestination.HOME),
        lockWallpaperId: String = querySelectedWallpaperId(destination = WallpaperDestination.LOCK),
    ): RestorableSnapshot {
        return RestorableSnapshot(
            args =
                buildMap {
                    put(
                        SELECTED_HOME_SCREEN_WALLPAPER_ID,
                        homeWallpaperId,
                    )
                    put(
                        SELECTED_LOCK_SCREEN_WALLPAPER_ID,
                        lockWallpaperId,
                    )
                }
        )
    }

    private fun querySelectedWallpaperId(destination: WallpaperDestination): String {
        return interactor.selectedWallpaperId(destination = destination).value
    }

    companion object {
        private const val SELECTED_HOME_SCREEN_WALLPAPER_ID = "selected_home_screen_wallpaper_id"
        private const val SELECTED_LOCK_SCREEN_WALLPAPER_ID = "selected_lock_screen_wallpaper_id"
    }
}

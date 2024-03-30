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

package com.android.wallpaper.picker.customization.data.repository

import android.graphics.Bitmap
import com.android.wallpaper.picker.customization.data.content.WallpaperClient
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Encapsulates access to wallpaper-related data. */
class WallpaperRepository(
    private val scope: CoroutineScope,
    private val client: WallpaperClient,
    private val backgroundDispatcher: CoroutineDispatcher,
) {
    /** The ID of the currently-selected wallpaper. */
    fun selectedWallpaperId(
        destination: WallpaperDestination,
    ): StateFlow<String> {
        return client
            .recentWallpapers(destination = destination, limit = 1)
            .map { previews -> previews.first().wallpaperId }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    runBlocking {
                        client.getCurrentWallpaper(destination = destination).wallpaperId
                    },
            )
    }

    private val _selectingWallpaperId =
        MutableStateFlow<Map<WallpaperDestination, String?>>(emptyMap())
    /**
     * The ID of the wallpaper that is in the process of becoming the selected wallpaper or `null`
     * if no such transaction is currently taking place.
     */
    val selectingWallpaperId: StateFlow<Map<WallpaperDestination, String?>> =
        _selectingWallpaperId.asStateFlow()

    /** Lists the most recent wallpapers. The first one is the most recent (current) wallpaper. */
    fun recentWallpapers(
        destination: WallpaperDestination,
        limit: Int,
    ): Flow<List<WallpaperModel>> {
        return client
            .recentWallpapers(destination = destination, limit = limit)
            .flowOn(backgroundDispatcher)
    }

    /** Returns a thumbnail for the wallpaper with the given ID. */
    suspend fun loadThumbnail(wallpaperId: String): Bitmap? {
        return withContext(backgroundDispatcher) { client.loadThumbnail(wallpaperId) }
    }

    /** Sets the wallpaper to the one with the given ID. */
    suspend fun setWallpaper(
        destination: WallpaperDestination,
        wallpaperId: String,
    ) {
        _selectingWallpaperId.value =
            _selectingWallpaperId.value.toMutableMap().apply { this[destination] = wallpaperId }
        withContext(backgroundDispatcher) {
            client.setWallpaper(
                destination = destination,
                wallpaperId = wallpaperId,
            ) {
                _selectingWallpaperId.value =
                    _selectingWallpaperId.value.toMutableMap().apply { this[destination] = null }
            }
        }
    }
}

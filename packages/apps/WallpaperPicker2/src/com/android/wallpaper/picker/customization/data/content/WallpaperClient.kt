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

package com.android.wallpaper.picker.customization.data.content

import android.graphics.Bitmap
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
import kotlinx.coroutines.flow.Flow

/** Defines interface for classes that can interact with the Wallpaper API. */
interface WallpaperClient {

    /** Lists the most recent wallpapers. The first one is the most recent (current) wallpaper. */
    fun recentWallpapers(
        destination: WallpaperDestination,
        limit: Int,
    ): Flow<List<WallpaperModel>>

    /** Returns the selected wallpaper. */
    suspend fun getCurrentWallpaper(
        destination: WallpaperDestination,
    ): WallpaperModel

    /**
     * Asynchronously sets the wallpaper to the one with the given ID.
     *
     * @param destination The screen to set the wallpaper on.
     * @param wallpaperId The ID of the wallpaper to set.
     * @param onDone A callback to invoke when setting is done.
     */
    suspend fun setWallpaper(
        destination: WallpaperDestination,
        wallpaperId: String,
        onDone: () -> Unit
    )

    /** Returns a thumbnail for the wallpaper with the given ID. */
    suspend fun loadThumbnail(wallpaperId: String): Bitmap?
}

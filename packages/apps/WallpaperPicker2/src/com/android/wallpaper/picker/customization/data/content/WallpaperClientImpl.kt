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

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class WallpaperClientImpl(
    private val context: Context,
) : WallpaperClient {

    override fun recentWallpapers(
        destination: WallpaperDestination,
        limit: Int,
    ): Flow<List<WallpaperModel>> {
        return callbackFlow {
            suspend fun queryAndSend(limit: Int) {
                send(queryRecentWallpapers(destination = destination, limit = limit))
            }

            val contentObserver =
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        launch { queryAndSend(limit = limit) }
                    }
                }

            context.contentResolver.registerContentObserver(
                LIST_RECENTS_URI,
                /* notifyForDescendants= */ true,
                contentObserver,
            )
            queryAndSend(limit = limit)

            awaitClose { context.contentResolver.unregisterContentObserver(contentObserver) }
        }
    }

    override suspend fun getCurrentWallpaper(
        destination: WallpaperDestination,
    ): WallpaperModel {
        return queryRecentWallpapers(destination = destination, limit = 1).first()
    }

    override suspend fun setWallpaper(
        destination: WallpaperDestination,
        wallpaperId: String,
        onDone: () -> Unit
    ) {
        val updateValues = ContentValues()
        updateValues.put(KEY_ID, wallpaperId)
        updateValues.put(KEY_SCREEN, destination.asString())
        val updatedRowCount = context.contentResolver.update(SET_WALLPAPER_URI, updateValues, null)
        if (updatedRowCount == 0) {
            Log.e(TAG, "Error setting wallpaper: $wallpaperId")
        }
        onDone.invoke()
    }

    private suspend fun queryRecentWallpapers(
        destination: WallpaperDestination,
        limit: Int,
    ): List<WallpaperModel> {
        context.contentResolver
            .query(
                LIST_RECENTS_URI.buildUpon().appendPath(destination.asString()).build(),
                arrayOf(
                    KEY_ID,
                    KEY_PLACEHOLDER_COLOR,
                ),
                null,
                null,
            )
            .use { cursor ->
                if (cursor == null || cursor.count == 0) {
                    return emptyList()
                }

                return buildList {
                    val idColumnIndex = cursor.getColumnIndex(KEY_ID)
                    val placeholderColorColumnIndex = cursor.getColumnIndex(KEY_PLACEHOLDER_COLOR)
                    while (cursor.moveToNext() && size < limit) {
                        val wallpaperId = cursor.getString(idColumnIndex)
                        val placeholderColor = cursor.getInt(placeholderColorColumnIndex)
                        add(
                            WallpaperModel(
                                wallpaperId = wallpaperId,
                                placeholderColor = placeholderColor,
                            )
                        )
                    }
                }
            }
    }

    override suspend fun loadThumbnail(
        wallpaperId: String,
    ): Bitmap? {
        try {
            // We're already using this in a suspend function, so we're okay.
            @Suppress("BlockingMethodInNonBlockingContext")
            context.contentResolver
                .openFile(
                    GET_THUMBNAIL_BASE_URI.buildUpon().appendPath(wallpaperId).build(),
                    "r",
                    null,
                )
                .use { file ->
                    if (file == null) {
                        Log.e(TAG, "Error getting wallpaper preview: $wallpaperId")
                    } else {
                        return BitmapFactory.decodeFileDescriptor(file.fileDescriptor)
                    }
                }
        } catch (e: IOException) {
            Log.e(TAG, "Error getting wallpaper preview: $wallpaperId", e)
        }

        return null
    }

    private fun WallpaperDestination.asString(): String {
        return when (this) {
            WallpaperDestination.BOTH -> SCREEN_ALL
            WallpaperDestination.HOME -> SCREEN_HOME
            WallpaperDestination.LOCK -> SCREEN_LOCK
        }
    }

    companion object {
        private const val TAG = "WallpaperClientImpl"
        private const val AUTHORITY = "com.google.android.apps.wallpaper.recents"

        /** Path for making a content provider request to set the wallpaper. */
        private const val PATH_SET_WALLPAPER = "set_recent_wallpaper"
        /** Path for making a content provider request to query for the recent wallpapers. */
        private const val PATH_LIST_RECENTS = "list_recent"
        /** Path for making a content provider request to query for the thumbnail of a wallpaper. */
        private const val PATH_GET_THUMBNAIL = "thumb"

        private val BASE_URI =
            Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY).build()
        /** [Uri] for making a content provider request to set the wallpaper. */
        private val SET_WALLPAPER_URI = BASE_URI.buildUpon().appendPath(PATH_SET_WALLPAPER).build()
        /** [Uri] for making a content provider request to query for the recent wallpapers. */
        private val LIST_RECENTS_URI = BASE_URI.buildUpon().appendPath(PATH_LIST_RECENTS).build()
        /**
         * [Uri] for making a content provider request to query for the thumbnail of a wallpaper.
         */
        private val GET_THUMBNAIL_BASE_URI =
            BASE_URI.buildUpon().appendPath(PATH_GET_THUMBNAIL).build()

        /** Key for a parameter used to pass the wallpaper ID to/from the content provider. */
        private const val KEY_ID = "id"
        /** Key for a parameter used to pass the screen to/from the content provider. */
        private const val KEY_SCREEN = "screen"
        private const val SCREEN_ALL = "all_screens"
        private const val SCREEN_HOME = "home_screen"
        private const val SCREEN_LOCK = "lock_screen"
        /**
         * Key for a parameter used to get the placeholder color for a wallpaper from the content
         * provider.
         */
        private const val KEY_PLACEHOLDER_COLOR = "placeholder_color"
    }
}

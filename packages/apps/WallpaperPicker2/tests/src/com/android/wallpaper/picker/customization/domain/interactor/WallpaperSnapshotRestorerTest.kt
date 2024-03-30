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

import android.util.Log
import androidx.test.filters.SmallTest
import com.android.wallpaper.picker.customization.data.content.FakeWallpaperClient
import com.android.wallpaper.picker.customization.data.repository.WallpaperRepository
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotStore
import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class WallpaperSnapshotRestorerTest {

    private lateinit var underTest: WallpaperSnapshotRestorer
    private lateinit var testScope: TestScope
    private lateinit var wallpaperClient: FakeWallpaperClient
    private lateinit var store: SnapshotStore
    private lateinit var storedSnapshots: MutableList<RestorableSnapshot>

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        wallpaperClient = FakeWallpaperClient()
        storedSnapshots = mutableListOf()
        store =
            object : SnapshotStore {
                override fun store(snapshot: RestorableSnapshot) {
                    Log.d("ALE", "storing snapshot #${storedSnapshots.size + 1}")
                    storedSnapshots.add(snapshot)
                }

                override fun retrieve(): RestorableSnapshot {
                    return storedSnapshots.last()
                }
            }

        underTest =
            WallpaperSnapshotRestorer(
                scope = testScope.backgroundScope,
                interactor =
                    WallpaperInteractor(
                        repository =
                            WallpaperRepository(
                                scope = testScope.backgroundScope,
                                client = wallpaperClient,
                                backgroundDispatcher = testDispatcher,
                            ),
                    )
            )
    }

    @Test
    fun restore() =
        testScope.runTest {
            wallpaperClient.setRecentWallpapers(
                buildMap {
                    put(WallpaperDestination.HOME, INITIAL_HOME_WALLPAPERS)
                    put(WallpaperDestination.LOCK, INITIAL_LOCK_WALLPAPERS)
                }
            )
            val initialSnapshot = underTest.setUpSnapshotRestorer(store)
            runCurrent()
            wallpaperClient.setWallpaper(
                destination = WallpaperDestination.HOME,
                wallpaperId = INITIAL_HOME_WALLPAPERS[1].wallpaperId,
                onDone = {},
            )
            runCurrent()
            assertThat(storedSnapshots).hasSize(1)
            wallpaperClient.setWallpaper(
                destination = WallpaperDestination.LOCK,
                wallpaperId = INITIAL_LOCK_WALLPAPERS[4].wallpaperId,
                onDone = {},
            )
            runCurrent()
            assertThat(storedSnapshots).hasSize(2)

            underTest.restoreToSnapshot(storedSnapshots[0])
            assertThat(wallpaperClient.getCurrentWallpaper(destination = WallpaperDestination.HOME))
                .isEqualTo(INITIAL_HOME_WALLPAPERS[1])
            assertThat(wallpaperClient.getCurrentWallpaper(destination = WallpaperDestination.LOCK))
                .isEqualTo(INITIAL_LOCK_WALLPAPERS[0])

            underTest.restoreToSnapshot(initialSnapshot)
            assertThat(wallpaperClient.getCurrentWallpaper(destination = WallpaperDestination.HOME))
                .isEqualTo(INITIAL_HOME_WALLPAPERS[0])
            assertThat(wallpaperClient.getCurrentWallpaper(destination = WallpaperDestination.LOCK))
                .isEqualTo(INITIAL_LOCK_WALLPAPERS[0])

            underTest.restoreToSnapshot(storedSnapshots[1])
            assertThat(wallpaperClient.getCurrentWallpaper(destination = WallpaperDestination.HOME))
                .isEqualTo(INITIAL_HOME_WALLPAPERS[1])
            assertThat(wallpaperClient.getCurrentWallpaper(destination = WallpaperDestination.LOCK))
                .isEqualTo(INITIAL_LOCK_WALLPAPERS[4])
        }

    companion object {
        private val INITIAL_HOME_WALLPAPERS =
            (0..5).map { index -> WallpaperModel(wallpaperId = "H$index", placeholderColor = 0) }
        private val INITIAL_LOCK_WALLPAPERS =
            (0..5).map { index -> WallpaperModel(wallpaperId = "L$index", placeholderColor = 0) }
    }
}

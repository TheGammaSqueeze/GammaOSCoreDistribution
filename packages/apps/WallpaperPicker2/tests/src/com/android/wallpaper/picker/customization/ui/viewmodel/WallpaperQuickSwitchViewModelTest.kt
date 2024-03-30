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

import androidx.test.filters.SmallTest
import com.android.wallpaper.picker.customization.data.content.FakeWallpaperClient
import com.android.wallpaper.picker.customization.data.repository.WallpaperRepository
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class WallpaperQuickSwitchViewModelTest {

    private lateinit var underTest: WallpaperQuickSwitchViewModel

    private lateinit var client: FakeWallpaperClient
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        client = FakeWallpaperClient()

        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        val interactor =
            WallpaperInteractor(
                repository =
                    WallpaperRepository(
                        scope = testScope.backgroundScope,
                        client = client,
                        backgroundDispatcher = testDispatcher,
                    ),
            )
        underTest =
            WallpaperQuickSwitchViewModel(
                interactor = interactor,
                maxOptions = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS.size,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial options`() =
        testScope.runTest {
            val options = collectLastValue(underTest.options)
            assertOptions(
                observed = options(),
                expected = expectations(),
            )
        }

    @Test
    fun `updates options`() =
        testScope.runTest {
            val options = collectLastValue(underTest.options)

            val models =
                listOf(
                    WallpaperModel(
                        wallpaperId = "aaa",
                        placeholderColor = 1200,
                    ),
                    WallpaperModel(
                        wallpaperId = "bbb",
                        placeholderColor = 1300,
                    ),
                    WallpaperModel(
                        wallpaperId = "ccc",
                        placeholderColor = 1400,
                    ),
                )
            client.setRecentWallpapers(buildMap { put(WallpaperDestination.HOME, models) })

            assertOptions(
                observed = options(),
                expected =
                    expectations(
                        models = models,
                    ),
            )
        }

    @Test
    fun `switches to third option`() =
        testScope.runTest {
            val options = collectLastValue(underTest.options)

            // Pause the client so we can examine the interim state.
            client.pause()
            val selectedIndex = 2
            val optionToSelect = checkNotNull(options()?.get(selectedIndex))
            val onSelected = collectLastValue(optionToSelect.onSelected)
            onSelected()?.invoke()

            assertOptions(
                observed = options(),
                expected =
                    expectations(
                        selectingIndex = selectedIndex,
                    ),
            )

            // Unpause the client so we can examine the final state.
            client.unpause()
            runCurrent()
            assertOptions(
                observed = options(),
                expected =
                    expectations(
                        selectedIndex = selectedIndex,
                    ),
            )
        }

    @Test
    fun `switches between screens`() =
        testScope.runTest {
            val options = collectLastValue(underTest.options)

            // We begin on the home screen by default.
            // Select option at index 2 on the home screen.
            val selectedIndex = 2
            val optionToSelect = checkNotNull(options()?.get(selectedIndex))
            val onSelected = collectLastValue(optionToSelect.onSelected)
            onSelected()?.invoke()
            runCurrent()
            assertOptions(
                observed = options(),
                expected =
                    expectations(
                        selectedIndex = selectedIndex,
                    ),
            )

            // Switch to the lock screen, it should still have the original option selected.
            underTest.setOnLockScreen(isLockScreenSelected = true)
            runCurrent()
            assertOptions(
                observed = options(),
                expected = expectations(),
            )

            // Switch back to the home screen, it should still have option at index 2 selected.
            underTest.setOnLockScreen(isLockScreenSelected = false)
            runCurrent()
            assertOptions(
                observed = options(),
                expected =
                    expectations(
                        models =
                            listOf(
                                FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[2],
                                FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[0],
                                FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS[1],
                            ),
                    ),
            )
        }

    private fun expectations(
        models: List<WallpaperModel> = FakeWallpaperClient.INITIAL_RECENT_WALLPAPERS,
        selectedIndex: Int = 0,
        selectingIndex: Int? = null,
    ): List<ExpectedOption> {
        return models.mapIndexed { index, model ->
            val nothingBeingSelected = selectingIndex == null
            val isBeingSelected = selectingIndex == index
            val isSelected = selectedIndex == index
            ExpectedOption(
                wallpaperId = model.wallpaperId,
                placeholderColor = model.placeholderColor,
                isLarge = isBeingSelected || (nothingBeingSelected && isSelected),
                isSelectionIconVisible = nothingBeingSelected && isSelected,
                isSelectionBorderVisible = isBeingSelected || (nothingBeingSelected && isSelected),
                isProgressIndicatorVisible = isBeingSelected,
                isSelectable =
                    (!nothingBeingSelected && !isBeingSelected) ||
                        (nothingBeingSelected && !isSelected),
            )
        }
    }

    private fun TestScope.assertOptions(
        observed: List<WallpaperQuickSwitchOptionViewModel>?,
        expected: List<ExpectedOption>,
    ) {
        checkNotNull(observed)
        assertThat(observed).hasSize(expected.size)
        observed.forEachIndexed { index, option ->
            assertWithMessage("mismatching wallpaperId for index $index.")
                .that(option.wallpaperId)
                .isEqualTo(expected[index].wallpaperId)
            assertWithMessage("mismatching isLarge for index $index.")
                .that(collectLastValue(option.isLarge)())
                .isEqualTo(expected[index].isLarge)
            assertWithMessage("mismatching placeholderColor for index $index.")
                .that(option.placeholderColor)
                .isEqualTo(expected[index].placeholderColor)
            assertWithMessage("mismatching isProgressIndicatorVisible for index $index.")
                .that(collectLastValue(option.isProgressIndicatorVisible)())
                .isEqualTo(expected[index].isProgressIndicatorVisible)
            assertWithMessage("mismatching isSelectionIconVisible for index $index.")
                .that(collectLastValue(option.isSelectionIconVisible)())
                .isEqualTo(expected[index].isSelectionIconVisible)
            assertWithMessage("mismatching isSelectionBorderVisible for index $index.")
                .that(collectLastValue(option.isSelectionBorderVisible)())
                .isEqualTo(expected[index].isSelectionBorderVisible)
            assertWithMessage("mismatching isSelectable for index $index.")
                .that(collectLastValue(option.onSelected)() != null)
                .isEqualTo(expected[index].isSelectable)
        }
    }

    private data class ExpectedOption(
        val wallpaperId: String,
        val placeholderColor: Int,
        val isLarge: Boolean = false,
        val isProgressIndicatorVisible: Boolean = false,
        val isSelectionIconVisible: Boolean = false,
        val isSelectionBorderVisible: Boolean = false,
        val isSelectable: Boolean = true,
    )
}

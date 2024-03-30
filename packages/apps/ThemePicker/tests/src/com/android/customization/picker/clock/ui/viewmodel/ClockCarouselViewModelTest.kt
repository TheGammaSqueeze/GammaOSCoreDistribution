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
package com.android.customization.picker.clock.ui.viewmodel

import androidx.test.filters.SmallTest
import com.android.customization.picker.clock.data.repository.FakeClockPickerRepository
import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import com.android.customization.picker.clock.shared.model.ClockMetadataModel
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
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
class ClockCarouselViewModelTest {
    private val repositoryWithMultipleClocks by lazy { FakeClockPickerRepository() }
    private val repositoryWithSingleClock by lazy {
        FakeClockPickerRepository(
            listOf(
                ClockMetadataModel(
                    "clock0",
                    "clock0",
                    null,
                    ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS,
                    null,
                ),
            )
        )
    }
    private lateinit var underTest: ClockCarouselViewModel

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setSelectedClock() = runTest {
        underTest = ClockCarouselViewModel(ClockPickerInteractor(repositoryWithMultipleClocks))
        val observedSelectedIndex = collectLastValue(underTest.selectedIndex)
        advanceTimeBy(ClockCarouselViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        underTest.setSelectedClock(FakeClockPickerRepository.fakeClocks[2].clockId)
        assertThat(observedSelectedIndex()).isEqualTo(2)
    }

    @Test
    fun setShouldShowCarousel() = runTest {
        underTest = ClockCarouselViewModel(ClockPickerInteractor(repositoryWithMultipleClocks))
        val observedIsCarouselVisible = collectLastValue(underTest.isCarouselVisible)
        advanceTimeBy(ClockCarouselViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        underTest.showClockCarousel(false)
        assertThat(observedIsCarouselVisible()).isFalse()
        underTest.showClockCarousel(true)
        assertThat(observedIsCarouselVisible()).isTrue()
    }

    @Test
    fun shouldNotShowCarouselWhenSingleClock() = runTest {
        underTest = ClockCarouselViewModel(ClockPickerInteractor(repositoryWithSingleClock))
        val observedIsCarouselVisible = collectLastValue(underTest.isCarouselVisible)
        advanceTimeBy(ClockCarouselViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        underTest.showClockCarousel(false)
        assertThat(observedIsCarouselVisible()).isFalse()
        underTest.showClockCarousel(true)
        assertThat(observedIsCarouselVisible()).isFalse()
    }

    @Test
    fun setShouldShowSingleClock() = runTest {
        underTest = ClockCarouselViewModel(ClockPickerInteractor(repositoryWithSingleClock))
        val observedIsSingleClockViewVisible = collectLastValue(underTest.isSingleClockViewVisible)
        advanceTimeBy(ClockCarouselViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        underTest.showClockCarousel(false)
        assertThat(observedIsSingleClockViewVisible()).isFalse()
        underTest.showClockCarousel(true)
        assertThat(observedIsSingleClockViewVisible()).isTrue()
    }

    @Test
    fun shouldNotShowSingleClockWhenMultipleClocks() = runTest {
        underTest = ClockCarouselViewModel(ClockPickerInteractor(repositoryWithMultipleClocks))
        val observedIsSingleClockViewVisible = collectLastValue(underTest.isSingleClockViewVisible)
        advanceTimeBy(ClockCarouselViewModel.CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
        underTest.showClockCarousel(false)
        assertThat(observedIsSingleClockViewVisible()).isFalse()
        underTest.showClockCarousel(true)
        assertThat(observedIsSingleClockViewVisible()).isFalse()
    }
}

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
import androidx.test.platform.app.InstrumentationRegistry
import com.android.customization.picker.clock.data.repository.FakeClockPickerRepository
import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import com.android.customization.picker.clock.shared.ClockSize
import com.android.customization.picker.clock.shared.model.ClockMetadataModel
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ClockSectionViewModelTest {

    private lateinit var clockColorMap: Map<String, ClockColorViewModel>
    private lateinit var interactor: ClockPickerInteractor
    private lateinit var underTest: ClockSectionViewModel

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clockColorMap = ClockColorViewModel.getPresetColorMap(context.resources)
        interactor = ClockPickerInteractor(FakeClockPickerRepository())
        underTest =
            ClockSectionViewModel(
                context,
                interactor,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setSelectedClock() = runTest {
        val colorRed = clockColorMap.values.first()
        val observedSelectedClockColorAndSizeText =
            collectLastValue(underTest.selectedClockColorAndSizeText)
        interactor.setClockColor(
            colorRed.colorId,
            ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS,
            ClockSettingsViewModel.blendColorWithTone(
                colorRed.color,
                colorRed.getColorTone(ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS),
            )
        )
        interactor.setClockSize(ClockSize.DYNAMIC)
        assertThat(observedSelectedClockColorAndSizeText()).isEqualTo("Red, dynamic")
    }
}

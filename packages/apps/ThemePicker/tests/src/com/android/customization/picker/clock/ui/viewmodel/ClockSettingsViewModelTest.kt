package com.android.customization.picker.clock.ui.viewmodel

import android.content.Context
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.customization.picker.clock.data.repository.FakeClockPickerRepository
import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import com.android.customization.picker.clock.shared.ClockSize
import com.android.customization.picker.clock.shared.model.ClockMetadataModel
import com.android.customization.picker.color.data.repository.FakeColorPickerRepository
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.color.domain.interactor.ColorPickerSnapshotRestorer
import com.android.wallpaper.testing.FakeSnapshotStore
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
class ClockSettingsViewModelTest {

    private lateinit var context: Context
    private lateinit var colorPickerInteractor: ColorPickerInteractor
    private lateinit var underTest: ClockSettingsViewModel
    private lateinit var colorMap: Map<String, ClockColorViewModel>

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        context = InstrumentationRegistry.getInstrumentation().targetContext
        colorPickerInteractor =
            ColorPickerInteractor(
                repository = FakeColorPickerRepository(context = context),
                snapshotRestorer = {
                    ColorPickerSnapshotRestorer(interactor = colorPickerInteractor).apply {
                        runBlocking { setUpSnapshotRestorer(store = FakeSnapshotStore()) }
                    }
                },
            )
        underTest =
            ClockSettingsViewModel.Factory(
                    context = context,
                    clockPickerInteractor = ClockPickerInteractor(FakeClockPickerRepository()),
                    colorPickerInteractor = colorPickerInteractor,
                )
                .create(ClockSettingsViewModel::class.java)
        colorMap = ClockColorViewModel.getPresetColorMap(context.resources)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun clickOnColorSettingsTab() = runTest {
        val tabs = collectLastValue(underTest.tabs)
        assertThat(tabs()?.get(0)?.name).isEqualTo("Color")
        assertThat(tabs()?.get(0)?.isSelected).isTrue()
        assertThat(tabs()?.get(1)?.name).isEqualTo("Size")
        assertThat(tabs()?.get(1)?.isSelected).isFalse()

        tabs()?.get(1)?.onClicked?.invoke()
        assertThat(tabs()?.get(0)?.isSelected).isFalse()
        assertThat(tabs()?.get(1)?.isSelected).isTrue()
    }

    @Test
    fun setSelectedColor() = runTest {
        val observedClockColorOptions = collectLastValue(underTest.colorOptions)
        val observedSelectedColorOptionPosition =
            collectLastValue(underTest.selectedColorOptionPosition)
        val observedSliderProgress = collectLastValue(underTest.sliderProgress)
        val observedSeedColor = collectLastValue(underTest.seedColor)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from colorOptions
        advanceTimeBy(ClockSettingsViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        assertThat(observedClockColorOptions()!![0].isSelected).isTrue()
        assertThat(observedClockColorOptions()!![0].onClick).isNull()
        assertThat(observedSelectedColorOptionPosition()).isEqualTo(0)

        observedClockColorOptions()!![1].onClick?.invoke()
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from colorOptions
        advanceTimeBy(ClockSettingsViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        assertThat(observedClockColorOptions()!![1].isSelected).isTrue()
        assertThat(observedClockColorOptions()!![1].onClick).isNull()
        assertThat(observedSelectedColorOptionPosition()).isEqualTo(1)
        assertThat(observedSliderProgress())
            .isEqualTo(ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS)
        val expectedSelectedColorModel = colorMap.values.first() // RED
        assertThat(observedSeedColor())
            .isEqualTo(
                ClockSettingsViewModel.blendColorWithTone(
                    expectedSelectedColorModel.color,
                    expectedSelectedColorModel.getColorTone(
                        ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS
                    ),
                )
            )
    }

    @Test
    fun setColorTone() = runTest {
        val observedClockColorOptions = collectLastValue(underTest.colorOptions)
        val observedIsSliderEnabled = collectLastValue(underTest.isSliderEnabled)
        val observedSliderProgress = collectLastValue(underTest.sliderProgress)
        val observedSeedColor = collectLastValue(underTest.seedColor)
        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from colorOptions
        advanceTimeBy(ClockSettingsViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        assertThat(observedClockColorOptions()!![0].isSelected).isTrue()
        assertThat(observedIsSliderEnabled()).isFalse()

        observedClockColorOptions()!![1].onClick?.invoke()

        // Advance COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS since there is a delay from colorOptions
        advanceTimeBy(ClockSettingsViewModel.COLOR_OPTIONS_EVENT_UPDATE_DELAY_MILLIS)
        assertThat(observedIsSliderEnabled()).isTrue()
        val targetProgress1 = 99
        underTest.onSliderProgressChanged(targetProgress1)
        assertThat(observedSliderProgress()).isEqualTo(targetProgress1)
        val targetProgress2 = 55
        underTest.onSliderProgressStop(targetProgress2)
        assertThat(observedSliderProgress()).isEqualTo(targetProgress2)
        val expectedSelectedColorModel = colorMap.values.first() // RED
        assertThat(observedSeedColor())
            .isEqualTo(
                ClockSettingsViewModel.blendColorWithTone(
                    expectedSelectedColorModel.color,
                    expectedSelectedColorModel.getColorTone(targetProgress2),
                )
            )
    }

    @Test
    fun setClockSize() = runTest {
        val observedClockSize = collectLastValue(underTest.selectedClockSize)
        underTest.setClockSize(ClockSize.DYNAMIC)
        assertThat(observedClockSize()).isEqualTo(ClockSize.DYNAMIC)

        underTest.setClockSize(ClockSize.SMALL)
        assertThat(observedClockSize()).isEqualTo(ClockSize.SMALL)
    }
}

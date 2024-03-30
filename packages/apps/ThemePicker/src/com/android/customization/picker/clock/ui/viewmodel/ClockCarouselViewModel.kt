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

import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull

/**
 * Clock carousel view model that provides data for the carousel of clock previews. When there is
 * only one item, we should show a single clock preview instead of a carousel.
 */
class ClockCarouselViewModel(
    private val interactor: ClockPickerInteractor,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val allClockIds: Flow<List<String>> =
        interactor.allClocks.mapLatest { allClocks ->
            // Delay to avoid the case that the full list of clocks is not initiated.
            delay(CLOCKS_EVENT_UPDATE_DELAY_MILLIS)
            allClocks.map { it.clockId }
        }

    val seedColor: Flow<Int?> = interactor.seedColor

    private val shouldShowCarousel = MutableStateFlow(false)
    val isCarouselVisible: Flow<Boolean> =
        combine(allClockIds.map { it.size > 1 }.distinctUntilChanged(), shouldShowCarousel) {
                hasMoreThanOneClock,
                shouldShowCarousel ->
                hasMoreThanOneClock && shouldShowCarousel
            }
            .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedIndex: Flow<Int> =
        allClockIds
            .flatMapLatest { allClockIds ->
                interactor.selectedClockId.map { selectedClockId ->
                    val index = allClockIds.indexOf(selectedClockId)
                    if (index >= 0) {
                        index
                    } else {
                        null
                    }
                }
            }
            .mapNotNull { it }

    // Handle the case when there is only one clock in the carousel
    private val shouldShowSingleClock = MutableStateFlow(false)
    val isSingleClockViewVisible: Flow<Boolean> =
        combine(allClockIds.map { it.size == 1 }.distinctUntilChanged(), shouldShowSingleClock) {
                hasOneClock,
                shouldShowSingleClock ->
                hasOneClock && shouldShowSingleClock
            }
            .distinctUntilChanged()

    val clockId: Flow<String> =
        allClockIds
            .map { allClockIds -> if (allClockIds.size == 1) allClockIds[0] else null }
            .mapNotNull { it }

    fun setSelectedClock(clockId: String) {
        interactor.setSelectedClock(clockId)
    }

    fun showClockCarousel(shouldShow: Boolean) {
        shouldShowCarousel.value = shouldShow
        shouldShowSingleClock.value = shouldShow
    }

    companion object {
        const val CLOCKS_EVENT_UPDATE_DELAY_MILLIS: Long = 100
    }
}

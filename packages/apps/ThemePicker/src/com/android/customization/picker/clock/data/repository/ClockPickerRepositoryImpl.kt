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
package com.android.customization.picker.clock.data.repository

import android.provider.Settings
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import com.android.customization.picker.clock.shared.ClockSize
import com.android.customization.picker.clock.shared.model.ClockMetadataModel
import com.android.systemui.plugins.ClockMetadata
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.wallpaper.settings.data.repository.SecureSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import org.json.JSONObject

/** Implementation of [ClockPickerRepository], using [ClockRegistry]. */
class ClockPickerRepositoryImpl(
    private val secureSettingsRepository: SecureSettingsRepository,
    private val registry: ClockRegistry,
    scope: CoroutineScope,
) : ClockPickerRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val allClocks: Flow<List<ClockMetadataModel>> =
        callbackFlow {
                fun send() {
                    val allClocks =
                        registry
                            .getClocks()
                            .filter { "NOT_IN_USE" !in it.clockId }
                            .map { it.toModel() }
                    trySend(allClocks)
                }

                val listener =
                    object : ClockRegistry.ClockChangeListener {
                        override fun onAvailableClocksChanged() {
                            send()
                        }
                    }
                registry.registerClockChangeListener(listener)
                send()
                awaitClose { registry.unregisterClockChangeListener(listener) }
            }
            .mapLatest { allClocks ->
                // Loading list of clock plugins can cause many consecutive calls of
                // onAvailableClocksChanged(). We only care about the final fully-initiated clock
                // list. Delay to avoid unnecessary too many emits.
                delay(100)
                allClocks
            }

    /** The currently-selected clock. This also emits the clock color information. */
    override val selectedClock: Flow<ClockMetadataModel> =
        callbackFlow {
                fun send() {
                    val currentClockId = registry.currentClockId
                    val metadata = registry.settings?.metadata
                    val model =
                        registry
                            .getClocks()
                            .find { clockMetadata -> clockMetadata.clockId == currentClockId }
                            ?.toModel(
                                selectedColorId = metadata?.getSelectedColorId(),
                                colorTone = metadata?.getColorTone()
                                        ?: ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS,
                                seedColor = registry.seedColor
                            )
                    trySend(model)
                }

                val listener =
                    object : ClockRegistry.ClockChangeListener {
                        override fun onCurrentClockChanged() {
                            send()
                        }

                        override fun onAvailableClocksChanged() {
                            send()
                        }
                    }
                registry.registerClockChangeListener(listener)
                send()
                awaitClose { registry.unregisterClockChangeListener(listener) }
            }
            .mapNotNull { it }

    override fun setSelectedClock(clockId: String) {
        registry.mutateSetting { oldSettings ->
            val newSettings = oldSettings.copy(clockId = clockId)
            newSettings.metadata = oldSettings.metadata
            newSettings
        }
    }

    override fun setClockColor(
        selectedColorId: String?,
        @IntRange(from = 0, to = 100) colorToneProgress: Int,
        @ColorInt seedColor: Int?,
    ) {
        registry.mutateSetting { oldSettings ->
            val newSettings = oldSettings.copy(seedColor = seedColor)
            newSettings.metadata =
                oldSettings.metadata
                    .put(KEY_METADATA_SELECTED_COLOR_ID, selectedColorId)
                    .put(KEY_METADATA_COLOR_TONE_PROGRESS, colorToneProgress)
            newSettings
        }
    }

    override val selectedClockSize: SharedFlow<ClockSize> =
        secureSettingsRepository
            .intSetting(
                name = Settings.Secure.LOCKSCREEN_USE_DOUBLE_LINE_CLOCK,
            )
            .map { setting -> setting == 1 }
            .map { isDynamic -> if (isDynamic) ClockSize.DYNAMIC else ClockSize.SMALL }
            .shareIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                replay = 1,
            )

    override suspend fun setClockSize(size: ClockSize) {
        secureSettingsRepository.set(
            name = Settings.Secure.LOCKSCREEN_USE_DOUBLE_LINE_CLOCK,
            value = if (size == ClockSize.DYNAMIC) 1 else 0,
        )
    }

    private fun JSONObject.getSelectedColorId(): String? {
        return if (this.isNull(KEY_METADATA_SELECTED_COLOR_ID)) {
            null
        } else {
            this.getString(KEY_METADATA_SELECTED_COLOR_ID)
        }
    }

    private fun JSONObject.getColorTone(): Int {
        return this.optInt(
            KEY_METADATA_COLOR_TONE_PROGRESS,
            ClockMetadataModel.DEFAULT_COLOR_TONE_PROGRESS
        )
    }

    /** By default, [ClockMetadataModel] has no color information unless specified. */
    private fun ClockMetadata.toModel(
        selectedColorId: String? = null,
        @IntRange(from = 0, to = 100) colorTone: Int = 0,
        @ColorInt seedColor: Int? = null,
    ): ClockMetadataModel {
        return ClockMetadataModel(
            clockId = clockId,
            name = name,
            selectedColorId = selectedColorId,
            colorToneProgress = colorTone,
            seedColor = seedColor,
        )
    }

    companion object {
        // The selected color in the color option list
        private const val KEY_METADATA_SELECTED_COLOR_ID = "metadataSelectedColorId"

        // The color tone to apply to the selected color
        private const val KEY_METADATA_COLOR_TONE_PROGRESS = "metadataColorToneProgress"
    }
}

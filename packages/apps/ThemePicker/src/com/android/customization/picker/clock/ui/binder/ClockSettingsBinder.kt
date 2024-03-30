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
package com.android.customization.picker.clock.ui.binder

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.picker.clock.shared.ClockSize
import com.android.customization.picker.clock.ui.adapter.ClockSettingsTabAdapter
import com.android.customization.picker.clock.ui.view.ClockSizeRadioButtonGroup
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.customization.picker.clock.ui.viewmodel.ClockSettingsViewModel
import com.android.customization.picker.color.ui.adapter.ColorOptionAdapter
import com.android.customization.picker.common.ui.view.ItemSpacing
import com.android.wallpaper.R
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/** Bind between the clock settings screen and its view model. */
object ClockSettingsBinder {
    fun bind(
        view: View,
        viewModel: ClockSettingsViewModel,
        clockViewFactory: ClockViewFactory,
        lifecycleOwner: LifecycleOwner,
    ) {
        val clockHostView: FrameLayout = view.requireViewById(R.id.clock_host_view)

        val tabView: RecyclerView = view.requireViewById(R.id.tabs)
        val tabAdapter = ClockSettingsTabAdapter()
        tabView.adapter = tabAdapter
        tabView.layoutManager = LinearLayoutManager(view.context, RecyclerView.HORIZONTAL, false)
        tabView.addItemDecoration(ItemSpacing(ItemSpacing.TAB_ITEM_SPACING_DP))

        val colorOptionContainerView: RecyclerView = view.requireViewById(R.id.color_options)
        val colorOptionAdapter = ColorOptionAdapter()
        colorOptionContainerView.adapter = colorOptionAdapter
        colorOptionContainerView.layoutManager =
            LinearLayoutManager(view.context, RecyclerView.HORIZONTAL, false)
        colorOptionContainerView.addItemDecoration(ItemSpacing(ItemSpacing.ITEM_SPACING_DP))

        val slider: SeekBar = view.requireViewById(R.id.slider)
        slider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        viewModel.onSliderProgressChanged(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.progress?.let { viewModel.onSliderProgressStop(it) }
                }
            }
        )

        val sizeOptions =
            view.requireViewById<ClockSizeRadioButtonGroup>(R.id.clock_size_radio_button_group)
        sizeOptions.onRadioButtonClickListener =
            object : ClockSizeRadioButtonGroup.OnRadioButtonClickListener {
                override fun onClick(size: ClockSize) {
                    viewModel.setClockSize(size)
                }
            }

        val colorOptionContainer = view.requireViewById<View>(R.id.color_picker_container)
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedClockId
                        .mapNotNull { it }
                        .collect { clockId ->
                            val clockView = clockViewFactory.getView(clockId)
                            (clockView.parent as? ViewGroup)?.removeView(clockView)
                            clockHostView.removeAllViews()
                            clockHostView.addView(clockView)
                        }
                }

                launch {
                    viewModel.seedColor.collect { seedColor ->
                        viewModel.selectedClockId.value?.let { selectedClockId ->
                            clockViewFactory.updateColor(selectedClockId, seedColor)
                        }
                    }
                }

                launch { viewModel.tabs.collect { tabAdapter.setItems(it) } }

                launch {
                    viewModel.selectedTab.collect { tab ->
                        when (tab) {
                            ClockSettingsViewModel.Tab.COLOR -> {
                                colorOptionContainer.isVisible = true
                                sizeOptions.isInvisible = true
                            }
                            ClockSettingsViewModel.Tab.SIZE -> {
                                colorOptionContainer.isInvisible = true
                                sizeOptions.isVisible = true
                            }
                        }
                    }
                }

                launch {
                    viewModel.colorOptions.collect { colorOptions ->
                        colorOptionAdapter.setItems(colorOptions)
                    }
                }

                launch {
                    viewModel.selectedColorOptionPosition.collect { selectedPosition ->
                        if (selectedPosition != -1) {
                            // We use "post" because we need to give the adapter item a pass to
                            // update the view.
                            colorOptionContainerView.post {
                                colorOptionContainerView.smoothScrollToPosition(selectedPosition)
                            }
                        }
                    }
                }

                launch {
                    viewModel.selectedClockSize.collect { size ->
                        when (size) {
                            ClockSize.DYNAMIC -> {
                                sizeOptions.radioButtonDynamic.isChecked = true
                                sizeOptions.radioButtonSmall.isChecked = false
                            }
                            ClockSize.SMALL -> {
                                sizeOptions.radioButtonDynamic.isChecked = false
                                sizeOptions.radioButtonSmall.isChecked = true
                            }
                        }
                    }
                }

                launch {
                    viewModel.sliderProgress.collect { progress ->
                        slider.setProgress(progress, true)
                    }
                }

                launch {
                    viewModel.isSliderEnabled.collect { isEnabled -> slider.isEnabled = isEnabled }
                }
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                clockViewFactory.registerTimeTicker()
            }
            // When paused
            clockViewFactory.unregisterTimeTicker()
        }
    }
}

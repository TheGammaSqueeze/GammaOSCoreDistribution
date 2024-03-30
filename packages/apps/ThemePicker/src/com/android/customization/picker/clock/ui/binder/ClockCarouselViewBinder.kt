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

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.customization.picker.clock.ui.view.ClockCarouselView
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.customization.picker.clock.ui.viewmodel.ClockCarouselViewModel
import com.android.wallpaper.R
import kotlinx.coroutines.launch

object ClockCarouselViewBinder {
    /**
     * The binding is used by the view where there is an action executed from another view, e.g.
     * toggling show/hide of the view that the binder is holding.
     */
    interface Binding {
        fun show()
        fun hide()
    }

    @JvmStatic
    fun bind(
        carouselView: ClockCarouselView,
        singleClockView: ViewGroup,
        viewModel: ClockCarouselViewModel,
        clockViewFactory: ClockViewFactory,
        lifecycleOwner: LifecycleOwner,
    ): Binding {
        val singleClockHostView =
            singleClockView.requireViewById<FrameLayout>(R.id.single_clock_host_view)
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isCarouselVisible.collect { carouselView.isVisible = it } }

                launch {
                    viewModel.allClockIds.collect { allClockIds ->
                        carouselView.setUpClockCarouselView(
                            clockIds = allClockIds,
                            onGetClockPreview = { clockId -> clockViewFactory.getView(clockId) },
                            onClockSelected = { clockId -> viewModel.setSelectedClock(clockId) },
                        )
                    }
                }

                launch {
                    viewModel.selectedIndex.collect { selectedIndex ->
                        carouselView.setSelectedClockIndex(selectedIndex)
                    }
                }

                launch {
                    viewModel.seedColor.collect { clockViewFactory.updateColorForAllClocks(it) }
                }

                launch {
                    viewModel.isSingleClockViewVisible.collect { singleClockView.isVisible = it }
                }

                launch {
                    viewModel.clockId.collect { clockId ->
                        singleClockHostView.removeAllViews()
                        val clockView = clockViewFactory.getView(clockId)
                        // The clock view might still be attached to an existing parent. Detach
                        // before adding to another parent.
                        (clockView.parent as? ViewGroup)?.removeView(clockView)
                        singleClockHostView.addView(clockView)
                    }
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

        return object : Binding {
            override fun show() {
                viewModel.showClockCarousel(true)
            }

            override fun hide() {
                viewModel.showClockCarousel(false)
            }
        }
    }
}

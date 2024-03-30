/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.customization.picker.clock.ui.section

import android.content.Context
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.customization.picker.clock.ui.binder.ClockSectionViewBinder
import com.android.customization.picker.clock.ui.fragment.ClockSettingsFragment
import com.android.customization.picker.clock.ui.view.ClockSectionView
import com.android.customization.picker.clock.ui.viewmodel.ClockSectionViewModel
import com.android.wallpaper.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.model.CustomizationSectionController
import com.android.wallpaper.model.CustomizationSectionController.CustomizationSectionNavigationController
import kotlinx.coroutines.launch

/** A [CustomizationSectionController] for clock customization. */
class ClockSectionController(
    private val navigationController: CustomizationSectionNavigationController,
    private val lifecycleOwner: LifecycleOwner,
    private val flag: BaseFlags,
    private val viewModel: ClockSectionViewModel,
) : CustomizationSectionController<ClockSectionView> {

    override fun isAvailable(context: Context): Boolean {
        return flag.isCustomClocksEnabled(context!!)
    }

    override fun createView(context: Context): ClockSectionView {
        val view =
            LayoutInflater.from(context)
                .inflate(
                    R.layout.clock_section_view,
                    null,
                ) as ClockSectionView
        lifecycleOwner.lifecycleScope.launch {
            ClockSectionViewBinder.bind(
                view = view,
                viewModel = viewModel,
                lifecycleOwner = lifecycleOwner
            ) {
                navigationController.navigateTo(ClockSettingsFragment())
            }
        }
        return view
    }
}

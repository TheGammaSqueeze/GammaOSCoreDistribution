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

package com.android.customization.model.grid.ui.binder

import android.view.View
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.model.grid.ui.viewmodel.GridIconViewModel
import com.android.customization.model.grid.ui.viewmodel.GridScreenViewModel
import com.android.customization.picker.common.ui.view.ItemSpacing
import com.android.wallpaper.R
import com.android.wallpaper.picker.option.ui.adapter.OptionItemAdapter
import com.android.wallpaper.picker.option.ui.binder.OptionItemBinder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch

object GridScreenBinder {
    fun bind(
        view: View,
        viewModel: GridScreenViewModel,
        lifecycleOwner: LifecycleOwner,
        backgroundDispatcher: CoroutineDispatcher,
        onOptionsChanged: () -> Unit,
    ) {
        val optionView: RecyclerView = view.requireViewById(R.id.options)
        optionView.layoutManager =
            LinearLayoutManager(
                view.context,
                RecyclerView.HORIZONTAL,
                /* reverseLayout= */ false,
            )
        optionView.addItemDecoration(ItemSpacing(ItemSpacing.ITEM_SPACING_DP))
        val adapter =
            OptionItemAdapter(
                layoutResourceId = R.layout.grid_option_2,
                lifecycleOwner = lifecycleOwner,
                backgroundDispatcher = backgroundDispatcher,
                foregroundTintSpec =
                    OptionItemBinder.TintSpec(
                        selectedColor = view.context.getColor(R.color.text_color_primary),
                        unselectedColor = view.context.getColor(R.color.text_color_secondary),
                    ),
                bindIcon = { foregroundView: View, gridIcon: GridIconViewModel ->
                    val imageView = foregroundView as? ImageView
                    imageView?.let { GridIconViewBinder.bind(imageView, gridIcon) }
                }
            )
        optionView.adapter = adapter

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.optionItems.collect { options ->
                        adapter.setItems(options)
                        onOptionsChanged()
                    }
                }
            }
        }
    }
}

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

package com.android.customization.picker.color.ui.binder

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.view.ViewGroup
import android.widget.ImageView
import com.android.customization.picker.color.ui.viewmodel.ColorOptionIconViewModel
import com.android.wallpaper.R

object ColorOptionIconBinder {
    fun bind(
        view: ViewGroup,
        viewModel: ColorOptionIconViewModel,
    ) {
        val color0View: ImageView = view.requireViewById(R.id.color_preview_0)
        val color1View: ImageView = view.requireViewById(R.id.color_preview_1)
        val color2View: ImageView = view.requireViewById(R.id.color_preview_2)
        val color3View: ImageView = view.requireViewById(R.id.color_preview_3)
        color0View.drawable.colorFilter = BlendModeColorFilter(viewModel.color0, BlendMode.SRC)
        color1View.drawable.colorFilter = BlendModeColorFilter(viewModel.color1, BlendMode.SRC)
        color2View.drawable.colorFilter = BlendModeColorFilter(viewModel.color2, BlendMode.SRC)
        color3View.drawable.colorFilter = BlendModeColorFilter(viewModel.color3, BlendMode.SRC)
    }
}

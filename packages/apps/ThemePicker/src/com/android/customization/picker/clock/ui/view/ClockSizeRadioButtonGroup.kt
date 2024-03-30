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
package com.android.customization.picker.clock.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RadioButton
import com.android.customization.picker.clock.shared.ClockSize
import com.android.wallpaper.R

/** The radio button group to pick the clock size. */
class ClockSizeRadioButtonGroup(
    context: Context,
    attrs: AttributeSet?,
) : FrameLayout(context, attrs) {

    interface OnRadioButtonClickListener {
        fun onClick(size: ClockSize)
    }

    val radioButtonDynamic: RadioButton
    val radioButtonSmall: RadioButton
    var onRadioButtonClickListener: OnRadioButtonClickListener? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.clock_size_radio_button_group, this, true)
        radioButtonDynamic = requireViewById(R.id.radio_button_dynamic)
        val buttonDynamic = requireViewById<View>(R.id.button_container_dynamic)
        buttonDynamic.setOnClickListener { onRadioButtonClickListener?.onClick(ClockSize.DYNAMIC) }
        radioButtonSmall = requireViewById(R.id.radio_button_large)
        val buttonLarge = requireViewById<View>(R.id.button_container_small)
        buttonLarge.setOnClickListener { onRadioButtonClickListener?.onClick(ClockSize.SMALL) }
    }
}

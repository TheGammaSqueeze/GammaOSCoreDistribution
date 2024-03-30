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
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.helper.widget.Carousel
import androidx.core.view.get
import com.android.wallpaper.R

class ClockCarouselView(
    context: Context,
    attrs: AttributeSet,
) :
    FrameLayout(
        context,
        attrs,
    ) {

    private val carousel: Carousel
    private lateinit var adapter: ClockCarouselAdapter

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.clock_carousel, this)
        carousel = view.requireViewById(R.id.carousel)
    }

    fun setUpClockCarouselView(
        clockIds: List<String>,
        onGetClockPreview: (clockId: String) -> View,
        onClockSelected: (clockId: String) -> Unit,
    ) {
        adapter = ClockCarouselAdapter(clockIds, onGetClockPreview, onClockSelected)
        carousel.setAdapter(adapter)
        carousel.refresh()
    }

    fun setSelectedClockIndex(
        index: Int,
    ) {
        carousel.jumpToIndex(index)
    }

    class ClockCarouselAdapter(
        val clockIds: List<String>,
        val onGetClockPreview: (clockId: String) -> View,
        val onClockSelected: (clockId: String) -> Unit,
    ) : Carousel.Adapter {

        override fun count(): Int {
            return clockIds.size
        }

        override fun populate(view: View?, index: Int) {
            val viewRoot = view as ViewGroup
            val clockHostView = viewRoot[0] as ViewGroup
            clockHostView.removeAllViews()
            val clockView = onGetClockPreview(clockIds[index])
            // The clock view might still be attached to an existing parent. Detach before adding to
            // another parent.
            (clockView.parent as? ViewGroup)?.removeView(clockView)
            clockHostView.addView(clockView)
        }

        override fun onNewItem(index: Int) {
            onClockSelected.invoke(clockIds[index])
        }
    }
}

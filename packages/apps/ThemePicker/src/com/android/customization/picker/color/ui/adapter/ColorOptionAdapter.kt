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

package com.android.customization.picker.color.ui.adapter

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.picker.color.ui.viewmodel.ColorOptionViewModel
import com.android.wallpaper.R

/**
 * Adapts between color option items and views.
 *
 * TODO (b/272109171): Remove after clock settings is refactored to use OptionItemAdapter
 */
class ColorOptionAdapter : RecyclerView.Adapter<ColorOptionAdapter.ViewHolder>() {

    private val items = mutableListOf<ColorOptionViewModel>()
    private var isTitleVisible = false

    fun setItems(items: List<ColorOptionViewModel>) {
        this.items.clear()
        this.items.addAll(items)
        isTitleVisible = items.any { item -> item.title != null }
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val borderView: View = itemView.requireViewById(R.id.selection_border)
        val backgroundView: View = itemView.requireViewById(R.id.background)
        val color0View: ImageView = itemView.requireViewById(R.id.color_preview_0)
        val color1View: ImageView = itemView.requireViewById(R.id.color_preview_1)
        val color2View: ImageView = itemView.requireViewById(R.id.color_preview_2)
        val color3View: ImageView = itemView.requireViewById(R.id.color_preview_3)
        val optionTitleView: TextView = itemView.requireViewById(R.id.option_title)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.color_option_with_background,
                    parent,
                    false,
                )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.itemView.setOnClickListener(
            if (item.onClick != null) {
                View.OnClickListener { item.onClick.invoke() }
            } else {
                null
            }
        )
        if (item.isSelected) {
            holder.borderView.alpha = 1f
            holder.borderView.scaleX = 1f
            holder.borderView.scaleY = 1f
            holder.backgroundView.scaleX = 0.86f
            holder.backgroundView.scaleY = 0.86f
        } else {
            holder.borderView.alpha = 0f
            holder.backgroundView.scaleX = 1f
            holder.backgroundView.scaleY = 1f
        }
        holder.color0View.drawable.colorFilter = BlendModeColorFilter(item.color0, BlendMode.SRC)
        holder.color1View.drawable.colorFilter = BlendModeColorFilter(item.color1, BlendMode.SRC)
        holder.color2View.drawable.colorFilter = BlendModeColorFilter(item.color2, BlendMode.SRC)
        holder.color3View.drawable.colorFilter = BlendModeColorFilter(item.color3, BlendMode.SRC)
        holder.itemView.contentDescription = item.contentDescription
        holder.optionTitleView.isVisible = isTitleVisible
        holder.optionTitleView.text = item.title
    }
}

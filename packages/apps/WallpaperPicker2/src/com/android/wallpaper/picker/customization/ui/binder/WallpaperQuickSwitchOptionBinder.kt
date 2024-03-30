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

package com.android.wallpaper.picker.customization.ui.binder

import android.animation.ValueAnimator
import android.view.View
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.picker.customization.ui.viewmodel.WallpaperQuickSwitchOptionViewModel
import kotlinx.coroutines.launch

/**
 * Binds between the view and view-model for a single wallpaper quick switch option.
 *
 * The options are presented to the user in some sort of collection and clicking on one of the
 * options selects that wallpaper.
 */
object WallpaperQuickSwitchOptionBinder {

    /** Binds the given view to the given view-model. */
    fun bind(
        view: View,
        viewModel: WallpaperQuickSwitchOptionViewModel,
        lifecycleOwner: LifecycleOwner,
        smallOptionWidthPx: Int,
        largeOptionWidthPx: Int,
    ) {
        val selectionBorder: View = view.requireViewById(R.id.selection_border)
        val selectionIcon: View = view.requireViewById(R.id.selection_icon)
        val progressIndicator: View = view.requireViewById(R.id.progress_indicator)
        val thumbnailView: ImageView = view.requireViewById(R.id.thumbnail)
        val placeholder: ImageView = view.requireViewById(R.id.placeholder)

        placeholder.setBackgroundColor(viewModel.placeholderColor)

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.onSelected.collect { onSelectedOrNull ->
                        view.setOnClickListener(
                            if (onSelectedOrNull != null) {
                                { onSelectedOrNull.invoke() }
                            } else {
                                null
                            }
                        )
                    }
                }

                launch {
                    // We want to skip animating the first width update.
                    var isFirstValue = true
                    viewModel.isLarge.collect { isLarge ->
                        updateWidth(
                            view = view,
                            targetWidthPx = if (isLarge) largeOptionWidthPx else smallOptionWidthPx,
                            animate = !isFirstValue,
                        )
                        isFirstValue = false
                    }
                }

                launch {
                    viewModel.isSelectionBorderVisible.collect {
                        selectionBorder.animatedVisibility(isVisible = it)
                    }
                }

                launch {
                    viewModel.isSelectionIconVisible.collect {
                        selectionIcon.animatedVisibility(isVisible = it)
                    }
                }

                launch {
                    viewModel.isProgressIndicatorVisible.collect {
                        progressIndicator.animatedVisibility(isVisible = it)
                    }
                }

                launch {
                    val thumbnail = viewModel.thumbnail()
                    if (thumbnailView.tag != thumbnail) {
                        thumbnailView.tag = thumbnail
                        if (thumbnail != null) {
                            thumbnailView.setImageBitmap(thumbnail)
                            thumbnailView.fadeIn()
                        } else {
                            thumbnailView.fadeOut()
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the view width.
     *
     * @param view The [View] to update.
     * @param targetWidthPx The width we want the view to have.
     * @param animate Whether the update should be animated.
     */
    private fun updateWidth(
        view: View,
        targetWidthPx: Int,
        animate: Boolean,
    ) {
        fun setWidth(widthPx: Int) {
            view.updateLayoutParams { width = widthPx }
        }

        if (!animate) {
            setWidth(widthPx = targetWidthPx)
            return
        }

        ValueAnimator.ofInt(
                view.width,
                targetWidthPx,
            )
            .apply {
                addUpdateListener { setWidth(it.animatedValue as Int) }
                start()
            }
    }

    private fun View.animatedVisibility(
        isVisible: Boolean,
    ) {
        if (isVisible) {
            fadeIn()
        } else {
            fadeOut()
        }
    }

    private fun View.fadeIn() {
        if (isVisible) {
            return
        }

        alpha = 0f
        isVisible = true
        animate().alpha(1f).start()
    }

    private fun View.fadeOut() {
        if (!isVisible) {
            return
        }

        animate().alpha(0f).withEndAction { isVisible = false }.start()
    }
}

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

package com.android.intentresolver.widget

import android.animation.ObjectAnimator
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import com.android.intentresolver.R
import com.android.intentresolver.widget.ImagePreviewView.TransitionElementStatusCallback
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.android.internal.R as IntR

private const val IMAGE_FADE_IN_MILLIS = 150L

class ChooserImagePreviewView : RelativeLayout, ImagePreviewView {

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int
    ) : this(context, attrs, defStyleAttr, 0)

    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    private val coroutineScope = MainScope()
    private lateinit var mainImage: RoundedRectImageView
    private lateinit var secondLargeImage: RoundedRectImageView
    private lateinit var secondSmallImage: RoundedRectImageView
    private lateinit var thirdImage: RoundedRectImageView

    private var loadImageJob: Job? = null
    private var transitionStatusElementCallback: TransitionElementStatusCallback? = null

    override fun onFinishInflate() {
        LayoutInflater.from(context)
            .inflate(R.layout.chooser_image_preview_view_internals, this, true)
        mainImage = requireViewById(IntR.id.content_preview_image_1_large)
        secondLargeImage = requireViewById(IntR.id.content_preview_image_2_large)
        secondSmallImage = requireViewById(IntR.id.content_preview_image_2_small)
        thirdImage = requireViewById(IntR.id.content_preview_image_3_small)
    }

    /**
     * Specifies a transition animation target readiness callback. The callback will be
     * invoked once when views preparation is done.
     * Should be called before [setImages].
     */
    override fun setTransitionElementStatusCallback(callback: TransitionElementStatusCallback?) {
        transitionStatusElementCallback = callback
    }

    override fun setImages(uris: List<Uri>, imageLoader: ImageLoader) {
        loadImageJob?.cancel()
        loadImageJob = coroutineScope.launch {
            when (uris.size) {
                0 -> hideAllViews()
                1 -> showOneImage(uris, imageLoader)
                2 -> showTwoImages(uris, imageLoader)
                else -> showThreeImages(uris, imageLoader)
            }
        }
    }

    private fun hideAllViews() {
        mainImage.isVisible = false
        secondLargeImage.isVisible = false
        secondSmallImage.isVisible = false
        thirdImage.isVisible = false
        invokeTransitionViewReadyCallback()
    }

    private suspend fun showOneImage(uris: List<Uri>, imageLoader: ImageLoader) {
        secondLargeImage.isVisible = false
        secondSmallImage.isVisible = false
        thirdImage.isVisible = false
        showImages(uris, imageLoader, mainImage)
    }

    private suspend fun showTwoImages(uris: List<Uri>, imageLoader: ImageLoader) {
        secondSmallImage.isVisible = false
        thirdImage.isVisible = false
        showImages(uris, imageLoader, mainImage, secondLargeImage)
    }

    private suspend fun showThreeImages(uris: List<Uri>, imageLoader: ImageLoader) {
        secondLargeImage.isVisible = false
        showImages(uris, imageLoader, mainImage, secondSmallImage, thirdImage)
        thirdImage.setExtraImageCount(uris.size - 3)
    }

    private suspend fun showImages(
        uris: List<Uri>, imageLoader: ImageLoader, vararg views: RoundedRectImageView
    ) = coroutineScope {
        for (i in views.indices) {
            launch {
                loadImageIntoView(views[i], uris[i], imageLoader)
            }
        }
    }

    private suspend fun loadImageIntoView(
        view: RoundedRectImageView, uri: Uri, imageLoader: ImageLoader
    ) {
        val bitmap = runCatching {
            imageLoader(uri)
        }.getOrDefault(null)
        if (bitmap == null) {
            view.isVisible = false
            if (view === mainImage) {
                invokeTransitionViewReadyCallback()
            }
        } else {
            view.isVisible = true
            view.setImageBitmap(bitmap)

            view.alpha = 0f
            ObjectAnimator.ofFloat(view, "alpha", 0.0f, 1.0f).apply {
                interpolator = DecelerateInterpolator(1.0f)
                duration = IMAGE_FADE_IN_MILLIS
                start()
            }
            if (view === mainImage && transitionStatusElementCallback != null) {
                view.waitForPreDraw()
                invokeTransitionViewReadyCallback()
            }
        }
    }

    private fun invokeTransitionViewReadyCallback() {
        transitionStatusElementCallback?.apply {
            if (mainImage.isVisible && mainImage.drawable != null) {
                mainImage.transitionName?.let { onTransitionElementReady(it) }
            }
            onAllTransitionElementsReady()
        }
        transitionStatusElementCallback = null
    }
}
